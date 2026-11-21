package app.gameport.core.patch.unity

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** The company and the product a Unity game was built with (`Application.companyName`, `Application.productName`). */
internal data class UnityNames(val company: String, val product: String)

/**
 * Makes a Unity game's company and product names follow the package name it runs as.
 *
 * Why: a game can build the folder of its own files from these two names instead of asking Android for its package: Escape Simulator looks for its asset bundles in
 * `Android/obb/` + `"com." + company + "." + product` (spaces taken out: `Pine Studio` and `Escape Simulator` make `com.PineStudio.EscapeSimulator`, the package it was released under).
 * Under another package name ([app.gameport.core.patch.patches.PackageRenamePatch]) the game looks in a folder that is not its own, which Android keeps shut, and starts without its
 * bundles: every text of its menu stays a bare key (`%singleplayer%`). The names are kept in the game's player settings (`globalgamemanagers`), written there once at build time.
 *
 * What is changed is narrow: only a pair of names that really give the package the game came with, once the spaces are taken out of them (a game whose names do not give its package does
 * not build its folder that way, and is left as it is). The new names are the parts of the package GamePort gives the game, **padded with spaces up to the size of the old ones**, which the
 * game takes out again: the size of the data never changes, so nothing that points into it moves. A package that does not fit (more letters than the old names had) is left alone.
 * The old names are kept in the game's manifest, so putting the game back under its own package restores them exactly.
 */
internal object UnityIdentity {
    /** A string longer than this is no name. */
    private const val MAX_NAME_BYTES = 200

    /** One pair of names found in serialized data: where the company string starts (at its length), and the names with the size in bytes each one takes. */
    internal class Found(val at: Int, val names: UnityNames, val companyBytes: Int, val productBytes: Int)

    /** The package Unity builds from two names: `com.<company>.<product>`, spaces taken out. */
    fun derive(names: UnityNames): String = "com." + names.company.filterNot { it == ' ' } + "." + names.product.filterNot { it == ' ' }

    /** [target] (`com.a.b`) as the two names that make it, or null when it is not of that form (a name needs a letter, and no dot or space). */
    fun split(target: String): UnityNames? {
        if (!target.startsWith("com.")) return null
        val rest = target.removePrefix("com.").split('.')
        if (rest.size != 2 || rest.any { it.isEmpty() || it.any { c -> c == ' ' } }) return null
        return UnityNames(rest[0], rest[1])
    }

    /** [wanted] followed by as many spaces as it takes to take the size in bytes of [current], or null when [wanted] is longer than [current]. */
    fun fit(current: String, wanted: String): String? {
        val room = current.toByteArray(Charsets.UTF_8).size
        val need = wanted.toByteArray(Charsets.UTF_8).size
        return if (need > room) null else wanted + " ".repeat(room - need)
    }

    /**
     * Every pair of strings of [data] that stand one after the other, as Unity serializes them (a 4 byte length, the bytes, zeros up to 4), for which [accept] says yes. Serialized
     * strings start on a multiple of 4. A pair found is not looked into again.
     */
    fun locate(data: ByteArray, accept: (UnityNames) -> Boolean): List<Found> {
        val found = ArrayList<Found>()
        var i = 0
        while (i + 8 <= data.size) {
            val company = stringAt(data, i)
            if (company != null) {
                val next = i + 4 + padded(company.second)
                val product = stringAt(data, next)
                if (product != null) {
                    val names = UnityNames(company.first, product.first)
                    if (accept(names)) {
                        found += Found(i, names, company.second, product.second)
                        i = next + 4 + padded(product.second)
                        continue
                    }
                }
            }
            i += 4
        }
        return found
    }

    /**
     * What a pair found must be changed to so the game runs as [target], or null when it is not to be changed. [recorded] is the pair the game was built with when an earlier pass wrote it down;
     * without it, the pair found is the original one if it gives [original]. [original] is the package the game came with.
     */
    fun wanted(found: UnityNames, recorded: UnityNames?, original: String, target: String): UnityNames? {
        val came = recorded ?: found.takeIf { derive(it) == original } ?: return null
        val wanted = if (target == original) {
            came
        } else {
            val parts = split(target) ?: return null
            UnityNames(fit(came.company, parts.company) ?: return null, fit(came.product, parts.product) ?: return null)
        }
        return if (wanted == found) null else wanted
    }

    /** Writes [names] over the pair [found], which they must match in size (the data keeps its size, so nothing after it moves). */
    fun write(data: ByteArray, found: Found, names: UnityNames) {
        val company = names.company.toByteArray(Charsets.UTF_8)
        val product = names.product.toByteArray(Charsets.UTF_8)
        require(company.size == found.companyBytes && product.size == found.productBytes) { "the new names do not match the size of the old ones" }
        System.arraycopy(company, 0, data, found.at + 4, company.size)
        val productAt = found.at + 4 + padded(found.companyBytes)
        System.arraycopy(product, 0, data, productAt + 4, product.size)
    }

    /** The string that starts at [at] (its text and its size in bytes), or null when what is there is not a name. */
    private fun stringAt(data: ByteArray, at: Int): Pair<String, Int>? {
        if (at < 0 || at + 4 > data.size) return null
        val length = (data[at].toInt() and 0xFF) or ((data[at + 1].toInt() and 0xFF) shl 8) or ((data[at + 2].toInt() and 0xFF) shl 16) or ((data[at + 3].toInt() and 0xFF) shl 24)
        if (length < 1 || length > MAX_NAME_BYTES || at.toLong() + 4 + length > data.size) return null
        for (k in 0 until length) {
            val b = data[at + 4 + k].toInt() and 0xFF
            if (b < 0x20 || b == 0x7F) return null
        }
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data, at + 4, length)).toString()
        } catch (e: CharacterCodingException) {
            return null
        }
        return text to length
    }

    /** A string takes its size up to the next multiple of 4. */
    private fun padded(length: Int): Int = (length + 3) / 4 * 4
}
