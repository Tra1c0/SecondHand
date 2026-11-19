package dev.personal.secondhand.companion

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.CRC32
import java.util.zip.ZipFile

// Stage 80d: the app's own resumable download of one bundle zip from Qualcomm's public S3 bucket, plus the checks that
// replace a checksum (Qualcomm publishes none): the exact byte count and the CRC32 of every entry of the zip.
// Plain java.net/java.io, no Android: unit-tested against a local HTTP server.

class DownloadException(val kind: Kind, message: String, val httpStatus: Int? = null, cause: Throwable? = null) : Exception(message, cause) {
    enum class Kind { NETWORK, HTTP_STATUS, SIZE_MISMATCH, ZIP_INVALID, NO_SPACE, CANCELLED }
}

/** Where a download stands and what the next request must be. Pure arithmetic. */
object ResumePlan {
    sealed interface Step {
        data object AlreadyComplete : Step
        data class Fetch(val offset: Long) : Step
        data class Restart(val reason: String) : Step
    }

    fun plan(partBytes: Long, expectedTotal: Long): Step = when {
        partBytes == expectedTotal && expectedTotal > 0 -> Step.AlreadyComplete
        partBytes > expectedTotal -> Step.Restart("Teildatei ($partBytes Bytes) ist größer als erwartet ($expectedTotal)")
        partBytes <= 0 -> Step.Fetch(0)
        else -> Step.Fetch(partBytes)
    }

    /** "bytes 100-999/1000" -> (100, 999, 1000). */
    fun parseContentRange(header: String?): Triple<Long, Long, Long>? {
        val m = Regex("""bytes\s+(\d+)-(\d+)/(\d+)""").find(header ?: return null) ?: return null
        return Triple(m.groupValues[1].toLong(), m.groupValues[2].toLong(), m.groupValues[3].toLong())
    }

    sealed interface Action {
        /** Append the body to the partial file. */
        data object Append : Action

        /** The server sent the whole file (it ignored the range or the validator changed): start the file again. */
        data object Truncate : Action
        data object Complete : Action
        data class Fail(val kind: DownloadException.Kind, val message: String) : Action
    }

    /** What to do with a response to a request that asked for [offset] onward of a file of [expectedTotal] bytes. */
    fun onResponse(status: Int, contentRange: String?, contentLength: Long, offset: Long, expectedTotal: Long): Action = when {
        status == 206 -> {
            val r = parseContentRange(contentRange)
            when {
                r == null -> Action.Fail(DownloadException.Kind.HTTP_STATUS, "206 ohne lesbaren Content-Range")
                r.first != offset -> Action.Fail(DownloadException.Kind.SIZE_MISMATCH, "Server liefert ab ${r.first}, verlangt war $offset")
                r.third != expectedTotal -> Action.Fail(DownloadException.Kind.SIZE_MISMATCH, "Server meldet ${r.third} Bytes gesamt, erwartet $expectedTotal")
                else -> Action.Append
            }
        }
        status == 200 ->
            if (contentLength >= 0 && contentLength != expectedTotal) {
                Action.Fail(DownloadException.Kind.SIZE_MISMATCH, "Server meldet $contentLength Bytes, erwartet $expectedTotal")
            } else {
                Action.Truncate
            }
        status == 416 -> if (offset == expectedTotal) Action.Complete else Action.Truncate
        else -> Action.Fail(DownloadException.Kind.HTTP_STATUS, "HTTP-Status $status")
    }

    /** Free bytes needed: what is still to download, plus the unpacked model, plus a reserve. */
    fun spaceNeeded(zipBytes: Long, alreadyDownloaded: Long, unpackedBytes: Long, reserve: Long = 1L shl 30): Long =
        (zipBytes - alreadyDownloaded).coerceAtLeast(0) + unpackedBytes + reserve
}

/** The two things the runner needs from a downloader (a fake in the unit tests). */
interface ZipDownloader {
    /**
     * Downloads [url] to [dest] (complete file, exactly [expectedBytes] bytes), resuming a partial download. Throws
     * DownloadException. [onProgress] gets (bytes so far, expected total).
     */
    fun download(url: String, dest: String, expectedBytes: Long, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean)

    /** Checks the zip's directory and the CRC32 of every entry; throws DownloadException(ZIP_INVALID). */
    fun verifyZip(path: String, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean): ZipSummary
}

data class ZipSummary(val entries: Int, val binFiles: Int, val hasMetadata: Boolean, val totalBytes: Long)

object NoDownloader : ZipDownloader {
    override fun download(url: String, dest: String, expectedBytes: Long, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean) {
        throw DownloadException(DownloadException.Kind.NETWORK, "kein Downloader")
    }

    override fun verifyZip(path: String, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean): ZipSummary =
        throw DownloadException(DownloadException.Kind.ZIP_INVALID, "kein Prüfer")
}

class HttpZipDownloader(
    private val maxAttempts: Int = 6,
    private val backoffMs: Long = 2000,
    private val connectTimeoutMs: Int = 20_000,
    private val readTimeoutMs: Int = 30_000,
) : ZipDownloader {
    override fun download(url: String, dest: String, expectedBytes: Long, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean) {
        val target = File(dest)
        val part = File("$dest.part")
        target.parentFile?.mkdirs()
        if (target.exists() && target.length() == expectedBytes) return
        var attempt = 0
        while (true) {
            if (cancelled()) throw DownloadException(DownloadException.Kind.CANCELLED, "abgebrochen")
            attempt++
            try {
                fetchOnce(url, part, expectedBytes, onProgress, cancelled)
                // the body ended early (a dropped connection often looks like a normal end): resume, do not give up
                if (part.length() < expectedBytes) throw IOException("Verbindung vorzeitig beendet bei ${part.length()} von $expectedBytes Bytes")
                if (part.length() != expectedBytes) {
                    throw DownloadException(DownloadException.Kind.SIZE_MISMATCH, "Datei hat ${part.length()} Bytes, erwartet $expectedBytes")
                }
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) throw DownloadException(DownloadException.Kind.NO_SPACE, "Datei lässt sich nicht umbenennen")
                return
            } catch (e: DownloadException) {
                if (e.kind == DownloadException.Kind.SIZE_MISMATCH && part.exists()) part.delete() // a mismatching file is never resumed
                throw e
            } catch (e: IOException) {
                if (e.message?.contains("ENOSPC", ignoreCase = true) == true || e.message?.contains("No space left", ignoreCase = true) == true) {
                    throw DownloadException(DownloadException.Kind.NO_SPACE, "Nicht genug freier Speicher: ${e.message}", cause = e)
                }
                // a network error: the bytes already written stay, the next attempt resumes there
                if (attempt >= maxAttempts) throw DownloadException(DownloadException.Kind.NETWORK, "${e.javaClass.simpleName}: ${e.message}", cause = e)
                Thread.sleep(backoffMs)
            }
        }
    }

    private fun fetchOnce(url: String, part: File, expectedBytes: Long, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean) {
        var step = ResumePlan.plan(if (part.exists()) part.length() else 0L, expectedBytes)
        if (step is ResumePlan.Step.Restart) {
            part.delete()
            step = ResumePlan.Step.Fetch(0)
        }
        if (step is ResumePlan.Step.AlreadyComplete) return
        val offset = (step as ResumePlan.Step.Fetch).offset
        val etagFile = File(part.path + ".etag")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.instanceFollowRedirects = true
            if (offset > 0) {
                conn.setRequestProperty("Range", "bytes=$offset-")
                runCatching { etagFile.readText() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { conn.setRequestProperty("If-Range", it) }
            }
            val status = conn.responseCode
            val len = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            when (val action = ResumePlan.onResponse(status, conn.getHeaderField("Content-Range"), len, offset, expectedBytes)) {
                is ResumePlan.Action.Fail -> throw DownloadException(action.kind, action.message, httpStatus = status)
                ResumePlan.Action.Complete -> return
                ResumePlan.Action.Truncate, ResumePlan.Action.Append -> {
                    val start = if (action == ResumePlan.Action.Append) offset else 0L
                    conn.getHeaderField("ETag")?.let { runCatching { etagFile.writeText(it) } }
                    RandomAccessFile(part, "rw").use { raf ->
                        raf.setLength(start)
                        raf.seek(start)
                        var done = start
                        val buf = ByteArray(256 * 1024)
                        conn.inputStream.use { input ->
                            while (true) {
                                if (cancelled()) throw DownloadException(DownloadException.Kind.CANCELLED, "abgebrochen")
                                val n = input.read(buf)
                                if (n < 0) break
                                raf.write(buf, 0, n)
                                done += n
                                onProgress(done, expectedBytes)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    override fun verifyZip(path: String, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean): ZipSummary = ZipCheck.verify(File(path), onProgress, cancelled)
}

object ZipCheck {
    /** Opens the zip directory, requires a metadata.json and at least one .bin, and recomputes the CRC32 of every entry. */
    fun verify(file: File, onProgress: (Long, Long) -> Unit = { _, _ -> }, cancelled: () -> Boolean = { false }): ZipSummary {
        try {
            ZipFile(file).use { zf ->
                val entries = zf.entries().asSequence().filter { !it.isDirectory }.toList()
                val total = entries.sumOf { it.size.coerceAtLeast(0) }
                val hasMeta = entries.any { it.name == "metadata.json" || it.name.endsWith("/metadata.json") }
                val bins = entries.count { it.name.endsWith(".bin") }
                if (!hasMeta) throw DownloadException(DownloadException.Kind.ZIP_INVALID, "kein metadata.json im Zip")
                if (bins == 0) throw DownloadException(DownloadException.Kind.ZIP_INVALID, "keine .bin-Datei im Zip")
                var done = 0L
                val buf = ByteArray(1 shl 20)
                for (e in entries) {
                    val crc = CRC32()
                    var read = 0L
                    zf.getInputStream(e).use { input ->
                        while (true) {
                            if (cancelled()) throw DownloadException(DownloadException.Kind.CANCELLED, "abgebrochen")
                            val n = input.read(buf)
                            if (n < 0) break
                            crc.update(buf, 0, n)
                            read += n
                            done += n
                            onProgress(done, total)
                        }
                    }
                    if (e.size >= 0 && read != e.size) throw DownloadException(DownloadException.Kind.ZIP_INVALID, "${e.name}: $read statt ${e.size} Bytes")
                    if (e.crc >= 0 && crc.value != e.crc) throw DownloadException(DownloadException.Kind.ZIP_INVALID, "${e.name}: Prüfsumme (CRC32) stimmt nicht")
                }
                return ZipSummary(entries.size, bins, hasMeta, total)
            }
        } catch (e: DownloadException) {
            throw e
        } catch (e: java.util.zip.ZipException) {
            throw DownloadException(DownloadException.Kind.ZIP_INVALID, "Zip nicht lesbar: ${e.message}", cause = e)
        } catch (e: IOException) {
            throw DownloadException(DownloadException.Kind.ZIP_INVALID, "Zip nicht lesbar: ${e.javaClass.simpleName}: ${e.message}", cause = e)
        }
    }
}
