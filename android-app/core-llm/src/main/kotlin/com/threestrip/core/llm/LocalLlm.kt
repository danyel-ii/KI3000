package com.threestrip.core.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.threestrip.core.storage.ModelLoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

sealed interface GenerationEvent {
    data class Token(val text: String) : GenerationEvent
    data class Completed(val text: String) : GenerationEvent
    data class Error(val message: String) : GenerationEvent
}

interface LocalLlmEngine {
    suspend fun loadModel(path: String): ModelLoadState
    fun generate(prompt: String): Flow<GenerationEvent>
    suspend fun stop()
    val state: ModelLoadState
}

class LiteRtLocalLlmEngine(private val context: Context) : LocalLlmEngine {
    private var llmInference: LlmInference? = null
    @Volatile
    override var state: ModelLoadState = ModelLoadState.Empty
        private set

    override suspend fun loadModel(path: String): ModelLoadState = withContext(Dispatchers.IO) {
        state = ModelLoadState.Loading(path)
        runCatching {
            llmInference?.close()
            val options = LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(256)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            state = ModelLoadState.Ready(path)
            state
        }.getOrElse { error ->
            state = ModelLoadState.Error(error.message ?: "Model load failed")
            state
        }
    }

    override fun generate(prompt: String): Flow<GenerationEvent> = channelFlow {
        val engine = llmInference
        if (engine == null) {
            send(GenerationEvent.Error("Import a local model to begin."))
            return@channelFlow
        }
        try {
            val response = withContext(Dispatchers.IO) {
                engine.generateResponseAsync(prompt).await()
            }
            response.chunked(24).forEach { chunk ->
                send(GenerationEvent.Token(chunk))
            }
            send(GenerationEvent.Completed(response))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            send(GenerationEvent.Error(error.message ?: "Generation failed"))
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.IO) { llmInference?.close(); llmInference = null }
        state = when (val current = state) {
            is ModelLoadState.Ready -> ModelLoadState.Imported(current.path)
            is ModelLoadState.Loading -> ModelLoadState.Imported(current.path)
            else -> current
        }
    }
}

class FakeLocalLlmEngine : LocalLlmEngine {
    override var state: ModelLoadState = ModelLoadState.Empty
        private set

    override suspend fun loadModel(path: String): ModelLoadState {
        state = ModelLoadState.Ready(path)
        return state
    }

    override fun generate(prompt: String): Flow<GenerationEvent> = flow {
        val reply = "Local standby response: ${prompt.take(80)}"
        emit(GenerationEvent.Token(reply))
        emit(GenerationEvent.Completed(reply))
    }

    override suspend fun stop() = Unit
}
