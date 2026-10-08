package app.gameport.core.sync

import app.gameport.core.model.Answer
import app.gameport.core.model.Verdict
import app.gameport.core.model.VoteMessage
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerdictSenderTest {
    private val voter = "0123456789abcdef0123456789abcdef"
    private fun message(appId: Int, verdict: Verdict) = VoteMessage(appId, verdict, "0.7.2", "quest", voter)
    private fun message(answer: Answer) = message(answer.appId, answer.verdict).copy(offline = answer.offline)

    @Test
    fun `every answer is sent in order and each one is told it got through`() = runBlocking {
        val sent = mutableListOf<String>()
        val told = mutableListOf<Answer>()
        sendAnswers(listOf(Answer(1, Verdict.WORKS, true), Answer(2, Verdict.FAILS, false)), ::message, { sent += it; Delivery.SENT }) { told += it }
        assertEquals(2, sent.size)
        assertTrue(sent[0].contains("\"appId\":1") && sent[0].contains("\"verdict\":\"works\"") && sent[0].contains("\"offline\":true"))
        assertTrue(sent[1].contains("\"appId\":2") && sent[1].contains("\"verdict\":\"fails\"") && !sent[1].contains("offline"))
        assertEquals(listOf(Answer(1, Verdict.WORKS, true), Answer(2, Verdict.FAILS, false)), told)
    }

    @Test
    fun `it stops at the first answer that does not get through and tells nothing about the others`() = runBlocking {
        val told = mutableListOf<Int>()
        var calls = 0
        val three = listOf(Answer(1, Verdict.WORKS, false), Answer(2, Verdict.FAILS, false), Answer(3, Verdict.WORKS, false))
        sendAnswers(three, ::message, { calls++; if (calls == 1) Delivery.SENT else Delivery.LATER }) { told += it.appId }
        assertEquals(2, calls)
        assertEquals(listOf(1), told)
    }

    @Test
    fun `nothing waiting sends nothing`() = runBlocking {
        var calls = 0
        sendAnswers(emptyList(), ::message, { calls++; Delivery.SENT }) { }
        assertEquals(0, calls)
    }

    private var server: HttpServer? = null

    @After
    fun stop() {
        server?.stop(0)
    }

    private fun serve(status: Int, seen: MutableList<Triple<String, String?, String>>): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/vote") { exchange ->
            seen += Triple(exchange.requestMethod, exchange.requestHeaders.getFirst("Content-Type"), exchange.requestBody.readBytes().decodeToString())
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        http.start()
        server = http
        return "http://127.0.0.1:${http.address.port}/vote"
    }

    @Test
    fun `the relay answering 204 is a message that got through, sent as json by POST`() = runBlocking {
        val seen = mutableListOf<Triple<String, String?, String>>()
        val transport = HttpVoteTransport(serve(204, seen))
        assertEquals(Delivery.SENT, transport.post(message(1, Verdict.WORKS).toJson()))
        assertEquals(1, seen.size)
        assertEquals("POST", seen[0].first)
        assertEquals("application/json", seen[0].second)
        assertEquals(message(1, Verdict.WORKS).toJson(), seen[0].third)
    }

    @Test
    fun `any other answer from the relay is not a message that got through`() = runBlocking {
        for (status in listOf(200, 429, 500, 502)) {
            val transport = HttpVoteTransport(serve(status, mutableListOf()))
            assertEquals("status $status", Delivery.LATER, transport.post(message(1, Verdict.WORKS).toJson()))
            server?.stop(0)
        }
    }

    @Test
    fun `an answer the relay refuses is given up, so it cannot block the ones after it`() = runBlocking {
        for (status in listOf(400, 413)) {
            assertEquals("status $status", Delivery.REFUSED, HttpVoteTransport(serve(status, mutableListOf())).post(message(1, Verdict.WORKS).toJson()))
            server?.stop(0)
        }
        val told = mutableListOf<Int>()
        var calls = 0
        sendAnswers(listOf(Answer(1, Verdict.WORKS, false), Answer(2, Verdict.FAILS, false)), ::message, { calls++; if (calls == 1) Delivery.REFUSED else Delivery.SENT }) { told += it.appId }
        assertEquals(listOf(1, 2), told)
    }

    @Test
    fun `a relay that cannot be reached is a message that did not get through, not a crash`() = runBlocking {
        val seen = mutableListOf<Triple<String, String?, String>>()
        val address = serve(204, seen)
        server?.stop(0)
        assertEquals(Delivery.LATER, HttpVoteTransport(address).post(message(1, Verdict.WORKS).toJson()))
    }

    @Test
    fun `a debug build sends to the test route and a release build to the real one`() {
        assertTrue(HttpVoteTransport.addressFor(debuggable = true).endsWith("/test/vote"))
        assertTrue(HttpVoteTransport.addressFor(debuggable = false).endsWith("/vote"))
        assertFalse(HttpVoteTransport.addressFor(debuggable = false).contains("/test/"))
        assertEquals("https://gameport-relay.gameport.workers.dev/vote", HttpVoteTransport.RELAY_VOTE_URL)
    }
}
