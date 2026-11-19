package app.gameport.core.patch

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceTableNamesTest {
    private fun le(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    private fun pool(): ByteArray = le(28).apply { putShort(1); putShort(28); putInt(28) }.array()

    private fun packageChunk(id: Int, name: String, size: Int = 300): ByteArray {
        val buffer = le(size)
        buffer.putShort(0x0200.toShort()); buffer.putShort(288); buffer.putInt(size); buffer.putInt(id)
        buffer.put(name.toByteArray(Charsets.UTF_16LE))
        buffer.position(12 + 256)
        while (buffer.hasRemaining()) buffer.put(0x5A)
        return buffer.array()
    }

    private fun table(vararg chunks: ByteArray): ByteArray {
        val size = 12 + chunks.sumOf { it.size }
        val buffer = le(size)
        buffer.putShort(2); buffer.putShort(12); buffer.putInt(size); buffer.putInt(chunks.count { it.size == 300 })
        chunks.forEach { buffer.put(it) }
        return buffer.array()
    }

    private fun nameOf(table: ByteArray, chunk: Int) = ResourceTableNames.nameAt(table, 12 + 28 + chunk * 300 + 12)

    @Test
    fun `the package of the application takes the new name and nothing else moves`() {
        val bytes = table(pool(), packageChunk(0x7f, "com.old.game"), packageChunk(0x01, "android"))
        val before = bytes.copyOf()
        assertTrue(ResourceTableNames.rename(bytes, "com.gameport.g42"))
        assertEquals("com.gameport.g42", nameOf(bytes, 0))
        assertEquals("android", nameOf(bytes, 1))
        assertEquals(before.size, bytes.size)
        // Outside the name, every byte is as it was.
        val changed = bytes.indices.filter { bytes[it] != before[it] }
        assertTrue(changed.all { it in (12 + 28 + 12) until (12 + 28 + 12 + 256) })
    }

    @Test
    fun `a name that is already there changes nothing`() {
        val bytes = table(pool(), packageChunk(0x7f, "com.gameport.g42"))
        val before = bytes.copyOf()
        assertFalse(ResourceTableNames.rename(bytes, "com.gameport.g42"))
        assertArrayEquals(before, bytes)
    }

    @Test
    fun `a shorter name leaves no trace of the longer one`() {
        val bytes = table(pool(), packageChunk(0x7f, "com.a.very.long.package.name.indeed"))
        assertTrue(ResourceTableNames.rename(bytes, "a.b"))
        assertEquals("a.b", nameOf(bytes, 0))
    }

    @Test
    fun `something that is not a table, or a name that does not fit, is left alone`() {
        val notTable = ByteArray(64) { 7 }
        assertFalse(ResourceTableNames.rename(notTable, "a.b"))
        val bytes = table(pool(), packageChunk(0x7f, "com.old"))
        assertFalse(ResourceTableNames.rename(bytes, "x".repeat(128)))
        assertEquals("com.old", nameOf(bytes, 0))
    }

    @Test
    fun `a table with no package of the application is left alone`() {
        val bytes = table(pool(), packageChunk(0x01, "android"))
        assertFalse(ResourceTableNames.rename(bytes, "com.gameport.g42"))
    }

    @Test
    fun `a truncated table does not read past its end`() {
        val bytes = table(pool(), packageChunk(0x7f, "com.old"))
        assertFalse(ResourceTableNames.rename(bytes.copyOf(12 + 28 + 100), "com.gameport.g42"))
    }
}
