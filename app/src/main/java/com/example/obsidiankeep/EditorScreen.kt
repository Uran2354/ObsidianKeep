package com.example.obsidiankeep // Проверьте, что этот package совпадает с вашим!

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager // ИМПОРТ МЕНЕДЖЕРА ФОКУСА
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.obsidiankeep.components.LineNumberedTextField
import com.example.obsidiankeep.data.Note
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    noteId: String,
    isNewNote: Boolean,
    initialFolderId: String?,
    viewModel: NoteViewModel,
    onBack: () -> Unit,
    onExportClick: (String, String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val folders by viewModel.folders.collectAsState()

    // ФИКС ЧЕРНОГО ЭКРАНА: Подключаем системный менеджер фокуса клавиатуры
    val focusManager = LocalFocusManager.current

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(0xFF2C2C2C.toInt()) }
    var selectedFolderId by remember { mutableStateOf<String?>(initialFolderId) }

    var isDataLoaded by remember { mutableStateOf(false) }
    var showFolderMenu by remember { mutableStateOf(false) }

    val keepColors = listOf(
        0xFF2C2C2C.toInt(), 0xFF603838.toInt(), 0xFF386038.toInt(),
        0xFF2A4B7C.toInt(), 0xFF6A5ACD.toInt(), 0xFF7D6608.toInt()
    )

    LaunchedEffect(noteId) {
        if (!isDataLoaded) {
            val loadedNote = viewModel.loadNoteById(noteId)
            if (isNewNote && loadedNote == null) {
                title = "Без названия"
                content = ""
                color = 0xFF2C2C2C.toInt()
                selectedFolderId = initialFolderId
                viewModel.createNoteInFolder(noteId, initialFolderId ?: "", title, content, color)
            } else if (loadedNote != null) {
                title = loadedNote.title
                content = loadedNote.content
                color = loadedNote.color
                selectedFolderId = loadedNote.folderId
            }
            isDataLoaded = true
        }
    }

    val saveChanges = {
        if (isDataLoaded) {
            viewModel.updateExistingNote(
                Note(id = noteId, folderId = selectedFolderId, title = title, content = content, color = color)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактор", color = Color.White) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            // ЖЕСТКАЯ ОЧИСТКА: Сначала гасим клавиатуру, затем сохраняем и выходим!
                            focusManager.clearFocus()
                            saveChanges()
                            onBack()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "←", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Normal, modifier = Modifier.offset(y = (-7).dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111))
                    .padding(16.dp)
            ) {
                Text("Цвет карточки:", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    keepColors.forEach { intColor ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(intColor), CircleShape)
                                .clickable {
                                    if (isDataLoaded) {
                                        color = intColor
                                        saveChanges()
                                    }
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Button(
                            onClick = { showFolderMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("📁 ${folders.find { it.id == selectedFolderId }?.name ?: "Корень"}")
                        }
                        DropdownMenu(
                            expanded = showFolderMenu,
                            onDismissRequest = { showFolderMenu = false },
                            modifier = Modifier.background(Color(0xFF1E1E1E))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Корень (без папки)", color = Color.White) },
                                onClick = {
                                    selectedFolderId = null
                                    showFolderMenu = false
                                    saveChanges()
                                }
                            )
                            folders.forEach { folder ->
                                DropdownMenuItem(
                                    text = { Text(folder.name, color = Color.White) },
                                    onClick = {
                                        selectedFolderId = folder.id
                                        showFolderMenu = false
                                        saveChanges()
                                    }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledIconButton(
                            onClick = {
                                val markdownText = viewModel.convertNoteToMarkdown(
                                    Note(id = noteId, folderId = selectedFolderId, title = title, content = content, color = color)
                                )
                                onExportClick(title, markdownText)
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF6A5ACD)),
                            modifier = Modifier.size(44.dp)
                        ) { Text("📤", fontSize = 20.sp) }

                        FilledIconButton(
                            onClick = {
                                // ЖЕСТКАЯ ОЧИСТКА: Гасим клавиатуру и принудительно сохраняем
                                focusManager.clearFocus()
                                saveChanges()
                                onBack()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF386038)),
                            modifier = Modifier.size(44.dp)
                        ) { Text("💾", fontSize = 20.sp) }

                        FilledIconButton(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.deleteNote(noteId)
                                onBack()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF900C3F)),
                            modifier = Modifier.size(44.dp)
                        ) { Text("🗑", fontSize = 20.sp) }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(color))
        ) {
            if (!isDataLoaded) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Заголовок заметки", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = Color.White, fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                LineNumberedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
