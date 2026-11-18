package com.novarun.lite

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.novarun.lite.projects.ProjectStore
import com.novarun.lite.projects.ProjectSummary
import com.novarun.lite.files.ProjectFileEntry
import com.novarun.lite.files.ProjectFileStore
import com.novarun.lite.database.ProjectDatabaseStore
import com.novarun.lite.database.SqlQueryResult
import com.novarun.lite.server.LocalServerService
import com.novarun.lite.server.findLanIpv4Address
import com.novarun.lite.security.SecureTokenStore
import com.novarun.lite.settings.AppSettingsStore
import com.novarun.lite.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.Base64

private val Navy = Color(0xFF101827)
private val Blue = Color(0xFF4F8CFF)
private val Purple = Color(0xFF8B6DFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NovaRunApp() }
    }
}

private enum class MainTab(val label: String, val symbol: String) {
    HOME("Home", "⌂"),
    PROJECTS("Projects", "▣"),
    PREVIEW("Preview", "▷"),
    SERVER("Server", "◉"),
    SETTINGS("Settings", "⚙")
}

@Composable
private fun NovaRunApp() {
    var selectedTab by remember { mutableStateOf(0) }
    var previewFullscreen by remember { mutableStateOf(false) }
    var requestZipImport by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val settingsStore = remember(context) { AppSettingsStore(context.applicationContext) }
    var themeMode by remember { mutableStateOf(settingsStore.themeMode()) }
    val darkMode = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val tabs = MainTab.entries
    val serverController = remember(context) { LocalServerController(context.applicationContext) }
    val colors = if (darkMode) darkColorScheme(
        primary = Blue,
        secondary = Purple,
        background = Navy,
        surface = Color(0xFF192438),
        onBackground = Color(0xFFF4F6FC),
        onSurface = Color(0xFFF4F6FC)
    ) else lightColorScheme(primary = Blue, secondary = Purple)

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!previewFullscreen) NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Text(tab.symbol) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { padding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(padding),
                color = MaterialTheme.colorScheme.background
            ) {
                when (tabs[selectedTab]) {
                    MainTab.HOME -> HomeScreen(
                        onProjects = { selectedTab = 1 },
                        onImport = { requestZipImport = true; selectedTab = 1 },
                        onPreview = { selectedTab = 2 },
                        onServer = { selectedTab = 3 }
                    )
                    MainTab.PROJECTS -> ProjectManagerScreen(
                        openImport = requestZipImport,
                        onImportOpened = { requestZipImport = false }
                    )
                    MainTab.PREVIEW -> ProjectPreviewScreen(
                        fullscreen = previewFullscreen,
                        onFullscreenChange = { previewFullscreen = it }
                    )
                    MainTab.SERVER -> ServerDashboardScreen(serverController, settingsStore)
                    MainTab.SETTINGS -> SettingsScreen(
                        settings = settingsStore,
                        themeMode = themeMode,
                        onThemeChange = {
                            themeMode = it
                            settingsStore.setThemeMode(it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onProjects: () -> Unit,
    onImport: () -> Unit,
    onPreview: () -> Unit,
    onServer: () -> Unit
) {
    val context = LocalContext.current
    val projectStore = remember(context) { ProjectStore(context.applicationContext) }
    var projects by remember { mutableStateOf(emptyList<ProjectSummary>()) }
    LaunchedEffect(projectStore) {
        projects = withContext(Dispatchers.IO) { projectStore.listProjects() }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("NovaRun Lite", style = MaterialTheme.typography.headlineMedium)
        Text(
            "A lightweight local development workspace",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Workspace", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Projects: ${projects.size}")
                Text(if (LocalServerService.isRunning) "Server: running" else "Server: stopped")
                if (projects.isEmpty()) {
                    Text("No recent projects yet.")
                } else {
                    Text("Recent projects", style = MaterialTheme.typography.labelLarge)
                    projects.take(3).forEach { Text("• ${it.name}") }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onProjects, modifier = Modifier.weight(1f)) { Text("Projects") }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import ZIP") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPreview, modifier = Modifier.weight(1f)) { Text("Preview") }
                OutlinedButton(onClick = onServer, modifier = Modifier.weight(1f)) { Text("Start Server") }
            }
        }
    }
}

@Composable
private fun ProjectManagerScreen(openImport: Boolean, onImportOpened: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ProjectStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf(emptyList<ProjectSummary>()) }
    var projectName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var exportTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var openedProject by remember { mutableStateOf<ProjectSummary?>(null) }
    var editorFile by remember { mutableStateOf<String?>(null) }
    var databaseOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(store) {
        projects = withContext(Dispatchers.IO) { store.listProjects() }
    }
    fun refresh() {
        scope.launch { projects = withContext(Dispatchers.IO) { store.listProjects() } }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val name = uri.lastPathSegment?.substringAfterLast(':') ?: "Imported Project.zip"
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("Could not open selected ZIP file")
                        input.use { store.importZip(it, name) }
                    }
                }
                result.onSuccess {
                    message = "Imported ${it.name}"
                    refresh()
                }.onFailure {
                    message = it.message ?: "ZIP import failed"
                }
            }
        }
    }

    LaunchedEffect(openImport) {
        if (openImport) {
            importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            onImportOpened()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        val target = exportTarget
        if (uri != null && target != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: error("Could not create backup file")
                        output.use { store.exportZip(target.directory, it) }
                    }
                }
                result.onSuccess {
                    message = "Backup saved"
                }.onFailure {
                    message = it.message ?: "Backup failed"
                }
            }
        }
        exportTarget = null
    }

    if (openedProject != null) {
        val activeProject = openedProject!!
        if (databaseOpen) {
            ProjectDatabaseScreen(
                project = activeProject,
                onBack = { databaseOpen = false }
            )
        } else if (editorFile != null) {
            CodeEditorScreen(
                project = activeProject,
                relativePath = editorFile!!,
                onBack = { editorFile = null }
            )
        } else {
            ProjectFilesScreen(
                project = activeProject,
                onBack = { openedProject = null },
                onOpenFile = { editorFile = it },
                onOpenDatabase = { databaseOpen = true }
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Projects", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { renameTarget = null; projectName = "My Project" }) { Text("New Project") }
            OutlinedButton(onClick = {
                importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }) { Text("Import ZIP") }
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search projects") },
            singleLine = true
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        openedProject?.let { selected ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Open project: ${selected.name}", style = MaterialTheme.typography.titleMedium)
                    Text("Location: app-private project storage")
                    Text("Files: ${selected.fileCount}")
                }
            }
        }
        if (projects.isEmpty()) {
            Text("No projects yet. Create a project or import a ZIP archive.")
        } else {
            val filteredProjects = projects.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredProjects, key = { it.directory.absolutePath }) { project ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(project.name, style = MaterialTheme.typography.titleMedium)
                            Text("${project.fileCount} files")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { openedProject = project }) { Text("Open") }
                                TextButton(onClick = { renameTarget = project; projectName = project.name }) {
                                    Text("Rename")
                                }
                                TextButton(onClick = {
                                    exportTarget = project
                                    exportLauncher.launch("${project.name}.zip")
                                }) { Text("Backup") }
                                TextButton(onClick = { deleteTarget = project }) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (projectName.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { projectName = ""; renameTarget = null },
            title = { Text(if (renameTarget == null) "Create project" else "Rename project") },
            text = {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Project name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        val target = renameTarget
                        if (target == null) store.createProject(projectName)
                        else store.renameProject(target.directory, projectName)
                    }.onSuccess {
                        message = "Project saved"
                        renameTarget = null
                        projectName = ""
                        refresh()
                    }.onFailure {
                        message = it.message ?: "Project operation failed"
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { projectName = ""; renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${target.name}?") },
            text = { Text("This permanently deletes the project stored inside NovaRun Lite.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { store.deleteProject(target.directory) }
                        .onSuccess { message = "Project deleted"; refresh() }
                        .onFailure { message = it.message ?: "Delete failed" }
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}


@Composable
private fun ProjectFilesScreen(
    project: ProjectSummary,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenDatabase: () -> Unit
) {
    val store = remember(project.directory.absolutePath) { ProjectFileStore(project.directory) }
    var currentDirectory by remember(project.directory.absolutePath) { mutableStateOf("") }
    var entries by remember(project.directory.absolutePath) { mutableStateOf(store.list()) }
    var dialogMode by remember { mutableStateOf("none") }
    var targetEntry by remember { mutableStateOf<ProjectFileEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectFileEntry?>(null) }
    var input by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() { entries = store.list(currentDirectory) }
    fun childPath(name: String): String =
        if (currentDirectory.isEmpty()) name else "$currentDirectory/$name"

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("‹ Projects") }
            Text(project.name, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onOpenDatabase) { Text("Database") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (currentDirectory.isNotEmpty()) {
                TextButton(onClick = {
                    currentDirectory = currentDirectory.substringBeforeLast('/', "")
                    refresh()
                }) { Text("Up") }
            }
            Text(if (currentDirectory.isEmpty()) "/" else "/$currentDirectory")
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search files and folders") },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { dialogMode = "new-file"; input = "" }) { Text("New File") }
            OutlinedButton(onClick = { dialogMode = "new-folder"; input = "" }) { Text("New Folder") }
        }
        selectedFile?.let { Text("Selected: $it") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        val visibleEntries = if (searchQuery.isBlank()) entries else store.search(searchQuery)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleEntries, key = { it.relativePath }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            (if (entry.isDirectory) "▰ " else "▤ ") + entry.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(if (entry.isDirectory) "Folder" else "${entry.sizeBytes} bytes")
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (entry.isDirectory) {
                                TextButton(onClick = { currentDirectory = entry.relativePath; refresh() }) {
                                    Text("Open")
                                }
                            } else {
                                TextButton(onClick = { selectedFile = entry.relativePath; onOpenFile(entry.relativePath) }) { Text("Open") }
                                TextButton(onClick = {
                                    targetEntry = entry
                                    input = entry.name
                                    dialogMode = "rename"
                                }) { Text("Rename") }
                                TextButton(onClick = {
                                    targetEntry = entry
                                    input = ""
                                    dialogMode = "copy"
                                }) { Text("Copy") }
                                TextButton(onClick = {
                                    targetEntry = entry
                                    input = ""
                                    dialogMode = "move"
                                }) { Text("Move") }
                            }
                            TextButton(onClick = { deleteTarget = entry }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }


    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${target.name}?") },
            text = { Text("This permanently removes the selected item from this project.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { store.delete(target.relativePath) }
                        .onSuccess { message = "Deleted ${target.name}"; refresh() }
                        .onFailure { message = it.message ?: "Delete failed" }
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    if (dialogMode != "none") {
        val title = when (dialogMode) {
            "new-file" -> "Create file"
            "new-folder" -> "Create folder"
            "rename" -> "Rename"
            "copy" -> "Copy to path"
            else -> "Move to path"
        }
        AlertDialog(
            onDismissRequest = { dialogMode = "none" },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(if (dialogMode == "copy" || dialogMode == "move") "Destination path" else "Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        when (dialogMode) {
                            "new-file" -> store.createFile(childPath(input))
                            "new-folder" -> store.createFolder(childPath(input))
                            "rename" -> store.rename(targetEntry!!.relativePath, input)
                            "copy" -> store.copyFile(targetEntry!!.relativePath, input)
                            "move" -> store.moveFile(targetEntry!!.relativePath, input)
                        }
                    }.onSuccess {
                        message = "Operation completed"
                        dialogMode = "none"
                        refresh()
                    }.onFailure {
                        message = it.message ?: "File operation failed"
                    }
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { dialogMode = "none" }) { Text("Cancel") }
            }
        )
    }
}


@Composable
private fun CodeEditorScreen(
    project: ProjectSummary,
    relativePath: String,
    onBack: () -> Unit
) {
    val store = remember(project.directory.absolutePath) { ProjectFileStore(project.directory) }
    val initialContent = remember(relativePath) { runCatching { store.readText(relativePath) } }
    val loadError = initialContent.exceptionOrNull()?.message
    val editorState = remember(relativePath) { TextFieldState(initialContent.getOrDefault("")) }
    val editorScrollState = rememberScrollState()
    var content by remember(relativePath) { mutableStateOf(initialContent.getOrDefault("")) }
    var savedContent by remember(relativePath) { mutableStateOf(content) }
    LaunchedEffect(editorState) {
        snapshotFlow { editorState.text.toString() }.collect { content = it }
    }
    val context = LocalContext.current
    val settings = remember(context) { AppSettingsStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var autoSave by remember { mutableStateOf(settings.autoSaveEnabled()) }
    var saveMessage by remember { mutableStateOf("Loaded") }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var showFind by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val extension = relativePath.substringAfterLast('.', "").lowercase()
    val lineCount = content.count { it == '\n' } + 1
    val syntax = remember(extension) { CodeSyntaxOutputTransformation(extension) }

    BackHandler(enabled = content != savedContent) {
        showDiscardDialog = true
    }

    suspend fun saveNow(): Boolean {
        val currentText = editorState.text.toString()
        val result = withContext(Dispatchers.IO) {
            runCatching { store.writeTextAtomic(relativePath, currentText) }
        }
        result.onSuccess {
            content = currentText
            savedContent = currentText
            saveMessage = "Saved"
        }.onFailure {
            saveMessage = it.message ?: "Save failed"
        }
        return result.isSuccess
    }

    LaunchedEffect(content, autoSave, relativePath) {
        if (loadError == null && autoSave && content != savedContent) {
            delay(settings.autoSaveDelayMs())
            val result = withContext(Dispatchers.IO) {
                runCatching { store.writeTextAtomic(relativePath, content) }
            }
            result.onSuccess {
                savedContent = content
                saveMessage = "Auto-saved"
            }.onFailure {
                saveMessage = it.message ?: "Auto-save failed"
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                if (content != savedContent) showDiscardDialog = true else onBack()
            }) { Text("‹ Files") }
            Text(relativePath, style = MaterialTheme.typography.titleMedium)
        }
        loadError?.let { Text("Cannot edit this file: $it", color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { scope.launch { saveNow() } }, enabled = loadError == null) { Text("Save") }
            OutlinedButton(onClick = { showFind = true }) { Text("Find / Replace") }
            Text(if (content == savedContent) "Saved" else "Unsaved")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Auto Save")
            Switch(checked = autoSave, onCheckedChange = {
                autoSave = it
                settings.setAutoSaveEnabled(it)
            })
            Text(saveMessage, color = MaterialTheme.colorScheme.primary)
        }
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(
                modifier = Modifier.width(30.dp)
                    .verticalScroll(editorScrollState)
                    .padding(top = 10.dp)
            ) {
                Text(
                    (1..lineCount).joinToString("\n"),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                )
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (content.isEmpty()) {
                    Text("Start writing code…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    state = editorState,
                    enabled = loadError == null,
                    modifier = Modifier.fillMaxSize(),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    lineLimits = TextFieldLineLimits.MultiLine(),
                    outputTransformation = syntax,
                    scrollState = editorScrollState,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }


    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Unsaved changes") },
            text = { Text("Save your changes before leaving this file?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (saveNow()) {
                            showDiscardDialog = false
                            onBack()
                        }
                    }
                }) { Text("Save and Exit") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showDiscardDialog = false
                        onBack()
                    }) { Text("Discard") }
                    TextButton(onClick = { showDiscardDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    if (showFind) {
        AlertDialog(
            onDismissRequest = { showFind = false },
            title = { Text("Find and Replace") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = findText,
                        onValueChange = { findText = it },
                        label = { Text("Find") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        label = { Text("Replace with") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (findText.isNotEmpty()) {
                        val updated = editorState.text.toString().replace(findText, replaceText)
                        editorState.edit { replace(0, length, updated) }
                    }
                    showFind = false
                }) { Text("Replace All") }
            },
            dismissButton = { TextButton(onClick = { showFind = false }) { Text("Close") } }
        )
    }
}

private class CodeSyntaxOutputTransformation(private val extension: String) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val source = asCharSequence()
        val commentPattern = if (extension == "py") {
            Regex("#[^\\n]*")
        } else {
            Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/")
        }
        commentPattern.findAll(source).forEach {
            addStyle(SpanStyle(color = Color(0xFF70A982)), it.range.first, it.range.last + 1)
        }
        Regex("\"[^\"]*\"|'[^']*'").findAll(source).forEach {
            addStyle(SpanStyle(color = Color(0xFFE3A66B)), it.range.first, it.range.last + 1)
        }
        val keywords = when (extension) {
            "py" -> "def|class|return|if|elif|else|for|while|import|from|as|try|except|with|True|False|None|async|await"
            "html" -> "html|head|body|div|span|script|style|title|meta|link|button|input"
            "css" -> "important|media|keyframes|font-face"
            else -> "const|let|var|function|return|if|else|class|new|import|export|from|async|await|true|false|null|undefined"
        }
        Regex("\\b($keywords)\\b").findAll(source).forEach {
            addStyle(SpanStyle(color = Color(0xFF8E9FFF)), it.range.first, it.range.last + 1)
        }
        if (extension == "html" || extension == "htm") {
            Regex("</?[A-Za-z][^>]*>").findAll(source).forEach {
                addStyle(SpanStyle(color = Color(0xFF69B7D6)), it.range.first, it.range.last + 1)
            }
        }
    }
}


private fun generateAccessToken(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private class LocalServerController(private val context: Context) {
    fun start(project: ProjectSummary, port: Int, bindAddress: String, authToken: String?) {
        launchServerAction(LocalServerService.ACTION_START, project, port, bindAddress, authToken)
    }

    fun restart(project: ProjectSummary, port: Int, bindAddress: String, authToken: String?) {
        launchServerAction(LocalServerService.ACTION_RESTART, project, port, bindAddress, authToken)
    }

    private fun launchServerAction(
        action: String,
        project: ProjectSummary,
        port: Int,
        bindAddress: String,
        authToken: String?
    ) {
        val intent = Intent(context, LocalServerService::class.java)
            .setAction(action)
            .putExtra(LocalServerService.EXTRA_PROJECT_PATH, project.directory.absolutePath)
            .putExtra(LocalServerService.EXTRA_PORT, port)
            .putExtra(LocalServerService.EXTRA_BIND_ADDRESS, bindAddress)
            .putExtra(LocalServerService.EXTRA_AUTH_TOKEN, authToken)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop() {
        context.startService(
            Intent(context, LocalServerService::class.java)
                .setAction(LocalServerService.ACTION_STOP)
        )
    }
}

@Composable
private fun ServerDashboardScreen(controller: LocalServerController, settings: AppSettingsStore) {
    val context = LocalContext.current
    val projectStore = remember(context) { ProjectStore(context.applicationContext) }
    var projects by remember { mutableStateOf(emptyList<ProjectSummary>()) }
    LaunchedEffect(projectStore) {
        projects = withContext(Dispatchers.IO) { projectStore.listProjects() }
    }
    var selectedProject by remember { mutableStateOf(projects.firstOrNull()) }
    LaunchedEffect(projects) {
        if (selectedProject == null) selectedProject = projects.firstOrNull()
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var portText by remember { mutableStateOf(settings.defaultPort().toString()) }
    var running by remember { mutableStateOf(LocalServerService.isRunning) }
    var starting by remember { mutableStateOf(false) }
    var lanEnabled by remember { mutableStateOf(settings.lanAccessEnabled()) }
    var lanAddress by remember { mutableStateOf(findLanIpv4Address()) }
    val tokenStore = remember(context) { SecureTokenStore(context.applicationContext) }
    var accessToken by remember { mutableStateOf(tokenStore.load().orEmpty()) }
    val logs = remember { mutableStateListOf<String>() }

    fun addLog(value: String) {
        logs.add(0, value)
        while (logs.size > 30) logs.removeAt(logs.lastIndex)
    }

    val receiver = remember(context) {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    LocalServerService.ACTION_LOG -> {
                        addLog(intent.getStringExtra(LocalServerService.EXTRA_MESSAGE) ?: "Server log")
                    }
                    LocalServerService.ACTION_STATE -> {
                        running = intent.getBooleanExtra(LocalServerService.EXTRA_RUNNING, false)
                        starting = false
                        if (running) {
                            accessToken = tokenStore.load().orEmpty()
                        } else {
                            accessToken = ""
                            tokenStore.clear()
                        }
                    }
                }
            }
        }
    }
    DisposableEffect(context, receiver) {
        val filter = IntentFilter().apply {
            addAction(LocalServerService.ACTION_LOG)
            addAction(LocalServerService.ACTION_STATE)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    fun startServer() {
        val port = portText.toIntOrNull()
        if (port == null || port !in 1024..65535) {
            addLog("Choose a port between 1024 and 65535")
            return
        }
        val bindAddress = if (lanEnabled) lanAddress else "127.0.0.1"
        if (bindAddress == null) {
            addLog("No private LAN IPv4 address found. Connect to Wi-Fi or start a hotspot.")
            return
        }
        settings.setDefaultPort(port)
        val token = if (lanEnabled) generateAccessToken() else null
        accessToken = token.orEmpty()
        if (token == null) tokenStore.clear() else tokenStore.save(token)
        starting = true
        runCatching {
            controller.start(selectedProject!!, port, bindAddress, token)
        }.onFailure {
            starting = false
            accessToken = ""
            tokenStore.clear()
            addLog(it.message ?: "Could not start server")
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startServer() else addLog("Notification permission is required to keep the server visible")
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Local Server", style = MaterialTheme.typography.headlineMedium)
        androidx.compose.foundation.layout.Box {
            OutlinedButton(onClick = { menuExpanded = true }) {
                Text(selectedProject?.name ?: "Select project")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                projects.forEach { project ->
                    DropdownMenuItem(
                        text = { Text(project.name) },
                        onClick = { selectedProject = project; menuExpanded = false }
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("LAN / Wi-Fi / Hotspot")
            Switch(
                checked = lanEnabled,
                onCheckedChange = {
                    lanEnabled = it
                    settings.setLanAccessEnabled(it)
                    accessToken = ""
                },
                enabled = !running && !starting
            )
            TextButton(onClick = { lanAddress = findLanIpv4Address() }, enabled = !running) {
                Text("Detect IP")
            }
        }
        Text("Detected private IP: ${lanAddress ?: "not available"}")
        OutlinedTextField(
            value = portText,
            onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) portText = it },
            label = { Text("Port (1024–65535)") },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !running && !starting && selectedProject != null,
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startServer()
                    }
                }
            ) { Text(if (starting) "Starting…" else "Start") }
            OutlinedButton(
                enabled = running && !starting && selectedProject != null,
                onClick = {
                    val port = portText.toIntOrNull()
                    val bindAddress = if (lanEnabled) lanAddress else "127.0.0.1"
                    if (port == null || port !in 1024..65535 || bindAddress == null) {
                        addLog("Check port and network address before restarting")
                    } else {
                        val token = if (lanEnabled) accessToken.ifBlank { generateAccessToken() } else null
                        accessToken = token.orEmpty()
                        if (token == null) tokenStore.clear() else tokenStore.save(token)
                        settings.setDefaultPort(port)
                        starting = true
                        runCatching {
                            controller.restart(selectedProject!!, port, bindAddress, token)
                        }.onFailure {
                            starting = false
                            addLog(it.message ?: "Could not restart server")
                        }
                    }
                }
            ) { Text("Restart") }
            OutlinedButton(
                enabled = running || starting,
                onClick = {
                    controller.stop()
                    running = false
                    starting = false
                    accessToken = ""
                    tokenStore.clear()
                }
            ) { Text("Stop") }
        }
        Text(if (running) "Status: Running" else "Status: Stopped")
        if (running && !lanEnabled) {
            Text("Local URL: http://127.0.0.1:$portText")
        }
        if (running && lanEnabled) {
            Text("LAN URL: http://${lanAddress ?: "unknown"}:$portText")
            Text("Access token: $accessToken")
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("NovaRun access token", accessToken))
                addLog("Access token copied")
            }) { Text("Copy access token") }
            Text(
                "Security warning: LAN traffic uses HTTP, not TLS. The token and project data are not encrypted in transit. Use only on a trusted private Wi-Fi or hotspot network.",
                color = MaterialTheme.colorScheme.error
            )
        }
        Text("Server Logs", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(logs.toList()) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}


@Composable
private fun ProjectDatabaseScreen(project: ProjectSummary, onBack: () -> Unit) {
    val store = remember(project.directory.absolutePath) { ProjectDatabaseStore(project.directory) }
    val scope = rememberCoroutineScope()
    var runningQuery by remember { mutableStateOf(false) }
    var sql by remember(project.directory.absolutePath) {
        mutableStateOf("SELECT name FROM sqlite_master WHERE type = 'table';")
    }
    var result by remember { mutableStateOf<SqlQueryResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(store) {
        onDispose { store.close() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("‹ Files") }
            Text("SQLite • ${project.name}", style = MaterialTheme.typography.titleLarge)
        }
        Text("Database file: ${ProjectDatabaseStore.DATABASE_NAME}")
        OutlinedTextField(
            value = sql,
            onValueChange = { sql = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("SQL statement") },
            minLines = 4
        )
        Button(
            enabled = !runningQuery,
            onClick = {
                runningQuery = true
                scope.launch {
                    val queryResult = withContext(Dispatchers.IO) { runCatching { store.execute(sql) } }
                    queryResult.onSuccess { result = it; error = null }
                        .onFailure { error = it.message ?: "SQL execution failed" }
                    runningQuery = false
                }
            }
        ) { Text(if (runningQuery) "Running…" else "Run SQL") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        result?.let { query ->
            Text(query.message, color = MaterialTheme.colorScheme.primary)
            if (query.columns.isNotEmpty()) {
                Text(query.columns.joinToString(" | "), style = MaterialTheme.typography.labelLarge)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(query.rows) { row ->
                        Text(row.joinToString(" | ") { it ?: "NULL" })
                    }
                }
                if (query.truncated) Text("Result truncated to 200 rows")
            }
        }
    }
}

@Composable
private fun ProjectPreviewScreen(
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val projectStore = remember(context) { ProjectStore(context.applicationContext) }
    var projects by remember { mutableStateOf(emptyList<ProjectSummary>()) }
    LaunchedEffect(projectStore) {
        projects = withContext(Dispatchers.IO) { projectStore.listProjects() }
    }
    var selectedProject by remember { mutableStateOf(projects.firstOrNull()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var htmlPath by remember(selectedProject?.directory?.absolutePath) { mutableStateOf("index.html") }
    var javaScriptEnabled by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }
    var previewError by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = fullscreen) { onFullscreenChange(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(if (fullscreen) 4.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!fullscreen) {
            Text("Live Preview", style = MaterialTheme.typography.headlineMedium)
            androidx.compose.foundation.layout.Box {
                OutlinedButton(onClick = { menuExpanded = true }) {
                    Text(selectedProject?.name ?: "Select project")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    projects.forEach { project ->
                        DropdownMenuItem(
                            text = { Text(project.name) },
                            onClick = {
                                selectedProject = project
                                menuExpanded = false
                                previewError = null
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = htmlPath,
                onValueChange = { htmlPath = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("HTML file path") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { refreshKey++; previewError = null }, enabled = selectedProject != null) {
                    Text("Refresh")
                }
                OutlinedButton(onClick = { onFullscreenChange(true) }, enabled = selectedProject != null) {
                    Text("Fullscreen")
                }
                Text("JavaScript")
                Switch(checked = javaScriptEnabled, onCheckedChange = { javaScriptEnabled = it })
            }
            previewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onFullscreenChange(false) }) { Text("Exit Fullscreen") }
                Button(onClick = { refreshKey++; previewError = null }) { Text("Refresh") }
            }
            previewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        val activeProject = selectedProject
        if (activeProject == null) {
            Text("Create or import a project before previewing.")
        } else {
            ProjectPreviewWebView(
                project = activeProject,
                htmlPath = htmlPath,
                javaScriptEnabled = javaScriptEnabled,
                refreshKey = refreshKey,
                onError = { previewError = it },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

@Composable
private fun ProjectPreviewWebView(
    project: ProjectSummary,
    htmlPath: String,
    javaScriptEnabled: Boolean,
    refreshKey: Int,
    onError: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(project.directory.absolutePath) { ProjectFileStore(project.directory) }
    val webView = remember(project.directory.absolutePath) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.databaseEnabled = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.setSafeBrowsingEnabled(true)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse {
                    val uri = request.url
                    if (uri.host != PREVIEW_HOST) {
                        return blockedResponse()
                    }
                    val path = uri.path.orEmpty().removePrefix("/").ifBlank { "index.html" }
                    return runCatching {
                        val mime = previewMimeType(path)
                        val encoding = if (mime.startsWith("text/") ||
                            mime == "application/javascript" || mime == "application/json"
                        ) "UTF-8" else null
                        WebResourceResponse(mime, encoding, store.openInputStream(path))
                    }.getOrElse {
                        WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            404,
                            "Not Found",
                            emptyMap(),
                            java.io.ByteArrayInputStream("Preview file not found".toByteArray())
                        )
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean = request.url.host != PREVIEW_HOST

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {
                    if (request.isForMainFrame) onError(error.description?.toString() ?: "Preview load failed")
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse
                ) {
                    if (request.isForMainFrame) {
                        onError("Preview returned HTTP ${errorResponse.statusCode}")
                    }
                }
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier,
        update = { view ->
            view.settings.javaScriptEnabled = javaScriptEnabled
            val cleanPath = htmlPath.trim().ifBlank { "index.html" }
            val loadKey = "${project.directory.absolutePath}|$cleanPath|$refreshKey|$javaScriptEnabled"
            if (view.tag != loadKey) {
                view.tag = loadKey
                onError(null)
                view.loadUrl("https://$PREVIEW_HOST/${Uri.encode(cleanPath, "/")}")
            }
        }
    )
}

private const val PREVIEW_HOST = "novarun.local"

private fun blockedResponse() = WebResourceResponse(
    "text/plain",
    "UTF-8",
    403,
    "Blocked",
    emptyMap(),
    java.io.ByteArrayInputStream("External requests are blocked in preview".toByteArray())
)

private fun previewMimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "html", "htm" -> "text/html"
    "css" -> "text/css"
    "js", "mjs" -> "application/javascript"
    "json" -> "application/json"
    "txt", "md" -> "text/plain"
    "svg" -> "image/svg+xml"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "ico" -> "image/x-icon"
    else -> "application/octet-stream"
}

@Composable
private fun PlaceholderScreen(title: String, detail: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Text(detail, modifier = Modifier.padding(18.dp))
        }
    }
}


@Composable
private fun SettingsScreen(
    settings: AppSettingsStore,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    var portText by remember { mutableStateOf(settings.defaultPort().toString()) }
    var autoSave by remember { mutableStateOf(settings.autoSaveEnabled()) }
    var delayText by remember { mutableStateOf(settings.autoSaveDelayMs().toString()) }
    var lanAccess by remember { mutableStateOf(settings.lanAccessEnabled()) }
    var storageBytes by remember { mutableStateOf(0L) }
    var maintenanceBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(settings) {
        storageBytes = withContext(Dispatchers.IO) { settings.storageUsageBytes() }
    }
    var message by remember { mutableStateOf<String?>(null) }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.values().forEach { mode ->
                if (themeMode == mode) {
                    Button(onClick = { onThemeChange(mode) }) { Text(mode.name) }
                } else {
                    OutlinedButton(onClick = { onThemeChange(mode) }) { Text(mode.name) }
                }
            }
        }

        Text("Server", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = portText,
            onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) portText = it },
            label = { Text("Default port (1024–65535)") },
            singleLine = true
        )
        OutlinedButton(onClick = {
            val port = portText.toIntOrNull()
            if (port != null && port in 1024..65535) {
                settings.setDefaultPort(port)
                message = "Default port saved"
            } else {
                message = "Enter a port from 1024 to 65535"
            }
        }) { Text("Save Port") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Allow LAN mode by default")
            Switch(checked = lanAccess, onCheckedChange = {
                lanAccess = it
                settings.setLanAccessEnabled(it)
            })
        }

        Text("Editor", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Auto Save")
            Switch(checked = autoSave, onCheckedChange = {
                autoSave = it
                settings.setAutoSaveEnabled(it)
            })
        }
        OutlinedTextField(
            value = delayText,
            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) delayText = it },
            label = { Text("Auto Save delay (250–5000 ms)") },
            singleLine = true
        )
        OutlinedButton(onClick = {
            val delay = delayText.toIntOrNull()
            if (delay != null && delay in 250..5_000) {
                settings.setAutoSaveDelayMs(delay)
                message = "Auto Save delay saved"
            } else {
                message = "Delay must be between 250 and 5000 ms"
            }
        }) { Text("Save Auto Save Delay") }

        Text("Runtime Management", style = MaterialTheme.typography.titleMedium)
        Text("Node.js: not connected — optional Termux connector requires approval")
        Text("Python: not connected — optional Termux connector requires approval")
        Text("SQLite: native Android SQLite available")

        Text("Storage", style = MaterialTheme.typography.titleMedium)
        Text("App storage used: ${formatBytes(storageBytes)}")
        Text("Project directory: ${settings.projectDirectoryPath()}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = !maintenanceBusy,
                onClick = {
                    maintenanceBusy = true
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) {
                            val cleared = settings.clearCache()
                            val removed = settings.clearStaleImportDirectories()
                            Triple(cleared, removed, settings.storageUsageBytes())
                        }
                        storageBytes = outcome.third
                        message = if (outcome.first) {
                            "Cache cleared; removed ${outcome.second} stale import folder(s)"
                        } else {
                            "Some cache files could not be removed"
                        }
                        maintenanceBusy = false
                    }
                }
            ) { Text(if (maintenanceBusy) "Cleaning…" else "Clean Cache / Temp") }
            TextButton(
                enabled = !maintenanceBusy,
                onClick = {
                    scope.launch {
                        storageBytes = withContext(Dispatchers.IO) { settings.storageUsageBytes() }
                    }
                }
            ) { Text("Refresh Usage") }
        }

        Text("App Information", style = MaterialTheme.typography.titleMedium)
        Text("NovaRun Lite $versionName")
        Text("Native Android • Minimum Android 10 (API 29)")
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
    return String.format(java.util.Locale.US, "%.2f MB", kb / 1024.0)
}

