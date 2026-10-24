package com.bigpapituna.sharpears.engine.brief.ai

import com.bigpapituna.sharpears.engine.MiniJson
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpRetryException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * The AI writer's way out (docs/PARENT-BRIEF-PLAN.md, "Option B"): one HTTPS POST to [baseUrl]/chat/completions in the
 * OpenAI chat shape OpenRouter speaks, with JSON mode asked for where the request wants it. java.net only, so it runs in
 * JVM tests against a local fake server.
 *
 * - The key is read from [keyProvider] at call time (BuildConfig.AI_KEY on Roy's build, empty on any other), goes only in
 *   the Authorization header, and is never stored in a field, logged, thrown or put in a message (see [Secrets]).
 * - [allowed] is asked before every call: false (a metered network the person did not allow) sends nothing.
 * - Timeouts come from the request (AiLimits). A 4xx is never retried; a 5xx or a timeout is retried once when the
 *   request says so (the weekly brief); a reply cut short at the token cap is asked once more with the request's larger
 *   cap ([AiRequest.retryTokens]). At most two attempts. Redirects are not followed, so the key never goes to another host.
 * - [privateProvidersOnly] asks OpenRouter to route only to providers that neither keep nor train on what is sent.
 * - [log] gets one line per attempt: what happened, the status and the time taken; never the key, the prompt or the reply.
 */
class OpenRouterTransport(
    private val baseUrl: String,
    private val keyProvider: () -> String?,
    private val model: String,
    private val allowed: () -> Boolean,
    private val privateProvidersOnly: Boolean = true,
    private val log: (String) -> Unit = {},
) : AiTransport {

    override fun complete(request: AiRequest): String {
        if (!allowed()) throw AiCallException(AiCallException.Kind.NOT_ALLOWED, message = "not sent: the network is metered")
        val key = keyProvider()?.trim().orEmpty()
        if (key.isEmpty()) throw AiCallException(AiCallException.Kind.NO_KEY, message = "not sent: this build has no AI key")
        val url = endpoint()
        var asked = request
        var attempt = 1
        while (true) {
            val started = System.nanoTime()
            try {
                val text = once(url, key, MiniJson.write(body(asked)).toByteArray(Charsets.UTF_8), asked.timeoutMs)
                log("ai: reply in ${elapsed(started)} ms (attempt $attempt, ${text.length} chars)")
                return text
            } catch (e: AiCallException) {
                log("ai: ${e.kind} after ${elapsed(started)} ms (attempt $attempt): ${e.message}")
                if (attempt > 1) throw e
                when {
                    request.retryOnce && (e.kind == AiCallException.Kind.SERVER || e.kind == AiCallException.Kind.TIMEOUT) -> {}
                    e.kind == AiCallException.Kind.CUT_SHORT && request.retryTokens > request.maxTokens -> {
                        asked = request.copy(maxTokens = request.retryTokens)
                        log("ai: asking again with ${request.retryTokens} tokens")
                    }
                    else -> throw e
                }
                attempt++
            }
        }
    }

    /** The request body: OpenAI-style chat, JSON mode when asked, the provider routing preference. */
    internal fun body(r: AiRequest): Map<String, Any?> = linkedMapOf<String, Any?>(
        "model" to r.model.ifBlank { model },
        "messages" to listOf(linkedMapOf("role" to "system", "content" to r.system), linkedMapOf("role" to "user", "content" to r.user)),
        "max_tokens" to r.maxTokens,
        "temperature" to r.temperature,
    ).apply {
        if (r.json) put("response_format", linkedMapOf("type" to "json_object"))
        if (privateProvidersOnly) put("provider", linkedMapOf("data_collection" to "deny"))
    }

    /** HTTPS only; plain HTTP to this device's loopback is allowed so tests can run a local fake server. */
    private fun endpoint(): URL {
        val url = runCatching { URL(baseUrl.trim().trimEnd('/') + "/chat/completions") }.getOrNull()
            ?: throw AiCallException(AiCallException.Kind.CONFIG, message = "the AI base URL is not a URL")
        val loopback = url.host in LOOPBACK
        if (url.protocol != "https" && !(url.protocol == "http" && loopback))
            throw AiCallException(AiCallException.Kind.CONFIG, message = "the AI base URL must be https")
        return url
    }

    private fun once(url: URL, key: String, body: ByteArray, timeoutMs: Long): String {
        val t = timeoutMs.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
        val c = try { url.openConnection() as HttpURLConnection } catch (e: IOException) { throw network(e, key) }
        try {
            c.requestMethod = "POST"
            c.connectTimeout = t
            c.readTimeout = t
            c.instanceFollowRedirects = false
            c.useCaches = false
            c.doOutput = true
            c.setFixedLengthStreamingMode(body.size)     // no chunking, and no silent replay of the POST by the platform
            c.setRequestProperty("Authorization", "Bearer $key")
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("HTTP-Referer", APP_REFERER)
            c.setRequestProperty("X-Title", APP_TITLE)
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            val text = read(if (code < 400) c.inputStream else c.errorStream)
            return when {
                code in 200..299 -> reply(text, key)
                code == 429 -> throw AiCallException(AiCallException.Kind.RATE_LIMITED, code, "HTTP 429" + detail(text, key))
                code in 500..599 -> throw AiCallException(AiCallException.Kind.SERVER, code, "HTTP $code" + detail(text, key))
                else -> throw AiCallException(AiCallException.Kind.CLIENT, code, "HTTP $code" + detail(text, key))
            }
        } catch (e: AiCallException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw AiCallException(AiCallException.Kind.TIMEOUT, message = "no reply within $t ms")
        } catch (e: HttpRetryException) {
            // A 401 with an auth challenge cannot be replayed in streaming mode: still a refusal, not a network fault.
            throw AiCallException(AiCallException.Kind.CLIENT, e.responseCode(), "HTTP ${e.responseCode()}: the key was refused")
        } catch (e: Exception) {
            throw network(e, key)
        } finally {
            c.disconnect()
        }
    }

    /** The assistant's text from a 2xx body; OpenRouter can answer 200 with an error object instead. */
    private fun reply(text: String, key: String): String {
        val root = runCatching { MiniJson.read(text) as? Map<*, *> }.getOrNull()
            ?: throw AiCallException(AiCallException.Kind.BAD_REPLY, 200, "the reply was not JSON")
        val choice = (root["choices"] as? List<*>)?.firstOrNull() as? Map<*, *>
        if (choice == null) {
            if (root["error"] != null) throw AiCallException(AiCallException.Kind.SERVER, 200, "an error in place of a reply" + detail(text, key))
            throw AiCallException(AiCallException.Kind.BAD_REPLY, 200, "the reply had no choices")
        }
        val out = when (val content = (choice["message"] as? Map<*, *>)?.get("content")) {
            is String -> content
            // Some providers send the content as parts: [{"type":"text","text":"..."}].
            is List<*> -> content.mapNotNull { (it as? Map<*, *>)?.get("text") as? String }.joinToString("")
            else -> ""
        }
        val finish = choice["finish_reason"]
        // Cut at the cap: empty (the budget went on thinking) or stopped mid-way; neither is a reply to show.
        if (finish == "length") throw AiCallException(AiCallException.Kind.CUT_SHORT, 200,
            "the reply was ${if (out.isBlank()) "empty" else "cut short"} (finish: length${usage(root)})")
        if (out.isBlank()) throw AiCallException(AiCallException.Kind.BAD_REPLY, 200, "the reply was empty (finish: $finish${usage(root)})")
        return out
    }

    /** ", 1500 tokens, 1480 thinking" from the reply's usage block (numbers only), or nothing. */
    private fun usage(root: Map<*, *>): String {
        val u = root["usage"] as? Map<*, *> ?: return ""
        val out = (u["completion_tokens"] as? Number)?.toLong() ?: return ""
        val thinking = ((u["completion_tokens_details"] as? Map<*, *>)?.get("reasoning_tokens") as? Number)?.toLong()
        return ", $out tokens" + (thinking?.takeIf { it > 0 }?.let { ", $it thinking" } ?: "")
    }

    /** The provider's own error message, scrubbed and short; a body that is not JSON adds nothing. */
    private fun detail(text: String, key: String): String {
        val msg = runCatching { ((MiniJson.read(text) as? Map<*, *>)?.get("error") as? Map<*, *>)?.get("message") as? String }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return ""
        return ": " + Secrets.scrub(msg.replace(Regex("\\s+"), " "), key).take(160)
    }

    private fun network(e: Exception, key: String) =
        AiCallException(AiCallException.Kind.NETWORK, message = Secrets.scrub("${e.javaClass.simpleName}: ${e.message.orEmpty()}", key).take(200))

    private fun read(s: InputStream?): String {
        if (s == null) return ""
        return s.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (out.size() < MAX_BODY) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n) }
            String(out.toByteArray(), Charsets.UTF_8)
        }
    }

    private fun elapsed(startNs: Long) = (System.nanoTime() - startNs) / 1_000_000

    companion object {
        /** OpenRouter's app attribution headers (openrouter.ai/docs: HTTP-Referer and X-Title). */
        const val APP_TITLE = "Sharp Ears"
        const val APP_REFERER = "android-app://com.bigpapituna.sharpears"
        /** A reply this long is not a brief; stop reading. */
        private const val MAX_BODY = 512 * 1024
        private val LOOPBACK = setOf("127.0.0.1", "localhost", "[::1]", "::1")
    }
}
