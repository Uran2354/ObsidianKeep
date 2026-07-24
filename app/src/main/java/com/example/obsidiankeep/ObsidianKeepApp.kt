package com.example.obsidiankeep

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.obsidiankeep.reminder.ReminderNotifier
import com.example.obsidiankeep.settings.LanguageHelper
import com.example.obsidiankeep.work.TrashCleanupWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class ObsidianKeepApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    /**
     * Применяем сохранённый язык ДО любого onCreate — так уведомления,
     * виджет и любые application-context операции получают корректную локаль.
     * Это гарантирует, что язык сохраняется между перезапусками приложения.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageHelper.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        ReminderNotifier.createChannel(this)
        scheduleTrashCleanup()
    }

    private fun scheduleTrashCleanup() {
        val request = PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TrashCleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
