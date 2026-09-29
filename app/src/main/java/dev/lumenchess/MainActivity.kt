package dev.lumenchess

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dev.lumenchess.background.BackgroundWork
import dev.lumenchess.player.lichess.LichessAuth
import dev.lumenchess.ui.LumenChessApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LumenChessApp() }
        handleOAuthRedirect(intent)
        // Background sync / cleanup schedules follow the saved settings.
        lifecycleScope.launch { runCatching { BackgroundWork.reschedule(applicationContext) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthRedirect(intent)
    }

    private fun handleOAuthRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (!LichessAuth.isCallback(uri)) return
        lifecycleScope.launch {
            val error = LichessAuth.complete(applicationContext, uri)
            Toast.makeText(this@MainActivity, error ?: "Signed in with Lichess", Toast.LENGTH_SHORT).show()
        }
    }
}
