package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.MiniJson
import com.bigpapituna.sharpears.engine.brief.ai.Secrets
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpRetryException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Base64

/**
 * One email to send: the recipient [to], the [subject], the [html] and [text] bodies, and an optional PDF [attachment]
 * named [attachmentName]. [toString] never shows the address or any content (the subject holds the child's name).
 */
class EmailMessage(
    val to: String, val subject: String, val html: String, val text: String,
    val attachment: ByteArray? = null, val attachmentName: String? = null,
) {
    override fun toString() = "EmailMessage(subject ${subject.length} chars, html ${html.length}, text ${text.length}, pdf ${attachment?.size ?: 0} bytes)"
}

/** How an email leaves the phone: [BrevoTransport] is the real one; tests inject a fake. Never logs or throws the key or the address. */
fun interface EmailTransport {
    /** Sends [message]; returns the provider's message id (or "accepted"). Throws [EmailSendException] (and nothing else) when it was not sent. */
    fun send(message: EmailMessage): String
}

/**
 * Why an email was not sent. The message is safe to log: it never holds the key, the address, the subject or the body,
 * and no cause is attached (a platform exception's text is scrubbed into the message instead).
 */
class EmailSendException(val kind: Kind, val status: Int? = null, message: String) : Exception(message) {
    enum class Kind {
        /** The network is metered and the grown-up did not allow it: nothing was sent. */
        NOT_ALLOWED,
        /** This build has no email key (BuildConfig.EMAIL_KEY is empty): nothing was sent. */
        NO_KEY,
        /** The base URL is not HTTPS (plain HTTP to this device's loopback only, for tests), or there is no sender address. */
        CONFIG,
        /** HTTP 429: too many requests (Brevo's free plan sends 300 a day). Not retried. */
        RATE_LIMITED,
        /** Any other 4xx (401 a wrong key, 400 a request Brevo refused, 402 no credits left) or a redirect. Never retried. */
        CLIENT,
        /** 5xx, after one retry. */
        SERVER,
        /** No reply in time. Not retried: Brevo may have taken it, and a second try could send it twice. */
        TIMEOUT,
        /** No connection (offline, DNS, TLS, reset). */
        NETWORK,
    }
}

/** Keeps the key and the recipient's address out of anything that may be logged, shown or thrown. */
object EmailScrub {
    private val ADDRESS = Regex("[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*")

    /** [text] with [address] and anything shaped like an address replaced, then the key and its pieces ([Secrets.scrub]). */
    fun scrub(text: String, key: String?, address: String?): String {
        var t = text
        if (!address.isNullOrBlank()) t = t.replace(address, "[address]", ignoreCase = true)
        t = ADDRESS.replace(t, "[address]")
        return Secrets.scrub(t, key)
    }
}

/**
 * The email brief's way out (Roy, 2026-10-21): one HTTPS POST to [baseUrl]/smtp/email, Brevo's transactional email API
 * (free plan, 300 a day), from the verified sender [senderName] <[senderEmail]>. java.net only, so it runs in JVM tests
 * against a local fake server; no test ever reaches Brevo.
 *
 * - The key is read from [keyProvider] at call time (BuildConfig.EMAIL_KEY on Roy's build, empty on any other), goes only in
 *   the `api-key` header, and is never stored in a field, logged, thrown or put in a message (see [EmailScrub]).
 * - [allowed] is asked before every send: false (a metered network the grown-up did not allow) sends nothing.
 * - [timeoutMs] bounds the connection and each read. A 4xx is never retried; a 5xx is retried once; a timeout is not
 *   (the email may have gone). Redirects are not followed, so the key never goes to another host.
 * - [log] gets one line per attempt: what happened, the status, the time taken and the size; never the key, the address,
 *   the subject or the body.
 */
class BrevoTransport(
    private val baseUrl: String,
    private val keyProvider: () -> String?,
    private val senderName: String,
    private val senderEmail: String,
    private val allowed: () -> Boolean,
    private val timeoutMs: Int = 20_000,
    private val log: (String) -> Unit = {},
) : EmailTransport {

    override fun send(message: EmailMessage): String {
        if (!allowed()) throw EmailSendException(EmailSendException.Kind.NOT_ALLOWED, message = "not sent: the network is metered")
        val key = keyProvider()?.trim().orEmpty()
        if (key.isEmpty()) throw EmailSendException(EmailSendException.Kind.NO_KEY, message = "not sent: this build has no email key")
        if (senderEmail.isBlank()) throw EmailSendException(EmailSendException.Kind.CONFIG, message = "not sent: this build has no sender address")
        val url = endpoint()
        val body = MiniJson.write(body(message)).toByteArray(Charsets.UTF_8)
        var attempt = 1
        while (true) {
            val started = System.nanoTime()
            try {
                val id = once(url, key, message.to, body)
                log("email: sent in ${elapsed(started)} ms (attempt $attempt, ${body.size / 1024} KB)")
                return id
            } catch (e: EmailSendException) {
                log("email: ${e.kind} after ${elapsed(started)} ms (attempt $attempt): ${e.message}")
                if (!(e.kind == EmailSendException.Kind.SERVER && attempt == 1)) throw e
                attempt++
            }
        }
    }

    /** Brevo's request: the sender, one recipient, the subject, both bodies and the PDF in base64. */
    internal fun body(m: EmailMessage): Map<String, Any?> = linkedMapOf<String, Any?>(
        "sender" to linkedMapOf("name" to senderName, "email" to senderEmail),
        "to" to listOf(linkedMapOf("email" to m.to)),
        "subject" to m.subject,
        "htmlContent" to m.html,
        "textContent" to m.text,
    ).apply {
        if (m.attachment != null) put("attachment", listOf(linkedMapOf(
            "content" to Base64.getEncoder().encodeToString(m.attachment), "name" to (m.attachmentName ?: "brief.pdf"))))
    }

    /** HTTPS only; plain HTTP to this device's loopback is allowed so tests can run a local fake server. */
    private fun endpoint(): URL {
        val url = runCatching { URL(baseUrl.trim().trimEnd('/') + "/smtp/email") }.getOrNull()
            ?: throw EmailSendException(EmailSendException.Kind.CONFIG, message = "the email base URL is not a URL")
        if (url.protocol != "https" && !(url.protocol == "http" && url.host in LOOPBACK))
            throw EmailSendException(EmailSendException.Kind.CONFIG, message = "the email base URL must be https")
        return url
    }

    private fun once(url: URL, key: String, to: String, body: ByteArray): String {
        val c = try { url.openConnection() as HttpURLConnection } catch (e: IOException) { throw network(e, key, to) }
        try {
            c.requestMethod = "POST"
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = false
            c.useCaches = false
            c.doOutput = true
            c.setFixedLengthStreamingMode(body.size)     // no chunking, and no silent replay of the POST by the platform
            c.setRequestProperty("api-key", key)
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("Accept", "application/json")
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            val text = read(if (code < 400) c.inputStream else c.errorStream)
            return when {
                // Accepted: the id when Brevo gives one. A 2xx is never treated as a failure (a retry could send it twice).
                code in 200..299 -> runCatching { (MiniJson.read(text) as? Map<*, *>)?.get("messageId") as? String }.getOrNull() ?: "accepted"
                code == 429 -> throw EmailSendException(EmailSendException.Kind.RATE_LIMITED, code, "HTTP 429" + detail(text, key, to))
                code in 500..599 -> throw EmailSendException(EmailSendException.Kind.SERVER, code, "HTTP $code" + detail(text, key, to))
                else -> throw EmailSendException(EmailSendException.Kind.CLIENT, code, "HTTP $code" + detail(text, key, to))
            }
        } catch (e: EmailSendException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw EmailSendException(EmailSendException.Kind.TIMEOUT, message = "no reply within $timeoutMs ms")
        } catch (e: HttpRetryException) {
            // A 401 with an auth challenge cannot be replayed in streaming mode: still a refusal, not a network fault.
            throw EmailSendException(EmailSendException.Kind.CLIENT, e.responseCode(), "HTTP ${e.responseCode()}: the key was refused")
        } catch (e: Exception) {
            throw network(e, key, to)
        } finally {
            c.disconnect()
        }
    }

    /** Brevo's own error message ({"code", "message"}), scrubbed and short; a body that is not JSON adds nothing. */
    private fun detail(text: String, key: String, to: String): String {
        val m = runCatching { MiniJson.read(text) as? Map<*, *> }.getOrNull() ?: return ""
        val msg = listOfNotNull(m["code"] as? String, m["message"] as? String).joinToString(": ").takeIf { it.isNotBlank() } ?: return ""
        return ": " + EmailScrub.scrub(msg.replace(Regex("\\s+"), " "), key, to).take(160)
    }

    private fun network(e: Exception, key: String, to: String) = EmailSendException(EmailSendException.Kind.NETWORK,
        message = EmailScrub.scrub("${e.javaClass.simpleName}: ${e.message.orEmpty()}", key, to).take(200))

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
        /** Brevo's replies are short; stop reading after this much. */
        private const val MAX_BODY = 64 * 1024
        private val LOOPBACK = setOf("127.0.0.1", "localhost", "[::1]", "::1")
    }
}
