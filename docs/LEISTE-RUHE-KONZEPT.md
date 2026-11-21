# Ruhige Leiste beim Schreiben: Konzept (Etappe 89)

Referenz für die Regel „Schreibsitzung“ der festen Leiste. Geschrieben vor dem Code (Teil 1: Analyse, Messung, Konzept; der Umbau ist
Teil 2). Muster: `docs/LEISTE-KONZEPT.md`. Die Messung steht in `StripCalmLogTest` (`app/src/test/.../latin/`), die Referenzfolgen in
`app/src/test/resources/stage89/`. Nichts hier wurde auf einem Gerät gesehen.

## 1. Was der Owner will, und was dem widerspricht

**Will (Build 43, Xiaomi 15):** „Teils bei Autokorrekturen, Leerzeichen, Satzzeichen und oder Absätzen wird die Vorschlagsleiste zur Leiste mit den
gleichmäßig verteilten Icons. Beim Löschen ist es noch extremer und die Leiste flackert bei jedem gelöschten Buchstaben hin und her.“ Vorbild: Gboard.
Zweck: beim Schreiben eine ruhige Leiste. Wer schreibt, schaut auf den Text; eine Leiste, die bei jedem Leerzeichen den Inhalt wechselt, zieht den Blick.

**Widerspricht dem:**
- Ein Wechsel Werkzeuge ↔ Wörter, den der Nutzer nicht ausgelöst hat und der nicht von neuem Inhalt kommt (Leerzeichen, Satzzeichen, Absatz, Löschen).
- Eine Leiste, die nach dem Wechsel an anderer Stelle liegt oder anders groß ist (Plätze hängen weiter nur von der Anzahl ab).
- Werkzeuge, die verschwinden, wenn man sie braucht (nach „Rückgängig“ ist der Wiederholen-Pfeil weg, das nächste „Rückgängig“ geht nicht mehr mit einem Tipp).
- Eine Leiste, die in einem Feld ohne Vorschläge leer bleibt und die Werkzeuge nicht mehr zeigt.
- Eine Regel, die nur in einem Test ruhig ist: die Prüfung läuft mit echten Tastenberührungen (Abschnitt 8).

## 2. Befund: die Ursache, belegt

**Ursache (belegt):** `SuggestionStripView.setSuggestions` ruft bei jeder leeren Liste `BarState.onNeutral()` auf
(`SuggestionStripView.kt:360`), und `onNeutral` schaltet auf die Werkzeuge (`BarState.kt:48-55`, `show = TOOLS`). Jede Leiste mit Inhalt schaltet über `onContent`
(`BarState.kt:57-65`) zurück. Der Inhalt, den der Nutzer sieht, hängt also an der Frage „hat die Wörterbuchsuche gerade etwas?“, nicht an dem, was er tut. Nach einem Leerzeichen
oder Satzzeichen hat sie oft nichts (keine Vorhersage für dieses Wort), nach dem nächsten Buchstaben wieder etwas.

Alle Wege führen durch `LatinIME.setNeutralSuggestionStrip()` (`LatinIME.java:1899`) oder `LatinIME.setSuggestions(leer)` (`LatinIME.java:1815`, ruft jenes auf) nach
`setSuggestedWords` (`LatinIME.java:1771`, Zeile 1805: `mSuggestionStripView.setSuggestions`). Die Aufrufer:

| Ereignis | Aufrufkette (Datei:Zeile) | Ergebnis heute |
| --- | --- | --- |
| Leerzeichen, keine Vorhersage | `handleSeparatorEvent` 1409, Leerzeichen-Zweig 1474-1481 (`setRequiresUpdateSuggestions`) → `updateStateAfterInputTransaction` (`LatinIME.java:1967-1977`) → 100 ms später `performUpdateSuggestionStripSync` (`InputLogic.java:2040/2048`) → leere Liste → `setSuggestions` 2099 → `LatinIME.java:1815` → neutral. Ohne Folgewortvorhersage (`!mBigramPredictionEnabled`) gleich neutral: 2067 | Wörter bleiben bis zum Ende der 100 ms, dann Werkzeuge (Protokoll: `SPACE\|W+\|T-`) |
| Leerzeichen, mit Vorhersage | derselbe Weg, die Liste ist nicht leer → `onContent` | bleibt Inhalt (Wörter wechseln nur die Liste) |
| Satzzeichen (Etappe 84) | `handleSeparatorEvent` 1526 `setNeutralStripAfterBatch` (`InputLogic.java:793`) → `flushNeutralStrip` 777/760 → `setNeutralSuggestionStrip`; im Tauschfall (Leerzeichen und Satzzeichen vertauscht) 1473; davor zeigt `commitCurrentAutoCorrection` 1436/2733 einmal die Wörter des Worts | sofort Werkzeuge (`,\|T-`, Spur `W+>T-`: Wörter und Werkzeuge in einem Tastendruck, das Bild zeigt nur das Ende) |
| Zeilenumbruch (Absatz) | `handleNonFunctionalEvent` 1189 → Zeichen mit Leerraum ist Trennzeichen (1252) → `handleSeparatorEvent` → 1526 wie ein Satzzeichen. Mit „Leiste automatisch anzeigen“ zusätzlich `LatinIME.java:1918-1923` (Zeichen vor dem Cursor ist Enter → `setToolbarVisibility(true)`) | Werkzeuge (`ENTER\|T-`); danach bis zum ersten Buchstaben des Absatzes (+ 100 ms) |
| Autokorrektur beim Leerzeichen | `handleSeparatorEvent` 1429-1437 → `commitCurrentAutoCorrection` 2720, wartende Aktualisierung 2733 mit `showPendingSuggestions` (zeigt die Wörter) → Text ersetzt → Leerzeichen-Zweig 1474 → leere/neutrale Folge wie oben | Wörter des Worts, dann nach 100 ms Werkzeuge oder Vorhersage |
| Rücktaste, im Wort | `handleBackspaceEvent` 1538 → 1734 `restartSuggestionsOnWordTouchedByCursor` (2117) → berührt der Cursor ein Wort: `restartSuggestions` → Wörter (kein Wechsel); die Leiste wird vor dem Neuberechnen **nicht** geleert (kein `setNeutral…` in `handleBackspaceEvent`) | bleibt Inhalt (Protokoll: `<-\|W+\|W+`) |
| Rücktaste, Wortanfang/Wortgrenze | 2134 (Auswahl, Cursor unbekannt, Stapeleingabe), 2148 (nicht am Wort: `postUpdateSuggestionStrip(RECORRECTION)` → Vorhersage oder leer → neutral), 2157 (privater Text), 2174 (Wort fängt mit `'` oder `-` oder Nichtwortzeichen an, `isResumableWord` 2430) | Werkzeuge, sobald nichts vorgeschlagen wird (`<-\|W+\|T-`) |
| Rücktaste über Leerzeichen zurück ins Wort | derselbe Pfad, nach dem Löschen berührt der Cursor wieder das Wort → `restartSuggestions` | Werkzeuge → 100 ms → Wörter (`<-\|T-\|W+`: **das ist das Hin und Her**) |
| Rücktaste, Mitte eines Worts | 1424/1560 `resetEntireInputState(…, clearSuggestionStrip = true)` → 2497 neutral | Werkzeuge, dann Wörter |
| Rücktaste bei „langsamer Verbindung“ | 1730-1731: `hasSlowInputConnection()` (die App hat einmal mehr als 200 ms für eine Leseanfrage gebraucht, zehn Minuten lang, `RichInputConnection.java:152`) → **bei jeder** Rücktaste `setNeutralSuggestionStrip` | die Leiste bleibt bei den Werkzeugen, Wörter kommen nicht mehr (Protokoll `slow-connection`) |
| Rücktaste-Wisch (Löschwisch) | `PointerTracker.java:1147` → `KeyboardActionListenerImpl.onMoveDeletePointer` 447 setzt eine Auswahl (`inputLogic.finishInput`, Cursor springt) → `onUpdateSelection` 602 → `postResumeSuggestions`; Auswahl-Chips (Etappe 82, `tryShowSelectionChips` `LatinIME.java:1904`) sind Inhalt; beim Loslassen löscht `onUpWithDeletePointerActive` 465 → neutral | Chips (Inhalt), nach dem Löschen Werkzeuge |
| Neues Feld, Tastatur wieder offen, Feld leer, Senden | `LatinIME.onStartInputView` 1174-1182 (`setNeutralSuggestionStrip`), `SuggestionStripView.onFieldStarted`/`onKeyboardHidden` → `barState.reset()` | Werkzeuge: das soll so bleiben |
| Chips ablehnen/entfernen | `removeExternalSuggestions` `LatinIME.java:1932`, `ClipboardHistoryManager.kt:112, 495`, `UpdateOnOpen.kt:170`, `InputLogic.java:286` (nach Einfügen/Textaktion), 527 (App-Vervollständigung) | Werkzeuge |

**Zahlen (Messung, `StripCalmLogTest`, Stub-Wörterbuch, Satz „Hallo, wie geht es dir. Gut“ + Absatz + „Und“ + zehn Rücktasten, 41 Tasten):**

| Fall | sichtbare Wechsel Werkzeuge ↔ Inhalt | Tasten, nach denen die Werkzeuge stehen | davon in den zehn Rücktasten |
| --- | --- | --- | --- |
| ohne Folgewortvorhersage (jedes Wort gleicht der Liste von heute: nichts angeboten) | 17 | 11 | 4 |
| mit Folgewortvorhersage („wie“, „und“, „ich“) | 7 | 3 | 0 |
| langsame Verbindung, ohne Vorhersage | 14 | 16 | 1 (danach dauerhaft Werkzeuge) |

(„Sichtbar“ = der Zustand nach dem Tastendruck und nach dem Ablauf der wartenden Aufgaben, 100 ms; was innerhalb eines Tastendrucks hin und her geht, sieht kein Bild.)

Protokoll des Satzes ohne Vorhersage (Taste, Zustand nach der Taste, Zustand nach den wartenden Aufgaben; `T` Werkzeuge, `W` Inhalt, `+`/`-` Inhalt angeboten oder nicht):

```
FIELD T- T-        w T- W+     SPACE W+ T-    ,  T- T-     SPACE W+ T-    r  W+ W+
H T- W+  | a W+ W+ | … | o W+ W+ | , T- T- | SPACE T- T- | w T- W+ | i W+ W+ | e W+ W+ | SPACE W+ T- | g T- W+ …
dir. Gut ENTER: T- T- | U T- W+ | n W+ W+ | d W+ W+ | <- W+ W+ | <- W+ W+ | <- W+ T- | <- T- W+(Gut) | <- W+ W+ | <- W+ W+ | …
```

(Vollständig: `app/src/test/resources/stage89/strip-protocol-*.txt`.) Zwei Muster tragen die Zahlen: **jedes neue Wort beginnt auf den Werkzeugen** (die erste Buchstabentaste nach Leerzeichen,
Satzzeichen oder Absatz zeigt die Werkzeuge, bis die Wörterbuchantwort nach ≈ 100 ms kommt) und **jedes Wort endet auf den Werkzeugen** (Leerzeichen ohne Vorhersage). Das ist ein
Wechsel je Wort, bei jedem Wortende und Wortanfang. Beim Löschen kommt der Wechsel bei jeder Wortgrenze, nicht bei jedem Buchstaben.

**Was die Messung nicht erklärt (ehrlich):** „Flackert bei jedem gelöschten Buchstaben“. Im Wort löscht die Rücktaste im Test ruhig (kein Wechsel). Gefunden sind drei Wege, die pro Rücktaste wechseln
können und die der Test nur teilweise abbildet: (1) die Wortgrenzen, bei gedrückt gehaltener Taste mehrere in der Sekunde; (2) die langsame Verbindung (1731: jede Rücktaste neutral, dauerhaft Werkzeuge,
nur ein Wechsel, aber „extremer“); (3) Wörter, die nicht wiederaufnehmbar sind (2174: Wort beginnt mit `'` oder `-`, Ziffern, URL-Teile): Rücktaste daran → Werkzeuge. Mit dem echten Wörterbuch
(Stub: Vorhersage nur eine feste Liste, immer „Tipp-Wort“ als erster Vorschlag) kann es weitere Fälle geben. Ob eine davon beim Owner zutrifft, zeigt nur ein Mitschnitt auf dem Telefon
(Abschnitt 8, Gerätetest) oder die Diagnose-Karte; Teil 2 soll dafür einen Zähler für Leistenwechsel in der Diagnose vorsehen (Vorschlag, Abschnitt 7).

**Der Owner hat die Voreinstellung „Flacher Look“ vermutlich angewendet** (eigenes dunkles Design, flacher Stil): `FlatPreset.values()` setzt `PREF_AUTO_SHOW_TOOLBAR = true`
und `PREF_AUTO_HIDE_TOOLBAR = true` (`FlatPreset.kt:44-45`). „Automatisch anzeigen“ zeigt die Werkzeuge nach einem Absatz und am Feldanfang
(`LatinIME.java:1918-1923`) auch dann, wenn Wörter angeboten werden (Vorhersage am Zeilenanfang). Das ist genau sein „Absätze“. Ob die Einstellung bei ihm an ist, zeigt die Diagnose;
die Regel muss mit beiden Werten ruhig sein (Abschnitt 4, Regel 8).

## 3. Die Regel „Schreibsitzung“ (Variante A, ohne Zeitsteuerung)

Der Lead hat entschieden. Hier ausgearbeitet, mit den Zuständen, die dazugehören.

**Zustand (neu in `BarState`):** `writing` (in dieser Sitzung ist geschrieben worden) und `pinned` (der Nutzer hat die Werkzeuge verlangt und behält sie bis zum nächsten Zeichen).

**Regeln, in der Reihenfolge, in der sie entscheiden:**

1. **Geltungsbereich.** Nur im Modus „Tasten + Vorschläge“ (`fixedBar`) und nur in einem Feld, das Wörter anzeigen kann: `mInputAttributes.mShouldShowSuggestions && mSuggestionsEnabled`
   (Passwortfeld, Feld mit `NO_SUGGESTIONS`, Zahlen-/Telefonfeld, ausgeschaltete Vorschläge, App-Vervollständigung = andere Regel). Sonst gilt alles wie heute (Werkzeuge bei leerer Liste), weil ein
   Wörter-Bereich, der nie Wörter bekommt, die Werkzeuge für immer verstecken würde.
2. **Anfang.** Neues Feld (`onFieldStarted`), geschlossene und wieder geöffnete Tastatur (`onKeyboardHidden`), leeres Feld: `writing = false`, `pinned = false`, Werkzeuge.
3. **Schreiben beginnt** mit der ersten Wörterliste für getippte Zeichen oder eine Wischeingabe (`INPUT_STYLE_TYPING`, `UPDATE_BATCH`, `TAIL_BATCH`): `writing = true`, `pinned = false`, Inhalt.
   Vorhersagen am Feldanfang und Wiederaufnahme (`BEGINNING_OF_SENTENCE_PREDICTION`, `PREDICTION`, `RECORRECTION`) setzen `writing` nicht (ein Feld mit Text, das offen steht, zeigt ihr Wort, aber noch keine Schreibsitzung).
   Einfügen, Chips und Diktat setzen `writing` nicht: erst ein getipptes Zeichen.
4. **Während `writing`:** `onNeutral` ändert `show` nicht. Die Leiste bleibt im Wörter-Bereich (`show = CONTENT`), `hasContent` ist `false`, der Bereich ist leer. Gilt für Leerzeichen,
   Satzzeichen, Absatz, Autokorrektur, Löschen (alle Wege der Tabelle in 2) und die langsame Verbindung.
5. **Zurück zu den Werkzeugen von selbst** nur bei: Feld wird leer (Cursor am Anfang des Felds und nichts dahinter: Beurteilung in `LatinIME.setNeutralSuggestionStrip` mit dem schon gelesenen
   `getCodePointBeforeCursor() == NOT_A_CODE`, siehe Risiko R5), neues Feld, Tastatur wieder geöffnet (Regel 2).
6. **Zu den Werkzeugen auf Wunsch:** linke Taste („Werkzeuge zeigen“, `onLeftKey` `SHOW_TOOLS`) oder Tipp auf ein Leistenwerkzeug bei sichtbaren Werkzeugen: `pinned = true`. Solange `pinned`,
   ändern Wörter (auch Wiederaufnahme nach „Rückgängig“), neutrale Aktualisierungen, Zwischenablage-Pille, Screenshot-Pille, Autofill, Update-Chip und Cursorbewegungen `show` nicht. `pinned` endet beim nächsten
   getippten Zeichen (Regel 3), bei Feldwechsel/Verstecken (Regel 2), bei neuer Auswahl (Auswahl-Chips gewinnen wie heute) und bei einem Befehls-Chip (er kommt nur durch ein getipptes Zeichen).
   Damit sind nach „Rückgängig“ der Wiederholen-Pfeil (`UndoOffer`) und ein zweites „Rückgängig“ sichtbar, ohne dass ein Wort die Werkzeuge verdrängt.
   Die linke Taste ist bei `pinned` das Raster (wie heute bei sichtbaren Werkzeugen), nicht „zurück zu den Wörtern“: den Weg zurück macht das Tippen.
7. **Auswahl-Chips (Etappe 82, `stickyTools`)** bleiben, wie sie sind: Auswahl → Chips (Inhalt), „Werkzeuge zeigen“ klebt für diese Auswahl. Nach dem Ende der Auswahl (neutral) entscheidet Regel 4 (während
   `writing` leerer Wörter-Bereich, sonst Werkzeuge). `pinned` und `stickyTools` sind ein Mechanismus (`stickyTools` bleibt für Chips, `pinned` ist die Ausweitung auf jeden Inhalt).
8. **Einstellungen des Owners bleiben erhalten**, bis auf einen Fall: „Leiste automatisch anzeigen“ (`mAutoShowToolbar`, vom Flachen Look gesetzt) wirkt in der festen Leiste weiter bei **Feldanfang** und
   **Auswahl**, aber **nicht mehr nach einem Absatz** (`codePointBeforeCursor == CODE_ENTER`, `LatinIME.java:1920`). Das steht im Widerspruch zu „Absätze“ der Meldung und
   ist die einzige Stelle, an der Regel A eine vorhandene Einstellung enger fasst (Rückfrage, Abschnitt 7). „Automatisch ausblenden“ ist in diesem Modus weiter ohne Wirkung (Inhalt ersetzt die Werkzeuge immer).
9. **Andere Leistenmodi** (`TOOLBAR_KEYS`, `SUGGESTION_STRIP`, `HIDDEN`) und der Flache Stil mit ausgeschaltetem Schalter „Feste Leiste wie Gboard“ bleiben unverändert (`fixedBar` falsch, nichts an dieser Regel läuft).
   Die alten Aufrufe `setToolbarVisibility(true/false)` laufen weiter über `onToolsRequested`/`onContentRequested`: `true` setzt `show = TOOLS` und `pinned` nicht (es ist der automatische Weg, kein Wunsch des Nutzers; kommt ein
   Inhalt, geht es zurück), `false` setzt `show = CONTENT` (jetzt auch ohne Inhalt, wenn `writing`).

**Was der leere Wörter-Bereich zeigt.** Nichts im Bereich der Wörter; links bleibt die Taste mit dem Pfeil („Werkzeuge zeigen“, `agent_bar_show_tools`), rechts nichts. Die Leiste behält ihre Höhe und Farbe (der
Bereich ist `suggestionsStrip`, leer, sichtbar). Das ist das Verhalten, das auch heute bei einem Leerzeichen *mit* Vorhersage vorkommt, nur dass es danach nicht mehr verschwindet. Es wirkt nicht wie ein Fehler,
wenn der Pfeil daneben steht und die Leiste eine feste Höhe behält; es wirkt wie ein Fehler, wenn der Nutzer nicht weiß, wohin die Werkzeuge gegangen sind (R1). Die Beschreibung der linken Taste sagt es bereits.

## 4. Angriff auf die Regel: wo scheitert sie beim Owner?

Fälle, in denen die Regel den Wortlaut erfüllt und der Owner trotzdem scheitert, nach Gewicht:

- **R1, die echte Kosten: Werkzeuge sind beim Schreiben einen Tipp weiter weg.** Der Owner arbeitet mit Werkzeugen mitten im Text (Aktionspanel, Skill-Tasten, Diktat, Zwischenablage, Rückgängig). Heute sind sie nach jedem
  Leerzeichen ohne Vorhersage und nach jedem Satzzeichen mit einem Tipp erreichbar; mit A braucht jedes Werkzeug zwei Tipps (Pfeil, Werkzeug) und das Raster drei (Pfeil, Raster-Knopf,
  Werkzeug), solange er schreibt, einschließlich des Diktat-Kreises rechts. Das ist der Preis der Ruhe und genau der Gboard-Zustand; Variante B (Abschnitt 6) holt ihn zurück. **Hier ist A klar schlechter als
  heute**, bewusst.
- **R2, leere Leiste in Feldern mit ausgeschalteten Vorschlägen:** durch den Geltungsbereich (Regel 1) abgefangen. Ohne ihn wäre A in jedem Feld ohne Wörter (Passwort, Zahlenfeld, Vorschläge aus, nur
  Autokorrektur) nach dem ersten Zeichen für immer leer. Das Passwortfeld ist der einzige Fall, in dem die Werkzeuge (gedimmt) sicher bleiben müssen.
- **R3, der Leerlauf nach „Werkzeuge zeigen“:** Der Owner tippt den Pfeil, wählt ein Werkzeug, das Text einfügt (Einfügen aus der Zwischenablage). Danach kommen Wörter (Wiederaufnahme) und, ohne `pinned`, würden sie die
  Werkzeuge sofort wegnehmen. `pinned` löst es; die Ausnahmen (Auswahl-Chips, Befehls-Chips) stehen in Regel 6. Ein Werkzeug, das selbst Text *tippt* (kein Beispiel im Raster heute), würde `pinned`
  beenden: Prüfen bei jedem neuen Werkzeug.
- **R4, die erste Taste jedes Felds wechselt spät:** Die erste Buchstabentaste lässt die Werkzeuge ≈ 100 ms stehen, bis die Wörterbuchantwort kommt (Protokoll `H T- W+`). Das ist ein Wechsel je Feld
  und ruhig genug; ein früherer Wechsel (Hook am Tastencode, `KeyboardActionListenerImpl.onCodeInput`, wie `UndoOffer.onCode`) wäre möglich, ist aber ein zweiter Weg, der sich mit dem ersten streitet.
  Nicht in Teil 2, sofern der Gerätetest den Wechsel nicht als störend zeigt.
- **R5, „leeres Feld“ erkennen:** Billig geht nur „Cursor am Anfang und nichts davor“ (`getCodePointBeforeCursor()`, aus dem Zwischenspeicher). Das erkennt „Nachricht gesendet, Feld geleert“ und „alles gelöscht“, aber auch
  „Cursor mit Pos1 an den Anfang eines Texts gesetzt“: dort zeigt die Leiste die Werkzeuge (kein Wort davor, kein Schaden). Nicht billig: „nichts hinter dem Cursor“ (`hasTextAfterCursor`, kann eine Leseanfrage an die App sein, Etappe 84
  hat solche Lesungen aus dem Tastendruck genommen). Entscheidung: nur das Billige.
- **R6, Apps, die für jedes Zeichen ein neues Feld melden:** Chrome/Compose/Xiaomi-Apps rufen manchmal `onStartInput` neu auf. Ein Neustart im selben Feld (`restarting`) lässt die Leiste offen und ändert `writing`
  nur über die Leere des Felds (P390). Meldet eine App jedoch ein *neues* Feld je Zeichen (kein `restarting`), setzt `onFieldStarted` zurück und die Leiste springt: dasselbe wie heute, nicht schlimmer, aber auch nicht
  besser. Zeigt die Diagnose „Letztes Feld“ viele Feldwechsel, ist das die Ursache, nicht die Regel.
- **R7, Update-Chip und Zwischenablage-Pille bleiben Inhalt der neutralen Leiste:** Nach jedem Leerzeichen ohne Vorhersage kann der Update-Chip oder die Pille erscheinen (`setNeutralSuggestionStrip`, `LatinIME.java:1899` bis
  1925); das ist heute so und ändert sich nicht. Auffällig wird es erst, wenn die Leiste sonst ruhig ist: ein Chip, der bei jedem Wortende kommt und geht, ist ein Wechsel Wörter ↔ Chip (nicht Werkzeuge). Nicht Teil dieser
  Etappe; Teil 2 misst es mit gesetzter Pille (`TypingRig.setUp(withClipboardPill)`).
- **R8, „Automatisch anzeigen“ (Flacher Look) widerspricht „Absätze“:** siehe Regel 8 und Rückfrage.
- **R9, Mikrofon-Kreis:** Er ist Teil der Werkzeugzeile; solange die Wörter stehen, ist er weg. In der Voreinstellung steht er zuletzt in der Leiste: Diktat beim Schreiben ist ein Tipp mehr (R1).

**Wo A schlechter ist als B:** nur in der Entdeckbarkeit und in R1 (zwei Tipps mitten im Satz). **Wo A besser ist als B:** nichts wechselt von selbst; B hat einen Zeitgeber, der Werkzeuge einblendet, während der Owner
nachdenkt oder die Hand zur Wörter-Leiste führt („Element, das von selbst die Position wechselt“, Abschnitt 1).

## 5. Zusammenspiel mit vorhandenen Funktionen

| Funktion | Verhalten mit der Regel |
| --- | --- |
| Auswahl-Knöpfe (Etappe 82, `stickyTools`) | wie heute (Regel 7); nach der Auswahl leerer Wörter-Bereich statt Werkzeuge, wenn `writing` |
| Zwischenablage-Pille, Screenshot-Pille | wie heute Inhalt der neutralen Leiste; bei `pinned` kein Wechsel; Tipp auf die Pille fügt ein und lässt `writing` unberührt |
| Autofill-Chips | Inhalt am Feldanfang (`writing` falsch), danach Regel 3 |
| Befehls-Chips (`@clip` …) | kommen nur durch getippte Zeichen: Inhalt; nach Wahl oder Löschen neutral → Regel 4 |
| Symbol-Chips (gemalte Symbole) | Inhalt, setzen `writing` nicht (kein getipptes Zeichen) |
| Update-Chip | wie Pille (R7) |
| `UndoOffer` (Wiederholen-Pfeil) | der Pfeil ist am Werkzeug „Rückgängig“; die Werkzeuge bleiben nach einem Tipp auf „Rückgängig“ stehen (`pinned`), so ist er sichtbar; er endet mit dem Text wie bisher |
| Passwort-/Inkognito-Feld, Zahlenlayout | Geltungsbereich (Regel 1): wie heute; Werkzeuge gedimmt wie heute |
| Feld wird von der App geleert (Nachricht gesendet) | `onStartInputView(restarting)` → `setNeutralSuggestionStrip`, Feld leer → Werkzeuge (Regel 5); ohne Leeren (Suchfeld bleibt gefüllt) bleibt der Wörter-Bereich |
| Cursor nur bewegt (Space-Wisch, Trackpad) | `onUpdateSelection` → Wiederaufnahme: Wörter, wenn der Cursor ein Wort berührt, sonst leer; kein Wechsel zu Werkzeugen |
| Text eingefügt (App, Einfügen-Werkzeug, Pille) | kein getipptes Zeichen: `writing` unverändert (im leeren Feld Werkzeuge, in der Sitzung leerer Bereich) |
| Gestentippen (Wischen) | Wörter der Eingabe setzen `writing` (Regel 3) |
| Sprachwechsel | ändert nur Layout und Wörterbuch, `writing` bleibt; Wörter kommen mit der nächsten Aktualisierung |
| Feld mit vorhandenem Text wird geöffnet | `writing` falsch, Werkzeuge; berührt der Cursor ein Wort, zeigt die Wiederaufnahme es (heute so). Erst das erste getippte Zeichen setzt `writing` |
| Raster, Anpassen-Modus | unverändert (`gridOpen()` zwingt die Werkzeuge in `applyBarState`) |
| Diagnose „Letztes Feld“ (P384) | die Leiste mit `show = CONTENT` und `hasContent = false` meldet „leer“, nicht „Werkzeugleiste“ (`reportToLastField`: `tools` falsch, Wörter keine, extern keine) |
| Tasten- und Zeichenverhalten, Autokorrektur, Lernen | unberührt; `InputLogic`, `RichInputConnection`, `PointerTracker` bleiben unberührt |

## 6. Verworfene Alternative: Variante B „Ruhepause“

Wie A, aber nach ≈ 1,5 s ohne Taste und ohne Vorschlag kommen die Werkzeuge von selbst.

**Vorteile:** Die Werkzeuge sind in Denkpausen einen Tipp entfernt (R1 entfällt); der leere Bereich dauert nie lang (kein „wo sind sie hin“); es braucht keine neue Bedienung, nur einen Zeitgeber.
**Nachteile:** Ein Element wechselt von selbst (Abschnitt 1, Gegenanforderung): Der Owner denkt nach, die Leiste wechselt; er greift zu einem Wort in der Leiste, genau dann wechselt sie zu den Werkzeugen und er trifft ein
Werkzeug. Ein Zeitgeber braucht Abbruch bei jeder Taste, jedem Cursorsprung, jedem Chip, bei Feldwechsel, bei Raster und Anpassen (eine Fehlerquelle mehr; Prüfung nur mit Zeit). Eine Pause von 1,5 s ist beim
Tippen auf dem Telefon alltäglich (Wort suchen, Nachricht lesen), also wechselt die Leiste im Alltag doch ständig. B lässt sich später auf A aufsetzen (`BarState.onPause()`), umgekehrt ist es ein Umbau.
**Entscheidung:** A zuerst. Bleibt R1 beim Owner ein Ärgernis, ist B ein kleiner, getrennter Nachtrag mit eigener Prüfung.

## 7. Offene Entscheidungen für den Owner (nur echte)

1. **„Leiste automatisch anzeigen“ nach einem Absatz:** In der festen Leiste nur noch bei neuem Feld und Auswahl, nicht mehr nach Enter (Regel 8)? Empfehlung: ja (sonst bleiben „Absätze“ bestehen,
   die Voreinstellung „Flacher Look“ setzt sie an). Das ändert eine vorhandene Einstellung und braucht darum sein Ja.
2. **Zwei Tipps statt einem für Werkzeuge während des Schreibens (R1):** Akzeptiert er das, oder soll B mit? Empfehlung: erst A, dann nach einer Woche am Telefon entscheiden (kein Umbau nötig).
3. **Zähler für Leistenwechsel in der Diagnose** (nur Zahlen, keine Texte; „Leiste: n Wechsel in 100 Tasten“): Vorschlag, nicht bestellt; hilft, die Rücktasten-Meldung am Gerät zu klären. Empfehlung: ja, klein, leicht entfernbar.

## 8. Prüfplan (Abnahme-Zeilen)

Nachweis: **U** = `StripCalmLogTest` (Robolectric, echte `LatinIME`/`InputLogic`/Leiste über `TypingRig`); **G** = Gerätetest (`tools/devicetest/devicetest.py`, echte Tastenberührungen, die Leiste wie die
`bar_*`-Szenarien abgefragt: `dump()`, `bar_report`, `left_key`); **B** = Bild über `shot()` (Job-Log, `shots_from_log.py`), angesehen im dunklen Design; **O** = nur der Owner am Telefon.

| # | Ausgangslage beim Owner | Aktion | Erwartung | Gegenfall | Nachweis |
| --- | --- | --- | --- | --- | --- |
| 1 | Neues Textfeld (Notiz-App), dunkles Design, Voreinstellung „Flacher Look“ (Auto-Anzeige an), Wörterbuch Deutsch | Satz „Hallo, wie geht es dir. Gut“ tippen | genau ein Wechsel (Werkzeuge → Wörter bei der ersten Taste), danach nie wieder Werkzeuge, auch nicht nach `,` `.` und Leerzeichen | die Leiste wird nach Satzzeichen zu den Werkzeugen (17 Wechsel heute) | U (neue Folge in `stage89/`), G, O |
| 2 | wie 1, Folgewort-Vorhersage aus oder keine für das Wort | Leerzeichen nach jedem Wort | Wörter-Bereich leer, Pfeil links, Höhe gleich, nichts springt | leerer Bereich wirkt kaputt (kein Pfeil, andere Höhe) | G, B (`calm_empty`) |
| 3 | wie 1, Absatz | Enter (Mehrzeilenfeld), „Und“ tippen | Enter wechselt nichts, auch nicht mit „Automatisch anzeigen“ | Werkzeuge nach Enter | U (Einstellung an und aus), G |
| 4 | Text im Feld | zehn Rücktasten einzeln, dann einmal gehalten | in der Sitzung null Wechsel, auch über Leerzeichen, Absatz und Satzzeichen zurück ins Wort | Werkzeuge ↔ Wörter an Wortgrenzen (4 Wechsel heute) | U, G (jede Rücktaste abgefragt) |
| 5 | wie 4, App mit langsamer Verbindung | Rücktaste | wie 4 (kein dauerhaftes Werkzeuge) | `hasSlowInputConnection` bringt die Werkzeuge | U (`todayWithSlowConnection`, neue Folge) |
| 6 | Text im Feld, „Tabula rasa“ | alles löschen | beim leeren Feld Werkzeuge; erste Taste: Wörter | Wörter-Bereich bleibt leer, Werkzeuge nie wieder | U, G |
| 7 | Chat-App, Nachricht getippt | Senden (Feld wird geleert, `restarting`) | Werkzeuge | leerer Bereich | U (`startField("", restarting = true)`), O (Chat-App) |
| 8 | in der Sitzung | Pfeil links tippen, „Rückgängig“ tippen, „Rückgängig“ noch einmal | Werkzeuge bleiben, der Wiederholen-Pfeil sichtbar, zweites Rückgängig geht; nächstes Zeichen: Wörter | Wörter der Wiederaufnahme verdrängen die Werkzeuge nach dem ersten Rückgängig | U, G, B (`calm_undo_redo`) |
| 9 | in der Sitzung | Pfeil, Raster-Knopf, Werkzeug | drei Tipps, das Raster öffnet | Raster nur über Langdruck | G |
| 10 | Passwortfeld, Zahlenfeld, Feld mit ausgeschalteten Vorschlägen | tippen | Werkzeuge (gedimmt wie heute) wie heute, nie ein leerer Bereich | leerer Bereich ohne Weg zurück | U, G |
| 11 | Text markiert (Auswahl-Chips) | „Werkzeuge zeigen“, Auswahl ändern, Auswahl beenden | wie heute (`FixedBarTest`, `stickyTools`); nach dem Ende in der Sitzung leerer Bereich | Chips bleiben nach dem Ende | U (vorhandene Tests grün), G |
| 12 | Zwischenablage-Pille (Kopie jünger als drei Minuten) | Leerzeichen nach einem Wort | Pille wie heute; Pille tippen fügt ein und ändert den Wörter-Bereich nicht | Pille verdrängt die Werkzeuge nach „Rückgängig“ (`pinned`) | U (`setUp(withClipboardPill = true)`) |
| 13 | Feld mit Text, Cursor am Wortende | Tastatur öffnen | Werkzeuge oder die Wörter des Worts (heute so); erster Buchstabe: Wörter | Sitzung beginnt ohne Zeichen | U, G |
| 14 | wie 1 | Tastatur schließen, öffnen, neues Feld | Werkzeuge | alter Zustand bleibt | U (`startField`, `onKeyboardHidden`), G |
| 15 | andere Leistenmodi | tippen | wie vor der Etappe | Regel in anderem Modus | U (`FixedBarTest` unverändert grün) |
| 16 | Raster/Anpassen offen, Tastenanschlag | Satz tippen | unverändert | Regel stört das Anpassen | vorhandene Tests (`BarEditModeTest`) grün |

**Reihenfolge in Teil 2:** (1) Referenzfolgen in `stage89/` durch die erwarteten Folgen ersetzen (rot sehen), (2) `BarState` (rein) mit Zustandstest, (3) Anschluss in `SuggestionStripView`/`LatinIME`
(`InputLogic`, `RichInputConnection`, `PointerTracker` unberührt), (4) Szenario `bar_calm_typing` im Gerätetest (Satz tippen, löschen, jede Probe der Leiste ablegen; Bilder `calm_after_space`, `calm_empty`,
`calm_undo_redo` im dunklen Design), (5) vor dem Bau liest ein Prüfer dieses Konzept mit dem Auftrag aus `product-initiative` (der Fall, in dem der Plan den Wortlaut erfüllt und der Owner trotzdem scheitert; R1 bis R9 sind
mein eigener Versuch, kein Ersatz).

**Was nur der Owner prüfen kann:** ob die Wechsel beim Löschen auf seinem Telefon tatsächlich von Wortgrenzen kommen (Abschnitt 2, offener Punkt), wie sich der leere Bereich im Alltag anfühlt (R1), und ob „Automatisch
anzeigen“ bei ihm an ist.
