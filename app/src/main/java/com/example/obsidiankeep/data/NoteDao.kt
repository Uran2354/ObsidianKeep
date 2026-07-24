package com.example.obsidiankeep.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY name ASC")
    fun getAllFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashedFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersOnce(): List<Folder>

    @Query("SELECT * FROM folders WHERE id = :folderId LIMIT 1")
    suspend fun getFolderByIdOnce(folderId: String): Folder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder)

    @Query("SELECT * FROM notes WHERE folderId IS NULL AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun getRootNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE folderId = :folderId AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun getNotesInFolder(folderId: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL")
    fun getAllNotesRaw(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE folderId = :folderId")
    suspend fun getNotesByFolderOnce(folderId: String): List<Note>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isFavorite = 1 ORDER BY isFavorite DESC, updatedAt DESC")
    fun getFavoriteNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY isFavorite DESC, updatedAt DESC")
    fun getRootNotesWithFavorites(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getNotesPage(limit: Int, offset: Int): List<Note>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND folderId = :folderId ORDER BY updatedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getNotesPageInFolder(folderId: String, limit: Int, offset: Int): List<Note>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND title LIKE '%' || :query || '%' ORDER BY updatedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun searchNotesPage(query: String, limit: Int, offset: Int): List<Note>

    @Query("SELECT COUNT(*) FROM notes WHERE deletedAt IS NULL")
    suspend fun countActiveNotes(): Int

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesOnce(): List<Note>

    @Query("SELECT * FROM notes WHERE reminderAt IS NOT NULL AND deletedAt IS NULL ORDER BY reminderAt ASC")
    fun getActiveReminders(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE reminderAt IS NOT NULL AND deletedAt IS NULL ORDER BY reminderAt ASC")
    suspend fun getNotesWithReminders(): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteRaw(note: Note)

    @Query("UPDATE notes SET deletedAt = :time WHERE id = :noteId")
    suspend fun softDeleteNote(noteId: String, time: Long)

    @Query("UPDATE notes SET isFavorite = :favorite WHERE id = :noteId")
    suspend fun setFavorite(noteId: String, favorite: Boolean)

    @Query("UPDATE notes SET deletedAt = :time WHERE id IN (:noteIds)")
    suspend fun softDeleteNotes(noteIds: List<String>, time: Long)

    @Query("UPDATE notes SET deletedAt = NULL WHERE id = :noteId")
    suspend fun restoreNote(noteId: String)

    @Query("UPDATE notes SET deletedAt = NULL WHERE id IN (:noteIds)")
    suspend fun restoreNotes(noteIds: List<String>)

    @Query("UPDATE notes SET deletedAt = NULL WHERE deletedAt IS NOT NULL")
    suspend fun restoreAllTrashedNotes()

    @Query("UPDATE folders SET deletedAt = NULL WHERE deletedAt IS NOT NULL")
    suspend fun restoreAllTrashedFolders()

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL")
    suspend fun deleteAllTrashedNotes()

    @Query("DELETE FROM folders WHERE deletedAt IS NOT NULL")
    suspend fun deleteAllTrashedFolders()

    @Query("UPDATE notes SET deletedAt = NULL WHERE folderId = :folderId")
    suspend fun restoreNotesInFolder(folderId: String)

    @Query("UPDATE folders SET deletedAt = :time WHERE id = :folderId")
    suspend fun softDeleteFolder(folderId: String, time: Long)

    @Query("UPDATE notes SET deletedAt = :time WHERE folderId = :folderId AND deletedAt IS NULL")
    suspend fun softDeleteNotesInFolder(folderId: String, time: Long)

    @Query("UPDATE folders SET deletedAt = NULL WHERE id = :folderId")
    suspend fun restoreFolder(folderId: String)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: String)

    @Query("DELETE FROM notes WHERE id IN (:noteIds)")
    suspend fun deleteNotesByIds(noteIds: List<String>)

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun purgeOldTrashedNotes(threshold: Long)

    @Query("DELETE FROM folders WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun purgeOldTrashedFolders(threshold: Long)

    @Query("UPDATE notes SET folderId = :folderId WHERE id = :noteId")
    suspend fun moveNoteToFolder(noteId: String, folderId: String?)

    @Query("UPDATE notes SET folderId = :folderId WHERE id IN (:noteIds)")
    suspend fun moveNotesToFolder(noteIds: List<String>, folderId: String?)

    @Query("DELETE FROM folders WHERE id IN (:folderIds)")
    suspend fun deleteFoldersByIds(folderIds: List<String>)

    @Transaction
    suspend fun softDeleteFolderWithNotes(folderId: String, time: Long) {
        softDeleteFolder(folderId, time)
        softDeleteNotesInFolder(folderId, time)
    }

    @Transaction
    suspend fun restoreFolderWithNotes(folderId: String) {
        restoreFolder(folderId)
        restoreNotesInFolder(folderId)
    }

    @Query("DELETE FROM links WHERE sourceId = :sourceId")
    suspend fun deleteLinksFromNote(sourceId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: NoteLink)

    @Query("SELECT id FROM notes WHERE title = :title AND deletedAt IS NULL LIMIT 1")
    suspend fun getNoteIdByTitle(title: String): String?

    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    suspend fun getNoteByIdOnce(noteId: String): Note?

    @Query("SELECT * FROM notes WHERE id IN (SELECT sourceId FROM links WHERE targetId = :noteId) AND deletedAt IS NULL")
    fun getBacklinkNotesFor(noteId: String): Flow<List<Note>>

    @Query("SELECT id, title FROM notes WHERE deletedAt IS NULL")
    suspend fun getAllNoteTitles(): List<NoteTitle>

    @Transaction
    suspend fun saveNoteWithLinks(note: Note) {
        insertNoteRaw(note)
        deleteLinksFromNote(note.id)
        val regex = "\\[\\[(.*?)]]".toRegex()
        regex.findAll(note.content).forEach { match ->
            val targetTitle = match.groupValues[1].trim()
            if (targetTitle.isNotEmpty()) {
                getNoteIdByTitle(targetTitle)?.let { targetId ->
                    insertLink(NoteLink(id = "${note.id}_$targetId", sourceId = note.id, targetId = targetId))
                }
            }
        }
    }

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): Tag?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tag)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteTagCrossRef(ref: NoteTagCrossRef)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun clearNoteTags(noteId: String)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId AND tagId = :tagId")
    suspend fun removeNoteTag(noteId: String, tagId: String)

    @Query("SELECT * FROM notes WHERE id IN (SELECT noteId FROM note_tags WHERE tagId = :tagId) AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun getNotesForTag(tagId: String): Flow<List<Note>>

    @Query("SELECT * FROM tags WHERE id IN (SELECT tagId FROM note_tags WHERE noteId = :noteId) ORDER BY name ASC")
    fun getTagsForNote(noteId: String): Flow<List<Tag>>

    @Transaction
    suspend fun setNoteTags(noteId: String, tagIds: List<String>) {
        clearNoteTags(noteId)
        tagIds.forEach { tagId ->
            insertNoteTagCrossRef(NoteTagCrossRef(noteId = noteId, tagId = tagId))
        }
    }
}

data class NoteTitle(val id: String, val title: String)
