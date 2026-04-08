package com.threestrip.core.chat

import com.threestrip.core.llm.GenerationEvent
import com.threestrip.core.llm.LocalLlmEngine
import com.threestrip.core.storage.ChatMessage
import com.threestrip.core.storage.ConsoleMode
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
    private val maxMessages: Int = 12,
    private val maxCorpusChars: Int = 3_000,
) {
    fun trim(
        messages: List<ChatMessage>,
        userInput: String,
        systemPrompt: String = "",
        corpusText: String = "",
    ): String {
        return buildString {
            val cleanSystemPrompt = systemPrompt.trim()
            val cleanCorpus = corpusText.trim().take(maxCorpusChars)
            if (cleanSystemPrompt.isNotEmpty()) {
                append("system: ")
                append(cleanSystemPrompt)
                append("\n")
            }
            if (cleanCorpus.isNotEmpty()) {
                append("reference: ")
                append(cleanCorpus)
                append("\n")
            }
            messages.takeLast(maxMessages).forEach { append("${it.role}: ${it.text}\n") }
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
        activeJob?.cancel()
        scope.launch { llmEngine.stop() }
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
        activeJob?.cancel()
        activeJob = scope.launch {
            val user = ChatMessage(UUID.randomUUID().toString(), "user", draft, System.currentTimeMillis())
            transcriptRepository.append(user)
            _uiState.update { it.copy(mode = ConsoleMode.THINKING, transientReply = "", error = null) }
            val prompt = promptAssembler.trim(
                messages = messages + user,
                userInput = draft,
                systemPrompt = systemPrompt,
                corpusText = corpusText,
            )
            llmEngine.generate(prompt).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> _uiState.update { state ->
                        state.copy(mode = ConsoleMode.THINKING, transientReply = state.transientReply + event.text)
                    }

                    is GenerationEvent.Completed -> {
                        val reply = event.text.ifBlank { uiState.value.transientReply }
                        transcriptRepository.append(ChatMessage(UUID.randomUUID().toString(), "assistant", reply, System.currentTimeMillis()))
                        _uiState.update { it.copy(mode = ConsoleMode.IDLE, transientReply = reply) }
                    }

                    is GenerationEvent.Error -> pushError(event.message)
                }
            }
        }
    }

    private fun pushError(message: String) {
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
