package com.example.obsidiankeep.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.obsidiankeep.data.NoteDatabase
import com.example.obsidiankeep.data.RepeatRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val tag = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(tag, "onReceive: action=${intent.action}")
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON" &&
            intent.action != "android.intent.action.MY_PACKAGE_REPLACED"
        ) return

        // Создаём канал уведомлений на старте устройства
        ReminderNotifier.createChannel(context)

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val notes = NoteDatabase.getDatabase(context).noteDao().getNotesWithReminders()
                Log.i(tag, "Found ${notes.size} notes with reminders, rescheduling...")
                val now = System.currentTimeMillis()
                var scheduled = 0
                var shownImmediate = 0
                notes.forEach { note ->
                    val trigger = note.reminderAt ?: return@forEach
                    if (trigger > now) {
                        ReminderScheduler.schedule(context, note.id, note.title, trigger, note.repeatRule)
                        scheduled++
                    } else if (note.repeatRule != null && note.repeatRule != RepeatRule.NONE) {
                        val next = RepeatRule.nextTrigger(trigger, note.repeatRule)
                        if (next != null && next > now) {
                            ReminderScheduler.schedule(context, note.id, note.title, next, note.repeatRule)
                            NoteDatabase.getDatabase(context).noteDao()
                                .insertNoteRaw(note.copy(reminderAt = next))
                            scheduled++
                        }
                    } else {
                        val body = note.content.take(500).ifEmpty { null }
                        ReminderNotifier.show(context, note.id, note.title, body, note.repeatRule)
                        ReminderScheduler.cancel(context, note.id)
                        shownImmediate++
                    }
                }
                Log.i(tag, "Done: scheduled=$scheduled, shownImmediate=$shownImmediate")
            } catch (e: Exception) {
                Log.e(tag, "Error rescheduling", e)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }
}
