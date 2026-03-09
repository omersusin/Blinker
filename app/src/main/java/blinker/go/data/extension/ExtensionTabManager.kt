package blinker.go.data.extension

import blinker.go.ui.browser.TabInfo

object ExtensionTabManager {
    private val tabs = mutableListOf<TabInfo>()
    var activeTabId: String = ""
    var onTabCreateRequest: ((String) -> Unit)? = null

    fun updateTabs(currentTabs: List<TabInfo>, currentActiveId: String) {
        tabs.clear()
        tabs.addAll(currentTabs)
        activeTabId = currentActiveId
    }

    fun queryTabs(): String {
        val arr = org.json.JSONArray()
        tabs.forEach { t ->
            val obj = org.json.JSONObject()
            obj.put("id", Math.abs(t.id.hashCode()))
            obj.put("url", t.url)
            obj.put("title", t.title)
            obj.put("active", t.id == activeTabId)
            arr.put(obj)
        }
        return arr.toString()
    }
}
