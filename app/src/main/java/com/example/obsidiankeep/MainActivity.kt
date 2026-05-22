package com.example.obsidiankeep // Проверьте, что этот package совпадает с вашим!

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.obsidiankeep.data.Folder
import com.example.obsidiankeep.data.Note
import java.util.UUID

class MainActivity : FragmentActivity() {
    private val viewModel: NoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this

        setContent {
            val navController = rememberNavController()
            var activeTab by remember { mutableStateOf("notes") }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF121212)
            ) {
                NavHost(navController = navController, startDestination = "main") {

                    composable("main") {
                        MainScreen(
                            viewModel = viewModel,
                            currentTab = activeTab,
                            onTabChange = { activeTab = it },
                            onNoteClick = { noteId ->
                                navController.navigate("editor/$noteId?isNew=false") {
                                    launchSingleTop = true
                                }
                            },
                            onFolderClick = { folderId, folderName ->
                                val folder = viewModel.folders.value.find { it.id == folderId }
                                val isAlreadyUnlocked = viewModel.unlockedFolderIds.value.contains(folderId)

                                if (folder?.isProtected == true && !isAlreadyUnlocked) {
                                    authenticateBiometric(
                                        onSuccess = {
                                            viewModel.unlockFolderSession(folderId)
                                            navController.navigate("folder/$folderId/$folderName")
                                        },
                                        onFailure = { message ->
                                            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                                        }
                                    )
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
                            }
                        )
                    }

                    composable("folder/{folderId}/{folderName}") { backStackEntry ->
                        val folderId = backStackEntry.arguments?.getString("folderId") ?: ""
                        val folderName = backStackEntry.arguments?.getString("folderName") ?: ""

                        DisposableEffect(key1 = folderId) {
                            onDispose {
                                viewModel.lockFolderSession(folderId)
                            }
                        }

                        FolderScreen(
                            folderId = folderId,
                            folderName = folderName,
                            viewModel = viewModel,
                            onNoteClick = { routeWithArgs ->
                                navController.navigate("editor/$routeWithArgs") {
                                    launchSingleTop = true
                                }
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
                        enterTransition = { androidx.compose.animation.EnterTransition.None },
                        exitTransition = { androidx.compose.animation.ExitTransition.None },
                        popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                        popExitTransition = { androidx.compose.animation.ExitTransition.None }
                    ) { backStackEntry ->
                        val noteId = backStackEntry.arguments?.getString("noteId") ?: ""
                        val isNew = backStackEntry.arguments?.getBoolean("isNew") ?: false
                        val parentFolderId = backStackEntry.arguments?.getString("folderId")

                        EditorScreen(
                            noteId = noteId,
                            isNewNote = isNew,
                            initialFolderId = parentFolderId,
                            viewModel = viewModel,
                            onBack = {
                                navController.popBackStack()
                            },
                            onExportClick = { title, markdownText ->
                                activity.shareNoteAsMarkdownFile(title, markdownText)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun authenticateBiometric(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    runOnUiThread { onSuccess() }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    runOnUiThread {
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                            onFailure(errString.toString())
                        } else {
                            onFailure("Доступ ограничен: отмена")
                        }
                    }
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Доступ к скрытой папке")
            .setSubtitle("Приложите палец к сканеру")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    fun shareNoteAsMarkdownFile(title: String, contentMarkdown: String) {
        try {
            val safeTitle = title.ifEmpty { "Без названия" }.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            val fileName = "$safeTitle.md"
            val cacheFile = java.io.File(cacheDir, fileName)
            cacheFile.writeText(contentMarkdown)

            val fileUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                cacheFile
            )

            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Запускаем окно выбора приложения
            startActivity(android.content.Intent.createChooser(shareIntent, "Экспорт в Obsidian (ПК)"))
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(applicationContext, "Ошибка экспорта: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareMultipleNotesAsMarkdownFiles(notesToExport: List<com.example.obsidiankeep.data.Note>) {
        try {
            val uriList = ArrayList<android.net.Uri>()

            // Циклом создаем отдельный файл для каждой выделенной заметки
            for (note in notesToExport) {
                val safeTitle = note.title.ifEmpty { "Без названия" }.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                val fileName = "$safeTitle.md"
                val cacheFile = java.io.File(cacheDir, fileName)
                cacheFile.writeText(note.content)

                // Генерируем URI через ваш FileProvider
                val fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    cacheFile
                )
                uriList.add(fileUri)
            }

            if (uriList.isNotEmpty()) {
                // Используем ACTION_SEND_MULTIPLE для отправки пачки файлов сразу
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "text/markdown"
                    putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uriList)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) // Даем права на чтение файлов
                }

                // Открываем окно выбора приложения (в Obsidian они прилетят как разные файлы)
                startActivity(android.content.Intent.createChooser(shareIntent, "Экспорт заметок в Obsidian"))
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(applicationContext, "Ошибка экспорта: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
} // <--- КЛАСС MAIN_ACTIVITY ИДЕАЛЬНО И НАДЁЖНО ЗАКРЫЛСЯ СТРОГО ЗДЕСЬ!
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: NoteViewModel,
    currentTab: String,
    onTabChange: (String) -> Unit,
    onNoteClick: (String) -> Unit,
    onFolderClick: (String, String) -> Unit,
    onCreateNewNoteClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? MainActivity

    val notes by viewModel.rootNotes.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val activeSortOrder by viewModel.sortOrder.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoveMenu by remember { mutableStateOf(false) }

    var selectedNoteIds by remember { mutableStateOf(setOf<String>()) }
    var selectedFolderIds by remember { mutableStateOf(setOf<String>()) }

    val isNoteSelectionMode = selectedNoteIds.isNotEmpty()
    val isFolderSelectionMode = selectedFolderIds.isNotEmpty()
    val isAnySelectionMode = isNoteSelectionMode || isFolderSelectionMode

    var lastClickTime by remember { mutableLongStateOf(0L) }

    val filteredNotes = remember(notes, searchQuery, activeSortOrder) {
        val baseList = notes.filter { note ->
            note.title.contains(searchQuery, ignoreCase = true) ||
                    note.content.contains(searchQuery, ignoreCase = true)
        }
        when (activeSortOrder) {
            SortOrder.NEWEST -> baseList.sortedByDescending { it.updatedAt }
            SortOrder.OLDEST -> baseList.sortedBy { it.updatedAt }
            SortOrder.ALPHABETIC -> baseList.sortedBy { it.title.lowercase() }
        }
    }

    val filteredFolders = remember(folders, searchQuery, activeSortOrder) {
        val baseList = folders.filter { folder ->
            folder.name.contains(searchQuery, ignoreCase = true)
        }
        when (activeSortOrder) {
            SortOrder.NEWEST -> baseList.sortedByDescending { it.updatedAt }
            SortOrder.OLDEST -> baseList.sortedBy { it.updatedAt }
            SortOrder.ALPHABETIC -> baseList.sortedBy { it.name.lowercase() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

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
                        ) { Text("Отмена", color = Color.White) }

                        Text(
                            text = "Выбрано: ${if (currentTab == "notes") selectedNoteIds.count() else selectedFolderIds.count()}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (currentTab == "notes") {
                            // --- НОВАЯ ФИОЛЕТОВАЯ КНОПКА ЭКСПОРТА МНОЖЕСТВА ЗАМЕТОК ---
                            FilledIconButton(
                                onClick = {
                                    // Получаем список всех выделенных объектов заметок
                                    val notesToExport = notes.filter { selectedNoteIds.contains(it.id) }

                                    if (notesToExport.isNotEmpty()) {
                                        // Вызываем новый метод паблишинга множества файлов
                                        activity?.shareMultipleNotesAsMarkdownFiles(notesToExport)

                                        // Сбрасываем выделение, чтобы закрыть панель выбора
                                        selectedNoteIds = emptySet()
                                    }
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF6A5ACD)), // Наш фиолетовый цвет
                                modifier = Modifier.width(40.dp).height(40.dp)
                            ) {
                                Text("📤", fontSize = 18.sp)
                            }

                            // Существующая кнопка переноса заметок в папку (без изменений)
                            Box {
                                FilledIconButton(
                                    onClick = { showMoveMenu = true },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF333333)),
                                    modifier = Modifier.width(40.dp).height(40.dp)
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

                        // Существующая кнопка удаления в корзину (без изменений)
                        FilledIconButton(
                            onClick = {
                                if (currentTab == "notes") {
                                    viewModel.deleteMultipleNotes(selectedNoteIds.toList())
                                    selectedNoteIds = emptySet()
                                } else {
                                    viewModel.deleteMultipleFolders(selectedFolderIds.toList())
                                    selectedFolderIds = emptySet()
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF900C3F)),
                            modifier = Modifier.width(40.dp).height(40.dp)
                        ) { Text("🗑", fontSize = 18.sp) }
                    }
                } else {
                    Text(
                        text = "Obsidian Keep",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

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
                        ) { Text("Заметки", color = Color.White) }

                        Button(
                            onClick = { onTabChange("folders"); searchQuery = "" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (currentTab == "folders") Color(0xFF333333) else Color.Transparent),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("Папки", color = Color.White) }

                        Box(modifier = Modifier.padding(start = 4.dp)) {
                            FilledIconButton(
                                onClick = { showSortMenu = true },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF2C2C2C)),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text(
                                    text = when (activeSortOrder) {
                                        SortOrder.NEWEST -> "⏳"
                                        SortOrder.OLDEST -> "⌛"
                                        SortOrder.ALPHABETIC -> "🔤"
                                    },
                                    fontSize = 14.sp
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(Color(0xFF1E1E1E))
                            ) {
                                DropdownMenuItem(text = { Text("⏳ Сначала новые", color = Color.White) }, onClick = { viewModel.changeSortOrder(SortOrder.NEWEST); showSortMenu = false })
                                DropdownMenuItem(text = { Text("⌛ Сначала старые", color = Color.White) }, onClick = { viewModel.changeSortOrder(SortOrder.OLDEST); showSortMenu = false })
                                DropdownMenuItem(text = { Text("🔤 По алфавиту (А-Я)", color = Color.White) }, onClick = { viewModel.changeSortOrder(SortOrder.ALPHABETIC); showSortMenu = false })
                            }
                        }
                    }
                }
            }
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(text = if (currentTab == "notes") "Поиск по заметкам..." else "Поиск папок...", color = Color.Gray) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1E1E1E), unfocusedContainerColor = Color(0xFF1E1E1E), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (currentTab == "notes") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        val isSelected = selectedNoteIds.contains(note.id)

                        val neonBorderColor = if (isSelected) {
                            try {
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(note.color, hsv)
                                hsv[1] = 0.95f
                                hsv[2] = 1.0f
                                Color(android.graphics.Color.HSVToColor(hsv))
                            } catch (e: Exception) {
                                Color(0xFFBB86FC)
                            }
                        } else {
                            Color.Transparent
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .border(width = if (isSelected) 3.dp else 0.dp, color = neonBorderColor, shape = RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onClick = { if (isNoteSelectionMode) { selectedNoteIds = if (isSelected) selectedNoteIds - note.id else selectedNoteIds + note.id } else { onNoteClick(note.id) } },
                                    onLongClick = { if (!isNoteSelectionMode) { selectedNoteIds = selectedNoteIds + note.id } }
                                ),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(note.color))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = note.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = note.content.ifEmpty { "Пустая заметка..." }, color = Color(0xFFCCCCCC), fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
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
                onClick = {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClickTime > 400L) {
                        lastClickTime = currentTime
                        if (currentTab == "notes") onCreateNewNoteClick() else viewModel.createFolder("Папка ${folders.size + 1}")
                    }
                },
                containerColor = Color(0xFFBB86FC),
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
            ) { Text(text = if (currentTab == "notes") "+ Заметка" else "+ Папка", modifier = Modifier.padding(horizontal = 16.dp), color = Color.Black, fontWeight = FontWeight.Bold) }
        }
    }
}