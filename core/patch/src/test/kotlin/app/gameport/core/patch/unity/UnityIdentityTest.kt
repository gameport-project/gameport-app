package app.gameport.core.patch.unity

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnityIdentityTest {
    private val came = UnityNames("Pine Studio", "Escape Simulator")
    private val original = "com.PineStudio.EscapeSimulator"
    private val target = "com.gameport.g1435790"

    @Test
    fun `the package Unity builds from two names has no spaces`() {
        assertEquals(original, UnityIdentity.derive(came))
        assertEquals("com.a.b", UnityIdentity.derive(UnityNames(" a ", "  b")))
    }

    @Test
    fun `a package of GamePort splits into its two names`() {
        assertEquals(UnityNames("gameport", "g1435790"), UnityIdentity.split(target))
    }

    @Test
    fun `a package that is not of the form com dot a dot b does not split`() {
        assertNull(UnityIdentity.split("org.gameport.g1"))
        assertNull(UnityIdentity.split("com.gameport"))
        assertNull(UnityIdentity.split("com.game.port.g1"))
        assertNull(UnityIdentity.split("com..g1"))
        assertNull(UnityIdentity.split("com.game port.g1"))
    }

    @Test
    fun `a name is padded with spaces to the size of the one it replaces`() {
        assertEquals("gameport   ", UnityIdentity.fit("Pine Studio", "gameport"))
        assertEquals("g1435790        ", UnityIdentity.fit("Escape Simulator", "g1435790"))
    }

    @Test
    fun `the size is counted in bytes, not in letters`() {
        // "é" takes two bytes: six bytes of room hold a four letter name and two spaces.
        assertEquals("abcd  ", UnityIdentity.fit("aébcd", "abcd"))
        assertNull(UnityIdentity.fit("éé", "abcde"))
    }

    @Test
    fun `a name longer than the room is not fitted`() {
        assertNull(UnityIdentity.fit("Ab", "gameport"))
    }

    @Test
    fun `a pair of strings that gives the package is found where Unity writes them`() {
        val data = ByteArray(36) { 0x55 } + TestArchive.serialized("Pine Studio", "Escape Simulator") + ByteArray(40) { 0x66 }
        val found = UnityIdentity.locate(data) { UnityIdentity.derive(it) == original }
        assertEquals(1, found.size)
        assertEquals(36, found[0].at)
        assertEquals(came, found[0].names)
        assertEquals(11, found[0].companyBytes)
        assertEquals(16, found[0].productBytes)
    }

    @Test
    fun `a pair that does not give the package is not found`() {
        val data = TestArchive.serialized("Pine Studio", "Another Game")
        assertTrue(UnityIdentity.locate(data) { UnityIdentity.derive(it) == original }.isEmpty())
    }

    @Test
    fun `strings that do not follow each other are not a pair`() {
        val data = TestArchive.serialized("Pine Studio", "x", "Escape Simulator")
        assertTrue(UnityIdentity.locate(data) { UnityIdentity.derive(it) == original }.isEmpty())
    }

    @Test
    fun `text that is not on a multiple of 4 is not taken for a string`() {
        val data = byteArrayOf(0x55) + TestArchive.serialized("Pine Studio", "Escape Simulator")
        assertTrue(UnityIdentity.locate(data) { UnityIdentity.derive(it) == original }.isEmpty())
    }

    @Test
    fun `control characters make a string no name`() {
        val data = TestArchive.serialized("Pine\u0001Studio", "Escape Simulator")
        assertTrue(UnityIdentity.locate(data) { true }.isEmpty())
    }

    @Test
    fun `an empty array, a short one and an implausible length are handled`() {
        assertTrue(UnityIdentity.locate(ByteArray(0)) { true }.isEmpty())
        assertTrue(UnityIdentity.locate(ByteArray(7)) { true }.isEmpty())
        val huge = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F) + ByteArray(20)
        assertTrue(UnityIdentity.locate(huge) { true }.isEmpty())
    }

    @Test
    fun `two pairs are both found`() {
        val one = TestArchive.serialized("Pine Studio", "Escape Simulator")
        val data = one + ByteArray(8) + one
        assertEquals(2, UnityIdentity.locate(data) { UnityIdentity.derive(it) == original }.size)
    }

    @Test
    fun `the names wanted for a renamed game give its new package`() {
        val wanted = UnityIdentity.wanted(came, recorded = null, original = original, target = target)
        assertNotNull(wanted)
        assertEquals(target, UnityIdentity.derive(wanted!!))
        assertEquals(came.company.length, wanted.company.length)
        assertEquals(came.product.length, wanted.product.length)
    }

    @Test
    fun `a game already carrying the names wanted is left alone`() {
        val renamed = UnityIdentity.wanted(came, null, original, target)!!
        assertNull(UnityIdentity.wanted(renamed, came, original, target))
    }

    @Test
    fun `a game put back under its own package gets its own names back`() {
        val renamed = UnityIdentity.wanted(came, null, original, target)!!
        assertEquals(came, UnityIdentity.wanted(renamed, came, original, original))
    }

    @Test
    fun `without the names written down, names that are not the original ones are not touched`() {
        val renamed = UnityIdentity.wanted(came, null, original, target)!!
        assertNull(UnityIdentity.wanted(renamed, null, original, original))
    }

    @Test
    fun `a game whose names do not give its package is left alone`() {
        assertNull(UnityIdentity.wanted(UnityNames("Pine Studio", "Other"), null, original, target))
    }

    @Test
    fun `a package that does not fit in the old names is left alone`() {
        assertNull(UnityIdentity.wanted(UnityNames("A", "B"), null, "com.A.B", target))
    }

    @Test
    fun `a game with the names of the original package is untouched when it keeps its package`() {
        assertNull(UnityIdentity.wanted(came, null, original, original))
    }

    @Test
    fun `the names are written over the old ones and nothing else changes`() {
        val before = ByteArray(36) { 0x55 } + TestArchive.serialized("Pine Studio", "Escape Simulator") + ByteArray(40) { 0x66 }
        val data = before.copyOf()
        val found = UnityIdentity.locate(data) { UnityIdentity.derive(it) == original }.single()
        UnityIdentity.write(data, found, UnityIdentity.wanted(came, null, original, target)!!)
        val expected = ByteArray(36) { 0x55 } + TestArchive.serialized("gameport   ", "g1435790        ") + ByteArray(40) { 0x66 }
        assertArrayEquals(expected, data)
        assertEquals(before.size, data.size)
    }

    @Test
    fun `writing names of another size is refused`() {
        val data = TestArchive.serialized("Pine Studio", "Escape Simulator")
        val found = UnityIdentity.locate(data) { true }.single()
        try {
            UnityIdentity.write(data, found, UnityNames("gameport", "g1435790"))
            org.junit.Assert.fail("expected the write to be refused")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
