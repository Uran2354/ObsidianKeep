package com.example.obsidiankeep

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.obsidiankeep.ui.theme.NoteColors
import com.example.obsidiankeep.data.Note
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FolderScreen(
    folderId: String,
    folderName: String,
    viewModel: NoteViewModel,
    onNoteClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? MainActivity }

    val allNotes by viewModel.allNotes.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val globalSortOrder by viewModel.sortOrder.collectAsState()
    val activeSortOrder = remember(folderId, globalSortOrder) { viewModel.getFolderSortOrder(folderId) }
    val unlockedIds by viewModel.unlockedFolderIds.collectAsState()

    val currentFolder = remember(folders, folderId) { folders.find { it.id == folderId } }
    // Decoy-папка "0" выглядит как защищённая в decoy-режиме (для пользователя):
    // он думает что открыл реальную защищённую папку, но контент — из decoy-папки.
    val isDecoyFolder = folderId == com.example.obsidiankeep.repository.NoteRepositoryImpl.DECOY_FOLDER_ID
    val isDecoySession = com.example.obsidiankeep.security.PinManager.get(LocalContext.current).isDecoySession()
    val isFolderProtected = currentFolder?.isProtected == true || (isDecoyFolder && isDecoySession)
    val isSessionUnlocked = unlockedIds.contains(folderId)

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(currentFolder?.name ?: folderName) }
    var folderSearchQuery by remember { mutableStateOf("") }
    var effectiveFolderQuery by remember { mutableStateOf("") }
    LaunchedEffect(folderSearchQuery) {
        kotlinx.coroutines.delay(200)
        effectiveFolderQuery = folderSearchQuery
    }
    var showSortMenu by remember { mutableStateOf(false) }

    var selectedNoteIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedNoteIds.isNotEmpty()

    val filteredFolderNotes = remember(allNotes, folderId, effectiveFolderQuery, activeSortOrder) {
        val baseList = allNotes.filter { note ->
            note.folderId == folderId &&
                    note.title.contains(effectiveFolderQuery, ignoreCase = true)
        }
        when (activeSortOrder) {
            SortOrder.NEWEST -> baseList.sortedByDescending { it.updatedAt }
            SortOrder.OLDEST -> baseList.sortedBy { it.updatedAt }
            SortOrder.ALPHABETIC -> baseList.sortedBy { it.title.lowercase() }
            SortOrder.FAVORITES -> baseList.sortedWith(
                compareByDescending<Note> { it.isFavorite }.thenByDescending { it.updatedAt }
            )
        }
    }

    LaunchedEffect(isEditingName) {
        if (isEditingName) focusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { selectedNoteIds = emptySet() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) { Text(stringResource(R.string.btn_cancel), color = Color.White) }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = stringResource(R.string.selected_count, selectedNoteIds.count()), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        val canRename = !isFolderProtected || isSessionUnlocked
                        if (isEditingName && canRename) {
                            BasicTextField(
                                value = editedName,
                                onValueChange = { editedName = it },
                                cursorBrush = SolidColor(Color.White),
                                textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontWeight = FontWeight.Bold),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (editedName.trim().isNotEmpty()) {
                                        viewModel.renameFolder(folderId, editedName.trim())
                                    }
                                    isEditingName = false
                                    focusManager.clearFocus()
                                }),
                                modifier = Modifier.fillMaxWidth().padding(end = 16.dp).focusRequester(focusRequester)
                            )
                        } else {
                            Text(
                                text = currentFolder?.name ?: editedName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    if (canRename) isEditingName = true
                                    else Toast.makeText(viewModel.getApplication(), (R.string.folder_unlock_first), Toast.LENGTH_SHORT).show()
                                }.fillMaxWidth()
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (!isSelectionMode) {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "←", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Normal, modifier = Modifier.offset(y = (-7).dp))
                            }
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        var showMoveMenu by remember { mutableStateOf(false) }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledIconButton(
                                onClick = {
                                    val notesToExport = filteredFolderNotes.filter { selectedNoteIds.contains(it.id) }
                                    if (notesToExport.isNotEmpty()) {
                                        activity?.shareMultipleNotesAsMarkdownFiles(notesToExport)
                                        selectedNoteIds = emptySet()
                                    }
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF8E24AA)),
                                modifier = Modifier.size(40.dp)
                            ) { Text("📤", fontSize = 18.sp) }

                            Box {
                                FilledIconButton(
                                    onClick = { showMoveMenu = true },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF333333)),
                                    modifier = Modifier.size(40.dp)
                                ) { Text("📁", fontSize = 18.sp) }

                                DropdownMenu(
                                    expanded = showMoveMenu,
                                    onDismissRequest = { showMoveMenu = false },
                                    modifier = Modifier.background(Color(0xFF1E1E1E))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.editor_folder_no_folder), color = Color.White)},
                                        onClick = {
                                            viewModel.moveMultipleNotes(selectedNoteIds.toList(), null)
                                            selectedNoteIds = emptySet()
                                            showMoveMenu = false
                                        }
                                    )
                                    folders.filter { it.id != folderId }.forEach { folder ->
                                        DropdownMenuItem(
                                            text = { Text(folder.name, color = Color.White) },
                                            onClick = {
                                                viewModel.moveMultipleNotes(selectedNoteIds.toList(), folder.id)
                                                selectedNoteIds = emptySet()
                                                showMoveMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            FilledIconButton(
                                onClick = {
                                    viewModel.deleteMultipleNotes(selectedNoteIds.toList())
                                    selectedNoteIds = emptySet()
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935)),
                                modifier = Modifier.size(40.dp)
                            ) { Text("🗑", fontSize = 18.sp) }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // В decoy-режиме кнопка только показывает статус «🔒 Защищено»,
                            // без действия (пользователь не должен менять защиту decoy-папки).
                            if (isDecoyFolder && isDecoySession) {
                                Button(
                                    onClick = { /* noop */ },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                                ) {
                                    Text(text = stringResource(R.string.folder_protected), color = Color.White)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (isFolderProtected) {
                                            viewModel.unprotectFolder(folderId)
                                            viewModel.lockFolderSession(folderId)
                                        } else {
                                            viewModel.protectFolder(folderId)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isFolderProtected) Color(0xFFE53935) else Color(0xFF43A047))
                                ) {
                                    Text(
                                        text = if (isFolderProtected) stringResource(R.string.folder_protected) else stringResource(R.string.folder_open),
                                        color = Color.White
                                    )
                                }
                            }

                            Box(modifier = Modifier.padding(end = 8.dp)) {
                                FilledIconButton(
                                    onClick = { showSortMenu = true },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF1E1E1E)),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text(
                                        text = when (activeSortOrder) {
                                            SortOrder.NEWEST -> "⏳"
                                            SortOrder.OLDEST -> "⌛"
                                            SortOrder.ALPHABETIC -> "🔤"
                                            SortOrder.FAVORITES -> "⭐"
                                        },
                                        fontSize = 14.sp
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    modifier = Modifier.background(Color(0xFF1E1E1E))
                                ) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_newest), color = Color.White) }, onClick = { viewModel.setFolderSortOrder(folderId, SortOrder.NEWEST); showSortMenu = false })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_oldest), color = Color.White) }, onClick = { viewModel.setFolderSortOrder(folderId, SortOrder.OLDEST); showSortMenu = false })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_alphabetic), color = Color.White) }, onClick = { viewModel.setFolderSortOrder(folderId, SortOrder.ALPHABETIC); showSortMenu = false })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_favorites), color = Color.White) }, onClick = { viewModel.setFolderSortOrder(folderId, SortOrder.FAVORITES); showSortMenu = false })
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
            if (!isSelectionMode) {
                TextField(
                    value = folderSearchQuery,
                    onValueChange = { folderSearchQuery = it },
                    placeholder = { Text(stringResource(R.string.folder_search), color = Color.Gray) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1E1E1E), unfocusedContainerColor = Color(0xFF1E1E1E), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredFolderNotes, key = { it.id }) { note ->
                    val isSelected = selectedNoteIds.contains(note.id)
                    val cardColor = remember(note.color) { Color(note.color) }
                    val neonColor = remember(isSelected, note.color) { NoteColors.neonBorder(note.color, isSelected) }
                    val protectedText = stringResource(R.string.protected_note)
                    val emptyNoteText = stringResource(R.string.editor_empty_note)
                    val previewText = if (note.isEncrypted) protectedText else note.content.ifEmpty { emptyNoteText }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .border(width = if (isSelected) 3.dp else 0.dp, color = neonColor, shape = RoundedCornerShape(10.dp))
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedNoteIds = if (isSelected) selectedNoteIds - note.id else selectedNoteIds + note.id
                                    } else {
                                        onNoteClick(note.id)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        selectedNoteIds = selectedNoteIds + note.id
                                    }
                                }
                            ),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = note.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = previewText, color = Color(0xFFEEEEEE), fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        if (!isSelectionMode) {
            var lastFolderClickTime by remember { mutableLongStateOf(0L) }

            FloatingActionButton(
                onClick = {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastFolderClickTime > 400L) {
                        lastFolderClickTime = currentTime
                        val newId = UUID.randomUUID().toString()
                        // Передаём folderId в URL, чтобы EditorScreen создал заметку в этой папке,
                        // а не в корне.
                        onNoteClick("$newId?isNew=true&folderId=$folderId")
                    }
                },
                containerColor = Color(0xFFBB86FC),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) { Text(stringResource(R.string.add_note), modifier = Modifier.padding(horizontal = 16.dp), color = Color.Black, fontWeight = FontWeight.Bold) }
        }
    }
}
