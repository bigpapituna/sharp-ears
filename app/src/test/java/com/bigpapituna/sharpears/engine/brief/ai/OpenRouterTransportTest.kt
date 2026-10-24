package com.bigpapituna.sharpears.engine.brief.ai

import com.bigpapituna.sharpears.engine.MiniJson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Collections

/**
 * The HTTP client against a local fake server (never the real API): the request's shape and headers, the key only in
 * the Authorization header and never in a message or a log, the retry rules, the metered-network and no-key gates.
 */
class OpenRouterTransportTest {
    /** Not a real key: shaped like one so the scrubbing rules are exercised. */
    private val key = "sk-or-v1-0000fake0000key0000for0000tests0000"
    private val logs: MutableList<String> = Collections.synchronizedList(ArrayList())
    private val servers = ArrayList<FakeHttpServer>()

    @After fun closeServers() = servers.forEach { it.close() }

    private fun server(handler: (FakeHttpServer.Recorded) -> FakeHttpServer.Reply) = FakeHttpServer(handler).also { servers += it }

    private fun transport(s: FakeHttpServer, keyProvider: () -> String? = { key }, allowed: () -> Boolean = { true }) =
        OpenRouterTransport(s.baseUrl, keyProvider, "anthropic/claude-haiku-5.5", allowed, log = { logs += it })

    private fun request(json: Boolean = true, retryOnce: Boolean = false, timeoutMs: Long = 3_000) =
        AiRequest("", "the system prompt", "the user message", json, 300, 0.4, timeoutMs, retryOnce)

    private fun call(t: AiTransport, r: AiRequest = request()): AiCallException =
        try { t.complete(r); fail("expected a failure"); throw IllegalStateException() } catch (e: AiCallException) { e }

    /** No eight characters in a row of the key anywhere in [text]. */
    private fun assertNoKey(text: String) {
        for (i in 0..key.length - 8) assertFalse("a piece of the key leaked: ${key.substring(i, i + 8)}", text.contains(key.substring(i, i + 8)))
    }

    @Test fun aCallSendsTheChatShapeWithTheKeyOnlyInTheAuthorizationHeader() {
        val s = server { FakeHttpServer.Reply(200, FakeHttpServer.chat("{\"headline\":\"hi\"}")) }
        assertEquals("{\"headline\":\"hi\"}", transport(s).complete(request()))
        val r = s.requests.single()
        assertEquals("POST", r.method)
        assertEquals("/api/v1/chat/completions", r.path)
        assertEquals("Bearer $key", r.headers["authorization"])
        assertEquals("Sharp Ears", r.headers["x-title"])
        assertEquals(OpenRouterTransport.APP_REFERER, r.headers["http-referer"])
        assertTrue(r.headers["content-type"]!!.startsWith("application/json"))
        assertFalse("the key is not in the body", r.body.contains(key))
        val body = MiniJson.read(r.body) as Map<*, *>
        assertEquals("anthropic/claude-haiku-5.5", body["model"])         // the request's blank model falls back to the transport's
        val messages = body["messages"] as List<*>
        assertEquals(listOf("system", "user"), messages.map { (it as Map<*, *>)["role"] })
        assertEquals(listOf("the system prompt", "the user message"), messages.map { (it as Map<*, *>)["content"] })
        assertEquals(mapOf("type" to "json_object"), body["response_format"])
        assertEquals(mapOf("data_collection" to "deny"), body["provider"])
        assertEquals(300L, body["max_tokens"])
        assertEquals(0.4, body["temperature"])
        assertTrue(logs.single().startsWith("ai: reply in "))
        logs.forEach(::assertNoKey)
    }

    @Test fun jsonModeOnlyWhenTheRequestAsksAndTheRequestsModelWins() {
        val s = server { FakeHttpServer.Reply(200, FakeHttpServer.chat("plain words")) }
        assertEquals("plain words", transport(s).complete(request(json = false).copy(model = "another/model")))
        val body = MiniJson.read(s.requests.single().body) as Map<*, *>
        assertNull(body["response_format"])
        assertEquals("another/model", body["model"])
    }

    @Test fun theKeyIsReadAtCallTime() {
        var current = "sk-or-v1-first0000000000"
        val s = server { FakeHttpServer.Reply(200, FakeHttpServer.chat("ok")) }
        val t = transport(s, keyProvider = { current })
        t.complete(request())
        current = "sk-or-v1-second000000000"
        t.complete(request())
        assertEquals(listOf("Bearer sk-or-v1-first0000000000", "Bearer sk-or-v1-second000000000"), s.requests.map { it.headers["authorization"] })
    }

    @Test fun aRefusalThatEchoesTheKeyNeverLeaksItIntoTheMessageOrTheLogs() {
        for (status in listOf(400, 401, 403, 429, 500, 200)) {
            logs.clear()
            val s = server { r ->
                val echo = "Bad header Authorization: ${r.headers["authorization"]}; the key ${key}; its start ${key.take(20)}"
                val error = MiniJson.write(mapOf("error" to mapOf("message" to echo, "code" to status)))
                FakeHttpServer.Reply(status, error)
            }
            val e = call(transport(s))
            assertEquals(status, e.status)
            assertNoKey(e.message.orEmpty())
            assertNoKey(e.toString())
            assertNoKey(e.stackTraceToString())
            assertNull("no cause that could carry the key", e.cause)
            assertTrue(logs.isNotEmpty())
            logs.forEach(::assertNoKey)
            // The provider's words are kept, scrubbed, for Roy to read in logcat. (The JVM's HttpURLConnection drops a 401's
            // body in streaming mode, so a 401 here says only its status; Android's client keeps it.)
            if (status != 401) assertTrue(e.message!!, e.message!!.contains("Bad header Authorization: Bearer [key]"))
        }
        // A body that is not JSON adds nothing to the message at all.
        val raw = server { r -> FakeHttpServer.Reply(400, "Authorization: ${r.headers["authorization"]}") }
        val e = call(transport(raw))
        assertEquals("HTTP 400", e.message)
    }

    @Test fun aFourHundredOrA429IsNeverRetried() {
        for ((status, kind) in listOf(400 to AiCallException.Kind.CLIENT, 429 to AiCallException.Kind.RATE_LIMITED, 404 to AiCallException.Kind.CLIENT)) {
            val s = server { FakeHttpServer.Reply(status, "{\"error\":{\"message\":\"no\"}}") }
            val e = call(transport(s), request(retryOnce = true))
            assertEquals(kind, e.kind)
            assertEquals(1, s.requests.size)
        }
    }

    @Test fun aServerErrorIsRetriedOnceOnlyWhenTheRequestSays() {
        val failing = server { FakeHttpServer.Reply(503, "{\"error\":{\"message\":\"busy\"}}") }
        assertEquals(AiCallException.Kind.SERVER, call(transport(failing), request(retryOnce = false)).kind)
        assertEquals(1, failing.requests.size)
        assertEquals(AiCallException.Kind.SERVER, call(transport(failing), request(retryOnce = true)).kind)
        assertEquals(3, failing.requests.size)      // one more, once

        var n = 0
        val flaky = server { if (n++ == 0) FakeHttpServer.Reply(502) else FakeHttpServer.Reply(200, FakeHttpServer.chat("second time")) }
        assertEquals("second time", transport(flaky).complete(request(retryOnce = true)))
        assertEquals(2, flaky.requests.size)
    }

    @Test fun aTimeoutIsATimeoutAndIsRetriedOnceWhenAsked() {
        val slow = server { FakeHttpServer.Reply(200, FakeHttpServer.chat("late"), delayMs = 1_500) }
        val started = System.nanoTime()
        assertEquals(AiCallException.Kind.TIMEOUT, call(transport(slow), request(timeoutMs = 200)).kind)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1_400)
        assertEquals(1, slow.requests.size)
        assertEquals(AiCallException.Kind.TIMEOUT, call(transport(slow), request(timeoutMs = 200, retryOnce = true)).kind)
        assertEquals(3, slow.requests.size)
    }

    @Test fun nothingIsSentOnAMeteredNetworkOrWithoutAKey() {
        val s = server { FakeHttpServer.Reply(200, FakeHttpServer.chat("never")) }
        var asked = 0
        val metered = call(transport(s, keyProvider = { asked++; key }, allowed = { false }))
        assertEquals(AiCallException.Kind.NOT_ALLOWED, metered.kind)
        assertEquals("the key is not even read", 0, asked)
        assertEquals(AiCallException.Kind.NO_KEY, call(transport(s, keyProvider = { "" })).kind)
        assertEquals(AiCallException.Kind.NO_KEY, call(transport(s, keyProvider = { null })).kind)
        assertEquals(AiCallException.Kind.NO_KEY, call(transport(s, keyProvider = { "   " })).kind)
        assertTrue(s.requests.isEmpty())
        assertFalse(AiCallException.Kind.NOT_ALLOWED.billed || AiCallException.Kind.NO_KEY.billed)
    }

    @Test fun theKeyGoesOnlyOverHttpsOrToThisDevicesLoopback() {
        val t = OpenRouterTransport("http://example.com/api/v1", { key }, "m", { true })
        val e = call(t)
        assertEquals(AiCallException.Kind.CONFIG, e.kind)
        assertEquals(AiCallException.Kind.CONFIG, call(OpenRouterTransport("not a url", { key }, "m", { true })).kind)
    }

    @Test fun redirectsAreNotFollowed() {
        val s = server { r -> if (r.path.endsWith("/chat/completions")) FakeHttpServer.Reply(302, headers = mapOf("Location" to "/elsewhere"))
                              else FakeHttpServer.Reply(200, FakeHttpServer.chat("followed")) }
        assertEquals(AiCallException.Kind.CLIENT, call(transport(s)).kind)
        assertEquals(1, s.requests.size)
    }

    @Test fun repliesInPartsErrorsInPlaceOfARepliesAndEmptyReplies() {
        val parts = MiniJson.write(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to listOf(
            mapOf("type" to "text", "text" to "one "), mapOf("type" to "text", "text" to "two")))))))
        assertEquals("one two", transport(server { FakeHttpServer.Reply(200, parts) }).complete(request()))
        assertEquals(AiCallException.Kind.BAD_REPLY, call(transport(server { FakeHttpServer.Reply(200, FakeHttpServer.chat("  ")) })).kind)
        assertEquals(AiCallException.Kind.BAD_REPLY, call(transport(server { FakeHttpServer.Reply(200, "<html>") })).kind)
        assertEquals(AiCallException.Kind.BAD_REPLY, call(transport(server { FakeHttpServer.Reply(200, "{\"choices\":[]}") })).kind)
        assertEquals(AiCallException.Kind.SERVER, call(transport(server { FakeHttpServer.Reply(200, "{\"error\":{\"message\":\"upstream\"}}") })).kind)
    }

    @Test fun aServerThatIsNotThereIsANetworkFault() {
        val s = server { FakeHttpServer.Reply(200) }
        val url = s.baseUrl
        s.close()
        val e = call(OpenRouterTransport(url, { key }, "m", { true }, log = { logs += it }))
        assertEquals(AiCallException.Kind.NETWORK, e.kind)
        assertFalse(e.kind.billed)
        assertNoKey(e.message.orEmpty())
    }

    /** A reply stopped at the cap (2.8.4, QA D2): [content] and the usage block of a model that thought first. */
    private fun cut(content: String) = MiniJson.write(mapOf(
        "choices" to listOf(mapOf("finish_reason" to "length", "message" to mapOf("role" to "assistant", "content" to content))),
        "usage" to mapOf("completion_tokens" to 300, "completion_tokens_details" to mapOf("reasoning_tokens" to 300)),
    ))

    @Test fun aReplyCutShortAtTheCapIsAskedOnceMoreWithTheLargerCap() {
        var n = 0
        val good = """{"headline":"hi"}"""
        val once = server { if (n++ == 0) FakeHttpServer.Reply(200, cut("")) else FakeHttpServer.Reply(200, FakeHttpServer.chat(good)) }
        assertEquals(good, transport(once).complete(request().copy(retryTokens = 900)))
        assertEquals(listOf(300L, 900L), once.requests.map { (MiniJson.read(it.body) as Map<*, *>)["max_tokens"] })
        assertTrue(logs.any { it.contains("CUT_SHORT") && it.contains("the reply was empty (finish: length, 300 tokens, 300 thinking)") })
        assertTrue(logs.any { it == "ai: asking again with 900 tokens" })

        // Cut again: it gives up after the second try, and the caller falls to the templates.
        val always = server { FakeHttpServer.Reply(200, cut("""{"headline":"half""")) }
        val e = call(transport(always), request().copy(retryTokens = 900))
        assertEquals(AiCallException.Kind.CUT_SHORT, e.kind)
        assertTrue(e.kind.billed)
        assertTrue(e.message!!.startsWith("the reply was cut short"))
        assertEquals(2, always.requests.size)

        // No larger cap (the session note, the child's card): no second try.
        val single = server { FakeHttpServer.Reply(200, cut("")) }
        assertEquals(AiCallException.Kind.CUT_SHORT, call(transport(single), request(retryOnce = true)).kind)
        assertEquals(1, single.requests.size)
        assertEquals(1, request().attempts)
        assertEquals(2, request().copy(retryTokens = 900).attempts)
        assertEquals(2, request(retryOnce = true).attempts)
    }

    @Test fun oneMoreTryInAllEvenWhenAServerErrorComesFirst() {
        var n = 0
        val s = server { if (n++ == 0) FakeHttpServer.Reply(503) else FakeHttpServer.Reply(200, cut("")) }
        assertEquals(AiCallException.Kind.CUT_SHORT, call(transport(s), request(retryOnce = true).copy(retryTokens = 900)).kind)
        assertEquals(2, s.requests.size)
    }

    @Test fun scrubbingRemovesTheKeyItsPiecesAndAnythingShapedLikeOne() {
        val text = "a $key b Bearer xyz.123 c ${key.substring(10, 30)} d sk-or-v1-somethingelse e ${"A".repeat(40)} f hello"
        val out = Secrets.scrub(text, key)
        assertNoKey(out)
        assertFalse(out.contains("xyz.123") || out.contains("somethingelse") || out.contains("AAAAAAAA"))
        assertTrue(out.startsWith("a [key] b Bearer [key] c [key] d [key] e [token] f hello"))
    }
}
