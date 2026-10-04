package app.gameport.feature.game

import android.text.format.DateUtils
import android.text.format.Formatter
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
import androidx.compose.material.icons.filled.Warning
import app.gameport.core.designsystem.BackdropDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import app.gameport.core.model.SaveDirection
import app.gameport.core.model.SaveFileInfo
import app.gameport.core.model.SaveFileState
import java.text.DateFormat
import java.util.Date

private enum class Confirming { RESTORE, SEND }

/** Where a game's saves stand: this device and Steam Cloud side by side, with the two ways to settle a difference. */
@Composable
fun SavesScreen(onBack: () -> Unit, viewModel: SavesViewModel = hiltViewModel()) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val gameName by viewModel.gameName.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf<Confirming?>(null) }

    confirming?.let { action ->
        val restore = action == Confirming.RESTORE
        BackdropDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(if (restore) R.string.saves_restore_title else R.string.saves_send_title)) },
            text = { Text(stringResource(if (restore) R.string.saves_restore_message else R.string.saves_send_message)) },
            confirmButton = {
                DangerButton(onClick = {
                    confirming = null
                    if (restore) viewModel.onRestore() else viewModel.onSend()
                }) { Text(stringResource(if (restore) R.string.saves_restore_confirm else R.string.saves_send_confirm)) }
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
                GlassIconButton(onClick = viewModel::refresh, enabled = !refreshing) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.saves_refresh))
                }
            }

            Summary(overview.inSync, overview.files.isEmpty(), overview.localSeenMillis, overview.cloudSeenMillis, offline)

            overview.pending?.let { PendingCard(it, onCancel = viewModel::onCancelPending) }

            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(overview.files, key = SaveFileState::path) { FileCard(it) }
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
    Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
private fun FileCard(file: SaveFileState) {
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
        Side(stringResource(R.string.saves_side_cloud), file.cloud)
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
