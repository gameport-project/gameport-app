package app.gameport.feature.library

import app.gameport.core.model.AppKind
import app.gameport.core.model.DisplaySettings
import app.gameport.core.model.Game
import app.gameport.core.model.Library
import app.gameport.core.model.LibrarySort
import app.gameport.core.model.Ownership
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrangedTest {
    private val zelda = Game(1, "Zelda", Ownership.OWNED, null)
    private val arizona = Game(2, "Arizona", Ownership.OWNED, null)
    private val metro = Game(3, "Metro", Ownership.OWNED, null)
    private val content = Library(listOf(zelda, arizona, metro), false).toUiState("", LibraryTab.VR, showTabs = false)
    private val history = PlayHistory(
        lastPlayed = mapOf(1 to 100L, 3 to 300L),
        favorites = setOf(2),
        installedAt = mapOf(1 to 10L, 2 to 50L, 3 to 20L),
    )

    private fun ids(games: List<Game>) = games.map { it.appId }

    @Test
    fun `sorts by name by default`() = assertEquals(listOf(2, 3, 1), ids(content.arranged(DisplaySettings(), history).allGames))

    @Test
    fun `recently played puts the games started here first, then installed ones, then the others`() {
        // Zelda (1) and Metro (3) are installed here; Metro was started last. Arizona (2) is not installed here.
        val shown = content.arranged(DisplaySettings(sort = LibrarySort.RECENTLY_PLAYED), history.copy(installedAt = mapOf(1 to 10L, 3 to 20L, 4 to 5L)))
        assertEquals(listOf(3, 1, 2), ids(shown.allGames))
    }

    @Test
    fun `a game never started here does not rank above one that was, whatever Steam says`() {
        val onlyZeldaPlayed = history.copy(lastPlayed = mapOf(1 to 100L), installedAt = mapOf(1 to 10L, 2 to 50L, 3 to 20L))
        val shown = content.arranged(DisplaySettings(sort = LibrarySort.RECENTLY_PLAYED), onlyZeldaPlayed)
        assertEquals(listOf(1, 2, 3), ids(shown.allGames))
    }

    @Test
    fun `a game no longer installed here is not counted as played`() {
        val shown = content.arranged(DisplaySettings(sort = LibrarySort.RECENTLY_PLAYED), history.copy(installedAt = mapOf(1 to 10L)))
        assertEquals(listOf(1, 2, 3), ids(shown.allGames))
        assertEquals(listOf(1), ids(shown.continueGames))
    }

    @Test
    fun `sorts by install date, newest first, installed games before the others`() {
        val shown = content.arranged(DisplaySettings(sort = LibrarySort.RECENTLY_INSTALLED), history.copy(installedAt = mapOf(1 to 10L, 3 to 20L)))
        assertEquals(listOf(3, 1, 2), ids(shown.allGames))
    }

    @Test
    fun `continue row lists the games started, then the installed ones not started yet`() {
        val shown = content.arranged(DisplaySettings(), history)
        assertEquals(listOf(3, 1, 2), ids(shown.continueGames))
        assertEquals(metro, shown.lastPlayed)
    }

    @Test
    fun `continue row is filled with the installed games before any is started`() {
        val shown = content.arranged(DisplaySettings(), PlayHistory(installedAt = mapOf(1 to 10L, 3 to 30L)))
        assertEquals(listOf(3, 1), ids(shown.continueGames))
        assertNull(shown.lastPlayed)
    }

    @Test
    fun `favorites row lists the starred games`() = assertEquals(listOf(2), ids(content.arranged(DisplaySettings(), history).favoriteGames))

    @Test
    fun `rows can be switched off`() {
        val shown = content.arranged(DisplaySettings(showContinue = false, showFavorites = false), history)
        assertEquals(emptyList<Game>(), shown.continueGames)
        assertEquals(emptyList<Game>(), shown.favoriteGames)
    }

    @Test
    fun `games that are not installed can be hidden`() {
        val shown = content.arranged(DisplaySettings(hideUninstalled = true), history.copy(installedAt = mapOf(1 to 10L)))
        assertEquals(listOf(1), ids(shown.allGames))
    }

    @Test
    fun `a search shows only the matches, without the rows`() {
        val searched = Library(listOf(zelda, arizona, metro), false).toUiState("a", LibraryTab.VR, showTabs = false)
        val shown = searched.arranged(DisplaySettings(), history)
        assertEquals(emptyList<Game>(), shown.continueGames)
        assertEquals(emptyList<Game>(), shown.favoriteGames)
        assertEquals(listOf(2, 1), ids(shown.allGames))
    }

    @Test
    fun `continue row spans VR and flat games whatever tab is selected`() {
        val vr = Game(10, "Beat", Ownership.OWNED, app.gameport.core.model.AndroidBuild(packageName = null, isVr = true))
        val flat = Game(11, "Farm", Ownership.OWNED, app.gameport.core.model.AndroidBuild(packageName = null, isVr = false))
        val onVrTab = Library(listOf(vr, flat), false).toUiState("", LibraryTab.VR, showTabs = true)
        val shown = onVrTab.arranged(DisplaySettings(), PlayHistory(lastPlayed = mapOf(10 to 5L, 11 to 9L), installedAt = mapOf(10 to 1L, 11 to 2L)))
        assertEquals(listOf(11, 10), ids(shown.continueGames))
        assertEquals(listOf(10), ids(shown.allGames))
    }

    @Test
    fun `the filters narrow every row`() {
        val demo = Game(20, "Trial", Ownership.OWNED, null, AppKind.DEMO)
        val beta = Game(21, "Playtest", Ownership.FAMILY_SHARED, null, AppKind.BETA)
        val all = Library(listOf(zelda, demo, beta), false).toUiState("", LibraryTab.VR, showTabs = false)
        val placed = PlayHistory(installedAt = mapOf(1 to 1L, 20 to 2L), favorites = setOf(21))
        fun shown(filters: LibraryFilters) = ids(all.arranged(DisplaySettings(), placed, filters).allGames).sorted()

        assertEquals(listOf(1, 20, 21), shown(LibraryFilters()))
        assertEquals(listOf(20), shown(LibraryFilters(kinds = setOf(AppKind.DEMO))))
        assertEquals(listOf(20, 21), shown(LibraryFilters(kinds = setOf(AppKind.DEMO, AppKind.BETA))))
        assertEquals(listOf(1, 20), shown(LibraryFilters(install = InstallFilter.INSTALLED)))
        assertEquals(listOf(21), shown(LibraryFilters(install = InstallFilter.NOT_INSTALLED)))
        assertEquals(listOf(21), shown(LibraryFilters(owner = OwnerFilter.FAMILY)))
        assertEquals(listOf(21), shown(LibraryFilters(favoritesOnly = true)))
        assertEquals(emptyList<Int>(), shown(LibraryFilters(kinds = setOf(AppKind.GAME), owner = OwnerFilter.FAMILY)))
    }

    @Test
    fun `filters count the groups that narrow the list, and the last kind stays selected`() {
        assertEquals(0, LibraryFilters().activeCount)
        assertEquals(3, LibraryFilters(kinds = setOf(AppKind.GAME), install = InstallFilter.INSTALLED, favoritesOnly = true).activeCount)
        val onlyGames = LibraryFilters(kinds = setOf(AppKind.GAME))
        assertEquals(onlyGames, onlyGames.toggled(AppKind.GAME))
        assertEquals(setOf(AppKind.GAME, AppKind.DEMO), onlyGames.toggled(AppKind.DEMO).kinds)
    }
}
