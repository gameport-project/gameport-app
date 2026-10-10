package app.gameport.core.sync

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReportSenderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val zip = byteArrayOf(0x50, 0x4b, 0x03, 0x04, 1, 2, 3, -6, -5, -1)
    private val meta = ReportMeta(846470, "0.7.4", "quest", "0123456789abcdef0123456789abcdef")
    private fun outbox(now: () -> Long = System::currentTimeMillis) = ReportOutbox(File(temp.root, "outbox"), now)

    @Test
    fun `a report kept is read back as it was, and forgotten when removed`() {
        val box = outbox()
        assertNull(box.read(1))
        box.put(1, zip)
        assertArrayEquals(zip, box.read(1))
        assertEquals(listOf(1), box.waiting())
        box.remove(1)
        assertNull(box.read(1))
        assertTrue(box.waiting().isEmpty())
    }

    @Test
    fun `a new report for the same game replaces the old one`() {
        val box = outbox()
        box.put(1, zip)
        box.put(1, byteArrayOf(9, 9))
        assertArrayEquals(byteArrayOf(9, 9), box.read(1))
        assertEquals(listOf(1), box.waiting())
    }

    @Test
    fun `only the five newest reports are kept`() {
        var clock = 1_000_000_000L
        val box = outbox { clock }
        for (id in 1..7) {
            box.put(id, zip)
            File(temp.root, "outbox/$id.zip").setLastModified(clock)
            clock += 1_000
        }
        box.put(8, zip)
        assertEquals(5, box.waiting().size)
        assertTrue(8 in box.waiting())
        assertFalse(1 in box.waiting())
    }

    @Test
    fun `a report older than two weeks is dropped the next time one is kept`() {
        val now = System.currentTimeMillis()
        val box = outbox { now }
        box.put(1, zip)
        File(temp.root, "outbox/1.zip").setLastModified(now - ReportOutbox.MAX_AGE_MS - 1)
        box.put(2, zip)
        File(temp.root, "outbox/2.zip").setLastModified(now)
        box.put(3, zip)
        assertEquals(setOf(2, 3), box.waiting().toSet())
    }

    @Test
    fun `a report cut short is never taken for a whole one`() {
        val box = outbox()
        File(temp.root, "outbox").mkdirs()
        File(temp.root, "outbox/5.zip.part").writeBytes(zip)
        assertNull(box.read(5))
        assertTrue(box.waiting().isEmpty())
    }

    @Test
    fun `a report the relay took is forgotten, one it will never take too, one it could not reach is kept`() = runBlocking {
        val box = outbox()
        box.put(meta.appId, zip)
        assertEquals(ReportStatus.WAITING, deliverReport(box, meta, { _, _ -> Delivery.LATER }))
        assertNotNull(box.read(meta.appId))
        assertEquals(ReportStatus.SENT, deliverReport(box, meta, { _, _ -> Delivery.SENT }))
        assertNull(box.read(meta.appId))
        box.put(meta.appId, zip)
        assertEquals(ReportStatus.REFUSED, deliverReport(box, meta, { _, _ -> Delivery.REFUSED }))
        assertNull(box.read(meta.appId))
    }

    @Test
    fun `nothing kept is nothing to send, and the relay is not asked`() = runBlocking {
        var asked = 0
        assertEquals(ReportStatus.UNAVAILABLE, deliverReport(outbox(), meta, { _, _ -> asked++; Delivery.SENT }))
        assertEquals(0, asked)
    }

    @Test
    fun `the transport receives the zip as it was kept, with the facts of the report`() = runBlocking {
        val box = outbox()
        box.put(meta.appId, zip)
        var got: Pair<ReportMeta, ByteArray>? = null
        deliverReport(box, meta, { m, bytes -> got = m to bytes; Delivery.SENT })
        assertEquals(meta, got!!.first)
        assertArrayEquals(zip, got!!.second)
    }

    private var server: HttpServer? = null

    @After
    fun stop() {
        server?.stop(0)
    }

    private data class Seen(val method: String, val type: String?, val query: String, val body: ByteArray)

    private fun serve(status: Int, seen: MutableList<Seen>): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/report") { exchange ->
            seen += Seen(exchange.requestMethod, exchange.requestHeaders.getFirst("Content-Type"), exchange.requestURI.rawQuery.orEmpty(), exchange.requestBody.readBytes())
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        http.start()
        server = http
        return "http://127.0.0.1:${http.address.port}/report"
    }

    @Test
    fun `the relay answering 204 got the report, sent by POST as a zip with its facts in the address`() = runBlocking {
        val seen = mutableListOf<Seen>()
        assertEquals(Delivery.SENT, HttpReportTransport(serve(204, seen)).post(meta, zip))
        assertEquals(1, seen.size)
        assertEquals("POST", seen[0].method)
        assertEquals("application/zip", seen[0].type)
        assertEquals("appId=846470&app=0.7.4&device=quest&voter=${meta.voter}", seen[0].query)
        assertArrayEquals(zip, seen[0].body)
    }

    @Test
    fun `the relay refusing the report is not asked again, a busy relay or none is asked later`() = runBlocking {
        assertEquals(Delivery.REFUSED, HttpReportTransport(serve(400, mutableListOf())).post(meta, zip))
        assertEquals(Delivery.REFUSED, HttpReportTransport(serve(413, mutableListOf())).post(meta, zip))
        assertEquals(Delivery.LATER, HttpReportTransport(serve(429, mutableListOf())).post(meta, zip))
        assertEquals(Delivery.LATER, HttpReportTransport(serve(500, mutableListOf())).post(meta, zip))
        server?.stop(0)
        assertEquals(Delivery.LATER, HttpReportTransport("http://127.0.0.1:1/report").post(meta, zip))
    }

    @Test
    fun `a test build sends to the test route and a release build to the real one`() {
        assertTrue(HttpReportTransport.addressFor(true).endsWith("/test/report"))
        assertTrue(HttpReportTransport.addressFor(false).endsWith("/report"))
        assertFalse(HttpReportTransport.addressFor(false).contains("/test/"))
    }
}
