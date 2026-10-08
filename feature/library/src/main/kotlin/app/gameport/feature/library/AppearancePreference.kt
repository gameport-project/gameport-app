package app.gameport.feature.library

import android.content.Intent
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.install.InstalledGames
import app.gameport.core.install.PackageGateway
import app.gameport.core.model.DisplaySettings
import app.gameport.core.model.Game
import app.gameport.core.model.LibrarySort
import app.gameport.core.settings.PlayHistoryStore
import app.gameport.core.settings.UserSettings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** What the library shows besides the covers themselves, as the player set it. */
fun interface AppearancePreference {
    fun observe(): Flow<DisplaySettings>
}

/** What the player did with the games: when each was started, which are starred, and which are installed (and since when). */
data class PlayHistory(
    val lastPlayed: Map<Int, Long> = emptyMap(),
    val favorites: Set<Int> = emptySet(),
    val installedAt: Map<Int, Long> = emptyMap(),
    /** Hidden in GamePort's library (not on Steam). */
    val hidden: Set<Int> = emptySet(),
    /** The games the players who use this kind of device say work. */
    val works: Set<Int> = emptySet(),
)

fun interface HistorySource {
    fun observe(): Flow<PlayHistory>
}

/** What the library's buttons do. */
interface LibraryActions {
    fun setSort(sort: LibrarySort)

    /** The intent that starts [game] now, or null when it is not installed or its page has something to explain first (a missing permission). */
    fun playIntent(game: Game): Intent?

    fun hide(appId: Int)

    fun toggleFavorite(appId: Int)

    fun update(game: Game)

    fun repatch(appId: Int)
}

internal class UserAppearancePreference @Inject constructor(
    private val settings: UserSettings,
) : AppearancePreference {
    override fun observe(): Flow<DisplaySettings> = settings.display
}

internal class StoredHistorySource @Inject constructor(
    private val history: PlayHistoryStore,
    private val installed: InstalledGames,
    private val packages: PackageGateway,
    private val incompatible: app.gameport.core.settings.IncompatibleGames,
    private val compat: app.gameport.core.sync.CompatRepository,
) : HistorySource {
    override fun observe(): Flow<PlayHistory> {
        val installedAt = packages.packageChanges()
            .onStart { emit(Unit) }
            .map { installed.all().mapNotNull { (appId, packageName) -> packages.installTimeOf(packageName)?.let { appId to it } }.toMap() }
            .flowOn(Dispatchers.IO)
        // A game confirmed as incompatible is hidden like one the player hid, unless they chose to show those anyway.
        return combine(history.lastPlayed, history.favorites, installedAt, history.hidden, combine(incompatible.showAnyway, compat.observeWorks()) { show, works -> show to works }) { played, starred, at, hidden, (showAnyway, works) ->
            PlayHistory(played, starred, at, hidden + incompatible.hiddenIds(showAnyway), works)
        }
    }
}

internal class InstallerLibraryActions @Inject constructor(
    private val settings: UserSettings,
    private val installer: GameInstallRepository,
    private val history: PlayHistoryStore,
) : LibraryActions {
    override fun hide(appId: Int) = history.setHidden(appId, true)

    override fun toggleFavorite(appId: Int) = history.toggleFavorite(appId)

    override fun update(game: Game) = installer.update(game)

    override fun repatch(appId: Int) = installer.repatch(appId)

    override fun setSort(sort: LibrarySort) = settings.updateDisplay { it.copy(sort = sort) }

    override fun playIntent(game: Game): Intent? =
        if (installer.shouldExplainStoragePermission(game.appId)) null else installer.launchIntent(game.appId, game.androidBuild?.isVr)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppearanceModule {
    @Binds
    abstract fun bindAppearancePreference(impl: UserAppearancePreference): AppearancePreference

    @Binds
    abstract fun bindHistorySource(impl: StoredHistorySource): HistorySource

    @Binds
    abstract fun bindLibraryActions(impl: InstallerLibraryActions): LibraryActions
}
