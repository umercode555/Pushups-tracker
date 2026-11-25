package com.pushr.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max

val Ink = Color(0xFF0A0A0A)
val Paper = Color(0xFFF7F7F7)
val Mist = Color(0xFFC4C6C6)
val Sub = Color(0xFF8A8A8E)
val Red = Color(0xFFFF2D3A)
val Soft = Color(0xFFEDEDED)

fun fmtTime(ms: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))
fun fmtDay(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
fun fmtClock(ms: Long): String {
    val m = ms / 60000
    val s = (ms / 1000) % 60
    val h = (ms / 10) % 100
    return "%02d:%02d.%02d".format(m, s, h)
}

@Composable
fun T(
    s: String, size: Int, w: FontWeight = FontWeight.Medium, c: Color = Ink,
    ls: Float = 0f, m: Modifier = Modifier, align: TextAlign? = null
) {
    Text(s, m, color = c, fontSize = size.sp, fontWeight = w, letterSpacing = ls.sp, textAlign = align)
}

private fun perms(): Array<String> = buildList {
    add(Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
fun PushrApp() {
    val ctx = LocalContext.current
    var sessions by remember { mutableStateOf(Store.all(ctx)) }
    var tab by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(Unit) { launcher.launch(perms()) }

    MaterialTheme {
        Surface(color = Paper, modifier = Modifier.fillMaxSize()) {
            if (running) {
                CounterScreen { s ->
                    if (s != null) {
                        Store.add(ctx, s)
                        sessions = Store.all(ctx)
                    }
                    running = false
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).statusBarsPadding()) {
                        when (tab) {
                            0 -> TodayScreen(sessions) {
                                if (granted) running = true else launcher.launch(perms())
                            }
                            1 -> StatsScreen(sessions)
                            else -> HistoryScreen(sessions)
                        }
                    }
                    BottomBar(tab) { tab = it }
                }
            }
        }
    }
}

@Composable
fun BottomBar(sel: Int, onSel: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf("Today", "Stats", "History").forEachIndexed { i, n ->
            val on = i == sel
            Box(
                Modifier.clip(CircleShape)
                    .background(if (on) Ink else Color.Transparent)
                    .clickable { onSel(i) }
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                T(n, 14, if (on) FontWeight.Bold else FontWeight.Medium, if (on) Color.White else Sub)
            }
        }
    }
}

@Composable
fun Card(m: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        m.shadow(6.dp, RoundedCornerShape(26.dp), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(RoundedCornerShape(26.dp)).background(Color.White).padding(18.dp),
        content = content
    )
}

@Composable
fun TodayScreen(sessions: List<Session>, onStart: () -> Unit) {
    val todayDate = LocalDate.now()
    val today = sessions.filter { Store.day(it.start) == todayDate }.sortedBy { it.start }
    val total = today.sumOf { it.count }
    val (cur, best) = Store.streaks(sessions)
    val all = sessions.sumOf { it.count }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        T("Pushr", 28, FontWeight.Black)
        T(fmtDay(todayDate), 14, c = Sub)
        Spacer(Modifier.height(18.dp))

        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp))
                .background(Brush.radialGradient(listOf(Color.White, Mist)))
                .padding(vertical = 34.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T("Today", 14, FontWeight.Bold, Sub, 1.5f)
                T("$total", 110, FontWeight.Black, ls = -3f)
                T("push-ups", 16, c = Sub)
            }
        }
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.weight(1f)) {
                T("Streak", 13, c = Sub)
                T("$cur ${if (cur == 1) "day" else "days"}", 26, FontWeight.Black)
                T("Best $best", 13, c = Sub)
            }
            Card(Modifier.weight(1f)) {
                T("All time", 13, c = Sub)
                T("$all", 26, FontWeight.Black)
                T("push-ups", 13, c = Sub)
            }
        }
        Spacer(Modifier.height(16.dp))

        Box(
            Modifier.fillMaxWidth().height(64.dp).clip(CircleShape).background(Ink).clickable(onClick = onStart),
            contentAlignment = Alignment.Center
        ) { T("Start", 18, FontWeight.Black, Color.White, 1f) }

        Spacer(Modifier.height(22.dp))
        T("Today's sets", 16, FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (today.isEmpty()) {
            T("No sets yet. Tap Start and face the camera.", 14, c = Sub)
        } else {
            Card(Modifier.fillMaxWidth()) {
                var running = 0
                today.forEachIndexed { i, s ->
                    running += s.count
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T("${fmtTime(s.start)} – ${fmtTime(s.end)}", 15, FontWeight.Bold)
                            T("Total so far: $running", 12, c = Sub)
                        }
                        T("${s.count}", 24, FontWeight.Black)
                        T(" push-ups", 13, c = Sub)
                    }
                    if (i < today.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Soft))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        T("Daily summary notification: 12:00 PM", 12, c = Sub)
    }
}

@Composable
fun StatsScreen(sessions: List<Session>) {
    var range by remember { mutableIntStateOf(7) }
    val daily = remember(sessions) { Store.daily(sessions) }
    val today = LocalDate.now()
    val values = (range - 1 downTo 0).map { daily[today.minusDays(it.toLong())] ?: 0 }
    val labels = (range - 1 downTo 0).map {
        val d = today.minusDays(it.toLong())
        if (range <= 7) d.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
        else d.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
    val sum = values.sum()
    val prev = (range * 2 - 1 downTo range).sumOf { daily[today.minusDays(it.toLong())] ?: 0 }
    val diff = sum - prev
    val (cur, best) = Store.streaks(sessions)
    val bestDay = daily.values.maxOrNull() ?: 0
    val activeDays = daily.size
    val avg = if (activeDays == 0) 0 else sessions.sumOf { it.count } / activeDays

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        T("Progress", 28, FontWeight.Black)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.clip(CircleShape).background(Soft).padding(4.dp)
        ) {
            listOf(7, 30).forEach { r ->
                val on = r == range
                Box(
                    Modifier.clip(CircleShape).background(if (on) Ink else Color.Transparent)
                        .clickable { range = r }.padding(horizontal = 22.dp, vertical = 8.dp)
                ) { T("${r}D", 14, FontWeight.Bold, if (on) Color.White else Sub) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth()) {
            T("Last $range days", 13, c = Sub)
            Row(verticalAlignment = Alignment.Bottom) {
                T("$sum", 40, FontWeight.Black)
                T("  push-ups", 14, c = Sub, m = Modifier.padding(bottom = 8.dp))
            }
            val sign = if (diff > 0) "▲ +$diff" else if (diff < 0) "▼ $diff" else "– 0"
            T("$sign vs previous $range days", 13, FontWeight.Bold, if (diff < 0) Red else Ink)
            Spacer(Modifier.height(10.dp))
            LineGraph(values, labels, Modifier.fillMaxWidth().height(220.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.weight(1f)) { T("Best day", 13, c = Sub); T("$bestDay", 26, FontWeight.Black) }
            Card(Modifier.weight(1f)) { T("Average / active day", 13, c = Sub); T("$avg", 26, FontWeight.Black) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.weight(1f)) { T("Streak", 13, c = Sub); T("$cur days", 26, FontWeight.Black) }
            Card(Modifier.weight(1f)) { T("Best streak", 13, c = Sub); T("$best days", 26, FontWeight.Black) }
        }
    }
}

@Composable
fun LineGraph(values: List<Int>, labels: List<String>, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val lab = TextStyle(fontSize = 10.sp, color = Sub)
    val valStyle = TextStyle(fontSize = 11.sp, color = Ink, fontWeight = FontWeight.Bold)
    Canvas(modifier) {
        val padB = 24.dp.toPx()
        val padT = 22.dp.toPx()
        val padX = 14.dp.toPx()
        val w = size.width - padX * 2
        val h = size.height - padB - padT
        val mx = max(values.maxOrNull() ?: 1, 1).toFloat()
        val n = values.size
        fun px(i: Int): Float = padX + if (n <= 1) w / 2 else w * i / (n - 1)
        fun py(v: Int): Float = padT + h * (1f - v / mx)

        for (g in 0..2) {
            val y = padT + h * g / 2f
            drawLine(Color(0xFFE6E6E6), Offset(padX, y), Offset(padX + w, y), 1.dp.toPx())
        }
        val line = Path()
        for (i in 0 until n) {
            if (i == 0) line.moveTo(px(0), py(values[0]))
            else {
                val x0 = px(i - 1)
                val x1 = px(i)
                val mid = (x0 + x1) / 2
                line.cubicTo(mid, py(values[i - 1]), mid, py(values[i]), x1, py(values[i]))
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(px(n - 1), padT + h)
            lineTo(px(0), padT + h)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(Ink.copy(alpha = 0.18f), Color.Transparent), startY = padT, endY = padT + h))
        drawPath(line, Ink, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        for (i in 0 until n) {
            val c = Offset(px(i), py(values[i]))
            if (n <= 7 || i == n - 1) {
                drawCircle(Color.White, 6.dp.toPx(), c)
                drawCircle(if (i == n - 1) Red else Ink, 4.dp.toPx(), c)
            }
            if (n <= 7 && values[i] > 0) {
                val r = tm.measure(values[i].toString(), valStyle)
                drawText(r, topLeft = Offset(c.x - r.size.width / 2f, c.y - r.size.height - 8.dp.toPx()))
            }
        }
        val idx = if (n <= 7) (0 until n).toList() else listOf(0, n / 3, 2 * n / 3, n - 1)
        for (i in idx) {
            val r = tm.measure(labels[i], lab)
            val x = (px(i) - r.size.width / 2f).coerceIn(0f, size.width - r.size.width)
            drawText(r, topLeft = Offset(x, padT + h + 6.dp.toPx()))
        }
    }
}

@Composable
fun HistoryScreen(sessions: List<Session>) {
    val groups = sessions.groupBy { Store.day(it.start) }.toSortedMap(compareByDescending { it })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        T("History", 28, FontWeight.Black)
        Spacer(Modifier.height(14.dp))
        if (groups.isEmpty()) T("Nothing here yet.", 14, c = Sub)
        groups.forEach { (d, list) ->
            Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(fmtDay(d), 16, FontWeight.Bold, m = Modifier.weight(1f))
                    T("${list.sumOf { it.count }}", 24, FontWeight.Black)
                    T(" total", 13, c = Sub)
                }
                Spacer(Modifier.height(6.dp))
                list.sortedBy { it.start }.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        T("${fmtTime(s.start)} – ${fmtTime(s.end)}", 14, c = Sub, m = Modifier.weight(1f))
                        T("${s.count} push-ups", 14, FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun Glass(
    m: Modifier = Modifier, bg: Color = Color(0xFFF4F4F4), onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        m.shadow(5.dp, CircleShape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
            .clip(CircleShape).background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center, content = content
    )
}

@Composable
fun CounterScreen(onDone: (Session?) -> Unit) {
    val view = LocalView.current
    var setNo by remember { mutableIntStateOf(1) }
    var setCount by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var showCam by remember { mutableStateOf(false) }
    var face by remember { mutableStateOf(false) }
    val start = remember { System.currentTimeMillis() }
    val pausedNow = rememberUpdatedState(paused)
    val counter = remember { RepCounter() }
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }
    val pulse = remember { Animatable(1f) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false; tone.release() }
    }
    fun finish() {
        onDone(if (total > 0) Session(start, System.currentTimeMillis(), total) else null)
    }
    BackHandler { finish() }

    LaunchedEffect(paused) {
        if (!paused) {
            var last = System.nanoTime()
            while (true) {
                delay(30)
                val n = System.nanoTime()
                elapsed += (n - last) / 1_000_000
                last = n
            }
        }
    }
    LaunchedEffect(setCount) {
        if (setCount > 0) {
            pulse.snapTo(1.07f)
            pulse.animateTo(1f, spring(dampingRatio = 0.45f))
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White, Color(0xFFC4C6C6))))) {
        CameraLayer(
            show = showCam,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp)
                .size(96.dp, 128.dp).clip(RoundedCornerShape(22.dp))
        ) { h ->
            face = h != null
            if (h != null && !pausedNow.value && counter.feed(h, SystemClock.elapsedRealtime())) {
                setCount++
                total++
                try { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 90) } catch (_: Exception) {}
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }

        Glass(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).size(42.dp),
            onClick = { showCam = !showCam }
        ) {
            T("◉", 18)
            Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(8.dp).clip(CircleShape)
                    .background(if (face) Color(0xFF22C55E) else Red)
            )
        }

        if (!face) {
            T(
                "Show your face to the camera", 12, c = Sub,
                m = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 28.dp)
            )
        }

        Column(
            Modifier.align(Alignment.Center).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            T("SET $setNo", 12, FontWeight.Bold, Sub, 2f)
            val big = TextStyle(
                fontSize = 150.sp, lineHeight = 150.sp, fontWeight = FontWeight.Black,
                letterSpacing = (-5).sp, color = Ink
            )
            Text("$setCount", style = big)
            Box(Modifier.height(80.dp).clip(RoundedCornerShape(0.dp))) {
                Text(
                    "$setCount", style = big,
                    modifier = Modifier
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .graphicsLayer { scaleY = -1f; compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f))),
                                blendMode = BlendMode.DstIn
                            )
                        }
                )
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Glass(Modifier.size(46.dp), onClick = { setNo++; setCount = 0 }) { T("☾", 20) }
                Glass(Modifier.height(46.dp)) {
                    Row(Modifier.padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                        T("TOTAL", 12, FontWeight.Bold, Sub, 1.5f)
                        T("  $total", 15, FontWeight.Black)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Glass(Modifier.height(46.dp)) {
                    Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                        T("◷", 16, c = Sub)
                        T("  ${fmtClock(elapsed)}", 14, FontWeight.Bold)
                    }
                }
                Glass(Modifier.size(46.dp), onClick = { paused = !paused }) {
                    if (paused) T("▶", 15)
                    else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(2) { Box(Modifier.size(4.dp, 15.dp).clip(RoundedCornerShape(2.dp)).background(Ink)) }
                    }
                }
                Glass(Modifier.size(46.dp), bg = Red, onClick = { finish() }) {
                    T("✕", 17, FontWeight.Black, Color.White)
                }
            }
        }
    }
}

@Composable
fun CameraLayer(show: Boolean, modifier: Modifier, onFace: (Float?) -> Unit) {
    val ctx = LocalContext.current
    val owner = ctx as ComponentActivity
    val cb = rememberUpdatedState(onFace)
    val exec = remember { Executors.newSingleThreadExecutor() }
    val pv = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(Unit) {
        val fut = ProcessCameraProvider.getInstance(ctx)
        var prov: ProcessCameraProvider? = null
        fut.addListener({
            val p = fut.get()
            prov = p
            val prev = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
            val ana = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(exec, FaceAnalyzer { h -> cb.value(h) }) }
            try {
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, prev, ana)
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { prov?.unbindAll(); exec.shutdown() }
    }
    AndroidView(factory = { pv }, modifier = modifier.alpha(if (show) 1f else 0f))
}
