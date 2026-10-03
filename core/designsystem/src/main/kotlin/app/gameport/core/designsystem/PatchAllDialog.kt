package app.gameport.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.model.PatchAllState

/**
 * Where "patch all" stands: each game of the run, with what became of it, and the progress as a whole. Closing the window does not stop
 * the patching, which goes on in the background; [onStop] does. Reopened from the button that started it.
 */
@Composable
fun PatchAllDialog(state: PatchAllState, names: Map<Int, String>, onClose: () -> Unit, onStop: () -> Unit) {
    val running = !state.finished
    BackdropDialog(
        // A press outside only hides the window.
        onDismissRequest = onClose,
        maxHeight = dialogMaxHeight(),
        title = { Text(stringResource(R.string.patch_all_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (running) {
                    Text(stringResource(R.string.patch_all_running, (state.done + 1).coerceAtMost(state.total), state.total))
                    LinearProgressIndicator(progress = { if (state.total == 0) 0f else state.done / state.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.patch_all_background), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } else if (state.failed.isEmpty()) {
                    Text(stringResource(R.string.patch_all_done))
                } else {
                    Text(stringResource(R.string.patch_all_partial, state.failed.size))
                }
                state.ids.forEachIndexed { index, appId ->
                    val failed = appId in state.failed
                    val current = running && appId == state.current
                    val done = !failed && !current && (index < state.done || state.finished)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        when {
                            current -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            failed -> Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.patch_all_failed), tint = HideRed, modifier = Modifier.size(18.dp))
                            done -> Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.patch_all_patched), modifier = Modifier.size(18.dp))
                            else -> Icon(Icons.Filled.Schedule, contentDescription = stringResource(R.string.patch_all_waiting), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            names[appId] ?: "#$appId",
                            style = if (current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                            color = if (current || done || failed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (failed(state)) {
                    Text(stringResource(R.string.patch_all_failed_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            GlassButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.patch_all_close))
            }
        },
        dismissButton = {
            if (running) {
                DangerButton(onClick = onStop) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.patch_all_stop))
                }
            }
        },
    )
}

private fun failed(state: PatchAllState) = state.finished && state.failed.isNotEmpty()
