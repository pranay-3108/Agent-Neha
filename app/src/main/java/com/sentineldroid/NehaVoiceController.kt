package com.sentineldroid

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

class NehaVoiceController(
    private val context: Context,
    private val callbacks: Callbacks
) : RecognitionListener, TextToSpeech.OnInitListener {

    interface Callbacks {
        fun onListeningChanged(listening: Boolean)
        fun onTextRecognized(text: String)
        fun onSpeechStarted()
        fun onSpeechStopped()
        fun onVoiceError(message: String)
    }

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var conversationMode = false
    private var speaking = false

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    fun setConversationMode(enabled: Boolean) {
        conversationMode = enabled
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callbacks.onVoiceError("Speech recognition is not available on this device.")
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                it.setRecognitionListener(this)
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLanguage())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Agent Neha")
        }

        runCatching {
            recognizer?.startListening(intent)
            callbacks.onListeningChanged(true)
        }.onFailure {
            callbacks.onVoiceError("I couldn't start voice recognition.")
        }
    }

    fun stopListening() {
        recognizer?.stopListening()
        callbacks.onListeningChanged(false)
    }

    fun speak(text: String) {
        val engine = tts ?: return
        if (engine.voice == null) {
            engine.language = preferredLocale()
        }
        engine.setPitch(1.06f)
        engine.setSpeechRate(0.92f)
        speaking = true
        val utteranceId = "neha_${System.currentTimeMillis()}"
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
    }

    fun isSpeaking(): Boolean = speaking

    fun stopSpeaking() {
        tts?.stop()
        speaking = false
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            callbacks.onVoiceError("Neha's voice engine could not be started.")
            return
        }

        val locale = preferredLocale()
        val voices = tts?.voices.orEmpty()

        val matching = voices
            .filter { it.locale.language == locale.language }
            .sortedWith(
                compareByDescending<Voice> {
                    it.locale == locale
                }.thenByDescending {
                    looksFeminine(it.name)
                }.thenBy {
                    it.name.length
                }
            )

        val chosen = matching.firstOrNull()
        if (chosen != null) {
            tts?.voice = chosen
        } else {
            tts?.language = locale
        }
        tts?.setPitch(1.06f)
        tts?.setSpeechRate(0.92f)

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                speaking = true
                callbacks.onSpeechStarted()
            }

            override fun onDone(utteranceId: String?) {
                speaking = false
                callbacks.onSpeechStopped()
                if (conversationMode) {
                    callbacks.onListeningChanged(false)
                }
            }

            override fun onError(utteranceId: String?) {
                speaking = false
                callbacks.onSpeechStopped()
                callbacks.onVoiceError("Neha could not finish speaking that reply.")
            }
        })
    }

    override fun onReadyForSpeech(params: Bundle?) {
        callbacks.onListeningChanged(true)
    }

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        callbacks.onListeningChanged(false)
    }

    override fun onError(error: Int) {
        callbacks.onListeningChanged(false)
        val message = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Try again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything. Try again when you're ready."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice chat."
            SpeechRecognizer.ERROR_NETWORK -> "The speech service reported a network problem."
            else -> "I couldn't understand that. Try again."
        }
        callbacks.onVoiceError(message)
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val text = matches.firstOrNull().orEmpty().trim()
        callbacks.onListeningChanged(false)
        if (text.isBlank()) {
            callbacks.onVoiceError("I didn't catch that. Try again.")
            return
        }
        callbacks.onTextRecognized(text)
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun preferredLocale(): Locale {
        val device = Locale.getDefault()
        return if (device.language == "hi") Locale("hi", "IN") else Locale("en", "IN")
    }

    private fun preferredLanguage(): String = preferredLocale().toLanguageTag()

    private fun looksFeminine(name: String): Boolean {
        val value = name.lowercase(Locale.ROOT)
        return value.contains("female") || value.contains("woman") ||
            value.contains("girl") || value.contains("f1") || value.contains("f2")
    }
}
