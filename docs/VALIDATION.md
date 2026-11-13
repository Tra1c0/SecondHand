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

## Gerätetest vor Meilensteinfreigabe

- Installation neben der bisherigen Tastatur und problemloser Rückwechsel.
- Tippen, Löschen, Umlaute, Emoji, Auswahl, Autokorrektur, Rotation, Appwechsel.
- Native Textfelder, Compose-Textfelder und WebView.
- Keine Ersetzung bei veraltetem Kontext, Feldwechsel oder zwischenzeitlicher Eingabe.
- Keine Verarbeitung in Passwort-, Inkognito- oder gesperrten Zuständen.
- Keine Netzwerkanfragen durch normales Tippen oder lokale Aktionen.
- Neues Profil anwenden und zum vorherigen funktionierenden Zustand zurückkehren.
