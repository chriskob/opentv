/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.recordings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import app.opentv.core.ServiceLocator
import app.opentv.data.model.Recording
import app.opentv.data.model.RecordingStatus
import app.opentv.recording.RecordingStorage
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecordingsViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = ServiceLocator.get(app)

    val recordings: StateFlow<List<Recording>> =
        graph.recordingRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // A row still marked "recording" on a cold start was killed mid-capture — reconcile it.
        viewModelScope.launch { graph.recordingRepository.failInterrupted() }
    }

    fun stop(id: Long) = graph.recordingEngine.stop(id)

    fun delete(recording: Recording) {
        viewModelScope.launch {
            if (recording.status == RecordingStatus.RECORDING) graph.recordingEngine.stop(recording.id)
            RecordingStorage.delete(graph.settings, recording.filePath)
            graph.recordingRepository.delete(recording.id)
        }
    }
}

@Composable
fun RecordingsScreen(onPlay: (Recording) -> Unit) {
    val viewModel: RecordingsViewModel = viewModel()
    val recordings by viewModel.recordings.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
        Text("Recordings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Record live TV to this box or your NAS, and play it back here with full seeking.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (recordings.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No recordings yet. Press Record in the player, or schedule one from the guide.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recordings, key = { it.id }) { rec ->
                    RecordingRow(
                        recording = rec,
                        onPlay = { onPlay(rec) },
                        onStop = { viewModel.stop(rec.id) },
                        onDelete = { viewModel.delete(rec) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(
    recording: Recording,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    val playable = recording.status == RecordingStatus.COMPLETED
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = recording.logoUrl,
            contentDescription = null,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                recording.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                statusLine(recording),
                style = MaterialTheme.typography.bodySmall,
                color = statusColor(recording.status),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        if (playable) {
            ActionButton(Icons.Filled.PlayArrow, "Play", onPlay)
            Spacer(Modifier.width(8.dp))
        }
        if (recording.status == RecordingStatus.RECORDING) {
            ActionButton(Icons.Filled.Stop, "Stop", onStop)
            Spacer(Modifier.width(8.dp))
        }
        ActionButton(Icons.Filled.Delete, "Delete", onDelete)
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Icon(icon, contentDescription = label, tint = fg)
    }
}

private fun statusLine(rec: Recording): String = when (rec.status) {
    RecordingStatus.SCHEDULED -> "Scheduled · ${rec.channelName}"
    RecordingStatus.RECORDING -> "● Recording · ${rec.channelName} · ${formatSize(rec.sizeBytes)}"
    RecordingStatus.COMPLETED ->
        "${rec.channelName} · ${formatDuration(rec.durationMillis)} · ${formatSize(rec.sizeBytes)}"
    RecordingStatus.FAILED -> "Failed · ${rec.error ?: "unknown error"}"
}

@Composable
private fun statusColor(status: RecordingStatus): Color = when (status) {
    RecordingStatus.RECORDING -> Color(0xFFE53935)
    RecordingStatus.FAILED -> MaterialTheme.colorScheme.error
    RecordingStatus.SCHEDULED -> MaterialTheme.colorScheme.primary
    RecordingStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format("%.1f GB", mb / 1024.0) else String.format("%.0f MB", mb)
}

private fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
