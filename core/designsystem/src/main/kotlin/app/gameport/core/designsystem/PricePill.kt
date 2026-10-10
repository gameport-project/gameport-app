package app.gameport.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gameport.core.model.StorePrice

private val Mint = Color(0xFF7BD389)
private val MintDark = Color(0xFF1E7A3C)
private val MintFillLight = Color(0xFFD7F0DC)
private val InkLight = Color(0xFF1B1F24)

/**
 * What a game costs, in one pill: the share off in its own soft green part when it is on sale, then the price; nothing struck out, the old price is on the page
 * of the game. [onLight] is for a light card; otherwise the pill is dark, for a picture or a dark card. A smaller [compact] one fits a narrow cover.
 */
@Composable
fun PricePill(price: StorePrice, modifier: Modifier = Modifier, onLight: Boolean = false, compact: Boolean = false) {
    val style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    val side = if (compact) 6.dp else 8.dp
    val shape = RoundedCornerShape(50)
    val frame = modifier.clip(shape).then(
        if (onLight) Modifier.border(1.dp, InkLight.copy(alpha = 0.16f), shape)
        else Modifier.background(Color.Black.copy(alpha = 0.55f)).border(1.dp, Color.White.copy(alpha = 0.25f), shape),
    )
    val ink = if (onLight) InkLight else Color.White
    val green = if (onLight) MintDark else Mint
    when (price) {
        StorePrice.Free -> Text(
            stringResource(R.string.price_free), modifier = frame.padding(horizontal = 10.dp, vertical = 3.dp),
            style = style, color = green, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
        )
        is StorePrice.Paid -> Row(frame, verticalAlignment = Alignment.CenterVertically) {
            if (price.discountPercent > 0) {
                Text(
                    "\u2212${price.discountPercent} %",
                    // The colour first, the margin after it: the green fills the whole height of the pill.
                    modifier = Modifier.background(if (onLight) MintFillLight else Mint.copy(alpha = 0.22f)).padding(horizontal = side, vertical = 3.dp),
                    style = style, color = green, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                )
            }
            Text(
                price.finalText(),
                modifier = Modifier.padding(horizontal = if (compact) 6.dp else 9.dp, vertical = 3.dp),
                style = style, color = ink, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
            )
        }
    }
}
