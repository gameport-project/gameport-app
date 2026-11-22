package app.gameport.feature.game

import app.gameport.core.model.reportable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
import app.gameport.core.sync.state
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
import kotlinx.coroutines.flow.first
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
        val controllerFrame: Boolean = false,
        val controllerAutomatic: Boolean = false,
        /** The layer adapted the game by itself (the headset has no play area): a small mark on the compatibility button. */
        val compatNotice: Boolean = false,
        /** The player starred the game: it is listed in the library's favorites. */
        val favorite: Boolean = false,
        /** The player hid the game in GamePort (Steam is not concerned). */
        val hidden: Boolean = false,
        /** Time played on this device and, once Steam answered, on the whole account. */
        val playtime: app.gameport.core.model.Playtime = app.gameport.core.model.Playtime(),
        /** False on a device without VR: nothing that belongs to VR is shown. */
        val vrDevice: Boolean = true,
        /** True on a device whose runtime takes the image size, the frame rate and the levels from GamePort (a Pico): the page of them is offered. */
        val performanceDevice: Boolean = false,
        /** What the player chose for the device's graphics in this game, when anything: the page offers to turn it off after a problem. */
        val performanceChoices: app.gameport.core.model.GameSettings? = null,
        /** The game has saves and the ones on this device and on Steam do not agree. */
        val savesNotSynced: Boolean = false,
        /** What the players say about the game: null while too few of them said anything. */
        val compat: app.gameport.core.model.Compat? = null,
        /** Why the game is known not to run, when it is on the list of incompatible games. */
        val incompatible: app.gameport.core.model.IncompatibleReason? = null,
        /** What is to be known about the game on this device though it runs (for instance that it may close on its own): told on its page, never hiding it. */
        val warning: app.gameport.core.model.IncompatibleReason? = null,
        /** What the players of every kind of device said about the game, for the details behind its label. */
        val compatCounts: app.gameport.core.model.CompatCounts? = null,
        /** The kind of device in hand ("quest", "pico", "phone", "tablet", "other") and its name when that is known (Quest 3, Pico 4). */
        val deviceKind: String = "other",
        val deviceModel: String? = null,
        /** The version of GamePort, for the details behind the label. */
        val appVersion: String = "",
        /** Whose copy of the game is used, when several people of the family have it; null when there is nothing to choose. */
        val family: FamilyView? = null,
    ) : GameUiState
}

/** One person of the family who has the game: [name] is null until Steam has told it. */
data class FamilyMember(val accountId: Long, val name: String?, val isMe: Boolean)

/** The people of the family who have a game, and the one whose copy is used. */
data class FamilyView(val members: List<FamilyMember>, val usedId: Long, val needsRepatch: Boolean = false) {
    val used: FamilyMember? get() = members.firstOrNull { it.accountId == usedId }
}

/** Where a re-patch or an update of an installed game stands. */
enum class RepatchStage { QUEUED, DOWNLOADING, PATCHING, INSTALLING }

/** Patching or updating an installed game, shown in the game's attention panel. */
sealed interface Repatch {
    data object None : Repatch

    data class Running(
        val stage: RepatchStage,
        val fraction: Float? = null,
        val bytesPerSecond: Long = 0,
        val step: app.gameport.core.model.InstallStep? = null,
        /** True when the install is over and only waits for a confirmation window the player closed: it has to be asked for again, or given up. */
        val waitingForConfirmation: Boolean = false,
    ) : Repatch

    data class Failed(val error: InstallError) : Repatch
}

@HiltViewModel
class GameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: SteamLibraryRepository,
    private val installer: GameInstallRepository,
    private val issuesRepository: GameIssuesRepository,
    private val saveSpaces: app.gameport.core.sync.SaveSpaceStore,
    private val controllerMappings: ControllerMappingStore,
    private val gameSettingsStore: app.gameport.core.settings.GameSettingsStore,
    device: DeviceProfile,
    settings: UserSettings,
    private val playHistory: app.gameport.core.settings.PlayHistoryStore,
    playtimeStore: app.gameport.core.settings.PlaytimeStore,
    private val playtimeTracker: app.gameport.core.sync.PlaytimeTracker,
    auth: app.gameport.core.steam.SteamAuthRepository,
    private val reports: app.gameport.core.sync.ReportStore,
    private val reporter: app.gameport.core.sync.ProblemReporter,
    private val reportSender: app.gameport.core.sync.ReportSender,
    achievementsRepository: app.gameport.core.steam.AchievementsRepository,
    saves: app.gameport.core.sync.SaveStateRepository,
    private val compat: app.gameport.core.sync.CompatRepository,
    incompatibleGames: app.gameport.core.settings.IncompatibleGames,
    private val launchOptions: app.gameport.core.steam.LaunchOptionsRepository,
    private val verdicts: app.gameport.core.settings.GameVerdicts,
    private val mediaRepository: app.gameport.core.sync.MediaRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    init {
        // The arguments Steam lists for starting the game are kept now, so the game starts with them even without a connection.
        viewModelScope.launch { launchOptions.refresh(savedStateHandle.toRoute<GameRoute>().appId) }
        // The screenshots and trailers are asked again when the list of them is old; what is kept shows meanwhile.
        viewModelScope.launch { runCatching { mediaRepository.refreshIfStale() } }
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
        combine(issuesRepository.observe(appId), saveSpaces.observe(appId)) { issues, saveMode -> issues to saveMode },
        combine(controllerMappings.observe(appId), gameSettingsStore.stageNotice(appId), gameSettingsStore.observe(appId)) { mapping, fallback, own -> Triple(mapping, fallback && own.recenter == app.gameport.core.model.RecenterMode.AUTO, own) },
        combine(playHistory.favorites, playHistory.hidden) { starred, hidden -> starred to hidden },
    ) { game, install, (issues, saveMode), (controllers, compatNotice, own), (favorites, hidden) ->
        if (game !is GameUiState.Content) return@combine game
        // Patching or updating a game that is already installed is not a first install: the game
        // keeps its Play and uninstall buttons, and the panel shows the progress.
        val installedPackage = installer.installedPackage(appId)
        val repatch = when {
            installedPackage == null -> Repatch.None
            install is InstallState.Queued -> Repatch.Running(RepatchStage.QUEUED)
            install is InstallState.Downloading -> Repatch.Running(RepatchStage.DOWNLOADING, install.progress, install.bytesPerSecond)
            install is InstallState.Patching -> Repatch.Running(RepatchStage.PATCHING)
            install is InstallState.Installing || install is InstallState.Finishing -> Repatch.Running(
                RepatchStage.INSTALLING,
                step = (install as? InstallState.Installing)?.step,
                // The window is gone (the player closed it) and nothing is running any more: the card has to offer asking for it again, or giving the install up.
                waitingForConfirmation = (install as? InstallState.Installing)?.step == app.gameport.core.model.InstallStep.AWAITING_CONFIRMATION && !installer.hasInstallConfirmation(),
            )
            install is InstallState.Failed -> Repatch.Failed(install.error)
            else -> Repatch.None
        }
        val shown = if (repatch != Repatch.None && installedPackage != null) InstallState.Installed(installedPackage) else install
        game.copy(install = shown, issues = issues + listOfNotNull(GameIssue.SavesApart.takeIf { !saveMode.usesPc && game.game.androidBuild?.saveRules?.any { it.pcCloudPrefix != null } == true }), repatch = repatch, controllerProfile = device.isHeadset, controllerFrame = device.isHeadset && controllers.forced, controllerAutomatic = device.isHeadset && controllers.automatic, compatNotice = device.isHeadset && compatNotice, favorite = appId in favorites, hidden = appId in hidden, vrDevice = device.isHeadset, performanceDevice = device.isHeadset, performanceChoices = own.takeIf { device.isHeadset && (reports.suspectedAt(installer.installedPackage(appId).orEmpty()) ?: 0L) > it.performanceChangedAt && (it.refreshRate != 0 || it.resolutionPercent != 0 || it.cpuLevel != -1 || it.gpuLevel != -1) })
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GameUiState.Loading)

    private val steamMinutes = MutableStateFlow<Int?>(null)

    /** The account's achievements for the game: what was kept at once, then what Steam says. Null while unknown. */
    val achievements: StateFlow<app.gameport.core.model.AchievementList?> = achievementsRepository.observe(appId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The screenshots and trailers of the game, as its page in the shop shows them, once the device has the file that holds them. */
    val media: StateFlow<app.gameport.core.model.AppMedia?> = mediaRepository.observe(appId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The extra content chosen for the install that is going, shown from its start. */
    val extras: StateFlow<List<app.gameport.core.model.ExtraPlanItem>> = installer.observeExtras(appId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val savesNotSynced: Flow<Boolean> = saves.observe(appId).map { it.files.isNotEmpty() && !it.inSync }

    /** What is said about the game by the players, and by the list of incompatible games. */
    private class Opinion(val compat: app.gameport.core.model.Compat?, val counts: app.gameport.core.model.CompatCounts?, val incompatible: app.gameport.core.model.IncompatibleReason?, val warning: app.gameport.core.model.IncompatibleReason?)

    private val opinion: Flow<Opinion> = combine(compat.observe(appId), compat.observeCounts(appId), incompatibleGames.lists) { community, counts, confirmed -> Opinion(community, counts, confirmed.reasonOf(appId), confirmed.warningReasonOf(appId)) }

    private val deviceKind = device.kind
    private val deviceModel = device.model
    private val appVersion = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }.getOrDefault("")

    private val _compatReports = MutableStateFlow<app.gameport.core.model.CompatReports?>(null)

    /** What the relay lists for this game (model, answer, version of GamePort, how many players, what the history says), once the details behind its label were opened; null until then. */
    val compatReports: StateFlow<app.gameport.core.model.CompatReports?> = _compatReports

    /** The details behind the label were opened: the lines are asked of the relay, and the totals of the models show meanwhile. */
    fun onCompatDetailsOpened() {
        viewModelScope.launch { compat.reports(appId)?.let { _compatReports.value = it } }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val family: Flow<FamilyView?> = repository.observeGame(appId)
        .map { it?.owners.orEmpty().distinct() }
        .distinctUntilChanged()
        .flatMapLatest { owners ->
            if (!app.gameport.core.model.FamilyChoice.hasChoice(owners)) {
                flowOf(null)
            } else {
                // The package changes when the game is patched again: the need to do it is read again then.
                combine(gameSettingsStore.observeOwnerChoice(appId), repository.observeOwnerNames(owners), installer.observePatchOutdated(appId)) { picked, names, _ ->
                    val me = repository.accountId().takeIf { it != 0L } ?: return@combine null
                    val used = app.gameport.core.model.FamilyChoice.resolve(owners, me, picked) ?: return@combine null
                    val game = repository.observeGame(appId).first()
                    FamilyView(owners.map { FamilyMember(it, names[it], it == me) }.sortedByDescending { it.isMe }, used, needsRepatch = game != null && installer.familyNeedsRepatch(game))
                }
            }
        }

    /** The player picks whose copy of the game is used; it stays picked. */
    fun onFamilyChosen(accountId: Long) = gameSettingsStore.setOwnerChoice(appId, accountId)

    val uiState: StateFlow<GameUiState> = combine(
        baseState,
        combine(playtimeStore.observe(appId), steamMinutes, savesNotSynced) { deviceMillis, steam, notSynced -> Triple(deviceMillis, steam, notSynced) },
        opinion,
        family,
    ) { state, (deviceMillis, steam, notSynced), opinion, family ->
        if (state is GameUiState.Content) {
            state.copy(
                family = family,
                playtime = app.gameport.core.model.Playtime(deviceMillis, steam),
                savesNotSynced = notSynced,
                compat = opinion.compat,
                incompatible = opinion.incompatible,
                warning = opinion.warning,
                compatCounts = opinion.counts,
                deviceKind = deviceKind,
                deviceModel = deviceModel,
                appVersion = appVersion,
            )
        } else state
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GameUiState.Loading)

    /** Asks Steam again for the account's total time in the game; the last answer stays when it cannot be reached. */
    fun refreshPlaytime() {
        viewModelScope.launch { playtimeTracker.steamMinutes(appId)?.let { steamMinutes.value = it } }
    }

    /** [dlc] is what the player chose; null resumes an interrupted install with its earlier choice. */
    fun onInstall(game: Game, dlc: Set<Int>?) = installer.install(game, dlc)

    /** The channel (branch) of Steam this game is fetched from: the one picked, the normal one by default. */
    val channelInUse: StateFlow<String> = gameSettingsStore.observe(appId)
        .map { it.channelName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), gameSettingsStore.get(appId).channelName)

    /** The player picks the channel the game is fetched from, before installing it (a game that has nothing on the normal channel has to be installed from another). */
    fun onChannelChosen(name: String) {
        val picked = if (name == app.gameport.core.model.UpdateChannels.NORMAL) null else name
        val current = gameSettingsStore.get(appId)
        if (current.channel != picked) gameSettingsStore.set(appId, current.copy(channel = picked))
    }

    fun onRepatch() = installer.repatch(appId)

    /**
     * Installs the game again under a name of GamePort's own: what a device that refused to start it needs (see [GameIssue.RefusedByDevice]). The choice is written
     * down as well, so the game is patched and updated that way from then on, and the patch is what puts it there.
     */
    fun onReinstallRenamed() {
        gameSettingsStore.set(appId, gameSettingsStore.get(appId).copy(packageName = app.gameport.core.model.PackageNameMode.ON))
        installer.repatch(appId)
    }

    /**
     * Turns off every choice of the Performance page for this game: the game goes back to what it asks for itself. When GamePort added the declaration of the Quests the
     * game is made for (for a frame rate), the game is patched again without it.
     */
    fun onTurnOffPerformance() {
        val declared = installer.carriesSupportedDevicesMark(appId)
        gameSettingsStore.set(
            appId,
            gameSettingsStore.get(appId).copy(
                resolutionPercent = app.gameport.core.model.PerformanceTuning.DEFAULT_RESOLUTION,
                refreshRate = 0,
                cpuLevel = app.gameport.core.model.PerformanceTuning.LEVELS.first(),
                gpuLevel = app.gameport.core.model.PerformanceTuning.LEVELS.first(),
            ),
        )
        if (declared) installer.repatch(appId)
    }

    /** Downloads the game's expansion files again and places them, the APK on the device staying as it is. */
    fun onRepair() {
        (uiState.value as? GameUiState.Content)?.game?.let(installer::repair)
    }

    /** The player says the game works: the question about its expansion files, and its blue mark, go. */
    fun onReinstallWorks() = issuesRepository.answerReinstallWorks(appId)

    /** The player says the game does not work and agreed to start again: it is uninstalled, then installed from scratch. */
    fun onReinstallFromScratch() {
        (uiState.value as? GameUiState.Content)?.game?.let(installer::reinstallFromScratch)
    }

    /** Installs the newer build Steam published, over the installed game. */
    fun onUpdate() {
        (uiState.value as? GameUiState.Content)?.game?.let(installer::update)
    }

    fun onCancel() = installer.cancel(appId)

    /** Opens again the confirmation Android asked for the install: the window may have been closed, covered or lost. */
    fun onReopenConfirmation() = installer.askConfirmationAgain(appId, (baseState.value as? GameUiState.Content)?.game)

    /** Whether this game looks like it had a problem (it closed at once, or crashed). */
    val suspicion: StateFlow<app.gameport.core.sync.Suspicion?> = reports.suspected
        .map { installer.installedPackage(appId)?.let(it::get) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    internal val lastRun: StateFlow<LastRun?> = lastRunFlow(appId, reports, gameSettingsStore, installer, deviceModel)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The player closed the advice on the resolution: it is not shown again for this run. */
    fun onCloseResolutionAdvice() {
        val packageName = installer.installedPackage(appId) ?: return
        reports.runs.value[packageName]?.let { reports.closeResolutionAdvice(packageName, it) }
    }

    /** The frame rate that was taken off this game because the device does not accept it, until the player closes the note. */
    val rateReset: StateFlow<app.gameport.core.model.RateReset?> = reports.rateResets
        .map { installer.installedPackage(appId)?.let(it::get) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun onDismissRateReset() {
        installer.installedPackage(appId)?.let(reports::dismissRateReset)
    }

    /** The Discord name given with an earlier report: it fills the field of the report window. Empty when none. */
    internal val discordName: StateFlow<String> = verdicts.discordName

    private val _reportProgress = MutableStateFlow<ReportProgress>(ReportProgress.Idle)
    internal val reportProgress: StateFlow<ReportProgress> = _reportProgress

    /** The report is made now and sent to the project, even when the automatic sending is off: the player asked. */
    internal fun onSendReport(game: Game, description: String? = null, symptoms: Set<app.gameport.core.model.Symptom> = emptySet(), discord: String? = null) {
        if (_reportProgress.value == ReportProgress.Working) return
        // The Discord name is kept for the next report, so the field is filled in and never asked again; what is typed now replaces it, and an emptied field removes it.
        if (discord != null) verdicts.setDiscordName(discord)
        // A problem reported by hand counts as "it did not work" for the players' totals, unless the player already said so; what was ticked goes with that answer.
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        verdicts.reportedProblem(appId, version, java.time.LocalDate.now().toString())
        if (symptoms.isNotEmpty()) verdicts.setIssues(appId, symptoms)
        viewModelScope.launch {
            _reportProgress.value = ReportProgress.Working
            val failure = ((uiState.value as? GameUiState.Content)?.install as? InstallState.Failed)?.error?.takeIf { it.reportable }?.toString()
            val status = reportSender.sendByHand(game, failure, description, symptoms)
            // The answer says what became of the report; it goes again when that changed.
            verdicts.reportStated(appId, status.state())
            _reportProgress.value = ReportProgress.Done(status)
            // The report said why it was made; the notice has done its job.
            if (status == app.gameport.core.sync.ReportStatus.SENT || status == app.gameport.core.sync.ReportStatus.WAITING) onDismissProblem()
        }
    }

    internal fun ticketUri(game: Game): android.net.Uri = reporter.ticketUrl(game, null)

    /** Puts the Steam Frame's controls on this game's controllers, from its next start. */
    fun onActivateFrame() = controllerMappings.setUseFrame(appId, true)

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

    /** The player opened the options the mark on the actions was about: the mark goes (until the game is adapted again). */
    fun onCompatNoticeSeen() = gameSettingsStore.setStageNoticeSeen(appId)

    /** The player read the notice about the controllers: it goes and does not come back. */
    fun onControllersNoticeRead() = controllerMappings.markNoticeSeen(appId)

    /**
     * Permissions are granted outside GamePort: check the game's issues again when the page is shown. A run started from GamePort also answers through its hook a few
     * seconds in — a device that refuses the game never does — so the page looks again for a short while: the card of a refusal is there without the player having to
     * leave the page and come back.
     */
    fun refreshIssues() {
        issuesRepository.refresh()
        // A renamed install whose old copy was not removed is finished when the page is opened: the player is there to confirm.
        installer.removeLeftoverCopy(appId)
        lookAgainWhileALaunchIsAnswered()
    }

    private var answerWatch: Job? = null

    /** Looks again a few times over the window in which the hook of a run started from GamePort answers, then stops. */
    private fun lookAgainWhileALaunchIsAnswered() {
        answerWatch?.cancel()
        answerWatch = viewModelScope.launch {
            repeat(LOOK_AGAIN_COUNT) {
                delay(LOOK_AGAIN_EVERY_MS)
                issuesRepository.refresh()
            }
        }
    }

    fun conflictIntent() = issuesRepository.conflictIntent(appId)

    fun storagePermissionBlocksPlay() = installer.storagePermissionBlocksPlay(appId)

    fun onPermissionsOpened() = installer.markStoragePermissionSettingsOpened(appId)

    fun appSettingsIntent() = installer.appSettingsIntent(appId)

    /** Intent that starts the installed game, or null if it is gone. */
    fun launchIntent() = installer.launchIntent(appId, (uiState.value as? GameUiState.Content)?.game?.androidBuild?.isVr)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** How long and how often the page looks again after it is shown, while the hook of a run started from GamePort has still not answered (see [refreshIssues]). */
        const val LOOK_AGAIN_EVERY_MS = 2_000L
        const val LOOK_AGAIN_COUNT = 5
    }
}

