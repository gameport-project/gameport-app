package app.gameport.feature.sync

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gameport.core.sync.CloudSyncCoordinator
import app.gameport.core.sync.PendingConflict
import app.gameport.core.sync.PlayDecisions
import app.gameport.core.sync.Side
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SyncViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val coordinator: CloudSyncCoordinator,
    private val decisions: PlayDecisions,
) : ViewModel() {
    /** The game that opened the screen (its package name, passed as an extra). */
    private val packageName: String = savedStateHandle["pkg"] ?: ""

    /** The open question for that game; null when there is none (answered, or the game gave up). */
    val conflict: StateFlow<PendingConflict?> = coordinator.conflicts
        .map { it[packageName]?.takeIf { pending -> pending.choice == null && !pending.deferred } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The question put when another device plays with the account; null when there is none (answered, or the game gave up). */
    val playChoice: StateFlow<PlayDecisions.Pending?> = decisions.pending
        .map { it[packageName]?.takeIf { pending -> pending.choice == null } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onQuit() = decisions.resolve(packageName, PlayDecisions.Choice.QUIT)

    fun onKick() = decisions.resolve(packageName, PlayDecisions.Choice.KICK)

    fun onPlayAnyway() = decisions.resolve(packageName, PlayDecisions.Choice.PLAY)

    fun onKeepLocal() = coordinator.resolve(packageName, Side.LOCAL)

    fun onUseCloud() = coordinator.resolve(packageName, Side.CLOUD)

    fun onDecideLater() = coordinator.defer(packageName)
}
