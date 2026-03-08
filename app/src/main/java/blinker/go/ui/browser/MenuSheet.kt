package blinker.go.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(
    isDesktopMode: Boolean,
    isBookmarked: Boolean,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onShare: () -> Unit,
    onFindInPage: () -> Unit,
    onDesktopMode: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    onCloseAllTabs: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            MenuItem(Icons.Rounded.Add, "New Tab") {
                onDismiss(); onNewTab()
            }
            MenuItem(
                icon = if (isBookmarked) Icons.Rounded.Bookmark
                       else Icons.Rounded.BookmarkBorder,
                text = if (isBookmarked) "Remove Bookmark"
                       else "Add Bookmark"
            ) { onDismiss(); onToggleBookmark() }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            MenuItem(Icons.Rounded.Star, "Bookmarks") {
                onDismiss(); onBookmarks()
            }
            MenuItem(Icons.Rounded.History, "History") {
                onDismiss(); onHistory()
            }
            MenuItem(Icons.Rounded.Download, "Downloads") {
                onDismiss(); onDownloads()
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            MenuItem(Icons.Rounded.Share, "Share") {
                onDismiss(); onShare()
            }
            MenuItem(Icons.Rounded.Search, "Find in Page") {
                onDismiss(); onFindInPage()
            }
            MenuItem(
                icon = if (isDesktopMode) Icons.Rounded.PhoneAndroid
                       else Icons.Rounded.DesktopWindows,
                text = if (isDesktopMode) "Mobile Site"
                       else "Desktop Site"
            ) { onDismiss(); onDesktopMode() }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            MenuItem(Icons.Rounded.Settings, "Settings") {
                onDismiss(); onSettings()
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            MenuItem(Icons.Rounded.Close, "Close All Tabs") {
                onDismiss(); onCloseAllTabs()
            }
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    text: String,
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
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
