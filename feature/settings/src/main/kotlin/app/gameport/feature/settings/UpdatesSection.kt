package app.gameport.feature.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.install.UpdateGate
import app.gameport.core.model.AppUpdateFailure
import app.gameport.core.model.AppUpdateState
import app.gameport.core.model.NoteBlock
import app.gameport.core.model.ReleaseNotes
import app.gameport.core.designsystem.NoteImage

/** The installed version, the state of the search for a newer one, and the switch for the automatic search. */
@Composable
internal fun UpdatesSection(
    installedVersion: String,
    state: AppUpdateState,
    blocker: UpdateGate.Blocker?,
    canUpdateInPlace: Boolean,
    checkHours: Int,
    onCheckHoursChanged: (Int) -> Unit,
    onCheck: () -> Unit,
    onUpdate: () -> Unit,
) {
    val french = LocalConfiguration.current.locales[0].language == "fr"
    val busy = state is AppUpdateState.Checking || state is AppUpdateState.Downloading || state is AppUpdateState.Installing
    Text(stringResource(R.string.settings_category_updates), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.settings_updates_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.settings_updates_installed, installedVersion), style = MaterialTheme.typography.titleMedium)

    val statusColor = if (state is AppUpdateState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val status = when (state) {
        AppUpdateState.Idle -> stringResource(R.string.settings_updates_never)
        AppUpdateState.Checking -> stringResource(R.string.settings_updates_checking)
        is AppUpdateState.UpToDate -> stringResource(R.string.settings_updates_up_to_date, ago(state.checkedAtMillis))
        is AppUpdateState.Available -> stringResource(R.string.settings_updates_available, state.release.version)
        is AppUpdateState.Downloading -> stringResource(R.string.settings_updates_downloading, state.release.version, (state.progress * 100).toInt())
        is AppUpdateState.Installing -> stringResource(R.string.settings_updates_installing, state.release.version)
        is AppUpdateState.Failed -> stringResource(
            when (state.reason) {
                AppUpdateFailure.OFFLINE -> R.string.settings_updates_failed_offline
                AppUpdateFailure.DOWNLOAD -> R.string.settings_updates_failed_download
                AppUpdateFailure.NOT_ENOUGH_SPACE -> R.string.settings_updates_failed_space
                AppUpdateFailure.DIFFERENT_SIGNATURE -> R.string.settings_updates_failed_signature
                AppUpdateFailure.REFUSED -> R.string.settings_updates_failed_refused
                AppUpdateFailure.OTHER -> R.string.settings_updates_failed_other
            },
        )
    }
    Text(status, color = statusColor)
    if (state is AppUpdateState.Downloading) {
        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp))
    }

    val release = when (state) {
        is AppUpdateState.Available -> state.release
        is AppUpdateState.Downloading -> state.release
        is AppUpdateState.Installing -> state.release
        is AppUpdateState.Failed -> state.release
        else -> null
    }
    release?.notes?.let { ReleaseNotes.whatsNewBlocks(it, french, "https://raw.githubusercontent.com/gameport-project/gameport-app/${release.tag}") }?.takeIf { it.isNotEmpty() }?.let { blocks ->
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_updates_whats_new), style = MaterialTheme.typography.titleMedium)
            blocks.forEach { block ->
                when (block) {
                    is NoteBlock.Line -> Text(block.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    is NoteBlock.Image -> NoteImage(block.url, block.description)
                }
            }
        }
    }

    val offered = state is AppUpdateState.Available || (state is AppUpdateState.Failed && state.release != null)
    if (offered && !canUpdateInPlace) {
        Text(stringResource(R.string.settings_updates_dev_build), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    blocker?.let {
        Text(
            stringResource(if (it == UpdateGate.Blocker.GAMES_BUSY) R.string.settings_updates_blocked_games else R.string.settings_updates_blocked_sync),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (offered && canUpdateInPlace) {
            val version = release?.version.orEmpty()
            Button(onClick = onUpdate, enabled = !busy && blocker == null) { Text(stringResource(R.string.settings_updates_update, version)) }
        }
        OutlinedButton(onClick = onCheck, enabled = !busy) { Text(stringResource(R.string.settings_updates_check)) }
    }

    var choosing by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().widthIn(max = 720.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_updates_auto), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_updates_auto_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            OutlinedButton(onClick = { choosing = true }, contentPadding = PaddingValues(start = 16.dp, end = 8.dp)) {
                Text(intervalLabel(checkHours))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(24.dp))
            }
            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                CHECK_HOURS.forEach { hours ->
                    DropdownMenuItem(text = { Text(intervalLabel(hours)) }, onClick = { onCheckHoursChanged(hours); choosing = false })
                }
            }
        }
    }
}

/** The choices for how often the app opens with a look for a new version; 0 is never. */
private val CHECK_HOURS = listOf(2, 4, 6, 8, 12, 0)

@Composable
private fun intervalLabel(hours: Int): String =
    if (hours <= 0) stringResource(R.string.settings_updates_never_check) else stringResource(R.string.settings_updates_every, hours)

@Composable
private fun ago(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
