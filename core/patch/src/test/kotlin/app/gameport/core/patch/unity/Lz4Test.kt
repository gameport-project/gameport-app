package app.gameport.core.patch.unity

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class Lz4Test {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun text(s: String) = s.toByteArray(Charsets.ISO_8859_1)

    private fun decode(src: ByteArray, outSize: Int) = Lz4.decompress(src, 0, src.size, outSize)

    private fun assertRejected(src: ByteArray, outSize: Int) {
        try {
            decode(src, outSize)
            fail("expected a UnityFormatException")
        } catch (e: UnityFormatException) {
            // expected
        }
    }

    @Test
    fun `a block of literals only is copied`() {
        val src = bytes(0x50) + text("hello")
        assertArrayEquals(text("hello"), decode(src, 5))
    }

    @Test
    fun `a match repeats what was written before`() {
        // "abc" as literals, then a match of 6 at distance 3.
        val src = bytes(0x32) + text("abc") + bytes(0x03, 0x00)
        assertArrayEquals(text("abcabcabc"), decode(src, 9))
    }

    @Test
    fun `a match longer than its distance repeats a pattern`() {
        // "a", then a match of 7 at distance 1: eight times "a".
        val src = bytes(0x13) + text("a") + bytes(0x01, 0x00)
        assertArrayEquals(text("aaaaaaaa"), decode(src, 8))
    }

    @Test
    fun `an extended literal length is added up`() {
        // 15 + 5 = 20 literals.
        val payload = ByteArray(20) { ('a'.code + it).toByte() }
        val src = bytes(0xF0, 0x05) + payload
        assertArrayEquals(payload, decode(src, 20))
    }

    @Test
    fun `an extended match length is added up`() {
        // "ab", then a match of 4 + 15 + 255 + 3 = 277 at distance 2: 279 bytes of "abab...".
        val src = bytes(0x2F) + text("ab") + bytes(0x02, 0x00, 0xFF, 0x03)
        val out = decode(src, 279)
        assertEquals(279, out.size)
        for (i in out.indices) assertEquals(if (i % 2 == 0) 'a'.code.toByte() else 'b'.code.toByte(), out[i])
    }

    @Test
    fun `a sequence that ends with a match and no literals is accepted`() {
        // "ab", then the shortest match (4) at distance 2.
        val src = bytes(0x20) + text("ab") + bytes(0x02, 0x00)
        assertArrayEquals(text("ababab"), decode(src, 6))
    }

    @Test
    fun `a distance of zero is rejected`() {
        assertRejected(bytes(0x10) + text("a") + bytes(0x00, 0x00), 5)
    }

    @Test
    fun `a distance before the start of the output is rejected`() {
        assertRejected(bytes(0x10) + text("a") + bytes(0x09, 0x00), 8)
    }

    @Test
    fun `literals past the end of the input are rejected`() {
        assertRejected(bytes(0x50) + text("hel"), 5)
    }

    @Test
    fun `an output larger than announced is rejected`() {
        assertRejected(bytes(0x50) + text("hello"), 3)
    }

    @Test
    fun `an output smaller than announced is rejected`() {
        assertRejected(bytes(0x50) + text("hello"), 9)
    }

    @Test
    fun `a match past the announced output is rejected`() {
        assertRejected(bytes(0x1F) + text("a") + bytes(0x01, 0x00, 0xFF, 0xFF, 0x00), 20)
    }

    @Test
    fun `an endless run of length bytes is rejected, not looped on`() {
        assertRejected(bytes(0xF0) + ByteArray(5000) { 0xFF.toByte() }, 10)
    }

    @Test
    fun `a cut offset is rejected`() {
        assertRejected(bytes(0x11) + text("a") + bytes(0x01), 6)
    }

    @Test
    fun `bounds that do not fit the input are rejected`() {
        try {
            Lz4.decompress(ByteArray(4), 2, 5, 3)
            fail("expected a UnityFormatException")
        } catch (e: UnityFormatException) {
            // expected
        }
    }

    @Test
    fun `an empty block decodes to nothing`() {
        assertArrayEquals(ByteArray(0), decode(ByteArray(0), 0))
    }
}
