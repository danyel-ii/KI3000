package com.threestrip.app

import android.app.Application
import com.threestrip.core.audio.ConsoleTtsController
import com.threestrip.core.audio.OfflineSpeechInputController
import com.threestrip.core.chat.ChatOrchestrator
import com.threestrip.core.llm.LiteRtLocalLlmEngine
import com.threestrip.core.storage.ModelFileRepository
import com.threestrip.core.storage.SettingsStore
import com.threestrip.core.storage.ThreeStripDatabase
import com.threestrip.core.storage.TranscriptRepository

class ThreeStripApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val db = ThreeStripDatabase.build(this)
        container = AppContainer(
            settingsStore = SettingsStore(this),
            transcriptRepository = TranscriptRepository(db.messageDao()),
            modelFileRepository = ModelFileRepository(this),
            llmEngine = LiteRtLocalLlmEngine(this),
            ttsController = ConsoleTtsController(this),
            speechInputController = OfflineSpeechInputController(this),
        )
    }
}

data class AppContainer(
    val settingsStore: SettingsStore,
    val transcriptRepository: TranscriptRepository,
    val modelFileRepository: ModelFileRepository,
    val llmEngine: LiteRtLocalLlmEngine,
    val ttsController: ConsoleTtsController,
    val speechInputController: OfflineSpeechInputController,
) {
    val chatOrchestrator: ChatOrchestrator = ChatOrchestrator(llmEngine, transcriptRepository)
}
