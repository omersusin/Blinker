package blinker.go.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
@Composable
fun BrowserScreen() {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(HOME_URL) }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isSecure by remember { mutableStateOf(true) }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AddressBar(
                url = currentUrl,
                isSecure = isSecure,
                isLoading = isLoading,
                progress = progress,
                onUrlSubmit = { input ->
                    webView?.loadUrl(processInput(input))
                }
            )
        },
        bottomBar = {
            BottomNavBar(
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                isLoading = isLoading,
                onBack = { webView?.goBack() },
                onForward = { webView?.goForward() },
                onHome = { webView?.loadUrl(HOME_URL) },
                onRefresh = {
                    if (isLoading) webView?.stopLoading()
                    else webView?.reload()
                }
            )
        }
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            factory = { context ->
                WebView(context).apply {
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
                        override fun onPageStarted(
                            view: WebView?, url: String?, favicon: Bitmap?
                        ) {
                            url?.let {
                                currentUrl = it
                                isSecure = it.startsWith("https://")
                            }
                            isLoading = true
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            progress = 100
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?, request: WebResourceRequest?
                        ): Boolean = false
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }
                    }

                    loadUrl(HOME_URL)
                    webView = this
                }
            }
        )
    }
}
