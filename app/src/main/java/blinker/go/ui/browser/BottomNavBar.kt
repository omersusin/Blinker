package blinker.go.ui.browser

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BottomNavBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val disabledAlpha = 0.3f

            IconButton(
                onClick = onBack,
                enabled = canGoBack,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Geri",
                    tint = if (canGoBack) MaterialTheme.colorScheme.onSurface
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
                )
            }

            IconButton(
                onClick = onForward,
                enabled = canGoForward,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "İleri",
                    tint = if (canGoForward) MaterialTheme.colorScheme.onSurface
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
                )
            }

            IconButton(
                onClick = onHome,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = "Ana Sayfa",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isLoading) Icons.Rounded.Close
                                  else Icons.Rounded.Refresh,
                    contentDescription = if (isLoading) "Durdur" else "Yenile",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = { /* TODO: Menü */ },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Menü",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
