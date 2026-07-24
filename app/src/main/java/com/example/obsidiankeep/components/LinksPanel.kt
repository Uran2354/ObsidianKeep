package com.example.obsidiankeep.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.obsidiankeep.data.Note

private val LINK_REGEX = "\\[\\[(.*?)]]".toRegex()

data class LinkChip(val title: String, val exists: Boolean, val noteId: String?)

fun extractLinkChips(content: String, allNotes: List<Note>): List<LinkChip> {
    val matches = LINK_REGEX.findAll(content).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
    if (matches.isEmpty()) return emptyList()
    val titleToNote = allNotes.associateBy { it.title.trim() }
    return matches.distinct().map { title ->
        val note = titleToNote[title.trim()]
        LinkChip(title = title, exists = note != null, noteId = note?.id)
    }
}

@Composable
fun LinksPanel(
    outgoingLinks: List<LinkChip>,
    backlinks: List<Note>,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (outgoingLinks.isNotEmpty()) {
            SectionLabel(stringResource(com.example.obsidiankeep.R.string.links_in_note_label))
            Spacer(modifier = Modifier.height(6.dp))
            ChipsRow(outgoingLinks, onLinkClick, isOutgoing = true)
            if (outgoingLinks.any { !it.exists }) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(com.example.obsidiankeep.R.string.links_missing_hint),
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        if (backlinks.isNotEmpty()) {
            if (outgoingLinks.isNotEmpty()) Spacer(modifier = Modifier.height(12.dp))
            SectionLabel(stringResource(com.example.obsidiankeep.R.string.backlinks_label))
            Spacer(modifier = Modifier.height(6.dp))
            ChipsRow(
                backlinks.map { LinkChip(title = it.title, exists = true, noteId = it.id) },
                onLinkClick,
                isOutgoing = false
            )
        }

        if (outgoingLinks.isEmpty() && backlinks.isEmpty()) {
            Text(
                text = stringResource(com.example.obsidiankeep.R.string.links_empty_hint),
                color = Color(0xFF666666),
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Color(0xFFCCCCCC),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun ChipsRow(chips: List<LinkChip>, onLinkClick: (String) -> Unit, isOutgoing: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { chip ->
            val bg = if (chip.exists) Color(0xFF2A2A2A) else Color(0xFF1A1A1A)
            val border = if (chip.exists) Color(0xFFBB86FC) else Color(0xFF444444)
            val textColor = if (chip.exists) Color.White else Color(0xFF888888)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg, RoundedCornerShape(8.dp))
                    .border(1.dp, border, RoundedCornerShape(8.dp))
                    .then(
                        if (chip.exists) Modifier.clickable { chip.noteId?.let(onLinkClick) }
                        else Modifier
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isOutgoing) "→ " else "← ",
                    color = border,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = chip.title,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
