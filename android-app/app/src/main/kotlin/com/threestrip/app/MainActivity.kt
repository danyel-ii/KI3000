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
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.threestrip.core.storage.ConsoleMode
import com.threestrip.core.storage.AppSettings
import com.threestrip.core.storage.ModelLoadState
import com.threestrip.core.ui.ThreeStripTheme
import com.threestrip.feature.console.ConsoleRoute
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ThreeStripApp).container
        setContent {
            ThreeStripTheme {
                val settings by container.settingsStore.settings.collectAsState(
                    initial = AppSettings(systemPrompt = "")
                )
                val messages by container.transcriptRepository.observeMessages().collectAsState(initial = emptyList())
                val ui by container.chatOrchestrator.uiState.collectAsState()
                val ttsMode by container.ttsController.mode.collectAsState()
                val speechMode by container.speechInputController.mode.collectAsState()
                val ttsAvailability by container.ttsController.availability.collectAsState()
                val ttsCompletions by container.ttsController.completedUtterances.collectAsState()
                val scope = rememberCoroutineScope()
                val lastSpokenId = remember { mutableStateOf<String?>(null) }
                var conversationLoopEnabled by remember { mutableStateOf(false) }
                var restartAfterSpeech by remember { mutableStateOf(false) }
                var lastHandledTtsCompletion by remember { mutableStateOf(0L) }
                var resolvedStartupModelPath by remember { mutableStateOf<String?>(null) }
                var startupModelLoadAttempted by remember { mutableStateOf(false) }
                val engineOptions = remember(ttsAvailability.ready, settings?.ttsEnginePackage) {
                    container.ttsController.listEngines()
                }
                val currentEngineLabel = remember(ttsAvailability.ready, settings?.ttsEnginePackage, engineOptions) {
                    val selected = settings?.ttsEnginePackage ?: container.ttsController.currentEnginePackage()
                    engineOptions.firstOrNull { it.packageName == selected }?.label ?: "system"
                }
                val voiceOptions = remember(ttsAvailability.ready, settings?.ttsEnginePackage, settings?.ttsVoiceName) {
                    container.ttsController.listVoices()
                }
                val languageTag = settings.speechLanguageTag
                val selectedLocale = remember(languageTag) { Locale.forLanguageTag(languageTag) }
                val currentLanguageLabel = when (selectedLocale.language) {
                    Locale.GERMAN.language -> "Deutsch"
                    else -> "English"
                }
                val currentVoiceLabel = remember(ttsAvailability.ready, settings?.ttsVoiceName, voiceOptions) {
                    val selected = settings?.ttsVoiceName ?: container.ttsController.currentVoiceName()
                    voiceOptions.firstOrNull { it.name == selected }?.label ?: "default"
                }
                val effectiveModelState = when (val engineState = container.llmEngine.state) {
                    is ModelLoadState.Ready -> engineState
                    is ModelLoadState.Error -> engineState
                    is ModelLoadState.Loading -> engineState
                    is ModelLoadState.Imported -> engineState
                    ModelLoadState.Empty -> settings?.modelPath?.let { ModelLoadState.Imported(it) } ?: ui.modelState
                }
                suspend fun submitVoiceInput(spoken: String) {
                    val corpusText = container.modelFileRepository.readText(settings.corpusPath)
                    container.chatOrchestrator.submit(
                        messages = messages,
                        input = spoken,
                        systemPrompt = settings.systemPrompt,
                        corpusText = corpusText,
                        responseLanguageTag = settings.speechLanguageTag,
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
                        conversationLoopEnabled = false
                        container.chatOrchestrator.onVoiceInputError("Microphone permission is required.")
                    }
                }
                fun startVoiceConversation() {
                    restartAfterSpeech = false
                    scope.launch {
                        val modelState = when (val engineState = container.llmEngine.state) {
                            is ModelLoadState.Ready -> engineState
                            is ModelLoadState.Error -> engineState
                            is ModelLoadState.Loading -> engineState
                            is ModelLoadState.Imported -> engineState
                            ModelLoadState.Empty -> {
                                val resolvedPath = settings.modelPath ?: container.modelFileRepository.firstLocalModelPath()
                                if (resolvedPath != null) {
                                    container.chatOrchestrator.updateModelState(ModelLoadState.Imported(resolvedPath))
                                    container.llmEngine.loadModel(resolvedPath)
                                } else {
                                    ModelLoadState.Empty
                                }
                            }
                        }
                        container.chatOrchestrator.updateModelState(modelState)
                        when (modelState) {
                            is ModelLoadState.Ready -> {
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
                                        onError = {
                                            restartAfterSpeech = false
                                            container.chatOrchestrator.onVoiceInputError(it)
                                        },
                                    )
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }

                            is ModelLoadState.Error -> {
                                conversationLoopEnabled = false
                                container.chatOrchestrator.onLocalFailure(modelState.message)
                            }

                            is ModelLoadState.Loading,
                            is ModelLoadState.Imported -> {
                                container.chatOrchestrator.onLocalFailure("Model is still loading.")
                            }

                            ModelLoadState.Empty -> {
                                conversationLoopEnabled = false
                                container.chatOrchestrator.onLocalFailure("Import a local model first.")
                            }
                        }
                    }
                }

                LaunchedEffect(settings?.modelPath) {
                    val configuredPath = settings.modelPath
                    val resolvedPath = configuredPath ?: resolvedStartupModelPath ?: container.modelFileRepository.firstLocalModelPath()
                    if (configuredPath == null && resolvedPath != null) {
                        resolvedStartupModelPath = resolvedPath
                        container.settingsStore.updateModelPath(resolvedPath)
                    }
                    if (resolvedPath != null) {
                        val currentModelState = ui.modelState
                        val alreadyResolved = when (currentModelState) {
                            is ModelLoadState.Ready -> currentModelState.path == resolvedPath
                            is ModelLoadState.Loading -> currentModelState.path == resolvedPath
                            is ModelLoadState.Imported -> currentModelState.path == resolvedPath
                            is ModelLoadState.Error -> false
                            ModelLoadState.Empty -> false
                        }
                        if (!alreadyResolved) {
                            container.chatOrchestrator.updateModelState(ModelLoadState.Imported(resolvedPath))
                        }
                        if (!startupModelLoadAttempted || resolvedStartupModelPath != resolvedPath) {
                            startupModelLoadAttempted = true
                            resolvedStartupModelPath = resolvedPath
                            container.chatOrchestrator.updateModelState(container.llmEngine.loadModel(resolvedPath))
                        }
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
                            restartAfterSpeech = false
                            container.chatOrchestrator.onLocalFailure(
                                ttsAvailability.message ?: "Speech output is unavailable."
                            )
                        } else if (!container.ttsController.speak(latest.text)) {
                            restartAfterSpeech = false
                            container.chatOrchestrator.onLocalFailure(
                                container.ttsController.availability.value.message
                                    ?: "Speech output failed."
                            )
                        } else {
                            restartAfterSpeech = conversationLoopEnabled
                        }
                    } else if (
                        latest != null &&
                        latest.role == "assistant" &&
                        latest.id != lastSpokenId.value &&
                        conversationLoopEnabled
                    ) {
                        lastSpokenId.value = latest.id
                        startVoiceConversation()
                    }
                }

                LaunchedEffect(speechMode) {
                    if (speechMode == ConsoleMode.IDLE) {
                        container.chatOrchestrator.onVoiceInputCleared()
                    }
                }

                LaunchedEffect(ttsCompletions, conversationLoopEnabled, restartAfterSpeech) {
                    if (
                        conversationLoopEnabled &&
                        restartAfterSpeech &&
                        ttsCompletions > lastHandledTtsCompletion
                    ) {
                        lastHandledTtsCompletion = ttsCompletions
                        restartAfterSpeech = false
                        startVoiceConversation()
                    }
                }

                LaunchedEffect(settings?.ttsEnginePackage) {
                    container.ttsController.setEngine(settings.ttsEnginePackage)
                }

                LaunchedEffect(languageTag) {
                    if (speechMode != ConsoleMode.LISTENING) {
                        container.speechInputController.setPreferredLocale(selectedLocale)
                    }
                    container.ttsController.setPreferredLocale(selectedLocale)
                }

                LaunchedEffect(settings.ttsEnginePackage, settings.ttsVoiceName, ttsAvailability.ready) {
                    val selectedEngine = settings.ttsEnginePackage ?: container.ttsController.currentEnginePackage()
                    if (ttsAvailability.ready && container.ttsController.currentEnginePackage() == selectedEngine) {
                        container.ttsController.setVoice(settings.ttsVoiceName)
                    }
                }

                ConsoleRoute(
                    orchestrator = container.chatOrchestrator,
                    displayMode = when {
                        ttsMode == ConsoleMode.SPEAKING -> ConsoleMode.SPEAKING
                        speechMode == ConsoleMode.LISTENING -> ConsoleMode.LISTENING
                        speechMode == ConsoleMode.ERROR -> ConsoleMode.ERROR
                        else -> ui.mode
                    },
                    messages = messages,
                    settings = settings,
                    modelState = effectiveModelState,
                    languageLabel = currentLanguageLabel,
                    engineLabel = currentEngineLabel,
                    voiceLabel = currentVoiceLabel,
                    onToggleVoiceInput = {
                        if (conversationLoopEnabled) {
                            conversationLoopEnabled = false
                            restartAfterSpeech = false
                            container.speechInputController.cancel()
                            container.ttsController.stop()
                            container.chatOrchestrator.stopAll()
                        } else {
                            conversationLoopEnabled = true
                            startVoiceConversation()
                        }
                    },
                    onStopAll = {
                        conversationLoopEnabled = false
                        restartAfterSpeech = false
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
                    onCycleLanguage = {
                        scope.launch {
                            val nextTag = if (languageTag.startsWith("de", ignoreCase = true)) "en-US" else "de-DE"
                            container.settingsStore.updateSpeechLanguageTag(nextTag)
                            container.settingsStore.updateTtsVoiceName(null)
                        }
                    },
                    onCycleEngine = {
                        scope.launch {
                            val engines = container.ttsController.listEngines()
                            if (engines.isNotEmpty()) {
                                val currentPackage = settings.ttsEnginePackage ?: container.ttsController.currentEnginePackage()
                                val currentIndex = engines.indexOfFirst { it.packageName == currentPackage }
                                val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % engines.size
                                val next = engines[nextIndex]
                                container.settingsStore.updateTtsEnginePackage(next.packageName)
                                container.settingsStore.updateTtsVoiceName(null)
                            }
                        }
                    },
                    onCycleVoice = {
                        scope.launch {
                            val voices = container.ttsController.listVoices()
                            if (voices.isNotEmpty()) {
                                val currentName = settings.ttsVoiceName ?: container.ttsController.currentVoiceName()
                                val currentIndex = voices.indexOfFirst { it.name == currentName }
                                val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % voices.size
                                val next = voices[nextIndex]
                                container.settingsStore.updateTtsVoiceName(next.name)
                            }
                        }
                    },
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

    override fun onDestroy() {
        if (isFinishing) {
            val container = (application as ThreeStripApp).container
            container.ttsController.shutdown()
            container.speechInputController.shutdown()
        }
        super.onDestroy()
    }
}
