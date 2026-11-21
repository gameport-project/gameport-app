package app.gameport.core.patch.unity

/** The data of a game's file is not what its format says it is: the file is left alone rather than guessed at. */
internal class UnityFormatException(message: String) : Exception(message)

/**
 * LZ4 block decoder, for the blocks of a Unity archive (`UnityFS`, compression types 2 and 3). Strict: every length and every distance is checked against the
 * input and the output, so a damaged or unexpected block ends in [UnityFormatException] and never in a write outside the arrays. It keeps no state between calls.
 */
internal object Lz4 {
    private const val MIN_MATCH = 4

    /** Decodes [length] bytes of [src] from [offset] into exactly [outSize] bytes. */
    fun decompress(src: ByteArray, offset: Int, length: Int, outSize: Int): ByteArray {
        if (outSize < 0 || offset < 0 || length < 0 || offset > src.size - length) throw UnityFormatException("lz4: bad bounds")
        val dst = ByteArray(outSize)
        var s = offset
        val end = offset + length
        var d = 0
        while (s < end) {
            val token = src[s++].toInt() and 0xFF
            var literals = token ushr 4
            if (literals == 15) {
                while (true) {
                    if (s >= end) throw UnityFormatException("lz4: literal length cut")
                    val b = src[s++].toInt() and 0xFF
                    literals += b
                    if (literals < 0 || literals > outSize) throw UnityFormatException("lz4: literal length too large")
                    if (b != 255) break
                }
            }
            if (literals > end - s || literals > outSize - d) throw UnityFormatException("lz4: literals out of range")
            System.arraycopy(src, s, dst, d, literals)
            s += literals
            d += literals
            // The last sequence of a block holds literals only.
            if (s >= end) break
            if (end - s < 2) throw UnityFormatException("lz4: offset cut")
            val distance = (src[s].toInt() and 0xFF) or ((src[s + 1].toInt() and 0xFF) shl 8)
            s += 2
            if (distance == 0 || distance > d) throw UnityFormatException("lz4: distance out of range")
            var match = token and 15
            if (match == 15) {
                while (true) {
                    if (s >= end) throw UnityFormatException("lz4: match length cut")
                    val b = src[s++].toInt() and 0xFF
                    match += b
                    if (match < 0 || match > outSize) throw UnityFormatException("lz4: match length too large")
                    if (b != 255) break
                }
            }
            match += MIN_MATCH
            if (match > outSize - d) throw UnityFormatException("lz4: match out of range")
            var from = d - distance
            // Copied byte by byte on purpose: a match may overlap what it writes (a distance shorter than its length repeats a pattern).
            for (i in 0 until match) dst[d++] = dst[from++]
        }
        if (d != outSize) throw UnityFormatException("lz4: decoded $d bytes, expected $outSize")
        return dst
    }
}
