package app.gameport.core.patch

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Renames the package a resource table belongs to, straight in its bytes. The table of a big game weighs tens of megabytes: decoding it into objects takes several times that
 * in memory, which a patch that otherwise only reads the manifest cannot afford. The name sits at a fixed place in the header of each package chunk (`ResTable_package`: type,
 * header size, size, id, then 128 UTF-16 characters), so the table is walked chunk by chunk and nothing else is read.
 */
internal object ResourceTableNames {
    private const val TABLE_TYPE = 0x0002
    private const val PACKAGE_TYPE = 0x0200
    private const val ID_AT = 8
    private const val NAME_AT = 12
    private const val NAME_CHARS = 128
    private const val PACKAGE_HEADER_MIN = NAME_AT + NAME_CHARS * 2

    /** The id every application's own resources have. */
    const val APP_PACKAGE_ID = 0x7f

    /**
     * Gives the package with id [packageId] the name [name], in [table] itself. Returns true when something changed; false when the table is not one, holds no such package, has
     * that name already, or when [name] does not fit (127 characters at most).
     */
    fun rename(table: ByteArray, name: String, packageId: Int = APP_PACKAGE_ID): Boolean {
        if (name.length >= NAME_CHARS || table.size < 12) return false
        val buffer = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN)
        if ((buffer.getShort(0).toInt() and 0xFFFF) != TABLE_TYPE) return false
        var offset = buffer.getShort(2).toInt() and 0xFFFF
        var changed = false
        while (offset + 8 <= table.size) {
            val type = buffer.getShort(offset).toInt() and 0xFFFF
            val size = buffer.getInt(offset + 4).toLong() and 0xFFFFFFFFL
            if (size < 8 || offset + size > table.size) break
            if (type == PACKAGE_TYPE && size >= PACKAGE_HEADER_MIN && buffer.getInt(offset + ID_AT) == packageId && writeName(table, offset + NAME_AT, name)) changed = true
            offset += size.toInt()
        }
        return changed
    }

    /** The name written at [at], up to its end mark. */
    fun nameAt(table: ByteArray, at: Int): String {
        val text = String(table, at, NAME_CHARS * 2, Charsets.UTF_16LE)
        return text.substringBefore('\u0000')
    }

    private fun writeName(table: ByteArray, at: Int, name: String): Boolean {
        if (nameAt(table, at) == name) return false
        val bytes = name.toByteArray(Charsets.UTF_16LE)
        java.util.Arrays.fill(table, at, at + NAME_CHARS * 2, 0)
        System.arraycopy(bytes, 0, table, at, bytes.size)
        return true
    }
}
