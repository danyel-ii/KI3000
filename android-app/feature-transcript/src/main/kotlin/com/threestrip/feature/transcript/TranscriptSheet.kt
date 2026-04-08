package com.threestrip.feature.transcript

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.threestrip.core.storage.ChatMessage
import com.threestrip.core.ui.ConsoleDim
import com.threestrip.core.ui.ConsoleRed
import com.threestrip.core.ui.HiddenOverlayTitle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TranscriptSheet(messages: List<ChatMessage>, transientReply: String, error: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        HiddenOverlayTitle("Trace", error ?: "Voice session history")
        LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
            items(messages.takeLast(14)) { message ->
                TranscriptEventChip(message)
            }
            if (transientReply.isNotBlank()) {
                item { TranscriptGhostChip() }
            }
        }
    }
}

@Composable
private fun TranscriptEventChip(message: ChatMessage) {
    val color = if (message.role == "assistant") ConsoleRed else Color(0xFFFF9A9A)
    val pulseCount = (message.text.length.coerceAtLeast(8).coerceAtMost(36) / 4)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(ConsoleDim.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(pulseCount) {
                Box(
                    modifier = Modifier
                        .background(color, CircleShape)
                        .padding(4.dp)
                )
            }
        }
        Text(
            text = formatTime(message.createdAt),
            color = Color(0xFFFFB0B0),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TranscriptGhostChip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(ConsoleDim.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(6) {
            Box(
                modifier = Modifier
                    .background(ConsoleRed.copy(alpha = 0.6f), CircleShape)
                    .padding(4.dp)
            )
        }
    }
}

private fun formatTime(createdAt: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(createdAt))
}
