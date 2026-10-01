package app.gameport.core.patch

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.CRC32
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile

/**
 * Rebuilds a zip with some entries replaced or added, copying every other entry byte for byte
 * (no decompression), so a multi-gigabyte game costs little time and memory. Uncompressed entries
 * are re-aligned, because Android maps native libraries and resources straight out of the APK.
 */
internal object ZipRewriter {
    private const val ALIGN_DEFAULT = 4
    private const val ALIGN_NATIVE_LIB = 16 * 1024
    private const val ALIGN_EXTRA_ID = 0xD935

    fun rewrite(
        input: File,
        output: File,
        replaced: Map<String, ByteArray>,
        stored: Map<String, ByteArray>,
        dropIf: (String) -> Boolean,
    ) {
        ZipFile.builder().setFile(input).get().use { source ->
            ZipArchiveOutputStream(output).use { out ->
                out.setUseZip64(org.apache.commons.compress.archivers.zip.Zip64Mode.AsNeeded)
                val entries = source.entries
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (dropIf(name) || name in replaced || name in stored || entry.isDirectory) continue
                    // Start from no extra fields: setExtra merges with the ones the source entry
                    // carries (its old alignment padding), which would throw the offsets off.
                    val copy = ZipArchiveEntry(entry).apply { setExtraFields(emptyArray()) }
                    if (copy.method == ZipEntry.STORED) copy.setExtra(alignmentExtra(out.bytesWritten, name, uncompressed = true))
                    out.addRawArchiveEntry(copy, source.getRawInputStream(entry))
                }
                replaced.forEach { (name, bytes) -> out.addDeflated(name, bytes) }
                stored.forEach { (name, bytes) -> out.addStored(name, bytes) }
            }
        }
    }

    private fun ZipArchiveOutputStream.addDeflated(name: String, bytes: ByteArray) {
        putArchiveEntry(ZipArchiveEntry(name).apply { method = ZipEntry.DEFLATED })
        write(bytes)
        closeArchiveEntry()
    }

    private fun ZipArchiveOutputStream.addStored(name: String, bytes: ByteArray) {
        val entry = ZipArchiveEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = CRC32().apply { update(bytes) }.value
            setExtra(alignmentExtra(bytesWritten, name, uncompressed = true))
        }
        putArchiveEntry(entry)
        write(bytes)
        closeArchiveEntry()
    }

    /** Padding (as a zipalign-style extra field) that puts the entry's data on an aligned offset. */
    private fun alignmentExtra(offset: Long, name: String, uncompressed: Boolean): ByteArray {
        if (!uncompressed) return ByteArray(0)
        val alignment = if (name.endsWith(".so")) ALIGN_NATIVE_LIB else ALIGN_DEFAULT
        val nameBytes = name.toByteArray(Charsets.UTF_8).size
        val headerEnd = offset + LOCAL_HEADER_SIZE + nameBytes
        var padding = ((alignment - headerEnd % alignment) % alignment).toInt()
        if (padding == 0) return ByteArray(0)
        if (padding < EXTRA_HEADER_SIZE) padding += alignment
        return ByteArray(padding).also {
            it[0] = (ALIGN_EXTRA_ID and 0xFF).toByte()
            it[1] = (ALIGN_EXTRA_ID shr 8).toByte()
            val dataSize = padding - EXTRA_HEADER_SIZE
            it[2] = (dataSize and 0xFF).toByte()
            it[3] = (dataSize shr 8).toByte()
        }
    }

    private const val LOCAL_HEADER_SIZE = 30
    private const val EXTRA_HEADER_SIZE = 4
}
