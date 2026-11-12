# Speichergrenzen

Die APK verwendet als Manifest-Voreinstellung Credential Encrypted Storage (CE).
`App.attachBaseContext` bewahrt diesen ursprünglichen Kontext auf, bevor der
bestehende Tastaturcode ausdrücklich einen Device Encrypted Context (DE) erhält.
Auch LatinIME, Rechtschreibdienst, Einstellungen und Emoji-Suche verwenden für
ihren bisherigen Datenpfad DE. Beide FileProvider verwenden denselben DE-Kontext
wie ihre Produzenten. Es werden keine privaten Android-APIs oder erratenen
Dateisystempfade benutzt.

Das ist notwendig, weil Android zwar die öffentliche Methode zum Erzeugen eines
DE-Kontexts anbietet, die umgekehrte Methode aber nicht Teil des öffentlichen SDK
ist. Ein globaler DE-Standard kann deshalb nicht für die neuen Zugangsdaten gelten.

## Zugangsdaten

- Zugriff ausschließlich über den aufbewahrten CE-Kontext und `noBackupFilesDir`.
- Nur feste Slots (`OPENAI`, `ANTHROPIC`, `GEMINI`; je eine Datei und eigener authentifizierter
  Slotname); keine Schlüssel als Dateinamen, Aliase oder Logbestandteile.
- AES-256-GCM mit zufälligem 96-Bit-IV, 128-Bit-Tag und authentifiziertem Slotnamen.
- Der Android Keystore verwahrt den AES-Schlüssel. Bearer-Tokens liegen nur als
  authentifizierter Geheimtext in begrenzten, atomar geschriebenen Dateien.
- Keine automatische Neuerzeugung eines fehlenden Schlüssels bei vorhandenen
  Geheimtextdateien. Beschädigungen führen zu einem neutralen Fehler.
- Sperrstatus und Benutzerentsperrung werden vor jedem Zugriff geprüft. Fehlende
  Systemdienste bedeuten keinen Zugriff. Vor Android 6 ist die Funktion gesperrt.
- Weder automatische Backups noch die bisherigen manuellen ZIP-Exporte oder
  FileProvider-Wurzeln umfassen diesen Ordner. Modellnamen liegen in separaten
  CE-Einstellungen, nicht in den exportierbaren Tastatureinstellungen.

Das Eingabeformular lädt gespeicherte Schlüssel niemals zurück. Eingaben sind
maskiert, nicht Teil des gespeicherten Activity-Zustands und werden bei ON_STOP,
Verlassen oder Speichern geleert. Das Fenster bleibt mit FLAG_SECURE geschützt.
Mutable Kopien werden nach Gebrauch gelöscht; unveränderliche Strings und
interne Kopien in Android/HTTP lassen sich nicht zuverlässig aus dem RAM löschen.

## Prüflimits

JVM-/Robolectric-Tests verwenden für den Algorithmus einen injizierten AES-Schlüssel.
Sie prüfen Manipulationen, Slotbindung, Größenlimits, Sperren und Dateigrenzen,
ersetzen aber keinen Hardware-Keystore- und Neustart-vor-Entsperren-Test.
Die verwendete Robolectric-Version unterstützt API 21 nicht; die Capability-Grenze
ist separat geprüft, Android-5-Gerätekompatibilität bleibt offen.

Vor Freigabe prüfen: Neustart ohne erstes Entsperren → Tippen bleibt möglich,
keine Geheimniszugriffe; danach Entsperren → explizite Aktion kann Zugangsdaten
verwenden. Außerdem Clipboard-Dateifreigabe und Emoji-Wörterbuch auf einem Gerät
prüfen, weil beide denselben DE-Pfad wie die Eingabe-Engine benötigen.

## Auftragsstatus (12a)

`agent_runs/status.sealed` liegt in CE/noBackup, mit eigenem AES-GCM-Alias
`agent.runs.v1` und AAD `AgentKeyboard/ActionRuns/v1`. Nur begrenzte Metadaten,
keine Inhalte oder Ergebnisse. Alte aktive Prozesse werden UNKNOWN; es gibt
keinen Scheduler und keine automatische Wiederholung. Höchstens 20 Einträge,
terminale Einträge sieben Tage. Löschen ist ausdrücklich über die Oberfläche
vorgesehen; beschädigte Dateien werden nicht still überschrieben.

Seit 12b wird der Intent vor dem echten Providerstart gespeichert. RAM enthält
nur die IDs tatsächlich aktiver Aufträge, damit bei gesperrtem Gerät gescheiterte
Abschlüsse nach Entsperren UNKNOWN werden. Die Registry ist kein Retry-Puffer und
enthält weder Texte noch Ergebnisse. Statusmeldungen sind in KI-Einstellungen
lesbar und löschbar.

## Workflow-Konfiguration (13b1)

`agent_workflows.json` liegt in CE/noBackup. Es enthält ausschließlich die
zugelassenen Konfigurationsfelder, keine Quelltexte, Ergebnisse oder Credentials.
Speicherwrapper `storageVersion:1`, zufällige `generation`, `configuration` mit
Austauschformat1. Jede Mutation prüft die gelesene Generation unter demselben Lock
und ersetzt sie durch einen neuen UUID-Token; auch identischer Import verhindert
so veraltetes Überschreiben. Die Generation wird nicht exportiert. Höchstens zehn
Abläufe, Austauschdatei32.768Bytes plus begrenzter Speicherwrapper.

Workflow-Austauschformat2 ergänzt ausdrücklich `condition:null` oder ein streng
validiertes Objekt mit `predicate`, `literal`, `minimumLength`, `negated`. Format1
wird ohne Bedingung gelesen; zusätzliche v2-Felder werden in v1 weiterhin abgelehnt.
Der lokale Speicherwrapper bleibt storageVersion1 mit Generationstoken. Neue
Schreibvorgänge/Exporte verwenden Format2, ohne Daten oder Berechtigungen zu ergänzen.

Skill-Austauschformat2 ergänzt `workflowId:null` für Builtins oder eine kanonische
UUID genau bei `action:workflow`. Format1 migriert ausschließlich zu Builtin/null;
neue Fähigkeiten/Felder unter alter Version bleiben unzulässig. Es werden keine
Workflowinhalte oder Zugangsdaten in Skill-Dateien eingebettet.

Seit Etappe 15 schreibt das Journal Schema3: zusätzlicher Subject-Typ `tool` mit genau den Feldern
`kind` und `tool` (feste Werkzeug-ID), ohne Provider, Modell oder Nutzdaten. Schema2 wird weiter
gelesen; ein Schema-2-Dokument mit `tool`-Eintrag wird als Ganzes abgewiesen und nicht überschrieben.

Laufmetadaten verwenden seit13f1 JSONschema2 mit RunSubject(TextAction, Workflow,
WorkflowBuilder), optionalem WorkflowStepLink und rollenspezifischen Pflichtfeldern.
Schema1-action wird ausdrücklich in TextAction migriert. Kryptokontext/Alias/AADv1
bleiben unverändert, damit alte authentifizierte Dateien lesbar bleiben. Keine
Prompts, Eingaben, Resultate, Zugangsdaten oder Editor-Tokens in irgendeinem Subject.

## Vorlage „Flacher Look“ (19a)

- Datei: SharedPreferences `agent_flat_preset_backup` im Standardspeicher der App (wie
  `agent_keyboard_profiles`).
- Inhalt: `present` (Boolean), `keys` (Menge der gesicherten Schlüssel) und je ersetzter Einstellung ein Eintrag `value:<Schlüssel>` mit dem
  Wert vor dem Anwenden (String, Boolean oder Float). Fehlt der Eintrag, war die Einstellung
  vorher nicht gesetzt und wird beim Wiederherstellen entfernt.
- Nur Darstellungs- und Layout-Einstellungen (Liste: `FlatPreset.values()`), keine Texte,
  Lerndaten oder Zugangsdaten. Wird beim Wiederherstellen geleert.

## Screenshot-Kopien (23d)

- Ort: `files/agent_screenshot/screenshot-<ID>.<Endung>` im gerätegeschützten Speicher (DE) der
  Tastatur, also schon vor dem ersten Entsperren lesbar; über den eigenen FileProvider
  (`clipboard_provider_authority`) für die Ziel-App freigegeben. Entsteht nur beim Tipp auf die
  Pille. Deshalb kurz gehalten: Kopien älter als 10 Minuten löscht jede Prüfung auf neue
  Screenshots, auch ohne Berechtigung, beim Ausschalten des Schalters werden alle gelöscht. Grenze:
  Eine App, die das Bild erst nach mehr als 10 Minuten liest (etwa ein späterer Upload aus
  einem Entwurf), findet es nicht mehr. Kein Backup, kein Protokoll.
- Einstellungen: `agent_screenshot_chip` (Schalter) und `agent_screenshot_dismissed` (ID des
  zuletzt benutzten oder geschlossenen Screenshots).

## Gemerkte Autokorrekturen (20a)

- Datei: SharedPreferences `agent_correction_memory` im gerätegeschützten Speicher (Direct Boot),
  wie die Lernwörterbücher der Engine. Schlüssel `rejected`: JSON-Liste von Einträgen
  „getippt␀korrigiert“, beides kleingeschrieben, älteste zuerst, höchstens 500.
- Entsteht nur mit eingeschaltetem „Rückgängig gemachte Korrekturen merken“. Kein Backup (die
  App schließt Backups aus), kein Protokoll. „Gemerkte Paare vergessen“ leert die Datei.

## Gelernte Tippfehler (20b)

- Datei: SharedPreferences `agent_typo_memory`, gerätegeschützter Speicher. Schlüssel `pairs`:
  JSON-Liste aus `{typed, meant: [{word, total, contexts: {vorheriges Wort: Anzahl}}]}`, alles
  kleingeschrieben, älteste getippte Form zuerst, höchstens 1000 Formen und 20 Kontexte je Wort.
- Entsteht nur mit eingeschaltetem „Meine Tippfehler lernen“, kein Backup, kein Protokoll.
  „Gelernte Tippfehler vergessen“ leert die Datei.

## Trefferzonen (22a)

- Datei: SharedPreferences `agent_touch_model`, gerätegeschützter Speicher. Schlüssel `offsets`:
  JSON-Objekt Buchstabencode → `[dx, dy, Anzahl]`, dx und dy in Tastengrößen. Keine Texte, keine
  Reihenfolge von Berührungen. Die Kalibrierung hält ihre Rohdaten nur im Speicher und verwirft
  sie beim Schließen.

## Befehle (24a, 24b)

- Nur Einstellungen in den normalen Einstellungen der Tastatur: `agent_commands` (Schalter),
  `agent_command_prefix` (ein Zeichen), `agent_commands_disabled` (Namen abgeschalteter Befehle),
  `agent_calculator_style` (zuletzt gewählte Ausgabe des Rechners).
- Getippte Befehle, Rechnungen und Ergebnisse werden nicht gespeichert und nicht protokolliert.
  Der Rechner hält seine Eingabe nur im Speicher und leert sie beim Schließen.

## Daten der Befehle 24c–24f

- `@snip`: CE-Datei `noBackupFilesDir/agent_snippets.json` (AtomicFile, `storageVersion` 1,
  höchstens 50 Bausteine, Lesegrenze 1 MiB), erst nach dem Entsperren lesbar, kein Backup, kein
  Export.
- `@ort`: CE-SharedPreferences `agent_places`, Schlüssel `places` (JSON-Array `{name, address}`,
  höchstens 10), nur entsperrt, nicht in Exporten.
- `@contact`, `@url`, `@clip`, Bildschirmlesen: nichts gespeichert. Gefundene Kontakte, Adressen,
  Clip-Texte und gelesener Bildschirmtext liegen nur im Speicher, solange der Befehl vor der
  Schreibmarke steht.
