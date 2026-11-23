package app.gameport.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.BackdropDialog
import app.gameport.core.designsystem.DiscordNameField
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.model.DiscordName
import app.gameport.core.model.LibraryChoice
import app.gameport.core.sync.LibraryProgress
import app.gameport.core.sync.LibraryResult
import app.gameport.core.sync.LibraryStage

/** Where the sending of a game's libraries stands. */
internal sealed interface LibrarySend {
    data object Idle : LibrarySend

    /** The window that lists the files and asks first; [pick] is null when the game is not installed. */
    data class Asking(val pick: LibraryChoice.Pick?) : LibrarySend

    /** The report is being made, then sent: [progress] says which step and how far it went. */
    data class Working(val progress: LibraryProgress) : LibrarySend

    data class Done(val result: LibraryResult) : LibrarySend
}

/**
 * The page of the game's settings that sends the libraries of its files with a report, for a player who chose to help find why the game does not work. A Discord name is required: without
 * it the button does nothing. The files that go are listed first, and a bar follows each step of the sending.
 */
@Composable
internal fun LibrariesSection(state: LibrarySend, savedName: String, onAsk: () -> Unit, onSend: (String) -> Unit, onClose: () -> Unit) {
    var name by rememberSaveable(savedName) { mutableStateOf(savedName) }
    Text(stringResource(R.string.game_settings_libraries), style = MaterialTheme.typography.headlineSmall)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.libraries_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        DiscordNameField(name, { name = it }, why = R.string.libraries_name_why, label = app.gameport.core.designsystem.R.string.discord_name_label_required)
        GlassButton(onClick = onAsk, enabled = DiscordName.clean(name).isNotEmpty()) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.libraries_send))
        }
    }
    if (state !is LibrarySend.Idle) LibrariesDialog(state, onSend = { onSend(name) }, onClose = onClose)
}

@Composable
private fun LibrariesDialog(state: LibrarySend, onSend: () -> Unit, onClose: () -> Unit) {
    val working = state is LibrarySend.Working
    val canSend = state is LibrarySend.Asking && state.pick?.included?.isNotEmpty() == true
    val cancel: @Composable () -> Unit = { GlassButton(onClick = onClose) { Text(stringResource(R.string.game_settings_cancel)) } }
    BackdropDialog(
        // The sending is not interrupted halfway: the window stays until it ends.
        onDismissRequest = { if (!working) onClose() },
        title = { Text(stringResource(R.string.libraries_ask_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    is LibrarySend.Asking -> {
                        val pick = state.pick
                        if (pick == null || pick.included.isEmpty()) {
                            Text(stringResource(R.string.libraries_ask_none))
                        } else {
                            Text(stringResource(R.string.libraries_ask_summary, pick.included.size, sizeText(pick.bytes)))
                            if (pick.left.isNotEmpty()) Text(stringResource(R.string.libraries_ask_left, pick.left.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // The names, so the player sees exactly what goes: a list that scrolls when it is long.
                            Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                                pick.included.forEach { Text("${it.path.substringAfterLast('/')}  ·  ${sizeText(it.stored)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Text(stringResource(R.string.libraries_ask_privacy), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    is LibrarySend.Working -> {
                        val progress = state.progress
                        Text(stringResource(if (progress.stage == LibraryStage.PREPARING) R.string.libraries_preparing else R.string.libraries_sending))
                        LinearProgressIndicator(progress = { progress.fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("${(progress.fraction.coerceIn(0f, 1f) * 100).toInt()} %", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is LibrarySend.Done -> {
                        val error = state.result != LibraryResult.SENT
                        Text(
                            stringResource(
                                when (state.result) {
                                    LibraryResult.SENT -> R.string.libraries_done_sent
                                    LibraryResult.LATER -> R.string.libraries_done_later
                                    LibraryResult.REFUSED -> R.string.libraries_done_refused
                                    LibraryResult.UNAVAILABLE -> R.string.libraries_done_unavailable
                                    LibraryResult.NO_NAME -> R.string.libraries_done_noname
                                },
                            ),
                            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    LibrarySend.Idle -> Unit
                }
            }
        },
        confirmButton = {
            when {
                canSend -> GlassButton(onClick = onSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.libraries_send))
                }
                !working -> GlassButton(onClick = onClose) { Text(stringResource(R.string.report_close)) }
            }
        },
        dismissButton = if (canSend) cancel else null,
    )
}

/** A size in kilobytes under a megabyte, in megabytes above (decimal, as the files are counted everywhere else on the page). */
@Composable
private fun sizeText(bytes: Long): String =
    if (bytes >= 1_000_000) stringResource(R.string.libraries_size_mb, bytes / 1_000_000.0) else stringResource(R.string.libraries_size_kb, maxOf(1L, bytes / 1_000).toInt())
