package blinker.go.data.extension

data class ContentScript(
    val matches: List<String>,
    val excludeMatches: List<String> = emptyList(),
    val js: List<String> = emptyList(),
    val css: List<String> = emptyList(),
    val runAt: String = "document_idle"
)

data class ExtensionInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val contentScripts: List<ContentScript> = emptyList(),
    val enabled: Boolean = true,
    val type: ExtensionType = ExtensionType.CRX,
    val permissions: List<String> = emptyList(),
    val iconPath: String? = null,
    val optionsPage: String? = null,
    val backgroundScripts: List<String> = emptyList(),
    val backgroundPage: String? = null
) {
    val hasBackground: Boolean
        get() = backgroundScripts.isNotEmpty() || backgroundPage != null
}

enum class ExtensionType(val label: String) {
    CRX("Chrome Extension"),
    XPI("Firefox Add-on")
}
