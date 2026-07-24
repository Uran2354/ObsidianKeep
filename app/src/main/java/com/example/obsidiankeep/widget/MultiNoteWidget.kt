package com.example.obsidiankeep.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.obsidiankeep.MainActivity
import com.example.obsidiankeep.R
import com.example.obsidiankeep.data.NoteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Базовый виджет заметок. 3 размера через наследование:
 *  - NoteWidgetSmall  (2x2) — 3 заметки
 *  - NoteWidgetWide   (4x2) — 5 заметок
 *  - NoteWidgetLarge  (4x4) — 8 заметок
 *
 * ВАЖНО: НЕ используем GlanceTheme (может падать на некоторых устройствах без темы).
 * НЕ используем SizeMode (по умолчанию Single — это и нужно).
 * НЕ используем remember/derivedStateOf — provideGlance это suspend функция, не Composable.
 */
abstract class BaseNoteWidget : GlanceAppWidget() {

    abstract val maxNotes: Int

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        android.util.Log.i("BaseNoteWidget", "provideGlance start, maxNotes=$maxNotes")

        // Загружаем заметки напрямую из БД — без Hilt, без декораторов.
        val notes: List<com.example.obsidiankeep.data.Note> = try {
            val all = NoteDatabase.getDatabase(context).noteDao().getAllNotesOnce()
                .filter { it.deletedAt == null && !it.isEncrypted }
                // Скрываем заметки из decoy-папки "0"
                .filter { it.folderId != "0" }
                .sortedByDescending { it.updatedAt }
            android.util.Log.i("BaseNoteWidget", "Loaded ${all.size} notes")
            all
        } catch (e: Exception) {
            android.util.Log.e("BaseNoteWidget", "Failed to load notes", e)
            emptyList()
        }

        val untitled = context.getString(R.string.editor_no_title)
        val noNotes = context.getString(R.string.widget_no_notes)
        val newNote = context.getString(R.string.widget_new_note)
        val notesToDisplay = notes.take(maxNotes)

        android.util.Log.i("BaseNoteWidget", "Calling provideContent with ${notesToDisplay.size} notes")

        provideContent {
            // НЕ используем GlanceTheme — рисуем напрямую
            Column(
                horizontalAlignment = Alignment.Horizontal.Start,
                verticalAlignment = Alignment.Vertical.Top,
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))
                    .padding(12.dp)
            ) {
                // Заголовок
                Text(
                    text = "ObsidianKeep",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFBB86FC)),
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(8.dp))

                // Список заметок (или "Нет заметок")
                if (notesToDisplay.isEmpty()) {
                    Text(
                        text = noNotes,
                        style = TextStyle(color = ColorProvider(Color(0xFF888888)))
                    )
                } else {
                    notesToDisplay.forEach { note ->
                        Text(
                            text = "• ${note.title.ifEmpty { untitled }}",
                            style = TextStyle(color = ColorProvider(Color.White)),
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable(actionStartActivity<MainActivity>())
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(8.dp))
                // Кнопка "+ Новая заметка"
                Text(
                    text = newNote,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFBB86FC)),
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(actionStartActivity<MainActivity>())
                )
            }
        }

        android.util.Log.i("BaseNoteWidget", "provideGlance end")
    }
}

class NoteWidgetSmall : BaseNoteWidget() {
    override val maxNotes = 3
}
class NoteWidgetWide : BaseNoteWidget() {
    override val maxNotes = 5
}
class NoteWidgetLarge : BaseNoteWidget() {
    override val maxNotes = 8
}

class NoteWidgetReceiverSmall : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NoteWidgetSmall()
}
class NoteWidgetReceiverWide : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NoteWidgetWide()
}
class NoteWidgetReceiverLarge : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NoteWidgetLarge()
}

/**
 * Хранилище настроек виджета: какая папка фильтруется.
 */
object WidgetPrefs {
    private const val PREFS = "widget_prefs"
    private const val KEY_PREFIX = "folder_for_widget_"

    fun getFolderFilter(context: Context, id: GlanceId): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PREFIX + id.toString(), null)
    }

    fun setFolderFilter(context: Context, id: GlanceId, folderId: String?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = KEY_PREFIX + id.toString()
        prefs.edit().apply {
            if (folderId == null) remove(key) else putString(key, folderId)
        }.apply()
    }
}

/**
 * Принудительно обновить все виджеты приложения.
 * Вызывается после сохранения/удаления заметки.
 *
 * ВАЖНО: Glance не обновляет виджеты автоматически при изменении данных в БД —
 * нужно явно вызвать updateAll() у каждого виджета.
 */
fun refreshAllWidgets(context: Context) {
    try {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                android.util.Log.i("refreshAllWidgets", "Updating all widgets...")
                NoteWidgetSmall().updateAll(context)
                NoteWidgetWide().updateAll(context)
                NoteWidgetLarge().updateAll(context)
                android.util.Log.i("refreshAllWidgets", "All widgets updated")
            } catch (e: Exception) {
                android.util.Log.e("refreshAllWidgets", "Failed to update widgets", e)
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("refreshAllWidgets", "Failed to launch", e)
    }
}
