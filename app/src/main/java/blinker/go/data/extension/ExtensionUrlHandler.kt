package blinker.go.data.extension

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.File

object ExtensionUrlHandler {
    const val DOMAIN = "blinker-ext"
    const val BASE_URL = "https://$DOMAIN"

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
        val host = url.host ?: return null
        val segments: List<String>
        val extId: String

        if (host == DOMAIN) {
            segments = url.pathSegments
            if (segments.size < 2) return null
            extId = segments[0]
        } else if (url.scheme == "chrome-extension") {
            extId = host
            segments = listOf(extId) + url.pathSegments
        } else {
            return null
        }

        val filePath = segments.drop(1).joinToString("/")
        if (filePath.isEmpty()) return null

        val extDir = File(context.filesDir, "extensions/$extId")
        val file = File(extDir, filePath)

        if (!file.exists()) return null
        if (!file.canonicalPath.startsWith(extDir.canonicalPath)) return null

        val mimeType = getMimeType(file.extension)
        return try {
            WebResourceResponse(mimeType, "UTF-8", file.inputStream())
        } catch (_: Exception) { null }
    }

    private fun getMimeType(ext: String): String = when (ext.lowercase()) {
        "html", "htm" -> "text/html"
        "js", "mjs" -> "application/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "ico" -> "image/x-icon"
        "webp" -> "image/webp"
        "map" -> "application/json"
        else -> "application/octet-stream"
    }
}
