package com.joseph.jarvis

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class VoiceState { Idle, Listening, Thinking, Speaking }

/** Everything the voice conversation needs, kept alive across screens. */
class VoiceSession(
    private val ctx: Context,
    private val io: VoiceIO,
    private val scope: CoroutineScope,
    private val onOpen: (Dest) -> Unit,
) {
    var state by mutableStateOf(VoiceState.Idle)
    var level by mutableFloatStateOf(0f)
    var heard by mutableStateOf("")
    var reply by mutableStateOf("")

    var askPermission: () -> Unit = {}

    /** Tap the core: start listening, or stop if already listening/speaking. */
    fun toggle() {
        when (state) {
            VoiceState.Listening -> { io.cancelListening(); state = VoiceState.Idle; level = 0f }
            VoiceState.Speaking -> { io.stopSpeaking(); state = VoiceState.Idle }
            VoiceState.Thinking -> {}
            VoiceState.Idle -> {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    askPermission()
                } else start()
            }
        }
    }

    fun start() {
        if (!io.available()) {
            say("Speech recognition isn't available on this phone, sir.")
            return
        }
        heard = ""
        reply = ""
        state = VoiceState.Listening
        Fx.listen()
        io.listen(
            onLevel = { level = it },
            onPartial = { heard = it },
            onResult = { text ->
                heard = text
                state = VoiceState.Thinking
                scope.launch {
                    val r = withContext(Dispatchers.IO) { Brain.respond(ctx, text) }
                    Fx.done()
                    r.go?.let(onOpen)
                    say(r.say)
                }
            },
            onFail = { msg -> Fx.error(); say(msg) },
        )
    }

    fun say(text: String) {
        reply = text
        state = VoiceState.Speaking
        io.speak(text) { scope.launch { if (state == VoiceState.Speaking) state = VoiceState.Idle } }
    }
}

@Composable
fun rememberVoiceSession(onOpen: (Dest) -> Unit): VoiceSession {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val io = remember { VoiceIO(ctx) }
    DisposableEffect(Unit) { onDispose { io.shutdown() } }
    val session = remember { VoiceSession(ctx, io, scope, onOpen) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) session.start() else session.say("I'll need the microphone for that, sir.")
    }
    session.askPermission = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    return session
}

/** What you said and what Jarvis said back, with the reply typing itself out. */
@Composable
fun VoicePanel(v: VoiceSession, modifier: Modifier = Modifier) {
    if (v.heard.isBlank() && v.reply.isBlank() && v.state == VoiceState.Idle) return
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(v.reply) {
        shown = 0
        while (shown < v.reply.length) { shown++; delay(18) }
    }
    HudPanel(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HudLabel(
                when (v.state) {
                    VoiceState.Listening -> "Listening"
                    VoiceState.Thinking -> "Processing"
                    VoiceState.Speaking -> "Responding"
                    VoiceState.Idle -> "Voice link"
                },
                size = 10.sp,
            )
            if (v.heard.isNotBlank()) {
                Text("› ${v.heard}", color = Hud.Text.copy(alpha = 0.75f), fontFamily = Hud.Mono, fontSize = 13.sp)
            }
            if (v.reply.isNotBlank()) {
                Text(v.reply.take(shown), color = Hud.Cyan, fontSize = 15.sp)
            }
        }
    }
}
