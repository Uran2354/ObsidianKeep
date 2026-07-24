package com.example.obsidiankeep.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.obsidiankeep.R
import com.example.obsidiankeep.SortOrder
import com.example.obsidiankeep.backup.FileMirrorManager
import com.example.obsidiankeep.backup.MarkdownImporter
import com.example.obsidiankeep.data.Folder
import com.example.obsidiankeep.data.Note
import com.example.obsidiankeep.data.NoteDao
import com.example.obsidiankeep.data.NoteTagCrossRef
import com.example.obsidiankeep.data.RepeatRule
import com.example.obsidiankeep.data.Tag
import com.example.obsidiankeep.reminder.ReminderScheduler
import com.example.obsidiankeep.security.CryptoManager
import com.example.obsidiankeep.security.PinManager
import com.example.obsidiankeep.ui.theme.NoteColors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(FlowPreview::class)
@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val dao: NoteDao,
    private val fileMirror: FileMirrorManager,
    @ApplicationContext private val context: Context
) : NoteRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    override val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _unlockedFolderIds = MutableStateFlow<Set<String>>(emptySet())
    override val unlockedFolderIds: StateFlow<Set<String>> = _unlockedFolderIds.asStateFlow()

    private val unlockedKeys = ConcurrentHashMap<String, ByteArray>()
    private val noteSaveFlow = MutableSharedFlow<Note>(replay = 0, extraBufferCapacity = 64)

    /**
     * ID специальной "decoy" папки. Эта папка НЕ видна в обычном списке папок;
     * она показывается только когда активна decoy-сессия (введён фейковый PIN).
     *
     * Заметки, созданные в этой папке, видны только при decoy-входе — это безопасное
     * "хранилище для посторонних", которое выглядит как обычные заметки.
     */
    companion object {
        const val DECOY_FOLDER_ID = "0"
    }

    private val pinManagerLazy by lazy { PinManager.get(context) }

    /** Активна ли decoy-сессия (введён фейковый PIN). */
    private fun isDecoySession(): Boolean = pinManagerLazy.isDecoySession()

    /** Создаёт decoy-папку если её ещё нет. Вызывается при первом включении decoy PIN. */
    fun ensureDecoyFolderExists() {
        scope.launch {
            val existing = dao.getFolderByIdOnce(DECOY_FOLDER_ID)
            if (existing == null) {
                dao.insertFolder(Folder(
                    id = DECOY_FOLDER_ID,
                    name = context.getString(R.string.decoy_folder_name),
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }
    }

    /**
     * Список папок: decoy-папка "0" ПОЛНОСТЬЮ скрыта — никогда не возвращается в списке,
     * даже при активной decoy-сессии. Это позволяет «подменять» имя: при decoy-входе
     * пользователь видит имя нажатой папки, но контент — из decoy-папки.
     */
    override val folders: StateFlow<List<Folder>> = dao.getAllFolders()
        .map { allFolders -> allFolders.filter { it.id != DECOY_FOLDER_ID } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Корневые заметки (без папки) — фильтруем заметки из decoy-папки всегда.
     * Заметки decoy-папки видны только через FolderScreen при decoy-навигации.
     */
    override val rootNotes: StateFlow<List<Note>> = dao.getRootNotes()
        .map { notes -> notes.filter { it.folderId != DECOY_FOLDER_ID } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Все заметки — фильтруем заметки из decoy-папки всегда.
     * Заметки decoy-папки видны только через FolderScreen при decoy-навигации.
     */
    override val allNotes: StateFlow<List<Note>> = dao.getAllNotesRaw()
        .map { notes -> notes.filter { it.folderId != DECOY_FOLDER_ID } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    override val trashedNotes: StateFlow<List<Note>> = dao.getTrashedNotes()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    override val trashedFolders: StateFlow<List<Folder>> = dao.getTrashedFolders()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    override val activeReminders: Flow<List<Note>> = dao.getActiveReminders()
    override val favoriteNotes: Flow<List<Note>> = dao.getFavoriteNotes()
    override val allTags: Flow<List<Tag>> = dao.getAllTags()

    private val folderNotesFlows = mutableMapOf<String, StateFlow<List<Note>>>()
    private val folderSortOrders = ConcurrentHashMap<String, SortOrder>()

    init {
        noteSaveFlow
            .debounce(300)
            .distinctUntilChanged()
            .onEach { note -> saveNoteInternal(note) }
            .launchIn(scope)
    }

    override fun changeSortOrder(order: SortOrder) { _sortOrder.value = order }

    override fun setFolderSortOrder(folderId: String, order: SortOrder) {
        folderSortOrders[folderId] = order
    }

    override fun getFolderSortOrder(folderId: String): SortOrder =
        folderSortOrders[folderId] ?: _sortOrder.value

    override fun getNotesInFolder(folderId: String): StateFlow<List<Note>> =
        folderNotesFlows.getOrPut(folderId) {
            dao.getNotesInFolder(folderId)
                .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    override fun getBacklinks(noteId: String): Flow<List<Note>> =
        dao.getBacklinkNotesFor(noteId)

    override fun getTagsForNote(noteId: String): Flow<List<Tag>> =
        dao.getTagsForNote(noteId)

    override fun getNotesForTag(tagId: String): Flow<List<Note>> =
        dao.getNotesForTag(tagId)

    override fun setFavorite(noteId: String, favorite: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) { dao.setFavorite(noteId, favorite) }
        }
    }

    override fun setNoteTags(noteId: String, tagNames: List<String>) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val tagIds = mutableListOf<String>()
                tagNames.forEach { name ->
                    val cleanName = name.trim().removePrefix("#")
                    if (cleanName.isNotEmpty()) {
                        val existing = dao.getTagByName(cleanName)
                        val tagId = existing?.id ?: UUID.randomUUID().toString()
                        if (existing == null) {
                            dao.insertTag(Tag(id = tagId, name = cleanName))
                        }
                        tagIds.add(tagId)
                    }
                }
                dao.setNoteTags(noteId, tagIds)
            }
        }
    }

    override fun addTagToNote(noteId: String, tagName: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val cleanName = tagName.trim().removePrefix("#")
                if (cleanName.isEmpty()) return@withContext
                val existing = dao.getTagByName(cleanName)
                val tagId = existing?.id ?: UUID.randomUUID().toString()
                if (existing == null) dao.insertTag(Tag(id = tagId, name = cleanName))
                dao.insertNoteTagCrossRef(NoteTagCrossRef(noteId = noteId, tagId = tagId))
            }
        }
    }

    override fun removeTagFromNote(noteId: String, tagName: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val tag = dao.getTagByName(tagName.trim().removePrefix("#")) ?: return@withContext
                dao.removeNoteTag(noteId, tag.id)
            }
        }
    }

    override fun unlockFolder(folderId: String) {
        scope.launch {
            // Если активна decoy-сессия (введён фейковый PIN) — отказываем в разблокировке.
            // Это поведение «decoy mode» как в Signal: фейковый PIN открывает приложение,
            // но защищённые папки остаются недоступны.
            if (PinManager.get(context).isDecoySession()) return@launch
            val folder = dao.getFolderByIdOnce(folderId) ?: return@launch
            val encryptedKey = folder.encryptedKey ?: return@launch
            try {
                val dataKey = CryptoManager.decryptDataKey(encryptedKey)
                unlockedKeys[folderId] = dataKey
                _unlockedFolderIds.value = _unlockedFolderIds.value + folderId
            } catch (_: Exception) { }
        }
    }

    override fun lockFolder(folderId: String) {
        // В decoy-режиме папка и так остаётся заблокированной — ничего не делаем
        if (PinManager.get(context).isDecoySession()) return
        unlockedKeys.remove(folderId)
        _unlockedFolderIds.value = _unlockedFolderIds.value - folderId
    }

    override fun isFolderSessionUnlocked(folderId: String): Boolean =
        _unlockedFolderIds.value.contains(folderId)

    override fun createFolder(name: String) {
        scope.launch {
            dao.insertFolder(
                Folder(id = UUID.randomUUID().toString(), name = name, updatedAt = System.currentTimeMillis())
            )
        }
    }

    override fun renameFolder(folderId: String, newName: String) {
        scope.launch {
            dao.getFolderByIdOnce(folderId)?.let { folder ->
                dao.insertFolder(folder.copy(name = newName, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    override fun protectFolder(folderId: String) {
        scope.launch {
            val folder = dao.getFolderByIdOnce(folderId) ?: return@launch
            if (folder.isProtected) return@launch
            val dataKey = CryptoManager.generateDataKey()
            val encryptedKey = CryptoManager.encryptDataKey(dataKey)
            val now = System.currentTimeMillis()
            // Загружаем только заметки этой папки, а не ВСЕ заметки из БД
            dao.getNotesByFolderOnce(folderId)
                .filter { !it.isEncrypted && it.deletedAt == null }
                .forEach { note ->
                    val encryptedContent = CryptoManager.encrypt(note.content, dataKey)
                    dao.insertNoteRaw(note.copy(content = encryptedContent, isEncrypted = true, updatedAt = now))
                }
            dao.insertFolder(folder.copy(isProtected = true, encryptedKey = encryptedKey, passwordHash = null, updatedAt = now))
            // Не разблокируем автоматически — пользователь должен войти биометрией
            // (или PIN-кодом, если он не decoy-сессия)
            if (!PinManager.get(context).isDecoySession()) {
                unlockedKeys[folderId] = dataKey
                _unlockedFolderIds.value = _unlockedFolderIds.value + folderId
            }
        }
    }

    override fun unprotectFolder(folderId: String) {
        scope.launch {
            val folder = dao.getFolderByIdOnce(folderId) ?: return@launch
            if (!folder.isProtected) return@launch
            val encryptedKey = folder.encryptedKey ?: return@launch
            val dataKey = try { CryptoManager.decryptDataKey(encryptedKey) } catch (_: Exception) { return@launch }
            val now = System.currentTimeMillis()
            // Загружаем только заметки этой папки, а не ВСЕ заметки из БД
            dao.getNotesByFolderOnce(folderId)
                .filter { it.isEncrypted }
                .forEach { note ->
                    val plain = try { CryptoManager.decrypt(note.content, dataKey) } catch (_: Exception) { note.content }
                    dao.insertNoteRaw(note.copy(content = plain, isEncrypted = false, updatedAt = now))
                }
            dao.insertFolder(folder.copy(isProtected = false, encryptedKey = null, passwordHash = null, updatedAt = now))
            unlockedKeys.remove(folderId)
            _unlockedFolderIds.value = _unlockedFolderIds.value - folderId
        }
    }

    override fun createNote(id: String, title: String, content: String, color: Int, folderId: String?) {
        scope.launch {
            // Пустая строка folderId → null (корень, без папки).
            // Это защита от передачи folderId="" из EditorScreen, когда initialFolderId был null.
            val effectiveFolderId = folderId?.takeIf { it.isNotBlank() }
            saveNoteInternal(
                Note(id = id, folderId = effectiveFolderId, title = title, content = content, color = color, updatedAt = System.currentTimeMillis())
            )
        }
    }

    override fun scheduleSave(note: Note) {
        scope.launch { noteSaveFlow.emit(note) }
    }

    override suspend fun forceSave(note: Note) = saveNoteInternal(note)

    private suspend fun saveNoteInternal(note: Note) {
        withContext(Dispatchers.IO) {
            // Шифруем только если папка защищена И ключ разблокирован И заметка ещё не зашифрована
            val toStore = if (note.folderId != null && !note.isEncrypted) {
                val folder = dao.getFolderByIdOnce(note.folderId)
                if (folder?.isProtected == true && folder.encryptedKey != null) {
                    val key = unlockedKeys[note.folderId]
                    if (key != null) {
                        note.copy(
                            content = CryptoManager.encrypt(note.content, key),
                            isEncrypted = true,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        note.copy(updatedAt = System.currentTimeMillis())
                    }
                } else {
                    note.copy(updatedAt = System.currentTimeMillis())
                }
            } else {
                note.copy(updatedAt = System.currentTimeMillis())
            }
            dao.saveNoteWithLinks(toStore)
            scheduleReminderInternal(toStore)
            fileMirror.mirrorNote(toStore)
            // Обновляем виджеты, чтобы они показали свежий контент
            com.example.obsidiankeep.widget.refreshAllWidgets(context)
        }
    }

    override fun setReminder(noteId: String, reminderAt: Long?, repeatRule: String?) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val note = dao.getNoteByIdOnce(noteId) ?: return@withContext
                val rule = repeatRule ?: RepeatRule.NONE
                val updated = note.copy(
                    reminderAt = reminderAt,
                    repeatRule = if (reminderAt != null) rule else null,
                    updatedAt = System.currentTimeMillis()
                )
                dao.insertNoteRaw(updated)
                scheduleReminderInternal(updated)
                fileMirror.mirrorNote(updated)
            }
        }
    }

    private fun scheduleReminderInternal(note: Note) {
        val now = System.currentTimeMillis()
        if (note.reminderAt != null && note.reminderAt > now) {
            ReminderScheduler.schedule(context, note.id, note.title, note.reminderAt, note.repeatRule)
        } else {
            ReminderScheduler.cancel(context, note.id)
        }
    }

    override suspend fun loadNote(noteId: String): Note? = withContext(Dispatchers.IO) {
        val note = dao.getNoteByIdOnce(noteId) ?: return@withContext null
        if (!note.isEncrypted) return@withContext note
        val folderId = note.folderId ?: return@withContext note
        val key = unlockedKeys[folderId] ?: return@withContext note
        try {
            note.copy(content = CryptoManager.decrypt(note.content, key))
        } catch (_: Exception) { note }
    }

    override fun softDeleteNote(noteId: String) {
        scope.launch {
            dao.getNoteByIdOnce(noteId)?.let { fileMirror.removeMirror(it.title) }
            ReminderScheduler.cancel(context, noteId)
            dao.softDeleteNote(noteId, System.currentTimeMillis())
            com.example.obsidiankeep.widget.refreshAllWidgets(context)
        }
    }

    override fun softDeleteNotes(noteIds: List<String>) {
        scope.launch {
            noteIds.forEach { id ->
                dao.getNoteByIdOnce(id)?.let { fileMirror.removeMirror(it.title) }
                ReminderScheduler.cancel(context, id)
            }
            dao.softDeleteNotes(noteIds, System.currentTimeMillis())
            com.example.obsidiankeep.widget.refreshAllWidgets(context)
        }
    }

    override fun softDeleteFolder(folderId: String) {
        scope.launch { dao.softDeleteFolderWithNotes(folderId, System.currentTimeMillis()) }
    }

    override fun restoreNote(noteId: String) {
        scope.launch { dao.restoreNote(noteId) }
    }

    override fun restoreNotes(noteIds: List<String>) {
        scope.launch { dao.restoreNotes(noteIds) }
    }

    override fun restoreFolder(folderId: String) {
        scope.launch { dao.restoreFolderWithNotes(folderId) }
    }

    override fun restoreAllTrash() {
        scope.launch {
            dao.restoreAllTrashedNotes()
            dao.restoreAllTrashedFolders()
        }
    }

    override fun hardDeleteNotes(noteIds: List<String>) {
        scope.launch { dao.deleteNotesByIds(noteIds) }
    }

    override fun clearAllTrash() {
        scope.launch {
            dao.deleteAllTrashedNotes()
            dao.deleteAllTrashedFolders()
        }
    }

    override fun purgeOldTrash(maxAgeDays: Int) {
        scope.launch {
            val threshold = System.currentTimeMillis() - maxAgeDays * 24L * 60L * 60L * 1000L
            dao.purgeOldTrashedNotes(threshold)
            dao.purgeOldTrashedFolders(threshold)
        }
    }

    override fun moveNotes(noteIds: List<String>, folderId: String?) {
        scope.launch { dao.moveNotesToFolder(noteIds, folderId) }
    }

    override fun convertNoteToMarkdown(note: Note): String = buildString {
        append("---\n")
        append("title: \"${note.title.replace("\"", "\\\"")}\"\n")
        append("updated: ${note.updatedAt}\n")
        append("color: ${note.color}\n")
        append("encrypted: ${note.isEncrypted}\n")
        append("---\n\n")
        append(note.content)
    }

    override suspend fun importMarkdownFilesFromTree(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext 0
        var count = 0
        suspend fun walk(dir: DocumentFile) {
            dir.listFiles().forEach { file ->
                when {
                    file.isDirectory -> walk(file)
                    file.isFile && (file.name?.endsWith(".md") == true || file.name?.endsWith(".markdown") == true) -> {
                        context.contentResolver.openInputStream(file.uri)?.use { stream ->
                            val text = stream.readBytes().toString(Charsets.UTF_8)
                            val parsed = MarkdownImporter.parse(text, file.name?.removeSuffix(".md") ?: context.getString(R.string.import_default_title))
                            val newId = UUID.randomUUID().toString()
                            dao.saveNoteWithLinks(
                                Note(
                                    id = newId,
                                    title = parsed.title,
                                    content = parsed.content,
                                    color = parsed.color ?: NoteColors.DEFAULT,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            count++
                        }
                    }
                }
            }
        }
        walk(tree)
        count
    }
}
