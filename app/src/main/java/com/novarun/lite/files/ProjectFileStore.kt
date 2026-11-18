package com.novarun.lite.files

import java.io.File
import java.nio.file.Files

data class ProjectFileEntry(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long
)

class ProjectFileStore(projectDirectory: File) {
    private val root = projectDirectory.canonicalFile

    init {
        require(root.isDirectory) { "Project directory does not exist" }
        require(!Files.isSymbolicLink(root.toPath())) { "Project root cannot be a symbolic link" }
    }

    fun list(relativeDirectory: String = ""): List<ProjectFileEntry> {
        val directory = resolve(relativeDirectory, allowRoot = true)
        require(directory.isDirectory) { "Selected path is not a folder" }
        return directory.listFiles().orEmpty()
            .filterNot { it.name == ".novarun-project" }
            .onEach { child ->
                require(!Files.isSymbolicLink(child.toPath())) { "Symbolic links are not supported" }
            }
            .map { child ->
                ProjectFileEntry(
                    name = child.name,
                    relativePath = child.relativeTo(root).invariantSeparatorsPath,
                    isDirectory = child.isDirectory,
                    sizeBytes = if (child.isFile) child.length() else 0L,
                    modifiedAt = child.lastModified()
                )
            }
            .sortedWith(compareByDescending<ProjectFileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    fun createFile(relativePath: String) {
        val file = resolve(relativePath)
        ensureParent(file)
        check(file.createNewFile()) { "File already exists or could not be created" }
    }

    fun createFolder(relativePath: String) {
        val folder = resolve(relativePath)
        ensureParent(folder)
        check(folder.mkdir()) { "Folder already exists or could not be created" }
    }

    fun rename(relativePath: String, newName: String): String {
        validateName(newName)
        val source = resolve(relativePath)
        require(source.exists()) { "Source does not exist" }
        val parent = source.parentFile ?: error("Missing parent directory")
        val parentRelative = if (parent.canonicalFile == root) "" else parent.relativeTo(root).invariantSeparatorsPath
        val destination = resolve(join(parentRelative, newName))
        require(!destination.exists()) { "A file with that name already exists" }
        check(source.renameTo(destination)) { "Rename failed" }
        return destination.relativeTo(root).invariantSeparatorsPath
    }

    fun delete(relativePath: String) {
        val target = resolve(relativePath)
        require(target.exists()) { "File does not exist" }
        check(target.deleteRecursively()) { "Delete failed" }
    }

    fun copyFile(sourcePath: String, destinationPath: String) {
        val source = resolve(sourcePath)
        val destination = resolve(destinationPath)
        require(source.isFile) { "Only files can be copied" }
        require(!destination.exists()) { "Destination already exists" }
        ensureParent(destination)
        source.copyTo(destination, overwrite = false)
    }

    fun moveFile(sourcePath: String, destinationPath: String) {
        val source = resolve(sourcePath)
        val destination = resolve(destinationPath)
        require(source.isFile) { "Only files can be moved" }
        require(!destination.exists()) { "Destination already exists" }
        ensureParent(destination)
        check(source.renameTo(destination)) { "Move failed" }
    }


    fun isDirectory(relativePath: String): Boolean = resolve(relativePath, allowRoot = true).isDirectory

    fun fileSize(relativePath: String): Long {
        val file = resolve(relativePath)
        require(file.isFile) { "Selected path is not a file" }
        return file.length()
    }

    fun openInputStream(relativePath: String): java.io.InputStream {
        val file = resolve(relativePath)
        require(file.isFile) { "Selected path is not a file" }
        return file.inputStream()
    }

    fun readText(relativePath: String, maxBytes: Long = 2L * 1024L * 1024L): String {
        val file = resolve(relativePath)
        require(file.isFile) { "Selected path is not a file" }
        require(file.length() <= maxBytes) { "File is larger than the 2 MB editor limit" }
        return file.readText(Charsets.UTF_8)
    }

    fun writeTextAtomic(relativePath: String, content: String, maxBytes: Long = 2L * 1024L * 1024L) {
        val target = resolve(relativePath)
        require(target.isFile) { "Selected file does not exist" }
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size.toLong() <= maxBytes) { "File is larger than the 2 MB editor limit" }
        val temporary = File(target.parentFile, ".${target.name}.tmp-${System.nanoTime()}")
        try {
            temporary.writeBytes(bytes)
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun search(query: String): List<ProjectFileEntry> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return root.walkTopDown()
            .filter { it != root && it.name != ".novarun-project" }
            .filter { !Files.isSymbolicLink(it.toPath()) && it.name.contains(needle, ignoreCase = true) }
            .map { file ->
                ProjectFileEntry(
                    name = file.name,
                    relativePath = file.relativeTo(root).invariantSeparatorsPath,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isFile) file.length() else 0L,
                    modifiedAt = file.lastModified()
                )
            }
            .toList()
    }

    private fun resolve(rawPath: String, allowRoot: Boolean = false): File {
        if (allowRoot && rawPath.isEmpty()) return root
        val normalized = validateRelativePath(rawPath)
        val candidate = File(root, normalized).canonicalFile
        require(candidate.toPath().startsWith(root.toPath()) && candidate != root) {
            "Path escapes project root"
        }
        var current = root
        normalized.split('/').forEach { segment ->
            current = File(current, segment)
            if (current.exists()) {
                require(!Files.isSymbolicLink(current.toPath())) { "Symbolic links are not supported" }
            }
        }
        return candidate
    }

    private fun ensureParent(file: File) {
        val parent = file.parentFile ?: error("Missing parent directory")
        require(parent.isDirectory) { "Parent folder does not exist" }
        require(parent.canonicalFile.toPath().startsWith(root.toPath())) { "Parent escapes project root" }
    }

    private fun validateName(name: String) {
        require(name.isNotBlank() && name.length <= 120) { "Name must be 1–120 characters" }
        require(name != "." && name != ".." && !name.contains('/') && !name.contains('\\')) {
            "Invalid file name"
        }
        require(!name.contains('\u0000')) { "Invalid file name" }
    }

    private fun validateRelativePath(path: String): String {
        require(path.isNotBlank() && !path.startsWith('/') && !path.startsWith('\\')) {
            "Path must be relative to the project"
        }
        require(!path.contains('\\') && !path.contains('\u0000')) { "Invalid path" }
        val parts = path.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." }) { "Unsafe path segment" }
        return parts.joinToString("/")
    }

    private fun join(parent: String, name: String): String =
        if (parent.isEmpty()) name else "$parent/$name"
}
