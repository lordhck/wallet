package com.lordhck.btcwallet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Every screen, with its id and parent from wallet.html
enum class S(val id: String, val parent: S? = null) {
    Welcome("welcome"),
    ObSeed("ob-seed", Welcome),
    ObVerify("ob-verify", ObSeed),
    ObRestore("ob-restore", Welcome),
    ObPin("ob-pin", Welcome),
    ObBio("ob-bio", ObPin),
    Lock("lock"),
    Home("home"),
    Send("send", Home),
    Receive("receive", Home),
    Scanner("scanner", Send),
    NfcSign("nfc-sign", Home),
    Settings("settings", Home),
    Backup("backup", Settings),
    Seed("seed", Backup),
    TestBackup("testbackup", Backup),
    Import("import", Backup),
    ScannerImport("scanner-import", Import),
    ImportSeed("import-seed", Import),
    PrivKey("privkey", Import),
    Export("export", Backup),
    ExportQr("export-qr", Export),
    WatchOnly("watchonly", Export),
    PinCheck("pincheck", Backup),
    Security("security", Settings),
    ChangePin("changepin", Security),
    PinLength("pinlength", Security),
    Currency("currency", Settings),
    BtcUnit("unit", Settings),
    Advanced("advanced", Settings),
    Network("network", Advanced),
    AddrType("addrtype", Advanced),
    Server("server", Advanced),
    Donate("donate", Settings),
}

val LocalNav = staticCompositionLocalOf<Nav> { error("no Nav") }

// One screen on top, parent slides under
class Nav(start: S) {
    private val stack = mutableStateListOf<S>()
    private var pinThen: S? = null
    private var pinToast: String? = null

    var forward by mutableStateOf(true)
        private set
    var z by mutableFloatStateOf(0f)
        private set
    var toastText by mutableStateOf("")
        private set
    var toastId by mutableIntStateOf(0)
        private set

    // null on mainnet, otherwise the test network's name
    var test by mutableStateOf<String?>(null)

    val top: S get() = stack.last()
    val canBack: Boolean get() = stack.size > 1 || top.parent != null

    init {
        // builds the parent chain, so Back works
        stack.addAll(generateSequence(start) { it.parent }.toList().reversed())
    }

    fun show(s: S) {
        if (stack.lastOrNull() == s) return
        forward = true
        z++
        stack.add(s)
    }

    fun back() {
        val cur = stack.last()
        val prev = stack.getOrNull(stack.size - 2) ?: cur.parent ?: return
        stack.removeAt(stack.lastIndex)
        if (stack.isEmpty()) stack.add(prev)
        forward = false
        z--
    }

    // Start over from a screen, like Home after onboarding
    fun root(s: S) {
        stack.clear()
        show(s)
    }

    fun toast(text: String) {
        toastText = text
        toastId++
    }

    // Ask for the PIN, then carry on to a screen
    fun pin(then: S, done: String? = null) {
        pinThen = then
        pinToast = done
        show(S.PinCheck)
    }

    fun pinChecked() {
        val then = pinThen
        val done = pinToast
        pinThen = null
        pinToast = null
        stack.removeAt(stack.lastIndex)
        forward = false
        z--
        if (then == S.Home) root(then) else if (then != null) show(then)
        if (done != null) toast(done)
    }
}

@Composable
fun App(start: String?) {
    val nav = remember { Nav(S.values().firstOrNull { it.id == start } ?: S.Home) }
    val saved = rememberSaveableStateHolder()
    CompositionLocalProvider(LocalNav provides nav) {
        BackHandler(nav.canBack) { nav.back() }
        Box(Modifier.fillMaxSize().background(C.bg).imePadding()) {
            AnimatedContent(
                targetState = nav.top,
                transitionSpec = {
                    val ease = tween<IntOffset>(250, easing = CubicBezierEasing(.25f, .1f, .25f, 1f))
                    val move = if (nav.forward) {
                        slideInHorizontally(ease) { it } togetherWith slideOutHorizontally(ease) { -it / 4 }
                    } else {
                        slideInHorizontally(ease) { -it / 4 } togetherWith slideOutHorizontally(ease) { it }
                    }
                    move.apply { targetContentZIndex = nav.z }
                },
                label = "screen",
            ) { s ->
                // keeps what was typed or picked, like the DOM does
                saved.SaveableStateProvider(s.name) { Route(s) }
            }
            Toast(nav)
        }
    }
}

@Composable
private fun Route(s: S) {
    when (s) {
        S.Welcome -> Welcome()
        S.ObSeed -> ObSeed()
        S.ObVerify -> ObVerify()
        S.ObRestore -> ObRestore()
        S.ObPin -> ObPin()
        S.ObBio -> ObBio()
        S.Lock -> Lock()
        S.Home -> Home()
        S.Send -> Send()
        S.Receive -> Receive()
        S.Scanner -> Scanner("Point the camera at a Bitcoin QR code")
        S.NfcSign -> NfcSign()
        S.Settings -> Settings()
        S.Backup -> Backup()
        S.Seed -> SeedPhrase()
        S.TestBackup -> TestBackup()
        S.Import -> Import()
        S.ScannerImport -> Scanner("Scan a SeedQR, seed phrase or private key")
        S.ImportSeed -> ImportSeed()
        S.PrivKey -> PrivKey()
        S.Export -> Export()
        S.ExportQr -> ExportQr()
        S.WatchOnly -> WatchOnly()
        S.PinCheck -> PinCheck()
        S.Security -> Security()
        S.ChangePin -> ChangePin()
        S.PinLength -> PinLength()
        S.Currency -> Currency()
        S.BtcUnit -> BitcoinUnit()
        S.Advanced -> Advanced()
        S.Network -> Network()
        S.AddrType -> AddrType()
        S.Server -> Server()
        S.Donate -> Donate()
    }
}

@Composable
private fun BoxScope.Toast(nav: Nav) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(nav.toastId) {
        if (nav.toastId > 0) {
            shown = true
            delay(1800)
            shown = false
        }
    }
    AnimatedVisibility(
        shown,
        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 40.dp),
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it },
        exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { it },
    ) {
        Row(
            Modifier
                .background(Color(0xFF2A2A31), CircleShape)
                .padding(horizontal = 18.dp, vertical = 11.dp),
            Arrangement.spacedBy(8.dp),
            Alignment.CenterVertically,
        ) {
            Icon(Ic.check, 16.dp, C.green)
            Txt(nav.toastText, size = 14.5f, weight = 600)
        }
    }
}
