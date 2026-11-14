# Handoff

Das kurze Gedächtnis des Projekts für den Lead (Begriffe: `CLAUDE.md` → Begriffe). Lies es zu
Beginn jeder Sitzung nach `CLAUDE.md`. Der Lead aktualisiert es am Ende jeder Runde: aktueller
Stand, offene Punkte, neue Sackgassen und wiederholte Fehler, eine Zeile pro Build im Build-Log.
Lösche, was nicht mehr stimmt; die Historie bleibt in git. Code-Fakten und Code-Fallstricke
gehören nach `docs/ARCHITECTURE.md`, nicht hierher.

## Aktueller Stand

<!-- Der Session-Start-Hook gibt diesen Abschnitt aus (bis ca. 10 Zeilen): kurz und aktuell halten. -->
- Stand 2026-11-14 (Ende der Runde): `main` = Build 19 (geprüft). Neu seit Build 16: 38a–38h
  Einstellungen 1:1 nach dem Mockup (Startseite und sieben Bereichsseiten, alte Seiten bleiben
  erreichbar, „Leiste“ statt „Symbolleiste“), 41 Formatwächter (libphonenumber), 42 Textbausteine
  2.0 (Platzhalter, Ausfüllfelder, Enter-Hook auch für Hardware-Enter). Alle Etappen mit Prüfer
  und Nachprüfung; volle Agent-Suite lokal grün; kein Gerätetest.
- Beim Owner: Build 18 (Test, gleiche Funktionen, noch ohne Prüferkorrekturen) und Build 19.
- RGB-Messung des Owners (Build 19, Xiaomi 24129PN74G, Android 16, Diagnose, 5 s mit Licht):
  138 Bilder (≈ 28/s bei 30 Hz Licht); Licht im UI-Thread Ø 0,3 / max 0,7 ms; ganzes Zeichnen der
  Tastatur Ø 2,7 / max 6,3 ms; Raster im Hintergrund 133× Ø 4,5 / max 11,9 ms. Damit ist das
  Zeichnen der Tastatur kein Engpass (Budget bei 120 Hz: 8,3 ms). Owner (2026-11-14): „ruckelt
  tatsächlich nichts“ – RGB-Leistung erledigt.
- Nächste Sitzung: Rückmeldungen zu Build 17–19 (Einstellungen am Gerät, Formatwächter,
  Textbausteine) und weiter offen zu Build 16 (RGB-Diagnosewerte, Symbol-Erkennung); dann ROADMAP
  34, 37, 39, 40.

## Offene Punkte

0. **Nächste Runde.** Zuerst Rückmeldung zu Build 18/19: Einstellungen am Gerät (alle sieben
   Bereiche, Schalter wirken, Design- und Profilkarten, FLAG_SECURE auf KI-Seiten, Direct Boot),
   Formatwächter (Chips in echten E-Mail-, Telefon-, IBAN-Feldern), Textbausteine 2.0 (Feldabfrage
   in Messengern, Schreibmarke nach `{cursor}`, P147). Annahmen des Leads aus 41/42 bestätigen
   (ROADMAP). Offene niedrige Befunde: VALIDATION 38b–42. Fallstricke belegt bis P149, frei ab
   P150. Bewährt: Agenten mit `--max-workers=1 -Xmx1536m`; mehr als zwei parallele Gradle-Läufe
   brachten den Container zum Neustart (Arbeitskopien überlebten, Agenten per SendMessage
   fortgesetzt). Weiter offen von Build 16: RGB-Leistung am Xiaomi (Diagnose → „RGB-
   Beleuchtung“; größter Posten laut JVM ist das Zeichnen des Lichts durch die Tastenmasken, 2,5–18
   ms je Bild in Robolectric-Software, am Gerät unbekannt; nächster Kandidat: Licht nicht je Taste
   durch Masken, sondern eine Ebene), Symbol-Erkennung (Tipp-Grenze 8 dp, Bereich 3 Tastenbreiten:
   Abwägung zwischen verlorenen Buchstaben neben der Zeichnung und abreißenden Symbolen; „glitcht“
   ungeklärt), Rastpunkte der Leertaste (Abstände, Gefühl), Regler-Ticks. Danach ROADMAP 34
   (Annahme: eigene RGB-Designs), ROADMAP 37 (Bildwiederholrate als Karten, Skizze unter
   `docs/referenzen/`) und die offenen niedrigen Befunde in VALIDATION (Etappen 28–36).
   Arbeitsweise dieser Runde: Agenten je Etappe in Arbeitskopien mit eigenen Sicherungsordnern
   (`scratchpad/bak-<etappe>/`), Fallstrick-Nummern vorab verteilen (belegt bis P118, frei ab
   P119), nie `agent-core` neu bauen, während ein eigener App-Testlauf läuft
   (ZipException), Merge-Konflikte in Docs: beide Seiten, generierte Blöcke per `--write`.
   Parallel-Sitzung auf eigenem Branch hat sich bewährt (35); Merge durch die Haupt-Sitzung.
   Danach offen: 25b animierte Emojis, 25c Sticker-Ersteller, U6 `@generate`.
   Wartet auf den Owner: Secret `RELEASES_TOKEN`. Entschieden (Owner, 2026-11-14, Etappe 44):
   vertrauliche Clips und Geheimnisse kommen nicht mehr in den normalen Verlauf, sondern verdeckt
   und nur im Speicher in den Tresor.

1. **Gerätetest teilweise erledigt (G1).** Im Emulator geprüft und in Ordnung: Installation,
   Tippen, Löschen, Rad, Panel, Ersetzen, Schutz bei geänderter Auswahl, Passwortfeld,
   Testwerkzeug, Start der Kalenderübergabe, KI-Einstellungen mit Screenshot-Schutz, Schlüssel
   speichern und löschen mit dem echten Keystore. Protokoll und die Liste des Ungeprüften:
   `docs/VALIDATION.md` → G1. Offen bleibt alles unter „Nicht geprüft“ dort, außerdem ein echtes
   Telefon und eine funktionierende Kalender-App als Ziel.
2. **Keine echte Modell-API-Anfrage.** Alle Provider-Tests nutzen Loopback- oder Testprovider. Ein
   Schlüssel ist nicht vorhanden und wird nur vom Owner in der App eingegeben.
3. **Git-Basis.** Der Code wurde aus dem Übergabe-Archiv vom 2026-11-11 importiert. Die frühere
   Historie (Etappen-Commits bis `71a4c959`) ist nicht enthalten. `main` gibt es seit dem
   2026-11-12 (Stand nach 24b und dem Update-Workflow); neue Sitzungen starten davon, sobald der
   Owner ihn in GitHub als Standard-Branch eingestellt hat. Jede Sitzung arbeitet auf ihrem
   eigenen Branch und bringt geprüfte Runden nach `main`.
4. **Geerbte Testfehler (Baseline, genau fünf):** `InputLogicTest.insertLetterIntoWordHangulFails`
   (Hangul), `StringUtilsTest.detectEmojisAtEndFails` (Emoji), `XLinkTest.layoutsLinks` und
   `XLinkTest.readmeLinks` (externe Erreichbarkeit, zuletzt HTTP 503, früher 404),
   `XLinkTest.knownDictionaries` (geändertes externes Datenformat). Die Baseline wird nicht
   erweitert; zeitweise externe 504-Fehler gelten nicht als neue Baseline.
   In der Cloud-Umgebung „SecondHand“ scheitern zusätzlich `XLinkTest.layoutsLinksInternal` und
   `XLinkTest.otherLinks` mit HTTP 403 vom Netzwerkfilter (Hosts nicht freigegeben); auch das
   ist Umgebung, keine neue Baseline.
5. **Upstream-CI `build-test-auto.yml`** ruft `testRunTestsUnitTest` auf, den es mit dem
   festgeschriebenen Plugin nicht gibt. Läuft nur bei Pull Requests und würde dort fehlschlagen.
   Bewusst unverändert gelassen.
6. **Android 5 (API 21/22)** ist nicht geprüft, weil Robolectric API 21 nicht unterstützt. Rein
   lokale Workflows sind dort ohne Journal verfügbar, Cloud ist ohne sicheren Speicher gesperrt.
7. **Alte `GlobalScope`-View-Callbacks** aus Upstream sind für eine eigene Härtung vorgemerkt.
8. **Ausgabegrenze bei OpenAI.** `max_output_tokens` steht bei 2048 (Builder 4096) und schließt
   bei Reasoning-Modellen die Denk-Token ein. Ob das für das eingestellte Modell reicht, lässt
   sich nur mit einer echten Anfrage klären. Anthropic und Gemini stehen bei 8192.
9. **Lerndaten mit der nativen Engine ungeprüft:** Pause (kein Wort landet in der Historie),
   Zählen, Export und Löschen echter Lerndaten brauchen ein Gerät (G2).
10. **Lint:** Die geerbte Baseline `app/lint-baseline-upstream.xml` und die Warnungen bleiben
    bestehen.
11. **Gemini-Vertrag nicht neu prüfbar.** Die Doku-Hosts `ai.google.dev` und
    `aistudio.google.com` sind in der Cloud-Umgebung „SecondHand“ nicht freigegeben. Der
    Anthropic-Vertrag in `docs/PROVIDERS.md` wurde am 2026-11-11 gegen die offizielle Doku
    bestätigt (Bearer-Schlüssel, `anthropic-version: 2026-10-21`, kein `temperature`,
    `stop_reason` u. a. `end_turn`, `max_tokens`, `refusal`).
12. **Externe Dienste (Gmail, Google Calendar, Notion)** brauchen vom Owner registrierte
    OAuth-Clients. Ohne sie ist dieser Teil von Etappe 16 nicht baubar.
13. **Diktat nur mit nachgebautem Erkenner getestet.** Offen auf einem echten Telefon: ob Android
    dem Tastaturdienst die Aufnahme erlaubt, der Berechtigungsdialog, Sprachpakete, Qualität.
    Die APK fordert jetzt zusätzlich `RECORD_AUDIO` an.
14. **Offen aus dem dritten Review** (`docs/VALIDATION.md` → Lesender Review von 18a): keine Tests
    für die Diktat-Verdrahtung im Controller und den Android-12-Pfad des Erkenners; das
    Zeitfenster beim Einfügen ist nur verkleinert (Idee: ab API 31 `getSurroundingText`); zwei
    schwache `InputLogicTest`-Fälle; kein Zeitlimit nach „Fertig“.

15. **Flacher Look (19a–19e) ohne Gerät.** Maße stammen aus Screenshots mit 1200 px Breite;
    die Dichte 3,25 ist erschlossen, die Systemschrift des Telefons unbekannt. Im Test gerendert
    wird mit Roboto. Offen am Gerät: Gesamtbild, Abstand zur Navigationsleiste, Wechsel zwischen
    Werkzeugen und Wortvorschlägen, Menü-Aktionen, Zwischenablage mit echten Bildern, Balken im
    Panel, TalkBack. Liste je Etappe: `docs/VALIDATION.md` → Etappe19….

16. **Emoji-Mix (25a): Lizenzdatei im Repo des Autors nachprüfen.** `xsalazar/emoji-kitchen-backend`
    hat keine Lizenzdatei; Erlaubnis laut Owner mündlich am 2026-11-13, schriftlich ausstehend
    (`docs/BUILD.md` → Emoji-Mix-Tabelle). Außerdem ungeprüft: echte Bilder von www.gstatic.com
    (in der Cloud-Umgebung gesperrt), Einfügen in WhatsApp/Telegram, der Mixer in der
    Emoji-Ansicht am Gerät (Leistenhöhe, Animation, Tastenbeschriftung „Emojis suchen“ /
    „Emoji-Mixer“ in schmalen Tasten), ob die Leiste nach Emojis aus der Emoji-Ansicht den Chip
    zeigt.

## Arbeitsweise

### Sitzungsstart

Der SessionStart-Hook (`.claude/hooks/session-start.sh`, registriert in `.claude/settings.json`)
gibt den Block „Aktueller Stand“ aus, die Startzeile jedes Abschnitts von ARCHITECTURE.md (als
Leseoffset nutzen) und die Zusammenfassung des Docs-Checks. Er blockiert nie eine Sitzung.

### Ein schreibender Lead, lesende Prüfer

- Der Lead arbeitet Etappe für Etappe auf einem Branch: implementieren, `tools/dev.py`-Prüfungen
  ausführen, Befunde beheben, committen, erst dann die nächste abhängige Etappe.
- Prüfer bekommen die betroffenen Dateien, die Etappe und die Prüfer-Regeln aus `CLAUDE.md`.
  Bewährte Review-Arten: Architektur und Datenschutz, Nebenläufigkeit, Tastatur-Engine
  (`InputLogic`, `RichInputConnection`, Touch).
- Denkaufwand nach Art der Arbeit: kleine Änderung im bekannten Muster niedrig bis mittel;
  normale Etappe mit Tests hoch; Nebenläufigkeit und komplexe Zustände sehr hoch; Architektur-
  und Datenschutzreview hoch; schwer reproduzierbarer Fehler sehr hoch bis maximal.

### Prüfungen

- Befehle und Baseline: `docs/ARCHITECTURE.md` → Build, Tests, CI, Docs-System.
- Was als bestanden gilt: Ein Befehl ist bestanden, wenn er ohne neue Fehler endet;
  `upstream-tests` endet wegen der fünf Baselinefehler immer mit Fehlerstatus, neue Fehler dort
  getrennt prüfen. Jede Etappe bekommt ihren Nachweis in `docs/VALIDATION.md`.

### Docs-Check (`scripts/docs_sync.py`, Konfiguration `scripts/docs_sync.json`)

- `python3 -I scripts/docs_sync.py --check`: zeigt Fehler und Warnungen, Exit 1 bei Fehlern.
- `python3 -I scripts/docs_sync.py --write`: erzeugt die generierten Blöcke und `AGENTS.md` neu,
  dann Check.
- `python3 -I scripts/docs_sync.py --toc docs/ARCHITECTURE.md`: Abschnitte mit Zeilenbereichen.
- Fehler: ein generierter Block ist veraltet (→ `--write`); eine kartierte Datei fehlt in
  ARCHITECTURE.md (→ in ihrem Bereich nennen); eine Zahl in `docs/FEATURES.md` passt nicht zur
  Quelle (KI-Textaktionen, lokale Textwerkzeuge, Zielsprachen); `AGENTS.md` weicht von
  `CLAUDE.md` ab (→ `--write`).
- Warnungen (nie ein Fehlschlag): Code geändert, aber `release-notes/next.md` ist leer; ein Name in
  der Landkarte zeigt auf nichts; HANDOFF.md ist sehr lang.
- Wo er läuft: Sitzungsstart (Zusammenfassung), CI (`.github/workflows/docs-check.yml`),
  optional Pre-Commit-Hook (`bash scripts/install-hooks.sh`; blockiert nur auf `main`).

### Release (nur Lead)

0. Test-Build (Wunsch des Owners vom 2026-11-13, gilt immer): Sind alle Funktionen der Runde auf
   dem Arbeitsbranch, kompiliert und die Agent-Tests grün, den Workflow „Agent Keyboard APK“ auf
   dem Arbeitsbranch starten und die APK als Datei an den Owner schicken (Download aus dem
   Release: `https://api.github.com/repos/Tra1c0/SecondHand/releases/assets/<id>` mit
   `Accept: application/octet-stream` geht über den Proxy). Dem Owner sagen, was noch ungeprüft
   ist. Dann Prüfer, volle Tests und Korrekturen; danach die Schritte unten und ein zweiter Build.
1. Runde auf `main` zusammenführen, dort bauen und prüfen.
2. Docs laut `CLAUDE.md` → „Docs aktuell halten“ im selben Push aktualisieren.
   `release-notes/next.md` durch die Notizen dieses Pushs ersetzen.
3. `python3 -I scripts/docs_sync.py --check` muss grün sein; `main` einmal pushen.
4. Prüf-Build: `python3 tools/dev.py build`. Zum Installieren startet der Owner (oder der Lead
   über die GitHub-Werkzeuge, falls verfügbar) den Workflow „Agent Keyboard APK“ auf dem
   gepushten Branch; die APK steht danach als Vorab-Release `build-<Nummer>` bereit und
   installiert sich als Update (`docs/BUILD.md` → Installierbare APK).

## Sackgassen (Prozess und Umgebung; Code-Sackgassen stehen in ARCHITECTURE.md)

- **Emulator in einer Cloud-Umgebung ohne `/dev/kvm`:** nur Software-Emulation. Die Toolchain
  liegt in `/home/user/.toolchains` (flüchtig, pro Container neu aufzubauen: JDK 17 per `apt`,
  cmdline-tools 16111833, Paketnamen mit `/` statt `;`, z. B. `platforms/android-37.0`).
  - Abbild `aosp_atd` (API 35): bootet in ca. 9 Minuten, rendert aber kein Bild
    (Screenshots schwarz) und startet keine normalen Apps. Für UI-Tests unbrauchbar.
  - Abbild `default` (API 35): rendert, bootet in ca. 10 Minuten. Mit vier Gast-Kernen und
    einem Emulations-Thread lag die Gast-Last bei 15–20, überall ANRs. Schon gesetzte Abhilfen:
    Bluetooth deaktiviert (Absturzschleife), Animationen aus, App mit
    `cmd package compile -m speed -f` vorkompiliert.
  - Bewährt (G1): vier Gast-Kerne ohne `thread=multi`, kein Gradle parallel, nach dem Boot
    warten, bis die Gast-Last (`cat /proc/loadavg`) unter etwa 6 fällt, dann
    `settings put global hide_error_dialogs 1`. Ein Tastendruck braucht 1–2 Sekunden.
  - Schlechter: `-qemu -accel tcg,thread=multi` mit zwei Gast-Kernen. Android bootete nach über
    20 Minuten nicht, weil der Systemdienst wegen Zeitüberschreitung immer wieder neu startete.
  - `uiautomator dump --windows` liefert auch das Tastaturfenster (Texte, IDs) und funktioniert
    auch dort, wo Screenshots wegen `FLAG_SECURE` schwarz sind.
  - 2026-11-12: Dasselbe Abbild mit denselben Optionen kam nicht mehr in einen stabilen
    Zustand. Der Systemdienst wurde alle 3–4 Minuten vom Watchdog beendet („Blocked in handler
    on main thread“), auch ohne Gradle und ohne Abfragen von außen. Statt des Emulators hat die
    Runde 19 die echten Views in Robolectric mit nativer Grafik gerendert und mit den
    Screenshots verglichen (`FlatStyleRenderTest` u. a.). Das ersetzt keinen Gerätetest.
  - Die Kalender-App des Abbilds `default` (API 35) ist defekt; Übergaben dorthin enden im
    Absturz der Ziel-App.
- **Gradle-Testbericht scheitert mit „Failed to create MD5 hash“ / „Could not generate test
  report“:** Die Shell hatte keine UTF-8-Locale, Testnamen mit Akzenten („né“) ließen sich nicht
  als Datei schreiben. Vor Gradle `export LC_ALL=C.UTF-8 LANG=C.UTF-8` setzen.
- **Maven Central leitet große Dateien auf `gcp.mirrors.maven.org` um, das in der Umgebung
  gesperrt ist (403):** lokal ein Gradle-Init-Skript `~/.gradle/init.d/mirror.gradle` mit
  `https://maven-central.storage-download.googleapis.com/maven2/` als zusätzlichem Repository
  anlegen (nicht ins Repo); die Robolectric-Laufzeiten (`android-all-instrumented`
  `16-robolectric-13921718-i7` und `9-robolectric-4913185-2-i7`) von dort nach `~/.m2` legen.
- **Gradle-Init-Skript mit Spiegel (2026-11-13):** Der Spiegel muss VOR Maven Central stehen
  (`repositories.addFirst` in `beforeSettings`/`allprojects`), sonst drosselt Maven Central schon
  beim Konfigurieren (429). Robolectric-Laufzeiten `16-…-i7` und `9-…-2-i7` per `curl` vom Spiegel
  nach `~/.m2` legen, bevor Tests laufen. Toolchain: `apt-get install openjdk-17-jdk-headless`,
  cmdline-tools 16111833, `sdkmanager --sdk_root=… 'platforms;android-37.0' …` ging mit `;`.
- **Maven Central drosselt (HTTP 429)** bei vielen parallelen Downloads; Build wiederholen.
  Robolectric konnte die Android-16-Laufzeit nicht selbst laden: die Datei
  `android-all-instrumented-16-robolectric-13921718-i7.jar` samt `.pom` und `.sha512` per `curl`
  nach `~/.m2/repository/org/robolectric/android-all-instrumented/<Version>/` legen.
- **`dl.google.com`** ist nur in der Cloud-Umgebung „SecondHand“ freigegeben, nicht in „Default“.
- **`sdkmanager`/`avdmanager`:** Der aktuelle `sdkmanager` ist ein Android-CLI-Shim. Die alte
  Java-Version von `avdmanager` erkannte einen getrennten SDK-Root erst über Symlinks im
  ursprünglichen SDK. Keine Paket-XMLs manipulieren.
- **`testRunTestsUnitTest`** aus der Upstream-CI existiert nicht; lokal `testDebugUnitTest` bzw.
  `tools/dev.py` nutzen.
- **Externe Link-Tests (`XLinkTest`)** hängen von fremden Servern ab und schwanken zwischen 404,
  503 und 504. Nicht als App-Fehler werten, Baseline aber nicht erweitern.

## Wiederholte Fehler (Prozess; Code-Fallstricke: ARCHITECTURE.md → Fallstricke)

- Docs liefen dem Code hinterher: `docs/WORKFLOWS.md` nannte nach Etappe 14 noch 13e als
  aktuellen Stand, die README nannte nur zwei der sechs KI-Aktionen (beides korrigiert). Regel: Docs im selben Push wie den
  Code aktualisieren (`CLAUDE.md` → Docs aktuell halten).

## Build-Log (eine Zeile pro Build, neueste zuerst)
- Build 19 (geprüft, von `main`): 38a–38h, 41, 42 mit allen Prüferkorrekturen und
  Nachprüfungen; Agent-Suite lokal grün (889, plus gezielte Läufe der letzten Korrekturen), GitHub-
  Lauf siehe Actions; kein Gerätetest.
- Build 18 (Test, Arbeitsbranch 5e8086c): 38a–38h, 41, 42 vor den Prüferkorrekturen
  (E-Mail-Hinweise bei echten Domains, Inkognito-Lücke in Zahlenfeldern, Hardware-Enter).
- Build 17 (Test, Arbeitsbranch 601c703): Einstellungen-Startseite 38a.
- Build 16 (geprüft, von `main`): Etappen 28–33, 35, 36 mit allen Prüferkorrekturen; GitHub-Lauf
  auf 9f097dd grün (Agent, Upstream ohne neue Fehler, Lint); kein Gerätetest.
- Build 15 (Test, Arbeitsbranch 7818560): RGB-Leistung (Hintergrund-Raster, Kacheln), Korrekturen
  zu 30/31, Lichtkosten auf der Diagnose-Seite; noch alte Symbol-Erkennung.
- Build 14 (Test, Arbeitsbranch caeca49): 28–33 und 35. Rückmeldung: Symbol-Erkennung schlecht,
  RGB-Menü und Tastatur ruckeln.
- Build 13 (geprüft, von `main`): wie Build 12 plus Prüferfixes (Emoji-Mix-
  Senke, Symbol nach Ausblenden, Screenshot vor dem Start, Update-Migrationen P84); Upstream mit
  den 7 bekannten Fehlern (lokal, Stand 15b3c5a), Agent-Suite (lokal, ef4f172), GitHub-Lauf 4 auf
  dem Endstand eef5254 grün (Agent, Upstream ohne neue Fehler, Lint; Lauf 3 hatte den wackeligen
  `LearningDataTest`, behoben); kein Gerätetest.
- Build 12 (Test-Build, Arbeitsbranch 15b3c5a, vor Abschluss der Prüfer an den Owner):
  Rückmeldung: Haptik-Kette spielt sofort statt an Rastpunkten (→ 33), eigenes Design nach dem
  Update verändert (→ P84, behoben).
- Build 11 (signiert, Workflow vom Arbeitsbranch c2a91d2, auf Wunsch vor Abschluss der Prüfungen
  ausgegeben): Screenshot zuerst, RGB, Sprachbefehle, Emoji-Wörterbücher; danach Upstream 778 mit
  den 7 bekannten Fehlern (nach globalem Test-Shadow), Agent-Suite, Lint, Build grün; kein Gerätetest.
- Build 10 (signiert, Workflow): Vorlagen, `@wecker`/`@timer`, Design-Editor, Leertasten-Gesten,
  Prüferbefunde behoben; Agent-Suite grün, Upstream 722 mit den 7 bekannten Fehlern, Lint und
  Build grün; kein Gerätetest.
- 0.1.0 Debug (Etappen 24a–24b): getippte Befehle mit Chips, `@math` mit Ergebnis-Chip und
  Rechner; Ergebnisse der Prüfungen: `docs/VALIDATION.md` → Etappe24a und 24b; kein Gerätetest.

- 0.1.0 Debug (Etappen 23a–23d): Symbolseiten wie die Vorlage, Emojis in der Leiste, Laufschrift,
  Screenshot-Pille (opt-in, Bildberechtigung); Agent-Suite 296/0, Lint grün, kein Gerätetest.
- 0.1.0 Debug (Etappen 20a–22b): Umlaut-Schutz, gemerkte und gelernte Korrekturen, persönliche
  Trefferzonen mit Kalibrierung; Vollregression 478 Tests mit den sieben bekannten Fehlern,
  Agent-Suite grün, Build und Lint grün; auf keinem Gerät geprüft.
- 0.1.0 Debug (Etappen 19a–19e): Tastenstil „Flat“ mit Vorlage, Leiste mit fünf Knöpfen,
  Tastaturmenü, Zwischenablage-Ansicht mit Vorschaubild-Cache, Panel-Look; Vollregression 449
  Tests mit den sieben bekannten Fehlern, Build und Lint grün; auf keinem Gerät geprüft.
- 0.1.0 Debug (Etappe 18a): Diktat über den On-device-Erkenner mit Vorschau und Einfügen an der
  Schreibmarke; neue Berechtigung Mikrofon; Agent-Suite 238/0, Build und Lint grün; im Emulator
  nur Panel und Ablehnungsmeldung gesehen, Erkennung ungeprüft.
- 0.1.0 Debug (Etappe 17a): Seite „Gelernte Wörter“ (Pause, Zählen, Export, Löschen);
  Agent-Suite 210/0, Build und Lint grün; auf keinem Gerät geprüft.
- 0.1.0 Debug (G1): Overlays enden über der Navigationsleiste, Panel bleibt nach Übergabe
  bedienbar; im Emulator Tippen, Rad, Panel, Ersetzen, Keystore geprüft; Agent-Suite 206/0.
- 0.1.0 Debug (Etappe 16a/b): Anthropic und Gemini als Anbieter; Agent-Suite 205/0, Kern 52/0,
  Build und Lint grün; auf keinem Gerät geprüft.
- 0.1.0 Debug (Etappe 15): Werkzeuge im Panel; APK nicht mehr test-only; Kern 52/0, Agent-Suite
  198/0, Lint grün; im Emulator installiert, Tippen ungeprüft.
- 0.1.0 Debug (Etappe 14c): Act Builder mit privater Entwurfsprüfung; ARM64- und x86_64-APK,
  Agent-Suite 189/0, Vollregression 370 Tests mit genau fünf Baselinefehlern, Lint grün.
