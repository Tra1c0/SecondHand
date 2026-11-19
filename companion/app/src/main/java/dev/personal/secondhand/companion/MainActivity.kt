package dev.personal.secondhand.companion

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The one screen of the spike (stage 80): plain Views, German, large text, everything visible on the
// screen. All SDK work runs off the main thread (Dispatchers.Default plus the adapter's IO dispatcher).
class MainActivity : Activity() {
    private enum class Kind { NONE, PULL, TEST, OTHER }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val report = ReportLog()
    private lateinit var runner: SpikeRunner
    private lateinit var prefs: SpikePrefs
    private lateinit var headerLines: List<String>

    private lateinit var bannerView: TextView
    private lateinit var headerView: TextView
    private lateinit var statusView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var answerView: TextView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var btnPull: Button
    private lateinit var btnTest2: Button
    private lateinit var btnTest3: Button
    private lateinit var btnStop: Button
    private lateinit var btnUnload: Button
    private lateinit var btnDelete: Button
    private lateinit var btnList: Button
    private lateinit var btnCompat: Button
    private lateinit var candidatesView: TextView
    private lateinit var btnCopy: Button

    private var activeJob: Job? = null
    private var activeKind = Kind.NONE
    private val modelPresent: Boolean get() = runner.presentModel != null
    private var lastProgressUpdate = 0L

    /** Set after the compatibility metadata was written: test 2 starts as soon as the busy state is cleared. */
    @Volatile
    private var pendingRetest = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SpikePrefs(this)
        val tracker = PrefsStepTracker(this)
        headerLines = ReportFormatter.headerLines(DeviceInfo.collect(this))
        runner = SpikeRunner(GeniexAdapter(this), AndroidProbe(this), RealClock(), tracker, report, AndroidDiagnostics(this), AndroidStoreFiles(this), HttpZipDownloader())
        buildUi()
        headerView.text = headerLines.joinToString("\n")
        report.listener = {
            runOnUiThread {
                logView.text = report.text()
                logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
        tracker.takeInterruptedStep()?.let {
            report.add("WARNUNG: Der vorherige Lauf endete mitten im Schritt '$it' (Absturz oder vom System beendet).")
        }
        DeviceInfo.lastExitLines(this).forEach { report.add(it) }
        showCandidates()
        refreshButtons()
        scope.launch(Dispatchers.Default) {
            runner.init()
            if (runner.supported) runner.refreshModelPresent()
            runOnUiThread {
                runner.initFailureLine?.let {
                    bannerView.text = it
                    bannerView.visibility = View.VISIBLE
                }
                showCandidates()
                if (runner.supported) {
                    statusView.text = if (modelPresent) "Modell ist vorhanden" + pulledSuffix() + "." else "Modell noch nicht geladen. Tippe Knopf 1."
                }
                refreshButtons()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        // free the model; on its own thread because the SDK call must not run on the main thread
        Thread { runBlocking { runCatching { runner.unload() } } }.start()
    }

    // ---- actions ------------------------------------------------------------------------------

    private fun onPullTapped() {
        val present = presentCandidate()
        if (present != null) {
            info("Das Paket ist schon vorhanden" + pulledSuffix() + ". Zum erneuten Laden tippe zuerst 'Modell / angefangenen Download löschen'.")
            return
        }
        val candidate = PullPlanner.current(prefs.abandoned.keys)
        if (candidate == null) {
            AlertDialog.Builder(this)
                .setTitle("Alle Pakete versucht")
                .setMessage(
                    "Alle Pakete sind schon gescheitert (siehe Liste und Bericht). Tippe 'Bericht kopieren' und schicke ihn dem Entwickler. " +
                        "Mit 'Liste zurücksetzen' darfst du alle noch einmal versuchen (zum Beispiel nach einem Netzproblem).",
                )
                .setPositiveButton("Liste zurücksetzen") { _, _ ->
                    prefs.clearAbandoned()
                    showCandidates()
                    refreshButtons()
                }
                .setNegativeButton("Schließen", null)
                .show()
            return
        }
        confirmPull(
            candidate,
            intro = if (modelPresent) "Das vorhandene Paket (${pulledSuffix().trim()}) bleibt dabei erhalten (etwa 3 GB), bis du es mit 'Modell / angefangenen Download löschen' entfernst. Die Tests nehmen dann das neue Paket.\n\n" else "",
        )
    }

    /** The candidate the bundle on the phone belongs to (same model, built with the QAIRT version of that candidate), or null. */
    private fun presentCandidate(): Candidate? = PullPlanner.matching(runner.presentModel, runner.presentFacts?.qairt)

    /** The one dialog before every download: what, size, source, what differs, "may fail". */
    private fun confirmPull(candidate: Candidate, intro: String) {
        val free = runner.freeStorageBytesNow()
        val freeText = if (free >= 0) "Freier Speicher jetzt: ${Metrics.gb(free)}" + if (free < 7L * 1073741824) " (das ist knapp!)" else "" else ""
        val net = runner.networkTypeNow()
        val netText = "Netz jetzt: $net" + if (net != "WLAN") " (WLAN wird empfohlen, 2,7 GB über Mobilfunk können Datenvolumen kosten)" else ""
        AlertDialog.Builder(this)
            .setTitle("Modell herunterladen?")
            .setMessage(
                intro +
                    "Paket: ${candidate.title}\n" +
                    "Modell-Name im SDK: ${candidate.modelName}, Ausgabe ${candidate.release}\n" +
                    "Größe: ${candidate.sizeText} (genau ${candidate.zipBytes} Bytes, die App prüft das und die Prüfsummen der Dateien)\n" +
                    "Quelle: Qualcomms öffentlicher Server (AI Hub, Amazon S3). Qualcomm sieht dabei deine IP-Adresse, wie bei jedem Download. Die App lädt selbst und setzt bei einem Abbruch fort.\n" +
                    "Speicher: etwa 2,7 GB für die Zip-Datei, dann etwa 3,2 GB entpackt im Modellspeicher; die Zip-Datei wird danach gelöscht. Gebraucht werden etwa 8 GB frei.\n" +
                    "$netText\n$freeText\n\n" +
                    candidate.difference + "\n\nEs werden keine eigenen Daten gesendet.",
            )
            .setPositiveButton("Jetzt laden") { _, _ -> startPull(candidate) }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun startPull(candidate: Candidate) {
        beginBusy(Kind.PULL)
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        statusView.text = "Lade herunter: ${candidate.title} ..."
        activeJob = scope.launch(Dispatchers.Default) {
            val outcome = runner.download(candidate) { phase, done, total -> showProgress(phase, done, total) }
            runOnUiThread {
                progressBar.visibility = View.GONE
                when (outcome) {
                    is SpikeRunner.PullOutcome.Success -> {
                        prefs.pulledCandidateId = outcome.candidate.id
                        statusView.text = "Modell geladen (${outcome.candidate.title}). Weiter mit Knopf 2."
                    }
                    is SpikeRunner.PullOutcome.Failed -> onPullFailed(outcome)
                }
                showCandidates()
            }
        }
        finishWhenDone()
    }

    private fun onPullFailed(f: SpikeRunner.PullOutcome.Failed) {
        if (f.advice.cancelled) {
            statusView.text = "Download abgebrochen. Knopf 1 setzt ihn fort."
            return
        }
        statusView.text = "Download fehlgeschlagen: ${f.error}"
        if (f.advice.giveUp) prefs.abandon(f.candidate.id, f.error)
        val next = PullPlanner.next(f.candidate, prefs.abandoned.keys + if (f.advice.giveUp) setOf(f.candidate.id) else emptySet())
        val sb = StringBuilder()
        sb.append("Paket: ${f.candidate.title}\nFehler: ${f.error}\n\n")
        sb.append(
            if (f.advice.giveUp) "Dieses Paket wird nicht noch einmal versucht: Es würde genauso scheitern.\n"
            else "Dasselbe Paket noch einmal zu versuchen kann helfen; der Download setzt fort.\n",
        )
        if (f.advice.deleteFirst) {
            sb.append("Tipp: Das Paket ist unvollständig oder beschädigt. Tippe zuerst 'Modell / angefangenen Download löschen' und danach Knopf 1.\n")
        }
        if (next != null) {
            sb.append("\nNächstes Paket: ${next.title} (${next.sizeText})\n${next.difference}\n")
        } else if (f.advice.giveUp) {
            sb.append("\nEs gibt kein weiteres Paket zum Versuchen. Tippe 'Bericht kopieren' und schicke ihn dem Entwickler.\n")
        }
        val dialog = AlertDialog.Builder(this).setTitle("Download fehlgeschlagen").setMessage(sb.toString())
            .setNegativeButton("Schließen", null)
        if (next != null) {
            dialog.setPositiveButton("Nächstes Paket laden") { _, _ ->
                if (!f.advice.giveUp) prefs.abandon(f.candidate.id, f.error)
                confirmPull(next, intro = "Vorheriges Paket gescheitert: ${f.error}\n\n")
            }
        }
        if (f.advice.repeatUseful && !f.advice.deleteFirst) {
            dialog.setNeutralButton("Dasselbe nochmal") { _, _ -> confirmPull(f.candidate, intro = "") }
        }
        dialog.show()
    }

    private fun onListTapped() {
        beginBusy(Kind.OTHER)
        statusView.text = "Frage Qualcomms Paketliste ab ..."
        activeJob = scope.launch(Dispatchers.Default) {
            runner.listHub()
            runOnUiThread { statusView.text = "Paketliste: Ergebnis im Bericht unten." }
        }
        finishWhenDone()
    }

    private fun showProgress(phase: String, done: Long, total: Long) {
        val now = System.currentTimeMillis()
        if (now - lastProgressUpdate < 300) return
        lastProgressUpdate = now
        val verb = when (phase) {
            "Prüfen" -> "Prüfe die Zip-Datei"
            "Importieren" -> "Importiere in den Modellspeicher"
            else -> "Lade herunter"
        }
        runOnUiThread {
            if (total > 0) {
                progressBar.isIndeterminate = false
                progressBar.progress = (done * 1000 / total).toInt()
                statusView.text = "$verb: ${Metrics.gb(done)} von ${Metrics.gb(total)} (${done * 100 / total} %)"
            } else {
                progressBar.isIndeterminate = true
                statusView.text = "$verb: ${Metrics.gb(done)}"
            }
        }
    }

    /** The list of candidates with their state: next attempt, given up (with the error), loaded. */
    private fun showCandidates() {
        val abandoned = prefs.abandoned
        val current = PullPlanner.current(abandoned.keys)
        candidatesView.text = PullPlanner.CANDIDATES.mapIndexed { i, c ->
            val state = when {
                presentCandidate()?.id == c.id -> "GELADEN (QAIRT ${runner.presentFacts?.qairt ?: "?"})"
                c.id in abandoned -> "gescheitert: " + abandoned[c.id]
                c.id == current?.id -> "nächster Versuch (Knopf 1)"
                else -> "noch nicht dran"
            }
            "${i + 1}. ${c.title}\n     $state"
        }.joinToString("\n") + presentOtherLine()
    }

    private fun presentOtherLine(): String {
        val model = runner.presentModel ?: return ""
        if (presentCandidate() != null) return ""
        return "\nVorhanden, aber keines der Pakete oben: $model, gebaut mit QAIRT ${runner.presentFacts?.qairt ?: "unbekannt"} " +
            "(Ausgabe v0.67.0 oder neuer; Laden dieser Fassung ist auf diesem Telefon gescheitert)"
    }

    private fun startTest(test: FixedTest) {
        if (!modelPresent) {
            info("Zuerst Knopf 1 tippen: Das Modell ist noch nicht geladen.")
            return
        }
        beginBusy(Kind.TEST)
        answerView.text = ""
        statusView.text = (if (runner.loaded) "" else "Lade das Modell auf die NPU ... ") + "${test.title} läuft ..."
        activeJob = scope.launch(Dispatchers.Default) {
            val outcome = runner.runTest(test) { text -> runOnUiThread { answerView.text = text } }
            runOnUiThread {
                when (outcome) {
                    is SpikeRunner.TestOutcome.Ok -> statusView.text = "${test.title}: fertig. Zahlen im Bericht unten."
                    is SpikeRunner.TestOutcome.Failed -> {
                        statusView.text = "${test.title}: fehlgeschlagen. Details im Bericht unten."
                        if (outcome.loadFailed) onLoadFailed(outcome)
                    }
                }
            }
        }
        finishWhenDone()
    }

    private fun onLoadFailed(out: SpikeRunner.TestOutcome.Failed) {
        val model = runner.presentModel
        val candidate = PullPlanner.matching(model, runner.presentFacts?.qairt)
        if (candidate != null) prefs.abandon(candidate.id, "Laden auf der NPU gescheitert (Details im Bericht)")
        val next = PullPlanner.current(prefs.abandoned.keys)
        showCandidates()
        if (candidate == null && out.metadataFormatTooNew) {
            val b = AlertDialog.Builder(this)
                .setTitle("Paket im neuen Format")
                .setMessage(
                    "Das Laden ist gescheitert, bevor der Chip überhaupt benutzt wurde: Das SDK in dieser App (0.10.0) kann die Beschreibung " +
                        "(metadata.json) dieses Pakets nicht lesen, weil das Paket (Ausgabe v0.67.0, gebaut mit QAIRT ${runner.presentFacts?.qairt ?: "?"}) ein neueres Format hat.\n\n" +
                        "Zwei Wege:\n" +
                        "1. Metadaten anpassen (kein Download, rückgängig durch Löschen): Die App schreibt die Beschreibung im alten Format und sichert das Original. " +
                        "Die Rechenprogramme des Pakets sind mit QAIRT ${runner.presentFacts?.qairt?.let { QairtVersion.majorMinor(it) } ?: "2.51"} gebaut, die Laufzeit ist " +
                        "${runner.runtimeQairt ?: "älter"}: Es kann danach trotzdem scheitern.\n" +
                        (if (next != null) "2. ${next.title} laden (${next.sizeText}): hat das alte Format und die Version der Laufzeit.\n" else ""),
                )
                .setPositiveButton("Metadaten anpassen und neu testen") { _, _ -> applyCompatAndRetest() }
                .setNegativeButton("Nicht jetzt", null)
            if (next != null) b.setNeutralButton("Ältere Ausgabe laden") { _, _ -> confirmPull(next, intro = "") }
            b.show()
        } else if (candidate == null && out.bundleNewerThanRuntime && next != null) {
            AlertDialog.Builder(this)
                .setTitle("Älteres Paket versuchen?")
                .setMessage(
                    "Das Laden ist gescheitert. Das vorhandene Paket ist mit QAIRT ${runner.presentFacts?.qairt ?: "?"} gebaut, die Laufzeit in dieser " +
                        "App ist älter (QAIRT ${runner.runtimeQairt ?: "älter"}). Das ist eine mögliche Ursache, noch nicht bewiesen.\n\n" +
                        "Nächstes Paket: ${next.title} (${next.sizeText})\n${next.difference}",
                )
                .setPositiveButton("Ja, laden") { _, _ -> confirmPull(next, intro = "") }
                .setNegativeButton("Nicht jetzt", null)
                .show()
        } else {
            report.add(
                if (next != null) {
                    "Hinweis: Das Laden ist gescheitert. Das nächste Paket wäre ${next.title}: tippe Knopf 1, wenn du es versuchen willst " +
                        "(das vorhandene Paket bleibt, bis du es löschst)."
                } else {
                    "Hinweis: Das Laden ist gescheitert, und es gibt kein weiteres Paket zum Probieren."
                },
            )
        }
    }

    private fun applyCompatAndRetest() {
        beginBusy(Kind.OTHER)
        statusView.text = "Schreibe Kompatibilitäts-Metadaten ..."
        activeJob = scope.launch(Dispatchers.Default) {
            val ok = runner.applyCompatMetadata()
            runOnUiThread {
                statusView.text = if (ok) "Metadaten angepasst. Teste jetzt neu ..." else "Metadaten konnten nicht angepasst werden, siehe Bericht."
                showCandidates()
            }
            pendingRetest = ok
        }
        finishWhenDone()
    }

    private fun onCompatTapped() {
        beginBusy(Kind.OTHER)
        statusView.text = "Schreibe Kompatibilitäts-Metadaten ..."
        activeJob = scope.launch(Dispatchers.Default) {
            val ok = runner.applyCompatMetadata()
            runOnUiThread {
                statusView.text = if (ok) "Metadaten angepasst. Tippe jetzt Knopf 2." else "Metadaten konnten nicht angepasst werden, siehe Bericht."
                showCandidates()
            }
        }
        finishWhenDone()
    }

    private fun onStopTapped() {
        val job = activeJob ?: return
        if (activeKind == Kind.PULL) {
            job.cancel()
            return
        }
        scope.launch(Dispatchers.Default) { runner.requestStop() }
        // if the stream does not end by itself, cancelling the collector stops the native generation
        scope.launch {
            delay(4000)
            if (job.isActive) job.cancel()
        }
    }

    private fun onUnloadTapped() {
        beginBusy(Kind.OTHER)
        activeJob = scope.launch(Dispatchers.Default) {
            runner.unload()
            runOnUiThread { statusView.text = "Modell entladen (bleibt auf dem Telefon gespeichert)." }
        }
        finishWhenDone()
    }

    private fun onDeleteTapped() {
        AlertDialog.Builder(this)
            .setTitle("Modell / angefangenen Download löschen?")
            .setMessage(
                "Löscht alle heruntergeladenen Modelle dieser App vom Telefon (je etwa 3 GB, auch angefangene Downloads, soweit das SDK sie löscht).\n\n" +
                    "Nimm das, wenn: ein Download mit 'unvollständig/beschädigt' endet, Knopf 1 nach einem Abbruch immer wieder scheitert, " +
                    "ein Modell sich nicht laden lässt und du das nächste Paket versuchen willst, oder du Speicher freigeben willst.\n\n" +
                    "Ein einfacher Abbruch braucht das nicht: Knopf 1 setzt den Download fort.",
            )
            .setPositiveButton("Löschen") { _, _ ->
                beginBusy(Kind.OTHER)
                activeJob = scope.launch(Dispatchers.Default) {
                    val ok = runner.deleteModels()
                    runner.refreshModelPresent()
                    runOnUiThread {
                        if (ok) prefs.pulledCandidateId = null
                        statusView.text = if (ok) "Modelle gelöscht." else "Löschen fehlgeschlagen, siehe Bericht."
                        showCandidates()
                    }
                }
                finishWhenDone()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun onCopyTapped() {
        val text = ReportFormatter.fullReport(
            headerLines,
            report.snapshot(),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMAN).format(Date()),
        )
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Bericht", text))
        Toast.makeText(this, "Bericht kopiert (${text.lines().size} Zeilen). Jetzt einfügen und senden.", Toast.LENGTH_LONG).show()
    }

    private fun info(message: String) {
        AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun pulledSuffix(): String =
        runner.presentModel?.let { " ($it, QAIRT ${runner.presentFacts?.qairt ?: "unbekannt"})" } ?: ""

    // ---- busy state ---------------------------------------------------------------------------

    private fun beginBusy(kind: Kind) {
        activeKind = kind
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        refreshButtons()
    }

    private fun finishWhenDone() {
        activeJob?.invokeOnCompletion {
            runOnUiThread {
                activeKind = Kind.NONE
                activeJob = null
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                progressBar.visibility = View.GONE
                refreshButtons()
                if (pendingRetest) {
                    pendingRetest = false
                    startTest(FixedTests.SENTENCE)
                }
            }
        }
    }

    private fun refreshButtons() {
        val idle = activeKind == Kind.NONE
        val ready = runner.supported
        btnPull.isEnabled = idle && ready
        btnTest2.isEnabled = idle && ready && modelPresent
        btnTest3.isEnabled = idle && ready && modelPresent
        btnStop.isEnabled = activeKind == Kind.PULL || activeKind == Kind.TEST
        btnUnload.isEnabled = idle && ready && runner.loaded
        btnDelete.isEnabled = idle && ready
        btnList.isEnabled = idle && ready
        btnCompat.isEnabled = idle && ready && runner.presentFacts?.modelFilesIsList == true
        btnCopy.isEnabled = true
    }

    // ---- views --------------------------------------------------------------------------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun text(size: Float, bold: Boolean = false, mono: Boolean = false): TextView = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(Color.BLACK)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        if (mono) typeface = Typeface.MONOSPACE
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        minimumHeight = dp(56)
        setOnClickListener { onClick() }
    }

    private fun buildUi() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        column.addView(text(24f, bold = true).apply { text = "SecondHand Begleit-Spike" })
        column.addView(text(16f).apply {
            text = "Testet, ob Qualcomms fertiges Qwen3-4B auf der NPU dieses Telefons läuft, und wie schnell. " +
                "Nur ein Test mit festen Texten, es geht kein eigener Text an das Modell."
        })
        bannerView = text(20f, bold = true).apply {
            setTextColor(Color.rgb(176, 0, 32))
            visibility = View.GONE
        }
        column.addView(bannerView)
        headerView = text(15f, mono = true).apply { setTextIsSelectable(true) }
        column.addView(headerView)
        column.addView(text(15f).apply {
            text = "Internet: Diese App nutzt das Internet nur für den Modell-Download, nur nach deinem Tipp auf Knopf 1 " +
                "und deiner Bestätigung. Keine Statistik, kein Hochladen von Texten."
            setTextColor(Color.rgb(60, 60, 60))
        })

        btnPull = button("1. Modell laden (2,7 GB, von Qualcomm)") { onPullTapped() }
        btnTest2 = button("2. Test: festen Satz korrigieren") { startTest(FixedTests.SENTENCE) }
        btnTest3 = button("3. Test: längerer Absatz") { startTest(FixedTests.PARAGRAPH) }
        btnStop = button("Stopp") { onStopTapped() }
        btnUnload = button("Modell entladen") { onUnloadTapped() }
        btnDelete = button("Modell / angefangenen Download löschen") { onDeleteTapped() }
        btnList = button("Paketliste bei Qualcomm abfragen") { onListTapped() }
        btnCompat = button("Option: Kompatibilitäts-Metadaten (kein Download)") { onCompatTapped() }
        btnCopy = button("Bericht kopieren") { onCopyTapped() }
        listOf(btnPull, btnTest2, btnTest3, btnStop, btnUnload, btnDelete, btnList, btnCompat, btnCopy).forEach {
            column.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            })
        }

        column.addView(text(16f, bold = true).apply { text = "Pakete (Reihenfolge der Versuche):" })
        candidatesView = text(15f)
        column.addView(candidatesView)
        statusView = text(18f, bold = true).apply { text = "Starte ..." }
        column.addView(statusView)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            visibility = View.GONE
        }
        column.addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16)))
        column.addView(text(16f, bold = true).apply { text = "Antwort des Modells (live):" })
        answerView = text(18f).apply {
            setTextIsSelectable(true)
            setBackgroundColor(Color.rgb(240, 240, 240))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            minHeight = dp(80)
        }
        column.addView(answerView)
        column.addView(text(16f, bold = true).apply { text = "Bericht (zum Kopieren markierbar):" })
        logView = text(13f, mono = true).apply { setTextIsSelectable(true) }
        column.addView(logView)

        logScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setBackgroundColor(Color.WHITE)
        }
        setContentView(logScroll)
    }
}
