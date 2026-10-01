package app.gameport.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@Composable
internal fun QrCode(content: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(content) { renderQr(content) }
    Image(
        bitmap = bitmap,
        contentDescription = stringResource(R.string.auth_qr_description),
        modifier = modifier.size(size),
        // The code is a few dozen pixels wide: enlarged with smoothing it turns blurry, with hard edges it stays sharp.
        filterQuality = FilterQuality.None,
    )
}

private fun renderQr(content: String): ImageBitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2))
    val bitmap = createBitmap(matrix.width, matrix.height)
    for (x in 0 until matrix.width) {
        for (y in 0 until matrix.height) {
            bitmap[x, y] = if (matrix[x, y]) BLACK else WHITE
        }
    }
    return bitmap.asImageBitmap()
}

private val BLACK = Color.Black.hashCode()
private val WHITE = Color.White.hashCode()
