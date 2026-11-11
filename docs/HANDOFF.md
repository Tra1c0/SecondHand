# Pausierter Arbeitsauftrag

**Am 11. November 2026 auf ausdrücklichen Nutzerwunsch pausiert.** Keine weitere
Entwicklung beginnen, bis der Nutzer die Fortsetzung beauftragt. Bericht:
[UEBERGABE-2026-11-11.md](UEBERGABE-2026-11-11.md).

Vor der Pause galt der Auftrag zur vollständigen selbstständigen Umsetzung in
abgegrenzten, getesteten und einzeln gesicherten Etappen. Bei neuer Fortsetzung
wieder daran anknüpfen; offene Geräte-/API-Prüfungen bleiben ehrlich offen.

## Zuständigkeit und Umfang

- Repository `/workspace/agent-keyboard`, Branch `work/agent-keyboard`.
- Genau ein schreibender Agent: root. Die beiden bestehenden Prüfer dürfen lesen:
  `architecture_review` (Astra High), `keyboard_basis_review` (Sol High).
- Android, privat, Kotlin. HeliBoards native Views/JNI bleiben; Compose-Einstellungen.
  Upstream und GPL-Attribution erhalten. Keine Upstream-Nachrichten/PRs/Issues.
- Keine öffentliche Community, Konten, Sync oder Backend. Austausch über Dateien.
- BYOK ausschließlich in der App; keine Schlüssel provisionieren oder erneut nach
  der bereits getroffenen Entscheidung fragen. Keine Live-API-Schlüssel vorhanden.
- Normal tippen bleibt offline. Keine Inhalte, Ergebnisse oder Zugangsdaten loggen.
  Lernen/Stiltraining nur opt-in; generierter/eingefügter Text ist kein Lern-Opt-in.

## Pausierter Stand: Etappe14c fertig, Emulator vorbereitet

Gesicherte letzte Commits:

- `d5beafde`: Workflow-Elternlauf und verknüpfte AI-Schritte (13f2).
- `6c26bfd1`: gezielter Reset beschädigter Workflow-Dateien und UI-Zustände (13g).
- `e87ff975`: strikt validierter Buildervertrag und Responses-Schema (14a).
- `be55e43b`: freigegebener, journalisierter Builder-Runner (14b).
- `71a4c959`: vollständige private Builder-Oberfläche/Entwurfsprüfung (14c).

14c ist vollständig implementiert und geprüft:

- `WorkflowBuilderDialog`: Prompt nur `remember`, maximal4096 Zeichen. Erst
  genaue Prompt-/OpenAI-/Modellvorschau, dann separater Sendeklick. Kein Editorinhalt
  wird ergänzt. Kontrollierte Textfelder benutzen unmaskierte Passwort-IME-Semantik,
  damit die Tastatur diese privaten Konfigurationsdaten nicht lernt.
- Parent friert Modell und dargestellte Workflow-Generation beim Öffnen ein.
  Gültiges Ergebnis öffnet einen vollständigen bearbeitbaren Entwurf. `proposal()`
  erhält Revision1; Speichern separat mit expectedRevision=null und ursprünglicher
  Generation. Kein automatischer Workflowstart.
- `ProtectWorkflowDraft` bleibt über Builder→ungespeicherte Entwurfsprüfung aktiv.
  Nur dieser Parent verwaltet Activity FLAG_SECURE; beide Dialoge zusätzlich
  SecureOn. Pause/Stop/Incognito verwerfen Prompt/Freigabe/Entwurf und canceln den
  Runner. Preference-Listener widerrufen synchron auf dem Android-Main-Thread.
- Astra fand anfangs die nachgelagerte Entwurfsprüfung ungeschützt; behoben und
  nachgeprüft. Name/Literal im privaten Entwurf ebenfalls ohne IME-Lernen.
- Zuletzt zusätzlich gehärtet: Save erfasst Proposal-Flag, Generation und Revision
  unveränderlich und verweigert busy oder editing!==initial. Alte Dismiss-/Builder-
  Abschluss-Callbacks prüfen ebenfalls Objektidentität. So kann ein vorgemerkter
  Speicherklick nach Discard/Incognito/reallow keinen verworfenen Entwurf speichern.
- 16 relevante Tests grün:6 Builder-UI,4 Workflow-UI,6 ManualDraft. SavedStateRegistry
  enthält weder Prompt noch ungespeicherten Namen. Native Vorschauen geprüft:
  `app/build/agent-previews/builder-send-review.png`, `builder-draft-review.png`.
- Astra-Nachprüfung auch der letzten Identitätsguards ohne Blocker. Keine Prüferedits.
- Abschließende Agent-Suite189/0, ARM64-Build und Lint grün. Vollständige
  Regression370Tests/genau5geerbteFehler,0Backgroundexceptions. Externe Links
  lieferten diesmal503statt404; identische Baselinefälle, keine Erweiterung.
  Logs `/workspace/.toolchains/stage14c-{tests,build,lint}.log` und
  `/workspace/.toolchains/stage14-regression.log`. Noch keine echte API-/Geräteprüfung.
- Beim Soft-Stop vorbereitet: x86_64-APK gebaut; Emulator37.2.13 und API35/AOSP-ATD
  x86_64 Image1.0.0 in /dev/shm/agent-keyboard-android installiert. AVD
  AgentKeyboardApi35 unter /dev/shm/agent-keyboard-avds erstellt; 1536MiB/2Kerne,
  480x800/SwiftShader/1GiB userdata, keine Snapshots. **Noch kein Boot gestartet.**
- Setup-/AVD-Kopie unter /workspace/.toolchains/agent-keyboard-emulator. /dev/shm
  ist flüchtig; SDK-Symlinks emulator/system-images zeigen dorthin und müssen ggf.
  neu aufgebaut oder als eigene Symlinks entfernt werden. Keine aktiven Builds,
  Tests oder Reviews. Nächster beauftragter Schritt: Boot-/Geräteprüfung, dann15.

## Danach weiterarbeiten

1. Vorbereitete Software-Emulatorumgebung booten und echte Android-Smoke-Tests
   durchführen. ARM64- und x86_64-APKs sind gesichert. Kein KVM. /dev/shm4.9GiB,
   Cgroup-RAM8GiB; eigene Gradle-Daemons wurden gestoppt. Emulatorinstallation
   hat2.5GiB RAM-Dateisystem belegt und war erfolgreich; Boot ist noch offen.
   Aktueller SDKmanager ist ein Android-CLI-Shim. Alte avdmanager-Java-Version
   erkannte den getrennten SDK-Root erst mit Symlinks im ursprünglichen SDK.
   Keine Paket-XMLs manipuliert. Logs emulator-{install,image-install,avd-create}.log
   unter .toolchains. Keine Geräteprüfung als bestanden werten.
2. Etappe15: erst deterministisches Testwerkzeug, dann optionale Kalenderübergabe
   mit ACTION_INSERT. Exakte Ziel-/Payloadvorschau, dauerhafter Intent vor Effekt,
   keine automatischen Wiederholungen. Ein Handoff bedeutet nicht „Termin gespeichert“.
3. Etappe16: Claude und Gemini sowie externe Dienste einzeln anbinden. Feste Ursprünge,
   OAuth/Freigaben, kein Modell darf selbst Fähigkeiten oder Credentials gewähren.
4. Etappe17: lokale Wörterbücher/Lernpause/Löschen/Export, danach getrenntes Stilprofil;
   Herkunft generierter/eingefügter Texte prüfen. Keine stillen Trainingsdaten.
5. Etappe18: echte lokale Diktierfunktion und echte optionale lokale Inferenz, getrennt.
   API31 On-device SpeechRecognizer, Verfügbarkeit und Mikrofonrecht in Activity,
   kein stiller Cloudfallback. Vorhandene VOICE-Taste delegiert bisher externe IME.
   Diktat braucht sicheren Insert-Anker statt gelockerter Selection-Replacement-Regeln.
   Inferenz braucht gepinnte echte Laufzeit und importiertes kompatibles Modell;
   keine Fake-Antworten oder automatischen Downloads. Runtime-Doku neu prüfen.

## Implementierungsgrenzen erhalten

- Main-Manifest standardmäßig CE. App bewahrt rohen CE-Kontext und wickelt die alte
  Engine ausdrücklich in DE. Legacydateien bleiben DE; neue Keys/Agent-Konfiguration
  CE/noBackup. Keine versteckten Android-APIs oder Credential-Exporte/Backups.
- EditorToken: Besitzer-UUID+Generation+Revision, einmalige Autorität. ModelRequest
  enthält ausschließlich String-Daten, keine Tokens/InputConnection. Frischer
  Auswahl-Snapshot, Privacy-Prüfung vor Lesen, vor Übernahme nochmals Vergleich.
- Native Ersetzung genau ein Commit, kein Delete+Insert. UNKNOWN verwirft Caches
  und blockiert Eingabepfade bis autoritativer Refresh/Restart. Generierte Übernahme
  unterdrückt Lernen bis neue Editorsitzung. Copy prüft denselben aktuellen Schutz.
- InputLogic/RichInputConnection/Touch nur eng und unabhängig geprüft ändern.
  Swipe PendingBatchGate hält Worker- und UI-Tail gemeinsam unter Epoch/Lock gesperrt.
- AgentKeyboardFrame-Overlay exakt Keyboardgröße; Hintergrundtasten sind aus
  Accessibility entfernt. Normaler Input unter Overlay/Unknown bleibt gesperrt.
- Alle Sendefreigaben einmalig und an unveränderliche Quelle/Definition gebunden.
  Job LAZY installieren, bevor synchroner/reentranter Abbruch eintreten kann.
  Generation vor cancel erhöhen; alte Antworten dürfen neue Läufe nie beenden.
- Nicht abbrechbare Journal-IO kann Gesamtbudget überschreiten; spät veröffentlichen
  bleibt trotzdem gesperrt. Keine Wiederaufnahme/automatische Ersetzung nach Neustart.

## Workflows, Skills, Builder und Laufdaten

- Workflowformat3: maximal8 Schritte/3AI, nur rückwärtige StepResult-Verweise oder
  ursprüngliche Auswahl. V1/V2 ausdrücklich maximal2; v1 ohne Bedingungen.
  NON_EMPTY/CONTAINS_LITERAL/LENGTH_AT_LEAST plus Negation; false reicht Eingabe
  unverändert weiter. Analyse-Herkunft sperrt Ersetzung auch in Nachfolgeschritten.
- Source/AIinput8192, Result16384, Gesamtdaten32768 UTF-16-Einheiten,95s Budget.
  Nur OpenAI im Runner verfügbar; andere Provider werden vor Anfragen abgewiesen.
- WorkflowStore:10 Definitionen/32KiB, CE/noBackup/AtomicFile, generation UUID für
  jede Mutation einschließlich identischer/leerender Imports. Save/Delete/Replace
  verlangen die aus der dargestellten Konfiguration erfasste Generation. Kein ABA.
- Recovery: nur erneut unlesbare Datei mit identischem SHA256 zurücksetzen,
  zusätzliche App-Bestätigung. Höchstens1MiB Fingerprintlesen; größere Dateien
  unangetastet. Gesunde, reparierte, ersetzte, fehlende, gesperrte Dateien ablehnen.
- Skillformat2: genau WORKFLOW braucht kanonische UUID, Builtins verbieten Referenz.
  Slots1–3 speichern nur Alias+Referenz; fehlende IDs bleiben ohne Fallback erhalten.
  UI `key(binding)` und `store.set(saved,binding)` schützen den dargestellten Slot.
- UI-Renderzustände direkt im Elternscope erfassen (`displayed…`); reine null→null-
  Konfiguration hatte zuvor Recovery-/Fehleranzeige nicht aktualisiert. Skill-Screen
  nutzt denselben Capture-Ansatz. Kein allgemeiner Compilerfehler behauptet.
- Buildervertrag: feste Responses text.format JSONSchema, geschlossene Objekte,
  alle Felder required, verschachtelte null/condition und Local/AI anyOf. Root nur
  name/steps; keine Modell-ID/Revision/Provider/Modell/Customprompts/Tools/URLs.
  App vergibt UUID/rev1 und bindet AI an freigegebenes OpenAI-Modell. Vollständige
  Validierung durch bestehenden Codec/Core. Prompt4096, Ausgabe weiterhin16384.
- WorkflowDraftRunner: prepare ohneIO, aktuelle identitätsgebundene Freigabe vor
  LAZY-Start verbrauchen,65s Budget. Provider erst nach dauerhaftem Builder-Intent,
  nur OpenAI+workflowDrafts-Fähigkeit; Generate+Validierung vor Journalabschluss.
  Kein Editor-/Save-/Aktivierungszugriff. Host liefert Main/Privacy/Lifecycle.
- Laufjournal verschlüsselt, nur Metadaten,20 Einträge/7Tage; aktive nicht verdrängen.
  Subject TextAction(optional WorkflowStepLink)/Workflow(defId,revision)/Builder.
  JSONschema2 liest v1action ausdrücklich; AES-Alias/AADv1 unverändert. Elternlauf
  vor Workflow, Kindaufrufe referenzieren Parent/Definition/Revision/Step-ID.
  Terminal vor Ergebnisanzeige. Timeout/unklare Antworten UNKNOWN, lokaler Cancel
  CANCELLED; keine Source/Results/Prompts/Tokens gespeichert. Clear+spätes Finish
  erzeugt nichts neu. Locked-Finish-Cleanup darf keinen CE-Pfadgetter anfassen.
- SDK21/22: rein lokale Workflows ohne behauptete Journalpersistenz verfügbar,
  Cloud ohne sicheren Speicher gesperrt. Keine Provider-/Transportinitialisierung
  durch lokale Arbeit. Historische Workflow-Namen werden nicht gespeichert.

## Transport und Credentials

- AES256GCM, getrennte Keystore-Aliase/AAD, kein Regenerieren bei vorhandenem Ciphertext.
  Zugangsdaten nur App-BYOK, maskiert, kein saved state, Screenshot-/Unlockschutz.
- OpenAI feste Responses-URL, store:false/stream:false,2048 bzw4096 Buildertokens,
  höchstens1MiB Antwortbytes/16384Text. Nur vollständig abgeschlossene Texte;
  Refusal/Incomplete/Tools keine Ergebnisse. Fehlerkörper weder anzeigen noch loggen.
- OkHttp5.3.2 Android-AAR +startup-runtime1.2.0, StartupInitializer gezielt entfernt;
  OkHttp.initialize nach Send aufIO. connect/read/write10s, call45s, Redirects und
  Retries aus. Zusätzlich one-shot RequestBody: sonst replay bei503+Retry-After0.
  Loopback-Test belegt einen POST. Normales Tippen initialisiert Transport nicht.
- Aktuelle Provider-/Runtime-Verträge und offizielle Quellen: `PROVIDERS.md`.
  gpt-6-luna ist geprüfter Default, frei änderbar; reale API-Zugänge nicht getestet.

## Werkzeuge, Baseline und bekannte Testfallen

- `.toolchains` neben Repo: JDK17.0.21+9, AndroidSDK37.0, NDK28.0.13004108,
  BuildTools36/37, GradleCache. Test-JVM ist systemseitige Java21.
- `python3 tools/dev.py doctor|build|core-tests|agent-tests|upstream-tests|lint`.
  `build --abi x86_64`; Default ARM64. APKs+SHA getrennt unter `artifacts/`, ignoriert.
- Gradle Test forkEvery1 pro Klasse: Robolectric hielt temporäre Wörterbuchkopien
  bis Workerende und verursachte sonst ENOSPC. Keine Assertions/Tests ausgeschlossen.
- Vollregression zuletzt370 Tests, genau fünf ursprüngliche Baselinefehler, keine
  Hintergrundexception. Die fünf sind: InputLogicTest#insertLetterIntoWordHangulFails,
  StringUtilsTest#detectEmojisAtEndFails, XLinkTest#layoutsLinks/#readmeLinks (404),
  XLinkTest#knownDictionaries (externes Format). Zeitweise zwei weitere externe504;
  Baseline nicht erweitern. API-/Gerätetests sind keine automatischen grünen Checks.
- Alte externe Crash-Dateien/LastLog-Erfassung entfernt; Hintergrund-NPE durch
  späten PackageManager-Startlog korrigiert (BuildConfig statt Runtimeabfrage).
- Neue native Tests brauchen Handler-shadow Boolean true, nicht Unit, sowie
  synchronisierte Runnable-Queues; awaitAgentUi liefert echte Callbacks zu.
- JUnit4-Testmethoden müssen Unit liefern; runBlocking+assertFails kann versehentlich
  Throwable zurückgeben. Compose isEnabled() als Matcher in onAllNodes verwenden.
- Menüpunkte ggf. performScrollTo vorClick. TextField-Label gehört zur gemergten
  Textsemantik: leeren Inhalt gezielt über SemanticsProperties.EditableText prüfen.
- Vor roher ShadowDialog-Bitmapaufnahme erst sichtbaren Dialog assertieren/idle.
  captureToImage wartete zuvor im Robolectric-Window auf ausbleibenden Redraw;
  native Canvas-Aufnahme liefert prüfbare Vorschauen ohne Assertions zu schwächen.
- Commit: `git -c user.name=Codex -c user.email=codex@local commit …`.
