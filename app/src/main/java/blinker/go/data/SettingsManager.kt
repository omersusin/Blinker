package blinker.go.data

import android.content.Context

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("blinker_settings", Context.MODE_PRIVATE)

    fun load(): BlinkerSettings {
        return BlinkerSettings(
            searchEngine = try {
                SearchEngine.valueOf(prefs.getString("search_engine", "GOOGLE") ?: "GOOGLE")
            } catch (_: Exception) {
                SearchEngine.GOOGLE
            },
            homepageUrl = prefs.getString("homepage_url", "https://www.google.com")
                ?: "https://www.google.com",
            javaScriptEnabled = prefs.getBoolean("javascript_enabled", true),
            blockPopups = prefs.getBoolean("block_popups", true),
            doNotTrack = prefs.getBoolean("do_not_track", false),
            themeMode = try {
                ThemeMode.valueOf(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM")
            } catch (_: Exception) {
                ThemeMode.SYSTEM
            },
            downloadChunks = prefs.getInt("download_chunks", 4),
            maxConcurrentDownloads = prefs.getInt("max_concurrent_downloads", 3)
        )
    }

    fun save(settings: BlinkerSettings) {
        prefs.edit().apply {
            putString("search_engine", settings.searchEngine.name)
            putString("homepage_url", settings.homepageUrl)
            putBoolean("javascript_enabled", settings.javaScriptEnabled)
            putBoolean("block_popups", settings.blockPopups)
            putBoolean("do_not_track", settings.doNotTrack)
            putString("theme_mode", settings.themeMode.name)
            putInt("download_chunks", settings.downloadChunks)
            putInt("max_concurrent_downloads", settings.maxConcurrentDownloads)
            apply()
        }
    }
}
