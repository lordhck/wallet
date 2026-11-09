package com.lordhck.btcwallet

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
// Fast while something is on its way, slow otherwise. Each sync costs about one
// request per address the wallet has handed out.
private const val SYNC_FAST_MS = 28_000L
private const val SYNC_IDLE_MS = 300_000L

// How long Receive keeps syncs fast after it shows an address
private const val WATCH_MS = 600_000L

// Sending… stays at least this long in Activity, however fast the broadcast
private const val SENDING_MIN_MS = 1_500L
// Fees are only fetched when Send opens, at most once a minute
private const val FEE_GAP_MS = 60_000L
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
    private var watchUntil = 0L

    // Counts trips to the background. Android cuts a background app's network,
    // always with battery saver on, so a sync that fails across one says nothing
    // about the server. Seen in the 2026-11-09 log: a connection abort and a DNS
    // failure within seconds of ON_STOP, each counted as a server failure.
    private var stops = 0

    // The last sync's server call. A blocking call can't be cancelled, so a new
    // sync waits for it to end instead of adding requests on top.
    private var inFlight: Job? = null

    var net by mutableStateOf(Net.values().firstOrNull { it.name == prefs.getString("net", null) } ?: Net.Main)
        private set
    var sats by mutableLongStateOf(0L)
        private set
    var payments by mutableStateOf(emptyList<Payment>())
        private set
    // A send that is signed but not broadcast yet. Apart from payments, so a sync can't drop it.
    var outgoing by mutableStateOf<Payment?>(null)
        private set

    // What Activity shows: a send in progress on top, then everything the wallet knows
    val activity: List<Payment>
        get() = listOfNotNull(outgoing) + payments.filterNot { it.id == outgoing?.id }
    var synced by mutableStateOf(false)
        private set
    // A sync has finished since the wallet opened, so what Home shows is current
    var ready by mutableStateOf(false)
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
        "synced: $synced, ready: $ready, syncing: $syncing, failures in a row: $misses",
        "request timeout: " + (System.getenv("MINREQ_TIMEOUT")?.let { it + "s" } ?: "none"),
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
        shown = fromSettings()
        if (price == null) loadPrice()
    }

    fun chooseUnit(value: String) {
        note("unit $value")
        unit = value
        prefs.edit().putString("unit", value).apply()
        shown = fromSettings()
    }

    // How amounts show right now: "USD", "BTC" or "sats". Tapping the balance moves
    // it on, and Activity, the transaction card, Send and review all follow. Settings
    // aren't changed, and changing one there starts over from it.
    var shown by mutableStateOf(fromSettings())
        private set

    private fun fromSettings() = if (currency == "USD") "USD" else unit

    fun display(next: String) {
        note("amounts shown in $next")
        shown = next
    }

    private fun usd(value: Long): String? = price?.takeIf { net == Net.Main }?.let { Money.usd(value, it) }

    // In the Bitcoin unit from Settings
    private fun coin(value: Long): String =
        if (unit == "sats") Money.sats(value) else Money.btc(value) + " " + net.coin

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

    // An amount the way amounts show right now. USD falls back to the Bitcoin unit
    // where there's no price, like on test networks.
    fun amount(value: Long): String = when (shown) {
        "USD" -> usd(value) ?: coin(value)
        "sats" -> Money.sats(value)
        else -> Money.btc(value) + " " + net.coin
    }

    // The smaller line under an amount: USD, or the Bitcoin amount when USD is the main one
    fun alt(value: Long): String? {
        val dollars = usd(value)
        return if (shown == "USD" && dollars != null) coin(value) else dollars
    }

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
    // signed runs once the transaction is signed and shows in Activity as Sending
    fun send(draft: Draft, signed: () -> Unit, done: (Boolean) -> Unit) {
        scope.launch {
            val ok = try {
                val open = checkNotNull(chain)
                note("signing")
                val tx = withContext(worker) { open.sign(draft) }
                val row = withContext(worker) { open.sending(tx) }
                if (open === chain) outgoing = row
                val shown = SystemClock.elapsedRealtime()
                signed()
                note("broadcasting")
                withContext(network) { open.broadcast(tx) }
                withContext(worker) { open.record(tx) }
                note("broadcast ok")
                show()
                // a broadcast often takes under a second, too short to see Sending…
                delay(maxOf(0L, SENDING_MIN_MS - (SystemClock.elapsedRealtime() - shown)))
                outgoing = null
                true
            } catch (e: Exception) {
                note("broadcast failed: " + why(e))
                // the next sync shows it if it went out after all
                outgoing = null
                false
            }
            done(ok)
            refresh(force = true)
        }
    }

    fun stopped() {
        stops++
    }

    // Receive shows an address, so a payment may be coming
    fun watch() {
        watchUntil = SystemClock.elapsedRealtime() + WATCH_MS
    }

    // Force is for when something changed. Otherwise this keeps server use low,
    // however often Home is opened.
    fun refresh(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPrice > PRICE_GAP_MS) loadPrice()
        // Home can get here first while the wallet is still opening
        val open = chain
        if (open == null) {
            note("sync skipped: wallet not open yet")
            return
        }
        // waits longer after each failure, up to about 4 minutes
        // not ready yet, like a restore whose scan failed, also counts as pending
        val pending = !ready || outgoing != null || payments.any { it.confirmations == 0 } || now < watchUntil
        val gap = maxOf(if (pending) SYNC_FAST_MS else SYNC_IDLE_MS, SYNC_FAST_MS shl minOf(misses, 3))
        if (syncing) {
            note("sync skipped: one is still running")
            return
        }
        // isActive turns false on cancel while the thread is still blocked, isCompleted doesn't
        if (inFlight?.isCompleted == false) {
            note("sync skipped: the last server call hasn't ended")
            return
        }
        if (!force && lastSync > 0 && now - lastSync < gap) {
            note("sync skipped: last try ${(now - lastSync) / 1000}s ago, waiting ${gap / 1000}s, misses $misses")
            return
        }
        syncing = true
        lastSync = now
        val trip = stops
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
                    inFlight = job
                    scope.launch {
                        while (job.isActive) {
                            delay(10_000)
                            if (job.isActive) note("still waiting on the server, ${took()}s")
                        }
                    }
                    // each request ends within MINREQ_TIMEOUT, this is only a backstop
                    val update = withTimeoutOrNull(if (full) 600_000L else 180_000L) { job.await() }
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
                    ready = true
                    misses = 0
                    offline = null
                }
            } catch (e: Exception) {
                if (stops != trip) {
                    note("failed after ${took()}s with the app in the background, not counted: " + why(e))
                    // runs again as soon as the app is back
                    if (open === chain) lastSync = 0L
                } else {
                    note("failed after ${took()}s: " + why(e))
                    open.distrust(e)
                    if (open === chain) {
                        // one failed request isn't worth showing, a rate limit is
                        misses++
                        if (e is RateLimited) {
                            offline = "The servers aren't answering. Trying again in a few minutes."
                        } else if (misses > 1) {
                            offline = (e.message ?: e.javaClass.simpleName).take(80)
                        }
                    }
                }
            }
            if (open === chain) syncing = false
        }
    }

    // Called when Send opens. The last rates stay on screen until new ones arrive.
    fun loadFees() {
        val open = chain ?: return
        val now = SystemClock.elapsedRealtime()
        if (lastFees > 0 && now - lastFees < FEE_GAP_MS) return
        lastFees = now
        scope.launch {
            try {
                val fresh = withContext(network) { open.feeRates() }
                note("fees: $fresh sat/vB")
                if (open === chain) rates = fresh
            } catch (e: Exception) {
                open.distrust(e)
                note("fees failed: " + why(e))
                // opening Send again may try again
                if (open === chain) lastFees = 0L
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
        outgoing = null
        synced = false
        ready = false
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
