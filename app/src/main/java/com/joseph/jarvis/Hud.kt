package com.joseph.jarvis

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------- palette

object Hud {
    val Bg = Color(0xFF02080D)
    val BgLight = Color(0xFF04141E)
    val Cyan = Color(0xFF2EE6FF)
    val CyanDim = Color(0xFF1B8FA3)
    val CyanFaint = Color(0xFF0E4652)
    val Text = Color(0xFFC9F7FF)
    val Red = Color(0xFFFF3B4E)
    val Mono = FontFamily.Monospace
}

/** Upper-case, spaced-out label text, like the readouts on a heads-up display. */
@Composable
fun HudLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Hud.Cyan,
    size: TextUnit = 11.sp,
    align: TextAlign? = null,
) {
    Text(
        text.uppercase(),
        color = color,
        fontSize = size,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Medium,
        textAlign = align,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------- background

/** Dark gradient, a faint grid and scattered points of light. Drawn once, costs almost nothing. */
@Composable
fun HudBackground(modifier: Modifier = Modifier) {
    val dots = remember {
        val r = Random(7)
        List(70) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Hud.Bg, Hud.BgLight, Hud.Bg)))
        val step = 32.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(Hud.Cyan.copy(alpha = 0.035f), Offset(x, 0f), Offset(x, size.height), 1f); x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(Hud.Cyan.copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y), 1f); y += step
        }
        dots.forEach { (fx, fy, s) ->
            val c = Offset(fx * size.width, fy * size.height)
            drawCircle(Hud.Cyan.copy(alpha = 0.08f + s * 0.12f), radius = 6f + s * 10f, center = c)
            drawCircle(Hud.Cyan.copy(alpha = 0.4f + s * 0.5f), radius = 1.5f + s * 2f, center = c)
        }
    }
}

// ---------------------------------------------------------------- panels

val PanelShape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)

/** A bordered box with clipped corners, the basic building block of every screen. */
@Composable
fun HudPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .border(1.dp, Hud.CyanDim.copy(alpha = 0.7f), PanelShape)
            .background(Hud.Cyan.copy(alpha = 0.04f), PanelShape)
            .padding(12.dp)
    ) { content() }
}

/** Frame for the inner screens: a "back to core" bar and a title. */
@Composable
fun HudScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 28.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.clickable(onClick = onBack).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniCore(Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                HudLabel("◂ Core", color = Hud.CyanDim)
            }
            Spacer(Modifier.weight(1f))
            HudLabel(title, size = 16.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(
            Brush.horizontalGradient(listOf(Color.Transparent, Hud.Cyan, Color.Transparent))
        ))
        Box(Modifier.weight(1f)) { content() }
    }
}

// ---------------------------------------------------------------- gauges

/** A ring that fills to [value] (0 to 1), with a readout in the middle. */
@Composable
fun RingGauge(label: String, value: Float, readout: String, modifier: Modifier = Modifier, warn: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx()
                val inset = stroke
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val tl = Offset(inset, inset)
                drawArc(Hud.CyanFaint, 135f, 270f, false, tl, arcSize, style = Stroke(stroke))
                val col = if (warn) Hud.Red else Hud.Cyan
                // soft glow under the bright arc
                drawArc(col.copy(alpha = 0.25f), 135f, 270f * value, false, tl, arcSize, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
                drawArc(col, 135f, 270f * value, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                drawCircle(Hud.CyanDim.copy(alpha = 0.5f), radius = size.minDimension / 2 - stroke * 2.6f, style = Stroke(1f))
            }
            Text(readout, color = Hud.Text, fontFamily = Hud.Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        HudLabel(label, size = 10.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

// ---------------------------------------------------------------- the core

/** Rotation angles shared by the big core and the little one in headers. */
@Composable
private fun spin(durationMs: Int, reverse: Boolean = false): Float {
    val t = rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(
        initialValue = 0f,
        targetValue = if (reverse) -360f else 360f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing)),
        label = "a",
    )
    return a
}

@Composable
fun MiniCore(modifier: Modifier = Modifier) {
    val a = spin(6000)
    Canvas(modifier) {
        val r = size.minDimension / 2
        drawCircle(Hud.Cyan.copy(alpha = 0.3f), r * 0.55f)
        drawCircle(Hud.Cyan, r * 0.3f)
        rotate(a) {
            drawArc(Hud.Cyan, 0f, 100f, false, Offset(2f, 2f), Size(size.width - 4f, size.height - 4f), style = Stroke(3f))
            drawArc(Hud.Cyan, 180f, 100f, false, Offset(2f, 2f), Size(size.width - 4f, size.height - 4f), style = Stroke(3f))
        }
    }
}

/** The big animated centrepiece: rotating rings, tick marks and a glowing centre. */
@Composable
fun Core(modifier: Modifier = Modifier, center: @Composable () -> Unit) {
    val a1 = spin(40_000)
    val a2 = spin(24_000, reverse = true)
    val a3 = spin(9_000)
    val pulseT = rememberInfiniteTransition(label = "pulse")
    val pulse by pulseT.animateFloat(
        0.75f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "p"
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2

            fun ring(rad: Float, color: Color, w: Float, effect: PathEffect? = null) =
                drawCircle(color, rad, c, style = Stroke(w, pathEffect = effect))

            fun arc(rad: Float, start: Float, sweep: Float, color: Color, w: Float) =
                drawArc(color, start, sweep, false, Offset(c.x - rad, c.y - rad), Size(rad * 2, rad * 2), style = Stroke(w))

            // outer hairline + slow tick ring
            ring(r * 0.99f, Hud.CyanDim.copy(alpha = 0.5f), 1.5f)
            rotate(a1, c) {
                for (i in 0 until 72) {
                    val ang = Math.toRadians(i * 5.0)
                    val long = i % 6 == 0
                    val r1 = r * 0.93f
                    val r2 = r * (if (long) 0.84f else 0.89f)
                    drawLine(
                        Hud.Cyan.copy(alpha = if (long) 0.9f else 0.45f),
                        Offset(c.x + r1 * cos(ang).toFloat(), c.y + r1 * sin(ang).toFloat()),
                        Offset(c.x + r2 * cos(ang).toFloat(), c.y + r2 * sin(ang).toFloat()),
                        strokeWidth = if (long) 3f else 1.5f,
                    )
                }
                arc(r * 0.965f, 200f, 46f, Hud.Red, 4f)
            }

            // heavy segmented band, turning the other way
            rotate(a2, c) {
                val w = r * 0.075f
                listOf(0f to 62f, 80f to 40f, 135f to 85f, 235f to 30f, 280f to 66f).forEach { (s, sw) ->
                    arc(r * 0.76f, s, sw, Hud.Cyan.copy(alpha = 0.18f), w * 1.8f)
                    arc(r * 0.76f, s, sw, Hud.Cyan, w)
                }
            }

            // dashed inner ring + quick little arcs
            ring(r * 0.64f, Hud.CyanDim, 2f, PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            rotate(a3, c) {
                arc(r * 0.57f, 0f, 70f, Hud.Cyan, 3f)
                arc(r * 0.57f, 120f, 30f, Hud.Cyan, 3f)
                arc(r * 0.57f, 210f, 95f, Hud.Cyan, 3f)
            }

            // the glow
            drawCircle(
                Brush.radialGradient(
                    listOf(Hud.Cyan.copy(alpha = 0.55f * pulse), Hud.Cyan.copy(alpha = 0.12f), Color.Transparent),
                    center = c, radius = r * 0.55f,
                ),
                r * 0.55f, c,
            )
            drawCircle(Hud.Bg.copy(alpha = 0.85f), r * 0.42f, c)
            ring(r * 0.42f, Hud.Cyan, 3f)
            ring(r * 0.46f, Hud.Cyan.copy(alpha = 0.3f), 1.5f)
        }
        center()
    }
}

// ---------------------------------------------------------------- system readings

data class SysStats(
    val storageUsed: Float, val storageFreeGb: Float,
    val battery: Int, val charging: Boolean,
    val ramUsed: Float,
)

fun readStats(ctx: Context): SysStats {
    val fs = StatFs(Environment.getExternalStorageDirectory().path)
    val total = fs.totalBytes.toFloat()
    val free = fs.availableBytes.toFloat()
    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    return SysStats(
        storageUsed = if (total > 0) 1f - free / total else 0f,
        storageFreeGb = free / 1_073_741_824f,
        battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
        charging = bm.isCharging,
        ramUsed = 1f - mi.availMem.toFloat() / mi.totalMem.toFloat(),
    )
}

data class HomeCounts(val songs: Int, val apps: Int, val leftovers: Int, val leftoverBytes: Long, val archived: Int)

fun readCounts(ctx: Context): HomeCounts {
    val songs = try { Music.all(ctx).size } catch (e: Exception) { 0 }
    val apps = try { Apps.launchable(ctx).size } catch (e: Exception) { 0 }
    val lib = Prefs.library(ctx).takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isDirectory }
    val items = try { Scanner.scanDownloads(File(Prefs.downloads(ctx)), lib) } catch (e: Exception) { emptyList() }
    return HomeCounts(songs, apps, items.size, items.sumOf { it.sizeBytes }, Archive.list().size)
}

// ---------------------------------------------------------------- home

enum class Dest(val label: String) { Music("Music"), Apps("Apps"), Cleanup("Clean up"), Archive("Archive"), Settings("Settings") }

@Composable
fun HomeScreen(notice: String?, onOpen: (Dest) -> Unit) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var stats by remember { mutableStateOf<SysStats?>(null) }
    var counts by remember { mutableStateOf<HomeCounts?>(null) }

    LaunchedEffect(Unit) {
        counts = withContext(Dispatchers.IO) { readCounts(ctx) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            stats = withContext(Dispatchers.IO) { readStats(ctx) }
            delay(1000)
        }
    }

    val date = Date(now)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
    ) {
        // header
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("JARVIS", color = Hud.Cyan, fontSize = 22.sp, letterSpacing = 8.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(10.dp))
            val blink = rememberInfiniteTransition(label = "b")
            val on by blink.animateFloat(0.2f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "o")
            Box(Modifier.size(7.dp).alpha(on).background(Hud.Cyan, CircleShape))
            Spacer(Modifier.width(6.dp))
            HudLabel("Online", size = 9.sp, color = Hud.CyanDim)
            Spacer(Modifier.weight(1f))
            HudLabel(SimpleDateFormat("EEEE", Locale.UK).format(date), color = Hud.CyanDim)
        }

        DateStrip(now)

        // core with menu nodes around it
        CoreWithNodes(now, onOpen)

        // gauges
        val s = stats
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            RingGauge("Storage", s?.storageUsed ?: 0f, s?.let { "%.0f%%".format(it.storageUsed * 100) } ?: "--",
                warn = (s?.storageUsed ?: 0f) > 0.9f)
            RingGauge("Power", (s?.battery ?: 0) / 100f, s?.let { "${it.battery}%" } ?: "--",
                warn = (s?.battery ?: 100) < 15 && s?.charging == false)
            RingGauge("Memory", s?.ramUsed ?: 0f, s?.let { "%.0f%%".format(it.ramUsed * 100) } ?: "--")
        }

        // system log
        HudPanel(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HudLabel("System")
                val c = counts
                LogLine("Free storage", s?.let { "%.1f GB".format(it.storageFreeGb) } ?: "…")
                LogLine("Power", s?.let { if (it.charging) "charging" else "on battery" } ?: "…")
                LogLine("Songs on phone", c?.songs?.toString() ?: "scanning…")
                LogLine("Apps", c?.apps?.toString() ?: "scanning…")
                LogLine(
                    "Audiobooks in Downloads",
                    c?.let { if (it.leftovers == 0) "clear" else "${it.leftovers} · ${fmtSize(it.leftoverBytes)}" } ?: "scanning…",
                    alert = (c?.leftovers ?: 0) > 0,
                )
                LogLine("In archive", c?.archived?.toString() ?: "…")
                notice?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("› $it", color = Hud.Cyan, fontFamily = Hud.Mono, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun LogLine(name: String, value: String, alert: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(name, color = Hud.Text.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = if (alert) Hud.Red else Hud.Text, fontFamily = Hud.Mono, fontSize = 13.sp)
    }
}

/** The days of this month along the top, with today boxed. */
@Composable
private fun DateStrip(now: Long) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (today - 4).coerceAtLeast(0))
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(days) { i ->
            val d = i + 1
            val isToday = d == today
            Box(
                Modifier.size(width = 34.dp, height = 28.dp)
                    .then(if (isToday) Modifier.background(Hud.Cyan, CutCornerShape(4.dp)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "%02d".format(d),
                    color = if (isToday) Hud.Bg else if (d < today) Hud.CyanFaint else Hud.CyanDim,
                    fontFamily = Hud.Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** The core in the middle with the destinations wired to it on both sides. */
@Composable
private fun CoreWithNodes(now: Long, onOpen: (Dest) -> Unit) {
    val left = listOf(Dest.Music, Dest.Apps, Dest.Cleanup)
    val right = listOf(Dest.Archive, Dest.Settings, null) // null = "Voice", coming in stage 3
    val rows = listOf(0.16f, 0.5f, 0.84f)

    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f / 1.02f)) {
        val w = maxWidth
        val h = maxHeight
        val coreSize = w * 0.6f
        val density = LocalDensity.current

        // connector lines from each label to the core's edge
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = with(density) { coreSize.toPx() } / 2
            val labelEdge = with(density) { 84.dp.toPx() }
            rows.forEach { fy ->
                listOf(false, true).forEach { isRight ->
                    val y = size.height * fy
                    val x0 = if (isRight) size.width - labelEdge else labelEdge
                    val dx = x0 - c.x
                    val dy = y - c.y
                    val len = kotlin.math.sqrt(dx * dx + dy * dy)
                    val edge = Offset(c.x + dx / len * r * 1.02f, c.y + dy / len * r * 1.02f)
                    drawLine(Hud.CyanDim.copy(alpha = 0.7f), Offset(x0, y), edge, 1.5f)
                    drawCircle(Hud.Cyan, 4f, Offset(x0, y))
                    drawCircle(Hud.Cyan, 3f, edge)
                }
            }
        }

        Core(Modifier.size(coreSize).align(Alignment.Center)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    SimpleDateFormat("HH:mm", Locale.UK).format(Date(now)),
                    color = Hud.Text, fontFamily = Hud.Mono, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                )
                HudLabel(SimpleDateFormat("d MMM", Locale.UK).format(Date(now)), size = 10.sp, color = Hud.CyanDim)
            }
        }

        rows.forEachIndexed { i, fy ->
            NodeLabel(left[i].label, enabled = true, alignEnd = false,
                modifier = Modifier.offset(x = 4.dp, y = h * fy - 14.dp)) { onOpen(left[i]) }
            val d = right[i]
            NodeLabel(d?.label ?: "Voice · soon", enabled = d != null, alignEnd = true,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = h * fy - 14.dp)) { d?.let(onOpen) }
        }
    }
}

@Composable
private fun NodeLabel(text: String, enabled: Boolean, alignEnd: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .width(80.dp)
            .height(28.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        HudLabel(
            text,
            size = 11.sp,
            color = if (enabled) Hud.Cyan else Hud.CyanFaint,
            align = if (alignEnd) TextAlign.End else TextAlign.Start,
        )
    }
}
