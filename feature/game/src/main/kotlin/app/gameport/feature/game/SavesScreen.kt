package app.gameport.feature.game

import app.gameport.core.designsystem.TourIds
import app.gameport.core.designsystem.tourTarget
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import app.gameport.core.designsystem.BackdropDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.core.designsystem.AttentionBadge
import app.gameport.core.designsystem.BackButton
import app.gameport.core.designsystem.DangerButton
import app.gameport.core.designsystem.DangerTextButton
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.GlassChip
import app.gameport.core.designsystem.GlassIconButton
import app.gameport.core.designsystem.glass
import app.gameport.core.model.PcSaves
import app.gameport.core.model.SaveDirection
import app.gameport.core.model.SaveFileInfo
import app.gameport.core.model.SaveOverview
import app.gameport.core.model.SaveFileState
import java.text.DateFormat
import java.util.Date

private enum class Confirming { RESTORE, SEND, USE_PC, LINK_PC, RESTORE_PC, CLEAR_LOCAL }

/** What a confirmation asks and does. */
private class DialogSpec(val title: Int, val message: Int, val confirm: Int, val onConfirm: () -> Unit)

/** Where a game's saves stand: this device and Steam Cloud side by side, with the two ways to settle a difference. */
@Composable
fun SavesScreen(onBack: () -> Unit, viewModel: SavesViewModel = hiltViewModel()) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val gameName by viewModel.gameName.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    SavesContent(
        overview, gameName, refreshing, offline, onBack, viewModel::refresh, viewModel::onRestore, viewModel::onSend, viewModel::onCancelPending,
        busy = busy,
        failed = failed,
        onUsePcChanged = viewModel::onUsePcChanged,
        onClearLocal = viewModel::onClearLocal,
        cleared = viewModel.cleared.collectAsStateWithLifecycle().value,
        onLinkPc = viewModel::onLinkPc,
        onUnlinkPc = viewModel::onUnlinkPc,
        onRestorePc = viewModel::onRestorePc,
    )
}

/** The page of the saves, from what it shows: the screen feeds it from the game, the tour of the page feeds it made-up values. */
@Composable
internal fun SavesContent(
    overview: SaveOverview,
    gameName: String,
    refreshing: Boolean,
    offline: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: () -> Unit,
    onSend: () -> Unit,
    onCancelPending: () -> Unit,
    busy: PcBusy? = null,
    failed: PcBusy? = null,
    onUsePcChanged: (Boolean) -> Unit = {},
    onClearLocal: () -> Unit = {},
    cleared: Int? = null,
    onLinkPc: () -> Unit = {},
    onUnlinkPc: () -> Unit = {},
    onRestorePc: () -> Unit = {},
) {
    var confirming by remember { mutableStateOf<Confirming?>(null) }
    // The Android saves are shown unless the player asks to see the Windows ones.
    var showPc by remember { mutableStateOf(false) }
    // The options for the Windows saves are folded: a block of switches is not what most players need on this page.
    var advanced by remember { mutableStateOf(false) }
    val pc = overview.pc
    val viewingPc = showPc && pc != null
    val shownFiles = if (viewingPc) pc!!.files else overview.files

    confirming?.let { action ->
        val spec = when (action) {
            Confirming.RESTORE -> DialogSpec(R.string.saves_restore_title, R.string.saves_restore_message, R.string.saves_restore_confirm, onRestore)
            Confirming.SEND -> DialogSpec(R.string.saves_send_title, R.string.saves_send_message, R.string.saves_send_confirm, onSend)
            Confirming.USE_PC -> DialogSpec(R.string.saves_pc_use_title, R.string.saves_pc_use_message, R.string.saves_pc_use_confirm) { onUsePcChanged(true) }
            Confirming.LINK_PC -> DialogSpec(R.string.saves_pc_link_title, R.string.saves_pc_link_message, R.string.saves_pc_link_confirm, onLinkPc)
            Confirming.RESTORE_PC -> DialogSpec(R.string.saves_pc_restore_title, R.string.saves_pc_restore_message, R.string.saves_pc_restore_confirm, onRestorePc)
            Confirming.CLEAR_LOCAL -> DialogSpec(R.string.saves_clear_title, R.string.saves_clear_message, R.string.saves_clear_confirm, onClearLocal)
        }
        BackdropDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(spec.title)) },
            text = { Text(stringResource(spec.message)) },
            confirmButton = {
                DangerButton(onClick = {
                    confirming = null
                    spec.onConfirm()
                }) { Text(stringResource(spec.confirm)) }
            },
            dismissButton = { DangerTextButton(onClick = { confirming = null }) { Text(stringResource(R.string.game_settings_cancel)) } },
        )
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onClick = onBack)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.saves_title), style = MaterialTheme.typography.headlineMedium)
                    Text(gameName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (refreshing) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                GlassIconButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.tourTarget(TourIds.SAVES_REFRESH)) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.saves_refresh))
                }
                app.gameport.core.designsystem.TourHelpButton(app.gameport.core.model.TourKind.GAME_SAVES)
            }

            // Everything above the buttons scrolls together: the files stay reachable whatever the height of the cards.
            LazyColumn(Modifier.weight(1f).tourTarget(TourIds.SAVES_FILES), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item(key = "summary") {
                    Summary(
                        if (viewingPc) pc!!.files.isNotEmpty() && pc.files.all { it.identical } else overview.inSync,
                        shownFiles.isEmpty(),
                        overview.localSeenMillis,
                        overview.cloudSeenMillis,
                        offline,
                    )
                }
                overview.pending?.let { direction -> item(key = "pending") { PendingCard(direction, onCancel = onCancelPending) } }
                if (pc != null) {
                    item(key = "windows") {
                        PcCard(
                            pc, viewingPc, advanced, busy, failed,
                            onToggleOpen = {
                                advanced = !advanced
                                // Closing the options while the Windows saves are listed would leave no way back to the Android ones.
                                if (!advanced) showPc = false
                            },
                            onShowChanged = { showPc = it },
                            onUseChanged = { on -> if (on) confirming = Confirming.USE_PC else onUsePcChanged(false) },
                            onLinkChanged = { on -> if (on) confirming = Confirming.LINK_PC else onUnlinkPc() },
                            onRestore = { confirming = Confirming.RESTORE_PC },
                            cleared = cleared,
                            onClear = { confirming = Confirming.CLEAR_LOCAL },
                        )
                    }
                }
                items(shownFiles, key = SaveFileState::path) { FileCard(it, cloudLabel = if (viewingPc) R.string.saves_pc_side else R.string.saves_side_cloud) }
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp).tourTarget(TourIds.SAVES_ACTIONS), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton(onClick = { confirming = Confirming.RESTORE }, enabled = overview.hasCloud && overview.pending == null) {
                    Text(stringResource(R.string.saves_restore))
                }
                GlassButton(onClick = { confirming = Confirming.SEND }, enabled = overview.hasLocal && overview.pending == null) {
                    Text(stringResource(R.string.saves_send))
                }
            }
        }
    }
}

@Composable
private fun Summary(inSync: Boolean, empty: Boolean, localSeen: Long, cloudSeen: Long, offline: Boolean) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    fun ago(millis: Long) = DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString()
    Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).tourTarget(TourIds.SAVES_SUMMARY).glass(RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(
                when {
                    empty -> R.string.saves_empty
                    inSync -> R.string.saves_in_sync
                    else -> R.string.saves_differ
                },
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        if (empty) Text(stringResource(R.string.saves_empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (localSeen > 0) Text(stringResource(R.string.saves_local_seen, ago(localSeen)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (cloudSeen > 0) Text(stringResource(R.string.saves_cloud_seen, ago(cloudSeen)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (offline) Text(stringResource(R.string.saves_offline), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/** A choice with a switch, a title and a sentence under it, like the settings. */
@Composable
private fun SwitchRow(title: Int, description: Int, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(description), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** The advanced options of the saves: a line the width of the page, that unfolds, like the notes of an update. */
@Composable
private fun PcCard(
    pc: PcSaves,
    viewing: Boolean,
    open: Boolean,
    busy: PcBusy?,
    failed: PcBusy?,
    onToggleOpen: () -> Unit,
    onShowChanged: (Boolean) -> Unit,
    onUseChanged: (Boolean) -> Unit,
    onLinkChanged: (Boolean) -> Unit,
    onRestore: () -> Unit,
    cleared: Int?,
    onClear: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().widthIn(max = 720.dp).glass(shape).animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The whole line opens and closes it.
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggleOpen).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.saves_advanced), style = MaterialTheme.typography.titleMedium)
                // Said while the options are folded, until the player chose to use the PC saves: without them the game starts with no save for a
                // player who has played on a PC.
                if (!pc.usePc) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = NOT_SYNCED_ORANGE, modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(if (pc.hasFiles) R.string.saves_pc_detected_recoverable else R.string.saves_pc_detected),
                            color = NOT_SYNCED_ORANGE,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                // What is turned on stays readable while the options are folded.
                if (pc.usePc) {
                    Text(
                        stringResource(if (pc.sendPc) R.string.saves_pc_linked else R.string.saves_pc_active),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.saves_advanced),
            )
        }
        if (open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.saves_pc_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(if (pc.hasFiles) R.string.saves_pc_description else R.string.saves_pc_none),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                SwitchRow(R.string.saves_pc_show, R.string.saves_pc_show_description, viewing, enabled = true, onChange = onShowChanged)
                SwitchRow(
                    R.string.saves_pc_use, R.string.saves_pc_use_description, pc.usePc,
                    enabled = busy == null && (pc.hasFiles || pc.usePc), onChange = onUseChanged,
                )
                // Writing to the Windows saves goes with using them: a start that does not read them first could overwrite what the PC has done.
                SwitchRow(
                    R.string.saves_pc_link, R.string.saves_pc_link_description, pc.sendPc,
                    enabled = busy == null && pc.usePc, onChange = onLinkChanged,
                )
                busy?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(
                                when (it) {
                                    PcBusy.LINKING -> R.string.saves_pc_working_link
                                    PcBusy.RESTORING -> R.string.saves_pc_working_restore
                                    PcBusy.CLEARING -> R.string.saves_clear_working
                                },
                            ),
                        )
                    }
                }
                failed?.let {
                    Text(
                        stringResource(
                            when (it) {
                                PcBusy.LINKING -> R.string.saves_pc_failed_link
                                PcBusy.RESTORING -> R.string.saves_pc_failed_restore
                                PcBusy.CLEARING -> R.string.saves_clear_failed
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (pc.backupMillis > 0) {
                    Text(
                        stringResource(
                            R.string.saves_pc_backup,
                            DateUtils.getRelativeTimeSpanString(pc.backupMillis, now, DateUtils.MINUTE_IN_MILLIS).toString(),
                            pc.backupFiles,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    GlassButton(onClick = onRestore, enabled = busy == null) { Text(stringResource(R.string.saves_pc_restore)) }
                }
                // Starting again from Steam on this device, for a test or a clean start: nothing is removed from Steam.
                Text(stringResource(R.string.saves_clear_local), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.saves_clear_local_description), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                // Red: it moves the saves of this device away. Nothing is removed from Steam.
                DangerButton(onClick = onClear, enabled = busy == null) { Text(stringResource(R.string.saves_clear_local)) }
                cleared?.let { Text(stringResource(R.string.saves_clear_done, it), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun PendingCard(direction: SaveDirection, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AttentionBadge()
        Text(
            stringResource(if (direction == SaveDirection.RESTORE_FROM_CLOUD) R.string.saves_pending_restore else R.string.saves_pending_send),
            modifier = Modifier.weight(1f),
        )
        DangerButton(onClick = onCancel) { Text(stringResource(R.string.game_settings_cancel)) }
    }
}

@Composable
private fun FileCard(file: SaveFileState, cloudLabel: Int = R.string.saves_side_cloud) {
    Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(file.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            val local = file.local
            val cloud = file.cloud
            val label = when {
                file.identical -> R.string.saves_state_identical
                local == null -> R.string.saves_state_cloud_only
                cloud == null -> R.string.saves_state_local_only
                local.modifiedMillis >= cloud.modifiedMillis -> R.string.saves_state_local_newer
                else -> R.string.saves_state_cloud_newer
            }
            if (file.identical) {
                GlassChip(
                    stringResource(label),
                    contentColor = SYNCED_GREEN,
                    accent = SYNCED_GREEN,
                    verticalPadding = 1.dp,
                    leading = { Icon(Icons.Filled.Check, contentDescription = null, tint = SYNCED_GREEN, modifier = Modifier.size(18.dp)) },
                )
            } else {
                GlassChip(
                    stringResource(label),
                    contentColor = NOT_SYNCED_ORANGE,
                    accent = NOT_SYNCED_ORANGE,
                    verticalPadding = 1.dp,
                    leading = { Icon(Icons.Filled.Warning, contentDescription = null, tint = NOT_SYNCED_ORANGE, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        // The same content under other dates: a game rewrites its files at every start, which says nothing about the content. Compared as shown.
        if (file.identical && formatted(file.local) != formatted(file.cloud)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = NOT_SYNCED_ORANGE, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.saves_identical_detail), color = NOT_SYNCED_ORANGE, style = MaterialTheme.typography.bodySmall)
            }
        }
        Side(stringResource(R.string.saves_side_local), file.local)
        Side(stringResource(cloudLabel), file.cloud)
    }
}

@Composable
private fun Side(label: String, info: SaveFileInfo?) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.widthIn(min = 110.dp))
        Text(
            if (info == null) stringResource(R.string.saves_missing)
            else "${Formatter.formatFileSize(context, info.size)} · ${formatted(info)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatted(info: SaveFileInfo?): String =
    info?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.modifiedMillis)) }.orEmpty()

private val SYNCED_GREEN = Color(0xFF66BB6A)
private val NOT_SYNCED_ORANGE = Color(0xFFFF9800)
