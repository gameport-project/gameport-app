package app.gameport.feature.game

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
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
import app.gameport.core.sync.Suspicion

/** Where the problem report stands. */
internal sealed interface ReportProgress {
    data object Idle : ReportProgress

    data object Working : ReportProgress

    data class Saved(val fileName: String, val uri: Uri) : ReportProgress

    data object Failed : ReportProgress
}

/** What the game page needs to offer a problem report. */
internal class ReportActions(
    val suspicion: Suspicion?,
    val progress: ReportProgress,
    val onSave: () -> Unit,
    val onTicket: () -> Unit,
    val onShare: () -> Unit,
    val onDismissProblem: () -> Unit,
    val onResetProgress: () -> Unit,
) {
    companion object {
        val None = ReportActions(null, ReportProgress.Idle, {}, {}, {}, {}, {})
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
                    is ReportProgress.Saved -> GlassChip(stringResource(R.string.report_saved, progress.fileName))
                    ReportProgress.Failed -> Text(stringResource(R.string.report_failed), color = MaterialTheme.colorScheme.error)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (report.progress is ReportProgress.Saved) {
                        OutlinedButton(onClick = report.onShare) { Text(stringResource(R.string.report_share)) }
                    } else {
                        Button(onClick = report.onSave, enabled = report.progress != ReportProgress.Working) { Text(stringResource(R.string.report_save)) }
                    }
                    OutlinedButton(onClick = report.onTicket) { Text(stringResource(R.string.report_ticket)) }
                }
            }
        },
        confirmButton = { OutlinedButton(onClick = onClose) { Text(stringResource(R.string.report_close)) } },
    )
}
