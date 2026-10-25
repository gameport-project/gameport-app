package app.gameport.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gameport.core.device.DeviceProfile
import app.gameport.core.install.AppUpdater
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.model.AppUpdateState
import app.gameport.core.model.AppLanguage
import app.gameport.core.model.AuthState
import app.gameport.core.model.DisplaySettings
import app.gameport.core.model.SpeedUnit
import app.gameport.core.model.PlayerDefaults
import app.gameport.core.model.Game
import app.gameport.core.settings.GameSettingsStore
import app.gameport.core.settings.PlayHistoryStore
import app.gameport.core.steam.SteamLibraryRepository
import kotlinx.coroutines.flow.combine
import app.gameport.core.settings.UserSettings
import app.gameport.core.steam.SteamAuthRepository
import app.gameport.core.sync.CloudSyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import app.gameport.core.model.FootprintTotal
import app.gameport.core.model.GameFootprint
import app.gameport.core.model.UnfinishedDownload
import app.gameport.core.model.total
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: UserSettings,
    private val auth: SteamAuthRepository,
    private val installer: GameInstallRepository,
    private val cloudSync: CloudSyncCoordinator,
    private val gameSettings: GameSettingsStore,
    private val updater: AppUpdater,
    private val patching: app.gameport.core.sync.PatchAllCoordinator,
    private val library: SteamLibraryRepository,
    private val adultApps: app.gameport.core.settings.AdultApps,
    private val history: PlayHistoryStore,
    private val incompatible: app.gameport.core.settings.IncompatibleGames,
    private val verdicts: app.gameport.core.settings.GameVerdicts,
    private val tours: app.gameport.core.settings.TourController,
    private val footprints: app.gameport.core.install.GameFootprints,
    private val updatesRepository: app.gameport.core.install.GameUpdatesRepository,
    private val downloadRegion: app.gameport.core.steam.DownloadRegion,
    device: DeviceProfile,
) : ViewModel() {
    /** The games hidden in GamePort's library (Steam's own library is untouched), by name. */
    val hiddenGames: StateFlow<List<Game>> = combine(library.observeLibrary(), history.hidden) { loaded, hidden ->
        loaded.games.filter { it.appId in hidden }.sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Starts a guided tour; the page it belongs to opens by itself. */
    fun onStartTour(kind: app.gameport.core.model.TourKind) = tours.start(kind)

    /** Starts the complete tour, which goes through every page. */
    fun onStartCompleteTour() = tours.startComplete()

    fun onShowAgain(appId: Int) = history.setHidden(appId, false)

    /** A game of the library that is confirmed as incompatible, with the reason to tell. */
    class IncompatibleEntry(val game: Game, val reason: app.gameport.core.model.IncompatibleReason?)

    /** The games of the library confirmed as incompatible. */
    val incompatibleGames: StateFlow<List<IncompatibleEntry>> = combine(library.observeLibrary(), incompatible.lists) { loaded, confirmed ->
        loaded.games.filter { confirmed.game(it.appId) != null }.sortedBy { it.name.lowercase() }
            .map { IncompatibleEntry(it, confirmed.reasonOf(it.appId)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val showIncompatible: StateFlow<Boolean> = incompatible.showAnyway

    /** The player lets their answers about games ("it worked" / "it did not") go to the project. */
    val shareVerdicts: StateFlow<Boolean> = verdicts.share

    fun onShareVerdictsChanged(share: Boolean) = verdicts.setShare(share)

    fun onShowIncompatibleChanged(show: Boolean) = incompatible.setShowAnyway(show)

    /** How many installed games are behind on patches, and where the run that patches them stands. */
    val patchAll: StateFlow<app.gameport.core.model.PatchAllInfo> = patching.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), app.gameport.core.model.PatchAllInfo(0, null))

    fun onPatchAll() = patching.patchAll()

    fun onStopPatchAll() = patching.stop()

    fun onClosePatchAll() = patching.clear()

    /** The names of the games, to say which one is being patched. */
    val gameNames: StateFlow<Map<Int, String>> = library.observeLibrary().map { loaded -> loaded.games.associate { it.appId to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    /** Height and seated defaults only exist on a VR device. */
    val isHeadset: Boolean = device.isHeadset

    /** The configuration every game uses unless it has its own. */
    val playerDefaults: StateFlow<PlayerDefaults> = gameSettings.defaults

    fun onDefaultHeightChanged(heightCm: Int) = gameSettings.setDefaults(PlayerDefaults(heightCm))

    val speedUnit: StateFlow<SpeedUnit> = settings.speedUnit

    val display: StateFlow<DisplaySettings> = settings.display

    fun onDisplayChanged(change: (DisplaySettings) -> DisplaySettings) = settings.updateDisplay(change)

    val language: StateFlow<AppLanguage> = settings.language

    fun onLanguageSelected(language: AppLanguage) = settings.setLanguage(language)

    /** Display name of the signed-in Steam account, if any. */
    val accountName: StateFlow<String?> = auth.authState
        .map { (it as? AuthState.SignedIn)?.account?.displayName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** True when the player chose offline mode. */
    val offline: StateFlow<Boolean> = auth.connection
        .map { it == app.gameport.core.model.SteamConnection.OFFLINE_MODE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    fun onOfflineModeChanged(enabled: Boolean) {
        viewModelScope.launch { auth.setOfflineMode(enabled) }
    }

    val countPlaytimeOnSteam: StateFlow<Boolean> = settings.countPlaytimeOnSteam

    fun onCountPlaytimeOnSteamChanged(count: Boolean) = settings.setCountPlaytimeOnSteam(count)

    val sendAchievementsToSteam: StateFlow<Boolean> = settings.sendAchievementsToSteam

    fun onSendAchievementsToSteamChanged(send: Boolean) = settings.setSendAchievementsToSteam(send)

    val returnMode: StateFlow<app.gameport.core.model.ReturnMode> = settings.returnMode

    fun onReturnModeChanged(mode: app.gameport.core.model.ReturnMode) = settings.setReturnMode(mode)

    fun onSpeedUnitSelected(unit: SpeedUnit) = settings.setSpeedUnit(unit)

    /** The region downloads are served from: 0 is automatic. */
    val downloadRegionId: StateFlow<Int> = downloadRegion.cellId

    fun onDownloadRegionSelected(id: Int) {
        if (id == downloadRegion.cellId.value) return
        downloadRegion.select(id)
        // The region is given to Steam when connecting: connect again now, so the player does not have to restart anything.
        viewModelScope.launch { auth.reconnect() }
    }

    /** Forgets the account and every unfinished download. Installed games stay. */
    fun onSignOut() {
        viewModelScope.launch {
            installer.discardAll()
            cloudSync.forgetSyncState()
            auth.signOut()
        }
    }

    val installedVersion: String = updater.installedVersion

    val canUpdateInPlace: Boolean = updater.canUpdateInPlace

    val updateState: StateFlow<AppUpdateState> = updater.state

    /** What stops an update from starting now, if anything. */
    val updateBlocker = updater.blocker.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val updateCheckHours: StateFlow<Int> = settings.updateCheckHours

    fun onUpdateCheckHoursChanged(hours: Int) = settings.setUpdateCheckHours(hours)

    fun onCheckForUpdate() = updater.check(manual = true)

    fun onUpdate() = updater.update()

    /** One installed game on the page that manages the games. */
    class ManagedGame(
        val appId: Int,
        val name: String,
        val footprint: GameFootprint,
        val lastPlayedMillis: Long?,
        val updateAvailable: Boolean,
        val patchBehind: Boolean,
        /** The cover, built by the model (see Art) from the library when the game is in it. */
        val art: app.gameport.core.model.Art,
    )

    /** The page that manages the games: the games with their sizes, the downloads left behind, and the free space. */
    class ManageState(
        val loading: Boolean = true,
        val games: List<ManagedGame> = emptyList(),
        val unfinished: List<Pair<String, UnfinishedDownload>> = emptyList(),
        val freeBytes: Long = 0L,
    ) {
        val total: FootprintTotal get() = games.map { it.footprint }.total()
        val unfinishedBytes: Long get() = unfinished.sumOf { it.second.bytes }
    }

    private class Measured(val sizes: Map<Int, GameFootprint>, val unfinished: List<UnfinishedDownload>, val freeBytes: Long)

    private val measured = MutableStateFlow<Measured?>(null)

    /** Measures the games again: the folders are read off the main thread, and the page shows what it had until the new figures come. */
    fun refreshManage() {
        viewModelScope.launch {
            measured.value = Measured(footprints.ofInstalled(), footprints.unfinished(), footprints.freeBytes())
        }
    }

    init {
        refreshManage()
        // A game installed, removed or stopped changes what is on the device: measure again once things settle, not at every step of a download.
        viewModelScope.launch {
            installer.observeAll()
                .map { states -> states.entries.sortedBy { it.key }.joinToString { "${it.key}:${it.value::class.simpleName}" } }
                .distinctUntilChanged()
                .drop(1)
                .debounce(MEASURE_SETTLE_MS)
                .collect { refreshManage() }
        }
    }

    val manage: StateFlow<ManageState> = combine(
        measured,
        library.observeLibrary(),
        history.lastPlayed,
        updatesRepository.updates,
        installer.observeOutdatedPatches(),
    ) { sizes, loaded, played, updates, behind ->
        if (sizes == null) return@combine ManageState()
        val byId = loaded.games.associateBy { it.appId }
        val names = byId.mapValues { it.value.name }
        val withUpdate = updates.map { it.appId }.toSet()
        ManageState(
            loading = false,
            // An adult game the player may not see is not listed here either.
            games = sizes.sizes.filterKeys { !adultApps.isHidden(it) }.map { (appId, footprint) ->
                ManagedGame(appId, names[appId] ?: "#$appId", footprint, played[appId], appId in withUpdate, appId in behind, byId[appId]?.capsuleArt ?: app.gameport.core.model.Covers.capsule(appId))
            },
            unfinished = sizes.unfinished.map { (names[it.appId] ?: "#${it.appId}") to it },
            freeBytes = sizes.freeBytes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ManageState())

    /** Installs the newer build of each game, as the page of the game does. */
    fun onUpdateGames(appIds: Collection<Int>) {
        viewModelScope.launch {
            appIds.forEach { appId -> library.observeGame(appId).first()?.let(installer::update) }
        }
    }

    /** Patches the games again, one after the other, the run the settings already show. */
    fun onPatchGames(appIds: Collection<Int>) = installer.repatchAll(appIds.sorted())

    /** Removes the games from the device; Android asks to confirm each one. */
    fun onUninstallGames(appIds: Collection<Int>) = appIds.forEach(installer::uninstall)

    /** Throws away what a stopped download left, which frees the space it took. */
    fun onDiscardUnfinished(appId: Int) = installer.discard(appId)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MEASURE_SETTLE_MS = 1_500L
    }
}
