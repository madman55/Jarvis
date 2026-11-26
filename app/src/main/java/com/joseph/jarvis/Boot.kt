package com.joseph.jarvis

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * The start-up sequence: rings draw themselves in while system checks type out,
 * then a greeting. About three seconds; tap anywhere to skip.
 */
@Composable
fun BootSequence(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val rings = remember { List(4) { Animatable(0f) } }
    val lines = remember { mutableStateListOf<String>() }
    var typed by remember { mutableIntStateOf(0) }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good morning, sir."
        in 12..17 -> "Good afternoon, sir."
        else -> "Good evening, sir."
    }
    val script = listOf(
        "INITIALISING CORE",
        "STORAGE LINK ........ OK",
        "AUDIO SUBSYSTEM ..... OK",
        "APP REGISTRY ........ OK",
        if (Prefs.apiKey(ctx).isNotBlank()) "VOICE INTERFACE ..... ONLINE" else "VOICE INTERFACE ..... LOCAL",
        greeting,
    )

    LaunchedEffect(Unit) {
        Fx.boot()
        rings.forEachIndexed { i, r ->
            launch {
                delay(i * 140L)
                r.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            }
        }
        for (line in script) {
            lines += ""
            for (k in 1..line.length) {
                lines[lines.lastIndex] = line.take(k)
                typed++
                delay(14)
            }
            delay(110)
        }
        Fx.done()
        delay(600)
        onDone()
    }

    Box(
        Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onDone,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(220.dp)) {
                val c = center
                val r = size.minDimension / 2
                val radii = listOf(0.98f, 0.8f, 0.62f, 0.44f)
                val starts = listOf(-90f, 30f, 150f, 260f)
                rings.forEachIndexed { i, a ->
                    val rad = r * radii[i]
                    val st = if (i == 1) 10f else 3f
                    drawArc(
                        Hud.Cyan.copy(alpha = if (i == 1) 1f else 0.8f),
                        starts[i], 360f * a.value, false,
                        Offset(c.x - rad, c.y - rad), Size(rad * 2, rad * 2),
                        style = Stroke(st),
                    )
                }
                val glow = rings.last().value
                drawCircle(Hud.Cyan.copy(alpha = 0.25f * glow), r * 0.36f, c)
                drawCircle(Hud.Cyan.copy(alpha = glow), r * 0.18f, c)
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 28.dp).height(170.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                lines.forEachIndexed { i, l ->
                    val last = i == script.lastIndex
                    Text(
                        l + if (i == lines.lastIndex && typed % 2 == 0) "▌" else "",
                        color = if (last) Hud.Cyan else Hud.Text.copy(alpha = 0.75f),
                        fontFamily = Hud.Mono,
                        fontSize = if (last) 17.sp else 12.sp,
                        fontWeight = if (last) FontWeight.Bold else FontWeight.Normal,
                        modifier = if (last) Modifier.padding(top = 10.dp) else Modifier,
                    )
                }
            }
        }
    }
}
