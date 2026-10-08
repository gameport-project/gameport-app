package app.gameport.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Why a game cannot run through GamePort: a title and an explanation, by language, shared by every game that has this reason. */
@Serializable
data class IncompatibleReason(val title: Localized, val text: Localized) {
    /** What goes between the title and the text on one line: a colon, spaced the way the language writes it. */
    fun separator(language: String): String = if (language == "fr") " : " else ": "
}

/** A game confirmed as incompatible, found by its Steam app id. [name] is only there to read the file. */
@Serializable
data class IncompatibleGame(val appId: Int, val name: String, val reason: String)

/**
 * The games confirmed as incompatible (`incompatible.json` in the assets). They are hidden from the home, and the player is told once for each
 * game that is in the library. Only a game proven not to run belongs here, never one that merely failed once.
 */
@Serializable
data class IncompatibleList(val reasons: Map<String, IncompatibleReason> = emptyMap(), val games: List<IncompatibleGame> = emptyList()) {
    private val byId = games.associateBy { it.appId }

    fun game(appId: Int): IncompatibleGame? = byId[appId]

    fun reasonOf(appId: Int): IncompatibleReason? = byId[appId]?.let { reasons[it.reason] }

    /** What is wrong with the file, one sentence each: empty when it is fine. */
    fun problems(languages: List<String>): List<String> {
        val found = mutableListOf<String>()
        games.groupBy { it.appId }.filter { it.value.size > 1 }.keys.forEach { found += "the app id $it is listed twice" }
        games.filter { it.reason !in reasons }.forEach { found += "${it.name}: unknown reason ${it.reason}" }
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
        private val json = Json { ignoreUnknownKeys = false }

        fun parse(text: String): IncompatibleList = json.decodeFromString(serializer(), text)
    }
}
