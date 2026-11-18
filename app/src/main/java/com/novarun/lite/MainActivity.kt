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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.novarun.lite.projects.ProjectStore
import com.novarun.lite.projects.ProjectSummary

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
                Text("Project manager: available")
                Text("Server: stopped")
                Text("Open Projects to create or import a project.")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onProjects, modifier = Modifier.weight(1f)) { Text("Projects") }
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

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Projects", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { projectName = "My Project" }) { Text("New Project") }
            OutlinedButton(onClick = {
                importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }) { Text("Import ZIP") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (projects.isEmpty()) {
            Text("No projects yet. Create a project or import a ZIP archive.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(projects, key = { it.directory.absolutePath }) { project ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(project.name, style = MaterialTheme.typography.titleMedium)
                            Text("${project.fileCount} files")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            onDismissRequest = { projectName = "" },
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
