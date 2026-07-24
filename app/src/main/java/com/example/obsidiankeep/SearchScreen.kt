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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.obsidiankeep.data.Note
import com.example.obsidiankeep.data.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    allNotes: List<Note>,
    allTags: List<Tag> = emptyList(),
    onNoteClick: (String) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    val cleanQuery = query.trim().removePrefix("#")
    val isTagSearch = query.trim().startsWith("#")

    val results = remember(query, allNotes, allTags) {
        if (query.isBlank()) emptyList()
        else allNotes.filter { note ->
            note.deletedAt == null && (
                    note.title.contains(cleanQuery, ignoreCase = true) ||
                            (!note.isEncrypted && note.content.contains(cleanQuery, ignoreCase = true)) ||
                            (!note.isEncrypted && note.content.contains("#$cleanQuery", ignoreCase = true))
                    )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("←", color = Color.White, fontSize = 28.sp, modifier = Modifier.offset(y = (-7).dp))
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
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_placeholder), color = Color.Gray) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            if (query.isNotBlank() && results.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_no_results), color = Color.Gray, fontSize = 16.sp)
                }
            } else if (query.isBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_start), color = Color.Gray, fontSize = 16.sp)
                }
            } else {
                Text(
                    text = stringResource(R.string.search_found, results.size),
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(results, key = { it.id }) { note ->
                        SearchResultRow(note, query, onNoteClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(note: Note, query: String, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
            .clickable { onClick(note.id) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier
            .size(14.dp)
            .background(Color(note.color), RoundedCornerShape(3.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightMatch(note.title, query),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!note.isEncrypted) {
                val snippet = findSnippet(note.content, query)
                if (snippet.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = highlightMatch(snippet, query),
                        color = Color(0xFFAAAAAA),
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(stringResource(R.string.protected_note), color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

private fun highlightMatch(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        var idx = 0
        while (idx < text.length) {
            val found = lowerText.indexOf(lowerQuery, idx)
            if (found < 0) {
                append(text.substring(idx))
                break
            }
            if (found > idx) append(text.substring(idx, found))
            withStyle(SpanStyle(background = Color(0xFFFFD54F), color = Color.Black)) {
                append(text.substring(found, found + query.length))
            }
            idx = found + query.length
        }
    }
}

private fun findSnippet(content: String, query: String): String {
    val lowerContent = content.lowercase()
    val idx = lowerContent.indexOf(query.lowercase())
    if (idx < 0) return ""
    val start = (idx - 30).coerceAtLeast(0)
    val end = (idx + query.length + 50).coerceAtMost(content.length)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < content.length) "…" else ""
    return prefix + content.substring(start, end).replace("\n", " ") + suffix
}
