package com.lordhck.btcwallet.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lordhck.btcwallet.Debug
import com.lordhck.btcwallet.Net
import com.lordhck.btcwallet.Report
import com.lordhck.btcwallet.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

const val VERSION = "0.1.0"

@Composable
fun Settings() {
    val nav = LocalNav.current
    val net = nav.wallet.net
    val name = if (net == Net.Main) "BTC Wallet" else "BTC Wallet ${net.label}"
    Scr {
        Bar("Settings")
        Body {
            Row(
                Modifier.padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 24.dp),
                Arrangement.spacedBy(18.dp),
                Alignment.CenterVertically,
            ) {
                Logo(76.dp)
                Column {
                    Txt(name, size = 25f, weight = 700)
                    Txt("Version $VERSION", Modifier.padding(top = 4.dp), C.dim, 13f, 500)
                }
            }
            Group {
                Go("Backup") { nav.show(S.Backup) }
                Go("Security", divider = true) { nav.show(S.Security) }
            }
            Gap()
            Group {
                Go("Currency", value = nav.wallet.currency) { nav.show(S.Currency) }
                Go("Bitcoin unit", value = if (nav.wallet.unit == "sats") "Sats" else "BTC", divider = true) {
                    nav.show(S.BtcUnit)
                }
            }
            Gap()
            Group { Go("Advanced") { nav.show(S.Advanced) } }
        }
    }
}

@Composable
fun Security() {
    val nav = LocalNav.current
    val context = LocalContext.current
    Scr {
        Bar("Security")
        Body {
            Big("Security")
            Group {
                Go("Change PIN") {
                    nav.newPinLength = null
                    nav.show(S.ChangePin)
                }
                Go("PIN length", value = "${Vault.pinLength(context)} digits", divider = true) {
                    nav.show(S.PinLength)
                }
            }
            Gap()
            Group {
                Cell("Unlock with biometrics", value = "Coming soon", off = true)
                Cell(
                    "Hide balance on start",
                    divider = true,
                    trailing = { Toggle(nav.wallet.hideBalance) },
                ) { nav.wallet.chooseHidden(!nav.wallet.hideBalance) }
                Cell(
                    "Screenshot protection",
                    divider = true,
                    trailing = { Toggle(nav.wallet.screenshotProtection) },
                ) { nav.wallet.chooseScreenshotProtection(!nav.wallet.screenshotProtection) }
            }
        }
    }
}

@Composable
fun PinLength() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val current = remember { Vault.pinLength(context) }
    Scr {
        Bar("PIN length")
        Body {
            Big("PIN length")
            Group {
                listOf(6, 8).forEachIndexed { i, n ->
                    Cell(
                        "$n digits",
                        divider = i > 0,
                        trailing = { Icon(Ic.check, 20.dp, C.accent, Modifier.alpha(if (n == current) 1f else 0f)) },
                    ) {
                        if (n != current) {
                            nav.newPinLength = n
                            nav.show(S.ChangePin)
                        }
                    }
                }
            }
            Note("Changing the length asks you to set a new PIN.")
        }
    }
}

@Composable
fun Currency() {
    val wallet = LocalNav.current.wallet
    Scr {
        Bar("Currency")
        Body {
            Big("Currency")
            val codes = listOf("USD", "BTC")
            Picks(listOf(Opt("USD", "US Dollar"), Opt("BTC", "Bitcoin")), codes.indexOf(wallet.currency)) {
                wallet.chooseCurrency(codes[it])
            }
            Note("Shown as your main balance. Tap the balance to switch at any time.")
        }
    }
}

@Composable
fun BitcoinUnit() {
    val wallet = LocalNav.current.wallet
    Scr {
        Bar("Bitcoin unit")
        Body {
            Big("Bitcoin unit")
            val units = listOf("BTC", "sats")
            Picks(listOf(Opt("BTC", "0.00125 BTC"), Opt("Sats", "125,000 sats")), units.indexOf(wallet.unit)) {
                wallet.chooseUnit(units[it])
            }
            Note("1 BTC = 100,000,000 sats.")
        }
    }
}

@Composable
private fun DeleteWallet() {
    val nav = LocalNav.current
    val context = LocalContext.current
    var armed by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    val fill by animateFloatAsState(
        if (holding) 1f else 0f,
        if (holding) tween(1500, easing = LinearEasing) else snap(),
        label = "hold",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (!armed) {
                        if (tryAwaitRelease()) armed = true
                        return@detectTapGestures
                    }
                    holding = true
                    val released = withTimeoutOrNull(1500) { tryAwaitRelease() }
                    holding = false
                    if (released == null) {
                        Vault.clear(context)
                        nav.words = null
                        nav.wallet.erase()
                        nav.root(S.Welcome)
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize()) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(C.red.copy(alpha = .2f)))
        }
        Txt(if (armed) "Hold to remove wallet" else "Delete wallet", color = C.red)
    }
}

@Composable
fun Advanced() {
    val nav = LocalNav.current
    Scr {
        Bar("Advanced")
        Body {
            Big("Advanced")
            Group {
                Go("Network", value = nav.wallet.net.label) { nav.show(S.Network) }
                Go("Address type", value = "Native SegWit", divider = true) { nav.show(S.AddrType) }
                Go("Server", value = if (nav.wallet.customServer == null) "Default" else "Custom", divider = true) {
                    nav.show(S.Server)
                }
            }
            Gap()
            Group { Cell("Replace-by-fee", value = "Coming soon", off = true) }
            Gap()
            Group { Go("Export debug log") { nav.pin(S.DebugLog) } }
            Gap()
            Group { DeleteWallet() }
            Note("Make sure your seed phrase is backed up before removing the wallet.")
        }
    }
}

// Goes straight to the system's save dialog. The log itself is never shown.
@Composable
fun DebugLog() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) {
            nav.back()
        } else {
            val wallet = nav.wallet.details()
            val log = Debug.lines.toList()
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { Report.write(context, uri, wallet, log) }
                    Debug.note("debug log exported")
                    nav.toast("Debug log saved")
                } catch (e: Exception) {
                    Debug.note("debug log export failed: " + Debug.why(e))
                    nav.toast("Couldn't save the debug log")
                }
                nav.back()
            }
        }
    }
    LaunchedEffect(Unit) { save.launch(Report.fileName()) }
    Scr {
        Bar("Export debug log")
        Body {
            Big("Debug log")
            Intro("Add this file when you report a bug. It has details about your phone and what the app did. It never has your seed, PIN, keys, addresses or amounts.")
        }
    }
}

@Composable
fun Network() {
    val nav = LocalNav.current
    Scr {
        Bar("Network")
        Body {
            Big("Network")
            Picks(
                listOf(
                    Opt("Mainnet", "Real bitcoin"),
                    Opt("Signet", "Recommended for testing"),
                    Opt("Testnet", "Testnet4 test coins"),
                ),
                Net.values().indexOf(nav.wallet.net),
            ) { nav.wallet.switch(Net.values()[it]) }
            Note("Each network is a separate wallet from the same seed phrase, with its own addresses and history. Test coins have no value.")
        }
    }
}

@Composable
fun AddrType() {
    Scr {
        Bar("Address type")
        Body {
            Big("Address type")
            Picks(
                listOf(
                    Opt("Native SegWit", "bc1q… · recommended"),
                    Opt("Taproot", "bc1p… · coming soon", off = true),
                    Opt("Nested SegWit", "3… · coming soon", off = true),
                ),
                0,
            )
            Note("Applies to new receive addresses. Existing funds stay where they are.")
        }
    }
}

@Composable
fun Server() {
    val nav = LocalNav.current
    val wallet = nav.wallet
    var url by rememberSaveable { mutableStateOf(wallet.customServer ?: "") }
    var bad by remember { mutableStateOf(false) }
    Scr {
        Bar("Server")
        Body {
            Big("Server")
            Intro("Balance and history come from a server with the Esplora API: blockstream.info by default, with mempool.space as backup if it can't be reached, or your own electrs. Leave empty for the default. The USD price comes from Coinbase, or Kraken as a backup.")
            Input(
                url,
                {
                    url = it
                    bad = false
                },
                wallet.net.api,
                mono = true,
            )
            Msg(if (bad) "The address must start with https://" else "Default: ${wallet.net.api}", bad)
            Primary("Save") {
                val value = url.trim()
                if (value.isEmpty() || value.startsWith("https://")) {
                    wallet.chooseServer(value)
                    nav.toast(if (value.isEmpty()) "Using default server" else "Server saved")
                    nav.back()
                } else {
                    bad = true
                }
            }
        }
    }
}
