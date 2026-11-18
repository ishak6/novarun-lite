package com.novarun.lite.settings

import android.content.Context
import java.io.File

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

class AppSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun themeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(preferences.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!) }
            .getOrDefault(ThemeMode.SYSTEM)

    fun setThemeMode(value: ThemeMode) {
        preferences.edit().putString(KEY_THEME, value.name).apply()
    }

    fun defaultPort(): Int = preferences.getInt(KEY_PORT, 8080).coerceIn(1024, 65535)

    fun setDefaultPort(value: Int) {
        require(value in 1024..65535)
        preferences.edit().putInt(KEY_PORT, value).apply()
    }

    fun autoSaveEnabled(): Boolean = preferences.getBoolean(KEY_AUTOSAVE, true)

    fun setAutoSaveEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_AUTOSAVE, value).apply()
    }

    fun autoSaveDelayMs(): Long = preferences.getInt(KEY_AUTOSAVE_DELAY, 900).coerceIn(250, 5_000).toLong()

    fun setAutoSaveDelayMs(value: Int) {
        require(value in 250..5_000)
        preferences.edit().putInt(KEY_AUTOSAVE_DELAY, value).apply()
    }

    fun lanAccessEnabled(): Boolean = preferences.getBoolean(KEY_LAN, false)

    fun setLanAccessEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_LAN, value).apply()
    }

    fun storageUsageBytes(): Long = directorySize(appContext.filesDir) + directorySize(appContext.cacheDir)

    fun clearCache(): Boolean = appContext.cacheDir.listFiles().orEmpty().all { it.deleteRecursively() }

    fun clearStaleImportDirectories(maxAgeMs: Long = 24L * 60L * 60L * 1000L): Int {
        val projects = File(appContext.filesDir, "projects")
        val cutoff = System.currentTimeMillis() - maxAgeMs
        return projects.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith(".import-") && it.lastModified() < cutoff }
            .count { it.deleteRecursively() }
    }

    fun projectDirectoryPath(): String = File(appContext.filesDir, "projects").absolutePath

    private fun directorySize(directory: File): Long =
        directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    companion object {
        private const val PREFERENCES = "novarun_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_PORT = "default_port"
        private const val KEY_AUTOSAVE = "auto_save"
        private const val KEY_AUTOSAVE_DELAY = "auto_save_delay_ms"
        private const val KEY_LAN = "lan_access_enabled"
    }
}
