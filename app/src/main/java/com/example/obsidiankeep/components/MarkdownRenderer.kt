package com.example.obsidiankeep.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class BlockType { HEADING, PARAGRAPH, LIST_ITEM, ORDERED_ITEM, QUOTE, CODE_BLOCK, HR, CHECKBOX, IMAGE }

data class MarkdownBlock(
    val type: BlockType,
    val text: String,
    val checked: Boolean? = null,
    val level: Int = 0,
    val originalLine: String = "",
    val imagePath: String = ""
)

object MarkdownParser {
    private val HEADING = Regex("^(#{1,6})\\s+(.+)$")
    private val CHECKBOX_CHECKED = Regex("^[-*]\\s+\\[[xX]]\\s+(.+)$")
    private val CHECKBOX_UNCHECKED = Regex("^[-*]\\s+\\[\\s]\\s+(.+)$")
    private val UNORDERED = Regex("^[-*]\\s+(.+)$")
    private val ORDERED = Regex("^\\d+\\.\\s+(.+)$")
    private val QUOTE = Regex("^>\\s*(.*)$")
    private val HR = Regex("^(-{3,}|\\*{3,}|_{3,})$")
    private val CODE_FENCE = Regex("^```")
    // Markdown image: ![alt](path) — alt может быть пустым, path может содержать любой символ кроме ")"
    private val IMAGE = Regex("^!\\[[^\\]]*]\\(([^)]+)\\)\\s*$")

    fun parse(text: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = text.lines()
        var inCodeBlock = false
        val codeBuffer = StringBuilder()

        for (line in lines) {
            if (CODE_FENCE.matches(line.trim())) {
                if (inCodeBlock) {
                    blocks.add(MarkdownBlock(BlockType.CODE_BLOCK, codeBuffer.toString()))
                    codeBuffer.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }
            if (inCodeBlock) {
                if (codeBuffer.isNotEmpty()) codeBuffer.append("\n")
                codeBuffer.append(line)
                continue
            }
            when {
                HR.matches(line) -> blocks.add(MarkdownBlock(BlockType.HR, ""))
                IMAGE.matches(line) -> {
                    val path = IMAGE.find(line)!!.groupValues[1]
                    blocks.add(MarkdownBlock(BlockType.IMAGE, "", imagePath = path))
                }
                CHECKBOX_CHECKED.matches(line) -> {
                    blocks.add(MarkdownBlock(BlockType.CHECKBOX, CHECKBOX_CHECKED.find(line)!!.groupValues[1], checked = true, originalLine = line))
                }
                CHECKBOX_UNCHECKED.matches(line) -> {
                    blocks.add(MarkdownBlock(BlockType.CHECKBOX, CHECKBOX_UNCHECKED.find(line)!!.groupValues[1], checked = false, originalLine = line))
                }
                HEADING.matches(line) -> {
                    val m = HEADING.find(line)!!
                    blocks.add(MarkdownBlock(BlockType.HEADING, m.groupValues[2], level = m.groupValues[1].length))
                }
                QUOTE.matches(line) -> {
                    blocks.add(MarkdownBlock(BlockType.QUOTE, QUOTE.find(line)!!.groupValues[1]))
                }
                UNORDERED.matches(line) -> {
                    blocks.add(MarkdownBlock(BlockType.LIST_ITEM, UNORDERED.find(line)!!.groupValues[1]))
                }
                ORDERED.matches(line) -> {
                    blocks.add(MarkdownBlock(BlockType.ORDERED_ITEM, ORDERED.find(line)!!.groupValues[1]))
                }
                line.isBlank() -> { }
                else -> blocks.add(MarkdownBlock(BlockType.PARAGRAPH, line))
            }
        }
        if (inCodeBlock && codeBuffer.isNotEmpty()) {
            blocks.add(MarkdownBlock(BlockType.CODE_BLOCK, codeBuffer.toString()))
        }
        return blocks
    }
}

private val INLINE_LINK = Regex("\\[\\[(.+?)]]")
private val INLINE_BOLD = Regex("\\*\\*(.+?)\\*\\*")
private val INLINE_ITALIC = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")
private val INLINE_CODE = Regex("`(.+?)`")

fun renderInline(text: String, linkColor: Color = Color(0xFFBB86FC)): AnnotatedString = buildAnnotatedString {
    var i = 0
    val s = text
    while (i < s.length) {
        val linkMatch = INLINE_LINK.find(s, i)
        val boldMatch = INLINE_BOLD.find(s, i)
        val codeMatch = INLINE_CODE.find(s, i)
        val italicMatch = INLINE_ITALIC.find(s, i)

        val nextMatch = listOfNotNull(linkMatch, boldMatch, codeMatch, italicMatch).minByOrNull { it.range.first }

        if (nextMatch == null) {
            append(s.substring(i))
            break
        }
        if (nextMatch.range.first > i) {
            append(s.substring(i, nextMatch.range.first))
        }
        when (nextMatch) {
            linkMatch -> withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) {
                append(nextMatch.groupValues[1])
            }
            boldMatch -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(nextMatch.groupValues[1])
            }
            codeMatch -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF2A2A2A))) {
                append(" ${nextMatch.groupValues[1]} ")
            }
            italicMatch -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(nextMatch.groupValues[1])
            }
        }
        i = nextMatch.range.last + 1
    }
}

@Composable
fun MarkdownPreview(
    content: String,
    onCheckboxToggle: (originalLine: String, checked: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val blocks = MarkdownParser.parse(content)
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block.type) {
                BlockType.HEADING -> {
                    val size = when (block.level) {
                        1 -> 26.sp; 2 -> 22.sp; 3 -> 20.sp; else -> 18.sp
                    }
                    Text(
                        text = renderInline(block.text),
                        color = Color.White,
                        fontSize = size,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                BlockType.PARAGRAPH -> {
                    Text(
                        text = renderInline(block.text),
                        color = Color(0xFFEEEEEE),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                BlockType.LIST_ITEM -> {
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("•  ", color = Color(0xFFBB86FC), fontSize = 16.sp)
                        Text(text = renderInline(block.text), color = Color(0xFFEEEEEE), fontSize = 16.sp)
                    }
                }
                BlockType.ORDERED_ITEM -> {
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("${index}.  ", color = Color(0xFFBB86FC), fontSize = 16.sp)
                        Text(text = renderInline(block.text), color = Color(0xFFEEEEEE), fontSize = 16.sp)
                    }
                }
                BlockType.QUOTE -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color(0xFF2A2A2A), RoundedCornerShape(4.dp))
                            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 8.dp)
                    ) {
                        Box(modifier = Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(Color(0xFFBB86FC)))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = renderInline(block.text), color = Color(0xFFCCCCCC), fontSize = 15.sp, fontStyle = FontStyle.Italic)
                    }
                }
                BlockType.CODE_BLOCK -> {
                    Text(
                        text = block.text,
                        color = Color(0xFF43A047),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color(0xFF1E1E1E), RoundedCornerShape(6.dp))
                            .padding(12.dp)
                    )
                }
                BlockType.HR -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(vertical = 8.dp)
                            .background(Color(0xFF333333))
                    )
                }
                BlockType.IMAGE -> {
                    // Загружаем изображение из filesDir/attachments/<noteId>/<file>
                    val context = LocalContext.current
                    val imageFile = remember(block.imagePath) {
                        try {
                            // Путь вида "attachments/<noteId>/<file>.png"
                            if (block.imagePath.startsWith("attachments/")) {
                                val relPath = block.imagePath.removePrefix("attachments/")
                                java.io.File(context.filesDir, "attachments/$relPath")
                            } else if (block.imagePath.startsWith("file://")) {
                                java.io.File(android.net.Uri.parse(block.imagePath).path ?: "")
                            } else if (block.imagePath.startsWith("content://")) {
                                null // Content URI — нужна отдельная загрузка через ContentResolver
                            } else {
                                java.io.File(block.imagePath)
                            }
                        } catch (_: Exception) { null }
                    }
                    val bitmap = remember(imageFile) {
                        if (imageFile != null && imageFile.exists()) {
                            try {
                                android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
                            } catch (_: Exception) { null }
                        } else null
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    } else {
                        // Если не удалось загрузить — показываем путь
                        Text(
                            text = "🖼 ${block.imagePath}",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        )
                    }
                }
                BlockType.CHECKBOX -> {
                    val isChecked = block.checked == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCheckboxToggle(block.originalLine, !isChecked) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    if (isChecked) Color(0xFF43A047) else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                )
                                .border(2.dp, if (isChecked) Color(0xFF43A047) else Color(0xFF888888), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChecked) Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = renderInline(block.text),
                            color = if (isChecked) Color(0xFF888888) else Color(0xFFEEEEEE),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
