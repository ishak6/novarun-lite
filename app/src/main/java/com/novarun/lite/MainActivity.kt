package com.novarun.lite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Navy = Color(0xFF101827)
private val Blue = Color(0xFF4F8CFF)
private val Purple = Color(0xFF8B6DFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NovaRunTheme {
                NovaRunApp()
            }
        }
    }
}

@Composable
private fun NovaRunTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Blue,
        secondary = Purple,
        background = Navy,
        surface = Color(0xFF192438),
        onPrimary = Color.White,
        onBackground = Color(0xFFF4F6FC),
        onSurface = Color(0xFFF4F6FC)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

private enum class MainTab(val label: String, val symbol: String) {
    HOME("Home", "⌂"),
    PROJECTS("Projects", "▣"),
    PREVIEW("Preview", "▷"),
    SETTINGS("Settings", "⚙")
}

@Composable
private fun NovaRunApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var darkMode by remember { mutableStateOf(true) }
    val tabs = MainTab.entries

    MaterialTheme(colorScheme = if (darkMode) darkColorScheme(
        primary = Blue,
        secondary = Purple,
        background = Navy,
        surface = Color(0xFF192438),
        onBackground = Color(0xFFF4F6FC),
        onSurface = Color(0xFFF4F6FC)
    ) else lightColorScheme(primary = Blue, secondary = Purple)) {
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
                    MainTab.PROJECTS -> PlaceholderScreen(
                        title = "Projects",
                        detail = "Project manager and secure ZIP import are scheduled for M1."
                    )
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
                Text("Project manager is being built.")
                Text("Server: stopped")
                Text("Projects: 0")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onProjects, modifier = Modifier.weight(1f)) {
                Text("Projects")
            }
            Button(onClick = onPreview, modifier = Modifier.weight(1f)) {
                Text("Preview")
            }
        }
        Text(
            "M0 foundation • M1 project manager next",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { onThemeChange(true) }) { Text("Dark") }
            Button(onClick = { onThemeChange(false) }) { Text("Light") }
        }
        Text(if (darkMode) "Dark theme selected" else "Light theme selected")
    }
}
