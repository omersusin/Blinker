package blinker.go.ui.settings

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import blinker.go.data.BlinkerSettings
import blinker.go.data.HistoryManager
import blinker.go.data.SearchEngine
import blinker.go.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: BlinkerSettings,
    onSettingsChange: (BlinkerSettings) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var showSearchEngine by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showHomepage by remember { mutableStateOf(false) }
    var showChunks by remember { mutableStateOf(false) }
    var showConcurrent by remember { mutableStateOf(false) }
    var showClearData by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ── General ──
            item { SectionHeader("General") }
            item {
                SettingItem(
                    icon = Icons.Rounded.Search,
                    title = "Search Engine",
                    subtitle = settings.searchEngine.label
                ) { showSearchEngine = true }
            }
            item {
                SettingItem(
                    icon = Icons.Rounded.Home,
                    title = "Homepage",
                    subtitle = settings.homepageUrl
                        .removePrefix("https://")
                        .removePrefix("http://")
                ) { showHomepage = true }
            }

            // ── Appearance ──
            item { SectionHeader("Appearance") }
            item {
                SettingItem(
                    icon = Icons.Rounded.DarkMode,
                    title = "Theme",
                    subtitle = settings.themeMode.label
                ) { showTheme = true }
            }

            // ── Privacy & Security ──
            item { SectionHeader("Privacy & Security") }
            item {
                SwitchSettingItem(
                    icon = Icons.Rounded.Code,
                    title = "JavaScript",
                    subtitle = "Allow websites to run JavaScript",
                    checked = settings.javaScriptEnabled
                ) { onSettingsChange(settings.copy(javaScriptEnabled = it)) }
            }
            item {
                SwitchSettingItem(
                    icon = Icons.Rounded.Block,
                    title = "Block Pop-ups",
                    subtitle = "Prevent pop-up windows",
                    checked = settings.blockPopups
                ) { onSettingsChange(settings.copy(blockPopups = it)) }
            }
            item {
                SwitchSettingItem(
                    icon = Icons.Rounded.VisibilityOff,
                    title = "Do Not Track",
                    subtitle = "Request websites not to track you",
                    checked = settings.doNotTrack
                ) { onSettingsChange(settings.copy(doNotTrack = it)) }
            }
            item {
                SettingItem(
                    icon = Icons.Rounded.DeleteSweep,
                    title = "Clear Browsing Data",
                    subtitle = "History, cookies, cache"
                ) { showClearData = true }
            }

            // ── Downloads ──
            item { SectionHeader("Downloads") }
            item {
                SettingItem(
                    icon = Icons.Rounded.Storage,
                    title = "Parallel Connections",
                    subtitle = "${settings.downloadChunks} chunks per download"
                ) { showChunks = true }
            }
            item {
                SettingItem(
                    icon = Icons.Rounded.Download,
                    title = "Simultaneous Downloads",
                    subtitle = "Max ${settings.maxConcurrentDownloads} at once"
                ) { showConcurrent = true }
            }

            // ── About ──
            item { SectionHeader("About") }
            item {
                SettingItem(
                    icon = Icons.Rounded.Info,
                    title = "Blinker Browser",
                    subtitle = "Version 1.0.0 (Build 1)"
                ) { }
            }
        }
    }

    // ── Dialogs ──

    if (showSearchEngine) {
        SingleChoiceDialog(
            title = "Search Engine",
            options = SearchEngine.values().map { it.label },
            selectedIndex = SearchEngine.values().indexOf(settings.searchEngine),
            onSelect = { index ->
                onSettingsChange(settings.copy(searchEngine = SearchEngine.values()[index]))
                showSearchEngine = false
            },
            onDismiss = { showSearchEngine = false }
        )
    }

    if (showTheme) {
        SingleChoiceDialog(
            title = "Theme",
            options = ThemeMode.values().map { it.label },
            selectedIndex = ThemeMode.values().indexOf(settings.themeMode),
            onSelect = { index ->
                onSettingsChange(settings.copy(themeMode = ThemeMode.values()[index]))
                showTheme = false
            },
            onDismiss = { showTheme = false }
        )
    }

    if (showHomepage) {
        HomepageDialog(
            currentUrl = settings.homepageUrl,
            onSave = { url ->
                val finalUrl = if (url.startsWith("http://") || url.startsWith("https://"))
                    url else "https://$url"
                onSettingsChange(settings.copy(homepageUrl = finalUrl))
                showHomepage = false
            },
            onDismiss = { showHomepage = false }
        )
    }

    if (showChunks) {
        val chunkOptions = listOf(1, 2, 4, 8)
        SingleChoiceDialog(
            title = "Parallel Connections",
            options = chunkOptions.map { "$it chunk${if (it > 1) "s" else ""}" },
            selectedIndex = chunkOptions.indexOf(settings.downloadChunks).coerceAtLeast(0),
            onSelect = { index ->
                onSettingsChange(settings.copy(downloadChunks = chunkOptions[index]))
                showChunks = false
            },
            onDismiss = { showChunks = false }
        )
    }

    if (showConcurrent) {
        val concurrentOptions = listOf(1, 2, 3, 5)
        SingleChoiceDialog(
            title = "Simultaneous Downloads",
            options = concurrentOptions.map { "$it download${if (it > 1) "s" else ""}" },
            selectedIndex = concurrentOptions.indexOf(settings.maxConcurrentDownloads).coerceAtLeast(0),
            onSelect = { index ->
                onSettingsChange(settings.copy(maxConcurrentDownloads = concurrentOptions[index]))
                showConcurrent = false
            },
            onDismiss = { showConcurrent = false }
        )
    }

    if (showClearData) {
        ClearDataDialog(
            onDismiss = { showClearData = false },
            onClear = { history, cookies, cache ->
                if (history) HistoryManager(context).clear()
                if (cookies) {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                }
                if (cache) {
                    try { WebStorage.getInstance().deleteAllData() } catch (_: Exception) { }
                    context.cacheDir.deleteRecursively()
                }
                showClearData = false
                Toast.makeText(context, "Browsing data cleared", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ── Helper Composables ──

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SwitchSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) }
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun HomepageDialog(
    currentUrl: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Homepage") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(url) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ClearDataDialog(
    onDismiss: () -> Unit,
    onClear: (history: Boolean, cookies: Boolean, cache: Boolean) -> Unit
) {
    var clearHistory by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(true) }
    var clearCache by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear Browsing Data") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { clearHistory = !clearHistory }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = clearHistory, onCheckedChange = { clearHistory = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Browsing History")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { clearCookies = !clearCookies }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = clearCookies, onCheckedChange = { clearCookies = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Cookies & Site Data")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { clearCache = !clearCache }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = clearCache, onCheckedChange = { clearCache = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Cached Images & Files")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onClear(clearHistory, clearCookies, clearCache) }) {
                Text("Clear", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
