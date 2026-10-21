package com.bigpapituna.sharpears.engine.brief.ai

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A tiny HTTP/1.1 server on this machine's loopback for the transport's tests: every request is recorded and answered by
 * [handler] (after its delay), one request per connection. Never the real API: no test may reach OpenRouter.
 */
class FakeHttpServer(private val handler: (Recorded) -> Reply) : AutoCloseable {
    data class Recorded(val method: String, val path: String, val headers: Map<String, String>, val body: String)
    data class Reply(val status: Int, val body: String = "", val delayMs: Long = 0, val headers: Map<String, String> = emptyMap())

    private val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    val requests = CopyOnWriteArrayList<Recorded>()
    /** The base URL to give the transport (it adds /chat/completions). */
    val baseUrl get() = "http://127.0.0.1:${socket.localPort}/api/v1"

    init {
        Thread({
            while (!socket.isClosed) {
                val s = try { socket.accept() } catch (e: Exception) { break }
                Thread({ serve(s) }, "fake-http-conn").apply { isDaemon = true; start() }
            }
        }, "fake-http").apply { isDaemon = true; start() }
    }

    private fun serve(s: Socket) {
        try {
            s.use {
                val input = BufferedInputStream(s.getInputStream())
                val first = line(input) ?: return
                val headers = LinkedHashMap<String, String>()
                while (true) {
                    val l = line(input) ?: return
                    if (l.isEmpty()) break
                    val i = l.indexOf(':')
                    if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
                }
                val len = headers["content-length"]?.toIntOrNull() ?: 0
                val body = ByteArray(len)
                var off = 0
                while (off < len) { val n = input.read(body, off, len - off); if (n < 0) break; off += n }
                val parts = first.split(" ")
                val rec = Recorded(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" }, headers, String(body, Charsets.UTF_8))
                requests += rec
                val r = handler(rec)
                if (r.delayMs > 0) Thread.sleep(r.delayMs)
                val bytes = r.body.toByteArray(Charsets.UTF_8)
                val head = StringBuilder("HTTP/1.1 ${r.status} Fake\r\n")
                    .append("Content-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n")
                r.headers.forEach { (k, v) -> head.append(k).append(": ").append(v).append("\r\n") }
                head.append("\r\n")
                s.getOutputStream().apply { write(head.toString().toByteArray(Charsets.US_ASCII)); write(bytes); flush() }
            }
        } catch (e: Exception) {
            // The client gave up (a timeout test) or the server closed: nothing to answer.
        }
    }

    private fun line(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (out.size() == 0) null else out.toString(Charsets.US_ASCII.name())
            if (b == '\n'.code) return out.toString(Charsets.US_ASCII.name()).removeSuffix("\r")
            out.write(b)
        }
    }

    override fun close() = socket.close()

    companion object {
        /** An OpenAI-style chat reply with [content] as the assistant's text. */
        fun chat(content: String): String = com.bigpapituna.sharpears.engine.MiniJson.write(mapOf(
            "id" to "gen-test", "model" to "anthropic/claude-haiku-5.5",
            "choices" to listOf(mapOf("index" to 0, "finish_reason" to "stop", "message" to mapOf("role" to "assistant", "content" to content))),
        ))
    }
}
