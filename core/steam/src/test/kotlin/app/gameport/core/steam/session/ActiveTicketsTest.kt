package app.gameport.core.steam.session

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveTicketsTest {
    private fun entry(crc: Int, appId: Int = 1, serverSecret: ByteArray? = null) = ActiveTickets.Entry(appId, crc, ByteArray(4), serverSecret)

    @Test
    fun `a new ticket is listed after the ones given before`() {
        val tickets = ActiveTickets()
        tickets.add(entry(1))
        assertEquals(listOf(1, 2), tickets.add(entry(2)).map { it.crc })
        assertEquals(listOf(1, 2, 3), tickets.add(entry(3)).map { it.crc })
    }

    @Test
    fun `the same ticket is listed once`() {
        val tickets = ActiveTickets()
        tickets.add(entry(1))
        tickets.add(entry(2))
        assertEquals(listOf(2, 1), tickets.add(entry(1)).map { it.crc })
    }

    @Test
    fun `the oldest tickets are let go past the limit`() {
        val tickets = ActiveTickets(limit = 3)
        (1..5).forEach { tickets.add(entry(it)) }
        assertEquals(listOf(3, 4, 5), tickets.add(entry(5)).map { it.crc })
    }

    @Test
    fun `a ticket keeps its own service and game`() {
        val tickets = ActiveTickets()
        val secret = "str:service\u0000".toByteArray()
        tickets.add(entry(1, appId = 10, serverSecret = secret))
        val listed = tickets.add(entry(2, appId = 20))
        assertEquals(listOf(10, 20), listed.map { it.appId })
        assertEquals(secret.toList(), listed[0].serverSecret?.toList())
        assertEquals(null, listed[1].serverSecret)
    }
}
