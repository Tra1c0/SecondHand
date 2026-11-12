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
- **Act Builder** – Natürliche Beschreibung → freigegebene Anfrage → streng validierter Workflow-Entwurf.
- **Einstellungen und Profile** – Compose-Seiten der Agent-Funktionen sowie Design-, Layout- und Gestenprofile.
- **Flacher Stil** – Tastenstil „Flat“, Farbsatz „Flach schwarz“ und die Vorlage, die beides mit Größen einstellt.
- **Diktat** – Diktieren über den On-device-Erkenner des Systems: zuhören, Vorschau, Einfügen erst nach eigenem Klick.
- **Lerndaten** – Was die Tastatur aus dem Tippen lernt: pausieren, zählen, exportieren, löschen. Alles auf dem Gerät.
- **Unveränderter Upstream** – Was ohne Änderung von HeliBoard übernommen ist; nur nach Ordnern beschrieben.
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
  Ersetzung. Dazu `TranslationLanguage` und `WritingTone`.
  <!-- gen:ai_actions -->
6 KI-Textaktionen: `CORRECT`, `TRANSLATE`, `REPHRASE`, `SUMMARIZE`, `REPLY`, `ANALYZE`
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
- Tests in `CT/`: `AiTextActionTest`, `EditorPolicyTest`, `LocalTextActionsTest`,
  `PendingBatchGateTest`, `SkillBindingTest`, `ToolTest`, `WorkflowTest`,
  `InsertionAnchorTest`. Befehl: `core-tests`.

## Geänderte Upstream-Dateien

> Jede Upstream-Datei, die der Fork ändert, mit dem Grund. Alle übrigen Upstream-Dateien sind unverändert.

Engine-Einbindung (nur eng ändern, jede Änderung von einem Prüfer lesen lassen):

- `LatinIME.java`: erzeugt `AgentEditorController`, meldet Editorstart/-ende, abgeschickten Text
  und unterdrückte Lernhistorie. Engine-Speicher läuft über `AgentStorageContexts.keyboard`.
- `InputLogic.java`: verwirft Caches und blockiert Eingabepfade nach einer unklaren Ersetzung
  (`ApplyOutcome.UNKNOWN`), unterdrückt Lernen nach generierter Übernahme. Ersetzen
  (`applyGeneratedText`) und Einfügen (`insertGeneratedText`) laufen durch dieselbe
  Nachbereitung (`applyGenerated`). Beim Einfügen kommt ein ausstehendes automatisches
  Leerzeichen (nach gewähltem Vorschlag) unter denselben Bedingungen wie beim Tippen mit in
  denselben einen Commit.
- `InputLogicHandler.java`: hält Worker- und UI-Teil eines Swipe-Batches über
  `PendingBatchGate` gemeinsam gesperrt.
- `RichInputConnection.java`: native Ersetzung als genau ein Commit, Ergebnis als `ApplyOutcome`
  (`replaceSelectedTextIfUnchanged`); ebenso `insertTextIfCursorUnchanged` für das Einfügen an
  der unveränderten Schreibmarke, nur wenn der Editor antwortet und nichts markiert ist
  (Fallstrick P24). Nur über `InputLogic.insertGeneratedText` aufrufen.
- `PointerTracker.java`: Leertasten-Langdruck öffnet das Rad über
  `CustomAction.SHOW_AGENT_WHEEL` und beendet den laufenden Zeiger samt Swipe/Touchpad-Flags.
- `KeyboardActionListener.java` (neue `CustomAction.SHOW_AGENT_WHEEL`) und
  `KeyboardActionListenerImpl.kt` (leitet Panel-, Rad-, Skill- und Diktat-Taste an den Controller,
  blockiert Eingabe während eines Overlays).
- `InputView.java`: leitet Touch und Hover an sichtbare Overlays (Panel, Rad).
- `main_keyboard_frame.xml`: Wurzel ist `AgentKeyboardFrame`, darin Tastatur, `FlatMenuView`, `AgentPanelView`
  und `AgentWheelView`.
- `KeyCode.kt` (`AGENT_ACTIONS` = -12000, `SKILL_1`–`SKILL_3` = -12001…-12003,
  `AGENT_DICTATE` = -12004),
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
- `InputLogic.java` (22b): `commitChosenWord` gibt die Tipp-Koordinaten des Worts an
  `TouchModel.learnFromWord` (nur mit Schalter und nur Tipp-Eingabe).

Flacher Stil (jeweils nur ein Aufruf in `FlatStyle`, Details im Abschnitt „Flacher Stil“):

- `KeyboardTheme.kt`: Stil `STYLE_FLAT` („Flat“, Theme-ID 5, immer mit Tastenhintergrund) und
  Farbsatz `THEME_FLAT_BLACK`. `attrs.xml`: Enum-Wert `Flat` = 5 bei `themeId` und
  `keyboardTheme`.
- `Key.java`: `KeyParams.mVerticalGapScale` (Lücke unter einer Taste je Taste skalierbar) und
  der Zustand `KeyBackgroundState.LAYOUT_SWITCH` (`state_single`) für die Tasten „?123“/„ABC“.
- `KeyboardParser.kt`: ruft im flachen Stil `FlatStyle.shortenNumberRow` auf.
- `KeyboardView.java`: Schrift für Beschriftung und Hinweise, rechtsbündige Hinweise,
  angehobene Kleinbuchstaben, jeweils nur im flachen Stil.
- `MainKeyboardView.java`: Sprachkürzel auf der Leertaste mit Punkt als Trenner und Schrift,
  nur im flachen Stil.
- `KeyboardIconsSet.kt`: Icon-Satz für den Stil „Flat“ (Material plus `FlatStyle.iconOverrides`).
- `SuggestionStripView.kt`: im flachen Stil ordnet `FlatStrip.arrange` die Leiste, angeheftete
  Tasten bleiben neben der Toolbar sichtbar, die linke Taste öffnet das Menü
  (`FlatStrip.toggleMenu`) statt zwischen Toolbar und Vorschlägen zu wechseln, jede
  Toolbar-Taste schließt es.
- Zwischenablage: `ClipboardHistoryView.kt` (im flachen Stil Kopfzeile, Abschnittstitel, Liste
  bis zum unteren Rand, keine untere Tastenreihe, keine Hardware-Ebene), `ClipboardAdapter.kt`
  (Kartenstil, quadratische Bilder), `ClipboardHistoryEntry.kt` und `ClipboardHistoryManager.kt`
  (Bilder über `ClipboardThumbnails`, Chip im flachen Stil), jeweils als Aufruf in
  `FlatClipboard`.
- `KeyboardActionListenerImpl.kt`: `KeyCode.AGENT_ACTIONS` öffnet im flachen Stil das Panel statt
  des Rads.
- `InputView.java`: zählt `agent_menu` zu den Overlays. `SettingsActivity.kt`: Extra
  `AgentPreferences.OPEN_SETTINGS_DESTINATION` öffnet „appearance“ oder „toolbar“ direkt.

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
- `DebugFlags.kt`: privater Crashhandler entfernt, damit keine rohen Fehlermeldungen oder
  LastLog automatisch in External/DE gespeichert werden. Androids Standardhandler bleibt.
- `AndroidManifest.xml`: `INTERNET`, `RECORD_AUDIO` (nur für das Diktat, siehe „Diktat“), kein
  Klartextverkehr, Backups aus
  (`agent_data_extraction_rules.xml`), CE als Standard, eigener FileProvider, der
  AndroidX-Startup-Initializer für HTTP ist entfernt.
- `SettingsNavHost.kt` und `MainSettingsScreen.kt`: Ziele und Menüeinträge der sechs
  Agent-Einstellungsseiten.
- App-Identität: `app/build.gradle.kts` (App-ID `dev.personal.agentkeyboard`, Version 0.1.0,
  APK-Name, Lint-Baseline `lint-baseline-upstream.xml`), `settings.gradle`
  (`:agent-core`), `clip_provider.xml` und `gesture_data.xml` (eigene Provider-Authorities,
  auch in `debug`/`debugNoMinify`), `donottranslate.xml` und `strings.xml` in
  `debug`/`debugNoMinify` (Namen „Agent Keyboard“ bzw. „Agent Keyboard Lab“).

Geänderte Upstream-Tests: `Shadows.kt` (IME-Info aus `BuildConfig.APPLICATION_ID`),
`InputLogicTest.kt` (zusätzliche erzeugte Fälle), `SuggestTest.kt` (Personalisierung im Test
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
- `AgentPreferences.kt`: Schlüssel der Leertastengeste, der Lernpause und des Extras für die
  KI-Einstellungen.
- Regeln: Ohne zuverlässig bestimmbare Auswahl wird nicht ersetzt. Editorwechsel, Neustart,
  geänderte Auswahl und zwischenzeitliche Eingaben machen eine ausstehende Ersetzung ungültig,
  auch wenn der Text gleich aussieht. Android hat kein universelles Compare-and-swap für fremde
  Textfelder, daher bleibt die Ersetzung bei Unklarheit gesperrt. Kopieren prüft dieselbe
  Schutzgrenze. Der Ablauf von Aktion bis Übernahme steht unter „Grundlagen“.
- Tests in `AT/`: `AgentPanelViewTest`, `AgentWheelViewTest`, `AgentSpaceGestureTest`,
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
- Texte: `agent_strings.xml` (Englisch in `values/`, Deutsch in `values-de/`), IDs und Attribute:
  `agent_ids.xml`, `agent_attrs.xml`.
- Tests: `KeyboardProfileStoreTest`, `SkillSettingsTest`, `WorkflowSettingsTest`.

## Flacher Stil

> Tastenstil „Flat“, Farbsatz „Flach schwarz“ und die Vorlage, die beides mit Größen einstellt.

- Auftrag: Aussehen nach fünf Screenshots des Owners (1200 px breit, 3,25 px je dp,
  Tastatur-View 793 px hoch: 39 px oberer Rand, 722 px Reihen, 32 px unterer Rand), als Ausgangspunkt für ein eigenes Design. Alle Maße stehen als
  Verhältnisse im Code, nicht als Pixel.
- `K/keyboard/FlatStyle.kt`: Konstanten (Farben, Zeilenskalierung, Höhe = 66 % der Breite),
  `colors()` (füllt jede `ColorType` über `AllColors`), `shortenNumberRow()` (Zahlenreihe
  niedriger, Buchstabenreihen entsprechend höher, Summe unverändert), `typeface()` (mittlere
  Strichstärke der Systemschrift), `lowercaseLift()`, `iconOverrides`, `LANGUAGE_SEPARATOR`.
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
  Emoji-Taste hat `"type": "character"` und damit den hellen Tastenhintergrund.
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
  Android 8 gar nicht gemerkt. Die Kopfzeile zeigt höchstens drei Tasten und nie „Schließen“;
  ohne sichtbare Leiste (Symbolleiste ausgeblendet) bleibt die Ansicht beim alten Aufbau mit
  unterer Tastenreihe.
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

## Unveränderter Upstream

> Was ohne Änderung von HeliBoard übernommen ist; nur nach Ordnern beschrieben.

- `K/latin/`: Eingabelogik, Wörterbücher, Vorschläge, Rechtschreibprüfung, Einstellungen-Backend.
- `K/keyboard/`: Tastatur-View, Layoutparser, Emoji, Zwischenablage.
- `K/settings/`: Compose-Einstellungen (bis auf die oben genannten Dateien).
- `app/src/main/jni/`: native Engine. `app/src/main/assets/`: Wörterbücher und Layouts.
- Upstream-Dokumente `README.md` (Fork-Hinweis oben), `layouts.md`, `CONTRIBUTING.md`,
  `AI_USAGE.md`, Lizenzen. Die Upstream-Tests in `app/src/test/` laufen mit `upstream-tests`.

## Build, Tests, CI, Docs-System

> Lokaler Build, Prüfbefehle, Workflows und die Docs-Prüfung.

- `tools/dev.py`: `python3 tools/dev.py doctor|build|core-tests|agent-tests|upstream-tests|lint`.
  `build` erzeugt `:app:assembleDebugNoMinify` für ARM64 (`--abi x86_64` für den Emulator) und
  legt APK plus SHA-256 unter `artifacts/` ab (nicht versioniert); die Datei heißt nach den
  enthaltenen ABIs, derzeit `…-universal.apk` (Fallstrick P19). `-Pandroid.injected.testOnly=false`
  verhindert, dass die APK als test-only markiert wird (Fallstrick P16). `agent-tests` läuft über
  `helium314.keyboard.agent.*`, die erzeugten `InputLogicTest`-Fälle und `BatchDeliveryTest`.
  Toolchain und Einrichtung: `docs/BUILD.md`. UI-Tests schreiben gerenderte Vorschauen nach
  `app/build/agent-previews/` (z. B. `builder-send-review.png`, nicht versioniert).
- Baseline: `upstream-tests` endet mit genau fünf geerbten Fehlern, Liste in
  `docs/VALIDATION.md` und `docs/HANDOFF.md` → Offene Punkte. Jeder weitere Fehler ist neu.
- CI: `.github/workflows/docs-check.yml` führt bei jedem Push den Docs-Check aus. Von Upstream
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

## Datei-Inventar

> Erzeugte Liste aller kartierten Dateien nach Ordner.

<!-- gen:inventory -->
<details><summary>Alle kartierten Dateien nach Ordner (210 files; generated, do not edit)</summary>

- `.claude/` (1): settings
- `.claude/hooks/` (1): session-start
- `.github/workflows/` (3): build-debug-apk, build-test-auto, docs-check
- `/` (1): settings
- `A/` (38): ActionRunJournal, AgentDocumentIO, AgentEditorController, AgentFileProvider,
  AgentKeyboardFrame, AgentPanelView, AgentPreferences, AgentStorageContexts, AgentWheelView,
  AndroidEditorAdapter, ApprovedActionRunner, CorrectionMemory, Dictation, EncryptedActionRunStore,
  FlatMenuView, FlatPanelStyle, FlatPreset, JournaledModelCall, JournaledRun, KeyboardProfileStore,
  LearningData, ManualWorkflowDraft, ModelSettings, OnDeviceSpeechEngine, RunSubjectCodec,
  SkillBindingStore, SkillLabels, ToolApprovalSummary, ToolLauncher, TouchCalibration, TouchModel,
  TypoMemory, WorkflowApprovalSummary, WorkflowBuilderContract, WorkflowConditionSummary,
  WorkflowDraftRunner, WorkflowInputSummary, WorkflowStore
- `A/providers/` (5): AnthropicProvider, CloudProviders, CloudTransport, GeminiProvider,
  OpenAiProvider
- `A/security/` (2): EncryptedCredentialStore, SecretCipher
- `AT/` (41): AgentDocumentIOTest, AgentPanelViewTest, AgentSpaceGestureTest,
  AgentStorageContextsTest, AgentWheelViewTest, AndroidEditorAdapterTest, ApprovedActionRunnerTest,
  ApprovedWorkflowRunnerTest, CalendarToolLauncherTest, CorrectionMemoryTest, DebugPrivacyTest,
  DictationSessionTest, DictationUiTest, EncryptedActionRunStoreTest, EncryptedCredentialStoreTest,
  FlatClipboardTest, FlatMenuTest, FlatPanelTest, FlatStyleRenderTest, FlatStyleTest,
  ForkIdentityTest, InsertAtCursorTest, JournaledModelCallTest, KeyboardProfileStoreTest,
  LearningDataTest, ManualWorkflowDraftTest, MoreProvidersTest, OpenAiProviderTest,
  SelectedTextCommitTest, SkillBindingStoreTest, SkillSettingsTest, TestRunJournal, ToolRunnerTest,
  TouchModelTest, TypoMemoryTest, WorkflowBuilderContractTest, WorkflowBuilderSettingsTest,
  WorkflowConditionSummaryTest, WorkflowDraftRunnerTest, WorkflowSettingsTest, WorkflowStoreTest
- `C/` (12): AiTextAction, ApplyOutcome, EditorSession, ExecutionPolicy, LocalTextActions,
  ModelProvider, PendingBatchGate, Profiles, SkillBinding, TextCondition, Tool, Workflow
- `CT/` (8): AiTextActionTest, EditorPolicyTest, InsertionAnchorTest, LocalTextActionsTest,
  PendingBatchGateTest, SkillBindingTest, ToolTest, WorkflowTest
- `K/keyboard/` (9): FlatStyle, Key, KeyDetector, KeyboardActionListener,
  KeyboardActionListenerImpl, KeyboardTheme, KeyboardView, MainKeyboardView, PointerTracker
- `K/keyboard/clipboard/` (3): ClipboardAdapter, ClipboardHistoryView, FlatClipboard
- `K/keyboard/emoji/` (1): EmojiSearchActivity
- `K/keyboard/internal/` (1): KeyboardIconsSet
- `K/keyboard/internal/keyboard_parser/` (1): KeyboardParser
- `K/keyboard/internal/keyboard_parser/floris/` (1): KeyCode
- `K/latin/` (9): App, ClipboardHistoryEntry, ClipboardHistoryManager, ClipboardThumbnails,
  DictionaryFacilitatorImpl, InputView, LatinIME, RichInputConnection, Suggest
- `K/latin/database/` (1): ClipboardDao
- `K/latin/define/` (1): DebugFlags
- `K/latin/dictionary/` (1): ExpandableBinaryDictionary
- `K/latin/inputlogic/` (2): InputLogic, InputLogicHandler
- `K/latin/settings/` (2): Defaults, SettingsValues
- `K/latin/spellcheck/` (1): AndroidSpellCheckerService
- `K/latin/suggestions/` (2): FlatStrip, SuggestionStripView
- `K/latin/utils/` (1): ToolbarUtils
- `K/settings/` (2): SettingsActivity, SettingsNavHost
- `K/settings/screens/` (10): AgentAiScreen, AgentDictationScreen, AgentLearningScreen,
  AgentProfilesScreen, AgentRunStatus, AgentSkillsScreen, AgentWorkflowsScreen, MainSettingsScreen,
  WorkflowBuilderDialog, WorkflowDraftProtection
- `K/settings/screens/gesturedata/` (1): Share
- `agent-core/` (1): build.gradle
- `app/` (1): build.gradle
- `app/src/debug/res/values/` (3): clip_provider, gesture_data, strings
- `app/src/debugNoMinify/res/values/` (3): clip_provider, gesture_data, strings
- `app/src/main/` (1): AndroidManifest
- `app/src/main/assets/layouts/functional/` (1): functional_keys_flat
- `app/src/main/res/drawable/` (20): btn_keyboard_key_flat, btn_keyboard_key_flat_pill,
  btn_keyboard_key_flat_rect, btn_keyboard_key_functional_flat, ic_agent_actions, ic_agent_dictate,
  ic_skill_1, ic_skill_2, ic_skill_3, sym_keyboard_clipboard_flat, sym_keyboard_delete_flat,
  sym_keyboard_go_flat, sym_keyboard_grid_flat, sym_keyboard_mic_flat, sym_keyboard_pen_flat,
  sym_keyboard_return_flat, sym_keyboard_shift_flat, sym_keyboard_shift_lock_flat,
  sym_keyboard_shifted_flat, sym_keyboard_smiley_flat
- `app/src/main/res/layout/` (1): main_keyboard_frame
- `app/src/main/res/values-de/` (1): agent_strings
- `app/src/main/res/values/` (8): agent_attrs, agent_ids, agent_strings, attrs, clip_provider,
  donottranslate, gesture_data, themes-flat
- `app/src/main/res/xml/` (1): agent_data_extraction_rules
- `app/src/test/java/helium314/keyboard/` (1): Shadows
- `app/src/test/java/helium314/keyboard/latin/` (2): InputLogicTest, SuggestTest
- `app/src/test/java/helium314/keyboard/latin/inputlogic/` (1): BatchDeliveryTest
- `scripts/` (3): docs_sync, docs_sync, install-hooks
- `tools/` (1): dev

</details>
<!-- /gen:inventory -->
