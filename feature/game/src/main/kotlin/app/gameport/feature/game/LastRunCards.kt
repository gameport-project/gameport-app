package app.gameport.feature.game

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.designsystem.glass
import app.gameport.core.model.RunRules
import app.gameport.core.model.RunStats
import app.gameport.core.settings.GameSettingsStore
import app.gameport.core.sync.ReportStore
import app.gameport.core.sync.Suspicion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** What the last run of a game measured, and what is worth telling of it (see [RunRules]). */
internal data class LastRun(
    val stats: RunStats,
    /** How long the run lasted, in seconds, or 0 when it has not ended. */
    val seconds: Int,
    /** The image the game drew as a percentage of the screen's, the way the resolution slider counts it (175 asked is 175 shown), or null when the screen is not known. */
    val resolutionPercent: Int?,
    /** The image holds clearly more pixels than the device usually gives a game. */
    val highResolution: Boolean,
    /** The game stopped answering while it was slow. */
    val stoppedAnswering: Boolean,
    val crashed: Boolean,
    /** The page tells the player that a lower resolution would help. */
    val adviseLowerResolution: Boolean,
)

/**
 * What the game's last run measured and what to tell of it, or null when the run measured nothing (a game without the OpenXR layer, a flat game, or no run yet). Judged again when a run
 * ends (its duration is only known then), when the player changes the resolution, and when the advice is closed.
 */
internal fun lastRunFlow(
    appId: Int,
    reports: ReportStore,
    settings: GameSettingsStore,
    installer: GameInstallRepository,
    deviceModel: String?,
): Flow<LastRun?> = combine(reports.runs, reports.sessionEnds, reports.adviceClosed, settings.observe(appId), reports.suspected) { runs, _, closed, own, suspected ->
    val packageName = installer.installedPackage(appId) ?: return@combine null
    val stats = runs[packageName] ?: return@combine null
    val seconds = ((reports.lastSessionMillis(packageName) ?: 0L) / 1000L).toInt()
    val crashed = suspected[packageName] == Suspicion.CRASH
    val advise = RunRules.adviseLowerResolution(stats, seconds, deviceModel, own.resolutionPercent, crashed)
    val screen = settings.eyeSides(null).screen
    val drawn = stats.shownImage
    LastRun(
        stats = stats,
        seconds = seconds,
        resolutionPercent = if (screen != null && drawn != null) drawn.first * 100 / screen.first else null,
        highResolution = RunRules.highResolution(stats, deviceModel),
        stoppedAnswering = RunRules.couldNotKeepUp(stats, seconds) && RunRules.stoppedAnswering(stats, seconds),
        crashed = crashed,
        adviseLowerResolution = advise && closed[packageName] != stats.write(),
    )
}

private val RunGreen = Color(0xFF66BB6A)
private val RunOrange = Color(0xFFFFB74D)
private val RunRed = Color(0xFFE57373)

/** Green when the frames asked for were nearly all handed over, orange from half, red under. */
private fun rateColor(share: Float) = when {
    share >= 0.9f -> RunGreen
    share >= 0.5f -> RunOrange
    else -> RunRed
}

/**
 * A value with its icon (colour-coded when it says something), a second figure in a lighter tone beside it, and under them a bar when there is a share to show. A short [tip] says what the
 * line means: it opens when the pointer is over the line and when the controller or the keys put the focus on it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunLine(icon: ImageVector, tip: String, value: String, color: Color, share: Float? = null, detail: String? = null, modifier: Modifier = Modifier, label: String? = null) {
    val state = rememberTooltipState()
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) { if (focused) state.show() else state.dismiss() }
    // The box that takes the place (a share of the line, or its whole width) is the caller's; the tooltip fills it, so it neither widens the line nor moves it.
    Box(modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(tip) } },
            state = state,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.focusable(),
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (label != null) Arrangement.SpaceBetween else Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, contentDescription = tip, tint = color, modifier = Modifier.size(18.dp))
                    if (label != null) Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) else Text(value, style = MaterialTheme.typography.bodyMedium)
                    if (label == null && detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (label != null) Text(value, style = MaterialTheme.typography.bodyMedium)
            }
            if (share != null) {
                LinearProgressIndicator(
                    progress = { share.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                    color = color,
                    trackColor = color.copy(alpha = 0.18f),
                )
            }
            }
        }
    }
}

/** The title of a group of lines, drawn as the one of the card of the size and the version above it (icon and text of the same size, in the accent colour). */
@Composable
private fun RunTitle(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** The image, the frame rate and the memory of the run, each as a line of its own to place where it fits. A line is only there when the run measured it. */
@Composable
private fun PerformanceLines(run: LastRun, line: @Composable (@Composable (Modifier) -> Unit) -> Unit) {
    val stats = run.stats
    val image = stats.shownImage
    if (image != null) {
        line { m ->
            RunLine(
                Icons.Filled.Visibility, stringResource(R.string.game_run_tip_image), stringResource(R.string.game_run_image_plain, image.first, image.second),
                MaterialTheme.colorScheme.primary, detail = run.resolutionPercent?.let { "$it %" }, modifier = m,
            )
        }
    }
    if (stats.hasFrameRate) {
        val share = stats.fpsMedian.toFloat() / stats.fpsTarget
        line { m -> RunLine(Icons.Filled.Speed, stringResource(R.string.game_run_tip_rate), stringResource(R.string.game_run_rate_value, stats.fpsMedian, stats.fpsTarget), rateColor(share), share, modifier = m) }
    }
    if (stats.peakMemoryMb > 0) {
        // What the game held at most, then what the device still had free at the lowest. The bar is the game's share of the two together: the fuller, the nearer to being killed.
        val share = if (stats.minFreeMb >= 0) stats.peakMemoryMb.toFloat() / (stats.peakMemoryMb + stats.minFreeMb) else null
        val color = when {
            share == null -> MaterialTheme.colorScheme.primary
            share >= 0.8f -> RunRed
            share >= 0.6f -> RunOrange
            else -> RunGreen
        }
        val free = if (stats.minFreeMb >= 0) stringResource(R.string.game_run_memory_free_short, megabytes(stats.minFreeMb)) else null
        line { m -> RunLine(Icons.Filled.Memory, stringResource(R.string.game_run_tip_memory), megabytes(stats.peakMemoryMb), color, share, free, m) }
    }
}

private fun LastRun.hasPerformance() = stats.shownImage != null || stats.hasFrameRate || stats.peakMemoryMb > 0

/** The last session on the game's page: the times first, a blank line, then the performance. */
@Composable
internal fun LastRunCard(run: LastRun, modifier: Modifier = Modifier) {
    val stats = run.stats
    val ended = run.crashed || run.stoppedAnswering
    val neutral = MaterialTheme.colorScheme.primary
    Column(modifier.glass(RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RunTitle(stringResource(R.string.game_run_title), Icons.Filled.History)
        if (run.seconds > 0) {
            RunLine(Icons.Filled.Schedule, stringResource(R.string.game_run_tip_duration), if (run.seconds < 60) stringResource(R.string.game_run_seconds, run.seconds) else durationText(run.seconds / 60), neutral, label = stringResource(R.string.game_run_duration))
        }
        if (stats.startupSeconds >= 0) {
            RunLine(Icons.Filled.HourglassBottom, stringResource(R.string.game_run_tip_start), stringResource(R.string.game_run_seconds, stats.startupSeconds), neutral, label = stringResource(R.string.game_run_start))
        }
        if (ended) {
            RunLine(
                Icons.Filled.Warning, stringResource(R.string.game_run_tip_end),
                if (run.crashed) stringResource(R.string.game_run_end_crash) else stringResource(R.string.game_run_end_silent, stats.handedOverSeconds), RunRed,
            )
        }
        if (run.hasPerformance()) {
            Spacer(Modifier.height(6.dp))
            RunTitle(stringResource(R.string.game_run_performance), Icons.Filled.Insights)
            PerformanceLines(run) { it(Modifier) }
        }
    }
}

/** The same performance on the Performance page, the three side by side on one line. */
@Composable
internal fun LastRunPerformanceRow(run: LastRun, modifier: Modifier = Modifier) {
    if (!run.hasPerformance()) return
    Column(modifier.glass(RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RunTitle(stringResource(R.string.game_run_title), Icons.Filled.History)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
            PerformanceLines(run) { it(Modifier.weight(1f)) }
        }
    }
}
