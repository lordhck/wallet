package com.lordhck.btcwallet.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.lordhck.btcwallet.Debug
import com.lordhck.btcwallet.Draft
import com.lordhck.btcwallet.FINAL_CONFIRMATIONS
import com.lordhck.btcwallet.Money
import com.lordhck.btcwallet.Net
import com.lordhck.btcwallet.Payment
import com.lordhck.btcwallet.Receiving
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bitcoindevkit.AddressParseException
import org.bitcoindevkit.CreateTxException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

// ---------- home ----------
@Composable
private fun Spinner() {
    val turn by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "turn",
    )
    Canvas(Modifier.size(12.dp)) {
        val w = 2.dp.toPx()
        val inner = Size(size.width - w, size.height - w)
        drawArc(C.surface3, 0f, 360f, false, Offset(w / 2, w / 2), inner, style = Stroke(w))
        drawArc(C.accent, turn, 90f, false, Offset(w / 2, w / 2), inner, style = Stroke(w, cap = StrokeCap.Round))
    }
}

@Composable
private fun PaymentRow(payment: Payment, amount: String, alt: String?, divider: Boolean, onClick: () -> Unit) {
    // stays highlighted until the payment is final
    val pending = payment.confirmations < FINAL_CONFIRMATIONS
    val sub = when {
        payment.sending -> "Sending…"
        payment.confirmations == 0 -> if (payment.incoming) "Unconfirmed" else "0 of $FINAL_CONFIRMATIONS confirmations"
        pending -> "${payment.confirmations} of $FINAL_CONFIRMATIONS confirmations"
        else -> "Confirmed · " + SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(payment.time!! * 1000))
    }
    if (divider) Spacer(Modifier.fillMaxWidth().height(1.dp).background(C.line))
    Row(
        Modifier.fillMaxWidth().press(onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp),
        Arrangement.spacedBy(14.dp),
        Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(if (payment.incoming) C.green.copy(alpha = .12f) else C.surface2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (payment.incoming) Ic.txIn else Ic.txOut, 18.dp, if (payment.incoming) C.green else C.soft)
        }
        Column(Modifier.weight(1f)) {
            Txt(
                when {
                    payment.self -> "Sent to yourself"
                    payment.incoming -> "Received"
                    else -> "Sent"
                },
                weight = 600,
            )
            Row(Modifier.padding(top = 2.dp), Arrangement.spacedBy(6.dp), Alignment.CenterVertically) {
                if (payment.sending) Spinner()
                Txt(sub, color = if (pending) C.accent else C.muted, size = 13f)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            BasicText(
                buildAnnotatedString {
                    number((if (payment.incoming) "+" else "−") + amount, 15f, muted = mutedFor(payment))
                },
                style = style(if (payment.incoming) C.green else C.text, weight = 600, tabular = true),
                maxLines = 1,
            )
            if (payment.self) {
                Txt("Network fee", Modifier.padding(top = 2.dp), C.muted, 13f)
            } else if (alt != null) {
                Txt(alt, Modifier.padding(top = 2.dp), C.muted, 13f, tabular = true)
            }
        }
    }
}

private fun stage(payment: Payment): String = when {
    payment.sending -> "Sending…"
    payment.confirmations == 0 -> if (payment.incoming) "Unconfirmed" else "0 of $FINAL_CONFIRMATIONS"
    payment.confirmations < FINAL_CONFIRMATIONS -> "${payment.confirmations} of $FINAL_CONFIRMATIONS"
    else -> "Confirmed"
}

private fun day(payment: Payment): String {
    val at = payment.time ?: payment.seen ?: return "Just now"
    val date = Date(at * 1000)
    val key = SimpleDateFormat("yyyyMMdd", Locale.US)
    val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    if (key.format(date) == key.format(Date())) return "Today, $clock"
    return SimpleDateFormat("d MMM", Locale.getDefault()).format(date) + ", " + clock
}

private fun short(text: String, keep: Int) =
    if (text.length <= keep * 2 + 1) text else text.take(keep) + "…" + text.takeLast(keep)

@Composable
private fun Line(key: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
        Txt(key, color = C.muted, size = 14.5f)
        Txt(value, Modifier.weight(1f), C.soft, if (mono) 13.5f else 14.5f, mono = mono, align = TextAlign.End, tabular = true)
    }
}

// Card at the bottom of Home for one payment. Slides up from the bottom.
// Tap outside, swipe down or Back slides it away.
@Composable
private fun BoxScope.TxCard(payment: Payment, amount: (Long) -> String, alt: (Long) -> String?, explorer: String, onClose: () -> Unit) {
    val nav = LocalNav.current
    val clipboard = LocalClipboardManager.current
    val uris = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val final = payment.confirmations >= FINAL_CONFIRMATIONS
    // 1 is fully below the screen, 0 is in place
    val out = remember { Animatable(1f) }
    var height by remember { mutableIntStateOf(0) }
    var drag by remember { mutableFloatStateOf(0f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { out.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                // carry on from wherever a swipe left it
                val from = if (height > 0) (drag / height).coerceIn(0f, 1f) else 0f
                drag = 0f
                out.snapTo(maxOf(out.value, from))
                out.animateTo(1f, tween(200, easing = FastOutLinearInEasing))
                onClose()
            }
        }
    }
    BackHandler(onBack = close)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - out.value }
            .background(Color.Black.copy(alpha = .5f))
            .press(onClick = close),
    )
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .onSizeChanged { height = it.height }
            // its height isn't known on the first frame, so it would flash in place
            .graphicsLayer { alpha = if (height == 0) 0f else 1f }
            .offset { IntOffset(0, (drag + out.value * height * 1.1f).roundToInt()) }
            .navigationBarsPadding()
            .padding(8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(C.surface)
            .press { }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (drag > 80.dp.toPx()) close() else drag = 0f },
                    onDragCancel = { drag = 0f },
                ) { _, dy -> drag = maxOf(0f, drag + dy) }
            }
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 18.dp)
                .size(36.dp, 4.dp)
                .background(C.surface3, RoundedCornerShape(2.dp)),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(if (payment.incoming) C.green.copy(alpha = .12f) else C.surface2, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (payment.incoming) Ic.txIn else Ic.txOut, 20.dp, if (payment.incoming) C.green else C.soft)
            }
            Column(Modifier.weight(1f)) {
                val text = (if (payment.incoming) "+" else "−") + amount(payment.sats)
                val size = if (text.length > 13) 20f else 24f
                BasicText(
                    buildAnnotatedString { number(text, size, muted = mutedFor(payment)) },
                    style = style(if (payment.incoming) C.green else C.text, size, 700, tabular = true),
                    maxLines = 1,
                )
                alt(payment.sats)?.let {
                    Txt("≈ $it", Modifier.padding(top = 2.dp), C.muted, 13.5f, tabular = true)
                }
            }
            Row(
                Modifier.height(28.dp).background(C.surface2, CircleShape).padding(start = 10.dp, end = 11.dp),
                Arrangement.spacedBy(6.dp),
                Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).background(if (final) C.green else C.accent, CircleShape))
                Txt(stage(payment), color = if (final) C.soft else C.accent, size = 13f, weight = 600)
            }
        }
        Spacer(Modifier.padding(top = 20.dp).fillMaxWidth().height(1.dp).background(C.surface3))
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Line(if (payment.incoming) "Received on" else "Sent to", payment.address?.let { short(it, 8) } ?: "—", mono = true)
            Line("Network fee", if (payment.incoming) "Paid by sender" else payment.fee?.let { Money.sats(it) } ?: "—")
            Line("Date", day(payment))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Txt("Transaction", color = C.muted, size = 14.5f)
                Row(
                    Modifier.press {
                        clipboard.setText(AnnotatedString(payment.id))
                        nav.toast("Transaction ID copied")
                    },
                    Arrangement.spacedBy(5.dp),
                    Alignment.CenterVertically,
                ) {
                    Txt(short(payment.id, 6), color = C.soft, size = 13.5f, mono = true)
                    Icon(Ic.copy, 15.dp, C.dim)
                }
            }
        }
        Row(
            Modifier
                .padding(top = 22.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(CircleShape)
                .background(C.surface3)
                .press(C.focus, !payment.sending) { uris.openUri(explorer + payment.id) },
            Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            Alignment.CenterVertically,
        ) {
            Txt("View transaction", size = 15f, weight = 600)
            Icon(Ic.txOut, 16.dp, C.text)
        }
    }
}

@Composable
private fun Action(label: String, bg: Color, icon: Int, onClick: () -> Unit) {
    Btn(
        label,
        Modifier,
        bg,
        Color.Black,
        icon,
        0.dp,
        PaddingValues(horizontal = 28.dp, vertical = 15.dp),
        onClick = onClick,
    )
}

// Green when synced and ready. Orange and pulsing while the first sync since
// opening runs, steady orange while the server can't be reached.
@Composable
private fun NetChip(label: String, ready: Boolean, offline: Boolean, modifier: Modifier = Modifier) {
    val color = if (ready && !offline) C.green else C.btc
    val halo = if (!ready && !offline) {
        rememberInfiniteTransition(label = "sync").animateFloat(
            .1f,
            .45f,
            infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "halo",
        ).value
    } else {
        .18f
    }
    Row(
        modifier
            .height(28.dp)
            .background(C.surface, CircleShape)
            .padding(start = 7.dp, end = 11.dp),
        Arrangement.spacedBy(4.dp),
        Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).background(color.copy(alpha = halo), CircleShape), Alignment.Center) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
        }
        Txt(label, color = C.soft, size = 13f, weight = 600)
    }
}

@Composable
private fun HomeContent() {
    val nav = LocalNav.current
    val wallet = nav.wallet
    val net = wallet.net
    // id of the payment whose card is open
    var details by remember { mutableStateOf<String?>(null) }
    // test coins have no dollar value
    val price = if (net == Net.Main) wallet.price else null
    // what Settings picked comes first, tapping moves through the rest. The tap
    // sets wallet.shown, so Activity, Send and review switch with it.
    val views = buildList {
        val coin = "BTC" to Money.btc(wallet.sats) + " " + net.coin
        val sats = "sats" to Money.sats(wallet.sats)
        // zero is zero in any currency, so this needs no price
        val usd = (price?.let { Money.usd(wallet.sats, it) } ?: if (net == Net.Main && wallet.sats == 0L) "$0.00" else null)
            ?.let { "USD" to it }
        if (usd != null && wallet.currency == "USD") add(usd)
        addAll(if (wallet.unit == "sats") listOf(sats, coin) else listOf(coin, sats))
        if (usd != null && wallet.currency != "USD") add(usd)
    }
    // USD isn't there without a price, like on test networks, so the first one shows then
    val current = views.firstOrNull { it.first == wallet.shown } ?: views.first()
    val balance = if (nav.balanceHidden) "••••••" else current.second

    Box(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // scales with the phone, a fixed offset leaves tall screens top heavy
            Spacer(Modifier.fillMaxHeight(.16f))
            val size = if (balance.length > 11) 32f else 46f
            BasicText(
                buildAnnotatedString { number(balance, size) },
                Modifier.press {
                    if (nav.balanceHidden) {
                        nav.balanceHidden = false
                    } else {
                        wallet.display(views[(views.indexOf(current) + 1) % views.size].first)
                    }
                },
                style(size = size, weight = 700, tabular = true),
            )
            Txt(
                when {
                    wallet.error != null -> "Wallet error: " + wallet.error
                    wallet.offline != null -> "Can't reach the server: " + wallet.offline
                    !wallet.synced -> "Syncing…"
                    else -> ""
                },
                Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp).heightIn(min = 18.dp),
                if (wallet.error != null) C.red else C.muted,
                13f,
                align = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Action("Send", C.btc, Ic.send) { nav.show(S.Send) }
                Action("Receive", C.light, Ic.receive) { nav.show(S.Receive) }
            }
            Column(
                Modifier
                    .padding(top = 40.dp)
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                Gt("Activity", first = true)
                val activity = wallet.activity
                if (activity.isEmpty()) {
                    Txt("No transactions yet", Modifier.padding(start = 4.dp, top = 4.dp), C.muted, 14f)
                }
                activity.forEachIndexed { i, payment ->
                    PaymentRow(payment, wallet.amount(payment.sats), wallet.alt(payment.sats), i > 0) {
                        // nothing to look up until the broadcast is done
                        if (!payment.sending) details = payment.id
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 18.dp)
                .clip(RoundedCornerShape(10.dp))
                .press { nav.show(S.Settings) }
                .padding(8.dp),
        ) { Icon(Ic.gear, 24.dp, C.soft) }
        NetChip(
            net.label,
            wallet.ready,
            wallet.offline != null,
            Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 32.dp),
        )
        // looked up by id, so the card follows new confirmations and closes on a network switch
        wallet.activity.firstOrNull { it.id == details }?.let { payment ->
            TxCard(payment, wallet::amount, wallet::alt, net.explorer) { details = null }
        }
    }
}

@Composable
fun Home() = HomeContent()

// The point and the decimals after it a little smaller and muted, as in
// $1,234.56 or 0.00012345 BTC. The rest of the text is left as it is.
private fun AnnotatedString.Builder.number(text: String, size: Float, points: String = ".", muted: Color = C.muted) {
    val dot = text.indexOfFirst { it in points }
    if (dot < 0) {
        append(text)
        return
    }
    var end = dot + 1
    while (end < text.length && text[end].isDigit()) end++
    append(text.substring(0, dot))
    withStyle(SpanStyle(color = muted, fontSize = (size * .85f).sp)) { append(text.substring(dot, end)) }
    append(text.substring(end))
}

// Green amounts keep their green, just fainter
private fun mutedFor(payment: Payment) = if (payment.incoming) C.green.copy(alpha = .55f) else C.muted

// What goes in Send's amount field for these sats in this unit. Plain digits,
// no thousands separators, so it parses back the same.
private fun typed(sats: Long, unit: String, price: Double?): String = when (unit) {
    "sats" -> sats.toString()
    "USD" -> price?.let { String.format(Locale.US, "%.2f", sats / 100_000_000.0 * it) } ?: ""
    else -> java.math.BigDecimal(sats).movePointLeft(8).stripTrailingZeros().toPlainString()
}

// ---------- send ----------
private val STOP_NAMES = listOf("Slow", "Medium", "Fast")

// Where Slow, Medium and Fast sit on the fee slider. Fixed, so the labels never
// overlap, even on test networks where Slow and Medium are both 1 sat/vB.
private val STOP_AT = listOf(.25f, .5f, .75f)

// One input and two outputs, the usual payment. Only for the estimate on the
// Send screen, review shows the real fee of the built transaction.
private const val TYPICAL_VBYTES = 141L

// By slider position, not by rate: on test networks every stop is 1 sat/vB
private fun eta(pos: Float): String = when {
    pos >= STOP_AT[2] -> "~10 min"
    pos >= STOP_AT[1] -> "~30 min"
    pos >= STOP_AT[0] -> "~1 hour"
    else -> "Hours"
}

// Slider position 0..1 to sat/vB: 1 at the left, 2x Fast at the right, linear between stops
private fun rateAt(p: Float, stops: List<Long>): Long {
    val xs = listOf(0f) + STOP_AT + 1f
    val ys = listOf(1L) + stops + maxOf(stops[2] * 2, stops[2] + 1)
    for (i in 0 until xs.lastIndex) {
        if (p <= xs[i + 1]) {
            val t = (p - xs[i]) / (xs[i + 1] - xs[i])
            return (ys[i] + (ys[i + 1] - ys[i]) * t).roundToInt().toLong()
        }
    }
    return ys.last()
}

@Composable
// The knob's position is the state, and the rate comes from it. With the
// rate as the state, equal stops (1, 1, 1 on Signet) put the knob at the far
// left whatever was picked.
private fun FeeSlider(pos: Float, stops: List<Long>, onPos: (Float) -> Unit) {
    val view = LocalView.current
    var dragging by remember { mutableStateOf(false) }
    val knob by animateFloatAsState(if (dragging) 1.25f else 1f, tween(120), label = "knob")
    val p = pos
    // the gesture handlers below outlive a recomposition
    val current by rememberUpdatedState(pos)
    fun pick(x: Float, width: Int) {
        if (width <= 0) return
        var at = (x / width).coerceIn(0f, 1f)
        // snaps onto a stop when close
        STOP_AT.firstOrNull { kotlin.math.abs(it - at) < .03f }?.let { at = it }
        if (at == current) return
        // a tick for each whole sat/vB, and on reaching a stop
        if (rateAt(at, stops) != rateAt(current, stops) || at in STOP_AT) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        onPos(at)
    }
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 0.dp)
                .padding(top = 6.dp)
                .height(22.dp)
                .pointerInput(stops) {
                    detectTapGestures(onPress = { pick(it.x, size.width) })
                }
                .pointerInput(stops) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true; pick(it.x, size.width) },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ ->
                        change.consume()
                        pick(change.position.x, size.width)
                    }
                },
        ) {
            val w = maxWidth
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(2.dp).background(C.surface3, CircleShape))
            Box(Modifier.align(Alignment.CenterStart).width(w * p).height(2.dp).background(C.btc, CircleShape))
            STOP_AT.forEachIndexed { i, at ->
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = w * at - 2.dp)
                        .size(4.dp)
                        .background(if (pos >= at) C.btc else C.surface3, CircleShape),
                )
            }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = w * p - 7.dp)
                    .size(14.dp)
                    .graphicsLayer { scaleX = knob; scaleY = knob }
                    .background(C.light, CircleShape),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 7.dp).height(18.dp)) {
            val w = maxWidth
            STOP_NAMES.forEachIndexed { i, name ->
                Txt(
                    name,
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = w * STOP_AT[i] - 32.dp)
                        .width(64.dp)
                        .press { onPos(STOP_AT[i]) },
                    if (pos == STOP_AT[i]) C.soft else C.muted,
                    12f,
                    600,
                    align = TextAlign.Center,
                )
            }
        }
    }
}

// Groups of four, first and last in orange, as on Receive
private fun grouped(address: String) = buildAnnotatedString {
    val parts = address.chunked(4)
    parts.forEachIndexed { i, part ->
        if (i > 0) append(" ")
        if (i == 0 || i == parts.lastIndex) {
            withStyle(SpanStyle(color = C.btc)) { append(part) }
        } else {
            append(part)
        }
    }
}

@Composable
private fun Kv(key: String, value: String, divider: Boolean = true, bold: Boolean = false) {
    if (divider) Spacer(Modifier.fillMaxWidth().height(1.dp).background(C.surface3))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
        Arrangement.spacedBy(16.dp),
    ) {
        Txt(key, size = 14.5f, weight = if (bold) 700 else 400)
        Txt(
            value,
            Modifier.weight(1f),
            if (bold) C.text else C.soft,
            14.5f,
            if (bold) 700 else 400,
            align = TextAlign.End,
            tabular = true,
        )
    }
}

@Composable
private fun SlideToSend(onSlid: () -> Unit) {
    val density = LocalDensity.current
    val inset = with(density) { 4.dp.toPx() }
    val near = with(density) { 6.dp.toPx() }
    val knob = with(density) { 52.dp.toPx() }

    var width by remember { mutableIntStateOf(0) }
    var x by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    val left by animateFloatAsState(x, if (dragging) snap() else tween(250), label = "knob")
    val travel = maxOf(0f, width - knob - inset * 2)
    // a light band sweeping across the text
    val sweep by rememberInfiniteTransition(label = "sheen").animateFloat(
        1.5f, -1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "sweep",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .height(60.dp)
            .clip(CircleShape)
            .background(C.bg)
            .onSizeChanged { width = it.width },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(with(density) { (left + knob + inset).toDp() })
                .background(
                    Brush.horizontalGradient(listOf(C.btc.copy(alpha = .05f), C.btc.copy(alpha = .35f))),
                    CircleShape,
                ),
        )
        val look = if (sent) {
            style(C.soft, weight = 600, align = TextAlign.Center)
        } else {
            val w = width.toFloat().coerceAtLeast(1f)
            val band = w * 2.5f
            val start = sweep * w
            style(weight = 600, align = TextAlign.Center).merge(
                TextStyle(
                    brush = Brush.linearGradient(
                        0f to C.dim, .4f to C.dim, .5f to C.light, .6f to C.dim, 1f to C.dim,
                        start = Offset(start, 0f),
                        end = Offset(start + band, 0f),
                    ),
                ),
            )
        }
        BasicText(if (sent) "Sending…" else "Slide to send", Modifier.fillMaxWidth(), look)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((inset + left).roundToInt(), 0) }
                .size(52.dp)
                .background(C.btc, CircleShape)
                .pointerInput(travel) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            if (x >= travel - near && !sent) {
                                sent = true
                                x = travel
                                onSlid()
                            } else if (!sent) {
                                x = 0f
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            if (!sent) x = 0f
                        },
                    ) { change, delta ->
                        change.consume()
                        if (!sent) x = (x + delta).coerceIn(0f, travel)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (sent) {
                val turn by rememberInfiniteTransition(label = "spin").animateFloat(
                    0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "turn",
                )
                Canvas(Modifier.size(20.dp).graphicsLayer { rotationZ = turn }) {
                    val stroke = 2.4.dp.toPx()
                    val at = Offset(stroke / 2, stroke / 2)
                    val box = Size(size.width - stroke, size.height - stroke)
                    drawArc(Color.Black.copy(alpha = .25f), 0f, 360f, false, at, box, style = Stroke(stroke))
                    drawArc(Color.Black, -90f, 90f, false, at, box, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            } else {
                Icon(Ic.arrow, color = Color.Black)
            }
        }
    }
}

@Composable
private fun BoxScope.ConfirmSheet(
    draft: Draft,
    amount: (Long) -> String,
    alt: (Long) -> String?,
    eta: String,
    sending: Boolean,
    onClose: () -> Unit,
    onSlid: () -> Unit,
) {
    // 1 is fully below the screen, 0 is in place
    val out = remember { Animatable(1f) }
    var height by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { out.animateTo(0f, tween(280, easing = FastOutSlowInEasing)) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - out.value }
            .background(Color.Black.copy(alpha = .6f))
            .press { if (!sending) onClose() },
    )
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .onSizeChanged { height = it.height }
            .graphicsLayer { translationY = out.value * height }
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(C.surface)
            .press { }
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 18.dp)
                .size(36.dp, 4.dp)
                .background(C.surface3, RoundedCornerShape(2.dp)),
        )
        val text = amount(draft.sats)
        BasicText(
            buildAnnotatedString {
                // "50,000 sats" has a unit to dim, "$34.00" doesn't
                if (' ' in text) {
                    number(text.substringBeforeLast(' '), 34f)
                    withStyle(SpanStyle(color = C.dim, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)) {
                        append(" " + text.substringAfterLast(' '))
                    }
                } else {
                    number(text, 34f)
                }
            },
            Modifier.fillMaxWidth(),
            style(size = 34f, weight = 700, align = TextAlign.Center, tabular = true),
        )
        alt(draft.sats)?.let {
            Txt(
                "≈ $it",
                Modifier.fillMaxWidth().padding(top = 4.dp),
                C.dim,
                align = TextAlign.Center,
                tabular = true,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 6.dp)
                .background(C.bg, RoundedCornerShape(14.dp))
                .padding(12.dp),
            Arrangement.spacedBy(10.dp),
            Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).background(C.surface2, CircleShape), Alignment.Center) {
                Icon(Ic.send, 18.dp, C.soft)
            }
            BasicText(
                grouped(draft.to),
                Modifier.weight(1f),
                style(C.dim, 12.5f, 600, mono = true, line = 1.5f, spacing = .02f),
            )
        }
        Kv("Network fee", amount(draft.fee) + " · " + eta, divider = false)
        Kv("Total", amount(draft.sats + draft.fee), bold = true)
        SlideToSend(onSlid)
        if (!sending) Ghost("Cancel", onClose)
    }
}

// Shows "0" in grey when empty, the decimals muted, and the unit after the
// number, smaller and dim
private fun withUnit(unit: String, size: Float) = VisualTransformation { text ->
    val shown = buildAnnotatedString {
        if (text.isEmpty()) {
            withStyle(SpanStyle(color = C.placeholder)) { append("0") }
        } else {
            // typed amounts have no thousands separators, so a comma is the point
            number(text.text, size, ".,")
        }
        withStyle(SpanStyle(color = C.dim, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)) { append(" $unit") }
    }
    val length = text.length
    TransformedText(
        shown,
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset
            override fun transformedToOriginal(offset: Int) = minOf(offset, length)
        },
    )
}

@Composable
private fun UnitSwitch(units: List<String>, unit: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier
            .padding(top = 12.dp)
            .background(C.surface, CircleShape)
            .padding(3.dp),
    ) {
        units.forEachIndexed { i, label ->
            Txt(
                label,
                Modifier
                    .clip(CircleShape)
                    .background(if (i == unit) C.surface3 else Color.Transparent)
                    .press { onPick(i) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                if (i == unit) C.text else C.dim,
                13f,
                600,
            )
        }
    }
}

@Composable
fun Send() {
    val nav = LocalNav.current
    val wallet = nav.wallet
    val net = wallet.net
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val price = if (net == Net.Main) wallet.price else null
    val units = if (net == Net.Main) listOf("USD", "BTC", "sats") else listOf(net.coin, "sats")
    val coinUnit = if (net == Net.Main) 1 else 0

    var addr by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    // the unit is the one amounts show in everywhere. Tapping the balance on Home
    // changes it, and so does the switch here.
    val unit = when {
        wallet.shown == "sats" -> units.lastIndex
        wallet.shown == "USD" && net == Net.Main && price != null -> 0
        else -> coinUnit
    }
    fun keyOf(i: Int) = if (units[i] == "USD" || units[i] == "sats") units[i] else "BTC"
    // where the fee slider's knob is, 0 to 1. Starts at Medium.
    var pos by rememberSaveable { mutableFloatStateOf(STOP_AT[1]) }
    var all by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<Draft?>(null) }

    LaunchedEffect(Unit) {
        nav.scanned?.let { code ->
            nav.scanned = null
            addr = code.removePrefix("bitcoin:").removePrefix("BITCOIN:").substringBefore('?')
            // a payment link's amount is in BTC, shown here in the current unit
            Regex("[?&]amount=([0-9.]+)", RegexOption.IGNORE_CASE).find(code)?.let {
                Money.parse(it.groupValues[1], "BTC", price)?.let { linked ->
                    amount = typed(linked, units[unit], price)
                    all = false
                }
            }
        }
        wallet.loadFees()
    }
    val stops = wallet.rates
    // sat/vB, 0 until the first rates arrive
    val rate = stops?.let { rateAt(pos, it) } ?: 0L

    val unitName = units[unit]
    val sats = Money.parse(amount, unitName, price)
    val ready = addr.isNotBlank() && (all || sats != null) && stops != null && rate > 0 && !busy

    BackHandler(draft != null && !sending) { draft = null }
    Box(Modifier.fillMaxSize()) {
        Scr {
            Bar("Send")
            Body {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    NetChip(net.label, wallet.ready, wallet.offline != null)
                    // the amount, typed in place, full width and centered. The unit is
                    // added by the field's transformation, so number and unit center
                    // together and the cursor can't move into the unit.
                    val size = when {
                        amount.length > 11 -> 30f
                        amount.length > 8 -> 36f
                        else -> 44f
                    }
                    BasicTextField(
                        amount,
                        {
                            amount = it
                            all = false
                        },
                        Modifier.fillMaxWidth().padding(top = 26.dp),
                        textStyle = style(size = size, weight = 700, align = TextAlign.Center, tabular = true, spacing = -.01f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        visualTransformation = withUnit(unitName, size),
                        cursorBrush = SolidColor(C.text),
                    )
                    val alt = when {
                        sats == null -> ""
                        unitName == "USD" -> "≈ " + Money.sats(sats)
                        price != null -> "≈ " + Money.usd(sats, price)
                        unitName == "sats" -> "≈ " + Money.btc(sats) + " " + net.coin
                        else -> "≈ " + Money.sats(sats)
                    }
                    BasicText(
                        buildAnnotatedString {
                            if (alt.isNotEmpty()) append("$alt · ")
                            withStyle(SpanStyle(color = C.btc, fontWeight = FontWeight.SemiBold)) { append("Max") }
                        },
                        Modifier.padding(top = 6.dp).press {
                            amount = typed(wallet.sats, unitName, price)
                            all = true
                        },
                        style(C.muted, 13.5f, tabular = true),
                    )
                    UnitSwitch(units, unit) { next ->
                        // the amount is converted, so it stays the same value. Max stays Max.
                        val value = if (all) wallet.sats else sats
                        if (value != null) amount = typed(value, units[next], price)
                        wallet.display(keyOf(next))
                    }
                }
                Spacer(Modifier.height(26.dp))
                InputRow {
                    Input(addr, { addr = it }, "Bitcoin address", Modifier.weight(1f), mono = true, bare = true)
                    IconBtn(Ic.paste) { clipboard.getText()?.let { addr = it.text.trim() } }
                    IconBtn(Ic.scan, C.accent) { nav.show(S.Scanner) }
                }
                // network fee: a one line summary over a thin slider
                Column(Modifier.fillMaxWidth().padding(top = 26.dp, start = 4.dp, end = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Bottom) {
                        Txt("NETWORK FEE", color = C.muted, size = 12f, weight = 600, spacing = .09f)
                        if (stops != null && rate > 0) {
                            val time = eta(pos)
                            val fee = rate * TYPICAL_VBYTES
                            BasicText(
                                buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            color = if (pos < STOP_AT[0]) C.btc else C.text,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    ) { append(time) }
                                    append(" · ~" + Money.sats(fee))
                                    if (price != null) append(" · " + Money.usd(fee, price))
                                },
                                style = style(C.soft, 13.5f, tabular = true),
                                maxLines = 1,
                            )
                        } else {
                            Txt("Getting fees…", color = C.soft, size = 13.5f)
                        }
                    }
                    if (stops != null) FeeSlider(pos, stops) { pos = it }
                }
                Spacer(Modifier.height(32.dp))
                Primary(if (busy) "Checking…" else "Review", ready) {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            draft = wallet.use { it.draft(addr.trim(), if (all) null else sats, rate) }
                        } catch (e: AddressParseException) {
                            Debug.note("review failed: " + Debug.why(e))
                            error = "That isn't a valid ${net.label} address"
                        } catch (e: CreateTxException.InsufficientFunds) {
                            Debug.note("review failed: " + Debug.why(e))
                            error = "Not enough funds, including the network fee"
                        } catch (e: CreateTxException.CoinSelection) {
                            Debug.note("review failed: " + Debug.why(e))
                            error = "Not enough for this amount plus the network fee. Try a lower fee or Max"
                        } catch (e: CreateTxException.OutputBelowDustLimit) {
                            Debug.note("review failed: " + Debug.why(e))
                            error = "That amount is too small to send"
                        } catch (e: Exception) {
                            Debug.note("review failed: " + Debug.why(e))
                            error = "Couldn't create the transaction"
                        }
                        busy = false
                    }
                }
                error?.let { Msg(it, true) }
            }
        }
        draft?.let { ready ->
            ConfirmSheet(
                ready,
                wallet::amount,
                wallet::alt,
                eta(pos),
                sending,
                onClose = { draft = null },
                onSlid = {
                    sending = true
                    wallet.send(
                        ready,
                        signed = {
                            // Activity shows the send as it goes out
                            sending = false
                            draft = null
                            addr = ""
                            amount = ""
                            all = false
                            nav.root(S.Home)
                        },
                    ) { ok ->
                        sending = false
                        if (ok) {
                            nav.toast("Transaction broadcast")
                        } else {
                            nav.toast("Sending failed. Check Activity first")
                        }
                    }
                },
            )
        }
    }
}

// ---------- receive ----------
@Composable
fun Receive() {
    val nav = LocalNav.current
    val wallet = nav.wallet
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var receiving by remember { mutableStateOf<Receiving?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(wallet.net) {
        wallet.watch()
        try {
            receiving = wallet.use { it.address() }
        } catch (e: Exception) {
            Debug.note("address failed: " + Debug.why(e))
            failed = e.message ?: e.javaClass.simpleName
        }
    }

    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }
    val copy: () -> Unit = {
        receiving?.let {
            clipboard.setText(AnnotatedString(it.address))
            nav.toast("Address copied")
            copied = true
        }
    }
    val copyBg by animateColorAsState(if (copied) C.green else C.btc, tween(200), label = "copy")

    val current = receiving
    Scr {
        Bar("Receive")
        Body(scroll = false) {
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // every address here is BIP84
                NetChip("${wallet.net.label} · Native SegWit", wallet.ready, wallet.offline != null)
                // free space splits 1 above and 0.6 below, so the group sits a little above center
                Spacer(Modifier.weight(1f))
                if (current == null) {
                    Txt(
                        failed?.let { "Couldn't get an address: $it" } ?: "Getting an address…",
                        Modifier.fillMaxWidth(),
                        if (failed != null) C.red else C.muted,
                        15f,
                        align = TextAlign.Center,
                    )
                } else {
                    Box(Modifier.background(Color.White, RoundedCornerShape(24.dp)).padding(16.dp)) {
                        Qr(current.uri, 232.dp)
                    }
                    BasicText(
                        grouped(current.address),
                        Modifier
                            .padding(top = 18.dp)
                            .widthIn(max = 280.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .press(C.surface, onClick = copy)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        style(C.dim, 15f, 600, mono = true, align = TextAlign.Center, line = 1.55f, spacing = .02f),
                    )
                    // Copy and Share as one pill
                    Row(
                        Modifier
                            .padding(top = 22.dp)
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(C.surface, CircleShape)
                            .padding(4.dp),
                        Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(copyBg)
                                .press(onClick = copy),
                            Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            Alignment.CenterVertically,
                        ) {
                            Icon(if (copied) Ic.check else Ic.copy, 18.dp, Color.Black)
                            Txt(if (copied) "Copied" else "Copy", color = Color.Black, weight = 600)
                        }
                        Row(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .press(C.surface3) {
                                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, current.address)
                                    context.startActivity(Intent.createChooser(send, null))
                                },
                            Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            Alignment.CenterVertically,
                        ) {
                            Icon(Ic.share, 18.dp, C.text)
                            Txt("Share", weight = 600)
                        }
                    }
                    Row(
                        Modifier
                            .padding(top = 14.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .press {
                                scope.launch {
                                    receiving = runCatching { wallet.use { it.newAddress() } }.getOrNull() ?: receiving
                                    nav.toast("New address")
                                }
                            }
                            .padding(6.dp),
                        Arrangement.spacedBy(7.dp),
                        Alignment.CenterVertically,
                    ) {
                        Icon(Ic.refresh, 15.dp, C.dim)
                        Txt("New address", color = C.dim, size = 14f, weight = 600)
                    }
                }
                Spacer(Modifier.weight(.6f))
            }
        }
    }
}

// ---------- scanner ----------
@Composable
private fun BoxScope.Round(
    v: Int,
    align: Alignment,
    on: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .align(align)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 32.dp)
            .clip(CircleShape)
            .background(if (on) C.accent else Color.Black.copy(alpha = .45f))
            .press(onClick = onClick)
            .padding(10.dp),
    ) { Icon(v, color = if (on) Color.Black else C.text) }
}

private fun decode(reader: QRCodeReader, image: ImageProxy): String? {
    val plane = image.planes[0]
    val bytes = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
    val source = PlanarYUVLuminanceSource(
        bytes,
        plane.rowStride,
        image.height,
        0,
        0,
        image.width,
        image.height,
        false,
    )
    return try {
        reader.decode(BinaryBitmap(HybridBinarizer(source))).text
    } catch (e: ReaderException) {
        null
    } finally {
        reader.reset()
    }
}

@Composable
private fun CameraPreview(torch: Boolean, onCode: (String) -> Unit) {
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember { QRCodeReader() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(camera, torch) { camera?.cameraControl?.enableTorch(torch) }
    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { context ->
            PreviewView(context).also { view ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener(
                    {
                        val cameras = future.get()
                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(view.surfaceProvider)
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { image ->
                            val code = decode(reader, image)
                            image.close()
                            if (code != null) view.post { onCode(code) }
                        }
                        cameras.unbindAll()
                        provider = cameras
                        camera = cameras.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    },
                    ContextCompat.getMainExecutor(context),
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
fun Scanner(hint: String) {
    val nav = LocalNav.current
    val context = LocalContext.current
    var torch by rememberSaveable { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var allowed by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Debug.note("camera permission: $it")
        allowed = it
    }
    LaunchedEffect(Unit) {
        if (!allowed) ask.launch(Manifest.permission.CAMERA)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        if (allowed) {
            CameraPreview(torch) { code ->
                if (!done) {
                    done = true
                    Debug.note("scanned a code, ${code.length} characters")
                    nav.scanned = code
                    nav.back()
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val side = 260.dp.toPx()
            val left = (size.width - side) / 2
            val top = size.height * .45f - side / 2
            val radius = 22.dp.toPx()
            val edge = 4.dp.toPx()
            val arm = 34.dp.toPx()
            val mid = edge / 2

            val hole = Path().apply {
                addRoundRect(RoundRect(left, top, left + side, top + side, CornerRadius(radius)))
            }
            clipPath(hole, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = .55f)) }

            val corner = Path().apply {
                moveTo(left + mid, top + arm)
                lineTo(left + mid, top + radius)
                arcTo(
                    Rect(left + mid, top + mid, left + 2 * radius - mid, top + 2 * radius - mid),
                    180f,
                    90f,
                    false,
                )
                lineTo(left + arm, top + mid)
            }
            repeat(4) {
                rotate(90f * it, Offset(left + side / 2, top + side / 2)) {
                    drawPath(corner, C.accent, style = Stroke(edge))
                }
            }
        }
        Round(Ic.close, Alignment.TopStart) { nav.back() }
        Round(if (torch) Ic.torchOff else Ic.torch, Alignment.TopEnd, torch) { torch = !torch }
        Txt(
            if (allowed) hint else "Allow camera access to scan",
            Modifier
                .fillMaxWidth()
                .offset(y = maxHeight * .45f + 154.dp)
                .padding(horizontal = 32.dp),
            Color(0xFFD4D4D4),
            14.5f,
            align = TextAlign.Center,
        )
    }
}

// ---------- NFC signing (planned) ----------
@Composable
private fun NfcWaves() {
    val paths = remember { Ic.nfcArcs.map { PathParser().parsePathString(it).toPath() } }
    val wave = rememberInfiniteTransition(label = "wave")
    val alphas = paths.indices.map { i ->
        wave.animateFloat(
            1f,
            .3f,
            infiniteRepeatable(
                tween(800, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
                StartOffset(i * 150),
            ),
            label = "wave",
        )
    }
    Canvas(Modifier.size(72.dp)) {
        scale(size.width / 24f, Offset.Zero) {
            paths.forEachIndexed { i, p ->
                drawPath(
                    p,
                    C.btc,
                    alphas[i].value,
                    Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

@Composable
fun NfcSign() {
    val nav = LocalNav.current
    Box(Modifier.fillMaxSize()) {
        HomeContent()
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .6f)))
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = .35f))
                .press { }
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(C.surface, RoundedCornerShape(28.dp))
                    .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Txt("Ready to Sign", color = C.dim, size = 30f, weight = 500)
                Box(
                    Modifier
                        .padding(vertical = 32.dp)
                        .size(136.dp)
                        .background(C.btc.copy(alpha = .1f), CircleShape)
                        .border(5.dp, C.btc, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { NfcWaves() }
                Txt(
                    "Hold your phone near your COLDCARD.",
                    Modifier.padding(bottom = 32.dp),
                    size = 16.5f,
                    align = TextAlign.Center,
                )
                Btn("Cancel", bg = C.surface, fg = C.text, top = 0.dp) { nav.back() }
            }
        }
    }
}
