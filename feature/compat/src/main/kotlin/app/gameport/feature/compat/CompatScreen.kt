package app.gameport.feature.compat

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.core.designsystem.CompatGlyph
import app.gameport.core.designsystem.GameImage
import app.gameport.core.designsystem.Glyph
import app.gameport.core.designsystem.GlassChip
import app.gameport.core.designsystem.GlassSearchField
import app.gameport.core.designsystem.glass
import app.gameport.core.model.CompatRules
import app.gameport.core.model.SteamImages

/**
 * What the players say of each game, device by device: the numbers behind the marks on the covers of the home. A game of the account opens its own
 * page, a game the account does not have opens the same page, which sends to Steam instead of installing.
 */
@Composable
fun CompatScreen(onBack: () -> Unit, onGameClick: (Int) -> Unit, viewModel: CompatViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp)) {
            CompatHeader(onBack, stringResource(R.string.compat_subtitle))
            when (val current = state) {
                CompatUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                CompatUiState.Unavailable -> Text(stringResource(R.string.compat_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                is CompatUiState.Content -> {
                    GlassSearchField(
                        value = current.query,
                        onValueChange = viewModel::onQueryChanged,
                        placeholder = stringResource(R.string.compat_search_hint),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )
                    Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(CompatFilter.WORKS, current.filter, R.string.compat_filter_works, Glyph.CHECK, CompatGreen, viewModel::onFilterChosen)
                        FilterChip(CompatFilter.FAILS, current.filter, R.string.compat_filter_fails, Glyph.CROSS, CompatRed, viewModel::onFilterChosen)
                    }
                    if (current.entries.isEmpty()) {
                        Text(stringResource(if (current.query.isBlank()) R.string.compat_empty else R.string.compat_no_match), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        // Two columns where the screen is wide, one where it is not. Cards are laid in rows of one height, so that a card unfolded
                        // stretches its neighbour; the bottom margin matches the top one.
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val columns = maxOf(1, ((maxWidth + CARD_GAP) / (CARD_MIN_WIDTH + CARD_GAP)).toInt())
                            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                                items(current.entries.chunked(columns), key = { row -> row.first().counts.appId }) { row ->
                                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                                        row.forEach { entry ->
                                            CompatCard(entry, current.deviceKind, Modifier.weight(1f).fillMaxHeight()) { onGameClick(entry.counts.appId) }
                                        }
                                        // The last row keeps the width of its cards.
                                        repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A game of the table: the numbers for the device in hand, and on request for the others. */
@Composable
private fun CompatCard(entry: CompatEntry, deviceKind: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val appId = entry.counts.appId
    var showAll by rememberSaveable(appId) { mutableStateOf(false) }
    // What the players of this kind of device say: the colour of the outline and the label at the bottom right.
    val verdict = CompatRules.of(entry.counts, deviceKind)
    val outline = verdict?.let { mark(it.level).first }
    val devices = devicesOf(entry.counts, deviceKind)
    val others = devices.filter { it.first != deviceKind }
    Row(
        modifier
            .glass(shape)
            .then(if (outline != null) Modifier.border(1.5.dp, outline.copy(alpha = 0.85f), shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GameImage(
            url = SteamImages.asset(appId, "library_600x900.jpg"),
            contentDescription = null,
            modifier = Modifier.size(width = 72.dp, height = 108.dp).clip(RoundedCornerShape(10.dp)),
        )
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                entry.name ?: stringResource(R.string.compat_unknown_game, appId),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pluralStringResource(R.plurals.compat_answers, entry.players, entry.players),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            // The device in hand always shows, with the small arrow that unfolds the others; the arrow is there on every card, greyed when there are none.
            devices.filter { it.first == deviceKind || showAll }.forEach { (kind, own) ->
                DeviceLine(
                    entry.counts, kind, own, mine = kind == deviceKind,
                    trailing = if (kind == deviceKind) {
                        { UnfoldButton(open = showAll, enabled = others.isNotEmpty(), onClick = { showAll = !showAll }) }
                    } else null,
                )
            }
            // The label stays at the bottom right, whatever the height the card takes.
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f))
                if (verdict != null) VerdictChip(verdict.level, deviceName(deviceKind))
            }
        }
    }
}

/** A small arrow, beside the device in hand, that unfolds the numbers for the other devices; it points up while they are shown. */
@Composable
private fun UnfoldButton(open: Boolean, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(28.dp)) {
        Icon(
            if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(if (open) R.string.compat_hide_others else R.string.compat_show_others),
            modifier = Modifier.size(22.dp),
            tint = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f),
        )
    }
}

/** A quick filter: its mark is always in its colour, and the whole chip takes the colour while the filter is on; touching it again takes it off. */
@Composable
private fun FilterChip(filter: CompatFilter, chosen: CompatFilter, label: Int, glyph: Glyph, colour: Color, onChosen: (CompatFilter) -> Unit) {
    val capitals = with(LocalDensity.current) { (MaterialTheme.typography.labelLarge.fontSize * CAPITAL_HEIGHT).toDp() }
    GlassChip(
        stringResource(label),
        onClick = { onChosen(filter) },
        accent = if (chosen == filter) colour else null,
        leading = { CompatGlyph(glyph, colour, capitals) },
    )
}

private val CompatGreen = Color(0xFF66BB6A)
private val CompatRed = Color(0xFFE57373)
private val CARD_MIN_WIDTH = 420.dp
private val CARD_GAP = 12.dp
