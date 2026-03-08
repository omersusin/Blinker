package blinker.go.ui.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private const val HOME_URL = "https://www.google.com"
private const val SEARCH_URL = "https://www.google.com/search?q="

private fun processInput(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.isEmpty() -> HOME_URL
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
        else -> SEARCH_URL + Uri.encode(trimmed)
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(context: Context, tab: TabInfo): WebView {
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

        loadUrl(tab.url)
    }
}

@Composable
fun BrowserScreen() {
    val context = LocalContext.current

    val tabs = remember { mutableStateListOf(TabInfo()) }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }
    var showTabSwitcher by remember { mutableStateOf(false) }

    val webViews = remember { mutableMapOf<String, WebView>() }

    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.first()

    DisposableEffect(Unit) {
        onDispose {
            webViews.values.forEach { it.destroy() }
            webViews.clear()
        }
    }

    fun addNewTab(url: String = HOME_URL) {
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

    fun switchTab(id: String) {
        activeTabId = id
        showTabSwitcher = false
    }

    BackHandler(enabled = showTabSwitcher || activeTab.canGoBack) {
        if (showTabSwitcher) {
            showTabSwitcher = false
        } else {
            webViews[activeTabId]?.goBack()
        }
    }

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
                AddressBar(
                    url = activeTab.url,
                    isSecure = activeTab.isSecure,
                    isLoading = activeTab.isLoading,
                    progress = activeTab.progress,
                    onUrlSubmit = { input ->
                        webViews[activeTabId]?.loadUrl(processInput(input))
                    }
                )
            },
            bottomBar = {
                BottomNavBar(
                    canGoBack = activeTab.canGoBack,
                    canGoForward = activeTab.canGoForward,
                    isLoading = activeTab.isLoading,
                    tabCount = tabs.size,
                    onBack = { webViews[activeTabId]?.goBack() },
                    onForward = { webViews[activeTabId]?.goForward() },
                    onHome = { webViews[activeTabId]?.loadUrl(HOME_URL) },
                    onRefresh = {
                        if (activeTab.isLoading) webViews[activeTabId]?.stopLoading()
                        else webViews[activeTabId]?.reload()
                    },
                    onShowTabs = { showTabSwitcher = true }
                )
            }
        ) { innerPadding ->
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                factory = { ctx ->
                    FrameLayout(ctx)
                },
                update = { container ->
                    container.removeAllViews()
                    val currentTab = tabs.find { it.id == activeTabId } ?: tabs.first()
                    val webView = webViews.getOrPut(activeTabId) {
                        createConfiguredWebView(context, currentTab)
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
