package com.example.obsidiankeep.ui.theme

import androidx.compose.ui.graphics.Color

object NoteColors {

    const val DEFAULT = 0xFF2C2C2C.toInt()

    val palette: List<Int> = listOf(
        DEFAULT,
        0xFFE53935.toInt(),
        0xFF43A047.toInt(),
        0xFF1E88E5.toInt(),
        0xFF8E24AA.toInt(),
        0xFFFB8C00.toInt(),
        0xFF00897B.toInt(),
        0xFFD81B60.toInt()
    )

    fun neonBorder(cardColor: Int, selected: Boolean): Color {
        if (!selected) return Color.Transparent
        return try {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(cardColor, hsv)
            hsv[1] = 0.95f
            hsv[2] = 1.0f
            Color(android.graphics.Color.HSVToColor(hsv))
        } catch (_: Exception) {
            Color(0xFFBB86FC)
        }
    }
}
