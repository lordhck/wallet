package com.lordhck.btcwallet.ui

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

private val AMOUNTS = listOf("$5", "$10", "$25", "$50")

@Composable
fun Settings() {
    val nav = LocalNav.current
    val name = nav.test?.let { "BTC Wallet $it" } ?: "BTC Wallet"
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
                Go("Currency", value = "USD") { nav.show(S.Currency) }
                Go("Bitcoin unit", value = "BTC", divider = true) { nav.show(S.BtcUnit) }
            }
            Gap()
            Group { Go("Advanced") { nav.show(S.Advanced) } }
            Gap()
            Group { Go("Donate") { nav.show(S.Donate) } }
        }
    }
}

@Composable
fun Security() {
    val nav = LocalNav.current
    Scr {
        Bar("Security")
        Body {
            Big("Security")
            Group {
                Go("Change PIN") { nav.show(S.ChangePin) }
                Go("PIN length", value = "6 digits", divider = true) { nav.show(S.PinLength) }
            }
            Gap()
            Group {
                ToggleCell("Unlock with biometrics", true)
                ToggleCell("Hide balance on start", false, divider = true)
            }
        }
    }
}

@Composable
fun PinLength() {
    Scr {
        Bar("PIN length")
        Body {
            Big("PIN length")
            Picks(listOf(Opt("4 digits"), Opt("6 digits")), 1)
            Note("Changing the length asks you to set a new PIN.")
        }
    }
}

@Composable
fun Currency() {
    Scr {
        Bar("Currency")
        Body {
            Big("Currency")
            Picks(listOf(Opt("USD", "US Dollar"), Opt("BTC", "Bitcoin")), 0)
            Note("Shown as your main balance. Tap the balance to switch at any time.")
        }
    }
}

@Composable
fun BitcoinUnit() {
    Scr {
        Bar("Bitcoin unit")
        Body {
            Big("Bitcoin unit")
            Picks(listOf(Opt("BTC", "0.00125 BTC"), Opt("Sats", "125,000 sats")), 0)
            Note("1 BTC = 100,000,000 sats.")
        }
    }
}

@Composable
private fun HoldToRemove() {
    val nav = LocalNav.current
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
                    holding = true
                    val released = withTimeoutOrNull(1500) { tryAwaitRelease() }
                    holding = false
                    if (released == null) nav.root(S.Welcome)
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize()) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(C.red.copy(alpha = .2f)))
        }
        Txt("Hold to remove wallet", color = C.red, weight = 600)
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
                Go("Network", value = "Mainnet") { nav.show(S.Network) }
                Go("Address type", value = "Native SegWit", divider = true) { nav.show(S.AddrType) }
                Go("Server", value = "Default", divider = true) { nav.show(S.Server) }
            }
            Gap()
            Group { ToggleCell("Replace-by-fee", true) }
            Note("Replace-by-fee lets you speed up a stuck transaction by paying a higher fee.")
            Gap()
            Group {
                Go("Export debug log") { nav.pin(S.Advanced, "Saved btc-wallet-$VERSION.log.txt") }
            }
            Gap()
            Group { HoldToRemove() }
            Note("Make sure your seed phrase is backed up before removing the wallet.")
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
                    Opt("Signet", "Free test coins from a faucet · recommended for testing"),
                    Opt("Testnet", "Testnet4 test coins"),
                ),
                0,
            ) { nav.test = listOf(null, "Signet", "Testnet")[it] }
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
                    Opt("Nested SegWit", "3… · for older wallets"),
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
    var url by rememberSaveable { mutableStateOf("") }
    Scr {
        Bar("Server")
        Body {
            Big("Server")
            Intro("Balance and history come from a server with the Esplora API: mempool.space (default), Blockstream's blockstream.info/api, or your own electrs. Leave empty for the default. The USD price always comes from mempool.space.")
            Input(url, { url = it }, "https://mempool.space/api", mono = true)
            Msg("Default: https://mempool.space/api")
            Primary("Save") {
                nav.toast("Using default server")
                nav.back()
            }
        }
    }
}

@Composable
fun Donate() {
    val nav = LocalNav.current
    var amt by rememberSaveable { mutableIntStateOf(1) }
    Scr {
        Bar("Donate")
        Body {
            Big("Donate")
            Intro("BTC Wallet is free, with no ads or tracking. Donations help keep it that way.")
            Row(Modifier.padding(top = 6.dp), Arrangement.spacedBy(8.dp)) {
                AMOUNTS.forEachIndexed { i, a -> Chip(a, i == amt) { amt = i } }
            }
            Primary("Donate ${AMOUNTS[amt]}.00") { nav.toast("Donation sheet") }
        }
    }
}
