package app.gameport.core.patch.unity

import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.CRC32
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnityFsTest {
    /** A player settings file as Unity writes it: some bytes, the two names at offset 36 (a multiple of 4), more bytes. */
    private val settings = ByteArray(36) { 0x41 } + TestArchive.serialized("Pine Studio", "Escape Simulator") + ByteArray(80) { 0x42 }

    private fun files() = listOf(
        "globalgamemanagers" to settings,
        "other.assets" to ByteArray(300) { (it % 251).toByte() },
        "zeros" to ByteArray(200),
    )

    private fun tmp(): File = File.createTempFile("unityfs", ".bin").also { it.deleteOnExit() }

    private fun find(data: ByteArray, needle: String): Int {
        val n = needle.toByteArray()
        outer@ for (i in 0..data.size - n.size) {
            for (k in n.indices) if (data[i + k] != n[k]) continue@outer
            return i
        }
        return -1
    }

    private fun replaceText(from: String, to: String): (ByteArray) -> Boolean = { data ->
        val i = find(data, from)
        if (i < 0) {
            false
        } else {
            to.toByteArray().copyInto(data, i)
            true
        }
    }

    private fun rewrite(archive: ByteArray, node: String = "globalgamemanagers", maxNode: Int = 1 shl 20, edit: (ByteArray) -> Boolean): Pair<UnityFsOutcome, File> {
        val out = tmp()
        return UnityFs.rewriteNode({ ByteArrayInputStream(archive) }, node, maxNode, out, edit) to out
    }

    private fun rawBlocks(archive: ByteArray): List<ByteArray> {
        val table = UnityFs.readTable(ByteArrayInputStream(archive))
        var at = table.dataStart.toInt()
        return table.blocks.map { block -> archive.copyOfRange(at, at + block.compressed).also { at += block.compressed } }
    }

    private fun assertUnsupported(outcome: UnityFsOutcome) = assertTrue("expected Unsupported, got $outcome", outcome is UnityFsOutcome.Unsupported)

    private val change = replaceText("Pine Studio", "gameport   ")

    @Test
    fun `the table of an archive is read`() {
        val archive = TestArchive.build(files())
        val table = UnityFs.readTable(ByteArrayInputStream(archive))
        assertEquals(8, table.version)
        assertEquals(listOf("globalgamemanagers", "other.assets", "zeros"), table.nodes.map { it.path })
        assertEquals(files().sumOf { it.second.size }, table.blocks.sumOf { it.uncompressed })
        assertEquals(0L, table.dataStart % 16)
        assertEquals(files().sumOf { it.second.size }, TestArchive.unpack(archive).size)
    }

    @Test
    fun `the names are changed in the file and everything else of the stream stays`() {
        val archive = TestArchive.build(files())
        val (outcome, file) = rewrite(archive, edit = change)
        assertTrue(outcome is UnityFsOutcome.Rewritten)
        val expected = TestArchive.unpack(archive)
        "gameport   ".toByteArray().copyInto(expected, find(expected, "Pine Studio"))
        assertArrayEquals(expected, TestArchive.unpack(file.readBytes()))
    }

    @Test
    fun `the size and the crc given are those of the file written`() {
        val archive = TestArchive.build(files())
        val (outcome, file) = rewrite(archive, edit = change)
        outcome as UnityFsOutcome.Rewritten
        assertEquals(file.length(), outcome.size)
        assertEquals(CRC32().apply { update(file.readBytes()) }.value, outcome.crc)
    }

    @Test
    fun `only the blocks that hold the change are rewritten, and every other block is copied as it was`() {
        val archive = TestArchive.build(files(), blockSize = 64)
        val (_, file) = rewrite(archive, edit = replaceText("Escape Simulator", "g1435790        "))
        val before = UnityFs.readTable(ByteArrayInputStream(archive))
        val bytes = file.readBytes()
        val after = UnityFs.readTable(ByteArrayInputStream(bytes))
        val rawBefore = rawBlocks(archive)
        val rawAfter = rawBlocks(bytes)
        val stored = after.blocks.indices.filter { after.blocks[it].flags and 0x3F == 0 && before.blocks[it].flags and 0x3F != 0 }
        // "Escape Simulator" starts at 36 + 4 + 12 + 4 = 56 and ends at 72: it lies in blocks 0 and 1 of 64 bytes.
        assertEquals(listOf(0, 1), stored)
        for (i in before.blocks.indices) {
            assertEquals(before.blocks[i].uncompressed, after.blocks[i].uncompressed)
            if (i !in stored) {
                assertEquals(before.blocks[i].compressed, after.blocks[i].compressed)
                assertEquals(before.blocks[i].flags, after.blocks[i].flags)
                assertArrayEquals("block $i is copied as it was", rawBefore[i], rawAfter[i])
            }
        }
    }

    @Test
    fun `a change inside one block rewrites that block only`() {
        val archive = TestArchive.build(files(), blockSize = 64)
        val (_, file) = rewrite(archive, edit = change)
        val before = UnityFs.readTable(ByteArrayInputStream(archive))
        val after = UnityFs.readTable(ByteArrayInputStream(file.readBytes()))
        // "Pine Studio" lies at 40..50, inside block 0.
        assertEquals(listOf(0), after.blocks.indices.filter { after.blocks[it].flags and 0x3F == 0 && before.blocks[it].flags and 0x3F != 0 })
    }

    @Test
    fun `a change across two blocks rewrites both`() {
        val archive = TestArchive.build(files(), blockSize = 16)
        val (outcome, file) = rewrite(archive, edit = change)
        assertTrue(outcome is UnityFsOutcome.Rewritten)
        val before = UnityFs.readTable(ByteArrayInputStream(archive))
        val after = UnityFs.readTable(ByteArrayInputStream(file.readBytes()))
        // "Pine Studio" lies at 40..50: blocks 2 (32..47) and 3 (48..63).
        assertEquals(listOf(2, 3), after.blocks.indices.filter { after.blocks[it].flags and 0x3F == 0 && before.blocks[it].flags and 0x3F != 0 })
        val expected = TestArchive.unpack(archive)
        "gameport   ".toByteArray().copyInto(expected, 40)
        assertArrayEquals(expected, TestArchive.unpack(file.readBytes()))
    }

    @Test
    fun `the table written is stored without compression and the data stays aligned`() {
        val archive = TestArchive.build(files())
        val (_, file) = rewrite(archive, edit = change)
        val table = UnityFs.readTable(ByteArrayInputStream(file.readBytes()))
        assertEquals(0, table.flags and 0x3F)
        assertEquals(0x200, table.flags and 0x200)
        assertEquals(0L, table.dataStart % 16)
    }

    @Test
    fun `an archive written can be written again`() {
        val archive = TestArchive.build(files())
        val (_, first) = rewrite(archive, edit = change)
        val (outcome, second) = rewrite(first.readBytes(), edit = replaceText("gameport   ", "Pine Studio"))
        assertTrue(outcome is UnityFsOutcome.Rewritten)
        assertArrayEquals(TestArchive.unpack(archive), TestArchive.unpack(second.readBytes()))
    }

    @Test
    fun `an archive of stored blocks and a stored table is handled`() {
        val archive = TestArchive.build(files(), blockType = 0, tableType = 0)
        val (outcome, file) = rewrite(archive, edit = change)
        assertTrue(outcome is UnityFsOutcome.Rewritten)
        val expected = TestArchive.unpack(archive)
        "gameport   ".toByteArray().copyInto(expected, find(expected, "Pine Studio"))
        assertArrayEquals(expected, TestArchive.unpack(file.readBytes()))
    }

    @Test
    fun `an archive with no padding after its table is handled`() {
        val archive = TestArchive.build(files(), paddingFlag = false)
        val (outcome, file) = rewrite(archive, edit = change)
        assertTrue(outcome is UnityFsOutcome.Rewritten)
        val table = UnityFs.readTable(ByteArrayInputStream(file.readBytes()))
        assertEquals(0, table.flags and 0x200)
        val expected = TestArchive.unpack(archive)
        "gameport   ".toByteArray().copyInto(expected, find(expected, "Pine Studio"))
        assertArrayEquals(expected, TestArchive.unpack(file.readBytes()))
    }

    @Test
    fun `version 7 is handled too`() {
        val archive = TestArchive.build(files(), version = 7)
        val (outcome, file) = rewrite(archive, edit = change)
        assertTrue(outcome is UnityFsOutcome.Rewritten)
        assertEquals(7, UnityFs.readTable(ByteArrayInputStream(file.readBytes())).version)
    }

    @Test
    fun `blocks of different compressions are handled and a block that is not decoded is copied`() {
        // The last block is marked LZMA (1): nothing decodes it, since the file being edited lies in the first ones.
        val count = (files().sumOf { it.second.size } + 63) / 64
        val archive = TestArchive.build(files(), blockSize = 64, perBlockType = { if (it == count - 1) 1 else if (it % 2 == 0) 0 else 2 })
        val (outcome, file) = rewrite(archive, edit = change)
        assertTrue(outcome is UnityFsOutcome.Rewritten)
        assertArrayEquals(rawBlocks(archive).last(), rawBlocks(file.readBytes()).last())
    }

    @Test
    fun `a block of the file being edited that uses LZMA is not handled`() {
        val archive = TestArchive.build(files(), blockSize = 64, perBlockType = { if (it == 0) 1 else 2 })
        assertUnsupported(rewrite(archive, edit = change).first)
    }

    @Test
    fun `nothing is written when the file is not in the archive`() {
        val archive = TestArchive.build(files())
        val (outcome, file) = rewrite(archive, node = "missing", edit = { true })
        assertTrue(outcome is UnityFsOutcome.Unchanged)
        assertEquals(0L, file.length())
    }

    @Test
    fun `nothing is written when the edit changes nothing`() {
        val archive = TestArchive.build(files())
        val (outcome, file) = rewrite(archive, edit = { false })
        assertTrue(outcome is UnityFsOutcome.Unchanged)
        assertEquals(0L, file.length())
    }

    @Test
    fun `nothing is written when the edit says it changed and did not`() {
        val archive = TestArchive.build(files())
        val (outcome, file) = rewrite(archive, edit = { true })
        assertTrue(outcome is UnityFsOutcome.Unchanged)
        assertEquals(0L, file.length())
    }

    @Test
    fun `a file larger than the limit is not handled`() {
        val archive = TestArchive.build(files())
        assertUnsupported(rewrite(archive, maxNode = 10, edit = change).first)
    }

    @Test
    fun `an archive of another version is not handled`() {
        assertUnsupported(rewrite(TestArchive.build(files(), version = 6), edit = change).first)
        assertUnsupported(rewrite(TestArchive.build(files(), version = 9), edit = change).first)
    }

    @Test
    fun `a table at the end of the archive is not handled`() {
        assertUnsupported(rewrite(TestArchive.build(files(), extraFlags = 0x40 or 0x80), edit = change).first)
    }

    @Test
    fun `an archive whose size does not match its blocks is not handled`() {
        val archive = TestArchive.build(files())
        // The size of the archive is the 8 bytes after the signature, the version and the two version strings.
        val at = 8 + 4 + "5.x.x".length + 1 + "6000.3.6f1".length + 1
        archive[at + 7] = (archive[at + 7] + 1).toByte()
        assertUnsupported(rewrite(archive, edit = change).first)
    }

    @Test
    fun `a truncated archive is not handled and does not throw`() {
        val archive = TestArchive.build(files())
        assertUnsupported(rewrite(archive.copyOf(archive.size / 2), edit = change).first)
        assertUnsupported(rewrite(archive.copyOf(archive.size - 5), edit = change).first)
        assertUnsupported(rewrite(archive.copyOf(20), edit = change).first)
        assertUnsupported(rewrite(ByteArray(0), edit = change).first)
    }

    @Test
    fun `a file that is not an archive is not handled`() {
        assertUnsupported(rewrite("UnityWeb\u0000 and the rest of something else".toByteArray(), edit = change).first)
        assertUnsupported(rewrite(ByteArray(300) { it.toByte() }, edit = change).first)
    }

    @Test
    fun `a block that does not decode to its size is not handled`() {
        val archive = TestArchive.build(files(), blockSize = 64)
        val table = UnityFs.readTable(ByteArrayInputStream(archive))
        // The table is stored as literals only: the entry of block 0 (64 unpacked, then its packed size) can be found in it by its bytes. Say 65 unpacked.
        val needle = TestArchive.be32(64) + TestArchive.be32(table.blocks[0].compressed)
        val at = (64 until 64 + 400).first { i -> needle.indices.all { archive[i + it] == needle[it] } }
        archive[at + 3] = 65
        assertUnsupported(rewrite(archive, edit = change).first)
    }

    @Test
    fun `the archive is opened again for each pass`() {
        var opened = 0
        val archive = TestArchive.build(files())
        val out = tmp()
        UnityFs.rewriteNode({ opened++; ByteArrayInputStream(archive) }, "globalgamemanagers", 1 shl 20, out, change)
        assertEquals("the table, the blocks to edit, the copy", 3, opened)
        assertFalse(out.length() == 0L)
    }
}
