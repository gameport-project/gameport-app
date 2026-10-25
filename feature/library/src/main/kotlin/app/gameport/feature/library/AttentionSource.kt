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

    /** Among them, the games with a patch to apply: they get a wrench, the others an exclamation mark. */
    fun observePatches(): Flow<Set<Int>> = kotlinx.coroutines.flow.flowOf(emptySet())

    /** The games that have a question for the player: they get a blue question mark until it is answered. */
    fun observeQuestions(): Flow<Set<Int>> = kotlinx.coroutines.flow.flowOf(emptySet())
}

internal class InstallAttentionSource @Inject constructor(
    private val issues: GameIssuesRepository,
) : AttentionSource {
    override fun observe(): Flow<Set<Int>> = issues.observeAttention()

    override fun observePatches(): Flow<Set<Int>> = issues.observePatchesToApply()

    override fun observeQuestions(): Flow<Set<Int>> = issues.observeQuestions()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AttentionModule {
    @Binds
    abstract fun bindAttentionSource(impl: InstallAttentionSource): AttentionSource
}
