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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Выносим стили в статические константы, чтобы не создавать объекты при каждой рекомпозиции
private val BackgroundModifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E))
private val LineNumbersStyle = TextStyle(color = Color(0xFF555555), fontSize = 16.sp, fontFamily = FontFamily.Monospace, lineHeight = 24.sp)
private val InputTextStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace, lineHeight = 24.sp)
private val PlaceholderStyle = TextStyle(color = Color(0xFF666666), fontSize = 16.sp, fontFamily = FontFamily.Monospace)
private val WhiteBrush = SolidColor(Color.White)

@Composable
fun LineNumberedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var lineCountState by remember { mutableIntStateOf(1) }

    var textFieldValueState by remember {
        mutableStateOf(TextFieldValue(text = value, selection = androidx.compose.ui.text.TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (value != textFieldValueState.text) {
            textFieldValueState = textFieldValueState.copy(text = value)
        }
    }

    val lineCount = remember(textFieldValueState.text) { textFieldValueState.text.count { it == '\n' } + 1 }

    val dynamicBottomPadding = remember(lineCount) {
        val calculatedPadding = lineCount * 24
        if (calculatedPadding > 320) 320.dp else calculatedPadding.dp
    }

    LaunchedEffect(textFieldValueState.text) {
        val cursorPosition = textFieldValueState.selection.end
        val textLength = textFieldValueState.text.length
        if (lineCount > 1 && cursorPosition >= textLength) {
            delay(40)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val linesText = remember(lineCountState) {
        val totalLines = maxOf(lineCountState, 1)
        (1..totalLines).joinToString("\n")
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

        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color(0xFF333333)))

        BasicTextField(
            value = textFieldValueState,
            onValueChange = { newTextFieldValue ->
                textFieldValueState = newTextFieldValue
                if (value != newTextFieldValue.text) {
                    onValueChange(newTextFieldValue.text)
                }
            },
            cursorBrush = WhiteBrush,
            onTextLayout = { textLayoutResult ->
                if (lineCountState != textLayoutResult.lineCount) {
                    lineCountState = textLayoutResult.lineCount
                }
            },
            textStyle = InputTextStyle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp, bottom = dynamicBottomPadding),
            decorationBox = { innerTextField ->
                if (textFieldValueState.text.isEmpty()) {
                    Text(text = "Начните писать... [[Ссылка]] свяжет заметки.", style = PlaceholderStyle)
                }
                innerTextField()
            }
        )
    }
}
