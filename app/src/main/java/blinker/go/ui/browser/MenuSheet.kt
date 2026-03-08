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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
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
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onShare: () -> Unit,
    onFindInPage: () -> Unit,
    onDesktopMode: () -> Unit,
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
            MenuItem(
                icon = Icons.Rounded.Add,
                text = "New Tab"
            ) { onNewTab(); onDismiss() }

            MenuItem(
                icon = Icons.Rounded.Share,
                text = "Share"
            ) { onShare(); onDismiss() }

            MenuItem(
                icon = Icons.Rounded.Search,
                text = "Find in Page"
            ) { onFindInPage(); onDismiss() }

            MenuItem(
                icon = if (isDesktopMode) Icons.Rounded.PhoneAndroid
                       else Icons.Rounded.DesktopWindows,
                text = if (isDesktopMode) "Mobile Site" else "Desktop Site"
            ) { onDesktopMode(); onDismiss() }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp)
            )

            MenuItem(
                icon = Icons.Rounded.Close,
                text = "Close All Tabs"
            ) { onCloseAllTabs(); onDismiss() }
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
