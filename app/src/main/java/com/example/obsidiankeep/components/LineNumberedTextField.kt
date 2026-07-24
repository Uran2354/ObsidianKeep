package com.example.obsidiankeep.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.obsidiankeep.R
import kotlinx.coroutines.delay

private val BackgroundModifier = Modifier.fillMaxSize()
private val LineNumbersStyle = TextStyle(color = Color(0x66FFFFFF), fontSize = 16.sp, fontFamily = FontFamily.Monospace, lineHeight = 24.sp)
private val InputTextStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace, lineHeight = 24.sp)
private val PlaceholderStyle = TextStyle(color = Color(0xFF888888), fontSize = 16.sp, fontFamily = FontFamily.Monospace)
private val WhiteBrush = SolidColor(Color.White)

// Цвета подсветки синтаксиса
private val HeadingColor = Color(0xFFBB86FC)        // фиолетовый для # заголовков
private val LinkColor = Color(0xFF4FC3F7)            // голубой для [[ссылок]]
private val BoldColor = Color(0xFFFFD54F)            // жёлтый для **жирного**
private val ItalicColor = Color(0xFFCE93D8)          // розовый для *курсива*
private val CodeColor = Color(0xFF43A047)            // зелёный для `кода`
private val TagColor = Color(0xFF00BCD4)             // бирюзовый для #тегов
private val CheckboxColor = Color(0xFFFFB74D)        // оранжевый для [ ] / [x]
private val QuoteColor = Color(0xFFB39DDB)           // светло-фиолетовый для > цитат
private val ListBulletColor = Color(0xFF80CBC4)      // светло-бирюзовый для - /*
private val HrColor = Color(0xFF888888)              // серый для ---

/**
 * Возвращает AnnotatedString с подсветкой синтаксиса Markdown.
 * Применяется построчно — для каждой строки определяем тип и красим соответствующие токены.
 */
fun highlightMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.split("\n")
    lines.forEachIndexed { lineIdx, line ->
        if (lineIdx > 0) append("\n")
        highlightLine(line, this)
    }
}

private fun highlightLine(line: String, builder: AnnotatedString.Builder) {
    // 1. Горизонтальная линия: --- или *** или ___
    if (line.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$"))) {
        builder.withStyle(SpanStyle(color = HrColor, fontWeight = FontWeight.Bold)) {
            append(line)
        }
        return
    }

    // 2. Заголовки: # ## ### ...
    val headingMatch = Regex("^(#{1,6})\\s+(.+)$").matchEntire(line)
    if (headingMatch != null) {
        val hashes = headingMatch.groupValues[1]
        val content = headingMatch.groupValues[2]
        builder.withStyle(SpanStyle(color = HeadingColor, fontWeight = FontWeight.Bold)) {
            append(hashes)
        }
        builder.append(" ")
        appendInlineWithHighlight(content, builder, baseStyle = SpanStyle(color = HeadingColor, fontWeight = FontWeight.Bold))
        return
    }

    // 3. Чек-листы: - [ ] task / - [x] task
    val checkboxMatch = Regex("^([-*])\\s+(\\[[ xX]])\\s+(.+)$").matchEntire(line)
    if (checkboxMatch != null) {
        val bullet = checkboxMatch.groupValues[1]
        val box = checkboxMatch.groupValues[2]
        val rest = checkboxMatch.groupValues[3]
        builder.withStyle(SpanStyle(color = ListBulletColor)) { append(bullet) }
        builder.append(" ")
        builder.withStyle(SpanStyle(color = CheckboxColor, fontWeight = FontWeight.Bold)) { append(box) }
        builder.append(" ")
        appendInlineWithHighlight(rest, builder)
        return
    }

    // 4. Цитаты: > text
    if (line.startsWith(">")) {
        builder.withStyle(SpanStyle(color = QuoteColor)) { append(">") }
        val rest = line.removePrefix(">")
        appendInlineWithHighlight(rest, builder, baseStyle = SpanStyle(color = QuoteColor, fontStyle = FontStyle.Italic))
        return
    }

    // 5. Списки: - item / * item / 1. item
    val listMatch = Regex("^(\\s*)([-*]|\\d+\\.)\\s+(.+)$").matchEntire(line)
    if (listMatch != null) {
        val indent = listMatch.groupValues[1]
        val bullet = listMatch.groupValues[2]
        val rest = listMatch.groupValues[3]
        builder.append(indent)
        builder.withStyle(SpanStyle(color = ListBulletColor, fontWeight = FontWeight.Bold)) { append(bullet) }
        builder.append(" ")
        appendInlineWithHighlight(rest, builder)
        return
    }

    // 6. Обычный параграф
    appendInlineWithHighlight(line, builder)
}

/**
 * Применяет инлайн-подсветку: [[ссылки]], **жирный**, *курсив*, `код`, #теги.
 * Красит только markdown-разметку, остальной текст остаётся белым.
 */
private fun appendInlineWithHighlight(
    text: String,
    builder: AnnotatedString.Builder,
    baseStyle: SpanStyle = SpanStyle(color = Color.White)
) {
    val linkRegex = Regex("\\[\\[(.+?)]]")
    val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
    val italicRegex = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")
    val codeRegex = Regex("`(.+?)`")
    val tagRegex = Regex("(?<![\\w#])#([\\wа-яА-ЯёЁ]+)")

    data class MatchInfo(val start: Int, val end: Int, val kind: Int)

    val allMatches = mutableListOf<MatchInfo>()
    linkRegex.findAll(text).forEach { allMatches.add(MatchInfo(it.range.first, it.range.last + 1, 0)) }
    boldRegex.findAll(text).forEach { allMatches.add(MatchInfo(it.range.first, it.range.last + 1, 1)) }
    italicRegex.findAll(text).forEach { allMatches.add(MatchInfo(it.range.first, it.range.last + 1, 2)) }
    codeRegex.findAll(text).forEach { allMatches.add(MatchInfo(it.range.first, it.range.last + 1, 3)) }
    tagRegex.findAll(text).forEach { allMatches.add(MatchInfo(it.range.first, it.range.last + 1, 4)) }

    allMatches.sortBy { it.start }

    // Убираем перекрытия — берём только первый вложенный
    val filtered = mutableListOf<MatchInfo>()
    var lastEnd = 0
    for (m in allMatches) {
        if (m.start >= lastEnd) {
            filtered.add(m)
            lastEnd = m.end
        }
    }

    var i = 0
    for (m in filtered) {
        if (m.start > i) {
            builder.withStyle(baseStyle) { append(text.substring(i, m.start)) }
        }
        val chunk = text.substring(m.start, m.end)
        val style = when (m.kind) {
            0 -> SpanStyle(color = LinkColor, fontWeight = FontWeight.Medium)
            1 -> SpanStyle(color = BoldColor, fontWeight = FontWeight.Bold)
            2 -> SpanStyle(color = ItalicColor, fontStyle = FontStyle.Italic)
            3 -> SpanStyle(color = CodeColor, fontFamily = FontFamily.Monospace)
            4 -> SpanStyle(color = TagColor, fontWeight = FontWeight.Bold)
            else -> baseStyle
        }
        builder.withStyle(style) { append(chunk) }
        i = m.end
    }
    if (i < text.length) {
        builder.withStyle(baseStyle) { append(text.substring(i)) }
    }
}

/**
 * VisualTransformation, который применяет подсветку Markdown к тексту в BasicTextField.
 * ВАЖНО: смещения (offsetMapping) идентичны — мы не добавляем и не удаляем символы,
 * только меняем визуальный стиль.
 */
class MarkdownHighlightTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        return androidx.compose.ui.text.input.TransformedText(
            highlightMarkdown(text.text),
            androidx.compose.ui.text.input.OffsetMapping.Identity
        )
    }
}

@Composable
fun LineNumberedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    syntaxHighlight: Boolean = true,
    onCursorChange: ((Int) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    var lineCountState by remember { mutableIntStateOf(1) }
    val placeholderText = stringResource(R.string.editor_placeholder)

    var textFieldValueState by remember {
        mutableStateOf(TextFieldValue(text = value, selection = androidx.compose.ui.text.TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (value != textFieldValueState.text) {
            textFieldValueState = textFieldValueState.copy(text = value)
            onCursorChange?.invoke(value.length)
        }
    }

    val logicalLineCount = remember(textFieldValueState.text) { textFieldValueState.text.count { it == '\n' } + 1 }

    val dynamicBottomPadding = remember(logicalLineCount) {
        val calculated = logicalLineCount * 24
        if (calculated > 320) 320.dp else calculated.dp
    }

    var hasInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(textFieldValueState.text) {
        if (!hasInitialized) {
            scrollState.scrollTo(0)
            hasInitialized = true
            return@LaunchedEffect
        }
        val cursorPosition = textFieldValueState.selection.end
        val textLength = textFieldValueState.text.length
        if (logicalLineCount > 1 && cursorPosition >= textLength) {
            delay(40)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Уведомляем о позиции курсора при каждом изменении
    LaunchedEffect(textFieldValueState.selection) {
        onCursorChange?.invoke(textFieldValueState.selection.end)
    }

    val linesText = remember(lineCountState) {
        val totalLines = maxOf(lineCountState, 1)
        (1..totalLines).joinToString("\n")
    }

    val highlightTransformation = remember(syntaxHighlight) {
        if (syntaxHighlight) MarkdownHighlightTransformation() else VisualTransformation.None
    }

    Row(
        modifier = modifier
            .then(BackgroundModifier)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = linesText,
            style = LineNumbersStyle,
            modifier = Modifier.width(44.dp).padding(top = 12.dp, bottom = dynamicBottomPadding),
            textAlign = TextAlign.Center
        )

        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color(0x33FFFFFF)))

        BasicTextField(
            value = textFieldValueState,
            onValueChange = { newTextFieldValue ->
                textFieldValueState = newTextFieldValue
                if (value != newTextFieldValue.text) {
                    onValueChange(newTextFieldValue.text)
                }
                onCursorChange?.invoke(newTextFieldValue.selection.end)
            },
            cursorBrush = WhiteBrush,
            onTextLayout = { textLayoutResult ->
                if (lineCountState != textLayoutResult.lineCount) {
                    lineCountState = textLayoutResult.lineCount
                }
            },
            textStyle = InputTextStyle,
            visualTransformation = highlightTransformation,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp, bottom = dynamicBottomPadding),
            decorationBox = { innerTextField ->
                if (textFieldValueState.text.isEmpty()) {
                    Text(text = placeholderText, style = PlaceholderStyle)
                }
                innerTextField()
            }
        )
    }
}
