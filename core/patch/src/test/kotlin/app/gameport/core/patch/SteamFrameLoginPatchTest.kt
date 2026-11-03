package app.gameport.core.patch

import app.gameport.core.patch.patches.SteamFrameLoginPatch
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamFrameLoginPatchTest {
    private val site = SteamFrameLoginPatch.SITES.first()

    /** A library of zeros that holds [bytes] at the known place. */
    private fun libraryWith(bytes: ByteArray) = ByteArray(site.offset + 64).also { bytes.copyInto(it, site.offset) }

    @Test
    fun `the expected bytes are replaced and nothing around them`() {
        val library = libraryWith(site.expected)
        assertTrue(SteamFrameLoginPatch.patchBytes(site.packageName, library))
        assertArrayEquals(site.replacement, library.copyOfRange(site.offset, site.offset + site.replacement.size))
        assertTrue(library.copyOfRange(0, site.offset).all { it == 0.toByte() })
        assertTrue(library.copyOfRange(site.offset + site.replacement.size, library.size).all { it == 0.toByte() })
    }

    @Test
    fun `only the call changes, the two instructions around it stay`() {
        assertArrayEquals(site.expected.copyOfRange(0, 4), site.replacement.copyOfRange(0, 4))
        assertArrayEquals(site.expected.copyOfRange(8, 12), site.replacement.copyOfRange(8, 12))
        assertFalse(site.expected.copyOfRange(4, 8).contentEquals(site.replacement.copyOfRange(4, 8)))
    }

    @Test
    fun `another build of the game is left as it is`() {
        val library = libraryWith(ByteArray(site.expected.size) { 7 })
        val before = library.copyOf()
        assertFalse(SteamFrameLoginPatch.patchBytes(site.packageName, library))
        assertArrayEquals(before, library)
    }

    @Test
    fun `another game is left as it is`() {
        val library = libraryWith(site.expected)
        val before = library.copyOf()
        assertFalse(SteamFrameLoginPatch.patchBytes("com.example.other", library))
        assertArrayEquals(before, library)
    }

    @Test
    fun `a library too short for the place is left as it is`() {
        assertFalse(SteamFrameLoginPatch.patchBytes(site.packageName, ByteArray(16)))
    }

    @Test
    fun `patching twice does nothing the second time`() {
        val library = libraryWith(site.expected)
        assertTrue(SteamFrameLoginPatch.patchBytes(site.packageName, library))
        assertFalse(SteamFrameLoginPatch.patchBytes(site.packageName, library))
    }

    @Test
    fun `it is on by default and changes nothing in a game it does not know`() {
        assertTrue(SteamFrameLoginPatch.recommended)
        val library = ByteArray(0x30_000_00) { 7 }
        assertFalse(SteamFrameLoginPatch.patchBytes("com.example.other", library))
    }
}
