# Agent Keyboard – Roadmap (noch nicht gebaut)

Alles, was es noch nicht gibt. Der Owner sortiert. Sitzungen bauen nur, was unter „Beschlossen“
steht oder was der Owner direkt beauftragt, und nur, wenn die Entwicklung nicht pausiert ist
(`docs/HANDOFF.md` → Aktueller Stand). Ist ein Punkt gebaut, wird er hier gestrichen und steht
dann in [FEATURES.md](FEATURES.md). Aufwand: S klein, M mittel, L groß. Die Nummern sind die
Etappen aus dem ursprünglichen Auftrag; Teilpakete bekommen Buchstaben (15a, 15b …).

## Beschlossen, noch nicht gebaut

Reihenfolge von oben nach unten.

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
  sie bei 390 dp zu schmal für die gesperrte Schrift), wandernder Lichttropfen, bei 60 Hz doppelt
  so schnell; still ohne Android-Animationen, im Energiesparmodus und außer Sicht. Annahmen des
  Leads, vom Owner zu bestätigen: Großbuchstaben „30 HERTZ“ wie die Auswahlkarten von 38 (die
  Skizze schreibt „30 Hertz“), Zusatzzeile „30 Bilder/s · spart Akku“ bzw. „60 Bilder/s ·
  flüssiger“, der Tropfen wandert hin und her mit kurzer Leuchtspur. Offen: Gerätetest (Aussehen,
  Tempo des Tropfens, ob er stört). Weitere Auswahlen der RGB-Seite, die in diesen Stil passen
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
  fügt ihn ein). Offen: Prüfer-Review, Gerätetest (Tracking-Seiten der sechs Dienste am Gerät,
  Karten-, Telefon-, Kontakte-, Mail- und Kalender-App, TalkBack, Leiste mit langer Pille).
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
  „andere Sprache“ = jede aktive Sprache und ihre zweiten Sprachen; die Regel „fur“ (49) gilt, bis
  du „fur“ einmal behältst; Rückgängig gemachte Korrekturen, eigene Wörter, Wortfolgen und Kürzel
  bleiben sprachübergreifend. Offen: Prüfer-Review (Engine: `DictionaryFacilitatorImpl`, `Suggest`,
  Pflicht), Gerätetest (echte Wörterbücher, Speicher der zusätzlich geöffneten Wörterbücher,
  Sprachtaste und Wischen auf der Leertaste).
- **52 Einheitliches Befehlssystem (mit dem Owner festgelegt, 2026-11-14)** — beschlossen, nächste
  Runde, noch nicht anfangen. Ausgangspunkt waren die Beispiele des Owners (`@lang set &duolingo
  spanish`, `@lang list &&apps lang`, `@lang del &whatsapp lang ; set &whatsapp ger`); der Owner wollte
  ausdrücklich einen eigenen, global sinnvollen Vorschlag. Festgelegt:
  - Aufbau `@<bereich> [aktion] [angaben …]`. Keine Ziel-Markierungen (`&`, `&&`): jeder Befehl
    kennt seine Plätze und ihren Typ (App, Sprache, Text, Zahl, an/aus); die Tastatur ordnet die
    Wörter mit Toleranz zu (gleiches Toleranzfenster wie die Wörterbuch-Toleranz), Reihenfolge egal.
    Mehrdeutig → beide Deutungen als Chips; `app:` erzwingt eine App.
  - Englisch zuerst, Deutsch geht auch, beide mit derselben Toleranz. Aktionen: ohne Aktion =
    zeigen (ohne Wert) bzw. setzen (mit Wert, überschreibt); `list`/`?` (zeig), `del`/`-` (weg),
    `on`/`off` (an/aus), `add`/`+` (neu). Bereiche: `@lang` (sprache), `@snip` (baustein), `@clip`
    (ablage), `@guard` (wächter), `@mem` (gedächtnis), `@group` (gruppe); `@deutsch`, `@sp` … bleiben
    als Kurzform für `@lang <sprache>` im aktuellen Feld.
  - Gruppen: `all`/`apps` (alle), eigene mit `@group messenger whatsapp signal`.
  - Ketten: `;`, ebenso `dann`/`then` und `→`; der Bereich gilt weiter bis zum nächsten `@`.
  - Ausführen: Vorschau in Klartext in der Leiste, ein Tipp führt aus; Enter nur bei genau einer,
    harmlosen Deutung; Löschen und `all` nur per Tipp; danach einige Sekunden „Undo“. `@?` listet
    die Bereiche, `@lang ?` die Plätze; Vorschläge je Platz (Apps, Sprachen, Bausteine).
  - Eine per Befehl festgelegte App-Sprache gilt auch, wenn „Sprache pro App“ aus ist.
  - Farben wie im Code-Editor: Bereich, Aktion, Ziel/Gruppe, Wert, Trenner und Unbekanntes je eigene
    Farbe. Sicher machbar in der Vorschau der Leiste. Im Textfeld selbst nur, solange der Befehl
    als Kompositionstext mit Farb-Spans steht, und nur in Apps, die solche Spans anzeigen (normale
    Textfelder ja, manche Web- und eigene Editoren nicht) — „so gut die App es zulässt“; das ist eine
    Engine-Änderung (Komposition über Leerzeichen hinweg), Prüfer Pflicht, vorher eng abgrenzen.
  - Grenzen wie bisher: Befehle ändern nur eigene Einstellungen und Daten, nie Werkzeuge, URLs,
    Provider oder Berechtigungen; nicht in Passwortfeldern, Inkognito, gesperrt.
  - Beispiele: `@lang duolingo spanish`, `@lang apps`, `@lang whatsapp german` (ersetzt
    `del …; set …`), `@lang whatsapp german; duolingo spanish; @guard banking off`.
- **53 Zeichen-Kunst-Fenster** ist gebaut (siehe FEATURES.md, Nachweis in `docs/VALIDATION.md` →
  Etappe 53). Annahmen des Leads, die der Owner ändern kann: Standardstil Braille; Profile WhatsApp,
  Telegram, Discord, Signal, Andere (Breiten 26/28/32/26/26, Codeblock bei WhatsApp und Discord an,
  bei Telegram wählbar); Text im Fenster über eigene Fenster-Tasten statt der Tastatur-Tasten (die
  liegen unter dem Fenster; umleiten hätte die Engine berührt); Fotovorlage aus den neuesten 12
  Bildern im Fenster statt System-Picker (der würde die Tastatur schließen); nach dem Einfügen
  bleibt das Fenster offen und „Einfügen“ ist 8 s „Rückgängig“; „Ersetzen“ nur, wenn hinter der
  Schreibmarke nichts steht. Offen: Prüfer-Review (Hooks in `TextKeyData`,
  `KeyboardActionListenerImpl`, `InputView`; Aufruf von `InputLogic.commitTyped` und
  `replaceCommandText`), Gerätetest (Langdruck, Fensterhöhe, Zeichnen mit dem Finger, Bilder mit
  echter Berechtigung, wie die Kunst in WhatsApp/Telegram/Discord/Signal wirklich aussieht).
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
- **54 Update-Fenster mit „Was ist neu“ (Wunsch des Owners vom 2026-11-14)** — beschlossen, diese
  Runde. Owner: „Neue Updates werden sofort in der App angezeigt, zusammen mit einem kurzen Dialog,
  was neu ist.“ Vorschlag des Leads: (1) Beim Öffnen der Einstellungen erscheint, wenn die letzte
  Prüfung einen neueren Build fand, ein Dialog „Build N ist da“ mit den Release-Notizen dieses
  Builds (kurz, aufklappbar) und „Installieren“ / „Später“; „Später“ blendet ihn für diesen Build
  aus. (2) Nach dem Installieren eines neuen Builds einmal „Neu in Build N“ mit den Notizen aus der
  APK (offline, aus `release-notes/next.md` beim Bauen eingebettet). (3) Optional einmal ein
  dezenter Chip in der Leiste „Build N verfügbar“ (nicht in Passwort-/Inkognito-Feldern, nicht
  gesperrt). Prüfung weiter nur mit dem bestehenden Schalter „täglich automatisch“ bzw. auf Tipp.
  Voraussetzung: Die Prüfung liest das Release-Repository `Tra1c0/AgenticKeyboard`; dort landen
  Builds nur mit dem Secret `RELEASES_TOKEN`, das fehlt (Workflow-Warnung). Alternative (Owner
  entscheidet): direkt die Releases dieses Repositorys lesen (öffentlich, kein Secret nötig, dann
  auch Test-Builds; Kanal „nur geprüfte“ / „auch Test-Builds“ wählbar). (M)
- **47 KI-Kontext im Hintergrund — verworfen (Owner, 2026-11-14):** „Dann lassen wir das.“ Alles
  lokal; die Tastatur lernt selbst dazu (45, 46).
- **Verworfen (Owner, 2026-11-14):** Mail-Anbindung (Gmail/IMAP) und Codes aus Benachrichtigungen.
  Einmalcodes kommen nur über die Zwischenablage (44).
- **34 Eigene RGB-Lichtmuster gestalten — bestätigt (Owner, 2026-11-14): „eigene rgb lichtmuster. Vollständig customizable.“ Owner: „wahrscheinlich eher XL“. (XL), diese Runde.** (Wunsch des Owners vom 2026-11-13). Eigene
  Tastatur-Designs gibt es schon (Design-Seite → „Meine Designs“, seit Build 10). Annahme des
  Leads, vor dem Bau bestätigen: gemeint sind eigene RGB-Designs, also selbst gestaltete
  Lichtmuster (Farben, Formen, Bewegung) auf Basis der neuen Muster und der Kurve aus 29. (L)
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
