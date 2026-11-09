package com.lordhck.btcwallet

import org.bitcoindevkit.Address
import org.bitcoindevkit.Amount
import org.bitcoindevkit.ChainPosition
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.EsploraClient
import org.bitcoindevkit.EsploraException
import org.bitcoindevkit.FeeRate
import org.bitcoindevkit.FullScanScriptInspector
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Psbt
import org.bitcoindevkit.Script
import org.bitcoindevkit.SyncScriptInspector
import org.bitcoindevkit.Transaction
import org.bitcoindevkit.TxBuilder
import org.bitcoindevkit.UnconfirmedTx
import org.bitcoindevkit.Update
import org.bitcoindevkit.Wallet
import android.os.SystemClock
import java.io.File
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject
import kotlin.math.ceil

enum class Net(
    val label: String,
    val bdk: Network,
    val api: String,
    val coin: String,
    val explorer: String,
    val backup: String? = null,
) {
    Main(
        "Mainnet", Network.BITCOIN, "https://blockstream.info/api", "BTC",
        "https://mempool.space/tx/", "https://mempool.space/api",
    ),
    Signet(
        "Signet", Network.SIGNET, "https://blockstream.info/signet/api", "sBTC",
        "https://mempool.space/signet/tx/", "https://mempool.space/signet/api",
    ),
    Testnet(
        "Testnet", Network.TESTNET4, "https://blockstream.info/testnet4/api", "tBTC",
        "https://mempool.space/testnet4/tx/", "https://mempool.space/testnet4/api",
    ),
}

private const val PROBE_MS = 5_000
private const val TRUST_MS = 60_000L

// How long a server that answered 429 Too Many Requests is left alone
private const val REST_MS = 600_000L

// How long a server that stopped answering mid-sync is left alone at first.
// Doubles with each stall in a row, up to an hour.
private const val STALL_REST_MS = 300_000L
private const val STALL_REST_MAX_MS = 3_600_000L

// Which servers are resting, by host. Kept for the whole app, not per Chain:
// a new Chain opens on every unlock and network switch, and the 2026-11-09 log
// showed each unlock asking the stalled mempool.space first again and waiting
// 20 s on it. By host, because mempool.space stalled on Mainnet and Signet alike.
private object Rest {
    private val until = ConcurrentHashMap<String, Long>()
    private val stalls = ConcurrentHashMap<String, Int>()

    private fun host(server: String) = runCatching { URL(server).host }.getOrDefault(server)

    fun resting(server: String, now: Long) = (until[host(server)] ?: 0L) > now

    fun limited(server: String, now: Long) {
        until[host(server)] = now + REST_MS
    }

    // Returns how long it rests
    fun stalled(server: String, now: Long): Long {
        val count = stalls.merge(host(server), 1, Int::plus) ?: 1
        val rest = minOf(STALL_REST_MAX_MS, STALL_REST_MS shl minOf(count - 1, 4))
        until[host(server)] = now + rest
        return rest
    }

    fun answered(server: String) {
        stalls.remove(host(server))
    }
}

// Time between address requests. BDK has no rate setting, but it asks the
// inspector about each address right before requesting it, so a pause there
// spaces the requests out. Seen in the 2026-11-08 log: 2.5 requests a second
// made mempool.space stop answering.
private const val PACE_MS = 1_000L

class RateLimited : Exception("every server is resting after errors")

// BDK retries a 429 six times by itself, then hands it to us
fun rateLimited(e: Throwable) =
    e is RateLimited || (e is EsploraException.HttpResponse && e.status.toInt() == 429)

// How long a sent transaction is kept when the server hasn't listed it yet
private const val KEEP_SECONDS = 600L

// A payment counts as final after this many blocks
const val FINAL_CONFIRMATIONS = 6

// sats is what was received, or what was sent without the fee. For a payment to yourself
// it's only the fee. fee is only known for sends. address is where a send went, or which
// of ours received it. time is the block time, seen when the server last saw it unconfirmed.
// confirmations is 0 until it is in a block. sending is true while it is still on its way to the server.
class Payment(
    val id: String,
    val incoming: Boolean,
    val sats: Long,
    val time: Long?,
    val self: Boolean,
    val confirmations: Int,
    val fee: Long?,
    val address: String?,
    val seen: Long?,
    val sending: Boolean = false,
)

class Receiving(val address: String, val uri: String)

class Draft(val psbt: Psbt, val to: String, val sats: Long, val fee: Long)

object Money {
    fun btc(sats: Long): String = BigDecimal(sats).movePointLeft(8).setScale(8).toPlainString()

    fun sats(sats: Long): String = String.format(Locale.US, "%,d sats", sats)

    fun usd(sats: Long, price: Double): String =
        String.format(Locale.US, "$%,.2f", sats / 100_000_000.0 * price)

    // Sats for text typed in BTC, sats or USD, or null if it isn't a positive amount
    fun parse(text: String, unit: String, price: Double?): Long? = runCatching {
        val value = BigDecimal(text.trim().replace(',', '.'))
        val sats = when (unit) {
            "sats" -> value
            "USD" -> value.divide(BigDecimal(checkNotNull(price)), 8, java.math.RoundingMode.DOWN).movePointRight(8)
            else -> value.movePointRight(8)
        }
        sats.toBigInteger().longValueExact()
    }.getOrNull()?.takeIf { it > 0 }
}

// One BDK wallet on one network. Calls block, and a wallet is not safe to share
// between threads, so WalletModel keeps every call on a single thread.
class Chain(dir: File, words: List<String>, private val net: Net, apis: List<String>) {
    private val servers = apis
    private val clients = apis.map { EsploraClient(it) }

    @Volatile
    private var active = 0

    @Volatile
    private var lastAnswer = 0L

    private val client get() = clients[active]
    private val persister: Persister
    private val wallet: Wallet

    // Sent from here and not in a block yet, with when it was sent. See keepSent().
    private val sentHere = mutableMapOf<String, Pair<Transaction, Long>>()

    init {
        val key = DescriptorSecretKey(net.bdk, Mnemonic.fromString(words.joinToString(" ")), null)
        val external = Descriptor.newBip84(key, KeychainKind.EXTERNAL, net.bdk)
        val change = Descriptor.newBip84(key, KeychainKind.INTERNAL, net.bdk)
        val file = File(dir, fileName(net))
        val existed = file.exists()
        persister = Persister.newSqlite(file.path)
        wallet = if (existed) {
            Wallet.load(external, change, persister)
        } else {
            Wallet(external, change, net.bdk, persister)
        }
        wallet.persist(persister)
    }

    // Returns the network part, for the network lane. A restored seed needs one
    // full scan, after that only known addresses are checked. Null when there is
    // nothing to ask the server yet, like a new wallet with no addresses.
    fun scanner(full: Boolean): (() -> Update)? {
        if (full) {
            val inspector = object : FullScanScriptInspector {
                override fun inspect(keychain: KeychainKind, index: UInt, script: Script) {
                    Debug.note("scan checking $keychain address #$index")
                    Thread.sleep(PACE_MS)
                }
            }
            val request = wallet.startFullScan().inspectSpksForAllKeychains(inspector).build()
            val server = client
            return { server.fullScan(request, 20uL, 1uL) }
        }
        val unused = wallet.derivationIndex(KeychainKind.EXTERNAL) == null &&
            wallet.derivationIndex(KeychainKind.INTERNAL) == null
        if (unused) return null
        val inspector = object : SyncScriptInspector {
            override fun inspect(script: Script, total: ULong) {
                Thread.sleep(PACE_MS)
            }
        }
        val request = wallet.startSyncWithRevealedSpks().inspectSpks(inspector).build()
        val server = client
        return { server.sync(request, 1uL) }
    }

    // For the debug log: never includes keys or addresses
    fun status(): String {
        val external = wallet.derivationIndex(KeychainKind.EXTERNAL)?.let { it.toLong() + 1 } ?: 0L
        val change = wallet.derivationIndex(KeychainKind.INTERNAL)?.let { it.toLong() + 1 } ?: 0L
        return "server ${serverName()}, addresses $external+$change, " +
            "height ${wallet.latestCheckpoint().height}, txs ${wallet.transactions().size}"
    }

    private fun serverName() =
        servers[active].takeIf { it == net.api || it == net.backup } ?: "custom"

    // Ask each server first with a short timeout. Runs on the network lane.
    // Sticks with one that works, and skips one that is resting after a 429.
    // Throws RateLimited without a request when every server is resting.
    // Synchronized so a sync and a fee request starting together probe once.
    @Synchronized
    fun pickServer() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAnswer < TRUST_MS && !Rest.resting(servers[active], now)) return
        var limited = false
        for (step in servers.indices) {
            val index = (active + step) % servers.size
            if (Rest.resting(servers[index], now)) {
                limited = true
                continue
            }
            when (probe(servers[index])) {
                200 -> {
                    active = index
                    lastAnswer = now
                    return
                }
                429 -> {
                    Rest.limited(servers[index], now)
                    limited = true
                }
            }
        }
        if (limited) throw RateLimited()
        error("no server answered")
    }

    // A call failed, so the next one should check again. After a 429 or a
    // request that got no answer, the server rests and the next call goes to the
    // other one. Its quick check still answers then, so it can't tell.
    fun distrust(e: Exception) {
        lastAnswer = 0L
        if (e is RateLimited) return
        val now = SystemClock.elapsedRealtime()
        val rest = when {
            rateLimited(e) -> REST_MS.also { Rest.limited(servers[active], now) }
            e is EsploraException.Minreq && "timeout" in e.errorMessage -> Rest.stalled(servers[active], now)
            else -> return
        }
        Debug.note("server ${serverName()} isn't answering, leaving it alone for ${rest / 60_000} min")
    }

    // The HTTP status, or -1 when there was no answer
    private fun probe(server: String): Int {
        val name = server.takeIf { it == net.api || it == net.backup } ?: "custom"
        val connection = URL("$server/blocks/tip/height").openConnection() as HttpURLConnection
        connection.connectTimeout = PROBE_MS
        connection.readTimeout = PROBE_MS
        return try {
            connection.responseCode.also { Debug.note("server $name answered $it") }
        } catch (e: Exception) {
            Debug.note("server $name failed: " + Debug.why(e))
            -1
        } finally {
            connection.disconnect()
        }
    }

    fun absorb(update: Update) {
        // a stall streak ends when the server delivers
        Rest.answered(servers[active])
        wallet.applyUpdate(update)
        keepSent()
        wallet.persist(persister)
        Debug.note("update saved")
    }

    // A sync drops an unconfirmed transaction that the server's address history doesn't
    // list. Right after a broadcast the server often hasn't indexed it yet, so it would
    // vanish for a minute. Seeing it again later than the drop brings it back. Gives up
    // after KEEP_SECONDS, in case it really never reached the network.
    private fun keepSent() {
        if (sentHere.isEmpty()) return
        val now = System.currentTimeMillis() / 1000
        val txs = wallet.transactions()
        val listed = txs.map { it.transaction.computeTxid().toString() }.toSet()
        val confirmed = txs.filter { it.chainPosition is ChainPosition.Confirmed }
            .map { it.transaction.computeTxid().toString() }.toSet()
        sentHere.entries.removeAll { (id, sent) -> id in confirmed || now - sent.second > KEEP_SECONDS }
        val dropped = sentHere.filterKeys { it !in listed }.values
        if (dropped.isEmpty()) return
        Debug.note("sync dropped ${dropped.size} sent transaction(s) the server hasn't listed yet, keeping them")
        // a drop at the same second still counts, so this has to be later than the sync
        wallet.applyUnconfirmedTxs(dropped.map { UnconfirmedTx(it.first, (now + 1).toULong()) })
    }

    fun balance(): Long = wallet.balance().total.toSat().toLong()

    fun payments(): List<Payment> {
        // each sync moves the tip forward, so the count grows with every new block
        val tip = wallet.latestCheckpoint().height.toLong()
        return wallet.transactions()
            .map { tx ->
                val block = (tx.chainPosition as? ChainPosition.Confirmed)?.confirmationBlockTime
                val confirmations = block?.let { maxOf(1L, tip - it.blockId.height.toLong() + 1).toInt() } ?: 0
                val seen = (tx.chainPosition as? ChainPosition.Unconfirmed)?.timestamp?.toLong()
                payment(tx.transaction, block?.confirmationTime?.toLong(), confirmations, seen)
            }
            .sortedByDescending { it.time ?: Long.MAX_VALUE }
    }

    // The row for a signed transaction that hasn't reached the server yet
    fun sending(tx: Transaction): Payment =
        payment(tx, null, 0, System.currentTimeMillis() / 1000, sending = true)

    private fun payment(tx: Transaction, time: Long?, confirmations: Int, seen: Long?, sending: Boolean = false): Payment {
        val values = wallet.sentAndReceived(tx)
        val sent = values.sent.toSat().toLong()
        val received = values.received.toSat().toLong()
        val outputs = tx.output()
        val incoming = received >= sent
        val self = sent > 0 && outputs.all { wallet.isMine(it.scriptPubkey) }
        // only our own inputs have known values, so only a send has a fee to work out
        val fee = if (sent > 0) runCatching { wallet.calculateFee(tx).toSat().toLong() }.getOrNull() else null
        val sats = when {
            incoming -> received - sent
            self || fee == null -> sent - received
            else -> sent - received - fee
        }
        val shown = if (incoming || self) {
            outputs.firstOrNull { wallet.isMine(it.scriptPubkey) }
        } else {
            outputs.firstOrNull { !wallet.isMine(it.scriptPubkey) }
        }
        val address = shown?.let { runCatching { Address.fromScript(it.scriptPubkey, net.bdk).toString() }.getOrNull() }
        return Payment(
            tx.computeTxid().toString(), incoming, sats, time, self, confirmations, fee, address, seen, sending,
        )
    }

    fun address(): Receiving = receiving(wallet.nextUnusedAddress(KeychainKind.EXTERNAL).address)

    fun newAddress(): Receiving = receiving(wallet.revealNextAddress(KeychainKind.EXTERNAL).address)

    private fun receiving(address: Address): Receiving {
        wallet.persist(persister)
        Debug.note("address ready, " + status())
        return Receiving(address.toString(), address.toQrUri())
    }

    // sat/vB for Slow, Medium and Fast, aiming for 6, 3 and 1 blocks
    fun feeRates(): List<Long> {
        pickServer()
        Debug.note("fees: asking ${serverName()}")
        val estimates = feeEstimates(servers[active])
        Debug.note("fees: got ${estimates.size} estimates")
        val rates = listOf(6, 3, 1).map { blocks ->
            maxOf(1L, ceil(estimates[blocks] ?: 1.0).toLong())
        }
        // test coins have no value, and small faucet amounts can't pay real estimates
        return if (net == Net.Main) rates else listOf(1L, 1L, rates.last())
    }

    // Not through BDK: its client only accepts status 200, and mempool.space
    // answers this one with 203 and the same JSON.
    private fun feeEstimates(server: String): Map<Int, Double> {
        val connection = URL("$server/fee-estimates").openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw EsploraException.HttpResponse(status.toUShort(), "fee estimates")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().readText())
            json.keys().asSequence().associate { it.toInt() to json.getDouble(it) }
        } finally {
            connection.disconnect()
        }
    }

    // Null sats sends everything. Nothing leaves the phone until send()
    fun draft(to: String, sats: Long?, feeRate: Long): Draft {
        val script = Address(to, net.bdk).scriptPubkey()
        val builder = TxBuilder().feeRate(FeeRate.fromSatPerVb(feeRate.toULong()))
        val psbt = if (sats == null) {
            builder.drainWallet().drainTo(script).finish(wallet)
        } else {
            builder.addRecipient(script, Amount.fromSat(sats.toULong())).finish(wallet)
        }
        val fee = psbt.fee().toLong()
        Debug.note("draft built at $feeRate sat/vB, send all: ${sats == null}")
        return Draft(psbt, to, sats ?: (balance() - fee), fee)
    }

    fun sign(draft: Draft): Transaction {
        check(wallet.sign(draft.psbt)) { "transaction was not fully signed" }
        return draft.psbt.extractTx()
    }

    fun broadcast(tx: Transaction) {
        pickServer()
        client.broadcast(tx)
    }

    fun record(tx: Transaction) {
        val now = System.currentTimeMillis() / 1000
        wallet.applyUnconfirmedTxs(listOf(UnconfirmedTx(tx, now.toULong())))
        sentHere[tx.computeTxid().toString()] = tx to now
        wallet.persist(persister)
    }

    companion object {
        fun fileName(net: Net) = "wallet-${net.name}.sqlite"
    }
}
