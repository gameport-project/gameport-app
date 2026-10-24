package app.gameport.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.AttentionBadge
import app.gameport.core.designsystem.CompatGlyph
import app.gameport.core.designsystem.CompatOrange
import app.gameport.core.designsystem.GlassChip
import app.gameport.core.designsystem.Glyph
import app.gameport.core.designsystem.KindBlue
import app.gameport.core.designsystem.UpdateBadge
import app.gameport.core.model.CompatLevel
import app.gameport.core.model.HelpIssueCase
import app.gameport.core.model.IssueTone
import app.gameport.core.model.TourKind
import app.gameport.core.designsystem.R as DesignR

/** Where the project is found: the invitation is the one in the README. */
private const val DISCORD_URL = "https://discord.gg/yWXXAC2AKc"
private val DiscordBlurple = Color(0xFF5865F2)
private val CompatGreen = Color(0xFF66BB6A)
private val CompatRed = Color(0xFFE57373)

/** The guided tours, then every label, notice and button of a game's page explained, then where to ask. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun HelpSection(onStartComplete: () -> Unit, onStartTour: (TourKind) -> Unit) {
    Text(stringResource(R.string.settings_category_help), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.help_description), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.readable())

    HelpBlock(R.string.help_tours_title) {
        Text(stringResource(R.string.help_tours_text), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.readable())
        Button(onClick = onStartComplete) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Text(stringResource(R.string.help_tour_complete), modifier = Modifier.padding(start = 8.dp))
        }
        Text(stringResource(R.string.help_tour_pages), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tours.forEach { (kind, icon, label) ->
                OutlinedButton(onClick = { onStartTour(kind) }) {
                    Icon(icon, contentDescription = null)
                    Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Text(stringResource(R.string.help_tours_page_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }

    HelpBlock(R.string.help_compat_title) {
        Text(stringResource(R.string.help_compat_text), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.readable())
        CompatLevel.entries.forEach { level ->
            val (label, colour, glyph, meaning) = when (level) {
                CompatLevel.WORKS -> Legend(R.string.help_compat_works, CompatGreen, Glyph.CHECK, R.string.help_compat_works_text)
                CompatLevel.OFFLINE_ONLY -> Legend(R.string.help_compat_offline, CompatOrange, Glyph.NO_NETWORK, R.string.help_compat_offline_text)
                CompatLevel.MIXED -> Legend(R.string.help_compat_mixed, CompatOrange, Glyph.EXCLAMATION, R.string.help_compat_mixed_text)
                CompatLevel.FAILS -> Legend(R.string.help_compat_fails, CompatRed, Glyph.CROSS, R.string.help_compat_fails_text)
            }
            CompatRow(label, colour, glyph, meaning)
        }
        CompatRow(R.string.help_compat_incompatible, CompatRed, Glyph.CROSS, R.string.help_compat_incompatible_text)
        Text(stringResource(R.string.help_compat_few), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.readable())
    }

    HelpBlock(R.string.help_notices_title) {
        Text(stringResource(R.string.help_notices_text), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.readable())
        HelpIssueCase.entries.forEach { case ->
            Row(Modifier.readable(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(24.dp), Alignment.Center) {
                    when (case.tone) {
                        IssueTone.ATTENTION -> AttentionBadge(size = 20.dp)
                        IssueTone.UPDATE -> UpdateBadge(size = 20.dp)
                        IssueTone.INFO -> Box(Modifier.size(12.dp).background(KindBlue, CircleShape))
                    }
                }
                Text(stringResource(noticeText(case)))
            }
        }
    }

    HelpBlock(R.string.help_buttons_title) {
        Text(stringResource(R.string.help_buttons_text), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.readable())
        ButtonRow(Icons.Filled.CloudSync, Color.White, R.string.help_button_saves)
        ButtonRow(Icons.Filled.BugReport, Color.White, R.string.help_button_report)
        ButtonRow(Icons.Filled.Build, CompatOrange, R.string.help_button_compat)
        ButtonRow(Icons.Filled.SportsEsports, Color.White, R.string.help_button_controllers)
        ButtonRow(Icons.Filled.SportsEsports, CompatOrange, R.string.help_button_controllers_forced)
        ButtonRow(Icons.Filled.SportsEsports, KindBlue, R.string.help_button_controllers_auto)
        ButtonRow(Icons.Filled.Settings, Color.White, R.string.help_button_game_settings)
    }

    HelpBlock(R.string.help_options_title) {
        Text(stringResource(R.string.help_options_text), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.readable())
        Column(Modifier.readable(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.help_option_frame_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.help_option_frame_text))
        }
        Column(Modifier.readable(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.help_option_recenter_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.help_option_recenter_text))
        }
        Column(Modifier.readable(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.help_option_mapping_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.help_option_mapping_text))
        }
    }

    HelpBlock(R.string.help_ask_title) {
        Text(stringResource(R.string.help_ask_text), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.readable())
        val links = LocalUriHandler.current
        Button(
            onClick = { links.openUri(DISCORD_URL) },
            colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple, contentColor = Color.White),
        ) {
            Icon(painterResource(DesignR.drawable.ic_discord), contentDescription = null, modifier = Modifier.size(22.dp))
            Text(stringResource(R.string.help_discord), modifier = Modifier.padding(start = 8.dp))
        }
        Text(stringResource(R.string.help_discord_where), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

/** The tours of single pages that Settings > Help starts, with the icon of each. */
private val tours = listOf(
    Triple(TourKind.HOME, Icons.Filled.Home, R.string.help_tour_home),
    Triple(TourKind.STORE, Icons.Filled.Storefront, R.string.help_tour_store),
    Triple(TourKind.COMPAT_TABLE, Icons.AutoMirrored.Filled.List, R.string.help_tour_table),
    Triple(TourKind.DOWNLOADS, Icons.Filled.Download, R.string.help_tour_downloads),
    Triple(TourKind.GAME, Icons.Filled.VideogameAsset, R.string.help_tour_game),
    Triple(TourKind.GAME_COMPAT, Icons.Filled.Build, R.string.help_tour_game_compat),
    Triple(TourKind.GAME_CONTROLLERS, Icons.Filled.SportsEsports, R.string.help_tour_controllers),
    Triple(TourKind.GAME_SAVES, Icons.Filled.CloudSync, R.string.help_tour_saves),
    Triple(TourKind.SETTINGS, Icons.Filled.Settings, R.string.help_tour_settings),
)

private data class Legend(val label: Int, val colour: Color, val glyph: Glyph, val meaning: Int)

@Composable
private fun HelpBlock(title: Int, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/** A label as a game's page shows it, with what it says. */
@Composable
private fun CompatRow(label: Int, colour: Color, glyph: Glyph, meaning: Int) {
    Column(Modifier.readable(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val capitals: Dp = with(LocalDensity.current) { (MaterialTheme.typography.labelLarge.fontSize * 0.72f).toDp() }
        GlassChip(stringResource(label), accent = colour, leading = { CompatGlyph(glyph, colour, capitals) })
        Text(stringResource(meaning))
    }
}

@Composable
private fun ButtonRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: Int) {
    Row(Modifier.readable(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(stringResource(text))
    }
}

private fun noticeText(case: HelpIssueCase): Int = when (case) {
    HelpIssueCase.UPDATE -> R.string.help_notice_update
    HelpIssueCase.PATCH_OUTDATED -> R.string.help_notice_patch
    HelpIssueCase.PROBLEM -> R.string.help_notice_problem
    HelpIssueCase.STORAGE -> R.string.help_notice_storage
    HelpIssueCase.SAVE_CONFLICT -> R.string.help_notice_conflict
    HelpIssueCase.SAVE_SYNC_FAILED -> R.string.help_notice_sync
    HelpIssueCase.CONTROLLER_SILENT -> R.string.help_notice_silent
    HelpIssueCase.CONTROLLER_MAPPING -> R.string.help_notice_mapping
    HelpIssueCase.REINSTALL -> R.string.help_notice_reinstall
    HelpIssueCase.EXPANSION_UNCHECKED -> R.string.help_notice_expansion
}

/**
 * Makes a line of the page reachable with a keyboard or a controller: the page is mostly text, so without this its few buttons are the only stops and the focus
 * jumps from the top to the bottom. The focused line gets a soft outline, and the page scrolls to it.
 */
private fun Modifier.readable(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    this
        .onFocusChanged { focused = it.isFocused }
        .focusable()
        .then(if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.55f), shape) else Modifier)
}
