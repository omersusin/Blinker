package blinker.go.data.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class DownloadEngine private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: DownloadEngine? = null

        fun getInstance(context: Context): DownloadEngine {
            return instance ?: synchronized(this) {
                instance ?: DownloadEngine(context.applicationContext).also {
                    instance = it
                }
            }
        }

        private const val CHUNK_THRESHOLD = 2L * 1024 * 1024
        private const val DEFAULT_CHUNKS = 4
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 15000
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val jobMap = mutableMapOf<String, Job>()

    val tasks = mutableStateListOf<DownloadTask>()

    fun startDownload(
        url: String,
        fileName: String,
        mimeType: String,
        userAgent: String,
        cookies: String?
    ) {
        val uniqueName = makeUniqueName(fileName)
        val task = DownloadTask(
            url = url, fileName = uniqueName,
            mimeType = mimeType, userAgent = userAgent, cookies = cookies
        )
        tasks.add(0, task)
        launchDownload(task)
    }

    fun pauseDownload(taskId: String) {
        jobMap[taskId]?.cancel()
        jobMap.remove(taskId)
        tasks.find { it.id == taskId }?.state = DownloadState.PAUSED
    }

    fun resumeDownload(taskId: String) {
        val task = tasks.find { it.id == taskId } ?: return
        if (task.state != DownloadState.PAUSED && task.state != DownloadState.FAILED) return
        task.state = DownloadState.QUEUED
        task.error = null
        launchDownload(task)
    }

    fun cancelDownload(taskId: String) {
        jobMap[taskId]?.cancel()
        jobMap.remove(taskId)
        tasks.find { it.id == taskId }?.let {
            it.state = DownloadState.CANCELLED
            cleanupTemp(it)
        }
    }

    fun removeTask(taskId: String) {
        jobMap[taskId]?.cancel()
        jobMap.remove(taskId)
        tasks.find { it.id == taskId }?.let { cleanupTemp(it) }
        tasks.removeAll { it.id == taskId }
    }

    fun clearCompleted() {
        tasks.filter {
            it.state == DownloadState.COMPLETED || it.state == DownloadState.CANCELLED
        }.forEach { cleanupTemp(it) }
        tasks.removeAll {
            it.state == DownloadState.COMPLETED || it.state == DownloadState.CANCELLED
        }
    }

    fun openFile(task: DownloadTask) {
        val uri = task.savedUri ?: return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, task.mimeType)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No app to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchDownload(task: DownloadTask) {
        val job = scope.launch {
            try {
                performDownload(task)
            } catch (_: CancellationException) {
                // State set by pause/cancel
            } catch (e: Exception) {
                task.error = e.message ?: "Download failed"
                task.state = DownloadState.FAILED
            }
        }
        jobMap[task.id] = job
    }

    private suspend fun performDownload(task: DownloadTask) {
        task.state = DownloadState.DOWNLOADING

        val info = fetchFileInfo(task)
        task.totalBytes = info.contentLength

        val chunkDir = chunkDir(task).also { it.mkdirs() }

        if (info.supportsRange && info.contentLength > CHUNK_THRESHOLD) {
            task.chunks = DEFAULT_CHUNKS
            downloadChunked(task, chunkDir, info.contentLength)
        } else {
            task.chunks = 1
            downloadSingle(task, chunkDir)
        }

        val merged = mergeChunks(task, chunkDir)
        saveToDownloads(task, merged)
        cleanupTemp(task)
        task.state = DownloadState.COMPLETED
    }

    private data class FileInfo(val contentLength: Long, val supportsRange: Boolean)

    private fun fetchFileInfo(task: DownloadTask): FileInfo {
        val conn = openConnection(task.url, task)
        conn.requestMethod = "HEAD"
        conn.connect()
        val len = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
        val ranges = conn.getHeaderField("Accept-Ranges")
        conn.disconnect()
        return FileInfo(
            contentLength = len,
            supportsRange = ranges?.contains("bytes", true) == true && len > 0
        )
    }

    private suspend fun downloadChunked(
        task: DownloadTask, dir: File, total: Long
    ) = coroutineScope {
        val chunkSize = total / task.chunks
        val perChunk = LongArray(task.chunks)

        for (i in 0 until task.chunks) {
            File(dir, "chunk_$i").let { if (it.exists()) perChunk[i] = it.length() }
        }
        task.downloadedBytes = perChunk.sum()

        val speedJob = launch {
            var last = task.downloadedBytes
            while (isActive) {
                delay(1000)
                val now = task.downloadedBytes
                task.speed = now - last
                last = now
            }
        }

        val jobs = (0 until task.chunks).map { i ->
            async {
                val start = i * chunkSize
                val end = if (i == task.chunks - 1) total - 1 else start + chunkSize - 1
                val expected = end - start + 1
                val file = File(dir, "chunk_$i")
                val existing = if (file.exists()) file.length() else 0L
                if (existing >= expected) {
                    perChunk[i] = expected
                    task.downloadedBytes = perChunk.sum()
                    return@async
                }

                val conn = openConnection(task.url, task)
                conn.setRequestProperty("Range", "bytes=${start + existing}-$end")
                conn.connect()
                conn.inputStream.use { input ->
                    FileOutputStream(file, true).use { output ->
                        val buf = ByteArray(BUFFER_SIZE)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            ensureActive()
                            output.write(buf, 0, n)
                            perChunk[i] += n
                            task.downloadedBytes = perChunk.sum()
                        }
                    }
                }
                conn.disconnect()
            }
        }
        jobs.awaitAll()
        speedJob.cancel()
        task.speed = 0
    }

    private suspend fun downloadSingle(
        task: DownloadTask, dir: File
    ) = coroutineScope {
        val file = File(dir, "chunk_0")
        val existing = if (file.exists()) file.length() else 0L
        val conn = openConnection(task.url, task)
        if (existing > 0) conn.setRequestProperty("Range", "bytes=$existing-")
        conn.connect()

        if (task.totalBytes <= 0) {
            conn.getHeaderField("Content-Length")?.toLongOrNull()?.let {
                task.totalBytes = if (existing > 0) it + existing else it
            }
        }
        task.downloadedBytes = existing

        val speedJob = launch {
            var last = task.downloadedBytes
            while (isActive) {
                delay(1000)
                val now = task.downloadedBytes
                task.speed = now - last
                last = now
            }
        }

        conn.inputStream.use { input ->
            FileOutputStream(file, true).use { output ->
                val buf = ByteArray(BUFFER_SIZE)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    ensureActive()
                    output.write(buf, 0, n)
                    task.downloadedBytes += n
                }
            }
        }
        conn.disconnect()
        speedJob.cancel()
        task.speed = 0
    }

    private fun mergeChunks(task: DownloadTask, dir: File): File {
        val merged = File(dir, task.fileName)
        FileOutputStream(merged).use { out ->
            for (i in 0 until task.chunks) {
                val chunk = File(dir, "chunk_$i")
                if (chunk.exists()) {
                    FileInputStream(chunk).use { it.copyTo(out) }
                }
            }
        }
        return merged
    }

    private fun saveToDownloads(task: DownloadTask, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, task.fileName)
                put(MediaStore.Downloads.MIME_TYPE, task.mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: throw IOException("MediaStore insert failed")

            context.contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(file).use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            task.savedUri = uri
        } else {
            val dlDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            if (!dlDir.exists()) dlDir.mkdirs()
            val dest = File(dlDir, task.fileName)
            file.copyTo(dest, overwrite = true)
            task.savedUri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", dest
            )
        }
    }

    private fun openConnection(url: String, task: DownloadTask): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            if (task.userAgent.isNotEmpty()) {
                setRequestProperty("User-Agent", task.userAgent)
            }
            task.cookies?.let { setRequestProperty("Cookie", it) }
        }
    }

    private fun chunkDir(task: DownloadTask) = File(context.cacheDir, "dl_${task.id}")

    private fun cleanupTemp(task: DownloadTask) {
        chunkDir(task).let { dir ->
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
                dir.delete()
            }
        }
    }

    private fun makeUniqueName(name: String): String {
        val existing = tasks.map { it.fileName }.toSet()
        if (name !in existing) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot >= 0) name.substring(0, dot) else name
        val ext = if (dot >= 0) name.substring(dot) else ""
        var c = 1
        while ("${base}_$c$ext" in existing) c++
        return "${base}_$c$ext"
    }
}
