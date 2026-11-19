package dev.personal.secondhand.companion

import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Stage 80f: the report of every run is also written, line by line, to a file in the app's private folder, and every line is
// forced to disk (flush + fsync) before the next step runs. Reason: after HyperOS force-stopped the app during a model load, the
// whole report of that run was gone (it lived only in memory). The next start shows the previous run's report from this file.
// The fixed test sentences are built into the app, so the file holds no text of the user; it says so at the top.

class RunLogFile internal constructor(
    val file: File,
    private val maxBytes: Long,
    private val wallClock: () -> Long,
    header: List<String>,
) {
    private val out = FileOutputStream(file, true)
    private var written = file.length()
    private var capped = false
    private val tf = SimpleDateFormat("HH:mm:ss.SSS", Locale.GERMAN)

    init {
        header.forEach { writeRaw(it) }
    }

    /** Appends one line with a time stamp and forces it to disk. A run file stops growing at [maxBytes] (one closing line says so). */
    @Synchronized
    fun append(line: String) {
        if (capped) return
        if (written >= maxBytes) {
            writeRaw("[" + tf.format(Date(wallClock())) + "] ... (Datei voll, weitere Zeilen werden nicht mehr gespeichert)")
            capped = true
            return
        }
        for (l in line.split('\n')) writeRaw("[" + tf.format(Date(wallClock())) + "] " + l)
    }

    private fun writeRaw(text: String) {
        try {
            val bytes = (text + "\n").toByteArray(Charsets.UTF_8)
            out.write(bytes)
            out.flush()
            out.fd.sync() // survives a kill of the process and, as far as the file system allows, a crash of the phone
            written += bytes.size
        } catch (e: Exception) {
            // a full or broken disk must not break the app; the on-screen report still works
        }
    }
}

/** The run files: the newest [keep] are kept. A run file is named run-<millis>.txt, so the names sort by time. */
class RunLogStore(
    private val dir: File,
    private val keep: Int = 3,
    private val maxBytes: Long = 200_000,
    private val wallClock: () -> Long = { System.currentTimeMillis() },
) {
    fun runFiles(): List<File> =
        (dir.listFiles { f -> f.isFile && f.name.startsWith("run-") && f.name.endsWith(".txt") } ?: emptyArray()).sortedByDescending { it.name }

    /** The text of the newest run file that exists now (call before [start]): the previous run. Null when there is none. */
    fun previousRunText(): String? = runFiles().firstOrNull()?.let { runCatching { it.readText(Charsets.UTF_8) }.getOrNull() }?.takeIf { it.isNotBlank() }

    fun previousRunStart(): String? = runFiles().firstOrNull()?.let { startOf(it.name) }

    /** Starts the file of this run and removes the oldest files beyond [keep]. */
    fun start(): RunLogFile {
        dir.mkdirs()
        val now = wallClock()
        val file = File(dir, "run-" + now.toString().padStart(13, '0') + ".txt")
        runFiles().drop(keep - 1).forEach { runCatching { it.delete() } }
        return RunLogFile(
            file, maxBytes, wallClock,
            listOf(
                "# SecondHand Begleit-Spike: Bericht eines Laufs, gestartet " + stamp(now),
                "# Hinweis: Die Prüftexte der App sind fest eingebaut. Dieser Bericht enthält keine Texte des Nutzers, nur Gerätedaten, Zahlen und Fehlermeldungen.",
            ),
        )
    }

    companion object {
        private fun stamp(ms: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMAN).format(Date(ms))

        fun startOf(fileName: String): String? =
            Regex("run-(\\d{13})\\.txt").find(fileName)?.groupValues?.get(1)?.toLongOrNull()?.let { stamp(it) }

        /** What "Bericht kopieren" copies: the previous run (from its file), a clear boundary, then the current run. */
        fun combined(previousText: String?, previousStart: String?, currentText: String, currentStart: String): String {
            val sb = StringBuilder()
            if (previousText != null) {
                sb.append("===== BERICHT DES VORHERIGEN LAUFS (gestartet ").append(previousStart ?: "unbekannt").append(", aus der Datei) =====\n")
                sb.append(previousText.trimEnd()).append("\n\n")
            } else {
                sb.append("===== Kein Bericht eines vorherigen Laufs vorhanden =====\n\n")
            }
            sb.append("===== AKTUELLER LAUF (gestartet ").append(currentStart).append(") =====\n")
            sb.append(currentText)
            return sb.toString()
        }
    }
}
