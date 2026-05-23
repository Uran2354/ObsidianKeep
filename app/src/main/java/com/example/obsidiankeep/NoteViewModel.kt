package com.example.obsidiankeep // Проверьте, что этот package совпадает с вашим!

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.obsidiankeep.data.Folder
import com.example.obsidiankeep.data.Note
import com.example.obsidiankeep.data.NoteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class SortOrder {
    NEWEST,     // Сначала новые
    OLDEST,     // Сначала старые
    ALPHABETIC  // По алфавиту
}

@OptIn(kotlinx.coroutines.FlowPreview::class) // Нужно для работы оператора debounce
class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = NoteDatabase.getDatabase(application).noteDao()

    // Глобальный режим сортировки приложения
    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    // Реактивный список ID разблокированных папок в оперативной памяти
    private val _unlockedFolderIds = MutableStateFlow<Set<String>>(emptySet())
    val unlockedFolderIds: StateFlow<Set<String>> = _unlockedFolderIds.asStateFlow()

    // Буферный поток для отложенного сохранения заметок во время ввода
    private val noteSaveFlow = MutableSharedFlow<Note>(replay = 0, extraBufferCapacity = 64)

    // ЧИСТЫЕ ПОТОКИ ИЗ БАЗЫ
    val folders: StateFlow<List<Folder>> = dao.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rootNotes: StateFlow<List<Note>> = dao.getRootNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNotes: StateFlow<List<Note>> = dao.getAllNotesRaw()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // НАСТРОЙКА DEBOUNCE: Слушаем поток изменений текста.
        // Запись в базу Room произойдет только после паузы в 300 миллисекунд.
        noteSaveFlow
            .debounce(300)
            .distinctUntilChanged() // Не сохраняем, если текст не изменился (например, при перемещении курсора)
            .onEach { note ->
                withContext(Dispatchers.IO) {
                    dao.saveNoteWithLinks(note.copy(updatedAt = System.currentTimeMillis()))
                }
            }
            .launchIn(viewModelScope)
    }

    fun changeSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun unlockFolderSession(folderId: String) {
        _unlockedFolderIds.value = _unlockedFolderIds.value + folderId
    }

    fun lockFolderSession(folderId: String) {
        _unlockedFolderIds.value = _unlockedFolderIds.value - folderId
    }

    fun isFolderSessionUnlocked(folderId: String): Boolean {
        return _unlockedFolderIds.value.contains(folderId)
    }

    fun getNotesInFolder(folderId: String): StateFlow<List<Note>> {
        return dao.getNotesInFolder(folderId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val folder = Folder(id = UUID.randomUUID().toString(), name = name, updatedAt = System.currentTimeMillis())
                dao.insertFolder(folder)
            }
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val currentFolders = folders.value
                val folder = currentFolders.find { it.id == folderId }
                if (folder != null) {
                    val updatedFolder = folder.copy(name = newName, updatedAt = System.currentTimeMillis())
                    dao.insertFolder(updatedFolder)
                }
            }
        }
    }

    fun protectFolder(folderId: String, passwordHash: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val currentFolders = folders.value
                val folder = currentFolders.find { it.id == folderId }
                if (folder != null) {
                    val updatedFolder = folder.copy(isProtected = true, passwordHash = passwordHash)
                    dao.insertFolder(updatedFolder)
                }
            }
        }
    }

    fun unprotectFolder(folderId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val currentFolders = folders.value
                val folder = currentFolders.find { it.id == folderId }
                if (folder != null) {
                    val updatedFolder = folder.copy(isProtected = false, passwordHash = null)
                    dao.insertFolder(updatedFolder)
                }
            }
        }
    }

    fun createNote(id: String, title: String, content: String, color: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val note = Note(id = id, title = title, content = content, color = color, updatedAt = System.currentTimeMillis())
                dao.saveNoteWithLinks(note)
            }
        }
    }

    fun createNoteInFolder(id: String, folderId: String, title: String, content: String, color: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val note = Note(id = id, folderId = folderId, title = title, content = content, color = color, updatedAt = System.currentTimeMillis())
                dao.saveNoteWithLinks(note)
            }
        }
    }

    // БЫСТРЫЙ И ОПТИМИЗИРОВАННЫЙ ВВОД ТЕКСТА: Кидаем заметку в асинхронный буфер без блокировки UI
    fun updateExistingNote(note: Note) {
        viewModelScope.launch {
            noteSaveFlow.emit(note)
        }
    }

    // МГНОВЕННОЕ ПРИНУДИТЕЛЬНОЕ СОХРАНЕНИЕ: Вызывается, когда пользователь нажимает кнопку Назад или Сохранить
    fun forceSaveChangesNow(note: Note) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.saveNoteWithLinks(note.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteNoteById(noteId)
            }
        }
    }

    fun deleteMultipleNotes(noteIds: List<String>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteNotesByIds(noteIds)
            }
        }
    }

    fun moveMultipleNotes(noteIds: List<String>, folderId: String?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                noteIds.forEach { id ->
                    dao.moveNoteToFolder(id, folderId)
                }
            }
        }
    }

    suspend fun loadNoteById(noteId: String): Note? {
        return withContext(Dispatchers.IO) {
            dao.getNoteByIdOnce(noteId)
        }
    }

    fun convertNoteToMarkdown(note: Note): String {
        val sb = StringBuilder()
        sb.append("---\n")
        sb.append("title: \"${note.title.replace("\"", "\\\"")}\"\n")
        sb.append("updated: ${note.updatedAt}\n")
        sb.append("color: \"${note.color}\"\n")
        sb.append("---\n\n")
        sb.append(note.content)
        return sb.toString()
    }

    fun deleteMultipleFolders(folderIds: List<String>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteFoldersByIds(folderIds)
            }
        }
    }
}
