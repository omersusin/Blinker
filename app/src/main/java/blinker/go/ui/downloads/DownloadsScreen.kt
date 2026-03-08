package blinker.go.ui.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import blinker.go.data.download.DownloadEngine
import blinker.go.data.download.DownloadState
import blinker.go.data.download.DownloadTask
import java.util.Locale

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.1f GB", gb)
}

private fun fileIcon(mime: String): ImageVector = when {
    mime.startsWith("image/") -> Icons.Rounded.Image
    mime.startsWith("video/") -> Icons.Rounded.VideoFile
    mime.startsWith("audio/") -> Icons.Rounded.AudioFile
    mime == "application/pdf" -> Icons.Rounded.PictureAsPdf
    mime.contains("zip") || mime.contains("compress") -> Icons.Rounded.FolderZip
    else -> Icons.Rounded.InsertDriveFile
}

@Composable
fun DownloadsScreen(
    engine: DownloadEngine,
    onDismiss: () -> Unit
) {
    val tasks = engine.tasks

    BackHandler { onDismiss() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
            Text(
                text = "Downloads",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            val hasFinished = tasks.any {
                it.state == DownloadState.COMPLETED || it.state == DownloadState.CANCELLED
            }
            if (hasFinished) {
                IconButton(onClick = { engine.clearCompleted() }) {
                    Icon(Icons.Rounded.DeleteSweep, "Clear completed")
                }
            }
        }

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Download, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No downloads yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp, vertical = 8.dp
                )
            ) {
                items(tasks, key = { it.id }) { task ->
                    DownloadItemCard(task, engine)
                }
            }
        }
    }
}

@Composable
private fun DownloadItemCard(task: DownloadTask, engine: DownloadEngine) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            val iconTint = when (task.state) {
                DownloadState.COMPLETED -> MaterialTheme.colorScheme.primary
                DownloadState.FAILED -> MaterialTheme.colorScheme.error
                DownloadState.CANCELLED -> MaterialTheme.colorScheme.onSurface.copy(0.3f)
                else -> MaterialTheme.colorScheme.onSurface.copy(0.6f)
            }
            Icon(
                imageVector = fileIcon(task.mimeType),
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .padding(top = 2.dp),
                tint = iconTint
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                when (task.state) {
                    DownloadState.QUEUED -> {
                        Text(
                            "Queued...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                    }

                    DownloadState.DOWNLOADING -> {
                        LinearProgressIndicator(
                            progress = { task.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Row {
                            val pct = (task.progress * 100).toInt()
                            Text(
                                "$pct%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (task.totalBytes > 0) {
                                Text(
                                    " · ${formatBytes(task.downloadedBytes)}/${formatBytes(task.totalBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                                )
                            }
                            if (task.speed > 0) {
                                Text(
                                    " · ${formatBytes(task.speed)}/s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                                )
                            }
                        }
                        if (task.chunks > 1) {
                            Text(
                                "${task.chunks} chunks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary.copy(0.6f)
                            )
                        }
                    }

                    DownloadState.PAUSED -> {
                        LinearProgressIndicator(
                            progress = { task.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Paused · ${formatBytes(task.downloadedBytes)}/${formatBytes(task.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    DownloadState.COMPLETED -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CheckCircle, null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Completed · ${formatBytes(task.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    DownloadState.FAILED -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Error, null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                task.error ?: "Failed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    DownloadState.CANCELLED -> {
                        Text(
                            "Cancelled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                        )
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (task.state) {
                    DownloadState.DOWNLOADING -> {
                        IconButton(
                            onClick = { engine.pauseDownload(task.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Rounded.Pause, "Pause", Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { engine.cancelDownload(task.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Cancel, "Cancel",
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error.copy(0.7f)
                            )
                        }
                    }

                    DownloadState.PAUSED -> {
                        IconButton(
                            onClick = { engine.resumeDownload(task.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow, "Resume",
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { engine.cancelDownload(task.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Cancel, "Cancel",
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error.copy(0.7f)
                            )
                        }
                    }

                    DownloadState.COMPLETED -> {
                        IconButton(
                            onClick = { engine.openFile(task) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.FolderOpen, "Open",
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { engine.removeTask(task.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Cancel, "Remove",
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                            )
                        }
                    }

                    DownloadState.FAILED -> {
                        IconButton(
                            onClick = { engine.resumeDownload(task.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Refresh, "Retry",
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { engine.removeTask(task.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Cancel, "Remove",
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error.copy(0.7f)
                            )
                        }
                    }

                    DownloadState.QUEUED, DownloadState.CANCELLED -> {
                        IconButton(
                            onClick = { engine.removeTask(task.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Cancel, "Remove",
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}
