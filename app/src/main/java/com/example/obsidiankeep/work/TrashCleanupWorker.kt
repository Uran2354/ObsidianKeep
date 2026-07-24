package com.example.obsidiankeep.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.obsidiankeep.data.NoteDatabase

class TrashCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val dao = NoteDatabase.getDatabase(applicationContext).noteDao()
            val threshold = System.currentTimeMillis() - MAX_AGE_DAYS * 24L * 60L * 60L * 1000L
            dao.purgeOldTrashedNotes(threshold)
            dao.purgeOldTrashedFolders(threshold)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "obsidian_keep_trash_cleanup"
        const val MAX_AGE_DAYS = 10
    }
}
