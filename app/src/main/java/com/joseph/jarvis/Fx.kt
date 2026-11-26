package com.joseph.jarvis

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Little UI sounds and vibrations. The sounds are generated in code (short sine-wave
 * chirps), so there are no audio files to ship. Switch them off in Settings.
 */
object Fx {
    enum class Sound { Tap, Confirm, Boot, Listen, Done, Error }

    private const val RATE = 44_100
    private val tracks = mutableMapOf<Sound, AudioTrack>()
    private var vibrator: Vibrator? = null
    private var appCtx: Context? = null

    fun init(ctx: Context) {
        if (appCtx != null) return
        appCtx = ctx.applicationContext
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun enabled() = appCtx?.let { Prefs.effects(it) } ?: false

    fun tap() = play(Sound.Tap, VibrationEffect.EFFECT_TICK)
    fun confirm() = play(Sound.Confirm, VibrationEffect.EFFECT_CLICK)
    fun boot() = play(Sound.Boot, null)
    fun listen() = play(Sound.Listen, VibrationEffect.EFFECT_CLICK)
    fun done() = play(Sound.Done, VibrationEffect.EFFECT_TICK)
    fun error() = play(Sound.Error, VibrationEffect.EFFECT_DOUBLE_CLICK)

    private fun play(s: Sound, haptic: Int?) {
        if (!enabled()) return
        try {
            haptic?.let { vibrator?.vibrate(VibrationEffect.createPredefined(it)) }
            val t = tracks.getOrPut(s) { build(s) }
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.play()
        } catch (e: Exception) {
            // Sound is a nicety; never let it crash anything.
        }
    }

    /** Each sound is a list of (start Hz, end Hz, milliseconds) chirps played back to back. */
    private fun recipe(s: Sound): List<Triple<Float, Float, Int>> = when (s) {
        Sound.Tap -> listOf(Triple(2200f, 2600f, 35))
        Sound.Confirm -> listOf(Triple(1400f, 1400f, 50), Triple(2100f, 2100f, 70))
        Sound.Boot -> listOf(Triple(300f, 1800f, 450), Triple(1800f, 1800f, 60), Triple(2400f, 2400f, 120))
        Sound.Listen -> listOf(Triple(900f, 1300f, 70), Triple(1700f, 2200f, 90))
        Sound.Done -> listOf(Triple(2200f, 1500f, 90))
        Sound.Error -> listOf(Triple(420f, 380f, 120), Triple(0f, 0f, 40), Triple(380f, 340f, 140))
    }

    private fun build(s: Sound): AudioTrack {
        val parts = recipe(s)
        val total = parts.sumOf { it.third } * RATE / 1000
        val pcm = ShortArray(total)
        var i = 0
        var phase = 0.0
        for ((f0, f1, ms) in parts) {
            val n = ms * RATE / 1000
            for (k in 0 until n) {
                val p = k.toDouble() / n
                val f = f0 + (f1 - f0) * p
                phase += 2 * PI * f / RATE
                // quick fade in and out so there are no clicks
                val env = min(1.0, min(k / 200.0, (n - k) / 400.0))
                pcm[i++] = if (f0 == 0f && f1 == 0f) 0.toShort() else (sin(phase) * env * 0.22 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }
}
