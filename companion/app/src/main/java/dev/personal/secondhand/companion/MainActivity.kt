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
    private lateinit var btnCopy: Button

    private var activeJob: Job? = null
    private var activeKind = Kind.NONE
    private var modelPresent = false
    private var lastProgressUpdate = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SpikePrefs(this)
        val tracker = PrefsStepTracker(this)
        headerLines = ReportFormatter.headerLines(DeviceInfo.collect(this))
        runner = SpikeRunner(GeniexAdapter(this), AndroidProbe(this), RealClock(), tracker, report)
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
        refreshButtons()
        scope.launch(Dispatchers.Default) {
            runner.init()
            if (runner.supported) modelPresent = runner.refreshModelPresent()
            runOnUiThread {
                runner.initFailureLine?.let {
                    bannerView.text = it
                    bannerView.visibility = View.VISIBLE
                }
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
        if (modelPresent) {
            info("Das Modell ist schon vorhanden" + pulledSuffix() + ". Zum erneuten Laden tippe zuerst 'Modell löschen'.")
            return
        }
        when (PullPlanner.nextOffer(prefs.failedChipsets)) {
            PullPlanner.Offer.Primary -> confirmPrimaryPull()
            PullPlanner.Offer.GalaxyBundle -> confirmGalaxyPull(afterFailedPrimary = true)
            PullPlanner.Offer.Exhausted -> info(
                "Beide Varianten (SM8750 und SM8750-AC) wurden schon versucht und haben nicht funktioniert. " +
                    "Schicke den Bericht an den Entwickler.",
            )
        }
    }

    private fun confirmPrimaryPull() {
        val free = DeviceInfo.collect(this).freeStorageBytes
        val freeText = if (free >= 0) "\n\nFreier Speicher jetzt: ${Metrics.gb(free)}" + if (free < 7L * 1073741824) " (das ist knapp!)" else "" else ""
        AlertDialog.Builder(this)
            .setTitle("Modell herunterladen?")
            .setMessage(
                "Modell: Qwen3-4B (Qualcomm, fertig für die NPU)\n" +
                    "Größe: etwa 2,7 GB\n" +
                    "Quelle: Qualcomms öffentlicher Server (AI Hub). Qualcomm sieht dabei deine IP-Adresse, wie bei jedem Download.\n" +
                    "WLAN wird empfohlen.\n" +
                    "Du brauchst etwa 7 GB freien Speicher (Download plus entpacktes Modell).\n" +
                    "Es werden keine eigenen Daten gesendet." + freeText,
            )
            .setPositiveButton("Jetzt laden") { _, _ -> startPull(GeniexConstants.CHIPSET_PRIMARY) }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun confirmGalaxyPull(afterFailedPrimary: Boolean) {
        val intro = if (afterFailedPrimary) {
            "Für die Chip-Bezeichnung SM8750 hat Qualcomm kein Modellpaket (oder es ließ sich nicht verwenden).\n\n"
        } else ""
        AlertDialog.Builder(this)
            .setTitle("Galaxy-Paket versuchen?")
            .setMessage(
                intro +
                    "Es gibt ein Paket 'SM8750-AC'. Es ist für eine andere Variante des Chips gebaut (Samsung Galaxy) " +
                    "und läuft auf diesem Telefon vielleicht NICHT.\n\n" +
                    "Größe: etwa 2,7 GB, Quelle: Qualcomms öffentlicher Server, WLAN empfohlen, etwa 7 GB frei nötig.",
            )
            .setPositiveButton("Ja, SM8750-AC laden") { _, _ -> startPull(GeniexConstants.CHIPSET_GALAXY) }
            .setNegativeButton("Nein", null)
            .show()
    }

    private fun startPull(chipset: String) {
        beginBusy(Kind.PULL)
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        statusView.text = "Lade herunter (Chipset $chipset) ..."
        activeJob = scope.launch(Dispatchers.Default) {
            val outcome = runner.pull(chipset) { done, total -> showProgress(done, total) }
            runOnUiThread {
                progressBar.visibility = View.GONE
                when (outcome) {
                    is SpikeRunner.PullOutcome.Success -> {
                        prefs.pulledChipset = outcome.chipset
                        modelPresent = true
                        statusView.text = "Modell geladen (Variante ${outcome.chipset}). Weiter mit Knopf 2."
                    }
                    is SpikeRunner.PullOutcome.Failed -> {
                        statusView.text = "Download fehlgeschlagen. Details im Bericht unten."
                        if (outcome.offerGalaxyBundle) {
                            prefs.markFailed(outcome.chipset)
                            confirmGalaxyPull(afterFailedPrimary = true)
                        }
                    }
                }
            }
        }
        finishWhenDone()
    }

    private fun showProgress(done: Long, total: Long) {
        val now = System.currentTimeMillis()
        if (now - lastProgressUpdate < 300) return
        lastProgressUpdate = now
        runOnUiThread {
            if (total > 0) {
                progressBar.isIndeterminate = false
                progressBar.progress = (done * 1000 / total).toInt()
                statusView.text = "Lade herunter: ${Metrics.gb(done)} von ${Metrics.gb(total)} (${done * 100 / total} %)"
            } else {
                progressBar.isIndeterminate = true
                statusView.text = "Lade herunter: ${Metrics.gb(done)}"
            }
        }
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
                        if (outcome.loadFailed) onLoadFailed()
                    }
                }
            }
        }
        finishWhenDone()
    }

    private fun onLoadFailed() {
        val chipset = prefs.pulledChipset ?: return
        prefs.markFailed(chipset)
        val next = PullPlanner.nextOffer(prefs.failedChipsets)
        report.add(
            when (next) {
                PullPlanner.Offer.GalaxyBundle ->
                    "Hinweis: Die Variante $chipset ließ sich nicht laden. Zum Probieren der anderen Variante tippe " +
                        "'Modell löschen' und danach Knopf 1: dann wird SM8750-AC angeboten."
                else -> "Hinweis: Die Variante $chipset ließ sich nicht laden, und es gibt keine weitere Variante zum Probieren."
            },
        )
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
            .setTitle("Modell löschen?")
            .setMessage("Das heruntergeladene Modell (etwa 3 GB) wird vom Telefon gelöscht. Danach kannst du es mit Knopf 1 neu laden.")
            .setPositiveButton("Löschen") { _, _ ->
                beginBusy(Kind.OTHER)
                activeJob = scope.launch(Dispatchers.Default) {
                    val ok = runner.deleteModel()
                    val present = runner.refreshModelPresent()
                    runOnUiThread {
                        modelPresent = present
                        if (ok) prefs.pulledChipset = null
                        statusView.text = if (ok) "Modell gelöscht." else "Löschen fehlgeschlagen, siehe Bericht."
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

    private fun pulledSuffix(): String = prefs.pulledChipset?.let { " (Variante $it)" } ?: ""

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
        btnDelete.isEnabled = idle && ready && modelPresent
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
        btnDelete = button("Modell löschen (Speicher freigeben)") { onDeleteTapped() }
        btnCopy = button("Bericht kopieren") { onCopyTapped() }
        listOf(btnPull, btnTest2, btnTest3, btnStop, btnUnload, btnDelete, btnCopy).forEach {
            column.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            })
        }

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
