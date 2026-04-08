package com.threestrip.core.chat

import android.util.Log
import com.threestrip.core.llm.GenerationEvent
import com.threestrip.core.llm.LocalLlmEngine
import com.threestrip.core.storage.ChatMessage
import com.threestrip.core.storage.ConsoleMode
import com.threestrip.core.storage.KITT_RUNTIME_PROMPT
import com.threestrip.core.storage.KITT_SYSTEM_PROMPT
import com.threestrip.core.storage.ModelLoadState
import com.threestrip.core.storage.TranscriptStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

data class ConsoleUiState(
    val mode: ConsoleMode = ConsoleMode.BOOT,
    val transcriptVisible: Boolean = false,
    val settingsVisible: Boolean = false,
    val transientReply: String = "",
    val error: String? = null,
    val modelState: ModelLoadState = ModelLoadState.Empty,
)

class PromptAssembler(
    private val maxMessages: Int = 4,
    private val maxCorpusChars: Int = 1_200,
) {
    private companion object {
        const val APP_FACTS =
            "App facts: ThreeStrip is a local, voice-first Android chat app with a retro three-bar console UI. " +
                "It records speech on-device, runs a local language model on-device, speaks replies with Android text to speech, " +
                "stores transcript history locally on the phone, supports importing a local model file, and does not use a cloud backend."
    }

    fun trim(
        messages: List<ChatMessage>,
        userInput: String,
        systemPrompt: String = "",
        corpusText: String = "",
    ): String {
        return buildString {
            val cleanSystemPrompt = when {
                systemPrompt.isBlank() -> KITT_RUNTIME_PROMPT
                systemPrompt == KITT_SYSTEM_PROMPT -> KITT_RUNTIME_PROMPT
                else -> systemPrompt.trim().take(1_600)
            }
            val cleanCorpus = corpusText.trim().take(maxCorpusChars)
            append("system: ")
            append(cleanSystemPrompt)
            append("\n")
            if (cleanSystemPrompt.length < 1_200) {
                append("facts: ")
                append(APP_FACTS)
                append("\n")
            }
            if (cleanCorpus.isNotEmpty()) {
                append("reference: ")
                append(cleanCorpus)
                append("\n")
            }
            val history = messages.takeLast(maxMessages).dropLastWhile { it.role == "user" && it.text.trim() == userInput }
            history.forEach { append("${it.role}: ${it.text}\n") }
            append("user: $userInput\nassistant:")
        }
    }
}

class ChatOrchestrator(
    private val llmEngine: LocalLlmEngine,
    private val transcriptRepository: TranscriptStore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate),
    private val promptAssembler: PromptAssembler = PromptAssembler(),
) {
    private companion object {
        const val TAG = "ThreeStripChat"
    }

    private val _uiState = MutableStateFlow(ConsoleUiState())
    val uiState: StateFlow<ConsoleUiState> = _uiState.asStateFlow()
    private var activeJob: Job? = null

    fun onBootFinished() = _uiState.update { it.copy(mode = ConsoleMode.IDLE) }
    fun openTranscript() = _uiState.update { it.copy(transcriptVisible = true) }
    fun closeTranscript() = _uiState.update { it.copy(transcriptVisible = false) }
    fun openSettings() = _uiState.update { it.copy(settingsVisible = true) }
    fun closeSettings() = _uiState.update { it.copy(settingsVisible = false) }
    fun updateModelState(state: ModelLoadState) = _uiState.update { it.copy(modelState = state) }
    fun onVoiceListening() = _uiState.update { it.copy(mode = ConsoleMode.LISTENING, error = null) }
    fun onVoiceInputError(message: String) = pushError(message)
    fun onLocalFailure(message: String) = pushError(message)
    fun onVoiceInputCleared() = _uiState.update { state ->
        if (state.mode == ConsoleMode.LISTENING || state.mode == ConsoleMode.ERROR) {
            state.copy(mode = ConsoleMode.IDLE)
        } else {
            state
        }
    }

    fun stopAll() {
        Log.d(TAG, "stopAll: cancelling active UI work without unloading model")
        activeJob?.cancel()
        _uiState.update { it.copy(mode = ConsoleMode.IDLE) }
    }

    fun submit(
        messages: List<ChatMessage>,
        input: String,
        systemPrompt: String = "",
        corpusText: String = "",
    ) {
        val draft = input.trim()
        if (draft.isBlank()) return
        Log.d(TAG, "submit: user input length=${draft.length}")
        activeJob?.cancel()
        activeJob = scope.launch {
            val user = ChatMessage(UUID.randomUUID().toString(), "user", draft, System.currentTimeMillis())
            transcriptRepository.append(user)
            Log.d(TAG, "submit: user message persisted")
            _uiState.update { it.copy(mode = ConsoleMode.THINKING, transientReply = "", error = null) }
            val prompt = promptAssembler.trim(
                messages = messages + user,
                userInput = draft,
                systemPrompt = systemPrompt,
                corpusText = corpusText,
            )
            Log.d(TAG, "submit: prompt length=${prompt.length}")
            llmEngine.generate(prompt).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> {
                        Log.d(TAG, "generate: token chunk length=${event.text.length}")
                        _uiState.update { state ->
                            state.copy(mode = ConsoleMode.THINKING, transientReply = state.transientReply + event.text)
                        }
                    }

                    is GenerationEvent.Completed -> {
                        val reply = event.text.ifBlank { uiState.value.transientReply }
                        transcriptRepository.append(ChatMessage(UUID.randomUUID().toString(), "assistant", reply, System.currentTimeMillis()))
                        Log.d(TAG, "generate: completed reply length=${reply.length}")
                        _uiState.update { it.copy(mode = ConsoleMode.IDLE, transientReply = reply) }
                    }

                    is GenerationEvent.Error -> {
                        Log.e(TAG, "generate: error=${event.message}")
                        pushError(event.message)
                    }
                }
            }
        }
    }

    private fun pushError(message: String) {
        Log.e(TAG, "pushError: $message")
        _uiState.update { it.copy(mode = ConsoleMode.ERROR, error = message) }
        scope.launch {
            delay(1400)
            _uiState.update { state ->
                if (state.mode == ConsoleMode.ERROR && state.error == message) {
                    state.copy(mode = ConsoleMode.IDLE)
                } else {
                    state
                }
            }
        }
    }
}
