package app.gameport.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.core.designsystem.PageHeader
import app.gameport.core.designsystem.PerformanceTeal
import app.gameport.core.model.GameSettings
import app.gameport.core.model.PerformanceTuning

/**
 * What the player asks of the device's graphics for one game: the size of the image the game draws, the frame rate, and how much the processor and the graphics chip may work.
 * Every choice starts on the one that leaves things as they are, and applies the next time the game starts.
 */
@Composable
fun GamePerformanceScreen(onBack: () -> Unit, viewModel: GamePerformanceViewModel = hiltViewModel()) {
    val settings by viewModel.gameSettings.collectAsStateWithLifecycle()
    val gameName by viewModel.gameName.collectAsStateWithLifecycle()
    GamePerformanceContent(
        settings, gameName, onBack,
        viewModel::onResolutionChosen, viewModel::onRefreshRateChosen, viewModel::onCpuLevelChosen, viewModel::onGpuLevelChosen,
    )
}

@Composable
internal fun GamePerformanceContent(
    settings: GameSettings,
    gameName: String,
    onBack: () -> Unit,
    onResolutionChosen: (Int) -> Unit,
    onRefreshRateChosen: (Int) -> Unit,
    onCpuLevelChosen: (Int) -> Unit,
    onGpuLevelChosen: (Int) -> Unit,
) {
    val performance = PerformanceTuning
    val levelLabels = listOf(R.string.gamecompat_mode_auto, R.string.gameperf_level_saving, R.string.gameperf_level_low, R.string.gameperf_level_high, R.string.gameperf_level_boost).map { stringResource(it) }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp)) {
            PageHeader(onBack, stringResource(R.string.gameperf_title), gameName) {
                Icon(Icons.Filled.Speed, contentDescription = null, tint = PerformanceTeal, modifier = Modifier.size(52.dp))
            }
            // The page keeps a margin at its bottom like the one at its top.
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ChoiceBox(
                    title = stringResource(R.string.gameperf_resolution),
                    legend = stringResource(R.string.gameperf_resolution_legend),
                    labels = performance.RESOLUTIONS.mapIndexed { i, percent -> if (i == 0) stringResource(R.string.gamecompat_haptics_normal) else "$percent %" },
                    selected = performance.RESOLUTIONS.indexOf(settings.resolutionPercent).coerceAtLeast(0),
                    onSelect = { onResolutionChosen(performance.RESOLUTIONS[it]) },
                    accent = PerformanceTeal,
                )
                ChoiceBox(
                    title = stringResource(R.string.gameperf_refresh),
                    legend = stringResource(R.string.gameperf_refresh_legend),
                    labels = performance.REFRESH_RATES.map { if (it == 0) stringResource(R.string.gameperf_refresh_game) else "$it Hz" },
                    selected = performance.REFRESH_RATES.indexOf(settings.refreshRate).coerceAtLeast(0),
                    onSelect = { onRefreshRateChosen(performance.REFRESH_RATES[it]) },
                    accent = PerformanceTeal,
                )
                ChoiceBox(
                    title = stringResource(R.string.gameperf_cpu_level),
                    legend = stringResource(R.string.gameperf_cpu_level_legend),
                    labels = levelLabels,
                    selected = performance.LEVELS.indexOf(settings.cpuLevel).coerceAtLeast(0),
                    onSelect = { onCpuLevelChosen(performance.LEVELS[it]) },
                    accent = PerformanceTeal,
                )
                ChoiceBox(
                    title = stringResource(R.string.gameperf_gpu_level),
                    legend = stringResource(R.string.gameperf_gpu_level_legend),
                    labels = levelLabels,
                    selected = performance.LEVELS.indexOf(settings.gpuLevel).coerceAtLeast(0),
                    onSelect = { onGpuLevelChosen(performance.LEVELS[it]) },
                    accent = PerformanceTeal,
                )
            }
        }
    }
}
