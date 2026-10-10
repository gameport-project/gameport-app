package app.gameport.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** The orange of everything that is about compatibility. */
val CompatOrange = Color(0xFFFFB74D)

/**
 * The mark of the compatibility pages: the list at the left and the wrench at the right, side by side and a little over a half of the mark each, the list at the
 * bottom and the wrench at the top. The same on the home's button and in the header of the table.
 */
@Composable
fun CompatToolsIcon(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Box(modifier.size(size)) {
        Icon(Icons.Filled.Build, contentDescription = contentDescription, tint = CompatOrange, modifier = Modifier.size(size * 0.66f).align(Alignment.TopEnd))
        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = CompatOrange, modifier = Modifier.size(size * 0.66f).align(Alignment.BottomStart))
    }
}
