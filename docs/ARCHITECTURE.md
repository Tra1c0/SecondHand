# Agent Keyboard — Architekturentscheidungen

## Grundlage

HeliBoards Eingabefläche, Touch-Verarbeitung, Layoutparser, Wörterbücher und
Autokorrektur bleiben zunächst erhalten. Neue Funktionen werden über wenige
Adapter angebunden. Bestehende Compose-Einstellungen bleiben erhalten; die
Tastatur bleibt eine View. Es gibt keinen vorgezogenen Komplettumbau der Engine.

Der erste Meilenstein arbeitet vollständig lokal. Er benötigt weder einen
API-Schlüssel noch eine Netzwerkberechtigung. Eine lokale Texttransformation ist
als lokale Funktion gekennzeichnet und täuscht keine KI-Antwort vor.

## Zuständigkeiten

- Android-App: IME, Einstellungen, Profiladapter, Editoradapter und Darstellung.
- `agent-core`: reine Kotlin-Datentypen, Ausführungsregeln und lokale Aktionen;
  später begrenzte Workflows. Keine Android- oder Netzwerkabhängigkeit.
- Ein Anbietermodul entsteht erst zusammen mit seinem echten Anschluss.

Die vorgesehenen Grenzen sind `EditorAdapter`, `ModelProvider`,
`ExecutionPolicy`, `ProfileStore`, `WorkflowRunner`, `RunStore`, `ToolAdapter`
und `CredentialStore`. Es werden nur die für den aktuellen Schritt benötigten
Grenzen implementiert.

## Kontext und Ergebnisübernahme

Aktion starten → Quelltextvorschau → explizit freigegebener Snapshot →
Policy-Prüfung → Verarbeitung → Ergebnisvorschau → bewusste Übernahme.

- Die erste Version verarbeitet ausschließlich markierten Text mit fester
  Größenbegrenzung. Ohne zuverlässig bestimmbare Auswahl wird nicht ersetzt.
- Snapshots enthalten kopierte Daten, Sitzungsgeneration und Änderungsstand.
  Eine `InputConnection` wird weder gespeichert noch an den Kern weitergereicht.
- Editorwechsel, Neustart, veränderte Auswahl und zwischenzeitliche Eingaben
  machen ausstehende Ersetzungen ungültig, auch wenn der Text später gleich aussieht.
- Vor einer Ersetzung werden Sitzung, Auswahl und Quelltext erneut geprüft.
  Android bietet keine universelle atomare Compare-and-swap-Operation für fremde
  Textfelder; bei Unklarheit bleibt die Ersetzung gesperrt.
- Ergebnisse werden niemals automatisch eingefügt. Das Panel darf keinen neuen
  Editor öffnen und damit den eigentlichen Eingabekontext verlieren.

## Daten und Berechtigungen

- Normales Tippen startet keine Netzwerkanfrage.
- Passwortfelder, Inkognito, gesperrtes Gerät und Lernverbotsfelder sperren
  zusätzliche Datenverarbeitung. Lernen und Cloud-Freigaben bleiben getrennt.
- Personalisierte Wörterbücher und Zwischenablageverlauf starten ausgeschaltet.
- Spätere Schlüssel werden mit einem Android-Keystore-Schlüssel verschlüsselt,
  in credential-protected storage gehalten und von Export/Backup ausgeschlossen.
- Feldinhalte, Ergebnisse, Zugangsdaten und Lerndaten gehören nicht in Logs.
- Persönliche Lerndaten verlassen das Gerät nicht. Erzeugte oder eingefügte
  Texte sind keine stillschweigende Einwilligung zum Stiltraining.
- Berechtigungen gelten für die gesamte APK. Modulgrenzen sind keine zusätzliche
  Betriebssystem-Sandbox; die Policy wird durch Tests nachgewiesen.

## Anpassbarkeit

Design, Layout und Gesten werden getrennt gespeichert. Der bestehende
HeliBoard-Layoutparser bleibt die Quelle für Layoutsyntax. Profile enthalten nur
zugelassene Einstellungen und niemals Berechtigungen, Geheimnisse, Lernverläufe
oder ausführbaren Code. Ungültige Imports werden vor Änderungen verworfen.

Eine neue Darstellung kann später den aktuellen Darstellungsadapter ersetzen.
Die vorhandene View vollständig austauschen zu können ist ein eigener Umbau,
keine bereits erledigte Eigenschaft des Forks.

## Spätere Workflows

Runner und Android-Scheduler bleiben getrennt. Persistenz bedeutet
wiederherstellbaren Zustand, keine garantierte permanente Hintergrundausführung.
Importierte Prompts und Modellantworten erteilen keine Berechtigungen.
Zugelassene Schritte, Providerziele und Budgets prüft eine deterministische Policy.

Vor externen Effekten wird die Absicht persistiert. Unbekannte Ergebnisse werden
als solche gespeichert und ohne Idempotenznachweis nicht automatisch wiederholt.
Ein Prozessneustart führt niemals zu einer automatischen Editor-Ersetzung.
