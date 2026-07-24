package com.example.obsidiankeep.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.obsidiankeep.R
import com.example.obsidiankeep.data.NoteDatabase
import com.example.obsidiankeep.data.RepeatRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    private val tag = "ReminderAlarmReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(tag, "onReceive: action=${intent.action}, extras=${intent.extras}")
        val noteId = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_ID)
        if (noteId == null) {
            Log.e(tag, "No noteId extra — ignoring")
            return
        }
        val fallbackTitle = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_TITLE)
            ?: context.getString(R.string.reminder_default_title)
        val repeatRule = intent.getStringExtra(ReminderScheduler.EXTRA_REPEAT)
        Log.i(tag, "noteId=$noteId, title=$fallbackTitle, repeatRule=$repeatRule")

        // Проверяем разрешение на уведомления (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.e(tag, "POST_NOTIFICATIONS not granted — cannot show notification")
                // Всё равно продолжаем, т.к. notify() не бросает исключение, но уведомление может не появиться
            }
        }

        // Канал уведомлений — создаём defensively, если ещё нет
        ReminderNotifier.createChannel(context)

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val note = NoteDatabase.getDatabase(context).noteDao().getNoteByIdOnce(noteId)
                Log.i(tag, "Loaded note from DB: ${if (note == null) "null" else note.title}")
                if (note != null) {
                    val body = if (note.isEncrypted) null else note.content.take(500).ifEmpty { null }
                    Log.i(tag, "Showing notification: title=${note.title}, bodyLen=${body?.length ?: 0}")
                    ReminderNotifier.show(context, noteId, note.title, body, note.repeatRule)

                    // Если повторяющееся — перепланируем на следующий триггер
                    if (note.reminderAt != null && note.repeatRule != null && note.repeatRule != RepeatRule.NONE) {
                        val next = RepeatRule.nextTrigger(note.reminderAt, note.repeatRule)
                        Log.i(tag, "Repeat rule=$repeatRule, next trigger=$next, now=${System.currentTimeMillis()}")
                        if (next != null && next > System.currentTimeMillis()) {
                            ReminderScheduler.schedule(context, note.id, note.title, next, note.repeatRule)
                            NoteDatabase.getDatabase(context).noteDao().insertNoteRaw(note.copy(reminderAt = next))
                            Log.i(tag, "Rescheduled next occurrence")
                        }
                    }
                } else {
                    Log.w(tag, "Note not found in DB — showing fallback notification")
                    ReminderNotifier.show(context, noteId, fallbackTitle, null, null)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error in onReceive", e)
                try {
                    ReminderNotifier.show(context, noteId, fallbackTitle, null, null)
                } catch (e2: Exception) {
                    Log.e(tag, "Fallback notification also failed", e2)
                }
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }
}
