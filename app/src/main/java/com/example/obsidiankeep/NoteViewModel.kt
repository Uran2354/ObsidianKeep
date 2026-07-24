package com.example.obsidiankeep

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.obsidiankeep.attachments.ImageAttachmentManager
import com.example.obsidiankeep.audio.AudioNoteManager
import com.example.obsidiankeep.backup.BackupManager
import com.example.obsidiankeep.backup.FileMirrorManager
import com.example.obsidiankeep.backup.MarkdownImporter
import com.example.obsidiankeep.data.Note
import com.example.obsidiankeep.export.HtmlExporter
import com.example.obsidiankeep.export.PdfExporter
import com.example.obsidiankeep.export.TxtExporter
import com.example.obsidiankeep.repository.NoteRepository
import com.example.obsidiankeep.settings.SettingsManager
import com.example.obsidiankeep.ui.theme.NoteColors
import com.example.obsidiankeep.work.TrashCleanupWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NoteViewModel @Inject constructor(
    application: Application,
    private val repository: NoteRepository,
    private val backupManager: BackupManager,
    private val fileMirror: FileMirrorManager,
    private val pdfExporter: PdfExporter,
    private val htmlExporter: HtmlExporter,
    private val txtExporter: TxtExporter,
    private val audioNoteManager: AudioNoteManager,
    private val imageAttachmentManager: ImageAttachmentManager,
    private val settings: SettingsManager
) : AndroidViewModel(application) {

    val sortOrder get() = repository.sortOrder
    val unlockedFolderIds get() = repository.unlockedFolderIds
    val folders get() = repository.folders
    val rootNotes get() = repository.rootNotes
    val allNotes get() = repository.allNotes
    val trashedNotes get() = repository.trashedNotes
    val trashedFolders get() = repository.trashedFolders
    val activeReminders: StateFlow<List<Note>> = repository.activeReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favoriteNotes get() = repository.favoriteNotes
    val allTags get() = repository.allTags
    val gridColumns get() = settings.gridColumns
    val language get() = settings.language

    fun setLanguage(lang: String) {
        viewModelScope.launch { settings.setLanguage(lang) }
    }

    fun changeSortOrder(order: SortOrder) = repository.changeSortOrder(order)

    fun unlockFolderSession(folderId: String) = repository.unlockFolder(folderId)
    fun lockFolderSession(folderId: String) = repository.lockFolder(folderId)
    fun isFolderSessionUnlocked(folderId: String) = repository.isFolderSessionUnlocked(folderId)

    fun getNotesInFolder(folderId: String) = repository.getNotesInFolder(folderId)
    fun getBacklinks(noteId: String): Flow<List<Note>> = repository.getBacklinks(noteId)
    fun getTagsForNote(noteId: String) = repository.getTagsForNote(noteId)
    fun getNotesForTag(tagId: String) = repository.getNotesForTag(tagId)
    fun setFavorite(noteId: String, favorite: Boolean) = repository.setFavorite(noteId, favorite)
    fun addTagToNote(noteId: String, tagName: String) = repository.addTagToNote(noteId, tagName)
    fun removeTagFromNote(noteId: String, tagName: String) = repository.removeTagFromNote(noteId, tagName)
    fun setFolderSortOrder(folderId: String, order: SortOrder) = repository.setFolderSortOrder(folderId, order)
    fun getFolderSortOrder(folderId: String) = repository.getFolderSortOrder(folderId)

    fun createFolder(name: String) = repository.createFolder(name)

    /** Создаёт decoy-папку "0" если её ещё нет. */
    fun ensureDecoyFolderExists() {
        val impl = repository as? com.example.obsidiankeep.repository.NoteRepositoryImpl
        impl?.ensureDecoyFolderExists()
    }
    fun renameFolder(folderId: String, newName: String) = repository.renameFolder(folderId, newName)
    fun protectFolder(folderId: String) = repository.protectFolder(folderId)
    fun unprotectFolder(folderId: String) = repository.unprotectFolder(folderId)

    fun createNote(id: String, title: String, content: String, color: Int) =
        repository.createNote(id, title, content, color, null)

    fun createNoteInFolder(id: String, folderId: String, title: String, content: String, color: Int) =
        repository.createNote(id, title, content, color, folderId)

    fun updateExistingNote(note: Note) = repository.scheduleSave(note)

    fun forceSaveChangesNow(note: Note) {
        viewModelScope.launch { repository.forceSave(note) }
    }

    suspend fun loadNoteById(noteId: String) = repository.loadNote(noteId)

    fun setReminder(noteId: String, reminderAt: Long?, repeatRule: String? = null) =
        repository.setReminder(noteId, reminderAt, repeatRule)

    fun deleteNote(noteId: String) = repository.softDeleteNote(noteId)
    fun deleteMultipleNotes(noteIds: List<String>) = repository.softDeleteNotes(noteIds)
    fun deleteMultipleFolders(folderIds: List<String>) = folderIds.forEach { repository.softDeleteFolder(it) }
    fun moveMultipleNotes(noteIds: List<String>, folderId: String?) = repository.moveNotes(noteIds, folderId)

    fun restoreNote(noteId: String) = repository.restoreNote(noteId)
    fun restoreNotes(noteIds: List<String>) = repository.restoreNotes(noteIds)
    fun restoreFolder(folderId: String) = repository.restoreFolder(folderId)
    fun restoreAllTrash() = repository.restoreAllTrash()
    fun hardDeleteNotes(noteIds: List<String>) = repository.hardDeleteNotes(noteIds)
    fun clearAllTrash() = repository.clearAllTrash()
    fun hardDeleteFolder(folderId: String) {
        repository.hardDeleteNotes(allNotes.value.filter { it.folderId == folderId }.map { it.id })
    }
    fun purgeOldTrash() = repository.purgeOldTrash(TrashCleanupWorker.MAX_AGE_DAYS)

    fun convertNoteToMarkdown(note: Note) = repository.convertNoteToMarkdown(note)

    suspend fun exportNoteToPdf(note: Note, outputStream: OutputStream) =
        pdfExporter.export(note, outputStream)

    suspend fun exportNoteToHtml(note: Note, outputStream: OutputStream) =
        withContext(Dispatchers.IO) {
            outputStream.write(htmlExporter.export(note).toByteArray(Charsets.UTF_8))
        }

    suspend fun exportNoteToTxt(note: Note, outputStream: OutputStream) =
        withContext(Dispatchers.IO) {
            outputStream.write(txtExporter.export(note).toByteArray(Charsets.UTF_8))
        }

    /** HTML-представление заметки — для печати через WebView. */
    fun renderNoteHtml(note: Note): String = htmlExporter.export(note)

    // === Аудио-заметки (диктофон) ===
    val recordingState get() = audioNoteManager.recordingState
    val recordingElapsedMs get() = audioNoteManager.recordingElapsedMs
    val playbackState get() = audioNoteManager.playbackState
    val playbackPositionMs get() = audioNoteManager.playbackPositionMs
    val playbackDurationMs get() = audioNoteManager.playbackDurationMs
    val audioRecords get() = audioNoteManager.records

    fun startAudioRecording() = audioNoteManager.startRecording()
    fun stopAudioRecording() = audioNoteManager.stopRecording()
    fun startAudioPlayback(recordId: String) = audioNoteManager.startPlayback(recordId)
    fun stopAudioPlayback() = audioNoteManager.stopPlayback()
    fun deleteAudioRecord(recordId: String) = audioNoteManager.deleteAudio(recordId)
    fun refreshAudioRecords() = audioNoteManager.refreshRecords()

    // === Изображения-вложения ===
    /**
     * Сохраняет изображение из Uri в filesDir/attachments/<noteId>/...
     * Возвращает markdown-ссылку для вставки в контент, или null при ошибке.
     */
    suspend fun saveImageAttachment(uri: Uri, noteId: String): String? =
        imageAttachmentManager.saveImageFromUri(uri, noteId)

    /** Удаляет все изображения заметки (например, при удалении заметки). */
    fun deleteImagesForNote(noteId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            imageAttachmentManager.deleteImagesForNote(noteId)
        }
    }

    /** Чистит осиротевшие изображения после сохранения заметки. */
    fun cleanupOrphanedImages(noteId: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            imageAttachmentManager.cleanupOrphanedImages(noteId, content)
        }
    }

    /** Возвращает File для markdown-пути (или null, если путь внешний). */
    fun resolveImagePath(path: String): java.io.File? = imageAttachmentManager.resolvePath(path)

    suspend fun importMarkdownFromTree(treeUri: Uri): Int =
        repository.importMarkdownFilesFromTree(treeUri)

    fun setGridColumns(columns: Int) {
        viewModelScope.launch { settings.setGridColumns(columns) }
    }

    fun isMirrorEnabled(): Boolean = fileMirror.isEnabled()
    fun setMirrorEnabled(enabled: Boolean) = fileMirror.setEnabled(enabled)
    fun getMirrorTreeUri(): Uri? = fileMirror.getTreeUri()
    fun setMirrorTreeUri(uri: Uri?) {
        fileMirror.setTreeUri(uri)
        if (uri != null) {
            viewModelScope.launch(Dispatchers.IO) {
                allNotes.value
                    .filter { !it.isEncrypted && it.deletedAt == null }
                    .forEach { fileMirror.mirrorNote(it) }
            }
        }
    }
    fun mirrorNoteNow(note: Note) {
        viewModelScope.launch(Dispatchers.IO) { fileMirror.mirrorNote(note) }
    }

    suspend fun exportTo(outputStream: OutputStream) = backupManager.exportTo(outputStream)

    suspend fun importFromZip(inputStream: InputStream): BackupManager.ImportResult =
        backupManager.importFrom(inputStream)

    suspend fun importMarkdownFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val text = resolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: return@withContext null
        val fileName = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }?.substringBeforeLast('.') ?: getApplication<Application>().getString(R.string.import_default_title)
        val parsed = MarkdownImporter.parse(text, fileName)
        val newId = UUID.randomUUID().toString()
        repository.forceSave(
            Note(
                id = newId,
                title = parsed.title,
                content = parsed.content,
                color = parsed.color ?: NoteColors.DEFAULT,
                updatedAt = System.currentTimeMillis()
            )
        )
        newId
    }
}
