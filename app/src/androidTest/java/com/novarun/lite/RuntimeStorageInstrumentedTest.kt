package com.novarun.lite

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novarun.lite.database.ProjectDatabaseStore
import com.novarun.lite.projects.ProjectStore
import com.novarun.lite.security.SecureTokenStore
import com.novarun.lite.settings.AppSettingsStore
import com.novarun.lite.settings.ThemeMode
import com.novarun.lite.server.LocalServerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.util.UUID
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class RuntimeStorageInstrumentedTest {

    @Test
    fun projectZipImportCreatesFilesAndBlocksTraversal() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ProjectStore(context)
        val archive = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("site/index.html"))
                zip.write("<h1>NovaRun</h1>".toByteArray())
                zip.closeEntry()
            }
        }
        val imported = store.importZip(ByteArrayInputStream(archive.toByteArray()), "M1-Import.zip")
        try {
            assertTrue(File(imported.directory, "site/index.html").isFile)
        } finally {
            store.deleteProject(imported.directory)
        }

        val malicious = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("../outside.txt"))
                zip.write("blocked".toByteArray())
                zip.closeEntry()
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.importZip(ByteArrayInputStream(malicious.toByteArray()), "M1-Malicious.zip")
        }
        assertFalse(File(context.filesDir, "outside.txt").exists())
    }

    @Test
    fun sqliteCreatesWritesAndReadsProjectDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "sqlite-${UUID.randomUUID()}").apply { mkdirs() }
        val store = ProjectDatabaseStore(root)
        try {
            store.execute("CREATE TABLE tasks (id INTEGER PRIMARY KEY, title TEXT NOT NULL)")
            store.execute("INSERT INTO tasks(title) VALUES ('NovaRun')")
            val result = store.execute("SELECT title FROM tasks")
            assertEquals(listOf("title"), result.columns)
            assertEquals("NovaRun", result.rows.single().single())
            assertThrows(IllegalArgumentException::class.java) {
                store.execute("ATTACH DATABASE '/tmp/outside.db' AS external")
            }
            assertThrows(IllegalArgumentException::class.java) {
                store.execute("-- external path\nATTACH DATABASE '/tmp/outside.db' AS external")
            }
            assertEquals("ATTACH", store.execute("SELECT 'ATTACH'").rows.single().single())
        } finally {
            store.close()
            root.deleteRecursively()
        }
    }



    @Test
    fun appSettingsPersistThemePortAndEditorPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = AppSettingsStore(context)
        settings.setThemeMode(ThemeMode.DARK)
        settings.setDefaultPort(9090)
        settings.setAutoSaveEnabled(false)
        settings.setAutoSaveDelayMs(1500)
        settings.setLanAccessEnabled(true)

        assertEquals(ThemeMode.DARK, settings.themeMode())
        assertEquals(9090, settings.defaultPort())
        assertEquals(false, settings.autoSaveEnabled())
        assertEquals(1500L, settings.autoSaveDelayMs())
        assertEquals(true, settings.lanAccessEnabled())

        settings.setThemeMode(ThemeMode.SYSTEM)
        settings.setDefaultPort(8080)
        settings.setAutoSaveEnabled(true)
        settings.setAutoSaveDelayMs(900)
        settings.setLanAccessEnabled(false)
    }

    @Test
    fun foregroundServiceStartsAndStopsLocalServer() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "service-${UUID.randomUUID()}").apply { mkdirs() }
        File(root, "index.html").writeText("service-ok")
        val port = ServerSocket(0).use { it.localPort }
        val startIntent = android.content.Intent(context, LocalServerService::class.java)
            .setAction(LocalServerService.ACTION_START)
            .putExtra(LocalServerService.EXTRA_PROJECT_PATH, root.absolutePath)
            .putExtra(LocalServerService.EXTRA_PORT, port)
            .putExtra(LocalServerService.EXTRA_BIND_ADDRESS, "127.0.0.1")

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                ContextCompat.startForegroundService(activity, startIntent)
            }
            val startDeadline = System.currentTimeMillis() + 8_000
            while (!LocalServerService.isRunning && System.currentTimeMillis() < startDeadline) {
                Thread.sleep(50)
            }
            assertEquals(true, LocalServerService.isRunning)

            context.startService(
                android.content.Intent(context, LocalServerService::class.java)
                    .setAction(LocalServerService.ACTION_STOP)
            )
            val stopDeadline = System.currentTimeMillis() + 5_000
            while (LocalServerService.isRunning && System.currentTimeMillis() < stopDeadline) {
                Thread.sleep(50)
            }
            assertEquals(false, LocalServerService.isRunning)
        } finally {
            context.startService(
                android.content.Intent(context, LocalServerService::class.java)
                    .setAction(LocalServerService.ACTION_STOP)
            )
            scenario.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun secureTokenStoreEncryptsAndClearsToken() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SecureTokenStore(context)
        try {
            store.clear()
            store.save("test-access-token")
            assertEquals("test-access-token", store.load())
            store.clear()
            assertNull(store.load())
        } finally {
            store.clear()
        }
    }
}
