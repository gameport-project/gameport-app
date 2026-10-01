package app.gameport.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.install.GameUpdatesRepository
import app.gameport.core.model.Game
import app.gameport.core.model.InstallState
import app.gameport.core.model.SpeedUnit
import app.gameport.core.settings.UserSettings
import app.gameport.core.steam.SteamLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One line of the page: a game and where its install stands. [game] is null if the library lacks it. */
data class DownloadEntry(val appId: Int, val game: Game?, val state: InstallState)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    library: SteamLibraryRepository,
    private val installer: GameInstallRepository,
    private val updatesRepository: GameUpdatesRepository,
    settings: UserSettings,
) : ViewModel() {
    val speedUnit: StateFlow<SpeedUnit> = settings.speedUnit

    val entries: StateFlow<List<DownloadEntry>> = combine(library.observeLibrary(), installer.observeAll()) { lib, states ->
        val games = lib.games.associateBy { it.appId }
        states.map { (appId, state) -> DownloadEntry(appId, games[appId], state) }.sortedWith(ORDER)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Installed games Steam has a newer build for. */
    val updatable: StateFlow<Set<Int>> = updatesRepository.updates
        .map { list -> list.map { it.appId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

    init {
        // The page that lists updates asks Steam again when it opens.
        viewModelScope.launch { runCatching { updatesRepository.check() } }
    }

    fun onUpdate(entry: DownloadEntry) {
        entry.game?.let(installer::update)
    }

    fun onCancel(appId: Int) = installer.cancel(appId)

    fun onDiscard(appId: Int) = installer.discard(appId)

    fun onUninstall(appId: Int) = installer.uninstall(appId)

    fun onResume(entry: DownloadEntry) {
        entry.game?.let { installer.install(it, null) }
    }

    fun launchIntent(entry: DownloadEntry) = installer.launchIntent(entry.appId, entry.game?.androidBuild?.isVr)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** Work in progress first, then what needs attention, then what is installed. */
        val ORDER = compareBy<DownloadEntry> {
            when (it.state) {
                is InstallState.Downloading, InstallState.Patching, InstallState.Installing, InstallState.Queued -> 0
                is InstallState.Interrupted, is InstallState.Failed -> 1
                else -> 2
            }
        }.thenBy { it.game?.name?.lowercase() ?: "~" }
    }
}
