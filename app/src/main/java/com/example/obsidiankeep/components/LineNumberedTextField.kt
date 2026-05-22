package com.example.obsidiankeep.components // Проверьте ваш package!

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun LineNumberedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var lineCountState by remember { mutableIntStateOf(1) }

    // Считаем количество строк
    val lineCount = value.count { it == '\n' } + 1

    // Автоскролл: активируется, если строк больше 15
    LaunchedEffect(value, scrollState.maxValue) {
        if (lineCount > 13) {
            delay(30) // Микропауза для плавной отработки движка Compose
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val linesText = remember(lineCountState, value) {
        val totalLines = maxOf(lineCountState, 1)
        (1..totalLines).joinToString("\n")
    }

    Row(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .verticalScroll(scrollState)
    ) {
        // Панель номеров строк (добавляем ей нижний отступ, если текст длинный)
        Text(
            text = linesText,
            style = TextStyle(
                color = Color(0xFF555555),
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 24.sp
            ),
            modifier = Modifier
                .width(44.dp)
                .padding(
                    top = 12.dp,
                    bottom = if (lineCount > 13) 340.dp else 12.dp // Резервируем место под клавиатуру для цифр
                ),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        // Разделительный нативный бордюр
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color(0xFF333333))
        )

        // Текстовое поле ввода
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            onTextLayout = { textLayoutResult ->
                if (lineCountState != textLayoutResult.lineCount) {
                    lineCountState = textLayoutResult.lineCount
                }
            },
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 24.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 12.dp,
                    start = 12.dp,
                    end = 12.dp,
                    // САМЫЙ ВАЖНЫЙ ФИКС: если строк > 15, создаем искусственное пустое поле снизу в 340dp.
                    // Клавиатура будет перекрывать этот пустой отступ, а весь текст останется НАД ней!
                    bottom = if (lineCount > 13) 340.dp else 12.dp
                ),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = "Начните писать... [[Ссылка]] свяжет заметки.",
                        color = Color(0xFF666666),
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                innerTextField()
            }
        )
    }
}
