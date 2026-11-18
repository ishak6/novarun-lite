package com.novarun.lite.server

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocalHttpServerTest {
    @Test
    fun requiresBearerTokenWhenLanAuthenticationIsEnabled() {
        val root = Files.createTempDirectory("novarun-auth-test").toFile()
        val port = ServerSocket(0).use { it.localPort }
        val started = CountDownLatch(1)
        val server = LocalHttpServer(
            root, port, {}, { running -> if (running) started.countDown() },
            bindAddress = "127.0.0.1",
            authToken = "test-secret-token"
        )
        try {
            File(root, "index.html").writeText("private")
            server.start()
            assertTrue("server should start", started.await(5, TimeUnit.SECONDS))
            Socket("127.0.0.1", port).use { client ->
                client.getOutputStream().write(
                    "GET / HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n".toByteArray()
                )
                val response = client.getInputStream().bufferedReader().readText()
                assertTrue(response.contains("401 Unauthorized"))
            }
            Socket("127.0.0.1", port).use { client ->
                client.getOutputStream().write(
                    "GET / HTTP/1.1\\r\\nHost: localhost\\r\\nAuthorization: Bearer test-secret-token\\r\\nConnection: close\\r\\n\\r\\n".toByteArray()
                )
                val response = client.getInputStream().bufferedReader().readText()
                assertTrue(response.contains("200 OK"))
                assertTrue(response.contains("private"))
            }
        } finally {
            server.stop()
            root.deleteRecursively()
        }
    }

    @Test
    fun servesProjectIndexOnlyOnLoopback() {
        val root = Files.createTempDirectory("novarun-http-test").toFile()
        val port = ServerSocket(0).use { it.localPort }
        val started = CountDownLatch(1)
        val server = LocalHttpServer(root, port, {}, { running -> if (running) started.countDown() })
        try {
            File(root, "index.html").writeText("<h1>NovaRun</h1>")
            server.start()
            assertTrue("server should start", started.await(5, TimeUnit.SECONDS))
            Socket("127.0.0.1", port).use { client ->
                client.soTimeout = 5_000
                client.getOutputStream().write(
                    "GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".toByteArray()
                )
                val response = client.getInputStream().bufferedReader().readText()
                assertTrue(response.contains("200 OK"))
                assertTrue(response.contains("<h1>NovaRun</h1>"))
            }
        } finally {
            server.stop()
            root.deleteRecursively()
        }
    }
}
