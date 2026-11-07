package com.lordhck.btcwallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
private fun Heading(text: String) {
    Txt(text, Modifier.padding(vertical = 8.dp), size = 25f, weight = 700)
}

@Composable
private fun Lead(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Txt(text, modifier.padding(bottom = 20.dp), C.soft, 15f, align = align, line = 1.4f)
}

@Composable
fun Welcome() {
    val nav = LocalNav.current
    Scr {
        Body(scroll = false) {
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Logo(88.dp)
                Txt("BTC Wallet", Modifier.padding(top = 20.dp), size = 32f, weight = 700)
                Txt("A simple, self-custodial Bitcoin wallet.", Modifier.padding(top = 10.dp), C.soft, 15f)
            }
            Spacer(Modifier.weight(1f))
            Primary("Create new wallet") { nav.show(S.ObSeed) }
            Ghost("Restore wallet") { nav.show(S.ObRestore) }
        }
    }
}

@Composable
fun ObSeed() {
    val nav = LocalNav.current
    Scr {
        Bar()
        Body {
            Heading("Your seed phrase")
            Lead("Write these 12 words down in order and keep them offline. They're the only way to recover your wallet.")
            SeedGrid()
            Spacer(Modifier.weight(1f))
            Primary("I've written it down") { nav.show(S.ObVerify) }
        }
    }
}

@Composable
private fun RowScope.Choice(word: String, ok: Boolean) {
    var picked by rememberSaveable { mutableStateOf(false) }
    val tint = if (ok) C.green else C.red
    Txt(
        word,
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (picked) tint.copy(alpha = .15f) else C.surface)
            .press { picked = true }
            .padding(horizontal = 8.dp, vertical = 13.dp),
        if (picked) tint else C.text,
        align = TextAlign.Center,
    )
}

@Composable
private fun Choices(label: String, words: List<String>, correct: Int) {
    Gt(label)
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
        words.forEachIndexed { i, w -> Choice(w, i == correct) }
    }
}

@Composable
fun ObVerify() {
    val nav = LocalNav.current
    Scr {
        Bar()
        Body {
            Heading("Confirm your seed")
            Lead("Pick the correct words.")
            Choices("Word #3", listOf("arrange", "orbit", "lunar"), 0)
            Choices("Word #7", listOf("mango", "inhale", "pilot"), 1)
            Choices("Word #11", listOf("tide", "rocket", "umbrella"), 2)
            Spacer(Modifier.weight(1f))
            Primary("Continue") { nav.show(S.ObPin) }
        }
    }
}

@Composable
fun ObRestore() {
    val nav = LocalNav.current
    var words by rememberSaveable { mutableStateOf("") }
    Scr {
        Bar()
        Body {
            Heading("Restore wallet")
            Lead("Enter your 12 or 24 word seed phrase.")
            Input(words, { words = it }, "word1 word2 word3 …", mono = true, area = true)
            Msg(wordCount(words))
            Spacer(Modifier.weight(1f))
            Primary("Continue") { nav.show(S.ObPin) }
        }
    }
}

@Composable
fun ObBio() {
    val nav = LocalNav.current
    val done = {
        nav.root(S.Home)
        nav.toast("Wallet created")
    }
    Scr {
        Bar()
        Body(scroll = false) {
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.padding(bottom = 18.dp).background(C.surface, CircleShape).padding(18.dp)) {
                    Icon(Ic.finger, 40.dp, C.accent)
                }
                Heading("Unlock with biometrics?")
                Lead(
                    "Use your fingerprint or face instead of typing the PIN. The PIN still works, and you can change this later in Settings → Security.",
                    Modifier.padding(horizontal = 12.dp),
                    TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))
            Primary("Use biometrics", done)
            Ghost("Not now", done)
        }
    }
}
