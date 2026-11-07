package com.lordhck.btcwallet

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

// Everything the app does, newest last. Kept on disk so a crash or a restart
// doesn't lose it. Users attach this to bug reports, so never log the seed, the
// PIN, keys, addresses, amounts or a custom server's address.
object Debug {
    private const val KEEP = 3000

    // Server errors can quote a script hash or an address, either one identifies a wallet
    private val SENSITIVE = Regex("\\b[0-9a-fA-F]{40,}\\b|\\b(bc1|tb1|BC1|TB1)[0-9a-zA-Z]{20,}\\b|\\b[13mn2][1-9A-HJ-NP-Za-km-z]{25,34}\\b")

    val lines = mutableStateListOf<String>()
    private val main = Handler(Looper.getMainLooper())
    private val disk = Executors.newSingleThreadExecutor()
    private val clock = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private var file: File? = null

    fun start(context: Context) {
        if (file != null) return
        val log = File(context.filesDir, "debug.log")
        file = log
        val old = if (log.exists()) log.readLines().takeLast(KEEP) else emptyList()
        log.writeText(old.joinToString("") { it + "\n" })
        lines.addAll(old)

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { log.appendText(stamp("CRASH on ${thread.name}: " + e.stackTraceToString()) + "\n") }
            previous?.uncaughtException(thread, e)
        }
        note("app started, version ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.SDK_INT}")
    }

    fun note(text: String) {
        val line = stamp(text.replace(SENSITIVE, "<hidden>"))
        main.post {
            lines.add(line)
            if (lines.size > KEEP) lines.removeAt(0)
        }
        disk.execute { runCatching { file?.appendText(line + "\n") } }
    }

    fun why(e: Throwable): String {
        val cause = e.cause?.let { " / caused by " + it.javaClass.simpleName + ": " + it.message } ?: ""
        return e.javaClass.name.substringAfterLast('.') + ": " + (e.message ?: "no details") + cause
    }

    private fun stamp(text: String) = synchronized(clock) { clock.format(Date()) } + "  " + text
}
