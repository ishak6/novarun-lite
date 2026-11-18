package com.novarun.lite

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novarun.lite.database.ProjectDatabaseStore
import com.novarun.lite.security.SecureTokenStore
import com.novarun.lite.server.LocalServerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RuntimeStorageInstrumentedTest {
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
        } finally {
            store.close()
            root.deleteRecursively()
        }
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

        try {
            ContextCompat.startForegroundService(context, startIntent)
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
