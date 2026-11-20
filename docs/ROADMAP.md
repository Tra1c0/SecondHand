# SecondHand – Roadmap (noch nicht gebaut)

Alles, was es noch nicht gibt. Der Owner sortiert. Sitzungen bauen nur, was unter „Beschlossen“
steht oder was der Owner direkt beauftragt, und nur, wenn die Entwicklung nicht pausiert ist
(`docs/HANDOFF.md` → Aktueller Stand). Ist ein Punkt gebaut, wird er hier gestrichen und steht
dann in [FEATURES.md](FEATURES.md). Aufwand: S klein, M mittel, L groß. Die Nummern sind die
Etappen aus dem ursprünglichen Auftrag; Teilpakete bekommen Buchstaben (15a, 15b …).

## Beschlossen, noch nicht gebaut

- **Runde 3 (Auftrag des Owners, 2026-11-20, Vergleich mit Gboard auf dem Xiaomi 15).** Bestandsaufnahme mit
  Codestellen: `docs/RUNDE3-BESTANDSAUFNAHME.md`. Etappen, jede einzeln geprüft:
  - **81 Symbolseiten wie Gboard, unabhängig vom Stil:** „?123“ und „=\<“ Taste für Taste wie Gboard (keine
    Zusatzreihe, § statt ¶, ✓, `\` und `%` an Gboards Stelle, keine Zahlenreihe auf Seite 2, keine
    Vierpfeil-Taste). Die Vorlage „Flacher Look“ hat diese Seiten schon; sie werden Standard.
  - **82 „Alles markieren“ zeigt Aktionen:** gebaut (Leiste mit Kopieren, Ausschneiden, Einfügen, Löschen bei
    jeder Markierung; `docs/VALIDATION.md` → Etappe 82). Ungeprüft: Gerät, andere Apps als Robolectric, ob
    `startSelectingText` das Menü der App öffnen würde (nicht eingebaut).
  - **83 Gezeichnete Symbole logisch:** gebaut („/“ und „\“ als Symbole; ein Symbol mit mehr Strichen als gemalt ist nie
    das beste Ergebnis; `docs/VALIDATION.md` → Etappe 83). Ungeprüft: Gerät und Finger des Owners. Offen: Es gibt
    weiter kein „-“, „_“, „:“, „;“, „*“, „<“, „>“ unter den Symbolen.
  - **84 Symbole ohne Verzögerung:** erst messen (Buchstabe gegen Symbol), dann die Ursache beheben.
  - **85 Leiste wie Gboard:** feste, gleichmäßig verteilte Plätze (1 bis 5 oder 8), Raster-Knopf links,
    Mikrofon rechts, kein Pfeil, kein Scrollen; Raster aller Werkzeuge. **86** Anpassen per Halten und Ziehen im
    Tastaturfenster.
  - **87 Cursor als Trackpad:** Leertaste in vier Richtungen, weiche Beschleunigung, leichter Widerstand an
    Wortgrenzen („Magnetismus“, Richtwert des Owners 5 %), ebenso das Wischen auf der Löschtaste. Grenze von
    Android: Die Tastatur sieht das Layout des Textfelds nicht; der Cursor bewegt sich zeichen- und zeilenweise,
    Animation und Scrollen macht die App.

Reihenfolge von oben nach unten.

- **72 Satzbezogene Autokorrektur**: Für regelmäßige deutsche Verbformen gebaut und abschaltbar.
  Offen sind der Gerätetest mit nativen Wörterbüchern, unregelmäßige Verben und die Auswertung
  weiterer Satzmuster, Sprachen und gemischtsprachiger Texte.
- **73 Emulator-Testlabor auf GitHub** (Auftrag des Owners vom 2026-11-18, „Ja, bau das“, dort als
  „Etappe 72“; die Nummer war vergeben): **73a Gerüst gebaut** (Workflow `device-tests.yml`, Test-App,
  sechs Tipp-Szenarien, Aufnahme; Nachweis in `docs/VALIDATION.md` → Etappe 73). Offen: **73b** Szenarien
  (Großschreibung, Autokorrektur mit den Slang-Fällen aus 71, Sprachwechsel, Emoji, Passwortfeld ohne Lernen
  und ohne Zwischenablage-Pille, Zwischenablage-Pille), **73c** Autofill Ende-zu-Ende mit einem
  Test-Autofill-Dienst in der Test-App. Nicht im Umfang: echte Passwortmanager, Instagram/WhatsApp,
  Google-Diktat, Firebase Test Lab. Vorgemerkt, nicht beauftragt: die Tastatur meldet Bedienungshilfen ihre
  Tasten im Emulator teils 128 Pixel zu hoch (P294); Ursache im Code gefunden und behoben (die Position
  der Ansicht wurde nur beim Wurzelknoten gelesen, jetzt bei jeder Taste; Einheitentest
  `KeyNodePositionTest`; Emulator-Bestätigung durch das Szenario `key_positions_reported` ausstehend).
  **Befund aus 73b:** „idr“ wurde mit den echten Wörterbüchern nicht zu „dir“ korrigiert; behoben durch
  Etappe 75 (Emulator-Bestätigung ausstehend, `docs/VALIDATION.md` → Etappe 73 und 75). Die Szenarien
  laufen wieder (21 im Stand dieses Branches, `KNOWN_FINDINGS` ist leer), Ergebnisse der Läufe nach
  Lauf 23 stehen aus; 73c nicht begonnen.
- **74 Ablage** (bis Build 36 „Prompt-Ablage“) (Auftrag des Owners vom 2026-11-19: „eine extra Zwischenablage für Prompts, die
  permanent gespeichert werden und die ich sortieren kann“, Zugang: „Eigenes Symbol beim , gedrückt
  halten“) ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 74): 74a Speicher und
  Einstellungsseite, 74b Symbol im Komma-Popup und Fenster in der Tastatur. Annahmen des Leads, die der
  Owner ändern kann: (1) Sortiert wird auf der Einstellungsseite (Ziehen im Dialog
  „Sortieren“), nicht im Fenster der Tastatur. (2) Das Symbol fehlt auch in Feldern, die Lernen verbieten
  (private Chats), nicht nur in Passwortfeldern. (3) Die Einträge liegen im geschützten Speicher ohne
  Android-Backup; in „Sichern“ sind sie seit Etappe 76. „Kopiertes in der Ablage speichern“ folgt der Regel des Zwischenablage-Verlaufs (ein Satz mit codeähnlicher Zahl wird gespeichert); bekannte Grenze: nach einem Neustart der Tastatur kennt der Tresor das Herkunftsfeld nicht mehr. Die zweite Prüfung der Korrekturen e5689c9 und bad8d4f ist erledigt; die spätere Regeländerung 5faced3 hat kein Prüfer gelesen.
  Offen: Gerätetest (Langdruck auf Komma, Symbol und Popup, Fenster im Querformat, auf Tablets und im
  flachen Stil, Tempo mit 200 langen Einträge); der GitHub-Lauf „SecondHand Tests“ (volle Agent-Suite,
  Upstream-Suite, Lint, Build) lief für diesen Stand noch nicht; ein signierter Build fehlt.
  Ideen danach, nicht gebaut: Ordner oder Kategorien; Sortieren im Fenster der Tastatur; Suche in der
  Ablage.
- **75 Buchstabendreher** (Reihenfolge des Owners vom 2026-11-19, „Behebe alles“; Anlass: der Befund „idr“
  aus 73b) ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 75): Ein sehr häufiges
  kurzes Wort mit zwei vertauschten Nachbarbuchstaben wird korrigiert, abschaltbar. Annahmen des Leads, die
  der Owner ändern kann: die Regel gilt bei jeder Korrekturstärke; die Häufigkeitsschwellen 105 (Deutsch),
  150 (Englisch), 130 (andere); Kandidaten zählen nur im Wörterbuch der Tastatursprache. Offen: Emulator mit
  den echten Wörterbüchern („Und idr “ → „Und dir “; „ort“, „rest“, „titel“, „idR“ bleiben richtig), ein
  zweites Review der Fassung 899c017, Gerätetest, Schwellen für andere Sprachen als Deutsch und Englisch.
- **76 Ablage in Sichern** (Reihenfolge des Owners vom 2026-11-19, „Behebe alles“; die Idee aus 74)
  ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 76): „Sichern“ nimmt die Einträge
  mit, „Wiederherstellen“ übernimmt sie zuletzt und nur aus einem gültigen, nicht leeren Eintrag. Annahme des
  Leads, die der Owner ändern kann: Ein leerer Eintrag oder eine Datei ohne Eintrag lässt die vorhandenen
  Einträge unverändert. Offen: die echte Wiederherstellung im Dialog (braucht eine Activity), Gerätetest;
  bekannte Grenze: über eine unlesbare Ablage-Datei auf dem Telefon wird kein Backup gespielt (erst
  zurücksetzen).
- **77 Ordner in der Ablage** (Auftrag des Owners vom 2026-11-19: „Ordner im prompt space möglich machen … dann wird er
  von prompt space zur dauerhaften, universellen Ablage“) ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 77).
  Entscheidungen des Leads, die der Owner ändern kann: eine Ebene, höchstens 50 Ordner; Einträge bleiben in einer globalen Reihenfolge;
  ein gelöschter Ordner löscht keine Einträge („Ohne Ordner“); der gewählte Chip im Fenster wird nicht gespeichert; neue Ordner stehen
  hinten. Der Name „Ablage“ gilt seit Etappe 77b (Entscheidung des Owners). Offen: Gerätetest, Einträge per Fenster in Ordner verschieben (heute nur auf der Seite),
  Ordner schachteln (nicht beauftragt).
- **27 Symbole malen** (Auftrag des Owners vom 2026-11-13) ist gebaut (siehe FEATURES.md), mit
  Engine-Review (`docs/VALIDATION.md` → Build 12: Prüfer zu Etappe 27), die niedrigen Befunde in
  31. Offen: Gerätetest (Erkennung mit echten Fingern, Schwellen, Spur, Tempo, Tipp-Regel,
  Malfeld der eigenen Symbole).
- **28 Haptik und Rastpunkte für alle Schieberegler** ist gebaut (siehe FEATURES.md). Annahmen des
  Leads, die der Owner ändern kann: Die Ticks folgen „Vibration bei Tastendruck“ (nicht der
  Android-Einstellung für Berührungs-Haptik); Rastpunkte je Regler sind Standardwert, beide Enden
  und runde Werte (Regel in `docs/ARCHITECTURE.md` → Einstellungen und Profile). Offen: Gerätetest
  (Stärke der Impulse, Breite der Fangzone, Antippen des Reglers) und die RGB-Seite, die den
  gemeinsamen Regler `AgentSlider` erst nach ihrer Überarbeitung (29) übernimmt.
- **29 RGB-Beleuchtung überarbeiten** (Wunsch des Owners, 2026-11-13) ist gebaut (siehe
  FEATURES.md): 29a neue Seite, 29b weiche Übergänge, 29c sechs bewegte Texturmuster, je Element
  wählbar, 29d eigene Kurve. Offen: Gerätetest (Aussehen, Bildkosten, Akku) und die Bestätigung
  der Annahme zur Kurve (x = Stelle im Durchlauf, y = Farbe bzw. Helligkeit bei „Atmen“).
- **30 Diagnose-Seite** und **31 Feinschliff Symbole malen** sind gebaut (siehe FEATURES.md,
  `docs/VALIDATION.md` → Etappen 30 und 31). Offen: Engine-Review der Hooks in `LatinIME` und
  `PointerTracker`, Gerätetest.
- **36 Symbole malen: Erkennung überarbeiten** (Rückmeldung des Owners zu Build 14) ist gebaut
  (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 36 (Erkennung Symbole malen)):
  Tipp-Grenze 8 dp, Folgestriche nur nahe der Zeichnung (3 Tasten; auch auf Satzzeichen- und
  Zifferntasten), Pause 0,9 s nach Tempo (0,6–1,5 s), Vorschau-Chip (nicht im Hauptthread),
  Erkennung mit Strichrichtung und Strichzahl, mehr Vorlagen, persönliche Vorlagen (Lernen aus
  Korrekturen, anfangs aus, und Kalibrieren). Prüfer-Befunde H1, M1–M4, N1, N3, N4 korrigiert
  (`docs/VALIDATION.md`), ebenso die Nachprüfer-Befunde F1–F5, F7. Offen: erneutes Prüfer-Review der `PointerTracker`-Änderung (Pflicht),
  Gerätetest am Xiaomi (Erkennung mit echten Fingern, Tipp-Grenze, Bereich um die Zeichnung, Tempo
  der Pause, ob der Vorschau-Chip beim Malen stört, Zeit der Erkennung), ob ⇒ → ≠ am Telefon weg
  ist (im Test nie nachgestellt); Annahme des Leads:
  „Aus meinen Korrekturen lernen“ ist opt-in (Regel in `CLAUDE.md`), der Owner schaltet es ein.
- **43 Symbole malen: Punkte und Start nach dem Tippen** (Rückmeldung des Owners zu Build 19: bei
  ÷, % und allen Symbolen mit einem getippten Punkt ging das Symbol verloren oder kam mit
  Buchstabe; direkt nach dem Tippen begann oft keine Zeichnung) ist gebaut (siehe FEATURES.md,
  Nachweis in `docs/VALIDATION.md` → Etappe 43): mögliche Punkte mit Entscheidung beim Schreiben,
  sonst Symbol und danach die Buchstaben; Punkt-Vorlagen für %, neu ‰ ! ¡ ? ¿; HeliBoards
  Schnelltipp-Sperre beim Malen ersetzt durch 0,75 Tastenbreiten direkt nach dem Tippen. Offen:
  Prüfer-Review der `PointerTracker`-Änderung (Pflicht), Gerätetest am Xiaomi (Punkte mit echten
  Fingern, ob die verzögerten Buchstaben stören, Malen direkt nach dem Tippen, rutschende Tipps).
- **33 Leertasten-Geste mit Rastpunkten** ist gebaut (siehe FEATURES.md, Nachweis in
  `docs/VALIDATION.md` → Etappe 33 (Leertasten-Rastpunkte)). Offen: Prüfer-Review der `PointerTracker`-Änderung
  (Pflicht vor dem Merge), Gerätetest (Gefühl und Unterscheidbarkeit der Ticks, Abstände am
  Telefon, Stufe der Haptik am Xiaomi) und die Annahmen des Leads, vom Owner zu bestätigen:
  Rastpunkte bei 0,8 / 1,6 / 2,65 Tastenbreiten (Startpunkt plus drei Punkte), „den ganzen Text
  rückgängig“ = ein normaler Rückgängig-Schritt, Zurückziehen tickt erneut statt abzubrechen,
  gilt für alle Einmal-Aktionen links/rechts.
- **37 Bildwiederholrate als Karten** (Wunsch des Owners vom 2026-11-13, Skizze
  [`docs/referenzen/2026-11-13-rgb-bildwiederholrate.jpg`](referenzen/2026-11-13-rgb-bildwiederholrate.jpg))
  ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 37 (Bildwiederholrate
  als Karten)): Karte „Bildwiederholrate“ mit zwei Farbflächen untereinander (nebeneinander wären
  sie bei 390 dp zu schmal für die gesperrte Schrift), wandernder Lichttropfen; still ohne
  Android-Animationen (beim Öffnen gelesen), im Energiesparmodus (live) und außer Sicht.
  Entscheidung des Owners vom 2026-11-14 (ersetzt den Vorschlag „60 Hz doppelt so schnell“):
  30 HERTZ gelb-orange, 60 HERTZ türkis-grün (Farben getauscht); beide Tropfen gleich schnell auf
  derselben Bahn und aus derselben Uhr genau übereinander, der 30-Hz-Tropfen springt sichtbar in
  Schritten von 33 ms (30 Bilder/s), der 60-Hz-Tropfen in 17 ms (60 Bilder/s), unabhängig von der
  Bildrate des Displays (60/90/120 Hz). Nachprüfung (Prüfer-Befunde): Schrift der nicht gewählten
  Fläche bleibt voll deckend und lesbar, Überschrift eine Zeile auch bei 360 dp und Schrift 130 %.
  Annahmen des Leads, vom Owner zu bestätigen: Großbuchstaben „30 HERTZ“ wie die Auswahlkarten
  von 38 (die Skizze schreibt „30 Hertz“), Zusatzzeile „30 Bilder/s · spart Akku“ bzw. „60
  Bilder/s · flüssiger“, der Tropfen wandert hin und her mit kurzer Leuchtspur. Offen: Gerätetest
  (Aussehen, ob das Springen bei 30 Hz am Telefon deutlich genug ist, ob der Tropfen stört). Weitere Auswahlen der RGB-Seite, die in diesen Stil passen
  (nicht umgebaut, Vorschlag): „Regenbogen“/„Eigene Farben“ (zwei Karten mit Farbverlauf bzw. den
  eigenen Farben), „Beim Tippen“ (vier Karten, jede zeigt ihren Effekt: nichts, Blitz, Ring,
  schneller), „Jede Taste einfarbig“ als Wahl „weich“/„einfarbig“ mit kleinem Tastenbild.
- **38 Großer Umbau der Einstellungen — BESCHLOSSEN: 1:1 nach dem Mockup umsetzen (Owner, 2026-11-13).** Quelle der Wahrheit: `docs/referenzen/einstellungen-mockup/*.dc.html` (Kopie des Artifacts). Ursprünglicher Wunsch:
  Richtung wie bei 37: verspieltere, visuelle Auswahl statt Listen und Schalter, Karten mit
  Farbflächen und kleinen Animationen. Vor dem Bau klären: was stört heute (Struktur, Tiefe der
  Menüs, Optik), welche Seiten zuerst, Vorlagen/Skizzen des Owners. Großer Umbau und neue
  Funktionen als getrennte Etappen (CLAUDE.md). (L)
  Vorschlag des Leads vom 2026-11-13 als Mockup (Artifact „Einstellungen neu gedacht“,
  https://claude.ai/artifact/KnkKpiePakdG3UGsh3F9Nk, privat beim Owner): Stil der RGB-Seite
  (dunkel, große runde Karten, Verlaufsflächen je Bereich, Auswahl als große Farbkarten wie in 37);
  22 Einträge der Startseite → 7 Bereiche: 1 Aussehen (Designs, RGB-Licht, Größe, Schrift,
  Hintergrund), 2 Tippen & Korrektur (Autokorrektur an einem Ort, Vorschläge, Leerzeichen,
  Wörterbücher, Lernen), 3 Tasten & Sprachen, 4 Gesten & Gefühl (Leertaste, Symbole malen,
  Vibration/Ton, Trefferzonen), 5 Leiste & Befehle (Leiste, @-Befehle, Zwischenablage,
  Screenshots, Emoji-Mix), 6 KI & Automationen, 7 System & Daten (Profile, Sichern, Updates,
  Diagnose, Experimentell, Über). Startseite mit Look-Vorschau und Schnellschaltern.
  - 38a Grundlage und Startseite ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` →
    Etappe 38a): Baukasten, neue Startseite, sieben Routen `area_*` mit vorläufiger Karte „Seiten“
    zu den bisherigen Seiten. Annahme des Leads: Systemschrift statt Space Grotesk/Inter (keine
    Schriftdateien im APK). Offen: Gerätetest.
  - 38d Tasten & Sprachen ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` →
    Etappe 38d). Offen: Prüfer-Review, Gerätetest.
  - 38b Aussehen ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 38b).
    Offen: Gerätetest.
  - Je Bereichsseite eine Etappe, jeweils 1:1 nach ihrer `*.dc.html`, mit dem Baukasten aus 38a;
    die Karte „Seiten“ fällt dabei für diesen Bereich weg, alles bisher Erreichbare bleibt
    erreichbar: 38b Aussehen, 38c Tippen & Korrektur, 38d Tasten & Sprachen, 38e Gesten & Gefühl,
    38f Leiste & Befehle (dort auch „Symbolleiste“/„Werkzeugleiste“ → „Leiste“), 38g KI &
    Automationen, 38h System & Daten.
  - 38c Tippen & Korrektur ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` →
    Etappe 38c). Annahme des Leads: Zuordnung der Stufen AUS/BEHUTSAM/MUTIG auf vorhandene
    Einstellungen (ARCHITECTURE.md → Einstellungen und Profile). Offen: Gerätetest.
  - 38e Gesten & Gefühl ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` →
    Etappe 38e). Offen: Gerätetest.
  - 38f Leiste & Befehle ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` →
    Etappe 38f), „Symbolleiste“/„Werkzeugleiste“ heißen in den deutschen Texten jetzt „Leiste“.
    Annahme des Leads: Zuordnung der drei Karten auf `toolbar_mode` (ARCHITECTURE.md →
    Einstellungen und Profile). Offen: Gerätetest.
  - 38g KI & Automationen ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` →
    Etappe 38g). Offen: Gerätetest.
  - 38h System & Daten ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` →
    Etappe 38h). Annahme des Leads: Profilkarten sind Aktionen (Anwenden nach Rückfrage),
    hervorgehoben ist, was den aktuellen Einstellungen entspricht. Offen: Prüfer-Review, Gerätetest.
- **40 Smart Paste (Wunsch des Owners vom 2026-11-13)** ist gebaut (siehe FEATURES.md, Nachweis in
  `docs/VALIDATION.md` → Etappe 40). Chips neben der Zwischenablage-Pille und auf den Karten des
  Verlaufs: Adresse → Karte (`geo:`) / als `@ort`-Ort speichern; Telefon → Wählen (ohne
  Anruf-Berechtigung) / Kontakt anlegen; IBAN → in Blöcken einfügen nach Prüfziffer (falsche
  Prüfziffer: nur Hinweis); Sendungsnummer → feste Seite von DHL, Deutsche Post, Hermes, DPD, GLS,
  UPS; Datum/Uhrzeit → Kalenderentwurf wie `@termin`; E-Mail → Mail schreiben; Link → ohne
  Tracking einfügen; Code aus einer kopierten Nachricht → nur die Ziffern. Annahmen des Leads, die
  der Owner ändern kann: eigener Schalter „Smart Paste“ (anfangs an, Leiste & Befehle →
  Zwischenablage & Bilder); in der Leiste nur mit der Pille („Zwischenablagen-Inhalt vorschlagen“,
  anfangs aus), im Verlauf immer; Feld- und Kontext-Vorschläge (45) gehen vor, dann gibt es weder
  Pille noch Smart Paste; nur in Textfeldern; höchstens drei Chips in der Leiste, zwei je Karte;
  „Als Ort“ benennt den Ort nach Straße und Hausnummer; reine Ziffernfolgen (12 Ziffern DHL,
  14 DPD/Hermes, 11–12 GLS) nur, wenn der Paketdienst im Text steht; Deutsche Post nur mit „DE“
  am Ende; ein Datum ohne Jahr, das mehr als 300 Tage voraus läge, gilt als gerade vergangen;
  `si` wird nur bei YouTube und Spotify entfernt; ein Code allein bekommt keinen Chip (die Pille
  fügt ihn ein). Nach dem Prüfer-Review (Nachprüfung 40): Anruf-Chips nur mit „+“, „00“ oder „0“
  am Anfang; nur Code und IBAN gehen als privater Text ein. Offen: erneute Prüfung der
  Nachprüfung, Gerätetest (Tracking-Seiten der sechs Dienste am Gerät, Karten-, Telefon-,
  Kontakte-, Mail- und Kalender-App, TalkBack, Leiste mit langer Pille).
- **39 Text-Stile** — in 53 aufgegangen und mit 53 gebaut (siehe FEATURES.md → Zeichen-Kunst-Fenster):
  Unicode-Stile (Schreibschrift, fett, kursiv, Kapitälchen, Kreise, durchgestrichen, kopfüber …,
  kombinierbar) im Reiter Text, Hinweis auf Screenreader in der Vorschau. Noch offen aus dem
  ursprünglichen Wunsch (nicht Teil von 53, eigener Auftrag nötig): `@style` als Befehl mit Chips,
  Messenger-Formatierung (*fett*, _kursiv_, ~durch~, `Code`, Spoiler) für markierten Text,
  `@kaomoji` mit lokaler Bibliothek, Typografie aufräumen („deutsche Anführungszeichen“, –, …,
  doppelte Leerzeichen).
- **41 Formatwächter** ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe
  41). Telefonnummern über libphonenumber (Messung: etwa 0,35 MB, unter der Grenze von 1,5 MB).
  Annahmen des Leads: Hinweise nur, wenn das ganze Feld geprüft werden kann (Schreibmarke am Ende,
  höchstens 100 Zeichen); IBAN nur in normalen Textfeldern und nur in voller Länge des Landes;
  Telefon schlägt das internationale Format vor. Prüfer-Befunde behoben (Nachprüfung in
  `docs/VALIDATION.md`). Offen: Gerätetest (Leiste in Telefon- und Zahlenfeldern echter Apps,
  Ersetzen in Bank-Apps, Dauer des ersten Telefon-Hinweises, P140).
- **42 Textbausteine 2.0** ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` →
  Etappe 42). Annahmen des Leads, die der Owner ändern kann: Platzhalter nur mit deutschen Namen;
  Tastatursprachen außer Englisch und Spanisch formatieren deutsch; alte Bausteine bleiben
  wörtlich, bis sie neu gespeichert werden. Prüfer-Befunde behoben (Nachprüfung in
  `docs/VALIDATION.md`), die Änderungen an `LatinIME`, `InputLogic` (Getter) und
  `KeyboardActionListenerImpl` brauchen noch einen Prüfer. Offen: Gerätetest (Feldabfrage in
  Messengern, Schreibmarke nach `{cursor}` in einem Editor, der zwei Auswahl-Meldungen anders
  verarbeitet (P147), App-Auswahl, Enter einer echten Hardware-Tastatur).
- **44 Zwischenablage-Wächter und Tresor** ist gebaut (siehe FEATURES.md, Nachweis in
  `docs/VALIDATION.md` → Etappe 44). Entscheidet die offene Frage „vertrauliche Clips im normalen
  Verlauf“: nein. Annahmen des Leads, die der Owner ändern kann: Geheimnis weg nach dem ersten
  Einfügen, spätestens nach 2 Minuten; Einmalcode 10 Minuten, bleibt nach dem Einfügen; im Zweifel
  bleibt ein Clip normal (nur starke Signale machen ein Geheimnis); kein Tresor in Passwortfeldern
  (Auftrag), dort Kopiertes wird nirgends behalten. System-Zwischenablage: Laut Android-Quelltext
  darf die Tastatur sie ohne Fokus leeren; sie tut es nur nach dem Einfügen aus dem Tresor und nur,
  wenn noch genau dieser Clip darin liegt. Offen: Gerätetest (Leeren auf Android 16, TalkBack,
  echte SMS-Codes, Passwortmanager), Prüfer-Review. (M)
- **45 Feld- und Kontext-Vorschläge** ist gebaut (siehe FEATURES.md, Nachweis in
  `docs/VALIDATION.md` → Etappe 45). Annahmen des Leads, die der Owner ändern kann: Kontext-Chips
  erst nach dem Leerzeichen oder Doppelpunkt, nicht mitten im Wort (sonst verdecken sie die
  Wortvorschläge); ein einzelnes Datenwort („Mail“, „Code“, „IBAN“) zählt nur mit Doppelpunkt;
  höchstens zwei Kontext- und drei Feld-Chips; ein reines Zahlenfeld ohne Code-Hinweis bekommt nur
  einen Code bis 3 Minuten; „Meine Daten“ mit je zwei Einträgen, erreichbar über Leiste & Befehle
  und den Tresor; das Kontext-Lernen hängt am Schalter „Personalisierte Vorschläge“ (anfangs aus)
  und an der Lernpause. Android gibt Tastaturen keine Autofill-Kennungen; genutzt werden Typ,
  Hinweistext, Beschriftung, Feldname und App. Einfügen in ein leeres Feld geht wie das Einfügen
  aus der Zwischenablage-Ansicht. Offen: Gerätetest (Code-Felder echter Apps, Messenger, Browser,
  TalkBack), Prüfer-Review (Änderungen an `KeyboardCommands`, `ClipVaultSection`). (M–L)
- **46 Gedächtnis (Wunsch des Owners vom 2026-11-14)** ist gebaut (siehe FEATURES.md, Nachweis in
  `docs/VALIDATION.md` → Etappe 46). Grenze des Owners eingehalten: Das Gedächtnis hängt nur auf
  Tipp hinter der Schreibmarke an und ersetzt nichts, die Autokorrektur bleibt bei einem Wort;
  einzige Ersetzung: das gerade getippte Kürzelwort, nur auf Tipp (Chip einer gemerkten Abkürzung).
  Annahmen des Leads, die der Owner ändern kann: diese eine Ersetzung; eigener Schalter „Merken, wie
  ich schreibe“ (anfangs aus), gelernt nur mit „Personalisierte Vorschläge“ an (wie Kontext- und
  Symbol-Lernen, dessen Ausschalten das Gedächtnis löscht); Chips wie in 45 nur nach dem Leerzeichen; der Rest einer Folge als Chip erst ab
  dreimal und 70 % (gemessen), dadurch braucht eine Folge etwa zehn Wiederholungen; eigene Wörter
  ab drei Nutzungen ohne Korrektur; Abkürzungs-Angebot ab zwei eigenen Ersetzungen, nur einmal in
  der Leiste; „Passende Textbausteine“ anfangs an (lernt nichts). Abschnitte (`MemorySections`)
  auf der Seite „Was ich mir gemerkt habe“ aus 49 (Tippen & Korrektur → Wörter & Lernen). Prüfer-Befunde
  behoben (`docs/VALIDATION.md` → Nachprüfung 46): gemerkte Abkürzungen nur im Gedächtnis und nur auf
  Tipp, nie im persönlichen Wörterbuch. Zweite Nachprüfung behoben (`docs/VALIDATION.md` → Zweite
  Nachprüfung 46): Schreibung der Erweiterung, „Mo“ → „Montag“ lernbar, Rücknahme nur an ihrer Stelle,
  Schreiben beim Verlassen des Felds sofort. Offen: keine Chips direkt hinter privatem Text (wartet auf
  eine öffentliche Abfrage in `InputLogic`, parallele Arbeit), erneute Prüfung (Engine: `InputLogic`, `Suggest`),
  Gerätetest (Chips beim echten Tippen, Abkürzungen mit echter Löschtaste und Erweiterungs-Chip,
  Speicherzeit bei 2000 Folgen, Bausteine mit `{cursor}`).
- **49 Korrekturen merken über das ganze Feld (Regeln mit dem Owner festgelegt, 2026-11-14)** ist
  gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 49), nach den Regeln des
  Owners. Annahmen des Leads, die der Owner ändern kann: hängt am Schalter „Meine Tippfehler
  lernen“ (die Rücknahme-Regel an „Rückgängig gemachte Korrekturen merken“), kein eigener Schalter;
  „echtes Wort“ heißt jedes aktive Wörterbuch einschließlich persönlichem Wörterbuch, Kontakten und
  Lernverlauf (mit Deutsch und Englisch gilt „fur“ als echt); ein zusätzlicher Buchstabe zählt nur
  als Vertipper, wenn er einen Nachbarbuchstaben verdoppelt („Haaus“, nicht „Hasus“); „andere
  Stelle“ = anderes Feld oder andere Position (ein vom Messenger geleertes Feld beginnt neu);
  der Vergleich reicht 1024 Zeichen vor und 300 nach der Schreibmarke; es zählt nur ein Wort, das
  eigene Eingabe (Tasten, Löschtaste, Vorschlag) berührt hat, keine Vervollständigung; in langsamen
  Apps nichts (Nachprüfung 49). Seite „Was ich mir gemerkt habe“ ist für 46 vorbereitet (Abschnitte). Offen:
  Prüfer-Review (Engine-Änderung, Pflicht) und Gerätetest. (M–L)
- **48 Autokorrektur: getipptes Wort vorne, keine ungewollte Großschreibung (Rückmeldung des Owners
  vom 2026-11-14)** ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 48).
  Offen: Prüfer-Review (Engine-Änderung, Pflicht) und Gerätetest. Zu entscheiden (Owner): Felder,
  die selbst „jedes Wort groß“ verlangen (Namen, manche Betreff- und Suchfelder), bekommen weiter
  an jedem Wortanfang einen Großbuchstaben; der Schalter „Großschreibung am Satzanfang“ schaltet
  auch das ab. Soll die Tastatur dort trotzdem nur Satzanfänge groß schreiben? (M)
- **50 Dynamische Trefferzonen mit Szeneprofilen** (Wunsch des Owners vom 2026-11-14) ist gebaut
  (siehe FEATURES.md, `docs/VALIDATION.md` → Etappe 50). Annahme des Leads, die der Owner ändern
  kann: eigener Schalter „Dynamisch anpassen (Szenen)“, anfangs aus, nur zusammen mit
  „Trefferzonen an mich anpassen“. Offen: Engine-Review (`KeyboardActionListenerImpl.onCodeInput`,
  `LatinIME.onStartInputViewInternal`), Gerätetest (Schwellen mit echten Fingern, Tageszeiten,
  Beförderung über mehrere Tage).
- **51 Autokorrektur folgt der Sprache, andere Sprachen im Kontext (Rückmeldung des Owners zu
  Build 21, 2026-11-14)** ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 51).
  Ursachen: zwei Upstream-Fehler bei Sprachen mit zweiter Sprache (die Hauptsprache stand nicht
  vorne; ein Wechsel zwischen denselben zwei Sprachen baute nichts neu auf) und sprachblinde
  Lerndaten aus 20b/49. Annahmen des Leads, die der Owner ändern kann: Wörter aus anderen Sprachen
  hängen an „Personalisierte Vorschläge“ (nicht am Gedächtnis-Schalter, weil sie wie HeliBoards
  Lernen nur getippte Wörter merken); gemerkt sofort nach einer rückgängig gemachten Korrektur,
  sonst erst beim zweiten Mal an anderer Stelle (ein einzelnes unkorrigiertes Wort kann ein
  Vertipper sein, der zufällig in einer anderen Sprache existiert, z. B. „fur“, „ser“ statt „der“);
  „andere Sprache“ = jede aktive Sprache und ihre zweiten Sprachen, außer den zweiten Sprachen der
  gerade benutzten (die deckt HeliBoards mehrsprachiges Tippen ab, Nachprüfung 51); die Regel „fur“
  (49) gilt, bis du „fur“ gegen die Korrektur behältst (einmal rückgängig gemacht) oder es zweimal an
  verschiedenen Stellen unkorrigiert stehen bleibt; ersetzt du ein gemerktes Wort durch ein anderes,
  ist es für diese Sprache wieder vergessen; Rückgängig gemachte Korrekturen, eigene Wörter,
  Wortfolgen und Kürzel bleiben sprachübergreifend. Prüfer-Review: Befunde behoben (Nachprüfung 51 in
  `docs/VALIDATION.md`), Restpunkte der zweiten Prüfung behoben (Zweite Nachprüfung 51), erneute
  Prüfung offen (Engine: `DictionaryFacilitatorImpl`, `Suggest`, `LatinIME.onTrimMemory`,
  `LatinIME.resetSuggestMainDict`, Pflicht). Offen: Gerätetest (echte Wörterbücher, Speicher der zusätzlich
  geöffneten Wörterbücher, Sprachtaste und Wischen auf der Leertaste).
- **60 Satzsprache (Rückmeldung des Owners, 2026-11-16)**: „Englisch wird ins Deutsche übersetzt,
  auch wenn der Satz komplett in Englisch ist.“ ist gebaut (siehe FEATURES.md, Nachweis in
  `docs/VALIDATION.md` → Etappe 60). Ursache: die Regel aus 48/49/51 zählt ein getipptes Wort nur
  als bekannt, wenn ein Hauptwörterbuch der Sprache der Korrektur es kennt; ein Wort, das nur die
  zweite Sprache kennt („name“, „bank“, „job“, „hand“), bekam so sein deutsches Gegenstück. Neu: die
  Regel schaut auf die Sprache des Satzes bisher. Annahmen des Leads, die der Owner ändern kann:
  Beweis sind die bis zu drei vorigen Wörter desselben Satzes (die, die die Engine für Vorschläge
  schon hat, bis zum Satzanfang); ein Wort zählt für eine Sprache, wenn ihr Hauptwörterbuch es kennt
  (wie getippt oder klein); Wörter, die beide oder keine Sprache kennen (Namen, Zahlen, „in“,
  „Team“), zählen nicht. Die Korrektur zur Sprache der Hauptsprache wird blockiert, wenn das getippte
  Wort in einem Hauptwörterbuch einer anderen Sprache der Wörterbuchgruppen steht und nicht in dem der Korrektur, mindestens
  ein voriges Wort nur in solchen Sprachen bekannt ist, die auch das getippte Wort kennen (bei drei Sprachen zählt ein
  spanisches Wort nicht für ein englisches getipptes), und keines nur in der Sprache der Korrektur; das getippte Wort bleibt, die
  Korrektur steht weiter als Vorschlag. Schwelle „mindestens ein Wort“ (nicht zwei), weil nur dann
  der Satz „what is your name“ am dritten Wort schon trägt und „in team“ (neutral) nichts blockiert.
  Es zählen nur die Sprachen der Wörterbuchgruppen (Hauptsprache und zweite Sprachen des Subtyps);
  die anderen aktivierten Sprachen aus 51 (`ForeignWords`) zählen hier nicht, weil sie nicht im
  Tippen mitlaufen und ihre Wörterbücher nur im Hintergrund geöffnet werden. Immer an, ohne Schalter.
  Bleibt: das erste Wort eines Satzes hat keinen Beweis („hand“ am Satzanfang wird „Hand“); nach einem Komma
  (und nach einem Wort mit angehängten Anführungszeichen, Klammern oder Emoji) zählen die Wörter davor nicht; ein einziges
  nur deutsches Wort unter den letzten drei beendet den Schutz; es zählen nur die zweiten Sprachen der
  Tastatursprache, nicht andere aktivierte Tastatursprachen; ein gelernter Tippfehler (20b) geht an der Regel vorbei. Die
  Regel gilt in beide Richtungen (englische Tastatur mit Deutsch als zweiter Sprache). Nachprüfung 60: Das Tor der Regel
  fragt die Hauptwörterbücher nach dem getippten Wort, nicht den ersten Treffer der Ergebnisse (der kann ein Verlaufseintrag sein).
  (S)
- **52 Einheitliches Befehlssystem (mit dem Owner festgelegt, 2026-11-14)** ist gebaut (siehe
  FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 52), nach den Festlegungen des Owners
  (Aufbau `@bereich [aktion] [angaben …]` ohne `&`, Plätze mit Toleranz in beliebiger Reihenfolge,
  zwei Deutungen als zwei Chips, `app:`, Englisch zuerst und Deutsch, Gruppen, Ketten mit `;`,
  `then`/`dann`, `→`, Vorschau in Klartext, Tipp führt aus, Enter nur bei genau einer harmlosen
  Deutung, Löschen und `all` nur per Tipp, „Rückgängig“, `@?` und `@lang ?`, Vorschläge je Platz,
  App-Sprache auch mit „Sprache pro App“ aus, Farben in der Vorschau). Annahmen des Leads, die der
  Owner ändern kann: `all` setzt für jede App denselben Wert und löscht dabei die eigenen Einträge
  (Sprache: Eintrag für alle Apps; Formatwächter und Gedächtnis: der Schalter in den
  Einstellungen; `@mem all off` behält die ausgeschlossenen Apps); ohne App gilt die App des
  Felds (`@guard off`), bei `@lang` mit Sprache wie bisher nur das Feld (`@lang spanisch`);
  `@lang whatsapp german` in WhatsApp selbst stellt sofort um; das Ziel einer App gilt vor dem
  Schalter (`@guard banking on` zeigt dort Hinweise, auch wenn der Formatwächter sonst aus ist);
  mit eingeschalteter „Sprache pro App“ gilt die letzte Wahl (Befehl oder Sprachtaste in der App);
  Hint-Locales eines Felds gehen wie bei HeliBoards eigener App-Sprache vor; nicht harmlos (nur per
  Tipp) sind außer Löschen und `all` auch Gedächtnis an, der Zwischenablage-Schalter (räumt wie
  der Einstellungsschalter ungepinnte Einträge, die Rücknahme stellt nur den Schalter zurück) und
  das Überschreiben eines Bausteins; Enter bei einer anderen Deutung im neuen Aufbau sendet nichts,
  sondern zeigt „Tippe auf die Vorschau“; ein Sprachwort mit genau einem Chip (`@deutsch`) schaltet
  jetzt auch mit Enter um; „Rückgängig“ 8 s; in `@snip add` gehören `;` und `dann` zum Text, die
  Kette geht mit dem nächsten `@` weiter; Gruppen bis 20 mit je 30 Apps, Gruppenname a–z; eine
  Sprache mit mehreren Layouts nimmt für eine App das gerade benutzte, sonst das erste.
  Nicht gebaut: Farben im Textfeld selbst. Das bräuchte den ganzen Befehl als Kompositionstext über
  Leerzeichen hinweg; `InputLogic` schließt das Wort an jedem Leerzeichen ab (Autokorrektur,
  Autoleerzeichen) und `RichInputConnection` führt Komposition und Schreibmarke selbst, das wäre
  kein enger Eingriff in die Engine (P202). Mögliche Etappe, wenn der Owner sie will: ein eigener
  Kompositionsmodus nur für Befehle, mit Prüfer. Prüfer-Befunde behoben (Nachprüfung 52 in
  `docs/VALIDATION.md`); Annahmen des Leads dabei: Text hinter einem Bereichswort ist nur ein
  Befehl, wenn ein Wort eine bekannte Rolle hat und das erste Wort passt oder höchstens eines
  unbekannt ist; geschützt vor der Autokorrektur ist dann auch das erste Wort allein (es kann eine
  App sein, die noch getippt wird); Ketten ohne Trenner nur hinter einem vollständigen Befehl;
  Enter auf einem Sprachwort nur beim genauen Namen; eine fehlgeschlagene Wirkung nimmt alles
  zurück und lässt den Text stehen (statt ihn neu einzufügen); ein Sprachwechsel im Feld einer App
  mit Befehlssprache bleibt im Feld (Schalter aus). Offen: erneuter Prüfer-Lauf
  (`LatinIME.onStartInputInternal`, `onCurrentInputMethodSubtypeChanged`, Pflicht), Gerätetest (App-Namen echter Apps, Sprache beim Feldstart in WhatsApp/Duolingo,
  Farben auf hellem und dunklem Design, Enter in Messengern, Hardware-Enter).
- **53 Zeichen-Kunst-Fenster** ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` →
  Etappe 53). Annahmen des Leads, die der Owner ändern kann: Standardstil Braille; Profile WhatsApp,
  Telegram, Discord, Signal, Andere (Breiten 26/28/32/26/26, Codeblock bei WhatsApp und Discord an,
  bei Telegram wählbar, nur um Kunst, Rahmen oder Nebeneinander; Apps ohne Profil bekommen
  „Andere“); Text im Fenster über eigene Fenster-Tasten statt der Tastatur-Tasten (die
  liegen unter dem Fenster; umleiten hätte die Engine berührt); Fotovorlage aus den neuesten 12
  Bildern im Fenster statt System-Picker (der würde die Tastatur schließen); nach dem Einfügen
  bleibt das Fenster offen und „Einfügen“ ist 8 s „Rückgängig“; „Ersetzen“ nur, wenn hinter der
  Schreibmarke nichts steht, mit Markierung „Markierung ersetzen“; das „✎“ als Hinweis auf jeder
  Emoji-Taste bleibt. Prüfer-Befunde behoben (`docs/VALIDATION.md` → Etappe 53, Nachprüfung 53).
  Offen: erneuter Prüfer-Lauf, Gerätetest (Langdruck, Fensterhöhe und Fenster-Tasten im
  Querformat, Zeichnen mit dem Finger, Bilder mit echter Berechtigung und gedrehte Kamerafotos,
  TalkBack, wie die Kunst in WhatsApp/Telegram/Discord/Signal wirklich aussieht).
  Ursprünglicher Wunsch (Owner, 2026-11-14), schließt 39 (Text-Stile) ein. Öffnen: Emoji-Taste lange drücken → Stift im Popup. Fenster: oben
  der vorhandene Feldtext (bearbeitbar), Mitte freie Zeichenfläche (Stift, Radierer, Linie, Kreis,
  Füllen, Rückgängig; auch Foto/Screenshot als Vorlage), unten Live-Vorschau als Text aus Zeichen.
  Owner: nicht „entweder oder“, sondern alles per Auswahl frei kombinierbar und selbstständig neu
  formatiert wie die bekannten Unicode-/ASCII-/Braille-Art-Generatoren:
  - Jedes Element (Zeichnung, Bildvorlage, Textzeilen, Rahmen) hat eigene Optionen: Stil (Braille,
    Blöcke, Halbblöcke, ASCII klassisch, Emoji-Pixel, eigene Zeichenmenge), Schwelle/Kontrast,
    Umkehren, Dithering, Kontur oder Fläche; Text als Groß-Schrift (FIGlet-artige Zeichenschriften),
    in Unicode-Stilen (fett, kursiv, Schreibschrift, Fraktur, Doppelstrich, Kreise, Quadrate,
    durchgestrichen, unterstrichen, kombinierbar) oder normal.
  - Anordnung: Elemente neben-/untereinander, zentriert, mit Rahmen (Linien-, Doppel-, Runde-,
    Emoji-Rahmen), Abstand; Spiegeln.
  - Jede Änderung (Stil, Breite, Element, Ziel-App) formatiert das Ganze sofort neu.
  - Ziel-App-Profil: Breite (Chatblase etwa 20–30 Zeichen, Regler mit Vorschau „passt auf ein
    Handy“), Codeblock wo möglich (WhatsApp, Discord), Leerstellen als `⠀` (U+2800) statt Leerzeichen,
    damit nichts verrutscht; Warnung, wenn die gewählte Kombination in der App vermutlich zerfällt.
  - Ergebnis nur per Tipp ins Feld (danach kurz „Rückgängig“) oder kopieren; als Textbaustein
    speicherbar. Alles lokal. Grenzen: Screenreader lesen Zeichen-Kunst als Zeichensalat; sehr kleine
    Bildschirme oder große Schrift beim Empfänger können umbrechen. Offen beim Owner: Standardstil
    (Vorschlag Braille) und Haupt-Apps.
- **54 Update-Fenster mit „Was ist neu“** ist gebaut (siehe FEATURES.md, Nachweis in
  `docs/VALIDATION.md` → Etappe 54). Entscheidung des Leads zum Chip: Er steht nur in der sonst
  leeren Leiste (nach Befehls-Chips, gemaltem Symbol und Zwischenablage-Pille, statt
  Satzzeichen-Vorschlägen), nie statt Wortvorschlägen; so ist er „immer da“, sobald die Leiste
  frei ist (Feldanfang, nach dem Löschen), ohne beim Tippen zu stören. Auch in Passwortfeldern und
  Inkognito (verrät nur die Build-Nummer), nie bei gesperrtem Gerät. „Installieren“ im Dialog öffnet
  die Seite Updates und startet dort den Download einmal. Offen: Prüfer-Review (Hook in
  `LatinIME.setNeutralSuggestionStrip`, `SettingsActivity`), Gerätetest (Dialog nach echtem
  Update, Chip in der Leiste, Notizen eines echten Releases von GitHub).
  Ursprünglicher Wunsch (Owner, 2026-11-14). Owner: „Neue Updates werden sofort in der App angezeigt, zusammen mit einem kurzen Dialog,
  was neu ist.“ Vorschlag des Leads: (1) Beim Öffnen der Einstellungen erscheint, wenn die letzte
  Prüfung einen neueren Build fand, ein Dialog „Build N ist da“ mit den Release-Notizen dieses
  Builds (kurz, aufklappbar) und „Installieren“ / „Später“; „Später“ blendet ihn für diesen Build
  aus. (2) Nach dem Installieren eines neuen Builds einmal „Neu in Build N“ mit den Notizen aus der
  APK (offline, aus `release-notes/next.md` beim Bauen eingebettet). (3) Optional einmal ein
  dezenter Chip in der Leiste „Build N verfügbar“ (nicht in Passwort-/Inkognito-Feldern, nicht
  gesperrt). Prüfung weiter nur mit dem bestehenden Schalter „täglich automatisch“ bzw. auf Tipp.
  Voraussetzung: Die Prüfung liest das Release-Repository `Tra1c0/SecondHandAgenticKeyboard`; dort landen
  Builds nur mit dem Secret `RELEASES_TOKEN`, das fehlt (Workflow-Warnung). Alternative (Owner
  entscheidet): direkt die Releases dieses Repositorys lesen (öffentlich, kein Secret nötig, dann
  auch Test-Builds; Kanal „nur geprüfte“ / „auch Test-Builds“ wählbar). (M)
  Owner (2026-11-14): Der Chip in der Tastatur steht IMMER da, solange ein neuerer Build bekannt
  ist (nicht nur einmal), dazu der Dialog in der App wie oben. Owner (2026-11-14): Variante A —
  „Ich will, dass die Builds kontrolliert released werden.“ Quelle ist das Release-Repository (seit 2026-11-14 vom Owner neu angelegt, vorher `Tra1c0/AgenticKeyboard`)
  `Tra1c0/SecondHandAgenticKeyboard` (Secret `RELEASES_TOKEN`, Einrichtung in BUILD.md, macht der Owner).
  Dazu: Der Workflow veröffentlicht dort nur Builds von `main` (geprüft); Test-Builds vom
  Arbeitsbranch bleiben Vorab-Releases in diesem Repository und erscheinen nicht als Update. Der Chip
  verrät nur „Build N verfügbar“; ein Tipp öffnet die Updates-Seite. Neue Builds erkennt die App nur
  über die Prüfung (Schalter „täglich automatisch“ oder „Jetzt prüfen“).
- **55 Diagnose: Letztes Feld** (Entscheidung des Owners vom 2026-11-15) ist gebaut (siehe
  FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 55). Anlass (Owner): „Stellenweise gibt's
  gar keine Vorschläge, Groß-/Kleinschreibung, Autokorrektur etc. Keine Ahnung wo und warum.“ Die
  Diagnose zeigt als erste Karte das letzte Feld: App, Feldtyp und Flags, was die App erlaubt,
  Sprache und Wörterbuch, was die Tastatur selbst abschaltet, und ein Urteil in einem Satz. Werte
  aus denselben `InputAttributes`/`SettingsValues`, mit denen die Engine arbeitet; nur im Speicher;
  Passwortfeld nur „Passwortfeld“, gesperrt nur „nichts gemerkt“ (Felder ohne Lernen seit 61 mit Merkmalen, ohne App). Nächster Schritt: Tritt
  es wieder auf, öffnet der Owner sofort Einstellungen → Diagnose und schickt einen Screenshot; dann
  wird die eigentliche Ursache behoben. Prüfer-Befunde behoben (`docs/VALIDATION.md` →
  Nachprüfung 55: Firefox-Workaround, Leiste, eigene Einstellungen). Offen: Nachprüfung, Prüfer-Review (eine Zeile in
  `LatinIME.onStartInputViewInternal`), Gerätetest.
- **61 Korrektur in Apps erzwingen** (Owner, 2026-11-16: „In Insta-Privat-Chats gibt's keine
  Autokorrektur.“; davor: „Stellenweise gibt's gar keine Vorschläge, Groß-/Kleinschreibung,
  Autokorrektur.“) ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 61).
  Annahmen des Leads, die der Owner ändern kann: Instagram (`com.instagram.android`) ist
  voreingestellt und entfernbar; die echten Flags seines Chatfelds sind ohne Gerät unbekannt
  (Annahme: `NO_SUGGESTIONS`, kein `CAP_*`, vermutlich `IME_FLAG_NO_PERSONALIZED_LEARNING`). Erzwungen
  wird nur in Textfeldern, nie in Passwort-, E-Mail-, Adress-, Filter-, Phonetik-, Namen- und
  Postanschrift-Feldern und nie bei der Aktion „Suchen“; Lernen und Inkognito bleiben unverändert, und
  die eigenen Schalter gewinnen. Felder ohne Lernen (`IME_FLAG_NO_PERSONALIZED_LEARNING`, eigenes
  Inkognito) behalten auf der Karte „Letztes Feld“ Feldtyp, Merkmale und Urteil (nur den App-Namen
  nicht): genau dort braucht der Owner den Grund. Offen: Prüfer-Review der Engine-Nähe (`InputLogic`,
  `SettingsValues`, `InputAttributes`), Gerätetest in Instagram; korrigiert es dort trotzdem nicht,
  schickt der Owner einen Screenshot der Karte nach dem Tippen im Chat.
- **56 Neue Builds sofort melden** (Entscheidung des Owners vom 2026-11-15: „Schalter, anfangs an“)
  ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 56). Anlass (Owner):
  „Kannst du es so machen, dass ein neuer release build sofort einen chip an die Tastatur schickt?“
  Echter Push (FCM oder ein Server) ist verworfen. Stattdessen prüft die Tastatur beim Öffnen in
  einem Feld, höchstens alle 15 Minuten seit dem letzten Versuch (gespeichert, gilt über Neustarts),
  im Hintergrund dieselbe Release-Liste wie „Jetzt prüfen“, mit ETag (`If-None-Match`, 304 behält
  das Ergebnis); Fehlschläge in Folge verdoppeln die Wartezeit bis 4 Stunden. Kein Hintergrund-Job
  alle 15 Minuten; die tägliche Suche und „Jetzt prüfen“ bleiben. Nicht gesperrt, nicht in
  Inkognito- oder Passwortfeldern, nicht mit Datensparmodus, nicht ohne Netz. Ein neu gefundener
  Build zeigt den Chip sofort in der leeren Leiste desselben Felds. Haken: kein neuer in `LatinIME`,
  die Zeile von 55 (`LastField.capture`) startet die Prüfung. Offen: Prüfer-Review (Engine-Nähe:
  Aufruf aus `capture`, Refresh über `setNeutralSuggestionStrip`), Gerätetest (echtes GitHub, 304,
  Datensparmodus, Chip nach einem echten Release).
- **57 Name SecondHand** (Auftrag des Owners vom 2026-11-16: „Bitte benenne die App in SecondHand um, ändere das Logo.“)
  ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 57). Annahmen des Leads,
  die der Owner ändern kann: Nur der sichtbare Name ändert sich; App-ID, Pakete, Klassen,
  Einstellungsschlüssel, Dateinamen auf dem Gerät, die beiden GitHub-Repositories, Secrets und das
  Tag-Format `build-<N>` bleiben (sonst gäbe es kein Update und Daten gingen verloren). Der frühere Debug-Zusatz
  „Lab“ entfällt: auch die ausgelieferte Variante `debugNoMinify` heißt „SecondHand“. Builds werden unter zwei Dateinamen veröffentlicht, solange
  installierte Builds bis 27 nur den alten Namen akzeptieren (`docs/BUILD.md`). Offen: Gerätetest
  (Name in der Android-Tastaturliste, in den Rechtschreib-Einstellungen, in der Benachrichtigung; ein
  echtes Update von Build 27 über die neue Datei) und der erste echte Lauf des Workflows auf GitHub.
- **58 Logo SecondHand** (Auftrag des Owners vom 2026-11-16, gleicher Satz) ist gebaut (siehe
  FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 58). Entwurf des Leads in
  `docs/referenzen/logo/` (die Variante „Sekundenzeiger“ ist nicht genommen). Annahmen: Das
  adaptive Symbol und die Bild-Symbole für Android 5–7 zeigen denselben Ausschnitt; die
  einfarbige Variante ist die Umriss-Fassung. Store-Bilder unter `fastlane/` sind Upstream-Material
  und bleiben. Offen: Ansehen auf dem Gerät (Launcher, runde Maske, Themed Icons).
- **59 KI mit höheren Grenzen (Meldung des Owners, 2026-11-16: „KI hat ein Zeichenlimit.“)** ist gebaut
  (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 59). Der Owner nutzt Gemini
  (selbst eingetragene Modellkennung, „Gemini 3.8 Flash-Lite“); bei `@ki` verschwand der Chip nach 5–6 Zeilen, das ist
  die 200-Zeichen-Grenze. Vier Grenzen, kohärent angehoben: (1) `@ki` und `@translate` bis 2000
  Zeichen, auch über mehrere Zeilen (Enter führt in einer laufenden Frage nie die Aktion des Feldes aus, Senden bleibt der Tipp auf den Chip),
  ohne zusätzlichen Editor-Lesezugriff beim normalen Tippen (Lesefenster nur während eines solchen
  Befehls bis zu dessen Anfang); (2) Auswahl und Modellinput 32.768 statt 8.192, ein Ergebnis 65.536
  statt 16.384, alle behaltenen Texte eines Workflows 131.072 statt 32.768; (3) Ausgabetoken
  `clamp(Eingabe / 2 + 2048, 8192, 16384)` (für alle drei Anbieter, Entwürfe 4096) und
  eine abgeschnittene Antwort aller drei Anbieter ist „abgeschnitten, Text zu lang für das Modell“,
  nie ein Ergebnis; (4) Zeit: Lesen 120 s, ein Aufruf 150 s, ein Workflow 310 s (vorher 10/45/95 s,
  die jede Antwort über 10 s zu einem Zeitfehler machten), ohne Streaming. Dazu eine feste Regel
  „Never translate“ in den Anweisungen aller Aktionen „in der Originalsprache“. Annahmen des Leads,
  die der Owner ändern kann: 2000 Zeichen für beide Befehle; ein älterer `@ki` bleibt bis 2000
  Zeichen dahinter der laufende Befehl (Autokorrektur aus), auch über eine Leerzeile; zwei Leerzeilen
  hintereinander beenden die Frage (Entscheidung des Leads in der Nachprüfung); über 2000 Zeichen
  zeigt ein gedämpfter Chip die Grenze; ein alter langer
  Befehl, dessen Anfang außerhalb des kurzen Lesefensters liegt, wird nicht erkannt;
  der dritte KI-Schritt eines Workflows kann am Gesamtbudget (310 s) enden. Offen: Gerätetest und
  echte Anfragen (lange Texte, Gemini-Vertrag nicht gegen die Referenz geprüft).
- **62 Ausgelieferte Variante ohne Debug-Modus** (Befund der Prüfung vom 2026-11-16 und Bericht des
  Owners „stellenweise Performance-Probleme“) ist gebaut (siehe FEATURES.md, Nachweis in
  `docs/VALIDATION.md` → Etappe 62). Die installierte APK war `debuggable`: Android führt sie ohne
  volle Optimierungen aus, und der Debug-Modus war an (jedes getippte Wort ins Logcat, fünf Ausnahmen
  bei Auffälligkeiten). Jetzt ist die Variante `debugNoMinify` nicht debuggable (Name, `.debug`,
  Signatur, Quellordner und fehlendes R8 unverändert), und ein gespeicherter Debug-Modus „an“ wird
  einmal zurückgesetzt. Die Diagnose zeigt beide Zustände. Annahme: Das macht Tippen und Einstellungen
  schneller; nichts davon ist gemessen. Offen: Gerätetest (spürbarer Unterschied, Diagnose-Zeilen,
  Debug-Einstellungen nach fünf Tipps wieder einschaltbar) und, falls es weiter ruckelt, eine
  Messung auf dem Gerät; R8 ist eine eigene spätere Etappe.
- **63 Release scheitert laut** (Befund der Prüfung vom 2026-11-16) ist gebaut (siehe FEATURES.md,
  Nachweis in `docs/VALIDATION.md` → Etappe 63). `agent-apk.yml` prüft die APK vor dem
  Veröffentlichen (Signatur, Zertifikat des Owners, Versionscode, Paket, nicht debuggable), verlangt
  auf `main` `RELEASES_TOKEN`, einen grünen Testlauf des Commits und liest das öffentliche Release
  zurück, und erlaubt einen erneuten Lauf; `agent-tests.yml` baut zusätzlich die ausgelieferte
  Variante und kennt Mindestzahlen und exakte Baseline-Namen; ein Vertragstest hält Workflow und App
  zusammen. Die Mindestzahlen stehen auf Werten aus Lauf 82 (GitHub-Lauf 83 damit grün). Offen: der
  erste echte Lauf des Release-Workflows auf GitHub (zuerst als Test-Build, die Schritte für `main`
  erst beim geprüften Build).
- **70 Aussehen und Gefühl** ist gebaut (siehe FEATURES.md, Prüfnachweis in `docs/VALIDATION.md`):
  A1, A3 bis A6, S13 und Z6 behoben; A2 (abgebrochene Wischgeste auf der Leertaste) ließ sich in
  Robolectric nicht nachstellen, `PointerTracker` blieb unverändert. Offen: Gerätetest (Licht hinter
  Panel, Rad, Rechner, Zeichen-Kunst und Menü pausiert und läuft weiter; Vorschau der Zeichen-Kunst
  folgt dem Finger; Lauftext hält an; abgebrochene Wischgeste auf einem echten Gerät).
- **64 Tipp-Leistung** (Befunde P1 bis P11, P13, S9, K4, Z9 der Prüfung vom 2026-11-16) wird in vier
  Teilen gebaut. **64a messen ist gebaut** (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` →
  Etappe 64a): `TypingPerf` zählt ohne Text, wie lange die eigenen Hooks je Tastendruck im Hauptthread
  brauchen und wie viele Anfragen an die App und an Android dabei laufen; die Diagnose hat dazu die Karte
  „Tippen“. **64b weniger Anfragen je Leistenaktualisierung ist gebaut** (`LockState`: ein gemeinsamer,
  höchstens eine Sekunde alter Sperrzustand; Text hinter der Schreibmarke und Clip in Feldern mit Ziel
  nicht bei jeder Aktualisierung; Chip-Zeilen, Update-Chip und Smart-Paste-Erkennung bei gleichem
  Inhalt nicht neu; Feldstart der Update-Prüfung im Hintergrund; Haptik einmal je Vibrator).
  Offen: ein Gerätetest mit Screenshot der Karte (vor und nach 64b bis 64d), 64c und 64d.
  **Offen für eine spätere Sitzung (64c, 64d; Entwurf liegt nicht im Repository):**
  - 64c: Befehlserkennung (P3) höchstens ein Lesen je Aktualisierung, geteilt, keins bei gleicher Schreibmarke und
    gleichem Textstand (nur lesender Zugriff auf den Textspeicher in `RichInputConnection`); (P4) Namenstabelle einmal
    bauen, eine Analyse je Aktualisierung, `forget()` nur beim Übergang, Regex in `ContextSuggest` als Konstanten;
    (P9) Chip-Quellen je leerer Leiste einmal (kleinste Änderung in `LatinIME`); (S9, K4) Fangnetz je Befehl in `load`,
    `onEnter` und Chip-Tipps, Zähler mit Befehlsname und Fehlerklasse auf der Karte „Tippen“; (Z9) Reihenfolge der Leiste
    in `docs/ARCHITECTURE.md` um Screenshot-Chip und Autofill ergänzen. `TypingPerf.commandFailed` gibt es noch nicht.
  - 64d: (P5) Text hinter der Schreibmarke in `FieldCorrectionWatcher.checkpoint` nur lesen, wenn es etwas lernen kann;
    (P6) „Meine Daten“ im Hintergrund laden, Fehlschlag bis Feld oder Entsperren merken, beim Sperren weg;
    (P11) Lernspeicher gesammelt im Hintergrund schreiben (Feldende und Zerstören schreiben aus), Gedächtnis beim Start
    im Hintergrund lesen; Tests: Änderung überlebt Feldende, zwei Änderungen ein Schreiben.
- **69 Einstellungen und Texte** (Befunde E1 nur Text, E2, E3, E5, E6, E7, E9, E10, S5 der Prüfung vom
  2026-11-16) ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 69). Die Suche
  findet jede Seite des Forks (Titel und deutsche und englische Stichwörter); „Sichern und
  Wiederherstellen“ sagt ehrlich, was in der Datei steht und was nicht, und dass sie nicht verschlüsselt
  ist; die Anleitung nennt die echten Menüpfade; Tippflächen mindestens 48 dp; abhängige Schalter auf
  „Tasten & Sprachen“ grau mit Grund; Fußzeile bricht um; fehlende deutsche Texte ergänzt; „Über“
  verweist auf das Repository des Forks statt auf die Fehlerseiten von HeliBoard; die tote
  Übergangsseite ist entfernt. Offen (Owner entscheidet): die Sicherung um die eigenen Speicher des Forks
  erweitern (Profile, Gedächtnis, Bausteine, Orte, Korrekturen, Trefferzonen, gemalte Symbole,
  KI-Einstellungen ohne Schlüssel); bis dahin steht im Dialog, dass sie fehlen. Offen: Gerätetest (Suche,
  Dialogtext, Größe der Tippflächen, Umbruch der Fußzeile mit großer Schrift).
  Noch zu tun für eine spätere Sitzung: Lint (`python3 tools/dev.py lint`) und erneuter gezielter Lauf von
  `SettingsSearchTest` (Stichwort „lighting“ nach dem letzten Lauf ergänzt); ob unbenutzte Strings
  (`about_wiki_link*`, `about_community_links*`) aus den Übersetzungen entfernt werden sollen.
- **71 Datenpaket DE/EN: Slang schützen** (Auftrag des Owners vom 2026-11-17: das Paket „SecondHand DE/EN-Datensätze 1.0“
  „sinnvoll aussortieren und die Autokorrektur erweitern/ergänzen“) ist gebaut, nach der Nachprüfung 71 mit den Wörterbüchern der App als Maßstab (siehe FEATURES.md, Nachweis in
  `docs/VALIDATION.md` → Etappe 71, Auswahl und Gründe in `docs/referenzen/datensaetze/AUSWAHL.md`). Aus dem Paket
  (136 MB, gemischte Lizenzen, nicht im Repository) wird nur die eigene, CC0-lizenzierte Liste informeller Formen
  genommen: 1.695 von 3.893 Einträgen werden zu einer 14 KB großen Asset-Datei, die die Autokorrektur nutzt, um ein
  getipptes Wort **nicht** zu ersetzen (Vorschlag bleibt in der Leiste). Dazu gedehnte Wörter und Lacher als Regel.
  Annahmen des Leads, die der Owner ändern kann: keine Schalter (die Liste verbietet nur Korrekturen, sie lernt und
  schlägt nichts vor); nur Deutsch und Englisch; „dont/cant“ bleiben korrigierbar (Apostroph); ein Konsonant dreimal
  am Wortende („addresss“) bleibt ein Tippfehler, erst vier (oder ein Vokal dreimal) zählen als Betonung. Bewusst nicht
  gebaut: Wortvorschläge aus der Liste (das Paket erlaubt sie nur bei getipptem Anfang oder ausdrücklichem
  Fachgebiet; sie würden die Leiste füllen, ohne dass es messbar wäre), Abkürzungen aufklappen (Paket: nie still),
  eine Tabelle häufiger Fehlschreibungen (von 203 sauberen Einzelwort-Fällen der Redaktion (Rechtschreibung, doppelter oder fehlender Buchstabe, Dreher, Nachbartaste) liegen 182 einen Schritt vom Ziel, die
  Engine findet sie selbst; nur die 21 weiter entfernten wie „Portmonai“ wären neu), Verwechslungsgruppen (brauchen den Satz), die
  Häufigkeits- und Kontextdaten (CC BY-SA, groß, keine Rechtschreibung). Offen: Gerätetest mit echten Wörterbüchern;
  die Entscheidung des Owners, ob der Zusatzwortschatz (Spiele/Technik/Musik, etwa 450 Formen der Liste) als Vorschlag erscheinen soll.
- **Runde 2 nach dem Scan (Auftrag des Owners vom 2026-11-16: „suche nach Lücken, Verbesserungen und
  Fehlern … scanne gebietübergreifend“)**: teilweise gebaut (Stand unten). Die Befunde stehen mit Stelle und
  Schwere in `docs/PRUEFBERICHT-2026-11-16.md`; jede Etappe behebt die dort genannten Nummern und
  bekommt einen Prüfer. Nichts davon ist auf einem Gerät gemessen. Reihenfolge: erst 64, 65, 69,
  70 (unabhängig voneinander), danach 66, 67, 68.
  Stand 2026-11-18: 64a, 64b, 65, 69 und 70 sind gebaut und seit Build 32 auf `main` (eigene
  Einträge „… ist gebaut“ in dieser Liste, Prüferbefunde in `docs/HANDOFF.md` → Offene Punkte und
  `docs/VALIDATION.md`), ohne Gerätetest. Offen: 64c, 64d, 66, 67, 68. Angehalten auf Wunsch des Owners.
  - **64 Tipp-Leistung** (P1 bis P11, P13, S9, K4, Z9): 64a messen (Zähler ohne Inhalt für die Zeit
    der eigenen Hooks und für Anfragen an die App und an Android je Tastendruck; Diagnose-Karte
    „Tippen“ mit Median, 95 %, Maximum und den zehn langsamsten Stellen); 64b weniger
    Systemanfragen je Leiste (ein gemeinsamer, kurz zwischengespeicherter Sperrzustand, der bei
    Feldstart und Bildschirm-Ereignissen verfällt; Zwischenablage und Text hinter der Schreibmarke
    nicht bei jeder Aktualisierung); 64c Befehlserkennung einmal je Aktualisierung und höchstens ein
    Lesen aus der App, Fangnetz je Befehl; 64d Laden und Speichern der Lernspeicher und von „Meine
    Daten“ nicht im Hauptthread.
  - **65 Härtung** (S2, S3, S4, S6, S7, S8, S12): Zwischenablage-Verlauf beachtet Inkognito, Felder
    ohne Lernen, Passwortfelder und Sperre; eine gescheiterte Wiederherstellung lässt alles, wie es
    war, und meldet es; keine Wörter in Protokollzeilen; Tresor und Feldtext nach Ende aus dem
    Speicher. Gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` → Etappe 65). S1 war schon
    behoben (die Zeile nennt nur die Länge). Entscheidung des Leads: Der Verlauf lässt nur solange nichts zu, wie die Tastatur
    in einem privaten Feld aktiv ist; bei ausgeblendeter Tastatur kopierte Clips bleiben erhalten (P270).
    Offen: Gerätetest (Verlauf in Passwortfeld, Inkognito und gesperrt; Wiederherstellen mit
    beschädigter Datei; Wörterbuchdatei von außen öffnen). Bekanntes Restrisiko: Stirbt der Prozess
    mitten in einer Wiederherstellung, bleibt der Datenordner halb geschrieben und der alte Stand in
    `<Name>2`; erst der nächste Wiederherstellungsversuch holt ihn zurück (unwahrscheinlich, das Fenster
    ist kurz). Behebung später: in einen Zwischenordner entpacken, am Ende tauschen, Rest beim App-Start
    zurückholen. Weitere Fundstellen ohne Änderung: S5 (Etappe 69) und Zeilen hinter
    `DebugFlags.DEBUG_ENABLED` sowie `dumpAllWordsForDebug` (nur im Debug-Modus bzw. auf ausdrückliche
    Aktion).
  - **66 Tipp-Logik** (T1, T6 bis T10): eigene Wörter nur dort zählen, wo die Autokorrektur an ist und
    das Wörterbuch geladen; Fangnetze in den Lern-Hooks; kleinere Fehler in Tippfehler-Gedächtnis,
    Fremdwörtern, Trefferzonen, Kontext-Chips.
  - **67 Befehle und KI-Abläufe** (K2, K3, K5 bis K10, Z7): QR-Chip, „Kein Schlüssel“ öffnet sicher
    die KI-Seite, eingefügte Kontaktdaten privat, „Senden“ nur mit Schlüssel, `@wecker`/`@timer` in
    der Tastatursprache, Ganztagstermin mit UTC-Mitternacht, `@mix` und `@contact` melden Fehler,
    ein Datums-Rückfall. Dazu neu (Befund der Nachprüfung 59): Lehnt ein Modell die Anfrage wegen
    seiner kleineren Ausgabegrenze ab, merkt sich die App die Grenze für dieses Modell und bittet um
    erneutes Senden (kein automatisches zweites Senden).
  - **68 Lernen einheitlich** (Z1 bis Z5, Z8, S11): eine Liste aller Lernspeicher mit einem
    „alles löschen“, eine gemeinsame Regel (Hauptschalter, eigener Schalter, Inkognito, Feld ohne
    Lernen, Passwort, ausgeschlossene Apps, Sperre), Hinweis auf den Seiten, wenn der Hauptschalter
    aus ist; die Gestendaten-Sammlung für HeliBoard verschwindet aus den Einstellungen.
  - **69 Einstellungen und Texte** (E1 nur Text, E2, E3, E5, E6, E7, E9, E10, S5): ehrlicher Text
    bei „Sichern und Wiederherstellen“, Suche findet die neuen Seiten, Menüpfade der Anleitung,
    48-dp-Tippflächen, fehlende deutsche Texte, „Über“ ohne Verweise zum Melden bei HeliBoard.
  - **70 Aussehen und Gefühl** (A1 bis A6, S13, Z6): Beleuchtung pausiert hinter Panel, Rad,
    Rechner, Zeichen-Kunst und Menü; Fangnetz beim Zeichnen der Beleuchtung; abgebrochene Wischgeste
    (erst mit Test bestätigen, `PointerTracker` nur mit Prüfer); Zeichen-Kunst rechnet im Hintergrund.
  - Ohne Etappe, Owner entscheidet oder später: Code-Verkleinerung R8 (eigener Test-Build),
    Sicherung um die eigenen Speicher erweitern (E1), helle neue Einstellungsseiten (E8), „Dein Look“
    mit echten Farben (E11), eigene Wörter je Sprache (T5), Befehlsname als Erwähnung (T4), Sperre der
    Korrektur einen Tastendruck früher (T3), Eckenrundung in Popups (A7), private Bereiche ohne
    Lesen je Wort (P12), „Installieren“ lädt genau den gezeigten Build (B11), feste Wartezeiten in
    Tests (B12), Größe der Build-Notizen prüfen (B13), KI-Ergebnis nach einiger Zeit aus der
    Zwischenablage nehmen (S10).
- **Graue Auswahlkarten (Owner, 2026-11-14: „Ja“):** Die grauen Mockup-Karten (AUS, NICHTS,
  AUSGEBLENDET, FLACHER LOOK) bekommen helle Schrift (Kontrast mit dunkler Schrift nur 1,74:1).
  Gebaut (`docs/VALIDATION.md` → Etappe 37, „Graue Karten“): helle Schrift 4,71:1 gewählt, 6,92:1
  gedimmt; FLACHER LOOK endet dafür in #6B6B76 statt #9C9CA6. Offen: Gerätetest.
- **47 KI-Kontext im Hintergrund — verworfen (Owner, 2026-11-14):** „Dann lassen wir das.“ Alles
  lokal; die Tastatur lernt selbst dazu (45, 46).
- **Verworfen (Owner, 2026-11-14):** Mail-Anbindung (Gmail/IMAP) und Codes aus Benachrichtigungen.
  Einmalcodes kommen nur über die Zwischenablage (44).
- **34 Eigene RGB-Lichtmuster gestalten** (Owner, 2026-11-14: „eigene rgb lichtmuster. Vollständig
  customizable.“, XL; nicht in den Test-Build 22, sondern direkt in den geprüften Build 23 mit eigenem
  Prüfer) ist gebaut (siehe FEATURES.md, `docs/VALIDATION.md` → Etappe 34). Annahmen des Leads, die der
  Owner ändern kann: Ein eigenes Muster hat 1–4 Ebenen, gemischt mit Normal, Addieren, Multiplizieren
  oder Aufhellen (Screen); jede Ebene hat eine von zwölf Formen (Fläche, linearer, radialer und
  Kegelverlauf, Wellen, Plasma, Rauschen, Streifen, Ringe vom Tippen, Spot auf der letzten Taste,
  Höhenlinien, Funkeln), 2–8 Farbstopps, Tempo (−4 bis 4 × die „Dauer“ der RGB-Seite), Richtung,
  Größe, Verschiebung, Deckkraft, die Bewegungskurve aus 29, eine Tipp-Reaktion (keine, Puls, Ring,
  getippte Taste leuchtet) und Farben wie gewählt oder als Farbkreis-Durchlauf (eine Drehung je
  „Dauer“). „Getippte Taste leuchtet“ ist ein weicher Fleck an der Tippstelle (etwa eine Taste
  groß), kein Umriss der Taste; Puls und Ring zeigen die Ebene nur während des Nachleuchtens. Die
  Dauer, Rückwärts, Sättigung, Helligkeit, Deckkraft und das Nachleuchten der RGB-Seite gelten weiter;
  Kurve, Farben und Wellenbreite der RGB-Seite nicht (die hat das Muster selbst). Eigene Muster haben
  keine feinen Detail-Kacheln wie die Texturen aus 29c, sie werden im Licht-Raster 48 × 32 gerechnet
  (feine Linien und Funken sind darum weich). „Eigenes Muster aus …“ macht aus dem gewählten Muster
  einen Ausgangspunkt: Wellen und Kreis genau, die Texturen als Annäherung aus Ebenen. Höchstens
  24 eigene Muster. Datei: JSON mit fester Form, Version 1, höchstens 16 KB. Offen: Gerätetest
  (Aussehen, Bildkosten der eigenen Muster am Telefon, Bedienung des Editors), Prüfer vor Build 23.
- **32 Emoji-Mix: zuletzt gemischt** ist gebaut (siehe FEATURES.md). Offen: Gerätetest (Höhe der
  Reihe in der Emoji-Ansicht, Einfügen in WhatsApp/Telegram).
- **35 Tolerantere Emoji-Vorschläge** (Auftrag des Owners vom 2026-11-13, eigener Branch neben
  Build 13) ist gebaut (siehe FEATURES.md), mit Prüfer (`docs/VALIDATION.md` → Etappe 35). Vom
  Owner gewählt: Wortformen, Groß/klein und Umlaute, Wortanfänge, Tippfehler, Deutsch/Englisch
  gemischt, Synonyme; höchstens zwei Emojis hinter den ersten zwei Wörtern. Offen: Gerätetest
  (Trefferqualität beim echten Tippen, Ladezeit auf dem Telefon), Gestenschreiben bekommt die
  neuen Treffer noch nicht. (M)

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

Wünsche des Owners vom 2026-11-19, aufgenommen, aber noch nicht zugeschnitten: Umfang und Reihenfolge sind
offen, gebaut wird erst nach einer Entscheidung zu den offenen Fragen.

- **W1 Ineinandergreifende Funktionen (kein eigenes Ökosystem).** Klarstellung des Owners vom 2026-11-19: kein
  Server, keine Konten, kein Abgleich, sondern Funktionen, die fließend ineinandergreifen wie bei Googles
  Tastatur mit Passwortmanager, Gmail, lernender Eingabe und AICore. „Keine Konten, kein Backend“ bleibt
  gültig. Bausteine (Deutung des Leads, Reihenfolge offen):
  (a) Passwortmanager: Autofill-Vorschläge in der Leiste zuverlässig machen (die in 73c gemeldeten Schwächen:
  feste Breite, Reihenfolge, Freigabe der Ansichten, verspätete Antworten) und mit Tresor und Zwischenablage
  abstimmen; ein eigener Passwortspeicher ist nicht gemeint, solange der Owner ihn nicht verlangt.
  **Etappe 79 liefert den ersten Teil** (nur lokal, nicht gepusht): Breite aus der echten Leiste und der Dichte, Chips in der Reihenfolge
  des Dienstes, veraltete Antworten und Rückrufe ignoriert; eine Freigabe-Methode für die Ansichten gibt es in der
  Plattform-Schnittstelle nicht (P320), die Ansichten werden nur losgelöst. Chips in Passwortfeldern und im Inkognito-Modus
  sind gewollt und dokumentiert. Offen in (a): ein Lauf des Szenarios `autofill_inline` mit drei Chips, echte
  Passwortmanager und ein echtes Telefon, die Abstimmung mit Tresor und Zwischenablage.
  (b) Lernende Eingabe: bessere Wortvorhersage und Korrektur aus dem eigenen Tippen, auf dem Gerät, opt-in.
  (c) KI auf dem Gerät: siehe W2.
  (d) Übergänge zwischen den vorhandenen Teilen: z. B. Kopiertes → Ablage → KI-Aktion → Einfügen ohne
  Umwege, Vorschläge passend zum Feld (E-Mail, Adresse, Code), Diktat → Korrektur.
  (e) Gmail und Kalender: weiter 16c, blockiert bis zu eigenen OAuth-Zugängen des Owners.
  Erster Schritt: die Liste aus W3. (L, mehrere Etappen)
- **W2 AICore (KI auf dem Gerät).** Googles AICore mit Gemini Nano als zweiter, lokaler Provider neben dem
  eigenen Schlüssel: Textaufgaben (Korrigieren, Umformulieren, Zusammenfassen, Antwortvorschläge) ohne Netz und
  ohne Kosten. Bekannte Grenzen vor dem Bau zu prüfen (nicht geprüft): AICore gibt es nur auf bestimmten
  Geräten (u. a. neuere Pixel- und Galaxy-Modelle) und nur mit Google-Diensten; das Telefon des Owners ist ein
  Xiaomi 15 (Angabe vom 2026-11-20); ob es von AICore und den ML-Kit-GenAI-Schnittstellen unterstützt wird, ist
  nicht verifiziert (die Geräteliste auf developers.google.com war aus der Arbeitsumgebung nicht erreichbar). Die Regeln bleiben: jede Übernahme braucht einen eigenen Klick, lokale Werkzeuge werden als
  lokal gekennzeichnet, sensible Felder und Sperrzustand gelten auch hier. Braucht einen Eintrag in
  `docs/PROVIDERS.md`. (M bis L)
  **Geparkt (Owner, 2026-11-20):** Etappe 78a „AICore-Diagnose“ ist gebaut und getestet, aber nicht eingemischt
  und von keinem Prüfer gelesen. Sie liegt als Patch-Serie (7 Commits auf 2301f2d) in
  `docs/geparkt/78a-aicore-diagnose.patch` (einspielen mit `git am`). Befund der Machbarkeitsprüfung an den
  Bibliotheken selbst: Googles eigenständiger Client `com.google.ai.edge.aicore:aicore:0.0.1-exp02`
  (experimentell, proprietäre AICore-Bedingungen, minSdk 31) bindet die System-App AICore direkt, ohne Play
  services zur Laufzeit und ohne Telemetrie-Bausteine; ML Kit GenAI bringt dagegen einen Autostart-Provider und
  Telemetrie (datatransport) mit und wurde verworfen. Der Client kennt keine Statusabfrage; ein Test kann einen
  Modell-Download durch AICore auslösen. Kosten: etwa 1 MB, eine Berechtigung und ein `<queries>`-Eintrag in
  jeder Installation. Nichts davon lief auf einem Gerät. Grund fürs Parken: Der Owner will lieber sein eigenes
  Modell auf dem KI-Chip (W4) und ungern Google-Code in der Tastatur.
- **W4 Eigenes lokales Modell (ohne Google).** Idee des Owners vom 2026-11-20: statt oder neben AICore ein offenes
  Sprachmodell (z. B. von Hugging Face) auf dem Gerät, als weiterer lokaler Provider. Angehalten, bis der Owner
  seine eigene Recherche zu Tempo, Energieverbrauch und vergleichbaren Projekten abgeschlossen hat („Schnelligkeit
  und Sparsamkeit sind A und O“). Gedachte Stufen (Deutung des Leads, nichts davon geprüft): (1) Diagnose-Karte, die
  ein Modell nach ausdrücklicher Freigabe lädt, einen festen Testsatz korrigiert und Dauer, Ladezeit und Speicher
  zeigt, auf dem Prozessor und dem Grafikchip (`llama.cpp`, MIT); (2) eigener Dienst außerhalb des
  Tastatur-Prozesses, Modell nur bei Bedarf geladen; (3) KI-Chip über Qualcomms proprietäres Paket nur, wenn (1)
  zu langsam ist, vorher Lizenzfrage (GPL-3.0) klären; läuft dann nur auf diesem Chip. Vergleichswert: die
  AICore-Diagnose (78a). Nie beim Tippen, nur auf Tipp für eine KI-Aktion. (L)
  Nachtrag 2026-11-20: (a) Der Owner nennt Qualcomms **GenieX** (Entwickler-Vorschau seit 2026-06, BSD-3 für den
  GenieX-Code, zusätzlich Qualcomms Nutzungsbedingungen; Android auf Snapdragon 8 Elite; führt GGUF-Modelle von
  Hugging Face über `llama.cpp` im Hybrid-Modus auf KI-Chip und Prozessor aus oder vorübersetzte Modelle nur auf
  dem KI-Chip; Kotlin-Paket über Maven, Demo-App „GenieX Chat“). Quelle: Suchergebnisse zu Qualcomms Seiten, nichts
  davon selbst ausprobiert; Tempo und Energie unbekannt. GenieX ist damit der bevorzugte Weg statt Stufe (3).
  (b) Klarstellung des Owners: Das eigene Modell ist **nur für sein eigenes Gerät** (Xiaomi 15) gedacht. Folgen:
  kein Rückfall für andere Chips nötig; die Weitergabe-Lizenz der Qualcomm-Laufzeit stellt sich nur, wenn sie in
  einer verteilten APK steckt. Die Builds dieses Projekts sind aber öffentlich herunterladbar (Vorab-Releases),
  also gehört W4 in eine eigene Build-Variante, die nicht veröffentlicht wird, oder in eine getrennte kleine App,
  die die Tastatur lokal anspricht. **Entschieden (Owner, 2026-11-20): getrennte kleine App** nur auf dem Gerät
  des Owners; sie hält Laufzeit und Modell und wird nie veröffentlicht, die Tastatur bleibt für alle gleich und
  ohne proprietären Code. Noch festzulegen beim Bau: die lokale Schnittstelle (nur für die eigene Signatur
  erreichbar, kein offener Netzwerk-Port), und dass die Regeln der Tastatur weiter gelten (Aufruf nur auf Tipp,
  Übernahme mit eigenem Klick, nichts loggen). Gebaut wird erst nach der Recherche des Owners zu Tempo und Energie.
  Modellwahl (Owner, 2026-11-20): ein **fertig abliteriertes Modell mit 4 Mrd. Parametern** als GGUF in 4-Bit
  (Kandidat: Qwen3-4B abliteriert; Qwen 3.8 gibt es laut Websuche offen nur mit 27B). Grund des Owners: Das
  Werkzeug soll ohne Ablehnungen und angehängte Klauseln redigieren. Die Modelldatei bleibt austauschbar.
  Korrektur (Owner, 2026-11-20): **nur der KI-Chip, kein GGUF** und kein Hybrid-Modus. Das heißt: QAIRT-Weg
  von GenieX mit einem für den Snapdragon 8 Elite übersetzten Modell. Qualcomm bietet Qwen3-4B fertig übersetzt
  an (w4a16, QAIRT 2.42, laut Suchauszug der Modellseite), aber nicht abliteriert. Ein abliteriertes Modell hat
  dieselbe Bauart, müsste also mit Qualcomms Exportwerkzeug (`qai_hub_models`, „custom weights“ ist bei
  Qwen3-8B ausdrücklich genannt, bei 4B nur vermutet) selbst übersetzt werden: braucht ein Qualcomm-Konto des
  Owners, lädt die Gewichte zum Übersetzen in Qualcomms Cloud und ist nicht ausprobiert. Reihenfolge: zuerst das
  fertige Qwen3-4B von Qualcomm auf dem Gerät messen (Tempo, Wärme), dann die abliterierte Fassung übersetzen.
  Zu prüfen: feste Kontextlänge der übersetzten Modelle, Qualitätsverlust durch die Quantisierung, Lizenz.
  **Endstand Modellwahl (Owner, 2026-11-20): das normale, von Qualcomm fertig übersetzte Qwen3-4B**, nicht
  abliteriert, kein eigener Export („kein Bock auf die Kopfschmerzen“). Die abliterierte Fassung ist damit vom
  Tisch, solange der Owner sie nicht wieder verlangt.
  **Machbarkeit (2026-11-20, `docs/W4-GENIEX-MACHBARKEIT.md`, aus Qualcomms Paket und Seiten gelesen, nichts
  auf einem Gerät gelaufen):** `com.qualcomm.qti:geniex-android:0.10.0` bringt die KI-Chip-Laufzeit (QAIRT 2.50)
  im Paket mit; das Modell ist ein öffentliches Zip (2,67 GB, entpackt etwa 3,2 GB, Kontext 4096, ohne Anmeldung).
  **Hauptrisiko:** Für den normalen Snapdragon 8 Elite des Xiaomi 15 (SM8750) ist kein Qwen3-4B veröffentlicht,
  nur für die Galaxy-Variante (SM8750-AC); ob dieses Paket auf dem Xiaomi läuft, zeigt nur ein Gerätetest.
  Zweites Risiko: Das Modell wurde mit QAIRT 2.51 gebaut, die Laufzeit ist 2.50 (Qualcomms Doku warnt vor
  falschen Ausgaben). Veröffentlichte Geschwindigkeit (Galaxy-Variante): 16 bis 21 Tokens pro Sekunde; zu
  Energie und Speicher nichts. Weitergabe einer APK mit der Laufzeit: aus den Texten nicht ablesbar, also keine
  Veröffentlichung.
  **Etappe 80 (gebaut; erster Lauf auf dem Gerät: SDK-Start ok, Download scheitert):** die kleine Begleit-App `companion/`
  („SecondHand Begleit-Spike“, `docs/ARCHITECTURE.md` → Begleit-App, `docs/BUILD.md`, `docs/ANLEITUNG.md`, `docs/VALIDATION.md` →
  80 und 80b). Sie lädt nach Bestätigung das Modell, lädt es nur auf der NPU (`qairt`), korrigiert einen festen Satz und schreibt
  einen längeren Absatz um und zeigt Antwort, Ladezeit, Zeit bis zum ersten Token, Tokens pro Sekunde (SDK und selbst gemessen),
  Speicher, Akku-Temperatur und jeden Fehler samt SDK-Code und -Text; „Bericht kopieren“ legt alles in die Zwischenablage. Kein
  Textfeld, keine Verbindung zur Tastatur, nie veröffentlicht, nur als Datei an den Owner. **Befund des ersten Laufs (Xiaomi 15):**
  der Pull mit `SM8750` scheitert mit `-100015` (Chip-Kennung unbekannt: bei Qualcomm gibt es nur `SM8750-AC`, also die Galaxy-Pakete;
  für beide Modelle auf dem Server bestätigt). **80b (0.2-spike):** geordnete Liste (1. Qwen3-4B-Instruct-2507, 2. Qwen3-4B, beide
  `SM8750-AC`), nach jedem Fehler Angebot des nächsten Pakets mit eigenem Dialog. Nächste Schritte, je erst nach dem Bericht des Owners:
  **80c/80d (0.3/0.4-spike):** Ursache des Ladefehlers auf dem Xiaomi gefunden: Das Paket der Ausgabe v0.67.0 hat ein Metadaten-Format (`model_files` als Liste), das das Android-SDK 0.10.0 nicht liest; der Chip wurde nie angesprochen. Die Ausgabe v0.66.0 hat das alte Format und ist mit QAIRT 2.50.0 gebaut (Version der Laufzeit); die App lädt sie jetzt selbst herunter (fortsetzbar, geprüft) und übernimmt sie über LOCALFS; zusätzlich gibt es die Option „Kompatibilitäts-Metadaten“ ohne Download. **80e (0.5-spike):** Download und Import der Ausgabe v0.66.0 klappten, das Laden des 4B-Pakets endete im Speichermangel von Android (App und Launcher beendet). Die App prüft jetzt vor dem Laden den freien Speicher (Regel: das Doppelte der Rechenprogramme plus 512 MB), misst beim Laden mit und beendet sich bei Gefahr selbst; zuerst kommt das kleine Qwen3-1.7B (1,5 GB), dann 0,6B und die beiden 4B. **80f (0.6-spike):** HyperOS beendet Apps ab 1,5 GB dma-buf; das 1,7B-Paket erreichte 3,8 GB (2,3-Fache der Rechenprogramme), schon seine Programme allein (1,62 GiB) liegen darüber. 0.6 misst den Verlauf mit Notbremse bei 1350 MiB, hält Berichte als Datei und sperrt das Laden; vorgeschlagen für 0.7: Qwen3-1.7B als GGUF Q4_0 über `llama_cpp` auf der NPU (etwa 1 GB, Vermutung 0,8 bis 1,0 GiB dma-buf). (a) Läuft ein Galaxy-Paket auf dem Xiaomi? Sonst Ursache klären (Chip, QAIRT 2.51 gegen 2.50, `enableThinking`); wenn keines läuft,
  ist der eigene Export mit Qualcomms Werkzeug (Konto nötig) die Alternative. (b) Tempo, Speicher und Wärme bewerten (Qualcomm nennt
  16 bis 21 Tokens/s für die Galaxy-Variante). (c) Erst dann die lokale Schnittstelle zwischen Tastatur und Begleit-App (Aufruf nur auf
  Tipp, Übernahme mit eigenem Klick, nichts loggen) und eine eigene Etappe für die Dienstform.
  **Geparkt (Owner, 2026-11-20):** W4 ruht nach fünf Gerätetests auf dem Xiaomi 15 (Begleit-App 0.1 bis 0.5;
  0.6 als Messwerkzeug gebaut, vom Owner nicht mehr gefahren). Stand: GenieX startet; die Android-Fassung 0.10.0
  liest nur das ältere Paketformat (Ausgabe v0.66.0); HyperOS beendet die App hart bei mehr als 1,5 GiB dma-buf,
  und schon Qwen3-1.7B (1,62 GiB Rechenprogramme) liegt rechnerisch darüber (gemessen 3,97 GiB). Kein Schalter in
  0.10.0 senkt das. Ungeprüfter Restweg: GGUF Q4_0 über `llama_cpp` mit teilweiser NPU-Nutzung. Einzelheiten:
  `docs/VALIDATION.md` → Etappe 80 bis 80f. Wieder aufnehmen, wenn Qualcomm das Android-Paket aktualisiert oder
  der Owner es verlangt.
- **W6 Spezialisierte Sprachengine statt allgemeinem LLM (Idee des Owners, 2026-11-20, geparkt).** Kleine,
  auf Tippen zugeschnittene Modelle statt eines LLM: Maßstab „Genauigkeit pro Millisekunde, Megabyte und
  Millijoule“, offline, CPU. Einschätzung des Leads: Schicht 1 (Wörterbuch, Wortfolgen, räumliches Tippmodell)
  existiert schon als HeliBoard-Engine; ein neuronales Modell (5–30 Mio. Parameter) sollte die Kandidaten der
  Engine nur neu sortieren, nicht selbst Wörter erzeugen; Satzkorrektur nur auf Wunsch und nur als Vorschlag;
  zuerst Deutsch. Offen und teuer: Trainingsdaten, Rechenzeit (hier keine Grafikkarte), Lizenzfragen bei
  Distillation, Messung falscher Korrekturen. Erster Schritt, wenn es wieder aufgenommen wird: ein Messstand für
  die heutige Engine (Trefferquote, gesparte Tastendrücke, falsche Korrekturen), ohne Modell.
- **W5 Sprache auf dem Gerät (Wunsch des Owners, 2026-11-20, noch nicht zugeschnitten).** Präzise
  Spracherfassung auch bei viel Hintergrundlärm, später eventuell Melodie-Erkennung. Das Textmodell aus W4 kann
  kein Audio; es braucht eigene Modelle in derselben Begleit-App. Deutung des Leads, nichts geprüft: (a) Diktat
  mit einem Whisper-Modell auf dem KI-Chip statt des Geräte-Erkenners, optional mit vorgeschalteter
  Rauschunterdrückung; (b) „Melodie-Erkennung“ ist zweierlei: Gesummtes oder Gespieltes in Noten umsetzen
  (Tonhöhen-Erkennung, lokal machbar) oder ein Lied wiedererkennen (braucht eine Lieddatenbank, also einen
  Online-Dienst). Antwort des Owners (2026-11-20): nicht für Gitarre gedacht; die Melodie-Erkennung darf auch
  über die KI-Schnittstelle laufen (Deutung des Leads: als Cloud-Aufruf mit dem eigenen Schlüssel und Freigabe,
  nicht auf dem Gerät). Der Owner will W5 („Fan der Idee“); Kern ist das Diktat mit Whisper auf dem KI-Chip.
  Machbarkeit (2026-11-20): GenieX kann kein Audio. Whisper für den KI-Chip gibt es bei Qualcomm nur für die
  Galaxy-Variante des 8 Elite und über einen anderen Unterbau (ONNX Runtime mit QNN); das ist ein eigenes Projekt
  nach W4. Regeln bleiben: Mikrofon nur auf
  Tipp, nichts wird gespeichert oder geloggt. (L)
- **W3 Alle Google-Funktionen.** Wortlaut des Owners: „alle Google features“. Deutung des Leads: der
  Funktionsumfang der Google-Tastatur und der KI-Funktionen von Google, soweit eine fremde Tastatur sie nutzen
  oder nachbauen kann. Erster Schritt ist eine Liste: je Funktion „schon vorhanden“, „nachbaubar (lokal oder
  mit eigenem Schlüssel)“, „nur über Google-Dienste“ oder „für fremde Apps nicht zugänglich“. Schon vorhanden
  sind u. a. Emoji-Mix, Zwischenablage, Diktat über den Geräte-Erkenner, Textkorrektur per KI. Externe
  Google-Dienste (Gmail, Kalender) stehen bereits als 16c und sind blockiert, bis der Owner eigene
  OAuth-Zugänge hat. Offene Frage: Welche Funktionen vermisst der Owner zuerst? (L, Liste zuerst: S)
  Die Liste liegt seit 2026-11-19 in `docs/GOOGLE-FUNKTIONEN.md` (Bestandsaufnahme mit Empfehlung und fünf
  offenen Fragen; viele Angaben zu ML Kit und Gboard nur aus Suchauszügen, dort als „nicht verifiziert“
  markiert). Empfohlene Reihenfolge ohne Google-Dienste: Autofill-Chips robust machen, feldbezogene Vorschläge
  und Übergänge, Textbearbeitung/Einhandmodus, Antwortvorschläge mit eigenem Schlüssel. Alles mit ML Kit oder
  AICore wartet auf die Entscheidung des Owners zu Google Play services.

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
