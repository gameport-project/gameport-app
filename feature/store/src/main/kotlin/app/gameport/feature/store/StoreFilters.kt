package app.gameport.feature.store

import app.gameport.core.model.AndroidApp
import app.gameport.core.model.AndroidCatalog
import app.gameport.core.model.CompatLevel
import app.gameport.core.model.StorePrice
import java.time.LocalDate

/** The quick filters over the list of Android games. */
enum class StoreFilter {
    VR,
    FLAT,

    /** A build is published: the game can be installed. */
    INSTALLABLE,

    /** An Android depot exists with nothing to install yet. */
    FUTURE,

    /** The Android version arrived this week. */
    NEW,

    /** The account has the game. */
    OWNED,

    /** The account does not have the game. */
    NOT_OWNED,

    /** The players say it works on this device, even only offline or for some of them. */
    COMPATIBLE,

    /** The players say it does not work on this device. */
    INCOMPATIBLE,
}

/** How the list is ordered: the newest Android versions first, by name, the best liked, the most reviewed, the latest release, the cheapest or dearest, or the biggest discount. */
enum class StoreSort { DEFAULT, NAME, RATING, POPULAR, RELEASE, PRICE, PRICE_DESC, DISCOUNT }

/** How the games are shown: a low list, two or three a row, or the covers. */
enum class StoreViewMode { LIST, COVERS }

/** Which of the three lists of languages of a game the language filter looks at. */
enum class LanguageKind { TEXT, VOICE, SUBTITLES }

/**
 * Everything the player narrowed the list down to besides the search: the quick filters, and the languages (in the text, the voices or the subtitles)
 * and the tags (a game passes with any one of those chosen).
 */
data class StoreFilters(
    val quick: Set<StoreFilter> = emptySet(),
    val languageKind: LanguageKind = LanguageKind.TEXT,
    val languages: Set<String> = emptySet(),
    val tags: Set<Int> = emptySet(),
) {
    val isEmpty: Boolean get() = quick.isEmpty() && languages.isEmpty() && tags.isEmpty()

    /** How many things are narrowing the list: each quick filter, the languages as one, the tags as one. */
    val activeCount: Int get() = quick.size + (if (languages.isEmpty()) 0 else 1) + (if (tags.isEmpty()) 0 else 1)

    /** After touching a quick filter: one touched again goes off; one that turns on takes off the one it excludes. */
    fun toggled(filter: StoreFilter): StoreFilters = copy(quick = if (filter in quick) quick - filter else (quick - filter.excludes()) + filter)

    fun withLanguage(code: String): StoreFilters = copy(languages = languages.toggled(code))

    fun withTag(tagId: Int): StoreFilters = copy(tags = tags.toggled(tagId))

    /** Written as one line, to be kept on the device. */
    fun encode(): String = listOf(quick.joinToString(",") { it.name }, languageKind.name, languages.sorted().joinToString(","), tags.sorted().joinToString(",")).joinToString("|")

    companion object {
        /** The filters written by [encode]; the default ones for anything else. */
        fun decode(text: String?): StoreFilters {
            val parts = text.orEmpty().split('|')
            if (parts.size != 4) return StoreFilters()
            fun <T> list(part: String, read: (String) -> T?) = part.split(',').filter { it.isNotBlank() }.mapNotNull(read).toSet()
            return StoreFilters(
                quick = list(parts[0]) { name -> StoreFilter.entries.firstOrNull { it.name == name } },
                languageKind = LanguageKind.entries.firstOrNull { it.name == parts[1] } ?: LanguageKind.TEXT,
                languages = list(parts[2]) { it },
                tags = list(parts[3]) { it.toIntOrNull() },
            )
        }
    }
}

private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

/** The filters that cannot hold at the same time as this one: a game is VR or flat, installable or not. */
private fun StoreFilter.excludes(): Set<StoreFilter> = when (this) {
    StoreFilter.VR -> setOf(StoreFilter.FLAT)
    StoreFilter.FLAT -> setOf(StoreFilter.VR)
    StoreFilter.INSTALLABLE -> setOf(StoreFilter.FUTURE)
    StoreFilter.FUTURE -> setOf(StoreFilter.INSTALLABLE)
    StoreFilter.OWNED -> setOf(StoreFilter.NOT_OWNED)
    StoreFilter.NOT_OWNED -> setOf(StoreFilter.OWNED)
    StoreFilter.COMPATIBLE -> setOf(StoreFilter.INCOMPATIBLE)
    StoreFilter.INCOMPATIBLE -> setOf(StoreFilter.COMPATIBLE)
    else -> emptySet()
}

/** The language codes of a game for the list [kind] names. */
internal fun AndroidApp.languagesOf(kind: LanguageKind): List<String> = when (kind) {
    LanguageKind.TEXT -> languages
    LanguageKind.VOICE -> audio
    LanguageKind.SUBTITLES -> subtitles
}

/**
 * The games to list: those that match the search (the name, the developer or the publisher) and every filter, the newest Android versions first, then
 * the best liked, then by name. On a device without VR no VR game is listed, as on the home.
 */
internal fun storeList(
    apps: List<AndroidApp>,
    text: String,
    filters: StoreFilters,
    ownedIds: Set<Int>,
    today: LocalDate,
    vrDevice: Boolean,
    prices: Map<Int, StorePrice> = emptyMap(),
    sort: StoreSort = StoreSort.DEFAULT,
    levels: Map<Int, CompatLevel> = emptyMap(),
): List<AndroidApp> {
    val needle = text.trim()
    val newIds = AndroidCatalog(apps = apps).arrivals(today).map { it.appid }.toSet()
    val ordered = apps
        .filter { vrDevice || !it.vr }
        .filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) || (it.developer + it.publisher).any { who -> who.contains(needle, ignoreCase = true) } }
        .filter { app ->
            filters.quick.all { filter ->
                when (filter) {
                    StoreFilter.VR -> app.vr
                    StoreFilter.FLAT -> !app.vr
                    StoreFilter.INSTALLABLE -> app.installable
                    StoreFilter.FUTURE -> !app.installable
                    StoreFilter.NEW -> app.appid in newIds
                    StoreFilter.OWNED -> app.appid in ownedIds
                    StoreFilter.NOT_OWNED -> app.appid !in ownedIds
                    StoreFilter.INCOMPATIBLE -> levels[app.appid] == CompatLevel.FAILS
                    StoreFilter.COMPATIBLE -> levels[app.appid].let { it == CompatLevel.WORKS || it == CompatLevel.OFFLINE_ONLY || it == CompatLevel.MIXED }
                }
            }
        }
        .filter { filters.languages.isEmpty() || app(it, filters).any { code -> code in filters.languages } }
        .filter { filters.tags.isEmpty() || it.tags.any { tag -> tag in filters.tags } }
        .sortedWith(
            compareByDescending<AndroidApp> { it.appid in newIds }
                .thenByDescending { it.arrival }
                .thenByDescending { it.reviewPercent ?: -1 }
                .thenBy { it.name.lowercase() },
        )
    // The sorts below keep the default order between games that tie, since the sort is stable.
    return when (sort) {
        StoreSort.DEFAULT -> ordered
        StoreSort.NAME -> ordered.sortedBy { it.name.lowercase() }
        StoreSort.RATING -> ordered.sortedWith(compareByDescending<AndroidApp> { it.reviewPercent ?: -1 }.thenByDescending { it.reviewCount ?: 0 })
        StoreSort.POPULAR -> ordered.sortedByDescending { it.reviewCount ?: 0 }
        StoreSort.RELEASE -> ordered.sortedByDescending { it.release.orEmpty() }
        StoreSort.PRICE_DESC -> ordered.sortedByDescending { prices[it.appid].cents() ?: -1L }
        // Free games first, then up; the games with no known price last.
        StoreSort.PRICE -> ordered.sortedBy { prices[it.appid].cents() ?: Long.MAX_VALUE }
        // The games on sale first, the biggest discount at the top.
        StoreSort.DISCOUNT -> ordered.sortedByDescending { prices[it.appid].discount() }
    }
}

private fun StorePrice?.cents(): Long? = when (this) {
    StorePrice.Free -> 0L
    is StorePrice.Paid -> finalCents
    null -> null
}

private fun StorePrice?.discount(): Int = (this as? StorePrice.Paid)?.discountPercent ?: 0

private fun app(app: AndroidApp, filters: StoreFilters) = app.languagesOf(filters.languageKind)

/** A language the filter offers, with the number of games that have it for the list chosen. */
data class LanguageOption(val code: String, val games: Int)

/** A tag the filter offers, with its name in the language of the device and the number of games that carry it. */
data class TagOption(val id: Int, val name: String, val games: Int)

/** The languages of the games for the list [kind] names, the most common first. */
internal fun languageOptions(apps: List<AndroidApp>, kind: LanguageKind): List<LanguageOption> =
    apps.flatMap { it.languagesOf(kind).distinct() }.groupingBy { it }.eachCount().map { LanguageOption(it.key, it.value) }.sortedWith(compareByDescending<LanguageOption> { it.games }.thenBy { it.code })

/** The tags of the games that have a name, the most common first. */
internal fun tagOptions(apps: List<AndroidApp>, nameOf: (Int) -> String?): List<TagOption> =
    apps.flatMap { it.tags.distinct() }.groupingBy { it }.eachCount()
        .mapNotNull { (id, games) -> nameOf(id)?.let { TagOption(id, it, games) } }
        .sortedWith(compareByDescending<TagOption> { it.games }.thenBy { it.name.lowercase() })
