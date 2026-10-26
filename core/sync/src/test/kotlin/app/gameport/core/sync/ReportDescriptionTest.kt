package app.gameport.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportDescriptionTest {
    @Test
    fun `nothing written, or only spaces, is no description`() {
        assertNull(ReportDescription.of(null))
        assertNull(ReportDescription.of(""))
        assertNull(ReportDescription.of("  \n \t "))
    }

    @Test
    fun `what was written is kept without the spaces around it`() {
        assertEquals("Black screen after the logo", ReportDescription.of("  Black screen after the logo \n"))
    }

    @Test
    fun `a very long text is cut`() {
        assertEquals(ReportDescription.MAX, ReportDescription.of("x".repeat(10_000))!!.length)
    }
}
