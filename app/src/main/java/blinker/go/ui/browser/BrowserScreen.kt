package blinker.go.ui.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import blinker.go.data.BlinkerSettings
import blinker.go.data.Bookmark
import blinker.go.data.BookmarkManager
import blinker.go.data.HistoryEntry
import blinker.go.data.HistoryManager
import blinker.go.data.download.DownloadEngine
import blinker.go.data.extension.ExtensionInfo
import blinker.go.data.extension.ExtensionInjector
import blinker.go.data.extension.ExtensionManager
import blinker.go.data.extension.ExtensionRuntime
import blinker.go.data.extension.ExtensionUrlHandler
import blinker.go.data.extension.ExtensionTabManager
import blinker.go.data.extension.ExtensionBridge
import blinker.go.ui.extensions.ExtensionOptionsScreen
import blinker.go.ui.extensions.ExtensionsSheet

private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

private fun processInput(input: String, homeUrl: String, searchUrl: String): String {
    val t = input.trim()
    return when {
        t.isEmpty() -> homeUrl
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains(".") && !t.contains(" ") -> "https://$t"
        else -> searchUrl + Uri.encode(t)
    }
}

private fun captureThumb(wv: WebView): Bitmap? {
    if (wv.width <= 0 || wv.height <= 0) return null
    return try {
        val s = 0.3f; val w = (wv.width * s).toInt(); val h = (wv.height * s).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val c = Canvas(bmp); c.scale(s, s); wv.draw(c); bmp
    } catch (_: Exception) { null }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    ctx: Context, tab: TabInfo, desktop: Boolean,
    settings: BlinkerSettings, extMgr: ExtensionManager,
    onFind: (Int, Int) -> Unit, onLoaded: (String, String) -> Unit
): WebView = WebView(ctx).apply {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    addJavascriptInterface(ExtensionBridge(ctx), "BlinkerBridge")
    this.settings.apply {
        javaScriptEnabled = settings.javaScriptEnabled
        javaScriptCanOpenWindowsAutomatically = !settings.blockPopups
        domStorageEnabled = true; databaseEnabled = true
        setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
        useWideViewPort = true; loadWithOverviewMode = true
        allowFileAccess = false; allowContentAccess = false
        cacheMode = WebSettings.LOAD_DEFAULT
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        mediaPlaybackRequiresUserGesture = false; setSupportMultipleWindows(false)
        if (desktop) userAgentString = DESKTOP_UA
    }
    webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
            return ExtensionUrlHandler.handleRequest(req.url, ctx)
                ?: super.shouldInterceptRequest(view, req)
        }
        override fun onPageStarted(v: WebView?, url: String?, fav: Bitmap?) {
            url?.let { tab.url = it; tab.isSecure = it.startsWith("https://") }
            tab.isLoading = true; tab.canGoBack = v?.canGoBack() ?: false
            tab.canGoForward = v?.canGoForward() ?: false
            if (v != null && url?.startsWith("http") == true)
                ExtensionInjector.injectAtStart(v, url, extMgr)
        }
        override fun onPageFinished(v: WebView?, url: String?) {
            tab.isLoading = false; tab.progress = 100
            tab.canGoBack = v?.canGoBack() ?: false; tab.canGoForward = v?.canGoForward() ?: false
            url?.let { tab.url = it }; v?.title?.let { if (it.isNotBlank()) tab.title = it }
            v?.let { tab.thumbnail = captureThumb(it) }
            val u = url ?: return; val t = v?.title ?: u
            if (u.startsWith("http://") || u.startsWith("https://")) onLoaded(u, t)
            if (v != null && u.startsWith("http")) ExtensionInjector.inject(v, u, extMgr)
        }
        override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean = false
    }
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(v: WebView?, p: Int) { tab.progress = p }
        override fun onReceivedTitle(v: WebView?, t: String?) { t?.let { if (it.isNotBlank()) tab.title = it } }
    }
    setFindListener { a, n, d -> if (d) onFind(a + 1, n) }
    setDownloadListener { url, ua, cd, mime, _ ->
        val fn = URLUtil.guessFileName(url, cd, mime)
        val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
        DownloadEngine.getInstance(ctx).startDownload(url, fn, mime ?: "*/*", ua ?: "", cookies)
        Toast.makeText(ctx, "Download started: $fn", Toast.LENGTH_SHORT).show()
    }
    loadUrl(tab.url)
}

@Composable
fun BrowserScreen(settings: BlinkerSettings, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val homeUrl = settings.homepageUrl
    val searchUrl = settings.searchEngine.searchUrl

    val tabs = remember { mutableStateListOf(TabInfo(initialUrl = homeUrl)) }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }
    var showTabs by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDl by remember { mutableStateOf(false) }
    var desktop by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var findA by remember { mutableIntStateOf(0) }
    var findT by remember { mutableIntStateOf(0) }
    var showBm by remember { mutableStateOf(false) }
    var showHist by remember { mutableStateOf(false) }
    var isBm by remember { mutableStateOf(false) }
    var bmList by remember { mutableStateOf(emptyList<Bookmark>()) }
    var histList by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var showExt by remember { mutableStateOf(false) }
    var extList by remember { mutableStateOf(emptyList<ExtensionInfo>()) }
    var optExt by remember { mutableStateOf<ExtensionInfo?>(null) }
    var optUrl by remember { mutableStateOf<String?>(null) }
    var lastHome by remember { mutableStateOf(homeUrl) }

    val bmMgr = remember { BookmarkManager(ctx) }
    val histMgr = remember { HistoryManager(ctx) }
    val extMgr = remember { ExtensionManager(ctx) }
    val extRuntime = remember { ExtensionRuntime(ctx) }
    val wvs = remember { mutableMapOf<String, WebView>() }
    val tab = tabs.find { it.id == activeTabId } ?: tabs.first()

    LaunchedEffect(Unit) { extRuntime.init(extMgr) }

    val extPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val r = extMgr.install(it)
            if (r != null) {
                Toast.makeText(ctx, "Installed: ${r.name}", Toast.LENGTH_SHORT).show()
                extRuntime.onExtensionInstalled(r)
                extList = extMgr.getAll()
                showExt = true
            } else Toast.makeText(ctx, "Invalid extension file", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(homeUrl) { if (homeUrl != lastHome) { lastHome = homeUrl; wvs[activeTabId]?.loadUrl(homeUrl) } }
    LaunchedEffect(tab.url) { isBm = bmMgr.isBookmarked(tab.url) }
    LaunchedEffect(settings.javaScriptEnabled, settings.blockPopups) {
        wvs.values.forEach { it.settings.javaScriptEnabled = settings.javaScriptEnabled; it.settings.javaScriptCanOpenWindowsAutomatically = !settings.blockPopups }
    }
    DisposableEffect(Unit) { onDispose { wvs.values.forEach { it.destroy() }; wvs.clear(); extRuntime.destroy() } }

    fun capThumb(id: String) { wvs[id]?.let { tabs.find { t -> t.id == id }?.thumbnail = captureThumb(it) } }
    fun addTab(url: String = homeUrl) { capThumb(activeTabId); val n = TabInfo(initialUrl = url); tabs.add(n); activeTabId = n.id; showTabs = false }
    fun closeTab(id: String) { wvs[id]?.destroy(); wvs.remove(id); val i = tabs.indexOfFirst { it.id == id }; tabs.removeAll { it.id == id }; if (tabs.isEmpty()) addTab() else if (activeTabId == id) activeTabId = tabs[maxOf(0, i - 1)].id }
    fun closeAll() { wvs.values.forEach { it.destroy() }; wvs.clear(); tabs.clear(); addTab() }
    fun switchTo(id: String) { capThumb(activeTabId); activeTabId = id; showTabs = false; showFind = false; wvs[id]?.clearMatches() }
    fun share() { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, tab.url); putExtra(Intent.EXTRA_SUBJECT, tab.title) }, null)) }
    fun toggleDt() { desktop = !desktop; wvs.forEach { (_, w) -> w.settings.userAgentString = if (desktop) DESKTOP_UA else null }; wvs[activeTabId]?.reload() }
    fun toggleBm() { if (isBm) bmMgr.remove(tab.url) else bmMgr.add(Bookmark(url = tab.url, title = tab.title)); isBm = !isBm }
    fun openDl() { showDl = true }

    LaunchedEffect(Unit) { ExtensionTabManager.onTabCreateRequest = { url -> addTab(url) } }
    LaunchedEffect(tabs.size, activeTabId, tab.url, tab.title) { ExtensionTabManager.updateTabs(tabs.toList(), activeTabId) }

    if (optExt != null && optUrl != null) {
        ExtensionOptionsScreen(extensionId = optExt!!.id, extensionName = optExt!!.name, optionsUrl = optUrl!!, onBack = { optExt = null; optUrl = null })
        return
    }

    BackHandler(enabled = showTabs || showFind || tab.canGoBack) {
        when { showFind -> { showFind = false; wvs[activeTabId]?.clearMatches() }; showTabs -> showTabs = false; else -> wvs[activeTabId]?.goBack() }
    }

    if (showMenu) MenuSheet(isDesktopMode = desktop, isBookmarked = isBm, onDismiss = { showMenu = false },
        onNewTab = { addTab() }, onToggleBookmark = { toggleBm() },
        onBookmarks = { bmList = bmMgr.getAll(); showBm = true },
        onHistory = { histList = histMgr.getAll(); showHist = true },
        onShare = { share() }, onFindInPage = { showFind = true },
        onDesktopMode = { toggleDt() }, onDownloads = { openDl() },
        onExtensions = { extList = extMgr.getAll(); showExt = true },
        onSettings = { onOpenSettings() }, onCloseAllTabs = { closeAll() })

    if (showBm) BookmarksSheet(bookmarks = bmList, onDismiss = { showBm = false },
        onBookmarkClick = { wvs[activeTabId]?.loadUrl(it); showBm = false },
        onBookmarkDelete = { bmMgr.remove(it); bmList = bmMgr.getAll(); if (it == tab.url) isBm = false })

    if (showHist) HistorySheet(history = histList, onDismiss = { showHist = false },
        onHistoryClick = { wvs[activeTabId]?.loadUrl(it); showHist = false },
        onClearAll = { histMgr.clear(); histList = emptyList() })

    if (showExt) ExtensionsSheet(extensions = extList, onDismiss = { showExt = false },
        onToggle = { id, en -> extMgr.setEnabled(id, en); extList = extMgr.getAll(); extList.find { it.id == id }?.let { extRuntime.onExtensionToggled(it) } },
        onDelete = { id -> extMgr.uninstall(id); extRuntime.onExtensionUninstalled(id); extList = extMgr.getAll() },
        onInstall = { showExt = false; extPicker.launch(arrayOf("*/*")) },
        onOpenOptions = { ext ->
            val url = ext.optionsPage?.let { extMgr.getExtensionUrl(ext.id, it) }
            if (url != null) { showExt = false; optExt = ext; optUrl = url }
            else Toast.makeText(ctx, "No options page available", Toast.LENGTH_SHORT).show()
        },
        loadIcon = { id, path -> extMgr.loadIcon(id, path) })

    if (showDl) {
        androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            blinker.go.ui.downloads.DownloadsScreen(
                engine = blinker.go.data.download.DownloadEngine.getInstance(ctx),
                onDismiss = { showDl = false }
            )
        }
    } else if (showTabs) TabSwitcher(tabs = tabs, activeTabId = activeTabId, onTabSelect = ::switchTo, onTabClose = ::closeTab, onNewTab = { addTab() }, onDismiss = { showTabs = false })
    else Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showFind) FindInPageBar(
                onQuery = { q -> if (q.isNotEmpty()) wvs[activeTabId]?.findAllAsync(q) else { wvs[activeTabId]?.clearMatches(); findA = 0; findT = 0 } },
                onNext = { wvs[activeTabId]?.findNext(true) }, onPrevious = { wvs[activeTabId]?.findNext(false) },
                onDismiss = { showFind = false; wvs[activeTabId]?.clearMatches(); findA = 0; findT = 0 },
                activeMatch = findA, totalMatches = findT)
            else AddressBar(url = tab.url, isSecure = tab.isSecure, isLoading = tab.isLoading, progress = tab.progress,
                onUrlSubmit = { wvs[activeTabId]?.loadUrl(processInput(it, homeUrl, searchUrl)) },
                onRefreshOrStop = { if (tab.isLoading) wvs[activeTabId]?.stopLoading() else wvs[activeTabId]?.reload() })
        },
        bottomBar = { BottomNavBar(canGoBack = tab.canGoBack, canGoForward = tab.canGoForward, tabCount = tabs.size,
            onBack = { wvs[activeTabId]?.goBack() }, onForward = { wvs[activeTabId]?.goForward() },
            onHome = { wvs[activeTabId]?.loadUrl(homeUrl) }, onShowTabs = { capThumb(activeTabId); showTabs = true },
            onShowMenu = { showMenu = true }) }
    ) { pad ->
        AndroidView(modifier = Modifier.fillMaxSize().padding(pad), factory = { FrameLayout(it) }, update = { c ->
            c.removeAllViews()
            val ct = tabs.find { it.id == activeTabId } ?: tabs.first()
            val wv = wvs.getOrPut(activeTabId) { createWebView(ctx, ct, desktop, settings, extMgr,
                onFind = { a, t -> findA = a; findT = t }, onLoaded = { u, t -> histMgr.add(HistoryEntry(url = u, title = t)) }) }
            (wv.parent as? ViewGroup)?.removeView(wv)
            c.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        })
    }
}
