package app.gameport.feature.store

import app.gameport.core.model.AndroidApp
import app.gameport.core.model.CompatLevel
import app.gameport.core.model.StorePrice
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StoreFiltersTest {
    private val today = LocalDate.of(2026, 10, 10)

    private fun app(
        id: Int,
        name: String = "Game $id",
        vr: Boolean = true,
        installable: Boolean = true,
        since: String? = null,
        review: Int? = null,
        developer: String? = null,
        languages: List<String> = emptyList(),
        audio: List<String> = emptyList(),
        subtitles: List<String> = emptyList(),
        tags: List<Int> = emptyList(),
    ) = AndroidApp(
        appid = id, name = name, vr = vr, installable = installable, android = if (installable) "ready" else "unreleased", androidSince = since,
        reviewPercent = review, developer = listOfNotNull(developer), languages = languages, audio = audio, subtitles = subtitles, tags = tags,
    )

    private val apps = listOf(
        app(1, "Alpha", vr = true, since = "2026-10-09", languages = listOf("en", "fr"), audio = listOf("en"), tags = listOf(19, 21)),
        app(2, "Bravo", vr = false, review = 90, languages = listOf("en"), subtitles = listOf("fr"), tags = listOf(19)),
        app(3, "Charlie", vr = true, installable = false, languages = listOf("fr", "de"), audio = listOf("fr"), tags = listOf(21)),
        app(4, "Delta", vr = false, review = 60, developer = "Polyarc", languages = listOf("de")),
        app(5, "Echo", vr = true, review = 95, languages = listOf("en", "fr", "de"), audio = listOf("fr", "de"), tags = listOf(1664)),
    )

    private fun ids(filters: StoreFilters = StoreFilters(), text: String = "", owned: Set<Int> = emptySet(), vrDevice: Boolean = true) =
        storeList(apps, text, filters, owned, today, vrDevice).map { it.appid }

    private fun quick(vararg filters: StoreFilter) = StoreFilters(quick = filters.toSet())

    @Test
    fun `the newest come first, then the best liked, then by name`() {
        assertEquals(listOf(1, 5, 2, 4, 3), ids())
    }

    @Test
    fun `every quick filter narrows the list`() {
        assertEquals(listOf(1, 5, 3), ids(quick(StoreFilter.VR)))
        assertEquals(listOf(2, 4), ids(quick(StoreFilter.FLAT)))
        assertEquals(listOf(3), ids(quick(StoreFilter.FUTURE)))
        assertEquals(listOf(1, 5, 2, 4), ids(quick(StoreFilter.INSTALLABLE)))
        assertEquals(listOf(1), ids(quick(StoreFilter.NEW)))
        assertEquals(listOf(5, 2), ids(quick(StoreFilter.OWNED), owned = setOf(2, 5)))
        assertEquals(listOf(1), ids(quick(StoreFilter.VR, StoreFilter.NEW)))
    }

    @Test
    fun `the search looks at the name, the developer and the publisher`() {
        assertEquals(listOf(4), ids(text = "poly"))
        assertEquals(listOf(2), ids(text = " BRAV "))
        assertEquals(emptyList<Int>(), ids(text = "nothing"))
    }

    @Test
    fun `a device without VR lists no VR game`() {
        assertEquals(listOf(2, 4), ids(vrDevice = false))
    }

    @Test
    fun `the language filter looks at the text, the voices or the subtitles, and a game passes with any language chosen`() {
        fun found(kind: LanguageKind, vararg languages: String) = ids(StoreFilters(languageKind = kind, languages = languages.toSet())).toSet()
        assertEquals(setOf(1, 3, 5), found(LanguageKind.TEXT, "fr"))
        assertEquals(setOf(1, 5), found(LanguageKind.VOICE, "en", "de"))
        assertEquals(setOf(3, 5), found(LanguageKind.VOICE, "fr"))
        assertEquals(setOf(2), found(LanguageKind.SUBTITLES, "fr"))
        assertEquals(setOf(1, 2, 3, 4, 5), found(LanguageKind.TEXT, "de", "en", "fr"))
    }

    @Test
    fun `the tag filter keeps the games that carry any tag chosen`() {
        assertEquals(setOf(1, 2, 3), ids(StoreFilters(tags = setOf(19, 21))).toSet())
        assertEquals(setOf(5), ids(StoreFilters(tags = setOf(1664))).toSet())
    }

    @Test
    fun `filters of every kind narrow together`() {
        assertEquals(listOf(1), ids(StoreFilters(quick = setOf(StoreFilter.VR), languages = setOf("fr"), tags = setOf(19))))
    }

    @Test
    fun `a quick filter touched again goes off, and one that turns on takes off the one it excludes`() {
        val vr = StoreFilters().toggled(StoreFilter.VR)
        assertEquals(setOf(StoreFilter.VR), vr.quick)
        assertEquals(setOf(StoreFilter.FLAT), vr.toggled(StoreFilter.FLAT).quick)
        assertEquals(emptySet<StoreFilter>(), vr.toggled(StoreFilter.VR).quick)
        val both = vr.toggled(StoreFilter.NEW).toggled(StoreFilter.INSTALLABLE)
        assertEquals(setOf(StoreFilter.VR, StoreFilter.NEW, StoreFilter.INSTALLABLE), both.quick)
        assertEquals(setOf(StoreFilter.VR, StoreFilter.NEW, StoreFilter.FUTURE), both.toggled(StoreFilter.FUTURE).quick)
    }

    @Test
    fun `what narrows the list is counted, the languages as one and the tags as one`() {
        assertEquals(0, StoreFilters().activeCount)
        assertEquals(4, StoreFilters(quick = setOf(StoreFilter.VR, StoreFilter.NEW), languages = setOf("fr", "en"), tags = setOf(1, 2, 3)).activeCount)
    }

    @Test
    fun `the filters kept are read back as they were written`() {
        val filters = StoreFilters(setOf(StoreFilter.VR, StoreFilter.INSTALLABLE), LanguageKind.VOICE, setOf("fr", "pt-BR"), setOf(19, 21978))
        assertEquals(filters, StoreFilters.decode(filters.encode()))
        assertEquals(StoreFilters(), StoreFilters.decode(StoreFilters().encode()))
    }

    @Test
    fun `anything that is not filters written by this version is the default`() {
        assertEquals(StoreFilters(), StoreFilters.decode(null))
        assertEquals(StoreFilters(), StoreFilters.decode("garbage"))
        assertEquals(StoreFilters(quick = setOf(StoreFilter.VR)), StoreFilters.decode("VR,UNKNOWN|NOPE||x,"))
    }

    @Test
    fun `the options are the languages and the tags of the games, the most common first`() {
        assertEquals(listOf("de", "en", "fr"), languageOptions(apps, LanguageKind.TEXT).map { it.code })
        assertEquals(listOf("fr", "de", "en"), languageOptions(apps, LanguageKind.VOICE).map { it.code })
        val tags = tagOptions(apps) { id -> mapOf(19 to "Action", 21 to "Aventure")[id] }
        assertEquals(listOf("Action", "Aventure"), tags.map { it.name })
        assertEquals(listOf(2, 2), tags.map { it.games })
    }

    @Test
    fun `the list opens with the games that can be installed, which are not all of them`() {
        val defaults = StoreFilters(quick = setOf(StoreFilter.INSTALLABLE))
        assertEquals(listOf(1, 5, 2, 4), ids(defaults))
        assertEquals(listOf(1, 5, 2, 4, 3), ids(StoreFilters()))
    }

    @Test
    fun `sorted by price the free games come first, then the cheapest, and the games with no known price last`() {
        val prices = mapOf(
            1 to StorePrice.Paid("EUR", 2000, 2000, 0),
            2 to StorePrice.Free,
            3 to StorePrice.Paid("EUR", 500, 500, 0),
            4 to StorePrice.Paid("EUR", 1000, 1000, 0),
        )
        val order = storeList(apps, "", StoreFilters(), emptySet(), today, true, prices, StoreSort.PRICE).map { it.appid }
        assertEquals(listOf(2, 3, 4, 1), order.take(4))
        assertEquals(5, order.last())
    }

    @Test
    fun `sorted by discount the biggest comes first and the games not on sale keep their order after`() {
        val prices = mapOf(
            1 to StorePrice.Paid("EUR", 1000, 2000, 50),
            3 to StorePrice.Paid("EUR", 400, 2000, 80),
            4 to StorePrice.Paid("EUR", 1000, 1000, 0),
        )
        val plain = storeList(apps, "", StoreFilters(), emptySet(), today, true).map { it.appid }
        val order = storeList(apps, "", StoreFilters(), emptySet(), today, true, prices, StoreSort.DISCOUNT).map { it.appid }
        assertEquals(listOf(3, 1), order.take(2))
        assertEquals(plain.filter { it != 3 && it != 1 }, order.drop(2))
    }

    @Test
    fun `not owned lists the games the account does not have, and excludes owned`() {
        assertEquals(listOf(1, 5, 2, 4, 3).filter { it !in setOf(2, 5) }, ids(quick(StoreFilter.NOT_OWNED), owned = setOf(2, 5)))
        val both = StoreFilters(quick = setOf(StoreFilter.OWNED)).toggled(StoreFilter.NOT_OWNED)
        assertEquals(setOf(StoreFilter.NOT_OWNED), both.quick)
    }

    @Test
    fun `compatible keeps the games that work, even offline or for some, and drops the ones that fail or have no word`() {
        val levels = mapOf(1 to CompatLevel.WORKS, 2 to CompatLevel.OFFLINE_ONLY, 3 to CompatLevel.MIXED, 4 to CompatLevel.FAILS)
        val listed = storeList(apps, "", quick(StoreFilter.COMPATIBLE), emptySet(), today, true, levels = levels).map { it.appid }
        assertEquals(setOf(1, 2, 3), listed.toSet())
    }

    @Test
    fun `incompatible keeps only the games the players say fail, and all lists the ones without a word too`() {
        val levels = mapOf(1 to CompatLevel.WORKS, 4 to CompatLevel.FAILS)
        assertEquals(listOf(4), storeList(apps, "", quick(StoreFilter.INCOMPATIBLE), emptySet(), today, true, levels = levels).map { it.appid })
        assertEquals(5, storeList(apps, "", StoreFilters(), emptySet(), today, true, levels = levels).size)
        assertEquals(setOf(StoreFilter.INCOMPATIBLE), StoreFilters(quick = setOf(StoreFilter.COMPATIBLE)).toggled(StoreFilter.INCOMPATIBLE).quick)
    }

    @Test
    fun `sorted by name, by rating and by price descending`() {
        val prices = mapOf(1 to StorePrice.Paid("EUR", 500, 500, 0), 2 to StorePrice.Free, 4 to StorePrice.Paid("EUR", 900, 900, 0))
        fun order(sort: StoreSort) = storeList(apps, "", StoreFilters(), emptySet(), today, true, prices, sort).map { it.appid }
        assertEquals(listOf(1, 2, 3, 4, 5), order(StoreSort.NAME))
        assertEquals(listOf(5, 2, 4), order(StoreSort.RATING).take(3))
        assertEquals(listOf(4, 1, 2), order(StoreSort.PRICE_DESC).take(3))
    }
}
