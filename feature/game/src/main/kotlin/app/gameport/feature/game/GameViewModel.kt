package app.gameport.feature.game

import app.gameport.core.model.reportable
import kotlinx.coroutines.flow.map
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.device.DeviceProfile
import app.gameport.core.model.GameIssue
import app.gameport.core.model.InstallError
import app.gameport.core.settings.ControllerMappingStore
import app.gameport.core.sync.GameIssuesRepository
import app.gameport.core.model.Game
import app.gameport.core.model.InstallState
import app.gameport.core.model.SpeedUnit
import app.gameport.core.settings.UserSettings
import app.gameport.core.steam.SteamLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface GameUiState {
    data object Loading : GameUiState

    data object NotFound : GameUiState

    data class Content(
        val game: Game,
        val install: InstallState = InstallState.NotInstalled,
        val issues: List<GameIssue> = emptyList(),
        val repatch: Repatch = Repatch.None,
        /** The game was seen using the Steam Frame's controllers: its controller page is offered. */
        val controllerProfile: Boolean = false,
        /** The player starred the game: it is listed in the library's favorites. */
        val favorite: Boolean = false,
        /** The player hid the game in GamePort (Steam is not concerned). */
        val hidden: Boolean = false,
        /** Time played on this device and, once Steam answered, on the whole account. */
        val playtime: app.gameport.core.model.Playtime = app.gameport.core.model.Playtime(),
        /** False on a device without VR: nothing that belongs to VR is shown. */
        val vrDevice: Boolean = true,
        /** The game has saves and the ones on this device and on Steam do not agree. */
        val savesNotSynced: Boolean = false,
        /** What the players say about the game: null while too few of them said anything. */
        val compat: app.gameport.core.model.Compat? = null,
        /** Why the game is known not to run, when it is on the list of incompatible games. */
        val incompatible: app.gameport.core.model.IncompatibleReason? = null,
    ) : GameUiState
}

/** Where a re-patch or an update of an installed game stands. */
enum class RepatchStage { QUEUED, DOWNLOADING, PATCHING, INSTALLING }

/** Patching or updating an installed game, shown in the game's attention panel. */
sealed interface Repatch {
    data object None : Repatch

    data class Running(val stage: RepatchStage, val fraction: Float? = null, val bytesPerSecond: Long = 0) : Repatch

    data class Failed(val error: InstallError) : Repatch
}

@HiltViewModel
class GameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: SteamLibraryRepository,
    private val installer: GameInstallRepository,
    private val issuesRepository: GameIssuesRepository,
    controllerMappings: ControllerMappingStore,
    device: DeviceProfile,
    settings: UserSettings,
    private val playHistory: app.gameport.core.settings.PlayHistoryStore,
    playtimeStore: app.gameport.core.settings.PlaytimeStore,
    private val playtimeTracker: app.gameport.core.sync.PlaytimeTracker,
    auth: app.gameport.core.steam.SteamAuthRepository,
    private val reports: app.gameport.core.sync.ReportStore,
    private val reporter: app.gameport.core.sync.ProblemReporter,
    achievementsRepository: app.gameport.core.steam.AchievementsRepository,
    saves: app.gameport.core.sync.SaveStateRepository,
    private val compat: app.gameport.core.sync.CompatRepository,
    incompatibleGames: app.gameport.core.settings.IncompatibleGames,
) : ViewModel() {
    init {
        // The totals of the players are asked again when they are old; what is kept shows meanwhile.
        viewModelScope.launch { compat.refreshIfStale() }
    }

    /** How GamePort stands with Steam. */
    val connection: StateFlow<app.gameport.core.model.SteamConnection> = auth.connection

    val speedUnit: StateFlow<SpeedUnit> = settings.speedUnit

    /** How much of the page's height the game's artwork covers, in percent (a look setting). */
    val artworkHeight: StateFlow<Int> = settings.display
        .map { it.gameArtworkHeight }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), app.gameport.core.model.DisplaySettings.DEFAULT_GAME_ARTWORK_HEIGHT)

    private val appId = savedStateHandle.toRoute<GameRoute>().appId

    private val baseState: StateFlow<GameUiState> = combine(
        repository.observeLibrary().toGameState(appId),
        installer.observe(appId),
        issuesRepository.observe(appId),
        controllerMappings.observe(appId),
        combine(playHistory.favorites, playHistory.hidden) { starred, hidden -> starred to hidden },
    ) { game, install, issues, controllers, (favorites, hidden) ->
        if (game !is GameUiState.Content) return@combine game
        // Patching or updating a game that is already installed is not a first install: the game
        // keeps its Play and uninstall buttons, and the panel shows the progress.
        val installedPackage = installer.installedPackage(appId)
        val repatch = when {
            installedPackage == null -> Repatch.None
            install is InstallState.Queued -> Repatch.Running(RepatchStage.QUEUED)
            install is InstallState.Downloading -> Repatch.Running(RepatchStage.DOWNLOADING, install.progress, install.bytesPerSecond)
            install is InstallState.Patching -> Repatch.Running(RepatchStage.PATCHING)
            install is InstallState.Installing || install is InstallState.Finishing -> Repatch.Running(RepatchStage.INSTALLING)
            install is InstallState.Failed -> Repatch.Failed(install.error)
            else -> Repatch.None
        }
        val shown = if (repatch != Repatch.None && installedPackage != null) InstallState.Installed(installedPackage) else install
        game.copy(install = shown, issues = issues, repatch = repatch, controllerProfile = device.isHeadset && controllers.detected.isNotEmpty(), favorite = appId in favorites, hidden = appId in hidden, vrDevice = device.isHeadset)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GameUiState.Loading)

    private val steamMinutes = MutableStateFlow<Int?>(null)

    /** The account's achievements for the game: what was kept at once, then what Steam says. Null while unknown. */
    val achievements: StateFlow<app.gameport.core.model.AchievementList?> = achievementsRepository.observe(appId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val savesNotSynced: Flow<Boolean> = saves.observe(appId).map { it.files.isNotEmpty() && !it.inSync }

    /** What is said about the game by the players, and by the list of incompatible games. */
    private class Opinion(val compat: app.gameport.core.model.Compat?, val incompatible: app.gameport.core.model.IncompatibleReason?)

    private val opinion: Flow<Opinion> = compat.observe(appId).map { community -> Opinion(community, incompatibleGames.list.reasonOf(appId)) }

    val uiState: StateFlow<GameUiState> = combine(
        baseState,
        combine(playtimeStore.observe(appId), steamMinutes, savesNotSynced) { deviceMillis, steam, notSynced -> Triple(deviceMillis, steam, notSynced) },
        opinion,
    ) { state, (deviceMillis, steam, notSynced), opinion ->
        if (state is GameUiState.Content) {
            state.copy(
                playtime = app.gameport.core.model.Playtime(deviceMillis, steam),
                savesNotSynced = notSynced,
                compat = opinion.compat,
                incompatible = opinion.incompatible,
            )
        } else state
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GameUiState.Loading)

    /** Asks Steam again for the account's total time in the game; the last answer stays when it cannot be reached. */
    fun refreshPlaytime() {
        viewModelScope.launch { playtimeTracker.steamMinutes(appId)?.let { steamMinutes.value = it } }
    }

    /** [dlc] is what the player chose; null resumes an interrupted install with its earlier choice. */
    fun onInstall(game: Game, dlc: Set<Int>?) = installer.install(game, dlc)

    fun onRepatch() = installer.repatch(appId)

    /** Installs the newer build Steam published, over the installed game. */
    fun onUpdate() {
        (uiState.value as? GameUiState.Content)?.game?.let(installer::update)
    }

    fun onCancel() = installer.cancel(appId)

    /** Whether this game looks like it had a problem (it closed at once, or crashed). */
    val suspicion: StateFlow<app.gameport.core.sync.Suspicion?> = reports.suspected
        .map { installer.installedPackage(appId)?.let(it::get) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _reportProgress = MutableStateFlow<ReportProgress>(ReportProgress.Idle)
    internal val reportProgress: StateFlow<ReportProgress> = _reportProgress

    internal fun onSaveReport(game: Game) {
        if (_reportProgress.value == ReportProgress.Working) return
        viewModelScope.launch {
            _reportProgress.value = ReportProgress.Working
            val failure = ((uiState.value as? GameUiState.Content)?.install as? InstallState.Failed)?.error?.takeIf { it.reportable }?.toString()
            val saved = reporter.save(game, failure)
            _reportProgress.value = if (saved != null) ReportProgress.Saved(saved.fileName, saved.uri) else ReportProgress.Failed
            // The report said why it was made; the notice has done its job.
            if (saved != null) onDismissProblem()
        }
    }

    internal fun ticketUri(game: Game): android.net.Uri =
        reporter.ticketUrl(game, (_reportProgress.value as? ReportProgress.Saved)?.fileName)

    fun onDismissProblem() {
        installer.installedPackage(appId)?.let(reports::dismiss)
    }

    internal fun onResetReport() {
        _reportProgress.value = ReportProgress.Idle
    }

    fun onVersionChosen(optionId: String?) = installer.chooseVersion(appId, optionId)

    fun onDuplicateChosen(replace: Boolean) = installer.chooseDuplicate(appId, replace)

    /** Patches the game again, then starts it ([then]) once it is up to date. A patch that fails or is cancelled does not start it. */
    fun onPatchAndPlay(then: () -> Unit) {
        viewModelScope.launch { if (installer.repatchAndAwait(appId)) then() }
    }

    fun onPause() = installer.pause(appId)

    fun onDiscard() = installer.discard(appId)

    fun onUninstall() = installer.uninstall(appId)

    fun onSetHidden(hide: Boolean) = playHistory.setHidden(appId, hide)

    fun onToggleFavorite() = playHistory.toggleFavorite(appId)

    /** Permissions are granted outside GamePort: check the game's issues again when the page is shown. */
    fun refreshIssues() = issuesRepository.refresh()

    fun conflictIntent() = issuesRepository.conflictIntent(appId)

    fun shouldExplainStoragePermission() = installer.shouldExplainStoragePermission(appId)

    fun markStoragePermissionExplained() = installer.markStoragePermissionExplained(appId)

    fun appSettingsIntent() = installer.appSettingsIntent(appId)

    /** Intent that starts the installed game, or null if it is gone. */
    fun launchIntent() = installer.launchIntent(appId, (uiState.value as? GameUiState.Content)?.game?.androidBuild?.isVr)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
