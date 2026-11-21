package app.gameport.core.patch.unity

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** What [UnityFs.rewriteNode] did. */
internal sealed interface UnityFsOutcome {
    /** Nothing to change (the node is not in the archive, or the edit changed nothing): no file was made. */
    data object Unchanged : UnityFsOutcome

    /** The archive was rewritten into the file given; [size] and [crc] (CRC-32) are what the zip entry that will hold it needs. */
    class Rewritten(val size: Long, val crc: Long) : UnityFsOutcome

    /** The archive is something this code does not handle (or not what it says it is): it is left as it is, and [reason] says why. */
    class Unsupported(val reason: String) : UnityFsOutcome
}

/**
 * Changes bytes inside a Unity archive (`UnityFS`, the `data.unity3d` of a recent Unity game) **without unpacking all of it**. The archive is a list of blocks, each compressed on its own,
 * and a table that says which blocks hold which files. A game's data is a few hundred megabytes, so nothing here holds more than the blocks of the one file being edited:
 * the table is read, the blocks that hold the file are decoded, the edit is made, and the archive is written again with every other block copied as it was.
 *
 * Only the blocks the edit changed are rewritten, stored without compression (the size of a decoded block is the one the table already gives, so nothing after it moves in the
 * uncompressed stream, and every offset the files point at stays right). The table is written without compression too, which the format allows, so no compressor is needed
 * at all. Any doubt ends in [UnityFsOutcome.Unsupported]: an archive of another version, a table at the end, a block compressed with LZMA, sizes that do not add up.
 *
 * The layout this follows (all numbers big endian), as measured on the data of a Unity 6 game: `"UnityFS\0"`, version (7 or 8), two version strings, the size of the whole archive
 * (8 bytes), the size of the table packed and unpacked, flags; padding to 16; the table; padding to 16 when the flags say so (`0x200`); then the blocks, one after the other.
 * The table holds 16 bytes of hash, the blocks (unpacked size, packed size, flags: 6 bits of compression) and the files (offset and size in the unpacked stream, flags, path).
 */
internal object UnityFs {
    private const val SIGNATURE = "UnityFS"
    private const val COMPRESSION_MASK = 0x3F
    private const val COMPRESSION_NONE = 0
    private const val COMPRESSION_LZ4 = 2
    private const val COMPRESSION_LZ4HC = 3
    private const val FLAG_TABLE_AT_END = 0x80
    private const val FLAG_PADDING_AFTER_TABLE = 0x200
    private const val ALIGNMENT = 16
    private const val MAX_TABLE_BYTES = 64 * 1024 * 1024
    private const val MAX_BLOCK_BYTES = 256 * 1024 * 1024
    private const val MAX_BLOCKS = 4_000_000
    private const val MAX_NODES = 100_000
    private const val MAX_STRING = 512
    private const val HASH_BYTES = 16
    private const val BLOCK_ENTRY_BYTES = 10
    private const val COPY_BUFFER = 1 shl 16

    internal class Block(val uncompressed: Int, val compressed: Int, val flags: Int)

    internal class Node(val offset: Long, val size: Long, val path: String)

    /** The archive's header and table, read; [dataStart] is where its first block begins in the file. */
    internal class Table(
        val version: Int,
        val unityVersion: String,
        val unityRevision: String,
        val flags: Int,
        /** The table, unpacked. */
        val info: ByteArray,
        /** Where the first block entry starts in [info]. */
        val blocksAt: Int,
        val blocks: List<Block>,
        val nodes: List<Node>,
        val dataStart: Long,
    )

    /**
     * Rewrites the archive from [open] into [destination], with the bytes of the file [node] changed by [edit]. [edit] gets the file's bytes, changes them in place (same length) and says
     * whether it changed anything. [open] is called more than once and must give the archive from its first byte each time.
     */
    fun rewriteNode(open: () -> InputStream, node: String, maxNodeBytes: Int, destination: File, edit: (ByteArray) -> Boolean): UnityFsOutcome {
        try {
            val table = open().use { readTable(it) }
            val target = table.nodes.firstOrNull { it.path == node } ?: return UnityFsOutcome.Unchanged
            if (target.size <= 0) return UnityFsOutcome.Unchanged
            if (target.size > maxNodeBytes) return UnityFsOutcome.Unsupported("the file $node is larger than $maxNodeBytes bytes")

            // Where each block begins in the unpacked stream, and which blocks hold the file.
            val starts = LongArray(table.blocks.size + 1)
            for (i in table.blocks.indices) starts[i + 1] = starts[i] + table.blocks[i].uncompressed
            if (target.offset < 0 || target.offset + target.size > starts[table.blocks.size]) throw UnityFormatException("the file $node lies outside the blocks")
            val first = (0 until table.blocks.size).first { target.offset < starts[it + 1] }
            val last = (first until table.blocks.size).first { target.offset + target.size <= starts[it + 1] }

            // Pass one: decode the blocks that hold the file.
            val joinedSize = (starts[last + 1] - starts[first]).toInt()
            val joined = ByteArray(joinedSize)
            open().use { input ->
                val reader = Reader(input)
                reader.skip(table.dataStart + packedBefore(table.blocks, first))
                for (i in first..last) {
                    val block = table.blocks[i]
                    val plain = decode(reader.bytes(block.compressed), block)
                    System.arraycopy(plain, 0, joined, (starts[i] - starts[first]).toInt(), plain.size)
                }
            }
            val inside = (target.offset - starts[first]).toInt()
            val bytes = joined.copyOfRange(inside, inside + target.size.toInt())
            if (!edit(bytes)) return UnityFsOutcome.Unchanged
            val edited = joined.copyOf().also { System.arraycopy(bytes, 0, it, inside, bytes.size) }
            val changed = BooleanArray(table.blocks.size)
            var any = false
            for (i in first..last) {
                val from = (starts[i] - starts[first]).toInt()
                val to = from + table.blocks[i].uncompressed
                if (!joined.copyOfRange(from, to).contentEquals(edited.copyOfRange(from, to))) {
                    changed[i] = true
                    any = true
                }
            }
            if (!any) return UnityFsOutcome.Unchanged

            // The table: the changed blocks are stored as they are decoded, and the table itself is stored without compression.
            val info = table.info.copyOf()
            var newPacked = 0L
            for (i in table.blocks.indices) {
                val entry = table.blocksAt + i * BLOCK_ENTRY_BYTES
                if (changed[i]) {
                    putInt(info, entry + 4, table.blocks[i].uncompressed)
                    putShort(info, entry + 8, table.blocks[i].flags and COMPRESSION_MASK.inv() and 0xFFFF)
                    newPacked += table.blocks[i].uncompressed
                } else {
                    newPacked += table.blocks[i].compressed
                }
            }
            val flags = table.flags and COMPRESSION_MASK.inv()
            val head = header(table, 0L, info.size, flags)
            val infoStart = align(head.size.toLong())
            val infoEnd = infoStart + info.size
            val dataStart = if (table.flags and FLAG_PADDING_AFTER_TABLE != 0) align(infoEnd) else infoEnd
            val total = dataStart + newPacked

            val counter = CrcCounter(destination.outputStream().buffered(COPY_BUFFER))
            counter.use { out ->
                out.write(header(table, total, info.size, flags))
                out.zeros(infoStart - head.size)
                out.write(info)
                out.zeros(dataStart - infoEnd)
                open().use { input ->
                    val reader = Reader(input)
                    reader.skip(table.dataStart)
                    val buffer = ByteArray(COPY_BUFFER)
                    for (i in table.blocks.indices) {
                        val block = table.blocks[i]
                        if (changed[i]) {
                            reader.skip(block.compressed.toLong())
                            val from = (starts[i] - starts[first]).toInt()
                            out.write(edited, from, block.uncompressed)
                        } else {
                            reader.copy(out, block.compressed.toLong(), buffer)
                        }
                    }
                }
            }
            if (counter.count != total) throw IOException("the archive written holds ${counter.count} bytes, expected $total")
            return UnityFsOutcome.Rewritten(counter.count, counter.crc.value)
        } catch (e: UnityFormatException) {
            return UnityFsOutcome.Unsupported(e.message ?: "unsupported archive")
        }
    }

    /** Reads the header and the table from the start of [input]. */
    internal fun readTable(input: InputStream): Table {
        val reader = Reader(input)
        if (reader.cString() != SIGNATURE) throw UnityFormatException("not a UnityFS archive")
        val version = reader.int()
        if (version != 7 && version != 8) throw UnityFormatException("UnityFS version $version is not handled")
        val unityVersion = reader.cString()
        val unityRevision = reader.cString()
        val declaredSize = reader.long()
        val packedInfo = reader.int()
        val unpackedInfo = reader.int()
        val flags = reader.int()
        if (packedInfo < 0 || unpackedInfo < 0 || packedInfo > MAX_TABLE_BYTES || unpackedInfo > MAX_TABLE_BYTES) throw UnityFormatException("the table has an impossible size")
        if (flags and FLAG_TABLE_AT_END != 0) throw UnityFormatException("a table at the end of the archive is not handled")
        reader.alignTo(ALIGNMENT)
        val raw = reader.bytes(packedInfo)
        val info = when (flags and COMPRESSION_MASK) {
            COMPRESSION_NONE -> if (packedInfo == unpackedInfo) raw else throw UnityFormatException("an unpacked table whose two sizes differ")
            COMPRESSION_LZ4, COMPRESSION_LZ4HC -> Lz4.decompress(raw, 0, raw.size, unpackedInfo)
            else -> throw UnityFormatException("the table uses compression ${flags and COMPRESSION_MASK}")
        }
        if (flags and FLAG_PADDING_AFTER_TABLE != 0) reader.alignTo(ALIGNMENT)
        val dataStart = reader.position

        var q = HASH_BYTES
        val blockCount = getInt(info, q).also { q += 4 }
        if (blockCount < 0 || blockCount > MAX_BLOCKS || q.toLong() + blockCount.toLong() * BLOCK_ENTRY_BYTES + 4 > info.size) throw UnityFormatException("the table lists $blockCount blocks")
        val blocksAt = q
        val blocks = ArrayList<Block>(blockCount)
        var packed = 0L
        for (i in 0 until blockCount) {
            val unpackedSize = getInt(info, q)
            val packedSize = getInt(info, q + 4)
            val blockFlags = getShort(info, q + 8)
            if (unpackedSize < 0 || packedSize < 0 || unpackedSize > MAX_BLOCK_BYTES || packedSize > MAX_BLOCK_BYTES) throw UnityFormatException("block $i has an impossible size")
            blocks += Block(unpackedSize, packedSize, blockFlags)
            packed += packedSize
            q += BLOCK_ENTRY_BYTES
        }
        val nodeCount = getInt(info, q).also { q += 4 }
        if (nodeCount < 0 || nodeCount > MAX_NODES) throw UnityFormatException("the table lists $nodeCount files")
        val nodes = ArrayList<Node>(nodeCount)
        for (i in 0 until nodeCount) {
            if (q + 20 > info.size) throw UnityFormatException("the table of files is cut")
            val offset = getLong(info, q)
            val size = getLong(info, q + 8)
            q += 20
            var end = q
            while (end < info.size && info[end].toInt() != 0) end++
            if (end >= info.size || end - q > MAX_STRING) throw UnityFormatException("a file path is not terminated")
            nodes += Node(offset, size, String(info, q, end - q, Charsets.UTF_8))
            q = end + 1
        }
        // The size the header gives must be what the blocks add up to: if it is not, this is not the layout described above.
        if (declaredSize != dataStart + packed) throw UnityFormatException("the archive says $declaredSize bytes, its blocks end at ${dataStart + packed}")
        return Table(version, unityVersion, unityRevision, flags, info, blocksAt, blocks, nodes, dataStart)
    }

    private fun header(table: Table, total: Long, infoSize: Int, flags: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(SIGNATURE.toByteArray(Charsets.US_ASCII)); out.write(0)
        out.write(intBytes(table.version))
        out.write(table.unityVersion.toByteArray(Charsets.US_ASCII)); out.write(0)
        out.write(table.unityRevision.toByteArray(Charsets.US_ASCII)); out.write(0)
        out.write(longBytes(total))
        out.write(intBytes(infoSize))
        out.write(intBytes(infoSize))
        out.write(intBytes(flags))
        return out.toByteArray()
    }

    private fun decode(raw: ByteArray, block: Block): ByteArray = when (block.flags and COMPRESSION_MASK) {
        COMPRESSION_NONE -> if (raw.size == block.uncompressed) raw else throw UnityFormatException("a stored block whose two sizes differ")
        COMPRESSION_LZ4, COMPRESSION_LZ4HC -> Lz4.decompress(raw, 0, raw.size, block.uncompressed)
        else -> throw UnityFormatException("a block uses compression ${block.flags and COMPRESSION_MASK}")
    }

    private fun packedBefore(blocks: List<Block>, index: Int): Long {
        var sum = 0L
        for (i in 0 until index) sum += blocks[i].compressed
        return sum
    }

    private fun align(value: Long): Long = (value + ALIGNMENT - 1) / ALIGNMENT * ALIGNMENT

    private fun getInt(b: ByteArray, at: Int): Int {
        if (at < 0 || at + 4 > b.size) throw UnityFormatException("the table is cut")
        return ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)
    }

    private fun getShort(b: ByteArray, at: Int): Int {
        if (at < 0 || at + 2 > b.size) throw UnityFormatException("the table is cut")
        return ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
    }

    private fun getLong(b: ByteArray, at: Int): Long = (getInt(b, at).toLong() shl 32) or (getInt(b, at + 4).toLong() and 0xFFFFFFFFL)

    private fun putInt(b: ByteArray, at: Int, value: Int) {
        b[at] = (value ushr 24).toByte(); b[at + 1] = (value ushr 16).toByte(); b[at + 2] = (value ushr 8).toByte(); b[at + 3] = value.toByte()
    }

    private fun putShort(b: ByteArray, at: Int, value: Int) {
        b[at] = (value ushr 8).toByte(); b[at + 1] = value.toByte()
    }

    private fun intBytes(value: Int) = ByteArray(4).also { putInt(it, 0, value) }

    private fun longBytes(value: Long) = ByteArray(8).also { putInt(it, 0, (value ushr 32).toInt()); putInt(it, 4, value.toInt()) }

    /** Reads an input stream front to back, and knows how far it is. */
    private class Reader(private val input: InputStream) {
        var position = 0L
            private set

        fun bytes(count: Int): ByteArray {
            if (count < 0) throw UnityFormatException("a negative length")
            val out = ByteArray(count)
            var done = 0
            while (done < count) {
                val n = input.read(out, done, count - done)
                if (n < 0) throw UnityFormatException("the archive ends before its table or blocks do")
                done += n
            }
            position += count
            return out
        }

        fun int(): Int = getInt(bytes(4), 0)

        fun long(): Long = getLong(bytes(8), 0)

        fun cString(): String {
            val out = java.io.ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b < 0) throw UnityFormatException("the archive ends inside its header")
                position++
                if (b == 0) break
                if (out.size() >= MAX_STRING) throw UnityFormatException("a string of the header is not terminated")
                out.write(b)
            }
            return String(out.toByteArray(), Charsets.US_ASCII)
        }

        fun skip(count: Long) {
            var left = count
            while (left > 0) {
                val n = input.skip(left)
                if (n > 0) {
                    left -= n
                } else {
                    if (input.read() < 0) throw UnityFormatException("the archive ends inside its blocks")
                    left--
                }
            }
            position += count
        }

        fun alignTo(boundary: Int) {
            val rest = (boundary - position % boundary) % boundary
            if (rest > 0) skip(rest)
        }

        fun copy(out: OutputStream, count: Long, buffer: ByteArray) {
            var left = count
            while (left > 0) {
                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n < 0) throw UnityFormatException("the archive ends inside its blocks")
                out.write(buffer, 0, n)
                left -= n
            }
            position += count
        }
    }

}
