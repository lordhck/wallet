package com.lordhck.btcwallet.ui

import android.app.Activity
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lordhck.btcwallet.Debug
import com.lordhck.btcwallet.Vault
import com.lordhck.btcwallet.WalletModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    PinCheck("pincheck", Backup),
    Security("security", Settings),
    ChangePin("changepin", Security),
    PinLength("pinlength", Security),
    Currency("currency", Settings),
    BtcUnit("unit", Settings),
    Advanced("advanced", Settings),
    DebugLog("debug-log", Advanced),
    Network("network", Advanced),
    AddrType("addrtype", Advanced),
    Server("server", Advanced),
}

val LocalNav = staticCompositionLocalOf<Nav> { error("no Nav") }

// How far a swipe back must travel
private const val SWIPE_DISTANCE = .4f
private const val SWIPE_SPEED = 1000f

// Away for longer than this and the wallet locks
private const val AUTO_LOCK_MS = 60_000L

// A stack of screens, the top one is showing
class Nav(start: S, val wallet: WalletModel) {
    private val stack = mutableStateListOf<S>()
    private var pinThen: S? = null
    private var pinToast: String? = null

    var toastText by mutableStateOf("")
        private set
    var toastId by mutableIntStateOf(0)
        private set

    // The seed, held in memory while the wallet is unlocked
    var words: List<String>? = null
    var restoring = false

    // True for a seed made in onboarding, false for a restored one, null otherwise
    var seedIsNew: Boolean? = null

    // A code from the scanner, waiting for Send to pick it up
    var scanned: String? = null

    // Hide balance on start: stays hidden until the balance is tapped
    var balanceHidden by mutableStateOf(false)

    // Set by the PIN length screen on its way to Change PIN
    var newPinLength: Int? = null

    val top: S get() = stack.last()
    val under: S? get() = stack.getOrNull(stack.size - 2) ?: top.parent
    val canBack: Boolean get() = under != null

    init {
        // builds the parent chain, so Back works
        stack.addAll(generateSequence(start) { it.parent }.toList().reversed())
    }

    fun show(s: S) {
        if (stack.lastOrNull() == s) return
        stack.add(s)
        Debug.note("screen ${s.id}")
    }

    fun back() {
        val prev = under ?: return
        stack.removeAt(stack.lastIndex)
        if (stack.isEmpty()) stack.add(prev)
        Debug.note("back to ${top.id}")
    }

    // Start over from a screen, like Home after onboarding
    fun root(s: S) {
        stack.clear()
        show(s)
    }

    fun unlocked(seed: List<String>) {
        words = seed
        balanceHidden = wallet.hideBalance
        wallet.open(seed, seedIsNew)
        seedIsNew = null
        root(S.Home)
    }

    fun lock() {
        Debug.note("auto lock")
        words = null
        wallet.close()
        root(S.Lock)
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
        if (then == S.Home) root(then) else if (then != null) show(then)
        if (done != null) toast(done)
    }
}

@Composable
fun App(start: String?) {
    val context = LocalContext.current
    val nav = remember {
        val first = if (Vault.exists(context)) S.Lock else S.Welcome
        Nav(S.values().firstOrNull { it.id == start } ?: first, WalletModel(context))
    }
    val saved = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(0) }
    var swipe by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    // read through this so a drag doesn't recompose the whole app every frame
    val swiping by remember { derivedStateOf { swipe > 0f } }
    val owner = LocalLifecycleOwner.current
    // keeps the wallet current on every screen, while the app is open
    LaunchedEffect(Unit) {
        while (true) {
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) nav.wallet.refresh()
            delay(30_000)
        }
    }
    val protect = nav.wallet.screenshotProtection
    SideEffect {
        val window = (context as Activity).window
        if (protect) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    DisposableEffect(owner) {
        var stopped = 0L
        val observer = LifecycleEventObserver { _, event ->
            Debug.note("app ${event.name}")
            if (event == Lifecycle.Event.ON_STOP) {
                stopped = SystemClock.elapsedRealtime()
                nav.wallet.stopped()
            }
            if (event == Lifecycle.Event.ON_START && stopped > 0 && nav.words != null &&
                SystemClock.elapsedRealtime() - stopped > AUTO_LOCK_MS
            ) {
                nav.lock()
            } else if (event == Lifecycle.Event.ON_START) {
                // picks up a sync the background cut short, without waiting for the next tick
                nav.wallet.refresh()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    CompositionLocalProvider(LocalNav provides nav) {
        BackHandler(nav.canBack) { nav.back() }
        Box(
            Modifier
                .fillMaxSize()
                .background(C.bg)
                .imePadding()
                .onSizeChanged { width = it.width }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (settling || !nav.canBack) {
                            return@awaitEachGesture
                        }
                        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            if (over > 0) change.consume()
                        } ?: return@awaitEachGesture
                        val speed = VelocityTracker()
                        horizontalDrag(start.id) { change ->
                            speed.addPosition(change.uptimeMillis, change.position)
                            swipe = (swipe + change.positionChange().x).coerceIn(0f, size.width.toFloat())
                            change.consume()
                        }
                        val leaving = swipe > size.width * SWIPE_DISTANCE ||
                            speed.calculateVelocity().x > SWIPE_SPEED
                        Debug.note("swipe ended at ${(swipe / size.width * 100).toInt()}%, going back: $leaving")
                        val target = if (leaving) size.width.toFloat() else 0f
                        settling = true
                        scope.launch {
                            animate(swipe, target, animationSpec = tween(150)) { value, _ -> swipe = value }
                            if (leaving) nav.back()
                            swipe = 0f
                            settling = false
                        }
                    }
                },
        ) {
            // Preview only: state the screen saved comes back when the swipe lands
            val under = nav.under
            if (swiping && under != null) {
                Box(Modifier.fillMaxSize().graphicsLayer { translationX = (swipe - width) / 4 }) {
                    Route(under)
                }
            }
            // moved in the draw phase, so a drag never touches layout
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = swipe }) {
                // keeps what was typed or picked while the screen is away
                val s = nav.top
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
        S.PinCheck -> PinCheck()
        S.Security -> Security()
        S.ChangePin -> ChangePin()
        S.PinLength -> PinLength()
        S.Currency -> Currency()
        S.BtcUnit -> BitcoinUnit()
        S.Advanced -> Advanced()
        S.DebugLog -> DebugLog()
        S.Network -> Network()
        S.AddrType -> AddrType()
        S.Server -> Server()
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
