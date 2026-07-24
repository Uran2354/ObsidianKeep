package com.example.obsidiankeep.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.obsidiankeep.MainActivity
import com.example.obsidiankeep.R

object ReminderNotifier {

    const val CHANNEL_ID = "obsidian_keep_reminders"
    const val GROUP_KEY = "obsidian_keep_reminders_group"
    private const val EXTRA_OPEN_NOTE_ID = "extra_open_note_id"
    private val tag = "ReminderNotifier"

    fun createChannel(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = android.app.NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.reminder_channel_desc)
                    enableVibration(true)
                    enableLights(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(channel)
                Log.i(tag, "Notification channel created")
            }
        } catch (e: Exception) {
            Log.e(tag, "createChannel failed", e)
        }
    }

    fun show(context: Context, noteId: String, title: String, body: String?, repeatRule: String? = null) {
        try {
            // Defensive: канал мог быть удалён пользователем в настройках приложения
            createChannel(context)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_NOTE_ID, noteId)
                action = Intent.ACTION_VIEW
            }
            val openPi = PendingIntent.getActivity(
                context, noteId.hashCode(), openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snoozeIntent = Intent(context, SnoozeReceiver::class.java).apply {
                action = ReminderScheduler.ACTION_SNOOZE
                putExtra(ReminderScheduler.EXTRA_NOTE_ID, noteId)
                putExtra(ReminderScheduler.EXTRA_NOTE_TITLE, title)
                putExtra(ReminderScheduler.EXTRA_NOTE_CONTENT, body ?: "")
            }
            val snoozePi = PendingIntent.getBroadcast(
                context, ("snooze_$noteId").hashCode(), snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val displayTitle = title.ifEmpty { context.getString(R.string.reminder_default_title) }
            val bigText = if (body.isNullOrBlank()) displayTitle else body
            val repeatSuffix = if (repeatRule != null && repeatRule != "none") " 🔁" else ""

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🔔 $displayTitle$repeatSuffix")
                .setContentText(bigText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(openPi)
                .addAction(0, context.getString(R.string.reminder_snooze), snoozePi)
                .setGroup(GROUP_KEY)
                .build()

            val manager = NotificationManagerCompat.from(context)
            // areNotificationsEnabled проверяет и канал, и общее разрешение
            if (!manager.areNotificationsEnabled()) {
                Log.w(tag, "Notifications are disabled for the app — cannot show reminder")
                return
            }
            manager.notify(noteId.hashCode(), notification)
            Log.i(tag, "Notification shown for noteId=$noteId, title=$displayTitle")
            showSummaryIfNeeded(context, manager)
        } catch (e: Exception) {
            Log.e(tag, "show failed", e)
        }
    }

    private fun showSummaryIfNeeded(context: Context, manager: NotificationManagerCompat) {
        try {
            val summary = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(R.string.notif_group_title))
                .setStyle(NotificationCompat.InboxStyle().setSummaryText("ObsidianKeep"))
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()
            manager.notify(0, summary)
        } catch (e: Exception) {
            Log.e(tag, "showSummaryIfNeeded failed", e)
        }
    }
}
