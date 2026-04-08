package com.threestrip.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.threestrip.core.storage.ConsoleMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.UUID

data class TtsAvailability(
    val ready: Boolean = false,
    val message: String? = null,
)

class ConsoleTtsController(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context, this)
    private val _mode = MutableStateFlow(ConsoleMode.IDLE)
    val mode: StateFlow<ConsoleMode> = _mode
    private val _availability = MutableStateFlow(TtsAvailability())
    val availability: StateFlow<TtsAvailability> = _availability
    private var ready = false

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _mode.value = ConsoleMode.SPEAKING
            }

            override fun onDone(utteranceId: String?) {
                _mode.value = ConsoleMode.IDLE
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _mode.value = ConsoleMode.ERROR
                _availability.value = TtsAvailability(
                    ready = false,
                    message = "Speech output failed. Check Android TTS settings."
                )
            }
        })
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) {
            _availability.value = TtsAvailability(
                ready = false,
                message = "Speech output is unavailable. Configure Android TTS."
            )
            return
        }

        val languageResult = tts.setLanguage(Locale.US)
        ready = languageResult != TextToSpeech.LANG_MISSING_DATA && languageResult != TextToSpeech.LANG_NOT_SUPPORTED
        _availability.value = if (ready) {
            TtsAvailability(ready = true)
        } else {
            TtsAvailability(
                ready = false,
                message = "Speech output language is unavailable. Install a TTS voice."
            )
        }
    }

    fun speak(text: String): Boolean {
        if (!ready || text.isBlank()) return false
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        return if (result == TextToSpeech.SUCCESS) {
            true
        } else {
            _availability.value = TtsAvailability(
                ready = false,
                message = "Speech output failed. Check Android TTS settings."
            )
            false
        }
    }

    fun stop() {
        tts.stop()
        _mode.value = ConsoleMode.IDLE
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
