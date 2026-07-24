package com.example.obsidiankeep.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.obsidiankeep.R
import com.example.obsidiankeep.data.Note
import com.example.obsidiankeep.data.Tag

/**
 * Определяет, нужно ли показывать автодополнение в текущей позиции курсора.
 * Возвращает:
 *  - LinkAutocomplete(query) если курсор сразу после "[[" с опциональным текстом
 *  - TagAutocomplete(query) если курсор сразу после "#" с буквами
 *  - null если автодополнение не активно
 */
sealed class AutocompleteContext {
    data class Link(val query: String) : AutocompleteContext()
    data class Tag(val query: String) : AutocompleteContext()
}

fun detectAutocompleteContext(text: String, cursor: Int): AutocompleteContext? {
    if (cursor <= 0) return null
    val before = text.substring(0, cursor)

    // [[ссылка — ищем последнюю "[[" без закрывающей "]]" после неё
    val lastOpen = before.lastIndexOf("[[")
    if (lastOpen >= 0) {
        val afterOpen = before.substring(lastOpen + 2)
        // Если после "[[" есть "]]" или перевод строки — автодополнение не активируем
        if ("]]" !in afterOpen && "\n" !in afterOpen) {
            return AutocompleteContext.Link(afterOpen.trim())
        }
    }

    // #тег — ищем последний "#", после которого идут буквы/цифры без пробелов
    val hashIdx = before.lastIndexOf('#')
    if (hashIdx >= 0) {
        val afterHash = before.substring(hashIdx + 1)
        // Тег должен состоять только из букв/цифр/нижнего подчёркивания
        if (afterHash.isNotEmpty() && afterHash.matches(Regex("^[\\wа-яА-ЯёЁ]+$")) && (hashIdx == 0 || !before[hashIdx - 1].isLetterOrDigit())) {
            return AutocompleteContext.Tag(afterHash)
        }
    }

    return null
}

/**
 * Панель автодополнения для [[ссылок]] и #тегов.
 * Показывается поверх контента, когда контекст активен.
 *
 * @param context текущий контекст автодополнения (см. detectAutocompleteContext)
 * @param allNotes все заметки (для подсказок ссылок)
 * @param allTags все теги (для подсказок тегов)
 * @param onPickNote вызывается, когда пользователь выбрал существующую заметку (id, title)
 * @param onCreateNote вызывается, когда пользователь выбрал "создать новую заметку" с именем query
 * @param onPickTag вызывается, когда пользователь выбрал тег
 */
@Composable
fun AutocompletePanel(
    context: AutocompleteContext?,
    allNotes: List<Note>,
    allTags: List<Tag>,
    onPickNote: (noteId: String, noteTitle: String) -> Unit,
    onCreateNote: (title: String) -> Unit,
    onPickTag: (tagName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (context == null) return

    when (context) {
        is AutocompleteContext.Link -> {
            val query = context.query
            // Пагинация: показываем по 30, подгружаем ещё при прокрутке
            val pageSize = 30
            var visibleCount by remember(query) { mutableStateOf(pageSize) }
            val filtered = remember(allNotes, query) {
                if (query.isBlank()) allNotes
                else allNotes.filter { it.title.contains(query, ignoreCase = true) }
            }
            val matches = filtered.take(visibleCount)
            val listState = rememberLazyListState()

            // Подгрузка при достижении конца списка
            LaunchedEffect(listState, filtered.size) {
                snapshotFlow { listState.firstVisibleItemIndex to listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                    .collect { (_, lastVisible) ->
                        if (lastVisible != null && lastVisible >= matches.size - 5 && visibleCount < filtered.size) {
                            visibleCount = (visibleCount + pageSize).coerceAtMost(filtered.size)
                        }
                    }
            }

            if (matches.isEmpty() && query.isBlank()) return
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                color = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
            ) {
                LazyColumn(state = listState) {
                    items(matches, key = { it.id }) { note ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickNote(note.id, note.title) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("→", color = Color(0xFF4FC3F7), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(note.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                        }
                    }
                    // Индикатор "ещё N" если не все показаны
                    if (visibleCount < filtered.size) {
                        item {
                            Text(
                                text = "··· +${filtered.size - visibleCount}",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (query.isNotBlank()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCreateNote(query) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("+", color = Color(0xFF43A047), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.autocomplete_create_note, query), color = Color(0xFF43A047), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
        is AutocompleteContext.Tag -> {
            val query = context.query
            // Пагинация для тегов (такая же логика)
            val pageSize = 30
            var visibleCount by remember(query) { mutableStateOf(pageSize) }
            val filtered = remember(allTags, query) {
                if (query.isBlank()) allTags
                else allTags.filter { it.name.contains(query, ignoreCase = true) }
            }
            val matches = filtered.take(visibleCount)
            val listState = rememberLazyListState()

            LaunchedEffect(listState, filtered.size) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                    .collect { lastVisible ->
                        if (lastVisible != null && lastVisible >= matches.size - 5 && visibleCount < filtered.size) {
                            visibleCount = (visibleCount + pageSize).coerceAtMost(filtered.size)
                        }
                    }
            }

            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                color = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
            ) {
                if (matches.isEmpty()) {
                    Text(
                        stringResource(R.string.autocomplete_no_tags),
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                } else {
                    LazyColumn(state = listState) {
                        items(matches, key = { it.id }) { tag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPickTag(tag.name) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("#", color = Color(0xFF00BCD4), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(tag.name, color = Color.White, fontSize = 14.sp)
                            }
                        }
                        if (visibleCount < filtered.size) {
                            item {
                                Text(
                                    text = "··· +${filtered.size - visibleCount}",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Диалог Find & Replace.
 *
 * @param initialContent текущий контент заметки
 * @param onApplyReplaceAll вызывается с новой строкой при "Заменить все"
 * @param onApplyReplaceNext вызывается с новой строкой при "Заменить" (одно вхождение начиная с курсора)
 * @param onDismiss закрытие диалога
 */
@Composable
fun FindReplaceDialog(
    initialContent: String,
    onApplyReplaceAll: (newContent: String) -> Unit,
    onApplyReplaceNext: (newContent: String) -> Unit,
    onDismiss: () -> Unit
) {
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }

    val matchCount = remember(findText, initialContent) {
        if (findText.isBlank()) 0
        else initialContent.split(findText, ignoreCase = true).size - 1
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.find_replace_title), color = Color.White) },
        containerColor = Color(0xFF1E1E1E),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = findText,
                    onValueChange = { findText = it },
                    label = { Text(stringResource(R.string.find_placeholder), color = Color.Gray) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontFamily = FontFamily.Monospace),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFBB86FC),
                        unfocusedBorderColor = Color(0xFF444444)
                    )
                )
                OutlinedTextField(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    label = { Text(stringResource(R.string.replace_placeholder), color = Color.Gray) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontFamily = FontFamily.Monospace),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFBB86FC),
                        unfocusedBorderColor = Color(0xFF444444)
                    )
                )
                Text(
                    text = if (findText.isBlank()) stringResource(R.string.find_replace_no_match)
                    else stringResource(R.string.find_replace_count, matchCount),
                    color = if (matchCount > 0) Color(0xFF43A047) else Color.Gray,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        if (findText.isNotBlank() && matchCount > 0) {
                            // Заменяем первое вхождение (ignoreCase=false для предсказуемости)
                            val idx = initialContent.indexOf(findText)
                            if (idx >= 0) {
                                val newContent = initialContent.substring(0, idx) +
                                        replaceText +
                                        initialContent.substring(idx + findText.length)
                                onApplyReplaceNext(newContent)
                            }
                        }
                    }
                ) { Text(stringResource(R.string.find_replace_replace_next), color = Color(0xFFBB86FC)) }
                TextButton(
                    onClick = {
                        if (findText.isNotBlank() && matchCount > 0) {
                            val newContent = initialContent.replace(findText, replaceText)
                            onApplyReplaceAll(newContent)
                        }
                    }
                ) { Text(stringResource(R.string.find_replace_replace_all), color = Color(0xFF43A047)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = Color.White)
            }
        }
    )
}
