package app.gameport.core.model

import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A Steam app that has an Android build, as the catalog repository publishes it (see its README). Every field but the ids has a default, so a file
 * from a later version that drops or adds one is still read.
 */
@Serializable
data class AndroidApp(
    val appid: Int,
    val name: String,
    /** `game`, `demo` or `beta`. */
    val type: String = "game",
    val parent: Int? = null,
    val vr: Boolean = false,
    /** `only`, `supported` or `none`. */
    val vrMode: String = "none",
    /** `ready`: a build is published; `unreleased`: an Android depot exists but nothing can be installed yet. */
    val android: String = "unreleased",
    val installable: Boolean = false,
    /** The day the catalog first saw the build installable, or null when that was before it kept track. */
    val androidSince: String? = null,
    val runtime: String? = null,
    val frame: String? = null,
    val deck: String? = null,
    val release: String? = null,
    val developer: List<String> = emptyList(),
    val publisher: List<String> = emptyList(),
    val reviewPercent: Int? = null,
    val reviewCount: Int? = null,
    val metacritic: Int? = null,
    val modes: List<String> = emptyList(),
    val controller: String? = null,
    val languages: List<String> = emptyList(),
    val audio: List<String> = emptyList(),
    val subtitles: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val tags: List<Int> = emptyList(),
    val store: String? = null,
    val cover: String? = null,
    val banner: String? = null,
    val header: String? = null,
    val capsule: String? = null,
    val installBytes: Long = 0,
    val downloadBytes: Long = 0,
) {
    /**
     * The picture to show for this entry: the slots in [order] first, then the ones left, then Steam's standard names for the number, so that an
     * entry with no picture of its own still has something to try (see [Art]).
     */
    fun art(vararg order: ArtSlot): Art {
        val slots = mapOf(ArtSlot.COVER to cover, ArtSlot.BANNER to banner, ArtSlot.HEADER to header, ArtSlot.CAPSULE to capsule)
        val chosen = (order.toList() + ArtSlot.entries).distinct().mapNotNull { slots[it] }
        return Art.of(chosen + Covers.capsule(appid).candidates)
    }

    /** The day [androidSince] names, when it is a date. */
    val arrival: LocalDate? get() = androidSince?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** The store says it holds nudity or sexual content: one of the tags that tell it is among the ones the catalog keeps for the app. */
    val isAdult: Boolean get() = tags.any { it in ADULT_TAGS }

    companion object {
        /** The store tags of adult content: Nudity, Sexual Content, NSFW and Hentai. */
        val ADULT_TAGS = setOf(6650, 12095, 24904, 9130)
    }
}

@Serializable
data class AndroidCatalog(
    val v: Int = 1,
    val count: Int = 0,
    val apps: List<AndroidApp> = emptyList(),
) {
    fun of(appId: Int): AndroidApp? = apps.firstOrNull { it.appid == appId }

    /**
     * The apps whose Android build arrived in the last [days] days up to [today], the newest first. The window slides with the day: what a date
     * of arrival tells is when the catalog first saw a published build, not when the game came out.
     */
    fun arrivals(today: LocalDate, days: Long = 7, vr: Boolean? = null): List<AndroidApp> {
        val from = today.minusDays(days)
        return apps
            .filter { it.installable && (vr == null || it.vr == vr) }
            .filter { app -> app.arrival?.let { !it.isBefore(from) && !it.isAfter(today) } == true }
            .sortedWith(compareByDescending<AndroidApp> { it.arrival }.thenBy { it.name.lowercase() })
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The file, or null when it cannot be read or is not a catalog this version understands. */
        fun parse(text: String?): AndroidCatalog? =
            runCatching { json.decodeFromString(serializer(), text.orEmpty()) }.getOrNull()?.takeIf { it.v == 1 && it.apps.isNotEmpty() }
    }
}
