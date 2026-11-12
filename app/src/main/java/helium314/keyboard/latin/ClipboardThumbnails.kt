// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache

/**
 * Reduced-size pictures for clipboard entries and the clipboard suggestion. An image is decoded
 * once at about the size it is shown at and then kept in memory, instead of being decoded at full
 * resolution on the main thread every time a list item is bound or the strip is refreshed.
 * Nothing is written to disk and nothing is logged.
 */
object ClipboardThumbnails {
    private const val MAX_BYTES = 16 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Largest power of two that still leaves the decoded image at least [targetWidth] wide. */
    fun sampleSize(imageWidth: Int, targetWidth: Int): Int {
        if (imageWidth <= 0 || targetWidth <= 0) return 1
        var sample = 1
        while (imageWidth / (sample * 2) >= targetWidth) sample *= 2
        return sample
    }

    fun fromFile(path: String, targetWidth: Int): Bitmap? {
        val key = "$path@$targetWidth"
        cache.get(key)?.let { return it }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth <= 0) return null
        options.inSampleSize = sampleSize(options.outWidth, targetWidth)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, options)?.also { cache.put(key, it) }
    }

    /** [stamp] distinguishes two clips that happen to have the same uri. May throw what the resolver throws. */
    fun fromUri(resolver: ContentResolver, uri: Uri, stamp: Long, targetWidth: Int): Bitmap? {
        val key = "$uri@$stamp@$targetWidth"
        cache.get(key)?.let { return it }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0) return null
        options.inSampleSize = sampleSize(options.outWidth, targetWidth)
        options.inJustDecodeBounds = false
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.also { cache.put(key, it) }
    }

    /** Forget everything, e.g. after clips were removed. */
    fun clear() = cache.evictAll()
}
