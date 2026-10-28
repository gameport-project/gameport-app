package app.gameport.feature.game

import app.gameport.core.designsystem.GlassButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.gameport.core.designsystem.BackdropDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.core.designsystem.BackButton
import app.gameport.core.designsystem.DangerButton
import app.gameport.core.designsystem.DangerRed
import app.gameport.core.designsystem.DangerTextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import app.gameport.core.designsystem.BackdropMenu
import app.gameport.core.designsystem.KindBlue
import app.gameport.core.model.LaunchOption
import app.gameport.core.model.LaunchOptions
import app.gameport.core.model.InstallState
import app.gameport.core.model.PlayerDefaults

/** Settings of one game, opened from the gear on its page. */
@Composable
fun GameSettingsScreen(onBack: () -> Unit, viewModel: GameSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val defaults by viewModel.defaults.collectAsStateWithLifecycle()
    val gameName by viewModel.gameName.collectAsStateWithLifecycle()
    val isVrGame by viewModel.isVrGame.collectAsStateWithLifecycle()
    val patchOutdated by viewModel.patchOutdated.collectAsStateWithLifecycle()
    val installState by viewModel.installState.collectAsStateWithLifecycle()
    val patchRemoved by viewModel.patchRemoved.collectAsStateWithLifecycle()
    val launchOptions by viewModel.launchOptions.collectAsStateWithLifecycle()
    val extraContent by viewModel.extraContent.collectAsStateWithLifecycle()
    var confirmingRemoval by remember { mutableStateOf(false) }
    if (confirmingRemoval) {
        BackdropDialog(
            onDismissRequest = { confirmingRemoval = false },
            title = { Text(stringResource(R.string.game_settings_remove_patch)) },
            text = { Text(stringResource(R.string.game_settings_remove_patch_confirm)) },
            confirmButton = {
                DangerButton(onClick = { confirmingRemoval = false; viewModel.onRemovePatch() }) {
                    Text(stringResource(R.string.game_settings_remove_patch))
                }
            },
            dismissButton = { DangerTextButton(onClick = { confirmingRemoval = false }) { Text(stringResource(R.string.game_settings_cancel)) } },
        )
    }

    val installedNow = installState is InstallState.Installed
    // What the page has to show, as categories like the application's settings: only those that mean something for this game now.
    val available = buildList {
        if (installedNow || installState is InstallState.Patching || installState is InstallState.Installing) add(GameCategory.PATCH)
        if (installedNow) add(GameCategory.PERMISSIONS)
        if (installedNow && !extraContent.isEmpty) add(GameCategory.EXTRA)
        if (launchOptions.isNotEmpty()) add(GameCategory.LAUNCH)
        if (viewModel.isHeadset && isVrGame) add(GameCategory.VR)
    }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val category = available.firstOrNull { it.name == chosen } ?: available.firstOrNull()

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onClick = onBack)
                Column {
                    Text(stringResource(R.string.game_settings_title), style = MaterialTheme.typography.headlineMedium)
                    Text(gameName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (category == null) {
                Text(stringResource(R.string.game_settings_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                // A wide screen has the categories on the left; a narrow one has them in a row above the page.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val compact = maxWidth < COMPACT_WIDTH
                    val page: @Composable (Modifier) -> Unit = { modifier ->
                        Column(modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            when (category) {
                                GameCategory.PATCH -> {
                                    val busy = installState !is InstallState.Installed
                                    Text(stringResource(R.string.game_settings_patch), style = MaterialTheme.typography.headlineSmall)
                                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            stringResource(
                                                when {
                                                    busy -> R.string.game_settings_patch_running
                                                    patchRemoved -> R.string.game_settings_patch_removed
                                                    patchOutdated -> R.string.game_settings_patch_outdated
                                                    else -> R.string.game_settings_patch_current
                                                },
                                            ),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text(stringResource(R.string.game_settings_patch_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            if (patchOutdated || patchRemoved) {
                                                Button(onClick = viewModel::onRepatch, enabled = !busy) {
                                                    Text(stringResource(if (patchRemoved) R.string.game_settings_apply_patch else R.string.game_settings_repatch))
                                                }
                                            } else {
                                                GlassButton(onClick = viewModel::onRepatch, enabled = !busy) { Text(stringResource(R.string.game_settings_repatch)) }
                                            }
                                            if (!patchRemoved) {
                                                DangerButton(onClick = { confirmingRemoval = true }, enabled = !busy) {
                                                    Text(stringResource(R.string.game_settings_remove_patch))
                                                }
                                            }
                                        }
                                    }
                                }
                                GameCategory.PERMISSIONS -> {
                                    val context = LocalContext.current
                                    Text(stringResource(R.string.game_settings_permissions), style = MaterialTheme.typography.headlineSmall)
                                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(stringResource(R.string.game_settings_permissions_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        GlassButton(onClick = { viewModel.appSettingsIntent()?.let { runCatching { context.startActivity(it) } } }) {
                                            Text(stringResource(R.string.game_settings_permissions_open))
                                        }
                                    }
                                }
                                GameCategory.EXTRA -> ExtraContentSection(extraContent, viewModel::onInstallDlc, viewModel::onRemoveDlc, viewModel::onDismissDlcError)
                                GameCategory.LAUNCH -> LaunchArgumentsSection(
                                    options = launchOptions,
                                    picked = settings.launchArguments,
                                    effective = LaunchOptions.chosen(launchOptions, settings.launchArguments, isVrGame).toSet(),
                                    onPicked = viewModel::onLaunchArgumentsChanged,
                                )
                                GameCategory.VR -> {
                                    Text(stringResource(R.string.game_settings_vr), style = MaterialTheme.typography.headlineSmall)
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(stringResource(R.string.game_settings_seated), style = MaterialTheme.typography.titleMedium)
                                            Text(stringResource(R.string.game_settings_seated_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = settings.seated, onCheckedChange = viewModel::onSeatedChanged)
                                    }
                                    val height = settings.effectiveHeightCm(defaults)
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.game_settings_height, height), style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            stringResource(if (settings.followsDefaults) R.string.game_settings_height_global else R.string.game_settings_height_own),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Slider(
                                            value = height.toFloat(),
                                            onValueChange = { viewModel.onHeightChanged(it.toInt()) },
                                            valueRange = PlayerDefaults.HEIGHT_RANGE_CM.first.toFloat()..PlayerDefaults.HEIGHT_RANGE_CM.last.toFloat(),
                                            enabled = settings.seated,
                                        )
                                        if (!settings.followsDefaults) {
                                            GlassButton(onClick = viewModel::onUseGlobalConfiguration) {
                                                Text(stringResource(R.string.game_settings_use_global, defaults.heightCm))
                                            }
                                        }
                                        Text(stringResource(R.string.game_settings_height_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (compact) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                available.forEach { entry -> GameCategoryItem(entry, entry == category, compact = true) { chosen = entry.name } }
                            }
                            page(Modifier.weight(1f).fillMaxWidth())
                        }
                    } else {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            Column(Modifier.width(260.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                available.forEach { entry -> GameCategoryItem(entry, entry == category, compact = false) { chosen = entry.name } }
                            }
                            page(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** The categories of a game's settings, listed on the left as the application's own are. */
private enum class GameCategory(val title: Int) {
    PATCH(R.string.game_settings_patch),
    PERMISSIONS(R.string.game_settings_permissions),
    EXTRA(R.string.extra_title),
    LAUNCH(R.string.game_settings_launch),
    VR(R.string.game_settings_vr),
}

private val COMPACT_WIDTH = 600.dp

@Composable
private fun GameCategoryItem(entry: GameCategory, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
        modifier = (if (compact) Modifier else Modifier.fillMaxWidth()).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    ) {
        Text(
            stringResource(entry.title),
            modifier = Modifier.padding(if (compact) PaddingValues(horizontal = 16.dp, vertical = 10.dp) else PaddingValues(16.dp)),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** The arguments Steam lists for starting the game, each one picked or not. What is picked at first is what Steam marks for VR. */
@Composable
private fun LaunchArgumentsSection(options: List<LaunchOption>, picked: Set<String>?, effective: Set<String>, onPicked: (Set<String>?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Text(stringResource(R.string.game_settings_launch), style = MaterialTheme.typography.headlineSmall)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.game_settings_launch_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(if (picked == null) R.string.game_settings_launch_suggested else R.string.game_settings_launch_own),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                GlassButton(onClick = { open = true }) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (effective.isEmpty()) stringResource(R.string.game_settings_launch_nothing) else stringResource(R.string.game_settings_launch_picked, effective.size))
                }
                BackdropMenu(expanded = open, onDismissRequest = { open = false }) {
                    options.forEach { option ->
                        val on = option.arguments in effective
                        Row(
                            Modifier.fillMaxWidth().clickable { onPicked(if (on) effective - option.arguments else effective + option.arguments) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = if (on) KindBlue else Color.Transparent, modifier = Modifier.size(18.dp))
                            Column {
                                Text(option.arguments, color = if (on) KindBlue else MaterialTheme.colorScheme.onSurface)
                                option.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
            if (picked != null) {
                GlassButton(onClick = { onPicked(null) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.game_settings_launch_reset))
                }
            }
        }
    }
}
