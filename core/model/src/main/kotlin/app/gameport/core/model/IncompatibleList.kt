package app.gameport.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Why a game cannot run through GamePort: a title and an explanation, by language, shared by every game that has this reason. */
@Serializable
data class IncompatibleReason(val title: Localized, val text: Localized, val kind: String = "blocks") {
    /** A reason that warns leaves the game on the home and playable: the player is only told, on the game's page. */
    val warns: Boolean get() = kind == "warns"

    /** What goes between the title and the text on one line: a colon, spaced the way the language writes it. */
    fun separator(language: String): String = if (language == "fr") " : " else ": "
}

/**
 * A game confirmed as incompatible, found by its Steam app id. [name] is only there to read the file. [devices] limits it to some devices: kinds ("quest", "pico", "phone", "tablet",
 * "other") and model names ("Quest 3"), none meaning every device. A device is told apart by [appliesTo].
 */
@Serializable
data class IncompatibleGame(val appId: Int, val name: String, val reason: String, val devices: List<String> = emptyList()) {
    /** Whether the game is incompatible on a device that goes by these names (its kind, the name people know it by, what it calls itself). */
    fun appliesTo(names: Set<String>): Boolean {
        if (devices.isEmpty()) return true
        val own = names.map(::plain).toSet()
        return devices.any { plain(it) in own }
    }

    private fun plain(name: String): String = name.trim().lowercase().removePrefix("meta ").removePrefix("oculus ").trim()
}

/**
 * The games confirmed as incompatible (kept in the relay, edited in the developer's console). They are hidden from the home, and the player is told once for each
 * game that is in the library. Only a game proven not to run belongs here, never one that merely failed once.
 */
@Serializable
data class IncompatibleList(
    val reasons: Map<String, IncompatibleReason> = emptyMap(),
    /** The games that cannot run: hidden from the home. */
    val games: List<IncompatibleGame> = emptyList(),
    /** The games that run but come with a warning (a reason that warns), for instance that they may close on their own: told on the game's page, never hidden. */
    val warnings: List<IncompatibleGame> = emptyList(),
) {
    // A game can have a line for each reason, each limited to some devices: the first that holds for this device is the one told.
    private val byId = games.groupBy { it.appId }
    private val warningsById = warnings.groupBy { it.appId }

    fun game(appId: Int): IncompatibleGame? = byId[appId]?.firstOrNull()

    /** The warning for a game on this device, or null. */
    fun warning(appId: Int): IncompatibleGame? = warningsById[appId]?.firstOrNull()

    /** The list as it holds for a device that goes by these names: a game limited to other devices is left out. */
    fun forDevice(names: Set<String>): IncompatibleList = IncompatibleList(reasons, games.filter { it.appliesTo(names) }, warnings.filter { it.appliesTo(names) })

    fun reasonOf(appId: Int): IncompatibleReason? = game(appId)?.let { reasons[it.reason] }

    /** The reason of the warning for a game, with its title and text in each language. */
    fun warningReasonOf(appId: Int): IncompatibleReason? = warning(appId)?.let { reasons[it.reason] }

    /** What is wrong with the file, one sentence each: empty when it is fine. */
    fun problems(languages: List<String>): List<String> {
        val found = mutableListOf<String>()
        val every = games + warnings
        every.groupBy { it.appId to it.reason }.filter { it.value.size > 1 }.keys.forEach { found += "the app id ${it.first} is listed twice for ${it.second}" }
        every.filter { it.reason !in reasons }.forEach { found += "${it.name}: unknown reason ${it.reason}" }
        reasons.forEach { (id, reason) ->
            for ((where, text) in listOf("title" to reason.title, "text" to reason.text)) {
                for (language in languages) {
                    val value = text[language]
                    if (value.isNullOrBlank()) found += "reason $id $where: no text in $language"
                    else if (';' in value) found += "reason $id $where ($language): a semicolon"
                }
            }
        }
        return found
    }

    companion object {
        private val relayJson = Json { ignoreUnknownKeys = true }

        /**
         * The list the relay publishes (`/incompatible`): a table of reasons, in both languages, and the games, each naming one. Null when the text is not that
         * list, so a bad answer never replaces a good one.
         */
        fun fromRelay(text: String): IncompatibleList? = runCatching {
            val read = relayJson.decodeFromString(RelayList.serializer(), text)
            if (read.v != 1) return null
            // The lines whose reason warns are kept apart: they never hide a game. A reason the file does not know blocks, as before.
            val (warning, blocking) = read.games.partition { read.reasons[it.reason]?.warns == true }
            IncompatibleList(reasons = read.reasons, games = blocking, warnings = warning)
        }.getOrNull()
    }

    @Serializable
    private class RelayList(val v: Int = 0, val reasons: Map<String, IncompatibleReason> = emptyMap(), val games: List<IncompatibleGame> = emptyList())
}
