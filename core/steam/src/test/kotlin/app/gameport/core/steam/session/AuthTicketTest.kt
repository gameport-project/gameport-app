package app.gameport.core.steam.session

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthTicketTest {
    @Test
    fun `the ticket is the token, a 24 byte header and the ownership ticket, each with its length`() {
        val token = ByteArray(20) { (it + 1).toByte() }
        val ownership = ByteArray(100) { (200 + it).toByte() }

        val ticket = AuthTicket.assemble(token, ownership, headerNonce = 0x0102030405060708L, millisSinceLogOn = 5000, connectionCount = 3)

        val buffer = ByteBuffer.wrap(ticket).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(4 + 20 + 4 + 24 + 4 + 100, ticket.size)
        assertEquals(20, buffer.int)
        val readToken = ByteArray(20).also { buffer.get(it) }
        assertArrayEquals(token, readToken)
        assertEquals(24, buffer.int)
        assertEquals(1, buffer.int)
        assertEquals(2, buffer.int)
        assertEquals(0x05060708, buffer.int)
        assertEquals(0x01020304, buffer.int)
        assertEquals(5000, buffer.int)
        assertEquals(3, buffer.int)
        assertEquals(100, buffer.int)
        val readOwnership = ByteArray(100).also { buffer.get(it) }
        assertArrayEquals(ownership, readOwnership)
    }

    @Test
    fun `a ticket for a web service carries its type in the header`() {
        val ticket = AuthTicket.assemble(ByteArray(20), ByteArray(10), headerNonce = 1, millisSinceLogOn = 0, connectionCount = 0, type = 5)
        val buffer = ByteBuffer.wrap(ticket).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(4 + 20 + 4 + 4)
        assertEquals(5, buffer.int)
    }

    @Test
    fun `Steam is told the service with its own prefix and a terminating zero`() {
        assertArrayEquals("str:epiconlineservices".toByteArray() + byteArrayOf(0), AuthTicket.serverSecret("epiconlineservices"))
    }
}
