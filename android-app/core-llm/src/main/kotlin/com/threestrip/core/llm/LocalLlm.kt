package com.threestrip.core.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.threestrip.core.storage.ModelLoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import java.io.File

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
    private companion object {
        const val TAG = "ThreeStripLlm"
        const val GENERATION_TIMEOUT_MS = 90_000L
    }

    private var llmInference: LlmInference? = null
    private val engineMutex = Mutex()
    private var loadedPath: String? = null
    @Volatile
    override var state: ModelLoadState = ModelLoadState.Empty
        private set

    override suspend fun loadModel(path: String): ModelLoadState = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            val modelFile = File(path)
            if (!modelFile.exists() || !modelFile.isFile) {
                Log.e(TAG, "loadModel: missing file at $path")
                state = ModelLoadState.Error("Model file is unavailable.")
                return@withLock state
            }
            if (loadedPath == path && llmInference != null && state is ModelLoadState.Ready) {
                Log.d(TAG, "loadModel: already ready for $path")
                return@withLock state
            }
            state = ModelLoadState.Loading(path)
            runCatching {
                Log.d(TAG, "loadModel: creating inference for $path")
                llmInference?.close()
                llmInference = null
                val options = LlmInferenceOptions.builder()
                    .setModelPath(path)
                    .setMaxTokens(256)
                    .setPreferredBackend(LlmInference.Backend.CPU)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
                loadedPath = path
                state = ModelLoadState.Ready(path)
                Log.d(TAG, "loadModel: ready for $path")
                state
            }.getOrElse { error ->
                loadedPath = null
                Log.e(TAG, "loadModel: failed for $path", error)
                state = ModelLoadState.Error(error.message ?: "Model load failed")
                state
            }
        }
    }

    override fun generate(prompt: String): Flow<GenerationEvent> = channelFlow {
        val engine = llmInference
        if (engine == null) {
            Log.e(TAG, "generate: engine unavailable")
            send(GenerationEvent.Error("Import a local model to begin."))
            return@channelFlow
        }
        try {
            Log.d(TAG, "generate: starting prompt length=${prompt.length}")
            val response = generateResponse(engine, prompt)
            Log.d(TAG, "generate: completed response length=${response.length}")
            if (response.isBlank()) {
                val fallbackPrompt = buildFallbackPrompt(prompt)
                Log.w(TAG, "generate: blank response, retrying fallback prompt length=${fallbackPrompt.length}")
                val fallbackResponse = generateResponse(engine, fallbackPrompt)
                Log.d(TAG, "generate: fallback completed response length=${fallbackResponse.length}")
                if (fallbackResponse.isBlank()) {
                    Log.e(TAG, "generate: blank response after fallback")
                    send(GenerationEvent.Error("Local model returned an empty reply. Try again or clear history."))
                    return@channelFlow
                }
                fallbackResponse.chunked(24).forEach { chunk ->
                    send(GenerationEvent.Token(chunk))
                }
                send(GenerationEvent.Completed(fallbackResponse))
                return@channelFlow
            }
            response.chunked(24).forEach { chunk ->
                send(GenerationEvent.Token(chunk))
            }
            send(GenerationEvent.Completed(response))
        } catch (cancelled: CancellationException) {
            Log.d(TAG, "generate: cancelled")
            throw cancelled
        } catch (error: Throwable) {
            Log.e(TAG, "generate: failed", error)
            send(GenerationEvent.Error(error.message ?: "Generation failed"))
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            engineMutex.withLock {
                Log.d(TAG, "stop: unloading current model")
                llmInference?.close()
                llmInference = null
                loadedPath = null
                state = when (val current = state) {
                    is ModelLoadState.Ready -> ModelLoadState.Imported(current.path)
                    is ModelLoadState.Loading -> ModelLoadState.Imported(current.path)
                    else -> current
                }
            }
        }
    }

    private suspend fun generateResponse(engine: LlmInference, prompt: String): String {
        return withContext(Dispatchers.IO) {
            withTimeout(GENERATION_TIMEOUT_MS) {
                engine.generateResponseAsync(prompt).await().trim()
            }
        }
    }

    private fun buildFallbackPrompt(prompt: String): String {
        val responseLanguageLine = prompt
            .lineSequence()
            .lastOrNull { it.startsWith("response_language:") }
            ?.substringAfter(':')
            ?.trim()
            .orEmpty()
        val latestUserLine = prompt
            .lineSequence()
            .lastOrNull { it.startsWith("user:") }
            ?.removePrefix("user:")
            ?.trim()
            .orEmpty()
        val question = latestUserLine.ifBlank { prompt.takeLast(400) }
        return buildString {
            appendLine("system: You are KITT, a local offline Android voice assistant.")
            appendLine("system_rules: Answer the user's latest request directly in 1 to 3 short sentences. Be accurate and concrete. Do not invent facts, app capabilities, product identity, or history.")
            if (responseLanguageLine.isNotBlank()) {
                append("response_language: ")
                appendLine(responseLanguageLine)
            }
            appendLine("facts: KITT is a local voice-first Android app that runs a local model and speaks replies with Android text to speech.")
            append("user: ")
            appendLine(question)
            append("assistant:")
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
