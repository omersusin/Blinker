package blinker.go.ui.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import blinker.go.data.Bookmark
import blinker.go.data.BookmarkManager
import blinker.go.data.HistoryEntry
import blinker.go.data.HistoryManager
import blinker.go.data.download.DownloadEngine
import blinker.go.ui.downloads.DownloadsScreen

private const val HOME_URL = "https://www.google.com"
private const val SEARCH_URL = "https://www.google.com/search?q="
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

private fun processInput(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.isEmpty() -> HOME_URL
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
        else -> SEARCH_URL + Uri.encode(trimmed)
    }
}

private fun captureWebViewThumbnail(webView: WebView): Bitmap? {
    if (webView.width <= 0 || webView.height <= 0) return null
    return try {
        val s = 0.3f
        val w = (webView.width * s).toInt()
        val h = (webView.height * s).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val c = Canvas(bmp); c.scale(s, s); webView.draw(c); bmp
    } catch (_: Exception) { null }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: Context,
    tab: TabInfo,
    isDesktopMode: Boolean,
    onFindResult: (Int, Int) -> Unit,
    onPageLoaded: (String, String) -> Unit,
    onDownload: (String, String, String, String) -> Unit
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        settings.apply {
            javaScriptEnabled = true
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
                val fUrl = url ?: return
                val fTitle = view?.title ?: fUrl
                if (fUrl.startsWith("http://") || fUrl.startsWith("https://")) {
                    onPageLoaded(fUrl, fTitle)
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

        setFindListener { activeOrdinal, count, done ->
            if (done) onFindResult(activeOrdinal + 1, count)
        }

        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            onDownload(url, userAgent, contentDisposition, mimeType)
        }

        loadUrl(tab.url)
    }
}

@Composable
fun BrowserScreen() {
    val context = LocalContext.current

    val tabs = remember { mutableStateListOf(TabInfo()) }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isDesktopMode by remember { mutableStateOf(false) }
    var showFindInPage by remember { mutableStateOf(false) }
    var findActiveMatch by remember { mutableIntStateOf(0) }
    var findTotalMatches by remember { mutableIntStateOf(0) }

    var showBookmarks by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(false) }
    var bookmarkList by remember { mutableStateOf(emptyList<Bookmark>()) }
    var historyList by remember { mutableStateOf(emptyList<HistoryEntry>()) }

    val bookmarkManager = remember { BookmarkManager(context) }
    val historyManager = remember { HistoryManager(context) }
    val downloadEngine = remember { DownloadEngine.getInstance(context) }

    val webViews = remember { mutableMapOf<String, WebView>() }
    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.first()

    LaunchedEffect(activeTab.url) {
        isBookmarked = bookmarkManager.isBookmarked(activeTab.url)
    }

    DisposableEffect(Unit) {
        onDispose {
            webViews.values.forEach { it.destroy() }
            webViews.clear()
        }
    }

    fun captureThumbnail(tabId: String) {
        webViews[tabId]?.let { wv ->
            tabs.find { it.id == tabId }?.thumbnail = captureWebViewThumbnail(wv)
        }
    }

    fun addNewTab(url: String = HOME_URL) {
        captureThumbnail(activeTabId)
        val t = TabInfo(initialUrl = url)
        tabs.add(t); activeTabId = t.id
        showTabSwitcher = false
    }

    fun closeTab(id: String) {
        webViews[id]?.destroy(); webViews.remove(id)
        val idx = tabs.indexOfFirst { it.id == id }
        tabs.removeAll { it.id == id }
        if (tabs.isEmpty()) addNewTab()
        else if (activeTabId == id) activeTabId = tabs[maxOf(0, idx - 1)].id
    }

    fun closeAllTabs() {
        webViews.values.forEach { it.destroy() }; webViews.clear()
        tabs.clear(); addNewTab()
    }

    fun switchTab(id: String) {
        captureThumbnail(activeTabId); activeTabId = id
        showTabSwitcher = false; showFindInPage = false
        webViews[id]?.clearMatches()
    }

    fun shareCurrentPage() {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, activeTab.url)
            putExtra(Intent.EXTRA_SUBJECT, activeTab.title)
        }
        context.startActivity(Intent.createChooser(i, null))
    }

    fun toggleDesktopMode() {
        isDesktopMode = !isDesktopMode
        webViews.forEach { (_, wv) ->
            wv.settings.userAgentString = if (isDesktopMode) DESKTOP_UA else null
        }
        webViews[activeTabId]?.reload()
    }

    fun toggleBookmark() {
        if (isBookmarked) bookmarkManager.remove(activeTab.url)
        else bookmarkManager.add(Bookmark(url = activeTab.url, title = activeTab.title))
        isBookmarked = !isBookmarked
    }

    BackHandler(
        enabled = showTabSwitcher || showFindInPage || showDownloads || activeTab.canGoBack
    ) {
        when {
            showDownloads -> showDownloads = false
            showFindInPage -> {
                showFindInPage = false; webViews[activeTabId]?.clearMatches()
            }
            showTabSwitcher -> showTabSwitcher = false
            else -> webViews[activeTabId]?.goBack()
        }
    }

    if (showMenu) {
        MenuSheet(
            isDesktopMode = isDesktopMode,
            isBookmarked = isBookmarked,
            onDismiss = { showMenu = false },
            onNewTab = { addNewTab() },
            onToggleBookmark = { toggleBookmark() },
            onBookmarks = {
                bookmarkList = bookmarkManager.getAll(); showBookmarks = true
            },
            onHistory = {
                historyList = historyManager.getAll(); showHistory = true
            },
            onShare = { shareCurrentPage() },
            onFindInPage = { showFindInPage = true },
            onDesktopMode = { toggleDesktopMode() },
            onDownloads = { showDownloads = true },
            onCloseAllTabs = { closeAllTabs() }
        )
    }

    if (showBookmarks) {
        BookmarksSheet(
            bookmarks = bookmarkList,
            onDismiss = { showBookmarks = false },
            onBookmarkClick = { url ->
                webViews[activeTabId]?.loadUrl(url); showBookmarks = false
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
                webViews[activeTabId]?.loadUrl(url); showHistory = false
            },
            onClearAll = { historyManager.clear(); historyList = emptyList() }
        )
    }

    if (showDownloads) {
        DownloadsScreen(
            engine = downloadEngine,
            onDismiss = { showDownloads = false }
        )
    } else if (showTabSwitcher) {
        TabSwitcher(
            tabs = tabs, activeTabId = activeTabId,
            onTabSelect = ::switchTab, onTabClose = ::closeTab,
            onNewTab = { addNewTab() },
            onDismiss = { showTabSwitcher = false }
        )
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (showFindInPage) {
                    FindInPageBar(
                        onQuery = { q ->
                            if (q.isNotEmpty()) webViews[activeTabId]?.findAllAsync(q)
                            else {
                                webViews[activeTabId]?.clearMatches()
                                findActiveMatch = 0; findTotalMatches = 0
                            }
                        },
                        onNext = { webViews[activeTabId]?.findNext(true) },
                        onPrevious = { webViews[activeTabId]?.findNext(false) },
                        onDismiss = {
                            showFindInPage = false
                            webViews[activeTabId]?.clearMatches()
                            findActiveMatch = 0; findTotalMatches = 0
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
                        onUrlSubmit = { webViews[activeTabId]?.loadUrl(processInput(it)) },
                        onRefreshOrStop = {
                            if (activeTab.isLoading) webViews[activeTabId]?.stopLoading()
                            else webViews[activeTabId]?.reload()
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
                    onHome = { webViews[activeTabId]?.loadUrl(HOME_URL) },
                    onShowTabs = {
                        captureThumbnail(activeTabId); showTabSwitcher = true
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
                    val curTab = tabs.find { it.id == activeTabId } ?: tabs.first()
                    val wv = webViews.getOrPut(activeTabId) {
                        createConfiguredWebView(
                            context, curTab, isDesktopMode,
                            onFindResult = { a, t ->
                                findActiveMatch = a; findTotalMatches = t
                            },
                            onPageLoaded = { url, title ->
                                historyManager.add(HistoryEntry(url = url, title = title))
                            },
                            onDownload = { url, ua, cd, mime ->
                                val name = URLUtil.guessFileName(url, cd, mime)
                                val cookies = CookieManager.getInstance().getCookie(url)
                                downloadEngine.startDownload(url, name, mime, ua, cookies)
                                Toast.makeText(context, "Downloading: $name", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        )
                    }
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    container.addView(
                        wv, FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            )
        }
    }
}
