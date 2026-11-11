# Agent Keyboard – Regeln für jede Sitzung

Private Android-Tastatur auf Basis von HeliBoard 4.2 mit Aktionspanel, Aktionsrad, Skill Keys,
eigenen Text-Workflows und optionaler KI über den eigenen OpenAI-Schlüssel. Diese Regeln gelten
für jede Sitzung. Jede Regel steht nur hier und nur einmal, Details stehen in der jeweils
genannten Datei. `AGENTS.md` ist eine automatisch erzeugte Kopie dieser Datei für Werkzeuge, die
`AGENTS.md` lesen. Bearbeite immer `CLAUDE.md`, nie `AGENTS.md`.

## Leseordnung (nur lesen, was die Aufgabe braucht)

1. Die Ausgabe beim Sitzungsstart: aktueller Stand, Startzeilen der Abschnitte von
   `docs/ARCHITECTURE.md` und Zusammenfassung des Docs-Checks.
2. `docs/HANDOFF.md`: Stand, offene Punkte, Arbeitsweise, Sackgassen.
3. `docs/ARCHITECTURE.md`: nur den Abschnitt des Bereichs, den du änderst, vor jedem Review
   zusätzlich „Fallstricke“. Diese Datei ist die Code-Landkarte. Durchsuche den Code nicht von
   Grund auf, wenn die Landkarte die Frage beantwortet.
4. `docs/FEATURES.md` (was es gibt) und `docs/ROADMAP.md` (was noch nicht gebaut ist), beide
   für den Owner geschrieben.
5. Nachschlagewerke, nur bei Bedarf: `docs/VALIDATION.md` (Prüfnachweise je Etappe),
   `docs/STORAGE.md`, `docs/WORKFLOWS.md`, `docs/PROVIDERS.md`, `docs/BUILD.md`.
   `docs/UEBERGABE-2026-11-11.md` ist ein abgeschlossener Bericht und wird nicht mehr gepflegt.

## Begriffe

- **Owner**: die Person, für die das Projekt gebaut wird. Schreibt Deutsch.
- **Lead**: die Sitzung, die mit dem Owner spricht. Sie ist die einzige schreibende Sitzung.
  Sie ändert Code, Tests und Docs, committet und pusht.
- **Prüfer**: eine Sitzung, die der Lead für ein unabhängiges Review startet. Sie liest nur und
  meldet Befunde. Der Lead behebt die Befunde und lässt sie erneut prüfen.
- **Etappe**: ein abgegrenztes, einzeln geprüftes und einzeln committetes Arbeitspaket. Die
  Nummern 0–18 stehen in `docs/FEATURES.md` (erledigt) und `docs/ROADMAP.md` (offen);
  Teilpakete heißen z. B. 14a, 14b, 14c.
- **Runde**: alle Etappen, die der Owner in einem Auftrag freigibt, bis zum Push nach `main`.
- **Upstream**: HeliBoard, festgeschrieben in `upstream.lock` (Tag `v4.2`). **Fork**: dieses
  Repository.
- **Build**: eine Debug-APK, die `python3 tools/dev.py build` lokal nach `artifacts/` schreibt
  (nicht versioniert) und die der Owner manuell installiert. Es gibt keine Release-Pipeline.
- **Gerätetest**: eine Prüfung auf einem echten Android-Gerät oder Emulator. Robolectric-Tests
  sind keine Gerätetests.

## Do

- Antworte dem Owner auf Deutsch, knapp. `CLAUDE.md`, `docs/` und `release-notes/` sind auf
  Deutsch. Code-Kommentare, Commit-Nachrichten und die README (Fork-Hinweis über der
  Upstream-Doku) sind auf Englisch.
- Baue nur, was der Owner beauftragt oder was in `docs/ROADMAP.md` unter „Beschlossen“ steht,
  und nur, wenn die Entwicklung nicht pausiert ist (`docs/HANDOFF.md` → Aktueller Stand).
- Arbeite in Etappen: eine Etappe fügt genau ein prüfbares Verhalten hinzu. Ein großer Umbau und
  eine neue Funktion sind getrennte Etappen. Prüfe und committe jede Etappe, bevor eine davon
  abhängige beginnt.
- Führe vor jedem Code-Commit die betroffenen Prüfungen aus (Befehle: `docs/ARCHITECTURE.md` →
  Build, Tests, CI, Docs-System). Halte Infrastrukturabbrüche und die geerbten Testfehler
  ehrlich fest.
- Halte normales Tippen offline. Cloud-Aufrufe gibt es nur nach einer ausdrücklichen,
  einmaligen Freigabe des genau angezeigten Inhalts.
- Lernen, Stiltraining und Zwischenablageverlauf bleiben opt-in. Neue Funktionen respektieren
  sensible Felder, Inkognito und den Sperrzustand des Geräts.
- Kennzeichne lokale Textwerkzeuge als lokal.
- Lege API-Schlüssel ausschließlich über die App ab (eigener Schlüssel des Owners, „BYOK“).
- Ändere `InputLogic`, `RichInputConnection` und die Touch-Verarbeitung (`PointerTracker`) nur
  eng begrenzt und lass jede solche Änderung von einem Prüfer lesen.
- Lead, im selben Push wie die Änderung: Docs laut Tabelle unten aktualisieren und
  `python3 -I scripts/docs_sync.py --check` grün bekommen (`--write` erzeugt die generierten
  Blöcke und `AGENTS.md` neu).
- Setze Status-Buchstaben in `docs/FEATURES.md` nur nach dem, was wirklich geprüft wurde.

## Don't

- Behaupte keine Prüfung, die nicht stattgefunden hat. Fehlende Geräte- oder Live-API-Tests
  gelten nicht als bestanden.
- Entferne oder schwäche keine Assertion und schließe keinen Test aus, um einen Fehler zu
  verdecken.
- Erzeuge keine vorgetäuschten KI-Ergebnisse.
- Logge keine Feldinhalte, Ergebnisse, Prompts, Zugangsdaten oder Lerndaten.
- Ersetze Editortext nie automatisch. Jede Übernahme braucht einen eigenen Klick des Nutzers.
- Lass keine Modellantwort und keinen Import Werkzeuge, URLs, Provider, Zugangsdaten oder
  Berechtigungen hinzufügen.
- Erstelle keine Issues, Pull Requests oder Kommentare beim Upstream-Projekt. Dessen
  `AI_USAGE.md` und `CONTRIBUTING.md` regeln Beiträge zu HeliBoard, nicht diesen privaten Fork.
- Committe keine APKs, Toolchains, Keystores oder Geheimnisse.
- Prüfer: bearbeiten keine Dateien und pushen nicht. Sie enden mit einem Bericht: Befunde mit
  Datei und Zeile, Schweregrad, neue oder umbenannte Dateien mit einem Satz Zweck.
- Pushe nicht nach `main`, bevor die Runde geprüft ist.
- Bearbeite keinen Text zwischen `<!-- gen:… -->` und `<!-- /gen:… -->`, er wird erzeugt.

## Docs aktuell halten (Lead, im selben Push)

| Wenn du … | aktualisiere |
| --- | --- |
| eine Funktion hinzufügst, änderst oder entfernst | `docs/FEATURES.md`, `docs/ROADMAP.md` |
| eine Datei, einen Hook oder einen Fallstrick hinzufügst, umbenennst oder löschst | `docs/ARCHITECTURE.md` (ihr Bereich) |
| eine bisher unveränderte Upstream-Datei änderst | `map.sources` in `scripts/docs_sync.json` und `docs/ARCHITECTURE.md` → Geänderte Upstream-Dateien |
| eine Etappe abschließt | `docs/VALIDATION.md` (Prüfnachweis der Etappe) |
| Speicherort, Dateiformat, Provider-Vertrag oder Toolchain änderst | `docs/STORAGE.md`, `docs/WORKFLOWS.md`, `docs/PROVIDERS.md` bzw. `docs/BUILD.md` |
| Code pushst, der ausgeliefert wird | `release-notes/next.md` (durch die Notizen dieses Pushs ersetzen) |
| eine Runde beendest | `docs/HANDOFF.md` (Stand, offene Punkte, Sackgassen, eine Zeile im Build-Log) |

Starte lieber pro Runde eine neue Sitzung als eine endlose Sitzung, denn der Kontext läuft voll
und wird zusammengefasst.
