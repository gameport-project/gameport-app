package app.gameport.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.BackdropDialog
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.model.ObbTroubleshoot
import app.gameport.core.model.TroubleshootAdvice
import app.gameport.core.model.TroubleshootOutcome
import app.gameport.core.model.Troubleshooting

/** The troubleshooting window of a game: closed, checking, or telling what the checks came to. */
sealed interface TroubleshootUi {
    data object Closed : TroubleshootUi
    data object Checking : TroubleshootUi
    data class Done(val result: Troubleshooting) : TroubleshootUi
}

/** What the page needs to run the checks and show them: the state of the window, what starts the checks, and what closes the window. */
data class TroubleshootActions(
    val state: TroubleshootUi = TroubleshootUi.Closed,
    val onStart: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * The window of the troubleshooting, in the player's own background. While the checks run it says so. Afterwards it says what was put right (and offers to start the game), or
 * that nothing was found, or that the game has to be given its files again: then a button leads to the question that does it (the repair when the game said its files are
 * missing, the reinstallation otherwise), nothing is removed from here.
 */
@Composable
fun TroubleshootDialog(actions: TroubleshootActions, onPlay: () -> Unit, onAskRepair: () -> Unit, onAskReinstall: () -> Unit) {
    when (val state = actions.state) {
        TroubleshootUi.Closed -> Unit
        TroubleshootUi.Checking -> BackdropDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.troubleshoot_title)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.troubleshoot_checking))
                }
            },
        )
        is TroubleshootUi.Done -> {
            val result = state.result
            BackdropDialog(
                onDismissRequest = actions.onClose,
                title = { Text(stringResource(R.string.troubleshoot_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(headline(result)))
                        result.reinstall?.let { Text(stringResource(reasonText(it)), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (result.fixed.isNotEmpty()) {
                            Text(pluralStringResource(R.plurals.troubleshoot_fixed_files, result.fixed.size, result.fixed.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        result.advice.forEach { Text(stringResource(adviceText(it)), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                confirmButton = {
                    when (troubleshootButton(result)) {
                        TroubleshootButton.REPAIR -> Button(onClick = { actions.onClose(); onAskRepair() }) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.troubleshoot_repair))
                        }
                        TroubleshootButton.REINSTALL -> Button(onClick = { actions.onClose(); onAskReinstall() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.troubleshoot_reinstall))
                        }
                        TroubleshootButton.PLAY -> Button(onClick = { actions.onClose(); onPlay() }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.troubleshoot_play))
                        }
                        TroubleshootButton.NONE -> Unit
                    }
                },
                dismissButton = {
                    GlassButton(onClick = actions.onClose) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.troubleshoot_close))
                    }
                },
            )
        }
    }
}

/** The one button the window offers beside Close, by what the checks came to. */
internal enum class TroubleshootButton { REPAIR, REINSTALL, PLAY, NONE }

/** The repair when the game said its files are missing, the reinstallation for the other reasons, Play when files were put right, nothing when there is nothing to do. */
internal fun troubleshootButton(result: Troubleshooting): TroubleshootButton = when {
    result.reinstall == ObbTroubleshoot.ReinstallReason.REPORTED_MISSING -> TroubleshootButton.REPAIR
    result.reinstall != null -> TroubleshootButton.REINSTALL
    result.outcome == TroubleshootOutcome.FIXED -> TroubleshootButton.PLAY
    else -> TroubleshootButton.NONE
}

private fun headline(result: Troubleshooting): Int = when (result.outcome) {
    TroubleshootOutcome.NEEDS_REINSTALL -> R.string.troubleshoot_needs_files
    TroubleshootOutcome.FIXED -> R.string.troubleshoot_fixed
    TroubleshootOutcome.NOTHING_FOUND -> if (result.advice.isEmpty()) R.string.troubleshoot_nothing else R.string.troubleshoot_nothing_by_itself
}

private fun reasonText(reason: ObbTroubleshoot.ReinstallReason): Int = when (reason) {
    ObbTroubleshoot.ReinstallReason.NO_MAIN_FILE -> R.string.troubleshoot_reason_no_main
    ObbTroubleshoot.ReinstallReason.EMPTY_FILE -> R.string.troubleshoot_reason_empty
    ObbTroubleshoot.ReinstallReason.REPORTED_MISSING -> R.string.troubleshoot_reason_reported
}

private fun adviceText(advice: TroubleshootAdvice): Int = when (advice) {
    TroubleshootAdvice.PATCH_OUTDATED -> R.string.troubleshoot_advice_patch
    TroubleshootAdvice.STORAGE_PERMISSION -> R.string.troubleshoot_advice_storage
    TroubleshootAdvice.EXPANSION_NOT_WRITABLE -> R.string.troubleshoot_advice_not_writable
}
