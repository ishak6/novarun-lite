package com.novarun.lite

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.novarun.lite.projects.ProjectStore
import com.novarun.lite.projects.ProjectSummary
import com.novarun.lite.files.ProjectFileEntry
import com.novarun.lite.files.ProjectFileStore
import kotlinx.coroutines.delay

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
    SETTINGS("Settings", "⚙")
}

@Composable
private fun NovaRunApp() {
    var selectedTab by remember { mutableStateOf(0) }
    var darkMode by remember { mutableStateOf(true) }
    val tabs = MainTab.entries
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
                NavigationBar {
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
                        onPreview = { selectedTab = 2 }
                    )
                    MainTab.PROJECTS -> ProjectManagerScreen()
                    MainTab.PREVIEW -> PlaceholderScreen(
                        title = "Live Preview",
                        detail = "HTML, CSS and JavaScript preview is scheduled for M4."
                    )
                    MainTab.SETTINGS -> SettingsScreen(
                        darkMode = darkMode,
                        onThemeChange = { darkMode = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onProjects: () -> Unit, onPreview: () -> Unit) {
    val context = LocalContext.current
    val projects = remember { ProjectStore(context.applicationContext).listProjects() }
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
                Text("Server: stopped")
                if (projects.isEmpty()) {
                    Text("No recent projects yet.")
                } else {
                    Text("Recent projects", style = MaterialTheme.typography.labelLarge)
                    projects.take(3).forEach { Text("• ${it.name}") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onProjects, modifier = Modifier.weight(1f)) { Text("Projects") }
            OutlinedButton(onClick = onProjects, modifier = Modifier.weight(1f)) { Text("Import ZIP") }
            Button(onClick = onPreview, modifier = Modifier.weight(1f)) { Text("Preview") }
        }
    }
}

@Composable
private fun ProjectManagerScreen() {
    val context = LocalContext.current
    val store = remember { ProjectStore(context.applicationContext) }
    var projects by remember { mutableStateOf(store.listProjects()) }
    var projectName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var exportTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var openedProject by remember { mutableStateOf<ProjectSummary?>(null) }
    var editorFile by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() { projects = store.listProjects() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val name = uri.lastPathSegment?.substringAfterLast(':') ?: "Imported Project.zip"
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("Could not open selected ZIP file")
                input.use { store.importZip(it, name) }
            }.onSuccess {
                message = "Imported ${it.name}"
                refresh()
            }.onFailure {
                message = it.message ?: "ZIP import failed"
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        val target = exportTarget
        if (uri != null && target != null) {
            runCatching {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("Could not create backup file")
                output.use { store.exportZip(target.directory, it) }
            }.onSuccess {
                message = "Backup saved"
            }.onFailure {
                message = it.message ?: "Backup failed"
            }
        }
        exportTarget = null
    }

    if (openedProject != null) {
        val activeProject = openedProject!!
        if (editorFile != null) {
            CodeEditorScreen(
                project = activeProject,
                relativePath = editorFile!!,
                onBack = { editorFile = null }
            )
        } else {
            ProjectFilesScreen(
                project = activeProject,
                onBack = { openedProject = null },
                onOpenFile = { editorFile = it }
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
    onOpenFile: (String) -> Unit
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
    var content by remember(relativePath) {
        mutableStateOf(runCatching { store.readText(relativePath) }.getOrDefault(""))
    }
    var savedContent by remember(relativePath) { mutableStateOf(content) }
    var autoSave by remember { mutableStateOf(true) }
    var saveMessage by remember { mutableStateOf("Loaded") }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var showFind by remember { mutableStateOf(false) }
    val extension = relativePath.substringAfterLast('.', "").lowercase()
    val lineCount = content.count { it == '\n' } + 1
    val syntax = remember(extension) { CodeSyntaxTransformation(extension) }

    fun saveNow() {
        runCatching { store.writeTextAtomic(relativePath, content) }
            .onSuccess {
                savedContent = content
                saveMessage = "Saved"
            }
            .onFailure {
                saveMessage = it.message ?: "Save failed"
            }
    }

    LaunchedEffect(content, autoSave, relativePath) {
        if (autoSave && content != savedContent) {
            delay(900)
            runCatching { store.writeTextAtomic(relativePath, content) }
                .onSuccess {
                    savedContent = content
                    saveMessage = "Auto-saved"
                }
                .onFailure {
                    saveMessage = it.message ?: "Auto-save failed"
                }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("‹ Files") }
            Text(relativePath, style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { saveNow() }) { Text("Save") }
            OutlinedButton(onClick = { showFind = true }) { Text("Find / Replace") }
            Text(if (content == savedContent) "Saved" else "Unsaved")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Auto Save")
            Switch(checked = autoSave, onCheckedChange = { autoSave = it })
            Text(saveMessage, color = MaterialTheme.colorScheme.primary)
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                (1..lineCount).joinToString("\n"),
                modifier = Modifier.padding(top = 16.dp),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.weight(1f).fillMaxSize(),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                visualTransformation = syntax,
                placeholder = { Text("Start writing code…") }
            )
        }
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
                    if (findText.isNotEmpty()) content = content.replace(findText, replaceText)
                    showFind = false
                }) { Text("Replace All") }
            },
            dismissButton = { TextButton(onClick = { showFind = false }) { Text("Close") } }
        )
    }
}

private class CodeSyntaxTransformation(private val extension: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        val commentPattern = if (extension == "py") {
            Regex("#[^\\n]*")
        } else {
            Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/")
        }
        commentPattern.findAll(text).forEach {
            builder.addStyle(SpanStyle(color = Color(0xFF70A982)), it.range.first, it.range.last + 1)
        }
        Regex("\"[^\"]*\"|'[^']*'").findAll(text).forEach {
            builder.addStyle(SpanStyle(color = Color(0xFFE3A66B)), it.range.first, it.range.last + 1)
        }
        val keywords = when (extension) {
            "py" -> "def|class|return|if|elif|else|for|while|import|from|as|try|except|with|True|False|None|async|await"
            "html" -> "html|head|body|div|span|script|style|title|meta|link|button|input"
            "css" -> "important|media|keyframes|font-face"
            else -> "const|let|var|function|return|if|else|class|new|import|export|from|async|await|true|false|null|undefined"
        }
        Regex("\\b($keywords)\\b").findAll(text).forEach {
            builder.addStyle(SpanStyle(color = Color(0xFF8E9FFF)), it.range.first, it.range.last + 1)
        }
        if (extension == "html" || extension == "htm") {
            Regex("</?[A-Za-z][^>]*>").findAll(text).forEach {
                builder.addStyle(SpanStyle(color = Color(0xFF69B7D6)), it.range.first, it.range.last + 1)
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
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
private fun SettingsScreen(darkMode: Boolean, onThemeChange: (Boolean) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Theme")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onThemeChange(true) }) { Text("Dark") }
            Button(onClick = { onThemeChange(false) }) { Text("Light") }
        }
        Text(if (darkMode) "Dark theme selected" else "Light theme selected")
    }
}
