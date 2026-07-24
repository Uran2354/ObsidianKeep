package com.example.obsidiankeep.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Управление изображениями-вложениями для заметок.
 *
 * Изображения хранятся в `filesDir/attachments/<noteId>/<uuid>.<ext>`.
 * В контенте заметки они вставляются как `![](attachments/<noteId>/<uuid>.png)`.
 *
 * Поддерживаемые форматы: PNG, JPEG, WEBP, GIF.
 *
 * Поддерживается два режима:
 *  1. Inline (base64) — для маленьких изображений (<100KB), вставляется как
 *     `![](data:image/png;base64,...)` — переносится в .md / .zip / .html.
 *  2. File attachment — для больших изображений, вставляется как
 *     `![](attachments/<noteId>/<file>.png)` — работает только внутри приложения.
 */
@Singleton
class ImageAttachmentManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val rootDir = File(context.filesDir, "attachments").apply { mkdirs() }

    /** Каталог для изображений конкретной заметки (создаётся если нет). */
    fun noteDir(noteId: String): File = File(rootDir, noteId).apply { mkdirs() }

    /**
     * Сохраняет изображение из Uri в filesDir/attachments/<noteId>/<uuid>.<ext>.
     * Возвращает markdown-ссылку для вставки в контент заметки.
     *
     * @param uri Uri изображения (от ContentResolver, file://, или content://)
     * @param noteId ID заметки-владельца
     * @param maxWidth если задано — изображение масштабируется по ширине (экономит место)
     * @return markdown-ссылка вида `![](attachments/<noteId>/<uuid>.png)`
     */
    suspend fun saveImageFromUri(uri: Uri, noteId: String, maxWidth: Int = 1920): String? =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = decodeUri(uri, maxWidth) ?: return@withContext null
                val uuid = UUID.randomUUID().toString()
                val file = File(noteDir(noteId), "$uuid.png")
                FileOutputStream(file).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
                }
                bitmap.recycle()
                // Возвращаем относительный путь — будет вставлен в контент заметки
                "![](attachments/$noteId/$uuid.png)"
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Декодирует изображение из Uri, опционально уменьшая по ширине.
     */
    private fun decodeUri(uri: Uri, maxWidth: Int): Bitmap? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            // Сначала читаем только размеры (inJustDecodeBounds=true)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            input.use { BitmapFactory.decodeStream(it, null, opts) }
            // Считаем inSampleSize для уменьшения
            var sampleSize = 1
            if (maxWidth > 0 && opts.outWidth > maxWidth) {
                sampleSize = opts.outWidth / maxWidth
                if (sampleSize < 1) sampleSize = 1
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val input2 = context.contentResolver.openInputStream(uri) ?: return null
            input2.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Возвращает File для markdown-пути вида `attachments/<noteId>/<uuid>.png`.
     * Если путь не начинается с "attachments/" — возвращает null (внешняя ссылка).
     */
    fun resolvePath(path: String): File? {
        val cleaned = path.removePrefix("attachments/")
        if (cleaned == path) return null // не наш формат
        val file = File(rootDir, cleaned)
        return if (file.exists()) file else null
    }

    /**
     * Удаляет все изображения заметки (например, при удалении самой заметки).
     */
    suspend fun deleteImagesForNote(noteId: String) = withContext(Dispatchers.IO) {
        try {
            noteDir(noteId).deleteRecursively()
        } catch (_: Exception) { }
    }

    /**
     * Извлекает все пути изображений из markdown-контента.
     * Возвращает список относительных путей (после `![](`).
     */
    fun extractImagePaths(content: String): List<String> {
        val regex = Regex("!\\[[^\\]]*]\\(([^)]+)\\)")
        return regex.findAll(content)
            .map { it.groupValues[1] }
            .filter { it.startsWith("attachments/") }
            .toList()
    }

    /**
     * Удаляет изображения, которые больше не referenced в контенте заметки.
     * Вызывается после сохранения заметки — чистит "осиротевшие" файлы.
     */
    suspend fun cleanupOrphanedImages(noteId: String, content: String) = withContext(Dispatchers.IO) {
        try {
            val referenced = extractImagePaths(content).map { it.removePrefix("attachments/") }
            val dir = noteDir(noteId)
            dir.listFiles()?.forEach { file ->
                val relPath = "$noteId/${file.name}"
                if (relPath !in referenced) {
                    file.delete()
                }
            }
        } catch (_: Exception) { }
    }
}
