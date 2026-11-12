// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import java.io.File

/**
 * Reduced-size pictures for clipboard entries and the clipboard suggestion. An image is decoded
 * once at about the size it is shown at and then kept in memory, instead of being decoded at full
 * resolution on the main thread every time a list item is bound or the strip is refreshed.
 * Nothing is written to disk and nothing is logged. The clipboard view clears the cache when it
 * is left.
 */
object ClipboardThumbnails {
    private const val MAX_BYTES = 16 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** The one picture of the clipboard suggestion: a single slot, so repeated strip updates cannot pile up copies. */
    private var suggestionKey: String? = null
    private var suggestion: Bitmap? = null

    /** Largest power of two that still leaves the decoded image at least [targetWidth] wide. */
    fun sampleSize(imageWidth: Int, targetWidth: Int): Int {
        if (imageWidth <= 0 || targetWidth <= 0) return 1
        var sample = 1
        while (imageWidth / (sample * 2) >= targetWidth) sample *= 2
        return sample
    }

    /** Null when the file is gone or not a picture. A replaced file is decoded again. */
    fun fromFile(path: String, targetWidth: Int): Bitmap? {
        val file = File(path)
        if (!file.isFile) return null
        val key = "$path@${file.lastModified()}@${file.length()}@$targetWidth"
        cache.get(key)?.let { return it }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth <= 0) return null
        options.inSampleSize = sampleSize(options.outWidth, targetWidth)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, options)?.also { cache.put(key, it) }
    }

    /**
     * Picture of the current clip. [stamp] is the time the clip was set, or null where the system
     * does not report it (before Android 8); then nothing is remembered. May throw what the
     * resolver throws. From Android 9 on the picture is turned as its EXIF data says, like
     * ImageView.setImageURI does.
     */
    fun fromUri(resolver: ContentResolver, uri: Uri, stamp: Long?, targetWidth: Int): Bitmap? {
        val key = stamp?.let { "$uri@$it@$targetWidth" }
        if (key != null && key == suggestionKey) suggestion?.let { return it }
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.setTargetSampleSize(sampleSize(info.size.width, targetWidth))
            }
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            if (options.outWidth <= 0) return null
            options.inSampleSize = sampleSize(options.outWidth, targetWidth)
            options.inJustDecodeBounds = false
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
        suggestionKey = key
        suggestion = if (key == null) null else bitmap
        return bitmap
    }

    /** Forget everything. */
    fun clear() {
        cache.evictAll()
        suggestionKey = null
        suggestion = null
    }
}
