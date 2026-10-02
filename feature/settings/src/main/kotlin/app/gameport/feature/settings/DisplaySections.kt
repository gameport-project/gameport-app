package app.gameport.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import app.gameport.core.designsystem.BackdropDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.DangerButton
import app.gameport.core.designsystem.DangerTextButton
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.PillTabs
import app.gameport.core.model.AccentPresets
import app.gameport.core.model.BackdropPresets
import app.gameport.core.model.CoverSize
import app.gameport.core.model.CoverSpacing
import app.gameport.core.model.DisplaySettings
import app.gameport.core.model.HoverAnimation
import app.gameport.core.model.PillSize
import app.gameport.core.model.ContinueScope

private typealias Change = ((DisplaySettings) -> DisplaySettings) -> Unit

/** How the app looks. Every setting has a button on its right that puts it back to the default. */
@Composable
internal fun AppearanceSection(display: DisplaySettings, onChange: Change) {
    val defaults = DisplaySettings()
    Text(stringResource(R.string.settings_category_appearance), style = MaterialTheme.typography.headlineSmall)

    // The main colour of buttons and highlights, and the background of the pages.
    GroupTitle(R.string.settings_group_colors)
    AccentSetting(display, defaults, onChange)
    GradientSetting(display, defaults, onChange)

    GroupTitle(R.string.settings_group_backdrop)
    SwitchRow(R.string.settings_backdrop, R.string.settings_backdrop_description, display.backdrop, defaults.backdrop) { value -> onChange { it.copy(backdrop = value) } }
    if (display.backdrop) {
        Setting(R.string.settings_backdrop_strength, R.string.settings_backdrop_strength_description, display.backdropStrength == defaults.backdropStrength, { onChange { it.copy(backdropStrength = defaults.backdropStrength) } }) { modifier ->
            Slider(
                value = display.backdropStrength.toFloat(),
                onValueChange = { value -> onChange { it.copy(backdropStrength = value.toInt()) } },
                valueRange = 0f..100f,
                modifier = modifier,
            )
        }
    }

    GroupTitle(R.string.settings_group_covers)
    ChoiceRow(
        R.string.settings_cover_size,
        listOf(R.string.settings_size_mini, R.string.settings_size_small, R.string.settings_size_medium, R.string.settings_size_large),
        CoverSize.entries.indexOf(display.coverSize), CoverSize.entries.indexOf(defaults.coverSize),
    ) { index -> onChange { it.copy(coverSize = CoverSize.entries[index]) } }
    ChoiceRow(
        R.string.settings_cover_spacing,
        listOf(R.string.settings_spacing_compact, R.string.settings_spacing_tight, R.string.settings_spacing_normal, R.string.settings_spacing_airy),
        CoverSpacing.entries.indexOf(display.coverSpacing), CoverSpacing.entries.indexOf(defaults.coverSpacing),
    ) { index -> onChange { it.copy(coverSpacing = CoverSpacing.entries[index]) } }
    ChoiceRow(
        R.string.settings_hover,
        listOf(R.string.settings_hover_full, R.string.settings_hover_reduced, R.string.settings_hover_none),
        HoverAnimation.entries.indexOf(display.hoverAnimation), HoverAnimation.entries.indexOf(defaults.hoverAnimation),
        description = R.string.settings_hover_description,
    ) { index -> onChange { it.copy(hoverAnimation = HoverAnimation.entries[index]) } }

    ResetSectionButton(R.string.settings_reset_appearance, R.string.settings_reset_appearance_message) { onChange { it.withDefaultAppearance() } }
}

/** What the home shows. */
@Composable
internal fun HomeSection(display: DisplaySettings, isHeadset: Boolean, onChange: Change) {
    val defaults = DisplaySettings()
    Text(stringResource(R.string.settings_category_home), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.settings_home_description), color = MaterialTheme.colorScheme.onSurfaceVariant)

    GroupTitle(R.string.settings_group_rows)
    SwitchRow(R.string.settings_home_continue, R.string.settings_home_continue_description, display.showContinue, defaults.showContinue) { value -> onChange { it.copy(showContinue = value) } }
    // Only a device with VR has two tabs, so only there is there a choice to make.
    if (isHeadset && display.showContinue) {
        ChoiceRow(
            R.string.settings_continue_scope,
            listOf(R.string.settings_continue_scope_all, R.string.settings_continue_scope_tab),
            ContinueScope.entries.indexOf(display.continueScope), ContinueScope.entries.indexOf(defaults.continueScope),
            description = R.string.settings_continue_scope_description,
        ) { index -> onChange { it.copy(continueScope = ContinueScope.entries[index]) } }
    }
    SwitchRow(R.string.settings_home_favorites, R.string.settings_home_favorites_description, display.showFavorites, defaults.showFavorites) { value -> onChange { it.copy(showFavorites = value) } }
    SwitchRow(R.string.settings_hide_uninstalled, R.string.settings_hide_uninstalled_description, display.hideUninstalled, defaults.hideUninstalled) { value -> onChange { it.copy(hideUninstalled = value) } }

    GroupTitle(R.string.settings_group_cover_names)
    SwitchRow(R.string.settings_cover_titles, R.string.settings_cover_titles_description, display.coverTitles, defaults.coverTitles) { value -> onChange { it.copy(coverTitles = value) } }
    SwitchRow(R.string.settings_hero_title, R.string.settings_hero_title_description, display.heroBanner, defaults.heroBanner) { value -> onChange { it.copy(heroBanner = value) } }
    if (display.heroBanner) {
        ChoiceRow(
            R.string.settings_pill_size,
            listOf(R.string.settings_size_small, R.string.settings_size_medium, R.string.settings_size_large),
            PillSize.entries.indexOf(display.pillSize), PillSize.entries.indexOf(defaults.pillSize),
            description = R.string.settings_pill_size_description,
        ) { index -> onChange { it.copy(pillSize = PillSize.entries[index]) } }
    }

    ResetSectionButton(R.string.settings_reset_home, R.string.settings_reset_home_message) { onChange { it.withDefaultHome() } }
}

@Composable
private fun GradientSetting(display: DisplaySettings, defaults: DisplaySettings, onChange: Change) {
    var picking by rememberSaveable { mutableStateOf<Int?>(null) }
    when (picking) {
        START -> AccentPickerDialog(display.gradientStart, R.string.settings_gradient_start, minBrightness = 0f, onDismiss = { picking = null }) { picked -> picking = null; onChange { it.copy(gradientStart = picked) } }
        END -> AccentPickerDialog(display.gradientEnd, R.string.settings_gradient_end, minBrightness = 0f, onDismiss = { picking = null }) { picked -> picking = null; onChange { it.copy(gradientEnd = picked) } }
    }
    Setting(
        R.string.settings_gradient, R.string.settings_gradient_description,
        display.gradientStart == defaults.gradientStart && display.gradientEnd == defaults.gradientEnd,
        { onChange { it.copy(gradientStart = defaults.gradientStart, gradientEnd = defaults.gradientEnd) } },
    ) { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BackdropPresets.pairs.forEach { (start, end) ->
                    val selected = start == display.gradientStart && end == display.gradientEnd
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(start), Color(end))))
                            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.3f), CircleShape)
                            .clickable { onChange { it.copy(gradientStart = start, gradientEnd = end) } },
                    )
                }
            }
            // Any other pair: each end opens the colour picker.
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_gradient_start))
                Swatch(Color(display.gradientStart), selected = false) { picking = START }
                Text(stringResource(R.string.settings_gradient_end))
                Swatch(Color(display.gradientEnd), selected = false) { picking = END }
            }
        }
    }
}

@Composable
private fun AccentSetting(display: DisplaySettings, defaults: DisplaySettings, onChange: Change) {
    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) AccentPickerDialog(display.accent, R.string.settings_accent_picker_title, onDismiss = { picking = false }) { picked -> picking = false; onChange { it.copy(accent = picked) } }
    val custom = display.accent !in AccentPresets.colors
    Setting(R.string.settings_accent, R.string.settings_accent_description, display.accent == defaults.accent, { onChange { it.copy(accent = defaults.accent) } }) { modifier ->
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AccentPresets.colors.forEach { argb ->
                Swatch(Color(argb), selected = argb == display.accent) { onChange { it.copy(accent = argb) } }
            }
            // The colour the player picked, or a rainbow circle that opens the picker.
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (custom) SolidColor(Color(display.accent)) else Brush.sweepGradient(RAINBOW))
                    .border(if (custom) 3.dp else 1.dp, if (custom) Color.White else Color.White.copy(alpha = 0.3f), CircleShape)
                    .clickable { picking = true },
            )
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.3f), CircleShape)
            .clickable(onClick = onClick),
    )
}

private val RAINBOW = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
private const val START = 0
private const val END = 1

/** A small title over a group of related settings, with a thin line above it. */
@Composable
private fun GroupTitle(title: Int) {
    Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** A setting with its control under the title and a reset button on the right; the button is only there when the value differs from the default. */
@Composable
private fun Setting(title: Int, description: Int?, isDefault: Boolean, onReset: () -> Unit, control: @Composable (Modifier) -> Unit) {
    Column(Modifier.fillMaxWidth().widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            control(Modifier.weight(1f))
            ResetButton(isDefault, onReset)
        }
        if (description != null) Text(stringResource(description), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: Int, description: Int, checked: Boolean, default: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().widthIn(max = 720.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChanged)
        ResetButton(checked == default) { onChanged(default) }
    }
}

/** A setting with a few exclusive values, shown as the pill tabs used elsewhere. */
@Composable
private fun ChoiceRow(title: Int, options: List<Int>, selectedIndex: Int, defaultIndex: Int, description: Int? = null, onSelect: (Int) -> Unit) {
    Setting(title, description, selectedIndex == defaultIndex, { onSelect(defaultIndex) }) { modifier ->
        PillTabs(labels = options.map { stringResource(it) }, selectedIndex = selectedIndex, onSelect = onSelect, modifier = modifier.widthIn(max = 480.dp))
    }
}

/** Puts one setting back to its default; it keeps its place (invisible) when there is nothing to reset, so rows do not shift. */
@Composable
private fun ResetButton(isDefault: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = !isDefault, modifier = Modifier.alpha(if (isDefault) 0f else 1f)) {
        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.settings_reset_option))
    }
}

/** Puts a whole section back to the creator's defaults, after a confirmation. */
@Composable
private fun ResetSectionButton(label: Int, message: Int, onConfirm: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    if (confirming) {
        BackdropDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(label)) },
            text = { Text(stringResource(message)) },
            confirmButton = { DangerButton(onClick = { confirming = false; onConfirm() }) { Text(stringResource(R.string.settings_reset_confirm)) } },
            dismissButton = { DangerTextButton(onClick = { confirming = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
    GlassButton(onClick = { confirming = true }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(label)) }
}
