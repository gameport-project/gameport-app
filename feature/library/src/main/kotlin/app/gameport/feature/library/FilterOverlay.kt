package app.gameport.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.GlassBorder
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.GlassIconButton
import app.gameport.core.designsystem.LocalBackdropColors
import app.gameport.core.model.AppKind

/**
 * The filters, in a panel that slides in from the left over the library. Tapping outside it, the back
 * button or "Apply" closes it; the library behind already follows each choice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterOverlay(open: Boolean, filters: LibraryFilters, onChange: (LibraryFilters) -> Unit, onClose: () -> Unit) {
    BackHandler(enabled = open, onBack = onClose)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            // Floating: a margin on the left, at the top and at the bottom, instead of a panel stuck to the edge.
            modifier = Modifier.align(Alignment.CenterStart).statusBarsPadding().padding(start = 24.dp, top = 24.dp, bottom = 24.dp),
        ) {
            val shape = RoundedCornerShape(28.dp)
            Column(
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(0.92f)
                    .shadow(24.dp, shape)
                    // The same gradient as every page (as the player chose it), not a colour of its own.
                    .background(Brush.linearGradient(LocalBackdropColors.current.toList()), shape)
                    .border(1.dp, GlassBorder, shape)
                    // A tap on the panel must not reach the dimmed area behind it, which closes it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(24.dp),
            ) {
              // Chips and the switch keep their own size: without the 48 dp touch minimum, every group has the same margins.
              CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    GlassIconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.filter_close)) }
                }

                // The groups scroll when there are more of them than room; the header above and the buttons below stay where they are.
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                Group(R.string.filter_type) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AppKind.entries.forEach { kind ->
                            Chip(stringResource(kindLabel(kind)), selected = kind in filters.kinds) { onChange(filters.toggled(kind)) }
                        }
                    }
                }
                Group(R.string.filter_status) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Chip(stringResource(R.string.filter_all), filters.install == InstallFilter.ALL) { onChange(filters.copy(install = InstallFilter.ALL)) }
                        Chip(stringResource(R.string.filter_installed), filters.install == InstallFilter.INSTALLED) { onChange(filters.copy(install = InstallFilter.INSTALLED)) }
                        Chip(stringResource(R.string.filter_not_installed), filters.install == InstallFilter.NOT_INSTALLED) { onChange(filters.copy(install = InstallFilter.NOT_INSTALLED)) }
                    }
                }
                Group(R.string.filter_owner) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Chip(stringResource(R.string.filter_all), filters.owner == OwnerFilter.ALL) { onChange(filters.copy(owner = OwnerFilter.ALL)) }
                        Chip(stringResource(R.string.filter_mine), filters.owner == OwnerFilter.MINE) { onChange(filters.copy(owner = OwnerFilter.MINE)) }
                        Chip(stringResource(R.string.filter_family), filters.owner == OwnerFilter.FAMILY) { onChange(filters.copy(owner = OwnerFilter.FAMILY)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.filter_favorites), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = filters.favoritesOnly, onCheckedChange = { onChange(filters.copy(favoritesOnly = it)) })
                }
                }

                // Each button takes half of the width.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassButton(onClick = { onChange(LibraryFilters()) }, enabled = !filters.isDefault, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.filter_reset)) }
                    Button(onClick = onClose, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.filter_apply)) }
                }
              }
            }
        }
    }
}

@Composable
private fun Group(title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White.copy(alpha = 0.06f),
            labelColor = Color.White,
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
            selectedLabelColor = Color.White,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = GlassBorder,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/** The label of an app kind, shown in the filters and on the covers. */
internal fun kindLabel(kind: AppKind): Int = when (kind) {
    AppKind.GAME -> R.string.library_kind_game
    AppKind.DEMO -> R.string.library_kind_demo
    AppKind.BETA -> R.string.library_kind_beta
}
