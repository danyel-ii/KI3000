package com.threestrip.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.threestrip.core.storage.ConsoleMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.UUID

data class TtsAvailability(
    val ready: Boolean = false,
    val message: String? = null,
)

data class TtsVoiceOption(
    val name: String,
    val label: String,
)

class ConsoleTtsController(context: Context) : TextToSpeech.OnInitListener {
    private companion object {
        const val TAG = "ThreeStripTts"
    }

    private val tts = TextToSpeech(context, this)
    private val _mode = MutableStateFlow(ConsoleMode.IDLE)
    val mode: StateFlow<ConsoleMode> = _mode
    private val _availability = MutableStateFlow(TtsAvailability())
    val availability: StateFlow<TtsAvailability> = _availability
    private val _voiceOptions = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val voiceOptions: StateFlow<List<TtsVoiceOption>> = _voiceOptions
    private val _completedUtterances = MutableStateFlow(0L)
    val completedUtterances: StateFlow<Long> = _completedUtterances
    private var ready = false

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "onStart: utterance=$utteranceId")
                _mode.value = ConsoleMode.SPEAKING
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "onDone: utterance=$utteranceId")
                _mode.value = ConsoleMode.IDLE
                _completedUtterances.value = _completedUtterances.value + 1L
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "onError: utterance=$utteranceId")
                _mode.value = ConsoleMode.ERROR
                _availability.value = TtsAvailability(
                    ready = false,
                    message = "Speech output failed. Check Android TTS settings."
                )
            }
        })
    }

    override fun onInit(status: Int) {
        Log.d(TAG, "onInit: status=$status")
        ready = status == TextToSpeech.SUCCESS
        if (!ready) {
            _availability.value = TtsAvailability(
                ready = false,
                message = "Speech output is unavailable. Configure Android TTS."
            )
            return
        }

        val languageResult = tts.setLanguage(Locale.US)
        Log.d(TAG, "onInit: languageResult=$languageResult")
        ready = languageResult != TextToSpeech.LANG_MISSING_DATA && languageResult != TextToSpeech.LANG_NOT_SUPPORTED
        _voiceOptions.value = listVoicesInternal()
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
        Log.d(TAG, "speak: ready=$ready length=${text.length}")
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

    fun listVoices(): List<TtsVoiceOption> = _voiceOptions.value

    fun currentVoiceName(): String? = tts.voice?.name

    fun setVoice(name: String?): Boolean {
        if (!ready) return false
        val targetVoice = if (name.isNullOrBlank()) {
            tts.defaultVoice
        } else {
            tts.voices.orEmpty().firstOrNull { it.name == name }
        } ?: return false
        val result = runCatching { tts.voice = targetVoice }.getOrDefault(TextToSpeech.ERROR)
        _voiceOptions.value = listVoicesInternal()
        return result == TextToSpeech.SUCCESS
    }

    private fun listVoicesInternal(): List<TtsVoiceOption> {
        return tts.voices.orEmpty()
            .filter { it.locale?.language == Locale.US.language || it.locale?.language == Locale.getDefault().language }
            .sortedWith(compareBy<Voice> { it.locale?.toLanguageTag().orEmpty() }.thenBy { it.name })
            .map { voice ->
                val locale = voice.locale?.toLanguageTag().orEmpty()
                TtsVoiceOption(
                    name = voice.name,
                    label = listOf(locale, voice.name.substringAfterLast('/').substringAfterLast('-')).filter { it.isNotBlank() }.joinToString(" ")
                )
            }
            .distinctBy { it.name }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
