package app.gameport.feature.game

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.GlassChip
import app.gameport.core.designsystem.SymptomPicker
import app.gameport.core.model.Symptom
import app.gameport.core.sync.ReportStatus
import app.gameport.core.sync.Suspicion

/** The longest description the field takes: what the report keeps (see ProblemReporter.MAX_DESCRIPTION). */
private const val MAX_DESCRIPTION_SHOWN = 2000

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
    /** The description, what was ticked, the Discord name (empty for none), and whether the game's libraries go with the report. */
    val onSend: (String, Set<Symptom>, String, Boolean) -> Unit,
    val onTicket: () -> Unit,
    val onDismissProblem: () -> Unit,
    val onResetProgress: () -> Unit,
    /** The Discord name given with an earlier report: it fills the field, so it is never asked again and can still be changed. */
    val discordName: String = "",
    /** A frame rate the device does not accept was taken off the game: the page says so, with the rates it accepts, until the note is closed. */
    val rateReset: app.gameport.core.model.RateReset? = null,
    val onDismissRateReset: () -> Unit = {},
    /** What the game's last run measured (null when it measured nothing), and the way to close the advice on the resolution it may lead to. */
    val lastRun: LastRun? = null,
    val onCloseResolutionAdvice: () -> Unit = {},
    /** The libraries that would go with the report (null while not known, or when the game is not installed). */
    val librariesPlan: suspend () -> app.gameport.core.model.LibraryChoice.Pick? = { null },
) {
    companion object {
        val None = ReportActions(null, ReportProgress.Idle, { _, _, _, _ -> }, {}, {}, {})
    }
}

@Composable
internal fun ReportDialog(gameName: String, report: ReportActions, onClose: () -> Unit) {
    var description by rememberSaveable { mutableStateOf("") }
    var codes by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val symptoms = Symptom.parse(codes)
    // The name is filled in from the last report, so it is never asked again; what is typed here replaces it when the report is sent.
    var discord by rememberSaveable(report.discordName) { mutableStateOf(report.discordName) }
    // The game's libraries can go in the same report; the project then needs a Discord name to talk about them.
    val plan by produceState<app.gameport.core.model.LibraryChoice.Pick?>(null) { value = report.librariesPlan() }
    var withLibraries by rememberSaveable { mutableStateOf(false) }
    val libraries = withLibraries && plan?.included?.isNotEmpty() == true
    val nameMissing = libraries && app.gameport.core.model.DiscordName.clean(discord).isEmpty()
    // Once the report has reached the project the window stays, so the Discord button can be reached: the close button ends it.
    val progress = report.progress
    val sent = progress is ReportProgress.Done && progress.status == ReportStatus.SENT
    BackdropDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.report_title)) },
        text = {
            if (sent) Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                app.gameport.core.designsystem.ReportSentMark()
                Text(stringResource(R.string.report_sent_discord))
                app.gameport.core.designsystem.DiscordButton()
            } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.report_message, gameName))
                // The long list of what the report holds is there on request, so the window is not a wall of text.
                var showContents by rememberSaveable { mutableStateOf(false) }
                app.gameport.core.designsystem.GlassChip(
                    label = stringResource(if (showContents) R.string.report_contents_hide else R.string.report_contents_show),
                    onClick = { showContents = !showContents },
                    leading = { Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    verticalPadding = 8.dp,
                )
                if (showContents) Text(stringResource(R.string.report_contents), color = MaterialTheme.colorScheme.onSurfaceVariant)
                SymptomPicker(selected = symptoms, onToggle = { symptom -> codes = (if (symptom in symptoms) symptoms - symptom else symptoms + symptom).map { it.code } })
                // Optional, and said to matter: the files show how the game ran, only the player knows what was seen.
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(MAX_DESCRIPTION_SHOWN) },
                    label = { Text(stringResource(R.string.report_description_label)) },
                    supportingText = { Text(stringResource(R.string.report_description_why)) },
                    minLines = 2,
                    maxLines = 5,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = app.gameport.core.designsystem.glassTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Optional, and kept for the next time: the way for the project to talk to the player about their answers.
                app.gameport.core.designsystem.LibrariesTick(plan, withLibraries, onCheckedChange = { withLibraries = it })
                if (libraries) app.gameport.core.designsystem.DiscordNameField(discord, { discord = it }, label = app.gameport.core.designsystem.R.string.discord_name_label_required)
                else app.gameport.core.designsystem.DiscordNameField(discord, { discord = it })
                when (val progress = report.progress) {
                    ReportProgress.Idle -> Unit
                    ReportProgress.Working -> {
                        Text(stringResource(R.string.report_working))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    is ReportProgress.Done -> {
                        ReportOutcome(progress.status)
                        // A report that did not go is the case where the Discord is the way left to be heard.
                        app.gameport.core.designsystem.DiscordButton()
                    }
                }
                // The two buttons share the width of the window.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Button(onClick = { report.onSend(description, symptoms, discord, libraries) }, enabled = report.progress != ReportProgress.Working && !nameMissing, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.report_send)) }
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
