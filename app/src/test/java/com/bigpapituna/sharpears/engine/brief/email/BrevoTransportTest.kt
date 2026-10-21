package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.MiniJson
import com.bigpapituna.sharpears.engine.brief.ai.FakeHttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64
import java.util.Collections

/**
 * The Brevo client against a local fake server (never the real API, no test may reach Brevo): the request's shape, the
 * key only in the api-key header and never in a message or a log, the address never in a message or a log, the retry
 * rules, the metered-network and no-key gates.
 */
class BrevoTransportTest {
    /** Not a real key: shaped like a Brevo one so the scrubbing rules are exercised. */
    private val key = "xkeysib-0000fake0000key0000for0000tests0000aaaa0000bbbb0000cccc0000dd-FAKEfake0000FAKE"
    private val to = "parent.of.mia@example.com"
    private val logs: MutableList<String> = Collections.synchronizedList(ArrayList())
    private val servers = ArrayList<FakeHttpServer>()

    @After fun closeServers() = servers.forEach { it.close() }

    private fun server(handler: (FakeHttpServer.Recorded) -> FakeHttpServer.Reply) = FakeHttpServer(handler).also { servers += it }

    private fun transport(s: FakeHttpServer, keyProvider: () -> String? = { key }, allowed: () -> Boolean = { true }, timeoutMs: Int = 3_000,
                          sender: String = "pup@sharpears.example") =
        BrevoTransport(s.baseUrl, keyProvider, "The pup at Sharp Ears", sender, allowed, timeoutMs, log = { logs += it })

    private val pdf = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37, 0, -1, 10)
    private fun message(attachment: ByteArray? = pdf) =
        EmailMessage(to, "Mia's week at the piano, from the pup", "<p>Hello</p>", "Hello", attachment, "Mia-weekly-brief-2026-10-21.pdf")

    private fun call(t: EmailTransport, m: EmailMessage = message()): EmailSendException =
        try { t.send(m); fail("expected a failure"); throw IllegalStateException() } catch (e: EmailSendException) { e }

    /** No eight characters in a row of the key, and not the address, anywhere in [text]. */
    private fun assertClean(text: String) {
        for (i in 0..key.length - 8) assertFalse("a piece of the key leaked: ${key.substring(i, i + 8)}", text.contains(key.substring(i, i + 8)))
        assertFalse("the address leaked: $text", text.contains(to, ignoreCase = true) || text.contains("parent.of.mia"))
    }

    @Test fun aSendPostsBrevosShapeWithTheKeyOnlyInItsHeader() {
        val s = server { FakeHttpServer.Reply(201, "{\"messageId\":\"<202610211830.1@smtp-relay.mailin.fr>\"}") }
        assertEquals("<202610211830.1@smtp-relay.mailin.fr>", transport(s).send(message()))
        val r = s.requests.single()
        assertEquals("POST", r.method)
        assertEquals("/api/v1/smtp/email", r.path)
        assertEquals(key, r.headers["api-key"])
        assertNull("no bearer, no other credential", r.headers["authorization"])
        assertTrue(r.headers["content-type"]!!.startsWith("application/json"))
        assertFalse("the key is not in the body", r.body.contains(key))
        val body = MiniJson.read(r.body) as Map<*, *>
        assertEquals(mapOf("name" to "The pup at Sharp Ears", "email" to "pup@sharpears.example"), body["sender"])
        assertEquals(listOf(mapOf("email" to to)), body["to"])
        assertEquals("Mia's week at the piano, from the pup", body["subject"])
        assertEquals("<p>Hello</p>", body["htmlContent"]); assertEquals("Hello", body["textContent"])
        val att = (body["attachment"] as List<*>).single() as Map<*, *>
        assertEquals("Mia-weekly-brief-2026-10-21.pdf", att["name"])
        assertArrayEquals(pdf, Base64.getDecoder().decode(att["content"] as String))
        assertTrue(logs.single().startsWith("email: sent in "))
        logs.forEach(::assertClean)
        val plain = server { FakeHttpServer.Reply(201, "{}") }
        transport(plain).send(message(attachment = null))
        assertNull("no attachment, no field", (MiniJson.read(plain.requests.single().body) as Map<*, *>)["attachment"])
    }

    @Test fun theKeyIsReadAtCallTimeAndAnAcceptedSendIsSentWhateverItsBody() {
        var current = "xkeysib-first00000000000000"
        val s = server { FakeHttpServer.Reply(201, "not json") }
        val t = transport(s, keyProvider = { current })
        assertEquals("accepted", t.send(message()))
        current = "xkeysib-second0000000000000"
        t.send(message())
        assertEquals(listOf("xkeysib-first00000000000000", "xkeysib-second0000000000000"), s.requests.map { it.headers["api-key"] })
    }

    @Test fun aRefusalThatEchoesTheKeyAndTheAddressNeverLeaksThem() {
        for (status in listOf(400, 401, 402, 403, 429, 500)) {
            logs.clear()
            val s = server { r ->
                val echo = "Bad header api-key: ${r.headers["api-key"]}; the key $key; its start ${key.take(24)}; recipient $to is blocked"
                FakeHttpServer.Reply(status, MiniJson.write(mapOf("code" to "invalid_parameter", "message" to echo)))
            }
            val e = call(transport(s))
            assertEquals(status, e.status)
            assertClean(e.message.orEmpty()); assertClean(e.toString()); assertClean(e.stackTraceToString())
            assertNull("no cause that could carry the key", e.cause)
            assertTrue(logs.isNotEmpty())
            logs.forEach(::assertClean)
            if (status != 401) assertTrue(e.message!!, e.message!!.contains("invalid_parameter: Bad header api-key: [key]") && e.message!!.contains("recipient [address] is blocked"))
        }
        val raw = server { r -> FakeHttpServer.Reply(400, "api-key: ${r.headers["api-key"]} for $to") }
        assertEquals("HTTP 400", call(transport(raw)).message)
    }

    @Test fun aFourHundredOrA429IsNeverRetriedAndAServerErrorOnce() {
        for ((status, kind) in listOf(400 to EmailSendException.Kind.CLIENT, 402 to EmailSendException.Kind.CLIENT, 429 to EmailSendException.Kind.RATE_LIMITED)) {
            val s = server { FakeHttpServer.Reply(status, "{\"code\":\"no\",\"message\":\"no\"}") }
            assertEquals(kind, call(transport(s)).kind)
            assertEquals(1, s.requests.size)
        }
        val failing = server { FakeHttpServer.Reply(503, "{\"message\":\"busy\"}") }
        assertEquals(EmailSendException.Kind.SERVER, call(transport(failing)).kind)
        assertEquals(2, failing.requests.size)
        var n = 0
        val flaky = server { if (n++ == 0) FakeHttpServer.Reply(502) else FakeHttpServer.Reply(201, "{\"messageId\":\"<2>\"}") }
        assertEquals("<2>", transport(flaky).send(message()))
        assertEquals(2, flaky.requests.size)
    }

    @Test fun aTimeoutIsNotRetriedBecauseTheEmailMayHaveGone() {
        val slow = server { FakeHttpServer.Reply(201, "{}", delayMs = 1_500) }
        val started = System.nanoTime()
        assertEquals(EmailSendException.Kind.TIMEOUT, call(transport(slow, timeoutMs = 200)).kind)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1_400)
        assertEquals(1, slow.requests.size)
    }

    @Test fun nothingIsSentOnAMeteredNetworkWithoutAKeyOrWithoutASender() {
        val s = server { FakeHttpServer.Reply(201, "{}") }
        var asked = 0
        assertEquals(EmailSendException.Kind.NOT_ALLOWED, call(transport(s, keyProvider = { asked++; key }, allowed = { false })).kind)
        assertEquals("the key is not even read", 0, asked)
        for (k in listOf("", null, "   ")) assertEquals(EmailSendException.Kind.NO_KEY, call(transport(s, keyProvider = { k })).kind)
        assertEquals(EmailSendException.Kind.CONFIG, call(transport(s, sender = " ")).kind)
        assertTrue(s.requests.isEmpty())
    }

    @Test fun theKeyGoesOnlyOverHttpsOrToThisDevicesLoopbackAndRedirectsAreNotFollowed() {
        assertEquals(EmailSendException.Kind.CONFIG, call(BrevoTransport("http://api.brevo.com/v3", { key }, "pup", "pup@x.example", { true })).kind)
        assertEquals(EmailSendException.Kind.CONFIG, call(BrevoTransport("not a url", { key }, "pup", "pup@x.example", { true })).kind)
        val s = server { r -> if (r.path.endsWith("/smtp/email")) FakeHttpServer.Reply(302, headers = mapOf("Location" to "/elsewhere")) else FakeHttpServer.Reply(201, "{}") }
        assertEquals(EmailSendException.Kind.CLIENT, call(transport(s)).kind)
        assertEquals(1, s.requests.size)
    }

    @Test fun aServerThatIsNotThereIsANetworkFault() {
        val s = server { FakeHttpServer.Reply(201) }
        val url = s.baseUrl
        s.close()
        val e = call(BrevoTransport(url, { key }, "pup", "pup@x.example", { true }, log = { logs += it }))
        assertEquals(EmailSendException.Kind.NETWORK, e.kind)
        assertClean(e.message.orEmpty())
        assertFalse("a message never prints its address", message().toString().contains("@"))
    }

    @Test fun scrubbingRemovesTheAddressAnythingLikeOneAndTheKey() {
        val out = EmailScrub.scrub("to $to, cc Other.Person@Mail.example.org, key $key, piece ${key.substring(20, 40)}", key, to)
        assertClean(out)
        assertFalse(out.contains("Other.Person"))
        assertTrue(out, out.startsWith("to [address], cc [address], key [key]"))
    }
}
