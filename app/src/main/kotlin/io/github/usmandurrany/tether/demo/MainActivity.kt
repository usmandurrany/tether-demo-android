package io.github.usmandurrany.tether.demo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.usmandurrany.tether.demo.ui.navigation.TetherNavHost
import io.github.usmandurrany.tether.demo.ui.theme.TetherTheme

/** The only activity: everything on screen is Compose, navigated by [TetherNavHost]. */
class MainActivity : ComponentActivity() {
    /** A chat a notification asked to open, until the navigation has opened it. */
    private var pendingChat by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingChat = intent.getStringExtra(EXTRA_PEER)
        val app = application as DemoApp
        setContent {
            TetherTheme {
                TetherNavHost(app, pendingChat, onPendingChatHandled = { pendingChat = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_PEER)?.let { pendingChat = it }
    }

    companion object {
        /** The chat to open, as a bare JID: set by message notifications. */
        const val EXTRA_PEER = "peer"
    }
}
