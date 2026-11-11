# Übergabe: Agent Keyboard — 11. November 2026

Die Entwicklung ist auf ausdrücklichen Nutzerwunsch pausiert. Der letzte
Funktionsstand ist abgeschlossen, getestet und committed. Eine Fortsetzung
beginnt erst nach einem neuen Auftrag.

## 1. Auftrag und festgelegter Umfang

Gefordert war eine eigenständige Android-Alternative zur agentischen Tastatur Acti:
zuverlässige Systemtastatur, Aktionsrad, programmierbare Skill Keys, KI-Textaktionen,
eigene Agenten/Workflows, Integrationen, Spracheingabe und optionale lokale KI.
Dazu wurden freie Modellwahl, transparente Datenfreigaben, exportierbare und
versionierte Workflows sowie lokale Personalisierung gewünscht.

Die Umsetzung sollte selbstständig in kleinen Etappen erfolgen: jede Änderung
prüfen, Fehler beheben und erst danach das nächste abhängige Paket beginnen.
Außerdem wurde eine begründete Aufteilung nach Codex-Modell und Denkstufe verlangt.

Für diesen Fork wurde festgelegt:

- Android, privat/persönlich; Kotlin und Compose für Einstellungen.
- HeliBoard als Basis; native Tastatur-/JNI-Engine erhalten.
- Eigene API-Schlüssel ausschließlich über die App, zunächst OpenAI.
- Lernen lokal und opt-in; keine stillschweigende Nutzung generierter Texte.
- Ein schreibender Hauptagent, unabhängige ausschließlich lesende Reviews.
- Dateiaustausch für Workflows und Profile. Öffentliche Community, Konten,
  Backend und Synchronisierung gehören nicht zum vereinbarten privaten Umfang.

## 2. Erledigter Funktionsumfang: Etappen 0–14

| Bereich | Implementierter Stand |
| --- | --- |
| Grundlage | Gepinnter HeliBoard-Fork, eigene App-ID und Bezeichnung, reproduzierbare Builds, Testbaseline und Entwicklungsdokumentation |
| Tastatur | Bestehende native Eingabe, Layouts, Wörterbücher, Autokorrektur und Vorschläge erhalten; Design-/Layout-/Gestenprofile getrennt |
| Editorzugriff | Begrenzte ausgewählte Texte, Sitzungs-/Änderungsprüfung, Einmalfreigaben, separate Ergebnisübernahme und Absicherung unklarer Ersetzungen |
| Aktionsoberfläche | Natives Panel, radiales Wheel, optionale Leertasten-Langdruckgeste, lokale Textwerkzeuge, Vorschau und bewusstes Kopieren/Übernehmen |
| KI-Textaktionen | OpenAI-Anschluss für Korrigieren, Übersetzen, Umformulieren, Zusammenfassen, Antworten und Analyse; Modellkennung frei wählbar |
| Zugangsdaten | Verschlüsselte CE-/Keystore-Speicherung, BYOK-Einstellungen, gesperrte sensible Kontexte und Screenshot-/Lifecycle-Schutz |
| Skill Keys | Drei belegbare Slots für lokale/KI-Aktionen oder gespeicherte Workflows; fehlende Referenzen ohne automatische Ersatzauswahl |
| Austausch | Validierter Dateiimport/-export für Profile, Skills und Workflows; Import/Speichern führt nichts aus |
| Laufzustände | Verschlüsselte Metadaten, Workflow-Gesamtläufe mit verknüpften Modellschritten; Abbruch/Timeout/unklarer Ausgang ohne automatische Wiederholung |
| Workflows | Bis acht Schritte und drei Modellaufrufe, frühere Zwischenergebnisse als Eingabe, deterministische Bedingungen, Revisionen und Schutz vor veralteten Schreibzugriffen |
| Wiederherstellung | Explizit bestätigter Reset derselben beschädigten Workflow-Datei, ohne Schlüssel/Profile/Skill-Belegungen zu löschen |
| Act Builder | Natürliche Beschreibung → genaue Sendefreigabe → strikt validierter Entwurf → vollständige Bearbeitung/Prüfung → separates Speichern |

Der Builder erzeugt derzeit Workflows aus den unterstützten Textaktionen und
Bedingungen. Modellantworten dürfen keine neuen Werkzeuge, URLs, Credentials,
Provider, fremden Workflow-IDs oder ausführbaren Code einschleusen. Provider,
Modellbindung, neue ID und Revision kontrolliert die App.

Der Schutz privater Entwürfe bleibt bis zum Speichern/Verwerfen erhalten.
Appwechsel und Incognito widerrufen die Freigabe. Ein verspäteter Speicherklick
kann einen bereits verworfenen Entwurf nicht wieder aktivieren. Diese Fälle
wurden eigens getestet und unabhängig nachgeprüft.

## 3. Prüfstand

| Prüfung | Ergebnis |
| --- | --- |
| Reiner Kotlin-Kern | 48 Tests bestanden |
| Gezielte Agenten-/Android-Integrationstests | 189 Tests bestanden; Android-Framework überwiegend mit Robolectric simuliert |
| Vollständige App-Regression | 370 Tests: 365 bestanden, fünf bereits geerbte Fehler |
| Neue Hintergrund-Exceptions im vollständigen Lauf | Keine |
| Android Lint | Bestanden; geerbte Baseline und Warnungen bleiben dokumentiert |
| ARM64- und x86-64-Debug-Build | Beide bestanden, APKs und SHA-256-Dateien vorhanden |
| Unabhängige Reviews | Architektur-, Datenschutz- und Nebenläufigkeitsbefunde behoben; abschließende Nachprüfungen ohne Blocker |
| Echter Android-Geräte-/Emulatordurchlauf | Noch offen |
| Echte Modell-API-Anfrage | Noch offen; kein API-Schlüssel vorhanden/provisioniert |

Die 189 gezielten Tests sind eine Teilmenge der vollständigen App-Suite;
die Zahlen werden nicht addiert. Die fünf geerbten Fehler sind:

1. `InputLogicTest.insertLetterIntoWordHangulFails`: bekannte Hangul-Abweichung.
2. `StringUtilsTest.detectEmojisAtEndFails`: bekannte Emoji-Abweichung.
3. `XLinkTest.layoutsLinks`: externe Erreichbarkeit, zuletzt HTTP 503.
4. `XLinkTest.readmeLinks`: externe Erreichbarkeit, zuletzt HTTP 503.
5. `XLinkTest.knownDictionaries`: geändertes externes Datenformat.

Es wurden keine fachlichen Assertions entfernt, um diese Fehler zu verdecken.
Geräte-, Direct-Boot-/Keystore-, Touch-/TalkBack-, Rotations- und Live-API-Prüfungen
werden ausdrücklich nicht als bestanden gewertet.

## 4. Stand beim Unterbrechen

Die letzte abgeschlossene Funktion ist **Etappe 14c: Builder-Oberfläche mit
privater Entwurfsprüfung**. Funktionscommit:

`71a4c9591228ae7dc3d489f9bd1c2550136b01cf`

Danach wurde ausschließlich die bereits begonnene Emulator-Einrichtung vorbereitet:

- Zusätzliche x86-64-APK erfolgreich gebaut.
- Eigene inaktive Gradle-Daemons gestoppt.
- Android Emulator 37.2.13 und AOSP-ATD-Image API 35/x86_64, Revision 1 installiert.
- AVD `AgentKeyboardApi35` erstellt und für begrenzte Ressourcen konfiguriert:
  1536 MiB RAM, zwei Kerne, 480×800, SwiftShader, 1 GiB Datenpartition, keine Snapshots.
- **Kein Emulator-Boot, keine App-Installation und kein Gerätetest erfolgt.**
- Keine laufenden Builds, Tests oder Reviews; keine halb fertige neue App-Funktion.

Die Installation liegt unter `/dev/shm/agent-keyboard-android`, das AVD unter
`/dev/shm/agent-keyboard-avds`. Diese RAM-Dateien sind flüchtig. Eine Kopie der
AVD-Konfiguration und Paketangaben liegt dauerhaft unter
`/workspace/.toolchains/agent-keyboard-emulator`.

Im ursprünglichen SDK zeigen die Verzeichnisse `emulator` und `system-images`
auf diese RAM-Ablage. Nach einem Umgebungsneustart müssen fehlende Ziele neu
aufgebaut oder ausschließlich diese eigenen Symlinks entfernt werden.

## 5. Offene Arbeit in empfohlener Reihenfolge

1. **Echte Android-Prüfung:** vorbereitetes AVD ohne KVM booten, APK installieren,
   IME aktivieren und Tippen/Löschen/Auswahl, Panel/Wheel, Feld-/Appwechsel,
   sensible Felder, Rotation und Abbruchpfade prüfen. Fehler eng korrigieren.
   Ein separates Test-Editor-APK wurde noch nicht erstellt.
2. **Etappe 15 — Werkzeuge:** zunächst klar gekennzeichnetes Testwerkzeug, dann
   Kalenderübergabe mit genauer Ziel-/Datenvorschau und dauerhaftem Intent.
   Übergabe an eine Kalender-App darf nicht als gespeicherter Termin gelten.
3. **Etappe 16 — weitere Anschlüsse:** echte Claude-/Gemini-Adapter und externe
   Dienste einzeln. Gmail, Google Calendar und Notion sind noch nicht angebunden;
   OAuth und Berechtigungen fehlen dafür noch. Keine Behauptung von300 Integrationen.
4. **Etappe 17 — Personalisierung:** Verwaltung lokaler Lerndaten mit Pause,
   Löschen/Export und anschließend getrenntem opt-in Stilprofil. Die sicheren
   Voreinstellungen und Lernunterdrückung existieren bereits.
5. **Etappe 18 — Sprache/lokale KI:** eigene On-device-Diktierfunktion, sichere
   Einfügevorschau und tatsächliche optionale lokale Inferenz mit importiertem
   Modell. Die vorhandene HeliBoard-Sprachtaste delegiert bislang an eine
   System-Sprach-IME; sie ist keine implementierte eigene Offline-Diktierfunktion.
6. **Abschluss:** verbleibende Hardening-/Baselinebefunde, echte Dienstprüfungen,
   wiederholte Gerätetests und Nutzungs-/Installationsdokumentation.

Aktuell stehen damit eine implementierte Tastatur- und Textworkflow-Grundlage
sowie der Act Builder. Die Integrations-, Personalisierungs- und eigenen
Sprach-/lokalen Inferenzpakete sind noch umzusetzen.

## 6. Modell- und Arbeitsaufteilung

Die vorgesehene Auswahl ist in [ROADMAP.md](ROADMAP.md) dokumentiert:

| Aufgabe | Modell / Denkstufe |
| --- | --- |
| Kleine Änderung im bekannten Muster | GPT-6.1 Sol, Low/Medium |
| Normale Etappe und Tests | GPT-6.1 Sol, High |
| Komplexe Zustände/Nebenläufigkeit | GPT-6.1 Sol, Extra High |
| Unabhängiges Architektur-/Datenschutzreview | GPT-6 Astra, High |
| Schwerer reproduzierbarer Fehler | Astra, Extra High; bei Bedarf Max |
| Visuelle Bedienprüfung | GPT-6.1 Terra, Medium/High |
| Extraktion/Übergabevergleich | GPT-6 Luna, High |

Ultra bleibt eine gezielte Option für besonders schwierige getrennte Reviews.
Es gibt weiterhin nur einen schreibenden Agenten. Tatsächlich wurden unabhängige
Lese-Reviews mit Astra und Sol verwendet; ihre Befunde wurden vom Hauptagenten
korrigiert und erneut geprüft.

## 7. Dateien für die Übernahme

Repository: `/workspace/agent-keyboard`, Branch `work/agent-keyboard`.
App-ID der Debug-App: `dev.personal.agentkeyboard.debug`.

- [HANDOFF.md](HANDOFF.md): technische Fortsetzungsinformationen und Grenzen.
- [ROADMAP.md](ROADMAP.md): Etappen und Modellstrategie.
- [ARCHITECTURE.md](ARCHITECTURE.md): Architektur-/Datenschutzentscheidungen.
- [VALIDATION.md](VALIDATION.md): detaillierte Prüfnachweise und Baseline.
- [PROVIDERS.md](PROVIDERS.md): geprüfte API-/Laufzeitverträge.
- [BUILD.md](BUILD.md): Toolchain und Build.
- [AGENTS.md](../AGENTS.md): verbindliche Arbeitsregeln im Repository.

APKs unter `artifacts/` bleiben absichtlich unversioniert:

- `AgentKeyboard_0.1.0-debugNoMinify-arm64-v8a.apk`: ARM64-Telefon.
- `AgentKeyboard_0.1.0-debugNoMinify-x86_64.apk`: x86-64-Emulator.
- Zugehörige `.apk.sha256`-Dateien zur Integritätsprüfung.

Prüfbefehle: `python3 tools/dev.py core-tests`, `agent-tests`, `build`, `lint`
und `upstream-tests`. Der letzte Befehl endet wegen der fünf dokumentierten
Baselinefehler aktuell mit Fehlerstatus; neue Fehler separat prüfen.

Der nächste Agent sollte zunächst diese Übergabe und HANDOFF lesen und bei
beauftragter Fortsetzung mit der vorbereiteten Android-Prüfung beginnen.
Neue API-Schlüssel sind dafür nicht erforderlich.
