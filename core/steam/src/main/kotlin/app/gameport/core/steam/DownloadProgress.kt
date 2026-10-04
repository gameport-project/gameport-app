package app.gameport.core.steam

/** The progress of a download of several depots, as one figure. */
internal object DownloadProgress {
    /**
     * The share of the whole that is done. Each depot counts for what it weighs ([depotBytes], the same for all of them when unknown), and a
     * depot that has not started counts as nothing done: a game of 18 GB with four extras of half a GB each is not 80 % done when the extras are.
     */
    fun overall(depotIds: Collection<Int>, fractions: Map<Int, Float>, depotBytes: Map<Int, Long>): Float {
        if (depotIds.isEmpty()) return 0f
        val weight = { id: Int -> depotBytes[id]?.takeIf { it > 0 }?.toDouble() ?: 1.0 }
        val total = depotIds.sumOf(weight)
        val done = depotIds.sumOf { id -> (fractions[id] ?: 0f).coerceIn(0f, 1f) * weight(id) }
        return (done / total).toFloat().coerceIn(0f, 1f)
    }
}
