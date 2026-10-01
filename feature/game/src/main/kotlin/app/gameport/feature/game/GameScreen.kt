@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.gameport.feature.game

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
import app.gameport.core.model.Playtime
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.gameport.core.designsystem.GameImage
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.glass
import app.gameport.core.designsystem.GlassChip
import app.gameport.core.designsystem.speedText
import app.gameport.core.model.DlcContent
import app.gameport.core.model.Game
import app.gameport.core.model.GameIssue
import app.gameport.core.model.InstallError
import app.gameport.core.model.InstallState
import app.gameport.core.model.SpeedUnit
import app.gameport.core.model.Ownership

@Composable
fun GameScreen(onBack: () -> Unit, onOpenSettings: () -> Unit, onOpenSaves: () -> Unit, onOpenControllers: () -> Unit, viewModel: GameViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val speedUnit by viewModel.speedUnit.collectAsStateWithLifecycle()
    var explainingStorage by remember { mutableStateOf(false) }
    // Permissions are granted in the system's settings: check again when the player comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshIssues()
        viewModel.refreshPlaytime()
    }
    val openPermissions = { viewModel.appSettingsIntent()?.let { runCatching { context.startActivity(it) } }; Unit }
    val play = { viewModel.launchIntent()?.let(context::startActivity); Unit }
    if (explainingStorage) {
        AlertDialog(
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
    GameContent(
        uiState = uiState,
        speedUnit = speedUnit,
        onOpenSettings = onOpenSettings,
        onOpenSaves = onOpenSaves,
        onOpenControllers = onOpenControllers,
        onToggleFavorite = viewModel::onToggleFavorite,
        onBack = onBack,
        onInstall = viewModel::onInstall,
        onCancel = viewModel::onCancel,
        onDiscard = viewModel::onDiscard,
        onUninstall = viewModel::onUninstall,
        onPlay = { if (viewModel.shouldExplainStoragePermission()) explainingStorage = true else play() },
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
    speedUnit: SpeedUnit,
    onOpenSettings: () -> Unit,
    onOpenSaves: () -> Unit,
    onOpenControllers: () -> Unit,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onInstall: (Game, Set<Int>?) -> Unit,
    onCancel: () -> Unit,
    onDiscard: () -> Unit,
    onUninstall: () -> Unit,
    onPlay: () -> Unit,
    onOpenPermissions: () -> Unit,
    onResolveConflict: () -> Unit,
    onRepatch: () -> Unit,
    onUpdate: () -> Unit,
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
                    onDiscard = onDiscard,
                    onUninstall = onUninstall,
                    onPlay = onPlay,
                    onOpenSettings = onOpenSettings,
                    onOpenSaves = onOpenSaves,
                    controllerProfile = uiState.controllerProfile,
                    vrDevice = uiState.vrDevice,
                    onOpenControllers = onOpenControllers,
                    onToggleFavorite = onToggleFavorite,
                    favorite = uiState.favorite,
                    playtime = uiState.playtime,
                    onOpenPermissions = onOpenPermissions,
                    onResolveConflict = onResolveConflict,
                )
            }
            BackButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 32.dp, top = 16.dp))

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
    onDiscard: () -> Unit,
    onUninstall: () -> Unit,
    onPlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSaves: () -> Unit,
    controllerProfile: Boolean,
    favorite: Boolean,
    playtime: Playtime,
    vrDevice: Boolean,
    onOpenControllers: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenPermissions: () -> Unit,
    onResolveConflict: () -> Unit,
) {
    // Owned extra content is offered when installing; what the account lacks cannot be installed.
    val ownedDlc = game.androidBuild?.dlc.orEmpty().filter { it.owned }
    var choosingDlc by remember { mutableStateOf(false) }
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
        Box(
            Modifier
                .fillMaxWidth()
                .height(HERO_HEIGHT)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)), blendMode = BlendMode.DstIn)
                },
        ) {
            GameImage(
                url = game.heroUrl,
                fallbackUrl = game.headerUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // The cover straddles the hero: half of it sits on the artwork, half below. On a narrow screen it is
        // centred above the details instead of beside them.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < COMPACT_WIDTH
            val cover: @Composable (Modifier) -> Unit = { coverModifier ->
                Box(coverModifier.width(COVER_WIDTH).height(COVER_HEIGHT)) {
                    GameImage(
                        url = game.capsuleUrl,
                        fallbackUrl = game.headerUrl,
                        contentDescription = game.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(16.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp)),
                    )
                    if (issues.any { it !is GameIssue.ControllerMappingAvailable } && install is InstallState.Installed) {
                        AttentionBadge(Modifier.align(Alignment.TopStart).padding(8.dp))
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
                        stringResource(if (game.ownership == Ownership.OWNED) R.string.game_owned else R.string.game_family_shared),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InstallActions(
                        install = install,
                        speedUnit = speedUnit,
                        onInstall = { if (ownedDlc.isEmpty()) onInstall(emptySet()) else choosingDlc = true },
                        onResume = { onInstall(null) },
                        onCancel = onCancel,
                        onDiscard = onDiscard,
                        onUninstall = onUninstall,
                        onPlay = onPlay,
                        busy = repatch is Repatch.Running,
                    )
                    if (install is InstallState.Installed) {
                        GlassIconButton(onClick = onOpenSaves, enabled = repatch !is Repatch.Running) {
                            Icon(Icons.Filled.CloudSync, contentDescription = stringResource(R.string.saves_title))
                        }
                    }
                    if (install is InstallState.Installed && controllerProfile) {
                        GlassIconButton(onClick = onOpenControllers, enabled = repatch !is Repatch.Running) {
                            Icon(Icons.Filled.SportsEsports, contentDescription = stringResource(R.string.controllers_title))
                        }
                    }
                    GlassIconButton(onClick = onToggleFavorite) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = stringResource(if (favorite) R.string.game_unfavorite else R.string.game_favorite),
                            tint = if (favorite) Color(0xFFE53935) else LocalContentColor.current,
                        )
                    }
                    GlassIconButton(onClick = onOpenSettings, enabled = repatch !is Repatch.Running) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.game_settings_title))
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
                    )
                }
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
                    InfoCard(game, installed, playtime, Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // The cover straddles the artwork; what is under it (size, play time) follows it up.
                    Column(Modifier.pullUp(COVER_HEIGHT / 2).width(COVER_WIDTH), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        cover(Modifier)
                        InfoCard(game, installed, playtime, Modifier.fillMaxWidth())
                    }
                    Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = details)
                }
            }
        }
    }
}

/** Below this width the details sit under the cover instead of beside it. */
private val COMPACT_WIDTH = 600.dp
private val HERO_HEIGHT = 280.dp
private val COVER_WIDTH = 190.dp
private val COVER_HEIGHT = 285.dp

@Composable
private fun InstallActions(
    install: InstallState,
    speedUnit: SpeedUnit,
    onInstall: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDiscard: () -> Unit,
    onUninstall: () -> Unit,
    onPlay: () -> Unit,
    busy: Boolean = false,
) {
    when (install) {
        InstallState.NotInstalled -> Button(onClick = onInstall, modifier = Modifier.widthIn(min = 240.dp)) {
            Text(stringResource(R.string.game_install))
        }
        InstallState.Interrupted -> FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onResume) { Text(stringResource(R.string.game_resume)) }
            DangerButton(onClick = onDiscard) { Text(stringResource(R.string.game_discard)) }
        }
        InstallState.Queued -> Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.game_queued))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            DangerTextButton(onClick = onCancel) { Text(stringResource(R.string.game_cancel)) }
        }
        is InstallState.Downloading -> Progress(step = Step.DOWNLOAD, fraction = install.progress, speed = install.bytesPerSecond, speedUnit = speedUnit, onCancel = onCancel)
        InstallState.Patching -> Progress(step = Step.PATCH, fraction = null, speed = 0, speedUnit = speedUnit, onCancel = null)
        InstallState.Installing -> Progress(step = Step.INSTALL, fraction = null, speed = 0, speedUnit = speedUnit, onCancel = null)
        is InstallState.Installed -> FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPlay, enabled = !busy) { Text(stringResource(R.string.game_play)) }
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
            }
        }
    }
}

private enum class Step(val label: Int) {
    DOWNLOAD(R.string.game_step_download),
    PATCH(R.string.game_step_patch),
    INSTALL(R.string.game_step_install),
}

/** The three stages an install goes through, with the current one highlighted. */
@Composable
private fun Progress(step: Step, fraction: Float?, speed: Long, speedUnit: SpeedUnit, onCancel: (() -> Unit)?) {
    Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Step.entries.forEach { entry ->
                val done = entry.ordinal < step.ordinal
                val current = entry == step
                Text(
                    text = (if (done) "✓ " else "${entry.ordinal + 1}. ") + stringResource(entry.label),
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        current -> MaterialTheme.colorScheme.primary
                        done -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            val percent = "${(fraction * 100).toInt()}%"
            Text(
                if (speed > 0) "$percent · ${speedText(speed, speedUnit)}" else percent,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                stringResource(if (step == Step.PATCH) R.string.game_patching else R.string.game_installing),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        onCancel?.let { DangerTextButton(onClick = it) { Text(stringResource(R.string.game_cancel)) } }
    }
}

/** Asked when Install is pressed: which owned extras to add. Nothing is ticked by default. */
@Composable
private fun DlcDialog(game: Game, dlc: List<DlcContent>, onDismiss: () -> Unit, onConfirm: (Set<Int>) -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    val build = game.androidBuild
    val download = (build?.downloadBytes ?: 0L) + dlc.filter { it.appId in selected }.sumOf { it.downloadBytes }
    val installed = (build?.installBytes ?: 0L) + dlc.filter { it.appId in selected }.sumOf { it.installBytes }
    AlertDialog(
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

@Composable
private fun installErrorText(error: InstallError): String {
    val context = LocalContext.current
    return when (error) {
        is InstallError.NotEnoughSpace -> stringResource(
            R.string.game_error_space,
            Formatter.formatFileSize(context, error.neededBytes),
            Formatter.formatFileSize(context, error.freeBytes),
        )
        InstallError.NotSignedIn -> stringResource(R.string.game_error_signed_out)
        InstallError.NoApk -> stringResource(R.string.game_error_no_apk)
        InstallError.UnreadableApk -> stringResource(R.string.game_error_unreadable)
        InstallError.VersionConflict -> stringResource(R.string.game_error_conflict)
        is InstallError.Other -> stringResource(R.string.game_install_failed, error.message.orEmpty())
    }
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
) {
    val busy = repatch is Repatch.Running
    val updateAvailable = GameIssue.UpdateAvailable in issues
    Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    if (repatch == Repatch.None) IssueRow(stringResource(R.string.issue_update_available), stringResource(R.string.issue_update_action), onUpdate)
                GameIssue.PatchOutdated ->
                    // An update patches the game again, so the two are not offered together.
                    if (repatch == Repatch.None && !updateAvailable) IssueRow(stringResource(R.string.issue_patch_outdated), stringResource(R.string.issue_patch_outdated_action), onRepatch)
                GameIssue.StoragePermissionMissing ->
                    IssueRow(stringResource(R.string.issue_storage_permission), stringResource(R.string.game_storage_open), onPermissions, enabled = !busy)
                GameIssue.SaveConflict ->
                    IssueRow(stringResource(R.string.issue_save_conflict), stringResource(R.string.issue_save_conflict_action), onConflict, enabled = !busy)
                is GameIssue.SaveSyncFailed ->
                    IssueRow(stringResource(if (issue.offline) R.string.issue_sync_offline else R.string.issue_sync_failed))
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
    secondAction: String? = null,
    onSecondAction: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (info) Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = Color(0xFF90CAF9)) else AttentionBadge()
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
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
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
