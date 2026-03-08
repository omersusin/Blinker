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
import blinker.go.data.extension.ExtensionInfo
import blinker.go.data.extension.ExtensionInjector
import blinker.go.data.extension.ExtensionManager
import blinker.go.ui.extensions.ExtensionOptionsScreen
import blinker.go.ui.extensions.ExtensionsSheet

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

private fun processInput(input: String, homeUrl: String, searchUrl: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.isEmpty() -> homeUrl
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
        else -> searchUrl + Uri.encode(trimmed)
    }
}

private fun captureWebViewThumbnail(webView: WebView): Bitmap? {
    if (webView.width <= 0 || webView.height <= 0) return null
    return try {
        val scale = 0.3f
        val w = (webView.width * scale).toInt()
        val h = (webView.height * scale).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        webView.draw(canvas)
        bitmap
    } catch (_: Exception) { null }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: Context,
    tab: TabInfo,
    isDesktopMode: Boolean,
    appSettings: BlinkerSettings,
    extensionManager: ExtensionManager,
    onFindResult: (Int, Int) -> Unit,
    onPageLoaded: (String, String) -> Unit
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        settings.apply {
            javaScriptEnabled = appSettings.javaScriptEnabled
            javaScriptCanOpenWindowsAutomatically = !appSettings.blockPopups
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(false)
            if (isDesktopMode) userAgentString = DESKTOP_UA
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                url?.let {
                    tab.url = it
                    tab.isSecure = it.startsWith("https://")
                }
                tab.isLoading = true
                tab.canGoBack = view?.canGoBack() ?: false
                tab.canGoForward = view?.canGoForward() ?: false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                tab.isLoading = false
                tab.progress = 100
                tab.canGoBack = view?.canGoBack() ?: false
                tab.canGoForward = view?.canGoForward() ?: false
                url?.let { tab.url = it }
                view?.title?.let { if (it.isNotBlank()) tab.title = it }
                view?.let { tab.thumbnail = captureWebViewThumbnail(it) }

                val finalUrl = url ?: return
                val finalTitle = view?.title ?: finalUrl
                if (finalUrl.startsWith("http://") || finalUrl.startsWith("https://")) {
                    onPageLoaded(finalUrl, finalTitle)
                }

                if (view != null && finalUrl.startsWith("http")) {
                    ExtensionInjector.inject(view, finalUrl, extensionManager)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean = false
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                tab.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                title?.let { if (it.isNotBlank()) tab.title = it }
            }
        }

        setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (isDoneCounting) {
                onFindResult(activeMatchOrdinal + 1, numberOfMatches)
            }
        }

        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    addRequestHeader("User-Agent", userAgent)
                    setTitle(fileName)
                    setDescription("Downloading...")
                    setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS, fileName
                    )
                }
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE)
                    as DownloadManager
                dm.enqueue(request)
                Toast.makeText(context, "Downloading: $fileName", Toast.LENGTH_SHORT)
                    .show()
            } catch (_: Exception) {
                Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
            }
        }

        loadUrl(tab.url)
    }
}

@Composable
fun BrowserScreen(
    settings: BlinkerSettings,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val homeUrl = settings.homepageUrl
    val searchUrl = settings.searchEngine.searchUrl

    val tabs = remember { mutableStateListOf(TabInfo(initialUrl = homeUrl)) }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isDesktopMode by remember { mutableStateOf(false) }
    var showFindInPage by remember { mutableStateOf(false) }
    var findActiveMatch by remember { mutableIntStateOf(0) }
    var findTotalMatches by remember { mutableIntStateOf(0) }

    var showBookmarks by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(false) }
    var bookmarkList by remember { mutableStateOf(emptyList<Bookmark>()) }
    var historyList by remember { mutableStateOf(emptyList<HistoryEntry>()) }

    var showExtensions by remember { mutableStateOf(false) }
    var extensionList by remember { mutableStateOf(emptyList<ExtensionInfo>()) }

    // Extension options screen state
    var optionsExt by remember { mutableStateOf<ExtensionInfo?>(null) }
    var optionsUrl by remember { mutableStateOf<String?>(null) }

    val bookmarkManager = remember { BookmarkManager(context) }
    val historyManager = remember { HistoryManager(context) }
    val extensionManager = remember { ExtensionManager(context) }

    val webViews = remember { mutableMapOf<String, WebView>() }
    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.first()

    val extPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val result = extensionManager.install(it)
            if (result != null) {
                Toast.makeText(
                    context,
                    "Installed: ${result.name}",
                    Toast.LENGTH_SHORT
                ).show()
                extensionList = extensionManager.getAll()
                showExtensions = true
            } else {
                Toast.makeText(
                    context,
                    "Invalid extension file",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(activeTab.url) {
        isBookmarked = bookmarkManager.isBookmarked(activeTab.url)
    }

    LaunchedEffect(settings.javaScriptEnabled, settings.blockPopups) {
        webViews.values.forEach { wv ->
            wv.settings.javaScriptEnabled = settings.javaScriptEnabled
            wv.settings.javaScriptCanOpenWindowsAutomatically =
                !settings.blockPopups
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViews.values.forEach { it.destroy() }
            webViews.clear()
        }
    }

    fun captureThumbnail(tabId: String) {
        webViews[tabId]?.let { wv ->
            tabs.find { it.id == tabId }?.thumbnail =
                captureWebViewThumbnail(wv)
        }
    }

    fun addNewTab(url: String = homeUrl) {
        captureThumbnail(activeTabId)
        val newTab = TabInfo(initialUrl = url)
        tabs.add(newTab)
        activeTabId = newTab.id
        showTabSwitcher = false
    }

    fun closeTab(id: String) {
        webViews[id]?.destroy()
        webViews.remove(id)
        val index = tabs.indexOfFirst { it.id == id }
        tabs.removeAll { it.id == id }
        if (tabs.isEmpty()) {
            addNewTab()
        } else if (activeTabId == id) {
            activeTabId = tabs[maxOf(0, index - 1)].id
        }
    }

    fun closeAllTabs() {
        webViews.values.forEach { it.destroy() }
        webViews.clear()
        tabs.clear()
        addNewTab()
    }

    fun switchTab(id: String) {
        captureThumbnail(activeTabId)
        activeTabId = id
        showTabSwitcher = false
        showFindInPage = false
        webViews[id]?.clearMatches()
    }

    fun shareCurrentPage() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, activeTab.url)
            putExtra(Intent.EXTRA_SUBJECT, activeTab.title)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    fun toggleDesktopMode() {
        isDesktopMode = !isDesktopMode
        webViews.forEach { (_, wv) ->
            wv.settings.userAgentString =
                if (isDesktopMode) DESKTOP_UA else null
        }
        webViews[activeTabId]?.reload()
    }

    fun toggleBookmark() {
        if (isBookmarked) {
            bookmarkManager.remove(activeTab.url)
        } else {
            bookmarkManager.add(
                Bookmark(url = activeTab.url, title = activeTab.title)
            )
        }
        isBookmarked = !isBookmarked
    }

    fun openDownloads() {
        try {
            context.startActivity(
                Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            )
        } catch (_: Exception) {
            Toast.makeText(context, "Cannot open Downloads", Toast.LENGTH_SHORT)
                .show()
        }
    }

    // ── Extension Options Screen ──
    if (optionsExt != null && optionsUrl != null) {
        ExtensionOptionsScreen(
            extensionName = optionsExt!!.name,
            optionsUrl = optionsUrl!!,
            onBack = {
                optionsExt = null
                optionsUrl = null
            }
        )
        return
    }

    BackHandler(
        enabled = showTabSwitcher || showFindInPage || activeTab.canGoBack
    ) {
        when {
            showFindInPage -> {
                showFindInPage = false
                webViews[activeTabId]?.clearMatches()
            }
            showTabSwitcher -> showTabSwitcher = false
            else -> webViews[activeTabId]?.goBack()
        }
    }

    // ── Sheets & Dialogs ──

    if (showMenu) {
        MenuSheet(
            isDesktopMode = isDesktopMode,
            isBookmarked = isBookmarked,
            onDismiss = { showMenu = false },
            onNewTab = { addNewTab() },
            onToggleBookmark = { toggleBookmark() },
            onBookmarks = {
                bookmarkList = bookmarkManager.getAll()
                showBookmarks = true
            },
            onHistory = {
                historyList = historyManager.getAll()
                showHistory = true
            },
            onShare = { shareCurrentPage() },
            onFindInPage = { showFindInPage = true },
            onDesktopMode = { toggleDesktopMode() },
            onDownloads = { openDownloads() },
            onExtensions = {
                extensionList = extensionManager.getAll()
                showExtensions = true
            },
            onSettings = { onOpenSettings() },
            onCloseAllTabs = { closeAllTabs() }
        )
    }

    if (showBookmarks) {
        BookmarksSheet(
            bookmarks = bookmarkList,
            onDismiss = { showBookmarks = false },
            onBookmarkClick = { url ->
                webViews[activeTabId]?.loadUrl(url)
                showBookmarks = false
            },
            onBookmarkDelete = { url ->
                bookmarkManager.remove(url)
                bookmarkList = bookmarkManager.getAll()
                if (url == activeTab.url) isBookmarked = false
            }
        )
    }

    if (showHistory) {
        HistorySheet(
            history = historyList,
            onDismiss = { showHistory = false },
            onHistoryClick = { url ->
                webViews[activeTabId]?.loadUrl(url)
                showHistory = false
            },
            onClearAll = {
                historyManager.clear()
                historyList = emptyList()
            }
        )
    }

    if (showExtensions) {
        ExtensionsSheet(
            extensions = extensionList,
            onDismiss = { showExtensions = false },
            onToggle = { id, enabled ->
                extensionManager.setEnabled(id, enabled)
                extensionList = extensionManager.getAll()
            },
            onDelete = { id ->
                extensionManager.uninstall(id)
                extensionList = extensionManager.getAll()
            },
            onInstall = {
                showExtensions = false
                extPickerLauncher.launch(arrayOf("*/*"))
            },
            onOpenOptions = { ext ->
                val url = extensionManager.getOptionsPageUrl(
                    ext.id, ext.optionsPage
                )
                if (url != null) {
                    showExtensions = false
                    optionsExt = ext
                    optionsUrl = url
                } else {
                    Toast.makeText(
                        context,
                        "No options page available",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            loadIcon = { id, path ->
                extensionManager.loadIcon(id, path)
            }
        )
    }

    // ── Main UI ──

    if (showTabSwitcher) {
        TabSwitcher(
            tabs = tabs,
            activeTabId = activeTabId,
            onTabSelect = ::switchTab,
            onTabClose = ::closeTab,
            onNewTab = { addNewTab() },
            onDismiss = { showTabSwitcher = false }
        )
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (showFindInPage) {
                    FindInPageBar(
                        onQuery = { query ->
                            if (query.isNotEmpty()) {
                                webViews[activeTabId]?.findAllAsync(query)
                            } else {
                                webViews[activeTabId]?.clearMatches()
                                findActiveMatch = 0
                                findTotalMatches = 0
                            }
                        },
                        onNext = {
                            webViews[activeTabId]?.findNext(true)
                        },
                        onPrevious = {
                            webViews[activeTabId]?.findNext(false)
                        },
                        onDismiss = {
                            showFindInPage = false
                            webViews[activeTabId]?.clearMatches()
                            findActiveMatch = 0
                            findTotalMatches = 0
                        },
                        activeMatch = findActiveMatch,
                        totalMatches = findTotalMatches
                    )
                } else {
                    AddressBar(
                        url = activeTab.url,
                        isSecure = activeTab.isSecure,
                        isLoading = activeTab.isLoading,
                        progress = activeTab.progress,
                        onUrlSubmit = { input ->
                            webViews[activeTabId]?.loadUrl(
                                processInput(input, homeUrl, searchUrl)
                            )
                        },
                        onRefreshOrStop = {
                            if (activeTab.isLoading) {
                                webViews[activeTabId]?.stopLoading()
                            } else {
                                webViews[activeTabId]?.reload()
                            }
                        }
                    )
                }
            },
            bottomBar = {
                BottomNavBar(
                    canGoBack = activeTab.canGoBack,
                    canGoForward = activeTab.canGoForward,
                    tabCount = tabs.size,
                    onBack = { webViews[activeTabId]?.goBack() },
                    onForward = { webViews[activeTabId]?.goForward() },
                    onHome = { webViews[activeTabId]?.loadUrl(homeUrl) },
                    onShowTabs = {
                        captureThumbnail(activeTabId)
                        showTabSwitcher = true
                    },
                    onShowMenu = { showMenu = true }
                )
            }
        ) { innerPadding ->
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                factory = { ctx -> FrameLayout(ctx) },
                update = { container ->
                    container.removeAllViews()
                    val currentTab = tabs.find {
                        it.id == activeTabId
                    } ?: tabs.first()
                    val webView = webViews.getOrPut(activeTabId) {
                        createConfiguredWebView(
                            context, currentTab, isDesktopMode, settings,
                            extensionManager,
                            onFindResult = { active, total ->
                                findActiveMatch = active
                                findTotalMatches = total
                            },
                            onPageLoaded = { url, title ->
                                historyManager.add(
                                    HistoryEntry(url = url, title = title)
                                )
                            }
                        )
                    }
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    container.addView(
                        webView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            )
        }
    }
}
