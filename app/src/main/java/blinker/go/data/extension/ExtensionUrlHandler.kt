package blinker.go.data.extension

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.File

object ExtensionUrlHandler {
    const val DOMAIN = "blinker-ext"
    const val BASE_URL = "https://$DOMAIN"
    
    private var assetLoader: WebViewAssetLoader? = null

    fun getUrl(extensionId: String, path: String): String {
        val clean = path.removePrefix("/")
        return "$BASE_URL/$extensionId/$clean"
    }

    fun isExtensionUrl(url: String): Boolean {
        return url.contains(DOMAIN) || url.startsWith("chrome-extension://")
    }

    fun handleRequest(
        url: Uri,
        context: Context
    ): WebResourceResponse? {
        if (assetLoader == null) {
            assetLoader = WebViewAssetLoader.Builder()
                .setDomain(DOMAIN)
                .addPathHandler("/", WebViewAssetLoader.InternalStoragePathHandler(context, File(context.filesDir, "extensions")))
                .build()
        }

        var finalUrl = url
        if (url.scheme == "chrome-extension") {
            val extId = url.host ?: return null
            val path = url.path?.removePrefix("/") ?: ""
            finalUrl = Uri.parse("$BASE_URL/$extId/$path")
        }

        if (finalUrl.host == DOMAIN) {
            val res = assetLoader?.shouldInterceptRequest(finalUrl)
            res?.let {
                val headers = it.responseHeaders?.toMutableMap() ?: mutableMapOf()
                headers["Access-Control-Allow-Origin"] = "*"
                it.responseHeaders = headers
            }
            return res
        }

        return null
    }
}
