package com.threestrip.core.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.threestrip.core.storage.ConsoleMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class OfflineSpeechInputController(private val context: Context) {
    private companion object {
        const val TAG = "ThreeStripSpeech"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _mode = MutableStateFlow(ConsoleMode.IDLE)
    val mode: StateFlow<ConsoleMode> = _mode

    private var recognizer: SpeechRecognizer? = null
    private var onRecognized: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var listening = false

    fun startListening(
        onRecognized: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "startListening: recognition unavailable")
            pushError()
            onError("Offline speech recognition is unavailable on this device.")
            return
        }

        this.onRecognized = onRecognized
        this.onError = onError
        listening = true
        _mode.value = ConsoleMode.LISTENING
        Log.d(TAG, "startListening: started")

        val speechRecognizer = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { created ->
            created.setRecognitionListener(listener)
            recognizer = created
        }

        speechRecognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        )
    }

    fun stop() {
        if (!listening) {
            _mode.value = ConsoleMode.IDLE
            return
        }
        Log.d(TAG, "stop: requested")
        listening = false
        recognizer?.stopListening()
    }

    fun cancel() {
        Log.d(TAG, "cancel: requested")
        listening = false
        recognizer?.cancel()
        _mode.value = ConsoleMode.IDLE
    }

    fun shutdown() {
        listening = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
            _mode.value = ConsoleMode.LISTENING
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
            _mode.value = ConsoleMode.LISTENING
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
        }
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onResults(results: Bundle?) {
            listening = false
            _mode.value = ConsoleMode.IDLE
            val match = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()

            if (match.isBlank()) {
                Log.e(TAG, "onResults: empty")
                onError?.invoke("No speech was recognized.")
                pushError()
            } else {
                Log.d(TAG, "onResults: \"$match\"")
                onRecognized?.invoke(match)
            }
        }

        override fun onError(error: Int) {
            listening = false
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio capture failed."
                SpeechRecognizer.ERROR_CLIENT -> "Voice capture was cancelled."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Offline language pack is unavailable."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> "Network recognition is disabled. Install offline speech on the device."
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was detected."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
                else -> "Voice recognition failed."
            }
            Log.e(TAG, "onError: code=$error message=$message")
            onError?.invoke(message)
            if (error == SpeechRecognizer.ERROR_CLIENT) {
                _mode.value = ConsoleMode.IDLE
            } else {
                pushError()
            }
        }
    }

    private fun pushError() {
        scope.launch {
            _mode.value = ConsoleMode.ERROR
            delay(900)
            if (_mode.value == ConsoleMode.ERROR) {
                _mode.value = ConsoleMode.IDLE
            }
        }
    }
}
