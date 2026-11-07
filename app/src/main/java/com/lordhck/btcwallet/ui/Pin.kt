package com.lordhck.btcwallet.ui

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.lordhck.btcwallet.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FIRST_PIN_LENGTH = 6
private const val WRONG = "Wrong PIN. Try again."
private const val MISMATCH = "The PINs didn't match. Try again."

@Composable
private fun RowScope.Key(onClick: () -> Unit, content: @Composable () -> Unit) {
    val view = LocalView.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        Modifier.weight(1f).height(68.dp).clickable(interactionSource = src, indication = null) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(68.dp).background(if (pressed) C.surface2 else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
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
    length: Int = FIRST_PIN_LENGTH,
    back: Boolean = true,
    err: Boolean = false,
    onDone: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pin by remember(step) { mutableStateOf("") }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(step) {
        if (err) {
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = 350
                    -10f at 87
                    10f at 262
                },
            )
        }
    }

    fun enter(value: String) {
        pin = value
        if (value.length == length) {
            scope.launch {
                delay(150)
                onDone(value)
            }
        }
    }
    val onKey = { d: Char -> if (pin.length < length) enter(pin + d) }

    Scr {
        Bar(back = back)
        Body(scroll = false, bottom = 40.dp) {
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(title, size = 24f, weight = 700)
                Txt(sub, Modifier.padding(top = 8.dp).heightIn(min = 20.dp), if (err) C.red else C.soft, 14.5f)
                Row(Modifier.padding(top = 32.dp).offset(x = shake.value.dp), Arrangement.spacedBy(16.dp)) {
                    repeat(length) { i ->
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
                    Spacer(Modifier.weight(1f))
                    Digit('0', onKey)
                    Key({ pin = pin.dropLast(1) }) { Icon(Ic.del, 26.dp, C.soft) }
                }
            }
            Spacer(Modifier.weight(.5f))
        }
    }
}

@Composable
fun ObPin() {
    val nav = LocalNav.current
    val context = LocalContext.current
    var first by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableIntStateOf(0) }
    var mismatch by remember { mutableStateOf(false) }
    PinPad(
        title = if (first == null) "Choose a PIN" else "Confirm PIN",
        sub = when {
            mismatch -> MISMATCH
            first == null -> "You'll use it to unlock the wallet."
            else -> "Enter it once more."
        },
        step = step,
        err = mismatch,
    ) { pin ->
        val chosen = first
        if (chosen == null) {
            first = pin
            mismatch = false
            step++
        } else if (chosen != pin) {
            first = null
            mismatch = true
            step++
        } else {
            val words = checkNotNull(nav.words)
            withContext(Dispatchers.Default) { Vault.create(context, pin, words) }
            nav.unlocked(words)
            nav.toast(if (nav.restoring) "Wallet restored" else "Wallet created")
        }
    }
}

// The seed, or null after telling why not
private suspend fun attempt(context: Context, pin: String, onFail: (String) -> Unit): List<String>? {
    val wait = Vault.lockedFor(context)
    if (wait > 0) {
        onFail("Too many tries. Wait ${wait}s.")
        return null
    }
    val words = withContext(Dispatchers.Default) { Vault.open(context, pin) }
    if (words == null) {
        val left = Vault.lockedFor(context)
        onFail(if (left > 0) "Too many tries. Wait ${left}s." else WRONG)
    }
    return words
}

@Composable
fun Lock() {
    val nav = LocalNav.current
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableIntStateOf(0) }
    val length = remember { Vault.pinLength(context) }
    PinPad(
        title = "Enter PIN",
        sub = error ?: "Unlock your wallet.",
        step = step,
        length = length,
        back = false,
        err = error != null,
    ) { pin ->
        val words = attempt(context, pin) {
            error = it
            step++
        }
        if (words != null) nav.unlocked(words)
    }
}

@Composable
fun PinCheck() {
    val nav = LocalNav.current
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableIntStateOf(0) }
    val length = remember { Vault.pinLength(context) }
    PinPad(
        title = "Enter PIN",
        sub = error ?: "Confirm it's you.",
        step = step,
        length = length,
        err = error != null,
    ) { pin ->
        val words = attempt(context, pin) {
            error = it
            step++
        }
        if (words != null) nav.pinChecked()
    }
}

// Also sets a new PIN length, when PIN length sent us here
@Composable
fun ChangePin() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val current = remember { Vault.pinLength(context) }
    val target = remember { nav.newPinLength ?: current }
    var old by remember { mutableStateOf<String?>(null) }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableIntStateOf(0) }
    val (title, sub) = when {
        first != null -> "Confirm PIN" to "Enter it once more."
        old != null -> "New PIN" to "Choose a $target-digit PIN."
        else -> "Current PIN" to "Enter the PIN you use now."
    }
    PinPad(title, error ?: sub, step, if (old == null) current else target, err = error != null) { pin ->
        val was = old
        val chosen = first
        if (was == null) {
            val words = attempt(context, pin) {
                error = it
                step++
            }
            if (words != null) {
                old = pin
                error = null
                step++
            }
        } else if (chosen == null) {
            first = pin
            error = null
            step++
        } else if (chosen != pin) {
            first = null
            error = MISMATCH
            step++
        } else {
            withContext(Dispatchers.Default) { Vault.changePin(context, was, pin) }
            nav.toast("PIN changed")
            nav.back()
        }
    }
}
