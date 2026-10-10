@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.gameport.feature.game

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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.runtime.Composable
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
import app.gameport.core.model.GameIssue
import app.gameport.core.model.DisplaySettings
import app.gameport.core.model.InstallError
import app.gameport.core.model.AchievementList
import app.gameport.core.model.InstallState
import app.gameport.core.model.SpeedUnit
import app.gameport.core.model.Ownership

@Composable
fun GameScreen(onBack: () -> Unit, onOpenSettings: () -> Unit, onOpenSaves: () -> Unit, onOpenControllers: () -> Unit, onOpenCompat: () -> Unit, onOpenAchievements: () -> Unit, onOpenSteamSettings: () -> Unit, viewModel: GameViewModel = hiltViewModel()) {
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
        onSend = { shownGame?.let(viewModel::onSendReport) },
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
        achievements = achievements,
        onOpenAchievements = onOpenAchievements,
        artworkHeight = artworkHeight,
        onToggleFavorite = viewModel::onToggleFavorite,
        onSetHidden = viewModel::onSetHidden,
        onBack = onBack,
        onInstall = viewModel::onInstall,
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
    onToggleFavorite: () -> Unit,
    onSetHidden: (Boolean) -> Unit,
    onBack: () -> Unit,
    onInstall: (Game, Set<Int>?) -> Unit,
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
    artworkHeight: Int = DisplaySettings.DEFAULT_GAME_ARTWORK_HEIGHT,
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
                    install = uiState.install,
                    issues = uiState.issues,
                    repatch = uiState.repatch,
                    onRepatch = onRepatch,
                    onUpdate = onUpdate,
                    speedUnit = speedUnit,
                    onInstall = { dlc -> onInstall(uiState.game, dlc) },
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
                    artworkHeight = artworkHeight,
                )
            }
            Row(
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 32.dp, top = 16.dp),
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
    install: InstallState,
    issues: List<GameIssue>,
    repatch: Repatch,
    onRepatch: () -> Unit,
    onUpdate: () -> Unit,
    speedUnit: SpeedUnit,
    onInstall: (Set<Int>?) -> Unit,
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
    onToggleFavorite: () -> Unit,
    onSetHidden: (Boolean) -> Unit,
    onOpenPermissions: () -> Unit,
    onResolveConflict: () -> Unit,
    report: ReportActions,
    achievements: AchievementList? = null,
    onOpenAchievements: () -> Unit = {},
    artworkHeight: Int = DisplaySettings.DEFAULT_GAME_ARTWORK_HEIGHT,
) {
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
    // A game that most players could not run is installed only after the player has been told.
    var warning by remember { mutableStateOf(false) }
    val startInstall = { if (ownedDlc.isEmpty()) onInstall(emptySet()) else choosingDlc = true }
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
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
                        url = game.heroUrl,
                        fallbackUrl = game.heroFallbacks.firstOrNull(),
                        moreFallbacks = game.heroFallbacks.drop(1),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        // The cover straddles the hero: half of it sits on the artwork, half below. On a narrow screen it is
        // centred above the details instead of beside them.
        BoxWithConstraints(Modifier.fillMaxWidth().pullUp(CONTENT_OVERLAP)) {
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
                        url = game.capsuleUrl,
                        fallbackUrl = game.capsuleFallbacks.firstOrNull(),
                        moreFallbacks = game.capsuleFallbacks.drop(1),
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
                        // Orange for what needs attention, green when the only news is an update.
                        val attention = issues.any { it !is GameIssue.ControllerMappingAvailable && it !is GameIssue.ControllerSilent && it !is GameIssue.UpdateAvailable }
                        val update = GameIssue.UpdateAvailable in issues
                        if (attention || update) {
                            Row(Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (attention) AttentionBadge(size = 22.dp)
                                if (update) UpdateBadge(size = 22.dp)
                            }
                        }
                    }
                }
            }
            val details: @Composable ColumnScope.() -> Unit = {
                Text(text = game.name, style = MaterialTheme.typography.headlineLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The VR / flat label only means something where VR exists.
                    game.androidBuild?.isVr?.takeIf { vrDevice }?.let { vr ->
                        GlassChip(stringResource(if (vr) R.string.game_kind_vr else R.string.game_kind_flat))
                    }
                    GlassChip(
                        stringResource(
                            when (game.ownership) {
                                Ownership.OWNED -> R.string.game_owned
                                Ownership.FAMILY_SHARED -> R.string.game_family_shared
                                Ownership.NOT_OWNED -> R.string.game_not_owned
                            },
                        ),
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
                    modifier = Modifier.onGloballyPositioned { alignment.actionsPlaced(it) },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val context = LocalContext.current
                    if (game.ownership == Ownership.NOT_OWNED) {
                        // A game the account does not have is not installed from here: its page on Steam is where it is bought.
                        Button(onClick = { openOnSteam(context, game.appId) }, modifier = Modifier.widthIn(min = 240.dp).height(ACTION_HEIGHT)) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.game_open_steam))
                        }
                    } else InstallActions(
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
                        onPlay = onPlay,
                        onPatchAndPlay = onPatchAndPlay,
                        patchOutdated = GameIssue.PatchOutdated in issues,
                        busy = repatch is Repatch.Running,
                        onReport = { reporting = true },
                    )
                    if (install is InstallState.Installed) {
                        Box {
                            GlassIconButton(onClick = onOpenSaves, enabled = repatch !is Repatch.Running) {
                                Icon(Icons.Filled.CloudSync, contentDescription = stringResource(R.string.saves_title))
                            }
                            // The saves on this device and on Steam do not agree: the page of the saves says which.
                            if (savesNotSynced) {
                                Box(Modifier.align(Alignment.TopEnd).size(12.dp).clip(CircleShape).background(Color(0xFFFF9800)))
                            }
                        }
                    }
                    if (install is InstallState.Installed) {
                        GlassIconButton(onClick = { reporting = true }) {
                            Icon(Icons.Filled.BugReport, contentDescription = stringResource(R.string.report_open))
                        }
                    }
                    if (install is InstallState.Installed && vrDevice && game.androidBuild?.isVr != false) {
                        // The options that make a game work: orange as the compatibility labels, with a small mark when GamePort adapted the game by itself.
                        Box {
                            GlassIconButton(onClick = onOpenCompat, enabled = repatch !is Repatch.Running) {
                                Icon(Icons.Filled.Build, contentDescription = stringResource(R.string.gamecompat_button), tint = CompatOrange)
                            }
                            if (compatNotice) {
                                Box(Modifier.align(Alignment.TopEnd).size(12.dp).clip(CircleShape).background(CompatOrange))
                            }
                        }
                    }
                    if (install is InstallState.Installed && controllerProfile) {
                        GlassIconButton(onClick = onOpenControllers, enabled = repatch !is Repatch.Running) {
                            Icon(
                                Icons.Filled.SportsEsports,
                                contentDescription = stringResource(R.string.controllers_title),
                                tint = when {
                                    controllerFrame -> ControllerForcedColor
                                    controllerAutomatic -> KindBlue
                                    else -> LocalContentColor.current
                                },
                            )
                        }
                    }
                    if (game.ownership != Ownership.NOT_OWNED) {
                        GlassIconButton(onClick = onToggleFavorite) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = stringResource(if (favorite) R.string.game_unfavorite else R.string.game_favorite),
                                tint = if (favorite) Color(0xFFE53935) else LocalContentColor.current,
                            )
                        }
                        GlassIconButton(onClick = { if (hidden) onSetHidden(false) else confirmingHide = true }) {
                            Icon(
                                if (hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = stringResource(if (hidden) R.string.game_show_again else R.string.game_hide),
                                // Hiding has the colour it has in the menu of a cover; showing again keeps the usual one.
                                tint = if (hidden) LocalContentColor.current else HideRed,
                            )
                        }
                        GlassIconButton(onClick = onOpenSettings, enabled = repatch !is Repatch.Running) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.game_settings_title))
                        }
                    }
                }
                if (install is InstallState.Installed && (issues.isNotEmpty() || repatch != Repatch.None)) {
                    IssuesPanel(
                        issues = issues,
                        repatch = repatch,
                        speedUnit = speedUnit,
                        onRepatch = onRepatch,
                        onUpdate = onUpdate,
                        onDismissRepatchError = onDiscard,
                        onPermissions = onOpenPermissions,
                        onConflict = onResolveConflict,
                        onControllers = onOpenControllers,
                        onActivateFrame = onActivateFrame,
                        onReport = { reporting = true },
                        onDismissProblem = report.onDismissProblem,
                    )
                }
                // A game without achievements, or one Steam has not answered for yet, shows nothing here.
                achievements?.takeIf { it.items.isNotEmpty() }?.let { AchievementsCard(it, onOpenAchievements) }
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
                    if (game.ownership != Ownership.NOT_OWNED) InfoCard(game, installed, playtime, Modifier.fillMaxWidth())
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
                        if (game.ownership != Ownership.NOT_OWNED) InfoCard(game, installed, playtime, Modifier.fillMaxWidth())
                    }
                    Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = details)
                }
            }
        }
    }
}

/** Below this width the details sit under the cover instead of beside it. */
private val COMPACT_WIDTH = 600.dp
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
) {
    when (install) {
        InstallState.NotInstalled -> Button(onClick = onInstall, modifier = Modifier.widthIn(min = 240.dp).height(ACTION_HEIGHT)) {
            Text(stringResource(R.string.game_install))
        }
        InstallState.Interrupted -> FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onResume) { Text(stringResource(R.string.game_resume)) }
            DangerButton(onClick = onDiscard) { Text(stringResource(R.string.game_discard)) }
        }
        InstallState.Queued -> Progress(state = install, speedUnit = speedUnit, onCancel = onCancel)
        is InstallState.Downloading -> Progress(state = install, speedUnit = speedUnit, onCancel = onCancel, onPause = onPause)
        is InstallState.ChoosingVersion -> {
            VersionDialog(install.options, onChosen = onVersionChosen)
            Text(stringResource(R.string.game_choose_version_waiting), style = MaterialTheme.typography.bodyMedium)
        }
        is InstallState.ChoosingDuplicate -> {
            DuplicateDialog(otherGamePort = install.otherGamePort, onChosen = onDuplicateChosen)
            Text(stringResource(R.string.game_choose_version_waiting), style = MaterialTheme.typography.bodyMedium)
        }
        InstallState.Patching, InstallState.Installing, InstallState.Finishing -> Progress(state = install, speedUnit = speedUnit, onCancel = null)
        is InstallState.Installed -> if (install.otherGamePort != null) {
            // The game was patched by another GamePort of this device: it starts and updates it, this one leaves it alone.
            Text(stringResource(R.string.game_other_gameport, install.otherGamePort!!), color = Color(0xFFFF9800), style = MaterialTheme.typography.bodyMedium)
        } else FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = if (patchOutdated) onPatchAndPlay else onPlay, enabled = !busy, modifier = Modifier.height(ACTION_HEIGHT)) {
                if (patchOutdated) {
                    Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(if (patchOutdated) R.string.game_patch_and_play else R.string.game_play))
            }
            DangerTrashButton(onClick = onUninstall, contentDescription = stringResource(R.string.game_uninstall), enabled = !busy)
        }
        is InstallState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = installErrorText(install.error),
                color = MaterialTheme.colorScheme.error,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onInstall) { Text(stringResource(R.string.game_retry)) }
                // Removes whatever was downloaded, so a failed attempt never has to keep gigabytes.
                DangerButton(onClick = onDiscard) { Text(stringResource(R.string.game_discard)) }
                // Not offered when the player can put it right at once (space, sign-in, connection): the message says what to do.
                if (install.error.reportable) OutlinedButton(onClick = onReport) { Text(stringResource(R.string.report_open)) }
            }
        }
    }
}

/** The steps an install goes through, with the current one highlighted, what it is doing, and its progress: the same as on the downloads page. */
@Composable
private fun Progress(state: InstallState, speedUnit: SpeedUnit, onCancel: (() -> Unit)?, onPause: (() -> Unit)? = null) {
    val stage = state.stage ?: return
    Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InstallStepper(stage)
        if (state is InstallState.Downloading) LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(Modifier.fillMaxWidth())
        installStatusText(state, speedUnit)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onPause?.let { androidx.compose.material3.OutlinedButton(onClick = it) { Text(stringResource(R.string.game_pause)) } }
            onCancel?.let { DangerButton(onClick = it) { Text(stringResource(R.string.game_cancel)) } }
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
    issues: List<GameIssue>,
    repatch: Repatch,
    speedUnit: SpeedUnit,
    onRepatch: () -> Unit,
    onUpdate: () -> Unit,
    onDismissRepatchError: () -> Unit,
    onPermissions: () -> Unit,
    onConflict: () -> Unit,
    onControllers: () -> Unit,
    onActivateFrame: () -> Unit,
    onReport: () -> Unit,
    onDismissProblem: () -> Unit,
) {
    val busy = repatch is Repatch.Running
    val updateAvailable = GameIssue.UpdateAvailable in issues
    // As wide as the achievements card beside it in the column.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (repatch is Repatch.Running) {
            val percent = ((repatch.fraction ?: 0f) * 100).toInt()
            IssueRow(
                message = when (repatch.stage) {
                    RepatchStage.QUEUED -> stringResource(R.string.issue_stage_queued)
                    RepatchStage.DOWNLOADING -> stringResource(R.string.issue_stage_downloading, percent) +
                        if (repatch.bytesPerSecond > 0) " · " + speedText(repatch.bytesPerSecond, speedUnit) else ""
                    RepatchStage.PATCHING -> stringResource(R.string.issue_patch_running)
                    RepatchStage.INSTALLING -> stringResource(R.string.issue_patch_installing)
                },
                progress = true,
                progressFraction = repatch.fraction.takeIf { repatch.stage == RepatchStage.DOWNLOADING },
            )
        }
        if (repatch is Repatch.Failed) {
            IssueRow(
                message = stringResource(R.string.issue_patch_failed, installErrorText(repatch.error)),
                action = stringResource(R.string.game_retry),
                onAction = if (updateAvailable) onUpdate else onRepatch,
                secondAction = stringResource(R.string.issue_dismiss),
                onSecondAction = onDismissRepatchError,
            )
        }
        issues.forEach { issue ->
            when (issue) {
                GameIssue.UpdateAvailable ->
                    if (repatch == Repatch.None) IssueRow(stringResource(R.string.issue_update_available), stringResource(R.string.issue_update_action), onUpdate, update = true)
                GameIssue.PatchOutdated ->
                    // An update patches the game again, so the two are not offered together.
                    if (repatch == Repatch.None && !updateAvailable) IssueRow(stringResource(R.string.issue_patch_outdated), stringResource(R.string.issue_patch_outdated_action), onRepatch)
                is GameIssue.ProblemSuspected ->
                    IssueRow(
                        stringResource(if (issue.crash) R.string.issue_problem_crash else R.string.issue_problem_short),
                        stringResource(R.string.issue_problem_action),
                        onReport,
                        secondAction = stringResource(R.string.issue_dismiss),
                        onSecondAction = onDismissProblem,
                    )
                GameIssue.StoragePermissionMissing ->
                    IssueRow(stringResource(R.string.issue_storage_permission), stringResource(R.string.game_storage_open), onPermissions, enabled = !busy)
                GameIssue.SaveConflict ->
                    IssueRow(stringResource(R.string.issue_save_conflict), stringResource(R.string.issue_save_conflict_action), onConflict, enabled = !busy)
                is GameIssue.SaveSyncFailed ->
                    IssueRow(stringResource(if (issue.offline) R.string.issue_sync_offline else R.string.issue_sync_failed))
                is GameIssue.ControllerSilent ->
                    if (issue.activated) IssueRow(stringResource(R.string.issue_controllers_silent_done), stringResource(R.string.issue_controllers_silent_open), onControllers, enabled = !busy, info = true)
                    else IssueRow(stringResource(R.string.issue_controllers_silent), stringResource(R.string.issue_controllers_silent_action), onActivateFrame, enabled = !busy, info = true)
                is GameIssue.ControllerMappingAvailable ->
                    IssueRow(stringResource(R.string.issue_controllers, sourceFamilyName(issue.source)), stringResource(R.string.issue_controllers_action), onControllers, enabled = !busy, info = true)
            }
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
    secondAction: String? = null,
    onSecondAction: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                info -> Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = Color(0xFF90CAF9))
                update -> UpdateBadge()
                else -> AttentionBadge()
            }
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (secondAction != null) GlassButton(onClick = onSecondAction) { Text(secondAction) }
            if (action != null) GlassButton(onClick = onAction, enabled = enabled) { Text(action) }
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

    fun actionsPlaced(coordinates: LayoutCoordinates) { actions = coordinates; update() }

    private fun update() {
        val inRow = row?.takeIf { it.isAttached } ?: return
        val buttons = actions?.takeIf { it.isAttached } ?: return
        val bottom = inRow.localPositionOf(buttons, Offset(0f, buttons.size.height.toFloat())).y
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
        InfoGroup(stringResource(R.string.game_info_size)) {
            InfoItem(stringResource(R.string.game_info_download), build?.downloadBytes?.let { Formatter.formatFileSize(context, it) } ?: none)
            InfoItem(stringResource(R.string.game_info_installed), build?.installBytes?.let { Formatter.formatFileSize(context, it) } ?: none)
        }
        if (installed) {
            InfoGroup(stringResource(R.string.game_info_playtime)) {
                InfoItem(stringResource(R.string.game_info_played_device), durationText((playtime.deviceMillis / 60_000L).toInt()))
                playtime.steamMinutes?.let { InfoItem(stringResource(R.string.game_info_played_steam), durationText(it)) }
            }
        }
    }
}

@Composable
private fun InfoGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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

/** What stands behind the label: why the game is incompatible, or that players tried it with the offline mode. No numbers: the label says it. */
@Composable
private fun CompatDetails(compat: app.gameport.core.model.Compat?, incompatible: app.gameport.core.model.IncompatibleReason?) {
    val language = LocalConfiguration.current.locales[0].language
    val style = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        incompatible != null -> Text(incompatible.title.pick(language) + incompatible.separator(language) + incompatible.text.pick(language), style = style, color = muted)
        compat != null && compat.offlineTested -> Text(stringResource(R.string.game_compat_offline), style = style, color = muted)
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

/** Opens the game's page on Steam, where it can be bought. Nothing happens on a device that has no browser. */
private fun openOnSteam(context: android.content.Context, appId: Int) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://store.steampowered.com/app/$appId")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
