package app.gameport.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
            Text(
                text = (if (done) "✓ " else "${entry.ordinal + 1}. ") + stringResource(entry.label()),
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    entry == stage -> MaterialTheme.colorScheme.primary
                    done -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
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
            state.bytesPerSecond > 0 -> "$percent% · ${speedText(state.bytesPerSecond, speedUnit)}"
            else -> "$percent%"
        }
    }
    InstallState.Patching -> stringResource(R.string.install_patching)
    InstallState.Installing -> stringResource(R.string.install_installing)
    InstallState.Finishing -> stringResource(R.string.install_finishing)
    else -> null
}
