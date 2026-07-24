package com.example.obsidiankeep.repository

import com.example.obsidiankeep.SortOrder
import com.example.obsidiankeep.data.Folder
import com.example.obsidiankeep.data.Note
import com.example.obsidiankeep.data.Tag
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface NoteRepository {

    val sortOrder: StateFlow<SortOrder>
    val unlockedFolderIds: StateFlow<Set<String>>

    val folders: StateFlow<List<Folder>>
    val rootNotes: StateFlow<List<Note>>
    val allNotes: StateFlow<List<Note>>
    val trashedNotes: StateFlow<List<Note>>
    val trashedFolders: StateFlow<List<Folder>>
    val activeReminders: Flow<List<Note>>
    val favoriteNotes: Flow<List<Note>>
    val allTags: Flow<List<Tag>>

    fun changeSortOrder(order: SortOrder)
    fun setFolderSortOrder(folderId: String, order: SortOrder)
    fun getFolderSortOrder(folderId: String): SortOrder
    fun getNotesInFolder(folderId: String): StateFlow<List<Note>>
    fun getBacklinks(noteId: String): Flow<List<Note>>
    fun getTagsForNote(noteId: String): Flow<List<Tag>>
    fun getNotesForTag(tagId: String): Flow<List<Note>>

    fun setFavorite(noteId: String, favorite: Boolean)
    fun setNoteTags(noteId: String, tagNames: List<String>)
    fun addTagToNote(noteId: String, tagName: String)
    fun removeTagFromNote(noteId: String, tagName: String)

    fun unlockFolder(folderId: String)
    fun lockFolder(folderId: String)
    fun isFolderSessionUnlocked(folderId: String): Boolean

    fun createFolder(name: String)
    fun renameFolder(folderId: String, newName: String)
    fun protectFolder(folderId: String)
    fun unprotectFolder(folderId: String)

    fun createNote(id: String, title: String, content: String, color: Int, folderId: String? = null)
    fun scheduleSave(note: Note)
    suspend fun forceSave(note: Note)
    suspend fun loadNote(noteId: String): Note?

    fun setReminder(noteId: String, reminderAt: Long?, repeatRule: String? = null)
    fun softDeleteNote(noteId: String)
    fun softDeleteNotes(noteIds: List<String>)
    fun softDeleteFolder(folderId: String)
    fun restoreNote(noteId: String)
    fun restoreNotes(noteIds: List<String>)
    fun restoreFolder(folderId: String)
    fun restoreAllTrash()
    fun hardDeleteNotes(noteIds: List<String>)
    fun clearAllTrash()
    fun purgeOldTrash(maxAgeDays: Int = 10)

    fun moveNotes(noteIds: List<String>, folderId: String?)

    fun convertNoteToMarkdown(note: Note): String

    suspend fun importMarkdownFilesFromTree(treeUri: Uri): Int
}

