# SecondHand – Code-Landkarte

Die Code-Landkarte für alle, die Code ändern: wo jeder Teil liegt, wie die Teile zusammenhängen
und was schon einmal kaputtging. Lies nur den Abschnitt des Bereichs, den du änderst. Seine
Startzeile steht in der Ausgabe beim Sitzungsstart, alternativ:
`python3 -I scripts/docs_sync.py --toc docs/ARCHITECTURE.md`.

Regeln für diese Datei: Jede Datei, die `map.sources` in `scripts/docs_sync.json` erfasst, wird
hier genannt, per Dateiname, Stamm oder Glob in Backticks wie `Workflow*.kt`. Sonst schlägt
`--check` fehl. Fakten stehen nach Bereich sortiert, nie nach Datum (die Historie liegt in git
und in `docs/VALIDATION.md`). Jeder `## `-Abschnitt beginnt mit einer `> `-Zeile, die als Eintrag
ins Inhaltsverzeichnis kommt. Blöcke zwischen `<!-- gen:… -->` und `<!-- /gen:… -->` werden
erzeugt: nicht bearbeiten, sondern `python3 -I scripts/docs_sync.py --write` ausführen.

Kurzformen (auch als `aliases` in der Konfiguration):
`A/` = `app/src/main/java/helium314/keyboard/agent/`,
`K/` = `app/src/main/java/helium314/keyboard/`,
`C/` = `agent-core/src/main/kotlin/dev/agentkeyboard/core/`,
`AT/` = `app/src/test/java/helium314/keyboard/agent/`,
`CT/` = `agent-core/src/test/kotlin/dev/agentkeyboard/core/`.

## Inhalt

<!-- gen:toc -->
- **Grundlagen** – Stack, Module, Einstiegspunkte und die Grundentscheidungen, die für jeden Bereich gelten.
- **Kern (`agent-core`)** – Reine Kotlin-Datentypen und Regeln: Editor-Snapshots, Policy, Aktionen, Workflows, Skills.
- **Geänderte Upstream-Dateien** – Jede Upstream-Datei, die der Fork ändert, mit dem Grund. Alle übrigen Upstream-Dateien sind unverändert.
- **Editorzugriff, Panel und Rad** – Wie die Tastatur Auswahl liest, Aktionen zeigt und Ergebnisse übernimmt.
- **Speicher, Zugangsdaten und Datenschutz** – CE/DE-Trennung, verschlüsselte Schlüssel, Dateifreigaben. Details und Prüflimits: docs/STORAGE.md.
- **KI-Anbindung** – Drei Cloud-Provider (OpenAI, Anthropic, Gemini) über einen gemeinsamen Transport. Verträge: docs/PROVIDERS.md.
- **Laufjournal** – Verschlüsselte Metadaten zu Läufen: Absicht vor Wirkung, kein Retry, keine Inhalte.
- **Workflows und Skill Keys** – Gespeicherte Abläufe, ihre Ausführung nach Freigabe und die drei Skill-Tasten. Format: docs/WORKFLOWS.md.
- **Werkzeuge** – Fest eingebaute Werkzeuge: lokales Testwerkzeug und Übergabe eines Kalenderentwurfs an eine Kalender-App.
- **Befehle** – Getippte Befehle wie `@math`: Erkennung vor der Schreibmarke, Chips in der Leiste, Ausführung erst nach Tipp.
- **Act Builder** – Natürliche Beschreibung → freigegebene Anfrage → streng validierter Workflow-Entwurf.
- **Einstellungen und Profile** – Compose-Seiten der Agent-Funktionen sowie Design-, Layout- und Gestenprofile.
- **Flacher Stil** – Tastenstil „Flat“, Farbsatz „Flach schwarz“ und die Vorlage, die beides mit Größen einstellt.
- **Feste Leiste** – Die Leiste wie Gboard (Etappen 85, 86 und 88): feste Plätze für die Werkzeuge, Raster aller Werkzeuge, Anpassen in der Tastatur mit eigener Berührungsverarbeitung. Das Konzept steht in `docs/LEISTE-KONZEPT.md`.
- **RGB-Beleuchtung** – Animierte Farben für Tasten, Beschriftung, Hintergrund und Leiste (Etappe 26, überarbeitet in 29): Rechnen im Kern, Zeichnen über einen Painter.
- **Diktat** – Diktieren über den On-device-Erkenner des Systems: zuhören, Vorschau, Einfügen erst nach eigenem Klick.
- **Lerndaten** – Was die Tastatur aus dem Tippen lernt: pausieren, zählen, exportieren, löschen. Alles auf dem Gerät.
- **Updates** – Prüfung auf neue Builds im öffentlichen Release-Repository (auf Tipp, täglich als Option, seit 56 beim Öffnen der Tastatur höchstens alle 15 Minuten, nie mit Getipptem), Download und Installation nur nach eigenem Klick.
- **Unveränderter Upstream** – Was ohne Änderung von HeliBoard übernommen ist; nur nach Ordnern beschrieben.
- **Symbole malen** – Etappe 27: Sonderzeichen auf die Buchstaben malen statt Gestenschreiben, lokal erkannt.
- **Zeichen-Kunst** – Etappe 53 (schließt 39 Text-Stile ein): Fenster in der Tastatur, das Zeichnung, Foto, Text und Rahmen zu Text aus Zeichen macht.
- **Ablage** – Etappe 74: eigene Ablage für Texte für KI-Chats, dauerhaft gespeichert und sortierbar; Einstellungsseite (74a) und Fenster in der Tastatur über das Komma-Popup (74b).
- **Begleit-App (Etappe 80)** – Etappe 80: kleine, getrennte App `companion/` nur für das Gerät des Owners; der Spike lädt Qualcomms fertiges Qwen3-4B und lässt es auf dem KI-Chip einen festen Satz korrigieren. Wird nie veröffentlicht.
- **Build, Tests, CI, Docs-System** – Lokaler Build, Prüfbefehle, Workflows und die Docs-Prüfung.
- **Fallstricke** – Fehler, die schon passiert sind oder nahelagen: bei jedem Review prüfen.
- **Datei-Inventar** – Erzeugte Liste aller kartierten Dateien nach Ordner.
<!-- /gen:toc -->

## Grundlagen

> Stack, Module, Einstiegspunkte und die Grundentscheidungen, die für jeden Bereich gelten.

- Fork von HeliBoard `v4.2`, Commit in `upstream.lock`. HeliBoards Eingabefläche,
  Touch-Verarbeitung, Layoutparser, Wörterbücher, Autokorrektur und die native JNI-Engine
  (`app/src/main/jni/`) bleiben erhalten. Neue Funktionen hängen über wenige Adapter an der
  Engine. Es gibt keinen Komplettumbau, die Tastatur bleibt eine View.
- Sprachen: Java und Kotlin. Einstellungen nutzen Jetpack Compose, die Tastatur und ihre
  Overlays sind klassische Views. Toolchain und Versionen stehen in `docs/BUILD.md`.
- Module: `:app` (Android-App mit IME, Einstellungen und Adaptern) und `:agent-core` (reines
  Kotlin/JVM, ohne Android- und Netzwerkabhängigkeit; `agent-core/build.gradle.kts`,
  eingebunden in `settings.gradle`).
  Außerhalb des Builds liegen zwei eigene Gradle-Projekte: die Test-App `tools/devicetest` (73) und die
  Begleit-App `companion/` (80, Abschnitt „Begleit-App“).
- Paketnamen: Die Upstream-Pakete `helium314.keyboard.*` bleiben, damit die native Engine stabil
  bleibt. Eigener App-Code liegt in `helium314.keyboard.agent` (`A/`) und in
  `K/settings/screens/`. Der Kern liegt in `dev.agentkeyboard.core` (`C/`).
- Name (57, Owner 2026-11-16): Die App heißt „SecondHand“ (bis Build 27 „Agent Keyboard“, in jeder
  Variante ohne Zusatz „Lab“). Geändert hat sich nur, was der Nutzer liest: `english_ime_name`, die
  Fork-Texte, die Rechtschreib- und IME-Namen (neue Schlüssel `agent_spell_checker_service_name`
  und `agent_android_spell_checker_settings` in `values{,-de}/agent_name_strings.xml`,
  P241), Workflow-Namen und Release-Titel, der `User-Agent` `SecondHand-Updater`, die Dateinamen der
  APK (zwei, P240) und das Logo (58, P243). Es bleiben: `applicationId` `dev.personal.agentkeyboard`,
  Pakete (`dev.agentkeyboard.core`, `helium314.keyboard.*`), Klassen (`AgentKeyboardFrame` …),
  Einstellungsschlüssel, Dateinamen und Ordner auf dem Gerät, AAD-Texte der Verschlüsselung
  („AgentKeyboard/…“, sonst wären Daten unlesbar), die Dateiformate (Vorschlagsnamen
  beim Speichern heißen seit der Nachprüfung 57 „SecondHand-…“, der Import prüft keinen Dateinamen), die Namen der beiden GitHub-Repositories, der Secrets und das
  Tag-Format `build-<N>`. Upstream-Texte mit „HeliBoard“ (Lizenzzeile „basiert auf HeliBoard“,
  Gestendaten, Firefox-Hinweise der Diagnose, Links) bleiben bewusst.
- Gebaute Variante (62, Owner-Bericht „stellenweise Performance-Probleme“): Die APK, die der Owner
  installiert, ist `debugNoMinify` und seit 62 **nicht mehr debuggable** (`isDebuggable = false` in
  `app/build.gradle.kts`). Name der Variante, Zusatz `.debug` der App-ID, Signatur und Quellordner
  `app/src/debugNoMinify` bleiben (die installierte App und ihre Daten hängen daran); R8 bleibt aus.
  Folge: `BuildConfig.DEBUG` ist false, der Debug-Modus ist standardmäßig aus, Android führt die App
  mit seinen vollen Optimierungen aus (P259). Nichts davon ist auf einem Gerät gemessen.
- Einstiegspunkte: IME-Dienst `LatinIME.java`, Anwendung `App.kt`, Einstellungen
  `SettingsActivity.kt` (alles Upstream-Dateien, siehe „Geänderte Upstream-Dateien“).
- Ablauf jeder Aktion: Aktion starten → Quelltextvorschau → ausdrücklich freigegebener Snapshot →
  Policy-Prüfung → Verarbeitung → Ergebnisvorschau → bewusste Übernahme.
- Vorgesehene Grenzen: `EditorAdapter`, `ModelProvider`, `ExecutionPolicy`, `ProfileStore`,
  `WorkflowRunner`, `RunStore`, `ToolLauncher`, `CredentialStore`. Es wird nur gebaut, was die
  aktuelle Etappe braucht.
- Ein Anbietermodul entsteht erst zusammen mit seinem echten Anschluss.
- Berechtigungen gelten für die ganze APK. Modulgrenzen sind keine Sandbox des Betriebssystems,
  die Policy wird durch Tests belegt.

## Kern (`agent-core`)

> Reine Kotlin-Datentypen und Regeln: Editor-Snapshots, Policy, Aktionen, Workflows, Skills.

- `EditorSession.kt`: `EditorToken` (Besitzer-UUID, Generation, Revision; einmalige Autorität),
  `EditorSession`, `TextSnapshot` (kopierter Text plus Sitzungsgeneration und Änderungsstand),
  `InsertionAnchor` (Schreibmarken-Position mit einmaliger Autorität zum Einfügen, ohne Text).
- `ExecutionPolicy.kt`: `EditorAccess`, `DenialReason`, `CaptureResult`. Prüft Größengrenzen,
  sensible Felder, Inkognito und Sperrzustand vor jedem Lesen und vor jeder Übernahme.
  `resultDenial` prüft auch Namen und Literale aus Dateien. `captureAnchor`/`insertDenial`
  (`AnchorResult`) gelten für das Einfügen an der Schreibmarke: nur ohne Auswahl
  (`DenialReason.NO_CURSOR` sonst), nur bei unverändertem Token und unveränderter Position.
- `FieldReport.kt` (Etappe 55): Modell und Urteil der Diagnose-Karte „Letztes Feld“. Eigene Kopien
  der `InputType`-/`EditorInfo`-Zahlen (geprüft gegen die Plattform in `AT/LastFieldTest`),
  `fieldType`/`caps`/`flags`, `Snapshot` (`Password`, `Hidden(locked)`, `Field` nur mit Paketname,
  `inputType` der Engine und `rawInputType` der App, `imeOptions`, Sprach-Tag, Zeit und Wahrheitswerten
  aus `InputAttributes`/`SettingsValues`), `Strip` (was die Leiste zuletzt zeigte), `Live` (Wörterbuch
  vorhanden/geladen, zuletzt im Befehl, Leiste zuletzt, verdeckte Leisten, Chips), `effective` und
  `verdict` (erster Grund in fester Reihenfolge, P232). Tests: `CT/FieldReportTest.kt`.
- `ApplyOutcome.kt`: Ergebnis einer nativen Ersetzung (inklusive `UNKNOWN`, siehe Fallstrick P3).
- `LocalTextActions.kt`: lokale Textwerkzeuge ohne Netz.
- `C/charart/` (Etappe 53): die Zeichen-Kunst-Engine, reine Funktionen ohne Android; Abschnitt
  „Zeichen-Kunst“.
  <!-- gen:local_actions -->
3 lokale Textwerkzeuge: `UPPERCASE`, `LOWERCASE`, `CLEAN_SPACES`
<!-- /gen:local_actions -->
- `AiTextAction.kt`: `AiTextAction` mit festen Instruktionen. `ANALYZE` erlaubt keine
  Ersetzung. `ASK` und `TRANSLATE_STYLED` gibt es nur über `@ki` bzw. `@translate`. Dazu
  `TranslationLanguage` und `WritingTone`.
- `StyledTranslation.kt`: `@translate`-Argument zerlegen (`parse`: Text im ersten
  Anführungszeichenpaar, Stil = Rest, leer = `natural English`) und die Anfrage bauen (`request`:
  zwei beschriftete Abschnitte „Translation style:“ und „Text to translate:“).
  <!-- gen:ai_actions -->
8 KI-Textaktionen: `CORRECT`, `TRANSLATE`, `REPHRASE`, `SUMMARIZE`, `REPLY`, `ANALYZE`, `ASK`, `TRANSLATE_STYLED`
<!-- /gen:ai_actions -->
  <!-- gen:languages -->
14 Zielsprachen: English, German, French, Spanish, Italian, Portuguese, Ukrainian, Russian, Arabic, Turkish, Chinese, Japanese, Korean, Hindi
<!-- /gen:languages -->
- `ModelBudget.kt` (Etappe 59): Grenzen und Zeitbudgets eines Modellaufrufs an einer Stelle
  (Eingabe 32.768, Ergebnis 65.536, Anweisung 4096, Lesen 120 s, Aufruf 150 s, Workflow 310 s) und
  die reine Funktion `outputTokens(inputChars)` = `clamp(inputChars / 2 + 2048, 8192, 16384)` (Nachprüfung: Untergrenze 8192 für alle drei
  Anbieter, nie mehr als 16384), `thinkingProviderTokens` = dasselbe.
- `ModelProvider.kt`: `ModelProvider`, `ProviderCapabilities`, `ModelOutputFormat`,
  `ModelRequest` (nur Strings, keine Tokens oder `InputConnection`), `ModelResponse`.
- `Workflow.kt`: `WorkflowDefinition` (unveränderlich, versioniert), `WorkflowStep.Local` und
  `WorkflowStep.Ai`, `WorkflowInput` (Auswahl oder `StepResult` eines früheren Schritts),
  `WorkflowRunner`, `WorkflowError`. Grenzen und Format: `docs/WORKFLOWS.md`.
  <!-- gen:providers -->
4 Provider-Kennungen im Workflowformat: `openai`, `claude`, `gemini`, `local`
<!-- /gen:providers -->
  Einen echten Anschluss haben `openai`, `claude` und `gemini` (`CloudProviders.SUPPORTED`).
  `local` wird vor jeder Anfrage abgewiesen (Etappe 18).
- `Tool.kt`: `ToolId` (fest eingebaute Werkzeuge, `external` = Wirkung außerhalb der Tastatur),
  `ToolField`, `ToolRequest` (unveränderlich, nur aus der freigegebenen Auswahl abgeleitet) und
  `Tools` (`prepare` ist rein und deterministisch, `runTest` ist das Testwerkzeug). Kein Modell
  und kein Import kann ein Werkzeug wählen, hinzufügen oder konfigurieren.
  <!-- gen:tools -->
2 Werkzeuge: `test.count`, `calendar.insert`
<!-- /gen:tools -->
- `TextCondition.kt`: deterministische Bedingungen `NON_EMPTY`, `CONTAINS_LITERAL`,
  `LENGTH_AT_LEAST`, optional negiert. Kein Regex, kein Skript.
- `SkillBinding.kt`: `BuiltinAction` (stabile Datei-IDs) und `SkillBinding` (Slot 1–3, Alias,
  Aktion, bei `WORKFLOW` eine kanonische UUID).
- `Profiles.kt`: `ProfileGroup`, `PreferenceValue`, `ConfigurationProfile`, `ProfileStore`.
- `PendingBatchGate.kt`: sperrt Swipe-Batches unter Epoch/Lock, wird in `InputLogicHandler.java`
  benutzt (Fallstrick P5).
- `UpdateReleases.kt`: Regeln der Update-Prüfung (Abschnitt „Updates“): `REPOSITORY`, Tag
  `build-<n>`, Asset-Namen, erwartete Download-URL, `newest`, `isNewer`, `parseChecksum`.
  `ReleaseInfo.body`/`AvailableBuild.notes` (54): Release-Text des Builds.
- `ReleaseNotes.kt` (54): `clean`, `cap` (höchstens `MAX_STORED_BYTES` = 8 KiB UTF-8, Schnitt an
  einer Zeile oder einem Codepunkt) und `blocks` (einfaches Markdown: Überschrift, Listenpunkt,
  Absatz; `**`, `__`, Backticks weg, Links als ihr Text) für die Update-Dialoge.
- `UpdateOnOpenPolicy.kt` (56): Regeln der Prüfung beim Öffnen: `interval(failures)` (15 min,
  je Fehlschlag in Folge doppelt, höchstens 4 h), `due(now, lastAttempt, failures)` (eine Uhr vor
  dem letzten Versuch erlaubt eine Prüfung), `failuresAfter` (Erfolg setzt zurück, höchstens
  `MAX_FAILURES` = 4), `etag` (nur sichtbares ASCII ohne Leerzeichen, höchstens 256 Zeichen).
- `RgbLighting.kt` (Etappe 26, überarbeitet 29): `RgbSettings` (alle Optionen, `encode`/`parse`
  mit feldweiser Begrenzung, nie eine Ausnahme; gemeinsamer Look plus `looks` je `RgbTarget`,
  `forTarget`, `lights`), `RgbLook`, `RgbEngine` (Farbe und Stärke für Punkt, Taste und Zeit:
  Muster × Kurve × Palettentabelle, Tipp-Effekte, Phase mit Tempo-Schub; `base`/`effects` für
  das Raster, `FIELD_KEYS`, `keyFactor`/`keyHighlight`/`overlayStrength`; ohne Allokation je
  Aufruf), `RgbEngines` (eine Engine je verschiedenem Look, eine Uhr), `RgbColors` (ARGB, HSV,
  OKLab/OKLCH, `smoothTable`, Kontrast, `readable`/`label` für mindestens 3:1), `RgbRun.mode`
  (Akku-Regeln), `RgbFramePacer` (Bildrate), `RgbFrameStats` (Selbstmessung der Bildkosten) und
  `RgbPaletteTables` (geteilte Palettentabellen). `RgbCurve.kt` (29d): eigene Kurve, monotone
  kubische Interpolation, Vorlagen (`RgbWaveform`), Text `x:y,…`, Migration von `wave=`.
  `RgbPatterns.kt` (29c): `RgbNoise` (periodisches Wertrauschen), `RgbPatterns.field` (Farbfeld
  der sechs Texturmuster), `RgbOverlay` (Detail-Kacheln und ihre Matrix), `RgbTile`. Details im
  Abschnitt „RGB-Beleuchtung“.
- Eigene RGB-Lichtmuster (Etappe 34, Abschnitt „RGB-Beleuchtung“ → Eigene Muster, P214–P218, P222–P223):
  `RgbOwnPattern.kt` (`RgbGenerator` mit zwölf Formen, `RgbBlend`, `RgbLayerReaction`,
  `RgbPaletteMode`, `RgbStop`, `RgbPatternLayer` mit `normalized`, `RgbOwnPattern` mit `reference`,
  `parseReference`, `fingerprint`, `cleanName`, `fitName` und `from` (Ausgangspunkt aus einem
  eingebauten Look), `RgbPatternLibrary` (zufällige IDs, `match` mit Inhaltsprüfung, `withNewIds`,
  eindeutige Namen, höchstens 24), `RgbOwnRenderer` (kompilierte Form je Engine: Stopp-Tabellen über
  `RgbPaletteTables`, Richtung als Sinus/Kosinus, `sample` ohne Allokation, `tapActive`, `stopTable`,
  `lookup`, `blend`, `hash`)), `RgbPatternJson.kt` (`RgbPatternJson` Export-Datei und gespeicherte
  Liste, `RgbPatternError`, `RgbPatternRead`, `StrictJson` als kleiner strenger JSON-Leser).
- Symbole malen (Etappe 27, Abschnitt „Symbole malen“): `InkRecognizer.kt` (`InkStroke`,
  `InkCloud` mit 32 Punkten, Strichrichtung je Punkt und Zahl der Striche und Punkte (36),
  `InkRecognizer` = `$P` plus Richtungs- und Strukturterm mit ±12° Drehtoleranz (36),
  `InkResult` mit Schwellen, `InkGroup` für mehrstrichige Symbole), `InkSymbols.kt` (40
  eingebaute Symbole als Striche im 100er-Feld, 105 Vorlagen), `OwnInkSymbols.kt` (eigene Symbole:
  Textregeln, Format `v1`, `parse` wirft nie), `PersonalInkSymbols.kt` (36, persönliche Vorlagen),
  `InkTiming.kt` (36, `InkTap` Tipp-Regel, `InkPace` Pause nach Tempo). Tests
  `InkRecognizerTest`, `OwnInkSymbolsTest`, `InkBenchmarkTest` (mit `InkFingerModel`),
  `InkTimingTest`, `PersonalInkSymbolsTest`, `InkLogicTest` (83: „/“ und „\“, kein „%“ für einen Strich, jede Teilzeichnung
  jeder eingebauten Zeichnung ist kein Symbol, das mehr Striche braucht).
- `C/format/` (Etappe 41, Formatwächter, Paket `dev.agentkeyboard.core.format`, P138–P140):
  `FormatGuard.kt` (`FieldKind` EMAIL/PHONE/DECIMAL/TEXT, `FormatHint` mit `replacement` = ganzer
  neuer Feldtext oder null für einen reinen Hinweis, `FormatGuard.check`: höchstens
  `MAX_LENGTH` = 100 Zeichen), `EmailCheck.kt` (Leerzeichen, Domain-Tippfehler gegen `PROVIDERS`
  mit OSA-Abstand, 1 Fehler bis 7 Zeichen, sonst 2, höchstens zwei gleich nahe Treffer; eine TLD
  aus `VALID_TLDS` vergleicht nur mit Anbietern derselben TLD, eine andere nur als Tippfehler
  (Abstand 1) einer Anbieter-TLD (`COMMON_TLDS`), P148; ohne Hinweis bei `KNOWN_DOMAINS`
  (Anbieter plus echte Varianten wie `hotmail.fr`, `online.de`, `mail.com`), bei Domain ohne Punkt
  oder als Anfang eines Anbieters (noch beim Tippen); Punkt am Ende nur nach einem Anbieter;
  fehlendes `@` nur vor einem Anbieter),
  `IbanCheck.kt` (Längen je Land, Modulo 97 ohne große Zahlen, nur volle Länge, Leerzeichen nur
  einzeln zwischen Blöcken, `group`), `PhoneFormat.kt` (libphonenumber, `region` aus Land, sonst
  Sprache, sonst `ZZ` = nur mit Ländervorwahl; Format `INTERNATIONAL`), `DecimalHint.kt` (Komma-
  und Punkt-Sprachen, drei Nachkommastellen gelten als Tausender, nie eine Ersetzung).
  Test `CT/format/FormatGuardTest.kt`.
- `C/clip/ClipGuard.kt` (Etappe 44, Paket `dev.agentkeyboard.core.clip`, P154–P157):
  `ClipGuard.classify(text, markedSensitive, region)` → `ClipClass` (`ClipKind` SECRET, OTP, IBAN,
  PHONE, EMAIL, ADDRESS, LINK, TEXT; `ClipCategory` SECRET/OTP/PERSONAL/NORMAL; `code` = Ziffern
  eines Einmalcodes; `inText` = Code in einer Nachricht, `keepsOutOfHistory` = Geheimnis oder Code
  allein; `SecretReason`; `toString` ohne Text), `mask` (bekanntes Präfix plus die
  letzten vier Zeichen ab 20 Zeichen, sonst `HIDDEN`), `entropy`, `SECRET_MILLIS` = 2 min,
  `OTP_MILLIS` = 10 min. Reihenfolge und Entscheidungen: markiert → SECRET; Block
  `-----BEGIN … PRIVATE KEY-----`; bekannte Schlüssel (`sk-`, `sk-proj-`, `sk-ant-`, `sk_live_`,
  `ghp_`/`gho_`/…, `github_pat_`, `AKIA`/`ASIA`, `xox?-`, `AIza`, `glpat-`, `hf_`, `npm_`,
  Telegram-Bot-Token `8–10 Ziffern:35 Zeichen`, `Bearer` + mindestens 20 Token-Zeichen mit einer
  Ziffer) und JWT überall im Clip bis `MAX_SCAN` = 64 KiB (auch `KEY=sk-…` und Code); danach nur bis
  `MAX_LENGTH` = 4096 Zeichen: nur Ziffern 4–8 (auch 3+3/4+4 mit Leerzeichen oder Strich) = OTP,
  außer einer Jahreszahl 1900–2099; IBAN über `IbanCheck.normalize` (auch „IBAN:“ davor);
  E-Mail (ganzer Clip); Telefon über `PhoneFormat.isValid` (neu, bis 30 Zeichen, kein Datum);
  Link über `UrlDetector.isUrl`; ein einzelnes Token als Geheimnis nur bei starkem Signal:
  20–200 Zeichen aus Base64/URL-Zeichen mit Groß- und Kleinbuchstaben, mindestens 2 Ziffern und
  höchstens 5 Kleinbuchstaben am Stück (ab 32 Zeichen 3 Ziffern, 8 am Stück) und Entropie
  ≥ 3,5 Bit/Zeichen (`TOKEN`), oder 8–32 ASCII-Zeichen mit allen vier Zeichenklassen, einer Ziffer
  direkt an einem Buchstaben, höchstens 5 Kleinbuchstaben am Stück, ohne Dateiendung und nicht nur
  mit `-`/`_`/`.` zwischen Buchstaben oder Ziffern (`separatedName`: „B2B-Shop“, „Win11-Pro“,
  „camelCase2_x“ bleiben normal) (`PASSWORD`); ein Passwort-Wort (Passwort, Kennwort, password,
  passwd, contraseña, WLAN-Schlüssel, auch „WLAN-Passwort“) mit Wert direkt danach (nach „:“/„=“
  ein Wert ab 6 Zeichen mit Ziffer oder Sonderzeichen, nach „:“/„=“ auch nur Buchstaben in Groß-
  und Kleinschreibung, wenn auf der Zeile kein weiteres Wort folgt (zweite Nachprüfung N8:
  „Passwort: bitte beim Admin erfragen“ bleibt Text; dritte Nachprüfung: auch kein einziges,
  „Passwort: Erfragen Sie“ bleibt Text, Komma oder Semikolon danach beenden den Wert); nicht nach „one-time“/„einmal“; ≤ 300
  Zeichen) ist ebenfalls `PASSWORD`; nie Hex (Git-Hashes, Prüfsummen, UUIDs, Farben), Pfade, `@`/`#`-Namen,
  `data:`-URIs, Zahlen, Daten, Wörter, Text mit Leerzeichen, längere Blobs. Code im Satz (≤ 300
  Zeichen, Schlüsselwort de/en/es nur als ganzes Wort mit eigenen Grenzen statt `\b`: Code,
  Komposita aus einer Liste (Bestätigungs-, Sicherheits-, Zugangs-, SMS-, Einmal-, PIN-…code) oder
  „X-Code“ außer QR, Bar, Promo, Rabatt, Gutschein, Post …; código, clave, OTP, passcode, (m)TAN,
  PIN, one-time, Einmalpasswort/-kennwort/-PIN/-TAN; nie „Barcode“, „noch einmal“, „Bestätigung“
  oder „Verifizierung“ allein): der Kandidat direkt nach dem Schlüsselwort, sonst der einzige; Daten,
  Uhrzeiten, Beträge, an Buchstaben klebende Ziffern und gruppierte Nummern (Telefon) zählen
  nicht. Adresse (≤ 200 Zeichen, ≤ 4 Zeilen): PLZ (4–5 Ziffern) plus Ort und Straße mit
  Hausnummer (de/at: -straße, -str., -weg, -gasse, -platz, Ring …; es: Calle, Avenida, Plaza …),
  oder englisch Hausnummer + Street/Road/… plus ZIP. Zweifel bleiben normal: ein übersehenes
  Geheimnis landet im Verlauf, den der Nutzer eingeschaltet hat, ein falsches verlässt ihn nur
  zwei Minuten früher. Test `CT/clip/ClipGuardTest.kt` (Beispielschlüssel aus Stücken
  zusammengesetzt, damit die Datei keinen schlüsselförmigen String enthält).
- `C/clip/PrivateRanges.kt` (zweite Nachprüfung 44/45, P178, P185): wo eingefügte private Texte im
  Editor stehen, ohne Text: je Wert Start, Länge und ein gesalzener Hash der ersten `HEAD` = 32
  Zeichen, höchstens `MAX` = 3 Werte (der älteste fällt). `Editor` (Schreibmarke, Text davor/danach),
  `add`, `beforeEdit(from, to)` (dritte Nachprüfung: prüft vor einer Bearbeitung der Tastatur einen
  Wert, dessen Kopf höchstens `HEAD` Zeichen entfernt ist, und findet ihn nach einer ungemeldeten
  Verschiebung wieder), `edited(removeFrom, removeTo, inserted)` (verschiebt Werte hinter einer
  Bearbeitung ohne Lesen und ohne neuen Hash, kürzt einen angeschnittenen Wert; nur eine
  Bearbeitung im Kopf hasht ihn neu; Einfügen an seinem Ende gehört nicht dazu), `touches(from, to)`
  (überlappt oder grenzt an; prüft den Hash, sucht einen verschobenen Wert per rollendem Hash bis
  `WINDOW` = 1024 Zeichen um seine letzte Stelle, unter `MIN_RELOCATE` = 8 Zeichen nur
  `SHORT_WINDOW` = 64; steht er dort mehrmals, gelten alle Fundstellen als privat (`ambiguous`);
  vergisst ihn nur nach einem vollständigen Lesen ohne Fund; nicht lesbar, zu kurz gelesen oder
  weiter als `READ_LIMIT` = 8192 Zeichen: `true`, nichts vergessen; Schreibmarke unbekannt oder
  Auswahl: `true`), `touchesLearningContext(editor, words)`
  (die letzten `words` Wörter vor der Schreibmarke, höchstens `CONTEXT_LIMIT` = 512 Zeichen),
  `contextLength`. Test `CT/clip/PrivateRangesTest.kt`.
- Smart Paste (Etappe 40, Paket `dev.agentkeyboard.core.clip`, P203–P205): `SmartPaste.kt`
  (`SmartAction`: `OpenMap`, `SavePlace`, `Dial`, `AddContact`, `InsertIban`, `IbanInvalid`, `Track`,
  `CreateEvent`, `WriteMail`, `InsertClean`, `InsertCode`, `inserts`, `insertsPrivately` = nur
  `InsertCode` und `InsertIban`, P206; `toString` ohne Text;
  `SmartPaste.recognize(text, markedSensitive, region, now, language)` = `ClipGuard.classify` plus
  `actions(text, verdict, region, now, language)`: markiert, SECRET, leer oder über `MAX_TEXT` =
  2000 Zeichen → nichts; ADDRESS → `AddressQuery.of` (Nachprüfung 40, B7; interne Klasse in
  `SmartPaste.kt`: Teile nach Zeilen, dann Kommas, eine bloße Hausnummer bleibt bei ihrer Straße;
  Bezeichnungen „…adresse:“/„Anschrift:“ fallen weg, Teile mit Telefon-, Mail- oder Web-Wort, `@`
  oder nur Ziffern auch, Namen, Firmen und Länder ebenso; übrig bleiben nur Straßen- und
  PLZ-Ort-Teile, `place` = true; steht ein Straßen- oder PLZ-Treffer mitten in anderem Text,
  Fließtext: erste Straße (`ClipGuard.STREET_DE/ES/EN`, `internal`) plus erste PLZ mit Ort, `place`
  = false) → Karte, Ort nur mit `place` und bis `Places.MAX_ADDRESS`; PHONE → Wählen und Kontakt
  nur, wenn der Text mit „+“, „00“ oder „0“ beginnt (auch nach „(“), mit `PhoneFormat.e164`
  (Nachprüfung 40, B1), sonst wie TEXT; IBAN → `IbanCheck.normalize` (wie ClipGuard, auch
  „IBAN:“), falsche Prüfziffer `IbanInvalid`, richtige `InsertIban(group)`, schon so geschrieben
  nichts; EMAIL → Mail (ohne `mailto:`); LINK → `LinkCleaner.clean`; OTP nur `inText` → die
  Ziffern; TEXT → `TrackingNumbers.find`, `ClipDates.find`, `LinkCleaner.cleanText`; höchstens
  `MAX_ACTIONS` = 3; `placeName` = erster Teil der Adresse, höchstens `Places.MAX_NAME`, bei
  Gleichstand „ 2“, „ 3“ …). `ClipGuard` selbst sortiert unverändert: „4500012345“ bleibt PHONE
  (Beschriftung „Telefon“ im Verlauf, Feld-Vorschläge 45), nur Smart Paste wählt sie nicht.
  `C/format/PhoneFormat.kt`: `e164(text, region)` (gültig wie `isValid`, sonst null).
  `TrackingNumbers.kt`: `Carrier` (DHL, DEUTSCHE_POST, HERMES, DPD, GLS, UPS) mit fester Seite je
  Dienst, `trackingUrl` nimmt nur 8–35 Großbuchstaben und Ziffern; `Tracking` (Dienst, Nummer,
  `url`, `toString` ohne Nummer); `find` bis `MAX_LENGTH` = 300 Zeichen: Wörter ohne Satzzeichen
  (mit Ziffer und höchstens vier Buchstaben in Großbuchstaben), ein kurzer Clip (≤ 40 Zeichen aus
  Buchstaben, Ziffern, Leerzeichen) auch ohne Leerzeichen; kein Kandidat direkt hinter `LABELLED`
  (Kunden-, Bestell-, Auftrags-, Rechnungs-, Vertrags-, Steuer-, Kontonummer/-Nr., „Bestellung“,
  „Rechnung“, customer/order/invoice number, pedido, factura …, bis 40 Zeichen davor, Nachprüfung
  40, B5); ohne Namen nur UPS `1Z` + 16 mit
  `upsValid`, S10 `XX` + 9 Ziffern + `DE` mit `s10Valid`, DHL `JJD`/`JVGL` + 12–22 Ziffern, DHL
  `00340434` + 12 Ziffern mit `gs1Valid` (SSCC), Hermes `H` + 19 Ziffern; mit dem Namen im Text
  (ganzes Wort, in Textreihenfolge) DHL 12 oder 20, Hermes 14 oder 16, DPD 14, GLS 11–12 Ziffern
  oder 8 Zeichen aus Großbuchstaben und Ziffern, diese nur mit „Track-ID“/„TrackID“ bis 20 Zeichen
  davor und nie in BIC-Form (`[A-Z]{6}[A-Z0-9]{2}`). `LinkCleaner.kt`: `clean` (nur über
  `UrlDetector.isUrl`, auch ohne Schema) und `cleanText` (jede `http(s)://`-Adresse bis zum
  Leerraum, schließende Satzzeichen wie in `UrlDetector.find`, bis `MAX_TEXT` = 4000) entfernen nur
  Query-Parameter aus `NAMES`, mit Präfix `utm_` (ohne Groß-/Kleinschreibung) und `si` nur auf
  youtube.com, youtu.be, spotify.com samt Subdomains; geteilt wird nur an `&`, ein Teil mit `;`
  bleibt ganz (Nachprüfung 40, B6); Reihenfolge, Kodierung, leere Werte, leere Teile („&&“) und das
  Fragment bleiben; null, wenn nichts zu entfernen ist. `ClipDates.kt`: Tor vor `EventParser.parse`
  (bis 200 Zeichen, 4 Zeilen; „Guten Morgen“ fällt weg, ein Punkt hinter Datum, Uhrzeit oder „Uhr“
  am Satzende auch; Öffnungszeiten und Erreichbarkeit (`HOURS`, `WEEKDAY_RANGE`: „Öffnungszeiten“,
  „erreichbar“, „Hotline“, „täglich“, „Mo-Fr“, „Montag bis Freitag“, opening hours, horario …) →
  nichts): Kalenderdatum (mit Jahr, ISO, `/` mit Jahr, `DD.MM.`, `am 3.12.`, Tag +
  ausgeschriebener Monat, englisch Monat in Großschreibung + Tag) genügt; Tageswort oder gar kein
  Datum nur mit Uhrzeit, Uhrzeit ohne Tag nur mit Marke („Uhr“, am/pm, `um`/`at`/`a las`/`gegen`,
  halb/viertel); `D.M` ohne Punkt am Ende (Version, Betrag) ohne `am`/`on`/`el` davor macht den
  ganzen Text zu keinem Datum, ebenso `D.M.YY` mit einstelligem Tag oder Monat ohne solches Wort
  („PHP 8.3.28“) und jede Zahl mit Punkten direkt nach `VERSION_WORDS` (Version, Update, Release,
  v, Firmware, Build, Patch …; Nachprüfung 40, B3); vergangene Tage und Daten ohne Jahr mehr als
  300 Tage voraus nie. Test `CT/clip/SmartPasteTest.kt` (Prüfziffern der Beispiele getrennt
  berechnet).
- `C/SentenceLanguage.kt` (Etappe 60, P249–P253): reine Entscheidung zur Satzsprache: `evidenceOf` (nur Sprache der Korrektur, nur andere Sprache, neutral), `blocksCorrection(onlyL1, onlyL2)` (mindestens ein Wort nur der anderen Sprache, keines nur der eigenen), `MAX_PREVIOUS_WORDS` = 3. Hält keinen Text. Test: `CT/SentenceLanguageTest.kt`.
- `C/GermanVerbAgreement.kt` (72): leitet konkurrierende Formen regelmäßiger deutscher Verben
  aus dem Stamm ab und wertet Personalpronomen und Modalverben im Wortkontext aus.
  `A/GermanVerbLemmas.kt` liest `app/src/main/assets/agent/german_verb_lemmas.txt` einmalig. Die
  Liste bestätigt den Infinitiv, bevor Endungen verglichen werden. `tools/build_german_verb_lemmas.py`
  erzeugt die Liste aus der festgeschriebenen Quelle (siehe `docs/BUILD.md`).
  `Suggest.kt` schützt die getippte Form oder
  bewertet einen vorhandenen Wörterbuchtreffer mit der üblichen Korrekturschwelle. Bei Bedarf
  liefert `InputLogic.java` höchstens 24 Zeichen nach dem Cursor derselben Editorverbindung.
  Ist die Abfrage langsam, pausiert sie kurz. Fehlender rechter Kontext lässt die Rangfolge
  bestehen. `TypedWordFirst.kt` merkt sich ein zurückgenommenes Paar an der Wortposition bis
  zum Wort- oder Feldwechsel. Der Schalter in `AgentAreaTypingScreen.kt` steuert beides über
  `SettingsValues.mContextualAutocorrection`. Tests: `CT/GermanVerbAgreementTest.kt`,
  `SuggestTest.kt`, `InputLogicTest.kt` und `AT/TypedWordFirstTest.kt`.
- `C/SwappedLetters.kt` (Etappe 75, P302–P304): reine Funktionen, kein Wörterbuch. `swaps(word)` (Wörter durch Tausch
  genau eines Paars benachbarter, verschiedener Buchstaben), `minFrequency(language)` (105 Deutsch, 150 Englisch, 130
  sonst) und `candidate(typed, language, typedIsKnown, frequencyOf)`: nur 3 bis 5 Buchstaben, nur Buchstaben, kein
  Großbuchstabe nach dem ersten, `typedIsKnown` wird erst danach gefragt, das Ergebnis nur bei genau einem
  Tausch mit Häufigkeit ≥ Schwelle. Aufruf: `Suggest.applySwappedLetters` (nach `applyLearnedCorrection`, vor
  `addFromMemory`). Sie läuft nur bei Tipp-Eingabe (kein Gestentippen, das hat seinen eigenen Weg), laufendem Wort
  ohne Ziffern, eingeschalteter Korrektur und dem Schalter `SettingsValues.mSwappedLettersCorrection`
  (`AgentPreferences.AUTOCORRECT_SWAPPED_LETTERS`, Standard an, Schalter „Buchstabendreher“ in
  `AgentAreaTypingScreen.kt`, Texte `agent_typing_strings.xml`). Sie tut nichts, wenn die erste Engine-Zeile eine
  Kurzform oder ein Whitelist-Eintrag oder die getippte Form in anderer Schreibweise ist, wenn die Engine eine eigene
  andere Korrektur hat, bei einem eigenen Wort, einem Slang-Wort, einem behaltenen Fremdwort, einem verworfenen Paar
  und bei der Verb-Bevorzugung (72); ein gelerntes Paar (20b) steht davor. Fehlt das Wort in den Engine-Ergebnissen, legt
  `Suggest` selbst eine Korrektur der Quelle `SWAPPED_LETTERS_DICTIONARY` an, wie bei einem gelernten Paar, damit
  Rücktaste und Verwerfen sie wie jede andere behandeln. `DictionaryFacilitatorImpl.mainFrequency(word, language)`
  liefert die höchste Häufigkeit im Hauptwörterbuch der Gruppen dieser Sprache (kleiner 0: unbekannt, gesperrt oder
  keine Gruppe der Sprache). Tests: `CT/SwappedLettersTest.kt`, `SuggestTest.kt` („stage 75 …“),
  `InputLogicTest.kt` („revert swapped letters autocorrect on delete“).
- `C/InformalWords.kt`, `A/InformalWordsStore.kt` und `app/src/main/assets/agent/informal_words.txt` (Etappe 71, P291–P293; 1.695 Wörter): die eingebaute Liste informeller Wörter, die `Suggest.guardAllows` nicht korrigieren lässt. `InformalWords` (rein): `normalize` (NFC, klein, gerader Apostroph), `parse`, `appliesTo` (nur `de`/`en`), `isExpressive` (gedehnt und Lacher), `protects`. `InformalWordsStore` lädt die Datei einmal aus den Assets (`@Volatile`, Fehler = leere Liste) und fragt mit Tastatursprache und Sprache der Korrektur. Die Liste macht `tools/build_informal_words.py`. Tests `CT/InformalWordsTest.kt` (liest die echte Datei und `agent-core/src/test/resources/informal_cases.tsv`), `AT/InformalWordsStoreTest.kt`, `SuggestTest` „stage 71“.
- Autokorrektur nach den Fehlfällen des Owners (Etappe 101, Konzept `docs/AUTOKORREKTUR-101-KONZEPT.md`): rein in `C/`: `GermanVerbForms.kt` (regelmäßige Form eines Verbs der Liste), `OtherLanguageWord.kt` (Schutz eines gewöhnlichen Worts einer anderen aktivierten Sprache), `EnglishI.kt` („i“ → „I“), `NeighborKeys.kt` (eine Nachbartaste, QWERTZ/QWERTY), `SentenceLanguage.evidenceOfFrequencies` (Satzbeleg mit Vorsprung 60). `A/OtherLanguageDictionaries.kt`: Hauptwörterbücher der anderen aktivierten Sprachen nur lesend, geöffnet auf `ForeignWords.worker`, geschlossen mit `ForeignWords.closeDictionaries` (nicht mit dessen Leerlauf), `source` in Tests ersetzbar. `A/CorrectionGuards.blocksTarget` (Ziel nur aus Großbuchstaben, fremder Akzent) als Veto in `Suggest.shouldBeAutoCorrected`. In `Suggest.kt`: `isUnknownGermanVerbForm` (in `guardAllows` und `applySwappedLetters`), `isCommonWordOfOtherLanguage` (in `guardAllows` nach `passiveOnlyTarget`), `applyEnglishI` und `applyNeighborKey` (vor bzw. nach `applySwappedLetters`, an `mContextualAutocorrection`), Hilfen `activeLanguages`, `frequencyIn`, `sentenceLean`. Tests: `AutocorrectReportTest` „stage 101 …“, `CT/GermanVerbFormsTest`, `CT/NeighborKeysTest`, `CT/OtherLanguageWordTest`, `CT/EnglishITest`, `CT/SentenceLanguageTest`, `AT/CorrectionGuardsTest`.
- Etappen 107/108 (Konzept Abschnitte 5 und 6): `C/RareWordWeights.kt` (Formel seltenes Wort → häufiger Nachbar: Häufigkeit ≤ 80, Kandidat ≥ 150, Verlauf ≥ 111 und 20 Punkte Vorsprung, ohne Verlauf Folgewort-Vorsprung 20, Ablehnung = nie), `C/GermanShortForms.kt` („gibts“ → „gibt's“), `NeighborKeys.candidate` mit eigener Schwelle, `A/RuleCorrections.kt` (zuletzt angebotene Regelpaare im Speicher, Zähler behalten/zurückgenommen für die Diagnose), `C/CorrectionWeights.kt` (Paar zurückgehalten, solange bestätigt < 3 × abgelehnt; Halbwertszeit 30 Tage, Ablehnung < 0,25 vergessen); `RejectedCorrections` (in `A/CorrectionMemory.kt`) speichert je Paar bestätigt/abgelehnt/Zeit (Schlüssel `weights`, alte Menge `rejected` wird als eine Ablehnung übernommen), `confirm` aus `Memory.onWordCommitted`, `remember` mit Doppelzählschutz 60 s, Regelpaare auch nur mit „Personalisierte Vorschläge“; `FieldCorrectionWatcher.checkpoint` zählt „Y → drittes Wort“ als Ablehnung von „X → Y“ (107b), `DictionaryFacilitatorImpl.historyFrequency`/`hasHistory` (Tipp-Verlauf nur lesend). In `Suggest.kt`: `applyShortForm` (vor den Buchstabendrehern), `applyRareWord` (nach `applyNeighborKey`, mit den Folgewort-Ergebnissen des Kontexts), Hilfen `ruleMayCorrect`, `sentenceLeansToOtherLanguage`, `putRuleCorrectionFirst`. Diagnose-Zeile `agent_diagnose_corr_rule_corrections`. Tests: `AutocorrectReportTest` „stage 107 …“/„stage 108 …“, `InputLogicTest` „stage 107 …“/„stage 107b …“, `CT/RareWordWeightsTest`, `CT/GermanShortFormsTest`, `CT/CorrectionWeightsTest`.
- Etappe 110: `A/RuleClassCleanup.kt` entfernt nach einem Update (`markDue` aus `AppUpgrade.onUpgrade`, Zweig `FORK_UPGRADES_ONLY`, nicht bei Neuinstallation; Schlüssel `agent_rule_cleanup_due` in den App-Einstellungen, erledigte Sprachen und Anzahl in `agent_rule_cleanup`) je Tastatursprache einmal die gelernten Wörter, die eine Regelklasse jetzt korrigiert, aus eigenen Wörtern samt gezählten Nutzungen (`Memory`), behaltenen Fremdwörtern (`ForeignWords`), persönlichem Wörterbuch (`UserDictionary`-Anbieter) und Tipp-Verlauf (`DictionaryFacilitatorImpl.removeFromUserHistory`, ohne Sperrliste). Gestartet aus `Suggest.startRuleClassCleanup` (erst wenn ein Hauptwörterbuch geladen und die übrigen aktivierten Sprachen in `OtherLanguageDictionaries` offen sind), eigener Thread. Die Klassenfrage `Suggest.ruleClassTarget`: Kurzform (108), Nachbartaste (101), vertauschte Buchstaben (75), je mit ihrem Schalter und ihren Ausschlüssen, ohne Großanfang, ohne Wort eines Hauptwörterbuchs; Paare mit einer Ablehnung in `RejectedCorrections` bleiben. Diagnose-Zeile `agent_diagnose_corr_rule_cleanup`. Tests: `AutocorrectReportTest` „stage 110 …“.
- Untersuchung Autokorrektur (2026-11-21, keine Produktänderung): `AutocorrectReportTest.kt` hält mit festen Engine-Ergebnissen fest, was `Suggest`/`CorrectionGuard` heute tun (Ziel „nicbmal“ aus dem Verlauf ohne Wörterbuch-Prüfung, „i“ → „I“ bei „Richtige Wörter nie ändern“ blockiert, englischer Tippfehler im englischen Satz → deutsches Wort); Namen „reported finding N“. `tools/devicetest/devicetest.py`: Szenarien `reported_finding_*` (nur mit `--only` bzw. Workflow-Eingabe `only`) tippen mit echten Berührungen, schreiben Zeilen `PROBE …` ins Log und halten das heutige Verhalten fest (dokumentiert, nicht gutgeheißen).
- Diagnose „Korrektur & Sprache“ (Etappe 90a): `Diagnose.correction` (Part `CORRECTION`, Karte direkt nach „Letztes Feld“ in `AgentDiagnoseScreen`) liest nur Einstellungen (`TypingAutocorrect.current`, Schalter, Layout und zweite Sprachen je aktivem Subtyp) und Zahlen aus `Diagnose.correctionCounts` (Hintergrund-Thread, `runCatching`, nicht lesbar = null = „unbekannt"): Verlaufswörter je Sprache, `TypoMemory`, `FieldCorrectionMemory`, `RejectedCorrections`. Kein Wort. Test `AT/DiagnoseCorrectionTest.kt`; Texte `agent_diagnose_corr_*` in `agent_diagnose_strings.xml`.
- `C/suggest/ContextSuggest.kt` (Etappe 45, Paket `dev.agentkeyboard.core.suggest`, P161–P163):
  `SuggestKind` (EMAIL, PHONE, IBAN, ADDRESS, NAME, OTP, LINK), `AppCategory.of(packageName)`
  (MESSENGER/MAIL/BROWSER/OTHER nach Paket-Präfix), `FieldClass`, `FieldSignals` (Klasse,
  Hinweistext und Beschriftung, Feldname, Autofill-Kennungen, `toString` ohne Text),
  `FieldTargets.target` (Reihenfolge: Autofill-Kennung, Code-Hinweis in Text-, Zahl- und
  Telefonfeldern ohne `NOT_OTP` wie Postleitzahl, Gutschein, Promo; dann Typ E-Mail, Telefon,
  Personenname, Postadresse; dann Hinweis IBAN, E-Mail (nicht bei Passwort-Wörtern), Telefon,
  Adresse, genau „Name“; ein Zahlenfeld ohne Hinweis = OTP `weak`, `WEAK_OTP_MILLIS` 3 min,
  sonst `OTP_MILLIS` 10 min) und `fits` (leeres Feld oder Anfang des Werts, bei Code/Telefon/IBAN
  nur Ziffern, Buchstaben und `+`). `ContextCues`: Tabelle `CUES` mit fester `id` (look, here,
  media, phone, email, iban, address, code, name), Gewicht je Art, Abstand (Link-Hinweise bis
  `LINK_DISTANCE` = 60 Zeichen vor der Schreibmarke, Nähe ab 8 Zeichen abnehmend bis 0,4;
  Datenhinweise bis `DATA_DISTANCE` = 3) und Phrasen de/en/es; `find` liest höchstens `WINDOW` =
  80 Zeichen nach dem letzten Zeilenumbruch, nur wenn der Text auf Leerraum oder „:“ endet,
  Phrasen nur an Wortgrenzen (`-` zählt als Buchstabe, „mail“ ≠ „E-Mail“), ein Datenwort aus einem
  Wort nur mit Doppelpunkt (+`COLON_BONUS` 0,1), nichts, wenn danach schon steht, was der Hinweis
  ankündigt; Stärke = bester + ½ zweiter + ¼ dritter Hinweis, höchstens 1. `ContextScorer`:
  Stärke + Frische (`OWN` 0,45 für Meine Daten; Clip `freshMax` × e^(−Alter/6 min), Link 0,7,
  Code 0,6, Daten 0,55; über `MAX_AGE_MILLIS` 10 min nie) + App (Messenger +0,15 für Links,
  Browser −0,1) + Feld (Kurznachricht +0,1 für Links) + gelerntes Gewicht; Chip ab `THRESHOLD`
  1,0 (ohne Hinweis höchstens 0,95). `ContextWeights` (unveränderlich): Schlüssel nur `id:ART`
  aus der festen Tabelle, `accept` +0,3 × (0,4 − w), `ignore` −0,12 × (w + 0,4), Grenzen ±0,4,
  `decayed` Halbwertszeit 30 Tage (Tag als Zahl, eine rückwärts laufende Uhr ändert nichts),
  `encode` „v1;Tag;id:ART=Zahl;…“, `parse` verwirft alles Unbekannte, nie eine Ausnahme,
  höchstens 4096 Zeichen. Test `CT/suggest/ContextSuggestTest.kt`.
- `SnippetTemplate.kt` (Etappe 42): Vorlagensprache von `@snip` (`parse` wirft nie, `literal` für
  Bausteine von vor 42, `expand` mit `Values` = Datum, Uhrzeit, `DateFormatting.Language`,
  Zwischenablage oder null, Felder; `Expansion` mit Cursor-Index in UTF-16), `Issue` (`UNKNOWN`,
  `UNCLOSED`, `EXTRA_CURSOR`) für die Warnungen im Editor, `language` (en/es folgen der Tastatur,
  sonst Deutsch); `SnippetApps` (Paketnamen, höchstens 20, `allows`). Test `CT/SnippetTemplateTest.kt`.
- `TouchDynamics.kt` (Etappe 50): Kurzzeitschicht und Szeneprofile der Trefferzonen, ohne Android
  (Details im Abschnitt „Lerndaten“ → Dynamische Trefferzonen). Test `CT/TouchDynamicsTest.kt`.
- `C/memory/` (Etappe 46, Gedächtnis, Paket `dev.agentkeyboard.core.memory`, P174–P177):
  `PhraseMemory.kt`: `PhraseText` (`currentSentence` = Tokens nach dem letzten `. ! ? … ; ( ) "`
  oder Zeilenumbruch, `key` = Wort kleingeschrieben ohne `,`/`:` am Ende, null für Ziffern, `@`,
  URL-Teile, über 32 Zeichen; `trailingWords`), `Phrase` (Schlüssel, Text wie zuletzt getippt,
  Gewicht, Tag, angepinnt; `toString` ohne Text), `PhrasePrediction` (`next`/`nextShare`,
  `rest`/`restWords`/`restShare`, `restKey`) und `PhraseBook`: `learn(run, Tag)` zählt die Folgen
  von 2 bis `MAX_WORDS` = 8 Wörtern, die mit dem neuen Wort enden; 2 Wörter immer, eine längere
  erst, wenn beide um ein Wort kürzeren Teile `GROW_AT` = 2 erreicht haben, neu mit Gewicht 1 (P175).
  Verstärkung +1 auf das verblasste Gewicht (Halbwertszeit `HALF_LIFE_DAYS` = 30, Grenze 1000).
  Höchstens `MAX_ENTRIES` = 2000; darüber fallen die schwächsten (bei Gleichstand die ältesten)
  nicht angepinnten bis 95 %. Angepinnt (höchstens 200): kein Verblassen, nie verdrängt.
  `predict(Text vor der Schreibmarke)` nur nach Leerraum: der längste Kontext (2–7 Wörter am Ende
  des Satzteils) mit Gewicht ≥ `MIN_CONTEXT` = 2 entscheidet; nächstes Wort ab `NEXT_MIN_SUPPORT` = 2
  und `NEXT_MIN_SHARE` = 0,4, der ganze Rest (≥ 2 Wörter, die längste Folge) ab `REST_MIN_SUPPORT`
  = 3 und `REST_MIN_SHARE` = 0,7 (Anteil am Kontextgewicht; gemessen, `docs/VALIDATION.md` →
  Etappe 46). `predictKeys` dasselbe für die Vorwörter der Engine; es liest nur die Schlüssel mit
  denselben ersten zwei Wörtern (`byHead`, Index in Einfügereihenfolge, gepflegt von `put`/`drop`,
  Nachprüfung 46 N1). `copy` für das Schreiben auf einem anderen Thread. `delete` entfernt die Folge und
  jede längere, die sie enthält; `encode` „v1“ plus „Gewicht;Tag;0/1;Text“ je Zeile, `parse`
  verwirft Kaputtes, nie eine Ausnahme, eine rückwärts gelaufene Uhr behält die Einträge.
  `OwnWords.kt`: `OwnWordBook` (Nutzungen je Wort, nach `PROMOTE_AT` = 3 eigenes Wort, Nutzungen
  älter als 90 Tage beginnen neu, je höchstens 500; `isCandidate` nur Buchstaben mit innerem `'`
  oder `-`, 2–32 Zeichen; `completions` ab 2 Zeichen; Kodierung „v1“, `o;Form`, `u;Zahl;Tag;Form`).
  `ShortcutOffers.kt`: `ShortcutRules.plausible` (Kurzform 2–6 Buchstaben, Erweiterung 1–5 Wörter
  und mindestens 2 Zeichen länger; ein Wort enthält die Buchstaben in Reihenfolge ab dem ersten,
  mehrere Wörter haben sie als Anfangsbuchstaben; Tippfehler-Korrekturen nie, mit `shortIsWord` eine
  Erweiterung, die mit der Kurzform beginnt, auch nicht: „Haus“ → „Hausaufgaben“, Nachprüfung 46 M2;
  ohne, „Mo“ → „Montag“, zweite Nachprüfung), `ReplacementTracker`
  (letzte 8 Commits mit Startposition; ein Commit an der Startposition eines früheren, anderen
  Worts = Ersetzung an Ort und Stelle, bei mehreren Wörtern bis zu 5 Commits gesammelt),
  `ShortcutBook` (Zählung je Paar, ab `OFFER_AT` = 2 `READY`, dann `SHOWN`/`CREATED`/`DECLINED`,
  höchstens 200, Kodierung „v2“ „Zahl;STATUS;C/l;Kurz;Lang“, „v1“ ohne Fall-Spalte wird gelesen;
  `CREATED` = gemerkt, nie mehr überschrieben, sonst gewinnt die Form nach kleiner Kurzform;
  `expansionOf`, `rememberedOf` (`Remembered`: Erweiterung, `fromCapitalShort`), `remembered`, P187). `SnippetMatcher.kt`: Merkmale = Wörter (`w:`,
  Gewicht 1), Wortanfänge aus 5 Buchstaben bei längeren Wörtern (`p:`, 0,7) und Buchstaben-Trigramme
  mit Rand (`t:`, 0,35), kleingeschrieben ohne Akzente, ß als ss, ohne Stoppwörter (de/en/es, auch
  „vielen“, „dank“, „minuten“); TF-IDF über die Bausteine (Name doppelt, Text ohne Platzhalter, 400
  Zeichen), Kosinus; 0 ohne genug gemeinsame Wörter (`sharesEnough`: ein Wort des Namens, 2 Wörter,
  40 % der Wörter des Bausteins oder ab 3 Bausteinen ein Wort, das nur dieser hat; ein Wort zählt mit
  sich oder seinem Wortanfang, Nachprüfung 46 N5). `best` ab 2 Wörtern mit 3 Buchstaben,
  `THRESHOLD` = 0,35 und `MARGIN` = 0,08 vor dem zweiten (gemessen). Tests
  `CT/memory/PhraseMemoryTest.kt` (mit synthetischem Schreiber und Präzisionsmessung),
  `CT/memory/MemoryRulesTest.kt`.
- Tests in `CT/`: `AiTextActionTest`, `ModelBudgetTest`, `EditorPolicyTest`, `LocalTextActionsTest`,
  `PendingBatchGateTest`, `SkillBindingTest`, `ToolTest`, `WorkflowTest`,
  `InsertionAnchorTest`, `StyledTranslationTest`, `UpdateReleasesTest`, `RgbLightingTest`,
  `RgbCurveTest`, `RgbPatternsTest`, `RgbFrameStatsTest`, `RgbOwnPatternTest`, `RgbPatternJsonTest`,
  `clip/ClipGuardTest`, `clip/PrivateRangesTest`,
  `clip/SmartPasteTest`.
  Befehl: `core-tests`.

## Geänderte Upstream-Dateien

> Jede Upstream-Datei, die der Fork ändert, mit dem Grund. Alle übrigen Upstream-Dateien sind unverändert.

Engine-Einbindung (nur eng ändern, jede Änderung von einem Prüfer lesen lassen):

- `LatinIME.java`: erzeugt `AgentEditorController`, meldet Editorstart/-ende, abgeschickten Text
  und unterdrückte Lernhistorie. Etappe 59: `tryShowCommandChips` liest die Länge für
  `updateGuard` mit `KeyboardCommands.readLength(cursor)` statt der festen Länge `LOOKBACK + 1`
  (wirft nie; P244). `switchToSubtypeByCommand` (Sprachbefehle) und die Prüfung
  `SubtypeChoice.holds` am Anfang von `onStartInputInternal` (P66). Engine-Speicher läuft über `AgentStorageContexts.keyboard`.
  Diagnose (30): je eine Zeile in `onCurrentInputMethodSubtypeChanged` (`SubtypeSwitchLog.onSwitched`
  nach den frühen Rücksprüngen), `switchToSubtype` (`internalSwitch`), `switchToSubtypeByCommand`
  (`expectCommand`) und `onStartInputInternal` vor `postSwitchLanguage` (`expectFromStart`), P98.
  Korrektur nach Prüfer (30/31): Die Prüfung `SubtypeChoice.holds` bekommt als dritten Wert eine
  Funktion, die `SubtypeState.getSubtypeForLocales(…, peek = true)` fragt; „behalten“ wird nur
  notiert, wenn die App wirklich umgeschaltet hätte (P98).
  Etappe 52: `onStartInputInternal` holt die Sprache der App an beiden Stellen (Prüfung
  `SubtypeChoice.holds` und Wahl nach Hint-Locales) über `CommandAppSettings.subtypeForApp(this,
  mSettings, packageName)` statt `mSettings.getSubtypeForApp`: zuerst HeliBoards eigene Sprache
  pro App (nur mit dem Schalter), dann die per Befehl gesetzte, dann die für alle Apps (P199).
  Nachprüfung 52: `onCurrentInputMethodSubtypeChanged` ruft nach `saveSubtypeForApp`
  `CommandAppSettings.onSwitchedInField` (eine Zeile, P219).
  Etappe 55: eine Zeile in `onStartInputViewInternal` nach dem Laden der Einstellungen und der
  Prüfung auf `editorInfo == null`, vor dem frühen Rücksprung ohne `MainKeyboardView`:
  `LastField.capture(this, editorInfo, currentSettingsValues, mDictionaryFacilitator)` (wirft nie,
  P233). Etappe 56 nutzt dieselbe Zeile: `capture` ruft zuerst `UpdateOnOpen.onStartInputView`
  (wirft nie, kehrt sofort zurück, die Anfrage läuft auf einem eigenen Thread, P237); `LatinIME`
  selbst ist dafür unverändert.
  Nachprüfung 55: `SuggestionStripView.kt` (keine Engine-Datei) meldet über `reportToLastField`
  (`runCatching`, `LastField.onStripView`) am Ende von `setToolbarVisibility` und nach dem neuen
  Inhalt in `setSuggestions` und `setExternalSuggestionView`, was die Leiste zeigt (Wörter,
  Symbolleiste, Emoji-Reihe, fremde Ansicht), nie Wörter oder Texte (P236).
- `InputLogic.java`: verwirft Caches und blockiert Eingabepfade nach einer unklaren Ersetzung
  (`ApplyOutcome.UNKNOWN`), unterdrückt Lernen nach generierter Übernahme. Ersetzen
  (`applyGeneratedText`) und Einfügen (`insertGeneratedText`) laufen durch dieselbe
  Nachbereitung (`applyGenerated`). Beim Einfügen kommt ein ausstehendes automatisches
  Leerzeichen (nach gewähltem Vorschlag) unter denselben Bedingungen wie beim Tippen mit in
  denselben einen Commit. `replaceCommandText` (24a) beendet zuerst ein gerade getipptes Wort
  so, wie es dasteht (ohne Korrektur, ohne Lernen), und läuft dann durch `applyGenerated`, aber
  ohne das Lernen für den Rest der Sitzung abzuschalten. `performAdditionToUserHistoryDictionary`
  lernt kein Befehlswort (`KeyboardCommands.isCommandWord`). Die Überladung mit `generated = true`
  (`@ki`) stoppt das Lernen für den Rest der Sitzung wie bei anderen KI-Übernahmen.
  Nachprüfung 44/45 (P178, zweite Nachprüfung P185): `mPrivateRanges` (`PrivateRanges`, keine
  Texte) hält bis zu drei eingefügte Tresor-Geheimnisse, Codes oder Werte aus „Meine Daten“.
  `markEnteredTextPrivate` (Tresor-Pfad, markiert `mEnteredText`, also das wirklich Committete nach
  der TLD-Behandlung) und `markPrivateText(text)` (Chip-Pfad, Text endet an der Schreibmarke) gehen
  über `markPrivateBeforeCursor`, das zugleich `mLastComposedWord`, `mEnteredText` und
  `mWordBeingCorrectedByCursor` leert. `onCodeInput` (Zeicheneingaben und Rücktaste) und
  `onTextInput` melden ihre Bearbeitung an der Schreibmarke (`privateTextBeforeEdit` vorher,
  dritte Nachprüfung; `privateTextEdited` danach, Auswahl davor und Schreibmarke danach). `privateTextAtCursor(davor, danach)` sperrt: `restartSuggestionsOnWordTouchedByCursor`
  (mit dem Wortbereich), den Neustart in `handleNonSeparatorEvent` (Wort bis zum letzten Leerraum)
  und das Merken von `mWordBeingCorrectedByCursor` in `onCodeInput` (entschieden beim Merken, nicht
  beim Lernen an der neuen Schreibmarke). `performAdditionToUserHistoryDictionary` lernt nichts,
  solange das Wort oder seine N-Gramm-Vorgänger (`MAX_PREV_WORD_COUNT_FOR_N_GRAM` + 1 Wörter vor der
  Schreibmarke) einen privaten Text berühren, außer für `mWordBeingCorrectedByCursor` (Überladung
  mit `privateDecided`, beim Merken entschieden, ohne N-Gramm-Kontext; `Memory.onWordCommitted`
  prüft auch dann an der neuen Schreibmarke, dritte Nachprüfung); `mFieldCorrections` bekommt
  `mPrivateRanges.touches` als `FieldCorrectionWatcher.PrivateText`; `onFreshEditorSession` vergisst alle.
- `InputLogicHandler.java`: hält Worker- und UI-Teil eines Swipe-Batches über
  `PendingBatchGate` gemeinsam gesperrt.
- `RichInputConnection.java`: native Ersetzung als genau ein Commit, Ergebnis als `ApplyOutcome`
  (`replaceSelectedTextIfUnchanged`); ebenso `insertTextIfCursorUnchanged` für das Einfügen an
  der unveränderten Schreibmarke, nur wenn der Editor antwortet und nichts markiert ist
  (Fallstrick P24). Nur über `InputLogic.insertGeneratedText` aufrufen.
  `replaceTextBeforeCursorIfUnchanged` (24a) ersetzt einen getippten Befehl direkt vor der
  Schreibmarke: liest den Text aus dem Editor (nicht aus dem Cache), dann `setComposingRegion`
  plus `commitText` als ein Commit. Nur über `InputLogic.replaceCommandText` aufrufen.
- Etappe 70: `LatinIME.java` (`onStartInputViewInternal`, Z6): der zweite Aufruf von
  `tryShowClipboardSuggestion` nach `setNeutralSuggestionStrip` läuft nur noch, wenn in der Leiste
  keine externe Ansicht (Chips) steht (`isExternalSuggestionVisible()`, P290);
  `SuggestionStripView.kt`: dafür ist `isExternalSuggestionVisible` von außen lesbar (`private set`).
  `RgbKeyPainter` fängt Ausnahmen selbst ab (P288), `KeyboardView.java` bleibt unverändert.
  `PointerTracker.java`: `cancelKeyTracking` setzt jetzt `mKeySwipeAllowed`, `sInKeySwipe`,
  `mInHorizontalSwipe`, `mInVerticalSwipe` und den Touchpad-Zustand zurück (Nachprüfung M2, P289); `sInKeySwipe` nur, wenn dieser Finger
  selbst wischen durfte (`mKeySwipeAllowed`); der Abbruch mitten in einer Wischgeste mit Tracker in der Warteschlange war schon richtig.
- `PointerTracker.java`: Leertasten-Langdruck fragt `CustomAction.SPACE_HOLD` (die eingestellte
  Halten-Aktion); ist sie verbraucht, endet der laufende Zeiger samt Swipe/Touchpad-Flags, sonst
  bleibt HeliBoards Tastaturwahl über `mSpaceForLangChange` der Rückfall. Leertasten-Gesten:
  `isSwiper` fragt `mSpaceGestures.hasSwipe()`; `onKeySwipe` merkt beim ersten Schritt die
  Startrichtung (`mSpaceSwipeGesture`, links/rechts bzw. oben/unten nach Vorzeichen) und meldet
  jeden Schritt als `onSpaceSwipe(Richtung, Schritte)`; `oneShotSwipe` fragt
  `SpaceAction.isContinuous` der Startrichtung statt der alten `SwipeAction`-Liste. Seit Etappe 33
  meldet ein seitlicher Wisch mit Einmal-Aktion statt Schritten bei jeder Bewegung den Abstand zum
  Aufsetzpunkt (`onSpaceSwipeDistance(Richtung, dX, SpaceDetents.keyWidth(mMostCommonKeyWidth, mOccupiedWidth))`, `mStartX` bleibt stehen,
  auch unter einem Schritt nahe dem Start), die Aktion kommt erst am letzten Rastpunkt
  (`SpaceDetents`, P100). Ganz vorn in `onKeySwipe`, noch vor der Schnelltipp-Sperre, beendet eine
  klar seitliche Bewegung ab 10 dp (`sHoldCancelDistance`, `SpaceDetents.cancelsHold`) den
  Langdruck-Timer der Leertaste (`cancelLongPressTimersOf`, P101). Schwelle, Achsenwahl,
  senkrechte Wische, fortlaufende Aktionen, Schnelltipp-Sperre, Ende über `onEndSpaceSwipe` und
  Gestentippen sind unverändert.
- `KeyboardActionListener.java` (Nachprüfung 44/45: `onPrivateTextInput`, Vorgabe `onTextInput`,
  `KeyboardActionListenerImpl` ruft `LatinIME.onPrivateTextInput`; `CustomAction.SPACE_HOLD`, früher `SHOW_AGENT_WHEEL`;
  `onSpaceSwipe(SpaceGesture, int)` ersetzt `onHorizontalSpaceSwipe`/`onVerticalSpaceSwipe`;
  `onSpaceSwipeDistance(SpaceGesture, int, int)` für seitliche Einmal-Wische, Etappe 33) und
  `KeyboardActionListenerImpl.kt` (leitet Panel-, Rad-, Skill- und Diktat-Taste an den Controller,
  blockiert Eingabe während eines Overlays; Leertasten-Aktionen und Doppeltippen, siehe
  „Editorzugriff, Panel und Rad“; seitliche Einmal-Wische laufen über `SpaceDetents` und spielen
  `HapticChain`; Etappe 100: `onCodeInput` und `onTextInput` rufen `SymbolInk.onTypedInput`).
- Etappe 87 (Cursor als Trackpad, P410–P433), jede Änderung von einem Prüfer lesen lassen:
  `PointerTracker.java` (+19 Zeilen in Etappe 87, dazu in Etappe 87c +1 Zeile: `static int getPointerStep()`, der Getter für `sPointerStep`, sonst nichts; alle als Hinzufügungen: in `onKeySwipe`, vor dem Leertasten-/Löschzweig und hinter der Schnelltipp-Sperre, 12 Zeilen:
  `sListener.onCursorTrack(mPointerId, code, x - mStartX, y - mStartY, eventTime, mStartTime)`; bei `TRACK_OFF` läuft alles wie vorher, sonst kehrt
  die Methode zurück, bei `TRACK_ACTIVE` zusätzlich (bei jedem Move, nicht nur beim ersten) `cancelKeyTimersOf` und `mInHorizontalSwipe = true`, damit das Heben wie
  bei einem seitlichen Wisch endet, bei `TRACK_REFUSED_DRAG` `cancelKeyTimersOf` und `cancelTrackingForAction()` (der Move-Weg und das Heben dieses Fingers tun danach nichts); dazu je eine Zeile `sListener.onCursorTrackEnd(mPointerId)` im Heben-Zweig von `mKeySwipeAllowed` und am Ende
  von `cancelKeyTracking`), `KeyboardActionListener.java` (`TRACK_OFF/PENDING/ACTIVE/REFUSED_DRAG`, `onCursorTrack`, `onCursorTrackEnd`, Vorgabe
  `TRACK_OFF` im `Adapter`), `KeyboardActionListenerImpl.kt` (Zustandsmaschine, siehe `A/CursorTrackpad.kt`; der Schwanz von
  `onMoveCursorHorizontally` als `applyHorizontalMove`, unverändert), `SettingsValues.java` (zwei Felder), `LatinIME.java` (zwei Zeilen `mKeyboardActionListener.onCursorTrackEnd(-1)` in `onStartInputViewInternal` und `onFinishInputViewInternal`). `InputLogic.java` und
  `RichInputConnection.java` sind unverändert.
- `InputAttributes.java` (Nachprüfung 45, P180): `mNoLearning` auch für Felder mit
  `privateImeOptions` = `AgentPreferences.NO_LEARNING_FIELD` (die Felder von „Meine Daten“), in
  jeder Feldklasse und API-Stufe; daraus wird wie beim Flag `IME_FLAG_NO_PERSONALIZED_LEARNING`
  Inkognito.
- Etappe 61 (Korrektur in Apps erzwingen, Prüfer Pflicht; `mInputType` bleibt unverändert, P254):
  `InputAttributes.java`: `getImeOptions()` (die `imeOptions` des Felds für die Regel; sonst nichts).
  `SettingsValues.java`: `mCorrectionForced = ForcedApps.forces(prefs, inputAttributes)`; in
  `mAutoCorrectEnabled` ein `|| mCorrectionForced` bei „die App verlangt es“, `mOverrideShowingSuggestions`
  ist jetzt `(bisherige Formel) || mCorrectionForced`; neue Methode `capsInputType(inputType)`.
  `InputLogic.java`: in `getCurrentAutoCapsState` ersetzt `settingsValues.capsInputType(ei.inputType)`
  das rohe `ei.inputType`.
- `SettingsValues.java`: `mSpaceGestures` (aufgelöste Leertasten-Gesten) statt
  `mSpaceSwipeHorizontal`/`mSpaceSwipeVertical`; `mSpaceForLangChange` aus
  `SpaceGestures.holdShowsPicker`; `mUseDoubleSpacePeriod` ist aus, solange Doppeltippen eine
  Aktion hat.
- `AdvancedScreen.kt`: statt der Zeilen „Horizontales/Vertikales Wischen auf der Leertaste“ und
  „Leertaste lang drücken …“ ein Eintrag „Leertaste“ (`AGENT_SPACE_ENTRY`); Sprach-Wischdistanz
  und Touchpad-Regler stehen jetzt auf der Seite „Leertaste“. `SettingsContainer.kt`:
  `createAgentSpaceSettings` für Suche und Seite. `SettingsNavHost.kt`: Ziel `agent_space`.
- Etappe 69: `SettingsContainer.kt`: `Setting` hat ein optionales Feld `keywords` (zusätzliche
  Suchwörter), `filter` sucht auch dort, die Liste enthält `createAgentPageSettings` (alle Seiten des
  Forks, P284); `SettingsWithoutKey` hat `FORK_REPO` statt `GITHUB_WIKI` und `COMMUNITY_LINKS`.
  `AboutScreen.kt`: statt Wiki, Community und „Auf GitHub ansehen“ (alles HeliBoard) die Zeile
  „SecondHand auf GitHub“ (`Links.FORK_REPO`) und „Basiert auf HeliBoard“ (Dank und Lizenz); die Lizenz
  bleibt. `latin/common/Constants.kt`: `Links.FORK_REPO` neu, `Links.COMMUNITY_LINKS` entfernt (nicht mehr
  benutzt). Texte: `app_slogan` ist übersetzbar (stand in `donottranslate.xml`), fehlende deutsche
  Namen in `values-de` ergänzt, `backup_restore_message` nennt, was die Sicherung enthält.
- `InputView.java`: leitet Touch und Hover an sichtbare Overlays (Panel, Rad, Rechner).
- `main_keyboard_frame.xml`: Wurzel ist `AgentKeyboardFrame`, darin Tastatur, `FlatMenuView`, `AgentPanelView`,
  `AgentWheelView` und `CalculatorView` (24b).
- `KeyCode.kt` (`AGENT_ACTIONS` = -12000, `SKILL_1`–`SKILL_3` = -12001…-12003,
  `AGENT_DICTATE` = -12004, `AGENT_EMOJI_MIXER` = -12005 für 25a),
  `ToolbarUtils.kt` (Toolbar-Tasten `AGENT`, `SKILL_1`–`SKILL_3` und `DICTATE`; nur `AGENT` ist
  standardmäßig in der Toolbar), `KeyboardIconsSet.kt` (Symbole `ic_agent_actions.xml`,
  `ic_agent_dictate.xml` und
  `ic_skill_*.xml` für die drei Skill-Tasten).

Autokorrektur (Etappe 20a, Details im Abschnitt „Lerndaten“):

- `Suggest.kt`: nach `shouldBeAutoCorrected` entscheidet `guardAllows` (über `CorrectionGuard`),
  ob die automatische Korrektur bleibt; sonst bleibt sie nur Vorschlag. `shouldBeAutoCorrected`
  selbst ist unverändert, damit die Upstream-Tests es weiter direkt prüfen.
- `InputLogic.java`: `revertCommit` meldet eine rückgängig gemachte Korrektur an
  `RejectedCorrections.remember` und `TypoMemory.forget`; `onPickSuggestionManually` meldet ein
  anderes als das getippte Wort an `TypoMemory.confirm` (20b, nur Tipp-Eingabe, kein Emoji).
- `Suggest.kt` (20b): `applyLearnedCorrection` setzt ein gelerntes Wort an die erste Stelle und
  macht es zur automatischen Korrektur; ein unbekanntes Wort bekommt die Quelle
  `TypoMemory.DICTIONARY`.
- `SettingsValues.java`: `mKeepValidWords`, `mKeepUmlautVariants`, `mRememberRejectedCorrections`,
  `mLearnTypos`, `mAdaptTouch`.
- Getipptes Wort vorne (Etappe 48, P158–P160): `InputLogic.java` hält `mTypedWordFirst`
  (`K/agent/TypedWordFirst.kt`), `startInput` leert es. `commitCurrentAutoCorrection` merkt nach
  dem Commit Start, korrigiertes und getipptes Wort (nicht beim Gestenschreiben), `revertCommit`
  vor dem Commit des getippten Worts dessen Start (nur Sprachen mit Leerzeichen; `reverted` merkt
  nur, wo eine Korrektur gemerkt ist, also nie bei Gesten). `commitChosenWord` ruft nach
  `commitWord` `committedAt(Start)` und verwirft so einen alten Eintrag an derselben Stelle
  (Feld per Löschtaste oder von der App geleert, Nachprüfung 48).
  `restartSuggestions` fragt `resumed(Start, Wort)` und ordnet die Span-Liste mit `arrange`;
  `getSuggestedWords` ordnet das Ergebnis von `Suggest`, solange der wiederaufgenommene Kompositions-
  text unverändert ist (`typedFor`). `onPickSuggestionManually`: Tipp auf die getippte Form eines
  korrigierten Worts (`correctionUndoneBy`) → `RejectedCorrections.remember` und
  `TypoMemory.forget` wie bei der Löschtaste; `TypoMemory.confirm` greift bei wiederaufgenommenen
  Wörtern ohnehin nicht.
- `Suggest.kt` (48): `guardAllows` blockiert eine reine Groß-/Klein-Korrektur
  (`CorrectionGuard.changesOnlyCase`), sobald Hauptwörterbuch oder Lernverlauf der Sprache der
  Korrektur genau die getippte Form kennt (in `suggestionResults`; eine andere aktive Sprache zählt
  nicht, „hand“ → „Hand“ bleibt automatisch, Nachprüfung 48), außer die Korrektur ist
  `KIND_WHITELIST` („i“ → „I“).
  `correctToCapitalizedWord` (Umschalttaste nach dem Wort mit „Vorgeschlagene Großschreibung
  automatisch korrigieren“) läuft daran vorbei und bleibt.
- `Suggest.kt` (60): `guardAllows` bekommt den `NgramContext` und fragt am Ende, nur wenn
  `CorrectionGuard` die Korrektur zulässt und die Korrektur eine Locale hat, `sentenceIsOfOtherLanguage`: zuerst das Tor (`mainLocalesKnowing(getippt)`
  enthält eine andere Sprache als die der Korrektur, aber nicht diese; nicht `typedInfo`, der erste Treffer kann ein Verlaufseintrag sein), dann bis zu drei vorige
  Wörter (`NgramContext`, Abbruch am Satzanfang oder an einem leeren Eintrag), je Wort
  `DictionaryFacilitatorImpl.mainLocalesKnowing` (Gruppen, deren Hauptwörterbuch das Wort kennt, wie
  geschrieben oder klein, ohne Sperre), die Entscheidung in `C/SentenceLanguage.kt`. `ForeignWords` und die
  Reihenfolge der Vorschläge bleiben unberührt. `DictionaryFacilitatorImpl.kt`: nur `mainLocalesKnowing`
  und die Konstante `MAIN_ONLY`.
- Korrekturen im ganzen Feld (Etappe 49, P169–P171): `InputLogic.java` hält `mFieldCorrections`
  (`K/agent/FieldCorrections.kt`, `FieldCorrectionWatcher`). `startInput` → `start` (Feld vergessen);
  `commitChosenWord` nach `commitWord` und `onUpdateSelection` nach dem Zurücksetzen der Caches
  (vor `mRecapitalizeStatus.enable`) → `checkpoint` (Feld mit dem letzten Stand vergleichen, bei
  `commitChosenWord` mit erlaubtem gelöschtem Rest); `commitCurrentAutoCorrection` → `autoCorrected`
  (nur Tipp-Eingabe, wie 48); `untrusted` (Vergleichsstand verwerfen) am Anfang von `onCodeInput`
  bei Markierung, am Ende von `onTextInput`, in `paste` und in `applyGenerated` nach erfolgreichem
  Commit. Nachprüfung 49: `checkpoint` bekommt `composing` (in `onUpdateSelection`
  `mWordComposer.isComposingWord()`: dann kein Vergleich und kein neuer Stand) und das Paar
  getippt → abgeschickt einer Autokorrektur (`commitChosenWord` bei `COMMIT_TYPE_DECIDED_WORD`,
  vor `commitWord` gelesen); `ownEdit(Schreibmarke)` am Anfang von `onCodeInput` (ohne Markierung,
  nur Zeichen und Löschtaste) und von `onPickSuggestionManually`; `untrusted` bei `UNDO`/`REDO`.
  Zweite Nachprüfung 49: neue Methode `checkpointFieldCorrectionsBeforeOwnCursorMove` (Prüfpunkt
  vor einer Schreibmarken-Bewegung der Tastatur selbst, P183), gerufen aus
  `KeyboardActionListenerImpl.onMoveCursorHorizontally` nach `finishInput` und vor `setSelection`;
  das Paar getippt → abgeschickt nur, wenn sich das Wort genau (Groß/klein) geändert hat
  (`FieldCorrectionWatcher.keyboardCorrectionFrom`). `KeyboardActionListenerImpl.onKeyDown` ruft
  bei Hardware-Strg+Z/Strg+Y `untrusted`. Dritte Nachprüfung 44/45/49: `FieldCorrectionWatcher`
  bekommt im Konstruktor `PrivateText` (aus `InputLogic`: `mPrivateRanges.touches`), `checkpoint`
  verwirft eine Änderung, deren Wort `[wordStart, wordStart + max(alt, neu)]` einen privaten Text
  berührt. Sonst unverändert; `RichInputConnection` nicht geändert, gelesen nur über
  `getTextBeforeCursor`/`getTextAfterCursor`/`hasSelection`/`getExpectedSelectionStart`/
  `hasSlowInputConnection`.
- `Suggest.kt` (71): `guardAllows` fragt nach dem Check der eigenen Wörter (Gedächtnis, 46) `InformalWordsStore.protects(context, getippt, Tastatursprache, Sprache der Korrektur)`, außer die Korrektur ändert nur die Großschreibung oder ist ein Kürzel oder ein Whitelist-Eintrag; bei Ja bleibt das Wort, die Korrektur steht weiter als Vorschlag. Eine Bedingung, vor `ForeignWords` und den Regeln aus 48.
- `CapsModeUtils.java` (48): im Zustand WORD (Wort direkt vor dem Punkt, ohne inneren Punkt) und
  am Textanfang fragt `getCapsMode` `K/agent/Abbreviations.isAbbreviation`; eine bekannte Abkürzung
  (Deutsch bei `mUsesGermanRules`, Englisch bei `mUsesAmericanTypography`) oder ein einzelner
  Buchstabe nach „x. “ („z. B.“) gibt keine Satz-Großschreibung. Seit der Nachprüfung 49 gelten
  die bekannten Paare (z. B., d. h., u. a., o. ä., s. o., s. u., v. a., u. U., i. d. R.) in jeder
  Schreibung („Z. B.“ am Satzanfang), andere Buchstabenpaare nur, wenn nicht beide groß sind
  (Initialen „J. K.“ schreiben groß weiter). Alles andere wie upstream.

Autokorrektur folgt der Sprache (Etappe 51, Details im Abschnitt „Lerndaten“ → Sprache der Lerndaten, P191–P193, P207–P208):

- `DictionaryFacilitatorImpl.kt`: `usesSameSettings` vergleicht zusätzlich die Hauptsprache
  (`locales.firstOrNull() == dictionaryGroups[0].locale`), sonst blieb beim Wechsel zwischen zwei
  Subtypen mit denselben Sprachen (Deutsch mit Spanisch ↔ Spanisch mit Deutsch) die alte Sprache
  Hauptsprache samt ihrer Konfidenz. `getUsedLocales` sammelt in `linkedSetOf` statt `hashSetOf`:
  Die Gruppe der Hauptsprache steht vorne (`mainLocale`, Großschreibung; bei gleicher Konfidenz
  `currentLocale` und Ziel des Lernverlaufs, das Letzte ohne Test). Vorher entschied der Hash: {es,
  de} und {en, de} begannen mit Deutsch (P191).
- `Suggest.kt`: `applyLearnedCorrection` fragt `TypoMemory.meantWord(…, language, knownInLanguage)`
  mit der Tastatursprache (`ForeignWords.languageOf(settings)`, P192); ein Paar von vor 51 nur,
  wenn `isValidSuggestionWord` (Gruppe der Hauptsprache) das gemeinte Wort in seiner oder kleiner
  Schreibung kennt. `guardAllows` und `applyLearnedCorrection` korrigieren ein gemerktes Wort einer
  anderen Sprache (`ForeignWords.isRemembered`) nie automatisch. `addForeignWord` direkt nach
  `addFromMemory`: meldet `ForeignWords.onTyped` (kennt ein Hauptwörterbuch der Tastatursprache das
  getippte Wort in irgendeiner Schreibung? Nachprüfung 51 N5: „bekannt“, solange die Hauptsprache des
  Facilitators nicht die Tastatursprache ist oder kein Hauptwörterbuch geladen ist) und setzt ein gemerktes Wort, das das getippte beginnt,
  als `KIND_COMPLETION` hinter die Korrektur (ohne Korrektur an den Anfang), in der Großschreibung
  des Tippens; nie als Korrektur.
- `LatinIME.java` (Nachprüfung 51 N3): `onTrimMemory` ruft bei knappem Speicher zusätzlich
  `ForeignWords.closeDictionaries` (neben `RgbLighting.onTrimMemory`); beim Beenden schließt
  `AgentEditorController.destroy` sie. Zweite Nachprüfung 51: auch bei `TRIM_MEMORY_BACKGROUND`
  (eigener Fall) und in `resetSuggestMainDict` (Wörterbuch hinzugefügt oder entfernt), P207.
- Stand Etappe 51 nicht geändert (spätere Etappen: siehe oben): `InputLogic`, `RichInputConnection`, `PointerTracker`. Die Sprachbefehle schalten
  weiter über `switchToSubtype` (wie die Sprachtaste); gelernt wird über den Hook von 46
  (`Memory.onWordCommitted` reicht jedes Wort zuerst an `ForeignWords` weiter, vor den eigenen
  Sperren des Gedächtnisses) und `RejectedCorrections.remember` (P193); vergessen über
  `TypoMemory.confirm` und `FieldCorrectionWatcher.checkpoint` (`ForeignWords.onReplaced`).

Gedächtnis (Etappe 46, Details im Abschnitt „Lerndaten“ → Gedächtnis, P174–P177):

- `InputLogic.java`: `performAdditionToUserHistoryDictionary` ruft nach den eigenen Ausstiegen
  (unterdrückte Historie, privater Text, Befehl, keine Vorschläge im Feld, Inkognito, langsame
  Verbindung) und vor `addToUserHistory` `Memory.onWordCommitted` mit Wort, getippter Form (nur
  Tipp-Eingabe, nicht wiederaufgenommen), einem Lambda für den Text vor der Schreibmarke
  (`Memory.LOOKBACK`; gelesen erst, wenn das Gedächtnis lernen darf, Nachprüfung 46 N1) und
  Schreibmarke. Sonst unverändert; das Gedächtnis ändert keinen Text.
- `Suggest.kt`: `addFromMemory` direkt nach der Entscheidung über die automatische Korrektur
  (`hasAutoCorrection` steht dann fest): meldet `Memory.onTyped` (kennt ein Hauptwörterbuch das
  getippte Wort?), setzt ein eigenes Wort, das das getippte beginnt, als `KIND_COMPLETION` hinter
  die Korrektur (ohne Korrektur an den Anfang, nie als Korrektur) und bei Vorhersagen das nächste
  Wort des Gedächtnisses (`Memory.nextWord`, nur mit „Wortvorschläge“) an die erste Stelle.
  `guardAllows` und `applyLearnedCorrection` korrigieren ein eigenes Wort oder die Kurzform einer
  gemerkten Abkürzung (`Memory.isOwnWord`) nie automatisch; die Korrektur bleibt Vorschlag.
- `SettingsNavHost.kt`: Ziel `agent_memory` (`SettingsDestination.AgentMemory`, Seite
  `AgentMemoryScreen`).

Trefferzonen (Etappe 22a, Touch-Verarbeitung, jede Änderung mit Prüfer):

- `KeyDetector.java`: nach der normalen Erkennung `TouchModel.adjust`, nur wenn eingeschaltet.
- `KeyboardActionListenerImpl.kt` (88): `onCodeInput` meldet jeden Code an `UndoOffer.onCode` (eine Zeile, Wiederholen-Pfeil nach Rückgängig); `ToolbarUtils.kt` (88): `createToolbarKey` nimmt die Schaltfläche als Parameter (die feste Leiste gibt ihre `BarToolButton` mit); `KeyboardIconsSet.kt` (88): `CHAR_ART` nimmt `ic_agent_char_art`.
- `KeyboardActionListenerImpl.kt`: `onCodeInput` meldet Tasten an `TouchCalibration`, solange
  das Kalibrierfeld den Fokus hat (Ansicht- in Tastatur-Koordinaten über das Padding).
- `LatinIME.java`: `onStartInputViewInternal` lädt `TouchModel` und schaltet die Kalibrierung
  nur für das eigene Feld mit `AgentPreferences.TOUCH_CALIBRATION_FIELD` ein. Etappe 50: der
  Aufruf bekommt zusätzlich `editorInfo` und `restarting` (`TouchModel.load(…, editorInfo,
  restarting)`), weil die Einstellungen an dieser Stelle noch die des vorigen Felds sind (P165).
- `KeyboardActionListenerImpl.kt` (50): `onCodeInput` meldet jeden Code an
  `TouchModel.onKeyInput`, direkt nach dem Kalibrier-Hook und vor der Verarbeitung (Löschtaste,
  Rückgängig und Ausschneiden verwerfen die Berührungen des zuletzt abgeschickten Worts, jede andere
  Taste bestätigt sie). Seit der Nachprüfung 50 mit `isKeyRepeat` (gehaltene Löschtaste zählt
  einmal) und `keyboardSwitcher.keyboard` (Geometriewechsel, P182).
- `KeyDetector.java` bleibt in 50 unverändert: `TouchModel.adjust` rechnet über `offsetX`/`offsetY`
  jetzt Basis plus Kurzzeitschicht.

Befehle (Etappe 24a, Details im Abschnitt „Befehle“):

- `LatinIME.java`: `setSuggestedWords` und `setNeutralSuggestionStrip` zeigen zuerst die Chips
  eines getippten Befehls (`tryShowCommandChips`); `getAgentTextBeforeCursor`,
  `getAgentCommandCursor`, `replaceCommandText` für `KeyboardCommands`;
  `onStartInputViewInternal` lädt die Befehlseinstellungen; `onCreate`/`onDestroy` hängen
  `SensitiveClips.attach` an die Zwischenablage (eigener Listener, der Verlauf bleibt unverändert).
  Nachprüfung 44/45: `onPrivateTextInput` (wie `onTextInput`, merkt den Text danach über
  `InputLogic.markPrivateText`, P178) und `markAgentPrivateText`; `onFinishInputView` verwirft
  `MyDataCache` (eine Zeile).
  Etappe 42: `onEvent` gibt die Bildschirm-Enter-Taste zuerst an `KeyboardCommands.onEnter` (nur
  ein Befehl, der gerade ein Feld abfragt, nimmt sie; sonst unverändert), `moveAgentCursorBack`
  setzt nach einem Baustein die Schreibmarke über `RichInputConnection.setSelection` zurück (eigener
  Batch nach dem Ersetzen, P147), `canReplaceAgentCommand` (keine blockierte Eingabe, keine laufende
  Geste über den Getter `InputLogic.hasPendingBatchInput`) für `Host.canReplaceNow`.
  `KeyboardActionListenerImpl.kt` (42): `onKeyDown` gibt Hardware-Enter ohne Modifikator zuerst an
  `KeyboardCommands.onEnter`, noch vor der Hardware-Tastatur-Einstellung; nimmt der Befehl sie,
  werden Wiederholungen und `onKeyUp` derselben Taste mit verschluckt (`agentEnterDown`, P142).
- `Suggest.kt`: keine automatische Korrektur für ein Befehlswort und, solange ein vollständiger
  Befehl vor der Schreibmarke steht, für die Wörter seines Arguments
  (`KeyboardCommands.suppressesCorrection`); ebenso keine gelernte Korrektur.
- `InputLogic.java` (22b): `commitChosenWord` gibt die Tipp-Koordinaten des Worts an
  `TouchModel.learnFromWord` (nur mit Schalter und nur Tipp-Eingabe).

Flacher Stil (jeweils nur ein Aufruf in `FlatStyle`, Details im Abschnitt „Flacher Stil“):

- `KeyboardTheme.kt`: Stil `STYLE_FLAT` („Flat“, Theme-ID 5, immer mit Tastenhintergrund) und
  Farbsatz `THEME_FLAT_BLACK`. `attrs.xml`: Enum-Wert `Flat` = 5 bei `themeId` und
  `keyboardTheme`.
- `Key.java`: `KeyParams.mVerticalGapScale` (Lücke unter einer Taste je Taste skalierbar) und
  der Zustand `KeyBackgroundState.LAYOUT_SWITCH` (`state_single`) für die Tasten „?123“/„ABC“.
- `KeyboardParser.kt`: ruft im flachen Stil `FlatStyle.shortenNumberRow` auf; auf der zweiten
  Symbolseite (`SYMBOLS_SHIFTED`) gibt es im flachen Stil keine Zahlenreihe (23a). Seit 81 sind die
  Symbolseiten stilunabhängig, siehe „Symbolseiten wie Gboard (Etappe 81)“ weiter unten in diesem Abschnitt;
  `LayoutParser.parseLayout` hat den Parameter `nameOverride` aus 23a nicht mehr (wieder wie Upstream).
- `KeyboardView.java`: Schrift für Beschriftung und Hinweise, rechtsbündige Hinweise,
  angehobene Kleinbuchstaben, jeweils nur im flachen Stil. Dazu (23f2) ein Aufruf
  `DesignLook.load` nach dem Laden der vier Tastenhintergründe (Rundung und Schrift eines
  eigenen Designs, für alle Stile); `FlatStyle.typeface` nimmt zuerst die Design-Schrift.
- `MainKeyboardView.java`: Sprachkürzel auf der Leertaste mit Punkt als Trenner und Schrift,
  nur im flachen Stil.
- `KeyboardIconsSet.kt`: Icon-Satz für den Stil „Flat“ (Material plus `FlatStyle.iconOverrides`).
- `SuggestionStripView.kt`: im flachen Stil ordnet `FlatStrip.arrange` die Leiste, angeheftete
  Tasten bleiben neben der Toolbar sichtbar, die linke Taste öffnet das Menü
  (`FlatStrip.toggleMenu`) statt zwischen Toolbar und Vorschlägen zu wechseln, jede
  Toolbar-Taste schließt es. Auf den Symbolseiten zeigt `showRecentEmojis` statt der Werkzeuge
  eine Reihe mit sechs zuletzt benutzten Emojis (`FlatStrip.createEmojiRow`, aufgefüllt mit
  `FlatStrip.DEFAULT_EMOJIS`); ein Tipp fügt über `LatinIME.onTextInput` ein und ruft
  `RecentEmojis.add`.
- `KeyboardSwitcher.java`: `setKeyboard` meldet der Leiste, ob eine Symbolseite (SYMBOLS oder
  SYMBOLS_SHIFTED) offen ist (`showRecentEmojis`); ohne flachen Stil gibt es die Reihe nicht. Etappe 79b: danach ruft es
  `onKeyboardLayoutChanged` (die abgedunkelte Ablage in Zahlenlayouts, P444).
- Etappe 106: Schalter `AgentPreferences.CLIPBOARD_TOOLBAR` (`clipboardToolbarShown`, Standard aus, Einstellung in `ToolbarScreen`, Texte in `agent_bar_tools_strings.xml`);
  aus: `ClipboardHistoryView` legt keine Tasten an, `hidesStrip()` (Standardstil mit sichtbarem Streifen) lässt `KeyboardSwitcher.setClipboardKeyboard` den Streifen verstecken
  und gibt seine Höhe an `onMeasure` und die Liste (P507). `SuggestionStripView.gridTools` zählt die Tasten der abgeschalteten Zeile als „schon benutzt“.
- Zwischenablage: `ClipboardHistoryView.kt` (im flachen Stil Kopfzeile, Abschnittstitel, Liste
  bis zum unteren Rand, keine untere Tastenreihe, keine Hardware-Ebene), `ClipboardAdapter.kt`
  (Kartenstil, quadratische Bilder), `ClipboardHistoryEntry.kt` und `ClipboardHistoryManager.kt`
  (Bilder über `ClipboardThumbnails`, Chip im flachen Stil), jeweils als Aufruf in
  `FlatClipboard`. `ClipboardHistoryManager.getClipboardSuggestionView` bietet zuerst einen
  frischen Screenshot an (`getScreenshotSuggestionView`, nur zwischen den Wörtern nach
  `screenshotMayReplaceStrip`, 23g);
  `hideSuggestionView` blendet einen Chip aus, ohne den Clip als erledigt zu markieren.
  `onCreate` setzt `ScreenshotChip.onChange` auf `onScreenshotChange` (Verlaufseintrag, dann Pille
  sofort zeigen oder nach Ablauf entfernen) und startet den Observer; `onDestroy` ruft
  `ScreenshotChip.stop` (Observer abmelden, ausstehende Meldung, Entprellen und Ablauf entfernen).
  Etappe 44 (Zwischenablage-Wächter, Abschnitt „Speicher, Zugangsdaten und Datenschutz“):
  `ClipboardHistoryManager.fetchPrimaryClip` gibt jeden Text-Clip zuerst an `ClipVault.offer`
  (vertraulich = `IS_SENSITIVE` oder Passwortfeld) und schreibt, was `ClipClass.keepsOutOfHistory`
  sagt (Geheimnis, Code allein), nie in `ClipboardDao`; eine Nachricht mit Code bleibt Verlaufstext.
  `getClipboardSuggestionView` zeigt keinen Chip, wenn `ClipVault.isSecretClip` (Markierung,
  `SensitiveClips`, vom Tresor als Geheimnis sortierte Kopierzeit, Inhalt) zutrifft (vorher
  Sternchen). Alte nicht angeheftete Geheimnisse (auch per `SensitiveClips`; Codes bleiben) verlassen
  den Verlauf einmal je App-Version (`PREF_OLD_HISTORY_CLEANED` = Versionscode, P186): `onCreate`
  (Verlauf an) sortiert eine Kopie der Texte in einem Hintergrund-Thread (`startOldHistoryCleanup`),
  gelöscht wird auf dem Hauptthread bei der nächsten `getClipboardSuggestionView`,
  `onPrimaryClipChanged` oder `prepareClipboardHistory` (`applyOldHistoryCleanup`, nur ohne
  offene Verlaufsansicht); war es beim Öffnen noch nicht geschehen, sortiert
  `prepareClipboardHistory` einmal selbst (`removeVaultClipsFromHistory`). `onCreate` ruft vorher
  `SensitiveClips.migrateLegacy` (ohne `ClipboardDao`, etwa vor dem ersten Entsperren: nichts tun,
  Datei bleibt) und nach einer Löschung `ClipboardDao.compactInBackground`; `clearHistory` leert den
  Tresor; `onCreate` setzt `ClipVault.currentClip`. `ClipboardDao.delete`/`clear` löschen über
  `deleteRows` (`PRAGMA secure_delete = ON` in derselben Transaktion), danach
  `checkpointInBackground` (`wal_checkpoint(TRUNCATE)` auf dem Thread `maintenance`, Löschungen
  kurz hintereinander teilen sich einen), `compactInBackground` = einmal `VACUUM` auf `maintenance`
  ohne die Sperre des Objekts und nur bis `COMPACT_MAX_BYTES` = 8 MiB Dateigröße (Nachprüfung 44/45,
  P179).
  `ClipboardHistoryView.startClipboardHistory`
  ruft `refreshVault` (Tresorreihen über der Liste, Liste um ihre Höhe kürzer, `ClipVault.onChange`
  bis `stopClipboardHistory`), `onVaultPick` fügt per `onPrivateTextInput` ein (P178). `ClipboardAdapter.setContent`
  beschriftet persönliche Clips und verdeckt alte angeheftete Geheimnisse
  (`ClipVaultSection.historyText`, Urteil je Eintrags-ID gemerkt).
  Etappe 40 (Smart Paste, Abschnitt „Befehle“ → Smart Paste, P203): `getClipboardSuggestionView` gibt
  bei Text die Pille an `SmartPasteChips.stripRow` (mit `imeHost(latinIME, editorInfo)`); gibt es
  Chips, ist die zurückgegebene Ansicht die Reihe aus Pille und Chips, und der Tipp auf die Pille
  blendet diese Reihe aus (`shownView`), nicht nur die Pille; ein einfügender Chip ruft danach
  `removeClipboardSuggestion`. `ClipboardAdapter.setContent` ruft für jede Karte
  `SmartPasteChips.bindHistoryRow` (Feld `smartPaste`), `ClipboardHistoryView.startClipboardHistory`
  setzt dafür eine `SmartPasteChips.History` für das Feld dieser Ansicht (`onSmartInsert(text,
  privateText)`: Code und IBAN wie `onVaultPick` über `onPrivateTextInput`, P178, ein bereinigter
  Link wie `onKeyUp` einer Karte über `onTextInput`, P206), `stopClipboardHistory` nimmt sie weg. Keine neue
  Upstream-Datei, kein Layout geändert (die Reihe entsteht im Code in der Textspalte der Karte).
- `LatinIME.java` (23g): `setSuggestedWords` ruft nach `tryShowCommandChips` und nach
  `mInputLogic.setSuggestedWords` `tryShowScreenshotChip` (blendet bei `mAutoHideToolbar` die
  Leiste aus wie der Zwischenablage-Chip); die Pille ersetzt also nur die Anzeige, die
  Vorschläge für die Autokorrektur bleiben in `InputLogic`. `onInlineSuggestionsResponse` zeigt
  Autofill über `showInlineAutofill` und merkt sich die View; `isShowingInlineAutofill` sagt, ob
  sie noch in der Leiste hängt (`removeExternalSuggestions` vergisst sie). `InputLogic` selbst ist
  unverändert.
- Etappe 79 (Autofill-Chips robust): `latin/utils/InlineAutofillUtils.java` (neu in der Liste der geänderten
  Upstream-Dateien) und `LatinIME.java`; die neue Datei `A/InlineAutofill.kt` enthält die drei reinen Helfer
  `InlineAutofillSizing`, `InlineAutofillSlots` und `InlineAutofillGeneration` (kein Android, kein Text der Vorschläge). `createInlineSuggestionRequest(context, stripWidthPx)` nimmt die Breite
  aus `A/InlineAutofillSizing` (Leistenbreite oder Anzeigebreite, sonst feste dp; die Mindestbreite skaliert mit der
  Dichte; weiter drei gleiche `InlinePresentationSpec`). `createView(…, BooleanSupplier isCurrent)` setzt jedes
  fertige Chip-Bild über `A/InlineAutofillSlots` an die Stelle seines Index und prüft vorher `isCurrent`.
  `LatinIME` hält eine `A/InlineAutofillGeneration`: `open()` (nur Wiederöffnen) in `onCreateInlineSuggestionsRequest` und am Anfang von
  `onStartInputInternal`, `close()` in `onFinishInputInternal`, `invalidate()` in `removeExternalSuggestions`,
  `beginResponse()` je nicht leerer Antwort; ohne aktive Eingabe oder ohne Leiste liefert
  `onInlineSuggestionsResponse` `false`. Tests: `AT/InlineAutofillSizingTest.kt`, `AT/InlineAutofillSlotsTest.kt`,
  `AT/InlineAutofillGenerationTest.kt` (P318–P320). `InputLogic`, `RichInputConnection` und `PointerTracker` sind unberührt.
- Etappe 79b (Autofill ohne leeren Kasten, Diagnose): `A/InlineAutofill.kt` bekommt `InlineAutofillResponse` (Reihe der Chips, Aktion „erster Chip“, Zähler je Antwort) und
  `InlineAutofillCounters` (Summen und letzte Antwort, `shared`); `InlineAutofillGeneration.isOpen`. `InlineAutofillUtils.createView(…, InlineAutofillResponse)` startet die Rückrufe erst, wenn der Container steht.
  `LatinIME`: `onInlineSuggestionsResponse` zeigt den Kasten (`showInlineAutofill`) nur aus der Aktion „erster Chip“, zählt leer/abgelehnt/angenommen; `dump()` druckt `InlineAutofillCounters.line()`;
  `openInlineAutofillGeneration()` hält `generationOpen` der Zähler. Diagnose: `Diagnose.autofill` (Part `AUTOFILL`, vor „App und Telefon“) und `AgentDiagnoseScreen`. Tests: `AT/InlineAutofillResponseTest.kt`, `AT/InlineAutofillCardTest.kt` (P440–P443).
- Etappe 87b (Trackpad sichtbar machen, auf der Regel von 87c): neue Datei `A/CursorTrackDiag.kt` mit `CursorTrackCounters` (Zähler je Taste, `shared`), `CursorTrackVerdict` (gilt die Einstellung für Leertaste und Löschtaste, mit Grund; `space` ruft `CursorTrackpad.spaceReason`) und
  `CursorTrackText` (die Worte für Statuszeile und Karte). `A/CursorTrackpad.kt` bekommt die Feldart-Hilfen (`isTextClass`, `hasMultiLineFlag`, `isMultiLine`).
  `KeyboardActionListenerImpl` ruft die Zähler an den Entscheidungsstellen des Trackpads (siehe P451). Diagnose: `Diagnose.trackpad` (Part `TRACKPAD`), Karte in `AgentDiagnoseScreen`;
  `AgentAreaGesturesScreen`: zwei Statuszeilen unter dem Schalter (Tags `TRACKPAD_SPACE_TAG`, `TRACKPAD_DELETE_TAG`). Texte: `values*/agent_trackdiag_strings.xml`.
  Tests: `AT/CursorTrackDiagTest.kt`, `InputLogicTest` `cursor87b…` (P450–P454). `InputLogic`, `RichInputConnection` und `PointerTracker` sind unberührt.
- `KeyboardActionListenerImpl.kt`: `KeyCode.AGENT_ACTIONS` öffnet im flachen Stil das Panel statt
  des Rads.
- `InputView.java`: zählt `agent_menu` zu den Overlays. `SettingsActivity.kt`: Extra
  `AgentPreferences.OPEN_SETTINGS_DESTINATION` öffnet „appearance“ oder „toolbar“ direkt.

RGB-Beleuchtung (Etappe 26, jeweils nur Aufrufe in `A/RgbLighting.kt` bzw. `RgbKeyPainter`;
ohne Einstellung ist der Painter `null` und das Zeichnen genau wie im Upstream):

- `KeyboardView.java`: Feld `mRgbPainter` mit `setRgbPainter`/`getRgbPainter`; in
  `onDrawKeyboard` `beginDraw` und `drawKeyboardBackground` (voller und Teil-Neuzeichnen), in
  `onDrawKey` `beginKey` und `tintBackground`/`restoreBackground` um den Tastenhintergrund, in
  `onDrawKeyBackground` direkt nach `background.draw` `drawOverBackground` (Licht und Textur durch
  die Form der Taste, 29), in
  `onDrawKeyTopVisuals` `labelColor` für Beschriftung (nicht Emoji), Hinweis und Hinweis-Symbol,
  in `drawKeyPopupHint` für das „…“, sowie `tintIcon`/`restoreIcon` um das Symbol.
  `setKeyIconColor` gibt den gesetzten `ColorType` zurück, damit `tintIcon` die wirklich
  gesetzte Farbe bekommt (Umschalt-Akzent, Tastentext). Am Ende von `onDrawKeyboard` `endDraw`
  (Messung der Bildkosten, Rückmeldung zu Build 14).
- `MainKeyboardView.java`: `setKeyboard` holt den Painter (`RgbLighting.painterFor`, liest dabei
  die Einstellung neu), `onKeyPressed` meldet die Taste (`RgbLighting.onKeyPressed`, nur Position
  der Taste, nichts aus `PointerTracker`), `onAttachedToWindow`/`onVisibilityChanged`/
  `onWindowVisibilityChanged`/`onSizeChanged` (`RgbLighting.onVisibilityChanged`),
  `onDetachedFromWindow` (`onDetached`) und `onHideWindow` starten oder stoppen die Bilder; das
  Sprachkürzel auf der Leertaste nimmt `labelColor` nach seinem Alpha.
- `SuggestionStripView.kt`: `updateKeys` (nach jedem Aufbau und jeden neuen Vorschlägen) ruft
  `RgbLighting.onStripChanged`.
- `LatinIME.java` (Prüfung 29): `onTrimMemory` ruft bei knappem Speicher `RgbLighting.onTrimMemory`
  (verwirft die Texturkacheln) und seit der Nachprüfung 51 `ForeignWords.closeDictionaries`.
- `SettingsNavHost.kt` (Ziel `agent_rgb`) und `AppearanceScreen.kt` (Eintrag „RGB-Beleuchtung“
  nach „Design“, `createAgentRgbSetting`).

Speicher, Datenschutz und Identität:

- `App.kt`: bewahrt den CE-Kontext (`credentialStorageContext`), bevor die Engine einen
  DE-Kontext bekommt. Kein Paketmanager-Startlog mehr (Fallstrick P8).
- `SettingsActivity.kt`, `EmojiSearchActivity.kt`, `AndroidSpellCheckerService.java`: nutzen für
  Upstream-Daten ausdrücklich den DE-Kontext. `SettingsActivity.kt` öffnet mit dem Extra
  `AgentPreferences.OPEN_AI_SETTINGS` direkt die KI-Einstellungen.
- `ClipboardDao.kt` und `gesturedata/Share.kt`: teilen Dateien über `AgentFileProvider`.
- `SettingsValues.java` (`mLearningPaused` aus `AgentPreferences.LEARNING_PAUSED`),
  `DictionaryFacilitatorImpl.kt` (`addToUserHistory` speichert bei Pause nichts; die beiden
  Sperrlisten-Fehlermeldungen loggen das Wort nicht mehr) und `ExpandableBinaryDictionary.java`
  (`readAllWordProperties`, `clearAndWait`): Lernpause und Lerndaten, siehe „Lerndaten“. Stufe 65 (S6):
  die Fehlerzeilen von `ExpandableBinaryDictionary.java` und `Ver4DictEncoder.java` nennen kein Wort
  und keinen Wortkontext mehr; `LayoutUtilsCustom.kt`: drei Warnzeilen nennen die Beschriftung der Taste nicht mehr.
- `ClipboardHistoryManager.kt` (65, S2/S8): `fetchPrimaryClip` fragt vor jedem Speichern, Text wie
  Datei, `ClipVault.mayKeep` (eine Regel für Tresor und Verlauf); `onDestroy` leert Tresor und
  Verweise (P270, P271). `LatinIME.java` (65, S12): `onFinishInputInternal` und
  `onFinishInputViewInternal` rufen je `ClipVault.fieldFinished(mInputLogic.mFieldCorrections)` (P272).
  Zwei Logzeilen ohne Inhalt: `onEmojiSearchDone` (gewähltes Emoji) und siehe P274.
- `BackupRestorePreference.kt` (65, S3/S4): Ordner-Paar als `RestoreFolders`, Einstellungen über
  `restoreWithRollback` (P273). `SettingsActivity.kt` (65, S7): die von außen übergebene
  Wörterbuchdatei wird einmal je Intent (nicht bei Drehung) in einem eigenen Thread in eine Datei mit
  eigenem Namen kopiert; Zustand im Companion, Fehler (`SecurityException`, kein Stream) zeigen
  `dictionary_load_error`; das Dialogfenster folgt nach dem Kopieren.
- `Defaults.kt`: personalisierte Wörterbücher, Zwischenablage-Vorschläge und
  Zwischenablageverlauf standardmäßig aus.
- `SubtypeUtils.kt` (Korrektur nach Prüfer zu 30): `SubtypeState.getSubtypeForLocales` hat den
  Parameter `peek` (Standard `false`, `@JvmOverloads`); mit `true` liefert sie dasselbe Ergebnis,
  ändert aber `overriddenByLocale` nicht. Nur für `SubtypeChoice.holds` (P98).
- Emoji-Wörterbücher (Wunsch des Owners vom 2026-11-13): `assets/dicts/emoji_de.dict`,
  `emoji_en.dict`, `emoji_es.dict`, unverändert aus `Helium314/aosp-dictionaries`
  (`emoji_cldr_signal_dictionaries`, Stand 2026-09-22, Lizenz Unicode License V3 für CLDR und
  AGPL-3.0 für die Signal-Daten; SHA-256 in `docs/BUILD.md`). `DictionaryInfoUtils.kt`:
  `getCachedDictForLocaleAndType` entpackt für `emoji` die passendste mitgelieferte Datei, wenn
  noch keine im Cache liegt (davon hängen Emoji-Vorschläge, Inline-Suche, Emoji-Suche und die
  Warnung „Ein Emoji-Wörterbuch wird benötigt“ ab). `DictionaryFactory.kt`: mitgelieferte
  Emoji-Wörterbücher werden nur geladen, wenn die Emoji-Einstellung an ist, wie heruntergeladene.
  `extractAssetsDictionary` schreibt jetzt in eine temporäre Datei unter `files/dicts_extracting/`
  (außerhalb der durchsuchten Locale-Ordner, P80) und benennt sie um; `finally` löscht sie bei
  jedem Fehler; ein schon entpacktes `emoji.dict` wird wiederverwendet (Fallstrick P52). `DictionaryUtils.kt`
  (`getDictionaryLocales`, Aufräumen) und `DictionaryScreen.kt` zählen ein Emoji-Wörterbuch nicht
  als eigene Sprache bzw. als internes Hauptwörterbuch. Test: `BundledEmojiDictTest`.
- Tolerante Emoji-Vorschläge (Etappe 35, Fallstricke P115–P118): `Suggest.kt`
  (`getSuggestedWordsForNonBatchInput`, nach der Entscheidung über die automatische Korrektur und
  vor dem Einfügen des getippten Worts) ruft `A/EmojiSuggestions.addTo`. Das setzt bis zu zwei
  Emojis (zusammen mit denen des Emoji-Wörterbuchs) an Index `min(2, size)` des Containers, hinter
  HeliBoards eigene Emojis, also dorthin, wo `makeFirstTwoSuggestionsNonEmoji` Emojis hinschiebt;
  bei anstehender Korrektur nie an Index 0. Quelle `EmojiSuggestions.DICTIONARY`
  (`PhonyDictionary` vom Typ `emoji`). Nur beim Tippen (nicht Gesten, nicht Vorhersagen nach dem
  Leerzeichen), nur mit „Emojis vorschlagen“, nicht in Passwortfeldern, bei Inkognito (auch
  `IME_FLAG_NO_PERSONALIZED_LEARNING`), bei gesperrtem Gerät (erst nach einem Treffer gefragt,
  höchstens alle 2 s, weil `isDeviceLocked` ein Systemaufruf ist) oder für ein Befehlswort.
  „Echtes Wort“ heißt: die Ergebnisse des Wörterbuchs enthalten das getippte Wort; dann keine
  Wortanfänge, keine Tippfehler und nur die Sprache dieses Wörterbuchs (`mSourceDict.mLocale`,
  P118). `EmojiSuggestions` liest `assets/emoji_keywords.bin` einmal auf
  einem eigenen Daemon-Thread (`ensureLoading`, beim ersten getippten Wort über
  `Settings.getCurrentContext`); bis dahin kommt nichts dazu. `setTableForTest` für Tests.
  Kern: `C/EmojiKeywords.kt` (Format lesen, Index je Sprache: gefaltete Stichwörter mit Varianten
  „käse“/„kaese“, Stämme, sortierte Schlüssel; `suggest` in fünf Stufen exakt, Synonym, Wortform,
  Wortanfang ab 4 Buchstaben, ein Tippfehler ab 4 Buchstaben mit gleichem Anfangsbuchstaben;
  Rangfolge `group`: zuerst das Emoji, das das Wort benennt, und ein sehr häufiges Emoji, in
  dessen Namen das Wort steht; dann ganze Stichwörter (häufige zuerst), zuletzt Wörter aus
  mehrteiligen Stichwörtern; danach `POPULAR`, wenige Stichwörter, Reihenfolge der Emoji-Ansicht.
  Wörter aus mehrteiligen Stichwörtern („zu viel gegessen“) zählen nur exakt, ab 5 Buchstaben,
  nie für Stämme, Wortanfänge oder Tippfehler (`strong`); `STOPWORDS` und allgemeine Stichwörter
  mit über 40 Emojis ohne eigenen Namen geben nichts), `C/EmojiSynonyms.kt` (feste Liste Umgangssprache und
  Chat-Kürzel). Sprachen: Hauptsprache, dann Deutsch und Englisch. Generator
  `tools/emoji_keywords.py` (Quelle, Summen: `docs/BUILD.md` → Emoji-Stichwörter). Tests
  `CT/EmojiKeywordsTest.kt` (mit Zeitmessung), `SuggestTest.emoji*`,
  `InputLogicTest.typedEmojiSuggestions*`.
- `DebugFlags.kt`: privater Crashhandler entfernt, damit keine rohen Fehlermeldungen oder
  LastLog automatisch in External/DE gespeichert werden. Androids Standardhandler bleibt.
- `AndroidManifest.xml`: `INTERNET`, `RECORD_AUDIO` (nur für das Diktat, siehe „Diktat“),
  `READ_MEDIA_IMAGES` bzw. bis Android 12 `READ_EXTERNAL_STORAGE` (nur für die Screenshot-Pille;
  `READ_MEDIA_VISUAL_USER_SELECTED`, damit „nur ausgewählte Fotos“ erkannt wird), kein
  Klartextverkehr, Backups aus
  (`agent_data_extraction_rules.xml`), CE als Standard, eigener FileProvider, der
  AndroidX-Startup-Initializer für HTTP ist entfernt.
- `res/xml/spellchecker.xml`, `AndroidManifest.xml` (Namen) und `SettingsActivity.kt` (Titel der
  Rechtschreib-Einstellungen): zeigen auf `agent_spell_checker_service_name` bzw.
  `agent_android_spell_checker_settings` statt auf die Upstream-Texte mit „HeliBoard“ (57, P241).
- `res/drawable/ic_launcher_foreground.xml`, `res/drawable/ic_launcher_monochrome.xml` (neu),
  `res/drawable-v24/ic_launcher_background.xml`, `res/mipmap-anydpi-v26/ic_launcher{,_round}.xml`
  und `res/mipmap-*/ic_launcher{,_round}.png`: das Logo SecondHand (58, P243).
- `res/xml/clipboard_provider_path.xml`: zusätzliche Pfade `agent_qr/` (`@qr`), `agent_emoji_mix/`
  (Emoji-Mix-Cache, 25a) und `agent_screenshot/` für die Kopien
  der Screenshot-Pille, getrennt von den Clip-Dateien (deren Aufräumen hielte ein Unterordner
  sonst für verwaist).
- `SettingsNavHost.kt` und `MainSettingsScreen.kt`: Ziele der Agent-Einstellungsseiten (dazu
  `agent_design`) und seit 38a die sieben Bereichs-Routen `area_*` (`SettingsDestination.Area*`,
  je ein `composable` aus `SettingsArea.entries`). `MainSettingsScreen(onNavigate, onClickBack)`
  zeigt für jeden Tastenstil die neue Startseite `AgentStartScreen`; die Upstream-Liste der
  Einträge gibt es nicht mehr, jeder Eintrag steht auf einer Bereichsseite (`SettingsAreas`).
- `SearchScreen.kt` (23e): optionaler Parameter `largeTitle` vor `content` (Fallstrick P46) und
  `LARGE_TITLE_BAR_TAG`; ohne ihn unverändert. Seit 38a nutzt ihn keine Seite mehr (blieb für
  spätere Seiten stehen). `ExpandableSearchField` nutzt auch die neue Startseite.
- `AppearanceScreen.kt` (23f1): Eintrag „Design“ als erstes Element. `KeyboardTheme.kt`:
  `getPreviewColors` für die Kacheln (`withBackgroundImage = false`: kein Dekodieren des
  Hintergrundbilds je Kachel).
- `AndroidManifest.xml` (24e): Dienst `ScreenReaderService` mit `BIND_ACCESSIBILITY_SERVICE`.
- `AndroidManifest.xml` (`@wecker`/`@timer`): `com.android.alarm.permission.SET_ALARM` (normale
  Berechtigung, bei der Installation erteilt, keine Nachfrage).
- `AndroidManifest.xml` (Update-Prüfung): `POST_NOTIFICATIONS`, `REQUEST_INSTALL_PACKAGES`, seit 56
  `ACCESS_NETWORK_STATE` (normale Berechtigung, bei der Installation erteilt: die Prüfung beim Öffnen
  fragt vorher, ob ein validiertes Netz da ist), Dienst
  `UpdateCheckJobService` (`BIND_JOB_SERVICE`) und Empfänger `UpdateInstallReceiver`, beide nicht
  exportiert und nicht `directBootAware`. `SettingsNavHost.kt`/`MainSettingsScreen.kt`: Ziel und
  Eintrag „Updates“ (`agent_updates`, auch in `AgentPreferences.SETTINGS_DESTINATIONS`).
- Update-Fenster (54, Abschnitt „Updates“): `LatinIME.java` `tryShowUpdateChip` (eine Zeile in
  `setNeutralSuggestionStrip` nach der Zwischenablage-Pille, sonst wie `tryShowScreenshotChip`);
  `SettingsActivity.kt` ein Zweig `AgentUpdatePrompts(onUpdatesPage = …)` nach Willkommen und
  Absturzberichten, vor der Gestendaten-Erinnerung; `app/build.gradle.kts` Task
  `EmbedReleaseNotes` (je Variante `embed<Variante>ReleaseNotes`, über
  `variant.sources.assets.addGeneratedSourceDirectory`).
- `SettingsNavHost.kt` (45): Ziel `agent_my_data` (`SettingsDestination.AgentMyData`, Seite
  „Meine Daten & Vorschläge“, auch in `AgentPreferences.SETTINGS_DESTINATIONS`).
- App-Identität: `app/build.gradle.kts` (App-ID `dev.personal.agentkeyboard`, Version 0.1.0,
  APK-Name, Lint-Baseline `lint-baseline-upstream.xml`), `settings.gradle`
  (`:agent-core`), `clip_provider.xml` und `gesture_data.xml` (eigene Provider-Authorities,
  auch in `debug`/`debugNoMinify`), `donottranslate.xml` und `strings.xml` in
  `debug`/`debugNoMinify` (seit 57 alle drei Namen ohne „Lab“, P242).
- `values-de/strings.xml` (38f): deutsche Texte von HeliBoard sagen „Leiste“ statt „Symbolleiste“
  (Modus, Tasten, Anheften, Hinweise, Gestendaten); nur Wortlaut, keine neuen oder entfernten
  Schlüssel. Stufe 65/69: `backup_restore_message` (Deutsch und Englisch, `values/strings.xml`) sagt, was in der Sicherungsdatei steht, was
  nicht und was ein Wiederherstellen entfernt; `app_slogan` mit `formatted="false"` (kein Formatstring); `values-de/strings-talkback-descriptions.xml`
  (69): fehlende deutsche Namen für die Sprachausgabe (Zwischenablage-Vorschlag, Modi, Emoji-Kategorien).

Symbole malen (Etappe 27, Touch-Verarbeitung, jede Änderung mit Prüfer; Details im Abschnitt
„Symbole malen“):

- `PointerTracker.java`: nur für Folgestriche eines wartenden Symbols. `onDownEvent` fragt nach
  dem Rauschfilter `startsInk` (wartendes Symbol laut `SymbolInk.continuesInk`, Gestenerkennung
  erlaubt, kein anderer Zeiger aktiv, Alphabet, eine Taste, die ein Zeichen schreibt:
  `SymbolInk.startsInkOn`, 36; vorher nur Buchstaben): dann `mInInk`, kein
  Tastendruck, kein Eintrag in die Zeigerwarteschlange, Spur über `showGestureTrail`.
  `onMoveEvent` (`moveInk`, auch historische Punkte), `onUpEvent` und `onCancelEvent` prüfen
  `mInInk` zuerst. Ein weiterer Finger während eines Folgestrichs beendet ihn
  (`SymbolInk.onSecondFinger`, P104): Ist der Strich noch ein Tipp, ist das Symbol damit
  geschrieben und `inkTapBecomesKey` macht den ersten Finger zu einem normalen Tastendruck; ein
  echter Strich verwirft die Zeichnung (`SymbolInk.cancel`). Der weitere Finger wird normal
  verarbeitet; `moveInk` zeichnet nach dem Verwerfen keine Spur mehr (31).
  31: Meldet `SymbolInk.onInkUp` einen Tipp, der kein Punkt ist (`true`, das Symbol ist dann schon
  geschrieben), holt `onUpEvent` den normalen Down nach (`sPointerTrackerQueue.add`,
  `onDownEventInternal` an der Aufsetzstelle `mInkDownX/Y`) und läuft weiter durch den normalen
  Up-Pfad (P96).
  `mUpTime` bleibt im Tintenzweig ungesetzt wie im normalen Pfad (P95). Gestenerkennung,
  Batch-Aufrufe, P5-Sperre: unverändert.
  36 (Review von 057a6d5, P110): Ein zweiter Finger macht den zeichnenden Finger nur dann zum
  Tastendruck, wenn dessen Tracker noch `mInInk` hat, sonst `SymbolInk.cancel`; `onCancelEvent`
  beendet auch einen Folgestrich eines anderen Trackers (`SymbolInk.isDrawing`, dessen `mInInk`
  aus); `moveInk` zeichnet nur für den Zeiger, der laut `SymbolInk.drawingPointer` malt.
  36 nach Prüfer: `startsInk` prüft zuerst `SymbolInk.isWaiting` (ohne wartendes Symbol wie vorher
  nichts) und zuletzt `SymbolInk.continuesInk(x, y, mKeyboard.mMostCommonKeyWidth)` (nur nahe der
  Zeichnung, H1); bei zweitem Finger und zeichnendem Tracker ruft es `onSecondFinger` und macht den
  Tracker nur bei einem Tipp zur Taste, ein Strich ist dann schon geschrieben (M1).
  43: `init` setzt `SymbolInk.tapper` auf das statische `typeInkTap` (ein möglicher Punkt, der
  Buchstabe ist: Druck, Code, Loslassen über den Listener, ohne Tracker-Zustand, P151).
  `onDownEvent` gibt bei Symbole malen `SymbolInk.gestureTypingTime` statt der letzten
  Tippzeit an `addDownEventPoint` (HeliBoards Schnelltipp-Sperre aus) und merkt
  `mInkAfterTyping`/`mGestureDownX/Y`; `onGestureMoveEvent` startet dann erst ab
  `SymbolInk.startsAfterTyping` (0,75 Tastenbreiten vom Aufsetzpunkt, P153). Ohne Symbole malen
  unverändert; `GestureStrokeRecognitionPoints`/`-Params` unverändert.
  43 nach Prüfer: `onDownEvent` lässt die Sperre an und `mInkAfterTyping` aus, wenn ein weiterer
  Zeiger in der Warteschlange ist (zwei Daumen, M1a); `onGestureMoveEvent` startet mit
  `mInkAfterTyping` nur allein und meldet den Start mit `SymbolInk.onStartAfterTyping` (Zeiger,
  Aufsetzpunkt, Tastenbreite, M1b). `typeInkTap` tippt nur Tasten laut `SymbolInk.startsInkOn`
  (N1; damit entfallen die Zweige für `MULTIPLE_CODE_POINTS`/`NOT_SPECIFIED`, deren Codes ≤ 0
  sind) und meldet `onPressKey` immer mit einem Zeiger (N2).
- `TextCorrectionScreen.kt` (36, Prüfer N4): Bestätigt man das Ausschalten von „Personalisierte
  Vorschläge“, löscht der Dialog auch die persönlichen Vorlagen von Symbole malen
  (`SymbolDrawing.clearPersonalSymbols`), eine Zeile; seit der Nachprüfung 44/45 auch die Gewichte
  der Kontext-Vorschläge (`ContextLearning.clear`), eine Zeile.
- `KeyboardActionListenerImpl.kt`: die vier Batch-Methoden gehen bei eingeschaltetem
  `mSymbolDrawing` an `SymbolInk` statt an `LatinIME` (LatinIME, InputLogic und
  `PendingBatchGate` sehen davon nichts); `onPressKey`, `onCodeInput`, `onTextInput` rufen zuerst
  `SymbolDrawing.beforeOtherInput`; Feld `symbolSink` hält die Senke stark.
- `SettingsValues.java`: `mSymbolDrawing` (`AgentPreferences.SYMBOL_DRAWING`, Standard aus);
  `mGestureInputEnabled` ist dann aus.
- `LatinIME.java` (31): `pickSuggestionManually` ruft zuerst `SymbolDrawing.beforeSuggestionPick`
  mit `mInputLogic.hasActiveComposition()` (P97).
- `LatinIME.java`: `setGestureHandlingEnabledByUser` mit `mGestureInputEnabled || mSymbolDrawing`
  (Spur immer, schwebender Wort-Text nie beim Malen); `tryShowSymbolChips` nach den Befehlschips
  in `setSuggestedWords` und `setNeutralSuggestionStrip`; `onStartInputViewInternal` ruft
  `SymbolDrawing.reset`.
- `MainSettingsScreen.kt` (Eintrag „Symbole malen“, auch ohne Gesten-Bibliothek),
  `SettingsNavHost.kt` (Ziel `agent_symbols`), `SettingsContainer.kt`
  (`createAgentSymbolSettings`), `GestureTypingScreen.kt` (Schalter und Eintrag oben, nur mit
  Gesten-Bibliothek sichtbar; 31: bei eingeschaltetem Malen ist „Gestenschreiben“ ausgegraut mit
  Hinweis `agent_symbols_gesture_off`, der gespeicherte Wert bleibt, die Gesten-Optionen außer der
  Ausblendzeit der Spur sind ausgeblendet).
- Diagnose (30): `MainSettingsScreen.kt` (Eintrag „Diagnose“ nach „Erweitert“, Parameter
  `onClickAgentDiagnose`), `SettingsNavHost.kt` (Ziel `agent_diagnose`).
Zeichen-Kunst (Etappe 53, Abschnitt „Zeichen-Kunst“; kein `InputLogic`, `RichInputConnection` oder
`PointerTracker` geändert):

- `TextKeyData.kt` (bis dahin unverändert): `getAdditionalPopupKeys` gibt für das Label
  `toolbarKeyStrings[ToolbarKey.EMOJI]` (die Emoji-Taste jedes Funktionslayouts, auch eigener)
  `CharArtKey.popupKeys(params.mId)` zurück: der Stift „✎“ mit `!code/-12006`, nicht in
  Passwortfeldern, ohne Lernen (`IME_FLAG_NO_PERSONALIZED_LEARNING`) und bei gesperrtem Gerät. Ein
  eigenes Popup der Taste wird angehängt (`merge`), nicht ersetzt. Langdruck, Popup-Anzeige und
  Auswahl laufen unverändert durch HeliBoards `PointerTracker`/`PopupKeysKeyboardView`.
- `KeyCode.kt`: `AGENT_CHAR_ART` = -12006 (in `checkAndConvertCode` durchgereicht).
- `KeyCodeDescriptionMapper.kt` (bis Nachprüfung 53 unverändert): TalkBack-Beschreibungen für
  `AGENT_ACTIONS` („Textaktionen“), `AGENT_DICTATE`, `AGENT_EMOJI_MIXER` und `AGENT_CHAR_ART`
  (`agent_char_art_key_description`); ohne sie las TalkBack den Stift als „✎“. Die Skill-Tasten
  behalten ihr eigenes Label, das den Skill nennt.
- `KeyboardActionListenerImpl.kt`: `onCodeInput` mit `AGENT_CHAR_ART` →
  `AgentEditorController.openCharArt` (eine Zeile neben den anderen Agent-Codes).
- `InputView.java`: `agent_char_art` zählt als Overlay (Touch und Hover gehen an das Fenster).
- `main_keyboard_frame.xml`: `CharArtView` als weiteres Kind von `AgentKeyboardFrame`.
- Geänderter Upstream-Test `KeyboardParserTest.kt` (bis dahin unverändert): `emojiKey` erwartet jetzt
  genau das Popup „✎“ mit `AGENT_CHAR_ART` (vorher keines). Das Popup macht „✎“ auch zum
  Hinweis-Label jeder Emoji-Taste (mit „Tasten-Hinweise anzeigen“); Entscheidung des Leads in der
  Nachprüfung: bleibt, es zeigt den Langdruck an (P196).

Ablage (Etappe 74, Abschnitt „Ablage“; kein `InputLogic`, `RichInputConnection` oder
`PointerTracker` geändert):

- `TextKeyData.kt`: `getCommaPopupKeys` hängt nach der Zwischenablage-Taste (`key_clipboard`) und vor
  der Emoji-Taste `PromptsKey.POPUP_KEY` an, wenn `PromptsKey.shown(params.mId)`. Die Reihenfolge ist
  festgehalten (`PromptsWindowTest.theCommaPopupHasThePromptsKeyRightAfterTheClipboardKey`).
- `KeyCode.kt`: `AGENT_PROMPTS` = -12007 (in `checkAndConvertCode` durchgereicht).
- `KeyboardIconsSet.kt`: Namenskonstante `NAME_AGENT_PROMPTS_KEY` und `agentIconIds` (ein Drawable
  `ic_agent_prompts.xml` für alle Symbolstile); `loadIcons` bildet die Id-Liste jetzt als
  `agentIconIds + (Stil-Ids bzw. Stil- plus eigene Ids)`: die Ids des Forks stehen zuerst, jede
  Stil-Zuordnung und jedes eigene Symbol darf sie überschreiben (P298).
- `KeyCodeDescriptionMapper.kt`: TalkBack-Beschreibung für `AGENT_PROMPTS`
  (`agent_prompts_key_description`).
- `KeyboardActionListenerImpl.kt`: `onCodeInput` mit `AGENT_PROMPTS` →
  `AgentEditorController.openPrompts` (eine Zeile neben den anderen Agent-Codes).
- `InputView.java`: `agent_prompts` zählt als Overlay (`hasAgentOverlay`).
- `main_keyboard_frame.xml`: `PromptsView` als weiteres Kind von `AgentKeyboardFrame`.
- `SettingsNavHost.kt`: Ziel `agent_prompts` (`SettingsDestination.AgentPrompts`, Seite
  `AgentPromptsScreen`). Die Zeile im Bereich Leiste & Befehle steht in `AgentAreaScreen.kt` und
  `AgentAreaBarScreen.kt`, der Suchbegriff in `AgentSearchEntries.kt` (alle drei Fork-Dateien).

Emoji-Ansicht (Emoji-Mix, 25a; Abschnitt „Befehle“):

- `EmojiPalettesView.java`: erzeugt in `initialize` den `EmojiMixPalette` (Leiste als erstes Kind),
  `startEmojiPalettes` meldet das Feld, `stopEmojiPalettes` beendet den Mixer-Modus, `onMeasure`
  lässt die Seiten vorher um die Höhe der Leiste schrumpfen (`beforeMeasure`), `onReleaseKey`
  gibt ein Emoji im Mixer-Modus an `onEmoji` statt es zu tippen (kein Verlauf „Zuletzt“, kein
  Wechsel zurück zu den Buchstaben).
- `assets/layouts/emoji_bottom/emoji_bottom_row.json` und `emoji_bottom_row_with_action.json`:
  statt Lupe (0,15) und Leertaste zwei Tasten mit Breite -1 (teilen sich den Rest genau gleich,
  `KeyboardParser.setReasonableWidths`): links `agent_emoji_search` (ohne Emoji-Wörterbuch die
  Leertaste), rechts `agent_emoji_mixer`; Zurück, Löschen und Aktion bleiben. Gilt in allen
  Stilen, der flache Stil hat keine eigene untere Emoji-Reihe. Ein eigenes Layout des Nutzers für
  die untere Emoji-Reihe ersetzt die Datei und damit beide Tasten.
- `KeyLabel.kt`: die beiden Namen werden zu Textbeschriftungen aus `agent_emoji_mix_strings.xml`
  (`Settings.getCurrentContext`, Rückfall Englisch) mit `!code/key_emoji_search` bzw.
  `!code/-12005`. `KeyboardActionListenerImpl.kt`: `AGENT_EMOJI_MIXER` →
  `EmojiMixPalette.toggleCurrent`. `KeyCode.kt`: `AGENT_EMOJI_MIXER = -12005` (in `checkAndConvertCode`
  durchgereicht).

Schieberegler (Etappe 28, Abschnitt „Einstellungen und Profile“, P85–P87):

- `SliderDialog.kt`: ein `AgentSlider` statt der zwei Material-Slider (mit und ohne Stufen);
  neue Parameter `defaultValue` (Rastpunkt) und `integerValues` (Ticks und runde Werte ganzzahlig).
- `SliderPreference.kt`: reicht `default` als `defaultValue` und `default is Int` als
  `integerValues` an den Dialog; sonst unverändert (alle `SliderPreference` der Upstream-Seiten
  bekommen so Ticks und Rasten, ohne dass ihre Seiten geändert sind).
- `KeyboardScalePreference.kt`: `AgentSlider` mit dem Standardwert der jeweiligen Variante.
  Seit 38b ein optionaler Platz `row` (Standard: die bisherige `Preference`-Zeile), damit die
  Seite Aussehen ihre eigene Zeile zeigt und denselben Dialog öffnet (P125).
- `CustomFontPreference.kt` (38b): derselbe optionale Platz `row` und ein optionales `onChanged`
  nach Laden oder Löschen der Datei (P146); Dateiauswahl, „Laden/Löschen“ und Fehlermeldung bleiben
  unverändert in der Datei.
- `ColorPickerDialog.kt`: Deckkraft- und Helligkeitsleiste der Bibliothek (colorpicker-compose)
  bleiben, ihre Berührungen übernimmt `AgentSliderInput` (setzt `setAlpha`/`setBrightness` wie die
  Bibliothek, mit `fromUser = true`); dazu Slider-Semantik für TalkBack (vorher keine).

Einstellungen System & Daten (38h, Abschnitt „Einstellungen und Profile“):

- `BackupRestorePreference.kt`, `LoadGestureLibPreference.kt`: optionaler letzter Parameter
  `row` zeichnet den Eintrag (Standard: die bisherige `Preference`); so öffnet die Bereichsseite
  denselben Dialog mit denselben Launchern aus ihrer eigenen Zeile. Sonst unverändert.

Fehlerbehebungen und Buchstabendreher (Etappen 73, 75 und 76, Nachweise in `docs/VALIDATION.md`):

- `Suggest.kt` (75): `applySwappedLetters` zwischen `applyLearnedCorrection` und `addFromMemory`; das Ergebnis von
  `applyLearnedCorrection` steht dazu in `learnedCorrection`, `engineCorrection` ist die erste Engine-Zeile, wenn die
  Engine selbst korrigieren würde (P302–P304). Dazu die Konstante `SWAPPED_LETTERS_DICTIONARY`.
- `DictionaryFacilitatorImpl.kt` (75): `mainFrequency(word, language)`, nur lesend, ohne Lock.
- `SettingsValues.java` (75): `mSwappedLettersCorrection` aus `AgentPreferences.AUTOCORRECT_SWAPPED_LETTERS`, Standard an.
- `BackupRestorePreference.kt` (76): der Eintrag der Einträge in `backupLauncher` und `restoreLauncher` (Abschnitt
  „Ablage“, P305–P306); sonst unverändert.
- `ClipboardHistoryManager.kt` (73): `onPrimaryClipChanged` setzt `dontShowCurrentSuggestion = false` auch ohne
  eingeschalteten Verlauf (P307).
- `KeyboardAccessibilityNodeProvider.kt` (73): `createAccessibilityNodeInfo` ruft `updateParentLocation()` für jede Taste (P294).
- `strings.xml` (`values`, `values-de`): `backup_restore_message` nennt die Einträge.

Update-Migrationen (Build 12, P84): `AppUpgrade.kt` läuft nach einem Update nur noch die Teile
für jedes Update (entpackte Wörterbücher leeren, `upgradeToolbarPrefs`, Layout-Cache) und
überspringt HeliBoards Versions-Migrationen (`FORK_UPGRADES_ONLY`); beim ersten Update von Build
12 oder älter repariert `MyDesigns.repairKeyGapsAfterOldUpgrades` nur Abstände mit genau dem Wert
der alten Migration (1,75 bzw. 1,1 im Querformat): Hochformat bekommt den Abstand des gewählten
eigenen Designs, alle anderen ihren Standard; die Höhe bleibt. Test: `ForkUpgradeTest`.

- `RichInputConnection.java` (64a): je eine Zeile `TypingPerf.app()` vor jedem lesenden Aufruf, der die App wirklich
  fragt: `getTextBeforeCursor` und `getTextAfterCursor` (in den zwei `…AndDetectLaggyConnection`, also nur wenn der
  Textspeicher nicht antworten kann), `getSelectedText`, `getExtractedText` in `reloadCursorPosition`, die Prüfung des
  letzten Zeichens nach `setComposingText` und `getSelectedText` in `tryFixIncorrectCursorPosition`. Nicht gezählt:
  schreibende Aufrufe (`commitText`, `setComposingText`, `deleteSurroundingText`, `sendKeyEvent`, `setSelection`), das
  `getExtractedText` in `copyText` (sein `getSelectedText` zählt, einmal) und die statischen Ersetzungs-Helfer (Antippen, nicht Tippen).
  Der Zähler wirft nie. `LatinIME.onEvent` zählt jeden Tastenbefehl, auch Dauerfeuer und Pfeile des Touchpads (verzerrt nur die Karte „Tippen“).
- `LatinIME.java` (64a): `onEvent` beginnt mit `TypingPerf.keyPress()` (ein Tastendruck beginnt; nur Zahlen).
- `LatinIME.java` (82): `getAgentSelection` (markierter Bereich, sonst null), `getAgentEditorGeneration` (Nummer der Editor-Sitzung,
  `AndroidEditorAdapter.generation`), `tryShowSelectionChips` (ohne Eingriff in die Werkzeugleiste, nicht mit `mAutoShowToolbar`) in
  `setSuggestedWords` (nach den Symbol-Chips, vor dem Screenshot-Chip, nicht für Wischtipp-Stile `UPDATE_BATCH`/`TAIL_BATCH` und
  `APPLICATION_SPECIFIED`) und `setNeutralSuggestionStrip` (nach den Symbol-Chips, vor der Pille), `onEvent` ruft am Ende
  `SelectionChips.afterKey`, `onUpdateSelection` danach `SelectionChips.afterSelectionUpdate` (dafür steht das Ergebnis von
  `mInputLogic.onUpdateSelection` in `selectionHandled`; der Rest des Ablaufs ist gleich). `ClipboardHistoryManager.onPrimaryClipChanged`
  ruft als erste Zeile `SelectionChips.clipChanged`. `InputLogic`, `RichInputConnection` und `PointerTracker` sind unverändert.

Geänderte Upstream-Tests (Leertasten-Gesten): `InputLogicTest.kt` ruft `onSpaceSwipe` statt
`onHorizontalSpaceSwipe` und hat die Fälle `generatedSpace*` (seit Etappe 33 auch die
Rastpunkte über `onSpaceSwipeDistance`).

Geänderte Upstream-Tests: `Shadows.kt` (IME-Info aus `BuildConfig.APPLICATION_ID`;
`getTextBeforeCursor(n)` liefert wie Android die letzten n Zeichen vor der Schreibmarke, nicht die
ersten n des Felds),
`Shadows.kt` liefert außerdem `acceptedMimeTypes` und `currentImeOptions` im `EditorInfo` (23g),
dazu `currentPackageName` und `currentFieldId` (Sprachbefehle, P66) und `contentCommit` (Antwort
des Felds auf `commitContent`, 25a),
`InputLogicTest.kt` (zusätzliche erzeugte Fälle, `typedCommand*` für 24a, `screenshotChip*` für
23g, `symbolDraw*` für 27, `typedEmoji*` für 25a), `SuggestTest.kt` (Personalisierung im Test
ausdrücklich eingeschaltet, weil sie jetzt opt-in ist).

Feststelltaste und Zahlenreihe (Etappe 98):

- `assets/layouts/number_row/number_row.json` (Upstream-Datei, jetzt geändert): die Symbole `! @ # $ % ^ & * ( )` stehen unter
  `shiftedManual` statt `manualOrLocked` des `shift_state_selector` (`floris/KeyData.kt`). Einmaliges Shift
  (`ALPHABET_MANUAL_SHIFTED`) zeigt sie wie bisher; Feststelltaste (`ALPHABET_SHIFT_LOCKED`) und automatisches Shift fallen auf
  `default`, die Ziffern. Eigene Zahlenreihen-Layouts des Nutzers (`LayoutType.NUMBER_ROW`) sind nicht betroffen. Test
  `SymbolPagesTest.capsLockKeepsTheDigitsOfTheNumberRow`.

Symbolseiten wie Gboard (Etappe 81, Fallstricke P345–P348):

- `assets/layouts/symbols/symbols.txt` und `more_symbols/symbols_shifted.txt` (Upstream-Dateien, jetzt
  geändert) haben den Inhalt der früheren Flat-Dateien; `symbols_shifted.txt` behält eine letzte Reihe
  mit `<` und `>` (Zweiergruppe), damit eigene Funktions-Layouts ohne `moreSymbols`-Eintrag sie wie bei Upstream
  anstelle von Komma und Punkt benutzen. `∆` (U+2206) ist Hauptzeichen, `Δ` das Popup.
- `assets/layouts/symbol_hints/symbols_hints.txt` (neu, kein wählbares Layout: der Ordner gehört zu keinem
  `LayoutType`): die alte erste Seite mit der Reihe `% \ | = [ ] < > { }`. Nur
  `KeyboardParser.addSymbolPopupKeys` liest sie (`builtInSymbolHintLabels`, nur die Beschriftungen, einmal
  je Prozess) für die Hinweise und Popups der Buchstabentasten.
- `KeyboardParser.kt`: `usesBuiltInSymbols(type)` (Layoutname = Standardname oder ein früherer Flat-Name
  `symbols_flat` / `symbols_shifted_flat`); `hasNumberRow()` ersetzt die drei Stellen mit der Stilbedingung:
  Seite 1 mit dem eingebauten Layout hat immer die Zahlenreihe, Seite 2 (eingebaut oder im flachen Stil)
  nie, sonst gilt wie bisher `numberRowEnabled`; `addNumberRowOrPopupKeys` ersetzt die erste Reihe nur noch
  bei nicht eingebauten Layouts. `FlatStyle.shortenNumberRow` hängt an `hasNumberRow()`.
  Hinweise: eingebaut = Datei oben, sonst das gewählte Layout wie vor 81.
- `functional_keys.json`, `functional_keys_tablet.json`, `functional_keys_khipro.json`: `moreSymbols` für `<`, `>`
  (mit den Popups `‹ ≤ «` und `› ≥ »`, ohne die Werkzeug-Popups des Kommas) und „12/34“; die Pfeiltaste bleibt hinter
  `dpadKeyEnabled`. Nur eigene (benutzerdefinierte) Funktions-Layouts ohne diese Einträge behalten die alte zweite
  Seite (kein „12/34“, `<`/`>` mit den Popups von Komma und Punkt).
- `AgentAreaKeysScreen.kt` und `PreferencesScreen.kt`: die Zeile „Zahlenreihe in Symbolen“ trägt die Zusammenfassung
  `agent_keys_number_row_in_symbols_sub` (gilt nur für eigene Symbol-Layouts).
  `Defaults.PREF_SHOW_DPAD_KEY` ist jetzt `false`: nur ungesetzte Werte folgen, ein gesetzter bleibt.
- `FlatPreset` schreibt `layout_SYMBOLS`/`layout_MORE_SYMBOLS` nicht mehr (nur noch `layout_FUNCTIONAL`).
  Steht der alte Name noch in den Einstellungen, ist die Datei weg: `LayoutUtils.getContent` fällt auf den
  Standard zurück, `usesBuiltInSymbols` zählt den Namen als eingebaut. Keine Datenmigration.
- Tests: `AT/SymbolPagesTest.kt` (Seiten Taste für Taste in vier Stilen, Zahlenreihe an/aus, Englisch,
  Pfeiltaste, Buchstaben-Hinweise gegen `src/test/resources/symbols81/letter_keys.txt`, vor der Änderung
  aufgenommen; dazu Tasten-Codes, Zustandsmaschine des Ziffernblocks, Popups, Pfeiltaste gesetzt/ungesetzt, eigenes
  Layout und `symbols_arabic`, Tablet/Khipro, Reihenhöhe); `AT/SettingsKeysScreenTest.kt` prüft die Zusammenfassung
  der Zeile; `AT/FlatStyleRenderTest.kt` erwartet `∆`.

Feste Leiste (Etappe 85, 86, 88; Beschreibung: „Feste Leiste“, Fallstricke P380–P391, P470–P482):

- `SuggestionStripView.kt`: `fixedBar` (Modus EXPANDABLE), `barState`, `applyBarState`, `updateLeftKey`, Raster (`openGrid`, `runTool`,
  `gridTools`), `onKeyboardHidden`; `setToolbarVisibility`, `setSuggestions`, `setExternalSuggestionView`, `onClick`, `onLongClick`,
  `updateKeys`, `updateVoiceKey` und `clear` verzweigen für die feste Leiste. Der Teil für die anderen Modi und den flachen Stil
  (`FlatStrip.arrange`, Menü) ist unverändert. `FlatStrip.arrangeLeftKey` ist aus `arrange` herausgezogen, `openSettings` ruft `SettingsLauncher`.
  Etappe 86, 88: Anpassen-Modus (`editor`, `enterEdit`, `exitEdit`, `onDragEvents`, `populateBar`; kein Systemziehen), Aussehen und Hinweise (`applyUndoOffer`, `showUnavailableHint`).
- `LatinIME.java`: `hideWindow` ruft `mSuggestionStripView.onKeyboardHidden()` (statt `setToolbarVisibility(false)`) im Modus EXPANDABLE. Sonst
  nichts: die Rangfolge der Chips in `setSuggestedWords` und `setNeutralSuggestionStrip` ist unverändert. `InputLogic`, `RichInputConnection`
  und `PointerTracker` sind nicht berührt.
- `InputView.java`: `agent_tool_grid` zählt als Overlay (`hasAgentOverlay`), wie das flache Menü. `main_keyboard_frame.xml`: `ToolGridView`.
- `SettingsValues.java`: `mQuickPinToolbarKeys` ist immer aus (die feste Leiste hat keine angehefteten Tasten).
- `ToolbarUtils.kt`: `ToolbarKey.PROMPTS`, `CHAR_ART`, `DESIGN` (mit Codes), `isKeyPinned`/`pinKey`/`unpinKey`. `KeyCode.kt`: `AGENT_DESIGN`.
  `KeyboardActionListenerImpl.kt`: `AGENT_DESIGN` öffnet die Einstellungen zum Aussehen. `KeyCodeDescriptionMapper.kt`, `KeyboardIconsSet.kt`
  (Symbole der drei neuen Werkzeuge in allen drei Symbolsätzen).
- `ToolbarScreen.kt`: im Modus EXPANDABLE „Werkzeuge in der Leiste“ (`AgentPreferences.BAR_TOOLS`) statt Leisten-Tasten, keine angehefteten Tasten, kein Schnell-Anheften.
  `AgentAreaBarScreen.kt` und `AgentSkillsScreen.kt` (Fork-Dateien): Skill-Tasten-Schalter über `pinKey`/`unpinKey`.

Symbole ohne Verzögerung (Etappe 84, Fallstricke P370–P374). Zeilen, die die geschützten Dateien ändern (`InputLogic`,
`RichInputConnection`, `PointerTracker`; `RichInputConnection` und `PointerTracker` sind unverändert):

- `InputLogic.java`: die Felder `mCodeInputBatchOpen`, `mNeutralStripPending`; `onCodeInput` setzt sie nach `beginBatchEdit`,
  ruft `flushNeutralStrip()` zwischen zwei Ereignissen derselben Eingabe und nach `endBatchEdit()` und liest das „Wort am Cursor“
  (`mWordBeingCorrectedByCursor`, zwei Leseanfragen an die App bei Zeichen wie `-` nach einem Leerzeichen) erst danach.
  `setNeutralStripAfterBatch()` ersetzt die zwei `setNeutralSuggestionStrip()`-Aufrufe von `handleSeparatorEvent` (Tausch von Leerzeichen
  und Satzzeichen, Trennzeichen). `performUpdateSuggestionStripSync(…, showOnStrip)` und `commitCurrentAutoCorrection(…, showPendingSuggestions)`:
  ein Trennzeichen außer Leerzeichen, `:` und Leerraum nimmt die Vorschläge einer wartenden Aktualisierung (die Autokorrektur kommt
  daraus), baut dafür aber keine Leiste, die gleich durch die neutrale ersetzt wird (`handleSeparatorEvent`: `stripFollows`).
- `LatinIME.java`: je eine Zeile `TypingPerf.work(…)` in `tryShow*Chips`, `tryShowClipboardSuggestion`, `setSuggestedWords` und
  `setNeutralSuggestionStrip`; `SuggestionStripView.setExternalSuggestionView`, `AsyncResultHolder.get` (nur der Hauptthread zählt im Test)
  und `ClipboardHistoryManager` (Dienstaufruf, Pille gebaut) haben dieselbe Zeile. `ClipboardHistoryManager.isClipSensitive(inputType, description)`
  liest die Zwischenablage nicht mehr selbst (ein Aufruf des Dienstes statt zwei, im flachen Stil drei).
- `A/TypingPerf.kt` (84): `Work` (feste Namen der Arbeitsstellen: `SUGGEST_WAIT`, `STRIP_NEUTRAL`, `STRIP_WORDS`, `STRIP_EXTERNAL`,
  `CHIPS_*`, `CLIPBOARD_SERVICE`, `CLIP_VIEW_BUILT`) und `work(place)`, das nur etwas tut, wenn ein Test `workListener` gesetzt hat.
  Keine Zahlen im Gerät, nichts auf der Diagnose-Karte.
- Messwerkzeug (Tests, `T/latin/`): `TypingRig.kt` tippt wie ein Finger (`KeyboardActionListenerImpl.onPressKey`/`onCodeInput`/`onReleaseKey`,
  echte `LatinIME`, echte Leiste, die Wörterbuchantwort aus einer kleinen Wortliste auf dem echten Vorschlags-Thread) und schreibt je Taste eine
  Zeitfolge: Aufrufe in den Editor (`Shadows.kt`: `ShadowInputMethodService.callListener`, nur Namen und der Text eines Schreibens) und Arbeitsstellen
  (`TypingPerf.workListener`), mit der Angabe, ob der Batch des Editors offen war. `SymbolLatencyTest` druckt die Tabelle (`build/latency84-work-table.txt`,
  `…-symbols.txt`, `…-wall-clock.txt`) und beweist die Änderungen mit Zählern; `TypingEquivalenceTest` vergleicht 41 Tippfolgen mit
  `src/test/resources/stage84/typing-transcripts.txt` (vor der Änderung aufgenommen: Text, Schreibaufrufe mit Text, Leistenwörter nur wo sie nicht von der
  Reihenfolge zweier Nachrichten abhängen).

## Editorzugriff, Panel und Rad

> Wie die Tastatur Auswahl liest, Aktionen zeigt und Ergebnisse übernimmt.

- `AgentEditorController.kt`: besitzt nur die Aktionssitzung der IME, nie das Textfeld der
  Host-App. Öffnet Panel, Rad und Skills, holt Freigaben ein, startet Läufe, zeigt Ergebnisse und
  führt Übernahme oder Kopieren aus. Übersetzt Fehler (`CloudError`, `DenialReason`) in Texte.
- `AndroidEditorAdapter.kt`: Main-Thread-Adapter. Holt die `InputConnection` jedes Mal neu,
  speichert sie nie in einem Snapshot. Prüft die Privacy vor dem Lesen und vergleicht vor der
  Übernahme erneut. `captureAnchor`/`insert` fügen an der Schreibmarke ein und lesen dafür
  keinen Text aus dem Editor; ohne gesetztes `commitInsertion` wird jede Einfügung abgelehnt.
  `protection()` (Etappe 53): der Grund, warum das Feld gar nicht verarbeitet werden darf
  (Passwort, Inkognito, ohne Lernen, gesperrt, kein Textfeld, kein Editor), ohne Komposition und
  Auswahl als Grund; liest keinen Text.
- `AgentKeyboardFrame.kt`: Overlays sind so breit wie die Tastatur, vergrößern die IME nie und
  enden über der Navigationsleiste (`overlayBottomInset`, Fallstrick P18). Hintergrundtasten sind
  währenddessen aus der Accessibility entfernt.
- `AgentPanelView.kt`: natives Aktionspanel im IME-Fenster (lokale Werkzeuge, KI-Aktionen,
  Workflows, Werkzeuge). Öffnet nie eine zweite
  `InputConnection` und keinen eigenen Editor.
- `AgentWheelView.kt`: radiales Aktionsrad aus echten Buttons (Tastaturnavigation, TalkBack). Eine
  Auswahl bereitet nur eine Vorschau vor.
- `SkillLabels.kt`: Titel und Toolbar-Tasten der Skills.
- `AgentPreferences.kt`: Schlüssel der Lernpause, des Extras für die KI-Einstellungen und das
  alte Opt-in `SPACE_LONG_PRESS` (nur noch gelesen, solange „Halten“ keinen eigenen Wert hat).
- `A/SpaceGestures.kt`: Leertasten-Gesten. `SpaceGesture` (links, rechts, oben, unten wischen,
  Halten, Doppeltippen, je ein Schlüssel `agent_space_*`), `SpaceAction` (feste Liste, nur
  `MOVE_CURSOR`, `SWITCH_LANGUAGE`, `TOUCHPAD_MODE` laufen fortlaufend, alle anderen einmal pro
  Wisch: senkrecht beim ersten Schritt, seitlich am letzten Rastpunkt, siehe `SpaceDetents`),
  `choices` je Geste, `resolve` = eigener Wert oder `inherited` aus den alten
  Einstellungen (HeliBoards horizontale/vertikale Wischaktion, „Leertaste lang drücken“,
  `SPACE_LONG_PRESS`). Es wird nichts umgeschrieben; „Standard“ entfernt den eigenen Wert.
  `KeyboardActionListenerImpl.runSpaceAction` führt die Einmal-Aktionen über die vorhandenen
  Wege aus: Tastencodes per `onCodeInput` (Sperre bei gesperrtem Gerät greift dort),
  `toggleLayout`, `openWheel`/`open` des Controllers (eigene Schutzprüfungen),
  `requestHideSelf`; `COMMAND_LIST` tippt das Befehlspräfix (vorher ein Leerzeichen nach einem
  Wort), nur wenn `KeyboardCommands` an und nicht gesperrt ist. Doppeltippen: Nur echte
  Leertasten-Tipps (Code zwischen `onPressKey` und `onReleaseKey` der Leertaste) zählen; der zweite Tipp innerhalb von
  `mDoubleSpacePeriodTimeout` an derselben Schreibmarke ohne Auswahl führt die Aktion statt
  eines Leerzeichens aus, jeder andere Code setzt zurück (Fallstrick P49).
- `A/SpaceDetents.kt` (Etappe 33): Rastpunkte eines seitlichen Leertasten-Wischs mit
  Einmal-Aktion, reine Zustandsmaschine ohne Android. Alle Abstände an einer Stelle:
  `AT_KEY_WIDTHS` = 0,8 / 1,6 / 2,65 Tastenbreiten ab dem Aufsetzpunkt (Skizze des Owners),
  `BACK_KEY_WIDTHS` = 0,2 (Rückweg, P102), `MIN_KEY_WIDTH_DP` = 20 (Untergrenze der Tastenbreite,
  `unitPx`; die Breite liefert `keyWidth`: `mMostCommonKeyWidth`, höchstens ein Siebtel der
  Tastatur, weil Ziffern-, Telefon- und Zahlenblock Tasten von etwa einem Viertel der Breite
  haben), `HOLD_CANCEL_DP` = 10 (unter 10 dp beendete ein rollender Finger das Halten) und
  `cancelsHold` (Abbruch des Haltens, P101). `move(links, dX, Einheit)` liefert `DETENT` (ein
  Tick je überquertem Rastpunkt, ein schneller Sprung über zwei tickt einmal), `COMPLETE` (letzter
  Rastpunkt, genau einmal bis `end`) oder `NONE`; die Richtung ist die Startrichtung, die andere
  Seite des Starts zählt nicht. `KeyboardActionListenerImpl.onSpaceSwipeDistance` hält eine
  Instanz: `isBlockingInput` oder Aktion `NONE`/fortlaufend → nichts; `DETENT` →
  `HapticChain` `DETENT`; `COMPLETE` → `runSpaceAction` (gleiche Wege und Sperren wie bisher)
  und nur wenn sie etwas getan hat `STRETCH`. `onEndSpaceSwipe` setzt sie vor der `isBlockingInput`-Prüfung zurück.
- `A/HapticChain.kt`: das Gefühl der Rastpunkte (bis Build 12: drei Ticks direkt nach
  Rückgängig/Wiederholen, mit Etappe 33 entfernt; Halten, Doppeltippen und die Toolbar-Tasten
  haben nur noch ihren normalen Tasten-/Langdruck-Impuls). `plan(Feel, Device)` ist eine reine
  Funktion, beste Stufe zuerst: `DETENT` ab API 30 `PRIMITIVE_TICK` 0,8 (sonst `PRIMITIVE_CLICK`
  0,5), sonst ab API 26 mit `hasAmplitudeControl` 12 ms mit Amplitude 153, sonst ein Puls von
  12 ms. `STRETCH` ab API 30 `PRIMITIVE_QUICK_RISE` 0,7 plus `PRIMITIVE_THUD` 1,0 (THUD erst ab
  API 31 gefragt, sonst `PRIMITIVE_CLICK`), nur wenn der Anstieg da ist; sonst eine Wellenform
  über 90 ms mit steigender Amplitude (63→255); sonst ein Puls von 70 ms (P103). `play` nur mit
  `mVibrateOn` (Tastenvibration an, Gerät hat Vibrator) und nicht bei „Bitte nicht stören“
  (`zen_mode` wie LatinIME), außer `mVibrateInDndMode`; ohne Usage-Attribute wie HeliBoards
  eigene Tastenvibration, damit der System-Schalter für Berührungs-Haptik sie nicht stumm
  schaltet, solange die Tasten vibrieren (Review Build 12). Der letzte Rastpunkt spielt den
  Impuls auch, wenn die Aktion danach nichts tut (z. B. nichts rückgängig zu machen).
  `Device` ist die Naht für Tests (`deviceForTest`); eine Ausnahme des Vibrators wird geschluckt
  (P67).
- `C/CursorTrack.kt` (Etappe 87, Cursor als Trackpad): der reine Kern ohne Android. Eingabe: `begin(t)`, dann
  `moveTo(xDp, yDp, tMs)` (Abstand vom Aufsetzpunkt, Ereigniszeit) und optional `setText(vor, nach, amAnfang, amEnde)`;
  Ausgabe: Einzelschritte `Step(Achse, Richtung, chars, boundary, detent)` (ein Graphem-Cluster waagerecht, eine Zeile
  senkrecht; `chars` = Zeichen im Fenster, 0 ohne Text). Reihenfolge der Verarbeitung: 4-ms-Raster mit linearer Interpolation
  (gleiche Bewegung bei 60 und 120 Hz), Geschwindigkeit als Sehne über 60 ms, Kurve `gain(v, max)` = 1 bis 90 dp/s, glatt
  (smoothstep) bis `HORIZONTAL_GAIN` 4 / `VERTICAL_GAIN` 2 ab 1100 dp/s, Achswahl (Gedächtnis 20 dp Weg, Wechsel bei Verhältnis
  1,6, Weg der anderen Achse bleibt als Guthaben), Spiel 3 dp bei Umkehr (Backlash; die erste Bewegung einer Achse hat keines),
  Magnetismus als Hysterese (Schritt, der eine über einen Schritt erreichte Wortgrenze verlässt, kostet
  `1 + magnetism · 1,0 · detentFactor(v)`; `detentFactor` 1 bis 120 dp/s, 0 ab 600 dp/s), Rest bleibt in `u`. Wortgrenze =
  Wechsel Wort/Leerraum/Sonstiges zwischen zwei Clustern (Apostroph zwischen Buchstaben gehört zum Wort; Textrand zählt nur im
  echten Textanfang/-ende, nicht am Fensterrand). Cluster nach `TextColumns.graphemes` (eigene Regeln, auf jeder JVM gleich).
  `forwardLimitChars` (Löschwisch) und `horizontalOnly`. Alle Konstanten stehen im `companion` und sind erste Schätzungen.
  Test `CT/CursorTrackTest.kt`.
  Test der reinen Entscheidungen von `A/CursorTrackpad.kt` (Gründe, Start): `AT/CursorTrackpadReasonTest.kt`.
- `A/CursorTrackpad.kt` (Etappe 87): `spaceReason(Belegung, Schalter)` mit `SpaceReason` (Etappe 87c: `QUALIFIES_ALL_AXES` = jede Richtung Cursor oder nichts, mindestens eine Cursor; `QUALIFIES_START_DECIDES` = mindestens eine Richtung Cursor, eine andere mit Aktion; `NOT_SETTING_OFF`, `NOT_NO_CURSOR_DIRECTION`), `appliesToSpace` (Name aus Etappe 87, wahr für beide Gründe), `decideStart(Belegung, dx, dy, mehrzeilig)` (`Start` UNDECIDED/TRACKPAD/LEGACY; UNDECIDED nur unter 10 dp, keine Wartezone), `startDirection` (Vergleich des alten Wischens: senkrecht, wenn |dy| ≥ 10 dp und |dx| < |dy|, sonst seitlich), `collapsesToStart`, `CLAIM_DP` = 10, `WINDOW_CHARS` = 200 und `runs` (gleiche Achse und
  Richtung hintereinander zu einem Zug mit einem Tick; `claims` = die alte Schwelle von 10 dp auf der Wischachse, `MAX_PENDING` = 64 Proben,
  `SELECTION_WAIT_MS` = 300). Die Zustandsmaschine steht in
  `KeyboardActionListenerImpl.onCursorTrack(pointerId, code, dx, dy, zeit, downTime)`: **ein Zustand je Finger** (`trackStates[pointerId]`,
  Schlüssel `downTime` = Beginn der Berührung) und **höchstens ein Besitzer** (`trackOwner`). `UNDECIDED` → beim ersten Aufruf einer
  Berührung `decideTrackMode` (`mCursorTrackpad` an, kein Overlay; **zuerst** der Besitz: besitzt ein anderer Finger die Spur oder ein Löschwischen,
  ist jeder Wisch auf Leer-/Löschtaste `REFUSED`; dann Leertaste: `spaceReason(...).qualifies` (die Markierung zählt seit Etappe 87c nicht mehr, sie wird beim Anspruch zusammengezogen); Löschtaste: immer) →
  `OFF` (der alte Code läuft, die ganze Berührung lang), `REFUSED` (ein anderer Finger besitzt die Spur: dieser bleibt `TRACK_PENDING`, also ein Tipp
  oder Halten mit Wiederholung, bis er die Wischschwelle überschreitet; dann `TRACK_REFUSED_DRAG`: der Tracker bricht seine Tastentimer ab und
  verwirft die Berührung mit `cancelTrackingForAction`, sie tippt, wiederholt und löscht nichts) oder `PENDING` (Proben sammeln, bis
  `claims`) → `ACTIVE` (Besitz nehmen, Feld merken, Text einmal lesen, die Spur beginnt an der ERSTEN angekommenen Probe mit Weg 0 und Tempo 0 (die
  Schnelltipp-Sperre schluckt die ersten Moves, ihr Weg wird nicht als Sprung nachgespielt), Proben abspielen, danach jede Probe in den Kern; nie wieder `OFF`).
  Ein neues Feld (anderes `mInputAttributes`) beendet die Berührung für die Spur: kein Schritt erreicht das neue Feld, der Besitz ist frei. Der Besitz endet
  nur durch `onCursorTrackEnd(pointerId)` (Heben, Abbruch, `cancelKeyTracking`) oder eine neue Berührung desselben Fingers. Kein Druck, keine
  Wiederholung und kein Ende eines anderen Fingers rührt einen Zustand an. Spur: Leertaste in einem Feld mit `TYPE_TEXT_FLAG_MULTI_LINE`
  (Klasse Text) die zweidimensionale `spaceTrack`, sonst und fürs Löschen die nur waagerechte `flatTrack` (senkrechte Bewegung wird
  ignoriert, auch für die Geschwindigkeit; unbekannter Feldtyp = nicht mehrzeilig). Ausführung: waagerecht `applyHorizontalMove(chars)` (der Schwanz
  des alten `onMoveCursorHorizontally`, unverändert herausgezogen: Wortinneres, `finishInput`, Prüfpunkt, Vorschläge neu starten); ohne
  Text der alte Pfad `onMoveCursorHorizontally(Cluster)` samt Pfeiltasten-Rückfall; senkrecht je Zeile `ARROW_UP`/`ARROW_DOWN` über
  `onCodeInput` — aber **einen Pfeil je Antwort des Editors** (`pendingLines`, höchstens 3 gemerkt, `pumpArrows`): Nach einem Pfeil wartet der Zustand
  (`awaitSelection`), bis `expectedSelectionStart` sich ändert (Antwort) oder 300 ms vergangen sind; dann liest er die echte Stelle aus dem Editor
  (`getExtractedText`, ein IPC): verschoben = verspätete Antwort (die Zwischenspeicher werden mit `resetCachesUponCursorMoveAndReturnSuccess` nachgezogen),
  nicht verschoben = Rand erreicht (`edgeUp`/`edgeDown`: kein weiterer Pfeil in diese Richtung in dieser Berührung). Nie ein Pfeil nach oben bei
  Cursor 0, nie einer nach unten ohne Text dahinter. Den Text liest er erst nach der Antwort neu (der Kern hält waagerechte Schritte bis dahin zurück und trägt den Weg); Löschwisch `moveDeleteRun` wie `onMoveDeletePointer`
  (Auswahlanfang, nie hinter das Ende; kann ein Schritt nicht angewandt werden, beginnt die Spur am Finger neu, statt dem Feld davonzulaufen). Während ein Overlay Eingaben sperrt, wird nichts gefüttert; danach beginnt die Spur am Finger neu
  (`needsRestart`: Ursprung = Fingerstelle, kein Weg von vorher, nie ein Klumpen). Haptik: ein Tick je Zug
  (`gestureMove…Haptics`), an einer Wortgrenze bei langsamem Finger `HapticChain.DETENT`. RTL: x wird für Leertaste umgekehrt wie
  im alten Wisch, nicht fürs Löschen. Textfenster: ein kürzerer Rückgabewert des Editors ist nur dann Textanfang/-ende, wenn eine Nachfrage nach einem weiteren Zeichen nichts mehr liefert
  (leere Antwort bzw. kein Zuwachs); sonst ein Fensterrand. Einstellungen: `AgentPreferences.CURSOR_TRACKPAD` (Schalter, anfangs an),
  `CURSOR_MAGNETISM` (0–100, anfangs 35) → `SettingsValues.mCursorTrackpad/mCursorMagnetism`; Zeilen in
  `AgentAreaGesturesScreen` (Treffen & Wischen), Regler `GestureSlider.MAGNETISM`, Texte `agent_gestures_strings.xml`.
  Tests: `AT/CursorGestureTrackerTest.kt` (echter `PointerTracker`, Mock-Listener: der alte Fixture-Teil und das Protokoll),
  `InputLogicTest` (`cursor87*`: echter Listener und `LatinIME`; `twoFingers*`: echter `PointerTracker` mit mehreren Fingern und echtem
  Listener über die Prüfstand-Klasse `TouchRig`, deren `FakeTimer` (ein `TimerProxy.Adapter`) Tastenwiederholung und Langdruck in virtueller Zeit
  wirklich auslöst, `wait(ms)` schickt alle 8 ms ein MOVE; die `twoFingers*`-Tests benutzen nur alte Schnittstellen und laufen deshalb auch auf dem Code
  vor Etappe 87),
  `SettingsAreaGesturesTest` unverändert.
- `K/settings/screens/AgentSpaceScreen.kt`: Seite „Leertaste“ (eine `ListPreference` je Geste,
  Vorgabe = geerbter Wert, „Standard“ entfernt den eigenen Wert), erreichbar über Erweitert und
  Tastaturprofile → Gesten. Texte in `agent_space_strings.xml`.
- Regeln: Ohne zuverlässig bestimmbare Auswahl wird nicht ersetzt. Editorwechsel, Neustart,
  geänderte Auswahl und zwischenzeitliche Eingaben machen eine ausstehende Ersetzung ungültig,
  auch wenn der Text gleich aussieht. Android hat kein universelles Compare-and-swap für fremde
  Textfelder, daher bleibt die Ersetzung bei Unklarheit gesperrt. Kopieren prüft dieselbe
  Schutzgrenze. Der Ablauf von Aktion bis Übernahme steht unter „Grundlagen“.
- Tests in `AT/`: `AgentPanelViewTest`, `AgentWheelViewTest`, `AgentSpaceGestureTest`,
  `SpaceGesturesTest`, `SpaceDetentsTest`, `HapticChainTest`,
  `AndroidEditorAdapterTest`, `SelectedTextCommitTest`, `DebugPrivacyTest`,
  `InsertAtCursorTest`, `ForkIdentityTest`, dazu `BatchDeliveryTest.kt` in
  `app/src/test/java/helium314/keyboard/latin/inputlogic/`.

## Speicher, Zugangsdaten und Datenschutz

> CE/DE-Trennung, verschlüsselte Schlüssel, Dateifreigaben. Details und Prüflimits: docs/STORAGE.md.

- Härtung (65): `ClipVault.mayKeep` ist die eine Regel für Tresor und Verlauf (P270); `RestoreFolders` und
  `restoreSettingsFromLines` in `BackupRestorePreference.kt` (P273); `FieldCorrectionWatcher.end()` (P272).
  Texte: `res/values/agent_hardening_strings.xml` und `res/values-de/agent_hardening_strings.xml`
  (`restore_settings_damaged`, `restore_settings_not_saved`, `restore_nothing_changed`,
  `restore_rollback_failed`). Tests: `HardeningClipHistoryTest.kt` (Verlauf je Bedingung, Dateien,
  Dienstende), `FieldEndHooksTest.kt` (die zwei `LatinIME`-Zeilen) und `RestoreSafetyTest.kt` (echte
  Ordner, Rückweg, Einstellungen).
- `AgentStorageContexts.kt`: liefert der Engine den DE-Kontext (Direct Boot). Geheimnisse und
  Agent-Konfiguration nutzen den rohen CE-Kontext aus `App.kt`.
- `security/EncryptedCredentialStore.kt`: `CredentialSlot` (ein Platz je Provider), `Secret`, `credentialContext`,
  `credentialAccessAllowed`. Feste Slots, gesperrt vor Entsperren und vor Android 6.
- `security/SecretCipher.kt`: AES-256-GCM mit Schlüssel im Android Keystore,
  `AesGcmSecretCipher` für Tests mit injiziertem Schlüssel.
- `ModelSettings.kt`: aktiver Provider der Tastatur-Aktionen, Modellkennung je Provider,
  Zielsprache und Ton in eigenen CE-Einstellungen (`agent_models`), nicht in den exportierbaren
  Tastatureinstellungen. Gemini hat bewusst keine Voreinstellung für das Modell.
- `AgentFileProvider.kt`: FileProvider mit denselben DE-Wurzeln wie die Engine-Komponenten, die
  die Dateien erzeugen.
- `AgentDocumentIO.kt`: liest und schreibt nur vom Nutzer gewählte SAF-Dokumente, mit
  Byte-Grenzen vor dem Parsen.
- Regeln: Personalisierte Wörterbücher und Zwischenablageverlauf starten ausgeschaltet. Lernen
  und Cloud-Freigaben sind getrennt. Erzeugter oder eingefügter Text ist keine Einwilligung zum
  Stiltraining. Neue Agent-Dateien liegen in CE/`noBackupFilesDir`, Upstream-Dateien in DE.
  Keine versteckten Android-APIs, keine Credential-Exporte oder -Backups.
- Emoji-Mix (25a): geladene Bilder in DE `files/agent_emoji_mix/` (LRU 20 MB, `EmojiMixCache`),
  über den Clipboard-Provider freigegeben; Paartabelle als Asset. Details: `docs/STORAGE.md`.
- Zwischenablage-Wächter und Tresor (Etappe 44): `A/ClipVault.kt` (Objekt, nur im Speicher):
  `offer(text, markedSensitive, copiedAt, region, allowed)` sortiert über `ClipGuard` und behält den
  Clip nur mit `allowed` (`mayKeep`: entsperrt, kein Inkognito; Passwortfelder zählen als
  Inkognito, P155) und solange er jünger als seine Lebensdauer ist (`lifetime`: Geheimnis 2 min,
  Code 10 min, andere `FRESH_MILLIS` = 10 min; Alter ab Zeitstempel des Clips, P154), höchstens
  `MAX_ITEMS` = 20. `secrets`/`codes` (neueste zuerst, abgelaufene vorher entfernt), `take` (ein
  Geheimnis verlässt den Tresor beim ersten Einfügen, seine Kopierzeit steht in `used`, damit
  derselbe Clip beim Neustart nicht zurückkommt; ein Code bleibt), `freshest(context, kind,
  maxAge)` für Etappe 45 (nie ein Geheimnis; ohne passenden Tresor-Eintrag und mit Verlauf aus nur
  der aktuelle Clip über `currentClip`, live gelesen, nie behalten, und nie, wenn `isSecretClip`
  zutrifft), `isSecretClip(text, copiedAt, marked)` (markiert, von `SensitiveClips` als im
  Passwortfeld kopiert vermerkt, Kopierzeit in `secretStamps` = von `offer` als Geheimnis sortiert,
  auch nicht behalten, höchstens 20, oder Geheimnis nach Inhalt; auch für den Leisten-Chip),
  `isBlocked` (gesperrt, Inkognito, Passwortfeld,
  unbekanntes Feld), `clearSystemClip` (P156), `clock` (Tests), `onChange` (Ablauf bei offener
  Ansicht, ein `Handler` weckt beim nächsten Ablauf). `Item.toString`/`Fresh.toString` ohne Text,
  nichts geloggt. `A/ClipVaultSection.kt`: Reihen „Tresor“ und „Einmalcodes“ (Tags
  `TAG_SECTION`, `TAG_SECRET_ROW`, `TAG_CODE_ROW`) als erstes Kind der Verlaufsansicht, nur wenn
  etwas da ist (P157); Chip-Text und TalkBack-Beschreibung nur `Item.shown` (verdeckt bzw. der
  Code); `historyText`/`label` für die Beschriftung persönlicher Clips (IBAN, Telefon, E-Mail,
  Adresse) in der Liste. Texte in `agent_clipvault_strings.xml` (`values/`, `values-de/`).
  `ClipCommand` (`@clip`) und `SnipCommand.SystemClipboard` (`{zwischenablage}`) behandeln ein
  Geheimnis wie einen vertraulichen Clip. `A/commands/SensitiveClips.kt` (seit der Nachprüfung
  44/45 nur im Speicher, je Prozess gesalzene Hashes, auch mit Verlauf aus; `isSensitive`,
  `matcher` mit den Hashes angehefteter Einträge aus der alten Datei, `migrateLegacy`, P155). Speicher: `docs/STORAGE.md`. Test `AT/ClipVaultTest.kt`.
  Etappe 45: Am Ende der letzten Tresor-Reihe öffnet der Chip „Meine Daten“ (`TAG_MY_DATA`, kein
  Tresor-Eintrag) über `SnipCommand.openSettings` die Seite `agent_my_data`.
- Meine Daten (Etappe 45, P164): `A/MyData.kt`. `MyData` (Name, Listen E-Mail, Telefon, Adresse,
  IBAN; `cleaned`: eine Zeile, `MAX_LENGTH` 200, `MAX_ENTRIES` 3, ohne Doppelte; `entries(kind)`;
  `toString` ohne Werte). `MyDataStore` wie `EncryptedCredentialStore`: AES-256-GCM
  (`androidSecretCipher`, eigener Keystore-Alias `agent.mydata.v1`, AAD „AgentKeyboard/MyData/v1“),
  Datei `agent_my_data/data.sealed` in CE `noBackupFilesDir` (`credentialContext`), höchstens
  16 KiB, `checkAccess` (`credentialAccessAllowed`, sonst `LOCKED` vor jedem Datei- und
  Schlüsselzugriff), manipulierte Datei = `UNAVAILABLE` ohne Löschen, leere Daten löschen die
  Datei. `MyDataCache`: gelesen erst, wenn ein Feld oder Hinweis eigene Daten braucht; bei
  gesperrtem Gerät verworfen, nach Speichern und Löschen verworfen, und in
  `LatinIME.onFinishInputView` (Bildschirm aus und Sperre blenden die Tastatur vorher aus). `ContextLearning` siehe
  „Lerndaten“. `K/settings/screens/AgentMyDataScreen.kt`: Seite „Meine Daten & Vorschläge“ (Route
  `agent_my_data`, auch in `SETTINGS_DESTINATIONS`): Schalter (Tastatur-Einstellungen, keine
  Daten), Karte mit neun Feldern (Name, je zwei E-Mail, Telefon, Adresse, IBAN; IBAN-Hinweis über
  `IbanCheck`; jedes Feld mit `PlatformImeOptions(AgentPreferences.NO_LEARNING_FIELD)`, die Tastatur
  lernt dort nichts, P180), Speichern, Alles löschen. `MyDataStore` entsteht nur im entsperrten Inhalt
  (P134/P145), `ON_STOP` verwirft ihn, `SecureWindow` solange die Karte steht, nichts in Saved
  State. Zeile auf Leiste & Befehle (`SettingsAreas.entries(BAR)`). Tests `AT/MyDataStoreTest.kt`,
  `AT/MyDataScreenTest.kt`.
- Tests in `AT/`: `EncryptedCredentialStoreTest`, `AgentStorageContextsTest`,
  `AgentDocumentIOTest`.

## KI-Anbindung

> Drei Cloud-Provider (OpenAI, Anthropic, Gemini) über einen gemeinsamen Transport. Verträge: docs/PROVIDERS.md.

- `providers/CloudTransport.kt`: der einzige Weg zu einem Provider. Genau ein nicht
  wiederholbarer JSON-POST (`isOneShot`), ein Aufruf zur selben Zeit (`BUSY`), Budget 150 s (`ModelBudget.CALL_TIMEOUT_MS`, Konstruktor-Parameter für Tests), Eingabe höchstens 32.768 Zeichen,
  Antwort höchstens 1 MiB, HTTP-Status wird auf neutrale `CloudError` abgebildet. Fremde
  Fehlerkörper werden weder angezeigt noch geloggt. Provider-Klassen liefern nur Body, Request
  und Parser.
- `providers/OpenAiProvider.kt`: `OpenAiProvider` (Responses-API, `store:false`/`stream:false`,
  `max_output_tokens` = `ModelBudget.outputTokens`, Builder 4096; `incomplete` wird `CUT_OFF`; einziger Provider mit `workflowDrafts`), `CloudHttpClient`
  (OkHttp, erst nach dem Senden initialisiert, ohne Redirects und Retries; Connect/Read/Write
  10 s, Read 120 s, Call 150 s), `CloudError` (Etappe 59: `CUT_OFF` = abgeschnitten),  `CloudFailure`. Der AndroidX-Startup-Initializer ist im
  Manifest entfernt, `OkHttp.initialize` läuft erst nach dem Senden auf IO.
- `providers/AnthropicProvider.kt`: Messages-API, `anthropic-version` aus `API_VERSION`,
  Bearer-Schlüssel, kein `temperature`, `max_tokens` = `ModelBudget.thinkingProviderTokens` (wie OpenAI, mindestens 8192; Denk-Token zählen bei
  Modellen mit Standard-Thinking mit). Ergebnis nur bei `stop_reason:end_turn`; `refusal` wird
  `REFUSED`; `max_tokens` wird `CUT_OFF` (abgeschnitten, nie ein Ergebnis); `thinking`-Blöcke
  werden übergangen, jeder andere Blocktyp ist ein Fehler.
- `providers/GeminiProvider.kt`: `generateContent` (v1beta), Schlüssel im Header
  `x-goog-api-key`, nie in der URL. Das Modell wird Teil des URL-Pfads und ist deshalb strenger
  geprüft (`validModel`: nur Kleinbuchstaben, Ziffern, Punkt, Bindestrich). Ergebnis nur bei
  `finishReason:STOP` und genau einem Kandidaten; `MAX_TOKENS` wird `CUT_OFF`; Teile mit
  `thought:true` werden übergangen. `maxOutputTokens` = `thinkingProviderTokens` (mindestens 8192).
- `providers/CloudProviders.kt`: `SUPPORTED` (Provider mit echtem Adapter, Reihenfolge der
  Einstellungen), `get` (erzeugt den Adapter erst bei der ersten freigegebenen Anfrage),
  `slot` (eigener Schlüsselplatz je Provider), `displayName`, `validModel`.
- Regeln für alle Provider: Ergebnis höchstens 65.536 Zeichen (`ModelBudget.MAX_RESULT_CHARS`), nur vollständig abgeschlossener
  Text; Refusal, abgeschnittene Antworten und Tool-Blöcke sind kein Ergebnis. Anthropic und
  Gemini lehnen `WORKFLOW_DRAFT` vor jedem Aufruf ab (der Act Builder bleibt bei OpenAI).
- `JournaledModelCall.kt`: genau ein freigegebener Modellaufruf mit Journal, unabhängig von der
  Tastatur-UI. Speichert nie Text.
- Tests: `OpenAiProviderTest`, `MoreProvidersTest` (Anthropic, Gemini, Platz- und
  Modellzuordnung; alles mit Test-Calls, kein echter Schlüssel), `JournaledModelCallTest`.

## Laufjournal

> Verschlüsselte Metadaten zu Läufen: Absicht vor Wirkung, kein Retry, keine Inhalte.

- `ActionRunJournal.kt`: `ActionRunJournal`, `ActionRunRecord`, `ActionRunState`, `RunSubject`
  (TextAction, Workflow, Builder, Tool) und `WorkflowStepLink`.
- `JournaledRun.kt`: schreibt die Absicht dauerhaft vor dem Start und den Endzustand danach.
- `EncryptedActionRunStore.kt`: Datei `agent_runs/status.sealed`, höchstens 20 Einträge, sieben
  Tage. `RunStoreFailure` bei beschädigter Datei.
- `RunSubjectCodec.kt`: strenges JSON-Schema 3 (`SCHEMA`). Schema 1 wird migriert, Schema 2
  gelesen; ein `tool`-Eintrag ist erst ab Schema 3 gültig.
- Regeln: Timeout oder unklare Antwort ergibt `UNKNOWN`, lokaler Abbruch `CANCELLED`. Keine
  automatische Wiederholung oder Editorübernahme nach einem Neustart. Workflow-Namen werden nicht
  ins Journal geschrieben. Ein Elternlauf entsteht vor dem Workflow, Kindaufrufe verweisen auf
  Parent, Definition, Revision und Schritt-ID. Speicherformat: `docs/STORAGE.md`.
- Tests: `EncryptedActionRunStoreTest`; `TestRunJournal.kt` ist das Test-Double für Runner-Tests.

## Workflows und Skill Keys

> Gespeicherte Abläufe, ihre Ausführung nach Freigabe und die drei Skill-Tasten. Format: docs/WORKFLOWS.md.

- `WorkflowStore.kt`: Laufzeitdatei „agent_workflows.json“ in CE/noBackup, höchstens zehn Abläufe. Jede
  Mutation verlangt die angezeigte Generation (Schutz vor veralteten Schreibzugriffen), auch ein
  identischer oder leerender Import erzeugt eine neue Generation.
  `WorkflowRecovery` setzt nur dieselbe unlesbare Datei (gleiche SHA-256) nach Bestätigung
  zurück; der Fingerabdruck liest höchstens 1 MiB, größere Dateien bleiben unangetastet.
  `WorkflowConfiguration` ist der gelesene Stand.
- `ManualWorkflowDraft.kt`: bearbeitbarer, unvollständiger Entwurf; die Umwandlung ist die
  einzige Validierungsgrenze und prüft die Modellkennung gegen den Provider des Schritts
  (`CloudProviders.validModel`). Der Editor setzt beim Providerwechsel das Modell dieses
  Providers ein. `proposal()` macht aus einem Builder-Ergebnis Revision 1.
- `ApprovedActionRunner.kt`: Main-Thread-Steuerung eines freigegebenen Laufs (Einzelaktion oder
  Workflow). Epoch-Prüfungen schützen vor späten Antworten, das Gesamtbudget beträgt 310 s (`ModelBudget.WORKFLOW_TIMEOUT_MS`).
- Zusammenfassungen für Freigabe und Anzeige: `WorkflowApprovalSummary.kt`,
  `WorkflowInputSummary.kt`, `WorkflowConditionSummary.kt`.
- `SkillBindingStore.kt`: Belegung der Slots 1–3. Fehlende Workflow-IDs bleiben ohne Ersatz
  stehen.
- Regeln: Import und Speichern führen nichts aus. Ein Alias erteilt keine Berechtigung. Ein
  Skill-Druck liest die aktuelle Definition und verlangt eine einmalige Freigabe.
- Tests: `WorkflowStoreTest`, `ManualWorkflowDraftTest`, `ApprovedActionRunnerTest`,
  `ApprovedWorkflowRunnerTest`, `WorkflowConditionSummaryTest`, `SkillBindingStoreTest`.

## Werkzeuge

> Fest eingebaute Werkzeuge: lokales Testwerkzeug und Übergabe eines Kalenderentwurfs an eine Kalender-App.

- Ablauf: Werkzeug im Panel wählen → `Tools.prepare` leitet die Felder aus der Auswahl ab →
  `ToolApprovalSummary.kt` zeigt Ziel und jeden Wert → ein eigener Klick → `startTool` in
  `ApprovedActionRunner.kt` schreibt die Absicht ins Journal (`RunSubject.Tool`) und ruft erst
  danach den `ToolLauncher` genau einmal auf.
- `ToolLauncher.kt`: `ToolLauncher` (die eine externe Wirkung), `CalendarToolLauncher`
  (`Intent.ACTION_INSERT` auf `CalendarContract.Events.CONTENT_URI` mit ausschließlich Titel und
  Beschreibung, ohne festes Zielpaket, ohne Kalender-Berechtigung), `ToolOutcome` (`Text` oder
  `HandedOff`), `ToolUnavailable` (keine App hat angenommen).
- Regeln: Eine Übergabe heißt nie „gespeichert“; die Tastatur liest den Kalender nicht. Ein
  Werkzeugbericht ist nie ersetzbar (`canApply` bleibt `false`). Ohne dauerhaftes Journal
  (API 21/22) läuft nur das Testwerkzeug, die Übergabe wird abgelehnt. Fehlt eine Kalender-App,
  endet der Lauf als `FAILED`, nicht als `UNKNOWN`, und wird nicht wiederholt.
- Tests in `AT/`: `ToolRunnerTest`, `CalendarToolLauncherTest`.

## Befehle

> Getippte Befehle wie `@math`: Erkennung vor der Schreibmarke, Chips in der Leiste, Ausführung erst nach Tipp.

- `C/Commands.kt`: `CommandSyntax.parse` erkennt im Text vor der Schreibmarke einen Befehl
  (`CommandNames` = Präfix mit angefangenem Namen, `CommandCall` = vollständiger Name mit
  optionalem Argument). Präfix nur am Anfang, nach Leerraum oder öffnender Klammer, Name aus
  ASCII-Buchstaben, Argument höchstens 200 Zeichen (`MAX_ARGUMENT`) auf derselben Zeile; nur
  Befehle mit `Command.longArgument` (`@snip`) dürfen 500 (`MAX_LONG_ARGUMENT`,
  `parse(…, longArguments)`). Etappe 59: Befehle mit `Command.aiArgument` (`@ki`, `@translate`)
  dürfen 2000 (`MAX_AI_ARGUMENT`, `parse(…, aiArguments)`) und als einzige über mehrere Zeilen
  (jeder andere Befehl endet am Zeilenumbruch; `parse` sucht deshalb über Zeilenumbrüche
  zurück, `parseAt` lehnt sie bei allen anderen ab). `LOOKBACK` bleibt bei der 500er-Grenze: die
  normalen Leser (`guardsTextBefore` aus InputLogic, `updateGuard`, Chips) lesen 519 + 1 Zeichen,
  damit der Cache der Eingabeverbindung (1024) auch in langem Text trifft. Nur solange ein
  AI-Befehl getippt wird, reicht das Lesen bis zu seinem Anfang (`LONG_LOOKBACK` = 2018, siehe
  `KeyboardCommands.readLength`, P244); `parse(…, lookback)` und `Active.parse` schließen aus der
  gelesenen Länge, ob das Fenster kurz oder lang ist. Erlaubte Präfixe
  (`ALLOWED_PREFIXES`) sind nur Zeichen, die in `symbols_word_separators` fehlen; sonst wäre der
  Befehl beim Tippen kein einzelnes Wort und die Autokorrektur könnte ihn ändern. Etappe 52:
  `aliases` (zweite Namen, nur ganz, nach `LanguageNames.normalize`, Umlaute auch als ae/oe/ue,
  nie angefangen, damit `@sp` Spanisch bleibt; `CommandCall.alias` trägt das getippte Wort, anders
  als `claimed` mit Argument) und `help` (`@?` am Wortanfang ergibt `CommandNames` mit `typed` =
  `CommandSyntax.HELP`); `isCommandWord` kennt die zweiten Namen.
- `A/KeyboardCommands.kt`: Einstellungen (`AgentPreferences.COMMANDS`, `COMMAND_PREFIX`,
  `COMMANDS_DISABLED`), die Liste der eingebauten Befehle (`BUILT_IN`, je Etappe einer; Befehle
  kommen nie aus Dateien oder Modellen), `find` (aus in Passwortfeldern, im Inkognito-Modus, bei
  gesperrtem Gerät und bei Auswahl), `chipsFor` und `chipRow`. Ein Namens-Chip vervollständigt
  den Namen, ein Befehl liefert eigene Chips; erst `Chip.onTap` handelt. `Chip.kind`: `ACTION`,
  `MUTED` (halbe Deckkraft: Abbrechen, Verwerfen, Fehler), `LOADING` (nicht antippbar, davor
  `A/LoadingDotsView.kt`: drei Punkte in Akzentfarbe, Welle 1,2 s mit 150 ms Versatz, läuft nur
  angehängt und steht bei `ANIMATOR_DURATION_SCALE` 0 still wie `FlatStyle.animationsEnabled`),
  `RESULT` (ohne Breitengrenze, die Zeile scrollt). Ein Befehl darf über
  `Host.replace` nur seinen eigenen Text ersetzen, und nur wenn der Editor ihn unverändert vor
  der Schreibmarke zeigt. Die Leiste zeigt die Chips über `setExternalSuggestionView`; der
  nächste normale Vorschlag ersetzt sie.
- Leiste, Reihenfolge in `LatinIME.setNeutralSuggestionStrip`: Befehls-Chips (mit Formatwächter
  41, Feld-Vorschlägen 45, Gedächtnis 46, Emoji-Mix aus `KeyboardCommands.buildChips`), Symbol-Chips
  (27), Auswahl-Chips (82, `A/SelectionChips.kt`: bei jeder Markierung Kopieren, Ausschneiden, Einfügen,
  Löschen; ersetzen Wortvorschläge, solange markiert ist; Texte in `agent_selection_strings.xml`, Englisch und Deutsch), Zwischenablage-Pille mit Smart Paste (40), dann der
  Update-Chip (54, `UpdateChip`), zuletzt Satzzeichen oder leer. In `setSuggestedWords` kommt der Update-Chip nicht vor: Wortvorschläge und
  Vorhersagen verdeckt er nie (Entscheidung des Leads, P212). Seit 56 findet ihn die Prüfung beim
  Öffnen; `UpdateOnOpen.refresh` ruft dann einmal `setNeutralSuggestionStrip`, aber nur, wenn die
  Tastatur noch im selben Feld steht und die Leiste zuletzt leer war (`LastField.stripNeutral`), sonst
  kommt er mit der nächsten neutralen Leiste (P238).
- Vorlagen: `Command.templates(host)` liefert `Template(label, text)` (Standard: keine). Bei
  `CommandCall` mit leerem Argument hängt `chipsFor` nach den eigenen Chips des Befehls
  `templateChips` an (`Chip.Kind.TEMPLATE`: umrandet, Text leicht gedämpft, Beschreibung
  `agent_command_template` „Vorlage: …“; leer in gesperrten Feldern). Ein Tipp ersetzt über
  `Host.replace` genau den getippten Befehl durch „Präfix + Name + Leerzeichen + `text`“; die
  normale Leistenaktualisierung nach dem Ersetzen zeigt dann die Chips für das Argument. Vorlagen
  haben `@math` (√, π), `@zeichen`/`@emoji` (Gruppen aus `SymbolTable.groups`), `@conv`
  (`ConvertCommand.PAIRS`), `@datum`/`@termin` (nach `DateFormatting.Language`) und
  `@translate`.
- Schutz vor Korrektur und Lernen: Ohne URL-Erkennung gehört das Präfix nicht zum getippten
  Wort, die Engine sieht nur `math`. `guardsTextBefore` entscheidet deshalb nach dem Text vor der
  Schreibmarke (Name angefangen oder vollständig ohne Argument, oder Argument eines Befehls mit
  `protectsArgument`). `InputLogic` prüft das beim Lernen direkt; für `Suggest` (Worker-Thread)
  setzt `updateGuard` bei jeder Leistenaktualisierung ein `@Volatile`-Flag, auch ohne sichtbare
  Leiste. Steht dabei kein Befehl vor der Schreibmarke (oder ist Text markiert), ruft `updateGuard`
  `forget()`: Gefundenes bleibt auch ohne Leiste nicht im Speicher. Liefert ein Neuaufbau
  (`Host.refresh`) keine Chips mehr, zeigt die Leiste wieder ihren Normalzustand
  (`setNeutralSuggestionStrip`) statt der alten Zeile.
- Ersetzen (`RichInputConnection.replaceTextBeforeCursorIfUnchanged`): vorher relativer Text und,
  wo der Editor es meldet, die absolute Schreibmarke (`getExtractedText`); nachher das Zeichen
  vor dem Befehl plus Ersatz und wieder die Schreibmarke, sonst UNKNOWN. UNKNOWN sperrt die
  Eingabe über `AgentEditorController.onUnclearEdit`; ein unklarer Versuch lässt nie einen
  Composing-Bereich stehen (P25). Ein Chip von vor dem letzten Tastendruck wird abgelehnt, bevor
  das getippte Wort abgeschlossen wird.
- `K/settings/screens/AgentCommandsScreen.kt`: Hauptschalter, Präfix, Schalter je Befehl.
- `@math` (24b): `MathCommand` in `A/KeyboardCommands.kt` bietet bei gültigem Argument einen
  Ergebnis-Chip (ersetzt den Befehl durch den formatierten Text) und „Rechner“
  (`AgentEditorController.openCalculator` mit Befehlstext und Ende; das Feld bleibt bis
  „Einfügen“ unverändert, dann ersetzt `InputLogic.replaceCommandText` den Befehl, sonst Einfügen
  an der Schreibmarke). Grund: Erst löschen und dann öffnen ließ den Rechner auf dem Telefon nicht
  erscheinen; wahrscheinlich startet eine App die Eingabe neu, wenn ihr Feld leer wird, und das
  schließt alle Overlays (Fallstrick P43). `C/MathExpression.kt`: eigener Parser
  (Punkt vor Strich, `^` rechtsassoziativ und stärker als das Vorzeichen, `%` = durch 100,
  `√`, `∛`, `²`, `³`, `!`, Konstanten `π`/`e`, Funktionen aus `FUNCTIONS` mit `;` als
  Argumenttrenner, Winkel in Grad, implizites Malnehmen `2π` als `Binary(implicit = true)`),
  exakt mit `BigDecimal`; Funktionen ohne exakten Weg (Winkel, Logarithmen, n-te Wurzeln) in
  `double`, auf 12 Stellen gerundet, nur bei kurzem Ergebnis ohne „≈“ (sin 30° = 0,5), Nullen
  unter 1e-12 werden zu 0 (cos 90°); `UNDEFINED` für ln(0), tan(90°), (−1)!; Division ohne endliche Dezimaldarstellung, Wurzeln
  und gebrochene Exponenten mit 40 Stellen und als ungefähr markiert; Anzeige höchstens 10
  Nachkommastellen. Grenzen gegen Rechenlast: Eingabe 200 Zeichen, Exponent ±999, Abschätzung
  vor `pow`, Beträge über 10^100 sind „zu groß“. Ausgabe in vier `Style`s mit Komma oder Punkt
  nach der Sprache (`Locale.forLanguage`), Gruppierung ab fünf Stellen.
- `A/CalculatorView.kt`: Overlay in `AgentKeyboardFrame` (Kind `agent_calculator`, auch in
  `InputView` als Overlay gezählt), Farben aus dem Tastatur-Farbsatz, eigene Tasten, Wahl der
  Ausgabe (gemerkt in `AgentPreferences.CALCULATOR_STYLE`). „Einfügen“ geht über
  `editor.captureAnchor` und `editor.insert` wie das Diktat (Prüfung von Token, Position,
  Schutzgrenzen; generierter Text, also ohne Lernen). `isBlockingInput` gilt, solange der Rechner
  offen ist; `close()` und jeder Editorwechsel schließen ihn.
- Tests: `CommandSyntaxTest` und `MathExpressionTest` (`CT/`), `KeyboardCommandsTest`, `CommandTemplatesTest`, `CommandReplaceTest` und
  `CalculatorViewTest` (`AT/`), `InputLogicTest.typedCommand*`.

- **Wort links beim Zurückgehen und Löschen (Etappe 96, ersetzt 94)**: `K/agent/CurrentWordShown.kt` ordnet die Vorschläge, solange ein Wort per Rücktaste bearbeitet oder wieder aufgenommen ist (`InputLogic.mLastKeyWasDelete`, `WordComposer.isResumed`): Index 2 (links) das linke Wort als `KIND_TYPED`, Index 1 (Mitte) und 3 ff. die übrigen Vorschläge, ohne Vorschlag Index 1 = das Wort; eine anstehende Autokorrektur behält ihre Ordnung. Das linke Wort wählt `InputLogic.getSuggestedWords`: kennt `K/agent/WordBeforeDelete.kt` (Feld `InputLogic.mWordBeforeDelete`; gesetzt in `handleBackspaceEvent`, verworfen in `onCodeInput` bei jeder anderen Taste, `startInput`, `onUpdateSelection`) ein Original, das Wort wie es steht, sonst die getippte Form (Etappe 48) oder das Wort. Langdruck auf das linke Wort: `SuggestionStripView.restoreTouch` (eigener Timer, `mKeyLongpressTimeout`; `onLongClick` gibt diesen Langdruck nicht an die Liste weiterer Vorschläge) → `LatinIME.restoreWordBeforeDelete` → `InputLogic.restoreWordBeforeDelete`. Tests `AT/CurrentWordShownTest.kt`, `AT/WordBeforeDeleteTest.kt`, `InputLogicTest` („long press restores it“).
- **Zuletzt benutzte Emojis abgegrenzt (Etappe 97)**: `EmojiPalettesView.PagerAdapter` setzt im `ConcatAdapter` vor und nach den Adapter der Zuletzt-Kategorie je einen `K/keyboard/emoji/RecentsMarkAdapter.kt` (Überschrift, Trennlinie; eigene View-Typen 1/2, ein Eintrag nur mit zuletzt benutzten Emojis, `recentsChanged` schaltet um). Listenposition ↔ Kategorie und Seite rechnet `K/keyboard/emoji/EmojiListPositions.kt` (`pagePosition`, `scrollTarget`, `at`) statt `startOf`/`categoryAt` von 93b; Test `app/src/test/java/helium314/keyboard/keyboard/emoji/EmojiListPositionsTest.kt`. String `emoji_recents_heading` in `agent_emoji_mix_strings.xml` (en/de).
- **Emoji-Suche (Etappe 93a)**: `K/keyboard/emoji/EmojiSearchIndex.kt` sucht nach Name und Stichwort in `assets/emoji_search/search_<de|en|es>.tsv` (Unicode CLDR, erzeugt von `tools/emoji/build_search_index.py`; Rang: ganzer Name, Wort des Namens, Stichwort, Wortanfang, enthalten; Gleichstand: sehr häufige Emojis (`COMMON`, nach Unicodes Häufigkeitsliste 2021, Reihenfolge ungefähr), kürzerer Name, Reihenfolge der Kategorien; eigener Ordner, weil Tests jede Datei in `assets/emoji/` als Kategorie-Liste lesen; Umlaute/Akzente egal); Test `AT/../keyboard/emoji/EmojiSearchIndexTest.kt`. `EmojiSearchActivity` zeigt die Treffer in einem `ScrollView` (Fenster 2 Reihen, `DynamicGridKeyboard.ofKeyCount`, `MAX_RESULTS` 400) und sendet beim Tipp sofort (`sendDone`, ohne Schließ-Animation); `LatinIME.onEmojiSearchDone` hält das Ergebnis, solange noch das Suchfeld bedient wird (`mPendingEmojiSearchDone`), und setzt es beim nächsten `onStartInputViewInternal` des App-Felds ein (`deliverPendingEmojiSearchDone`, höchstens 5 s). Ohne Index bleibt die alte Wörterbuchsuche.
- Weitere Befehle (Etappe 24c–24f), jeder in einer eigenen Datei, Reihenfolge in `BUILT_IN`:
  - `A/ContactCommand.kt` (`@contact`): `rank` (gleich, beginnt mit, Wortanfänge, enthält; höchstens
    6), austauschbare `source`, echte Quelle `PhoneContacts` (`ContactsContract`, Projektion und
    Grenzen 30/20, keine Logs von Namen oder Nummern), Feldauswahl nur für denselben Befehlstext an
    derselben Schreibmarke (`Host.cursor`, `Host.refresh`). Abfragen nie auf dem Main-Thread:
    `Worker` (`BackgroundWorker`, ein Hintergrund-Thread), Name 150 ms nach dem letzten Tastendruck
    (`DEBOUNCE_MS`, ein neuer Tastendruck bricht ab), Felder sofort; das Ergebnis kommt über
    `Host.showChips`, nur wenn derselbe Befehl noch vor derselben Schreibmarke steht; `reset` erhöht
    eine Generation, späte Antworten verfallen. Bis dahin bleiben die alten Treffer, die noch passen. Berechtigung `READ_CONTACTS` steht
    schon im Upstream-Manifest und gilt auch für das Kontakte-Wörterbuch.
  - `A/commands/UrlCommand.kt` und `C/UrlDetector.kt` (`@url`): strenge Erkennung (http/https
    oder bekannte Endung, keine Dateinamen, Versionen, Benutzerdaten); Vorschläge aus
    Zwischenablage (nur Text, nicht sensibel), Feld vor und nach der Schreibmarke (je 2000
    Zeichen), einmal je Befehlstext und Schreibmarke gelesen (Cache, `reset`); Öffnen vor Entfernen
    (P43). `A/commands/ScreenReaderService.kt`: Bedienungshilfe-
    Dienst ohne abonnierte Ereignisse (`res/xml/agent_screen_reader.xml`, Fallstrick P44), liest
    nur nach einem Tipp einmal die App-Fenster, überspringt Passwort-, sensible und eigene
    Knoten, höchstens 3000 Knoten/20 000 Zeichen; Brücke über ein statisches `connected` im
    selben Prozess; Ergebnis über `Host.showChips` (nur solange dieselbe Chip-Zeile steht), ohne
    Adressen, die die Zeile schon zeigt.
    Einstellungen: `K/settings/screens/AgentScreenReaderSection.kt`.
  - `A/SearchCommand.kt` (`@such`, `ACTION_WEB_SEARCH`, sonst DuckDuckGo; austauschbarer
    `launcher`), `A/CharacterCommand.kt` (`@zeichen`, `C/SpecialCharacters.kt`),
    `A/EmojiCommand.kt` (`@emoji`, `C/EmojiNames.kt`; HeliBoards Emoji-Suche braucht ein
    nachzuladendes Wörterbuch), beide über `C/SymbolTable.kt` (ä→a, ß→ss, exakt vor Präfix,
    höchstens 12; `search` zeigt zuerst eine ganze Gruppe, `Group` mit Name und Aliassen, nur
    über den vollen Namen); `A/ConvertCommand.kt` (`@conv`, `C/UnitConversion.kt`, exakt mit `BigDecimal`,
    US-Maße, „Pfund“ = lb; ein Paar ohne Zahl wie „oz in g“ rechnet 1 Einheit, eine Zahl
    dahinter ist der Wert: „oz in g 5“).
  - `A/commands/SnipCommand.kt` und `A/commands/SnippetStore.kt` (`@snip`, CE-Datei,
    Einstellungen `K/settings/screens/AgentSnippetsSection.kt`). Etappe 42 (Textbausteine 2.0):
    Text = `SnippetTemplate`, erst beim Tipp erweitert (Uhr `clock`/`zone`, Sprache aus
    `Host.language`, Zwischenablage `clipboard` nur bei `{zwischenablage}`). `SystemClipboard`:
    nur mit Verlauf an, nicht gesperrt, nur Text, nie markiert vertraulich oder in
    `SensitiveClips`, höchstens 2000 Zeichen; sonst leer plus Toast (`ClipRead.Missing`). Felder
    `{?Name}`: Der Tipp ersetzt den Befehl durch „@snip name Feld: “ (`argumentStart`), der Wert
    ist das Argument (geschützt, nicht korrigiert, nicht gelernt); `Filling` hält Werte und Index
    nur im Speicher, solange genau dieser Befehl vor der Schreibmarke steht (`reset`). Enter
    (`Command.onEnter`, `KeyboardCommands.onEnter` aus `LatinIME.onEvent` und, für Hardware-Enter,
    `KeyboardActionListenerImpl.onKeyDown`) oder „Weiter“ fragt das nächste Feld, das letzte fügt
    ein; „Abbrechen“ stellt „@snip name“ her. Enter während einer Feldabfrage wird immer genommen,
    `next` aber nur bei `Host.canReplaceNow` (P149). `longArgument`: Name, Feld und Wert bis
    `MAX_LONG_ARGUMENT` (500), ab `ROOM_WARNING` (40) Restzeichen „(noch N Zeichen)“ im Feld-Chip
    (`roomLeft`), vom Wert nur die letzten `LABEL_VALUE` (40) Zeichen; darüber ist es kein Befehl
    mehr und die Werte sind weg. `{cursor}`:
    `Host.moveCursorBack` nach erfolgreichem `replace`. `apps` filtert nach
    `EditorInfo.packageName` (`SnippetApps.allows`). Alte Bausteine (`storageVersion` 1) sind
    `literal` und werden wörtlich eingefügt, bis sie im Editor gespeichert werden. Editor:
    `SnippetEditor` mit Platzhalter-Reihe (fügt an der Auswahl ein), Warnungen aus `issues`,
    Live-Vorschau `SnipCommand.editorPreview` (Felder als ‹Name›, Zwischenablage nur als
    Platzhaltertext, nie gelesen), Apps über `PackageManager.queryIntentActivities` (Launcher,
    nur Paketname und Name, keine Symbole, auf IO) oder getippt. Texte in `agent_snippet_strings.xml` (`values/`, `values-de/`). Tests
    `AT/SnipCommandTest.kt` (mit `SnippetStoreTest`),
    `InputLogicTest.typedSnippetFieldTakesEnterThenInsertsAndPlacesTheCursor`. `A/commands/ClipCommand.kt`
    (`@clip`, nur mit eingeschaltetem Verlauf, nur Text; überspringt den aktuellen vertraulichen Clip
    und alles, was `A/commands/SensitiveClips.kt` als vertraulich kopiert vermerkt hat: eigener
    Zwischenablage-Listener aus `LatinIME`, Markierung `IS_SENSITIVE` oder Passwortfeld als
    aktueller Editor, gesalzene SHA-256 nur im Speicher seit der Nachprüfung 44/45, vorher in
    DE-Einstellungen, Speicher: `docs/STORAGE.md`; vor Etappe 24f kopierte Clips bleiben unerkannt), `A/commands/WorkflowCommand.kt` (`@wf`,
    `Host.openWorkflow` → `AgentEditorController.openWorkflow`; ändert das Feld nicht; `false`
    ergibt einen Toast). Ohne Auswahl (`NO_SELECTION`/`COMPOSITION`, `WAITS_FOR_SELECTION`) merkt
    sich der Controller nur die ID (`chosenWorkflowId`) und zeigt „Workflow vorgemerkt“; das
    nächste `open()` (Stift, Rad ohne Aktion) mit Auswahl bereitet ihn wie eine Skill-Taste vor
    (`prepareStoredWorkflow`, Freigabe wie immer) und verbraucht die Wahl. Verworfen bei
    `startEditor`/`finishEditor` und bei jeder anderen Ablehnung (Inkognito, Sperre, Passwortfeld);
    Schließen des Panels behält sie. Interne Öffner (Rad mit Aktion, Skill-Tasten) nutzen `capture()`
    und übergehen sie.
  - `A/DateCommand.kt` (`@datum`), `A/EventCommand.kt` (`@termin`, Entwurf über
    `CalendarToolLauncher.eventDraft`, Entfernen erst nach erfolgreichem Start), `A/PlaceCommand.kt`
    (`@ort`, Maps-Link einfügen oder `geo:` öffnen) mit `A/SavedPlaces.kt` (CE) und
    `SavedPlacesSection.kt`; Logik in `C/CivilDate.kt` (Datum ohne `java.time`, Fallstrick P45),
    `C/DateFormatting.kt` (de, en-US, es), `C/EventParser.kt` (Datum, Uhrzeit, Titel aus Freitext,
    „jetzt“ und Zeitzone übergeben), `C/Places.kt`. `@ort` prüft selbst `isBlocked` und liest die
    Orte einmal je Befehl (`reset`, `SavedPlaces.store` verwirft den Cache).
  - `A/commands/AskCommand.kt` (`@ki`) und `A/commands/AiCommandChips.kt` (Variante A, zwei
    Tipps ohne Panel): `AiCommandChips.chips` zeigt je nach `CommandAiRequest.state` den
    Senden-Chip (nennt den Anbieter, `Host.aiProvider`), `Running` (Lade-Chip, „Abbrechen“),
    `Answer` (Ergebnis-Chip, „↻“, „✕“) oder `Failed` (ein grauer Chip). Der Tipp auf den
    Senden-Chip ruft `Host.sendAi` → `AgentEditorController.sendCommandRequest`: Frage als
    `TextSnapshot` mit leerer Auswahl (`ExecutionPolicy.captureQuestion`, `isCurrentQuestion`:
    gleiche Sitzung und Schutzregeln, Composition erlaubt; `apply` lehnt solche Snapshots ab), die
    Freigabe (`CloudApproval`, `AiTextAction.ASK`, kein `BuiltinAction`) entsteht und vergeht im
    Tipp mit genau einem `cloudRunner.start`. Der Zustand gehört zu Befehlstext und Schreibmarke
    (`commandRequest`, wie die Feldauswahl von `@contact`): `Host.aiRequest` liefert ihn nur dafür
    und hängt `onChange` an die zuletzt gebauten Chips (übersteht normale Leistenaktualisierungen);
    jede andere Kombination, `selectionChanged` mit anderer Schreibmarke, `reset`
    (`KeyboardCommands.dropAiRequests`, über `KeyboardCommands.aiRequests`), ein geschützter Editor
    und `close()` brechen den Lauf ab und verwerfen die Antwort (P2). Der Tipp auf das Ergebnis
    (`insertCommandAnswer`) ersetzt über `InputLogic.replaceCommandText(…, generated = true)`
    (Lernen stoppt wie bei anderen KI-Übernahmen), sonst `captureAnchor`/`insert`. Ein Fehler-Chip
    öffnet `openCommandRequestDetails`: fehlender oder abgelehnter Schlüssel, fehlendes Modell →
    KI-Einstellungen; sonst `openQuestion` (das Panel als Detailansicht mit derselben Anfrage, neuer
    Freigabe über „Senden“ und dem Grund über `AgentPanelView.showQuestionNote`). Solange eine Frage
    im Panel offen ist, sind Auswahl-Aktionen gesperrt (`asking`); die Laufliste der
    KI-Einstellungen titelt `ASK` über `textActionTitle` (ohne `BuiltinAction`, sonst Absturz).
    Tests `CT/QuestionCaptureTest.kt`, `AskCommandTest` und `LoadingDotsViewTest` (`AT/`),
    `InputLogicTest` (Frage-Fälle, Leiste und Panel).
  - `A/commands/TranslateCommand.kt` (`@translate "Text" Stil`): `StyledTranslation.parse`; ohne
    geschlossenes, nicht leeres Anführungszeichenpaar nur ein Hinweis-Chip (Tipp zeigt einen Toast,
    sonst nichts). Sonst dieselben Chips wie `@ki` (`AiCommandChips`) mit
    `AiTextAction.TRANSLATE_STYLED` und `StyledTranslation.request` als Anfrage.
    `sendCommandRequest` und `openQuestion` nehmen nur `ASK` und `TRANSLATE_STYLED` an. Die
    Panel-Vorschau (Detailansicht) zeigt die ganze Anfrage ungekürzt (`showQuestion` hebt
    `maxLines` auf; `asking`, `questionAction`, eigene Texte über `translating`).
    `longArgument = true` (500 Zeichen). Laufliste: `textActionTitle` → „Übersetzen mit Stil“.
    Tests `AT/commands/TranslateCommandTest.kt`, `CT/StyledTranslationTest.kt`,
    `InputLogicTest.typedTranslation*`.
  - `A/commands/QrCommand.kt` (`@qr`): ZXing `QRCodeWriter` (Fehlerkorrektur M, Ruhezone 4,
    512 px), erzeugt erst nach dem Tipp auf einem Hintergrund-Thread. Einfügen über
    `ScreenshotChip.editorAccepts`/`isSameField` und `SYSTEM_SINK` (Datei unter `files/agent_qr/`,
    Pfad in `clipboard_provider_path.xml`), erst danach `removeAfterInsert` → `Host.replace` (P43,
    P83); sonst Teilen über die
    Auswahl mit Leseberechtigung, Entfernen nach erfolgreichem Start. Austauschbar: `sink`,
    `launcher`, `blocked`. `SYSTEM_SINK` (auch für Emoji-Mix) committet wie
    `KeyboardActionListenerImpl.onContent`, aber selbst über `InputConnectionCompat.commitContent`
    und meldet die Antwort des Editors: false bei blockierter Eingabe, fremdem Typ oder Ablehnung
    (dann Toast `agent_image_not_inserted`, Text bleibt). `removeAfterInsert` liest danach den Text
    vor der Schreibmarke beim Editor (`Host.editorTextBeforeCursor`, nicht aus dem Cache) und
    ersetzt nur, wenn er unverändert ist (P83). Test `AT/commands/QrCommandTest.kt` (dekodiert den Code mit ZXing),
    mit echtem Host `InputLogicTest.typedCommandImageSinkReportsTheFieldsAnswer`.
    Robolectric: `FileProvider` merkt sich seine Wurzeln JVM-weit, der Test leert `sCache`.
  - `A/commands/AlarmCommand.kt` (`@wecker`) und `A/commands/TimerCommand.kt` (`@timer`), dazu
    `ClockCommands` (in `AlarmCommand.kt`: Start, dann Entfernen; Hinweis-Chip `MUTED`, dessen Tipp
    nur einen Toast zeigt). `AlarmClock.ACTION_SET_ALARM` (`EXTRA_HOUR`, `EXTRA_MINUTES`,
    `EXTRA_MESSAGE` nur mit Titel) bzw. `ACTION_SET_TIMER` (`EXTRA_LENGTH` in Sekunden), immer
    `EXTRA_SKIP_UI = false` und `FLAG_ACTIVITY_NEW_TASK`; austauschbarer `launcher` (wie
    `SearchCommand`), Entfernen erst nach erfolgreichem Start (P43). Logik in `C/AlarmParser.kt`
    (`EventParser.parse(…, alarm = true)`: umgangssprachliche Zeiten bleiben morgens, eine nackte
    Zahl ist eine Stunde; `dayIgnored`, wenn der getippte Tag nicht das nächste Mal der Uhrzeit
    ist, denn `ACTION_SET_ALARM` kennt nur Stunde und Minute, `EXTRA_DAYS` wäre ein
    wiederkehrender Wecker) und `C/TimerParser.kt` (Einheiten de/en/es, Hälften, `h:mm:ss`,
    höchstens `MAX_SECONDS` = 24 h). Berechtigung `SET_ALARM` im Manifest. Texte in eigener Datei
    `agent_alarm_strings.xml` (`values/`, `values-de/`).
  - Sprachbefehle (24g): `A/commands/LanguageCommand.kt` (`@lang`) und `C/LanguageNames.kt`
    (`LanguageNames`: `normalize` ohne Akzente, ß→ss, nur a–z; `allowedEdits` 0/1/2 nach dem
    kürzeren Wort (bis 2, bis 5, ab 6 Zeichen); `distance` Damerau-Levenshtein (OSA); `isClose`
    (bis 4 Zeichen nur ein Vertauschen zweier Nachbarn); `isTypo` = `isClose` plus beide Wörter
    ab 4 Zeichen und gleicher erster Buchstabe; `isNear` = die lockerere erste Regel, nur für
    `reserved`. `classifiedAliases` aus `java.util.Locale` mit `AliasKind`: `CODE` (ISO-639-1/-3,
    nur exakt), `COUNTRY` (Ländernamen aus `COUNTRIES` (kuratiert) und dem Land des Subtyps in en,
    de und der eigenen Sprache; exakt oder Präfix, keine Tippfehler; Ländercodes wie `us`, `ca`
    sind keine Aliasse), `SHORT` (`SHORT_FORMS`: Owner-Kürzel `sp`, `ger`, `eng`, dazu `usa`,
    `uk`, `england` …), `NAME` (Sprachname in `DISPLAY_LANGUAGES` (en, de, es, fr, it, pt) und in
    sich selbst); deutsche Umschrift ä/ö/ü → ae/oe/ue als zweite Form. „In sich selbst“ hilft nur
    Sprachen in lateinischer Schrift: `normalize` macht „Русский“ zu nichts, solche Sprachen
    treffen nur über Code und die Namen in den sechs Anzeigesprachen. `aliases` = nur die
    Schlüssel. `LanguageMatcher`: allgemeine Wörter (`GENERIC`, z. B. „sprache“) = alle; exakter
    Alias vor allem; 2 Zeichen nur exakt; ab 3 Zeichen Präfix (kein `CODE`); ab 4 Zeichen
    Tippfehler (`isTypo`, nur `NAME`/`SHORT`); `match(…, exactOnly)` nur allgemein und exakt;
    Aliasse gleich oder nah (`isNear`) an einem Befehlsnamen (`reserved`) nur exakt). Parser:
    `CommandSyntax.parse(…, fuzzyNames, claim)`, beides nur aus `KeyboardCommands`
    (`Active.parse`, alle Leser gleich). Reihenfolge: exakter Name, angefangener Name, Name mit
    Tippfehler (`closeNames` mit `isTypo`, `CommandNames.fuzzy`, nur vervollständigen, nie
    ausführen, auch nicht mit Argument), dann `claim`. Tippfehler und `claim` sind „schwach“: Die
    Suche rückwärts merkt sich den ersten schwachen Treffer und sucht weiter; ein früherer Befehl
    mit exaktem Namen auf derselben Zeile (innerhalb `LOOKBACK`, mit gültigem Argument) gewinnt
    (`@ki frag @matt`, `@termin Treffen mit @sp`, `@translate "…" @es`), sonst bleibt der schwache
    Treffer (P56). Ein beanspruchtes Wort nimmt kein Argument: `@sp hola` ist für den Parser kein
    Befehl mehr (danach normale Autokorrektur). Nur ein `claim` darf Nicht-ASCII-Buchstaben lesen
    (`@español`); `CommandCall.claimed` trägt das getippte Wort.
    `Command.claims(word)` (darf auf dem Vorschlags-Thread laufen) und `Command.load(context)`
    (aus `KeyboardCommands.load`, nur für eingeschaltete Befehle): `LanguageCommand` baut dort den
    Matcher aus den aktiven Subtypen (`SubtypeSettings.getEnabledSubtypes(true)`, austauschbar
    `subtypes`, Standard `defaultSubtypes` mit `runCatching`; „zz“ ohne Aliasse) und hält ihn
    `@Volatile`, zwischengespeichert nach den Sprach-Tags der aktiven Subtypen plus den
    Befehlsnamen der Registry (`matcherKey`; neu gebaut nur, wenn sich eins davon ändert).
    `isCommandWord` zählt beanspruchte Wörter mit (nicht gelernt). Chips: je aktivem Subtyp der
    Sprache einer („🌐 Español (ES)“, mit Layout, wenn dieselbe Locale mehrfach aktiv ist), ohne
    den Subtyp in Benutzung (`Host.currentSubtype`, echter Host:
    `RichInputMethodManager.currentSubtype.rawSubtype`); trifft nur dieser, ein grauer Chip „Schon
    aktiv: Deutsch (DE)“, dessen Tipp nichts tut. Nach dem Leerzeichen (`call.text` endet auf
    „ “) nur noch exakte Treffer (`exactOnly`). Tipp: erst prüfen, ob der Subtyp noch in
    `subtypes()` ist (sonst Toast, Text bleibt), dann `Host.replace(call, "")`, erst bei Erfolg
    `Host.switchSubtype` → `LatinIME.switchToSubtypeByCommand`: derselbe `switchToSubtype` wie die
    Sprachtaste, danach merkt sich `A/commands/SubtypeChoice.kt` (nur im Speicher) Feld (App,
    `fieldId`, Eingabeklasse) und Subtyp. `LatinIME.onStartInputInternal` überspringt die
    automatische Wahl nach Hint-Locales und App-Subtyp, solange dasselbe Feld neu startet und der
    gewählte Subtyp noch in Benutzung ist; ein anderes Feld oder ein anderer Subtyp beendet die
    Wahl (P66). `@lang` ohne
    Argument listet alle aktiven Sprachen als Chips (keine Vorlagen), mit Argument filtert er. Nur
    ein aktiver Subtyp: grauer Chip „Nur eine Sprache aktiv“ → Spracheinstellungen (`languages` in
    `AgentPreferences.SETTINGS_DESTINATIONS`). Texte in `agent_language_strings.xml` (`values/`,
    `values-de/`). Tests `CT/LanguageNamesTest.kt`, `CommandSyntaxTest` (Tippfehler, `claim`,
    Vorrang des früheren Befehls), `AT/commands/LanguageCommandTest.kt`,
    `InputLogicTest.typedCommandLanguageChipRemovesTheCommandThenSwitches` (echter Host),
    `InputLogicTest.typedCommandLanguageChoiceSurvivesTheAppRestartingTheField` (de → es → en → de
    im geleerten Feld mit deutschem Hint und App-Subtyp, Popup-Tasten von „a“),
    `AT/commands/SubtypeChoiceTest.kt`.
  - Emoji-Mix (25a, Fallstricke P70–P74, P83): `C/EmojiMix.kt` (rein, ohne Netz): `EmojiMixTable.read`
    liest die gzip-Tabelle aus `app/src/main/assets/emoji_mix_pairs.bin` (Format im Generator
    `tools/emoji_mix_table.py`, Quelle und Lizenzstand `docs/BUILD.md`): 619 Emojis, Bitmap der
    Paare (links, rechts) und ein Datumsindex je Paar, Rang über `rowStart`; jeder Codepunkt muss
    ein Unicode-Skalarwert sein (≤ 10FFFF, kein Surrogat), sonst gilt die ganze Tabelle als kaputt
    (ebenso im Generator). `find` sucht in
    Tabellenreihenfolge, sonst umgekehrt; `indexOf` vergleicht ohne FE0F/FE0E, dann ohne Hautton
    (die Tabelle hat keine Hauttöne). `EmojiClusters.split` (Codepunkt plus Variationsselektor,
    Hautton, Keycap, Tags, ZWJ-Folgen, Flaggenpaare). `EmojiMixUrls`: Bild-URL
    `https://www.gstatic.com/android/keyboard/emojikitchen/<datum>/u<l>/u<l>_u<r>.png` (Codepunkte
    mit „u“, durch „-“ verbunden; der Generator prüft das Muster an allen 293 586 Einträgen),
    `isAllowed` nur genau diese Form. `EmojiMixCache` (LRU nach `lastModified`, 20 MB, schreibt
    über `.tmp`), `PngCheck`, `EmojiMixTable.POPULAR` (Partnerliste).
    `A/commands/EmojiMixer.kt`: gemeinsamer Zustand je Paar (`Loading`, `Ready` mit Vorschaubild,
    `Missing` nach 404, `Failed`), höchstens 12 im Speicher (LRU), nur Main-Thread; Tabelle,
    Platte und Netz auf einem Worker. `request` ist der einzige Netzweg und nur nach einem Tipp:
    erst Cache, sonst genau ein `EmojiMixClient.fetch`; bei `Loading`/`Ready`/`Missing` nichts;
    jede Ausnahme im Worker endet in `Failed` (nie dauerhaft `Loading`). `state` liest ein
    gecachtes Bild ohne Netz nach. `thumbnail` dekodiert auf höchstens `THUMBNAIL_PX` (320 px,
    Abtastung plus Skalierung; die Bilder haben 534 px). `clearCache` erhöht `generation`: Ergebnisse
    von vorher eingereihter Arbeit werden verworfen, und die Ansichten bauen sofort neu.
    `listen("chips"|"palette")` baut nach jeder
    Änderung neu. `deliver`: `ScreenshotChip.editorAccepts(…, image/png)` → `sink`
    (`QrCommand.SYSTEM_SINK`; `afterInsert` nur nach true, sonst Toast), sonst Teilen über `launcher`; austauschbar `tableSource`, `calls`, `sink`,
    `launcher`, `blocked`, `toMain` (Übergabe an den Main-Thread, P73). `EmojiMixClient` (eigenes OkHttp `EmojiMixHttp`: keine Weiterleitung,
    kein Cookie, Zeitlimits 10/15/20 s): URL vor der Anfrage prüfen, 3xx = Fehler, 404 = `Missing`,
    nur `image/png` mit PNG-Signatur, höchstens 2 MB.
    `A/commands/EmojiMixCommand.kt` (`@mix`): ein Emoji → bis zu 6 Partner, zwei → Paar-Chip,
    sonst ein Hinweis. `stripChips` (aus `KeyboardCommands.buildChips`, wenn kein Befehl vor der
    Schreibmarke steht): nur bei eingeschalteten Befehlen und `STRIP_PREFERENCE`, letzte zwei
    Cluster der letzten 32 Zeichen, nur Paare der Tabelle; Ersetzen über `Host.replace` mit einem
    `CommandCall`, dessen `text` die zwei Emojis sind. `pairChips` je Zustand; vor dem Einfügen
    muss der erwartete Text unverändert vor der Schreibmarke stehen, entfernt wird erst nach dem
    Einfügen (P43); beim Teilen bleibt der Text der Leiste stehen, der Befehl geht. Vorschaubild
    über `ImageSpan` im Chip-Text (keine Änderung an `chipRow`).
    `A/EmojiMixPalette.kt`: Mixer-Modus der Emoji-Ansicht (Leiste, Auswahl höchstens 2, ein drittes
    ersetzt das zweite, Partnerzeile bei fehlender Mischung, Vorschau mit Gleit- und
    Skalier-Animation 220 ms, still bei `ANIMATOR_DURATION_SCALE` 0). Das zweite Emoji ist die
    Anfrage: `load` (einziger Netzweg der Ansicht) nur bei unbekanntem Zustand und höchstens
    einmal je Wahl (`requestedKey`, zurückgesetzt bei jeder Änderung der Auswahl, P74); ist der
    Zustand danach aus dem Speicher verschwunden, lädt erst ein Tipp auf „Tippen, um … zu laden“.
    Gesperrt: `toggle` zeigt einen Toast, `onEmoji` beendet den Modus und lässt das Emoji normal
    tippen, `load` (Partner, Wiederholen) beendet ihn ohne Anfrage. „Zuletzt gemischt“ (32,
    P88–P89): `A/commands/EmojiMixRecents.kt` (Paarschlüssel in `agent_emoji_mix_recent`, höchstens
    12, neueste zuerst; `record` aus `EmojiMixer.request` bei `Ready` (nur wenn beim Tipp und beim
    Ergebnis `recording()`) und aus `deliver` nach Einfügen oder Teilen (Parameter `mix`);
    `recording()` = nicht Inkognito (auch Passwort- und No-Learning-Felder), nicht gesperrt;
    `shown` filtert auf Dateien im Cache, liest dabei kein Bild; `picture` dekodiert 160 px auf dem
    Worker von `EmojiMixer` (`afterWork`, `thumbnail(file, maxPx)`) in eine eigene LRU, getrennt von
    `states`; `clear` aus `EmojiMixer.clearCache`). Im Palette-Mixer `recentsRow` (letztes Kind
    der Leiste, sichtbar bei weniger als zwei gewählten Emojis), Tipp = `insertRecent` (gesperrt:
    Modus aus; sonst `deliver` mit der Cache-Datei). `EmojiMixer.changed` ist dafür `internal`.
    Einstellungen:
    `K/settings/screens/AgentEmojiMixSection.kt`. Texte in `agent_emoji_mix_strings.xml`
    (`values/`, `values-de/`). Tests `CT/EmojiMixTest.kt`, `AT/commands/EmojiMixCommandTest.kt`,
    `AT/EmojiMixPaletteTest.kt`, `AT/EmojiMixRecentsTest.kt`, mit echtem Host
    `InputLogicTest.typedEmojiPairOffersTheMixAndReplacesItOnlyAfterTwoTaps` (P73) und
    `InputLogicTest.typedEmojiPairStaysWhenTheFieldDoesNotTakeTheImage` (echter Sink: Ablehnung,
    Bild als Objektzeichen an der Schreibmarke, blockierte Eingabe; P83).
  - Formatwächter (41, P138–P140, P148): `A/FormatGuardChips.kt`. `KeyboardCommands.buildChips` fragt
    ihn, wenn kein Befehl vor der Schreibmarke steht, vor dem Emoji-Mix (eine Zeile). `fieldKind`
    aus `EditorInfo.inputType` (E-Mail-Varianten, `TYPE_CLASS_PHONE`, Zahl mit
    `TYPE_NUMBER_FLAG_DECIMAL`, sonstiger Text nur für die IBAN; URI und alle Passworttypen nie),
    eigenes `isBlocked` (keine Einstellungen, `mIncognitoModeEnabled` inklusive No-Learning und
    Passwort, `mIsLocked`, `IME_FLAG_NO_PERSONALIZED_LEARNING` selbst geprüft, Passworttyp; P139),
    Schalter `AgentPreferences.FORMAT_GUARD` (an). Sprache aus `hintLocales` des Felds, sonst
    Tastatur; Land (`country`) aus `hintLocales`, sonst `mLocale` (Subtyp), sonst `systemLocale` (nur bei gleicher Sprache wie die Tastatur)
    (Land der Systemsprache aus `Resources.getSystem()`), sonst rät `PhoneFormat.region` nach der
    Sprache. Ein Chip mit `replacement` prüft beim Tipp erneut, dass hinter der Schreibmarke nichts
    steht (P138), und ersetzt über `Host.replace` mit einem `CommandCall`, dessen `text` der ganze
    geprüfte Feldtext ist (`replaceCommandText`: nur wenn er unverändert vor der Schreibmarke
    steht, also genau einmal); Hinweise (`MUTED`) tun beim Tipp nichts. IBAN-Punkt als
    `ForegroundColorSpan` (grün 34C759, rot FF453A). Schalter auf der Seite Tippen & Korrektur
    (Karte „Regeln“, eine Zeile). Texte in `agent_format_strings.xml` (`values/`, `values-de/`).
    Tests `AT/FormatGuardChipsTest.kt`, mit echtem Host
    `InputLogicTest.formatGuardChipChangesTheFieldOnlyOnATapAndOnce`.
  - Gedächtnis (46, P177): `A/MemoryChips.kt`, ohne Befehl nach den Feld- und Kontext-Vorschlägen
    und vor dem Emoji-Mix (Abschnitt „Lerndaten“ → Gedächtnis). `Host.predictions` und
    `Host.pickPrediction` (46) geben einer Chipreihe die üblichen Vorhersagen der Engine;
    `pickPrediction` prüft beim Tipp erneut, dass die Leiste noch Vorhersagen zeigt (Nachprüfung 46
    N3). `Host.insertAtCursor(text, privateText = true)`: privat über `onPrivateTextInput`, mit
    `false` über `onTextInput` wie ein Einfügen aus dem Verlauf (Gedächtnis-Chips, P177).
  - Feld- und Kontext-Vorschläge (45, P161–P164): `A/FieldSuggestionChips.kt`.
    `KeyboardCommands.buildChips` fragt ihn ohne Befehl nach dem Formatwächter und vor dem
    Emoji-Mix. `fieldClass` aus `inputType` (Passwort, URI, Filter, Datum: nie; Zahl mit Komma oder
    Vorzeichen = `OTHER`), `signals` (Hinweistext und `label`, `fieldName`; `EditorInfo` trägt auf
    keiner API-Stufe Autofill-Kennungen, P161), `isBlocked` = `FormatGuardChips.isBlocked` (keine
    Einstellungen, Inkognito, No-Learning-Flag, Passwort, `mIsLocked`) oder `deviceLocked` (Gerät
    oder Nutzer gesperrt, live; `stripChips` fragt es erst, wenn Feldziel oder Hinweis etwas
    anbieten könnten, also nicht bei jedem Tastendruck über Binder), Schalter
    `AgentPreferences.FIELD_SUGGESTIONS` (an). Liest
    `LOOKBACK` = 81 Zeichen vor der Schreibmarke. Feldmodus (`FieldTargets.target`): nur das ganze
    Feld (weniger als 81 Zeichen, nichts dahinter), Werte aus `myData` (`MyDataCache`) und
    `fresh` (`ClipVault.freshest`, nie ein Geheimnis), gefiltert mit `fits`, höchstens 3.
    Kontextmodus (nur `TEXT`/`SHORT_MESSAGE`): `ContextCues.find`, je Treffer eigene Einträge und
    der frische Clip der Art, `ContextScorer.score` mit `AppCategory`, Feldklasse und
    `ContextLearning.weights`, nur ab Schwelle, nicht schon im Text, höchstens 2. Chip-Text
    `label` (🔗 `shortLink` = Host plus „/…“, ✉️, 📞, 🏦 erste und letzte vier Zeichen, 🏠, 👤, 🔢),
    Beschreibung „… aus Meine Daten“ bzw. „gerade kopiert“. Tipp genau einmal (`used`): leeres Feld
    `Host.insertAtCursor` (privat; echter Host: `LatinIME.onPrivateTextInput` wie das Einfügen aus dem
    Tresor, nur bei `canReplaceAgentCommand` und unveränderter Schreibmarke, P162, P178),
    angefangener Feldtext `Host.replace` (Text → Wert), Kontext `Host.replace` der letzten bis zu 24
    Zeichen durch sich selbst plus Wert (nach „:“ mit Leerzeichen; `replaceCommandText` prüft, dass
    sie unverändert vor der Schreibmarke stehen). Beim Tipp prüft `insert` das Feld erneut
    (`isBlocked` mit den aktuellen Einstellungen und live gesperrt; im leeren Feld nichts vor oder
    hinter der Schreibmarke) und meldet einen ersetzten Wert über `Host.markPrivate`
    (`LatinIME.markAgentPrivateText`, P178). Ein Zahlenfeld ohne Code-Hinweis bekommt einen Code
    bewusst trotzdem (`weak`), aber nur bis 3 min alt: ein PIN- oder Betragsfeld ohne Hinweis kann so
    einen frischen Code angeboten bekommen, nie ohne Tipp. Lernen: `shown` hält die gezeigten Angebote
    (Hinweis-`id`, Art, App und Feld-ID, nur im Speicher); verschwindet eines ohne Tipp, zählt es
    einmal als übergangen, ein Tipp als angenommen (`ContextLearning.record`, P163). Texte in
    `agent_suggest_strings.xml` (`values/`, `values-de/`). Tests `AT/FieldSuggestionChipsTest.kt`,
    mit echtem Host `InputLogicTest.fieldSuggestionChipInsertsOnlyOnATapAndOnce`.
  - Smart Paste (40, P203–P205): `A/SmartPasteChips.kt`, kein Befehl, sondern Chips zum Clip der
    Zwischenablage-Pille (`stripRow`, Tag `TAG_STRIP`) und auf den Karten des Verlaufs
    (`bindHistoryRow`, Tag `TAG_HISTORY_ROW`, höchstens `MAX_HISTORY_CHIPS` = 2 mit gleicher
    Breite, Kurztexte `*_short`). Schalter `AgentPreferences.SMART_PASTE` (an). `isBlocked` =
    `FormatGuardChips.isBlocked` (Einstellungen fehlen, Inkognito samt Passwort und No-Learning,
    No-Learning-Flag, Passworttyp, `mIsLocked`), kein `TYPE_CLASS_TEXT` oder `deviceLocked` (live,
    austauschbar). `actions` = nichts für `ClipVault.isSecretClip` (Markierung, `SensitiveClips`,
    vom Tresor als Geheimnis sortierte Kopierzeit, Inhalt), sonst `SmartPaste.recognize` mit
    `ClipVault.region`, `DateCommand.clock`/`zone` und der Tastatursprache. `intent`: `geo:` über
    `PlaceCommand.geoIntent`, `ACTION_DIAL` mit `tel:` (keine `CALL_PHONE`), `Insert.ACTION` mit
    `RawContacts.CONTENT_TYPE` und `Insert.PHONE` (keine `WRITE_CONTACTS`), `ACTION_SENDTO` mit
    `mailto:`, `ACTION_VIEW` + `CATEGORY_BROWSABLE` mit `Tracking.url` aus der festen Tabelle,
    `EventCommand.intent` (`ACTION_INSERT` auf `Events.CONTENT_URI`, keine Kalender-Berechtigung);
    alle mit `FLAG_ACTIVITY_NEW_TASK`, gestartet über `launcher` (`SearchCommand.Launcher`,
    fehlende App → Toast). `<queries>` braucht es nicht: `startActivity` fragt keine Sichtbarkeit
    ab, und das Manifest hat ohnehin `action *`. `perform`: einfügende Aktionen über `insert(text,
    action.insertsPrivately)` (`insertOrSay`: abgelehnt → Toast `agent_smart_insert_refused`), „Als
    Ort“ über `SavedPlaces` (nur `available`; dieselbe Adresse → Toast mit ihrem Namen, sonst
    `SmartPaste.placeName` gegen die vorhandenen Namen; voll/gesperrt → Toast), `IbanInvalid` nur
    Toast. Leiste: `StripHost` (`imeHost`: Feld, für das die Pille gebaut wurde; `insert(text,
    privateText)` nur bei derselben Schreibmarke ohne Auswahl und `canReplaceAgentCommand`, dann
    Code und IBAN über `LatinIME.onPrivateTextInput` wie Feld-Vorschläge, P162/P178, ein
    bereinigter Link über `onTextInput` wie die Pille, P206); `stripChips` baut
    `KeyboardCommands.Chip`s (Hinweis `MUTED`), jeder wirkt einmal (`used`) und erst nach erneuter
    Prüfung von Schalter, Feld und Sperre; ein Tipp, der nichts tat (gesperrt, abgelehnt, keine
    App, voll), setzt `used` zurück (Nachprüfung 40, B8); `stripRow` nimmt `KeyboardCommands.chipRow`, setzt die
    Pille an Index 0, nimmt der Zeile die Kennung `agent_command_chips` und begrenzt den Pillentext
    auf 2/5 der Breite. Verlauf: `History` je Besuch (Urteil je Eintrags-ID gemerkt, gesperrt beim
    Öffnen: keine Chips; `marked` = `SensitiveClips.matcher` einmal je Besuch wie
    `ClipCommand.History`, also auch alte angeheftete Einträge aus dem Protokoll von 24f, Nachprüfung
    40, B4), ein Tipp prüft das Feld erneut, auch dort setzt ein Tipp ohne Wirkung `used` zurück.
    Texte `agent_smart_paste_strings.xml` (`values/`, `values-de/`). Test `AT/SmartPasteChipsTest.kt`.
  - Tests: `CT/UrlDetectorTest.kt`, `CT/SymbolTableTest.kt`, `CT/UnitConversionTest.kt`,
    `CT/EventParserTest.kt`, `CT/DateFormattingTest.kt`, `CT/PlacesTest.kt`, `CT/AlarmParserTest.kt`,
    `CT/TimerParserTest.kt`, `AT/commands/ClockCommandsTest.kt`;
    `AT/ContactCommandTest.kt`, `AT/MoreCommandsTest.kt`, `AT/SnipCommandTest.kt`,
    `AT/ClipCommandTest.kt`, `AT/DatePlaceCommandsTest.kt`, `UrlCommandTest` und
    `ScreenReaderServiceTest` (`AT/commands/`), `InputLogicTest.typed*`.

- Einheitliches Befehlssystem (Etappe 52, P199–P202): `@<bereich> [aktion] [angaben …]` für die
  Bereiche `@lang`, `@snip`, `@clip`, `@guard`, `@mem`, `@group` (zweite Namen `@sprache`,
  `@baustein`, `@ablage`, `@wächter`, `@gedächtnis`, `@gruppe` über `Command.aliases`).
  - `C/CommandGrammar.kt` (rein): `tokenize` (Leerraum; `;`, `→`, `->` auch angeklebt eigene
    Wörter), `parse(text, prefix, areas, vocabulary, claim)` → `Reading`s (höchstens
    `MAX_READINGS` = 3, nur die mit den wenigsten unbekannten Wörtern und den geringsten Kosten).
    Segmente beginnen mit einem Bereichswort (`areas`: Namen und normalisierte zweite Namen) oder
    einem beanspruchten Sprachwort (`claim`, `@deutsch` = Wert); Schritte teilen sich an
    `SEPARATORS` (`;`, `then`, `dann`, `→`, `->`), außer in `@snip` und `@clip` (`freeText`, seit
    Nachprüfung 52 auch die Suche von `@clip`: dort gehört alles bis zum nächsten Bereichswort zum
    Text, nur `;`/`→`/`->` direkt davor fallen weg; seit der zweiten Nachprüfung auch ein Trennwort,
    wenn davor genau ein genaues `@clip`-Aktionswort steht, `isClipAction`: `@clip on dann @lang …`).
    `endsWithSeparator` für die Ketten (P220). Ein Bereichswort ohne Wörter dahinter in einer Kette
    ist ein eigener Schritt mit `Step.bare` (vorher fiel es weg). Jeder Bereich kennt
    seine Aktionen (`ACTIONS`) und Plätze: `@lang`/`@guard`/`@mem` Ziele (App, Gruppe, `ALL_WORDS`
    = alle; leer = die App des Felds) und bei `@lang` eine Sprache, `@group` Name (erstes Wort ohne
    Aktion; eigene Gruppe auch mit Tippfehler oder neuer Name, `isValidGroupName`) und Apps,
    `@snip` Aktion als erstes Wort, dann Name und Text, `@clip` genau ein Aktionswort.
    `ACTION_WORDS` Englisch zuerst, dann Deutsch; Aktionen genau oder ab vier Buchstaben mit
    Tippfehler. Wörter: genau (0), angefangen ab drei Buchstaben oder ganzes Wort eines längeren
    Namens (1), Tippfehler des ganzen Namens (2) über `NameMatch` mit den Regeln von
    `LanguageNames` (`isTypo`, `allowedEdits`); Sprachen über `LanguageMatcher` (genau 0, sonst 1);
    Nachbarwörter, die zusammen genau ein App-Name sind, gelten als eins („google maps“);
    `APP_PREFIX` (`app:`) erzwingt eine App. Jede Bedeutung mit den geringsten Kosten bleibt
    (gleichstand = mehrere Deutungen), ein Wort ohne Platz kostet 3 (`Role.UNKNOWN`). `Step.legacy`
    = die alte Form des Bereichs (`@lang spanisch`, `@deutsch`, `@snip adr`, `@clip Termin`); bei
    `@lang` seit Nachprüfung 52 nur mit höchstens einem Wort oder ohne unbekanntes Wort
    (`@lang foo german` ist neu und rot). `Step.looksLikeCommand`/`Reading.looksLikeCommand`
    (P220): mindestens ein Wort mit bekannter Rolle (ein neuer Gruppenname allein zählt nicht) und
    das erste Wort bekannt oder höchstens ein unbekanntes; seit der zweiten Nachprüfung reicht ein
    genau getipptes Aktionswort oder ein genauer Sprachname (`Step.exactAt`, seit der dritten nur als
    erstes oder letztes Wort, mit den unbekannten Wörtern in einem Stück von höchstens
    `MAX_APP_WORDS` = 3), und ein genau getipptes beanspruchtes Sprachwort (`Step.claimed`,
    `@deutsch`, nicht `@spanis`) zählt wie `@lang deutsch`. Ein unbekanntes Wort aus `TEXT_WORDS`
    (häufige Satzwörter wie „ist“, „we“, „at“) lässt keine unbekannten Wörter zu (P231). `AppName.normalized`/`parts` halten den
    normalisierten Namen je App (einmal je Lesen der Apps, nicht je getipptem Wort).
    `Word`s geben jedem Wort eine `Role` (Bereich, Aktion, Ziel, Wert, Trenner, Unbekannt).
  - `A/KeyboardCommands.kt`: `Active.parse` verlängert einen Bereichsbefehl rückwärts über frühere
    Bereichsbefehle derselben Zeile (`MAX_CHAIN` = 8) zu einem `CommandCall` über die ganze Kette
    (Name des ersten Bereichs, Argument bis zur Schreibmarke); alle Leser parsen gleich. Seit
    Nachprüfung 52 nur über einen Trenner oder hinter einem vollständigen Befehl
    (`CommandSystem.continuesChain`, P220); `Active` hält dafür den App-Kontext.
    `Command.protects(context, call)` (Standard `protectsArgument`) entscheidet je getipptem Befehl,
    ob seine Wörter vor der Autokorrektur geschützt sind (`guardsTextBefore`; seit der dritten
    Nachprüfung: scheitert `Active.parse`, kein Befehl und nicht geschützt, scheitert `protects`,
    geschützt; `updateGuard` und `find` fangen Fehler von `parse` ebenso ab). `Host.replaceOutcome`
    gibt die Antwort des Editors (`ApplyOutcome`; Standard aus `replace`, der echte Host liefert
    `UNKNOWN` weiter; `UNKNOWN` heißt nicht sicher entfernt, P221). `forget` ruft `CommandSystem.dropReadings`. `aliases`
    und `help` (die eingeschalteten Bereiche) gehen an `CommandSyntax.parse`. `chipsFor` gibt für
    `@?` `CommandSystem.helpChips`; `buildChips` fragt ohne Befehl zuerst `CommandSystem.undoChips`.
    `chipBackground` ist die Chipfarbe (auch für `CommandColors`), `activeNames` die
    eingeschalteten Befehle. `load` setzt `active` jetzt vor den `Command.load`-Aufrufen.
  - `A/commands/CommandSystem.kt`: `readings` (Wortschatz aus `InstalledApps`, `CommandAppSettings.groups`,
    `SnipCommand.names`, `LanguageCommand.matchLanguages`), `chips` (null = alte Form, der Bereich
    zeigt seine alten Chips; ein Schritt, der nur zeigt oder auflistet, gibt Einträge als
    `TEMPLATE`-Chips, die „@bereich wort “ schreiben, bei `?` davor den Aufbau (`syntaxChip`);
    sonst je Deutung ein Vorschau-Chip in Klartext, farbig über `Words`; dahinter Vorschläge je
    Platz (`suggestions`, höchstens 4). `Plan`/`StepPlan`: Text, Wirkung (`Effect` → `Done` mit
    `undo` und `after`), `complete`, `harmless`, `problem` (Grenze, Kette), `showOnly`; `Plan.hint`
    sagt, warum Enter oder der graue Chip nicht ausführt. In einer Kette ist ein Schritt, der nur
    zeigt oder einfügt (alte `@snip`/`@clip`), unvollständig (`agent_unified_chain_toast`). Die
    Grenzen (`MAX_APPS`, `Memory.MAX_EXCLUDED`, `MAX_GROUPS`, `MAX_MEMBERS`,
    `SnippetStore.MAX_SNIPPETS`) prüft der Plan vorher (`agent_unified_limit_toast`). Ein Tipp
    (`execute`, einmal je Chip) prüft Sperren, führt seit Nachprüfung 52 erst jede Wirkung aus,
    entfernt dann den ganzen Befehl (`Host.replaceOutcome(call, "")`) und erst danach `after`
    (Sprache umschalten, Verlauf leeren; `Done.afterRan`); scheitert eine Wirkung oder wird das
    Entfernen abgelehnt (`REJECTED`), werden die schon gelaufenen zurückgenommen und der Text bleibt
    (`agent_unified_failed_nothing` nennt den Schritt; `agent_unified_failed` nur noch für ein
    gescheitertes Rückgängig). `UNKNOWN` gilt als entfernt (P221). `Undo` (Text, App, Schreibmarke nach dem Entfernen,
    `UNDO_MILLIS` = 8 s) zeigt „↶ Rückgängig“ nur an genau dieser Schreibmarke in derselben App;
    `schedule` baut die Leiste nach Ablauf neu (`Undo.refresh` = `Host.refresh` der zuletzt
    gebauten Leiste). `onEnter`: genau eine Deutung, ausführbar und harmlos → wie ein Tipp (bei
    `canReplaceNow`); jede andere Deutung im neuen Aufbau nimmt Enter mit `Plan.hint`, der Text
    bleibt (P200); alte Form und Wörter, die nicht wie ein Befehl aussehen → null (P220).
    `protects`: geschützt, solange die Wörter wie ein Befehl aussehen oder nur ein Wort hinter dem
    Bereichswort steht (`protects` und `continuesChain` in `runCatching`: geschützt bzw. keine Kette).
    `readings` merkt sich die letzten vier Texte (`readingsCache`, P230; `dropReadings`). In einer
    Kette ist ein `Step.bare` unvollständig (`agent_unified_incomplete_toast`). Harmlos ist nicht:
    Löschen, `all`, Gedächtnis an, Zwischenablage-Schalter (räumt wie der Einstellungsschalter
    ungepinnte Einträge, `clearNonPinned`), einen Baustein überschreiben, eine Gruppe mit weniger
    Apps überschreiben (harmlos nur neu oder mit allen bisherigen Apps). Wirkungen: Sprache je App
    (setzt und entfernt HeliBoards Eintrag der App; die App des Felds schaltet sofort um; `all` =
    alle eigenen Einträge weg, Eintrag `ALL`), Formatwächter je App (`all` = Schalter
    `FORMAT_GUARD` und keine Einträge), Gedächtnis (`Memory.setExcluded`; `all on` = Schalter an und
    keine Ausnahmen, `all off` = Schalter aus), Gruppen, Bausteine (`snippetStore`, austauschbar),
    Zwischenablage-Verlauf, Sprache des Felds (alte Form in einer Kette, `Host.switchSubtype`).
    `CommandColors`: je Rolle eine Farbe, zwei Paletten nach `isDark(chipBackground())`.
    Austauschbar `clock`, `schedule`, `snippetStore`.
  - `A/commands/CommandAppSettings.kt`: eigene DE-Datei `agent_command_settings` (kein Neuladen
    der Tastatur beim Schreiben), Schlüssel `language:<paket|*>`, `guard:<paket>`,
    `group:<name>` (`docs/STORAGE.md`); `subtypeForApp` (P199), `onSwitchedInField` (P219, aus
    `LatinIME.onCurrentInputMethodSubtypeChanged`; ganz in `runCatching`, nicht für die Gründe
    `SubtypeSwitchLog.FIELD_START`), `setGroup` ruft `CommandSystem.dropReadings`, `resolve` (wie HeliBoard:
    gleicher Subtyp, sonst einer der Sprache), `guardAllows` (Eintrag der App vor dem Schalter,
    aus `FormatGuardChips.stripChips`; `guard` liest seit Nachprüfung 52 nur den einen Schlüssel),
    Grenzen `MAX_APPS` 100, `MAX_GROUPS` 20, `MAX_MEMBERS` 30.
  - `A/commands/InstalledApps.kt`: Paketname und Name aller Apps mit Launcher-Eintrag, auf einem
    eigenen Thread höchstens alle 10 min gelesen (`refresh` aus `Command.load` von `@lang`,
    `@guard`, `@mem`, `@group`), nur im Speicher; bis dahin sind App-Wörter unbekannt (P201).
    Austauschbar `source` (Tests lesen sofort).
  - `A/commands/AreaCommands.kt`: `AreaCommand` und `GuardCommand`, `MemoryCommand`,
    `GroupCommand` (neu in `BUILT_IN`, hinter `@mix`); `LanguageCommand`, `SnipCommand` und
    `ClipCommand` fragen zuerst `CommandSystem.chips`/`onEnter` und behalten ihre alten Chips
    (`SnipCommand.listChips`, `ClipCommand.listChips` auch für `list`). `LanguageCommand`:
    `protects` über `CommandSystem.protects` (auch `AreaCommand`; `@snip`/`@clip` schützen weiter
    immer), `subtypeFor`, `matchLanguages`, Enter für ein Sprachwort mit genau einem Chip (wie
    dessen Tipp), seit Nachprüfung 52 nur für einen genauen Namen der Sprache (`exactOnly`); ist sie
    schon aktiv oder hat sie mehrere Layouts, nimmt Enter die Taste und sagt es (P200); die zweiten
    Namen sind für den Sprach-Matcher reserviert.
  - Farben im Textfeld selbst (Komposition mit Farb-Spans über Leerzeichen hinweg) sind nicht
    gebaut (P202, ROADMAP 52).
  - Texte in `agent_command_system_strings.xml` (`values/`, `values-de/`). Tests
    `CT/CommandGrammarTest.kt`, `CommandSyntaxTest` (zweite Namen, `@?`),
    `AT/commands/CommandSystemTest.kt`, mit echtem Host `InputLogicTest.unifiedCommand*`
    (Farben der Vorschau, ein Tipp genau einmal, Rückgängig, Enter-Regeln auch mit Hardware-Enter,
    Passwortfeld, Sprache pro App beim Feldstart mit ausgeschaltetem Schalter; Nachprüfung 52:
    Text hinter einem Bereichswort, Bausteinfelder hinter `@gruppe …`, Sprachtaste im Feld und
    Neustart des Felds), `SettingsBarAreaTest` (23 Befehle).

## Act Builder

> Natürliche Beschreibung → freigegebene Anfrage → streng validierter Workflow-Entwurf.

- `WorkflowBuilderContract.kt`: festes JSON-Schema für Responses `text.format` (strict,
  geschlossene Objekte). Das Modell darf nur `name` und `steps` vorschlagen.
- `WorkflowDraftRunner.kt`: `WorkflowDraftApproval` (identitätsgebundene, einmalige Freigabe)
  und der Runner (Budget 65 s, nur OpenAI mit Structured-Output-Fähigkeit). Kein Editor-,
  Speicher- oder Startzugriff.
- `K/settings/screens/WorkflowBuilderDialog.kt`: Prompt (höchstens 4096 Zeichen) → genaue
  Sendevorschau → separater Sendeklick → Entwurf.
- `K/settings/screens/WorkflowDraftProtection.kt`: `ProtectWorkflowDraft` hält `FLAG_SECURE`
  vom Builder bis zum Speichern oder Verwerfen. Pause, Stop und Inkognito verwerfen Prompt,
  Freigabe und Entwurf.
- Tests: `WorkflowBuilderContractTest`, `WorkflowDraftRunnerTest`,
  `WorkflowBuilderSettingsTest`.

## Einstellungen und Profile

> Compose-Seiten der Agent-Funktionen sowie Design-, Layout- und Gestenprofile.

- `K/settings/screens/AgentAiScreen.kt`: API-Schlüssel (maskiert, nie zurückgeladen),
  Modellkennung, Sprache, Ton, Laufstatus. Gesperrt ohne Entsperren.
- `K/settings/screens/AgentRunStatus.kt`: lesbare und löschbare Laufmetadaten.
- `K/settings/screens/AgentWorkflowsScreen.kt`: Workflows anlegen, bearbeiten, importieren,
  exportieren, wiederherstellen, Builder öffnen.
- `K/settings/screens/AgentSkillsScreen.kt`: Skill-Slots belegen, Import/Export.
- `K/settings/screens/AgentProfilesScreen.kt` mit `KeyboardProfileStore.kt`: getrennte Design-,
  Layout- und Gestenprofile. Profile enthalten nur erlaubte Einstellungen, nie Berechtigungen,
  Geheimnisse, Lernverläufe oder Code. Ungültige Importe werden vor jeder Änderung verworfen.
  Das Gestenprofil enthält die sechs Leertasten-Gesten; `INHERIT` heißt „kein eigener Wert“
  (beim Anwenden wird der Schlüssel entfernt), ältere Profile ohne diese Schlüssel bekommen beim
  Lesen überall `INHERIT`.
- Texte: `agent_strings.xml` (Englisch in `values/`, Deutsch in `values-de/`), IDs und Attribute:
  `agent_ids.xml`, `agent_attrs.xml`.
- Tests: `KeyboardProfileStoreTest`, `SkillSettingsTest`, `WorkflowSettingsTest`.

- Neue Einstellungen (38a, Grundlage für 38b–38h; Vorlage `docs/referenzen/einstellungen-mockup/`,
  1 CSS-px = 1 dp/sp): `K/settings/screens/AgentSettingsKit.kt` ist der Baukasten:
  `AgentSettingsTokens` (Farben, Radien Karte 28, Held 30, Kachel 26, Auswahlkarte 22, Zeile 18 dp,
  Mindestgröße 44 dp, Schalter 52 × 30 mit Knopf 22), `AgentSettingsType` (Schriftstile),
  `SettingsArea` (sieben Bereiche: Route, Nummer, Texte, Verlauf, Punktfarbe, breite Kachel),
  `cssGradient` (CSS-Winkel und -Länge des Verlaufs), `AgentSettingsTheme` (dunkles Schema, auch
  für die Upstream-Suchtreffer), `AgentLineIcon`/`AgentRoundButton` (Lupe, Zurück, Pfeil als
  Canvas-Linien), `BackHeader`, `AreaHero`, `SectionCard` (Zeilenkarte, 8 dp innen),
  `FeatureCard` (freie Karte, 18 dp innen, Titel optional gesperrt in Großbuchstaben),
  `SettingRow` (Punkt, Titel, Untertitel, Wert, Pfeil; ganze Zeile ein Knopf, „… öffnen“),
  `SettingSwitchRow` und `GradientSwitch` (Role.Switch, `toggleable`), `ChoiceCards` (große
  Verlaufskarten mit Leucht-Pille; Radio-Buttons in einer `selectableGroup`). Ihre Fläche ist
  `Modifier.choiceSurface(selected, from, to, outline, base, enabled, glow)` (Nachprüfung 37, auch
  für Bildwiederholrate, Profil- und Sprachkarten): Verlauf 120°, gewählt voll mit Rahmen 3 dp
  in 2 dp Abstand, sonst nur der Verlauf (und die Pille) mit `choiceDim` über `base` (die Karte
  dahinter), ohne Alpha-Ebene, die Schrift bleibt deckend. `choiceDim` = `CHOICE_DIM_ALPHA` 0,7,
  in Schritten von 0,01 erhöht, bis `OnGradient` auf jedem Punkt des Verlaufs
  `CHOICE_MIN_CONTRAST` 4,5:1 plus 0,1 Reserve für die 8-Bit-Rundung hat (`contrastRatio`, WCAG,
  sRGB-Mischung wie der Shader); reicht auch 1 nicht, bleibt 1 (bei heller Schrift 0,7). Die
  Schriftfarbe wählt `choiceText(from, to)`: `OnGradient`, wo sie auf dem ganzen Verlauf voll
  4,5:1 hält, sonst hell `Text` (#F2F2F5). So bekommen die grauen Karten AUS, NICHTS, AUSGEBLENDET
  und FLACHER LOOK (#3A3A42→#6B6B76; der flache Look endet dafür dunkler als im Mockup, #9C9CA6
  hielte mit heller Schrift nur 2,4:1) helle Schrift, gewählt 4,71:1, gedimmt 6,92:1 (Owner,
  2026-11-14). `ChoiceCard`, Profil- und Sprachkarten nehmen `choiceText`. Nicht wählbar (`enabled = false`): die ganze Karte 35 % (`CHOICE_DISABLED_ALPHA`, inaktiv).
  `ChipToggles` (hell wie auf der Startseite oder
  im Bereichsverlauf wie die @-Befehle), `AreaTile`, `FooterPill`. Schriften: Systemschrift in
  denselben Stärken statt Space Grotesk/Inter (beide OFL, aber keine Schriftdateien im APK;
  Entscheidung des Leads, 38a). `K/settings/screens/AgentStartScreen.kt`: Startseite (Titel,
  Lupe öffnet die bisherige Suche über `settingsContainer.filter`, Look-Karte mit
  `lookName` = eigenes Design, „Flacher Look“ oder Tastenstil und Lichtmuster der Tasten,
  „Ändern“ → `area_look`; Schnellschalter auf `always_incognito_mode`, `RgbSettings.enabled`
  über `RgbLighting.write`, `vibrate_on`, `agent_symbol_drawing`; Kacheln 01 breit, 02/03, 04/05,
  06 und 07 breit; Pillen Diagnose, „Updates · Build <VERSION_CODE>“, Über).
  `K/settings/screens/AgentAreaScreen.kt`: leitet jeden `SettingsArea` an seine Seite weiter (seit 69 ohne die
  alte Übergangsseite mit Karte „Seiten“, P286) und
  `SettingsAreas.entries(area)`, die vorläufige Zuordnung der bisherigen Seiten nach
  `Struktur.dc.html` (Aussehen: Design, RGB, Aussehen; Tippen: Textkorrektur, Gelernte Wörter,
  Wörterbücher; Tasten: Sprachen, Optionen, Sekundäre Layouts; Gesten: Leertaste, Symbole malen,
  Gestenschreiben (nur mit Bibliothek), Optionen als „Vibration & Ton“ (seit 38e nur noch Symbole
  malen und Gestenschreiben, siehe unten); Leiste: Leiste
  (früher „Symbolleiste“), Befehle, Screenshots, seit 38f Zeilen der neuen Seite; KI: KI-Anbieter,
  Skill-Tasten, Workflows, Diktat; System:
  Profile, Updates, Diagnose, Erweitert, Gestendaten (nur in der Sammelphase), Über). 38b–38h
  ersetzen die Karte „Seiten“ je Bereich durch den Aufbau des Mockups. `patternLabel` in
  `AgentRgbScreen.kt` ist dafür `internal`. Texte: `agent_settings_strings.xml`. Tests
  `AT/SettingsStartScreenTest.kt` und `AT/SettingsKitTest.kt` (Bilder unter
  `app/build/agent-previews/settings-*.png`).
  Etappe 69: `AgentSettingsKit.kt`: `MinTouch` ist 48 dp; `SettingRow` und `SettingSwitchRow` haben
  `enabled` (grau mit `DISABLED_ROW_ALPHA`, bei den Schaltern mit `disabledReason` als Zusammenfassung,
  P285); die Fußzeile der Startseite ist ein `FlowRow` (bricht bei großer Schrift um).
  `K/settings/screens/AgentSearchEntries.kt` (neu): `createAgentPageSettings` für die Suche (P284);
  Suchwörter in `agent_search_strings.xml`. Test `AT/SettingsSearchTest.kt`.
- Bereich Tippen & Korrektur (38c, P126–P127): `K/settings/screens/AgentAreaTypingScreen.kt`,
  aufgerufen von `AgentAreaScreen` für `SettingsArea.TYPING` (eine Zeile). `TypingAutocorrect`
  ordnet die drei Auswahlkarten vorhandenen Einstellungen zu (Annahme des Leads): AUS =
  `auto_correction` aus (Rest bleibt); BEHUTSAM = `auto_correction` an,
  `agent_autocorrect_keep_valid_words` an, `agent_autocorrect_keep_umlaut_variants` an,
  `auto_correct_confidence` ≤ 0,40 (HeliBoards Stufe „Mäßig“; außerhalb wird 0,24 geschrieben);
  MUTIG = `auto_correction` an, `keep_valid_words` aus, Stärke > 0,80 („Sehr stark“; außerhalb
  wird 0,9 geschrieben), die Umlaut-Regel bleibt. `current` liest die Stufe zurück, jede andere
  Kombination (auch HeliBoards Standard) ist `null` = keine Karte und „Eigene Einstellung“.
  Schalter schreiben die echten Schlüssel (Regeln, Vorschläge, Leerzeichen, Inkognito;
  „Nächstes Wort“ und „Satzzeichen“ mit `setThemeNeedsReload` wie die alte Seite; Emoji wie
  `SwitchPreferenceWithEmojiDictWarning`; Pause über `LearningData.setPaused`) und sind ausgegraut
  wie die alte Seite sie ausblendet (Korrektur-Regeln ohne Autokorrektur, Wortvorschläge ohne
  Vorschlagsleiste, Emoji ohne beides, Doppeltipp-Punkt bei belegter Leertasten-Geste
  `SpaceGestures.doubleTapAssigned`). „Personalisierte Vorschläge“ (Löschwarnung), Kontakte (Berechtigung)
  und der Rest bleiben auf `TextCorrectionScreen` („Alle Korrektur-Einstellungen“, „Kontakte und
  App-Namen“). `WordCounts` liest Wörterbücher der eingeschalteten Sprachen
  (`getDictionaryLocales`) und die Wortzahl von `UserDictionary` einmal über `Dispatchers.IO`.
  Zeilen zu Seiten tragen `AREA_ROW_TAG` + Route; die Routen von `SettingsAreas.entries(TYPING)`
  bleiben als Liste für P119. Texte `agent_typing_strings.xml`. Test
  `AT/SettingsTypingAreaTest.kt` (Bild `app/build/agent-previews/settings-area_typing.png`, ganze
  Seite; `SettingsStartScreenTest` schreibt für diesen Bereich `settings-area_typing-top.png`).
- Gesten & Gefühl (38e, Vorlage `Gesten.dc.html`): `K/settings/screens/AgentAreaGesturesScreen.kt`,
  von `AgentAreaScreen` für `SettingsArea.GESTURES` aufgerufen (eine Zeile). Karte „Leertaste“ mit
  `SpaceDetentBar` (Leiste 64 dp, Punkte bei 22/42/72 %, Leuchten als radialer Verlauf) und den
  sechs `SpaceGesture` (Wert `SpaceGestures.resolve`, kurz gefasst über `shortLabel`, sonst
  `labelResource` aus `AgentSpaceScreen.kt`, dafür wie `titleResource` `internal`); Tipp →
  `ListPickerDialog` mit `SpaceGestures.choices`, „Standard“ = `write(…, null)`.
  `swipeChoice`/`writeSwipeChoice`: 0 = beide Schalter aus, 1 = `agent_symbol_drawing` an
  (`gesture_input` bleibt, Symbole haben Vorrang), 2 = `gesture_input` an und Symbole aus, nur mit
  `JniUtils.sHaveGestureLib` (`Choice.enabled` im Baukasten). Regler über `GestureSlider` (Schlüssel,
  Standard, Bereich, Schritte und Live-Rückmeldung wie die alten `SliderPreference`) und
  `SliderDialog`; „Trefferzonen kalibrieren“ öffnet `CalibrationDialog` aus `AgentLearningScreen.kt`
  (`internal`). `SettingsAreas.entries(GESTURES)` nennt nur noch die Seiten, die die Seite öffnet
  (Symbole malen, Gestenschreiben); `agent_space` ist Unterseite (Erweitert, Suche). Texte
  `agent_gestures_strings.xml`. Test `AT/SettingsAreaGesturesTest.kt` (Bild
  `app/build/agent-previews/settings-area_gestures-full.png`, ganze Seite).
  Bewusste Abweichung von den alten Seiten (Mockup 1:1): „Spur anzeigen“ und
  „Touchpad-Empfindlichkeit“ stehen immer da, auch ohne Symbole malen/Gestenschreiben bzw. ohne
  Wischgeste „Touchpad“; der Wert wirkt erst, wenn die Funktion an ist.
- Bereich Tasten & Sprachen (38d, Vorlage `Tasten.dc.html`, P128–P129):
  `K/settings/screens/AgentAreaKeysScreen.kt`; `AgentAreaScreen` leitet `SettingsArea.KEYS` mit
  einer Zeile dorthin. `languageCards` baut je eingeschaltetem Subtyp (`getEnabledSubtypes(true)`)
  Name (in der eigenen Sprache über `runInLocale(context, …)`, P128), Layout
  (`getLayoutDisplayNameInSystemLocale`), Wörterbuchstand (Haupt über
  `getUserAndInternalDictionaries`, Emoji nur lesend aus Cache-Ordner und Assets, nichts wird
  entpackt) und „aktiv“ (`getSelectedSubtype`); die Karte ist ein Knopf (Role.Button,
  stateDescription „aktiv“) im Look von `ChoiceCards`, Route `subtypeRoute`. Schalter schreiben
  dieselben Schlüssel mit denselben Nebenwirkungen wie `createPreferencesSettings` bzw.
  `createAdvancedSettings` (`reloadKeyboard`, `setThemeNeedsReload`,
  `KeyboardLayoutSet.onSystemLocaleChanged`); `KeysDialogs` öffnet dieselben Dialoge
  (`ListPickerDialog`, `ReorderDialog`, `SliderDialog`, `TextInputDialog`, `InfoDialog`). Wer dort
  eine Nebenwirkung ändert, ändert sie hier mit. `SettingsAreas.entries(KEYS)` bleibt die Liste der
  Seiten, die diese Seite öffnet (Zeilen mit `AREA_ROW_TAG + route`, P119). Texte:
  `agent_settings_keys_strings.xml`. Test `AT/SettingsKeysScreenTest.kt` (Bild
  `app/build/agent-previews/settings-area_keys-full.png`).
- `K/settings/screens/SecureWindow.kt`: FLAG_SECURE mit Zähler je Fenster für geschützte Seiten
  (KI & Automationen, KI-Anbieter, Workflow-Entwurf), P144. Test `AT/SecureWindowTest.kt`.
- Bereich Leiste & Befehle (38f, P132–P133): `K/settings/screens/AgentAreaBarScreen.kt`,
  aufgerufen von `AgentAreaScreen` für `SettingsArea.BAR` (eine Zeile). `BarModes` ordnet die
  drei Karten HeliBoards `toolbar_mode` zu: TASTEN + VORSCHLÄGE = `EXPANDABLE`, NUR VORSCHLÄGE =
  `SUGGESTION_STRIP`, VERSTECKT = `HIDDEN`; `TOOLBAR_KEYS` („Nur Leisten-Tasten“) ist keine Karte
  und „Eigene Einstellung“. Schreiben wie die alte Liste (mit `setThemeNeedsReload`).
  `pinnedSkills` zählt `SKILL_1..3` in `getPinnedToolbarKeys`. Karte „@-Befehle“: Kopf mit Titel,
  „Präfix <Zeichen>“ und dem Hauptschalter (`KeyboardCommands.setEnabled`, Beschreibung „Getippte
  Befehle erkennen“); je Befehl aus `KeyboardCommands.registry` ein Chip auf
  `isCommandEnabled`/`setCommandEnabled` (eigene Chips statt `ChipToggles`, weil sie bei
  ausgeschaltetem Hauptschalter gesperrt sein müssen); fehlt einem eingeschalteten Befehl die
  Berechtigung (`@contact`), fragt der Chip beim Einschalten und eine Zeile darunter auf Tipp.
  Abschnitt Leiste: „Knöpfe wählen und sortieren“ öffnet die alte Leisten-Seite (`ToolbarScreen`
  mit ihren Dialogen), Schalter `auto_show_toolbar` (nur bei `EXPANDABLE`) und
  `toolbar_swipe_down_to_hide` (nicht bei `HIDDEN`), „Skill-Tasten anheften“ öffnet einen Dialog
  mit drei Schaltern (`addPinnedKey`/`removePinnedKey` wie die Skill-Seite). Abschnitt
  Zwischenablage & Bilder: Verlauf (mit `ClipboardDao.clearNonPinned` wie der alte Schalter),
  Aufbewahrung über HeliBoards `SliderDialog` (1–121, über 120 „Keine Begrenzung“,
  `clearOldClips`), Bilder und Dateien (`cleanupFiles`), beide nur mit Verlauf; Screenshots wie
  `AgentScreenshotScreen` (Aufräumen beim Ausschalten, Berechtigung beim Einschalten);
  Emoji-Mix über `EmojiMixCommand.setStripEnabled`; Smart Paste (40) über `AgentPreferences.SMART_PASTE`,
  Untertitel `agent_smart_paste_setting_no_pill`, solange die Pille (`PREF_SUGGEST_CLIPBOARD_CONTENT`)
  aus ist. Karte am Ende: „Alle Befehls-Einstellungen“
  (`AgentCommandsScreen`: Präfix, Textbausteine, Orte, Bildschirm lesen, Emoji-Mix-Speicher),
  „Screenshots“ (Berechtigung erlaubt/fehlt), „Weitere Zwischenablage-Einstellungen“
  (Optionen: angeheftete zuerst, Dateigröße) und „Meine Daten & Vorschläge“ (45, `agent_my_data`). Berechtigungen liest `rememberPermissions` beim
  Öffnen und bei `ON_RESUME`. Texte `agent_bar_strings.xml`. Test `AT/SettingsBarAreaTest.kt`
  (Bild `app/build/agent-previews/settings-area_bar.png`, ganze Seite; `SettingsStartScreenTest`
  schreibt für diesen Bereich `settings-area_bar-top.png`).
- KI & Automationen (38g, Vorlage `KI.dc.html`, P134–P135): `K/settings/screens/AgentAreaAiScreen.kt`,
  von `AgentAreaScreen` für `SettingsArea.AI` aufgerufen (eine Zeile). Wie `AgentAiScreen`:
  `FLAG_SECURE` solange die Seite steht, `credentialAccessAllowed` bei `ON_RESUME`, `ON_STOP` sperrt;
  gesperrt wird weder `ModelSettings` noch ein Store gebaut (Direct Boot), es gibt nur den Hinweis
  `agent_ai_unlock` und die vier Zeilen aus `SettingsAreas.entries(AI)`. Entsperrt: `ChoiceCards`
  „Anbieter“ über `CloudProviders.SUPPORTED` ↔ `ModelSettings.provider`/`setProvider` (Hinweis =
  `ModelSettings.model`, leer → „Modell-ID selbst eintragen“; Farben der Karten aus dem Mockup);
  Schlüsselstatus je Anbieter nur über `EncryptedCredentialStore.contains(CloudProviders.slot(…))`
  auf `Dispatchers.IO` (nie `read`), „Ändern“ → `agent_ai`. Kacheln der Skill-Tasten aus
  `SkillBindingStore.read` (Name = `label`, leer → `BuiltinAction.titleResource`), Anzahl aus
  `WorkflowStore.read`, beides einmal je Besuch auf IO. Zielsprache und Ton mit `DropdownMenu` wie
  auf der alten Seite (`setTranslationLanguage`, `setWritingTone`). Diktat: `OnDeviceSpeechEngine.available`
  (in Tests `dictationAvailable`) und `RECORD_AUDIO`, neu bei `ON_RESUME`; Auftragsstatus →
  `agent_ai` (dort `AgentRunStatus`), Zahlen aus `EncryptedActionRunStore.MAX_RECORDS`/`MAX_AGE_MS`,
  Workflow-Grenzen aus `WorkflowDefinition.MAX_STEPS`/`MAX_MODEL_CALLS`. Zeilen-Tags für P119:
  „Ändern“, Kachel 1, „Meine Workflows“ und Diktat tragen `AREA_ROW_TAG` + Route. Texte
  `agent_ai_area_strings.xml`. Test `AT/SettingsAreaAiTest.kt` (Bild
  `app/build/agent-previews/settings-area_ai-full.png`, ganze Seite).
- Bereich System & Daten (38h, Vorlage `System.dc.html`, P136–P137, P145):
  `K/settings/screens/AgentAreaSystemScreen.kt`; `AgentAreaScreen` leitet `SettingsArea.SYSTEM` mit
  einer Zeile dorthin. Die Seite liest `credentialAccessAllowed` (neu bei `ON_RESUME`, dazu der
  Zähler `resumed`): gesperrt zeigt die Karte „Updates“ nur `agent_updates_unlock` und „Diagnose“
  wie die alte Seite (P145); Profilkarten und Gesten-Bibliothek bleiben, sie liegen im
  geräteverschlüsselten Speicher. Entsperrt: `updateCardText` aus `UpdateSettings.status`, `checkedAt`, `daily` und
  `Updates.installedVersion` („verfügbar“ nur für einen neueren Build, „ist aktuell“ nur, wenn die
  Prüfung auf diesem Build lief, Fehlertext über `updateErrorText` aus `AgentUpdatesScreen.kt`;
  Download- und Installationsfehler als „Installation fehlgeschlagen“, Prüfzeit „gerade eben“,
  „vor …“ bis eine Woche, danach „am <Datum>“). „Jetzt prüfen“ ruft `Updates.check` auf
  `Dispatchers.IO` mit dem Client aus dem Parameter `updateClient` (Tests: Attrappe), erst bei
  diesem Tipp; Überschrift und
  „Build N installieren“ öffnen `AgentUpdates` (Schalter, Download). Karte „PROFILE“
  (`profileCards`): je `KeyboardProfileStore.hasSaved` eine Karte, dann Flacher Look, dann
  „+ Neues Profil“ (→ `AgentProfiles`); Knöpfe mit Rückfrage, die `restoreSaved` bzw.
  `FlatPreset.apply` aufrufen; hervorgehoben (100 %, Rahmen, stateDescription), solange
  `KeyboardProfileStore.matchesCurrent` (wirft nie) bzw. `FlatPreset.isApplied` gilt; die Karten
  entstehen in `remember(Zählersumme, resumed)`, nicht bei jeder Komposition. Sichern:
  `BackupRestorePreference`, Für Fortgeschrittene: Emoji-Version (`SliderDialog` mit Bereich,
  Standard und Nebenwirkungen der alten `SliderPreference`), URL-Erkennung, Hardware-Tastatur,
  `LoadGestureLibPreference` (nicht in `nouserlib`), Debug (wie Erweitert nur `DEBUG` oder
  freigeschaltet), Gestendaten (Bedingung aus `SettingsAreas`), „Alle erweiterten Einstellungen“;
  Über (`AboutRows`): Version mit fünf Tipps wie `AboutScreen`, Lizenz, Versteckte Funktionen
  (derselbe `AlertDialog`), Protokoll speichern (derselbe Export), „Alle Infos und Links“ → About.
  Wer dort eine Nebenwirkung ändert, ändert sie hier mit. `SettingsAreas.entries(SYSTEM)` bleibt
  die Liste der Seiten, die diese Seite öffnet (Tags `AREA_ROW_TAG + route`, P119). Texte
  `agent_settings_system_strings.xml`. Test `AT/SettingsSystemAreaTest.kt` (Bild
  `app/build/agent-previews/settings-area_system-full.png`).
- Bereichsseite Aussehen (38b, `Aussehen.dc.html`): `K/settings/screens/AgentAreaLookScreen.kt`,
  von `AgentAreaScreen` mit einer Zeile für `SettingsArea.LOOK` aufgerufen. Karte „Designs“: eigene
  Designs zuerst, dann die Kacheln aus `DesignTiles.sections`; zwei Reihen (sechs Kacheln, die
  gewählte immer dabei), Antippen wählt (`MyDesigns.select` bzw. `DesignTiles.select`), langes
  Drücken einer eigenen Kachel, „+ Neues Design“ und „Bearbeiten“ (nur bei gewähltem eigenem
  Design) öffnen `AgentDesignEditor` an Stelle der Seite wie auf der Design-Seite; „Alle <n>
  Designs“ → `agent_design`. RGB-Karte (Streifen 115° über Verlauf 110°, Zusammenfassung Muster
  der Tasten · Bilder/s als Hz · leuchtende Elemente über `targetLabel`, das dafür `internal` ist;
  `GradientSwitch` schreibt nur `enabled` über `RgbLighting.write`), Antippen → `agent_rgb`.
  Abschnitte: Tastaturhöhe und Tastenabstand (nur mit Tastenrändern, wie die alte Seite) über
  `KeyboardScalePreference` mit `row`, Ränder,
  Schriftgröße, Emoji und Hintergrundbild als Dialog mit den Upstream-Zeilen aus
  `createAppearanceSettings` (je Zeile ihr eigener Dialog), Geteilte Tastatur mit denselben
  Schaltern wie der Upstream-Dialog plus Teilungsabstand (die Gruppen-Dialoge bekommen die
  Zählersumme, ihre Bedingungen gelten auch bei offenem Dialog), Eigene Schrift über
  `CustomFontPreference` mit `row` und `onChanged` (P146), Tag/Nacht und Navigationsleiste als `SettingSwitchRow`,
  zuletzt „Alle Aussehen-Einstellungen“ → `appearance` (Stil, Symbolstil, Tastenränder, Farben,
  Symbole anpassen, Leertastentext, Höhe der unteren Reihe). `SettingsAreas.entries(LOOK)` nennt
  genau die drei Ziele der Seite in Seitenreihenfolge (P119), die Knöpfe tragen
  `AREA_ROW_TAG + Route`. Texte `agent_settings_look_strings.xml`. Test
  `AT/SettingsLookScreenTest.kt` (Bild `app/build/agent-previews/settings-area_look.png`).
- Design-Seite (23f1): `A/DesignTiles.kt` (Abschnitte, Kacheln, Auswahl; schreibt genau die
  Schlüssel von Tag/Nacht-Schalter und Farbwähler, „Flacher Look“ über `FlatPreset.apply`) und
  `K/settings/screens/AgentDesignScreen.kt` (Raster, Rahmen und Häkchen, TalkBack als
  Radio-Buttons; Kachelfarben neu bei geändertem Stil oder Tastenrahmen, `DesignTiles.look`); erreichbar über Aussehen → „Design“ und die Menü-Pille „Design“
  (`agent_design`). Test `AT/DesignTilesTest.kt` (Bild unter `app/build/reports/design-tiles/`).
- Eigene Designs (23f2): `A/MyDesigns.kt` (Modell, JSON-Liste `agent_designs`, Speichern,
  Auswählen, Löschen; ein Design ist ein HeliBoard-Farbsatz `user_colors_agent_design_<id>` plus
  Werte für vorhandene Einstellungen, `selectionValues()` nennt genau die Schlüssel, die die
  Auswahl schreibt oder entfernt; eigenes Bild in `agent_designs/` neben HeliBoards
  Hintergrundbild, siehe `docs/STORAGE.md`), `A/DesignLook.kt` (Eckenrundung und Schrift aus
  `agent_key_corner_radius`/`agent_key_font`, gelesen je `KeyboardView`; Pillen ab 100 dp Radius
  bleiben Pillen; vor Android 7 keine Rundung, weil der Radius dort nicht lesbar ist) und
  `K/settings/screens/AgentDesignEditor.kt` (Editor mit fester Live-Vorschau oben, die
  `DesignPreview` mit Compose aus demselben `Colors`-Objekt zeichnet, das die Tastatur nutzt;
  Farbwahl über HeliBoards `ColorPickerDialog`; Bild über die System-Dateiauswahl, bis zum
  Speichern in `pending.img`). `AgentDesignScreen.kt` zeigt oben „Meine Designs“ mit Kachel „+“
  (Antippen wählt, langes Drücken oder Stift öffnet den Editor, Löschen im Editor mit Rückfrage);
  der Editor ersetzt die Seite ohne eigenes Navigationsziel. `DesignTiles` zeigt eigene
  Farbsätze aus HeliBoards Farbeditor unter „Farben“ und nimmt beim Wählen einer eingebauten
  Kachel Rundung, Schrift und Designbild zurück (`MyDesigns.leaveDesign`). Texte:
  `agent_design_strings.xml`. Tests `AT/MyDesignsTest.kt` (genau geschriebene und entfernte
  Schlüssel, Bild, Rundung, Schrift) und `AT/DesignEditorTest.kt` (Kacheln, Editor, Löschen mit
  Rückfrage, Vorschau-Pixel; Bilder unter `app/build/reports/design-editor/`).

- RGB-Seite (26, neu gestaltet 29a, Abschnitt „RGB-Beleuchtung“): `K/settings/screens/AgentRgbScreen.kt`
  und `AgentRgbCurveEditor.kt` (alle Optionen, speichert jede Änderung
  sofort über `RgbLighting.write`, Vorschau oben mit `withFrameNanos` und `RgbFramePacer`,
  Tipp auf die Vorschau probiert die Effekte; bei ausgeschalteten Android-Animationen steht sie
  still). Die Vorschau ist `DesignPreview` aus `AgentDesignEditor.kt` mit `PreviewLight` (gleiche
  Engine und Farbregeln wie die Tastatur). Bildwiederholrate als Karte mit zwei Farbflächen (37,
  `AgentRgbFrameRate.kt`). Einträge auf der Design-Seite (`agent_design_rgb`) und
  unter Aussehen. Texte: `agent_rgb_strings.xml`. Tests `AT/RgbSettingsScreenTest.kt`,
  `AT/RgbFrameRateCardTest.kt`. Eigene Lichtmuster (34): Galerie, Knöpfe und Editor
  `K/settings/screens/AgentRgbPatternEditor.kt` an Stelle der Seite, Texte
  `agent_rgb_pattern_strings.xml`, Test `AT/RgbPatternEditorTest.kt` (Abschnitt „RGB-Beleuchtung“).
- Schieberegler mit Ticks und Rastpunkten (28, P85–P87): `K/settings/screens/AgentSlider.kt`
  (`AgentSlider`: Material-3-`Slider` über einen eigenen `SliderState`; nur Werte vom Finger
  laufen durch `SliderDrag`, erkannt an `Finger.down` (Initial-Pass bis Final-Pass des Loslassens)
  oder `SliderState.isDragging`; TalkBack und Tasten setzen den Wert genau. `AgentSliderInput`
  für selbst gezeichnete Leisten: übernimmt die Berührungen eines Elements und gibt ihm
  Slider-Semantik. `rememberSliderSink`), `A/SliderFeel.kt` (rein: `SliderFeel` mit Tick-Abstand,
  runden Werten, Rastpunkten und Fangzone, `SliderDrag` je Zug mit Fangen, Halten, Loslassen,
  Wiederscharfstellen und Ratenbegrenzung, `SliderDrag.clockForTest`) und `A/SliderHaptics.kt`
  (Impulse wie die Haptik-Kette: ab API 30 `PRIMITIVE_TICK` 0,5 bzw. `PRIMITIVE_CLICK` 0,8, sonst
  Einzelpuls mit Amplitude 70/180, sonst Puls 8/16 ms; nur mit „Vibration bei Tastendruck“ und
  außerhalb von „Bitte nicht stören“ (außer erlaubt), gelesen einmal je Zug; ohne Usage-Attribute;
  `deviceForTest`). Unabhängig von `HapticChain` (eigene Konstanten).
  Regeln (Annahmen des Leads, vom Owner änderbar): Ticks bei festen Stufen auf jeder k-ten Stufe
  (höchstens 40), sonst auf Vielfachen von 1, 2, 5 × 10^n mit 20–40 Ticks, höchstens alle 25 ms
  (eine Rast darf direkt auf einen Tick folgen); Rastpunkte = Standardwert (wenn im Bereich),
  beide Enden und Vielfache von 1 oder 5 × 10^n mit höchstens 11 Abständen (0..1 → 0,1;
  0,3..1,5 → 0,5; 0..100 → 10; 100..700 → 100), bei festen Stufen mindestens vier Stufen
  auseinander und auf eine Stufe gelegt; Fangzone 1,5 % des Bereichs je Seite; eine Rast, die der
  Wert verlässt oder neben der ein Zug beginnt, fängt erst wieder nach dem doppelten Abstand; ein
  Sprung über eine Rast gibt den Rast-Impuls ohne zu halten. Übernommen in `SliderDialog`
  (alle `SliderPreference`), `KeyboardScalePreference`, `ColorPickerDialog` (siehe „Geänderte
  Upstream-Dateien“) und im Design-Editor (`SliderRow`). Noch nicht in `AgentRgbScreen.kt`
  (wird in 29 überarbeitet; dort `Slider` gegen `AgentSlider` tauschen, `defaultValue` aus
  `RgbSettings`). Texte `agent_slider_strings.xml`. Tests `AT/SliderFeelTest.kt` (rein, eigene
  Uhr) und `AT/AgentSliderTest.kt` (Compose: Ziehen, Ende, TalkBack, `SliderDialog` mit
  Vibrator-Attrappe, Einstellungen, Stufen der Geräte).
- Diagnose (30): `K/settings/screens/AgentDiagnoseScreen.kt` (Seite, „Kopieren“ legt
  `Diagnose.text` als Klartext in die Zwischenablage, Knopf „Test“ spielt den Plan der Kette einmal,
  „gstatic erreichbar?“ nur auf Tipp über `Dispatchers.IO`; kein `FLAG_SECURE`; jeder Abschnitt
  wird beim Öffnen einmal gebaut, „Symbole malen“ wegen der Wörterbuch-Dateien über
  `Dispatchers.IO`, Emoji-Mix neu nur, wenn sich sein Zustand ändert) und `A/Diagnose.kt`
  (liest nur Zustände: `HapticChain.plan` allein in `Diagnose.chain`, damit eine geänderte Kette
  nur dort angepasst wird; `HapticChain.deviceFor` ist dafür `internal`; Gesten-Bibliothek,
  Hauptwörterbuch über `getUserAndInternalDictionaries`, TalkBack über `AccessibilityManager`;
  `SubtypeSwitchLog.entries`; Emoji-Mix-Tabelle aus dem Speicher oder dem Asset, Cache-Zahl und
  -Größe, `checkGstatic` = ein GET des Paars 🐶 + 🐱 mit `EmojiMixClient` und `EmojiMixer.calls`,
  das Bild wird verworfen; Versionen und `Build.MODEL`, keine Kennungen, kein Schlüsselstatus).
  `A/SubtypeSwitchLog.kt`: die letzten fünf Sprachwechsel mit Grund, Zeit und Sprach-Tag, nur im
  Speicher (P98); `SubtypeChoice.holds` meldet eine behaltene Befehlswahl (`onKept`), nur wenn
  die App wirklich umgeschaltet hätte. Seit der zweiten Nachprüfung 52 `lastReason` (Grund des
  zuletzt notierten Wechsels) und `FIELD_START` (`APP_HINT`, `HINT_ENDED`, `PER_APP`) für
  `CommandAppSettings.onSwitchedInField` (P219).
  Texte: `agent_diagnose_strings.xml`. Tests: `AT/DiagnoseScreenTest.kt`,
  `InputLogicTest.subtypeSwitch*`.
  Etappe 55 „Letztes Feld“: `A/LastField.kt` hält nur im Speicher den `FieldReport.Snapshot` des
  letzten Felds, in dem die Tastatur gezeigt wurde. Zufuhr: `capture` (eine Zeile in
  `LatinIME.onStartInputViewInternal`, P233), `onSubtypeSwitched` (aus `SubtypeSwitchLog.onSwitched`,
  P234), `onStrip(chips, insideCommand)` (aus `KeyboardCommands.createChipsView`: Chips der
  Tastatur, Befehl) und `onStripView` (aus `SuggestionStripView`, P236). Alle Aufrufe unter einem
  Sperrobjekt (`lock`, Nachprüfung 55 M1). `snapshotOf` liest `InputAttributes` (`mInputType`, Paket,
  `mShouldShowSuggestions`, `mInputTypeShouldAutoCorrect`, `mApplicationSpecifiedCompletionOn`,
  `mIsPasswordField`, `mNoLearning`), dazu `EditorInfo.inputType` der App (vor `AppWorkarounds`,
  P232), und `SettingsValues` (`mSuggestionsEnabled`, `needsToLookupSuggestions()`,
  `mAutoCorrectEnabled`, `mAutoCap`, `mIsLocked`, `mIncognitoModeEnabled`, `mLocale` …), dazu die
  Schalter „Änderungsvorschläge“, „Immer Vorschläge anzeigen“ samt Web-Ausnahme, „Mehr
  Autokorrektur“ und „Autom. Groß-/Kleinschreibung“ aus den Einstellungen; Passwortfeld → nur
  `Password`, gesperrt → nur `Locked`; kein Lernen/Inkognito behält das Feld ohne Paket (seit 61, P257). Felder der eigenen Einstellungen
  ändern nichts und zählen nichts (`ownField`). Seit 56: `capture` startet zuerst
  `UpdateOnOpen.onStartInputView` (Abschnitt „Updates“), `stripNeutral` sagt, ob die Leiste des Felds
  zuletzt leer war (für den Update-Chip, P238). `live` fragt beim Öffnen die Wörterbücher der Engine
  (`WeakReference` auf den `DictionaryFacilitator`, nur bei gleicher Sprache, sonst unbekannt).
  `Diagnose.lastField` (über `Dispatchers.IO`: Wörterbuch-Dateien der Feldsprache über
  `mainDictionary`, das bei einem Fehler `null` = „unbekannt“ gibt, App-Name über den
  `PackageManager`) baut die erste Karte der Seite, Urteil und Ergebnis zuerst; `verdictText` macht
  aus `FieldReport.verdict` einen Satz. Die Seite fasst jede Zeile für TalkBack zusammen und setzt
  lange Werte (und auf schmalen Bildschirmen schon mittlere) unter ihre Beschriftung; „Kopieren“
  markiert den Clip bewusst nicht als vertraulich (er ist zum Weitergeben da und enthält keinen
  Feldtext; markiert legte `ClipGuard` ihn mit Zwischenablageverlauf als Geheimnis in den Tresor). Tests: `AT/LastFieldTest.kt` (echte `EditorInfo` und
  `SettingsValues`, Plattformzahlen, Firefox-Workaround, Leiste, eigene Einstellungen),
  `AT/DiagnoseScreenTest.kt` (Karte, Reihenfolge, Kopieren, keine Feldtexte, Firefox, verdeckte
  Leiste, Wörterbuch-Fehler, Passwort, Inkognito, gesperrt),
  `InputLogicTest.lastFieldHoldsWhatTheEngineUsedInTheField` (Weg durch LatinIME, Sprachwechsel).
  Etappe 61 „Korrektur in Apps erzwingen“: `C/ForcedCorrection.kt` (rein: `eligible(inputType,
  imeOptions)`: nur Textfelder, ohne Passwort-, E-Mail-, URI-, Filter-, Phonetik-, Namen-,
  Postanschrift-Variationen und ohne Aktion „Suchen“; `capsInputType`: `CAP_SENTENCES` dazu, wenn erzwungen
  und kein `CAP_*`-Flag; `DEFAULT_PACKAGES`), `A/ForcedApps.kt` (Liste in `agent_forced_correction_apps`,
  `forces(prefs, attributes)` ohne Ausnahme nach außen), `K/settings/screens/AgentForcedAppsDialog.kt` (Liste, Entfernen,
  Hinzufügen mit dem `AppChooser` des Snippet-Editors; Zeile in `AgentAreaTypingScreen` unter Regeln).
  `FieldReport.Snapshot.Field` hat `noLearning`, `incognito`, `forced`, `appForced`; `Snapshot.Hidden` und
  `Finding.INCOGNITO` gibt es nicht mehr (`Snapshot.Locked` für gesperrt); `Verdict` hat `forced` (was die
  Erzwingung wirklich einschaltete) und `learning`. `Diagnose.Section` hat `appPackage` und `appForced` für den
  Knopf der Karte. Tests: `CT/ForcedCorrectionTest.kt`, `AT/LastFieldTest.kt`, `AT/DiagnoseScreenTest.kt`,
  `AT/SettingsTypingAreaTest.kt`, `InputLogicTest` (`a forced app …`).
- `KeyboardProfileStore.kt`: das Designprofil enthält `agent_rgb_lighting` als Text
  (`Binding.freeText`); beim Lesen feldweise über `RgbSettings.parse` begrenzt, leer (Profile von
  vor 26) heißt „unverändert lassen“.
- Tipp-Leistung (64): `A/TypingPerf.kt` (64a) ist der Zähler ohne Text. `Hook` nennt die gemessenen Stellen mit festen
  Namen (`COMMAND`, `STRIP_CHIPS`, `FORMAT_CHIPS`, `FIELD_CHIPS`, `MEMORY_CHIPS`, `UPDATE_CHIP`, `FIELD_START`,
  `WORD_COMMIT`, `LEARNING`); `measure(hook) { … }` (inline) oder `begin()` / `end(hook, token)` messen einen Lauf mit zwei
  `System.nanoTime()`; nur der Hauptthread wird gezeitet, verschachtelte Läufe zählen für den Tastendruck nur außen, stehen aber
  einzeln in der Liste der zehn langsamsten. `app()` und `system()` zählen Anfragen aus jedem Thread (atomar, ohne
  Allokation). `keyPress()` (eine Zeile in `LatinIME.onEvent`) schließt den vorigen Tastendruck in einen Ring der letzten
  500 ein: Zeit des Tastendrucks, Anfragen an App und Android. `snapshot()` rechnet Median, 95. Perzentil (nächster Rang),
  Maximum und Durchschnitt, `reset()` leert alles und setzt „seit“. Es gibt keine Zeichenketten in der Schnittstelle außer
  `commandFailed(name, error)` (64c: Name des eingebauten Befehls und Klasse der Ausnahme, nie die Meldung). Die Zeit kommt
  aus `nanoSource` (Tests). Gemessene Stellen: `KeyboardCommands` (`updateGuard`, `createChipsView`, die drei
  Chip-Quellen, `load`), `UpdateChip.createView`, `UpdateOnOpen.onStartInputView`, `TouchModel.load` und
  `learnFromWord`, `Memory.onWordCommitted`, `TypoMemory.confirm`, `FieldCorrectionWatcher.checkpoint`; dort ist der
  Rumpf in eine `…Now`-Funktion gewandert und die öffentliche Funktion ruft sie über `measure`. Karte: `Diagnose.typing`
  (`Part.TYPING`), in `AgentDiagnoseScreen` als eigene Liste `shown` vor „App und Telefon“ eingefügt (`sections`
  bleibt, wie es war), Knopf `agent_diagnose_typing_reset`. Tests: `AT/TypingPerfTest.kt`.
  64b (P1, P2, P7, P8, P10, P13), Tests `AT/StripRequestsTest.kt`: `A/LockState.kt` ist der gemeinsame Sperrzustand.
  `LockState.normal` (Gerät gesperrt oder Speicher des Nutzers nicht entsperrt, wie `Memory`, `MemoryChips`,
  `ForeignWords`, `FieldSuggestionChips`, `SmartPasteChips` und `TouchModel.load` ihn fragten; deren Prüf-Hooks
  `deviceLocked` zeigen jetzt darauf) und `LockState.credentials` (die strengere Frage des Zugangsdaten-Speichers für
  den Update-Chip, `UpdateChip.locked`). Eine Antwort „entsperrt“ gilt `UNLOCKED_MILLIS` (1 s), „gesperrt“ `LOCKED_MILLIS`
  (5 s), ein Fehler beim Lesen zählt als gesperrt. Verfall sofort bei `ACTION_SCREEN_OFF`, `ACTION_SCREEN_ON`,
  `ACTION_USER_PRESENT` (ein Empfänger, beim ersten Lesen am Anwendungskontext angemeldet), beim Feldstart
  (`TouchModel.load` ruft `LockState.drop()` zuerst) und bei jedem Tipp auf einen Chip (`KeyboardCommands.chipRow` und `bindTaps`, also auch
  der Tresor, der Tipp-Zeitpunkt prüft live). Bewusst weiter live gefragt: `ClipVault.isBlocked`/`mayKeep`,
  `ScreenshotChip`, `EmojiSuggestions` (eigener 2-s-Speicher), `AgentEditorController` (Panel), `SettingsValues.mIsLocked`,
  `credentialAccessAllowed` im Zugangsdaten-Speicher.
  `FieldSuggestionChips` fragt den Text hinter der Schreibmarke über `nothingAfterCursor`: „nichts dahinter“ gilt, solange
  die Schreibmarke nur vorwärts geht (`emptyAfterCursorAt`); zurück, unbekannt, neues Feld (`fieldStarted`, aus
  `KeyboardCommands.load`) fragen neu, „Text dahinter“ wird nie gemerkt, und `insert` fragt beim Tipp noch einmal.
  `ClipVault.liveClip` merkt den gelesenen Clip `LIVE_MILLIS` (1 s; `offer` und `clear` verwerfen ihn; seit der Nachprüfung 64
  überleben die Zwischenspeicher des Tresors (`live`, `classified`) und `SmartPasteChips.recognised` höchstens `CACHE_MILLIS` = 2 s nach der Füllung, die die Frist armt (eine Frist, die spätere Füllungen nicht verschieben; liegt der Clip noch auf der System-Zwischenablage, lädt die nächste Anfrage ihn neu, und Chip-Ansichten sowie `UrlCommand.cached` erfasst die Frist nicht), und `forgetCaches()` leert `live`,
  `classified` und `SmartPasteChips.recognised`, wenn der Tresor ein Geheimnis herausgibt (`take`), bei `clear()` und bei jedem
  `LockState.drop()`; Füllen, `armForget` und `clear()` laufen im Hauptthread und sind nicht untereinander synchronisiert; nimmt der Handler die
  Frist nicht an, wird der Merker zurückgesetzt; Test `ClipCachesTest`),
  `classifyLive` die Einordnung des zuletzt gesehenen Texts (`isSecretClip` und `freshest` fragten je Aufruf einzeln).
  `SmartPasteChips.actions` merkt die Erkennung je (Kopierzeit, Inhalt, Markierung, Sprache, Region, Minute); die
  Geheim-Prüfung davor läuft jedes Mal. `KeyboardCommands.rowFor` baut die Chip-Zeile nur neu, wenn Leiste, Farben
  oder Beschriftungen, Beschreibungen und Arten der Chips sich ändern; sonst bleibt die Zeile und `bindTaps` bindet die
  Tipps an die neuen Chips (was ein Tipp tut, gehört zu dieser Aktualisierung). `UpdateChip.createView` merkt seine Zeile
  ebenso. `UpdateOnOpen.start` fragt nur noch die Einstellungen und `UpdateChip.locked` auf dem Hauptthread;
  `runDue` (Datensparmodus, Netz, installierte Version) läuft auf dem Worker, ein Grund zum Überspringen steht dort in
  `lastSkip`. `HapticChain.planFor` entscheidet je Vibrator und Art einmal, `deviceFor` gibt dasselbe Gerät zurück.
  Die Host-Aufrufe `textAfterCursor` und `editorTextBeforeCursor` in `KeyboardCommands.host` zählen ihre Anfrage selbst
  (sie fragen die App direkt, nicht über `RichInputConnection`).

## Flacher Stil

> Tastenstil „Flat“, Farbsatz „Flach schwarz“ und die Vorlage, die beides mit Größen einstellt.

- Auftrag: Aussehen nach fünf Screenshots des Owners (1200 px breit, 3,25 px je dp,
  Tastatur-View 793 px hoch: 39 px oberer Rand, 722 px Reihen, 32 px unterer Rand), als Ausgangspunkt für ein eigenes Design. Alle Maße stehen als
  Verhältnisse im Code, nicht als Pixel.
- `K/keyboard/FlatStyle.kt`: Konstanten (Farben, Zeilenskalierung, Höhe = 66 % der Breite),
  `colors()` (füllt jede `ColorType` über `AllColors`), `shortenNumberRow()` (Zahlenreihe
  niedriger, Buchstabenreihen entsprechend höher, Summe unverändert), `typeface()` (mittlere
  Strichstärke der Systemschrift), `lowercaseLift()`, `iconOverrides`, `LANGUAGE_SEPARATOR`,
  `scrollWhenTooLong()` (Laufschrift über `TruncateAt.MARQUEE` und `isSelected`, für TalkBack
  nicht „ausgewählt“; bei Animationsskala 0 „…“ statt Laufschrift), `animationsEnabled()`.
- `themes-flat.xml` (in `res/values/`): Stile `KeyboardTheme.Flat` (erbt von Rounded mit Rahmen),
  `Keyboard.Flat` (Lücken und Ränder), `KeyboardView.Flat`/`MainKeyboardView.Flat`
  (Hintergründe, Schriftverhältnisse), `PopupKeysKeyboard.Flat`.
- Hintergründe: `btn_keyboard_key_flat.xml` (Buchstaben, Leertaste, Aktionstaste über
  `state_active`) und `btn_keyboard_key_functional_flat.xml` (`state_single` = Pille), beide
  aus `btn_keyboard_key_flat_rect.xml` (6 dp) und `btn_keyboard_key_flat_pill.xml`. Je Taste
  genau eine Form ohne Kante, Schatten oder Ebenen: ein `drawRoundRect` je Taste.
- Icons `sym_keyboard_{shift,shifted,shift_lock,delete,smiley,go,return}_flat.xml`: Vektoren im
  Pixelmaß der Vorlage (Viewport 91 = 28 dp), deshalb keine Skalierung beim Zeichnen.
- `assets/layouts/functional/functional_keys_flat.json`: wie `functional_keys.json` (seit 81 auch dort
  `<`, `>` und „12/34“ auf der zweiten Symbolseite), aber die Emoji- und die 1234-Taste haben
  `"type": "character"` (heller Hintergrund) und es gibt nie eine Pfeiltaste.
- Die Symbolseiten `symbols_flat.txt` und `symbols_shifted_flat.txt` (23a) gibt es seit 81 nicht mehr:
  ihr Inhalt ist `symbols.txt` und `symbols_shifted.txt`, `FlatPreset` setzt keine Symbol-Layouts mehr.
- `K/agent/FlatPreset.kt`: schreibt 24 Darstellungs-, Layout- und Leisten-Einstellungen für das Hochformat
  (Liste: `values()`), berechnet die Höhenskala aus Bildschirmbreite und Standardhöhe und
  sichert die ersetzten Werte in `agent_flat_preset_backup` (siehe `docs/STORAGE.md`): beim ersten
  Anwenden alle, später nur Schlüssel, die eine neuere Vorlage dazubringt. Die Höhenskala liest
  die Hochformat-Ressourcen, auch wenn die Einstellungen gerade quer stehen.
  `restore()` setzt genau diese Schlüssel zurück, auch „war nicht gesetzt“. Knöpfe dafür:
  `AgentProfilesScreen.kt`.
- Leiste: `K/latin/suggestions/FlatStrip.kt`. Die Leiste behält ihre Höhe (40 dp, im
  Querformat 36 dp); was über die 36 dp hohe Knopfzeile hinausgeht, wird oberer Innenabstand.
  4 dp davon und der 39 px hohe obere Rand der Tastatur ergeben die 169 px der Vorlage.
  Solange das Menü offen ist, zeigt die Leiste die Werkzeuge (auch ohne „automatisch anzeigen“);
  ein externer Vorschlag (Autofill, Zwischenablage) verdrängt die Toolbar immer.
  Knöpfe 34 dp, außen 6 dp Rand; die Toolbar-Tasten verteilen sich über Abstandhalter mit
  gleichem Gewicht in einem einzigen Layout-Durchlauf (`fillViewport`), die letzte angeheftete
  Taste bekommt den Kreis. Keine `LayoutTransition` mehr im flachen Stil. Icons
  `sym_keyboard_{grid,clipboard,pen,mic}_flat.xml` (Viewport 111 = 34 dp).
- Menü: `K/agent/FlatMenuView.kt`, Kind mit Index 1 von `AgentKeyboardFrame` (`agent_menu`), also
  unter Panel und Rad. Der Inhalt liegt in einer `ScrollView` (Querformat). Es
  malt und fängt Touches nur unterhalb der Leiste; die Leiste bleibt bedienbar. Sechs `Button`
  (`agent_menu_*`) plus `agent_menu_edit`; jede Aktion schließt zuerst das Menü und geht dann
  als Tastencode über den Listener der Leiste oder als Intent an `SettingsActivity`. Für
  Screenreader blendet `FlatStrip.toggleMenu` nur `keyboard_view_wrapper` aus, nicht die
  Leiste; `AgentKeyboardFrame.updateOverlayAccessibility` zählt das Menü deshalb nicht mit. Es
  schließt sich, wenn das Tastaturfenster verschwindet.
- Zwischenablage: `K/keyboard/clipboard/FlatClipboard.kt` baut die Kopfzeile in `clipboard_strip`
  (`agent_clipboard_back`, `agent_clipboard_title`, danach die Tasten aus
  `clipboard_toolbar_keys` als Kreise), den Abschnittstitel `agent_clipboard_section`, den
  Kartenstil (`styleCard`, `squareImage`) und den Chip der Leiste (`styleChip`: dunkle Pille,
  rundes Vorschaubild, kein Schließen-Kreuz, langes Drücken blendet aus; ein Clip-Label erscheint
  nur bis 24 Zeichen und nie bei sensiblen Clips). `K/latin/ClipboardThumbnails.kt` gilt für alle
  Stile: Bilder werden mit passender Abtastrate einmal dekodiert und in einem `LruCache` von
  16 MB gehalten, nur im Speicher; der Schlüssel enthält Änderungszeit und Größe der Datei, und
  beim Verlassen der Zwischenablage-Ansicht wird alles verworfen. Das Bild des Chips liegt in
  einem einzelnen Platz, wird ab Android 9 mit `ImageDecoder` (EXIF-Drehung) dekodiert und vor
  Android 8 gar nicht gemerkt. `styleChip` richtet die Beschriftung links aus (die Ansicht
  umschließt den Text, also sieht es aus wie zentriert; einzeilig zentrierter Text wird in
  Robolectric nicht gezeichnet). Die Kopfzeile zeigt höchstens drei Tasten und nie „Schließen“;
  ohne sichtbare Leiste (Leiste ausgeblendet) bleibt die Ansicht beim alten Aufbau mit
  unterer Tastenreihe.
- Screenshot-Pille: `K/agent/ScreenshotChip.kt`, opt-in (`AgentPreferences.SCREENSHOT_CHIP`,
  aus) und nur mit Bildberechtigung. `current` sucht das neueste Bild mit „screenshot“ im
  Ordnerpfad (`RELATIVE_PATH`, vor Android 10 `DATA`), höchstens drei Minuten alt; das Ergebnis
  bleibt gemerkt, bis ein `ContentObserver` auf `MediaStore.Images` eine Änderung meldet. Nie in
  Passwort- oder Zahlenfeldern, im Inkognito-Modus oder bei gesperrtem Gerät, und nur in Feldern,
  deren `EditorInfo` den Bildtyp annimmt (`editorAccepts`), damit `onContent` nie den Umweg über
  die System-Zwischenablage nimmt. Ohne funktionierenden Observer höchstens eine Abfrage alle
  5 s. Die Pille nutzt das Layout des Zwischenablage-Chips (im flachen Stil
  `FlatClipboard.styleChip`). Ein Tipp kopiert das Bild auf einem Hintergrund-Thread nach
  `files/agent_screenshot/` des Tastaturspeichers (DE) und übergibt danach auf dem Main-Thread
  die URI des eigenen FileProviders an `KeyboardActionListenerImpl.onContent`, nur wenn noch
  dasselbe Feld aktiv ist (`isSameField`: Paket, `fieldId`, Eingabetyp) und es das Bild annimmt; der
  Medienspeicher-URI selbst wird nie weitergegeben. Kopien älter als 10 Minuten löscht jede
  Abfrage, beim Ausschalten werden alle gelöscht. Benutzt oder geschlossen, kommt derselbe
  Screenshot nicht wieder (ID in `agent_screenshot_dismissed`). Einstellungen und
  Berechtigung: `K/settings/screens/AgentScreenshotScreen.kt`. Test: `ScreenshotChipTest`.
  Etappe 23g „Screenshot zuerst“: Der Observer ruft `mediaChanged` (setzt `stale`; nach
  `DEBOUNCE_MILLIS` = 500 ms Ruhe startet `settle` die Abfrage, `deliver` meldet danach einmal
  `onChange` auf dem Main-Thread); `start` hängt ihn schon beim Start der Tastatur an. Threads:
  Abfrage (`finder`), Größenprüfung, Kopie und Prüfsumme für den Verlauf und das Aufräumen der
  Kopien (`scheduleCleanUp`, nur solange `copiesMayExist`) laufen auf `background` (eigener
  Einzel-Thread, nicht `ExecutorUtils.KEYBOARD`, auf den `FileUtils` mit einem Latch wartet);
  `current`/`newest` lesen auf dem Main-Thread nur `cached` und stoßen bei `stale` eine Abfrage
  an (`refresh`, nie zwei zugleich, eine Änderung währenddessen fragt danach noch einmal);
  Ergebnisse kommen über `onMain` (`postAtTime`) zurück, ein neues Ergebnis meldet `onChange`.
  `resetForTest` lässt `background` an Ort und Stelle laufen; `clock` ist die Wanduhr (Tests).
  `ClipboardHistoryManager.onScreenshotChange` zeigt die Pille nur bei sichtbarer Tastatur und
  zwischen den Wörtern über `setNeutralSuggestionStrip` (Befehlschips zuerst); im Modus
  EXPANDABLE wird die Leiste eingeklappt wie bei Befehlschips. „Zwischen den Wörtern“
  (`screenshotMayReplaceStrip`): keine Komposition, vor der Schreibmarke nichts, ein Leerraum
  oder ein Worttrenner (`isWordSeparator`), so auch in Feldern ohne Komposition; kein
  Inline-Autofill (`LatinIME.isShowingInlineAutofill`) und keine nicht leeren App-Vorschläge
  (`mApplicationSpecifiedCompletionOn`, Stil `INPUT_STYLE_APPLICATION_SPECIFIED`). Während ein
  Wort getippt wird, zeigt die Leiste dessen Vorschläge; an der nächsten Wortgrenze ersetzt die
  Pille die Vorhersagen bzw. die neutrale Leiste, bis sie benutzt, geschlossen oder zu alt ist.
  `scheduleExpiry` meldet den Ablauf nach drei Minuten; ist der Screenshot nach der Wanduhr noch
  frisch (Uptime und Wanduhr laufen auseinander), plant `expiry` neu. Danach blendet
  `hideSuggestionView` die Pille aus und stößt `postResumeSuggestions` an, das über
  `restartSuggestionsOnWordTouchedByCursor` die Wortvorhersagen zurückholt. Die Pillen-View wird
  für denselben Screenshot nur mit denselben `Colors` wiederverwendet (neues Design oder neue
  Einstellungen: neue View). Verlauf: Beim ersten Sehen eines Screenshots entscheidet `deliver`
  einmal, ob er in den Verlauf darf: nur wenn eine Medienänderung ihn höchstens
  `HISTORY_MAX_AGE` = 60 s nach der Aufnahme meldet, er nach der letzten Änderung bei
  unerlaubtem Zustand aufgenommen wurde (`privacyBlockMillis`, gesetzt in `mediaChanged`) und
  `privacyAllowsHistory` gilt (Gerät entsperrt, letztes Feld bekannt und nicht inkognito, auch
  bei ausgeblendeter Tastatur). Sonst steht seine ID sofort in `agent_screenshot_history_id` und
  er kommt nie hinein, auch nicht nach dem Entsperren oder einem Neustart. `addToHistory` prüft
  dazu live Schalter, Berechtigung, Verlauf, Inkognito und Sperre, kopiert auf `background`
  (`maySaveFromUri`, Kopie nach `files/agent_screenshot_pending/`, SHA-256) und legt den Eintrag
  in `finishHistory` auf dem Main-Thread an (`ClipboardDao.insertNewEntry`, Datei umbenannt nach
  `files/clipboard/<sha256>.<endung>` wie bei `addClipUri`). Liegt dasselbe Bild schon im
  Verlauf, bleibt dieser Eintrag unberührt. Ein Fehler beim Kopieren wird einmal wiederholt, dann
  aufgegeben. Die Kopie wird wie jedes Bild-Clip mit dem Eintrag gelöscht (FileProvider
  `clipboard_data`). Angelegte Einträge stehen als „ID:Zeitstempel“ in
  `agent_screenshot_history_entries`; `removeFromHistory` (Schalter aus in den Einstellungen oder
  `stopWatching`) löscht nur Einträge mit unveränderter ID und Zeit, außer angehefteten, nie
  während die Verlaufsansicht offen ist. Kopiert der Nutzer dasselbe Bild selbst, bekommt der
  Eintrag eine neue Zeit und bleibt. Die System-Zwischenablage wird nie gesetzt. Tests:
  Build 12 (Nachprüfung): `finder` liefert alle Screenshots der letzten drei Minuten (neueste
  zuerst, höchstens 10, sortiert nach `DATE_ADDED` und `_ID`); `deliver` entscheidet jeden zum
  ersten Mal gesehenen einzeln (`seenIds`), erlaubte kommen in `historyCandidates`, und
  `addToHistory` kopiert alle offenen Kandidaten, so geht ein zweiter Screenshot im
  Entprellfenster nicht verloren (P81). Entschiedene IDs stehen in `agent_screenshot_history_ids`
  (die neuesten 32; die alte Einzel-ID `agent_screenshot_history_id` wird weiter gelesen).
  `cleanUp` löscht auch Reste in `files/agent_screenshot_pending/`, die älter als 10 Minuten sind
  (beim Ausschalten alle). `stop` (aus `ClipboardHistoryManager.onDestroy`) meldet den Observer ab
  und entfernt alle geposteten Runnables. Tests mit nur einem Screenshot setzen `findOnly`.
  `ScreenshotChipTest` (Entprellen, Ablauf mit Neuplanung, Threads von Abfrage und Kopie,
  Sperre/Inkognito beim ersten Sehen, Verlauf, eigene Kopie bleibt, Wiederholung),
  `InputLogicTest.screenshotChip*`.
- Panel: `K/agent/FlatPanelStyle.kt` ändert nur Aussehen und Reihenfolge von `AgentPanelView`
  (IDs, View-Typen und Callbacks bleiben): Kopfzeile mit rundem Zurück- und Einstellungs-Knopf
  (Text als `contentDescription`), Ergebnisblock direkt unter Senden/Abbrechen, Beschreibung und
  Diktat-Chip ans Ende, alle `Button` als Chips, „Übernehmen“ und „Senden“ in der Akzentfarbe.
  `setBusy` zeigt in der Karte vier Balken, die in 80-ms-Schritten pulsieren, nur während
  `showCloudBusy` und nur bei eingeschalteten Systemanimationen.
- Tests: `FlatStyleTest` (Vorlage, Farben, Tastengeometrie gegen die Messwerte),
  `FlatStyleRenderTest` (zeichnet die echte `MainKeyboardView` und die Leiste mit nativer Grafik
  und prüft Pixel und Positionen), `FlatMenuTest` (Positionen der Pillen, Aktionen,
  Zusammenspiel mit der Leiste), `FlatClipboardTest` (Abtastrate und Cache, Kopfzeile, Karten,
  echte `ClipboardHistoryView`, Chip) und `FlatPanelTest` (IDs, Typen und Callbacks, Positionen,
  Balken). Jeder dieser Tests lädt danach die Standardeinstellungen neu (Fallstrick P32). Bilder unter `app/build/reports/flat-style/`, nicht versioniert.

- Etappe 70 (A5, A6): `FlatStyle.scrollWhenTooLong` lässt den Lauftext `MARQUEE_REPEATS` = 3 Mal
  laufen (vorher endlos). Der Lade-Puls von `FlatPanelStyle` hält an, sobald das Panel nicht
  sichtbar ist (`result.isShown`), und `FlatPanelStyle.resume()` startet ihn, wenn `AgentPanelView`
  wieder sichtbar wird (`setVisibility`, `onWindowVisibilityChanged`) und die Anfrage noch läuft.
  Tests `AT/FlatMenuTest.theTickerStopsAfterAFewRuns`,
  `AT/FlatPanelTest.theLoadingPulseStopsWhileThePanelIsClosedAndResumesWhenItIsShownAgain`.

## Feste Leiste

> Die Leiste wie Gboard (Etappen 85, 86 und 88): feste Plätze für die Werkzeuge, Raster aller Werkzeuge, Anpassen in der Tastatur mit eigener Berührungsverarbeitung. Das Konzept steht in `docs/LEISTE-KONZEPT.md`.

- Auftrag (Owner, 2026-11-20): Die Leiste soll in jedem Stil feste, gleichmäßig verteilte Plätze haben (1 bis 5 oder 8
  Werkzeuge, nichts scrollt, kein Pfeil) und sich anpassen lassen. Gilt für den Modus „Tasten + Vorschläge“
  (`ToolbarMode.EXPANDABLE`, Standard); die anderen Modi (`TOOLBAR_KEYS`, `SUGGESTION_STRIP`, `HIDDEN`) sehen aus und
  verhalten sich wie vorher. Wer den Modus je ändern musste, hat ihn nie geändert, solange er auf dem Standard steht.
  `SuggestionStripView.fixedBar` ist `mToolbarMode == EXPANDABLE`, gelesen beim Bauen der Ansicht (jede Änderung der Leiste
  baut sie neu, `setThemeNeedsReload`).
- Modell: `K/latin/utils/BarTools.kt` (rein, ohne Ansicht). Die Leiste ist eine geordnete Liste von höchstens `MAX` = 8
  `ToolbarKey`; bis `FULL_SIZE_MAX` = 5 volle Größe, darüber die kompakte Reihe. Gespeichert in
  `AgentPreferences.BAR_TOOLS` (`agent_bar_tools`) im Format der alten Leistenwerte (`NAME:true|NAME:false|…`, die
  eingeschalteten in Leistenreihenfolge), damit der Sortier-Dialog der Einstellungen (`ReorderSwitchPreference`) sie
  unverändert bearbeitet (`ToolbarScreen.kt`: im Modus EXPANDABLE steht dort „Werkzeuge in der Leiste“ statt
  „Leisten-Tasten“, ohne „Angeheftete“ und ohne „Beim Halten anheften“). Fehlt der Wert, **berechnet** `BarTools.read`
  die Leiste aus den alten Werten (`migrate`) und schreibt nichts; geschrieben wird nur bei einer Bearbeitung
  (`write`, `pinKey`, `unpinKey`, Etappe 86: jede Ablage). `migrate`: die ersten fünf eingeschalteten Tasten der alten
  Reihenfolge, danach die angehefteten Tasten, die nicht darunter sind (sie waren immer sichtbar), höchstens acht; steht
  keine Mikrofon-Taste (`MIC_KEYS` = `VOICE`, `DICTATE`) zuletzt, wandert die erste `VOICE` (sonst `DICTATE`) ans Ende, denn
  die letzte Mikrofon-Taste sitzt im Kreis rechts. Ergebnisse: Standard (AGENT, SETTINGS, VOICE, CLIPBOARD, UNDO, …, angeheftet
  AGENT) → AGENT, SETTINGS, CLIPBOARD, UNDO, VOICE; Flacher Look (CLIPBOARD, EMOJI, AGENT, angeheftet DICTATE) → CLIPBOARD, EMOJI,
  AGENT, DICTATE (wie die Leiste vorher). Alles andere der alten Liste liegt im Raster.
- Neue Werkzeuge im Enum `ToolbarKey`: `PROMPTS` (Ablage, Code `AGENT_PROMPTS`), `CHAR_ART` (Zeichen-Kunst, `AGENT_CHAR_ART`)
  und `DESIGN` (Einstellungen → Aussehen, neuer `KeyCode.AGENT_DESIGN` = -12008, behandelt in `KeyboardActionListenerImpl`
  über `A/SettingsLauncher.kt`), je mit Zeichenkette und Symbol (`ic_palette.xml` für Design, `ic_agent_prompts`, `ic_edit`). Sie erscheinen
  auch in den alten Listen der Einstellungen (ausgeschaltet). „Größe ändern“ gibt es in der Tastatur nirgends als Werkzeug und wurde nicht erfunden.
  `BarTools.offered` = alle Werkzeuge außer `CLOSE_HISTORY` (interne Taste der Zwischenablage-Ansicht); `gridOrder` ordnet das Raster
  (Textaktionen, Diktat, Skill-Tasten, Ablage, Zeichen-Kunst, Zwischenablage, Emoji, … danach der Rest in Enum-Reihenfolge).
- Zustand: `K/latin/suggestions/BarState.kt` (rein). Zeigt die Leiste die Werkzeuge (`TOOLS`) oder Inhalt (`CONTENT`: Wortvorschläge,
  Befehls-, Auswahl-, Symbol-Chips, Zwischenablage-Pille, Screenshot-Pille, Autofill, Update-Chip)? Welcher Inhalt gewinnt, bleibt
  unverändert bei `LatinIME` (`setSuggestedWords`, `setNeutralSuggestionStrip`); `BarState` sieht nur „es gibt Inhalt“.
  `setSuggestions(leer)` → `onNeutral` (Werkzeuge); `setSuggestions(nicht leer)` und `setExternalSuggestionView` → `onContent`;
  `setToolbarVisibility(true/false)` (die alten Aufrufe von `LatinIME`) → `onToolsRequested` / `onContentRequested` (ohne Inhalt bleibt es bei
  den Werkzeugen, damit die Leiste nie leer ist); `onKeyboardHidden` (aus `LatinIME.hideWindow`) → `reset`. Linke Taste: bei Werkzeugen
  das Raster (`openGrid`, Zustand bleibt), bei Inhalt „Werkzeuge zeigen“ (`onLeftKey`: `TOOLS`, `toolsChosen`; der Inhalt kommt mit der
  nächsten Aktualisierung zurück, also dem nächsten Tastendruck oder Cursorsprung), bei offenem Raster das Kreuz. Der Modus
  „Automatisch zeigen“ (`mAutoShowToolbar`) gilt weiter (Werkzeuge bei Markierung und Zeilenanfang statt der Auswahl-Chips); „Automatisch
  ausblenden“ ist in diesem Modus ohne Wirkung (Inhalt ersetzt die Werkzeuge immer).
  `applyBarState` setzt alle Sichtbarkeiten an einer Stelle (Werkzeuge, Vorschlagsleiste, angeheftete Tasten immer weg, Verfügbarkeit, linke Taste).
- Geometrie: `K/latin/suggestions/FixedBar.kt`. Die `HorizontalScrollView` der Leiste wird in `install` durch einen `FrameLayout` mit
  derselben Kennung `toolbar_container` ersetzt (die Zeile `toolbar` darin bleibt; Tests und `RgbStrip` finden beide wie bisher). Vor jedem
  Knopf steht ein Abstandhalter (`Space`, Gewicht 1, `agent_bar_gap`); alle Knöpfe und die linke Taste haben eine Größe (voll 40 dp, im
  flachen Stil 34 dp, kompakt ab sechs Werkzeugen 30 dp mit Rand um das Symbol), links sechs dp Rand, rechts sechs dp Rand der Zeile: die Mitten
  von linker Taste und Werkzeugen liegen gleichmäßig von Rand zu Rand, der Platz hängt nur von der **Anzahl** ab, nie vom Werkzeug oder vom
  Inhalt (ein Layoutdurchlauf, keine `LayoutTransition`). Die letzte Mikrofon-Taste bekommt einen Kreis (`CircleBadge`, höchstens 34 dp,
  Farbe `ACTION_KEY_BACKGROUND`, im flachen Stil `FlatStyle.STRIP_CIRCLE`). Die Knöpfe tragen die Kennung `agent_bar_tool` (der Gerätetest
  filtert sie damit). Ein Werkzeug, das das Feld nicht erlaubt (`BarTools.PROTECTED` bei Passwortfeld, Inkognito, `mIncognitoModeEnabled`, oder
  gesperrtem Gerät), bleibt am Platz, zu 38 % sichtbar, nicht anklickbar, Beschreibung „…, in diesem Feld nicht verfügbar“; die Steuerungen der
  Werkzeuge prüfen beim Tipp weiter selbst (das Dimmen ist nur der Hinweis). Die Zwischenablage-Taste ist nie gedimmt (sie hat kein eigenes Feld-Verbot).
  Bei gesperrtem Gerät gibt es die Leiste gar nicht (`mToolbarMode` = HIDDEN).
- Raster: `K/agent/ToolGridView.kt`, Kind von `AgentKeyboardFrame` (`agent_tool_grid`, nach `agent_menu`), in jedem Stil. Gleiches Maß wie
  `FlatMenuView` (Pillen 48 dp hoch, zwei Spalten, Ränder 24 dp, Lücken 8 dp), `ScrollView`, malt und fängt Touches nur unter der Leiste,
  schließt sich mit dem Tastaturfenster. Eine Pille je Werkzeug (Symbol und Name, Beschreibung = Name), Tipp schließt zuerst das Raster und
  sendet dann denselben Tastencode wie die Taste (`getCodeForToolbarKey`, auch eigene Codes der Einstellungen) über den Listener der Leiste.
  `VOICE` nur mit Spracheingabe, `BACKGROUND_GATHERING` nur wenn je benutzt (wie die Einstellungen). `InputView.hasAgentOverlay` und
  `AgentKeyboardFrame.isKeyAreaMenu` zählen das Raster wie das flache Menü. Das flache Menü (`FlatMenuView`) bleibt für den flachen Stil in den
  anderen Modi (nur dort noch erreichbar).
- Überspringen alter Wege: Schnell-Anheften (`mQuickPinToolbarKeys`) ist immer aus; die Skill-Tasten-Schalter (`pinKey`/`unpinKey`/`isKeyPinned`
  in `ToolbarUtils.kt`) schreiben die alte Liste der angehefteten Tasten und im Modus EXPANDABLE zusätzlich die Leiste (voll: nichts).
- Aussehen (Etappe 88, Fallstricke P470 bis P474): `K/latin/suggestions/StripIconColors.kt` (rein, Farbrechnung mit `RgbColors.contrast`): Jede Farbe der Farbliste der Leistensymbole
  (normal, gedrückt, aktiviert; gelesen wie `RgbStrip` es tut) wird zu Weiß oder Schwarz verschoben, bis der Kontrast gegen den Streifen (`STRIP_BACKGROUND` über
  `MAIN_BACKGROUND`) mindestens 4,5:1 beträgt (`ensureContrast`), nie unter 3:1. Nicht verfügbar (`BarTools.unavailableReason`: `PRIVATE_FIELD`, `LOCKED_DEVICE`, `NUMBER_LAYOUT`):
  echte Farbe (halbe Symbolfarbe über dem Streifen, mindestens 2:1, `off`) und ein Schloss (`BarToolButton`, Unterklasse von `ImageButton` in `FixedBar`), kein Alpha der Ansicht; der Platz bleibt, das Werkzeug
  bleibt für das Berührungssystem aktiv und ein Tipp sagt den Grund (`FixedBar.showHint`, Zeile 2,5 s anstelle der Werkzeuge; im Raster `ToolGridView.onUnavailable`). Die Symbole der Fork sind
  weiß gefüllt (`ic_agent_actions`, `ic_skill_*`, `ic_agent_dictate`, neu `ic_agent_char_art` statt `ic_edit` für `CHAR_ART`): schwarz mal Farbe war schwarz.
  Linke Taste: das Symbol bekommt den Kontrast gegen ihre Scheibe oder den Streifen (`updateLeftKey`).
- Abstände (Etappe 88): `FixedBar.Sizes`: Jedes Element (linke Taste, Werkzeuge, auch der Kreis) hat dieselbe Zelle (40 dp voll, flach 34 dp, kompakt 30 dp), Abstandhalter mit gleichem Gewicht, 6 dp Rand an
  beiden Enden: die **Mitten** sind gleichmäßig verteilt (so die Referenzbilder des Owners, `FlatStyleRenderTest`), die Plätze hängen nur von der Anzahl ab. Alle Symbole werden in dieselbe Fläche skaliert
  (24 dp, kompakt 20 dp, `FIT_CENTER`; im flachen Stil mit voller Größe bleiben die eigenen 34-dp-Symbole, `centred`), damit sind die sichtbaren Lücken zwischen gleich großen Symbolen gleich. Nur der Kreis ist breiter.
  `FixedBar.visualBox`/`leftBox` geben die sichtbaren Flächen (Tests), `slotCentres(count)` die Plätze aus der Anzahl (Treffer beim Ziehen).
- Anpassen (Etappe 86, neu gebaut in Etappe 88; Fallstricke P475 bis P481): Das Ziehen nutzt **kein Systemziehen**. `K/latin/suggestions/BarDrag.kt` (rein, mit Zeit und Anordnung als
  Parameter): Zustandsmaschine (`IDLE`, `PRESSED`, `DRAGGING`, `SCROLLING`), Haltezeit 180 ms, Touch-Slop, Zone (Leiste plus 40 dp, nach oben alles), Lücke an dem Platz, dessen Mitte dem Finger am nächsten
  liegt (Anordnung mit der Anzahl nach dem Ablegen, darum ohne Rückkopplung), Ablegen/Abbruch/voll, ein Zeiger. `K/latin/suggestions/BarEditor.kt` (rein): das Modell; `bar` gespeichert, `preview`,
  `shown`; `add`, `remove`, `shift`, `toggle`, `dragStart`, `dragOverBar`, `dragLeftBar`, `dropOnBar`, `dropOnGrid`, `dragEnd`; neu `undo` (eine Ebene), `reset` (`BarTools.standard`), `slotCountAfterDrop`.
  **Gespeichert wird beim Loslassen** (`write` in `commit`, einmal je Änderung). Der Behälter ist `ToolGridView` (ganzes Tastaturfenster; `onInterceptTouchEvent`/`onTouchEvent` in `A/ToolGridView.kt`):
  Berührungen auf einem Leistenplatz (`slotAt`) nimmt er selbst, auf einer Pille erst, wenn die Logik aufnimmt (davor gehört die Berührung der Pille und dem `ScrollView`), alles andere (linke Taste) lässt er
  durch. Die Kopie des Werkzeugs (`floatingCopy`, Kind des Behälters, Erhebung, 1,15fach) folgt dem Finger und gleitet beim Ablegen in den Platz (`slotCentre`) oder zur Pille. Verdrahtung in
  `SuggestionStripView`: `enterEdit`/`exitEdit`, `onDragEvents` (Haptik, Hinweise, Ansagen), `barGeometry`, `slotKeyAt`, `slotCentreOf`, `populateBar` (mit `glideSlots`, die Plätze gleiten in 140 ms),
  `refreshEdit`. Rückgängig und Zurücksetzen (mit Frage im Raster) im Hinweisbereich von `ToolGridView.header`. `exitEdit` (Häkchen, Raster geschlossen, `onKeyboardHidden`) lässt Gespeichertes stehen
  und verwirft ein laufendes Ziehen. `populateBar` baut die Plätze aus einer Liste (auch beim Start der Leiste); Texte in `agent_bar_tools_strings.xml` (Englisch und Deutsch).
- Kontextwerkzeuge (Etappe 88, P482): `A/UndoOffer.kt` (rein): nach einem Rückgängig trägt das Symbol einen Wiederholen-Pfeil, bis der Text sich ändert; Hook in `KeyboardActionListenerImpl.onCodeInput`
  (eine Zeile), Rücksetzen in `onKeyboardHidden` und `onFieldStarted`; Wiederholen ist der Langdruck (bestand) und eine Aktion für Screenreader.
- Tests: `AT/BarToolsTest.kt` (Modell), `AT/BarEditModeTest.kt` (Anpassen an der echten Leiste mit **echten `MotionEvent`s** an den Behälter, kein `DragEvent`), `AT/BarIconVisibilityTest.kt` (jedes Symbol als echte Pixel in mehreren Designs), `AT/UndoOfferTest.kt`, `BarStateTest.kt`, `BarEditorTest.kt` und `app/src/test/java/helium314/keyboard/latin/suggestions/BarDragTest.kt` (Zustand, Anpassen-Modell, Berührungslogik mit falscher Uhr; `app/src/test/…/latin/suggestions/`), `AT/FixedBarTest.kt` (Geometrie
  für 0, 1, 2, 3, 4, 5, 6 und 8 Werkzeuge im Standard- und im flachen Stil, Zustandsmaschine an der echten Leiste, Raster, Umzug aus drei
  gespeicherten Einstellungen, Dimmen, andere Modi).

## RGB-Beleuchtung

> Animierte Farben für Tasten, Beschriftung, Hintergrund und Leiste (Etappe 26, überarbeitet in 29): Rechnen im Kern, Zeichnen über einen Painter.

- Rechnen: `C/RgbLighting.kt`, `C/RgbCurve.kt`, `C/RgbPatterns.kt` (siehe „Kern“). Koordinaten
  0..1 über die Tastatur-View, `aspect` = Höhe/Breite hält Kreise rund; die Leiste liegt bei
  y = 0. Eine Lichtfarbe trägt die Stärke im Alpha (Muster, Atmen, Effekte, Deckkraft),
  `RgbColors.applyLight` mischt sie mit der Designfarbe. Ablauf je Wert: Stelle im Durchlauf
  (`phase` ± Ort; Texturmuster: `RgbPatterns.field` liefert Farbstelle, Helligkeit, Weiß- und
  Grauanteil) → eigene Kurve (`RgbCurve.value`) → Palettentabelle (`paletteAt`).
- Elemente (29, Wunsch des Owners): `RgbTarget` Tasten, Hintergrund, Beschriftung, Vorschläge,
  Leistenknöpfe. Ob ein Element leuchtet, bleibt bei Ebenen und Bereichen (`lights`). Mit
  „Gleiches Muster überall“ (Vorgabe, `separate` = 0) nutzen alle den gemeinsamen Look; sonst
  hat jedes Element einen eigenen `RgbLook` (Muster, Kurve, Dauer, Richtung, Wellenbreite,
  Farben, Sättigung, Helligkeit, Deckkraft). `RgbEngines` legt je verschiedenem Look eine Engine
  an (gleiche Looks teilen eine), alle laufen auf derselben Uhr und bekommen jeden Tipp.
- Kurve (29d): x = Stelle im Durchlauf (Zeit und Ort), y = Stelle in der Palette (bei „Atmen“
  die Helligkeit), also genau, was die alten Verläufe waren (Annahme des Leads, mit dem Owner zu
  bestätigen). 2–24 Punkte, x streng steigend mit mindestens 0,01 Abstand, Enden bei 0 und 1,
  Werte auf 1/1000 gerundet. Monotone kubische Interpolation (Fritsch–Carlson): kein
  Überschwingen, flache Stücke bleiben flach; gleich hohe Enden teilen ihre Steigung (Kurve
  läuft ohne Knick weiter). Die Palette läuft im Kreis (letzte Farbe zurück zur ersten), wenn die
  Enden mindestens 0,5 auseinanderliegen (Sägezahn, Stufen), sonst wie die alten Hin-und-zurück-
  Verläufe über 300° bzw. erste bis letzte Farbe. Vorlagen: Sinus (9 Punkte, bis 0,02 genau),
  Dreieck (7), Sägezahn (2, exakt), An- und Abstieg (5, exakt), Stufen (2 je Stufe, Anstieg in
  1 % des Durchlaufs). Alte Texte ohne `curve` bekommen die passende Vorlage (`wave`, `steps`).
- Weiche Farben (29b): Palettentabelle je Engine mit 256 Einträgen je Abschnitt (eigene Farben
  liegen genau auf Einträgen). Eigene Farben werden in OKLCH gemischt (Mitte bleibt hell und
  farbig; reines OKLab wird zwischen Gegenfarben grau), der Regenbogen ist HSV mit gerundeten
  Ecken (`smoothTable`, zwei Box-Durchgänge über ±28 Einträge in sRGB, keine hellen Kanten bei
  Gelb, Cyan, Magenta). Raster des Lichts 48 Spalten (vorher 24). Die Zeit kommt nur über die
  Phase, die mit der echten Zeit zwischen Bildern wächst (Bildraten-unabhängig).
- Texturmuster (29c): Tinte (verzerrtes Rauschen, schwarz ohne Tinte), Rauten (diagonaler
  Regenbogen, Rauten-Rahmen als Kachel), Lamellen (zwei Farbwelten mit wanderndem Rand,
  Lamellen-Schattierung als Streifenkachel), Raster (Regenbogen um eine wandernde Mitte, graue
  Mitte, Vignette, Schraffur als Streifenkachel), LED-Punkte (fließender Regenbogen, Lochblende
  als Kachel), Höhenlinien (weiche Hügel, Linien als Kachel). Das Farbfeld wird je Bild im Raster
  gerechnet und gefiltert gestreckt; die feinen Details sind Kacheln (`RgbOverlay.tile`, einmal je
  Muster, die großen 384 × 384 in etwa 20–40 ms auf dem Testrechner), die nur über eine Matrix
  verschoben werden (`RgbOverlay.transform`, Drift über die Phase, je Bild in ein festes Feld
  geschrieben). Kein `RuntimeShader`: ein Weg für alle Android-Versionen, alles auf der JVM prüfbar.
- Zeit der Texturen (Prüfung 29): `RgbEngine.flow` wächst wie die Phase, springt aber an
  `RgbPatterns.flowPeriod` des Musters zurück, wo sich das Farbfeld genau wiederholt (P107); die
  Phase selbst verliert ab 10⁶ Durchläufen ganze `RgbOverlay.DRIFT_PERIOD` (200), da wiederholen
  sich Farben und Kachel-Drift. Vorher sprang die Bewegung alle 4096 Durchläufe.
- Ablauf: `A/RgbLighting.kt` (Singleton auf dem UI-Thread; Raster und Kacheln entstehen im
  Hintergrund, siehe „Bild des Lichts“): Einstellung `agent_rgb_lighting`,
  `engines` (`engineFor`), Uhr (`drawTimeMs`, in `STATIC` eingefroren), Modus aus `RgbRun.mode`
  (läuft, wenn irgendein leuchtendes Element sich bewegt, `RgbSettings.moves`) und ein
  `Choreographer.FrameCallback`, der nur in `RUNNING` läuft und je erlaubtem Bild
  `invalidateAllKeys` der Tastatur und `RgbStrip.paint` aufruft. Das nächste Bild wird erst
  angefordert, wenn es fällig ist (`RgbFramePacer.nanosUntilDue`, `postFrameCallbackDelayed`),
  nicht bei jedem VSync. Sichtbar heißt: angehängt, `isShown`, Fenster sichtbar, Breite > 0; die
  erste Größe meldet `onSizeChanged`. Energiesparmodus: Broadcast
  `ACTION_POWER_SAVE_MODE_CHANGED`, registriert am Application-Kontext nur, solange die
  Beleuchtung an und die Tastatur angehängt ist (abgemeldet beim Ausschalten und in
  `onDetached`, P63). Animationsskala (kein Broadcast): bei jedem Sichtbarwerden, beim Laden und
  höchstens alle 2 s während des Laufs. „Nur beim Tippen“ und statische Muster brauchen Bilder
  nur, solange ein Effekt verblasst; danach wird einmal neu gezeichnet.
- Nur die Tastatur aus `main_keyboard_frame` (`R.id.keyboard_view`, `RgbLighting.isMainKeyboard`)
  bekommt einen Painter und steuert den Ablauf. Die unteren Reihen der Emoji- und
  Zwischenablage-Ansicht sind ebenfalls `MainKeyboardView`s (`bottom_row_keyboard`, P65); ihre
  `setKeyboard`, Tipps, Sichtbarkeit und `onHideWindow` werden ignoriert.
- Bild des Lichts: `A/RgbTexture.kt` (Raster 48 × bis 32 aus `engine.base`/`effects`,
  `BitmapShader` mit Filter, ab API 33 ausdrücklich `FILTER_MODE_LINEAR` über
  `RgbTexture.bitmapShader`, P105; Kachel-Shader mit REPEAT). Genutzt von Tastatur und Vorschau.
- Raster im Hintergrund (Rückmeldung zu Build 14, Ruckeln): `update` rechnet das Raster nicht
  selbst. Es übernimmt das fertige Raster (Kopie von höchstens 48 × 32 Werten ins Bitmap) und
  bestellt das nächste auf dem Thread `rgb-light` (`RgbWork.run`), nur wenn sich etwas geändert
  hat (Engine, Phase, Fluss, Seitenverhältnis, Zeilen, Element, Uhr nur solange ein Tipp-Effekt
  verblasst); ein stehendes Muster rechnet danach nichts mehr. Die Engine des Auftrags ist eine
  Kopie (`RgbEngine.copyStateFrom`: Phase, Fluss, Seitenverhältnis, Tipps), die UI-Engine läuft
  weiter. Doppelt gepuffert: Auftrag schreibt in einen Puffer, den der UI-Thread nicht zeigt
  (`spare`), der UI-Thread tauscht; läuft noch ein Auftrag, bestellt das nächste Bild. Das Bild
  liegt damit ein Raster hinter der Uhr (beim Laufen ein Bild); das allererste Bild eines
  Elements hat noch kein Licht. Fertige Raster und Kacheln melden `RgbWork`-Zuhörer auf dem
  Main-Thread: die Tastatur zeichnet nur neu, wenn keine Bilder laufen, die Vorschau nur, wenn
  sie steht. `wait = true` rechnet alles auf dem aufrufenden Thread (Galeriebilder im
  Hintergrund). Palettentabellen teilen Engines mit gleichen Farben (`RgbPaletteTables`, 16).
- Selbstmessung (Rückmeldung zu Build 14): `RgbLighting.frameWork()` liefert
  `RgbFrameStats.Summary` über die letzten 5 s: Bilder, Licht-Arbeit auf dem UI-Thread je Bild
  (`beginDraw` plus `beginKey`/`drawOverBackground`) im Mittel und höchstens, das ganze
  Zeichnen der Tastatur (`beginDraw` bis `endDraw`), Zahl und Dauer der Raster im Hintergrund.
  Für die Diagnose-Seite gedacht, dort noch nicht angezeigt.
- Kacheln (Prüfung 29): `RgbTiles` in derselben Datei macht sie auf einem eigenen
  Hintergrund-Thread (`rgb-tiles`, niedrige Priorität). `ready` blockiert nie: fehlt die Kachel,
  wird sie bestellt und das Bild ohne Detail gezeichnet; ist sie fertig, ruft `RgbTiles` auf dem
  Main-Thread seine Zuhörer (`RgbLighting` zeichnet die Tastatur neu, die RGB-Seite ihre
  Vorschau). `RgbLighting.load` bestellt die Kacheln der benutzten Muster schon beim Lesen der
  Einstellung. Der Cache hält alle fünf Kacheln (nur Bitmap und Mittelfarbe, nicht das
  Pixel-Array, zusammen etwa 1,3 MB) und wird geleert, wenn die Beleuchtung aus ist und bei
  `onTrimMemory`. `build` macht eine Kachel auf dem aufrufenden Thread (nur Hintergrund:
  Galeriebilder, Tests). Tests warten vor Pixelvergleichen mit `RgbTiles.awaitIdle` (P106).
- Tasten: `A/RgbKeyPainter.kt`, einer für die Haupttastatur (andere `KeyboardView`s wie Popups,
  Emoji-Seiten, untere Reihen und mehr Vorschläge bekommen nie einen). `areaOf` ordnet Tasten
  Bereichen zu (Leertaste, Ziffern = Zahlenreihe, Funktions-/Aktionstasten, Umschalt, Komma und
  Punkt = Funktionstasten, sonst Buchstaben). Vorgabe (29b): Die Taste wird normal in der
  Designfarbe gezeichnet, danach `drawOverBackground` (Hook in `onDrawKeyBackground`) das weiche
  Licht der Tasten durch eine Alpha-Maske ihrer Drawable (`RgbKeyMasks`: das gewählte Kind,
  einmal je Drawable und Größe mit weißem SRC_IN-Filter als ALPHA_8 gezeichnet, P60), dann die
  Textur-Kachel, Blitz (weiß) und Druck (heller/dunkler) durch dieselbe Maske. Die Maske liegt bei
  (0, 0) des schon verschobenen Canvas, jede Taste hat eigene Shader-Objekte (P90). Mit „Jede Taste
  einfarbig“ (`solidKeys`) wie in 26: `PorterDuffColorFilter(SRC_IN)` auf dem gewählten Kind mit
  der Lichtfarbe der Mitte, gedrückt `RgbColors.pressed`; die Kachel kommt trotzdem über die Maske.
  Dann rechnet `beginDraw` kein Raster unter den Tasten, nur die Lage der Kachel
  (`RgbTexture.updateTileOnly`). Die eigenen Shader je Taste liegen in einem `SparseArray`.
  Reaktiver Blitz: Raster der Tasten ohne Tasten-Effekt (`FIELD_KEYS`), jede Taste mit ihrem
  Faktor; sonst nutzen Tasten und Hintergrund mit gleicher Engine dasselbe Raster (`copyFrom`).
  Ohne Tastenrahmen gilt die Designfarbe als durchsichtig. Beschriftung, Hinweise,
  Hinweis-Symbole, das „…“ und Symbole gehen durch `labelColor`: mit Ebene „Beschriftung“ in der
  Lichtfarbe der Beschriftungs-Engine an der Tastenmitte, immer mindestens 3:1 zum Hintergrund
  dahinter; als Hintergrund zählt die Mitte von Taste und Tastaturhintergrund plus der
  Mittelwert der Kachel (`RgbTile.mean`, z. B. dunkle LED-Blende). Grenze: Feine Details wechseln
  innerhalb der Taste, die Lesbarkeit gilt gegen den Mittelwert; mit eigenem Hintergrundbild wird
  gegen `MAIN_BACKGROUND` gerechnet, nicht gegen das Bild.
- Leiste: `A/RgbStrip.kt` färbt die Wort-`TextView`s direkt in `suggestions_strip` (Engine der
  Vorschläge) und alle `ImageView`s in Toolbar, angehefteten Tasten und der linken Taste (Engine
  der Leistenknöpfe). Nur Farbe an der Stelle, keine Kachel. Chips (Zwischenablage, Screenshot,
  Befehle) bleiben unverändert. Die Originalfarbe liegt im Tag `agent_rgb_original`; hat die
  Leiste die Textfarbe inzwischen selbst geändert, wird diese das neue Original. Leistenknöpfe
  mit Tönungsliste des Designs (`TOOL_BAR_KEY`) bekommen eine abgeleitete `ColorStateList`
  (`RgbStrip.litTint`): nur „aktiviert“ leuchtet (P64). Knöpfe mit View-Farbfilter
  (Ausklapp-Taste) bekommen das Licht als Filter; Knöpfe mit eigenem Filter auf der Drawable
  (runder Knopf im flachen Stil) bleiben unverändert. Werden die Leistenbereiche abgewählt,
  während Tasten weiter leuchten, stellt das nächste Laden der Einstellung die Leiste zurück.
- Kosten je Bild (Kern-Messung `RgbPatternsTest.oneFrameOfEachPatternFitsTheBudget`, JVM des
  Testrechners, 1634 Werte = Raster 48 × 32 plus 45 Tasten doppelt plus Leiste): Wellen
  0,2–0,3 ms, Rauten/Lamellen/LED 0,35–0,4 ms, Raster 0,46 ms, Höhenlinien 0,67 ms, Tinte
  1,0 ms; zwei verschiedene Raster (Tasten und Hintergrund mit eigenem Look) kosten das Doppelte.
  Dazu je Taste bis zu vier Zeichenaufrufe mit Shader, ein volles Neuzeichnen der Tastatur-View
  und das Hochladen des kleinen Rasters. GPU-Kosten und Akku auf einem Telefon sind nicht
  gemessen. Kein Frame-Callback zwischen den fälligen Bildern. RGB-Seite: die Uhr der Vorschau
  wird nur beim Zeichnen gelesen (`PreviewLight.timeMs` ist eine Funktion), Bilder zeichnen also
  nur die Vorschau neu. Messung auf dem UI-Thread (`RgbLightingRenderTest.uiThreadLightingWorkPerFrame`,
  JVM mit Robolectric-Software-Zeichnen, Bilder ohne Pause): Vorbereitung je Bild vorher
  0,15–0,9 ms (Tinte 0,9), jetzt 0,03–0,2 ms; das Zeichnen des Lichts durch die Tastenmasken
  bleibt auf dem UI-Thread und kostet hier 2,5–6 ms, Lamellen und Raster (gedrehte Kachel)
  17–18 ms, das ganze Zeichnen der Tastatur 7–45 ms. Robolectric rastert in Software; am
  Telefon zeichnet die GPU, dort zeigt `RgbLighting.frameWork()` die echten Werte.
- Datenschutz: liest keinen Text, speichert nur die Einstellung; Tipp-Positionen (höchstens
  acht) nur im Speicher, beim Ausblenden verworfen. Keine Logs.
- Robustheit: `RgbEngine.aspect` ignoriert NaN/Unendlich und begrenzt auf 0,01–100,
  `light`, `paletteAt`, `RgbColors.hsv`, `lerp` und `RgbCurve.value` werfen bei NaN nicht;
  `RgbCurve.parse`/`of` lehnen kaputte Kurven ab (dann alter Verlauf oder Vorgabe); die
  Designvorschau zeichnet ohne Breite oder Höhe nichts.
- RGB-Seite, Ziehen ohne Ruckeln (Rückmeldung zu Build 14): Zwischenstände einer gezogenen Kurve
  (`commit = false`) landen in `draft`, das nur `LivePreview` liest: Die Seite wird beim Ziehen
  nicht neu zusammengesetzt, die Vorschau höchstens einmal je Bild (Compose fasst Änderungen
  eines Bildes zusammen), und der Editor zeichnet den gezogenen Punkt aus seinem eigenen Zustand
  (`live`, nur beim Zeichnen gelesen). Die Vorschau behält ihre zwei `RgbTexture`s über neue
  Engines hinweg (kein leeres Bild bei jeder Änderung). P109.
- RGB-Seite: `K/settings/screens/AgentRgbScreen.kt` (29a: große Live-Vorschau oben mit runden
  Ecken, darunter flache Karten je Gruppe, Muster als Bildergalerie, „Gleiches Muster überall“
  mit Wahl des Elements, Kurven-Editor mit Vorlagen und Stufen-Regler; Änderungen gehen in der
  Vorschau mit derselben Phase weiter, `RgbEngines.continueFrom`) und
  `K/settings/screens/AgentRgbCurveEditor.kt` (Achsen, Kurve, Punkte als Kreise mit Kreuz;
  Ziehen verschiebt, Tippen auf freie Fläche fügt hinzu, Tippen oder langes Drücken auf einen
  Punkt wählt ihn, entfernt wird über „Punkt entfernen“ oder Hinausziehen, nie durch langes
  Drücken; TalkBack: eigene Aktionen hoch, runter, links, rechts (Schritt 0,05, Enden nur hoch und
  runter) und entfernen für den gewählten Punkt; der gewählte Punkt übersteht Drehen
  (`rememberSaveable`); Zwischenstände nur in der Vorschau, gespeichert beim Loslassen; eine
  Geste auf einem Punkt gehört dem Editor, nicht dem Scrollen, P92). Galerie (Prüfung 29): Bilder
  hängen nur an Muster und Farben (`GalleryKey`: Muster, Regenbogen, Farben, Sättigung,
  Helligkeit, Deckkraft, Richtung des gewählten Looks; nicht Kurve, Dauer, Wellenbreite), werden
  über `produceState` auf `Dispatchers.Default` gemacht (nach der ersten Runde erst, wenn sich die
  Farben 250 ms nicht geändert haben) und für die Seite gemerkt; bis dahin bleibt das alte Bild.
- Bildwiederholrate (37, Skizze des Owners `docs/referenzen/2026-11-13-rgb-bildwiederholrate.jpg`):
  `K/settings/screens/AgentRgbFrameRate.kt` (`FrameRateCard`) ersetzt die Chips 30/60 als letzte
  Karte der RGB-Seite. Überschrift „Bildwiederholrate“ (`agent_rgb_fps`, Tag
  `agent_rgb_fps_heading`) fett, gesperrt in em, eine Zeile mit `TextAutoSize.StepBased` 12–21 sp
  (wo 21 sp nicht passen kleiner statt mitten im Wort umgebrochen; bei 360 dp und Schrift 130 %
  passen sie noch, weil Compose große Schrift nichtlinear skaliert, bei 200 % nicht), zwei Farbflächen
  untereinander (`FRAME_RATE_COLOURS`: 30 Hz gelb-orange, 60 Hz türkis-grün, Entscheidung des
  Owners 2026-11-14), Fläche `choiceSurface` mit Rahmen in `onSurface` über
  `surfaceContainerHigh` (heller und dunkler Theme), Zusatzzeile aus einem Format
  `agent_rgb_fps_line`, Radiobuttons in einer `selectableGroup`, Tags `agent_rgb_fps_30`/`_60`
  wie vorher die Chips. Schreibt wie bisher nur
  `fps` über `change` → `RgbLighting.write` (Einstellung `agent_rgb_lighting`, Vorgabe 30 = Eintrag
  entfernt). Darunter über den Platz `below` der Schalter „Im Energiesparmodus anhalten“ und der
  Hinweis. Licht: ein `rememberInfiniteTransition` (eine Runde 2,4 s ab `STILL_PHASE`, kein
  Sprung beim Anlaufen) für beide Flächen, gelesen nur in `drawBehind` (Bilder zeichnen die zwei
  Flächen neu, nichts wird zusammengesetzt); der Tropfen wandert hin und her (Kosinus), beide aus
  derselben Phase gleich schnell auf derselben Bahn (Flächen gleich breit), `lightPhase(phase,
  rate)` rastet sie auf ganze Bilder der Rate (72 bzw. 144 Stellen je Runde, 33 bzw. 17 ms),
  unabhängig von der Bildrate des Displays; Spur aus fünf blasseren Ovalen je ein 30-Hz-Bild
  (1/72) zurück, ein pulsierender Hof; auf der nicht gewählten Fläche mit `choiceDim` blasser;
  nur Ovale, keine Shader.
  Die Transition gibt es nur, solange die Flächen im Bild sind (`onGloballyPositioned`,
  `boundsInWindow` ist im Scroll-Container beschnitten) und Bewegung erlaubt ist
  (`FlatStyle.animationsEnabled` beim Öffnen, Energiesparmodus live über
  `ACTION_POWER_SAVE_MODE_CHANGED`, angemeldet am Application-Kontext, solange die Karte da ist,
  nach dem Anmelden noch einmal gelesen);
  sonst steht der Tropfen bei `STILL_PHASE`. Im Hintergrund pausiert Compose die Bilder ohnehin.
  Texte in `agent_rgb_strings.xml`. P198.
- Eigene Muster (34, Wunsch des Owners „vollständig customizable“; Annahmen in ROADMAP 34):
  Modell, Rechnung und JSON im Kern (`C/RgbOwnPattern.kt`, `C/RgbPatternJson.kt`). Ein Muster hat
  1–4 Ebenen, unten zuerst; jede Ebene: Form (`RgbGenerator`: Fläche, linearer, radialer,
  Kegelverlauf, Wellen, Plasma, Rauschen, Streifen, Ringe vom Tippen, Spot auf der letzten Taste,
  Höhenlinien, Funkeln), 2–8 Farbstopps (Tabelle mit 513 Einträgen, OKLCH wie 29b, Sättigung und
  Helligkeit des Looks auf jeden Stopp, rund laufend, wenn die Kurve rund läuft), Tempo in Schritten
  von 0,05 (P214), Richtung, Größe, Verschiebung, Deckkraft, Bewegungskurve aus 29d, Reaktion
  (`PULSE` stärkster Fade aller Tipps, `RIPPLE` Ring wie der Effekt aus 26b, `KEY_GLOW` weicher Fleck
  von 0,08 Breiten an der Tippstelle) und Farbkreis-Durchlauf (Farbdreh-Matrix wie CSS
  `hue-rotate`, eine Drehung je Durchlauf, je Bild einmal gerechnet). Gemischt nach W3C
  (`Cs' = (1 − ab)·Cs + ab·B(Cb, Cs)`, Normal, Addieren, Multiplizieren, Screen); das Alpha des
  Ergebnisses ist die Stärke des Lichts wie bei den eingebauten Mustern. Die Formen laufen wie die
  Wellen „vorwärts“ (`u = t − Ort`), dadurch ist der Ausgangspunkt aus Welle waagerecht/senkrecht
  und Kreis mit eigenen Farben bis auf 3/255 dasselbe Licht; die Texturen sind Annäherungen aus
  Ebenen (`RgbOwnPattern.from`). Alle Formen sind reine Funktionen von Ort, Phase, Uhr und den
  höchstens acht Tipps der Engine, ganze Durchläufe wiederholen sich genau.
- Eigene Muster im Ablauf: `RgbLook.own`/`RgbSettings.own` (je Element oder gemeinsam) ersetzt
  `pattern`, das als Rückfall bleibt; der Text der Einstellung nennt nur ID und Inhaltsprüfung
  (`own=<id>.<prüfung>`, P217, P222), `RgbLighting.read` löst sie über `A/RgbOwnPatterns.kt`
  (Einstellung `agent_rgb_patterns`) mit `RgbSettings.resolve` auf, `RgbLighting.load` liest neu,
  wenn sich eine der beiden Einstellungen ändert, und ruft bei geänderter Liste vorher
  `RgbOwnPatterns.migrate` (alte IDs `p1` … werden zufällig). `RgbOwnPatterns.write` ist der einzige
  Schreibweg der Liste: Er lässt Beleuchtung und gespeichertes Designprofil
  (`KeyboardProfileStore.updateSavedRgb`) über `RgbSettings.restamp` mitziehen und schreibt nichts,
  solange die gespeicherte Liste unlesbar ist (`locked`, `RgbPatternJson.readLibrary` gibt null). `RgbEngine` baut
  daraus einen `RgbOwnRenderer` (null für eine bloße Referenz); `base(x, y, nowMs)` fragt ihn,
  `effects` (Mischung, Tipp-Effekte der Seite) bleibt gleich. Kein Detail-Bild: `tilePattern` ist
  null, `RgbTexture.updateTile` und `RgbTiles.request` überspringen eigene Muster. `RgbTexture`
  rechnet das Raster wie bisher im Hintergrund, jetzt mit `base(x, y, nowMs)` (P216);
  `hasActiveEffect` hält Raster nach, solange ein Spot gleitet (180 ms) oder eine Reaktion
  verblasst. `RgbSettings.reactsToTaps` lässt Tipps zur Engine durch, wenn ein leuchtendes eigenes
  Muster sie braucht, auch ohne Tipp-Reaktion der Seite; `moves` folgt `RgbLook.moves`. Keine Tipps
  in Passwort-/PIN-Feldern und im Inkognito-Modus, neue `SettingsValues` (anderes Feld) verwerfen
  die alten (`RgbLighting.privateField`, `forgetTapsOfAnotherField`, P223).
- Eigene Muster, Seite: `K/settings/screens/AgentRgbScreen.kt` zeigt unter der Galerie „Eigene
  Muster“ (`OwnGallery`, Bilder 32 × 18 im Hintergrund, Schlüssel ohne Namen, nach dem ersten Bild
  erst nach `GALLERY_DEBOUNCE_MS` Ruhe, abbrechbar je Zeile, Zähler `ownPictures`; Tags
  `agent_rgb_own_<id>`), „Eigenes Muster aus …“ (`agent_rgb_own_new`, aus dem gewählten Look
  „… (eigen)“ oder als Kopie des eigenen „… (Kopie)“, `RgbOwnPattern.fitName` kürzt auf 32
  Zeichen; Rückfallname übersetzt über `agent_rgb_own_default_name`), „Bearbeiten“ und
  „Importieren“; ein eingebautes Muster wählen setzt `own` zurück. Unlesbare gespeicherte Liste:
  Hinweis `agent_rgb_own_locked` statt Galerie und Knöpfen. Farbkreise der Seite und Farbstopps
  des Editors: `ColorSwatch` (Kreis 36 dp in 48 dp Tippfläche, Name „Farbe n, #RRGGBB“ bzw.
  „Farbstopp n, #RRGGBB“). Mit eigenem Muster sind
  Kurve, Farben und Wellenbreite der Seite ausgeblendet. Der Editor
  (`K/settings/screens/AgentRgbPatternEditor.kt`, `RgbPatternEditor`) steht an Stelle der Seite
  (`editing`, Zurück über `BackHandler`), mit derselben `LivePreview` (Einstellungen der Seite mit
  dem Muster für alle Elemente, `previewWith`, echte Bildrate) und den Bausteinen der Seite
  (`RgbSection`, `RgbChips`, `RgbNote`, `RgbLabel` jetzt `internal`), Farbwähler
  `ColorPickerDialog`, Kurven-Editor `RgbCurveEditor`. Regler (`DraftSlider`, TalkBack-Name =
  Beschriftung mit Wert; Tempo und Größe in Schritten von 0,05, Verschiebung 0,01) und
  Kurvenpunkte ändern beim Ziehen nur einen Entwurf, den nur die Vorschau liest; gespeichert wird
  beim Loslassen (P218), ein verschobener Farbstopp bleibt nach dem Sortieren gewählt
  (`stopAfterSort`). Der Name bleibt beim Tippen im Feld und wird bei „Fertig“, Fokusverlust,
  Zurück, Duplizieren und Exportieren gespeichert (`onRename` gibt den gespeicherten Namen
  zurück). „Mischen“ fehlt bei Ebene 1. Dateien über `AgentDocumentIO` (nur `content://`, Lesegrenze 16 KiB) mit
  `CreateDocument`/`OpenDocument`; Import liest selbst (`openInputStream` null oder `IOException`:
  „nicht lesbar“, `SecurityException`/`FileNotFoundException`: „kein Zugriff“, Grenze überschritten:
  „zu groß“); Meldungen als Toast auf dem Main-Thread. Diagnose: `Diagnose.lightPatterns`
  (benutzte Muster, eigene nur als „eigenes Muster n“ nach ihrem Platz in der Liste, nie mit dem
  Namen des Nutzers, und ein Raster 48 × 32 je Engine gemessen, im Hintergrund beim Öffnen) hängt
  an „RGB-Beleuchtung“.
- Kosten eigener Muster (`CT/RgbOwnPatternTest.ownPatternsStayWithinTheBudget`, dieselben 1634 Werte
  je Bild wie oben, Testrechner, bestes von drei Läufen, zwei Läufe): Tinte 0,68 ms, eine lineare
  Ebene 0,20–0,22 ms, Plasma + Funkeln + Ringe + Spot 0,64–0,67 ms, vier Höhenlinien mit Farbkreis
  0,84–0,89 ms, vier Rauschen 1,04–1,11 ms (Grenze im Test: höchstens doppelt so viel wie Tinte im selben Lauf und unter 4 ms).
  `sample` alloziert nichts (gemessen mit `ThreadMXBean`, unter 64 KiB auf 100 000 Werte über alle
  zwölf Formen, alle Mischarten, Reaktionen und Farbmodi mit verblassenden Tipps).
  `RgbPaletteTables` hält 64 Tabellen (eigene Muster bringen bis zu vier je Muster).
- Tests: `CT/RgbLightingTest.kt`, `CT/RgbCurveTest.kt` (Kurve, Grenzen, Text, Vorlagen,
  Migration, eigene Looks), `CT/RgbPatternsTest.kt` (Bewegung, keine Sprünge in Ort und Zeit,
  Regenbogen ohne Kanten, OKLCH, Merkmale der sechs Muster, Kacheln nahtlos, Matrix, Kosten,
  Wiederholung des Flusses, kein Sprung am Umbruch von Fluss und Phase,
  Bilder unter `agent-core/build/rgb-previews/`), `CT/RgbFrameStatsTest.kt` (Selbstmessung,
  Kopie der Engine für das Hintergrund-Raster, geteilte Palettentabellen), `AT/RgbLightingRenderTest.kt` (echte
  `MainKeyboardView` im flachen Stil: aus = gleiche Pixel, einfarbige Tasten wie 26, weiches
  Licht gegen das gerechnete Raster, Texturen auf Tasten und Hintergrund, eigene Looks,
  Migration, Bereiche, Lesbarkeit, Blitz, Animationen aus, Leiste, nur die Haupttastatur,
  Energiesparmodus, Designprofil, einfarbige Tasten ohne Raster), `AT/RgbSettingsScreenTest.kt`
  (Seite, Kurve per Touch, TalkBack-Aktionen, eigene Looks, bewegte Vorschau der neuen Muster und
  weiche Vorschau gegen das bilineare Raster mit SDK 36, Galerie ohne Neubau beim Ziehen),
  `AT/RgbTilesTest.kt` (Kachel im Hintergrund, Zeichnen wartet nicht, Cache mit allen Mustern,
  Leeren), `CT/RgbOwnPatternTest.kt` (34: Goldwerte je Form, W3C-Formeln je Mischart, Kurve,
  Stopp-Tabellen, Farbkreis, Reaktionen als reine Funktionen der Tipps, Wiederholung über ganze
  Durchläufe und den Phasenumbruch, Begrenzung, ID und Prüfung in der Einstellung, Rückfall bei
  anderem Inhalt, zufällige IDs auch nach Löschen des neuesten, Umstellung alter IDs mit
  `restamp`, Namen kürzen, Trenner und Surrogate, Ausgangspunkte (diagonal auf quadratischer
  Fläche), Bibliothek, Kosten, keine Allokation; Bericht
  `agent-core/build/rgb-previews/own-pattern-cost.txt`), `CT/RgbPatternJsonTest.kt` (Rundreise,
  Vorgaben und Begrenzung auch für `1e300`, Ablehnungen mit Grund, `\u` genau vier Hexziffern,
  Namen mit Trennern, Prüfung nur über die Ebenen, Liste überspringt kaputte Einträge, unlesbare
  Liste ist null, strenger JSON-Leser), `AT/RgbPatternEditorTest.kt` (anlegen aus dem gewählten
  Muster, jede Option, Einstellung folgt, Farbstopp 48 dp mit Namen und gewählt nach dem Sortieren,
  feine Regler mit TalkBack-Namen, Mischen erst ab Ebene 2, Name erst bei „Fertig“, duplizieren als
  „(Kopie)“, löschen mit Rückfall; Galerie ohne Bild beim Umbenennen und erst nach Ruhe; Wahl je
  Element; laufende Vorschau mit 60 Hz und Bild `app/build/agent-previews/rgb-pattern-editor.png`;
  Export und Import über ein Test-Register für Dateiwähler, Ablehnung mit Grund, kein Zugriff,
  nicht lesbar; Raster ohne Kachel, Neuladen bei geänderter Liste, Rückfall; alte IDs umgestellt mit
  Einstellung und Designprofil, Profil von einem anderen Telefon; unlesbare Liste bleibt; Diagnose
  ohne Namen), `AT/RgbLightingRenderTest.privateFieldsFeedNoTapsAndANewFieldDropsThem` (Passwort,
  PIN, Inkognito, neues Feld), `AT/RgbFrameRateCardTest.kt` (37: Karte mit Überschrift darin, Texte und Reihenfolge,
  echte Einstellung, Auswahl folgt, Energiesparschalter bleibt, 44 dp, Überschrift eine Zeile bei
  390 dp in 21 sp, bei 360 dp mit Schrift 130 % ganz, mit 200 % kleiner (Breite gegen
  `TextMeasurer`, das Layout-Ergebnis nennt die Größe des Stils, nicht die Auto-Größe), eine Uhr,
  gleiches Tempo,
  30 Hz in Schritten von 1/72 und nie zwei Bilder nacheinander, 60 Hz in Schritten von 1/144,
  nichts zusammengesetzt, still ohne Animationen, im Energiesparmodus und außer Sicht, Empfänger
  mit der Karte abgemeldet, Kontrast beider Flächen in hellem und dunklem Schema gerechnet und im
  Bild gemessen, Bild `app/build/agent-previews/rgb-framerate-card.png` mit Pixelprüfung, Tropfen
  übereinander). Bilder unter
  `app/build/reports/rgb-lighting/` und `app/build/agent-previews/`.

- Etappe 70 (A1, S13, A4): `AgentKeyboardFrame.updateOverlayAccessibility` (ruft jede Überlagerung
  bei `setVisibility`: Panel, Rad, Rechner, Zeichen-Kunst, neu auch `FlatMenuView`) meldet
  `RgbLighting.setOverlayOpen(…)`; `RgbLighting.update` rechnet dann `visible = false`, also
  `PAUSED` ohne Bildanforderung, und das Licht läuft nach dem Schließen der letzten Überlagerung
  weiter (P287). `onAttachedToWindow` meldet den Stand neu, `onDetachedFromWindow` setzt ihn zurück.
  `RgbKeyPainter` ruft alle Einstiege über `guarded(…)`: eine Ausnahme setzt `failed`, ab dann tut
  der Painter nichts, die Farben bleiben die des Designs, `RgbLighting.onPainterFailed` merkt nur
  den Klassennamen (`painterFailure`), schaltet das Licht für die Sitzung aus (Modus `OFF`,
  `painterFor` liefert null) und nimmt dem View den Painter erst nach dem laufenden Zeichnen
  (P288). Die Diagnose-Karte der Beleuchtung zeigt oben die Zeile „Licht für diese Sitzung nach
  einem Fehler ausgeschaltet“ mit dem Klassennamen (`Diagnose.lighting(…, failure)`), nie mit der
  Meldung. Farbfilter (`SRC_IN`) kommen aus `RgbFilterCache` (höchstens 64, voll = neu anfangen).
  Tests in `AT/RgbLightingRenderTest` (`noFramesWhileAnOverlayCoversTheKeysAndTheyRunAgainAfterwards`,
  `aPainterThatThrowsSwitchesTheLightOffForTheSessionAndShowsOnlyTheClass`,
  `colourFiltersAreCachedByColourAndBounded`).

## Diktat

> Diktieren über den On-device-Erkenner des Systems: zuhören, Vorschau, Einfügen erst nach eigenem Klick.

- Ablauf: Taste `DICTATE` oder Knopf „Diktieren“ im Panel → `openDictation` in
  `AgentEditorController.kt` → `DictationSession.start` prüft der Reihe nach Editor
  (`captureAnchor`: schlichte Schreibmarke, kein geschütztes Feld), Erkenner und
  Mikrofonrecht; erst danach läuft das Mikrofon → „Fertig“ → Vorschau → eigener Klick
  „An Schreibmarke einfügen“ → `AndroidEditorAdapter.insert` → `InputLogic.insertGeneratedText`.
- `Dictation.kt`: `SpeechEngine` (Nahtstelle zum Erkenner), `DictationError`,
  `DictationSession` (Zustandsautomat auf dem Main-Thread, ohne Android-Abhängigkeit; der Text
  liegt nur im Speicher; jede Änderung von Editor, Schreibmarke oder Schutzzustand verwirft ihn;
  späte Rückrufe einer älteren Erkennung werden über eine Epoche ignoriert).
- `OnDeviceSpeechEngine.kt`: einzige Implementierung, `SpeechRecognizer.createOnDeviceSpeechRecognizer`
  (Android 12+). Fehlt die On-device-Erkennung, ist das Diktat nicht verfügbar. Der allgemeine
  `SpeechRecognizer`, der laut Plattform-Doku Ton an Server streamen kann, wird nie erzeugt.
  Die Erkennung selbst macht der Systemdienst, nicht die App. Pro Diktat ein neuer Erkenner, der
  danach zerstört wird.
- `AgentPanelView.kt`: `showDictationListening` (Text direkt über „Fertig“ und „Abbrechen“,
  Bildschirm bleibt an), `showDictationProcessing`, `showDictationReview`,
  `showDictationStatus`. Textaktionen bleiben im Diktat aus; der Diktier-Knopf ist nur aktiv,
  wenn keine Auswahl angezeigt wird.
- Sperre und Inkognito lösen kein Editor-Ereignis aus: `DictationSession` prüft deshalb bei
  jedem Zwischenergebnis `isCurrent` und bricht sonst ab.
- `K/settings/screens/AgentDictationScreen.kt`: Erklärung, Verfügbarkeit und die einzige Stelle,
  an der das Mikrofonrecht angefragt wird (ein Tastaturdienst kann den Systemdialog nicht zeigen).
- Regeln: keine Aufnahme vor den drei Prüfungen; kein Einfügen ohne Klick; nie eine Auswahl
  ersetzen; diktierter Text wird wie erzeugter Text nicht gelernt; kein Journal, weil nichts das
  Gerät verlässt. Die vorhandene `VOICE`-Taste (Wechsel zu einer System-Sprachtastatur) ist
  davon unabhängig.
- Tests in `AT/`: `DictationSessionTest` (reiner JVM-Test), `DictationUiTest` (Panel, Fehlercodes,
  Taste). Der echte Erkenner und das Mikrofon sind nicht getestet (kein Gerät mit
  On-device-Erkennung).

## Lerndaten

> Was die Tastatur aus dem Tippen lernt: pausieren, zählen, exportieren, löschen. Alles auf dem Gerät.

- `LearningData.kt`: `LearningData` (`blocksLearning` ist die eine Regel, die die Engine vor dem
  Speichern fragt; `isPaused`/`setPaused`; `counts`; `export` als lesbares JSON, Format
  `agent-keyboard-learning` Version 1, höchstens 1 MiB, sonst Abbruch statt Kürzung),
  `LearnedWord`, `LearningSource`, `UserHistoryLearningSource` (arbeitet über
  `PersonalizationHelper` auf denselben Wörterbuch-Objekten wie die laufende Tastatur; Dateien
  „UserHistoryDictionary.<Sprache>.dict“ im DE-Speicher der Engine).
- Lesen und Löschen sind „ganz oder Fehler“: `words` wirft, wenn ein Wörterbuch nicht innerhalb
  von 10 s gelesen werden kann; `deleteAll` leert jedes Wörterbuch an Ort und Stelle und wartet
  darauf. Dafür gibt es in `ExpandableBinaryDictionary.java` `readAllWordProperties` (liefert
  `null` statt eines leeren Feldes bei Zeitüberschreitung, siehe Fallstrick P21) und
  `clearAndWait`.
- `K/settings/screens/AgentLearningScreen.kt`: Einstellungsseite „Gelernte Wörter“. Der
  Exportinhalt liegt nur im Speicher, nie im Saved State. Wird die Seite neu aufgebaut, während
  der Dateidialog offen ist, entsteht kein leerer „Export“: Die leere Datei wird entfernt und
  ein Fehler gemeldet.
- Zwei verschiedene Schalter: „Personalisierte Vorschläge“ (Upstream, Textkorrektur) schaltet
  das Lernen ab und löscht dabei alles Gelernte. „Lernen pausieren“ behält das Gelernte; Tippen
  ändert dann keine gespeicherten Daten: nichts kommt in die Tipp-Historie oder ins persönliche
  Wörterbuch, und ein getipptes Wort wird auch nicht von der Sperrliste genommen.
- Die Sprach-Konfidenz für mehrsprachiges Tippen wird auch bei Pause angepasst; sie liegt nur im
  Arbeitsspeicher.
- Tests: `LearningDataTest` (Pause erreicht die Engine-Einstellungen, Exportformat, Abbruch bei
  Übergröße und bei unlesbarer Sprache, nur echte Historien-Dateien). Nicht durch Tests belegt,
  weil sie die native Engine brauchen: dass bei Pause kein Wort in der Historie landet, das
  Lesen echter Lerndaten und das Leeren.
- Autokorrektur (20a): `K/agent/CorrectionMemory.kt`. `CorrectionGuard.allowsAutoCorrection`
  blockiert nur, korrigiert nie selbst: gesperrtes Paar, dann (nur wenn das getippte Wort im
  Wörterbuch steht) „richtige Wörter nie ändern“ oder ein reiner Umlaut-Unterschied (NFD,
  U+0308 entfernt). `RejectedCorrections` hält die Paare kleingeschrieben in
  `agent_correction_memory` (gerätegeschützter Speicher wie die Lernwörterbücher, siehe
  `docs/STORAGE.md`), höchstens 500, älteste zuerst verworfen, im Speicher als Menge für die
  Abfrage aus dem Vorschlags-Thread. Gespeichert wird nur mit Schalter an, ohne Lernpause,
  ohne Inkognito und bei entsperrtem Gerät; nur gemeinsam mit Rückgängig per Löschtaste.
  Einstellungen: Abschnitt „Autokorrektur“ in `AgentLearningScreen.kt`. Tests:
  `CorrectionMemoryTest`, Fälle „stage 20a“ in `SuggestTest` und `InputLogicTest`.
- Eigene Tippfehler (20b): `K/agent/TypoMemory.kt`, Datei `agent_typo_memory` (gerätegeschützt).
  Je Tastatursprache (seit 51) und getippter Form die gemeinten Wörter mit Gesamtzahl und Zahl pro vorherigem Wort (höchstens
  20). Höchstens 1000 getippte Formen, zuletzt benutzte bleiben. Angewendet ab drei
  Bestätigungen und 80 % aller Bestätigungen dieser Form; eine Form, die selbst Wort eines
  Hauptwörterbuchs ist, nur über den Kontext. Nicht gezählt: Vervollständigungen (gemeintes Wort
  beginnt mit dem getippten), Formen mit Ziffern, unter zwei Zeichen, nur andere Großschreibung.
  Tests: `TypoMemoryTest`, Fälle „stage 20b“ in `SuggestTest` und `InputLogicTest`.
- Getipptes Wort vorne (48): `K/agent/TypedWordFirst.kt` (nur im Speicher, je Feld, höchstens 32
  Korrekturen, kein Lernspeicher), `K/agent/Abbreviations.kt` (feste Listen, keine Lerndaten); `K/agent/CorrectionGuards.kt` (Etappe 91: Veto gegen das stille Ersetzen von Abkürzungen in Großbuchstaben und von Wörtern, deren deutsche Vorsilbe die Korrektur austauschen würde; nach AdaptKey D-404/D-354, aufgerufen in `Suggest.shouldBeAutoCorrected`; Test `AT/CorrectionGuardsTest.kt`);
  Einbindung im Abschnitt „Geänderte Upstream-Dateien“ → Autokorrektur. Tests:
  `TypedWordFirstTest`, Fälle „stage 48“ in `SuggestTest` und `InputLogicTest`.
- Korrekturen im ganzen Feld (49): Regeln rein in `C/CorrectionRules.kt` (`diff`: genau ein Wort
  geändert, Rest gleich oder bei einem gerade abgeschickten Wort am Feldende gelöscht; `classify`:
  TYPO nur bei Nicht-Wort → echtem Wort mit 1–2 Vertipp-Schritten, bis 4 Buchstaben 1;
  `KeyAdjacency` aus den Tasten der letzten Buchstaben-Tastatur, auch während Symbole oder Ziffern
  zu sehen sind, ohne Tastatur QWERTZ/QWERTY nach Sprache, Nachprüfung 49 N2).
  `K/agent/FieldCorrections.kt`: `FieldCorrectionWatcher` (je `InputLogic`, Feldtext nur im
  Speicher, bis 1024 Zeichen vor und 300 nach der Schreibmarke (P184), nur Wörter, die eigene
  Eingabe berührt hat (P183), keine Wortvervollständigung (`isCompletion`), „echt“ = `isValidSpellingWord` auch klein,
  also Wörterbuch, persönliches Wörterbuch, Kontakte, Lernverlauf) und `FieldCorrectionMemory`
  (Datei `agent_field_corrections`, je Tastatursprache seit 51, Kandidat → gelernt beim zweiten
  Vorkommen mit anderer Feldnummer oder Stelle). Wirkung über `TypoMemory.meantWord` (gelerntes Paar nur, wenn das
  Getippte kein Hauptwörterbuch-Wort ist; danach wie 20b über `Suggest.applyLearnedCorrection`),
  `TypoMemory.forget` löscht mit. Zurückgenommene Autokorrektur → `RejectedCorrections.remember`.
  Seite `K/settings/screens/AgentMemoryScreen.kt` („Was ich mir gemerkt habe“, eine Liste von
  Abschnitten, 46 ergänzt seinen; Ziel `agent_memory`, Zeile in `AgentAreaTypingScreen.kt` und
  Eintrag in `SettingsAreas`), Texte `values{,-de}/agent_memory_strings.xml`. Tests:
  `CT/CorrectionRulesTest.kt`, `AT/FieldCorrectionMemoryTest.kt` (Speicher und Seite), Fälle
  „stage 49“ in `InputLogicTest`.
- Trefferzonen (22a): `K/agent/TouchModel.kt` hält pro Buchstabe (Code unter U+0250) den
  gleitenden Mittelwert der Abweichung vom Tastenmittelpunkt in Tastenbreiten und -höhen und die
  Anzahl, Datei `agent_touch_model` (gerätegeschützt). Wirksam erst ab 15 Treffern, gedämpft und
  auf ein Drittel Taste begrenzt. `adjust` wählt unter den Buchstabentasten, deren verschobene
  Trefferfläche die Berührung enthält, die mit dem nächsten verschobenen Mittelpunkt; haben beide
  Kandidaten nichts gelernt, gilt die normale Regel (Flächen überlappen um ein Pixel).
  `K/agent/TouchCalibration.kt` sammelt im Speicher Berührungen samt Tastengeometrie, gleicht sie
  mit dem Zieltext ab (Editierdistanz, Nachbartasten als günstigere Verwechslung, das nicht
  getippte Ende kostet nichts) und liefert Mittelwerte ab vier Treffern; ändert sich die
  Tastatur, beginnt die Sammlung neu. `learnFromWord` (22b) ordnet jede Berührung dem Buchstaben
  an derselben Stelle des abgeschickten Worts zu (gleiche Länge, höchstens zwei Unterschiede) und
  speichert alle zehn Wörter. Der Wörterbuch-Decoder bekommt weiter die rohen Koordinaten und
  rechnet mit den normalen Tastenpositionen. Oberfläche: Abschnitt „Trefferzonen“ in `AgentLearningScreen.kt`.
  Tests: `TouchModelTest`.
- Dynamische Trefferzonen (50, Schalter `AgentPreferences.ADAPT_TOUCH_DYNAMIC`, Standard aus, wirkt
  nur zusammen mit „Trefferzonen an mich anpassen“). Drei Schichten:
  - Basis: die Werte aus 22a (Kalibrierung, Lernen von vor 50). Mit dem Schalter lernt sie nicht
    mehr aus Wörtern („bleibt wie kalibriert“), nur über eine Beförderung (unten).
  - Kurzzeit: `C/TouchDynamics.kt` hält Residuen (Abweichung vom Mittelpunkt der gemeinten Taste
    minus Basis) als gleitende Mittel: global (Gewicht 0,1, nach 30 Tipps rund 95 %) und je
    Region (3 Reihen × 3 Spalten der Buchstaben, Gewicht 0,2, zählt halb ab 8 Tipps der Region).
    Ein Tipp bewegt die Schicht höchstens 0,03 Taste; Schicht allein und Summe mit der Basis je
    höchstens ein Drittel Taste. Nie gespeichert. Ende der Sitzung (Schicht auf null): Pause über
    5 Minuten, andere App, eingeschränktes Feld; ein anderes Feld derselben App halbiert sie.
  - Szenen: Am Sitzungsende (ab 40 Tipps) gilt eine Abweichung als anhaltend bei mittlerer
    Verschiebung ≥ 0,12 Taste, ≥ 3 Standardfehler und ≥ 75 % der 20er-Blöcke in dieselbe Richtung,
    oder Streuung ≥ 1,3 × und +0,04 über dem Bezug, oder Korrekturrate (Löschtasten je Buchstabe)
    ≥ 1,5 × und +0,04 über dem Bezug. Bezug = gleitendes Mittel unauffälliger Sitzungen. Eine
    Szene hält Region-Mittel, Verschiebung, Streuung, Tempo (Buchstaben pro Minute aus Abständen
    unter 2 s), Korrekturrate, Tageszeit (morgens 5–11, tagsüber 11–17, abends 17–22, nachts) und
    Tag; höchstens 8, ähnliche werden gemittelt. Nach 15 Tipps einer neuen Sitzung: liegt eine
    Szene nah genug (gewichteter Abstand der Merkmale, Tageszeit als Zuschlag ≤ 4), startet die
    Kurzzeitschicht dort.
  - Beförderung: nur verschobene Sitzungen zählen (`Observation`, 30 Tage, höchstens 60). Liegen
    ≥ 5 davon innerhalb 0,08 Taste beieinander, an ≥ 3 Tagen und in ≥ 2 Tageszeiten („nicht nur in
    einer Szene“), wandert die Basis jeder Region um die Hälfte, höchstens 0,06 Taste je Achse
    (`MAX_PROMOTION`); die Szenen werden um denselben Schritt zurückgerechnet. Vorher sichert
    `TouchModel` die Basis (`previous`, auch vor jeder Kalibrierung), „Auf vorherige Kalibrierung
    zurücksetzen“ stellt sie wieder her (ein Schritt; der Knopf erscheint nur, wenn `previous`
    existiert). Befördert werden nur trainierte Buchstaben (Basis ab 15 Treffern oder 15 bestätigte
    Kurzzeit-Berührungen seit Prozessstart, `dynamicTouches`, nie gespeichert); nie getippte
    Buchstaben behalten Basis und Anzahl. Die übrigen Beobachtungen werden wie die Szenen
    zurückgerechnet. Weil nur Abweichungen ab 0,12 Taste beobachtet werden, nähert sich die Basis
    einer dauerhaften Abweichung nur bis auf etwa 0,12 Taste, den Rest trägt die Kurzzeitschicht.
  - Kalibrierung (`replace`) und „Auf vorherige Kalibrierung zurücksetzen“ (`restorePrevious`)
    verschieben die Basis außerhalb der Beförderung: `rebaseScenes` rechnet die Szenen je Region um
    die mittlere Änderung der angewandten Basis zurück (`TouchDynamics.rebase`), leert die
    Beobachtungen und verwirft die laufende Sitzung (P181).
  - Ausgeschaltet („Dynamisch anpassen“ oder „Trefferzonen an mich anpassen“ aus, auch über die
    Gesten-Seite, die `TouchModel.dynamic` mitsetzt): der nächste Feldstart verwirft die Sitzung
    ungesehen (`discardSession`), damit sich die Basis nicht mehr ändert (genau 22a/22b).
  - Tastaturgeometrie: `updateRegions` merkt sich Code, Lage und Größe jeder Buchstabentaste.
    Weicht sie ab (Drehung, Einhand, geteilt, anderes Layout), endet die Sitzung und die Regionen
    entstehen neu; normale und Umschalt-Tastatur haben dieselbe Geometrie, Tastaturen mit weniger
    als 10 Buchstaben (Symbole) zählen nicht. Geprüft beim Lernen eines Worts und bei jeder Taste
    (`onKeyInput` bekommt `keyboardSwitcher.keyboard`). Die Ausrichtung ist kein Szenemerkmal:
    eine Szene entsteht immer in einer Geometrie, gespeichert wird sie ohne (P182).
  - Gegen Rückkopplung: Lernen nur aus Wörtern, die `InputLogic` schon heute an `learnFromWord`
    gibt (getippt wie abgeschickt oder aus der Leiste gewählt, keine Autokorrektur); die Berührungen
    warten auf die nächste Taste (`onKeyInput`): Löschtaste, Rückgängig (`UNDO`), Ausschneiden
    (`CLIPBOARD_CUT`) oder Feldwechsel verwirft sie; eine gehaltene Löschtaste (`isKeyRepeat`)
    zählt nur einmal als Korrektur. Ein Versetzen der Schreibmarke ohne neues Feld sieht der Hook
    nicht, dann entscheidet die nächste Taste (bewusst, kein Eingriff in die Engine). Nur
    Berührungen innerhalb der normalen Taste (`IN_KEY` = 0,5) zählen, so treibt eine verschobene
    Zone sich nicht selbst weiter (P166). Nie in Passwortfeldern, Inkognito, Feldern ohne Lernen,
    bei pausiertem Lernen, gesperrt oder im Kalibrierfeld; dort gilt auch keine Kurzzeitschicht.
  - Oberfläche: Abschnitt „Trefferzonen“ in `AgentLearningScreen.kt` (Schalter, Szenen in Worten
    über `describeScene`, „Szene löschen“, „Auf vorherige Kalibrierung zurücksetzen“), Texte in
    `agent_touch_strings.xml` (`values/`, `values-de/`). Tests: `CT/TouchDynamicsTest.kt`
    (Modell), `AT/TouchZonesDynamicTest.kt` (Hook, Bestätigung, gesperrte Felder, Beförderung),
    `AT/TouchScenesScreenTest.kt` (Seite).
- Sprache der Lerndaten (51, P192): Was eine Korrektur hinzufügt, gehört zur Tastatursprache, in
  der es gelernt wurde (`SettingsValues.mLocale.language`, `ForeignWords.languageOf`):
  `TypoMemory` (20b) und `FieldCorrectionMemory` (49) schlüsseln nach Sprache und getippter Form,
  `meantWord`/`learnedFor` fragen nur die aktuelle Sprache; Einträge von vor 51 haben `""` als
  Sprache und gelten nur, wo ein Wörterbuch der aktuellen Sprache das gemeinte Wort kennt (Rückruf
  `knownInLanguage`, in `Suggest` `isValidSuggestionWord`). `forget` (Rücknahme einer Korrektur)
  löscht das Paar in jeder Sprache, `FieldCorrectionMemory.delete` (Seite) nur den einen Eintrag.
  Ohne Sprache (`language = null`) fragen nur die Unit-Tests der Regeln. `TypoMemory.all` liefert
  `TypoMemory.Entry` mit Sprache. `FieldCorrectionWatcher` merkt sich die Tastatursprache des Felds;
  wechselt sie im selben Feld, lernt er dort keine Paare mehr (P208).
  Bewusst sprachneutral, weil
  sie nur verhindern oder nur auf Tipp anbieten: `RejectedCorrections` (ein abgelehntes Paar ist in
  jeder Sprache unerwünscht, das korrigierte Wort kommt ohnehin nur aus seiner Sprache), eigene
  Wörter, Wortfolgen und Abkürzungen des Gedächtnisses (Namen, Slang und Kürzel des Owners;
  Folgen passen nur nach denselben Wörtern), `TypedWordFirst` (nur im Speicher je Feld), die
  Kontext-Gewichte von 45. HeliBoards Lernverlauf und Sperrliste sind ohnehin je Sprache.
- Wörter aus anderen Sprachen (51, P193, P207): `A/ForeignWords.kt`. Ein Wort, das das Hauptwörterbuch
  der Tastatursprache nicht kennt (in keiner Schreibung, so bleibt 48 unberührt), wohl aber das
  Hauptwörterbuch einer anderen aktiven Sprache (jeder aktive Subtyp und dessen zweite Sprachen,
  `enabledLocales`, ohne die zweiten Sprachen des benutzten Subtyps, `mSecondaryLocales`: die deckt
  HeliBoards mehrsprachiges Tippen ab), wird für die Tastatursprache gemerkt, in der Schreibung jenes
  Wörterbuchs (getippt, klein oder mit großem Anfang, „haus“ → „Haus“): sofort, wenn der Owner es
  gegen eine automatische Korrektur behalten hat (`onCorrectionUndone` aus
  `RejectedCorrections.remember`, auch ohne deren Schalter; zählt beim nächsten Commit, wenn das Wort
  genau an der gemeldeten Stelle steht, wie P188, und dieser Commit zählt sein eigenes Wort
  zusätzlich; reine Groß-/Kleinänderungen nie), sonst beim zweiten Commit genau wie getippt an
  anderer Stelle (anderes Feld oder andere Position, `onFieldChanged` aus
  `AgentEditorController.startEditor`/`finishEditor`) und nur in Feldern mit Autokorrektur
  (`mAutoCorrectEnabled`). „Kennt die Tastatursprache nicht“ meldet der Vorschlags-Thread (`onTyped`,
  wie P176; als bekannt, wenn der Facilitator noch eine andere Hauptsprache hat oder kein
  Hauptwörterbuch geladen ist); die Prüfung auf `worker` (eigener Thread, jede Aufgabe in
  `runCatching`) fragt es für alle drei Wege noch einmal im Hauptwörterbuch der Tastatursprache
  nach. `Dictionaries` öffnet die Hauptwörterbücher erst bei Bedarf
  (`DictionaryFactory.createMainDictionaryCollection`, nur lesend wie zweite Sprachen), je Locale
  einmal: das der Tastatursprache und die der anderen Sprachen; sie bleiben über einen Wechsel der
  Tastatursprache offen, eine nicht mehr aktive Sprache wird bei der nächsten Prüfung geschlossen,
  alle mit `closeDictionaries` (`LatinIME.onTrimMemory` bei knappem Speicher und im Hintergrund,
  nach `IDLE_MILLIS` = 10 min ohne Prüfung beim nächsten Feldwechsel, `LatinIME.resetSuggestMainDict`
  nach einer Wörterbuchänderung, `AgentEditorController.destroy`, `clear`; P207). Mit „Anstößige Wörter sperren“
  (`mBlockPotentiallyOffensive`) zählt keine Form mit Häufigkeit 0 (wie HeliBoards Lernverlauf).
  Ersetzt der Owner ein gemerktes Wort (oder einen Kandidaten) durch ein anderes, ist es für die
  Tastatursprache vergessen (`onReplaced`: aus `TypoMemory.confirm`, also Vorschlag statt getipptem
  Wort, und aus `FieldCorrectionWatcher.checkpoint` für eine eigene Änderung im Feld; nicht bei
  Groß-/Kleinänderung, fertig geschriebenem Wort, Lernpause, gesperrtem Feld oder Gerät, und nach
  einem Sprachwechsel im Feld nicht über den Feldweg, P208). `forget` erhöht je Wort einen Zähler
  (`forgotten`): eine Prüfung, die davor geplant wurde, legt das Wort nicht wieder an. Gelernt
  wird nur mit „Personalisierte Vorschläge“ (`mUsePersonalizedDicts`), ohne Lernpause, nicht in
  gesperrten Feldern (`Memory.isBlocked`: Inkognito, Passwort, `NO_LEARNING_FIELD`, vom Gedächtnis
  ausgeschlossene Apps) und nicht bei gesperrtem Gerät; angeboten wird nur ungesperrt und nicht in
  gesperrten Feldern. Datei `agent_foreign_words` (gerätegeschützt, `docs/STORAGE.md`), höchstens
  500 gemerkte Wörter und 200 Kandidaten, älteste zuerst verworfen; im Speicher als Karte je Sprache
  (`lookup`) für den Vorschlags-Thread. Ein erneut benutztes gemerktes Wort bekommt seine Zeit nur
  im Speicher (`dirty`), geschrieben bei `onFieldChanged(context)`. `clear` aus „Gelernte Wörter
  löschen“ und dem bestätigten Ausschalten von „Personalisierte Vorschläge“ (`TextCorrectionScreen`)
  verwirft auch wartende Prüfungen (`generation`). Seite: Abschnitt `ForeignWordsSection` (dritter
  Eintrag von `memorySections` in `AgentMemoryScreen.kt`, „Wörter aus anderen Sprachen“, Wort mit
  Herkunfts- und Zielsprache, „Löschen“), Texte in `agent_memory_strings.xml`; Testmarken je
  Sprache (`memoryEntryTag`, z. B. `de_hola`), wie die Korrekturen von 49, die ihre Sprache zeigen
  (`agent_memory_pair_in`). Tests: Fälle „stage 51“ in `InputLogicTest` mit echtem Wörterbuch-Zugang
  (`latin/WordListDictionaries.kt`: `ShadowWordListDictionaries` ersetzt nur
  `DictionaryFactory.createMainDictionaryCollection` durch Wortlisten, `WordLists.offensive` meldet
  Häufigkeit 0, `WordLists.opened` mit Thread; Facilitator, Gruppen, Konfidenz, `Suggest` und
  `InputLogic` laufen echt) und in `SuggestTest` („fur“, fremde Hauptsprache), `TypoMemoryTest`,
  `FieldCorrectionMemoryTest`, `MemoryScreenTest`.
- Kontext-Lernen (45): `A/ContextLearning.kt` hält `ContextWeights` (nur Hinweis-`id`, Art und
  Zahl) in `agent_context_weights` (gerätegeschützt, Schlüssel `weights`), im Speicher
  zwischengespeichert. `record` nur bei `mayLearn` („Personalisierte Vorschläge“ an, keine
  Lernpause, kein Inkognito, nicht gesperrt), `weights` nur mit eingeschaltetem Schalter (sonst
  keine). „Gelernte Wörter löschen“ ruft `clear`, ebenso das bestätigte Ausschalten von
  „Personalisierte Vorschläge“ (`TextCorrectionScreen`, Nachprüfung 44/45).
- Gedächtnis (46, P174–P177): `A/Memory.kt` (Schalter `AgentPreferences.MEMORY`, Standard aus;
  `MEMORY_SNIPPETS`, Standard an). Hält `PhraseBook`, `OwnWordBook`, `ShortcutBook` unter einer Sperre,
  eigene Wörter und Kurzformen gemerkter Abkürzungen zusätzlich als Menge für den Vorschlags-Thread
  (`ownLookup`, erst bei Bedarf und nur nach einer Änderung neu gebaut); schreibt höchstens alle
  30 s beim Tippen und beim Verlassen des Felds (`AgentEditorController.startEditor`/`finishEditor` →
  `onFieldChanged`, das auch Lauf, `ReplacementTracker`, offene Rücknahme und gezählte Stellen leert).
  `save` kopiert die Folgen unter der Sperre und kodiert und schreibt sie auf einem eigenen Thread
  (`saver`); ein Zähler (`generation`), den `clear` erhöht, verwirft ältere wartende Schreibvorgänge
  (Nachprüfung 46 N1, gemessen: 2000 Folgen kopieren und kodieren rund 11 ms auf der JVM).
  Beim Verlassen des Felds (`finishEditor` → `onFieldChanged(finishing = true)`) schreibt `saveNow`
  auf dem Aufrufer-Thread und macht ältere wartende Schreibvorgänge ungültig (zweite Nachprüfung 46).
  Rest-Risiko: Beim Tippen liegt bis zu 30 s Gelerntes nur im Speicher; stirbt der Prozess in dieser
  Zeit, ohne dass das Feld verlassen wurde, geht es verloren (vorsichtig: nichts Falsches gespeichert).
  `resetForTest` schreibt sofort, `dropBackgroundWritesForTest` verwirft Schreibvorgänge auf `saver`. Datei `agent_phrase_memory`
  (gerätegeschützt, `docs/STORAGE.md`). `isBlocked`: keine Einstellungen, Inkognito (auch kein
  Lernen und Passwort), `IME_FLAG_NO_PERSONALIZED_LEARNING` oder `privateImeOptions` = `AgentPreferences.NO_LEARNING_FIELD`
  (direkt aus der `EditorInfo`, P180), gesperrt, kein Textfeld, Passwort,
  ausgeschlossene App (höchstens 50 Paketnamen); `mayLearn` zusätzlich Schalter, „Personalisierte
  Vorschläge“ (`mUsePersonalizedDicts`, zweite Nachprüfung 46), keine Lernpause und der Sperrzustand jetzt. `onWordCommitted` (Hook in `InputLogic`): der Lauf = längstes Ende der
  eigenen Commits, mit dem der Satzteil vor der Schreibmarke noch endet (P174), `learn`; eine Nutzung
  als eigenes Wort, wenn getippt = abgeschickt und das Hauptwörterbuch es laut `onTyped` nicht kennt
  (P176); `ReplacementTracker` für Abkürzungen, gezählt nur, wenn kein Hauptwörterbuch die
  Kurzform kannte (`unknownCommits`, aus `onTyped`, P187). `onCorrectionUndone` aus
  `RejectedCorrections.remember(…, at)` (auch ohne dessen Schalter) merkt die Rücknahme mit der Stelle
  des getippten Worts vor; gezählt wird sie beim nächsten Commit, wenn das Wort genau dort noch
  dasteht, je Wort und Stelle einmal (P188).
  `acceptOffer` merkt die Abkürzung nur (`CREATED`), `rememberedOf` für den Chip (Erweiterung und ob
  nur nach großer Kurzform aufgezeichnet); nichts geht ins
  persönliche Wörterbuch (P187). `nextWord` und `ownCompletion` fragen den Sperrzustand jetzt,
  bevor sie etwas zurückgeben (Nachprüfung 46 N4). `clear` (auch aus „Gelernte Wörter löschen“ in
  `AgentLearningScreen` und dem bestätigten Ausschalten von „Personalisierte Vorschläge“ in
  `TextCorrectionScreen`, Nachprüfung 46 H1) leert auch Lauf, `lastTyped` und
  `MemoryChips.forget` (das angezeigte Angebot). `A/MemoryChips.kt`: Chips nach `FieldSuggestionChips` in
  `KeyboardCommands.buildChips`, nur nach Leerraum (nicht nach Zeilenumbruch), nie gesperrt: der
  sichere Rest (`RESULT`, „… Rest“), die Erweiterung der gerade getippten gemerkten Abkürzung
  (`ACTION`, „vlt → vielleicht“; das letzte ganze Wort vor dem Leerraum, Schreibung nach
  `expansionCase` (P177); der Tipp ersetzt über `Host.replace` genau „Kurzform + Leerraum“ durch
  „Erweiterung + Leerraum“, P187), der passende Baustein („📋 Name: …“; mit Feldern nur, wenn
  `@snip` an ist, dann wird „@snip Name“ angehängt) und einmal das fällige Abkürzungs-Angebot
  (`TEMPLATE`, beim ersten Zeigen `SHOWN`, bleibt nur bei unveränderter Schreibmarke); mit einem davon
  dazu die üblichen Vorhersagen (`Host.predictions`, Tipp über `Host.pickPrediction` →
  `LatinIME.pickSuggestionManually`). Rest und Erweiterung stehen vor den Vorhersagen. Jeder Chip
  wirkt nur an der Schreibmarke, für die er gebaut wurde, einmal, und nur wenn der Text davor noch
  derselbe ist; Rest und Baustein über `Host.insertAtCursor(…, privateText = false)` (P177). Bausteinindex im Speicher, neu bei anderer `SnipCommand.source` oder `SnippetStore.version`.
  `AgentEditorController.suggestedWords` gibt den Chips die Vorschläge der Engine, `isKnownWord`
  (`Host.isKnownWord`) die Wörterbuchabfrage für die Schreibung. `showing` ist `@Volatile` (`forget`
  kommt auch von anderen Threads). Auf der Seite
  „Was ich mir gemerkt habe“ (`K/settings/screens/AgentMemoryScreen.kt`, aus 49) ist
  `MemorySections` der zweite Eintrag von `memorySections`, unter den Korrekturen: Überschrift
  „Gedächtnis“, Schalter, Wortfolgen ab 3 Wörtern (die 100 stärksten, Anpinnen/Löschen, Tags
  `MEMORY_PIN_TAG`/`MEMORY_PHRASE_DELETE_TAG` + Zeile), eigene Wörter, Abkürzungen
  Merken/Verwerfen, ausgeschlossene Apps über `AppChooser` aus `AgentSnippetsSection.kt`, „Alles
  vergessen“; bei gesperrtem Gerät nur ein Hinweis. Texte in `agent_memory_strings.xml` (`values/`,
  `values-de/`, gemeinsam mit 49). Tests: Fälle „stage 46“ in `InputLogicTest`
  (Chip hängt nur auf Tipp an, gesperrte Felder, neben privatem Text und in `NO_LEARNING_FIELD`,
  eigene Wörter mit Rücknahme und Weitertippen, Abkürzung merken und auf Tipp ersetzen,
  weitergeschriebene und bekannte Kurzformen, Baustein, Rest nicht privat) und `SuggestTest`
  (eigenes Wort und gemerkte Kurzform nicht korrigiert, Vervollständigung hinter der Korrektur),
  `AT/MemoryScreenTest.kt` (Seite), `AT/SymbolSettingsScreenTest.kt` (Löschen auf „Gelernte
  Wörter“ und Ausschalten von „Personalisierte Vorschläge“ leeren das Gedächtnis).

## Updates

> Prüfung auf neue Builds im öffentlichen Release-Repository (auf Tipp, täglich als Option, seit 56 beim Öffnen der Tastatur höchstens alle 15 Minuten, nie mit Getipptem), Download und Installation nur nach eigenem Klick.

- Quelle: öffentliches Repository `Tra1c0/SecondHandAgenticKeyboard` (`UpdateReleases.REPOSITORY` in
  `C/UpdateReleases.kt`, im Workflow `RELEASE_REPOSITORY`). Kein Token, kein `Authorization`-Header.
- `A/updates/GitHubUpdateClient.kt`: `UpdateHttp` (eigener OkHttp-Client, nicht der KI-Client,
  ohne automatische Redirects und Retries; Connect 15 s, Read 30 s) und `GitHubUpdateClient`:
  `GET …/releases?per_page=10` (höchstens 2 MiB, 30 s; seit 56 `releaseList(etag)` mit
  `If-None-Match`, 304 = `ReleaseList.Unchanged`, nur wenn ein ETag gesendet wurde, sonst Fehler;
  200 = `ReleaseList.Fresh` mit dem geprüften `ETag`), strenges Parsen, `.sha256`-Asset
  (höchstens 4 KiB), APK-Download über `browser_download_url` mit selbst verfolgten Redirects
  (höchstens 5, nur HTTPS), Größe ≤ 120 MB und genau die gelistete Größe, SHA-256 beim Schreiben.
  `UpdateError`/`UpdateFailure` sind neutral, kein Antworttext wird gezeigt oder geloggt.
- `C/UpdateReleases.kt` nimmt nur Assets mit genau der erwarteten Download-URL
  (`https://github.com/<REPOSITORY>/releases/download/build-<n>/<Name>`). Seit 57 heißt die APK
  `SecondHand-build-<n>.apk` (`apkName`/`checksumName`); `newest` nimmt dieses Paar aus APK und
  Prüfsumme zuerst und fällt auf `AgentKeyboard-build-<n>.apk` (`legacyApkName`/`legacyChecksumName`)
  zurück, nie gemischt (P240). `GitHubUpdateClient.USER_AGENT` ist `SecondHand-Updater`.
- `A/updates/Updates.kt`: `check` (speichert das Ergebnis; seit 56 mit dem gespeicherten ETag, solange
  `compared_version` die installierte Version ist; 304 behält Build, Notizen und ETag und schreibt
  nur `status` und `checkedAt`, ein Fehlschlag behält alles, P239), `downloadAndInstall` (fragt die API
  erneut, lädt, vergleicht SHA-256, prüft mit `ApkCheck` Paket, Zertifikate und höhere
  Versionsnummer, erst dann `ApkInstallSession`; die Datei wird immer gelöscht), `cleanup`,
  `runScheduled` (nur entsperrt, nur bei eingeschaltetem Schalter, sonst Job abbrechen;
  Benachrichtigung einmal je Build), `setDaily`.
- `A/updates/UpdateInstaller.kt`: `ApkIdentity`, `ApkCheck` (gleiche nicht leere Menge der
  SHA-256 der aktuellen Signaturzertifikate, `apkContentsSigners` ab Android 9),
  `AndroidApkInspector` (`getPackageArchiveInfo`), `PackageInstallerSession` (Session mit
  `setAppPackageName`, Rückmeldung per explizitem `PendingIntent`), `UpdateInstallReceiver`
  (startet bei `STATUS_PENDING_USER_ACTION` den Bestätigungsdialog des Systems).
- `A/updates/UpdateCheckJob.kt`: `UpdateScheduler` (JobScheduler, 24 h, Netz nötig, persistiert),
  `UpdateCheckJobService`, `UpdateNotifier` (Kanal „Updates“, öffnet nur die Seite über
  `OPEN_SETTINGS_DESTINATION` mit `FLAG_ACTIVITY_CLEAR_TASK`, P39).
- `A/updates/UpdateSettings.kt`: CE-Einstellungen `agent_updates` (`UpdateStatus`, seit 38h
  `checkedAt`, von `Updates.check` mit jedem Ergebnis geschrieben; seit 56 `onOpen` (Standard an),
  `openAttemptAt`, `openFailures` und `etag`, das nur `remember` schreibt).
- Prüfung beim Öffnen (56, Entscheidung des Owners vom 2026-11-15, kein Push über FCM oder Server):
  `A/updates/UpdateOnOpen.kt`. `onStartInputView` (aus `LastField.capture`, also der Zeile von 55
  in `LatinIME.onStartInputViewInternal`) prüft auf dem Hauptthread nur Werte und Zustände:
  `SettingsValues.mIsLocked`, Passwortfeld (`mIsPasswordField`, jede Variante, auch im
  `EditorInfo.inputType`), Inkognito (`mIncognitoModeEnabled`, `mNoLearning`,
  `IME_FLAG_NO_PERSONALIZED_LEARNING`), live gesperrt (`UpdateChip.locked`, vor jedem Lesen der
  CE-Einstellungen), eine laufende Prüfung, Schalter `onOpen`, `UpdateOnOpenPolicy.due`,
  Datensparmodus (`restrictBackgroundStatus == ENABLED`, unbekannt = eingeschränkt), validiertes Netz
  (`NET_CAPABILITY_INTERNET` und `_VALIDATED`, unbekannt = keins), installierte Version. Erst dann
  geht `run` an den `executor` (ein Daemon-Thread niedriger Priorität): `openAttemptAt` zuerst
  speichern, `Updates.check`, `openFailures` nach `failuresAfter`, und bei einem neu bekannten Build
  `refresh` per `postAtTime` auf den Hauptthread (P51). Ein Überspringen ist kein Versuch und
  schreibt nichts. `Skip` nennt den Grund nur für Tests. Austauschbar in Tests: `clock`, `online`,
  `dataSaver`, `client`, `executor`.
- `K/settings/screens/AgentUpdatesScreen.kt`: Seite „Updates“ (seit 56 zuerst der Schalter „Neue
  Builds sofort melden“ mit Text je Zustand, dann der Schalter der täglichen Suche, „Jetzt prüfen“, Ergebnis,
  Download-Knopf mit Fortschritt und Abbrechen, Weg zu „Unbekannte Apps installieren“, Hinweis
  für Freunde). Texte in `agent_update_strings.xml`, Icon `ic_agent_settings_updates.xml`.
  Seit 54 zeigt sie den Download-Knopf auch für `knownBuild` nach einem fehlgeschlagenen Download
  (`downloadAndInstall` fragt die API ohnehin erneut) und startet `startInstall` einmal, wenn
  `UpdatePrompts.takeInstallRequest` für diesen Build zutrifft.
- Update-Fenster (54): `Updates.check` speichert nach jeder erfolgreichen Prüfung über
  `UpdateSettings.remember` den neueren Build (`known_build`), seinen Release-Text
  (`ReleaseNotes.cap`, `known_notes`, `known_notes_truncated`) und die verglichene Versionsnummer
  (`compared_version`); eine fehlgeschlagene Prüfung und ein fehlgeschlagener Download ändern das
  nicht (P211). `GitHubUpdateClient.parseReleases` liest `body` (fehlt oder `null` = leer, jeder
  andere Typ lässt die Prüfung scheitern). `newerBuild(installed)` gilt nur über der installierten
  Version. `A/updates/UpdatePrompts.kt`: `UpdatePrompts.next` (gesperrt nichts; erst „Neu in“, wenn
  die installierte Version über `seen_version` liegt, nach einer frischen Installation
  (`firstInstallTime == lastUpdateTime`) nur merken; dann „Build N ist da“ bis `later_build`;
  `available = false` auf der Seite Updates selbst), `markSeen`, `later`, `embeddedNotes` (Asset
  `NOTES_ASSET` im Ordner `agent` der Assets, höchstens 256 KiB), `requestInstall`/`takeInstallRequest` (einmal je
  Build, jeder Blick löscht). `UpdateChip`: `build` (`mIsLocked` zuerst, dann CE-Einstellungen in
  `runCatching`, erst bei einem Kandidaten live `locked`, austauschbar wie `installed`, das je
  Prozess einmal gelesen wird), `createView` (`KeyboardCommands.chipRow` mit einem Chip, ohne die
  Kennung `agent_command_chips`, Tag `TAG`), `open` (prüft die Sperre erneut, dann
  `UpdateNotifier.pageIntent`). `K/settings/screens/AgentUpdatePrompts.kt`: `AgentUpdatePrompts`
  (gibt true zurück, solange ein Dialog steht; „Neu in“ gilt beim Zeigen als gesehen, „OK“ holt den
  nächsten, „Installieren“ = `requestInstall` + `SettingsDestination.navigateTo(AgentUpdates)`,
  Zurück schließt „Build N ist da“ nur für jetzt), `ReleaseNotesText` (6 Blöcke, dann „Alle
  Notizen anzeigen“, höchstens 420 dp hoch, scrollt; gekürzt mit Link-Text zur Release-Seite).
  Texte `agent_update_prompt_strings.xml` (`values/`, `values-de/`). Asset aus
  `release-notes/next.md` beim Bauen (Task `EmbedReleaseNotes` in `app/build.gradle.kts`, auch
  lokal und in den Unit-Tests). Chip-Platz: siehe „Befehle“ → Leiste.
- Tests: `AT/UpdateCheckTest.kt` (Anfrage ohne Zugangsdaten, strenges Parsen, Redirect nur
  HTTPS, Prüfsummen-, Paket- und Zertifikatsfehler, Größen, Schalter und Job, Benachrichtigung
  einmal je Build; seit 54 Release-Text, Kappung, verglichene Version, Fehlschlag behält),
  `CT/UpdateReleasesTest.kt`, `CT/ReleaseNotesTest.kt` (54), `AT/UpdatePromptsTest.kt` (54:
  eingebettete Notizen = `release-notes/next.md`, „Neu in“ einmal und nicht nach frischer
  Installation, „Build N ist da“ je Build mit „Später“, weg nach der Installation, gesperrt nichts,
  Dialoge in Compose, keine Prüfung und kein Job), `AT/UpdateChipTest.kt` (54: Chip nur bei
  bekanntem neuerem Build, weg nach der Installation, gesperrt nicht, Tipp öffnet nur die Seite),
  `InputLogicTest.updateChipShowsOnlyOnTheNeutralStripWhileANewerBuildIsKnown` (echte Leiste).
  Seit 56: `CT/UpdateOnOpenPolicyTest.kt` (15 Minuten, Verdopplung bis 4 h, Rücksetzen, Uhr
  zurückgestellt, ETag-Form), `UpdateCheckTest.theETagIsStoredAndSentAndA304KeepsTheResult`,
  `AT/UpdateOnOpenTest.kt` (Anfrage nur im Executor, Versuchszeit gespeichert und nach Neustart
  gültig, keine zweite laufende Prüfung, Verdopplung nach 429, Überspringen bei Schalter aus,
  gesperrt, Inkognito, Passwort, Datensparmodus, ohne Netz; echte Netz- und Datensparabfrage mit
  den Robolectric-Schatten; Schalter auf der Seite) und
  `InputLogicTest.updateChipAppearsAtOnceWhenTheCheckOnOpenFindsANewerBuild` (Chip ohne weiteres
  Zutun in der leeren Leiste, nicht statt Wörtern, nicht gesperrt).

## Unveränderter Upstream

> Was ohne Änderung von HeliBoard übernommen ist; nur nach Ordnern beschrieben.

- `K/latin/`: Eingabelogik, Wörterbücher, Vorschläge, Rechtschreibprüfung, Einstellungen-Backend.
- `K/keyboard/`: Tastatur-View, Layoutparser, Emoji, Zwischenablage.
- `K/settings/`: Compose-Einstellungen (bis auf die oben genannten Dateien).
- `app/src/main/jni/`: native Engine. `app/src/main/assets/`: Wörterbücher und Layouts.
- Upstream-Dokumente `README.md` (Fork-Hinweis oben), `layouts.md`, `CONTRIBUTING.md`,
  `AI_USAGE.md`, Lizenzen. Die Upstream-Tests in `app/src/test/` laufen mit `upstream-tests`.

## Symbole malen

> Etappe 27: Sonderzeichen auf die Buchstaben malen statt Gestenschreiben, lokal erkannt.

- Ablauf: Schalter `agent_symbol_drawing` (Standard aus) → `mSymbolDrawing`, Gestenschreiben aus,
  HeliBoards Gestenerkennung bleibt an (auch ohne Gesten-Bibliothek; nicht in Passwortfeldern,
  ohne Hauptwörterbuch und mit TalkBack-Erkundung, weil `GestureEnabler` dann aus ist).
  Erster Strich: unveränderte Erkennung in `PointerTracker`, `onEndBatchInput` liefert alle
  Punkte seit dem Aufsetzen; `A/SymbolInk.kt` nimmt nur die Punkte des ersten Zeigers.
  Weitere Striche innerhalb der Pause: Hook `startsInk`, sofort Tinte, auch Punkte (÷), auf jeder
  Taste, die ein Zeichen schreibt (36: auch Satzzeichen; Leertaste, Enter und Modifier nicht), aber
  nur nahe der Zeichnung (Prüfer H1, `InkTap.nearDrawing`: ihr Rechteck, auf jeder Seite um 0,6 ihrer
  längeren Seite vergrößert, mindestens um 3 Tastenbreiten `mMostCommonKeyWidth`, `InkTap.KEY_MARGIN`;
  Nachprüfer F2: mit einer Tastenbreite begannen % (erster Kreis klein) und ≤/≥ (Strich zuerst) ein
  neues Symbol; `CT/InkAreaTest.kt`: eingebaute Zeichnungen 2–3 Reihen hoch brauchen bis 2,6, die
  zufälligen Reihenfolgen des Finger-Modells zu 97 % bis 3). Eine Berührung
  weiter weg beendet die Zeichnung und wird normal getippt (Ziffer, Satzzeichen, Buchstabe).
  Pause (36, `C/InkTiming.kt` `InkPace`): 900 ms, ab drei gemessenen Lücken (Loslassen bis
  nächstes Aufsetzen, nur innerhalb eines Symbols, `SystemClock.uptimeMillis`) 2,5 × Median der
  letzten neun, begrenzt auf 600–1500 ms; nur im Speicher. Vorher fest 500 ms (Rückmeldung zu
  Build 14: der Rest eines Symbols begann ein neues). Seit 100: max(2,2 × Median, 1,5 × längste) der Lücken vor einem Strich, 300–1500 ms (unten „Sofort, wenn fertig“). Ein Tipp (36: Bewegung unter
  `InkTap.SLOP_DP` = 8 dp wie Androids Touch-Slop (Nachprüfer F3; 6 dp nach dem ersten Prüfer) vom
  Aufsetzpunkt, Dauer egal, P111; vorher 10 dp, in der ersten Fassung
  von 36 4 dp, Prüfer H1) ist nur dann ein
  (seit 43: möglicher, siehe „Punkte (43)“) Punkt, wenn `SymbolInk.dotCheck` zustimmt (31, `C/InkDots.kt`): (1) ein Symbol hat Punkte
  (eingebaut bis 43 nur ÷, dazu eigene), (2) die bisherigen Striche ohne frühere Punkte gleichen dessen
  punktlosem Teil (Abstand des Erkenners ≤ `ACCEPT_DISTANCE`, seit 36 1,8 mit Richtungs- und
  Strukturterm, vorher `$P` ≤ 1,3), (3) der Tipp liegt nach Deckung der Teile (gleiche Mitte, gleiche
  längere Seite, ein schräger Strich bis 20° gerade gedreht) in Richtung eines seiner Punkte, von
  der Mitte des punktlosen Teils aus gesehen höchstens 0,12 Symbolgrößen seitlich und 0,25 näher
  oder weiter (bei ÷: ungefähr senkrecht über oder unter der Mitte des Strichs; Korrektur nach
  Prüfer, vorher ein Kreis mit 0,3), (4) kein ganzes Symbol passt klar besser (Faktor 1,25 plus
  0,1; ein fertiges → ist nah an einem Strich, bleibt aber ein Pfeil). Seit 43 ist (3) ein Kreis
  `InkDots.DOT_REACH` 0,5 um einen Punkt, (2) erlaubt `BASE_DISTANCE` = `CLEAR_DISTANCE` 2,3 und
  verlangt weniger Punkte in der Zeichnung als im Symbol, (4) vergleicht nur mit Symbolen ohne Punkte.
  Sonst schreibt `SymbolInk` das wartende Symbol sofort, und `PointerTracker` tippt die Taste wie
  einen Tipp (P96). Abwägung: ein Buchstabe direkt über oder unter der Mitte eines gemalten Strichs
  wird zum Punkt (etwa eine Taste je Nachbarreihe, P104). `SymbolDrawing.attach` setzt `dotCheck`
  mit den eigenen Symbolen (gleiche Vorlagen und derselbe `InkRecognizer` wie der Erkenner), sonst
  `builtInDotCheck`. Die Regel kostet nur bei Tipps im Fenster etwas (ca. 50 µs, mit Schritt 4
  einmal die volle Erkennung, ca. 5–14 ms auf dem JVM); schreibt der Tipp danach das Symbol, liefert
  `InkRecognizer` das Ergebnis von Schritt 4 aus seinem Merker für die letzte Zeichnung, ohne neu
  zu rechnen. Zwei Daumen im Fenster: P104. Bewusst so gelassen (Prüfer 30/31): Ein Finger im
  Fenster ist Tinte ohne Langdruck-Timer; langes Halten ohne Bewegung ist beim Loslassen ein Tipp
  und tippt den Grundbuchstaben, kein Popup. Der Randfall eines Wortvorschlags im Fenster bei
  einbuchstabiger Vorhersage bzw. Satzzeichen-Leiste (P97) bleibt wie er ist.
  Ein Wortvorschlag im Fenster: `SymbolDrawing.beforeSuggestionPick` (P97). Nach der Pause (Timer per `postAtTime`, P51) oder vor jeder anderen Taste
  (`flush`) gehen die Striche an die Senke. Bis 99 wurde nie sofort nach einem Strich geschrieben, weil
  jeder Strich der erste eines mehrstrichigen Symbols sein kann (– → =, ≠); seit 100 sofort, wenn nichts mehr folgen kann
  (unten „Sofort, wenn fertig“).
- Punkte (43, Rückmeldung zu Build 19: ÷/% verloren oder Symbol plus Buchstabe): Ein Tipp, dem
  `isDot` zustimmt, ist ein möglicher Punkt: `SymbolInk.onInkUp` legt ihn als einen Punkt (seine
  Mitte) in die Gruppe, merkt Index, Zeiger, Aufsetzpunkt und Lücke (`Possible`) und tippt nichts.
  `SymbolInk.resolve` (in `finish` und `takeWaiting`) fragt `DotCheck.keeps`: `InkDots.keeps` nimmt
  die Punkte nur, wenn `InkRecognizer.best` der ganzen Zeichnung ein Symbol ist, dessen Vorlage mit
  Punkten genau so viele Punkte hat, und jeder Punkt nach Deckung (wie (3)) höchstens
  `KEEP_REACH` 0,35 von einem eigenen Punkt der Vorlage liegt (der Erkenner allein sieht kaum, wo 2
  von 32 Punkten liegen). Dann zählen ihre Lücken fürs Tempo. Sonst geht die Zeichnung ohne sie an
  die Senke, danach tippt `SymbolInk.tapper` (`PointerTracker.typeInkTap`, gesetzt in
  `PointerTracker.init`) jeden als Taste am Aufsetzpunkt auf der jetzigen Tastatur, in Reihenfolge
  (P151). Ein späterer Tipp, der kein möglicher Punkt ist, schreibt so erst Symbol und mögliche
  Punkte, dann sich selbst. Die Vorschau entscheidet auf dem Worker genauso (`Sink.onWaiting` mit
  `possibleDots`, `SymbolDrawing.onWaiting`). Neue Vorlagen (`InkSymbols`): % mit Strich und zwei
  Punkten (gestaucht wie die übrigen), ‰ (Kreise oder Punkte), ! ¡ ? ¿; in Vorlagen mit Punkten steht
  der Strich zuerst (P152). Abwägung: ein möglicher Punkt erscheint als Buchstabe erst nach der
  Pause; verloren ist ein Buchstabe nur als Teil eines Punkt-Symbols (zwei Buchstaben über und
  unter der Mitte eines Strichs: ÷; einer unter einem senkrechten Strich: !). `cancel` verwirft
  mögliche Punkte mit der Zeichnung. Zahlen: `docs/VALIDATION.md` → Etappe 43.
- Start direkt nach dem Tippen (43): HeliBoard setzt nach einem getippten Buchstaben 500 ms
  (`mGestureFastTypingCooldown`) `mAfterFastTyping`; dann verlangt `isStartOfAGesture` von 6 auf
  0,35 Tastenbreiten über 450 ms fallend, so dass ein Symbolstrich direkt nach einem Wort ein Tipp
  blieb. Mit Symbole malen gibt `PointerTracker.onDownEvent` `SymbolInk.gestureTypingTime`
  (`eventTime − Int.MAX_VALUE`, Sperre aus) weiter und merkt `mInkAfterTyping`
  (`InkTap.isAfterTyping`, 500 ms); `onGestureMoveEvent` startet dann erst ab
  `InkTap.START_AFTER_TYPING_KEYS` 0,75 Tastenbreiten vom Aufsetzpunkt (`mGestureDownX/Y`). Ohne
  Symbole malen unverändert; die Einstellung „Schnelltipp-Sperre“ wirkt beim Malen nicht und ist
  dann auf der Gestenseite (wie bisher) ausgeblendet (P153). Nach Prüfer (M1): Mit einem weiteren
  Finger auf der Tastatur bleibt HeliBoards Sperre. Startet die Zeichnung so direkt nach dem Tippen
  (`SymbolInk.onStartAfterTyping`, `AfterTyping`), entscheidet `SymbolInk.resolve` beim Schreiben
  nach der Pause oder vor einer Taste (nicht beim Chip, `takeWaiting`): Ist das, was geschrieben
  würde (ohne mögliche Punkte, die Buchstaben werden), ein einziger Strich und
  `InkTap.isSlidTap` (weitester Punkt höchstens `SLID_TAP_KEYS` 1,5 Tastenbreiten vom Aufsetzpunkt,
  kein Punkt weiter als `SLID_TAP_BEND` 0,25 dieser Strecke von der Linie dorthin) oder sagt
  `Sink.recognizes` nein (`SymbolDrawing`: gleicher Erkenner, `best == null`), schreibt es nichts,
  meldet `Sink.onWaiting` leer und tippt über `tapper` die Taste am Aufsetzpunkt, danach die
  möglichen Punkte. Ein zweiter Strich oder behaltene Punkte lassen es eine Zeichnung (=, ÷).
  Für so einen Strich zeigt die Vorschau keinen Chip (`isSlidTapSoFar`).
- Vorschau (36): 120 ms nach einem Strich ohne neuen (`SymbolInk.PREVIEW_DELAY_MILLIS`, Prüfer M2)
  meldet `SymbolInk` die wartende Zeichnung an `Sink.onWaiting`; `SymbolDrawing` liest den Erkenner
  im Hauptthread, erkennt auf einem eigenen Thread (`previewExecutor`, in Tests direkt) und zeigt das
  Ergebnis per `postAtTime` (P51) nur, wenn sich nichts geändert hat (`previewGeneration`, jede
  Zustandsänderung über `setShown`): in der Leiste „✎ →“ plus bis zu zwei Alternativen
  (`Waiting`, mit der Zahl der Striche, für die es gilt). Beginnt ein weiterer Strich
  (`Sink.onStrokeStarted`), ist der Chip veraltet (Nachprüfer F1): ein Tipp darauf, oder ein Tipp,
  nachdem die Zeichnung mehr Striche hat, schreibt den besten Treffer aller Striche und lernt nichts.
  Auch bei gesperrter Eingabe wird eine laufende Vorschau verworfen (F5). Derselbe Erkenner; das
  Schreiben nach der Pause kommt aus dessen Merker (P112). Ein
  Tipp schreibt sofort (`SymbolInk.takeWaiting`, nichts, solange ein Strich auf den Tasten ist oder
  die Eingabe gesperrt ist, Prüfer N3: die Zeichnung bleibt dann); eine Alternative ist eine
  Korrektur (Lernen, unten). Bekannt, nicht geändert: im Modus „Leisten-Tasten und Vorschläge“ (EXPANDABLE) wird die
  Leiste bei jedem Wechsel zwischen Vorschau und Vorschlägen wie bei den übrigen Chips umgeschaltet.
  Wird die Zeichnung
  verworfen, meldet `cancel` eine leere Liste und die Leiste holt ihre Vorschläge zurück
  (`postUpdateSuggestionStrip`); `reset` setzt den Zustand vor `SymbolInk.cancel` zurück (P112).
- Vollständigkeit (83, `C/InkRecognizer.kt`): `InkMatch.complete` ist wahr, wenn irgendeine Vorlage des Textes höchstens so viele
  Striche (`InkCloud.strokeCount`, Linien plus Punkte) hat, wie gemalt wurden; der Abstand ist dann der der nächsten solchen
  Vorlage (ohne Nähe-Schwelle: eine ferne vollständige Vorlage verdrängt eine nahe unvollständige desselben Textes aus der Liste,
  der Text steht dann weit hinten). `match` führt zwei Listen; die Drehprüfung (`near`) rechnet je Art mit dem nächsten Abstand
  der eigenen Art, eine nahe unvollständige Vorlage kürzt die Drehung der vollständigen nicht. `InkResult.best`: der nächste
  vollständige Kandidat, wenn er angenommen wird (Abstand ≤ `ACCEPT_DISTANCE`, oder bis `CLEAR_DISTANCE` mit Abstand zum Nächsten
  unter den vollständigen); nur wenn keiner angenommen wird, der nächste unvollständige nach derselben Regel unter den
  unvollständigen (Rückfall: „?“ ohne Punkt, „+“ in einem Zug). Ausnahme: ein vollständiger Kandidat, der nur als klarer
  angenommen wird (über `ACCEPT_DISTANCE`), weicht einem unvollständigen, der innerhalb davon und näher liegt. `alternatives`:
  erst vollständige, dann unvollständige (% nach „/“), ohne Bestes nur unvollständige. `candidates` bleibt nach Abstand sortiert
  und enthält alle. Neue Vorlagen „/“ (45°, etwa 60°, etwa 30°) und „\\“ (gespiegelt, `InkSymbols.slash`) sowie Fingervarianten
  „%“ (Kreise und Strich in einem Zug), „$“ (S und Strich in einem Zug), „≠“ (Balken als Zickzack oder Schrägstrich angehängt).
  `InkDots.isDot` (Regel 4) vergleicht nur mit vollständigen Symbolen ohne Punkte und lässt Texte aus, die nur ein gerader Strich
  sind (`bareLines`: – | / \\ und eigene gerade Striche): Eine Linie, die auf Punkte wartet, ist der punktlose Teil von %, ÷, !, kein
  Symbol, das „klar besser“ passt (ohne das nahm „/“ die Tipps von % weg: 177 statt 195 von 200). Winkel eines einzelnen Strichs
  (`InkLogicTest.theAnglesOfASingleStrokePinned…`): bis 15° „–“, 18° bis 75° „/“, ab 78° „|“, gespiegelt „\\“. Eine Zeichnung direkt
  nach dem Tippen, die weder Tipp noch angenommen ist (`SymbolInk.resolve`, `Sink.recognizes` = `best != null`), wird wie vorher
  als Taste getippt; mit dem Rückfall ist `best` seltener null als nach der ersten Fassung von 83, genauso oft wie vor 83, wo
  unvollständige Symbole auch gewannen.
- `A/SymbolDrawing.kt`: Erkennung (eingebaute plus eigene Vorlagen, neu gebaut, wenn sich der
  gespeicherte Text ändert), Einfügen über `LatinIME.onTextInput` (beendet das Wort wie ein
  Satzzeichen, Autokorrektur des Worts davor wie dort), danach bis zu drei Alternativen als Chips
  (`KeyboardCommands.chipRow`); ein Tipp ersetzt über `LatinIME.replaceCommandText` (ein Commit,
  Prüfung von Text und Schreibmarke). Nichts erkannt: kein Text, gedämpfter Chip „Nicht erkannt“
  öffnet die Seite (bei gesperrtem Gerät nicht, P77). Die Chips gelten nur, solange die Schreibmarke steht und der Text davor das
  Symbol ist; jede Taste beendet sie. Nichts wird gespeichert oder geloggt.
- Erkennung (`C/InkRecognizer.kt`, 36): `$P` plus (1) Richtung des Strichs an jedem Punkt ohne
  Vorzeichen (`InkCloud.angles`, `$P+`-artig; Kosten 0,25 bei rechtem Winkel, ein Punkt gegen einen
  Strich 0,125), (2) Struktur: 0,25 je Strich und 0,3 je Punkt mehr oder weniger als die Vorlage
  (`lineCount`/`dotCount`, ein Punkt = Strich bis 0,08 der Größe). Reihenfolge und Richtung der
  Striche bleiben egal. Gedreht (±12°) wird nur gegen Vorlagen, die aufrecht höchstens 1,6-mal so
  weit wie die beste sind; über 2,8 (`CUTOFF`) wird nicht weiter summiert. Annahme bei ≤ 1,8 oder
  ≤ 2,3 mit 1,2-fachem Vorsprung, Alternativen bis 2,6 (P114). Vorlagen: 105 (vorher 73), dazu
  Fingervarianten (Kopf in einem Strich oder als zwei Striche, Pfeil ohne Absetzen, ⇒ mit Balken bis
  unter einen hohen Kopf, flache und schmale °, ★, ×, zweiteiliges ♥, € mit einem Balken).
  Ca. 4–7 ms je Zeichnung gegen 105 Vorlagen auf dem JVM (geteilter Rechner), der alte Code 12 ms
  gegen 73; mit allen Vorlagen, die ein Nutzer haben kann (105 + 90 eigene + 120 persönliche = 315),
  13,5 ms (Test mit Grenze 40 ms; am Telefon langsamer, darum die Vorschau nicht im Hauptthread,
  Prüfer M2). Gewichte und Schwellen
  aus dem Benchmark (`docs/VALIDATION.md` → Etappe 36).
- Persönliche Vorlagen (36, `C/PersonalInkSymbols.kt`): Schlüssel `personal` in `agent_symbols`
  (Format wie eigene Symbole, höchstens 3 Zeichnungen je Symbol, die neuesten bleiben, höchstens
  40 Symbole; Prüfer M2, vorher 5 und 80). Eine Zeichnung mit Punkt wird nur für ein Symbol mit
  Punkten behalten (`PersonalInkSymbols.hasDots` über alle Vorlagen des Symbols, Prüfer M3, P113).
  Quellen: (a) Wahl einer Alternative (Vorschau oder nach dem Schreiben), nur mit
  Schalter `agent_symbol_learning` (Standard aus), ohne Lernpause, ohne Inkognito, entsperrt, kein
  Passwortfeld, und nur mit „Personalisierte Vorschläge“ (Nachprüfer F7: dessen Ausschalten löscht
  sie) (`SymbolDrawing.mayLearn`). Die Wolke der Zeichnung liegt dafür im Speicher am
  geschriebenen Symbol (`Inserted.cloud`), dazu die persönlichen Vorlagen von vor dem Lernen
  (`Inserted.learnedOn`, Prüfer N4): eine zweite Wahl lernt auf diesem Stand neu, die Wahl des
  erkannten Symbols stellt ihn wieder her, auch eine dabei verdrängte Zeichnung. (b) Kalibrieren
  auf der Seite: jedes eingebaute Symbol zweimal (`CalibrationCard`, Überspringen, Fertig; jede
  Zeichnung wird sofort gespeichert). Gelöscht werden sie auf der Seite, mit „Gelernte Wörter
  löschen“ (`AgentLearningScreen`, zeigt auch ihre Zahl) und beim Ausschalten von „Personalisierte
  Vorschläge“ (`TextCorrectionScreen`, `SymbolDrawing.clearPersonalSymbols`). Der Erkenner nimmt
  eigene, persönliche und eingebaute Vorlagen und wird neu gebaut, wenn sich einer der beiden
  gespeicherten Texte ändert.
- Eigene Symbole: DE-SharedPreferences `agent_symbols`, Schlüssel `own` (Format in
  `docs/STORAGE.md`), Seite `K/settings/screens/AgentSymbolsScreen.kt` (Schalter, eigene
  Symbole dreimal malen in einer Karte `AddSymbolCard` auf der Seite, kein Dialog (P78), Liste
  der eingebauten als kleine Zeichnungen). `DrawingPad` nimmt wie die Tastatur 4096 Punkte
  (`InkGroup.MAX_POINTS`) für alle Striche zusammen und höchstens einen mehr; über 12 Striche oder
  über 4096 Punkten zeigt die Karte „Bitte neu malen“ (`agent_symbol_pad_limit`) und sperrt
  „Weiter“ (31). Texte in
  `agent_symbol_strings.xml`.
- Tests 43: `CT/InkDotFlowTest.kt` (ganzer Weg durch die Pause mit dem Finger-Modell: Punkt-Symbole,
  Buchstaben nach Symbolen ohne Punkte), `InkFingerModel.drawDotted`, `InkDotsTest` (mögliche
  Punkte und `keeps`), `SymbolInkTest` (möglicher Punkt wird Buchstabe, Reihenfolge, ÷ beide
  Reihenfolgen, ÷ mit seitlichen Punkten, % mit Punkten und kleinem Kreis, ! und ?, Malen
  100/200/400 ms nach einem Wort, rutschender Tipp beim schnellen Tippen, ohne Malen bleibt die
  Sperre), `InputLogicTest.symbolDraw*` (Buchstabe nach dem Symbol, ÷ aus zwei Tipps, Vorschau-Chip
  mit möglichem Punkt).
- Tests 36 nach Prüfer: `SymbolInkTest` (Ziffer und Punkt neben der Zeichnung werden getippt,
  kurzer Strich nahe der Zeichnung gehört dazu, rollender Tipp dort ist Buchstabe, zweiter Daumen
  nach 7 px, Strich bei zweitem Finger wird geschrieben, eine Vorschau für schnelle Striche),
  `InputLogicTest.symbolDraw*` (Chip bei gesperrter Eingabe, keine Punkt-Zeichnung für „–“,
  Zurücknehmen stellt verdrängte Vorlage wieder her), `SymbolSettingsScreenTest` (Zahl und Löschen
  auf „Gelernte Wörter“, Ausschalten der personalisierten Vorschläge), `InkRecognizerTest`
  (315 Vorlagen, `jvmTimingWithEveryTemplateTheUserCanHave`), `PersonalInkSymbolsTest` (3/40,
  Punkte), `InkTimingTest` (8 dp, Bereich), `InkAreaTest` (Folgestriche aller Symbole im Bereich).
  Nachprüfer: `InputLogicTest.symbolDrawPreviewChipForFewerStrokesWritesTheWholeDrawingAndLearnsNothing`
  (F1), `symbolDrawPreviewOnTheWorkerIsShownAndALateOneIsDropped` (F5, echter Thread; der direkte
  Executor der übrigen Tests wird nach jedem Test zurückgesetzt), `symbolDrawLearnsNothingWithoutPersonalizedSuggestions` (F7).
- Tests 36: `CT/InkBenchmarkTest.kt` mit `CT/InkFingerModel.kt` (Finger-Modell, Nachweis),
  `CT/InkTimingTest.kt`, `CT/PersonalInkSymbolsTest.kt`; `AT/SymbolInkTest.kt` (kurzer Strich,
  schneller Strich ohne Bewegung, Strich nach 800 ms, Pause nach Tempo, still gehaltener Tipp,
  Strich ab der Punkt-Taste, Vorschau-Meldungen, Abbruch an anderem Tracker);
  `InputLogicTest.symbolDraw*` (Vorschau-Chip, Chip während eines Strichs, Alternative der Vorschau
  und Lernen nur mit Schalter, zweite Wahl verschiebt, kein Lernen bei Inkognito/Pause/Sperre,
  persönliche Vorlage in der Tastatur); `AT/SymbolSettingsScreenTest.kt` (Lernschalter, Liste und
  Löschen, Kalibrieren mit Überspringen und Fertig).
- Tests: `AT/SymbolInkTest.kt` (echter PointerTracker, auch Passwortfeld; 31: Tipp im Fenster,
  Tipp nach →, Punkte nur mit Punkt-Symbolen, keine Spur nach dem Verwerfen; Korrektur: zwei
  Daumen in beiden Reihenfolgen, Tipp mit kleinem Rutschen auf die Löschtaste), `CT/InkDotsTest.kt`
  (Korrektur: Tasten neben der Mitte eines Strichs, Tipps rund um gemalte Striche, ÷ in beiden
  Reihenfolgen, eine Erkennung je abgelehntem Tipp),
  `AT/SymbolSettingsScreenTest.kt` (Schalter, Liste, eigenes Symbol dreimal malen, speichern,
  löschen), `InputLogicTest.symbolDraw*` (echte Weiterleitung, Einfügen, Chips, Sperre; 31: Vorschlag
  im Fenster; Korrektur: ein abgelehnter Tipp kostet eine Erkennung), Kern siehe oben. `AT/SymbolSettingsScreenTest.kt` prüft auch den ausgegrauten
  Gestenschalter und die Grenzen des Malfelds (31).

- Sofort, wenn fertig (100, Konzept `docs/SYMBOLE-TEMPO-KONZEPT.md`): `SymbolInk.add` meldet nach jedem Strich und jedem
  möglichen Punkt `Sink.onStrokeEnded(strokes, possibleDots, serial)`; `SymbolDrawing.checkFinished` prüft auf dem Hilfs-Thread
  (`previewExecutor`, in Tests direkt; übersprungen, wenn `SymbolInk.currentSerial` schon weiter ist), ob mögliche Punkte Punkte
  blieben (`InkDots.keeps`) und `C/InkCompletion.kt` `finished(cloud, result)` gilt: bestes Symbol vollständig und ≤
  `ACCEPT_DISTANCE`, nicht `extendable` (eine seiner Vorlagen liegt ≤ `EXTEND_DISTANCE` 2,0 an einem Teil einer Vorlage mit mehr
  Strichen; ein Teil ist jede echte Teilmenge der Striche, bei mehr als `MAX_SUBSETS` 35 Teilmengen nur die ersten Striche), und die
  Zeichnung selbst liegt an keinem Teil (≤ `PART_DISTANCE` 2,0). Dann postet es `SymbolInk.onFinished(serial)`, das nur schreibt
  (`finish(Way.INSTANT)`), wenn `serial` gleich ist (jeder Strichbeginn, jedes Schreiben, Verwerfen, Chip und `onBatchStart` zählt
  hoch), etwas wartet und kein Strich auf den Tasten ist. Der Timer der Pause bleibt immer gesetzt (Rückweg ohne Antwort). Die
  Teile rechnet `InkCompletion` faul (`SymbolDrawing.recognizer` baut es mit dem Erkenner aus denselben Vorlagen, eingebaut, eigen,
  persönlich), ob ein Text erweiterbar ist, je Text gemerkt; vorab rechnet ein eigener Thread niedriger Priorität („symbol-parts“) alle
  Texte (Code-Prüfung, Befund 3: auf dem gemeinsamen Hilfs-Thread stauten sich danach Vorschau und Prüfung; alle eingebauten zusammen
  ca. 0,3 s auf dem JVM); eine Prüfung, die vorher kommt, rechnet ihren Text selbst. Teile werden nur gedreht, wenn sie aufrecht unter 1,6-mal der Grenze lagen (wie im Erkenner). Die Prüfung
  kostet ca. 5 µs (Median, meist ist das Symbol erweiterbar) bis 8 ms (90 %) auf dem JVM; der Erkenner rechnet die Zeichnung dabei einmal, `write` nimmt das Ergebnis aus seinem Merker.
  Pause (100, `InkPace`): max(2,2 × Median, 1,5 × längste) der letzten neun Lücken vor einem Strich, 300–1500 ms, anfangs 900;
  Lücken zwischen getippten Punkten zählen nicht mehr (Kritik, Befund 1). Nachzügler (`SymbolInk.noteLateStroke`): beginnt bis
  `LATE_PAUSES` 2 Pausen nach einer nach der Pause oder sofort geschriebenen Zeichnung (`Written`) eine neue nahe an ihr
  (`InkTap.nearDrawing`, Tastenbreite aus `KeyboardSwitcher`), ohne getippte Taste oder Text dazwischen
  (`SymbolInk.onTypedInput` aus `KeyboardActionListenerImpl.onCodeInput`/`onTextInput`; der Druck auf die Taste, auf der der neue Strich
  beginnt, zählt nicht), nur nach einem Symbol ohne nachfolgende Buchstaben (`resolve` gibt die geschriebenen Striche zurück), und ergeben
  beide ein anderes angenommenes Symbol (`Sink.joins`, `SymbolDrawing`: zwei Erkennungen auf dem Hilfs-Thread, Antwort im Hauptthread),
  zählt `SymbolPerf.late`; nach der Pause zählt die Lücke
  (Beginn der Geste `onBatchStart` bis zum Ende des letzten Strichs) für die Pause. Geschriebenes bleibt.
  Tests: `CT/InkCompletionTest.kt` (Finger-Modell: keine Früheingabe in 8593 Anfängen, auch mit 40 persönlichen Symbolen; Anteil
  sofort je Symbol; → aus „–“ und „>“ in jeder Reihenfolge und Richtung; % aus „/“ und zwei Punkten; ÷ erst nach dem zweiten Punkt;
  Verwechslungen; eigenes Symbol lässt seinen Anfang warten; Zeit), `CT/InkTimingTest.kt` (Pause),
  `InputLogicTest.symbolDrawTwoTappedDotsMakeADivisionSign` (÷ sofort, einmal).
- Messpunkte (100): `A/SymbolPerf.kt` zählt je geschriebener Zeichnung den Weg (`Way`: sofort, nach der Pause, per Taste, per
  Chip; `SymbolInk.finish(way)`, `takeWaiting`) und die Zeit vom Loslassen des letzten Strichs bis zum Ende des Schreibens
  (`SystemClock.uptimeMillis`), dazu die Dauer der Prüfung „kann weitergehen?“ und Nachzügler; nur Zahlen, nur im Speicher.
  Ausgabe: Zeile `symbolPerf:` in `LatinIME.dump` (`SymbolInk.perfLine`, `dumpsys input_method`, auf dem Binder-Thread, darum
  mit `runCatching`) und fünf Zeilen in der Diagnose-Karte „Symbole“ (`Diagnose.symbolSpeed`). `InkPace.size`: Zahl der Lücken.
  Gerätetests `symbol_draw_latency`, `symbol_draw_variants` (`tools/devicetest/devicetest.py`) malen mit echten Berührungen über
  `InkProbe.java` der Test-App und schreiben die Zeiten als `INFO symbol …`.

## Zeichen-Kunst

> Etappe 53 (schließt 39 Text-Stile ein): Fenster in der Tastatur, das Zeichnung, Foto, Text und Rahmen zu Text aus Zeichen macht.

- Kern `C/charart/` (rein, ohne Android, ohne Netz; gleiche Eingabe gibt immer denselben Text):
  - `Raster.kt`: `Raster` (Grau 0 Papier … 1 Tinte; `withContrast`, `inverted`, `resampled` mit
    `Pool.AVERAGE` für Fotos und `Pool.MAX` für Striche, damit dünne Linien beim Verkleinern
    bleiben; `toBits` mit Schwelle 0,05–0,95 und `Dither` NONE/ORDERED (Bayer 4×4)/FLOYD_STEINBERG),
    `Bits` (Schwarzweiß; `outline` = Tinte mit Papier an einer der vier Seiten, `inverted`,
    `mirrored`, `scaled`, `placed`, `stack`).
  - `Sketch.kt`: die Zeichnung als Liste von Schritten in 0..1-Koordinaten (`SketchOp`: Stroke
    mit Radierer-Flag, Line, Ellipse als Umriss, Fill als 4er-Flutfüllung bis zur Tinte auf den
    Pixel-Indizes, ohne Paare), gezeichnet auf `SIZE` = 160 Pixel; `undo`, höchstens 400 Schritte
    und 2000 Punkte je Strich. Nachprüfung 53 (M3): `rasterize()` gibt das behaltene Raster der
    fertigen Schritte (`add` zeichnet nur den neuen Schritt auf eine Kopie, `undo` einmal alles neu),
    `preview(op)` den laufenden Schritt darüber in einem wiederverwendeten Puffer; ein Strich, der
    den zuletzt gezeigten fortsetzt, zeichnet nur seine neuen Punkte (P209). `SketchRaster`: `ink`
    und `erased` (der Radierer nimmt auch das Foto weg), `inkBounds`.
  - `BigFont.kt`: drei eigene Pixel-Schriften (FIGlet-artig, aber als Pixel, damit jeder Stil sie
    zeichnet): `MINI` 3×5, `STANDARD` 5×7, `BOLD` (Standard, jeder Strich ein Pixel breiter).
    Großbuchstaben, Ziffern, Ä Ö Ü, Satzzeichen und ♥; ß und ẞ → SS, Akzente fallen auf den
    Buchstaben zurück (É → E), anderes wird „?“ und gemeldet.
  - `UnicodeStyle.kt`: Familie (Serif, Serifenlos, Schreibschrift, Fraktur, Doppelstrich,
    Monospace, Kreise, Quadrate, Kapitälchen, Kopfüber) plus fett, kursiv, durchgestrichen
    (U+0336) und unterstrichen (U+0332), frei kombinierbar; die Lücken der mathematischen
    Alphabete (ℎ, ℬ, ℭ, ℂ …) als Ausnahmen; Umlaute als Buchstabe plus Trema (NFD/NFC); fehlt eine
    Kombination in Unicode, nimmt `resolve` die nächste und meldet `approximated`; fett plus
    Quadrate = negative Quadrate, deren A B O P als Emojis erscheinen (`emojiLetters`). Seit der
    Nachprüfung arbeitet `apply` je Graphem: ein Emoji bleibt ganz (keine Striche in ZWJ-Folgen,
    Hautfarben, VS16), Kopfüber dreht Grapheme statt UTF-16-Zeichen (N5).
  - `TextColumns.kt`: geschätzte Spalten (Emoji und breite CJK 2, Zeichen mit Kombination 0, sonst
    1) und eigene Graphem-Zerlegung (Hautfarben, ZWJ-Folgen, Flaggen, Tastenkappen), weil
    `BreakIterator` auf älteren JVMs sie trennt; `isArt`, `isStyledLetter`, `isEmoji`.
  - `CharArt.kt`: `ArtStyle` (Pixel je Zeichen: Braille 2×4, Blöcke 2×2 Viertel, Halbblöcke 1×2,
    ASCII-Rampe „ .:-=+*#%@“ 1×2, Emoji 1×1 mit zwei Spalten, eigene Zeichen 1×2 als Rampe oder
    bei zwei Zeichen schwarzweiß), `Look` (Stil, Umkehren, Umriss, eigene Zeichen, `EmojiPixels`),
    `PhotoOptions` (umwandeln oder nur Vorlage, Kontrast, Schwelle, Dithering, Umkehren nur im
    Foto), `PictureElement` (Zeichnung, `Photo.fitted` im Quadrat, Größe 25–100 %, Zuschneiden auf
    den Inhalt), `TextElement` (`TextMode` BIG/UNICODE/PLAIN, Größe 1–3 oder automatisch: die
    größte, die passt und höchstens `MAX_BIG_ROWS` = 10 Zeilen hat; sonst Wörter, dann Buchstaben
    umbrechen), `FrameOptions` (Linie, Doppelt, Rund, Emoji, Innenabstand 0–2), `LayoutOptions`
    (untereinander oder nebeneinander, Text oben, zentriert, Abstand, Spiegeln: Bilder per Pixel,
    Text rückwärts mit gedrehten Klammern), `TargetApp` (Breite, Handy-Breite, Codeblock,
    Nachrichtengrenze; `forPackage` für WhatsApp, Telegram, Discord, Signal), `OutputOptions`
    (Breite 8–60, Codeblock, Leerstellen als U+2800). `render` setzt alles zusammen: Breite minus
    Rahmen; nebeneinander bekommt der Text höchstens die Hälfte, das Bild den Rest; ein Bild hat
    halb so viele Zeilen wie Spalten mal Seitenverhältnis (Zeichen sind etwa doppelt so hoch wie
    breit), höchstens `MAX_PICTURE_ROWS` = 40. Füllstellen sind intern `PAD` (U+E000) und werden
    am Ende U+2800 oder Leerzeichen; Braille behält seine leeren Zellen (U+2800) immer, eigene
    Leerzeichen im Text bleiben Leerzeichen. Ohne Rahmen fallen Leerstellen am Zeilenende weg,
    leere Zeilen oben und unten immer. Codeblock: WhatsApp „```…```“ ohne Zeilenumbruch,
    Telegram und Discord mit, und nur, wo gleiche Breiten zählen: Bild, Groß-Schrift, Rahmen oder
    nebeneinander; Text in Unicode-Stilen oder normal allein nie (`ArtResult.codeBlock`, P210).
    Rahmen bleiben in der Breite (N6): ein Emoji-Rahmen hält sein Inneres auf ganze Emojis, bei
    kleiner Breite gibt zuerst der Innenabstand nach (`framed` mit der Breite). Groß-Schrift hat
    höchstens `MAX_BIG_LINES` = 80 Zeilen (ganze große Zeilen, Rest weg, `TOO_LARGE`). `wrap`
    trennt an Leerzeichen-Graphemen: das Leerzeichen eines durchgestrichenen oder
    unterstrichenen Texts geht samt Strich, kein Strich steht am Zeilenanfang (N4). `ArtResult`
    mit `warnings` (`ArtWarning`: zu breit fürs Handy, zu lang für die App, Rahmen/Nebeneinander
    ohne Codeblock, gemischte Breiten in einer Zeile, Emojis oder Schrift-Stile im Codeblock,
    Leerzeichen gehen verloren, Stil nur teilweise, Emoji-Buchstaben, Zeichen fehlen in der
    Groß-Schrift, Groß-Schrift gekürzt).
  - Tests `CT/charart/CharArtTest.kt` (Golden-Ausgaben je Stil, Breiten, Groß-Schrift, Schriften,
    Unicode-Stile samt Kombinationen, U+2800, Rahmen, Anordnung, Spiegeln, Ziel-Apps und
    Warnungen, Spalten; Nachprüfung: Codeblock nur für Kunst, Umbruch ohne Strich am
    Zeilenanfang, Kopfüber mit Emojis, Rahmen bei allen Breiten 8–33, 80 Zeilen),
    `CT/charart/SketchTest.kt` (Werkzeuge, Rückgängig, Füllen, Radierer über dem Foto, Dithering,
    Fotovorlage; Nachprüfung: behaltenes Raster und Vorschau gleich dem Neuzeichnen, Zeitgrenze für
    einen Strich aus 2000 Punkten über 399 Schritten).
- App `A/charart/`:
  - `CharArtKey.kt`: das Popup der Emoji-Taste (`LABEL` „✎“, `popupKeys`), siehe „Geänderte
    Upstream-Dateien“.
  - `CharArtView.kt`: das Fenster als Overlay in `AgentKeyboardFrame` (Views wie der Rechner,
    Farben aus dem Tastatur-Farbsatz). Oben Reiter (Zeichnen, Bild, Stil, Text, Rahmen, Anordnung,
    App) und Schließen, darunter die Textzeile (Tipp: Text mit den Fenster-Tasten ändern), die
    Optionen des Reiters, in der Mitte die Arbeitsfläche (Zeichenfläche, Tasten, Bilderleiste oder
    Breite mit Chatblase) und die Vorschau (Schrift passt sich der Größe an, 4–14 sp, im
    Codeblock Monospace), unten Hinweise und Kopieren, Baustein, Ersetzen, Einfügen. Ab 380 dp
    Höhe steht die Vorschau unter der Zeichenfläche, sonst daneben (`STACK_MIN_DP`). Jede Änderung
    ruft `render` (beim Malen höchstens alle 40 ms, beim Loslassen sofort). `show` liest Stil und
    Breite je App aus den Einstellungen; die App ist die des Felds, ohne Profil „Andere“
    (Nachprüfung 53, M1: vorher die zuletzt gewählte, anfangs WhatsApp mit Codeblock); eine Wahl im
    Reiter App gilt nur, solange das Fenster offen ist. Die Vorschau zeigt `result.text`, also genau
    das, was „Einfügen“ einsetzt, samt Backticks. Tippen mit den Fenster-Tasten (`isTyping`, M2):
    Optionen, Vorschau und die untere Reihe sind weg, die Tasten nehmen die ganze Breite und alles
    unter der Textzeile; ist die Reihe dann unter `KEY_ROW_MIN_DP` = 36 dp hoch (Querformat), gibt
    auch die Kopfzeile Platz (`onMeasure`). `hide` tut bei geschlossenem Fenster nichts (N9), sonst
    vergisst es Zeichnung, Foto und Text. Die Bilderleiste wird je Öffnen einmal gelesen
    (`picturesRequested`, N12); erlaubt, aber leer, sagt „Keine Fotos oder Screenshots gefunden.“
    Der Breitenregler rendert beim Ziehen und schreibt die Breite erst beim Loslassen (N13). Mit
    einer Markierung heißt „Ersetzen“ „Markierung ersetzen“ und steht statt „Einfügen“ da (N8).
    `offerUndo` macht „Einfügen“ für `UNDO_MILLIS` = 8 s zu „Rückgängig“; der zweite Tipp ist also
    nie eine zweite Einfügung. Die Chatblase im Reiter App ist so breit, dass die Handy-Breite der
    App in Braille hineinpasst, und bricht um wie im Chat.
  - `CharArtCanvas.kt`: die quadratische Zeichenfläche; zeigt die Pixel der Zeichnung (so grob wie
    das Ergebnis) über dem Foto (blasser, wenn es nur Vorlage ist), Werkzeuge Stift, Radierer
    (doppelt breit), Linie, Kreis, Füllen, drei Stärken; meldet jede Änderung an das Fenster. Ein
    Zug zeichnet über `Sketch.preview` nur Neues, Füllen rechnet erst beim Loslassen, ein Strich
    nimmt höchstens `Sketch.MAX_POINTS` Punkte und keinen näher als einen halben Pixel am vorigen
    (M3, P209).
  - `CharArtKeys.kt`: die eigenen Tasten des Fensters (QWERTZ mit Umlauten und ß bei Deutsch,
    sonst QWERTY; Ziffern und Zeichen; Umschalt, Löschen, neue Zeile, Fertig; Umschalt + ß = ẞ).
    Ohne Außenabstände: die sichtbare Taste ist um 2 dp eingerückt (`InsetDrawable`), getippt wird
    auf die ganze Zelle (bei 360 dp Breite 32 dp je Buchstabe in der deutschen Reihe, 35 dp in der
    englischen, Reihen 44 dp hoch; im Querformat bei 640 × 200 dp 63 dp breit, 41 dp hoch). Entscheidung des
    Leads statt einer internen `InputConnection`: Die Tastatur-Tasten liegen unter dem Fenster, und
    sie in ein eigenes Feld umzuleiten hätte `InputLogic`/`LatinIME` (Komposition, Autokorrektur,
    Umschalt-Zustand, Gesten) berührt. Die Fenster-Tasten ändern nur den Text des Fensters, nichts
    erreicht die App, nichts wird korrigiert oder gelernt. Für Namen von Bausteinen nur Buchstaben.
  - `CharArtImages.kt`: die neuesten 12 Bilder (Fotos und Screenshots) über `MediaStore`, nur mit
    der Bild-Berechtigung der Screenshot-Pille oder „nur ausgewählte Fotos“, gelesen erst im
    Reiter Bild und auf einem eigenen Thread; Vorlage höchstens 256 Pixel, als Dunkelheit
    (`darkness`), gedreht nach der Spalte `orientation` von MediaStore (`rotated` mit einer
    `Matrix`; ein hochkant fotografiertes Bild liegt sonst quer, M4). Kein System-Picker: eine
    Activity würde die Tastatur und damit das Fenster schließen. Ohne Berechtigung öffnet „Bilder
    erlauben“ die App-Seite der Systemeinstellungen.
  - Controller (`AgentEditorController`): `openCharArt` öffnet nur, wenn `editor.protection()` nichts
    meldet (sonst Hinweis, nichts gelesen); ein Wort in Komposition wird wie beim Öffnen der
    Einstellungen so abgeschlossen, wie es dasteht (`InputLogic.commitTyped`, kein Gestenrest), dann
    liest es bis zu `CharArtView.MAX_TEXT` = 300 Zeichen vor und nach der Schreibmarke direkt aus
    dem Editor. Eine Markierung liest es über `editor.capture()` wie das Panel (Länge vorher
    geprüft, höchstens `maxSelectionChars`, sonst Hinweis und kein Text; N8); bis 300 Zeichen wird
    der Schnappschuss zu „Markierung ersetzen“ über `editor.apply` (generierter Text, ein Commit,
    `commitSelection` meldet `onAgentTextSubmitted`). „Ersetzen“ ohne Markierung gibt es nur, wenn
    das ganze Feld vor der Schreibmarke gelesen wurde, dahinter nichts steht und
    `expectedSelectionStart` der gelesenen Länge entspricht; beim Tipp prüft es erneut Schreibmarke
    = Länge und nichts dahinter (N2, P197). Einfügen über `editor.captureAnchor`/`editor.insert`
    wie der Rechner (generierter Text, ein Commit, kein Lernen); Ersetzen über
    `InputLogic.replaceCommandText(Feldtext, Kunst, Schreibmarke, generated = true)`, das den Text
    vorher im Editor vergleicht; Rückgängig über `replaceCommandText(Kunst, vorher, Schreibmarke)`,
    nur an derselben Schreibmarke direkt danach; beide melden danach wie die anderen Wege
    `ime.onAgentTextSubmitted()` (N3). Kunst über `policy.maxSelectionChars` (32.768 Zeichen) lehnen
    Einfügen und Ersetzen mit eigenem Hinweis ab (`charArtTooLarge`, N7), nicht mit „Das Feld hat
    sich geändert“. UNKNOWN sperrt wie beim Rechner die Eingabe bis zur Rückmeldung des Editors.
    Kopieren ohne Vertraulich-Markierung (die Kunst ist kein Geheimnis); „Baustein“ fragt in der
    Textzeile nach dem Namen und speichert über `SnippetStore` als `literal` (so eingefügt, wie sie
    ist), auf `charArtWorker`, und wie Kopieren nur ohne `editor.protection()` (N10).
    `isBlockingInput` gilt, solange das Fenster offen ist; `close()` und jeder Editorwechsel
    schließen es (auch Zurück, Drehen, ein Wechsel des Designs: die Tastatur baut ihre Ansicht neu,
    Zeichnung und Text sind weg, N11).
  - Texte `agent_charart_strings.xml` (`values/`, `values-de/`), IDs `agent_charart_ids.xml`,
    Einstellungen `AgentPreferences.CHAR_ART_STYLE`, `CHAR_ART_WIDTH_PREFIX` (`docs/STORAGE.md`;
    `agent_char_art_app` wird seit der Nachprüfung nicht mehr gelesen).
  - Tests `AT/CharArtWindowTest.kt` (echter Langdruck auf die Emoji-Taste und Loslassen auf dem
    Stift, Popup-Code, Feldtext, Passwort/ohne Lernen/Inkognito/gesperrt, Malen aktualisiert die
    Vorschau, Einfügen nur per Tipp und genau einmal mit Rückgängig, Ersetzen und zurück, Fenster-
    Tasten, Baustein, App des Felds, Fotovorlage, Vorschaubild `app/build/agent-previews/charart-window.png`;
    Nachprüfung: „Andere“ für unbekannte Apps, kein Codeblock um Text-Stile, Vorschau = Einfügen,
    Tippen hochkant und quer mit `charart-typing-portrait.png`/`charart-typing-landscape.png`,
    Füllen erst beim Loslassen, gedrehtes Foto über einen eigenen `media`-Provider, kurzer Tipp
    öffnet weiter die Emojis, Ersetzen nur bei unverändertem Feld, Markierung ersetzen, zu große
    Kunst, Bilderleiste je Öffnen, ẞ, Regler, TalkBack).

- Etappe 70 (A3): Während der Finger zeichnet (`CharArtView.postRender`, alle 40 ms), rechnet
  `renderLive` die Kunst auf einem eigenen Thread (`char-art-render`) aus einer Kopie der Zeichnung
  (der Puffer des laufenden Strichs wird vom nächsten Punkt überschrieben); `renderGeneration`
  lässt nur das neueste Ergebnis auf den Hauptthread (P290). Das Loslassen und jede Änderung am
  Fenster gehen weiter über `render()` sofort und entwerten ältere Hintergrundergebnisse. Die Paints
  für Vorschau und Blase sind Felder (`previewPaint`, `bubblePaint`). `onDetachedFromWindow` beendet
  den Thread. Test `AT/CharArtWindowTest.theLivePreviewIsMadeInTheBackgroundAndFollowsTheFinger`.

## Ablage

> Etappe 74: eigene Ablage für Texte für KI-Chats, dauerhaft gespeichert und sortierbar; Einstellungsseite (74a) und Fenster in der Tastatur über das Komma-Popup (74b).

Der Owner wollte „eine extra Zwischenablage für Prompts, die permanent gespeichert werden und die ich
sortieren kann“, erreichbar über „Eigenes Symbol beim , gedrückt halten“. Nur die Einstellungsseite
bearbeitet, löscht und sortiert; die Tastatur liest und fügt ein und kann mit „Kopiertes als Eintrag
speichern“ vorn einen Eintrag hinzufügen. Nichts hier nutzt das Netz oder protokolliert Titel oder Texte.

- `A/PromptStore.kt`: `Prompt` (`id`, `title`, `text`, seit 77 `folderId`; `toString` zeigt nur Längen), `PromptError`,
  `PromptStoreState` (`READY`, `LOCKED`, `UNUSABLE`), `PromptResult` und `PromptStore`, nach dem Vorbild
  von `SnippetStore`. Datei „agent_prompts.json“ (`AtomicFile`) in `noBackupFilesDir` des
  CE-Kontexts (`credentialContext`, Zugriff über `credentialAccessAllowed`); Format in
  `docs/STORAGE.md` → Ablage (74). Höchstens `MAX_PROMPTS` = 200, `MAX_TITLE` = 60,
  `MAX_TEXT` = 8000 Zeichen (UTF-16), `MAX_STORAGE_BYTES` = 2 MiB. `add` fügt vorn ein,
  `update` behält die Stelle, `delete`, `reorder` (nur genau die gespeicherten Ids, sonst
  `INVALID_ORDER`), `reset`. `load()` ist null bei gesperrtem Gerät oder unbrauchbarer Datei, `read()`
  dann leer, `state()` unterscheidet beides. Alles läuft unter einem Prozess-Lock; `version` zählt
  erfolgreiche Schreibvorgänge. `decode` ist streng (genau die Schlüssel je Format, Version 1 oder 2, Grenzen,
  eindeutige Ids, kein einzelnes Surrogat). Ein Titel ist eine Zeile (`cleanTitle`: Zeilenumbrüche
  werden Leerzeichen, dann getrimmt). Nie wird etwas gekürzt: zu lang ist ein Fehler
  (`TEXT_TOO_LONG`, `TITLE_TOO_LONG`), eine Datei über 2 MiB `FILE_FULL`, ein defektes Zeichen
  `INVALID_TEXT`. Dazu `promptPreview` (höchstens 300 Zeichen, nie mitten in einem Surrogatpaar) und
  `promptLabel` (Titel oder erste nicht leere Zeile), von Seite und Fenster gemeinsam benutzt.
- Ordner (Etappe 77, P312–P317), alles in `A/PromptStore.kt`, `A/prompts/PromptsView.kt`, `AgentEditorController` und
  `K/settings/screens/AgentPromptsScreen.kt`; kein `InputLogic`, `RichInputConnection`, `PointerTracker`:
  - Modell: `PromptFolder` (`id`, `name`), `PromptLibrary` (`folders`, `prompts`; eine globale Liste der Einträge, `promptsIn(filter)`,
    `offersNoFolder()`, `effective(filter)`), `PromptFilter` (`All`, `NoFolder`, `Folder(id)`; `encode`/`decode` für den
    gespeicherten Zustand der Seite, `folderIdForNew`). Neue `PromptError`: `FOLDER_NAME_EMPTY`, `…_TOO_LONG`, `…_INVALID`,
    `…_TAKEN`, `FOLDERS_FULL`, `FOLDER_NOT_FOUND`. Grenzen: `MAX_FOLDERS` = 50, `MAX_FOLDER_NAME` = 40 (UTF-16, nach dem Trimmen,
    kein Steuerzeichen, kein einzelnes Surrogat; Anlegen und Umbenennen verlangen Eindeutigkeit ohne Groß-/Kleinschreibung, der Leser nicht).
  - Schreiben: `add(title, text, folderId = null)`, `update(id, title, text)` (behält den Ordner) und `update(id, title, text,
    folderId)` (verschiebt, der Eintrag behält seinen globalen Platz), `addFolder` (hängt hinten an), `renameFolder`, `deleteFolder`
    (die Einträge bleiben, werden „ohne Ordner“; `PromptResult.moved` zählt sie), `reorderFolders`, `reorderInFolder(folderId, ids)`
    (nur genau die Ids des Ordners, sie tauschen nur ihre eigenen Plätze). Lesen: `loadLibrary()`, `readFolders()`; `read()` und
    `load()` geben weiter alle Einträge.
  - Format: Format 1 (wie bis 76) wird gelesen und geschrieben, solange es keinen Ordner gibt; mit Ordnern schreibt der Speicher Format 2
    (`docs/STORAGE.md`). Ein Eintrag eines fehlenden Ordners und eine höhere Version machen die Datei unbrauchbar (P312).
  - Fenster: `PromptsView` hat eine waagerecht scrollbare Chip-Reihe (`agent_prompts_chips`, Ids `agent_prompts_chip_all`/`_none`,
    Tag der Chips ist der `PromptFilter`) nur mit Ordnern; `filter` (der gezeigte Filter), `onFilterChanged`, `show`/`refresh`
    nehmen eine `PromptLibrary`; der gewählte Chip steht nur im Prozess (`PromptsView.rememberedFilter`, P314). Controller:
    `showPromptFilter` (Chip-Tipp: Schutz und Sperre wie bei `insertPrompt`, dann neu lesen), `saveClipAsPrompt` speichert in den
    gezeigten Ordner.
  - Seite: Chip-Reihe als Filter (`FilterChip`), „Neuer Ordner“, „Ordner umbenennen“, „Ordner löschen“ (Dialog ohne Textfeld, nennt die
    Zahl der Einträge), „Ordner sortieren“ (`confirmFolderOrder`), `FolderEditorCard` (Karte, P315), Ordner-Wähler im `PromptEditorCard`,
    `confirmPromptOrder(store, filter, ids)` sortiert in einem Ordner.
  - Texte: `agent_prompts_folder_*`, `agent_prompts_chip_*`, `agent_prompts_filter_*` in `agent_prompt_strings.xml` (`values/`, `values-de/`).
- Sicherung (Etappe 76, P305–P306): `PromptStore.exportBytes` (die Bytes der gespeicherten Datei; null bei gesperrtem
  Gerät, ohne Datei, unlesbar, ungültig oder leer), `PromptStore.decodeBackup` (strenge Prüfung, null bei Ungültigem)
  und `PromptStore.replaceAll(bytes)` (prüft zuerst, schreibt dann unter dem Lock wie jede andere Änderung, mit
  denselben Ablehnungen). In `K/settings/preferences/BackupRestorePreference.kt`: `PROMPTS_ENTRY_NAME`
  (Name „agent/agent_prompts.json“, genau verglichen, nie als Pfad), `writePromptsEntry` (in `runCatching` im Sichern, ein
  Fehler dort lässt die Sicherung nicht scheitern), `PromptsEntryCollector` (sammelt den Eintrag, während die
  Wiederherstellungsschleife die ZIP-Datei liest; bei mehreren Einträgen zählt der letzte; zu große Einträge
  gelten als ungültig), `restorePromptsFromBackup` (die Entscheidung, wirft nie) mit den Ergebnissen `PromptsRestore`
  `NO_ENTRY`, `APPLIED`, `CORRUPT`, `LOCKED`, `UNREADABLE` und `promptsRestoreMessage` (nur die drei Fehlerfälle haben
  einen Text: `restore_prompts_corrupt`, `…_locked`, `…_unreadable` in `agent_prompt_strings.xml`). Aufgerufen wird sie
  in `restoreLauncher` ganz am Ende des Blocks von `restoreWithRollback`, nach `Database.copyFromDb`; ihr Ergebnis
  wird nach dem Erfolgs-Toast als zweite Meldung gezeigt. Der Text `backup_restore_message` (`strings.xml`,
  `values-de`) nennt die Einträge. Test `app/src/test/java/helium314/keyboard/settings/BackupPromptsTest.kt` (23: Eintrag,
  Sammler, Entscheidungsfunktion, Meldungen; seit 77 fünf Fälle mit Ordnern), daneben `AT/PromptStoreTest.kt` (47) und das bestehende
  `RestoreSafetyTest.kt` (15, unverändert).
- `K/settings/screens/AgentPromptsScreen.kt` (74a): die Seite „Ablage“ unter Einstellungen →
  Leiste & Befehle. Liste mit Titel oder erster Zeile und zwei Zeilen Vorschau, „Neuer Eintrag“,
  „Sortieren“ (`ReorderDialog`; `confirmPromptOrder` speichert nur, wenn die Ids noch passen, sonst
  Hinweis `agent_prompts_sort_changed`), je Zeile „Bearbeiten“ und „Löschen“ (mit Rückfrage), Zähler
  „n von 200“. Der Editor ist eine Karte auf der Seite (`PromptEditorCard`), kein Dialog (P78); beim
  Öffnen verschwinden Liste und Knöpfe. `editingId` und die Felder sind `rememberSaveable`. „Aus
  Zwischenablage einfügen“ liest die Zwischenablage nur auf diesen Tipp und fügt an der
  Schreibmarke ein; passt das Ergebnis nicht in 8000 Zeichen, wird nichts eingefügt. Zurück und
  „Abbrechen“ fragen bei Änderungen „Änderungen verwerfen?“ (P300). Ist die Ablage nicht lesbar,
  steht ein Hinweis statt der Liste; bei einer unbrauchbaren Datei (nicht bei gesperrtem Gerät)
  zusätzlich „Ablage zurücksetzen“ mit Rückfrage (`PromptStore.reset`, P296).
  Der Zugang: `SettingsAreas` (`AgentAreaScreen.kt`) und eine `SettingRow` in `AgentAreaBarScreen.kt`
  (Bereich Leiste & Befehle, nach „Meine Daten & Vorschläge“), `SettingsDestination.AgentPrompts`, Suche
  über `KEYWORDS` in `AgentSearchEntries.kt` (`agent_search_kw_agent_prompts`).
- `A/prompts/PromptsKey.kt` (74b): `POPUP_KEY` (Symbol `agent_prompts_key`, Code `AGENT_PROMPTS`) und
  `shown(KeyboardId)`: nicht bei gesperrtem Gerät, in Passwortfeldern, in Zahlen-Layouts und in Feldern
  mit `IME_FLAG_NO_PERSONALIZED_LEARNING`. Der Layout-Parser kennt Inkognito nicht; das prüft der
  Controller beim Tipp (P301).
- `A/prompts/PromptsView.kt` (74b): das Fenster als Overlay in `AgentKeyboardFrame`, aus Views wie die
  anderen Fenster, Farben aus dem Tastatur-Farbsatz (ohne Farbsatz aus dem Thema). Kopf mit Titel,
  „Verwalten“ und „Schließen“, Statuszeile (`accessibilityLiveRegion`), darunter in einer
  `ScrollView` die erste Zeile „Kopiertes in der Ablage speichern“ und eine Zeile je Eintrag
  (Tag = Id, Beschreibung „Eintrag einfügen: …“). Es hält nur, was es zeigt (`shownIds`); `hide`
  vergisst alles und tut bei geschlossenem Fenster nichts. `setVisibility` meldet dem
  `AgentKeyboardFrame` die geänderte Bedienungshilfe (`updateOverlayAccessibility`). Ohne lesbare
  Ablage zeigt es nur den Hinweis (keine Speichern-Zeile).
- `AgentEditorController` (74b): `promptStoreFactory` (für Tests ersetzbar), `openPrompts`,
  `insertPrompt`, `saveClipAsPrompt`, `isSecretClip`, `openPromptSettings`; das Fenster zählt zu
  `isBlockingInput`, `close()` blendet es aus.
  - `openPrompts`: nicht bei `editor.protection() != null` oder gesperrter Tastatur (Hinweis
    „Die Ablage gibt es in diesem Feld nicht.“, nichts gelesen). Ein Wort in Komposition wird
    wie bei der Zeichen-Kunst so abgeschlossen, wie es dasteht (`InputLogic.commitTyped`); läuft noch
    eine Wischgeste, öffnet es nicht. Dann `close()`, Popups und Vorschlags-Panel schließen,
    `show(load())`.
  - `insertPrompt(id)`: tut nichts, wenn das Fenster nicht sichtbar ist; prüft Schutz und Sperrzustand
    erneut (schließt mit Hinweis), liest die Ablage neu (fehlt der Eintrag: Liste neu laden und
    „Diesen Eintrag gibt es nicht mehr.“) und fügt über `editor.captureAnchor`/`editor.insert` ein wie der
    Rechner und die Zeichen-Kunst (generierter Text, ein Commit, kein Lernen, keine Autokorrektur). Eine
    Markierung wird nicht ersetzt („Erst die Schreibmarke setzen …“); eine Ablehnung wegen Feldänderung
    sagt „Das Feld hat sich geändert. Nichts eingefügt.“ (P297). Nach Erfolg schließt das Fenster.
  - `saveClipAsPrompt`: liest die Zwischenablage nur hier (nicht beim Öffnen). Nur Text, höchstens 8000
    Zeichen, nicht leer; nicht, wenn `isSecretClip` zutrifft; nicht, wenn der oberste Eintrag schon genau
    dieser Text ist („Schon gespeichert.“); sonst `store.add("", text)` (ohne Titel, vorn) und
    Liste neu laden. Fehler werden als Meldung der Statuszeile gezeigt (voll, zu lang, Datei voll,
    defektes Zeichen, sonst „Speichern nicht möglich …“).
  - `isSecretClip` (P299): `ClipVault.isSecretClip(text, Kopierzeit, Markierung)` mit der eigenen
    Kopierzeit und Vertraulich-Markierung des Clips, dazu `ClipGuard.classify(...).keepsOutOfHistory`
    (Geheimnis oder bloßer Einmalcode; ein Satz mit codeähnlicher Zahl bleibt erlaubt, wie im Verlauf).
  - `openPromptSettings` („Verwalten“): bei gesperrtem Gerät nur schließen (P77), sonst schließen und
    `SnipCommand.openSettings(ime, SettingsDestination.AgentPrompts)`.
- Ressourcen: `agent_prompt_strings.xml` (`values/`, `values-de/`), `agent_prompts_ids.xml`
  (Ids des Fensters), Symbol `ic_agent_prompts.xml` (ein Drawable für alle Symbolstile), Suchbegriffe
  in `agent_search_strings.xml`.
- Tests (Robolectric): `AT/PromptStoreTest.kt` (47, seit 77 mit Ordnern: Migration aus Format 1, Format je nach Ordnern, Ordner anlegen, umbenennen, löschen, sortieren, Namensregeln, Grenzen, Sortieren im Ordner, strenges Lesen mit 17 unbrauchbaren Dateien, gesperrt, Sicherung; vorher 25: Dauerhaftigkeit in CE/`noBackupFilesDir`, Reihenfolge
  und Ids, Grenzen, Zeichen, gesperrt, kaputte, zu große und fremde Dateien, Datei voll, Surrogate,
  `state`, `reset`, `reorder`, seit 76 `exportBytes` und `replaceAll`), `AT/PromptsScreenTest.kt` (53, seit 77 mit Ordnern: Chip-Reihe, Anlegen, Umbenennen, Löschen mit Rückfrage, Sortieren, Ordner-Wähler, Standard-Ordner für neue Einträge, Wiederherstellung; vorher 29: Seite, Editor-Karte, Zurück und
  Abbrechen, Wiederherstellung nach Aktivitäts-Neustart, Löschen, Sortieren, gesperrt, Zurücksetzen,
  Einfügen aus der Zwischenablage, Vorschau), `AT/PromptsWindowTest.kt` (35, seit 77 mit Chip-Reihe, Filter, Speichern in den gezeigten Ordner, gemerkter Chip, Schutz beim Chip-Tipp; vorher 25: Popup-Taste und ihre
  Bedingungen, Fenster und Zeilen, Einfügen genau einmal, Markierung, Sperre nach dem Öffnen,
  Doppeltipp, „Verwalten“, „Kopiertes speichern“ mit allen Ablehnungen), dazu `AT/SettingsBarAreaTest.kt`
  (die Zeile im Bereich Leiste & Befehle).

## Begleit-App (Etappe 80)

> Etappe 80: kleine, getrennte App `companion/` nur für das Gerät des Owners; der Spike lädt Qualcomms fertiges Qwen3-4B und lässt es auf dem KI-Chip einen festen Satz korrigieren. Wird nie veröffentlicht.

- Was es ist: ein eigenes Gradle-Projekt (`companion/settings.gradle.kts`, `companion/build.gradle.kts`,
  `companion/app/build.gradle.kts`, `companion/app/src/main/AndroidManifest.xml`), gebaut mit
  `./gradlew -p companion :app:testDebugUnitTest :app:assembleDebug`. Das Haupt-`settings.gradle` kennt es nicht,
  `app/` und `agent-core/` bleiben unberührt. App-ID und Paket `dev.personal.secondhand.companion`, minSdk 27
  (der des SDK), targetSdk 35, nur `arm64-v8a`, nur Debug-Build, einfache Views ohne Compose. Es ist **keine
  Tastaturfunktion**: Die Tastatur spricht die App (noch) nicht an. Alle Dateien liegen unter
  `companion/app/src/main/java/dev/personal/secondhand/companion/` (Tests unter `src/test/java/…`).
- **Der eine SDK-Adapter:** `GeniexPort.kt` ist die einzige Datei, die `com.geniex.sdk.*` importiert. Sie enthält
  die SDK-freien Typen (`InitInfo`, `LoadInfo`, `PullUpdate`, `GenEvent`, `GenProfile`, `NpuException`), die
  Schnittstelle `NpuPort` (`init`, `isModelPresent`, `pull`, `deleteModel`, `load`, `generate`, `stop`, `unload`),
  die Konstanten (`GeniexConstants`: Laufzeit `qairt`, Chipset `SM8750-AC`) und die echte Umsetzung `GeniexAdapter`. Die Modell-ID ist
  ein Parameter jedes Aufrufs (80b/80c: zwei Kandidaten; 80d: `importLocal` übernimmt eine fertig geladene Zip-Datei über `HubSource.LOCALFS`; `inspect` liest die Dateien des Pakets); `listHub` fragt Qualcomms Paketliste ab, `InitInfo.detectedChipset` ist die lokal gelesene Chip-Kennung des SDK. SDK-Fehler werden zu `NpuException` (Klasse, SDK-Code,
  SDK-Text bleiben erhalten). Alles andere arbeitet nur mit `NpuPort`, darum kann ein Fake (`Fakes.kt` in den
  Tests) die ganze App-Logik prüfen.
- App-Logik ohne Android und SDK: `SpikeRunner.kt` (Ablauf: SDK starten, Download, die zwei Tests, Stopp, Entladen,
  Löschen; schreibt jeden Fehler als Zeilen in den Bericht), `PullPlanner.kt` (80b: die geordnete Liste der Pakete `Candidate`, Rat nach einem fehlgeschlagenen Pull, SDK-Fehlercodes `SdkCodes`; nichts wird von selbst geladen), `RunLog.kt` (80f: der Bericht jedes Laufs wird Zeile für Zeile in eine Datei geschrieben und auf die Platte erzwungen; `RunLogFile`, `RunLogStore` mit den letzten drei Läufen und der Zusammenfassung „Bericht kopieren“) und `MemoryGuard.kt` (80e/80f, jetzt mit der dma-buf-Regel, dem Lesen des dma-buf aus `/proc` `DmaBufParse` und `StartupReport`: die Speicherregel vor dem Laden `MemoryGuard`, der Wächter `LoadWatchdog` mit Notbremse, Messwerte `MemSample`/`StoredRun` und deren Text zum Speichern, Schnittstellen `MemoryMeter`/`SampleStore`/`Watchdog`), `Downloader.kt` (80d: eigener fortsetzbarer HTTPS-Download der Paket-Zip-Datei: `ResumePlan` mit Fortsetzen, Range und Content-Range, Platzbedarf; `HttpZipDownloader`; `ZipCheck` prüft das Zip-Verzeichnis und die CRC32 jeder Datei), `MetadataCompat.kt` (80d: wandelt die Metadaten-Datei des neuen Formats in das alte um, Option ohne Download) und `LoadDiagnose.kt` (80c: kleiner JSON-Leser und -Schreiber `MiniJson`, `BundleFacts` aus die Metadaten-Datei des Pakets und der HTP-Konfiguration, QAIRT-Versionsvergleich `QairtVersion`, Filter `LogFilter` für das Protokoll der Laufzeit, `DiagnoseFormatter` mit den Vermutungen H1 bis H5), `ErrorText.kt` (Fehler zu Berichtszeilen, „Auf diesem Gerät nicht
  unterstützt: <Klasse>“), `Metrics.kt` (Tokens pro Sekunde, Vergleich mit dem erwarteten Satz, Zahlenformat),
  `FixedTests.kt` (die zwei festen deutschen Texte), `ReportFormatter.kt` (Kopfzeilen, Testzeilen, gesamter
  Bericht; Datentypen `DeviceHeader`, `TestResult`) und `Support.kt` (die Schnittstellen `Probe`, `Clock`,
  `StepTracker` und der `ReportLog`).
- Android-Teil: `AndroidSupport.kt` (80c: `AndroidDiagnostics` liest Herstellerbibliotheken, öffentliche Bibliothekslisten, DSP-Ordner, Speicher, Ladetests der Bibliotheken des KI-Chip-Teils, Prozessumgebung und das eigene Protokoll über `logcat`; Messungen `AndroidProbe`: nativer Heap, PSS, freier Systemspeicher, Akku-
  temperatur; `PrefsStepTracker`; `SpikePrefs` merkt das geladene Paket, das gewählte Test-Modell und die aufgegebenen Pakete samt Fehler; `AndroidMemoryMeter` liest MemAvailable, SwapFree und den RSS der App aus `/proc`; `PrefsSampleStore` schreibt die Messwerte mit `commit()`; `DeviceInfo`:
  Kopfzeilen und Grund des letzten Prozessendes über `ApplicationExitInfo`) und `MainActivity.kt` (ein Bildschirm,
  alle Views im Code (Liste der Pakete mit Zustand, Dialoge vor jedem Download und nach jedem Fehler), Hochformat, Bildschirm bleibt während eines Schritts an, SDK-Arbeit nie im Hauptthread,
  Modell wird in `onDestroy` freigegeben).
- Tests (`companion/app/src/test/java/…`): `MetricsTest`, `ErrorTextTest`, `PullPlannerTest`,
  `ReportFormatterTest`, `LoadDiagnoseTest`, `DownloaderTest` (mit einem lokalen HTTP-Server des JDK), `MetadataCompatTest` und `RealMetadataCompatTest` (mit den echten Metadaten-Dateien beider Ausgaben unter `src/test/resources/metadata/`), `MemoryGuardTest` (mit `DmabufTest`), `RunLogTest`, `SpikeRunnerTest` (mit `Fakes.kt`); reines JUnit 4, kein Robolectric, kein SDK-Aufruf.
- Regeln der App: Internet nur durch den Download des SDK nach Tipp auf Knopf 1 und Bestätigung; kein Textfeld,
  nur zwei feste Texte; mit `android.util.Log` nur Schrittnamen und Fehlerklassen; keine Statistik; nichts
  wird übernommen oder in ein Feld geschrieben.

## Build, Tests, CI, Docs-System

> Lokaler Build, Prüfbefehle, Workflows und die Docs-Prüfung.

- `tools/dev.py`: `python3 tools/dev.py doctor|build|core-tests|agent-tests|upstream-tests|lint`.
  `build` erzeugt `:app:assembleDebugNoMinify` für ARM64 (`--abi x86_64` für den Emulator) und
  legt APK plus SHA-256 unter `artifacts/` ab (nicht versioniert); die Datei heißt nach den
  enthaltenen ABIs, derzeit `…-universal.apk` (Fallstrick P19). `-Pandroid.injected.testOnly=false`
  verhindert, dass die APK als test-only markiert wird (Fallstrick P16). `agent-tests` läuft über
  `helium314.keyboard.agent.*`, die erzeugten `InputLogicTest`-Fälle (`generated*`,
  `typedCommand*`, `typedEmoji*`, `screenshotChip*`, `symbolDraw*`, `subtypeSwitch*`) und `BatchDeliveryTest`.
  Toolchain und Einrichtung: `docs/BUILD.md`. UI-Tests schreiben gerenderte Vorschauen nach
  `app/build/agent-previews/` (z. B. `builder-send-review.png`, nicht versioniert).
- `tools/render_logo.js` (58): rendert mit dem installierten Playwright/Chromium die Bild-Symbole
  `mipmap-*/ic_launcher{,_round}.png` aus den SVGs in `docs/referenzen/logo/` (P243).
- `tools/build_informal_words.py` (71): kuratiert aus dem entpackten Datenpaket (`informal_lexicon.jsonl`, `words_dictionary_*`, `frequency_priors_*`, `correction_cases_editorial.jsonl`) `app/src/main/assets/agent/informal_words.txt`, die Testdatei `agent-core/src/test/resources/informal_cases.tsv` und den Bericht `docs/referenzen/datensaetze/AUSWAHL.md`: `python3 -I tools/build_informal_words.py <Ordner>`. Die Ausgabe nie von Hand ändern.
- `tools/emoji_mix_table.py` (25a): baut `app/src/main/assets/emoji_mix_pairs.bin` aus der
  Emoji-Kitchen-Metadatei; Aufruf und Quelle in `docs/BUILD.md` → Emoji-Mix-Tabelle.
- Baseline: `upstream-tests` endet mit genau fünf geerbten Fehlern, Liste in
  `docs/VALIDATION.md` und `docs/HANDOFF.md` → Offene Punkte. Jeder weitere Fehler ist neu.
- CI: `.github/workflows/docs-check.yml` führt bei jedem Push den Docs-Check aus.
  `.github/workflows/agent-tests.yml` (seit Build 12) läuft bei Pushes auf `claude/**` und `main`,
  die Code oder `release-notes/**` ändern (die Notizen stecken in der APK und in `UpdatePromptsTest`),
  und von Hand: vier parallele Jobs `agent` (core- und agent-tests), `upstream`
  (voller Lauf), `lint` und `build` (seit 63: `tools/dev.py build`, die ausgelieferte Variante), je
  über `tools/dev.py`. Die Zusammenfassung (Job-Log und Step-Summary)
  listet jeden Fehler als `known` oder `NEW`; nur `NEW` außerhalb der Baseline (die fünf Namen aus
  `docs/HANDOFF.md`, seit 63 ohne Präfix) oder eine Testzahl unter der Mindestzahl des Jobs lässt den
  Job scheitern. Berichte als Artefakt `reports-<job>` (7 Tage). Ein
  neuer Push auf denselben Branch bricht den laufenden Lauf ab.
  `.github/workflows/agent-apk.yml` baut von Hand gestartet die installierbare, mit dem
  Schlüssel des Owners signierte APK (`docs/BUILD.md` → Installierbare APK); dafür liest
  `app/build.gradle.kts` `AGENT_KEYSTORE_FILE`, `AGENT_KEYSTORE_PASSWORD`, `AGENT_KEY_ALIAS` und
  `AGENT_VERSION_CODE` aus der Umgebung. Mit dem Secret `RELEASES_TOKEN` legt er dasselbe Release
  im öffentlichen Repository `RELEASE_REPOSITORY` an (Abschnitt „Updates“); ohne Secret scheitert
  der Lauf auf `main` (seit 63). Von Upstream
  übernommen: `build-debug-apk.yml` (nur manuell) und `build-test-auto.yml` (seit 63 nur noch von
  Hand: der Lauf bei Pull Requests war immer rot, siehe `docs/BUILD.md`).
- Release-Workflow hart (63, P262/P263): `agent-apk.yml` prüft die gebaute APK vor jeder
  Veröffentlichung (`apksigner verify`, SHA-256 des Zertifikats gleich `OWNER_CERT_SHA256`, Paket
  `APP_PACKAGE`, Versionscode gleich Laufnummer, nicht debuggable) und scheitert sonst. Auf `main`
  verlangt er `RELEASES_TOKEN`, einen grünen `agent-tests.yml`-Lauf für einen Commit mit demselben
  Stand der getesteten Pfade, und liest das öffentliche Release danach ohne Token zurück (vier Assets,
  Download-URLs, beide APK-Dateien geladen und gegen ihre Prüfsumme verglichen). Ein erneuter Lauf
  derselben Nummer lädt mit `--clobber` neu hoch. Gradle bekommt
  im Workflow `-Xmx3g`. `app/build.gradle.kts` bricht mit Besitzer-Schlüssel ohne gültige
  `AGENT_VERSION_CODE` ab. `agent-tests.yml` hat seit 63 einen vierten Job `build` (baut die
  ausgelieferte Variante), erweiterte Pfade (`gradle.properties`, `upstream.lock`,
  `agent-apk.yml`), die fünf bekannten Fehler mit exakten Namen und je Job eine Mindestzahl an Tests.
  `CT/WorkflowContractTest.kt` (agent-core, Tests) liest `agent-apk.yml` als Text und hält Tag, Dateinamen,
  Repository und Zertifikat (gegen `docs/BUILD.md`) gleich `UpdateReleases`.
- Gerätetests im Emulator (73, `docs/BUILD.md` → Gerätetests im Emulator): `.github/workflows/device-tests.yml`
  baut die ausgelieferte Variante (`tools/dev.py build --abi x86_64`) und die Test-App, startet auf dem
  GitHub-Runner einen Emulator (Android 14, `google_apis`, x86_64, KVM) und ruft
  `tools/devicetest/devicetest.py` auf (`python3 -I`, nur Standardbibliothek). Das Skript installiert beide
  APKs, stellt die Systemsprachen auf Deutsch und Englisch, macht die Tastatur zur aktuellen
  Eingabemethode, liest die Knoten aller Fenster über die Test-App (`adb shell content read` auf
  `DumpProvider.java`, der `ProbeAccessibilityService.java` fragt), findet darin die Tasten, tippt mit `input tap` und vergleicht
  den Feldinhalt. Ergebnis: `RESULT`- und `SUMMARY`-Zeilen im Job-Log, Tabelle in der Job-Zusammenfassung,
  Artefakt `device-test-<n>` (Aufnahme, Screenshots, Ergebnisdatei). `--control` lässt das letzte Zeichen
  jedes getippten Textes weg; der Kontrolllauf ist grün genau dann, wenn jedes Szenario an einem Vergleich scheitert.
  Die Test-App ist ein eigenes Gradle-Projekt (`tools/devicetest/settings.gradle.kts`,
  `tools/devicetest/build.gradle.kts`, `tools/devicetest/app/build.gradle.kts`,
  `tools/devicetest/app/src/main/AndroidManifest.xml`), gebaut mit
  `./gradlew -p tools/devicetest :app:assembleDebug`; das Haupt-`settings.gradle` kennt sie nicht.
  `MainActivity.java`: vier Felder (Text, Passwort, E-Mail, Zahl) und je eine Spiegelzeile mit dem echten
  Inhalt. `InkProbe.java` (100): malt auf Anfrage (`content call … --method ink`, `DumpProvider.call`) Striche und Tipps
  mit echten Berührungen über `dispatchGesture` des Bedienungshilfe-Dienstes (`canPerformGestures`), notiert Start, geplantes Ende
  und jede Feldänderung (Uhrzeit des Geräts) und liefert das als JSON (`content read …/ink`). `ProbeAccessibilityService.java`: Bedienungshilfen-Dienst nur in der Test-App, listet die Knoten
  aller Fenster als JSON und handelt nie; nur solange ein Dienst aktiv ist, legt
  `MainKeyboardView.setKeyboard` die Tasten als Knoten an (P294). `FakeAutofillService.java`: Autofill-Dienst nur in
  der Test-App für das Szenario `autofill_inline` (73c), bietet seit 79 drei feste erfundene Datensätze mit den Titeln „Test 73c“,
  „Test 73c-2“, „Test 73c-3“ in dieser Reihenfolge als Inline-Vorschläge an (das Szenario prüft die Reihenfolge der Chips von links nach rechts
  über die Titel-Knoten und tippt den linken; nicht gelaufen),
  wird nur für dieses Szenario per `settings put secure autofill_service` gewählt und danach abgewählt (P311).
  Diagnose (Untersuchung clipboard_pill): `foreground_info` in `devicetest.py` schreibt als INFO-Zeilen die fortgesetzte Aktivität
  (`dumpsys activity activities`) und die letzten Aktivitätsstarts (nur Komponentennamen aus `START u0` im Log des ActivityTaskManager),
  wenn eine Wartezeit abläuft oder `field_…`/`copy_…` fehlt, sowie einmal nach dem Home-Druck in `_open_settings_and_switch_on`. Keine Feldinhalte.
- Docs-System: `scripts/docs_sync.py` (Konfiguration `scripts/docs_sync.json`),
  `scripts/install-hooks.sh` (optionaler Pre-Commit-Hook), `.claude/hooks/session-start.sh`
  (registriert in `.claude/settings.json`). Bedienung: `docs/HANDOFF.md` → Arbeitsweise.
  `.claude/skills/product-initiative/SKILL.md` ist der Projekt-Skill zur Regel „Verantworte das Produkt“ in
  `CLAUDE.md` (Auftrag durchdenken, eigene Ideen einordnen, wie der Owner prüfen, Bericht in vier Teilen); er
  enthält keine eigenen Regeln. Seit 2026-11-21 mit Abnahme-Zeilen, Kritiker vor dem Bau und der Tabelle „Aussage →
  Nachweis“ (übernommen aus dem Paket „Product Initiative Suite“ des Owners; dessen Design-Datenbank, Nachweis-Skript
  und Fremddienste sind bewusst nicht übernommen). `.claude/skills/product-initiative/evals/evals.json` mit den
  Vorlagen `dark-toolbar.html`, `dialog-zustaende.json`, `toolbar-profil.json`, `trackpad-profil.json` sind die 14
  Verhaltensfälle des Owners aus diesem Paket, unverändert kopiert (das Feld `skill_name` nennt noch die Suite); sie
  sind noch nie ausgeführt worden und dienen als Messlatte für einen späteren Vergleich mit und ohne Skill.

## Fallstricke

> Fehler, die schon passiert sind oder nahelagen: bei jedem Review prüfen.

- **P1** Eine Freigabe gilt genau einmal und nur für die unveränderliche Quelle bzw. Definition,
  die angezeigt wurde. Eine verbrauchte oder fremde Freigabe darf nie einen zweiten Lauf starten.
- **P2** Läufe: den Job `LAZY` installieren, bevor ein synchroner oder reentranter Abbruch
  eintreten kann. Die Generation vor `cancel` erhöhen. Eine alte Antwort darf nie einen neuen Lauf
  beenden oder etwas veröffentlichen.
- **P3** `ApplyOutcome.UNKNOWN` verwirft die Caches und blockiert die Eingabepfade, bis ein
  autoritativer Refresh oder Restart kommt. Eine Ersetzung ist genau ein Commit, nie
  Löschen plus Einfügen.
- **P4** Nicht abbrechbare Journal-IO kann das Zeitbudget überschreiten. Die späte
  Veröffentlichung muss trotzdem gesperrt bleiben (Job-, Epoch- und Policy-Prüfung).
- **P5** Swipe: `PendingBatchGate` hält Worker- und UI-Teil unter derselben Epoch/Lock gesperrt.
  Beide Teile nie getrennt sperren oder freigeben.
- **P6** UI-Zustände direkt im Elternscope erfassen (`displayed…`). Eine reine
  null→null-Konfiguration hatte die Recovery- und Fehleranzeige sonst nicht aktualisiert.
  Speichern braucht `key(binding)` bzw. die angezeigte Generation.
- **P7** Speichern im Builder erfasst Proposal-Flag, Generation und Revision unveränderlich und
  prüft die Objektidentität. Sonst kann ein vorgemerkter Klick nach Verwerfen oder Inkognito
  einen verworfenen Entwurf speichern.
- **P8** Kein `PackageManager`-Aufruf im Start-Log von `App.kt`: Die späte Abfrage warf nach dem
  Robolectric-Teardown eine Hintergrund-Exception (`NameNotFoundException`). Nutze `BuildConfig`.
- **P9** Kein Lock-Finish-Cleanup darf einen CE-Pfadgetter anfassen, solange das Gerät gesperrt
  ist.
- **P10** Private Eingabefelder (Builder-Prompt, Workflow-Name, Literal) nutzen unmaskierte
  Passwort-IME-Semantik, damit die eigene Tastatur sie nicht lernt. Prompt und ungespeicherte
  Namen dürfen nicht in den Saved State.
- **P11** Tests: App-Unit-Tests laufen mit `forkEvery=1` (ein Java-21-Worker pro Klasse).
  Robolectric hält sonst Wörterbuchkopien bis zum Prozessende und füllt den Datenträger (ENOSPC).
- **P12** Tests: Neue native Tests brauchen einen Handler-Shadow, der `true` (Boolean, nicht
  `Unit`) liefert, und synchronisierte Runnable-Queues. `awaitAgentUi` liefert echte Callbacks.
- **P13** Tests: JUnit4-Methoden müssen `Unit` liefern. `runBlocking` mit `assertFails` kann
  versehentlich `Throwable` zurückgeben, dann läuft der Test nicht.
- **P14** Compose-Tests: `isEnabled()` als Matcher in `onAllNodes` verwenden. Menüpunkte vor dem
  Klick ggf. mit `performScrollTo` sichtbar machen. Das Label eines TextFields gehört zur
  gemergten Textsemantik, leeren Inhalt daher über `SemanticsProperties.EditableText` prüfen.
- **P15** Dialog-Bitmaps: vor der `ShadowDialog`-Aufnahme den sichtbaren Dialog assertieren und
  idle warten. `captureToImage` wartete im Robolectric-Fenster sonst auf einen Redraw, der nie
  kam.
- **P16** Build: Ein injiziertes ABI (`android.injected.build.abi`) markierte die APK als
  test-only. Ein Telefon lehnt so eine APK bei der Installation aus dem Dateimanager ab, `adb`
  verlangt `-t`. `tools/dev.py` setzt deshalb `android.injected.testOnly=false`. Nach jedem
  Build-Umbau mit `aapt2 dump badging` prüfen.
- **P17** Ein neuer Konstruktor-Parameter am Ende von `AgentEditorController` fängt das
  nachgestellte Lambda bestehender Aufrufe ein (Tests übergeben so den Provider). Neue
  Abhängigkeiten als ersetzbare Eigenschaft anlegen (`toolLauncher`).
- **P18** Android 15 zeichnet die Navigationsleiste über das Tastaturfenster. Die Tastatur hat
  dort leeren Rand, ein Overlay in voller Höhe legt seine untersten Knöpfe unter die Leiste
  (im Gerätetest waren „Replace selection“ und „Copy result“ nicht antippbar). Robolectric
  zeigt das nicht. Neue Overlays in `AgentKeyboardFrame` einhängen und auf einem Gerät ansehen.
- **P19** Nach jedem Build die Datei installieren, die `tools/dev.py` als `APK:` ausgibt, und
  nach der Installation prüfen, dass die Änderung sichtbar ist. Eine alte Datei im Ordner
  `artifacts/` sah im Gerätetest wie ein wirkungsloser Fix aus.
- **P20** Einfügen an der Schreibmarke prüft Token, Position, „Editor antwortet“ und „nichts
  markiert“, aber nicht den umgebenden Text (der wird bewusst nicht gelesen). Ändert die
  Host-App den Text, ohne die Schreibmarke zu bewegen, oder ist eine Bewegung noch nicht
  gemeldet, landet die Einfügung an der alten Position im neuen Text. Deshalb gibt es Einfügen
  nur nach Vorschau und eigenem Klick, nie automatisch.
- **P21** `ExpandableBinaryDictionary.getWordPropertiesForSyncing` wartet 100 ms und liefert
  danach ein leeres Feld; „nichts gelernt“ und „nicht rechtzeitig gelesen“ sind damit nicht zu
  unterscheiden. Für alles, was der Nutzer als vollständig ansehen soll (Zählen, Export), nur
  `readAllWordProperties` verwenden.
- **P22** `PersonalizationHelper.removeAllUserHistoryDictionaries` wirft die Wörterbücher aus dem
  gemeinsamen Cache. Solange das Lernen an bleibt, behält die Tastatur ihre alten Objekte und
  ein späterer Zugriff erzeugt ein zweites Objekt auf derselben Datei. Zum Leeren bei
  eingeschaltetem Lernen deshalb `clearAndWait` auf dem vorhandenen Objekt verwenden.
- **P23** Ausgabegrenzen der Provider schließen Denk-Token ein. Eine zu knappe Grenze lässt
  bezahlte Anfragen als „abgeschnitten“ enden. Seit Etappe 59 skaliert die Grenze mit der Eingabe
  (`ModelBudget.outputTokens`, P245); ein abgeschnittenes Ergebnis ist `CUT_OFF`.
- **P24** `InputConnection.getSelectedText` liefert `null` sowohl bei „nichts markiert“ als auch,
  wenn die App nicht rechtzeitig antwortet. Wer daraus „keine Auswahl“ liest, ersetzt im
  schlimmsten Fall später doch eine Auswahl. Vor einem Commit, der sich auf „nichts markiert“
  stützt, erst eine Antwort des Editors verlangen (`getTextBeforeCursor(1, 0) != null`).
- **P25** `commitText` ersetzt einen Composing-Bereich im Editor, wo auch immer die Schreibmarke
  steht. `insertTextIfCursorUnchanged` ist nur zusammen mit der Prüfung
  `hasActiveComposition` in `InputLogic` sicher; nie direkt aufrufen.
- **P26** Die Aktionstaste (Enter) nimmt ihren Hintergrund aus `keyBackground`, nicht aus
  `functionalKeyBackground` (`Colors.selectAndColorDrawable`). Eine Form für `state_active`
  muss deshalb im Selektor der normalen Tasten stehen.
- **P27** Tastenhöhen werden abgeschnitten, nicht gerundet (`Key`-Konstruktor). Ein rechnerisch
  exakter Skalenwert ergibt leicht eine Taste, die 1 px zu niedrig ist; `FlatStyle` nutzt deshalb
  Werte knapp über dem exakten Verhältnis, und `FlatStyleTest` pinnt die Pixel.
- **P28** Der seitliche Rand der Tastatur ist in allen Stilen 8 % der Breite mal der
  Seitenrand-Einstellung, und die ist im Hochformat standardmäßig 0. `FlatPreset` setzt sie auf
  0,073 (7 px bei 1200 px). Den Rand nie im Stil selbst verkleinern: dann wirkt die Einstellung
  des Nutzers nicht mehr.
- **P29** Ein gerendertes Bild aus Robolectric (native Grafik) ist kein Gerätetest: Es zeigt
  Roboto statt der Systemschrift des Telefons und weder Leiste noch Navigationsbereich.
- **P30** Ein Layout-Wunsch während eines Layout-Durchlaufs (`doOnLayout` → `requestLayout`)
  kostet auf dem Gerät einen zweiten Durchlauf und geht in einem Test ohne Fenster ganz
  verloren. Abstände deshalb über Gewichte lösen, nicht nachträglich setzen (`FlatStrip`).
- **P31** Views ohne Fenster lösen die Leserichtung nicht auf: relative Icons an `TextView`
  fehlen im gerenderten Testbild, bis die Wurzel eine feste `layoutDirection` hat.
- **P32** `Settings.getValues()` ist ein Singleton und überlebt einen Robolectric-Test. Ein Test,
  der einen anderen Stil lädt, muss danach die Standardwerte neu laden, sonst sehen spätere Tests
  im selben Prozess z. B. das Panel im flachen Look.
- **P33** `clipToOutline` beschneidet nur mit Hardware-Rendering. In einem Test-Bitmap bleiben
  Bilder in runden Karten eckig; das Flag prüfen, nicht die Pixel.
- **P34** In einem `apply { }` auf einer `TextView` meint `text` die Eigenschaft der View, nicht
  eine gleichnamige Variable der umgebenden Klasse. Farbvariablen deshalb nie `text` nennen.
- **P35** Horizontale `LinearLayout`-Zeilen richten ihre Kinder an der Grundlinie aus. Ein
  zweizeiliger Knopf rutscht dadurch nach unten und wird abgeschnitten; für Chip-Zeilen
  `isBaselineAligned = false` setzen.
- **P36** Ein Drawable wird erst durch eine zustandsabhängige Tönung „stateful“ und behält bis
  zur nächsten Zustandsänderung seinen alten, leeren Zustand. Nach `setTintList` deshalb
  `drawable.state = view.drawableState` setzen, sonst erscheint die Farbe des Aus-Zustands.
- **P37** `AllColors` kennt weder „ohne Tastenrahmen“ noch den aktivierten Zustand von
  Leistentasten. Ein eingebauter Farbsatz darauf muss beides selbst behandeln (`FlatStyle.colors`).
- **P38** `BitmapFactory` ignoriert die EXIF-Drehung, `ImageView.setImageURI` nicht (ab Android 9).
  Wer `setImageURI` durch eigenes Dekodieren ersetzt, nimmt `ImageDecoder`.
- **P39** Ein Intent an eine schon laufende Aufgabe, der sich nur in den Extras unterscheidet,
  holt sie bloß nach vorn. Für ein Sprungziel in den Einstellungen `FLAG_ACTIVITY_CLEAR_TASK`.
- **P40** Robolectric (Native-Grafik) zeichnet einzeiligen Text mit horizontal zentrierter
  Gravitation nicht (`singleLine` plus `gravity=center`); im Render-Test fehlt der Text, auf
  Geräten nicht. Für Render-Tests die Beschriftung links ausrichten, wenn die Ansicht den Text
  ohnehin umschließt.
- **P41** Bekommt eine Kotlin-Funktion mit Lambda-Parametern einen weiteren Lambda-Parameter mit
  Standardwert ans Ende, bindet ein bisheriges nachgestelltes Lambda an den neuen Parameter.
  Aufrufer dann mit benanntem Argument (`onDone = { … }`) schreiben.
- **P42** Ein Präfix, der in `symbols_word_separators` steht (z. B. `/`, `!`, `?`, `.`)
  beendet das Wort: Der Befehlsname wird dann als normales Wort getippt und beim
  Leerzeichen autokorrigiert (`math` → `Mathe`). Deshalb nur `CommandSyntax.ALLOWED_PREFIXES`.
  Außerdem korrigiert der zweite Zweig von `shouldBeAutoCorrected` auch Wörter mit `@`, wenn der
  Vorschlag gut genug ist; der Schutz für Befehle steht deshalb in der Schlussbedingung von
  `hasAutoCorrection`, nicht neben der `@`-Regel.
- **P43** Ein Overlay nicht direkt nach einer Änderung im Feld öffnen, die die App zu einem
  Neustart der Eingabe bewegen kann (Feld wird leer, Vorschläge der App ändern sich):
  `onStartInputView` → `AgentEditorController.startEditor` schließt alle Overlays. Erst öffnen,
  dann mit einem eigenen Klick ändern. Am Telefon beim Rechner beobachtet (Ursache vermutet).
- **P44** Der Bedienungshilfe-Dienst (`agent_screen_reader.xml`) abonniert absichtlich keine
  Ereignisse; so weckt ihn das System nie und er liest nur auf Befehl. Wer `accessibilityEventTypes`
  hinzufügt, bricht die Zusage an den Owner; `ScreenReaderServiceTest` prüft die Attributliste.
- **P45** `java.time` ist ohne eingeschaltetes Desugaring erst ab API 26 sicher (minSdk 21);
  Datumsrechnung deshalb über `CivilDate`.
- **P46** Ein neuer Parameter an `SearchScreen` oder `SearchSettingsScreen` muss vor `content`
  stehen, sonst brechen alle Aufrufe mit nachgestelltem Lambda (P41). `captureToImage` läuft bei
  Compose-Seiten in Robolectric in einen Timeout (wie P15): die `decorView` selbst zeichnen.
- **P47** Eine Anfrage aus der Leiste (`@ki`, `@translate`) gehört zu genau einem Befehlstext an
  einer Schreibmarke. Chips werden bei jeder Leistenaktualisierung neu gebaut, deshalb hängt die
  Antwort an `onChange` der zuletzt gebauten Chips, nie an der ersten Chip-Zeile. Wer den Zustand
  an anderer Stelle hält oder den Schlüssel lockert, zeigt alte Antworten an fremden Stellen.
- **P48** Die Update-Prüfung folgt Redirects selbst (`followRedirects(false)`): OkHttp würde
  sonst auch auf `http://` folgen dürfen. Nur Assets mit der erwarteten Download-URL zulassen
  und vor der Installation immer Paketname und Signaturzertifikate gegen die installierte App
  prüfen; die SHA-256 schützt nur gegen Übertragungsfehler, nicht gegen ein manipuliertes
  Release.
- **P49** Leertaste: Doppeltippen und „Punkt plus Leerzeichen“ dürfen nie beide laufen; deshalb
  schaltet eine Doppeltipp-Aktion `mUseDoubleSpacePeriod` ab, statt in `InputLogic` einzugreifen.
  Der erste Tipp schreibt sein Leerzeichen sofort (kein Warten beim Tippen). Codes ohne
  Tastendruck (Leiste, Popup-Tasten, eigene Aktionen; kein `onPressKey` der Leertaste davor)
  zählen nicht als Tipp; Koordinaten taugen nicht dafür, weil PointerTracker sie außerhalb der
  Buchstaben-Layouts weglässt. Einmal-Aktionen beim senkrechten Wischen laufen beim ersten
  Schritt, beim seitlichen am letzten Rastpunkt (Etappe 33), und danach nicht mehr bis zum
  Loslassen; eine Aktion, die ein Overlay öffnet, blockiert die restlichen Schritte über
  `isBlockingInput`. Ein Wisch endet ohne `onReleaseKey` (wie im Upstream); deshalb setzen
  `onSpaceSwipe`, `onSpaceSwipeDistance` und `SPACE_HOLD` den Doppeltipp-Zähler zurück.

- **P50** Ein Kotlin-Zugriff auf `Defaults.PREF_KEY_GAP_SCALE[0]` (bzw.
  `PREF_KEYBOARD_HEIGHT_SCALE[0]`) aus einer anderen Datei ließ den Compiler ein ungültiges
  `Defaults.<clinit>` erzeugen (`VerifyError: Bad local variable type`, jeder Robolectric-Test
  bricht ab, auch bei vollem Neubau). `MyDesigns` nutzt deshalb `1f` (Standard im Hochformat).
  Bei so einem `VerifyError` zuerst den letzten neuen Zugriff auf `Defaults` prüfen.
- **P51** `InputLogicTest` ersetzt `Handler.sendMessage` und `sendMessageDelayed` für alle
  Handler und gibt die Nachrichten an `LatinIME.mHandler`. Fork-Code, der dort läuft, plant
  deshalb mit `postAtTime` (`ScreenshotChip.postIn`, auch `onMain` für Ergebnisse vom
  Hintergrund-Thread), sonst landet sein Runnable im falschen Handler. In `apply { }` auf einem `EditorInfo` meint `contentMimeTypes` das Feld des
  `EditorInfo` (wie P34); die Shadow-Variable heißt deshalb `acceptedMimeTypes`. Robolectric:
  `FileProvider` merkt sich die Pfade der ersten Test-App; ein späterer Test derselben Klasse
  bekommt dann für seine Dateien eine Ausnahme. In Tests nur einmal pro Klasse `getUriForFile`.
- **P52** Ein aus `assets` entpacktes Wörterbuch heißt `<typ>.dict` (ohne `_`); die Suche
  `getCachedDictForLocaleAndType` vergleicht `name.substringBefore("_")` und findet es deshalb
  nicht. Wer bei „nicht gefunden“ entpackt, schreibt sonst bei jedem Aufruf eine Datei neu, die
  ein geladenes Wörterbuch per mmap hält (SIGBUS-Gefahr). Vorher auf `<typ>.dict` prüfen und nur
  über temporäre Datei plus Umbenennen schreiben.

- **P53** Sprachwörter (`@sp`) dürfen nie einen Befehl überdecken: Ein Befehlsname, der mit einem
  Sprachkürzel beginnt, schluckt es als angefangenen Namen (deshalb heißt der Befehl `@lang` und
  nicht `@sprache`, sonst zeigte `@sp` nur „@sprache“). Neue Befehlsnamen gegen `SHORT_FORMS` und
  ISO-Codes prüfen. Ein `claim` kommt immer zuletzt (nach exakt, Präfix, Tippfehler eines Namens),
  `LanguageMatcher` nimmt Aliasse nah an Befehlsnamen nur exakt, und weder ein `claim` noch ein
  Tippfehler übernimmt das Argument eines früheren echten Befehls (P56).
- **P54** Sprache erst nach dem Entfernen umschalten: `LatinIME.switchToSubtype` ruft
  `InputLogic.onSubtypeChanged` (`finishInput`/`startInput`) und lädt die Tastatur neu. Der Tipp
  ersetzt deshalb zuerst den Befehl (ein Commit, Prüfung von Text und Schreibmarke) und schaltet
  nur bei Erfolg um; umgekehrt liefe das Ersetzen gegen einen frisch gestarteten Eingabezustand.
  `switchToSubtype` braucht ein aktuelles `EditorInfo` (Robolectric ohne Eingabe: NPE in
  `saveSubtypeForApp`), daher prüft `LanguageCommandTest` den Wechsel über einen Test-Host und
  `InputLogicTest` den echten Weg.
- **P56** Die Rückwärtssuche von `CommandSyntax.parse` nimmt sonst den letzten Treffer auf der
  Zeile. Seit es unscharfe Treffer gibt (`claim`, Tippfehler eines Namens), fand sie im Argument
  eines echten Befehls ein Sprachwort oder einen vertippten Namen (`@ki schreib an @isa`,
  `@translate "…" @es`) und warf dessen Chips und `protectsArgument` weg. Schwache Treffer zählen
  deshalb nur ohne früheren Befehl mit exaktem Namen auf derselben Zeile. Wer neue unscharfe
  Treffer einführt, markiert sie in `isWeak`. Erwähnungen bleiben ein Kompromiss: Je lockerer
  die Regeln, desto öfter blitzt beim Tippen eines Namens ein Chip auf (`@ale…` → Deutsch über
  „alemán“); deshalb 2 Zeichen nur exakt, Präfix ab 3, Tippfehler ab 4 mit gleichem
  Anfangsbuchstaben, keine Tippfehler für Länder und Codes, nach dem Leerzeichen nur exakt.
- **P60** `DrawableContainer` (Zustandslisten wie `btn_keyboard_key_*.xml`) merkt sich nach
  `setColorFilter` – auch mit `null` – für immer, dass es einen Filter gibt, und gibt Kindern,
  die es später zum ersten Mal wählt, nur noch diesen Filter statt der Tönung. Einen Filter
  für ein einzelnes Zeichnen deshalb nur auf `drawable.current` setzen und danach den alten
  Filter dieses Kindes zurückgeben (`RgbKeyPainter.tintBackground`).
- **P61** Werte im `companion object` werden in der Reihenfolge ihrer Deklaration erzeugt. Ein
  `val DEFAULT = RgbSettings()` vor `DEFAULT_PALETTE` gab der Vorgabe eine `null`-Palette und
  jedem Zugriff einen `NoClassDefFoundError`. Vorgaben, die andere Konstanten brauchen, zuletzt
  deklarieren.
- **P62** Eine Compose-Seite mit endloser `withFrameNanos`-Schleife (Vorschau der RGB-Seite)
  wird mit der automatisch laufenden Testuhr nie „idle“, `waitForIdle` liefe in einen Timeout
  (vorsorglich vermieden, nicht beobachtet). Tests schalten dafür die Animationsskala auf 0 (die
  Seite steht dann still) oder setzen `mainClock.autoAdvance = false` und gehen mit
  `advanceTimeBy` weiter.
- **P63** Während `View.onDetachedFromWindow` gilt die View noch als angehängt
  (`isAttachedToWindow` ist erst danach `false`). Wer dort „sichtbar?“ neu prüft, lässt Bilder
  und Empfänger weiterlaufen. `RgbLighting.onDetached` stoppt deshalb ausdrücklich.
- **P64** Ein Farbfilter auf einer Drawable (auch über `ImageView.setColorFilter`) ersetzt ihre
  Tönungsliste ganz: Zustände wie „aktiviert“ oder „gedrückt“ sind dann unsichtbar, und
  `colorFilter = null` nimmt auch einen Filter weg, den jemand anders direkt auf die Drawable
  gesetzt hat. Leistenknöpfe mit Tönungsliste deshalb über eine abgeleitete `ColorStateList`
  färben (`RgbStrip.litTint`), nicht über einen Filter.
- **P65** Emoji- und Zwischenablage-Ansicht enthalten je eine eigene `MainKeyboardView` für die
  untere Reihe (`bottom_row_keyboard`). Code in `MainKeyboardView` läuft also auch für diese
  Views; was nur für die Haupttastatur gilt, muss die View prüfen (`R.id.keyboard_view`).
- **P90** Ein Shader auf einem gezeichneten Bitmap (ALPHA_8-Maske mit `BitmapShader`) liegt bei
  Skia im Raum des Bitmaps, nicht des Canvas: `drawBitmap(mask, x, y, paint)` verschob das Licht
  im Test um genau die Tastenposition. Masken deshalb bei (0, 0) eines dorthin verschobenen
  Canvas zeichnen und die Matrix des Shaders je Taste setzen; dann sind beide Deutungen gleich.
  Weil eine Display-List einen Shader bis zum Zeichnen halten kann, bekommt jede Taste eigene
  Shader-Objekte (`RgbTexture.drawMask`), nie einen gemeinsamen mit wechselnder Matrix.
- **P91** `RgbNoise.noise` ist nur mit Zweierpotenzen als Periode periodisch (Bitmaske); eine
  Kachel mit Periode 3 hatte eine sichtbare Naht. Kacheln aus Rauschen immer mit 2, 4, 8 … Zellen.
- **P92** Im scrollbaren Inhalt nimmt die Seite eine Ziehbewegung, sobald die Bewegung die
  Schwelle überschreitet und das Ereignis nicht verbraucht ist. Der Kurven-Editor verbraucht
  deshalb jedes Ereignis einer Geste, die auf einem Punkt beginnt, schon vor der Schwelle;
  sonst scrollt die Seite mit und der Punkt landet daneben.
- **P93** Ein steiles Farbfeld (scharfe Ränder der Tinte) zeigt im 48er-Raster sichtbare Blöcke,
  weil bilinear nur zwischen den Rasterpunkten mischt. Felder so glatt halten, dass Nachbarwerte
  bei 96 Spalten höchstens etwa 45 von 255 auseinanderliegen (`texturesHaveNoJumpsInSpace`);
  Feines gehört in eine Kachel.
- **P94** OKLab-Mischung zwischen Gegenfarben (Rot–Blau) läuft durch Grau; Mischen in OKLCH
  (Farbton über den kurzen Weg) hält die Mitte farbig. Glätten des Regenbogens in OKLab erzeugte
  Farben außerhalb von sRGB, deren Abschneiden neue Kanten machte; deshalb in sRGB glätten.
- **P105** Ab API 33 hat ein `BitmapShader` einen eigenen Filtermodus, der vor dem
  `FILTER_BITMAP_FLAG` der Paint gilt; Shader für Licht und Kacheln setzen ihn deshalb
  ausdrücklich auf linear (`RgbTexture.bitmapShader`). Robolectric mit SDK 28 zeichnet einen
  `BitmapShader` immer ungefiltert (Blöcke): Bildtests der Weichheit laufen mit SDK 36.
- **P106** Texturkacheln entstehen im Hintergrund (`RgbTiles`). Ein Test, der Pixel einer
  Textur prüft, muss vorher `RgbTiles.awaitIdle()` aufrufen und den Main-Looper laufen lassen
  (Zuhörer, Neuzeichnen); sonst sieht er je nach Tempo des Rechners das Bild ohne Detail.
  Zeichnen darf nie `RgbTiles.build` aufrufen (macht die Kachel auf dem aufrufenden Thread).
- **P107** Die Zeit der Texturmuster darf nur dort umbrechen, wo `RgbPatterns.field` sich
  genau wiederholt (`RgbPatterns.flowPeriod`: ganze Drehungen jedes Sinus, Vielfache von 64
  Rauschzellen). Wer einen Zeitfaktor in `field` ändert, muss die Periode anpassen;
  `RgbPatternsTest.theFlowRepeatsExactlyAtItsPeriod` schlägt sonst fehl. Ebenso müssen alle
  Drift-Geschwindigkeiten in `RgbOverlay.transform` mal `DRIFT_PERIOD` ganzzahlig bleiben.
- **P108** Das Licht kommt ein Bild später aus dem Hintergrund (`RgbWork`). Pixeltests zeichnen
  deshalb zweimal: erstes Bild (bestellt Raster und Kacheln), `RgbWork.awaitIdle()`,
  `RgbTiles.awaitIdle()`, Main-Looper laufen lassen, `invalidateAllKeys()`, zweites Bild. Ohne
  `invalidateAllKeys` zeichnet die `KeyboardView` beim Software-Zeichnen nur ihren alten Puffer;
  der Zuhörer übernimmt das im Betrieb nur für eine sichtbare Tastatur.
- **P109** Liest eine Composable einen Zustand, wird sie bei jeder Änderung neu
  zusammengesetzt. Beim Ziehen eines Kurvenpunkts darf deshalb nur die Vorschau den Zwischenstand
  lesen (`draft` in `LivePreview`), nicht `AgentRgbScreen`; sonst baut jede Bewegung die ganze
  Seite neu (`RgbSettingsScreenTest.draggingACurvePointDoesNotRecomposeThePage`).
- **P66** Ein Chip, der Text entfernt und danach die Sprache umschaltet, leert oft das Feld; viele
  Apps starten die Eingabe dann im selben Feld neu. HeliBoards `onStartInputInternal` wendet dabei
  Hint-Locales und den gespeicherten App-Subtyp erneut an (`SubtypeState.getSubtypeForLocales`)
  und schaltete sofort zurück, z. B. auf Englisch ohne „ä“ unter „a“. Die Sprachtaste leert kein
  Feld und war deshalb nicht betroffen; der Umschaltweg selbst war schon derselbe. Deshalb gilt
  eine Befehlswahl für dasselbe Feld (`SubtypeChoice`). Robolectric bildet nur den Neustart mit
  Hint-Locale nach; ob das Xiaomi-Telefon genau so neu startet oder das System zusätzlich
  `onCurrentInputMethodSubtypeChanged` ruft, ist ungeprüft. Grenze: Die Feldidentität ist Paket,
  `fieldId` und Eingabeklasse; viele Felder (Compose, WebView, Chats) teilen `fieldId` 0 bzw. eine
  ID über alle Chats, dort hält die Wahl auch im nächsten Chat derselben App. Ein Drehen des
  Bildschirms kann über `onStartInputInternal(null, …)` die Wahl löschen (altes Verhalten).
- **P67** Ein Telefon hat einen Vibrationsmotor: „links nach rechts“ ist nicht darstellbar, nur
  Stärke und Rhythmus (`HapticChain`). In einer `VibrationEffect.Composition` zählt die
  Verzögerung ab dem Ende des vorigen Primitivs, nicht ab dessen Start. Ohne Amplitudensteuerung
  ignoriert Android Amplituden still; deshalb vorher `hasAmplitudeControl` fragen und sonst
  gleich starke Pulse nehmen, statt eine Stärke vorzutäuschen.
- **P80** Die Locale-Ordner unter `files/dicts/` werden nach Wörterbüchern durchsucht
  (`getCachedDictsForLocale`, `getDictionaryLocales`, `DictionaryFactory`): jede Datei darin zählt,
  auch eine halb geschriebene. Temporäre Dateien beim Entpacken gehören deshalb nach
  `files/dicts_extracting/` (selbes Volume, also `renameTo` möglich), nicht in den Zielordner und
  auch nicht als Unterordner von `files/dicts/` (der würde als Sprache gezählt).
- **P84** Die Versionsnummer des Forks ist die Laufnummer des Workflows (1, 2, …), weit unter
  HeliBoards Versionscodes. `AppUpgrade` lief deshalb bei jedem Update alle alten Migrationen
  (`oldVersion <= 3901` u. a.) erneut; eine davon setzt die Tastenabstände auf 1,75 und zerstörte
  so das eigene Design des Owners. Der Fork wird nie über HeliBoard installiert (eigenes Paket),
  also gelten diese Migrationen nie: `FORK_UPGRADES_ONLY` überspringt sie. Folgen: Eine neuere
  Upstream-Version bringt neue Migrationen mit, die ebenfalls übersprungen werden; sie gehören dann
  von Hand in den Fork-Zweig. Das Wiederherstellen einer alten HeliBoard-Sicherung
  (`BackupRestorePreference` ruft `checkVersionUpgrade`) überführt deren alte Schlüssel nicht mehr.
  Bis Build 12 setzte jedes Update außerdem die angepinnten Leistenknöpfe zurück (`<= 2000`) und
  „Satzzeichen vorschlagen“ auf das Gegenteil der Wortvorhersage (`<= 3201`); das ist nicht
  reparierbar, weil der vorige Wert unbekannt ist.
- **P81** Der Medienspeicher meldet mehrere Screenshots im Entprellfenster als eine Änderung; eine
  Abfrage nur nach dem neuesten verliert die übrigen. `ScreenshotChip.finder` liefert deshalb
  eine Liste, und jeder Screenshot wird einzeln für den Verlauf entschieden. Dabei kommt nur in
  den Verlauf, was nach dem Start des aktuellen Observers entstand (`watchingSinceMillis`, auf
  Sekunden abgerundet wie `DATE_ADDED`): Ein älterer, nie gesehener Screenshot (z. B. vom
  Sperrbildschirm, während die Tastatur nicht lief) wurde nie gegen Sperre und Feld geprüft.
- **P75** Symbole malen braucht `GestureEnabler.shouldHandleGesture()`: Die Robolectric-Tests
  müssen `PointerTracker.setKeyDetector` (Passwortmodus) und `AccessibilityUtils.init` aufrufen,
  sonst erkennt der echte PointerTracker keine Geste und tippt einen Buchstaben. Ein Folgestrich
  steht nicht in der Zeigerwarteschlange; deshalb beendet ein zweiter Finger ihn sofort, statt zu
  warten (ein Tipp wird Taste, ein Strich verwirft die Zeichnung, P104), und ein hängender Strich
  kann die Tastatur nie blockieren.
- **P76** Beim Malen nie `LatinIME.onStartBatchInput` erreichen: das startet die
  Gestenwort-Verarbeitung und die P5-Sperre; die Weiterleitung steht allein in
  `KeyboardActionListenerImpl`.
- **P77** Ein Chip in der Leiste kann auch auf dem Sperrbildschirm angetippt werden. Was eine
  Einstellungsseite öffnet („Nicht erkannt“ beim Symbole-Malen), prüft deshalb vorher
  `SettingsValues.mIsLocked` und tut gesperrt nichts außer den Chip zu entfernen.
- **P78** Ein Textfeld in einem Compose-`AlertDialog` wird in Robolectric nie „idle“
  (`AppNotIdleException`, auch mit angehaltener Testuhr), ein Textfeld auf einer normalen Seite
  schon. Abläufe mit Texteingabe, die getestet werden sollen, deshalb auf der Seite (Karte) statt
  im Dialog bauen (`AgentSymbolsScreen.AddSymbolCard`).
- **P95** HeliBoard setzt `PointerTracker.mUpTime` nirgends (nur deklariert und in `onDownEvent`
  gelesen, so auch im Upstream-Commit aus `upstream.lock`); der „naive Rauschfilter“ dort greift
  also nie. Der Tintenzweig von `onUpEvent` setzt ihn deshalb auch nicht: Setzte nur er ihn, würde
  ein schneller zweiter Punkt (÷) nahe dem Ende des vorigen Strichs als Rauschen verworfen,
  anders als jeder normale Tipp.
- **P96** Ein Tipp im Wartefenster sieht genau aus wie ein Punkt; erst beim Loslassen ist klar,
  dass er ein Buchstabe ist. Dann muss zuerst das Symbol ins Feld (es kann die Umschaltung ändern,
  z. B. Großschreibung nach einem eigenen „.“) und erst danach die Taste ermittelt werden.
  `SymbolInk.onInkUp` schreibt das Symbol, `PointerTracker` holt den Down mit
  `onDownEventInternal` an der Aufsetzstelle nach (Tastendruck, Rückmeldung, Vorschau; nicht an der
  Up-Stelle, sonst drückt ein Tipp, der bis zur Tipp-Grenze (seit 36 unter 8 dp) auf die
  Nachbartaste rutscht, z. B. Löschen, diese) und lässt den normalen Up-Pfad die Taste senden. Kam ein zweiter Finger dazu, ist der Tipp
  schon Taste (P104): `inkTapBecomesKey` hat `mInInk` gelöscht, `onUpEvent` ruft `onInkUp` dann
  gar nicht und der normale Pfad tippt ihn (Korrektur nach Prüfer zu 057a6d5).
- **P97** Ein Wortvorschlag kann im Wartefenster zwei Dinge sein: ein neues Wort (Vorhersage) oder
  die Korrektur des Worts vor der Zeichnung. Schreibt man das Symbol bei einer Korrektur zuerst,
  beendet `onTextInput` das Wort, und die Wahl würde danach eingefügt („Preis→Preise“). Deshalb
  schreibt `beforeSuggestionPick` das Symbol nur, wenn nichts komponiert wird
  (`hasActiveComposition`); sonst ersetzt die Wahl das Wort und das Symbol folgt nach der Pause.
- **P98** Android und die Tastatur selbst schalten über dieselbe Methode
  `onCurrentInputMethodSubtypeChanged` um (`switchToSubtype` ist nur ein Alias). `SubtypeSwitchLog`
  unterscheidet sie über eine Notiz, die `switchToSubtype` vor dem Aufruf setzt; ein Wechsel ohne
  frische Notiz (2 s, gleicher Subtyp) zählt als System. Der Wechsel aus `onStartInputInternal`
  läuft über eine Nachricht (`postSwitchLanguage`); die Notiz dafür setzt `expectFromStart` beim
  Posten. Ein Wechsel, der wegen „gleicher Subtyp“ früh endet, erzeugt keinen Eintrag.
  „Behalten“ (`SubtypeChoice.holds`) zählt nur, wenn `getSubtypeForLocales` einen anderen Subtyp
  geliefert hätte; gefragt wird mit `peek = true`, denn ohne setzt die Methode `overriddenByLocale`
  auf die behaltene Sprache, und ein späteres Feld ohne Hinweis schaltet dann nach einem Wechsel
  per Sprachtaste auf sie zurück (`onSubtypeChanged` löscht den Wert nicht, wenn der alte Subtyp
  er selbst ist).
- **P99** `SearchSettingsScreen` mit einer Liste von Einstellungsschlüsseln (z. B.
  `GestureTypingScreen`) liest `SettingsActivity.settingsContainer`; ein Compose-Test ohne
  `SettingsActivity` muss ihn vorher setzen (`SettingsContainer(app)`), sonst
  `UninitializedPropertyAccessException`.
- **P104** Zwei Daumen im Wartefenster von Symbole malen: Daumen A tippt (als Tinte, er könnte ein
  Punkt sein), Daumen B setzt auf, bevor A loslässt. `startsInk` von B fragt
  `SymbolInk.onSecondFinger`: Ist As Strich noch ein Tipp (bei zweitem Finger unter 10 dp,
  `InkTap.SECOND_FINGER_SLOP_DP`, großzügiger als die 8 dp beim Loslassen), wird das Symbol sofort
  geschrieben und A über `inkTapBecomesKey` ein normaler Tastendruck an seiner Aufsetzstelle (in
  der Warteschlange vor B, also in Tipp-Reihenfolge); ein echter Strich endet mit der Zeichnung, die
  samt ihm geschrieben wird (Prüfer M1, vorher verworfen); As Loslassen bleibt danach still. Ein Tipp
  mit zweitem Finger wird nie Punkt. Die Punkt-Zone (`InkDots`, Schritt 3) ist schmal und in
  Richtung des Punkts gestreckt; ein `$P`-Vergleich „Strich plus Tipp gleicht ÷ besser als –“
  taugt nicht als weitere Regel: mit einem Punkt ist er schief (ein erster Punkt unter dem Strich
  verlor gegen „–“, auch genau an der ÷-Stelle) und sortierte kaum Buchstaben aus. Schritt 4 von
  `InkDots` fragt den Erkenner mit allen bisherigen Strichen einschließlich früherer Punkte (genau
  die Zeichnung, die bei einem Buchstaben geschrieben wird), damit dessen Merker das Schreiben
  danach beantwortet; die Schritte 2 und 3 sehen die Striche ohne frühere Punkte.
- **P70** Assets mit der Endung `.gz` entpackt der Android-Build beim Zusammenführen und lässt
  die Endung weg (`mergeDebugAssets`); `assets.open("….gz")` findet dann nichts. Die
  Emoji-Mix-Tabelle heißt deshalb `emoji_mix_pairs.bin`, obwohl sie gzip-Daten enthält.
- **P71** Emoji Kitchen speichert jedes Paar nur in einer Reihenfolge (links, rechts), und die
  Codepunkte in der URL tragen FE0F so, wie die Tabelle sie kennt (`u2764-ufe0f`). Getippte
  Emojis deshalb normalisiert suchen und die URL immer aus den Codepunkten der Tabelle bauen,
  nie aus dem getippten Text; erst die umgekehrte Reihenfolge probieren, dann aufgeben.
- **P72** Ein Netzweg darf nur aus einem Tipp entstehen: Chips und die Leiste rufen nur
  `EmojiMixer.state` (Cache auf dem Gerät), nie `request`. Im Mixer der Emoji-Ansicht ist die
  Wahl des zweiten Emojis der Tipp; `request` dort nur bei unbekanntem Zustand, sonst würde jede
  Neuzeichnung nach einem Fehler erneut laden.
- **P73** `InputLogicTest` ersetzt `Handler.sendMessage`/`sendMessageDelayed` durch einen Schatten,
  der Nachrichten nur sammelt; ein `Handler.post` aus einem Hintergrund-Thread (hier die
  Ergebnisse von `EmojiMixer`) kommt dort nie an, auch nicht über `ShadowLooper.idle`. Der Test
  setzt deshalb `EmojiMixer.toMain` und führt die Übergaben selbst aus. Ein Laden, das vor dem
  Austausch begonnen hat (z. B. die Tabelle über die Leiste beim Start des Felds), meldet sich
  nie zurück; `resetForTest` erlaubt dann ein neues Laden der Tabelle.
- **P74** Ein Zustand in `EmojiMixer` kann verschwinden (LRU mit 12 Paaren, „Gespeicherte
  Mischungen löschen“), während die Emoji-Ansicht dieselbe Wahl zeigt. Jede Änderung irgendeines
  Paars zeichnet den Mixer neu; ohne die Merkung `requestedKey` würde die Neuzeichnung dann ohne
  Tipp erneut laden. Eine neue Anfrage für dieselbe Wahl kommt nur aus einem Tipp.
- **P83** Nach einem eingefügten Bild (Emoji-Mix, `@qr`) ist der Cache von `RichInputConnection`
  veraltet: `commitContent` läuft an ihm vorbei, und ein Rich-Text-Editor kann das Bild als
  Objektzeichen (U+FFFC) an die Schreibmarke setzen. Die Prüfung aus dem Cache besteht dann,
  `replaceTextBeforeCursorIfUnchanged` liest beim Editor und lehnt ab, und der Nutzer sähe
  „Text geändert, nichts ersetzt“, obwohl das Bild drin ist. Deshalb liest
  `QrCommand.removeAfterInsert` den Text vor der Schreibmarke beim Editor
  (`Host.editorTextBeforeCursor`) und meldet bei einer Abweichung „Bild eingefügt …, der getippte
  Text bleibt stehen“. Ebenso darf der Sink nie „eingefügt“ melden, ohne die Antwort des Editors
  zu kennen: `KeyboardActionListenerImpl.onContent` und `RichInputConnection.commitContent` geben
  sie nicht weiter, `QrCommand.SYSTEM_SINK` committet deshalb selbst.
- **P100** Seitlicher Leertasten-Wisch mit Einmal-Aktion (Etappe 33): Der Abstand zählt ab dem
  Aufsetzpunkt, `PointerTracker` darf `mStartX` dafür nicht nachziehen (das tut nur
  `onSpaceSwipe` bei fortlaufenden Aktionen). Gemeldet wird jede Bewegung, auch unter einem
  10-dp-Schritt nahe dem Start; mit der alten Bedingung `stepsX != 0` ginge ein Zurückziehen
  hinter den ersten Rastpunkt in einem Zug verloren. Die Richtung legt weiter der erste Schritt
  fest; wer erst nach rechts zuckt und dann nach links zieht, löst links nichts aus.
- **P101** Halten der Leertaste gegen langsames Wischen: Der Abbruch steht vor der
  Schnelltipp-Sperre in `onKeySwipe`. Dahinter würde eine seitliche Bewegung in den ersten zwei
  Dritteln der Langdruckzeit nach dem Tippen ignoriert; bleibt der Finger danach kurz liegen,
  liefe die Halten-Aktion. Nur `cancelLongPressTimersOf`, nur klar seitlich (|dX| ≥ 10 dp und
  größer als |dY|); senkrecht gilt weiter der erste 10-dp-Schritt.
- **P102** Rastpunkte: Ein überquerter Punkt gilt erst 0,2 Tastenbreiten dahinter als verlassen,
  sonst brummt ein ruhender Finger auf dem Punkt bei jedem Zittern. Nach dem letzten Punkt passiert
  bis zum Loslassen nichts mehr (eine Aktion pro Wisch). `onEndSpaceSwipe` setzt die
  Zustandsmaschine zurück, bevor es bei `isBlockingInput` aussteigt; sonst bliebe sie nach einem
  geöffneten Rad „vollendet“ und schluckte den nächsten Wisch.
- **P103** `PRIMITIVE_THUD` gibt es erst ab API 31: vorher nicht nach ihm fragen. Ein schwerer
  Impuls allein ist kein „längerer“ Impuls; ohne `PRIMITIVE_QUICK_RISE` nimmt der letzte
  Rastpunkt deshalb die Wellenform mit steigender Amplitude, und ohne Amplitudensteuerung einen
  langen Puls (P67).
- **P85** Ein Material-3-`Slider` meldet Ziehen, Tippen, TalkBack (`setProgress`) und Tasten über
  dasselbe `onValueChange`. Wer dort einrasten lässt, macht Werte neben einer Rast für TalkBack
  unerreichbar. `AgentSlider` lässt deshalb nur Werte vom Finger durch `SliderDrag`. Erkannt wird
  der Finger nicht über die `interactionSource` (deren `DragInteraction` kommt über einen Flow erst
  nach den ersten Werten an), sondern über `SliderState.isDragging` (Ziehen; die letzten Schritte
  kommen nach dem Loslassen) und einen eigenen Beobachter, der den Finger im Initial-Pass als
  unten und erst im Final-Pass des Loslassens als oben zählt (ein Tipp meldet seinen Wert im
  Main-Pass des Loslassens).
- **P86** `SliderState` rechnet den Wert aus der Fingerposition (`rawOffset`), nicht aus dem
  zuletzt gemeldeten Wert: Ein eingerasteter Wert verschiebt den Finger nicht, deshalb kann
  `SliderDrag` halten und beim Weiterziehen wieder loslassen. Beim Ziehen hinkt der Wert dem
  Finger um die Touch-Slop hinterher (der erste Schritt meldet nur den Überschuss); Tests folgen
  deshalb dem Wert in kleinen Schritten statt fester Fingerpositionen. Ein Tipp auf die Leiste
  erreicht unter Robolectric nicht einmal einen unveränderten Material-Slider; dieser Weg ist nur
  am Gerät prüfbar.
- **P87** Robolectric bewegt `SystemClock` nicht mit den Eingaben des Compose-Tests. Mit einer
  stehenden Uhr würde die 25-ms-Grenze alle Impulse nach dem ersten schlucken; UI-Tests setzen
  `SliderDrag.clockForTest`, die Grenze selbst prüft `SliderFeelTest` mit eigener Uhr. Eine
  Rast auf Stufen muss genau auf einer Stufe liegen, sonst setzt `SliderState.value` den Wert auf
  die nächste Stufe, während der Aufrufer einen anderen Wert hält.
- **P88** „Zuletzt gemischt“ darf die Zustände von `EmojiMixer` nicht benutzen: höchstens 12 im
  Speicher, und zwölf Bilder der Reihe plus das gewählte Paar würden sich bei jeder Neuzeichnung
  gegenseitig verdrängen (Neuladen von der Platte ohne Ende, und das gewählte Paar fiele auf
  „Tippen, um … zu laden“, P74). Die Reihe hat deshalb eine eigene Bild-LRU und liest nur Dateien,
  die im Cache liegen (`shown` prüft nur, ob die Datei da ist); sie lädt nie aus dem Netz (P72).
- **P89** Ein gespeicherter Paarschlüssel kann Codepunkte jenseits von Unicode enthalten (von Hand
  geänderte Einstellungen, Sicherung); `EmojiClusters.fromCodepoints` wirft dann. `mixOf` fängt
  das ab und behandelt das Paar als unbekannt. Ob gemerkt wird, entscheidet `recording()` beim
  Tipp und beim Ergebnis: eine Mischung, die vor dem Inkognito-Wechsel angefragt wurde, wird nicht
  gemerkt, wenn sie danach ankommt.
- **P115** Ein Emoji aus `EmojiSuggestions` darf nie an der Stelle der automatischen Korrektur
  stehen: Es wird erst nach `shouldBeAutoCorrected`, `guardAllows` und `applyLearnedCorrection`
  eingefügt und bei anstehender Korrektur nie an Index 0 des Containers (sonst setzte die Leertaste
  ein Emoji statt des Worts). Wer die Reihenfolge in `getSuggestedWordsForNonBatchInput` ändert,
  prüft `SuggestTest.emojiSuggestionsNeverBecomeTheAutocorrection`.
- **P116** Die Stichwort-Tabelle wird nie auf dem Tipp-Pfad geladen (etwa 0,1–0,3 s): erst ein
  Daemon-Thread liest sie, bis dahin fügt `addTo` nichts hinzu. Tests, die genaue Vorschlagslisten
  prüfen, setzen `EmojiSuggestions.setTableForTest(null)` (`SuggestTest`, `InputLogicTest`), sonst
  hinge das Ergebnis davon ab, ob das Laden schon fertig ist. `InputLogicTest` holt Vorschläge
  synchron über `mSuggest.getSuggestedWords`: `performUpdateSuggestionStripSync` wartet auf den
  Handler-Thread, der in Robolectric nicht läuft, und liefert dort nichts.
- **P117** Die Faltung macht aus „schön“ „schon“ und aus „für“ „fur“: Funktionswörter
  (`EmojiKeywords.STOPWORDS`) werden deshalb ungefaltet verglichen, sonst bekäme „schön“ nie ein
  Emoji. Der einfache Stammformer schneidet nur einmal ab, „maus“ wird zu „mau“, „mäuse“ zu
  „maus“; die Wortform-Stufe sucht den Stamm deshalb auch als ganzes Stichwort.
- **P118** Deutsch und Englisch teilen viele Schreibweisen mit anderer Bedeutung („Tag“ 🏷️,
  „Gift“ 🎁, „Art“ 🖼️). Ein Wort, das ein Wörterbuch kennt, fragt deshalb nur dessen Sprache
  (nicht die Hauptsprache: beim mehrsprachigen Tippen kennt das englische Wörterbuch „love“);
  Wörter, die kein Wörterbuch kennt, fragen Hauptsprache, Deutsch und Englisch.
  Ebenso fließen Wörter aus mehrteiligen Stichwörtern nicht in Stämme: sonst trafen „viel“
  („zu viel gegessen“), „macht“ („sauber machen“) und „sagt“ („Säge“).

- **P110** Ein Folgestrich von Symbole malen steht nicht in der Zeigerwarteschlange, und
  `ACTION_CANCEL` erreicht nur einen Tracker. Ohne Gegenmittel blieb `SymbolInk.drawingPointer`
  stehen, und der nächste Finger machte den alten Tracker per `inkTapBecomesKey` zu einem
  Tastendruck, der nie losgelassen wird (Geisterbuchstabe). Deshalb: `onCancelEvent` beendet jeden
  laufenden Folgestrich (auch eines anderen Trackers), und `startsInk` macht nur einen Tracker mit
  `mInInk` zur Taste, sonst `SymbolInk.cancel`. Test `SymbolInkTest.aCancelThatReachesAnotherTrackerEndsTheInkStroke`.
- **P111** Die Tipp-Regel im Wartefenster misst nur die Bewegung (unter 8 dp wie Androids Touch-Slop;
  die erste Fassung mit 4 dp verlor nach dem Modell 7 % der Buchstaben, Prüfer H1, 6 dp noch 2 %,
  Nachprüfer F3; der Preis: kurze absichtliche Striche unter 8 dp gelten als Tipp), nicht die Dauer.
  Innerhalb des Bereichs um die Zeichnung (3 Tasten) kann ein stark verwackelter Tipp (ab 8 dp)
  trotzdem als Strich zählen.
  Eine still gehaltene Berührung hat keine Form; als Strich gezählt, fügte sie der Zeichnung einen
  Punkt hinzu und verlöre den Buchstaben (ein langsamer Tipp). Die vorgeschlagene Regel „unter
  4 dp und unter 180 ms“ ist im Nachweis gemessen und verworfen (`InkTimingTest`).
- **P112** Die Vorschau erkennt 120 ms nach dem letzten Strich auf einem eigenen Thread; das
  Ergebnis gilt nur, wenn sich der Zustand nicht geändert hat (jede Änderung über `setShown`, sonst
  überschreibt eine späte Vorschau ein geschriebenes Symbol). Das Schreiben nach der Pause, der Punkt-Check
  (Schritt 4) und der Chip-Tipp müssen denselben `InkRecognizer` fragen, sonst rechnet der
  Hauptthread dieselbe Zeichnung mehrfach (`InputLogicTest.symbolDrawTapInThePauseThatIsNoDotMatchesTheDrawingOnce`
  zählt ab vor dem Malen). `SymbolDrawing.reset` setzt seinen Zustand vor `SymbolInk.cancel`
  zurück, sonst baut die Leere-Meldung beim Editorwechsel die Leiste neu.
- **P113** Eine Vorlage mit Punkten näher am Strich als `InkDots.DOT_ALONG` (0,25) macht einen Tipp
  genau auf den Strich zum Punkt: Die Punkt-Zone reicht dann über die Mitte. Versucht mit einem ÷
  mit Punkten bei 25/75 (`InkDotsTest.tapsAwayFromTheDotsOfADashAreLetters` wurde rot), wieder
  entfernt. Neue Vorlagen mit Punkten nur mit diesem Test ändern.
- **P114** Mit Richtungs- und Strukturterm sind alle Abstände größer als beim reinen `$P`. Die
  Schwellen (`ACCEPT_DISTANCE` 1,8, `CLEAR_DISTANCE` 2,3, `ALTERNATIVE_DISTANCE` 2,6) und
  `InkDots.BASE_DISTANCE` (= Annahme) gehören zu den Gewichten in `InkRecognizer.Tuning`; wer
  eines ändert, misst `InkBenchmarkTest` und `scribblesAreMostlyRejected` neu. `CUTOFF` muss über
  `ALTERNATIVE_DISTANCE` und `CLEAR_DISTANCE × CLEAR_MARGIN` bleiben: Kandidaten darüber stehen
  nur mit dem abgeschnittenen Wert in der Liste.
- **P119** Jedes neue Ziel in `SettingsDestination` muss auf einer Bereichsseite stehen
  (`SettingsAreas.entries`) oder in der Liste `subPages` von
  `SettingsStartScreenTest.everyOldMainEntryAndEveryPageIsOnAnAreaPage` (Unterseiten mit
  Argument); sonst ist es seit 38a von der Startseite aus nicht mehr erreichbar. Der Test liest
  die Ziele per Reflexion und wird rot.
- **P120** Robolectric ohne native Grafik misst Text fast ohne Breite: Ein Knopf nur aus Text und
  Innenabstand war dort 35 dp breit. Knöpfe, Pillen und Chips der neuen Einstellungen haben
  deshalb `heightIn`/`widthIn(min = 44.dp)` ausdrücklich (`everyClickableIsAtLeast44dp`).
- **P121** Startseite und Bereiche lesen Einstellungen bei jeder Komposition und zählen dafür
  `SettingsActivity.prefChanged` und einen eigenen Zähler (in Tests gibt es keine
  `SettingsActivity`); ein Schnellschalter schreibt sofort, ohne Kopie im `remember`. Der
  RGB-Schalter ändert nur `enabled` und schreibt über `RgbLighting.write` (normalisiert, bei
  Standardwerten wird der Schlüssel entfernt), nie eine eigene Kodierung.
- **P122** `Color` ist eine Value-Klasse und darf kein `vararg` sein (Kompilierfehler);
  `cssGradient` nimmt zwei Farben oder eine Liste aus Stopps. Der CSS-Winkel zählt ab oben im
  Uhrzeigersinn, die Länge der Verlaufslinie ist |b·sin| + |h·cos| wie in CSS, sonst sitzen die
  Endfarben nicht in den Ecken.
- **P126** Eine Zeile der Tippen-Seite muss die Einstellung in ihrem eigenen Composable lesen
  (`TypingSwitchRow` ruft `checked()` auf, `bool()` liest dabei beide Zähler aus P121). Liest der
  Aufrufer den Wert und reicht nur Schlüssel und Funktionsreferenzen weiter, überspringt Compose
  die Zeile nach dem Schreiben (gleiche Parameter), Einstellung und Schalter laufen auseinander.
- **P127** Die Stufen der Autokorrektur sind Bänder, keine festen Werte: Eine Stufe schreibt die
  Stärke nur, wenn sie außerhalb ihres Bands liegt (≤ 0,40 bzw. > 0,80, wie die Texte „Mäßig“ und
  „Sehr stark“ von `TextCorrectionScreen`). Wer die Bänder oder die Texte dort ändert, ändert
  beide und `SettingsTypingAreaTest.mappingReadsAndWritesTheRealPreferences`.
- **P130** Compose vergleicht unbenutzte Parameter nicht: Ein „tick“-Parameter, der nur ein
  Neuzeichnen erzwingen soll, lässt den Abschnitt trotzdem überspringen. Abschnitte, die
  Einstellungen selbst lesen, sind deshalb `@NonSkippableComposable`
  (`AgentAreaGesturesScreen.kt`); sonst zeigt ein Schalter nach dem Tipp den alten Zustand.
  Außerdem: in `prefs.edit { … }` ruft `putBoolean` die nächstgelegene Funktion dieses Namens auf,
  eine lokale `fun putBoolean` ruft sich dort selbst auf (StackOverflow).
- **P131** `TouchModel.replace` speichert alle Tasten aus dem Speicher; ohne vorheriges
  `TouchModel.loadStored` überschreibt eine Kalibrierung die gelernten Zonen der übrigen Tasten.
  Jeder Weg zu `CalibrationDialog` lädt zuerst (Lernseite beim Öffnen, Gestenseite beim Tipp).
- **P128** `displayName(displayLocale)` und `localizedDisplayName(res, displayLocale)` laufen über
  `runInLocale(resources, …)`, das `Resources` mit dem geteilten `AssetManager` neu baut und dessen
  Konfiguration umstellt: danach waren im Test alle Texte der Seite spanisch/englisch. Namen in
  einer anderen Sprache nur über `runInLocale(context, …)` (Konfigurations-Kontext) holen
  (`languageCards`, `SettingsKeysScreenTest` prüft die Sprache danach).
- **P129** Die Wertspalte von `SettingRow` hat keine Breitengrenze: ein langer Wert (Zeitstempel
  `yyyy-MM-dd HH:mm:ss`, Sätze wie bei „Mehr Buchstaben …“) drückt den Titel auf einen Buchstaben
  Breite. Lange Werte gehören in den Untertitel (Seite Tasten & Sprachen: Zeitstempel über 10
  Zeichen, Verhalten der Sprachwechseltaste, mehr Popup-Buchstaben).
- **P132** Die Befehls-Chips der Leiste-Seite sind keine `ChipToggles` des Baukastens: Bei
  ausgeschaltetem Hauptschalter müssen sie gesperrt sein (`toggleable(enabled = false)`), sonst
  schaltet ein Tipp still einen Befehl, der ohnehin nicht erkannt wird. Wer `ChipToggles` um
  `enabled` erweitert, kann sie dort einsetzen.
- **P133** Die Karte „@-Befehle“ liest Hauptschalter, Präfix und Chip-Zustände in ihrem eigenen
  Composable und ruft dafür `observe()` (beide Zähler aus P121); sonst überspringt Compose sie
  nach einem Tipp (P126), Chip und Einstellung laufen auseinander.
- **P134** Die KI-Bereichsseite darf auf gesperrtem Gerät nichts aus dem geschützten Speicher
  öffnen: `ModelSettings`, `SkillBindingStore`, `WorkflowStore` und `EncryptedCredentialStore`
  entstehen erst in `UnlockedContent`, hinter `credentialAccessAllowed` (wie `AgentAiScreen`).
  Wer dort einen Wert schon im äußeren Composable liest, wirft auf echten Geräten vor dem ersten
  Entsperren (Direct Boot). Der Schlüsselstatus nutzt nur `contains`, nie `read`
  (`SettingsAreaAiTest.keyStatusFollowsTheProviderAndNeverShowsTheKey` sucht den Schlüssel in allen
  Texten).
- **P135** `SettingsStartScreenTest.eachAreaPageHasItsHeroAndOpensItsPages` klickt je Route aus
  `SettingsAreas.entries` genau einen Knoten mit `AREA_ROW_TAG` + Route und erwartet die Routen in
  dieser Reihenfolge. Auf der KI-Seite öffnen mehrere Elemente dieselbe Seite (drei Kacheln,
  drei Workflow-Zeilen, „Ändern“ und Auftragsstatus): nur je eines trägt den Tag, die anderen
  `SKILL_TAG`/`AI_ROW_TAG`, sonst ist der Knoten nicht eindeutig und der Test wird rot.
- **P136** Profilkarten auf System & Daten sind Aktionen, keine Auswahl: `ChoiceCards` ist eine
  Radio-Gruppe mit genau einer Wahl, hier können mehrere Karten gleichzeitig „aktiv“ sein (Design
  und Gesten passen) oder keine. Deshalb eigene Knöpfe (Role.Button, stateDescription); die
  Hervorhebung rechnet `FlatPreset.isApplied`/`KeyboardProfileStore.matchesCurrent` nach jeder
  Änderung (Zähler aus P121) und bei `ON_RESUME` neu. `matchesCurrent` fängt alles ab: ein
  aktueller Wert außerhalb der Bindungen (z. B. aus einer Sicherung) lässt `capture` werfen, das
  hieß vorher Absturz bei jeder Komposition. Wer `FlatPreset.values` oder die Profil-Bindungen ändert, ändert damit, wann
  eine Karte hell ist (`SettingsSystemAreaTest.profileCardsAreButtonsThatApplyTheRealProfiles`).
- **P137** Ein `clip` mit runden Ecken auf einer klickbaren Fläche ohne Innenabstand schneidet die
  ersten Buchstaben ab (im Bild „JPDATES“, „Zuletzt“ ohne „Z“). Die klickbare Überschrift der
  Update-Karte hat deshalb keinen Clip. `DateUtils.getRelativeTimeSpanString` schreibt deutsch
  „Vor 2 Stunden“ groß; mitten im Satz wird der erste Buchstabe klein gemacht.
- **P145** Auch Bereichsseiten außer der KI-Seite (P134) berühren den CE-Speicher: System & Daten
  mit `UpdateSettings` (über `credentialContext`). Sie entsteht nur, wenn `credentialAccessAllowed`
  gilt; sonst ein Hinweis. Nicht CE sind `KeyboardProfileStore`, `FlatPreset`, `protectedPrefs()`
  und `filesDir` in der Einstellungen-Activity: `SettingsActivity.attachBaseContext` setzt den
  DE-Kontext (`AgentStorageContexts.keyboard`), also bleiben Profilkarten und Gesten-Bibliothek
  gesperrt sichtbar. Vor einer Sperre prüfen, welcher Kontext den Speicher öffnet. Ein gesperrter
  Bildschirm allein reicht als Test nicht: vor dem ersten Entsperren kann der Sperrbildschirm weg
  sein, der Nutzer aber noch gesperrt (`UserManager.isUserUnlocked`, im Test
  `ShadowUserManager.setUserUnlocked(false)`,
  `SettingsSystemAreaTest.beforeTheFirstUnlockOnlyTheUpdateCardWaits`; Robolectric wirft bei
  CE-Zugriff nicht, der Test prüft nur das Sichtbare).
- **P146** Eigene Schrift, Emoji-Schrift und Hintergrundbild sind Dateien: Laden oder Löschen
  ändert keine Einstellung, also zählt `prefChanged` nicht und die Seite Aussehen zeigte den alten
  Wert. Sie zählt deshalb bei `ON_RESUME` (die Dateiauswahl pausiert die Activity), beim Schließen
  eines Gruppen-Dialogs und über `CustomFontPreference(onChanged)`; außerdem hört sie selbst auf
  Einstellungsänderungen (auch ohne `SettingsActivity`, Tests). Wer eine neue Datei-Zeile baut,
  braucht einen dieser Wege (`SettingsLookScreenTest.fileOnlyChangesShowAfterResumeDeleteAndClosingTheDialog`).
- **P144** Geschützte Seiten (FLAG_SECURE) können verschachtelt sein (KI & Automationen →
  KI-Anbieter), und der NavHost animiert: Die neue Seite startet, bevor die alte entsorgt ist. Ein
  „wasSecure“-Schnappschuss je Seite löscht das Flag dann unter der neuen Seite. Immer
  `SecureWindow.acquire/release` (Zähler je Fenster) benutzen, nie `addFlags/clearFlags` direkt.
- **P124** Abschnitte einer Bereichsseite, die Einstellungen selbst lesen (eigene `@Composable` mit
  `context`/`prefs` als Parameter), überspringt Compose bei der Neukomposition (gleiche Instanzen,
  „strong skipping“). Sie bekommen deshalb die Zählersumme `version` als Parameter und müssen ihn
  lesen (38b: `check(version >= 0)`): einen ungenutzten Parameter lässt der Compose-Compiler beim
  Vergleich weg. Ohne das zeigt ein Schalter nach dem Tipp den alten Zustand, obwohl die
  Einstellung geschrieben ist (im Test nur je nach Reihenfolge der Klassen rot).
- **P125** Tastaturhöhe und Tastenabstand auf der Seite Aussehen rufen `KeyboardScalePreference`
  mit eigenen Bereichen (0,3–1,5 bzw. 0,5–2,5) und Dimensionen auf, wie `createAppearanceSettings`.
  Wer dort Bereich oder Dimensionen ändert, ändert sie auch in `AgentAreaLookScreen.kt`. Die
  Gruppen-Dialoge zeigen dagegen die Upstream-`Setting`-Zeilen selbst und folgen automatisch.
- **P138** Der Formatwächter prüft nur das ganze Feld: Text nach der Schreibmarke (oder ein
  unbekannter) heißt kein Hinweis, sonst würde ein Tipp nur den Teil vor der Schreibmarke
  ersetzen. `textAfterCursor` geht an die App (IPC); `FormatGuardChips.stripChips` fragt es deshalb
  erst, wenn `FormatGuard.check` etwas gefunden hat, denn die Prüfung läuft bei jeder
  Leistenaktualisierung in jedem Textfeld (IBAN-Prüfung). Der Tipp auf einen Chip fragt es noch
  einmal: `replaceCommandText` prüft nur den Text vor der Schreibmarke, Text, der seit dem Bau
  dahinter dazukam, bliebe sonst stehen.
- **P139** `KeyboardCommands.isBlocked`/`find` weisen alle Felder außer `TYPE_CLASS_TEXT` ab;
  Telefon- und Zahlenfelder kommen trotzdem bis `buildChips` (Befehl = null). Der Formatwächter hat
  deshalb eine eigene Sperre (Inkognito, gesperrt, Passworttypen inklusive
  `TYPE_NUMBER_VARIATION_PASSWORD`); wer sie auf `KeyboardCommands.isBlocked` umstellt, verliert
  Telefon und Zahlen. `InputAttributes` zählt `IME_FLAG_NO_PERSONALIZED_LEARNING` nur in
  `TYPE_CLASS_TEXT` als Inkognito; die Sperre prüft das Flag deshalb selbst, sonst bekämen
  Telefon- und Zahlenfelder ohne Lernen Hinweise. Mit Auswahl (`agentCommandCursor` < 0) gibt `buildChips` vorher null zurück.
- **P140** libphonenumber formatiert nach seinen Metadaten, nicht nach Gefühl: deutsche
  Mobilnummern als `+49 1512 3456789`. Ein Update der Bibliothek kann das Format einzelner Länder
  ändern; dann die erwarteten Texte in `FormatGuardTest` an die neue Ausgabe anpassen, nicht die
  Prüfung lockern. Die Metadaten liegen als Java-Ressourcen im Jar und werden beim ersten Aufruf
  geladen (`PhoneNumberUtil.getInstance`, lazy); ein minifizierter Build muss sie behalten. Der
  erste Aufruf kommt aus dem Leistenbau im Hauptthread; ein Vorladen im Hintergrund müsste danach
  die Leiste neu anstoßen (`Host.refresh` hilft nicht, solange keine Chips gezeigt werden). Die
  Dauer auf einem Gerät ist offen.
- **P141** `@snip`-Felder: `SnipCommand` setzt `filling` (bzw. den nächsten Index) vor
  `Host.replace` und nimmt es bei Misserfolg zurück. Der echte Host baut die Leiste schon während
  des Ersetzens neu (`setNeutralSuggestionStrip`); wäre der Zustand erst danach gesetzt, sähe der
  Neuaufbau „@snip name Feld: “ ohne Feld und zeigte den normalen Streifen.
- **P142** Enter wird an zwei Stellen abgefangen: `LatinIME.onEvent` (Bildschirmtastatur) und
  `KeyboardActionListenerImpl.onKeyDown` (Hardware-Tastatur, `KEYCODE_ENTER`/`NUMPAD_ENTER` ohne
  Modifikator). Dort steht die Abfrage vor der Prüfung `mEnableHardwareKeyboard`: Ist die
  Einstellung aus (Standard), bekäme sonst die App die Taste und schickte den Befehlstext. Nimmt
  der Befehl die Taste, muss auch `onKeyUp` sie nehmen (`agentEnterDown`, dort nur nach Tastencode,
  auch wenn inzwischen ein Modifikator gedrückt ist; zurückgesetzt, wenn `onKeyDown` wegen
  blockierter Eingabe früh zurückkehrt), denn `TextView.onKeyUp`
  springt bei Enter in einzeiligen Feldern sonst zum nächsten Feld; Wiederholungen eines
  gehaltenen Enter fragen kein weiteres Feld ab. `onEnter` darf nur `true` liefern, wenn ein Feld
  wirklich abgefragt wird, sonst wäre Enter in jedem `@snip`-Text tot.
- **P143** `{zwischenablage}` liest die Zwischenablage erst beim Einfügen und nie in den
  Einstellungen: Die Vorschau zeigt nur „‹Zwischenablage›“. Vertraulich prüft `SystemClipboard`
  mit derselben Umwandlung wie Verlauf und `SensitiveClips` (`coerceToText`), sonst passt der
  Hash nicht. Bausteine aus `storageVersion` 1 bleiben `literal`; wer `decode` ändert, darf sie
  nicht als Vorlage lesen (`{{` oder `{datum}` in altem Text würden sich sonst ändern).
- **P147** `{cursor}` setzt die Schreibmarke in einem zweiten Batch nach dem Ersetzen
  (`LatinIME.moveAgentCursorBack` über `RichInputConnection.setSelection`); der Editor meldet also
  zwei Auswahländerungen. Im selben Batch ginge es nur über `commitText(…, 1 - back)` in
  `replaceTextBeforeCursorIfUnchanged`, deren Nachprüfung (Text und Schreibmarke am Ende der
  Ersetzung) und Cache-Pflege dann mit umgebaut werden müssten; das ist kein enger Eingriff. Im
  Robolectric-Test ist `checkConnectionConsistency` grün; ob ein Editor (Messenger mit eigener
  Auswahlbehandlung) die zweite Meldung anders verarbeitet, ist ein Gerätetest-Punkt.
- **P148** Der Formatwächter schlägt nie eine andere echte TLD vor: `hotmail.fr` ist kein
  `hotmail.de`. Eine typisierte TLD aus `EmailCheck.VALID_TLDS` vergleicht nur mit Anbietern
  derselben TLD; eine unbekannte nur, wenn sie ein Tippfehler (Abstand 1) einer Anbieter-TLD ist.
  Echte Domains, die einem Anbieter nahe sind (`online.de` ↔ `t-online.de`, `mail.com` ↔
  `gmail.com`, `ymail.com` ↔ `gmail.com`, kurze Firmendomains wie `ms.com`/`ge.com` neben
  `msn.com`/`me.com`), stehen in `KNOWN_DOMAINS`; wer `PROVIDERS` erweitert, prüft solche Nachbarn.
  Bekannte Lücke: Ein Tippfehler, der selbst eine echte TLD ergibt (`gmx.se`, `web.dk`), bekommt
  keinen Hinweis.
- **P149** Enter während einer `@snip`-Feldabfrage wird immer genommen, auch wenn das Ersetzen
  gerade nicht geht (laufende Geste: `replaceCommandText` lehnt ab). Dann ruft `SnipCommand.onEnter`
  `next` gar nicht auf (`Host.canReplaceNow`), das Feld bleibt gefragt und es kommt kein
  „abgelaufen“-Hinweis; ein zweites Enter geht weiter. Ließe es Enter durch, schickte die App
  „@snip name Feld: Wert“. Grenze: Wächst der Wert über 500 Zeichen (z. B. durch Einfügen), ist
  der Text kein Befehl mehr, und das nächste Enter geht an die App (im Chat: Senden).
- **P151** Ein möglicher Punkt (43) wird erst beim Schreiben der Zeichnung zur Taste, oft mitten in
  einem anderen Touch (der nächste Tipp, `flush` aus `onPressKey` einer anderen Taste); ebenso seit
  der Nachprüfung der gerutschte Tipp direkt nach dem Tippen (P153). Darum tippt
  `PointerTracker.typeInkTap` statisch über den Listener und fasst keinen Tracker-Zustand an
  (`mCurrentKey`, Warteschlange); `beforeOtherInput` steht in `onPressKey` vor allem anderen, so
  dass der verschachtelte Tastendruck vor der äußeren Taste fertig ist. Nicht zustandslos: es
  schreibt `sTypingTimeRecorder.onCodeInput` mit der Zeit des Schreibens (nicht des Tipps), startet
  aber keinen `startTypingStateTimer`; kein Haptik-Ereignis, kein Ton, keine Tastenvorschau.
  `onPressKey` bekommt immer einen Zeiger (Prüfer N2: mit zwei hätte die Akkord-Regel die
  Auto-Großschreibung aufgehoben). Die Taste wird an der Aufsetzstelle auf der Tastatur nach dem
  Symbol bestimmt (P96) und nur getippt, wenn sie laut `SymbolInk.startsInkOn` ein Zeichen schreibt
  (N1: Leertaste, Enter, Löschen nie). P113 (`DOT_ALONG`) gilt seit 43 nicht mehr: die Lage prüft `KEEP_REACH` beim Schreiben;
  `InkDots.BASE_DISTANCE` (P114) ist seit 43 `CLEAR_DISTANCE` statt der Annahme.
- **P152** Eine Vorlage, die mit einem Punkt beginnt, sprengt `InkAreaTest` (der Bereich wächst ab
  einem Punkt nur um 3 Tasten) und kommt am Telefon nie vor: ein Tipp allein ist ein Buchstabe. In
  Vorlagen mit Punkten steht der Strich zuerst; der Finger-Bereichstest zieht Punkte am Anfang
  hinter den ersten Strich. Kleine Kreise (r 9–12) als Vorlage scheitern am Rauschen von
  `InkRecognizerTest.perturb` fast immer (Zitter je Punkt, Richtungsterm); gemischte %-Vorlagen
  (ein Kreis, ein Punkt) wurden darum wieder entfernt.
- **P153** Die Schnelltipp-Sperre beim Malen (43) hängt am Abstand vom Aufsetzpunkt, nicht an
  HeliBoards Abstand ab der „schnellen Bewegung“: ein rutschender Tipp von 0,7 Tasten erreicht nach
  dem Tippen nie den Start, ohne Tippen davor startet er wie bisher ab 0,35 Tasten ab dem Punkt,
  wo er schnell wurde. `gestureTypingTime` darf nicht 0 liefern (`(int)` von großen Uptime-Werten
  wird negativ, dann wäre die Sperre an). Nach Prüfer (M1): Ohne Sperre startet ein schneller Tipp,
  der 0,75 Tasten rutscht, eine Zeichnung (im angenommenen Rutschmodell 923 von 10 000 Tipps,
  `InkDotFlowTest`); `InkTap.isSlidTap` gibt sie als Buchstaben zurück, wenn kein zweiter Strich
  folgt. Wer `SLID_TAP_KEYS` senkt, verliert solche Buchstaben als „–“, „/“ oder „|“; wer ihn hebt,
  macht kurze Striche nach dem Tippen zu Buchstaben. Ein zweiter, ebenfalls rutschender Tipp nahe
  der Zeichnung (ab 8 dp) bleibt ein Strich und macht beide zu einem Symbol (P111). Liegt ein
  zweiter Finger auf, gilt HeliBoards Sperre: sonst endete das Loslassen des anderen Fingers im
  `sInGesture`-Zweig ohne Code.
- **P158** Das getippte Wort steht in `TypedWordFirst.arrange` auf Index 2 und das Ergebnis im
  Tippstil (`INPUT_STYLE_TYPING`): Nur dort blendet `SuggestionStripLayoutHelper` Index 0 (das Wort
  im Editor) aus und setzt Index 1 in die Mitte, Index 2 links daneben (in RTL rechts). Im
  Recorrection-Stil stünde Index 0 in der Mitte. Wer die Leistenreihenfolge oder
  `getPositionInSuggestionStrip` ändert, prüft die Stufe-48-Tests (`stripSlots`). Anstehende
  Autokorrektur, Vorhersagen, Satzzeichen und Gesten ordnet `arrange` nie um.
  Bekannte Grenzen (Nachprüfung 48): Gibt es keinen weiteren Vorschlag, steht das getippte Wort
  allein und damit breit in der Mitte. Leisten mit 5 Vorschlagsplätzen sind
  nicht geprüft; die Rechnung gilt für 3 Plätze. In RTL steht es rechts, wie HeliBoards getipptes
  Wort; ein Gerätetest dazu fehlt.
- **P159** Nach der Löschtaste kommen die Vorschläge zweimal: `restartSuggestions` und danach die
  verzögerte Aktualisierung (`setRequiresUpdateSuggestions` in `revertCommit`,
  `performUpdateSuggestionStripSync`). Beide laufen über `InputLogic.getSuggestedWords`; deshalb
  ordnet dort `typedFor` und nicht nur `restartSuggestions`, sonst überschreibt die zweite Liste die
  erste (so verschwand das getippte Wort schon upstream). `typedFor` gilt nur, solange der
  wiederaufgenommene Kompositionstext unverändert ist (`isResumed` und gleiches Wort).
- **P160** `TypedWordFirst` erkennt ein korrigiertes Wort an Startposition und Wort im Editor. Ändert
  sich Text davor, passt der Start nicht mehr, und das getippte Wort fehlt (gewollt, kein
  Fehlgriff auf ein gleiches Wort an anderer Stelle). Einträge nur im Speicher, nie loggen; neue
  Aufrufer von `corrected` nur für automatische Korrekturen der Tipp-Eingabe. Jeder Commit an
  einem Start löscht dort alte Einträge (`committedAt` vor `corrected`); ein neuer Commit-Weg, der
  nicht über `commitChosenWord` läuft, muss das selbst tun.
- **P169** `FieldCorrectionWatcher` vergleicht nur an Prüfpunkten (Wort abgeschickt, Schreibmarke vom
  Nutzer bewegt). Erwartete Auswahl-Meldungen (Tippen, Löschtaste) kehren in `onUpdateSelection`
  vor dem Prüfpunkt zurück; so bleibt der Stand vor dem Zurücklöschen erhalten und „Hsus und“ →
  Löschen → „Haus“ wird erkannt. Wer den Prüfpunkt vor das frühe `return` legt, verliert das.
  Eine Änderung, die erst beim nächsten Prüfpunkt sichtbar wird, gilt für das ganze dazwischen.
  Solange ein Wort komponiert wird (Tipp ins komponierte Wort, Apps mit verkürzter Komposition,
  upstream #1141), vergleicht der Prüfpunkt nichts und nimmt keinen Stand auf, der alte bleibt;
  sonst würde ein halb getipptes Wort zum Stand und jedes fertige Wort zum Kandidaten. Die
  Autokorrektur des gerade abgeschickten Worts (getippt → abgeschickt) und Vervollständigungen
  („Bahnho“ → „Bahnhof“, ohne Akzente Anfang des neuen Worts, wie `TypoMemory.confirm`) zählen nie.
  Der Stand am Commit-Prüfpunkt endet vor dem Trennzeichen; eine Änderung, die die App direkt
  danach macht, ist bis zum nächsten Prüfpunkt deshalb oft „mehrere Änderungen“. Leert die App das
  Feld (vor und nach der Schreibmarke leer, Messenger nach dem Senden), beginnt eine neue
  Feldnummer: dieselbe Stelle in der nächsten Nachricht ist „eine andere Stelle“.
- **P170** Markieren, Einfügen und eingefügter Text verwerfen den Vergleichsstand (`untrusted`, bei
  Markierung im Prüfpunkt selbst): Der nächste Prüfpunkt nimmt nur einen neuen Stand auf. Neue Wege,
  auf denen Text ohne Tippen ins Feld kommt (Werkzeuge, Bausteine, Chips), müssen `untrusted` rufen
  oder über `applyGenerated` laufen, sonst kann ein eingefügtes Einzelwort als Korrektur zählen.
  Das gilt auch, wenn die App selbst Text ändert, auf Wunsch der Tastatur: `UNDO`/`REDO` schicken
  Strg+Z an die App und rufen deshalb `untrusted`. Seit der Nachprüfung 49 wirkt zusätzlich P183.
- **P171** In `InputLogicTest` ersetzt `LatinIME` bei jedem `onStartInputView` den Wörterbuch-Zugang
  von `InputLogic` (`setFacilitator`, Gesten-Datensammlung). Die Stufe-49-Tests setzen ihren
  gestellten Zugang deshalb in `setText` neu (`installRealWords`); ohne das ist jedes Wort „unecht“
  und nichts wird gelernt. Eine Änderung durch die App spielt `appChangesText` nach (Text und
  Auswahl im Schatten setzen, dann `onUpdateSelection`). Ein langsamer Zugang lässt sich nur mit
  `mLastSlowInputConnectionTime` weit in der Zukunft halten: jeder schnelle Aufruf kürzt den
  Zustand upstream um 5 min.
- **P150** Jede neue Textdatei `values/agent_*strings.xml` braucht
  `tools:ignore="MissingTranslation"` am `<resources>`-Tag (der Fork liefert nur Englisch und
  Deutsch); sonst bricht `lintDebugNoMinify` mit Fehlern für alle Upstream-Sprachen ab. Die
  Robolectric-Tests merken davon nichts, nur der Lint-Job auf GitHub.
- **P154** Robolectric stempelt Clips mit `SystemClock.uptimeMillis()`, ein Gerät mit der Wanduhr
  (`ClipboardService`). `ClipVault` misst das Alter ab dem Zeitstempel des Clips; `ClipVaultTest`
  setzt `ClipVault.clock` deshalb auf die Uptime, und für den Leisten-Chip (vergleicht mit
  `System.currentTimeMillis()`) setzt `stampNow` den Zeitstempel per Reflexion. Vor Android 8 hat ein
  Clip keine Zeit: dann gilt „jetzt“, ein Neustart der Tastatur innerhalb von 2 min kann ein
  schon eingefügtes Geheimnis wieder in den Tresor holen (bekannte Lücke). Dasselbe gilt ab
  Android 8 nach einem Neustart des Prozesses: `used` liegt nur im Speicher, `onCreate` liest den
  aktuellen Clip wieder ein, und solange er jünger als 2 min ist, steht das Geheimnis wieder im
  Tresor. Das passiert nur, wenn `clearSystemClip` den Clip nicht leeren konnte (oder durfte); sonst
  ist nichts mehr da. Bewusst offen: eine Liste eingefügter Clips auf der Platte wäre eine neue Spur.
- **P155** Passwortfelder zählen als Inkognito (`InputAttributes.mNoLearning`). Ein Clip, der
  kopiert wird, während ein Passwortfeld den Fokus hat, ist ein Geheimnis und landet nirgends,
  weder im Verlauf noch im Tresor. Im Passwortfeld selbst gibt es keinen Tresor (Auftrag); dort
  bleibt nur das Einfügen des Systems bzw. die Einfüge-Taste mit dem aktuellen Clip. Der System-Clip
  trägt diese Herkunft nicht: Wer den aktuellen Clip live liest (Feld-Vorschläge, Leisten-Chip),
  fragt `ClipVault.isSecretClip`, das `SensitiveClips` (hört seit der Nachprüfung 44/45 auch mit
  Verlauf aus mit, nur im Speicher) und die vom Tresor als Geheimnis sortierten Kopierzeiten kennt.
  Nur nach `ClipGuard.classify(text, IS_SENSITIVE)` zu fragen, bot eine im Passwortfeld kopierte
  PIN als Code an (`ClipVaultTest.aPinCopiedInAPasswordFieldIsNeverOffered*`). Kopiert man denselben
  Text danach in einem normalen Feld, bleibt er bis zum Neustart vertraulich (Hash des Textes).
- **P156** Leeren der System-Zwischenablage nach dem Einfügen eines Geheimnisses
  (`ClipVault.clearSystemClip`): nur wenn sie noch genau diesen Clip hält (Text und, ab Android 8,
  Zeitstempel). Laut AOSP-`ClipboardService` (Stand `main`, gelesen 2026-11-14) braucht
  `clearPrimaryClip` wie `setPrimaryClip` nur `OP_WRITE_CLIPBOARD` („Writing is allowed without
  focus“), keinen Fokus und keine Rolle als Standard-IME; eine Ablehnung (AppOps) ist still, darum
  liest die Funktion danach zurück. Vor Android 9 setzt `ClipboardManagerCompat` einen leeren Clip
  (kann eine „Kopiert“-Meldung zeigen). Eigene Verläufe anderer Apps (Samsung, Gboard-Sync) leert
  das nicht. Auf keinem Gerät geprüft.
- **P157** Die Tresorreihen entstehen erst, wenn etwas darin steht, und sitzen dann vor dem
  Abschnittstitel „Letzte“ (Index 0). `FlatClipboardTest` erwartet ohne Tresor den Titel als
  erstes Kind; die Liste wird um die Höhe der Reihen kürzer, höchstens auf ein Drittel. Der
  Tresor lebt im Prozess der Tastatur: Ein Neustart des Prozesses vergisst alles (gewollt; zum
  aktuellen Clip siehe P154).
- **P165** `TouchModel.load` steht in `onStartInputViewInternal` vor `loadSettings`: die
  `SettingsValues` sind dort noch die des vorigen Felds. Was zum neuen Feld gehört (Passwort,
  `IME_FLAG_NO_PERSONALIZED_LEARNING`, Paket, Feld-ID), liest Etappe 50 aus `editorInfo`, die
  Schalter (Inkognito, Pause, beide Trefferzonen-Schalter) aus den Einstellungen selbst;
  `learnFromWord` und `onKeyInput` prüfen beim Lernen zusätzlich die dann aktuellen
  `SettingsValues`. Mit ausgeschaltetem Schalter ruft `load` `discardSession`, nie `endSession`:
  `endSession` wertet die Sitzung aus und kann befördern, also die Basis ändern.
- **P166** Die Kurzzeitschicht darf nur Berührungen innerhalb der normalen Taste lernen
  (`TouchDynamics.IN_KEY`). Mit der alten Grenze `OUTLIER` = 0,9 trägt sich eine verschobene Zone
  selbst (Simulation in `movedZonesDoNotTeachThemselves`: Restverschiebung etwa 0,05 statt 0,01).
- **P167** Sitzungen enden erst beim nächsten Ereignis (Tipp, Taste, Feldstart, andere
  Tastaturgeometrie), nicht per Timer; eine Szene oder Beförderung entsteht also beim ersten
  Tippen nach der Pause. Wiederholte Tasten (`isKeyRepeat`) sind kein Ereignis. Die Wanduhr darf
  dabei nicht rückwärts laufen (Tests: Sitzungen chronologisch, sonst gilt die Pause nicht).
- **P168** `TouchModel.replace` und jede Beförderung überschreiben `previous`: „Auf vorherige
  Kalibrierung zurücksetzen“ geht genau einen Schritt zurück, nicht bis zur ersten Kalibrierung.
  Danach fehlt `previous`, der Knopf verschwindet.
- **P181** Szenen und Beobachtungen sind relativ zur Basis. Jede Änderung der Basis muss sie
  mitnehmen: die Beförderung im Modell (Szenen und übrige Beobachtungen um den Schritt),
  Kalibrierung und Zurücksetzen über `TouchModel.rebaseScenes` (Szenen je Region um die mittlere
  Änderung der angewandten Basis, Beobachtungen geleert). Kennt der Prozess noch keine Regionen
  (seit dem Start kein Wort mit dynamischen Zonen), gilt für alle Regionen der Mittelwert über alle
  Buchstaben mit Basis. Eine neue Stelle, die `dx`/`dy`/`count` setzt, braucht denselben Schritt.
- **P183** Nur ein Wort, das eigene Eingabe seit dem letzten Stand berührt hat, kann eine Korrektur
  sein: `onCodeInput` (Zeichen, Löschtaste) und `onPickSuggestionManually` melden die Schreibmarke
  vor der Eingabe (`ownEdit`), der Wächter hält daraus einen Bereich, den jeder Prüfpunkt leert.
  Ein Bereich reicht, weil die Schreibmarke zwischen zwei Prüfpunkten nur durch eigene Eingabe
  wandert. Das stimmt nur, weil auch die Bewegungen der Tastatur selbst Prüfpunkte sind:
  Leertasten-Wischen ruft vor `setSelection` `checkpointFieldCorrectionsBeforeOwnCursorMove`, denn
  seine Auswahl-Meldung ist erwartet und kehrt vor dem Prüfpunkt in `onUpdateSelection` zurück
  (zweite Nachprüfung 49). Ein neuer Weg, auf dem die Tastatur die Schreibmarke selbst setzt
  (`setExpectCursorMove` + `setSelection`), braucht denselben Aufruf, sonst geht eine Korrektur vor
  der Bewegung verloren und der Bereich wird zu breit. Änderungen der App, gemeinsames Bearbeiten und
  Rückgängig fern der eigenen Eingabe liegen außerhalb und zählen nicht; ändert die App aber das Wort,
  an dessen Rand oder in dem gerade getippt wurde (ihre Autokorrektur des eben getippten Worts), zählt
  das wie eine eigene Korrektur. Rückgängig über die Tastatur (`UNDO`/`REDO`) und Strg+Z/Strg+Y einer
  Hardware-Tastatur verwerfen den Stand (P170); Strg mit Z auf der Bildschirmtastatur ist nicht
  geprüft. Gestenschreiben ruft kein `ownEdit` (ein per Geste ersetztes Wort zählt nur, wenn danach
  eine Taste direkt am Wort folgt, etwa das Leerzeichen). Korrekturen über das
  Rechtschreib-Menü der App (`SuggestionSpan`) ersetzt die App selbst: Sie zählen nie. Ein neuer
  Eingabeweg, der Wörter des Nutzers ändert, muss `ownEdit` rufen. Der Bereich gilt als berührt, wenn
  er das neue Wort einschließlich seiner Ränder schneidet. `commitChosenWord` gibt das Paar getippt
  → abgeschickt nur weiter, wenn sich das Wort genau geändert hat (`keyboardCorrectionFrom`); sonst
  hielte ein abgeschicktes „ich“ das Zurücksetzen von „Ich“ auf „ich“ für die eigene Autokorrektur.
  Reine Groß-/Klein-Paare speichern `RejectedCorrections.remember` und `TypoMemory` trotzdem nicht.
- **P184** `checkpoint` liest höchstens `Constants.EDITOR_CONTENTS_CACHE_SIZE` (1024) Zeichen vor
  der Schreibmarke (meist aus dem Zwischenspeicher von `RichInputConnection`, ein Lesen bei der App
  nur, wenn der Speicher kürzer ist) und 300 danach (immer ein Aufruf bei der App), im Hauptthread
  und innerhalb der Batch-Bearbeitung. Bei `hasSlowInputConnection()` liest er nichts und verwirft
  den Stand (nichts gelernt, solange die App als langsam gilt). Wer die Grenzen hebt, riskiert, dass
  `getTextBeforeCursorAndDetectLaggyConnection` mitten im Commit die Caches neu lädt und die
  Tastatur 10 min als langsam gilt. Die Nachbartasten (`adjacency`) kommen aus der letzten
  Buchstaben-Tastatur (`KeyboardId.element.isAlphabet`). Gehalten werden nur die daraus gebauten
  `KeyAdjacency` und eine `WeakReference` auf das `Keyboard` als Kennung, nie das `Keyboard` selbst:
  sein `KeyboardId` hält `editorInfo` mit `initialSurroundingText`, also Feldtext. Jeder Prüfpunkt
  vergleicht die gezeigte Tastatur mit der Kennung (billig, solange sie gleich bleibt) und baut nur
  bei einer anderen Buchstaben-Tastatur neu, so wirkt ein Sprach- oder Layoutwechsel; `start` leert
  beides.
- **P182** `updateRegions` vergleicht die Geometrie aller Buchstabentasten, nicht das
  `Keyboard`-Objekt: Umschalten erzeugt ein anderes Objekt mit gleicher Geometrie und darf die
  Sitzung nicht beenden. Symbol-Layouts mit einzelnen Buchstaben (µ) liegen unter
  `MIN_LETTER_KEYS` = 10 und lassen die Regionen stehen. Die Szenezahlen werden auf 3
  Nachkommastellen gerundet gespeichert; Tests, die gespeicherte Szenen vergleichen, brauchen eine
  Toleranz von 1e-3.
- **P161** `EditorInfo` trägt keine Autofill-Kennungen (`autofillHints` gibt es nur in
  `ViewStructure` für Autofill-Dienste, Inline-Vorschläge kommen vom Autofill-Dienst). Ein Code-Feld
  erkennt die Tastatur nur an Hinweistext, `label`, `fieldName` und Typ; `FieldSignals.autofillHints`
  bleibt für Aufrufer mit eigener Quelle. Wörter wie „Postleitzahl“, „Gutschein“, „Promo“ machen
  aus „Code“ kein Code-Feld (`NOT_OTP`); wer die Wortlisten ändert, prüft
  `ContextSuggestTest.codeLikeFieldsThatAreNoCodeFields`.
- **P162** Einfügen in ein leeres Feld nicht über `AndroidEditorAdapter.insert`: dessen Policy lehnt
  Nicht-Text-Felder ab (`UNSUPPORTED_EDITOR`), Telefon- und Zahlenfelder bekämen so nie ihren Code.
  Der echte Host nimmt deshalb `LatinIME.onPrivateTextInput` (wie der Tresor, P178), nur bei
  unveränderter Schreibmarke; Text vor der Schreibmarke geht über `replaceCommandText`, das das
  getippte Wort abschließt, ohne zu korrigieren. `insertGeneratedText` würde außerdem das Lernen
  für die ganze Sitzung abschalten.
- **P163** Kontext-Chips nur nach Leerraum oder „:“: Die Chips ersetzen die Wortvorschläge
  (`tryShowCommandChips` vor den Vorschlägen). Mitten im Wort hätte jeder Hinweis („Video“) die
  Vorschläge des getippten Worts verdeckt. Als übergangen zählt ein Angebot, wenn ein späterer
  Aufbau es nicht mehr zeigt; jedes nur einmal, ein Tipp nimmt es aus `shown`. Wird der Aufbau
  einmal nicht gerufen (ein Befehl steht vor der Schreibmarke), zählt das Angebot beim nächsten
  Aufbau.
- **P164** `MyDataStore` öffnet CE-Speicher und Keystore: in den Einstellungen nur im entsperrten
  Inhalt (P134/P145), in der Tastatur nur über `MyDataCache.get`, das bei gesperrtem Gerät nichts
  liest und den Zwischenspeicher verwirft; `FieldSuggestionChips` fragt es erst nach allen
  Sperrprüfungen (`FieldSuggestionChipsTest.neverInPasswordFieldsIncognitoNoLearningOrLocked`
  zählt die Lesezugriffe). Robolectric hat keinen AndroidKeyStore: Tests geben `AesGcmSecretCipher`
  mit eigenem Schlüssel. Der Zwischenspeicher fällt in `onFinishInputView`; es gibt keinen eigenen
  Empfänger für `ACTION_SCREEN_OFF` (die Tastatur wird vorher ausgeblendet). Nachprüfung 44/45.
- **P178** Ein eingefügter privater Text (Tresor-Geheimnis, Code, Wert aus „Meine Daten“) darf nie
  das bearbeitete Wort werden: `LatinIME.onTextInput` ruft danach
  `restartSuggestionsOnWordTouchedByCursor`, das das Wort an der Schreibmarke zum Kompositionswort
  macht; die Leiste zeigte es dann im Klartext, und das nächste Leerzeichen lernte es ins
  Nutzerwörterbuch (das in manuellen Sicherungen steckt). Deshalb `onPrivateTextInput` und
  `InputLogic.markEnteredTextPrivate`/`markPrivateText`: kein Neustart der Vorschläge auf einem Wort,
  das ihn berührt (auch nach Rücktaste, angehängtem Buchstaben oder Antippen), kein Lernen, solange
  er das Wort oder dessen N-Gramm-Kontext ist. Drei Wege lernten trotzdem (zweite Nachprüfung):
  `mWordBeingCorrectedByCursor` wurde nach Rücktaste gemerkt und beim Wegsetzen der Schreibmarke
  gelernt (die Prüfung lief an der neuen Stelle; jetzt beim Merken), `revertCommit` nach einer
  Tresor-Wahl, die ein korrigiertes Wort beendete, setzte den Text (Trenner in `mLastComposedWord`)
  als Kompositionswort ein (jetzt wird `mLastComposedWord` geleert), und der Neustart in
  `handleNonSeparatorEvent` nach einem Wortverbinder fragte nicht. Bearbeitungen der Tastatur vor
  dem Text verschieben die Marke (`privateTextEdited`), andere (Gesten, gewählte Vorschläge, die App)
  findet `PrivateRanges.touches` über den Hash wieder (P185). Dritte Nachprüfung: eine Korrektur in
  einem privaten Text („Mustr“ → „Muster“ in einem Namen aus „Meine Daten“) wird nie ein Kandidat
  von Etappe 49 (`FieldCorrectionWatcher.PrivateText`), und ein beim Merken geprüftes
  `mWordBeingCorrectedByCursor` lernt auch an einer Schreibmarke neben einem privaten Text. Ein normaler Clip aus dem Verlauf geht
  findet `PrivateRanges.touches` über den Hash wieder (P185). Ein Wert aus „Meine Daten“, der über
  einen Chip in ein fremdes Feld kommt, ist dort ein privater Text (bis zu drei je Feld, etwa Name
  und Adresse); die eigenen Felder von „Meine Daten“ lernen gar nicht (P180). Ein normaler Clip aus dem Verlauf geht
  weiter über `onTextInput` (Upstream). Tests `InputLogicTest.aPrivateTextIsNeverComposedShownOrLearned`,
  `aWordTouchingAPrivateTextIsNotLearnedWhenTheCursorLeaves`,
  `aBackspaceAfterAPickThatEndedACorrectedWordDoesNotComposeIt`,
  `severalPrivateTextsAreKeptAndFollowEditsBeforeThem`, `learningResumesOnceThePrivateTextIsNoLongerContext`,
  `aLongPrivateTextIsMarkedAsCommitted`, `aPickedSuggestionBeforeAPrivateTextKeepsItsMarkOnIt`,
  `aFixInAPrivateTextIsNoFieldCorrection`, `aWordTakenAwayFromAPrivateTextIsLearnedNextToOne`.
- **P179** `PRAGMA secure_delete` gilt je Verbindung. `SQLiteDatabase` hat einen Verbindungs-Pool;
  ein `rawQuery` außerhalb einer Transaktion kann auf einer Lese-Verbindung landen. Darum setzt
  `ClipboardDao.deleteRows` es innerhalb der Lösch-Transaktion (dort hält der Thread die
  schreibende Verbindung). PRAGMAs, die eine Zeile liefern, gehen nur über `rawQuery`, nicht
  `execSQL`. `secure_delete` wirkt nicht rückwirkend: Darum `VACUUM` nach einer Bereinigung beim
  Start (`compactInBackground`, nicht in einer Transaktion, auf dem Thread `maintenance`, ohne
  `synchronized(this)`, damit Einfügen und Löschen auf dem Hauptthread nicht auf diese Sperre
  warten). Blockierfrei ist das nicht (dritte Nachprüfung): `VACUUM` hält die schreibende Verbindung
  des Pools, ein Einfügen oder Löschen auf dem Hauptthread wartet währenddessen dort. Beschlossen:
  so lassen, weil `compactInBackground` nur nach einer einmaligen Bereinigung läuft (alte
  Geheimnisse einmal je Versionscode, `SensitiveClips.migrateLegacy`) und die Datei höchstens 8 MiB
  hat; kein Aufschieben bis zum Ausblenden der Tastatur. `runCatching` bleibt.
  `GESTURE_DATA` liegt in derselben Datei und kann groß sein: Über `COMPACT_MAX_BYTES` = 8 MiB läuft
  kein `VACUUM` (die Datenbank wäre lange belegt); alte freie Seiten bleiben dann, bis SQLite sie
  wiederverwendet. Der WAL-Checkpoint nach dem Löschen läuft ebenfalls auf `maintenance` (zweite
  Nachprüfung N7). Auf keinem Gerät geprüft.
- **P180** Compose-Textfelder können `IME_FLAG_NO_PERSONALIZED_LEARNING` nicht setzen; die Felder von
  „Meine Daten“ tragen deshalb `privateImeOptions` = `AgentPreferences.NO_LEARNING_FIELD`, und
  `InputAttributes` zählt das als `mNoLearning` (damit Inkognito) in jeder Feldklasse und auf jeder
  API-Stufe, anders als das System-Flag (nur Text, ab Android 8, P139). Wer ein weiteres eigenes Feld
  ohne Lernen braucht, setzt dieselbe Kennung.
- **P174** Das Gedächtnis lernt nur über den Hook in `performAdditionToUserHistoryDictionary`, also
  nur Wörter, die die Engine selbst lernen würde (kein Befehl, kein KI-Text, kein eingefügter Text:
  `onTextInput` lernt nicht, auch nicht der angehängte Rest eines Chips). Die `SettingsValues` können
  dort noch die des vorigen Felds sein (P165): `Memory.mayLearn` prüft deshalb die `EditorInfo` des
  Felds und den Sperrzustand jetzt. Zur Wortfolge gehören nur die eigenen Commits, mit denen der
  Satzteil vor der Schreibmarke noch endet; vorhandener Text des Felds, Sprünge der Schreibmarke und
  Bearbeitungen brechen die Folge ab. Die Test-Verbindung von `InputLogicTest` liest höchstens 100
  Zeichen (`getTextFromConnection`): die Tests tippen jeden Satz in ein neues Feld.
- **P175** Längere Folgen starten bei Gewicht 1, nicht beim Gewicht ihrer Teile. Gemessen: Mit dem
  kleineren Teilgewicht als Start fiel die Präzision des Rests von 100 % auf 93 %, in Zufallssätzen
  kam 78-mal ein Rest-Chip (1 richtig), das nächste Wort fiel auf 55 %. Folge: Eine Folge braucht
  etwa zehn Wiederholungen für zwei Wörter Rest und rund zwanzig für einen langen; das ist gewollt.
- **P176** „Kennt kein Hauptwörterbuch“ meldet der Vorschlags-Thread (`Memory.onTyped` in
  `Suggest.addFromMemory`) für das zuletzt getippte Wort. Kam die Leiste beim schnellen Tippen nicht
  mehr dazu, passt das Wort beim Commit nicht, und die Nutzung zählt nicht (vorsichtig); dasselbe
  gilt für die Kurzform einer Abkürzung (P187). Wie Kontext- und Symbol-Lernen lernt das Gedächtnis
  nur mit `mUsePersonalizedDicts` (`mayLearn`, `onCorrectionUndone`; Standard aus, deren Ausschalten
  löscht es; zweite Nachprüfung 46): Gedächtnis-Tests in `InputLogicTest` setzen den Schalter in
  `startMemory`. In
  `InputLogicTest` läuft die verzögerte Leistenaktualisierung nicht: Tests rufen `updateSuggestions`
  selbst. Die echte Schwelle der Autokorrektur erreicht dort kein Wort (native Bewertung fehlt),
  darum prüft `SuggestTest`, dass ein eigenes Wort nicht korrigiert wird und als
  Vervollständigung hinter der Korrektur steht, nie an ihrer Stelle. `SuggestTest` hat ohne
  `loadSettings` mit einem Textfeld kein Feld: `Memory.isBlocked` sperrt dann alles.
- **P177** Gedächtnis-Chips nur nach Leerraum wie P163: Nach einem angetippten Vorschlag
  (Phantom-Leerzeichen) kommen sie erst mit dem getippten Leerzeichen. Der Rest wird über
  `Host.insertAtCursor(…, privateText = false)` = `onTextInput` ohne Leerzeichen danach angehängt:
  Rest und Baustein sind der eigene Text des Owners, kein privater Text (seit 8e344e9 ging jedes
  `insertAtCursor` über `onPrivateTextInput` und sperrte das Lernen der Wörter danach; Nachprüfung
  46 M4). Tresor, „Meine Daten“ und Codes bleiben privat (Standard `true`). Die Verstärkung (+1)
  macht `Memory.accepted` ausdrücklich, damit das Gedächtnis sich nicht selbst aus eingefügtem
  Text lernt. `SnippetStore.version`
  zählt nur Schreibvorgänge dieses Prozesses (Einstellungen und Tastatur laufen im selben Prozess);
  ein Baustein mit Feldern wird als `@snip Name` angehängt, den Rest macht `SnipCommand`. Die einzige
  Ersetzung ist der Chip einer gemerkten Abkürzung: genau das gerade getippte Kürzelwort, nur auf Tipp
  (ROADMAP 46). Seine Schreibung (`MemoryChips.expansionCase`, zweite Nachprüfung 46): großgeschriebene
  Kurzform → großer Anfang; kleine Kurzform → die gespeicherte Schreibung, außer sie wurde nur nach
  großgeschriebener Kurzform (Satzanfang) aufgezeichnet (`ShortcutBook.Remembered.fromCapitalShort`)
  und `Host.isKnownWord` (`AgentEditorController.isKnownWord` → `isValidSpellingWord`) kennt das erste
  Wort klein, dann klein. Offen: keine Chips direkt hinter privatem Text; `InputLogic.privateTextAtCursor`
  ist privat und wird parallel umgebaut, ein `Host`-Zugang fehlt noch.
- **P185** Private Texte werden ohne Klartext verfolgt (`PrivateRanges`): Start, Länge, Hash der
  ersten 32 Zeichen. Die Tastatur kennt nur ihre eigenen Bearbeitungen genau: `onCodeInput`
  (Zeichen, Rücktaste) und `onTextInput` melden sie als „Auswahl davor entfernt, bis zur Schreibmarke
  danach eingefügt“; das stimmt für alles hinter der Bearbeitungsstelle und für die Rücktaste im
  Text. Gesten, gewählte Vorschläge und Änderungen der App verschieben ihn ungemeldet; dann sucht
  `touches` den Hash bis 1024 Zeichen um seine letzte Stelle und vergisst ihn erst nach einem
  vollständigen Lesen ohne Fund. Dritte Nachprüfung: Eine Bearbeitung vor dem Wert verschiebt nur
  und behält den Hash (kein Lesen je Taste); neu gehasht wird nur nach einer Bearbeitung in den
  ersten 32 Zeichen, und davor prüft `beforeEdit` den Wert an seiner Stelle. Früher hashte jede
  Verschiebung neu an der errechneten Stelle; nach einer ungemeldeten Verschiebung war das der
  falsche Text, der Hash passte dann immer, und die Marke lag dauerhaft daneben. `null` oder ein zu
  kurz gelesener Text vor der Schreibmarke gilt als nicht lesbar (gesperrt, nichts vergessen); kommt
  nach der Schreibmarke weniger zurück, gilt das als Feldende. Ein Wert unter 8 Zeichen wird nur
  64 Zeichen um seine Stelle gesucht, damit er nicht auf gleichen Text weiter weg springt; steht
  der Kopf im Fenster mehrmals (Duplikate, kurze Werte), gelten alle Fundstellen als privat, bis
  nur noch eine übrig ist. Rest-Risiko: eine ungemeldete Verschiebung um mehr als das Fenster
  (bei kurzen Werten 64 Zeichen) oder um mehr als 32 Zeichen direkt vor einer Bearbeitung im Kopf. Werte
  weiter als das Fenster von der gefragten Stelle werden nicht geprüft (sie können sie nicht
  berühren). Die Prüfung beim Lernen zählt Wörter nach Leerraum (wie `NgramContextUtils`), nicht
  Zeichen: Das vierte Wort nach dem Text wird wieder gelernt. Unbekannte Schreibmarke oder Auswahl:
  gesperrt, nichts vergessen. Unit-Test `CT/clip/PrivateRangesTest.kt`; die Testverbindung von
  `InputLogicTest` liest höchstens 100 Zeichen, lange Werte (PEM) prüft nur der Unit-Test.
  Vierte Prüfung (Lead): Eine nach der Schreibmarke gekürzte Antwort, die vor dem bekannten Kopf
  endet und nicht leer ist, gilt als „nicht lesbar“ (nichts wird vergessen), nicht mehr als Feldende
  (`PrivateRangesTest.aReadCutShortAfterTheCursorForgetsNothing`). Bekannte Restkosten: bis 32
  Zeichen vor einem Wert liest `beforeEdit` je Taste einmal nach der Schreibmarke (IPC, ≤ ~64
  Zeichen); mehrdeutige Werte und lange Werte (PEM) lesen bei `touches` über den Cache hinaus; Werte
  über 8192 Zeichen sperren die 1024 Zeichen danach für die Sitzung (vorsichtig, übersperrend);
  eine ungemeldete Verschiebung über 32 Zeichen plus eine Bearbeitung im Kopf, Überschreiben einer
  Auswahl im Kopf und das neue Hashen nach `headUnread` können den Wert noch vergessen (selten).
- **P186** Die nachträgliche Bereinigung alter Geheimnisse im Verlauf läuft einmal je Versionscode,
  nicht bei jedem Start: Seit Etappe 44 kommen neue Geheimnisse nie in den Verlauf, und jeder Start
  löschte sonst Einträge neu nach den aktuellen Regeln (Regeländerungen wirken mit dem nächsten
  Update). Sortiert wird eine Kopie im Hintergrund, gelöscht auf dem Hauptthread, weil
  `ClipboardDao.cache` nicht threadsicher gelesen wird und die Ansicht über `listener` hängt; ohne
  `Handler`, weil Robolectric-Tests (`InputLogicTest`) `Handler` abfangen. Tests, die die Bereinigung
  erwarten, entfernen `PREF_OLD_HISTORY_CLEANED` wie nach einem Update.
- **P187** Eine gemerkte Abkürzung ist nie ein Kürzel des persönlichen Wörterbuchs: Mit
  „Autokorrektur-Kürzel“ (`PREF_AUTOCORRECT_SHORTCUTS`, Standard an) wendet die Engine ein
  Kürzel beim Leerzeichen selbst an, aus „mfg“ würden drei Wörter (gegen die Grenze des Owners).
  Darum bleibt sie in `shortcuts` (`CREATED`), die Erweiterung kommt als Chip, und der Tipp ersetzt
  über `replaceCommandText` nur „Kurzform + Leerraum“. Die Kurzform gilt als eigenes Wort
  (`ownLookup`), sonst korrigierte die Autokorrektur sie vor dem Chip weg. Als Abkürzung zählt eine
  Ersetzung nur, wenn kein Hauptwörterbuch die Kurzform kannte (`unknownCommits` aus `onTyped`);
  „Erweiterung beginnt mit der Kurzform“ lehnt `ShortcutRules.plausible` nur mit `shortIsWord` ab
  (zweite Nachprüfung 46: „Mo“ → „Montag“, „Prof“ → „Professor“ sind lernbar, „Haus“ →
  „Hausaufgaben“ scheitert schon an `unknownCommits`). Tests müssen darum nach der Kurzform
  `updateSuggestions` rufen oder `Memory.onTyped` melden. `ShortcutBook.note` überschreibt die
  Erweiterung nach `CREATED` nie und bevorzugt eine nach kleiner Kurzform aufgezeichnete Form
  (`shortcuts` in `v2` mit Fall-Spalte, `v1` wird noch gelesen). `setText` in `InputLogicTest`
  startet für das Gedächtnis kein neues Feld (`startEditor` läuft nur über `LatinIME.onStartInput`):
  Tests mit mehreren Feldern rufen `Memory.onFieldChanged`, sonst wirkt das erste Wort des nächsten
  „Felds“ wie eine Ersetzung. Test-Build 20 legte noch Kürzel im
  persönlichen Wörterbuch an; sie werden nicht migriert.
- **P188** Eine zurückgenommene Korrektur melden bis zu drei Wege: `revertCommit` (Löschtaste),
  das Antippen der getippten Form (48) und der Feldvergleich von 49 (Y → X), oft beim selben oder
  nächsten Commit. `onCorrectionUndone` zählt darum nichts sofort, sondern merkt das Wort vor;
  beim nächsten Commit zählt es, wenn es noch als ganzes Wort im gelesenen Text steht (auch als
  das gerade wieder abgeschickte Wort selbst), und nur, wenn „Wort@Position“ in diesem Feld noch
  nicht gezählt ist (`countedAt`, `LinkedHashSet`, das älteste fällt zuerst, auch normale
  Nutzungen). Seit der zweiten Nachprüfung meldet jeder Weg, wo das getippte Wort jetzt beginnt
  (`RejectedCorrections.remember(…, at)`: `revertCommit` die Schreibmarke nach dem Löschen, der Tipp
  von 48 Schreibmarke minus Wortlänge, der Feldvergleich `wordStart`); gezählt wird nur ein ganzes
  Wort genau an dieser Stelle und nur mit bekannter Schreibmarke, ein Weg ohne Stelle zählt nichts. Wird das Feld vorher verlassen, zählt
  die Rücknahme nicht (vorsichtig). Nach `revertCommit` ist das Wort wieder Kompositionswort; das
  Leerzeichen danach schickt es ab.
- **P198** Endlose Compose-Animationen (`rememberInfiniteTransition`, alles über
  `withInfiniteAnimationFrameNanos`) laufen im Compose-Test nur mit `mainClock.autoAdvance = false`.
  Mit automatisch laufender Uhr bricht die Testumgebung sie ab (`CancellationException` „Infinite
  animations are disabled on tests“), der Wert bleibt beim Startwert und startet auch nach dem
  Umschalten der Uhr nicht neu. Tests der Bewegung schalten die Uhr vor `setContent` auf manuell
  (anders als P62: eine eigene `withFrameNanos`-Schleife läuft weiter und hält `waitForIdle` auf).
  Robolectric legt beim Weiterstellen der Uhr weder Layout noch Zeichnen an: Ein Scrollen erreicht
  `onGloballyPositioned` nicht, Werte aus dem Zeichnen (`frameRateTrips`) bleiben alt. Die Tests
  von 37 zeichnen deshalb je Bild einmal `decorView.draw` in ein 1-×-1-Bitmap (Layout und alle
  Zeichenaufrufe laufen) und lassen dazwischen den Main-Looper laufen. Die Testuhr macht Bilder
  nur alle 16 ms: Schritte von 16,7 ms (60 Hz) lassen sich so nicht einzeln treffen, darum prüft
  der Test, dass der 60-Hz-Tropfen in fast jedem Bild einen Schritt von 1/144 macht und der
  30-Hz-Tropfen nie in zwei Bildern nacheinander. Ob der Empfänger beim Entfernen der Karte
  abgemeldet wird, zeigt `shadowOf(app).registeredReceivers`. Text mit `autoSize`: das
  `TextLayoutResult` aus `GetTextLayoutResult` nennt in `layoutInput.style.fontSize` die Größe des
  Stils, nicht die gewählte; geprüft wird über die Breite (`getLineRight`) gegen `TextMeasurer`.
  `Density(dichte, fontScale)` skaliert große Schrift nichtlinear wie Android 14 (21 sp bei 1,3
  nur etwa × 1,13), auch unter SDK 28.
- **P203** Smart Paste in der Leiste ist keine eigene Leistenansicht, sondern hängt an der Pille:
  `getClipboardSuggestionView` gibt dann die Reihe (`HorizontalScrollView` aus
  `KeyboardCommands.chipRow`) statt der Pille zurück. Wer dort etwas ausblendet, muss die Reihe
  ausblenden (`shownView`, `clipboardSuggestionView`), sonst bleiben die Chips nach dem Einfügen der
  Pille stehen. `chipRow` gibt der inneren Zeile die Kennung `agent_command_chips`, nach der Tests
  und Code getippte Befehle erkennen; `stripRow` nimmt sie ihr wieder. Im Scroll-Container misst
  die Zeile ohne Grenze: der gewichtete Pillentext nähme seine ganze Breite (bis 200 Zeichen) und
  schöbe die Chips aus dem Bild, darum `maxWidth`. Vorrang: `setNeutralSuggestionStrip` fragt zuerst
  `tryShowCommandChips` (Befehle, Formatwächter, Feld- und Kontext-Vorschläge, Gedächtnis,
  Emoji-Mix), erst danach die Pille; zeigt die Leiste einen Feld-Chip, gibt es weder Pille noch
  Smart Paste, so steht derselbe Einfüge-Chip nie doppelt da. Die Pille braucht
  „Zwischenablagen-Inhalt vorschlagen“ (anfangs aus); der Verlauf zeigt die Chips auch ohne.
- **P204** Der Leisten-Host prüft beim Tipp das Feld, für das die Pille gebaut wurde
  (`imeHost(…, editorInfo)`), nicht `LatinIME.currentInputEditorInfo`: in Robolectric ist das null
  (keine Eingabe gestartet), und ein neues Feld baut die Leiste ohnehin neu. Einfügen geht nur an
  derselben Schreibmarke ohne Auswahl (`agentCommandCursor`); in Robolectric ohne Verbindung ist
  sie −1, darum testet `SmartPasteChipsTest` das Einfügen über einen eigenen `StripHost` und den
  echten Weg nur bis zum Chip. Die Kartenreihe entsteht im Code in der Textspalte der Karte
  (`text.parent`); ein geändertes Upstream-Layout `clipboard_entry_key.xml` mit anderer Spalte
  zeigt still keine Chips mehr. Ein Tipp ohne Wirkung (Schreibmarke verschoben, gesperrt, keine
  App) macht den Chip nicht tot: `used` gilt nur nach einer Wirkung (Nachprüfung 40, B8). Die Karten
  fragen `SensitiveClips.matcher` einmal je Besuch, denn `ClipVault.isSecretClip` kennt nur die
  Hashes im Speicher, nicht das Protokoll von 24f für alte angeheftete Einträge (B4).
- **P205** Erkennung bewusst vorsichtig, und `EventParser` allein ist dafür zu großzügig: er liest
  „Guten Morgen“ als morgen, „Version 2.1“ als 2. Januar, „4h“ als 4 Uhr und „Abfahrt 18:30“ als
  Termin; `ClipDates` sperrt das vorher (Tests `versionNumbersAndAmountsAreNoDates`,
  `dayWordsAloneUnmarkedTimesAndGreetingsAreNoDates`, `openingHoursAndAvailabilityAreNoEvents`).
  Nachprüfung 40 (B3): „PHP 8.3.28“, „Version 2.10.27“ und „Firmware 3.1.99“ las das Tor als Datum
  mit Jahr; `D.M.YY` braucht jetzt „am“/„vom“/„on“/„el“ … davor oder zweistellige Tag und Monat,
  nach Version/Update/Release/v/Firmware/Build gilt keine Zahl mit Punkten, und Öffnungszeiten
  („Montag bis Freitag 9 bis 18 Uhr“, „Hotline ab 8 Uhr erreichbar“) sind kein Termin. Bekannt
  offen: „Tabelle 10.11.30“ (zweistellig) und „Preis 12.12.“ gelten weiter als Datum.
  Sendungsnummern: 12 bis 16 Ziffern sind auch Kunden-, Bestell- und Kontonummern, deshalb ohne
  Namen des Dienstes nur die Formate mit Präfix und Prüfziffer, und auch mit Namen nichts direkt
  hinter einer Kunden-, Bestell- oder Rechnungsnummer (B5); eine Kundennummer ohne Bezeichnung
  neben „DHL“ gilt weiter als Sendung. GLS-IDs aus 8 Zeichen sehen aus wie BICs („GENODEM1“ der GLS
  Bank) und wie beliebige Referenzen, darum nur mit „Track-ID“ davor und nie in BIC-Form.
  Telefon (B1): libphonenumber nimmt in DE fast jede Ziffernfolge von 7 bis 12 Stellen als gültige
  Nummer ohne Vorwahl-Null (Ortsnetz ohne 0), so wurden DHL-Paketnummern („340434161094“) und
  SAP-Bestellnummern („4500012345“) zu „Anrufen“. Smart Paste wählt nur Text mit „+“, „00“ oder
  „0“ am Anfang und gibt `tel:` und `Insert.PHONE` die E.164-Form; `ClipGuard` bleibt dabei (die
  Beschriftung „Telefon“ im Verlauf und Feld-Vorschläge 45 sehen solche Folgen weiter als Nummer).
  In Ländern ohne Vorwahl-Null (Spanien) und für US-Nummern ohne „1“ gibt es so nur mit „+“/„00“
  einen Anruf-Chip. Adressen (B7): die Suche nimmt nur Straßen- und PLZ-Ort-Teile, Fließtext bekommt
  nur „In Karten öffnen“ mit herausgesuchter Straße und PLZ; Straßennamen aus mehreren Wörtern vor
  einem zusammengesetzten Straßenwort („Termin Hauptstraße 5“) gelten als Fließtext. `;` in Links
  (B6): manche Server teilen auch an `;`; ein Teil mit `;` bleibt deshalb ganz, sonst ginge mit
  „utm_source=x;id=2“ auch `id=2` verloren. Die Seiten der Dienste (`Carrier`) sind ohne Netz nicht
  geprüft: Ändert ein Dienst
  seine Adresse, öffnet der Chip eine Fehlerseite (Gerätetest offen). `si` ist nur bei YouTube und
  Spotify ein Share-Parameter; anderswo kann er gebraucht werden und bleibt. Smart Paste liest nur
  Clips bis 2000 Zeichen und sortiert jeden Clip ein zweites Mal (`isSecretClip`, dann
  `recognize`); die Karten merken sich das Ergebnis je Eintrag.
- **P206** Ein Feld hat höchstens `PrivateRanges.MAX` = 3 Marken für private Texte; jede weitere
  verdrängt die älteste, auch die eines Tresor-Geheimnisses (P185). Smart Paste fügte anfangs alles
  privat ein, drei bereinigte Links nach einem Geheimnis nahmen ihm so die Marke (Nachprüfung 40,
  B2). Privat sind nur noch Code und IBAN (`SmartAction.insertsPrivately`: der Code wie aus dem
  Tresor, die IBAN wie derselbe Clip als Feld-Vorschlag 45); ein bereinigter Link geht wie die Pille
  über `onTextInput` (wird also wie jeder eingefügte Text danach wieder bearbeitbar und kann wie bei
  der Pille gelernt werden). Drei Codes oder IBANs nach einem Geheimnis verdrängen es weiterhin
  (bekannte Grenze von `PrivateRanges`). Test `SmartPasteChipsTest.aVaultSecretKeepsItsPrivateMarkAfterThreeSmartPasteInserts`
  (mit echtem `PrivateRanges`, gefüttert wie `InputLogic`; der echte Weg über `InputLogic` ist für
  Smart Paste nicht geprüft, P204).
- **P194** Robolectric-Tests mit Feld müssen `latinIME.onStartInput`/`onStartInputView` aufrufen,
  nicht `latinIME.mHandler.onStartInput…` wie `InputLogicTest`: Nur `LatinIME` startet die
  Editor-Sitzung des Controllers (`startEditor`); sonst meldet `editor.protection()` `NO_EDITOR` und
  das Zeichen-Kunst-Fenster (wie Panel und Rechner) öffnet nie. Eine Markierung braucht im Test
  `initialSelStart`/`initialSelEnd` im `EditorInfo` und dieselbe Auswahl in
  `ShadowInputMethodService`, sonst kennen Adapter und `RichInputConnection` sie nicht (N8).
  `KeyboardSwitcher.isShowingEmojiPalettes` ist in Robolectric immer falsch (`isShown` braucht ein
  Fenster); Tests prüfen die Sichtbarkeit von `emoji_palettes_view` und der Tastenansicht.
  Robolectric hat einen eigenen `FakeMediaProvider` für `media`: `ShadowContentResolver.setCursor`
  greift dort nicht, ein Test der Drehung registriert einen eigenen Provider
  (`Robolectric.setupContentProvider`, Bilddaten weiter über `registerInputStreamSupplier`, M4).
- **P195** Eine Zeichenfläche, die ihre Fläche nur in `onDraw` berechnet, ignoriert jede Berührung,
  solange sie nicht gezeichnet wurde (in Tests nie, auf dem Gerät vor dem ersten Bild).
  `CharArtCanvas` setzt die Fläche deshalb schon in `onSizeChanged`. Füllen hat während des Zugs
  keinen vorläufigen Schritt (`provisional` gibt `null`): die Flutfüllung läuft einmal beim
  Loslassen (Nachprüfung 53, M3).
- **P196** Das Popup der Emoji-Taste kommt aus `TextKeyData` für jedes Label „emoji“, also auch aus
  eigenen Funktionslayouts; ein eigenes Popup wird angehängt. Die Tastatur-Ebene kennt nur Passwort,
  „ohne Lernen“ und gesperrt (`KeyboardId`); die Einstellung Inkognito kennt sie nicht, die prüft
  erst `openCharArt` beim Tipp. Der Stift ist ein Text-Label: ein Icon bräuchte Einträge in allen
  vier Icon-Sätzen von `KeyboardIconsSet`. Das erste Popup-Label wird bei „Tasten-Hinweise
  anzeigen“ (Standard an) zum Hinweis der Taste (`findPopupHintLabelOrIcon`): jede Emoji-Taste zeigt
  klein „✎“. Entscheidung des Leads (Nachprüfung 53, N1): bleibt, es zeigt den Langdruck an; ein
  kurzer Tipp öffnet weiter die Emojis (Test `aShortTapOnTheEmojiKeyStillOpensTheEmojis`). TalkBack
  liest den Stift über `KeyCodeDescriptionMapper` als „Stift in der Emoji-Taste: Zeichen-Kunst“.
- **P197** Rückgängig nach dem Einfügen entfernt genau die Kunst vor der Schreibmarke. Hat
  `insertGeneratedText` ein ausstehendes automatisches Leerzeichen davor gesetzt, bleibt dieses
  stehen. Rückgängig geht nur, solange die Schreibmarke dort steht, wo sie nach dem Einfügen stand
  (`charArtUndoEnd`), und der Text dort unverändert ist; sonst ein Hinweis und nichts passiert.
  Nach „Markierung ersetzen“ steht die Kunst ebenso vor der Schreibmarke; Rückgängig setzt den
  markierten Text wieder ein, aber nicht die Markierung, und bietet kein zweites Ersetzen an.
  „Ersetzen“ ohne Markierung verlässt sich nicht auf `replaceCommandText` allein: das vergleicht
  nur den Text vor der Schreibmarke. Beim Öffnen muss `expectedSelectionStart` der gelesenen Länge
  entsprechen, beim Tipp die Schreibmarke wieder dort stehen und dahinter nichts (eine App, die
  inzwischen Text hinter der Schreibmarke ergänzt hat, hätte sonst Text vor einem Rest ersetzt, N2).
  Beide Wege und Rückgängig melden `onAgentTextSubmitted` (Umschalt-Zustand, Gestenrest), N3.
- **P209** `Sketch.rasterize()` gibt das behaltene Raster der fertigen Schritte zurück, nicht jedes
  Mal ein neues: wer es ändert, ändert die Zeichnung für alle. `add` zeichnet den neuen Schritt
  deshalb auf eine Kopie (ein früher geholtes Raster bleibt, wie es war), `undo` und `clear`
  verwerfen es. `preview(op)` gibt einen Puffer, den der nächste `preview` überschreibt: nur sofort
  lesen (Fenster und Zeichenfläche lesen ihn auf dem Hauptthread vor dem nächsten Zug). Ein Strich
  gilt als fortgesetzt, wenn Grundraster, Stärke und Radierer gleich sind und sein zuletzt
  gezeichneter Punkt noch an derselben Stelle der Liste steht; nur dann kommen allein die neuen
  Punkte dazu. Das ist gleich dem Neuzeichnen, weil ein Strich jedes Pixel gleich setzt, egal wie
  oft er es trifft; Linie und Kreis werden je Zug aus dem Grundraster neu gezeichnet (Test
  `keptRasterAndPreviewMatchDrawingEverythingAgain`). Vorher zeichnete jeder Zug alle bis zu 400
  Schritte und den ganzen Strich neu auf dem Hauptthread (Nachprüfung 53, M3).
- **P210** Ein Codeblock nur um Kunst (Bild, Groß-Schrift), Rahmen oder Nebeneinander, nie um Text
  in Unicode-Stilen oder normal allein: WhatsApp zeigte „𝐇𝐚𝐥𝐥𝐨“ sonst als Monospace mit
  Backticks, und in Apps ohne Profil galt WhatsApp als Standard (Nachprüfung 53, M1). Ohne Profil
  ist die App jetzt „Andere“ (kein Codeblock); die frühere Einstellung `agent_char_art_app` wird
  nicht mehr gelesen. Die Vorschau zeigt `ArtResult.text` mit den Backticks, die Chatblase im Reiter
  App weiter nur die Zeilen (so sieht es im Chat aus). Der Test
  `replaceTakesThePlaceOfTheFieldTextAndUndoBringsItBack` erwartet darum jetzt genau „𝐇𝐚𝐥𝐥𝐨“ statt
  „𝐇𝐚𝐥𝐥𝐨“ ohne umgebende Backticks; das ist strenger, keine Abschwächung.
- **P191** HeliBoards Facilitator erkennt die Sprache, in der getippt wird, an der ersten Gruppe
  (`mainLocale`, Großschreibung) und an der Konfidenz (`currentLocale`, Gewichte, Lernverlauf; bei
  Gleichstand gewinnt die erste Gruppe). Darum muss `getUsedLocales` die Hauptsprache zuerst liefern
  (`linkedSetOf`, nie ein `HashSet`) und `usesSameSettings` die Hauptsprache vergleichen: sonst blieb
  nach `@sp` mit Spanisch-mit-Deutsch Deutsch Hauptsprache und behielt seine Konfidenz (Spanisch mit
  0,7, Deutsch mit 1,0 gewichtet, „mui“ → „Mut“ statt „muy“, Etappe 51). Ein Neuaufbau mit denselben
  Sprachen benutzt die offenen Wörterbücher weiter (`getNewDictGroupsAndDictsToCleanup`) und beginnt
  mit frischer Konfidenz: dann wiegen beide Sprachen gleich, und ein in beiden gleich häufiges Wort
  entscheidet die Reihenfolge der Vorschläge (Punkte, Länge, Alphabet), nicht die Hauptsprache; die
  Hauptsprache gewinnt nur den Gleichstand der Konfidenz (`currentLocale`, Ziel des Lernverlaufs) und
  bestimmt die Großschreibung (Nachprüfung 51). In `InputLogicTest` schattet `ShadowFacilitator2`
  `addToUserHistory` und damit `adjustConfidences`: Tests zur Konfidenz rufen `adjustConfidences`
  selbst; in welche Gruppe gelernt wird, prüft kein Test. Echte
  Wörterbücher gibt es in Unit-Tests nicht; `ShadowWordListDictionaries` (nur per `@Config` an den
  Testmethoden) ersetzt allein das Öffnen der Hauptwörterbücher durch Wortlisten, deren Punkte
  (1,5 Mio. genau, 600 000 ein Schritt, 300 000 Anfang, plus 1000 je Häufigkeitspunkt, mal Gewicht der
  Sprache) die echte Schwelle der Autokorrektur erreichen; das Laden läuft asynchron, Tests warten mit
  `waitForLoadingMainDictionaries`.
- **P192** Lerndaten, die eine Korrektur *hinzufügen* (20b, 49), gehören zur Tastatursprache von
  `SettingsValues.mLocale`, beim Lernen wie beim Anwenden in `Suggest` (nicht zur Gruppe, aus der ein
  Vorschlag kommt): bei Deutsch mit Englisch als zweiter Sprache lernt alles für „de“. Neue Speicher,
  die etwas einsetzen, brauchen diese Sprache im Schlüssel; Speicher, die nur verhindern oder nur
  auf Tipp anbieten, dürfen sprachneutral bleiben (Liste in „Lerndaten“ → Sprache der Lerndaten).
  Einträge ohne Sprache (`""`, gespeichert vor 51) wirken nur über den Rückruf `knownInLanguage`;
  wer ihn mit `{ true }` füllt, holt das Durchsickern zurück (ein deutsches Paar setzte „danke“ in
  spanischen Text). Nach einem Sprachbefehl sind Einstellungen sofort neu, der Facilitator erst nach
  `MSG_REOPEN_DICTIONARIES`; in dieser kurzen Lücke gelten schon die Paare der neuen Sprache, und
  `Suggest.addForeignWord` meldet jedes Wort als bekannt (die Ergebnisse stammen noch von der alten
  Sprache, Nachprüfung 51 N5). Die Sprache eines Paars aus 49 ist die Sprache, in der die Feldsitzung
  getippt wurde, nicht die beim Prüfpunkt (P208).
- **P193** `ForeignWords` lernt über `Memory.onWordCommitted`, das jedes Wort zuerst weiterreicht
  (vor `Memory.mayLearn`, mit `readOnce`, damit der Text vor der Schreibmarke höchstens einmal
  gelesen wird); wer den Hook umbaut, behält diese Reihenfolge. „Kennt die Tastatursprache nicht“
  kommt wie P176 vom Vorschlags-Thread (`onTyped`): Kam die Leiste beim schnellen Tippen nicht dazu,
  zählt das Wort nicht (vorsichtig); die Tests rufen `updateSuggestions`. Bekannt heißt in jeder
  Schreibung, damit Groß-/Kleinkorrekturen (48 M1, „hand“ → „Hand“) nie zu gemerkten Fremdwörtern
  werden; Rücknahmen, die nur die Großschreibung ändern, zählen ebenfalls nicht. Die Leiste sieht
  nur beim Weg über die Löschtaste das zurückgeholte Wort; nach einer späteren Änderung im Feld (49)
  und nach dem Antippen der getippten Form (48) hat sie zuletzt ein anderes Wort gesehen. Deshalb
  prüft `Dictionaries.find` auf dem Worker jedes Wort zuerst im Hauptwörterbuch der Tastatursprache
  (getippt, klein, mit großem Anfang) und verwirft es, wenn es dort steht oder dieses Wörterbuch fehlt
  (Nachprüfung 51 M1); wer diese Prüfung entfernt, holt „hand“ als englisches Wort in Deutsch zurück.
  Die wartende Rücknahme verbraucht der nächste Commit, der sein eigenes Wort trotzdem zählt (N8).
  Die Regel „fur“ (20a/49) bleibt: Ein Wort einer anderen aktiven Sprache schützt erst, wenn
  der Owner es behalten hat (Rücknahme oder zweimal an anderer Stelle); dann gilt es nur in dieser
  Sprache, bis er es durch ein anderes Wort ersetzt (`onReplaced`, M2; sonst überstimmt das gemerkte
  Wort jede später gelernte eigene Korrektur, weil `applyLearnedCorrection` `isRemembered` vor
  `TypoMemory.meantWord` fragt). Tests setzen `worker` auf direkt, `enabledLocales` ist
  austauschbar. Gemerkte Wörter sind nie die automatische Korrektur, nur Vervollständigung hinter
  ihr.
- **P207** `ForeignWords.Dictionaries` hält Hauptwörterbücher je Locale nur auf dem Worker-Thread;
  kein anderer Thread darf sie anfassen (`closeDictionaries` schickt das Schließen dorthin). Sie
  werden zusätzlich zu denen des Facilitators geöffnet (das der Tastatursprache doppelt, Speicher wie
  zweite Sprachen, Gerätetest offen) und bleiben über einen Wechsel der Tastatursprache offen;
  geschlossen werden sie nur bei einer Prüfung nach dem Abschalten einer Sprache, in
  `LatinIME.onTrimMemory` (knapper Speicher; seit der zweiten Nachprüfung 51 auch
  `TRIM_MEMORY_BACKGROUND`, weil Android 14+ die Stufen für knappen Speicher kaum noch schickt),
  beim ersten Feldwechsel nach `IDLE_MILLIS` (10 min) ohne Prüfung (`lastCheck`, ohne Timer: ein
  verzögerter Handler liefe in den Robolectric-Tests mitten in `handleMessages`), in
  `LatinIME.resetSuggestMainDict` (ein Wörterbuch kam dazu oder fiel weg; HeliBoard lädt dort seine
  eigenen neu), in `AgentEditorController.destroy` und mit `clear`. Ein Wörterbuch, das nicht
  geladen werden konnte, wird je Locale gemerkt (`failed`) und erst nach einem dieser Schließen
  wieder versucht (vorher bei jeder Prüfung). Wer einen neuen Weg baut, auf dem Wörterbücher dazukommen,
  ruft `closeDictionaries`. Ein gemerktes Wort, das wieder benutzt wird, schreibt nichts (`dirty`); erst
  `onFieldChanged(context)` schreibt; wer einen neuen Weg zum Feldwechsel baut, ruft es mit Kontext
  (ohne Kontext bleibt die neue Zeit bis zum nächsten Aufruf mit Kontext nur im Speicher). Jede Aufgabe auf dem Worker läuft in `runCatching`: Ein Fehler in einem Wörterbuch darf den
  Tastatur-Prozess nicht beenden. In Tests läuft der Worker auf dem Test-Thread; `WordLists.opened`
  hält den Thread, damit die Öffnungen der Prüfungen von denen des Facilitators (Hintergrund)
  unterscheidbar sind.
- **P208** `FieldCorrectionWatcher` merkt sich die Tastatursprache eines Felds beim ersten Prüfpunkt
  (`sessionLanguage`); sieht ein späterer Prüfpunkt im selben Feld eine andere, lernt er dort kein
  Paar mehr (`languageSwitched`), denn der Text davor kann in der alten Sprache sein („hpld“ auf
  Deutsch getippt, nach `@eng` verbessert). „Feld“ ist `ForeignWords.fieldSession` (neu bei
  `AgentEditorController.startEditor`/`finishEditor`), nicht `start`: Ein Sprachwechsel ruft
  `InputLogic.onSubtypeChanged` → `startInput` → `start`, das Feld bleibt aber dasselbe. Wer die
  Sprache an `start` hängt, verliert den Wechsel. Ein von der App geleertes Feld beginnt in der
  Sprache von jetzt neu. Rücknahmen (`RejectedCorrections`) laufen weiter; `ForeignWords.onReplaced`
  seit der zweiten Nachprüfung 51 nicht mehr (das ersetzte Wort kann in der alten Sprache stehen,
  vergessen würde es aber für die neue). Lücke: Startet die App das Feld nach dem Sprachwechsel neu (`onStartInputView`, z. B. nach
  dem Löschen eines Befehls), ist es ein neues Feld in der neuen Sprache; eine Verbesserung im alten
  Text gilt dann für die neue Sprache, wenn deren Wörterbuch das gemeinte Wort kennt (sonst ist es
  kein Tippfehler im Sinn von `CorrectionRules.classify`). In `InputLogicTest` ruft nur `newField`
  `ForeignWords.onFieldChanged`; `setText` allein bleibt dasselbe Feld.
  Sprache. Die Prüfung gegen die anderen Wörterbücher läuft auf `ForeignWords.worker` und öffnet
  dort deren Hauptwörterbücher (Speicher wie zweite Sprachen); Tests setzen `worker` auf direkt,
  `enabledLocales` ist austauschbar. Gemerkte Wörter sind nie die automatische Korrektur, nur
  Vervollständigung hinter ihr.
- **P199** Sprache pro App (52): HeliBoard liest seine eigene App-Sprache nur mit „Sprache pro App“
  an (`Settings.getSubtypeForApp`) und speichert sie bei jedem Sprachwechsel in der App neu. Die
  Befehlssprache liegt darum getrennt in `CommandAppSettings`, und `LatinIME.onStartInputInternal`
  fragt `subtypeForApp` an beiden Stellen (auch im Blick von `SubtypeChoice.holds`, sonst hält eine
  Feldwahl gegen die falsche Sprache). Ein Befehl entfernt HeliBoards Eintrag der App, damit mit
  eingeschaltetem Schalter die letzte Wahl gilt; die Rücknahme stellt ihn wieder her. Hint-Locales
  des Felds gehen wie bei HeliBoards eigener App-Sprache vor (`SubtypeState.getSubtypeForLocales`).
  Die eigene Datei verhindert, dass jedes Setzen über `onSharedPreferenceChanged` die Tastatur neu
  lädt; die Schlüssel `saved_app_subtype_…` lösen dort kein Neuladen aus. Mit ausgeschaltetem
  Schalter gewänne die Befehlssprache auch gegen einen Wechsel im Feld, sobald die App das Feld
  neu startet (P219).
- **P200** Enter bei einem Befehl im neuen Aufbau, der nicht genau eine harmlose Deutung hat, wird
  genommen und nur mit einem Hinweis beantwortet (`Plan.hint`: unbekanntes Wort, auch „App
  unbekannt oder noch nicht geladen“, Grenze, Kette, nur Anzeige, unvollständig, sonst „Tippe auf
  die Vorschau“): sonst schickte ein Messenger „@lang whatsapp del“ als Nachricht ab. Seit
  Nachprüfung 52 nur, wenn die Wörter wie ein Befehl aussehen (P220); sonst gehört Enter der App
  („Hallo @gruppe wer kommt heute?“, ein bloßes `@mem`). Die alte Form behält ihr Enter (`@lang`,
  `@snip name`, `@clip wort`); ein Sprachwort schaltet mit Enter nur um, wenn es genau ein Name der
  Sprache ist (`matchLanguages(…, exactOnly = true)`: `@Fran`, `@Dan` können Namen sein) und genau
  einen Chip hat; ist die Sprache schon aktiv oder hat sie mehrere Layouts, nimmt Enter die Taste
  und sagt es. `onEnter` liefert null für die alte Form und für Text.
- **P201** App-Namen kommen aus `PackageManager.queryIntentActivities` (Launcher), auf einem
  eigenen Thread; im ersten Augenblick nach dem Start der Tastatur kennt die Leiste noch keine App
  (das Wort ist rot „unbekannt“, nichts läuft; Enter und der graue Chip sagen „App unbekannt oder
  noch nicht geladen“, `@lang whatsapp german` ist dann keine alte Form, P200). Robolectric liest mit
  der Systemquelle gleichzeitig zum Test; Tests setzen `InstalledApps.source` und danach wieder `SYSTEM`.
  Testhosts müssen die Schreibmarke beim Ersetzen mitführen (Rückgängig gilt nur an der
  Schreibmarke nach dem Entfernen) und groß genug starten (negativ = unbekannt).
- **P202** Farben im Textfeld wären Kompositionstext über Leerzeichen hinweg: `InputLogic`
  schließt das Wort an jedem Leerzeichen und Trenner ab (`handleSeparatorEvent` → `commitTyped`,
  Autokorrektur, Autoleerzeichen, Satzzeichen-Tausch), `RichInputConnection` führt Komposition und
  Schreibmarke selbst (`mComposingText`, erwartete Auswahl), und ein fremder Kompositionsbereich
  setzt in `onUpdateSelection` den Composer zurück. Dazu Löschtaste, Gesten und Enter-Hook. Das ist
  kein enger Eingriff; die Farben stehen deshalb nur in der Vorschau der Leiste. Unverändert in
  der Nachprüfung 52.
- **P211** Chip und Dialog „Build N ist da“ lesen `UpdateSettings.knownBuild`, nicht `status`: Ein
  fehlgeschlagener Download schreibt `status = Failed` (Seite Updates), und vorher verschwand damit
  jeder Hinweis auf den Build, obwohl er noch da ist. Nur `Updates.check` schreibt `known_build`
  (über `remember`), und nur nach einer erfolgreichen Prüfung; wer einen neuen Schreibweg baut, hält
  das ein. Ob der Build neuer ist, entscheidet immer die gerade installierte Version
  (`newerBuild(installed)`), nie `compared_version`: Nach einem Update ist der gespeicherte Build
  sonst noch „neu“.
- **P212** (Nachtrag 2026-11-19: `UpdateChip.build` gibt in Passwortfeldern nichts zurück; die Gerätetests hatten den Chip dort gesehen.
  Die eine Prüfung `UpdateChip.isPasswordField` (Flag der Engine, jede Passwort-Variante in `mInputType`, dazu der rohe
  `inputType` des Felds, wenn gegeben) gilt für den Chip und für `UpdateOnOpen`; keine zweite Kopie bauen. Test
  `AT/UpdateChipTest.neverInAPasswordField` mit vier Varianten.)
  Der Update-Chip gehört nur in `setNeutralSuggestionStrip` (nach der Pille), nie in
  `setSuggestedWords`/`tryShowCommandChips`: Dort würde er Wortvorschläge oder Befehle verdecken.
  `createView` liest bei jedem neutralen Streifen; `mIsLocked` und der gespeicherte Wert kommen
  zuerst, die Sperre (Binder-Aufrufe) wird live erst gefragt, wenn ein neuerer Build bekannt ist,
  und beim Tipp noch einmal. Die Zeile trägt nicht die Kennung `agent_command_chips`, sonst hielte
  Code, der Befehls-Chips sucht, sie für einen Befehl.
- **P213** `AgentUpdatePrompts` gibt einen Wert zurück und ist deshalb nicht neu startbar: Sein
  Zustand macht die aufrufende Lambda in `SettingsActivity` ungültig, nur so prüft die
  `else if`-Kette nach dem Schließen weiter (Gestendaten-Erinnerung). Wer es in eine
  `Unit`-Funktion umbaut, muss den Zustand heben. „Neu in Build N“ zählt beim Zeigen als gesehen
  (`LaunchedEffect`), sonst käme es nach jeder Drehung oder einem abgebrochenen Start wieder.
  Tests geben `freshInstall` vor, statt sich auf die Installationszeiten von Robolectric zu verlassen.
- **P214** Tempo eigener Ebenen (34): `0.35f` ist nicht 0,35. Mit `phase * speed` sprang eine
  Ebene am Phasenumbruch (ganze 200 Durchläufe, P107) um bis zu 0,006 Durchläufe. Der Renderer
  rechnet die Zeit der Ebene in `double` aus ganzen Schritten (`speedSteps / 20`); das Tempo ist
  darum auf 0,05 gerundet, und 200 × Schritte / 20 bleibt ganzzahlig.
- **P215** `RgbOwnRenderer` hat Zwischenwerte je Instanz (Farbdreh-Matrix des Bilds, Abstand zum
  Ring, Mitte des Spots) und gehört wie die Engine zu einem Thread. Nie in ein `object` legen: Das
  Hintergrund-Raster (`rgb-light`) und der UI-Thread (Tastenmitten, Leiste) rechnen gleichzeitig;
  `RgbTexture.ask` macht dafür eine eigene Engine und damit einen eigenen Renderer.
- **P216** `RgbEngine.base(x, y)` ohne Uhr sieht keine Tipps. Ringe, Spot und Reaktionen eigener
  Muster brauchen `base(x, y, nowMs)` (so in `RgbTexture.computeGrid` und `light`). Ein neues
  Raster wird nur bestellt, solange `hasActiveEffect` gilt; darum deckt `RgbOwnRenderer.tapActive`
  Nachleuchten und Gleiten (180 ms) ab, sonst bliebe das Raster mitten im Gleiten stehen.
- **P217** Die Einstellung nennt ein eigenes Muster nur über ID und Inhaltsprüfung: `RgbSettings.parse`
  gibt eine Referenz ohne Ebenen (`check` gesetzt), die wie das eingebaute `pattern` zeichnet. Wer
  zeichnet oder eine Zusammenfassung zeigt, liest über `RgbLighting.read` (löst mit `resolve` auf).
  Drei Wege, nicht vertauschen: `resolve` (Text aus Einstellung oder Profil, prüft ID und Inhalt),
  `refresh` (Einstellungen, die schon Muster dieses Telefons halten, nach einer Änderung der
  eigenen Liste: nur nach ID; `resolve` würde hier das gerade bearbeitete Muster verwerfen, weil
  sein Inhalt neu ist), `restamp` (gespeicherten Text auf eine geänderte Liste umschreiben). `own`
  wird nie vom gemeinsamen Look geerbt (`readLook`), sonst bekäme ein Element ohne eigenes Muster
  das des gemeinsamen Looks.
- **P218** Editor: Regler und Kurvenpunkte ändern beim Ziehen nur einen Entwurf für die Vorschau,
  der Name nur das Feld (gespeichert bei „Fertig“, Fokusverlust, Zurück, Duplizieren, Exportieren).
  Bei jedem Schritt oder Buchstaben zu speichern hieße, die ganze Liste zu kodieren, Beleuchtung und
  Designprofil umzuschreiben, `agent_rgb_patterns` (und damit die Tastatur) neu zu laden und Engines
  neu zu bauen, alles auf dem Main-Thread. Wer Duplizieren oder Exportieren anfasst: erst den Namen
  speichern, dann den aktuellen Stand aus `library` lesen (nicht das beim Zeichnen gefangene
  `edited`). Nach Datei-Arbeit auf `Dispatchers.IO` geht es mit
  `withContext(Dispatchers.Main)` zurück: In Robolectric lief die Fortsetzung sonst auf dem
  IO-Thread („Can't toast on a thread that has not called Looper.prepare()“).
- **P219** Sprache im Feld bei ausgeschalteter „Sprache pro App“ (Nachprüfung 52): Hat eine App eine
  Befehlssprache, setzte `onStartInputInternal` sie bei jedem Start eines Felds dieser App, auch wenn
  die App dasselbe Feld nur neu startet (`restarting`, z. B. nach dem Senden im Messenger); ein
  Wechsel mit der Sprachtaste im Feld war dann weg. `CommandAppSettings.onSwitchedInField` (aus
  `LatinIME.onCurrentInputMethodSubtypeChanged`, nach `saveSubtypeForApp`) merkt einen solchen
  Wechsel wie die Wahl eines Sprachbefehls (`SubtypeChoice.remember`), nur bei ausgeschaltetem
  Schalter, nur wenn für die App (oder alle) eine Befehlssprache gilt und nur, wenn der Wechsel
  nicht zu ihr selbst ging. Ein anderes Feld der App beginnt wieder in ihrer Sprache
  (`SubtypeChoice.holds` vergleicht das Feld). Nicht einfach `restarting` überspringen: Dann bliebe
  beim ersten Start eines neu geöffneten Felds, das die App als Neustart meldet, die Sprache von
  vorher. Der automatische Wechsel beim Feldstart (Hint-Locales, Sprache der App, Ende eines
  Hints) läuft auch hier durch, wird aber seit der zweiten Nachprüfung nicht gemerkt:
  `SubtypeSwitchLog.lastReason` (gerade von `onSwitched` notiert) in `FIELD_START`; ohne
  Änderung an `LatinIME`. Wer den Weg von `expectFromStart` über `internalSwitch` nach `onSwitched`
  ändert, prüft das mit. `onSwitched` und `lastReason` laufen beide in
  `onCurrentInputMethodSubtypeChanged`; ruft das System diese Methode gleichzeitig auf einem
  anderen Thread (HeliBoard warnt davor), kann `lastReason` den Grund des anderen Wechsels lesen,
  und eine Notiz von `expectFromStart` gilt nur 2 s: Ein Feldstart-Wechsel, der später läuft, gilt
  als `KEYBOARD` und wird gemerkt (selten, harmlos: dasselbe Feld behält dann die Sprache).
  Messenger wie WhatsApp haben für alle Chats ein Feld: Dort gilt ein Wechsel mit der Sprachtaste
  für alle Chats, bis ein anderes Feld beginnt.
- **P220** Text hinter einem Bereichswort ist kein Befehl (Nachprüfung 52): „Hallo @gruppe wer kommt
  heute?“ war ein `@group`-Befehl mit unbekannten Wörtern; Enter wurde geschluckt und die
  Autokorrektur war für den ganzen Rest der Zeile aus. `Step.looksLikeCommand` entscheidet für
  `CommandSystem.onEnter` (null = Enter der App) und `CommandSystem.protects` (`Command.protects`,
  aus `KeyboardCommands.guardsTextBefore`; zusätzlich geschützt, solange nur ein Wort hinter dem
  Bereichswort steht, das kann eine App sein, die gerade getippt wird oder noch nicht geladen ist).
  Seit der zweiten Nachprüfung reicht ein genau getipptes Aktionswort oder ein genauer Sprachname
  bei mehreren unbekannten Wörtern (`@guard google maps off`, bevor die Apps gelesen sind); seit
  der dritten nur als erstes oder letztes Wort und mit den unbekannten Wörtern in einem Stück von
  höchstens drei (ein möglicher App-Name), sonst schluckten Sätze wie „@group we set off at 9“ das
  Enter (P231). „@guard on duty tonight“ bleibt ein Befehl (erstes Wort bekannt, so seit Etappe 52).
  Ketten (`Active.parse`) nur über einen Trenner am Ende des früheren Teils oder hinter einem
  vollständigen früheren Befehl ohne unbekannte Wörter (`continuesChain`; eine alte Form zählt nur
  als `@lang`-Sprachwechsel): sonst nahm ein früheres Bereichswort ein späteres `@snip adr` in seine
  Kette, und der Enter-Hook der Bausteinfelder (Etappe 42) bekam es nie. `continuesChain` parst den
  früheren Teil bei jedem Lesen des Texts vor der Schreibmarke neu; nur wenn wirklich ein weiterer
  Bereichsbefehl davor steht; seit der zweiten Nachprüfung aus dem Zwischenspeicher (P230).
- **P221** Wirkungen vor dem Entfernen des Texts (Nachprüfung 52): Vorher entfernte ein Tipp erst den
  Befehl und scheiterte dann z. B. an einer vollen Liste; der Text war weg und nichts geändert.
  `execute` führt jetzt jede Wirkung aus (`Effect.run` → `Done`), entfernt dann den Befehl und erst
  danach `Done.after` (Sprache umschalten, ungepinnte Einträge des Verlaufs löschen); scheitert eine
  Wirkung oder lehnt der Editor das Entfernen ab (`ApplyOutcome.REJECTED`, Feld geändert), nimmt es
  die gelaufenen zurück (`undo`) und der Text bleibt. `UNKNOWN` sagt nichts Sicheres:
  `RichInputConnection` liefert es, wenn die Prüfung nach dem Commit scheitert (Text sehr
  wahrscheinlich weg), aber auch, wenn `setComposingRegion` oder `commitText` false gab oder nach dem
  Versuch etwas warf (Text sehr wahrscheinlich noch da). Seit der dritten Nachprüfung liest
  `execute` (`removed`) dann den Text vor der Schreibmarke neu (`Host.editorTextBeforeCursor`):
  Endet er noch mit dem Befehl oder ist er nicht lesbar, wie `REJECTED` (alles zurück); sonst
  bleiben die Wirkungen, `after` läuft, Rückgängig wird angeboten. `after` (auch `clearNonPinned`
  von `@clip`) läuft nie ohne bestätigtes Entfernen. Scheitert beim Zurücknehmen ein Schritt, sagt
  der Hinweis `agent_unified_failed` statt „Nichts geändert“. Zurücknehmen vor dem Entfernen schaltet keine Sprache
  zurück (`Done.afterRan`), sonst merkte `switchToSubtypeByCommand` die alte Sprache als Wahl des
  Felds. Eine Wirkung, die mittendrin scheitert (`languageEffect`, `guardEffect`, `memoryEffect`
  bei `all`), stellt selbst her, was sie schon geschrieben hat, und prüft die Grenze vor dem ersten
  Schreiben; seit der zweiten Nachprüfung auch bei einer Ausnahme mittendrin (`try`, dann
  `restore`, Ergebnis null). Wer eine Wirkung baut, die nicht zurückzunehmen ist (Daten löschen) oder die Tastatur
  oder das Feld ändert, legt sie in `after`. Das Schreiben von `FORMAT_GUARD`, `MEMORY` und
  `PREF_ENABLE_CLIPBOARD_HISTORY` lädt die Einstellungen neu (`Settings.onSharedPreferenceChanged`),
  bevor der Befehl entfernt wird; das ändert die Eingabeverbindung nicht.
- **P230** Gelesene Befehle zwischengespeichert (zweite Nachprüfung 52): Ein Tastendruck las
  denselben Befehl mehrmals (`guardsTextBefore` aus `updateGuard` und aus
  `InputLogic.performAdditionToUserHistoryDictionary`, `Active.parse` mit `continuesChain`, die
  Leiste, Enter). `CommandSystem.readings` hält die letzten vier Texte (`readingsCache`), gültig
  bei gleichem Präfix, gleichen Bereichswörtern, derselben App-Liste (`InstalledApps.apps`, gleiche
  Instanz) und gleicher `readingsGeneration`. Alles andere, was ein Wort benennen kann, muss bei
  einer Änderung `dropReadings` rufen: `execute` und Rückgängig, `CommandAppSettings.setGroup`,
  `SnipCommand.reset`, `KeyboardCommands.forget` (auch aus `load`, also bei geänderten Sprachen
  oder Befehlen), seit der dritten Nachprüfung auch die Bausteinseite der Einstellungen
  (`AgentSnippetsSection`: Speichern und Löschen rufen `SnipCommand.reset`). Wer einen neuen
  Wortschatz (Gruppen, Bausteine, Sprachen) oder einen neuen Schreibweg dafür baut, ruft es auch,
  sonst zeigt die Leiste für denselben Text die alte Deutung. Ein Lesen nimmt eine Momentaufnahme
  von `InstalledApps.apps` für Schlüssel und Wortschatz (nie zwei Listen gemischt).
- **P231** Satzwörter hinter einem Bereichswort (dritte Nachprüfung 52): Häufige Wörter sind
  Aktionswörter (an, aus, weg, neu, dazu, liste, zeig, on, off, set, new, add, `-`, `+`, `?`), und
  Bereichsbefehle laufen bis zu 200 Zeichen. Ein genaues Wort macht darum nur als erstes oder letztes
  Wort einen Befehl, mit höchstens drei unbekannten Wörtern in einem Stück, und kein unbekanntes Wort
  darf in `CommandGrammar.TEXT_WORDS` stehen (sonst wären „@sprache ist weg“ und „@mem ich bin aus
  Berlin“ wegen höchstens eines bzw. eines genauen Worts Befehle). Nachgeschlagen werden nur
  unbekannte Wörter; eine App, die genau so heißt und gelesen ist, ist vorher ein Ziel. Vierte
  Nachprüfung: Ein Satzwort nennt eine App nur mit ihrem ganzen Namen, nicht als Anfang, Namensteil
  oder Tippfehler (`CommandGrammar.appMeanings`; „das“ ist nicht Dashlane, „wir“ nicht Wire), sonst
  wäre es ein bekanntes erstes Wort und der Satz ein Befehl. Wer die Liste erweitert, nimmt keine
  Aktions- oder Trennwörter auf.

- **P222** IDs eigener Muster (34, Nachprüfung): nie wieder hochzählen (`max + 1`). Mit `p1`, `p2` …
  bekam ein neues Muster nach dem Löschen des neuesten dessen ID, und ein Designprofil, das nur
  `own=<id>` speichert, zeigte still ein anderes Muster; Profile von einem anderen Telefon trafen
  lokale IDs. Darum: zufällige ID mit 12 Zeichen (`RgbPatternLibrary.newId`, `SecureRandom`) und im
  Text die Inhaltsprüfung (`<id>.<prüfung>`, FNV-1a über die Ebenen ohne Namen), die
  `RgbPatternLibrary.match` vergleicht; passt sie nicht, gilt das eingebaute Muster. Weil eine
  Bearbeitung die Prüfung ändert, muss jeder Schreibweg der Liste über `RgbOwnPatterns.write` gehen
  (zieht Beleuchtung und Designprofil nach). Ein Wert ohne Prüfung gilt nur für eine alte, kurze ID;
  `migrate` ersetzt diese einmal, danach findet ein alter Profiltext nichts mehr.
- **P223** Tipps und Privatsphäre (34, Nachprüfung): Ringe, Spot und Aufleuchten zeigen die
  getippte Taste. `RgbLighting.onKeyPressed` nimmt in Passwort-/PIN-Feldern
  (`KeyboardId.isPasswordInput`, `InputAttributes`) und bei Inkognito (`mIncognitoModeEnabled`,
  schließt „keine Personalisierung“ ein) keine Tipps an. Einen eigenen Haken in `onStartInputView`
  gibt es nicht (LatinIME bleibt unberührt): LatinIME lädt für jedes andere Feld neue
  `SettingsValues`, bevor es die Tastatur setzt; `painterFor` und `onKeyPressed` vergleichen die
  Instanz (`WeakReference`) und rufen bei einer neuen `clearTaps`. Wer das Laden der Einstellungen
  ändert, muss diesen Wechsel erhalten.
- **P232** Urteil der Karte „Letztes Feld“ (55): `FieldReport.verdict` nennt nur den ersten Grund,
  in fester Reihenfolge: Passwort, gesperrt, Inkognito, kein Textfeld, Vorschläge (Leiste
  ausgeblendet vor Einstellung vor Firefox-Workaround vor App im Webfeld mit Web-Ausnahme vor App),
  Wörterbuch (fehlt vor nicht geladen), Autokorrektur (Einstellung vor Adressfeld ohne URL-Erkennung
  vor „App verlangt sie nicht“ ohne „Mehr Autokorrektur“), Großschreibung (Einstellung vor Schrift
  ohne Großbuchstaben vor keinen `CAP_*`-Flags), zuletzt der flüchtige Zustand der letzten Leiste:
  im Befehl, Chips der Tastatur, Pille (Zwischenablage, Screenshot, Update, gemaltes Symbol,
  Autofill), Wörter unter der Symbolleiste bzw. Emoji-Reihe, Symbolleiste bzw. Emoji-Reihe ohne
  Wörter, Leiste nie gemeldet („nicht erfasst“). „Alles an“ nur, wenn die Leiste zuletzt Wörter
  oder nichts im Weg zeigte. Zweite Nachprüfung (M-a): Eine Meldung ohne neuen Inhalt deckt die
  Leiste nur zu, nie auf; `hideWindow` (und das Schließen des flachen Menüs) klappt eine
  ausgeklappte Symbolleiste zu, bevor die Diagnose offen ist, das darf „Wörter unter der
  Symbolleiste“ nicht zu „Wörter“ machen (`LastField.onStripView`). Bekannte Grenze (N-b): ein
  Firefox-Webfeld ohne `AUTO_CORRECT`, dem nur der Workaround (`WEB_EDIT_TEXT`) die Autokorrektur
  nimmt, gilt als „App verlangt sie nicht“; beide Zahlen und „Workaround der Tastatur“ stehen auf der
  Karte, der genannte Ausweg „Mehr Autokorrektur“ stimmt. Die Zweige folgen den Formeln in `SettingsValues`
  (`mAutoCorrectEnabled`, `mSuggestionsEnabled`, `mOverrideShowingSuggestions`,
  `needsToLookupSuggestions`) und `InputAttributes`; wer dort etwas ändert, prüft die Reihenfolge
  und `CT/FieldReportTest`. „Die App verbietet Vorschläge“ ist eine Folgerung: Vorschläge aus,
  Leiste da, Einstellung an. Nachprüfung 55 (H1): `mInputType` ist schon durch
  `AppWorkarounds.adjustInputType` geändert (Firefox und Abkömmlinge: Webfelder mit
  `IME_MULTI_LINE` + `AUTO_CORRECT` bekommen `NO_SUGGESTIONS` und `WEB_EDIT_TEXT` von der
  Tastatur). Darum hält der Snapshot auch `rawInputType` (`EditorInfo.inputType`); fügte erst der
  Workaround `NO_SUGGESTIONS` hinzu, heißt der Grund `WORKAROUND_NO_SUGGESTIONS` (Ausweg: „Immer
  Vorschläge anzeigen“ an und die Web-Ausnahme `…_EXCEPT_WEB_TEXT`, Standard an, aus), und die
  Karte zeigt beide Zahlen. Nicht erfasst (Karte „Nicht erfasst“): ob hinter Chips oder Pillen
  Wörter bereitlagen (LatinIME gibt sie der Leiste dann gar nicht). `FieldReport` kopiert die
  Plattformzahlen; `AT/LastFieldTest` vergleicht sie mit `InputType`/`EditorInfo`.
- **P233** Zeitpunkt der Aufnahme (55): Felder der eigenen Einstellungen (Paketname der Tastatur, z. B.
  die Suche auf dem Weg zur Diagnose) nimmt `capture` nicht auf, das Feld der App davor bleibt
  (Test `aFieldOfTheKeyboardsOwnSettingsKeepsTheAppBefore`). `LastField.capture` steht in `onStartInputViewInternal`
  nach `loadSettings` (sonst wären es die Werte des vorigen Felds) und vor dem frühen Rücksprung
  ohne `MainKeyboardView` und vor `setNeutralSuggestionStrip` (die Zähler der Leiste beginnen
  dort neu). Ein Haken in `AgentEditorController.startEditor` wäre zu früh: `onStartInputView` ruft
  ihn vor dem Handler, die Einstellungen sind dann noch die alten. Die Wörterbücher laden im
  Hintergrund; darum fragt die Seite sie erst beim Öffnen (`LastField.live`) statt zur Aufnahme,
  und nur, wenn `mainLocale` noch die Sprache des Felds hat.
- **P234** Sprachwechsel im Feld (55): `SubtypeSwitchLog.onSwitched` läuft vor `loadKeyboard`,
  die Einstellungen sind dann noch die alten. Es setzt nur `LastField.onSubtypeSwitched`; erst
  `LastField.current` übernimmt `Settings.getValues()`, und nur, wenn deren `InputAttributes` zum
  selben Feld gehören (Paket und `mInputType`). Ohne Wechsel bleibt die Aufnahme, wie das Feld sie
  hatte: ein später geänderter Schalter (`Settings.onSharedPreferenceChanged` lädt mit denselben
  `InputAttributes` neu) ändert die Karte nicht.
- **P235** Datenschutz der Karte (55; für Felder ohne Lernen seit 61 durch P257 ersetzt): `FieldReport.Snapshot.Field` hat nur Paketname, Sprach-Tag,
  Zahlen und Wahrheitswerte (`CT/FieldReportTest.aSnapshotHoldsNoTextOfTheField`); nie
  `hintText`, `label`, `fieldName`, `privateImeOptions` oder Feldtext. Passwortfeld
  (`mIsPasswordField`, jede Passwort-Variante, auch im `rawInputType`) → nur `Password`; gesperrt
  (`mIsLocked`, das `isDeviceLocked` beim Feldstart liest; ein noch nicht entsperrter Nutzer liegt
  hinter dem Sperrbildschirm, Nachprüfung N5) → nur `Locked` (seit 61; vorher auch Inkognito, jetzt P257);
  beide ersetzen das Feld davor. Nur im Speicher (Einstellungen und Tastatur teilen den Prozess, wie
  bei `SubtypeSwitchLog`), nie geloggt. Der App-Name kommt erst beim Öffnen aus dem
  `PackageManager` und wird nicht gemerkt.
- **P236** Was die Leiste zeigte (Nachprüfung 55, M2/M3): `SuggestionStripView.reportToLastField`
  meldet nach neuem Inhalt (`setSuggestions`, `setExternalSuggestionView`: `newContent`) und nach
  jedem `setToolbarVisibility` (auch beim Ein- und Ausblenden der Emoji-Reihe) Wörter vorhanden,
  fremde Ansicht, Symbolleiste, Emoji-Reihe. `LastField` zählt erst beim nächsten neuen Inhalt
  (der Endzustand nach dem Ein- oder Ausklappen durch LatinIME zählt, nicht der Zwischenstand) und
  ordnet eine fremde Ansicht als „Chips der Tastatur“ ein, wenn `KeyboardCommands.createChipsView`
  direkt davor Chips meldete (`pendingChips`), sonst als Pille. `reportToLastField` steckt in
  `runCatching`, weil der `init`-Block von `SuggestionStripView` `setToolbarVisibility` aufrufen
  kann, bevor `suggestedWords` gesetzt ist. Solange die Tastatur in den eigenen Einstellungen offen
  ist (`ownField`), zählen `onStrip` und `onStripView` nichts; erst das nächste fremde Feld setzt
  es zurück.
- **P237** Prüfung beim Öffnen (56) nie auf dem Tipp-Pfad: `UpdateOnOpen.onStartInputView` läuft in
  jedem `onStartInputView`, darum auf dem Hauptthread nur Felder und Systemzustände lesen und erst
  nach allen Bedingungen (Schalter, 15 Minuten) die Binder-Fragen nach Netz und Datensparmodus; das
  Speichern (`commit`) und die Anfrage nur im `executor`. Nachprüfung 56: vor dem Lesen der
  CE-Einstellungen nur `isUserUnlocked` (einmal wahr, dann je Prozess gemerkt), die Keyguard-Fragen
  (`UpdateChip.locked`) erst nach Schalter und 15 Minuten; `Updates.check` läuft unter einem Lock, so
  dass Prüfung beim Öffnen, „Jetzt prüfen“ und täglicher Job nacheinander schreiben. Der Aufruf steht vor dem
  `synchronized`-Block von `LastField.capture`, weil dessen `return` in `runCatching` die ganze
  Funktion verlässt (eigene Einstellungen, keine Einstellungen). Ein `AtomicBoolean` verhindert eine
  zweite Prüfung, solange eine läuft. Unit-Tests erreichen nie das echte Netz: Robolectrics
  Standardnetz ist nicht `VALIDATED` (`UpdateOnOpenTest.theNetworkMustBeValidated…` hält das fest);
  wer `networkUsable` lockert, muss in allen Tests, die `onStartInputView` auslösen, `online`
  ersetzen. Die Versuchszeit ist Wanduhrzeit in CE-Einstellungen, damit ein Neustart die 15 Minuten
  nicht zurücksetzt; ein Überspringen schreibt keine Zeit.
- **P238** Chip sofort, aber nie statt Wörtern (56): Der Refresh nach einer Prüfung ruft
  `setNeutralSuggestionStrip` nur, wenn die Tastatur noch sichtbar im selben Feld ist (Paket,
  `fieldId`, `inputType` von `currentInputEditorInfo`) und `LastField.stripNeutral` die leere Leiste
  meldet (keine Wörter, keine Chips, keine Pille, keine Symbolleiste, keine Emoji-Reihe, kein Befehl).
  `LatinIME` gibt `mInputLogic` nur dem eigenen Paket; der Zustand der Leiste kommt darum aus der
  Meldung von `SuggestionStripView` (P236). In Passwort-, Inkognito- und gesperrten Feldern prüft die
  Tastatur nicht; in den eigenen Einstellungen prüft sie, aber `LastField` zählt dort nicht, der Chip
  kommt dann erst mit der nächsten neutralen Leiste. `UpdateChip.build` fragt die Sperre im Refresh noch einmal live.
- **P239** ETag (56): `etag` steht für die Release-Liste, aus der `known_build` und `compared_version`
  stammen. Darum gesendet nur, solange `compared_version` die installierte Version ist (nach einem
  Update fragt die App die volle Liste), und nur `remember` schreibt es (eine erfolgreiche 200-Antwort
  mit gültigem `ETag`, sonst gelöscht). Ein 304 rechnet den Status aus `newerBuild(installed)` neu,
  auch nach einem Fehlschlag. Ein 304 auf eine Anfrage ohne `If-None-Match` ist ein Fehler
  (`SERVICE`). `downloadAndInstall` fragt immer ohne ETag (es braucht die Assets). Jeder Fehlschlag
  (auch 403/429) behält Build, Notizen und ETag (P211) und verdoppelt die Wartezeit der Prüfung beim
  Öffnen; ein Erfolg (200 oder 304) setzt sie zurück. Laut GitHub-Doku zählt ein 304 nur bei
  angemeldeten Anfragen (`Authorization`) nicht gegen das Limit; ohne Anmeldung zählt vermutlich auch
  er gegen die 60 Anfragen je Stunde und IP (nicht gegen das echte GitHub geprüft). Mit höchstens vier
  Anfragen je Stunde und Gerät reicht das; hinter einer geteilten IP (CGNAT) mögliche 403 fängt die
  Wartezeit ab. Der ETag spart vor allem Datenmenge.
- **P240** Zwei Dateinamen (57): Jedes Release enthält vier Dateien, `SecondHand-build-<N>.apk(.sha256)`
  und `AgentKeyboard-build-<N>.apk(.sha256)` mit gleichen Bytes. Installierte Builds bis 27 suchen nur
  den alten Namen; ihn zu streichen schnitte sie von Updates ab. Jede `.sha256` muss den Namen der
  eigenen Datei tragen (`sha256sum` im Ordner der Datei), sonst lehnt `parseChecksum` sie ab.
  `UpdateReleases.newest` nimmt nie die APK des einen mit der Prüfsumme des anderen Namens. Beide
  Veröffentlichungsschritte in `agent-apk.yml` laden alle vier Dateien hoch; wer einen Schritt ändert,
  ändert beide. Streichen darf man den alten Namen erst, wenn keine Installation unter Build 28
  mehr existiert (`docs/BUILD.md`). Der Gradle-Ausgabename (`SecondHand_<Version>-…`) ist davon
  unabhängig: der Workflow nimmt die APK per Glob und benennt sie beim Kopieren. Die
  Workflow-Dateien (`agent-apk.yml`, `agent-tests.yml`) nie umbenennen oder verschieben, nur ihre
  `name:`-Zeile ändern: die Build-Nummer ist die Laufnummer je Workflow-Datei, eine neue Datei finge
  wieder bei 1 an, und jede Installation sähe eine niedrigere Versionsnummer und aktualisierte nie mehr.
- **P241** Upstream-Texte nicht überschreiben (57): `spell_checker_service_name`, `ime_settings` und
  `android_spell_checker_settings` stehen in `values/strings.xml` mit rund hundert Übersetzungen und
  sagen „HeliBoard“. Ein gleichnamiger Eintrag in einer Fork-Datei wäre ein doppelter Schlüssel.
  Darum eigene Schlüssel `agent_*` (`agent_name_strings.xml`), auf die `AndroidManifest.xml`,
  `res/xml/spellchecker.xml` und `SettingsActivity.kt` zeigen. Wer eine weitere Stelle findet, die
  den Upstream-Schlüssel nutzt, stellt sie ebenfalls um. `ime_settings` wird nirgends gelesen und hat darum keinen Fork-Ersatz.
- **P242** Der Name, den der Owner sieht, kommt aus der Variante `debugNoMinify` (die ausgelieferte
  APK), nicht aus `app/src/main/res`: `app/src/debug{,NoMinify}/res/values/strings.xml`
  überschreiben `english_ime_name` („SecondHand“, ohne „Lab“); die Fork-Namen der Rechtschreibprüfung
  kommen aus `main` (`agent_name_strings.xml`). Vor 57
  überschrieben sie die Upstream-Schlüssel (`spell_checker_service_name`, `ime_settings`) mit „Agent
  Keyboard Lab …“; die Manifest-Einträge zeigen jetzt auf die `agent_*`-Schlüssel, ein alter
  Upstream-Schlüssel dort bringt keinen alten Namen zurück. Neue Namensschlüssel immer in `main`
  und in beiden Debug-Dateien prüfen. `gesture_data.xml` und `clip_provider.xml` dort (Provider-
  Authorities der `.debug`-App-ID) nicht anfassen.
- **P243** Logo (58): Quellen sind die SVGs in `docs/referenzen/logo/`; die Vektor-Drawables
  (`ic_launcher_foreground/_monochrome/_background`) übernehmen ihre Koordinaten (Viewport 108).
  Die PNGs für Android 5–7 sind erzeugt: Ändert sich ein SVG, `node tools/render_logo.js` laufen
  lassen und die Vektor-Drawables von Hand nachziehen (kein Konverter). `AboutScreen` und
  `fake_toast.xml` lesen `@mipmap/ic_launcher_round`, ein eigenes Marken-Drawable gibt es nicht mehr.
  Die einfarbige Variante hat bewusst keine Füllung (Launcher färben die Fläche selbst ein). Store-
  Bilder unter `fastlane/` sind Upstream und nicht angefasst.
- **P254** Erzwingen und `mInputType` (61): `InputAttributes.mInputType` bleibt der Typ der App (nach
  `AppWorkarounds`); `isSameInputType` vergleicht ihn mit dem rohen `EditorInfo.inputType`, ein Unterschied
  ließe jeden Neustart desselben Felds als neues Feld zählen. Darum liegt die Entscheidung in
  `SettingsValues.mCorrectionForced` und wirkt nur an drei Stellen: `mAutoCorrectEnabled`,
  `mOverrideShowingSuggestions` und die Großschreibung (`capsInputType`). Wer eine vierte Stelle
  braucht, ändert nicht `mInputAttributes`. `mShouldShowSuggestions` und `mInputTypeShouldAutoCorrect`
  bleiben die Angaben der App (Karte „App erlaubt Vorschläge“).
- **P255** Was erzwungen wird (61, Nachprüfung 61): Textfelder einer gelisteten App, Entscheidung beim Feldstart
  (`ForcedApps.forces`, liest die Liste neu; ein geänderter Schlüssel lädt die Einstellungen neu, es gilt also ab
  dem nächsten Feld oder der nächsten Neuladung). Ausgeschlossen (rein, `ForcedCorrection.eligible`): jede
  Passwortvariante, URI, E-Mail (auch Web), Filter, Phonetik, Personenname, Postanschrift, Aktion „Suchen“ und
  jede Nicht-Textklasse; seit der Nachprüfung auch Felder mit `IME_FLAG_FORCE_ASCII` und, nur in einzeiligen Feldern, den Aktionen GO und NEXT (Anmelde- und
  Formularfelder; Chatfelder sind mehrzeilig, und `TextView` setzt NEXT selbst); `ForcedCorrection.excludedReason` nennt den Grund für die Karte; SEND, DONE, NONE, UNSPECIFIED und `AUTO_COMPLETE` bleiben zulässig. Zusätzlich nie bei `mIsPasswordField`. Webfelder (`WEB_EDIT_TEXT`) und
  E-Mail-Betreff sind nicht ausgeschlossen. Erzwungene Vorschläge umgehen auch die Web-Ausnahme von „Immer
  Vorschläge anzeigen“, weil der Owner die App ausdrücklich gelistet hat. Die eigenen Schalter gewinnen
  (`PREF_SHOW_SUGGESTIONS`, `PREF_AUTO_CORRECTION`, `mAutoCap`, Symbolleistenmodus). Lernen und Inkognito
  bleiben: `mNoLearning`/`mIncognitoModeEnabled` werden nicht angefasst, ein Feld mit
  `IME_FLAG_NO_PERSONALIZED_LEARNING` hat weiter keinen Nutzerverlauf. Bekannte Nebenwirkung: Mit erzwungenen
  Vorschlägen in einem `NO_SUGGESTIONS`-Feld setzt die Engine Composing-Text (wie bei „Immer Vorschläge
  anzeigen“); `RichInputConnection.setComposingText` prüft dort nach (die Prüfung und ihr `return false` sind unverändert; auf einem
  Gerät nicht geprüft). Zweite Nachprüfung 61 (M2): Ein Editor, der den Text ändert (z. B. nur Großbuchstaben) oder spät übernimmt,
  ließ die Prüfung scheitern; `InputLogic` setzt danach den Wort-Composer zurück, der nächste Buchstabe begann einen neuen Composing-Text
  und ersetzte den alten Bereich (gemessen: „hal welt“ ergab „AL ELT“). Darum ruft die Fehlerstelle `finishComposingText()` auf, bevor
  `false` zurückkommt: Der Text bleibt, der nächste Buchstabe wird angehängt, für dieses Wort gibt es keine Vorschläge. Das gilt für
  Editoren, die den Composing-Text annehmen (verändert oder spät). Ein Editor, der Composing-Text gar nicht annimmt, verliert in einem
  erzwungenen Feld weiter Zeichen (im Test mit dem dritten Ersatz-Editor kommt von „hal welt“ nur „al “ an, vor und nach der Änderung;
  keine solche App ist bekannt, auf keinem Gerät geprüft): dann die App aus der Liste nehmen. Die Stelle liegt in
  `RichInputConnection`, weil dort beide Zwischenspeicher (`mComposingText` und der Text davor) zusammen geführt werden; sie gilt
  auch für „Immer Vorschläge anzeigen“ ohne Erzwingen (Normaleditoren unverändert). Nachprüfung 61: Das Log dieser Prüfung nennt nur die Länge, nie den Text, und ein Aufruf
  `LastField.onComposingRefused()` (wirft nie) zählt jeden Fehlschlag für das Feld; die Karte zeigt „App nahm getippten Text
  nicht sofort an: n-mal“ und das Urteil sagt, dass Vorschläge und Autokorrektur abbrechen können. Nur die Zahl, sie beginnt mit
  jedem Feld neu. Zeigt Instagram nach diesem Build weiter keine Korrektur, zeigt der Screenshot der Karte, ob die App den Text
  verweigert. Ebenso gilt `mAutoCorrectEnabled == mAutoCorrectionEnabledPerUserSettings` in
  `DictionaryFacilitatorImpl` (Persönliches Wörterbuch, Opt-in) im erzwungenen Feld wie in einem normalen
  Feld.
- **P256** Großschreibung (61): `InputLogic.getCurrentAutoCapsState` liest `settingsValues.capsInputType(ei.inputType)`.
  `CAP_SENTENCES` kommt nur dazu, wenn das Feld erzwungen ist und kein `CAP_CHARACTERS`, `CAP_WORDS` oder
  `CAP_SENTENCES` gesetzt hat; setzt die App eins, gilt es. Andere Leser des rohen `inputType` (KeyboardId,
  `KeyboardLayoutSet`, `ClipboardHistoryManager`) bleiben bei der Angabe der App: Die Großschreibung
  betrifft nur den Auto-Shift-Zustand, nicht das Layout.
- **P257** Karte ohne Lernen (61, löst P235 ab): Felder mit `IME_FLAG_NO_PERSONALIZED_LEARNING`, den eigenen
  `NO_LEARNING_FIELD`-Feldern oder Nutzer-Inkognito bleiben als `Snapshot.Field` mit Feldtyp, Flags, Zahlen,
  Verdict und Ergebnis, aber `packageName = null` und `appForced = false` (kein App-Name; `appForced` ist dort aus); `noLearning`/`incognito` sagen warum, das Urteil hängt „Das Feld verlangt kein Lernen …“ an.
  Der Knopf der Karte fehlt dort (`Section.appPackage` ist `null`). `forced` bleibt wahr, wenn die Engine
  erzwungen hat, und die Karte zeigt „Korrektur erzwungen: ja“: Sie behält dort weder Paketname noch App-Name, verrät aber,
  ob erzwungen wurde (und damit mittelbar, dass die App in der Liste steht; `appForced` selbst ist dort aus). Die Zahl der verweigerten Eingaben bleibt, wenn die App dasselbe Feld neu startet (gesalzener Hash aus Paket, Feld-ID und Eingabetyp, `restartKey`), und beginnt bei einem anderen Feld neu. Zum Wiedererkennen
  desselben Felds nach einem Sprachwechsel hält `Field.fieldKey` einen Hash des Pakets mit einem zufälligen Salz je Prozess
  (nie angezeigt, nie gespeichert). Passwortfeld (jede Variante, auch roh) bleibt nur
  `Password`, gesperrt nur `Locked`. Der Update-Chip (`stripNeutral`) erscheint in Feldern ohne Lernen weiter
  nie, die Leistenzähler laufen aber. `LastField.current` übernimmt nach einem Sprachwechsel auch dort die neuen
  Einstellungen (Vergleich über `fieldKey`). Test: `AT/LastFieldTest.aFieldWithoutLearningKeepsItsReasonButNotTheApp`,
  `AT/DiagnoseScreenTest.aFieldWithoutLearningShowsFlagsAndVerdictButNotTheApp`, `CT/FieldReportTest.aSnapshotHoldsNoTextOfTheField`.
- **P258** Urteil und Erzwingen (61): `FieldReport.Verdict.forced` nennt nur, was die Erzwingung wirklich
  einschaltete (App hat Vorschläge verboten und die Schalter erlauben sie; die App hat keine Autokorrektur
  verlangt und „Mehr Autokorrektur“ ist aus; kein `CAP_*`-Flag und Großschreibung an). Der Hauptgrund
  (`finding`) folgt weiter P232 (`Verdict.composingRefused` hängt den Satz zur verweigerten Eingabe an), die Sätze der Erzwingung und des Lernens hängen dahinter
  (`Diagnose.verdictText`). `Snapshot.Field.capsUsed` ist, was die Engine liest, `caps` bleibt der Wunsch der
  App; `FieldReport.effective` und `CAPS_APP` nutzen `capsUsed`.
- **P244** Langer KI-Befehl, Lesefenster (59): Die Leser des Befehls vor der Schreibmarke lesen
  `LOOKBACK + 1` (519) Zeichen, nie mehr, weil `RichInputConnection.getTextBeforeCursor(n)` nur
  aus dem Cache (1024, schrumpft beim Löschen) antwortet, solange er `n` Zeichen hält; eine größere
  Konstante kostete in langem Text einen IPC je Tastendruck. Nur solange ein `@ki`/`@translate`
  getippt wird, merkt `KeyboardCommands.longRun` den absoluten Anfang (aus dem Parse mit dem
  kurzen Fenster) und `readLength(cursor)` liefert `Schreibmarke - Anfang + 1` (höchstens
  `LONG_LOOKBACK + OVERFLOW_GRACE + 1` = 2519). Der Zustand endet NUR, wenn (a) ein Lesen, das bis
  zum Anfang reicht, den Befehl dort nicht mehr findet (gelöscht, ersetzt, durch zwei Leerzeilen
  beendet, Kopf `@ki` geändert), (b) die Schreibmarke mehr als 2018 + 500 Zeichen dahinter steht,
  (c) ein anderes Feld beginnt (`load` mit anderem `FieldKey`: Paket, Feld-ID, Eingabetyp; dasselbe
  Feld nach Neustart oder Drehen behält ihn) oder (d) die Befehle ausgeschaltet werden. Eine
  Auswahl oder eine Schreibmarke vor dem Anfang machen ihn nur ruhend (`active` false: nichts
  geschützt, normale Lesefenster); die nächste einfache Schreibmarke dahinter lässt den Parse
  entscheiden. Ein angefangener Name (`@`, `@t`) oder ein anderer vollständiger Befehl mitten in der
  Frage beendet ihn nicht; ein zweites `@ki` darin übernimmt nur, wenn das äußere nicht mehr
  steht. Beenden außerdem: der Anfang liegt vor dem Text (`start < 0`) und ein fehlgeschlagenes Lesen (`getTextBeforeCursor`
  liefert nichts: nur ruhend, kein Verwerfen). Text, der vor dem Befehl gelöscht wird, verschiebt seinen
  Anfang nach links: das Lesen hat deshalb `START_SLACK` = 64 Zeichen Luft vor dem gemerkten Anfang, der Parse
  findet den Befehl am neuen Anfang und verankert neu (`trackLong`); wird mehr als etwa 64 Zeichen vor dem
  Befehl gelöscht, endet der Zustand, und eine Frage, die länger als das kurze Fenster ist, wird nicht mehr
  gefunden (dokumentiert, kein Fehler). Ebenfalls nicht erkannt, ohne Hinweis: ein Einfügen von mehr als etwa
  2500 Zeichen hinter `@ki ` auf einmal und eine ganze, längere als das kurze Fenster, auf einmal in ein leeres
  Feld eingefügte Frage (nie getippt, also nie gemerkt). Der Hinweis-Chip steht hinter dem Rückgängig-Chip.
  Länger als 2000 Zeichen ist kein Befehl mehr (nichts geschützt), `overflowHint` zeigt einen
  gedämpften Chip „Zu lang: höchstens 2000 Zeichen“ (Tipp = derselbe Text als Toast), solange die
  Schreibmarke höchstens 500 Zeichen über der Grenze steht; danach endet der Zustand. Ein alter langer Befehl, dessen Anfang
  außerhalb des kurzen Fensters liegt und der nicht hier getippt wurde, wird nicht erkannt
  (dokumentiert, kein Fehler). `guardsTextBefore` (InputLogic, kurzes Fenster) bleibt für Wörter
  eines laufenden langen Befehls `true` (`longRun.guards`), sonst würden sie gelernt und
  korrigiert. `updateGuard` und `readLength` hängen über `readCursor` zusammen (gleicher Thread,
  unmittelbar nacheinander). Test: `LongCommandTest` zählt die angeforderten Zeichen.
- **P245** Ausgabetoken und Abschneiden (59): `max_output_tokens`/`max_tokens`/`maxOutputTokens`
  zählen Denk-Token mit; ein fester Wert ließ lange Texte als „abgeschnitten“ enden. Die Grenze
  folgt der Eingabe (`ModelBudget`), mindestens 8192 bei allen drei Anbietern (auch eine kurze Frage
  braucht Platz für Reasoning) und nie über 16384 (ein Modell mit kleinerem Limit könnte die Anfrage
  ablehnen; nicht gegen eine echte API geprüft). Bei `@ki` und einer Frage im Panel nennt die
  Abschnitt-Meldung „Stell die Frage enger“ statt „weniger Text markieren“. Jede abgeschnittene Antwort ist `CloudError.CUT_OFF`, nie ein
  Ergebnis: OpenAI `status:"incomplete"` (auch eine `message` mit `status:"incomplete"`,
  `content_filter` ist `REFUSED`), Anthropic `max_tokens`/`model_context_window_exceeded`, Gemini
  `MAX_TOKENS` (auch ohne Inhalt). `TOO_LARGE` bleibt für Ergebnis über 65.536 und Körper über 1 MiB.
- **P246** Zeit ohne Streaming (59): ohne `stream` kommt das erste Byte erst nach der ganzen
  Generierung; `readTimeout` 10 s machte jede Antwort über 10 s zu `TIMEOUT`, praktisch eine
  Längenbegrenzung. Jetzt Lesen 120 s, Aufruf 150 s (`callTimeout` und `withTimeoutOrNull`),
  Workflow 310 s (bis zu drei Aufrufe, der dritte kann also am Gesamtbudget enden), Entwurf 160 s.
  Der Weg „Abbrechen“ (Leiste: Lade-Chip mit „Abbrechen“, Panel: `cancelButton`) ruft
  `ApprovedActionRunner.cancel`, das den Job beendet; `CloudTransport` bricht dabei den OkHttp-Call
  ab (`invokeOnCancellation`). Test: `LongTextBudgetTest`.
- **P247** Zeilenumbruch im KI-Befehl (59): Nur `@ki` und `@translate` dürfen mehrere Zeilen haben
  (`aiArguments`); `CommandSyntax.parse` sucht deshalb über Zeilenumbrüche zurück, und `parseAt`
  verwirft ein Argument mit Zeilenumbruch für jeden anderen Befehl. Folge: ein älteres `@ki` bleibt bis
  2000 Zeichen dahinter „der laufende Befehl“ (Chip, Autokorrektur aus), auch über eine Leerzeile.
  Zwei Leerzeilen hintereinander (drei Zeilenumbrüche, dazwischen nur Leerraum,
  `CommandSyntax.endsQuestion`) beenden die Frage; ebenso den Text von `@translate`. Senden
  bleibt der Tipp auf den Chip. Enter (Nachprüfung, M1): `AskCommand.onEnter`/`TranslateCommand.onEnter`
  sind `false`, aber `KeyboardCommands.onEnter` nimmt die Taste selbst (`holdsEnterForQuestion`), solange
  eine `@ki`/`@translate`-Frage mit Argument vor der Schreibmarke steht UND Enter in diesem Feld eine
  Editor-Aktion ausführen würde (dieselbe Funktion wie die Engine, `InputTypeUtils.getImeOptionsActionIdFromEditorInfo`
  ≠ `IME_ACTION_NONE`: Senden, Los, Fertig, eigene Beschriftung, UNSPECIFIED). Die Aktion läuft nicht; in
  einem Mehrzeilenfeld (`TYPE_TEXT_FLAG_MULTI_LINE`/`IME_MULTI_LINE`) geht der Zeilenumbruch über
  `LatinIME.onTextInput("\n")` (Pfad der Engine, ihre Caches bleiben stimmig), sonst passiert nichts und ein
  Toast (`agent_command_enter_blocked`) sagt es, einmal je Befehl (`enterHintFor`). Ohne Aktion
  (`NO_ENTER_ACTION`, NONE) bleibt es beim Zeilenumbruch der Engine. Eine bloße `@ki` ohne Argument und
  eine durch zwei Leerzeilen beendete Frage gehören wieder der App. Grenzen (Abschlussprüfung): Gehalten
  wird nur, solange der Parse die Frage im gelesenen Fenster findet, also nicht bei einer Auswahl, mit
  der Schreibmarke vor dem Befehl, jenseits von etwa 2500 Zeichen und nicht für eine Frage über dem
  kurzen Fenster (519 Zeichen), deren Zustand weg ist (anderes Feld, Neustart des Dienstes): dort führt
  Enter die Aktion aus. `onTextInput("\n")` ist nicht die Enter-Taste der Engine: Im Zustand PHANTOM
  (nach einem übernommenen Vorschlag) setzt es ein Leerzeichen vor den Umbruch, und manche Webviews
  machen aus `commitText("\n")` keinen Umbruch (ungeprüft). `onEnter` hat kein Fangnetz (Etappe 64). `StyledTranslation.request`: Stil eine Zeile, Text zuletzt.
- **P248** Sprache des Ergebnisses (59): Die Anweisungen sind englisch; ohne Regel antwortet ein
  Modell mitunter auf Englisch oder Deutsch statt in der Sprache des Textes.
  `AiTextAction.KEEP_LANGUAGE` (gemischter Text: jeder Teil in seiner Sprache) steht in CORRECT und
  REPHRASE, `KEEP_MAIN_LANGUAGE` (Hauptsprache des Textes, weil neuer Text entsteht) in SUMMARIZE,
  REPLY und ANALYZE; ASK und die
  zwei Übersetzungen behalten ihre Bedeutung. Ob das hilft, ist nicht an einem echten Modell geprüft.

- **P249** Satzsprache (60): Beweis sind nur die vorigen Wörter des Satzes aus dem `NgramContext` der Engine
  (bis zu drei, Abbruch am Satzanfang und an einem leeren Eintrag, etwa nach einem Komma), nie der Editor.
  Das erste Wort eines Satzes hat deshalb keinen Beweis und wird wie vorher korrigiert; ein Satzzeichen
  beginnt einen neuen Satz ohne Beweis aus dem davor. Der Test-Helfer `updateSuggestions()` übergibt
  `EMPTY_PREV_WORDS_INFO`; für diese Etappe gibt es `updateSuggestions(real = true)`.
- **P249a** Satzsprache (60): `applyLearnedCorrection` (gelerntes Tippfehler-Paar, 20b) läuft nach `guardAllows` und geht an der
  Regel vorbei, wie die Korrekturen ohne Locale (Gedächtnis, `ForeignWords`, Emoji).
- **P250** Satzsprache (60): Ein vorheriges Wort zählt für die Sprache der Korrektur oder für eine andere
  Gruppe nach dem Hauptwörterbuch, wie geschrieben oder klein (`mainLocalesKnowing`). „Andere Gruppe“
  heißt: eine Sprache, die auch das getippte Wort kennt (zweite Runde; mit drei Sprachen, Deutsch als Korrektur und
  getipptem „hand“ nur Englisch, ist ein spanisches „gracias“ kein Beweis, es ist neutral). Gewählt ist „das vorige Wort
  ist einer Sprache bekannt, die auch das getippte Wort kennt“ statt „nur solchen Sprachen“: Ein Wort, das Englisch und Spanisch kennen, Deutsch nicht, spricht gegen einen deutschen Satz und
  zählt für Englisch; ein Wort, das nur Deutsch und Spanisch kennen, zählt für die Korrektur. Kennen es beide,
  ist es neutral: ein kleingeschriebenes „hand“ vorher gilt nur englisch, aber ein großes „Hand“ wegen der
  Kleinschreibung beidseitig (neutral). Nicht in Benutzerverlauf, Kontakten oder dem persönlichen
  Wörterbuch (das würde Namen zu Beweisen machen).
- **P251** Satzsprache (60): Läuft nur, wenn die Korrektur sonst angewendet würde, eine Locale hat und das getippte
  Wort in einem Hauptwörterbuch einer anderen Sprache steht, aber nicht in dem der Korrektur (die seltene
  Ausnahme; beide Bedingungen, gefragt wird `mainLocalesKnowing(getippt)`), im Vorschlags-Thread,
  ohne Sperre und ohne Editorzugriff; die Wörterbuchabfragen sind lesend (`isValidWord`). Der Zugriff geht
  über `mDictionaryFacilitator as? DictionaryFacilitatorImpl`; `SuggestTest` ersetzt `mainLocalesKnowing`
  im Schatten `ShadowFacilitator` (sonst hat dort die Gruppenliste keine Wörterbücher und alles ist neutral).
  Locales: Gruppe, Hauptwörterbuch und Teilwörterbücher werden mit demselben `Locale`-Objekt angelegt
  (`DictionaryFactory.getDictionary(file, locale)` nimmt die Locale der Anfrage, nicht die der Datei), `de_CH` und `de`
  können also nicht gegeneinander stehen; getestet mit `de-CH` und `en-GB`. Geschlossenes Wörterbuch: `isValidWord` →
  `ReadOnlyBinaryDictionary.isInDictionary` nimmt nur `tryLock` (während des Schließens `false`),
  `BinaryDictionary.getFrequency` übergibt den Zeiger 0 an `getProbabilityNative`, das `NOT_A_PROBABILITY` liefert;
  im Java-Pfad kann nichts werfen, es gibt kein `try/catch`, ein geschlossenes Wörterbuch heißt „unbekannt“ (alte Regel).
  Ein fehlendes Hauptwörterbuch (`getDict` null) ist ebenso „unbekannt“.
- **P252** Satzsprache (60): `ForeignWords` (51) zählt hier nicht als „andere Sprache“. Es meint andere
  aktivierte Subtypen, deren Wörterbücher nur im Hintergrund geöffnet werden und die nicht im Tippen
  mitlaufen; die Regel hier gilt nur für die Gruppen des Facilitators. Wer Englisch nur als weiteren
  Subtyp (nicht als zweite Sprache) aktiviert hat, bekommt „name“ → „Name“ wie vorher.
- **P253** Satzsprache (60): Die Regel ändert nur, ob die Korrektur automatisch ist; die deutsche Korrektur
  bleibt Vorschlag, Reihenfolge, `TypoMemory`, die Regel von 48 (nur Groß-/Kleinschreibung) und Wischtippen
  bleiben unberührt. Beim Test mit Wortlisten bleibt ein getipptes englisches Wort, das nicht nur Groß-/
  Kleinschreibung unterscheidet („fur“ → „für“), in `InputLogicTest` unberührt (die Wortlisten werten das
  genaue Wort höher); dieser Fall ist nur in `SuggestTest` mit festen Punkten geprüft.
- **P259** Die ausgelieferte Variante ist nicht debuggable (62): `BuildConfig.DEBUG` ist dort false,
  das hat mehrere Folgen. (1) `Defaults.PREF_DEBUG_MODE` ist false, also ist `DebugFlags.DEBUG_ENABLED`
  aus: `InputLogic.commitChosenWord` schreibt keine Wörter mehr ins Logcat, `LatinIME` loggt keine
  `EditorInfo`, und die fünf Ausnahmen, die der Debug-Modus bei Auffälligkeiten warf, werden toleriert
  statt zu werfen: `InputLogic.java:1149` „Unknown event“, `InputLogic.java:2220` „revertCommit, but we
  are composing a word“, `InputLogic.java:2225` „revertCommit check failed“, `WordComposer.java:233`
  „Wrong cursor position“ (je `RuntimeException`) und `LatinIME.java:1044` Null-`EditorInfo` in
  `onStartInputView` (`NullPointerException`; ohne Debug-Modus kehrt die Methode still zurück). (2) `Constants.GET_SUGGESTED_WORDS_TIMEOUT` ist
  200 statt 500 ms (der Upstream-Wert eines normalen Builds; ein langsames Telefon kann dadurch
  Vorschläge verpassen, gemessen ist nichts). (3) Die Debug-Einstellungen (`AgentAreaSystemScreen`,
  `AboutScreen`, `AdvancedScreen`, `DebugScreen`) erscheinen nur, wenn „Debug-Einstellungen zeigen“
  (`PREF_SHOW_DEBUG_SETTINGS`) an ist: fünf Tipps auf die Versionszeile unter Über; dort lässt sich
  auch der Debug-Modus wieder einschalten. (4) `App.onCreate` ruft `upgradeToolbarPrefs` nicht mehr
  bei jedem Start auf, sondern nur beim Update (`AppUpgrade`, läuft bei jedem neuen Build); eine neue
  Symbolleisten-Taste erscheint also mit dem nächsten Build, nicht beim Neustart. Der Absturzbericht-
  Dialog (`SettingsActivity.findCrashReports`) liest bei ausgeschaltetem Debug-Modus nur den
  ungeschützten Ordner; die App schreibt selbst keine Berichte (`DebugFlags`). Unverändert:
  `BuildConfig.BUILD_TYPE` ist weiter `debugNoMinify`, die Unit-Tests laufen auf der Variante `debug`
  (dort ist `DEBUG` true), `debugNoMinifyImplementation("…ui-tooling")` bleibt (nur Größe). (5) Der
  native Teil der Vorschlags-Engine (`libjni_latinime.so`: Wörterbuchsuche, Wischtippen) wird jetzt
  optimiert übersetzt: AGP ruft `ndk-build` für eine debuggable Variante mit `NDK_DEBUG=1` auf (ohne
  Optimierung) und für eine nicht debuggable mit `NDK_DEBUG=0`; `isJniDebuggable = false` ändert daran
  nichts. Bis Build 27 lief dieser Code also unoptimiert (Codeabschnitt `.text` für arm64 in Build 27
  962.692 Bytes, im Stand von Etappe 62 647.508; die Aufgaben heißen deshalb
  `configureNdkBuildRelease[…]` statt `…Debug[…]`, Ordner `app/build/.cxx/Release`). Das ist
  wahrscheinlich der größte Einzelgrund für träge Vorschläge; gemessen ist es auf keinem Gerät. Beim
  Zurückstellen von `isDebuggable` Name der Variante, `.debug`, Signatur und Quellordner nie
  anfassen; die Diagnose zeigt, ob die installierte App debuggable ist (P261). (6) Weitere Stellen
  hängen an `DebugFlags.DEBUG_ENABLED` (geprüft im Code, im Stand von Build 27 war der Schalter an):
  - `utils/SubtypeSettings.kt:262` und `:273` (Laden der aktivierten Sprachen): Eine aktivierte Sprache
    ohne Ressourcen-Untertyp oder eine, die sich nicht laden lässt, zeigte im Debug-Modus nur einen
    Toast und blieb in der Liste; ohne Debug-Modus wird der Eintrag aus der Liste der aktivierten
    Sprachen entfernt. Wer nie einen solchen Toast gesehen hat, merkt nichts (es gab keinen defekten
    Eintrag). Wer ihn sah, verliert diese Sprache beim ersten Laden still und muss sie bei Bedarf unter
    Sprachen neu aktivieren.
  - `suggestions/SuggestionStripView.kt:475` (langes Drücken auf einen Vorschlag): zeigte im Debug-Modus
    in einigen Fällen die Quell-Wörterbuchliste (`showSourceDict`); jetzt immer nur „mehr Vorschläge“.
    `SuggestedWords.java:156`: die Debug-Texte der Vorschläge (`getDebugString`) sind aus.
  - `InputLogic.java:2010`/`:2062` (Zeitmessung um `performUpdateSuggestionStripSync`) und
    `:2731`–`:2798` (sechs Messungen in `commitChosenWord`): im Debug-Modus bei jeder Wortübernahme
    `SystemClock`-Aufrufe und Logeinträge; jetzt entfallen sie. `InputLogic.java:2218`–`:2230`
    (`revertCommit`): im Debug-Modus fragte die Prüfung bei jedem Zurücknehmen die App über
    `getTextBeforeCursor` nach dem Text vor dem Cursor, ein zusätzlicher Aufruf an die Texteingabe der
    fremden App; jetzt entfällt er.
  - `utils/FoldableUtils.kt:142`: ein zusätzlicher Hinge-Sensor-Listener (`featureString == null ||
    DEBUG_ENABLED`) wurde im Debug-Modus auch dort registriert, wo die Display-Features den
    Faltzustand schon liefern; jetzt nicht mehr. Weitere Logs dort (`:48`, `:89`, `:113`, `:129`).
  - `keyboard/PointerTracker.java:55` (`DEBUG_MODE = DEBUG_ENABLED || DEBUG_EVENT`, beim Laden der
    Klasse gelesen; Zeilen `:675`, `:990`, `:1037`, `:1414`, `:1423`): Touch-Protokolle, jetzt aus.
  - `MainKeyboardView.java:852`: im Debug-Modus zeigte die Leertaste Sprache und Konfidenz
    (`getLocaleAndConfidenceInfo`), jetzt wieder die Sprachbeschriftung.
  - Reine Logs, im Debug-Modus an und jetzt aus: `RichInputConnection.java` (`:511`, `:755`, `:791`,
    `:884`, `:919`, `:997`), `Suggest.kt` (`:728`, `:754`), `AndroidWordLevelSpellCheckerSession.kt`,
    `ContactsContentObserver.java`, `FloatingKeyboardUtils.kt`, `SettingsSubtype.kt:100`, `LatinIME.java`
    (`:949`, `:996`, `:1049`, `:1226`, `:1314`), `KeyboardState.kt`, `KeyboardBuilder.kt`,
    `KeyboardParser.kt`, `KeySpecParser.java`, `TouchPositionCorrection.java`,
    `BogusMoveEventDetector.java`, `AutoCorrectionUtils.java` und die Prüfungen beim Schreiben von
    Wörterbüchern (`makedict/*`). Hinweis: Weil `PointerTracker.DEBUG_MODE` statisch ist, gilt ein
    später eingeschalteter Debug-Modus für die Touch-Protokolle erst nach einem Neustart.
- **P260** Einmaliger Rückschritt des Debug-Modus (62): `A/DebugModeReset.kt` setzt einen
  gespeicherten `DebugSettings.PREF_DEBUG_MODE = true` genau einmal auf false (Schlüssel
  `agent_debug_mode_reset_done`). Aufruf in `AppUpgrade.onUpgrade`, in beiden Zweigen: beim
  Update und bei frischer Installation (die setzt nur die Marke, sonst würde ein später absichtlich
  eingeschalteter Debug-Modus beim ersten Update gelöscht). Der Schritt läuft vor `upgradeToolbarPrefs`
  und setzt auch `DebugFlags.DEBUG_ENABLED`, denn `App.onCreate` liest es vor der Upgrade-Prüfung.
  Er hängt am Versionswechsel: ein Telefon, das nie aktualisiert wird, behält den Wert. Tests:
  `AT/ForkUpgradeTest.kt`.
- **P261** Diagnose, Zeilen unter „App und Telefon“ (62): „Debugfähige App“ liest
  `ApplicationInfo.FLAG_DEBUGGABLE` der laufenden App, „Debug-Modus“ liest `DebugFlags.DEBUG_ENABLED`;
  beide kommen mit in den kopierten Text, ein Screenshot belegt also den Zustand. Texte am Ende von
  `agent_diagnose_strings.xml` (`agent_diagnose_debuggable`, `agent_diagnose_debug_mode`). Test:
  `DiagnoseScreenTest.theAppRowsProveWhetherTheAppIsDebuggableAndDebugModeIsOn`.
- **P262** Prüfungen von `agent-apk.yml` (63): Die Konstanten `OWNER_CERT_SHA256` (Kleinbuchstaben
  ohne Doppelpunkte; `docs/BUILD.md` hat dieselbe Zahl mit Doppelpunkten) und `APP_PACKAGE`
  stehen im Kopf des Workflows; ändert sich der Schlüssel des Owners oder die App-ID, beides dort und
  in `BUILD.md` nachziehen, `WorkflowContractTest` prüft die Gleichheit. Tag und Dateinamen
  (`RELEASE_TAG`, `APK_NAME`, `LEGACY_APK_NAME`) sind ebenfalls dort und müssen zu `UpdateReleases`
  passen (gleicher Test). Der Lauf der Nummer N im öffentlichen Repository kann das Tag nicht gegen
  den Commit prüfen (andere Historie); die Prüfung des Tags auf Commit-Gleichheit gilt für das
  private Release, das davor entsteht. Schritte mit Geheimnissen (`RELEASES_TOKEN`) geben nie die
  Umgebung aus; `set -x` nie einschalten. Der Zurücklese-Schritt wiederholt bis zu sechsmal (zehn
  Sekunden Abstand), die Liste kann kurz hinterherhinken. Er fragt zuerst ohne Token wie die App; für
  anonyme Anfragen gilt das Ratenlimit von GitHub je IP, und die Runner teilen sich Adressen: bei 403
  oder 429 fragt er mit dem Token des Laufs nach (im öffentlichen Repository ohne Rechte, sieht
  dieselbe Liste). Danach lädt er beide APK-Dateien und ihre `.sha256` ohne Token über `github.com`
  und vergleicht: die `.sha256` muss die des Laufs sein, die APK zu ihr passen. Das Tag nie mit
  `gh api …/git/ref/tags/…` suchen: bei einem fehlenden Tag schreibt `gh api` den Fehlertext auf die
  Standardausgabe, der dann wie ein Tag auf einem anderen Commit aussieht und jeden neuen Build
  stoppt; der Workflow fragt `git ls-remote --tags` (leer = kein Tag; das zweite Muster `…^{}` liefert
  bei einem von Hand angelegten, annotierten Tag den Commit dahinter). Der Zurücklese-Schritt wendet
  die Grenzen der App an (`UpdateReleases`: genau ein Asset je Name, APK 1 bis 120 MB, Prüfsumme 1 bis
  4096 Bytes, kein Entwurf). Ein Branchname kommt nur über `env` in ein Skript (`REF_NAME`), nie als
  `${{ … }}` im Skripttext. Nach `gh release upload` im Wiederholungszweig folgt `gh release edit
  --draft=false`: ein abgebrochener Upload des ersten Versuchs kann einen Entwurf hinterlassen.
- **P263** Test-Tor und `agent-tests.yml` (63): Das Tor vor dem öffentlichen Veröffentlichen liest die
  Pfade aus `agent-tests.yml` (Schlüssel `paths:` unter `push`, einfache Zeilenauswertung, kein
  YAML-Parser) und nimmt als Nachweis jeden grünen Lauf (`gh run list --workflow agent-tests.yml`,
  die letzten 100, braucht `actions: read`), dessen Commit in diesen Pfaden gleich dem gebauten ist
  (`git diff --quiet <sha> $GITHUB_SHA -- :(glob)<Pfad>`, daher `fetch-depth: 0`). Nicht den „letzten
  Commit, der die Pfade geändert hat“ verlangen: ein Push startet die Tests nur einmal, für den
  neuesten Commit des Pushs, und der ist oft ein Merge- oder Docs-Commit; das Tor fände dann nie
  einen Lauf. Ändert sich die Pfadliste, bleibt das Tor automatisch richtig; die Pfad-Liste muss
  `.github/workflows/agent-apk.yml` selbst enthalten, dazu `docs/BUILD.md` (der Vertragstest liest
  dort das Zertifikat) und `gradlew*`. Das Tor scheitert laut, statt weniger zu prüfen: eine Pfadzeile
  mit Kommentar dahinter oder mit `!` und ein Pfad, der auf keine Datei passt, brechen ab. Die Mindestzahlen je Job (`minimum` im
  Zusammenfassungsschritt; seit dem grünen Lauf 82 agent 1600 von 1788 und upstream 1330 von 1477)
  beim Hinzufügen von Tests auf etwa 90 % des Wertes eines grünen Laufs anheben; die fünf bekannten Fehler stehen mit exakten Namen in
  `known` (die zwei Cloud-Umgebungs-Fehler `layoutsLinksInternal`, `otherLinks` nicht: auf GitHub
  müssen sie bestehen).
- **P264** Messung der Tipp-Leistung (64a): `TypingPerf` darf nie Text, nie eine Meldung einer Ausnahme und nie ein
  Feldwort aufnehmen: jede neue Messstelle bekommt einen Eintrag in `Hook` (feste Namen, Beschriftung in
  `agent_diagnose_strings.xml`), keine Zeichenkette. Auf dem heißen Pfad gibt es keine Allokation (keine Lambdas mit
  Erfassung außerhalb der inline-Funktion `measure`, kein String, kein Logging); jeder Einstieg fängt Throwable und wirft nie
  in die Engine. Zählung der App-Anfragen nur dort, wo wirklich ein lesender `InputConnection`-Aufruf die App verlässt (nicht beim
  Antworten aus dem Textspeicher, nicht bei schreibenden Aufrufen): eine neue lesende Stelle in `RichInputConnection` braucht genau eine Zeile `TypingPerf.app()`.
  Der Tastendruck zählt Zeit bis zum nächsten `keyPress()`; Arbeit auf anderen Threads zählt nur bei den Anfragen. Die
  Karte zeigt nur, was seit dem Start des Prozesses oder dem letzten „Zurücksetzen“ gemessen wurde.
- **P265** Gemeinsamer Sperrzustand (64b): `LockState` darf nur „entsperrt“ aus dem Speicher melden, solange die Antwort
  jünger als `UNLOCKED_MILLIS` ist; jede neue Stelle, die Daten vor einem gesperrten Gerät schützt, fragt
  `LockState.isLocked` und nie selbst `KeyguardManager`, außer sie entscheidet etwas Unumkehrbares (Tresor, Zugangsdaten,
  Panel): dann live. Ein Tipp auf einen Chip verwirft die Antwort (`chipRow`, `bindTaps`); wer Chips anders verdrahtet, muss
  `LockState.drop()` vor der Prüfung selbst rufen. Tests, die das Schloss umschalten, müssen `LockState.drop()` rufen (so
  wie das Ereignis „Bildschirm aus“ es täte): sonst gilt die alte Antwort bis zu einer Sekunde.
- **P266** Zeilen und Erkennung merken (64b): `KeyboardCommands.rowFor` darf nur wiederverwenden, was nichts Gebundenes
  mehr enthält: die Tipps werden bei jeder Wiederverwendung neu gebunden. Was ein Chip über den Befehlstext oder die
  Schreibmarke weiß, steht in seinem `onTap`, nie in der Zeile. `SmartPasteChips.actions` und `ClipVault.classifyLive` merken
  nur reine Funktionen des Inhalts; Geheimmarken (`isSecretClip`: markiert, Passwortfeld, `secretStamps`) werden bei jedem
  Aufruf neu gefragt. Der Clip der Zwischenablage wird höchstens einmal je Sekunde gelesen: ein neuer Clip erscheint bei
  ausgeschaltetem Verlauf bis zu eine Sekunde später (mit Verlauf sofort, `offer`).

- **P287** Beleuchtung hinter Überlagerungen (70): `RgbLighting.isVisible` kennt nur die Tastatur-View.
  Jede neue Überlagerung in `main_keyboard_frame.xml` muss `AgentKeyboardFrame.updateOverlayAccessibility`
  aus ihrem `setVisibility` rufen (wie `FlatMenuView`), sonst läuft das Licht dahinter weiter und
  `isShown` bleibt `true`. Das Rad zählt mit. Das Menü des flachen Looks bedeckt nur die Tasten, die
  Leiste darüber bleibt sichtbar (Nachprüfung N1): `setOverlayOpen(open, menu)` kennt beide Zustände.
  Bei offenem Menü läuft das Licht weiter, wenn die Leiste leuchtet (`lightsStrip()`); `redraw`
  zeichnet dann nur die Leiste, die Tasten werden nicht neu gezeichnet. Leuchtet die Leiste nicht,
  pausiert alles wie bei den anderen Überlagerungen. Der Zustand ist statisch; ein abgehängter Frame
  setzt ihn zurück.
- **P288** Fangnetz im Painter (70): `RgbKeyPainter.guarded` fängt nur `Exception`, keine `Error`. Die
  Rückgabe nach einem Fehler ist der Eingabewert (`labelColor`), `restoreBackground` und
  `restoreIcon` laufen bewusst ungeschützt und immer, damit kein Farbfilter am Drawable hängen
  bleibt. `onPainterFailed` setzt den Painter am View nicht mitten im Zeichnen auf null (die
  Aufrufe in `KeyboardView.onDrawKey` lesen das Feld jedes Mal), sondern in einem Handler-Post.
  Es wird nie die Meldung der Ausnahme gespeichert, nur der Klassenname. Ein Fehler im Painter
  könnte Programmierfehler in anderen Render-Tests verdecken: `RgbLightingRenderTest` prüft nach jedem
  Bild und im `@After`, dass kein Fehler vermerkt ist (nur der Test, der einen auslöst, setzt ihn zurück).
- **P289** Abbruch auf der Leertaste (70, A2 und Nachprüfung M2): Mitten in einer Wischgeste ist der Tracker
  noch in der Warteschlange; `onCancelEvent` ruft `cancelAllPointerTrackers` (setzt nur „Aktion aus“) und
  `releaseAllPointers`, das `onUpEventInternal` aufruft und damit `mKeySwipeAllowed`, `sInKeySwipe`,
  `mInHorizontalSwipe`, `mInVerticalSwipe` und das Touchpad zurücksetzt (drei Tests, bestanden ohne
  Änderung). Anders nach einem Langdruck: Jeder Zweig von `onLongPressed`, der `cancelKeyTracking()` ruft
  (Sprachwahl über die Leertaste, Taste ohne Panel-Popup, `SPACE_HOLD`), nimmt den Tracker aus der
  Warteschlange. Ein späteres `ACTION_CANCEL` (die Sprachwahl öffnet ein Fenster) erreicht ihn dann nicht, und
  `mKeySwipeAllowed` und das statische `sInKeySwipe` blieben wahr: Der nächste Druck startete mit
  ausgeschalteter Gestenerkennung und im Wisch-Zweig, ein Wort durch Wischen konnte verloren gehen. Nur der
  Zweig `SPACE_HOLD` setzte die Flags selbst zurück. Behoben in `cancelKeyTracking`. Mit einem `ACTION_UP`
  statt des Abbruchs war der Zustand immer sauber (`onUpEventInternal`).
  Nachprüfung M-2 (zweiter Finger): `cancelKeyTracking()` löscht `mKeySwipeAllowed` und das statische `sInKeySwipe` nur, wenn dieser Tracker `mKeySwipeAllowed` gesetzt hatte, wie alle anderen Stellen. Sonst beendete der Langdruck eines zweiten Fingers auf einer Buchstabentaste die Wischgeste des ersten (ein dritter Druck konnte Gestenerkennung starten, während der Cursor wischt). Test `aSecondFingersLongPressDoesNotEndTheFirstFingersSwipe` (rot ohne die Bedingung, geprüft). Dürfen beide Finger wischen (beide auf Leertaste oder Entf), beendet der abgebrochene Langdruck des einen den Wisch des anderen, wie bei Upstreams Dauerfeuer und Panel-Langdruck; nichts bleibt hängen, ein Test dafür fehlt (Prüfung zu `e1d0131`).
- **P290** Hintergrundrechnen der Zeichen-Kunst und Z6 (70): `renderGeneration` zählt nur, was ein
  Hintergrundergebnis entwertet (`render()`, `resetContent`, Abhängen); ein Tick des Strichs zählt sie
  nicht. Es läuft höchstens eine Rechnung; Ticks währenddessen merken nur „neueres gewünscht“
  (`livePending`), das beim Zeigen des Ergebnisses startet. Würde jeder Tick entwerten, bliebe die
  Vorschau bei einer Rechnung über 40 ms den ganzen Strich lang stehen (Nachprüfung M1). Ein Fehler im
  Hintergrund (`Throwable`) leert die Vorschau; `render()` bleibt der Ort, an dem ein echter Fehler
  auftritt. Test-Haken `liveComputeHook` und `awaitLiveWorker`. Das Lesen von `sketch`/`raster` im Thread wäre ein Datenrennen (der Strich-Puffer wird
  wiederverwendet): immer die Kopie aus `renderInput(copy = true)` übergeben. Z6: `setNeutralSuggestionStrip`
  zeigt selbst Befehls-, Symbol-, Zwischenablage- und Update-Chips; ein weiterer Aufruf von
  `tryShowClipboardSuggestion` in `onStartInputViewInternal` darf sie nicht ersetzen. Folge (gewollt,
  Nachprüfung N5): Mit „Werkzeugleiste automatisch zeigen“ an und Chips in der Leiste öffnet sich die
  Werkzeugleiste beim Feldstart nicht mehr (vorher ersetzte oder verdeckte der zweite Aufruf die Chips).

- **P284** Suche nach Seiten (69): `SettingsContainer.filter` kennt nur `Setting`-Einträge. Jede Seite
  des Forks braucht dort einen eigenen Eintrag, sonst findet die Suche sie nicht:
  `createAgentPageSettings` (`AgentSearchEntries.kt`) baut ihn für die sieben Bereiche und jede Seite
  aus `SettingsAreas`, der Schlüssel ist `agent_page_<Route>`. Eine neue Seite muss in `SettingsAreas`
  stehen und ihre Suchwörter in `KEYWORDS` (`agent_search_strings.xml`, deutsch und englisch in einer
  Liste, `translatable="false"`); `SettingsSearchTest` bricht sonst. Seiten mit eigenem Eintrag
  (`AGENT_SPACE_ENTRY`, `AGENT_SYMBOLS_ENTRY`) stehen nicht doppelt darin.
- **P285** Abhängige Schalter (69): Nicht mehr ausblenden, sondern grau (`enabled = false`,
  `DISABLED_ROW_ALPHA`) mit dem Grund als Zusammenfassung (`disabledReason`); `SettingSwitchRow` und
  `SettingRow` haben dafür `enabled`. Ein Lambda nach den Klammern gehört bei lokalen Hilfen wie
  `SwitchRow` in `AgentAreaKeysScreen` zum letzten Parameter: `after` deshalb benennen (`after = { … }`),
  sonst meldet der Compiler einen Typfehler bei `reason`. Mindestgröße zum Tippen ist `MinTouch` = 48 dp;
  die Tests „everyClickableIsAtLeast48dp“ prüfen es je Seite.
- **P286** Alte Übergangsseite (69): `AgentAreaScreen` hatte hinter den sieben Weichen einen Zweig mit
  Karte „Seiten“, der nie erreicht wurde (`when` ist jetzt vollständig); die Strings `agent_area_pages`
  und `agent_area_pages_note` sind weg. `SettingsAreas.entries` bleibt: Karte der Seiten je Bereich für
  Tests und Suche. Die Tests prüfen mit dem alten Text, dass die Karte nicht wiederkommt.
- **P270** Eine Regel für „darf ein Clip bleiben“ (65, S2, Nachbesserung): `ClipVault.mayKeep(context,
  settings, sensitive)` gilt für Tresor, Verlauf (Text, Dateien, Bilder) und
  `ScreenshotChip.addToHistory` (dessen weitere Bedingungen: Verlauf an, Schalter und Berechtigung für
  Screenshots). Immer verboten: Gerät gesperrt (`mIsLocked`, `isDeviceLocked` und `isUserLocked`, der
  Direct-Boot-Zustand, bei dem der Bildschirm nicht gesperrt sein muss), „Immer Inkognito“ (direkt aus
  den Einstellungen gelesen) und ein vom System als vertraulich markierter Datei- oder Bildclip (bei
  Text sortiert der Tresor, wie vorher). Nur solange die Eingabeansicht in einem privaten Feld
  (Passwort, ohne Lernen, Inkognito) aktiv ist, zusätzlich verboten. „Aktiv“ ist der eigene Zustand der
  Gabel: `ClipVault.fieldStarted` aus `LastField.capture` (Start der Eingabeansicht), `fieldFinished` aus
  den zwei Zeilen in `LatinIME` (`onFinishInputInternal`, `onFinishInputViewInternal`); nicht
  `isInputViewShown` (Zustand der Engine, in Robolectric falsch). Ein Clip, der bei ausgeblendeter
  Tastatur irgendwo kopiert wird, bleibt also erhalten, sofern keine globale Bedingung ihn verbietet.
  Die frühere „strenge Lesart“ (zuletzt gesehenes Feld auch bei ausgeblendeter Tastatur) ist verworfen:
  sie ließ nach einem privaten Chat den Link aus dem Browser für immer aus dem Verlauf. Ein nicht
  behaltener Clip wird nie später nachgereicht. Die Passwortfeld-Marke in `fetchPrimaryClip`
  (Geheimnis für die Sortierung) gilt ebenfalls nur bei aktivem privatem Feld. Neue Wege, die Clips
  speichern, müssen `mayKeep` fragen.
  Nachprüfung 65 (Start des Dienstes): `ClipboardHistoryManager.onCreate` liest die Zwischenablage mit `fetchPrimaryClip(startup = true)` und schreibt dabei nie in den Verlauf: der Clip lag schon da, sein Feld kennt die Tastatur nicht (ein Passwort aus dem Feld einer anderen Tastatur wäre sonst im Klartext im Verlauf gelandet). `ClipVault.offer` stuft ihn weiter ein (Geheimnis-Stempel, `allowed = false`), speichert aber nichts. Test `aClipThatWasAlreadyOnTheClipboardWhenTheServiceStartsIsNotKept`.
- **P271** Zustand, der den Dienst überlebt (65, S8): Objekte (`ClipVault`, `ScreenshotChip`) halten
  Verweise (`currentClip`, `onChange`) auf Lambdas, die den Dienst festhalten. `onDestroy` von
  `ClipboardHistoryManager` setzt sie und die beiden Chip-Ansichten auf `null` und leert den Tresor;
  wer so einen Verweis neu einführt, räumt ihn dort ab (der Test prüft jeden einzeln). Der Tresor wird
  beim Beenden geleert, nicht beim Ausblenden (dort läuft die Ablaufzeit weiter).
- **P272** Der Feldtext des Beobachters (65, S12): `FieldCorrectionWatcher.reference` hält bis etwa 1300
  Zeichen des Felds. `FieldCorrectionWatcher.end()` wirft ihn, die Selbstkorrekturen und die eigenen
  Änderungen weg; aufgerufen über `ClipVault.fieldFinished(watcher)` aus den zwei Zeilen in `LatinIME`
  (Ende des Felds und Ausblenden; ein Neustart im selben Feld ruft ohnehin `start()`). Es verliert nur
  einen Vergleich, nie Gelerntes. `FieldEndHooksTest` ruft die echten `LatinIME`-Wege auf: fehlt eine der
  zwei Zeilen, scheitert er.
- **P273** Wiederherstellen (65, S3/S4, Nachbesserung): Ablauf in `restoreWithRollback`
  (`BackupRestorePreference.kt`): alte Kopie `heliboard.db_restored` löschen, `RestoreFolders.moveAside`,
  Arbeit, bei Erfolg `discardCopies`, bei einer Ausnahme `putBack` und beide Einstellungsdateien
  zurückschreiben (mit `commit`, Ergebnis geprüft). Jeder Schritt (`renameTo`, `deleteRecursively`)
  wird geprüft; eine Kopie `<Name>2` wird nur gelöscht, wenn ihr Ordner wieder an seinem Platz ist, und
  beim Verwerfen erst in `<Name>2_discard` umbenannt, damit eine halb gelöschte Kopie nie für einen
  Rückweg gehalten wird. Ein Rest `<Name>2` einer unterbrochenen früheren Wiederherstellung wird beim
  nächsten Versuch zuerst zurückgeholt (er ersetzt den halb geschriebenen Ordner), dann läuft die neue
  Wiederherstellung; gelingt das nicht, bricht sie ab. Meldungen: „Es wurde nichts geändert“ nur, wenn
  alles zurückgesetzt ist (Start nicht möglich, beschädigte oder nicht speicherbare Einstellungen);
  scheitert das Zurücksetzen selbst, sagt `restore_rollback_failed`, wo die alten Daten liegen. Jede
  andere Ausnahme zeigt den Text des Systems. Der frühere Fehler: die zweite Umbenennung hatte Quelle und
  Ziel vertauscht, ein Fehlschlag löschte den geschützten Ordner. **Restrisiko (nicht Teil von 65):**
  stirbt der Prozess mitten in der Wiederherstellung (Absturz, Akku, App beenden), bleibt der Zustand
  „Datenordner halb geschrieben, alter Stand in `<Name>2`“; erst der nächste Wiederherstellungsversuch
  holt ihn zurück, der Start der App tut es nicht. Unwahrscheinlich (das Fenster ist kurz, die Dateien
  klein), aber möglich. Behebung später: in einen Zwischenordner entpacken und am Ende tauschen, dazu
  Wiederherstellung des Rests beim App-Start. Ein weiteres Restrisiko: schlägt nach erfolgreicher
  Wiederherstellung sowohl das Umbenennen als auch das Löschen einer Kopie fehl, bleibt `<Name>2` mit
  altem Stand liegen; der nächste Versuch würde ihn zurückholen und die neuen Daten ersetzen.
  Nachprüfung 65: Scheitert der Rückweg (`rolledBack` falsch), läuft `LayoutUtilsCustom.removeMissingLayouts` nicht (sie würde die Einstellungen „fehlender“ eigener Layouts endgültig löschen, obwohl nur der Datenordner fehlt); der Dialog sagt, was die Datei enthält (von `dicts/` nur `*user.dict`, also von dir hinzugefügte Wörterbuchdateien; nicht das Persönliche Wörterbuch des Systems, nicht die mitgelieferten `.dict`, die bei Bedarf neu entstehen, nicht `agent_designs/`) und dass ein Wiederherstellen alles im Datenordner entfernt, was kein Muster trifft (Bilder eigener Designs, `libjni_latinime.so` der geladenen Gesten-Bibliothek). `LayoutUtilsCustom.removeMissingLayouts` läuft auch beim App-Start (`App.kt`): nach einem Prozesstod mitten im Wiederherstellen ist das derselbe Rest. Offen bleibt die Markerdatei in der Kopie `…2` (siehe oben).
- **P274** Protokollzeilen ohne Inhalt (65, Nachbesserung N2): nicht mehr geloggt werden das gewählte Emoji
  (`LatinIME.onEmojiSearchDone`, `EmojiSearchActivity.onStop`) und Tastenbeschriftungen importierter
  Layouts (`LayoutUtilsCustom`, `TextKeyData`). Bewusste Ausnahmen: `dumpAllWordsForDebug` (eine
  ausdrückliche Debug-Aktion des Nutzers) und Zeilen hinter `DebugFlags.DEBUG_ENABLED` (`Suggest.kt`,
  `AutoCorrectionUtils.java`, Spell-Checker-Sitzung, `GestureDataGathering`; nur im Debug-Modus, den die
  ausgelieferte Variante nicht hat).

- **P291** Slang-Liste (71): Ihre einzige Wirkung ist ein `false` in `Suggest.guardAllows`: das getippte Wort bleibt, der
  Vorschlag der Engine bleibt in der Leiste (`mWillAutoCorrect` aus). Sie schlägt nichts vor, lernt nichts und prüft keine
  Schreibung (auch nicht für die Rechtschreibprüfung anderer Apps). Der Check läuft bei jeder Aktualisierung der Vorschläge, in der die
  Engine eine Korrektur anwenden würde (bei einem unbekannten Wort oft schon nach vielen Tasten hintereinander), kostet Mikrosekunden (eine Mengenabfrage)
  und liest die Datei (14 KB) beim ersten Mal; ein Lesefehler ergibt eine leere Liste für diesen Aufruf und wird nicht gemerkt.
  Sie gilt für Tastatursprache oder Sprache der Korrektur `de`/`en`, nie für Spanisch allein, und nicht an
  „Personalisierte Vorschläge“ oder am Gedächtnis (feste Ausstattung, keine Lerndaten). Sie hält bewusst nichts auf, was der Owner oder das
  Wörterbuch ausdrücklich eingerichtet hat: kein Schutz, wenn die Korrektur nur die Großschreibung ändert (Stufe 48 entscheidet, „jungs“ wird
  „Jungs“), nicht gegen ein Kürzel des Persönlichen Wörterbuchs (`KIND_SHORTCUT`) und nicht gegen einen Eintrag der Whitelist des Wörterbuchs
  (`KIND_WHITELIST`). Gelernte Tippfehler-Paare (`applyLearnedCorrection`) und die Umschaltkorrektur der Großschreibung
  (`correctToCapitalizedWord`) laufen an der Liste vorbei: ein ausdrücklicher Wunsch geht vor. Einfache Anführungszeichen um das Wort
  zählen nicht („'lowkey'“), das zu einem Listenwort gehörende („goin'“) schon. Die Liste gilt nicht beim Gestentippen (`getSuggestedWordsForBatchInput`
  ruft `guardAllows` nicht auf; Listenwörter stehen in keinem Wörterbuch). Sie kennt keine Sprache je Wort: jedes Wort gilt für Deutsch und
  Englisch (das Paket nennt eine Sprache je Eintrag, sie wird nicht benutzt); ein englisches Wort nahe einem deutschen („ish“, „meh“) schützt
  deshalb auch auf der deutschen Tastatur. Der Zweig für `KIND_WHITELIST` hat heute keine Wirkung (kein Whitelist-Schlüssel der Wörterbücher
  steht auf der Liste) und keinen eigenen Test.
- **P292** Auswahl der Liste (71): Aufnehmen darf man nur, was die Wörterbücher **der App** nicht kennen (`main_de.dict`, `main_en-US.dict`,
  `main_en-GB.dict`; das Skript liest sie selbst, `read_app_dictionary`; groß geschrieben zählt mit, sonst bräche „jungs“ die Großschreibung
  der Stufe 48; auch „möglicherweise anstößige“ Einträge zählen als bekannt: Bit 0x01 heißt in der Engine `isPossiblyOffensive`, `isBlacklisted`
  ist dort immer falsch, und ein genau getipptes solches Wort gibt die Engine aus). Die Hunspell-Stämme des Pakets taugen dafür nicht: Die Nachprüfung 71 fand
  bei ihnen „gefixt“, „gepusht“, „zocken“ als bekannt (die Engine kennt sie nicht) und „texte“, „jungs“ als unbekannt (die Engine kennt sie).
  Keine Phrasen, Sonderzeichen oder Formen bis zwei Zeichen und keine Form, die ein Tippfehler eines sehr häufigen Wortes (Wahrscheinlichkeit ab
  150 von 255 in den App-Wörterbüchern oder Zipf ab 5,0 laut Häufigkeitsliste: die Wahrscheinlichkeiten sind keine Häufigkeit, „ich“ hat 139,
  „bist“ 105) sein kann (Buchstabe fehlt, überzähliger Buchstabe neben seinem Nachbarn, Nachbartaste, Dreher), wenn sie
  im Netz selten ist (Zipf unter 3,0 laut Häufigkeitsliste des Pakets, nur zum Entscheiden gelesen); über die Eigenhäufigkeit (Zipf ab 3,0)
  bleiben 43 Formen stehen, die einem sehr häufigen Wort nahe liegen („nich“, „nen“, „würd“, „habs“, „goin“, „meh“, „ish“, „welp“ …): ein
  Tippfehler dieser Art bleibt, wie getippt. „dont“, „cant“, „youre“ (Apostroph
  weggelassen) bleiben draußen, damit die Ergänzung des Apostrophs nicht wegfällt. Das Skript schreibt die Gründe nach `AUSWAHL.md`. Eine
  unverdiente Auslassung kostet nichts (Zustand wie vorher), ein unverdienter Schutz verhindert eine Korrektur. `InformalWordsTest` hält fest:
  kein Fehlschreibungs-Eintrag und kein Standardwort der Redaktionsfälle steht in der Liste (297 Wörter), die Slangfälle der Redaktion, die
  die Liste abdecken soll (45), stehen darin, und typische Hauptwörter („jungs“, „texte“, „feierabend“) nicht; ebenso Tippfehler sehr häufiger
  Wörter („idr“, „bish“, „bih“, „toml“) und „anstößige“ Wörterbuchwörter („fuck“, „geil“), die nicht darin stehen dürfen.
- **P293** Gedehnte Wörter und Lacher (71, `InformalWords.isExpressive`, wirkt auch ohne Listeneintrag): nur Buchstaben; letzter Buchstabe
  ein Vokal mindestens dreimal („neee“, „jaaaa“, „hääää“: „neee“ ist ein Fall der Redaktion, der bleiben soll), ein Konsonant mindestens viermal
  („yesssss“), oder eine Silbe aus zwei Buchstaben mindestens dreimal („hahaha“). Ein Konsonant dreimal („addresss“, „wennn“) bleibt Tippfehler,
  weil Wörter mit doppeltem Endbuchstaben den dritten aus Versehen bekommen. Dasselbe gilt für Vokale (Wörter auf „ee“, „oo“: „see“, „too“,
  „Idee“): „seee“ und „Kaffeee“ bleiben deshalb stehen, wie getippt; das ist der Preis dafür, „neee“ zu schützen (nach dem Wörterbuch
  lässt es sich nicht unterscheiden, der Kern kennt keins). Ein versehentlich gehaltenes „wennnnn“ wird nicht korrigiert; auf einem
  Touch-Feld ist das selten. Wörter mit Wiederholung in der Mitte („Schifffahrt“, „Kaffeeernte“) sind nicht betroffen: nur der Wortschluss zählt.
- **P294** Gerätetests (73), in GitHub-Läufen von `device-tests.yml` gesehen, soweit nicht als vermutet oder ungeprüft markiert (Nachweise: `docs/VALIDATION.md` → Etappe 73):
  (1) Die Tasten sind nur dann einzelne Bedienungshilfen-Knoten, wenn beim Aufbau der Tastatur (`MainKeyboardView.setKeyboard`) ein
  Bedienungshilfen-Dienst aktiv ist (`AccessibilityManager.isEnabled`); darum der `ProbeAccessibilityService` der Test-App. Er fordert kein
  „Tippen zum Erkunden“ an: alles, was das Tippen ändert, hängt an `isTouchExplorationEnabled`. (2) Nie `am force-stop` auf die Test-App:
  Android beendet und deaktiviert damit auch ihren Dienst (Läufe 2, 4, 5). Felder leert `am start --activity-clear-task`; eine Option
  `--activity-new-task` gibt es nicht (Lauf 6). (3) `dumpsys accessibility` nennt gebundene Dienste beim App-Namen, aktivierte beim
  Klassennamen (Lauf 3). (4) Zwei Knoten tragen die ID `keyboard_view`: ein Rahmen so hoch wie das Fenster und die echte Ansicht
  (Klasse `android.view.View`, direkt unter `suggestion_strip_view`); `devicetest.py` nimmt die kleinste sichtbare `View`. (5) Die Tastatur
  meldet die Tastenpositionen nicht verlässlich: Lauf 8 ganz oben am Bildschirm, Lauf 9 um 128 Pixel (Höhe der Statusleiste) zu hoch,
  Läufe 10 und 12 richtig. Ursache (der Unit-Test `AT/KeyNodePositionTest.kt` zeigt sie, rot ohne die Zeile): `KeyboardAccessibilityNodeProvider.updateParentLocation` las die Lage der Ansicht nur
  beim Aufbau des Wurzelknotens, also auch bevor das Fenster an seinem Platz ist; behoben, indem `createAccessibilityNodeInfo` sie für jede Taste liest
  (Etappe 73; HeliBoard-Code, hätte auch TalkBack betroffen). Bestätigung im Emulator: Szenario `key_positions_reported`, Ergebnis ausstehend.
  Untereinander stimmen die Positionen; `Screen.keys()`
  legt den Tastenblock weiter mit der unteren linken Ecke auf die der echten Ansicht. (6) In Passwortfeldern heißen alle Zeichentasten „Aufzählungspunkt“, solange
  `settings put secure speak_password 1` nicht gesetzt ist (`AccessibilityUtils.shouldObscureInput`). Eine Trenntaste heißt bei
  anstehender Autokorrektur „Mit Leertaste wird "x" in "y" geändert.“ (`getAutoCorrectionDescription`); `plain_key_name` löst das auf.
  Die Eingabetaste heißt nach ihrer Aktion („Eingabetaste“, „Weiter“, „Fertig“), die Umschalttaste im Großzustand „Umschalttaste-Taste“.
  (7) Mit `-Pandroid.injected.build.abi=<ABI>` (`tools/dev.py build --abi …`) baut Gradle `libjni_latinime.so` nur für diese ABI: für den
  Emulator `--abi x86_64` (an der Dateiliste der APK nachgesehen; nur `libandroidx.graphics.path.so` liegt für alle vier ABIs bei).
  (8) Ob `uiautomator dump --windows` die Tasten bei laufendem Dienst zeigen würde, ist nicht geprüft; der Dienst liest die Fenster selbst.

- **P295** Prompt-Ablage, Editor (74a): Der Editor der Seite bleibt eine Karte auf der Seite, kein
  `AlertDialog` (P78: ein Textfeld im Dialog wird in Robolectric nie „idle“). Solange er offen ist,
  sind Liste und ihre Knöpfe nicht im Baum (`PromptsScreenTest.withTheEditorOpenNoRowAndNoRowButtonIsInTheTree`).
- **P296** Prompt-Ablage, kaputte Datei (74a): Eine Datei, die sich nicht lesen lässt (kaputt, zu groß,
  nicht verstanden), wird nie überschrieben: `modify` liest zuerst und gibt sonst `IO` zurück, auch
  `add` schreibt dann nichts. Nur `reset` löscht, und nur wenn es die Datei unter dem Lock noch einmal
  liest und sie unbrauchbar ist; eine lesbare Datei (auch „keine Datei“, auch ein lesbares Backup)
  ergibt `READABLE`, löscht nichts und ändert `version` nicht. `AtomicFile.openRead` beendet einen
  unterbrochenen Schreibvorgang selbst (Backup „agent_prompts.json.bak“ ersetzt die Hauptdatei), schon
  ein Lesen kann also den letzten fertigen Stand zurückholen. Die Seite zeigt „Ablage zurücksetzen“
  nur bei `PromptStoreState.UNUSABLE`, nie bei gesperrtem Gerät.
- **P297** Prompt-Ablage, Einfügen (74b): Zwischen Öffnen des Fensters und Tipp kann sich alles ändern.
  `insertPrompt` prüft deshalb beim Tipp selbst, nicht aus dem Zustand beim Öffnen: Fenster sichtbar (ein
  zweiter oder veralteter Tipp auf eine Zeile eines schon geschlossenen Fensters tut nichts, ein
  Doppeltipp fügt einmal ein), Schutz des Felds, Sperre des Geräts und des Nutzers, `mIsLocked`, und
  liest den Text frisch aus der Ablage. Die Tests zur Prüfung von 74b deckten zwei echte Fehler auf
  (ein Tipp nach dem Sperren ließ das Fenster offen; ein zweiter Tipp auf eine verborgene Zeile
  konnte noch einmal einfügen): `aLockAfterOpeningMeansATapInsertsNothing`, `aDoubleTapInsertsOnce`,
  `aFieldChangeClosesTheWindowAndAStaleRowCannotInsert`. Nie den Text aus dem Fenster übernehmen und
  nie ohne `editor.captureAnchor` einfügen.
- **P298** Prompt-Ablage, Symbol (74b): `KeyboardIconsSet.loadIcons` setzt `agentIconIds` vor die Ids des
  Stils; bei gleichem Namen gewinnt der spätere Eintrag. Die Reihenfolge nicht umdrehen (sonst
  überschreibt das Symbol des Forks Stil und eigene Symbole) und das Symbol nicht in eine einzelne
  Stil-Map eintragen (dann fehlt es in den anderen Stilen). Wie das Symbol aussieht und ob es wie die
  anderen Symbole eingefärbt wird, ist nicht auf einem Gerät geprüft.
- **P299** Prompt-Ablage, vertrauliche Texte (74b): „Kopiertes als Prompt speichern“ folgt der Regel
  des Zwischenablage-Verlaufs (`ClipVault.isSecretClip` mit der eigenen Kopierzeit und Markierung des
  Clips), nicht einer eigenen Prüfung: Ein Clip, den der Tresor beim Kopieren als vertraulich sortiert hat
  (etwa in einem Passwortfeld), bleibt auch später in einem normalen Feld abgelehnt. Eine eigene,
  schwächere Regel war ein Befund der Prüfung von 74b. Zusätzlich gilt `ClipGuard.classify(...).keepsOutOfHistory`
  (Geheimnis oder bloßer Einmalcode). Die erste Fassung lehnte jede Kategorie `OTP` ab und damit auch
  Alltags-Prompts wie „Write Python code that prints 10000 random numbers“; nicht wieder verschärfen
  (`aPromptThatMentionsCodeAndANumberIsStoredLikeTheHistoryKeepsIt`, Rot-Beweis mit der alten Regel).
  Bekannte Grenzen: Nach einem Neustart des Tastatur-Prozesses kennt der Tresor das Herkunftsfeld eines Clips
  nicht mehr (die Markierung liegt nur im Speicher, auch im Verlauf); ein harmlos aussehender Text, der vorher
  in einem Passwortfeld kopiert wurde, kann dann als Prompt gespeichert werden; nicht behebbar, das Feld
  ist nicht wiederherstellbar. Auf Android-Versionen ohne Zeitstempel am Clip kann der Abgleich der Kopierzeit
  im Tresor nicht greifen (gleiche Schwäche wie im Verlauf). Tests: `aClipTheVaultSortedAsSecretWhenCopiedIsRefusedLaterInANormalField`,
  `aClipTheSystemMarkedSensitiveIsRefused`, `everyCategoryTheHistoryKeepsOutIsRefusedHere`,
  `aBareOneTimeCodeIsRefused`.
- **P300** Prompt-Ablage, Zurück im Editor (74a): Der `BackHandler` der Editor-Karte ruft `cancel()`, das
  Titel und Text beim Aufruf liest; das Callback überlebt Neuzeichnungen, ein beim Zusammensetzen
  festgehaltener Wert wäre veraltet. Mit angezeigter Liste ist der Handler nicht aktiv, Zurück verlässt
  die Seite (`withTheListShownTheSystemBackLeavesThePage`). `editingId`, Felder und die Rückfrage sind
  `rememberSaveable`, damit Drehen oder ein Neustart der Aktivität den Editor nicht verwirft
  (`theEditorStateSurvivesAnActivityRestore`).
- **P301** Prompt-Ablage, zwei Prüfungen (74b): `PromptsKey.shown` entscheidet beim Aufbau des Layouts
  und kennt „Immer Inkognito“ nicht; `openPrompts` und `insertPrompt` prüfen beim Tipp mit
  `editor.protection()` noch einmal. Keine der beiden Prüfungen streichen. Das Symbol soll auch dort
  fehlen, wo das Fenster nie aufginge (gesperrt, Passwort, Zahlen-Layout, ohne Lernen).
- **P302** Das Wörterbuch unterscheidet Groß- und Kleinschreibung, und ein Test-Double davon muss es auch (Etappe 75):
  Die erste Fassung der Buchstabendreher prüfte „bekannt“ mit einem Double, das die Schreibweise ignorierte; dadurch
  blieb unbemerkt, dass „ort“ im Wörterbuch nur als „Ort“ steht und zu „rot“ geworden wäre (ebenso „rest“ → „erst“,
  „bsi“ → „bis“). `Suggest.applySwappedLetters` fragt deshalb getippt, mit großem Anfang und ganz groß
  (`mainLocalesKnowing`) und prüft die Treffer der Engine in jeder Schreibweise; `SuggestTest` hat dafür
  `mainLocalesKnowingWord` und `mainFrequencyOfWord` je Sprache und den Fall „a word that exists only
  capitalised stays as typed“. Ein neues Double eines Wörterbuchs muss dieselbe Schreibweise-Regel haben. Rot-Beweis:
  die Prüfung „in jeder Schreibweise bekannt“ entfernt.
- **P303** Die Buchstabendreher stehen hinter der eigenen Korrektur der Engine und urteilen nur in der Tastatursprache
  (Etappe 75): Hat die Engine eine eigene Korrektur (`engineCorrection`, nur wenn `guardedAutoCorrection` gilt), gilt
  sie, außer es ist dasselbe Wort; die erste Fassung überstimmte sie. Kandidaten zählen nur im Hauptwörterbuch der
  Tastatursprache (`mainFrequency(wort, sprache)`): Mit allen Sprachen wäre bei deutscher Tastatur „titel“ zu
  „title“ und „foto“ zu „foot“ geworden. Ein Wort, das ein aktives Wörterbuch irgendeiner Sprache kennt, bleibt, wie
  getippt. Eine Änderung hier braucht einen Prüfer und die echten Wörterbücher im Emulator (Szenarien
  `swapped_letters_keep_nouns`, `swapped_letters_keep_caps`; Ergebnis ausstehend).
- **P304** Die Schwellen der Buchstabendreher (Etappe 75) sind an den echten Wörterbüchern gemessen:
  `SwappedLetters.MIN_FREQUENCY_DE` 105 (Deutsch: „dir“ 108, „mir“ 125, „dich“ 112, „ich“ 139; „ebd“ 101 fällt heraus),
  `MIN_FREQUENCY_EN` 150 („the“ 222, „and“ 212; „hub“ 119, „sub“ 131 fallen heraus) und `MIN_FREQUENCY_OTHER` 130
  (nicht geprüft, vorsichtig). „dir“ liegt nur 3 über der deutschen Schwelle: Ein neues deutsches Wörterbuch kann
  die Häufigkeit senken und „idr“ → „dir“ ohne Fehlermeldung wieder verschwinden lassen. Die Tabellen in `SuggestTest`
  (`deFrequencies`, `enFrequencies`) sind gelesene Werte, kein Beleg für ein neues Wörterbuch; das Gerätetest-Szenario
  `autocorrect_typo` würde es zeigen (Ergebnis ausstehend).
- **P305** Ein leerer Eintrag der Prompts in einer Sicherung darf vorhandene Prompts nie löschen (Etappe 76):
  `exportBytes` schreibt keinen Eintrag für eine leere Liste, und `restorePromptsFromBackup` behandelt einen leeren
  Eintrag wie keinen (`NO_ENTRY`); sonst würde das Wiederherstellen einer Sicherung später angelegte Prompts
  wegwischen (Befund des Prüfers, `BackupPromptsTest`, Rot-Beweis „leere Liste ausgeführt“). Ein fehlender, leerer,
  beschädigter oder zu großer Eintrag, ein gesperrtes Gerät und eine unlesbare Prompt-Datei lassen die Prompts
  unverändert.
- **P306** Die Prompts werden beim Wiederherstellen zuletzt geschrieben und ein Fehlschlag wird gesagt (Etappe 76): Die
  Datei liegt in `noBackupFilesDir`, außerhalb der beiden Ordner, die `restoreWithRollback` zurückrollt; deshalb steht
  `prompts.apply(...)` ganz am Ende des Blocks, nach `Database.copyFromDb`, damit nach ihr nichts mehr scheitern
  kann, was die Wiederherstellung zurückrollen müsste. Eine Ausnahme dort zählt als `UNREADABLE`, statt die fertige
  Wiederherstellung zurückzurollen. Ein Fehlschlag erscheint als zweite Meldung, nie still (Befund des Prüfers).
  Neue Einträge in der Sicherung gehören nach demselben Muster behandelt (genau verglichener Name, Prüfung vor dem
  Schreiben, zuletzt anwenden).
- **P307** `ClipboardHistoryManager.dontShowCurrentSuggestion` wird außerhalb der Verlaufsprüfung zurückgesetzt
  (Etappe 73): In `onPrimaryClipChanged` stand das Zurücksetzen bei HeliBoard im `if` der Verlaufs-Einstellung; ohne
  Verlauf (Standard) war die erste getippte oder geschlossene Pille bis zum Neustart der Tastatur die letzte
  (Befund der Gerätetests, Läufe 20–22). Es steht jetzt nach dem `if`. Der Clip-Listener ist immer angemeldet, keine
  Datenschutzregel hängt an dem Flag (Prüfer). Test
  `SmartPasteChipsTest.aNewClipIsOfferedAgainAfterAPillWasTappedAlsoWithoutTheHistory` (rot mit der alten Bedingung).
  Bestätigung im Emulator ausstehend.
- **P308** `ClipboardManagerCompat.getClipSensitivity` liefert ab API 24 nie `null`, sondern `false` für jeden
  unmarkierten Clip. Wer das Ergebnis mit `?.let { return it }` auswertet, fragt das Passwortfeld nie ab
  (`ClipboardHistoryManager.isClipSensitive`, Gerätetest-Lauf 26: Klartext in der Pille im Passwortfeld). Immer
  `== true` vergleichen. Test: `SmartPasteChipsTest.aPasswordFieldShowsAnUnmarkedClipAsStarsInThePill`.
- **P309** `SettingsValues.mIsLocked` wird nur beim Feldstart gesetzt. Jede Sperre (`isBlocked`) muss zusätzlich
  `LockState.isLocked(context)` fragen, sonst gilt sie nicht, wenn das Gerät bei offenem Feld gesperrt wird.
  `KeyboardCommands.isBlocked` merkt sich dafür den Kontext in `loadNow`. Test: `LiveLockGateTest`.
- **P310** Im Agent-Paket nie ein Throwable an `Log` übergeben (Meldung und Stacktrace können Pfade oder URIs
  enthalten), nur `e.javaClass.simpleName`. Kein Test erzwingt das.
- **P311** Ein Test-`AutofillService` braucht `android:supportsInlineSuggestions="true"` in seiner
  `android.autofill`-Metadatei, sonst fragt das System die Tastatur nie (`dumpsys autofill`:
  „Inline Suggestions Enabled: false“, Gerätetest-Lauf 38).
- **P312** Prompt-Ablage, Format mit Ordnern (77): Der Speicher schreibt Format 1 (ohne `folders`, wie bis Etappe 76), solange es keinen
  Ordner gibt, sonst Format 2; so bleibt eine Datei ohne Ordner byte-gleich lesbar für ältere App-Versionen und `exportBytes` für
  Nutzer ohne Ordner unverändert. Der Leser ist streng: Ein Prompt, der auf einen Ordner zeigt, den die Datei nicht hat, eine höhere
  `storageVersion`, falsche Schlüssel je Format, doppelte Ids, ein Name außerhalb der Regeln
  oder mehr als 50 Ordner machen die ganze Datei unbrauchbar. Zwei Namen, die sich nur in der Schreibweise unterscheiden, lässt der Leser dagegen zu (die
  Tabellen für Groß-/Kleinbuchstaben unterscheiden sich zwischen Android-Versionen, sonst wäre ein ganzer Speicher unlesbar; Test
  `twoFolderNamesThatDifferOnlyInCaseAreReadAndStayWritable`); `encode` prüft nur die Ordner der Prompts, nie die Namen (`UNUSABLE`, Schreibvorgänge geben `IO`, die Datei bleibt, wie in
  P296). Bewusst nicht „den Prompt dann ohne Ordner laden“: Der nächste Schreibvorgang würde die Zuordnung still ändern. Folge: Die
  Datei einer neueren App-Version bietet in dieser Version „Ablage zurücksetzen“ an (mit Rückfrage, wie jede unbrauchbare Datei).
  Tests: `aFileWithFoldersThatTheReaderDoesNotAcceptIsUnusableAndNeverChanged` (17 Dateien), `anOldFileWithoutFoldersLoadsUnchanged…`, `aStoreNearTheFileLimitThatGainsItsFirstFolderIsRefusedAsFileFull…` (Format 2 ist je Prompt 16 Byte länger; `FILE_FULL`, Datei unverändert).
- **P313** Prompt-Ablage, Reihenfolge mit Ordnern (77): Es gibt genau eine globale Liste der Prompts; die Ansicht eines Ordners ist ihre
  gefilterte Sicht. `reorder(ids)` verlangt weiter alle Ids (nie mit der gefilterten Liste aufrufen: `INVALID_ORDER`); in einer
  Ordneransicht gilt `reorderInFolder`, das nur die Plätze der Prompts dieses Ordners untereinander tauscht. Ein Prompt, der den Ordner
  wechselt, behält seinen globalen Platz (er steht im neuen Ordner also dort, wo er relativ zu dessen Prompts liegt); gelöschte Ordner
  lassen ihre Prompts an ihren Plätzen. Neue Prompts kommen vorn in die globale Liste, also auch vorn in ihrem Ordner.
- **P314** Prompt-Ablage, Fenster mit Ordnern (77): Der gewählte Chip lebt in `PromptsView.rememberedFilter` (statisch, nur im Prozess, nie
  gespeichert, auch nicht in den Einstellungen). `PromptLibrary.effective` fällt auf „Alle“ zurück, wenn der Ordner fehlt oder „Ohne Ordner“
  nicht angeboten wird. Ein Chip-Tipp liest den Speicher neu und prüft Schutz und Sperre wie `insertPrompt` (`showPromptFilter`).
  „Kopiertes als Prompt speichern“ legt in den gezeigten Ordner ab; ist der gezeigte Ordner inzwischen gelöscht, wird nichts gespeichert
  („Diesen Ordner gibt es nicht mehr.“), nie stillschweigend woanders. „Schon gespeichert“ vergleicht mit dem obersten Prompt der gezeigten
  Liste. Tests, die das Fenster benutzen, setzen den statischen Filter zurück (`forgetTheRememberedChip`).
- **P315** Prompt-Ablage, Seite mit Ordnern (77): Der Ordner-Editor ist wie der Prompt-Editor eine Karte auf der Seite (P78, kein Textfeld in
  einem Dialog); der Löschdialog hat kein Textfeld und nennt die Zahl der Prompts aus dem aktuellen Stand. Der gewählte Filter und der
  Ordner-Editor sind `rememberSaveable`. Im Prompt-Editor zählt ein inzwischen gelöschter Ordner als „Ohne Ordner“ (so zeigt es der
  Wähler); trifft das Speichern trotzdem auf `FOLDER_NOT_FOUND`, steht die Meldung da, die Seite lädt neu und nichts wird gespeichert.
- **P316** Prompt-Ablage, Sicherung mit Ordnern (77): `PromptStore.decodeBackup` liefert eine `PromptLibrary`; für die Sicherung zählen nur Prompts
  (`PromptLibrary.hasPrompts`, Regel von 76 gilt weiter): `exportBytes` ist null ohne Prompts, Ordner allein werden also nicht gesichert
  (bekannte Grenze), und ein Eintrag, der zu null Prompts dekodiert, ist beim Wiederherstellen `NO_ENTRY`, auch mit Ordnern; sonst könnte ein
  handgemachter oder alter Eintrag nur mit Ordnern einen Speicher voller Prompts durch eine leere Ablage ersetzen. Wiederherstellen
  ersetzt bei einem Eintrag mit Prompts auch die Ordner (der Stand der Sicherung); eine Sicherung im Format 1 stellt Prompts ohne Ordner
  her, so wie vor 77. Tests: `anEntryWithFoldersButNoPromptsNeverReplacesAStoreWithPromptsAndFolders`, `aStoreWithOnlyFoldersHasNoBackupEntry`.
- **P317** Prompt-Ablage, Ordner-Editor und Seite (77, Nachprüfung): Der Ordner-Editor fragt bei geändertem Namen vor dem Verwerfen (Zurück und
  „Abbrechen“, wie P300), sein Feld ist ein `TextFieldValue` mit der Schreibmarke am Ende. Der Löschdialog liest die Zahl der Prompts beim Öffnen
  frisch aus dem Speicher; die Seite lädt bei `ON_RESUME` neu (das Fenster der Tastatur kann den Speicher inzwischen geändert haben). Der Klick eines
  Chips tut nichts, solange das Fenster nicht sichtbar ist (`this@PromptsView.visibility`, nicht die Sichtbarkeit des Chips): ein veralteter Chip darf
  `rememberedFilter` nicht ändern.
- **P321** Begleit-App, Modellkonfiguration (80): Für `runtime_id` `qairt` lehnt das SDK `nCtx != 0` und `nGpuLayers != 0`
  mit `PARAM_NOT_SUPPORTED` ab, aber die Vorgaben von `ModelConfig` in 0.10.0 sind `nCtx = 2048` und `nGpuLayers = -1` (javap des
  Konstruktors; die Doku-Beispiele mit `ModelConfig()` stimmen nicht). `GeniexAdapter.load` setzt beide ausdrücklich auf 0. Die
  Rechenart steht nicht in `ModelConfig`: `LlmCreateInput(runtime_id = "qairt", compute_unit = "npu")`.
- **P322** Begleit-App, Aufruf der Erzeugung (80): In 0.10.0 ist `generateStreamFlow(prompt, inputIds, embeddings, Int, config)`;
  das Beispiel der Doku mit `GenerationConfig` als zweitem Argument kompiliert nicht, also benannt `prompt =` und `config =`.
  Immer den Text aus `applyChatTemplate(...).formattedText` übergeben, nie den Rohtext (sonst „degenerate output“). Das Denken wird
  mit dem dritten Parameter `enableThinking = false` von `applyChatTemplate` abgeschaltet, nicht in `ModelConfig`. `maxTokens` hat
  in `GenerationConfig` die Vorgabe 32 und wird darum immer gesetzt (96 und 192). `PullEvent` und `LlmStreamResult` sind
  versiegelt; `Completed` beendet den Strom, `GeniexAdapter` bricht ihn dort per `transformWhile` ab.
- **P323** Begleit-App, Chip-Kennung (80, korrigiert in 80b nach dem ersten Lauf auf dem Xiaomi 15): Der Pull mit Chipset `SM8750` scheitert
  immer mit `-100015` (`COMMON_CHIPSET_UNAVAILABLE`). Grund (gelesen in den Listen „platform“ und „release-assets“ von Qualcomms S3 und im Quelltext der
  Paketauswahl des SDK): `SM8750` ist bei Qualcomm **kein** Alias eines Chips (bekannt ist nur `sm8750-ac` für
  `qualcomm-snapdragon-8-elite-for-galaxy`), und die Pakete von Qwen3-4B und Qwen3-4B-Instruct-2507 gibt es nur für diesen Galaxy-Chip (und
  Gen 5 for Galaxy, X Elite, QCS); die Anleitung im Netz („chipset SM8750“) stimmt dafür nicht. Darum sind beide Kandidaten
  `PullPlanner.CANDIDATES` mit `SM8750-AC` (seit 80c mit der Ausgabe v0.66.0, P334), zuerst Instruct-2507 (das Modell der Android-Anleitung, ohne Denkblock, in der Fehlerhilfe der
  Anleitung als registrierter Name genannt), dann Qwen3-4B. Nach jedem Fehler zeigt die App den Fehler und bietet das nächste Paket mit
  eigenem Dialog an; geladen wird nur nach Tipp. Ob die Galaxy-Pakete auf dem Xiaomi laufen, ist weiter offen.
- **P324** Begleit-App, Paketierung (80): Das SDK sucht `libgeniex_plugin_*.so` in `applicationInfo.nativeLibraryDir`, also müssen
  die Bibliotheken auf der Platte liegen: `packaging.jniLibs.useLegacyPackaging = true` und `android:extractNativeLibs="true"`.
  Das AAR deklariert keine Abhängigkeiten, die App muss `kotlinx-coroutines` selbst einbinden, und es deklariert keine
  Berechtigung, die App muss `INTERNET` selbst deklarieren. Das AAR hat nur `arm64-v8a`: Auf anderen Geräten scheitert schon
  das Laden der Klasse `GenieXSdk` (statischer Block `System.loadLibrary("npu_jni")`, aus dem Bytecode gelesen, nicht ausgeführt) mit `UnsatisfiedLinkError`; `GeniexAdapter.init` fängt jedes `Throwable`
  und zeigt „Auf diesem Gerät nicht unterstützt“.
- **P325** Begleit-App, kein stiller Rückfall (80): `runtime_id` ist fest `qairt`; zusätzlich prüft `SpikeRunner` nach dem Laden die
  Laufzeit, die der Modellspeicher des SDK für das Paket vermerkt hat (`ModelPaths.runtime_id`), und meldet alles außer `qairt` als
  Fehler `NpuFallbackDetected`; die Version des QAIRT-Plugins steht im Bericht. Ob die Rechnung wirklich auf der NPU läuft, sieht die
  App nicht; Hinweis nur über das Tempo (unter 8 Tokens/s: „auffällig langsam“, Qualcomm nennt 16 bis 21). Das ist eine Heuristik.
- **P326** Begleit-App, Absturz im nativen Code (80): Ein Fehler der QNN-Laufzeit (z. B. Bundle 2.51 gegen Laufzeit 2.50) kann den Prozess
  beenden, ohne dass Kotlin etwas fängt. `PrefsStepTracker` schreibt den laufenden Schritt mit `commit()` (synchron) vor jedem
  Schritt; beim nächsten Start steht im Bericht, in welchem Schritt der Lauf endete, und `DeviceInfo.lastExitLines` nennt den Grund laut
  Android (Absturz, Speichermangel, …). Ohne das wäre „die App ist weg“ der ganze Befund.
- **P327** Begleit-App, Speicherzahlen (80): Es sind drei verschiedene Zahlen und der Bericht nennt jede mit ihrer Quelle: nativer Heap
  (`Debug.getNativeHeapAllocatedSize`, ohne eingeblendete Dateien), PSS (`Debug.MemoryInfo`) und freier Speicher des ganzen Systems
  (`ActivityManager.MemoryInfo.availMem`). Speicher, den NPU und Treiber außerhalb des Prozesses belegen, ist in keiner enthalten.
- **P328** Begleit-App, Gradle (80): `-p companion` nimmt `companion/gradle.properties` (nicht die des Hauptprojekts). Der lokale
  Maven-Spiegel der Cloud-Umgebung (Init-Skript im Gradle-Benutzerordner, `docs/HANDOFF.md` → Sackgassen) setzt Projekt-Repositories und nimmt nur ein Projekt namens
  `secondhand-devicetest` aus: darum hat `companion/build.gradle.kts` wie das Hauptprojekt `allprojects { repositories {…} }` (kein
  `FAIL_ON_PROJECT_REPOSITORIES`). `pluginManagement` braucht `gradlePluginPortal()`. Das Plugin `kotlin.plugin.serialization` 2.4.0
  (`apply false`) steht nur da, damit AGP 9 mit dem Kotlin 2.4.0 des Hauptprojekts übersetzt (geprüft mit `buildEnvironment`: 2.4.0).
- **P329** Begleit-App, Weitergabe (80): Die APK enthält Qualcomms Laufzeit (QAIRT/QNN, `llama.cpp`) und ist etwa 97 MB groß. Wegen der
  unklaren Weitergabebedingungen wird sie nie in ein Release, einen Workflow oder das Repository gelegt (`.gitignore` deckt
  `companion/**/build` ab); sie geht nur als Datei an den Owner. Sie ist ein Debug-Build (debuggable) und für diesen Zweck so gewollt.

- **P318** Autofill-Chips, Breite (79): Die Breite wird beim Erzeugen der Anfrage gelesen (`mSuggestionStripView.getWidth()`, 0 solange die Leiste nicht
  gelayoutet ist; dann gilt die Anzeigebreite, ohne beides 280 dp). Davon gehen 66 dp ab (linke Werkzeugtaste 36 dp und Schließen-Knopf 30 dp,
  `SuggestionStripView.setExternalSuggestionView`); angeheftete Tasten des flachen Stils zählen nicht mit, die Zahl ist eine Obergrenze, kein
  Layout, denn die Chips liegen in einer `HorizontalScrollView`. Die Anfrage entsteht einmal je Eingabe: Dreht sich das Gerät danach, ohne dass das System neu fragt,
  bleibt die alte Breite (nicht geprüft, ob es neu fragt). Weiter drei gleiche Spezifikationen (manche Passwortmanager verlangen drei);
  die Höhe ist weiter `config_suggestions_strip_height`.
- **P319** Autofill-Chips, Reihenfolge und veraltete Antworten (79): Die Chips werden einzeln aufgeblasen; `InlineAutofillSlots.onResult(index, hasView)` gibt die
  Einfügestelle (Zahl der schon eingesetzten Chips mit kleinerem Index) oder -1 (Fehlschlag `view == null`, unbekannter oder doppelter Index). Bewusst
  kein Warten auf alle und kein Zeitlimit: Ein Chip, der nie zurückgerufen wird, hält die anderen nicht auf; kommt er spät, rutscht er an seine Stelle
  (die Chips dahinter verschieben sich dann). `InlineAutofillGeneration` macht Tokens nur ungültig bei einer neuen nicht leeren Antwort, beim Ende der Eingabe (`close`) und beim
  Schließen durch den Nutzer (`invalidate`); `open()` öffnet nur eine geschlossene Generation und ändert sonst nichts, denn ein Neustart
  derselben Eingabe oder eine zweite Anfrage für dasselbe Feld darf die Chips der laufenden Antwort nicht verwerfen (Gerätelauf: zwei von drei Chips,
  danach korrigiert; die erste Fassung bumpte in `open()`). Folge: Ein Rückruf eines Fensters, das schon durch Wörter ersetzt wurde (`setNeutralSuggestionStrip`), fügt weiter in
  einen losgelösten Container ein (harmlos, ohne Wirkung). `onFinishInputInternal` schließt, `onStartInputInternal` und die Anfrage öffnen nur wieder; die
  verzögerten Rückrufe nach dem Drehen (`executePendingImsCallback`) führen beide aus, am Ende ist wieder geöffnet. Fällt `open()` aus, würde
  Autofill still nichts mehr zeigen; darum öffnet auch die Anfrage. Eine Antwort ohne aktive Eingabe liefert `false`.
- **P320** Autofill-Chips, Freigabe und Datenschutz (79): `InlineContentView` (Android 11+) hat in der öffentlichen Schnittstelle keine Freigabe-Methode (`javap` auf
  `android.jar` von API 37: nur `getSurfaceControl`, `setSurfaceControlCallback`, `setClipBounds`, `setZOrderedOnTop`, `onLayout`; `InlineSuggestion`
  nur `inflate`). Darum gibt es keinen Aufruf zum Freigeben; die Ansichten werden mit der Leiste losgelöst (`clear()` entfernt sie) und die Referenz in
  `LatinIME` wird verworfen. Dass die Oberfläche dann vom System freigegeben wird, ist Plattformverhalten (nach dem Wissen über AOSP, im SDK
  liegen nur Stubs; auf einem Gerät nicht geprüft). Verhalten, gewollt und hier festgehalten: Die Chips erscheinen auch in Passwortfeldern und im Inkognito-Modus
  (nur `mSuggestionStripHiddenPerUserSettings` schaltet sie ab), denn ein Passwortmanager braucht genau das; Text und Werte der Vorschläge liest, speichert
  und loggt die Tastatur nicht (nur „onInlineSuggestionsResponse called“).
- **P440** Autofill, Kasten erst beim ersten Chip (79b): Der Container mit dem Schließen-Kreuz steht erst, wenn `InlineAutofillResponse.onResult` den ersten Chip der aktuellen Generation eingesetzt hat (Aktion „erster Chip“ läuft genau einmal, nach dem Einsetzen). Vorher bleibt die Leiste, wie sie ist, und `isShowingInlineAutofill` ist falsch
  (die Zwischenablage-Pille darf also kommen und wird dann vom Kasten ersetzt, wie Wörter es auch werden). Kommt der Chip während des Tippens, ersetzt der Kasten die Leiste trotzdem (Rangfolge wie vor 79b). Ein Chip nach Eingabeende, neuerer Antwort oder Schließen kommt nie an (`isCurrent`). Die Rückrufe laufen auf dem Haupt-Executor, der Container existiert also, wenn die Aktion läuft;
  `LatinIME` fängt trotzdem ein fehlendes Bild ab. Wer die Reihenfolge von Container und `inflate` ändert, prüft, dass die Aktion nie vor dem Container läuft. Gerätebefund: Auf einem Emulator kam für die erste Antwort kein einziger `inflate`-Rückruf (`pending=3`, `firstCallbackMs=-1`); beim zweiten Fokussieren kamen alle drei Chips. Warum die Plattform schwieg, ist nicht geklärt (Plattform, das Protokoll war leer).
- **P441** Autofill, Zähler (79b): `InlineAutofillCounters` zählt nur Zahlen; kein Titel, kein Text, kein Datensatz erscheint je in `dump()`, auf der Diagnose-Seite oder im Gerätetest. `null` (Plattform lieferte keine Ansicht), veraltet verworfen und „noch nicht beantwortet“ sind getrennt; die Zeit bis zum ersten Rückruf zählt jeden Rückruf, auch einen veralteten.
  Doppelte Rückrufe desselben Index zählen bei `null` doppelt (die Plattform ruft je Vorschlag einmal). Die Felder sind `volatile`, weil `dump()` auf einem Binder-Thread liest. Der Dump von `LatinIME` ist über `dumpsys input_method` und `dumpsys activity service` erreichbar gedacht; auf einem Gerät nicht geprüft.
- **P442** Autofill, Diagnose im Gerätetest (79b): Die INFO-Zeilen von `autofill_inline` entstehen nur nach einem Fehlschlag außerhalb des Kontrolllaufs und dürfen nie ein Ergebnis ändern; `cmd autofill set log_level verbose` gilt nur in diesem Schritt und wird im `finally` auf `off` gesetzt. Das Protokoll kann (nur) die erfundenen Testdaten enthalten.
- **P443** Diagnose-Seite (79b): Die Karte „Autofill-Vorschläge“ (`Diagnose.Part.AUTOFILL`) steht zwischen „Tippen“ und „App und Telefon“ und liest die Zähler beim Öffnen der Seite (nicht live). Settings und Tastatur teilen den Prozess, sonst wäre die Karte leer.
- **P444** Leiste und Zahlenlayouts (79b): Die Abdunklung der Ablage (`BarTools.NOT_IN_NUMBER_LAYOUTS`) wurde nur in `applyBarState` neu berechnet; `KeyboardSwitcher.setKeyboard` ruft jetzt `SuggestionStripView.onKeyboardLayoutChanged` (nur bei geändertem Zahlenzustand). `AgentEditorController.openPrompts` prüft das Zahlenlayout selbst (still, kein Hinweis), weil der Weg über die Leiste, den Code `AGENT_PROMPTS` und das Popup verschiedene Quellen hat.
  `SuggestionStripView.onFieldStarted` setzt `barState` zurück (sonst überlebt `stickyTools` ein neues Feld derselben App).
- **P445** Test-Feldwechsel (79b): `InputLogicTest.setText` startet ein Feld nur über `latinIME.mHandler`; `LatinIME.onStartInput` und damit `AgentEditorController.startEditor` (verwirft gemerkte Wahlen) laufen dabei nicht. Wer in diesem Prüfstand ein „anderes Feld“ prüft, ruft `controller.startEditor` selbst auf, sonst hängt das Ergebnis an einer asynchronen Aufgabe (Fortsetzung auf dem IO-Thread unter Legacy-Looper).
- **P450** Trackpad-Zähler (87b): `CursorTrackCounters` hält nur Zahlen, Enum-Namen und zwei Feld-Flags (Textklasse, Mehrzeilig); nie Text, nie den Paketnamen der App (`mTargetApplicationPackageName`), nie eine Position. Wer ein Feld hinzufügt, prüft den Test `cursor87bTheCardHoldsNoTextOfTheFieldAndNoPackageName`. Die Zähler sind pro Prozess und beginnen bei null; die Karte liest beim Öffnen.
- **P451** Trackpad, Zählstellen (87b): `deleteClaimed` setzt nur das Ende des beanspruchenden Fingers (Modus ACTIVE), ein Feldwechsel (`onCursorTrackEnd(-1)`) und eine neue Berührung desselben Fingers zurück; ein abgewiesener Finger lässt es stehen. Eine Entscheidung wird genau einmal gezählt: Aus-Gründe (Schalter, Eingabe gesperrt, keine Cursor-Richtung) und „abgewiesen, anderer Finger führt den Cursor“ in `decideTrackMode`; „abgewiesen“ auch beim Anspruch (zweiter Finger, dann auch „anderer Finger führt eine Aktion aus“); „Aktion statt Trackpad“ im LEGACY-Zweig vor dem Rückgabewert `TRACK_OFF` (eine Entscheidung, kein Fehler: der Text sagt nie „nicht gestartet“); „nie übernommen“ in `countNeverClaimed` (Ende oder Ersatz einer PENDING-Berührung, auch beim Feldwechsel `onCursorTrackEnd(-1)`; ein Zustand im Modus OFF zählt nicht). Ein neuer Weg, der einen Zustand verwirft, muss `countNeverClaimed` aufrufen. Das Löschen beim Loslassen zählt nur, wenn der Wisch vom Trackpad übernommen war (`deleteClaimed`); `onUpWithDeletePointerActive` liest die Markierung vor `deleteAtLift`. Das Zusammenziehen einer Markierung (87c) zählt `onCollapsed` (nur ein Zähler und ein Flag).
- **P452** Trackpad, unterdrückte Pfeile (87b): In einem nicht mehrzeiligen Feld läuft die flache Spur, senkrechter Weg erzeugt keinen Schritt; gezählt wird `Weg / VERTICAL_STEP_DP` (26 dp) je Berührung, also eine Schätzung, kein gesendeter Pfeil. Nur auf der Leertaste.
- **P453** Trackpad, eine Regel (87b auf 87c): `CursorTrackpad.spaceReason` ist die einzige Stelle der Bindungsregel; Listener (`decideTrackMode`), `CursorTrackVerdict` (Statuszeile, Karte) rufen sie, `appliesToSpace` ist nur ihr `qualifies`. `CursorTrackText.startDecides` ordnet die Richtungen mit `CursorTrackpad.isFree` (dieselbe Einordnung wie `decideStart`: „Cursor bewegen“ und „nichts“ sind frei); wer `decideStart` ändert, ändert den Satz mit. Die Statuszeile kennt Panel, zweiten Finger und Feldart nicht (nur beim Tippen bekannt): „aktiv“ dort heißt „die Einstellung passt“, nicht „hat gerade gewirkt“. Unter dem Schalter steht der Erklärtext von 87c (`agent_gestures_trackpad_summary*`, allgemein) und darunter die Zeilen von 87b (der aktuelle Stand); beide sagen dasselbe in verschiedener Tiefe und dürfen sich nicht widersprechen.
- **P454** Diagnose-Zeilen (87b): Die Beschriftung einer Zeile der Diagnose darf kein „: “ enthalten; `DiagnoseScreenTest.copyPutsExactlyTheShownValuesOnTheClipboard` teilt die kopierten Zeilen am ersten „: “ und sucht den Rest als Wert auf der Seite.
- **P455** Geschützte Felder (87b): Eine Regel an einer Stelle: In einem geschützten Feld (`isProtectedField`: Passwortfeld, `mIncognitoModeEnabled` einschließlich Feld ohne Lernen, gesperrtes Gerät nach `LockState.isLocked`, der Live-Abfrage; nie der zwischengespeicherte Settings-Wert, vgl. Etappe 82) zählt die Diagnose nur die Entscheidung und dass ein Wisch stattfand. Jeder Wert, der etwas über die Bewegung im Text verrät (Schritte, Pfeile, unterdrückte Pfeile, Rasten, Markierungslänge, Zusammenziehen, Zahl der gelöschten Wische), liegt in `CursorTrackCounters.Movement` und wird nur über `move` geschrieben; `move` zeichnet bei `protectedSwipe` (beim Anspruch gesetzt) nichts auf. Ein neuer Bewegungszähler gehört in `Movement`; `CursorTrackDiagTest.aProtectedSwipeRecordsNoMovementValueAtAll` prüft jedes Feld von `Movement` und lässt eine neue Ganzzahl in `PerKey` außerhalb der Entscheidungszähler scheitern. Der Listener liest die Markierungslänge im geschützten Fall nicht.
- **P456** Trackpad-Texte (87b): Die Zeile unter dem Schalter sagt für ↑/↓ mit „nichts“ oder „Cursor bewegen“ „nur in mehrzeiligen Feldern“ (`CursorTrackText.startDecides`, passend zu `decideStart`); bei ausgeschaltetem Schalter ist es eine Aussage für beide Tasten (`agent_trackdiag_off_both`, auf der Seite einmal). „Unterdrückte Pfeile“ ist eine Schätzung („etwa“).

- **P330** Begleit-App, Fehlercodes (80b): Der Kotlin-Wrapper des SDK macht aus jedem Pull-Fehler nur `geniex_model_pull failed (rc=<Code>)`; die
  Klartextmeldung des Rust-Teils (`geniex_model_last_error_message`, z. B. „chipset … not available for this model; supported: …“) ist über
  die Java-Klassen nicht erreichbar (thread-lokal, `ModelManager.lastErrorMessage` nur auf dem Pull-Thread). Die App entscheidet darum nach
  dem Code (`SdkCodes`, Tabelle aus `sdk/model-manager/src/ffi/types.rs` im GenieX-Repository, Stand main 2026-11-19): `-100015` und `-100010`
  geben das Paket auf, `-100005/-100011/-100012/-100017` erlauben Wiederholen, `-100203` rät zum Löschen, `-100006` ist der Abbruch des Nutzers.
  Der Text ist nur der Rückfall für unbekannte Codes. Die Codes stehen im Bericht mit Namen; Codes außerhalb der Tabelle bleiben Zahlen.
- **P331** Begleit-App, angefangene Downloads (80b): Das SDK legt je Modell-ID einen Ordner an und hält `.inflight/`, bis alle Dateien da sind
  (`pull.rs`); Abbruch und Netzfehler lassen die fertigen Teile liegen, der nächste Pull derselben ID setzt fort. Ein Pull, der schon beim
  Planen scheitert (z. B. `-100015`), lässt nur einen leeren `.inflight/`-Ordner zurück, der das Modell nicht als vorhanden zeigt. „Modell /
  angefangenen Download löschen“ ruft `ModelManagerWrapper.remove` für beide Modell-IDs auf (`-100004` „nicht im Speicher“ gilt als in
  Ordnung); ob `remove` einen reinen `.inflight/`-Ordner entfernt, ist nicht belegt.
- **P332** Begleit-App, Netzart und freier Speicher (80b): Der Bericht nennt vor jedem Versuch freien Speicher und Netzart (WLAN, Mobilfunk, …).
  Dafür deklariert die App die normale Berechtigung `ACCESS_NETWORK_STATE` (keine Abfrage beim Nutzer; sie liefert nur die Art der
  Verbindung). Die Signatur der Debug-APK kommt vom Schlüssel `~/.android/debug.keystore` des Containers; ein Update über eine ältere Fassung
  klappt nur mit demselben Schlüssel (SHA-256 des Zertifikats `ba38b227…c1ad`, geprüft für 0.1 und 0.2 mit `apksigner verify --print-certs`).
- **P333** Begleit-App, Ladefehler ohne Detail (80c, nach dem zweiten Lauf auf dem Xiaomi 15): `LlmWrapper.builder().build()` liefert für jedes Problem
  der Laufzeit nur `RuntimeException: Llm create failed: Model loading failed`, ohne Code. Die Ursache steht nur im Protokoll der nativen Teile
  (`libgeniex_core.so` schreibt Zeilen wie `[ERROR] "Device Creation failure"`, „Could not create context from binary“, „Failed to map context Binary“,
  „Unrecognized system context binary info version“, „HTP arch v… detected“; stdout und stderr werden in logcat umgeleitet). Eine App darf ihre eigenen
  logcat-Zeilen lesen (`logcat -d`, ohne Berechtigung); `AndroidDiagnostics.logSince` schneidet nach einer Markerzeile vor dem Laden, `LogFilter` behält NPU-
  Stichwörter und Warnungen und entfernt Zeilen mit Teilen der festen Prüftexte. Eine Java-Schnittstelle für die Protokollstufe gibt es nicht
  (`JNI_OnLoad` setzt Stufe 4 selbst); die App setzt zusätzlich `GENIEX_LOG=trace` über `android.system.Os.setenv` vor dem Start des SDK (Wirkung nicht belegt).
- **P334** Begleit-App, Paketausgabe und Laufzeit (80c, korrigiert in 80d): Die Laufzeit im AAR 0.10.0 ist QAIRT 2.50.40 (Zeichenkette `v2.50.40.260831140417` in `libQnnHtp.so`, die App
  liest sie aus der Datei). Das neueste Paket (AI-Hub-Ausgabe v0.67.0) ist mit QAIRT 2.51.0 gebaut (Eintrag tool_versions.qairt in der Metadaten-Datei des Pakets), v0.65.0 und
  v0.66.0 mit 2.50.0, v0.63.0 und v0.64.0 mit 2.45.0 (Paketlisten je Ausgabe auf Qualcomms S3). Das Plugin prüft nur die QNN-C-Schnittstelle der
  Laufzeit (Untergrenze 2.27), nicht die Version des Pakets. **Die Festlegung der Ausgabe über die Umgebungsvariable `GENIEX_AIHUBVERSION` hat auf dem Telefon nicht gewirkt** (beide
  Pulls mit „v0.66.0“ lieferten die Ausgabe v0.67.0; ob die Variable anders heißt, einmal gelesen wird oder `Os.setenv` den nativen Teil nicht erreicht, ist offen). Seit 80d verlässt sich die App
  nicht mehr darauf: Sie lädt die Zip-Datei der Ausgabe selbst (genaue URL und Bytezahl je Kandidat in `PullPlanner`) und übernimmt sie über `HubSource.LOCALFS`; der Modellname im
  SDK ist dann frei wählbar (`local/…`), also gibt es keine Verwechslung mit den älteren Paketen (`ai-hub-models/…`, im Ordner `qualcomm/…`), die die Versionen 0.2 und 0.3 angelegt haben.
- **P335** Begleit-App, Diagnose ohne Modell (80c): Das SDK hat in Java keinen Aufruf, der den KI-Chip ohne Modell anspricht. Als Ersatz lädt die App die Bibliotheken
  des KI-Chip-Teils einzeln (`System.loadLibrary("cdsprpc")`, `System.load` für `libQnnHtp.so`, `libQnnSystem.so` und weitere) und meldet die Systemfehlermeldung; das
  prüft, ob der Lader der App die Herstellerbibliotheken sieht (H2), nicht ob der Chip antwortet. `libcdsprpc.so` ist für Apps nur über `uses-native-library` und die
  `public.libraries`-Listen des Herstellers sichtbar; beide Listen stehen im Bericht. Alle Diagnosezeilen sind Fakten des Telefons, kein Nutzertext.
- **P336** Begleit-App, Ladefehler durch das Metadaten-Format (80d, dritter Lauf auf dem Xiaomi 15): Die wirkliche Ursache von „Model loading failed“ war im Protokoll der Laufzeit:
  `dispatch: cannot read metadata.json: llm_spec_loader: metadata.json missing 'model_files' object`, danach `Failed to create QAIRT LLM pipeline` und Fehlercode `-100201`. Ab AI-Hub-Ausgabe v0.67.0
  ist `model_files` in der Metadaten-Datei eine Liste (`filename`, Tensoren als Liste mit `name`, dtype `DTYPE_UINT16`, `io_type`, runtime `RUNTIME_GENIEX_QAIRT`), bis v0.66.0 ein Objekt je Dateiname. Das Plugin
  im AAR 0.10.0 enthält nur die Meldung „missing 'model_files' object“ und liest nur Objekte (Zeichenkette in `libgeniex_plugin_qairt.so`); der Quelltext auf `main` (Datei llm_spec_loader.cpp im Plugin-Repository
  qualcomm/geniex-qairt-plugin, Ordner core/src/llm) akzeptiert „object or array“. Das Paketformat ist also neuer als das Android-SDK 0.10.0, und der Chip wurde nie angesprochen. Verglichen
  an den echten Dateien beider Ausgaben: Nach der Umwandlung sind `model_files` (alle Graphdateien, Tensornamen, Formen, Typen, Quantisierung) und `supplementary_files` **gleich** wie in v0.66.0
  (`RealMetadataCompatTest`); die `.bin`-Dateien sind andere (neu gebaut, andere Größen). `MetadataCompat` macht daraus die Option „Kompatibilitäts-Metadaten“: schreibt die Datei im alten Format, sichert das Original
  außerhalb des Modellordners (`files/companion-backup/…`). Offen bleibt, ob die mit QAIRT 2.51 gebauten Programme auf der Laufzeit 2.50.40 laufen. Die ältere Ausgabe v0.66.0 hat beides: das Format des SDK und die Version der Laufzeit.
- **P337** Begleit-App, Download und Import (80d): Qualcomm veröffentlicht keine Prüfsumme der Zip-Dateien (der S3-ETag ist nur eine Multipart-Prüfsumme). Die App prüft die genaue Bytezahl (HEAD-Abfrage 2026-11-19), danach das Zip-Verzeichnis
  (metadata.json und mindestens eine .bin-Datei) und die CRC32 jeder Datei; eine beschädigte Zip-Datei wird gelöscht. Fortsetzen: Range-Anfrage ab der Größe der `.part`-Datei, `If-Range` mit dem gemerkten ETag, antwortet der Server mit 200,
  beginnt die Datei von vorn; ein vorzeitiges Ende der Antwort zählt als Netzfehler (bis zu sechs Versuche mit Wartezeit). Der Import (`LOCALFS`, ohne Netz) kopiert die Dateien in den Modellspeicher; danach wird die Zip-Datei gelöscht. Platzbedarf: Zip etwa 2,7 GB plus entpackt etwa 3,2 GB
  plus Reserve, das alte Paket (3,0 GB) bleibt bis zum Löschen.
- **P338** Begleit-App, Löschen (80d, Fehler aus Version 0.3): Das SDK meldete beim Löschen `0` („Gelöscht“), die Dateien blieben aber liegen (das nächste „Vorhandenes Paket“ zeigte sie). `SpikeRunner.deleteModels` ruft darum nach dem SDK
  die Ordner unter `files/geniex/models/<org>/<repo>` selbst ab (der Speicher liegt im privaten Ordner der App), löscht übrig gebliebene selbst, prüft danach erneut (SDK und Ordner) und schreibt „Gelöscht und geprüft“ nur, wenn nichts mehr da ist; sonst „NICHT vollständig gelöscht“ mit dem Rest.
  Zum Zuordnen: Das SDK legt „ai-hub-models/X“ im Ordner „qualcomm/X“ ab (Auflösung in `pull.rs` des GenieX-Repositorys); die Beschriftung, welches Paket da ist, kommt jetzt aus der Metadaten-Datei (QAIRT-Version) und dem Speichernamen, nicht aus der zuletzt angefragten Kennung.
- **P339** Begleit-App, Speichermangel beim Laden des 4B-Pakets (80e, vierter Lauf auf dem Xiaomi 15): Das Paket v0.66.0 (Import und Metadaten waren in Ordnung, das Laden kam über den Punkt, an dem 0.3 scheiterte) ließ das Telefon in
  Speichermangel geraten: Android beendete die App („vom System wegen Speichermangel beendet“, `REASON_LOW_MEMORY`) und den Launcher, der Bildschirm lud neu. Zuvor waren nur etwa 4,2 GB frei (`MemAvailable`; Swap 12,6 GB, 6,4 GB frei) bei 3,16 GB Rechenprogrammen (vier `.bin`-Dateien).
  Veröffentlichte Speicherzahlen gibt es nicht: die Leistungsliste jeder Ausgabe enthält nur Tokens pro Sekunde, Zeit bis zum ersten Token und Einlesegeschwindigkeit; die Karten bei Hugging Face und die AI-Hub-Seite nennen keinen Arbeitsspeicher; die GenieX-Doku
  nennt als einziges Beispiel ein 4,94-GB-Modell, das auf einem Gerät mit 4,7 GB frei vom Kernel beendet wird. Die Doku des Plugins: „Memory footprint: Dominated by KV cache size (num_layers * num_kv_heads * context_length * head_dim * 2 * dtype_size)“.
  Aus den Formen in der Metadaten-Datei gerechnet: KV-Cache des 4B bei Kontext 4096 etwa 0,3 GB (36 Schichten, 8 Köpfe, 128, uint8); der Rest sind die Gewichte (3,16 GB), die der Chip über gemeinsamen Speicher (`shared_buffer`, `weight_sharing_enabled`) bekommt. Vermutung, nicht belegt: Dieser Speicher
  ist für das System nicht verdrängbar und steht in `MemAvailable` als frei, bis er belegt wird.
- **P340** Begleit-App, Regler gegen den Speicherbedarf (80e, Recherche): Die Schlüssel `use-mmap`, `mmap-budget`, `spill-fill-bufsize` und `allow-async-init` in der Genie-Konfigurationsdatei des Pakets gehören zum Genie-SDK von Qualcomm; sie kommen weder in `libgeniex_plugin_qairt.so` noch in `libgeniex_core.so`
  des AAR 0.10.0 vor und das GenieX-Plugin liest aus `engine.backend.QnnHtp` nur `rope-theta` (Quelltext `llm_spec_loader.cpp`, Zeilen 415 bis 418). Eine Änderung dieser Datei würde also nichts bewirken. Was GenieX selbst hat: `ModelConfig::mmap_context_bins = true` und `file_read_budget_mb = 40`
  (Datei types.h im Plugin-Repository, Ordner core/include), schon die Vorgabe; im AAR 0.10.0 stehen `fileReadMemoryBudgetInMb` und `QNN_HTP_CONTEXT_CONFIG_OPTION_FILE_READ_MEMORY_BUDGET`, aber die Kotlin-Klasse `ModelConfig` hat kein solches Feld (nur `nCtx`, `nGpuLayers`, Threads, `power_mode` …).
  Die Kontextlängen (bis 4096) stecken fest in den Programmen. **Es gibt keinen dokumentierten Regler, der den Spitzenbedarf des 4B senkt; die App bietet darum keine „sparsame Einstellung“ an.**
- **P341** Begleit-App, Speicherwächter (80e): Vor jedem Laden prüft `MemoryGuard`: frei (`MemAvailable`) muss mindestens das Doppelte der Rechenprogramme plus 512 MB sein (4B: 6,4 GB, 1,7B: 3,7 GB, 0,6B: 1,9 GB; GB zu 1024 hoch 3 Bytes wie in der App). Der Faktor 2 ist eine **Vermutung**, eingestellt an einer Beobachtung (3,2 GB Programme bei 4,2 GB frei: beendet);
  er soll an den gespeicherten Messungen korrigiert werden. Reicht der Speicher nicht, wird nichts geladen; „Trotzdem laden“ steht hinter einer zweiten Bestätigung. Während des Ladens misst `LoadWatchdog` alle 200 ms MemAvailable, RSS der App und freien Swap und schreibt jede Messung mit `commit()`;
  `onTrimMemory`-Meldungen der Activity werden mit Zeit gespeichert. Fällt `MemAvailable` unter 800 MB (Vermutung) oder meldet das System `TRIM_MEMORY_RUNNING_CRITICAL`, beendet sich die App selbst (`Process.killProcess`), nachdem sie den Grund gespeichert hat: Das Laden im SDK
  (`create()`, nativ) lässt sich nicht abbrechen, aber ein früher Tod des Prozesses gibt den Speicher zurück, bevor Android Launcher und andere Apps beendet. Beim nächsten Start steht im Bericht „Letzte Speichermessung vor dem Ende“ mit freiem Speicher, App-Größe, Zeit und Notbremse.
  Ein Vordergrunddienst wurde erwogen und nicht gebaut: Er senkt den Bedarf nicht, und eine sichtbare Vordergrund-App hat bei Android schon die höchste Priorität; beendet wird sie erst, wenn der Speicher nach dem Beenden aller unwichtigeren Prozesse noch nicht reicht (Android-Grundsatz, nicht auf diesem Telefon gemessen).
- **P342** Begleit-App, HyperOS-Grenze für dma-buf (80f, fünfter Lauf auf dem Xiaomi 15): Das Laden des 1,7B-Pakets (1,74 GB Rechenprogramme) endete mit `ApplicationExitInfo` „vom Nutzer beendet“ und der Beschreibung des Systems
  `[FORCE STOP] stop dev.personal.secondhand.companion due to used too many dmabuf resource,dmabuf used rss:3966224kb,dmabuf threshold:1572864kb`. HyperOS beendet also eine App, deren dma-buf-Nutzung über 1 572 864 kB (1,5 GiB) liegt; das Modell erreichte 3 966 224 kB, das 2,3-Fache
  der Rechenprogramme. Die Regel über freien Speicher (80e) und der Wächter sahen das nicht: Sie messen `MemAvailable`, nicht dma-buf. Wahrscheinlich war auch das Ende des 4B-Versuchs (Meldung „Speichermangel“) diese Grenze oder ihre Folge; nicht belegt. Eine Beschreibung der Regel
  oder Ausnahmen für Nutzer fand die Websuche nicht (siehe VALIDATION 80f). Seit 0.6: Regel 2 sagt vorher (2,4 mal die Rechenprogramme) und sperrt jedes Modell; „Messversuch“ lädt trotzdem, aber `LoadWatchdog` beendet die App selbst, sobald ihr dma-buf 1350 MiB erreicht (88 Prozent der Grenze von 1536 MiB, Vermutung: Zuwachs zwischen zwei Messungen), Messung alle 100 ms. dma-buf der App: `AndroidMemoryMeter.dmabufBytes` nimmt das Größte von `/proc/self/dmabuf_rss` (Xiaomi-Datei, Einheit geschätzt, `DmaBufParse.procBytes`), der Summe der `size:`-Zeilen in `/proc/self/fdinfo` (dma-buf-Dateien haben `exp_name:`) und der Summe der Abbildungen mit „dmabuf“ im Namen in `/proc/self/maps`; ob das die Zählung von HyperOS trifft, ist eine Vermutung.
- **P343** Begleit-App, Berichte überleben das Ende des Prozesses (80f): Der Bericht lag nur im Speicher; nach dem Zwangsbeenden zeigte der nächste Start nur die Schrittmarke und den Grund des Systems. Jetzt schreibt `ReportLog` jede Zeile über `sink` in die Datei `files/run-logs/run-<Millisekunden>.txt` (`RunLogFile.append`:
  schreiben, `flush`, `FileDescriptor.sync`), mit Zeitstempel je Zeile; die Messungen während des Ladens gehen nur in die Datei (`addQuiet`, jede fünfte Messung). Oben in jeder Datei steht, dass sie keine Texte des Nutzers enthält (die Prüftexte sind fest eingebaut). Behalten werden die letzten drei Läufe, je höchstens 200 000 Bytes
  (danach eine Schlusszeile). Beim Start liest `RunLogStore.previousRunText` die Datei des vorherigen Laufs vor dem Anlegen der neuen; sie ist aufklappbar unter der Kopfzeile, „Bericht kopieren“ gibt vorherigen und aktuellen Lauf mit Trennzeilen und Startzeit. **Warum der Block „Letzte Speichermessung“ in 0.5 fehlte, ist nicht bewiesen.** Geprüft: Der Wächter
  wird im Code vor `port.load` gestartet und schreibt mit `commit()`; der Test `aLoadThatNeverReturns…` bildet ein nie endendes Laden mit dem echten Wächter und der Datei nach und findet Schritt, Befunde und Messungen auf der Platte. Gefunden wurde eine Lücke: 0.5 zeigte den Block nur, wenn die Messung unfertig war (`!finished`); `stop()` setzt `finished`
  schon bevor der Schritt abgeschlossen ist (`tracker.end` kommt danach), und endet der Prozess in dem Moment, sieht der nächste Start eine Schrittmarke und eine fertige Messung und zeigte nichts. Seit 0.6 zeigt `StartupReport.lastRunLines` den Block, sobald eine Schrittmarke blieb oder die Messung unfertig ist. Offen bleibt, ob `commit()` das Zwangsbeenden von HyperOS nicht überlebt
  (auf der JVM nicht prüfbar); die Dateizeilen mit `sync` umgehen das.
- **P344** Begleit-App, Quelltext-Befunde zum Speicher des QAIRT-Wegs (80f, `main` des Plugins, Zeilen siehe VALIDATION 80f): Ein-/Ausgabepuffer immer als dma-buf (`SHARED_BUFFER` fest, `libdmabufheap`), ein Block je Kontextdatei, geteilt von allen Graphvarianten; alle sechs Graphen je Datei (`prompt_ar128_cl{512,1024,4096}`, `token_ar1_cl{512,1024,4096}`) werden geladen, eine Auswahl gibt es nicht (`loadSelectGraphs` und
  `graphSwitching` fest `false`); `mem_type` und die Schlüssel der Genie-Konfiguration sind wirkungslos. Die Rechenprogramme des 1,7B (1,62 GiB) liegen allein schon über der HyperOS-Grenze (1,5 GiB). Darum: höchstens eine Modell-Instanz (`InstanceCounter` im Adapter, Prüfung im Läufer), die Phasenzeilen (SDK-Start, vor und nach dem Laden, Erzeugung, Entladen) und der Verlauf des dma-buf in der Berichtsdatei. Das GGUF-Backend (`ggml-hexagon`) nutzt ebenfalls `rpcmem` für die auf die NPU gelegten Tensoren; ungemessen.
- **P345** Symbolseiten (81): Die Hinweise und Popups der Buchstabentasten kamen aus der ersten Reihe der Standard-Datei
  `symbols.txt`. Wer diese Reihe aus der Seite nimmt (wie Gboard), verschiebt jeden Hinweis (q zeigt dann `@` statt `%`).
  Darum liegen sie in `symbol_hints/symbols_hints.txt`; `SymbolPagesTest.letterKeysKeepTheirHintsAndPopupKeys` vergleicht
  jede Buchstabentaste in vier Stilen, Deutsch und Englisch, Zahlenreihe an und aus, mit einer Aufnahme von vor der
  Änderung. Die Datei darf kein wählbares Layout sein (nicht in `layouts/symbols/`, sonst bietet sie die Layout-Auswahl an).
- **P346** Symbolseiten (81): „Zahlenreihe in Symbolen“ ersetzte die erste Symbolreihe durch Ziffern. Mit der Gboard-Seite
  wäre das die Reihe `@ # € …` gewesen (nur noch als Popups). Für das eingebaute Layout ist die Zahlenreihe auf Seite 1
  immer Reihe 1 und ersetzt nichts. Eigene Layouts behalten das alte Verhalten. Ein Test der Seiten muss beide
  Einstellungen durchspielen (`SymbolPagesTest.theNumberRowSettingDoesNotChangeAnythingOnThePages`).
- **P347** Symbolseiten (81): `<` und `>` der zweiten Seite kamen früher über die letzte Basisreihe (Zweiergruppe) und erbten
  dabei die Popups von Komma und Punkt (Zwischenablage, Prompt-Ablage, Emoji, Einhandmodus …). Jetzt stehen sie als
  `moreSymbols` in `functional_keys.json` mit eigenen Popups; die Basisreihe bleibt nur für andere Funktions-Layouts.
  Ändert man die Reihenfolge der Auswahl in `KeyboardStateSelector.compute`, ändern sich beide Seiten.
  Eigene Funktions-Layouts ohne `moreSymbols` haben weiter die alte Unterreihe.
- **P348** Symbolseiten (81): Die Pfeiltaste ist ab jetzt standardmäßig aus (`Defaults.PREF_SHOW_DPAD_KEY`). Eine Einstellung, die
  der Owner einmal gesetzt hat (auch „an“), steht als gesetzter Wert in den Einstellungen und gewinnt gegen den Standard; nichts
  wird migriert. Keine Stelle im Code darf alle Standardwerte in die Einstellungen schreiben, sonst gilt der alte Wert weiter.
- **P360** Auswahl-Chips (82): Der Zustand wird beim Bauen der Leiste gelesen (`LatinIME.getAgentSelection`), nicht gemerkt; ein
  Tipp prüft Markierung (Anfang und Ende) und Editor-Sitzung des Feldes (`Bound`) und den Sperrzustand frisch (`LockState`, der Tipp
  auf einen Chip leert ihn ohnehin, P309) und tut sonst nichts. „Alles auswählen“ und „Wort markieren“ gehen über
  `mIC.performContextMenuAction` bzw. `mIC.setSelection`, ohne die erwartete Markierung der `RichInputConnection` zu setzen; die
  Markierung kommt als normales `onUpdateSelection` zurück, das über `postResumeSuggestions` die neutrale Leiste baut, aber
  `restartSuggestionsOnWordTouchedByCursor` kehrt auf einigen Wegen vorher zurück: darum baut `LatinIME.onUpdateSelection` die Leiste selbst
  neu, wenn Chips stehen und die Markierung eine andere ist (`afterSelectionUpdate`). Die Robolectric-Schattenverbindung sendet das
  Update nicht (`reportSelection` in `InputLogicTest`) und `handleMessages` verwirft die verzögerte Vorschlagsaktualisierung.
  Kopieren, Ausschneiden und Einfügen baut die Leiste in `SelectionChips.afterKey` neu; ein Tipp, der die Markierung entfernt, baut
  sie sofort neu (`shown`-Merker). Die Zeile kommt aus `KeyboardCommands.rowFor`: bei gleicher Beschriftung wird dieselbe Ansicht
  wiederverwendet und ihre Tipps an die neue Markierung gebunden (ein „alter“ Chip derselben Zeile gilt also für die jetzige
  Markierung; nur eine Zeile mit anderen Chips, z. B. neuem „Einfügen“, lässt die alte Ansicht an ihre alte Markierung und ihr altes
  Feld gebunden). „Einfügen“ fragt die Zwischenablage einmal je Clip (`clipChanged` aus dem Listener des Verlaufs-Managers).
- **P361** Auswahl-Chips stehen in jedem Feld außer Passwort, Inkognito, Feld ohne Lernen und gesperrtem Gerät (`FormatGuardChips.isBlocked`
  plus `LockState`), strenger als die Kopieren-Taste der Werkzeugleiste (`copyText` prüft nichts). Felder, die beim Fokus alles
  markieren (Adressleiste), zeigen die Chips sofort; Tippen ersetzt die Markierung und nimmt sie weg. Sie rufen nie
  `setToolbarVisibility` auf (die erste Fassung schloss im Modus „Aufklappbar“ bei jedem Neubau die Werkzeugleiste): Eine offene
  Werkzeugleiste verdeckt sie wie jeden Vorschlag (**in der festen Leiste seit 85 nicht mehr:** die Chips ersetzen die Werkzeuge, der Tipp auf
  „Werkzeuge zeigen“ gilt aber für diese Markierung **klebrig**, `BarState.stickyTools`: Bereichsänderungen und das Echo der App bauen die Chips
  dahinter neu und lassen die Werkzeuge stehen, bis die Markierung endet (neutrale Leiste) oder Wörter kommen; der Pfeil links bringt die Chips
  zurück und wieder die Werkzeuge, `LeftKey.SHOW_CONTENT`; `LatinIME.tryShowSelectionChips` meldet `selection = true`); im Flat-Stil und mit „Leiste automatisch ausblenden“ gilt, was
  `SuggestionStripView.setExternalSuggestionView` für jede externe Ansicht tut (Werkzeugleiste ausblenden, wie bei der Pille). Mit
  `mAutoShowToolbar` bauen sie nichts, damit gilt das alte Verhalten (Werkzeugleiste bei Markierung). Schalter
  `AgentPreferences.SELECTION_CHIPS` („Aktionen bei markiertem Text“, `AgentAreaBarScreen`, Standard an). Wischtippen über einer
  Markierung: `setSuggestedWords` zeigt für `UPDATE_BATCH`/`TAIL_BATCH` keine Chips, damit die Wörter erscheinen.
- **P362** `performContextMenuAction(startSelectingText)` fehlt mit Absicht. Aus dem Framework gelesen (nicht auf einem Gerät
  geprüft): `TextView` kennt die Kennung nicht und lehnt sie ab; WebView und andere Editoren schalten damit einen Auswahlmodus ein,
  in dem die nächste Cursorbewegung markiert, also nicht harmlos.
- **P363** Erkenner (83): Ein Text ist unvollständig, wenn keine seiner Vorlagen so wenige Striche hat wie die Zeichnung; er gewinnt nur,
  wenn kein vollständiger Kandidat angenommen wird. Eine Vorlage mit nur einem Strich (eigene Symbole, gelernte Zeichnungen) ist für jede
  Zeichnung vollständig. Wer Vorlagen ergänzt, ergänzt auch die Fingervarianten mit weniger Strichen (ein Pfeil ohne Absetzen, ein
  Herz in einem Zug, „%“ in einem Zug); sonst gewinnt ein einfacheres Symbol, das zufällig passt, oder es erscheint nichts. Neue gerade
  Striche (wie „/“) wirken über `InkDots.bareLines` in Regel 4. Einzelne Striche um 18° bis 75° sind „/“, flacher „–“, steiler „|“.
  Verbundene Fingervarianten mit Sprung (Kreis, Strich, Kreis) werden unter dem Rauschen des Prüfstands schlecht erkannt
  (`InkRecognizerTest`, verrauschte Zeichnungen: „%“ 69 von 400 nicht erkannt, gesamt 97,5 %; die Variante ist für saubere Zeichnungen da).
- **P370** Die Leiste eines Trennzeichens kommt nach `endBatchEdit()`, nicht davor (84). Gelesen im Framework (`TextView.handleTextChanged`,
  Bytecode von Android 16, nicht auf einem Gerät geprüft): ein `EditText` ruft `updateAfterEdit()` (Neuzeichnen) nur bei `mBatchEditNesting == 0`; innerhalb von
  `beginBatchEdit`/`endBatchEdit` zeigt es das Zeichen also nicht. Jede Arbeit, die der Editor nicht braucht (Leiste, Chips, Lesen für das Lernen),
  gehört hinter `endBatchEdit()` in `onCodeInput`; sonst wartet das Zeichen darauf. Neue Arbeit in `handleSeparatorEvent`: `setNeutralStripAfterBatch()`,
  nicht `setNeutralSuggestionStrip()`. Bei mehreren Ereignissen einer Eingabe (kombinierte Zeichen) läuft die Leiste zwischen den Ereignissen, damit das nächste sie wie
  früher sieht. Ein Aufruf außerhalb von `onCodeInput` zeigt die Leiste sofort.
- **P371** Die zweite Runde der Chip-Quellen in `setNeutralSuggestionStrip` (`setSuggestedWords` fragt Befehl, Symbol, Auswahl und Screenshot noch einmal)
  ist nicht überflüssig: `InputLogic.setSuggestedWords` setzt dazwischen `mSuggestedWords`, und der Screenshot-Chip (`screenshotMayReplaceStrip`) und die
  Vorhersage-Chips lesen es. Der erste Versuch, sie zu überspringen, ließ `screenshotChipKeepsTheAppsCompletionsAndInlineAutofill` rot werden. 9 Prüfungen je Trennzeichen
  (5 + 4) sind der Stand; ein Weglassen braucht einen Beweis je Quelle, dass sie `mSuggestedWords` nicht liest.
- **P372** Nach einem Trennzeichen außer Leerzeichen kommt immer die neutrale Leiste (Ausnahmen: `:` wegen der Emoji-Suche im Text, Leerraum, und wenn die Suche läuft). Darum baut
  die synchrone Vorschlagsabfrage vor dem Festschreiben (wartende 100-ms-Aktualisierung) für sie keine Leiste (`showOnStrip` false); `InputLogic.setSuggestedWords` läuft weiter,
  denn aus ihm kommt die Autokorrektur und die blaue Unterstreichung. Bei Leerzeichen bleibt es wie früher: die Leiste zeigt die Wörter bis zur nächsten Aktualisierung. Wer ein
  neues Trennzeichen mit eigener Folge einführt (Zeichen, nach dem keine neutrale Leiste kommt), nimmt es in `stripFollows` aus.
- **P373** Die Messung zählt Arbeit, nicht Zeit: `SymbolLatencyTest` schreibt die Wanduhr-Zeiten nur in eine Datei (kein Assert). Der Editor im Test antwortet sofort; Binder-Aufrufe
  (Lesen von Text, Zwischenablage) kosten dort nichts. Zahlen der Tabelle: Schritte der Hauptthread-Arbeit je Taste, nicht Millisekunden. Neue Arbeit auf dem Tastenpfad bekommt eine
  Zeile `TypingPerf.work(Work.…)` (Eintrag in `Work` nachtragen) und einen Fall in der Tabelle.
- **P374** `TypingEquivalenceTest`: die Wörter der Leiste nach einer Rücktaste oder nach dem schnellen Tippen hängen davon ab, welche von zwei Nachrichten (verzögerte Aktualisierung,
  Wiederaufnahme der Vorschläge) zuerst beantwortet wird, und wechselten bei ausgelastetem Rechner; der Emoji-Tabellen-Thread (`EmojiSuggestions`) füllt die Leiste mit Emojis, wenn er fertig
  ist (`TypingRig.setUp` schaltet ihn aus). Verglichen wird dort nur der Editor. Wer Folgen ergänzt: `strip=` nur für Folgen ohne Rücktaste.

- Ruhige Leiste (Etappe 89, Stufe 1; Konzept `docs/LEISTE-RUHE-KONZEPT.md`, Fallstricke P490–P495): `K/latin/suggestions/BarState.kt` hat `calm` (die Regel gilt im Feld), `writing` (Schreibsitzung), `pinned`
  (Werkzeuge auf Wunsch) und `CALM_DEBOUNCE_MS` (250). `onTyped()` setzt die Sitzung, `onNeutral(fieldEmpty)` lässt in der Sitzung `show = CONTENT` (leerer Bereich), `onContent` und `onNeutral` ändern bei `pinned` nichts, `onLeftKey` (SHOW_TOOLS)
  und `onToolTapped()` pinnen. `SuggestionStripView.kt`: `updateCalm()` (Feld kann Wörter zeigen: `mShouldShowSuggestions && mSuggestionsEnabled && !mApplicationSpecifiedCompletionOn`), `onTyped()`, `setFieldEmpty()`, `isWritingSession()`,
  Entprellung in `setSuggestions` (neutral + Sitzung + Wörter sichtbar → `staleWords`, `calmHandler.postDelayed(releaseWords, 250 ms)`; eine neue Liste, ein Chip, ein neues Feld oder Verstecken bricht ab), Sperre in `onClick`/`onLongClick`/Wischen
  für veraltete Wörter, `noteShown()` für die Zähler. `LatinIME.java`: `onAgentTyped()`, in `setNeutralSuggestionStrip` `setFieldEmpty(Sitzung && getCodePointBeforeCursor() == NOT_A_CODE)` und „automatisch anzeigen“ nach Enter nur ohne Sitzung,
  in `onStartInput` der Zähler `BarCalmCounters.onFieldStart`. `KeyboardActionListenerImpl.kt`: `barSessionKey` in `onCodeInput` (Code ≥ 0 oder `DELETE`: `onAgentTyped` und der Weg der Rücktaste für die Diagnose), `onStartBatchInput`, `onUpWithDeletePointerActive`
  (wenn gelöscht). `A/BarCalmCounters.kt` (nur Zahlen), `Diagnose.barCalm` (Karte „Leiste“, `AgentDiagnoseScreen`), Texte `agent_barcalm_strings.xml` (de, en). `InputLogic`, `RichInputConnection` und `PointerTracker` sind unberührt.
  Tests: `StripCalmLogTest` (`T/latin/`, tippt über `TypingRig` einen Satz, einen Absatz und zehn Rücktasten, fragt die Leiste alle 10 ms ab und vergleicht mit `src/test/resources/stage89/strip-protocol-*.txt`; `TypingRig` hat `predictions` und
  `startField(…, imeOptions)`), `BarStateTest`, `AT/BarCalmCountersTest`. Gerätetest: `bar_calm_typing` in `tools/devicetest/devicetest.py`.
- Etappe 99 (Konzept Abschnitt 10, Fallstricke P505, P506): `BarState.resting` und `onPause()` (Werkzeuge nach `WRITING_PAUSE_MS` = 4 s ohne Taste, unabhängig vom Inhalt), `CALM_DEBOUNCE_MS` = 250;
  in der Sitzung zeigt `onNeutral` die Mitte (leer), Werkzeuge nur bei `pinned` oder `resting`. `SuggestionStripView`: `writingPause` (Handler, in `onTyped` neu gestartet, abgebrochen bei Feldstart und
  Verstecken; überspringt Chips, Raster, Anpassen, Emoji-Reihe, Liste weiterer Wörter), `endCircle` mit `arrangeEndCircle`/`endCircleShown` (in `arrangeBar`, Sichtbarkeit in `applyBarState`),
  Langdruck des Kreises leitet an den Platz der Zeile weiter. `FixedBar.styleSlot`, `endCircleOf`, `arrangeEnd` (zweiter Knopf, Id `agent_bar_end_circle` in `agent_ids.xml`).
- **P380** Feste Leiste, nur ein Ort setzt Sichtbarkeiten (85): `SuggestionStripView.applyBarState` setzt Werkzeuge, Vorschlagsleiste, angeheftete Tasten, Verfügbarkeit und linke Taste
  zusammen aus `BarState` (und dem offenen Raster). Alte Wege, die `suggestionsStrip.isVisible` oder `toolbarContainer.isVisible` selbst setzen (`clear`, `removeSuggestion`,
  `onVisibilityChanged`, `setToolbarVisibility`), sind für die feste Leiste umgeleitet oder gesperrt; wer einen neuen setzt, zeigt Wörter neben den Werkzeugen. Eigene Sichtbarkeit nie
  an `toolbar_container` setzen: es ist in der festen Leiste ein `FrameLayout` mit der Kennung des alten `HorizontalScrollView`.
- **P381** Feste Leiste, Mitten (85): Die Mitten von linker Taste und Werkzeugen liegen nur dann gleichmäßig von Rand zu Rand, wenn alle gleich breit sind. `FixedBar.arrange` setzt die Breite der
  linken Taste mit (voll 40 dp, flach 34 dp, kompakt 30 dp). Wer Größen ändert, ändert den Test `FixedBarTest.slotsAreEvenlySpread…` mit (er fand den Fehler im ersten Lauf: 171 gegen 175 px).
  Beim Ziehen werden die Plätze bei jedem Ereignis neu gebaut; bis zum Layout haben sie Breite 0. Die Zuordnung zum Platz (`FixedBar.slotCentres`) rechnet darum aus Anzahl und Breite der Zeile.
- **P382** Gedimmte Werkzeuge (85): `View.performClick` prüft `isEnabled` nicht (Berührungen schon). `SuggestionStripView.onClick`/`onLongClick` und `ToolGridView.act` prüfen es selbst. Im Anpassen-Modus
  (86) sind gedimmte Plätze und Pillen aktiv (nur der Alpha-Wert bleibt), damit man sie herausnehmen kann. Das Dimmen ist ein Hinweis: Die Steuerungen der Werkzeuge (`AgentEditorController`) prüfen beim Tipp weiter
  selbst. Die Menge der gedimmten Werkzeuge (`BarTools.PROTECTED`) ist aus ihren Verweigerungsgründen (Passwort, Inkognito, kein Lernen, gesperrt) gelesen; kommt ein Werkzeug dazu, das so prüft, gehört es hinein.
- **P383** Speicherformat (85): `Constants.Separators.KV` ist `:` und `ENTRY` ist `|` (nicht `,` und `;`). Die Einstellung `agent_bar_tools` ist `NAME:true|NAME:false|…`. Ein leerer Wert ist eine Wahl (keine Werkzeuge), ein fehlender Wert
  heißt „nie bearbeitet“ und wird jedes Mal neu aus den alten Einstellungen **berechnet** (`BarTools.read` schreibt nie). Geschrieben wird nur bei Bearbeitung (`write`, `pinKey`, `unpinKey`, jedes Ablegen im Anpassen-Modus).
- **P384** Diagnose (85): `LastField.onStripView` bekommt `toolbar = toolbarContainer.isVisible && (!fixedBar || barState.toolsChosen)`. Die neutrale feste Leiste (Werkzeuge, nichts vorgeschlagen) ist „leer“, nicht „Werkzeugleiste“; sonst
  lehnt `UpdateOnOpen.refresh` ab (`LastField.stripNeutral`) und die Diagnose meldet Wörter unter der Werkzeugleiste. Nur „Werkzeuge zeigen“ per Hand zählt als Werkzeugleiste.
- **P385** Flaches Menü (85): `FlatMenuView` und `FlatStrip.toggleMenu` sind nur noch bei ausgeschaltetem Schalter „Feste Leiste wie Gboard“ im flachen Stil erreichbar (die linke Taste gab es in keinem anderen Modus). Wer das Menü ändert, prüft es mit
  `FlatMenuTest` (Schalter aus); wer das Raster ändert, mit `FixedBarTest`/`BarEditModeTest`. Beide dürfen nie gleichzeitig offen sein (`FlatStrip.closeMenu` ist in der festen Leiste ausgeschaltet).
- **P386** Anpassen (86, ersetzt in 88, siehe P475): `startDragAndDrop` gab es nur mit Fenster; Tests ersetzen `SuggestionStripView.dragStarter` und rufen den Zuhörer der Leiste (`toolbarContainer`) und des Rasters (`ToolGridView.dropListener` an Inhalt und Scroll-View) mit echten
  `DragEvent`-Objekten auf. Eine Änderung der Ereignislogik (`onBarDrag`, `onGridDrag`, `dragSlot`, `BarEditor`) prüft `BarEditModeTest` und `BarEditorTest`, ein echter Fingertest steht aus. Der Zustand der Ablage liegt im `BarEditor` (nur dort wird gespeichert, beim Loslassen); die Leiste
  baut ihre Plätze nur neu, wenn sich die angezeigte Liste ändert (`renderedBar`). Der Langdruck auf einen Platz ohne eigene Aktion startet den Modus; ein neues Werkzeug mit Langdruck-Aktion (`getCodeForToolbarKeyLongClick`) behält sie.

- **P387** Auswahl und Werkzeuge (Nachprüfung 85/86): Die Wahl „Werkzeuge zeigen“ ist nur für Auswahl-Chips klebrig (`setExternalSuggestionView(…, selection = true)`); Wörter, andere Chips und eine neutrale Leiste beenden sie. Wer einen
  neuen Inhalt einführt, der sich bei jedem Neubau wiederholt (Echo der App), gibt ihm dieselbe Behandlung oder verliert die Wahl des Nutzers. Solange die Wahl klebt, ist der Pfeil links „Auswahl-Aktionen zeigen“ und das Raster nur über
  Langdruck erreichbar (nach dem Ende der Markierung ist es wieder der Raster-Knopf); `onContentRequested` („Leiste automatisch ausblenden“) lässt die Werkzeuge ebenfalls stehen.
- **P388** Platz gegen Listenstelle (Nachprüfung 85/86): Ein verstecktes Werkzeug (`VOICE` ohne Spracheingabe) bleibt in der gespeicherten Liste, hat aber keinen Platz. Plätze zählen nur sichtbare Werkzeuge, `BarTools.move/insert` zählen
  die ganze Liste: Zwischen beiden vermittelt `BarEditor.listIndexForSlot` (und `canShift`/`shift` überspringen versteckte Werkzeuge). Neue Stellen, die Plätze in Listenstellen umrechnen, nehmen diese Funktion.
- **P389** Kosten je Tastendruck (Nachprüfung 85/86): `applyBarState` läuft bei jedem Neubau der Leiste. Das Raster wird je Wurzel-View nur einmal gesucht (`toolGrid`), Beschreibungen kommen aus einem Zwischenspeicher (`labelOf`), `FixedBar.arrange` läuft nur bei
  Änderung der Sichtbarkeit der Sprachtaste und bei jedem Neubau der Plätze, die linke Taste setzt Symbol und Text nur bei Änderung. `BarCounters` zählt Suche, Neubau, Anordnung und Textabrufe; `FixedBarTest.aStripUpdateWhileTyping…` hält sie bei 0.
- **P390** Feldwechsel (Nachprüfung 85/86): Ohne `hideWindow` zwischen zwei Feldern (`onStartInputView` mit anderem Feld) schließt `LatinIME` über `SuggestionStripView.onFieldStarted` das Raster und den Anpassen-Modus (nichts Ausstehendes wird gespeichert,
  ein Neustart im selben Feld lässt es offen). Gedimmte Werkzeuge werden danach aus den neuen Einstellungen berechnet. `KeyCode.AGENT_DESIGN` prüft den Sperrzustand live (`LockState`), wie `KeyCode.SETTINGS` (gesperrt: nichts).
- **P391** Zerstörende Werkzeuge (Nachprüfung 85/86): Das Raster bietet `BarTools.DESTRUCTIVE` (`CLEAR_CLIPBOARD`: leert Verlauf, Tresor und Zwischenablage mit einem Tipp) nur, wenn der Nutzer es vorher in einer Liste hatte (Leiste, alte Leistenliste, angeheftet). Alle
  anderen Werkzeuge des Rasters schalten einen Modus, öffnen ein Fenster oder bearbeiten Text (Rückgängig); `BACKGROUND_GATHERING` (Daten sichern oder verwerfen) erscheint wie in den Einstellungen nur nach der ersten Nutzung. Der Speicher `agent_bar_tools` gilt als
  unbrauchbar (Rückfall auf die Berechnung, ohne zu schreiben), wenn er leer ist oder ein Eintrag nicht `NAME:true`/`NAME:false` ist. `FlatPreset` setzt und sichert `agent_bar_tools` mit den anderen Leistenwerten (die Karte „angewendet“ sieht es).
- **P470** Schwarze Symbole (88): Die Leiste färbt Symbole mit `Colors.setColor(…, TOOL_BAR_KEY)`, einer Farbliste im Modus MULTIPLY. Ein Symbol muss **weiß** gefüllt sein (weiß mal Farbe = Farbe); jede andere Füllung
  (`#FF000000`, `@color/foreground_weak`) ergibt Schwarz oder Dunkelgrau in jedem Design. Das war der Fehler von Build 41 (AGENT und SKILL_1 schwarz auf dunklem Grund). Ein neues Werkzeugsymbol: weiß füllen;
  `BarIconVisibilityTest` zeichnet **jedes** `ToolbarKey` als echte Pixel und fällt bei einem ungetönten Symbol.
- **P471** Kontrastregel (88): Die Farbliste der Leistensymbole kommt aus `StripIconColors.guaranteed`, nicht mehr unmittelbar aus `Colors`; `RgbStrip.paintImage` nimmt für Leistenplätze die Liste aus dem Tag `agent_bar_tint`
  (die RGB-Beleuchtung bleibt damit lesbar) und lässt Plätze mit Tag `agent_bar_off` aus. Wer eine Farbe der Symbole ändert, ändert sie dort. Der Kontrast hängt am Streifen (`StripIconColors.background`); mit einem Hintergrundbild
  der Tastatur gilt die Farbe `MAIN_BACKGROUND` als Näherung (nicht an einem Bild geprüft).
- **P472** Nicht verfügbar (88): `FixedBar.setAvailable(button, reason, …)` setzt weder `alpha` noch `isEnabled=false`; das Werkzeug bleibt für die Berührung aktiv, `SuggestionStripView.onClick`/`onLongClick` und
  `ToolGridView.act` prüfen `reasonOf`/`reasons` und zeigen den Grund. Wer eine Stelle ergänzt, die `isEnabled` eines Platzes liest, fragt `FixedBar.reasonOf`. Beschreibung: `„<Name>. <Grund>“`.
- **P473** Plätze (88): Plätze hängen nur von der Anzahl ab (gleiche Mitten, auch mit dem Kreis am Ende; erst auf gleiche Lücken zwischen sichtbaren Rändern gebaut, wegen der Referenzbilder des Owners zurückgenommen, siehe `docs/LEISTE-KONZEPT.md`). Der Test
  `slotsAreEvenlySpread…` prüft die Mitten, die gleiche Größe der Symbolflächen und gleiche sichtbare Lücken zwischen gleich großen Symbolen. Wer Größen ändert, ändert `FixedBar.Sizes`, diesen Test und `FlatStyleRenderTest`.
- **P474** Linke Taste (88): Ihr Symbol bekommt den Kontrast gegen die Scheibe (`toolbar_expand_key_background`, 20 von 24,8 der Taste) oder, im flachen Stil, gegen den Streifen; die Farbe steht als `colorFilter` auf der Ansicht
  (`leftKeyIconBase/Behind` zwischenspeichern die Rechnung).
- **P475** Systemziehen (88): `startDragAndDrop` im Tastaturfenster (IME-Fenster, nicht fokussierbar, neben dem App-Fenster) lieferte auf dem Gerät des Owners kein Ablegen (Symbol folgte dem Finger und schnappte zurück, Build 41).
  Das Ziehen läuft darum in **einem** Behälter mit eigener Berührungsverarbeitung (`ToolGridView` + `BarDrag`); `DragEvent`, `dragStarter`, `dropListener` gibt es nicht mehr. Wer das Ziehen ändert, prüft es mit echten
  Berührungen: `BarEditModeTest` (MotionEvents) und die Gerätetests `bar_edit_*` (`input motionevent`).
- **P476** Behälter (88): `ToolGridView.onInterceptTouchEvent` nimmt DOWN auf einem Leistenplatz sofort, auf einer Pille erst bei einer Aufnahme (Halten, seitliche Bewegung). Ein senkrechter Zug auf einer Pille vor der Haltezeit
  gehört dem `ScrollView` (die Logik ist dann sofort wieder `IDLE`, P484). `onTouchEvent` gibt ohne eigene Berührung `false` zurück, sonst käme die linke Taste (Häkchen) nicht durch. Ein kurzes Tippen wird im Behälter erledigt (`Event.Tapped`),
  nicht über `performClick` der Pille. Der Timer der Haltezeit ist ein `Handler` (ein `View.postDelayed` ohne Fenster läuft nie).
- **P477** Haltezeit auf der Stelle (88): Halten und Loslassen ohne Bewegung um den Slop legt das Werkzeug zurück (`travelled`); eine Pille, die durch Scrollen in die Zone gerät, wird sonst ungewollt eingefügt.
- **P478** Lücke ohne Rückkopplung (88): Der Platz unter dem Finger ist der nächste der Anordnung **nach** dem Ablegen (`BarEditor.slotCountAfterDrop`, nominale Mitten `FixedBar.slotCentres(count)`), nicht der der gerade gezeichneten
  Plätze; die Lücke öffnet sich also nicht, weil sie sich selbst verschiebt. Versteckte Werkzeuge (Sprachtaste ohne Spracheingabe) zählen nicht (P388). Rechts nach links: `barGeometry.slotAt` spiegelt.
- **P479** Kopie und Animationen (88): Die Kopie ist ein Kind von `ToolGridView` (nicht von Fenster); ohne Fenster (Tests) wird sie ohne Animation entfernt. `glideSlots` und `shakeSlots` laufen nur mit Fenster. Jeder Zustand
  ist vor der Animation endgültig (gespeichert, Anordnung): Animationen sind nur Bewegung.
- **P480** Rückgängig im Anpassen-Modus (88): `BarEditor.previous` hält eine Ebene; `reset` ist eine Änderung wie jede andere und mit `undo` rückholbar. `ToolGridView.refreshActions` blendet „Rückgängig“ aus, wenn es nichts gibt.
- **P481** Gerätetests der Leiste (88): `bar_edit_*` ändern die Leiste dauerhaft in der App der Tastatur und stellen sie am Ende mit der Oberfläche („Zurücksetzen“, „Ja“) wieder her (`_restore_default_bar`, auch im Fehlerfall); neue
  Szenarien dürfen sich nicht auf andere Werkzeuge in der Leiste als die Standard-Leiste verlassen. `shot()` druckt Bilder als `SHOT`-Zeilen ins Protokoll (`tools/devicetest/shots_from_log.py` baut die PNG-Dateien).
- **P482** Kontextwerkzeuge (88): `UndoOffer` kennt nur Tastencodes (Heuristik); `onTextInput` (Emoji) und das Wählen eines Vorschlags laufen nicht über `onCodeInput` und beenden das Angebot nicht. Das schadet nicht (ein Langdruck-Wiederholen
  tut dann, was die App tut). Das Angebot ist ein Zustand der Tastatur, nicht des Feldes: nach dem Verstecken ist es weg.
- **P483** Hintergrundbild (88): Die Kontrastregel der Leistensymbole (`StripIconColors.background`) rechnet mit `STRIP_BACKGROUND` über `MAIN_BACKGROUND`. Hat das Design ein Hintergrundbild der Tastatur, kennt die Regel das Bild nicht: Der Kontrast gilt gegen die Farbe,
  nicht gegen das Bild (ein heller Bildstreifen hinter einem hellen Symbol wird nicht erkannt). Nicht gebaut, nicht geprüft; wer es baut, misst den mittleren Bildwert hinter der Leiste.
- **P484** Eine Berührung hängt nie an einem alten Zustand (88, Prüferbefund): Gibt der Behälter eine senkrechte Geste an den `ScrollView` ab, ruft dieser `requestDisallowInterceptTouchEvent(true)`, und der Behälter sieht weder weitere Moves noch das Ende. Darum geht `BarDrag` bei `Event.Scroll` sofort in `IDLE`,
  jedes neue `ACTION_DOWN` ruft `endTouch` (alter Zustand weg, ein laufendes Ziehen wird wie Abbruch zurückgegeben), `requestDisallowInterceptTouchEvent(true)` (jede Übernahme der Geste durch ein Kind: Scrollen, Anhalten eines Flings mit einem Finger) beendet eine noch nicht ziehende Berührung sofort, damit der Timer der Haltezeit unter einem Finger, der nur das Raster anhält, nichts aufnimmt (eine Prüfung von `scrollY` wäre damit überflüssig und entfiel), `onDetachedFromWindow` bricht ab. Im Raster sind geteilte Berührungen ausgeschaltet
  (`isMotionEventSplittingEnabled = false`): ein zweiter Finger auf einer anderen Pille tut nichts. Tests: `BarEditModeTest` (`afterScrolling…`, `aScrollThatTheContainerNeverSeesTheEndOf…`, `aTouchThatStopsAFlingPicksNothingUp…`, `aSecondFinger…`), Gerätetest `bar_edit_scroll_then_drag`.
- **P485** Wiederholen-Angebot endet bei jeder Texteingabe (88): zusätzlich zu den Tastencodes (`KeyboardActionListenerImpl.onCodeInput`) ruft `UndoOffer.onTextChange()` an den **gemeinsamen Stellen** in `LatinIME.java`: `onTextInput` (Emoji, mehrere Zeichen, Chips), `onPrivateTextInput` und `pickSuggestionManually` (Leiste, erweiterte Vorschläge `MoreSuggestionsView`, Chips wie `pickPrediction`),
  dazu `KeyboardActionListenerImpl.onStartBatchInput` (Gestentippen) und `onContent` (Sticker, GIF); `SHIFT_ENTER` steht nicht mehr in der Liste der folgenlosen Codes. Nichts davon berührt `InputLogic` oder `RichInputConnection`. Test je Weg: `UndoOfferHooksTest` (echte `LatinIME`).
- **P486** Grund bei „Immer Inkognito“ (88): `BarTools.unavailableReason(…, alwaysIncognito)`: Ist der Schalter `PREF_ALWAYS_INCOGNITO_MODE` an, heißt der Grund `INCOGNITO_SWITCH` („Nicht verfügbar: „Immer Inkognito“ ist eingeschaltet“), nicht „privates Feld“, auch wenn das Feld selbst privat ist.
- **P487** An/aus bei Umschalt-Werkzeugen (88, dreimal überarbeitet): `StripIconColors.guaranteed` in drei Schritten: (1) jeder Zustand bekommt den Mindestkontrast (`ensureContrast`, 4,5:1, die Richtung folgt dem Design), das gewinnt immer; (2) zeigt das Design ein eingeschaltetes Werkzeug (`activated`) anders als ein ausgeschaltetes, geht das eingeschaltete
  auf der Seite seiner angehobenen Farbe weiter vom Grund weg (sein Kontrast wächst dabei nur), bis es nicht das schwächere ist und `MIN_STATE_STEP` (1,25:1) Abstand hat, höchstens bis Weiß/Schwarz; ist „an“ dort und noch nicht 1,25:1 von „aus“ entfernt (oder noch das schwächere), kommt „aus“ zum Grund hin zurück, höchstens bis 4,5:1 (sonst würden beide am Extrem gleich); (3) zuletzt `hard`: kein Zustand unter 3:1. Es gibt keine Seitenwahl aus den Originalfarben mehr (beides war die Quelle von Fehlern: ein eingeschaltetes Symbol konnte quer durch die Grundfarbe laufen und auf 1,1:1 fallen). Designs ohne Unterschied (alle eigenen Designs über `AllColors`) bleiben unverändert (Pixelvergleich der Owner-Bilder). Ein Symbol im Akzentkreis braucht 3:1 (weiß auf Rosa bleibt weiß). Absicherung: `BarIconVisibilityTest.theRuleHoldsOverAGridOfGroundsAndColours` (rund 6000 Kombinationen aus Grund, an und aus).
- **P488** Pillen im Raster (88c): Die Farben einer Pille werden gegen die **gezeichnete** Fläche gerechnet, nicht gegen `KEY_BACKGROUND`: Die Tastenfarbe eines Designs ist oft durchscheinend (beim Owner Weiß, ~47 %), `ensureContrast` kennt keinen Alphakanal. `PillColors.surface` legt sie über den Grund des Rasters (`MAIN_BACKGROUND`); Text, Symbol, gedrückt, „nicht verfügbar“ und „in der Leiste“ kommen aus `PillColors` (eine Stelle). Kein `View.alpha` zum Dimmen (außer `LIFTED_ALPHA` für die Pille des gehaltenen Werkzeugs): das nimmt auch dem Text die Deckkraft. Wer eine Farbregel für Text auf einer Fläche baut, rechnet mit dem Ergebnis der Mischung, nicht mit der Eingabe; der Pixeltest `GridPillsTest` misst gegen das Gezeichnete. Namen: `ToolGridView.fitText` (16 dp bis 10 dp, höchstens zwei Zeilen; die Auto-Größe des Systems greift neben einem Compound-Symbol nicht, und ohne Fenster läuft sie nie); die Zeilen des Rasters haben `isBaselineAligned = false`, sonst schiebt eine zweizeilige Pille sich aus der Zeile. Ein `Button(context, null, 0)` hat keine Schwerkraft „Mitte“: setzen. Im Test (ohne Fenster) lösen relative Compound-Symbole erst mit `layoutDirection` auf, und die Textgröße von `fitText` gilt erst im zweiten Layout (`GridPillsTest.show` macht beides).
- **P489** Kontrast nur gegen die tatsächlich sichtbare, deckende Fläche (88c, Prüferbefund): Eine Kontrastfunktion bekommt als Hintergrund nur ein `Seen` (agent-core `RgbLighting.kt`), gebaut mit `Seen.over(farbe, grund)` oder `Seen.of(farbe)`; `StripIconColors.ensureContrast`/`off`, `PillColors.*`, `RgbColors.readable`/`label` haben keinen `Int`-Hintergrund mehr (Test `SeenSurfaceTest`). Wer eine neue Farbregel baut, fragt zuerst: **was wird dahinter wirklich gemalt?** (der Streifen über dem Tastaturhintergrund, die deckende Fläche der Pille, ein Kreis, dessen Pinsel die Deckkraft ignoriert wie `CircleBadge`) und rechnet gegen das. `RgbColors.over(oben, unten)` legt einen durchscheinenden unteren Wert zuerst über Schwarz (vorher wurde seine Deckkraft verworfen, `StripIconColors.background` hielt eine durchscheinende `MAIN_BACKGROUND` für deckend). Das Raster malt seinen Grund deckend (`ToolGridView.build`), darauf stehen Pillen, Hinweis, Frage und die schwebende Kopie. Beim Ändern einer Beschriftungsfarbe auf dem Raster: Text auf der **Pille** gegen `PillColors`, Text auf dem **Grund** (Hinweis, Frage) gegen `Seen.of(Grund)`. Die Systemschrift-Größe wirkt auf die Pillen nicht (`fitText` setzt die Größe in Pixeln, 16 dp bis 10 dp): das ist so gewollt, ein Owner mit großer Systemschrift sieht dieselbe Größe. `fitText` und die Anzeige brechen nach derselben Regel um (`BREAK_STRATEGY_SIMPLE`, keine Silbentrennung, `layoutOf`): ändert man eins, das andere mit.
- **P410** Cursor als Trackpad (87), Hook im `PointerTracker`: `onCursorTrack` wird für jeden Move auf Leer- und Löschtaste gefragt, nach der
  Schnelltipp-Sperre. Ein Listener, der nichts weiß (Mock = 0, `Adapter`), ergibt `TRACK_OFF` und damit den alten Code
  Zeile für Zeile (`CursorGestureTrackerTest`, alter Fixture-Teil). Wer den Hook vor die Schnelltipp-Sperre zöge, ließe Tipps beim
  schnellen Tippen zu Wischen werden. Das Trackpad ändert `mStartX`/`mStartY` nie; `SpaceDetents.cancelsHold` rechnet weiter mit ihnen.
- **P411** Mehrere Finger (Korrektur nach der Prüfung; die erste Fassung hielt EINEN Zustand für alle Finger und setzte ihn bei jedem `onPressKey` zurück,
  das nahm einem laufenden Wisch den Besitzer und löschte still, siehe VALIDATION Etappe 87). `PointerTracker.processMotionEvent` gibt jedes
  `ACTION_MOVE` an jeden Tracker, auch an ruhende Finger, darum bekommt jeder Finger auf Leer-/Löschtaste Aufrufe mit seinem Abstand vom Aufsetzpunkt.
  Seitdem: Zustand je `pointerId`, ein Besitzer, `downTime` als Berührungskennung, `onCursorTrackEnd` vom Tracker. Ein zweiter Finger bekommt
  `TRACK_PENDING` (REFUSED), solange der erste die Spur besitzt: Sein Tipp auf Löschen löscht ein Zeichen, auf Leertaste schreibt ein Leerzeichen,
  Halten wiederholt (`onKeyRepeat` setzt `mKeySwipeAllowed` zurück, der Hook wird dann nicht mehr gefragt), aber er kann nicht wischen, also auch nicht
  das alte Löschwischen (vor Etappe 87 ging das parallel). Ein Druck oder eine Wiederholung (`onPressKey`, `repeatCount > 0`) setzt nichts zurück. Bleibt
  ein Besitz hängen (Ende nie gemeldet), endet er mit der nächsten Berührung desselben Fingers; bis dahin sind andere Finger Tippfinger.
- **P412** Ein Wisch beginnt erst bei 10 dp Abstand vom Aufsetzpunkt (`CLAIM_DP`, Löschen: seitlich). Davor ist es für den Tracker ein
  Tipp (Leerzeichen beim Heben). Der Listener sammelt die Proben bis dahin, liest den Text erst beim Beginn und spielt die Proben dann in den Kern;
  ein Tippen mit zitterndem Finger fragt den Editor nie.
- **P413** Textfenster: 200 Zeichen vor und hinter dem Cursor, einmal beim Beginn (`getTextAfterCursor` ist ein IPC). Danach liest der Kern nur
  neu (`needsText`), wenn der Cursor weniger als 24 Zeichen vom Rand eines gekürzten Fensters steht, höchstens alle 60 ms, und 100 ms
  nach einem senkrechten Schritt (der Editor braucht Zeit; bis dahin keine Wortgrenzen, Schritte ohne `chars`). Der Kern führt die Position
  selbst mit; setzt der Editor anders (Rand, Ablehnung), driftet sie bis zum nächsten Lesen. Geben Vor- und Nachtext beide nichts, gilt der
  Text als nicht lesbar: der alte Pfad schickt Pfeiltasten.
- **P414** Der Kern teilt Cluster nach `TextColumns.graphemes` (eigene Regeln), der alte Pfad (Rückfall ohne Text) nach `BreakIterator`/ICU. Beide
  stimmen für Akzente, Emoji-Folgen, Flaggen und Hautfarben überein (Test `steps are whole grapheme clusters`), nicht für alle Schriften (Hangul, Indisch).
- **P415** Magnetismus: Nur das Verlassen einer über einen Schritt erreichten Wortgrenze kostet mehr, nie die Startposition. Ein Leerzeichen
  zwischen Wörtern sind zwei Grenzen, ein Emoji neben einem Buchstaben eine. Tests, die Zeichenzahlen prüfen, setzen `CURSOR_MAGNETISM` auf 0 oder
  rechnen die 35 % ein (13 dp · 1,35 = 17,55 dp).
- **P416** Zeit: Der Kern rastert in 4-ms-Schritten ab `begin`; der letzte angebrochene Schritt (<4 ms) einer Geste wird nicht gerechnet
  (höchstens 1 Schritt bei sehr schnellem Finger). Eine Pause über 100 ms ohne Ereignis gilt als Ruhe bis 16 ms vor dem nächsten (sonst gäbe ein
  `input motionevent` aus adb einen Geschwindigkeitssprung). Zeiten sind `MotionEvent.getEventTime()` in ms.
- **P417** Mit Markierung kein Trackpad auf der Leertaste (der alte Weg setzt `Start + Schritte` und löst die Markierung auf). Die Entscheidung fällt
  beim ersten Aufruf jeder Berührung (`decideTrackMode`), nicht mitten im Wisch; ebenso die Prüfung der Belegung (`appliesToSpace`). Ein Wisch
  mit anderer Aktion (Sprache, Rückgängig, Touchpad-Modus …) bleibt unberührt.
- **P418** Löschwisch: Fenster = Text vor dem Auswahlanfang + markierter Text + Text dahinter; `forwardLimitChars` = Länge der Markierung (wächst mit
  jedem Schritt nach links); nach rechts nie hinter das Ende (zusätzlich die alte Prüfung `start > end`). Das Heben löscht über das bestehende
  `onUpWithDeletePointerActive`. Keine RTL-Umkehr wie früher. Der Schalter „Cursor als Trackpad“ gilt auch für das Löschwischen, unabhängig von der Leertaste.
- **P419** Tests: `setText` startet das Feld neu und setzt den Subtyp zurück (ein RTL-Test wechselt danach); `InputLogicTest` kennt im Schatten
  des Editors `keyDownCodes` (Android-Tastencodes der gesendeten Tasten, für DPAD). Die Robolectric-Dichte ist 1: dp = px. Die Gerätetests
  `cursor_trackpad` und `cursor_trackpad_vertical` sind geschrieben, nicht gelaufen (`Driver.drag_on`: erster Schritt 16 dp sofort nach dem Aufsetzen, damit die Geste vor dem Langdruck der Leertaste (450 ms) beginnt, dann 4-dp-Schritte, je `input motionevent` 100–300 ms).
- **P420** Senkrechte Bewegung nur in mehrzeiligen Feldern (`isMultiLineField`: `inputType`-Klasse Text mit `TYPE_TEXT_FLAG_MULTI_LINE`). `IME_MULTI_LINE`
  zählt nicht (Browser melden es für jedes Feld), ein unbekannter Typ auch nicht. In einem einzeiligen Feld würde `DPAD_DOWN` aus dem Feld springen,
  Vorschläge der Adressleiste durchlaufen oder in Termux den Verlauf rufen. Dort läuft die nur waagerechte `flatTrack`; ein senkrechter Zug ist noch
  ein Wisch (kein Leerzeichen beim Heben, wie früher bei einem senkrechten Wisch ohne Aktion), bewegt aber nichts.
- **P421** Nach einem Pfeil schickt der Editor die neue Cursorstelle später als `onUpdateSelection`. Bis dahin hält der Kern waagerechte Schritte zurück
  (er trägt den Weg) und der Listener liest den Text nicht neu (`awaitSelection`: bis `expectedSelectionStart` sich ändert oder 300 ms); am ersten Zeilenanfang
  oder -ende ändert ein Pfeil nichts, dann gilt die Wartezeit voll. Ein Editor, der länger als 300 ms braucht, bekommt Schritte auf der alten Stelle.
- **P422** Textfenster: Ein Editor darf weniger zurückgeben als gefragt. Ein Fensterrand ist nur dann Textanfang bzw. -ende (`atStart`/`atEnd`), wenn die Antwort
  leer ist oder eine Nachfrage nach einem weiteren Zeichen nichts Neues bringt; sonst ist er ein Rand ohne Wortgrenze und ohne Anschlag, der Kern
  fragt dort neu (`needsText`, Rand 24 Zeichen, höchstens alle 60 ms). Ohne die Nachfrage würde jeder Cursor in den ersten 24 Zeichen eines kurzen
  Textes alle 60 ms lesen.
- **P423** Die Schwelle zum Wisch ist die alte: 10 dp auf der Wischachse (`CursorTrackpad.claims`), nicht die Länge des Weges. 7 dp zittern in beide Richtungen sind ein Tipp
  (Leerzeichen, Doppel-Leerzeichen-Punkt funktioniert); ein Wisch, der die Spur bekam, bekommt nie wieder `TRACK_OFF` (Einstellung oder Markierung
  mitten in der Geste ändern nichts, erst die nächste Berührung).
- **P424** Gerätetest-Szenarien (nicht gelaufen): Der erste `MOVE` von `drag_on` folgt dem `DOWN` sofort und ist 16 dp lang; sonst käme der Wisch nach 300–900 ms
  und der Langdruck der Leertaste (450 ms) käme ihm zuvor. Weil die Spur an der ersten angekommenen Probe beginnt (Weg 0), zählt der 16-dp-Sprung nicht mit: der senkrechte Zug geht darum 70 dp weit (54 dp Weg, zwei Zeilen); mit 40 dp (erste Fassung) bewegte sich der Cursor auf dem Emulator nicht (Lauf 44746919230).
- **P425** Ein verweigerter Finger, der wischt (`TRACK_REFUSED_DRAG`), verwirft seine Berührung. Ein ruhender oder tippender verweigerter Finger bleibt, was er war.
  Ohne die Verwerfung läuft die Wiederholungs-Zeitschaltuhr einer Löschtaste weiter (400 ms nach dem Aufsetzen, dann alle 50 ms ein Zeichen), weil `onKeySwipe` vor dem
  alten Code zurückkehrt und `cancelKeyTimersOf` nie erreicht. Auch das Heben tippt dann nichts (`mIsTrackingForActionDisabled`). Die Berührung ist verworfen, aber die Taste bleibt bis zum Heben als gedrückt gezeichnet, und dieses Heben ruft im Tracker `releaseAllPointersOlderThan`: ein älterer Besitzer endet dann (bestehendes HeliBoard-Verhalten; ein Löschwischen löscht dabei seine Markierung).
- **P426** Pfeile gibt es nur einzeln und nur mit bekannter Stelle: Ein Pfeil nach oben am ersten und einer nach unten am letzten Feldende wird vom Editor nicht
  behandelt, Android bewegt dann den FOKUS in ein Nachbarfeld (Mailtext → Betreff). Der verlässliche Randmelder ist „der Pfeil bewegte nichts“
  (umbrochene Zeilen sieht die Tastatur nicht); die Vorprüfungen (Cursor 0, kein Text dahinter) sparen nur den ersten Fehlversuch. Grenze: ein Editor, der
  nach 300 ms noch nicht geantwortet hat und dessen Stelle nicht lesbar ist, gilt als „Rand“; dort gibt es bis zum Ende der Berührung keinen Pfeil in diese Richtung.
- **P427** Besitz über Löschwischen: Ein Löschwischen ist ein Besitzer wie die Leertaste. Solange es läuft, werden Leertasten-Wische anderer Finger verweigert (früher
  lief der alte Leertasten-Wisch neben der Auswahl und löste sie auf). Mit ausgeschaltetem Schalter ist alles `OFF`, wie vor Etappe 87.
- **P428** Ein Wisch während `ACTION_CANCEL` (Fenster, Systemgeste): Für die Leertaste bleibt der Cursor, wo er ist, nichts wird gelöscht und kein Leerzeichen getippt;
  beim Löschwischen löscht der Abbruch das Markierte (der Tracker behandelt ihn als Heben, genau wie der alte Code).
- **P429** Prüfstand: Ereigniszeiten wachsen über alle `TouchRig` hinweg (`rigClock`), weil der Tipp-Zeitschreiber des Trackers statisch ist: ein Prüfstand, der in der Zeit zurückspringt, gälte als schnelles Tippen und die Wisch-Geste würde verschluckt (nur in der vollen Klasse sichtbar, GitHub). Der Langdruck der Leertaste öffnet die Eingabemethodenwahl, die ein Fenster-Token braucht: Tests, die ihn auslösen, schalten `PREF_SPACE_TO_CHANGE_LANG` aus. `FakeTimer` im Prüfstand: Die gestylten Attribute der Tests tragen keine Wiederholzeiten (0), der Prüfstand nimmt dann 400 ms Anlauf und 50 ms Takt. Die
  Zeitschaltuhren laufen in der Ereigniszeit des Prüfstands, nicht in der Uhr des Telefons; `SystemClock` im Tracker (Schnelltipp-Sperre) bleibt echte Zeit.
- **P430** Pfeil unterwegs (Dritte Nachprüfung): Nach jedem gesendeten Pfeil vergisst der Kern seinen Text (`CursorTrack.holdForEditor`) und hält waagerechte Schritte zurück, bis der Editor geantwortet hat und der Listener den Text neu las; sonst setzt ein waagerechter Schritt von der alten Stelle den Pfeil außer Kraft (eine Zeile geht verloren) und `resolveArrow` hielte das eigene `setSelection` für die Antwort des Editors. Ein Schritt, der doch bei laufendem Pfeil ankäme, lässt die Spur neu beginnen. Prüfung im Test: zwischen einem Pfeil (`sendKeyEvent`) und der Antwort darf kein `setSelection` des Schatten-Editors liegen.
- **P431** Feldstart und Verbergen (`LatinIME.onStartInputViewInternal`, `onFinishInputViewInternal`, je eine Zeile `onCursorTrackEnd(-1)`): gibt jeden Besitz frei; eine aktive Berührung bleibt „aktiv“, bewegt aber nichts mehr (der Tracker hält sie für einen Wisch bis zum Heben); eine Berührung ohne Heben oder Abbruch hinterlässt so keinen Besitzer, der Wische verweigert oder Leertasten-Tipps mit 10 dp Zittern verschluckt.
- **P432** Löschwischen: Die Länge der Markierung kommt aus der erwarteten Auswahl (`expectedSelectionEnd - expectedSelectionStart`), nicht aus `getSelectedText` (ein Editor darf null antworten); fehlt der markierte Text, gilt der Text als nicht lesbar (Schritte über den alten Pfad `onMoveDeletePointer`), die Vorwärtsgrenze bleibt die Markierungslänge.
- **P433** Löschen beim Heben nur im selben Feld: `deleteSlideField` hält das Feld (`mInputAttributes`), in dem das Löschwischen begann (Spur und alter Pfad `onMoveDeletePointer`); `onUpWithDeletePointerActive` löscht nichts, wenn es ein anderes ist (eine Adressleiste, die beim Fokus alles markiert). Ohne Eingriff in `PointerTracker` oder `InputLogic`; `ACTION_CANCEL` im selben Feld löscht das Markierte weiterhin.
- **P460** Start entscheidet (Etappe 87c): Ist mindestens eine Richtung der Leertaste „Cursor bewegen“ und eine andere mit Aktion belegt (`QUALIFIES_START_DECIDES`), bleibt die Berührung `PENDING`, bis `CursorTrackpad.decideStart` entscheidet, und zwar **bei der ersten Meldung, die 10 dp auf einer Achse erreicht, nach dem Vergleich, mit dem `PointerTracker.onKeySwipe` seitlich von senkrecht trennt** (senkrecht, wenn |dy| ≥ 10 dp und |dx| < |dy|, sonst seitlich, Gleichstand seitlich; dann das Vorzeichen). Die Schwelle ist in px der Wert des Trackers selbst, `PointerTracker.getPointerStep()` (floor(10 · Dichte) beim Laden der Klasse; N-1: ein Vergleich in dp entschied bis zu 1 px später als der alte Wisch; N-2: eine eigene Formel über die aktuelle Dichte liefe nach einer Änderung der Anzeigegröße auseinander, weil `sPointerStep` `static final` ist). Es gibt keine Wartezone: `PENDING` dauert nie länger, als der alte Code die Berührung ein Tipp oder Halten sein ließ (Prüferbefund M-1: eine Wartezone ließ „Alles auswählen“ feuern, wo der alte Wisch schon begonnen hatte, und ein Loslassen dort schrieb ein Leerzeichen). Die Entscheidung kommt vor dem Besitz-Check: Besitzt ein anderer Finger die Spur, ist auch ein Wisch, der eine Aktion auslösen würde, `REFUSED` (L-1). Belegung der Startrichtung: „Cursor bewegen“ oder nichts = Trackpad (alle vier Richtungen für den Rest der Berührung), eine Aktion = `TRACK_OFF` für den Rest der Berührung (der alte Code rechnet immer vom Aufsetzpunkt aus). Ausnahme: Start nach oben/unten mit „nichts“ in einem nicht mehrzeiligen Feld ist `LEGACY` (nichts soll geschehen). `OFF` ist nur VOR dem Anspruch möglich; wer die Spur hat, bekommt nie wieder `TRACK_OFF`. `onCursorTrackEnd(-1)` (neues Feld, Tastatur weg) lässt `OFF`-Finger `OFF`: ein als Aktion begonnener Wisch wird im neuen Feld nicht zum Trackpad (L-2). Sind alle Richtungen frei (`QUALIFIES_ALL_AXES`), gilt wie in Etappe 87 der Anspruch auf jeder Achse bei 10 dp (`claims`).
- **P461** `PENDING` → `OFF` gegen den alten Code geprüft: `PointerTracker.onKeySwipe` hält vor dem Haken nur die Schnelltipp-Sperre und `SpaceDetents.cancelsHold` (beide laufen weiter) und rechnet danach aus `mStartX`/`mStartY` neu; kein Zustand wird in der PENDING-Zeit vorweggenommen. Einmal-Aktionen oben/unten laufen bei der ersten Meldung (`onSpaceSwipe(Richtung, Schritte)`), seitliche mit Rastpunkten zählen den Abstand ab Aufsetzpunkt (`onSpaceSwipeDistance`), fortlaufende (Sprache, Touchpad-Modus) bekommen die Schritte ab Aufsetzpunkt (`mStartX`/`mStartY` rücken nur bei `true` vor). Fixture `bindings87c*` in `InputLogicTest` (Aufruffolge des echten Listeners, zwölf Tests, auf 31d0787 und nach 87c gleich, darunter Ruhe und Loslassen in der Diagonalzone); Sprachwechsel nur in den ersten drei Meldungen verglichen (die Folge danach hängt von den aktivierten Sprachen ab).
- **P462** Markierung (Etappe 87c): kein Ausschlussgrund mehr. Beim Anspruch zieht `collapseSelection` die Markierung zusammen (`CursorTrackpad.collapsesToStart`: Start links/oben, Ende rechts/unten, in Rechts-nach-links-Sprachen links = Ende), `setExpectCursorMove`, `finishInput`, `checkpointFieldCorrectionsBeforeOwnCursorMove` und `setSelection(p, p)` (wie `applyHorizontalMove`, L-3), danach beginnt die Spur an der Stelle; nichts wird getippt oder gelöscht. Das Löschwischen bleibt unverändert und die Leertasten-Haltegeste feuert wie vorher (der Anspruch bricht sie ab). Der Editor muss die Auswahl der Tastatur gemeldet haben (`hasSelection` nutzt die erwartete Auswahl): Tests rufen nach „Alles auswählen“ `onUpdateSelection` selbst auf.
- **P463** Warum Etappe 87 beim Owner nie anlief und kein Test es sah: Seine Belegung war links/rechts „Cursor bewegen“, oben/unten mit anderen Aktionen; die Prüfung verlangte oben/unten frei. Alle Tests der Etappe 87 benutzten die Vorgabe oder „Cursor bewegen“ für oben/unten.
- **P464** Prüfstand: Der Rig-Start liegt hinter `SystemClock.uptimeMillis()` plus 10 s, damit Tipp-Zeiten früherer Tests im statischen Tipp-Zeitschreiber die Wisch-Meldungen nicht verschlucken; die Meldungsfolgen der Fixture sind ohne Zählung gleicher Folgemeldungen verglichen (ihre Zahl hängt von der Ereignisanzahl ab).
- **P465** Einstellungszeile (`AgentAreaGesturesScreen`, Karte „Treffen & Wischen“): Der Erklärtext unter dem Schalter ist seit 87b in allen Fällen derselbe (`agent_gestures_trackpad_summary`; `agent_gestures_trackpad_summary_none` entfiel, den Hinweis „lege eine Richtung auf Cursor bewegen“ trägt die Statuszeile von 87b bei `NOT_NO_CURSOR_DIRECTION`). Die Diagnose-Karte und die Statuszeilen von 87b benutzen dieselbe reine Funktion (P453).
- **P466** Sperre durch eine alte Aktion (Nachprüfung 87c): `TrackState.runsAction` (nur bei `LEGACY` über die Startrichtung mit einer anderen Aktion als „nichts“, nur solange `OFF` und der Finger unten) lässt `otherFingerRunsAnAction` einen anderen Finger auf der Leertaste, der das Trackpad beanspruchen würde, `REFUSED` machen. Ende: `onCursorTrackEnd(Finger)` oder `-1`; wer `-1` hört, bleibt `OFF` ohne Sperre. Zwei alte Aktionen nebeneinander bleiben erlaubt; Schalter aus und gesperrte Eingabe setzen die Sperre nie. Der Tracker bleibt unberührt.
- **P467** Dichte im Test: `PointerTracker.sPointerStep` ist `static final` und wird beim Laden der Klasse gesetzt (Robolectric: Dichte 1,0, 10 px). Der Listener liest ihn über `PointerTracker.getPointerStep()` (Etappe 87c, N-2). Die Tests ändern die Dichte NACH dem Laden (`RuntimeEnvironment.setQualifiers("+168dpi")`, `+420dpi` = 2,625, `+480dpi` = 3,0) und prüfen am echten Tracker, dass der Listener bei 10 px entscheidet, wie der Tracker, nicht bei floor(10 · neue Dichte) (26 bzw. 30 px); `10.dpToPx(Resources.getSystem())` zeigt in `cursor87cTheTrackersStepComesFromTheTrackerAndDoesNotFollowALaterDensityChange` das Auseinanderlaufen. Echte Startdichten wie 2,625 prüft nur das reine Gitter (Entscheidungsfunktion mit px-Schwelle).

- **P490** Entprellung (89): `SuggestionStripView.setSuggestions` hält bei einer leeren Liste in der Sitzung die Wörter 250 ms (`staleWords`). Das Zeitgeber-Objekt ist ein `Handler` auf dem Hauptthread, nicht `View.postDelayed` (die wartet auf ein Fenster und läuft ohne nie, in Tests und bei versteckter Ansicht). Wer einen neuen Weg einbaut, der die Leiste mit Inhalt füllt (`setExternalSuggestionView` oder Ähnliches), ruft `cancelHeldWords()`, sonst räumt die Frist den neuen Inhalt weg.
- **P491** Veraltete Wörter (89): In der Frist sind die Wörter nicht antippbar (`onClick`, `onLongClick`, Wischen nach oben prüfen `staleWords`; gezählt in `BarCalmCounters.droppedTaps`). Eine neue Berührungsstelle für Wörter braucht dieselbe Prüfung, sonst setzt ein Tipp ein Wort ein, das zu einem älteren Text gehört.
- **P492** Sitzung am Tastencode (89): `writing` entsteht nur im Listener (`barSessionKey`, Batch-Start, Löschwisch), nie aus der Art der Wörterliste (die Wiederaufnahme liefert `INPUT_STYLE_TYPING`). Ein neuer Weg, der Text eintippt (nicht einfügt), ruft `LatinIME.onAgentTyped()`. Einfügen, Chips und Diktat setzen die Sitzung nicht.
- **P493** `pinned` (89): Wörter, Pille, Autofill, Update-Chip und neutrale Aufrufe ändern die auf Wunsch gezeigten Werkzeuge nicht, Auswahl-Chips schon. Tests, die „Typing“ mit `strip.setSuggestions(…)` nachstellen, rufen vorher `strip.onTyped()` (`InputLogicTest` Auswahl-Werkzeuge). `reportToLastField` meldet `show = CONTENT` ohne Inhalt als „leer“ (P384).
- **P494** Leeres Feld (89): Nur „Cursor am Anfang und nichts davor“ (`getCodePointBeforeCursor() == NOT_A_CODE`), und nur gelesen, solange die Sitzung läuft (der Wert kommt aus dem Zwischenspeicher, keine Leseanfrage an die App). Pos1 in einem gefüllten Feld zeigt damit die Werkzeuge. `hasTextAfterCursor` wäre eine Leseanfrage und gehört nicht in den Tastendruck (Etappe 84).
- **P495** Diagnose „Leiste“ (89): `BarCalmCounters` zählt nichts in Passwortfeld, Inkognito und gesperrtem Gerät (`onShown`, `onDelete`, `onFieldStart` bekommen `protectedField`); die Beschriftungen der Zeilen enthalten kein „: “ (P454). Die Messung im Test (`StripCalmLogTest.visible`) zählt jeden sichtbaren Wechsel einschließlich „leer“; Werkzeuge gegen Inhalt allein sähe „Wörter, leer, Wörter“ nicht.

- **P496** Frist ab dem letzten Zeichen (89, Prüferbefund F1): `SuggestionStripView.onTyped` startet `releaseWords` neu, solange `staleWords` gilt. Wer einen weiteren Weg einbaut, der Zeichen schreibt, ruft `onAgentTyped`; sonst blinkt die Mitte zwischen zwei Wörtern leer. Eine Antwort, die später kommt als `CALM_DEBOUNCE_MS`, bleibt als kurze leere Phase sichtbar (Test `aResumeThatAnswersAfterTheDebounceShowsAShortEmptyBlink`).
- **P497** „Feld leer“ (89, F2/F5): `LatinIME.isAgentFieldEmpty()` verlangt erwartete Cursorposition 0 und nichts davor; unlesbarer Text (-1, leerer Zwischenspeicher) zählt nicht. Er wird auf dem neutralen und auf dem Weg mit Inhalt geprüft (`setNeutralSuggestionStrip`, `setSuggestedWords`) und nur, solange eine Sitzung läuft.
- **P498** Zähler und Feldstart (89, F4): `BarCalmCounters.onFieldStart` wird in `onStartInputViewInternal` nach dem Laden der Einstellungen gerufen (Passwort, Inkognito, gesperrt: nichts), nicht in `onStartInput`; ein neues Feld setzt `last` zurück.
- **P505** Kreis neben den Wörtern (99): Der Kreis neben Wörtern und Chips ist ein **zweiter Knopf** (`SuggestionStripView.endCircle`, Id `agent_bar_end_circle`) am Ende von `suggestions_strip_wrapper`, nicht der Platz der Zeile. Er hat keine Id `agent_bar_tool`, zählt nicht als Platz (`FixedBar.slots`, Ziehen, `slotCentres`) und wird vom Gerätetest nicht als Werkzeug gelesen (`bar_slots` schließt `END_CIRCLE_ID` aus). Wer Größe, Rand oder Farben des letzten Platzes ändert, ändert sie in `FixedBar.styleSlot` (gilt für beide); wer die Sichtbarkeit eines Teils der Leiste setzt, setzt sie in `applyBarState` (P380). Nur in Feldern mit `BarState.calm`; neben Chips erst ab `CHIPS_WITH_CIRCLE_MIN_DP` (340).
- **P506** Pause der Schreibsitzung (99): `writingPause` hängt nur an Tasten (`onTyped`), nie am Inhalt der Mitte; das war die Quelle des „mal so, mal so“ in 89b. Wer einen Weg einbaut, der in der Sitzung Text tippt, ruft `onAgentTyped` (P492), sonst kommen mitten im Schreiben die Werkzeuge. Tests, die länger als 4 s ohne Taste warten (`idleFor`), sehen danach die Werkzeuge.
- **P507** Zwischenablage ohne Werkzeugzeile (106): Versteckt der Standardstil den Streifen (`ClipboardHistoryView.hidesStrip`), muss die Ansicht genau seine Höhe (`config_suggestions_strip_height`) zusätzlich messen und der Liste geben, sonst springt die Tastaturhöhe beim Öffnen der Zwischenablage. `setMainKeyboardFrame` zeigt den Streifen beim Zurückwechseln wieder. Der flache Stil behält den Streifen (Zurück-Knopf); ohne Streifen insgesamt (Modus versteckt) gilt die alte Rechnung. Die Tasten werden im Konstruktor gelesen: der Schalter ruft `setThemeNeedsReload`.
- **P508** Gezeichnete Symbole, Antwort des Hilfs-Threads (100): `SymbolInk.onFinished(serial)` schreibt nur, wenn `serial` noch der
  Stand ist, für den `SymbolDrawing.checkFinished` geprüft hat. Jede Änderung an der Gruppe, an `inkPointer` oder an einem Weg des
  Schreibens (neuer Strich, `onBatchStart`, `finish`, `takeWaiting`, `cancel`, `add`) muss `serial` erhöhen; sonst schreibt eine
  späte Antwort eine Zeichnung, die inzwischen weitergeht (Früheingabe), oder ein zweites Mal. Der Timer der Pause bleibt immer
  gesetzt: ohne Antwort schreibt die Pause wie vor 100. Wer eine neue Eingabe einbaut, die keine Taste ist, ruft
  `SymbolInk.onTypedInput`, sonst zählt eine Zeichnung danach als Nachzügler.
- **P499** Langlebige Ansicht, kurzlebige Einstellung (Etappe 95): Was eine View einmal im Konstruktor aus `SettingsValues` liest, bleibt für ihre ganze Lebenszeit. `SettingsValues` enthält Zustände des Augenblicks (Sperre → `mToolbarMode` HIDDEN, also `mFixedBar` falsch). `SuggestionStripView.fixedBar` liest deshalb die Einstellung selbst (`Settings.readToolbarMode`, `AgentPreferences.FIXED_BAR`); im Sperrzustand gebaut kam sonst nach dem Entsperren die alte Leiste zurück.

## Datei-Inventar

> Erzeugte Liste aller kartierten Dateien nach Ordner.

<!-- gen:inventory -->
<details><summary>Alle kartierten Dateien nach Ordner (796 files; generated, do not edit)</summary>
<details><summary>Alle kartierten Dateien nach Ordner (799 files; generated, do not edit)</summary>

- `.claude/` (1): settings
- `.claude/hooks/` (1): session-start
- `.claude/skills/product-initiative/` (1): SKILL
- `.claude/skills/product-initiative/evals/` (1): evals
- `.claude/skills/product-initiative/evals/files/` (4): dark-toolbar, dialog-zustaende,
  toolbar-profil, trackpad-profil
- `.github/workflows/` (6): agent-apk, agent-tests, build-debug-apk, build-test-auto, device-tests,
  docs-check
- `/` (1): settings
- `A/` (106): Abbreviations, ActionRunJournal, AgentDocumentIO, AgentEditorController,
  AgentFileProvider, AgentKeyboardFrame, AgentPanelView, AgentPreferences, AgentStorageContexts,
  AgentWheelView, AndroidEditorAdapter, ApprovedActionRunner, BarCalmCounters, CalculatorView,
  CharacterCommand, ClipVault, ClipVaultSection, ContactCommand, ContextLearning, ConvertCommand,
  CorrectionGuards, CorrectionMemory, CurrentWordShown, CursorTrackDiag, CursorTrackpad,
  DateCommand, DebugModeReset, DesignLook, DesignTiles, Diagnose, Dictation, EmojiCommand,
  EmojiMixPalette, EmojiSuggestions, EncryptedActionRunStore, EventCommand, FieldCorrections,
  FieldSuggestionChips, FlatMenuView, FlatPanelStyle, FlatPreset, ForcedApps, ForeignWords,
  FormatGuardChips, GermanVerbLemmas, HapticChain, InformalWordsStore, InlineAutofill,
  JournaledModelCall, JournaledRun, KeyboardCommands, KeyboardProfileStore, LastField, LearningData,
  LoadingDotsView, LockState, ManualWorkflowDraft, Memory, MemoryChips, ModelSettings, MyData,
  MyDesigns, OnDeviceSpeechEngine, OtherLanguageDictionaries, PillColors, PlaceCommand, PromptStore,
  RgbKeyPainter, RgbLighting, RgbOwnPatterns, RgbStrip, RgbTexture, RuleClassCleanup,
  RuleCorrections, RunSubjectCodec, SavedPlaces, ScreenshotChip, SearchCommand, SelectionChips,
  SettingsLauncher, SkillBindingStore, SkillLabels, SliderFeel, SliderHaptics, SmartPasteChips,
  SpaceDetents, SpaceGestures, SubtypeSwitchLog, SymbolDrawing, SymbolInk, ToolApprovalSummary,
  RgbKeyPainter, RgbLighting, RgbOwnPatterns, RgbStrip, RgbTexture, RuleCorrections,
  RunSubjectCodec, SavedPlaces, ScreenshotChip, SearchCommand, SelectionChips, SettingsLauncher,
  SkillBindingStore, SkillLabels, SliderFeel, SliderHaptics, SmartPasteChips, SpaceDetents,
  SpaceGestures, SubtypeSwitchLog, SymbolDrawing, SymbolInk, SymbolPerf, ToolApprovalSummary,
  ToolGridView, ToolLauncher, TouchCalibration, TouchModel, TypedWordFirst, TypingPerf, TypoMemory,
  UndoOffer, WordBeforeDelete, WorkflowApprovalSummary, WorkflowBuilderContract,
  WorkflowConditionSummary, WorkflowDraftRunner, WorkflowInputSummary, WorkflowStore
- `A/charart/` (5): CharArtCanvas, CharArtImages, CharArtKey, CharArtKeys, CharArtView
- `A/commands/` (22): AiCommandChips, AlarmCommand, AreaCommands, AskCommand, ClipCommand,
  CommandAppSettings, CommandSystem, EmojiMixCommand, EmojiMixRecents, EmojiMixer, InstalledApps,
  LanguageCommand, QrCommand, ScreenReaderService, SensitiveClips, SnipCommand, SnippetStore,
  SubtypeChoice, TimerCommand, TranslateCommand, UrlCommand, WorkflowCommand
- `A/prompts/` (2): PromptsKey, PromptsView
- `A/providers/` (5): AnthropicProvider, CloudProviders, CloudTransport, GeminiProvider,
  OpenAiProvider
- `A/security/` (2): EncryptedCredentialStore, SecretCipher
- `A/updates/` (7): GitHubUpdateClient, UpdateCheckJob, UpdateInstaller, UpdateOnOpen,
  UpdatePrompts, UpdateSettings, Updates
- `AT/` (135): AgentDocumentIOTest, AgentPanelViewTest, AgentSliderTest, AgentSpaceGestureTest,
  AgentStorageContextsTest, AgentWheelViewTest, AndroidEditorAdapterTest, ApprovedActionRunnerTest,
  ApprovedWorkflowRunnerTest, AskCommandTest, BarCalmCountersTest, BarEditModeTest,
  BarIconVisibilityTest, BarToolsTest, BundledEmojiDictTest, CalculatorViewTest,
  CalendarToolLauncherTest, CharArtWindowTest, ClipCachesTest, ClipCommandTest, ClipVaultTest,
  CommandReplaceTest, CommandTemplatesTest, ContactCommandTest, CorrectionGuardsTest,
  CorrectionMemoryTest, CurrentWordShownTest, CursorGestureTrackerTest, CursorTrackDiagTest,
  CursorTrackpadReasonTest, DatePlaceCommandsTest, DebugPrivacyTest, DesignEditorTest,
  DesignTilesTest, DiagnoseCorrectionTest, DiagnoseScreenTest, DictationSessionTest,
  DictationUiTest, EmojiMixPaletteTest, EmojiMixRecentsTest, EncryptedActionRunStoreTest,
  EncryptedCredentialStoreTest, FieldCorrectionMemoryTest, FieldSuggestionChipsTest, FixedBarTest,
  FlatClipboardTest, FlatMenuTest, FlatPanelTest, FlatStyleRenderTest, FlatStyleTest,
  ForkIdentityTest, ForkUpgradeTest, FormatGuardChipsTest, GridPillsTest, HapticChainTest,
  HardeningClipHistoryTest, InformalWordsStoreTest, InlineAutofillCardTest,
  InlineAutofillGenerationTest, InlineAutofillResponseTest, InlineAutofillSizingTest,
  InlineAutofillSlotsTest, InsertAtCursorTest, JournaledModelCallTest, KeyNodePositionTest,
  KeyboardCommandsTest, KeyboardProfileStoreTest, LastFieldTest, LearningDataTest, LiveLockGateTest,
  LoadingDotsViewTest, LongTextBudgetTest, ManualWorkflowDraftTest, MemoryScreenTest,
  MoreCommandsTest, MoreProvidersTest, MyDataScreenTest, MyDataStoreTest, MyDesignsTest,
  OpenAiProviderTest, PromptStoreTest, PromptsScreenTest, PromptsWindowTest, RgbFrameRateCardTest,
  RgbLightingRenderTest, RgbPatternEditorTest, RgbSettingsScreenTest, RgbTilesTest,
  ScreenshotChipTest, SecureWindowTest, SeenSurfaceTest, SelectedTextCommitTest, SettingsAreaAiTest,
  SettingsAreaGesturesTest, SettingsBarAreaTest, SettingsKeysScreenTest, SettingsKitTest,
  SettingsLookScreenTest, SettingsSearchTest, SettingsStartScreenTest, SettingsSystemAreaTest,
  SettingsTypingAreaTest, SkillBindingStoreTest, SkillSettingsTest, SliderFeelTest,
  SmartPasteChipsTest, SnipCommandTest, SpaceDetentsTest, SpaceGesturesTest, StripRequestsTest,
  SymbolInkTest, SymbolPagesTest, SymbolSettingsScreenTest, TestRunJournal, ToolRunnerTest,
  TouchModelTest, TouchScenesScreenTest, TouchZonesDynamicTest, TypedWordFirstTest, TypingPerfTest,
  TypoMemoryTest, UndoOfferHooksTest, UndoOfferTest, UpdateCheckTest, UpdateChipTest,
  UpdateOnOpenTest, UpdatePromptsTest, WordBeforeDeleteTest, WorkflowBuilderContractTest,
  WorkflowBuilderSettingsTest, WorkflowCommandTest, WorkflowConditionSummaryTest,
  WorkflowDraftRunnerTest, WorkflowSettingsTest, WorkflowStoreTest
- `AT/commands/` (10): ClockCommandsTest, CommandSystemTest, EmojiMixCommandTest,
  LanguageCommandTest, LongCommandTest, QrCommandTest, ScreenReaderServiceTest, SubtypeChoiceTest,
  TranslateCommandTest, UrlCommandTest
- `C/` (64): AiTextAction, AlarmParser, ApplyOutcome, CivilDate, CommandGrammar, Commands,
  CorrectionRules, CorrectionWeights, CursorTrack, DateFormatting, EditorSession, EmojiKeywords,
  EmojiMix, EmojiNames, EmojiSynonyms, EnglishI, EventParser, ExecutionPolicy, FieldReport,
  ForcedCorrection, GermanShortForms, GermanVerbAgreement, GermanVerbForms, InformalWords,
  InkCompletion, InkDots, InkRecognizer, InkSymbols, InkTiming, LanguageNames, LocalTextActions,
  MathExpression, ModelBudget, ModelProvider, NeighborKeys, OtherLanguageWord, OwnInkSymbols,
  PendingBatchGate, PersonalInkSymbols, Places, Profiles, RareWordWeights, ReleaseNotes, RgbCurve,
  RgbLighting, RgbOwnPattern, RgbPatternJson, RgbPatterns, SentenceLanguage, SkillBinding,
  SnippetTemplate, SpecialCharacters, StyledTranslation, SwappedLetters, SymbolTable, TextCondition,
  TimerParser, Tool, TouchDynamics, UnitConversion, UpdateOnOpenPolicy, UpdateReleases, UrlDetector,
  Workflow
- `C/charart/` (6): BigFont, CharArt, Raster, Sketch, TextColumns, UnicodeStyle
- `C/clip/` (6): ClipDates, ClipGuard, LinkCleaner, PrivateRanges, SmartPaste, TrackingNumbers
- `C/format/` (5): DecimalHint, EmailCheck, FormatGuard, IbanCheck, PhoneFormat
- `C/memory/` (4): OwnWords, PhraseMemory, ShortcutOffers, SnippetMatcher
- `C/suggest/` (1): ContextSuggest
- `CT/` (63): AiTextActionTest, AlarmParserTest, CommandGrammarTest, CommandSyntaxTest,
  CorrectionRulesTest, CorrectionWeightsTest, CursorTrackTest, DateFormattingTest, EditorPolicyTest,
  EmojiKeywordsTest, EmojiMixTest, EnglishITest, EventParserTest, FieldReportTest,
  ForcedCorrectionTest, GermanShortFormsTest, GermanVerbAgreementTest, GermanVerbFormsTest,
  InformalWordsTest, InkAreaTest, InkBenchmarkTest, InkCompletionTest, InkDotFlowTest, InkDotsTest,
  InkFingerModel, InkLogicTest, InkRecognizerTest, InkTimingTest, InsertionAnchorTest,
  LanguageNamesTest, LocalTextActionsTest, MathExpressionTest, ModelBudgetTest, NeighborKeysTest,
  OtherLanguageWordTest, OwnInkSymbolsTest, PendingBatchGateTest, PersonalInkSymbolsTest,
  PlacesTest, QuestionCaptureTest, RareWordWeightsTest, ReleaseNotesTest, RgbCurveTest,
  RgbFrameStatsTest, RgbLightingTest, RgbOwnPatternTest, RgbPatternJsonTest, RgbPatternsTest,
  SentenceLanguageTest, SkillBindingTest, SnippetTemplateTest, StyledTranslationTest,
  SwappedLettersTest, SymbolTableTest, TimerParserTest, ToolTest, TouchDynamicsTest,
  UnitConversionTest, UpdateOnOpenPolicyTest, UpdateReleasesTest, UrlDetectorTest,
  WorkflowContractTest, WorkflowTest
- `CT/charart/` (2): CharArtTest, SketchTest
- `CT/clip/` (3): ClipGuardTest, PrivateRangesTest, SmartPasteTest
- `CT/format/` (1): FormatGuardTest
- `CT/memory/` (2): MemoryRulesTest, PhraseMemoryTest
- `CT/suggest/` (1): ContextSuggestTest
- `K/accessibility/` (2): KeyCodeDescriptionMapper, KeyboardAccessibilityNodeProvider
- `K/keyboard/` (10): FlatStyle, Key, KeyDetector, KeyboardActionListener,
  KeyboardActionListenerImpl, KeyboardSwitcher, KeyboardTheme, KeyboardView, MainKeyboardView,
  PointerTracker
- `K/keyboard/clipboard/` (3): ClipboardAdapter, ClipboardHistoryView, FlatClipboard
- `K/keyboard/emoji/` (2): EmojiPalettesView, EmojiSearchActivity
- `K/keyboard/internal/` (1): KeyboardIconsSet
- `K/keyboard/internal/keyboard_parser/` (1): KeyboardParser
- `K/keyboard/internal/keyboard_parser/floris/` (3): KeyCode, KeyLabel, TextKeyData
- `K/latin/` (11): App, AppUpgrade, ClipboardHistoryEntry, ClipboardHistoryManager,
  ClipboardThumbnails, DictionaryFacilitatorImpl, InputAttributes, InputView, LatinIME,
  RichInputConnection, Suggest
- `K/latin/common/` (1): Constants
- `K/latin/database/` (1): ClipboardDao
- `K/latin/define/` (1): DebugFlags
- `K/latin/dictionary/` (2): DictionaryFactory, ExpandableBinaryDictionary
- `K/latin/inputlogic/` (2): InputLogic, InputLogicHandler
- `K/latin/makedict/` (1): Ver4DictEncoder
- `K/latin/settings/` (2): Defaults, SettingsValues
- `K/latin/spellcheck/` (1): AndroidSpellCheckerService
- `K/latin/suggestions/` (5): BarEditor, BarState, FixedBar, FlatStrip, SuggestionStripView
- `K/latin/utils/` (9): AsyncResultHolder, BarTools, CapsModeUtils, DictionaryInfoUtils,
  DictionaryUtils, InlineAutofillUtils, LayoutUtilsCustom, SubtypeUtils, ToolbarUtils
- `K/settings/` (4): SearchScreen, SettingsActivity, SettingsContainer, SettingsNavHost
- `K/settings/dialogs/` (2): ColorPickerDialog, SliderDialog
- `K/settings/preferences/` (5): BackupRestorePreference, CustomFontPreference,
  KeyboardScalePreference, LoadGestureLibPreference, SliderPreference
- `K/settings/screens/` (49): AboutScreen, AdvancedScreen, AgentAiScreen, AgentAreaAiScreen,
  AgentAreaBarScreen, AgentAreaGesturesScreen, AgentAreaKeysScreen, AgentAreaLookScreen,
  AgentAreaScreen, AgentAreaSystemScreen, AgentAreaTypingScreen, AgentCommandsScreen,
  AgentDesignEditor, AgentDesignScreen, AgentDiagnoseScreen, AgentDictationScreen,
  AgentEmojiMixSection, AgentForcedAppsDialog, AgentLearningScreen, AgentMemoryScreen,
  AgentMyDataScreen, AgentProfilesScreen, AgentPromptsScreen, AgentRgbCurveEditor,
  AgentRgbFrameRate, AgentRgbPatternEditor, AgentRgbScreen, AgentRunStatus,
  AgentScreenReaderSection, AgentScreenshotScreen, AgentSearchEntries, AgentSettingsKit,
  AgentSkillsScreen, AgentSlider, AgentSnippetsSection, AgentSpaceScreen, AgentStartScreen,
  AgentSymbolsScreen, AgentUpdatePrompts, AgentUpdatesScreen, AgentWorkflowsScreen,
  AppearanceScreen, DictionaryScreen, GestureTypingScreen, MainSettingsScreen, TextCorrectionScreen,
  ToolbarScreen, WorkflowBuilderDialog, WorkflowDraftProtection
- `K/settings/screens/gesturedata/` (1): Share
- `agent-core/` (1): build.gradle
- `app/` (1): build.gradle
- `app/src/debug/res/values/` (3): clip_provider, gesture_data, strings
- `app/src/debugNoMinify/res/values/` (3): clip_provider, gesture_data, strings
- `app/src/main/` (1): AndroidManifest
- `app/src/main/assets/` (1): emoji_mix_pairs
- `app/src/main/assets/agent/` (1): german_verb_lemmas
- `app/src/main/assets/dicts/` (3): emoji_de, emoji_en, emoji_es
- `app/src/main/assets/layouts/emoji_bottom/` (2): emoji_bottom_row, emoji_bottom_row_with_action
- `app/src/main/assets/layouts/functional/` (2): functional_keys, functional_keys_flat
- `app/src/main/assets/layouts/more_symbols/` (1): symbols_shifted
- `app/src/main/assets/layouts/number_row/` (1): number_row
- `app/src/main/assets/layouts/symbol_hints/` (1): symbols_hints
- `app/src/main/assets/layouts/symbols/` (1): symbols
- `app/src/main/res/drawable-v24/` (1): ic_launcher_background
- `app/src/main/res/drawable/` (26): btn_keyboard_key_flat, btn_keyboard_key_flat_pill,
  btn_keyboard_key_flat_rect, btn_keyboard_key_functional_flat, ic_agent_actions, ic_agent_char_art,
  ic_agent_dictate, ic_agent_prompts, ic_agent_settings_updates, ic_launcher_foreground,
  ic_launcher_monochrome, ic_palette, ic_skill_1, ic_skill_2, ic_skill_3,
  sym_keyboard_clipboard_flat, sym_keyboard_delete_flat, sym_keyboard_go_flat,
  sym_keyboard_grid_flat, sym_keyboard_mic_flat, sym_keyboard_pen_flat, sym_keyboard_return_flat,
  sym_keyboard_shift_flat, sym_keyboard_shift_lock_flat, sym_keyboard_shifted_flat,
  sym_keyboard_smiley_flat
- `app/src/main/res/layout/` (1): main_keyboard_frame
- `app/src/main/res/mipmap-anydpi-v26/` (2): ic_launcher, ic_launcher_round
- `app/src/main/res/mipmap-hdpi/` (2): ic_launcher, ic_launcher_round
- `app/src/main/res/mipmap-mdpi/` (2): ic_launcher, ic_launcher_round
- `app/src/main/res/mipmap-xhdpi/` (2): ic_launcher, ic_launcher_round
- `app/src/main/res/mipmap-xxhdpi/` (2): ic_launcher, ic_launcher_round
- `app/src/main/res/mipmap-xxxhdpi/` (2): ic_launcher, ic_launcher_round
- `app/src/main/res/values-de/` (39): agent_ai_area_strings, agent_alarm_strings, agent_bar_strings,
  agent_bar_tools_strings, agent_barcalm_strings, agent_charart_strings, agent_clipvault_strings,
  agent_command_system_strings, agent_design_strings, agent_diagnose_strings,
  agent_emoji_mix_strings, agent_format_strings, agent_gestures_strings, agent_hardening_strings,
  agent_language_strings, agent_memory_strings, agent_name_strings, agent_prompt_strings,
  agent_rgb_pattern_strings, agent_rgb_strings, agent_selection_strings,
  agent_settings_keys_strings, agent_settings_look_strings, agent_settings_strings,
  agent_settings_system_strings, agent_slider_strings, agent_smart_paste_strings,
  agent_snippet_strings, agent_space_strings, agent_strings, agent_suggest_strings,
  agent_symbol_strings, agent_touch_strings, agent_trackdiag_strings, agent_typing_strings,
  agent_update_prompt_strings, agent_update_strings, strings, strings-talkback-descriptions
- `app/src/main/res/values/` (48): agent_ai_area_strings, agent_alarm_strings, agent_attrs,
  agent_bar_strings, agent_bar_tools_strings, agent_barcalm_strings, agent_charart_ids,
  agent_charart_strings, agent_clipvault_strings, agent_command_system_strings,
  agent_design_strings, agent_diagnose_strings, agent_emoji_mix_strings, agent_format_strings,
  agent_gestures_strings, agent_hardening_strings, agent_ids, agent_language_strings,
  agent_memory_strings, agent_name_strings, agent_prompt_strings, agent_prompts_ids,
  agent_rgb_pattern_strings, agent_rgb_strings, agent_search_strings, agent_selection_strings,
  agent_settings_keys_strings, agent_settings_look_strings, agent_settings_strings,
  agent_settings_system_strings, agent_slider_strings, agent_smart_paste_strings,
  agent_snippet_strings, agent_space_strings, agent_strings, agent_suggest_strings,
  agent_symbol_strings, agent_touch_strings, agent_trackdiag_strings, agent_typing_strings,
  agent_update_prompt_strings, agent_update_strings, attrs, clip_provider, donottranslate,
  gesture_data, strings, themes-flat
- `app/src/main/res/xml/` (4): agent_data_extraction_rules, agent_screen_reader,
  clipboard_provider_path, spellchecker
- `app/src/test/java/helium314/keyboard/` (2): KeyboardParserTest, Shadows
- `app/src/test/java/helium314/keyboard/latin/` (7): InputLogicTest, StripCalmLogTest, SuggestTest,
  SymbolLatencyTest, TypingEquivalenceTest, TypingRig, WordListDictionaries
- `app/src/test/java/helium314/keyboard/latin/inputlogic/` (1): BatchDeliveryTest
- `app/src/test/java/helium314/keyboard/latin/suggestions/` (3): BarDragTest, BarEditorTest,
  BarStateTest
- `app/src/test/java/helium314/keyboard/settings/` (1): BackupPromptsTest
- `companion/` (2): build.gradle, settings.gradle
- `companion/app/` (1): build.gradle
- `companion/app/src/main/` (1): AndroidManifest
- `companion/app/src/main/java/dev/personal/secondhand/companion/` (15): AndroidSupport, Downloader,
  ErrorText, FixedTests, GeniexPort, LoadDiagnose, MainActivity, MemoryGuard, MetadataCompat,
  Metrics, PullPlanner, ReportFormatter, RunLog, SpikeRunner, Support
- `companion/app/src/test/java/dev/personal/secondhand/companion/` (11): DownloaderTest,
  ErrorTextTest, Fakes, LoadDiagnoseTest, MemoryGuardTest, MetadataCompatTest, MetricsTest,
  PullPlannerTest, ReportFormatterTest, RunLogTest, SpikeRunnerTest
- `scripts/` (3): docs_sync, docs_sync, install-hooks
- `tools/` (3): build_german_verb_lemmas, dev, emoji_mix_table
- `tools/devicetest/` (3): build.gradle, devicetest, settings.gradle
- `tools/devicetest/app/` (1): build.gradle
- `tools/devicetest/app/src/main/` (1): AndroidManifest
- `tools/devicetest/app/src/main/java/dev/personal/secondhand/devicetest/` (5): DumpProvider,
  FakeAutofillService, InkProbe, MainActivity, ProbeAccessibilityService

</details>
<!-- /gen:inventory -->
