package com.novarun.lite.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProjectFileStoreTest {
    @Test
    fun createsListsRenamesAndDeletesFiles() {
        val root = Files.createTempDirectory("novarun-project-test").toFile()
        try {
            val store = ProjectFileStore(root)
            store.createFolder("src")
            store.createFile("src/main.js")
            assertEquals("src/main.js", store.list("src").single().relativePath)
            assertEquals("src/app.js", store.rename("src/main.js", "app.js"))
            store.delete("src/app.js")
            assertTrue(store.list("src").isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        val root = Files.createTempDirectory("novarun-path-test").toFile()
        try {
            val store = ProjectFileStore(root)
            assertThrows(IllegalArgumentException::class.java) { store.createFile("../escape.txt") }
            assertThrows(IllegalArgumentException::class.java) { store.createFile("/tmp/escape.txt") }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun copiesAndMovesFilesWithoutOverwriting() {
        val root = Files.createTempDirectory("novarun-copy-test").toFile()
        try {
            val store = ProjectFileStore(root)
            store.createFile("one.txt")
            File(root, "one.txt").writeText("hello")
            store.copyFile("one.txt", "two.txt")
            assertEquals("hello", File(root, "two.txt").readText())
            store.moveFile("two.txt", "three.txt")
            assertFalse(File(root, "two.txt").exists())
            assertTrue(File(root, "three.txt").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymbolicLinkEscapeWhenSupported() {
        val root = Files.createTempDirectory("novarun-link-test").toFile()
        val outside = Files.createTempFile("novarun-outside", ".txt")
        try {
            Files.createSymbolicLink(File(root, "escape.txt").toPath(), outside)
            val store = ProjectFileStore(root)
            assertThrows(IllegalArgumentException::class.java) { store.list() }
        } finally {
            root.deleteRecursively()
            Files.deleteIfExists(outside)
        }
    }
}
