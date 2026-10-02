package app.gameport.core.sync

import java.io.InputStream

/** Finds the Steam interface versions a game's library asks for (SteamClient022, SteamUser023, ...), read from its bytes. */
object SteamInterfaces {
    private val versioned = Regex("""Steam[A-Za-z]{3,24}\d{3}""")
    private val macro = Regex("""STEAM[A-Z_]{3,40}_INTERFACE_VERSION(?:_\d{3})?""")
    private const val BLOCK = 64 * 1024
    private const val OVERLAP = 80

    fun find(bytes: ByteArray): List<String> = find(bytes.inputStream())

    /** Reads the stream block by block, so a large library never sits in memory. */
    fun find(input: InputStream): List<String> {
        val found = sortedSetOf<String>()
        val buffer = ByteArray(BLOCK + OVERLAP)
        var carried = 0
        while (true) {
            val read = input.read(buffer, carried, BLOCK)
            if (read < 0) break
            val length = carried + read
            val text = String(CharArray(length) { index ->
                val b = buffer[index].toInt() and 0xFF
                if (b in 32..126) b.toChar() else '\n'
            })
            versioned.findAll(text).forEach { found += it.value }
            macro.findAll(text).forEach { found += it.value }
            // A name cut by the end of the block is found again with the next one.
            carried = minOf(OVERLAP, length)
            System.arraycopy(buffer, length - carried, buffer, 0, carried)
        }
        return found.toList()
    }
}
