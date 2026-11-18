package com.novarun.lite.projects

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ProjectSummary(
    val name: String,
    val directory: File,
    val updatedAt: Long,
    val fileCount: Int
)

class ProjectStore(context: Context) {
    private val root = File(context.filesDir, "projects").apply { mkdirs() }

    fun listProjects(): List<ProjectSummary> = root.listFiles()
        .orEmpty()
        .filter { it.isDirectory && !it.name.startsWith(".import-") }
        .map { directory ->
            val metadata = Properties()
            val metadataFile = File(directory, META_FILE)
            if (metadataFile.isFile) {
                runCatching { metadataFile.inputStream().use(metadata::load) }
            }
            ProjectSummary(
                name = metadata.getProperty("name") ?: directory.name,
                directory = directory,
                updatedAt = directory.lastModified(),
                fileCount = countFiles(directory)
            )
        }
        .sortedByDescending { it.updatedAt }

    @Synchronized
    fun createProject(rawName: String): ProjectSummary {
        val name = validateProjectName(rawName)
        val target = uniqueDirectory(name)
        check(target.mkdir()) { "Could not create project directory" }
        writeMetadata(target, name)
        return summarize(target, name)
    }

    @Synchronized
    fun renameProject(oldDirectory: File, rawName: String): ProjectSummary {
        val source = requireProjectDirectory(oldDirectory)
        val name = validateProjectName(rawName)
        val target = File(root, name)
        require(!target.exists() || target.canonicalFile == source.canonicalFile) {
            "A project with that name already exists"
        }
        if (target.canonicalFile != source.canonicalFile) {
            check(source.renameTo(target)) { "Could not rename project" }
        }
        writeMetadata(target, name)
        return summarize(target, name)
    }

    @Synchronized
    fun deleteProject(directory: File) {
        val project = requireProjectDirectory(directory)
        check(project.deleteRecursively()) { "Could not delete project" }
    }

    @Synchronized
    fun importZip(input: InputStream, suggestedName: String): ProjectSummary {
        val baseName = validateProjectName(
            suggestedName.substringBeforeLast('.', suggestedName).ifBlank { "Imported Project" }
        )
        val temporary = File(root, ".import-${System.nanoTime()}")
        check(temporary.mkdir()) { "Could not prepare import directory" }
        var committed = false
        try {
            val seen = HashSet<String>()
            var entryCount = 0
            var totalBytes = 0L
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    require(entryCount <= MAX_ENTRIES) { "ZIP contains too many entries" }
                    val relative = normalizeZipEntryPath(entry.name)
                    require(seen.add(relative)) { "ZIP contains duplicate paths" }
                    val output = safeChild(temporary, relative)
                    if (entry.isDirectory) {
                        check(output.isDirectory || output.mkdirs()) { "Could not create ZIP folder" }
                    } else {
                        check(output.parentFile?.isDirectory == true || output.parentFile?.mkdirs() == true) {
                            "Could not create ZIP folder"
                        }
                        FileOutputStream(output).use { fileOut ->
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                totalBytes += count
                                require(totalBytes <= MAX_UNCOMPRESSED_BYTES) {
                                    "ZIP expands beyond the 100 MB safety limit"
                                }
                                fileOut.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            require(entryCount > 0) { "ZIP archive is empty" }
            val destination = uniqueDirectory(baseName)
            writeMetadata(temporary, baseName)
            check(temporary.renameTo(destination)) { "Could not finalize imported project" }
            committed = true
            return summarize(destination, baseName)
        } finally {
            if (!committed) temporary.deleteRecursively()
        }
    }

    fun exportZip(directory: File, output: OutputStream) {
        val project = requireProjectDirectory(directory)
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            val base = project.canonicalFile
            project.walkTopDown().forEach { file ->
                if (file.isFile) {
                    require(!file.isSymbolicLink()) { "Symbolic links cannot be exported" }
                    val canonical = file.canonicalFile
                    require(canonical.toPath().startsWith(base.toPath())) { "Project path escaped root" }
                    val relative = canonical.relativeTo(base).invariantSeparatorsPath
                    if (relative != META_FILE) {
                        zip.putNextEntry(ZipEntry(relative))
                        FileInputStream(file).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    private fun summarize(directory: File, name: String) = ProjectSummary(
        name = name,
        directory = directory,
        updatedAt = directory.lastModified(),
        fileCount = countFiles(directory)
    )

    private fun countFiles(directory: File): Int =
        directory.walkTopDown().count { it.isFile && it.name != META_FILE }

    private fun validateProjectName(raw: String): String {
        val name = raw.trim()
        require(name.isNotEmpty() && name.length <= 48) { "Project name must be 1–48 characters" }
        require(name != "." && name != "..") { "Invalid project name" }
        require(name.matches(Regex("[A-Za-z0-9 _.-]+"))) {
            "Use only English letters, numbers, spaces, dot, dash or underscore"
        }
        return name
    }

    private fun uniqueDirectory(name: String): File {
        var candidate = File(root, name)
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(root, "$name ($suffix)")
            suffix++
        }
        return candidate
    }

    private fun requireProjectDirectory(directory: File): File {
        val canonicalRoot = root.canonicalFile
        val canonical = directory.canonicalFile
        require(canonical.parentFile == canonicalRoot && canonical.isDirectory) {
            "Invalid project directory"
        }
        require(!canonical.name.startsWith(".import-")) { "Temporary directories are not projects" }
        return canonical
    }

    private fun safeChild(base: File, relative: String): File {
        val destination = File(base, relative).canonicalFile
        val canonicalBase = base.canonicalFile
        require(destination.toPath().startsWith(canonicalBase.toPath())) {
            "ZIP entry escapes the project directory"
        }
        return destination
    }

    private fun writeMetadata(directory: File, name: String) {
        val properties = Properties().apply {
            setProperty("name", name)
            setProperty("createdAt", System.currentTimeMillis().toString())
        }
        FileOutputStream(File(directory, META_FILE)).use {
            properties.store(it, "NovaRun Lite project")
        }
    }

    private fun File.isSymbolicLink(): Boolean =
        runCatching { java.nio.file.Files.isSymbolicLink(toPath()) }.getOrDefault(false)

    companion object {
        private const val META_FILE = ".novarun-project"
        private const val MAX_ENTRIES = 5_000
        private const val MAX_UNCOMPRESSED_BYTES = 100L * 1024L * 1024L
    }
}

internal fun normalizeZipEntryPath(rawPath: String): String {
    require(rawPath.isNotEmpty()) { "ZIP entry path is empty" }
    require(!rawPath.startsWith('/') && !rawPath.startsWith('\\')) {
        "Absolute ZIP paths are not allowed"
    }
    require(!rawPath.contains('\\') && !rawPath.contains('\u0000')) {
        "Invalid ZIP entry path"
    }
    require(!Regex("^[A-Za-z]:").containsMatchIn(rawPath)) { "Drive paths are not allowed" }

    val trimmed = rawPath.removeSuffix("/")
    require(trimmed.isNotEmpty()) { "ZIP entry path is empty" }
    val parts = trimmed.split('/')
    require(parts.none { it.isEmpty() || it == "." || it == ".." }) {
        "ZIP path contains an unsafe segment"
    }
    return parts.joinToString("/")
}
