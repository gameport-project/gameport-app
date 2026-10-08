package app.gameport.core.sync

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatTransportTest {
    private var server: HttpServer? = null

    @After
    fun stop() {
        server?.stop(0)
    }

    private fun serve(status: Int, body: String): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/summary") { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        http.start()
        server = http
        return "http://127.0.0.1:${http.address.port}/summary"
    }

    @Test
    fun `an answer 200 is read as it is`() = runBlocking {
        assertEquals("""{"v":1,"games":[]}""", HttpSummaryTransport(serve(200, """{"v":1,"games":[]}""")).get())
    }

    @Test
    fun `any other answer is nothing, so what is kept stays`() = runBlocking {
        for (status in listOf(404, 429, 500, 502)) {
            assertNull("status $status", HttpSummaryTransport(serve(status, """{"error":"x"}""")).get())
            server?.stop(0)
        }
    }

    @Test
    fun `a relay that cannot be reached is nothing, not a crash`() = runBlocking {
        val address = serve(200, "{}")
        server?.stop(0)
        assertNull(HttpSummaryTransport(address).get())
    }

    @Test
    fun `a debug build reads the test totals and a release build the real ones`() {
        assertTrue(HttpSummaryTransport.addressFor(debuggable = true).endsWith("/test/summary"))
        assertTrue(HttpSummaryTransport.addressFor(debuggable = false).endsWith("/summary"))
        assertTrue(!HttpSummaryTransport.addressFor(debuggable = false).contains("/test/"))
    }
}
