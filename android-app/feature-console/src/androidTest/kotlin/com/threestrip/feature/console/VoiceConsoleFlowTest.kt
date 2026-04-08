package com.threestrip.feature.console

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.threestrip.core.chat.ChatOrchestrator
import com.threestrip.core.llm.FakeLocalLlmEngine
import com.threestrip.core.storage.AppSettings
import com.threestrip.core.storage.ChatMessage
import com.threestrip.core.storage.ConsoleMode
import com.threestrip.core.storage.TranscriptStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class VoiceConsoleFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun voiceConsoleDoesNotShowTextComposer() {
        val orchestrator = ChatOrchestrator(FakeLocalLlmEngine(), object : TranscriptStore {
            override fun observeMessages(): Flow<List<ChatMessage>> = flowOf(emptyList())
            override suspend fun append(message: ChatMessage) = Unit
            override suspend fun clear() = Unit
        })
        composeRule.setContent {
            ConsoleRoute(
                orchestrator = orchestrator,
                displayMode = ConsoleMode.IDLE,
                messages = emptyList(),
                settings = AppSettings(),
                onToggleVoiceInput = {},
                onStopAll = {},
                onImportModel = {},
                onImportCorpus = {},
                onToggleTts = {},
                onToggleAutoSpeak = {},
                onToggleDebug = {},
                onOpenSpeechSettings = {},
                onSaveSystemPrompt = {},
                onClearCorpus = {},
                onClearHistory = {},
            )
        }
        composeRule.onNodeWithText("Say something").assertDoesNotExist()
    }
}
