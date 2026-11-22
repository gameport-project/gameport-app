package app.gameport.feature.game

import androidx.compose.foundation.layout.Arrangement
import app.gameport.core.settings.DisplayRates
import app.gameport.core.designsystem.BackdropDialog
import androidx.compose.material3.Button
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import app.gameport.core.designsystem.GlassButton
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Build
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import kotlin.math.abs
import kotlin.math.roundToInt
import app.gameport.core.designsystem.CompatOrange
import app.gameport.core.designsystem.glass
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import app.gameport.core.settings.EyeSides
import app.gameport.core.settings.OptimizerState

/**
 * What the player asks of the device's graphics for one game: the size of the image the game draws, the frame rate, and how much the processor and the graphics chip may work.
 * Every choice starts on the one that leaves things as they are, and applies the next time the game starts.
 */
@Composable
fun GamePerformanceScreen(onBack: () -> Unit, viewModel: GamePerformanceViewModel = hiltViewModel()) {
    val settings by viewModel.gameSettings.collectAsStateWithLifecycle()
    val gameName by viewModel.gameName.collectAsStateWithLifecycle()
    val refreshNeedsPatch by viewModel.refreshNeedsPatch.collectAsStateWithLifecycle()
    val refreshOutcome by viewModel.refreshOutcome.collectAsStateWithLifecycle()
    val lastRun by viewModel.lastRun.collectAsStateWithLifecycle()
    GamePerformanceContent(
        settings, gameName, viewModel.eyeSides, viewModel.optimizerState, viewModel.refreshRates, viewModel.displayRates, refreshNeedsPatch, viewModel::onPatchAgain, onBack,
        viewModel::onResolutionChosen, viewModel::onRefreshRateChosen, viewModel::onCpuLevelChosen, viewModel::onGpuLevelChosen, refreshOutcome, lastRun,
    )
}

@Composable
internal fun GamePerformanceContent(
    settings: GameSettings,
    gameName: String,
    eyeSides: EyeSides,
    optimizerState: OptimizerState,
    refreshRates: List<Int>,
    displayRates: DisplayRates,
    refreshNeedsPatch: Boolean,
    onPatchAgain: () -> Unit,
    onBack: () -> Unit,
    onResolutionChosen: (Int) -> Unit,
    onRefreshRateChosen: (Int, Int?) -> Unit,
    onCpuLevelChosen: (Int) -> Unit,
    onGpuLevelChosen: (Int) -> Unit,
    refreshOutcome: app.gameport.core.model.RefreshOutcome? = null,
    lastRun: LastRun? = null,
) {
    val performance = PerformanceTuning
    var pendingRate by remember { mutableStateOf<Int?>(null) }
    pendingRate?.let { rate ->
        val at = displayRates.lowerResolutionAt(rate)
        val best = displayRates.best
        if (at != null && best != null) {
            // The image size held to what the mode shows: lowered now when it is above, and the slider then stops there.
            val cap = PerformanceTuning.capForMode(at, eyeSides.screen)
            val now = currentPercent(settings.resolutionPercent, eyeSides)
            val lowered = cap?.takeIf { now > it }
            BackdropDialog(
                onDismissRequest = { pendingRate = null },
                title = { Text(stringResource(R.string.gameperf_refresh_lower_title, rate)) },
                text = {
                    Text(
                        if (lowered != null) stringResource(R.string.gameperf_refresh_lower_text_cap, rate, at.first, at.second, best.first, best.second, lowered)
                        else stringResource(R.string.gameperf_refresh_lower_text, rate, at.first, at.second, best.first, best.second),
                    )
                },
                confirmButton = { Button(onClick = { onRefreshRateChosen(rate, lowered); pendingRate = null }) { Text(stringResource(R.string.gameperf_refresh_lower_confirm, rate)) } },
                dismissButton = { GlassButton(onClick = { pendingRate = null }) { Text(stringResource(R.string.gameperf_refresh_lower_cancel)) } },
            )
        }
    }
    val levelLabels = listOf(R.string.gamecompat_mode_auto, R.string.gameperf_level_saving, R.string.gameperf_level_low, R.string.gameperf_level_high, R.string.gameperf_level_boost).map { stringResource(it) }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp)) {
            PageHeader(onBack, stringResource(R.string.gameperf_title), gameName) {
                Icon(Icons.Filled.Speed, contentDescription = null, tint = PerformanceTeal, modifier = Modifier.size(52.dp))
            }
            // The page keeps a margin at its bottom like the one at its top.
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // What the last run of the game made of these choices comes first.
                lastRun?.let { LastRunPerformanceRow(it, Modifier.fillMaxWidth()) }
                if (optimizerState != OptimizerState.NONE) {
                    // Something else sets the graphics of this game: its choices are the ones that count, and these would only be a second setting.
                    OptimizerNotice(optimizerState)
                    return@Column
                }
                // At a rate that only comes with a smaller picture, the slider stops at the largest image that picture can show.
                val resolutionCap = settings.refreshRate.takeIf { it != 0 }?.let { displayRates.lowerResolutionAt(it) }?.let { PerformanceTuning.capForMode(it, eyeSides.screen) }
                ResolutionBox(settings.resolutionPercent, eyeSides, onResolutionChosen, resolutionCap, settings.refreshRate)
                ChoiceBox(
                    title = stringResource(R.string.gameperf_refresh),
                    legend = stringResource(R.string.gameperf_refresh_legend),
                    labels = refreshRates.map { if (it == 0) stringResource(R.string.gameperf_refresh_game) else "$it Hz" },
                    selected = refreshRates.indexOf(settings.refreshRate).coerceAtLeast(0),
                    onSelect = {
                        // A rate that only comes with a smaller picture than the screen's own is confirmed first.
                        val rate = refreshRates[it]
                        if (rate != 0 && displayRates.lowerResolutionAt(rate) != null) pendingRate = rate else onRefreshRateChosen(rate, null)
                    },
                    accent = PerformanceTeal,
                    fitLabels = true,
                )
                refreshOutcome?.let { RefreshOutcomeNotice(it) }
                if (refreshNeedsPatch) PatchAgainRow(onPatchAgain)
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

/** The amber of the mark that shows the default on the slider. */
private val DefaultMark = Color(0xFFFFC107)

/** How near the mark, in percent, the thumb has to be for the default to be taken. */
private const val DEFAULT_GRIP = 1.5f

/**
 * The image size as a slider from 70 to 200 % of the screen's resolution, with an amber mark where the default sits (the size the system recommends), a button that
 * goes back to it, and at the bottom the screen's resolution and the one of the image shown.
 */
@Composable
private fun ResolutionBox(percent: Int, eyeSides: EyeSides, onChosen: (Int) -> Unit, cap: Int? = null, rate: Int = 0) {
    val custom = percent != PerformanceTuning.DEFAULT_RESOLUTION
    // The default sits where the recommended width falls on the screen's, as a percentage.
    val defaultPercent = eyeSides.screen?.let { screen -> eyeSides.recommended?.let { it.first * 100f / screen.first } }
    val shown = if (custom) eyeSides.screen?.let { PerformanceTuning.scaledSizes(it.first, it.second, percent) } else eyeSides.recommended
    val min = PerformanceTuning.MIN_RESOLUTION.toFloat()
    val max = (cap ?: PerformanceTuning.MAX_RESOLUTION).toFloat()
    Column(
        Modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.gameperf_resolution), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.gameperf_resolution_legend), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Back to the default, at the left: outlined and with its icon, dimmed while the default is the choice.
            val markColor = if (custom) DefaultMark else DefaultMark.copy(alpha = 0.3f)
            // While the slider is held down, the default (the system's size) is above the cap too, so it is not offered either.
            val defaultAllowed = cap == null || (defaultPercent ?: 0f) <= cap
            OutlinedIconButton(onClick = { onChosen(PerformanceTuning.DEFAULT_RESOLUTION) }, enabled = custom && defaultAllowed, border = BorderStroke(1.dp, markColor)) {
                Icon(Icons.Filled.Restore, contentDescription = stringResource(R.string.gameperf_resolution_default), tint = markColor)
            }
            Box(
                Modifier.weight(1f).drawWithContent {
                    drawContent()
                    // The mark sits where the slider's track puts that value: the thumb's half width in from each end.
                    defaultPercent?.takeIf { it in min..max }?.let { mark ->
                        val inset = 10.dp.toPx()
                        val x = inset + (mark - min) / (max - min) * (size.width - 2 * inset)
                        drawLine(DefaultMark, Offset(x, size.height / 2 - 11.dp.toPx()), Offset(x, size.height / 2 + 11.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    }
                },
            ) {
                Slider(
                    value = (if (custom) percent.toFloat() else defaultPercent ?: PerformanceTuning.CUSTOM_START.toFloat()).coerceIn(min, max),
                    // Near the amber mark the default is taken, even though it is not a multiple of 5; elsewhere the value goes to the nearest multiple.
                    onValueChange = {
                        onChosen(
                            if (defaultPercent != null && defaultPercent <= max && abs(it - defaultPercent) <= DEFAULT_GRIP) PerformanceTuning.DEFAULT_RESOLUTION
                            else PerformanceTuning.MIN_RESOLUTION + ((it - min) / PerformanceTuning.RESOLUTION_STEP).roundToInt() * PerformanceTuning.RESOLUTION_STEP,
                        )
                    },
                    valueRange = min..max,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.onSurface,
                        activeTrackColor = MaterialTheme.colorScheme.onSurface,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    ),
                )
            }
            Text(if (custom) "$percent %" else stringResource(R.string.gameperf_resolution_default), style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 72.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SizeTile(Modifier.weight(1f), stringResource(R.string.gameperf_resolution_screen), eyeSides.screen, emphasised = false)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            SizeTile(Modifier.weight(1f), stringResource(R.string.gameperf_resolution_shown), shown, emphasised = true)
        }
        Text(stringResource(R.string.gameperf_resolution_per_eye), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        if (cap != null) Text(stringResource(R.string.gameperf_resolution_capped, rate, cap), color = CompatOrange, style = MaterialTheme.typography.bodySmall)
    }
}

/** The image size now, as a percentage of the screen's: the choice, or the size the system recommends when nothing is chosen. */
private fun currentPercent(percent: Int, eyeSides: EyeSides): Int {
    if (percent != PerformanceTuning.DEFAULT_RESOLUTION) return percent
    val screen = eyeSides.screen ?: return 100
    val recommended = eyeSides.recommended ?: return 100
    return recommended.first * 100 / screen.first
}

/** A size in pixels under its name: the screen's quiet, the image shown lit with the amber. */
@Composable
private fun SizeTile(modifier: Modifier, label: String, size: Pair<Int, Int>?, emphasised: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape)
            .background(if (emphasised) DefaultMark.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (emphasised) DefaultMark.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.16f), shape)
            .padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(size?.let { "${it.first} × ${it.second}" } ?: "—", color = if (emphasised) DefaultMark else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
    }
}

/** Something else sets the graphics of this game: it takes over, and nothing here would be applied. */
/** The frame rate chosen did not apply in the last run: the device refused it, the game sets its own, or the frames came at another rate. Doubts are told in orange under the option. */
@Composable
private fun RefreshOutcomeNotice(outcome: app.gameport.core.model.RefreshOutcome) {
    val text = when (outcome.verdict) {
        app.gameport.core.model.RefreshOutcome.Verdict.REFUSED -> stringResource(R.string.gameperf_refresh_refused, outcome.asked)
        app.gameport.core.model.RefreshOutcome.Verdict.GAME_OWN -> stringResource(R.string.gameperf_refresh_game_own, outcome.gameAsked)
        app.gameport.core.model.RefreshOutcome.Verdict.NOT_KEPT -> stringResource(R.string.gameperf_refresh_not_kept, outcome.asked, outcome.observed)
        else -> return
    }
    Row(
        Modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = CompatOrange, modifier = Modifier.size(24.dp))
        Text(text, color = CompatOrange, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OptimizerNotice(state: OptimizerState) {
    Row(
        Modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = CompatOrange, modifier = Modifier.size(24.dp))
        Text(
            stringResource(if (state == OptimizerState.APP) R.string.gameperf_optimizer else R.string.gameperf_optimizer_properties),
            color = CompatOrange,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** A frame rate is chosen on a Quest and the game has to be patched again for the runtime to offer it: the reason, and the button that does it. */
@Composable
private fun PatchAgainRow(onPatchAgain: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = CompatOrange, modifier = Modifier.size(24.dp))
            Text(stringResource(R.string.gameperf_refresh_patch), color = CompatOrange, style = MaterialTheme.typography.bodyMedium)
        }
        GlassButton(onClick = onPatchAgain) {
            Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.gameperf_refresh_patch_action))
        }
    }
}
