package com.lordhck.btcwallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val CHIPS = listOf("orbit", "ordinary", "organ")

@Composable
fun Backup() {
    val nav = LocalNav.current
    Scr {
        Bar("Backup")
        Body {
            Big("Backup")
            Group {
                Go("Seed phrase", "Show your recovery words") { nav.show(S.Seed) }
                Go("Test your backup", "Check that you wrote the words down right", divider = true) {
                    nav.show(S.TestBackup)
                }
            }
            Gap()
            Group {
                Go("Import", "Restore a wallet or sweep a key") { nav.show(S.Import) }
                Go("Export", "Move this wallet to another app", divider = true) { nav.show(S.Export) }
            }
            Note("If you lose your phone, your seed phrase is the only way to get your bitcoin back.")
        }
    }
}

@Composable
fun SeedPhrase() {
    Scr {
        Bar("Seed phrase")
        Body {
            Big("Seed phrase")
            SeedGrid()
            Note("Write the words down in order and keep them offline. Anyone with these words can take your bitcoin. Never type them into a website or share a photo of them.")
        }
    }
}

@Composable
private fun WordRow(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        Arrangement.spacedBy(10.dp),
        Alignment.CenterVertically,
    ) {
        Txt(label, Modifier.width(64.dp), C.muted, 13f, 600)
        Input(value, onChange, placeholder, Modifier.weight(1f), mono = true)
    }
}

@Composable
fun TestBackup() {
    val nav = LocalNav.current
    var words by rememberSaveable { mutableStateOf(listOf("arrange", "or", "")) }
    fun set(i: Int, v: String) {
        words = words.toMutableList().also { it[i] = v }
    }
    Scr {
        Bar("Test your backup")
        Body {
            Big("Test your backup")
            Intro("Enter these words from your paper backup.")
            WordRow("Word 3", words[0], "") { set(0, it) }
            WordRow("Word 7", words[1], "Word 7") { set(1, it) }
            WordRow("Word 11", words[2], "Word 11") { set(2, it) }
            Row(Modifier.padding(top = 6.dp), Arrangement.spacedBy(8.dp)) {
                CHIPS.forEach { c ->
                    Chip(c) {
                        val i = words.indexOfFirst { it.length < 3 && it !in SEED }
                        set(if (i >= 0) i else 1, c)
                    }
                }
            }
            Msg("")
            Primary("Check") {
                nav.show(S.Backup)
                nav.toast("Your backup is correct")
            }
        }
    }
}

@Composable
fun Import() {
    val nav = LocalNav.current
    Scr {
        Bar("Import")
        Body {
            Big("Import")
            Group {
                Go("QR code", "SeedQR, seed phrase or private key") { nav.show(S.ScannerImport) }
                Go("Seed phrase", "Type your 12 or 24 words", divider = true) { nav.show(S.ImportSeed) }
                Cell("Import over NFC", value = "Coming soon", divider = true, off = true)
            }
            Gt("More")
            Group {
                Go("Private key", "Move bitcoin from a paper wallet") { nav.show(S.PrivKey) }
            }
            Note("Importing a seed replaces the wallet on this phone. You see its balance and confirm first.")
        }
    }
}

@Composable
fun ImportSeed() {
    val nav = LocalNav.current
    var words by rememberSaveable { mutableStateOf("") }
    var passphrase by rememberSaveable { mutableStateOf("") }
    Scr {
        Bar("Seed phrase")
        Body {
            Big("Seed phrase")
            Input(words, { words = it }, "word1 word2 word3 …", mono = true, area = true)
            Msg(wordCount(words))
            Gt("Optional")
            Input(passphrase, { passphrase = it }, "Passphrase")
            Note("The 25th word. Leave empty unless you set one when you made the wallet.")
            Notice(
                "This replaces the current wallet. Its balance is 0.01250000 BTC. Make sure you have its seed phrase before you continue.",
                C.btc,
                Color(0xFFF5C58A),
                .08f,
            )
            Primary("Restore") { nav.pin(S.Home, "Wallet restored") }
        }
    }
}

@Composable
fun PrivKey() {
    val nav = LocalNav.current
    var key by rememberSaveable { mutableStateOf("") }
    var found by rememberSaveable { mutableStateOf(false) }
    Scr {
        Bar("Private key")
        Body {
            Big("Private key")
            Intro("Got bitcoin on a paper wallet or an old private key? Scan or paste the key and the funds are moved into this wallet.")
            InputRow {
                Input(key, { key = it }, "Private key", Modifier.weight(1f), mono = true, bare = true)
                IconBtn(Ic.paste) { nav.toast("Pasted") }
                IconBtn(Ic.scan, C.accent) { nav.show(S.ScannerImport) }
            }
            Msg("")
            if (found) {
                Column(
                    Modifier
                        .padding(top = 20.dp)
                        .fillMaxWidth()
                        .background(C.surface, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Gt("Found on this key", first = true)
                    Txt("0.00042000 BTC", Modifier.padding(top = 6.dp), size = 25f, weight = 700)
                    Txt("≈ $43.16", Modifier.padding(top = 3.dp), C.dim, 14.5f)
                }
            }
            Primary(if (found) "Move to wallet" else "Check key") {
                if (found) {
                    nav.toast("Funds on their way")
                    found = false
                    nav.back()
                } else {
                    found = true
                }
            }
        }
    }
}

@Composable
fun Export() {
    val nav = LocalNav.current
    Scr {
        Bar("Export")
        Body {
            Big("Export")
            Group {
                Go("QR code", "SeedQR, works with SeedSigner and others") { nav.pin(S.ExportQr) }
                Go("Seed phrase", "Show the words", divider = true) { nav.show(S.Seed) }
                Cell("Export over NFC", value = "Coming soon", divider = true, off = true)
            }
            Gt("More")
            Group {
                Go("Watch-only key", "See your balance in another app, without spending") {
                    nav.show(S.WatchOnly)
                }
            }
            Note("PIN or biometric is required. Nothing is ever sent anywhere.")
        }
    }
}

@Composable
fun ExportQr() {
    val nav = LocalNav.current
    var left by remember { mutableIntStateOf(60) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left--
        }
        nav.back()
    }
    Scr {
        Bar("QR code")
        Body {
            Big("QR code")
            Intro("Scan with the other wallet. Anyone who sees this code can take your bitcoin.")
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(14.dp),
            ) { Qr(QR_EXPORT, 232.dp) }
            Txt(
                if (left == 60) "Hides in 1:00" else "Hides in 0:" + "$left".padStart(2, '0'),
                Modifier.fillMaxWidth().padding(top = 12.dp),
                C.dim,
                13f,
                align = TextAlign.Center,
            )
            Notice(
                "Never show this code on a video call or let anyone take a photo of it.",
                C.red,
                Color(0xFFF3A3A3),
                .1f,
            )
            Ghost("Done") { nav.back() }
        }
    }
}

@Composable
fun WatchOnly() {
    val nav = LocalNav.current
    Scr {
        Bar("Watch-only key")
        Body {
            Big("Watch-only key")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(C.surface)
                    .press { nav.toast("Copied") }
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.background(Color.White, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Qr(QR_WATCH, 236.dp)
                }
                Txt("Tap to copy", Modifier.padding(top = 14.dp), C.muted, 13f, 600)
            }
            Box(Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)) { Tag("zpub") }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(C.surface, RoundedCornerShape(14.dp))
                    .padding(start = 16.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt("zpub6rFR7y4Q2Aij…z2oz2AGutZYs", Modifier.weight(1f), C.soft, 13.5f, mono = true)
                IconBtn(Ic.copy) { nav.toast("Copied") }
            }
            Note("Lets another app show your balance and transactions. It can't spend your bitcoin, but it does reveal your whole history, so only share it with apps you trust.")
        }
    }
}
