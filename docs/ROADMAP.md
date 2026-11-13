# Agent Keyboard – Roadmap (noch nicht gebaut)

Alles, was es noch nicht gibt. Der Owner sortiert. Sitzungen bauen nur, was unter „Beschlossen“
steht oder was der Owner direkt beauftragt, und nur, wenn die Entwicklung nicht pausiert ist
(`docs/HANDOFF.md` → Aktueller Stand). Ist ein Punkt gebaut, wird er hier gestrichen und steht
dann in [FEATURES.md](FEATURES.md). Aufwand: S klein, M mittel, L groß. Die Nummern sind die
Etappen aus dem ursprünglichen Auftrag; Teilpakete bekommen Buchstaben (15a, 15b …).

## Beschlossen, noch nicht gebaut

Reihenfolge von oben nach unten.

- **27 Symbole malen** (Auftrag des Owners vom 2026-11-13) ist gebaut (siehe FEATURES.md). Offen:
  Gerätetest (Erkennung mit echten Fingern, Schwellen, Spur, Tempo, Malfeld der eigenen Symbole),
  Prüfer-Review der PointerTracker-Hooks (Pflicht).

Ziel des Owners (2026-11-12): ein „Agentic Keyboard“, das aus dem Gesprächskontext erkennt, was
du tun willst, und es mit einer Geste und einem Tipp erledigt, dazu eine lernfähige
Autokorrektur. Der Owner hat die Entscheidung über Machbarkeit und Reihenfolge abgegeben; das
Folgende ist die Entscheidung des Leads.

- **24 Befehle in der Tastatur (Auftrag des Owners vom 2026-11-12, vor 23e/23f).** Tippst du
  einen Befehl wie `@math` am Wortanfang, zeigt die Leiste passende Chips; erst ein Tipp auf einen
  Chip löscht den Befehlstext und führt aus. Ein bloßes `@` zeigt alle Befehle, ein angefangener
  Name die passenden. E-Mail-Adressen und Erwähnungen (`a@b`, `@lewin`) lösen nichts aus. Der
  Präfix ist einstellbar (Standard `@`, nur Zeichen, die keine Worttrenner sind, damit die
  Autokorrektur den Befehl nicht ändert). Jeder Befehl ist einzeln abschaltbar. Nie in Passwort-
  feldern, im Inkognito-Modus oder bei gesperrtem Gerät. Befehlstexte werden nicht gelernt.
  - 24a ist gebaut (siehe FEATURES.md).
  - 24b ist gebaut (siehe FEATURES.md), einschließlich des Ergebnis-Chips für `@math 12*3,5`.
  - 24c, 24d, 24e und aus 24f `@such`, `@zeichen`, `@emoji`, `@conv`, `@snip`, `@clip`, `@wf`,
    `@datum`, `@termin`, `@ort`, `@ki`, `@qr`, `@translate`, `@wecker` und `@timer` sind gebaut (siehe FEATURES.md).
  - 24g Sprachbefehle (`@lang`, `@sp`, `@ger`, `@eng` mit Tippfehlertoleranz, Auftrag vom
    2026-11-13) ist gebaut (siehe FEATURES.md). Offen: ein Land wählt nur die Sprache, nicht den
    passenden Subtyp (`@uk` bietet alle aktiven Englisch-Subtypen an). Offen aus 24c–24f:
    Rückfall Screenshot mit Texterkennung für `@url`, wenn eine App Text als Grafik zeichnet;
    `@clip` erkennt nur den aktuellen Clip als vertraulich (der Upstream-Verlauf speichert die
    Markierung nicht, Änderung braucht eine Entscheidung); `@wf` bietet den gewählten Workflow erst
    nach dem Markieren an; `@ort` ohne Kontakt-Adressen und Standort.
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
- **23 Gboard-Details und moderne Oberfläche (Auftrag des Owners vom 2026-11-12, mit Screenshots).**
  - 23a ist gebaut (siehe FEATURES.md).
  - 23b ist gebaut (siehe FEATURES.md).
  - 23c ist gebaut (siehe FEATURES.md).
  - 23d ist gebaut (siehe FEATURES.md).
  - 23g „Screenshot zuerst“ (Auftrag des Owners vom 2026-11-13) ist gebaut (siehe FEATURES.md).
  - 23e ist gebaut (siehe FEATURES.md). Vorlage war (Screenshot des Owners, nicht im Repo):
    - große Überschrift oben links, die beim Scrollen kleiner wird;
    - Hauptliste mit großen Einträgen: rundes oder flaches Icon links, Titel groß, keine
      Untertitel-Flut, viel Abstand;
    - dunkler Hintergrund passend zum flachen Look.
    Beobachtet auf dem Screenshot (2026-11-12): Titel groß links, Outline-Icons einfarbig ohne
    Kreis, nur „Sprachen“ hat einen Untertitel, sehr hohe Zeilen ohne Trennlinien, warm-dunkler
    Hintergrund (vermutlich dynamische Systemfarbe). Ob der Titel beim Scrollen schrumpft, zeigt
    das Standbild nicht.
    Betrifft vor allem `MainSettingsScreen.kt` und die gemeinsamen Compose-Bausteine
    (`Preference`, `SearchSettingsScreen`). Upstream-Dateien nur eng ändern, besser eigene
    Bausteine mit Weiche auf den flachen Stil.
  - 23f1 und 23f2 sind gebaut (siehe FEATURES.md). Offen nur noch der Gerätetest (Rundung,
    Schrift und Hintergrundbild auf einem echten Telefon ansehen). Ursprünglich: Vorlage: ein Raster aus Kacheln, jede eine kleine
    gerenderte Tastatur im jeweiligen Design (Farben, Tastenform), Antippen wählt das Design.
    Der Owner will darüber hinaus mehr Optionen als Gboard (Farben, Rundung, Abstände,
    Schrift, Hintergrund), jeweils mit Live-Vorschau beim Ändern. Beobachtet auf dem Screenshot
    (2026-11-12): Zurück-Pfeil, Titel „Design“, Abschnittstitel in Akzentfarbe; Abschnitte „Meine
    Designs“ (Kachel „+“ und eigene), „Standard“ (Dynamische Farbe, Systemeinstellung als halb
    helle, halb dunkle Kachel, Standard, Dunkel, mit Beschriftung darunter), dann aufklappbare
    Gruppen (Farben, Landschaften, heller und dunkler Farbverlauf). Die Kacheln sind keine
    gerenderte Tastatur, sondern Hintergrundfarbe, ein Balken als Leertaste und ein Punkt in
    Akzentfarbe als Enter; Auswahl durch Rahmen in Akzentfarbe und rundes Häkchen. Das macht 23f1
    eher zu (M). Bilder zum Herunterladen (Landschaften) passen nicht zu „keine automatischen
    Downloads“: höchstens ein selbst gewähltes Bild als Hintergrund. Vorschlag: zuerst 23f1 Kacheln
    für die vorhandenen Themes und die Vorlage „Flacher Look“, dann 23f2 Editor mit Vorschau.
- **22 Persönliche Trefferzonen (Wunsch des Owners vom 2026-11-12).** Die Tastatur lernt, wo du
  jede Taste tatsächlich triffst, und verschiebt unsichtbar die Erkennungszonen; die Tasten
  selbst bleiben an ihrem Platz, damit das Muskelgedächtnis nicht gestört wird.
  - 22a ist gebaut (siehe FEATURES.md).
  - 22b ist gebaut (siehe FEATURES.md).
  - 22c Dieselben Werte auch für das Korrekturmodell der Autokorrektur. (S)
  - Gespeichert werden nur Mittelwerte pro Taste, keine Texte und keine Tippfolgen; opt-in,
    nur auf dem Gerät. Berührt die Touch-Verarbeitung: nur eng und mit Prüfer.
- **26 RGB-Beleuchtung (Auftrag des Owners vom 2026-11-13).** 26a–26c sind gebaut (siehe
  FEATURES.md). Offen: Ansehen auf dem Telefon (Flüssigkeit bei 30/60 Bilder/s, Akku, Lesbarkeit,
  Holo-Stil mit Bildtasten); die Leistenknöpfe im flachen Look leuchten nur als Symbol, nicht
  ihr Kreis; der Tastaturhintergrund leuchtet nur hinter den Tasten, nicht hinter der Leiste. (S)
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
- **U6** `@generate` (Frage des Owners vom 2026-11-13, „vielleicht später“): Bild aus Text über
  Cloudflare Workers AI (FLUX.1 schnell, 10.000 Neurons pro Tag gratis, etwa 170 Bilder in
  1024×1024), mit eigenem Konto und Token (BYOK), Ablauf wie `@ki` (Freigabe, Punkte, Bild-Chip,
  Einfügen wie `@qr`). Gemini-Bildmodelle haben laut Stand 2026-11 kein Gratis-Kontingent;
  Pollinations ohne Konto hat Wasserzeichen und ungeklärte Bedingungen. (M)
- **U7** Animierte Emojis (25b) und Sticker-Ersteller (25c) (Wunsch des Owners vom 2026-11-13).
  Emoji-Mix (25a) ist gebaut (siehe FEATURES.md); Bilder von Google laden ist erlaubt (Owner).
  Offen aus 25a: Lizenzdatei im Repository des Autors der Paarliste nachprüfen (Stand und Quelle:
  `docs/BUILD.md` → Emoji-Mix-Tabelle); eine neue Paarliste von Google braucht einen neuen Lauf
  des Generators. (L)
- **U5** „Embeds“ (Frage des Owners vom 2026-11-12, noch nicht geklärt, was gemeint ist). Möglich
  sind nur Text und Inhalte über `commitContent`. Link-Vorschauen baut die Ziel-App selbst aus
  einem Link, Discord-Embeds dürfen nur Bots senden. Machbar wäre eine lokal gezeichnete
  Info-Karte als Bild (z. B. Termin oder Ort), nur in Feldern, die Bilder annehmen.

## Unsortiert

- (leer)

## Bewusst nicht im Umfang

Vereinbart für dieses private Projekt: keine öffentliche Community, keine Konten, kein Backend,
keine Synchronisierung. Austausch läuft über Dateien. Keine Behauptung von hunderten
Integrationen.
- Lerndaten auf einem Server, auch verschlüsselt (2026-11-12 entschieden): Lernen geschieht
  ohnehin auf dem Gerät; ein Server brächte nur Risiko. Sicherung über verschlüsselte
  Exportdatei.
- Bildschirm anderer Apps ständig mitlesen oder Benachrichtigungen mitlesen: zu weitreichender
  Zugriff. Ausnahme (Owner, 2026-11-12): der Bedienungshilfe-Dienst aus 24e, der nur auf einen
  ausdrücklichen Befehl einmal liest. Für Absichten (21) gilt weiter: Nachricht kopieren.
- Aktionen ganz ohne Bestätigung ausführen: eine Fehlerkennung würde still falsche Termine
  anlegen oder Orte verschicken.
