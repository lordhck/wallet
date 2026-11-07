package com.lordhck.btcwallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lordhck.btcwallet.Seed

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
            Primary("Create new wallet") {
                nav.words = Seed.generate()
                nav.restoring = false
                nav.seedIsNew = true
                nav.show(S.ObSeed)
            }
            Ghost("Restore wallet") { nav.show(S.ObRestore) }
        }
    }
}

@Composable
fun ObSeed() {
    val nav = LocalNav.current
    var revealed by remember { mutableStateOf(false) }
    Scr {
        Bar()
        Body {
            Heading("Your seed phrase")
            Lead("Write these 12 words down in order and keep them offline. They're the only way to recover your wallet.")
            SeedGrid(nav.words.orEmpty()) { revealed = true }
            Spacer(Modifier.weight(1f))
            Primary("I've written it down", revealed) { nav.show(S.ObVerify) }
        }
    }
}

private class Question(val position: Int, val answer: String, val options: List<String>) {
    var picked by mutableStateOf<String?>(null)
    val solved: Boolean get() = picked == answer
}

// Three random words, each hidden among two words not in the seed
private fun quiz(words: List<String>): List<Question> {
    val decoys = (Seed.generate() + Seed.generate()).filter { it !in words }.distinct().toMutableList()
    return words.indices.shuffled().take(3).sorted().map { i ->
        val options = listOf(words[i], decoys.removeAt(0), decoys.removeAt(0)).shuffled()
        Question(i + 1, words[i], options)
    }
}

@Composable
private fun RowScope.Choice(word: String, q: Question) {
    val picked = word == q.picked
    val tint = if (word == q.answer) C.green else C.red
    val shape = RoundedCornerShape(12.dp)
    Txt(
        word,
        Modifier
            .weight(1f)
            .clip(shape)
            .background(C.surface)
            .then(if (picked) Modifier.border(1.5.dp, tint, shape) else Modifier)
            .press { q.picked = word }
            .padding(horizontal = 8.dp, vertical = 13.dp),
        if (picked) tint else C.text,
        align = TextAlign.Center,
    )
}

@Composable
fun ObVerify() {
    val nav = LocalNav.current
    val questions = remember { quiz(nav.words.orEmpty()) }
    Scr {
        Bar()
        Body {
            Heading("Confirm your seed")
            Lead("Pick the correct words.")
            questions.forEach { q ->
                Gt("Word #${q.position}")
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    q.options.forEach { Choice(it, q) }
                }
            }
            Spacer(Modifier.weight(1f))
            Primary("Continue", questions.all { it.solved }) { nav.show(S.ObPin) }
        }
    }
}

@Composable
fun ObRestore() {
    val nav = LocalNav.current
    // not saveable, so the seed never lands in saved instance state
    var text by remember { mutableStateOf("") }
    val count = Seed.split(text).size
    val words = Seed.parse(text)
    val bad = (count == 12 || count == 24) && words == null
    Scr {
        Bar()
        Body {
            Heading("Restore wallet")
            Lead("Enter your 12 or 24 word seed phrase.")
            Input(
                text,
                { text = it },
                "word1 word2 word3 …",
                mono = true,
                area = true,
                keyboard = KeyboardType.Password,
            )
            Msg(if (bad) "That isn't a valid seed phrase" else wordCount(text), bad)
            Spacer(Modifier.weight(1f))
            Primary("Continue", words != null) {
                nav.words = words
                nav.restoring = true
                nav.seedIsNew = false
                nav.show(S.ObPin)
            }
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
            Primary("Use biometrics", onClick = done)
            Ghost("Not now", done)
        }
    }
}
