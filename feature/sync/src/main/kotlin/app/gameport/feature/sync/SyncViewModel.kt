package app.gameport.feature.sync

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gameport.core.sync.CloudSyncCoordinator
import app.gameport.core.sync.PendingConflict
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
) : ViewModel() {
    /** The game that opened the screen (its package name, passed as an extra). */
    private val packageName: String = savedStateHandle["pkg"] ?: ""

    /** The open question for that game; null when there is none (answered, or the game gave up). */
    val conflict: StateFlow<PendingConflict?> = coordinator.conflicts
        .map { it[packageName]?.takeIf { pending -> pending.choice == null && !pending.deferred } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onKeepLocal() = coordinator.resolve(packageName, Side.LOCAL)

    fun onUseCloud() = coordinator.resolve(packageName, Side.CLOUD)

    fun onDecideLater() = coordinator.defer(packageName)
}
