package app.gameport.whatsnew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.R
import app.gameport.core.designsystem.BackdropDialog
import app.gameport.core.designsystem.DangerRed
import app.gameport.core.designsystem.DangerButton
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.GoldTrophy
import app.gameport.core.designsystem.HideRed
import app.gameport.core.designsystem.dialogMaxHeight
import app.gameport.core.model.WindowItem
import app.gameport.core.model.pick

/** The news of a new GamePort, when there are some to show. */
@Composable
fun WhatsNewHost(viewModel: WhatsNewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The news first; once it is closed, the games confirmed as incompatible that the player was not told about yet.
    if (state != null) {
        WhatsNewDialog(state!!, onPatchAll = viewModel::onPatchAll, onLater = viewModel::onLater, onStop = viewModel::onStop, onClose = viewModel::onClose)
    } else {
        IncompatibleNoticeHost()
    }
}

/**
 * What changes in this version, and the games confirmed to work. When games have to be patched again, a red alert says so, and three buttons
 * (patch all, see later, got it) share the width; otherwise one button validates. While patching, one button stops it. It is never closed by a press outside or by Back.
 */
@Composable
internal fun WhatsNewDialog(state: WhatsNewState, onPatchAll: () -> Unit, onLater: () -> Unit, onStop: () -> Unit, onClose: () -> Unit) {
    val progress = state.progress
    val running = progress != null && !progress.finished
    val finished = progress?.finished == true
    BackdropDialog(
        onDismissRequest = {},
        // About 70 % of the height of the screen at most: what does not fit scrolls.
        maxHeight = dialogMaxHeight(),
        title = { Text(stringResource(R.string.whatsnew_title, state.version)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.preview) {
                    Text(stringResource(R.string.whatsnew_preview), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
                when {
                    running -> Running(state, progress!!.done, progress.total)
                    finished -> Finished(state)
                    else -> Intro(state)
                }
            }
        },
        // The alert stays in view above the buttons, whatever the scrolling of the news.
        pinned = if (!running && !finished && state.games.isNotEmpty()) {
            { Alert(pluralStringResource(R.plurals.whatsnew_alert_title, state.games.size, state.games.size)) }
        } else null,
        confirmButton = {
            // The buttons share the whole width, whatever their number.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    running -> StopAction(onStop)
                    finished -> Action(onClose, Icons.Filled.Close, R.string.whatsnew_close)
                    state.games.isNotEmpty() -> {
                        Action(onPatchAll, Icons.Filled.Build, R.string.whatsnew_patch_all)
                        Action(onLater, Icons.Filled.Schedule, R.string.whatsnew_later)
                        Action(onClose, Icons.Filled.Check, R.string.whatsnew_got_it)
                    }
                    else -> Action(onClose, Icons.Filled.Check, R.string.whatsnew_got_it)
                }
            }
        },
    )
}

@Composable
private fun RowScope.Action(onClick: () -> Unit, icon: ImageVector, text: Int) {
    // Little padding, so a label like "See later" fits the third of the width it gets.
    GlassButton(onClick = onClick, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(text), maxLines = 1)
    }
}

/** Stops the patching; red, as it throws work away. */
@Composable
private fun RowScope.StopAction(onClick: () -> Unit) {
    DangerButton(onClick = onClick, modifier = Modifier.weight(1f)) {
        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.whatsnew_stop), maxLines = 1)
    }
}

@Composable
private fun Intro(state: WhatsNewState) {
    // The news first, then what has to be done about the games.
    Text(stringResource(R.string.whatsnew_in_this_version), style = MaterialTheme.typography.titleSmall)
    state.items.forEach { item -> Feature(item) }
    state.compat?.let { compat ->
        // What was tested, then what is covered without having been tried.
        val language = currentLanguage()
        Text(compat.title.pick(language), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        Text(compat.testedLabel.pick(language) + " " + compat.tested.joinToString(", ") + ".")
        Text(compat.others.pick(language))
    }
    if (state.games.isNotEmpty()) {
        Text(stringResource(R.string.whatsnew_games), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        Text(state.games.joinToString(", "))
        if (state.onScreen.isNotEmpty()) {
            Text(stringResource(R.string.whatsnew_on_screen, state.onScreen.joinToString(", ")), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.whatsnew_android_note), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

/** One novelty: its picture in a tile, then its text. */
@Composable
private fun Feature(item: WindowItem) {
    val shape = RoundedCornerShape(12.dp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(44.dp).clip(shape).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.18f), shape),
            contentAlignment = Alignment.Center,
        ) {
            WindowIcon(item.icon)
        }
        Text(item.text.pick(currentLanguage()), modifier = Modifier.weight(1f))
    }
}

/** A red notice that stands out from the rest of the text, short and tight as it stays in view above the buttons. */
@Composable
private fun Alert(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(DangerRed.copy(alpha = 0.18f)).border(1.dp, DangerRed.copy(alpha = 0.6f), shape).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = HideRed, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Running(state: WhatsNewState, done: Int, total: Int) {
    Text(stringResource(R.string.whatsnew_running, (done + 1).coerceAtMost(total), total))
    state.currentName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
    LinearProgressIndicator(progress = { if (total == 0) 0f else done / total.toFloat() }, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.whatsnew_android_note), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Finished(state: WhatsNewState) {
    if (state.failedNames.isEmpty()) {
        Text(stringResource(R.string.whatsnew_done))
    } else {
        Text(stringResource(R.string.whatsnew_partial, state.failedNames.joinToString(", ")))
    }
}

/** The language the texts of the release files are read in: the one GamePort is shown in. */
@Composable
private fun currentLanguage(): String = LocalConfiguration.current.locales[0].language

/** One of the pictures the release files may name (see [app.gameport.core.model.Release.ICONS]). */
@Composable
private fun WindowIcon(name: String) {
    val tint = MaterialTheme.colorScheme.primary
    val size = Modifier.size(26.dp)
    when (name) {
        "trophy" -> GoldTrophy(28.dp)
        "cloud-sync" -> Icon(Icons.Filled.CloudSync, contentDescription = null, tint = tint, modifier = size)
        "key" -> Icon(Icons.Filled.Key, contentDescription = null, tint = tint, modifier = size)
        "check" -> Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF66BB6A), modifier = size)
        "download" -> Icon(Icons.Filled.Download, contentDescription = null, tint = tint, modifier = size)
        "play" -> Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = tint, modifier = size)
        "warning" -> Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFFB74D), modifier = size)
        "build" -> Icon(Icons.Filled.Build, contentDescription = null, tint = tint, modifier = size)
        "trash" -> Icon(Icons.Filled.Delete, contentDescription = null, tint = HideRed, modifier = size)
        else -> Icon(Icons.Filled.Info, contentDescription = null, tint = tint, modifier = size)
    }
}
