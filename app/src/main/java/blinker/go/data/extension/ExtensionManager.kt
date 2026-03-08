package blinker.go.data.extension

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ExtensionManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(
        "blinker_extensions", Context.MODE_PRIVATE
    )

    fun getAll(): List<ExtensionInfo> {
        val json = prefs.getString("list", "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                try {
                    deserialize(array.getJSONObject(i))
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun install(uri: Uri): ExtensionInfo? {
        val info = ExtensionParser.parse(context, uri) ?: return null
        val list = getAll().toMutableList()
        list.add(info)
        save(list)
        return info
    }

    fun uninstall(id: String) {
        val list = getAll().toMutableList()
        list.removeAll { it.id == id }
        save(list)
        File(context.filesDir, "extensions/$id").deleteRecursively()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val list = getAll().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index] = list[index].copy(enabled = enabled)
            save(list)
        }
    }

    fun getContentScriptsForUrl(url: String): List<Pair<ExtensionInfo, ContentScript>> {
        return getAll().filter { it.enabled }.flatMap { ext ->
            ext.contentScripts
                .filter { cs -> urlMatches(url, cs.matches, cs.excludeMatches) }
                .map { cs -> ext to cs }
        }
    }

    fun readFile(extensionId: String, path: String): String? {
        val file = File(context.filesDir, "extensions/$extensionId/$path")
        return if (file.exists()) {
            try { file.readText() } catch (_: Exception) { null }
        } else null
    }

    fun loadIcon(extensionId: String, iconPath: String?): Bitmap? {
        if (iconPath == null) return null
        val file = File(context.filesDir, "extensions/$extensionId/$iconPath")
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) { null }
    }

    /**
     * Get the full file:// URL for an extension's options page
     */
    fun getOptionsPageUrl(extensionId: String, optionsPage: String?): String? {
        if (optionsPage == null) return null
        val file = File(context.filesDir, "extensions/$extensionId/$optionsPage")
        return if (file.exists()) "file://${file.absolutePath}" else null
    }

    private fun urlMatches(
        url: String,
        matches: List<String>,
        excludes: List<String>
    ): Boolean {
        if (excludes.any { patternMatches(url, it) }) return false
        return matches.any { patternMatches(url, it) }
    }

    private fun patternMatches(url: String, pattern: String): Boolean {
        if (pattern == "<all_urls>") {
            return url.startsWith("http://") || url.startsWith("https://")
        }
        return try {
            val regex = pattern
                .replace(".", "\\.")
                .replace("*://", "(https?|ftp)://")
                .replace("*", ".*")
            Regex("^$regex").containsMatchIn(url)
        } catch (_: Exception) { false }
    }

    private fun save(list: List<ExtensionInfo>) {
        val array = JSONArray()
        list.forEach { array.put(serialize(it)) }
        prefs.edit().putString("list", array.toString()).apply()
    }

    private fun serialize(ext: ExtensionInfo): JSONObject = JSONObject().apply {
        put("id", ext.id)
        put("name", ext.name)
        put("version", ext.version)
        put("description", ext.description)
        put("enabled", ext.enabled)
        put("type", ext.type.name)
        put("iconPath", ext.iconPath ?: "")
        put("optionsPage", ext.optionsPage ?: "")
        put("permissions", JSONArray(ext.permissions))
        put("content_scripts", JSONArray().apply {
            ext.contentScripts.forEach { cs ->
                put(JSONObject().apply {
                    put("matches", JSONArray(cs.matches))
                    put("exclude_matches", JSONArray(cs.excludeMatches))
                    put("js", JSONArray(cs.js))
                    put("css", JSONArray(cs.css))
                    put("run_at", cs.runAt)
                })
            }
        })
    }

    private fun deserialize(obj: JSONObject): ExtensionInfo {
        val csArray = obj.optJSONArray("content_scripts") ?: JSONArray()
        val iconPathStr = obj.optString("iconPath", "")
        val optionsPageStr = obj.optString("optionsPage", "")
        return ExtensionInfo(
            id = obj.getString("id"),
            name = obj.getString("name"),
            version = obj.getString("version"),
            description = obj.optString("description", ""),
            enabled = obj.optBoolean("enabled", true),
            type = try {
                ExtensionType.valueOf(obj.optString("type", "CRX"))
            } catch (_: Exception) { ExtensionType.CRX },
            permissions = toList(obj.optJSONArray("permissions")),
            iconPath = iconPathStr.ifEmpty { null },
            optionsPage = optionsPageStr.ifEmpty { null },
            contentScripts = (0 until csArray.length()).map { i ->
                val cs = csArray.getJSONObject(i)
                ContentScript(
                    matches = toList(cs.optJSONArray("matches")),
                    excludeMatches = toList(cs.optJSONArray("exclude_matches")),
                    js = toList(cs.optJSONArray("js")),
                    css = toList(cs.optJSONArray("css")),
                    runAt = cs.optString("run_at", "document_idle")
                )
            }
        )
    }

    private fun toList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull {
            try { array.getString(it) } catch (_: Exception) { null }
        }
    }
}
