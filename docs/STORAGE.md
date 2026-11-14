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

## Update-Prüfung

- Kein Zugangsdatum: Das Release-Repository `Tra1c0/AgenticKeyboard` ist öffentlich, die App
  sendet keinen `Authorization`-Header und speichert keinen Token.
- CE-Einstellungen `agent_updates` (nicht in den exportierbaren Tastatureinstellungen): Schalter
  der täglichen Suche (`daily`, Standard aus), zuletzt gemeldeter Build (`notified_build`) und
  das letzte Ergebnis (`status`, `status_number`, `status_error`: nur Art, Build-Nummer oder
  Fehlercode, nie eine Antwort von GitHub) mit dem Zeitpunkt der letzten Prüfung (`checked_at`,
  Millisekunden, 0 = nie; seit 38h, für die Karte „Updates“ in System & Daten).
- Download nach `cacheDir/agent_update/update.apk` im CE-Kontext, höchstens 120 MB, Prüfsumme
  höchstens 4 KiB, Release-Liste höchstens 2 MiB. Die Datei wird nach der Übergabe an den
  Installer, bei jedem Fehler und beim nächsten Öffnen der Seite oder Lauf des Jobs gelöscht.
- Der tägliche Job (JobScheduler, ID `0x41475550`, persistiert über Neustarts) liest nichts vor
  dem ersten Entsperren; Dienst und Empfänger sind nicht `directBootAware`.

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

## Eigene Designs (23f2)

- Liste: Einstellung `agent_designs` in den Tastatureinstellungen (geräteverschlüsselter
  Speicher wie alle Tastatureinstellungen), ein JSON-Array mit höchstens 30 Einträgen: `id`
  (`d1`, `d2`, …), `name`, `style`, `keyBorders`, `colors` (Farbname → ARGB-Zahl für
  `background`, `keys`, `functional_keys`, `text`, `hint_text`, `accent`), `cornerRadius` (dp
  oder fehlt), `keyGap`, `keyHeight`, `font` (oder fehlt), `hasImage`. Ungültige Einträge
  (fremder Stil, unbekannte Farbe oder Schrift, ID mit anderen Zeichen) werden beim Lesen
  verworfen.
- Farbsatz: HeliBoards Format `user_colors_agent_design_<id>` (Liste von `ColorSetting`); er
  erscheint deshalb auch im Farbwähler von HeliBoard.
- Auswahl schreibt genau `MyDesigns.selectionValues()`: `theme_style`, `icon_style`,
  `theme_key_borders`, `theme_auto_day_night` = aus, `theme_colors`, den Farbsatz,
  `key_gap_scale_false_false`, `keyboard_height_scale_false_false` (Hochformat) und
  `agent_key_corner_radius`/`agent_key_font` (entfernt, wenn das Design sie nicht setzt).
  `agent_design_background` merkt, welches Design gerade sein Bild als Hintergrund hat,
  `agent_design_background_hash` den SHA-256 dieses Bildes.
- Bilder: `files/agent_designs/<id>.img` im geräteverschlüsselten Speicher (neben HeliBoards
  `custom_background_image`), Kopie der gewählten Datei. `pending.img` hält ein gewähltes Bild
  bis zum Speichern. Beim Auswählen wird das Bild über `custom_background_image` kopiert; ein
  eigenes Bild von vorher liegt solange als `background_before` daneben und kommt beim Wechsel
  zurück, aber nur, wenn der Hintergrund noch genau das Designbild ist (Hash). Hat der Owner
  inzwischen ein eigenes Bild gesetzt, bleibt dieses und wird beim nächsten Design die neue
  Sicherung. Löschen eines Designs entfernt Farbsatz, Listeneintrag und Bild.
- HeliBoards Sicherung erfasst die Einstellungen, aber nicht den Ordner `agent_designs/`.

## RGB-Beleuchtung (26, 29)

- Eine Einstellung `agent_rgb_lighting` in den Tastatureinstellungen (geräteverschlüsselt wie
  alle Tastatureinstellungen). Fehlt sie, gilt die Vorgabe (aus); die Einstellungsseite entfernt
  sie, wenn alles wieder auf der Vorgabe steht.
- Format seit Etappe 29 (`v=2`): Text `schlüssel=wert` durch `;` getrennt, höchstens 4096 Zeichen
  (vorher 1024), z. B.
  `v=2;on=1;pattern=WAVE_HORIZONTAL;curve=0:0,1:1;period=4000;reverse=0;width=1.0;palette=rainbow;`
  `colors=FF0050,00C8FF,9B4DFF;sat=100;bright=100;mix=100;layers=KEY_BACKGROUND;`
  `areas=LETTERS,FUNCTIONAL,SPACE,NUMBER_ROW;tap=NONE;reactive=0;glow=800;fps=30;saver=1;keys=smooth;sep=0`
  (`RgbSettings.encode`). Neu: `curve` (Kurve als `x:y`-Paare mit `,`, höchstens drei
  Nachkommastellen, 2–24 Punkte, x streng steigend mit mindestens 0,01 Abstand, Enden 0 und 1,
  Werte 0–1), `keys` (`smooth` oder `solid` = jede Taste einfarbig), `sep` (1 = eigene Looks je
  Element) und je Element mit eigenem Look dieselben Look-Felder mit Vorsilbe `k.` (Tasten),
  `b.` (Hintergrund), `l.` (Beschriftung), `s.` (Vorschläge), `t.` (Leistenknöpfe), z. B.
  `b.pattern=INK;b.curve=…;b.period=12000`. Muster neu: `INK`, `DIAMONDS`, `SLATS`, `HATCH`,
  `LED`, `TOPO`.
- Gelesen wird feldweise (`RgbSettings.parse`): Fehlendes, Unbekanntes und Ungültiges bekommt die
  Vorgabe dieses Feldes (in einem eigenen Look: den Wert des gemeinsamen Looks), Zahlen werden
  begrenzt (Dauer 0,5–30 s, Nachleuchten 0,2–5 s, Wellenbreite 0,25–4, Helligkeit 10–100 %,
  2–6 Farben, Bildrate 30 oder 60). Eine kaputte Kurve (zu viele Punkte, NaN, unlesbar) wird
  verworfen.
- Übernahme alter Einstellungen (Etappe 26, `v=1`): Ohne `curve` wird `wave` (und `steps`,
  2–12) zur passenden Vorlage-Kurve; der eine Look gilt für alle Elemente, Tasten weich. Die
  Einstellung wird dabei nicht umgeschrieben, erst die nächste Änderung auf der Seite speichert
  das neue Format.
- Designprofil: derselbe Text als Wert `agent_rgb_lighting`; beim Import feldweise begrenzt.
  Ein Designprofil von vor Etappe 26 hat den Wert leer und lässt die Beleuchtung unverändert;
  „Zurücksetzen“ schaltet sie aus.
- Sonst speichert die Beleuchtung nichts: Tipp-Positionen der Effekte (höchstens acht) liegen nur
  im Speicher und verfallen nach der Nachleuchtzeit und beim Ausblenden der Tastatur. Die
  Muster-Kacheln entstehen im Speicher und werden nicht auf die Platte geschrieben. Keine Texte,
  keine Logs.

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
- Verlauf (23g, Build 12): Kopie zuerst nach `files/agent_screenshot_pending/screenshot-<ID>`,
  dann umbenannt nach `files/clipboard/`. Reste dort (Tastatur mitten im Kopieren beendet) löscht
  dieselbe Prüfung, sobald sie älter als 10 Minuten sind, beim Ausschalten alle. Einstellung
  `agent_screenshot_history_ids`: IDs der schon für den Verlauf entschiedenen Screenshots
  (höchstens die neuesten 32); die frühere Einzel-ID `agent_screenshot_history_id` wird noch
  gelesen.

## Entpacken mitgelieferter Wörterbücher (Build 12)

- Temporäre Datei `files/dicts_extracting/<typ>.dict.<Thread>.tmp`, danach umbenannt nach
  `files/dicts/<Locale>/<typ>.dict`; bei einem Fehler gelöscht. Nie im Locale-Ordner (P80).

## Gemerkte Autokorrekturen (20a)

- Datei: SharedPreferences `agent_correction_memory` im gerätegeschützten Speicher (Direct Boot),
  wie die Lernwörterbücher der Engine. Schlüssel `rejected`: JSON-Liste von Einträgen
  „getippt␀korrigiert“, beides kleingeschrieben, älteste zuerst, höchstens 500.
- Entsteht nur mit eingeschaltetem „Rückgängig gemachte Korrekturen merken“. Kein Backup (die
  App schließt Backups aus), kein Protokoll. „Gemerkte Paare vergessen“ leert die Datei.

## Gelernte Tippfehler (20b)

- Datei: SharedPreferences `agent_typo_memory`, gerätegeschützter Speicher. Schlüssel `pairs`:
  JSON-Liste aus `{typed, lang, meant: [{word, form, total, contexts: {vorheriges Wort: Anzahl}}]}`,
  alles kleingeschrieben außer `form`, älteste getippte Form zuerst, höchstens 1000 Formen (über alle
  Sprachen) und 20 Kontexte je Wort. `lang` (seit 51) ist die Tastatursprache, in der das Paar gelernt
  wurde (z. B. `de`, `es`); Einträge ohne `lang` stammen von vorher, werden als `""` gelesen und
  gelten nur, wo ein Wörterbuch der aktuellen Sprache das gemeinte Wort kennt.
- Entsteht nur mit eingeschaltetem „Meine Tippfehler lernen“, kein Backup, kein Protokoll.
  „Gelernte Tippfehler vergessen“ leert die Datei.

## Gedächtnis (46)

- Datei: SharedPreferences `agent_phrase_memory` im gerätegeschützten Speicher (Direct Boot), wie
  die Lernwörterbücher. Bewusst lesbarer Text, keine Hashes: Der Owner sieht und löscht die
  Einträge auf der Seite „Was ich mir gemerkt habe“ (Abschnitt „Gedächtnis“).
  - `phrases`: erste Zeile `v1`, dann je Wortfolge `Gewicht;Tag;0|1;Text` (Gewicht mit drei
    Nachkommastellen, Stand am Tag der letzten Nutzung; Tag = Tage seit 1970 in UTC; `1` =
    angepinnt; Text 2–8 Wörter wie zuletzt getippt, mit Kommas, je Wort höchstens 32 Zeichen).
    Höchstens 2000 Folgen, darüber fallen die schwächsten nicht angepinnten; Halbwertszeit 30 Tage
    (angepinnte verblassen nicht, höchstens 200).
  - `own_words`: `v1`, dann `o;Wort` je eigenem Wort (höchstens 500) und `u;Zahl;Tag;Wort` je
    gezähltem Wort, das noch keins ist (höchstens 500, Nutzungen älter als 90 Tage zählen neu).
  - `shortcuts`: `v2`, dann `Zahl;STATUS;Fall;Kurzform;Erweiterung` (STATUS `COUNTING`, `READY`,
    `SHOWN`, `CREATED`, `DECLINED`; Fall `C`, wenn die Erweiterung nach einer großgeschriebenen
    Kurzform aufgezeichnet wurde, sonst `l`; höchstens 200). `v1` ohne Fall-Spalte (Test-Build bis
    zur zweiten Nachprüfung 46) wird weiter gelesen, als `l`. `CREATED` heißt seit der Nachprüfung 46
    „gemerkt“: Das Kürzel steht nur hier, nicht mehr im persönlichen Wörterbuch; seine Erweiterung
    ändert sich danach nicht mehr. Unplausible Paare verwirft das Lesen.
  - `excluded_apps`: Menge von Paketnamen (höchstens 50), in denen nichts gelernt und nichts
    angeboten wird.
- Geschrieben höchstens alle 30 Sekunden beim Tippen, beim Wechsel in ein anderes Feld und bei
  jeder Änderung auf der Seite, auf einem eigenen Hintergrund-Thread (Nachprüfung 46); beim
  Schließen der Tastatur bzw. Verlassen des Felds sofort (zweite Nachprüfung 46). „Alles
  vergessen“ macht noch wartende Schreibvorgänge ungültig. Rest-Risiko: Beim Tippen liegen bis zu
  30 Sekunden Gelerntes nur im Speicher und gehen verloren, wenn der Prozess in dieser Zeit endet. Im Speicher dazu: die Wortfolge des
  aktuellen Satzteils, die letzten 8 Commits mit Position (Abkürzungen), das zuletzt getippte Wort,
  die letzten 16 Wörter, die kein Hauptwörterbuch kannte, eine noch nicht gezählte Rücknahme und
  die schon gezählten Stellen („Wort@Position“, höchstens 200); beim Feldwechsel verworfen.
- Schalter `agent_memory` (Standard aus) und `agent_memory_snippets` (Standard an) in den
  Tastatureinstellungen. Ein angenommenes Abkürzungs-Angebot bleibt nur in `shortcuts`; ins
  persönliche Wörterbuch des Systems schreibt das Gedächtnis nichts (Nachprüfung 46, M1). Nur der
  Test-Build 20 legte Kürzel dort an (`UserDictionary`, Häufigkeit 250); solche Einträge bleiben,
  bis der Owner sie in „Persönliches Wörterbuch“ löscht, es gibt keine Migration.
- Gelöscht mit „Alles vergessen“, „Gelernte Wörter löschen“ und dem bestätigten Ausschalten von
  „Personalisierte Vorschläge“ (`phrases`, `own_words`, `shortcuts`; `excluded_apps` bleibt).
- Der Bausteinindex für passende Textbausteine (Wortmerkmale der eigenen Bausteine aus dem
  CE-Speicher) liegt nur im Speicher, nie auf der Platte; bei gesperrtem Gerät wird nichts gelesen.
- Entsteht nur mit eingeschaltetem Schalter und eingeschalteten „Personalisierten Vorschlägen“
  (zweite Nachprüfung 46), nie aus Passwortfeldern, Inkognito, Feldern ohne
  Lernen, ausgeschlossenen Apps, bei pausiertem Lernen oder gesperrtem Gerät. Kein Backup (die App
  schließt Backups aus), kein Protokoll, kein Export. „Alles vergessen“ leert `phrases`,
  `own_words` und `shortcuts`.

## Korrekturen im ganzen Feld (49)

- Datei: SharedPreferences `agent_field_corrections`, gerätegeschützter Speicher. Schlüssel
  `data`: JSON `{candidates: [{typed, lang, meant, session, position, seen}], learned: [{typed, lang,
  meant, seen}]}`; `lang` die Tastatursprache (seit 51, ohne `lang` wie bei 20b), `typed` kleingeschrieben, `meant` in der zu setzenden Schreibung, `session` eine
  Zufalls-/Zeitnummer des Felds (kein App-Name), `position` die Stelle im Feld, `seen` Zeit in ms.
  Höchstens 200 Kandidaten und 500 gelernte Paare, älteste zuerst verworfen.
- Entsteht nur mit „Meine Tippfehler lernen“, ohne Lernpause, Inkognito, Passwort- oder
  Nicht-lernen-Feld und bei entsperrtem Gerät. Kein Backup, kein Protokoll. Löschen: Seite „Was ich
  mir gemerkt habe“ (einzeln) oder „Gelernte Wörter löschen“ (alles); die Löschtaste nach einer
  Korrektur aus einem gelernten Paar entfernt es (`TypoMemory.forget`).
- Der Feldtext, mit dem verglichen wird (höchstens 1024 Zeichen vor und 300 nach der Schreibmarke),
  liegt nur im Arbeitsspeicher und wird beim Feldwechsel verworfen, nie gespeichert.

## Wörter aus anderen Sprachen (51)

- Datei: SharedPreferences `agent_foreign_words`, gerätegeschützter Speicher (Direct Boot), wie die
  Lernwörterbücher. Schlüssel `data`: JSON `{candidates: [{lang, word, from, session, position,
  seen}], remembered: [{lang, word, from, seen}]}`; `lang` die Tastatursprache, in der das Wort
  behalten wurde, `from` die Sprache, deren Wörterbuch es kennt, `word` in der Schreibung dieses
  Wörterbuchs, `session` eine Zeitnummer des Felds (kein App-Name), `position` die Stelle im Feld,
  `seen` Zeit in ms. Höchstens 200 Kandidaten und 500 gemerkte Wörter, älteste zuerst verworfen.
- Entsteht nur mit „Personalisierte Vorschläge“, ohne Lernpause, nicht in Inkognito, Passwort- oder
  Nicht-lernen-Feldern, nicht in vom Gedächtnis ausgeschlossenen Apps und nur bei entsperrtem Gerät.
  Kein Backup, kein Protokoll, kein Export. Löschen: Seite „Was ich mir gemerkt habe“ (einzeln),
  „Gelernte Wörter löschen“ und das bestätigte Ausschalten von „Personalisierte Vorschläge“ (alles).
- Nur im Arbeitsspeicher: das zuletzt getippte Wort mit Sprache und ob die Tastatursprache es kennt,
  eine wartende Rücknahme (Wort und Stelle, beim Feldwechsel verworfen) und die geöffneten
  Hauptwörterbücher der anderen aktiven Sprachen (nur lesend, aus `files/dicts` bzw. den
  mitgelieferten Wörterbüchern wie bei zweiten Sprachen).

## Trefferzonen (22a)

- Datei: SharedPreferences `agent_touch_model`, gerätegeschützter Speicher. Schlüssel `offsets`:
  JSON-Objekt Buchstabencode → `[dx, dy, Anzahl]`, dx und dy in Tastengrößen. Keine Texte, keine
  Reihenfolge von Berührungen. Die Kalibrierung hält ihre Rohdaten nur im Speicher und verwirft
  sie beim Schließen.
- Etappe 50, dieselbe Datei: Schlüssel `previous` (Basis vor der letzten Kalibrierung oder
  Beförderung, Format wie `offsets`; „Auf vorherige Kalibrierung zurücksetzen“ liest und löscht
  ihn). Schlüssel `dynamics`: JSON `{v: 1, next, promotions, spreadRef, speedRef, correctionRef,
  scenes: [{id, bucket, sessions, first, last, rx[9], ry[9], rn[9], dx, dy, spread, speed,
  corrections}], observations: [{day, bucket, scene, dx, dy, rx[9], ry[9], rn[9]}]}`. Offsets in
  Tastengrößen relativ zur Basis, je Region (3 × 3 Buchstabenbereich), `rn` Anzahl Berührungen,
  `bucket` MORNING/DAY/EVENING/NIGHT, `first`/`last`/`day` lokale Tagesnummer (Tage seit 1970),
  `speed` Buchstaben pro Minute, `corrections` Löschtasten je Buchstabe; fehlende Werte fehlen im
  JSON. Kommazahlen mit höchstens 3 Nachkommastellen (seit der Nachprüfung 50; ältere Werte werden
  unverändert gelesen). Geschrieben wird nur, wenn sich etwas geändert hat (Szene erkannt,
  Sitzungsende, Szene gelöscht, Kalibrierung), nicht bei jeder Taste. Eine Kalibrierung und
  „Auf vorherige Kalibrierung zurücksetzen“ rechnen die Szenen auf die neue Basis um und leeren
  `observations`. Höchstens 8 Szenen und 60 Beobachtungen (30 Tage). Keine Texte, keine Buchstaben, keine
  Reihenfolge, kein Ort, keine App. Die Kurzzeitschicht und die wartenden Berührungen des letzten
  Worts liegen nur im Speicher. Schalter `agent_adapt_touch_dynamic` in den Tastatureinstellungen
  (Standard aus). „Trefferzonen zurücksetzen“ und „Gelernte Wörter löschen“ leeren die Datei.

## Befehle (24a, 24b)

- Nur Einstellungen in den normalen Einstellungen der Tastatur: `agent_commands` (Schalter),
  `agent_command_prefix` (ein Zeichen), `agent_commands_disabled` (Namen abgeschalteter Befehle),
  `agent_calculator_style` (zuletzt gewählte Ausgabe des Rechners).
- Getippte Befehle, Rechnungen und Ergebnisse werden nicht gespeichert und nicht protokolliert.
  Der Rechner hält seine Eingabe nur im Speicher und leert sie beim Schließen.

## Zeichen-Kunst (53)

- Nur Einstellungen in den normalen Einstellungen der Tastatur (DE, ohne Inhalt):
  `agent_char_art_style` (zuletzt gewählter Stil, ein `ArtStyle`-Name) und
  `agent_char_art_width_<app>` (gewählte Breite je Ziel-App, Zahl 8–60, geschrieben beim Loslassen
  des Reglers). Mit HeliBoards Einstellungs-Sicherung werden sie wie alle Tastatur-Einstellungen
  mitgesichert. `agent_char_art_app` (zuletzt gewählte Ziel-App) wird seit der Nachprüfung 53 weder
  geschrieben noch gelesen: eine App ohne Profil bekommt immer „Andere“; ein alter Wert bleibt
  ungenutzt liegen.
- Zeichnung, Fotovorlage (höchstens 256 Pixel, nie kopiert), Vorschaubilder der Bilderleiste, der
  gelesene Feldtext und das Ergebnis liegen nur im Speicher, solange das Fenster offen ist;
  Schließen, Feldwechsel und das Ausblenden der Tastatur verwerfen sie. Nichts davon wird
  geschrieben, gesendet oder protokolliert.
- „Baustein“ speichert die Kunst als Textbaustein in `agent_snippets.json` (CE, kein Backup, wie
  alle Bausteine, siehe „Daten der Befehle 24c–24f“) mit `literal: true`; „Kopieren“ legt sie als
  normalen Text in die Zwischenablage (mit eingeschaltetem Verlauf landet sie dort wie jede Kopie).

## Symbole malen (27, 36)

- Schalter `agent_symbol_drawing` in den Tastatureinstellungen (Standard aus).
- Eigene Symbole: SharedPreferences-Datei `agent_symbols` im gerätegeschützten Speicher (DE, wie
  die Tastatureinstellungen, damit Malen auch vor dem ersten Entsperren geht), Schlüssel `own`.
  Format (`OwnInkSymbols`): erste Zeile `v1`, dann je Symbol eine Zeile: Text (`%`, Tab und
  Zeilenumbruch als `%XX`), dann je Zeichnung ein Tab und 32 Punkte `x,y,s` mit `;` getrennt
  (x, y in Tausendsteln der normierten Wolke, s = Strichnummer). Höchstens 30 Symbole,
  3 Zeichnungen, Text 1–8 Codepoints ohne Steuerzeichen, Lesegrenze 512 KiB; kaputte Zeilen
  werden übersprungen. Keine rohen Berührungspunkte. Nicht in HeliBoards Sicherung, kein Backup,
  kein Export.
- Persönliche Vorlagen (36): dieselbe Datei `agent_symbols`, Schlüssel `personal`, dasselbe Format
  `v1` (`PersonalInkSymbols`), aber höchstens 3 Zeichnungen je Symbol (die neuesten bleiben) und
  40 Symbole (das am längsten nicht geänderte fällt zuerst weg); eine Zeile mit mehr Zeichnungen
  wird beim Lesen übersprungen. Eine Zeichnung mit Punkt nur für ein Symbol mit Punkten. Geschrieben nur (a) beim
  Kalibrieren auf der Einstellungsseite und (b) bei der Wahl einer Alternative in der Leiste, dann
  nur mit Schalter `agent_symbol_learning` (Tastatureinstellungen, Standard aus) und
  „Personalisierte Vorschläge“ an, ohne Lernpause
  (`agent_learning_paused`), ohne Inkognito, bei entsperrtem Gerät und nie aus einem Passwortfeld.
  Löschen: einzeln oder alle auf der Seite „Symbole malen“, mit „Gelernte Wörter löschen“ und beim
  Ausschalten von „Personalisierte Vorschläge“. Keine rohen Berührungspunkte, nicht in
  HeliBoards Sicherung, kein Backup, kein Export.
- Die Pause zwischen zwei Strichen passt sich dem Tempo an (36): die letzten neun Lücken liegen nur
  im Speicher und gehen beim Beenden der Tastatur verloren.
- Gemalte Zeichnungen und Ergebnisse liegen nur im Speicher bis zur Erkennung bzw. bis zur
  nächsten Eingabe; die Wolke des zuletzt geschriebenen Symbols bleibt dort, solange seine Chips
  gelten (für das Lernen). Nichts davon wird protokolliert.

## Daten der Befehle 24c–24f

- `@snip`: CE-Datei `noBackupFilesDir/agent_snippets.json` (AtomicFile, höchstens 50 Bausteine,
  Lesegrenze 1 MiB), erst nach dem Entsperren lesbar, kein Backup, kein Export. Seit Etappe 42
  schreibt die App `storageVersion` 2: je Baustein `name`, `text` und nur wenn gesetzt `apps`
  (nicht leeres Array eindeutiger Paketnamen, höchstens 20) und `literal: true`. Version 1
  (`name`, `text`) wird weiter gelesen; solche Bausteine gelten als `literal` (Text wörtlich,
  keine Platzhalter), bis sie im Editor gespeichert werden. Keine Umschreibung beim Lesen; beim
  nächsten Schreiben entsteht Version 2 mit `literal: true` für die alten. Ältere Builds lesen
  Version 2 nicht (Einstellungen zeigen dann „nicht lesbar“). Test:
  `SnippetStoreTest.version1FilesStayReadableAndTheirSnippetsLiteral`.
- `@ort`: CE-SharedPreferences `agent_places`, Schlüssel `places` (JSON-Array `{name, address}`,
  höchstens 10), nur entsperrt, nicht in Exporten.
- `@clip`, vertrauliche Clips (`SensitiveClips`): seit der Nachprüfung 44/45 nur im Speicher des
  Tastaturprozesses, SHA-256 über ein je Prozess zufälliges Salt und den Cliptext, höchstens 200,
  älteste zuerst verdrängt; für jeden neuen Clip, der als vertraulich markiert ist oder in einem
  Passwortfeld kopiert wurde, auch mit Verlauf aus. Ein Neustart vergisst sie; das genügt, weil
  solche Clips seit Etappe 44 nie in die Verlaufsdatenbank kommen (der Tresor sortiert sie als
  Geheimnis), nur der aktuelle System-Clip kann noch einer sein. Alt (Etappe 24f bis 45):
  DE-SharedPreferences `agent_sensitive_clips` (Tastatur-Kontext), Schlüssel `salt` (16 Bytes, hex)
  und `hashes` (kommagetrennt). Beim ersten Start danach (`SensitiveClips.migrateLegacy` in
  `ClipboardHistoryManager.onCreate`; ohne lesbare Verlaufsdatenbank, etwa vor dem ersten
  Entsperren, bleibt die Datei für einen späteren Start) löscht die Tastatur die nicht angehefteten Verlaufseinträge,
  zu denen ein Hash passt, behält nur die Hashes angehefteter Einträge (deren Text steht ohnehin
  daneben in der Datenbank) und löscht alle anderen samt Datei, sobald keiner mehr übrig ist; neue
  schreibt sie nie. Kein Backup (`allowBackup=false`), kein Export.
- Zwischenablage-Wächter und Tresor (Etappe 44): nichts gespeichert. `ClipVault` hält Geheimnisse
  (höchstens 2 min, bis zum ersten Einfügen), Einmalcodes (10 min) und für Etappe 45 die übrigen
  neuen Clips (10 min) nur im Speicher des Tastaturprozesses, höchstens 20, dazu die Kopierzeiten
  schon eingefügter und als Geheimnis sortierter Clips (Zahlen, kein Text). Geheimnisse und Codes
  allein gehen nie in die Verlaufsdatenbank (`ClipboardDao`, DE, Tabelle `CLIPBOARD`); eine
  Nachricht mit Code bleibt dort als Text. Vor Etappe 44 gespeicherte, nicht angeheftete Geheimnisse
  werden einmal je App-Version daraus gelöscht (Start mit Verlauf an, sortiert im Hintergrund, oder
  beim Öffnen der Ansicht); erledigt merkt der Versionscode in den Tastatur-Einstellungen
  (`agent_clip_history_cleaned_version`, Zahl). Gelöschte Zeilen überschreibt SQLite mit Nullen
  (`PRAGMA secure_delete = ON` auf der schreibenden Verbindung), danach wird das WAL im Hintergrund
  gekürzt (`wal_checkpoint(TRUNCATE)`); nach einer solchen Bereinigung schreibt `VACUUM` die Datei
  einmal neu (im Hintergrund, nur bis 8 MiB Dateigröße, weil die Gestendaten in derselben Datei
  liegen). Mit Verlauf aus, bei gesperrtem
  Gerät, im Inkognito-Modus oder in Passwortfeldern wird nichts behalten. Ein Neustart des
  Prozesses vergisst alles. Kein Format, kein Export, kein Backup. Eingefügte private Texte
  (Tresor, Code-Chip, „Meine Daten“) merkt `InputLogic` je Feld nur als Position, Länge und
  gesalzenen Hash der ersten 32 Zeichen (höchstens drei, nur im Speicher, vergessen beim nächsten
  Feld; `PrivateRanges`).
- Meine Daten (Etappe 45): Datei `agent_my_data/data.sealed` in CE `noBackupFilesDir`
  (`credentialContext`, nie vor dem ersten Entsperren), höchstens 16 KiB, atomar geschrieben
  (`AtomicFile`). Format: Version 1 (Byte `1`) + 12 Byte IV + AES-256-GCM-Chiffrat mit AAD
  „AgentKeyboard/MyData/v1“, Schlüssel im Android Keystore (Alias `agent.mydata.v1`, nur angelegt,
  solange der Ordner leer ist). Klartext: JSON `{"v":1,"name":"…","emails":[…],"phones":[…],
  "addresses":[…],"ibans":[…]}`, je Liste höchstens 3 Einträge zu höchstens 200 Zeichen, eine Zeile.
  Leere Daten löschen die Datei; eine unlesbare oder veränderte Datei bleibt liegen und gilt als
  nicht lesbar. Die Tastatur liest sie erst, wenn ein Feld oder ein Hinweiswort eigene Daten
  braucht, und hält sie nur im Speicher, solange das Gerät entsperrt ist und die Tastatur zu sehen
  ist (`onFinishInputView` verwirft sie). Kein Backup
  (`allowBackup=false`, `noBackupFilesDir`), kein Export, nie in Profilen oder Tastatureinstellungen.
  Schalter „Feld- und Kontext-Vorschläge“: Tastatureinstellung `agent_field_suggestions` (Boolean,
  Vorgabe an, keine Daten).
- Kontext-Lernen (Etappe 45): DE-SharedPreferences `agent_context_weights` (Tastatur-Kontext),
  Schlüssel `weights`, Text `v1;<Tag>;<id>:<ART>=<Zahl>;…` (Tag = Tage seit 1970; `id` nur aus der
  festen Hinweis-Tabelle: look, here, media, phone, email, iban, address, code, name; ART aus
  `SuggestKind`; Zahl −0,4 bis 0,4 mit vier Nachkommastellen), höchstens 4096 Zeichen, also höchstens
  63 Einträge. Kein getippter Text, kein Clip, keine App, kein Feld. Geschrieben nur mit
  „Personalisierte Vorschläge“ an, ohne Lernpause, ohne Inkognito, entsperrt; Gewichte verblassen mit
  30 Tagen Halbwertszeit. „Gelernte Wörter löschen“ leert die Datei, ebenso (seit der
  Nachprüfung 44/45) das bestätigte Ausschalten von „Personalisierte Vorschläge“. Kein Backup, kein Export.
- `@contact`, `@url`, `@clip`, Bildschirmlesen: nichts gespeichert. Gefundene Kontakte, Adressen,
  Clip-Texte und gelesener Bildschirmtext liegen nur im Speicher, solange der Befehl vor der
  Schreibmarke steht.
- `@qr`: `files/agent_qr/qr-<uuid>.png` im DE-Speicher der Tastatur, über den eigenen Provider nur
  per Leseberechtigung freigegeben, gelöscht nach 10 Minuten bzw. beim nächsten QR-Code.

## Emoji-Mix (25a)

- Paartabelle: Asset `app/src/main/assets/emoji_mix_pairs.bin` (48 690 Byte, gzip; Format im Kopf
  von `tools/emoji_mix_table.py`), nur gelesen, einmal je Prozess im Hintergrund in den Speicher
  (Bitmap 48 KB plus 147 000 Byte Datumsindex). Herkunft und Prüfsummen: `docs/BUILD.md`.
- Geladene Bilder: `files/agent_emoji_mix/<links>_<rechts>.png` im DE-Speicher der Tastatur (die
  Namen sind die Codepunkte des Paars, kein getippter Text), höchstens 20 MB, die am längsten
  nicht benutzten zuerst gelöscht (`lastModified` bei jedem Lesen); Zwischendatei `.tmp`, beim
  Aufräumen entfernt. Ein Bild höchstens 2 MB. Über den eigenen Provider nur für das Einfügen
  bzw. Teilen mit Leseberechtigung freigegeben. Löschen: Einstellungen → Befehle →
  „Gespeicherte Mischungen löschen“. Kein Backup (`allowBackup=false`), kein Export. DE, damit es
  dieselbe Wurzel wie `@qr` und die Screenshot-Kopien hat; bei gesperrtem Gerät ist Emoji-Mix aus.
- Im Speicher: Zustand der zuletzt angefragten 12 Paare samt kleinem Vorschaubild (höchstens 320
  px), nicht auf der Platte. Einstellung `agent_emoji_mix_strip` (Tastatur-Einstellungen, Standard an).
- „Zuletzt gemischt“ (32): Einstellung `agent_emoji_mix_recent` (Tastatur-Einstellungen, DE, wie
  HeliBoards zuletzt benutzte Emojis): bis zu 12 Paarschlüssel (`<links>_<rechts>` als Codepunkte,
  kein getippter Text), durch Kommas getrennt, neueste zuerst. Nicht geschrieben im Inkognito-Modus
  (auch Passwort- und No-Learning-Felder) oder bei gesperrtem Gerät. Beim Lesen werden ungültige
  Einträge übergangen. Gelöscht mit „Gespeicherte Mischungen löschen“; ein Paar, dessen Bild der
  Cache gelöscht hat, bleibt in der Liste, wird aber nicht gezeigt, bis es aus der Liste fällt.
  Kleine Bilder der Reihe (höchstens 160 px, höchstens 12) nur im Speicher. Mit HeliBoards
  Einstellungs-Sicherung wird die Liste wie alle Tastatur-Einstellungen mitgesichert.
