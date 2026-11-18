package com.novarun.lite.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ZipPathSafetyTest {
    @Test
    fun acceptsNestedRelativePath() {
        assertEquals("site/assets/app.css", normalizeZipEntryPath("site/assets/app.css"))
    }

    @Test
    fun acceptsDirectoryEntryWithTrailingSlash() {
        assertEquals("assets/images", normalizeZipEntryPath("assets/images/"))
    }

    @Test
    fun rejectsEmptySegments() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeZipEntryPath("assets//image.png")
        }
    }

    @Test
    fun rejectsParentTraversal() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeZipEntryPath("../../outside.txt")
        }
    }

    @Test
    fun rejectsAbsolutePath() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeZipEntryPath("/storage/outside.txt")
        }
    }

    @Test
    fun rejectsWindowsDrivePath() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeZipEntryPath("C:/outside.txt")
        }
    }

    @Test
    fun rejectsBackslashPath() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeZipEntryPath("..\\outside.txt")
        }
    }
}
