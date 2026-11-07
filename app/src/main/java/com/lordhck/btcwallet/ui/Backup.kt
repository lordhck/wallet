package com.lordhck.btcwallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lordhck.btcwallet.Seed

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
                Cell("Import", "Restore a wallet or sweep a key", "Coming soon", off = true)
                Cell("Export", "Move this wallet to another app", "Coming soon", true, true)
            }
            Note("If you lose your phone, your seed phrase is the only way to get your bitcoin back.")
        }
    }
}

@Composable
fun SeedPhrase() {
    val nav = LocalNav.current
    Scr {
        Bar("Seed phrase")
        Body {
            Big("Seed phrase")
            SeedGrid(nav.words.orEmpty())
            Note("Write the words down in order and keep them offline. Anyone with these words can take your bitcoin. Never type them into a website or share a photo of them.")
        }
    }
}

@Composable
private fun WordRow(label: String, value: String, onFocus: () -> Unit, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        Arrangement.spacedBy(10.dp),
        Alignment.CenterVertically,
    ) {
        Txt(label, Modifier.width(64.dp), C.muted, 13f, 600)
        Input(
            value,
            onChange,
            label,
            Modifier.weight(1f).onFocusChanged { if (it.isFocused) onFocus() },
            mono = true,
            keyboard = KeyboardType.Password,
        )
    }
}

@Composable
fun TestBackup() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val list = remember { Seed.wordlist(context) }
    val words = nav.words.orEmpty()
    var active by remember { mutableIntStateOf(0) }
    val asks = remember { words.indices.shuffled().take(3).sorted() }
    // not saveable, so the seed never lands in saved instance state
    val typed = remember { mutableStateListOf("", "", "") }
    var wrong by remember { mutableStateOf(false) }
    Scr {
        Bar("Test your backup")
        Body {
            Big("Test your backup")
            Intro("Enter these words from your paper backup.")
            asks.forEachIndexed { i, word ->
                WordRow("Word ${word + 1}", typed[i], { active = i }) {
                    typed[i] = it.trim().lowercase()
                    wrong = false
                }
            }
            val start = typed[active]
            val hints = if (start.isEmpty()) emptyList() else list.filter { it.startsWith(start) && it != start }.take(3)
            Row(Modifier.padding(top = 6.dp), Arrangement.spacedBy(8.dp)) {
                hints.forEach { hint ->
                    Chip(hint) {
                        typed[active] = hint
                        wrong = false
                    }
                }
            }
            Msg(if (wrong) "That doesn't match your seed phrase" else "", wrong)
            Primary("Check", typed.all { it.isNotEmpty() }) {
                if (asks.indices.all { typed[it] == words[asks[it]] }) {
                    nav.toast("Your backup is correct")
                    nav.back()
                } else {
                    wrong = true
                }
            }
        }
    }
}
