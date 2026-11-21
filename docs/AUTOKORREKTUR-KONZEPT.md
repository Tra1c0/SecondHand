# Autokorrektur: Konzept (Etappe 90b)

Entwurf vor dem Bau, noch nichts davon gebaut (Stand: Teil 90a, die Diagnose-Karte „Korrektur & Sprache“, ist gebaut).
Anlass sind vier Befunde des Owners (2026-11-21); die Untersuchung steht in `docs/VALIDATION.md` und in den Tests
`AutocorrectReportTest.kt` und `reported_finding_*` (Emulator). Arbeitsweise: `.claude/skills/product-initiative/SKILL.md`.

## 1. Was der Owner will, und was dem widerspricht

**Will:** Die Tastatur ändert nichts Richtiges (Stufe BEHUTSAM) und trotzdem wird „i“ im englischen Satz zu „I“. Ein
Wort, das ein Wörterbuch nicht kennt, wird nie in ein Wort verwandelt, das es nirgends gibt („war's“ → „war'a“,
„nichtmal“ → „nicbmal“).

**Widerspricht dem:**
- BEHUTSAM lockern, sodass echte Wörter geändert werden („bot“ → „not“, „nut“ → „but“, „ill“ → „I'll“, „id“ → „I'd“, „lets“ → „let's“).
- Gelerntes still ausschalten oder löschen: Was der Owner sich beigebracht hat, bleibt.
- Ein deutscher Satz („im Haus“, „wer kommt“) ändert sich, weil Englisch als zweite Sprache aktiv ist.
- Etwas, das nur mit Standardwerten funktioniert: Der Owner hat sehr wahrscheinlich Stufe BEHUTSAM und „Personalisierte
  Vorschläge“ an (Annahme, die Diagnose-Karte aus 90a beantwortet es).

## 2. Befunde, Ursachen (belegt)

| Befund | Ursache | Beleg |
| --- | --- | --- |
| „i“ bleibt klein | BEHUTSAM = „Richtige Wörter nie ändern“; das englische Wörterbuch führt „i“ als Wort (Häufigkeit 0) mit Whitelist „I“, `CorrectionGuard.allowsAutoCorrection` (`CorrectionMemory.kt:45-46`) sperrt, die Whitelist-Ausnahme von Stufe 48 sitzt nur in `Suggest.guardAllows` (`Suggest.kt:418-421`) | Emulator `reported_finding_3_level_gentle`, Test „reported finding 3“ |
| „war's“ → „war'a“, „nicbmal“ | Der Tipp-Verlauf lernt jedes abgeschickte Wort, auch einen behaltenen Tippfehler und das Ergebnis einer Autokorrektur (`DictionaryFacilitatorImpl.addToUserHistory`, `InputLogic.commitCurrentAutoCorrection`, Zeile 2744); die Rücktaste vergisst nichts (`unlearnFromUserHistory`: BACKSPACE lässt das Wort stehen). Das Korrekturziel wird nicht gegen ein Wörterbuch geprüft | Emulator `reported_finding_2_4_history_loop`, Test „reported finding 4“ |
| Englisch wird zu Deutsch | Je nach Einrichtung: deutsches Layout ohne zweite Sprache (kein Fehler, nur deutsches Wörterbuch), oder Mehrsprachig mit Grenzfällen von Stufe 60 | Emulator `reported_finding_1_*`, Test „reported finding 1“ |

## 3. Behebung 1: „i“ bleibt klein auf BEHUTSAM

### 3.1 Der Plan angegriffen: Weg 1 (alle Whitelist-Einträge bei „Richtige Wörter nie ändern“ zulassen)

Das englische Wörterbuch hat 99 Whitelist-Einträge (en-US und en-GB gleich; gelesen aus `main_en-US.dict`, 160.715 Wörter,
Skript im Untersuchungszweig, nicht im Repository). Sie zerfallen in drei Gruppen:

| Gruppe | Anzahl | Inhalt |
| --- | --- | --- |
| A: nur Groß-/Kleinschreibung oder Apostroph, getippte Form im Wörterbuch Häufigkeit 0 (kein echtes Wort) | 25 | i→I, im→I'm, aint, arent, hed, heres, howd, howll, howve, itd, maam, thatd, whatd, whatm, whatre, whered, wherell, wheres, wholl, whove, whyd, whyll, whyve, yall, youd |
| B: nur Groß-/Kleinschreibung oder Apostroph, getippte Form ist ein echtes englisches Wort (Häufigkeit > 0) | 9 | cant→can't (70), hows (35), id→I'd (99), ill→I'll (112), lets→let's (106), whats (53), whens (23), whys (39), wont→won't (50) |
| C: andere Rechtschreibung („Tippfehler-Korrekturen“) | 65 | davon 43 mit echtem englischem Wort als getippter Form (bot→not, nut→but, hum→him, oft→off, sill→will, lust→list, dame→same, mare→made, hid→his …), 23 kollidieren mit einem gültigen DEUTSCHEN Wort (bern, bot, bur, cam, dame, fay, fife, foo, fora, hel, hum, il, lust, manu, mare, nut, oft, pater, sill, thong, tine, un, wer) |

Kollisionen mit gültigen deutschen Wörtern gesamt: 24 Einträge, nämlich die 23 aus Gruppe C und „im“ (Gruppe A, deutsch Häufigkeit 198). Die in der
Anfrage genannten „wed“, „hell“, „well“, „were“ stehen **nicht** in der Whitelist (hell, well, were sind keine Einträge;
„wed“ auch nicht); echte Kollisionen sind „im“, „wer“, „un“, „il“, „nut“, „oft“, „bot“ und so weiter.

**Weg 1 verletzt BEHUTSAM** an 52 Stellen (Gruppe B und die 43 echten Wörter aus C): „bot“, „nut“, „hum“, „ill“, „id“, „lets“, „wont“ sind richtige englische Wörter, die der Owner
auf BEHUTSAM gerade nicht geändert haben will. Heute schützt BEHUTSAM sie; mit Weg 1 würden sie wieder geändert. Weg 1 ist zu weit.

**Heute und nach der Änderung, wie gefragt:**
- Deutsches Layout, Englisch als zweite Sprache, Satz „im Haus“: Die Engine setzt die englische Whitelist („I'm“) nur durch, wenn die Hauptsprache
  des Wörterbuchs englisch ist (`Suggest.shouldBeAutoCorrected`, Zweig „dict locale different“, `Suggest.kt:540-545`, aus dem Code gelesen, mit
  einem Gerät nicht geprüft); „im“ ist deutsches Hauptwort mit Häufigkeit 198. Die enge Fassung unten fragt nur, wenn getippte Form und Korrektur aus **derselben** Sprache stammen (`typedIsDictionaryWord`, `Suggest.kt:403-404`):
  Hier ändert sich also nichts, weder heute noch danach.
- Englisches Layout, „cant“, „id“, „ill“ (Gruppe B): heute auf BEHUTSAM unverändert (Emulator: „cant“ bleibt). Mit der engen Fassung bleibt es so (Häufigkeit > 0). Mit Weg 1 würden alle drei zu „can't“, „I'd“, „I'll“.
- „were“ steht nicht in der Whitelist, wird nicht berührt.

### 3.2 Engere Fassung (Empfehlung)

In `Suggest.guardAllows` gilt die Sperre „Richtige Wörter nie ändern“ **nicht**, wenn alle drei Bedingungen stimmen:
1. die Korrektur ist ein Whitelist-Eintrag (`KIND_WHITELIST`),
2. sie unterscheidet sich von der getippten Form nur in Groß-/Kleinschreibung und Apostrophen,
3. die getippte Form hat im Hauptwörterbuch der Sprache der Korrektur die Häufigkeit 0 (`DictionaryFacilitatorImpl.mainFrequency(typed, language)` gibt es schon, Stufe 75).

Das deckt genau Gruppe A ab: 25 Einträge, darunter „i“ und die englischen Kurzformen ohne Apostroph (aint, arent, itd, wheres …) und „im“ für das englische Layout.
Es lässt Gruppe B und C unberührt: „cant“, „id“, „ill“, „lets“, „wont“, „whats“ bleiben auf BEHUTSAM stehen (das sind richtige Wörter; der Vorschlag steht in der Leiste). Unberührt bleibt auch das Verhalten ohne BEHUTSAM.

Frage an den Owner (offen): Reicht es ihm, dass „cant“, „ill“, „id“, „lets“, „wont“ auf BEHUTSAM nicht zu „can't“ usw. werden? Das sind echte Wörter, aber im Chat meist als Tippfehler gemeint. Das ist seine Entscheidung (Option: ein
eigener Schalter), nicht unsere.

### 3.3 Eingriff

`app/src/main/java/helium314/keyboard/latin/Suggest.kt` (`guardAllows`, vor dem Aufruf von `CorrectionGuard.allowsAutoCorrection`) und eine kleine reine Funktion in `agent/CorrectionMemory.kt`
(`CorrectionGuard.isCaseOrApostropheOnly`). **InputLogic, RichInputConnection und PointerTracker bleiben unberührt** (bestätigt: die Entscheidung fällt vollständig im Vorschlags-Thread, `Suggest`).

### 3.4 Abnahme-Zeilen

| Ausgangslage | Aktion | Erwartung | Gegenfall | Nachweis |
| --- | --- | --- | --- | --- |
| Englisches Layout, Stufe BEHUTSAM, Owner-Design | „and i“ + Leertaste | „And I“ | „and bot“ bleibt „And bot“ (echtes Wort, BEHUTSAM) | Emulator `reported_finding_3_level_gentle` auf „erwartet“ umstellen |
| dasselbe | „im“, „i'll“ | „I'm“, „I'll“ | „cant“, „id“, „ill“, „lets“ bleiben stehen | Emulator, dieselbe Szene |
| Deutsches Layout + Englisch zweite Sprache (nur Robolectric mit festen Ergebnissen), BEHUTSAM | „im Haus“, „wer kommt“, „oft“ | unverändert | Whitelist „I'm“ aus englischer Gruppe ändert deutsches „im“ nicht | Robolectric `SuggestTest`-Stil, feste Engine-Ergebnisse mit en-Whitelist-Eintrag und de-Treffer |
| Deutsches Layout, kein Englisch | „i“ | bleibt „i“ | – (kein englisches Wörterbuch) | Emulator `reported_finding_3_capital_i` (unverändert) |
| Standardeinstellungen (kein BEHUTSAM) | alle bisherigen englischen Fälle | exakt wie heute | `bot`→`not` bleibt wie heute | Emulator `reported_finding_2_apostrophe_en` bleibt grün |

## 4. Behebung 2: Nicht-Wort aus dem Verlauf als Korrekturziel

### 4.1 Regel (Empfehlung)

Ein Korrekturziel, dessen Quelle `TYPE_USER_HISTORY` ist, wird nur dann **automatisch** angewendet, wenn
(a) ein Hauptwörterbuch irgendeiner aktiven Sprache das Wort kennt (wie geschrieben oder klein, `mainLocalesKnowing`, schon da, Stufe 60), oder
(b) es ein eigenes Wort des Gedächtnisses ist (`Memory.isOwnWord`, nur mit Gedächtnis an).
Sonst bleibt es **Vorschlag** in der Leiste (wie heute alle verhinderten Korrekturen). Das persönliche Wörterbuch (`TYPE_USER`), Kontakte und Apps bleiben unverändert Ziel:
das sind bewusste Eingaben des Owners bzw. des Telefons.

### 4.2 Angriff auf die Regel

- **Was verliert der Owner?** Wörter, die nur der Verlauf kennt (Namen, Slang, Dialekt, Fachwörter, die er getippt hat) werden nicht mehr automatisch Ziel einer Korrektur von
  einem Tippfehler aus („Larsn“ → „Larsen“). Sie bleiben in der Leiste vorgeschlagen und als Vervollständigung. Das Ziel „bewusst beibringen“ gibt es schon: „Meine Tippfehler lernen“ (20b: nach dreimal gewählt automatisch, auch zu Wörtern, die kein Wörterbuch kennt) und das persönliche Wörterbuch.
- **Bleibt ein gelerntes Wort beim Tippen selbst unangetastet?** Ja. Die Regel betrifft das *Ziel* einer Korrektur, nicht das getippte Wort. Ein getipptes Wort, das im Verlauf steht, gilt für die Engine als bekannt
  (Emulator: „nicbmal“ und „war'a“ getippt bleiben stehen).
- **Reicht „ein Hauptwörterbuch kennt es“?** Für Tippfehler: ja. Die Alternative „der Owner hat es mehrfach selbst getippt und nie korrigiert“ lässt sich aus dem Verlauf **nicht** ablesen: Er speichert nur Wort und
  Häufigkeit/Zeit, nicht ob getippt, korrigiert oder von einer Autokorrektur erzeugt (auch das Ergebnis einer Autokorrektur wird gelernt, die Rücktaste vergisst nichts). Ein eigener Zähler wäre ein Speicher in `InputLogic` (Hook beim Commit)
  und ist damit der falsche Preis; das Gedächtnis (46) leistet es schon bewusst und opt-in (dreimal so abgeschickt = eigenes Wort). Darum (a) oder (b), mehr nicht.
- **Was passiert mit schon gelernten „war'a“ und „nicbmal“?** Sie bleiben im Verlauf (nichts wird gelöscht, nichts migriert), werden aber nur noch Vorschlag, nie mehr Ziel einer automatischen Korrektur. Sie verschwinden aus der Leiste erst mit Weg 3 oder „Gelernte Wörter löschen“.
- **Nebenwirkung:** Eine Korrektur, die heute automatisch geschieht und danach nur noch Vorschlag ist, fällt dem Owner auf („das macht er nicht mehr“). Das ist die gewollte Richtung.

### 4.3 Weg 3 (Ergänzung): gelernte Wörter ansehen und einzeln löschen

Einstellungen → Tippen & Korrektur → Wörter & Lernen → Gelernte Wörter: Liste je Sprache (die Daten liefert `LearningSource.words` schon für den Export), pro Eintrag „Löschen“. Dafür fehlt `LearningSource.deleteWord`
(über `ExpandableBinaryDictionary.removeUnigramEntryDynamically`, wie `unlearnFromUserHistory` es für REJECTION tut). Aufwand: etwa ein Arbeitstag (Seite, Löschen mit Bestätigung, Robolectric-Test, kein InputLogic-Eingriff). Es zeigt Wörter, also nur in den Einstellungen,
nie in der Diagnose-Karte. Empfehlung: nach Behebung 2 als eigene Etappe, nicht im selben Schritt.

### 4.4 Eingriff

`Suggest.kt` (`guardAllows`; die Quelle des ersten Vorschlags ist dort bekannt: `first.mSourceDict.mDictType`), `facilitator.mainLocalesKnowing` (vorhanden, nicht geändert), `Memory.isOwnWord` (vorhanden).
**InputLogic und RichInputConnection bleiben unberührt** (bestätigt: kein Aufruf aus dem Eingabepfad geändert; der Lernpfad `addToUserHistory` bleibt, wie er ist, weil das Lernen bewusst opt-in und HeliBoards Verhalten ist).

### 4.5 Abnahme-Zeilen

| Ausgangslage | Aktion | Erwartung | Gegenfall | Nachweis |
| --- | --- | --- | --- | --- |
| Deutsch, „Personalisierte Vorschläge“ an, „war'a“ zweimal behalten | „war's“ tippen, Leertaste | bleibt „war's“; „war'a“ nur in der Leiste | „Larsn“ → Kontaktname oder persönliches Wörterbuch wird weiter korrigiert | Emulator `reported_finding_2_4_history_loop` auf „erwartet“ umstellen; Robolectric (feste Engine-Ergebnisse, `TYPE_USER_HISTORY`/`TYPE_USER`) |
| dasselbe mit „nicbmal“ | „nichtmal“ | „nicht mal“ oder bleibt; nie „nicbmal“ | – | Robolectric: History-Treffer mit Nicht-Wort wird nicht `mWillAutoCorrect` |
| Verlauf kennt „Larsen“ (kein Wörterbuch), Gedächtnis aus | „Larsn“ + Leertaste | **bleibt „Larsn“**, „Larsen“ in der Leiste | mit Gedächtnis an und „Larsen“ als eigenes Wort: wird korrigiert | Robolectric |
| Verlauf kennt „gehe“ (steht auch im Hauptwörterbuch) | „gehen“-Tippfehler | wie heute | – | Robolectric (Kontrollfall) |
| Deutsche Sätze, Standardeinstellungen | `TypingEquivalenceTest`, `SuggestTest` ganz, `InputLogicTest` „stage 20a/20b/48/49/60/71“ | unverändert grün | – | vorhandenes Netz, keine Änderung an diesen Tests |
| Der Owner tippt ein im Verlauf bekanntes Wort selbst | „nicbmal“ tippen | bleibt stehen | – | Emulator (wie heute) |

## 5. Prüfplan

1. Zuerst die `reported_finding_*`-Szenarien im Emulator von „heutiges Verhalten“ auf „erwartetes Verhalten“ umstellen (rot vor dem Bau, grün danach): `reported_finding_3_level_gentle` („i“ → „I“), `reported_finding_2_4_history_loop` („war's“ bleibt).
2. Robolectric `AutocorrectReportTest`: die Tests „reported finding 3/4“ drehen ihre Erwartung; neue Kontrollfälle für Gruppe B und C und für `TYPE_USER`.
3. Netz: `SuggestTest`, `InputLogicTest`, `TypingEquivalenceTest`, `CorrectionMemoryTest`, `DiagnoseScreenTest` vollständig; die fünf geerbten Fehler bleiben das einzige Rot (`docs/HANDOFF.md`).
4. Was nur der Owner am Telefon prüft: „i“ im englischen Chat auf BEHUTSAM; „war's“ nach seinem Verlauf; ob ihm ein früher automatisches Namens-Ziel fehlt. Die Diagnose-Karte aus 90a nennt vorher seine Einrichtung.
5. Mehrsprachiges Tippen ist nicht im Emulator geprüft (nur Code und Robolectric mit festen Ergebnissen), das gilt für beide Behebungen.

## 6. Offene Punkte für den Owner (nicht ausgearbeitet)

- Deutsche Kurzformen mit Apostroph: „gehts“ → „geht's“, „wars“ → „war's“ (heute: „wars“ → „ward“; die Slang-Liste führt „gibts“ und „habs“ bewusst ohne Apostroph; das deutsche Wörterbuch hat nur 18 Wörter mit Apostroph, keine Kurzformen). Neue Funktion, eigenes Konzept, berührt die Slang-Liste.
- Englisch „dont“ → „font“ statt „don't“ (Emulator; „dont“ steht bewusst nicht in der Slang-Liste, „cant“ steht als Whitelist im Wörterbuch, „dont“ nicht).
- Befund 1 (Englisch wird zu Deutsch): hängt an seiner Einrichtung (eigenes englisches Layout: kein Fehler; deutsches Layout ohne zweite Sprache: deutsches Wörterbuch; Mehrsprachig: Grenzfälle von Stufe 60 bei Tippfehlern und unbekannten Wörtern). Erst entscheiden, wenn die Diagnose-Karte seine Einrichtung zeigt.
- Ob „cant“/„ill“/„id“/„lets“/„wont“ auf BEHUTSAM zu „can't“ usw. werden sollen (siehe 3.2).

## 7. Kritiker

Vor dem Bau liest ein unabhängiger Kritiker dieses Konzept mit dem Auftrag, den Fall zu finden, in dem der Plan den Wortlaut erfüllt und der Owner trotzdem scheitert (höchstens drei Punkte, je ein kleinster Versuch).
