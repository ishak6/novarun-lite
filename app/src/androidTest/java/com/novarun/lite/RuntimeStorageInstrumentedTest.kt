package com.novarun.lite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novarun.lite.database.ProjectDatabaseStore
import com.novarun.lite.security.SecureTokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
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
