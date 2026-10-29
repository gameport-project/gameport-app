package app.gameport.feature.game

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.AlertNote
import app.gameport.core.designsystem.BackdropDialog
import app.gameport.core.designsystem.BackdropMenu
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.KindBlue
import app.gameport.core.model.Game
import app.gameport.core.model.UpdateChannel
import app.gameport.core.model.UpdateChannels
import java.util.Date

/**
 * What the page of a game says when its Android version is on a channel (a branch of Steam) other than the normal one: that it is only there (the normal channel has nothing), or that the
 * game is installed and updated from such a channel. A test version may be unfinished, and the page says so in the same orange as the other alerts. Nothing for a game on the normal channel.
 */
@Composable
internal fun ChannelNotice(game: Game, channelInUse: String) {
    val build = game.androidBuild ?: return
    val followed = channelInUse != UpdateChannels.NORMAL && build.channels.any { it.name == channelInUse }
    when {
        build.onlyOnOtherChannels -> {
            val names = if (followed) channelInUse else build.channels.filter { !it.passwordRequired }.joinToString(", ") { it.name }
            AlertNote(stringResource(R.string.game_channel_only_title), stringResource(R.string.game_channel_only_text, names), Modifier.padding(bottom = 8.dp))
        }
        followed -> AlertNote(stringResource(R.string.game_channel_followed_title, channelInUse), stringResource(R.string.game_channel_followed_text, channelInUse), Modifier.padding(bottom = 8.dp))
    }
}

/**
 * Asked when a game with nothing on the normal channel is installed: there is no Android version on it, there is one on these channels, and the player picks which to install. The most
 * recently updated one is picked at first.
 */
@Composable
internal fun ChannelChoiceDialog(channels: List<UpdateChannel>, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val context = LocalContext.current
    var picked by rememberSaveable { mutableStateOf(channels.maxByOrNull { it.updatedSeconds }?.name) }
    var open by remember { mutableStateOf(false) }
    BackdropDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_channel_dialog_title)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.game_channel_dialog_text))
                Box {
                    GlassButton(onClick = { open = true }) {
                        Text(stringResource(R.string.game_channel_dialog_pick, picked.orEmpty()))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                    BackdropMenu(expanded = open, onDismissRequest = { open = false }) {
                        channels.forEach { channel ->
                            val on = channel.name == picked
                            Row(
                                Modifier.fillMaxWidth().clickable { picked = channel.name; open = false }.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = if (on) KindBlue else Color.Transparent, modifier = Modifier.size(18.dp))
                                Column {
                                    Text(channel.name, color = if (on) KindBlue else MaterialTheme.colorScheme.onSurface)
                                    if (channel.updatedSeconds > 0) {
                                        val date = DateFormat.getMediumDateFormat(context).format(Date(channel.updatedSeconds * 1000))
                                        Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { GlassButton(enabled = picked != null, onClick = { picked?.let(onConfirm) }) { Text(stringResource(R.string.game_install)) } },
        dismissButton = { GlassButton(onClick = onDismiss) { Text(stringResource(R.string.game_settings_cancel)) } },
    )
}
