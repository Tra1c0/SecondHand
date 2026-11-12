# Agent Keyboard – Roadmap (noch nicht gebaut)

Alles, was es noch nicht gibt. Der Owner sortiert. Sitzungen bauen nur, was unter „Beschlossen“
steht oder was der Owner direkt beauftragt, und nur, wenn die Entwicklung nicht pausiert ist
(`docs/HANDOFF.md` → Aktueller Stand). Ist ein Punkt gebaut, wird er hier gestrichen und steht
dann in [FEATURES.md](FEATURES.md). Aufwand: S klein, M mittel, L groß. Die Nummern sind die
Etappen aus dem ursprünglichen Auftrag; Teilpakete bekommen Buchstaben (15a, 15b …).

## Beschlossen, noch nicht gebaut

Reihenfolge von oben nach unten.

Ziel des Owners (2026-11-12): ein „Agentic Keyboard“, das aus dem Gesprächskontext erkennt, was
du tun willst, und es mit einer Geste und einem Tipp erledigt, dazu eine lernfähige
Autokorrektur. Der Owner hat die Entscheidung über Machbarkeit und Reihenfolge abgegeben; das
Folgende ist die Entscheidung des Leads.

- **20 Lernende Autokorrektur, lokal, ohne KI.**
  - 20a ist gebaut (siehe FEATURES.md). Abweichend vom ersten Entwurf gilt der Schutz nur für
    Umlaute, nicht für alle Akzente: Spanische Akzente (esta/está) sind meist aus Bequemlichkeit
    weggelassen, ihre Korrektur bleibt erwünscht.
  - 20b ist gebaut (siehe FEATURES.md). Abweichend vom Entwurf zählt nur ein selbst gewähltes
    Wort aus der Leiste, nicht eine behaltene Autokorrektur: Sonst würde die Tastatur ihre
    eigenen, unbemerkt falschen Korrekturen verstärken. Offen aus dem Entwurf: dass das
    eingebaute Lernen (Personalisierte Vorschläge) häufige, nie korrigierte Tippfehler als Wort
    lernt; ein gelerntes Paar korrigiert sie trotzdem.
  - 20c Seite zum Ansehen, Löschen und verschlüsselten Exportieren der gelernten Paare. (S)
  - Opt-in, nur auf dem Gerät, nicht in geschützten Feldern, ohne Protokollierung.
- **21 Absichten erkennen und mit einem Tipp ausführen.** Leertaste halten öffnet statt des
  Rads (oder in ihm) ein bis drei Aktionsvorschläge zum Text vor der Schreibmarke, zur Auswahl
  oder zu frisch kopiertem Text; eine Aktion braucht genau einen bestätigenden Tipp auf einer
  Karte, die zeigt, was passiert.
  - 21a Datum und Uhrzeit (Deutsch, Englisch, Spanisch, auch „morgen 18 Uhr“, „Samstag halb
    acht“) erkennen und als Termin eintragen: direkt mit Kalenderberechtigung oder wie bisher
    über die Kalender-App. Ersetzt U3. (M)
  - 21b Orte: Maps-Link zu erkannter oder markierter Adresse, zu selbst gespeicherten Orten
    („Zuhause“, „Arbeit“), zu Kontakt-Adressen (Kontakte-Berechtigung) und optional zum
    aktuellen Standort (Standort-Berechtigung, jedes Mal sichtbar bestätigt). Der Link wird
    eingefügt, nicht gesendet. (M)
  - 21c Androids eingebaute Texterkennung (TextClassifier, auf dem Gerät) als zusätzliche Quelle
    für Adressen, Termine, Telefonnummern; ob das Xiaomi sie anbietet, muss am Gerät geprüft
    werden. (S)
  - 21d Wenn nichts lokal passt: „KI fragen“ mit dem eigenen Schlüssel, wie alle Cloud-Aktionen
    nur nach Freigabe des genau angezeigten Texts. Später mit lokaler KI (18b). (M)
  - Grenze: Die Tastatur sieht nur das eigene Eingabefeld, nicht die Nachricht des
    Gegenübers. Für fremde Nachrichten: Nachricht kopieren, dann Leertaste halten.
- **22 Persönliche Trefferzonen (Wunsch des Owners vom 2026-11-12).** Die Tastatur lernt, wo du
  jede Taste tatsächlich triffst, und verschiebt unsichtbar die Erkennungszonen; die Tasten
  selbst bleiben an ihrem Platz, damit das Muskelgedächtnis nicht gestört wird.
  - 22a ist gebaut (siehe FEATURES.md).
  - 22b ist gebaut (siehe FEATURES.md).
  - 22c Dieselben Werte auch für das Korrekturmodell der Autokorrektur. (S)
  - Gespeichert werden nur Mittelwerte pro Taste, keine Texte und keine Tippfolgen; opt-in,
    nur auf dem Gerät. Berührt die Touch-Verarbeitung: nur eng und mit Prüfer.
- **19f Flacher Look am Gerät.** 19a–19e (Tasten, Leiste, Menü, Zwischenablage, Panel) sind
  gebaut, aber nur mit Tests und gerenderten Bildern geprüft. Offen: Vergleich auf dem Telefon
  des Owners mit Bildschirmfotos (Dichte, Systemschrift, Abstand zur Navigationsleiste,
  Tastenvorschau), danach Feinjustierung. Aus der Vorlage bewusst nicht übernommen: GIF, Sticker,
  „Tastatur teilen“, automatisches Senden ohne Freigabe, der umlaufend animierte Leuchtrand
  der Karte, Laufschrift in den Menü-Pillen, mehrere Menüseiten. (S)
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
- **U4** Diktiertes direkt an eine KI-Aktion weitergeben (z. B. diktieren und korrigieren). (M)

## Unsortiert

- (leer)

## Bewusst nicht im Umfang

Vereinbart für dieses private Projekt: keine öffentliche Community, keine Konten, kein Backend,
keine Synchronisierung. Austausch läuft über Dateien. Keine Behauptung von hunderten
Integrationen.
- Lerndaten auf einem Server, auch verschlüsselt (2026-11-12 entschieden): Lernen geschieht
  ohnehin auf dem Gerät; ein Server brächte nur Risiko. Sicherung über verschlüsselte
  Exportdatei.
- Bildschirm anderer Apps mitlesen (Bedienungshilfe-Dienst) oder Benachrichtigungen mitlesen:
  zu weitreichender Zugriff auf alles Angezeigte. Ersatz: Nachricht kopieren.
- Aktionen ganz ohne Bestätigung ausführen: eine Fehlerkennung würde still falsche Termine
  anlegen oder Orte verschicken.
