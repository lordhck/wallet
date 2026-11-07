package com.lordhck.btcwallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PIN_LEN = 6

@Composable
private fun RowScope.Key(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.weight(1f).height(68.dp).clip(Oval).press(C.surface2, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun RowScope.Digit(d: Char, onKey: (Char) -> Unit) {
    Key({ onKey(d) }) { Txt("$d", size = 26f, weight = 500) }
}

// step clears the dots when a flow moves on to its next prompt
@Composable
private fun PinPad(
    title: String,
    sub: String,
    step: Int = 0,
    back: Boolean = true,
    bio: Boolean = false,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pin by remember(step) { mutableStateOf("") }

    fun enter(value: String) {
        pin = value
        if (value.length == PIN_LEN) {
            scope.launch {
                delay(150)
                onDone()
            }
        }
    }
    val onKey = { d: Char -> if (pin.length < PIN_LEN) enter(pin + d) }

    Scr {
        Bar(back = back)
        Body(scroll = false, bottom = 40.dp) {
            Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(title, size = 24f, weight = 700)
                Txt(sub, Modifier.padding(top = 8.dp).heightIn(min = 20.dp), C.soft, 14.5f)
                Row(Modifier.padding(top = 32.dp), Arrangement.spacedBy(16.dp)) {
                    repeat(PIN_LEN) { i ->
                        val fill = i < pin.length
                        Box(
                            Modifier
                                .size(14.dp)
                                .then(if (fill) Modifier.background(C.accent, CircleShape) else Modifier)
                                .border(2.dp, if (fill) C.accent else C.dim, CircleShape),
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                Arrangement.spacedBy(12.dp),
            ) {
                for (row in listOf("123", "456", "789")) {
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(24.dp)) {
                        row.forEach { Digit(it, onKey) }
                    }
                }
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(24.dp)) {
                    if (bio) {
                        Key({ if (pin.length < PIN_LEN) enter("000000") }) { Icon(Ic.finger, 26.dp, C.soft) }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Digit('0', onKey)
                    Key({ pin = pin.dropLast(1) }) { Icon(Ic.del, 26.dp, C.soft) }
                }
            }
        }
    }
}

@Composable
fun ObPin() {
    val nav = LocalNav.current
    var confirm by remember { mutableStateOf(false) }
    PinPad(
        title = if (confirm) "Confirm PIN" else "Choose a PIN",
        sub = if (confirm) "Enter it once more." else "You'll use it to unlock the wallet.",
        step = if (confirm) 1 else 0,
    ) {
        if (confirm) {
            nav.show(S.ObBio)
        } else {
            confirm = true
        }
    }
}

@Composable
fun Lock() {
    val nav = LocalNav.current
    PinPad("Enter PIN", "Unlock your wallet.", back = false, bio = true) { nav.root(S.Home) }
}

@Composable
fun PinCheck() {
    val nav = LocalNav.current
    PinPad("Enter PIN", "Confirm it's you.", bio = true) { nav.pinChecked() }
}

@Composable
fun ChangePin() {
    val nav = LocalNav.current
    var step by remember { mutableIntStateOf(0) }
    val (title, sub) = listOf(
        "Current PIN" to "Enter the PIN you use now.",
        "New PIN" to "Choose a 6-digit PIN.",
        "Confirm PIN" to "Enter it once more.",
    )[step]
    PinPad(title, sub, step) {
        if (step < 2) {
            step++
        } else {
            nav.toast("PIN changed")
            nav.back()
        }
    }
}
