# Agent Keyboard — Entwicklungsstand

Persönliche Android-Tastatur auf Basis von HeliBoard. Genau ein Agent verändert
Code; unabhängige Reviews dürfen parallel lesen. Jede Etappe bekommt einen
eigenen Commit und einen nachvollziehbaren Prüfnachweis. Ein offener Gerätetest
gilt nicht als bestanden.

## Festgelegte Grundlage

- Upstream: HeliBorg/HeliBoard, Tag `v4.2`.
- Commit: `0c60dffb03a4e2e7a37cb82210f5299f93c03979`.
- JDK 17; unveränderte Gradle-/AGP-/Kotlin-Versionen aus dem Upstream-Snapshot.
- Android persönlich; eigene API-Schlüssel; OpenAI zuerst.
- Lernen lokal und opt-in. Cloud-Aufrufe ausschließlich nach bewusstem Start.
- Keine automatisierten Beiträge oder Nachrichten an das Upstream-Projekt.

## Etappen

| Nr. | Ergebnis | Stand |
| --- | --- | --- |
| 0 | Reproduzierbarer unveränderter Ausgangsbuild | Build bestanden; Testbaseline erfasst; Gerätetest offen |
| 1 | Eigene App-Identität, sichere Voreinstellungen | Implementiert; Build und betroffene Tests bestanden; Gerätetest offen |
| 2 | Reiner Kotlin-Kern, Provider- und Policy-Grenzen | Implementiert; 16 Kernprüfungen und App-Build bestanden |
| 3 | Getrennte Design-, Layout- und Gestenprofile | Drei Bereiche einzeln implementiert und geprüft; visuelle Geräteprüfung offen |
| 4 | Sicherer Editorzugriff mit lokaler Verarbeitung | Implementiert; 20 Kern- und 29 Android-/Integrationstests bestanden |
| 5 | Toolbar-Aktionspanel mit Vorschau und Übernahme | Implementiert; 36 gezielte Tests und ARM64-Build bestanden; Touch-/TalkBack-Geräteprüfung offen |
| 6 | Radiales Wheel | Implementiert; 40 gezielte Tests, gerenderte Vorschau und ARM64-Build bestanden |
| 7 | Optionale Leertastengeste | Implementiert; 20 Kern- und 49 gezielte Android-Tests sowie ARM64-Build bestanden |
| 8 | Verschlüsselte Schlüsselverwaltung | Implementiert; 61 gezielte Tests, ARM64-Build und Lint bestanden; echte Keystore-/Direct-Boot-Prüfung offen |
| 9 | Erste echte OpenAI-Aktion: Korrigieren | Implementiert; 74 gezielte Tests, ARM64-Build und Lint bestanden; echte API-/Geräteprüfung offen |
| 10 | Weitere Textaktionen, einzeln umgesetzt | Alle sechs Aktionen implementiert; 21 Kern- und 79 gezielte Android-Tests, ARM64-Build bestanden |
| 11 | Skill Keys und versionierte Aktionsdateien | 11a–c implementiert; 91 gezielte Tests, ARM64-Build und Lint bestanden |
| 12 | Wiederherstellbare Auftragszustände | 12a–b implementiert; 103 gezielte Tests, ARM64-Build und Lint bestanden |
| 13 | Begrenzte deklarative Workflows, danach Bedingungen | 13a–g inklusive8Steps/3AI, Bedingungen, Skill-Verweisen, Laufmetadaten und gezielter Konfigurations-Recovery fertig (48/165 Tests) |
| 14 | Act Builder als validierter Workflow-Entwurf | 14a–c implementiert;189 gezielte Tests/Build/Lint, Vollregression370/5Baseline; Geräte-/API-Prüfung offen |
| 15 | Testwerkzeug, danach optionale Kalenderübergabe | Offen |
| 16 | Weitere Anbieter und externe Dienste, je ein Adapter | Offen |
| 17 | Lokales Wörterbuch, danach getrenntes Stilprofil | Offen |
| 18 | Diktat, Sprachverarbeitung und lokale Modelle, einzeln | Offen |

## Arbeitsregeln

1. Ein Arbeitspaket fügt genau ein prüfbares Verhalten hinzu.
2. Ein großer Architekturumbau und neue Funktionen bekommen getrennte Pakete.
3. Build und betroffene Tests müssen vor dem nächsten abhängigen Paket laufen.
4. Vor Meilensteinfreigaben sind echte Android-Gerätetests notwendig.
5. Jede Übergabe dokumentiert Änderungen, Befunde, offene Prüfungen und nächsten
   Schritt. Fehlende API- oder Geräteprüfungen werden ausdrücklich benannt.

## Modellwahl

| Arbeit | Modell | Denkstufe |
| --- | --- | --- |
| Kleine Änderung im bekannten Muster | GPT-6.1 Sol | Low / Medium |
| Normale Etappe und passende Tests | GPT-6.1 Sol | High |
| Nebenläufigkeit und komplexe Zustände | GPT-6.1 Sol | Extra High |
| Architektur, Datenschutz und Berechtigungsreview | GPT-6 Astra | High |
| Schwerer reproduzierbarer Fehler | GPT-6 Astra | Extra High, bei Bedarf Max |
| Visuelle Bedienprüfung | GPT-6.1 Terra | Medium / High |
| Extraktion, Checklisten und Übergabevergleich | GPT-6 Luna | High |

Ultra bleibt optional für getrennte Lese-Reviews. Es gibt weiterhin nur einen
schreibenden Agenten. Modelle in der fertigen App werden davon unabhängig gewählt.
