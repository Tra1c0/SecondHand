// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.shelf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import helium314.keyboard.latin.ClipboardThumbnails
import helium314.keyboard.latin.utils.Log
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Stage 102: small pictures of the shelf's screenshots, decoded off the main thread (the media store's own thumbnail
 * from Android 10 on) and kept in memory while the clipboard view is open ([clear] when it closes). A view that was
 * bound to another screenshot meanwhile does not get the late picture. Nothing is written to disk or logged.
 */
object ShelfThumbnails {
    private const val MAX_BYTES = 12 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val main = Handler(Looper.getMainLooper())
    /** Two threads: the visible tiles of a grid come in quickly; tests run it in place. */
    @JvmStatic var background: Executor = Executors.newFixedThreadPool(2) { Thread(it, "shelf-thumbs").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
    /** Replaced in tests, which have no media store. */
    @JvmStatic var decoder: (ContentResolver, Shelf.Shot, Int) -> Bitmap? = ::decode

    /** Binds the picture of [shot] at about [sizePx] to [view]; until it is there, the view shows nothing (its tile keeps its size). */
    fun bind(view: ImageView, shot: Shelf.Shot?, sizePx: Int) {
        if (shot == null) { view.tag = null; view.setImageDrawable(null); return }
        val key = "${shot.id}@${shot.addedMillis}@${sizePx.coerceAtLeast(1)}"
        if (view.tag == key && view.drawable != null) return
        view.tag = key
        cache.get(key)?.let { view.setImageBitmap(it); view.alpha = 1f; return }
        view.setImageDrawable(null)
        val resolver = view.context.applicationContext.contentResolver
        background.execute {
            val bitmap = try { decoder(resolver, shot, sizePx) } catch (e: Exception) {
                Log.w("ShelfThumbnails", "could not show a screenshot: ${e.javaClass.simpleName}") // deleted meanwhile
                null
            } ?: return@execute
            val show = Runnable {
                cache.put(key, bitmap)
                if (view.tag != key) return@Runnable
                view.setImageBitmap(bitmap)
                view.alpha = 0f
                view.animate().alpha(1f).setDuration(120L).start()
            }
            if (Looper.myLooper() == Looper.getMainLooper()) show.run() else main.post(show)
        }
    }

    private fun decode(resolver: ContentResolver, shot: Shelf.Shot, sizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) return resolver.loadThumbnail(shot.uri, Size(sizePx, sizePx), null)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(shot.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0) return null
        options.inSampleSize = ClipboardThumbnails.sampleSize(options.outWidth, sizePx)
        options.inJustDecodeBounds = false
        return resolver.openInputStream(shot.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    fun clear() = cache.evictAll()
}
