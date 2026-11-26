package app.gameport.feature.game

import app.gameport.core.designsystem.TourIds
import app.gameport.core.designsystem.tourTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.core.designsystem.PillTabs
import app.gameport.core.designsystem.PageHeader
import app.gameport.core.model.PassthroughMode
import app.gameport.core.model.RecenterMode
import app.gameport.core.designsystem.glass

/**
 * What the player can turn on when a game does not work as it should, all in one place. Each switch is off until chosen; what GamePort did by itself is
 * told under the switch it concerns, and only when it did.
 */
@Composable
fun GameCompatScreen(onBack: () -> Unit, viewModel: GameCompatViewModel = hiltViewModel()) {
    val mapping by viewModel.mapping.collectAsStateWithLifecycle()
    val settings by viewModel.gameSettings.collectAsStateWithLifecycle()
    val stageFallback by viewModel.stageFallback.collectAsStateWithLifecycle()
    val gameName by viewModel.gameName.collectAsStateWithLifecycle()
    val showOpenInVr by viewModel.showOpenInVr.collectAsStateWithLifecycle()
    val showOvrPluginStandIn by viewModel.showOvrPluginStandIn.collectAsStateWithLifecycle()
    val renameNeeded by viewModel.renameNeeded.collectAsStateWithLifecycle()
    GameCompatContent(mapping, settings, stageFallback, gameName, onBack, viewModel::onUseFrameChanged, viewModel::onRecenterChosen, showOpenInVr, viewModel::onOpenInVrChosen, showOvrPluginStandIn, viewModel::onOvrPluginStandInChosen, viewModel::onPassthroughChosen, viewModel.passthroughListed, viewModel.showEyeFoveation, viewModel::onEyeFoveationChosen, viewModel.showHaptics, viewModel::onHapticsChosen, viewModel::onPackageNameChosen, viewModel.refusesReSignedGames, renameNeeded, viewModel.controllerDeviceFamily, viewModel::onPresentedChosen)
}

/** The page of the options, from what it shows: the screen feeds it from the game, the tour of the page feeds it made-up values. */
@Composable
internal fun GameCompatContent(
    mapping: app.gameport.core.model.ControllerMapping,
    settings: app.gameport.core.model.GameSettings,
    stageFallback: Boolean,
    gameName: String,
    onBack: () -> Unit,
    onUseFrameChanged: (Boolean) -> Unit,
    onRecenterChosen: (RecenterMode) -> Unit,
    showOpenInVr: Boolean = false,
    onOpenInVrChosen: (Boolean) -> Unit = {},
    showOvrPluginStandIn: Boolean = false,
    onOvrPluginStandInChosen: (Boolean) -> Unit = {},
    onPassthroughChosen: (PassthroughMode) -> Unit = {},
    passthroughListed: Boolean = false,
    showEyeFoveation: Boolean = false,
    onEyeFoveationChosen: (app.gameport.core.model.EyeFoveationMode) -> Unit = {},
    showHaptics: Boolean = false,
    onHapticsChosen: (Int) -> Unit = {},
    onPackageNameChosen: (app.gameport.core.model.PackageNameMode) -> Unit = {},
    refusesReSignedGames: Boolean = false,
    renameNeeded: Boolean = false,
    deviceFamily: String? = null,
    onPresentedChosen: (app.gameport.core.model.PresentedControllers) -> Unit = {},
) {
    val choice = ControllerChoiceState.of(mapping, deviceFamily)
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp)) {
            PageHeader(onBack, stringResource(R.string.gamecompat_title), gameName, tour = app.gameport.core.model.TourKind.GAME_COMPAT) {
                Icon(Icons.Filled.Build, contentDescription = null, tint = CompatOrange, modifier = Modifier.size(52.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (choice.choosable) {
                    PresentedControllersBox(Modifier.tourTarget(TourIds.GAMECOMPAT_FRAME), choice, mapping.presented, onPresentedChosen)
                } else {
                    OptionBox(
                        modifier = Modifier.tourTarget(TourIds.GAMECOMPAT_FRAME),
                        title = stringResource(R.string.controllers_use_frame),
                        legend = stringResource(R.string.controllers_use_frame_legend),
                        checked = mapping.useFrame,
                        onChecked = onUseFrameChanged,
                        // Only when the game ran and the layer saw no control for the Steam Frame's controllers.
                        warning = stringResource(R.string.controllers_use_frame_warning).takeIf { mapping.useFrame && mapping.noFrameControls },
                        hint = stringResource(R.string.controllers_offered_waiting).takeIf { choice.waitingForLaunch },
                    )
                }
                ChoiceBox(
                    modifier = Modifier.tourTarget(TourIds.GAMECOMPAT_RECENTER),
                    title = stringResource(R.string.gamecompat_recenter),
                    legend = stringResource(R.string.gamecompat_recenter_legend),
                    labels = listOf(R.string.gamecompat_mode_auto, R.string.gamecompat_mode_on, R.string.gamecompat_mode_off).map { stringResource(it) },
                    selected = settings.recenter.ordinal,
                    onSelect = { onRecenterChosen(RecenterMode.entries[it]) },
                    // What GamePort did by itself, said only when it did.
                    note = stringResource(R.string.gamecompat_recenter_auto).takeIf { stageFallback && settings.recenter == RecenterMode.AUTO },
                )
                ChoiceBox(
                    title = stringResource(R.string.gamecompat_passthrough),
                    legend = stringResource(R.string.gamecompat_passthrough_legend),
                    labels = listOf(R.string.gamecompat_mode_auto, R.string.gamecompat_mode_on, R.string.gamecompat_mode_off).map { stringResource(it) },
                    selected = settings.passthrough.ordinal,
                    onSelect = { onPassthroughChosen(PassthroughMode.entries[it]) },
                    note = stringResource(R.string.gamecompat_passthrough_auto).takeIf { passthroughListed && settings.passthrough == PassthroughMode.AUTO },
                )
                ChoiceBox(
                    title = stringResource(R.string.gamecompat_package_name),
                    legend = stringResource(R.string.gamecompat_package_name_legend),
                    labels = listOf(R.string.gamecompat_mode_auto, R.string.gamecompat_mode_on, R.string.gamecompat_mode_off).map { stringResource(it) },
                    selected = settings.packageName.ordinal,
                    onSelect = { onPackageNameChosen(app.gameport.core.model.PackageNameMode.entries[it]) },
                    note = stringResource(R.string.gamecompat_package_name_auto).takeIf { refusesReSignedGames && settings.packageName == app.gameport.core.model.PackageNameMode.AUTO },
                    warning = when {
                        refusesReSignedGames && settings.packageName == app.gameport.core.model.PackageNameMode.OFF -> stringResource(R.string.gamecompat_package_name_refused)
                        renameNeeded -> stringResource(R.string.gamecompat_package_name_warning)
                        else -> null
                    },
                )
                if (showEyeFoveation) ChoiceBox(
                    title = stringResource(R.string.gamecompat_eye_foveation),
                    legend = stringResource(R.string.gamecompat_eye_foveation_legend),
                    labels = listOf(R.string.gamecompat_mode_auto, R.string.gamecompat_mode_on, R.string.gamecompat_mode_off).map { stringResource(it) },
                    selected = settings.eyeFoveation.ordinal,
                    onSelect = { onEyeFoveationChosen(app.gameport.core.model.EyeFoveationMode.entries[it]) },
                )
                if (showHaptics) ChoiceBox(
                    title = stringResource(R.string.gamecompat_haptics),
                    legend = stringResource(R.string.gamecompat_haptics_legend),
                    labels = listOf(R.string.gamecompat_haptics_normal, R.string.gamecompat_haptics_light, R.string.gamecompat_haptics_strong, R.string.gamecompat_mode_off).map { stringResource(it) },
                    selected = app.gameport.core.model.HapticsStrength.CHOICES.indexOf(settings.hapticsPercent).coerceAtLeast(0),
                    onSelect = { onHapticsChosen(app.gameport.core.model.HapticsStrength.CHOICES[it]) },
                )
                if (showOpenInVr) ChoiceBox(
                    title = stringResource(R.string.gamecompat_open_in_vr),
                    legend = stringResource(R.string.gamecompat_open_in_vr_legend),
                    labels = listOf(R.string.gamecompat_mode_auto, R.string.gamecompat_mode_off).map { stringResource(it) },
                    selected = if (settings.openInVr) 0 else 1,
                    onSelect = { onOpenInVrChosen(it == 0) },
                )
                if (showOvrPluginStandIn) ChoiceBox(
                    title = stringResource(R.string.gamecompat_ovrplugin),
                    legend = stringResource(R.string.gamecompat_ovrplugin_legend),
                    labels = listOf(R.string.gamecompat_mode_auto, R.string.gamecompat_mode_off).map { stringResource(it) },
                    selected = if (settings.ovrPluginStandIn) 0 else 1,
                    onSelect = { onOvrPluginStandInChosen(it == 0) },
                )
            }
        }
    }
}

/** A choice among a few, always showing which is taken, with its legend. The first is the automatic one, where GamePort decides. */
@Composable
internal fun ChoiceBox(modifier: Modifier = Modifier, title: String, legend: String, labels: List<String>, selected: Int, onSelect: (Int) -> Unit, note: String? = null, warning: String? = null, accent: Color = CompatOrange, titleEnd: String? = null, fitLabels: Boolean = false) {
    Column(
        modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (titleEnd != null) Text(titleEnd, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        }
        Text(legend, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        PillTabs(labels, selected, onSelect, Modifier.fillMaxWidth(), fitLabels = fitLabels)
        if (note != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Text(note, color = accent, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (warning != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Text(warning, color = accent, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A switch with its legend. A [warning], in orange with a mark, says a doubt about the switch. */
@Composable
private fun OptionBox(
    modifier: Modifier = Modifier,
    title: String,
    legend: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    warning: String? = null,
    hint: String? = null,
) {
    Column(
        modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(legend, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
        if (hint != null) Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (warning != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = CompatOrange, modifier = Modifier.size(20.dp))
                Text(warning, color = CompatOrange, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * What the game says it handles, for information, and which of the Steam Frame's and the Touch's controllers to put forward, from its next start. Automatic
 * is the first choice and leaves it to GamePort. A choice that could not be applied is told in orange under the option.
 */
@Composable
private fun PresentedControllersBox(
    modifier: Modifier,
    choice: ControllerChoiceState,
    selected: app.gameport.core.model.PresentedControllers,
    onChosen: (app.gameport.core.model.PresentedControllers) -> Unit,
) {
    Column(
        modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.controllers_offered_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.controllers_offered_legend), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(R.string.controllers_offered_listed, choice.listed.map { stringResource(kindName(it)) }.joinToString(", ")),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
        if (choice.nothingToChoose) {
            Text(stringResource(R.string.controllers_offered_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else {
            PillTabs(
                choice.offered.map { stringResource(presentedName(it)) },
                choice.offered.indexOf(selected).coerceAtLeast(0),
                { onChosen(choice.offered[it]) },
                Modifier.fillMaxWidth(),
            )
        }
        if (choice.ignored) {
            Text(stringResource(R.string.controllers_offered_ignored), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (choice.noFrameControlsWarning) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = CompatOrange, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.controllers_use_frame_warning), color = CompatOrange, style = MaterialTheme.typography.bodySmall)
            }
        }
        choice.used?.let { used ->
            Text(stringResource(R.string.controllers_offered_used, stringResource(usedName(used))), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (choice.fallback) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = CompatOrange, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.controllers_offered_fallback), color = CompatOrange, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

internal fun kindName(kind: app.gameport.core.model.ControllerProfile.Kind): Int = when (kind) {
    app.gameport.core.model.ControllerProfile.Kind.FRAME -> R.string.controllers_kind_frame
    app.gameport.core.model.ControllerProfile.Kind.TOUCH -> R.string.controllers_kind_touch
    app.gameport.core.model.ControllerProfile.Kind.PICO -> R.string.controllers_kind_pico
    app.gameport.core.model.ControllerProfile.Kind.INDEX -> R.string.controllers_kind_index
    app.gameport.core.model.ControllerProfile.Kind.VIVE -> R.string.controllers_kind_vive
    app.gameport.core.model.ControllerProfile.Kind.SIMPLE -> R.string.controllers_kind_simple
    app.gameport.core.model.ControllerProfile.Kind.GAZE -> R.string.controllers_kind_gaze
    app.gameport.core.model.ControllerProfile.Kind.OTHER -> R.string.controllers_kind_other
}

private fun presentedName(choice: app.gameport.core.model.PresentedControllers): Int = when (choice) {
    app.gameport.core.model.PresentedControllers.AUTO -> R.string.gamecompat_mode_auto
    app.gameport.core.model.PresentedControllers.FRAME -> R.string.controllers_kind_frame
    app.gameport.core.model.PresentedControllers.TOUCH -> R.string.controllers_kind_touch
}

private fun usedName(used: String): Int = when (used) {
    "frame" -> R.string.controllers_kind_frame
    "touch" -> R.string.controllers_kind_touch
    "native" -> R.string.controllers_used_native
    else -> R.string.controllers_used_none
}

private val CompatOrange = Color(0xFFFFB74D)
