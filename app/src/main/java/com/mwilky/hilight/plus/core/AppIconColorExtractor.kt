package com.mwilky.hilight.plus.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.palette.graphics.Palette

object AppIconColorExtractor {

    /**
     * Extracts a vibrant/dominant brand color from an installed app package's icon.
     * Returns a 64-bit ARGB Long (e.g. 0xFF25D366L).
     */
    fun extractColorForPackage(context: Context, packageName: String, fallbackColor: Long = 0xFF4285F4L): Long {
        return runCatching {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            extractColorFromDrawable(drawable, fallbackColor)
        }.getOrDefault(fallbackColor)
    }

    /**
     * Extracts a vibrant/dominant color from a Drawable using Palette.
     */
    fun extractColorFromDrawable(drawable: Drawable, fallbackColor: Long = 0xFF4285F4L): Long {
        return runCatching {
            val bitmap = drawableToBitmap(drawable)
            val palette = Palette.from(bitmap).generate()

            // Prioritize vibrant brand accents, falling back to dominant/muted
            val colorInt = palette.getVibrantColor(
                palette.getDominantColor(
                    palette.getLightVibrantColor(
                        palette.getDarkVibrantColor(fallbackColor.toInt())
                    )
                )
            )

            // Force opaque alpha and convert to Long
            (colorInt.toLong() and 0x00FFFFFFL) or 0xFF000000L
        }.getOrDefault(fallbackColor)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val bmp = drawable.bitmap
            if (bmp.width > 0 && bmp.height > 0) return bmp
        }

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceAtMost(96) else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceAtMost(96) else 96

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
