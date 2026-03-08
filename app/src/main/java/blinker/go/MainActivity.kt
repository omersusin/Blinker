package blinker.go

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import blinker.go.data.SettingsManager
import blinker.go.ui.browser.BrowserScreen
import blinker.go.ui.settings.SettingsScreen
import blinker.go.ui.theme.BlinkerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsManager = remember { SettingsManager(this@MainActivity) }
            var settings by remember { mutableStateOf(settingsManager.load()) }
            var showSettings by remember { mutableStateOf(false) }

            BlinkerTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        BrowserScreen(
                            settings = settings,
                            onOpenSettings = { showSettings = true }
                        )

                        if (showSettings) {
                            SettingsScreen(
                                settings = settings,
                                onSettingsChange = {
                                    settings = it
                                    settingsManager.save(it)
                                },
                                onBack = { showSettings = false }
                            )
                        }
                    }
                }
            }
        }
    }
}
