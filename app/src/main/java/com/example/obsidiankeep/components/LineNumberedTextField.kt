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

@Composable
fun LineNumberedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // СВЕРХБЫСТРЫЙ И БЕЗОПАСНЫЙ РАСЧЕТ СТРОК: Считаем физические строки без перегрузки видеочипа
    val linesText = remember(value) {
        val lines = value.split("\n")
        val totalLines = maxOf(lines.size, 1)
        (1..totalLines).joinToString("\n")
    }

    Row(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .verticalScroll(scrollState)
    ) {
        // Панель номеров строк
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
                .padding(top = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        // Разделительная линия
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
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 24.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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