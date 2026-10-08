package app.gameport.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A text by language code ("en", "fr", and others to come). */
typealias Localized = Map<String, String>

/** The text in [language], else in English, else any: a language that is not translated yet never leaves a blank. */
fun Localized.pick(language: String): String = this[language] ?: this[Release.FALLBACK_LANGUAGE] ?: values.firstOrNull().orEmpty()

/** One line of the news window: a picture (see [Release.ICONS]) and a short text. */
@Serializable
data class WindowItem(val id: String, val icon: String, val text: Localized)

/** The games block of the news window: the games tested and confirmed, then what is covered without having been tried. */
@Serializable
data class WindowGames(val title: Localized, val testedLabel: Localized, val tested: List<String>, val others: Localized)

@Serializable
data class WindowNotes(val items: List<WindowItem> = emptyList(), val games: WindowGames? = null)

/** A picture of the release notes, a path from the root of the documents. */
@Serializable
data class NoteImage(val src: String, val alt: Localized, val width: Int = 360)

@Serializable
data class NoteItem(val id: String, val text: Localized, val image: NoteImage? = null)

@Serializable
data class NoteSection(val id: String, val title: Localized? = null, val items: List<NoteItem>)

/**
 * Everything one version tells, in one file (`releases/<version>.json` in the assets): the news window is built from [window] and the release
 * notes from [notes], so they cannot drift apart. [window] is short, [notes] is detailed, and they need not list the same things.
 */
@Serializable
data class Release(
    val version: String,
    val previous: String,
    val code: Int,
    /** Whether games patched by an earlier version have to be patched again. */
    val patchNeeded: Boolean = true,
    val window: WindowNotes = WindowNotes(),
    val notes: List<NoteSection> = emptyList(),
) {
    /** What is wrong with this file, one sentence each: empty when it is fine. Every text must exist in each of [languages]. */
    fun problems(languages: List<String>): List<String> {
        val found = mutableListOf<String>()
        val expected = version.split(".").takeIf { it.size == 3 && it.all { part -> part.toIntOrNull() != null } }
        if (expected == null || expected[0].toInt() * 10_000 + expected[1].toInt() * 100 + expected[2].toInt() != code) found += "$version: the code $code is not the version"
        val texts = mutableListOf<Pair<String, Localized>>()
        window.items.forEach { texts += "window ${it.id}" to it.text }
        window.games?.let { texts += "window games title" to it.title; texts += "window games label" to it.testedLabel; texts += "window games others" to it.others }
        notes.forEach { section ->
            section.title?.let { texts += "section ${section.id} title" to it }
            section.items.forEach { item ->
                texts += "note ${item.id}" to item.text
                item.image?.let { texts += "image ${item.id}" to it.alt }
            }
        }
        for ((where, text) in texts) {
            for (language in languages) {
                val value = text[language]
                if (value.isNullOrBlank()) found += "$version $where: no text in $language"
                else if (';' in value) found += "$version $where ($language): a semicolon"
            }
            (text.keys - languages.toSet()).forEach { found += "$version $where: unknown language $it" }
        }
        // The window speaks of "this device", never of a headset, and keeps to a few lines.
        window.items.forEach { item ->
            if (item.icon !in ICONS) found += "$version window ${item.id}: unknown icon ${item.icon}"
            item.text.forEach { (language, value) -> if (HEADSET.containsMatchIn(value)) found += "$version window ${item.id} ($language): speaks of a headset" }
        }
        val ids = window.items.map { it.id } + notes.map { it.id } + notes.flatMap { section -> section.items.map { it.id } }
        ids.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { found += "$version: the id $it is used twice" }
        return found
    }

    companion object {
        const val FALLBACK_LANGUAGE = "en"

        /** The pictures the news window knows. Adding one is a line in the window, once. */
        val ICONS = setOf("trophy", "cloud-sync", "key", "check", "download", "play", "warning", "build", "trash", "info")

        private val HEADSET = Regex("headset|casque", RegexOption.IGNORE_CASE)
        private val json = Json { ignoreUnknownKeys = false }

        fun parse(text: String): Release = json.decodeFromString(serializer(), text)
    }
}
