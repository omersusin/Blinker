package blinker.go.data

enum class SearchEngine(val label: String, val searchUrl: String, val homeUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q=", "https://www.google.com"),
    BING("Bing", "https://www.bing.com/search?q=", "https://www.bing.com"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=", "https://duckduckgo.com"),
    YAHOO("Yahoo", "https://search.yahoo.com/search?p=", "https://search.yahoo.com"),
    YANDEX("Yandex", "https://yandex.com/search/?text=", "https://yandex.com")
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

data class BlinkerSettings(
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val homepageUrl: String = "https://www.google.com",
    val javaScriptEnabled: Boolean = true,
    val blockPopups: Boolean = true,
    val doNotTrack: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val downloadChunks: Int = 4,
    val maxConcurrentDownloads: Int = 3
)
