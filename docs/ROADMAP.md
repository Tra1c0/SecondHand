# Agent Keyboard – Roadmap (noch nicht gebaut)

Alles, was es noch nicht gibt. Der Owner sortiert. Sitzungen bauen nur, was unter „Beschlossen“
steht oder was der Owner direkt beauftragt, und nur, wenn die Entwicklung nicht pausiert ist
(`docs/HANDOFF.md` → Aktueller Stand). Ist ein Punkt gebaut, wird er hier gestrichen und steht
dann in [FEATURES.md](FEATURES.md). Aufwand: S klein, M mittel, L groß. Die Nummern sind die
Etappen aus dem ursprünglichen Auftrag; Teilpakete bekommen Buchstaben (15a, 15b …).

## Beschlossen, noch nicht gebaut

Reihenfolge von oben nach unten.

- **G2 Restlicher Gerätetest.** Was G1 nicht abdeckt (Liste: `docs/VALIDATION.md` → G1 → Nicht
  geprüft): Rotation, TalkBack, Gesten, Feld- und App-Wechsel, Inkognito, Sperre, Direct Boot,
  Skill Keys, Workflows, Act Builder, dazu Diktat mit echtem Mikrofon und die Seite „Gelernte
  Wörter“ mit echten Lerndaten. Am besten auf einem echten Telefon; im Emulator ohne KVM
  dauert jeder Schritt Minuten. (M)
- **16c Externe Dienste, einzeln.** Gmail, Google Calendar und Notion, jeweils mit eigener
  Anmeldung (OAuth) und Freigabe. Blockiert, bis der Owner eigene OAuth-Zugänge bei Google und
  Notion registriert hat. Kein Modell darf sich selbst Fähigkeiten oder Zugangsdaten geben. (L)
- **17b Stilprofil.** Ein getrenntes Stilprofil, nur mit ausdrücklicher Zustimmung: Die
  Tastatur merkt sich auf dem Gerät, wie du schreibst (z. B. Anrede, Satzlänge, typische
  Wendungen), und gibt das auf Wunsch den KI-Aktionen als Stilhinweis mit. Erzeugter oder
  eingefügter Text zählt nie als Zustimmung. Vor dem Bau klären: woraus gelernt wird und was
  davon an den KI-Anbieter geht. (M)
- **18b Lokale KI.** Echte optionale lokale KI mit einem selbst importierten Modell, ohne
  automatische Downloads, mit begrenztem Speicher und begrenzter Ausgabe. Braucht eine gepinnte
  native Laufzeit (z. B. llama.cpp) für alle ABIs und ein Gerät mit Modell zum Prüfen; ohne
  beides wird nichts als fertig gemeldet. (L)
- **A Abschluss.** Restliche Härtungen und Lint-Befunde, echte Dienstprüfungen mit eigenem
  Schlüssel, wiederholte Gerätetests. Die Anleitung (`docs/ANLEITUNG.md`) gibt es schon; sie
  wird nach dem ersten Test auf einem echten Telefon nachgezogen. (M)

## Eventuell später (nicht entschieden)

- **U1** Ein eigenes kleines Test-Editor-APK für reproduzierbare Gerätetests. (S)
- **U2** Werkzeuge auch als Workflow-Schritt und auf Skill Keys. (M)
- **U3** Kalenderentwurf mit erkanntem Datum und erkannter Uhrzeit. (M)
- **U4** Diktiertes direkt an eine KI-Aktion weitergeben (z. B. diktieren und korrigieren). (M)

## Unsortiert

- (leer)

## Bewusst nicht im Umfang

Vereinbart für dieses private Projekt: keine öffentliche Community, keine Konten, kein Backend,
keine Synchronisierung. Austausch läuft über Dateien. Keine Behauptung von hunderten
Integrationen.
