# Handoff

Das kurze Gedächtnis des Projekts für den Lead (Begriffe: `CLAUDE.md` → Begriffe). Lies es zu
Beginn jeder Sitzung nach `CLAUDE.md`. Der Lead aktualisiert es am Ende jeder Runde: aktueller
Stand, offene Punkte, neue Sackgassen und wiederholte Fehler, eine Zeile pro Build im Build-Log.
Lösche, was nicht mehr stimmt; die Historie bleibt in git. Code-Fakten und Code-Fallstricke
gehören nach `docs/ARCHITECTURE.md`, nicht hierher.

## Aktueller Stand

<!-- Der Session-Start-Hook gibt diesen Abschnitt aus (bis ca. 10 Zeilen): kurz und aktuell halten. -->
- Stand 2026-11-19, Arbeitsbranch `claude/build-12-completion-3unh51` (2205ba7 plus Doku): **73 Emulator-Testlabor** fertig (22 Szenarien
  inkl. Autofill 73c; Lauf 40 dreimal 22/22, Kontrolllauf 43 dreimal alle rot), **74 Ablage**, **75 Buchstabendreher**, **76 Einträge
  in der Sicherung**, Korrekturen an Pille (Wiederkehr, Sternchen im Passwortfeld P308), Update-Chip, Tastenpositionen, Sperrzustand der
  Befehle (P309), Logging im Screenshot-Chip. „SecondHand Tests“ Lauf 140 grün. Nachweise: `docs/VALIDATION.md` (oberste Abschnitte).
- Stand 2026-11-20: **77 Ordner in der Ablage** (bis Build 36 „Prompt-Ablage“; seit 77b „Ablage“, Entscheidung des Owners) fertig, geprüft (Prüfer, Tests Lauf 145, Gerätetests 48–50) und auf `main`; Test-Build 36 beim Owner.
- 77b Umbenennung in „Ablage“ (nur Texte) ist in Test-Build 37 und auf `main`. 78a AICore-Diagnose ist geparkt (`docs/geparkt/`, Owner-Entscheidung).
- **79 Autofill-Vorschläge robust** (Breite nach Leiste, Reihenfolge, keine Chips veralteter Antworten): Prüfer ohne Befund ab mittlerer Schwere; Gerätetest fand eine Regression (dritter Chip fehlte in 1 von 3 Durchgängen), behoben in b26cecb; danach Lauf mit fünf Emulatoren grün (je 23/23), Kontrolllauf mit drei Emulatoren grün (alle rot), „SecondHand Tests“ grün für b26cecb; auf `main`, in keinem Build. Die Korrektur b26cecb hat kein Prüfer gelesen.
- **80 Begleit-App (GenieX-Spike, `companion/`, eigenes Gradle-Projekt, nie veröffentlicht): geparkt (Owner, 2026-11-20)**, ebenso W6 (spezialisierte Sprachengine). Stand und Gründe: `docs/ROADMAP.md` → W4, W6; Messberichte des Owners: `docs/VALIDATION.md` → 80 bis 80f. Die APKs gingen nur als Datei an den Owner (Debug-Schlüssel dieses Containers; in einer neuen Sitzung ist er weg, dann Neuinstallation). Der Owner nennt als Nächstes ein anderes Thema.
- **Runde 3 (Owner, 2026-11-20, Vergleich mit Gboard)**: Etappen 81–87 in `docs/ROADMAP.md` → Beschlossen, Bestandsaufnahme `docs/RUNDE3-BESTANDSAUFNAHME.md`. **81 Symbolseiten wie Gboard** fertig: Prüfer ohne Befund ab mittlerer Schwere, Nacharbeiten erledigt, „SecondHand Tests“ und Gerätetests (drei Emulatoren, je 23/23) grün für f30d90f, auf `main`, in keinem Build; kein neuer Kontrolllauf (Szenarien unverändert). 82 (Aktionen bei markiertem Text) und 83 (gezeichnete Symbole logisch) sind gebaut und in Nacharbeit nach dem Prüfer (Arbeitskopie `wt-select-82`). W7 Leertasten-Animation: nur Entscheidungsvorlage (`docs/LEERTASTE-ANIMATION.md`), nicht beauftragt.
- **Runde 3 abgeschlossen (2026-11-20):** 84 Symbole ohne Verzögerung, 85/86 feste Leiste mit Raster und Anpassen per Ziehen, 87 Cursor als Trackpad (drei Prüfungen auf höchster Stufe; erste fand stilles Löschen mit zwei Fingern, zweite Auto-Repeat eines abgewiesenen Fingers und Pfeiltasten am Feldrand; dritte ohne Befund ab mittlerer Schwere), 79b Autofill ohne leeren Kasten mit Zählern. Stand 97daca8: „SecondHand Tests“ grün, fünf Emulatoren je 25/25, Kontrolllauf mit drei Emulatoren grün (alle rot), auf `main`. Offen je Etappe: `docs/VALIDATION.md` (oberste Abschnitte). Nichts davon auf einem Telefon geprüft; alle Trackpad-Werte sind erste Schätzungen. Autofill kann beim ersten Feld nach einem Start des Tastatur-Prozesses ausbleiben (vermutlich Plattform, unbestätigt).
- **Arbeitsweise (Owner, 2026-11-20):** volle Testläufe auf GitHub statt im Container. Je Etappe ein eigener Zweig `claude/etappe-<nr>-<name>` (kein Force-Push), dort laufen „SecondHand Tests“ und „SecondHand Device Tests“; lokal nur die betroffenen Testklassen. Eingemischt wird weiter erst nach Prüfer und grünen Läufen.
- **Regel des Owners (2026-11-19):** Was nach den Prüfmöglichkeiten des Leads lückenlos belegt ist, darf ohne Rückfrage auf `main`,
  aber leicht rückholbar: vorher den alten Stand von `main` hier notieren. Vor dieser Runde: `main` = 74ed58c; vor Etappe 77: 90723e1; vor 77b (Umbenennung in „Ablage“): 2301f2d; vor 79 (Autofill) und dem Quelltext der Begleit-App: 3d123b8; vor 81 (Symbolseiten): c414ab0; vor 82/83: 268d71e; vor 84: 3455b72; vor 85–87 und 79b: c6e13ff; vor 87c (Trackpad: Startrichtung entscheidet): c038698.
- **Nach Build 41 (Owner-Rückmeldung, 2026-11-20):** Trackpad startete beim Owner nie (seine ↑/↓ sind anders belegt) → **87c** (jede auf „Cursor bewegen“ gelegte Richtung startet den freien Cursor, die Startrichtung entscheidet; vier Prüfrunden, zuletzt ohne Befund; „SecondHand Tests“ 44818030695 und Gerätetests 44818030717 grün für e429bad), auf `main`, in keinem Build. In Arbeit auf eigenen Zweigen: **87b** Diagnose-Karte Trackpad (`claude/etappe-87b-diagnose`), **88** Leiste v2 (`claude/etappe-88-leiste`, Konzept `docs/LEISTE-KONZEPT.md`). Danach Test-Build an den Owner.
- **Gerätetests, ungeklärt:** `clipboard_pill` (einmal zusätzlich `prompt_folders`) scheiterte in zwei Erstversuchen (Läufe 44802691345, 44813914900), die Wiederholung war grün; in sechs weiteren Versuchen nicht reproduziert. Die Einstellungsseite, die dabei vorne lag, öffnet das Szenario selbst (`_open_settings_and_switch_on`); vermutet wird ein Rennen zwischen Home und dem Start der Test-App, belegt ist es nicht. `devicetest.py` schreibt seither `INFO foreground …` und `INFO recent activity starts …` (ohne Feldinhalte): beim nächsten Fehlschlag diese Zeilen lesen, erst dann den Testaufbau ändern; die Zeile „after Home“ wieder entfernen, sobald die Ursache feststeht.
- Offen: Autofill-Schwächen der Tastatur (nur gemeldet), rückwirkende Bestandsprüfung (nur Block Datenschutz erledigt; Bedienung und
  Funktionen fehlen), kleine Datenschutz-Befunde und die Wünsche W1–W3 (`docs/ROADMAP.md` → Unsortiert; für W2 fehlt das Telefonmodell
  des Owners), `autocorrect_typo_in_sentence` einmal von zwölf Durchgängen rot (ungeklärt). Kein Gerätetest auf einem Telefon.
- Davor, Stand 2026-11-18: Build 33 ist als signiertes Update veröffentlicht. Er ergänzt Build 32 um
  satzbezogene Autokorrektur für regelmäßige deutsche Verben (72) mit Rücknahme im aktuellen
  Feld und Schalter unter „Tippen & Korrektur → Regeln“. Der vollständige GitHub-Lauf 103 ist grün.
  Offen sind der Gerätetest mit dem echten Wörterbuch und echte KI-Aufrufe.
- Beim Owner: Build 29, die Test-Builds 30 und 31 (frühere Fassungen der Slang-Liste) und Build 32. Erbeten: Slang tippen („gecovert“, „lowkey“,
  „neee“ bleiben; „idr“ wird entgegen der früheren Angabe NICHT zu „dir“, Befund aus 73b), Tempo (Diagnose → Karte „Tippen“, ein Screenshot), `@ki` über 6 Zeilen mit Gemini, Instagram-Privatchat.
- Runde 2 ist angehalten (Owner, 2026-11-17: „alles, was nicht zwangsläufig gefixt werden muss, erstmal außen vor lassen“), `docs/ROADMAP.md` →
  „Runde 2 nach dem Scan“, Befunde in `docs/PRUEFBERICHT-2026-11-16.md`. Nicht gebaut: 64c, 64d, 66, 67, 68; die angefangene Arbeit liegt nur als
  lokale Commits in `.claude/worktrees/…` (Offene Punkte).
- Owner-Fragen offen: Zusatzwortschatz (Spiele/Technik/Musik, etwa 450 Formen) als Vorschläge; „meh“/„ish“ lieber korrigieren (Sprache je Wort);
  Code-Verkleinerung R8 als eigener Test-Build; Satz in `CLAUDE.md` zur Update-Prüfung; Logo-Alternative.

## Offene Punkte

### Runde 2 (angehalten am 2026-11-17)

- Die mittleren Prüferbefunde zu 64/65/69/70 sind am 2026-11-18 behoben (`docs/VALIDATION.md` →
  „Nachprüfung 64, 65 und 70: Befunde behoben“): Zwischenspeicher mit Clip-Text, `PointerTracker`
  (zweiter Finger), Start-Lesen der Zwischenablage, Text des Sicherungsdialogs, Nacharbeiten nach
  gescheitertem Rückweg. Offen davon:
  - mittel, bewusst verschoben: Wiederherstellen: scheitert nach einem Erfolg das Aufräumen
    (`discardCopies`) und dann noch ein zweiter Schritt, bleibt eine halbe Kopie `…2`, die der nächste
    Versuch über die frischen Daten zurückholt. Beheben: Markerdatei in der Kopie, nur Kopien mit
    Marker zurückholen. Sehr unwahrscheinlich (zwei Fehler hintereinander).
  - niedrig: `TypingPerf` rechnet Feldstart-Arbeit dem letzten Tastendruck zu und zählt nicht alle
    Anfragen an die App (`getExtractedText`, `getCursorCapsMode`, `requestCursorUpdates`): die Karte
    „Tippen“ bis dahin nicht als Messung lesen. `LockState`: theoretisches Rennen nach `drop()` auf
    einem zweiten Thread (Generationszähler). `UpdateOnOpen`: Sperre vor dem Senden auf dem Worker
    nicht noch einmal geprüft. `CharArtView.startLive`: `liveRunning` ohne Fangnetz.
  - niedrig: `docs/ANLEITUNG.md` hat nach dem Zusammenführen zwei Absätze „Sichern“ hintereinander
    (der zweite ist der alte) und einen doppelten Satz unter „Über“; „Schriftgröße“ liegt unter
    „Schrift & Emoji“, nicht „Größe & Form“. Screenshot-Kandidaten werden bei `mayKeep = false` bis zu
    drei Minuten nachgereicht. Fehlende Tests für `discardCopies`-Fehlschlag und die Meldungswahl.
- Etappe 71 (Slang-Liste aus dem Datenpaket des Owners, `docs/referenzen/datensaetze/AUSWAHL.md`) ist in Build 32, mit Tests und zwei
  Nachprüfungen, ohne Gerätetest. Offen (niedrig): Die Sprache je Wort bleibt ungenutzt (ein englisches Wort schützt auch auf der deutschen
  Tastatur), das Testfixture fängt Verluste nur über die Mindestzahl 40, die Liste gilt nicht beim Gestentippen.
- Engine-Zeilen: Ein Prüfer hat am 2026-11-18 alle geänderten Engine-Dateien gegen `main` gelesen (`PointerTracker`, `RichInputConnection`,
  `LatinIME`, `Suggest.kt`): keine hohen oder mittleren Befunde. Testlücken: nichts prüft, dass die sechs `TypingPerf.app()`-Zeilen die richtigen
  Aufrufe zählen; kein Test für `cancelKeyTracking` bei zwei Fingern, die beide wischen dürfen; `LatinIME.getSelectedEmoji` hat keinen Aufrufer
  mehr (toter Upstream-Code). Der Sicherungsdialog zeigt in 41 anderen Sprachen weiter den kurzen Upstream-Text.
- Angefangene, nicht zusammengeführte Arbeit liegt nur als lokale Commits in den Arbeitskopien
  dieser Sitzung (`.claude/worktrees/agent-…`, Commit „WIP (stopped by the lead …)“): 64c
  (`a2a77041…`), 66 (`a0ca7bbb…`), 67 (`a7569c81…`), 68 (`a50d6dc5…`), Korrekturen zu den Befunden
  oben (`a94b3c46…`). Ungetestet und unvollständig; ist der Container neu, sind sie weg, dann die
  Etappen aus `docs/ROADMAP.md` und dem Prüfbericht neu beauftragen. Die Aufträge standen im
  Arbeitsordner der Sitzung, nicht im Repository.
- Für Etappe 67 beschlossen und in der ROADMAP notiert: Ausgabegrenze je Modell merken, wenn ein
  Anbieter die Anfrage wegen zu vieler Ausgabe-Token ablehnt (kein automatisches zweites Senden).
- Aus der Abschlussprüfung von Runde 1 offen (niedrig): Enter in einer `@ki`-Frage setzt im Zustand
  PHANTOM ein Leerzeichen vor den Umbruch; ein Editor, der Composing-Text gar nicht annimmt,
  verliert in einem erzwungenen Feld Zeichen (Idee: Erzwingen je Feld aufgeben, wenn die App den
  Text mehrfach nicht annimmt).

0. **Nächste Runde.** Zuerst Rückmeldung zu Build 22/23. Fallstricke belegt bis P239, frei ab
   P240. Offen niedrig: 40 Telefonnummern in ES/US nur mit +/00, PrivateRanges höchstens 3 Werte,
   `RgbOwnPatternTest.ownPatternsStayWithinTheBudget` misst Zeit und fällt unter paralleler Last
   (allein grün, Verhältnis ≈ 1,7 von erlaubten 2). Davor offen: Rückmeldung zu Build 20/21 und die Owner-Fragen: Großschreibung in
   Feldern mit „jedes Wort groß“ (48), Kürzel-Chip als einzige Ersetzung (46), Passwortfeld-Kopien
   nirgends gespeichert (44), Annahmen 41/42/45/46/49/50 in der ROADMAP. Offen niedrig: Kürzel-Chip
   prüft private Werte nicht (46 #5), Restrisiken P185, Gerätetests (Zwischenablage leeren auf
   Android 16, Direct Boot, VACUUM). Fallstricke belegt bis P190, frei ab P191. Arbeitsweise dieser
   Runde: höchstens drei Agenten mit Gradle gleichzeitig (`--max-workers=1 -Xmx1536m`); Agenten auf
   alter Basis zusammenführen ist der teuerste Teil, Merge-Helfer des Leads ist im Scratchpad.
   Davor offen von Build 18/19: Einstellungen am Gerät (alle sieben
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
   Secret `RELEASES_TOKEN` angelegt (Owner, 2026-11-14, für `Tra1c0/SecondHandAgenticKeyboard`; wirkt ab dem nächsten Build aus `main`, noch nicht beobachtet). Entschieden (Owner, 2026-11-14, Etappe 44):
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
   dem Arbeitsbranch, kompiliert und die Agent-Tests grün, den Workflow „SecondHand APK“
   (`agent-apk.yml`) auf dem Arbeitsbranch starten und die APK als Datei an den Owner schicken
   (`https://github.com/Tra1c0/SecondHand/releases/download/build-<N>/SecondHand-build-<N>.apk`
   lässt sich über den Proxy laden; Prüfsumme, Zertifikat und Versionscode danach lokal ansehen).
   Dem Owner sagen, was noch ungeprüft ist, und bekannte Schwächen in die Notizen schreiben. Dann
   Prüfer, volle Tests und Korrekturen; danach die Schritte unten und ein zweiter Build.
1. Runde auf dem Arbeitsbranch fertig prüfen; `release-notes/next.md` durch die Notizen dieses
   Builds ersetzen; Docs laut `CLAUDE.md` → „Docs aktuell halten“; `python3 -I
   scripts/docs_sync.py --check` grün.
2. Pushen und den GitHub-Lauf „SecondHand Tests“ grün abwarten (vier Jobs). Danach nur noch Dateien
   außerhalb der Pfadliste von `agent-tests.yml` ändern (reine Docs, nicht `docs/BUILD.md`): Der
   Release-Workflow verlangt auf `main` einen grünen Lauf für genau diesen Stand der getesteten
   Dateien.
3. `main` vorspulen (`git push origin HEAD:main`, nur als Fast-Forward) und den Workflow
   „SecondHand APK“ auf `main` starten. Er prüft die APK, legt das private Vorab-Release an,
   verlangt den Test-Nachweis und `RELEASES_TOKEN`, veröffentlicht im öffentlichen Repository und
   liest von dort zurück (`docs/BUILD.md` → Prüfungen des Workflows, mit „Was tun, wenn …“).
4. Die APK laden, prüfen und dem Owner schicken; Zeile im Build-Log.

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
  - Seit 73a gibt es den Emulator auf GitHub (`device-tests.yml`, KVM, `docs/BUILD.md` → Gerätetests im Emulator); lokal
    weiter nicht. Was dabei schiefging und wie das Skript die Tasten findet: `docs/ARCHITECTURE.md` → P294.
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
- **`RgbOwnPatternTest.ownPatternsStayWithinTheBudget` (Zeitmessung)** schlug am 2026-11-18 in zwei von vier GitHub-Läufen fehl (Lauf 98 erster
  Versuch, Lauf 99), allein lokal liegt er beim 1,3- bis 1,5-fachen der INK-Zeit gegen erlaubte 2. Er verdeckt die App-Agent-Tests desselben Jobs
  (`core-tests && agent-tests`; die Zusammenfassung sagt dann „TOO FEW TESTS: 638“ und nennt ihn als „NEW“). Erst „Re-run failed jobs“
  (`rerun_failed_jobs`), nie die Grenze lockern. Die Messung wärmt jetzt 400 Bilder auf und nimmt das Beste aus neun Läufen (Lauf 100 grün);
  scheitert er weiter, die Ursache (vermutet: JIT auf einem ausgelasteten Rechner) neu untersuchen, nicht die Grenze. Den Fehlschlag zeigt das
  Ende des Job-Logs (`get_job_logs` mit `tail_lines` um 80; der Blob-Download des Logs ist hinter dem Proxy gesperrt, 403).
- **Lokaler Gradle-Lauf scheitert mit „Could not resolve org.ow2.asm“ oder „Could not generate test report“:** `tools/dev.py` setzt
  `GRADLE_USER_HOME` auf `.toolchains/gradle-user`, wo das Mirror-Skript fehlt. Gezielte Läufe mit `./gradlew`, `JAVA_HOME=…/.toolchains/jdk-17`,
  `ANDROID_HOME`, `LC_ALL=C.UTF-8` und ohne `GRADLE_USER_HOME` (Standard `~/.gradle`). Ein Test, der eine Datei außerhalb des Moduls liest (z. B.
  `InformalWordsTest` die Asset-Liste), ist für Gradle „UP-TO-DATE“, wenn sich nur diese Datei ändert: davor `:agent-core:cleanTest`, sonst
  ist ein „rot ohne die Änderung“-Nachweis leer.

## Wiederholte Fehler (Prozess; Code-Fallstricke: ARCHITECTURE.md → Fallstricke)

- Docs liefen dem Code hinterher: `docs/WORKFLOWS.md` nannte nach Etappe 14 noch 13e als
  aktuellen Stand, die README nannte nur zwei der sechs KI-Aktionen (beides korrigiert). Regel: Docs im selben Push wie den
  Code aktualisieren (`CLAUDE.md` → Docs aktuell halten).
- Zwei Gradle-Läufe im selben Checkout (volle Suite und ein gezielter Lauf) überschreiben einander
  die Testergebnisse; die volle Suite meldete FAILED ohne lesbaren Grund, allein wiederholt grün
  (2026-11-15). Gezielte Läufe nur in einer eigenen Arbeitskopie oder nach der Suite. Lokal grün
  heißt nicht GitHub grün: Lint und ein Zeitbudget-Test fielen erst dort auf (Lauf 65); vor `main`
  immer den GitHub-Lauf abwarten.
- `release-notes/next.md` wird in die APK eingebettet und von `UpdatePromptsTest` gelesen, startete
  aber keinen GitHub-Lauf: Die gekürzten Notizen für Build 25 ließen
  `whatsNewDialogComesOnceThenTheAvailableOne` (brauchte mehr als 6 Blöcke) auf `main` rot werden,
  unbemerkt bis Etappe 56. Behoben: Der Test nimmt eigene lange Notizen (`notesForTest`), und
  `release-notes/**` startet den Lauf.

- Drei Agenten mit je einem Gradle-Lauf gleichzeitig haben den Container (16 GB) zum Neustart
  gebracht (2026-11-16); alle laufenden Agenten waren weg, ihre Arbeitskopien blieben. Seitdem läuft
  jeder Gradle-Aufruf, auch der des Leads, hinter einer gemeinsamen Sperre (`flock <Datei>
  ./gradlew …`), und jeder Auftrag an einen Agenten nennt sie. Die Läufe warten dann aufeinander:
  volle Suiten des Leads zuerst einplanen.
- Das Nutzungslimit der Agenten (5 Stunden) hält alle laufenden Agenten mitten in der Arbeit an
  (2026-11-17). Nichts geht verloren, was committet ist: im Auftrag verlangen, vor dem Bericht zu
  committen, und nach dem Zurücksetzen jeden Agenten mit einer Nachricht fortsetzen statt neu zu
  starten.
- Agenten geben ab, sobald sie nur noch auf ihren eigenen Gradle-Lauf warten, mit uncommitteter
  Arbeit. Im Auftrag ausdrücklich sagen: erst committen, dann berichten; während des Wartens an den
  Docs arbeiten.
- Eine Datei, die ein laufender Test liest (`release-notes/next.md`), während des Laufs im selben
  Checkout zu ändern, verfälscht den Lauf. Solche Änderungen als Patch beiseitelegen.
- Jeder Push mit Änderungen an getesteten Pfaden bricht den laufenden GitHub-Lauf des Branches ab.
  Vor einem Release den letzten Lauf abwarten und danach nur noch reine Docs pushen.

- Gerätetests (73a, 2026-11-19): elf GitHub-Läufe bis zum ersten Grün, alle wegen ungeprüfter Annahmen des Leads über `adb` und
  die Emulator-Action (eine Version aus einer unsortierten Tag-Liste, eine erfundene `am`-Option, `am force-stop` auf die App mit
  dem Hilfsdienst). Vor einem Lauf jede `adb`-Option und jede Action-Version nachschlagen, `git ls-remote --tags … | sort -V`.
  Logs nie groß in den Kontext holen: ein Hilfs-Agent (haiku) holt `get_job_logs` und gibt nur die `INFO`/`RESULT`/`SUMMARY`-Zeilen
  zurück. Ein Bericht eines Agenten „alle Tests grün“ stimmte einmal nicht mit den Ergebnisdateien überein (sie zeigten den letzten
  Rot-Beweis): im Auftrag verlangen, dass der letzte Lauf auf dem Endstand läuft, und die XML-Dateien selbst lesen.
- Der Container startete am 2026-11-19 zweimal neu, während nur ein einzelner Gradle-Lauf eines Agenten lief (Ursache unbekannt,
  Speicher war frei). Uncommittete Arbeit überlebte auf der Platte, Hintergrundaufgaben nicht: Agenten nach jedem Schritt committen
  lassen und nach einem Neustart mit einer Nachricht fortsetzen.
- Lokales Gradle-Skript für den Spiegel (`~/.gradle/init.d/mirror.gradle`): in `projectsLoaded { rootProject.allprojects { repositories { maven { … } } } }`
  eintragen (so steht er vor den Repositories der Build-Skripte), nicht am Projektnamen festmachen (eine Arbeitskopie heißt wie ihr
  Ordner) und für `tools/devicetest` auslassen (`rootProject.name != 'secondhand-devicetest'`, dessen Einstellungen verbieten
  Projekt-Repositories). Einem Agenten wurde das Anlegen verweigert; der Owner hat es dem Lead am 2026-11-19 erlaubt.

## Build-Log (eine Zeile pro Build, neueste zuerst)
- Build 41 (Test, Arbeitsbranch und `main` 97daca8): Runde 3 vollständig (84, 85/86, 87, 79b; dazu 81–83 aus Build 38). Tests, fünf Emulatoren und Kontrolllauf grün; APK nachgeprüft (Zertifikat des Owners, Versionscode 41), dem Owner geschickt. Build 39 (c4c701e: 84, 85/86) ging vorher an den Owner; Nummer 40 wurde gebaut, aber nicht verschickt (eine Zeile Absturzschutz fehlte). Kein öffentliches Update, kein Test auf einem Telefon.
- Build 38 (Test, Arbeitsbranch und `main` b26826b): 79 Autofill robust, 81 Symbolseiten wie Gboard, 82 Aktionen bei markiertem Text, 83 gezeichnete Symbole logisch (dazu 77b „Ablage“ aus Build 37). Je Etappe ein Prüfer, bei 82/83 zwei; kein Befund ab mittlerer Schwere offen. GitHub „SecondHand Tests“ grün für b26826b, Gerätetests ein Emulator 23/23 (für f30d90f drei Emulatoren); 82 und 83 haben kein Emulator-Szenario. APK nachgeprüft (Zertifikat des Owners, Versionscode 38), dem Owner geschickt. Kein öffentliches Update, kein Test auf einem Telefon.
- Build 36 (Test, Arbeitsbranch 0add48d): 77 Ordner in der Prompt-Ablage mit den Prüferkorrekturen. GitHub „SecondHand Tests“ Lauf 142 grün; APK nachgeprüft (SHA-256 e458917e…00b410cb, Zertifikat des Owners, Versionscode 36), dem Owner geschickt. Danach auf a689d7f: Gerätetests Lauf 48 (23/23), Lauf 49 (dreimal grün), Kontrolllauf 50 (dreimal alle rot), Tests Lauf 145 grün; `main` auf diesen Stand vorgespult (vorher 90723e1). Kein öffentliches Update, kein Test auf einem Telefon.
- Build 35 (Arbeitsbranch und `main` edfe3d0, Release-Lauf 35 vom Arbeitsbranch): 73 Emulator-Testlabor mit 22 Szenarien inkl. Autofill, 74, 75, 76, Korrekturen an Pille (P307, P308), Update-Chip, Tastenpositionen, Sperrzustand der Befehle (P309), Logging im Screenshot-Chip (P310). GitHub „SecondHand Tests“ Lauf 140 und auf `main` Lauf 141 grün; Gerätetests Lauf 40 (dreimal 22/22), Kontrolllauf 43 (dreimal alle rot), auf `main` Lauf 44 grün. APK nachgeprüft: SHA-256 d49894d7…689dafe3, Zertifikat des Owners d56e8f00…63602a, Versionscode 35. Dem Owner geschickt. Der Release-Workflow auf `main` (öffentliches Update) wurde nicht gestartet; kein Test auf einem Telefon.
- Build 34 (Test, Arbeitsbranch c6526bf): 74 Prompt-Ablage (Symbol im Komma-Menü, Fenster, Einstellungsseite) und 73a Emulator-Testlabor
  (ändert die App nicht). GitHub-Lauf 115 grün (Agent, Upstream, Lint, Build), Gerätetests Lauf 14 grün; APK nachgeprüft (Prüfsumme,
  Zertifikat, Versionscode 34, nicht debuggable); dem Owner als Datei geschickt; kein Gerätetest. Nicht auf `main`.
- Build 33 (geprüft, von `main` d051fe8) ergänzt die satzbezogene Autokorrektur (72). GitHub-Lauf 103 grün (Agent, Upstream, Lint, Build). Release-Lauf 33 grün (Signatur, Versionscode und öffentlicher Download geprüft). Die Geräteprüfung steht aus.
- Build 32 (geprüft, von `main` 7ada405): Runde 2 teilweise (64a, 64b, 65, 69, 70) mit allen Prüferkorrekturen und Etappe 71 (Slang-Liste,
  1.695 Wörter, zwei Nachprüfungen); am Ende zwei Prüfer über den ganzen Branch (Engine-Zeilen; Zwischenablage und Sicherung), nur niedrige Befunde,
  behoben; lokal 686 Tests der berührten Klassen und der Kern-Suite grün; GitHub-Lauf 100 grün (Agent, Upstream, Lint, Build); Release-Lauf 32 auf `main` grün (Signatur und Zertifikat des Owners, Versionscode 32, Test-Nachweis, öffentliches Release, Zurücklesen); APK nachgeprüft (Prüfsumme, Zertifikat, nicht debuggable, Slang-Liste 1.695 Zeilen); kein Gerätetest.
- Build 31 (Test, Arbeitsbranch 0d5d36b): 71 mit 1.750 Wörtern (nach der ersten Nachprüfung, vor der zweiten).
- Build 30 (Test, Arbeitsbranch 575f66a): Arbeitsstand von Runde 2 und 71 mit 2.138 Wörtern (vor den Nachprüfungen der Liste).
- Build 29 (geprüft, von `main`): Runde 1 (57 bis 63) mit allen Prüferkorrekturen; lokal 630 Kern-
  und 1187 App-Tests grün (Code-Stand c6e5299; das lokale Lint lief wegen des Zeitlimits nicht mehr),
  GitHub-Lauf 90 grün (Agent, Upstream, Lint, Build); erster Lauf der `main`-Schritte des neuen Release-Workflows (Test-Nachweis,
  öffentliches Release, Zurücklesen); kein Gerätetest.
- Build 28 (Test, Arbeitsbranch c64f0d7): Runde 1 vor der zweiten Korrekturrunde (Enter in `@ki`
  konnte senden; abgelehnter Composing-Text konnte Buchstaben kosten); erster echter Lauf des neuen
  Release-Workflows.
- Build 27 (geprüft, von `main`): 56 „Neue Builds sofort melden“ mit Prüfer und Korrekturen; volle
  Agent-Suite lokal grün (1131 App auf bcc46ad, danach gezielt 591 Kern und 46 App auf 42248cd),
  GitHub-Lauf 75 grün (Agent, Upstream, Lint); kein Gerätetest.
- Build 26 (Test, Arbeitsbranch cdb676c): 56 mit allen Prüferkorrekturen.
- Build 25 (geprüft, von `main`): 55 Diagnose „Letztes Feld“ mit Prüfer und Nachprüfung; Agent-Suite
  lokal grün (586 Kern, 1123 App), GitHub-Lauf 72 grün (Agent, Upstream, Lint); kein Gerätetest.
