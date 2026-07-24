package com.example.obsidiankeep.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey val id: String,
    val name: String,
    val isProtected: Boolean = false,
    val passwordHash: String? = null,
    val encryptedKey: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

@Entity(
    tableName = "notes",
    indices = [
        Index("folderId"),
        Index("updatedAt"),
        Index("reminderAt"),
        Index("isFavorite")
    ]
)
data class Note(
    @PrimaryKey val id: String,
    val folderId: String? = null,
    val title: String,
    val content: String,
    val color: Int = 0xFF2C2C2C.toInt(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val isEncrypted: Boolean = false,
    val reminderAt: Long? = null,
    val repeatRule: String? = null,
    val isFavorite: Boolean = false
)

@Entity(tableName = "links")
data class NoteLink(
    @PrimaryKey val id: String,
    val sourceId: String,
    val targetId: String
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class Tag(
    @PrimaryKey val id: String,
    val name: String
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    indices = [Index("noteId"), Index("tagId")]
)
data class NoteTagCrossRef(
    val noteId: String,
    val tagId: String
)

object RepeatRule {
    const val NONE = "none"
    const val DAILY = "daily"
    const val WEEKDAYS = "weekdays"
    const val WEEKLY = "weekly"

    fun nextTrigger(currentTrigger: Long, rule: String?): Long? {
        if (rule == null || rule == NONE) return null
        val day = 24L * 60 * 60 * 1000
        return when (rule) {
            DAILY -> currentTrigger + day
            WEEKLY -> currentTrigger + 7 * day
            WEEKDAYS -> {
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = currentTrigger
                    add(java.util.Calendar.DAY_OF_MONTH, 1)
                }
                while (cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SATURDAY ||
                    cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY
                ) {
                    cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                }
                cal.timeInMillis
            }
            else -> null
        }
    }
}
