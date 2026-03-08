package blinker.go.data.download

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

enum class DownloadState {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED
}

class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val fileName: String,
    val mimeType: String,
    val userAgent: String = "",
    val cookies: String? = null
) {
    var state by mutableStateOf(DownloadState.QUEUED)
    var totalBytes by mutableStateOf(0L)
    var downloadedBytes by mutableStateOf(0L)
    var speed by mutableStateOf(0L)
    var chunks by mutableStateOf(1)
    var error by mutableStateOf<String?>(null)
    var savedUri by mutableStateOf<Uri?>(null)
    val timestamp: Long = System.currentTimeMillis()

    val progress: Float
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
}
