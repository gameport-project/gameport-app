package app.gameport.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import app.gameport.core.designsystem.BackdropDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.GlassChip
import app.gameport.core.sync.ReportStatus
import app.gameport.core.sync.Suspicion

/** Where the problem report stands. */
internal sealed interface ReportProgress {
    data object Idle : ReportProgress

    data object Working : ReportProgress

    /** The report was made and handed to the sender; [status] is where it stands. */
    data class Done(val status: ReportStatus) : ReportProgress
}

/** What the game page needs to offer a problem report. */
internal class ReportActions(
    val suspicion: Suspicion?,
    val progress: ReportProgress,
    val onSend: () -> Unit,
    val onTicket: () -> Unit,
    val onDismissProblem: () -> Unit,
    val onResetProgress: () -> Unit,
) {
    companion object {
        val None = ReportActions(null, ReportProgress.Idle, {}, {}, {}, {})
    }
}

@Composable
internal fun ReportDialog(gameName: String, report: ReportActions, onClose: () -> Unit) {
    BackdropDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.report_message, gameName))
                Text(stringResource(R.string.report_contents), color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (val progress = report.progress) {
                    ReportProgress.Idle -> Unit
                    ReportProgress.Working -> {
                        Text(stringResource(R.string.report_working))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    is ReportProgress.Done -> ReportOutcome(progress.status)
                }
                // The two buttons share the width of the window.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Button(onClick = report.onSend, enabled = report.progress != ReportProgress.Working, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.report_send)) }
                    OutlinedButton(onClick = report.onTicket, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.report_ticket)) }
                }
            }
        },
        confirmButton = { OutlinedButton(onClick = onClose) { Text(stringResource(R.string.report_close)) } },
    )
}

/** What became of the report the player sent. */
@Composable
private fun ReportOutcome(status: ReportStatus) {
    val (text, error) = when (status) {
        ReportStatus.SENT -> R.string.report_status_sent to false
        ReportStatus.WAITING -> R.string.report_status_waiting to false
        ReportStatus.REFUSED, ReportStatus.OFF -> R.string.report_status_refused to true
        ReportStatus.UNAVAILABLE -> R.string.report_status_unavailable to true
    }
    if (error) Text(stringResource(text), color = MaterialTheme.colorScheme.error) else GlassChip(stringResource(text))
}
