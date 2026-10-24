package app.gameport.feature.settings

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.BackdropDialog
import app.gameport.core.designsystem.CompatOrange
import app.gameport.core.designsystem.DangerButton
import app.gameport.core.designsystem.DangerTrashButton
import app.gameport.core.designsystem.GameImage
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.GlassChip
import app.gameport.core.designsystem.GlassIconButton
import app.gameport.core.designsystem.glass
import app.gameport.core.model.SteamImages

private enum class ManageSort(val label: Int) {
    SIZE(R.string.manage_sort_size),
    PLAYED(R.string.manage_sort_played),
    NAME(R.string.manage_sort_name),
}

private val UpdateGreen = Color(0xFF66BB6A)

/**
 * The installed games with what each takes on the device, and the actions on them: update, patch again, uninstall, one or several at once. The figures are what GamePort can
 * see, and the page says what is counted and what is not, so a total is never taken for more than it is.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ManageGamesSection(
    state: SettingsViewModel.ManageState,
    onOpenGame: (Int) -> Unit,
    onUpdate: (Collection<Int>) -> Unit,
    onPatch: (Collection<Int>) -> Unit,
    onUninstall: (Collection<Int>) -> Unit,
    onDiscard: (Int) -> Unit,
) {
    val context = LocalContext.current
    var sort by rememberSaveable { mutableStateOf(ManageSort.SIZE) }
    var selected by rememberSaveable { mutableStateOf(emptySet<Int>()) }
    var confirming by remember { mutableStateOf<Set<Int>?>(null) }
    var discarding by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val size = { bytes: Long -> Formatter.formatFileSize(context, bytes) }

    Text(stringResource(R.string.settings_category_manage), style = MaterialTheme.typography.headlineSmall)
    if (state.loading) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(stringResource(R.string.manage_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    // What was selected may have been uninstalled since: only games still listed stay selected.
    val shown = state.games.map { it.appId }.toSet()
    val chosen = selected.intersect(shown)

    val total = state.total
    Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(pluralStringResource(R.plurals.manage_summary_games, state.games.size, state.games.size, size(total.bytes)), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.manage_free, size(state.freeBytes)))
        if (state.unfinished.isNotEmpty()) Text(stringResource(R.string.manage_summary_unfinished, size(state.unfinishedBytes)), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (total.withUnknownExpansion > 0) {
            Text(pluralStringResource(R.plurals.manage_unknown_expansion, total.withUnknownExpansion, total.withUnknownExpansion), color = CompatOrange, style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.manage_counted), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }

    if (state.games.isEmpty()) {
        Text(stringResource(R.string.manage_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.manage_sort), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ManageSort.entries.forEach { option ->
                GlassChip(stringResource(option.label), onClick = { sort = option }, accent = if (sort == option) MaterialTheme.colorScheme.primary else null)
            }
        }
        if (chosen.isNotEmpty()) {
            val byId = state.games.associateBy { it.appId }
            val toUpdate = chosen.filter { byId[it]?.updateAvailable == true }
            val toPatch = chosen.filter { byId[it]?.patchBehind == true }
            val chosenBytes = chosen.sumOf { byId[it]?.footprint?.knownBytes ?: 0L }
            Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pluralStringResource(R.plurals.manage_selected, chosen.size, chosen.size, size(chosenBytes)), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(onClick = { onUpdate(toUpdate); selected = emptySet() }, enabled = toUpdate.isNotEmpty()) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.manage_action_update, toUpdate.size), modifier = Modifier.padding(start = 8.dp))
                    }
                    GlassButton(onClick = { onPatch(toPatch); selected = emptySet() }, enabled = toPatch.isNotEmpty()) {
                        Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.manage_action_patch, toPatch.size), modifier = Modifier.padding(start = 8.dp))
                    }
                    DangerButton(onClick = { confirming = chosen }) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.manage_action_uninstall, chosen.size), modifier = Modifier.padding(start = 8.dp))
                    }
                    GlassButton(onClick = { selected = emptySet() }) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.manage_clear), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        val ordered = when (sort) {
            ManageSort.SIZE -> state.games.sortedByDescending { it.footprint.knownBytes }
            ManageSort.PLAYED -> state.games.sortedByDescending { it.lastPlayedMillis ?: Long.MIN_VALUE }
            ManageSort.NAME -> state.games.sortedBy { it.name.lowercase() }
        }
        ordered.forEach { game ->
            ManagedRow(
                game = game,
                checked = game.appId in chosen,
                size = size,
                onChecked = { on -> selected = if (on) chosen + game.appId else chosen - game.appId },
                onOpen = { onOpenGame(game.appId) },
                onUpdate = { onUpdate(listOf(game.appId)) },
                onPatch = { onPatch(listOf(game.appId)) },
                onUninstall = { confirming = setOf(game.appId) },
            )
        }
    }

    if (state.unfinished.isNotEmpty()) {
        Text(stringResource(R.string.manage_unfinished_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.manage_unfinished_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.unfinished.forEach { (name, download) ->
            Row(
                Modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(size(download.bytes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DangerButton(onClick = { discarding = name to download.appId }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.manage_discard), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    confirming?.let { ids ->
        val names = state.games.filter { it.appId in ids }.joinToString(", ") { it.name }
        BackdropDialog(
            onDismissRequest = { confirming = null },
            title = { Text(pluralStringResource(R.plurals.manage_uninstall_title, ids.size, ids.size)) },
            text = { Text(stringResource(R.string.manage_uninstall_text, names)) },
            confirmButton = {
                DangerButton(onClick = { onUninstall(ids); selected = selected - ids; confirming = null }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.manage_uninstall_confirm), modifier = Modifier.padding(start = 8.dp))
                }
            },
            dismissButton = { GlassButton(onClick = { confirming = null }) { Text(stringResource(R.string.manage_cancel)) } },
        )
    }
    discarding?.let { (name, appId) ->
        BackdropDialog(
            onDismissRequest = { discarding = null },
            title = { Text(stringResource(R.string.manage_discard_title)) },
            text = { Text(stringResource(R.string.manage_discard_text, name)) },
            confirmButton = {
                DangerButton(onClick = { onDiscard(appId); discarding = null }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.manage_discard), modifier = Modifier.padding(start = 8.dp))
                }
            },
            dismissButton = { GlassButton(onClick = { discarding = null }) { Text(stringResource(R.string.manage_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManagedRow(
    game: SettingsViewModel.ManagedGame,
    checked: Boolean,
    size: (Long) -> String,
    onChecked: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onUpdate: () -> Unit,
    onPatch: () -> Unit,
    onUninstall: () -> Unit,
) {
    val footprint = game.footprint
    Row(
        Modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        GameImage(
            url = SteamImages.asset(game.appId, "library_600x900.jpg"),
            contentDescription = null,
            modifier = Modifier.size(width = 40.dp, height = 60.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(game.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(size(footprint.knownBytes), style = MaterialTheme.typography.titleSmall)
            Text(
                text = buildString {
                    append(
                        if (footprint.expansionBytes == null) stringResource(R.string.manage_parts_unknown, size(footprint.appBytes))
                        else stringResource(if (footprint.expansionRecorded) R.string.manage_parts_recorded else R.string.manage_parts, size(footprint.appBytes), size(footprint.expansionBytes!!)),
                    )
                    if (footprint.keptBytes > 0) append(" · ").append(stringResource(R.string.manage_kept, size(footprint.keptBytes)))
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = game.lastPlayedMillis?.let { stringResource(R.string.manage_played, DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()) }
                    ?: stringResource(R.string.manage_never_played),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (game.updateAvailable || game.patchBehind) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (game.updateAvailable) GlassChip(stringResource(R.string.manage_update_available), accent = UpdateGreen, textStyle = MaterialTheme.typography.labelSmall, verticalPadding = 2.dp)
                    if (game.patchBehind) GlassChip(stringResource(R.string.manage_patch_behind), accent = CompatOrange, textStyle = MaterialTheme.typography.labelSmall, verticalPadding = 2.dp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (game.updateAvailable) GlassIconButton(onClick = onUpdate) { Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.manage_update)) }
            if (game.patchBehind) GlassIconButton(onClick = onPatch) { Icon(Icons.Filled.Build, contentDescription = stringResource(R.string.manage_patch)) }
            DangerTrashButton(onClick = onUninstall, contentDescription = stringResource(R.string.manage_uninstall))
        }
    }
}
