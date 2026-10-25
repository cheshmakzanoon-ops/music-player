package io.github.musicplayer.spotify

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/** RFC 8252-style loopback redirect; bind ONLY Android's own 127.0.0.1 interface. */
class SpotifyLoopbackRedirect : Closeable {
    private val server = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
    val redirectUri: String = "http://127.0.0.1:${server.localPort}/callback"

    /** Must execute on an IO dispatcher. Only the valid state/code pair completes login. */
    fun awaitCode(expectedState: String): String {
        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(2)
        var attempts = 0
        while (attempts++ < 16) {
            val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
            if (remaining <= 0) break
            server.soTimeout = remaining.coerceIn(1, 30_000).toInt()
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 3_000
                    val line = readRequestLine(socket.getInputStream())
                    val parts = line?.split(' ', limit = 3).orEmpty()
                    val target = parts.getOrNull(1)
                    if (parts.size != 3 || parts[0] != "GET" || target == null ||
                        !target.startsWith("/callback?") || target.any { it == '\r' || it == '\n' }
                    ) {
                        sendPage(socket, 404, "No authorization callback found.")
                        return@use
                    }
                    val callbackUrl = "http://127.0.0.1:${server.localPort}$target"
                    val code = try {
                        SpotifyPkce.codeFromCallback(callbackUrl, redirectUri, expectedState)
                    } catch (failure: SpotifyAuthorizationException) {
                        sendPage(socket, 400, "Authorization was not accepted. Return to NIGHT SHIFT.")
                        if (failure.message?.contains("state mismatch") == true) return@use
                        throw failure
                    }
                    sendPage(socket, 200, "Spotify authorization received. Return to NIGHT SHIFT.")
                    return code
                }
            } catch (_: SocketTimeoutException) {
                // Keep waiting until the overall authorization deadline expires.
            }
        }
        throw SpotifyAuthorizationException("Spotify authorization timed out. Try connecting again.")
    }

    override fun close() = server.close()

    private fun readRequestLine(stream: java.io.InputStream): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (bytes.size() <= 4_096) {
            val c = stream.read()
            if (c == -1) return null
            if (c == 10) return bytes.toString(StandardCharsets.US_ASCII.name()).trimEnd('\r')
            bytes.write(c)
        }
        return null
    }

    private fun sendPage(socket: java.net.Socket, code: Int, message: String) {
        val text = "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'><title>NIGHT SHIFT</title><body style='background:#080c13;color:#c6ff3e;font-family:monospace;padding:3rem'><h1>NIGHT SHIFT</h1><p>$message</p></body>"
        val payload = text.toByteArray(StandardCharsets.UTF_8)
        val status = if (code == 200) "200 OK" else "${code} Bad Request"
        val headers = "HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n"
        runCatching { socket.getOutputStream().apply { write(headers.toByteArray(StandardCharsets.US_ASCII)); write(payload); flush() } }
    }
}
