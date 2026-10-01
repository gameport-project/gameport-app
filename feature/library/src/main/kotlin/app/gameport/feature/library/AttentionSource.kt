package app.gameport.feature.library

import app.gameport.core.sync.GameIssuesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The games that need the player's attention (see GameIssuesRepository). */
fun interface AttentionSource {
    fun observe(): Flow<Set<Int>>
}

internal class InstallAttentionSource @Inject constructor(
    private val issues: GameIssuesRepository,
) : AttentionSource {
    override fun observe(): Flow<Set<Int>> = issues.observeAttention()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AttentionModule {
    @Binds
    abstract fun bindAttentionSource(impl: InstallAttentionSource): AttentionSource
}
