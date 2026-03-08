package blinker.go.ui.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

class TabInfo(
    val id: String = UUID.randomUUID().toString(),
    initialUrl: String = "https://www.google.com"
) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf("New Tab")
    var isSecure by mutableStateOf(initialUrl.startsWith("https"))
    var isLoading by mutableStateOf(true)
    var progress by mutableIntStateOf(0)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
}
