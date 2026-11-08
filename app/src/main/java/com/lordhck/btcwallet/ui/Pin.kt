package com.lordhck.btcwallet.ui

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lordhck.btcwallet.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FIRST_PIN_LENGTH = 6
private const val WRONG = "Wrong PIN. Try again."
private const val MISMATCH = "The PINs didn't match. Try again."

// From the pin.html design
private val KEY = Color(0xFF1F1F1F)
private val KEY_DOWN = Color(0xFF333333)
private val DOT_LINE = Color(0xFF3A3A3A)

@Composable
private fun Key(
    size: Dp,
    icon: Boolean,
    enabled: () -> Boolean,
    onLong: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    val view = LocalView.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        if (!pressed || !enabled()) return@LaunchedEffect
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (onLong != null) {
            delay(500)
            onLong()
        }
    }
    val bg by animateColorAsState(
        when {
            icon && pressed -> KEY
            icon -> Color.Transparent
            pressed -> KEY_DOWN
            else -> KEY
        },
        tween(100),
    )
    val scale by animateFloatAsState(if (pressed) .94f else 1f, tween(80))
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .background(bg, CircleShape)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(if (icon && !pressed) C.soft else C.text) }
}

@Composable
private fun Dot(filled: Boolean, error: Boolean) {
    val fill = when {
        !filled -> Color.Transparent
        error -> C.red
        else -> C.accent
    }
    val bg by animateColorAsState(fill, tween(120))
    val line by animateColorAsState(if (filled) fill else DOT_LINE, tween(120))
    val scale by animateFloatAsState(if (filled) 1.1f else 1f, tween(120))
    Box(Modifier.size(14.dp).scale(scale).background(bg, CircleShape).border(2.dp, line, CircleShape))
}

// step clears the dots when a flow moves on to its next prompt.
// error shows in red with full red dots and a shake, until the next digit.
@Composable
private fun PinPad(
    title: String,
    sub: String,
    step: Int = 0,
    length: Int = FIRST_PIN_LENGTH,
    back: Boolean = true,
    error: String? = null,
    onDone: suspend (String) -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var pin by remember(step) { mutableStateOf("") }
    var flash by remember(step) { mutableStateOf(error != null) }
    var typed by remember(step) { mutableStateOf(false) }
    var checking by remember(step) { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val busy = flash || checking

    LaunchedEffect(step) {
        if (error != null) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
            )
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = 400
                    -10f at 80
                    10f at 160
                    -6f at 240
                    6f at 320
                },
            )
            delay(50)
            flash = false
        }
    }

    fun digit(d: Char) {
        if (busy || pin.length >= length) return
        pin += d
        typed = true
        if (pin.length == length) {
            checking = true
            val value = pin
            scope.launch {
                delay(150)
                onDone(value)
            }
        }
    }
    val enabled = { !busy }
    val showError = error != null && !typed

    BoxWithConstraints(Modifier.fillMaxSize().background(C.bg)) {
        val small = maxHeight < 700.dp
        val key = if (small) 68.dp else 76.dp
        val font = if (small) 26.4f else 29.6f
        Column(
            Modifier.fillMaxSize().padding(top = maxHeight * if (small) .12f else .2f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt(title, size = 21.6f, weight = 600)
            Txt(
                if (showError) error.orEmpty() else sub,
                Modifier.padding(top = 6.dp, bottom = 28.dp).height(19.dp),
                if (showError) C.red else C.muted,
                13.6f,
            )
            Row(
                Modifier.height(14.dp).offset(x = shake.value.dp),
                Arrangement.spacedBy(14.dp),
            ) {
                repeat(length) { i -> Dot(flash || i < pin.length, flash) }
            }
            Column(
                Modifier.padding(top = if (small) 32.dp else 48.dp),
                Arrangement.spacedBy(if (small) 13.dp else 18.dp),
            ) {
                val gap = Arrangement.spacedBy(if (small) 24.dp else 28.dp)
                for (row in listOf("123", "456", "789")) {
                    Row(horizontalArrangement = gap) {
                        row.forEach { d ->
                            Key(key, false, enabled, onClick = { digit(d) }) {
                                Txt("$d", color = it, size = font, weight = 500, tabular = true)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = gap) {
                    Spacer(Modifier.size(key))
                    Key(key, false, enabled, onClick = { digit('0') }) {
                        Txt("0", color = it, size = font, weight = 500, tabular = true)
                    }
                    Key(
                        key,
                        true,
                        enabled,
                        onLong = { if (!busy) pin = "" },
                        onClick = { if (!busy) pin = pin.dropLast(1) },
                    ) { Icon(Ic.del, 28.dp, it) }
                }
            }
        }
        Bar(back = back)
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
        sub = if (first == null) "You'll use it to unlock the wallet." else "Enter it once more.",
        step = step,
        error = if (mismatch) MISMATCH else null,
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
        sub = "Unlock your wallet.",
        step = step,
        length = length,
        back = false,
        error = error,
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
        sub = "Confirm it's you.",
        step = step,
        length = length,
        error = error,
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
    PinPad(title, sub, step, if (old == null) current else target, error = error) { pin ->
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
