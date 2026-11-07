package com.lordhck.btcwallet

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// The file users attach to bug reports. It may end up public, so it must never
// hold the seed, PIN, keys, addresses, amounts or a custom server's address.
object Report {
    private const val BDK = "2.3.1"
    private const val MB = 1024L * 1024L

    fun fileName(): String =
        "btc-wallet-${BuildConfig.VERSION_NAME}-debug-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".log"

    fun write(context: Context, uri: Uri, wallet: List<String>, log: List<String>) {
        val text = build(context, wallet, log)
        checkNotNull(context.contentResolver.openOutputStream(uri)) { "can't open the file" }
            .use { it.write(text.toByteArray()) }
    }

    private fun build(context: Context, wallet: List<String>, log: List<String>) = buildString {
        fun section(title: String, lines: List<String>) {
            append("== ").append(title).append(" ==\n")
            lines.forEach { append(it).append('\n') }
            append('\n')
        }
        section("Report", report())
        section("App", app(context))
        section("Device", device(context))
        section("Network", network(context))
        section("Wallet", wallet)
        section("Log (${log.size} lines, oldest first)", log)
    }

    private fun report(): List<String> {
        val utc = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US)
        utc.timeZone = TimeZone.getTimeZone("UTC")
        return listOf(
            "created: " + utc.format(Date()),
            "time zone: " + TimeZone.getDefault().id,
            "language: " + Locale.getDefault().toLanguageTag(),
            "device uptime: ${SystemClock.elapsedRealtime() / 60_000} min",
        )
    }

    private fun app(context: Context): List<String> {
        val pm = context.packageManager
        val name = context.packageName
        val info = pm.getPackageInfo(name, 0)
        val dates = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return listOf(
            "package: $name",
            "version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            "build type: " + BuildConfig.BUILD_TYPE,
            "commit: " + BuildConfig.COMMIT,
            "apk sha256: " + fileHash(File(context.applicationInfo.sourceDir)),
            "signing cert sha256: " + signers(pm, name),
            "installed from: " + (pm.getInstallerPackageName(name) ?: "sideloaded"),
            "first installed: " + dates.format(Date(info.firstInstallTime)),
            "last updated: " + dates.format(Date(info.lastUpdateTime)),
            "bdk: $BDK",
        )
    }

    private fun device(context: Context): List<String> {
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val storage = StatFs(context.filesDir.path)
        val screen = context.resources.displayMetrics
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return listOf(
            "manufacturer: " + Build.MANUFACTURER,
            "brand: " + Build.BRAND,
            "model: " + Build.MODEL,
            "device: " + Build.DEVICE,
            "product: " + Build.PRODUCT,
            "hardware: " + Build.HARDWARE,
            "cpu types: " + Build.SUPPORTED_ABIS.joinToString(", "),
            "android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            "security patch: " + Build.VERSION.SECURITY_PATCH,
            "build: " + Build.DISPLAY,
            "screen: ${screen.widthPixels}x${screen.heightPixels} px, density ${screen.density}",
            "memory: ${memory.availMem / MB} MB free of ${memory.totalMem / MB} MB, low: ${memory.lowMemory}",
            "storage: ${storage.availableBytes / MB} MB free of ${storage.totalBytes / MB} MB",
            "battery saver: " + power.isPowerSaveMode,
        )
    }

    private fun network(context: Context): List<String> {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
            ?: return listOf("connection: none")
        val kinds = listOf(
            NetworkCapabilities.TRANSPORT_WIFI to "wifi",
            NetworkCapabilities.TRANSPORT_CELLULAR to "mobile",
            NetworkCapabilities.TRANSPORT_ETHERNET to "ethernet",
            NetworkCapabilities.TRANSPORT_VPN to "vpn",
        ).filter { caps.hasTransport(it.first) }.map { it.second }
        return listOf(
            "connection: " + kinds.joinToString(", "),
            "internet confirmed: " + caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            "metered: " + manager.isActiveNetworkMetered,
            "data saver status: " + manager.restrictBackgroundStatus,
            "speed estimate: ${caps.linkDownstreamBandwidthKbps} kbps down, ${caps.linkUpstreamBandwidthKbps} kbps up",
        )
    }

    private fun signers(pm: PackageManager, name: String): String {
        val certs = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo.apkContentsSigners
        } else {
            pm.getPackageInfo(name, PackageManager.GET_SIGNATURES).signatures
        }
        return certs.joinToString(", ") { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }
    }

    private fun fileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
