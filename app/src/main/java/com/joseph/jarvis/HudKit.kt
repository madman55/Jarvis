package com.joseph.jarvis

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// The HUD building blocks used on every inner screen.

/**
 * A holographic card: corner brackets instead of a box, a glow when selected,
 * and it slides in from the side the first time it appears (staggered by [index]).
 */
@Composable
fun HudCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    index: Int = 0,
    accent: Color = Hud.Cyan,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(12) * 35).toLong())
        appear.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
    }
    val glow by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "glow")

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .graphicsLayer {
                alpha = appear.value
                translationX = (1f - appear.value) * 60f
            }
            .drawBehind {
                val c = accent.copy(alpha = 0.55f + 0.45f * glow)
                val len = 14.dp.toPx()
                val w = 2f
                // background wash
                drawRect(accent.copy(alpha = 0.035f + 0.09f * glow))
                // left edge scan line
                drawLine(accent.copy(alpha = 0.25f + 0.6f * glow), Offset(0f, len), Offset(0f, size.height - len), 1.5f)
                // four corner brackets
                drawLine(c, Offset(0f, 0f), Offset(len, 0f), w); drawLine(c, Offset(0f, 0f), Offset(0f, len), w)
                drawLine(c, Offset(size.width, 0f), Offset(size.width - len, 0f), w); drawLine(c, Offset(size.width, 0f), Offset(size.width, len), w)
                drawLine(c, Offset(0f, size.height), Offset(len, size.height), w); drawLine(c, Offset(0f, size.height), Offset(0f, size.height - len), w)
                drawLine(c, Offset(size.width, size.height), Offset(size.width - len, size.height), w)
                drawLine(c, Offset(size.width, size.height), Offset(size.width, size.height - len), w)
            }
            .then(if (onClick != null) Modifier.clickable { Fx.tap(); onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) { content() }
}

/** A small diamond that lights up when ticked, in place of a checkbox. */
@Composable
fun HudCheck(checked: Boolean, modifier: Modifier = Modifier) {
    val on by animateFloatAsState(if (checked) 1f else 0f, tween(150), label = "chk")
    Canvas(modifier.size(22.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 2f
        rotate(45f, c) {
            drawRect(Hud.CyanDim, Offset(c.x - r * 0.7f, c.y - r * 0.7f), Size(r * 1.4f, r * 1.4f), style = Stroke(2f))
            if (on > 0f) {
                val s = r * 0.9f * on
                drawRect(Hud.Cyan.copy(alpha = 0.3f), Offset(c.x - s, c.y - s), Size(s * 2, s * 2))
                drawRect(Hud.Cyan, Offset(c.x - s * 0.55f, c.y - s * 0.55f), Size(s * 1.1f, s * 1.1f))
            }
        }
    }
}

/** A thin bar showing how big something is relative to the biggest in the list. */
@Composable
fun SizeBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Hud.Cyan) {
    Canvas(modifier.fillMaxWidth().height(4.dp)) {
        drawRect(Hud.CyanFaint.copy(alpha = 0.6f))
        val w = size.width * fraction.coerceIn(0.02f, 1f)
        drawRect(color.copy(alpha = 0.3f), Offset(0f, -2f), Size(w, size.height + 4f))
        drawRect(color, Offset.Zero, Size(w, size.height))
        // segment notches
        var x = 0f
        while (x < size.width) { drawLine(Hud.Bg, Offset(x, 0f), Offset(x, size.height), 2f); x += 12.dp.toPx() }
    }
}

/** A ring that empties as the days count down. */
@Composable
fun CountdownRing(daysLeft: Int, totalDays: Int, modifier: Modifier = Modifier) {
    val frac = (daysLeft.toFloat() / totalDays).coerceIn(0f, 1f)
    val col = when {
        daysLeft <= 3 -> Hud.Red
        daysLeft <= 10 -> Color(0xFFFFB347)
        else -> Hud.Cyan
    }
    Box(modifier.size(46.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = 4.dp.toPx()
            val tl = Offset(st, st)
            val sz = Size(size.width - st * 2, size.height - st * 2)
            drawArc(Hud.CyanFaint, -90f, 360f, false, tl, sz, style = Stroke(st))
            drawArc(col, -90f, 360f * frac, false, tl, sz, style = Stroke(st, cap = StrokeCap.Butt))
            // tick marks every 5 days
            val r = size.minDimension / 2
            for (i in 0 until totalDays step 5) {
                val a = Math.toRadians(-90.0 + 360.0 * i / totalDays)
                drawLine(
                    Hud.Bg,
                    Offset(center.x + (r - st * 1.6f) * cos(a).toFloat(), center.y + (r - st * 1.6f) * sin(a).toFloat()),
                    Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat()),
                    2f,
                )
            }
        }
        Text("$daysLeft", color = col, fontFamily = Hud.Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Radar sweep with a live counter, shown while scanning. */
@Composable
fun RadarScan(title: String, detail: String, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "radar")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "sweep")
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(200.dp)) {
            val c = center
            val r = size.minDimension / 2
            for (k in 1..4) drawCircle(Hud.CyanDim.copy(alpha = 0.5f), r * k / 4, c, style = Stroke(1.5f))
            drawLine(Hud.CyanFaint, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f)
            drawLine(Hud.CyanFaint, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)
            rotate(a, c) {
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, Color.Transparent, Hud.Cyan.copy(alpha = 0.45f)), c),
                    -90f, 90f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2),
                )
                drawLine(Hud.Cyan, c, Offset(c.x + r * cos(0.0).toFloat(), c.y), 3f)
            }
            drawCircle(Hud.Cyan, 5f, c)
        }
        HudLabel(title, size = 13.sp, modifier = Modifier.padding(top = 16.dp))
        Text(detail, color = Hud.Text, fontFamily = Hud.Mono, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Cut-corner button that beeps. [danger] makes it red. */
@Composable
fun HudButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    danger: Boolean = false,
) {
    val col = if (danger) Hud.Red else Hud.Cyan
    val a = if (enabled) 1f else 0.35f
    Box(
        modifier
            .graphicsLayer { alpha = a }
            .border(1.5.dp, col, PanelShape)
            .background(if (filled) col.copy(alpha = 0.85f) else col.copy(alpha = 0.06f), PanelShape)
            .clickable(enabled = enabled) { Fx.confirm(); onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        HudLabel(text, color = if (filled) Hud.Bg else col, size = 12.sp, align = TextAlign.Center)
    }
}

/** Section heading: a label, a thin line, and an optional action on the right. */
@Composable
fun HudSection(title: String, subtitle: String = "", action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(Hud.Cyan))
            HudLabel(title, size = 11.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
            action?.let {
                HudLabel(it, size = 10.sp, color = Hud.CyanDim, modifier = Modifier.clickable { Fx.tap(); onAction() }.padding(4.dp))
            }
        }
        Box(
            Modifier.fillMaxWidth().padding(top = 6.dp).height(1.dp)
                .background(Brush.horizontalGradient(listOf(Hud.Cyan.copy(alpha = 0.7f), Color.Transparent)))
        )
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = Hud.Text.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** The bar of buttons pinned to the bottom of a screen. */
@Composable
fun HudActionBar(content: @Composable RowScope.() -> Unit) {
    Column {
        Box(
            Modifier.fillMaxWidth().height(1.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Hud.Cyan, Color.Transparent)))
        )
        Row(
            Modifier.fillMaxWidth().background(Hud.Bg.copy(alpha = 0.85f)).padding(12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Monospace data readout, e.g. "1.2 GB". */
@Composable
fun HudData(text: String, modifier: Modifier = Modifier, color: Color = Hud.Text, size: Dp = 12.dp) {
    Text(text, color = color, fontFamily = Hud.Mono, fontSize = size.value.sp, modifier = modifier)
}
