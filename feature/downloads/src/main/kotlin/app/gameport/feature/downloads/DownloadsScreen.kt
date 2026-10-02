@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.gameport.feature.downloads

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import app.gameport.core.designsystem.installErrorText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.core.designsystem.BackButton
import app.gameport.core.designsystem.DangerButton
import app.gameport.core.designsystem.DangerTrashButton
import app.gameport.core.designsystem.GameImage
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.GlassChip
import app.gameport.core.designsystem.glass
import app.gameport.core.designsystem.speedText
import app.gameport.core.model.InstallState
import app.gameport.core.model.SpeedUnit

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onGameClick: (Int) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val speedUnit by viewModel.speedUnit.collectAsStateWithLifecycle()
    val updatable by viewModel.updatable.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onClick = onBack)
                Text(stringResource(R.string.downloads_title), style = MaterialTheme.typography.headlineMedium)
            }
            if (entries.isEmpty()) {
                Text(stringResource(R.string.downloads_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val waiting = entries.filter { it.appId in updatable && it.state is InstallState.Installed && it.game != null }
            // The list is ordered by what needs doing first, and the order changes once Steam has answered about
            // updates. A list that keeps its place on the item it showed would leave the new first row above the
            // top, so a list still near the top follows the change.
            val listState = rememberLazyListState()
            LaunchedEffect(entries.firstOrNull()?.appId, waiting.size > 1) {
                if (listState.firstVisibleItemIndex <= 2) listState.scrollToItem(0)
            }
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // One game with an update has its own line and button below; the summary is only for several.
                if (waiting.size > 1) {
                    item(key = "updates") {
                        Row(
                            Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.downloads_update_many, waiting.size),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Button(onClick = { waiting.forEach(viewModel::onUpdate) }) {
                                Text(stringResource(R.string.downloads_update_all))
                            }
                        }
                    }
                }
                items(entries, key = DownloadEntry::appId) { entry ->
                    EntryRow(
                        entry = entry,
                        speedUnit = speedUnit,
                        updateAvailable = entry.appId in updatable,
                        onUpdate = { viewModel.onUpdate(entry) },
                        onOpen = { onGameClick(entry.appId) },
                        onCancel = { viewModel.onCancel(entry.appId) },
                        onPause = { viewModel.onPause(entry.appId) },
                        onDiscard = { viewModel.onDiscard(entry.appId) },
                        onUninstall = { viewModel.onUninstall(entry.appId) },
                        onResume = { viewModel.onResume(entry) },
                        onPlay = { viewModel.launchIntent(entry)?.let(context::startActivity) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: DownloadEntry,
    speedUnit: SpeedUnit,
    updateAvailable: Boolean,
    onUpdate: () -> Unit,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onDiscard: () -> Unit,
    onUninstall: () -> Unit,
    onResume: () -> Unit,
    onPlay: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(12.dp)) {
        val compact = maxWidth < COMPACT_WIDTH
        val cover: @Composable () -> Unit = {
            Box(Modifier.width(54.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))) {
                entry.game?.let { GameImage(it.capsuleUrl, null, Modifier.fillMaxSize(), fallbackUrl = it.capsuleFallbacks.firstOrNull(), moreFallbacks = it.capsuleFallbacks.drop(1)) }
            }
        }
        val info: @Composable ColumnScope.() -> Unit = {
            // The name is plain text that opens the game, so it lines up with the rest instead of sitting inside a button.
            Text(
                entry.game?.name ?: stringResource(R.string.downloads_unknown_game, entry.appId),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.bleed(NAME_BLEED).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen).padding(horizontal = NAME_BLEED),
            )
            when (val state = entry.state) {
                InstallState.Queued -> Text(stringResource(R.string.downloads_queued))
                is InstallState.Downloading -> {
                    val percent = stringResource(R.string.downloads_downloading, (state.progress * 100).toInt())
                    if (state.verifying) Text(stringResource(R.string.downloads_verifying, (state.progress * 100).toInt()))
                    else Text(if (state.bytesPerSecond > 0) "$percent · ${speedText(state.bytesPerSecond, speedUnit)}" else percent)
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
                is InstallState.ChoosingVersion -> Text(stringResource(R.string.downloads_choose_version))
                InstallState.Patching -> Text(stringResource(R.string.downloads_patching))
                InstallState.Installing -> Text(stringResource(R.string.downloads_installing))
                InstallState.Interrupted -> Text(stringResource(R.string.downloads_interrupted))
                is InstallState.Failed -> Text(installErrorText(state.error), color = MaterialTheme.colorScheme.error)
                is InstallState.Installed ->
                    if (updateAvailable) GlassChip(stringResource(R.string.downloads_update_available), accent = Color(0xFF66BB6A))
                    else Text(stringResource(R.string.downloads_installed))
                InstallState.NotInstalled -> Unit
            }
        }
        val actions: @Composable () -> Unit = {
            when (entry.state) {
                InstallState.Queued, is InstallState.Downloading -> {
                    if (entry.state is InstallState.Downloading) GlassButton(onClick = onPause) { Text(stringResource(R.string.downloads_pause)) }
                    DangerButton(onClick = onCancel) { Text(stringResource(R.string.downloads_cancel)) }
                }
                InstallState.Interrupted, is InstallState.Failed -> {
                    if (entry.game != null) Button(onClick = onResume) { Text(stringResource(R.string.downloads_resume)) }
                    DangerButton(onClick = onDiscard) { Text(stringResource(R.string.downloads_discard)) }
                }
                is InstallState.Installed -> {
                    if (updateAvailable) GlassButton(onClick = onUpdate) { Text(stringResource(R.string.downloads_update)) }
                    Button(onClick = onPlay) { Text(stringResource(R.string.downloads_play)) }
                    DangerTrashButton(onClick = onUninstall, contentDescription = stringResource(R.string.downloads_uninstall))
                }
                else -> Unit
            }
        }
        if (compact) {
            // Narrow: the name and its state on top, the actions under them.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                    cover()
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = info)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                cover()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = info)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            }
        }
    }
}

/** Below this width an entry puts its actions under the name. */
private val COMPACT_WIDTH = 600.dp


/** The highlight that shows when the name is pointed at reaches this far past the text, on each side. */
private val NAME_BLEED = 8.dp

/**
 * Lets the content (and what is drawn around it, such as a hover highlight) extend [horizontal] past
 * each side of the space it takes in the layout: the text beside it does not move.
 */
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx()
    val wider = if (constraints.hasBoundedWidth) constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth + 2 * extra) else constraints
    val placeable = measurable.measure(wider)
    layout((placeable.width - 2 * extra).coerceAtLeast(0), placeable.height) { placeable.place(-extra, 0) }
}
