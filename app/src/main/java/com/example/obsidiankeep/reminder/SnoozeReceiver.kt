package com.example.obsidiankeep.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.obsidiankeep.R

class SnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_ID) ?: return
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_TITLE) ?: context.getString(R.string.reminder_default_title)
        val content = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_CONTENT) ?: ""

        ReminderScheduler.scheduleSnooze(context, noteId, title, content, minutes = 15)

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        mgr.cancel(noteId.hashCode())
    }
}
