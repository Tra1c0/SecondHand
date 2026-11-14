# Agent Keyboard – Code-Landkarte

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
- **RGB-Beleuchtung** – Animierte Farben für Tasten, Beschriftung, Hintergrund und Leiste (Etappe 26, überarbeitet in 29): Rechnen im Kern, Zeichnen über einen Painter.
- **Diktat** – Diktieren über den On-device-Erkenner des Systems: zuhören, Vorschau, Einfügen erst nach eigenem Klick.
- **Lerndaten** – Was die Tastatur aus dem Tippen lernt: pausieren, zählen, exportieren, löschen. Alles auf dem Gerät.
- **Updates** – Opt-in-Prüfung auf neue Builds im öffentlichen Release-Repository, Download und Installation nur nach eigenem Klick.
- **Unveränderter Upstream** – Was ohne Änderung von HeliBoard übernommen ist; nur nach Ordnern beschrieben.
- **Symbole malen** – Etappe 27: Sonderzeichen auf die Buchstaben malen statt Gestenschreiben, lokal erkannt.
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
- Paketnamen: Die Upstream-Pakete `helium314.keyboard.*` bleiben, damit die native Engine stabil
  bleibt. Eigener App-Code liegt in `helium314.keyboard.agent` (`A/`) und in
  `K/settings/screens/`. Der Kern liegt in `dev.agentkeyboard.core` (`C/`).
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
- `ApplyOutcome.kt`: Ergebnis einer nativen Ersetzung (inklusive `UNKNOWN`, siehe Fallstrick P3).
- `LocalTextActions.kt`: lokale Textwerkzeuge ohne Netz.
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
- Symbole malen (Etappe 27, Abschnitt „Symbole malen“): `InkRecognizer.kt` (`InkStroke`,
  `InkCloud` mit 32 Punkten, Strichrichtung je Punkt und Zahl der Striche und Punkte (36),
  `InkRecognizer` = `$P` plus Richtungs- und Strukturterm mit ±12° Drehtoleranz (36),
  `InkResult` mit Schwellen, `InkGroup` für mehrstrichige Symbole), `InkSymbols.kt` (40
  eingebaute Symbole als Striche im 100er-Feld, 105 Vorlagen), `OwnInkSymbols.kt` (eigene Symbole:
  Textregeln, Format `v1`, `parse` wirft nie), `PersonalInkSymbols.kt` (36, persönliche Vorlagen),
  `InkTiming.kt` (36, `InkTap` Tipp-Regel, `InkPace` Pause nach Tempo). Tests
  `InkRecognizerTest`, `OwnInkSymbolsTest`, `InkBenchmarkTest` (mit `InkFingerModel`),
  `InkTimingTest`, `PersonalInkSymbolsTest`.
- Tests in `CT/`: `AiTextActionTest`, `EditorPolicyTest`, `LocalTextActionsTest`,
  `PendingBatchGateTest`, `SkillBindingTest`, `ToolTest`, `WorkflowTest`,
  `InsertionAnchorTest`, `StyledTranslationTest`, `UpdateReleasesTest`, `RgbLightingTest`,
  `RgbCurveTest`, `RgbPatternsTest`, `RgbFrameStatsTest`.
  Befehl: `core-tests`.

## Geänderte Upstream-Dateien

> Jede Upstream-Datei, die der Fork ändert, mit dem Grund. Alle übrigen Upstream-Dateien sind unverändert.

Engine-Einbindung (nur eng ändern, jede Änderung von einem Prüfer lesen lassen):

- `LatinIME.java`: erzeugt `AgentEditorController`, meldet Editorstart/-ende, abgeschickten Text
  und unterdrückte Lernhistorie. `switchToSubtypeByCommand` (Sprachbefehle) und die Prüfung
  `SubtypeChoice.holds` am Anfang von `onStartInputInternal` (P66). Engine-Speicher läuft über `AgentStorageContexts.keyboard`.
  Diagnose (30): je eine Zeile in `onCurrentInputMethodSubtypeChanged` (`SubtypeSwitchLog.onSwitched`
  nach den frühen Rücksprüngen), `switchToSubtype` (`internalSwitch`), `switchToSubtypeByCommand`
  (`expectCommand`) und `onStartInputInternal` vor `postSwitchLanguage` (`expectFromStart`), P98.
  Korrektur nach Prüfer (30/31): Die Prüfung `SubtypeChoice.holds` bekommt als dritten Wert eine
  Funktion, die `SubtypeState.getSubtypeForLocales(…, peek = true)` fragt; „behalten“ wird nur
  notiert, wenn die App wirklich umgeschaltet hätte (P98).
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
- `InputLogicHandler.java`: hält Worker- und UI-Teil eines Swipe-Batches über
  `PendingBatchGate` gemeinsam gesperrt.
- `RichInputConnection.java`: native Ersetzung als genau ein Commit, Ergebnis als `ApplyOutcome`
  (`replaceSelectedTextIfUnchanged`); ebenso `insertTextIfCursorUnchanged` für das Einfügen an
  der unveränderten Schreibmarke, nur wenn der Editor antwortet und nichts markiert ist
  (Fallstrick P24). Nur über `InputLogic.insertGeneratedText` aufrufen.
  `replaceTextBeforeCursorIfUnchanged` (24a) ersetzt einen getippten Befehl direkt vor der
  Schreibmarke: liest den Text aus dem Editor (nicht aus dem Cache), dann `setComposingRegion`
  plus `commitText` als ein Commit. Nur über `InputLogic.replaceCommandText` aufrufen.
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
- `KeyboardActionListener.java` (`CustomAction.SPACE_HOLD`, früher `SHOW_AGENT_WHEEL`;
  `onSpaceSwipe(SpaceGesture, int)` ersetzt `onHorizontalSpaceSwipe`/`onVerticalSpaceSwipe`;
  `onSpaceSwipeDistance(SpaceGesture, int, int)` für seitliche Einmal-Wische, Etappe 33) und
  `KeyboardActionListenerImpl.kt` (leitet Panel-, Rad-, Skill- und Diktat-Taste an den Controller,
  blockiert Eingabe während eines Overlays; Leertasten-Aktionen und Doppeltippen, siehe
  „Editorzugriff, Panel und Rad“; seitliche Einmal-Wische laufen über `SpaceDetents` und spielen
  `HapticChain`).
- `SettingsValues.java`: `mSpaceGestures` (aufgelöste Leertasten-Gesten) statt
  `mSpaceSwipeHorizontal`/`mSpaceSwipeVertical`; `mSpaceForLangChange` aus
  `SpaceGestures.holdShowsPicker`; `mUseDoubleSpacePeriod` ist aus, solange Doppeltippen eine
  Aktion hat.
- `AdvancedScreen.kt`: statt der Zeilen „Horizontales/Vertikales Wischen auf der Leertaste“ und
  „Leertaste lang drücken …“ ein Eintrag „Leertaste“ (`AGENT_SPACE_ENTRY`); Sprach-Wischdistanz
  und Touchpad-Regler stehen jetzt auf der Seite „Leertaste“. `SettingsContainer.kt`:
  `createAgentSpaceSettings` für Suche und Seite. `SettingsNavHost.kt`: Ziel `agent_space`.
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

Trefferzonen (Etappe 22a, Touch-Verarbeitung, jede Änderung mit Prüfer):

- `KeyDetector.java`: nach der normalen Erkennung `TouchModel.adjust`, nur wenn eingeschaltet.
- `KeyboardActionListenerImpl.kt`: `onCodeInput` meldet Tasten an `TouchCalibration`, solange
  das Kalibrierfeld den Fokus hat (Ansicht- in Tastatur-Koordinaten über das Padding).
- `LatinIME.java`: `onStartInputViewInternal` lädt `TouchModel` und schaltet die Kalibrierung
  nur für das eigene Feld mit `AgentPreferences.TOUCH_CALIBRATION_FIELD` ein.

Befehle (Etappe 24a, Details im Abschnitt „Befehle“):

- `LatinIME.java`: `setSuggestedWords` und `setNeutralSuggestionStrip` zeigen zuerst die Chips
  eines getippten Befehls (`tryShowCommandChips`); `getAgentTextBeforeCursor`,
  `getAgentCommandCursor`, `replaceCommandText` für `KeyboardCommands`;
  `onStartInputViewInternal` lädt die Befehlseinstellungen; `onCreate`/`onDestroy` hängen
  `SensitiveClips.attach` an die Zwischenablage (eigener Listener, der Verlauf bleibt unverändert).
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
  Symbolseite (`SYMBOLS_SHIFTED`) gibt es im flachen Stil keine Zahlenreihe (23a); die
  Buchstaben-Hinweise kommen bei `symbols_flat` weiter aus dem Standard-Layout `symbols`.
- `LayoutParser.kt`: `parseLayout` nimmt optional einen festen Layoutnamen (für die Hinweise).
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
  SYMBOLS_SHIFTED) offen ist (`showRecentEmojis`); ohne flachen Stil gibt es die Reihe nicht.
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
- `LatinIME.java` (23g): `setSuggestedWords` ruft nach `tryShowCommandChips` und nach
  `mInputLogic.setSuggestedWords` `tryShowScreenshotChip` (blendet bei `mAutoHideToolbar` die
  Symbolleiste aus wie der Zwischenablage-Chip); die Pille ersetzt also nur die Anzeige, die
  Vorschläge für die Autokorrektur bleiben in `InputLogic`. `onInlineSuggestionsResponse` zeigt
  Autofill über `showInlineAutofill` und merkt sich die View; `isShowingInlineAutofill` sagt, ob
  sie noch in der Leiste hängt (`removeExternalSuggestions` vergisst sie). `InputLogic` selbst ist
  unverändert.
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
  (verwirft die Texturkacheln).
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
  (`readAllWordProperties`, `clearAndWait`): Lernpause und Lerndaten, siehe „Lerndaten“.
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
- `AndroidManifest.xml` (Update-Prüfung): `POST_NOTIFICATIONS`, `REQUEST_INSTALL_PACKAGES`, Dienst
  `UpdateCheckJobService` (`BIND_JOB_SERVICE`) und Empfänger `UpdateInstallReceiver`, beide nicht
  exportiert und nicht `directBootAware`. `SettingsNavHost.kt`/`MainSettingsScreen.kt`: Ziel und
  Eintrag „Updates“ (`agent_updates`, auch in `AgentPreferences.SETTINGS_DESTINATIONS`).
- App-Identität: `app/build.gradle.kts` (App-ID `dev.personal.agentkeyboard`, Version 0.1.0,
  APK-Name, Lint-Baseline `lint-baseline-upstream.xml`), `settings.gradle`
  (`:agent-core`), `clip_provider.xml` und `gesture_data.xml` (eigene Provider-Authorities,
  auch in `debug`/`debugNoMinify`), `donottranslate.xml` und `strings.xml` in
  `debug`/`debugNoMinify` (Namen „Agent Keyboard“ bzw. „Agent Keyboard Lab“).

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
- `TextCorrectionScreen.kt` (36, Prüfer N4): Bestätigt man das Ausschalten von „Personalisierte
  Vorschläge“, löscht der Dialog auch die persönlichen Vorlagen von Symbole malen
  (`SymbolDrawing.clearPersonalSymbols`), eine Zeile.
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
- `ColorPickerDialog.kt`: Deckkraft- und Helligkeitsleiste der Bibliothek (colorpicker-compose)
  bleiben, ihre Berührungen übernimmt `AgentSliderInput` (setzt `setAlpha`/`setBrightness` wie die
  Bibliothek, mit `fromUser = true`); dazu Slider-Semantik für TalkBack (vorher keine).

Update-Migrationen (Build 12, P84): `AppUpgrade.kt` läuft nach einem Update nur noch die Teile
für jedes Update (entpackte Wörterbücher leeren, `upgradeToolbarPrefs`, Layout-Cache) und
überspringt HeliBoards Versions-Migrationen (`FORK_UPGRADES_ONLY`); beim ersten Update von Build
12 oder älter repariert `MyDesigns.repairKeyGapsAfterOldUpgrades` nur Abstände mit genau dem Wert
der alten Migration (1,75 bzw. 1,1 im Querformat): Hochformat bekommt den Abstand des gewählten
eigenen Designs, alle anderen ihren Standard; die Höhe bleibt. Test: `ForkUpgradeTest`.

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

## Editorzugriff, Panel und Rad

> Wie die Tastatur Auswahl liest, Aktionen zeigt und Ergebnisse übernimmt.

- `AgentEditorController.kt`: besitzt nur die Aktionssitzung der IME, nie das Textfeld der
  Host-App. Öffnet Panel, Rad und Skills, holt Freigaben ein, startet Läufe, zeigt Ergebnisse und
  führt Übernahme oder Kopieren aus. Übersetzt Fehler (`CloudError`, `DenialReason`) in Texte.
- `AndroidEditorAdapter.kt`: Main-Thread-Adapter. Holt die `InputConnection` jedes Mal neu,
  speichert sie nie in einem Snapshot. Prüft die Privacy vor dem Lesen und vergleicht vor der
  Übernahme erneut. `captureAnchor`/`insert` fügen an der Schreibmarke ein und lesen dafür
  keinen Text aus dem Editor; ohne gesetztes `commitInsertion` wird jede Einfügung abgelehnt.
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
- Tests in `AT/`: `EncryptedCredentialStoreTest`, `AgentStorageContextsTest`,
  `AgentDocumentIOTest`.

## KI-Anbindung

> Drei Cloud-Provider (OpenAI, Anthropic, Gemini) über einen gemeinsamen Transport. Verträge: docs/PROVIDERS.md.

- `providers/CloudTransport.kt`: der einzige Weg zu einem Provider. Genau ein nicht
  wiederholbarer JSON-POST (`isOneShot`), ein Aufruf zur selben Zeit (`BUSY`), Budget 45 s,
  Antwort höchstens 1 MiB, HTTP-Status wird auf neutrale `CloudError` abgebildet. Fremde
  Fehlerkörper werden weder angezeigt noch geloggt. Provider-Klassen liefern nur Body, Request
  und Parser.
- `providers/OpenAiProvider.kt`: `OpenAiProvider` (Responses-API, `store:false`/`stream:false`,
  2048 Ausgabetokens, Builder 4096; einziger Provider mit `workflowDrafts`), `CloudHttpClient`
  (OkHttp, erst nach dem Senden initialisiert, ohne Redirects und Retries; Connect/Read/Write
  10 s, Call 45 s), `CloudError`, `CloudFailure`. Der AndroidX-Startup-Initializer ist im
  Manifest entfernt, `OkHttp.initialize` läuft erst nach dem Senden auf IO.
- `providers/AnthropicProvider.kt`: Messages-API, `anthropic-version` aus `API_VERSION`,
  Bearer-Schlüssel, kein `temperature`, `max_tokens` 8192 (`MAX_TOKENS`; Denk-Token zählen bei
  Modellen mit Standard-Thinking mit). Ergebnis nur bei `stop_reason:end_turn`; `refusal` wird
  `REFUSED`; `max_tokens` wird `TOO_LARGE` (abgeschnitten, nie ein Ergebnis); `thinking`-Blöcke
  werden übergangen, jeder andere Blocktyp ist ein Fehler.
- `providers/GeminiProvider.kt`: `generateContent` (v1beta), Schlüssel im Header
  `x-goog-api-key`, nie in der URL. Das Modell wird Teil des URL-Pfads und ist deshalb strenger
  geprüft (`validModel`: nur Kleinbuchstaben, Ziffern, Punkt, Bindestrich). Ergebnis nur bei
  `finishReason:STOP` und genau einem Kandidaten; `MAX_TOKENS` wird `TOO_LARGE`; Teile mit
  `thought:true` werden übergangen. `maxOutputTokens` 8192.
- `providers/CloudProviders.kt`: `SUPPORTED` (Provider mit echtem Adapter, Reihenfolge der
  Einstellungen), `get` (erzeugt den Adapter erst bei der ersten freigegebenen Anfrage),
  `slot` (eigener Schlüsselplatz je Provider), `displayName`, `validModel`.
- Regeln für alle Provider: Ergebnis höchstens 16384 Zeichen, nur vollständig abgeschlossener
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
  Workflow). Epoch-Prüfungen schützen vor späten Antworten, das Gesamtbudget beträgt 95 s.
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
  Befehle mit `Command.longArgument` (bisher `@translate`) dürfen 500 (`MAX_LONG_ARGUMENT`,
  `parse(…, longArguments)`). `LOOKBACK` richtet sich nach der langen Grenze, alle Leser
  (`guardsTextBefore`, `updateGuard`, Chips) lesen deshalb bis zu 519 Zeichen. Erlaubte Präfixe
  (`ALLOWED_PREFIXES`) sind nur Zeichen, die in `symbols_word_separators` fehlen; sonst wäre der
  Befehl beim Tippen kein einzelnes Wort und die Autokorrektur könnte ihn ändern.
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
    Einstellungen `K/settings/screens/AgentSnippetsSection.kt`), `A/commands/ClipCommand.kt`
    (`@clip`, nur mit eingeschaltetem Verlauf, nur Text; überspringt den aktuellen vertraulichen Clip
    und alles, was `A/commands/SensitiveClips.kt` als vertraulich kopiert vermerkt hat: eigener
    Zwischenablage-Listener aus `LatinIME`, Markierung `IS_SENSITIVE` oder Passwortfeld als
    aktueller Editor, gesalzene SHA-256 in DE-Einstellungen, Speicher: `docs/STORAGE.md`; vor
    diesem Stand kopierte Clips bleiben unerkannt), `A/commands/WorkflowCommand.kt` (`@wf`,
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
  - Tests: `CT/UrlDetectorTest.kt`, `CT/SymbolTableTest.kt`, `CT/UnitConversionTest.kt`,
    `CT/EventParserTest.kt`, `CT/DateFormattingTest.kt`, `CT/PlacesTest.kt`, `CT/AlarmParserTest.kt`,
    `CT/TimerParserTest.kt`, `AT/commands/ClockCommandsTest.kt`;
    `AT/ContactCommandTest.kt`, `AT/MoreCommandsTest.kt`, `AT/SnipCommandTest.kt`,
    `AT/ClipCommandTest.kt`, `AT/DatePlaceCommandsTest.kt`, `UrlCommandTest` und
    `ScreenReaderServiceTest` (`AT/commands/`), `InputLogicTest.typed*`.

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
  Verlaufskarten mit Leucht-Pille, gewählt 100 % mit weißem Rahmen 3 dp in 2 dp Abstand, sonst
  55 %; Radio-Buttons in einer `selectableGroup`), `ChipToggles` (hell wie auf der Startseite oder
  im Bereichsverlauf wie die @-Befehle), `AreaTile`, `FooterPill`. Schriften: Systemschrift in
  denselben Stärken statt Space Grotesk/Inter (beide OFL, aber keine Schriftdateien im APK;
  Entscheidung des Leads, 38a). `K/settings/screens/AgentStartScreen.kt`: Startseite (Titel,
  Lupe öffnet die bisherige Suche über `settingsContainer.filter`, Look-Karte mit
  `lookName` = eigenes Design, „Flacher Look“ oder Tastenstil und Lichtmuster der Tasten,
  „Ändern“ → `area_look`; Schnellschalter auf `always_incognito_mode`, `RgbSettings.enabled`
  über `RgbLighting.write`, `vibrate_on`, `agent_symbol_drawing`; Kacheln 01 breit, 02/03, 04/05,
  06 und 07 breit; Pillen Diagnose, „Updates · Build <VERSION_CODE>“, Über).
  `K/settings/screens/AgentAreaScreen.kt`: Bereichsseite (Zurück, Held, Karte „Seiten“) und
  `SettingsAreas.entries(area)`, die vorläufige Zuordnung der bisherigen Seiten nach
  `Struktur.dc.html` (Aussehen: Design, RGB, Aussehen; Tippen: Textkorrektur, Gelernte Wörter,
  Wörterbücher; Tasten: Sprachen, Optionen, Sekundäre Layouts; Gesten: Leertaste, Symbole malen,
  Gestenschreiben (nur mit Bibliothek), Optionen als „Vibration & Ton“; Leiste: Symbolleiste
  als „Leiste“, Befehle, Screenshots; KI: KI-Anbieter, Skill-Tasten, Workflows, Diktat; System:
  Profile, Updates, Diagnose, Erweitert, Gestendaten (nur in der Sammelphase), Über). 38b–38h
  ersetzen die Karte „Seiten“ je Bereich durch den Aufbau des Mockups. `patternLabel` in
  `AgentRgbScreen.kt` ist dafür `internal`. Texte: `agent_settings_strings.xml`. Tests
  `AT/SettingsStartScreenTest.kt` und `AT/SettingsKitTest.kt` (Bilder unter
  `app/build/agent-previews/settings-*.png`).
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
  Engine und Farbregeln wie die Tastatur). Einträge auf der Design-Seite (`agent_design_rgb`) und
  unter Aussehen. Texte: `agent_rgb_strings.xml`. Test `AT/RgbSettingsScreenTest.kt`.
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
  die App wirklich umgeschaltet hätte.
  Texte: `agent_diagnose_strings.xml`. Tests: `AT/DiagnoseScreenTest.kt`,
  `InputLogicTest.subtypeSwitch*`.
- `KeyboardProfileStore.kt`: das Designprofil enthält `agent_rgb_lighting` als Text
  (`Binding.freeText`); beim Lesen feldweise über `RgbSettings.parse` begrenzt, leer (Profile von
  vor 26) heißt „unverändert lassen“.

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
- `assets/layouts/functional/functional_keys_flat.json`: wie `functional_keys.json`, aber die
  Emoji- und die 1234-Taste haben `"type": "character"` (heller Hintergrund), keine Pfeiltaste,
  und auf der zweiten Symbolseite `<` und `>` statt Komma und Punkt.
- `assets/layouts/symbols/symbols_flat.txt` und `assets/layouts/more_symbols/symbols_shifted_flat.txt`:
  Symbolseiten nach der Gboard-Vorlage (23a), von `FlatPreset` gesetzt.
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
  ohne sichtbare Leiste (Symbolleiste ausgeblendet) bleibt die Ansicht beim alten Aufbau mit
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
  EXPANDABLE wird die Symbolleiste eingeklappt wie bei Befehlschips. „Zwischen den Wörtern“
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
  Leeren). Bilder unter `app/build/reports/rgb-lighting/` und `app/build/agent-previews/`.

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
  Je getippter Form die gemeinten Wörter mit Gesamtzahl und Zahl pro vorherigem Wort (höchstens
  20). Höchstens 1000 getippte Formen, zuletzt benutzte bleiben. Angewendet ab drei
  Bestätigungen und 80 % aller Bestätigungen dieser Form; eine Form, die selbst Wort eines
  Hauptwörterbuchs ist, nur über den Kontext. Nicht gezählt: Vervollständigungen (gemeintes Wort
  beginnt mit dem getippten), Formen mit Ziffern, unter zwei Zeichen, nur andere Großschreibung.
  Tests: `TypoMemoryTest`, Fälle „stage 20b“ in `SuggestTest` und `InputLogicTest`.
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

## Updates

> Opt-in-Prüfung auf neue Builds im öffentlichen Release-Repository, Download und Installation nur nach eigenem Klick.

- Quelle: öffentliches Repository `Tra1c0/AgenticKeyboard` (`UpdateReleases.REPOSITORY` in
  `C/UpdateReleases.kt`, im Workflow `RELEASE_REPOSITORY`). Kein Token, kein `Authorization`-Header.
- `A/updates/GitHubUpdateClient.kt`: `UpdateHttp` (eigener OkHttp-Client, nicht der KI-Client,
  ohne automatische Redirects und Retries; Connect 15 s, Read 30 s) und `GitHubUpdateClient`:
  `GET …/releases?per_page=10` (höchstens 2 MiB, 30 s), strenges Parsen, `.sha256`-Asset
  (höchstens 4 KiB), APK-Download über `browser_download_url` mit selbst verfolgten Redirects
  (höchstens 5, nur HTTPS), Größe ≤ 120 MB und genau die gelistete Größe, SHA-256 beim Schreiben.
  `UpdateError`/`UpdateFailure` sind neutral, kein Antworttext wird gezeigt oder geloggt.
- `C/UpdateReleases.kt` nimmt nur Assets mit genau der erwarteten Download-URL
  (`https://github.com/<REPOSITORY>/releases/download/build-<n>/<Name>`).
- `A/updates/Updates.kt`: `check` (speichert das Ergebnis), `downloadAndInstall` (fragt die API
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
- `A/updates/UpdateSettings.kt`: CE-Einstellungen `agent_updates` (`UpdateStatus`).
- `K/settings/screens/AgentUpdatesScreen.kt`: Seite „Updates“ (Schalter, „Jetzt prüfen“, Ergebnis,
  Download-Knopf mit Fortschritt und Abbrechen, Weg zu „Unbekannte Apps installieren“, Hinweis
  für Freunde). Texte in `agent_update_strings.xml`, Icon `ic_agent_settings_updates.xml`.
- Tests: `AT/UpdateCheckTest.kt` (Anfrage ohne Zugangsdaten, strenges Parsen, Redirect nur
  HTTPS, Prüfsummen-, Paket- und Zertifikatsfehler, Größen, Schalter und Job, Benachrichtigung
  einmal je Build), `CT/UpdateReleasesTest.kt`.

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
  Build 14: der Rest eines Symbols begann ein neues). Ein Tipp (36: Bewegung unter
  `InkTap.SLOP_DP` = 8 dp wie Androids Touch-Slop (Nachprüfer F3; 6 dp nach dem ersten Prüfer) vom
  Aufsetzpunkt, Dauer egal, P111; vorher 10 dp, in der ersten Fassung
  von 36 4 dp, Prüfer H1) ist nur dann ein
  Punkt, wenn `SymbolInk.dotCheck` zustimmt (31, `C/InkDots.kt`): (1) ein Symbol hat Punkte
  (eingebaut nur ÷, dazu eigene), (2) die bisherigen Striche ohne frühere Punkte gleichen dessen
  punktlosem Teil (Abstand des Erkenners ≤ `ACCEPT_DISTANCE`, seit 36 1,8 mit Richtungs- und
  Strukturterm, vorher `$P` ≤ 1,3), (3) der Tipp liegt nach Deckung der Teile (gleiche Mitte, gleiche
  längere Seite, ein schräger Strich bis 20° gerade gedreht) in Richtung eines seiner Punkte, von
  der Mitte des punktlosen Teils aus gesehen höchstens 0,12 Symbolgrößen seitlich und 0,25 näher
  oder weiter (bei ÷: ungefähr senkrecht über oder unter der Mitte des Strichs; Korrektur nach
  Prüfer, vorher ein Kreis mit 0,3), (4) kein ganzes Symbol passt klar besser (Faktor 1,25 plus
  0,1; ein fertiges → ist nah an einem Strich, bleibt aber ein Pfeil).
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
  (`flush`) gehen die Striche an die Senke. Es wird nie sofort nach einem Strich geschrieben, weil
  jeder Strich der erste eines mehrstrichigen Symbols sein kann (– → =, ≠).
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
  Korrektur (Lernen, unten). Bekannt, nicht geändert: im Modus „Werkzeugleiste ausklappbar“ wird die
  Leiste bei jedem Wechsel zwischen Vorschau und Vorschlägen wie bei den übrigen Chips umgeschaltet.
  Wird die Zeichnung
  verworfen, meldet `cancel` eine leere Liste und die Leiste holt ihre Vorschläge zurück
  (`postUpdateSuggestionStrip`); `reset` setzt den Zustand vor `SymbolInk.cancel` zurück (P112).
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
- `tools/emoji_mix_table.py` (25a): baut `app/src/main/assets/emoji_mix_pairs.bin` aus der
  Emoji-Kitchen-Metadatei; Aufruf und Quelle in `docs/BUILD.md` → Emoji-Mix-Tabelle.
- Baseline: `upstream-tests` endet mit genau fünf geerbten Fehlern, Liste in
  `docs/VALIDATION.md` und `docs/HANDOFF.md` → Offene Punkte. Jeder weitere Fehler ist neu.
- CI: `.github/workflows/docs-check.yml` führt bei jedem Push den Docs-Check aus.
  `.github/workflows/agent-tests.yml` (seit Build 12) läuft bei Pushes auf `claude/**` und `main`,
  die Code ändern, und von Hand: drei parallele Jobs `agent` (core- und agent-tests), `upstream`
  (voller Lauf) und `lint`, je über `tools/dev.py`. Die Zusammenfassung (Job-Log und Step-Summary)
  listet jeden Fehler als `known` oder `NEW`; nur `NEW` außerhalb der Baseline (Hangul, Emoji,
  `XLinkTest.*`) lässt den Job scheitern. Berichte als Artefakt `reports-<job>` (7 Tage). Ein
  neuer Push auf denselben Branch bricht den laufenden Lauf ab.
  `.github/workflows/agent-apk.yml` baut von Hand gestartet die installierbare, mit dem
  Schlüssel des Owners signierte APK (`docs/BUILD.md` → Installierbare APK); dafür liest
  `app/build.gradle.kts` `AGENT_KEYSTORE_FILE`, `AGENT_KEYSTORE_PASSWORD`, `AGENT_KEY_ALIAS` und
  `AGENT_VERSION_CODE` aus der Umgebung. Mit dem Secret `RELEASES_TOKEN` legt er dasselbe Release
  im öffentlichen Repository `RELEASE_REPOSITORY` an (Abschnitt „Updates“), ohne Secret nur eine
  Warnung. Von Upstream
  übernommen: `build-debug-apk.yml` (nur manuell) und `build-test-auto.yml` (bei Pull Requests;
  ruft den nicht existierenden Task `testRunTestsUnitTest` auf, siehe `docs/BUILD.md`).
- Docs-System: `scripts/docs_sync.py` (Konfiguration `scripts/docs_sync.json`),
  `scripts/install-hooks.sh` (optionaler Pre-Commit-Hook), `.claude/hooks/session-start.sh`
  (registriert in `.claude/settings.json`). Bedienung: `docs/HANDOFF.md` → Arbeitsweise.

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
  bezahlte Anfragen als „abgeschnitten“ enden. OpenAI steht noch bei 2048 (offener Punkt in
  `docs/HANDOFF.md`), Anthropic und Gemini bei 8192.
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

## Datei-Inventar

> Erzeugte Liste aller kartierten Dateien nach Ordner.

<!-- gen:inventory -->
<details><summary>Alle kartierten Dateien nach Ordner (439 files; generated, do not edit)</summary>

- `.claude/` (1): settings
- `.claude/hooks/` (1): session-start
- `.github/workflows/` (5): agent-apk, agent-tests, build-debug-apk, build-test-auto, docs-check
- `/` (1): settings
- `A/` (69): ActionRunJournal, AgentDocumentIO, AgentEditorController, AgentFileProvider,
  AgentKeyboardFrame, AgentPanelView, AgentPreferences, AgentStorageContexts, AgentWheelView,
  AndroidEditorAdapter, ApprovedActionRunner, CalculatorView, CharacterCommand, ContactCommand,
  ConvertCommand, CorrectionMemory, DateCommand, DesignLook, DesignTiles, Diagnose, Dictation,
  EmojiCommand, EmojiMixPalette, EmojiSuggestions, EncryptedActionRunStore, EventCommand,
  FlatMenuView, FlatPanelStyle, FlatPreset, HapticChain, JournaledModelCall, JournaledRun,
  KeyboardCommands, KeyboardProfileStore, LearningData, LoadingDotsView, ManualWorkflowDraft,
  ModelSettings, MyDesigns, OnDeviceSpeechEngine, PlaceCommand, RgbKeyPainter, RgbLighting,
  RgbStrip, RgbTexture, RunSubjectCodec, SavedPlaces, ScreenshotChip, SearchCommand,
  SkillBindingStore, SkillLabels, SliderFeel, SliderHaptics, SpaceDetents, SpaceGestures,
  SubtypeSwitchLog, SymbolDrawing, SymbolInk, ToolApprovalSummary, ToolLauncher, TouchCalibration,
  TouchModel, TypoMemory, WorkflowApprovalSummary, WorkflowBuilderContract,
  WorkflowConditionSummary, WorkflowDraftRunner, WorkflowInputSummary, WorkflowStore
- `A/commands/` (18): AiCommandChips, AlarmCommand, AskCommand, ClipCommand, EmojiMixCommand,
  EmojiMixRecents, EmojiMixer, LanguageCommand, QrCommand, ScreenReaderService, SensitiveClips,
  SnipCommand, SnippetStore, SubtypeChoice, TimerCommand, TranslateCommand, UrlCommand,
  WorkflowCommand
- `A/providers/` (5): AnthropicProvider, CloudProviders, CloudTransport, GeminiProvider,
  OpenAiProvider
- `A/security/` (2): EncryptedCredentialStore, SecretCipher
- `A/updates/` (5): GitHubUpdateClient, UpdateCheckJob, UpdateInstaller, UpdateSettings, Updates
- `AT/` (76): AgentDocumentIOTest, AgentPanelViewTest, AgentSliderTest, AgentSpaceGestureTest,
  AgentStorageContextsTest, AgentWheelViewTest, AndroidEditorAdapterTest, ApprovedActionRunnerTest,
  ApprovedWorkflowRunnerTest, AskCommandTest, BundledEmojiDictTest, CalculatorViewTest,
  CalendarToolLauncherTest, ClipCommandTest, CommandReplaceTest, CommandTemplatesTest,
  ContactCommandTest, CorrectionMemoryTest, DatePlaceCommandsTest, DebugPrivacyTest,
  DesignEditorTest, DesignTilesTest, DiagnoseScreenTest, DictationSessionTest, DictationUiTest,
  EmojiMixPaletteTest, EmojiMixRecentsTest, EncryptedActionRunStoreTest,
  EncryptedCredentialStoreTest, FlatClipboardTest, FlatMenuTest, FlatPanelTest, FlatStyleRenderTest,
  FlatStyleTest, ForkIdentityTest, ForkUpgradeTest, HapticChainTest, InsertAtCursorTest,
  JournaledModelCallTest, KeyboardCommandsTest, KeyboardProfileStoreTest, LearningDataTest,
  LoadingDotsViewTest, ManualWorkflowDraftTest, MoreCommandsTest, MoreProvidersTest, MyDesignsTest,
  OpenAiProviderTest, RgbLightingRenderTest, RgbSettingsScreenTest, RgbTilesTest,
  ScreenshotChipTest, SelectedTextCommitTest, SettingsKitTest, SettingsStartScreenTest,
  SettingsTypingAreaTest, SkillBindingStoreTest, SkillSettingsTest, SliderFeelTest, SnipCommandTest,
  SpaceDetentsTest, SpaceGesturesTest, SymbolInkTest, SymbolSettingsScreenTest, TestRunJournal,
  ToolRunnerTest, TouchModelTest, TypoMemoryTest, UpdateCheckTest, WorkflowBuilderContractTest,
  WorkflowBuilderSettingsTest, WorkflowCommandTest, WorkflowConditionSummaryTest,
  WorkflowDraftRunnerTest, WorkflowSettingsTest, WorkflowStoreTest
- `AT/commands/` (8): ClockCommandsTest, EmojiMixCommandTest, LanguageCommandTest, QrCommandTest,
  ScreenReaderServiceTest, SubtypeChoiceTest, TranslateCommandTest, UrlCommandTest
- `C/` (40): AiTextAction, AlarmParser, ApplyOutcome, CivilDate, Commands, DateFormatting,
  EditorSession, EmojiKeywords, EmojiMix, EmojiNames, EmojiSynonyms, EventParser, ExecutionPolicy,
  InkDots, InkRecognizer, InkSymbols, InkTiming, LanguageNames, LocalTextActions, MathExpression,
  ModelProvider, OwnInkSymbols, PendingBatchGate, PersonalInkSymbols, Places, Profiles, RgbCurve,
  RgbLighting, RgbPatterns, SkillBinding, SpecialCharacters, StyledTranslation, SymbolTable,
  TextCondition, TimerParser, Tool, UnitConversion, UpdateReleases, UrlDetector, Workflow
- `CT/` (36): AiTextActionTest, AlarmParserTest, CommandSyntaxTest, DateFormattingTest,
  EditorPolicyTest, EmojiKeywordsTest, EmojiMixTest, EventParserTest, InkAreaTest, InkBenchmarkTest,
  InkDotsTest, InkFingerModel, InkRecognizerTest, InkTimingTest, InsertionAnchorTest,
  LanguageNamesTest, LocalTextActionsTest, MathExpressionTest, OwnInkSymbolsTest,
  PendingBatchGateTest, PersonalInkSymbolsTest, PlacesTest, QuestionCaptureTest, RgbCurveTest,
  RgbFrameStatsTest, RgbLightingTest, RgbPatternsTest, SkillBindingTest, StyledTranslationTest,
  SymbolTableTest, TimerParserTest, ToolTest, UnitConversionTest, UpdateReleasesTest,
  UrlDetectorTest, WorkflowTest
- `K/keyboard/` (10): FlatStyle, Key, KeyDetector, KeyboardActionListener,
  KeyboardActionListenerImpl, KeyboardSwitcher, KeyboardTheme, KeyboardView, MainKeyboardView,
  PointerTracker
- `K/keyboard/clipboard/` (3): ClipboardAdapter, ClipboardHistoryView, FlatClipboard
- `K/keyboard/emoji/` (2): EmojiPalettesView, EmojiSearchActivity
- `K/keyboard/internal/` (1): KeyboardIconsSet
- `K/keyboard/internal/keyboard_parser/` (2): KeyboardParser, LayoutParser
- `K/keyboard/internal/keyboard_parser/floris/` (2): KeyCode, KeyLabel
- `K/latin/` (10): App, AppUpgrade, ClipboardHistoryEntry, ClipboardHistoryManager,
  ClipboardThumbnails, DictionaryFacilitatorImpl, InputView, LatinIME, RichInputConnection, Suggest
- `K/latin/database/` (1): ClipboardDao
- `K/latin/define/` (1): DebugFlags
- `K/latin/dictionary/` (2): DictionaryFactory, ExpandableBinaryDictionary
- `K/latin/inputlogic/` (2): InputLogic, InputLogicHandler
- `K/latin/settings/` (2): Defaults, SettingsValues
- `K/latin/spellcheck/` (1): AndroidSpellCheckerService
- `K/latin/suggestions/` (2): FlatStrip, SuggestionStripView
- `K/latin/utils/` (4): DictionaryInfoUtils, DictionaryUtils, SubtypeUtils, ToolbarUtils
- `K/settings/` (4): SearchScreen, SettingsActivity, SettingsContainer, SettingsNavHost
- `K/settings/dialogs/` (2): ColorPickerDialog, SliderDialog
- `K/settings/preferences/` (2): KeyboardScalePreference, SliderPreference
- `K/settings/screens/` (33): AdvancedScreen, AgentAiScreen, AgentAreaScreen, AgentAreaTypingScreen,
  AgentCommandsScreen, AgentDesignEditor, AgentDesignScreen, AgentDiagnoseScreen,
  AgentDictationScreen, AgentEmojiMixSection, AgentLearningScreen, AgentProfilesScreen,
  AgentRgbCurveEditor, AgentRgbScreen, AgentRunStatus, AgentScreenReaderSection,
  AgentScreenshotScreen, AgentSettingsKit, AgentSkillsScreen, AgentSlider, AgentSnippetsSection,
  AgentSpaceScreen, AgentStartScreen, AgentSymbolsScreen, AgentUpdatesScreen, AgentWorkflowsScreen,
  AppearanceScreen, DictionaryScreen, GestureTypingScreen, MainSettingsScreen, TextCorrectionScreen,
  WorkflowBuilderDialog, WorkflowDraftProtection
- `K/settings/screens/gesturedata/` (1): Share
- `agent-core/` (1): build.gradle
- `app/` (1): build.gradle
- `app/src/debug/res/values/` (3): clip_provider, gesture_data, strings
- `app/src/debugNoMinify/res/values/` (3): clip_provider, gesture_data, strings
- `app/src/main/` (1): AndroidManifest
- `app/src/main/assets/` (1): emoji_mix_pairs
- `app/src/main/assets/dicts/` (3): emoji_de, emoji_en, emoji_es
- `app/src/main/assets/layouts/emoji_bottom/` (2): emoji_bottom_row, emoji_bottom_row_with_action
- `app/src/main/assets/layouts/functional/` (1): functional_keys_flat
- `app/src/main/assets/layouts/more_symbols/` (1): symbols_shifted_flat
- `app/src/main/assets/layouts/symbols/` (1): symbols_flat
- `app/src/main/res/drawable/` (21): btn_keyboard_key_flat, btn_keyboard_key_flat_pill,
  btn_keyboard_key_flat_rect, btn_keyboard_key_functional_flat, ic_agent_actions, ic_agent_dictate,
  ic_agent_settings_updates, ic_skill_1, ic_skill_2, ic_skill_3, sym_keyboard_clipboard_flat,
  sym_keyboard_delete_flat, sym_keyboard_go_flat, sym_keyboard_grid_flat, sym_keyboard_mic_flat,
  sym_keyboard_pen_flat, sym_keyboard_return_flat, sym_keyboard_shift_flat,
  sym_keyboard_shift_lock_flat, sym_keyboard_shifted_flat, sym_keyboard_smiley_flat
- `app/src/main/res/layout/` (1): main_keyboard_frame
- `app/src/main/res/values-de/` (13): agent_alarm_strings, agent_design_strings,
  agent_diagnose_strings, agent_emoji_mix_strings, agent_language_strings, agent_rgb_strings,
  agent_settings_strings, agent_slider_strings, agent_space_strings, agent_strings,
  agent_symbol_strings, agent_typing_strings, agent_update_strings
- `app/src/main/res/values/` (20): agent_alarm_strings, agent_attrs, agent_design_strings,
  agent_diagnose_strings, agent_emoji_mix_strings, agent_ids, agent_language_strings,
  agent_rgb_strings, agent_settings_strings, agent_slider_strings, agent_space_strings,
  agent_strings, agent_symbol_strings, agent_typing_strings, agent_update_strings, attrs,
  clip_provider, donottranslate, gesture_data, themes-flat
- `app/src/main/res/xml/` (3): agent_data_extraction_rules, agent_screen_reader,
  clipboard_provider_path
- `app/src/test/java/helium314/keyboard/` (1): Shadows
- `app/src/test/java/helium314/keyboard/latin/` (2): InputLogicTest, SuggestTest
- `app/src/test/java/helium314/keyboard/latin/inputlogic/` (1): BatchDeliveryTest
- `scripts/` (3): docs_sync, docs_sync, install-hooks
- `tools/` (2): dev, emoji_mix_table

</details>
<!-- /gen:inventory -->
