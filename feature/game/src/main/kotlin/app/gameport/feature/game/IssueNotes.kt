package app.gameport.feature.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.gameport.core.model.GameIssue

/**
 * One line of the card of the notifications: its words, the colour of its dot and words, what tapping the line does (nothing when null), and up to two buttons: [action] (and
 * [secondAction], which sits before it). [actionEnabled] greys [action] while something else is going on.
 */
internal class NoteItem(
    val text: String,
    val dot: Color,
    val textColor: Color,
    val onClick: (() -> Unit)? = null,
    val action: String? = null,
    val actionEnabled: Boolean = true,
    val secondAction: String? = null,
    val onSecond: () -> Unit = {},
)

/** The colours of the notifications: orange for what needs attention, green for a new version, blue for a notice and for a question. */
internal object NoteColors {
    val Orange = Color(0xFFFF9800)
    val OrangeText = Color(0xFFFFB74D)
    val Green = Color(0xFF4CAF50)
    val GreenText = Color(0xFFA5D6A7)
    val Info = Color(0xFF90CAF9)
    val InfoText = Color(0xFFBBDEFB)
    val Question = Color(0xFF42A5F5)
}

/**
 * What the list of issues of a game tells the player, as lines of the card of the notifications, each with the way to deal with it. A new version, a newer patch and a problem that
 * can be reported are not here: the first two have their own lines, and the report is offered on the page itself. [busy]: a patch or an update is going on, so the buttons are greyed.
 */
@Composable
internal fun issueNotes(
    issues: List<GameIssue>,
    busy: Boolean,
    onPermissions: () -> Unit,
    onConflict: () -> Unit,
    onSaves: () -> Unit,
    onControllers: () -> Unit,
    onActivateFrame: () -> Unit,
    onAskRepair: () -> Unit,
    onAskReinstall: () -> Unit,
    onReinstallWorks: () -> Unit,
): List<NoteItem> = issues.mapNotNull { issue ->
    when (issue) {
        GameIssue.UpdateAvailable, GameIssue.PatchOutdated, is GameIssue.ProblemSuspected -> null
        GameIssue.ReinstallMayHelp -> NoteItem(
            stringResource(R.string.issue_reinstall_may_help), NoteColors.Question, NoteColors.InfoText,
            action = stringResource(R.string.issue_reinstall_not_working), onClick = onAskReinstall, actionEnabled = !busy,
            secondAction = stringResource(R.string.issue_reinstall_working), onSecond = onReinstallWorks,
        )
        is GameIssue.ExpansionMissing -> NoteItem(
            pluralStringResource(R.plurals.issue_expansion_missing, issue.missing, issue.missing), NoteColors.Orange, NoteColors.OrangeText,
            onClick = onAskRepair, action = stringResource(R.string.issue_expansion_unchecked_action), actionEnabled = !busy,
        )
        GameIssue.ExpansionMayBeMissing -> NoteItem(
            stringResource(R.string.issue_expansion_unchecked), NoteColors.Info, NoteColors.InfoText,
            onClick = onAskRepair, action = stringResource(R.string.issue_expansion_unchecked_action), actionEnabled = !busy,
        )
        GameIssue.StoragePermissionMissing -> NoteItem(
            stringResource(R.string.issue_storage_permission), NoteColors.Orange, NoteColors.OrangeText,
            onClick = onPermissions, action = stringResource(R.string.game_storage_open), actionEnabled = !busy,
        )
        GameIssue.SaveConflict -> NoteItem(
            stringResource(R.string.issue_save_conflict), NoteColors.Orange, NoteColors.OrangeText,
            onClick = onConflict, action = stringResource(R.string.issue_save_conflict_action), actionEnabled = !busy,
        )
        GameIssue.SavesApart -> NoteItem(
            stringResource(R.string.issue_saves_apart), NoteColors.Info, NoteColors.InfoText,
            onClick = onSaves, action = stringResource(R.string.issue_saves_apart_action), actionEnabled = !busy,
        )
        is GameIssue.SaveSyncFailed -> NoteItem(
            stringResource(if (issue.offline) R.string.issue_sync_offline else R.string.issue_sync_failed), NoteColors.Orange, NoteColors.OrangeText,
        )
        is GameIssue.ControllerSilent ->
            if (issue.activated) {
                NoteItem(
                    stringResource(R.string.issue_controllers_silent_done), NoteColors.Info, NoteColors.InfoText,
                    onClick = onControllers, action = stringResource(R.string.issue_controllers_silent_open), actionEnabled = !busy,
                )
            } else {
                NoteItem(
                    stringResource(R.string.issue_controllers_silent), NoteColors.Info, NoteColors.InfoText,
                    onClick = onActivateFrame, action = stringResource(R.string.issue_controllers_silent_action), actionEnabled = !busy,
                )
            }
        is GameIssue.ControllerMappingAvailable -> NoteItem(
            stringResource(R.string.issue_controllers, sourceFamilyName(issue.source)), NoteColors.Info, NoteColors.InfoText,
            onClick = onControllers, action = stringResource(R.string.issue_controllers_action), actionEnabled = !busy,
        )
    }
}
