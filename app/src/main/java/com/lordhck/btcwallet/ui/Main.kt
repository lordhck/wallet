package com.lordhck.btcwallet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------- home ----------
@Composable
private fun Spinner() {
    val spin = rememberInfiniteTransition(label = "spin")
    val turn by spin.animateFloat(
        0f,
        360f,
        infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spin",
    )
    Canvas(Modifier.requiredSize(46.dp)) {
        val w = 2.dp.toPx()
        drawArc(
            C.accent,
            turn - 135f,
            90f,
            false,
            Offset(w / 2, w / 2),
            Size(size.width - w, size.height - w),
            style = Stroke(w),
        )
    }
}

@Composable
private fun TxRow(tx: Tx, divider: Boolean) {
    if (divider) Spacer(Modifier.fillMaxWidth().height(1.dp).background(C.line))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
        Arrangement.spacedBy(14.dp),
        Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(if (tx.incoming) C.green.copy(alpha = .12f) else C.surface2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (tx.incoming) Ic.txIn else Ic.txOut, 18.dp, if (tx.incoming) C.green else C.soft)
            if (tx.pending) Spinner()
        }
        Column(Modifier.weight(1f)) {
            Txt(tx.title, weight = 600)
            Txt(tx.sub, Modifier.padding(top = 2.dp), if (tx.pending) C.accent else C.muted, 13f)
        }
        Column(horizontalAlignment = Alignment.End) {
            Txt(tx.amount, color = if (tx.incoming) C.green else C.text, weight = 600, tabular = true)
            Txt(tx.fiat, Modifier.padding(top = 2.dp), C.muted, 13f)
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
        onClick,
    )
}

@Composable
private fun BoxScope.TestBadge(label: String) {
    Row(
        Modifier
            .align(Alignment.TopStart)
            .padding(start = 20.dp, top = 20.dp)
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
    var bal by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(
                BALANCES[bal],
                Modifier
                    .padding(top = 100.dp, bottom = 12.dp)
                    .press { bal = (bal + 1) % BALANCES.size },
                size = 46f,
                weight = 700,
                tabular = true,
            )
            Row(
                Modifier.padding(bottom = 32.dp),
                Arrangement.spacedBy(6.dp),
                Alignment.CenterVertically,
            ) {
                Txt("+$23.18", color = C.green, weight = 600)
                Row(
                    Modifier
                        .background(C.green.copy(alpha = .15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    Arrangement.spacedBy(5.dp),
                    Alignment.CenterVertically,
                ) {
                    Txt("1.84%", color = C.green, size = 13.5f, weight = 600)
                    Icon(Ic.trendUp, 14.dp, C.green)
                }
            }
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
                TXS.forEachIndexed { i, tx -> TxRow(tx, i > 0) }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 18.dp)
                .clip(RoundedCornerShape(10.dp))
                .press { nav.show(S.Settings) }
                .padding(8.dp),
        ) { Icon(Ic.gear, 24.dp, C.soft) }
        nav.test?.let { TestBadge(it) }
    }
}

@Composable
fun Home() = HomeContent()

// ---------- send ----------
private val UNITS = listOf("USD", "BTC", "sats")
private val FEES = listOf(
    "Slow" to "~1 hour · 2 sat/vB",
    "Medium" to "~30 min · 6 sat/vB",
    "Fast" to "~10 min · 12 sat/vB",
)

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
        Arrangement.SpaceBetween,
    ) {
        Txt(key, size = 14.5f)
        Txt(value, color = C.soft, size = if (mono) 13.5f else 14.5f, mono = mono)
    }
}

@Composable
private fun SlideToSend(onSent: () -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val inset = with(density) { 4.dp.toPx() }
    val near = with(density) { 6.dp.toPx() }
    val knob = with(density) { 52.dp.toPx() }

    var label by remember { mutableStateOf("Slide to send") }
    var width by remember { mutableIntStateOf(0) }
    var x by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
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
        Txt(label, Modifier.padding(start = 40.dp), C.dim, weight = 600)
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
                            if (x >= travel - near) {
                                label = "Sending…"
                                scope.launch {
                                    delay(700)
                                    onSent()
                                }
                            } else {
                                x = 0f
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            x = 0f
                        },
                    ) { change, delta ->
                        change.consume()
                        x = (x + delta).coerceIn(0f, travel)
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Icon(Ic.arrow, color = Color.Black) }
    }
}

@Composable
private fun BoxScope.ConfirmSheet(open: Boolean, onClose: () -> Unit, onSent: () -> Unit) {
    AnimatedVisibility(open, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)).press(onClick = onClose))
    }
    AnimatedVisibility(
        open,
        Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(250)) { it },
        exit = slideOutVertically(tween(250)) { it },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(C.surface)
                .press { }
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        ) {
            Txt(
                "0.00100000 BTC",
                Modifier.fillMaxWidth(),
                size = 30f,
                weight = 700,
                align = TextAlign.Center,
                tabular = true,
            )
            Txt(
                "≈ $102.76",
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp),
                C.dim,
                14.5f,
                align = TextAlign.Center,
            )
            Kv("To", "bc1qxy2k…hx0wlh", mono = true)
            Kv("Network fee", "846 sats · $0.87")
            Kv("Total", "0.00100846 BTC")
            SlideToSend(onSent)
            Ghost("Cancel", onClose)
        }
    }
}

@Composable
fun Send() {
    val nav = LocalNav.current
    var addr by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("0.001") }
    var unit by rememberSaveable { mutableIntStateOf(1) }
    var fee by rememberSaveable { mutableIntStateOf(1) }
    var sheet by rememberSaveable { mutableStateOf(false) }

    BackHandler(sheet) { sheet = false }
    Box(Modifier.fillMaxSize()) {
        Scr {
            Bar("Send")
            Body {
                InputRow {
                    Input(addr, { addr = it }, "Bitcoin address", Modifier.weight(1f), mono = true, bare = true)
                    IconBtn(Ic.paste) { nav.toast("Pasted") }
                    IconBtn(Ic.scan, C.accent) { nav.show(S.Scanner) }
                }
                Msg("")
                Gt("Amount")
                InputRow {
                    Input(
                        amount,
                        { amount = it },
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
                            .press { unit = (unit + 1) % UNITS.size }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        Arrangement.spacedBy(5.dp),
                        Alignment.CenterVertically,
                    ) {
                        Txt(UNITS[unit], size = 13.5f, weight = 600)
                        Icon(Ic.updown, 14.dp, C.dim)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
                    Arrangement.SpaceBetween,
                ) {
                    Txt("≈ $102.76", color = C.muted, size = 13f)
                    BasicText(
                        buildAnnotatedString {
                            append("Available 0.01250000 BTC · ")
                            withStyle(SpanStyle(color = C.accent, fontWeight = FontWeight.SemiBold)) {
                                append("Max")
                            }
                        },
                        style = style(C.muted, 13f),
                    )
                }
                Gt("Network fee")
                FeePicker(fee) { fee = it }
                Msg(FEES[fee].second)
                Primary("Review") { sheet = true }
            }
        }
        ConfirmSheet(
            sheet,
            onClose = { sheet = false },
            onSent = {
                sheet = false
                nav.root(S.Home)
                nav.toast("Transaction broadcast")
            },
        )
    }
}

// ---------- receive ----------
@Composable
fun Receive() {
    val nav = LocalNav.current
    var a by rememberSaveable { mutableIntStateOf(0) }
    Scr {
        Bar("Receive")
        Body(scroll = false) {
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth(),
                Arrangement.spacedBy(22.dp),
                Alignment.CenterHorizontally,
            ) {
                Tag("Native SegWit")
                Box(Modifier.background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp)) {
                    Qr(QR_RECEIVE, 248.dp)
                }
                val parts = ADDRS[a].chunked(4)
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
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                Btn("Copy", Modifier.weight(1f), C.light, Color.Black, Ic.copy, 0.dp) {
                    nav.toast("Address copied")
                }
                Btn("Share", Modifier.weight(1f), C.surface, C.text, Ic.share, 0.dp) {
                    nav.toast("Share sheet opens")
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .press {
                        a = (a + 1) % ADDRS.size
                        nav.toast("New address")
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
            .padding(start = 16.dp, end = 16.dp, top = 20.dp)
            .clip(CircleShape)
            .background(if (on) C.accent else Color.Black.copy(alpha = .45f))
            .press(onClick = onClick)
            .padding(10.dp),
    ) { Icon(v, color = if (on) Color.Black else C.text) }
}

// A stand-in for the camera, as in wallet.html
@Composable
fun Scanner(hint: String) {
    val nav = LocalNav.current
    var torch by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            val side = 260.dp.toPx()
            val left = (size.width - side) / 2
            val top = size.height * .45f - side / 2
            val radius = 22.dp.toPx()
            val edge = 4.dp.toPx()
            val arm = 34.dp.toPx()
            val mid = edge / 2

            drawRect(
                Brush.radialGradient(
                    0f to Color(0xFF3A3A3A),
                    .7f to Color(0xFF121212),
                    center = Offset(size.width * .5f, size.height * .4f),
                    radius = size.height * .85f,
                ),
            )
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
            hint,
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
