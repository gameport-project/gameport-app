package app.gameport.core.model

/**
 * What the game itself says about its expansion files, set against what Steam says it has. The game can list its own folder, which no other app can read: it sends the
 * size of each file it holds, and the sizes are compared with those of the files the depot puts there. Names are not compared, since they follow the version code the game
 * was patched to. A file of the right size that is damaged is not found this way: that would take reading all of it.
 */
object ExpansionVerification {
    sealed interface Result {
        /** Every file Steam lists for the game is there, by size (or Steam lists none). */
        data object Complete : Result

        /** [missing] of the [expected] files have no file of that size on the device, [missingBytes] in all. */
        data class Missing(val missing: Int, val expected: Int, val missingBytes: Long) : Result
    }

    /** [expected]: the sizes of the files the depot puts in the game's folder; [held]: the sizes of the files the game holds in its own. */
    fun compare(expected: List<Long>, held: List<Long>): Result {
        val left = held.toMutableList()
        val missing = mutableListOf<Long>()
        for (size in expected) if (!left.remove(size)) missing += size
        return if (missing.isEmpty()) Result.Complete else Result.Missing(missing.size, expected.size, missing.sum())
    }
}
