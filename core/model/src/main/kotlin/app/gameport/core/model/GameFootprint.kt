package app.gameport.core.model

/**
 * What an installed game takes on the device, as far as GamePort can see. Three parts are counted: the installed app (the files of its install folder that Android lets GamePort read: its APKs and libraries), its expansion
 * files, and what GamePort itself keeps for the game (files waiting to be handed over, a download left behind). What the game keeps for itself (its data, its caches) lies
 * in places Android hides from other apps, so it is not in the figures.
 */
data class GameFootprint(
    val appBytes: Long,
    /** The expansion files, or null when GamePort has no access to the folder they live in, so their size is unknown rather than zero. */
    val expansionBytes: Long?,
    val keptBytes: Long,
    /** [expansionBytes] was written down when the files were put in place, not measured now: it says what the game was installed with. */
    val expansionRecorded: Boolean = false,
) {
    /** The sum of what is known: an unknown expansion adds nothing, and [expansionUnknown] says so. */
    val knownBytes: Long get() = appBytes + (expansionBytes ?: 0L) + keptBytes

    val expansionUnknown: Boolean get() = expansionBytes == null
}

/** A download that stopped and left files behind. [bytes] is what those files take. */
data class UnfinishedDownload(val appId: Int, val bytes: Long)

/** The size of all the games together, and how many of them have a part that is not in it. */
data class FootprintTotal(val bytes: Long, val withUnknownExpansion: Int)

fun Collection<GameFootprint>.total(): FootprintTotal = FootprintTotal(sumOf { it.knownBytes }, count { it.expansionUnknown })
