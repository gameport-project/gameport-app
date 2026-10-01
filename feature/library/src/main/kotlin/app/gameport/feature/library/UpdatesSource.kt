package app.gameport.feature.library

import app.gameport.core.install.GameUpdatesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The installed games for which Steam published a newer build, as their names. */
interface UpdatesSource {
    fun observe(): Flow<List<String>>

    /** Asks Steam again. */
    suspend fun check()
}

internal class InstalledGamesUpdatesSource @Inject constructor(
    private val updates: GameUpdatesRepository,
) : UpdatesSource {
    override fun observe(): Flow<List<String>> = updates.updates.map { list -> list.map { it.name } }

    override suspend fun check() = updates.check()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UpdatesModule {
    @Binds
    abstract fun bindUpdatesSource(impl: InstalledGamesUpdatesSource): UpdatesSource
}
