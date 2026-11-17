# Prüfbericht 2026-11-16: Scan über alle Bereiche

Anlass: Der Owner bat am 2026-11-16 um eine Suche nach „Lücken, Verbesserungen und Fehlern“ über
alle Bereiche. Stand des Codes: `585bbb8` (Build 27) bzw. `15767da`.

Methode: Acht Prüf-Agenten haben je einen Bereich nur gelesen, nichts geändert und nichts
ausgeführt. Der Lead hat die schwersten Befunde im Code nachgelesen; diese tragen ein „L“ in der
Spalte „Geprüft“. Nichts in diesem Bericht ist auf einem Gerät gemessen oder ausprobiert. Zeiten und
Größenordnungen sind Schätzungen aus dem Code.

Die Spalte „Plan“ nennt die Etappe, in der der Befund behoben wird (Nachweis dann in
`docs/VALIDATION.md`), „offen“ für Befunde ohne Etappe (sie stehen auch in `docs/ROADMAP.md`) oder
„bleibt“ mit Grund. Dieser Bericht wird bis zum Ende von Runde 2 gepflegt und danach nicht mehr. Stand
2026-11-17: Runde 1 (Etappen 57 bis 63) ist gebaut und geprüft; Runde 2 (64 bis 70) läuft.

## Das Wichtigste

1. **Die ausgelieferte APK ist eine Debug-Variante** (`debugNoMinify`, `isDebuggable = true`, kein
   R8). Damit ist der Debug-Modus standardmäßig an: Jedes abgeschickte Wort steht im
   Systemprotokoll, die Tastatur stürzt bei fünf Ausnahmefällen absichtlich ab, und Android führt
   die App ohne volle Optimierung aus. Nachtrag des Leads: Auch der native Teil der Vorschlags-Engine
   (Wörterbuchsuche, Wischtippen) war dadurch ohne Optimierung übersetzt (`NDK_DEBUG=1`), wahrscheinlich
   der größte Einzelgrund für träge Vorschläge. Etappe 62.
2. **Bei jeder Aktualisierung der Vorschlagsleiste laufen Anfragen an Android und an die App**:
   Sperrzustand, Zwischenablage, Text hinter der Schreibmarke, in langen Texten nach dem Löschen
   auch der Text davor. Betroffen sind vor allem E-Mail-, Telefon- und Code-Felder. Etappe 64.
3. **Die Beleuchtung zeichnet weiter, wenn ein Fenster die Tasten verdeckt** (Aktionspanel,
   Rechner, Zeichen-Kunst). Etappe 70.
4. **Tippfehler werden zu „eigenen Wörtern“**: In Feldern ohne Autokorrektur zählt das Gedächtnis
   jeden dreimal abgeschickten Vertipper als gewolltes Wort und korrigiert ihn danach nirgends
   mehr. Etappe 66.
5. **Der Zwischenablage-Verlauf speichert auch im Inkognito-Modus und bei gesperrtem Gerät.**
   Etappe 65.
6. **Eine fehlgeschlagene Wiederherstellung einer Sicherung kann Tastaturdaten löschen**
   (Tippfehler im Code von HeliBoard). Etappe 65.
7. **Die Veröffentlichung eines Builds kann still ausfallen**, und Zertifikat und Build-Nummer der
   fertigen APK werden nicht geprüft. Etappe 63.
8. **„Personalisierte Vorschläge“ ausschalten löscht nicht alles Gelernte**, obwohl Dialog und
   Anleitung das sagen. Etappe 68.

## 1. Leistung beim Tippen

| Nr. | Befund | Schwere | Stelle | Geprüft | Plan |
| --- | --- | --- | --- | --- | --- |
| P1 | In Feldern mit Ziel (E-Mail, Telefon, Zahl, Code, Name, Adresse) fragt jede Leistenaktualisierung den Sperrzustand, den Text hinter der Schreibmarke und die Zwischenablage ab und sortiert den Clip zweimal ein. | hoch | `FieldSuggestionChips.kt:131–156`, `ClipVault.kt:141–158` | L | erledigt (Etappe 64b): Text hinter der Schreibmarke nur beim ersten Bauen je Feld und beim Tipp, Clip und Einordnung einmal je Clip; die Pille liest ihren Clip in `ClipboardHistoryManager` weiterhin selbst (gehört zu Etappe 65), nicht auf einem Gerät gemessen |
| P2 | Der Sperrzustand wird nirgends zwischengespeichert: je Wort und je Leerzeichen mehrere Systemaufrufe, auch im normalen Textfeld. | mittel | `Memory.kt:62`, `MemoryChips.kt:62`, `ForeignWords.kt:122–124` | L | erledigt (Etappe 64b): gemeinsamer Sperrzustand `LockState`, nicht auf einem Gerät gemessen |
| P3 | Nach viel Löschen in langen Texten reicht der Textspeicher der Tastatur nicht mehr; die Befehlserkennung liest dann je Aktualisierung zweimal 519 Zeichen aus der App. | hoch | `LatinIME.java:187–188`, `KeyboardCommands.kt:425, 435`, `RichInputConnection.java:627–650` | L | 64 |
| P4 | Die Befehlsanalyse läuft drei- bis fünfmal je Tastendruck und baut ihre Namenstabelle jedes Mal neu. | mittel | `KeyboardCommands.kt:360–378`, `Commands.kt` | – | 64 |
| P5 | „Tippfehler lernen“ liest je Wort 300 Zeichen hinter der Schreibmarke aus der App, ohne die Zeit zu messen. | mittel | `FieldCorrections.kt:139–140` | – | 64 |
| P6 | „Meine Daten“ wird nach jedem Schließen der Tastatur verworfen und im nächsten Zielfeld im Hauptthread neu entschlüsselt; ein Fehlschlag wird nicht gemerkt und wiederholt sich je Taste. | mittel | `LatinIME.java:919`, `MyData.kt:164–170` | – | 64 |
| P7 | Zwischenablage-Pille und Smart Paste werden bis 3 Minuten nach dem Kopieren bei jeder leeren Leiste neu erkannt und neu gebaut. | mittel | `ClipboardHistoryManager.kt:360–418`, `SmartPasteChips.kt:77–82` | – | erledigt (Etappe 64b): Erkennung je Clip gemerkt; die Pille selbst baut `ClipboardHistoryManager` weiter je Aktualisierung (Etappe 65), nicht auf einem Gerät gemessen |
| P8 | Chip-Zeilen und der Update-Chip werden je Aktualisierung neu gebaut, auch bei gleichem Inhalt. | mittel | `KeyboardCommands.kt:560–620`, `UpdatePrompts.kt` | – | erledigt (Etappe 64b): Zeile der Chips und Update-Chip bei gleichem Inhalt nicht neu gebaut; die Pille mit Smart Paste ausgenommen (wie P7), nicht auf einem Gerät gemessen |
| P9 | Die Chip-Quellen laufen je leerer Leiste zweimal. | niedrig | `LatinIME.java:1813–1829`, `1694–1712` | – | 64 |
| P10 | Beim Feldstart laufen mehrere Systemaufrufe im Hauptthread (Update-Prüfung, Trefferzonen). | niedrig | `UpdateOnOpen.kt:83–114`, `TouchModel.kt:113–139` | – | erledigt (Etappe 64b): Sperrprüfung des Feldstarts über den gemeinsamen Zustand, Datensparmodus, Netz und Version der Update-Prüfung im Hintergrund, nicht auf einem Gerät gemessen |
| P11 | Lernspeicher schreiben ihre ganze Datei im Hauptthread (Tippfehler, abgelehnte Korrekturen, Feldkorrekturen); das Gedächtnis wird beim ersten Wort im Hauptthread eingelesen. | niedrig | `TypoMemory.kt:131`, `Memory.kt:146–176` | – | 64 |
| P12 | Private Bereiche lesen je Wort aus der App, solange ein privater Wert im Feld steht. | niedrig | `PrivateRanges.kt:115–226` | – | offen |
| P13 | Haptik fragt je Raststufe der Leertaste die Fähigkeiten des Geräts neu ab. | niedrig | `HapticChain.kt:84–130` | – | erledigt (Etappe 64b): Fähigkeiten und Plan je Vibrator einmal, nicht auf einem Gerät gemessen |

Unauffällig laut Bericht: das Zeichnen der Tasten und der Beleuchtung, Touch, Symbole malen, die
Arbeit im Vorschlags-Thread. Zur Messung am Telefon bekommt die Diagnose eine Karte „Tippen“
(Etappe 64, seit 64a gebaut).

## 2. Build, Release, Updates, Tests

| Nr. | Befund | Schwere | Stelle | Geprüft | Plan |
| --- | --- | --- | --- | --- | --- |
| B1 | Die ausgelieferte Variante ist debuggable und nicht verkleinert; Debug-Modus standardmäßig an (Wörter im Protokoll, absichtliche Abstürze); die native Vorschlags-Engine ohne Optimierung übersetzt. | hoch | `app/build.gradle.kts:64–71`, `Defaults.kt:194`, `InputLogic.java:2733` | L | erledigt (Etappe 62), nicht auf einem Gerät gemessen |
| B2 | Fehlt das Token für das öffentliche Repository, gibt der Workflow nur eine Warnung aus und endet grün. | hoch | `agent-apk.yml:76–82` | L | erledigt (Etappe 63) |
| B3 | Signatur, Zertifikat und Versionsnummer der fertigen APK werden nicht geprüft (`|| true`); ohne gültige Nummer fällt der Build still auf 1. | hoch | `agent-apk.yml:67`, `app/build.gradle.kts:31` | L | erledigt (Etappe 63) |
| B4 | Ein fehlgeschlagener Lauf lässt sich nicht wiederholen, weil das Release schon existiert. | mittel | `agent-apk.yml:63–88` | – | erledigt (Etappe 63) |
| B5 | Ein Build von `main` wird auch ohne grünen Testlauf veröffentlicht. | mittel | `agent-apk.yml`, `agent-tests.yml` | – | erledigt (Etappe 63) |
| B6 | Die Testläufe bauen nie die ausgelieferte Variante. | mittel | `tools/dev.py:53–67` | – | erledigt (Etappe 63) |
| B7 | Die Zusammenfassung der Tests bemerkt abgestürzte Testklassen nicht und toleriert jeden Fehler in `XLinkTest`. | mittel | `agent-tests.yml:49, 84–103` | – | erledigt (Etappe 63) |
| B8 | Umbenennen der Workflow-Datei, des Repositorys oder der App-ID würde die Update-Kette brechen. | hoch | `UpdateReleases.kt`, `GitHubUpdateClient.kt:49` | L | erledigt (Etappen 57 und 63: P240, Vertragstest) |
| B9 | Änderungen an `gradle.properties` und `upstream.lock` starten keinen Testlauf. | niedrig | `agent-tests.yml:12–20` | – | erledigt (Etappe 63) |
| B10 | `build-test-auto.yml` (HeliBoard) ruft eine Aufgabe auf, die es hier nicht gibt. | niedrig | `.github/workflows/build-test-auto.yml` | – | erledigt (Etappe 63) |
| B11 | „Installieren“ lädt den neuesten Build, nicht unbedingt den im Dialog gezeigten. | niedrig | `Updates.kt:75–87` | – | offen |
| B12 | Tests mit festen Wartezeiten können auf langsamen Rechnern kippen. | niedrig | `RgbSettingsScreenTest.kt:319`, `ClipVaultTest.kt:199` u. a. | – | offen |
| B13 | Die Notizen zum Build werden von Hand überschrieben; die Grenze von 8 KiB für den Dialog prüft niemand. | niedrig | `release-notes/next.md`, `docs_sync.json` | – | offen |

Nicht eingeschaltet ist die Code-Verkleinerung (R8). Sie brächte zusätzlich Tempo, kann aber ohne
Gerätetest einzelne Seiten zum Absturz bringen: eigener Test-Build nach Entscheidung des Owners.

## 3. Datenschutz, Sicherheit, Robustheit

| Nr. | Befund | Schwere | Stelle | Geprüft | Plan |
| --- | --- | --- | --- | --- | --- |
| S1 | Eine Protokollzeile von HeliBoard schreibt in Feldern ohne Vorschläge den getippten Text ins Systemprotokoll, auch in Passwortfeldern. | hoch | `RichInputConnection.java:894` | L | bereits behoben (die Zeile nennt nur die Länge); Prüfung in Etappe 65 |
| S2 | Der Zwischenablage-Verlauf speichert auch im Inkognito-Modus, in Feldern ohne Lernen und bei gesperrtem Gerät; nur der Tresor prüft das. | mittel | `ClipboardHistoryManager.kt:174–180` | L | erledigt (Etappe 65) |
| S3 | Schlägt „Wiederherstellen“ fehl, wird der geschützte Datenordner gelöscht, weil seine Sicherung nie angelegt wurde. | mittel | `BackupRestorePreference.kt:181, 236–237` | L | erledigt (Etappe 65) |
| S4 | Eine beschädigte Sicherung leert die Einstellungen und meldet trotzdem „wiederhergestellt“. | mittel | `BackupRestorePreference.kt:209–216` | – | erledigt (Etappe 65) |
| S5 | Die Sicherungs-ZIP enthält gelernte Wörter und den Zwischenablage-Verlauf unverschlüsselt, ohne Hinweis. | niedrig | `BackupRestorePreference.kt:343–352` | – | erledigt (Etappe 69): Der Dialog sagt, dass die Datei nicht verschlüsselt ist und gelernte Wörter und Zwischenablage enthält |
| S6 | Weitere Protokollzeilen von HeliBoard nennen im Fehlerfall gelernte Wörter. | niedrig | `ExpandableBinaryDictionary.java:283, 300, 326` | – | erledigt (Etappe 65; Zeilen hinter `DebugFlags.DEBUG_ENABLED` bleiben, nur im Debug-Modus) |
| S7 | Die Einstellungen kopieren eine von außen übergebene Datei im Hauptthread. | niedrig | `SettingsActivity.kt:165–171` | – | erledigt (Etappe 65) |
| S8 | Der Tresor und ein Verweis auf den Tastaturdienst bleiben nach dem Beenden im Speicher. | niedrig | `ClipboardHistoryManager.kt:64, 78–81` | – | erledigt (Etappe 65) |
| S9 | Eine Ausnahme in einem einzelnen Befehl kann den Feldstart abbrechen (Schleife ohne Fangnetz). | niedrig | `KeyboardCommands.kt:312` | – | 64 |
| S10 | Ein KI-Ergebnis bleibt nach „Kopieren“ unbegrenzt in der System-Zwischenablage (als vertraulich markiert). | niedrig | `AgentEditorController.kt:1150–1157` | – | offen |
| S11 | Die Gestendaten-Sammlung für das HeliBoard-Projekt ist noch erreichbar. | niedrig | `GestureDataGathering.kt:174–190`, `AgentAreaSystemScreen.kt:291` | – | 68 |
| S12 | Der Feldtext des letzten Felds (bis etwa 1300 Zeichen) bleibt bis zum nächsten Feldstart im Arbeitsspeicher. | niedrig | `InputLogic.java:388–396`, `FieldCorrections.kt` | – | erledigt (Etappe 65) |
| S13 | Das Zeichnen der Beleuchtung hat kein Fangnetz: Ein Fehler dort würde die Tastatur wiederholt beenden. | mittel | `KeyboardView.java:309–597`, `RgbKeyPainter.kt` | – | erledigt (Etappe 70) |

Geprüft und in Ordnung: Backup-Regeln für Cloud und Gerätewechsel, exportierte Komponenten,
Prüfung der Update-APK (Prüfsumme, Paket, Signatur), Schlüsselspeicher, Bildschirmschutz der
KI-Seiten, Start vor dem ersten Entsperren. Was das Gerät verlässt: KI-Anfragen nach Freigabe, die
Update-Prüfung (`api.github.com`), der Download der APK und der Emoji-Mix nach Tipp, Links im
Browser nach Tipp.

## 4. Tipp-Logik: Korrektur und Lernen

| Nr. | Befund | Schwere | Stelle | Geprüft | Plan |
| --- | --- | --- | --- | --- | --- |
| T1 | In Feldern ohne Autokorrektur wird ein dreimal abgeschickter Vertipper zum „eigenen Wort“ und danach nie mehr korrigiert. | hoch | `Memory.kt:277–283` | L | 66 |
| T2 | Englische Wörter bekommen in englischen Sätzen den deutschen Zwilling („hand“ → „Hand“). | mittel | `Suggest.kt:289` (`guardAllows`) | L | erledigt (Etappe 60), nicht auf einem Gerät geprüft |
| T3 | Die Sperre der Korrektur in Befehlen hinkt einen Tastendruck hinterher. | mittel | `LatinIME.java:187`, `Suggest.kt:195, 366` | – | offen |
| T4 | Ein Befehlsname hinter „@“ (z. B. „@ki “ als Erwähnung) macht den Rest der Zeile zum Befehl ohne Autokorrektur. | mittel | `Commands.kt:102–150` | – | offen |
| T5 | Eigene Wörter, Wortfolgen und Kürzel gelten für alle Sprachen (so entschieden in Etappe 51). | mittel | `Memory.kt` | – | bleibt, Owner entscheidet |
| T6 | Trefferzonen: Kleinschreibung ohne feste Sprache (türkisches „ı“). | niedrig | `TouchModel.kt:206` | – | 66 |
| T7 | Zwei Aufrufe beim Abschicken eines Worts haben kein Fangnetz. | niedrig | `InputLogic.java:2790–2796` | – | 66 |
| T8 | Die gelernte Schreibweise eines Tippfehlers hängt davon ab, ob er zuerst am Satzanfang stand. | niedrig | `TypoMemory.kt:114–121` | – | 66 |
| T9 | „Wörter aus anderen Sprachen“ setzt beim Feldwechsel einen Merker nicht zurück. | niedrig | `ForeignWords.kt` (`onFieldChanged`) | – | 66 |
| T10 | Kontext-Vorschläge zählen einen Feldwechsel als „Vorschlag übergangen“. | niedrig | `FieldSuggestionChips.kt:111–114` | – | 66 |
| T11 | Vorschläge, Autokorrektur und Großschreibung fehlen, wo die App sie abschaltet (Instagram). | mittel | `InputAttributes.java`, `SettingsValues.java:240–290` | L | erledigt (Etappe 61), nicht auf einem Gerät geprüft; ob Instagram damit korrigiert, zeigt die Diagnose-Karte |

## 5. Befehle, Zwischenablage, KI-Abläufe

| Nr. | Befund | Schwere | Stelle | Geprüft | Plan |
| --- | --- | --- | --- | --- | --- |
| K1 | `@ki` nimmt höchstens 200 Zeichen, `@translate` 500; markierter Text 8.192; Antwort 2.048 Token; Abbruch nach 10 Sekunden ohne Antwort. | hoch | `Commands.kt:32–36`, `CloudTransport.kt:37`, `OpenAiProvider.kt:28–31, 57` | L | erledigt (Etappe 59), mit keiner echten Anfrage geprüft |
| K2 | Der QR-Chip reagiert nach einem Fehlschlag nicht mehr, teils ohne Meldung. | mittel | `QrCommand.kt:117–143` | – | 67 |
| K3 | Der Chip „Kein Schlüssel“ öffnet nicht sicher die KI-Einstellungen, wenn die Einstellungen schon offen waren. | mittel | `AgentEditorController.kt:813–817` | L | 67 |
| K4 | Enter und Chip-Tipps haben kein Fangnetz. | niedrig | `KeyboardCommands.kt:432–440, 598` | – | 64 |
| K5 | Eingefügte Kontaktdaten werden nicht als privat markiert und können als Kontext gelernt werden. | niedrig | `ContactCommand.kt:221–224` | – | 67 |
| K6 | Das Panel zeigt „Senden“ auch ohne gespeicherten Schlüssel; der Fehler kommt erst danach. | niedrig | `AgentEditorController.kt:933–968` | – | 67 |
| K7 | Die Vorlagen von `@wecker` und `@timer` sind immer deutsch. | niedrig | `AlarmCommand.kt:30–31`, `TimerCommand.kt:23–24` | – | 67 |
| K8 | Ein Ganztagstermin wird mit lokaler statt UTC-Mitternacht übergeben und kann einen Tag zu früh landen. | niedrig | `EventCommand.kt:35–38`, `ToolLauncher.kt:53–58` | L | 67 |
| K9 | `@mix` zeigt endlos „lädt“, wenn die Tabelle nicht geladen werden kann. | niedrig | `EmojiMixCommand.kt:60`, `EmojiMixer.kt:117–128` | – | 67 |
| K10 | `@contact` bleibt stumm, wenn nichts gefunden wurde. | niedrig | `ContactCommand.kt:151–161` | – | 67 |

## 6. Einstellungen und Texte

| Nr. | Befund | Schwere | Stelle | Geprüft | Plan |
| --- | --- | --- | --- | --- | --- |
| E4 | Auf dem Telefon hieß die App „Agent Keyboard Lab“ (Name der ausgelieferten Variante). | niedrig | `app/src/debugNoMinify/res/values/strings.xml` | L | erledigt (Etappe 57) |
| E1 | „Sichern und Wiederherstellen“ sichert nur die Einstellungen von HeliBoard, nicht Profile, Gedächtnis, Bausteine, Orte, Korrekturen; der Dialog verspricht „alle“. | hoch | `BackupRestorePreference.kt:100–150, 343–352` | – | Text erledigt (Etappe 69); Inhalt der Sicherung offen, Owner entscheidet (ROADMAP.md) |
| E2 | Die Suche findet die neuen Seiten nicht (KI, RGB, Updates, Diagnose …). | mittel | `SettingsContainer.kt:76–80` | – | erledigt (Etappe 69) |
| E3 | Die Anleitung nennt alte Menüpfade und hat einen doppelten Absatz. | mittel | `docs/ANLEITUNG.md:92–100` u. a. | – | erledigt (Etappe 69) |
| E5 | Tippflächen der neuen Seiten sind 44 statt 48 dp groß. | niedrig | `AgentSettingsKit.kt:108` | – | erledigt (Etappe 69) |
| E6 | Englische Texte in der deutschen Oberfläche (Slogan, zehn Tastennamen); „Über“ verlinkt auf HeliBoard. | niedrig | `donottranslate.xml:75`, `Constants.kt:9–22` | – | erledigt (Etappe 69): Slogan, sechs Ansagen und zwei Einstellungstexte ergänzt; die Tastennamen (kopieren, ausschneiden, einfügen, alles auswählen, Ziffernblock, Steuerkreuz) fehlten nicht, sie kommen aus Android und aus übersetzten Layoutnamen; „Über“ verweist auf das Repository des Forks |
| E7 | Abhängige Schalter verschwinden auf „Tasten & Sprachen“, statt ausgegraut mit Grund zu erscheinen. | niedrig | `AgentAreaKeysScreen.kt:199–212` | – | erledigt (Etappe 69) |
| E8 | Neue Seiten sind immer dunkel, alte folgen dem System. | niedrig | `AgentSettingsKit.kt:200–212` | – | offen, Owner entscheidet |
| E9 | Die Fußzeile der Startseite kann bei großer Schrift abgeschnitten werden. | niedrig | `AgentStartScreen.kt:154` | – | erledigt (Etappe 69) |
| E10 | Unerreichbarer Code und unbenutzte Texte der alten Übergangsseite. | niedrig | `AgentAreaScreen.kt:108–125` | – | erledigt (Etappe 69) |
| E11 | Die Karte „Dein Look“ zeigt nicht die echten Farben. | niedrig | `AgentStartScreen.kt` (`LookCard`) | – | offen |

## 7. Aussehen und Gefühl

| Nr. | Befund | Schwere | Stelle | Geprüft | Plan |
| --- | --- | --- | --- | --- | --- |
| A1 | Die Beleuchtung läuft hinter Aktionspanel, Rad, Rechner, Zeichen-Kunst und Menü weiter. | hoch | `RgbLighting.kt:281` | L | erledigt (Etappe 70) |
| A2 | Bricht Android eine Wischgeste auf der Leertaste ab, bleibt ihr Zustand hängen; der nächste Tipp kann verloren gehen. | mittel | `PointerTracker.java` (`onCancelEvent`) | – | behoben in der Nachprüfung 70: Ein Abbruch mitten im Wischen war in Robolectric nicht nachzustellen; ein langer Druck auf die Leertaste (Sprachauswahl) mit anschließendem Abbruch ließ aber Wisch-Merker stehen (Test rot), `PointerTracker.cancelKeyTracking` löscht sie jetzt. Prüfer für diese Engine-Änderung und Gerätetest fehlen noch |
| A3 | Die Zeichen-Kunst rechnet beim Zeichnen alle 40 ms im Hauptthread. | mittel | `CharArtView.kt:827–890` | – | erledigt (Etappe 70) |
| A4 | Je Taste und Bild entstehen neue Farbfilter-Objekte. | niedrig | `RgbKeyPainter.kt:177–181, 228–232` | – | erledigt (Etappe 70) |
| A5 | Lauftext im Menü des flachen Looks läuft endlos. | niedrig | `FlatStyle.kt:36–52` | – | erledigt (Etappe 70) |
| A6 | Der Lade-Puls des Panels läuft bei geschlossenem Panel weiter. | niedrig | `FlatPanelStyle.kt:55–79` | – | erledigt (Etappe 70) |
| A7 | Die Eckenrundung eigener Designs gilt auch für Popups und „mehr Vorschläge“. | niedrig | `KeyboardView.java:129`, `DesignLook.kt:37–46` | – | offen, Owner entscheidet |

## 8. Zusammenspiel der Bereiche

| Nr. | Befund | Schwere | Stelle | Geprüft | Plan |
| --- | --- | --- | --- | --- | --- |
| Z1 | „Personalisierte Vorschläge“ ausschalten löscht Tippfehler-Gedächtnis, Feldkorrekturen, abgelehnte Korrekturen und Trefferzonen nicht; sie wirken weiter. | mittel | `TextCorrectionScreen.kt:215–225`, `AgentLearningScreen.kt:119–136`, `Suggest.kt:205–213` | – | 68 |
| Z2 | Lernschalter (Gedächtnis, Fremdwörter, Symbole, Kontext) wirken nicht, solange „Personalisierte Vorschläge“ aus ist; die Seiten sagen es nicht. | mittel | `Defaults.kt:76`, `AgentMemoryScreen.kt:185` | – | 68 |
| Z3 | „Ausgeschlossene Apps“ gelten nur für Gedächtnis und Fremdwörter, nicht für Tippfehler, Trefferzonen, Kontext. | niedrig | `Memory.kt:133`, `TypoMemory.kt:102` | – | 68 |
| Z4 | Der Lernverlauf von HeliBoard lernt auch bei gesperrtem Gerät. | niedrig | `InputLogic.java:1940–1996` | – | 68 |
| Z5 | „Gesperrt“ wird teils live, teils mit dem Stand vom Feldstart geprüft. | niedrig | `SettingsValues.java:197` gegen `Memory.kt:62` u. a. | – | 68 |
| Z6 | Beim Feldstart kann die Zwischenablage-Pille gerade gezeigte Chips überschreiben (mit zwei Nicht-Standard-Schaltern). | niedrig | `LatinIME.java:1146–1148` | – | erledigt (Etappe 70) |
| Z7 | Datum ohne passende Sprache: Bausteine fallen auf Deutsch zurück, Befehle auf Englisch. | niedrig | `SnippetTemplate.kt:98–104`, `DateFormatting.kt:33–39` | – | 67 |
| Z8 | „Zuletzt gemischt“ (Emoji-Mix) beachtet die Lernpause nicht und bleibt bei „Gelernte Wörter löschen“. | niedrig | `EmojiMixRecents.kt:34` | – | 68 |
| Z9 | Die Reihenfolge der Leiste in `ARCHITECTURE.md` nennt Screenshot-Chip und Autofill nicht. | niedrig | `docs/ARCHITECTURE.md` (Befehle → Leiste) | – | 64 |

## Ideen aus den Berichten (nicht geplant, Owner entscheidet)

- Autokorrektur auch innerhalb einer `@ki`-Frage; „Rückgängig“ nach einer `@ki`-Antwort.
- Enter nimmt bei `@math` und `@datum` den einzigen Chip.
- Eigene Wörter je Sprache; „ß/ss“ und spanische Akzente als Varianten behalten.
- In der Diagnose anzeigen, wenn ein eigenes Wort die Korrektur verhindert.
- Eine Liste der eigenen Wörter mit Hinweis „würde korrigiert zu …“, um gelernte Vertipper zu finden.
- Sicherung mit Auswahl („Einstellungen“, „Gelerntes“, „Profile und Designs“).
- Heller Entwurf für die neuen Einstellungsseiten.
- Bildrate der Beleuchtung bei schwachem Akku senken.
- Code-Verkleinerung (R8) für die ausgelieferte APK.
