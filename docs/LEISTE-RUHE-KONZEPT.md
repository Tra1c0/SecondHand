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


## 2a. Nachtrag nach der Kritik (Kritiker, Entscheidungen des Leads)

- **K1 (schwer):** `SuggestionStripView.setSuggestions` ruft zuerst `clear()` und legt dann die neue Liste an (`SuggestionStripView.kt` 350-360, `clear` 635). Bliebe nur `show = CONTENT`, würde aus „Werkzeuge ↔ Wörter“ ein
  „Wörter ↔ leer“: dasselbe Flackern. Darum zählt die Messung **jeden sichtbaren Inhaltswechsel** (Werkzeuge, Wörter, leer, Chip/Pille getrennt), und die Regel enthält die Entprellung (Regel 5).
  Vermuteter Weg beim Owner (Claude-App): langsame Verbindung (`InputLogic:1730`: jede Rücktaste neutral) plus `onUpdateSelection` → `postResumeSuggestions` (`InputLogic:636-653`) bringt die Wörter zurück: je Buchstabe einmal leer, einmal Wörter.
  Der Test `todayWithSlowConnectionAndResume` stellt genau das nach und muss vor dem Umbau rot sein.
- **K2 (schwer):** Der Mikrofon-Kreis ist nur der letzte Platz der Werkzeugzeile (`FixedBar` 166, 181); `applyBarState` (785-787) tauscht die ganze Zeile gegen die Wörter. Unter Regel A ist Diktieren in einer Schreibsitzung zwei Tipps weit weg.
  Entscheidung: Stufe 2 (Abschnitt 9): linke Taste und Kreis stehen fest, nur die Mitte wechselt (wie Gboard). Stufe 1 baut das nicht.
- **K3:** „Schreibsitzung nur aus getippten Zeichen über die Eingabeart“ geht nicht (die Wiederaufnahme liefert `INPUT_STYLE_TYPING`, `InputLogic:2219`), und nach dem Wiederöffnen wäre `writing` zurückgesetzt:
  wer dann nur löscht, hätte das alte Verhalten, genau der gemeldete Fall. Entscheidung: `writing` wird am **Tastencode** gesetzt (Regel 3).
- **K4:** Ohne Personalisierung (`PREF_KEY_USE_PERSONALIZED_DICTS = false`) und ohne Folgewortvorhersage steht der Wörter-Bereich nach fast jedem Leerzeichen leer. Das ist der Normalfall der Regel, nicht die Ausnahme; die Diagnose-Karte zeigt, ob es beim Owner so ist.
- **K5:** Die Zwischenablage-Pille (`LatinIME.setNeutralSuggestionStrip` 1905) wechselt unter A nach jeder Kopie drei Minuten lang an jedem Wortende mit den Wörtern. Siehe Abschnitt 7, Vorschlag 3 (nicht gebaut).

## 3. Die Regel „Schreibsitzung“ (Variante A, ohne Zeitsteuerung für den Wechsel Werkzeuge/Wörter)

**Zustand (neu in `BarState`):** `writing` (in dieser Sitzung ist geschrieben worden), `pinned` (der Nutzer hat die Werkzeuge verlangt und behält sie bis zum nächsten Zeichen), `calm` (die Regel gilt in diesem Feld).

**Regeln, in der Reihenfolge, in der sie entscheiden:**

1. **Geltungsbereich (`calm`).** Nur im Modus „Tasten + Vorschläge“ (`fixedBar`) und nur in einem Feld, das Wörter anzeigen kann: `mInputAttributes.mShouldShowSuggestions && mSuggestionsEnabled` (kein Passwort-, Zahlen-, Telefonfeld, keine
   ausgeschalteten Vorschläge, keine App-Vervollständigung). Sonst gilt alles wie heute.
2. **Anfang.** Neues Feld (`onFieldStarted`), geschlossene und wieder geöffnete Tastatur (`onKeyboardHidden`), leeres Feld: `writing = false`, `pinned = false`, Werkzeuge.
3. **`writing` wird am Tastencode gesetzt** (Hook im Listener wie `UndoOffer.onCode`, `KeyboardActionListenerImpl.onCodeInput`): durch jede Zeichentaste (Code ≥ 0, auch Leerzeichen und Enter), jede Wischeingabe (`onEndBatchInput`)
   und die Rücktaste, auch den Löschwisch beim Loslassen (`onUpWithDeletePointerActive`), in einem Feld mit `calm`. Cursorbewegung, Feld öffnen, Einfügen, Chips und Diktat setzen es nicht. Der Hook beendet auch `pinned`.
   Er ändert die Leiste nicht selbst: die Anzeige wechselt mit dem nächsten Inhalt oder dem nächsten neutralen Aufruf.
4. **Während `writing`:** ein neutraler Aufruf (`onNeutral`) schaltet nicht auf die Werkzeuge. `show` ist `CONTENT`, `hasContent` falsch, der Bereich leer. Gilt für Leerzeichen, Satzzeichen, Absatz, Autokorrektur, Löschen, langsame Verbindung.
5. **Keine Leerung zwischen zwei Listen (Entprellung der Anzeige).** Ein neutraler Aufruf nimmt die sichtbaren Wörter nicht sofort weg. Kommt innerhalb von **`BarState.CALM_DEBOUNCE_MS` = 150 ms** eine neue nichtleere Liste (oder ein Chip),
   ersetzt sie die alten Wörter direkt; sonst wird geleert. 150 ms, weil die Aktualisierung nach einem Zeichen mit 100 ms Verzögerung läuft (`mDelayInMillisecondsToUpdateSuggestions`) plus Antwortzeit des Wörterbuchs; eine längere Frist ließe
   veraltete Wörter sichtbar stehen. **Die Frist läuft ab dem letzten Zeichen** (Prüferbefund F1): jede getippte Taste, auch die Rücktaste, startet sie neu, solange die Wörter als veraltet gelten; leer wird die Mitte also nur in
   einer echten Pause (länger als 250 ms nach dem letzten Zeichen). Eine Antwort der App, die später kommt als die Frist (langsame Verbindung, Wiederaufnahme nach 350 ms), macht die Mitte weiter kurz leer (gemessen: etwa 100 ms bei 350 ms Antwort). Das ist nur eine Entprellung, kein Wechsel zu den Werkzeugen. **Veraltete Wörter in der Frist** sind **nicht antippbar** (Tipp wird verworfen, `onClick`/`onLongClick` der Wörter und das Wischen nach oben prüfen
   `stale`): das ist das Sichere, denn ein Tipp auf „hello“, während der Text schon weiter ist, würde das falsche Wort einsetzen. Die Wörter sehen in der Frist unverändert aus (kein Abdunkeln, das wäre selbst ein Flackern).
   Kein Entprellen bei leerem Feld, neuem Feld, Verstecken (sofort).
6. **Zurück zu den Werkzeugen von selbst** nur bei: Feld leer (erwartete Cursorposition 0 **und** nichts davor, `getExpectedSelectionStart() == 0 && getCodePointBeforeCursor() == NOT_A_CODE`, nur gelesen, solange `writing`; Prüferbefund F2: konnte die
   Tastatur den Text nicht lesen, ist der Zwischenspeicher leer und die Position unbekannt (-1), das ist kein leeres Feld), neues Feld, Tastatur wieder geöffnet. Der Weg mit Inhalt (Vorhersagen am Feldanfang) prüft dasselbe und beendet die Sitzung, die Vorhersagen bleiben stehen (F5: sie sind Inhalt; Gboard zeigt dort Vorhersagen und feste Knöpfe, mit Stufe 2 wäre beides sichtbar); ein späterer neutraler Aufruf zeigt dann die Werkzeuge.
7. **Zu den Werkzeugen auf Wunsch:** linke Taste („Werkzeuge zeigen“) oder Tipp auf ein Leistenwerkzeug bei sichtbaren Werkzeugen: `pinned = true`. Solange `pinned`, ändern Wörter (auch die Wiederaufnahme nach „Rückgängig“), neutrale Aufrufe, Pille,
   Screenshot-Pille, Autofill, Update-Chip und Cursorbewegungen `show` nicht. `pinned` endet beim nächsten Zeichen (Hook), Feldwechsel/Verstecken, neuer Auswahl (Auswahl-Chips gewinnen) und Befehls-Chip (kommt nur durch ein Zeichen).
   Die linke Taste ist bei `pinned` das Raster (wie bei sichtbaren Werkzeugen heute).
8. **Auswahl-Chips (Etappe 82, `stickyTools`)** bleiben, wie sie sind. Nach der Auswahl entscheidet Regel 4.
9. **„Leiste automatisch anzeigen“ (Entscheidung Lead):** in der festen Leiste während `writing` **nicht** nach Enter (`LatinIME.java:1920`: Bedingung `CODE_ENTER` nur ohne Schreibsitzung); bei neuem Feld und Auswahl wie heute. „Automatisch ausblenden“
   bleibt ohne Wirkung.
10. **Andere Leistenmodi** (`TOOLBAR_KEYS`, `SUGGESTION_STRIP`, `HIDDEN`) unverändert. Die alten Aufrufe `setToolbarVisibility(true/false)` laufen über `onToolsRequested`/`onContentRequested`; `true` setzt `pinned` nicht.

**Was der leere Wörter-Bereich zeigt.** Nichts im Bereich der Wörter, links der Pfeil „Werkzeuge zeigen“. Höhe und Farbe bleiben. In Stufe 2 stehen auch der Kreis und die linke Taste fest, sodass der Bereich nicht wie ein Loch wirkt.

## 4. Angriff auf die Regel: wo scheitert sie beim Owner?

- **R1 (Kosten, K2): Werkzeuge sind beim Schreiben einen Tipp weiter weg**, Diktat eingeschlossen. In Stufe 1 bewusst so (zwei Tipps statt einem, das Raster drei); Stufe 2 nimmt dem Diktat den Mehrweg. **Hier ist A schlechter als heute.**
- **R2, leere Leiste ohne Wörter:** durch den Geltungsbereich (Regel 1) abgefangen (Passwort, Zahlenfeld, Vorschläge aus).
- **R3, Leerlauf nach „Werkzeuge zeigen“:** `pinned` (Regel 7).
- **R4, die erste Taste jedes Felds wechselt spät** (≈ 100 ms Werkzeuge bis zur Wörterbuchantwort): ein Wechsel je Feld, hingenommen.
- **R5, „leeres Feld“ nur billig erkennbar** (Cursor am Anfang, nichts davor): Pos1 in gefülltem Feld zeigt die Werkzeuge.
- **R6, Apps, die für jedes Zeichen ein neues Feld melden:** die Leiste springt wie heute; die Diagnose zählt `onStartInput` mit und ohne `restarting`.
- **R7 (K5), Pille und Update-Chip:** wechseln wie heute mit den Wörtern; nach jeder Kopie drei Minuten lang an jedem Wortende. Nicht gebaut (Abschnitt 7).
- **R8, Entprellung:** 150 ms veraltete Wörter; deshalb nicht antippbar. Ein Gegenfall: der Owner tippt in genau diesem Fenster auf ein Wort und nichts geschieht. 150 ms sind kürzer als eine Zielbewegung; der Zähler „verworfene Tipps“ ist in der Diagnose.
- **R9, Entprellung und Hartnäckigkeit:** Wechselt die Liste öfter als alle 150 ms zwischen leer und nichtleer (Wörterbuchantwort langsamer als 150 ms), flackert es weiter. Die Diagnose zählt die Wechsel „leer“ mit.
- **R10, Mehr als Stufe 1:** Das Hin und Her an Wortgrenzen ist mit R8 ruhiger, aber der Wechsel *erste Taste → Wörter* bleibt (R4).

**Wo A schlechter ist als B:** nur in der Entdeckbarkeit und R1. **Wo A besser ist als B:** nichts wechselt von selbst.

## 5. Zusammenspiel mit vorhandenen Funktionen

| Funktion | Verhalten mit der Regel |
| --- | --- |
| Auswahl-Knöpfe (82, `stickyTools`) | wie heute; nach der Auswahl leerer Bereich, wenn `writing` |
| Zwischenablage-Pille, Screenshot-Pille | Inhalt der neutralen Leiste wie heute (ersetzt direkt, ohne Frist); bei `pinned` kein Wechsel |
| Autofill-Chips | Inhalt am Feldanfang (`writing` falsch) |
| Befehls-Chips, Symbol-Chips | Inhalt; Symbol-Chips setzen `writing` nicht |
| Update-Chip | wie Pille |
| `UndoOffer` | Werkzeuge bleiben nach dem Tipp auf „Rückgängig“ (`pinned`), der Wiederholen-Pfeil ist sichtbar |
| Passwort-/Inkognito-Feld, Zahlenlayout | Regel 1: wie heute |
| Feld von der App geleert | Feld leer → Werkzeuge (Regel 6) |
| Cursor bewegt (Trackpad) | Wiederaufnahme: Wörter oder leer, kein Wechsel zu den Werkzeugen |
| Text eingefügt | `writing` unverändert |
| Wischeingabe | setzt `writing` (Hook) |
| Sprachwechsel | `writing` bleibt |
| Feld mit Text geöffnet | Werkzeuge, bis ein Zeichen oder die Rücktaste kommt (Regel 3) |
| Raster, Anpassen | unverändert |
| Diagnose „Letztes Feld“ (P384) | `show = CONTENT`, `hasContent = false` meldet „leer“ |
| `InputLogic`, `RichInputConnection`, `PointerTracker` | unberührt |

## 6. Verworfene Alternative: Variante B „Ruhepause“ (bleibt verworfen, Entscheidung Lead)

Wie A, aber nach ≈ 1,5 s ohne Taste und ohne Vorschlag kommen die Werkzeuge von selbst. Vorteil: Werkzeuge in Denkpausen einen Tipp entfernt. Nachteil: ein Element wechselt von selbst (Gegenanforderung); 1,5 s Pause ist beim Tippen alltäglich;
Zeitgeber mit Abbruch an jeder Taste, jedem Cursorsprung, jedem Chip. Mit Stufe 2 (feste Taste und feststehender Kreis) entfällt auch der Hauptvorteil.

## 7. Offene Entscheidungen und Vorschläge für den Owner

1. **Entschieden (Lead):** „Leiste automatisch anzeigen“ zeigt nach Enter in der Schreibsitzung nicht mehr die Werkzeuge.
2. **Stufe 2 (feste linke Taste und Kreis neben den Wörtern):** auf Zeichen des Leads; Abschnitt 9.
3. **Vorschlag (nicht gebaut): Zwischenablage-Pille während `writing` nur einmal je Kopie.** Die Pille käme nach jeder Kopie an jedem Wortende wieder (R7). Ob es beim Owner vorkommt, zeigt der Zähler „Chip/Pille“ der Diagnose-Karte.
4. **Zähler in der Diagnose** (Karte „Leiste“): gebaut in Stufe 1 (Abschnitt 8a).

## 8. Prüfplan (Abnahme-Zeilen)

Nachweis: **U** = Robolectric (`StripCalmLogTest`, `BarStateTest`, `FixedBarTest`); **G** = Gerätetest (`tools/devicetest/devicetest.py`, Szenario `bar_calm_typing`, echte Tastenberührungen); **B** = Bild über `shot()` (Job-Log,
`shots_from_log.py`), angesehen im dunklen Design; **O** = nur der Owner am Telefon.

| # | Ausgangslage beim Owner | Aktion | Erwartung | Gegenfall | Nachweis |
| --- | --- | --- | --- | --- | --- |
| 1 | Neues Textfeld, dunkles Design, Flacher Look (Auto-Anzeige an), Deutsch | Satz „Hallo, wie geht es dir. Gut“ tippen | genau ein Wechsel (Werkzeuge → Wörter bei der ersten Taste), danach nie wieder Werkzeuge | Werkzeuge nach Satzzeichen (heute 17 Wechsel) | U, G, O |
| 2 | wie 1, keine Folgewortvorhersage | Leerzeichen nach jedem Wort | Wörter-Bereich leer ohne Sprung; innerhalb eines Worts null Wechsel, **auch kein Leeren** | Wörter ↔ leer (K1) | U (`switches` zählt jeden Inhaltswechsel), G |
| 3 | wie 1, Absatz, „Automatisch anzeigen“ an und aus | Enter, „Und“ | Enter wechselt nichts | Werkzeuge nach Enter | U (beide Einstellungen), G |
| 4 | Text im Feld | zehn Rücktasten einzeln | in der Sitzung null Wechsel, auch über Leerzeichen und Absatz zurück ins Wort | Wechsel an Wortgrenzen (heute 4) | U, G |
| 5 | langsame Verbindung (Claude-App) | Rücktaste, mit `onUpdateSelection`-Wiederaufnahme | kein Leeren zwischen den Listen | je Buchstabe einmal leer, einmal Wörter | U (`todayWithSlowConnectionAndResume`, vorher rot) |
| 6 | Text im Feld, Tastatur wieder geöffnet | nur löschen | Rücktaste setzt `writing`: ruhig | altes Verhalten | U, G |
| 7 | Text im Feld | alles löschen | beim leeren Feld Werkzeuge; erste Taste: Wörter | Bereich bleibt leer | U, G |
| 8 | Chat-App | Senden (Feld leer, `restarting`) | Werkzeuge | leerer Bereich | U, O |
| 9 | in der Sitzung | Pfeil links tippen, „Rückgängig“, „Rückgängig“ | Werkzeuge bleiben, Wiederholen-Pfeil sichtbar; nächstes Zeichen: Wörter | Wiederaufnahme verdrängt die Werkzeuge | U, G, B |
| 10 | Passwort-, Zahlenfeld, Vorschläge aus | tippen | Werkzeuge wie heute | leerer Bereich ohne Weg zurück | U, G |
| 11 | alte Wörter in der Frist (150 ms) | Tipp auf ein Wort | wird verworfen, nichts eingefügt | falsches Wort eingesetzt | U |
| 12 | Auswahl | „Werkzeuge zeigen“, Auswahl beenden | wie heute | Chips bleiben | U (`FixedBarTest`) |
| 13 | Pille (Kopie jünger als drei Minuten) | Leerzeichen nach einem Wort | Pille ersetzt direkt, ohne Leeren | Leeren vor der Pille | U |
| 14 | Feld mit Text geöffnet | Tastatur öffnen, ein Zeichen | Werkzeuge, dann Wörter | Sitzung beginnt ohne Zeichen | U, G |
| 15 | Karte „Leiste“ der Diagnose | nach dem Tippen öffnen | Zähler stimmen mit dem Protokoll des Tests überein; kein Text, nichts im geschützten Feld | Text oder App-Name in der Karte | U (Test der Karte) |
| 16 | andere Leistenmodi, Raster, Anpassen | wie vorher | unverändert | Regel wirkt dort | vorhandene Tests grün |

### 8a. Diagnose-Karte „Leiste“ (E2)

`BarCalmCounters` (Zahlen, nie Text, nie App-Name, nichts in geschützten Feldern, nach dem Muster der Trackpad-Diagnose): sichtbare Inhaltswechsel seit dem Start und im letzten Feld (Werkzeuge, Wörter, leer, Chip/Pille getrennt);
je Rücktaste der Weg (im Wort, Wiederaufnahme, langsame Verbindung); `onStartInput` mit und ohne `restarting`; ob die Verbindung gerade als langsam gilt; „Leiste automatisch anzeigen/verbergen“, Folgewortvorschläge, Personalisierung an oder aus;
Zahl der verworfenen Tipps auf veraltete Wörter.

## 9. Stufe 2 (nicht gebaut, auf Zeichen des Leads): linke Taste und Kreis stehen fest

**Ziel (E3):** Linke Taste und Akzent-Kreis (Mikrofon, wenn er in der Leiste liegt) stehen fest, auch neben den Wörtern; nur die Mitte wechselt zwischen Werkzeugen und Wörtern, wie Gboard.

**Eingriffsplan:**
- `FixedBar.arrange` (166, 181): die Mikrofon-Taste mit Kreis aus der Werkzeugzeile lösen; die Plätze für die übrigen Werkzeuge rechnen mit einem Platz weniger, wenn der Kreis fest steht.
- `SuggestionStripView.applyBarState` (785-787): nicht die ganze Zeile, nur die Mitte (Werkzeugplätze ohne Kreis) tauschen; der Kreis bleibt sichtbar (Verfügbarkeit, Kontrastregel, RGB wie bisher).
- Platzberechnung fürs Anpassen (`SuggestionStripView` 1090-1111, `slotCentres`, `BarEditor.slotCountAfterDrop`): der Kreis zählt als fester Endplatz; „Plätze hängen nur von der Anzahl ab“ gilt weiter.
- Anpassen-Modus: im Modus gilt unverändert die Werkzeugzeile mit Kreis am Ende (kein Wörter-Bereich); Ablegen auf den Kreis-Platz wie bisher.
- Schmale Bildschirme: Breite des Wörter-Bereichs = Leistenbreite − linke Taste (40 dp) − Kreis (34 dp) − Ränder (12 dp). Bei 360 dp bleiben ≈ 274 dp, bei 320 dp ≈ 234 dp; die Leiste zeigt bis zu drei Wörter (je ≈ 90 beziehungsweise 78 dp); lange Wörter werden gekürzt, der Pfeil für weitere Vorschläge entfällt bei weniger Platz. **Vor dem Bau zu messen** mit dem Renderbild bei 320/360/411 dp.
- Zusammenspiel: Kontrastregel (`StripIconColors`) und `RgbStrip` für den Kreis neben Wörtern; `UndoOffer` unberührt.

**Abnahme-Zeilen Stufe 2:**

| # | Ausgangslage | Aktion | Erwartung | Gegenfall | Nachweis |
| --- | --- | --- | --- | --- | --- |
| S1 | Standardleiste mit Mikrofon am Ende, dunkles Design | tippen | links Taste und rechts Kreis bleiben an ihrem Platz, die Mitte wechselt | Kreis verschwindet mit den Werkzeugen | G, B |
| S2 | dasselbe, 320 dp / 360 dp | Wort tippen | zwei bis drei Wörter lesbar, nichts überlappt | abgeschnittene Wörter ohne Platz | Render-Test, B |
| S3 | Diktat mitten im Satz | Kreis tippen | ein Tipp | zwei Tipps | G |
| S4 | Anpassen-Modus | ziehen | wie Etappe 88, Kreis am Ende | Plätze verschieben sich | `BarEditModeTest`, G |
