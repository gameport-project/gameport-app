package app.gameport.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gameport.core.model.SteamConnection

/** The width of a status notice, which is also the width of the VR / flat tabs it sits beside. */
val StatusNoticeWidth = 260.dp

/** The colour of a notice: a warm one that catches the eye, or a green one for an update. */
enum class NoticeTone(val accent: Color, val text: Color) {
    ATTENTION(Color(0xFFFFA83D), Color(0xFFFFD9A8)),
    UPDATE(Color(0xFF66BB6A), Color(0xFFD3EDD5)),
}

/**
 * A notice as tall and wide as the tabs. A label too long for the room is cut with an ellipsis, so the name of a
 * game never pushes the notice wider.
 */
@Composable
fun StatusNotice(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.ErrorOutline,
    onClick: (() -> Unit)? = null,
    tone: NoticeTone = NoticeTone.ATTENTION,
) {
    val warm = tone.accent
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .width(StatusNoticeWidth)
            .height(44.dp)
            .clip(shape)
            .background(warm.copy(alpha = 0.16f))
            .border(1.dp, warm.copy(alpha = 0.75f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = warm, modifier = Modifier.size(20.dp))
        Text(label, color = tone.text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}

/** Says how GamePort stands with Steam when it is not connected; shows nothing when it is. */
@Composable
fun ConnectionNotice(connection: SteamConnection, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    when (connection) {
        SteamConnection.ONLINE -> Unit
        SteamConnection.CONNECTING -> StatusNotice(stringResource(R.string.status_connecting), modifier, Icons.Rounded.Sync, onClick)
        SteamConnection.OFFLINE_MODE -> StatusNotice(stringResource(R.string.status_offline_mode), modifier, Icons.Rounded.CloudOff, onClick)
        SteamConnection.UNREACHABLE -> StatusNotice(stringResource(R.string.status_offline), modifier, Icons.Rounded.CloudOff, onClick)
    }
}
