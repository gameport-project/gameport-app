package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionOrderTest {
    @Test
    fun `versions are compared as numbers`() {
        assertTrue(VersionOrder.compare("0.9.10", "0.9.9")!! > 0)
        assertTrue(VersionOrder.compare("0.8.2", "0.9.0")!! < 0)
        assertEquals(0, VersionOrder.compare("1.0.0", "1.0.0"))
    }

    @Test
    fun `a test build comes before its release`() {
        assertTrue(VersionOrder.compare("0.9.2-alpha.3", "0.9.2")!! < 0)
        assertTrue(VersionOrder.compare("0.9.2-alpha.3", "0.9.2-alpha.10")!! < 0)
    }

    @Test
    fun `what is not a version is not compared`() {
        assertNull(VersionOrder.compare("admin", "0.9.0"))
        assertNull(VersionOrder.compare("0.9.0", ""))
    }

    @Test
    fun `the last word of a turn is a regression only when it broke after it was fixed`() {
        assertTrue(CompatTurn(brokeIn = "0.9.3").regression)
        assertTrue(CompatTurn(fixedIn = "0.9.0", brokeIn = "0.9.10").regression)
        assertFalse(CompatTurn(fixedIn = "0.9.3", brokeIn = "0.9.0").regression)
        assertFalse(CompatTurn(fixedIn = "0.9.0").regression)
    }
}
