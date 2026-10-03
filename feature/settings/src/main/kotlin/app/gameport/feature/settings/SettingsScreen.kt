package app.gameport.feature.settings

import app.gameport.core.designsystem.DangerTextButton
import app.gameport.core.designsystem.GameImage
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.model.Game
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Spacer
import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import app.gameport.core.designsystem.PillTabs
import app.gameport.core.model.AccentPresets
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import app.gameport.core.model.CoverSize
import app.gameport.core.model.CoverSpacing
import app.gameport.core.model.DisplaySettings
import app.gameport.core.model.HoverAnimation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.remember
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import app.gameport.core.model.ReturnMode
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import app.gameport.core.designsystem.BackdropDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import app.gameport.core.designsystem.DangerRed
import app.gameport.core.designsystem.OnDangerRed
import app.gameport.core.model.AppLanguage
import app.gameport.core.model.AppUpdateState
import app.gameport.core.model.PlayerDefaults
import app.gameport.core.model.SpeedUnit

private enum class Category(val title: Int) {
    ACCOUNT(R.string.settings_category_account),
    DOWNLOADS(R.string.settings_category_downloads),
    APPEARANCE(R.string.settings_category_appearance),
    HOME(R.string.settings_category_home),
    LANGUAGE(R.string.settings_category_language),
    UPDATES(R.string.settings_category_updates),
    GAMES(R.string.settings_category_games),
    HIDDEN(R.string.settings_category_hidden),
}

/** A full settings page: categories on the left, the selected one on the right. */
@Composable
fun SettingsScreen(onBack: () -> Unit, startOnAccount: Boolean = false, viewModel: SettingsViewModel = hiltViewModel()) {
    val speedUnit by viewModel.speedUnit.collectAsStateWithLifecycle()
    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val playerDefaults by viewModel.playerDefaults.collectAsStateWithLifecycle()
    val display by viewModel.display.collectAsStateWithLifecycle()
    val countPlaytime by viewModel.countPlaytimeOnSteam.collectAsStateWithLifecycle()
    val sendAchievements by viewModel.sendAchievementsToSteam.collectAsStateWithLifecycle()
    val returnMode by viewModel.returnMode.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val updateBlocker by viewModel.updateBlocker.collectAsStateWithLifecycle(null)
    val checkHours by viewModel.updateCheckHours.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    var category by rememberSaveable { mutableStateOf(if (!startOnAccount && viewModel.updateState.value is AppUpdateState.Available) Category.UPDATES else Category.ACCOUNT) }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onClick = onBack)
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            }
            // A wide screen has the categories on the left; a narrow one has them in a row above the page.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxWidth < COMPACT_WIDTH
                val entries = Category.entries.filter { it != Category.GAMES || viewModel.isHeadset }
                val page: @Composable (Modifier) -> Unit = { modifier ->
                    Column(
                        modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        when (category) {
                            Category.ACCOUNT -> AccountSection(accountName, offline, viewModel::onOfflineModeChanged, countPlaytime, viewModel::onCountPlaytimeOnSteamChanged, sendAchievements, viewModel::onSendAchievementsToSteamChanged, returnMode, viewModel::onReturnModeChanged, viewModel::onSignOut)
                            Category.DOWNLOADS -> DownloadsSection(speedUnit, viewModel::onSpeedUnitSelected)
                            Category.APPEARANCE -> AppearanceSection(display, viewModel::onDisplayChanged)
                            Category.HOME -> HomeSection(display, viewModel.isHeadset, viewModel::onDisplayChanged)
                            Category.LANGUAGE -> LanguageSection(language) { chosen ->
                                viewModel.onLanguageSelected(chosen)
                                // The texts are read again in the new language.
                                activity?.recreate()
                            }
                            Category.UPDATES -> UpdatesSection(
                                viewModel.installedVersion, updateState, updateBlocker, viewModel.canUpdateInPlace,
                                checkHours, viewModel::onUpdateCheckHoursChanged, viewModel::onCheckForUpdate, viewModel::onUpdate,
                            )
                            Category.GAMES -> GamesSection(playerDefaults.heightCm, viewModel::onDefaultHeightChanged)
                            Category.HIDDEN -> HiddenSection(viewModel.hiddenGames.collectAsStateWithLifecycle().value, viewModel::onShowAgain)
                    }
                    }
                }
                if (compact) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            entries.forEach { entry -> CategoryItem(entry, entry == category, compact = true) { category = entry } }
                        }
                        page(Modifier.weight(1f).fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            entries.forEach { entry -> CategoryItem(entry, entry == category, compact = false) { category = entry } }
                        }
                        page(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSection(
    accountName: String?,
    offline: Boolean,
    onOfflineChanged: (Boolean) -> Unit,
    countPlaytime: Boolean,
    onCountPlaytimeChanged: (Boolean) -> Unit,
    sendAchievements: Boolean,
    onSendAchievementsChanged: (Boolean) -> Unit,
    returnMode: ReturnMode,
    onReturnModeChanged: (ReturnMode) -> Unit,
    onSignOut: () -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    if (confirming) {
        BackdropDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.settings_sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = OnDangerRed),
                ) { Text(stringResource(R.string.settings_sign_out)) }
            },
            dismissButton = { DangerTextButton(onClick = { confirming = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
    Text(stringResource(R.string.settings_category_account), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.settings_account_description),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    accountName?.let { Text(stringResource(R.string.settings_signed_in_as, it), style = MaterialTheme.typography.titleMedium) }
    Row(Modifier.fillMaxWidth().widthIn(max = 720.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_offline), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_offline_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = offline, onCheckedChange = onOfflineChanged)
    }
    Row(Modifier.fillMaxWidth().widthIn(max = 720.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_playtime), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_playtime_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = countPlaytime, onCheckedChange = onCountPlaytimeChanged)
    }
    Row(Modifier.fillMaxWidth().widthIn(max = 720.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_send_achievements), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_send_achievements_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = sendAchievements, onCheckedChange = onSendAchievementsChanged)
    }
    var choosingReturn by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().widthIn(max = 720.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_return), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_return_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            OutlinedButton(onClick = { choosingReturn = true }, contentPadding = PaddingValues(start = 16.dp, end = 8.dp)) {
                Text(stringResource(returnModeLabel(returnMode)))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(24.dp))
            }
            DropdownMenu(expanded = choosingReturn, onDismissRequest = { choosingReturn = false }) {
                ReturnMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(stringResource(returnModeLabel(mode))) },
                        leadingIcon = { if (mode == returnMode) Icon(Icons.Filled.Check, contentDescription = null) },
                        onClick = { onReturnModeChanged(mode); choosingReturn = false },
                    )
                }
            }
        }
    }
    Button(
        onClick = { confirming = true },
        colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = OnDangerRed),
    ) { Text(stringResource(R.string.settings_sign_out)) }
}

@Composable
private fun DownloadsSection(speedUnit: SpeedUnit, onSelect: (SpeedUnit) -> Unit) {
    Text(stringResource(R.string.settings_category_downloads), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.settings_speed_unit), style = MaterialTheme.typography.titleMedium)
    SpeedUnit.entries.forEach { unit ->
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onSelect(unit) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = unit == speedUnit, onClick = null)
            Text(
                stringResource(
                    if (unit == SpeedUnit.MEGABYTES_PER_SECOND) R.string.settings_unit_megabytes else R.string.settings_unit_megabits,
                ),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun LanguageSection(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Text(stringResource(R.string.settings_category_language), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.settings_language_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
    AppLanguage.entries.forEach { language ->
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).clickable { if (language != selected) onSelect(language) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = language == selected, onClick = null)
            // Each language is written in itself, so it stays findable whatever language is shown now.
            Text(language.nativeName ?: stringResource(R.string.settings_language_auto), modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun GamesSection(heightCm: Int, onHeightChanged: (Int) -> Unit) {
    Text(stringResource(R.string.settings_category_games), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.settings_games_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.settings_games_height, heightCm), style = MaterialTheme.typography.titleMedium)
    Slider(
        value = heightCm.toFloat(),
        onValueChange = { onHeightChanged(it.toInt()) },
        valueRange = PlayerDefaults.HEIGHT_RANGE_CM.first.toFloat()..PlayerDefaults.HEIGHT_RANGE_CM.last.toFloat(),
        modifier = Modifier.widthIn(max = 720.dp),
    )
    Text(stringResource(R.string.settings_games_height_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The games hidden in GamePort, each with a button to show it again. Steam's own hidden games are another matter and stay as they are. */
@Composable
private fun HiddenSection(games: List<Game>, onShowAgain: (Int) -> Unit) {
    Text(stringResource(R.string.settings_category_hidden), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.settings_hidden_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (games.isEmpty()) {
        Text(stringResource(R.string.settings_hidden_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    games.forEach { game ->
        Row(
            Modifier.fillMaxWidth().widthIn(max = 720.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameImage(
                url = game.capsuleUrl,
                fallbackUrl = game.capsuleFallbacks.firstOrNull(),
                moreFallbacks = game.capsuleFallbacks.drop(1),
                contentDescription = null,
                modifier = Modifier.width(48.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)),
            )
            Text(game.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2)
            GlassButton(onClick = { onShowAgain(game.appId) }) {
                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_hidden_show))
            }
        }
    }
}

private fun returnModeLabel(mode: ReturnMode): Int = when (mode) {
    ReturnMode.NEVER -> R.string.settings_return_never
    ReturnMode.APP -> R.string.settings_return_app
    ReturnMode.LIBRARY -> R.string.settings_return_library
    ReturnMode.ALL -> R.string.settings_return_all
}

/** Below this width the categories move from the left to a row above the page. */
private val COMPACT_WIDTH = 600.dp

@Composable
private fun CategoryItem(entry: Category, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
        modifier = (if (compact) Modifier else Modifier.fillMaxWidth()).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    ) {
        Text(
            stringResource(entry.title),
            modifier = Modifier.padding(if (compact) PaddingValues(horizontal = 16.dp, vertical = 10.dp) else PaddingValues(16.dp)),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
