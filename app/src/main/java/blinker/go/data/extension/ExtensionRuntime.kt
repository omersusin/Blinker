package blinker.go.data.extension

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import blinker.go.data.extension.ExtensionBridge

@SuppressLint("SetJavaScriptEnabled")
class ExtensionRuntime(private val context: Context) {

    private val backgroundViews = mutableMapOf<String, WebView>()
    private var manager: ExtensionManager? = null

    fun init(extensionManager: ExtensionManager) {
        manager = extensionManager
        extensionManager.getAll().filter { it.enabled && it.hasBackground }.forEach {
            startBackground(it)
        }
    }

    fun onExtensionInstalled(ext: ExtensionInfo) {
        if (ext.enabled && ext.hasBackground) {
            startBackground(ext)
        }
    }

    fun onExtensionToggled(ext: ExtensionInfo) {
        if (ext.enabled && ext.hasBackground) {
            startBackground(ext)
        } else {
            stopBackground(ext.id)
        }
    }

    fun onExtensionUninstalled(id: String) {
        stopBackground(id)
    }

    private fun startBackground(ext: ExtensionInfo) {
        if (backgroundViews.containsKey(ext.id)) return
        val mgr = manager ?: return

        val webView = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(0, 0)
            addJavascriptInterface(ExtensionBridge(context), "BlinkerBridge")
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView, request: WebResourceRequest
                ): WebResourceResponse? {
                    return ExtensionUrlHandler.handleRequest(request.url, context)
                        ?: super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d("BlinkerExt", "Background loaded: ${ext.name}")
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                    Log.d("BlinkerExt", "[${ext.name}] ${msg?.message()}")
                    return true
                }
            }
        }

        val bgPage = ext.backgroundPage
        if (bgPage != null) {
            val url = ExtensionUrlHandler.getUrl(ext.id, bgPage)
            webView.loadUrl(url)
        } else {
            val html = buildString {
                append("<!DOCTYPE html><html><head>")
                append("<script>${ExtensionInjector.getApiShim(ext.id)}</script>")
                ext.backgroundScripts.forEach { scriptPath ->
                    val scriptUrl = ExtensionUrlHandler.getUrl(ext.id, scriptPath)
                    append("<script src=\"$scriptUrl\"></script>")
                }
                append("</head><body></body></html>")
            }
            val baseUrl = ExtensionUrlHandler.getUrl(ext.id, "")
            webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
        }

        backgroundViews[ext.id] = webView
    }

    private fun stopBackground(id: String) {
        backgroundViews[id]?.destroy()
        backgroundViews.remove(id)
    }

    fun destroy() {
        backgroundViews.values.forEach { it.destroy() }
        backgroundViews.clear()
    }
}
