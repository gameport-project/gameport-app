package app.gameport.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** A plain red for destructive actions; the theme's error colour turns pink in dark mode. */
val DangerRed = Color(0xFFD32F2F)
val OnDangerRed = Color.White

/** Deleting or discarding something: a solid red button. */
@Composable
fun DangerButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = OnDangerRed),
        content = content,
    )
}

/** Cancelling: red text, lighter than [DangerButton]. */
@Composable
fun DangerTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = DangerRed.copy(alpha = 0.14f), contentColor = DangerRed),
        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.55f)),
        content = content,
    )
}

/** A red trash can, for uninstalling. */
@Composable
fun DangerTrashButton(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.clip(CircleShape).background(DangerRed.copy(alpha = 0.14f)).border(1.dp, DangerRed.copy(alpha = 0.55f), CircleShape),
        colors = IconButtonDefaults.iconButtonColors(contentColor = DangerRed),
    ) {
        Icon(Icons.Filled.Delete, contentDescription = contentDescription)
    }
}
