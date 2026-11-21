package app.gameport.core.patch.unity

import java.io.OutputStream
import java.util.zip.CRC32

/** An output stream that counts what goes through it and keeps its CRC-32, for the zip entry that will hold the file written. */
internal class CrcCounter(private val out: OutputStream) : OutputStream() {
    val crc = CRC32()

    var count = 0L
        private set

    override fun write(b: Int) {
        out.write(b)
        crc.update(b)
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        crc.update(b, off, len)
        count += len
    }

    /** Writes [count] zero bytes. */
    fun zeros(count: Long) {
        var left = count
        val zero = ByteArray(minOf(left, 4096L).toInt().coerceAtLeast(1))
        while (left > 0) {
            val n = minOf(left, zero.size.toLong()).toInt()
            write(zero, 0, n)
            left -= n
        }
    }

    override fun flush() = out.flush()

    override fun close() = out.close()
}
