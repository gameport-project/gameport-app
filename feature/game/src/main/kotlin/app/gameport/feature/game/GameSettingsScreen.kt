package app.gameport.feature.game

import app.gameport.core.designsystem.GlassButton
import androidx.compose.foundation.layout.Arrangement
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
    var confirmingRemoval by remember { mutableStateOf(false) }
    if (confirmingRemoval) {
        BackdropDialog(
            onDismissRequest = { confirmingRemoval = false },
            title = { Text(stringResource(R.string.game_settings_remove_patch)) },
            text = { Text(stringResource(R.string.game_settings_remove_patch_confirm)) },
            confirmButton = {
                DangerTextButton(onClick = { confirmingRemoval = false; viewModel.onRemovePatch() }) {
                    Text(stringResource(R.string.game_settings_remove_patch), color = DangerRed)
                }
            },
            dismissButton = { DangerTextButton(onClick = { confirmingRemoval = false }) { Text(stringResource(R.string.game_settings_cancel)) } },
        )
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onClick = onBack)
                Column {
                    Text(stringResource(R.string.game_settings_title), style = MaterialTheme.typography.headlineMedium)
                    Text(gameName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (installState is InstallState.Installed || installState is InstallState.Patching || installState is InstallState.Installing) {
                val busy = installState !is InstallState.Installed
                Text(stringResource(R.string.game_settings_patch), style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            if (installState is InstallState.Installed) {
                val context = LocalContext.current
                Text(stringResource(R.string.game_settings_permissions), style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.game_settings_permissions_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    GlassButton(onClick = { viewModel.appSettingsIntent()?.let { runCatching { context.startActivity(it) } } }) {
                        Text(stringResource(R.string.game_settings_permissions_open))
                    }
                }
            }
            if (viewModel.isHeadset && isVrGame) {
                Text(stringResource(R.string.game_settings_vr), style = MaterialTheme.typography.headlineSmall)
                Row(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.game_settings_seated), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.game_settings_seated_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.seated, onCheckedChange = viewModel::onSeatedChanged)
                }
                val height = settings.effectiveHeightCm(defaults)
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
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
                    Text(
                        stringResource(R.string.game_settings_height_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(stringResource(R.string.game_settings_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
