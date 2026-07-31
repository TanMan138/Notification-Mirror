package com.tanmay.notificationmirror.notifications

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Turns an app icon into the compact Base64 payload the Mac renders. */
object IconCodec {

    private const val FALLBACK_ICON_SIZE = 96
    private const val MAX_SOURCE_EDGE_PX = 512
    private const val ICON_MAX_EDGE_PX = 64
    private const val WEBP_QUALITY = 70

    /**
     * Returns the target size for [width] x [height] constrained to [maxEdge],
     * preserving aspect ratio. Null if the dimensions are unusable.
     */
    fun computeScaledSize(width: Int, height: Int, maxEdge: Int): Pair<Int, Int>? {
        if (width <= 0 || height <= 0) return null
        if (width <= maxEdge && height <= maxEdge) return width to height
        val scale = minOf(maxEdge.toFloat() / width, maxEdge.toFloat() / height)
        val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
        return scaledWidth to scaledHeight
    }

    /** Encodes [drawable] as a lossy WebP Base64 string, or null if it cannot be rendered. */
    fun encode(drawable: Drawable?): String? {
        if (drawable == null) return null
        return try {
            val source = toBitmap(drawable) ?: return null
            val (width, height) = computeScaledSize(source.width, source.height, ICON_MAX_EDGE_PX)
                ?: return null
            val scaled = if (width == source.width && height == source.height) {
                source
            } else {
                Bitmap.createScaledBitmap(source, width, height, true)
            }
            val output = ByteArrayOutputStream()
            if (!scaled.compress(webpFormat(), WEBP_QUALITY, output)) return null
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

    private fun toBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(MAX_SOURCE_EDGE_PX)
            ?: FALLBACK_ICON_SIZE
        val height = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(MAX_SOURCE_EDGE_PX)
            ?: FALLBACK_ICON_SIZE
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }
}
