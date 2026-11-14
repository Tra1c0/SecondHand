// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.charart

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import dev.agentkeyboard.core.charart.Raster
import helium314.keyboard.agent.ScreenshotChip
import kotlin.math.max

/**
 * Pictures for the template of the character art window (stage 53): the newest photos and
 * screenshots on the device, read only after the user opened the "Picture" tab and only with the
 * permission the screenshot pill uses (or the photos the user selected for the app). The keyboard
 * cannot open the system picker without leaving the field, so the pick happens in the window.
 * Pictures stay in memory while the window is open; nothing is copied, stored or sent.
 */
interface CharArtImages {
    /** Whether pictures may be read at all. */
    fun allowed(): Boolean
    /** The newest pictures, at most [limit]; call off the main thread. */
    fun recent(limit: Int): List<Uri>
    /** [uri] as a bitmap of at most [maxPx] pixels per side, or null; call off the main thread. */
    fun load(uri: Uri, maxPx: Int): Bitmap?

    class Device(context: Context) : CharArtImages {
        private val context = context.applicationContext

        override fun allowed() = ScreenshotChip.hasPermission(context) || ScreenshotChip.hasOnlySelectedPhotos(context)

        override fun recent(limit: Int): List<Uri> {
            if (!allowed()) return emptyList()
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val out = ArrayList<Uri>()
            runCatching {
                context.contentResolver.query(collection, arrayOf(MediaStore.Images.Media._ID), null, null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC")?.use { cursor ->
                    while (out.size < limit && cursor.moveToNext()) out += ContentUris.withAppendedId(collection, cursor.getLong(0))
                }
            }
            return out
        }

        override fun load(uri: Uri, maxPx: Int): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            // a photo taken upright with the phone turned is stored sideways plus its orientation
            // (EXIF); MediaStore has it as degrees (review of stage 53, M4)
            rotated(scaledDown(decoded, maxPx), orientation(uri))
        }.getOrNull()

        /** The clockwise turn MediaStore knows for [uri] (0, 90, 180, 270), 0 if it knows none. */
        private fun orientation(uri: Uri): Int = runCatching {
            context.contentResolver.query(uri, arrayOf(ORIENTATION), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getInt(0) else 0
            } ?: 0
        }.getOrDefault(0)
    }

    companion object {
        /** MediaStore's column of the orientation in degrees, the same name on every Android version. */
        private const val ORIENTATION = "orientation"

        /** [bitmap] turned clockwise by [degrees] (a multiple of 90); the same bitmap without a turn. */
        fun rotated(bitmap: Bitmap, degrees: Int): Bitmap {
            val turn = ((degrees % 360) + 360) % 360
            if (turn == 0) return bitmap
            val matrix = Matrix().apply { postRotate(turn.toFloat()) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        /** [bitmap] at most [maxPx] per side (the same bitmap when it is small enough). */
        fun scaledDown(bitmap: Bitmap, maxPx: Int): Bitmap {
            val longest = max(bitmap.width, bitmap.height)
            if (longest <= maxPx) return bitmap
            val factor = maxPx.toFloat() / longest
            return Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * factor).toInt()), max(1, (bitmap.height * factor).toInt()), true)
        }

        /** The darkness of [bitmap]: 0 for white or transparent, 1 for black. */
        fun darkness(bitmap: Bitmap): Raster {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return Raster(bitmap.width, bitmap.height, FloatArray(pixels.size) { index ->
                val color = pixels[index]
                val light = (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f
                (1f - light) * Color.alpha(color) / 255f
            })
        }
    }
}
