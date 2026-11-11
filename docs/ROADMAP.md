# Agent Keyboard – Roadmap (noch nicht gebaut)

Alles, was es noch nicht gibt. Der Owner sortiert. Sitzungen bauen nur, was unter „Beschlossen“
steht oder was der Owner direkt beauftragt, und nur, wenn die Entwicklung nicht pausiert ist
(`docs/HANDOFF.md` → Aktueller Stand). Ist ein Punkt gebaut, wird er hier gestrichen und steht
dann in [FEATURES.md](FEATURES.md). Aufwand: S klein, M mittel, L groß. Die Nummern sind die
Etappen aus dem ursprünglichen Auftrag; Teilpakete bekommen Buchstaben (15a, 15b …).

## Beschlossen, noch nicht gebaut

Reihenfolge von oben nach unten.

- **G1 Echter Gerätetest.** Emulator neu aufsetzen (ohne KVM), APK installieren, Tastatur
  aktivieren und alles von der Checkliste in `docs/VALIDATION.md` prüfen: Tippen, Löschen,
  Auswahl, Panel und Rad, Feld- und App-Wechsel, sensible Felder, Rotation, Abbrüche. Gefundene
  Fehler eng korrigieren. Kein API-Schlüssel nötig. (M)
- **15 Werkzeuge.** Zuerst ein klar gekennzeichnetes Testwerkzeug, danach die Übergabe an eine
  Kalender-App mit genauer Vorschau von Ziel und Daten. Die Übergabe gilt nicht als
  gespeicherter Termin. Keine automatischen Wiederholungen. (M)
- **16 Weitere Anschlüsse, einzeln.** Echte Claude- und Gemini-Anbindung, danach externe Dienste
  wie Gmail, Google Calendar und Notion, jeweils mit eigener Anmeldung (OAuth) und Freigabe.
  Kein Modell darf sich selbst Fähigkeiten oder Zugangsdaten geben. Die API-Verträge in
  `docs/PROVIDERS.md` vorher neu prüfen. (L)
- **17 Personalisierung.** Lokale Lerndaten verwalten: Pause, Löschen, Export. Danach ein
  getrenntes Stilprofil, nur mit ausdrücklicher Zustimmung. Erzeugter oder eingefügter Text
  zählt nie als Zustimmung. (M)
- **18 Sprache und lokale KI, getrennt.** Eigenes Diktat auf dem Gerät (Android 12+, ohne
  stillen Rückfall auf einen Cloud-Erkenner) mit sicherer Einfügevorschau. Danach echte
  optionale lokale KI mit einem selbst importierten Modell, ohne automatische Downloads. Die
  heutige Sprachtaste gibt nur an eine andere Sprach-Tastatur weiter. (L)
- **A Abschluss.** Restliche Härtungen und Lint-Befunde, echte Dienstprüfungen mit eigenem
  Schlüssel, wiederholte Gerätetests, Anleitung für Installation und Nutzung. (M)

## Eventuell später (nicht entschieden)

- **U1** Ein eigenes kleines Test-Editor-APK für reproduzierbare Gerätetests. (S)

## Unsortiert

- (leer)

## Bewusst nicht im Umfang

Vereinbart für dieses private Projekt: keine öffentliche Community, keine Konten, kein Backend,
keine Synchronisierung. Austausch läuft über Dateien. Keine Behauptung von hunderten
Integrationen.
