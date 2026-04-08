package com.threestrip.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ConsoleMode {
    BOOT,
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR,
}

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val role: String,
    val text: String,
    val createdAt: Long,
)

data class ChatMessage(
    val id: String,
    val role: String,
    val text: String,
    val createdAt: Long,
)

data class AppSettings(
    val modelPath: String? = null,
    val systemPrompt: String = "",
    val corpusPath: String? = null,
    val ttsEnabled: Boolean = true,
    val autoSpeak: Boolean = true,
    val ttsEnginePackage: String? = null,
    val ttsVoiceName: String? = null,
    val debugOverlay: Boolean = false,
)

sealed interface ModelLoadState {
    data object Empty : ModelLoadState
    data class Imported(val path: String) : ModelLoadState
    data class Loading(val path: String) : ModelLoadState
    data class Ready(val path: String) : ModelLoadState
    data class Error(val message: String) : ModelLoadState
}
