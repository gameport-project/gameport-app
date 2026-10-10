package app.gameport.feature.game

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
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp)) {
            PageHeader(onBack, stringResource(R.string.gamecompat_title), gameName) {
                Icon(Icons.Filled.Build, contentDescription = null, tint = CompatOrange, modifier = Modifier.size(52.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OptionBox(
                    title = stringResource(R.string.controllers_use_frame),
                    legend = stringResource(R.string.controllers_use_frame_legend),
                    checked = mapping.useFrame,
                    onChecked = viewModel::onUseFrameChanged,
                    // Only when the game ran and the layer saw no control for the Steam Frame's controllers.
                    warning = stringResource(R.string.controllers_use_frame_warning).takeIf { mapping.useFrame && mapping.noFrameControls },
                )
                ChoiceBox(
                    title = stringResource(R.string.gamecompat_recenter),
                    legend = stringResource(R.string.gamecompat_recenter_legend),
                    labels = listOf(R.string.gamecompat_mode_auto, R.string.gamecompat_mode_on, R.string.gamecompat_mode_off).map { stringResource(it) },
                    selected = settings.recenter.ordinal,
                    onSelect = { viewModel.onRecenterChosen(RecenterMode.entries[it]) },
                    // What GamePort did by itself, said only when it did.
                    note = stringResource(R.string.gamecompat_recenter_auto).takeIf { stageFallback && settings.recenter == RecenterMode.AUTO },
                )
            }
        }
    }
}

/** A choice among a few, always showing which is taken, with its legend. The first is the automatic one, where GamePort decides. */
@Composable
private fun ChoiceBox(title: String, legend: String, labels: List<String>, selected: Int, onSelect: (Int) -> Unit, note: String? = null) {
    Column(
        Modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(legend, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        PillTabs(labels, selected, onSelect, Modifier.fillMaxWidth())
        if (note != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = CompatOrange, modifier = Modifier.size(20.dp))
                Text(note, color = CompatOrange, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** A switch with its legend. A [warning], in orange with a mark, says a doubt about the switch. */
@Composable
private fun OptionBox(
    title: String,
    legend: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    warning: String? = null,
) {
    Column(
        Modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(legend, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
        if (warning != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = CompatOrange, modifier = Modifier.size(20.dp))
                Text(warning, color = CompatOrange, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private val CompatOrange = Color(0xFFFFB74D)
