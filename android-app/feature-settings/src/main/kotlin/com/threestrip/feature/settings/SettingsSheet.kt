package com.threestrip.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.threestrip.core.storage.AppSettings
import com.threestrip.core.storage.ModelLoadState
import com.threestrip.core.ui.ConsoleBlack
import com.threestrip.core.ui.ConsoleDim
import com.threestrip.core.ui.ConsoleRed
import com.threestrip.core.ui.HiddenOverlayTitle
import java.io.File

@Composable
fun SettingsSheet(
    settings: AppSettings,
    modelState: ModelLoadState,
    engineLabel: String,
    voiceLabel: String,
    onToggleTts: (Boolean) -> Unit,
    onToggleAutoSpeak: (Boolean) -> Unit,
    onToggleDebug: (Boolean) -> Unit,
    onCycleEngine: () -> Unit,
    onCycleVoice: () -> Unit,
    onOpenSpeechSettings: () -> Unit,
    onImportModel: () -> Unit,
    onImportCorpus: () -> Unit,
    onClearCorpus: () -> Unit,
    onSaveSystemPrompt: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    var draftPrompt by remember(settings.systemPrompt) { mutableStateOf(settings.systemPrompt) }
    var showAbout by remember { mutableStateOf(false) }
    val modelLabel = remember(settings.modelPath, modelState) {
        settings.modelPath?.let { File(it).name } ?: when (modelState) {
            is ModelLoadState.Ready -> File(modelState.path).name
            is ModelLoadState.Imported -> File(modelState.path).name
            is ModelLoadState.Loading -> File(modelState.path).name
            is ModelLoadState.Error -> "error"
            ModelLoadState.Empty -> "empty"
        }
    }
    val modelStatus = remember(modelState) {
        when (modelState) {
            is ModelLoadState.Ready -> "ready"
            is ModelLoadState.Imported -> "imported"
            is ModelLoadState.Loading -> "loading"
            is ModelLoadState.Error -> "error"
            ModelLoadState.Empty -> "empty"
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SettingsHeader(
            title = if (showAbout) "About" else "Console",
            subtitle = if (showAbout) "What each control does" else "Local controls",
            trailingLabel = if (showAbout) "<" else "i",
            onTrailingClick = { showAbout = !showAbout },
        )
        if (showAbout) {
            AboutPage(modelStatus = modelStatus, modelLabel = modelLabel)
            return@Column
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionTile(label = "MIC", glyph = "|||", onClick = onOpenSpeechSettings, modifier = Modifier.weight(1f))
            ActionTile(label = "LLM", glyph = "[]", onClick = onImportModel, modifier = Modifier.weight(1f))
            ActionTile(label = "DOC", glyph = "##", onClick = onImportCorpus, modifier = Modifier.weight(1f))
            ActionTile(label = "CLR", glyph = "X", onClick = onClearHistory, modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusTile(
                label = "LLM",
                value = modelStatus,
                modifier = Modifier.weight(1f)
            )
            StatusTile(
                label = "SYS",
                value = if (settings.systemPrompt.isBlank()) "empty" else "set",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusTile(
                label = "VOX",
                value = voiceLabel.take(18),
                modifier = Modifier.weight(1f)
            )
            StatusTile(
                label = "FILE",
                value = modelLabel.take(18),
                modifier = Modifier.weight(1f)
            )
        }
        ActionRow(
            label = "ENGINE",
            value = engineLabel,
            glyph = "[]",
            onClick = onCycleEngine,
            modifier = Modifier.padding(top = 12.dp)
        )
        ActionRow(
            label = "VOICE",
            value = voiceLabel,
            glyph = ">>",
            onClick = onCycleVoice,
            modifier = Modifier.padding(top = 12.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusTile(
                label = "DOC",
                value = if (settings.corpusPath == null) "empty" else "loaded",
                modifier = Modifier.weight(1f)
            )
            ActionTile(label = "DROP", glyph = "--", onClick = onClearCorpus, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(
            value = draftPrompt,
            onValueChange = { draftPrompt = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            label = { Text("Preprompt", color = Color(0xFFFFB0B0)) },
            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFFFD9D9)),
            minLines = 3,
            maxLines = 6,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .border(1.dp, ConsoleRed.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .clickable { onSaveSystemPrompt(draftPrompt) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("SAVE", color = ConsoleRed, fontWeight = FontWeight.Bold)
            }
        }
        ToggleRow(glyph = ")))", checked = settings.ttsEnabled, onCheckedChange = onToggleTts)
        ToggleRow(glyph = "A>", checked = settings.autoSpeak, onCheckedChange = onToggleAutoSpeak)
        ToggleRow(glyph = "::", checked = settings.debugOverlay, onCheckedChange = onToggleDebug)
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SettingsHeader(
    title: String,
    subtitle: String,
    trailingLabel: String,
    onTrailingClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        HiddenOverlayTitle(title, subtitle)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(28.dp)
                .background(ConsoleDim.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF555555), RoundedCornerShape(14.dp))
                .clickable(onClick = onTrailingClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = trailingLabel,
                color = Color(0xFFB0B0B0),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AboutPage(modelStatus: String, modelLabel: String) {
    val context = LocalContext.current
    val packageInfo = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AboutLine(
            glyph = "v",
            label = packageInfo?.versionName ?: "unknown",
            detail = "installed build version",
        )
        AboutLine("[]", "LLM $modelStatus", modelLabel)
        AboutLine("|||", "MIC", "opens the device speech input settings")
        AboutLine("[]", "LLM", "imports a local model file into app-private storage")
        AboutLine("##", "DOC", "imports a local reference corpus for prompt grounding")
        AboutLine("X", "CLR", "clears saved transcript history")
        AboutLine("LLM", "status", "shows whether the local model is empty, imported, loading, ready, or error")
        AboutLine("ENG", "engine", "cycles through installed local text-to-speech engines such as RHVoice")
        AboutLine("VOX", "voice", "cycles through available local text-to-speech voices")
        AboutLine("FILE", "model", "shows the active model filename from app-private storage")
        AboutLine("SYS", "status", "shows whether the saved preprompt is empty or set")
        AboutLine("DOC", "status", "shows whether a corpus is currently loaded")
        AboutLine("--", "DROP", "removes the active corpus from settings")
        AboutLine("SAVE", "preprompt", "stores the current system instruction text")
        AboutLine(")))", "speech", "enables or disables spoken output")
        AboutLine("A>", "autospeak", "speaks replies automatically after generation")
        AboutLine("::", "debug", "shows or hides the trace/debug overlay")
    }
}

@Composable
private fun AboutLine(glyph: String, label: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleDim.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(ConsoleBlack, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, color = ConsoleRed, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label.uppercase(), color = Color(0xFFFFD0D0), fontWeight = FontWeight.Bold)
            Text(detail, color = Color(0xFFFFB0B0))
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    value: String,
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ConsoleDim.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(ConsoleBlack, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, color = ConsoleRed, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = ConsoleRed, fontWeight = FontWeight.Bold)
            Text(value, color = Color(0xFFFFD0D0), maxLines = 2)
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(88.dp)
            .background(ConsoleDim.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(ConsoleBlack, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, color = ConsoleRed, fontWeight = FontWeight.Bold)
        }
        Text(label, color = Color(0xFFFFB0B0))
    }
}

@Composable
private fun StatusTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(72.dp)
            .background(ConsoleDim.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = ConsoleRed, fontWeight = FontWeight.Bold)
        Text(value, color = Color(0xFFFFB0B0))
    }
}

@Composable
private fun ToggleRow(glyph: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(ConsoleDim.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(glyph, modifier = Modifier.weight(1f), color = ConsoleRed, fontWeight = FontWeight.Bold)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
