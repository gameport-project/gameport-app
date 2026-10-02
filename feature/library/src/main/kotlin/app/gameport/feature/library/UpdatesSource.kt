package app.gameport.feature.library

import app.gameport.core.install.GameUpdatesRepository
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

    /** How GamePort stands with Steam. */
    fun observeConnection(): Flow<SteamConnection> = flowOf(SteamConnection.ONLINE)

    /** Asks Steam again. */
    suspend fun check()
}

internal class InstalledGamesUpdatesSource @Inject constructor(
    private val updates: GameUpdatesRepository,
    private val auth: app.gameport.core.steam.SteamAuthRepository,
) : UpdatesSource {
    override fun observe(): Flow<List<String>> = updates.updates.map { list -> list.map { it.name } }

    override fun observeConnection(): Flow<SteamConnection> = auth.connection

    override suspend fun check() = updates.check()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UpdatesModule {
    @Binds
    abstract fun bindUpdatesSource(impl: InstalledGamesUpdatesSource): UpdatesSource
}
