package com.lordhck.btcwallet

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

private const val SEED_NEW = "seed-new"
private const val SYNC_GAP_MS = 28_000L
private const val FEE_GAP_MS = 600_000L
private const val PRICE_GAP_MS = 900_000L

// What the screens show, kept in step with the open Chain
class WalletModel(context: Context) {
    private val dir = context.applicationContext.filesDir
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Two lanes. Wallet work is local and quick and takes turns on one thread.
    // Anything that talks to a server waits on the other, so it can never hold
    // up an address, a QR code or the screen.
    private val worker = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val network = Executors.newCachedThreadPool().asCoroutineDispatcher()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile
    private var chain: Chain? = null
    private var words: List<String>? = null
    private var syncing = false
    private var lastSync = 0L
    private var lastPrice = 0L
    private var lastFees = 0L
    private var misses = 0

    var net by mutableStateOf(Net.values().firstOrNull { it.name == prefs.getString("net", null) } ?: Net.Main)
        private set
    var sats by mutableLongStateOf(0L)
        private set
    var payments by mutableStateOf(emptyList<Payment>())
        private set
    var synced by mutableStateOf(false)
        private set
    // Why the server can't be reached, null when all is well
    var offline by mutableStateOf<String?>(null)
        private set

    // Last known price, so USD shows right away
    var price by mutableStateOf(prefs.getString("price", null)?.toDoubleOrNull())
        private set

    // Fetched ahead of time, so Send is ready the moment it opens
    var rates by mutableStateOf<List<Long>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var currency by mutableStateOf(prefs.getString("currency", "USD") ?: "USD")
        private set
    var unit by mutableStateOf(prefs.getString("unit", "BTC") ?: "BTC")
        private set
    var hideBalance by mutableStateOf(prefs.getBoolean("hide-balance", false))
        private set
    var screenshotProtection by mutableStateOf(prefs.getBoolean("screenshot-protection", true))
        private set
    var customServer by mutableStateOf(prefs.getString(serverKey(), null))
        private set

    private fun note(text: String) = Debug.note(text)

    // For the debug report: state only, no amounts
    fun details(): List<String> = listOf(
        "network: " + net.label,
        "server: " + if (customServer == null) "default" else "custom",
        "wallet open: " + (chain != null),
        "seed made here: " + prefs.getBoolean(SEED_NEW, false),
        "full scan done: " + Net.values().joinToString { it.label + " " + prefs.getBoolean(scannedKey(it), false) },
        "synced: $synced, syncing: $syncing, failures in a row: $misses",
        "last sync: " + if (lastSync == 0L) "never" else "${(SystemClock.elapsedRealtime() - lastSync) / 1000}s ago",
        "server problem: " + (offline ?: "none"),
        "wallet error: " + (error ?: "none"),
        "transactions: " + payments.size,
        "price known: " + (price != null),
        "currency: $currency, unit: $unit",
        "hide balance: $hideBalance, screenshot protection: $screenshotProtection",
        "wallet files: " + (dir.listFiles { file: File -> file.name.startsWith("wallet-") }
            ?.joinToString { it.name + " " + it.length() / 1024 + " KB" } ?: "none"),
    )

    private fun why(e: Exception) = Debug.why(e)

    // Run something on the open wallet. Throws if the call fails.
    suspend fun <T> use(block: (Chain) -> T): T = withContext(worker) { block(checkNotNull(chain)) }

    // The same for calls that only talk to the server
    suspend fun <T> online(block: (Chain) -> T): T = withContext(network) { block(checkNotNull(chain)) }

    // isNew: true for a seed made here, false for a restored one, null on unlock.
    // A seed made here has no history anywhere, so it never needs a scan.
    fun open(seed: List<String>, isNew: Boolean?) {
        note("wallet open, seed new: $isNew, saved as new: ${prefs.getBoolean(SEED_NEW, false)}")
        if (isNew != null) prefs.edit().putBoolean(SEED_NEW, isNew).apply()
        words = seed
        load()
    }

    fun close() {
        note("wallet closed")
        words = null
        reset()
        scope.launch { withContext(worker) { chain = null } }
    }

    fun switch(to: Net) {
        if (to == net) return
        note("network ${net.label} -> ${to.label}")
        net = to
        prefs.edit().putString("net", to.name).apply()
        customServer = prefs.getString(serverKey(), null)
        reset()
        load()
    }

    fun chooseCurrency(value: String) {
        note("currency $value, price known: ${price != null}")
        currency = value
        prefs.edit().putString("currency", value).apply()
        if (price == null) loadPrice()
    }

    fun chooseUnit(value: String) {
        note("unit $value")
        unit = value
        prefs.edit().putString("unit", value).apply()
    }

    fun chooseHidden(value: Boolean) {
        note("hide balance $value")
        hideBalance = value
        prefs.edit().putBoolean("hide-balance", value).apply()
    }

    fun chooseScreenshotProtection(value: Boolean) {
        note("screenshot protection $value")
        screenshotProtection = value
        prefs.edit().putBoolean("screenshot-protection", value).apply()
    }

    // An amount in the chosen Bitcoin unit
    fun amount(value: Long): String =
        if (unit == "sats") Money.sats(value) else Money.btc(value) + " " + net.coin

    // Empty goes back to the default server for this network
    fun chooseServer(url: String) {
        val value = url.trim().trimEnd('/').ifEmpty { null }
        note("server set to ${if (value == null) "default" else "custom"}")
        customServer = value
        prefs.edit().putString(serverKey(), value).apply()
        reset()
        load()
    }

    // Removes this seed's history on every network
    fun erase() {
        note("wallet erased")
        close()
        prefs.edit().apply { Net.values().forEach { remove(scannedKey(it)) } }.remove(SEED_NEW).apply()
        scope.launch {
            withContext(worker) { Net.values().forEach { deleteFiles(it) } }
        }
    }

    // Lives here so leaving the screen can't lose track of a transaction.
    // A failure may mean the network dropped after the transaction went out.
    fun send(draft: Draft, done: (Boolean) -> Unit) {
        scope.launch {
            val ok = try {
                val open = checkNotNull(chain)
                note("signing")
                val tx = withContext(worker) { open.sign(draft) }
                note("broadcasting")
                withContext(network) { open.broadcast(tx) }
                withContext(worker) { open.record(tx) }
                show()
                note("broadcast ok")
                true
            } catch (e: Exception) {
                note("broadcast failed: " + why(e))
                false
            }
            done(ok)
            refresh(force = true)
        }
    }

    // Force is for when something changed. Otherwise this keeps server use low,
    // however often Home is opened.
    fun refresh(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPrice > PRICE_GAP_MS) loadPrice()
        // Home can get here first while the wallet is still opening
        loadFees()
        val open = chain
        if (open == null) {
            note("sync skipped: wallet not open yet")
            return
        }
        // waits longer after each failure, up to about 4 minutes
        val gap = SYNC_GAP_MS shl minOf(misses, 3)
        if (syncing) {
            note("sync skipped: one is still running")
            return
        }
        if (!force && lastSync > 0 && now - lastSync < gap) {
            note("sync skipped: last try ${(now - lastSync) / 1000}s ago, waiting ${gap / 1000}s, misses $misses")
            return
        }
        syncing = true
        lastSync = now
        val target = net
        val full = needsScan(target)
        scope.launch {
            val started = SystemClock.elapsedRealtime()
            val took = { (SystemClock.elapsedRealtime() - started) / 1000.0 }
            try {
                withContext(network) { open.pickServer() }
                val scan = withContext(worker) { open.scanner(full) }
                note("${target.label} ${if (full) "full scan" else "sync"}: " + withContext(worker) { open.status() })
                if (scan == null) note("nothing to ask the server yet")
                if (scan != null) {
                    // a blocking call can't be cancelled, so it runs outside the
                    // timeout's scope and a stuck server is simply left behind
                    val job = scope.async(network) { scan() }
                    scope.launch {
                        while (job.isActive) {
                            delay(10_000)
                            if (job.isActive) note("still waiting on the server, ${took()}s")
                        }
                    }
                    val update = withTimeoutOrNull(if (full) 180_000L else 60_000L) { job.await() }
                    if (update == null) job.cancel()
                    checkNotNull(update) { "timed out" }
                    withContext(worker) { open.absorb(update) }
                    if (full) prefs.edit().putBoolean(scannedKey(target), true).apply()
                    note("done in ${took()}s: " + withContext(worker) { open.status() })
                }
                // a sync that outlived a network switch must not touch the new screen
                if (open === chain) {
                    show()
                    synced = true
                    misses = 0
                    offline = null
                }
            } catch (e: Exception) {
                note("failed after ${took()}s: " + why(e))
                open.distrust()
                if (open === chain) {
                    // one failed request isn't worth showing
                    misses++
                    if (misses > 1) offline = (e.message ?: e.javaClass.simpleName).take(80)
                }
            }
            if (open === chain) syncing = false
        }
    }

    // Force is for when Send opens with no fees yet
    fun loadFees(force: Boolean = false) {
        val open = chain ?: return
        val now = SystemClock.elapsedRealtime()
        if (lastFees > 0 && now - lastFees < FEE_GAP_MS && !(force && rates == null)) return
        lastFees = now
        scope.launch {
            try {
                val fresh = withContext(network) { open.feeRates() }
                note("fees: $fresh sat/vB")
                if (open === chain) rates = fresh
            } catch (e: Exception) {
                open.distrust()
                note("fees failed: " + why(e))
            }
        }
    }

    private fun loadPrice() {
        // counts tries, not successes, so a dead server is left alone for 15 minutes
        lastPrice = SystemClock.elapsedRealtime()
        scope.launch {
            val fresh = try {
                note("price: asking Coinbase")
                withContext(network) { fetchPrice() }.also { note("price: got $it") }
            } catch (e: Exception) {
                note("price failed: " + why(e))
                return@launch
            }
            price = fresh
            prefs.edit().putString("price", fresh.toString()).apply()
        }
    }

    private fun needsScan(target: Net) =
        !prefs.getBoolean(SEED_NEW, false) && !prefs.getBoolean(scannedKey(target), false)

    private fun scannedKey(target: Net) = "scanned-${target.name}"

    private fun load() {
        val seed = words ?: return
        val target = net
        val apis = customServer?.let { listOf(it) } ?: listOfNotNull(target.api, target.backup)
        synced = !needsScan(target)
        scope.launch {
            try {
                note("opening ${target.label}, scan needed: ${needsScan(target)}")
                withContext(worker) { chain = Chain(dir, seed, target, apis) }
                syncing = false
                error = null
                show()
                refresh(force = true)
                loadFees()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
                note("wallet failed to open: " + why(e))
            }
        }
    }

    private suspend fun show() {
        val (balance, list) = withContext(worker) {
            val open = chain
            (open?.balance() ?: 0L) to (open?.payments() ?: emptyList())
        }
        note("showing ${list.size} transactions")
        sats = balance
        payments = list
    }

    private fun reset() {
        sats = 0L
        payments = emptyList()
        synced = false
        rates = null
        lastFees = 0L
        offline = null
        misses = 0
        error = null
    }

    private fun serverKey() = "server-${net.name}"

    private fun deleteFiles(net: Net) {
        dir.listFiles { file: File -> file.name.startsWith(Chain.fileName(net)) }?.forEach { it.delete() }
    }

    private fun fetchPrice(): Double = try {
        JSONObject(get("https://api.coinbase.com/v2/prices/BTC-USD/spot"))
            .getJSONObject("data").getString("amount").toDouble()
    } catch (e: Exception) {
        note("price: Coinbase failed, trying Kraken: " + why(e))
        JSONObject(get("https://api.kraken.com/0/public/Ticker?pair=XBTUSD"))
            .getJSONObject("result").getJSONObject("XXBTZUSD").getJSONArray("c").getString(0).toDouble()
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
}
