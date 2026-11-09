package com.lordhck.btcwallet

import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import android.system.Os
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lordhck.btcwallet.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // BDK's HTTP client (minreq) has no timeout and waits forever on a dead
        // connection. It reads this on every request as a deadline in seconds.
        // Set once, before any wallet thread exists, since setenv isn't thread safe.
        if (System.getenv("MINREQ_TIMEOUT") == null) {
            runCatching { Os.setenv("MINREQ_TIMEOUT", "20", true) }
        }
        Debug.start(this)
        val debug = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        // debug builds only: in a release this would skip the PIN
        val start = if (debug) intent.getStringExtra("screen") else null
        setContent { App(start) }
    }
}
