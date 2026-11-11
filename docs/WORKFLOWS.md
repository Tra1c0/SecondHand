# Workflows

## Aktueller Stand13e

Bis zu acht geordnete Schritte und drei Modellaufrufe; Format3. Ein Eingang ist
die freigegebene Auswahl oder exakt das Ergebnis eines früheren Schritts, auch
nicht unmittelbar vorher. Keine Zyklen/Vorwärtsverweise. V1/V2 bleiben bei ihren
alten Zwei-Schritt-Grenzen. Textbudget32.768, Modellinput8.192, Ausgabe16.384,
Gesamtbudget95s; langsame/große Abläufe können kontrolliert scheitern. Neue
Schritte wählen standardmäßig den zuletzt angelegten Schritt. Alle Ansichten
beschreiben den tatsächlichen Ursprung mit Schrittnummer.

## Etappe 13a: reiner Ausführungskern

Ein Ablauf besteht zunächst aus höchstens zwei geordneten Schritten. Ein Schritt
liest die freigegebene Auswahl oder das Ergebnis eines vorherigen Schritts.
Vorwärtsverweise, Zyklen und doppelte IDs sind ungültig. Die letzte Ausgabe ist
das Ergebnis. Die Definition wird bei Erstellung defensiv kopiert und eingefroren.

- Lokale Schritte: Großschreibung, Kleinschreibung, Leerzeichenbereinigung.
- KI-Schritte: dieselben sechs festen Textaktionen wie im Tastaturpanel;
  Anbieter, Modell, Zielsprache und Ton sind Bestandteil der Definition.
- Keine URLs, beliebiger Code, Credentials, Toolaufrufe oder Editorberechtigungen.
- Auswahl und Modellinput höchstens 8.192 UTF-16-Zeichen, einzelne Ausgabe
  höchstens 16.384, alle behaltenen Texte zusammen höchstens 32.768.
- Höchstens zwei Modellaufrufe; alle benötigten Anbieter werden vor dem ersten
  Schritt auf Unterstützung geprüft. Import erteilt keine Fähigkeiten.
- Analyseausgaben sind entlang ihrer Abhängigkeiten nicht ersetzbar.

`ModelRequest` enthält nur Textdaten. Der Kompatibilitätskonstruktor aus einem
`TextSnapshot` kopiert ausschließlich dessen Text; Token und Editorzugriff bleiben
im Android-Controller. Kein künstlicher Editor-Snapshot für Zwischenergebnisse.

Konfiguration ist seit13b2 in den Einstellungen verfügbar. Der Start aus dem
Tastaturpanel ist seit13b3b angeschlossen. Der Android-Host muss vor
Ausführung die vollständige Definition und Datenweitergabe bestätigen lassen,
Editoraktualität an Schrittgrenzen prüfen sowie Zeitlimit, Abbruch und Journal
bereitstellen. Er darf erst das gesamte Ergebnis als Vorschau veröffentlichen.
Ein Fehler veröffentlicht kein teilweise ausgeführtes Ergebnis.

## Nächste getrennte Pakete

1. Erledigt: strikter CE-Konfigurationsspeicher mit versionierter Datei,
   Revisionsnummern und zusätzlichem Generationstoken gegen veraltete Mutationen.
2. Erledigt: manuelle Zusammenstellung und geprüfter Import/Export. Nächster
   Teil13b3 ebenfalls erledigt: native Auswahl/Freigabe/Ausführung.
3. Danach größere begrenzte Abläufe und Bedingungen mit ausdrücklicher Migration.
4. Natürlichsprachlicher Builder erzeugt später nur einen geprüften Entwurf.

Neue Werkzeuge oder Anbieter werden einzeln angeschlossen. Ergebnistext kann
weder Capability-Listen erweitern noch externe Aktionen autorisieren. Nach
Prozessverlust findet keine automatische Wiederholung oder Editorübernahme statt.

Seit13b3a implementiert ApprovedActionRunner die Ablauf-Ausführung mit gemeinsamem
JournaledModelCall. Nach Intent-Persistenz erneut Editor/Abbruch prüfen; Gesamtbudget
95s, Timeout als Fehler und unbeantwortete Anfrage UNKNOWN. Der konkrete Job wird
an allen Schrittgrenzen geprüft, Zwischenresultate bleiben intern. ProAI-Schritt
wird ein Metadatenauftrag erfasst; eine gemeinsame Workflow-Lauf-ID ist noch offen.
Native Freigabe/Oberfläche sind seit13b3b angeschlossen.

## Etappe13c1a: Bedingungen im Kern

Schritte können drei deterministische Prädikate auf ihre deklarierte Eingabe
anwenden: nicht leer, enthält einen festen Text (case sensitive), Mindestlänge
in UTF-16-Einheiten. Optionale Negation. Falsch bedeutet unveränderte Weitergabe,
kein Modell und keine erneute Textbudgetbelastung. Analyse-Schutz wird nur für
wirklich ausgeführte Analysen erzeugt, aber nie aus eingehenden Daten entfernt.
Die Ergebnisanzeige nennt tatsächliche Modellaufrufe und übersprungene Schritte.
Seit13c1b speichert und exportiert Format2 diese Regeln. Format1 wird ausdrücklich
bedingungslos migriert; ein v1-Dokument mit neuen Regel-Feldern wird abgewiesen.
Der Editor hält unfertige Eingaben als Draft, bis vollständige Validierung möglich
ist. Importierte Bedingungen bleiben erhalten. Same-Predicate-Auswahl setzt nichts zurück.

## Skill-Verweise (13d2)

Drei Skill-Plätze können per UUID auf gespeicherte Workflows verweisen. Skill-Dateien
exportieren lediglich diese Referenz; Workflowdateien separat. Der Tastendruck liest
die aktuelle Definition und zeigt tatsächliche Schritte/Modelle zur einmaligen
Freigabe. Ein Alias erteilt keine Berechtigung. Fehlende IDs bleiben ohne Fallback;
Import/Umbenennen/Anheften führt keinen Ablauf aus.
