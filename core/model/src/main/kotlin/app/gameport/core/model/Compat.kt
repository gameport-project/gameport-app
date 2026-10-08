package app.gameport.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the players of one kind of device said about a game. [worksOffline] is part of [works]. */
@Serializable
data class DeviceCounts(val works: Int, val fails: Int, val worksOffline: Int = 0, val offlineOnly: Int = 0)

/**
 * What the players said about one game, as the relay publishes it (see the relay's README): the totals, and the same for each kind of device
 * ("quest", "pico", "phone", "tablet", "other"), since a game that works on one may not on another. [worksOffline] is part of [works].
 */
@Serializable
data class CompatCounts(
    val appId: Int,
    val works: Int,
    val fails: Int,
    val worksOffline: Int = 0,
    val devices: Map<String, DeviceCounts> = emptyMap(),
    val offlineOnly: Int = 0,
)

@Serializable
data class CompatSummary(val v: Int, val games: List<CompatCounts> = emptyList()) {
    fun of(appId: Int): CompatCounts? = games.firstOrNull { it.appId == appId }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The relay's answer, or an empty summary when it cannot be read: a bad answer never hides a good one already kept. */
        fun parse(text: String?): CompatSummary? = runCatching { json.decodeFromString(serializer(), text.orEmpty()) }.getOrNull()?.takeIf { it.v == 1 }
    }
}

/** How a game fares for the players who said. */
enum class CompatLevel {
    WORKS,

    /** It works for most players, and for at least half of them only without the network. */
    OFFLINE_ONLY,
    MIXED,
    FAILS,
}

/** The verdict of the players on a game, for one kind of device, with the numbers it rests on. Its three counts are the three answers. */
data class Compat(val level: CompatLevel, val device: String, val works: Int, val offlineOnly: Int, val fails: Int, val worksOffline: Int) {
    val players: Int get() = works + offlineOnly + fails

    /** Enough players tried it with the offline mode on to say so. */
    val offlineTested: Boolean get() = worksOffline >= CompatRules.MIN_PLAYERS
}

object CompatRules {
    /** Under this many answers nothing is said about a game: one player's luck is not a verdict. */
    const val MIN_PLAYERS = 3

    /** Share of the answers from which a game is said to work, and under which it is said not to. */
    private const val WORKS_SHARE = 0.75
    private const val FAILS_SHARE = 0.25

    /** What the players of [device] (a kind of device) say of the game, or null while too few of them said anything: what players of another kind said does not count. */
    fun of(counts: CompatCounts?, device: String): Compat? {
        val own = counts?.devices?.get(device) ?: return null
        val total = own.works + own.offlineOnly + own.fails
        if (total < MIN_PLAYERS) return null
        // A game that works without the network works, for this: the answers "it works" and "it works offline only" are on the same side.
        val positive = own.works + own.offlineOnly
        val share = positive.toDouble() / total
        val level = when {
            share >= WORKS_SHARE -> if (own.offlineOnly * 2 >= positive) CompatLevel.OFFLINE_ONLY else CompatLevel.WORKS
            share <= FAILS_SHARE -> CompatLevel.FAILS
            else -> CompatLevel.MIXED
        }
        return Compat(level, device, own.works, own.offlineOnly, own.fails, own.worksOffline.coerceIn(0, own.works))
    }
}
