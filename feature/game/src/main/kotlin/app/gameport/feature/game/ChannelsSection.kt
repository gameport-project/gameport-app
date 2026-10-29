package app.gameport.feature.game

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.BackdropMenu
import app.gameport.core.designsystem.GlassBorder
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.KindBlue
import app.gameport.core.designsystem.glass
import app.gameport.core.model.UpdateChannel
import app.gameport.core.model.UpdateChannels
import java.util.Date

/**
 * The channels of a game on Steam that hold an Android build, in the game's settings: a small table (the name, the date of the last update and the description Steam gives, as it gives it)
 * and, under it, a list to pick the channel the game follows. [onUse] null shows the table only.
 */
@Composable
internal fun ChannelsSection(channels: List<UpdateChannel>, inUse: String = UpdateChannels.NORMAL, onUse: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Text(stringResource(R.string.game_settings_channels), style = MaterialTheme.typography.headlineSmall)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.game_settings_channels_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 4.dp)) {
            TableRow(
                stringResource(R.string.game_settings_channels_column_name),
                stringResource(R.string.game_settings_channels_column_date),
                stringResource(R.string.game_settings_channels_column_description),
                header = true,
            )
            channels.forEach { channel ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(GlassBorder))
                val date = if (channel.updatedSeconds > 0) DateFormat.getMediumDateFormat(context).format(Date(channel.updatedSeconds * 1000)) else ""
                TableRow(channel.name, date, channel.description.orEmpty(), highlighted = channel.name == inUse)
            }
        }
        if (onUse != null) {
            Box {
                GlassButton(onClick = { open = true }) {
                    Text(stringResource(R.string.game_settings_channel_followed, inUse))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(24.dp))
                }
                BackdropMenu(expanded = open, onDismissRequest = { open = false }) {
                    channels.forEach { channel ->
                        val on = channel.name == inUse
                        // A channel that asks a password cannot be fetched from here.
                        val usable = !channel.passwordRequired
                        Row(
                            Modifier.fillMaxWidth().then(if (usable) Modifier.clickable { open = false; if (!on) onUse(channel.name) } else Modifier).padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = if (on) KindBlue else Color.Transparent, modifier = Modifier.size(18.dp))
                            Column {
                                Text(channel.name, color = if (on) KindBlue else if (usable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!usable) Text(stringResource(R.string.game_settings_channel_password), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.game_settings_channels_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One line of the table: the name, the date and the description, side by side. The channel the game follows is in colour. */
@Composable
private fun TableRow(name: String, date: String, description: String, header: Boolean = false, highlighted: Boolean = false) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Row(Modifier.weight(1.1f), verticalAlignment = Alignment.CenterVertically) {
            if (highlighted) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.game_settings_channel_in_use), tint = KindBlue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(name, style = style, color = if (highlighted) KindBlue else if (header) muted else MaterialTheme.colorScheme.onSurface)
        }
        Text(date, style = style, color = muted, modifier = Modifier.weight(1f))
        Text(description, style = style, color = muted, modifier = Modifier.weight(2.2f))
    }
}
