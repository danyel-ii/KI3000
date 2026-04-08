package com.threestrip.core.audio

import android.content.Context
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.EngineInfo
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

data class TtsEngineOption(
    val packageName: String,
    val label: String,
)

class ConsoleTtsController(private val context: Context) : TextToSpeech.OnInitListener {
    private companion object {
        const val TAG = "ThreeStripTts"
    }

    private var tts: TextToSpeech = TextToSpeech(context, this)
    private val _mode = MutableStateFlow(ConsoleMode.IDLE)
    val mode: StateFlow<ConsoleMode> = _mode
    private val _availability = MutableStateFlow(TtsAvailability())
    val availability: StateFlow<TtsAvailability> = _availability
    private val _engineOptions = MutableStateFlow<List<TtsEngineOption>>(emptyList())
    val engineOptions: StateFlow<List<TtsEngineOption>> = _engineOptions
    private val _voiceOptions = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val voiceOptions: StateFlow<List<TtsVoiceOption>> = _voiceOptions
    private val _completedUtterances = MutableStateFlow(0L)
    val completedUtterances: StateFlow<Long> = _completedUtterances
    private var ready = false

    init {
        attachUtteranceListener(tts)
    }

    private fun attachUtteranceListener(target: TextToSpeech) {
        target.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
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
            _engineOptions.value = listEnginesInternal()
            _availability.value = TtsAvailability(
                ready = false,
                message = "Speech output is unavailable. Configure Android TTS."
            )
            return
        }

        val preferredLocale = resolvePreferredLocale()
        val languageResult = tts.setLanguage(preferredLocale)
        Log.d(TAG, "onInit: languageResult=$languageResult locale=${preferredLocale.toLanguageTag()}")
        ready = languageResult != TextToSpeech.LANG_MISSING_DATA && languageResult != TextToSpeech.LANG_NOT_SUPPORTED
        _engineOptions.value = listEnginesInternal()
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

    fun listEngines(): List<TtsEngineOption> = _engineOptions.value

    fun currentEnginePackage(): String? = tts.defaultEngine

    fun listVoices(): List<TtsVoiceOption> = _voiceOptions.value

    fun currentVoiceName(): String? = tts.voice?.name

    fun setEngine(packageName: String?): Boolean {
        ready = false
        _availability.value = TtsAvailability(
            ready = false,
            message = "Speech engine is loading."
        )
        val next = if (packageName.isNullOrBlank()) {
            TextToSpeech(context, this)
        } else {
            TextToSpeech(context, this, packageName)
        }
        attachUtteranceListener(next)
        tts.stop()
        tts.shutdown()
        tts = next
        return true
    }

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

    private fun listEnginesInternal(): List<TtsEngineOption> {
        return tts.engines.orEmpty()
            .sortedWith(compareBy<EngineInfo> { it.label ?: it.name }.thenBy { it.name })
            .map { engine ->
                TtsEngineOption(
                    packageName = engine.name,
                    label = engine.label ?: engine.name.substringAfterLast('.')
                )
            }
    }

    private fun listVoicesInternal(): List<TtsVoiceOption> {
        val preferredLocale = resolvePreferredLocale()
        return tts.voices.orEmpty()
            .sortedWith(
                compareByDescending<Voice> { voiceMatchesLocale(it = it, target = preferredLocale) }
                    .thenByDescending { voiceMatchesLanguage(it = it, target = preferredLocale) }
                    .thenBy { it.locale?.toLanguageTag().orEmpty() }
                    .thenBy { it.name }
            )
            .map { voice ->
                val locale = voice.locale?.toLanguageTag().orEmpty()
                TtsVoiceOption(
                    name = voice.name,
                    label = listOf(locale, voice.name.substringAfterLast('/').substringAfterLast('-')).filter { it.isNotBlank() }.joinToString(" ")
                )
            }
            .distinctBy { it.name }
    }

    private fun resolvePreferredLocale(): Locale {
        val configured = runCatching {
            Settings.Secure.getString(context.contentResolver, "tts_default_locale")
        }.getOrNull().orEmpty()
        val currentEngine = currentEnginePackage()
        val engineLocale = configured
            .split(',')
            .map { it.trim() }
            .firstOrNull { entry ->
                currentEngine != null && entry.startsWith("$currentEngine:")
            }
            ?.substringAfter(':')
            ?.takeIf { it.isNotBlank() }
            ?.replace('_', '-')
            ?.let(Locale::forLanguageTag)
        return engineLocale ?: Locale.getDefault()
    }

    private fun voiceMatchesLocale(it: Voice, target: Locale): Boolean {
        val locale = it.locale ?: return false
        return locale.language == target.language && locale.country == target.country
    }

    private fun voiceMatchesLanguage(it: Voice, target: Locale): Boolean {
        val locale = it.locale ?: return false
        return locale.language == target.language
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
