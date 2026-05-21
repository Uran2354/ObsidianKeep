package com.example.obsidiankeep.data // Проверьте ваш package!

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey val id: String,
    val name: String,
    val isProtected: Boolean = false,
    val passwordHash: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String,
    val folderId: String? = null,
    val title: String,
    val content: String,
    // Перевели цвет в Int! По умолчанию — темно-серый графит (0xFF2C2C2C)
    val color: Int = 0xFF2C2C2C.toInt(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "links")
data class NoteLink(
    @PrimaryKey val id: String,
    val sourceId: String,
    val targetId: String
)