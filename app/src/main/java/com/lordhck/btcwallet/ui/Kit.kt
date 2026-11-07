package com.lordhck.btcwallet.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor

// ---------- tokens ----------
object C {
    val bg = Color(0xFF0C0C0E)
    val surface = Color(0xFF17171B)
    val surface2 = Color(0xFF1D1D22)
    val surface3 = Color(0xFF26262D)
    val line = Color(0xFF232329)
    val text = Color.White
    val muted = Color(0xFF6B6B75)
    val dim = Color(0xFF8A8A94)
    val soft = Color(0xFFA3A3AD)
    val placeholder = Color(0xFF55555E)
    val focus = Color(0xFF35353D)
    val btc = Color(0xFFF7931A)
    val accent = Color(0xFFE5E5E5)
    val accentA = Color.White.copy(alpha = .14f)
    val green = Color(0xFF22C55E)
    val red = Color(0xFFEF4444)
    val light = Color(0xFFECECEC)
}

// ---------- text ----------
fun style(
    color: Color = C.text,
    size: Float = 15f,
    weight: Int = 400,
    mono: Boolean = false,
    align: TextAlign = TextAlign.Start,
    line: Float = 0f,
    spacing: Float = 0f,
    tabular: Boolean = false,
) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = FontWeight(weight),
    fontFamily = if (mono) FontFamily.Monospace else null,
    textAlign = align,
    lineHeight = if (line > 0f) (size * line).sp else TextUnit.Unspecified,
    letterSpacing = spacing.em,
    fontFeatureSettings = if (tabular) "tnum" else null,
)

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = C.text,
    size: Float = 15f,
    weight: Int = 400,
    mono: Boolean = false,
    align: TextAlign = TextAlign.Start,
    line: Float = 0f,
    spacing: Float = 0f,
    tabular: Boolean = false,
) {
    BasicText(text, modifier, style(color, size, weight, mono, align, line, spacing, tabular))
}

@Composable
fun Icon(v: Int, size: Dp = 22.dp, color: Color = C.text, modifier: Modifier = Modifier) {
    Image(painterResource(v), null, modifier.size(size), colorFilter = ColorFilter.tint(color))
}

@Composable
fun Logo(size: Dp) {
    Image(painterResource(Ic.app), null, Modifier.size(size))
}

// Tap with a CSS :active style background instead of a ripple
@Composable
fun Modifier.press(
    down: Color = Color.Transparent,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    return background(if (pressed) down else Color.Transparent)
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

// ---------- screen structure ----------
@Composable
fun Scr(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(C.bg), content = content)
}

@Composable
fun Bar(title: String = "", back: Boolean = true) {
    val nav = LocalNav.current
    Box(
        Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp).height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (back) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .press(C.surface2) { nav.back() }
                    .padding(9.dp),
            ) { Icon(Ic.back, color = C.soft) }
        }
        Txt(title, size = 17f, weight = 600)
    }
}

// heightIn keeps weight(1f) spacers working inside the scroll
@Composable
fun ColumnScope.Body(
    scroll: Boolean = true,
    bottom: Dp = 32.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        Column(
            Modifier
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom),
            content = content,
        )
    }
}

@Composable
fun Big(text: String) {
    Txt(
        text,
        Modifier.padding(start = 4.dp, end = 4.dp, top = 28.dp, bottom = 12.dp),
        size = 32f,
        weight = 700,
        spacing = -.01f,
    )
}

@Composable
fun Gt(text: String, first: Boolean = false) {
    Txt(
        text.uppercase(),
        Modifier.padding(start = 4.dp, top = if (first) 0.dp else 24.dp, bottom = 8.dp),
        C.muted,
        12f,
        600,
        spacing = .06f,
    )
}

@Composable
fun Gap() = Gt(" ")

@Composable
fun Note(text: String) {
    Txt(text, Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp), C.muted, 13f, line = 1.35f)
}

@Composable
fun Intro(text: String) {
    Txt(text, Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 20.dp), C.soft, 14.5f, line = 1.4f)
}

@Composable
fun Msg(text: String, err: Boolean = false) {
    Txt(
        text,
        Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp).heightIn(min = 18.dp),
        if (err) C.red else C.muted,
        13f,
    )
}

// ---------- groups / rows ----------
@Composable
fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.surface),
        content = content,
    )
}

@Composable
fun Cell(
    label: String,
    sub: String? = null,
    value: String? = null,
    divider: Boolean = false,
    off: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().alpha(if (off) .45f else 1f)) {
        if (divider) Spacer(Modifier.fillMaxWidth().height(1.dp).background(C.surface3))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .press(Color(0xFF202026), !off, onClick)
                .padding(horizontal = 16.dp),
            Arrangement.spacedBy(12.dp),
            Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Txt(label)
                if (sub != null) Txt(sub, Modifier.padding(top = 2.dp), C.muted, 12.5f)
            }
            if (value != null) Txt(value, color = C.dim)
            trailing?.invoke()
        }
    }
}

// Row with a chevron that opens another screen
@Composable
fun Go(
    label: String,
    sub: String? = null,
    value: String? = null,
    divider: Boolean = false,
    onClick: () -> Unit,
) {
    Cell(label, sub, value, divider, trailing = { Icon(Ic.chev, 16.dp, C.dim) }, onClick = onClick)
}

class Opt(val label: String, val sub: String? = null, val off: Boolean = false)

// One choice per group. Ticks it, then goes back after a beat.
@Composable
fun Picks(opts: List<Opt>, initial: Int, onPick: (Int) -> Unit = {}) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var on by rememberSaveable { mutableIntStateOf(initial) }
    var leaving by remember { mutableStateOf(false) }
    Group {
        opts.forEachIndexed { i, o ->
            Cell(
                o.label,
                o.sub,
                divider = i > 0,
                off = o.off,
                trailing = { Icon(Ic.check, 20.dp, C.accent, Modifier.alpha(if (i == on) 1f else 0f)) },
            ) {
                on = i
                onPick(i)
                if (!leaving) {
                    leaving = true
                    scope.launch {
                        delay(180)
                        nav.back()
                    }
                }
            }
        }
    }
}

@Composable
fun Toggle(on: Boolean) {
    val x by animateDpAsState(if (on) 18.dp else 0.dp, tween(200), label = "toggle")
    val track by animateColorAsState(if (on) C.accent else C.surface3, tween(200), label = "track")
    val knob by animateColorAsState(if (on) Color.Black else C.dim, tween(200), label = "knob")
    Box(Modifier.size(46.dp, 28.dp).background(track, CircleShape)) {
        Box(Modifier.offset(x = 3.dp + x, y = 3.dp).size(22.dp).background(knob, CircleShape))
    }
}

@Composable
fun ToggleCell(label: String, initial: Boolean, divider: Boolean = false) {
    var on by rememberSaveable { mutableStateOf(initial) }
    Cell(label, divider = divider, trailing = { Toggle(on) }) { on = !on }
}

// ---------- inputs ----------
@Composable
fun Input(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier.fillMaxWidth(),
    mono: Boolean = false,
    area: Boolean = false,
    big: Boolean = false,
    bare: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val size = if (big) 25f else if (mono && !area) 13.5f else 15f
    val look = style(C.text, size, if (big) 600 else 400, mono, line = if (area) 1.6f else 0f, tabular = big)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        textStyle = look,
        singleLine = !area,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        cursorBrush = SolidColor(C.text),
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (area) Modifier.heightIn(min = 140.dp) else Modifier)
                    .then(
                        if (bare) Modifier
                        else Modifier
                            .background(C.surface, shape)
                            .border(1.dp, if (focused) C.focus else Color.Transparent, shape),
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) BasicText(placeholder, style = look.copy(color = C.placeholder))
                field()
            }
        },
    )
}

@Composable
fun InputRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(C.surface, RoundedCornerShape(14.dp))
            .padding(end = 6.dp),
        Arrangement.spacedBy(2.dp),
        Alignment.CenterVertically,
        content,
    )
}

@Composable
fun IconBtn(v: Int, color: Color = C.soft, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .press(C.surface3, onClick = onClick)
            .padding(9.dp),
    ) { Icon(v, color = color) }
}

@Composable
fun Chip(label: String, onClick: () -> Unit) {
    Txt(
        label,
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(C.surface2)
            .press(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        C.soft,
        14f,
    )
}

fun wordCount(s: String): String {
    val n = if (s.isBlank()) 0 else s.trim().split(Regex("\\s+")).size
    return if (n == 1) "1 word" else "$n words"
}

// ---------- buttons ----------
@Composable
fun Btn(
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    bg: Color = C.accent,
    fg: Color = Color.Black,
    icon: Int? = null,
    top: Dp = 24.dp,
    pad: PaddingValues = PaddingValues(15.dp),
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val down = if (bg == Color.Transparent) bg else lerp(bg, Color.White, .1f)
    Row(
        modifier
            .padding(top = top)
            .alpha(if (enabled) 1f else .45f)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .press(down, enabled, onClick)
            .padding(pad),
        Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, 20.dp, fg)
        Txt(label, color = fg, size = 16f, weight = 600)
    }
}

@Composable
fun Primary(label: String, enabled: Boolean = true, onClick: () -> Unit) =
    Btn(label, enabled = enabled, onClick = onClick)

@Composable
fun Ghost(label: String, onClick: () -> Unit) =
    Btn(label, bg = Color.Transparent, fg = C.soft, top = 8.dp, onClick = onClick)

// ---------- seed + QR ----------
@Composable
fun Qr(text: String, side: Dp) {
    val matrix = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    }
    // whole-pixel modules keep the code crisp enough to scan
    Canvas(Modifier.size(side)) {
        val cell = floor(size.width / matrix.width)
        val start = floor((size.width - cell * matrix.width) / 2)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color.Black, Offset(start + x * cell, start + y * cell), Size(cell, cell))
            }
        }
    }
}

@Composable
private fun RowScope.SeedWord(words: List<String>, i: Int) {
    Row(
        Modifier
            .weight(1f)
            .background(C.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        Arrangement.spacedBy(10.dp),
    ) {
        Txt("${i + 1}", Modifier.width(18.dp), C.muted, mono = true, align = TextAlign.End)
        Txt(words[i], mono = true)
    }
}

// Hidden again each time the screen opens
@Composable
fun SeedGrid(words: List<String>, onReveal: () -> Unit = {}) {
    var shown by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))) {
        Column(
            Modifier.fillMaxWidth().then(if (shown) Modifier else Modifier.blur(9.dp)),
            Arrangement.spacedBy(8.dp),
        ) {
            for (i in words.indices step 2) {
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    SeedWord(words, i)
                    SeedWord(words, i + 1)
                }
            }
        }
        if (!shown) {
            // blur needs Android 12, older phones get a solid cover
            val veil = C.bg.copy(alpha = if (Build.VERSION.SDK_INT >= 31) .45f else 1f)
            Column(
                Modifier.matchParentSize().background(veil).press {
                    shown = true
                    onReveal()
                },
                Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                Alignment.CenterHorizontally,
            ) {
                Icon(Ic.eye, color = C.soft)
                Txt("Tap to reveal", color = C.soft, size = 14f, weight = 600)
            }
        }
    }
}
