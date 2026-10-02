package app.gameport.core.install

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Keeps an update of GamePort and the work on games from overlapping, in both directions: GamePort is replaced
 * when it updates, so nothing must be running that would be cut off, and nothing new must start meanwhile.
 */
@Singleton
class UpdateGate @Inject constructor() {
    /** Why GamePort cannot update itself right now. */
    enum class Blocker { GAMES_BUSY, SYNCING }

    private val _updating = MutableStateFlow(false)

    /** GamePort is downloading or installing its own update. */
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    private val gamesBusy = MutableStateFlow(false)
    private val syncing = MutableStateFlow(false)

    /** The reason an update cannot start now, or null when it can. */
    val blocker: Flow<Blocker?> = combine(gamesBusy, syncing) { busy, sync -> blockerOf(busy, sync) }

    fun currentBlocker(): Blocker? = blockerOf(gamesBusy.value, syncing.value)

    fun setUpdating(value: Boolean) { _updating.value = value }

    /** A game is being downloaded, patched or installed. */
    fun setGamesBusy(value: Boolean) { gamesBusy.value = value }

    /** A game's saves are being synced with Steam. */
    fun setSyncing(value: Boolean) { syncing.value = value }

    internal companion object {
        fun blockerOf(gamesBusy: Boolean, syncing: Boolean): Blocker? = when {
            gamesBusy -> Blocker.GAMES_BUSY
            syncing -> Blocker.SYNCING
            else -> null
        }
    }
}
