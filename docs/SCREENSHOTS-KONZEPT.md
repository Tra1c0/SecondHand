# Screenshots in der Zwischenablage: Konzept (Etappe 102)

Geschrieben vor dem Code, danach gepflegt. Code-Landkarte: `docs/ARCHITECTURE.md` → „Screenshot-Ablage (102)“,
Speicherformat: `docs/STORAGE.md` → „Screenshot-Ablage (102)“, Prüfnachweise: `docs/VALIDATION.md` → „Etappe 102“.

## 1. Was der Owner will, und was dem widerspricht

**Auftrag (2026-11-26, wörtlich):** „Alle von Android bereitgestellten Screenshots lückenlos anzeigen. Ab drei automatisch in
einem Ordner bündeln, der clean, flott und reaktiv aufploppt. Ordner: designkonformer Zurück-Button oben links, umbenennbar und
pinbar. Android-artiges Drag & Drop: innerhalb sortieren, in normale Ablage herausziehen, zwischen Ordnern verschieben oder im
vorgesehenen Ablagebereich ablegen: Neuer Ordner entsteht organisch animiert mit Haptik und enthält den Screenshot. Gestenabbruch
ändert nichts. Screenshots einzeln pinbar. Gepinnte Ordner schützen ihren Inhalt; Ungepinntes gemäß Aufbewahrungslimit löschen;
gepinnte Screenshots auch in ungepinnten Ordnern erhalten. […] Keine Duplikate, ungewollten Datenverluste, Layoutsprünge oder
blockierenden Animationen.“ Früher (ROADMAP 102): „Die Zwischenablage speichert alle Screenshots und bündelt ab 3 in einem Ordner,
übersichtlich.“

**Ziel im Alltag:** Wer einen Screenshot macht, findet ihn in der Zwischenablage der Tastatur, egal ob die Tastatur dabei lief.
Viele Screenshots machen die Zwischenablage nicht unübersichtlich, sie liegen in Ordnern, die man wie auf dem Startbildschirm
ordnet.

**Widerspricht dem (Antiprompt):**
- Ein Screenshot fehlt, weil die Tastatur gerade nicht lief, weil er älter als eine Minute war, als die Tastatur ihn sah, oder weil
  er in `DCIM/Screenshots` (Xiaomi) liegt. (Heute so: nur Screenshots, die der laufende Beobachter binnen 60 s meldet.)
- Derselbe Screenshot zweimal (als alte Bildkopie und als neuer Eintrag, oder nach einem Neustart erneut).
- Ein Ziehen, das abgebrochen wird oder an derselben Stelle endet, ändert trotzdem etwas.
- Die Tastatur löscht Bilder aus der Galerie. „Löschen gemäß Aufbewahrungslimit“ heißt nur: aus der Zwischenablage.
- Ordnerfenster, das langsam aufgeht, ruckelt oder während der Animation keine Tipps annimmt; Kacheln, die springen, während
  der Finger darauf liegt.
- Material-Look in fremden Farben statt der Tastenfarben des Owners (#090001, weiße Symbole, Akzent #DA3658).
- Die Werkzeugzeile der Zwischenablage taucht wieder auf (Etappe 106: Standard aus, „Ich brauche das alles nicht in der
  Zwischenablage“). Geprüft: `AgentPreferences.CLIPBOARD_TOOLBAR` steht auf aus und bleibt so. Smart-Paste-Chips gibt es nur an
  Textkarten, nie an Bildern oder Screenshots (`ClipboardAdapter` bindet sie nur bei `filename == null`); daran ändert 102 nichts.

## 2. Bestand (gelesen, nicht vermutet)

- `ScreenshotChip` (23d/23g): Schalter „Screenshots anbieten“ (`agent_screenshot_chip`, Standard aus) plus Bildberechtigung
  (`READ_MEDIA_IMAGES`, bis Android 12 `READ_EXTERNAL_STORAGE`). Ein `ContentObserver` auf `MediaStore.Images` läuft, solange
  der Tastaturdienst läuft; Abfrage nach `RELATIVE_PATH LIKE '%screenshot%'` (fängt `Pictures/Screenshots/` und
  `DCIM/Screenshots/`), nur die letzten drei Minuten, höchstens 10.
- Verlauf heute: `addToHistory` kopiert einen Screenshot als Bild-Clip in die HeliBoard-Datenbank (`files/clipboard/<sha256>.png`),
  nur wenn der Beobachter ihn höchstens 60 s nach der Aufnahme meldet, die Tastatur schon beobachtete und Gerät/Feld es erlaubten.
  Angelegte Einträge stehen als „ID:Zeit“ in `agent_screenshot_history_entries`.
- Aufbewahrung der Zwischenablage: `PREF_CLIPBOARD_HISTORY_RETENTION_TIME` in Minuten (Standard 10, Regler bis 120, über 120 =
  unbegrenzt), angewandt in `ClipboardDao.clearOldClips`, nie während die Ansicht offen ist; angeheftete Clips bleiben. Dazu
  `PREF_CLIPBOARD_FILES_SIZE_LIMIT` (20 MB) nur für Dateikopien. „Langdruck“ auf eine Karte heftet an/ab, Wischen nach links
  löscht eine Karte.
- Ansicht: `ClipboardHistoryView` (LinearLayout: Tresor-Zeilen, Liste `ClipboardHistoryRecyclerView` mit zwei Spalten
  `StaggeredGridLayoutManager`, untere Tastenreihe), `ClipboardAdapter` liest die Positionen der `ClipboardDao`.

## 3. Entscheidungen

**E1 Quelle ist der Medienspeicher, keine Kopien.** Die Ablage zeigt Verweise auf die Screenshots in `MediaStore` (stabile ID =
`_ID`), keine Bildkopien. Damit ist sie lückenlos (jede Abfrage liefert alle, auch die, die entstanden, als die Tastatur nicht
lief), ohne Duplikate (eine ID = ein Eintrag), braucht keinen Speicher, und ein in der Galerie gelöschtes Bild verschwindet
(nächste Abfrage). Eine Kopie entsteht nur beim Einfügen (wie bei der Pille, `copyForEditor`). Die alte Kopie in den Verlauf
(`addToHistory`) entfällt; ihre Tests werden mit dem Verhalten entfernt und durch Tests der Ablage ersetzt (bewusst geändert).
Folge: Wer ein angeheftetes Bild in der Galerie löscht, verliert es auch hier (Vorschlag in 8).

**E2 Welche Screenshots.** Bilder mit `screenshot` im Ordnerpfad (`RELATIVE_PATH`, vor Android 10 `DATA`, Groß-/Kleinschreibung
egal), nicht `IS_PENDING` (noch im Schreiben), nicht im Papierkorb (Android 11+ blendet ihn ohnehin aus). Gefragt wird ohne
Zeitgrenze nur nach `_ID`, `DATE_ADDED`, `MIME_TYPE` (klein, auch bei Tausenden), im Hintergrund. Zeit eines Screenshots =
`DATE_ADDED` (Sekunden).

**E3 Sichtbar ist** ein Screenshot, der existiert und (a) angeheftet ist, oder (b) in einem angehefteten Ordner liegt, oder (c)
innerhalb der Aufbewahrung liegt (`DATE_ADDED ≥ jetzt − Aufbewahrung`, unbegrenzt bei > 120 min) und nicht vor dem letzten
„Zwischenablage leeren“ entstand. Alles andere ist aus der Zwischenablage gelöscht: sein Platz (Ordner, Reihenfolge) wird
vergessen, das Bild bleibt in der Galerie. Wie bei den Clips wird beim Öffnen der Ansicht aufgeräumt, nie während sie offen ist
(nichts verschwindet unter dem Finger). Die 20-MB-Grenze gilt nicht (keine Kopien).

**E4 Bündeln ab drei.** Neue Screenshots sind zuerst „lose neu“ und stehen als Karten in der Liste. Sobald es drei lose neue gibt,
entsteht der Ordner „Screenshots“ (automatischer Ordner) mit allen dreien; jeder weitere neue Screenshot kommt vorn hinein. Der
Ordner bleibt ein Ordner, auch wenn man später Bilder herauszieht (er löst sich nicht unter dem Finger auf); leer verschwindet er,
danach beginnt das Zählen neu. Wird der automatische Ordner umbenannt oder angeheftet, gehört er dem Owner: Neue Screenshots
landen dann nicht mehr dort (sonst wüchse ein angehefteter Ordner unbegrenzt), sondern beginnen wieder lose bis drei.

**E5 Ordner und Plätze.** Ein Screenshot liegt in genau einem von: lose neu, lose (vom Owner in die normale Ablage gezogen, wird nie
automatisch gebündelt), in Ordner X. Leere Ordner verschwinden, außer angeheftete. Ordner mit einem Bild sind erlaubt (Ablagebereich).
Namen: „Screenshots“ (automatisch), „Ordner 1“, „Ordner 2“ … (kleinste freie Zahl), umbenennbar auf 1–40 Zeichen.

**E6 Wo es steht.** Oben in der Zwischenablage-Liste, vor den Text- und Bildclips, im selben Raster aus zwei Spalten: Ordnerkacheln
(2×2-Vorschau der neuesten Bilder, Name, Anzahl) und Screenshot-Karten, quadratisch, in den Tastenfarben (Kartenfarbe
`KEY_BACKGROUND`, Schrift `KEY_TEXT`, Hervorhebung beim Ziehen in der Akzentfarbe `ACTION_KEY_BACKGROUND`, Pin-Zeichen
`CLIPBOARD_PIN`). Reihenfolge: angeheftete zuerst, wenn „Angeheftete zuerst“ an ist (wie bei Clips), sonst nach Zeit; dann nach dem
neuesten Bild (Ordner) bzw. der Zeit (Karte), neueste zuerst. Innerhalb eines Ordners bestimmt der Owner die Reihenfolge; neue
kommen vorn hinein.

**E7 Ordner öffnen.** Tipp auf die Kachel: das Ordnerfenster liegt über der Liste (gleiche Fläche, kein neues Fenster), geht in
160 ms von der Kachel aus auf (Größe 0,9 → 1, Deckkraft), nimmt Tipps sofort an. Kopf: oben links runder Zurück-Knopf wie der
flache Kopf der Zwischenablage (`FlatClipboard`: Kreis in `KEY_BACKGROUND`, Pfeil in `KEY_TEXT`), Name (Tipp = umbenennen), Anzahl,
Pin-Knopf (angeheftet in `CLIPBOARD_PIN`). Darunter ein Raster aus drei Spalten. Zurück schließt (Animation zur Kachel, die Liste
nimmt sofort Tipps an). Beim nächsten Öffnen der Zwischenablage ist kein Ordner offen.

**E8 Umbenennen** ohne das Feld der App: Die Kopfzeile zeigt den Namen als eigenes Eingabefeld, darunter die kleinen eigenen
Tasten der Tastatur (`CharArtKeys`, wie in der Zeichen-Kunst); nichts geht in die App, nichts wird gelernt. „Fertig“ speichert
(getrimmt, leer = alter Name bleibt), Zurück bricht ab.

**E9 Ziehen (wie Startbildschirm, eigene Logik, kein Systemziehen; vgl. `docs/LEISTE-KONZEPT.md` Abschnitt 6).**
- Langdruck 350 ms auf eine Karte oder Kachel hebt sie an (Haptik nach den Tastatur-Einstellungen, Kachel leicht größer). Wird
  vorher mehr als die Berührungstoleranz bewegt, scrollt die Liste wie immer.
- Loslassen ohne Bewegung nach dem Anheben = anheften/abheften (wie der Langdruck auf Clips heute). Ein kurzer Tipp fügt ein
  (Karte) bzw. öffnet (Kachel).
- Ab der Toleranz zieht eine Kopie unter dem Finger mit; die Karte am Ursprung bleibt blass stehen (Platzhalter). Am oberen und
  unteren Rand scrollt die Liste mit.
- Ablageziele in der Liste: andere Karte (Akzentrahmen: „Ordner erstellen“, beide in neuen Ordner, das Ziel zuerst), Ordnerkachel
  (Akzentrahmen: hinein, vorn), Ablagebereich „Neuer Ordner“ (erscheint beim Ziehen als Leiste oben über der Liste, ohne die Liste zu
  verschieben; neuer Ordner mit nur diesem Bild: Kachel wächst mit leichtem Überschwingen, Haptik), sonst: kam das Bild aus einem
  Ordner, wird es lose (herausziehen); war es schon lose, ändert sich nichts.
- Im Ordnerfenster: Die anderen Bilder rücken beim Ziehen live zur Seite (Sortieren); Loslassen speichert die neue Reihenfolge. Die
  Kopfzeile wird zu zwei Ablagebereichen: „In die Ablage“ (360 ms darüber halten schließt den Ordner, das Ziehen geht in der Liste
  weiter, dort kann es auf einen anderen Ordner, eine Karte oder „Neuer Ordner“; direkt dort loslassen = lose) und „Neuer Ordner“.
- Abbruch ändert nichts: Systemabbruch (`ACTION_CANCEL`), Ausblenden der Tastatur oder der Zwischenablage, ein zweiter Finger,
  Loslassen auf dem eigenen Platz, auf dem eigenen Ordner oder außerhalb der Ansicht. Die Kopie fliegt dann zurück (150 ms).
- Während des Ziehens werden neue Abfrageergebnisse zurückgehalten und danach angewandt (keine Kachel springt unter dem Finger).
- Animationen blockieren nie: Das Modell ist sofort geändert, Kopien und Übergänge sind nicht berührbar, ein neuer Langdruck während
  einer Rückflug-Animation beendet diese sofort. Systemanimationen aus = keine Animation.

**E10 Privatsphäre.** Nur mit Zwischenablage-Verlauf, Schalter „Screenshots anbieten“ und Bildberechtigung (alles opt-in, wie
bisher). Keine Screenshot-Kacheln bei gesperrtem Gerät, im Inkognito-Modus und in Passwortfeldern (`ClipVault.isBlocked`, wie die
Tresor-Zeilen). Gespeichert werden nur IDs, Zeiten, Ordnernamen und Pins; nichts davon wird geloggt. Die frühere Regel „ein
Screenshot, der bei Sperre oder im Inkognito-Feld entstand, kommt nie in den Verlauf“ entfällt, weil nichts mehr kopiert wird und
„lückenlos“ sonst nicht erfüllbar ist (die Tastatur weiß bei einem Screenshot, den sie nicht beobachtet hat, nichts über das Feld):
Das Bild liegt ohnehin in der Galerie des Owners. Offene Owner-Frage, siehe 8.

**E11 Löschen.** „Zwischenablage leeren“ (Taste im Raster) setzt eine Marke: Ungeschützte Screenshots bis jetzt sind aus der
Zwischenablage weg (Galerie unberührt), angeheftete und solche in angehefteten Ordnern bleiben. Schalter „Screenshots anbieten“ aus
oder Verlauf aus: dasselbe, und die Ablage ist nicht zu sehen. Wischen löscht Clips wie bisher; Screenshot-Kacheln haben kein
Wischen (ein versehentliches Wischen soll keinen Ordner leeren).

**E12 Speicher und Neustart.** Datei `agent_screenshot_shelf.json` (`AtomicFile`) im Tastaturspeicher (DE, neben der
Zwischenablage-Datenbank), Format in `docs/STORAGE.md`. Geschrieben im Hintergrund nach jeder Änderung (immer der neueste Stand,
eine Schreibung nach der anderen), gelesen beim ersten Gebrauch. Eine unlesbare oder fremde Datei wird nicht überschrieben, solange
nichts geändert wurde, und beim Lesen beiseitegelegt (`.bad`), damit kein Ordner still verloren geht. Vergessen wird eine ID nur,
wenn eine erfolgreiche Abfrage mit mindestens einem Screenshot sie nicht mehr kennt (eine leere oder fehlgeschlagene Abfrage, etwa
ohne Berechtigung, löscht nichts).

**E13 Übernahme alter Daten (ohne Verlust).** Einmal pro Gerät: Alte Screenshot-Kopien im Verlauf (`agent_screenshot_history_entries`,
„ID:Zeit“), die unverändert und nicht angeheftet sind und deren Original im Medienspeicher mit genau dieser Zeit noch existiert,
werden entfernt (das Original erscheint in der Ablage; Kopie und Original verfallen nach derselben Aufbewahrung). Fehlt das
Original, bleibt die Kopie als normales Bild. Angeheftete alte Kopien bleiben angeheftete Bilder (nichts geht verloren; ein mögliches
Doppel wird im Bericht genannt). Die Merkliste wird danach geleert.

## 4. Unausgesprochenes, das dazugehört

- Einfügen wie bisher bei Bild-Clips (Kopie im eigenen FileProvider, Hintergrund, danach `onContent`); nicht im Passwortfeld.
- TalkBack: Karte „Screenshot, 14:32:05“ (+ „, angeheftet“), Kachel „Ordner Screenshots, 5 Screenshots“; Kopfknöpfe beschriftet.
- Vorschaubilder im Hintergrund (`loadThumbnail` ab Android 10, sonst verkleinert dekodiert), im Speicher gemerkt; bis dahin eine
  ruhige Fläche in der Kartenfarbe, kein Springen der Größe (Kacheln sind quadratisch, bevor das Bild da ist).
- Schnelles Antippen: Doppeltipp auf eine Kachel öffnet einmal; Zurück während des Aufgehens schließt sofort.

## 5. Abnahme-Zeilen

| # | Ausgangslage beim Owner | Aktion | Erwartung | Gegenfall | Nachweis |
| --- | --- | --- | --- | --- | --- |
| A1 | Verlauf und „Screenshots anbieten“ an, Berechtigung erteilt, dunkles Design | Zwei Screenshots, Zwischenablage öffnen | Zwei Karten oben, neueste zuerst | Fehlt einer, oder er steht doppelt | Unit `ScreenshotShelfTest`, Emulator `screenshot_shelf_bundles` |
| A2 | wie A1 | dritter Screenshot | ein Ordner „Screenshots · 3“ statt drei Karten | Zwei Karten bleiben neben dem Ordner | Unit, Emulator |
| A3 | Tastatur beendet (Prozess weg) | Screenshot machen, Feld antippen, Zwischenablage öffnen | Der Screenshot ist im Ordner | Fehlt, weil die Tastatur nicht lief | Emulator `screenshot_shelf_restart` (`am force-stop`) |
| A4 | Xiaomi-Pfad `DCIM/Screenshots` | Screenshot dort | erscheint wie die anderen | Nur `Pictures/Screenshots` zählt | Emulator (Bild in `DCIM/Screenshots`), Unit (Auswahl) |
| A5 | Ordner vorhanden | Tipp auf die Kachel | Ordner geht in < 200 ms auf, Zurück oben links | Langsam, Material-Look, kein Zurück | Emulator (Zeit Tipp→Ordner im Log), Robolectric |
| A6 | Ordner offen | Bild lange halten, an andere Stelle ziehen, loslassen | Reihenfolge geändert, nach Neustart gleich | Springt zurück, nach Neustart alt | Robolectric, Emulator (`input motionevent`), Unit |
| A7 | Ordner offen | Bild ziehen, über „In die Ablage“ halten, in der Liste loslassen | Bild ist lose Karte, Ordner eins weniger | Ordner löst sich auf, Bild weg | Unit, Robolectric, Emulator |
| A8 | Zwei Ordner | Bild aus A ziehen, über Kachel B loslassen | Bild vorn in B | In beiden, oder in keinem | Unit, Robolectric, Emulator |
| A9 | Liste | Bild ziehen, auf „Neuer Ordner“ loslassen | Neuer Ordner „Ordner 1“ mit genau diesem Bild, wächst, Haptik | Zwei Ordner, leerer Ordner | Unit, Robolectric, Emulator |
| A10 | Liste | Bild auf ein anderes loses Bild ziehen | neuer Ordner mit beiden | Ziel verschwindet | Unit, Emulator |
| A11 | beliebig | Ziehen, dann Abbruch / Finger zurück an den Start / außerhalb loslassen | nichts geändert | Ordner angelegt, Reihenfolge anders | Unit `ShelfDragTest`, Robolectric (ACTION_CANCEL), Emulator (zurück zum Start) |
| A12 | Ordner offen | Name tippen, umbenennen, Fertig | neuer Name, nach Neustart gleich; neue Screenshots nicht mehr dort, wenn es der automatische war | Text landet in der App | Robolectric, Unit, Emulator |
| A13 | Aufbewahrung 10 min | Bild anheften, 11 min später öffnen | Bild noch da, ungeheftete Nachbarn weg | Angeheftetes weg | Unit (Uhr) |
| A14 | Angehefteter Ordner mit alten Bildern | nach Ablauf öffnen | Ordner mit allen Bildern | Inhalt verfallen | Unit |
| A15 | Ungehefteter Ordner mit einem angehefteten Bild | nach Ablauf | Ordner mit nur diesem Bild | Ordner weg samt Bild | Unit |
| A16 | Bild in der Galerie gelöscht | Zwischenablage öffnen | Bild weg, kein leerer Platz | Leere Kachel, Absturz | Unit, Emulator (`content delete`) |
| A17 | Alte Screenshot-Kopien im Verlauf (vor 102) | erster Start mit 102 | jedes Bild genau einmal, angeheftete bleiben | doppelt oder verloren | Robolectric `ScreenshotShelfMigrationTest` |
| A18 | Inkognito / Passwortfeld / gesperrt | Zwischenablage öffnen | keine Screenshot-Kacheln | Kacheln sichtbar | Robolectric |
| A19 | 50 Screenshots | Zwischenablage öffnen, scrollen, ziehen | keine ruckelnden Bilder (Janky-Anteil nicht schlechter als vorher), Öffnen nicht langsamer | Öffnen dauert Sekunden | Emulator (`gfxinfo`, Zeiten vorher/nachher) |
| A20 | Schnelle Folge: öffnen, sofort zurück, sofort ziehen | – | jede Eingabe wirkt, kein Hänger | Tipps gehen in der Animation verloren | Robolectric (Animation läuft, Tipp wirkt), Emulator |

## 6. Prüfplan

- Reine Logik in `agent-core` (JVM, schnell): `ScreenshotShelf` (Modell und Regeln) und `ShelfDrag` (Zustandsmaschine der Berührung
  mit Uhr), Tests `ScreenshotShelfTest`, `ShelfDragTest`.
- Robolectric (`app/src/test`): Speicher und Übernahme (`ScreenshotShelfStoreTest`), Ansicht und Ziehen mit echten MotionEvents
  inkl. `ACTION_CANCEL` (`ScreenshotShelfViewTest`).
- Emulator (`tools/devicetest/devicetest.py`): Screenshots per `screencap` in `Pictures/Screenshots` und `DCIM/Screenshots` (der
  Medienspeicher nimmt sie über FUSE auf; sonst Scan-Broadcast), Zwischenablage über die Leiste, Ordner, Zurück, Ziehen mit
  `input motionevent`, Neustart mit `am force-stop`. Jedes Szenario scheitert im Kontrolllauf (es fehlt der letzte Screenshot bzw.
  das Ziel).
- Messung vorher (`main` 9228c5a0) und nachher: Öffnen der Zwischenablage (ms bis zum ersten Bild), neuer Screenshot bis sichtbar,
  Tipp→Ordner sichtbar (nur nachher), `dumpsys gfxinfo framestats` beim Öffnen, Scrollen und Ziehen.

## 7. Grenzen

- Kein Gerätetest auf einem Telefon; Xiaomi/HyperOS kann Screenshots anders ablegen (Pfad wird im Emulator nachgestellt).
- Barrierefreiheit: Ziehen geht nur mit dem Finger; Anheften und Öffnen gehen auch mit TalkBack.
- Die Ablage ist nicht Teil der Sicherung (ZIP) – wie die Zwischenablage-Datenbank selbst.

## 8. Offen für den Owner (nicht gebaut)

- Soll ein angeheftetes Bild eine eigene Kopie bekommen, damit es das Löschen in der Galerie überlebt?
- Soll ein Screenshot, der bei gesperrtem Gerät oder in einem Inkognito-Feld entstand, ausgeblendet werden (gilt dann nur, solange
  die Tastatur lief, also nicht lückenlos)?
- Wischen zum Ausblenden einzelner Screenshots?
