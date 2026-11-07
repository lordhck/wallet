package com.lordhck.btcwallet

import org.bitcoindevkit.Address
import org.bitcoindevkit.Amount
import org.bitcoindevkit.ChainPosition
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.EsploraClient
import org.bitcoindevkit.FeeRate
import org.bitcoindevkit.FullScanScriptInspector
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Psbt
import org.bitcoindevkit.Script
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
import kotlin.math.abs
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
        "https://blockstream.info/tx/", "https://mempool.emzy.de/api",
    ),
    Signet(
        "Signet", Network.SIGNET, "https://blockstream.info/signet/api", "sBTC",
        "https://blockstream.info/signet/tx/", "https://mempool.space/signet/api",
    ),
    Testnet(
        "Testnet", Network.TESTNET4, "https://blockstream.info/testnet4/api", "tBTC",
        "https://blockstream.info/testnet4/tx/", "https://mempool.emzy.de/testnet4/api",
    ),
}

private const val PROBE_MS = 5_000
private const val TRUST_MS = 60_000L

// sats is what the wallet gained or lost. For a payment to yourself that's only the fee.
class Payment(val id: String, val incoming: Boolean, val sats: Long, val time: Long?, val self: Boolean)

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
                }
            }
            val request = wallet.startFullScan().inspectSpksForAllKeychains(inspector).build()
            val server = client
            return { server.fullScan(request, 20uL, 2uL) }
        }
        val unused = wallet.derivationIndex(KeychainKind.EXTERNAL) == null &&
            wallet.derivationIndex(KeychainKind.INTERNAL) == null
        if (unused) return null
        val request = wallet.startSyncWithRevealedSpks().build()
        val server = client
        return { server.sync(request, 2uL) }
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

    // Esplora calls wait forever on a dead connection, so ask each server first
    // with a short timeout. Runs on the network lane. Sticks with one that works.
    fun pickServer() {
        if (SystemClock.elapsedRealtime() - lastAnswer < TRUST_MS) return
        for (step in servers.indices) {
            val index = (active + step) % servers.size
            if (reachable(servers[index])) {
                active = index
                lastAnswer = SystemClock.elapsedRealtime()
                return
            }
        }
        error("no server answered")
    }

    // A call failed, so the next one should check again
    fun distrust() {
        lastAnswer = 0L
    }

    private fun reachable(server: String): Boolean {
        val name = server.takeIf { it == net.api || it == net.backup } ?: "custom"
        val connection = URL("$server/blocks/tip/height").openConnection() as HttpURLConnection
        connection.connectTimeout = PROBE_MS
        connection.readTimeout = PROBE_MS
        return try {
            val ok = connection.responseCode == 200
            Debug.note("server $name answered ${connection.responseCode}")
            ok
        } catch (e: Exception) {
            Debug.note("server $name failed: " + Debug.why(e))
            false
        } finally {
            connection.disconnect()
        }
    }

    fun absorb(update: Update) {
        wallet.applyUpdate(update)
        wallet.persist(persister)
        Debug.note("update saved")
    }

    fun balance(): Long = wallet.balance().total.toSat().toLong()

    fun payments(): List<Payment> = wallet.transactions()
        .map { tx ->
            val values = wallet.sentAndReceived(tx.transaction)
            val sent = values.sent.toSat().toLong()
            val received = values.received.toSat().toLong()
            val time = (tx.chainPosition as? ChainPosition.Confirmed)
                ?.confirmationBlockTime?.confirmationTime?.toLong()
            val self = sent > 0 && tx.transaction.output().all { wallet.isMine(it.scriptPubkey) }
            Payment(tx.transaction.computeTxid().toString(), received >= sent, abs(received - sent), time, self)
        }
        .sortedByDescending { it.time ?: Long.MAX_VALUE }

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
        val estimates = client.getFeeEstimates()
        Debug.note("fees: got ${estimates.size} estimates")
        val rates = listOf(6, 3, 1).map { blocks ->
            maxOf(1L, ceil(estimates[blocks.toUShort()] ?: 1.0).toLong())
        }
        // test coins have no value, and small faucet amounts can't pay real estimates
        return if (net == Net.Main) rates else listOf(1L, 1L, rates.last())
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
        wallet.applyUnconfirmedTxs(listOf(UnconfirmedTx(tx, (System.currentTimeMillis() / 1000).toULong())))
        wallet.persist(persister)
    }

    companion object {
        fun fileName(net: Net) = "wallet-${net.name}.sqlite"
    }
}
