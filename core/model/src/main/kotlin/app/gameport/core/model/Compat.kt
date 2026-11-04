package app.gameport.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the players of one kind of device said about a game. [worksOffline] is part of [works]. */
@Serializable
data class DeviceCounts(
    val works: Int,
    val fails: Int,
    val worksOffline: Int = 0,
    val offlineOnly: Int = 0,
    /** What players ticked as going wrong, by code of [Symptom] (see the relay's README). Only what the relay counted. */
    val issues: Map<String, Int> = emptyMap(),
    /** The same totals for each model of this kind of device, by what the device calls itself ("Quest 3"); empty when no vote came with its model. */
    val models: Map<String, ModelCounts> = emptyMap(),
    /** What the history of the answers says of this kind of device: from which version it works after it did not, and so on. Null when it says nothing. */
    val turn: CompatTurn? = null,
) {
    /** The symptoms players reported, most reported first; a code this version does not know is left out. */
    fun reported(): List<Pair<Symptom, Int>> = issues.mapNotNull { (code, n) -> Symptom.parse(listOf(code)).firstOrNull()?.let { it to n } }.sortedWith(compareByDescending<Pair<Symptom, Int>> { it.second }.thenBy { it.first.ordinal })
}

/** What the players of one model of device said about a game: how many said it works, works offline only, or fails, and the name the relay knows the model by. */
@Serializable
data class ModelCounts(val works: Int = 0, val offlineOnly: Int = 0, val fails: Int = 0, val name: String? = null, val issues: Map<String, Int> = emptyMap())

/**
 * What the history of the answers says of a game for one kind of device (see the relay's README): [fixedIn] the oldest version of GamePort where a player said it works after saying it
 * did not on an older one ([failedIn]), [stillIn] the newest version where it works, [brokeIn] the oldest version where a player said it did not work after saying it did.
 */
@Serializable
data class CompatTurn(val fixedIn: String? = null, val failedIn: String? = null, val stillIn: String? = null, val brokeIn: String? = null) {
    /** The last word of the history is that it broke: in a version newer than the one that fixed it, or it was never fixed. */
    val regression: Boolean get() = brokeIn != null && (fixedIn == null || (VersionOrder.compare(brokeIn, fixedIn) ?: 0) > 0)
}

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
    val issues: Map<String, Int> = emptyMap(),
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

/** What a verdict is about: the exact model of the device, its kind (a Quest, a Pico, a phone), or every device that was tried. */
enum class CompatScope { MODEL, KIND, ALL }

/**
 * The verdict of the players on a game, with the numbers it rests on. Its three counts are the three answers. [device] is the kind of device in hand; [scope] says how narrow the
 * verdict is, and [name] is the model it is about when [scope] is [CompatScope.MODEL].
 */
data class Compat(
    val level: CompatLevel,
    val device: String,
    val works: Int,
    val offlineOnly: Int,
    val fails: Int,
    val worksOffline: Int,
    val scope: CompatScope = CompatScope.KIND,
    val name: String? = null,
) {
    val players: Int get() = works + offlineOnly + fails

    /** Few players answered: the verdict is shown, but it may not be reliable. */
    val unreliable: Boolean get() = players < CompatRules.MIN_PLAYERS

    /** Enough players tried it with the offline mode on to say so. */
    val offlineTested: Boolean get() = worksOffline >= CompatRules.MIN_PLAYERS
}

object CompatRules {
    /** Under this many answers the verdict is shown with a warning, since one player's luck is not a verdict; it also takes this many to say a game was tried offline. */
    const val MIN_PLAYERS = 3

    /** Share of the answers from which a game is said to work, and under which it is said not to. */
    private const val WORKS_SHARE = 0.75
    private const val FAILS_SHARE = 0.25

    /** Answers a model needs to have of its own before its verdict is the one shown. */
    const val MIN_MODEL_ANSWERS = 2

    /** What the players of [device] (a kind of device) say of the game, or null while none of them said anything: what players of another kind said does not count. */
    fun of(counts: CompatCounts?, device: String): Compat? {
        val own = counts?.devices?.get(device) ?: return null
        return verdict(device, own.works, own.offlineOnly, own.fails, own.worksOffline, CompatScope.KIND, null)
    }

    /**
     * What the players say of the game for the device in hand, as narrowly as there are answers to say it: the exact [model] when at least [MIN_MODEL_ANSWERS] players with it
     * answered, else the kind of device ([device]) when any did, else every device that was tried. Null while nobody said anything.
     */
    fun ofDevice(counts: CompatCounts?, device: String, model: String?): Compat? {
        counts ?: return null
        val own = counts.devices[device]
        val modelCounts = model?.let { own?.models?.get(it) }
        if (modelCounts != null && modelCounts.works + modelCounts.offlineOnly + modelCounts.fails >= MIN_MODEL_ANSWERS) {
            val name = CompatRows.shortName(modelCounts.name) ?: model
            return verdict(device, modelCounts.works, modelCounts.offlineOnly, modelCounts.fails, 0, CompatScope.MODEL, name)
        }
        of(counts, device)?.let { return it }
        return verdict(device, counts.works, counts.offlineOnly, counts.fails, counts.worksOffline, CompatScope.ALL, null)
    }

    private fun verdict(device: String, works: Int, offlineOnly: Int, fails: Int, worksOffline: Int, scope: CompatScope, name: String?): Compat? {
        val total = works + offlineOnly + fails
        if (total == 0) return null
        // A game that works without the network works, for this: the answers "it works" and "it works offline only" are on the same side.
        val positive = works + offlineOnly
        val share = positive.toDouble() / total
        val level = when {
            share >= WORKS_SHARE -> if (offlineOnly * 2 >= positive) CompatLevel.OFFLINE_ONLY else CompatLevel.WORKS
            share <= FAILS_SHARE -> CompatLevel.FAILS
            else -> CompatLevel.MIXED
        }
        return Compat(level, device, works, offlineOnly, fails, worksOffline.coerceIn(0, works), scope, name)
    }
}
