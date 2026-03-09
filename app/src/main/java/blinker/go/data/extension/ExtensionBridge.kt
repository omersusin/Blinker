package blinker.go.data.extension

import android.content.Context
import android.webkit.JavascriptInterface

class ExtensionBridge(private val context: Context) {
    @JavascriptInterface
    fun storageLocalGet(extId: String, keysJson: String?): String {
        return ExtensionStorage(context, extId).get(keysJson)
    }

    @JavascriptInterface
    fun storageLocalSet(extId: String, itemsJson: String) {
        ExtensionStorage(context, extId).set(itemsJson)
    }

    @JavascriptInterface
    fun storageLocalRemove(extId: String, keysJson: String) {
        ExtensionStorage(context, extId).remove(keysJson)
    }

    @JavascriptInterface
    fun storageLocalClear(extId: String) {
        ExtensionStorage(context, extId).clear()
    }

    @JavascriptInterface
    fun tabsQuery(): String {
        return ExtensionTabManager.queryTabs()
    }

    @JavascriptInterface
    fun tabsCreate(url: String) {
        ExtensionTabManager.onTabCreateRequest?.invoke(url)
    }
}
