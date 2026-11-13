// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.InputContentInfoCompat
import dev.agentkeyboard.core.EmojiMix
import dev.agentkeyboard.core.EmojiMixCache
import dev.agentkeyboard.core.EmojiMixTable
import dev.agentkeyboard.core.EmojiMixUrls
import dev.agentkeyboard.core.PngCheck
import helium314.keyboard.agent.AgentStorageContexts
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.agent.SearchCommand
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import okhttp3.Call
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Emoji-Mix (stage 25a): the shared part of `@mix`, the strip chip and the mixer in the emoji
 * palette. Knows the pair table (offline asset), the state of each pair the user asked for, the
 * image cache and how an image reaches the field.
 *
 * Network: only [request], called from an explicit tap on a chip that shows both emojis (or the
 * second pick in the palette's mixer mode), and only one GET of exactly the table's image URL on
 * [EmojiMixUrls.HOST] ([EmojiMixClient]). Building chips never requests anything; a cached image is
 * read from the device only. Nothing about the emojis or the field is logged.
 *
 * States, the table and the listeners are touched on the main thread only; disk and network run on
 * one worker thread.
 */
object EmojiMixer {
    private const val TAG = "EmojiMixer"
    const val ASSET = "emoji_mix_pairs.bin"
    /** Served by the clipboard provider (clipboard_provider_path.xml). */
    const val DIRECTORY = "agent_emoji_mix"
    const val MIME_TYPE = "image/png"
    /** Pairs whose state (and thumbnail) stays in memory. */
    const val MAX_STATES = 12
    /** Longest side of a decoded thumbnail. */
    const val THUMBNAIL_PX = 320

    sealed interface State {
        /** Requested (or read from the cache); the image is on its way. */
        object Loading : State
        class Ready(val file: File, val thumbnail: Bitmap?) : State
        /** The server has no image for this pair (HTTP 404). */
        object Missing : State
        class Failed(val reason: EmojiMixClient.Reason) : State
    }

    /** Replaced in tests: the asset, the HTTP calls, the field sink, the share sheet and the block check. */
    @JvmStatic var tableSource: (Context) -> InputStream = { it.assets.open(ASSET) }
    @JvmStatic var calls: (Context) -> Call.Factory = { EmojiMixHttp.calls(it) }
    @JvmStatic var sink: QrCommand.ContentSink = QrCommand.SYSTEM_SINK
    @JvmStatic var launcher: SearchCommand.Launcher = SearchCommand.SYSTEM_LAUNCHER
    @JvmStatic var blocked: (EditorInfo?) -> Boolean = { KeyboardCommands.isBlocked(Settings.getValues(), it) }

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    /** How the worker hands results to the main thread; replaced in tests whose shadow handler only collects messages. */
    @Volatile @JvmStatic var toMain: (() -> Unit) -> Unit = { block -> main.post(block) }

    @Volatile private var table: EmojiMixTable? = null
    private var tableLoading = false
    private var tableFailed = false

    // access order: the least recently shown pair goes first
    private val states = object : LinkedHashMap<String, State>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, State>?): Boolean =
            size > MAX_STATES && eldest?.value !is State.Loading
    }
    /** Pairs whose cached file is being read without a tap (no network). */
    private val cachedReads = HashSet<String>()

    private val listeners = LinkedHashMap<String, () -> Unit>()

    /**
     * [listener] is called on the main thread when the table is loaded or a state changes; one per
     * [owner] ("chips", "palette"), the last one set wins.
     */
    fun listen(owner: String, listener: (() -> Unit)?) {
        if (listener == null) listeners.remove(owner) else listeners[owner] = listener
    }

    private fun changed() {
        listeners.values.toList().forEach { it() }
    }

    /** The table, or null while it is still being read (it is read once, in the background). */
    fun table(context: Context): EmojiMixTable? {
        table?.let { return it }
        if (tableLoading || tableFailed) return null
        tableLoading = true
        val app = context.applicationContext ?: context
        val source = tableSource
        worker.execute {
            val loaded = try { source(app).use { EmojiMixTable.read(it) } } catch (e: Exception) {
                Log.w(TAG, "could not read the pair table: ${e.javaClass.simpleName}")
                null
            }
            toMain {
                tableLoading = false
                if (loaded == null) tableFailed = true else table = loaded
                changed()
            }
        }
        return null
    }

    fun cache(context: Context) = EmojiMixCache(File(AgentStorageContexts.keyboard(context).filesDir, DIRECTORY))

    /**
     * The state of [mix], or null if it was never asked for. A pair that is cached but not in memory
     * is read from the device (no network) and reported through the listeners.
     */
    fun state(context: Context, mix: EmojiMix): State? {
        states[mix.key]?.let { return it }
        val cache = cache(context)
        if (cache.file(mix).isFile && cachedReads.add(mix.key)) worker.execute {
            val file = cache.get(mix)
            val thumbnail = file?.let { thumbnail(it) }
            toMain {
                cachedReads.remove(mix.key)
                if (file != null && thumbnail != null && states[mix.key] == null) {
                    states[mix.key] = State.Ready(file, thumbnail)
                    changed()
                }
            }
        }
        return null
    }

    /**
     * The explicit request for [mix] (a tap): the cached image, or exactly one download. Does
     * nothing while it is loading or already there, or when the pair has no mix; false then.
     */
    fun request(context: Context, mix: EmojiMix): Boolean {
        when (states[mix.key]) {
            State.Loading, is State.Ready, State.Missing -> return false
            else -> {}
        }
        states[mix.key] = State.Loading
        changed()
        val cache = cache(context)
        val app = context.applicationContext ?: context
        val callsFor = calls
        worker.execute {
            val result: State = try {
                val cached = cache.get(mix)
                if (cached != null) State.Ready(cached, thumbnail(cached))
                else when (val fetched = EmojiMixClient(callsFor(app)).fetch(mix.url)) {
                    is EmojiMixClient.Result.Image -> {
                        val file = cache.put(mix, fetched.bytes)
                        State.Ready(file, thumbnail(file))
                    }
                    EmojiMixClient.Result.Missing -> State.Missing
                    is EmojiMixClient.Result.Failed -> State.Failed(fetched.reason)
                }
            } catch (e: IOException) {
                Log.w(TAG, "could not store the image: ${e.javaClass.simpleName}")
                State.Failed(EmojiMixClient.Reason.STORAGE)
            }
            toMain {
                states[mix.key] = result
                changed()
            }
        }
        return true
    }

    /** A small bitmap of [file] for chips and the palette; null if it is no readable image. */
    fun thumbnail(file: File): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMBNAIL_PX && bounds.outHeight / (sample * 2) >= THUMBNAIL_PX) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (_: Exception) { null }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.getString(R.string.clipboard_provider_authority), file)

    /**
     * Hands the image of [file] to [field]: inserted if the field takes PNG images, otherwise the
     * share sheet. [afterInsert] runs only after the image went out (e.g. removes the typed emojis
     * or the command); [afterShare] after the share sheet started. False if nothing went out.
     */
    fun deliver(context: Context, field: EditorInfo?, file: File, afterInsert: () -> Unit, afterShare: () -> Unit): Boolean {
        if (blocked(field) || !file.isFile) return false
        val uri = try { uriFor(context, file) } catch (e: Exception) {
            Log.w(TAG, "could not share the image: ${e.javaClass.simpleName}")
            return false
        }
        if (helium314.keyboard.agent.ScreenshotChip.editorAccepts(field, MIME_TYPE)) {
            val content = InputContentInfoCompat(uri, ClipDescription(context.getString(R.string.agent_emoji_mix_label), arrayOf(MIME_TYPE)), null)
            if (!sink.insert(context, content)) return false
            afterInsert()
            return true
        }
        if (!launcher.start(context, shareIntent(context, uri))) {
            android.widget.Toast.makeText(context, R.string.agent_command_qr_none, android.widget.Toast.LENGTH_SHORT).show()
            return false
        }
        afterShare()
        return true
    }

    fun shareIntent(context: Context, uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).setType(MIME_TYPE).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(context.getString(R.string.agent_emoji_mix_label), uri)
        return Intent.createChooser(send, context.getString(R.string.agent_emoji_mix_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Deletes every cached image (settings) and forgets the states. */
    fun clearCache(context: Context) {
        val cache = cache(context)
        states.clear()
        worker.execute { cache.clear() }
    }

    /** The keyboard service behind a view's context, or null. */
    fun latinIme(context: Context?): LatinIME? {
        var current = context
        while (current != null) {
            if (current is LatinIME) return current
            current = (current as? ContextWrapper)?.baseContext
        }
        return null
    }

    /** Runs [block] on the worker after everything queued before; tests wait for it. */
    internal fun afterWork(block: () -> Unit) { worker.execute(block) }

    @JvmStatic fun resetForTest() {
        tableSource = { it.assets.open(ASSET) }
        calls = { EmojiMixHttp.calls(it) }
        sink = QrCommand.SYSTEM_SINK
        launcher = SearchCommand.SYSTEM_LAUNCHER
        blocked = { KeyboardCommands.isBlocked(Settings.getValues(), it) }
        toMain = { block -> main.post(block) }
        states.clear()
        cachedReads.clear()
        listeners.clear()
        tableFailed = false
        // a load started under another toMain may never report back (a test's shadow handler): allow a new one
        if (table == null) tableLoading = false
    }
}

/** HTTP client of Emoji-Mix, separate from the AI and update clients. */
object EmojiMixHttp {
    private val delegate = lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
            // a redirect is a failure: the image must come from the pinned host itself
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .cache(null).cookieJar(okhttp3.CookieJar.NO_COOKIES)
            .build()
    }

    /** Only after an explicit tap, never while typing. Call off the main thread. */
    fun calls(context: Context): Call.Factory {
        OkHttp.initialize(context.applicationContext ?: context)
        return delegate.value
    }
}

/**
 * Fetches one Emoji-Mix image. Refuses every URL but the table's form on [EmojiMixUrls.HOST] before
 * any request; then exactly one GET without cookies or credentials, no redirects, at most
 * [MAX_BYTES], answered as `image/png` with a PNG signature. Blocking: call on a worker thread.
 */
class EmojiMixClient(private val calls: Call.Factory) {
    enum class Reason { NOT_ALLOWED, NETWORK, TIMEOUT, REDIRECT, SERVICE, NOT_PNG, TOO_LARGE, STORAGE }

    sealed interface Result {
        class Image(val bytes: ByteArray) : Result
        object Missing : Result
        class Failed(val reason: Reason) : Result
    }

    fun fetch(url: String): Result {
        if (!EmojiMixUrls.isAllowed(url)) return Result.Failed(Reason.NOT_ALLOWED)
        val request = Request.Builder().url(url).get().header("Accept", EmojiMixer.MIME_TYPE).build()
        if (request.url.host != EmojiMixUrls.HOST || !request.url.isHttps) return Result.Failed(Reason.NOT_ALLOWED)
        val response = try { calls.newCall(request).execute() }
            catch (_: InterruptedIOException) { return Result.Failed(Reason.TIMEOUT) }
            catch (_: IOException) { return Result.Failed(Reason.NETWORK) }
        response.use {
            if (it.request.url.host != EmojiMixUrls.HOST) return Result.Failed(Reason.NOT_ALLOWED)
            if (it.code in 300..399) return Result.Failed(Reason.REDIRECT)
            if (it.code == 404) return Result.Missing
            if (it.code != 200) return Result.Failed(Reason.SERVICE)
            val type = it.body.contentType()
            if (type == null || type.type != "image" || type.subtype != "png") return Result.Failed(Reason.NOT_PNG)
            if (it.body.contentLength() > MAX_BYTES) return Result.Failed(Reason.TOO_LARGE)
            val bytes = try { readBounded(it.body.byteStream()) ?: return Result.Failed(Reason.TOO_LARGE) }
                catch (_: InterruptedIOException) { return Result.Failed(Reason.TIMEOUT) }
                catch (_: IOException) { return Result.Failed(Reason.NETWORK) }
            if (!PngCheck.isPng(bytes)) return Result.Failed(Reason.NOT_PNG)
            return Result.Image(bytes)
        }
    }

    /** At most [MAX_BYTES] or null. */
    private fun readBounded(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_BYTES) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        const val MAX_BYTES = 2L * 1024 * 1024
    }
}
