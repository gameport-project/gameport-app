package app.gameport.feature.library

import app.gameport.core.model.AppKind
import app.gameport.core.model.Game
import app.gameport.core.model.Ownership

enum class InstallFilter { ALL, INSTALLED, NOT_INSTALLED }

enum class OwnerFilter { ALL, MINE, FAMILY }

/**
 * What the player narrows the library down to. The default shows everything. Nothing here is kept after
 * the app closes: a filter left on by accident would leave games missing with no clue why.
 */
data class LibraryFilters(
    /** Games, demos, betas: the kinds shown. At least one stays selected. */
    val kinds: Set<AppKind> = AppKind.entries.toSet(),
    val install: InstallFilter = InstallFilter.ALL,
    val owner: OwnerFilter = OwnerFilter.ALL,
    val favoritesOnly: Boolean = false,
) {
    /** How many of the four groups are narrowing the list; shown on the filter button. */
    val activeCount: Int
        get() = listOf(kinds.size != AppKind.entries.size, install != InstallFilter.ALL, owner != OwnerFilter.ALL, favoritesOnly).count { it }

    val isDefault: Boolean get() = activeCount == 0

    fun accepts(game: Game, installedIds: Set<Int>, favoriteIds: Set<Int>): Boolean =
        game.kind in kinds &&
            when (install) {
                InstallFilter.ALL -> true
                InstallFilter.INSTALLED -> game.appId in installedIds
                InstallFilter.NOT_INSTALLED -> game.appId !in installedIds
            } &&
            when (owner) {
                OwnerFilter.ALL -> true
                OwnerFilter.MINE -> game.ownership == Ownership.OWNED
                OwnerFilter.FAMILY -> game.ownership == Ownership.FAMILY_SHARED
            } &&
            (!favoritesOnly || game.appId in favoriteIds)

    /** Selects or unselects [kind]; the last selected kind cannot be unselected. */
    fun toggled(kind: AppKind): LibraryFilters {
        val next = if (kind in kinds) kinds - kind else kinds + kind
        return if (next.isEmpty()) this else copy(kinds = next)
    }
}
