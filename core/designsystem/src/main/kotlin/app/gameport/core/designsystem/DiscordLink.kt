package app.gameport.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The invitation to the project's Discord: one place for the link, used by every button that opens it. */
const val DISCORD_URL = "https://discord.gg/yWXXAC2AKc"

/** The colour of Discord's own brand. */
val DiscordBlurple = Color(0xFF5865F2)

/** The button that opens the invitation: Discord's colour, its icon and a few words. */
@Composable
fun DiscordButton(modifier: Modifier = Modifier) {
    val links = LocalUriHandler.current
    Button(
        onClick = { runCatching { links.openUri(DISCORD_URL) } },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple, contentColor = Color.White),
    ) {
        Icon(painterResource(R.drawable.ic_discord), contentDescription = null, modifier = Modifier.size(22.dp))
        Text(stringResource(R.string.discord_join), modifier = Modifier.padding(start = 8.dp))
    }
}

/** The same invitation as a small round button with the icon alone, for a place that has no room for words. */
@Composable
fun DiscordRoundButton(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val links = LocalUriHandler.current
    val label = stringResource(R.string.discord_join)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(DiscordBlurple)
            .clickable(onClickLabel = label, role = Role.Button) { runCatching { links.openUri(DISCORD_URL) } },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_discord), contentDescription = label, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * The field for the Discord name a player may give, with Discord's icon at its left. Optional, kept for the next time, and sent with the player's answers so the project can talk to
 * them about it on its Discord. The caller keeps the text; what is typed is cut at [app.gameport.core.model.DiscordName.MAX_LENGTH].
 */
@Composable
fun DiscordNameField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(app.gameport.core.model.DiscordName.MAX_LENGTH)) },
        label = { Text(stringResource(R.string.discord_name_label)) },
        supportingText = { Text(stringResource(R.string.discord_name_why)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_discord), contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(22.dp)) },
        singleLine = true,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = glassTextFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}
