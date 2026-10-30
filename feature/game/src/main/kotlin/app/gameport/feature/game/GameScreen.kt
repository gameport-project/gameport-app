@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.gameport.feature.game

import app.gameport.core.designsystem.TourIds
import app.gameport.core.designsystem.tourTarget
import app.gameport.core.designsystem.KindBlue
import app.gameport.core.model.reportable
import android.content.Intent
import androidx.compose.material.icons.filled.BugReport
import app.gameport.core.designsystem.ConnectionNotice
import app.gameport.core.model.SteamConnection
import app.gameport.core.designsystem.installErrorText
import app.gameport.core.model.VersionOption
import app.gameport.core.designsystem.GlassIconButton
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.layout.layout
import app.gameport.core.model.AppKind
import app.gameport.core.model.Playtime
import app.gameport.core.model.pick
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import app.gameport.core.designsystem.BackdropDialog
import app.gameport.core.designsystem.CompatGlyph
import app.gameport.core.designsystem.Glyph
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import app.gameport.core.designsystem.HideRed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.layout.positionInParent
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.IntrinsicSize
import app.gameport.core.designsystem.GlassBorder
import app.gameport.core.designsystem.GlassFill
import app.gameport.core.designsystem.LocalBackdropColors
import app.gameport.core.model.ExtraPlanItem
import app.gameport.core.model.InstallStage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlin.math.pow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.core.designsystem.BackButton
import app.gameport.core.designsystem.DangerButton
import app.gameport.core.designsystem.DangerTextButton
import app.gameport.core.designsystem.DangerTrashButton
import app.gameport.core.designsystem.AttentionBadge
import app.gameport.core.designsystem.QuestionBadge
import app.gameport.core.designsystem.InstallStepper
import app.gameport.core.designsystem.installStatusText
import app.gameport.core.model.stage
import app.gameport.core.designsystem.UpdateBadge
import app.gameport.core.designsystem.GameImage
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.glass
import app.gameport.core.designsystem.GlassChip
import app.gameport.core.designsystem.speedText
import app.gameport.core.model.DlcContent
import app.gameport.core.model.Game
import app.gameport.core.model.UpdateChannels
import app.gameport.core.model.GameIssue
import app.gameport.core.model.needsAttention
import app.gameport.core.model.DisplaySettings
import app.gameport.core.model.InstallError
import app.gameport.core.model.AchievementList
import app.gameport.core.model.InstallState
import app.gameport.core.model.SpeedUnit
import app.gameport.core.model.Ownership
import app.gameport.core.install.ExpansionStorageAccess

@Composable
fun GameScreen(onBack: () -> Unit, onOpenSettings: () -> Unit, onOpenSaves: () -> Unit, onOpenControllers: () -> Unit, onOpenCompat: () -> Unit, onOpenAchievements: () -> Unit, onOpenSteamSettings: () -> Unit, onOpenWorkshop: () -> Unit = {}, onOpenStore: () -> Unit = {}, viewModel: GameViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val speedUnit by viewModel.speedUnit.collectAsStateWithLifecycle()
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val artworkHeight by viewModel.artworkHeight.collectAsStateWithLifecycle()
    var explainingStorage by remember { mutableStateOf(false) }
    // Permissions are granted in the system's settings: check again when the player comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshIssues()
        viewModel.refreshPlaytime()
    }
    val openPermissions = { viewModel.appSettingsIntent()?.let { runCatching { context.startActivity(it) } }; Unit }
    val play = { viewModel.launchIntent()?.let(context::startActivity); Unit }
    if (explainingStorage) {
        BackdropDialog(
            onDismissRequest = { explainingStorage = false },
            title = { Text(stringResource(R.string.game_storage_title)) },
            text = { Text(stringResource(R.string.game_storage_text)) },
            confirmButton = {
                TextButton(onClick = {
                    explainingStorage = false
                    openPermissions()
                }) { Text(stringResource(R.string.game_storage_open)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    explainingStorage = false
                    viewModel.markStoragePermissionExplained()
                    play()
                }) { Text(stringResource(R.string.game_storage_play_anyway)) }
            },
        )
    }
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val suspicion by viewModel.suspicion.collectAsStateWithLifecycle()
    val reportProgress by viewModel.reportProgress.collectAsStateWithLifecycle()
    val shownGame = (uiState as? GameUiState.Content)?.game
    val report = ReportActions(
        suspicion = suspicion,
        progress = reportProgress,
        onSend = { text, symptoms -> shownGame?.let { viewModel.onSendReport(it, text, symptoms) } },
        onTicket = { shownGame?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, viewModel.ticketUri(it))) } } },
        onDismissProblem = viewModel::onDismissProblem,
        onResetProgress = viewModel::onResetReport,
    )
    GameContent(
        uiState = uiState,
        connection = connection,
        onOpenSteamSettings = onOpenSteamSettings,
        report = report,
        speedUnit = speedUnit,
        onOpenSettings = onOpenSettings,
        onOpenSaves = onOpenSaves,
        onOpenControllers = onOpenControllers,
        onOpenCompat = onOpenCompat,
        onActivateFrame = viewModel::onActivateFrame,
        onRepair = viewModel::onRepair,
        onReinstallWorks = viewModel::onReinstallWorks,
        onControllersRead = viewModel::onControllersNoticeRead,
        onReinstallFromScratch = viewModel::onReinstallFromScratch,
        achievements = achievements,
        onOpenAchievements = onOpenAchievements,
        onOpenWorkshop = onOpenWorkshop,
        onOpenStore = onOpenStore,
        artworkHeight = artworkHeight,
        extras = viewModel.extras.collectAsStateWithLifecycle().value,
        media = viewModel.media.collectAsStateWithLifecycle().value,
        onCompatNoticeSeen = viewModel::onCompatNoticeSeen,
        onToggleFavorite = viewModel::onToggleFavorite,
        onSetHidden = viewModel::onSetHidden,
        onBack = onBack,
        onInstall = viewModel::onInstall,
        channelInUse = viewModel.channelInUse.collectAsStateWithLifecycle().value,
        onChannelChosen = viewModel::onChannelChosen,
        onCancel = viewModel::onCancel,
        onPause = viewModel::onPause,
        onVersionChosen = viewModel::onVersionChosen,
        onDuplicateChosen = viewModel::onDuplicateChosen,
        onDiscard = viewModel::onDiscard,
        onUninstall = viewModel::onUninstall,
        onPlay = { if (viewModel.shouldExplainStoragePermission()) explainingStorage = true else play() },
        onPatchAndPlay = { viewModel.onPatchAndPlay { if (viewModel.shouldExplainStoragePermission()) explainingStorage = true else play() } },
        onOpenPermissions = openPermissions,
        onRepatch = viewModel::onRepatch,
        onUpdate = viewModel::onUpdate,
        onResolveConflict = { viewModel.conflictIntent()?.let { runCatching { context.startActivity(it) } }; Unit },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GameContent(
    uiState: GameUiState,
    connection: SteamConnection = SteamConnection.ONLINE,
    onOpenSteamSettings: () -> Unit = {},
    report: ReportActions = ReportActions.None,
    speedUnit: SpeedUnit,
    onOpenSettings: () -> Unit,
    onOpenSaves: () -> Unit,
    onOpenControllers: () -> Unit,
    onOpenCompat: () -> Unit,
    onActivateFrame: () -> Unit,
    onRepair: () -> Unit = {},
    onReinstallWorks: () -> Unit = {},
    onControllersRead: () -> Unit = {},
    onReinstallFromScratch: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onSetHidden: (Boolean) -> Unit,
    onBack: () -> Unit,
    onInstall: (Game, Set<Int>?) -> Unit,
    /** The channel (branch) of Steam the game is fetched from, and what picks another one. */
    channelInUse: String = UpdateChannels.NORMAL,
    onChannelChosen: (String) -> Unit = {},
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onVersionChosen: (String?) -> Unit,
    onDuplicateChosen: (Boolean) -> Unit,
    onDiscard: () -> Unit,
    onUninstall: () -> Unit,
    onPlay: () -> Unit,
    onPatchAndPlay: () -> Unit,
    onOpenPermissions: () -> Unit,
    onResolveConflict: () -> Unit,
    onRepatch: () -> Unit,

    onUpdate: () -> Unit,
    achievements: AchievementList? = null,
    onOpenAchievements: () -> Unit = {},
    onOpenWorkshop: () -> Unit = {},
    artworkHeight: Int = DisplaySettings.DEFAULT_GAME_ARTWORK_HEIGHT,
    extras: List<ExtraPlanItem> = emptyList(),
    media: app.gameport.core.model.AppMedia? = null,
    onCompatNoticeSeen: () -> Unit = {},
    onOpenStore: () -> Unit = {},
) {
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (uiState) {
                GameUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                GameUiState.NotFound -> Text(
                    text = stringResource(R.string.game_not_found),
                    modifier = Modifier.align(Alignment.Center),
                )
                is GameUiState.Content -> GameDetails(
                    game = uiState.game,
                    installReal = uiState.install,
                    issues = uiState.issues,
                    repatch = uiState.repatch,
                    onRepatch = onRepatch,
                    onUpdate = onUpdate,
                    speedUnit = speedUnit,
                    onInstall = { dlc -> onInstall(uiState.game, dlc) },
                    channelInUse = channelInUse,
                    onChannelChosen = onChannelChosen,
                    onCancel = onCancel,
                    onPause = onPause,
                    onVersionChosen = onVersionChosen,
                    onDuplicateChosen = onDuplicateChosen,
                    onDiscard = onDiscard,
                    onUninstall = onUninstall,
                    onPlay = onPlay,
                    onPatchAndPlay = onPatchAndPlay,
                    onOpenSettings = onOpenSettings,
                    onOpenSaves = onOpenSaves,
                    controllerProfile = uiState.controllerProfile,
                    controllerFrame = uiState.controllerFrame,
                    controllerAutomatic = uiState.controllerAutomatic,
                    compatNotice = uiState.compatNotice,
                    savesNotSynced = uiState.savesNotSynced,
                    compat = uiState.compat,
                    incompatible = uiState.incompatible,
                    vrDevice = uiState.vrDevice,
                    onOpenControllers = onOpenControllers,
                    onOpenCompat = onOpenCompat,
                    onActivateFrame = onActivateFrame,
                    onRepair = onRepair,
                    onReinstallWorks = onReinstallWorks,
                    onControllersRead = onControllersRead,
                    onReinstallFromScratch = onReinstallFromScratch,
                    onToggleFavorite = onToggleFavorite,
                    onSetHidden = onSetHidden,
                    hidden = uiState.hidden,
                    favorite = uiState.favorite,
                    playtime = uiState.playtime,
                    onOpenPermissions = onOpenPermissions,
                    onResolveConflict = onResolveConflict,
                    report = report,
                    achievements = achievements,
                    onOpenAchievements = onOpenAchievements,
                    onOpenWorkshop = onOpenWorkshop,
                    artworkHeight = artworkHeight,
                    extras = extras,
                    media = media,
                    onCompatNoticeSeen = onCompatNoticeSeen,
                    onOpenStore = onOpenStore,
                )
            }
            Row(
                // (The status bar is already left free by the page's scaffold: it is not counted twice.)
                Modifier.align(Alignment.TopStart).padding(start = 32.dp, top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onClick = onBack)
                ConnectionNotice(connection, onClick = onOpenSteamSettings)
            }

        }
    }
}

@Composable
private fun GameDetails(
    game: Game,
    // The state the page really has; a debug build can play another one to look at the install (see [LayoutTrial]).
    installReal: InstallState,
    issues: List<GameIssue>,
    repatch: Repatch,
    onRepatch: () -> Unit,
    onUpdate: () -> Unit,
    speedUnit: SpeedUnit,
    onInstall: (Set<Int>?) -> Unit,
    channelInUse: String,
    onChannelChosen: (String) -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onVersionChosen: (String?) -> Unit,
    onDuplicateChosen: (Boolean) -> Unit,
    onDiscard: () -> Unit,
    onUninstall: () -> Unit,
    onPlay: () -> Unit,
    onPatchAndPlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSaves: () -> Unit,
    controllerProfile: Boolean,
    /** The Steam Frame's controls are in use for this game: the controllers button is coloured. */
    controllerFrame: Boolean,
    /** GamePort puts the Steam Frame's controls on the controllers by itself for this game: the button has another colour. */
    controllerAutomatic: Boolean,
    /** The layer adapted the game by itself: a small mark on the compatibility button. */
    compatNotice: Boolean,
    savesNotSynced: Boolean,
    compat: app.gameport.core.model.Compat?,
    incompatible: app.gameport.core.model.IncompatibleReason?,
    favorite: Boolean,
    hidden: Boolean,
    playtime: Playtime,
    vrDevice: Boolean,
    onOpenControllers: () -> Unit,
    onOpenCompat: () -> Unit,
    onActivateFrame: () -> Unit,
    onRepair: () -> Unit,
    onReinstallWorks: () -> Unit = {},
    onControllersRead: () -> Unit = {},
    onReinstallFromScratch: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onSetHidden: (Boolean) -> Unit,
    onOpenPermissions: () -> Unit,
    onResolveConflict: () -> Unit,
    report: ReportActions,
    achievements: AchievementList? = null,
    onOpenAchievements: () -> Unit = {},
    onOpenWorkshop: () -> Unit = {},
    artworkHeight: Int = DisplaySettings.DEFAULT_GAME_ARTWORK_HEIGHT,
    /** The extra content chosen for the install that is going, shown in its card. */
    extras: List<ExtraPlanItem> = emptyList(),
    media: app.gameport.core.model.AppMedia? = null,
    onCompatNoticeSeen: () -> Unit = {},
    onOpenStore: () -> Unit = {},
) {
    var actionsOpen by rememberSaveable { mutableStateOf(false) }
    // The card of the notifications: open when the page opens, closed from its cross or from the bell, opened again from the bell. Where the bell is says where its arrow points.
    var notesShown by rememberSaveable { mutableStateOf(true) }
    val pageScroll = rememberScrollState()
    var topRowWidthPx by remember { mutableFloatStateOf(0f) }
    var bellCenterPx by remember { mutableFloatStateOf(0f) }
    val install = installReal
    // The two questions the notifications can lead to: start the game over from scratch, and check its extra files.
    var confirmingReinstall by remember { mutableStateOf(false) }
    if (confirmingReinstall) {
        BackdropDialog(
            onDismissRequest = { confirmingReinstall = false },
            title = { Text(stringResource(R.string.reinstall_title)) },
            text = { Text(stringResource(R.string.reinstall_text)) },
            confirmButton = { Button(onClick = { confirmingReinstall = false; onReinstallFromScratch() }) { Text(stringResource(R.string.reinstall_confirm)) } },
            dismissButton = { GlassButton(onClick = { confirmingReinstall = false }) { Text(stringResource(R.string.repair_cancel)) } },
        )
    }
    var confirmingRepair by remember { mutableStateOf(false) }
    if (confirmingRepair) {
        BackdropDialog(
            onDismissRequest = { confirmingRepair = false },
            title = { Text(stringResource(R.string.repair_title)) },
            text = { Text(stringResource(R.string.repair_text)) },
            confirmButton = { Button(onClick = { confirmingRepair = false; onRepair() }) { Text(stringResource(R.string.repair_confirm)) } },
            dismissButton = { GlassButton(onClick = { confirmingRepair = false }) { Text(stringResource(R.string.repair_cancel)) } },
        )
    }
    var reporting by remember { mutableStateOf(false) }
    var confirmingHide by remember { mutableStateOf(false) }
    if (confirmingHide) {
        BackdropDialog(
            onDismissRequest = { confirmingHide = false },
            title = { Text(stringResource(R.string.game_hide_title)) },
            text = { Text(stringResource(R.string.game_hide_text)) },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        confirmingHide = false
                        onSetHidden(true)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = HideRed.copy(alpha = 0.14f), contentColor = HideRed),
                    border = BorderStroke(1.dp, HideRed.copy(alpha = 0.7f)),
                ) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.game_hide))
                }
            },
            dismissButton = { GlassButton(onClick = { confirmingHide = false }) { Text(stringResource(R.string.game_hide_cancel)) } },
        )
    }
    if (reporting) ReportDialog(game.name, report) { reporting = false; report.onResetProgress() }
    // Owned extra content is offered when installing; what the account lacks cannot be installed.
    val ownedDlc = game.androidBuild?.dlc.orEmpty().filter { it.owned }
    var choosingDlc by remember { mutableStateOf(false) }
    // What waits for the game when Play is pressed: a new version, a newer patch (not while one of them is going on).
    val updatePending = GameIssue.UpdateAvailable in issues && repatch == Repatch.None
    val patchPending = GameIssue.PatchOutdated in issues && repatch == Repatch.None
    var choosingPlay by remember { mutableStateOf(false) }
    if (choosingPlay) {
        BackdropDialog(
            onDismissRequest = { choosingPlay = false },
            // Half as wide as a usual dialog: a few words and three buttons one under the other.
            minWidth = 260.dp,
            maxWidth = 280.dp,
            title = { Text(stringResource(R.string.game_play_pending_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // One sentence for what is waiting: a new version and a newer patch, or one of them.
                    Text(stringResource(when {
                        updatePending && patchPending -> R.string.game_play_pending_both
                        updatePending -> R.string.issue_update_available
                        else -> R.string.issue_patch_outdated
                    }))
                    // The words start at the left, under one another, as in the actions panel; every button is as wide as the dialog.
                    Button(onClick = { choosingPlay = false; onPlay() }, modifier = Modifier.fillMaxWidth().height(ACTION_ROW_HEIGHT)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.game_play), Modifier.weight(1f))
                    }
                    if (updatePending) GlassButton(onClick = { choosingPlay = false; onUpdate() }, modifier = Modifier.fillMaxWidth().height(ACTION_ROW_HEIGHT)) {
                        // Green, as the new version is in the notifications.
                        Icon(Icons.Filled.Download, contentDescription = null, tint = NoteColors.Green, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.issue_update_action), Modifier.weight(1f))
                    }
                    if (patchPending) GlassButton(onClick = { choosingPlay = false; onRepatch() }, modifier = Modifier.fillMaxWidth().height(ACTION_ROW_HEIGHT)) {
                        // Orange, as the newer patch is in the notifications.
                        Icon(Icons.Filled.Build, contentDescription = null, tint = NoteColors.Orange, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.issue_patch_outdated_action), Modifier.weight(1f))
                    }
                    GlassButton(onClick = { choosingPlay = false }, modifier = Modifier.fillMaxWidth().height(ACTION_ROW_HEIGHT)) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.repair_cancel), Modifier.weight(1f))
                    }
                }
            },
        )
    }
    // A game that most players could not run is installed only after the player has been told.
    var warning by remember { mutableStateOf(false) }
    val afterChannel = { if (ownedDlc.isEmpty()) onInstall(emptySet()) else choosingDlc = true }
    // A game that has nothing on the normal channel is installed from another: the player picks one first, unless a channel that exists was already picked for this game.
    var choosingChannel by remember { mutableStateOf(false) }
    val channels = game.androidBuild?.channels.orEmpty()
    val needsChannel = game.androidBuild?.onlyOnOtherChannels == true && channels.none { it.name == channelInUse }
    val startInstall = { if (needsChannel) choosingChannel = true else afterChannel() }
    if (choosingChannel) {
        ChannelChoiceDialog(
            channels = channels.filter { !it.passwordRequired },
            onDismiss = { choosingChannel = false },
            onConfirm = { name ->
                choosingChannel = false
                onChannelChosen(name)
                afterChannel()
            },
        )
    }
    if (warning && compat != null) {
        BackdropDialog(
            onDismissRequest = { warning = false },
            title = { Text(stringResource(R.string.game_compat_warning_title)) },
            text = { Text(stringResource(R.string.game_compat_warning_text, deviceName(compat.device))) },
            confirmButton = { Button(onClick = { warning = false; startInstall() }) { Text(stringResource(R.string.game_compat_warning_install)) } },
            dismissButton = { GlassButton(onClick = { warning = false }) { Text(stringResource(R.string.game_compat_warning_cancel)) } },
        )
    }
    if (choosingDlc) {
        DlcDialog(
            game = game,
            dlc = ownedDlc,
            onDismiss = { choosingDlc = false },
            onConfirm = { chosen ->
                choosingDlc = false
                onInstall(chosen)
            },
        )
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(pageScroll).padding(bottom = 24.dp)) {
        // The artwork fades out into the backdrop rather than into a colour.
        // The page's layout does not depend on the setting: the artwork only fills more or less of what lies behind it. It covers
        // [artworkHeight] percent of the screen's height and fades out at its bottom edge, going down behind the content when it is taller than the slot kept for it.
        val artworkSize = (LocalConfiguration.current.screenHeightDp * artworkHeight.coerceIn(0, 100) / 100f).dp
        Box(Modifier.fillMaxWidth().height(HERO_HEIGHT)) {
            if (artworkSize > 0.dp) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .height(artworkSize)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)), blendMode = BlendMode.DstIn)
                        },
                ) {
                    GameImage(
                        art = game.heroArt,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            // What concerns the game as a whole sits at the top right, level with the back button, as small as the buttons of the shop's header.
            // On a darker, more solid ground than the glass: over the artwork the buttons are hard to see otherwise.
            // The buttons keep the size they are given, without the 48 dp touch minimum, so the group is as low as asked.
            CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
            Column(Modifier.align(Alignment.TopEnd).padding(end = 32.dp, top = 16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Each button has the page's colour behind its glass (no ground around the group): they read over the artwork without a patch under them.
            val buttonFill = Brush.linearGradient(LocalBackdropColors.current.toList())
            // A new version is a notification too (green): its line has the button that starts the update. While an update or a patch goes on, its progress is in the page's own panel instead.
            val updateNote = install is InstallState.Installed && GameIssue.UpdateAvailable in issues && repatch == Repatch.None
            // A newer patch is a notification as well, next to the new version when there is one.
            val patchNote = install is InstallState.Installed && GameIssue.PatchOutdated in issues && repatch == Repatch.None
            // Every notification of the game, in one list: the new version, the newer patch, what the issues of the game say, then the saves and the adaptation. Only an installed game has them.
            val notes = if (install !is InstallState.Installed) emptyList() else
                listOfNotNull(
                    if (updateNote) NoteItem(stringResource(R.string.issue_update_available), NoteColors.Green, NoteColors.GreenText, onUpdate, stringResource(R.string.issue_update_action)) else null,
                    if (patchNote) NoteItem(stringResource(R.string.issue_patch_outdated), NoteColors.Orange, NoteColors.OrangeText, onRepatch, stringResource(R.string.issue_patch_outdated_action)) else null,
                ) + issueNotes(
                    issues = issues,
                    busy = repatch is Repatch.Running,
                    onPermissions = onOpenPermissions,
                    onConflict = onResolveConflict,
                    onSaves = onOpenSaves,
                    onControllers = onOpenControllers,
                    onActivateFrame = onActivateFrame,
                    onAskRepair = { confirmingRepair = true },
                    onAskReinstall = { confirmingReinstall = true },
                    onReinstallWorks = onReinstallWorks,
                    onControllersRead = onControllersRead,
                ) + listOfNotNull(
                    if (savesNotSynced) NoteItem(stringResource(R.string.game_attention_saves), NoteColors.Orange, NoteColors.OrangeText, onOpenSaves) else null,
                    // Opening the options is looking at what was said: the line goes, and with it its part of the number on the bell.
                    if (compatNotice) NoteItem(stringResource(R.string.game_attention_compat), NoteColors.Orange, NoteColors.OrangeText, { onCompatNoticeSeen(); onOpenCompat() }, onRead = onCompatNoticeSeen) else null,
                )
            val hasNotes = notes.isNotEmpty()
            Row(
                Modifier.onGloballyPositioned { topRowWidthPx = it.size.width.toFloat() },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The bell: the notifications of the game, what wants a look. It carries their number, and the card below points at it.
                if (hasNotes) {
                    Box(Modifier.tourTarget(TourIds.GAME_NOTES).onGloballyPositioned { bellCenterPx = it.positionInParent().x + it.size.width / 2f }) {
                        GlassIconButton(onClick = { notesShown = !notesShown }, modifier = Modifier.size(40.dp).background(buttonFill, CircleShape)) {
                            Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.game_notifications_title))
                        }
                        Box(Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(Color(0xFFFF9800)), contentAlignment = Alignment.Center) {
                            Text(notes.size.toString(), style = MaterialTheme.typography.labelSmall, color = Color(0xFF1B1B1B))
                        }
                    }
                }
                // Opens the actions of the game. The steps of the tour that point at the buttons inside it point here.
                Box(Modifier.tourTarget(TourIds.GAME_MENU)) {
                    GlassButton(onClick = { actionsOpen = true }, modifier = Modifier.height(40.dp).background(buttonFill, RoundedCornerShape(50)), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)) {
                        Icon(Icons.Filled.Menu, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.game_actions))
                    }
                }
                GlassIconButton(onClick = onOpenSettings, enabled = repatch !is Repatch.Running, modifier = Modifier.size(40.dp).background(buttonFill, CircleShape).tourTarget(TourIds.GAME_SETTINGS)) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.game_settings_title))
                }
                app.gameport.core.designsystem.TourHelpButton(app.gameport.core.model.TourKind.GAME, Modifier.size(40.dp).background(buttonFill, CircleShape))
            }
            if (hasNotes && notesShown) {
                val card: @Composable () -> Unit = {
                    NotificationCard(
                        notes = notes,
                        title = stringResource(R.string.game_notifications_title),
                        closeLabel = stringResource(R.string.game_actions_close),
                        readLabel = stringResource(R.string.issue_mark_read),
                        arrowFromRightPx = (topRowWidthPx - bellCenterPx).coerceAtLeast(0f),
                        onClose = { notesShown = false },
                    )
                }
                if (LocalConfiguration.current.screenWidthDp.dp < COMPACT_WIDTH) {
                    // On a phone the cover sits under this corner: the card is a popover, above the cover, under the buttons, its right edge on theirs. It is a window of its own, which
                    // would stay over the page while the page scrolls under it: it is only there while the page is at its top.
                    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
                    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
                    if (!actionsOpen && pageScroll.value < with(LocalDensity.current) { 24.dp.roundToPx() }) androidx.compose.ui.window.Popup(
                        popupPositionProvider = remember(gap, margin) {
                            object : androidx.compose.ui.window.PopupPositionProvider {
                                override fun calculatePosition(
                                    anchorBounds: androidx.compose.ui.unit.IntRect,
                                    windowSize: androidx.compose.ui.unit.IntSize,
                                    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                                    popupContentSize: androidx.compose.ui.unit.IntSize,
                                ) = androidx.compose.ui.unit.IntOffset((anchorBounds.right - popupContentSize.width).coerceAtLeast(margin), anchorBounds.bottom + gap)
                            }
                        },
                    ) { card() }
                } else {
                    card()
                }
            }
            }
            }
        }
        // The cover straddles the hero: half of it sits on the artwork, half below. On a narrow screen it is
        // centred above the details instead of beside them.
        // On a phone the cover is centred and would sit over the buttons at the top right: the content starts lower there, under them.
        BoxWithConstraints(Modifier.fillMaxWidth().pullUp(if (LocalConfiguration.current.screenWidthDp.dp < COMPACT_WIDTH) CONTENT_OVERLAP - 60.dp else CONTENT_OVERLAP)) {
            val compact = maxWidth < COMPACT_WIDTH
            // Wide layout: the bottom of the cover is lined up with the bottom of the action buttons. The size card under the cover
            // then starts level with the first card beside it (patch panel or achievements), as far from the cover as that card is from the buttons.
            val density = LocalDensity.current
            val alignment = remember(density) { CoverAlignment(with(density) { (COVER_HEIGHT / 2).toPx() }) }
            // Eased, so a change in the buttons moves the cover smoothly instead of jumping.
            val coverShift by animateFloatAsState(alignment.shift, tween(250), label = "cover shift")
            val cover: @Composable (Modifier) -> Unit = { coverModifier ->
                // The cover floats above the page: three shadows stacked, as a real card would cast them, drawn by hand (no blur effect, which
                // does not always render): a tight dark one where it would nearly touch, a wide soft one under it, and a very wide faint one.
                Box(coverModifier.width(COVER_WIDTH).height(COVER_HEIGHT).liftedShadow(14.dp)) {
                    GameImage(
                        art = game.capsuleArt,
                        contentDescription = game.name,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                    )
                    // A faint sheen across the top left, as light on a glossy card.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(0f to Color.White.copy(alpha = 0.16f), 0.45f to Color.Transparent)),
                    )
                    if (install is InstallState.Installed) {
                        // Orange for what needs attention, blue for a question, green when the only news is an update.
                        val attention = issues.any { it.needsAttention }
                        val question = GameIssue.ReinstallMayHelp in issues
                        val update = GameIssue.UpdateAvailable in issues
                        if (attention || question || update) {
                            Row(Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (attention) AttentionBadge(size = 22.dp)
                                if (question) QuestionBadge(size = 22.dp)
                                if (update) UpdateBadge(size = 22.dp)
                            }
                        }
                    }
                }
            }
            val details: @Composable ColumnScope.() -> Unit = {
                Text(text = game.name, style = MaterialTheme.typography.headlineLarge)
                Row(Modifier.tourTarget(TourIds.GAME_LABELS), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The VR / flat label only means something where VR exists.
                    game.androidBuild?.isVr?.takeIf { vrDevice }?.let { vr ->
                        GlassChip(stringResource(if (vr) R.string.game_kind_vr else R.string.game_kind_flat))
                    }
                    GlassChip(
                        stringResource(if (game.ownership == Ownership.OWNED) R.string.game_owned else R.string.game_family_shared),
                    )
                    when (game.kind) {
                        AppKind.DEMO -> GlassChip(stringResource(R.string.game_kind_demo), accent = KindBlue)
                        AppKind.BETA -> GlassChip(stringResource(R.string.game_kind_beta), accent = KindBlue)
                        AppKind.GAME -> Unit
                    }
                    CompatChips(compat, incompatible)
                }
                CompatDetails(compat, incompatible)
                FlowRow(
                    // While an install goes on, the card is taller than the buttons: the cover keeps the place it has with the buttons (one row high), it does not follow the card.
                    modifier = Modifier.onGloballyPositioned { alignment.actionsPlaced(it, if (install.stage != null) with(density) { ACTION_HEIGHT.toPx() } else null) },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.tourTarget(TourIds.GAME_ACTIONS)) { InstallActions(
                        install = install,
                        speedUnit = speedUnit,
                        onInstall = { if (compat?.level == app.gameport.core.model.CompatLevel.FAILS) warning = true else startInstall() },
                        onResume = { onInstall(null) },
                        onCancel = onCancel,
                    onPause = onPause,
                    onVersionChosen = onVersionChosen,
                    onDuplicateChosen = onDuplicateChosen,
                        onDiscard = onDiscard,
                        onUninstall = onUninstall,
                        // Play plays. When a new version or a newer patch is waiting, the player is asked first what to do: play as it is, update, patch again.
                        onPlay = { if (updatePending || patchPending) choosingPlay = true else onPlay() },
                        onPatchAndPlay = onPatchAndPlay,
                        patchOutdated = false,
                        channel = channelInUse.takeIf { name -> name != UpdateChannels.NORMAL && game.androidBuild?.channels?.any { it.name == name } == true },
                        busy = repatch is Repatch.Running,
                        onReport = { reporting = true },
                        extras = extras,
                    ) }
                }
                // A patch or an update of the installed game goes through the steps of an install, in the same card.
                (repatch as? Repatch.Running)?.let { Progress(state = it.asInstallState(), speedUnit = speedUnit, extras = emptyList(), onCancel = null) }
                // The notifications are in the card of the bell, the progress of a patch in the card of the install; what stays here is the report that can be sent, and a patch that failed.
                val suspected = issues.filterIsInstance<GameIssue.ProblemSuspected>().firstOrNull()
                if (install is InstallState.Installed && (suspected != null || repatch is Repatch.Failed)) {
                    Box(Modifier.tourTarget(TourIds.GAME_ISSUES)) { IssuesPanel(
                        problem = suspected,
                        repatch = repatch,
                        updateAvailable = GameIssue.UpdateAvailable in issues,
                        onRepatch = onRepatch,
                        onUpdate = onUpdate,
                        onDismissRepatchError = onDiscard,
                        onReport = { reporting = true },
                        onDismissProblem = report.onDismissProblem,
                    ) }
                }
                // A game without achievements, or one Steam has not answered for yet, shows nothing here.
                achievements?.takeIf { it.items.isNotEmpty() }?.let { AchievementsCard(it, onOpenAchievements) }
                // The pictures and the trailer of the game, as its page in the shop shows them, in a card like the one above.
                media?.takeIf { !it.isEmpty }?.let { MediaCard(game.appId, it) }
            }
            val installed = install is InstallState.Installed
            if (compact) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // It takes half its height in the layout; the other half lies over the artwork.
                    cover(Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height / 2) { placeable.place(0, -placeable.height / 2) }
                    })
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), content = details)
                    // Size and play time are not what the player came for: after everything else.
                    InfoCard(game, installed, playtime, Modifier.fillMaxWidth().tourTarget(TourIds.GAME_INFO))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).onGloballyPositioned { alignment.rowPlaced(it) },
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // The cover straddles the artwork; what is under it (size, play time) follows it up, and down by what lines it up with the buttons.
                    Column(
                        Modifier.pullUp(COVER_HEIGHT / 2).padding(top = with(density) { coverShift.toDp() }).width(COVER_WIDTH),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        cover(Modifier)
                        InfoCard(game, installed, playtime, Modifier.fillMaxWidth().tourTarget(TourIds.GAME_INFO))
                    }
                    Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = details)
                }
            }
        }
    }
    // The actions of the game: opened from the button at the top right, over the page, as the filters are on the home.
    GameActionsOverlay(
        open = actionsOpen,
        onClose = { actionsOpen = false },
        usable = install is InstallState.Installed && repatch !is Repatch.Running,
        canReport = install is InstallState.Installed,
        // A game that is not there yet has no saves, controls or options to open, and nothing to uninstall: only what concerns the game in the list remains.
        showGameActions = install is InstallState.Installed || install.stage != null,
        favorite = favorite,
        hidden = hidden,
        onToggleFavorite = onToggleFavorite,
        onToggleHidden = { if (hidden) onSetHidden(false) else confirmingHide = true },
        savesNotSynced = savesNotSynced,
        hasWorkshop = game.androidBuild?.hasWorkshop == true,
        controllerProfile = controllerProfile,
        controllerTint = when {
            controllerFrame -> ControllerForcedColor
            controllerAutomatic -> KindBlue
            else -> LocalContentColor.current
        },
        compat = vrDevice && game.androidBuild?.isVr != false,
        compatNotice = compatNotice,
        onOpenSaves = onOpenSaves,
        onOpenWorkshop = onOpenWorkshop,
        onOpenStore = onOpenStore,
        onOpenControllers = onOpenControllers,
        onOpenCompat = { onCompatNoticeSeen(); onOpenCompat() },
        onReport = { reporting = true },
        onUninstall = onUninstall,
    )
    }
}

/** Below this width the details sit under the cover instead of beside it. */
private val COMPACT_WIDTH = 600.dp

/** The card of the notifications never gets wider than this: a long line wraps instead of stretching the card across a tall, narrow window. */
private val NOTES_MAX_WIDTH = 420.dp
/** How far the content starts over the artwork's bottom edge. */
private val CONTENT_OVERLAP = 98.dp

/** The slot the artwork takes at the top of the page, whatever its height setting. */
private val HERO_HEIGHT = 320.dp

/** The height of the round buttons of the action row; the main button matches it so the row has one baseline. */
private val ACTION_HEIGHT = 48.dp
private val COVER_WIDTH = 190.dp
private val COVER_HEIGHT = 285.dp

@Composable
private fun InstallActions(
    install: InstallState,
    speedUnit: SpeedUnit,
    onInstall: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onVersionChosen: (String?) -> Unit,
    onDuplicateChosen: (Boolean) -> Unit,
    onDiscard: () -> Unit,
    onUninstall: () -> Unit,
    onPlay: () -> Unit,
    onPatchAndPlay: () -> Unit = {},
    patchOutdated: Boolean = false,
    busy: Boolean = false,
    onReport: () -> Unit = {},
    extras: List<ExtraPlanItem> = emptyList(),
    /** The channel (branch) of Steam the installed game follows when it is not the normal one: the play button names it. */
    channel: String? = null,
) {
    val installContext = LocalContext.current
    when (install) {
        InstallState.NotInstalled -> Button(onClick = onInstall, modifier = Modifier.widthIn(min = 240.dp).height(ACTION_HEIGHT)) {
            Text(stringResource(R.string.game_install))
        }
        InstallState.Interrupted -> FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onResume) { Text(stringResource(R.string.game_resume)) }
            DangerButton(onClick = onDiscard) { Text(stringResource(R.string.game_discard)) }
        }
        InstallState.Queued -> Progress(state = install, speedUnit = speedUnit, extras = extras, onCancel = onCancel)
        // The game is installed and its extra content is being put in place: the same place and the same steps, the game not offered to play yet.
        is InstallState.ExtraContent -> Progress(state = install, speedUnit = speedUnit, extras = extras, onCancel = onCancel)
        is InstallState.Downloading -> Progress(state = install, speedUnit = speedUnit, extras = extras, onCancel = onCancel, onPause = onPause)
        is InstallState.ChoosingVersion -> {
            VersionDialog(install.options, onChosen = onVersionChosen)
            Text(stringResource(R.string.game_choose_version_waiting), style = MaterialTheme.typography.bodyMedium)
        }
        is InstallState.ChoosingDuplicate -> {
            DuplicateDialog(otherGamePort = install.otherGamePort, onChosen = onDuplicateChosen)
            Text(stringResource(R.string.game_choose_version_waiting), style = MaterialTheme.typography.bodyMedium)
        }
        InstallState.Patching, InstallState.Installing, InstallState.Finishing -> Progress(state = install, speedUnit = speedUnit, extras = extras, onCancel = null)
        is InstallState.Installed -> if (install.otherGamePort != null) {
            // The game was patched by another GamePort of this device: it starts and updates it, this one leaves it alone.
            Text(stringResource(R.string.game_other_gameport, install.otherGamePort!!), color = Color(0xFFFF9800), style = MaterialTheme.typography.bodyMedium)
        } else FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = if (patchOutdated) onPatchAndPlay else onPlay, enabled = !busy, modifier = Modifier.height(ACTION_HEIGHT)) {
                Icon(if (patchOutdated) Icons.Filled.Build else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                // On a channel other than the normal one, the button says which.
                Text(if (channel != null && !patchOutdated) stringResource(R.string.game_play_channel, channel) else stringResource(if (patchOutdated) R.string.game_patch_and_play else R.string.game_play))
            }
            // Uninstalling is in the actions (the red button at the bottom of the panel).
        }
        is InstallState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = installErrorText(install.error),
                color = MaterialTheme.colorScheme.error,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (install.error == InstallError.ExpansionStoragePermission || install.error == InstallError.ExpansionFiles) {
                    OutlinedButton(onClick = { ExpansionStorageAccess.openSettings(installContext) }) { Text(stringResource(app.gameport.core.designsystem.R.string.game_error_expansion_storage_open)) }
                }
                Button(onClick = onInstall) { Text(stringResource(R.string.game_retry)) }
                // Removes whatever was downloaded, so a failed attempt never has to keep gigabytes.
                DangerButton(onClick = onDiscard) { Text(stringResource(R.string.game_discard)) }
                // Not offered when the player can put it right at once (space, sign-in, connection): the message says what to do.
                if (install.error.reportable) OutlinedButton(onClick = onReport) { Text(stringResource(R.string.report_open)) }
            }
        }
    }
}

private val CardShape = RoundedCornerShape(16.dp)

/** The height of the buttons of the actions panel: finer than the round buttons of the page, so the list stays short. */
private val ACTION_ROW_HEIGHT = 40.dp

/**
 * The actions of the game, in a panel that slides in from the right over the page, as the filters do on the home: the buttons all one size, the red one at the bottom. Tapping outside
 * the panel, or back, closes it. Greyed while an install goes on (the buttons are there, they cannot be used yet).
 */
@Composable
private fun GameActionsOverlay(
    open: Boolean,
    onClose: () -> Unit,
    usable: Boolean,
    canReport: Boolean,
    showGameActions: Boolean,
    favorite: Boolean,
    hidden: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit,
    savesNotSynced: Boolean,
    hasWorkshop: Boolean,
    controllerProfile: Boolean,
    controllerTint: Color,
    compat: Boolean,
    compatNotice: Boolean,
    onOpenSaves: () -> Unit,
    onOpenWorkshop: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenControllers: () -> Unit,
    onOpenCompat: () -> Unit,
    onReport: () -> Unit,
    onUninstall: () -> Unit,
) {
    BackHandler(enabled = open, onBack = onClose)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp, top = 24.dp, bottom = 24.dp),
        ) {
            val shape = RoundedCornerShape(28.dp)
            Column(
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(0.92f)
                    .shadow(24.dp, shape)
                    // The same gradient as every page (as the player chose it), not a colour of its own.
                    .background(Brush.linearGradient(LocalBackdropColors.current.toList()), shape)
                    .border(1.dp, GlassBorder, shape)
                    // A tap on the panel must not reach the dimmed area behind it, which closes it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.game_actions), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    GlassIconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.game_actions_close)) }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showGameActions) {
                        // The saves on this device and on Steam do not agree: the page of the saves says which. The same mark sits on the button that opens this panel.
                        ActionRow(Icons.Filled.CloudSync, stringResource(R.string.saves_title), usable, { onClose(); onOpenSaves() }, badge = Color(0xFFFF9800).takeIf { savesNotSynced })
                        // A game that has a Workshop on Steam: its page opens here, with the account already signed in, and subscribing downloads the item for the game.
                        if (hasWorkshop) ActionRow(Icons.Filled.Extension, stringResource(R.string.workshop_button), usable, { onClose(); onOpenWorkshop() })
                        if (controllerProfile) ActionRow(Icons.Filled.SportsEsports, stringResource(R.string.controllers_title), usable, { onClose(); onOpenControllers() }, tint = controllerTint)
                        // The options that make a game work: orange as the compatibility labels, with a small mark when GamePort adapted the game by itself.
                        if (compat) ActionRow(Icons.Filled.Build, stringResource(R.string.gamecompat_button), usable, { onClose(); onOpenCompat() }, tint = CompatOrange, badge = CompatOrange.takeIf { compatNotice })
                        ActionRow(Icons.Filled.BugReport, stringResource(R.string.report_open), canReport, { onClose(); onReport() })
                        Spacer(Modifier.height(8.dp))
                    }
                    // The page of the game in the shop of the application (its pictures, its extra content, its link on Steam): for any game, installed or not.
                    ActionRow(Icons.Filled.Storefront, stringResource(R.string.game_open_store), true, { onClose(); onOpenStore() })
                    ActionRow(
                        Icons.Filled.Favorite,
                        stringResource(if (favorite) R.string.game_unfavorite else R.string.game_favorite),
                        true,
                        { onToggleFavorite() },
                        tint = if (favorite) Color(0xFFE53935) else LocalContentColor.current,
                    )
                    // Hiding has the colour it has in the menu of a cover; showing again keeps the usual one.
                    ActionRow(
                        if (hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        stringResource(if (hidden) R.string.game_show_again else R.string.game_hide),
                        true,
                        { onClose(); onToggleHidden() },
                        tint = if (hidden) LocalContentColor.current else HideRed,
                    )
                }
                if (showGameActions) {
                    DangerButton(onClick = { onClose(); onUninstall() }, enabled = usable, modifier = Modifier.fillMaxWidth().height(ACTION_ROW_HEIGHT)) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.game_uninstall), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * The notifications of the game in one card under the bell, an arrow on its top edge pointing at the bell ([arrowFromRightPx] is how far from the right edge the bell's centre is).
 * One line for each, as wide as the card, its words from the left; tapping a line opens the page it is about. The cross closes the card, the bell opens it again.
 */
@Composable
private fun NotificationCard(notes: List<NoteItem>, title: String, closeLabel: String, readLabel: String, arrowFromRightPx: Float, onClose: () -> Unit) {
    val colors = LocalBackdropColors.current.toList()
    val arrowHeight = 11.dp
    val fromRight = with(LocalDensity.current) { arrowFromRightPx.toDp() }
    // One shape for the card and its arrow: the edge is drawn once, all around, so nothing separates the arrow from the card.
    val bubble = remember(fromRight) { BubbleShape(corner = 16.dp, arrowWidth = 22.dp, arrowHeight = arrowHeight, arrowFromRight = fromRight) }
    Column(
        // At least as wide as the arrow's place needs: the arrow stands on the straight part of the top edge, never on the rounded corner.
        Modifier
            .widthIn(min = fromRight + 36.dp, max = maxOf(NOTES_MAX_WIDTH, fromRight + 36.dp))
            .width(IntrinsicSize.Max)
            .background(Brush.linearGradient(colors), bubble)
            .background(GlassFill, bubble)
            .border(1.dp, GlassBorder, bubble)
            .padding(start = 12.dp, end = 12.dp, top = arrowHeight + 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            GlassIconButton(onClick = onClose, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Close, contentDescription = closeLabel, modifier = Modifier.size(16.dp)) }
        }
        notes.forEach { note ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).let { base -> note.onClick?.let { base.clickable(onClick = it) } ?: base }.padding(horizontal = 4.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(note.dot))
                    Text(note.text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = note.textColor)
                    // A note that leads somewhere has a chevron, one that does something has its button; one with two buttons has them under its words.
                    if (note.secondAction == null) {
                        if (note.action != null) NoteButton(note.action, note.actionEnabled, note.onClick ?: {})
                        else if (note.onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    // A notice that only informs can be marked as read: the line goes and does not come back.
                    note.onRead?.let { GlassIconButton(onClick = it, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Check, contentDescription = readLabel, modifier = Modifier.size(16.dp)) } }
                }
                if (note.secondAction != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        NoteButton(note.secondAction, true, note.onSecond)
                        note.action?.let { NoteButton(it, note.actionEnabled, note.onClick ?: {}) }
                    }
                }
            }
        }
    }
}

/** A small button of a notification: fine, with its words in the small size. */
@Composable
private fun NoteButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp), modifier = Modifier.height(28.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** The screenshots and trailers of the game in a card of the same kind as the achievements': a title, then the thumbnails in a row, which open the viewer. */
@Composable
private fun MediaCard(appId: Int, media: app.gameport.core.model.AppMedia) {
    Column(Modifier.fillMaxWidth().glass(CardShape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(26.dp))
            Text(stringResource(R.string.game_media_title), style = MaterialTheme.typography.titleMedium)
        }
        app.gameport.core.designsystem.MediaGallery(appId, media)
    }
}

/** A rounded card with a small arrow on its top edge, [arrowFromRight] from the right edge to the arrow's centre, as one outline. */
private class BubbleShape(private val corner: Dp, private val arrowWidth: Dp, private val arrowHeight: Dp, private val arrowFromRight: Dp) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        val r = with(density) { corner.toPx() }
        val aw = with(density) { arrowWidth.toPx() }
        val ah = with(density) { arrowHeight.toPx() }
        val centre = size.width - with(density) { arrowFromRight.toPx() }
        val top = ah
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(r, top)
            lineTo(centre - aw / 2f, top)
            lineTo(centre, 0f)
            lineTo(centre + aw / 2f, top)
            lineTo(size.width - r, top)
            arcTo(androidx.compose.ui.geometry.Rect(size.width - 2 * r, top, size.width, top + 2 * r), -90f, 90f, false)
            lineTo(size.width, size.height - r)
            arcTo(androidx.compose.ui.geometry.Rect(size.width - 2 * r, size.height - 2 * r, size.width, size.height), 0f, 90f, false)
            lineTo(r, size.height)
            arcTo(androidx.compose.ui.geometry.Rect(0f, size.height - 2 * r, 2 * r, size.height), 90f, 90f, false)
            lineTo(0f, top + r)
            arcTo(androidx.compose.ui.geometry.Rect(0f, top, 2 * r, top + 2 * r), 180f, 90f, false)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

/** A button of the actions panel: the same size as the others, its icon and its words, and a small mark on its corner when [badge] is given. */
@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, tint: Color = LocalContentColor.current, badge: Color? = null) {
    Box(Modifier.fillMaxWidth()) {
        GlassButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(ACTION_ROW_HEIGHT)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            // The words start at the left, under one another.
            Text(label, Modifier.weight(1f))
        }
        if (badge != null) Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).size(12.dp).clip(CircleShape).background(badge))
    }
}

/** The steps an install goes through, with the current one highlighted, what it is doing, and its progress: the same as on the downloads page, in a card of its own. */
@Composable
private fun Progress(state: InstallState, speedUnit: SpeedUnit, extras: List<ExtraPlanItem>, onCancel: (() -> Unit)?, onPause: (() -> Unit)? = null) {
    val stage = state.stage ?: return
    // Where each extra content stands: the ones in the game's download are in place when the game is downloaded, the others are fetched one after the other once it is installed.
    val rows = extraStands(extras, stage, (state as? InstallState.ExtraContent)?.index)
    val inPlace = rows.count { it.second == Stand.DONE }
    val pending = rows.filter { it.second != Stand.DONE }
    val shown = pending.take(EXTRAS_SHOWN)
    val gameInPlace = state is InstallState.ExtraContent
    Column(Modifier.fillMaxWidth().glass(CardShape).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InstallStepper(stage, Modifier.weight(1f))
            // Both stay where they are for the whole install, greyed when they cannot be used in this step: the card keeps its height from one step to the next.
            val small = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)
            androidx.compose.material3.OutlinedButton(onClick = { onPause?.invoke() }, enabled = onPause != null, contentPadding = small, modifier = Modifier.height(32.dp)) {
                Text(stringResource(R.string.game_pause), style = MaterialTheme.typography.labelLarge)
            }
            DangerButton(onClick = { onCancel?.invoke() }, enabled = onCancel != null, contentPadding = small, modifier = Modifier.height(32.dp)) {
                Text(stringResource(R.string.game_cancel), style = MaterialTheme.typography.labelLarge)
            }
        }
        // The game, then the extra content: the one being put in place has its bar, the game is greyed once it is in place.
        InstallRow(
            name = stringResource(R.string.install_game),
            stand = if (gameInPlace) Stand.DONE else Stand.ACTIVE,
            progress = (state as? InstallState.Downloading)?.progress,
            status = if (gameInPlace) null else app.gameport.core.designsystem.installStatusText(state, speedUnit),
            dimmed = gameInPlace,
        )
        shown.forEach { (item, stand) ->
            InstallRow(
                name = item.name,
                stand = stand,
                progress = if (stand == Stand.ACTIVE) (state as? InstallState.ExtraContent)?.progress else null,
                status = when {
                    stand == Stand.ACTIVE -> "${(((state as? InstallState.ExtraContent)?.progress ?: 0f) * 100).toInt()}%"
                    item.withGame -> stringResource(R.string.install_extra_with_game)
                    else -> null
                },
                dimmed = false,
            )
        }
        val more = pending.size - shown.size
        if (inPlace > 0 || more > 0) {
            Text(
                listOfNotNull(
                    if (inPlace > 0) stringResource(R.string.install_extras_in_place, inPlace) else null,
                    if (more > 0) stringResource(R.string.install_extras_more, more) else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** How many of the extra content still to come are named in the card; the rest are counted. */
private const val EXTRAS_SHOWN = 2

private val InstallGreen = Color(0xFF4CAF50)

/** One line of the card of an install: the mark of where it stands, its name, and its bar and words when it is going on. */
@Composable
private fun InstallRow(name: String, stand: Stand, progress: Float?, status: String?, dimmed: Boolean) {
    // Its name on the first line; when it is going on, its bar and, under the bar, what it is doing in thin letters. Nothing sits at the right, so nothing has to line up.
    val thin = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Light)
    Column(Modifier.fillMaxWidth().alpha(if (dimmed) 0.55f else 1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                when (stand) {
                    // Green with a white tick once in place.
                    Stand.DONE -> Box(Modifier.size(20.dp).clip(CircleShape).background(InstallGreen), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    Stand.ACTIVE -> Box(Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                    Stand.WAITING -> Box(Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)))
                }
            }
            Text(name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            // Not started: what it is waiting for, right after its name.
            if (stand != Stand.ACTIVE && status != null) Text(status, style = thin, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (stand == Stand.ACTIVE) {
            Column(Modifier.padding(start = 30.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                if (status != null) Text(status, style = thin, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        } else if (stand == Stand.DONE && dimmed) {
            LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth().padding(start = 30.dp))
        }
    }
}

/** Asked when a download holds several builds and none clearly fits this device. */
@Composable
private fun VersionDialog(options: List<VersionOption>, onChosen: (String?) -> Unit) {
    BackdropDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.game_choose_version_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.game_choose_version_message))
                options.forEach { option ->
                    val kind = stringResource(if (option.forHeadset) R.string.game_version_headset else R.string.game_version_flat)
                    OutlinedButton(onClick = { onChosen(option.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("$kind · ${option.versionName ?: option.versionCode}")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { DangerTextButton(onClick = { onChosen(null) }) { Text(stringResource(R.string.game_cancel)) } },
    )
}

/** A copy of the game that GamePort did not install is on the device: keep it, or uninstall it so this version can be installed. */
@Composable
private fun DuplicateDialog(otherGamePort: String?, onChosen: (Boolean) -> Unit) {
    BackdropDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.game_duplicate_title)) },
        text = { Text(if (otherGamePort != null) stringResource(R.string.game_duplicate_other_gameport, otherGamePort) else stringResource(R.string.game_duplicate_message)) },
        confirmButton = {
            DangerButton(onClick = { onChosen(true) }) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.game_duplicate_replace))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { onChosen(false) }) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.game_duplicate_keep))
            }
        },
    )
}

/** Asked when Install is pressed: which owned extras to add. Nothing is ticked by default. */
@Composable
private fun DlcDialog(game: Game, dlc: List<DlcContent>, onDismiss: () -> Unit, onConfirm: (Set<Int>) -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    val build = game.androidBuild
    val download = (build?.downloadBytes ?: 0L) + dlc.filter { it.appId in selected }.sumOf { it.downloadBytes }
    val installed = (build?.installBytes ?: 0L) + dlc.filter { it.appId in selected }.sumOf { it.installBytes }
    BackdropDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_dlc_title)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                dlc.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selected = if (item.appId in selected) selected - item.appId else selected + item.appId
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = item.appId in selected, onCheckedChange = null)
                        Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                Formatter.formatFileSize(context, item.downloadBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected) }) {
                Text(
                    stringResource(
                        R.string.game_dlc_install,
                        Formatter.formatFileSize(context, download),
                        Formatter.formatFileSize(context, installed),
                    ),
                )
            }
        },
        dismissButton = { DangerTextButton(onClick = onDismiss) { Text(stringResource(R.string.game_cancel)) } },
    )
}

/**
 * What needs the player's attention on this game, each with the way to deal with it. Patching or
 * updating the game runs here too: its progress replaces the row and every button is greyed out.
 */
@Composable
private fun IssuesPanel(
    problem: GameIssue.ProblemSuspected?,
    repatch: Repatch,
    updateAvailable: Boolean,
    onRepatch: () -> Unit,
    onUpdate: () -> Unit,
    onDismissRepatchError: () -> Unit,
    onReport: () -> Unit,
    onDismissProblem: () -> Unit,
) {
    // As wide as the achievements card beside it in the column.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // (The progress of a patch is in the card of the install, above.)
        if (repatch is Repatch.Failed) {
            IssueRow(
                message = stringResource(R.string.issue_patch_failed, installErrorText(repatch.error)),
                action = stringResource(R.string.game_retry),
                onAction = if (updateAvailable) onUpdate else onRepatch,
                secondAction = stringResource(R.string.issue_dismiss),
                onSecondAction = onDismissRepatchError,
            )
        }
        // A problem the game seems to have had: the report that can be sent is offered here, on the page (the rest of what is said about the game is in the notifications).
        problem?.let {
            IssueRow(
                stringResource(if (it.crash) R.string.issue_problem_crash else R.string.issue_problem_short),
                stringResource(R.string.issue_problem_action),
                onReport,
                secondAction = stringResource(R.string.issue_dismiss),
                onSecondAction = onDismissProblem,
            )
        }
    }
}

@Composable
private fun IssueRow(
    message: String,
    action: String? = null,
    onAction: () -> Unit = {},
    enabled: Boolean = true,
    progress: Boolean = false,
    progressFraction: Float? = null,
    /** A notice rather than a problem: a calm icon instead of the orange warning. */
    info: Boolean = false,
    /** A new version: a green arrow instead of the orange warning. */
    update: Boolean = false,
    /** A question for the player: a blue question mark instead of the orange warning. */
    question: Boolean = false,
    secondAction: String? = null,
    onSecondAction: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val badge: @Composable () -> Unit = {
                when {
                    info -> Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = Color(0xFF90CAF9))
                    question -> QuestionBadge()
                    update -> UpdateBadge()
                    else -> AttentionBadge()
                }
            }
            val buttons: @Composable () -> Unit = {
                if (secondAction != null) GlassButton(onClick = onSecondAction) { Text(secondAction) }
                if (action != null) GlassButton(onClick = onAction, enabled = enabled) { Text(action) }
            }
            if (maxWidth < 520.dp) {
                // On a narrow screen (a phone) the buttons go under the message, instead of squeezing it into a thin column.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        badge()
                        Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (secondAction != null || action != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) { buttons() }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    badge()
                    Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    buttons()
                }
            }
        }
        if (progress) {
            if (progressFraction != null) LinearProgressIndicator(progress = { progressFraction }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

/** The name of the controllers a game was written for: "Steam Frame" or "Quest". */
@Composable
internal fun sourceFamilyName(source: String): String =
    stringResource(if (source == "touch") R.string.controllers_family_touch else R.string.controllers_family_valve)

/**
 * Keeps the cover's bottom edge level with the bottom of the action buttons. The cover is placed [halfCoverPx] above the top of the row
 * and straddles it, so how far it must be lowered follows from the bottom of the buttons alone: it does not depend on where the cover
 * is, which is what keeps this from chasing itself while the buttons change (a download updates them all the time). The place of the
 * buttons is read in the coordinates of the row, so scrolling the page changes nothing. [shift] is that distance, in pixels.
 */
private class CoverAlignment(private val halfCoverPx: Float) {
    var shift by mutableFloatStateOf(0f)
        private set
    private var row: LayoutCoordinates? = null
    private var actions: LayoutCoordinates? = null

    fun rowPlaced(coordinates: LayoutCoordinates) { row = coordinates; update() }

    /** [nominalHeightPx]: the height the buttons are taken to have, when what is there is taller for a while (the card of an install): the cover then stays where it is. */
    fun actionsPlaced(coordinates: LayoutCoordinates, nominalHeightPx: Float? = null) { actions = coordinates; actionsHeight = nominalHeightPx; update() }

    private var actionsHeight: Float? = null

    private fun update() {
        val inRow = row?.takeIf { it.isAttached } ?: return
        val buttons = actions?.takeIf { it.isAttached } ?: return
        val bottom = inRow.localPositionOf(buttons, Offset(0f, actionsHeight ?: buttons.size.height.toFloat())).y
        // Only ever lowered: when the buttons end above the cover's bottom the cover stays where it is.
        val wanted = (bottom - halfCoverPx).coerceAtLeast(0f)
        if (abs(wanted - shift) > 1f) shift = wanted
    }
}

/**
 * A soft shadow drawn by stacking rounded rectangles that grow and fade, so it needs no blur. Three of them make a card
 * float: a tight dark one right under its edge, a wide soft one lower down, and a very wide faint one lower still.
 */
private fun Modifier.liftedShadow(corner: Dp): Modifier = drawBehind {
    fun layer(spread: Dp, drop: Dp, strength: Float) {
        val steps = 24
        val reach = spread.toPx()
        val lower = drop.toPx()
        val radius = corner.toPx()
        // Each step alone is faint; together they reach [strength] under the card and fade out to nothing at [reach].
        val each = 1f - (1f - strength).pow(1f / steps)
        for (i in 0 until steps) {
            val grow = reach * (1f - i / steps.toFloat()).pow(1.6f) - reach * 0.25f
            drawRoundRect(
                color = Color.Black.copy(alpha = each),
                topLeft = Offset(-grow, -grow + lower),
                size = Size(size.width + 2 * grow, size.height + 2 * grow),
                cornerRadius = CornerRadius((radius + grow).coerceAtLeast(0f)),
            )
        }
    }
    layer(spread = 70.dp, drop = 34.dp, strength = 0.38f)
    layer(spread = 30.dp, drop = 16.dp, strength = 0.5f)
    layer(spread = 8.dp, drop = 3.dp, strength = 0.6f)
}

/** Moves its content up by [amount] and gives that space back to the layout, so what follows is not pushed down by it. */
private fun Modifier.pullUp(amount: Dp): Modifier = layout { measurable, constraints ->
    val up = amount.roundToPx()
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height - up).coerceAtLeast(0)) { placeable.place(0, -up) }
}

/**
 * The facts about the game that are not what the player came for: how big it is, and how long it was
 * played (here, and on the Steam account once Steam answered). Sits under the cover.
 */
@Composable
private fun InfoCard(game: Game, installed: Boolean, playtime: Playtime, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val build = game.androidBuild
    val none = stringResource(R.string.game_info_unknown)
    Column(modifier.glass(RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoGroup(stringResource(R.string.game_info_size), Icons.Filled.Storage) {
            InfoItem(stringResource(R.string.game_info_download), build?.downloadBytes?.let { Formatter.formatFileSize(context, it) } ?: none)
            InfoItem(stringResource(R.string.game_info_installed), build?.installBytes?.let { Formatter.formatFileSize(context, it) } ?: none)
        }
        if (installed) {
            InfoGroup(stringResource(R.string.game_info_playtime), Icons.Filled.Schedule) {
                InfoItem(stringResource(R.string.game_info_played_device), durationText((playtime.deviceMillis / 60_000L).toInt()))
                playtime.steamMinutes?.let { InfoItem(stringResource(R.string.game_info_played_steam), durationText(it)) }
            }
        }
    }
}

@Composable
private fun InfoGroup(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        content()
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    // Side by side when they fit, the value under its label when it does not.
    FlowRow(horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun durationText(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.game_duration_none)
    minutes < 60 -> stringResource(R.string.game_duration_minutes, minutes)
    else -> stringResource(R.string.game_duration_hours, minutes / 60, minutes % 60)
}

private val CompatGreen = Color(0xFF66BB6A)
private val CompatOrange = Color(0xFFFFB74D)
private val CompatRed = Color(0xFFE57373)

/** The label of what is known about the game, in colour. */
@Composable
private fun CompatChips(compat: app.gameport.core.model.Compat?, incompatible: app.gameport.core.model.IncompatibleReason?) {
    when {
        // A game known not to run says so, whatever the players said.
        incompatible != null -> CompatChip(stringResource(R.string.game_compat_incompatible), CompatRed, Glyph.CROSS)
        compat != null -> when (compat.level) {
            app.gameport.core.model.CompatLevel.WORKS -> CompatChip(stringResource(R.string.game_compat_works, deviceName(compat.device)), CompatGreen, Glyph.CHECK)
            app.gameport.core.model.CompatLevel.OFFLINE_ONLY -> CompatChip(stringResource(R.string.game_compat_offline_only, deviceName(compat.device)), CompatOrange, Glyph.NO_NETWORK)
            app.gameport.core.model.CompatLevel.MIXED -> CompatChip(stringResource(R.string.game_compat_mixed, deviceName(compat.device)), CompatOrange, Glyph.EXCLAMATION)
            app.gameport.core.model.CompatLevel.FAILS -> CompatChip(stringResource(R.string.game_compat_fails, deviceName(compat.device)), CompatRed, Glyph.CROSS)
        }
    }
}

/** A label of what is known about the game: its colour, and before the words the mark of the same colour, as tall as the capitals of the text. */
@Composable
private fun CompatChip(label: String, colour: Color, glyph: Glyph) {
    val capitals = with(LocalDensity.current) { (MaterialTheme.typography.labelLarge.fontSize * CAPITAL_HEIGHT).toDp() }
    GlassChip(label, accent = colour, leading = { CompatGlyph(glyph, colour, capitals) })
}

/** The height of a capital letter, as a share of the size of the font. */
private const val CAPITAL_HEIGHT = 0.72f

/** What stands behind the label: why the game is incompatible, that players tried it with the offline mode, or that few answered so far. No numbers: the label says it. */
@Composable
private fun CompatDetails(compat: app.gameport.core.model.Compat?, incompatible: app.gameport.core.model.IncompatibleReason?) {
    val language = LocalConfiguration.current.locales[0].language
    val style = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        incompatible != null -> IncompatibleNote(incompatible.title.pick(language), incompatible.text.pick(language))
        compat != null && compat.offlineTested -> Text(stringResource(R.string.game_compat_offline), style = style, color = muted)
        // (A result with few answers is not told here: it is not taken into account.)
    }
}

/** Why the game cannot run: in red, with a cross, so it is the first thing seen on the page of a game that cannot be played. */
@Composable
private fun IncompatibleNote(title: String, text: String) {
    val shape = RoundedCornerShape(16.dp)
    val capitals = with(LocalDensity.current) { (MaterialTheme.typography.titleSmall.fontSize * CAPITAL_HEIGHT).toDp() }
    Row(
        Modifier.fillMaxWidth().glass(shape).border(1.dp, CompatRed.copy(alpha = 0.7f), shape).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 3.dp)) { CompatGlyph(Glyph.CROSS, CompatRed, capitals) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CompatRed)
            Text(text.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The kind of device as it is written, in the words of the player's language. */
@Composable
private fun deviceName(kind: String): String = stringResource(
    when (kind) {
        "quest" -> R.string.game_device_quest
        "pico" -> R.string.game_device_pico
        "phone" -> R.string.game_device_phone
        "tablet" -> R.string.game_device_tablet
        else -> R.string.game_device_other
    },
)

/** The controllers button when the player forced the Steam Frame's controls. */
private val ControllerForcedColor = Color(0xFFFFB74D)
