package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ai.configureAppCheck
import com.example.core.datastore.UserPreferences
import com.example.navigation.RRAppRoot
import com.example.ui.theme.RRReminderTheme

class MainActivity : ComponentActivity() {
    private var widgetDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { configureAppCheck(this, intent) }
        runCatching { enableEdgeToEdge() }
        widgetDestination = runCatching { intent?.getStringExtra("navigate_to") }.getOrNull()

        val container = (application as? RRReminderApp)?.container
            ?: com.example.core.di.AppContainer(applicationContext)

        setContent {
            val prefs by container.effectivePreferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences())

            RRReminderTheme(
                themeMode = prefs.themeMode,
                accentPreset = prefs.accentPreset,
            ) {
                RRAppRoot(
                    container = container,
                    initialDestination = widgetDestination,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        widgetDestination = runCatching { intent.getStringExtra("navigate_to") }.getOrNull()
    }
}
