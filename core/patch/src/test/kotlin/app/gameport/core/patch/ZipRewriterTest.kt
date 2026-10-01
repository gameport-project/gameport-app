package app.gameport.core.patch

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZipRewriterTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `stored entries are aligned and content survives`() {
        val source = temp.newFile("in.zip")
        java.util.zip.ZipOutputStream(source.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("a.txt")); zip.write("hello".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("lib/arm64-v8a/libgame.so").apply {
                method = ZipEntry.STORED; size = 300; compressedSize = 300
                crc = java.util.zip.CRC32().apply { update(ByteArray(300) { 7 }) }.value
            })
            zip.write(ByteArray(300) { 7 }); zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/CERT.RSA")); zip.write(1); zip.closeEntry()
        }
        val output = temp.newFile("out.zip")

        ZipRewriter.rewrite(
            input = source,
            output = output,
            replaced = mapOf("a.txt" to "changed".toByteArray()),
            stored = mapOf("lib/arm64-v8a/libsteamclient.so" to ByteArray(500) { 9 }, "assets/gameport/steam.cfg" to "x=1".toByteArray()),
            dropIf = { it.startsWith("META-INF/") },
        )

        ZipFile(output).use { zip ->
            assertEquals("changed", zip.getInputStream(zip.getEntry("a.txt")).readBytes().decodeToString())
            assertEquals(300, zip.getInputStream(zip.getEntry("lib/arm64-v8a/libgame.so")).readBytes().size)
            assertEquals(500, zip.getInputStream(zip.getEntry("lib/arm64-v8a/libsteamclient.so")).readBytes().size)
            assertEquals(null, zip.getEntry("META-INF/CERT.RSA"))
        }
        assertEquals(0L, dataOffset(output, "lib/arm64-v8a/libgame.so") % 16384)
        assertEquals(0L, dataOffset(output, "lib/arm64-v8a/libsteamclient.so") % 16384)
        assertEquals(0L, dataOffset(output, "assets/gameport/steam.cfg") % 4)
        assertTrue(true)
    }

    @Test
    fun `a real apk keeps its stored entries aligned`() {
        val source = System.getenv("GAMEPORT_TEST_APK")?.let(::File)
        org.junit.Assume.assumeTrue("set GAMEPORT_TEST_APK to run", source?.isFile == true)
        val output = System.getenv("GAMEPORT_TEST_REWRITE_OUT")?.let(::File) ?: temp.newFile("real.zip")

        ZipRewriter.rewrite(source!!, output, emptyMap(), mapOf("lib/arm64-v8a/libsteamclient.so" to ByteArray(4096) { 1 }), { false })

        ZipFile(output).use { zip ->
            zip.entries().asSequence().filter { it.method == ZipEntry.STORED && it.name.endsWith(".so") }.forEach {
                assertEquals(it.name, 0L, dataOffset(output, it.name) % 16384)
            }
        }
    }

    /** Offset of an entry's data, read from its local header rather than trusted from the writer. */
    private fun dataOffset(file: File, name: String): Long {
        val headerOffset = ZipFile(file).use { zip ->
            // The central directory is the only place to find the header offset; scan for the name.
            centralOffset(file, name)
        }
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(30)
            raf.seek(headerOffset); raf.readFully(header)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            return headerOffset + 30 + buffer.getShort(26) + buffer.getShort(28)
        }
    }

    private fun centralOffset(file: File, name: String): Long {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var i = bytes.size - 22
        while (buffer.getInt(i) != 0x06054b50) i--
        var pos = buffer.getInt(i + 16)
        while (buffer.getInt(pos) == 0x02014b50) {
            val nameLen = buffer.getShort(pos + 28).toInt()
            val extraLen = buffer.getShort(pos + 30).toInt()
            val commentLen = buffer.getShort(pos + 32).toInt()
            if (String(bytes, pos + 46, nameLen) == name) return buffer.getInt(pos + 42).toLong() and 0xFFFFFFFFL
            pos += 46 + nameLen + extraLen + commentLen
        }
        error("no entry $name")
    }
}
