package com.example.obsidiankeep

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.obsidiankeep.components.LineNumberedTextField
import com.example.obsidiankeep.components.MarkdownPreview
import com.example.obsidiankeep.components.LinksPanel
import com.example.obsidiankeep.components.extractLinkChips
import com.example.obsidiankeep.data.Note
import com.example.obsidiankeep.data.RepeatRule
import com.example.obsidiankeep.ui.theme.NoteColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    noteId: String,
    isNewNote: Boolean,
    initialFolderId: String?,
    viewModel: NoteViewModel,
    onBack: () -> Unit,
    onExportClick: (String, String) -> Unit,
    onExportPdf: (Note) -> Unit,
    onExportHtml: (Note) -> Unit,
    onExportTxt: (Note) -> Unit,
    onPrintNote: (Note) -> Unit,
    onNoteClick: (String) -> Unit,
    onOpenMarkdownHelp: () -> Unit,
    onInsertImage: (onMarkdownReady: (String) -> Unit) -> Unit
) {
    val folders by viewModel.folders.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState()
    val backlinks by viewModel.getBacklinks(noteId).collectAsState(initial = emptyList())
    val noteTags by viewModel.getTagsForNote(noteId).collectAsState(initial = emptyList())
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val defaultTitle = stringResource(R.string.editor_no_title)

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(NoteColors.DEFAULT) }
    var selectedFolderId by remember { mutableStateOf(initialFolderId) }
    var reminderAt by remember { mutableStateOf<Long?>(null) }
    var repeatRule by remember { mutableStateOf<String?>(null) }
    var isFavorite by remember { mutableStateOf(false) }
    var showRepeatMenu by remember { mutableStateOf(false) }

    var isDataLoaded by remember { mutableStateOf(false) }
    var showFolderMenu by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(true) }
    var showLinks by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(true) }
    var showTitle by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var bottomCollapsed by remember { mutableStateOf(false) }
    var previewMode by remember { mutableStateOf(false) }
    var newTagText by remember { mutableStateOf("") }

    val keepColors = remember { NoteColors.palette }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    LaunchedEffect(noteId) {
        // Сбрасываем состояние загрузки при смене заметки, иначе после первой
        // загруженной заметки вторая уже не перечитает данные (isDataLoaded=true)
        isDataLoaded = false
        if (isNewNote) {
            title = defaultTitle
            content = ""
            color = NoteColors.DEFAULT
            selectedFolderId = initialFolderId
            // ВАЖНО: пустая строка → null, иначе заметка привяжется к несуществующей папке ""
            val effectiveFolderId = initialFolderId?.takeIf { it.isNotBlank() }
            viewModel.createNoteInFolder(noteId, effectiveFolderId ?: "", title, content, color)
            // Если effectiveFolderId null — createNoteInFolder создаст заметку с folderId=""
            // В repository.createNote folderId="" обрабатывается как отсутствие папки (см. ниже)
            isDataLoaded = true
        } else {
            val cached = allNotes.find { it.id == noteId }
            if (cached != null && !cached.isEncrypted) {
                title = cached.title
                content = cached.content
                color = cached.color
                selectedFolderId = cached.folderId
                reminderAt = cached.reminderAt
                repeatRule = cached.repeatRule
                isFavorite = cached.isFavorite
                isDataLoaded = true
            } else {
                val loadedNote = viewModel.loadNoteById(noteId)
                if (loadedNote != null) {
                    title = loadedNote.title
                    content = loadedNote.content
                    color = loadedNote.color
                    selectedFolderId = loadedNote.folderId
                    reminderAt = loadedNote.reminderAt
                    repeatRule = loadedNote.repeatRule
                    isFavorite = loadedNote.isFavorite
                }
                isDataLoaded = true
            }
        }
    }

    val saveChanges = remember(isDataLoaded, noteId, selectedFolderId, title, content, color, reminderAt, repeatRule, isFavorite) {
        {
            if (isDataLoaded) {
                viewModel.forceSaveChangesNow(
                    Note(
                        id = noteId,
                        folderId = selectedFolderId,
                        title = title,
                        content = content,
                        color = color,
                        reminderAt = reminderAt,
                        repeatRule = repeatRule,
                        isFavorite = isFavorite
                    )
                )
            }
        }
    }

    val contentKey = remember(content) { content.hashCode() }
    val outgoingChips = remember(contentKey, allNotes) { extractLinkChips(content, allNotes) }

    fun pickReminderDateTime() {
        val cal = Calendar.getInstance()
        DatePickerDialog(context, { _, year, month, day ->
            cal.set(year, month, day)
            TimePickerDialog(context, { _, hour, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val time = cal.timeInMillis
                if (time > System.currentTimeMillis()) {
                    reminderAt = time
                    viewModel.setReminder(noteId, time, repeatRule ?: RepeatRule.NONE)
                }
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("✎", color = Color.White, fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            saveChanges()
                            onBack()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "←", color = Color.White, fontSize = 28.sp, modifier = Modifier.offset(y = (-7).dp))
                        }
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        FilledIconButton(
                            onClick = {
                                val newFav = !isFavorite
                                isFavorite = newFav
                                viewModel.setFavorite(noteId, newFav)
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isFavorite) Color(0xFFFB8C00) else Color(0xFF2C2C2C)
                            ),
                            modifier = Modifier.size(40.dp)
                        ) { Text(if (isFavorite) "★" else "☆", fontSize = 20.sp, color = Color.White) }

                        // Переключатель режим ✏️/👁 (правка/предпросмотр)
                        FilledIconButton(
                            onClick = { previewMode = !previewMode },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (previewMode) Color(0xFF43A047) else Color(0xFF2C2C2C)
                            ),
                            modifier = Modifier.size(40.dp)
                        ) { Text(if (previewMode) "✏️" else "👁", fontSize = 18.sp) }

                        // Вставка изображения из галереи
                        FilledIconButton(
                            onClick = {
                                onInsertImage { markdown ->
                                    content = if (content.isBlank()) markdown
                                    else content + "\n\n" + markdown
                                    saveChanges()
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF00897B)),
                            modifier = Modifier.size(40.dp)
                        ) { Text("🖼", fontSize = 16.sp) }

                        // Меню экспорта (PDF/HTML/TXT/Печать/MD) перенесено в блок «Действия».

                        // Find & Replace остаётся в TopAppBar — это инструмент редактирования.
                        var showFindReplace by remember { mutableStateOf(false) }
                        FilledIconButton(
                            onClick = { showFindReplace = true },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF555555)),
                            modifier = Modifier.size(40.dp)
                        ) { Text("🔍", fontSize = 16.sp) }
                        if (showFindReplace) {
                            com.example.obsidiankeep.components.FindReplaceDialog(
                                initialContent = content,
                                onApplyReplaceAll = { newContent ->
                                    content = newContent
                                    saveChanges()
                                    showFindReplace = false
                                },
                                onApplyReplaceNext = { newContent ->
                                    content = newContent
                                    saveChanges()
                                },
                                onDismiss = { showFindReplace = false }
                            )
                        }

                        // Аудио-заметка (запись/воспр.) перенесена в «⋮» меню действий.

                        Button(
                            onClick = { pickReminderDateTime() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFB8C00)),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(40.dp)
                        ) { Text("🔔", fontSize = 18.sp) }

                        if (reminderAt != null) {
                            Box {
                                Button(
                                    onClick = { showRepeatMenu = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = when (repeatRule) {
                                            RepeatRule.DAILY -> "🔁"
                                            RepeatRule.WEEKDAYS -> "📅"
                                            RepeatRule.WEEKLY -> "🗓"
                                            else -> "🔁"
                                        },
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                }
                                DropdownMenu(
                                    expanded = showRepeatMenu,
                                    onDismissRequest = { showRepeatMenu = false },
                                    modifier = Modifier.background(Color(0xFF1E1E1E))
                                ) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.reminder_repeat_once), color = Color.White) }, onClick = {
                                        repeatRule = RepeatRule.NONE
                                        showRepeatMenu = false
                                        viewModel.setReminder(noteId, reminderAt, RepeatRule.NONE)
                                    })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.reminder_repeat_daily), color = Color.White) }, onClick = {
                                        repeatRule = RepeatRule.DAILY
                                        showRepeatMenu = false
                                        viewModel.setReminder(noteId, reminderAt, RepeatRule.DAILY)
                                    })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.reminder_repeat_weekdays), color = Color.White) }, onClick = {
                                        repeatRule = RepeatRule.WEEKDAYS
                                        showRepeatMenu = false
                                        viewModel.setReminder(noteId, reminderAt, RepeatRule.WEEKDAYS)
                                    })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.reminder_repeat_weekly), color = Color.White) }, onClick = {
                                        repeatRule = RepeatRule.WEEKLY
                                        showRepeatMenu = false
                                        viewModel.setReminder(noteId, reminderAt, RepeatRule.WEEKLY)
                                    })
                                }
                            }
                        }

                        // Кнопка 📤 Markdown-экспорта перенесена в блок «Действия».
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
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .animateContentSize(animationSpec = tween(200))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Компактная полоса «Свернуть/Развернуть всё» — высота как у заголовка заметки
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (bottomCollapsed) stringResource(R.string.editor_expand_all) else stringResource(R.string.editor_collapse_all),
                        color = Color.Gray,
                        fontSize = 10.sp,
                        modifier = Modifier.clickable {
                            if (bottomCollapsed) {
                                bottomCollapsed = false
                                showColors = false
                                showActions = false
                                showLinks = false
                            } else {
                                bottomCollapsed = true
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (bottomCollapsed) "▼" else "▲",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable {
                            if (bottomCollapsed) {
                                bottomCollapsed = false
                                showColors = false
                                showActions = false
                                showLinks = false
                            } else {
                                bottomCollapsed = true
                            }
                        }
                    )
                }

                AnimatedVisibility(visible = !bottomCollapsed) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showColors = !showColors }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (showColors) "▼" else "▶",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.editor_color_label), color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        AnimatedVisibility(visible = showColors) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    keepColors.forEach { intColor ->
                                        val isSelected = color == intColor
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .border(
                                                    width = if (isSelected) 3.dp else 0.dp,
                                                    color = if (isSelected) Color.White else Color.Transparent,
                                                    shape = CircleShape
                                                )
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
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showActions = !showActions }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (showActions) "▼" else "▶",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.editor_actions_label, folders.find { it.id == selectedFolderId }?.name ?: stringResource(R.string.editor_folder_root)),
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        AnimatedVisibility(visible = showActions) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    Button(
                                        onClick = { showFolderMenu = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text("📁 ${folders.find { it.id == selectedFolderId }?.name ?: stringResource(R.string.editor_folder_root)}")
                                    }
                                    DropdownMenu(
                                        expanded = showFolderMenu,
                                        onDismissRequest = { showFolderMenu = false },
                                        modifier = Modifier.background(Color(0xFF1E1E1E))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.editor_folder_no_folder), color = Color.White) },
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

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Переключатель режима ✏️/👁 перенесён в TopAppBar.

                                    // ? — справка по Markdown (ПЕРЕД 📄)
                                    FilledIconButton(
                                        onClick = onOpenMarkdownHelp,
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF2C2C2C)),
                                        modifier = Modifier.size(44.dp)
                                    ) { Text("?", fontSize = 18.sp, color = Color.White) }

                                    // 📄 — меню экспорта (PDF / HTML / TXT / Печать / MD)
                                    var showExportMenu by remember { mutableStateOf(false) }
                                    val currentNote = remember(noteId, selectedFolderId, title, content, color) {
                                        Note(id = noteId, folderId = selectedFolderId, title = title, content = content, color = color)
                                    }
                                    Box {
                                        FilledIconButton(
                                            onClick = { showExportMenu = true },
                                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF1E88E5)),
                                            modifier = Modifier.size(44.dp)
                                        ) { Text("📄", fontSize = 18.sp) }
                                        DropdownMenu(
                                            expanded = showExportMenu,
                                            onDismissRequest = { showExportMenu = false },
                                            modifier = Modifier.background(Color(0xFF1E1E1E))
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.export_menu_pdf), color = Color.White) },
                                                onClick = { showExportMenu = false; onExportPdf(currentNote) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.export_menu_html), color = Color.White) },
                                                onClick = { showExportMenu = false; onExportHtml(currentNote) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.export_menu_txt), color = Color.White) },
                                                onClick = { showExportMenu = false; onExportTxt(currentNote) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.export_menu_print), color = Color.White) },
                                                onClick = { showExportMenu = false; onPrintNote(currentNote) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.export_menu_md), color = Color.White) },
                                                onClick = {
                                                    showExportMenu = false
                                                    onExportClick(title, viewModel.convertNoteToMarkdown(currentNote))
                                                }
                                            )
                                        }
                                    }

                                    // 🎙 аудио перенесено в «⋮» меню → экран «Аудио-заметки».

                                    // 🖼 изображение перенесено в TopAppBar.

                                    FilledIconButton(
                                        onClick = {
                                            focusManager.clearFocus()
                                            saveChanges()
                                            onBack()
                                        },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF43A047)),
                                        modifier = Modifier.size(44.dp)
                                    ) { Text("💾", fontSize = 20.sp) }

                                    FilledIconButton(
                                        onClick = {
                                            focusManager.clearFocus()
                                            viewModel.deleteNote(noteId)
                                            onBack()
                                        },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935)),
                                        modifier = Modifier.size(44.dp)
                                    ) { Text("🗑", fontSize = 20.sp) }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLinks = !showLinks }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val hasLinks = outgoingChips.isNotEmpty() || backlinks.isNotEmpty()
                            Text(
                                text = if (showLinks) "▼" else "▶",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasLinks) {
                                    stringResource(R.string.editor_links_label, outgoingChips.size, backlinks.size)
                                } else {
                                    stringResource(R.string.editor_links_none)
                                },
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        AnimatedVisibility(visible = showLinks) {
                            LinksPanel(
                                outgoingLinks = outgoingChips,
                                backlinks = backlinks,
                                onLinkClick = onNoteClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTags = !showTags }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (showTags) "▼" else "▶",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (noteTags.isEmpty()) {
                                    stringResource(R.string.editor_tags_none)
                                } else {
                                    stringResource(R.string.editor_tags_list, noteTags.joinToString(" ") { "#${it.name}" })
                                },
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        AnimatedVisibility(visible = showTags) {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextField(
                                        value = newTagText,
                                        onValueChange = { newTagText = it },
                                        placeholder = { Text(stringResource(R.string.editor_tag_new), color = Color.Gray, fontSize = 13.sp) },
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFF1E1E1E),
                                            unfocusedContainerColor = Color(0xFF1E1E1E),
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (newTagText.isNotBlank()) {
                                                viewModel.addTagToNote(noteId, newTagText.trim())
                                                newTagText = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) { Text("+", color = Color.Black, fontWeight = FontWeight.Bold) }
                                }
                                if (noteTags.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        noteTags.forEach { tag ->
                                            AssistChip(
                                                onClick = { viewModel.removeTagFromNote(noteId, tag.name) },
                                                label = { Text("#${tag.name}", fontSize = 11.sp, color = Color.White) },
                                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF00897B)),
                                                modifier = Modifier.padding(2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(color))
                    .clickable { showTitle = !showTitle }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showTitle) "▼" else "▶",
                    color = Color.White,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (showTitle) {
                        stringResource(R.string.editor_title_header)
                    } else {
                        title.ifEmpty { defaultTitle }
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.weight(1f))
                if (reminderAt != null) {
                    Text(
                        text = "🔔 ${dateFormat.format(Date(reminderAt!!))}",
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            AnimatedVisibility(visible = showTitle) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(color))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text(stringResource(R.string.editor_title_placeholder), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White) },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(color = Color.White, fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    if (reminderAt != null) {
                        AssistChip(
                            onClick = { reminderAt = null; viewModel.setReminder(noteId, null) },
                            label = { Text("🔔 ${dateFormat.format(Date(reminderAt!!))}", fontSize = 11.sp, color = Color.White, maxLines = 1) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFFB8C00)),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF1A1A1A))
            ) {
                if (!isDataLoaded) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Color.White) }
                } else if (previewMode) {
                    val scrollState = rememberScrollState()
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                        MarkdownPreview(
                            content = content,
                            onCheckboxToggle = { originalLine, checked ->
                                val newLine = if (checked) {
                                    originalLine.replaceFirst(Regex("\\[\\s]"), "[x]")
                                } else {
                                    originalLine.replaceFirst(Regex("\\[[xX]]"), "[ ]")
                                }
                                content = content.replaceFirst(originalLine, newLine)
                                saveChanges()
                            }
                        )
                    }
                } else {
                    // Состояние для автодополнения
                    var cursorPos by remember { mutableStateOf(content.length) }
                    val autocompleteContext = remember(content, cursorPos) {
                        com.example.obsidiankeep.components.detectAutocompleteContext(content, cursorPos)
                    }
                    val allTags by viewModel.allTags.collectAsState(initial = emptyList())

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Панель автодополнения (если активно)
                        if (autocompleteContext != null) {
                            com.example.obsidiankeep.components.AutocompletePanel(
                                context = autocompleteContext,
                                allNotes = allNotes,
                                allTags = allTags,
                                onPickNote = { pickedNoteId, pickedTitle ->
                                    // Заменяем текст после "[[" на заголовок выбранной заметки + "]]"
                                    val before = content.substring(0, cursorPos)
                                    val lastOpen = before.lastIndexOf("[[")
                                    if (lastOpen >= 0) {
                                        val after = content.substring(cursorPos)
                                        val replacement = "$pickedTitle]]"
                                        content = content.substring(0, lastOpen + 2) + replacement + after
                                        val newCursor = lastOpen + 2 + replacement.length
                                        cursorPos = newCursor.coerceAtMost(content.length)
                                    }
                                },
                                onCreateNote = { newTitle ->
                                    // Создаём новую заметку с указанным заголовком и заменяем ссылку
                                    val before = content.substring(0, cursorPos)
                                    val lastOpen = before.lastIndexOf("[[")
                                    if (lastOpen >= 0) {
                                        val after = content.substring(cursorPos)
                                        val replacement = "$newTitle]]"
                                        content = content.substring(0, lastOpen + 2) + replacement + after
                                        val newCursor = (lastOpen + 2 + replacement.length).coerceAtMost(content.length)
                                        cursorPos = newCursor
                                    }
                                    // Параллельно создаём саму заметку
                                    val newId = java.util.UUID.randomUUID().toString()
                                    viewModel.createNote(newId, newTitle, "", NoteColors.DEFAULT)
                                },
                                onPickTag = { tagName ->
                                    // Заменяем текст после "#" на выбранное имя тега
                                    val before = content.substring(0, cursorPos)
                                    val lastHash = before.lastIndexOf('#')
                                    if (lastHash >= 0) {
                                        val after = content.substring(cursorPos)
                                        content = content.substring(0, lastHash + 1) + tagName + after
                                        val newCursor = (lastHash + 1 + tagName.length).coerceAtMost(content.length)
                                        cursorPos = newCursor
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        LineNumberedTextField(
                            value = content,
                            onValueChange = { content = it },
                            modifier = Modifier.weight(1f),
                            onCursorChange = { newPos -> cursorPos = newPos }
                        )
                    }
                }
            }
        }
    }
}
