package com.example.obsidiankeep.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.obsidiankeep.data.Note
import com.example.obsidiankeep.data.RepeatRule

object ReminderScheduler {

    const val ACTION_SNOOZE = "com.example.obsidiankeep.SNOOZE"
    /** Кастомный action для будильника напоминания. Не используем системный ACTION_RUN. */
    const val ACTION_REMINDER_FIRE = "com.example.obsidiankeep.REMINDER_FIRE"
    const val EXTRA_NOTE_ID = "extra_note_id"
    const val EXTRA_NOTE_TITLE = "extra_note_title"
    const val EXTRA_NOTE_CONTENT = "extra_note_content"
    const val EXTRA_REPEAT = "extra_repeat"

    fun schedule(context: Context, noteId: String, title: String, triggerAtMillis: Long, repeatRule: String? = null) {
        val triggerInSec = (triggerAtMillis - System.currentTimeMillis()) / 1000
        Log.i("ReminderScheduler", "schedule: noteId=$noteId, title=$title, trigger in ${triggerInSec}s, rule=$repeatRule")
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = buildPendingIntent(context, noteId, title, "", repeatRule)
            // На Android 12+ (S) точные будильники требуют canScheduleExactAlarms().
            // Если разрешения нет — падаем на setAndAllowWhileIdle (не exact, но будит устройство).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                Log.w("ReminderScheduler", "canScheduleExactAlarms()=false, using inexact alarm")
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
            Log.i("ReminderScheduler", "Alarm scheduled successfully")
        } catch (e: SecurityException) {
            Log.e("ReminderScheduler", "SecurityException, trying inexact fallback", e)
            try {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pi = buildPendingIntent(context, noteId, title, "", repeatRule)
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } catch (e2: Exception) {
                Log.e("ReminderScheduler", "Inexact fallback also failed", e2)
            }
        } catch (e: Exception) {
            Log.e("ReminderScheduler", "schedule failed", e)
        }
    }

    fun scheduleSnooze(context: Context, noteId: String, title: String, content: String, minutes: Int = 15) {
        val trigger = System.currentTimeMillis() + minutes * 60_000L
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = buildSnoozePendingIntent(context, noteId, title, content)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: Exception) {
            Log.e("ReminderScheduler", "scheduleSnooze failed", e)
        }
    }

    fun cancel(context: Context, noteId: String) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(buildPendingIntent(context, noteId, "", "", null))
            am.cancel(buildSnoozePendingIntent(context, noteId, "", ""))
        } catch (e: Exception) {
            Log.e("ReminderScheduler", "cancel failed", e)
        }
    }

    fun rescheduleAll(context: Context, notes: List<Note>) {
        val now = System.currentTimeMillis()
        notes.forEach { note ->
            val trigger = note.reminderAt ?: return@forEach
            if (trigger > now) {
                schedule(context, note.id, note.title, trigger, note.repeatRule)
            }
        }
    }

    private fun buildPendingIntent(context: Context, noteId: String, title: String, content: String, repeat: String?): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER_FIRE
            putExtra(EXTRA_NOTE_ID, noteId)
            putExtra(EXTRA_NOTE_TITLE, title)
            putExtra(EXTRA_NOTE_CONTENT, content)
            repeat?.let { putExtra(EXTRA_REPEAT, it) }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, noteId.hashCode(), intent, flags)
    }

    private fun buildSnoozePendingIntent(context: Context, noteId: String, title: String, content: String): PendingIntent {
        val intent = Intent(context, SnoozeReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_NOTE_ID, noteId)
            putExtra(EXTRA_NOTE_TITLE, title)
            putExtra(EXTRA_NOTE_CONTENT, content)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, ("snooze_$noteId").hashCode(), intent, flags)
    }
}
