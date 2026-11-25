package app.gameport.core.patch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import java.io.ByteArrayOutputStream

/**
 * Lays GamePort's logo over a game's icon. Behind an interface so the image work (which needs `android.graphics`, absent from the JVM unit tests) can be
 * swapped for a fake one in a test.
 */
fun interface IconBadger {
    /**
     * The PNG of [icon] with [logo] inset as a small patch in the bottom-right corner, taking [fraction] of the icon's width, or null when either image
     * cannot be read. The result keeps the icon's own size.
     */
    fun badge(icon: ByteArray, logo: ByteArray, fraction: Float): ByteArray?
}

/** The real badger, used on the device. */
object AndroidIconBadger : IconBadger {
    override fun badge(icon: ByteArray, logo: ByteArray, fraction: Float): ByteArray? {
        val base = runCatching { BitmapFactory.decodeByteArray(icon, 0, icon.size) }.getOrNull() ?: return null
        val mark = runCatching { BitmapFactory.decodeByteArray(logo, 0, logo.size) }.getOrNull() ?: return null.also { base.recycle() }
        return try {
            // Work on a mutable copy at the icon's own size: the launcher keeps showing it just as big.
            val out = base.copy(Bitmap.Config.ARGB_8888, true) ?: return null
            val side = (out.width * fraction).toInt().coerceAtLeast(1)
            // A small inset from the corner so the patch does not sit flush against the edge.
            val inset = (out.width * CORNER_INSET).toInt()
            val right = out.width - inset
            val bottom = out.height - inset
            val destination = Rect(right - side, bottom - side, right, bottom)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            Canvas(out).drawBitmap(mark, Rect(0, 0, mark.width, mark.height), destination, paint)
            ByteArrayOutputStream().use { stream ->
                out.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }.also { out.recycle() }
        } finally {
            base.recycle()
            mark.recycle()
        }
    }

    /** How far the patch sits from the icon's edge, as a share of its width. */
    private const val CORNER_INSET = 0.04f
}
