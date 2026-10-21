package com.bigpapituna.sharpears.engine.brief.ai

/**
 * How the AI writer reaches a model: one chat completion in, the reply's text out. [OpenRouterTransport] is the real
 * one (HTTPS to OpenRouter); tests inject a fake. A transport never logs or throws the key, the prompt or the reply.
 */
fun interface AiTransport {
    /** The assistant's reply text. Throws [AiCallException] (and nothing else) when there is none. */
    fun complete(request: AiRequest): String
}

/**
 * One chat completion: a [system] prompt and one [user] message (Prompts.kt builds both from the redacted findings).
 * [json] asks for JSON mode where the model supports it. [timeoutMs] bounds the connection and each read;
 * [retryOnce] allows one more attempt after a server error or a timeout (the weekly brief only), never after a 4xx.
 */
data class AiRequest(
    val model: String,
    val system: String,
    val user: String,
    val json: Boolean,
    val maxTokens: Int,
    val temperature: Double,
    val timeoutMs: Long,
    val retryOnce: Boolean = false,
)

/**
 * Why a call produced no reply. The message is safe to log: it never holds the key (see [Secrets.scrub]), the prompt or
 * the reply, and the exception has no cause attached (a platform exception's text is scrubbed into the message instead).
 */
class AiCallException(val kind: Kind, val status: Int? = null, message: String) : Exception(message) {
    enum class Kind(val billed: Boolean) {
        /** The network is metered and the person did not allow it, so nothing was sent. */
        NOT_ALLOWED(false),
        /** This build has no key (BuildConfig.AI_KEY is empty), so nothing was sent. */
        NO_KEY(false),
        /** The base URL is not HTTPS (plain HTTP is allowed to this device's loopback only, for tests). */
        CONFIG(false),
        /** HTTP 429: too many requests, or the key's spending cap reached. */
        RATE_LIMITED(false),
        /** Any other 4xx (or a redirect): the request was refused. Never retried. */
        CLIENT(false),
        /** 5xx, or an error object in place of a reply. */
        SERVER(false),
        /** No reply in time. The provider may have answered (and billed) after we stopped listening. */
        TIMEOUT(true),
        /** No connection (offline, DNS, TLS, reset). */
        NETWORK(false),
        /** A 2xx whose body held no reply text. */
        BAD_REPLY(true),
    }
}

/** Keeps a key out of anything that may be logged, shown or thrown. */
object Secrets {
    private val BEARER = Regex("(?i)bearer\\s+\\S+")
    private val OPENROUTER = Regex("sk-[A-Za-z0-9_-]{6,}")
    private val TOKEN = Regex("[A-Za-z0-9_-]{6,}")
    private val OPAQUE = Regex("[A-Za-z0-9_-]{32,}")

    /**
     * [text] with [key], any piece of it six characters or longer, any "Bearer ..." credential, anything shaped like an
     * OpenRouter key and any opaque token of 32 characters or more replaced. Used on every message an error carries,
     * including a provider's error text, which may echo the request's headers back.
     */
    fun scrub(text: String, key: String?): String {
        var t = text
        val k = key?.trim().orEmpty()
        if (k.isNotEmpty()) t = t.replace(k, "[key]")
        t = BEARER.replace(t, "Bearer [key]")
        t = OPENROUTER.replace(t, "[key]")
        if (k.isNotEmpty()) t = TOKEN.replace(t) { m -> if (k.contains(m.value)) "[key]" else m.value }
        return OPAQUE.replace(t, "[token]")
    }
}
