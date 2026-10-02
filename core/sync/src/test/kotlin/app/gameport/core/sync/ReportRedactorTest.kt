package app.gameport.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReportRedactorTest {
    @Test
    fun `account numbers, addresses and e-mails are removed`() {
        val text = "id 76561198000000001 mail a.b@example.com ip 192.168.1.36 mac c0:dd:8a:b1:3e:1b ok"
        val cleaned = ReportRedactor.clean(text)
        assertEquals("id [steamid] mail [email] ip [ip] mac [mac] ok", cleaned)
    }

    @Test
    fun `the display name is hidden wherever it appears`() {
        val cleaned = ReportRedactor.clean("Hello Charly, welcome back CHARLY", listOf("Charly"))
        assertFalse(cleaned.contains("harly", ignoreCase = true))
    }

    @Test
    fun `short names and version numbers are left alone`() {
        assertEquals("version 1.0.944126 of ab", ReportRedactor.clean("version 1.0.944126 of ab", listOf("ab")))
    }
}
