package com.example.obsidiankeep

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.obsidiankeep.audio.AudioRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioNotesScreen(
    records: List<AudioRecord>,
    currentlyPlayingId: String?,
    playbackPositionMs: Int,
    playbackDurationMs: Int,
    isRecording: Boolean,
    recordingElapsedMs: Long,
    onPlay: (String) -> Unit,
    onStopPlayback: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audio_notes_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("←", color = Color.White, fontSize = 28.sp, modifier = Modifier.offset(y = (-7).dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = if (isRecording) onStopRecording else onStartRecording,
                containerColor = if (isRecording) Color(0xFFE53935) else Color(0xFFBB86FC),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    if (isRecording) "■" else "🎙",
                    color = if (isRecording) Color.White else Color.Black,
                    fontSize = 20.sp
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(innerPadding)
        ) {
            if (isRecording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE53935))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("● " + stringResource(R.string.audio_recording_in_progress), color = Color.White, fontWeight = FontWeight.Bold)
                    Text(formatTimeMs(recordingElapsedMs), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (records.isEmpty() && !isRecording) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.audio_notes_empty), color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(records, key = { it.id }) { record ->
                        AudioRecordRow(
                            record = record,
                            isPlaying = currentlyPlayingId == record.id,
                            playbackPositionMs = if (currentlyPlayingId == record.id) playbackPositionMs else 0,
                            playbackDurationMs = if (currentlyPlayingId == record.id) playbackDurationMs else record.durationMs,
                            onPlay = { onPlay(record.id) },
                            onStop = onStopPlayback,
                            onDelete = { onDelete(record.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioRecordRow(
    record: AudioRecord,
    isPlaying: Boolean,
    playbackPositionMs: Int,
    playbackDurationMs: Int,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit
) {
    val timeText = if (isPlaying && playbackDurationMs > 0) {
        "${formatTimeMs(playbackPositionMs.toLong())} / ${formatTimeMs(playbackDurationMs.toLong())}"
    } else {
        formatTimeMs(record.durationMs.toLong())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
            .clickable { if (isPlaying) onStop() else onPlay() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🎵", fontSize = 16.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = timeText,
                color = if (isPlaying) Color(0xFF43A047) else Color.Gray,
                fontSize = 11.sp
            )
        }
        FilledIconButton(
            onClick = if (isPlaying) onStop else onPlay,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isPlaying) Color(0xFFE53935) else Color(0xFF43A047)
            ),
            modifier = Modifier.size(32.dp)
        ) {
            Text(if (isPlaying) "■" else "▶", color = Color.White, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        FilledIconButton(
            onClick = onDelete,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935)),
            modifier = Modifier.size(32.dp)
        ) {
            Text("✕", color = Color.White, fontSize = 14.sp)
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}
