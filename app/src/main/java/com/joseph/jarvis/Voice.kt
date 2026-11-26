package com.joseph.jarvis

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Ears and mouth. Listening uses the phone's own speech recognition;
 * speaking uses Android text-to-speech with a British voice.
 * Must be created and used on the main thread.
 */
class VoiceIO(private val ctx: Context) {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts ?: return@TextToSpeech
                t.language = Locale.UK
                // Prefer a male British voice if the phone has one installed.
                val uk = t.voices?.filter { it.locale == Locale.UK && !it.isNetworkConnectionRequired }.orEmpty()
                val male = uk.firstOrNull { v -> listOf("gbb", "gbd", "rjs").any { v.name.contains(it) } }
                (male ?: uk.firstOrNull())?.let { t.voice = it }
                t.setPitch(0.92f)
                t.setSpeechRate(1.02f)
                ttsReady = true
            }
        }
    }

    fun available() = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun speak(text: String, after: () -> Unit = {}) {
        val t = tts
        if (t == null || !ttsReady) { after(); return }
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { after() }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { after() }
        })
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    fun stopSpeaking() { tts?.stop() }

    /**
     * Listens for one phrase. [onLevel] gets the microphone loudness (0 to 1) so the core can pulse,
     * [onPartial] gets words as they're recognised, then [onResult] or [onFail].
     */
    fun listen(
        onLevel: (Float) -> Unit,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onFail: (String) -> Unit,
    ) {
        stopSpeaking()
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { onLevel(0f) }
            override fun onError(error: Int) {
                onLevel(0f)
                onFail(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that, sir."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "I need microphone permission, sir."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a connection, sir."
                        else -> "Something went wrong listening, sir."
                    }
                )
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) onFail("I didn't catch that, sir.") else onResult(text)
            }
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        r.startListening(intent)
    }

    fun cancelListening() { recognizer?.cancel() }

    fun shutdown() {
        recognizer?.destroy(); recognizer = null
        tts?.shutdown(); tts = null
    }
}
