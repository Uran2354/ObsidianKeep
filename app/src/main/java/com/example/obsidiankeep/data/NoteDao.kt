package com.example.obsidiankeep.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<Folder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder)

    @Query("SELECT * FROM notes WHERE folderId IS NULL ORDER BY updatedAt DESC")
    fun getRootNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun getNotesInFolder(folderId: String): Flow<List<Note>>

    @Query("SELECT * FROM notes")
    fun getAllNotesRaw(): Flow<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteRaw(note: Note)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: String)

    @Query("DELETE FROM notes WHERE id IN (:noteIds)")
    suspend fun deleteNotesByIds(noteIds: List<String>)

    @Query("UPDATE notes SET folderId = :folderId WHERE id = :noteId")
    suspend fun moveNoteToFolder(noteId: String, folderId: String?)

    @Query("DELETE FROM links WHERE sourceId = :sourceId")
    suspend fun deleteLinksFromNote(sourceId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: NoteLink)

    @Query("SELECT id FROM notes WHERE title = :title LIMIT 1")
    suspend fun getNoteIdByTitle(title: String): String?

    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    suspend fun getNoteByIdOnce(noteId: String): Note?

    @Transaction
    suspend fun saveNoteWithLinks(note: Note) {
        insertNoteRaw(note)
        deleteLinksFromNote(note.id)

        val regex = "\\[\\[(.*?)]]".toRegex()
        val matches = regex.findAll(note.content)

        for (match in matches) {
            val targetTitle = match.groupValues[1].trim()
            if (targetTitle.isNotEmpty()) {
                val targetId = getNoteIdByTitle(targetTitle)
                if (targetId != null) {
                    insertLink(NoteLink(id = "${note.id}_$targetId", sourceId = note.id, targetId = targetId))
                }
            }
        }
    }
}
