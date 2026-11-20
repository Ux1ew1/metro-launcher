package com.nostalgia.metro

import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MetroApp : Application() {
    val controller: LauncherController by lazy { LauncherController(this) }
}

class MainActivity : ComponentActivity() {
    private val ctl: LauncherController get() = (application as MetroApp).controller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) {
            // при старте плитки «въезжают» турникетом
            ctl.turnstile.hideImmediately()
            ctl.pendingIn = true
        }
        setContent { LauncherRoot(ctl) }
    }

    override fun onResume() {
        super.onResume()
        ctl.onResumed()
    }

    override fun onPause() {
        super.onPause()
        ctl.onPaused()
    }

    override fun onStop() {
        super.onStop()
        ctl.onStopped()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) ctl.onHome()
    }
}
