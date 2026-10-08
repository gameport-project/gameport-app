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
    fun `the display name is hidden wherever it appears as a word`() {
        val cleaned = ReportRedactor.clean("Hello Charly, welcome back CHARLY, path /home/charly/data, charly-pc", listOf("Charly"))
        assertFalse(cleaned.contains("harly", ignoreCase = true))
    }

    @Test
    fun `a name inside another word is left alone, as rue in true`() {
        val text = "headset=true seated=false has Internet: true, Maximum 5"
        assertEquals(text, ReportRedactor.clean(text, listOf("rue", "Max")))
        assertEquals("hello [name], true", ReportRedactor.clean("hello rue, true", listOf("rue")))
    }

    @Test
    fun `a name that is a word of the system is not hidden`() {
        assertEquals("a true b", ReportRedactor.clean("a true b", listOf("True")))
    }

    @Test
    fun `short names and version numbers are left alone`() {
        assertEquals("version 1.0.944126 of ab", ReportRedactor.clean("version 1.0.944126 of ab", listOf("ab")))
    }

    @Test
    fun `the libraries and files of the system are not taken for addresses`() {
        val text = "/apex/com.android.vndk.v34/lib64/android.hardware.graphics.common@1.2.so\n/data/resource-cache/product@com.oculus.vrshell.overlay@idmap"
        assertEquals(text, ReportRedactor.clean(text))
        assertEquals("mail [email] and /x/lib@1.0.so", ReportRedactor.clean("mail a@b.example.com and /x/lib@1.0.so"))
    }
}
