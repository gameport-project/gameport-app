package app.gameport.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.BackdropDialog
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.HideRed
import app.gameport.core.model.Game

/** What the actions menu of a cover does. The library's buttons and this menu share them. */
internal class GameMenuActions(
    val onOpen: (Int) -> Unit = {},
    val onPlay: (Game) -> Unit = {},
    val onSettings: (Int) -> Unit = {},
    val onToggleFavorite: (Int) -> Unit = {},
    val onUpdate: (Game) -> Unit = {},
    val onRepatch: (Int) -> Unit = {},
    val onHide: (Int) -> Unit = {},
)

/**
 * The actions of a game, opened by staying pressed on its cover. Hiding only concerns GamePort's library,
 * which the entry says; the hidden games are listed again in the settings.
 */
@Composable
internal fun GameMenu(
    game: Game,
    installed: Boolean,
    hasUpdate: Boolean,
    needsAttention: Boolean,
    favorite: Boolean,
    actions: GameMenuActions,
    onDismiss: () -> Unit,
) {
    fun run(action: () -> Unit): () -> Unit = {
        onDismiss()
        action()
    }
    BackdropDialog(
        onDismissRequest = onDismiss,
        minWidth = 220.dp,
        maxWidth = 280.dp,
        padding = 14.dp,
        title = { Text(game.name, maxLines = 2, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (installed) MenuEntry(Icons.Filled.PlayArrow, R.string.menu_play, run { actions.onPlay(game) }, primary = true)
                // What the cover's marks announce comes right after, in the mark's own colour.
                if (installed && hasUpdate) MenuEntry(Icons.Filled.Download, R.string.menu_update, run { actions.onUpdate(game) }, tint = UpdateGreen)
                if (installed && needsAttention) MenuEntry(Icons.Filled.Build, R.string.menu_repatch, run { actions.onRepatch(game.appId) }, tint = AttentionOrange)
                MenuEntry(Icons.AutoMirrored.Filled.OpenInNew, R.string.menu_open, run { actions.onOpen(game.appId) })
                if (installed) MenuEntry(Icons.Filled.Settings, R.string.menu_settings, run { actions.onSettings(game.appId) })
                MenuEntry(
                    if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (favorite) R.string.menu_unfavorite else R.string.menu_favorite,
                    run { actions.onToggleFavorite(game.appId) },
                    iconTint = if (favorite) FavoriteRed else null,
                )
                MenuEntry(Icons.Filled.VisibilityOff, R.string.menu_hide, run { actions.onHide(game.appId) }, tint = HideRed)
                Text(stringResource(R.string.menu_hide_note), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        },
    )
}

private val UpdateGreen = Color(0xFF66BB6A)
private val AttentionOrange = Color(0xFFFF9800)

/**
 * One line of the menu, as small as a menu line should be: the buttons give up the room they keep around themselves for touch,
 * so the lines sit close. [primary] is the theme's main colour, [tint] colours an outlined line, [iconTint] only the icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MenuEntry(icon: ImageVector, label: Int, onClick: () -> Unit, primary: Boolean = false, tint: Color? = null, iconTint: Color? = null) {
    val padding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = iconTint ?: LocalContentColor.current)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(label), modifier = Modifier.weight(1f), maxLines = 1)
    }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        val modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp)
        when {
            primary -> Button(onClick = onClick, modifier = modifier, contentPadding = padding, content = content)
            tint != null -> OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                contentPadding = padding,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = tint.copy(alpha = 0.14f), contentColor = tint),
                border = BorderStroke(1.dp, tint.copy(alpha = 0.7f)),
                content = content,
            )
            else -> GlassButton(onClick = onClick, modifier = modifier, contentPadding = padding, content = content)
        }
    }
}
