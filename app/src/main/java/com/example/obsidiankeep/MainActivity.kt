package com.example.obsidiankeep

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.obsidiankeep.data.Note
import com.example.obsidiankeep.export.NotePrinter
import com.example.obsidiankeep.security.PinCheckResult
import com.example.obsidiankeep.security.PinManager
import com.example.obsidiankeep.settings.LanguageHelper
import com.example.obsidiankeep.ui.theme.NoteColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: NoteViewModel by viewModels()
    private var pendingImportUri by mutableStateOf<Uri?>(null)
    private var pendingOpenNoteId by mutableStateOf<String?>(null)
    private var pendingQuickText by mutableStateOf<String?>(null)

    /**
     * Применяем сохранённый язык ДО onCreate — это гарантирует, что
     * все stringResource(...) в Compose уже на старте получают правильную
     * локаль, а переключение языка сохраняется между запусками приложения.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageHelper.wrap(newBase))
    }

    private val exportZipLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                contentResolver.openOutputStream(uri)?.use { stream ->
                    viewModel.exportTo(stream)
                }
                Toast.makeText(this@MainActivity, getString(R.string.export_done), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importZipLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val result = contentResolver.openInputStream(uri)?.use { stream ->
                    viewModel.importFromZip(stream)
                }
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.import_count, result?.foldersImported ?: 0, result?.notesImported ?: 0),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private val importMdLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImportUri = uri
    }

    private val mirrorTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.setMirrorTreeUri(uri)
                viewModel.setMirrorEnabled(true)
                Toast.makeText(this, getString(R.string.mirror_on), Toast.LENGTH_SHORT).show()
            } catch (_: SecurityException) {
                Toast.makeText(this, getString(R.string.mirror_access_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    /**
     * Запрос разрешения на запись аудио.
     * Колбэк хранится в [pendingAudioAction], чтобы после получения разрешения
     * запустить запись — иначе пользовательский тап пропадёт.
     */
    private var pendingAudioAction: (() -> Unit)? = null
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingAudioAction?.invoke()
        } else {
            Toast.makeText(this, R.string.audio_permission_required, Toast.LENGTH_SHORT).show()
        }
        pendingAudioAction = null
    }

    /**
     * Запросить разрешение на микрофон и выполнить [action] после подтверждения.
     * Если разрешение уже есть — выполняет сразу.
     */
    fun requestMicrophoneThen(action: () -> Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingAudioAction = action
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    /**
     * Launcher для выбора изображения из галереи.
     * После выбора вызывает [pendingImageInsertCallback] с markdown-ссылкой.
     */
    private var pendingImageInsertCallback: ((String) -> Unit)? = null
    private var pendingImageNoteId: String? = null
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val noteId = pendingImageNoteId
            val cb = pendingImageInsertCallback
            if (noteId != null && cb != null) {
                lifecycleScope.launch {
                    val markdown = viewModel.saveImageAttachment(uri, noteId)
                    if (markdown != null) {
                        cb(markdown)
                    } else {
                        Toast.makeText(this@MainActivity, "Не удалось сохранить изображение", Toast.LENGTH_SHORT).show()
                    }
                    pendingImageInsertCallback = null
                    pendingImageNoteId = null
                }
            }
        } else {
            pendingImageInsertCallback = null
            pendingImageNoteId = null
        }
    }

    /**
     * Запустить выбор изображения из галереи для вставки в заметку.
     * @param noteId ID заметки-владельца (для filesDir/attachments/<noteId>/)
     * @param onMarkdownReady колбэк с markdown-ссылкой для вставки в контент
     */
    fun launchImagePicker(noteId: String, onMarkdownReady: (String) -> Unit) {
        pendingImageNoteId = noteId
        pendingImageInsertCallback = onMarkdownReady
        imagePickerLauncher.launch("image/*")
    }

    private val vaultImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val count = viewModel.importMarkdownFromTree(uri)
                Toast.makeText(
                    this@MainActivity,
                    if (count > 0) getString(R.string.import_md_count, count) else getString(R.string.md_not_found),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private var pendingPdfNote: Note? = null
    private val pdfExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            val note = pendingPdfNote
            if (note != null) {
                lifecycleScope.launch {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        viewModel.exportNoteToPdf(note, stream)
                    }
                    Toast.makeText(this@MainActivity, getString(R.string.pdf_saved), Toast.LENGTH_SHORT).show()
                    pendingPdfNote = null
                }
            }
        }
    }

    private var pendingHtmlNote: Note? = null
    private val htmlExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) {
            val note = pendingHtmlNote
            if (note != null) {
                lifecycleScope.launch {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        viewModel.exportNoteToHtml(note, stream)
                    }
                    Toast.makeText(this@MainActivity, getString(R.string.export_html_saved), Toast.LENGTH_SHORT).show()
                    pendingHtmlNote = null
                }
            }
        }
    }

    private var pendingTxtNote: Note? = null
    private val txtExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val note = pendingTxtNote
            if (note != null) {
                lifecycleScope.launch {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        viewModel.exportNoteToTxt(note, stream)
                    }
                    Toast.makeText(this@MainActivity, getString(R.string.export_txt_saved), Toast.LENGTH_SHORT).show()
                    pendingTxtNote = null
                }
            }
        }
    }

    /** Запустить печать заметки через системный PrintManager. */
    fun printNote(note: Note) {
        try {
            NotePrinter(this).print(note, com.example.obsidiankeep.export.HtmlExporter())
            Toast.makeText(this, getString(R.string.export_print_started), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.export_error, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    /** Запуск экспорта в HTML. */
    fun launchExportHtml(note: Note) {
        pendingHtmlNote = note
        val safeTitle = note.title.ifEmpty { getString(R.string.editor_no_title) }.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        htmlExportLauncher.launch("$safeTitle.html")
    }

    /** Запуск экспорта в TXT. */
    fun launchExportTxt(note: Note) {
        pendingTxtNote = note
        val safeTitle = note.title.ifEmpty { getString(R.string.editor_no_title) }.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        txtExportLauncher.launch("$safeTitle.txt")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF1A1A1A.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xFF111111.toInt())
        )
        handleIntent(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            val navController = rememberNavController()
            var activeTab by remember { mutableStateOf("notes") }

            // PIN-блокировка: если PIN включён и приложение только что запустилось/разблокировалось
            val pinManager = remember { PinManager.get(this@MainActivity) }
            var isLocked by remember {
                mutableStateOf(pinManager.isPinEnabled() && pinManager.shouldLockNow())
            }

            // Перепроверяем блокировку при возврате в приложение (onResume).
            // Если прошло больше autoLock секунд — блокируем.
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        if (pinManager.isPinEnabled() && pinManager.shouldLockNow()) {
                            isLocked = true
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(pendingImportUri) {
                val uri = pendingImportUri ?: return@LaunchedEffect
                pendingImportUri = null
                val newId = viewModel.importMarkdownFromUri(uri)
                if (newId != null) {
                    navController.navigate("editor/$newId?isNew=false") { launchSingleTop = true }
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
                }
            }

            LaunchedEffect(pendingOpenNoteId) {
                val noteId = pendingOpenNoteId ?: return@LaunchedEffect
                pendingOpenNoteId = null
                navController.navigate("editor/$noteId?isNew=false") { launchSingleTop = true }
            }

            LaunchedEffect(pendingQuickText) {
                val text = pendingQuickText ?: return@LaunchedEffect
                pendingQuickText = null
                val newId = java.util.UUID.randomUUID().toString()
                viewModel.createNote(newId, getString(R.string.quick_capture), text, com.example.obsidiankeep.ui.theme.NoteColors.DEFAULT)
                navController.navigate("editor/$newId?isNew=true") { launchSingleTop = true }
            }

            if (isLocked) {
                LockScreen(
                    pinManager = pinManager,
                    onUnlocked = { result ->
                        isLocked = false
                        if (result == PinCheckResult.DECOY) {
                            // Decoy-сессия: не показываем защищённые папки.
                            // Repository уже фильтрует по unlockedFolderIds, поэтому просто блокируем биометрию.
                        }
                    },
                    onUseBiometric = {
                        authenticateBiometric(
                            onSuccess = {
                                pinManager.setDecoySession(false)
                                pinManager.setLastUnlockTime(System.currentTimeMillis())
                                isLocked = false
                            },
                            onFailure = { msg ->
                                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    biometricAvailable = true
                )
            } else {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            MainScreen(
                                viewModel = viewModel,
                                currentTab = activeTab,
                                onTabChange = { activeTab = it },
                                onNoteClick = { noteId ->
                                    navController.navigate("editor/$noteId?isNew=false") { launchSingleTop = true }
                                },
                                onFolderClick = { folderId, folderName ->
                                    val folder = viewModel.folders.value.find { it.id == folderId }
                                    val pinManager = PinManager.get(this@MainActivity)

                                    if (folder?.isProtected == true) {
                                        // Защищённая папка:
                                        //  - Если PIN включён → ВСЕГДА показываем PIN-диалог, даже если
                                        //    папка уже была разблокирована ранее. Это предотвращает ситуацию,
                                        //    когда пользователь разблокировал основным PIN, потом вышел,
                                        //    потом ввёл decoy — папка осталась бы разблокированной.
                                        //    Также принудительно залочиваем сессию перед диалогом.
                                        //  - Если PIN выключен → биометрия (как раньше)
                                        if (pinManager.isPinEnabled()) {
                                            // Принудительно залочить сессию этой папки перед запросом
                                            viewModel.lockFolderSession(folderId)
                                            requestFolderAccess(
                                                folderId = folderId,
                                                folderName = folderName,
                                                onUnlocked = {
                                                    viewModel.unlockFolderSession(folderId)
                                                    navController.navigate("folder/$folderId/$folderName")
                                                },
                                                onDecoy = {
                                                    // Decoy: открываем специальную невидимую папку "0" с
                                                    // "безобидными" заметками, НО под именем нажатой папки.
                                                    // Пользователь думает что открыл реальную папку,
                                                    // но видит только заметки из decoy-папки.
                                                    viewModel.ensureDecoyFolderExists()
                                                    navController.navigate("folder/${com.example.obsidiankeep.repository.NoteRepositoryImpl.DECOY_FOLDER_ID}/$folderName")
                                                },
                                                onBiometricFailure = { message ->
                                                    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        } else {
                                            // PIN выключен → биометрия
                                            val isAlreadyUnlocked = viewModel.unlockedFolderIds.value.contains(folderId)
                                            if (isAlreadyUnlocked) {
                                                navController.navigate("folder/$folderId/$folderName")
                                            } else {
                                                authenticateBiometric(
                                                    onSuccess = {
                                                        pinManager.setDecoySession(false)
                                                        viewModel.unlockFolderSession(folderId)
                                                        navController.navigate("folder/$folderId/$folderName")
                                                    },
                                                    onFailure = { message ->
                                                        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        navController.navigate("folder/$folderId/$folderName")
                                    }
                                },
                                onCreateNewNoteClick = {
                                    val newId = UUID.randomUUID().toString()
                                    navController.navigate("editor/$newId?isNew=true") {
                                        popUpTo("main") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onOpenTrash = { navController.navigate("trash") },
                                onOpenReminders = { navController.navigate("reminders") },
                                onOpenSettings = { navController.navigate("settings") },
                                onOpenSearch = { navController.navigate("search") },
                                onOpenTags = { navController.navigate("tags") },
                                onOpenGraph = { navController.navigate("graph") },
                                onOpenAudioNotes = { navController.navigate("audio_notes") },
                                onOpenAdvancedSearch = { navController.navigate("advanced_search") },
                                onExportZip = { launchExportZip() },
                                onImportZip = { launchImportZip() },
                                onImportMarkdown = { launchImportMarkdown() },
                                onImportVault = { launchImportVault() },
                                onToggleMirror = { toggleMirror() }
                            )
                        }
                        composable("trash") {
                            TrashScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("reminders") {
                            // Календарь заметок-напоминаний (заменил RemindersScreen).
                            // Фильтруем заметки из залоченных защищённых папок.
                            val foldersList = viewModel.folders.collectAsState().value
                            val unlockedIds = viewModel.unlockedFolderIds.collectAsState().value
                            val protectedLockedFolderIds = remember(foldersList, unlockedIds) {
                                foldersList.filter { it.isProtected && it.id !in unlockedIds }.map { it.id }.toSet()
                            }
                            val visibleNotes = viewModel.allNotes.collectAsState().value
                                .filter { it.folderId !in protectedLockedFolderIds }
                            CalendarScreen(
                                notes = visibleNotes,
                                onNoteClick = { id ->
                                    navController.navigate("editor/$id?isNew=false") { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() },
                                onCreateNoteForDay = { triggerAtMillis ->
                                    // Создаём новую заметку с напоминанием на указанный день
                                    val newId = UUID.randomUUID().toString()
                                    viewModel.createNote(newId, "", "", NoteColors.DEFAULT)
                                    viewModel.setReminder(newId, triggerAtMillis, "none")
                                    navController.navigate("editor/$newId?isNew=false") { launchSingleTop = true }
                                },
                                onDeleteNote = { id -> viewModel.deleteNote(id) }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onExportZip = { launchExportZip() },
                                onImportZip = { launchImportZip() },
                                onImportMarkdown = { launchImportMarkdown() },
                                onImportVault = { launchImportVault() },
                                onToggleMirror = { toggleMirror() }
                            )
                        }
                        composable("search") {
                            val allTags by viewModel.allTags.collectAsState(initial = emptyList())
                            // Скрываем из поиска заметки из защищённых папок, которые НЕ разблокированы
                            // в текущей сессии. Это prevents утечку содержимого через поиск.
                            val foldersList = viewModel.folders.collectAsState().value
                            val unlockedIds = viewModel.unlockedFolderIds.collectAsState().value
                            val protectedLockedFolderIds = remember(foldersList, unlockedIds) {
                                foldersList.filter { it.isProtected && it.id !in unlockedIds }.map { it.id }.toSet()
                            }
                            val visibleNotes = viewModel.allNotes.collectAsState().value
                                .filter { it.folderId !in protectedLockedFolderIds }
                            SearchScreen(
                                allNotes = visibleNotes,
                                allTags = allTags,
                                onNoteClick = { id ->
                                    navController.navigate("editor/$id?isNew=false") { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("advanced_search") {
                            val allTags by viewModel.allTags.collectAsState(initial = emptyList())
                            val foldersList = viewModel.folders.collectAsState().value
                            val unlockedIds = viewModel.unlockedFolderIds.collectAsState().value
                            val protectedLockedFolderIds = remember(foldersList, unlockedIds) {
                                foldersList.filter { it.isProtected && it.id !in unlockedIds }.map { it.id }.toSet()
                            }
                            val visibleNotes = viewModel.allNotes.collectAsState().value
                                .filter { it.folderId !in protectedLockedFolderIds }
                            AdvancedSearchScreen(
                                allNotes = visibleNotes,
                                allFolders = foldersList,
                                allTags = allTags,
                                onNoteClick = { id ->
                                    navController.navigate("editor/$id?isNew=false") { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("graph") {
                            val foldersList = viewModel.folders.collectAsState().value
                            val unlockedIds = viewModel.unlockedFolderIds.collectAsState().value
                            val protectedLockedFolderIds = remember(foldersList, unlockedIds) {
                                foldersList.filter { it.isProtected && it.id !in unlockedIds }.map { it.id }.toSet()
                            }
                            GraphScreen(
                                allNotes = viewModel.allNotes.collectAsState().value
                                    .filter { !it.isEncrypted && it.folderId !in protectedLockedFolderIds },
                                onNoteClick = { id ->
                                    navController.navigate("editor/$id?isNew=false") { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("audio_notes") {
                            val records by viewModel.audioRecords.collectAsState()
                            val recordingState by viewModel.recordingState.collectAsState()
                            val playbackState by viewModel.playbackState.collectAsState()
                            val playbackPositionMs by viewModel.playbackPositionMs.collectAsState()
                            val playbackDurationMs by viewModel.playbackDurationMs.collectAsState()
                            val recordingElapsedMs by viewModel.recordingElapsedMs.collectAsState()
                            val isRecording = recordingState is com.example.obsidiankeep.audio.RecordingState.Recording
                            val currentlyPlayingId = (playbackState as? com.example.obsidiankeep.audio.PlaybackState.Playing)?.recordId

                            AudioNotesScreen(
                                records = records,
                                currentlyPlayingId = currentlyPlayingId,
                                playbackPositionMs = playbackPositionMs,
                                playbackDurationMs = playbackDurationMs,
                                isRecording = isRecording,
                                recordingElapsedMs = recordingElapsedMs,
                                onPlay = { id -> viewModel.startAudioPlayback(id) },
                                onStopPlayback = { viewModel.stopAudioPlayback() },
                                onStartRecording = {
                                    requestMicrophoneThen { viewModel.startAudioRecording() }
                                },
                                onStopRecording = { viewModel.stopAudioRecording() },
                                onDelete = { id -> viewModel.deleteAudioRecord(id) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("markdown_help") {
                            MarkdownHelpScreen(onBack = { navController.popBackStack() })
                        }
                        composable("tags") {
                            val allTags by viewModel.allTags.collectAsState(initial = emptyList())
                            TagsScreen(
                                allTags = allTags,
                                notesForTagFlow = { tagId -> viewModel.getNotesForTag(tagId) },
                                onNoteClick = { id ->
                                    navController.navigate("editor/$id?isNew=false") { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("folder/{folderId}/{folderName}") { backStackEntry ->
                            val folderId = backStackEntry.arguments?.getString("folderId") ?: ""
                            val folderName = backStackEntry.arguments?.getString("folderName") ?: ""

                            DisposableEffect(key1 = folderId) {
                                onDispose { viewModel.lockFolderSession(folderId) }
                            }

                            FolderScreen(
                                folderId = folderId,
                                folderName = folderName,
                                viewModel = viewModel,
                                onNoteClick = { routeWithArgs ->
                                    navController.navigate("editor/$routeWithArgs") { launchSingleTop = true }
                                },
                                onBack = {
                                    activeTab = "folders"
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(
                            route = "editor/{noteId}?isNew={isNew}&folderId={folderId}",
                            arguments = listOf(
                                navArgument("noteId") { type = NavType.StringType },
                                navArgument("isNew") { type = NavType.BoolType; defaultValue = false },
                                navArgument("folderId") { type = NavType.StringType; nullable = true; defaultValue = null }
                            ),
                            enterTransition = {
                                androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(220))
                            },
                            exitTransition = {
                                androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(150))
                            },
                            popEnterTransition = {
                                androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(220))
                            },
                            popExitTransition = {
                                androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(150))
                            }
                        ) { backStackEntry ->
                            val noteId = backStackEntry.arguments?.getString("noteId") ?: ""
                            val isNew = backStackEntry.arguments?.getBoolean("isNew") ?: false
                            val parentFolderId = backStackEntry.arguments?.getString("folderId")

                            EditorScreen(
                                noteId = noteId,
                                isNewNote = isNew,
                                initialFolderId = parentFolderId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onExportClick = { title, markdownText ->
                                    shareNoteAsMarkdownFile(title, markdownText)
                                },
                                onExportPdf = { note ->
                                    pendingPdfNote = note
                                    val safeTitle = note.title.ifEmpty { getString(R.string.editor_no_title) }.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                                    pdfExportLauncher.launch("$safeTitle.pdf")
                                },
                                onExportHtml = { note -> launchExportHtml(note) },
                                onExportTxt = { note -> launchExportTxt(note) },
                                onPrintNote = { note -> printNote(note) },
                                onNoteClick = { linkNoteId ->
                                    val targetNote = viewModel.allNotes.value.find { it.id == linkNoteId }
                                    val folderId = targetNote?.folderId
                                    if (folderId != null) {
                                        val folder = viewModel.folders.value.find { it.id == folderId }
                                        val isProtected = folder?.isProtected == true
                                        val isUnlocked = viewModel.unlockedFolderIds.value.contains(folderId)
                                        if (isProtected && !isUnlocked) {
                                            authenticateBiometric(
                                                onSuccess = {
                                                    viewModel.unlockFolderSession(folderId)
                                                    navController.navigate("editor/$linkNoteId?isNew=false") { launchSingleTop = true }
                                                },
                                                onFailure = { msg ->
                                                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        } else {
                                            navController.navigate("editor/$linkNoteId?isNew=false") { launchSingleTop = true }
                                        }
                                    } else {
                                        navController.navigate("editor/$linkNoteId?isNew=false") { launchSingleTop = true }
                                    }
                                },
                                onOpenMarkdownHelp = { navController.navigate("markdown_help") },
                                onInsertImage = { onMarkdownReady ->
                                    // Запуск выбора изображения из галереи.
                                    // После выбора: файл сохраняется в filesDir/attachments/<noteId>/,
                                    // и в EditorScreen возвращается markdown-ссылка для вставки.
                                    launchImagePicker(noteId, onMarkdownReady)
                                }
                            )
                        }
                    }
                }
            } // end if (!isLocked) else-branch

            // Диалог ввода PIN для доступа к защищённой папке.
            // Появляется когда requestFolderAccess() решает использовать PIN (а не биометрию).
            // Читаем folderAccessRequest (это mutableStateOf — Compose подписан и перерисуется).
            val currentFolderRequest = folderAccessRequest
            if (currentFolderRequest != null) {
                FolderPinDialog(
                    pinManager = pinManager,
                    folderName = currentFolderRequest.folderName,
                    onMainPin = {
                        clearFolderAccessRequest()
                        currentFolderRequest.onUnlocked()
                    },
                    onDecoyPin = {
                        clearFolderAccessRequest()
                        currentFolderRequest.onDecoy()
                    },
                    onDismiss = {
                        clearFolderAccessRequest()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        // Запоминаем момент сворачивания — для авто-блокировки
        // (используем lastUnlock как "last active", чтобы shouldLockNow() возвращал true
        // после возвращения в приложение через N секунд)
    }

    override fun onResume() {
        super.onResume()
        // Если PIN включён и прошло больше autoLock секунд с момента последней разблокировки —
        // блокируем (setContent() прочитает isLocked=true)
        // Логика в setContent проверяет pinManager.shouldLockNow() каждый раз при пересоздании.
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val openNoteId = intent.getStringExtra("extra_open_note_id")
        if (openNoteId != null) {
            pendingOpenNoteId = openNoteId
            intent.removeExtra("extra_open_note_id")
            return
        }
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                pendingQuickText = text
                intent.action = null
                return
            }
        }
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> intent.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        if (uri != null) {
            pendingImportUri = uri
            intent.action = null
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Общая логика переключения зеркалирования для MainScreen и SettingsScreen.
     * Раньше дублировалась в двух местах — вынесена сюда.
     */
    private fun toggleMirror() {
        if (viewModel.isMirrorEnabled()) {
            viewModel.setMirrorEnabled(false)
            viewModel.setMirrorTreeUri(null)
            Toast.makeText(this, getString(R.string.mirror_off), Toast.LENGTH_SHORT).show()
        } else {
            mirrorTreeLauncher.launch(null)
        }
    }

    /** Запуск экспорта в zip. */
    private fun launchExportZip() {
        exportZipLauncher.launch("obsidian_keep_backup.zip")
    }

    /** Запуск импорта из zip. */
    private fun launchImportZip() {
        importZipLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
    }

    /** Запуск импорта одного .md файла. */
    private fun launchImportMarkdown() {
        importMdLauncher.launch(arrayOf("text/markdown", "text/plain", "application/octet-stream"))
    }

    /** Запуск импорта .md из выбранной папки (vault). */
    private fun launchImportVault() {
        vaultImportLauncher.launch(null)
    }

    private fun authenticateBiometric(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
        val biometricPrompt = androidx.biometric.BiometricPrompt(this, executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    runOnUiThread { onSuccess() }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    runOnUiThread {
                        if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED) {
                            onFailure(errString.toString())
                        } else {
                            onFailure(getString(R.string.biometric_canceled))
                        }
                    }
                }
            })

        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Pending-запрос на доступ к защищённой папке. Когда [requestFolderAccess] решает,
     * что нужен PIN-диалог, он кладёт сюда колбэки и выставляет [folderPinDialogState].
     * Composable в setContent читает состояние и показывает [FolderPinDialog].
     *
     * ВАЖНО: это mutableStateOf, чтобы Compose подписывался на изменения и показывал/прятал диалог.
     * Раньше был @Volatile var — Compose его не наблюдал, диалог не появлялся.
     */
    private data class FolderAccessRequest(
        val folderId: String,
        val folderName: String,
        val onUnlocked: () -> Unit,
        val onDecoy: () -> Unit
    )
    private val _folderAccessRequest = mutableStateOf<FolderAccessRequest?>(null)
    private val folderAccessRequest: FolderAccessRequest? get() = _folderAccessRequest.value

    /**
     * Маршрутизатор доступа к защищённой папке:
     *  - PIN включён в настройках → открываем FolderPinDialog (main/decoy PIN)
     *  - PIN выключен → биометрия
     */
    fun requestFolderAccess(
        folderId: String,
        folderName: String,
        onUnlocked: () -> Unit,
        onDecoy: () -> Unit,
        onBiometricFailure: (String) -> Unit
    ) {
        val pinManager = PinManager.get(this)
        if (pinManager.isPinEnabled()) {
            // Сначала сбрасываем decoy-сессию (если была) — новый запрос на доступ
            pinManager.setDecoySession(false)
            // Кладём запрос в mutableStateOf — Compose подписан и покажет диалог
            _folderAccessRequest.value = FolderAccessRequest(folderId, folderName, onUnlocked, onDecoy)
        } else {
            // PIN выключен → биометрия
            authenticateBiometric(
                onSuccess = {
                    pinManager.setDecoySession(false)
                    onUnlocked()
                },
                onFailure = onBiometricFailure
            )
        }
    }

    /** Скрыть PIN-диалог (вызывается после успеха/decoy/dismiss). */
    fun clearFolderAccessRequest() {
        _folderAccessRequest.value = null
    }

    fun shareNoteAsMarkdownFile(title: String, contentMarkdown: String) {
        shareNotesAsMarkdownFiles(listOf(title to contentMarkdown))
    }

    fun shareMultipleNotesAsMarkdownFiles(notesToExport: List<Note>) {
        shareNotesAsMarkdownFiles(notesToExport.map { it.title to it.content })
    }

    private fun shareNotesAsMarkdownFiles(items: List<Pair<String, String>>) {
        if (items.isEmpty()) return
        try {
            val untitled = getString(R.string.editor_no_title)
            val sanitizeRegex = "[\\\\/:*?\"<>|]".toRegex()
            val uriList = ArrayList<Uri>()
            items.forEach { (title, content) ->
                val safeTitle = title.ifEmpty { untitled }.replace(sanitizeRegex, "_")
                val cacheFile = File(cacheDir, "$safeTitle.md")
                cacheFile.writeText(content)
                uriList.add(FileProvider.getUriForFile(this, "$packageName.fileprovider", cacheFile))
            }

            val shareIntent = if (uriList.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/markdown"
                    putExtra(Intent.EXTRA_STREAM, uriList.first())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "text/markdown"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_notes_to_obsidian)))
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(applicationContext, getString(R.string.export_error, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: NoteViewModel,
    currentTab: String,
    onTabChange: (String) -> Unit,
    onNoteClick: (String) -> Unit,
    onFolderClick: (String, String) -> Unit,
    onCreateNewNoteClick: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenGraph: () -> Unit,
    onOpenAudioNotes: () -> Unit,
    onOpenAdvancedSearch: () -> Unit,
    onExportZip: () -> Unit,
    onImportZip: () -> Unit,
    onImportMarkdown: () -> Unit,
    onImportVault: () -> Unit,
    onToggleMirror: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? MainActivity }

    val notes by viewModel.rootNotes.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val globalSortOrder by viewModel.sortOrder.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState(initial = 2)

    var notesSortOrder by remember { mutableStateOf(globalSortOrder) }
    var foldersSortOrder by remember { mutableStateOf(globalSortOrder) }
    val activeSortOrder = if (currentTab == "notes") notesSortOrder else foldersSortOrder
    fun changeSortOrder(order: SortOrder) {
        if (currentTab == "notes") notesSortOrder = order else foldersSortOrder = order
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var effectiveQuery by remember { mutableStateOf("") }
    LaunchedEffect(searchQuery) {
        kotlinx.coroutines.delay(200)
        effectiveQuery = searchQuery
    }
    val mirrorEnabled = remember { mutableStateOf(viewModel.isMirrorEnabled()) }

    var showSortMenu by remember { mutableStateOf(false) }
    var showMoveMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    var selectedNoteIds by remember { mutableStateOf(setOf<String>()) }
    var selectedFolderIds by remember { mutableStateOf(setOf<String>()) }

    val isNoteSelectionMode = selectedNoteIds.isNotEmpty()
    val isFolderSelectionMode = selectedFolderIds.isNotEmpty()
    val isAnySelectionMode = isNoteSelectionMode || isFolderSelectionMode

    var lastClickTime by remember { mutableLongStateOf(0L) }

    val filteredNotes = remember(notes, effectiveQuery, notesSortOrder) {
        val baseList = notes.filter { note ->
            note.title.contains(effectiveQuery, ignoreCase = true)
        }
        when (notesSortOrder) {
            SortOrder.NEWEST -> baseList.sortedByDescending { it.updatedAt }
            SortOrder.OLDEST -> baseList.sortedBy { it.updatedAt }
            SortOrder.ALPHABETIC -> baseList.sortedBy { it.title.lowercase() }
            SortOrder.FAVORITES -> baseList.sortedWith(
                compareByDescending<Note> { it.isFavorite }.thenByDescending { it.updatedAt }
            )
        }
    }

    val filteredFolders = remember(folders, effectiveQuery, foldersSortOrder) {
        val baseList = folders.filter { folder ->
            folder.name.contains(effectiveQuery, ignoreCase = true)
        }
        val regularFolders = baseList.filter { !it.isProtected }
        val protectedFolders = baseList.filter { it.isProtected }

        val sortedRegular = when (foldersSortOrder) {
            SortOrder.NEWEST -> regularFolders.sortedByDescending { it.updatedAt }
            SortOrder.OLDEST -> regularFolders.sortedBy { it.updatedAt }
            SortOrder.ALPHABETIC -> regularFolders.sortedBy { it.name.lowercase() }
            SortOrder.FAVORITES -> regularFolders.sortedBy { it.name.lowercase() }
        }
        val sortedProtected = when (foldersSortOrder) {
            SortOrder.NEWEST -> protectedFolders.sortedByDescending { it.updatedAt }
            SortOrder.OLDEST -> protectedFolders.sortedBy { it.updatedAt }
            SortOrder.ALPHABETIC -> protectedFolders.sortedBy { it.name.lowercase() }
            SortOrder.FAVORITES -> protectedFolders.sortedBy { it.name.lowercase() }
        }
        sortedRegular + sortedProtected
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isAnySelectionMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                selectedNoteIds = emptySet()
                                selectedFolderIds = emptySet()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                        ) { Text(stringResource(R.string.btn_cancel), color = Color.White) }

                        Text(
                            text = stringResource(R.string.selected_count, if (currentTab == "notes") selectedNoteIds.count() else selectedFolderIds.count()),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (currentTab == "notes") {
                            FilledIconButton(
                                onClick = {
                                    val notesToExport = notes.filter { selectedNoteIds.contains(it.id) }
                                    if (notesToExport.isNotEmpty()) {
                                        activity?.shareMultipleNotesAsMarkdownFiles(notesToExport)
                                        selectedNoteIds = emptySet()
                                    }
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF8E24AA)),
                                modifier = Modifier.size(40.dp)
                            ) { Text("📤", fontSize = 18.sp) }

                            Box {
                                FilledIconButton(
                                    onClick = { showMoveMenu = true },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF333333)),
                                    modifier = Modifier.size(40.dp)
                                ) { Text("📁", fontSize = 18.sp) }

                                DropdownMenu(
                                    expanded = showMoveMenu,
                                    onDismissRequest = { showMoveMenu = false },
                                    modifier = Modifier.background(Color(0xFF1E1E1E))
                                ) {
                                    folders.forEach { folder ->
                                        DropdownMenuItem(
                                            text = { Text(folder.name, color = Color.White) },
                                            onClick = {
                                                viewModel.moveMultipleNotes(selectedNoteIds.toList(), folder.id)
                                                selectedNoteIds = emptySet()
                                                showMoveMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        FilledIconButton(
                            onClick = {
                                if (currentTab == "notes") {
                                    val deletedIds = selectedNoteIds.toList()
                                    viewModel.deleteMultipleNotes(deletedIds)
                                    selectedNoteIds = emptySet()
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.delete_count_notes, deletedIds.size),
                                            actionLabel = context.getString(R.string.btn_undo),
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreNotes(deletedIds)
                                        }
                                    }
                                } else {
                                    val deletedFolderIds = selectedFolderIds.toList()
                                    viewModel.deleteMultipleFolders(deletedFolderIds)
                                    selectedFolderIds = emptySet()
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.delete_count_folders, deletedFolderIds.size),
                                            actionLabel = context.getString(R.string.btn_undo),
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            deletedFolderIds.forEach { viewModel.restoreFolder(it) }
                                        }
                                    }
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935)),
                            modifier = Modifier.size(40.dp)
                        ) { Text("🗑", fontSize = 18.sp) }
                    }
                } else {
                    Text(
                        text = "Obsidian Keep ",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onTabChange("notes"); searchQuery = "" },
                                colors = ButtonDefaults.buttonColors(containerColor = if (currentTab == "notes") Color(0xFF333333) else Color.Transparent),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) { Text(stringResource(R.string.tab_notes), color = Color.White) }

                            Button(
                                onClick = { onTabChange("folders"); searchQuery = "" },
                                colors = ButtonDefaults.buttonColors(containerColor = if (currentTab == "folders") Color(0xFF333333) else Color.Transparent),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) { Text(stringResource(R.string.tab_folders), color = Color.White) }

                            Box(modifier = Modifier.padding(start = 4.dp)) {
                                FilledIconButton(
                                    onClick = { showSortMenu = true },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text(
                                        text = when (activeSortOrder) {
                                            SortOrder.NEWEST -> "⏳"
                                            SortOrder.OLDEST -> "⌛"
                                            SortOrder.ALPHABETIC -> "🔤"
                                            SortOrder.FAVORITES -> "⭐"
                                        },
                                        fontSize = 14.sp
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    modifier = Modifier.background(Color(0xFF1E1E1E))
                                ) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_newest), color = Color.White) }, onClick = { changeSortOrder(SortOrder.NEWEST); showSortMenu = false })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_oldest), color = Color.White) }, onClick = { changeSortOrder(SortOrder.OLDEST); showSortMenu = false })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_alphabetic), color = Color.White) }, onClick = { changeSortOrder(SortOrder.ALPHABETIC); showSortMenu = false })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_favorites), color = Color.White) }, onClick = { changeSortOrder(SortOrder.FAVORITES); showSortMenu = false })
                                }
                            }

                            Box {
                                FilledIconButton(
                                    onClick = { showOverflowMenu = true },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent),
                                    modifier = Modifier.size(32.dp)
                                ) { Text("⋮", fontSize = 16.sp) }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                    modifier = Modifier.background(Color(0xFF1E1E1E))
                                ) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_search), color = Color.White) }, onClick = { showOverflowMenu = false; onOpenAdvancedSearch() })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_tags), color = Color.White) }, onClick = { showOverflowMenu = false; onOpenTags() })
                                    DropdownMenuItem(text = { Text("🕸 " + stringResource(R.string.graph_title), color = Color.White) }, onClick = { showOverflowMenu = false; onOpenGraph() })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.audio_notes_title), color = Color.White) }, onClick = { showOverflowMenu = false; onOpenAudioNotes() })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.calendar_title), color = Color.White) }, onClick = { showOverflowMenu = false; onOpenReminders() })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_settings), color = Color.White) }, onClick = { showOverflowMenu = false; onOpenSettings() })
                                }
                            }
                        }
                    }
                }
            }

            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(text = stringResource(if (currentTab == "notes") R.string.search_notes else R.string.search_folders), color = Color.Gray) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1E1E1E), unfocusedContainerColor = Color(0xFF1E1E1E), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
            )

            Spacer(modifier = Modifier.height(8.dp))
            if (currentTab == "notes") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns.coerceIn(1, 4)),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        val isSelected = selectedNoteIds.contains(note.id)
                        val cardColor = remember(note.color) { Color(note.color) }
                        val neonBorderColor = remember(isSelected, note.color) { NoteColors.neonBorder(note.color, isSelected) }
                        val emptyNoteText = stringResource(R.string.editor_empty_note)
                        val previewText = if (note.isEncrypted) stringResource(R.string.protected_note) else note.content.ifEmpty { emptyNoteText }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .animateItem()
                                .border(width = if (isSelected) 3.dp else 0.dp, color = neonBorderColor, shape = RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onClick = { if (isNoteSelectionMode) { selectedNoteIds = if (isSelected) selectedNoteIds - note.id else selectedNoteIds + note.id } else { onNoteClick(note.id) } },
                                    onLongClick = { if (!isNoteSelectionMode) { selectedNoteIds = selectedNoteIds + note.id } }
                                ),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = cardColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = note.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    if (note.isFavorite) Text("★", color = Color(0xFFFB8C00), fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = previewText, color = Color(0xFFEEEEEE), fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredFolders, key = { it.id }) { folder ->
                        val isFolderSelected = selectedFolderIds.contains(folder.id)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isFolderSelected) Color(0xFF2A2A2A) else Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                .border(width = if (isFolderSelected) 2.dp else 0.dp, color = if (isFolderSelected) Color(0xFFBB86FC) else Color.Transparent, shape = RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (isFolderSelectionMode) {
                                            selectedFolderIds = if (isFolderSelected) selectedFolderIds - folder.id else selectedFolderIds + folder.id
                                        } else {
                                            onFolderClick(folder.id, folder.name)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isFolderSelectionMode) {
                                            selectedFolderIds = selectedFolderIds + folder.id
                                        }
                                    }
                                )
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = if (folder.isProtected) "🔒" else "📁", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = folder.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        if (!isAnySelectionMode) {
            FloatingActionButton(
                onClick = onOpenTrash,
                containerColor = Color(0xFFE53935),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) { Text(stringResource(R.string.btn_trash), modifier = Modifier.padding(horizontal = 16.dp), color = Color.White, fontWeight = FontWeight.Bold) }

            FloatingActionButton(
                onClick = {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClickTime > 400L) {
                        lastClickTime = currentTime
                        if (currentTab == "notes") onCreateNewNoteClick() else viewModel.createFolder(context.getString(R.string.folder_default_name, folders.size + 1))
                    }
                },
                containerColor = Color(0xFFBB86FC),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) { Text(text = stringResource(if (currentTab == "notes") R.string.add_note else R.string.add_folder), modifier = Modifier.padding(horizontal = 16.dp), color = Color.Black, fontWeight = FontWeight.Bold) }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp)
        )
    }
}
