package com.novarun.lite.server

import com.novarun.lite.files.ProjectFileStore
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class LocalHttpServer(
    private val projectDirectory: java.io.File,
    private val port: Int,
    private val onLog: (String) -> Unit,
    private val onState: (Boolean) -> Unit,
    private val bindAddress: String = "127.0.0.1",
    private val authToken: String? = null
) {
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var worker: Thread? = null

    @Synchronized
    fun start() {
        require(port in 1024..65535) { "Port must be between 1024 and 65535" }
        check(worker?.isAlive != true) { "Server is already running" }
        worker = Thread({ runServer() }, "NovaRun-LocalHTTP").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        worker?.interrupt()
        worker = null
        onState(false)
        onLog("Server stopped")
    }

    private fun runServer() {
        try {
            val store = ProjectFileStore(projectDirectory)
            ServerSocket(port, 32, InetAddress.getByName(bindAddress)).use { socket ->
                serverSocket = socket
                socket.soTimeout = 0
                onState(true)
                onLog("Listening on $bindAddress:$port")
                while (!socket.isClosed && !Thread.currentThread().isInterrupted) {
                    val client = socket.accept()
                    client.use { connection ->
                        connection.soTimeout = 5_000
                        handleClient(connection, store)
                    }
                }
            }
        } catch (error: Exception) {
            if (error !is java.net.SocketException || serverSocket != null) {
                onLog("Server error: ${error.message ?: error.javaClass.simpleName}")
            }
        } finally {
            serverSocket = null
            onState(false)
        }
    }

    private fun handleClient(client: Socket, store: ProjectFileStore) {
        val output = client.getOutputStream()
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(' ', limit = 3)
            if (parts.size != 3) {
                respond(output, 400, "Bad Request", "Invalid request line")
                return
            }
            val method = parts[0]
            if (method != "GET" && method != "HEAD") {
                respond(output, 405, "Method Not Allowed", "Only GET and HEAD are supported", mapOf("Allow" to "GET, HEAD"))
                return
            }
            val target = parts[1]
            if (!target.startsWith("/") || target.startsWith("//")) {
                respond(output, 400, "Bad Request", "Invalid request target")
                return
            }
            val requestHeaders = HashMap<String, String>()
            var headerBytes = 0
            var headerCount = 0
            while (true) {
                val header = reader.readLine() ?: break
                headerBytes += header.length
                headerCount++
                if (headerBytes > MAX_HEADER_BYTES || headerCount > MAX_HEADERS) {
                    respond(output, 431, "Request Header Fields Too Large", "Headers are too large")
                    return
                }
                if (header.isEmpty()) break
                val separator = header.indexOf(':')
                if (separator > 0) {
                    requestHeaders[header.substring(0, separator).trim().lowercase()] =
                        header.substring(separator + 1).trim()
                }
            }
            val expectedAuthorization = authToken?.let { "Bearer $it" }
            val providedAuthorization = requestHeaders["authorization"].orEmpty()
            val authorized = expectedAuthorization == null ||
                java.security.MessageDigest.isEqual(
                    expectedAuthorization.toByteArray(StandardCharsets.UTF_8),
                    providedAuthorization.toByteArray(StandardCharsets.UTF_8)
                )
            if (!authorized) {
                respond(output, 401, "Unauthorized", "A valid NovaRun access token is required",
                    mapOf("WWW-Authenticate" to "Bearer"))
                onLog("Rejected unauthenticated LAN request")
                return
            }

            val encodedPath = target.substringBefore('?').substringBefore('#')
            val decodedPath = URLDecoder.decode(encodedPath, "UTF-8")
            val relative = validateRequestPath(decodedPath)
            val filePath = if (store.isDirectory(relative)) {
                if (relative.isEmpty()) "index.html" else "$relative/index.html"
            } else relative
            val size = runCatching { store.fileSize(filePath) }.getOrElse {
                respond(output, 404, "Not Found", "File not found")
                return
            }
            val input = runCatching { store.openInputStream(filePath) }.getOrElse {
                respond(output, 404, "Not Found", "File not found")
                return
            }
            val mime = mimeType(filePath)
            val charset = if (mime.startsWith("text/") || mime == "application/javascript" || mime == "application/json") "; charset=utf-8" else ""
            val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: $mime$charset\r\n" +
                "Content-Length: $size\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                "Cache-Control: no-store\r\n" +
                "Connection: close\r\n\r\n"
            output.write(headers.toByteArray(StandardCharsets.ISO_8859_1))
            if (method == "GET") input.use { it.copyTo(output) } else input.close()
            output.flush()
            onLog("$method $filePath -> 200")
        } catch (error: IllegalArgumentException) {
            respond(output, 400, "Bad Request", "Unsafe request path")
            onLog("Rejected unsafe request")
        } catch (error: Exception) {
            runCatching { respond(output, 500, "Internal Server Error", "Request failed") }
            onLog("Request error: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun validateRequestPath(path: String): String {
        require(path.startsWith("/") && !path.startsWith("//"))
        require(!path.contains('\\') && !path.contains('\u0000'))
        val decoded = path.removePrefix("/").removeSuffix("/")
        if (decoded.isEmpty()) return ""
        val parts = decoded.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." })
        return parts.joinToString("/")
    }

    private fun respond(
        output: java.io.OutputStream,
        status: Int,
        reason: String,
        message: String,
        extraHeaders: Map<String, String> = emptyMap()
    ) {
        val body = message.toByteArray(StandardCharsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: text/plain; charset=utf-8\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Connection: close\r\n")
            extraHeaders.forEach { (key, value) -> append("$key: $value\r\n") }
            append("\r\n")
        }
        output.write(headers.toByteArray(StandardCharsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }

    private fun mimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js", "mjs" -> "application/javascript"
        "json" -> "application/json"
        "txt", "md" -> "text/plain"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    companion object {
        private const val MAX_HEADER_BYTES = 16 * 1024
        private const val MAX_HEADERS = 64
    }
}
