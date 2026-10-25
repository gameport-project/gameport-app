package app.gameport.feature.library

import app.gameport.core.install.AppUpdater
import kotlinx.coroutines.flow.distinctUntilChanged
import app.gameport.core.model.inProgress
import app.gameport.core.install.GameUpdatesRepository
import app.gameport.core.model.AppUpdateState
import app.gameport.core.model.PatchAllInfo
import app.gameport.core.model.SteamConnection
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The installed games for which Steam published a newer build, as their names. */
interface UpdatesSource {
    fun observe(): Flow<List<String>>

    /** The ids of those games, to mark their covers. */
    fun observeIds(): Flow<Set<Int>> = flowOf(emptySet())

    /** The ids of the games whose update is going on: their cover shows a spinner instead of the mark. */
    fun observeUpdating(): Flow<Set<Int>> = flowOf(emptySet())

    /** The version of GamePort itself that can be installed, if a newer one exists. */
    fun observeApp(): Flow<String?> = flowOf(null)

    /** How GamePort stands with Steam. */
    fun observeConnection(): Flow<SteamConnection> = flowOf(SteamConnection.ONLINE)

    /** How many installed games were patched by an older patcher, and where "patch all" stands. */
    fun observePatchAll(): Flow<PatchAllInfo> = flowOf(PatchAllInfo(0, null))

    /** How many games are being installed right now. */
    fun observeInstalling(): Flow<Int> = flowOf(0)

    /** Patches the games that are behind. */
    fun patchAll() {}

    /** Stops the run that is going on. */
    fun stopPatchAll() {}

    /** Closes the report of a run that ended. */
    fun clearPatchAll() {}

    /** Asks Steam again. */
    suspend fun check()
}

internal class InstalledGamesUpdatesSource @Inject constructor(
    private val updates: GameUpdatesRepository,
    private val app: AppUpdater,
    private val auth: app.gameport.core.steam.SteamAuthRepository,
    private val patching: app.gameport.core.sync.PatchAllCoordinator,
    private val installer: app.gameport.core.install.GameInstallRepository,
) : UpdatesSource {
    // The notice at the top of the home is for the updates to start: a game whose update is going on is not in it.
    override fun observe(): Flow<List<String>> = kotlinx.coroutines.flow.combine(updates.updates, installer.observeUpdating()) { list, running -> list.filter { it.appId !in running }.map { it.name } }

    override fun observeApp(): Flow<String?> = app.state.map { state ->
        (state as? AppUpdateState.Available)?.release?.version?.takeIf { app.canUpdateInPlace }
    }

    // An update that is going on is not offered again.
    override fun observeIds(): Flow<Set<Int>> = kotlinx.coroutines.flow.combine(updates.updates, installer.observeUpdating()) { list, running -> list.map { it.appId }.toSet() - running }

    override fun observeUpdating(): Flow<Set<Int>> = installer.observeUpdating()

    override fun observeConnection(): Flow<SteamConnection> = auth.connection

    override fun observePatchAll(): Flow<PatchAllInfo> = patching.observe()

    override fun observeInstalling(): Flow<Int> = installer.observeAll().map { states -> states.values.count { it.inProgress } }.distinctUntilChanged()

    override fun patchAll() = patching.patchAll()

    override fun stopPatchAll() = patching.stop()

    override fun clearPatchAll() = patching.clear()

    override suspend fun check() = updates.check()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UpdatesModule {
    @Binds
    abstract fun bindUpdatesSource(impl: InstalledGamesUpdatesSource): UpdatesSource
}
