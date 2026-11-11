# Handoff

Das kurze Gedächtnis des Projekts für den Lead (Begriffe: `CLAUDE.md` → Begriffe). Lies es zu
Beginn jeder Sitzung nach `CLAUDE.md`. Der Lead aktualisiert es am Ende jeder Runde: aktueller
Stand, offene Punkte, neue Sackgassen und wiederholte Fehler, eine Zeile pro Build im Build-Log.
Lösche, was nicht mehr stimmt; die Historie bleibt in git. Code-Fakten und Code-Fallstricke
gehören nach `docs/ARCHITECTURE.md`, nicht hierher.

## Aktueller Stand

<!-- Der Session-Start-Hook gibt diesen Abschnitt aus (bis ca. 10 Zeilen): kurz und aktuell halten. -->
- Pausiert auf Wunsch des Owners (2026-11-11, mitten in G1). Weiter erst nach neuem Auftrag.
- Fertig: Etappen 0–15 (zuletzt Werkzeuge: Testwerkzeug und Kalenderentwurf).
- Letzter Build: 0.1.0 Debug nach Etappe 15, eine APK für alle ABIs, nicht mehr test-only.
- Geprüft: Kern 52/0, Agent-Suite 198/0, Build, Lint. Vollregression nach Etappe 15 steht aus.
- G1 begonnen: Emulator bootet, Installation und Aktivierung klappen; Tippen noch NICHT geprüft
  (Offene Punkte 1 und Sackgassen → Emulator).
- Danach: `docs/ROADMAP.md` → Beschlossen (16 mit Anthropic zuerst, dann 17, 18, A).
- Wartet auf den Owner: Auftrag zur Fortsetzung; Entscheidung über den Branch `main`.

## Offene Punkte

1. **Gerätetest erst angefangen.** Erreicht im Emulator (API 35, ohne KVM): Boot, Installation
   der APK ohne `-t`, Tastatur per `ime enable`/`ime set` aktiviert und als Standard gesetzt.
   Nicht erreicht: ein einziger Tastendruck. Der Gast war so überlastet, dass Android Apps und
   die Tastatur wegen Startzeitüberschreitung beendete („failed to complete startup“). Das ist
   bisher ein Umgebungsbefund, kein belegter App-Fehler; ob die Tastatur bei normaler
   Geschwindigkeit sauber startet, ist offen. Nächster Versuch: Sackgassen → Emulator.
   Checkliste: `docs/VALIDATION.md` → Gerätetest vor Meilensteinfreigabe; Keystore und
   Direct Boot: `docs/STORAGE.md` → Prüflimits. Auch die Kalenderübergabe (Etappe 15) ist auf
   einem Gerät ungeprüft.
2. **Keine echte Modell-API-Anfrage.** Alle Provider-Tests nutzen Loopback- oder Testprovider. Ein
   Schlüssel ist nicht vorhanden und wird nur vom Owner in der App eingegeben.
3. **Git-Basis.** Der Code wurde aus dem Übergabe-Archiv vom 2026-11-11 importiert. Die frühere
   Historie (Etappen-Commits bis `71a4c959`) ist nicht enthalten. Auf dem Remote gibt es noch
   keinen Branch `main`. `base_branch` in `scripts/docs_sync.json` ist trotzdem `main`; bis
   dahin vergleichen die Warnungen mit dem letzten Commit.
4. **Geerbte Testfehler (Baseline, genau fünf):** `InputLogicTest.insertLetterIntoWordHangulFails`
   (Hangul), `StringUtilsTest.detectEmojisAtEndFails` (Emoji), `XLinkTest.layoutsLinks` und
   `XLinkTest.readmeLinks` (externe Erreichbarkeit, zuletzt HTTP 503, früher 404),
   `XLinkTest.knownDictionaries` (geändertes externes Datenformat). Die Baseline wird nicht
   erweitert; zeitweise externe 504-Fehler gelten nicht als neue Baseline.
5. **Upstream-CI `build-test-auto.yml`** ruft `testRunTestsUnitTest` auf, den es mit dem
   festgeschriebenen Plugin nicht gibt. Läuft nur bei Pull Requests und würde dort fehlschlagen.
   Bewusst unverändert gelassen.
6. **Android 5 (API 21/22)** ist nicht geprüft, weil Robolectric API 21 nicht unterstützt. Rein
   lokale Workflows sind dort ohne Journal verfügbar, Cloud ist ohne sicheren Speicher gesperrt.
7. **Alte `GlobalScope`-View-Callbacks** aus Upstream sind für eine eigene Härtung vorgemerkt.
8. **Lint:** Die geerbte Baseline `app/lint-baseline-upstream.xml` und die Warnungen bleiben
   bestehen.
9. **Gemini-Vertrag nicht neu prüfbar.** Die Doku-Hosts `ai.google.dev` und
   `aistudio.google.com` sind in der Cloud-Umgebung „SecondHand“ nicht freigegeben. Der
   Anthropic-Vertrag in `docs/PROVIDERS.md` wurde am 2026-11-11 gegen die offizielle Doku
   bestätigt (Bearer-Schlüssel, `anthropic-version: 2026-10-21`, kein `temperature`,
   `stop_reason` u. a. `end_turn`, `max_tokens`, `refusal`).
10. **Externe Dienste (Gmail, Google Calendar, Notion)** brauchen vom Owner registrierte
    OAuth-Clients. Ohne sie ist dieser Teil von Etappe 16 nicht baubar.
11. **Beide APK-Artefakte sind gleich:** Das injizierte ABI wirkt in dieser AGP-Version nicht, die
    APK enthält alle vier ABIs (ca. 36 MB). `docs/BUILD.md` beschreibt noch getrennte ABIs.

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

1. Runde auf `main` zusammenführen, dort bauen und prüfen.
2. Docs laut `CLAUDE.md` → „Docs aktuell halten“ im selben Push aktualisieren.
   `release-notes/next.md` durch die Notizen dieses Pushs ersetzen.
3. `python3 -I scripts/docs_sync.py --check` muss grün sein; `main` einmal pushen.
4. Build: `python3 tools/dev.py build` (und `--abi x86_64` für den Emulator). Der Owner bekommt
   die APK aus `artifacts/` samt `.apk.sha256` und installiert sie selbst. Eine Release-Pipeline
   oder einen Download-Link gibt es nicht.

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
  - Noch nicht ausgewertet: Start mit `-qemu -accel tcg,thread=multi` (ca. 150 % Host-CPU statt
    100 %); der Boot war nach über 9 Minuten nicht fertig, als pausiert wurde. Nächste Ideen:
    nur 1–2 Gast-Kerne, kein paralleler Gradle-Lauf, älteres leichteres Abbild (z. B. API 30
    `default`), nach dem Boot 10 Minuten ruhen lassen.
  - Die Ansichtshierarchie per `uiautomator dump` taugt besser zur Prüfung als Screenshots.
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

- 0.1.0 Debug (Etappe 15): Werkzeuge im Panel; APK nicht mehr test-only; Kern 52/0, Agent-Suite
  198/0, Lint grün; im Emulator installiert, Tippen ungeprüft.
- 0.1.0 Debug (Etappe 14c): Act Builder mit privater Entwurfsprüfung; ARM64- und x86_64-APK,
  Agent-Suite 189/0, Vollregression 370 Tests mit genau fünf Baselinefehlern, Lint grün.
