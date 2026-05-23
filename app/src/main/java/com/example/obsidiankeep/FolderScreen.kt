package com.example.obsidiankeep // Проверьте, что этот package совпадает с вашим!

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
    val activeSortOrder by viewModel.sortOrder.collectAsState()
    val unlockedIds by viewModel.unlockedFolderIds.collectAsState()

    val currentFolder = remember(folders, folderId) { folders.find { it.id == folderId } }
    val isFolderProtected = currentFolder?.isProtected == true
    val isSessionUnlocked = unlockedIds.contains(folderId)

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(currentFolder?.name ?: folderName) }
    var folderSearchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }

    var selectedNoteIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedNoteIds.isNotEmpty()

    // Пересчитываем список заметок только при реальном изменении данных
    val filteredFolderNotes = remember(allNotes, folderId, folderSearchQuery, activeSortOrder) {
        val baseList = allNotes.filter { note ->
            note.folderId == folderId && (
                    note.title.contains(folderSearchQuery, ignoreCase = true) ||
                            note.content.contains(folderSearchQuery, ignoreCase = true)
                    )
        }
        when (activeSortOrder) {
            SortOrder.NEWEST -> baseList.sortedByDescending { it.updatedAt }
            SortOrder.OLDEST -> baseList.sortedBy { it.updatedAt }
            SortOrder.ALPHABETIC -> baseList.sortedBy { it.title.lowercase() }
        }
    }

    LaunchedEffect(isEditingName) {
        if (isEditingName) focusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { selectedNoteIds = emptySet() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) { Text("Отмена", color = Color.White) }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "Выбрано: ${selectedNoteIds.count()}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                                    else Toast.makeText(viewModel.getApplication(), "Сначала откройте папку отпечатком", Toast.LENGTH_SHORT).show()
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
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF6A5ACD)),
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
                                        text = { Text("Корень (без папки)", color = Color.White) },
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
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF900C3F)),
                                modifier = Modifier.size(40.dp)
                            ) { Text("🗑", fontSize = 18.sp) }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (isFolderProtected) {
                                        viewModel.unprotectFolder(folderId)
                                        viewModel.lockFolderSession(folderId)
                                    } else {
                                        viewModel.protectFolder(folderId, "1234")
                                        viewModel.lockFolderSession(folderId)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isFolderProtected) Color(0xFF900C3F) else Color(0xFF386038))
                            ) { Text(text = if (isFolderProtected) "🔒 Защищено" else "🔓 Открыто", color = Color.White) }

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
                                        },
                                        fontSize = 14.sp
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    modifier = Modifier.background(Color(0xFF1E1E1E))
                                ) {
                                    DropdownMenuItem(text = { Text("⏳ Сначала новые", color = Color.White) }, onClick = { viewModel.changeSortOrder(SortOrder.NEWEST); showSortMenu = false })
                                    DropdownMenuItem(text = { Text("⌛ Сначала старые", color = Color.White) }, onClick = { viewModel.changeSortOrder(SortOrder.OLDEST); showSortMenu = false })
                                    DropdownMenuItem(text = { Text("🔤 По алфавиту (А-Я)", color = Color.White) }, onClick = { viewModel.changeSortOrder(SortOrder.ALPHABETIC); showSortMenu = false })
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
                    placeholder = { Text("Поиск в этой папке...", color = Color.Gray) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1E1E1E), unfocusedContainerColor = Color(0xFF1E1E1E), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredFolderNotes, key = { it.id }) { note ->
                    val isSelected = selectedNoteIds.contains(note.id)
                    val cardColor = remember(note.color) { Color(note.color) }

                    // Тяжелые вычисления неоновой рамки изолированы в remember
                    val neonColor = remember(isSelected, note.color) {
                        if (isSelected) {
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(note.color, hsv)
                            hsv[1] = 0.95f
                            hsv[2] = 1.0f
                            Color(android.graphics.Color.HSVToColor(hsv))
                        } else {
                            Color.Transparent
                        }
                    }

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
                            Text(text = note.content.ifEmpty { "Пустая заметка..." }, color = Color(0xFFCCCCCC), fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
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
                        onNoteClick("$newId?isNew=true&folderId=$folderId")
                    }
                },
                containerColor = Color(0xFFBB86FC),
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
            ) { Text("+ Заметка", modifier = Modifier.padding(horizontal = 16.dp), color = Color.Black, fontWeight = FontWeight.Bold) }
        }
    }
}

