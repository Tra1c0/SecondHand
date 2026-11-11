# Handoff

Das kurze Gedächtnis des Projekts für den Lead (Begriffe: `CLAUDE.md` → Begriffe). Lies es zu
Beginn jeder Sitzung nach `CLAUDE.md`. Der Lead aktualisiert es am Ende jeder Runde: aktueller
Stand, offene Punkte, neue Sackgassen und wiederholte Fehler, eine Zeile pro Build im Build-Log.
Lösche, was nicht mehr stimmt; die Historie bleibt in git. Code-Fakten und Code-Fallstricke
gehören nach `docs/ARCHITECTURE.md`, nicht hierher.

## Aktueller Stand

<!-- Der Session-Start-Hook gibt diesen Abschnitt aus (bis ca. 10 Zeilen): kurz und aktuell halten. -->
- Pause aufgehoben: Der Owner hat am 2026-11-11 die vollständige Fertigstellung beauftragt.
- Fertig: Etappen 0–14c (Tastatur, Panel/Rad, OpenAI-Aktionen, Skill Keys, Workflows, Act Builder).
- Letzter Build: 0.1.0 Debug, ARM64 und x86_64, Etappe 14c (APKs nicht im Repo).
- Nicht geprüft: kein Gerätetest, keine echte API-Anfrage.
- Laufende Runde: `docs/ROADMAP.md` → Beschlossen von oben nach unten (G1, dann 15–18, A).
- Wartet auf den Owner: Entscheidung über den Branch `main` (Offene Punkte 3).

## Offene Punkte

1. **Gerätetest fehlt komplett.** Es gab nie einen Emulator-Boot, eine Installation oder eine
   Prüfung von Tippen, Löschen, Auswahl, Panel/Rad, Feld- und App-Wechsel, sensiblen Feldern,
   Rotation, TalkBack und Abbruchpfaden. Checkliste: `docs/VALIDATION.md` → Gerätetest vor
   Meilensteinfreigabe; Keystore und Direct Boot: `docs/STORAGE.md` → Prüflimits. Ein separates
   Test-Editor-APK gibt es noch nicht.
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

- **Emulator in einer Cloud-Umgebung ohne `/dev/kvm`:** nur Software-Emulation möglich, sehr
  langsam. Eine parallele Emulator-Installation füllte einen 8-GB-Datenträger; den Download erst
  nach abgeschlossenem Build starten und Archive danach löschen.
- **Emulator-Ablage in `/dev/shm`:** Die vorbereitete Installation (Emulator 37.2.13, Image
  API 35 AOSP-ATD x86_64, AVD `AgentKeyboardApi35`) lag in flüchtigem RAM und in einer anderen
  Umgebung. Sie ist weg. Neu aufbauen; dabei zeigten die SDK-Ordner `emulator` und
  `system-images` per Symlink dorthin.
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

- 0.1.0 Debug (Etappe 14c): Act Builder mit privater Entwurfsprüfung; ARM64- und x86_64-APK,
  Agent-Suite 189/0, Vollregression 370 Tests mit genau fünf Baselinefehlern, Lint grün.
