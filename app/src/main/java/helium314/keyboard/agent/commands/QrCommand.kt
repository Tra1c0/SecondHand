// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.agentkeyboard.core.CommandCall
import helium314.keyboard.agent.AgentStorageContexts
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.agent.ScreenshotChip
import helium314.keyboard.agent.SearchCommand
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * `@qr text`: makes a QR code for the typed text, locally with the ZXing encoder (no network).
 *
 * - Fields that declare they take PNG images get "Insert QR code": the PNG is written to the
 *   keyboard's own file provider and handed to the field like a screenshot ([ScreenshotChip]).
 * - Other fields get "Share QR code", which opens the share sheet with the PNG.
 *
 * Building the chips encodes nothing; the code is made only after a tap, on a background thread.
 * The typed text is never logged. Commands are off in password fields, in incognito mode and while
 * the device is locked, and a tap checks that again before anything leaves the keyboard.
 */
object QrCommand : KeyboardCommands.Command {
    private const val TAG = "QrCommand"
    override val name = "qr"
    // the argument is text to encode, not a word: never correct or learn it
    override val protectsArgument: Boolean get() = true

    const val MIME_TYPE = "image/png"
    /** Side of the PNG in pixels, quiet zone included. */
    const val SIZE = 512
    /** Quiet zone in modules, as the QR standard asks for. */
    const val QUIET_ZONE = 4
    /** Served by the clipboard provider (see clipboard_provider_path.xml), outside the clip files. */
    const val DIRECTORY = "agent_qr"
    /** Long enough for the receiving app to read the file, see [cleanUp]. */
    const val KEEP_MILLIS = 10 * 60 * 1000L

    /** Hands an image to the current field; true only if the field took it. */
    fun interface ContentSink {
        fun insert(context: Context, content: InputContentInfoCompat): Boolean
    }

    /**
     * Commits the image like the keyboard's own image keys (KeyboardActionListenerImpl.onContent),
     * but reports the editor's answer: false while the keyboard blocks input (panel, wheel, an unclear
     * edit), when the field does not declare the type, or when the app refuses the image. The caller
     * removes typed text only after true.
     */
    val SYSTEM_SINK = ContentSink { context, content ->
        val ime = context as? LatinIME ?: return@ContentSink false
        if (ime.agentEditorController.isBlockingInput) return@ContentSink false
        val field = ime.currentInputEditorInfo ?: return@ContentSink false
        if (EditorInfoCompat.getContentMimeTypes(field).none { content.description.hasMimeType(it) }) return@ContentSink false
        val connection = ime.currentInputConnection ?: return@ContentSink false
        ime.invalidateAgentEditor()
        try {
            InputConnectionCompat.commitContent(connection, field, content, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not insert the image: ${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * After an inserted image: removes [text] (the typed command or emojis) by [replace] only if it
     * still stands right before the editor's cursor. A rich-text editor may put the image there as an
     * object character (P83); the image went in then, the text stays, and the toast says so instead
     * of "changed, nothing replaced".
     */
    fun removeAfterInsert(host: KeyboardCommands.Host, text: String, replace: () -> Boolean) {
        val now = host.editorTextBeforeCursor(text.length)?.toString()
        if (now != null && now != text) {
            Toast.makeText(host.context, R.string.agent_image_inserted_text_kept, Toast.LENGTH_SHORT).show()
            return
        }
        replace()
    }

    /** Replaced in tests, which have no editor and no share sheet. */
    @JvmStatic var sink: ContentSink = SYSTEM_SINK
    @JvmStatic var launcher: SearchCommand.Launcher = SearchCommand.SYSTEM_LAUNCHER
    @JvmStatic var blocked: (EditorInfo?) -> Boolean = { KeyboardCommands.isBlocked(Settings.getValues(), it) }

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var scheduledCleanUp: Runnable? = null

    override fun description(context: Context) =
        context.getString(R.string.agent_command_qr, KeyboardCommands.prefix(context).toString())

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        val text = call.argument.trim()
        if (text.isEmpty() || blocked(host.editorInfo)) return emptyList()
        val context = host.context
        // one tap per chip row: a second tap while the first is running does nothing
        val used = AtomicBoolean(false)
        return if (ScreenshotChip.editorAccepts(host.editorInfo, MIME_TYPE)) {
            listOf(KeyboardCommands.Chip(context.getString(R.string.agent_command_qr_insert),
                context.getString(R.string.agent_command_qr_insert_description, text)) {
                if (used.compareAndSet(false, true)) insert(host, call, text)
            })
        } else {
            listOf(KeyboardCommands.Chip(context.getString(R.string.agent_command_qr_share),
                context.getString(R.string.agent_command_qr_share_description, text)) {
                if (used.compareAndSet(false, true)) share(host, call, text)
            })
        }
    }

    /**
     * Inserts the image first and removes the command text only afterwards. Removing it first would
     * empty the field in many chats, and apps may restart the input then (P43); if anything fails
     * on the way (encoding, a different field, the field no longer taking images) the typed command
     * stays as it is. [KeyboardCommands.Host.replace] refuses if the text changed in the meantime.
     */
    private fun insert(host: KeyboardCommands.Host, call: CommandCall, text: String) {
        val context = host.context
        val field = host.editorInfo
        worker.execute {
            val content = createFile(context, text)?.let { contentFor(context, it) }
            main.post {
                if (content == null) {
                    Toast.makeText(context, R.string.agent_command_qr_failed, Toast.LENGTH_SHORT).show()
                    return@post
                }
                // only into the field that was tapped in, and only if it still takes the image
                val now = host.editorInfo
                if (!ScreenshotChip.isSameField(field, now) || blocked(now) || !ScreenshotChip.editorAccepts(now, MIME_TYPE)) return@post
                if (sink.insert(context, content)) removeAfterInsert(host, call.text) { host.replace(call, "") }
                else Toast.makeText(context, R.string.agent_image_not_inserted, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Opens the share sheet; the command text is removed only after the sheet started, like `@such`:
     * a failed start keeps it, and nothing is emptied before the switch (P43).
     */
    private fun share(host: KeyboardCommands.Host, call: CommandCall, text: String) {
        val context = host.context
        val field = host.editorInfo
        worker.execute {
            val uri = createFile(context, text)?.let { uriFor(context, it) }
            main.post {
                if (uri == null) {
                    Toast.makeText(context, R.string.agent_command_qr_failed, Toast.LENGTH_SHORT).show()
                    return@post
                }
                val now = host.editorInfo
                if (!ScreenshotChip.isSameField(field, now) || blocked(now)) return@post
                if (!launcher.start(context, shareIntent(context, uri))) {
                    Toast.makeText(context, R.string.agent_command_qr_none, Toast.LENGTH_SHORT).show()
                    return@post
                }
                host.replace(call, "")
            }
        }
    }

    /** The chooser for [uri]; the keyboard is a service, so it starts a new task. */
    fun shareIntent(context: Context, uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME_TYPE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // the clip data carries the read grant on to the app picked in the chooser
        send.clipData = ClipData.newRawUri(context.getString(R.string.agent_command_qr_label), uri)
        return Intent.createChooser(send, context.getString(R.string.agent_command_qr_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** The QR code for [text] as a [SIZE] × [SIZE] bitmap, black on white with a quiet zone; error correction M. */
    fun encode(text: String): Bitmap {
        val hints = mutableMapOf<EncodeHintType, Any>(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to QUIET_ZONE,
        )
        // plain ASCII needs no charset; anything else is UTF-8 (with an ECI marker), not ISO-8859-1
        if (text.any { it.code >= 0x80 }) hints[EncodeHintType.CHARACTER_SET] = Charsets.UTF_8.name()
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, SIZE, SIZE, hints)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) pixels[row + x] = if (matrix.get(x, y)) BLACK else WHITE
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** Encodes [text] into a new PNG in [DIRECTORY]; old files go first. Null if that failed. */
    fun createFile(context: Context, text: String): File? = try {
        val directory = directory(context).apply { mkdirs() }
        cleanUp(context, System.currentTimeMillis())
        val bitmap = encode(text)
        val file = File(directory, "qr-${UUID.randomUUID()}.png")
        file.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IllegalStateException("no PNG") }
        bitmap.recycle()
        scheduleCleanUp(context.applicationContext ?: context)
        file
    } catch (e: Exception) {
        // never the text, only what went wrong
        Log.w(TAG, "could not create the QR code: ${e.javaClass.simpleName}")
        null
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.getString(R.string.clipboard_provider_authority), file)

    private fun contentFor(context: Context, file: File): InputContentInfoCompat? = try {
        InputContentInfoCompat(uriFor(context, file),
            ClipDescription(context.getString(R.string.agent_command_qr_label), arrayOf(MIME_TYPE)), null)
    } catch (e: Exception) {
        Log.w(TAG, "could not share the QR code: ${e.javaClass.simpleName}")
        null
    }

    /** Like the screenshot copies, the files sit in the keyboard's device protected storage. */
    private fun directory(context: Context) = File(AgentStorageContexts.keyboard(context).filesDir, DIRECTORY)

    /** Deletes files older than [KEEP_MILLIS], or all of them with [all]. */
    fun cleanUp(context: Context, now: Long, all: Boolean = false) {
        directory(context).listFiles()?.filter { all || now - it.lastModified() > KEEP_MILLIS }?.forEach { it.delete() }
    }

    /** Deletes the newest file once it is old enough, also when no further QR code is made. */
    private fun scheduleCleanUp(context: Context) {
        val task = Runnable { worker.execute { cleanUp(context, System.currentTimeMillis()) } }
        main.post {
            scheduledCleanUp?.let { main.removeCallbacks(it) }
            scheduledCleanUp = task
            main.postDelayed(task, KEEP_MILLIS + 1_000)
        }
    }

    @JvmStatic fun resetForTest() {
        sink = SYSTEM_SINK
        launcher = SearchCommand.SYSTEM_LAUNCHER
        blocked = { KeyboardCommands.isBlocked(Settings.getValues(), it) }
        scheduledCleanUp?.let { main.removeCallbacks(it) }
        scheduledCleanUp = null
    }
}
