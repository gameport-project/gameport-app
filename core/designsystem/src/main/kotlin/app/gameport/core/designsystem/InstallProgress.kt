package app.gameport.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.model.InstallStage
import app.gameport.core.model.InstallState
import app.gameport.core.model.SpeedUnit

private fun InstallStage.label(): Int = when (this) {
    InstallStage.DOWNLOAD -> R.string.install_step_download
    InstallStage.PATCH -> R.string.install_step_patch
    InstallStage.INSTALL -> R.string.install_step_install
    InstallStage.FINISH -> R.string.install_step_finish
}

/**
 * The steps of an install, with the current one highlighted and the earlier ones ticked. The game's page and the downloads page show the same
 * thing, so a game reads the same wherever it is looked at.
 */
@Composable
fun InstallStepper(stage: InstallStage, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InstallStage.entries.forEach { entry ->
            val done = entry.ordinal < stage.ordinal
            val color = when {
                entry == stage -> MaterialTheme.colorScheme.primary
                done -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            // Only the tick of a finished step is green; its words keep the usual colour. The tick is an icon, not a character: a character from another font makes the line taller and the
            // words move down when it arrives.
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (done) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.Check,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text("${entry.ordinal + 1}.", style = MaterialTheme.typography.labelLarge, color = color)
                }
                Text(stringResource(entry.label()), style = MaterialTheme.typography.labelLarge, color = color)
            }
        }
    }
}

/** What an install says it is doing, in words, or null when [state] is not a step of an install. The same words on every page. */
@Composable
fun installStatusText(state: InstallState, speedUnit: SpeedUnit): String? = when (state) {
    InstallState.Queued -> stringResource(R.string.install_queued)
    is InstallState.Downloading -> {
        val percent = (state.progress * 100).toInt()
        when {
            state.verifying -> stringResource(R.string.install_verifying, percent)
            state.bytesPerSecond > 0 -> "$percent% · ${speedText(state.bytesPerSecond, speedUnit)}" + (state.etaSeconds?.let { " · ${etaText(it)}" } ?: "")
            else -> "$percent%"
        }
    }
    InstallState.Patching -> stringResource(R.string.install_patching)
    InstallState.Installing -> stringResource(R.string.install_installing)
    InstallState.Finishing -> stringResource(R.string.install_finishing)
    is InstallState.ExtraContent -> stringResource(R.string.install_extra, state.index, state.total, (state.progress * 100).toInt())
    else -> null
}

/**
 * What an install says it is doing, on one line whatever it says: the words of [installStatusText], and for the extra content being put in place after the game its name, cut with
 * an ellipsis when it is long, before a figure that stays where it is. So the area of the install keeps its height from one step to the next.
 */
@Composable
fun InstallStatusLine(state: InstallState, speedUnit: SpeedUnit, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current) {
    if (state is InstallState.ExtraContent) {
        androidx.compose.foundation.layout.Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Text(state.name, style = style, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            androidx.compose.material3.Text(installStatusText(state, speedUnit).orEmpty(), style = style, maxLines = 1)
        }
    } else {
        installStatusText(state, speedUnit)?.let { androidx.compose.material3.Text(it, modifier, style = style) }
    }
}

/** The time left of a download in words: under a minute, minutes, then hours and minutes (rounded up to the minute, so it never says 0). */
@Composable
fun etaText(seconds: Long): String {
    val minutes = (seconds + 59) / 60
    return when {
        minutes <= 1 -> stringResource(R.string.install_eta_under_minute)
        minutes < 60 -> stringResource(R.string.install_eta_minutes, minutes.toInt())
        else -> stringResource(R.string.install_eta_hours, (minutes / 60).toInt(), (minutes % 60).toInt())
    }
}
