package app.gameport.core.patch.unity

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Builds Unity archives (`UnityFS`) in memory for the tests, and unpacks them again with the code under test. */
internal object TestArchive {
    fun be32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    fun be16(v: Int) = byteArrayOf((v ushr 8).toByte(), v.toByte())

    fun be64(v: Long) = be32((v ushr 32).toInt()) + be32(v.toInt())

    /** An LZ4 block made of literals only: valid for any decoder. */
    fun literalOnly(src: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(minOf(src.size, 15) shl 4)
        if (src.size >= 15) {
            var rest = src.size - 15
            while (rest >= 255) {
                out.write(255)
                rest -= 255
            }
            out.write(rest)
        }
        out.write(src)
        return out.toByteArray()
    }

    /** An LZ4 block for [length] times [byte]: one literal, then one long match at distance 1, so that the matches of the decoder are used by the archives too. */
    fun run(byte: Byte, length: Int): ByteArray {
        require(length >= 6)
        val out = ByteArrayOutputStream()
        val match = length - 1 - 4
        out.write((1 shl 4) or minOf(match, 15))
        out.write(byte.toInt())
        out.write(1)
        out.write(0)
        if (match >= 15) {
            var rest = match - 15
            while (rest >= 255) {
                out.write(255)
                rest -= 255
            }
            out.write(rest)
        }
        return out.toByteArray()
    }

    private fun pack(block: ByteArray, type: Int): ByteArray = when (type) {
        2, 3 -> if (block.size >= 6 && block.all { it == block[0] }) run(block[0], block.size) else literalOnly(block)
        else -> block
    }

    private fun align16(value: Int) = (value + 15) / 16 * 16

    /**
     * An archive holding [files] one after the other, cut into blocks of [blockSize]. [blockType] and [tableType] are the compression of the blocks and of the table (0 none, 2 and 3 LZ4;
     * 1 is not decodable here: the blocks are only marked with it). [perBlockType] gives the compression of one block. [extraFlags] is added to the flags of the header.
     */
    fun build(
        files: List<Pair<String, ByteArray>>,
        blockSize: Int = 64,
        blockType: Int = 2,
        tableType: Int = 3,
        version: Int = 8,
        paddingFlag: Boolean = true,
        extraFlags: Int = 0x40,
        perBlockType: ((Int) -> Int)? = null,
    ): ByteArray {
        val stream = ByteArrayOutputStream().also { o -> files.forEach { o.write(it.second) } }.toByteArray()
        val blocks = (0 until stream.size step blockSize).map { stream.copyOfRange(it, minOf(it + blockSize, stream.size)) }
        val types = blocks.indices.map { perBlockType?.invoke(it) ?: blockType }
        val packed = blocks.mapIndexed { i, block -> pack(block, types[i]) }
        val info = ByteArrayOutputStream().apply {
            write(ByteArray(16) { (it + 1).toByte() })
            write(be32(blocks.size))
            blocks.forEachIndexed { i, block ->
                write(be32(block.size))
                write(be32(packed[i].size))
                write(be16(types[i]))
            }
            write(be32(files.size))
            var offset = 0L
            for ((name, data) in files) {
                write(be64(offset))
                write(be64(data.size.toLong()))
                write(be32(4))
                write(name.toByteArray())
                write(0)
                offset += data.size
            }
        }.toByteArray()
        val table = if (tableType == 2 || tableType == 3) literalOnly(info) else info
        val flags = tableType or extraFlags or (if (paddingFlag) 0x200 else 0)
        val unityVersion = "5.x.x"
        val revision = "6000.3.6f1"
        val headerLength = 8 + 4 + unityVersion.length + 1 + revision.length + 1 + 8 + 12
        val infoStart = align16(headerLength)
        val infoEnd = infoStart + table.size
        val dataStart = if (paddingFlag) align16(infoEnd) else infoEnd
        val total = dataStart.toLong() + packed.sumOf { it.size }
        return ByteArrayOutputStream().apply {
            write("UnityFS".toByteArray()); write(0)
            write(be32(version))
            write(unityVersion.toByteArray()); write(0)
            write(revision.toByteArray()); write(0)
            write(be64(total))
            write(be32(table.size))
            write(be32(info.size))
            write(be32(flags))
            write(ByteArray(infoStart - size()))
            write(table)
            write(ByteArray(dataStart - size()))
            packed.forEach { write(it) }
        }.toByteArray()
    }

    /** The uncompressed stream of an archive: what its files are cut from. Decoded with the code under test. */
    fun unpack(archive: ByteArray): ByteArray {
        val table = UnityFs.readTable(ByteArrayInputStream(archive))
        var at = table.dataStart.toInt()
        val out = ByteArrayOutputStream()
        for (block in table.blocks) {
            val raw = archive.copyOfRange(at, at + block.compressed)
            at += block.compressed
            out.write(
                when (block.flags and 0x3F) {
                    0 -> raw
                    2, 3 -> Lz4.decompress(raw, 0, raw.size, block.uncompressed)
                    else -> error("a block that cannot be decoded here")
                },
            )
        }
        return out.toByteArray()
    }

    /** Serialized strings as Unity writes them: a 4 byte length, the bytes, zeros up to a multiple of 4. */
    fun serialized(vararg strings: String): ByteArray = ByteArrayOutputStream().apply {
        for (s in strings) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            write(byteArrayOf(bytes.size.toByte(), (bytes.size ushr 8).toByte(), 0, 0))
            write(bytes)
            write(ByteArray((4 - bytes.size % 4) % 4))
        }
    }.toByteArray()
}
