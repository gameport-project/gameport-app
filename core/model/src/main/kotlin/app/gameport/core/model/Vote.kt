package app.gameport.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What a player says about a game once it has closed. [wire] is how the relay writes it. */
enum class Verdict(val wire: String) {
    WORKS("works"),
    FAILS("fails"),
    ;

    companion object {
        fun fromWire(wire: String?): Verdict? = entries.firstOrNull { it.wire == wire }
    }
}

/** The kind of device a vote comes from, in the few words the relay accepts: nothing finer than that is sent. */
object VoteDevice {
    fun of(platformId: String?, tablet: Boolean): String = when {
        platformId == "meta" -> "quest"
        platformId == "pico" -> "pico"
        platformId != null -> "other"
        tablet -> "tablet"
        else -> "phone"
    }
}

/** The message sent to the relay: only these fields, never a name, a path or a serial number. See the relay's README. */
data class VoteMessage(
    val appId: Int,
    val verdict: Verdict,
    val appVersion: String,
    val device: String,
    val voter: String,
    val gameBuild: String? = null,
    /** The game worked with GamePort's offline mode on (see [OfflineEvidence]). Only said when true. */
    val offline: Boolean = false,
) {
    fun toJson(): String = buildJsonObject {
        put("v", 1)
        put("appId", appId)
        put("verdict", verdict.wire)
        put("app", appVersion)
        put("device", device)
        put("voter", voter)
        gameBuild?.let { put("game", JsonPrimitive(it)) }
        if (offline) put("offline", true)
    }.toString()
}

/** What is known about one game: the last answer, the version of GamePort and the day it was last asked, and whether the answer reached the relay. */
@Serializable
data class GameVerdict(
    val verdict: String? = null,
    val askedFor: String? = null,
    val askedDay: String? = null,
    val sent: Boolean = true,
    /** The answer was "it worked", and the game had been played with the offline mode on. */
    val offline: Boolean = false,
)

/** An answer that has not reached the relay yet. */
data class Answer(val appId: Int, val verdict: Verdict, val offline: Boolean)

/**
 * The answers a player gave, and the games that are waiting to be asked about, as plain data so the rules are tested without a device. A game is
 * asked about when it closes, but not twice for the same version of GamePort, unless the last answer was "it did not work": then once a day, so the
 * player can say when it works. [today] is a day, YYYY-MM-DD.
 */
@Serializable
data class VerdictBook(
    val games: Map<Int, GameVerdict> = emptyMap(),
    val pending: Set<Int> = emptySet(),
    /** The games whose last run was a clean test of the offline mode (see [OfflineEvidence]). */
    val offlineRuns: Set<Int> = emptySet(),
) {
    fun shouldAsk(appId: Int, appVersion: String, today: String): Boolean {
        val known = games[appId] ?: return true
        if (known.askedFor != appVersion) return true
        return known.verdict == Verdict.FAILS.wire && known.askedDay != today
    }

    /** A game closed: it waits to be asked about if the rules say so. [offlineRun] is whether this run proves the offline mode (see [OfflineEvidence]). */
    fun closed(appId: Int, appVersion: String, today: String, offlineRun: Boolean = false): VerdictBook {
        val runs = if (offlineRun) offlineRuns + appId else offlineRuns - appId
        return if (shouldAsk(appId, appVersion, today)) copy(pending = pending + appId, offlineRuns = runs) else copy(offlineRuns = runs)
    }

    /** The player answered. [send] is false when they chose not to share: the answer stays on this device. */
    fun answered(appId: Int, verdict: Verdict, appVersion: String, today: String, send: Boolean): VerdictBook =
        copy(
            games = games + (appId to GameVerdict(verdict.wire, appVersion, today, sent = !send, offline = verdict == Verdict.WORKS && appId in offlineRuns)),
            pending = pending - appId,
            offlineRuns = offlineRuns - appId,
        )

    /** The player closed the question without answering: it is not asked again for this version. */
    fun dismissed(appId: Int, appVersion: String, today: String): VerdictBook =
        copy(games = games + (appId to (games[appId] ?: GameVerdict()).copy(askedFor = appVersion, askedDay = today)), pending = pending - appId)

    /** The answers the relay has not received yet. */
    fun unsent(): List<Answer> = games.mapNotNull { (appId, game) -> if (!game.sent) Verdict.fromWire(game.verdict)?.let { Answer(appId, it, game.offline) } else null }

    /** The relay received this answer, unless the player changed it meanwhile. */
    fun sent(appId: Int, verdict: Verdict): VerdictBook {
        val game = games[appId] ?: return this
        return if (game.verdict == verdict.wire) copy(games = games + (appId to game.copy(sent = true))) else this
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String?): VerdictBook = runCatching { json.decodeFromString(serializer(), text.orEmpty()) }.getOrDefault(VerdictBook())
    }
}

/**
 * When a run of a game says something about the offline mode: it was chosen by the player (not a connection that failed), the device did have a
 * network (so it was a real choice), and no window about another device playing was opened during the launch (the game may have run without its
 * ticket for that reason). Only then is "it worked offline" added to a "it worked" answer, with no question asked.
 */
object OfflineEvidence {
    fun qualifies(connection: SteamConnection, networkAvailable: Boolean, otherDeviceAsked: Boolean): Boolean =
        connection == SteamConnection.OFFLINE_MODE && networkAvailable && !otherDeviceAsked
}
