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
import com.example.obsidiankeep.data.Note
import com.example.obsidiankeep.data.RepeatRule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    notes: List<Note>,
    onNoteClick: (String) -> Unit,
    onClearReminder: (String) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val now = remember { System.currentTimeMillis() }
    val sorted = remember(notes, now) { notes.filter { it.reminderAt != null }.sortedBy { it.reminderAt } }
    var showConfirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminders_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("←", color = Color.White, fontSize = 28.sp, modifier = Modifier.offset(y = (-7).dp))
                        }
                    }
                },
                actions = {
                    if (sorted.isNotEmpty()) {
                        TextButton(onClick = { showConfirmClear = true }) {
                            Text(stringResource(R.string.btn_clear_all), color = Color(0xFFE53935), fontSize = 13.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(innerPadding)
        ) {
            if (sorted.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.reminders_empty), color = Color.Gray, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sorted, key = { it.id }) { note ->
                        ReminderRow(
                            note = note,
                            dateFormat = dateFormat,
                            isOverdue = (note.reminderAt ?: 0) <= now,
                            onClick = { onNoteClick(note.id) },
                            onClear = { onClearReminder(note.id) }
                        )
                    }
                }
            }
        }

        if (showConfirmClear) {
            AlertDialog(
                onDismissRequest = { showConfirmClear = false },
                title = { Text(stringResource(R.string.reminders_confirm_clear)) },
                text = { Text(stringResource(R.string.reminders_confirm_clear_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        onClearAll()
                        showConfirmClear = false
                    }) { Text(stringResource(R.string.reminders_clear_all), color = Color(0xFFE53935)) }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmClear = false }) { Text(stringResource(R.string.btn_cancel), color = Color.White) }
                }
            )
        }
    }
}

@Composable
private fun ReminderRow(
    note: Note,
    dateFormat: SimpleDateFormat,
    isOverdue: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    val trigger = note.reminderAt ?: return
    val repeatLabel = when (note.repeatRule) {
        RepeatRule.DAILY -> stringResource(R.string.reminder_repeat_daily)
        RepeatRule.WEEKDAYS -> stringResource(R.string.reminder_repeat_weekdays)
        RepeatRule.WEEKLY -> stringResource(R.string.reminder_repeat_weekly)
        else -> ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            .border(1.dp, if (isOverdue) Color(0xFFE53935) else Color(0xFF333333), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(Color(note.color), RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dateFormat.format(Date(trigger)) + repeatLabel + if (isOverdue) stringResource(R.string.reminders_overdue) else "",
                color = if (isOverdue) Color(0xFFE53935) else Color.Gray,
                fontSize = 12.sp
            )
        }
        Button(
            onClick = onClear,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) { Text("✕", color = Color.White, fontSize = 14.sp) }
    }
}
