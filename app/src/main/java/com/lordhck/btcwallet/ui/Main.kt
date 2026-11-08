package com.lordhck.btcwallet.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
private fun PaymentRow(payment: Payment, amount: String, price: Double?, divider: Boolean, onClick: () -> Unit) {
    // stays highlighted until the payment is final
    val pending = payment.confirmations < FINAL_CONFIRMATIONS
    val sub = when {
        payment.sending -> "Sending…"
        payment.confirmations == 0 -> if (payment.incoming) "Unconfirmed" else "Broadcast"
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
            Txt(
                (if (payment.incoming) "+" else "−") + amount,
                color = if (payment.incoming) C.green else C.text,
                weight = 600,
                tabular = true,
            )
            if (payment.self) {
                Txt("Network fee", Modifier.padding(top = 2.dp), C.muted, 13f)
            } else if (price != null) {
                Txt(Money.usd(payment.sats, price), Modifier.padding(top = 2.dp), C.muted, 13f)
            }
        }
    }
}

private fun stage(payment: Payment): String = when {
    payment.sending -> "Sending…"
    payment.confirmations == 0 -> if (payment.incoming) "Unconfirmed" else "Broadcast"
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
private fun BoxScope.TxCard(payment: Payment, amount: (Long) -> String, price: Double?, explorer: String, onClose: () -> Unit) {
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
                Txt(
                    (if (payment.incoming) "+" else "−") + amount(payment.sats),
                    color = if (payment.incoming) C.green else C.text,
                    size = 24f,
                    weight = 700,
                    tabular = true,
                )
                if (price != null) {
                    Txt("≈ " + Money.usd(payment.sats, price), Modifier.padding(top = 2.dp), C.muted, 13.5f, tabular = true)
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

@Composable
private fun BoxScope.TestBadge(label: String) {
    Row(
        Modifier
            .align(Alignment.TopStart)
            .padding(start = 20.dp, top = 32.dp)
            .height(28.dp)
            .background(C.surface, CircleShape)
            .padding(start = 7.dp, end = 11.dp),
        Arrangement.spacedBy(4.dp),
        Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).background(C.btc.copy(alpha = .18f), CircleShape), Alignment.Center) {
            Box(Modifier.size(8.dp).background(C.btc, CircleShape))
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
    // starts over when a setting or the first price changes the list
    var view by rememberSaveable(wallet.currency, wallet.unit, price != null) { mutableIntStateOf(0) }
    // what Settings picked comes first, tapping moves through the rest
    val views = buildList {
        val coin = Money.btc(wallet.sats) + " " + net.coin
        val sats = Money.sats(wallet.sats)
        // zero is zero in any currency, so this needs no price
        val usd = price?.let { Money.usd(wallet.sats, it) } ?: if (net == Net.Main && wallet.sats == 0L) "$0.00" else null
        if (usd != null && wallet.currency == "USD") add(usd)
        addAll(if (wallet.unit == "sats") listOf(sats, coin) else listOf(coin, sats))
        if (usd != null && wallet.currency != "USD") add(usd)
    }
    val balance = if (nav.balanceHidden) "••••••" else views[view % views.size]

    Box(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // scales with the phone, a fixed offset leaves tall screens top heavy
            Spacer(Modifier.fillMaxHeight(.16f))
            Txt(
                balance,
                Modifier.press {
                    if (nav.balanceHidden) nav.balanceHidden = false else view = (view + 1) % views.size
                },
                size = if (balance.length > 11) 32f else 46f,
                weight = 700,
                tabular = true,
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
                    PaymentRow(payment, wallet.amount(payment.sats), price, i > 0) {
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
        if (net != Net.Main) TestBadge(net.label)
        // looked up by id, so the card follows new confirmations and closes on a network switch
        wallet.activity.firstOrNull { it.id == details }?.let { payment ->
            TxCard(payment, wallet::amount, price, net.explorer) { details = null }
        }
    }
}

@Composable
fun Home() = HomeContent()

// ---------- send ----------
private val FEES = listOf("Slow" to "~1 hour", "Medium" to "~30 min", "Fast" to "~10 min")

@Composable
private fun FeePicker(fee: Int, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 5.dp, bottom = 4.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(22.dp)) {
            val x by animateDpAsState(maxWidth * fee / 2, tween(200), label = "fee")
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(C.surface3, RoundedCornerShape(2.dp)),
            )
            Box(Modifier.offset(x = x - 11.dp).size(22.dp).background(C.accent, CircleShape))
        }
        Row(Modifier.fillMaxWidth().padding(top = 7.dp), Arrangement.SpaceBetween) {
            FEES.forEachIndexed { i, f ->
                Txt(
                    f.first,
                    Modifier.width(64.dp).press { onPick(i) },
                    if (i == fee) C.text else C.dim,
                    14f,
                    600,
                    align = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Kv(key: String, value: String, mono: Boolean = false) {
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(C.surface3))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 13.dp),
        Arrangement.spacedBy(16.dp),
    ) {
        Txt(key, size = 14.5f)
        Txt(
            value,
            Modifier.weight(1f),
            C.soft,
            if (mono) 13.5f else 14.5f,
            mono = mono,
            align = TextAlign.End,
        )
    }
}

@Composable
private fun SlideToSend(onSlid: () -> Unit) {
    val density = LocalDensity.current
    val inset = with(density) { 4.dp.toPx() }
    val near = with(density) { 6.dp.toPx() }
    val knob = with(density) { 52.dp.toPx() }

    var label by remember { mutableStateOf("Slide to send") }
    var width by remember { mutableIntStateOf(0) }
    var x by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    val left by animateFloatAsState(
        x,
        if (dragging) snap() else tween(200),
        label = "knob",
    )
    val travel = maxOf(0f, width - knob - inset * 2)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .height(60.dp)
            .background(C.surface3, CircleShape)
            .onSizeChanged { width = it.width },
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, color = C.dim, weight = 600)
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
                                label = "Sending…"
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
        ) { Icon(Ic.arrow, color = Color.Black) }
    }
}

@Composable
private fun BoxScope.ConfirmSheet(
    draft: Draft,
    amount: (Long) -> String,
    price: Double?,
    sending: Boolean,
    onClose: () -> Unit,
    onSlid: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)).press { if (!sending) onClose() })
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(C.surface)
            .press { }
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
    ) {
        Txt(
            amount(draft.sats),
            Modifier.fillMaxWidth(),
            size = 30f,
            weight = 700,
            align = TextAlign.Center,
            tabular = true,
        )
        Txt(
            if (price != null) "≈ " + Money.usd(draft.sats, price) else "",
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp),
            C.dim,
            14.5f,
            align = TextAlign.Center,
        )
        Kv("To", draft.to, mono = true)
        Kv("Network fee", Money.sats(draft.fee))
        Kv("Total", amount(draft.sats + draft.fee))
        SlideToSend(onSlid)
        if (!sending) Ghost("Cancel", onClose)
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
    var unit by rememberSaveable { mutableIntStateOf(if (wallet.unit == "sats") units.lastIndex else coinUnit) }
    var fee by rememberSaveable { mutableIntStateOf(1) }
    var all by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<Draft?>(null) }

    LaunchedEffect(Unit) {
        nav.scanned?.let { code ->
            nav.scanned = null
            addr = code.removePrefix("bitcoin:").removePrefix("BITCOIN:").substringBefore('?')
            Regex("[?&]amount=([0-9.]+)", RegexOption.IGNORE_CASE).find(code)?.let {
                amount = it.groupValues[1]
                unit = coinUnit
                all = false
            }
        }
        wallet.loadFees(force = true)
    }

    val sats = Money.parse(amount, units[unit % units.size], price)
    val rate = wallet.rates?.get(fee)
    val ready = addr.isNotBlank() && (all || sats != null) && rate != null && !busy

    BackHandler(draft != null && !sending) { draft = null }
    Box(Modifier.fillMaxSize()) {
        Scr {
            Bar("Send")
            Body {
                InputRow {
                    Input(addr, { addr = it }, "Bitcoin address", Modifier.weight(1f), mono = true, bare = true)
                    IconBtn(Ic.paste) { clipboard.getText()?.let { addr = it.text.trim() } }
                    IconBtn(Ic.scan, C.accent) { nav.show(S.Scanner) }
                }
                Gt("Amount")
                InputRow {
                    Input(
                        amount,
                        {
                            amount = it
                            all = false
                        },
                        "0",
                        Modifier.weight(1f),
                        big = true,
                        bare = true,
                        keyboard = KeyboardType.Decimal,
                    )
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(C.surface3)
                            .press {
                                unit = (unit + 1) % units.size
                                all = false
                            }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        Arrangement.spacedBy(5.dp),
                        Alignment.CenterVertically,
                    ) {
                        Txt(units[unit % units.size], size = 13.5f, weight = 600)
                        Icon(Ic.updown, 14.dp, C.dim)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
                    Arrangement.SpaceBetween,
                ) {
                    Txt(
                        if (sats != null && price != null) "≈ " + Money.usd(sats, price) else "",
                        color = C.muted,
                        size = 13f,
                    )
                    BasicText(
                        buildAnnotatedString {
                            append("Available " + wallet.amount(wallet.sats) + " · ")
                            withStyle(SpanStyle(color = C.accent, fontWeight = FontWeight.SemiBold)) {
                                append("Max")
                            }
                        },
                        Modifier.press {
                            amount = Money.btc(wallet.sats)
                            unit = coinUnit
                            all = true
                        },
                        style(C.muted, 13f),
                    )
                }
                Gt("Network fee")
                FeePicker(fee) { fee = it }
                Msg(
                    error ?: rate?.let { if (net == Net.Main) FEES[fee].second + " · $it sat/vB" else "$it sat/vB" }
                        ?: "Getting fees…",
                    error != null,
                )
                Primary(if (busy) "Checking…" else "Review", ready) {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val feeRate = checkNotNull(rate)
                            draft = wallet.use { it.draft(addr.trim(), if (all) null else sats, feeRate) }
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
            }
        }
        draft?.let { ready ->
            ConfirmSheet(
                ready,
                wallet::amount,
                price,
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
        try {
            receiving = wallet.use { it.address() }
        } catch (e: Exception) {
            Debug.note("address failed: " + Debug.why(e))
            failed = e.message ?: e.javaClass.simpleName
        }
    }

    val current = receiving
    Scr {
        Bar("Receive")
        Body(scroll = false) {
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
                Column(
                    Modifier.fillMaxWidth(),
                    Arrangement.spacedBy(22.dp),
                    Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp)) {
                        Qr(current.uri, 248.dp)
                    }
                    val parts = current.address.chunked(4)
                    BasicText(
                        buildAnnotatedString {
                            parts.forEachIndexed { i, p ->
                                if (i > 0) append(' ')
                                if (i == 0 || i == parts.lastIndex) {
                                    withStyle(SpanStyle(color = C.text, fontWeight = FontWeight.SemiBold)) { append(p) }
                                } else {
                                    append(p)
                                }
                            }
                        },
                        Modifier.widthIn(max = 300.dp),
                        style(C.soft, 15f, mono = true, align = TextAlign.Center, line = 1.6f, spacing = .02f),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                Btn("Copy", Modifier.weight(1f), C.light, Color.Black, Ic.copy, 0.dp, enabled = current != null) {
                    current?.let {
                        clipboard.setText(AnnotatedString(it.address))
                        nav.toast("Address copied")
                    }
                }
                Btn("Share", Modifier.weight(1f), C.surface, C.text, Ic.share, 0.dp, enabled = current != null) {
                    current?.let {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, it.address)
                        context.startActivity(Intent.createChooser(send, null))
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .press {
                        scope.launch {
                            receiving = runCatching { wallet.use { it.newAddress() } }.getOrNull() ?: receiving
                            nav.toast("New address")
                        }
                    }
                    .padding(6.dp),
                Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                Alignment.CenterVertically,
            ) {
                Icon(Ic.refresh, 15.dp, C.dim)
                Txt("New address", color = C.dim, size = 14f, weight = 600)
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
