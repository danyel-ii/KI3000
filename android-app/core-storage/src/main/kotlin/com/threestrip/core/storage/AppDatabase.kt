package com.threestrip.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore("three_strip_settings")

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY createdAt ASC")
    fun observeMessages(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class ThreeStripDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        fun build(context: Context): ThreeStripDatabase =
            Room.databaseBuilder(context, ThreeStripDatabase::class.java, "three_strip.db").build()
    }
}

class SettingsStore(private val context: Context) {
    private val modelPathKey = stringPreferencesKey("model_path")
    private val systemPromptKey = stringPreferencesKey("system_prompt")
    private val corpusPathKey = stringPreferencesKey("corpus_path")
    private val speechLanguageTagKey = stringPreferencesKey("speech_language_tag")
    private val ttsEnabledKey = booleanPreferencesKey("tts_enabled")
    private val autoSpeakKey = booleanPreferencesKey("auto_speak")
    private val ttsEnginePackageKey = stringPreferencesKey("tts_engine_package")
    private val ttsVoiceNameKey = stringPreferencesKey("tts_voice_name")
    private val debugOverlayKey = booleanPreferencesKey("debug_overlay")

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            AppSettings(
                modelPath = prefs[modelPathKey],
                systemPrompt = prefs[systemPromptKey] ?: KITT_SYSTEM_PROMPT,
                corpusPath = prefs[corpusPathKey],
                speechLanguageTag = prefs[speechLanguageTagKey] ?: "en-US",
                ttsEnabled = prefs[ttsEnabledKey] ?: true,
                autoSpeak = prefs[autoSpeakKey] ?: true,
                ttsEnginePackage = prefs[ttsEnginePackageKey],
                ttsVoiceName = prefs[ttsVoiceNameKey],
                debugOverlay = prefs[debugOverlayKey] ?: false,
            )
        }

    suspend fun updateModelPath(path: String?) {
        context.settingsDataStore.edit { prefs ->
            if (path == null) prefs.remove(modelPathKey) else prefs[modelPathKey] = path
        }
    }

    suspend fun updateSystemPrompt(value: String) {
        context.settingsDataStore.edit { prefs ->
            if (value.isBlank()) prefs.remove(systemPromptKey) else prefs[systemPromptKey] = value.trim()
        }
    }

    suspend fun updateCorpusPath(path: String?) {
        context.settingsDataStore.edit { prefs ->
            if (path == null) prefs.remove(corpusPathKey) else prefs[corpusPathKey] = path
        }
    }

    suspend fun updateSpeechLanguageTag(value: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[speechLanguageTagKey] = value
        }
    }

    suspend fun updateTtsEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[ttsEnabledKey] = value }
    }

    suspend fun updateAutoSpeak(value: Boolean) {
        context.settingsDataStore.edit { it[autoSpeakKey] = value }
    }

    suspend fun updateTtsEnginePackage(value: String?) {
        context.settingsDataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(ttsEnginePackageKey) else prefs[ttsEnginePackageKey] = value
        }
    }

    suspend fun updateTtsVoiceName(value: String?) {
        context.settingsDataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(ttsVoiceNameKey) else prefs[ttsVoiceNameKey] = value
        }
    }

    suspend fun updateDebugOverlay(value: Boolean) {
        context.settingsDataStore.edit { it[debugOverlayKey] = value }
    }
}

interface TranscriptStore {
    fun observeMessages(): Flow<List<ChatMessage>>
    suspend fun append(message: ChatMessage)
    suspend fun clear()
}

class TranscriptRepository(private val dao: MessageDao) : TranscriptStore {
    override fun observeMessages(): Flow<List<ChatMessage>> = dao.observeMessages().map { list ->
        list.map { ChatMessage(it.id, it.role, it.text, it.createdAt) }
    }

    override suspend fun append(message: ChatMessage) {
        dao.insert(MessageEntity(message.id, message.role, message.text, message.createdAt))
    }

    override suspend fun clear() = dao.clear()
}
