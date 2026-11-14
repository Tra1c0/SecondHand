# Prüfnachweise

## Etappe 0 — unveränderte HeliBoard-Codebasis

Stand: 2026-11-08. Upstream-Commit siehe `upstream.lock`.

| Prüfung | Ergebnis |
| --- | --- |
| Gradle-Konfiguration (`help`) | Bestanden |
| Unverändertes `assembleDebugNoMinify`, JDK 17, ARM64 | Bestanden, 42 Tasks, 4 min 14 s |
| APK enthält native ARM64-Wörterbuchbibliothek | Bestanden, ZIP-Inhalt geprüft |
| `testDebugUnitTest`, unveränderte Quelle, Test-JVM 21 | 181 Tests: 176 bestanden, 5 geerbte Fehler |
| Software-Emulator | Installation wegen begrenztem Dateisystemplatz abgebrochen |
| Physisches Android-Gerät | Ausstehend |

Baseline-APK: `HeliBoard_4.2-debugNoMinify.apk`, 34.779.764 Bytes.

SHA-256:
`1fa0f8e36a7dd01b1e35ac4232e6bea70ec023fc5e334359d49908b5378a9ec4`

Der erste Buildversuch wurde durch eine gleichzeitige Emulatorinstallation und
ein volles Dateisystem unterbrochen. Er wurde nach Bereinigung ausschließlich
eigener temporärer Dateien erfolgreich wiederholt. Upstream-C++-Warnungen und
Gradle-Deprecations wurden nicht durch sachfremde Quellcodeänderungen behoben.

Robolectric mit SDK 36 verlangt Java 21. Der erste Testlauf auf JDK 17 konnte
deshalb neun Testklassen nicht starten. Die Wiederholung verwendete nur einen
externen Gradle-Init-Hook für die Test-JVM 21; der Anwendungsquellcode war
unverändert. Die fünf Baseline-Fehler sind:

- `InputLogicTest.insertLetterIntoWordHangulFails`: bekannte Hangul-Abweichung.
- `StringUtilsTest.detectEmojisAtEndFails`: bekannte Emoji-Abweichung.
- `XLinkTest.layoutsLinks`: externer Link liefert HTTP 404.
- `XLinkTest.readmeLinks`: externer Link liefert HTTP 404.
- `XLinkTest.knownDictionaries`: geändertes externes Datenformat.

Diese Fehler werden nicht als bestanden gewertet. Neue Fehler müssen gegenüber
dieser Baseline separat erkannt und vor dem nächsten Schritt behoben werden.

## Etappe 1 — persönlicher Fork

- App-ID `dev.personal.agentkeyboard.debug`, Name `Agent Keyboard Lab` aus
  der tatsächlich gepackten ARM64-APK mit `aapt2 dump badging` geprüft.
- Build bestanden. Die drei neuen Manifest-/Provider-Tests bestehen.
- Unabhängiges Astra-Review fand noch alte Namen in Debug-Ressourcen; beide
  Varianten sind korrigiert und die sichtbare Identität ist nun mitgeprüft.
- Vollständiger Regressionslauf: 184 Tests, fünf Baseline-Fehler und ein
  zusätzlicher Test, der personalisierte Vorschläge implizit voraussetzte.
  Dieser Test aktiviert nun gezielt die Personalisierung für sein Szenario.
  Seine Erwartungswerte bleiben unverändert.
- Anschließend alle 29 Suggestion-Tests und drei Fork-Tests bestanden.
- Kompilierung weiterhin Java 17; Testprozesse verwenden automatisch Java 21.
- Keine Internetberechtigung, automatische Backups einschließlich Transfer
  ausgeschlossen. Physischer Installationstest weiterhin offen.

## Etappe 2 — unabhängiger Kern

- 16 JVM-Tests bestanden: sensible Editoren, Größen-/Unicode-Grenzen,
  Sitzungswechsel, zwischenzeitliche Änderungen, Einmal-Token und lokale Aktionen.
- Kern ohne Android- oder Netzwerkabhängigkeit, JDK 17.
- Unabhängiges Review: Tokens enthalten zusätzlich eine zufällige Besitzer-ID,
  damit ein neuer IME-Service kein Ergebnis seines Vorgängers übernehmen kann.
  Ein eigener Regressionstest deckt diesen Fall ab.
- ARM64-App-Build mit eingebundenem Kern bestanden.
- `ModelProvider` ist nur ein Vertrag; es gibt noch keinen echten Anbieteranschluss.

## Etappe 3a — Designprofil

- Profilseite in den Einstellungen; je Bereich zunächst ein gespeicherter Stand.
- Designprofil umfasst Tastenstil, Ränder und Schriftgröße. Bestehende Farb-/Layouteditoren bleiben verfügbar.
- Sieben Agent-Tests bestanden, darunter vier Profilprüfungen: Bereichstrennung,
  Reset ohne Verlust des gespeicherten Profils, ungültige Imports ohne Mutation
  und Export ohne fremde Einstellungen/Zugangsdaten.
- App-Build bestanden. Visuelle Geräteprüfung steht noch aus.

## Etappe 3b — Tastenanordnung

- Separater Stand für Zahlenreihe, Emoji- und Sprachtaste.
- Acht Agent-Tests und App-Build bestanden. Ein zusätzlicher Test prüft,
  dass Wiederherstellung Design und Sprachlayouts nicht verändert.
- Sprachen und eigene Layoutdateien bleiben im vorhandenen Layouteditor.

## Etappe 3c — Gestenprofil

- Separater Stand für horizontale/vertikale Leertasten-Gesten und Wischen zum Löschen.
- Neun Agent-Tests und App-Build bestanden. Nicht unterstützte Aktionen werden
  vor jeder Änderung abgewiesen; Cursorbewegung bleibt die horizontale Voreinstellung.
- Profilformat 1 speichert je Bereich einen Stand. Änderungen der Feldlisten
  brauchen eine explizite Formatmigration; unbekannte Daten werden nicht still übernommen.

## Etappe 4 — Android-Editoradapter

- 20 Kernprüfungen und 29 gezielte Android-/Integrationstests bestanden.
- Quellenzugriff ausschließlich auf begrenzten markierten Text; sensible Flags
  werden vor jedem Textzugriff geprüft. Editorwechsel, Eingaben, Auswahländerungen
  und neue IME-Instanzen machen alte Übernahmen ungültig.
- Einmalige direkte Ersetzung mit Cache-Abgleich, ohne TLD-/Autospace-Verarbeitung.
  Unklare Ergebnisse verwerfen Cache und erwartete Positionen; kein automatischer Retry.
- Echte InputLogic-Tests prüfen exakte Ersetzung, danach einzelnes Löschen und
  unterdrücktes Lernen bis zu einer neuen Framework-Editorsitzung.
- Unabhängiges Review fand und korrigierte unsichere Cache-Koordinaten,
  Callback-Reentranz und die Worker-/UI-Lücke verspäteter Swipe-Ergebnisse.
  Tokens verfallen sofort; reine UI-Beobachter laufen im nächsten Main-Loop-Turn.
- Generation-geprüfte Swipe-Ergebnisse bleiben bis zur tatsächlichen UI-Verarbeitung
  gesperrt. Drei zusätzliche Produktionstests prüfen den echten Zustellungsweg.
- ARM64-App-Build bestanden. Physische Editor-/Geräteprüfung bleibt offen.

## Etappe 5 — lokale Aktionsoberfläche

- 36 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- Drei lokale Aktionen mit Quell-/Ergebnisvorschau, separater Übernahme und
  ausdrücklichem Kopieren. Keine neue Activity oder editierbare Vorschau.
- Gerenderte helle/dunkle Vorschau geprüft; das Panel scrollt auf kleinen Flächen.
- Vollständige Regression: 215 Tests, ausschließlich dieselben fünf Baseline-Fehler.
- Unabhängiges Review fand zwei Integrationsfehler: Vollbild-IME-Geometrie und
  Accessibility-Eingaben unter dem Panel. Beide sind korrigiert und durch
  Layout-/Messungs- sowie Produktionseingabetests abgedeckt; erneutes Review ohne P1.
- Overlay-Größe folgt der ursprünglichen Keyboard-Engine, niemals dem Vollbildfenster.
  UNKNOWN blockiert Text-, Tasten-, Inhalts-, Swipe- und Vorschlagseingaben bis
  zur Cache-Erholung mit autoritativen Cursorpositionen.
- Erste Lint-Prüfung: ein neuer Ressourcenindexfehler korrigiert; Forktexte sind
  vorerst Deutsch/Englisch mit englischem Fallback. 68 Übersetzungslücken sind
  nach byteweisem Vergleich mit dem Upstream-Pin separat baseliniert.
- Abschließendes Lint bestanden: keine neuen Fehler, 105 Warnungen und ein Hinweis;
  68 geerbte Übersetzungslücken bleiben in der Baseline sichtbar.
- Alte Hintergrundwarnung im Robolectric-Lauf bleibt zu untersuchen.
  Physische Touch-/TalkBack-/Editorprüfungen bleiben offen.

## Etappe 6 — Aktionsrad

- 40 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- Radiale echte Buttons, Tastatur-/TalkBack-Semantik, kompakte Anordnung bei
  schmaler/niedriger Tastatur. Zielgrößen und überlappungsfreie Bounds geprüft.
- Gerenderte Vorschau visuell geprüft. Ein Produktionstest durchläuft Wheel →
  lokale Aktion → Vorschau → explizite Ersetzung im echten InputLogic.
- Öffnen/Schließen erfasst keinen Editorinhalt und startet keine Aktion.
- Geräteprüfung mit TalkBack/Rotation/Floating Keyboard bleibt offen.

## Etappe 7 — optionale Leertastengeste

- 20 Kernprüfungen und 49 gezielte Android-/Integrationstests bestanden; ARM64-Build bestanden.
- Standard ausgeschaltet, getrennte Einstellung im Gestenprofil. Live-Prüfung erst
  beim Langdruck; keine Änderung an Cursor-/Sprachwischkonfiguration oder Timing.
- Echte TimerHandler-/PointerTracker-Sequenzen prüfen Hold → UP, Hold → CANCEL,
  nächste Taste, Swipe-Abbruch, normale/Auto-Popups, Globe-Picker, Callback-Fallback
  und Mehrfingerpakete mit Pointer-IDs. Keine Aktion beim bloßen Öffnen des Rads.
- Unabhängiges Review ohne blockierende Befunde; die dort genannten Testlücken
  zu Fallback/Auto-Popups/Mehrfingerpaketen anschließend geschlossen.
- Profilformat 2: explizite Migration aus Format 1. Neue Geste bleibt dabei aus;
  unpassende Schlüssel/Versionen werden vor Mutation abgewiesen.
- Physische Touch-/TalkBack-/Mehrfingerprüfung bleibt offen.

## Etappe 8 — verschlüsselte Zugangsdaten

- 61 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- AES-256-GCM, zufällige IVs, authentifizierte Slotbindung, begrenzte atomare
  Dateien im CE-No-Backup-Speicher; Keystore erst bei explizitem Zugriff.
- Sperrstatus wird vor Zugriff geprüft, unbekannte Systemdienste sperren.
  Keine automatische Schlüsselregeneration bei vorhandenen Geheimtextdateien.
- Formular maskiert und nicht saveable; ON_STOP/Verlassen/Speichern leert es,
  FLAG_SECURE bleibt während der Seite gesetzt. Mutable Kopien werden gelöscht.
- Read-only-Sicherheitsreview durch Sol: Speicherpfade für Emoji-Suche und
  Dateianbieter sowie Formular-Lifecycle korrigiert. Astra-Review konnte wegen
  des gemeldeten Nutzungslimits nicht laufen.
- Produktionsnahe Provider-Tests öffnen eine DE-Clipboard-Datei nach Attach aus
  CE und weisen CE-Zugangsdaten außerhalb beider Freigabewurzeln ab.
- Vollständiger Lauf: 240 Tests, 7 Fehler. Zwei bekannte Textabweichungen, fünf
  externe Prüfungen: vier Links liefern HTTP 504 und ein Dictionary-Formatfehler.
  Damit zwei zusätzliche externe Timeout-Fälle gegenüber der ursprünglichen
  Fünfer-Baseline; keine Abschwächung der Assertions oder Änderung der Baseline.
- Erster Lint-Lauf brach mit einem Kotlin-FIR-/ProviderPermissionDetector-Fehler
  ab; Wiederholung auf unverändertem Stand bestanden: 110 Warnungen, ein Hinweis,
  keine neuen Fehler, weiterhin 68 geerbte Übersetzungsfehler separat baseliniert.
- API 21 wird von Robolectric 4.16 nicht unterstützt. Die Capability-Grenze
  (Schlüssel ab API 23) ist separat geprüft; kein API-21-Gerätetest behauptet.
- Algorithmustests verwenden einen injizierten AES-Schlüssel. Echte Android-
  Keystore-, Direct-Boot-, Formular- und Emoji-/Freigabe-Gerätetests bleiben offen.
- Einzelheiten zur erhaltenen Engine-Speicherbasis siehe `STORAGE.md`.

## Etappe 9a — echter OpenAI-Transport

- 69 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- Fester Responses-HTTPS-Ursprung, kein Redirect, keine automatischen POST-Retries,
  10-Sekunden-I/O- und 45-Sekunden-Gesamtfrist einschließlich Warteschlange.
- Ein aktiver Lauf pro Providerinstanz, tatsächliches Call.cancel bei Abbruch,
  maximal 1 MiB Antwortkörper und 16.384 Ergebniszeichen.
- Strikte JSON-Texttypen, vollständige Nachrichtenblöcke, keine Refusal-/Thinking-
  oder unvollständigen Ergebnisse. Neutrale Fehler ohne fremde Fehlerkörper.
- Sicherheitsreview fand HTTP-503-Wiederholung trotz retryOnConnectionFailure(false):
  RequestBody ist nun zusätzlich one-shot. Ein echter Loopback-HTTP-Test bestätigt
  exakt einen POST bei 503 + Retry-After:0. Kein echter Provideraufruf.
- Weitere Reviewfunde (Warteschlangenfrist, JSON-Zahl als Text, synchrone Fehler)
  korrigiert; erneutes Read-only-Review ohne Transportblocker.
- OkHttp 5.3.2 gepinnt, Square-POM und AAR-Minimum API 21 geprüft.
  Automatischer HTTP-Startup-Initializer entfernt. Produktionseingabetest bestätigt,
  dass normales Tippen den Cloud-Client nicht initialisiert.
- INTERNET nun bewusst vorhanden; Klartexttransport auf APK-Ebene ausgeschaltet.
  Das Panel startet noch keinen Provideraufruf; Verkabelung folgt in 9b.

## Etappe 9b — bestätigte KI-Korrektur

- 74 gezielte Android-/Integrationstests, ARM64-Build und Lint bestanden.
- Panel zeigt Provider, Modell und Umfang. Erst der separate Senden-Knopf startet
  eine Anfrage. Ergebnisvorschau und Editorübernahme bleiben getrennte Aktionen.
- Tatsächlicher InputLogic-Integrationstest mit simuliertem Provider: Vorschau →
  Sendefreigabe → Antwortvorschau → bewusste Ersetzung; vorher bleibt Text unverändert.
- Runner prüft Sitzungen und Generationen. Tests mit absichtlich nicht kooperativem
  Anbieter verwerfen Ergebnisse nach Abbruch/Editorwechsel und schützen neue Läufe.
- Abbruch bei veraltetem Kontext, Schließen, Editorende oder Servicezerstörung.
  Anbieterinstanz bleibt pro Controller erhalten; kein automatischer Retry.
- Read-only-Review ohne Einfüge-/Datenschutzblocker. Die gefundenen UI-Punkte
  (lokale Aussage eingrenzen, Aktionen nach Antwort wieder freigeben) sind korrigiert.
- Lint fand zunächst die fehlende Startup-Klasse im Compile-Klassenpfad. Android-AAR
  und Startup-Runtime sind nun explizit eingebunden; Gradle bestätigt androidJvm-
  Variante. HTTP-Initializer bleibt entfernt, öffentlicher manueller Einstieg wird
  ausschließlich beim expliziten Cloudstart verwendet.
- Echte API-Schlüssel, Live-Anfragen und physische Geräteprüfung weiter ausstehend.

## Etappe 9c — Initialisierung ohne blockierte Tastatur

- Lazy Factory hält nur den App-Kontext. HTTP-/TLS-Client und Call werden erst
  nach Sendefreigabe im IO-Kontext aufgebaut. Secret-Zugriff bleibt ebenfalls in IO.
- Transporttest prüft tatsächlich unterschiedliche Threads für Aufrufer und
  Call-Aufbau. Alle 74 gezielten Tests und ARM64-Build bestanden.

## Etappe 10a — Übersetzen

- 21 Kernprüfungen, 75 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- 14 feste Zielsprachen, separat in KI-Einstellungen gespeichert. Keine freie
  Sprachzeichenfolge wird in die Systeminstruktion importiert.
- Bestätigung zeigt Aufgabe und Zielsprache. Approval friert Modell, Aufgabe und
  Zielsprache ein. Ein echter InputLogic-/Paneltest ändert die Einstellungen nach
  der Vorschau und bestätigt, dass weiterhin die angezeigte Sprache gesendet wird.
- Auswahl, Sendefreigabe, Vorschau und Übernahme nutzen unverändert denselben
  abgesicherten Weg. Kein echter Anbieter-/Übersetzungsqualitätstest behauptet.

## Etappe 10b — Umformulieren

- 21 Kernprüfungen, 76 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- Neutraler, freundlicher oder professioneller Tonfall, in Einstellungen wählbar
  und vor Sendefreigabe sichtbar. Approval friert den Tonfall ein.
- Produktionstest ändert den Tonfall nach Vorschau und bestätigt die unveränderte
  freigegebene Instruktion. Keine automatische Ersetzung; kein Qualitätsversprechen
  aus simulierten Modellantworten.

## Etappe 10c — Zusammenfassen

- 21 Kernprüfungen, 77 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- Zusammenfassung als eigener ausdrücklich bestätigter Auftrag, mit Ergebnisvorschau
  und separater Übernahme. Native Integration durchläuft denselben sicheren Pfad.
- Die modellseitige Zusammenfassungsqualität bleibt eine echte API-Prüfung.

## Etappe 10d — Antwortentwurf

- 21 Kernprüfungen, 78 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- Antwortentwurf mit sichtbar gewähltem, eingefrorenem Tonfall. Es wird keine
  Nachricht verschickt; erst bewusste Übernahme ersetzt den ausgewählten Editortext.
- Tatsächlicher InputLogic-/Paneltest bestätigt Sendefreigabe, Tonfallbindung und
  unveränderten Editor bis zur Übernahme. Antworten bleiben prüfbedürftige KI-Entwürfe.

## Etappe 10e — Kontextanalyse ohne Ersetzung

- 21 Kernprüfungen, 79 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- Inhalt, Ton und mögliche Absichten werden als Analyse angefordert. UI kennzeichnet
  Absichten als Vermutungen; das Ergebnis kann gelesen und bewusst kopiert werden.
- Analyse sperrt Übernahme in Controller und View. Der native Test prüft sowohl
  den deaktivierten Button als auch den direkten Callback-Aufruf ohne Textmutation.
- Unabhängiges Read-only-Review der Aktionserweiterung ohne konkreten Datenschutz-
  oder Einfügungsbefund. Alte Ergebnisse, Sprach-/Tonbindung und Stale-Sperren bleiben erhalten.
- Echte Analysequalität und Gerätekontrolle sind weiterhin ausstehend.

## Härtung 9d — gesperrte KI-Einstellungen

- 80 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- AgentAiScreen konstruiert die CE-Einstellungen erst nach strikter Entsperrprüfung.
  Vor dem ersten Unlock erscheint nur ein Hinweis. Bei ON_RESUME wird erneut
  geprüft, bei ON_STOP wird das Eingabeformular entfernt.
- Settings und CredentialStore verwenden dieselbe ausfallsichere Sperrprüfung.
  Tests prüfen fehlende/fehlerhafte Systemdienste, Keyguard und User-Unlock getrennt.
- Der echte Reboot-/Settings-Lifecycle-Gerätetest bleibt ausstehend.

## Etappe 10f — kompakte Aktionsauswahl

- 80 gezielte Android-/Integrationstests, ARM64-Build und Lint bestanden.
- Sechs KI-Aktionen in zwei Spalten, getrennt von lokalen Werkzeugen.
  Anbieter-Einstellungen sind im Kopf erreichbar; Sendebestätigung steht direkt
  unter dem Auswahltext. Alle Felder bleiben nicht editierbare Views.
- Native helle Vorschau visuell geprüft. Lint: 116 Warnungen, ein Hinweis,
  keine neuen Fehler; 68 geerbte Übersetzungsfehler bleiben separat baseliniert.

## Etappe 11a — Skill-Belegungen und Dateiformat

- 21 Kernprüfungen, 84 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- Drei feste Plätze, stabile Builtin-IDs, begrenzte Beschriftungen ohne Steuer-/
  BiDi-Täuschungszeichen. Keine Code-, URL- oder Zugangsdatenfelder.
- CE-No-Backup-Datei, atomare Speicherung, striktes Format 1. Ganze Datei wird
  geprüft, bevor etwas verändert wird. Unbekannte Versionen/Felder/Aktionen,
  doppelte Plätze, falsche Typen und übergroße Dateien verändern keinen Stand.
- Bei gesperrtem oder unlesbarem Speicher liefert der Tippweg eine neutrale
  unbelegte Taste; Schreibzugriffe bleiben gesperrt. Tastatureinstellungen sind getrennt.
- Native Tasten und Dateiauswahl werden im nächsten Teil verbunden.

## Etappe 11b — native Skill-Tasten

- 86 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- Drei nummerierte Toolbar-/Layoutcodes mit Icons für alle drei Tastenstile.
  Belegungen und explizites Anheften sind in den Einstellungen verfügbar.
- Quelle wird vor asynchronem Konfigurationszugriff erfasst. Identität und Token
  müssen vor Vorbereitung weiterhin stimmen; Abbruch/Editorwechsel stoppen den Weg.
- Native Integrationstests: Skill 1 bereitet lokale Großschreibung ohne Mutation
  vor; Skill 2 zeigt eine KI-Sendebestätigung ohne initialisierten Cloud-Transport.
- Die alte Handler-Testattrappe liefert nun den echten Boolean-Erfolg zurück,
  statt durch Unit einen abgewiesenen Coroutine-Post vorzutäuschen. Neue Async-Tests
  stellen Runnable-Callbacks gezielt zu; bisherige Testassertionen unverändert.
- Unabhängiges Read-only-Review ohne konkreten Sicherheits-/Tippbefund.
- Vollständige Regression: 267 Tests, genau zwei bekannte Textfehler und fünf
  externe Link-/Formatfehler wie im letzten vollständigen Lauf. Keine neue
  funktionale Abweichung durch die präzisere Handler-Attrappe.
- Lint bestanden: 119 Warnungen, ein Hinweis; 68 geerbte Übersetzungsfehler baseliniert.

## Etappe 11c — Dateiimport und -export

- 91 gezielte Android-/Integrationstests, ARM64-Build und Lint bestanden.
- SAF-Dokumentauswahl für Skill-Belegungen und vorhandene Profilgruppen.
  UTF-8 wird strikt dekodiert, BOM akzeptiert, Dateigröße vor Parsing begrenzt.
- Exporte werden vor dem Öffnen/Trunkieren auf UTF-8-Bytegröße geprüft.
- Falsche Profilgruppe verändert weder gespeicherte noch aktive Profile. Import
  speichert zunächst; erst „Anwenden“ ändert die Tastatur. Skills führen nie aus.
- Keine Credentials, Editorinhalte oder Ergebnisse in diesen Dateiformaten.
- Unabhängiges lesendes Review ohne blockierenden Befund. Echte SAF-Provider-
  und Rotationsprüfung auf Android bleibt offen.

## Etappe 12a — verschlüsseltes Statusjournal

- 97 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- Eigene CE/noBackup-Datei, eigenes Keystore-Alias und AAD. Höchstens 20 Einträge,
  terminale Zustände höchstens sieben Tage; aktive Absichten werden nicht verdrängt.
- Metadaten ausschließlich: Aktion, Anbieter, Modell, Zeit, Prozess, Zustand/ID.
  Keine Quelltexte, Ergebnisse, Prompts, Zugangsdaten oder Editorberechtigungen.
- Neustart markiert fremde laufende Aufträge UNKNOWN; abgeschlossene Zustände sind
  unveränderlich. Löschen der Historie erzeugt bei späterer Antwort keinen Eintrag.
- Sperre, beschädigte Verschlüsselung und ungültige Metadaten verändern keine Datei.
- Unabhängiges Review ohne Speicherblocker; bei 12b ist Beginn/ID-Übernahme gegen
  Coroutine-Abbruch zu schützen. Keystore-/Prozesskill-Gerätetest bleibt offen.

## Etappe 12b — abbrechsichere Ausführung und Statusoberfläche

- 103 gezielte Android-/Integrationstests, ARM64-Build und Lint bestanden.
- Intent und ID-Übernahme sind gemeinsam nicht abbrechbar; danach wird Abbruch
  vor jedem Providerstart geprüft. Abschluss wird vor der Ergebnisanzeige gespeichert.
- Kaputter Anfangsspeicher verhindert die Anfrage. Kaputter Abschlussspeicher
  veröffentlicht kein ungesichertes Erfolgsergebnis. Keine automatischen Retries.
- Netzwerk-/Timeout-Ausgänge sind UNKNOWN; lokaler Abbruch bedeutet ausdrücklich
  keine Zusicherung, dass der Anbieter die Anfrage noch nicht verarbeitet hat.
- Review fand Sperren→Abbruch→Entsperren im selben Prozess: RAM-Registry aktiver
  IDs erlaubt nun, fehlgeschlagene Abschlüsse nach Entsperren als UNKNOWN zu
  bereinigen. Gezielter Test plus erneutes unabhängiges Review bestanden.
- Native Textaktionen verwenden das echte Journal; Test-Provider und Testjournal
  sind unabhängig injizierbar. Alle sechs End-to-end-Flows prüfen den Abschluss.
- Entsperrte KI-Einstellungen zeigen nur Metadaten, Aktualisieren und Löschen.
  Echte Prozesskill-/Keystore-/Lock-Lifecycle-Geräteprüfung bleibt offen.

## Härtung — automatische Crashdateien entfernt

- 104 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- Alter privater Crashhandler entfernt: keine automatische Persistenz roher
  Fehlermeldungen oder LastLog in External/DE. Androids Standardhandler bleibt
  erhalten, unabhängig vom Debugschalter. Regressionstest und lesendes Review.
- Dadurch ist die alte maskierte Warnung lokalisierbar: Robolectric meldet im
  Hintergrund PackageManager.NameNotFoundException aus App.kt:41 (Versionslog
  nach Test-Teardown). Dieser Ursprungsfehler wird separat korrigiert.
- Vorhandene alte Crashdateien bleiben ausdrücklich manuell exportierbar.

## Härtung — Startmetadaten ohne Paketmanager-Race

- 104 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- Startlog verwendet BuildConfig-Konstanten statt spät aufgelöster Paketdaten.
  Keine asynchrone PackageManager-Abfrage nach einem Robolectric-Teardown.
- Test-XML und Laufprotokoll enthalten im erneuten Lauf keine Hintergrund-
  Exception/NameNotFoundException/NullPointerException. Kein Handler unterdrückt sie.
- Andere alte GlobalScope-View-Callbacks bleiben für eine eigene Härtung markiert.

## Etappe 13a — begrenzter Workflow-Kern

- 35 reine Kernprüfungen (14 neue Workflowfälle), 104 gezielte Android-/
  Integrationstests, ARM64-Build und Lint bestanden. Vollständige Regression: 285 Tests, genau die
  fünf ursprünglichen Baseline-Fehler; keine zusätzlichen externen Timeouts.
- Unveränderliche Definitionen, höchstens zwei Schritte/Modellaufrufe, strikte
  rückwärts gerichtete Referenzen, Anbieterprüfung vor jeder Ausführung.
- Textbudgets und UTF-16 validiert; zu großes Zwischenergebnis wird nicht an das
  nächste Modell gesendet. Analyse bleibt entlang der Abhängigkeiten nicht ersetzbar.
- ModelRequest hält ausschließlich input:String; der Snapshot-Konstruktor kopiert
  Text, übergibt jedoch keine Editorberechtigung. Nativer Send-/Apply-Weg unverändert.
- Unabhängiges Sol-Review ohne Grenzverletzung; Astra-Review von Kern und Journal ebenfalls ohne Blocker.
- Speicher, Aktivierung, Sendefreigabe, Timeout und Journal-Anbindung sind der
  nächste getrennte Teil. Aktuell kein Workflow-Start über die Oberfläche.

## Etappe 13b1 — Workflow-Konfigurationsspeicher

- 111 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- CE/noBackup, max. zehn Abläufe/32.768 Austauschbytes. Vollständige Typ-/Feld-/
  Referenzprüfung vor atomarem Schreiben. Kein Start beim Laden oder Import.
- Austauschformat 1 mit IDs/Revisionsnummern, festen Aktionen und Modellparametern.
  Lokaler Speicherwrapper hält zusätzlich einen Generationstoken.
- Review fand Same-ID/Same-Revision-Import-Race: Jede Mutation verlangt jetzt den
  gelesenen Generationstoken und erzeugt einen neuen. Stale save/delete/replaceAll
  werden auch nach Import, Leerung und Delete/Recreate abgewiesen. Nachtest und
  unabhängiges Rereview ohne weiteren Befund.
- Defekte Dateien sind keine leere Sammlung; Sperre/ungültige Daten verändern nichts.
  Austauschformat enthält den internen Generationstoken nicht.

## Etappe 13b2 — manuelle Workflow-Einstellungen und Dateivorschau

- 115 gezielte Android-/Integrationstests, ARM64-Build und Lint bestanden.
- Neuer Einstellungen-Einstieg: bis zwei Schritte erstellen/bearbeiten/löschen;
  Modell, Sprache, Ton und ursprüngliche Auswahl/vorheriges Ergebnis festlegen.
- Import validiert und zeigt sämtliche Schritte. Erst „Konfiguration aktivieren“
  ersetzt den gespeicherten Stand, ohne Ausführung. Export enthält Konfiguration.
- Neuer Compose-/Robolectric-Test: Datei wählen, Activity stoppen/fortsetzen,
  Importvorschau anzeigen, gespeicherten Stand vor Aktivierung unverändert prüfen,
  danach den aktivierten Stand. Native Bitmap-Vorschau des Dialogs visuell geprüft.
- ON_STOP entfernt CE-Oberfläche. SaveableStateHolder erhält ausschließlich die
  SAF-Auftragsmetadaten/Launcherkeys; keinerlei Editorinhalt oder Credential.
- Review fand Capture-Race zwischen altem Card-Inhalt und neuer Generation; alle
  UI-Callbacks erfassen jetzt Definitionen und Generation gemeinsam aus demselben
  dargestellten Snapshot. Rereview ohne weiteren Befund.
- Echte SAF-Provider-/Rotations-/Lock-Geräteprüfung offen. Compose-WindowCapture
  wartete im Test auf einen nicht gelieferten Redraw; native Canvas-Aufnahme des
  Dialogs liefert die geprüfte Vorschau ohne Änderung der funktionalen Assertions.
- Start aus dem Tastaturpanel folgt als eigenständiger Schritt13b3.

## Härtung — Kopieren prüft die aktuelle Schutzgrenze

- 119 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- Copy prüft denselben aktuellen Token/Datenschutzstatus wie Verarbeitung und
  Übernahme. Inkognito zwischen Vorschau und Klick sperrt auch einen direkten
  Callback, bevor die UI ihre deaktivierte Darstellung erhalten hat.
- Erkannte Staleness verwirft Quelle, Ergebnis und Freigaben; beide sichtbaren
  Vorschauen werden gelöscht. Auch Stale-Cancel und abgewiesene Übernahme tun das.
- Native Tests prüfen unveränderte Zwischenablage bei Inkognito und Stale-Cancel,
  gültige bewusste Kopie mit IS_SENSITIVE und unveränderten Editorinhalt.
- Unabhängiges Review ohne Copy-Bypass; dort genannter Cancel-Konsistenzpfad
  ebenfalls korrigiert und gezielt geprüft. Keine InputLogic-/IC-Mutation geändert.

## Etappe13b3a — journalisierter Workflow-Runner

- 127 gezielte Android-/Integrationstests, ARM64-Build und Lint bestanden.
- JournaledModelCall extrahiert die geprüften Intent-/Abschlussregeln für Einzel-
  und Workflow-Aktionen. Nach begin nochmals Abbruch und ursprünglichen Editor
  prüfen. Text wird weiterhin ausschließlich an den Provider übergeben.
- startWorkflow führt den Core unter95s-Budget aus. Checkpoints prüfen den Job
  des zeitbegrenzten Laufs. Timeout wird als Ergebnisfehler zurückgegeben und
  unbeantworteter Aufruf UNKNOWN; manuelles Cancel lokalCANCELLED. Nicht abbrechbare
  Journal-I/O kann das Budget überziehen, ohne spätere Veröffentlichung zu erlauben.
- LAZY-Job vor Ausführung zugewiesen; auch synchroner/reentranter Cancel verhindert
  Schritt2. Lokale Abläufe initialisieren keinen Provider und keinen Modellauftrag.
- Acht neue Runnerfälle plus bisherige acht Single-Action-Fälle: sequenzieller
  Datenfluss, nur Endergebnis, Sperrwechsel beim Begin, Cancel zwischen Schritten,
  Timeout, unsupported-provider-Preflight, Teilfehler und verspäteter alter Lauf.
- Unabhängiges Astra-Code-Review ohne Blocker. Einmalige Freigabe und native Panel-
  Anbindung folgen13b3b. Journal aktuell pro AI-Schritt, kein Gesamtworkflow-Resume.

## Etappe13b3b — native Workflow-Freigabe und Ergebnisvorschau

- 130 gezielte Android-/Integrationstests, ARM64-Build und Lint bestanden.
- Native Workflowliste lädt IO und bleibt an den ursprünglichen Snapshot gebunden.
  Vollständige Bestätigung nennt echte Aktionen, Anbieter/Modelle, Eingaben und
  mögliche Weitergabe von Zwischenwerten. Importierter Name ersetzt diese Angaben nie.
- Unveränderliche Definition wird vor Coroutine-Start einmalig verbraucht. Spätere
  Konfigurationsimporte ändern den genehmigten Ablauf nicht; doppelte Sendeklicks
  starten keine zweite Anfrage. Close/Cancel/Stale löschen beide Freigabetypen.
- Native Tests: lokaler Ablauf ohne Provider/Transport, separater Apply; Cloud mit
  genau freigegebenem Zwischentext trotz zwischenzeitlichem Same-ID-Import;
  Stale-Direktcallback ohne Provider. Analyse-Sperre kommt aus dem Core-Ergebnis.
- Später Cancel nach schnellem lokalen Abschluss löscht nun ebenfalls Ergebnis/
  canApply; Direkt-Apply bleibt danach ohne Wirkung. Neue Freigabe ist erforderlich.
- Unabhängiges Sol-Review ohne Consent-/Race-Befund.
- Vollständiger Lauf vor dieser letzten Cancel-Ergänzung:311 Tests, genau fünf
  ursprüngliche Baseline-Fehler. Anschließende gezielte Suite einschließlich
  zusätzlichem Cancel-Fall vollständig grün. Echte Geräte-/API-Prüfungen offen.

## Etappe13c1a — deterministische Bedingungslogik

- 43 Kernprüfungen,132 gezielte Android-/Integrationstests, ARM64-Build und Lint
  bestanden. Acht neue Kernfälle plus Freigabedarstellungs-/Speicherschutztests.
- NON_EMPTY, CONTAINS_LITERAL (case sensitive) und LENGTH_AT_LEAST (UTF-16),
  optionale Negation; kein Regex/Code. Literal höchstens128 Zeichen, gültiges UTF-16,
  keine BiDi-Steuerzeichen; Zeilenumbrüche/Tabulatoren eindeutig escaped.
- Falsch: Eingabe unverändert weiterreichen, kein Modellaufruf und kein neu
  budgetierter Ausgabetext. Vorhandene Analyse-Sperre bleibt erhalten; ein
  übersprungener Analyseschritt erzeugt keine Analyseausgabe/Sperre.
- Ergebnis enthält unveränderliche Listen ausgeführter/übersprungener IDs, keine
  zusätzliche Inhaltskopie. Native Anzeige nennt tatsächliche Modellanfragen/Skips.
- Review fand U+2028/U+2029-Zeilentrenner: ebenfalls sichtbar escaped, Regressionstest.
  Unabhängiges Rereview ohne weiteren Befund.
- Speicherformat1 lehnt bedingte Definitionen ausdrücklich ab. Bedingungen sind
  noch keine erstell-/importierbare App-Funktion; Format2 und Editor folgen13c1b.

## Etappe13c1b — Bedingungsformat und Editor

- 43 Kernprüfungen,137 gezielte Android-/Integrationstests, ARM64-Build und Lint
  bestanden. Bedingungseditor als native Bitmap visuell geprüft.
- Austauschformat2 mit condition:null oder strengem Regelobjekt. Format1 wird
  ausdrücklich bedingungslos gelesen; neue Felder in v1 bleiben unzulässig.
  Bestehender Speicherwrapper1/Generationstoken unverändert. Neue Exporte Format2.
- Unfertige Literal-/Zahleneingaben bleiben reine Drafts, Speichern erst nach
  vollständiger Core-Validierung. Importierte Regeln bleiben beim Bearbeiten erhalten.
- Compose-Test: leeres Literal sperrt Save, Text/Negation eingeben, dieselbe Regel
  nochmals wählen, Save, gespeicherten Inhalt prüfen. Review fand Same-Predicate-
  Reset; behoben und per Test gegen Datenverlust abgesichert, Rereview bestanden.
- Nativer End-to-end-Test: false AI-Bedingung initialisiert keinen Provider,
  zeigt0Modellanfragen/1Skip und verändert den Editor nicht. Alle realen Regeln
  und Fallback sind in Import-/Aktivierungs-/Ausführungsansichten sichtbar.
- Falsche Versionen, Typen, unbekannte Prädikate/Felder und v1-Bedingungsfelder
  verändern keine vorhandene Konfiguration. Gerätetests bleiben offen.

## Etappe13d1 — versionierte Skill-Verweise

- 45 Kernprüfungen,140 gezielte Android-/Integrationstests und ARM64-Build bestanden.
- Skillformat2 speichert zusätzliche workflowId:null/String. Genau WORKFLOW plus
  kanonische UUID ist erlaubt; Builtins dürfen keine versteckte Referenz tragen.
- Format1 wird ausdrücklich zu Builtin/null migriert. Neue Felder oder WORKFLOW
  unter v1, falsche UUID-/JSON-Typen und inkonsistente Kombinationen werden vor
  jeder Mutation abgewiesen. Export enthält nur Referenz, keinen Workflowinhalt.
- Workflow-Editor und Codec gestatten keine verschachtelten Workflow-Schritte.
- Unabhängiges Sol-Review ohne Format-/Capabilitybefund. UI-/Tastaturverdrahtung
  der Referenz folgt13d2; derzeit keine automatische oder implizite Ausführung.

## Etappe13d2 — Workflows auf Skill Keys

- 147 gezielte Android-/Integrationstests, ARM64-Build und Lint bestanden; Core
  weiterhin45Prüfungen. Neuer Slot-CAS-Test ergänzt die beiden Compose-/vier Nativefälle.
- Skill-Einstellungen wählen gespeicherte Workflows, speichern ausschließlich UUID
  und Alias. Default-Alias bleibt gültiges UTF-16; fehlende importierte UUID wird
  angezeigt und bei Umbenennung beibehalten statt auf ersten Ablauf umzubiegen.
- Slot-Schreibzugriff kann die angezeigte alte Belegung vergleichen; ein veralteter
  Editor überschreibt keinen zwischenzeitlichen Import. Andere Slots bleiben mergebar.
- Tastendruck lädt Belegung und Definition IO, prüft Source-Identität/Policy und
  öffnet nur die vollständige Freigabe. Lokaler Ablauf benötigt Start plus Apply;
  Cloud-Alias ersetzt niemals Anbieter/Modell/Schritte. Fehlende UUID ohne Fallback.
- Neue Nativefälle prüfen lokalen Workflow, irreführenden Cloud-Alias ohne Anfrage,
  Missing-Referenz trotz anderem gespeicherten Ablauf sowie Stale während IO.
- Compose-Fälle prüfen UTF-16-sicheren Alias, unveränderte Tastaturpräferenzen,
  gespeicherte UUID ohne Workflowkopie und Umbenennen eines fehlenden Verweises.
- Unabhängiges Sol-Review ohne Capability-/Racebefund. Echte Geräteprüfung offen.

## Etappe13e — größere Abläufe und genaue Datenverweise

- 48 Kernprüfungen,151 gezielte Android-/Integrationstests, ARM64-Build und Lint
  bestanden. Astra-Review ohne Referenz-/Migrations-/Budgetbefund.
- Format3 erlaubt bis8Schritte/max3Modellaufrufe. V1/V2 bleiben ausdrücklich bei
  höchstens2Schritten; v1keineBedingung, v2/v3strengeBedingung. Kein impliziter Upgrade.
- ManualDraft nutzt nullable inputStepIndex statt Boolean/hardcodedstep1. Import-
  Referenzen bleiben beim kanonischen Umbenennen erhalten, nur frühere Indizes erlaubt.
  AddStep nimmt zuletzt vorhandenen Schritt als Default. Editor, Karten und Freigabe
  nennen exakt das referenzierte Ergebnis von SchrittN.
- Core/Manual/Compose/Native prüfen nicht benachbarte Verweise; acht lokale Schritte,
  drei erlaubte Modellaufrufe und Ablehnung von vier. Textbudget32K/AIinput8192/
  output16384 und95s-Gesamtbudget bleiben erhalten; Höchstzahlen garantieren keinen
  Abschluss bei zu großen Zwischenwerten oder langsamen Providern.
- Erster Volltest332/23: fünf Baselinefehler plus18 TempDirectory-Initialisierungs-
  fehler wegen temporärer Festplattenbelegung; kein Produktbefund. Robolectric hält
  App-/Wörterbuchkopien bis zum Workerende. App-Tests jetzt forkEvery1 proTestklasse,
  keine Assertion abgeschwächt oder ausgeschlossen. Temporärdateien werden beim
  Workerende freigegeben; keine Benutzerdateien zur Bereinigung gelöscht.
- Wiederholung332Tests/genau5ursprünglicheBaselinefehler, keine Hintergrundexception.
  Zwei bekannte Textabweichungen und drei externe Link-/Formatfehler bleiben offen.

## Etappe13f1 — allgemeine verschlüsselte Laufmetadaten

- 156 gezielte Android-/Integrationstests, ARM64-Build und Lint bestanden; Core48.
- RunSubject unterscheidet Textaktion, Workflowdefinition/Revision und Builder.
  Textaktionen können einen streng validierten Workflow-/Run-/Step-Link tragen.
  Eltern haben keine Provider-/Modellfelder; Modellaufrufe benötigen beide.
- Journal-JSONschema2 mit expliziter v1-action→TextSubject-Migration. AES-Alias und
  AADv1 bleiben der bestehende Kryptokontext; kein Schlüsselwechsel/Regenerieren.
- GenericJournaledModelCall unterstützt Builderdaten ohne Editor-Token und prüft
  beforeCall nach dem unverlierbaren Intent. Workflow-Eltern dürfen darüber keine
  Modellanfrage starten. Neue Titel/Abschlusstexte unterscheiden die tatsächliche Rolle.
- Neue Crypto-/Legacy-/strikteSchemafälle und zwei Builder-Callerfälle grün.
  Ein erster neuer Test musste den Unit-Rückgabetyp für JUnit4 ausdrücklich machen;
  danach vollständige gezielte Suite grün. Keine Funktionsassertion abgeschwächt.
- Astra-Review ohne Krypto-/Type-/Scopeblocker. Noch keine Elternproduzenten;
  Verknüpfung im Runner folgt13f2, Builderoberfläche14. Keine Inhaltsretention.

## Etappe13f2 — Workflow-Gesamtlauf und Schrittverknüpfung

- 161 gezielte Android-/Integrationstests, ARM64-Build und Lint bestanden; Core48
  unverändert. Unabhängiges Astra-Review ohne blockierenden Befund.
- Gemeinsamer JournaledRun speichert Intent und terminale Metadaten ohne Inhalte.
  Der Elternlauf umfasst lokale und AI-Schritte; Modellaufträge referenzieren
  dessen ID sowie Definition, Revision und Step-ID. Ergebnis erst nach Parent-Finish.
- Konkreter zeitbegrenzter Job bleibt Checkpoint-Grundlage. Timeout UNKNOWN,
  manueller Cancel CANCELLED; Abbruch während begin verliert die gespeicherte ID
  nicht. Parent-Intent-/Finish-Fehler verhindern Provider bzw. Ergebnisanzeige.
- SDK21/22 führt rein lokale Workflows ohne Keystore-Metadaten aus; keine behauptete
  Persistenz. AI dort weiter gesperrt. Zusätzlicher Crypto-Test prüft Locked-Finish
  ohne Zugriff auf nicht verfügbaren CE-Verzeichnispfad; nach Unlock UNKNOWN.
- Status löst Workflow-Namen ausschließlich über passende aktuelle ID/Revision
  auf; kein persistierter historischer Name. API-/Geräteprüfungen bleiben offen.

## Etappe13g — gezielte Wiederherstellung beschädigter Konfiguration

- 165 gezielte Android-/Integrationstests, ARM64-Build und Lint bestanden; Core48
  unverändert. Sol-Read-Review ohne
  Consent-/Atomizitätsblocker; dessen Robustheitsfund (unbegrenzte Hashlesung)
  durch1MiB-Obergrenze behoben. Größere Dateien bleiben unangetastet.
- Nur eine erneut als unlesbar geprüfte Datei mit identischem SHA256 kann nach
  zweiter Dialogbestätigung durch eine gültige leere Konfiguration ersetzt werden.
  Gesunde, inzwischen reparierte/ersetzte, fehlende und gesperrte Dateien werden
  nicht zurückgesetzt. Reset betrifft keine Keys, Profile, Laufdaten oder Skills.
- Drei Storetests und ein Composefall prüfen Zugriffsschutz, Übergröße, stale
  Bestätigung, Dateierhalt bei Cancel, expliziten Reset und erneute Bearbeitbarkeit.
  Native Dialogvorschau visuell geprüft. Keine Fehlertexte/Inhalte geloggt.
- Der UI-Test fand eine Renderlücke bei null→null-Konfiguration: Recovery-/Fehler-
  und Busy-Zustände werden nun im Elternscope eingefroren. Regressionslauf zeigte
  denselben Beobachtungspfad im Skill-Screen; Parent-Captures ergänzen, ohne
  key(binding), fehlende UUIDs oder store.set(saved,binding)-CAS zu ändern.
- Diagnose- und Clock-Pumpversuche wieder entfernt. Vor roher Dialog-Bitmapaufnahme
  wartet der Test auf die sichtbare Bestätigung; fachliche Assertions unverändert.
  Abschließend vollständige gezielte Suite grün. Geräteprüfungen offen.

## Etappe14a — geschlossener Act-Builder-Vertrag

- 48 Kernprüfungen,28 relevante App-Tests (Buildervertrag, OpenAI, WorkflowStore)
  sowie ARM64-Build und Lint bestanden.
  Unabhängiges Astra-Review ohne Schema-/API-/Validierungsblocker.
- Fester Outputformat-Enum und feste Providerfähigkeit; kein frei übergebenes
  Schema. Responses text.format verwendet app-eigenes geschlossenes JSONSchema.
  Normaler Textauftrag behält seinen bisherigen HTTP-Körper, Builder4096Tokens.
- Sechs Vertragsfälle plus neuer Providerfall prüfen neue App-Identität/Revision,
  eingefrorenes Modell, unveränderte Transportgrenzen, rückwärtige Referenzen,
  Bedingungen, Typ-/Unicode-/Größen- und Schrittgrenzen sowie verbotene Felder.
- Unbekannte Identität, Provider-/Modellwahl, Prompts, URLs und Tools werden vor
  weiterer Verarbeitung abgewiesen; Decoder verwendet denselben Codec/Core.
  Keine Speicherung oder Ausführung. Noch kein UI-/Runnerproduzent; folgt14b/c.
- Ein Testfixture ersetzte zunächst auch einen Action-Namen beim Umbenennen einer
  Step-ID; präzisiert und relevant wiederholt. Keine Funktionsassertion entfernt.
  Echte Modell-/Geräteprüfungen bleiben offen.

## Etappe14b — freigegebene Entwurfsgenerierung

- 36 relevante Runner-/Vertragstests, ARM64-Build und Lint bestanden. Zehn neue
  Builderfälle; Core48 unverändert. Astra-Read-Review ohne blockierenden Befund.
- prepare friert Text/Modell ohne IO ein. start akzeptiert genau die aktuelle
  identitätsgebundene Freigabe, verbraucht sie vor Coroutine-Start und installiert
  einen LAZY-Job vor synchroner Arbeit. Feste OpenAI-/Structured-Capabilitygrenze.
- Dauerhafter Intent vor Providerkonstruktion; Validierung gehört zum gespeicherten
  Builderlauf. Ein Entwurf wird erst nach gültigem Ergebnis und erfolgreichem
  Terminalschreiben veröffentlicht. Keine Editor-, Speicher- oder Ausführungsautorität.
- Tests prüfen Doppelsenden, alte/fremde Freigaben, Cancel während begin, Privacy-
  Wechsel in begin/generate/finish, Storagefehler vor/nach Provider, ungültige
  Ausgabe, unsupported-provider und Capability, Timeout sowie nicht kooperativen
  alten Provider. Timeout UNKNOWN, lokaler Cancel CANCELLED, Fehler neutral.
- 65s Gesamtbudget kann durch nicht abbrechbare Journal-IO überschritten werden;
  Job-/Epoch-/Policy-Prüfung verhindert trotzdem späte Veröffentlichung. Host muss
  Main-Thread, Lifecycle- und Inkognito-Abbruch liefern; Oberfläche folgt14c.
  Keine Live-API-/Geräteprüfung behauptet.

## Etappe14c — Act Builder mit privater Entwurfsprüfung

- 189 gezielte Android-/Integrationstests, ARM64-Build und Lint bestanden, Core48
  unverändert. Vollständige Regression370Tests/genau5geerbteFehler, keine neue
  Hintergrundexception. Identische Baselinefälle: Hangul, Emoji, zwei externe
  Links (diesmal503statt404) und externes Dictionaryformat.
- Sechs neue Builder-UI-Fälle und ein zusätzlicher ManualDraft-Fall. Genaue
  Sendefreigabe ist vom Generieren und vom Speichern getrennt. Modell und
  Konfigurationsgeneration stammen unveränderlich aus dem beim Öffnen dargestellten
  Stand. Neue Entwürfe bleiben Revision1; Store-CAS verhindert stale Aktivierung.
- Tests belegen kein Provider vor Send, keine Konfiguration vor Save, kein zweiter
  Auftrag bei deaktiviertem Send, eingefrorenes Modell trotz Einstellungsänderung,
  unveränderte neu importierte Konfiguration, Cancellation bei Hintergrundwechsel
  und leere neue Formulare nach Rückkehr bzw. Incognito.
- SaveableStateRegistry enthält weder Prompt noch ungespeicherten Entwurfsnamen.
  Activity- und Dialog-Screenshotschutz werden geprüft. Native Sendungs-/Entwurfs-
  Bitmaps visuell geprüft. Keine Live-API-Anfrage, nur klar erkennbare Testprovider.
- Astra fand fehlenden Schutz beim Übergang zur Entwurfsprüfung. Ein gemeinsamer
  Parent hält FLAG_SECURE durchgehend, beide Dialoge SecureOn, Pause/Stop/Incognito
  verwerfen auch den ungespeicherten Entwurf. Name/Literal verwenden unmaskierte
  Passwort-IME-Eingabesemantik ohne Autokorrektur. Nachprüfung ohne Blocker.
- Zusätzlicher Regressionstest ruft den echten alten Save-OnClick nach Verwerfen,
  Incognito-Rückkehr und neu geöffnetem Builder direkt auf. Keine Speicherung:
  Identität, Proposal-Flag, Generation und Revision sind gebunden; busy/stale
  Callbacks werden vor IO verworfen. Preference-Widerruf erfolgt synchron auf Main.
  Auch Builder-Abschluss-/Dismiss-Callbacks können keine neue Sitzung verändern.
- Zwei anfängliche UI-Assertions erfassten auch das gemergte Textfeldlabel statt
  nur den leeren Inhalt. Präzise EditableText-Prüfung, keine Erwartung abgeschwächt.
  Tatsächliche IME-/Geräte-, Lock-/Rotations- und Live-API-Prüfungen bleiben offen.

## Etappe15 — Werkzeuge: Testwerkzeug und Kalenderübergabe

- Neue Toolchain in neuer Umgebung (JDK 17.0.21, SDK 37.0, Build-Tools 36.0.0, NDK 28.0.13004108).
  Vor der Änderung: Kern 48/0. Die Agent-Suite lief im ersten Versuch nur bis 164 Fälle, weil
  Robolectric die Android-16-Laufzeit nicht laden konnte (Maven-Ratelimit 429/IOException);
  nach manuellem Laden der Laufzeit mit geprüfter SHA-512 lief sie vollständig.
- Nach der Änderung: Kern 52/0 (vier neue `ToolTest`-Fälle), Agent-Suite 198/0 (189 bisherige,
  fünf `ToolRunnerTest`, drei `CalendarToolLauncherTest`, ein Journal-Fall). ARM64- und
  x86_64-Build sowie Lint bestanden.
- Kern: `Tools.prepare` ist rein und deterministisch; Titel höchstens 120, Beschreibung höchstens
  2000 UTF-16-Einheiten, nie ein halbes Surrogatpaar; ungültige Auswahl ergibt keine Anfrage;
  `toString` gibt keinen Text aus.
- Runner: Die Absicht steht im Journal, bevor der Launcher läuft (im Launcher geprüft:
  Zustand `RUNNING`). Genau ein Aufruf. Fehlende App → `FAILED` ohne Wiederholung. Veralteter
  Editor vor Start oder während des Journalschreibens → kein Aufruf, `CANCELLED`. Journal nicht
  schreibbar → kein Aufruf, Speicherfehler. Ohne dauerhaftes Journal läuft nur das Testwerkzeug.
- Intent: nur `ACTION_INSERT`, Kalender-URI, Titel, Beschreibung, `NEW_TASK`; kein Zielpaket.
- Panel: Werkzeugtasten ohne Auswahl deaktiviert; Vorschau zeigt Titel und vollständige
  Beschreibung; Senden ist ein eigener Klick; ein Bericht aktiviert nie „Ersetzen“.
- Journal: Schema 3. Zwei bestehende Assertions prüften die Formatnummer 2 und prüfen jetzt
  `RunSubjectCodec.SCHEMA` (3). Das ist die beabsichtigte Formatänderung, keine abgeschwächte
  Erwartung; Migration von Schema 1 und Ablehnung unbekannter Subjects bleiben geprüft.
- Befund beim Installieren: Die APK war test-only (`INSTALL_FAILED_TEST_ONLY`). Behoben in
  `tools/dev.py`; `aapt2 dump badging` zeigt kein `testOnly` mehr. Die APK enthält alle vier ABIs,
  beide Artefakte sind also gleich nutzbar.
- Offen: echte Übergabe an eine Kalender-App auf einem Gerät (siehe Gerätetest).

## Etappe16a/b — Anthropic und Gemini

- Agent-Suite 205/0 (198 bisherige, sieben neue in `MoreProvidersTest`), Kern 52/0 unverändert.
  Build und Lint: siehe Abschluss dieser Etappe im Build-Log.
- Der Transport wurde aus `OpenAiProvider` in `CloudTransport` herausgelöst. Alle zehn
  bestehenden `OpenAiProviderTest`-Fälle laufen unverändert weiter (fester Ursprung, One-shot-Body,
  kein Replay bei 503, Abbruch, BUSY, neutrale Fehler).
- Anthropic: fester Ursprung, Versions-Header, Body genau `model`, `max_tokens`, `system`,
  `messages`, `stream`; Schlüsselkopie nach Gebrauch gelöscht. Parser: nur `end_turn` mit Text;
  `refusal` → REFUSED; `max_tokens`, `tool_use`, `pause_turn`, Tool-Blöcke, falsche Rolle,
  Nicht-String und Überlänge scheitern.
- Gemini: Schlüssel nur im Header, nie in der URL; acht ungültige Modellnamen (Pfad, Query,
  Fragment, Doppelpunkt, Großbuchstaben) erzeugen keinen Aufruf. Parser: nur `STOP` mit genau
  einem Kandidaten; Sperrgründe → REFUSED; `MAX_TOKENS`, Funktionsaufrufe, zwei Kandidaten
  scheitern; Thought-Teile erscheinen nie im Ergebnis.
- Beide lehnen Workflow-Entwürfe vor jedem Aufruf ab. HTTP 401/429/400/529 werden neutral
  abgebildet, der fremde Fehlertext taucht nicht auf.
- Ein Test erwartete Gemini als nicht unterstützten Provider; er prüft dasselbe jetzt mit `local`.
- Ein eigener Testfall war falsch gestellt (leerer Modellname scheitert schon in `ModelRequest`)
  und wurde korrigiert, ohne eine Erwartung abzuschwächen.
- Offen: echte Anfragen bei allen drei Anbietern; Gemini-Vertrag nicht neu geprüft
  (`docs/PROVIDERS.md`); Bedienung der Anbieter-Auswahl auf einem Gerät.

## G1 — erster Gerätetest im Emulator (2026-11-11)

Umgebung: Android-Emulator 37.2.13, Abbild `system-images/android-35/default/x86_64` (Android 15,
480×800), reine Software-Emulation ohne KVM, vier Gast-Kerne. Bedienung über `adb shell input`,
Auswertung über Screenshots und `uiautomator dump --windows` (zeigt auch das Tastaturfenster).
Der Gast war stark überlastet; Zeitüberschreitungen fremder System-Apps sind kein App-Befund.

Geprüft und in Ordnung:

- Installation der APK ohne `-t`, Aktivierung und Auswahl als Standardtastatur.
- Tastatur erscheint im Textfeld der Nachrichten-App. Zehn Tastendrücke ergeben „Hallo welt“
  (automatische Großschreibung, unterstrichenes Wort in Bearbeitung, Vorschlagsleiste). Zweimal
  Löschen und Weitertippen ändern den Text wie erwartet.
- Agent-Taste öffnet das Rad; „Uppercase“ zeigt im Panel die Vorschau „HALLO WETT“; erst der
  eigene Klick „Replace selection“ ersetzt den Text im Feld; Panel schließt, Tastatur ist zurück.
- Auswahl während offener Vorschau geändert: Ergebnis verworfen, alle Aktionen gesperrt, Hinweis
  „The text or selection changed…“, Text unverändert.
- Testwerkzeug: Freigabetext, dann Bericht `chars=10 words=2 lines=1`; „Replace“ bleibt gesperrt.
- Kalenderentwurf: Vorschau mit Titel und Beschreibung; nach Klick startet Android
  `ACTION_INSERT` auf `com.android.calendar/.EditEventActivity` mit Extras (Systemlog, Start aus
  dem sichtbaren Tastaturfenster erlaubt).
- KI-Einstellungen öffnen sich aus dem Panel; Anbieter-Auswahl ist sichtbar; der Screenshot ist
  vollständig schwarz (`FLAG_SECURE` wirkt, auch über der Tastatur).
- Passwortfeld (API-Schlüssel): Panel meldet „Text actions are disabled in this protected field“.
- Schlüssel speichern mit dem echten Android Keystore: fünf Zeichen eingegeben (maskiert),
  „Save key“ → „A key is stored on this device“, Feld geleert, Datei
  `no_backup/agent_credentials/openai.sealed` mit 34 Byte (1 + 12 + 5 + 16) ohne Klartext;
  „Delete stored key“ → „No key is stored“, Ordner leer.
- Kein Absturz der Tastatur im Crash-Log über die ganze Sitzung.

Gefundene Fehler, behoben und auf dem Gerät nachgeprüft:

- Rad und Panel reichten unter die Navigationsleiste: „Clean spaces“, „Replace selection“ und
  „Copy result“ waren verdeckt und nicht antippbar. `AgentKeyboardFrame` kürzt Overlays jetzt um
  den von der Leiste verdeckten Teil. Nachher liegen alle Knöpfe sichtbar darüber.

Gefundene Fehler, behoben, nur durch Tests geprüft (nicht erneut auf dem Gerät):

- Bei Werkzeugen hieß der Abbruchknopf „Cancel AI action“; jetzt „Cancel“.
- Nach einer Übergabe blieb das Panel gesperrt, wenn die andere App nicht erschien; jetzt sind
  die Aktionen wieder frei.
- Nach verworfener Vorschau stand das Panel am unteren Ende, Hinweis und „Close“ waren erst nach
  Scrollen sichtbar; jetzt springt es nach oben.

Befunde zur Umgebung und zum Werkzeug:

- Die Kalender-App dieses Abbilds ist defekt (`ClassNotFoundException` für
  `EditEventActivity` in `Calendar.apk`). Ob Titel und Beschreibung in einer funktionierenden
  Kalender-App ankommen, ist deshalb weiter offen.
- Beim ersten Nachtest wurde eine veraltete APK installiert, weil `tools/dev.py` die Datei nach
  dem gewünschten ABI benannte, obwohl die APK alle ABIs enthält. Die Datei heißt jetzt nach
  ihrem tatsächlichen Inhalt (`…-universal.apk`).
- Start der Tastatur: beim ersten Versuch (Gast-Last 15–20) „failed to complete startup“; bei
  Gast-Last um 5 startete sie normal. Ein Startproblem der App ist damit nicht belegt.

Nicht geprüft: Rotation, TalkBack, Mehrfinger- und Wischgesten, Leertasten-Langdruck, Wechsel
zwischen Feldern und Apps unter Last, Inkognito, gesperrtes Gerät, Direct Boot (Neustart vor dem
ersten Entsperren), Compose- und WebView-Felder, Netzwerkmitschnitt beim Tippen, Profile, Skill
Keys, Workflows, Act Builder und jede echte KI-Anfrage. Ein echtes Telefon wurde nicht benutzt.

## Etappe17a — Lerndaten verwalten

- Agent-Suite 210/0 (206 nach G1, vier neue in `LearningDataTest`), Kern 52/0, Build und Lint
  bestanden.
- Pause: Voreinstellung aus; der Schalter schreibt genau einen Schlüssel und erreicht nach dem
  Neuladen `SettingsValues.mLearningPaused`; `blocksLearning` folgt ihm.
- Export: nur `format`, `version`, `languages`; je Wort nur `word` und `probability`; stabile
  Reihenfolge; ein zu großer Export (40 000 Wörter) wird abgelehnt statt gekürzt.
- Quelle: nur Verzeichnisse „UserHistoryDictionary.<Sprache>.dict“ zählen; fremde, leere oder
  ungültige Namen werden nicht geöffnet.
- Nicht durch Tests belegt: der Abbruch in `DictionaryFacilitatorImpl.addToUserHistory` selbst
  (die Tests ersetzen die Wörterbuchschicht), das Lesen echter Lerndaten über die native Engine
  und die Bedienung der Seite. Das gehört in G2.
- Geänderte Upstream-Dateien: `SettingsValues.java` (ein Feld), `DictionaryFacilitatorImpl.kt`
  (ein früher Rücksprung). `InputLogic`, `RichInputConnection` und die Touch-Verarbeitung sind
  unverändert.

## Etappe18a1 — sichere Einfügestelle

- Kern 54/0 (zwei neue `InsertionAnchorTest`-Fälle), Agent-Suite 214/0 (vier neue
  `InsertAtCursorTest`-Fälle), Build und Lint bestanden.
- Kern: Anker nur bei schlichter Schreibmarke in einem erlaubten Editor; Passwort, Inkognito,
  Lernverbot, Sperre, Komposition und Nicht-Textfelder werden abgelehnt. Einfügen wird nach jeder
  Änderung (Position, Auswahl, Token) und für leeren, zu langen oder ungültigen Text abgelehnt.
- Verbindung: genau ein Commit an der geprüften Position; verschobene Schreibmarke oder eine
  hinter dem Rücken der Tastatur entstandene Auswahl → kein Commit, nie eine Ersetzung;
  abgelehnter oder verlorener Commit → UNKNOWN ohne zweiten Versuch.
- Adapter: liest keinen Editortext; Anker wird vor dem Commit verbraucht (Doppelklick wirkungslos);
  ohne Commit-Pfad, nach Bewegung, in Inkognito und im Passwortfeld keine Einfügung.
- `InputLogic` und `RichInputConnection` wurden eng geändert: Die Nachbereitung der Ersetzung
  wurde unverändert in eine gemeinsame Methode gezogen, die bestehenden
  `SelectedTextCommitTest`- und `InputLogicTest`-Fälle laufen unverändert durch. Die Änderung
  bekommt nach Projektregel einen eigenen lesenden Review.
- Noch keine Oberfläche benutzt die Einfügestelle; das folgt mit dem Diktat (18a2).

## Lesender Review der Etappen 15–17a und Korrekturen

Ein unabhängiger, nur lesender Review über `28163d1..b992cb2` (Werkzeuge, Anbieter, Gerätetest-
Korrekturen, Lerndaten) fand keinen Blocker. Bestätigt wurden u. a.: einmalige Freigaben und kein
Doppelstart bei Werkzeugen und Cloud-Anfragen, Journal vor externer Wirkung, eigener
Schlüsselplatz je Anbieter, Schlüssel nie in der URL, unverändertes Verhalten von OpenAI nach dem
Herauslösen des Transports, Parser nehmen nur abgeschlossene Antworten, Gemini-Modell kann Host,
Pfad und Query nicht verändern, keine Layout-Schleife im Tastaturrahmen.

Sechs Befunde, alle am Code nachvollzogen und behoben:

1. Zählen und Export konnten nach 100 ms Zeitüberschreitung „0 Wörter“ bzw. eine leere Datei als
   Erfolg melden. Jetzt blockierendes Lesen mit 10 s; Zeitüberschreitung ist ein Fehler.
2. `max_tokens` 2048 bei Anthropic und Gemini konnte bei Modellen mit Thinking bezahlte Anfragen
   abgeschnitten enden lassen. Jetzt 8192, und Abschneiden wird als „zu lang“ gemeldet.
3. Nach Neuaufbau der Seite bei offenem Dateidialog blieb eine leere Exportdatei ohne Meldung.
   Jetzt wird sie entfernt und ein Fehler gezeigt.
4. Löschen warf die Wörterbücher aus dem gemeinsamen Cache; danach arbeiteten Tastatur und
   Seite auf getrennten Objekten. Jetzt wird an Ort und Stelle geleert und darauf gewartet.
5. Bei Pause wird ein getipptes Wort auch nicht von der Sperrliste genommen. Beibehalten und
   dokumentiert: Bei Pause ändert Tippen keine gespeicherten Daten.
6. Beim Wechsel des Anbieters eines Workflow-Schritts blieb die alte Modellkennung stehen, und
   eine für Gemini ungültige Kennung ließ sich speichern. Jetzt setzt der Editor das Modell des
   neuen Anbieters ein, die Umwandlung prüft je Anbieter, und das Panel weist einen Workflow mit
   unbrauchbarer Modellkennung vor der Freigabe ab.

Zusätzlich: Zwei Upstream-Fehlermeldungen der Sperrliste schrieben das betroffene Wort ins Log;
sie nennen es nicht mehr.

Nach den Korrekturen: Agent-Suite 216/0 (zwei neue Fälle, zwei erweiterte), Kern 54/0, Build und
Lint bestanden. Die Punkte 1 und 4 betreffen die native Wörterbuchschicht und sind nur am Code
geprüft, nicht durch einen Test mit echten Lerndaten.

## Lesender Review von 18a1 und Korrekturen

Der nach Projektregel nötige unabhängige Review der Änderung an `InputLogic` und
`RichInputConnection` (`cea7096`) fand keinen Blocker. Bestätigt: Die Umstellung in `InputLogic`
ist bis auf eine Zeile identisch und verhaltensgleich; `insertTextIfCursorUnchanged` hat dieselben
Schutzprüfungen, dieselbe Fehlerbehandlung und dieselbe Cache-Auffrischung wie die Ersetzung; der
Adapter verbraucht den Anker vor jedem Commit; kein veralteter Anker passiert die Policy.

Befunde, am Code nachvollzogen und behoben:

1. Fail-open: `getSelectedText` liefert `null` auch, wenn die App nicht antwortet. In einer
   eingefrorenen App hätte ein später verarbeiteter Commit eine inzwischen entstandene Auswahl
   ersetzen können. Jetzt wird vor dem Commit eine Antwort des Editors verlangt
   (`getTextBeforeCursor(1, 0) != null`), sonst gibt es keinen Commit.
2. Testlücken: Nach UNKNOWN wird jetzt geprüft, dass die Caches verworfen sind; neu sind die
   Fälle „Commit gelungen, Auffrischung gescheitert“ und „Editor antwortet nicht“; der Text nach
   verlorener Antwort wird geprüft. Drei Assertions, die auch ohne den geprüften Code bestanden
   hätten, wurden ersetzt (leerer Text und Position -1 jetzt in Zuständen, in denen nur die
   Schutzprüfung den Commit verhindert; Lesezähler statt wirkungsloser Ausnahme; exakter
   `toString`).
3. `InputLogic.insertGeneratedText` hatte keinen Aufrufer und keinen Test. Jetzt vier
   `InputLogicTest`-Fälle: Einfügen mit konsistenten Caches und einzelnem Löschen danach;
   Ablehnung während einer Komposition ohne Änderung der Lernunterdrückung; automatisches
   Leerzeichen nach gewähltem Vorschlag; gleiches Verhalten wie Tippen in Text- und URL-Feld.

Aus den Hinweisen des Reviews für 18a2 übernommen: ein ausstehendes automatisches Leerzeichen
wird beim Einfügen mitgesetzt (sonst „wordHallo“); eigene Meldungen für „keine Schreibmarke“ und
„Wort noch in Arbeit“.

Ein eigener neuer Testfall für URL-Felder ging zunächst von einer falschen Annahme über das
Upstream-Verhalten aus und wurde durch einen Vergleich mit dem Tippen im selben Zustand ersetzt.

## Etappe18a2 — Diktat auf dem Gerät

- Kern 54/0, Agent-Suite 236/0 zum Zeitpunkt des Etappen-Commits (216 zuvor; elf `DictationSessionTest`, drei `DictationUiTest`,
  zwei zusätzliche `InsertAtCursorTest`, vier `InputLogicTest`), Build und Lint bestanden.
- Ablauf (`DictationSessionTest`, reiner JVM-Test mit nachgebautem Erkenner): keine Aufnahme bei
  Auswahl, Passwortfeld, Inkognito, fehlendem Erkenner oder fehlendem Mikrofonrecht (der Erkenner
  wird in keinem dieser Fälle gestartet); Gehörtes wird nie von selbst eingefügt; Einfügen nur
  einmal; während des Zuhörens kein Einfügen; Änderung des Editors beim Zuhören stoppt das
  Mikrofon und ignoriert späte Ergebnisse; Änderung in der Vorschau verwirft den Text; Text nach
  bewegter Schreibmarke wird nicht angeboten; Abbruch, Fehler, Stille, Überlänge und ungültiger
  Text hinterlassen nichts; sofort scheiternder Erkenner lässt keine Sitzung offen; späte
  Rückrufe einer alten Erkennung erreichen keine neue; unklares Einfügen wird nicht wiederholt.
- Oberfläche (`DictationUiTest`): Panel trennt Zuhören, Vorschau und Einfügen; Beschriftungen
  fallen danach auf „Ersetzen“ zurück; auf SDK 28 ist der Erkenner „nicht verfügbar“ und ein Start
  meldet das, statt auszuweichen; Netzfehler des Erkenners werden nie zu „online wiederholen“;
  die Taste hat einen eigenen Code und ist standardmäßig nicht in der Toolbar.
- API gegen die offizielle Referenz geprüft (`docs/PROVIDERS.md` → Sprache und lokale Modelle).
- Neue Berechtigung `RECORD_AUDIO` für die ganze APK; sie wird nur auf der Seite „Diktat“
  angefragt.
- Nicht geprüft: echter Erkenner, echtes Mikrofon, ob Android dem Tastaturdienst die Aufnahme
  erlaubt, Sprachpakete, die Einstellungsseite samt Berechtigungsdialog, das Symbol der Taste.
  Das Emulator-Abbild hat keine On-device-Erkennung. Das gehört auf ein echtes Telefon (G2).

## Lesender Review von 18a (Korrektur und Diktat) und Korrekturen

Der dritte unabhängige Review (`a5acd76`, `f4d60e6`) fand keinen Blocker: kein Pfad fügt ohne
Klick ein, ersetzt eine Auswahl, committet doppelt oder erzeugt den allgemeinen Erkenner.

Behoben:

- Sperre oder Inkognito ohne Editor-Ereignis stoppte das Mikrofon nicht. Jetzt prüft jedes
  Zwischenergebnis den Editorzustand und bricht ab; das Panel hält beim Zuhören den Bildschirm an.
- Der Diktier-Knopf war auch bei vorhandener Auswahl aktiv, wo er nur scheitern konnte und ein
  vorbereitetes (bezahltes) Ergebnis verwarf. Er ist jetzt nur ohne Auswahl aktiv.
- Reihenfolge beim Abbruch: erst Zustand zurücksetzen, dann den Erkenner abbrechen; ein
  Erkenner, der aus `cancel` zurückruft, kann nichts mehr veröffentlichen.
- „Einfügen“ während des Zuhörens tut nichts mehr (vorher wurde der Änderungs-Hook entfernt).
- Nach „Fertig“ gibt es einen eigenen Zustand ohne zweiten Stopp.
- Diktat-Seite: Hinweis „nicht freigegeben“ verschwindet nach späterer Freigabe; „Mikrofon
  erlauben“ erscheint nur, wenn es eine On-device-Erkennung gibt. Sprachmeldung deckt
  „nicht unterstützt“ und „nicht installiert“ ab.

Nach den Korrekturen: Agent-Suite 238/0 (zwei neue `DictationSessionTest`-Fälle, erweiterte
Panel-Prüfungen), Kern 54/0, Build und Lint bestanden.

Bewusst offen gelassen und im Handoff vermerkt:

- Die Verdrahtung im Controller (`openDictation`, Abbruch bei `close`, Tastencode) und der
  Android-12-Pfad des Erkenners haben keinen eigenen Test.
- Das verbleibende Zeitfenster beim Einfügen (App antwortet auf die erste Abfrage und friert
  dann ein) ist nur verkleinert, nicht geschlossen.
- Zwei `InputLogicTest`-Fälle beweisen weniger, als ihr Name sagt: Der Vergleich Text-/URL-Feld
  trifft in beiden Fällen den Zweig „Leerzeichen wird gesetzt“; die Ablehnung während einer
  Komposition trennt die beiden Schutzprüfungen nicht.
- Kein Zeitlimit, falls der Erkenner nach „Fertig“ nie antwortet (Abbrechen bleibt möglich).

Gerätecheck des Stands `f4d60e6` im Emulator: Panel mit Diktier-Knopf wird korrekt dargestellt;
ein Tipp auf „Dictate“ bei einer Schreibmarke direkt an einem Wort meldet „Finish the current word
first…“, ohne Absturz. Die Erkennung selbst gibt es im Emulator nicht.

## Etappe19a — flacher Look, Tastenfläche

Auftrag des Owners vom 2026-11-12: Aussehen nach fünf Screenshots (1200 × 2670 px) nachbauen,
als Ausgangspunkt für ein eigenes Design.

- Vermessen: Tasten, Lücken, Farben, Schriftgrößen und Icons aus den Screenshots (Pixelmaße).
  Die Dichte 3,25 px je dp ist aus wiederkehrenden Maßen geschlossen (26 px = 8 dp, 19,5 px =
  6 dp, 111 px ≈ 34 dp), nicht am Gerät abgelesen.
- `FlatStyleTest` (7 Fälle): Vorlage setzt und restauriert genau ihre 18 Einstellungen, auch
  „war nicht gesetzt“; zweites Anwenden überschreibt die Sicherung nicht; Lernen, Verlauf und
  fremde Einstellungen bleiben unberührt; Höhenskala; Stil und Farbwerte; Tastengeometrie bei
  1200 × 793 px gegen die Messwerte mit höchstens 1 px Abweichung (Reihen bei 39/178/327/477/627,
  Höhen 117/123/124/124/134, Tastenbreite 102, Lücke 16, Leertaste 459); andere Stile behalten
  gleich hohe Reihen und ihre Icons.
- `FlatStyleRenderTest` (2 Fälle): zeichnet die echte `MainKeyboardView` mit nativer Grafik bei
  1200 px Breite und 3,25 px je dp und prüft Farben, 6-dp-Ecken und Pillen an Pixeln.
- Bildvergleich des gerenderten Bilds mit den beiden Tastatur-Screenshots (Skript, nicht im
  Repo): Tastenkanten in allen fünf Reihen identisch bis auf 1–2 px; Ziffernhöhe 58/59 px,
  x-Höhe 43/43 px, Hinweise 26/25 px, „?123“ 111 × 39/40 px, Leertastentext 271/266 px breit,
  Icons Umschalt 79 × 77 / 81 × 76, Löschen 92 × 75 / 92 × 74, Emoji 73/72, Enter 69 × 51 / 69 × 50
  (jeweils Nachbau / Vorlage). 4 bis 6 % der Pixel weichen deutlich ab, fast nur an
  Schriftkanten: das Bild nutzt Roboto, die Vorlage eine andere Schrift.
- Bekannte Abweichungen: Die Vorlage rundet ihre 1,5 Zellen breiten Tasten links auf 161 px und
  rechts auf 163 px und setzt die Leertaste 2 px weiter links; der Nachbau liegt auf dem
  gleichmäßigen Raster. Der Schrägstrich ist in der Vorlage schmaler.
- Agent-Suite 247/0, Build und Lint bestanden (drei neue Stilhinweise „UseKtx“ in
  `FlatPreset.kt`). Vollregression mit einem Zwischenstand dieser Etappe (Tastatur-View 780 statt
  793 px, sonst gleich): 428 Tests, 7 Fehler, dieselben wie zuvor (fünf Baseline, zwei
  Link-Tests am Netzwerkfilter).
- Nicht geprüft: kein Gerät und kein Emulator. Der Emulator dieser Umgebung startete seinen
  Systemdienst wiederholt neu (Zeitüberschreitung ohne Hardware-Beschleunigung). Offen sind
  damit: Aussehen mit der Systemschrift des Telefons, Zusammenspiel mit Leiste und
  Navigationsbereich, die tatsächliche Dichte, Druckfarbe und Tastenvorschau.

## Etappe19b/19c — flacher Look, Leiste und Menü

- 19b Leiste. `FlatStyleRenderTest` (jetzt 3 Fälle) lädt das echte `strip_container`-Layout bei
  1200 px Breite: fünf Knöpfe (Menü, Zwischenablage, Emoji, Aktionen, Diktat) in genau dieser
  Reihenfolge, je 111 px, Mittelpunkte bei x = 75,5 / 338 / 600 / 861 / 1124,5 (± 2,5 px) und
  71,5 px unter der Oberkante (± 1 px), Kreis #4B4B4B hinter dem letzten, alles nach einem
  einzigen Layout-Durchlauf. Bildvergleich mit dem Screenshot: Raster 42–108 / 42–108,
  Zwischenablage 308–366 / 308–367, Diktatkreis 1069–1179 / 1069–1179 (Nachbau / Vorlage, x in
  px); der Stift lag 3–4 px daneben und ist danach verschoben worden. `FlatStyleTest` prüft die
  fünf neuen Leisten-Einstellungen der Vorlage (jetzt 23 Einstellungen).
- 19c Menü. `FlatMenuTest` (3 Fälle): sechs Pillen und der Stift-Knopf liegen bei 1200 px
  Breite auf den Messwerten der Vorlage (Spalten 78–587 und 613–1122, Reihen 208/390/572 px
  unter der Oberkante der Leiste, je 156 px hoch, Stift 888–1122 × 806–910; ± 2 px), der
  Bereich der Leiste wird nicht übermalt; jede Pille schließt erst das Menü und meldet dann
  genau ihre Aktion; die linke Leistentaste öffnet und schließt das Menü, wechselt Icon,
  Hintergrund und Beschriftung für Screenreader, blendet nur die Tasten für Screenreader aus;
  eine Toolbar-Taste schließt das Menü vor ihrer Aktion; „Bearbeiten“ startet die
  Einstellungen mit dem Ziel „toolbar“.
- Agent-Suite 251/0, Build und Lint bestanden (138 Warnungen, ein Hinweis).
- Nicht geprüft: kein Gerät, kein Emulator. Offen sind damit das Umschalten zwischen
  Werkzeugen und Wortvorschlägen beim echten Tippen, die Wirkung der Menü-Aktionen in echten
  Apps (Rückgängig hängt von der App ab), der Einhand- und der unverankerte Modus zusammen mit
  dem flachen Stil, das Menü über der Navigationsleiste (Fallstrick P18) und TalkBack.

## Etappe19d/19e — flacher Look, Zwischenablage und Panel

- 19d Zwischenablage. `FlatClipboardTest` (7 Fälle): Abtastrate (1200 px breites Bild bei 600 px
  Ziel → 2 statt früher 0, also volle Auflösung); ein 1200 × 2670 px großes Bild wird als
  600 × 1335 px dekodiert, beim zweiten Aufruf kommt dasselbe Objekt aus dem Cache, nach
  `clear()` ein neues; Kopfzeile im echten `strip_container` bei 1200 px Breite: Kreise bei
  x 20–131, 906–1017 und 1069–1180, Titel ab x 173 (± 2 px, wie in der Vorlage); Karten in
  einer echten `RecyclerView`: Spalten 22–585 und 615–1178, quadratische Bildkarte 563 px,
  26 px Reihenabstand; die echte `ClipboardHistoryView` im flachen Stil: keine Hardware-Ebene,
  untere Tastenreihe ausgeblendet, Abschnittstitel als erstes Kind, Liste bis zum unteren Rand,
  Kopfzeile mit genau Zurück, Titel und den zwei Tasten der Vorlage, Zurück sendet
  `KeyCode.CLIPBOARD`, ein zweiter Start verdoppelt nichts; Chip: dunkle Pille, rundes
  Vorschaubild vorn, kein Kreuz, langes Drücken blendet aus, Label nur wenn übergeben.
- 19e Panel. `FlatPanelTest` (4 Fälle): alle bisherigen IDs bleiben `Button` bzw. `TextView`;
  jede Aktion ruft genau ihren Callback, Übernehmen und Senden brauchen weiter einen eigenen
  Klick; runde Knöpfe tragen ihren Namen als `contentDescription`; Zurück-Kreis x 52–163, Titel ab
  x 204, Karte x 52–1148 (wie in der Vorlage); Ergebnisblock über den Chips; Kartenrand oben
  links dunkler und gelblicher als unten rechts; „Übernehmen“ in #6A96EF; bei laufender Anfrage
  Balken in #514745 statt Text, jeder andere Zustand stellt die normale Karte wieder her; in
  einem anderen Stil bleibt das Panel unverändert. Die bisherigen Panel-Tests laufen unverändert.
- Vollregression nach 19e: 443 Tests, 7 Fehler, dieselben wie zuvor (`InputLogicTest`
  Hangul, `StringUtilsTest` Emoji, fünf `XLinkTest` an externen Hosts bzw. am Netzwerkfilter).
  Build und Lint bestanden (138 Warnungen, ein Hinweis).
- Nicht geprüft: kein Gerät, kein Emulator. Offen: echte Zwischenablage-Einträge samt Bildern
  aus anderen Apps, der Eckenbeschnitt der Bildkarten (greift nur mit Hardware-Rendering),
  Scroll-Verhalten und Speicherbedarf mit vielen Bildern, der Chip mit einem echten Clip, das
  Pulsieren der Balken und das Panel über der Navigationsleiste. Nicht durch Tests abgedeckt:
  der Aufruf `styleChip` in `ClipboardHistoryManager` und der geänderte Zweig für
  `KeyCode.AGENT_ACTIONS` in `KeyboardActionListenerImpl` (nur gelesen).

## Lesender Review der Etappen 19a–19e und Korrekturen

Fünf Prüfer, je ein Bereich (Tasten, Leiste und Menü, Zwischenablage, Panel, Vorlage/Tests/Doku),
nur lesend. Für Tasten, Leiste/Menü und Panel hat je ein zweiter Prüfer jeden Befund am Code
nachvollzogen und alle 21 bestätigt. Die Gegenprüfungen für Zwischenablage und
Vorlage/Tests/Doku brachen am Nutzungslimit ab; deren 15 Befunde hat der Lead selbst am Code
geprüft. Kein Blocker. Behoben:

- Tasten: Seitenrand wieder über die gemeinsame 8-%-Einstellung (Vorlage setzt 0,073);
  „Flach schwarz“ ohne Tastenrahmen malt keine deckenden Tasten mehr; die Einstellung
  „Akzentfarbe für das Umschalt-Icon“ wirkt; Leisten-Schalter (Inkognito u. a.) zeigen ihren
  Aus-Zustand dunkler; die wirkungslose Farbprüfung im Test ist ersetzt; die Sprachkürzel der
  Leertaste entstehen in `FlatStyle.languageCodes` und sind getestet.
- Leiste und Menü: Autofill- und Zwischenablage-Vorschläge verdrängen im flachen Stil immer
  die Toolbar (vorher blieben sie in Passwortfeldern verdeckt); ohne Vorlage sind die Werkzeuge
  über das offene Menü erreichbar; das Menü scrollt, wenn die Tastenfläche niedriger ist
  (Querformat); langes Drücken einer Leistentaste und das Ausblenden der Leiste schließen das
  Menü; angeheftete Kopien bekommen eigene Layout-Parameter; kein zweiter Layout-Durchlauf mehr;
  das Sprungziel in die Einstellungen ersetzt eine vorhandene Einstellungs-Aufgabe; der Kreis
  hinter dem X nimmt die Farbe des Farbsatzes; im Querformat (36 dp Leiste) wird nichts mehr
  abgeschnitten.
- Panel: Workflow-Chips bekommen ihre Abstände und zeigen ihren Namen ganz; feste Chips haben
  zwei Zeilen mit Auslassungspunkten; die Balken sind heller (#6A5E5A) und damit sichtbar; jeder
  Zustand nach einer laufenden Anfrage zeigt wieder die normale Karte (13 Zustände im Test).
- Zwischenablage: ohne sichtbare Leiste bleibt der alte Aufbau mit unterer Tastenreihe (vorher
  kein Weg zurück); die Kopfzeile zeigt höchstens drei Tasten; Chip-Bilder werden wieder nach
  EXIF gedreht; der Bild-Cache merkt gelöschte und ersetzte Dateien und wird beim Verlassen der
  Ansicht geleert; kein Aufstauen vor Android 8; Dateityp-Icons werden nicht mehr aufgeblasen.
- Vorlage und Doku: Die Höhenskala stimmt auch, wenn die Einstellungen quer stehen; eine
  zweite Anwendung sichert neu hinzugekommene Schlüssel; der Test prüft jetzt Farbe und Form des
  Chips; die Anleitung sagt richtig, dass die Zwischenablage-Taste bei ausgeschaltetem Verlauf
  einfügt.

Nicht behoben, bewusst: Nach einer zweiten Anwendung der Vorlage gehen Handänderungen
dazwischen beim Wiederherstellen verloren (jetzt so beschrieben). Die Flat-Zweige des echten
`ClipboardAdapter` und `ClipboardThumbnails.fromUri` laufen in keinem Test.
Danach: 30 Flat-Tests bestanden; Vollregression 449 Tests mit denselben sieben bekannten Fehlern;
Kern-Tests, Build und Lint bestanden (139 Warnungen, ein Hinweis). Ein zweiter Emulator-Versuch
mit frischen Daten bootete, ging aber mit einem Neustart der Arbeitsumgebung verloren, bevor die
APK installiert war: weiterhin kein Gerätelauf.

## Etappe20a — Autokorrektur behält Umlaut-Wörter und merkt rückgängig gemachte Korrekturen

- `CorrectionMemoryTest` (4 Fälle): Umlaut-Erkennung (mochte/möchte, schon/schön, Wurde/würde
  ja; esta/está, ne/né, nur Großschreibung nein), der Wächter blockiert nur, Speichern nur mit
  Schalter und ohne Lernpause oder Inkognito, nur-Großschreibung und leere Wörter werden nicht
  gespeichert, Richtung zählt, Grenze 500 mit Verwerfen der ältesten, Neuladen nach Neustart,
  Vergessen.
- `SuggestTest` (3 neue Fälle, durch die echte Vorschlagslogik): ohne Schalter wird „mochte“
  wie bisher zu „möchte“ korrigiert, mit Standard nicht mehr und „möchte“ bleibt Vorschlag; ein
  unbekanntes Wort wird weiter korrigiert; englisches „fur“ wird bei Deutsch weiter zu „für“;
  „Richtige Wörter nie ändern“; ein gemerktes Paar wird nicht angewendet, eine andere
  Vertippung schon, ausgeschaltet gilt das Paar nicht mehr.
- `InputLogicTest` (1 neuer Fall): Löschtaste direkt nach einer Korrektur stellt das Wort wieder
  her und merkt das Paar nur mit Schalter, nicht im Inkognito-Modus.
- Lesender Prüfer (Pflicht wegen `InputLogic`): ein Hauptbefund behoben (als „bekannt“ galt jedes
  Wort aus irgendeinem Wörterbuch, auch aus einer anderen Sprache oder dem Tippverlauf; jetzt nur
  Hauptwörterbuch derselben Sprache wie die Korrektur). Behoben: falsche Annahme zur
  Wischeingabe im Rückgängig-Pfad, nicht atomare Lese-Menge. Offen, gering: Lesen und Löschen
  der kleinen Datei auf dem UI-Thread der Einstellungsseite.
- Agent-Suite 272/0, die betroffenen Upstream-Klassen ohne neue Fehler (nur der geerbte
  Hangul-Fall), Build und Lint bestanden (142 Warnungen, ein Hinweis).
- Nicht geprüft: echtes Tippen auf dem Telefon, also ob sich die Korrektur im Alltag besser
  anfühlt.

## Etappe20b — eigene Tippfehler lernen

- `TypoMemoryTest` (9 Fälle): nur mit Schalter, nicht bei Lernpause oder Inkognito; drei gleiche
  Bestätigungen nötig, Groß- und Kleinschreibung gleich; konkurrierende Bedeutungen blockieren
  sich bis 80 %; ein echtes Wort nur im gelernten Kontext; Vervollständigungen (auch Mad →
  Mädchen, stras → Straße), Ziffern, gleiche Wörter und Einbuchstabiges zählen nicht; Großschreibung
  von Nomen bleibt, die vom Satzanfang nicht; höchstens fünf Bedeutungen je Form; Rückgängig
  vergisst das Paar; Grenze 1000 Formen und Neuladen (aus dem Speicher der Prefs, nicht von der
  Platte).
- `SuggestTest` (2 neue Fälle, echte Vorschlagslogik): gelernter Tippfehler wird korrigiert,
  auch zu einem Wort ohne Wörterbuch, mit Großschreibung bei Umschalttaste; nicht bei
  ausgeschalteter Autokorrektur, ausgeschaltetem Schalter oder gemerktem Rückgängig; ein echtes
  Wort, das zuerst aus einer anderen Quelle kommt, nur im Kontext; „Richtige Wörter nie ändern“
  gilt auch hier. `InputLogicTest` (1 neuer Fall): Auswahl aus der Leiste zählt nur mit Schalter,
  mit dem vorherigen Wort als Kontext.
- Lesender Prüfer: vier Hauptbefunde behoben (Großschreibung ging verloren, Kontextschutz über
  die Tipp-Historie umgehbar, gelernte Paare übergingen „Richtige Wörter nie ändern“, „Gelernte
  Wörter löschen“ ließ Tippfehler, gemerkte Korrekturen und Trefferzonen stehen) und die
  kleineren (fortgesetzte Wörter, Umlaut-Vervollständigung, fehlende Ausschlüsse wie bei der
  normalen Autokorrektur, Lesen ohne Sperre, unbegrenzte Bedeutungen). Bewusst entschieden: ein
  selbst beigebrachtes Paar geht der Umlaut-Regel vor (Text des Schalters sagt das). Offen:
  Paare gelten sprachübergreifend; das ganze Format wird bei jeder Auswahl neu geschrieben.

## Etappe22a — persönliche Trefferzonen mit Kalibrierung

- `TouchModelTest` (6 Fälle, echte Tastatur und echter `KeyDetector`): Wirkung erst ab 15
  Treffern, gedämpft, auf ein Drittel begrenzt, Ausreißer und Nicht-Buchstaben ignoriert; ohne
  gelernte Werte ist die Erkennung an einem Raster von 23 000 Punkten identisch mit der normalen;
  ein gelerntes Verschieben von e nach rechts macht aus einem Treffer knapp rechts der e/r-Kante
  ein e, die Mitte von r bleibt r, Umschalttaste und ferne Tasten bleiben gleich; Speichern und
  Laden; Kalibrierung gleicht „hsllo welt“ mit „Hallo Welt“ ab, zählt Löschen und Popup-Buchstaben
  nicht, misst Abweichungen vom gemeinten Tastenmittelpunkt; Nachbarverwechslungen werden
  bevorzugt zugeordnet.
- Agent-Suite 287/0, Lint bestanden (148 Warnungen, ein Hinweis).
- Nicht geprüft: echtes Tippen auf dem Telefon, die Kalibrierungsseite im Gerät, Einhand- und
  geteilte Tastatur.

## Lesender Review von 22a und Etappe22b — Lernen beim Tippen

- Prüfer zu 22a: ein Hauptbefund behoben (wer die Kalibrierung früh beendet, bekam eine falsche
  Zuordnung, weil das ungetippte Ende teurer war als ein übersprungener Anfang; jetzt kostet
  das ungetippte Ende nichts, getestet mit 20 von 168 Buchstaben). Behoben: Treffer, die
  über eine gelernte Zone erkannt wurden, fielen weg und zogen jede neue Kalibrierung zur
  Mitte; geänderte Tastatur mitten in der Kalibrierung; Löschen entfernte beliebige Treffer;
  Schaltertext versprach Lernen beim Tippen (jetzt gebaut); Umlaut-Hinweis falsch für QWERTZ+
  (Text enthält jetzt ä ö ü ß); Zähler nach Neustart 0. Offen, dokumentiert: Der
  Wörterbuch-Decoder rechnet mit den normalen Tastenpositionen.
- 22b: `TouchModelTest` (jetzt 9 Fälle): Lernen aus abgeschickten Wörtern nur mit Schalter; ein
  korrigierter Vertipper (wrrt → wert) zählt für e, gemessen vom e; andere Wörter oder Längen
  lehren nichts; Inkognito und Lernpause lehren nichts.
- Prüfer zu 22b: behoben, dass fortgesetzte Wörter (Cursor ins Wort gesetzt) mit Tastenmitten
  statt Berührungen gelernt wurden; dass automatische Korrekturen sofort zählten, obwohl sie noch
  rückgängig gemacht werden können; doppelt abgezogenes Padding (heute 0, also ohne Wirkung).
  Offen: kein Test über den echten `InputLogic`-Haken.

## Etappe23a — Symbolseiten wie Gboard

- `FlatStyleRenderTest` rendert beide Symbolseiten mit der Vorlage und prüft jede Reihe gegen
  die Screenshots des Owners: Seite 1 Ziffern, `@ # € _ & - + ( ) /`, `* " ' : ; ! ?`; Seite 2
  `~ ` | • √ π ÷ × § Δ`, `£ ¥ $ ¢ ^ ° = { } \`, `% © ® ™ ✓ [ ]`, `<` und `>` unten; die
  1234-Taste hell, keine Pfeiltaste; je vier Reihen. `FlatStyleTest` prüft weiter die Hinweise
  der Buchstaben (q zeigt %). `ParserTest` (Upstream) unverändert grün.

## Etappe23b — Emojis in der Leiste auf den Symbolseiten

- `FlatStyleRenderTest.symbolPagesShowRecentEmojisInsteadOfTheTools`: Reihe zunächst versteckt;
  mit `showRecentEmojis(true)` sichtbar, Toolbar weg, Inhalt zuerst die zuletzt benutzten
  Emojis, dann die Standardliste ohne Doppelte; Menü-Taste und angeheftete Tasten bleiben; ein
  erneutes `setToolbarVisibility(true)` holt die Toolbar nicht zurück; ein Tipp rückt das Emoji
  in `RecentEmojis` nach vorn; `showRecentEmojis(false)` stellt die Toolbar wieder her. Bild
  `strip-emojis.png` gerendert. Das Umschalten in `KeyboardSwitcher` ist nur durch den Build
  geprüft, nicht auf einem Gerät.

## Etappe23c — Laufschrift in den Menü-Pillen

- `FlatMenuTest.aLabelThatIsTooLongScrollsUnlessAnimationsAreOff`: alle sechs Pillen mit
  `TruncateAt.MARQUEE`, ausgewählt (Voraussetzung für das Laufen), in der Bedienungshilfe nicht
  als ausgewählt gemeldet; bei `ANIMATOR_DURATION_SCALE` 0 Ellipse am Ende und nicht ausgewählt.
  Dass der Text in einem Tastaturfenster wirklich läuft, ist nur auf einem Gerät prüfbar und
  dort nicht geprüft.

## Etappe23d — Screenshot-Pille

- `ScreenshotChipTest` (Robolectric, Medienspeicher durch `finder` ersetzt):
  - ohne Schalter oder ohne Berechtigung keine Pille und keine Abfrage;
  - mit beiden ein drei Minuten frischer Screenshot, danach nicht mehr; das Ergebnis wird bis
    zur nächsten Änderung gemerkt;
  - nach dem Ausblenden kommt er nicht wieder;
  - keine Pille in Passwort-, sichtbaren Passwort- und Zahlenfeldern und im Inkognito-Modus;
  - ein Tipp übergibt eine Kopie mit der Authority des eigenen FileProviders, Bildtyp und
    gleichem Inhalt und schließt die Pille; langes Drücken schließt nur.
  - keine Pille in Feldern ohne Bildtyp; ein benutzter Screenshot kommt auch nach einem
    Neustart nicht wieder; Kopien älter als 10 Minuten werden gelöscht.
- Prüfer: Befunde behoben, und zwar
  - kein Umweg über die System-Zwischenablage (Pille nur bei passendem Bildtyp);
  - Kopien nur 10 Minuten und beim Ausschalten gelöscht, DE in STORAGE vermerkt;
  - eigener Provider-Pfad statt Unterordner der Clip-Dateien;
  - Kopie im Hintergrund;
  - gedrosselte Abfrage ohne Observer;
  - Teilzugriff auf Fotos erkannt;
  - benutzte ID gespeichert;
  - Dateiendung aus dem MIME-Typ.
- Nachprüfung: acht Befunde bestätigt. Drei neue behoben: kein Einfügen nach Feldwechsel während
  der Kopie (im Test geprüft), Aufräumen auch ohne Berechtigung, die 10-Minuten-Grenze steht in
  STORAGE und ANLEITUNG.
- Bild `screenshot-chip.png` gerendert (das runde Zuschneiden zeigt Robolectric nicht).
- Nicht geprüft: die echte Abfrage des Medienspeichers, der Berechtigungsdialog und das
  Einfügen in einer echten App. Dafür braucht es einen Gerätetest.

## Etappe24a und 24b — getippte Befehle und `@math`

- `CommandSyntaxTest`: Präfix allein, angefangener Name, vollständiger Name mit und ohne
  Argument, Groß- und Kleinschreibung; keine Auslösung bei E-Mail-Adressen, Erwähnungen,
  längeren Wörtern, über Zeilen hinweg und bei Worttrenner-Präfixen; Längengrenzen.
- `MathExpressionTest`: exakte Dezimalrechnung (0,1 + 0,2 = 0,3), Rangfolge, Vorzeichen, `^`
  rechtsassoziativ, `%`, `√`, `²`; gerundete Ergebnisse mit „≈“; Fehler (Syntax, Division
  durch null, negative Wurzel, zu groß, zu lang); vier Ausgaben, Komma/Punkt, Gruppierung;
  Potenz einer Potenz geklammert; `(0,99^999)^999` wird gerundet statt Millionen Stellen zu
  rechnen.
- `KeyboardCommandsTest`: Namens-Chips vervollständigen, ein Befehl handelt erst beim Tipp;
  keine Befehle in Passwort-, Zahlen- und Telefonfeldern oder ohne Einstellungen; abgeschaltete
  Befehle und Hauptschalter; eigener Präfix, ungültiger gespeicherter Präfix fällt auf `@`
  zurück; `@math` bietet das Ergebnis im gemerkten Format und den Rechner, ohne Rechner keinen
  Chip, der den Befehl löscht.
- `CalculatorViewTest`: Start mit der getippten Rechnung, Einfügen nur auf Tipp, Tasten,
  Löschen, Leeren, Fehlermeldung, Wahl und Merken der Ausgabe, englischer Dezimalpunkt.
- `InputLogicTest.typedCommand*`: Ersetzen in einem Commit während eines getippten Worts, ohne
  das Lernen abzuschalten; Ablehnung bei anderem Text, anderer Position, Auswahl, einem Chip von
  vor dem letzten Tastendruck (das Wort bleibt im Entstehen) und unbemerkt verschobener
  Schreibmarke.
- `CommandReplaceTest` (echtes `Editable`): genau ein Commit, Löschen mit leerem Ersatz, nie
  fremder Text; ein Editor, der den Bereich ignoriert, ergibt UNKNOWN; nach einem
  gescheiterten Commit bleibt kein Composing-Bereich über dem Befehl.
- Test-Attrappe korrigiert: `Shadows.kt` lieferte bei `getTextBeforeCursor(n)` die ersten statt
  der letzten n Zeichen. Die übrigen `InputLogicTest`-Fälle laufen unverändert (nur der
  bekannte Hangul-Fehler).
- Prüfer (Engine, Datenschutz, Rechenkern): ein hoher Befund (ungebremste Stellenzahl exakter
  Potenzen konnte die Tastatur einfrieren), drei mittlere (UNKNOWN sperrte die Eingabe nicht und
  konnte einen Composing-Bereich über dem Befehl lassen; absolute Position nicht geprüft; ein
  veralteter Chip beendete das getippte Wort vor der Prüfung), dazu niedrige: Schutz vor
  Autokorrektur nur noch für Befehle, deren Argument Text ist (`@math` nicht); Chips auch bei
  offener Toolbar sichtbar und nur bei sichtbarer Tastatur; Potenz einer Potenz geklammert;
  `0^-0,5` ist Division durch null; kein „Rechner“-Chip, wenn er nicht öffnen kann; nur
  Textfelder; Ergebnis für TalkBack als Live-Region. Alle behoben und getestet, wo testbar.
  Bewusst offen: ein Komma gilt auch bei englischer Sprache als Dezimalzeichen (`1,000` = 1);
  der Chip zeigt das Ergebnis vor dem Tipp.
- Nachprüfung: alle zehn Befunde bestätigt. Vier neue, behoben:
  - Ohne URL-Erkennung gehört das Präfix nicht zum getippten Wort; die Engine sah nur `math`, die
    Korrektur dazu fiel mit dem engeren Argumentschutz weg (hoch). Jetzt schützt
    `guardsTextBefore` den Namen nach dem Text vor der Schreibmarke, auch beim Lernen, und
    `updateGuard` läuft auch ohne sichtbare Leiste (Test `typedCommandNameIsNeitherLearned…`).
  - Die Toolbar wird nur im aufklappbaren Modus für die Chips eingeklappt (mittel).
  - Ohne ExtractedText prüft das Ersetzen das Zeichen vor dem Befehl (Test).
  - Offen und nur am Gerät prüfbar: Editoren, die Änderungen verzögert anwenden (WebView), könnten
    nach dem Ersetzen den alten Stand melden. Dann sperrt die Tastatur wie bei P3 und meldet es,
    die sichere Richtung.
- Gerätetest durch den Owner (Build 1, Xiaomi): Ergebnis-Chip `@math 2*3` funktioniert; ein Tipp
  auf „Rechner“ öffnete nichts. Im Test öffnete er sich; vermutete Ursache: Löschen des Befehls
  vor dem Öffnen (P43). Geändert: Der Rechner öffnet sich ohne Änderung am Feld, „Einfügen“
  ersetzt den Befehl (`InputLogicTest.typedCommandCalculator*`). Am Gerät noch nicht bestätigt.
- Owner: Der Rechner konnte keine Wurzeln oder Funktionen (nur die `√`-Taste). Ergänzt: Funktionen,
  `π`, `e`, `!`, n-te Wurzeln, implizites Malnehmen, Taste „fx“ im Rechner;
  `MathExpressionTest.function*` (Werte, Fehler, alle Ausgaben), `CalculatorViewTest.functionKeys`.
- Nicht geprüft: alles weitere auf einem Gerät (Leiste mit Chips, Rechner über der Navigationsleiste,
  Querformat, TalkBack, Einfügen in echten Apps, WebView).

## Review-Korrekturen 24c–24f

Befunde eines lesenden Reviews der Befehle, behoben:

- `@clip` (mittel): Der Upstream-Verlauf speichert Clips ohne Vertraulich-Markierung, ein altes
  Passwort erschien nach dem nächsten Kopieren wieder. Jetzt merkt `SensitiveClips` (eigener
  Zwischenablage-Listener, Verlauf unverändert) gesalzene Hashes vertraulicher oder im Passwortfeld
  kopierter Clips; `@clip` überspringt sie. Tests `ClipCommandTest.aSensitiveClipStaysHidden…`,
  `…PasswordField…`, `…NewestHashes`. Grenze: vor diesem Stand kopierte Clips bleiben unerkannt.
- `@wf` (mittel): Ohne Markierung bereitete der Befehl nie etwas vor. Jetzt merkt sich das Panel
  den Workflow bis zum nächsten Öffnen mit Markierung im selben Feld; Toast, wenn das Panel nicht
  öffnen kann. Tests `InputLogicTest.typedWorkflowCommandIsPreparedWhenThePanelOpensAgain…`
  (getippter Befehl, Chip, Markieren, Stift; kein Cloud-Aufruf ohne Freigabe),
  `typedWorkflowChoiceIsDropped…` (Feldwechsel, Inkognito).
- `@contact` (mittel): Adressbuch-Abfragen liefen je Tastendruck auf dem Main-Thread. Jetzt im
  Hintergrund mit 150 ms Verzögerung; Tests mit steuerbarem Worker (`ContactCommandTest`).
- Niedrig: `@ort` prüft selbst `isBlocked` und liest Orte einmal je Befehl; `@url` liest
  Vorschlagsquellen einmal je Befehl und Schreibmarke, Bildschirmtreffer ohne Dubletten, Regex
  vorkompiliert; `updateGuard` vergisst Gefundenes auch ohne Leiste, ein leerer Neuaufbau zeigt
  die normale Leiste; Design-Kacheln ohne Hintergrundbild und neu bei Stilwechsel; Kommentar zu
  einzeiligen Adressen; `map.sources` umfasst `AT/commands/`.
- Geprüft (Robolectric/JVM): `:agent-core:test` und gezielte App-Tests (`*Command*`,
  `commands.*`, `DesignTilesTest`, `InputLogicTest.typed*`), alle grün; `tools/dev.py agent-tests`
  grün (415 App-Tests in 58 Klassen, 0 Fehler).
- Nicht geprüft: alles auf einem Gerät (Zwischenablage-Listener mit echten Passwort-Managern,
  `@wf`-Ablauf, Kontakte-Verzögerung, Design-Seite).

## 24f `@translate`

- Gebaut: `@translate "Text" Stil` über den Weg von `@ki` (`openQuestion` mit
  `AiTextAction.TRANSLATE_STYLED`), Anfrage aus `StyledTranslation.request`, Argument bis 500
  Zeichen nur für diesen Befehl (`Command.longArgument`, `CommandSyntax.MAX_LONG_ARGUMENT`).
- Geprüft (JVM): `:agent-core:test` grün, darin `StyledTranslationTest` (alle Anführungszeichen,
  fehlende oder leere Anführungszeichen, leerer Stil, Anführungszeichen im Stil, Apostrophe im
  Text, Anfrage mit zwei Abschnitten), `CommandSyntaxTest.onlyNamedCommandsTakeLongArguments`,
  `AiTextActionTest.styledTranslation…` (feste Anweisung, kein `BuiltinAction`).
- Geprüft (Robolectric): `TranslateCommandTest` (8, Chip, Hinweis-Chip ohne Wirkung, Kürzung,
  500-Zeichen-Grenze nur hier, gesperrte Felder, Titel der Laufliste) und
  `InputLogicTest.typedTranslation*` (6: nichts vor „Senden“, Anfrage genau wie angezeigt,
  Freigabe einmalig, Journal, „Einfügen“ ersetzt genau den Befehl in einem Commit, Einfügen an der
  Schreibmarke ohne Befehl, ohne Schlüssel, Abbruch vor und während der Anfrage, andere Aktionen
  über `openQuestion` abgewiesen); `AskCommandTest` und die Frage-Fälle von `InputLogicTest`
  weiter grün. `tools/dev.py agent-tests` grün (431 Tests in 60 Klassen, 0 Fehler), die ganze
  `InputLogicTest` grün bis auf den geerbten Fall `insertLetterIntoWordHangulFails`;
  `tools/dev.py lint` ohne neue Fehler.
- Nicht geprüft: keine echte Anfrage an einen Anbieter (kein Schlüssel, nur Test-Anbieter), also
  auch nicht, ob Modelle derbe Texte getreu übersetzen oder ablehnen; nichts auf einem Gerät
  (Chip, Vorschau, Einfügen in echten Apps, Tippen von „…“-Zeichen mit der Tastatur).

## 24f Variante A (`@ki`, `@translate` in zwei Tipps)

- Gebaut: Der Befehls-Chip ist der Senden-Knopf und nennt den Anbieter; sein Tipp erzeugt und
  verbraucht die Freigabe genau der getippten Anfrage (`sendCommandRequest`, derselbe
  `ApprovedActionRunner`). Lade-Chip mit drei Punkten (`LoadingDotsView`) und „Abbrechen“,
  Ergebnis-Chip mit „↻“ und „✕“, ein grauer Fehler-Chip (KI-Einstellungen oder Panel als
  Detailansicht). Zustand je Befehlstext und Schreibmarke, Abbruch bei Tippen, Cursorwechsel,
  Feldwechsel, Schutz und Panel. Keine Änderung an `InputLogic`, `RichInputConnection`,
  `Suggest`, `PointerTracker`.
- Geprüft (Robolectric): `AskCommandTest` (10: Chip nennt Anbieter, nichts vor dem Tipp, Tipp
  sendet genau die Frage, Kürzung nur im Label, Lade- und Abbruch-Chip, Ergebnis-Chip fügt nur
  per Tipp ein, ↻ ist ein neuer Tipp, Fehler-Chip, gesperrte Felder), `TranslateCommandTest` (8),
  `LoadingDotsViewTest` (3: läuft nur angehängt, stoppt beim Abhängen, steht bei
  `ANIMATOR_DURATION_SCALE` 0 still, Welle mit 150 ms Versatz in 1,2 s),
  `InputLogicTest.typedQuestion*`/`typedTranslation*`/`typedRequest*` (18, davon 11 neu für die
  Leiste: genau eine Anfrage mit genau dem Text, Journal, Antwort übersteht neu gebaute Leisten,
  ersetzt genau den Befehl in einem Commit erst nach Tipp, Abbrechen, Tippen mit und ohne
  Cursor-Meldung bricht ab und zeigt keine späte Antwort, Inkognito bricht ab, ↻/✕, ohne
  Schlüssel öffnet die KI-Einstellungen ohne Anfrage, Fehler öffnet das Panel mit Grund, Einfügen
  an der Schreibmarke ohne Befehl; die bisherigen Panel-Fälle laufen jetzt über `openQuestion`).
  `tools/dev.py agent-tests` grün (439 Tests in 61 Klassen, 0 Fehler; nach der letzten Änderung an
  `LoadingDotsView` nur die betroffenen Klassen erneut, grün). `tools/dev.py lint` ohne neue
  Befunde in den geänderten Dateien.
- Nicht geprüft: kein Gerät (Aussehen und Flüssigkeit der Punkte, Breite und Wischen langer
  Antwort-Chips, ob eine echte App beim Warten Leistenaktualisierungen oder Cursor-Meldungen
  schickt, die die Anfrage ungewollt abbrechen), keine echte Anfrage an einen Anbieter.
## Update-Prüfung in der App

- Gebaut: Seite „Updates“, Prüfung und Download aus dem öffentlichen Release-Repository
  `Tra1c0/AgenticKeyboard` ohne Token, täglicher Job (opt-in), Benachrichtigung, Installation über
  `PackageInstaller` nach Prüfung von SHA-256, Paketname und Signaturzertifikat; Workflow
  veröffentlicht zusätzlich dort (mit Secret `RELEASES_TOKEN`, sonst Warnung).
- Geprüft (JVM): `UpdateReleasesTest` (5: Tags, höchster vollständiger Build, Entwürfe, fehlende
  Prüfsumme, Größen, fremde Download-URLs, nur höhere Nummer, Prüfsummenzeile).
- Geprüft (Robolectric, nur mit nachgebautem GitHub über einen OkHttp-Interceptor):
  `UpdateCheckTest` (11: genaue URL ohne `Authorization`, strenges Parsen, Redirect zum
  Speicher-Host, `http`-Redirect abgelehnt, Prüfsumme falsch, fremdes Paket, fremdes oder
  zusätzliches Zertifikat, keine höhere Version, Größenfehler, Schalter aus bricht den Job ab und
  fragt nichts, Benachrichtigung einmal je Build, Benachrichtigung öffnet nur die Seite,
  Aufräumen). `tools/dev.py agent-tests` grün (442 Tests in 61 Klassen, 0 Fehler), `tools/dev.py lint`
  ohne Fehler (nur Warnungen wie im übrigen Agent-Code: `UseKtx`, `PluralsCandidate`).
- Nicht geprüft: keine echte Anfrage an api.github.com und kein echter Download (das Repository
  hatte noch kein Release); `getPackageArchiveInfo` mit echter APK, `PackageInstaller`-Session,
  Systemdialog, Recht „Unbekannte Apps installieren“, Benachrichtigungsberechtigung und der
  tägliche Job laufen nur auf einem Gerät; der Workflow-Schritt ins öffentliche Repository ist
  nicht gelaufen.

## 24f `@wecker` und `@timer`

- Gebaut: `AlarmCommand` (`ACTION_SET_ALARM`) und `TimerCommand` (`ACTION_SET_TIMER`), beide mit
  `EXTRA_SKIP_UI = false`, `FLAG_ACTIVITY_NEW_TASK`, `protectsArgument`, Entfernen erst nach
  erfolgreichem Start; `AlarmParser` (über `EventParser.parse(…, alarm = true)`) und `TimerParser`
  in agent-core; Berechtigung `SET_ALARM` im Manifest. Entscheidung zum Tag: nicht übergeben, der
  Chip zeigt „(Tag ignoriert)“, nach dem Start ein Toast; kein `EXTRA_DAYS` (das wäre ein
  wiederkehrender Wecker). Keine Änderung an `InputLogic`, `RichInputConnection`, `Suggest`,
  `PointerTracker`.
- Geprüft (JVM): `:agent-core:test` grün (139 Tests in 21 Klassen), darin `AlarmParserTest` (4:
  Beispiele, morgendliche Lesart, nackte Zahl, `abends`, en/es, `EventParser` unverändert für
  `@termin`, Tag passt oder wird ignoriert, ungültige Eingaben) und `TimerParserTest` (6: Einheiten
  de/en/es, zusammengeschriebene Formen, Hälften, mehrere Teile, nackte Zahlen, `h:mm:ss`, 24-h-
  Grenze, ungültige Eingaben, gescheiterte Teile hinterlassen nichts, Format).
- Geprüft (Robolectric): `ClockCommandsTest` (9: eingebaut, Beschreibung mit Präfix, Schutz;
  Wecker- und Timer-Intent mit genau den Extras über austauschbaren `launcher`, nichts vor dem
  Tipp, Text erst nach erfolgreichem Start entfernt; Fehlstart: Toast „Keine Uhr-App gefunden“,
  Text bleibt; Hinweis-Chip `MUTED` ohne Start und ohne Ersetzen; Tag ignoriert; Passwortfeld ohne
  Befehl). `tools/dev.py agent-tests`: 459 Tests in 63 Klassen, 1 Fehler in
  `OpenAiProviderTest.serviceRetryAfterZeroDoesNotReplayThePost` (Loopback, `NETWORK` statt
  `SERVICE`, während vier Builds parallel liefen); die Klasse allein danach grün (8/8).
  `tools/dev.py lint` erfolgreich (170 Warnungen, 0 neue Fehler), kein Befund in den neuen oder geänderten Dateien.
- Nicht geprüft: kein Gerät und keine echte Uhr-App (ob die Uhr-App Bezeichnung und Länge
  übernimmt, ob sie den Wecker trotz `EXTRA_SKIP_UI = false` sofort stellt, Verhalten am Xiaomi).
## Befehlsvorlagen

- Gebaut: `Command.templates`, `KeyboardCommands.Template`, `Chip.Kind.TEMPLATE` (umrandet,
  „Vorlage: …“ für TalkBack); bei leerem Argument erst die eigenen Chips, dann die Vorlagen; ein
  Tipp ersetzt nur den getippten Befehl durch „@name Vorlage“. Gruppen in `SymbolTable`
  (`SpecialCharacters` 8, `EmojiNames` 10, einige Zeichen und drei Emojis neu), `@conv` rechnet
  ein Paar ohne Zahl als 1 Einheit und nimmt eine Zahl dahinter als Wert. Keine Änderung an
  `InputLogic`, `RichInputConnection`, `Suggest`, `PointerTracker`.
- Geprüft (JVM): `:agent-core:test` grün, neu `SymbolTableTest.groups…`/`everyGroup…` (Gruppen
  vollständig, nur Zeichen der Tabelle, eindeutige Namen, Namenssuche unverändert) und
  `UnitConversionTest.aPairWithoutANumber…` (alle acht Paare, Zahl dahinter, Dezimalzeichen beim
  Tippen, alte Reihenfolge, Unsinn, unter dem absoluten Nullpunkt).
- Geprüft (Robolectric): `CommandTemplatesTest` (11: Vorlagen je Befehl, Tipp ersetzt genau den
  Befehlstext und führt nichts aus, Ergebnis-Chips danach für `@zeichen`, `@emoji`, `@conv`,
  `@datum` und `@termin` in de/en/es, `@translate spanisch "` zeigt den Hinweis, mit
  geschlossenem Text den Senden-Chip, `@math` mit „Rechner“ zuerst, Namens-Chip führt zu den
  Vorlagen, anderes Präfix, keine Vorlagen bei Argument oder in Passwortfeldern, Darstellung).
  Angepasst, weil leere Argumente jetzt Vorlagen zeigen (eigene Chips weiter genau geprüft):
  `MoreCommandsTest`, `DatePlaceCommandsTest`, `KeyboardCommandsTest`, `TranslateCommandTest`.
  `tools/dev.py agent-tests` grün (461 Tests in 63 Klassen, 0 Fehler), `tools/dev.py lint` ohne Fehler und ohne Befunde in den geänderten Dateien (170 Warnungen wie bisher).
- Nicht geprüft: kein Gerät (Aussehen der umrandeten Chips in beiden Stilen, TalkBack, ob die
  Leiste nach dem Tipp in echten Apps sofort die neuen Chips zeigt).
## Etappe23f2 — Design-Editor und „Meine Designs“

- Gebaut: `MyDesigns`, `DesignLook`, `AgentDesignEditor` mit Live-Vorschau, Abschnitt „Meine
  Designs“ mit Kachel „+“, eigene HeliBoard-Farbsätze unter „Farben“, eigenes Hintergrundbild.
  Upstream nur `KeyboardView.java` (ein Aufruf), dazu `FlatStyle.typeface` (Fork-Datei).
- Geprüft (Robolectric): `MyDesignsTest` (12: Speichern schreibt nur Liste und Farbsatz, Auswahl
  genau `selectionValues()` und HeliBoard liest die Farben, Ändern eines gewählten Designs
  entfernt Rundung und Schrift, Speichern eines nicht gewählten Designs ändert die Tastatur
  nicht, Löschen gewählt/nicht gewählt mit genauer Schlüsselliste, Bild ersetzt den Hintergrund
  nur während der Auswahl und das eigene Bild kommt zurück, eingebaute Kachel nimmt Rundung,
  Schrift und Bild zurück, neues Design vom aktuellen Look, ungültiges JSON, Eckenrundung an
  echten Flat- und Rounded-Drawables mit Pillen, Schrift für alle Stile) und `DesignEditorTest`
  (5: „+“ und gespeicherte Kacheln, Antippen wählt, langes Drücken und Stift öffnen den Editor,
  Editor legt ein Design an und schreibt vor dem Speichern nichts, Löschen mit Rückfrage,
  Vorschau-Pixel folgen Hintergrund- und Akzentfarbe, Höhe folgt der Tastenhöhe).
  `DesignTilesTest` weiter grün. Bilder: `app/build/reports/design-editor/editor.png` und
  `my-designs.png` (nicht versioniert, Roboto statt Systemschrift, P29).
  `tools/dev.py agent-tests` grün (467 Tests in 64 Klassen, 0 Fehler), `tools/dev.py lint` ohne
  Fehler (neue Warnung nur `PluralsCandidate` bei `agent_design_limit`).
- Nicht geprüft: kein Gerätetest. Ob die echte Tastatur Rundung, Schrift und Bild so zeigt wie
  die Vorschau, ist nur über den Unit-Test der Drawables belegt, nicht gerendert. Die
  System-Dateiauswahl und der Farbwähler-Dialog wurden im Test nicht bedient.
## Leertasten-Gesten (frei belegbar)

- Gebaut: je eine Aktion für Wischen nach links, rechts, oben, unten, Halten und Doppeltippen
  (`SpaceGestures`), Seite „Leertaste“ (Erweitert und Tastaturprofile → Gesten), Gestenprofil
  mit `INHERIT`. Ohne eigene Wahl folgen die Gesten den alten Einstellungen; nichts wird
  umgeschrieben. `PointerTracker` merkt die Startrichtung eines Wischs; Doppeltippen ersetzt bei
  belegter Aktion „Punkt plus Leerzeichen“.
- Geprüft (Robolectric): `SpaceGesturesTest` (7: Vorgaben wie vorher, Abbildung der
  HeliBoard-Einstellungen und des Rad-Opt-ins, eigener Wert gewinnt, „Standard“, ungültige
  Werte, Auswahllisten, Profile alt/neu und Version 1 ohne Rad-Einwilligung),
  `AgentSpaceGestureTest` (echter PointerTracker, 4 neue: jede Richtung erreicht ihre Geste,
  Rückwärtswischen bleibt bei der Startrichtung, Einmal-Aktion genau einmal pro Wisch und danach
  wieder Leerzeichen, ohne Wischaktion kein Swiper; die 7 bisherigen Fälle laufen weiter),
  `InputLogicTest.generatedSpace*` (7: „Punkt plus Leerzeichen“ ohne Aktion unverändert,
  Doppeltippen mit Aktion statt Punkt, nicht nach Ablauf der Zeit, nach Cursorwechsel oder nach
  einem Leerzeichen ohne Tastendruck; jede Aktion über ihren vorhandenen Tastencode bzw.
  `toggleLayout` für Wischen und Halten; Befehlsliste nur bei eingeschalteten Befehlen und nicht
  im Passwortfeld; Halten folgt dem eigenen Wert; Rad per Wischen blockiert weitere Schritte),
  `KeyboardProfileStoreTest` unverändert grün. `tools/dev.py agent-tests` grün (468 Tests in
  63 Klassen, 0 Fehler). `tools/dev.py upstream-tests`: 683 Tests, genau
  die fünf geerbten Fehler plus `XLinkTest.layoutsLinksInternal` und `XLinkTest.otherLinks` (HTTP
  403 vom Netzwerkfilter der Cloud-Umgebung). `tools/dev.py lint` ohne Fehler; neue Warnungen:
  `UnusedResources` für die nicht mehr genutzten Upstream-Texte der alten Leertasten-Zeilen
  (bewusst nicht gelöscht) und `UseKtx` wie im übrigen Agent-Code.
- Nicht geprüft: kein Gerät und kein Emulator. Offen sind das Gefühl der Schwelle (erste
  Wischstufe 10 dp, wie bisher), Overlays, die während eines Wischs aufgehen, TalkBack-Doppeltipps
  auf der Leertaste, die Tastaturauswahl und die Einstellungsseite selbst (nur kompiliert, kein
  UI-Test).

## Prüfer zur Runde nach df80dbb (Vorlagen, Wecker/Timer, Design-Editor, Leertasten-Gesten)

- Ein Prüfer hat die Änderungen df80dbb..4dd84dd gelesen, mit Schwerpunkt PointerTracker und
  Leertasten-Gesten. Kein hoher Befund.
- Behoben: (mittel) ein eigenes Hintergrundbild, das der Owner setzt, während ein Design mit Bild
  gewählt ist, wurde beim nächsten Designwechsel überschrieben oder gelöscht; jetzt merkt sich
  `MyDesigns` den Hash des eingesetzten Bildes und fasst ein geändertes Bild nicht an (neuer Test
  `MyDesignsTest.anOwnImageSetWhileADesignIsUsedIsNeverOverwrittenOrDeleted`). (niedrig) Ein Wisch
  oder Halten auf der Leertaste setzt den Doppeltipp-Zähler zurück. (niedrig) „Speichern und
  verwenden“ wählt ein schon gewähltes Design nicht ein zweites Mal (doppelte Bildkopie).
- In der Anleitung ergänzt: Was ein Design nach dem Verlassen an Einstellungen zurücklässt, und
  dass eine Zahl direkt nach einer Timer-Dauer mitzählt.
- Offen gelassen: Dateikopien beim Wählen eines Designs laufen auf dem Main-Thread (Ruckeln bei
  großen Bildern möglich, kein Absturz); Ansichtswechsel mitten im Wisch nur ohne Gerät geprüft.

## Eingebaute Emoji-Wörterbücher

- Gebaut: `emoji_de/en/es.dict` in `assets/dicts`; `DictionaryInfoUtils` entpackt sie bei Bedarf,
  `DictionaryFactory` lädt sie nur bei eingeschalteter Emoji-Einstellung.
- Geprüft (Robolectric): `BundledEmojiDictTest` (3: alle drei Dateien da; Deutsch und US-Englisch
  bekommen die passende Datei, Japanisch keine, andere Typen werden so nicht entpackt; ohne
  Emoji-Einstellung kein Emoji-Wörterbuch, mit ihr `emoji_de.dict`).
- Nicht geprüft: echte Emoji-Vorschläge mit der nativen Wörterbuch-Bibliothek (gibt es in
  Robolectric nicht) und der Dialog am Gerät.
- Prüfer: ein hoher Befund behoben. Das entpackte `emoji.dict` wurde bei jedem Aufruf neu
  geschrieben, auch auf dem Main-Thread und während ein geladenes Wörterbuch es per mmap hielt.
  Jetzt wird es wiederverwendet und nur atomar geschrieben (P52, Tests
  `anExtractedEmojiDictionaryIsReusedNotRewritten`). Ebenfalls behoben (niedrig): Emoji-Dateien
  erzeugten zusätzliche Sprachen in der Wörterbuchliste (`bundledEmojiDictionariesAddNoLanguages`).
  Anleitung korrigiert: Die Emoji-Schalter sind standardmäßig an. `BundledEmojiDictTest` 5/5 grün.
- Nachtrag: Mit dem mitgelieferten Emoji-Wörterbuch las `LatinIME.onCreate` (Inline-Emoji-Suche)
  in Robolectric einen Wörterbuch-Kopf über die native Bibliothek, die es dort nicht gibt;
  `InputLogicTest` brach komplett ab (`UnsatisfiedLinkError`). Behoben im Test:
  `ShadowBinaryDictionaryUtils.getHeaderWithOffsetAndLength` meldet „Format nicht lesbar“, und
  `InputLogicTest` nutzt diesen Shadow. Danach 146 Tests, nur der geerbte Fehler
  `insertLetterIntoWordHangulFails`. In der App gibt es die native Bibliothek; geändert hat sich
  dort nichts. Weil auch `InputTest` (und jede Klasse, die `LatinIME` startet) so abbrach, gilt
  der Shadow seit Build 11 für alle Robolectric-Tests (`app/src/test/resources/robolectric.properties`,
  `shadows=`); `SuggestTest` nutzte ihn schon vorher.

## 23g Screenshot zuerst

- Gebaut: Ein neuer Screenshot meldet sich über den Observer entprellt (500 ms) auf dem
  Main-Thread; die Pille erscheint ohne erneutes Tippen ins Feld, aber nur zwischen den Wörtern
  (keine Komposition). Zwischen den Wörtern ersetzt sie Vorhersagen bzw. neutrale Leiste
  (`LatinIME.setSuggestedWords` → `tryShowScreenshotChip`, nach den Befehlschips); beim Tippen
  eines Worts zeigt die Leiste dessen Vorschläge. Ablauf nach drei Minuten bringt die normale
  Leiste zurück. Mit eingeschaltetem Zwischenablageverlauf landet jeder neue Screenshot einmal
  als Bild im Verlauf der Tastatur (`ClipboardDao.addClipUri`, eigene Kopie in
  `files/clipboard/`), nie in der System-Zwischenablage, nicht bei gesperrtem Gerät oder im
  Inkognito-Modus; Ausschalten der Pille löscht diese Einträge außer angehefteten. `InputLogic`
  ist unverändert; geändert sind `LatinIME.java` (eine Methode, ein Aufruf),
  `ClipboardHistoryManager.kt`, `ScreenshotChip.kt`, `AgentScreenshotScreen.kt`, Texte.
  Nachträgliche Regel des Owners (2026-11-13): beim Tippen eines Worts normale Vorschläge, die
  Pille nur an Wortgrenzen; so umgesetzt.
- Geprüft (Robolectric): `InputLogicTest.screenshotChip*` (4: Pille im leeren Feld, weg beim
  Komponieren, wieder nach Leerzeichen, Punkt und neuer Zeile, weg beim nächsten Buchstaben und
  wieder nach dem nächsten Leerzeichen, nichts eingefügt; `InputLogic.mSuggestedWords` bleibt
  die gelieferte Liste, Schließen bringt sofort die Wortvorschläge zurück und der Screenshot
  kommt nicht wieder; getippter `@math`-Befehl zeigt seine Chips statt der Pille; neuer
  Screenshot über `mediaChanged` erscheint nach dem Entprellen einmal und sofort, beim
  Komponieren erst an der nächsten Wortgrenze, nach Ablauf verschwindet die Pille von selbst).
  Die verzögerte Leistenaktualisierung läuft in `InputLogicTest` nicht; der Test liefert sie
  selbst nach (getipptes Wort beim Komponieren, sonst neutrale Leiste). `ScreenshotChipTest`
  (2 neu: Entprellen und Ablaufmeldung; Verlauf nur mit eingeschaltetem Verlauf, nicht im
  Inkognito-Modus, nicht bei gesperrtem Gerät, Kopie im Verlaufsordner, System-Zwischenablage
  unverändert, jeder Screenshot einmal, Größen-/Dateiregel des Verlaufs, Kopie verschwindet mit
  dem Eintrag, Ausschalten entfernt Einträge und Kopien außer angehefteten; 4 bisherige Fälle
  unverändert grün). `InputLogicTest` gesamt: 142 Tests, nur der geerbte
  `insertLetterIntoWordHangulFails` schlägt fehl. `FlatClipboardTest` grün.
  `tools/dev.py agent-tests` (jetzt mit `InputLogicTest.screenshotChip*`) grün: 513 Tests in 67
  Klassen, 0 Fehler. `tools/dev.py lint` ohne Fehler (180 Warnungen, neue nur `UseKtx` in
  `ScreenshotChip.kt` wie im übrigen Agent-Code). `upstream-tests` nicht gelaufen.
- Nicht geprüft: kein Gerät und kein Emulator. Offen: ob der Medienspeicher auf echten Geräten
  so meldet, dass 500 ms Entprellen reichen (ausstehende Dateien, `IS_PENDING`), wie die Pille
  bei offener Symbolleiste wirkt, wie lange das Kopieren großer Screenshots in den Verlauf den
  Main-Thread braucht (wie im Upstream bei Bild-Clips), und das Zusammenspiel mit der
  Verlaufsansicht, wenn sie beim Screenshot offen ist. Kein Prüfer-Review (macht der Lead).
- Prüfer zu 23g (2026-11-13), alle Befunde behoben:
  M1 Abfrage des Medienspeichers lief bei `stale` synchron auf dem Main-Thread, dazu `listFiles`
  bei jedem `current`: jetzt auf `ScreenshotChip.background`, der Main-Thread liest nur das
  gemerkte Ergebnis, ein neues meldet `onChange` (Entprellen und Wortgrenze bleiben); Aufräumen
  nur, solange Kopien liegen können. M2 Verlaufskopie (Größe, Kopie, SHA-256, `getAll`) lief
  auf dem Main-Thread über `addClipUri`/`FileUtils` mit Latch auf `ExecutorUtils.KEYBOARD`:
  jetzt Kopie und Prüfsumme auf dem eigenen Hintergrund-Thread, Eintrag auf dem Main-Thread
  (`insertNewEntry`). M3 Sperre und Inkognito galten nur beim Aufruf, ein bei Sperre gemachter
  Screenshot kam später hinein: jetzt Entscheidung beim ersten Sehen, ausgeschlossene IDs
  bleiben draußen; bei ausgeblendeter Tastatur zählt das letzte Feld nur, wenn es bekannt und
  nicht inkognito ist (sonst ausgeschlossen); eine Änderung bei unerlaubtem Zustand sperrt auch
  ältere Aufnahmen, deren Abfrage scheiterte; der Screenshot beim Start der Tastatur (ohne
  Medienänderung) kommt nicht hinein. N1 Ablauf plant neu, solange die Wanduhr den Screenshot
  noch frisch nennt; danach holt der normale Weg (`postResumeSuggestions`) die Vorhersagen zurück
  (das tat er schon, jetzt im Test belegt). N2 `tryShowScreenshotChip` blendet bei
  `mAutoHideToolbar` die Symbolleiste aus wie der Zwischenablage-Chip (`setExternalSuggestionView`
  tat das bereits; jetzt ausdrücklich und im Test). N3 App-Vorschläge und Inline-Autofill bleiben
  vor der Pille. N4 „Zwischen den Wörtern“ heißt jetzt: keine Komposition und vor der
  Schreibmarke nichts, Leerraum oder Worttrenner. N5 Verlaufseinträge werden über ID und Zeit
  verfolgt, eine eigene Kopie desselben Bilds bleibt beim Ausschalten; ein Kopierfehler wird
  einmal wiederholt. N6 Pillen-View nur mit denselben `Colors` wiederverwendet.
  Geändert: `ScreenshotChip.kt`, `ClipboardHistoryManager.kt`, `LatinIME.java`
  (`showInlineAutofill`, `isShowingInlineAutofill`, Toolbar in `tryShowScreenshotChip`).
  Geprüft (Robolectric): `ScreenshotChipTest` 10 Tests grün (4 neu: Abfrage im Hintergrund mit
  eigenem Executor, Verlaufskopie im Hintergrund und Eintrag auf dem Main-Thread, Sperre/
  Inkognito/unbekanntes Feld/gescheiterte Abfrage/Start ohne Änderung beim ersten Sehen, eigene
  Kopie bleibt und Wiederholung nach Fehler; der bisherige Verlaufstest sieht den Screenshot jetzt
  über eine Medienänderung, und der Fall „Verlauf nimmt keine Dateien“ meldet den gestarteten
  Versuch und prüft, dass kein Eintrag entsteht; der Ablauftest prüft zusätzlich die Neuplanung).
  `InputLogicTest.screenshotChip*` 7 (3 neu: ohne Komposition nur nach Leerraum/Trenner, nicht
  direkt hinter einem Wort; App-Vorschläge und Autofill bleiben; Toolbar bei
  `mAutoHideToolbar` und neue View nach neuem `Colors`; Ablauf stößt `MSG_RESUME_SUGGESTIONS`
  an). Infrastruktur: Auf dem Stand mit eingebauten Emoji-Wörterbüchern bricht `InputLogicTest`
  schon im Konstruktor mit `UnsatisfiedLinkError` (`BinaryDictionary.openNative`, Kopfzeile
  des entpackten `emoji.dict`) ab, auch ohne diese Änderung (geprüft auf `f7aaca0`). Mit einem
  nur lokal eingefügten, nicht committeten `catch (UnsatisfiedLinkError)` in
  `DictionaryInfoUtils.getDictionaryFileHeaderOrNull` lief `InputLogicTest` ganz: 145 Tests, nur
  der geerbte `insertLetterIntoWordHangulFails` schlägt fehl. `FlatClipboardTest` 8 grün.
  `tools/dev.py agent-tests` (ohne den lokalen Eingriff): 539 Tests in 70 Klassen, 52 Fehler,
  alle der genannte `UnsatisfiedLinkError` in `InputLogicTest`; die übrigen 69 Klassen grün.
  Nicht geprüft: kein Gerät; ob echte Medienspeicher-Abfragen und große Kopien auf dem
  Hintergrund-Thread schnell genug sind, wie Inline-Autofill eines echten Passwortmanagers mit
  der Pille zusammenspielt.
## Etappe 26 — RGB-Beleuchtung (26a–26c)

- Gebaut: Rechnen im Kern (`RgbLighting.kt`: Muster, Wellenformen, Paletten, Tipp-Effekte,
  Akku-Regeln, Bildratenbegrenzung), Zeichnen über `RgbKeyPainter` in `KeyboardView`/
  `MainKeyboardView` (26a Tastenhintergrund, Tastaturhintergrund, Beschriftung, Bereiche),
  Tipp-Effekte über `MainKeyboardView.onKeyPressed` (26b), Wortvorschläge und Leistenknöpfe
  über `RgbStrip` (26c), Seite „RGB-Beleuchtung“ mit bewegter Vorschau, Designprofil. Die drei
  Teilpakete sind in einem Commit, weil sie dieselbe Engine, denselben Frame-Ablauf und dieselbe
  Einstellungsseite teilen; jedes hat eigene Tests (unten).
- Geprüft (JVM): `RgbLightingTest` (33): jede Wellenform (Werte, Symmetrie, Stufenzahl,
  Halten/Pause), jedes Muster (waagerecht, senkrecht, diagonal, rund, Atmen, statisch),
  Phase nach Dauer und ohne Sprung nach langer Pause, Richtung umkehren, Wellenbreite,
  Regenbogen und eigene Palette (Interpolation, zyklisch/hin und zurück), Sättigung/Helligkeit,
  Deckkraft, Blitz nur an der getippten Taste, „Nur beim Tippen“ mit Ausblenden, Ring wandert
  rund nach außen, Tempo-Schub, höchstens acht Tipps, HSV, Mischung, Lesbarkeit 3:1, gedrückte
  Tasten, Einstellungen (Vorgabe aus, Rundweg, feldweise Begrenzung kaputter Werte),
  `RgbRun.mode` (aus, verborgen, Energiesparmodus, Animationen aus, reaktiv, statisch mit Effekt)
  und `RgbFramePacer` (30/60 Bilder/s auf 30-, 60-, 90- und 120-Hz-Takt). Grobe Kostenmessung
  (kein Benchmark): die aufwendigste Einstellung (Kreis, Ring mit acht Tipps) braucht für 474
  Lichtwerte eines Bildes auf der Testmaschine etwa 0,24 ms; auf einem Telefon unbekannt.
- Geprüft (Robolectric, native Grafik, kein Gerätetest, P29): `RgbLightingRenderTest` (10) mit
  der echten `MainKeyboardView` im flachen Stil: ausgeschaltet, ohne Ebene oder ohne Bereich kein
  Painter und pixelgleiches Bild; Tastenfarben genau aus der Engine; nur gewählte Bereiche;
  Beschriftung in der Lichtfarbe mit mindestens 3:1; Tastaturhintergrund zwischen den Tasten;
  „Nur beim Tippen“ mit Blitz nur an der gedrückten Taste, Ausblenden verwirft die Effekte;
  Animationen aus = stehendes Bild ohne Tipp-Effekte; ohne Fenster keine Bilder; Leiste färbt
  Wörter (auch neue Vorschläge ohne Bilder) und Knöpfe und stellt sie zurück; Designprofil
  Rundweg, Begrenzung beim Import, altes Profil lässt die Beleuchtung unverändert, Zahl statt Text
  wird abgelehnt, Zurücksetzen schaltet aus. `RgbSettingsScreenTest` (4): Eintrag auf der
  Design-Seite, jede Option sofort gespeichert und genau so gelesen, Vorschau bewegt sich, Tipp
  auf die Vorschau lässt die Taste aufleuchten. Bilder unter `app/build/reports/rgb-lighting/`.
- Weiter grün: `tools/dev.py core-tests` (175), `tools/dev.py agent-tests` (521 Tests in 69
  Klassen, 0 Fehler; danach geänderte Klassen erneut: `Rgb*`, `DesignEditorTest`,
  `DesignTilesTest`, `FlatStyleRenderTest`, `KeyboardProfileStoreTest` grün), `InputTest` (6) und
  `InputLogicTest` (138, nur der geerbte Fehler `insertLetterIntoWordHangulFails`). Der volle
  Lauf `upstream-tests` wurde nicht ausgeführt. `tools/dev.py lint` ohne Fehler; neue Warnungen
  nur `UseKtx` und `PluralsCandidate` wie im übrigen Agent-Code.
- Akku und Kosten (nur begründet, nicht gemessen): Bilder gibt es nur im Modus `RUNNING`
  (Tastatur angehängt, sichtbar, Fenster sichtbar, Muster bewegt sich oder ein Effekt
  verblasst), begrenzt auf 30 bzw. 60 je Sekunde. Je Bild wird die Tastatur-View einmal ganz
  neu gezeichnet (mit Hardware-Beschleunigung zeichnet sie ohnehin bei jedem Tastendruck alle
  Tasten), dazu ein Farbfilter-Objekt je Taste und ein 24 × ≤16 Pixel großes Raster. Aus: kein
  Painter, keine Systemabfrage, kein Frame-Callback.
- Nicht geprüft: kein Gerät und kein Emulator. Offen: Flüssigkeit und Akkuverbrauch auf dem
  Telefon, Aussehen im Holo-Stil (Bild-Tasten werden einfarbig), Wechsel des Energiesparmodus
  während die Tastatur offen und das Licht stehend ist (greift erst beim nächsten Öffnen),
  TalkBack auf der neuen Seite, Querformat.
## 24g Sprachbefehle

- Gebaut: `LanguageNames`/`LanguageMatcher` (agent-core: Normalisierung, Aliasse aus
  `java.util.Locale` plus kleine Tabellen, Damerau-Levenshtein mit 0/1/2 erlaubten Fehlern,
  Präfix ab 2 Zeichen), `CommandSyntax.parse(…, fuzzyNames, claim)` (Tippfehler von Befehlsnamen
  nur zum Vervollständigen, `claim` zuletzt), `Command.claims`/`Command.load`,
  `Host.switchSubtype` (→ `LatinIME.switchToSubtype`), `LanguageCommand` (`@lang`). Keine
  Änderung an `InputLogic`, `RichInputConnection`, `Suggest`, `PointerTracker` oder einer
  bisher unveränderten Upstream-Datei.
- Geprüft (JVM): `tools/dev.py core-tests` grün (155 Tests in 22 Klassen), neu `LanguageNamesTest`
  (9: alle Beispiele des Owners, Präfixe, 18 Vornamen und Befehls-Tippfehler ohne Treffer,
  allgemeine Wörter, Abstandsregeln, Normalisierung, erzeugte Aliasse, Aliasse nah an
  Befehlsnamen nur exakt, Rangfolge) und `CommandSyntaxTest` (4 neue Fälle: `@mtah`/`@tranlate`
  nur als Namensvorschlag, nie mit Argument; `claim` für alle Beispiele inkl. `@español`,
  `@inglés`, `@Österreich`; `a@spain`, `@lewin`, `@max` nichts; Befehlsnamen und ihre Tippfehler
  gewinnen gegen jeden `claim`; beanspruchte Wörter sind Befehlswörter). Alte Fälle unverändert
  (`@mathe` ohne Option weiter `null`).
- Geprüft (Robolectric): `LanguageCommandTest` (9: eingebaut mit Beschreibung der Kürzel; Chips
  für alle Beispiele, nichts vor dem Tipp, `@sp ` mit Leerzeichen ja, `@sp hola` nein; Tipp =
  erst Ersetzen, dann Umschalten, bei abgelehntem Ersetzen kein Umschalten; keine Überdeckung von
  `@math`, `@url`, `@ki`, `@u`, `@e`; `@`-Liste mit einem Eintrag `lang`; `@mtah` vervollständigt
  nur; `@lang` listet und filtert; zwei russische Layouts = zwei Chips mit Layout; eine Sprache →
  grauer Chip, öffnet die Spracheinstellungen; Passwortfeld, Inkognito, abgeschalteter Befehl,
  kein Lernen). Der Wechsel ist dort über einen Test-Host beobachtet (echtes
  `switchToSubtype` braucht ein laufendes Eingabefeld, P54); den echten Host prüft
  `InputLogicTest.typedCommandLanguageChipRemovesTheCommandThenSwitches` (`hola @spnaish` →
  Chip über `createChipsView`, Tipp → Text „hola “, aktueller Subtyp danach Spanisch, vorher
  Deutsch). `tools/dev.py agent-tests` grün (517 Tests in 68 Klassen, 0 Fehler).
  `tools/dev.py lint` erfolgreich (179 Warnungen, 0 neue Fehler), kein Befund in den neuen oder
  geänderten Dateien.
- Nicht geprüft: kein Gerät (ob das Umschalten in echten Apps ohne Flackern sofort greift, Chip-
  Darstellung mit 🌐, Android-ICU-Namen statt JDK-CLDR-Namen der Sprachen und Länder).
- Prüfer zu 24g (sieben Befunde, alle behoben, Befund 5 nur dokumentiert):
  1 (mittel) Sprachwort oder vertippter Name im Argument eines echten Befehls übernahm: behoben.
  `CommandSyntax.parse` merkt sich schwache Treffer (`claim`, `CommandNames.fuzzy`) und gibt
  einem früheren Befehl mit exaktem Namen auf derselben Zeile den Vorrang (P56); ein
  beanspruchtes Wort mit Argument ist kein Befehl mehr. Tests: die vier Beispiele des Prüfers
  (`@ki schreib an @isa`, `@termin Treffen mit @gerd`, `@translate "…" @es`, `@ki frag @matt`)
  mit einem `claim`, der jedes Wort nimmt, plus echtem Matcher; `guardsTextBefore` schützt das
  Argument von `@termin`/`@translate` weiter. 2 (mittel/niedrig) Fehltreffer bei Erwähnungen:
  gemildert wie beschlossen. Zwei Zeichen nur exakt (Codes, Kürzel), Präfix ab 3, Tippfehler
  ab 4 Zeichen auf beiden Seiten mit gleichem Anfangsbuchstaben und nur für Sprachnamen und
  Kürzel (`AliasKind`), Länder exakt oder Präfix, Codes nur exakt, Ländercodes keine Aliasse
  mehr; nach dem Leerzeichen nur exakte Treffer; ein beanspruchtes Wort mit Argument schaltet
  nichts mehr ab. Zusätzlich (über den Beschluss hinaus): Die erlaubten Fehler richten sich nach
  dem kürzeren Wort, und bis 4 Zeichen zählt nur ein Vertauschen zweier Nachbarn; sonst blieben
  `@gerda`→german, `@lana`/`@lange`→lang, `@matt`→math, `@clio`→clip. Deutsche Umschrift
  (`@oesterreich`) ist jetzt ein eigener Alias, weil Länder keine Tippfehler mehr bekommen. Kein
  Treffer mehr: `@isa @gerd @gero @gerda @inge @ines @espen @den @uri @urs @lana @lange @matt
  @clio` und `@us @ca @co @pe @pa @ar @ch @au @me @al @an @re @ge @ne @un @ve` (`@co` beginnt
  weiter `@contact`/`@conv`, wie vor 24g). Weiter Treffer (Kompromiss, in P56 und der Anleitung
  genannt): die Codes `@de @es @en` und Kürzel `@sp @uk` am Anfang jeder Erwähnung, die mit
  ihnen beginnt; drei Buchstaben als Präfix während des Tippens (`@ger…`, `@ing…`, `@ale…`,
  `@ang…`, `@col…`, `@per…`, `@ted…`, `@ver…`, `@bri…`, `@cas…`, `@ame…`); ganze Namen
  `@alle` (Präfix von „allemand“), `@amelia` (Tippfehler von „america“), Zwischenstände wie
  `@alexan` (Tippfehler von „aleman“) und `@peru` (Land, auch nach dem Leerzeichen). Alle
  Owner-Beispiele (`@sp @spain @spanisch @spnaish @espanol @español @ger @deutsch @deustch
  @germany @eng @english @englisch @inglés @usa`, `@mtah`, `@tranlate`) treffen weiter (Tests in
  `LanguageNamesTest`, `CommandSyntaxTest`, `LanguageCommandTest`). 3 (niedrig/mittel) Matcher bei
  jedem `onStartInputView` neu: behoben, zwischengespeichert nach Sprach-Tags der aktiven Subtypen
  und Befehlsnamen (Test: zweimal laden = dieselbe Instanz, andere Sprachen oder Befehle = neu).
  4 (niedrig) Chip für die aktive Sprache: behoben, `Host.currentSubtype`; nur sie getroffen →
  grauer Chip „Schon aktiv: Deutsch (DE)“ ohne Wirkung (Test-Host und echter Host in
  `InputLogicTest`). 5 (niedrig) nicht-lateinische Schriften: nicht im Code geändert, in
  ARCHITECTURE (24g) und der Anleitung dokumentiert. 6 (niedrig) Subtyp beim Tipp nicht mehr
  aktiv: behoben, Toast „Diese Sprache ist nicht mehr aktiv“, Text bleibt (Test). 7 (niedrig)
  Aufräumen im `InputLogicTest`: behoben, `LanguageCommand.defaultSubtypes` (mit `runCatching`)
  wird genau wiederhergestellt, auch in `LanguageCommandTest`.
  Geprüft (JVM): `tools/dev.py core-tests` grün (192 Tests in 23 Klassen). Infrastruktur: Auf
  diesem Stand mit eingebauten Emoji-Wörterbüchern bricht jeder `InputLogicTest` schon im
  Konstruktor mit `UnsatisfiedLinkError` ab (wie bei der Screenshot-Prüfung festgehalten; ohne
  Eingriff hier `typedCommand*` 9 von 9 so gescheitert). Mit dem nur lokal eingefügten, nicht
  committeten `catch (UnsatisfiedLinkError)` in `DictionaryInfoUtils.getDictionaryFileHeaderOrNull`:
  ganzer `InputLogicTest` 143 Tests, nur der geerbte `insertLetterIntoWordHangulFails` rot;
  `tools/dev.py agent-tests` 547 Tests in 71 Klassen, 0 Fehler (`LanguageCommandTest` 14).
  `tools/dev.py lint` erfolgreich (186 Warnungen, 0 Fehler außerhalb der Baseline), kein Befund
  in den geänderten Dateien. Nicht geprüft: kein Gerät.
- Prüfer zu 26 (zehn Befunde und ein kosmetischer, alle behoben):
  1 (mittel) Untere Reihen von Emoji- und Zwischenablage-Ansicht übernahmen die Steuerung:
  behoben, nur `R.id.keyboard_view` bekommt einen Painter; Tipps, Sichtbarkeit und Ausblenden
  anderer Views werden ignoriert (Test `onlyTheMainKeyboardViewIsLit`).
  2 (mittel) Beim ersten Öffnen blieb das Licht wegen Breite 0 stehen: behoben mit
  `onSizeChanged` in `MainKeyboardView` (Test `theLightStartsAfterTheFirstLayout…`; ohne den
  Hook schlägt er fehl, ausprobiert).
  3 (mittel) Symbole wurden gegen die falsche Grundfarbe geprüft, der Umschalt-Akzent ging
  verloren: behoben, `setKeyIconColor` gibt den gesetzten `ColorType` zurück (Test
  `theShiftAccentIsTheBaseOfTheIconColour`).
  4 (niedrig) Leiste blieb gefärbt, wenn nur die Leistenbereiche abgewählt wurden: behoben
  (`restoreStrip`, Test `stripColoursComeBackWhenOnlyTheStripAreasAreDeselected`).
  5 (niedrig) Farbfilter auf Leistenknöpfen überdeckte Zustände: behoben mit abgeleiteter
  `ColorStateList`; nur „aktiviert“ leuchtet, ausgeschaltet und eigene Druckfarben bleiben,
  Alpha je Zustand bleibt (Test `toolbarButtonsKeepTheirStates`). Der runde Knopf im flachen
  Stil hat einen eigenen Filter auf der Drawable und bleibt jetzt unbeleuchtet.
  6 (niedrig) Kontrast ohne Alpha, Hinweis-Symbole und „…“ ohne `labelColor`: behoben, Kontrast
  mit der sichtbaren Mischfarbe, Sprachkürzel mit seinem Alpha, Hinweis-Symbol und „…“ über
  `labelColor` (Tests `contrastCountsTheAlphaOfTheLabel`,
  `semiTransparentLabelsAreReadableAsSeen…`). Hinweis-Symbole und das „…“ sind nur durch den
  Code-Pfad abgedeckt, nicht durch ein Pixelbild. Nicht behoben: Mit Hintergrundbild wird weiter
  gegen `MAIN_BACKGROUND` gerechnet; das steht jetzt ehrlich in ARCHITECTURE und ANLEITUNG.
  7 (niedrig) Vorschau ohne Breite: behoben, `drawKeyboard` bricht ab, Engine und HSV werfen bei
  NaN/Unendlich nicht (Tests `nanAndInfinityNeverThrow`, `aPreviewWithoutWidthDoesNotCrash`).
  8 (niedrig) Kosten: (a) Bilder werden erst angefordert, wenn sie fällig sind
  (`nanosUntilDue`, Test `pacerSaysWhenTheNextFrameIsDue`: 30 angeforderte = 30 gezeichnete
  Bilder auf 120 Hz); (b) die Uhr der RGB-Seite wird nur beim Zeichnen gelesen (Test
  `framesRedrawOnlyThePreview`: höchstens eine Neuzusammensetzung der Optionen in einer Sekunde,
  vorher 31, ausprobiert); (c) die Leiste sammelt ihre Views einmal je Aufbau und erzeugt
  Farbobjekte nur bei neuer Farbe; `getLocationInWindow` läuft weiter je Bild und View (ohne
  Allokation).
  9 (niedrig) Energiesparmodus im stehenden Licht nicht erkannt: behoben mit Broadcast-Empfänger
  (nur angemeldet, solange an und angehängt; Abmeldung beim Ausschalten und beim Abhängen
  geprüft), Animationsskala bei jedem Sichtbarwerden (Tests
  `theLightStartsAfterTheFirstLayoutAndFollowsPowerSaving`, `theLightOffUnregistersTheReceiver`).
  Dabei gefunden und behoben: während `onDetachedFromWindow` gilt die View noch als angehängt
  (P63).
  10 (niedrig) `background.current` konnte null sein: behoben (im Test oben mit leerer
  `StateListDrawable`).
  Kosmetisch: Licht auf durchsichtigem Tastenhintergrund wurde doppelt abgeschwächt: behoben,
  `applyLight` mischt mit vormultipliziertem Alpha (Test
  `lightOnATransparentThemeIsNotDarkenedTwice`).
  Geprüft (JVM bzw. Robolectric, kein Gerät): `RgbLightingTest` 37, `RgbLightingRenderTest` 17,
  `RgbSettingsScreenTest` 6, `tools/dev.py core-tests` (179) grün, danach `Flat*`, `Design*`
  erneut grün. `tools/dev.py agent-tests`: 541 Tests, 49 Fehler, alle in `InputLogicTest` und
  alle schon im Konstruktor (`UnsatisfiedLinkError` in `BinaryDictionary.openNative` über
  `InputLogic.updateEmojiDictionary` beim Start von `LatinIME`, also beim Laden der eingebauten
  Emoji-Wörterbücher, kein RGB-Code); auf dem Stand vor dieser Änderung nicht gegengeprüft,
  bleibt offen. `tools/dev.py lint` ohne Fehler, an den RGB-Dateien nur `UseKtx`. Weiter offen: alles aus „Nicht geprüft“
  außer dem Energiesparmodus-Wechsel bei offener Tastatur (jetzt im Robolectric-Test, nicht auf
  einem Gerät).

## 24g Korrektur: Sprachwahl übersteht den Neustart des Felds (nach Build 11)

- Befund des Owners (Xiaomi, Build 11): Deutsch → Spanisch → Englisch per Befehl ging, zurück zu
  Deutsch fehlte das „ä“ unter „a“.
- Untersucht: Befehl und Sprachtaste schalten schon gleich um (`LatinIME.switchToSubtype` mit
  einem Subtyp aus `SubtypeSettings.getEnabledSubtypes(true)`; `RichInputMethodManager`,
  `SettingsValues` und Tastatur werden neu geladen). In Robolectric de → es → en → de per Chip:
  Subtyp, `Settings.mLocale` und Popup-Tasten von „a“ (mit „ä“ nur bei Deutsch) stimmen, ohne
  Neustart des Felds also kein Fehler. Unterschied: Der Chip löscht den Befehl und leert das
  Feld; startet die App das Feld neu, wendet `onStartInputInternal` Hint-Locales und App-Subtyp
  an. Nachgestellt mit Hint „de-DE“ und „Sprache pro App“: nach `@spanisch` und `@englisch`
  sprang die Tastatur zurück (Test schlug vor der Korrektur bei „@spanisch after the restart“
  fehl, ausprobiert). Welche Sprache die App auf dem Telefon vorgibt, ist unbekannt; mit Hint
  „en“ entspräche das dem Befund.
- Gebaut: `SubtypeChoice`, `LatinIME.switchToSubtypeByCommand`, Prüfung in
  `onStartInputInternal` (P66).
- Geprüft (Robolectric): `InputLogicTest.typedCommandLanguageChoiceSurvivesTheAppRestartingTheField`
  (Rundweg, Popup-Tasten gleich wie über `switchToSubtype` direkt, anderes Feld folgt wieder dem
  Hint), `SubtypeChoiceTest` (6), `LanguageCommandTest` (14) grün. Gesamtläufe siehe Abschnitt
  zur Haptik-Kette unten.
- Nicht geprüft: auf dem Telefon; ob Android/MIUI zusätzlich `onCurrentInputMethodSubtypeChanged`
  mit einem anderen Subtyp ruft.

## Haptik-Kette für Rückgängig/Wiederholen per Leertaste (nach Build 11)

- Auftrag des Owners: „3 kurze haptische Klicks von links nach rechts pro Auslösung und
  andersrum“. Umgesetzt als Stärkeverlauf (ein Motor, P67): Wiederholen steigend, Rückgängig
  fallend, 50 ms Abstand. Nur Leertasten-Gesten; Toolbar-Tasten bewusst nicht (sie haben schon
  den Tastendruck-Impuls, eine Kette dort wäre doppelt).
- Gebaut: `A/HapticChain.kt`, zwei Zeilen in `KeyboardActionListenerImpl.runSpaceAction`.
- Geprüft (JVM): `HapticChainTest` (5: Muster und Abstände, Primitive mit Skalen, Klick als
  Ersatz, Wellenform mit Amplituden ohne Primitive und unter API 30, gleiche Pulse ohne
  Amplitudensteuerung und unter API 26).
- Geprüft (Robolectric): `InputLogicTest.generatedSpaceUndoAndRedoPlayTheirTickChainOnlyWithKeyVibration`
  (Wischen mit Rückgängig spielt fallend, mit Wiederholen steigend; die Rückgängig-Taste selbst
  keine Kette; Tastenvibration aus: keine Kette; mit Test-Vibrator).
- Gesamtläufe (A und B): `tools/dev.py core-tests` grün; `tools/dev.py lint` ohne Fehler (eine
  NewApi-Meldung in `HapticChain` behoben, keine Warnung an den neuen Dateien);
  `tools/dev.py agent-tests` nach A grün (570 Tests); nach B die betroffenen Klassen grün
  (`HapticChainTest`, `InputLogicTest.generatedSpace*`). Der volle Lauf `upstream-tests` nach B
  war bei der Übergabe noch nicht fertig (offen).
- Nicht geprüft: ob die Ticks auf dem Xiaomi zu spüren und zu unterscheiden sind, welche Stufe
  das Telefon wirklich spielt (Primitive, Wellenform, Pulse), „Bitte nicht stören“ am Gerät.

## Build 12: Prüfer zu Sprachwahl-Korrektur und Haptik-Kette

- Prüfer (lesend, Engine-Review Pflicht) zu `e5c3be0` und `a56cdab`: kein hoher Befund. Der
  Eingriff in `onStartInputInternal` ist eng begrenzt, Threading und Lebensdauer der Wahl halten.
- 1 (mittel) Ursache der fehlenden Umlaute nur Hypothese; Test bildete nur „Sprache pro App“ an
  mit Hint `de` ab: neuer Test `InputLogicTest.typedCommandGermanChoiceKeepsUmlautsWithAnEnglishHintAndNoPerAppLanguage`
  (Einstellung aus, Hint `en`, `@deutsch`, Neustart des Felds → Deutsch mit ä, ö, ü, ß). Der
  bestehende Test prüft jetzt nach `@deutsch` ä, ö, ü, ß statt nur ä. Gegenprobe: Mit
  ausgeschaltetem `holds`-Rücksprung scheitern beide Tests, mit ihm laufen sie. Am Xiaomi
  unbestätigt (kein Gerätetest), ebenso ob MIUI zusätzlich `onCurrentInputMethodSubtypeChanged` ruft.
- 2, 3 (niedrig) grobe Feldidentität (`fieldId` 0, Chats) und Drehen des Bildschirms: in P66
  dokumentiert, nicht geändert.
- 5 (mittel) Kette folgte dem System-Schalter für Berührungs-Haptik, die Tastenvibration nicht:
  behoben, `HapticChain` vibriert ohne Usage-Attribute wie `AudioAndHapticFeedbackManager.vibrate`.
- 7 (niedrig) Kette auch bei Halten/Doppeltipp und ohne etwas zum Rückgängigmachen: dokumentiert
  (ARCHITECTURE, ANLEITUNG). 6 (Tick 0,3 evtl. kaum spürbar): nur am Gerät prüfbar. 8, 9: belassen.
- Geprüft: `InputLogicTest.typedCommand*`, `InputLogicTest.generatedSpaceUndo*`,
  `SubtypeChoiceTest`, `HapticChainTest` grün. Volle Läufe: siehe Abschluss von Build 12.

## Build 12: Befunde der Nachprüfung

- Auftrag: die vier niedrigen Befunde der Nachprüfung von Build 11 (HANDOFF → Offene Punkte 0.3).
- Gebaut: (1) `ScreenshotChip.finder` liefert alle Screenshots der letzten drei Minuten,
  `deliver` entscheidet jeden zum ersten Mal gesehenen einzeln, `addToHistory` kopiert alle
  offenen Kandidaten; entschiedene IDs als Menge `agent_screenshot_history_ids` (P81).
  (2) `cleanUp` löscht Reste in `files/agent_screenshot_pending/` älter als 10 Minuten, beim
  Ausschalten alle. (3) `ClipboardHistoryManager.onDestroy` ruft `ScreenshotChip.stop`
  (Observer abmelden, Meldung, Entprellen und Ablauf entfernen). (4) `extractAssetsDictionary`
  schreibt die Zwischendatei nach `files/dicts_extracting/` und löscht sie in `finally` (P80).
  Geänderte Upstream-Dateien: `ClipboardHistoryManager.kt`, `DictionaryInfoUtils.kt` (beide
  waren schon geändert und stehen in `map.sources`).
- Geprüft (Robolectric): je ein neuer Test, jeder schlug mit dem alten Verhalten fehl
  (ausprobiert: alle vier Korrekturen zurückgenommen, die vier Tests rot, die übrigen grün) und
  ist mit der Korrektur grün: `ScreenshotChipTest.twoScreenshotsWithinTheDebounceTimeBothGoIntoTheHistory`
  (beide Einträge, jeder einmal, nach Neustart keiner doppelt; beim Gegentest wurde das alte
  Verhalten als „nur der neueste wird entschieden“ nachgebildet, weil der alte `finder` nur
  einen Screenshot kannte), `ScreenshotChipTest.copiesForTheHistoryLeftByAStoppedKeyboardAreRemoved`,
  `ScreenshotChipTest.theEndOfTheKeyboardStopsTheObserverAndEverythingPosted`,
  `BundledEmojiDictTest.theTemporaryFileIsNeverInTheLocaleFolderAndIsRemovedOnFailure` (über
  einen Test-Hook `extractedForTest` vor dem Umbenennen). Bestehende Tests nur auf `findOnly`
  umgestellt, keine Assertion geändert. `ScreenshotChipTest` 13, `BundledEmojiDictTest` 6,
  `InputLogicTest.screenshotChip*` 7, `FlatClipboardTest` 8 grün. `tools/dev.py agent-tests`:
  580 Tests in 73 Klassen, 0 Fehler.
- Nicht gelaufen: `upstream-tests`, `core-tests`, `lint`, Build (geteilte Maschine). Kein
  Prüfer-Review, kein Gerät: ob echte Medienspeicher mehrere Screenshots in einer Sekunde mit
  aufsteigender `_ID` liefern, ist ungeprüft.

- Prüfer (lesend) zu `029c7b4`: 1 (mittel, Datenschutz) Nach einem Neustart der Tastatur kam ein
  älterer, nie gesehener Screenshot (Sperrbildschirm, Tastatur lief nicht) mit einem neuen in den
  Verlauf: behoben, nur Screenshots nach dem Start des Observers (`watchingSinceMillis`). Test
  `ScreenshotChipTest.aScreenshotFromBeforeTheKeyboardWatchedNeverGoesIntoTheHistory`; Gegenprobe:
  ohne die Bedingung rot, mit ihr grün. `historyOn` im Test startet den Observer jetzt 30 s vor
  `now` (vorher zufällig mit der echten Uhr). `ScreenshotChipTest`, `FlatClipboardTest`,
  `InputLogicTest.screenshotChip*` grün.
- Nicht geändert (niedrig): ein `onChange` auf dem Binder-Thread kann kurz nach `stop` noch eine
  Abfrage auslösen (folgenlos, `onChange` ist null); ist der Schalter schon beim Start aus, bleiben
  junge Reste in `agent_screenshot_pending/` bis zu 10 Minuten; Aufräumen aus den Einstellungen
  parallel zu einer Kopie (kein Eintrag, kein Leck); nach einem Prozessende zwischen Kopie und
  Umbenennen bleiben `*.tmp` in `files/dicts_extracting/` liegen und gehen ins Backup.
## Etappe 27 — Symbole malen

- Auftrag des Owners (2026-11-13): Symbole malen ersetzt Gestenschreiben ganz (Einstellung,
  Standard aus), lokale feste Liste plus eigene Symbole, kein ML Kit, kein Netz. Gebaut auf dem
  ungetesteten Zwischenstand `wip/symbole-malen` (d924da2), in einer Arbeitskopie fertiggestellt.
- Gebaut: `C/InkRecognizer.kt` (`$P`), `C/InkSymbols.kt` (40 Symbole, 73 Vorlagen),
  `C/OwnInkSymbols.kt`, `A/SymbolInk.kt`, `A/SymbolDrawing.kt`, `AgentSymbolsScreen.kt`; Hooks in
  `PointerTracker`, `KeyboardActionListenerImpl`, `LatinIME`, `SettingsValues`, Einträge in
  `MainSettingsScreen`, `SettingsNavHost`, `SettingsContainer`, `GestureTypingScreen`.
- Nachgetragen beim Fertigstellen: „Nicht erkannt“ öffnet bei gesperrtem Gerät keine
  Einstellungen (P77); das Malen eigener Symbole ist eine Karte auf der Seite statt eines Dialogs,
  weil ein Textfeld im Compose-Dialog in Robolectric nie „idle“ wird (P78; Ursache eingegrenzt:
  ohne Textfeld wird der Dialog idle, mit angehaltener Testuhr kommt der Klick nicht an).
- Geprüft (JVM, `tools/dev.py core-tests`, 214/0): `InkRecognizerTest` (14: jede eingebaute
  Zeichnung erkennt sich selbst; 1825 künstlich verzerrte Zeichnungen 97,5 % richtig, 32
  abgelehnt, alle bei %, schwächstes Symbol % 190/225; 20 von 300 zufälligen Kritzeleien
  angenommen (Grenze 10 %); Reihenfolge und Richtung egal; Größe egal, Seitenverhältnis nicht;
  u. a.), `OwnInkSymbolsTest` (4: Hin und zurück, kaputte Zeilen, Grenzen, Textregeln).
  Laufzeit ca. 12 ms je Zeichnung gegen 73 Vorlagen (geteilter Rechner).
- Geprüft (Robolectric): `SymbolInkTest` (12, echter `PointerTracker` mit synthetischen
  Berührungen: Wischen ist Tinte, zwei Striche ergeben →, Tupfer gehören dazu (÷), andere Taste
  schreibt zuerst das Symbol, langes Drücken, Leer- und Löschtaste wischen unverändert, zweiter
  Finger verwirft, abgebrochene Berührung verwirft, Schalter aus = Gestenschreiben wie vorher,
  Schalter an schaltet `mGestureInputEnabled` aus, Passwortfeld ohne Tinte, zu viele Striche
  kein Symbol), `SymbolSettingsScreenTest` (3: Schalter Standard aus und gespeichert, alle
  eingebauten Symbole gelistet; eigenes Symbol dreimal gemalt, Text über 8 Zeichen abgelehnt,
  gespeichert, als eigenes Symbol erkannt, gelöscht; Abbrechen speichert nichts),
  `InputLogicTest.symbolDraw*` (9, echte Weiterleitung über `KeyboardActionListenerImpl`: → nach
  „Preis“ beendet das Wort ohne Gestenwort, Alternative ersetzt in einem Schritt und bietet das
  alte Symbol an, Chips enden mit der nächsten Eingabe und alte Chips ersetzen nichts, „Nicht
  erkannt“ schreibt nichts und öffnet die Seite, gesperrt öffnet es nichts, wartendes Symbol vor
  der nächsten Taste, eigenes Symbol zusammen mit den eingebauten, neuer Editor verwirft).
- Gesamtläufe: `tools/dev.py agent-tests` 600/0. Gezielt `InputLogicTest` komplett,
  `AgentSpaceGestureTest`, `SymbolInkTest`, `BatchDeliveryTest`: 183 Tests, nur der geerbte
  Fehler `insertLetterIntoWordHangulFails`. Ein Lauf davor (gleiche Klassen) hatte zusätzlich
  `typedWorkflowChoiceIsDroppedWhenTheEditorChanges` rot (Senden-Knopf sichtbar), allein grün;
  danach zwei Test-Lecks behoben (statischer Passwortmodus von `PointerTracker` aus
  `SymbolInkTest`, gesperrte Werte im `Settings`-Singleton aus dem Sperr-Test), seitdem zweimal
  grün. Ob eines der Lecks die Ursache war, ist nicht bewiesen. `tools/dev.py build`:
  erfolgreich (ARM64, debugNoMinify). Die volle `upstream-tests`-Suite lief in dieser Arbeitskopie nicht (macht der Lead).
- Nicht geprüft: kein Gerätetest (echte Finger, Tempo, ob HeliBoards Gestenerkennung langsames
  Malen als Geste startet, Spur, Schwellen, Malfeld der eigenen Symbole am Telefon, TalkBack);
  kein Prüfer-Review der `PointerTracker`-Änderung (Pflicht vor dem Merge).
## 25a Emoji-Mix

- Gebaut: Paartabelle als Asset (`tools/emoji_mix_table.py`, 48 690 Byte, aus `metadata.json`
  mit SHA-256 `73a7a2a6…1804`, am 2026-11-13 erneut geladen und gleich), `C/EmojiMix.kt`,
  `EmojiMixer`/`EmojiMixClient`, `@mix`, Leisten-Chip nach zwei Emojis, Mixer in der
  Emoji-Ansicht (untere Reihe: „Emojis suchen“ und „Emoji-Mixer“ gleich breit), Einstellungen.
  Geänderte Upstream-Dateien: `EmojiPalettesView.java`, `KeyLabel.kt` (neu geändert),
  `emoji_bottom_row*.json` (neu geändert), `KeyCode.kt`, `KeyboardActionListenerImpl.kt`,
  `clipboard_provider_path.xml`. Keine Änderung an `InputLogic`, `RichInputConnection` oder
  `PointerTracker`; die Leiste ersetzt über den vorhandenen `replaceCommandText`.
- Generator: prüft das URL-Muster an allen 293 586 `isLatest`-Einträgen (0 Abweichungen), zwei
  Läufe ergeben dieselbe Datei (SHA-256 `28a06b84…a556`).
- Geprüft (JVM): `CT/EmojiMixTest` (9: echte Tabelle 619/147 000, Reihenfolge und umgekehrtes
  Paar 🐱🐶 → `u1f436_u1f431`, FE0F ❤/❤️, Hautton auf Basis, ZWJ 😮‍💨, Cluster, Partner, kaputte
  Dateien, URL-Prüfung gegen fremde Hosts/HTTP/Query, LRU-Cache, PNG-Signatur).
- Geprüft (Robolectric, nachgebauter Server per OkHttp-Interceptor): `AT/commands/EmojiMixCommandTest`
  (10: Chips und Partner ohne Anfrage, ein Tipp = genau eine Anfrage ohne Cookie/Authorization,
  zweiter Tipp während des Ladens nichts, Ergebnis-Chip mit Vorschaubild, Einfügen über den
  Provider und erst danach Entfernen des Befehls, gecachtes Bild ohne Netz, Teilen, geänderter
  Text → nichts, 404 → „Keine Mischung“ ohne zweite Anfrage, Fehler → nur neuer Tipp, Leisten-Chip
  nur direkt an der Schreibmarke und nur für Paare der Tabelle, Ersetzen der zwei Emojis, Schalter,
  gesperrte Felder/Inkognito/Sperre, Client: fremde Hosts nie angefragt, Weiterleitung nicht
  verfolgt, falscher Typ, zu groß, 404, 500, Zeitlimit; echte Ein-Commit-Ersetzung der zwei
  Emojis mit `RichInputConnection` für Surrogate, FE0F und ZWJ) und `AT/EmojiMixPaletteTest` (anfangs 7:
  Leiste an/aus, Seiten geben ihre Höhe ab, Auswahl höchstens zwei, Laden genau einmal je Paar,
  Vorschau und Einfügen, Partner ohne Netz, gesperrt bleibt aus; echte Emoji-Ansicht aus
  `KeyboardSwitcher`: beide Hälften genau gleich breit, Zurück/Löschen bleiben, Taste schaltet den
  Mixer, Verlassen beendet ihn; auch im flachen Stil).
- Fertigstellung aus dem ungetesteten Zwischenstand `wip/emoji-mix` (Arbeitskopie, nach dem
  Merge auf den Arbeitsbranch): Stand gelesen, kompiliert, die 9 + 10 + 7 Tests liefen schon grün.
  Ergänzt: Der Mixer lädt je Wahl höchstens einmal ohne neuen Tipp (`requestedKey`, P74; vorher
  hätte eine Neuzeichnung nach Verlust des Zustands erneut geladen) und prüft vor jeder Anfrage
  aus Partner-Tipp und Wiederholen das Feld (gesperrt → Modus aus, keine Anfrage); neuer Text
  „Tippen, um die Mischung von Google zu laden“. Testnaht `EmojiMixer.toMain` (P73). Schwache
  Prüfung `requests.size <= 2` durch die genaue Liste ersetzt; die Hälften-Prüfung verlangt jetzt
  die Taste „Emojis suchen“ (mit eingebautem Emoji-Wörterbuch) statt die Leertaste zuzulassen.
- Neu geprüft (Robolectric): `AT/EmojiMixPaletteTest` jetzt 10 (neu: Neuzeichnung lädt dieselbe
  Wahl nicht erneut, erst der Tipp; ein Emoji-Tipp in `EmojiPalettesView.onReleaseKey` wählt im
  Mixer-Modus aus statt zu tippen, auch Emojis aus mehreren Codepunkten, nur `onReleaseKey` an den
  Listener; gesperrt gewordenes Feld beendet den Mixer beim Partner-Tipp ohne Anfrage) und
  `InputLogicTest.typedEmojiPairOffersTheMixAndReplacesItOnlyAfterTwoTaps` (echter Host über
  `KeyboardCommands.createChipsView`: „Na 🐶🔥“ getippt → Chip ohne Anfrage → Tipp = genau die
  eine URL, Feld unverändert → Sticker-Chip → Tipp fügt ein und entfernt die zwei Emojis in einem
  Commit, Verbindung konsistent; „🐶x🔥“ → kein Chip, keine Anfrage). `agent-tests` nimmt
  `InputLogicTest.typedEmoji*` jetzt mit.
- Generator am 2026-11-13 erneut laufen lassen: Eingabe-SHA-256 gleich (`73a7a2a6…1804`,
  98 873 872 Byte), Ausgabe Byte für Byte gleich dem Asset (`28a06b84…a556`).
- Gesamtläufe: `tools/dev.py core-tests` grün (`EmojiMixTest` 9/9); `tools/dev.py agent-tests`
  grün (597 Tests in 75 Klassen, 0 Fehler, 0 übersprungen); `tools/dev.py build` erfolgreich, die
  APK enthält `assets/emoji_mix_pairs.bin` (48 690 Byte). `upstream-tests` (voller Lauf) und
  `lint` nicht in dieser Arbeitskopie gelaufen (Auftrag: macht der Lead).
- Nicht geprüft: www.gstatic.com ist aus der Cloud-Umgebung nicht erreichbar (Proxy lehnt ab,
  am 2026-11-13 erneut versucht), es wurde also nie ein echtes Bild geladen (Muster nur gegen die
  Metadatei geprüft); kein Gerätetest (Einfügen in echte Chat-Apps, Mixer-Leiste und Animation,
  Lesbarkeit der Tastentexte, ob der Leisten-Chip nach Emojis aus der Emoji-Ansicht beim
  Zurückwechseln erscheint, ob die Leiste nach getippten Emojis auf dem Gerät neu aufgebaut
  wird – der Test ruft `createChipsView` direkt). Kein Prüfer gelesen. Lizenz der Paarliste: nur
  mündliche Zustimmung, Lizenzdatei später zu prüfen (`docs/BUILD.md`).

## Build 12: Prüfer zu Etappe 27 (Symbole malen, Engine-Review)

- Prüfer (lesend) zu `79f99b5..71c9f20`: kein hoher Befund. Mit ausgeschaltetem Schalter ist
  `PointerTracker` nach Codelesung unverändert (`startsInk` kehrt vor Rauschfilter-Zustand und
  Warteschlange zurück); belegt durch Test nur für Wischen und Tippen.
- Mittel: Ein wartendes Symbol wurde nach dem Ausblenden der Tastatur (Zurück, Bildschirm aus)
  oder dem Ende der Eingabe noch ins Feld geschrieben. Behoben: `SymbolDrawing.reset` auch in
  `onFinishInputInternal` und `onFinishInputViewInternal`. Test
  `InputLogicTest.symbolDrawWaitingDrawingIsDroppedWhenTheKeyboardHides`; Gegenprobe ohne die
  zwei Zeilen rot, mit ihnen grün; `symbolDraw*` und `SymbolInkTest` grün.
- Niedrig, offen: Ein Buchstabentipp innerhalb von 500 ms nach einem Strich wird zum Tupfer
  (Buchstabe fehlt, in der Anleitung genannt); ein Wortvorschlag in diesem Fenster landet vor dem
  Symbol; ein verworfener Strich zeichnet weiter Spur; `mUpTime` im Tintenzweig nicht gesetzt;
  „Gestenschreiben“ bleibt sichtbar an, wirkt aber nicht; `DrawingPad` ohne Punktgrenze und ohne
  Hinweis bei mehr als 12 Strichen; Surrogates in manipulierten Prefs. Kein Test von Berührung bis
  Text in einem Stück (SymbolInkTest mit nachgebautem Listener, InputLogicTest ohne PointerTracker).

## Build 12: Layout nach dem Update (Rückmeldung des Owners)

- Befund des Owners: Nach dem Update auf Test-Build 12 hatte sein eigenes Design große
  Tastenabstände. Ursache (P84): `AppUpgrade` lief bei jedem Update HeliBoards alte Migrationen;
  `oldVersion <= 3901` setzt die Abstände auf 1,75. Betraf jedes Update seit Build 1.
- Behoben: `FORK_UPGRADES_ONLY` überspringt die Versions-Migrationen; beim Update von Build 12
  oder älter einmalige Reparatur der Abstände, die genau den Wert der Migration haben.
- Der Owner bestätigte die Ursache: „Design bearbeiten, nichts ändern, Speichern“ stellte das
  Aussehen wieder her (das gespeicherte Design war unverändert).
- Prüfer (lesend) zu `3e2bba2` und `6059869`: kein hoher Befund. Mittel: Die Reparatur überschrieb
  ohne Bedingung auch die Höhe und nur den Hochformat-Abstand; behoben (nur Werte 1,75/1,1, alle
  vier Ausrichtungen, Höhe unberührt, ohne eigenes Design zurück auf Standard). Mittel: weitere
  Schäden der alten Migrationen (angepinnte Leistenknöpfe zurückgesetzt, „Satzzeichen
  vorschlagen“ umgestellt) sind nicht reparierbar; in P84 und den Release-Notes genannt.
  Niedrig: „Bild eingefügt …“ erschien auch, wenn der Nutzer weitergetippt hatte; jetzt nur bei
  einem Objektzeichen vor der Schreibmarke (Test-Fall in
  `InputLogicTest.typedEmojiPairStaysWhenTheFieldDoesNotTakeTheImage`). Niedrig, nur
  dokumentiert: Unter Android 7.1 meldet `commitContent` nur die Annahme der Aktion; alte
  HeliBoard-Sicherungen; neue Upstream-Migrationen nach einem neuen Pin (P84).
- Geprüft: `ForkUpgradeTest` (4: spätere Updates lassen alles stehen; erste Reparatur nur der
  Migrationswerte; ohne eigenes Design Standard; von Hand geänderter Abstand bleibt),
  `InputLogicTest.typedEmoji*`, `typedCommandImage*`, `QrCommandTest`, `EmojiMixCommandTest`
  grün. Gegenproben: ohne Reparatur, ohne `FORK_UPGRADES_ONLY` und ohne die Objektzeichen-Bedingung
  jeweils rot. Kein Gerätetest.

## Build 12: Prüfer zu Etappe 25a (Emoji-Mix)

- Prüfer (lesend) zu 25a: zwei mittlere, vier niedrige Befunde behoben, vier niedrige offen.
  Keine Änderung an `InputLogic`, `RichInputConnection`, `PointerTracker` oder
  `KeyboardActionListenerImpl`; geänderte Upstream-Dateien nur die schon geänderten Tests
  `Shadows.kt` (`contentCommit`) und `InputLogicTest.kt`.
- Mittel 1: Der Sink meldete immer „eingefügt“ (`onContent` gibt die Antwort des Editors nicht
  weiter), auch bei blockierter Eingabe oder wenn die App das Bild ablehnte; danach wurden die
  getippten Emojis bzw. `@mix …` gelöscht. `@qr` nutzte denselben Sink. Behoben:
  `QrCommand.SYSTEM_SINK` committet selbst über `InputConnectionCompat.commitContent` und gibt die
  Antwort zurück; bei false Toast „Das Bild wurde nicht eingefügt. Der Text bleibt stehen.“ (Emoji-Mix
  und `@qr`). Tests `InputLogicTest.typedEmojiPairStaysWhenTheFieldDoesNotTakeTheImage` (echter
  Host und Sink: ablehnender Editor → Text bleibt, Toast; blockierte Eingabe → kein Commit, Text
  bleibt) und `InputLogicTest.typedCommandImageSinkReportsTheFieldsAnswer` (fremder Typ,
  Annahme, Ablehnung, kein IME-Kontext, blockiert); `QrCommandTest` prüft den Toast.
- Mittel 2: Ein Rich-Text-Editor, der das Bild als Objektzeichen an die Schreibmarke setzt, ließ
  das folgende Ersetzen scheitern, mit „Text geändert, nichts ersetzt“. Behoben (P83):
  `QrCommand.removeAfterInsert` liest beim Editor (`Host.editorTextBeforeCursor`); weicht der Text
  ab, bleibt er stehen mit „Bild eingefügt. Die App hat es anders platziert, deshalb bleibt der
  getippte Text stehen.“ Gilt für Emoji-Mix und `@qr`. Test im selben `InputLogicTest`.
- Niedrig 3: Eine `RuntimeException` im Worker ließ das Paar für immer in `Loading`. Behoben:
  jede Ausnahme → `Failed` (`Reason.INTERNAL`). Test
  `EmojiMixCommandTest.anUnexpectedErrorFailsThePairInsteadOfLoadingForever`.
- Niedrig 4: `clearCache` gegen eine eingereihte Anfrage (`Ready` auf gelöschter Datei) und ohne
  Neuzeichnen. Behoben: Zähler `generation`, Ergebnisse älterer Arbeit werden verworfen, `changed()`
  sofort. Test `EmojiMixCommandTest.clearingTheCacheDropsALoadStillQueuedAndRedrawsAtOnce`.
- Niedrig 5: Vorschaubilder blieben 534 px groß (Abtastung 1). Behoben: Abtastung plus
  Skalierung auf höchstens 320 px. Test `EmojiMixCommandTest.thumbnailsAreDecodedToTheirDisplaySize`
  (534 → 320×320, 1400×700 → 320×160, 64 bleibt, kein Bild → null).
- Niedrig 6: Codepunkte über 10FFFF oder Surrogate in der Tabelle wurden angenommen. Behoben in
  `EmojiMixTable.read` (ganze Tabelle abgelehnt) und `tools/emoji_mix_table.py`
  (`valid_codepoints`, von Hand mit gültigen und ungültigen Werten geprüft; der Generator selbst
  nicht neu gelaufen, die Eingabedatei liegt nicht vor). Test
  `EmojiMixTest.codePointsOutsideUnicodeRejectTheWholeTable`.
- Gegenproben: jede Prüfung ohne ihre Korrektur rot. EmojiMix.kt zurückgesetzt → neuer
  `EmojiMixTest` rot. Die vier Quelldateien (`EmojiMixer`, `QrCommand`, `EmojiMixCommand`,
  `KeyboardCommands`) auf den alten Stand → die drei neuen `EmojiMixCommandTest`-Fälle, die zwei
  neuen `InputLogicTest`-Fälle und der Toast in `QrCommandTest` rot. Nur die Prüfung in
  `removeAfterInsert` ausgeschaltet → `typedEmojiPairStays…` rot mit genau dem alten Toast
  „Der Text vor der Schreibmarke hat sich geändert …“.
- Gesamtläufe: `tools/dev.py core-tests` grün (224 Tests); `tools/dev.py agent-tests` grün
  (633 Tests, 0 Fehler, 0 übersprungen). Kein Build, kein voller `upstream-tests`-Lauf, kein
  Gerätetest (gemeinsam genutzte Maschine); ob echte Apps `commitContent` mit false ablehnen oder
  das Bild als Objektzeichen setzen, ist nur nachgebaut.
- Offen (niedrig, unverändert): 7 Höhe der Mixer-Leiste im Layout; 8 eine Anfrage je Tipp (kein
  Bündeln); 9 englischer Ersatztext; 10 globaler Listener je Besitzer („chips“, „palette“).

## Build 12: wackeliger Test `LearningDataTest.pauseIsOffByDefault…`

- Im GitHub-Lauf 3 (Endstand 675fb38) und im ersten lokalen Lauf rot, einzeln grün. Ursache:
  `App.onCreate` schreibt `emoji_max_sdk` aus einer Hintergrund-Coroutine
  (`SupportedEmojis.load`) zu einem unbestimmten Zeitpunkt; der Test erwartete genau einen
  Schlüssel. Behoben im Test: `@Before` ruft `SupportedEmojis.load` selbst (die Coroutine
  überspringt danach), die Prüfung bleibt gleich streng (vorher genau `emoji_max_sdk`, danach
  zusätzlich genau `agent_learning_paused`). Lokal grün; der nächste GitHub-Lauf prüft den vollen
  Satz.
- Gleiche Ursache bei `SpaceGesturesTest.defaultsEqualTheBehaviorBeforePerGestureSettings`
  (erwartet leere Einstellungen, fand `emoji_max_sdk`): `@Before` wartet jetzt bis zu 5 s auf den
  einmaligen Schreibvorgang der Start-Coroutine und leert erst dann; die Assertions sind
  unverändert. Klasse grün.
## Etappe 33 (Leertasten-Rastpunkte)

- Auftrag: ROADMAP 33 (Rückmeldung des Owners zu Build 12 mit Skizze). Seitliche Leertasten-Wische
  mit Einmal-Aktion laufen über drei Rastpunkte bei 0,8 / 1,6 / 2,65 Tastenbreiten ab dem
  Aufsetzpunkt (`mMostCommonKeyWidth`, mindestens 20 dp); je ein leichter Tick, der letzte ein
  stärkerer, längerer Impuls („Überdehnen“), erst dort die Aktion, einmal pro Wisch. Vorher
  loslassen tut nichts; 0,2 Tastenbreiten hinter einen Punkt zurück und wieder vor tickt erneut.
  Halten wird ab 6 dp klar seitlicher Bewegung abgebrochen, auch während der Schnelltipp-Sperre.
  Senkrechte Wische und fortlaufende Aktionen unverändert. Die drei Ticks direkt nach
  Rückgängig/Wiederholen (Build 12) sind entfernt, auch bei Halten und Doppeltippen.
- Annahmen des Leads (vom Owner zu bestätigen): Abstände wie oben (Startpunkt plus drei
  Rastpunkte); „den ganzen Text rückgängig“ = ein normaler Rückgängig-Schritt; Zurückziehen tickt
  erneut statt abzubrechen; gilt für alle Einmal-Aktionen links/rechts (auch Rad, Emoji …); die
  Richtung legt der erste 10-dp-Schritt fest (P100).
- Gebaut: `A/SpaceDetents.kt` (neu), `A/HapticChain.kt` (umgebaut: `Feel.DETENT`/`STRETCH` statt
  `Direction`), `KeyboardActionListenerImpl.onSpaceSwipeDistance`; Engine: `PointerTracker`
  (`onKeySwipe`: seitliche Einmal-Wische melden den Abstand zum Aufsetzpunkt bei jeder Bewegung;
  Abbruch des Haltens vor der Schnelltipp-Sperre), `KeyboardActionListener`
  (`onSpaceSwipeDistance`, leere Vorgabe im `Adapter`). Fallstricke P100–P103.
- Geprüft (JVM): `SpaceDetentsTest` (9: Skizze, vorwärts, einmal vollenden bis Loslassen, früh
  loslassen, Rückweg mit Schwelle, schneller Sprung, Richtung, Einheit mit Untergrenze,
  Abbruchregel des Haltens), `HapticChainTest` (7, neu geschrieben: Primitive mit THUD, THUD erst
  ab API 31 und Klick als Ersatz, weicherer Klick ohne Tick, Wellenform statt schwerem Primitiv
  ohne Anstieg, Wellenform mit Amplituden, Pulse ohne Amplitudensteuerung oder unter API 26, der
  letzte Impuls auf jeder Stufe länger und nicht schwächer). Die fünf alten Fälle prüften die
  entfernte Drei-Tick-Kette und sind ersetzt, nicht abgeschwächt.
- Geprüft (Robolectric, echter `PointerTracker`): `AgentSpaceGestureTest` (15, davon 4 neu):
  Abstand jeder Bewegung ab Aufsetzpunkt (auch nahe dem Start, Tastenbreite 50 übergeben, kein
  `onSpaceSwipe`); echte `SpaceDetents` hinter dem Mock: Ticks bei 40/80 px, nach dem Rückweg
  erneut, Vollendung bei 135 px, früh losgelassen keine Vollendung; Halten nach 7 px seitlich
  abgebrochen, der Wisch zählt (Gegenproben im selben Test: 4 px und 7/8 px schräg halten weiter);
  Halten auch während der Schnelltipp-Sperre abgebrochen. `oneShotSwipeActionRunsOncePerSwipe`
  heißt jetzt `oneShotVerticalSwipeActionRunsOncePerSwipe` und prüft dieselben Zusicherungen mit
  einem Wisch nach oben, weil der seitliche Einmal-Wisch absichtlich anders läuft.
- Geprüft (Robolectric, echter `KeyboardActionListenerImpl`): `InputLogicTest.generatedSpace*`.
  `generatedSpaceUndoAndRedoPlayTheirTickChainOnlyWithKeyVibration` heißt jetzt
  `…PlayTheirDetentsOnlyWithKeyVibration`: statt drei Ticks nach der Aktion prüft er nichts vor
  dem ersten Punkt, Tick, Tick, Vollendung (links und rechts), nichts danach; Toolbar-Taste und
  Halten mit Rückgängig ohne Kette; Tastenvibration aus: nichts. Neu:
  `generatedSpaceUndoAndRedoRunOnlyAtTheLastDetent` (Rückgängig erst am letzten Punkt, genau
  einmal, früh loslassen nichts, Rückweg, Richtung, `NONE`),
  `generatedSpaceSideSwipeRunsEveryOneShotActionAtTheLastDetent` (alle Tastencodes und
  Layoutwechsel beider Seiten erst bei 2,7 statt 2,6),
  `generatedSpaceSideSwipeOpensTheWheelAtTheLastDetentAndStopsThere` (offenes Rad blockiert,
  danach wieder Rückgängig). `generatedSpaceGestureActionsUseTheExistingKeyCodes` unverändert
  (prüft die Zuordnung über `onSpaceSwipe`, das `PointerTracker` seitlich nur noch für
  fortlaufende Aktionen ruft).
- Gegenproben (Code vorübergehend zurückgedreht, danach byte-gleich wiederhergestellt): ohne
  Abbruch des Haltens und mit Aktion am ersten Rastpunkt scheitern die beiden Halten-Tests und
  die drei neuen `InputLogicTest`-Fälle (5 rot); mit der Wischlogik aus Build 12 in
  `PointerTracker` scheitern die drei neuen Wisch-Tests in `AgentSpaceGestureTest`; ohne
  Rückweg-Schwelle und ohne Sperre nach der Vollendung scheitern die zwei passenden
  `SpaceDetentsTest`-Fälle.
- Gesamtläufe: `tools/dev.py agent-tests` grün (655 Tests, 79 Klassen, 0 Fehler).
  `InputLogicTest` komplett: 165 Tests, nur der geerbte Fehler `insertLetterIntoWordHangulFails`.
  Nicht gelaufen (gemeinsam genutzte Maschine): `upstream-tests`, `lint`, `build`, `core-tests`
  (`agent-core` unverändert).
- Nicht geprüft: kein Gerätetest. Ungeprüft sind das Gefühl der Rastpunkte (Abstände, ob Tick und
  letzter Impuls unterscheidbar sind), welche Haptik-Stufe das Xiaomi spielt, ob 6 dp als
  Abbruch des Haltens zu empfindlich ist, Querformat (breitere Tasten, längerer Weg). Kein
  Prüfer-Review der `PointerTracker`-Änderung (Pflicht vor dem Merge).


- Prüfer (lesend, Engine-Review Pflicht) zu `2779492`: kein hoher Befund; `PointerTracker` für alles
  außer seitlichen Einmal-Wischen auf der Leertaste verhaltensgleich. Behoben: (mittel) In
  Ziffern-, Telefon- und Zahlenblock (Tasten ≈ ¼ der Breite) lag der letzte Rastpunkt bei ⅔ der
  Bildschirmbreite: Tastenbreite jetzt höchstens ein Siebtel der Tastatur (`SpaceDetents.keyWidth`,
  Test `theDetentsCountInLetterKeyWidthsAlsoOnNumberPads`). Ein Rechtswisch von der Leertaste am
  rechten Rand des Ziffernblocks bleibt aus Platzgründen kaum möglich. (mittel/niedrig) Abbruch
  des Haltens erst ab 10 dp statt 6 dp (rollender Finger); `AgentSpaceGestureTest` angepasst (10 px
  bricht ab, 8 px behält das Halten). (niedrig) Der starke Impuls kommt nur, wenn die Aktion etwas
  getan hat (Test mit ausgeschalteten Befehlen; Gegenprobe ohne die Bedingung rot). Offen,
  niedrig: ein gemeinsamer Zustand für zwei gleichzeitig wischende Finger; Zustand nach Ausblenden
  ohne Finger-hoch bis zum nächsten Loslassen; Dichte zweimal gelesen; Testlücken (Mehrfinger,
  Abbruch mitten im Wisch, RTL, geteilte Tastatur). Geprüft: `SpaceDetentsTest`, `HapticChainTest`,
  `AgentSpaceGestureTest`, `InputLogicTest.generatedSpace*` grün.
## Etappen 28 und 32

Etappe 28 (Haptik und Rastpunkte für alle Schieberegler) und 32 (Emoji-Mix: zuletzt gemischt),
gebaut in einer eigenen Arbeitskopie, ohne Prüfer (steht noch aus). Keine Änderung an
`InputLogic`, `RichInputConnection`, `PointerTracker`, `KeyboardActionListenerImpl` oder
`HapticChain`. Neu geänderte Upstream-Dateien: `SliderDialog.kt`, `SliderPreference.kt`,
`KeyboardScalePreference.kt`, `ColorPickerDialog.kt` (in `map.sources` und unter „Geänderte
Upstream-Dateien“).

- Annahmen des Leads (vom Owner änderbar): Die Impulse folgen „Vibration bei Tastendruck“ und
  „Bitte nicht stören“ wie die Haptik-Kette, nicht der Android-Einstellung für Berührungs-Haptik.
  Rastpunkte je Regler = Standardwert, Enden, runde Werte nach der Regel in ARCHITECTURE.md →
  Einstellungen und Profile (Fangzone 1,5 % je Seite, Ticks 20–40 über den Bereich, höchstens
  alle 25 ms). Als „gemischt“ zählt ein Sticker, sobald er nach einem Tipp fertig ist, und wieder
  beim Einfügen oder Teilen.
- Übernommene Regler: `SliderDialog` (damit jede `SliderPreference`: Zwischenablage-Aufbewahrung
  und -Größe, Vibrationsdauer, Lautstärke, Schrift-, Hinweis- und Emoji-Größe,
  Autokorrektur-Schwelle, Langdruck-Verzögerung, Sprachwechsel-Wischweg, Touchpad-Empfindlichkeit,
  Android-Version (Debug), Gestenschreiben-Pause und Spurdauer), `KeyboardScalePreference`
  (Trennabstand, Tastenabstand, Tastaturhöhe, untere Reihe, Abstand unten und seitlich),
  `ColorPickerDialog` (Deckkraft, Helligkeit) und `AgentDesignEditor` (Eckenrundung, Abstand,
  Höhe). Nicht übernommen: `AgentRgbScreen.kt` (wird parallel in 29 neu gebaut).
- Tests: `SliderFeelTest` (8: Tick-Abstand und Rastpunkte für zehn echte Regler, langsamer Zug
  10 Ticks + 10 Rasten, 25-ms-Grenze, Fangen/Halten/Loslassen/Wiederscharfstellen, Start neben
  und auf einer Rast, Sprung über eine Rast, Stufen-Regler, leerer Bereich), `AgentSliderTest`
  (7, Compose: Zug fängt genau 0,5 und kein Wert in der Zone, feiner Wert nach dem Zurückziehen,
  Ende genau 100, TalkBack setzt 0,49 und 0,505 ohne Impuls, `SliderDialog` rastet auf 50 und
  spielt Klick und Tick auf der Vibrator-Attrappe, ohne Tastenvibration still, Einstellungen und
  „Bitte nicht stören“, Stufen der Geräte), `EmojiMixRecentsTest` (6: höchstens 12, neueste
  zuerst, Wiederholung nach vorn, kaputte Einstellung; nur Bilder im Cache, keine Anfrage,
  gelöschtes Bild verschwindet; Tipp fügt über die Senke ein und rückt nach vorn; gesperrtes
  Feld fügt nichts ein; neue Mischung wird gemerkt, im Inkognito-Modus nichts; Löschen leert
  die Liste). `DesignEditorTest` (5) unverändert grün.
- Gegenproben (danach per `git checkout` zurückgesetzt): `AgentSlider` lässt jeden Wert durch
  `SliderDrag` (auch TalkBack) → `talkBackSetsExactValuesWithoutSnappingOrImpulses` rot;
  `EmojiMixRecents.recording()` ohne Inkognito-/Sperrprüfung → `aNewMixIsRecordedButNotInIncognitoMode`
  rot; `shown` ohne Cache-Filter → `theRowShowsOnlyCachedStickersAndLoadsNothing` rot.
- Gesamtlauf: `tools/dev.py agent-tests` grün (658 Tests in 81 Klassen, 0 Fehler, 0 übersprungen), darin alle
  Emoji-Mix-Tests (`EmojiMixPaletteTest`, `EmojiMixCommandTest`, `InputLogicTest.typedEmoji*`) und
  `DesignEditorTest`. `python3 -I scripts/docs_sync.py --check` grün.
- Nicht geprüft: Gerätetest (Stärke und Gefühl der Impulse, Fangzone mit echtem Finger, Tippen
  auf die Leiste eines Reglers: erreicht unter Robolectric nicht einmal einen unveränderten
  Material-Slider, P86), Farbwähler-Leisten nur kompiliert, nicht im Test bedient; Höhe der
  Reihe „Zuletzt gemischt“ in der Emoji-Ansicht; Einfügen in echte Apps. Kein Build, kein voller
  `upstream-tests`-Lauf, Lint nicht gelaufen (gemeinsam genutzte Maschine).
## Etappen 30 und 31

- Auftrag (Lead, 2026-11-13): 30 Diagnose-Seite, 31 Feinschliff Symbole malen (niedrige Befunde aus
  „Build 12: Prüfer zu Etappe 27“). Gebaut in einer Arbeitskopie, kein Prüfer-Review.
- 30 gebaut: `A/Diagnose.kt`, `A/SubtypeSwitchLog.kt`, `K/settings/screens/AgentDiagnoseScreen.kt`,
  `agent_diagnose_strings.xml`; Einträge in `MainSettingsScreen.kt` (nach „Erweitert“) und
  `SettingsNavHost.kt`; `HapticChain.deviceFor` jetzt `internal`; `SubtypeChoice.holds` meldet
  eine behaltene Wahl. Engine: vier Einzeilen-Hooks in `LatinIME` (P98). Der Sprachwechsel wird nur
  im Speicher gehalten (Grund, Zeit, Sprach-Tag, letzte fünf), ohne Paketnamen. Kein
  Schlüsselstatus auf der Seite, kein `FLAG_SECURE`.
- 31 gebaut: Tipp-Regel `C/InkDots.kt` mit `SymbolInk.onInkUp` und zwei Zeilen in
  `PointerTracker.onUpEvent` (Down nachholen, P96); `moveInk` zeichnet nach dem Verwerfen keine
  Spur (eine Zeile); Wortvorschlag im Fenster `SymbolDrawing.beforeSuggestionPick` (eine Zeile in
  `LatinIME.pickSuggestionManually`, P97); ausgegrauter Gestenschalter (`GestureTypingScreen.kt`);
  `DrawingPad` mit 4096 Punkten und Hinweis über 12 Striche. Befund `mUpTime`: nicht geändert,
  weil Upstream ihn nirgends setzt (gegen den Upstream-Commit geprüft, P95).
- Regel der Tipps und Abwägung: ARCHITECTURE → Symbole malen. Kalibrierung (Versuch auf dem JVM,
  nicht eingecheckt, Radius 0,3, Grenze 1,3, vor Schritt 4): erster Punkt von ÷ 390/400 und beide
  Punkte 380/400 angenommen; zufällige Tipps rund um andere Symbole 106/7200 als Punkt, fast alle
  nach nur dem ersten Strich (ein Strich) oder nach Pfeilen. Nach Schritt 4 nimmt
  `InkDotsTest` Tipps an den ÷-Stellen nach fertigen →, ←, ↔, ⇒, =, ≠, ≈, ~ in höchstens 5 % als
  Punkt (Test grün). Die volle Erkennung in Schritt 4 läuft nur für solche Kandidaten.
- Geprüft (JVM): `CT/InkDotsTest` (7: ÷ ≥ 95 % beider Punkte, Tipps abseits der Punkte eines
  Strichs, nach Symbolen ohne Striche ≤ 1 %, nach fertigen Strich-Symbolen ≤ 5 %, ohne
  Punkt-Symbole nie, eigenes „!“ auch nach Speichern und Lesen, frühere Punkte zählen nicht zum
  Strich), `InkRecognizerTest` 14 grün.
- Geprüft (Robolectric): `SymbolInkTest` 16 (neu: Tipp im Fenster schreibt erst das Symbol, dann
  den Buchstaben mit Tastendruck und Loslassen; Tipp nach → ist Buchstabe; ohne ÷ ist der Tipp an
  der ÷-Stelle Buchstabe; keine Spur nach Verwerfen durch zweiten Finger; ÷ mit zwei Tipps weiter
  ÷). `tooManyStrokesAreNoSymbol` bekam statt Tipps kurze Striche als Eingabe (Tipps sind jetzt
  Buchstaben), die Assertions sind unverändert. `SymbolSettingsScreenTest` 7 (neu: Gestenschalter
  ausgegraut mit Hinweis, Klick ändert nichts, eigener Wert bleibt, Optionen weg, Ausblendzeit
  bleibt; ohne Malen normal; Malfeld: 12 Striche gehen, der 13. sperrt „Weiter“ mit Hinweis, „Neu
  malen“ hebt ihn auf; 4200 Bewegungen zeigen „Zu lange gemalt“). `DiagnoseScreenTest` 6 (Werte mit
  Test-Vibrator, Sprachbefehl als Grund, echte Paartabelle, keine Anfrage ohne Tipp, keine
  Geheimnisse aus Zwischenablage und Einstellungen auf der Seite oder in der Kopie, kein
  `FLAG_SECURE`; „Kopieren“ = genau die angezeigten Werte; „Test“ spielt einmal; „gstatic
  erreichbar?“ = genau ein GET an www.gstatic.com ohne Cookie/Authorization, Bild nicht im Cache;
  gesperrtes Netz → „nicht erreichbar: Netzwerkfehler“; Eintrag in der Hauptliste).
  `InputLogicTest.symbolDraw*` neu 2 (Vorhersage im Fenster kommt nach dem Symbol; Korrektur des
  Worts davor ersetzt es, Symbol dahinter), `InputLogicTest.subtypeSwitch*` 3 (Tastatur vs. System,
  kein Eintrag ohne Wechsel, Sprachtaste; Befehl und behaltene Wahl bei zwei Neustarts als ein
  Eintrag; App-Hinweis, Ende des Hinweises, Sprache pro App).
- Gegenprobe (alle Korrekturen in einem Lauf zugleich zurückgenommen, danach wiederhergestellt):
  Down-Nachholen in `PointerTracker`, `moveInk`-Zeile, `beforeSuggestionPick`, `internalSwitch`,
  `expectFromStart`, `onKept`, Schritt 4 in `InkDots`. Rot wurden genau die neuen Tests:
  `InkDotsTest` (Strich-Symbole), `SymbolInkTest` (Tipp im Fenster, Tipp nach →, Punkte nur mit
  Punkt-Symbolen, keine Spur), `symbolDrawPredictionPicked…`, alle 3 `subtypeSwitch*`; die übrigen
  blieben grün. Eine Zuordnung je Korrektur gibt es damit nicht.
- Gesamtläufe: `InputLogicTest` komplett 167 Tests, nur der geerbte Fehler
  `insertLetterIntoWordHangulFails`; `SubtypeChoiceTest` 6, `HapticChainTest` 5 grün.
  `tools/dev.py agent-tests`: 656 Tests, 1 Fehler: `SpaceGesturesTest.defaultsEqualTheBehaviorBeforePerGestureSettings`
  fand `emoji_max_sdk` in den Einstellungen (geschrieben von `SupportedEmojis.load` aus der
  Hintergrund-Coroutine in `App.onCreate`, ein Wettlauf mit dem Leeren der Einstellungen im Test,
  nicht von diesen Etappen); allein wiederholt grün, ebenso `DiagnoseScreenTest` 6.
  `tools/dev.py core-tests` 231/0. Nicht gelaufen: `upstream-tests`, `lint`, Build (geteilte
  Maschine, macht der Lead).
- Nicht geprüft: kein Gerätetest (Tipp-Regel mit echten Fingern, Haptik-Stufe und „Test“ auf dem
  Xiaomi, echter Sprachwechsel durch MIUI, echte Erreichbarkeit von www.gstatic.com, Kopieren in
  echte Apps); kein Prüfer-Review der Hooks in `LatinIME` und `PointerTracker` (Pflicht).
  Der Punkt-Bound des Malfelds ist nur über den Hinweis geprüft, nicht über die Punktzahl.
- Prüfer und Korrekturen (2026-11-13): Ein lesender Engine-Prüfer las 30/31 (Stand `e083b61`),
  zwei mittlere und fünf niedrige Befunde; korrigiert in einer Arbeitskopie, nicht gepusht.
  - M1 Zwei Daumen im Wartefenster: Daumen B setzte auf, bevor der Tipp von Daumen A (Tinte)
    losließ; `startsInk` verwarf die ganze Zeichnung und A tippte nichts. Jetzt
    `SymbolInk.onSecondFinger`: Ist As Strich noch ein Tipp (≤ 10 dp), wird das Symbol sofort
    geschrieben und A über `PointerTracker.inkTapBecomesKey` ein normaler Tastendruck an seiner
    Aufsetzstelle; nur ein echter Strich wird verworfen (P104). Tests
    `SymbolInkTest.twoThumbsInThePauseTypeBothLettersAfterTheSymbol` (A hebt zuerst ab: Symbol, a, b)
    und `…KeepTheirOrderWhenTheSecondLiftsFirst` (B zuerst: weiter Symbol, a, b; A einmal gedrückt).
    `secondFingerDropsTheDrawingAndIsHandledAsUsual` lässt den ersten Finger jetzt vor dem zweiten
    21 px weit ziehen (ein echter Strich); ohne das wäre er nach der neuen Regel ein Tastendruck.
    Seine Assertions sind unverändert.
  - M2 Punkt-Zone zu groß: statt eines Kreises mit 0,3 Symbolgrößen jetzt in Richtung des Punkts
    höchstens 0,12 seitlich und 0,25 näher oder weiter, ein schräger Strich bis 20° gerade gedreht
    (ohne Drehung fiel der bestehende ÷-Test mit ±8° Neigung auf 263/300). Messung mit festem Seed
    (`InkDotsTest`, Tastatur 10 × 36 dp, Reihen 55 dp, Striche über 3–6 Tasten, Tipps auf alle
    Tasten der Nachbarreihen ±8 dp): 826/8000 Tipps werden Punkt, vorher 1972/8000; was bleibt, ist
    die Taste genau über oder unter der Mitte (die Abwägung bleibt). ÷ auf diesen Maßen 294/300 mit
    beiden Punkten, in umgekehrter Reihenfolge ebenso 294/300; der bestehende verzerrte ÷-Test 297
    erster Punkt, 294 beide (Grenze ≥ 95 % unverändert). Die übrigen Raten: nach Symbolen ohne
    Striche 0/1720, nach fertigen Strich-Symbolen 0/960. Nicht umgesetzt, weil sie nicht trägt: die
    vorgeschlagene dritte Bedingung „Strich plus Tipp gleicht ÷ klar besser als –“. Mit `$P` ist sie
    schief: Strich plus Tipp darüber ÷ 0,45 vor – 0,66, darunter – 0,65 vor ÷ 0,89, also wäre jeder
    erste Punkt unter dem Strich ein Buchstabe. Auch mit Teilvorlagen (Strich plus ein Punkt) und
    Faktor 1 nahm sie mit unterem Punkt zuerst nur 185/300 ÷ an und sortierte kaum Buchstaben aus
    (809 statt 827/8000 im Versuch, nicht eingecheckt); Faktor 1,5 bzw. 2 nahm unten zuerst 1 bzw. 0
    an. Neue Tests: `lettersBesideTheMiddleOfADashAreLetters` (Nachbartasten der Mitte in beiden
    Nachbarreihen Buchstaben, die Taste genau darüber Punkt), `lettersTappedAroundADrawnDashAreRarelyDots`
    (≤ 12 %), `aDivisionSignDrawnOverTheKeysKeepsBothDots` (beide Reihenfolgen ≥ 95 %).
  - N1 Der nachgeholte Down nahm die Taste an der Up-Stelle; ein Tipp, der bis 10 dp auf die
    Löschtaste rutschte, löschte. Jetzt die Aufsetzstelle (`mInkDownX/Y`). Test
    `aTapThatSlidesALittleTypesTheKeyWhereItTouchedDown`.
  - N2 Ein in Schritt 4 abgelehnter Tipp rechnete zwei volle Erkennungen im Hauptthread.
    `InkRecognizer` merkt sich die letzte Zeichnung mit Ergebnis, und `SymbolDrawing` gibt `InkDots`
    seinen eigenen Erkenner; das Schreiben danach rechnet nicht neu. Zähler `recognitions`; Tests
    `InkDotsTest.aTapThatIsNoDotIsRecognizedOnceAndTheWrittenDrawingIsFree` und
    `InputLogicTest.symbolDrawTapInThePauseThatIsNoDotMatchesTheDrawingOnce` (echte Verdrahtung, 1
    statt 2; die Zeichnung ist verschoben, weil derselbe Pfeil aus einem anderen Test der Klasse
    sonst schon im Merker läge).
  - N3 „Behalten“ wurde bei jedem Neustart notiert, auch wenn die App gar nicht umgeschaltet hätte.
    Jetzt nur, wenn `getSubtypeForLocales` einen anderen Subtyp liefert. Gefragt wird mit dem neuen
    Parameter `peek` (Upstream-Datei `SubtypeUtils.kt`, zwei Zeilen), denn der normale Aufruf
    setzt `overriddenByLocale` und schaltete später zurück (P98). In `LatinIME` bleibt es die eine
    bestehende Prüfung, sie bekommt nur die Abfrage als dritten Wert. Test
    `InputLogicTest.subtypeSwitchLogNotesAKeptChoiceOnlyWhenTheFieldWouldHaveSwitched` (ohne
    Hinweis und mit spanischem Hinweis kein Eintrag, mit deutschem einer; danach Sprachtaste und
    Feld ohne Hinweis: keine Rückschaltung).
  - N4 Die Diagnose-Seite baute alle Abschnitte bei jeder Neuzusammensetzung neu, samt Auflisten
    der Wörterbuch-Dateien im Hauptthread. Jetzt einmal, „Symbole malen“ über `Dispatchers.IO`.
    Test `DiagnoseScreenTest.theSectionsAreBuiltOnceAndTheDictionaryFilesListedOffTheMainThread`
    (eine Abfrage trotz gstatic-Prüfung, nicht im Hauptthread).
  - N5 Das Malfeld meldete schon bei genau 4096 Punkten „Zu lange gemalt“, die Tastatur nimmt 4096.
    Jetzt nimmt das Feld bis 4097 auf und meldet erst über 4096. Test
    `SymbolSettingsScreenTest.drawingPadTakesExactlyAsManyPointsAsTheKeyboard` (4096 gehen, 4097
    nicht). Damit ist der Punkt-Bound jetzt auch über die Punktzahl geprüft.
  - Bewusst unverändert, dokumentiert in ARCHITECTURE → Symbole malen: der Randfall eines
    Wortvorschlags im Fenster bei einbuchstabiger Vorhersage bzw. Satzzeichen-Leiste; langes Halten
    eines Buchstabens im Fenster tippt beim Loslassen den Grundbuchstaben (Tinte hat keinen
    Langdruck-Timer).
  - Gegenproben: Alter `PointerTracker` ganz: rot genau die beiden Zwei-Daumen-Tests und der
    Rutsch-Test. Nur die Zeile der Aufsetzstelle zurück: rot nur der Rutsch-Test. Alte
    `InkDots`/`InkRecognizer` (nur mit Zähler und Konstruktor, damit der Test übersetzt): rot genau
    die beiden Buchstaben-Tests (Taste 3 in Reihe 1 Punkt; 1972/8000) und der Zähler-Test (2 statt 1).
    In einem Lauf zugleich der Merker aus, `onKept` ohne Bedingung, alte Diagnose-Seite und altes
    Malfeld: rot genau die vier neuen Tests dazu (N2 App, N3, N4, N5), die übrigen grün.
  - Läufe: `SymbolInkTest` 19, `SymbolSettingsScreenTest` 8, `DiagnoseScreenTest` 7,
    `SubtypeChoiceTest` 6, `InputLogicTest.symbolDraw*` und `subtypeSwitch*` zusammen 17, alle grün.
    `tools/dev.py core-tests` 235/0. `tools/dev.py agent-tests` 704 Tests, 1 Fehler: wieder
    `SpaceGesturesTest.defaultsEqualTheBehaviorBeforePerGestureSettings` (`emoji_max_sdk`, derselbe
    Wettlauf wie oben, nicht von diesen Korrekturen); allein wiederholt 7/7 grün. Nicht gelaufen:
    `upstream-tests`, `lint`, Build
    (geteilte Maschine).
  - Nicht geprüft: kein Gerätetest (zwei Daumen, Punkt-Zone mit echten Fingern); kein
    Prüfer-Review der neuen Änderungen in `PointerTracker` (`startsInk`, `inkTapBecomesKey`,
    Aufsetzstelle), `LatinIME` (dritter Wert der `holds`-Prüfung) und `SubtypeUtils.kt` (Pflicht).

## Zusammenführung der Runde (Etappen 28–33 ohne 29)

- Etappen 30/31 nach 33 gemergt: Die Diagnose-Seite nutzte noch die alte `HapticChain`-API
  (`Direction`); angepasst an `Feel` (`Diagnose.chain`): Rastpunkt-Tick und letzter Rastpunkt mit
  je eigener Stufe (der letzte braucht `quick_rise`/`thud` und kann tiefer liegen), „Test“ spielt
  den letzten Rastpunkt. `DiagnoseScreenTest` an die neuen Texte angepasst (gleiche Prüfungen,
  zusätzlich die Stufe des letzten Punkts) und grün.
- Prüfer (lesend) zu `51ca775` (28, 32): kein hoher Befund. Behoben (mittel): Ein Bereichsende
  neben einer runden Rast war per Ziehen nicht erreichbar (Vibrationsdauer −1 = Systemstandard,
  Tastenton −0,01, Zwischenablage-Größe 1001 = kein Limit). Runde Rasten in der Fangzone eines
  Endes oder des Standardwerts entfallen, und am Bereichsende gewinnt immer das Ende. Test
  `SliderFeelTest.anEndNextToARoundValueIsReachedByDragging` (Gegenprobe ohne die Korrektur rot);
  die Erwartung der Rasten von −1..100 ist entsprechend ohne 0. Offen, niedrig: Tipp auf die
  Leiste am Gerät ungeprüft; jedes Merken in „Zuletzt gemischt“ lädt die Einstellungen neu
  (`Settings.reloadOnChanged`); bis zu 12 Dateiprüfungen im Hauptthread; Testlücken (gesperrt,
  No-Learning beim Merken).
- Agent-Tests auf dem Stand vor 30/31 (9363dc3): grün.
## Etappe 29 (RGB-Überarbeitung)

Auftrag des Owners vom 2026-11-13 (ROADMAP 29a–29d mit Referenzbildern; Nachträge: alle sechs
Muster bewegt und Pflicht, Muster je Element wählbar mit „Gleiches Muster überall“ als Vorgabe).
Gebaut in einer Arbeitskopie, ohne Prüfer (folgt durch den Lead), ohne Gerätetest.

- **29a Seite:** große Live-Vorschau oben (runde Ecken), Karten je Gruppe, Muster als
  Bildergalerie, Kurven-Editor, Wahl des Elements. Geprüft in Robolectric
  (`RgbSettingsScreenTest`: alle bisherigen Optionen speichern weiter, Kurve per Touch, eigene
  Looks, bewegte Vorschau aller sechs neuen Muster) und durch Ansehen der Bilder
  `app/build/agent-previews/rgb-settings-top.png` und `rgb-settings-curve.png`.
- **29b Weichheit:** gemessen in `RgbPatternsTest`: größter Sprung zwischen benachbarten Werten
  bei 96 Spalten (von 255) Rauten 12, Raster 11, Höhenlinien 19, Lamellen 22 (ohne ihre gewollte
  Kante), LED 25, Tinte 43; je Bild bei 30 s Dauer alle Muster höchstens 3, Lamellen an der
  wandernden Kante 8. Regenbogen: zweite Differenz der Kanäle bei 3°-Schritten 3 statt 13 (HSV).
  Rot–Blau in OKLCH heller und farbiger als in sRGB. Render-Test: weiches Licht in der Taste
  entspricht dem gerechneten Raster (±4 je Kanal), Kantensprung zwischen q und w kleiner als ein
  Drittel des Sprungs bei einfarbigen Tasten.
- **29c Muster:** sechs Texturmuster, deterministisch, bewegt (Kern- und Vorschau-Test), mit
  Merkmalstests (Tinte mit dunklen und hellen Flächen, Lamellen mit Farbwechsel > 90° an der
  Kante, Raster mit dunklen Ecken und Schraffur auf der Taste, LED-Blende auf Tasten und im
  Spalt, Kacheln nahtlos). Selbst mit den Referenzbildern verglichen (Bilder unten): Rauten,
  Lamellen, Raster, LED-Punkte und Höhenlinien kommen den Vorlagen nahe; die Tinte ist weicher
  und gröber als die Vorlage, weil das Farbfeld nur im 48er-Raster gerechnet wird (schärfere
  Ränder zeigten Blöcke, P93). Ein Kachel-Versuch für feinen Rauch sah wie Risse aus und wurde
  verworfen.
- **29d Kurve:** `RgbCurveTest` (Punkte, kein Überschwingen, keine Sprünge, Grenzen, Text,
  Vorlagen, Migration aller fünf alten Verläufe aus dem Text von Etappe 26, eigene Looks), Vorlagen
  gegen die alten Formen (Sägezahn und An- und Abstieg exakt, Sinus ≤ 0,02, Dreieck ≤ 0,03,
  Stufen außerhalb der 1-%-Anstiege exakt auf drei Nachkommastellen). Compose-Test: Tippen fügt
  hinzu, Ziehen verschiebt, Ende nur senkrecht, langes Drücken und Hinausziehen entfernen, „+“ und
  „Punkt entfernen“, Vorlagen, Stufen-Regler. Annahme zur Bedeutung der Achsen ist mit dem Owner
  noch zu bestätigen.
- **Kosten je Bild** (`RgbPatternsTest.oneFrameOfEachPatternFitsTheBudget`, JVM des geteilten
  Testrechners, 1634 Werte): Wellen 0,19–0,27 ms, Rauten 0,37, Lamellen 0,36, LED 0,35,
  Raster 0,46, Höhenlinien 0,67, Tinte 1,03 ms; Kacheln einmalig 21 ms (Rauten) und 38 ms
  (Höhenlinien). Nicht gemessen: Telefon, GPU, Akku.
- **Befehle:** `python3 tools/dev.py core-tests`-Äquivalent (`:agent-core:test`): 245 Tests,
  0 Fehler. RGB-App-Tests (`--tests 'helium314.keyboard.agent.Rgb*'`): 30 Tests, 0 Fehler.
  Agent-Tests (dieselben Filter wie `python3 tools/dev.py agent-tests`): 644 Tests, 0 Fehler
  (9 min 48 s, zwei Worker). Volle Upstream-Suite nicht lokal (geteilter Rechner; GitHub-CI).
  `python3 tools/dev.py build`: erfolgreich (Debug-APK unter `artifacts/`, nicht versioniert).
  `python3 -I scripts/docs_sync.py --check`: grün.
- **Geändert am Upstream:** nur eine Zeile in `KeyboardView.java` (`drawOverBackground`), die
  Datei war schon geändert.
- **Bilder** (nicht im Repo): `app/build/agent-previews/rgb-keyboard-{ink,diamonds,slats,hatch,led,topo,own-looks}.png`,
  `rgb-preview-*.png`, `rgb-settings-*.png`, `agent-core/build/rgb-previews/*.png`.
- **Nicht geprüft:** jedes Gerät (Aussehen, Flüssigkeit, Bildkosten, Akku, TalkBack im
  Kurven-Editor), Android unter 10 (Shader-Verhalten des alten Renderers, P90), das Ziehen mit
  echten Fingern, Lesbarkeit über sehr feinen Mustern am Gerät.

## Etappe 35: Tolerantere Emoji-Vorschläge

- Gebaut (eigener Branch `ccr-6e6a4bdd-hhrd6w`, abgezweigt von `claude/build-12-completion-3unh51`
  eef5254): lokale CLDR-Stichworttabelle (`emoji_keywords.bin`, Generator reproduzierbar, Summen in
  `docs/BUILD.md`), Matcher `C/EmojiKeywords.kt` mit `C/EmojiSynonyms.kt`, Anbindung
  `A/EmojiSuggestions.kt`, Haken in `Suggest.kt`. Vom Owner gewählte Fälle: Wortformen,
  Groß/klein und Umlaute, Wortanfänge, Tippfehler, Deutsch/Englisch gemischt, Synonyme; höchstens
  zwei Emojis hinter den ersten zwei Wörtern.
- Geprüft (JVM): `CT/EmojiKeywordsTest` mit echten Fällen (Katzen, Mäuse, KAESE, Schmetterl,
  Kafee, Hnud, party, geil, gatos; Funktionswörter und häufige Wörter leer; „Tag“/„Gift“ als
  deutsches Wort ohne englische Treffer; Format, Faltung, Stamm, Editierabstand).
- Geprüft (Robolectric): `SuggestTest.emoji*` (Platz hinter den ersten zwei Wörtern, nie
  Autokorrektur, getipptes Wort in der Mitte, Zählung mit HeliBoards Emojis, Sprache des
  Wörterbuchs, keine Vorhersagen, keine Tippfehler bei echten Wörtern) und
  `InputLogicTest.typedEmojiSuggestions*` (echter Pfad InputLogic → Suggest; aus bei Schalter aus,
  Inkognito, `IME_FLAG_NO_PERSONALIZED_LEARNING`, Passwortfeldern und gesperrtem Gerät; nichts wird
  getippt).
- Gemessen (Build-Rechner, JVM, nicht das Telefon): Suche im Mittel 7–12 µs, p99 50–120 µs je
  Tastendruck über 303 Tastendrücke; Laden der Tabelle 125–135 ms (warm), einmalig auf einem
  Hintergrund-Thread; laut Prüfer etwa 5 MB Heap insgesamt.
- Prüfer: zwei Runden. Runde 1: kein hoher Befund; mittel behoben: Rangfolge (benanntes Emoji
  zuerst), Fehltreffer bei häufigen Wörtern (Teilwörter mehrteiliger Stichwörter nur exakt,
  mehr Funktionswörter), deutsch/englische Homographe; niedrig behoben: Ladegrenze im Test,
  Sperrabfrage höchstens alle 2 s, Endung „-ungen“, Test „getipptes Wort in der Mitte“.
  Runde 2: neuer mittlerer Befund behoben (englische Wörterbuchwörter beim mehrsprachigen Tippen:
  jetzt fragt die Sprache des Wörterbuchs, das das Wort kennt), „-chen“ nur an längeren Wörtern.
- Bewusst offen (niedrig): ein beliebtes, nur lose passendes Emoji kann vorn stehen („Hut“ → 🥳
  vor 🤠, „happy“ → 😂); deutsche Stamm-Fehltreffer wie „Fall“ → 🪤; bis zu 2 s nach dem Sperren
  noch Emojis; ein fehlgeschlagenes Laden der Tabelle wird bis zum Neustart nicht wiederholt;
  Gestenschreiben bekommt die neuen Treffer nicht.
- Gesamtläufe auf 6abe9ac: `core-tests` grün, `agent-tests` grün, `build` grün, `lint` grün,
  `upstream-tests` 860 Tests mit genau den 7 bekannten Fehlern (5 Baseline, `XLinkTest.layoutsLinksInternal`
  und `otherLinks` mit 403 vom Netzwerkfilter). Nicht geprüft: Gerätetest (Trefferqualität beim
  echten Tippen, Ladezeit und Speicher auf dem Telefon).

## Etappe 29: Prüfer (lesend) und Sofort-Korrektur

- Prüfer zu `b2783bb`: Absturz (mittel) beim Ziehen eines eingeklemmten Kurvenpunkts:
  `RgbCurve.move` rief `coerceIn` mit Untergrenze > Obergrenze (1 ulp bei Nachbarn im Abstand
  2 × `MIN_GAP`), die Ausnahme in der Zeiger-Coroutine hätte Einstellungen und Tastatur beendet.
  Behoben: Bei leerem Bereich bleibt der Punkt stehen. Test
  `RgbCurveTest.aSqueezedPointCanBeDraggedWithoutACrash` (751 echte Fälle; Gegenprobe ohne Fix:
  `IllegalArgumentException`). `:agent-core:test` grün.
- Die klötzchenhafte Live-Vorschau im Bild der Einstellungsseite kommt laut Prüfer von Robolectric
  (SDK 28 setzt den Filter eines `BitmapShader` fest auf aus), nicht vom Code; der Tastatur-Test
  mit SDK 36 ist weich. Die übrigen Befunde (Kacheln auf dem UI-Thread, Kachel-Cache zu klein,
  Vorschaubilder bei jeder Änderung neu, Filter ab API 33 ausdrücklich setzen, Speicher,
  TalkBack im Kurven-Editor, langes Drücken löscht sofort) werden in einer eigenen Korrektur
  behoben.
- Lokaler Agent-Lauf auf ceca94d: 698 Tests, 84 Fehler, alle `ZipException` beim Laden von
  Klassen, weil der Lead währenddessen `agent-core` neu baute (Infrastrukturabbruch, kein
  Befund). Maßgeblich ist der GitHub-Lauf.
- Korrektur der übrigen Prüfer-Befunde (eigene Arbeitskopie, ohne Gerätetest, ohne neuen Prüfer):
  - **M1 Kacheln auf dem UI-Thread:** `RgbTiles` (in `A/RgbTexture.kt`) macht die Kacheln auf
    einem Hintergrund-Thread; Zeichnen fragt nur nach (`ready`, blockiert nie) und zeichnet bis
    dahin ohne Detail, danach zeichnen Tastatur und Vorschau über einen Zuhörer neu;
    `RgbLighting.load` bestellt die Kacheln schon beim Lesen der Einstellung. Test
    `RgbTilesTest.drawingNeverWaitsAndTheTileIsMadeOffTheMainThread` (zurückgehaltener Executor:
    Zeichnen ohne Kachel, kein Bau auf dem Main-Thread, Bau auf einem anderen Thread, Zuhörer auf
    dem Main-Thread, danach Kachel da) und `theRealExecutorIsABackgroundThread`. Gegenprobe:
    Kachel wieder beim Zeichnen gebaut → „no tile before it is made“ schlägt fehl.
  - **M2 Galerie und Cache:** Cache hält alle fünf Kachel-Muster
    (`RgbTilesTest.theCacheHoldsEveryTiledPatternAndNothingIsMadeTwice`). Galeriebilder hängen
    nur an Muster und Farben, entstehen auf `Dispatchers.Default`, nach der ersten Runde erst
    nach 250 ms Ruhe, und werden gemerkt. Test
    `RgbSettingsScreenTest.theGalleryIsNotRebuiltWhileACurvePointIsDragged` (12 Zwischenstände
    eines gezogenen Punkts: kein neues Bild; andere Farben: neue Bilder, keine neue Kachel).
    Gegenprobe: Kurve im Schlüssel → 24 statt 12 Bilder, Test schlägt fehl.
  - **L1 Speicher:** Cache ohne Pixel-Array (nur Bitmap und Mittelfarbe), geleert bei
    ausgeschalteter Beleuchtung und über `LatinIME.onTrimMemory` → `RgbLighting.onTrimMemory`
    (`theTilesAreDroppedWhenTheLightIsOffOrMemoryRunsLow`, `aTileMadeBeforeAClearIsNotKept`).
  - **L2 Einfarbige Tasten:** nur noch die Lage der Kachel, kein Raster
    (`RgbLightingRenderTest.solidKeysComputeNoGridUnderTheKeys`; Gegenprobe ohne Fix: „no grid
    under solid keys“ schlägt fehl).
  - **L3 Filter:** alle `BitmapShader` über `RgbTexture.bitmapShader`, ab API 33
    `FILTER_MODE_LINEAR` (P105). `theNewPatternsMoveInThePreview` und der neue
    `thePreviewLightIsSmoothLikeTheGrid` laufen mit SDK 36: Vorschau gegen das bilineare Raster
    höchstens 4 je Kanal über 34 Pixel der Leertaste, gegen Blöcke bis 32. Gegenprobe mit SDK 28
    (Robolectric zeichnet ungefiltert): schlägt fehl (8 statt höchstens 4). Dass gerade das
    Setzen des Filtermodus den Unterschied macht, zeigt der Test nicht: Robolectric mit SDK 36
    filtert auch über die Paint-Flagge; nur am Gerät prüfbar.
  - **L4 Allokationen:** `RgbOverlay.transform` schreibt in ein festes Feld (ohne `Quad` und
    Closure), Shader je Taste in `SparseArray` (`RgbPatternsTest.theTileMatrixIsReusedAndItsDriftRepeats`).
  - **L5 Sprung im Fluss:** `RgbEngine.flow` bricht an `RgbPatterns.flowPeriod` um, wo sich das
    Farbfeld genau wiederholt (P107); die Phase verliert ab 10⁶ nur ganze `DRIFT_PERIOD`.
    Tests `theFlowRepeatsExactlyAtItsPeriod` (größte Abweichung nach einer Periode 2·10⁻⁴, Tinte)
    und `noJumpAcrossTheWrapOfTheFlow` (alle Texturen, beide Richtungen, höchstens 8 je Bild bei
    30 s; Phase über 10⁶ ohne Sprung in Farbe und Kachel). Gegenprobe mit dem alten Umbruch bei
    4096: Tinte springt um 197.
  - **L6 Kurven-Editor:** langes Drücken wählt nur noch, entfernt nichts; TalkBack-Aktionen
    hoch, runter, links, rechts (Enden nur hoch, runter) und entfernen für den gewählten Punkt;
    gewählter Punkt mit `rememberSaveable`; Galerie bekommt `look.reverse`; Kommentare berichtigt.
    Tests `theCurveIsEditedByTouch` (geändert: langes Drücken lässt 3 Punkte, „Punkt entfernen“
    entfernt) und `theSelectedPointHasTalkBackActions`. Gegenprobe mit altem Langdruck: „a long
    press removes nothing“ schlägt fehl. TalkBack selbst nicht am Gerät geprüft.
  - Sichtbar für den Owner (bleibt, steht in ANLEITUNG und Release-Notes): alte Einstellungen
    ohne `keys=` laufen weich; „Jede Taste einfarbig“ holt das alte Bild zurück.
  - **Befehle:** `python3 tools/dev.py core-tests`: 276 Tests, 0 Fehler. RGB-App-Tests
    (`--tests 'helium314.keyboard.agent.Rgb*'`): 39 Tests, 0 Fehler (Render 22, Seite 12, Kacheln 5). `python3 tools/dev.py agent-tests`:
    704 Tests, 1 Fehler:
    `SpaceGesturesTest.defaultsEqualTheBehaviorBeforePerGestureSettings` (leere Einstellungen
    erwartet; dieselbe Stelle wie der bekannte Wettlauf mit `SupportedEmojis.load`, siehe „Etappen
    30 und 31“; allein wiederholt grün; die Ursache in diesem Lauf nicht nachgelesen). Kein
    Build, keine volle Upstream-Suite. `python3 -I scripts/docs_sync.py --check`: grün.
- Rückmeldung des Owners zu Build 14 (Xiaomi: Ruckeln im RGB-Menü, besonders beim Kurven-Editor;
  Verzögerungen der Tastatur), in derselben Arbeitskopie umgesetzt, ohne Gerätetest:
  - **Raster im Hintergrund:** `RgbTexture` rechnet das Raster auf dem Thread `rgb-light` aus
    einer Kopie der Engine, doppelt gepuffert; der UI-Thread übernimmt nur fertige Raster und
    bestellt neue nur bei Änderung (stehende Muster: keins je Bild). 30 Bilder/s sind schon
    Vorgabe. Tests: `RgbTilesTest.drawingNeverWaitsAndTheTileIsMadeOffTheMainThread` (auch das
    Raster nicht beim Zeichnen), `RgbLightingRenderTest.stillPatternsComputeNothingPerFrame`
    (statischer Regenbogen 0 Raster in 10 Bildern, Welle eins je Bild, nicht auf dem
    Main-Thread), `RgbFrameStatsTest.aCopiedEngineLightsLikeTheOriginal`. Gegenproben: Raster
    wieder beim Zeichnen → „the grid is not computed while drawing either“ schlägt fehl; immer
    neu bestellen → statischer Regenbogen 10 statt 0 Raster, schlägt fehl. Alle Pixeltests
    (Weichheit gegen das bilineare Raster ±4, Texturen, eigene Looks) laufen unverändert auf dem
    Hintergrund-Raster (P108).
  - **Kurven-Editor:** Zwischenstände nur für die Vorschau (`draft`), Seite nicht neu
    zusammengesetzt, Editor zeichnet den Punkt selbst, Palettentabellen geteilt
    (`RgbFrameStatsTest.enginesWithTheSameColoursShareTheirPaletteTable`). Test
    `RgbSettingsScreenTest.draggingACurvePointDoesNotRecomposeThePage`: 24 Bewegungen in 28
    Bildern, Seite 1-mal (beim Loslassen), Vorschau 2 Engines, während des Haltens 0-mal. Gegenprobe
    mit dem alten Weg (Zwischenstand in die Seite): „no page composition while dragging“ schlägt
    fehl (1 statt 0).
  - **Selbstmessung:** `RgbLighting.frameWork()` (`RgbFrameStats`, letzte 5 s: Licht auf dem
    UI-Thread, ganzes Zeichnen der Tastatur über den neuen Hook `endDraw` in
    `KeyboardView.onDrawKeyboard`, Raster im Hintergrund). Tests
    `RgbFrameStatsTest.averageAndMaximumOverTheLastSeconds`, `uiThreadLightingWorkPerFrame`
    (misst über die API). Nicht auf der Diagnose-Seite angezeigt (Auftrag).
  - **Messung (JVM, Robolectric-Software-Zeichnen, nicht das Telefon)** mit
    `uiThreadLightingWorkPerFrame`, 60 Bilder ohne Pause, alle zwölf Muster, Tasten, Hintergrund und
    Beschriftung leuchten: Vorbereitung je Bild auf dem UI-Thread vorher (Raster auf dem
    UI-Thread) 0,15–0,9 ms im Mittel (Tinte 0,91, Höhenlinien 0,57), jetzt 0,03–0,20 ms (Tinte
    0,07); Raster im Hintergrund 0,11–0,9 ms (Tinte 0,89). Das Zeichnen des Lichts durch die
    Tastenmasken bleibt auf dem UI-Thread: hier 2,5–6 ms je Bild, Lamellen und Raster 17–18 ms,
    ganzes Zeichnen der Tastatur 7–45 ms, vorher wie nachher (Software-Rastern; am Telefon
    rastert die GPU). Ob das am Telefon ruckelt, zeigt erst `frameWork()` auf dem Gerät.
  - Nicht geprüft: jedes Gerät (Flüssigkeit beim Ziehen und Tippen, echte Bildkosten mit GPU,
    Akku); das erste Bild ohne Licht und das um ein Bild verspätete Licht nicht angesehen.
  - **Befehle auf dem Endstand:** `python3 tools/dev.py core-tests`: 279 Tests, 0 Fehler.
    RGB-App-Tests: 42 Tests, 0 Fehler (Render 24, Seite 13, Kacheln 5).
    `python3 tools/dev.py agent-tests`: 724 Tests, 0 Fehler (10 min 32 s). Kein Build, keine
    volle Upstream-Suite, kein Prüfer für den neuen Hook in `KeyboardView.java`.
    `python3 -I scripts/docs_sync.py --check`: grün.

## Diagnose: Kosten der RGB-Beleuchtung (Rückmeldung zu Build 14)

- Der Owner meldete am Telefon Verzögerungen der Tastatur und Einbrüche im RGB-Menü. Die
  Korrektur (`b9b1bfe`, `c1fd67b`) rechnet Raster und Kacheln im Hintergrund; auf der JVM bleibt das
  Zeichnen des Lichts durch die Tastenmasken der größte Posten (2,5–6 ms, gedrehte Kachel 17–18 ms
  in Robolectric-Software). Ob das am Telefon (GPU) reicht, zeigt erst eine Messung dort.
- Gebaut: Abschnitt „RGB-Beleuchtung (letzte 5 s mit Licht)“ auf der Diagnose-Seite aus
  `RgbLighting.lastFrameWork()` (`RgbFrameStats.summaryOfLastActivity`: die 5 s vor dem letzten
  Eintrag, auch wenn die Tastatur jetzt verborgen ist): gemessene Bilder, Licht im UI-Thread,
  ganzes Zeichnen der Tastatur, Raster im Hintergrund (jeweils Ø und max). Tests:
  `RgbFrameStatsTest.theLastActivityIsSummedUpAlsoLaterOn`, `DiagnoseScreenTest` (Zeile ohne
  Messung) grün.

## Etappe 36 (Erkennung Symbole malen)

- Auftrag (Lead, dringend, Rückmeldung des Owners zu Build 14 am Xiaomi): (1) ein Tipp übernimmt
  das Symbol bis dahin, der Rest wird ein neues Zeichen; (2) ⇒ wird zu ≠; (3) Zeichen werden oft
  nicht erkannt; (4) die Eingabe „glitcht“. Dazu drei niedrige Befunde des Prüfers zu `057a6d5`
  (N-a, N-b, N-c). Gebaut in einer Arbeitskopie, kein Prüfer-Review, kein Gerätetest.
- Gebaut (Kern): `C/InkTiming.kt` (`InkTap`: Tipp unter 4 dp; `InkPace`: Pause 900 ms, dann
  2,5 × Median der letzten neun Lücken, 600–1500 ms), `C/PersonalInkSymbols.kt` (persönliche
  Vorlagen, 5 je Symbol, 80 Symbole, Format der eigenen Symbole), `InkRecognizer` mit Richtungs-
  und Strukturterm (`InkCloud.angles`, `lineCount`, `dotCount`, `Tuning`), Drehung nur für nahe
  Vorlagen, `CUTOFF`, neue Schwellen 1,8 / 2,3 / 2,6; `InkSymbols` 105 statt 73 Vorlagen
  (Fingervarianten), `OwnInkSymbols.encode/parse` mit Grenzen als Parameter.
- Gebaut (App): `SymbolInk` (neue Tipp-Regel, Pause nach Tempo, nur Lücken vor echten Strichen
  zählen, `Sink.onWaiting`, `takeWaiting`, `startsInkOn`), `SymbolDrawing` (Vorschau-Chip
  „✎ →“ plus zwei Alternativen, Lernen aus Korrekturen mit Verschieben und Zurücknehmen,
  persönliche Vorlagen im Erkenner), `AgentSymbolsScreen` (Schalter „Aus meinen Korrekturen
  lernen“, Liste mit Löschen, „Kalibrieren“ als Karte), `AgentPreferences.SYMBOL_LEARNING`, Texte
  in `agent_symbol_strings.xml`. `PointerTracker.java` (Touch-Verarbeitung, Prüfer Pflicht):
  `startsInk` mit `SymbolInk.startsInkOn` statt `Character.isLetter`; zweiter Finger macht den
  zeichnenden Tracker nur mit `mInInk` zur Taste, sonst `SymbolInk.cancel` (N-a); `onCancelEvent`
  beendet auch den Folgestrich eines anderen Trackers (N-a); `moveInk` nur für den malenden Zeiger.
  `LatinIME` unverändert.
- Ursachen, soweit im Code zu finden (Gerät nicht verfügbar): (1) Die Pause von 500 ms ist für
  einen Finger knapp; nach ihr geht der nächste Strich durch HeliBoards Gestenstart, und ein kurzer
  Strich wird dort ein Buchstabe. Zusätzlich begann ein Folgestrich auf einer Satzzeichen-Taste
  (Komma, Punkt; etwa der Strich unter ≤ oder der Kopf von ⇒ in der unteren Reihe) keine Tinte:
  das Symbol wurde geschrieben, der Strich war das erste eines neuen. Die 10-dp-Tipp-Grenze traf
  nur Striche unter 10 dp. (2) `$P` sieht nur Punktwolken. (3) wenige Vorlagen, Drehtoleranz
  ±10°. (4) Gefundene Zustandsfehler: N-a (Geisterbuchstabe nach `ACTION_CANCEL` an einem anderen
  Tracker), Spur eines verworfenen Strichs, während ein anderer Finger malt, Satzzeichen-Taste
  (oben). Was der Owner mit „glitcht“ meinte, bleibt ungeklärt.
- Benchmark (`CT/InkBenchmarkTest.kt` mit `CT/InkFingerModel.kt`, feste Seeds, 50 Zeichnungen je
  Symbol): Die Formen sind im Test neu geschrieben, nicht aus `InkSymbols` übernommen; je Symbol
  mehrere Arten zu malen (Kopf als Haken oder zwei Striche, Pfeil ohne Absetzen, ♥ in zwei
  Hälften, € mit einem Balken, Proportionen zufällig), dann Reihenfolge und Richtung zufällig,
  Enden bis 12 % gekürzt, Biegung, Haken beim Absetzen (jeder fünfte Strich), Zittern, ungleiche
  Abtastung, Drehung bis ±15°, Streckung bis 25 % je Achse („hart“; „mild“: ±8°, 12 %, ohne
  Kürzen, Haken und Umordnen). Die neuen Vorlagen decken dieselben Malarten ab (nicht dieselben
  Zufallswerte): Kopf als Haken oder als zwei Striche, Pfeil ohne Absetzen, ⇒ mit Balken bis unter
  einen hohen Kopf, zweiteiliges ♥, € mit einem Balken, flache und schmale °, ★ und ×, = mit
  ungleichen Balken, ≠ mit flacherem Schrägstrich, ≈ und ~ mit kleinerer Welle, ≤ mit kürzerem
  Winkel. Die 96,2 % sind also eine Messung innerhalb der Verteilung, für die die Vorlagen gebaut
  wurden, keine Aussage über die Handschrift des Owners (Prüfer M4). „Vorher“ ist
  der Code von `ec91dac` (alter Erkenner und alte Vorlagen, vorübergehend in den Test kopiert, nicht
  eingecheckt).
  - Hart: vorher 1725/2000 (86,3 %), 91 nicht erkannt, 184 falsch; nachher 1924/2000 (96,2 %),
    43 nicht erkannt, 33 falsch. Mild: vorher 1979/2000 (99,0 %), nachher 1998/2000 (99,9 %).
  - Je Symbol hart (vorher → nachher, %): → 98→98, ← 98→98, ↓ 98→100, ↑ 98→100, ↗ 92→92,
    ↘ 92→90, ↔ 78→90, ⇒ 60→88, = 74→92, ≠ 76→90, ≈ 78→82, ~ 82→98, ≤ 96→100, ≥ 92→100,
    + 98→100, ± 98→100, × 72→100, ÷ 80→100, – 100→100, | 100→100, ∞ 90→96, √ 96→94, π 96→98,
    ° 50→98, ✓ 92→98, ♥ 86→80, ★ 52→98, € 86→98, $ 90→100, § 98→98, & 82→96, @ 98→94, # 100→100,
    % 100→100, ( 98→98, ) 100→100, [ 60→100, ] 64→100, { 80→96, } 72→88. Schlechter: ↘, √, @
    (je 2 Zeichnungen) und ♥ (3, vor allem nicht erkannt).
  - Häufigste Verwechslungen vorher: ⇒→= 17, [→( 10, ≠→= 10, ÷→– 10, [→{ 9, }→| 9, ]→} 9, ★→# 7;
    nachher: ↗→↑ 3, }→| 3, je 2 √→|, }→), ♥→°, ⇒→=, @→°, ≈→=, ↘→↓.
  - Paare (hart, vorher → nachher): ⇒→≠ 0 → 0 %, ≠→⇒ 0 → 0 %, →→↗ 0 → 0 %, ↗→→ 2 → 2 %,
    =→≈ 8 → 0 %, ≈→= 12 → 4 %, ≠→= 20 → 2 %, ⇒→→ 0 → 2 %, +↔× 0 → 0 %. Mild ≠→⇒ 2 → 0 %.
  - ⇒ als ≠: Die Verwechslung des Owners wurde nie nachgestellt (Prüfer M4): vorher 0 von je 50 ⇒ im
    Benchmark (hart und mild) und 1 von 200 im eigenen Fall. Was sie verursacht hat, ist unbekannt. Eigener
    Fall „⇒ des Owners“ (Balken bis unter einen hohen, schmalen Kopf, der die Balken wie ein
    Schrägstrich kreuzt; wie der Owner wirklich malt, ist nicht bekannt), 200 Zeichnungen: vorher
    164 ⇒, 1 ≠; nachher 190 ⇒, 0 ≠. Ob ⇒ am Telefon nicht mehr zu ≠ wird, ist offen.
  - Kritzeleien (`InkRecognizerTest`, 300 zufällige Linienzüge): vorher 20 angenommen, nachher
    10. Alter Test mit künstlich verzerrten eingebauten Zeichnungen: 2583/2625 (98,4 %; vorher
    97,5 %), schwächstes Symbol wieder % (185/225, Grenze 80 %).
  - Zeit je Zeichnung (€, verschiedene Zeichnungen, JVM, geteilter Rechner): vorher 12,4 ms gegen
    73 Vorlagen, nachher 6,8 ms gegen 105 (Drehung nur für Vorlagen bis 1,6-mal die beste, im
    Benchmark ohne Unterschied in der Trefferzahl gegenüber Drehung für alle).
  - Gewichte und Schwellen aus einem Lauf über Richtungsgewicht 0–0,3, Strichgewicht 0–0,35,
    Punktgewicht 0/0,3 und Annahme 1,3–1,9 (je 30 Zeichnungen, Seed 99, nicht eingecheckt): ohne
    Richtung und Struktur, mit den neuen Vorlagen (Zwischenstand, 103) 90,4 % bei 1,3 und 74 von
    500 Kritzeleien angenommen; gewählt 0,25 / 0,25 / 0,3 mit 1,8 / 2,3 (96,5 %, 32 von 500; 1,9
    gab 97,1 % und 33, die kleinere Schwelle gewählt).
- Tipp-Regel (`CT/InkTimingTest.kt`, synthetisches Modell, keine echten Finger: Tipps 90 ms
  Median, 1,2 dp Bewegung je Achse, jeder sechste „gerollt“ mit 3 dp; absichtliche kurze Striche
  5–40 dp bei 100–600 dp/s). Falsche Striche = Tipps, die als Strich zählen (Buchstabe fehlt, die
  Zeichnung bekommt einen Strich); falsche Tipps = kurze Striche, die als Tipp zählen:
  alt (≤ 10 dp) 0,1 % / 14,4 %; vorgeschlagen (< 4 dp und < 180 ms) 12,6 % / 0,0 %; gewählt
  (< 4 dp, Dauer egal) 7,1 % / 0,0 %; zum Vergleich < 6 dp 2,2 % / 3,0 %. Die Dauer wird nicht
  verwendet (P111): Eine still gehaltene Berührung hat keine Form, als Strich wäre sie ein
  verirrter Punkt und der Buchstabe fehlte. Die 7,1 % hängen ganz am angenommenen Anteil gerollter
  Tipps; am Telefon zu prüfen, ob 4 dp Buchstaben kostet (dann 6 dp).
- Geprüft (JVM, `tools/dev.py core-tests` 290/0): `InkBenchmarkTest` 3 (hart ≥ 95 %, je Symbol
  ≥ 70 %, Paare ≤ 8 %, falsch ≤ 3 %; mild ≥ 98 %; ⇒ des Owners ≥ 90 % und ≤ 2 % ≠),
  `InkTimingTest` 4 (Grenze 4 dp, Raten der Regeln, Pause 900 → 2,5 × Median, 600/1500, ein
  Ausreißer verschiebt nichts), `PersonalInkSymbolsTest` 5 (5 je Symbol, neueste bleiben,
  80 Symbole, Format hin und zurück, eigene Symbole lesen keine 5er-Zeile, Textregeln, eine
  persönliche Vorlage gewinnt), `InkRecognizerTest` 15 (neu: Richtung ohne Vorzeichen, Punkte ohne
  Richtung, Strich- und Punktzahl; der Zeit-Test nimmt jetzt 20 verschiedene Zeichnungen, weil
  dieselbe seit 31 aus dem Merker kam und 0,1 ms maß), `InkDotsTest` unverändert grün.
- Geprüft (Robolectric): `SymbolInkTest` 28 (neu: 6-px-Strich ist Strich, Strich ohne
  Bewegungen, Strich nach 800 ms gehört dazu, Pause nach Tempo 600 ms, Buchstaben setzen die Pause
  nicht, still gehaltener Tipp ist Buchstabe, Strich ab der Punkt-Taste und Tipp darauf, Vorschau
  nach jedem Strich und leer beim Abbruch, Abbruch an anderem Tracker ohne Geisterbuchstabe);
  `InputLogicTest.symbolDraw*` 19 (neu: Vorschau-Chip „✎ –“ → „✎ →“, Tipp schreibt sofort und nur
  einmal; Chip während eines Strichs tut nichts, Verwerfen nimmt ihn weg; Alternative der Vorschau
  wird geschrieben, gelernt nur mit Schalter, danach erkennt die Tastatur die Zeichnung als sie;
  Alternative nach dem Schreiben gelernt, zweite Wahl verschiebt, Rückwahl nimmt zurück; kein
  Lernen bei Inkognito, Lernpause, Sperre; persönliche Vorlage in der Tastatur);
  `SymbolSettingsScreenTest` 11 (neu: Lernschalter Standard aus und gespeichert; Liste mit Anzahl,
  einzeln und alle löschen; Kalibrieren: zwei Zeichnungen, Überspringen, Fertig, sofort
  gespeichert).
- Angepasste Tests (alte Regeln, ehrlich offengelegt): Alle Wartezeiten von 600 ms in
  `SymbolInkTest` und `InputLogicTest` warten jetzt die längste Pause plus 100 ms
  (`SymbolInk.MAX_PAUSE_MILLIS`); sie kodierten die 500 ms. `aTapThatSlidesALittleTypesTheKeyWhereItTouchedDown`
  rutscht jetzt 3 px statt 8 px (8 px sind nach der neuen Regel ein Strich); Aussage und
  Assertions gleich. `symbolDrawTapInThePauseThatIsNoDotMatchesTheDrawingOnce` zählt jetzt ab vor
  dem Malen und erwartet 2 (eine Erkennung je Strich für die Vorschau; Punkt-Check und Schreiben
  fügen keine hinzu), vorher 1 ab nach dem Malen. Keine Assertion entfernt.
- Gegenproben: (a) in einem Lauf zugleich alte Tipp-Grenze 10 dp, feste Pause 500 ms, Folgestriche
  nur auf Buchstaben, alter `onCancelEvent`, Lernen auch bei Inkognito: rot genau
  `aStrokeMayStartOnThePeriodKeyAndATapThereTypesIt`, `thePauseFollowsThePaceOfTheStrokes`,
  `aCancelThatReachesAnotherTrackerEndsTheInkStroke`, `aStrokeAfterMoreThanHalfASecondStillBelongsToTheSymbol`,
  `symbolDrawLearnsNothingInIncognitoWhilePausedOrLocked`; `aShortStrokeInThePauseIsAStrokeNotATap`
  blieb grün, weil der Strich in der ÷-Punktzone lag; verschoben, danach mit nur der 10-dp-Grenze
  rot. (b) Lücke schon beim Aufsetzen gezählt: rot `lettersTypedInThePauseDoNotSetThePace`.
  (c) Richtungs-, Strich- und Punktgewicht 0: rot `fingerDrawingsHard` (92,4 %, 153 falsch),
  `theOwnersDoubleArrowIsNoNotEquals` (168 ⇒, 1 ≠), `scribblesAreMostlyRejected` (295/300). Danach
  alles wiederhergestellt und grün.
- Fallstrick P113 entstand hier: ein zusätzliches ÷ mit Punkten bei 25/75 machte einen Tipp genau
  auf den Strich zum Punkt (`InkDotsTest.tapsAwayFromTheDotsOfADashAreLetters` rot); entfernt.
- Gesamtläufe: `tools/dev.py core-tests` 290/0, `tools/dev.py agent-tests` 737/0 (auch der
  früher wackelige `SpaceGesturesTest` grün).
  Nicht gelaufen: `upstream-tests`, `lint`, Build (geteilte Maschine, macht der Lead).
- Nicht geprüft: kein Gerätetest (echte Finger am Xiaomi: Tipp-Grenze, Pause und Tempo, Vorschau
  beim Malen, Erkennung, ⇒/≠, Kalibrieren im Malfeld), keine echten Zeichnungen des Owners im
  Benchmark; kein Prüfer-Review der `PointerTracker`-Änderung (Pflicht). Annahme: „Aus meinen
  Korrekturen lernen“ ist opt-in (Regel in `CLAUDE.md`), der Owner muss es einschalten.
- Prüfer und Korrekturen (Pflicht-Prüfer zu `f4bc806`, ein hoher, vier mittlere, mehrere
  niedrige Befunde; korrigiert in der Arbeitskopie, nicht gepusht):
  - H1 Normales Tippen im Wartefenster verlor Zeichen, jetzt auch Ziffern und Satzzeichen (jede
    Zeichentaste begann einen Folgestrich, Tipp nur unter 4 dp, Fenster 0,9–1,5 s; „≈“, dann „3“
    mit rollendem Finger: 3 weg, Symbol verändert). Jetzt beginnt ein Folgestrich nur nahe der
    Zeichnung (`InkTap.nearDrawing`: Rechteck plus 0,6 ihrer längeren Seite, mindestens eine
    Tastenbreite); weiter weg wird das Symbol geschrieben und die Taste normal getippt. Tipp-Grenze
    6 dp statt 4 (P101: rollende Finger bewegen sich mehr als 4 dp). Modell `InkTimingTest`: 2,2 %
    falsche Striche, 3,0 % falsche Tipps (4 dp: 7,1 % / 0 %); die Grenze im Test ist jetzt ≤ 3 %
    falsche Striche statt < 12 % und ≤ 4 % falsche Tipps. Tests
    `SymbolInkTest.digitsAndPunctuationTypedAwayFromTheDrawingAreTyped` (3 mit 7 px Rollen, „.“ mit
    7 px, beide nach dem Symbol), `aShortStrokeNearTheDrawingStillJoinsAndARollingTapThereIsALetter`,
    `InkTimingTest.furtherStrokesStartOnlyNearTheDrawing`. `aShortStrokeInThePauseIsAStrokeNotATap`
    zieht jetzt 8 px statt 6 px (6 px sind mit „unter 6 dp“ gerade kein Tipp mehr; die Aussage bleibt).
  - M1 Zweiter Finger: Ob der erste Finger ein Tipp war, entscheidet wieder die großzügige 10-dp-Grenze
    (`InkTap.SECOND_FINGER_SLOP_DP`); war er ein Strich, wird die Zeichnung samt Strich geschrieben
    statt verworfen, sein Loslassen bleibt still. Tests `aSecondThumbAfterASlightlyMovedTapStillTypesBoth`
    (7 px), `secondFingerDuringAStrokeWritesTheDrawingAndIsHandledAsUsual` (vorher
    `secondFingerDropsTheDrawingAndIsHandledAsUsual`, das das Verwerfen festschrieb: jetzt „Symbol“,
    dann der Buchstabe des zweiten Fingers, die Zeichnung mit 2 Strichen; die übrigen Assertions
    gleich). `InputLogicTest.symbolDrawPreviewChipDoesNothingWhileAStrokeIsDrawnAndGoesWithTheDrawing`
    verwirft die Zeichnung jetzt über einen Abbruch statt über einen zweiten Finger.
  - M2 Hauptthread: Die Vorschau kommt erst 120 ms nach dem letzten Strich und wird auf einem eigenen
    Thread erkannt; das Ergebnis gilt nur ohne Zustandswechsel dazwischen. Persönliche Vorlagen
    höchstens 3 je Symbol und 40 Symbole (vorher 5 und 80). Zeit-Test mit allen Vorlagen, die ein
    Nutzer haben kann (105 eingebaute + 90 eigene + 120 persönliche = 315): 13,5 ms je Zeichnung auf
    dem JVM, Grenze 40 ms (`recognitionWithEveryTemplateTheUserCanHaveFitsTheMainThread`); auf dem
    Telefon nicht gemessen. Der Zähler-Test `symbolDrawTapInThePauseThatIsNoDotMatchesTheDrawingOnce`
    erwartet wieder 1 (zwei schnelle Striche: noch keine Vorschau; Punkt-Check und Schreiben teilen
    eine Erkennung). Vorschau-Tests warten 130 ms. Test `strokesInQuickSuccessionGetOnePreview`.
  - M3 Lernen und Kalibrieren behalten eine Zeichnung mit Punkt nur für ein Symbol mit Punkten
    (P113). Tests `PersonalInkSymbolsTest.aDrawingWithADotIsKeptOnlyForASymbolWithDots`,
    `InputLogicTest.symbolDrawLearnsNoDrawingWithADotForASymbolWithoutDots`.
  - M4 Aussagen in FEATURES, VALIDATION und Release-Notes abgeschwächt (oben: geteilte Malarten
    aufgezählt, 96 % innerhalb der Verteilung, ⇒/≠ nie nachgestellt; „⇒ und ≠ sind getrennt“
    gestrichen).
  - N1 ARCHITECTURE: Schritt 2 der Punkt-Regel nennt die aktuelle Schwelle, P96/P104 die aktuellen
    Grenzen. N3 Ein Tipp auf den Vorschau-Chip bei gesperrter Eingabe lässt die Zeichnung stehen
    (Test `symbolDrawPreviewChipKeepsTheDrawingWhileInputIsBlocked`); das Umschalten der ausklappbaren
    Werkzeugleiste ist unverändert (wie bei allen Chips). N4 Zurücknehmen stellt die persönlichen
    Vorlagen von vor dem Lernen wieder her, auch eine verdrängte (`Inserted.learnedOn`, Test
    `symbolDrawUndoingALearnedDrawingRestoresTheOneItPushedOut`); „Gelernte Wörter“ zeigt ihre Zahl
    und „Gelernte Wörter löschen“ löscht sie mit, ebenso das Ausschalten von „Personalisierte
    Vorschläge“ (neue Upstream-Änderung `TextCorrectionScreen.kt`, eine Zeile). Tests
    `SymbolSettingsScreenTest.theLearnedDataPageCountsAndDeletesPersonalTemplates`,
    `turningPersonalizedSuggestionsOffDeletesPersonalTemplates`.
  - `PointerTracker`: `startsInk` prüft zuerst `SymbolInk.isWaiting`, zuletzt
    `continuesInk(x, y, mMostCommonKeyWidth)`; der Zweig des zweiten Fingers ruft `onSecondFinger`
    nur für einen zeichnenden Tracker und macht ihn nur bei einem Tipp zur Taste. `LatinIME`
    unverändert.
  - Gegenproben (in einem Lauf zugleich: kein Bereich, 4 dp, zweiter Finger mit 6 dp und Verwerfen,
    Punkt-Zeichnungen für jedes Symbol, kein Sperr-Check am Chip, Lernen ohne gemerkten Stand, keine
    Lösch-Zeilen): rot genau `aShortStrokeNearTheDrawing…`, `digitsAndPunctuation…`,
    `aSecondThumbAfterASlightlyMovedTap…`, `secondFingerDuringAStroke…`, beide neuen
    `SymbolSettingsScreenTest`, `symbolDrawUndoingALearnedDrawing…`, `symbolDrawLearnsNoDrawingWithADot…`,
    `symbolDrawPreviewChipKeepsTheDrawing…` und `symbolDrawAlternativeAfterWritingIsLearnedAndMovesWithASecondPick`
    (das nachgebaute alte Zurücknehmen). Die Vorschau sofort nach jedem Strich: zuerst blieb
    `strokesInQuickSuccessionGetOnePreview` grün, weil der Test zwischen den Strichen den Hauptthread
    nicht laufen ließ; nach 60 ms Leerlauf zwischen den Strichen rot. Danach alles wiederhergestellt.
  - Läufe: `tools/dev.py core-tests` 300/0; `SymbolInkTest` 32, `SymbolSettingsScreenTest` 13,
    `InputLogicTest.symbolDraw*` 22, alle grün. `tools/dev.py agent-tests`: erster Lauf 758 Tests,
    1 Fehler: `theLearnedDataPageCountsAndDeletesPersonalTemplates` fand die Zeile „0 Zeichnungen“
    nur im ungemergten Baum (allein grün); der Test sucht sie jetzt dort, nachdem er auf das Löschen
    gewartet hat (die Prüfung der Daten selbst ist unverändert). Zweiter Lauf 758/0. Nicht gelaufen:
    `upstream-tests`, `lint`, Build.
- Nachprüfer zu `efb3939` und Korrekturen (in der Arbeitskopie, nicht gepusht):
  - F1 Veralteter Vorschau-Chip: Der Chip für Strich N, nach Strich N+1 angetippt, schrieb die alte
    Vermutung für alle Striche und lernte sie (ein Pfeil als „–“). Jetzt trägt `Waiting` die Zahl der
    Striche; `SymbolInk` meldet jeden neuen Strich (`Sink.onStrokeStarted`, der Chip wird veraltet,
    eine laufende Vorschau verworfen). Ein veralteter Chip schreibt den besten Treffer aller Striche
    und lernt nichts. Test `symbolDrawPreviewChipForFewerStrokesWritesTheWholeDrawingAndLearnsNothing`
    (über die Batch-Striche und über die `PointerTracker`-Hooks).
  - F2 Mehrteilige Symbole mit kleinem erstem Teil (% mit erstem Kreis, ≤/≥ mit dem Strich zuerst,
    Pfeilkopf zuerst) begannen beim nächsten Strich ein neues Symbol. Neuer Test `CT/InkAreaTest.kt`:
    alle eingebauten Zeichnungen in ihrer Reihenfolge und Richtung und 4800 Finger-Zeichnungen mit
    zufälliger Reihenfolge und Richtung, 2, 2,5 und 3 Reihen hoch (Taste 36 dp, Reihe 55 dp). Die
    eingebauten brauchen bis 2,58 Tastenbreiten; vom Finger-Modell brauchen mehr als 1,5 / 2 / 2,5 /
    3 / 4 Tastenbreiten 466 / 357 / 230 / 125 / 15 von 4800. Gewählt: mindestens 3 Tastenbreiten
    (`InkTap.KEY_MARGIN`), eingebaute 100 %, Finger-Modell 4675/4800 (97,4 %, Test ≥ 97 %); der Rest
    sind Zeichnungen, deren nächster Strich weit weg beginnt (vor allem Pfeilkopf zuerst, dann der
    Schaft vom anderen Ende, π, ≤). Dass jede Folgezeichnung angenommen wird, wie gewünscht, ginge
    erst mit etwa 4,6 Tastenbreiten, dann wäre fast die ganze Tastatur Malbereich und H1 hinfällig;
    darum nicht. Preis der 3 Tasten: Im Umkreis von 3 Tasten um die Zeichnung zählt ein Tipp nur
    unter 8 dp als Tipp (F3).
  - F3 Tipp-Grenze 8 dp statt 6 (Androids Touch-Slop). Modell `InkTimingTest`: 0,5 % falsche Striche
    (Tipps als Strich), 8,7 % falsche Tipps (kurze Striche 5–40 dp als Tipp; 6 dp: 2,2 % / 3,0 %,
    10 dp: 0,1 % / 14,4 %). Die Test-Grenzen sind jetzt < 1 % falsche Striche und < 10 % falsche
    Tipps, und weniger falsche Tipps als mit 10 dp (vorher: ein Drittel davon, mit 8 dp nicht mehr
    erfüllt, ehrlich gelockert). Restrisiko in ANLEITUNG und Release-Notes: direkt neben der
    Zeichnung kann ein verwackelter Tipp noch als Strich zählen. `aShortStrokeInThePauseIsAStrokeNotATap`
    zieht 9 px (8 px sind jetzt gerade ein Strich), der rollende Tipp im Bereich rollt 7 px (unter 8,
    mit 6 dp ein Strich), der Ziffern-Test tippt 15 px weiter rechts (außerhalb der 3 Tasten).
  - F4 Zeit-Test umbenannt in `jvmTimingWithEveryTemplateTheUserCanHave` (misst auf dem JVM, nicht am
    Telefon).
  - F5 `InputLogicTest` setzt den direkten Vorschau-Executor nach jedem Test auf den echten Thread
    zurück (`@AfterTest`). Neuer Test über den echten Thread:
    `symbolDrawPreviewOnTheWorkerIsShownAndALateOneIsDropped` (Vorschau kommt; nach Verwerfen kein
    Chip; nach dem Schreiben bleiben die Alternativen, keine späte Vorschau). Unter gesperrter Eingabe
    verwirft `onWaiting` eine laufende Vorschau.
  - F7 Lernen nur mit „Personalisierte Vorschläge“ (dessen Ausschalten löscht die Vorlagen). Test
    `symbolDrawLearnsNothingWithoutPersonalizedSuggestions`; der Test-Helfer für das Lernen schaltet
    beides ein.
  - Gegenproben (in einem Lauf: 6 dp, 1,5 Tastenbreiten, alter Chip schreibt seine Vermutung,
    Vorschau ohne Zustands-Prüfung, Lernen ohne personalisierte Vorschläge): rot beide Bereichs-Tests
    in `InkAreaTest`, drei `InkTimingTest`-Fälle, `aShortStrokeNearTheDrawing…`, der F1- und der
    F7-Test. Der Worker-Test blieb dabei zuerst grün (der Chip-Aufbau verwarf die späte Vorschau
    schon); auf „Schreiben, dann späte Vorschau“ umgestellt, danach mit nur der fehlenden
    Zustands-Prüfung rot. Danach alles wiederhergestellt.
  - Flaky behoben: `theLearnedDataPageCountsAndDeletesPersonalTemplates` wartet jetzt, bis die Zahl
    nach dem Löschen (IO-Thread) wieder im Hauptthread steht, statt sofort zu prüfen; das war die
    Ursache des einen Fehlers im vorigen Gesamtlauf.
  - Läufe: `tools/dev.py core-tests` 303/0, `tools/dev.py agent-tests` 761/0; nach einer letzten
    Textänderung `SymbolInkTest` 32, `SymbolSettingsScreenTest` 13, `InputLogicTest.symbolDraw*` 25
    grün. `PointerTracker` und `LatinIME` in dieser Runde unverändert. Nicht gelaufen:
    `upstream-tests`, `lint`, Build. Kein Gerätetest.

## Etappe 38a (Einstellungen: Grundlage und Start)

- Auftrag (Lead, ROADMAP 38, beschlossen 1:1 nach `docs/referenzen/einstellungen-mockup/`):
  Baukasten, neue Startseite, sieben Bereichs-Routen mit vorläufiger Zuordnung der bisherigen
  Seiten nach `Struktur.dc.html`. Gebaut in einer Arbeitskopie, kein Prüfer-Review, kein Gerätetest.
- Gebaut: `AgentSettingsKit.kt` (Tokens, `SettingsArea`, `cssGradient`, `AgentSettingsTheme`,
  `BackHeader`, `AreaHero`, `SectionCard`, `FeatureCard`, `SettingRow`, `SettingSwitchRow`,
  `GradientSwitch`, `ChoiceCards`, `ChipToggles`, `AreaTile`, `FooterPill`), `AgentStartScreen.kt`,
  `AgentAreaScreen.kt` (`SettingsAreas`), `agent_settings_strings.xml` (Englisch, Deutsch).
  Upstream: `SettingsNavHost.kt` (Routen `area_*`, Startseite), `MainSettingsScreen.kt` (nur noch
  die neue Startseite). Entfernt: `AgentMainSettings.kt` (Look 23e nur mit Flat), neun nicht mehr
  benutzte Icons `ic_agent_settings_*` (das Update-Icon bleibt), `MainSettingsLookTest.kt` (prüfte
  die entfernte Liste). Entscheidung: Systemschrift statt Space Grotesk/Inter (keine Schriftdateien
  im APK). Abweichungen vom Mockup für 44 dp Mindestgröße: Fußzeilen-Pillen und „Ändern“ sind
  44 dp hoch (Mockup etwa 36 bzw. 41), die Befehls-Chips 44 statt 40 dp.
- Tests (Robolectric, kein Gerätetest): `SettingsStartScreenTest` 10 Tests (Kacheln im Raster
  und ihre Routen, exakte Texte, „Ändern“ → `area_look`, Schnellschalter schreiben die echten
  Einstellungen und sind Schalter für TalkBack, Look-Karte „Abgerundet · Licht: Höhenlinien“,
  „Updates · Build <VERSION_CODE>“, Suche findet „Vibration bei Tastendruck“, alles Anklickbare
  ≥ 44 dp, alle 22 Einträge der alten Startseite und jedes Navigationsziel auf einer Bereichsseite
  oder als Unterseite, jede Bereichsseite mit Held und klickbaren Zeilen, echter `SettingsNavHost`
  mit Bereich → Über → Zurück und alter Route über `navigateTo`, Bilder mit Pixelprüfung) und
  `SettingsKitTest` 4 Tests (Schalterzeile und eigener Schalter mit Role.Switch und Zustand,
  Auswahlkarten als Radio-Buttons, Chips, Bild mit weißem Rahmen nur um die gewählte Karte und
  55 % Deckkraft der anderen). `DiagnoseScreenTest.theSettingsListLeadsToThePage` ehrlich angepasst:
  Pille „Diagnose“ der Startseite → Route `agent_diagnose`.
- `python3 tools/dev.py agent-tests`: grün, 772 Tests in 86 Klassen, 0 Fehler (2026-11-13).
  `python3 tools/dev.py build`: grün (Debug-APK lokal). `docs_sync --check`: grün.
- Bilder (nicht versioniert): `app/build/agent-previews/settings-start.png`,
  `settings-area_{look,typing,keys,gestures,bar,ai,system}.png`, `settings-kit.png`; vom Lead mit
  `Main.dc.html` und `Gesten.dc.html` verglichen: Farben, Radien, Raster, Abstände stimmen; die
  Systemschrift ist breiter als Inter, deshalb brechen die Schnellschalter wie im Mockup in zwei
  Zeilen und die Halb-Kacheln umbrechen ihren Titel.
- Offen: Prüfer-Review, Gerätetest (Aussehen am Telefon, TalkBack, Suche, Zurück), 38b–38h.

## Etappe 38c (Einstellungen: Tippen & Korrektur)

- Auftrag (Lead, ROADMAP 38c): Bereichsseite „Tippen & Korrektur“ 1:1 nach `Tippen.dc.html` mit
  dem Baukasten aus 38a, in eigener Datei; Stufen AUS/BEHUTSAM/MUTIG auf vorhandene Einstellungen.
  Gebaut in einer Arbeitskopie parallel zu anderen Bereichen, kein Prüfer-Review, kein Gerätetest.
- Gebaut: `AgentAreaTypingScreen.kt` (`TypingAutocorrect`, Seite, `WordCounts`),
  `agent_typing_strings.xml` (Englisch, Deutsch), eine Zeile in `AgentAreaScreen.kt`.
  Zuordnung der Stufen: ARCHITECTURE.md → Einstellungen und Profile (Annahme des Leads).
  Abweichungen vom Mockup: zusätzliche Karte „Alle Korrektur-Einstellungen“ am Ende (Rest der
  alten Textkorrektur, auch „Personalisierte Vorschläge“ mit Löschwarnung); „Kontakte und
  App-Namen“ ist eine Zeile zur Korrekturseite (Berechtigung), ihr Wert nennt, was an ist;
  ausgegraute Zeilen zeigen statt des Untertitels den Grund; „Eigene Einstellung“ unter den
  Karten, wenn keine Stufe passt (im Mockup ist immer eine gewählt). Ohne Inhaltsanbieter (Tests)
  zeigt „Persönliches Wörterbuch“ keine Zahl.
- Tests (Robolectric, kein Gerätetest): `SettingsTypingAreaTest` 9 Tests (Zuordnung in beide
  Richtungen samt Bandgrenzen 0,40/0,41/0,80/0,81 und behaltener Stärke; Tipp auf eine Karte
  schreibt die Einstellungen und wählt sie, ein Regel-Schalter macht daraus „Eigene Einstellung“;
  von außen gesetzte Einstellungen wählen MUTIG; 13 Schalter schreiben ihren Schlüssel und zeigen
  den Zustand, Pause über `LearningData`, `use_personalized_dicts` bleibt unberührt; ausgegraute
  Schalter ohne Autokorrektur, ohne Vorschlagsleiste und mit Doppeltipp-Geste; Zeilen öffnen
  ihre Seiten mit echten Werten (12 Wörter über einen Test-Inhaltsanbieter, Wörterbücher wie
  `WordCounts`, „Apps“); alle Texte des Mockups; alles Anklickbare ≥ 44 dp; Bild mit Pixelprüfung).
  `SettingsStartScreenTest` 10 Tests grün (P119: die Routen von `SettingsAreas.entries(TYPING)`
  sind Zeilen der neuen Seite; Bild dieses Bereichs jetzt `settings-area_typing-top.png`).
- `python3 tools/dev.py agent-tests`: grün, 781 Tests in 87 Klassen, 0 Fehler (2026-11-14). Kein Build (geteilte Maschine).
  `docs_sync --check`: grün.
- Bild (nicht versioniert): `app/build/agent-previews/settings-area_typing.png` (ganze Seite,
  390 × 2400 dp), mit `Tippen.dc.html` verglichen: Held, Karten, Reihenfolge, Texte, Punkte und
  Schalterfarben stimmen; gewählt ist BEHUTSAM wie im Mockup. Abweichend: die Schalterzustände
  sind die echten Standardwerte (Mockup: Beispielwerte), Systemschrift (38a).
- Offen: Prüfer-Review, Gerätetest (Stufen am Telefon, TalkBack, Emoji-Hinweis ohne Wörterbuch).
## Etappe 38e (Einstellungen: Gesten & Gefühl)

- Auftrag (Lead, ROADMAP 38, 1:1 nach `docs/referenzen/einstellungen-mockup/Gesten.dc.html`): die
  Bereichsseite 04 mit dem Baukasten aus 38a, echte Werte, bisherige Dialoge. Gebaut in einer
  Arbeitskopie, kein Prüfer-Review, kein Gerätetest.
- Gebaut: `AgentAreaGesturesScreen.kt` (Held, Karte „Leertaste“ mit `SpaceDetentBar` und sechs
  Gesten-Knöpfen, Auswahlkarten „Über die Buchstaben wischen“, Abschnitte „Symbole malen“,
  „Vibration & Ton“, „Treffen & Wischen“, `GestureSlider`), `agent_gestures_strings.xml` (Englisch,
  Deutsch). Klein geändert: `AgentAreaScreen.kt` (eine Zeile Weiche, `SettingsAreas` für Gesten
  nur noch Symbole malen und Gestenschreiben), `AgentSettingsKit.kt` (`Choice.enabled`, gesperrte
  Karte 35 % und nicht wählbar), `AgentSpaceScreen.kt` (`titleResource`/`labelResource` `internal`),
  `AgentLearningScreen.kt` (`CalibrationDialog` `internal`).
- Zuordnung: Nichts = `agent_symbol_drawing` und `gesture_input` aus; Symbole malen =
  `agent_symbol_drawing` an (`gesture_input` bleibt gespeichert); Gestenschreiben = `gesture_input`
  an, Symbole aus, nur mit Bibliothek. Gesten-Werte über `SpaceGestures.resolve`, die Liste wie auf
  der Leertasten-Seite. Regler mit Schlüssel, Standard, Bereich, Schritten und Live-Ton/-Vibration
  der alten Zeilen.
- Abweichungen vom Mockup: Werte sind echt (z. B. „Spur anzeigen“ 0,9 s statt 0,6 s, Doppeltipp
  „Punkt + Leer“ nur bei „Punkt plus Leerzeichen“), Vibrationsdauer und „Bitte nicht stören“ nur
  bei eingeschalteter Vibration, „Lautstärke“ als eigene Zeile bei eingeschaltetem Ton,
  Touchpad-Rand und Wischweite für Sprachwechsel nur bei belegter Aktion (wie auf den alten
  Seiten), Zeile „Gestenschreiben“ unter den Karten nur mit Bibliothek, „Kalibrieren“ (Symbole)
  öffnet die Seite „Symbole malen“, nicht direkt die Kalibrierung. Langdruck-Dauer bleibt bei
  Tasten & Sprachen (38d). Systemschrift wie in 38a.
- Tests (Robolectric, kein Gerätetest): `SettingsAreaGesturesTest` 11 Tests (Aufbau und Texte des
  Mockups, Gesten im 2er-Raster mit echten Aktionen, Liste öffnet, wählt, „Standard“, Doppeltipp
  „Punkt + Leer“/„Nichts“, Auswahlkarten auf beiden Schaltern mit und ohne Bibliothek, Schalter
  schreiben die Einstellungen und zeigen den neuen Zustand, Reglerwerte und alte Dialoge mit
  „Standard“, bedingte Zeilen, Zeilen zu Symbole malen und Trefferzonen-Kalibrierung, alles
  Anklickbare ≥ 44 dp, Bild mit Pixelprüfung von Held, Leiste, Punkten und Leuchten).
  `SettingsStartScreenTest`: `agent_space` als Unterseite (P119), Gesten-Bereich prüft genau seine
  Zeilen statt „mindestens drei“. Alle 25 Tests von `Settings*` grün.
- `python3 tools/dev.py agent-tests`: grün, 681 Tests in 84 Klassen laut Ergebnisdateien, 0 Fehler
  (2026-11-14). Kein Build, keine Upstream-Suite (geteilte Maschine). `docs_sync --check`: grün.
- Bilder (nicht versioniert): `app/build/agent-previews/settings-area_gestures-full.png` (ganze
  Seite, 390 × 2200 dp) und `settings-area_gestures.png` (oberer Teil aus
  `SettingsStartScreenTest`); mit `Gesten.dc.html` verglichen: Reihenfolge, Farben, Radien, Raster
  und Punkte stimmen; wie im Mockup liegt der 42-%-Punkt am Rand von „Deutsch“.
- Offen: Prüfer-Review, Gerätetest (Dialoge, Vibration/Ton, TalkBack, Bibliothek geladen).
## Etappe 38d (Einstellungen: Tasten & Sprachen)

- Auftrag (Lead, ROADMAP 38): Bereich 03 1:1 nach `Tasten.dc.html` mit dem Baukasten aus 38a, in
  eigener Datei, alles bisher Erreichbare bleibt erreichbar. Gebaut in einer Arbeitskopie, kein
  Prüfer-Review, kein Gerätetest.
- Gebaut: `AgentAreaKeysScreen.kt`, `agent_settings_keys_strings.xml` (Englisch, Deutsch), eine
  Zeile in `AgentAreaScreen.kt`. Abweichungen vom Mockup: Sprachkarten sind Knöpfe zur Seite der
  Sprache (keine Auswahl), hell und gerahmt ist die aktive Sprache; zusätzliche Zeilen, damit die
  alten Einträge erreichbar bleiben: Verhalten der Sprachwechseltaste, die bedingten
  Zahlenzeilen-Schalter, Emoji-Beschreibung, Hinweis-Quelle (nur mit Tasten-Hinweisen),
  funktionale Hinweise, TLD-Popups, mehr diakritische Zeichen, die vier Rückkehr-Schalter
  (aufklappend unter „Zurück zu den Buchstaben“, Wert „n von 4“) und „Weitere Optionen“ (alte
  Seite Optionen wegen Zwischenablage, Vibration, Ton bis 38e/38f). Untertitel „Reihenfolge der
  Popups“ und „Hinweis-Quelle“ = die eingeschalteten Quellen; Währungen zeigen „Standard“ ohne
  eigene Liste; lange Werte stehen im Untertitel (P129).
- Tests (Robolectric, kein Gerätetest): `SettingsKeysScreenTest` 9 Tests (Texte und
  Abschnittsfolge des Mockups, keine Karte „Seiten“; Karten aus den echten Subtypen Deutsch,
  English (US), Español mit Layout und Wörterbüchern, Role.Button, „aktiv“ nur an der gewählten,
  Tipp → `subtype/…`, Seite bleibt deutsch (P128); acht Schalter schreiben die echten Schlüssel,
  bedingte Zeilen wie auf der alten Seite; Rückkehr-Schalter mit „n von 4“; Dialoge
  Sprachwechseltaste, Währungen, Zeitstempel, Langdruck, Reihenfolge, Hinweis-Quelle, mehr
  Popup-Buchstaben schreiben ihre Werte; alle alten Optionen-/Erweitert-Einträge des Bereichs als
  Zeile vorhanden, Seiten Sprachen, Optionen, Sekundäre Layouts öffnen ihre Route; alles
  Anklickbare ≥ 44 dp; echter `SettingsNavHost` Kachel 03 → Sprachkarte → Sprachseite → Zurück;
  Bild mit Pixelprüfung). `SettingsStartScreenTest` (P119, Bereichsseiten) und `SettingsKitTest`
  unverändert grün.
- `python3 tools/dev.py agent-tests`: grün, 781 Tests in 87 Klassen, 0 Fehler (2026-11-14). Kein
  Build, keine volle Upstream-Suite (geteilte Maschine). `docs_sync --check`: grün.
- Bilder (nicht versioniert): `app/build/agent-previews/settings-area_keys.png` (aus
  `SettingsStartScreenTest`, 1180 dp hoch) und `settings-area_keys-full.png` (ganze Seite); mit
  `Tasten.dc.html` verglichen: Held, Karte SPRACHEN mit drei Verlaufskarten, Leucht-Pillen,
  weißem Rahmen, Abschnitte, Punkte, Schalter und Werte („+“, „300 ms“, „1 von 4“) stimmen;
  Systemschrift breiter als Inter, einige Titel brechen um.
- Offen: Prüfer-Review, Gerätetest (Aussehen, TalkBack, Dialoge im dunklen Schema).

## Etappe 38f (Einstellungen: Leiste & Befehle)

- Auftrag (Lead, ROADMAP 38f): Bereichsseite „Leiste & Befehle“ 1:1 nach `Leiste.dc.html` mit
  dem Baukasten aus 38a, in eigener Datei; „Symbolleiste“/„Werkzeugleiste“ → „Leiste“ in den
  deutschen Texten. Gebaut in einer Arbeitskopie parallel zu anderen Bereichen, kein
  Prüfer-Review, kein Gerätetest.
- Gebaut: `AgentAreaBarScreen.kt` (`BarModes`, Seite, Befehls-Karte, Skill-Dialog),
  `agent_bar_strings.xml` (Englisch, Deutsch), eine Zeile in `AgentAreaScreen.kt`; Umbenennung in
  `values-de/strings.xml` (25 HeliBoard-Texte, nur Wortlaut), `values-de/agent_strings.xml`
  (Skill-Seite, Diktat, flacher Look) und `values-de/agent_settings_strings.xml` (Kachel
  „Leiste, @-Befehle, Zwischenablage“; das Mockup der Startseite sagt dort noch „Symbolleiste“).
  Zuordnung der Karten: ARCHITECTURE.md → Einstellungen und Profile (Annahme des Leads).
  Abweichungen vom Mockup: Hauptschalter „Getippte Befehle erkennen“ im Kopf der Karte
  „@-Befehle“ (im Mockup fehlt er), bei aus sind die Chips gesperrt und der Text darunter sagt es;
  eine Zeile „… braucht Zugriff auf Kontakte“ unter den Chips, solange `@contact` an ist und die
  Berechtigung fehlt; „Eigene Einstellung“ unter den Karten bei „Nur Leisten-Tasten“;
  ausgegraute Zeilen zeigen den Grund; „Knöpfe wählen und sortieren“ öffnet die bisherige
  Leisten-Seite (dort die Dialoge für Tasten, angeheftete Tasten, Zwischenablage-Tasten, Keycodes),
  „Skill-Tasten anheften“ einen eigenen Dialog mit drei Schaltern; zusätzliche Karte am Ende mit
  „Alle Befehls-Einstellungen“, „Screenshots“ (Zugriff erlaubt/fehlt) und „Weitere
  Zwischenablage-Einstellungen“ (Optionen).
- Tests (Robolectric, kein Gerätetest): `SettingsBarAreaTest` 11 Tests (Zuordnung in beide
  Richtungen samt `TOOLBAR_KEYS` = keine Karte; Tipp auf eine Karte schreibt `toolbar_mode` und
  wählt sie; „Eigene Einstellung“ verschwindet nach einer Wahl; 20 Befehls-Chips als Schalter mit
  echtem Zustand, Tipp schreibt `agent_commands_disabled`, von außen gesetzter Wert, Hauptschalter
  sperrt die Chips und behält ihren Zustand; vier Schalter, „Bilder und Dateien“ und Screenshots
  schreiben ihren Schlüssel; ausgegraute Zeilen je Modus und ohne Verlauf; „2 von 3“ und „60 min“
  aus echten Werten, Dialog heftet Skill-Taste 2 an und 3 ab, Aufbewahrung „Standard“ entfernt den
  Schlüssel; die Routen von `SettingsAreas.entries(BAR)` plus Optionen sind Zeilen (P119); alle
  Texte des Mockups und die 20 Befehle in seiner Reihenfolge; alles Anklickbare ≥ 44 dp; Bild mit
  Pixelprüfung). `SettingsStartScreenTest` 10 Tests grün (Kacheltext „Leiste, @-Befehle,
  Zwischenablage“; die Leisten-Zeile heißt „Knöpfe wählen und sortieren“ und die alte Seite
  „Leiste“; Bild dieses Bereichs jetzt `settings-area_bar-top.png`). `SettingsTypingAreaTest`
  9 Tests grün.
- `python3 tools/dev.py agent-tests` (2026-11-14): 792 Tests, 1 Fehler, `RgbSettingsScreenTest.thePreviewLightIsSmoothLikeTheGrid` (`ArrayIndexOutOfBounds` in
  `RgbTexture.gridPixel`, leeres Raster; berührt 38f nicht). Die Klasse allein danach: 13 Tests
  grün. Vermutlich reihenfolge- oder lastabhängig auf der geteilten Maschine, nicht geklärt. Kein Build (geteilte Maschine).
  `docs_sync --check`: grün.
- Bild (nicht versioniert): `app/build/agent-previews/settings-area_bar.png` (ganze Seite,
  390 × 2400 dp), mit `Leiste.dc.html` verglichen: Held, Karten, Chips, Reihenfolge, Texte, Punkte
  und Schalterfarben stimmen; gewählt ist TASTEN + VORSCHLÄGE wie im Mockup. Abweichend: echte
  Werte (z. B. „0 von 3“ statt „2 von 3“), Schalter im Kopf der Befehls-Karte, Kontakte-Zeile
  (Robolectric ohne Berechtigung), Karte am Ende, Systemschrift (38a).
- Offen: Prüfer-Review, Gerätetest (Karten am Telefon, Berechtigungsdialoge für Kontakte und
  Bilder, TalkBack, Skill-Dialog).

## Etappe 38g (Einstellungen: KI & Automationen)

- Auftrag (Lead, ROADMAP 38, 1:1 nach `docs/referenzen/einstellungen-mockup/KI.dc.html`): die
  Bereichsseite 06 mit dem Baukasten aus 38a, echte Werte, bisherige Seiten für Schlüssel,
  Skill-Belegung, Workflows und Diktat. Gebaut in einer Arbeitskopie, kein Prüfer-Review, kein
  Gerätetest.
- Gebaut: `AgentAreaAiScreen.kt` (Held, Auswahlkarten „Anbieter“ mit Schlüsselstatus und „Ändern“,
  Karte „Skill-Tasten“ mit drei Kacheln, Abschnitte „Texte“, „Workflows“, „Diktat & Verlauf“;
  `FLAG_SECURE`, gesperrter Zustand), `agent_ai_area_strings.xml` (Englisch, Deutsch). Klein
  geändert: `AgentAreaScreen.kt` (eine Zeile Weiche, Kommentar an `SettingsAreas` für KI).
- Zuordnung: Karten in der Reihenfolge `CloudProviders.SUPPORTED` (OpenAI, Anthropic = `CLAUDE`,
  Google Gemini) ↔ `ModelSettings.provider`/`setProvider`; Hinweis = `ModelSettings.model(…)`,
  leer (Gemini ohne eigenes Modell) „Modell-ID selbst eintragen“. Schlüsselstatus nur über
  `EncryptedCredentialStore.contains` des Platzes des gewählten Anbieters. Kachel = `label`, leer
  der Name der Aktion („Nicht belegt“). Diktat: Erkenner fehlt → „Nicht verfügbar“, Mikrofonrecht
  fehlt → „Erlauben“, sonst „Bereit“.
- Abweichungen vom Mockup: Hinweise der Anbieterkarten zeigen das echte Modell („Modell:
  gpt-6-luna“ statt „Modell: eigenes oder Vorgabe“, „Modell: claude-haiku-5-5“ statt
  „Claude-Modelle“); „Ändern“ 44 dp hoch statt 40 (P120); Statuszeile hat zusätzlich „Kein
  Schlüssel gespeichert“, „wird geprüft“ und „nicht verfügbar“; „Neuer Workflow“ und „Import &
  Export“ öffnen die Workflow-Seite (dort Anlegen, Builder, Import/Export), „Auftragsstatus“ die
  Seite KI-Anbieter (dort die Liste). Gesperrt: Hinweis plus Karte „Seiten“ statt der Inhalte.
  Systemschrift wie in 38a.
- Tests (Robolectric, kein Gerätetest): `SettingsAreaAiTest` 11 Tests (Aufbau, Texte und
  Reihenfolge des Mockups, Kacheln nebeneinander, `FLAG_SECURE`; Anbieter in beide Richtungen
  inkl. Modell-Hinweisen, Modelle bleiben unverändert; Schlüsselstatus je Anbieter mit echtem,
  verschlüsseltem Testschlüssel, der in keinem Text, keiner Beschreibung erscheint und unverändert
  bleibt, „Ändern“ → `agent_ai`; gesperrtes Gerät ohne Anbieter, Status und Kacheln, die vier
  Seiten erreichbar; Kacheln mit echten Namen inkl. Workflow und „Nicht belegt“; Zielsprache und
  Ton lesen und über die Liste schreiben, Workflow-Anzahl, Zeilen öffnen ihre Seiten; Diktat
  „Nicht verfügbar“/„Erlauben“/„Bereit“; alles Anklickbare ≥ 44 dp; Bild mit Pixelprüfung von
  Held, Kachelfarben, gewählter und gedämpfter Anbieterkarte). `SettingsStartScreenTest`
  unverändert grün (P119: die vier Routen bleiben in `SettingsAreas.entries(AI)` und tragen auf der
  neuen Seite je einen `AREA_ROW_TAG`, P135).
- `python3 tools/dev.py agent-tests`: grün, 803 Tests in 89 Klassen laut Ergebnisdateien, 0 Fehler
  (2026-11-14, darin `SettingsAreaAiTest` 11 und `SettingsStartScreenTest`). Kein Build, keine
  Upstream-Suite (geteilte Maschine). `docs_sync --check`: grün.
- Bild (nicht versioniert): `app/build/agent-previews/settings-area_ai-full.png` (ganze Seite,
  390 × 2200 dp); mit `KI.dc.html` verglichen: Reihenfolge, Farben der Karten und Kacheln, Radien,
  Schlüsselzeile und Abschnitte stimmen; wie im Mockup liegt die Leucht-Pille der dritten Karte
  am Ende von „GOOGLE GEMINI“.
- Offen: Prüfer-Review, Gerätetest (FLAG_SECURE, Sperre und Entsperren mit offener Seite,
  echter Schlüssel, Diktat mit On-device-Erkenner, TalkBack).
## Etappe 38h (Einstellungen: System & Daten)

- Auftrag (Lead, ROADMAP 38): Bereich 07 1:1 nach `System.dc.html` mit dem Baukasten aus 38a, in
  eigener Datei, alles bisher Erreichbare bleibt erreichbar. Gebaut in einer Arbeitskopie, kein
  Prüfer-Review, kein Gerätetest.
- Gebaut: `AgentAreaSystemScreen.kt`, `agent_settings_system_strings.xml` (Englisch, Deutsch),
  eine Zeile in `AgentAreaScreen.kt`; `UpdateSettings.checkedAt` (geschrieben von `Updates.check`),
  `FlatPreset.isApplied`, `KeyboardProfileStore.matchesCurrent`, `updateErrorText` aus
  `AgentUpdatesScreen.kt` herausgezogen; Upstream: Parameter `row` in `BackupRestorePreference` und
  `LoadGestureLibPreference`.
- Zuordnung (Annahme des Leads): Profilkarten = gesicherte Bereiche (Design, Tastenanordnung,
  Gesten; nur vorhandene), Flacher Look, „+ Neues Profil“ (→ Tastaturprofile); Knöpfe mit
  Rückfrage, hervorgehoben solange die Einstellungen ihnen entsprechen (P136). Die Mockup-Karte
  „ALLTAG“ (ein Profil über alle Bereiche) gibt es nicht, weil der Speicher nur getrennte Bereiche
  kennt. Abweichungen vom Mockup: Pfeil neben „UPDATES“ (Überschrift öffnet die Seite Updates),
  Knopf „Build N installieren“ nur bei einem neueren Build, zusätzliche Zeilen „Alle erweiterten
  Einstellungen“, „Alle Infos und Links“, Gestendaten (nur in der Sammelphase); Titel der
  App-Zeile ist der App-Name (im Debug-Build „Agent Keyboard Lab“).
- Tests (Robolectric, kein Gerätetest): `SettingsSystemAreaTest` 10 Tests (Texte und
  Abschnittsfolge des Mockups, keine Karte „Seiten“; `updateCardText` für Unbekannt, Aktuell,
  Verfügbar, veraltetes Ergebnis nach Update und Fehler; Karte zeigt den gespeicherten Stand, beim
  Öffnen kein Client und keine Anfrage, „Jetzt prüfen“ genau eine Anfrage an die Releases-API ohne
  `Authorization` über die Attrappe, Ergebnis und Zeit gespeichert, „Build N installieren“ →
  Updates; gesperrt nur Hinweis und Diagnose, kein Client; Profilkarten Role.Button ohne
  Auswahl, Flacher Look und gesichertes Design nach Rückfrage angewandt, Abbrechen ändert nichts,
  Hervorhebung folgt den Einstellungen, „+ Neues Profil“ → Tastaturprofile; URL-Erkennung und
  Hardware-Tastatur schreiben die echten Schlüssel; Emoji-Version zeigt „Android 14“, Dialog mit
  „Standard“ entfernt den Schlüssel; Dialoge Sichern/Wiederherstellen, Gesten-Bibliothek und
  Versteckte Funktionen öffnen; alle Seiten aus `SettingsAreas.entries(SYSTEM)` plus Debug und
  Export öffnen ihre Route; Weiterleitung aus `AgentAreaScreen`; alles Anklickbare ≥ 44 dp; Bild
  mit Pixelprüfung). `SettingsStartScreenTest` (P119), `UpdateCheckTest`,
  `KeyboardProfileStoreTest`, `FlatStyleTest` unverändert grün. Nicht geprüft: fünf Tipps auf die
  Version (im Debug-Test sind die Debug-Einstellungen immer sichtbar), Protokoll speichern und
  Lizenz-Link (Systemdialoge), Sperrwechsel während die Seite offen ist.
- `python3 tools/dev.py agent-tests`: grün, 811 Tests in 90 Klassen, 0 Fehler (2026-11-14). Kein
  Build, keine volle Upstream-Suite (geteilte Maschine). `docs_sync --check`: grün.
- Bild (nicht versioniert): `app/build/agent-previews/settings-area_system-full.png`; mit
  `System.dc.html` verglichen: Held, grün getönte Update-Karte mit hellem und umrandetem Knopf,
  Karte PROFILE mit Verlaufskarten und Leucht-Pillen, drei Zeilenkarten mit Punkten, Schaltern und
  Wert „Android …“ stimmen. Erster Entwurf schnitt Buchstaben am linken Rand ab (P137), behoben.
  Systemschrift statt Space Grotesk/Inter.
- Offen: Prüfer-Review (berührt keine Eingabepfade), Gerätetest (Aussehen, TalkBack, Dialoge im
  dunklen Schema, echte Update-Prüfung).
- Nachprüfung 38h (Befunde des Prüfers, 2026-11-14): Profilkarten, Flacher Look und
  Gesten-Bibliothek entstehen nur mit `credentialAccessAllowed`, sonst Hinweis (P145);
  `KeyboardProfileStore.matchesCurrent` wirft nicht mehr bei einem Wert außerhalb der Bindungen;
  Profilkarten nur nach Änderung bzw. `ON_RESUME` neu berechnet; Leucht-Pille `index % 3`;
  Download-/Installationsfehler als „Installation fehlgeschlagen“, „gerade eben“ unter einer
  Minute, „am <Datum>“ ab einer Woche; Debug-Untertitel „Freigeschaltet“. Neue Tests:
  `SettingsSystemAreaTest.beforeTheFirstUnlockNothingTouchesCredentialProtectedStorage` (Nutzer
  gesperrt bei offenem Sperrbildschirm, danach `ON_RESUME` zeigt die Karten),
  `aCurrentValueOutsideTheProfileBindingsDoesNotBreakThePage`,
  `KeyboardProfileStoreTest.matchesCurrentIsFalseInsteadOfThrowingForAValueOutsideTheBindings`;
  erweitert: Sperrtest prüft versteckte Profilkarten, `updateCardText` (Installationsfehler, gerade
  eben, Datum), Emoji-Version mit Wert aus dem Regler (OK → 30, „Android 11“) und „Standard“
  (Schlüssel neu gesetzt von `SupportedEmojis.load`, mindestens API 28, Zeile zeigt ihn), Zustand
  nach dem Neuzeichnen-Tipp. Gezielt (Robolectric): `SettingsLookScreenTest` 21,
  `SettingsSystemAreaTest` 12, `KeyboardProfileStoreTest` 9, `FlatStyleTest` 10,
  `SettingsStartScreenTest` 10 – 62 Tests, 0 Fehler. Kein `agent-tests`-Lauf, kein Build, kein
  Gerätetest (geteilte Maschine). Echte Direct-Boot-Prüfung auf einem Gerät steht aus.
- Zweite Nachprüfung 38h (Prüfer, 2026-11-14): Die Sperre für Profilkarten, Flachen Look und
  Gesten-Bibliothek war unnötig, denn die Einstellungen-Activity öffnet ihren Speicher mit dem
  DE-Kontext (`SettingsActivity.attachBaseContext`); nur `UpdateSettings` ist CE. Lead: Sperre
  auf die Update-Karte beschränkt, Hinweistext entfernt, P145 richtiggestellt; Test umbenannt in
  `beforeTheFirstUnlockOnlyTheUpdateCardWaits` und prüft jetzt, dass Profilkarten und
  Gesten-Bibliothek gesperrt sichtbar bleiben (Robolectric wirft bei CE-Zugriff nicht, geprüft
  wird nur das Sichtbare). Testlauf siehe Lead-Lauf vor Build 19.
## Etappe 38b (Einstellungen: Aussehen)

- Auftrag (Lead, ROADMAP 38): Bereichsseite Aussehen 1:1 nach
  `docs/referenzen/einstellungen-mockup/Aussehen.dc.html`, eigene Datei, nur Baukasten 38a, jede
  Zeile mit echtem Wert und bestehendem Dialog, alles Bisherige erreichbar. Gebaut in einer
  Arbeitskopie, kein Prüfer-Review, kein Gerätetest.
- Gebaut: `AgentAreaLookScreen.kt` (Designs, RGB-Karte, drei Abschnitte, letzte Zeile zur alten
  Seite), `agent_settings_look_strings.xml` (Englisch, Deutsch). Geändert: `AgentAreaScreen.kt`
  (eine Zeile Weiterleitung für LOOK, Kommentar an `SettingsAreas.entries(LOOK)`; die drei Ziele
  bleiben), `AgentRgbScreen.kt` (`targetLabel` `internal`). Upstream: `KeyboardScalePreference.kt`
  und `CustomFontPreference.kt` (optionaler Platz `row`, Standard unverändert).
- Abweichungen vom Mockup: Kacheln zeigen die echten Designs (Namen und Farben aus
  `MyDesigns`/`DesignTiles`) statt der Beispielnamen, Verlauf Hintergrund → Akzent (Tag/Nacht:
  Tag- → Nachthintergrund); zusätzlich unter dem Raster die Pille „Alle <n> Designs“ zur
  Design-Seite (die übrigen Kacheln, Farbsätze aus HeliBoards Farbeditor); „Bearbeiten“ ist ohne
  gewähltes eigenes Design ausgegraut (eingebaute Looks sind nicht bearbeitbar). Tag/Nacht und
  Navigationsleiste sind Schalter statt Wert und Pfeil (Auftrag: Schalter schreiben echte
  Einstellungen); Tag/Nacht nur ab Android 9 wie bisher. Schalter 52 × 30 dp aus dem Baukasten
  (Mockup 56 × 32). Werte: „Tastenabstand“ als Faktor „1,0“ wie im Mockup (der Dialog zeigt %),
  „Geteilte Tastatur“ nennt die eingeschalteten Lagen. Zusätzliche letzte Zeile „Alle
  Aussehen-Einstellungen“ (Stil, Symbolstil, Tastenränder, Farben, Symbole anpassen,
  Leertastentext, Höhe der unteren Reihe). Ränder, Schriftgröße, Emoji und Hintergrundbild öffnen
  eine Liste mit den Upstream-Zeilen (ein Tipp mehr), weil es je mehrere Einstellungen sind.
- Tests (Robolectric, kein Gerätetest): `SettingsLookScreenTest` 18 Tests (Texte und Reihenfolge
  des Mockups, Karte „Seiten“ weg; Standardwerte und geänderte Werte samt Dateien; Tag/Nacht,
  Navigationsleiste und RGB-Schalter schreiben die echten Einstellungen, RGB nur `enabled`;
  RGB-Zusammenfassung mit zwei und drei Zielen und 30/60 Hz; RGB-Karte, „Alle Designs“ und letzte
  Zeile öffnen genau `SettingsAreas.entries(LOOK)`; Kacheln wählen Dunkel, Flacher Look, eigenes
  Design; gewählte Kachel von der Design-Seite erscheint; „Bearbeiten“, „+ Neues Design“ und langes
  Drücken öffnen den Editor; Höhen-Dialog, vier Gruppen-Dialoge mit den Upstream-Zeilen und deren
  eigener Dialog; Geteilte Tastatur schreibt `split_keyboard_landscape` und zeigt den
  Teilungsabstand; Eigene Schrift öffnet die Dateiauswahl bzw. „Laden/Löschen“ und löscht; alles
  Anklickbare ≥ 44 dp; Bild mit Pixelprüfung). `SettingsStartScreenTest` (10) und
  `SettingsKitTest` (4) unverändert grün.
- Gezielt (Robolectric, 2026-11-14): `SettingsLookScreenTest` 18, `SettingsStartScreenTest` 10,
  `SettingsKitTest` 4, `DesignTilesTest` 4, `DesignEditorTest` 5, `MyDesignsTest` 13,
  `RgbSettingsScreenTest` 13 – 67 Tests, 0 Fehler. `python3 tools/dev.py agent-tests` lief vor der
  Korrektur P124 zweimal mit je 790 Tests: einmal rot in
  `SettingsLookScreenTest.switchesWriteTheRealPreferences` (Ursache gefunden: ungenutzter
  Parameter `version`, P124; danach mit `DesignTilesTest`/`DesignEditorTest` davor nachgestellt und
  grün), einmal rot in `DesignTilesTest.theSystemTileWritesWhatTheSwitchAndBothColourPickersWrite`
  (`emoji_max_sdk` taucht zeitabhängig in den Einstellungen auf; nicht von 38b berührt, gezielt
  grün). Der volle Lauf nach der Korrektur brach ab (Container ohne Speicher); ihn macht der Lead.
  `docs_sync --check`: grün. Kein Build (geteilte Maschine).
- Bild (nicht versioniert): `app/build/agent-previews/settings-area_look.png` (390 × 2200 dp), mit
  `Aussehen.dc.html` verglichen: Held, Karten, Radien, Raster 3 × 2, Pillen, RGB-Streifenbild,
  Abschnitte und Punktfarben stimmen; lange Kachelnamen („Systemeinstellung“) werden gekürzt.
  `SettingsStartScreenTest.renderStartPageAndAreaPages` schreibt dieselbe Datei in 1180 dp Höhe;
  maßgeblich ist der Lauf von `SettingsLookScreenTest.renderLookPage`.
- Offen: Prüfer-Review, Gerätetest (Aussehen am Telefon, TalkBack, Dialoge, Dateiauswahl).
- Nachprüfung 38b (Befunde des Prüfers, 2026-11-14): `ConfigurationCompat.getLocales` statt
  `configuration.locales` (API 24+, minSdk 21); Eigene Schrift, Emoji und Hintergrundbild zeigen
  nach Laden/Löschen den neuen Wert (P146: `ON_RESUME`, Schließen des Gruppen-Dialogs,
  `CustomFontPreference(onChanged)`, eigener Einstellungs-Listener); Gruppen-Dialoge werten ihre
  Bedingungen bei offenem Dialog neu aus (Emoji: Tastengröße ab Skalierung ≠ 1, Hautton ab
  Emoji-Version 24); „Tastenabstand“ nur mit Tastenrändern wie auf der alten Seite (vorher immer).
  Neue Tests: `SettingsLookScreenTest.keyGapRowFollowsTheKeyBorders`,
  `fileOnlyChangesShowAfterResumeDeleteAndClosingTheDialog`,
  `emojiDialogReadsItsConditionsAgainWhileOpen`; Standardwerte prüfen, dass „Tastenabstand“ ohne
  Ränder fehlt, Reihenfolge/44-dp-Test mit Rändern. Testlauf siehe Nachprüfung 38h (gleicher Lauf).

## Etappe 41 (Formatwächter)

- Auftrag (Lead, ROADMAP 41): Hinweis-Chips nur in Feldern mit eindeutigem Typ, nie automatisch,
  ein Tipp übernimmt; E-Mail (Domain-Tippfehler, fehlendes `@`, Leerzeichen), Telefon, IBAN
  (Modulo 97, grüner/roter Punkt, Viererblöcke), Dezimalzeichen (nur Hinweis); reine Funktionen
  in `agent-core`, Schalter „Formatwächter“ (an) auf Tippen & Korrektur → Regeln.
- Größenmessung libphonenumber (vor dem Einbau): Jar 9.0.40 von Maven Central 360 070 Byte
  (68 Klassen 257 KB, 545 Metadaten-Dateien 331 KB entpackt bzw. 126 KB gepackt, keine
  Abhängigkeiten). Debug-APK vorher (`tools/dev.py build`, 2026-11-14) 38 660 284 Byte. Der
  zweite Build nach dem Einbau entfiel (Speicher der geteilten Maschine, Anweisung des Leads);
  geschätzt etwa 0,35 MB mehr (Jar gepackt, Klassen als Dex), also weit unter 1,5 MB:
  libphonenumber statt eines eigenen Formatierers.
- `./gradlew :agent-core:test`: grün, 331 Tests, 0 Fehler, darin `format.FormatGuardTest` 28
  (Domain-Tippfehler von 16 Anbietern, richtige und unbekannte Domains, Groß-/Kleinschreibung,
  Punkt am Ende, Umlaut-Tippfehler und echte Umlaut-Domains ohne Hinweis, Domains beim Tippen,
  Abstandsgrenzen, zwei gleich nahe Anbieter, Leerzeichen, fehlendes `@`; gültige IBANs aus DE, AT,
  ES, CH, GB, NL, FR, ungültige Prüfziffern, falsche Längen und Formen; Telefon national →
  international für DE, AT, CH, ES, US, `00`/`+`-Normalisierung, ungültige und schon formatierte
  Nummern; Dezimalzeichen de/es/en, Tausender-Fall).
- `./gradlew :app:testDebugUnitTest` gezielt: `FormatGuardChipsTest` 9 (Feldtypen, Chips nur im
  richtigen Typ, nie in Passwort-/Zahlenpasswortfeldern, Inkognito, No-Learning, gesperrt, ohne
  Einstellungen; Schalter aus; nur ganzes Feld; Bauen ändert nichts, Tipp genau einmal; IBAN-Punkt
  grün/rot, Gruppieren nur gültig; Dezimal-Hinweis ändert nie; Sprache aus `hintLocales`),
  `InputLogicTest.formatGuardChipChangesTheFieldOnlyOnATapAndOnce` (echter Host: E-Mail, Telefon,
  IBAN, zweiter Tipp auf alten Chip abgelehnt, Schalter aus), dazu unverändert grün
  `SettingsTypingAreaTest` 9, `SettingsStartScreenTest` 10, `KeyboardCommandsTest` 5,
  `EmojiMixCommandTest` 13 (`--max-workers=1`, 2026-11-14).
- Nicht gelaufen: `tools/dev.py agent-tests` (der Lead führt die volle Suite aus), Upstream-Suite,
  Build nach dem Einbau. Keine Engine-Datei geändert (`InputLogic`, `RichInputConnection`,
  `PointerTracker` unverändert); der Ersatz geht über das vorhandene
  `InputLogic.replaceCommandText`. `docs_sync --check`: grün.
- Offen: Prüfer-Review, Gerätetest (Leiste in Telefon- und Zahlenfeldern echter Apps, Ersetzen in
  Bank- und Anmeldefeldern, TalkBack).
- Nachprüfung (Prüfer-Befunde, 2026-11-14): M1 `FormatGuardChips.isBlocked` prüft
  `IME_FLAG_NO_PERSONALIZED_LEARNING` selbst (InputAttributes zählt es nur in Textfeldern als
  Inkognito); M2 `EmailCheck`: echte TLD bleibt (nur Anbieter mit derselben TLD werden
  verglichen), eine andere TLD nur als Tippfehler einer Anbieter-TLD, Liste `KNOWN_DOMAINS` echter
  Varianten (`online.de`, `mail.com`, `hotmail.fr` …) nie mit Hinweis; N5 der Tipp prüft vor dem
  Ersetzen erneut, dass hinter der Schreibmarke nichts steht; N6 Land für Telefon: Feld, dann
  Tastatur-Subtyp, dann Systemsprache (`FormatGuardChips.country`, `systemCountry`), dann
  Sprache; N7 KDoc-Beispiel korrigiert, App-Liste im Baustein-Editor ohne Symbole. Metadaten von
  libphonenumber werden weiter beim ersten Telefon-Hinweis im Hauptthread geladen (ein
  Vorladen im Hintergrund bräuchte eine neue Leisten-Aktualisierung danach; offen als Gerätetest,
  P140). Tests: `FormatGuardTest` 29 (neu `realDomainsOfOtherCountriesOrServicesGetNoHint`: alle
  gemeldeten Domains, echte TLDs anderer Länder, TLD-Tippfehler `.cpm`/`.comm`/`.ed`/`.nte`, jede
  bekannte Domain ohne Hinweis; alle bisherigen Positivfälle unverändert grün),
  `FormatGuardChipsTest` 12 (neu: No-Learning in Telefon- und Kommazahlfeld, Land aus Subtyp,
  System (AT mit `0664`) und Feld, Tipp nach Text hinter der Schreibmarke). Befehle siehe
  Nachprüfung von Etappe 42.

## Etappe 42 (Textbausteine 2.0)

- Auftrag (Lead, ROADMAP 42): `@snip` mit Vorlagensprache in `agent-core`, Ausfüllfeldern in der
  Leiste, Cursor-Marke, App-Beschränkung, Editor mit Vorschau. Gebaut in einer Arbeitskopie, kein
  Prüfer-Review, kein Gerätetest.
- Gebaut: `C/SnippetTemplate.kt` (`SnippetTemplate`, `SnippetApps`), `SnipCommand.kt`
  (Erweiterung beim Tipp, `Filling`, `onEnter`, `SystemClipboard`, `editorPreview`),
  `SnippetStore.kt` (`storageVersion` 2, `apps`, `literal`), `AgentSnippetsSection.kt`
  (`SnippetEditor`, `AppChooser`), `KeyboardCommands.kt` (`Command.onEnter`,
  `Host.moveCursorBack`, `onEnter`, Host als eigene Funktion), `LatinIME.java` (Enter-Hook in
  `onEvent`, `moveAgentCursorBack`), `agent_snippet_strings.xml` (Englisch, Deutsch). Nicht
  geändert: `InputLogic`, `RichInputConnection`, `PointerTracker`, `AgentArea*Screen.kt`.
- Entscheidungen: Felder nutzen den vorhandenen Weg der Befehlsargumente (der Wert wird im Feld
  hinter „@snip name Feld: “ getippt, die Leiste zeigt Feld und Wert), statt Tasten in ein eigenes
  Eingabefeld umzuleiten; so bleibt die Engine unberührt und der Wert geschützt. Speicher ohne
  Umschreibung: Version 1 wird gelesen, ihre Bausteine bleiben wörtlich (`literal`).
- Tests (JVM bzw. Robolectric, kein Gerätetest): `SnippetTemplateTest` 16 (Beispiel des Owners,
  Datum über Monats-, Jahres- und Schaltjahresgrenzen mit fester Uhr, Wochen, Wochentage,
  Sprachen de/en/es und Rückfall Deutsch, Groß-/Kleinschreibung, Zwischenablage, Felder einmal
  je Name, ungültige Feldnamen, Cursor (erster zählt, UTF-16), `{{`, Unbekanntes, offene und
  verschachtelte Klammern, 2000 Zufallstexte ohne Ausnahme, wörtliche Vorlagen, keine Texte in
  `toString`, Paketnamen). `SnipCommandTest` 22 (davon 13 neu: Erweiterung erst beim Tipp,
  Tastatursprache, Cursor zurück, Beispiel des Owners über Leiste und Enter, mehrere Felder und
  gleicher Name, gescheiterter Schritt und Abbrechen, Enter ohne Feld, vergessene Werte,
  App-Beschränkung, Zwischenablage-Regeln mit Hinweis, echte Zwischenablage mit Verlauf aus,
  vertraulich gemerkt und markiert, zu lang, leer, gesperrt; Passwortfeld; alte Bausteine;
  Editor-Vorschau), `SnippetStoreTest` 8 (Version-1-Datei, Version 2 mit Apps, kaputte Dateien;
  die alte Prüfung „Version 2 ist ungültig“ prüft jetzt Version 3 und 0),
  `InputLogicTest.typedSnippetFieldTakesEnterThenInsertsAndPlacesTheCursor` (echter Host: Tipp,
  Wert getippt, Enter fügt ein statt zu senden, Datum in der Tastatursprache, Schreibmarke).
  Zusätzlich grün: `KeyboardCommandsTest`, `CommandTemplatesTest`, `ClipCommandTest`,
  `InputLogicTest.typedCommand*` (2026-11-14). `python3 tools/dev.py agent-tests` nicht gelaufen
  (Auftrag: der Lead lässt die volle Suite laufen), kein Build, keine Upstream-Suite.
  `docs_sync --check`: grün.
- Offen: Prüfer-Review (Enter-Hook, P141–P143), Gerätetest (Feldabfrage und Enter in Messengern
  mit Senden-Taste, Schreibmarke in WhatsApp/Telegram, App-Auswahl und Paketsichtbarkeit,
  Zwischenablage mit vertraulichem Clip), Enter einer Hardware-Tastatur (P142).
- Nachprüfung (Prüfer-Befunde, 2026-11-14): N2 `SnipCommand.onEnter` nimmt Enter während einer
  Feldabfrage immer (sonst schickte die App den Befehlstext), ruft aber nur `next`, wenn
  `Host.canReplaceNow` (echter Host: `LatinIME.canReplaceAgentCommand` = keine blockierte Eingabe
  und keine laufende Geste, neuer Getter `InputLogic.hasPendingBatchInput`); sonst bleibt das Feld
  gefragt, kein Hinweis „abgelaufen“. N3 Hardware-Enter (`KEYCODE_ENTER`/`NUMPAD_ENTER` ohne
  Modifikator) fragt in `KeyboardActionListenerImpl.onKeyDown` zuerst `KeyboardCommands.onEnter`,
  auch bei ausgeschalteter Hardware-Tastatur-Einstellung; Wiederholungen und das Loslassen
  derselben Taste werden mit verschluckt (P142). N4 `SnipCommand.longArgument` (500 statt 200
  Zeichen), ab 40 Restzeichen „(noch N Zeichen)“ im Feld-Chip. N1 nicht geändert: das Setzen der
  Schreibmarke nach `{cursor}` im selben Batch wie das Ersetzen bräuchte einen Umbau von
  `RichInputConnection.replaceTextBeforeCursorIfUnchanged` (Prüfung nach dem Commit); bleibt zwei
  Schritte, Gerätetest-Punkt (P147). Tests: `SnipCommandTest` 24 (neu: Enter während einer Geste
  genommen ohne Änderung, danach weiter; lange Werte bleiben Befehl, Rest-Hinweis, Einfügen),
  `InputLogicTest.hardwareEnterDuringASnippetFieldMovesOnInsteadOfSending` (echter Host: Enter
  über `LatinIME.onKeyDown` springt weiter und fügt ein, Wiederholung nur einmal, Loslassen
  genommen, ohne Feld bleibt die Taste bei der App, `checkConnectionConsistency`). Gelaufen:
  `./gradlew :agent-core:test --tests 'dev.agentkeyboard.core.format.*'` und gezielt
  `:app:testDebugUnitTest` mit `FormatGuard*`, `SnipCommandTest`, `SnippetStoreTest`,
  `*InputLogicTest`, `KeyboardCommandsTest` (`--max-workers=1`, 2026-11-14): `agent-core`-Format
  grün; App 238 Tests, 1 Fehler = geerbtes `InputLogicTest.insertLetterIntoWordHangulFails`; nach
  der letzten Änderung am Feld-Chip `SnipCommandTest` 24, `FormatGuardChipsTest` 12 und die drei
  `InputLogicTest`-Fälle (Hardware-Enter, Baustein, Formatwächter) erneut grün. Nicht gelaufen:
  volle Suite, Build, Gerätetest. Engine-Änderungen (Prüfer nötig): `InputLogic.java` (nur der
  Getter), `LatinIME.java` (`canReplaceAgentCommand`), `KeyboardActionListenerImpl.kt`
  (Hardware-Enter).

## Zweite Nachprüfung 41/42 (Lead, 2026-11-14)

- Prüfer (nur Lesen, Python-Nachbau von `EmailCheck` mit rund 80 Domains): M1, M2, N2–N7 in
  Ordnung, N1 ehrlich dokumentiert; kein hoher Befund. Vom Lead behoben: echte Domains `ymail.com`,
  `email.com`, `gm.com`, `gmc.com`, `ms.com`, `ge.com`, `cloud.com`, `weg.de`, `life.com`,
  `line.com` ohne Hinweis (`KNOWN_DOMAINS`, Test erweitert); Hardware-Enter: `onKeyUp` nimmt das
  Loslassen nach Tastencode auch mit inzwischen gedrücktem Modifikator, `agentEnterDown` wird bei
  blockierter Eingabe zurückgesetzt; Telefonland aus der Systemsprache nur, wenn sie die Sprache
  der Tastatur ist (`systemLocale`; Test: en-US-System mit deutscher Tastatur ergibt +49). Bekannte
  Lücken dokumentiert (P148: Tippfehler, die eine echte TLD ergeben; P149: Werte über 500
  Zeichen). Testlauf siehe Lead-Lauf vor Build 19.
- GitHub-Lauf auf dd28d93: Agent und Upstream grün, Lint rot (MissingTranslation in fünf neuen
  Textdateien der Bereichsseiten, P150). Behoben mit `tools:ignore`; `lintDebugNoMinify` lokal grün.

## Etappe 44 (Zwischenablage-Wächter und Tresor)

- Auftrag (Lead, ROADMAP 44, Owner 2026-11-14): jeden neuen Clip ohne Rückfrage sortieren
  (Geheimnis, Einmalcode, persönliche Daten, normal), reiner Klassifikator in `agent-core`,
  Bereich „Tresor“ und „Einmalcodes“ in der Verlaufsansicht, Geheimnisse und Codes nur im
  Speicher. Gebaut in einer Arbeitskopie, kein Prüfer-Review, kein Gerätetest.
- Entscheidet die offene Frage aus `docs/HANDOFF.md` „vertrauliche Clips im normalen Verlauf“:
  nein. Vertraulich markierte Clips, Clips aus Passwortfeldern und alles, was der Wächter als
  Geheimnis oder Code erkennt, kommen nie in `ClipboardDao`; vorher gespeicherte, nicht
  angeheftete werden beim Öffnen der Ansicht gelöscht (Handoff-Eintrag aktualisiert der Lead).
- Vorher geprüft: Verlauf heute. `ClipboardHistoryManager.fetchPrimaryClip` schrieb jeden
  Text-Clip ohne Blick auf `IS_SENSITIVE` in die Datenbank (DE, Tabelle `CLIPBOARD`, Cache
  `ClipboardDao.cache`); vertraulich war nur die Anzeige im Leisten-Chip (Sternchen) und der Hash in
  `SensitiveClips` für `@clip`. Jetzt entscheidet `ClipVault.offer` vor `addClip`.
- System-Zwischenablage leeren (Frage vor dem Bau): AOSP `ClipboardService.java` (Spiegel
  `aosp-mirror/platform_frameworks_base`, Zweig `main`, gelesen 2026-11-14, `android.googlesource.com`
  war aus der Sitzung nicht erreichbar): `clearPrimaryClip` prüft `clipboardAccessAllowed(
  OP_WRITE_CLIPBOARD, …)`, dort „Writing is allowed without focus. allowed = true“, danach nur
  AppOps; eine Ablehnung gibt still zurück. Upstream ruft `clearPrimaryClip` schon bei „Verlauf
  leeren“ aus der Tastatur auf. Gebaut eng: nur nach einem Tipp auf ein Geheimnis im Tresor, nur
  wenn die Zwischenablage noch denselben Text (ab Android 8 denselben Zeitstempel) hält, danach
  zurückgelesen (P156). Auf Android 16 nicht auf einem Gerät geprüft; ob Hersteller-Verläufe
  (Samsung, Gboard-Sync) den Clip behalten, ist offen.
- Entscheidungen des Klassifikators (konservativ, Einzelheiten in `docs/ARCHITECTURE.md` →
  Kern): Geheimnis nur bei starkem Signal (Markierung, privater Schlüssel, bekannte
  Schlüsselformate und JWT überall im Clip, ein einzelnes zufälliges Token, ein passwortartiges
  Wort); Hex (Git-Hashes, SHA-256, MD5, UUIDs, Farben), Pfade, Links, E-Mail, Namen mit `@`/`#`,
  `data:`-URIs, lange Base64-Blobs (über 200 Zeichen) und Text mit Leerzeichen bleiben normal.
  Kurze Base64-Werte mit Ziffern (Schlüssel) sind Geheimnisse. Einmalcode: 4–8 Ziffern allein
  (keine Jahreszahl 1900–2099) oder in einem Satz bis 300 Zeichen mit Schlüsselwort (de/en/es).
  Bekannte Grenzen: passwortartige Produktnamen wie „iPhone15-Pro!“ gelten als Geheimnis; eine
  kopierte fünfstellige PLZ allein gilt als Code; zufällige Tokens mit wenigen Ziffern werden
  übersehen (dann normaler Verlauf).
- `./gradlew :agent-core:test`: grün, 369 Tests, 0 Fehler, darin neu `clip.ClipGuardTest` 21
  (Schlüsselformate OpenAI/Anthropic/GitHub/AWS/Slack/Google/Stripe auch in Zeilen und Code, JWT,
  privater Schlüssel, Markierung; Tokens und Passwörter; deutsche Wörter, Komposita und
  Kennungen; Links; Git-Hashes, SHA-256, MD5, UUID, Pfade, Dateinamen; Code ohne Schlüssel;
  Base64 kurz/lang/`data:`; Codes allein und in Sätzen de/en/es; Sätze ohne klaren Code
  (Datum, Telefon, Preis, zwei Zahlen, „Spinne“); IBAN DE/AT/ES mit und ohne Leerzeichen;
  Telefon DE/AT/ES; E-Mail; Adressen de/at/es/en; Maske zeigt höchstens vier Zeichen des
  Geheimnisses; `toString` ohne Text; sehr lange Clips) und `format.FormatGuardTest` unverändert
  grün (`PhoneFormat.isValid` neu).
- `./gradlew :app:testDebugUnitTest` gezielt (`--max-workers=1`, 2026-11-14): neu `ClipVaultTest` 16
  (Geheimnisse, Codes und markierte Clips nie in der Datenbank, IBAN und Text schon; Clip aus dem
  Passwortfeld nirgends; Verlauf aus: nichts behalten, `freshest` liest nur den aktuellen Clip, nie
  ein Geheimnis; gesperrt und Inkognito behalten nichts; alte Geheimnisse und Codes verlassen den
  Verlauf, angeheftete bleiben; Ablauf 2 min/10 min mit Testuhr, kein Zurückkommen beim erneuten
  Lesen; erstes Einfügen entfernt das Geheimnis, der Code bleibt; Etappe-45-Abfrage nach Art und
  Alter; Maske auf dem Bildschirm und in allen `contentDescription`s, kein Teil des Schlüssels in
  der Ansicht; Tipp fügt das Geheimnis einmal ein und leert dieselbe System-Zwischenablage, einen
  anderen Clip nicht; Tipp auf einen Code fügt nur die Ziffern ein; kein Tresor in Passwortfeld,
  Inkognito, gesperrt, Tipp nach dem Sperren fügt nichts ein; Ablauf bei offener Ansicht;
  Beschriftungen; kein Leisten-Chip für Geheimnisse), dazu unverändert grün `ClipCommandTest` 10,
  `FlatClipboardTest` 8, `ScreenshotChipTest` 14, `SnipCommandTest` 24, `FlatStyleRenderTest` 5,
  `InputLogicTest.screenshotChip*` und `generated*Copy*` 9.
- Nicht gelaufen: `tools/dev.py agent-tests`, volle Suite und Build (geteilte Maschine, Anweisung
  des Leads), Lint. Keine Engine-Datei geändert (`InputLogic`, `RichInputConnection`,
  `PointerTracker` unverändert); geändert nur schon geänderte Upstream-Dateien
  (`ClipboardHistoryManager.kt`, `ClipboardHistoryView.kt`, `ClipboardAdapter.kt`).
  `docs_sync --check`: grün.
- Offen: Prüfer-Review, Gerätetest (Leeren der Zwischenablage auf Android 16, TalkBack in der
  Ansicht, echte SMS-Codes und Passwortmanager, Höhe der Liste mit zwei Tresorreihen).
## Etappe 43 (Symbole malen: Punkte und Start nach dem Tippen)

- Auftrag (Lead, Rückmeldung des Owners zu Build 19): (A) bei ÷, % und allen Symbolen mit einem
  getippten Punkt geht das Symbol verloren oder es kommen Symbol und Buchstabe; (B) direkt nach dem
  Tippen eines Worts erkennt die Tastatur oft keine Zeichnung. Gebaut in einer Arbeitskopie, kein
  Prüfer-Review, kein Gerätetest. Das „&“ der Rückmeldung als „und“ gelesen: seine Vorlage ist ein
  Zug ohne Punkt, unverändert.
- Ursache A (aus dem Code, im Modell gemessen): Ein Tipp in der Pause war nur dann ein Punkt, wenn
  `InkDots` ihm an der Lage zustimmte (0,12 seitlich, 0,25 näher/weiter); eingebaut hatte nur ÷
  Punkte. Sonst wurde das Symbol sofort geschrieben und der Tipp getippt („Symbol + Buchstabe“);
  ein % mit getippten Kreisen war nie möglich. Ursache B (verifiziert): HeliBoard setzt nach einem
  getippten Buchstaben 500 ms `mAfterFastTyping` (`GestureStrokeRecognitionPoints.addDownEventPoint`,
  `mGestureFastTypingCooldown`); `isStartOfAGesture` verlangt dann 6 Tastenbreiten, über 450 ms
  fallend auf 0,35. Der erste Symbolstrich läuft über genau diese Erkennung. Nachweis:
  `SymbolInkTest.drawingRightAfterTypingAWordStartsInk` mit abgeschalteter Änderung
  (`gestureTypingTime` liefert die alte Zeit) scheitert schon bei 100 ms (kein `onStartBatchInput`),
  mit Änderung grün. Die Einstellung „Schnelltipp-Sperre“ steht auf der Gestenseite nur bei
  Gestenschreiben (bei Symbole malen ausgeblendet, `GestureTypingScreen`), auf der Bereichsseite
  nie; sie wirkt beim Malen jetzt nicht mehr und bleibt deshalb ausgeblendet.
- Gebaut (Kern): `InkDots.isDot` als möglicher Punkt (Kreis `DOT_REACH` 0,5 der Größe des
  punktlosen Teils, `BASE_DISTANCE` 2,3 statt 1,8, weniger Punkte als das Symbol, Regel 4 nur
  gegen Symbole ohne Punkte), neu `InkDots.keeps` (ganze Zeichnung = Symbol mit genau so vielen
  Punkten und jeder Punkt höchstens `KEEP_REACH` 0,35 von einem eigenen Punkt); `InkSymbols`: %
  mit Strich und zwei Punkten (auch gestaucht), ‰, !, ¡, ?, ¿ (118 statt 105 Vorlagen);
  `InkTap.AFTER_TYPING_MILLIS` 500, `START_AFTER_TYPING_KEYS` 0,75. App: `SymbolInk` (mögliche
  Punkte als ein Punkt, `resolve`, `Tapper`, `takeWaiting` mit Rückruf, `onWaiting` mit
  `possibleDots`, `gestureTypingTime`), `SymbolDrawing` (`keeps` mit eigenen Vorlagen, Vorschau
  entscheidet wie das Schreiben), `PointerTracker` (siehe unten).
- Messung (`CT/InkDotFlowTest`, neu, Finger-Modell `InkFingerModel.drawDotted`: Striche zuerst,
  Punkte danach als Tipps, %○ mit halb winzigen Kreisen; 1,5–3 Reihen hoch, 2,75 px/dp; je 200),
  vorher = Stand Build 19 mit demselben Modell: ÷ 142 → 197, % (Tupfer) 0 → 195, %○ 10 → 106,
  ‰ 0 → 163, ! 0 → 180, ¡ 0 → 172, ? 0 → 173, ¿ 0 → 169 (vorher nicht eingebaut); zusammen 152 →
  1355 von 1600. Buchstaben direkt nach Symbolen ohne Punkte (je Symbol 20 Zeichnungen, dann 1–4
  Buchstaben zufällig auf der Tastatur): verloren 3 → 2 von 1875 (beide nach „|“, als ! bzw. ¡),
  Symbol geändert 0 → 2 von 760, Reihenfolge immer richtig. Abstimmung: `DOT_REACH` 0,35/0,5/0,7
  und `KEEP_REACH` 0,25/0,3/0,35 gemessen; ohne Lageprüfung in `keeps` (nur Abstand ≤ 1,0–1,4)
  wurden 20 % der Buchstabenpaare über und unter einem Strich zu ÷ (verworfen).
  `InkDotsTest.lettersTappedAroundADrawnDashAreRarelyDots` (Paare, schlimmster Fall): 770 von 8000
  Buchstaben werden Punkte, 1741 von 4000 ersten Buchstaben warten als mögliche Punkte.
  Erkennung: `InkBenchmarkTest` hart 96,4 % (36: 96,2 %; neue Symbole ‰ 90, ! 100, ¡ 98, ? 100,
  ¿ 96), mild 99,9 %, ⇒ des Owners 190/200 ⇒, 0 ≠; `InkRecognizerTest` gestörte Vorlagen 98,3 %
  (% 259/300), Kritzeleien 10/300, 3,4 ms je Zeichnung gegen 118 Vorlagen, 9,2 ms gegen 328.
  Nur synthetisch; nichts davon am Telefon gemessen.
- Abwägung (dokumentiert in FEATURES, ANLEITUNG, `InkDots`): Ein möglicher Punkt erscheint als
  Buchstabe erst nach der Pause (höchstens so viele wie das Symbol Punkte hat, danach schreibt der
  nächste Tipp sofort). Verloren ist ein Buchstabe nur als Teil eines Punkt-Symbols an den
  richtigen Stellen (zwei Buchstaben über und unter der Mitte eines Strichs: ÷; einer unter einem
  senkrechten Strich: !); vorher reichte ein Buchstabe über oder unter der Mitte. Ein Symbol, das
  mit einem Punkt beginnt (ein Tipp allein), bleibt unmöglich. % mit winzigen Kreisen (Strich, der
  kein Tipp ist, aber zu klein für die Kreis-Vorlagen) ist schwach (53 %); gemischte Vorlagen
  (ein Kreis, ein Punkt) halfen im Modell (89 %), scheiterten aber vollständig am Rauschen von
  `InkRecognizerTest` (P152) und sind entfernt. Bei `cancel` (Editorwechsel, abgebrochener Touch)
  gehen mögliche Punkte mit der Zeichnung verloren.
- Grenze nach dem Tippen (B): 0,75 Tastenbreiten vom Aufsetzpunkt innerhalb von 500 ms nach einem
  Buchstaben; danach HeliBoards üblicher Start (0,35 Tasten ab der schnellen Bewegung). Ein
  rutschender Tipp von 0,7 Tasten in 30 ms bleibt nach dem Tippen Buchstabe und ist 2 s später ein
  Strich (wie vor 43). Keine Mindestdauer: ein schnell gezogener Strich dauert nicht länger als ein
  rutschender Tipp. Ohne Symbole malen unverändert
  (`SymbolInkTest.withoutDrawnSymbolsTheFastTypingCooldownStays`).
- Geänderte Prüfungen alter Tests (Grund: sie beschrieben die strenge Regel von 31, die 43 auf
  Wunsch des Owners ersetzt): `InkDotsTest.tapsAwayFromTheDotsOfADashAreLetters`,
  `lettersBesideTheMiddleOfADashAreLetters`, `earlierDotsAreNotPartOfTheLine` prüfen jetzt das
  Ende (`isDot` und `keeps`, Hilfe `endsAsDots`) statt nur `isDot`: Tipps abseits bleiben
  Buchstaben, auch zusammen mit einem richtigen zweiten Punkt; ein einzelner Buchstabe über der
  Mitte ist nie ÷. `lettersTappedAroundADrawnDashAreRarelyDots` misst Paare über/unter dem Strich
  (Grenze 11 %, vorher 12 % einzelner Tipps). `withoutSymbolsWithDotsEveryTapIsALetter` und
  `SymbolInkTest.dotsCountOnlyForSymbolsWithDots` lassen alle Vorlagen mit Punkten weg (vorher nur
  ÷). `ownSymbolsWithDotsCount` nimmt ein eigenes „⊙“, weil ! jetzt eingebaut ist.
  `SymbolInkTest.aTapInThePauseWritesTheSymbolFirstAndThenTypesItsLetter` und
  `lettersTypedInThePauseDoNotSetThePace` tippen weit weg von den Punkten (330, 140) statt über dem
  linken Ende (jetzt ein möglicher Punkt, eigener Test `aTapNearADotOfASymbolWaitsAndIsTypedAfterIt`);
  `aTapHeldStillIsALetterNotADot` prüft nach der Pause. `InkAreaTest` (Finger) zieht Punkte am
  Anfang hinter den ersten Strich (P152). Keine Prüfung entfernt oder abgeschwächt, keine Ausnahme.
- Neue Tests: `InkDotFlowTest` (2, mit Grenzen), `SymbolInkTest` (11: möglicher Punkt wird Buchstabe,
  einzelner Tipp über der Mitte, Reihenfolge mit späterem Tipp, ÷ unterer Punkt zuerst, ÷ mit
  seitlichen Punkten, % mit Tupfern, % mit kleinem Kreis und Tupfer, ! und ?, Malen 100/200/400 ms
  nach einem Wort, rutschender Tipp, ohne Malen bleibt die Sperre), `InputLogicTest.symbolDraw*`
  (3: Buchstabe nach dem Symbol samt Vorschau „✎ –“, ÷ aus zwei Tipps, Vorschau-Chip mit möglichem
  Punkt; der Tapper dort ein Ersatz, der echte `typeInkTap` läuft in `SymbolInkTest`).
- Gelaufen (2026-11-14, `--max-workers=1`): `./gradlew :agent-core:test` grün;
  `:app:testDebugUnitTest --tests 'helium314.keyboard.agent.SymbolInkTest' --tests '*InputLogicTest'
  --tests 'helium314.keyboard.agent.SymbolSettingsScreenTest'`: alle grün bis auf das geerbte
  `InputLogicTest.insertLetterIntoWordHangulFails`. Nicht gelaufen: volle Suite, Agent-Tests über
  `tools/dev.py`, Lint, Build, Gerätetest.
- Engine-Änderungen (Prüfer Pflicht): `PointerTracker.java` (`init` setzt den Tapper, statisches
  `typeInkTap`, `onDownEvent` mit `gestureTypingTime` und `mInkAfterTyping`, Bedingung in
  `onGestureMoveEvent`). `InputLogic`, `RichInputConnection`, `GestureStrokeRecognitionParams` und
  `GestureStrokeRecognitionPoints` unverändert.

### Nachprüfung 43 (Prüfer-Befunde zu 357c9d0, 2026-11-14)

- Auftrag (Lead): Befunde M1, N1–N8 des Prüfers beheben, in einer Arbeitskopie. Kein Gerätetest.
- M1 (Buchstabenverlust nach dem Tippen, verifiziert): Ohne Sperre startete ein schneller Tipp, der
  0,75 Tasten rutscht, eine Zeichnung, der Buchstabe kam als „–“/„/“/„|“ oder gar nicht; setzte ein
  zweiter Daumen den Strich, während der erste lag, endete dessen Loslassen im `sInGesture`-Zweig
  ohne Code. Behoben: (a) liegt bei einem Aufsetzen schon ein weiterer Zeiger auf, bleibt HeliBoards
  Sperre (`onDownEvent`), und mit `mInkAfterTyping` startet eine Zeichnung nur allein
  (`onGestureMoveEvent`); (b) eine so direkt nach dem Tippen begonnene Zeichnung, die beim Schreiben
  nur ein Strich ist (mögliche Punkte, die Buchstaben werden, nicht mitgezählt) und
  `InkTap.isSlidTap` ist (weitester Punkt ≤ `SLID_TAP_KEYS` 1,5 Tastenbreiten vom Aufsetzpunkt, keine
  Abweichung über `SLID_TAP_BEND` 0,25 dieser Strecke) oder vom Erkenner nicht angenommen wird
  (`Sink.recognizes`), wird nicht geschrieben: `typeInkTap` tippt die Taste am Aufsetzpunkt, dann die
  möglichen Punkte. Die Vorschau zeigt für so einen Strich keinen Chip (sonst blitzte beim
  schnellen Tippen „✎ –“ auf); nur wenn er nicht erkannt wird, kann „✎ ?“ erscheinen. Ein Tipp auf
  einen Vorschau-Chip schreibt weiter das Symbol.
- M1c Messung (`CT/InkDotFlowTest`, neu, Rutschmodell angenommen, nicht gemessen: vier von fünf
  Tipps |N(0; 0,1)| Tastenbreiten, einer von fünf rollt 0,2–1,2 Tasten, ±35° zur Reihe, leicht
  gebogen, 1 dp Zittern; 10 000 Tipps): ab 0,35 Tasten 1727 (HeliBoards Start ohne Sperre), ab 0,75
  Tasten 923 (9,2 %, starten nach 43 eine Zeichnung), davon kommen mit (b) alle 923 als Buchstabe
  zurück (vorher 923 verloren; vor 43 wegen der Sperre 0). Preis von (b), einstrichige Symbole des
  Finger-Modells direkt nach dem Tippen, je Größe (je 680): 0,75–1 Taste 212 als Buchstabe (– und |
  80 von 80), 1–1,5 Tasten 200 (78 von 80), 1,5–2,3 Tasten 1, 2,3–4,6 Tasten (übliche Größe) 0. Bei
  üblicher Größe nicht erkannt (jetzt Buchstabe statt „Nicht erkannt“): 9 von 680. Abwägung: Ein
  kurzer – oder | direkt nach einem Wort braucht 1,5 Tasten Länge oder einen zweiten Strich.
  Offene Restlücke: Rutscht auch der nächste Tipp nahe der Zeichnung 8 dp oder mehr (im Modell 2206
  von 10 000), ist er ein zweiter Strich (P111), und beide Buchstaben werden ein Symbol; grob
  9,2 % × 22 % ≈ 2 von 1000 schnellen Tipps im Modell (nur geschätzt, nicht als Ablauf gemessen).
- N1: `typeInkTap` tippt nur, was `SymbolInk.startsInkOn` erlaubt (sonst nichts). N2: `onPressKey`
  mit festem Zeigerzähler 1 (vorher `getActivePointerTrackerCount()`, im Test 0 beim Ende über einen
  zweiten Finger). N3: P151 und KDoc präzisiert (schreibt die Tippzeit beim Schreiben, kein
  `startTypingStateTimer`, keine Haptik/Ton/Vorschau). N5: `withoutDrawnSymbolsTheFastTypingCooldownStays`
  prüft jetzt auch, dass derselbe Strich 2 s später einen Batch startet (Gestenerkennung also an).
  N6: `InkDotFlowTest` meldet Verluste nur für Tipps innerhalb von `nearDrawing`: 2 von 597 (Grenze
  1 %). N7: KDoc von `InkDots.keeps` (zählt alle kleinen Striche, nicht nur die Tipps). N8:
  FEATURES/ANLEITUNG sagen, dass solche Buchstaben erst nach der Pause oder vor der nächsten Taste
  und ohne Tastenvorschau, Vibration und Ton erscheinen.
- Neue Tests: `SymbolInkTest` (7, echter PointerTracker): gerutschter Tipp 0,8 Tasten → Buchstabe
  (ein Zeiger, keine Vorschau), dann nächster Buchstabe fern und nah in Reihenfolge, kurzer Strich mit zweitem
  Strich → „=“ und 1,6 Tasten → „–“, nicht erkannter Strich nach dem Tippen → Buchstabe (2 s später
  an die Senke), zwei Daumen (zweiter bzw. erster rutscht) → beide Buchstaben, möglicher Punkt auf
  jetzt Löschtaste → nichts, möglicher Punkt beim Ende über zweiten Finger → `onPressKey` mit 1.
  `InputLogicTest` (3, echter `KeyboardActionListenerImpl`/`InputLogic`, Tapper-Ersatz „x“):
  möglicher Punkt, dann ferne Taste → „a –xb“ samt `checkConnectionConsistency`; gerutschter Tipp
  nach „Ha“ → „Hax“, Wort läuft weiter, kein Vorschau-Chip und keine Chips danach; nicht erkannter Strich nach dem Tippen → „Hax“,
  ein Pfeil danach → „Hax→“. `InkDotFlowTest` (2 neu, N6-Grenze im bestehenden). Gegenprobe: mit
  zurückgenommenen Änderungen (M1a, M1b, N1, N2) scheitern 6 der 7 neuen `SymbolInkTest`-Tests;
  der =/–-Test ist absichtlich in beiden Fällen grün. Keine Prüfung entfernt oder abgeschwächt.
- Gelaufen (2026-11-14, `--max-workers=1`): `./gradlew :agent-core:test` grün;
  `:app:testDebugUnitTest --tests 'helium314.keyboard.agent.SymbolInkTest' --tests '*InputLogicTest'`:
  alle grün bis auf das geerbte `InputLogicTest.insertLetterIntoWordHangulFails`. Nicht gelaufen:
  volle Suite, Agent-Tests über `tools/dev.py`, Lint, Build, Gerätetest.
- Engine-Änderungen (Prüfer Pflicht): `PointerTracker.java` `onDownEvent` (Sperre bleibt bei
  weiterem Zeiger), `onGestureMoveEvent` (nach dem Tippen nur allein, `SymbolInk.onStartAfterTyping`),
  `typeInkTap` (N1-Wächter, Zeigerzähler 1, tote Zweige für `MULTIPLE_CODE_POINTS`/`NOT_SPECIFIED`
  entfernt). `InputLogic`, `RichInputConnection`, `GestureStrokeRecognition*` unverändert.
## Etappe 48 (Autokorrektur: getipptes Wort vorne, keine ungewollte Großschreibung)

- Auftrag (Lead, ROADMAP 48, Rückmeldung des Owners vom 2026-11-14): (a) nach dem Zurückgehen zu
  einer Korrektur das getippte Wort ganz links, Mitte/rechts ähnliche Wörter; (b) Ursache für
  ungewollte Großbuchstaben finden und eng beheben. Gebaut in einer Arbeitskopie, kein
  Prüfer-Review, kein Gerätetest.
- Befund vorher (dieselben neuen Tests gegen den alten Code, 2026-11-14, 7 von 8 rot): nach
  Löschtaste direkt nach „hullo“ → „hello“ zeigte die Leiste `[hull, hello, hallo]`, nach dem
  Zurücksetzen der Schreibmarke in „hello“ `[hallo, hull, –]`; das getippte Wort fehlte beide Male
  (HeliBoard blendet im Tippstil Index 0 aus, die Textmarke-Variante kennt das getippte Wort nur
  aus `SuggestionSpan`s, die viele Apps nicht zurückgeben, und die verzögerte Aktualisierung nach
  dem Zurücknehmen ersetzt auch diese). Mit Spans (Recorrection-Stil) stand das getippte Wort in
  der Mitte, die Korrektur links. (b) „essen“ wurde bei Wörterbuch-Reihenfolge „Essen“ vor „essen“
  automatisch zu „Essen“ (`SuggestTest`, Zeile mit `assertFalse(essen.mWillAutoCorrect)` rot);
  ein zurückgenommenes Paar nur mit anderer Großschreibung merkt `RejectedCorrections` nicht
  (Schlüssel klein), es kam also immer wieder. Nach „ca. “, „approx. “ und „z. B. “ schaltete die
  Auto-Großschreibung den nächsten Buchstaben groß (`CapsModeUtils`, Zustand WORD ohne
  Abkürzungsliste). Kein Fehler gefunden (Tests grün, auch alter Code): „z.B.“, „3. “ (deutsch),
  Emoji, URL, nach gewähltem Vorschlag mit automatischem Leerzeichen, klein geschriebener
  Satzteil. Absatzanfang (neue Zeile) schreibt groß, so gewollt. Felder mit
  `TYPE_TEXT_FLAG_CAP_WORDS` (die App verlangt jedes Wort groß) schreiben jedes Wort groß: wörtlich
  „groß, nur weil ein neues Wort beginnt“, aber von der App verlangt; nicht geändert, Frage an den
  Owner (ROADMAP 48). Zuordnung 38c „Großschreibung am Satzanfang“ = `auto_cap` (Standard an)
  stimmt; der Schalter schaltet HeliBoards ganze Auto-Großschreibung, also auch die Wortanfänge
  solcher Felder.
- Gebaut: `K/agent/TypedWordFirst.kt` (Korrekturen des Felds nur im Speicher, Start + Wort im
  Editor → getippte Form, höchstens 32; `arrange` setzt die getippte Form auf Index 2 im
  Tippstil = Platz links der Mitte, in RTL rechts, wie HeliBoards getipptes Wort bei anstehender
  Korrektur), `K/agent/Abbreviations.kt`, `CorrectionGuard.changesOnlyCase`. Engine:
  `InputLogic.java` (Feld `mTypedWordFirst`; `startInput` leert; `commitCurrentAutoCorrection`
  merkt Tipp-Korrekturen; `revertCommit` merkt das zurückgenommene Wort; `restartSuggestions` fragt
  nach Start und Wort und ordnet auch die Span-Liste; `getSuggestedWords` ordnet das Ergebnis für
  das unveränderte wiederaufgenommene Wort; `onPickSuggestionManually` meldet einen Tipp auf die
  getippte Form eines korrigierten Worts an `RejectedCorrections.remember` und
  `TypoMemory.forget`). `Suggest.kt` (`guardAllows`: reine Groß-/Klein-Korrektur eines Worts, das
  ein Wörterbuch kennt, bleibt Vorschlag, außer Whitelist). Upstream neu geändert:
  `CapsModeUtils.java` (bekannte Abkürzung im Zustand WORD und am Textanfang = keine
  Satz-Großschreibung). Nicht geändert: `RichInputConnection`, `PointerTracker`,
  `SuggestionStripLayoutHelper`, `WordComposer`.
- Tests (Robolectric, echte `InputLogic` und `Suggest` mit gestelltem Wörterbuch, kein Gerätetest):
  `InputLogicTest` neu 6 („typed word comes first after undoing …“, „… keeps its case …, also in
  German“ mit „Hallo“ und „iPhone“, „… cursor goes back into a corrected word …“ mit gemerktem Paar
  und ohne gelernten Tippfehler, „a word the cursor goes back to without a correction …“ mit „ok“ →
  „OK“ und neuem Feld, „automatic capitals only at sentence starts …“, „a field asking for capital
  words …“); dafür laufen in `withSimilarWords` die Nachrichten des Vorschlags-Threads wirklich
  (`dispatch`), `ShadowKeyboardSwitcher.getKeyboard` liefert nur dort eine Ersatztastatur, sonst
  wie das Original. Die Leistenplätze rechnet `stripSlots` mit
  `SuggestionStripLayoutHelper.getPositionInSuggestionStrip` aus (die Ansicht hat im Test keine
  Breite). `SuggestTest` neu 1 (Deutsch „essen“/„Esen“/„berlin“, Englisch „may“ und „i“ → „I“),
  `TypedWordFirstTest` neu 5 (Speicher, Grenze, Anordnung, unberührte Fälle, Abkürzungen Deutsch und
  Englisch mit Gegenproben „usw.“, „etc.“, „Plan B.“, „Max M.“). Gelaufen gezielt
  `:app:testDebugUnitTest` mit `*InputLogicTest`, `*SuggestTest`, `*TypedWordFirstTest`,
  `*CorrectionMemoryTest`, `*TypoMemoryTest`, `*StringUtilsTest` (`--max-workers=1`, 2026-11-14):
  272 Tests, 2 Fehler = geerbt (`InputLogicTest.insertLetterIntoWordHangulFails`,
  `StringUtilsTest.detectEmojisAtEndFails`). In einem von drei Läufen von `*InputLogicTest` scheiterte
  zusätzlich `typedWorkflowChoiceIsDroppedWhenTheEditorChanges` (Senden-Knopf sichtbar), allein und
  in den anderen Läufen grün; berührt keinen geänderten Code, vermutlich reihenfolgeabhängig.
  Nicht gelaufen: `tools/dev.py agent-tests`, volle Suite, Build, Gerätetest. `docs_sync --check`:
  grün.
- Bekannte Grenzen: Ohne weiteren Vorschlag steht das getippte Wort allein in der Mitte. Wird Text
  vor dem Wort geändert, passt der gemerkte Start nicht mehr (kein Fehlgriff, nur kein getipptes
  Wort vorne). Ein Tipp auf die getippte Form nach dem Zurücksetzen der Schreibmarke nimmt das
  korrigierte Wort nicht aus dem Lernverlauf (`unlearnWord` nur bei der Löschtaste wie bisher).
  Abkürzungsliste fest (Deutsch/Englisch), „usw.“/„etc.“ bewusst nicht.

## Etappe 50 (Dynamische Trefferzonen mit Szeneprofilen)

- Auftrag: `docs/ROADMAP.md` → 50 (Owner, 2026-11-14). Drei Schichten: Basis (bleibt wie
  kalibriert), Kurzzeit, Szenen; Beförderung nur bei Regelmäßigkeit. Annahme des Leads (vom Owner
  änderbar): eigener Schalter „Dynamisch anpassen (Szenen)“, Standard aus, wirkt nur mit
  „Trefferzonen an mich anpassen“ (Lernen bleibt opt-in, `CLAUDE.md`); ausgeschaltet bleibt das
  Verhalten von 22a/22b genau gleich.
- Schwellen: Kurzzeit global Gewicht 0,1, je Region (3 × 3) 0,2, Schritt ≤ 0,03 Taste, Schicht und
  Summe ≤ ⅓ Taste; Ende nach 5 Minuten Pause oder in anderer App, anderes Feld halbiert. Szene am
  Sitzungsende ab 40 Tipps: Verschiebung ≥ 0,12 Taste, ≥ 3 Standardfehler, ≥ 75 % der 20er-Blöcke
  gleichgerichtet; oder Streuung ≥ 1,3 × (+0,04); oder Korrekturrate ≥ 1,5 × (+0,04). Erkennung
  nach 15 Tipps (Abstand ≤ 4). Beförderung: ≥ 5 Sitzungen innerhalb 0,08 Taste, ≥ 3 Tage,
  ≥ 2 Tageszeiten, Schritt = halbe Abweichung, ≤ 0,06 Taste je Achse; vorherige Basis gesichert.
- Gegen Rückkopplung: nur Wörter, die `InputLogic` schon an `learnFromWord` gibt; Berührungen
  zählen erst nach der nächsten Taste (Löschtaste oder Feldwechsel verwirft); nur Berührungen in der
  normalen Taste. Gegenprobe (2026-11-14, im Arbeitsbaum, nicht committet): mit `IN_KEY` = 0,9
  bleibt in `movedZonesDoNotTeachThemselves` eine Restverschiebung von 0,053, mit 0,5 von 0,013;
  die Grenze im Test ist 0,03.
- Neue Tests: `CT/TouchDynamicsTest.kt` (10: Anpassung in 30 Tipps, Pause/App/Feld, kurze
  Sitzungen ändern nichts, Szene entsteht und wird wiedererkannt, Szene aus Korrekturrate,
  Beförderung nur mit Regel (ein Tag / nur abends / Regel erfüllt), Grenzen und Schrittweite,
  Rückkopplungs-Simulation, Löschen, Tageszeiten), `AT/TouchZonesDynamicTest.kt` (5: Hook mit
  `KeyDetector` und Basis unverändert, gelöschtes Wort lernt nichts, Passwort/sichtbares
  Passwort/ohne Lernen/Inkognito/Pause/gesperrt/Schalter aus, Beförderung über `TouchModel` samt
  Neustart und Zurücksetzen, Kalibrierung sichert die vorherige Basis), `AT/TouchScenesScreenTest.kt`
  (3: Schalter, Szene in Worten und löschen, Zurücksetzen). `TouchModelTest` unverändert.
- Gelaufen (2026-11-14, `--max-workers=1`): `./gradlew :agent-core:test` grün (381 Tests);
  `:app:testDebugUnitTest --tests 'helium314.keyboard.agent.TouchModelTest'
  --tests 'helium314.keyboard.agent.TouchZonesDynamicTest' --tests
  'helium314.keyboard.agent.TouchScenesScreenTest'` grün (17); `SettingsAreaGesturesTest`,
  `SymbolSettingsScreenTest`, `InputLogicTest` grün bis auf das geerbte
  `InputLogicTest.insertLetterIntoWordHangulFails`. `docs_sync --check` grün. Nicht gelaufen:
  volle Suite, Agent-Tests über `tools/dev.py`, Lint, Build, Gerätetest (Schwellen mit echten
  Fingern, Tageszeiten, Akku).
- Engine-Änderungen (Prüfer Pflicht): `KeyboardActionListenerImpl.kt` (`onCodeInput`: ein Aufruf
  `TouchModel.onKeyInput`), `LatinIME.java` (`onStartInputViewInternal`: `TouchModel.load` mit
  `editorInfo`, `restarting`). `KeyDetector.java` unverändert (rechnet über `TouchModel.adjust`
  jetzt Basis plus Kurzzeit). `InputLogic`, `RichInputConnection`, `PointerTracker` unverändert.

## Nachprüfung 50 (Prüfer-Befunde zu Etappe 50, 2026-11-14)

- M1: Ausgeschaltet („Dynamisch anpassen“ oder „Trefferzonen an mich anpassen“) verwirft der
  nächste Feldstart die Sitzung mit `TouchDynamics.discardSession` statt sie auszuwerten (keine
  Szene, keine Beobachtung, keine Beförderung, kein Bezug). Die Gesten-Seite setzt
  `TouchModel.dynamic` jetzt mit. Tests `switchedOffTheNextFieldLooksAtNothing` (beide Schalter,
  fünfte Sitzung wäre befördert worden), `aDiscardedSessionLeavesNoTrace`,
  `SettingsAreaGesturesTest.switchesWriteTheRealPreferences` (erweitert).
- M2: Entscheidung: Szenen werden umgerechnet, nicht gelöscht. `replace` und `restorePrevious`
  rechnen die Szenen je Region um die mittlere Änderung der angewandten Basis zurück
  (`TouchModel.rebaseScenes` → `TouchDynamics.rebase`), Beobachtungen werden geleert, die laufende
  Sitzung verworfen (P181). Dialogtext und ANLEITUNG angepasst. Tests
  `aCalibrationAndTheStepBackMoveTheScenesWithTheBase`, `aMovedBaseRebasesScenesAndDropsObservations`.
- N1: `onKeyInput(code, isKeyRepeat, keyboard)`; Wiederholungen werden ignoriert, eine gehaltene
  Löschtaste zählt einmal. N2: `UNDO` und `CLIPBOARD_CUT` verwerfen das Wort wie die Löschtaste
  (zählen aber nicht als Löschtaste). Versetzen der Schreibmarke ohne Feldstart: dokumentiert,
  Engine unverändert. Test `aHeldDeleteCountsOnceAndUndoOrCutDropTheWord`.
- N3: `updateRegions` vergleicht die Geometrie der Buchstabentasten; ein Wechsel (Drehung, Einhand,
  geteilt, Layout) beendet die Sitzung und verwirft das wartende Wort, Umschalten und Symbole
  nicht. Ausrichtung bewusst kein Szenemerkmal (P182). Test `anotherKeyboardGeometryEndsTheSession`.
- N4: Szenezahlen mit 3 Nachkommastellen gespeichert; geschrieben wird weiterhin nur bei Änderung
  (Erkennung, Sitzungsende, Löschen, Kalibrierung), nicht je Taste; kein Hintergrund-Thread (die
  Datei ist klein, `apply()` schreibt asynchron). Test `storedSceneNumbersAreRounded`.
- N5: Beförderung nur für trainierte Buchstaben (Basis ab 15 oder 15 bestätigte Kurzzeit-Berührungen
  seit Prozessstart); nie getippte behalten Basis und Anzahl. Dokumentiert: die Basis nähert sich
  einer dauerhaften Abweichung nur bis etwa 0,12 Taste. Test: erweiterte Beförderungsprüfung
  (`samples('e')` = 45, `y` bleibt 0).
- N6: Nach einer Beförderung werden die übrigen Beobachtungen wie die Szenen verschoben. Test
  `observationsLeftAfterAPromotionMoveWithTheBase`.
- N7: „Auf vorherige Kalibrierung zurücksetzen“ erscheint nur, wenn `previous` existiert
  (`TouchScenesScreenTest`: vorher `assertIsNotEnabled`, jetzt `assertDoesNotExist`); ANLEITUNG
  erwähnt den Knopf auch außerhalb des Abschnitts „Dynamisch anpassen“.
  `restrictedFieldsAndStatesTeachAndMoveNothing` prüft zusätzlich die Kurzzeitschicht selbst
  (alle Regionen und global null), die Basis (`samples`, `baseOffsetY`) und dass im nächsten
  normalen Feld nichts nachwirkt (keine Szene, keine Beobachtung). Keine Assertion entfernt.
- Gelaufen (2026-11-14, `--max-workers=1`): `./gradlew :agent-core:test` grün (417 Tests, davon
  `TouchDynamicsTest` 13); `:app:testDebugUnitTest` mit `TouchModelTest`, `TouchZonesDynamicTest`,
  `TouchScenesScreenTest`, `SettingsAreaGesturesTest` grün (33). Nicht gelaufen: volle Suite,
  `InputLogicTest`, Agent-Tests über `tools/dev.py`, Lint, Build, Gerätetest.
- Engine-Änderungen (Prüfer Pflicht): `KeyboardActionListenerImpl.kt` (`onCodeInput`: Aufruf
  `TouchModel.onKeyInput(primaryCode, isKeyRepeat, keyboardSwitcher.keyboard)`). `InputLogic`,
  `RichInputConnection`, `PointerTracker`, `KeyDetector`, `LatinIME` unverändert.

## Etappe 45 (Feld- und Kontext-Vorschläge)

- Auftrag (Lead, ROADMAP 45, Owner 2026-11-14): aus Feld (Typ, Hinweistext, App) und Text vor der
  Schreibmarke passende Chips; E-Mail, Einmalcode, IBAN, Telefon, Adresse, Name; Kontext über eine
  lokale Gewichtung mit Schwelle; Kontext-Lernen als begrenzte Gewichte; Karte „Meine Daten“
  verschlüsselt. Gebaut in einer Arbeitskopie, kein Prüfer-Review, kein Gerätetest.
- Vorher geprüft: `EditorInfo` hat `hintText`, `label`, `fieldName`, `packageName`, aber auf keiner
  API-Stufe Autofill-Kennungen (P161). `AndroidEditorAdapter.insert` lehnt Nicht-Text-Felder ab
  (`ExecutionPolicy.editorDenial` → `UNSUPPORTED_EDITOR`); der erste Versuch, leere Felder darüber
  zu füllen, schlug im echten Host fehl (`InputLogicTest`), deshalb `LatinIME.onTextInput` wie die
  Zwischenablage-Ansicht (P162). Speicher wie die KI-Schlüssel (`EncryptedCredentialStore`,
  `credentialContext`, `credentialAccessAllowed`, P134/P145).
- `./gradlew :agent-core:test`: grün, 390 Tests, 0 Fehler, darin neu `suggest.ContextSuggestTest` 21
  (Owner-Beispiel „guck mal, hier dieses Video “ mit Link von vor 2 Minuten, en/es; Hinweise je
  Sprache und Art; nichts mitten im Wort, Datenwort nur mit Doppelpunkt, keine Teilwörter, „E-Mail“
  ≠ „Mail“; Datenhinweise direkt vor der Schreibmarke, Nähe senkt Link-Hinweise; schon Getipptes
  nicht noch einmal; nur die letzte Zeile; Stärke begrenzt; Frische fällt und endet nach
  10 Minuten; ein frischer Clip allein erreicht die Schwelle nie, auch nicht mit Messenger und
  Kurznachricht; alter Link plus schwacher Hinweis nicht, Browser senkt, Messenger hebt; eigene
  Daten mit klarem Hinweis, Art muss passen; App-Kategorien; Feldziele aus Typ, Hinweis, Feldname
  und Autofill-Kennung; „Postleitzahl / ZIP code“, „Gutscheincode“, „Promo code“, „Código postal“,
  „Benutzername“, „Passwort für E-Mail“, „Vorname“ ohne Ziel; `fits`; Annehmen hebt, Übergehen
  senkt, beides innerhalb ±0,4, unbekannte Kennungen werden nicht gespeichert; Übergehen kleiner
  als Annehmen; gelernte Gewichte entscheiden Grenzfälle; Halbwertszeit 30/60 Tage, rückwärts
  laufende Uhr; Kodierung nur Kennungen und Zahlen; kaputte Daten werden verworfen).
- `./gradlew :app:testDebugUnitTest` gezielt (`--max-workers=1`, 2026-11-14): neu
  `FieldSuggestionChipsTest` 18 (Feldklassen, Passwort/URI/Datum nie; leeres E-Mail-Feld: eigene
  und frisch kopierte Adresse, Tipp fügt genau einmal ein, zweiter Tipp nichts, vor dem Tipp
  nichts; angefangener Text wird ersetzt, fremder Text und Text hinter der Schreibmarke ohne Chip;
  Code-Feld (Hinweis, Feldname, Telefonfeld mit „OTP“) bekommt nur die Ziffern aus dem Tresor,
  nie eigene Daten; Zahlenfeld nur bis 3 Minuten, mit Hinweis bis zum Ablauf nach 10 Minuten;
  Telefon, IBAN, Name, Adresse; ohne Meine Daten nur frische Clips; Owner-Beispiel im Messenger
  und in einer anderen App, ein Tipp hängt den Link genau einmal an; ohne Hinweis, mitten im Wort,
  alter Link, schon getippter Link: nichts; „meine Nummer ist “, „IBAN:“ (Leerzeichen nach dem
  Doppelpunkt), „Adresse: “, „meine Mail ist “; „Code: “ mit frischem Code; Geheimnisse aus dem
  Tresor in keinem Feld und nach keinem Hinweis; Passwortfelder, Inkognito, No-Learning auch für
  Telefon- und Zahlenfelder, gesperrt, ohne Einstellungen: nichts, Meine Daten dabei nicht
  gelesen; Schalter aus; Lernen: Tipp hebt, Übergehen senkt einmal, gespeichert nur
  „v1;Tag;id:ART=Zahl“ ohne Text, Link oder App, Löschen leert; mit Lernen aus oder pausiert
  nichts gespeichert; gelernte Gewichte entscheiden „hier “, mit Lernen aus nicht benutzt;
  Beschriftungen), `MyDataStoreTest` 5 (anfangs leer, verschlüsselt in CE `noBackupFilesDir`,
  kein Wert im Klartext der Datei oder in den Tastatureinstellungen, bereinigt; gesperrt: `LOCKED`
  vor jedem Krypto-Aufruf, Zwischenspeicher verworfen und nicht neu gelesen; Speichern und Löschen
  verwerfen ihn, leere Daten löschen die Datei; manipulierte und zu große Datei: nicht lesbar,
  bleibt liegen; Grenzen, `toString` ohne Werte), `MyDataScreenTest` 2 (Eintragen und Speichern
  verschlüsselt; gesperrt nur der Hinweis, kein Store erzeugt, Schalter bedienbar),
  `ClipVaultTest` 17 (neu: Chip „Meine Daten“ am Ende der letzten Reihe öffnet die Seite, ist
  kein Tresor-Eintrag; Hilfsfunktion `chips` lässt ihn aus, keine Assertion geändert),
  `InputLogicTest` 190 mit neu `fieldSuggestionChipInsertsOnlyOnATapAndOnce` (echter Host: leeres
  E-Mail-Feld, Hinweis „meine Nummer ist “, je zweiter Tipp ändert nichts, Passwortfeld ohne Chip);
  einziger Fehler `insertLetterIntoWordHangulFails` (geerbte Baseline). Unverändert grün:
  `FormatGuardChipsTest` 12, `KeyboardCommandsTest` 5, `EmojiMixCommandTest` 13, `ClipCommandTest`
  10, `UrlCommandTest` 13, `SettingsStartScreenTest` 10, `SettingsBarAreaTest` 11 (erwartete
  Zeilenliste um `agent_my_data` ergänzt), `SettingsTypingAreaTest` 9, `SymbolSettingsScreenTest`
  13, `EncryptedCredentialStoreTest` 11.
- Nicht gelaufen: `tools/dev.py agent-tests`, volle Suite und Build (geteilte Maschine, Anweisung
  des Leads), Lint. Keine Engine-Datei geändert (`InputLogic`, `RichInputConnection`,
  `PointerTracker`, `LatinIME` unverändert); geändert nur die schon geänderte Upstream-Datei
  `SettingsNavHost.kt` (Ziel). `docs_sync --check`: grün.
- Offen: Prüfer-Review (`KeyboardCommands`-Host `insertAtCursor` über `onTextInput`,
  `ClipVaultSection`), Gerätetest (Code-Felder echter Apps und Browser, Hinweistexte in Messengern,
  TalkBack auf den Chips, Keystore beim ersten Speichern, Seite bei gesperrtem Gerät).
## Nachprüfung 48 (Prüfer-Befunde zu Etappe 48, 2026-11-14)

- Behoben: M1 `Suggest.guardAllows` blockierte eine reine Groß-/Klein-Korrektur, sobald irgendein
  Wörterbuch die getippte Form kannte, auch eine andere aktive Sprache („hand“ → „Hand“, „name“,
  „bank“ mit Deutsch und Englisch); jetzt nur Hauptwörterbuch oder Lernverlauf der Sprache der
  Korrektur mit genau der getippten Form. N1 `TypedWordFirst.committedAt` (aus `commitChosenWord`)
  verwirft alte Einträge am selben Start. N2 Abkürzungen: entfernt „fr“, „jh“, „jhd“ (Deutsch),
  „ms“, „st“, „jr“, „sr“ (Englisch), neu „bzgl“, „insb“, „insbes“, „lt“, „gem“, „mind“, „allg“,
  „ugs“, „eigtl“. N3 Einzelbuchstaben-Regel nur nach einem Kleinbuchstaben mit Punkt („z. B“,
  „o. ä“), nicht bei Initialen („J. K. Rowling“). N4 `reverted` merkt nur, wo eine Korrektur gemerkt
  ist (Gestenwörter werden nie gemerkt, also auch nicht zurückgenommen); keine Änderung in
  `revertCommit` nötig.
- N5 dokumentiert (P158, bekannte Grenzen): ohne weiteren Vorschlag steht das getippte Wort breit
  in der Mitte; Leisten mit 5 Plätzen nicht geprüft; RTL rechts, ohne Gerätetest.
- Tests: `SuggestTest` Fall „a known word never gets only another case“ erweitert („hand“ mit
  deutschem „Hand“ und englischem „hand“ → automatisch; beides deutsch → nicht), `TypedWordFirstTest`
  neu 2 (Abkürzungsliste mit Gegenproben und Initialen; Commit am selben Start, Rücknahme ohne
  gemerkte Korrektur), `InputLogicTest` neu 1 („a correction of text deleted since …“: gegen den
  Stand ohne `committedAt` rot, mit grün). Gelaufen gezielt `*InputLogicTest`, `*SuggestTest`,
  `*TypedWordFirstTest`, `*CorrectionMemoryTest`, `*TypoMemoryTest`, `*StringUtilsTest`
  (`--max-workers=1`, 2026-11-14): 289 Tests, 2 Fehler = geerbt
  (`InputLogicTest.insertLetterIntoWordHangulFails`, `StringUtilsTest.detectEmojisAtEndFails`).
  Nicht gelaufen: volle Suite, Build, Gerätetest. Erneute Prüfung durch einen Prüfer steht aus.

## Etappe 49 (Korrekturen merken über das ganze Feld)

- Auftrag (Lead, ROADMAP 49, Regeln mit dem Owner vom 2026-11-14): spätere Korrekturen im ganzen
  Feld; Tippfehler nur bei Nicht-Wort → echtem Wort mit Vertipp-Schritten (Nachbartaste auf dem
  aktuellen Layout, fehlender/doppelter/vertauschter Buchstabe, Umlaut/Akzent; ≤ 2, bis 4 Buchstaben
  ≤ 1), nur dieses Wort geändert; alles andere Überarbeitung; Kandidat → gelernt beim zweiten
  Vorkommen an anderer Stelle; zurückgenommene Autokorrektur sofort „nicht mehr“; Seite „Was ich mir
  gemerkt habe“. Gebaut in einer Arbeitskopie, kein Prüfer-Review, kein Gerätetest.
- Gebaut: `C/CorrectionRules.kt` (`diff`, `classify`, `mistypingSteps`, `KeyAdjacency`),
  `K/agent/FieldCorrections.kt` (`FieldCorrectionWatcher`, `FieldCorrectionMemory`, Datei
  `agent_field_corrections`), `TypoMemory.meantWord`/`forget` greifen auf die gelernten Paare zu,
  `K/settings/screens/AgentMemoryScreen.kt` mit Ziel `agent_memory`, Zeile auf „Tippen & Korrektur“,
  „Gelernte Wörter löschen“ leert mit. Engine: `InputLogic.java` (Feld `mFieldCorrections`;
  `startInput` → `start`; `commitChosenWord` und `onUpdateSelection` → `checkpoint`;
  `commitCurrentAutoCorrection` → `autoCorrected`; `untrusted` in `onCodeInput` bei Markierung,
  `onTextInput`, `paste`, `applyGenerated`). Nicht geändert: `RichInputConnection`, `Suggest.kt`,
  `PointerTracker`, `KeyDetector`, `TouchModel`. Kein bisher unverändertes Upstream-File neu
  geändert.
- Tests (Robolectric, echte `InputLogic` mit gestelltem „echt“ über `isValidSpellingWord`, kein
  Gerätetest): `CorrectionRulesTest` neu 10 (Hsus → Haus, teh → the, fur → für, Haaus, Strase →
  Straße, Montag → Freitag, das → dass, Kurzwörter, Layout-Nachbarn, Diff mit einem/mehreren
  Wörtern, gelöschtem Rest, abgeschnittenem Text), `FieldCorrectionMemoryTest` neu 5 (zweites
  Vorkommen, gleiche Stelle bleibt Kandidat, andere Bedeutung, ohne Erlaubnis nichts, Löschtaste
  vergisst, Seite zeigt/übernimmt/löscht), `InputLogicTest` neu 6 („stage 49“: Korrektur nach fünf
  weiteren Wörtern und gelernt im zweiten Feld, nach Löschen über Leerzeichen, über einen Vorschlag
  im Wort und für ein per Vorschlag abgeschicktes Wort, Überarbeitungen inkl. das → dass,
  Montag → Freitag, Markieren und Überschreiben, spätere Rücknahme einer Autokorrektur, nichts bei
  Passwortfeld, Inkognito, Pause, Schalter aus). Gelaufen gezielt `:agent-core:test --tests
  '*CorrectionRulesTest*'` (10/10) und `:app:testDebugUnitTest` mit `*InputLogicTest`,
  `*SuggestTest`, `*TypoMemoryTest`, `*CorrectionMemoryTest`, `*TypedWordFirstTest`,
  `*FieldCorrectionMemoryTest`, `*SettingsTypingAreaTest`, `*SettingsStartScreenTest`,
  `*SymbolSettingsScreenTest` (`--max-workers=1`, 2026-11-14): 300 Tests, 1 Fehler = geerbt
  (`InputLogicTest.insertLetterIntoWordHangulFails`). Nicht gelaufen: `tools/dev.py agent-tests`,
  volle Suite, Lint, Build, Gerätetest. `docs_sync --check`: grün.
- Bekannte Grenzen: Ein zusätzlicher Buchstabe zählt nur, wenn er einen Nachbarbuchstaben
  verdoppelt (Regel „doppelt“ wörtlich). Ein Wort, das nur im Lernverlauf steht, zählt als echt.
  Der Vergleich deckt je 2000 Zeichen um die Schreibmarke ab. Eine Korrektur, nach der ohne
  Prüfpunkt markiert oder eingefügt wird, geht verloren (vorsichtig). Mehrere Sprachen: „echt“ in
  irgendeiner aktiven Sprache. Rücknahme-Regel erkennt das Paar am Wort, nicht an der Stelle.

### Nachprüfung 49 (Prüfer-Befunde zu f983cd7 und eine Regression aus 76155ed)

- Behoben (Arbeitskopie, Lead-Auftrag 2026-11-14), Engine-Änderung in `InputLogic.java` eng an den
  49er-Haken, erneutes Prüfer-Review nötig:
  - M1: In `onUpdateSelection` kein Vergleich und kein neuer Stand, solange ein Wort komponiert wird
    (`checkpoint(..., composing = mWordComposer.isComposingWord())`, der alte Stand bleibt);
    Vervollständigungen zählen nicht (`isCompletion`, ohne Akzente Anfang des neuen Worts, wie
    `TypoMemory.confirm`); die Autokorrektur des gerade abgeschickten Worts zählt nicht
    (`commitChosenWord` gibt bei `COMMIT_TYPE_DECIDED_WORD` getippt → abgeschickt mit).
  - M2: Bei `hasSlowInputConnection()` liest der Prüfpunkt nichts und verwirft den Stand; gelesen
    werden höchstens 1024 Zeichen davor (Zwischenspeicher-Größe) und 300 danach. Das Verschieben
    des Commit-Prüfpunkts hinter `endBatchEdit` ist nicht gemacht.
  - N1: Nur ein Wort, das eigene Eingabe seit dem Stand berührt hat (`ownEdit` aus `onCodeInput`
    für Zeichen und Löschtaste, aus `onPickSuggestionManually`); `UNDO`/`REDO` rufen `untrusted`.
  - N2: Nachbartasten aus der letzten Buchstaben-Tastatur, zwischengespeichert je `Keyboard`.
  - N3: Ist das Feld vor und nach der Schreibmarke leer, beginnt eine neue Feldnummer.
  - N4 (Regression aus 76155ed): `Abbreviations.isAbbreviation` kennt die Buchstabenpaare z. B.,
    d. h., u. a., o. ä., s. o., s. u., v. a., u. U., i. d. R. in jeder Schreibung; Initialen nur,
    wenn beide Buchstaben groß sind und kein bekanntes Paar („Z. B. das“ klein, „J. K. Rowling“ groß).
- Nur festgehalten (N5–N7):
  - N5: „Echt“ schließt den Lernverlauf ein; ab wann HeliBoard ein dort gelerntes Wort als gültig
    meldet (Schwelle von `isValidSpellingWord` für den Verlauf), ist nur auf einem Gerät prüfbar.
    Ein oft getippter Tippfehler kann so „echt“ werden und wird dann nie gelernt. Kein Gerätetest.
  - N6: „fur“ gilt mit Deutsch und Englisch als echt (jede aktive Sprache zählt), also wird
    „fur“ → „für“ hier nie gelernt. Das ist die dokumentierte Annahme (ROADMAP 49) und bewusst
    anders als 48 M1, wo `guardAllows` eine andere Sprache nicht zählen lässt.
  - N7: Gelernte Feld-Paare laufen über `TypoMemory.meantWord` und `Suggest.applyLearnedCorrection`,
    nicht über `guardAllows`; sie greifen nur, wenn das Getippte kein Hauptwörterbuch-Wort ist. Die
    48-Regel gegen reine Groß-/Klein-Korrekturen betrifft sie nicht, weil `classify` eine reine
    Groß-/Klein-Änderung als Überarbeitung wertet.
- Tests (Robolectric, echte `InputLogic`, kein Gerätetest): `InputLogicTest` neu 6 („stage 49“:
  kein Stand im komponierten Wort, Vervollständigung, eigene Autokorrektur, langsamer Zugang,
  Änderung der App und Rückgängig, vom Messenger geleertes Feld), `FieldCorrectionMemoryTest` neu 1
  (AZERTY-Nachbarn bleiben bei Symbolen), `TypedWordFirstTest` neu 1 (Buchstabenpaare am
  Satzanfang, Initialen). Gegenprobe: mit zurückgenommenen Korrekturen in `FieldCorrections.kt`
  schlagen die neuen 49er-Tests fehl (6 InputLogicTest-Fälle und der Nachbartasten-Test; Rückgängig über den UNDO-Haken in InputLogic nicht einzeln gegengeprüft). Gelaufen (`--max-workers=1`, 2026-11-14):
  `:agent-core:test` 417/417; `:app:testDebugUnitTest` mit `*InputLogicTest`,
  `FieldCorrectionMemoryTest`, `TypedWordFirstTest`, `*SuggestTest`: 270 Tests, 1 Fehler = geerbt
  (`InputLogicTest.insertLetterIntoWordHangulFails`). Nicht gelaufen: `tools/dev.py agent-tests`,
  volle Suite, Lint, Build, Gerätetest.

### Zweite Nachprüfung 49 (Prüfer-Befunde zu 4f2c8b9)

- Behoben (Arbeitskopie, Lead-Auftrag 2026-11-14). Engine-Änderung eng, erneutes Prüfer-Review
  nötig: `InputLogic.java` (neue Methode `checkpointFieldCorrectionsBeforeOwnCursorMove`, eine
  Zeile in `commitChosenWord`), `KeyboardActionListenerImpl.kt` (je ein Aufruf in
  `onMoveCursorHorizontally` und `onKeyDown`). `RichInputConnection`, `PointerTracker` unverändert.
  - 1 (mittel): Leertasten-Wischen setzt die Schreibmarke selbst, die Auswahl-Meldung ist erwartet
    und kehrte vor dem Prüfpunkt zurück; eine Korrektur nach dem Zurückwischen ging verloren
    (mehrere Änderungen) und der eigene Bereich wurde zu breit. Jetzt Prüfpunkt nach `finishInput`
    und vor `setSelection` (`composing` wie in `onUpdateSelection`).
  - 2 (gering): Das Paar getippt → abgeschickt geht nur noch an den Prüfpunkt, wenn sich das Wort
    genau geändert hat (`FieldCorrectionWatcher.keyboardCorrectionFrom`). Wirkung heute ohne
    sichtbare Folge: reine Groß-/Klein-Paare speichern `RejectedCorrections.remember` und
    `TypoMemory.confirm` ohnehin nicht (Regel aus 48); „als zurückgenommen gemerkt“ wird „ich“ ←
    „Ich“ darum auch jetzt nicht, nur die Rücknahme-Erkennung wird erreicht.
  - 3 (gering): Kein `Keyboard` mehr fest gehalten (sein `KeyboardId` hält `editorInfo` mit
    `initialSurroundingText`): nur die `KeyAdjacency` und eine `WeakReference` als Kennung, in
    `start` geleert, an jedem Prüfpunkt mit der gezeigten Tastatur verglichen (neu gebaut nur bei
    einer anderen Buchstaben-Tastatur).
  - 4, 5 (Doku): P183 sagt jetzt, dass eine Änderung der App am gerade getippten Wort (Rand
    eingeschlossen) zählt, dass Gestenschreiben kein `ownEdit` ruft und Korrekturen über das
    Rechtschreib-Menü der App (`SuggestionSpan`) nie zählen. Hardware-Strg+Z/Strg+Y rufen jetzt
    `untrusted` (vor der Abfrage „Hardware-Tastatur“, beide Wege geben die Taste an die App).
    Strg mit Z auf der Bildschirmtastatur nicht geprüft.
  - 6 (Doku): FEATURES/ANLEITUNG nennen die Grenze: fehlende Endbuchstaben („nich“ → „nicht“) sind
    eine Vervollständigung und werden nie gelernt.
- Tests (Robolectric, echte `InputLogic`, kein Gerätetest): `InputLogicTest` neu 2 (Korrektur nach
  Zurückwischen mit der Leertaste wird Kandidat, Änderung der App dazwischen nicht; Hardware-Strg+Z
  verwirft den Stand), `FieldCorrectionMemoryTest` neu 2 (Layoutwechsel AZERTY → QWERTY, Symbole
  behalten QWERTY, `start` vergisst das Layout, kein Feld vom Typ `Keyboard` oder `EditorInfo`;
  `keyboardCorrectionFrom` mit „ich“/„ich“). Gegenprobe: ohne die beiden Aufrufe in
  `KeyboardActionListenerImpl.kt` schlagen beide neuen `InputLogicTest`-Fälle fehl. Der Test zu
  Befund 2 prüft nur die Hilfsfunktion (keine sichtbare Wirkung, siehe oben). Gelaufen
  (`--max-workers=1`, 2026-11-14): `:app:testDebugUnitTest` mit `*InputLogicTest`,
  `FieldCorrectionMemoryTest`, `SpaceGesturesTest`: 241 Tests, 1 Fehler = geerbt
  (`InputLogicTest.insertLetterIntoWordHangulFails`); `:agent-core:test` 438/438. `docs_sync --check`: grün. Nicht gelaufen:
  `tools/dev.py agent-tests`, volle Suite, Lint, Build, Gerätetest.

## Nachprüfung 44/45 (Prüfer-Befunde zu Etappe 44 und 45, 2026-11-14)

- B1 (mittel): Eine im Passwortfeld kopierte PIN kam über den Live-Clip als Code in Code- und
  Zahlenfelder und als Leisten-Chip. Jetzt fragt `ClipVault.isSecretClip` (Markierung,
  `SensitiveClips`, vom Tresor als Geheimnis sortierte Kopierzeit, Inhalt); `SensitiveClips` hört
  auch mit Verlauf aus mit. Tests `ClipVaultTest.aPinCopiedInAPasswordFieldIsNeverOfferedWithTheHistoryOn`
  /`…Off`, `aClipTheVaultSortedAsSecretIsNotOfferedFromTheLiveClip`.
- B2 (mittel): Code-Schlüsselwörter nur als ganze Wörter bzw. benannte Komposita; eine Nachricht mit
  Code bleibt Verlaufstext (`ClipClass.inText`, `keepsOutOfHistory`), nachträglich gelöscht werden
  nur Geheimnisse. Negativtests „noch einmal … 2023114“, „Bestätigung Ihrer Bestellung 12345678“,
  „Barcode 40071234“, „QR-Code“, „Promo-Code“ in `ClipGuardTest.codeWordsCountOnlyAsWholeWords`.
  Geänderte Erwartungen (gewolltes neues Verhalten, keine Abschwächung): `ClipVaultTest` erwartet
  „Dein Code lautet 551177“ und ein altes „123456“ jetzt im Verlauf; der Inkognito-Fall prüft einen
  Code allein.
- B3 (mittel): `SensitiveClips` nur im Speicher (je Prozess gesalzen); die alte DE-Datei wird einmal
  zum Aufräumen des Verlaufs benutzt und gelöscht, nur Hashes angehefteter Einträge bleiben.
  `ClipCommandTest` erwartet jetzt keine Datei mehr (statt „Hash vorhanden“) und
  `theOldHashFileIsUsedOnceAndThenOnlyForPinnedEntries`. Speicher statt Keystore-HMAC, weil
  solche Clips seit Etappe 44 nie in den Verlauf kommen.
- B4 (mittel): `LatinIME.onPrivateTextInput`, `InputLogic.markPrivateText`/`nearPrivateText`
  (Tresor, Code-Chip im leeren Feld, ersetzte Werte über `Host.markPrivate`). Test
  `InputLogicTest.aPrivateTextIsNeverComposedShownOrLearned` (Leerzeichen, nächstes Wort,
  Rücktaste: nichts gelernt, nichts komponiert, Leiste ohne Geheimnis; ein normales `onTextInput`
  wird weiter komponiert).
- B5: Felder von „Meine Daten“ mit `NO_LEARNING_FIELD`, `InputAttributes` zählt es
  (`FieldSuggestionChipsTest.theMyDataFieldsAreNoLearningFields`; dass Compose die Kennung wirklich
  in `EditorInfo.privateImeOptions` schreibt, ist nicht getestet).
- B6: Passwort-Wort plus Wert = `SECRET` (`aPasswordWordWithItsValueIsASecret`; die alte Erwartung
  „Das WLAN-Passwort lautet Sommer2024“ = Text ist bewusst auf Geheimnis umgestellt).
- B7: `MyDataCache.drop` in `onFinishInputView` (`hidingTheKeyboardDropsTheDecryptedData`).
- B8: `secure_delete` + `wal_checkpoint(TRUNCATE)` beim Löschen, Bereinigung auch beim Start, danach
  einmal `VACUUM`. Nur indirekt getestet (bestehende Lösch-Tests grün); ob die Seiten auf einem Gerät
  wirklich überschrieben werden, ist nicht geprüft.
- B9: Telegram-Bot-Token, Bearer-Token, Namen wie „B2B-Shop“/„Win11-Pro“/„camelCase2_x“ bleiben
  normal (`moreKeyFormatsAndIdentifiersThatAreNone`); alte Passwort-Positive grün.
- B10: P154 um den Prozess-Neustart ergänzt (bewusst offen).
- B11: Ausschalten von „Personalisierte Vorschläge“ löscht auch die Kontext-Gewichte (eine Zeile,
  ohne eigenen Test).
- B12: Tipp prüft Sperre, Inkognito, Passwortfeld und Text hinter der Schreibmarke erneut
  (`FieldSuggestionChipsTest.aTapChecksTheFieldAgainAndMarksTheValuePrivate`); schwacher Code im
  reinen Zahlenfeld bleibt (höchstens 3 min), dokumentiert.
- Geprüft: `./gradlew :agent-core:test` grün; `:app:testDebugUnitTest` mit `ClipVaultTest` (20),
  `FieldSuggestionChipsTest` (21), `MyData*` (7), `ClipCommandTest` (11), `SnipCommandTest` (24)
  grün; `InputLogicTest` (207), `FlatClipboardTest`, `ScreenshotChipTest`, `FormatGuardChipsTest`,
  `TouchModelTest`, `EmojiMixPaletteTest` grün bis auf das geerbte
  `InputLogicTest.insertLetterIntoWordHangulFails`. Nicht gelaufen: volle Suite, Agent-Tests über
  `tools/dev.py`, Lint, Build, Gerätetest.
- Engine-Änderungen (Prüfer Pflicht): `InputLogic.java` (`markPrivateText`, `nearPrivateText`, je
  eine Bedingung in `restartSuggestionsOnWordTouchedByCursor` und
  `performAdditionToUserHistoryDictionary`, `onFreshEditorSession`), `LatinIME.java`
  (`onPrivateTextInput`, `markAgentPrivateText`, eine Zeile in `onFinishInputView`),
  `KeyboardActionListener.java`/`KeyboardActionListenerImpl.kt` (`onPrivateTextInput`),
  `InputAttributes.java` (bisher unverändert), `ClipboardDao.kt`. `RichInputConnection`,
  `PointerTracker` unverändert.

## Etappe 46 (Gedächtnis)

- Auftrag (Lead, ROADMAP 46, Owner 2026-11-14): Wortfolgen aus eigenem Text mit Häufigkeit,
  Frische und Verblassen, nächstes Wort und bei sicherem Treffer der ganze Rest als ein Chip;
  Umgangssprache wird nach wiederholter Nutzung eigenes Wort; Abkürzungs-Angebot nach eigenen
  Ersetzungen; Textbausteine über lokale Ähnlichkeit; Seite zum Ansehen, Anpinnen, Löschen und
  Ausschließen von Apps. Grenze des Owners: nie einen Satz in einen anderen verwandeln, nur auf Tipp
  hinter der Schreibmarke anhängen, nichts ersetzen, Autokorrektur bleibt bei einem Wort. Gebaut in
  einer Arbeitskopie neben 49 (gleiche Engine-Datei) und der `PointerTracker`-Korrektur (nicht
  berührt). Kein Prüfer-Review, kein Gerätetest.
- Schwellen gemessen (`CT/memory/PhraseMemoryTest`, synthetischer Schreiber: 12 Gewohnheiten, zwei
  davon mit zwei Enden 70:30 bzw. 60:40, sonst Zufallssätze aus einem Zipf-Wortschatz; 1500 Sätze
  über 60 Tage gelernt, 600 neue gemessen; je Wortgrenze ab dem zweiten Wort):
  - Nächstes Wort (ab 2-mal und 40 %): 493/509 = 96,9 %, 539/559 = 96,4 %, 514/544 = 94,5 % (drei
    Korpora).
  - Ganzer Rest (ab 3-mal und 70 %): in Gewohnheitssätzen 265/265, 309/309, 272/272 richtig (100 %);
    gefunden in 83 %, 89 %, 80 % der Gewohnheiten ab vier Wörtern; in Zufallssätzen kein einziger
    Rest-Chip.
  - Anteil-Schwelle über alle drei Korpora (Präzision / gefunden): 0,35: 94,0 % / 90,2 %; 0,5:
    94,3 % / 88,6 %; 0,6: 98,2 % / 83,9 %; 0,7: 100 % / 83,9 %; 0,8: 100 % / 83,9 %. Gewählt 0,7.
  - Verworfen: längere Folgen mit dem Gewicht ihrer Teile starten (wächst schneller): Rest 93,2 %,
    in Zufallssätzen 78 Rest-Chips mit 1 Treffer, nächstes Wort 54,6 % (P175).
  - Textbausteine (`CT/memory/MemoryRulesTest`, 8 Bausteine, 46 Sätze: 23 mit passendem Baustein,
    23 ohne, davon 7 mit zufällig gemeinsamen Wörtern): Schwelle 0,1: 23/26 Chips richtig; 0,2:
    21/24; 0,3: 20/23; 0,35: 17/17 richtig, 17 von 23 gefunden; 0,4: 17/17; 0,5: 15/15. Gewählt
    0,35 mit Abstand 0,08 zum zweiten (die Fehlgriffe lagen bei 0,31–0,34, also knapp; erweiterte
    Stoppwörter wie „vielen“, „dank“, „minuten“ nötig, sonst 19/24 bei 0,3).
- `./gradlew :agent-core:test`: grün, 419 Tests, 0 Fehler, darin neu `memory.PhraseMemoryTest` 10
  (Tokens und Satzteile, Wachsen erst nach Wiederholung, Halbwertszeit und Verstärkung, Verdrängen
  der schwächsten, nie angepinnte, Löschen samt längeren Folgen, Vorhersage nur nach Leerzeichen und
  nicht über Satzenden, zweideutige Fortsetzung ohne Rest-Chip, Kodierung mit kaputten Zeilen und
  rückwärts laufender Uhr, Präzisionsmessung, Schwellentabelle) und `memory.MemoryRulesTest` 7
  (eigenes Wort nach drei Nutzungen, Vervollständigung, Verblassen nach 90 Tagen, Nicht-Wörter;
  plausible Abkürzungen, keine Tippfehler; Ersetzung an Ort und Stelle mit einem und mehreren
  Wörtern, nicht bei nacheinander getippten Wörtern; Angebot genau einmal; Merkmale, Wortteil
  trifft andere Wortform, Trigramme allein nie; Präzisionsmessung der Bausteine).
- `./gradlew :app:testDebugUnitTest` gezielt (`--max-workers=1`, 2026-11-14): `InputLogicTest` 207
  mit 8 neuen Fällen: `memoryChipAppendsTheRestOnlyOnATapAndNeverReplaces` (20 echt getippte Sätze,
  gespeichert als lesbarer Text; „Danke. Ich melde mich “ zeigt „… sobald ich mehr weiss“, Zeigen
  ändert nichts, Tipp hängt an, zweiter Tipp nichts, mitten im Wort kein Chip, nach geändertem Text
  tut der alte Chip nichts, Schalter aus: kein Chip), `memoryNextWordForTheEngineAfterTwoWords`
  (nicht nach einem Wort, nicht im Passwortfeld), `memoryLearnsNothingInBlockedFields` (aus,
  Passwort, Inkognito, Feld ohne Lernen, Pause, ausgeschlossene App, gesperrt: nichts gelernt und
  nichts gespeichert; dieselbe App in einem normalen Feld lernt),
  `slangBecomesOwnAfterThreeUndoneCorrectionsAndIsOfferedWhileTyped` (drei mit der Löschtaste zurückgenommene Korrekturen, auch ohne
  „Rückgängig gemachte Korrekturen merken“; „kra“ bietet „krass“ nie als Korrektur an; gelöscht nicht
  mehr), `slangCommittedAsTypedThreeTimesBecomesOwn` (Autokorrektur aus; ein Wort des
  Hauptwörterbuchs nie), `abbreviationExpandedTwiceByHandIsOfferedOnceAsAQuietChip` („vlt“ mit der
  Löschtaste entfernt und „vielleicht“ getippt, zweimal; Chip ändert keinen Text, erst der Tipp legt
  an, danach nicht wieder), `anIgnoredAbbreviationOfferIsNotShownAgain`,
  `fittingSnippetIsAChipThatInsertsAtTheCursor` (fügt an der Schreibmarke ein, einmal; Baustein mit
  Feld hängt „@snip Termin“ an; nichts Passendes, Schalter aus, Passwortfeld: kein Chip); einziger
  Fehler `insertLetterIntoWordHangulFails` (geerbte Baseline). `SuggestTest` 42 mit neu `own word of
  the memory stays as typed and the correction stays a suggestion` (echter Suggest bei sehr starker
  Korrektur: „krass“ → „kraft“, als eigenes Wort nicht mehr, auch nicht über ein gelerntes
  Tippfehler-Paar, „kraft“ bleibt Vorschlag; nur mit Schalter; gelöscht: wieder korrigiert).
  `MemoryScreenTest` 3 (Schalter anfangs aus und gespeichert; Anpinnen und Lösen, Löschen entfernt
  auch längere Folgen und steht sofort im Speicher; eigenes Wort und ausgeschlossene App
  entfernen). Unverändert grün: `SettingsTypingAreaTest` 9 (öffnet jede Seite aus
  `SettingsAreas`), `SettingsStartScreenTest` 10, `KeyboardCommandsTest` 5,
  `FieldSuggestionChipsTest` 18, `FormatGuardChipsTest` 12, `SnipCommandTest` 24,
  `CorrectionMemoryTest` 4, `TypoMemoryTest` 9.
- Nicht gelaufen: `tools/dev.py agent-tests`, volle Suite, Build und Lint (geteilte Maschine,
  Anweisung des Leads). Engine geändert, eng: `InputLogic.java` (ein Aufruf in
  `performAdditionToUserHistoryDictionary`), `Suggest.kt` (`addFromMemory`, je eine Zeile in
  `guardAllows` und `applyLearnedCorrection`); dazu die schon geänderte Upstream-Datei
  `SettingsNavHost.kt` (Ziel). `RichInputConnection`, `PointerTracker`, `LatinIME` unverändert.
  `docs_sync --check`: grün.
- Offen: Prüfer-Review der Engine-Änderung (Pflicht), Gerätetest (Chips beim echten Tippen, wie viele
  Wiederholungen eine Folge im Alltag braucht, Abkürzung mit echter Löschtaste und ob das
  persönliche Wörterbuch des Telefons das Kürzel, auch mit mehreren Wörtern, annimmt; Dauer von
  Lesen und Schreiben bei 2000 Folgen; Bausteine mit `{cursor}`), Zusammenführen der Seite mit
  „Was ich mir gemerkt habe“ (49).
### Nachprüfung 49 (Prüfer-Befunde zu f983cd7 und eine Regression aus 76155ed)

- Behoben (Arbeitskopie, Lead-Auftrag 2026-11-14), Engine-Änderung in `InputLogic.java` eng an den
  49er-Haken, erneutes Prüfer-Review nötig:
  - M1: In `onUpdateSelection` kein Vergleich und kein neuer Stand, solange ein Wort komponiert wird
    (`checkpoint(..., composing = mWordComposer.isComposingWord())`, der alte Stand bleibt);
    Vervollständigungen zählen nicht (`isCompletion`, ohne Akzente Anfang des neuen Worts, wie
    `TypoMemory.confirm`); die Autokorrektur des gerade abgeschickten Worts zählt nicht
    (`commitChosenWord` gibt bei `COMMIT_TYPE_DECIDED_WORD` getippt → abgeschickt mit).
  - M2: Bei `hasSlowInputConnection()` liest der Prüfpunkt nichts und verwirft den Stand; gelesen
    werden höchstens 1024 Zeichen davor (Zwischenspeicher-Größe) und 300 danach. Das Verschieben
    des Commit-Prüfpunkts hinter `endBatchEdit` ist nicht gemacht.
  - N1: Nur ein Wort, das eigene Eingabe seit dem Stand berührt hat (`ownEdit` aus `onCodeInput`
    für Zeichen und Löschtaste, aus `onPickSuggestionManually`); `UNDO`/`REDO` rufen `untrusted`.
  - N2: Nachbartasten aus der letzten Buchstaben-Tastatur, zwischengespeichert je `Keyboard`.
  - N3: Ist das Feld vor und nach der Schreibmarke leer, beginnt eine neue Feldnummer.
  - N4 (Regression aus 76155ed): `Abbreviations.isAbbreviation` kennt die Buchstabenpaare z. B.,
    d. h., u. a., o. ä., s. o., s. u., v. a., u. U., i. d. R. in jeder Schreibung; Initialen nur,
    wenn beide Buchstaben groß sind und kein bekanntes Paar („Z. B. das“ klein, „J. K. Rowling“ groß).
- Nur festgehalten (N5–N7):
  - N5: „Echt“ schließt den Lernverlauf ein; ab wann HeliBoard ein dort gelerntes Wort als gültig
    meldet (Schwelle von `isValidSpellingWord` für den Verlauf), ist nur auf einem Gerät prüfbar.
    Ein oft getippter Tippfehler kann so „echt“ werden und wird dann nie gelernt. Kein Gerätetest.
  - N6: „fur“ gilt mit Deutsch und Englisch als echt (jede aktive Sprache zählt), also wird
    „fur“ → „für“ hier nie gelernt. Das ist die dokumentierte Annahme (ROADMAP 49) und bewusst
    anders als 48 M1, wo `guardAllows` eine andere Sprache nicht zählen lässt.
  - N7: Gelernte Feld-Paare laufen über `TypoMemory.meantWord` und `Suggest.applyLearnedCorrection`,
    nicht über `guardAllows`; sie greifen nur, wenn das Getippte kein Hauptwörterbuch-Wort ist. Die
    48-Regel gegen reine Groß-/Klein-Korrekturen betrifft sie nicht, weil `classify` eine reine
    Groß-/Klein-Änderung als Überarbeitung wertet.
- Tests (Robolectric, echte `InputLogic`, kein Gerätetest): `InputLogicTest` neu 6 („stage 49“:
  kein Stand im komponierten Wort, Vervollständigung, eigene Autokorrektur, langsamer Zugang,
  Änderung der App und Rückgängig, vom Messenger geleertes Feld), `FieldCorrectionMemoryTest` neu 1
  (AZERTY-Nachbarn bleiben bei Symbolen), `TypedWordFirstTest` neu 1 (Buchstabenpaare am
  Satzanfang, Initialen). Gegenprobe: mit zurückgenommenen Korrekturen in `FieldCorrections.kt`
  schlagen die neuen 49er-Tests fehl (6 InputLogicTest-Fälle und der Nachbartasten-Test; Rückgängig über den UNDO-Haken in InputLogic nicht einzeln gegengeprüft). Gelaufen (`--max-workers=1`, 2026-11-14):
  `:agent-core:test` 417/417; `:app:testDebugUnitTest` mit `*InputLogicTest`,
  `FieldCorrectionMemoryTest`, `TypedWordFirstTest`, `*SuggestTest`: 270 Tests, 1 Fehler = geerbt
  (`InputLogicTest.insertLetterIntoWordHangulFails`). Nicht gelaufen: `tools/dev.py agent-tests`,
  volle Suite, Lint, Build, Gerätetest.
## Zweite Nachprüfung 44/45 (zweiter Prüfer zu den Korrekturen, 2026-11-14)

- H1 (hoch): Nach Rücktaste oder angehängtem Buchstaben merkte `onCodeInput` das Wort an der
  Schreibmarke (`mWordBeingCorrectedByCursor`), und das Wegsetzen der Schreibmarke lernte es (die
  Prüfung lief an der neuen Stelle). Jetzt wird beim Merken entschieden: berührt das Wort einen
  privaten Text, bleibt es leer. Test `InputLogicTest.aWordTouchingAPrivateTextIsNotLearnedWhenTheCursorLeaves`
  (Rücktaste bzw. „a“ angehängt, Schreibmarke auf 0: nichts gelernt, nichts komponiert, Leiste ohne Text).
- M1: Beendete die Tresor-Wahl ein autokorrigiertes Wort, stand der Text als Trenner in
  `mLastComposedWord`, und die Rücktaste setzte ihn per `revertCommit` als Kompositionswort ein.
  `markPrivateBeforeCursor` leert `mLastComposedWord` und `mEnteredText`. Test
  `aBackspaceAfterAPickThatEndedACorrectedWordDoesNotComposeIt` („hullo“ → „hello“ + Geheimnis).
- M2: `SensitiveClips.migrateLegacy` ohne `ClipboardDao` (vor dem ersten Entsperren) löschte die
  alte Hash-Datei ungenutzt; jetzt `return false`, Datei bleibt. Test
  `ClipCommandTest.withoutAReadableHistoryTheOldHashFileStays`.
- N1: bis zu drei private Texte je Feld (`PrivateRanges.MAX`); N2: Bearbeitungen der Tastatur davor
  verschieben die Marke, andere findet der Hash wieder (P185). Tests
  `severalPrivateTextsAreKeptAndFollowEditsBeforeThem` (Name, dann Adresse, dann „Hallo “ davor),
  `PrivateRangesTest` (Verschieben, Kürzen, ungemeldete Verschiebung, drei Werte).
- N3: statt 64 Zeichen zählt die Lernsperre Wörter (`MAX_PREV_WORD_COUNT_FOR_N_GRAM` + 1). Positiv-
  test `learningResumesOnceThePrivateTextIsNoLongerContext` („eins zwei drei“ nicht, „vier“ gelernt)
  und `PrivateRangesTest.theLearningContextAreTheLastWords`.
- N4: Neustart in `handleNonSeparatorEvent` nach einem Wortverbinder prüft jetzt auch
  (`privateTextAtCursor`). Kein eigener Test.
- N5: markiert wird `mEnteredText` (das Committete nach der TLD-Behandlung) statt eines
  Textvergleichs mit dem Rohtext. Test `aLongPrivateTextIsMarkedAsCommitted` (Schlüssel mit
  Zeilenumbrüchen, länger als der Hash-Kopf; die Testverbindung liest höchstens 100 Zeichen),
  `PrivateRangesTest.longValuesAreCheckedByTheirHead` (über 3000 Zeichen).
- N6: kein Klartext mehr (Position, Länge, Hash der ersten 32 Zeichen), `mLastComposedWord` leer
  (M1). `PrivateRangesTest.keepsNoTextOnlyFindsTheValueByItsHash`.
- N7: WAL-Checkpoint und `VACUUM` auf einem Hintergrund-Thread von `ClipboardDao`, `VACUUM` ohne
  `synchronized(this)` und nur bis 8 MiB Dateigröße; `runCatching` bleibt. Nur indirekt getestet
  (Lösch-Tests grün), auf keinem Gerät geprüft.
- N8: Passwort-Wort plus Wert nur mit wertartigem Wert (ab 6 Zeichen, Ziffer oder Sonderzeichen;
  nach „:“/„=“ auch Groß-/Kleinbuchstaben allein am Zeilenende; bis zur dritten Nachprüfung ließ
  der Code dort noch ein weiteres Wort zu). Test
  `ClipGuardTest.aPasswordWordNeedsAValueThatLooksLikeOne`; die alten Positive bleiben grün. Die
  nachträgliche Bereinigung läuft einmal je Versionscode, sortiert im Hintergrund, löscht auf dem
  Hauptthread (P186). Test `ClipVaultTest.theOldHistoryIsSortedOncePerVersionOffTheMainThread`;
  `oldSecretsLeaveTheHistoryPinnedOnesStay` entfernt dafür vorher den Versions-Merker (wie nach
  einem Update), die Erwartung ist unverändert.
- Etappe 46 (`Memory.onWordCommitted`): nachgeprüft in der dritten Nachprüfung (siehe unten). Der
  Aufruf steht in `performAdditionToUserHistoryDictionary` hinter der Sperre für private Texte
  (`InputLogic.java` ~1947/1975), die Wortsperre gilt also auch für das Gedächtnis.
- Geprüft: `./gradlew :agent-core:test` grün (mit `PrivateRangesTest`, 7); `:app:testDebugUnitTest`
  mit `InputLogicTest` (215, grün bis auf das geerbte `insertLetterIntoWordHangulFails`),
  `ClipVaultTest` (21), `ClipCommandTest` (12), `FieldSuggestionChipsTest` (21) grün. Nicht
  gelaufen: volle Suite, Agent-Tests über `tools/dev.py`, Lint, Build, Gerätetest.
- Engine-Änderungen (Prüfer Pflicht): `InputLogic.java` (`mPrivateRanges`/`mPrivateEditor`,
  `markPrivateText`, `markEnteredTextPrivate`, `markPrivateBeforeCursor`, `privateTextEdited`,
  `privateTextAtCursor`; je ein Aufruf in `onCodeInput` (Positionen merken, nach der Ereignisschleife
  melden, beim Merken von `mWordBeingCorrectedByCursor`), `onTextInput` (merken, vor
  `endBatchEdit` melden), `handleNonSeparatorEvent`, `restartSuggestionsOnWordTouchedByCursor`
  (nach dem Wortbereich statt in der ersten Bedingung), `performAdditionToUserHistoryDictionary`;
  `nearPrivateText` und `PRIVATE_LEARNING_DISTANCE` entfernt), `LatinIME.onPrivateTextInput`
  (`markEnteredTextPrivate`). `RichInputConnection`, `PointerTracker` unverändert.

## Nachprüfung 46 (Prüfer-Befunde zu d6ea051/2417070, 2026-11-14)

- H1 behoben: „Gelernte Wörter löschen“ (`AgentLearningScreen`) und das bestätigte Ausschalten von
  „Personalisierte Vorschläge“ (`TextCorrectionScreen`) rufen `Memory.clear`; `clear` leert auch
  Lauf, `lastTyped`, offene Rücknahme, gezählte Stellen und `MemoryChips.forget` (N6) und macht
  wartende Schreibvorgänge ungültig. Test: `SymbolSettingsScreenTest`
  `theLearnedDataPageCountsAndDeletesPersonalTemplates` und
  `turningPersonalizedSuggestionsOffDeletesPersonalTemplates` legen Wortfolge, eigenes Wort und
  gemerkte Abkürzung an und prüfen danach Speicher und neu gelesenen Zustand leer.
- M1 behoben: Abkürzungen gehen nie mehr ins persönliche Wörterbuch (`createShortcut` entfernt);
  `CREATED` = gemerkt, die Erweiterung kommt nach „Kurzform + Leerraum“ als Chip, erst der Tipp
  ersetzt genau dieses Wort (`Host.replace`), die Kurzform wird nicht automatisch korrigiert (P187).
  Keine Migration: nur Test-Build 20 hat Kürzel angelegt (STORAGE, Release-Notizen). Test:
  `abbreviationExpandedTwiceIsRememberedOnATapAndOnlyATapReplacesIt` (Angebot ändert nichts, Tipp
  merkt; „das ist vlt “ bleibt beim Leerzeichen so, auch mit „Autokorrektur-Kürzel“ an; Chip „vlt →
  vielleicht“ ersetzt nur das Wort, einmal; „Vlt“ → „Vielleicht“; „xvlt “ nichts; geänderter Text:
  nichts; verworfen: kein Chip), `SuggestTest` `remembered abbreviation stays as typed` (sehr starke
  Korrektur „mfg“ → „mag“ ohne, keine mit gemerkter Abkürzung, kein mehrwortiger Vorschlag),
  `MemoryRulesTest.aRememberedAbbreviationIsOnlyLookedUp`.
- M2 behoben: `ShortcutRules.plausible` lehnt Erweiterungen ab, die mit der Kurzform beginnen;
  `Memory` zählt eine Ersetzung nur, wenn kein Hauptwörterbuch die Kurzform kannte (`onTyped`,
  höchstens 16 Wörter je Feld). Tests: `MemoryRulesTest.continuingAWordIsNoAbbreviation` („Haus“ →
  „Hausaufgaben“, „Wo“ → „Wochenende“, „wie“ → „wieder“, „wo“ → „wo oder wann“, auch beim Lesen),
  `InputLogicTest.continuedWordsAndDictionaryWordsAreNoAbbreviations` (weitergeschrieben und bekannt:
  kein Angebot; dieselbe unbekannte Kurzform: Angebot).
- M3 behoben: `onCorrectionUndone` merkt nur vor; gezählt beim nächsten Commit, wenn das Wort dann
  noch dasteht, je „Wort@Position“ und Feld einmal (P188). Tests:
  `slangBecomesOwnAfterThreeUndoneCorrectionsAndIsOfferedWhileTyped` (jetzt drei Rücknahmen in einem
  Feld mit Weitertippen ohne `setText("")` dazwischen, nach jeder Runde genau eine Nutzung mehr, je
  Runde zweimal zusätzlich `RejectedCorrections.remember` wie der Feldvergleich von 49: zählt nichts),
  `anUndoneCorrectionTheOwnerThenChangesDoesNotCount` (nach der Rücknahme korrigiert: nichts gezählt).
- M4 entschieden und umgesetzt: `Host.insertAtCursor(text, privateText = true)`; Gedächtnis-Chips
  (Rest, Baustein, `@snip Name`) mit `false` über `onTextInput`, Tresor, „Meine Daten“ und Codes
  weiter privat. P177 und Gedächtnis-Text in ARCHITECTURE angepasst. Tests:
  `theRestOfAPhraseIsTheOwnersTextNotPrivate` (das Wort nach dem angehängten Rest wird gelernt),
  `FieldSuggestionChipsTest` prüft in der Test-Host-Methode, dass Feld-Vorschläge privat einfügen.
- N1: `onWordCommitted` bekommt den Text vor der Schreibmarke als Lambda und liest ihn erst nach
  `mayLearn` (eine Zeile in `InputLogic`); `PhraseBook` indexiert nach den ersten zwei Wörtern;
  `ownLookup` erst bei Bedarf und nur nach Änderungen; `save` kopiert unter der Sperre und kodiert und
  schreibt auf einem eigenen Thread. Gemessen (`PhraseMemoryTest.predictionWithTwoThousandPhrasesIsFast`,
  2000 Folgen, JVM des Containers): 0,076 ms je Vorhersage (Grenze 2 ms), kopieren und kodieren
  11,1 ms (Grenze 500 ms); `theIndexFollowsDeleteEvictionAndClear`. Auf dem Gerät nicht gemessen.
- N2: `memoryLearnsNothingNextToPrivateTextOrInTheKeyboardsOwnFields` (privater Text vor dem Satz,
  `privateImeOptions` = `NO_LEARNING_FIELD`; `Memory.isBlocked` prüft das jetzt selbst aus der
  `EditorInfo`; ein normales Feld lernt danach). Die bedingte Prüfung im Slang-Test wurde durch
  eine unbedingte ersetzt („krass“ ist `KIND_COMPLETION`), die echte Korrekturschwelle prüft neu
  `SuggestTest` `own word completion never takes the place of the automatic correction` (Korrektur
  bleibt an Stelle 1, eigenes Wort dahinter als Vervollständigung).
- N3: `pickPrediction` prüft `isPrediction` beim Tipp. N4: `nextWord` und `ownCompletion` fragen den
  Sperrzustand, bevor sie etwas liefern. Kein eigener Test (Robolectric-Host ohne Leiste bzw.
  `deviceLocked` live; `stripChips` mit Sperre prüft `memoryLearnsNothingInBlockedFields`).
- N5: mit 1–2 Bausteinen reichte ein gemeinsames Wort („ich muss aufpassen“ → „Hundesitter“ 0,46).
  Neu `sharesEnough`: Namenswort, 2 Wörter, 40 % der Wörter oder ab 3 Bausteinen ein Wort, das nur
  dieser Baustein hat. Messung mit 8 Bausteinen unverändert 17/17 richtig, 17 von 23 gefunden
  (0,35); ohne die Ausnahme ab 3 Bausteinen wären es 16 von 23 gewesen. Test
  `MemoryRulesTest.withFewSnippetsOneSharedWordIsNotEnough`.
- `./gradlew :agent-core:test`: grün, 443 Tests, 0 Fehler (`memory.PhraseMemoryTest` 12,
  `memory.MemoryRulesTest` 10).
- `./gradlew :app:testDebugUnitTest` gezielt (`--max-workers=1`): `InputLogicTest` 222, einziger
  Fehler `insertLetterIntoWordHangulFails` (geerbt); die 12 Fälle zu 46 grün:
  `memoryChipAppendsTheRestOnlyOnATapAndNeverReplaces`, `memoryNextWordForTheEngineAfterTwoWords`,
  `memoryLearnsNothingInBlockedFields`, `slangBecomesOwnAfterThreeUndoneCorrectionsAndIsOfferedWhileTyped`,
  `anUndoneCorrectionTheOwnerThenChangesDoesNotCount`, `slangCommittedAsTypedThreeTimesBecomesOwn`,
  `abbreviationExpandedTwiceIsRememberedOnATapAndOnlyATapReplacesIt`,
  `continuedWordsAndDictionaryWordsAreNoAbbreviations`, `anIgnoredAbbreviationOfferIsNotShownAgain`,
  `memoryLearnsNothingNextToPrivateTextOrInTheKeyboardsOwnFields`,
  `theRestOfAPhraseIsTheOwnersTextNotPrivate`, `fittingSnippetIsAChipThatInsertsAtTheCursor`.
  `SuggestTest` 44, `MemoryScreenTest` 3, `SymbolSettingsScreenTest` 13, `FieldSuggestionChipsTest`
  21, `CorrectionMemoryTest` 4, `FieldCorrectionMemoryTest` 5, `LearningDataTest` 5: grün.
- Engine: `InputLogic.java` nur die eine Hook-Zeile (Lambda statt Text); `Suggest.kt`,
  `RichInputConnection`, `PointerTracker`, `LatinIME` unverändert. Die Prüfung neben privatem Text
  (`mPrivate*`/`nearPrivateText`) ändert parallel eine andere Arbeitskopie; hier nicht berührt.
- Nicht gelaufen: `tools/dev.py agent-tests`, volle Suite, Build, Lint (Speicher, Anweisung des
  Leads). Gerätetest offen (Erweiterungs-Chip mit echter Leiste, Speicherzeit auf dem Gerät).
## Dritte Nachprüfung 44/45/49 (Prüfer-Befunde zu 912624f, 2026-11-14)

- Behoben (Arbeitskopie, Lead-Auftrag). Engine-Änderung eng, erneutes Prüfer-Review nötig:
  `InputLogic.java` (neue Methode `privateTextBeforeEdit`, je ein Aufruf in `onCodeInput` und
  `onTextInput` direkt nach dem Merken der Auswahl; Überladung
  `performAdditionToUserHistoryDictionary(…, privateDecided)`, gerufen mit `true` nur in
  `onUpdateSelection` für `mWordBeingCorrectedByCursor`; `mFieldCorrections` mit
  `PrivateText`-Lambda). `RichInputConnection`, `PointerTracker` unverändert.
  - 1 (mittel): `PrivateRanges.edited` hashte bei jeder Verschiebung neu an der errechneten Stelle;
    nach einer ungemeldeten Verschiebung (gewählter Vorschlag, Geste, App) war das falscher Text,
    `stillThere` passte dann immer und die Marke lag dauerhaft um k Zeichen daneben (Ende des Werts
    ungeschützt). Jetzt: reine Verschiebung behält Hash und Kopflänge (kein Lesen, auch kein
    `getTextAfterCursor` je Taste); neu gehasht wird nur bei einer Bearbeitung im Kopf, und davor
    prüft `beforeEdit` einen Wert, dessen Kopf höchstens 32 Zeichen von der Bearbeitung liegt, und
    findet ihn nach einer ungemeldeten Verschiebung wieder.
  - 2 (mittel): `FieldCorrectionWatcher.checkpoint` verwirft eine Änderung, deren Wort
    `[wordStart, wordStart + max(alt, neu)]` einen privaten Text berührt (Prädikat
    `PrivateText` aus `InputLogic`, `mPrivateRanges.touches`). Kein Bruchstück eines Werts wird
    Kandidat oder abgelehnte Korrektur.
  - 3 (gering): `null` oder ein zu kurz gelesener Text vor der Schreibmarke vergisst nichts mehr:
    nahe Werte gelten dann als berührt; vergessen wird nur nach einem vollständigen Lesen ohne Fund
    (weniger Text nach der Schreibmarke = Feldende). Eine Bearbeitung im Kopf, die nicht gelesen
    werden kann, hält den Wert privat (`headUnread`), bis er wieder gehasht werden kann. Gelesen wird
    höchstens `READ_LIMIT` = 8192 Zeichen von der Schreibmarke; die Suche läuft um die letzte Stelle
    des Werts statt Länge + Fenster um die Schreibmarke. Den Kopf eines langen Werts vor der
    Schreibmarke erreicht `getTextBeforeCursor` nur mit dem Abstand bis zu ihm; das bleibt.
  - 4 (gering): `mWordBeingCorrectedByCursor` (ohne N-Gramm-Kontext, beim Merken geprüft) wird an
    der neuen Schreibmarke nicht mehr zusätzlich gesperrt. `Memory.onWordCommitted` liest dort Text
    vor der Schreibmarke und prüft darum weiter (`privateContext`).
  - 5 (gering): unter `MIN_RELOCATE` = 8 Zeichen wird nur 64 Zeichen um die letzte Stelle gesucht;
    mehrere Fundstellen gelten alle als privat (`ambiguous`), bis nur eine übrig ist. Abweichung vom
    Vorschlag „Mindestlänge für das Wiederfinden“: Codes mit 6 Ziffern sollen nach einem gewählten
    Vorschlag davor weiter geschützt sein (`PrivateRangesTest.aValueMovedByAnUnseenEditIsFoundAgain`
    bleibt unverändert).
  - 6 (gering): Beschluss: Doku korrigiert, Verhalten unverändert. `VACUUM` hält die schreibende
    Verbindung, Einfügen/Löschen auf dem Hauptthread wartet währenddessen; es läuft nur nach einer
    einmaligen Bereinigung bei höchstens 8 MiB (P179, KDoc `ClipboardDao.compactInBackground`).
  - 7 (Doku): Etappe 46 nachgeprüft: `Memory.onWordCommitted` steht hinter der Sperre (oben).
  - 8 (gering): `ClipGuard.passwordInSentence` lässt nach einem Wert nur aus Buchstaben kein
    weiteres Wort mehr zu („Passwort: Erfragen Sie“ bleibt Text); Komma, Semikolon oder `|`
    danach und das Zeilenende beenden den Wert weiter.
- Tests (Robolectric bzw. JVM, kein Gerätetest): `PrivateRangesTest` neu 4
  (`anEditAfterAnUnseenShiftDoesNotHashTheWrongText`, `typingFarBeforeAValueReadsNothing`,
  `anUnreadableOrShortReadForgetsNothing`, `equalTextNearbyIsPrivateTooAShortValueDoesNotJumpFar`;
  der Test-Editor ruft jetzt wie `InputLogic` `beforeEdit` vor jeder Bearbeitung), `ClipGuardTest`
  neu 1 (`aLettersOnlyPasswordValueMustEndItsLine`), `InputLogicTest` neu 3
  (`aPickedSuggestionBeforeAPrivateTextKeepsItsMarkOnIt`, `aFixInAPrivateTextIsNoFieldCorrection`
  mit Gegenprobe im normalen Text, `aWordTakenAwayFromAPrivateTextIsLearnedNextToOne`; dort setzt der
  Test `mWordBeingCorrectedByCursor` per Reflexion, weil die Testverbindung das Wort nach der
  Rücktaste sofort wieder komponiert und `onCodeInput` dann keins merkt). Gegenproben: ohne
  `beforeEdit` schlägt `anEditAfterAnUnseenShiftDoesNotHashTheWrongText` fehl, ohne die Prüfung in
  `checkpoint` `aFixInAPrivateTextIsNoFieldCorrection`, mit `privateDecided = false`
  `aWordTakenAwayFromAPrivateTextIsLearnedNextToOne`. `aPickedSuggestionBeforeAPrivateTextKeepsItsMarkOnIt`
  besteht auch mit nachgebautem altem Verhalten (ohne `beforeEdit`, Neuhashen bei jeder
  Bearbeitung): Das Lernen des gewählten Worts fragt `touches` und findet den Wert dort schon
  wieder; den Fehler (wenn beim Wählen nichts gelernt wird) belegt der Unit-Test. Gelaufen (`--max-workers=1`,
  2026-11-14): `:agent-core:test` 451/451; `:app:testDebugUnitTest` mit `*InputLogicTest`,
  `ClipVaultTest`, `FieldCorrectionMemoryTest`, `ClipCommandTest`: 275 Tests, 1 Fehler = geerbt
  (`InputLogicTest.insertLetterIntoWordHangulFails`). `docs_sync --check`: grün.
- Nicht gelaufen: `tools/dev.py agent-tests`, volle Suite, Lint, Build, Gerätetest.

## Vierte Prüfung Privattext (Prüfer und Lead, 2026-11-14)

- Prüfer (nur Lesen) zu 79a66c8 und Merge 13b5526: kein hoher Befund; Schutz für den geprüften Build
  schlüssig und vollständig genug; Memory-Hook nach dem Merge korrekt (`!privateContext` plus Lambda).
  Mittel: gekürzte Antwort nach der Schreibmarke wurde als Feldende gewertet und vergaß den Wert. Vom
  Lead behoben (`PrivateRanges.find`), Test `aReadCutShortAfterTheCursorForgetsNothing`, Gegenprobe:
  ohne die Zeile rot. Niedrige Befunde als Restrisiken in P185 festgehalten; Doku-Präzisierung: „kein
  Lesen je Taste“ gilt nur weit vor einem Wert. Lauf: `:agent-core:test --tests PrivateRangesTest`
  12/12 grün.

## Zweite Nachprüfung 46 (Prüfer-Befunde zum Stand 8e14a9e, 2026-11-14)

- Vor den Korrekturen hat der Lead auf dem Merge 8e14a9e `InputLogicTest` (einziger Fehler
  `insertLetterIntoWordHangulFails`, geerbt), `SuggestTest`, `MemoryScreenTest`, `ClipVaultTest`,
  `FieldSuggestionChipsTest`, `SymbolSettingsScreenTest` und `:agent-core:test` laufen lassen: grün.
- 1 entschieden (Annahme des Leads, Owner kann ändern): Die einzige Ersetzung des Gedächtnisses ist
  der Chip einer gemerkten Abkürzung, genau das gerade getippte Kürzelwort, nur auf Tipp (ROADMAP 46,
  FEATURES, ANLEITUNG, P177).
- 2 behoben: `Memory.mayLearn` und `onCorrectionUndone` verlangen `mUsePersonalizedDicts` (wie
  `ContextLearning` und `SymbolDrawing`; das Ausschalten löscht das Gedächtnis). Tests:
  `memoryLearnsNothingInBlockedFields` mit neuem Fall „not personalized“,
  `anUndoCountsOnlyAtItsPlaceWithAKnownCursorAndWithPersonalizedSuggestions` (ohne den Schalter:
  `mayLearn` falsch, Rücknahme zählt nicht). `startMemory` schaltet ihn für die Gedächtnis-Tests an.
- 3 behoben: `ShortcutBook.note` überschreibt die Erweiterung nach `CREATED` nie, eine nach kleiner
  Kurzform aufgezeichnete Form gewinnt, die Fall-Spalte merkt „nur nach großer Kurzform“ (`v2`, `v1`
  lesbar); `MemoryChips.expansionCase`: kleine Kurzform behält die eigene Schreibung, nur am
  Satzanfang gelernt wird der Anfang klein, wenn ein Wörterbuch das erste Wort klein kennt
  (`Host.isKnownWord`). Tests: `MemoryRulesTest.theStoredExpansionKeepsTheFormOfALowerCaseShortFormAndNeverChangesOnceRemembered`,
  `InputLogicTest.aRememberedExpansionKeepsItsOwnCaseOrFollowsTheTypedShortForm` („tel “ → „Telefon“,
  „Tel “ → „Telefon“, „mfg “ → „mit freundlichen Gruessen“ eingesetzt, „Mfg “ → „Mit …“, ohne
  bekanntes „mit“ bleibt „Mit …“).
- 4 behoben: `plausible(…, shortIsWord)` lehnt „Erweiterung beginnt mit der Kurzform“ nur für echte
  Wörter ab; die App gibt dem Buch ohnehin nur Kurzformen, die kein Hauptwörterbuch kennt. Tests:
  `MemoryRulesTest.continuingAWordIsNoAbbreviation` („Haus“, „Wo“, „wie“, „wo“ mit
  `shortIsWord` abgelehnt; „Mo“ → „Montag“, „tel“ → „Telefon“, „Prof“ → „Professor“ plausibel,
  „tel“ zweimal: Angebot), `continuedWordsAndDictionaryWordsAreNoAbbreviations` („haus“ jetzt als
  bekanntes Wort gemeldet, wie es ein Hauptwörterbuch täte). Geändert nach Entscheidung des Leads:
  Die Assertions, dass „Haus“ → „Hausaufgaben“ schon `plausible` und der `ReplacementTracker`
  ablehnen und dass das Lesen „wie;wieder“ verwirft, gelten jetzt nur mit `shortIsWord` bzw. über
  `unknownCommits` (keine Abschwächung der Regel für echte Wörter).
- 5 nicht umgesetzt: keine Chips direkt hinter privatem Text. `InputLogic.privateTextAtCursor` ist
  privat und wird parallel umgebaut (`PrivateRanges`, `mPrivate*`); ein `Host`-Zugang ohne
  öffentliche Abfrage ginge nur über eine Änderung dort. Offen für den Lead nach dem Merge.
- 6 behoben: `RejectedCorrections.remember(…, at)` gibt die Stelle des getippten Worts weiter
  (`revertCommit`, Tipp von 48, Feldvergleich 49); `Memory` zählt nur ein ganzes Wort genau dort,
  nie bei unbekannter Schreibmarke oder ohne Stelle; `countedAt` ist ein `LinkedHashSet`. Tests:
  `anUndoCountsOnlyAtItsPlaceWithAKnownCursorAndWithPersonalizedSuggestions` (Stelle 5 und −1 und
  ein Weg ohne Stelle: nichts; Stelle 4: eins), `slangBecomesOwnAfterThreeUndoneCorrectionsAndIsOfferedWhileTyped`
  (die doppelte Meldung jetzt mit derselben Stelle: zählt nichts).
- 7 behoben: Rest-Risiko „bis zu 30 s nur im Speicher“ in STORAGE und ARCHITECTURE; beim
  Verlassen des Felds (`finishEditor`) schreibt `saveNow` sofort auf dem Aufrufer-Thread. Test
  `theFieldEndingWritesWhatWasLearnedAtOnce` (Hintergrund-Schreiben verworfen, nach
  `onFinishInputView` steht die Folge im Speicher).
- 8 behoben: `MemoryChips.showing` ist `@Volatile`. Kein eigener Test.
- 9 behoben: Satzreste am Ende von P177 entfernt; der Satz zu „Meine Daten“ steht jetzt in P178.
- `./gradlew :agent-core:test`: grün (`memory.MemoryRulesTest` 11).
- `./gradlew :app:testDebugUnitTest` gezielt (`--max-workers=1`): `InputLogicTest` 238, einziger
  Fehler `insertLetterIntoWordHangulFails` (geerbt); `SuggestTest` 44, `MemoryScreenTest` 3,
  `CorrectionMemoryTest` 4, `FieldCorrectionMemoryTest` 8: grün.
- Engine: `InputLogic.java` nur die zwei Aufrufe von `RejectedCorrections.remember` (Stelle
  zusätzlich), sonst nichts; `Suggest.kt`, `RichInputConnection`, `PointerTracker`, `LatinIME`
  unverändert. `FieldCorrections.kt` eine Zeile (Stelle an `remember`). Prüfer-Review nötig.
- Nicht gelaufen: `tools/dev.py agent-tests`, volle Suite, Build, Lint (Speicher, Anweisung des
  Leads). Gerätetest offen.

## Etappe 37 (Bildwiederholrate als Karten)

- Auftrag (Lead, ROADMAP 37, Skizze `docs/referenzen/2026-11-13-rgb-bildwiederholrate.jpg`, „nicht
  als exakte Vorgabe, sondern mit Visualisierung“): Auf der RGB-Seite wird die Wahl 30/60 eine
  Karte „Bildwiederholrate“ mit zwei Farbflächen, leuchtendem Tropfen als Tempo (60 Hz doppelt so
  schnell), gewählte Fläche hervorgehoben, „Im Energiesparmodus anhalten“ darunter; dieselbe
  Einstellung wie bisher; Animation billig, still ohne Animationen, im Energiesparmodus und außer
  Sicht. Gebaut in einer Arbeitskopie, kein Prüfer-Review, kein Gerätetest.
- Gebaut: `AgentRgbFrameRate.kt` (`FrameRateCard`, neu). Geändert: `AgentRgbScreen.kt` (Karte
  „Akku“ mit Chips → `FrameRateCard`, Schalter und Hinweis im Platz `below`),
  `agent_rgb_strings.xml` (Englisch, Deutsch: `agent_rgb_fps` deutsch „Bildwiederholrate“, neu
  `agent_rgb_fps_hertz`, `agent_rgb_fps_30_hint`, `agent_rgb_fps_60_hint`; das nicht mehr
  genutzte `agent_rgb_group_battery` entfernt). Keine Upstream-Datei, kein Kern, keine neue
  Einstellung: geschrieben wird wie bisher `fps` in `agent_rgb_lighting` über `RgbLighting.write`.
- Umsetzung: Flächen untereinander (nebeneinander je etwa 157 dp, zu schmal für „30 HERTZ“ in
  19 sp mit Sperrung), Auswahl wie `ChoiceCards` (Rahmen 3 dp in `onSurface`, die andere 55 %),
  Radiobuttons. Ein `rememberInfiniteTransition` für beide Flächen, nur in `drawBehind` gelesen;
  es gibt ihn nur, solange die Flächen im Bild sind und Bewegung erlaubt ist. Abweichungen von der
  Skizze (Annahmen, in ROADMAP 37 genannt): Großbuchstaben, Zusatzzeile „30 Bilder/s · spart
  Akku“ bzw. „60 Bilder/s · flüssiger“, die nicht gewählte Fläche blasser, der Tropfen wandert hin
  und her mit Spur und pulsierendem Hof.
- Tests (Robolectric, kein Gerätetest): `RgbFrameRateCardTest` 7 Tests: Karte mit Überschrift
  (Überschrift für TalkBack), beide Flächen als Radiobuttons mit „30 Hertz“/„60 Hertz“, Zusatzzeilen,
  Vorgabe 30 gewählt, Energiesparschalter in der Karte unter beiden Flächen, Flächen untereinander
  in voller Breite, Flächen und Schalterzeile ≥ 44 dp; Tipp auf 60 schreibt genau
  `RgbSettings.DEFAULT.copy(fps = 60)` (sonst nichts geändert), Taktgeber 1/60 s, Auswahl folgt,
  Tipp auf 30 entfernt die Einstellung wieder (Vorgabe, wie bisher), Energiesparschalter schreibt
  weiter; Bewegung mit manueller Uhr: in 300 ms ein Achtel der Runde (±0,02), 60 Hz doppelt so
  weit (±0,02), Bild ändert sich, Karte wird dabei nicht neu zusammengesetzt; Animationsskala 0
  und Energiesparmodus: keine Transition, Bild nach 1 s gleich, Tropfen an der Ruhestelle; Ende des
  Energiesparmodus (Broadcast) startet die Bewegung; außer Sicht (3000 dp weiter unten) keine
  Transition, hineingescrollt läuft sie, wieder hinaus steht sie; Bild mit Pixelprüfung (Rahmen
  um die gewählte, keiner um die andere, volle bzw. 55-%-Farbe, Tropfen fast weiß an der
  erwarteten Stelle). Gegenprobe: mit Transition auch außer Sicht und gleichem Tempo für beide
  schlugen 4 der 7 Tests fehl. `RgbSettingsScreenTest` (13) unverändert grün, auch
  `everyOptionIsSavedAtOnce` (Tag `agent_rgb_fps_60` jetzt auf der Fläche) und
  `framesRedrawOnlyThePreview`.
- Gezielt (Robolectric, 2026-11-14): `RgbSettingsScreenTest` 13, `RgbFrameRateCardTest` 7 – 20
  Tests, 0 Fehler. Kein voller Lauf, kein `agent-tests`, kein Build (geteilte Maschine, Auftrag);
  beides macht der Lead. `docs_sync --check`: grün.
- Bild (nicht versioniert): `app/build/agent-previews/rgb-framerate-card.png` (390 dp breit, dunkel,
  Animationen aus), mit der Skizze verglichen: Überschrift fett und gesperrt, zwei runde Flächen
  untereinander in Türkis und Gelb-Orange, gesperrte Schrift in der Fläche, leuchtender Tropfen
  oben, Schalter „Im Energiesparmodus anhalten“ darunter stimmen; anders als die Skizze ist die
  nicht gewählte Fläche blasser und die Schrift dunkel statt weiß (Lesbarkeit auf den hellen
  Flächen).
- Offen: Prüfer-Review, Gerätetest (Aussehen, Tempo und Wirkung des Tropfens, Bildkosten am
  Telefon, Energiesparmodus live, TalkBack).

## Etappe 40 (Smart Paste)

- Auftrag (Lead, ROADMAP 40, Wunsch des Owners vom 2026-11-13): für den aktuellen Clip (Pille in der
  Leiste) und je Karte des Verlaufs Typen lokal erkennen und Chips anbieten: Adresse → Karte /
  speichern, Telefon → wählen / Kontakt, IBAN → formatiert mit Prüfziffer, Sendungsnummer → Seite
  des Dienstes (feste Tabelle), Datum/Uhrzeit → Kalender, E-Mail → Mail, Link → ohne Tracking,
  Einmalcode nur aus der Zwischenablage. Nie für Geheimnisse, nie in Passwort-, Inkognito-,
  No-Learning-Feldern oder gesperrt. Gebaut in einer Arbeitskopie, kein Prüfer-Review, kein
  Gerätetest.
- Vorher geprüft: `@ort` (`PlaceCommand`, `SavedPlaces`, `Places.geoUri`) und `@termin`
  (`EventCommand.intent`, `EventParser`) gibt es, beide werden benutzt, kein neuer Befehl.
  `ClipGuard` bleibt unverändert (seine Arten bestimmen Tresor und Beschriftung aus 44/45);
  Sendungsnummern und Daten sucht Smart Paste nur in Clips, die er als `TEXT` sortiert.
  `<queries>`: nicht nötig (`startActivity` ohne Sichtbarkeitsabfrage, Manifest hat `action *`).
  Keine neue Berechtigung: Wählen, Kontakt, Kalender und Mail öffnen nur die Apps.
- Gebaut: Kern `C/clip/SmartPaste.kt`, `TrackingNumbers.kt`, `LinkCleaner.kt`, `ClipDates.kt`; App
  `A/SmartPasteChips.kt`, `agent_smart_paste_strings.xml` (`values/` mit
  `tools:ignore="MissingTranslation"`, `values-de/`), Schalter `AgentPreferences.SMART_PASTE` in
  `AgentAreaBarScreen.kt`. Geänderte, schon geänderte Upstream-Dateien:
  `ClipboardHistoryManager.kt` (Pille plus Chips, `shownView`), `ClipboardAdapter.kt`
  (`bindHistoryRow`), `ClipboardHistoryView.kt` (`History` je Besuch, `onSmartInsert`). Keine
  bisher unveränderte Upstream-Datei, keine Engine-Datei (`InputLogic`, `RichInputConnection`,
  `PointerTracker`, `LatinIME`, `Suggest` unverändert), kein Layout, `map.sources` unverändert.
- `./gradlew :agent-core:test`: grün, 479 Tests, 0 Fehler, darin neu `clip.SmartPasteTest`
  21 (Adresse → Karte und Ort, Zeilen verbunden; Telefon → Wählen und Kontakt; IBAN nur mit
  richtiger Prüfziffer in Blöcken, auch „IBAN:“ und klein geschrieben, schon in Blöcken nichts,
  falsche Prüfziffer nur der Hinweis; E-Mail mit und ohne `mailto:`; Link mit/ohne Tracking;
  Code aus Nachricht, Code allein nichts; Geheimnisse, Passwortsatz, markierte, leere und zu lange
  Clips nichts; Text mit DHL-Nummer, Datum und Link ergibt alle drei in dieser Reihenfolge;
  `toString` ohne Clip; UPS (öffentliche Beispielnummer, auch in Gruppen), Deutsche Post S10 (auch
  „RR 1234 5678 5DE“, in Klammern), DHL `00340434…` (GS1) und `JJD…`, Hermes `H…`; falsche
  Prüfziffern und S10 eines anderen Landes nichts; reine Ziffern ohne Namen des Dienstes nichts
  (Bestell-, Kunden-, 11-stellige Nummern), mit Namen DPD, Hermes, DHL, GLS (auch Track-ID), der
  zuerst genannte Dienst entscheidet; Postleitzahlen, Packstation, Hotline, Telefonnummer, Beträge,
  IBAN keine Sendung; Seiten nur aus der Tabelle, nur Großbuchstaben und Ziffern; Daten de/en/es
  („am 15.03.2027 um 14:30“, „morgen um 18 Uhr.“, „15. Dezember“, „bis 03.12.“, „am 3.12.“, ISO,
  „20.01.2027.“, „tomorrow at 6pm“, „December 24th“, „mañana a las 9 de la noche“, „el 3 de marzo
  de 2027“, „um 18:30“, „Sonntag/Samstag halb acht“); keine Daten: „Version 2.1“, „Update 1.2.3“,
  „Android 14.1.2“, „Kapitel 3.2.“, „Preis 3.10 €“, „Preis: 12.50“, „Version 2.1 um 18 Uhr“,
  „v2.1.0“, „Seite 4.11“, „bis morgen“, „Samstag“, „Guten Morgen!“, „Abfahrt 18:30“, „Fahrt 4h“,
  „see you tomorrow“, „hasta mañana“, „Ergebnis 3:21“; vergangene Daten, „Rechnung vom 03.11.“
  ohne Jahr, „gestern“, zu viele Zeilen, zu lang; Link-Reinigung behält `id`, `color`, `v`, `t`,
  `page`, `q`, Kodierung, leere Werte, Reihenfolge und Fragment, entfernt `utm_*` (jede
  Schreibweise), `fbclid`, `gclid`, `igshid`, `mc_eid`, `mc_cid`, `_hsenc`, `srsltid`, `si` nur bei
  YouTube/Spotify, lässt `utmost`, `gclidx`, `siren` stehen, nichts bei `javascript:`; Links im
  Text samt Satzzeichen und Klammern; Ortsname aus der Straße, Zähler bei gleichem Namen, Grenze
  40 Zeichen). Prüfziffern der Beispiele getrennt mit einem kleinen Python-Skript berechnet; die
  UPS-Beispielnummer `1Z999AA10123456784` ist UPS' öffentliches Beispiel.
- `./gradlew :app:testDebugUnitTest` gezielt (`--max-workers=1`, 2026-11-14): neu
  `SmartPasteChipsTest` 11 (Chips je Art mit echten Texten, Hinweis `MUTED`; nichts für Text
  ohne Typ, Code allein, Link ohne Tracking, „Version 2.1“; echte Pille: Reihe aus Pille und
  Chips, Pille zuerst mit dem Clip, Zeile ohne Befehlskennung, Karte startet `geo:` ohne
  Einfügen, Schalter aus → Pille allein; Absichten: `geo:0,0?q=…`, `ACTION_DIAL` `tel:`,
  `Insert.ACTION` mit `RawContacts.CONTENT_TYPE` und Nummer, `ACTION_SENDTO` `mailto:`,
  UPS-Seite mit `CATEGORY_BROWSABLE`, `ACTION_INSERT` Kalender mit Beginn 15.11. 18:00 Berlin und
  Titel, `FLAG_ACTIVITY_NEW_TASK`, nichts eingefügt, Manifest ohne `CALL_PHONE`, `WRITE_CONTACTS`,
  `READ/WRITE_CALENDAR` (mit `READ_CONTACTS` als Gegenprobe, dass die Liste echt ist), fehlende
  App nur Toast; Einfügen: vor dem Tipp nichts, zwei Tipps ein Einfügen und ein Ausblenden,
  bereinigter Link, nur die Code-Ziffern, Hinweis fügt nichts ein, verschobene Schreibmarke und
  gesperrt nach dem Zeigen nichts; Geheimnis, markiert, im Passwortfeld kopiert, Passwortfeld,
  Inkognito, No-Learning, Zahlenfeld, kein Feld, gesperrt, Schalter aus: keine Chips; echte Pille:
  Geheimnis und markierter Clip ohne Pille, Passwortfeld ohne Chips; „Als Ort“: Name „Musterstraße
  12“, dieselbe Adresse nur einmal, gesperrter Speicher nichts; Karten: Kurztexte, Beschreibung für
  TalkBack, Tipp fügt privat ein (`onPrivateTextInput`, nicht `onTextInput`), zweiter Tipp nichts,
  Adresse zwei Chips, normaler Text keine Reihe; Karten in Passwortfeld, Inkognito, gesperrt
  ohne Chips, Tipp nach dem Sperren nichts, im Passwortfeld kopierter Text ohne Chips; offene
  Verlaufsansicht setzt `smartPaste`, Karte mit „Anrufen“/„Kontakt“, Tipp wählt, Schließen nimmt
  es weg), `SettingsBarAreaTest` 11 (neuer Schalter `SMART_PASTE` in
  `switchesWriteTheirPreferences`: an, Tipp schreibt aus, keine Assertion geändert). Gegenprobe:
  ohne `used`, ohne Sperrprüfung und mit immer erlaubtem Verlauf schlugen 3 der 11 Tests fehl
  (`insertOnlyOnATapAndOnce`, `nothingForSecretsOrInProtectedFields`,
  `historyCardsHaveNoChipsInProtectedFieldsOrForSecrets`). Unverändert grün: `ClipVaultTest` 21,
  `FlatClipboardTest` 8, `FieldSuggestionChipsTest` 21, `ClipCommandTest` 12,
  `DatePlaceCommandsTest` 17, `FormatGuardChipsTest` 12.
- Nicht gelaufen: `tools/dev.py agent-tests`, volle Suite, `InputLogicTest` und Build (geteilte
  Maschine, Anweisung des Leads), Lint. Das Einfügen über die echte Tastatur
  (`LatinIME.onPrivateTextInput`) ist derselbe Weg wie bei Feld-Vorschlägen
  (`InputLogicTest.fieldSuggestionChipInsertsOnlyOnATapAndOnce`), für Smart Paste aber nicht mit
  echtem Host geprüft (P204). `docs_sync --check`: grün.
- Offen: Prüfer-Review, Gerätetest (die sechs Tracking-Seiten wirklich öffnen; Karten-, Telefon-,
  Kontakte-, Mail- und Kalender-App; Leiste mit langer Pille und drei Chips, Karten mit zwei
  Chips im flachen Look; TalkBack; Start aus der Tastatur heraus auf Android 14/15).

## Gerätetest vor Meilensteinfreigabe

- Installation neben der bisherigen Tastatur und problemloser Rückwechsel.
- Tippen, Löschen, Umlaute, Emoji, Auswahl, Autokorrektur, Rotation, Appwechsel.
- Native Textfelder, Compose-Textfelder und WebView.
- Keine Ersetzung bei veraltetem Kontext, Feldwechsel oder zwischenzeitlicher Eingabe.
- Keine Verarbeitung in Passwort-, Inkognito- oder gesperrten Zuständen.
- Keine Netzwerkanfragen durch normales Tippen oder lokale Aktionen.
- Neues Profil anwenden und zum vorherigen funktionierenden Zustand zurückkehren.
