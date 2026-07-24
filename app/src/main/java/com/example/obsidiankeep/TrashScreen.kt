package com.example.obsidiankeep

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: NoteViewModel,
    onBack: () -> Unit
) {
    val trashedNotes by viewModel.trashedNotes.collectAsState()
    val trashedFolders by viewModel.trashedFolders.collectAsState()

    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val now = remember { System.currentTimeMillis() }
    val tenDaysMs = 10L * 24 * 60 * 60 * 1000
    var showConfirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("←", color = Color.White, fontSize = 28.sp, modifier = Modifier.offset(y = (-7).dp))
                        }
                    }
                },
                actions = {
                    if (trashedNotes.isNotEmpty() || trashedFolders.isNotEmpty()) {
                        TextButton(onClick = { viewModel.restoreAllTrash() }) {
                            Text(stringResource(R.string.btn_restore_all), color = Color(0xFF43A047), fontSize = 13.sp)
                        }
                        TextButton(onClick = { showConfirmClear = true }) {
                            Text(stringResource(R.string.reminders_clear_all),  color = Color(0xFFE53935), fontSize = 13.sp)
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
            Text(
                stringResource(R.string.trash_hint),
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (trashedFolders.isEmpty() && trashedNotes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.trash_empty), color = Color.Gray, fontSize = 16.sp)
                }
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (trashedFolders.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.trash_section_folders), color = Color(0xFFBB86FC), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp))
                    }
                    items(trashedFolders, key = { it.id }) { folder ->
                        val deletedAt = folder.deletedAt ?: now
                        val daysLeft = ((deletedAt + tenDaysMs - now) / (24L * 60 * 60 * 1000)).coerceAtLeast(0)
                        TrashFolderRow(
                            name = folder.name,
                            deletedAtText = dateFormat.format(Date(deletedAt)),
                            daysLeft = daysLeft.toInt(),
                            onRestore = { viewModel.restoreFolder(folder.id) }
                        )
                    }
                }

                if (trashedNotes.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.trash_section_notes), color = Color(0xFFBB86FC), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp))
                    }
                    items(trashedNotes, key = { it.id }) { note ->
                        val deletedAt = note.deletedAt ?: now
                        val daysLeft = ((deletedAt + tenDaysMs - now) / (24L * 60 * 60 * 1000)).coerceAtLeast(0)
                        TrashNoteRow(
                            title = note.title,
                            preview = if (note.isEncrypted) stringResource(R.string.protected_note) else note.content,
                            color = note.color,
                            deletedAtText = dateFormat.format(Date(deletedAt)),
                            daysLeft = daysLeft.toInt(),
                            onRestore = { viewModel.restoreNote(note.id) },
                            onDeleteForever = { viewModel.hardDeleteNotes(listOf(note.id)) }
                        )
                    }
                }
            }
        }

        if (showConfirmClear) {
            AlertDialog(
                onDismissRequest = { showConfirmClear = false },
                title = { Text(stringResource(R.string.trash_confirm_clear)) },
                text = { Text(stringResource(R.string.trash_confirm_clear_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearAllTrash()
                        showConfirmClear = false
                    }) { Text(stringResource(R.string.trash_confirm_clear), color = Color(0xFFE53935)) }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmClear = false }) { Text(stringResource(R.string.btn_cancel), color = Color.White) }
                }
            )
        }
    }
}

@Composable
private fun TrashFolderRow(
    name: String,
    deletedAtText: String,
    daysLeft: Int,
    onRestore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "📁", fontSize = 20.sp)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = "${stringResource(R.string.trash_deleted_days, deletedAtText)} · ${stringResource(R.string.trash_days_left, daysLeft)}", color = Color.Gray, fontSize = 12.sp)
        }
        Button(
            onClick = onRestore,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) { Text("↩", color = Color.White, fontSize = 16.sp) }
    }
}

@Composable
private fun TrashNoteRow(
    title: String,
    preview: String,
    color: Int,
    deletedAtText: String,
    daysLeft: Int,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(Color(color), RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = preview.ifEmpty { stringResource(R.string.editor_empty_note) },
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = stringResource(R.string.trash_deleted_full, deletedAtText, daysLeft), color = Color.Gray, fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRestore,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("↩", color = Color.White, fontSize = 16.sp) }
            Button(
                onClick = onDeleteForever,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("✕", color = Color.White, fontSize = 16.sp) }
        }
    }
}
