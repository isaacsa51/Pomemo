package com.serranoie.app.pomemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.serranoie.app.pomemo.ui.AppScreen
import com.serranoie.app.pomemo.ui.theme.PomemoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** A one-shot action from a Quick Settings tile, cleared once AppScreen has acted on it. */
    private var action by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Not on recreation (rotation, theme change): the action was already handled.
        if (savedInstanceState == null) action = intent?.action
        setContent { PomemoTheme { AppScreen(action = action, onActionHandled = { action = null }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        action = intent.action
    }

    companion object {
        const val ACTION_NEW_NOTE = "com.serranoie.app.pomemo.NEW_NOTE"
    }
}
