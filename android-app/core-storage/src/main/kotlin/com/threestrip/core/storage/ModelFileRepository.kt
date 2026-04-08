package com.threestrip.core.storage

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ModelFileRepository(private val context: Context) {
    suspend fun importModel(uri: Uri): File = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val ext = resolver.getType(uri)?.substringAfterLast('/') ?: "task"
        val name = "model-${UUID.randomUUID()}.$ext"
        val modelDir = File(context.filesDir, "models").apply { mkdirs() }
        val target = File(modelDir, name)
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open selected model")
        target
    }

    suspend fun importCorpus(uri: Uri): File = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?: uri.lastPathSegment?.substringAfterLast('.', "txt")
            ?: "txt"
        val name = "corpus-${UUID.randomUUID()}.$extension"
        val corpusDir = File(context.filesDir, "corpus").apply { mkdirs() }
        val target = File(corpusDir, name)
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open selected corpus")
        target
    }

    suspend fun readText(path: String?): String = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext ""
        runCatching { File(path).takeIf { it.exists() }?.readText() ?: "" }.getOrDefault("")
    }

    suspend fun firstLocalModelPath(): String? = withContext(Dispatchers.IO) {
        val modelDir = File(context.filesDir, "models")
        modelDir.listFiles()
            ?.filter { it.isFile && (it.extension.equals("task", true) || it.extension.equals("litertlm", true)) }
            ?.sortedBy { it.name }
            ?.firstOrNull()
            ?.absolutePath
    }
}
