package com.threestrip.feature.console

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.threestrip.core.chat.ChatOrchestrator
import com.threestrip.core.storage.AppSettings
import com.threestrip.core.storage.ChatMessage
import com.threestrip.core.storage.ConsoleMode
import com.threestrip.core.storage.ModelLoadState
import com.threestrip.core.ui.BootOverlay
import com.threestrip.core.ui.SettingsCogButton
import com.threestrip.core.ui.ThreeStripConsole
import com.threestrip.feature.settings.SettingsSheet
import com.threestrip.feature.transcript.TranscriptSheet
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleRoute(
    orchestrator: ChatOrchestrator,
    displayMode: ConsoleMode,
    messages: List<ChatMessage>,
    settings: AppSettings,
    modelState: ModelLoadState,
    voiceLabel: String,
    onToggleVoiceInput: () -> Unit,
    onStopAll: () -> Unit,
    onImportModel: (Uri) -> Unit,
    onImportCorpus: (Uri) -> Unit,
    onToggleTts: (Boolean) -> Unit,
    onToggleAutoSpeak: (Boolean) -> Unit,
    onToggleDebug: (Boolean) -> Unit,
    onCycleVoice: () -> Unit,
    onOpenSpeechSettings: () -> Unit,
    onSaveSystemPrompt: (String) -> Unit,
    onClearCorpus: () -> Unit,
    onClearHistory: () -> Unit,
) {
    val ui by orchestrator.uiState.collectAsState()
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) onImportModel(uri)
    }
    val corpusPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) onImportCorpus(uri)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                coroutineScope {
                    while (true) {
                        var openedTranscript = false
                        var openedSettings = false
                        awaitPointerEventScope {
                            val first = awaitPointerEvent()
                            if (first.changes.count { it.pressed } >= 2) {
                                val start = first.changes.first().uptimeMillis
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.count { it.pressed }
                                    val elapsed = event.changes.first().uptimeMillis - start
                                    val upward = event.changes.fold(0f) { acc, change -> acc + change.positionChange().y }
                                    if (pressed < 2) break
                                    if (!openedTranscript && upward < -60f) {
                                        orchestrator.openTranscript()
                                        openedTranscript = true
                                    }
                                    if (!openedSettings && elapsed > 550L) {
                                        orchestrator.openSettings()
                                        openedSettings = true
                                    }
                                }
                            }
                        }
                        delay(16)
                    }
                }
            }
    ) {
        ThreeStripConsole(
            mode = displayMode,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleVoiceInput() },
                    onDoubleTap = { onStopAll() },
                )
            }
        )
        BootOverlay(visible = ui.mode == ConsoleMode.BOOT, onFinished = orchestrator::onBootFinished)
        SettingsCogButton(
            onClick = orchestrator::openSettings,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        )

        if (ui.transcriptVisible) {
            ModalBottomSheet(onDismissRequest = orchestrator::closeTranscript) {
                TranscriptSheet(messages, ui.transientReply, ui.error)
            }
        }

        if (ui.settingsVisible) {
            ModalBottomSheet(onDismissRequest = orchestrator::closeSettings) {
                SettingsSheet(
                    settings = settings,
                    modelState = modelState,
                    voiceLabel = voiceLabel,
                    onToggleTts = onToggleTts,
                    onToggleAutoSpeak = onToggleAutoSpeak,
                    onToggleDebug = onToggleDebug,
                    onCycleVoice = onCycleVoice,
                    onOpenSpeechSettings = onOpenSpeechSettings,
                    onImportModel = { modelPicker.launch(arrayOf("*/*")) },
                    onImportCorpus = { corpusPicker.launch(arrayOf("text/*", "*/*")) },
                    onClearCorpus = onClearCorpus,
                    onSaveSystemPrompt = onSaveSystemPrompt,
                    onClearHistory = onClearHistory,
                )
            }
        }
    }
}
