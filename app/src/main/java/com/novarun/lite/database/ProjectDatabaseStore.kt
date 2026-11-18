package com.novarun.lite.database

import android.database.sqlite.SQLiteDatabase
import java.io.File

data class SqlQueryResult(
    val columns: List<String>,
    val rows: List<List<String?>>,
    val truncated: Boolean,
    val message: String
)

class ProjectDatabaseStore(projectDirectory: File) : AutoCloseable {
    private val root = projectDirectory.canonicalFile
    private val databaseFile = File(root, DATABASE_NAME)

    private val database: SQLiteDatabase

    init {
        require(root.isDirectory) { "Project directory does not exist" }
        require(databaseFile.canonicalFile.parentFile == root) { "Database path escaped project" }
        database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        database.setForeignKeyConstraintsEnabled(true)
    }

    @Synchronized
    fun execute(sql: String, maxRows: Int = 200): SqlQueryResult {
        val statement = sql.trim()
        require(statement.isNotEmpty()) { "Enter a SQL statement" }
        require(statement.length <= MAX_SQL_LENGTH) { "SQL statement is too long" }
        require(maxRows in 1..MAX_ROWS) { "Invalid result row limit" }

        val normalized = statement.substringBefore(';').trimStart().uppercase()
        val isQuery = normalized.startsWith("SELECT") ||
            normalized.startsWith("WITH") ||
            normalized.startsWith("PRAGMA") ||
            normalized.startsWith("EXPLAIN")

        return if (isQuery) {
            database.rawQuery(statement, null).use { cursor ->
                val columns = cursor.columnNames.toList()
                val rows = ArrayList<List<String?>>()
                var truncated = false
                while (cursor.moveToNext()) {
                    if (rows.size >= maxRows) {
                        truncated = true
                        break
                    }
                    rows += columns.indices.map { index ->
                        if (cursor.isNull(index)) null else cursor.getString(index)
                    }
                }
                SqlQueryResult(columns, rows, truncated, "Query returned ${rows.size} row(s)")
            }
        } else {
            database.execSQL(statement)
            SqlQueryResult(emptyList(), emptyList(), false, "SQL statement executed")
        }
    }

    override fun close() {
        if (database.isOpen) database.close()
    }

    companion object {
        const val DATABASE_NAME = "novarun.sqlite"
        private const val MAX_SQL_LENGTH = 100_000
        private const val MAX_ROWS = 1_000
    }
}
