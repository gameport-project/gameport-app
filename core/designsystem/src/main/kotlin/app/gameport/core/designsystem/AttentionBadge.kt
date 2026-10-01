package app.gameport.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A small orange "!" that tells the player a game needs their attention, such as an outdated patch. */
@Composable
fun AttentionBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(28.dp).clip(CircleShape).background(Color(0xFFFF9800)),
        contentAlignment = Alignment.Center,
    ) {
        Text("!", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
