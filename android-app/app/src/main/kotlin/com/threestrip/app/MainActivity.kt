package com.threestrip.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import com.threestrip.core.storage.ConsoleMode
import com.threestrip.core.storage.ModelLoadState
import com.threestrip.core.ui.ThreeStripTheme
import com.threestrip.feature.console.ConsoleRoute
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ThreeStripApp).container
        setContent {
            ThreeStripTheme {
                val settings by container.settingsStore.settings.collectAsState(initial = null)
                val messages by container.transcriptRepository.observeMessages().collectAsState(initial = emptyList())
                val ui by container.chatOrchestrator.uiState.collectAsState()
                val ttsMode by container.ttsController.mode.collectAsState()
                val speechMode by container.speechInputController.mode.collectAsState()
                val ttsAvailability by container.ttsController.availability.collectAsState()
                val scope = rememberCoroutineScope()
                val lastSpokenId = remember { mutableStateOf<String?>(null) }
                suspend fun submitVoiceInput(spoken: String) {
                    val settingsSnapshot = settings ?: return
                    val corpusText = container.modelFileRepository.readText(settingsSnapshot.corpusPath)
                    container.chatOrchestrator.submit(
                        messages = messages,
                        input = spoken,
                        systemPrompt = settingsSnapshot.systemPrompt,
                        corpusText = corpusText,
                    )
                }
                val micPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        container.ttsController.stop()
                        container.chatOrchestrator.stopAll()
                        container.chatOrchestrator.onVoiceListening()
                        container.speechInputController.startListening(
                            onRecognized = { spoken ->
                                scope.launch { submitVoiceInput(spoken) }
                            },
                            onError = container.chatOrchestrator::onVoiceInputError,
                        )
                    } else {
                        container.chatOrchestrator.onVoiceInputError("Microphone permission is required.")
                    }
                }

                LaunchedEffect(settings?.modelPath) {
                    settings?.modelPath?.let { path ->
                        container.chatOrchestrator.updateModelState(ModelLoadState.Imported(path))
                        container.chatOrchestrator.updateModelState(container.llmEngine.loadModel(path))
                    }
                }

                LaunchedEffect(messages.lastOrNull()?.id, settings?.autoSpeak, settings?.ttsEnabled) {
                    val latest = messages.lastOrNull()
                    if (
                        latest != null &&
                        latest.role == "assistant" &&
                        latest.id != lastSpokenId.value &&
                        settings?.autoSpeak == true &&
                        settings?.ttsEnabled == true
                    ) {
                        lastSpokenId.value = latest.id
                        if (!ttsAvailability.ready) {
                            container.chatOrchestrator.onLocalFailure(
                                ttsAvailability.message ?: "Speech output is unavailable."
                            )
                        } else if (!container.ttsController.speak(latest.text)) {
                            container.chatOrchestrator.onLocalFailure(
                                container.ttsController.availability.value.message
                                    ?: "Speech output failed."
                            )
                        }
                    }
                }

                LaunchedEffect(speechMode) {
                    if (speechMode == ConsoleMode.IDLE) {
                        container.chatOrchestrator.onVoiceInputCleared()
                    }
                }

                if (settings != null) {
                    ConsoleRoute(
                        orchestrator = container.chatOrchestrator,
                        displayMode = when {
                            ttsMode == ConsoleMode.SPEAKING -> ConsoleMode.SPEAKING
                            speechMode == ConsoleMode.LISTENING -> ConsoleMode.LISTENING
                            speechMode == ConsoleMode.ERROR -> ConsoleMode.ERROR
                            else -> ui.mode
                        },
                        messages = messages,
                        settings = settings!!,
                        onPressVoiceInput = {
                            val modelReady = when (ui.modelState) {
                                is ModelLoadState.Ready -> true
                                is ModelLoadState.Imported,
                                is ModelLoadState.Loading -> {
                                    container.chatOrchestrator.onLocalFailure("Model is still loading.")
                                    false
                                }
                                is ModelLoadState.Error -> {
                                    container.chatOrchestrator.onLocalFailure(
                                        (ui.modelState as ModelLoadState.Error).message
                                    )
                                    false
                                }
                                ModelLoadState.Empty -> {
                                    container.chatOrchestrator.onLocalFailure("Import a local model first.")
                                    false
                                }
                            }
                            if (!modelReady) return@ConsoleRoute
                            val hasMicPermission = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasMicPermission) {
                                container.ttsController.stop()
                                container.chatOrchestrator.stopAll()
                                container.chatOrchestrator.onVoiceListening()
                                container.speechInputController.startListening(
                                    onRecognized = { spoken ->
                                        scope.launch { submitVoiceInput(spoken) }
                                    },
                                    onError = container.chatOrchestrator::onVoiceInputError,
                                )
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onReleaseVoiceInput = {
                            container.speechInputController.stop()
                        },
                        onStopAll = {
                            container.speechInputController.cancel()
                            container.ttsController.stop()
                            container.chatOrchestrator.stopAll()
                        },
                        onImportModel = { uri ->
                            scope.launch {
                                val file = container.modelFileRepository.importModel(uri)
                                container.settingsStore.updateModelPath(file.absolutePath)
                                container.chatOrchestrator.updateModelState(container.llmEngine.loadModel(file.absolutePath))
                            }
                        },
                        onImportCorpus = { uri ->
                            scope.launch {
                                val file = container.modelFileRepository.importCorpus(uri)
                                container.settingsStore.updateCorpusPath(file.absolutePath)
                            }
                        },
                        onToggleTts = { value -> scope.launch { container.settingsStore.updateTtsEnabled(value) } },
                        onToggleAutoSpeak = { value -> scope.launch { container.settingsStore.updateAutoSpeak(value) } },
                        onToggleDebug = { value -> scope.launch { container.settingsStore.updateDebugOverlay(value) } },
                        onOpenSpeechSettings = {
                            startActivity(Intent("android.settings.VOICE_INPUT_SETTINGS"))
                        },
                        onSaveSystemPrompt = { value ->
                            scope.launch { container.settingsStore.updateSystemPrompt(value) }
                        },
                        onClearCorpus = {
                            scope.launch { container.settingsStore.updateCorpusPath(null) }
                        },
                        onClearHistory = { scope.launch { container.transcriptRepository.clear() } },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            val container = (application as ThreeStripApp).container
            container.ttsController.shutdown()
            container.speechInputController.shutdown()
        }
        super.onDestroy()
    }
}
