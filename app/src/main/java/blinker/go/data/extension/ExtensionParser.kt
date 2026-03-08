package blinker.go.data.extension

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.ZipInputStream

object ExtensionParser {

    fun parse(context: Context, uri: Uri): ExtensionInfo? {
        val bytes: ByteArray
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            bytes = stream.use { it.readBytes() }
        } catch (_: Exception) {
            return null
        }

        val type = detectType(bytes) ?: return null
        val zipBytes = extractZipBytes(bytes, type) ?: return null

        val id = UUID.randomUUID().toString().take(12)
        val extDir = File(context.filesDir, "extensions/$id")

        try {
            extDir.mkdirs()
            var manifestJson: String? = null

            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name
                        val safeFile = File(extDir, name)
                        if (!safeFile.canonicalPath.startsWith(extDir.canonicalPath)) {
                            zis.closeEntry()
                            entry = zis.nextEntry
                            continue
                        }
                        safeFile.parentFile?.mkdirs()
                        safeFile.outputStream().use { out -> zis.copyTo(out) }
                        if (name.equals("manifest.json", ignoreCase = true)) {
                            manifestJson = safeFile.readText()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val manifest = manifestJson?.let { JSONObject(it) } ?: run {
                extDir.deleteRecursively()
                return null
            }

            val messages = loadMessages(manifest, extDir)
            return buildInfo(manifest, id, type, messages, extDir)
        } catch (_: Exception) {
            extDir.deleteRecursively()
            return null
        }
    }

    private fun loadMessages(
        manifest: JSONObject,
        extDir: File
    ): Map<String, String> {
        val localesDir = File(extDir, "_locales")
        if (!localesDir.exists()) return emptyMap()

        val defaultLocale = manifest.optString("default_locale", "")
        val candidates = mutableListOf<String>()
        if (defaultLocale.isNotEmpty()) candidates.add(defaultLocale)
        candidates.addAll(listOf("en", "en_US", "en_GB"))

        var messagesFile: File? = null
        for (locale in candidates) {
            val f = File(localesDir, "$locale/messages.json")
            if (f.exists()) {
                messagesFile = f
                break
            }
        }

        if (messagesFile == null) {
            localesDir.listFiles()?.firstOrNull { it.isDirectory }?.let { dir ->
                val f = File(dir, "messages.json")
                if (f.exists()) messagesFile = f
            }
        }

        if (messagesFile == null) return emptyMap()

        return try {
            val json = JSONObject(messagesFile!!.readText())
            val map = mutableMapOf<String, String>()
            json.keys().forEach { key ->
                val msgObj = json.optJSONObject(key)
                val message = msgObj?.optString("message", "") ?: ""
                if (message.isNotEmpty()) {
                    map[key.lowercase()] = message
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun resolveI18n(text: String, messages: Map<String, String>): String {
        if (!text.contains("__MSG_")) return text
        val regex = Regex("__MSG_(\\w+)__")
        return regex.replace(text) { match ->
            val key = match.groupValues[1].lowercase()
            messages[key] ?: match.value
        }
    }

    private fun findBestIcon(manifest: JSONObject, extDir: File): String? {
        val iconsObj = manifest.optJSONObject("icons")
        if (iconsObj != null) {
            val preferred = listOf("128", "96", "64", "48", "32", "16")
            for (size in preferred) {
                val path = iconsObj.optString(size, "")
                if (path.isNotEmpty()) {
                    val file = File(extDir, path)
                    if (file.exists()) return path
                }
            }
            iconsObj.keys().forEach { key ->
                val path = iconsObj.optString(key, "")
                if (path.isNotEmpty()) {
                    val file = File(extDir, path)
                    if (file.exists()) return path
                }
            }
        }

        val action = manifest.optJSONObject("browser_action")
            ?: manifest.optJSONObject("action")
        if (action != null) {
            val actionIcons = action.optJSONObject("default_icon")
            if (actionIcons != null) {
                actionIcons.keys().forEach { key ->
                    val path = actionIcons.optString(key, "")
                    if (path.isNotEmpty()) {
                        val file = File(extDir, path)
                        if (file.exists()) return path
                    }
                }
            }
            val singleIcon = action.optString("default_icon", "")
            if (singleIcon.isNotEmpty()) {
                val file = File(extDir, singleIcon)
                if (file.exists()) return singleIcon
            }
        }

        return null
    }

    /**
     * Find options page from manifest.
     * Chrome: options_page or options_ui.page
     * Firefox: options_ui.page
     */
    private fun findOptionsPage(manifest: JSONObject, extDir: File): String? {
        // options_ui.page (modern)
        val optionsUi = manifest.optJSONObject("options_ui")
        if (optionsUi != null) {
            val page = optionsUi.optString("page", "")
            if (page.isNotEmpty()) {
                val file = File(extDir, page)
                if (file.exists()) return page
            }
        }

        // options_page (legacy)
        val optionsPage = manifest.optString("options_page", "")
        if (optionsPage.isNotEmpty()) {
            val file = File(extDir, optionsPage)
            if (file.exists()) return optionsPage
        }

        return null
    }

    private fun detectType(bytes: ByteArray): ExtensionType? {
        if (bytes.size < 4) return null
        if (bytes[0] == 0x43.toByte() && bytes[1] == 0x72.toByte() &&
            bytes[2] == 0x32.toByte() && bytes[3] == 0x34.toByte()
        ) {
            return ExtensionType.CRX
        }
        if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            return ExtensionType.XPI
        }
        return null
    }

    private fun extractZipBytes(bytes: ByteArray, type: ExtensionType): ByteArray? {
        return when (type) {
            ExtensionType.XPI -> bytes
            ExtensionType.CRX -> extractCrxZip(bytes)
        }
    }

    private fun extractCrxZip(bytes: ByteArray): ByteArray? {
        if (bytes.size < 12) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(4)
        val version = buf.int
        return when (version) {
            2 -> {
                if (bytes.size < 16) return null
                val pubKeyLen = buf.int
                val sigLen = buf.int
                val offset = 16 + pubKeyLen + sigLen
                if (offset >= bytes.size) null
                else bytes.copyOfRange(offset, bytes.size)
            }
            3 -> {
                val headerLen = buf.int
                val offset = 12 + headerLen
                if (offset >= bytes.size) null
                else bytes.copyOfRange(offset, bytes.size)
            }
            else -> null
        }
    }

    private fun buildInfo(
        manifest: JSONObject,
        id: String,
        type: ExtensionType,
        messages: Map<String, String>,
        extDir: File
    ): ExtensionInfo {
        val rawName = manifest.optString("name", "Unknown Extension")
        val rawDesc = manifest.optString("description", "")
        val version = manifest.optString("version", "0.0")

        val name = resolveI18n(rawName, messages)
        val description = resolveI18n(rawDesc, messages)
        val iconPath = findBestIcon(manifest, extDir)
        val optionsPage = findOptionsPage(manifest, extDir)

        val contentScripts = mutableListOf<ContentScript>()
        manifest.optJSONArray("content_scripts")?.let { csArray ->
            for (i in 0 until csArray.length()) {
                val cs = csArray.getJSONObject(i)
                contentScripts.add(
                    ContentScript(
                        matches = toList(cs.optJSONArray("matches")),
                        excludeMatches = toList(cs.optJSONArray("exclude_matches")),
                        js = toList(cs.optJSONArray("js")),
                        css = toList(cs.optJSONArray("css")),
                        runAt = cs.optString("run_at", "document_idle")
                    )
                )
            }
        }

        return ExtensionInfo(
            id = id,
            name = name,
            version = version,
            description = description,
            contentScripts = contentScripts,
            type = type,
            permissions = toList(manifest.optJSONArray("permissions")),
            iconPath = iconPath,
            optionsPage = optionsPage
        )
    }

    private fun toList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull {
            try { array.getString(it) } catch (_: Exception) { null }
        }
    }
}
