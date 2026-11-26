# Gezeichnete Symbole ohne Wartezeit: Konzept (Etappe 100)

Auftrag des Owners (Runde 5, wörtlich): „Minimiere realistisch die Latenz ab Zeichnungsabschluss. Keine Fehl-, Früh- oder
Doppeleingaben. Mehrstrichsymbole mit Absetzen, beliebiger Strichreihenfolge und variabler Richtung erkennen: → aus „-“ und „>“, %
aus „/“ und zwei Punkten. Fertige Symbole von Zwischenzuständen unterscheiden; weder unnötig warten noch spekulativ eingeben. Teste
alle Symbole, Zeichenvarianten, Geschwindigkeiten, Größen, Pausen und Verwechslungskandidaten.“ Früher dazu (`docs/ROADMAP.md`, 100):
„Symbole/Zeichen braucht dringend Schnelligkeit. Aktuell ca. eine halbe Sekunde.“

Annahme (der Owner schläft, nicht gefragt): „Symbole“ meint hier die **gemalten** Symbole (Einstellung „Symbole malen“), so wie der
neue Auftrag es sagt. Die Lesart des alten Roadmap-Eintrags (Symbolseite der Tastatur, Etappe 84) ist nicht Teil dieser Etappe und
bleibt in `docs/ROADMAP.md` offen.

## 1. Was er will, und was dem widerspricht

Will: Finger hebt sich vom letzten Strich, das Symbol steht im Feld, so schnell es geht. Ein Symbol aus mehreren Strichen
(→, =, ÷, %) wird als eines erkannt, egal in welcher Reihenfolge und Richtung er die Striche malt.

Widerspricht dem (Antiprompt): jedes Symbol wartet gleich lange, auch eines, das gar nicht weitergehen kann; das Warten ist länger,
als er zwischen zwei Strichen braucht; ein „–“ wird schon geschrieben, während er den Kopf von → noch malt (Früheingabe, aus einem →
werden „–“ und ein zweites Symbol); ein Symbol kommt zweimal; ein Zwischenstand kommt erst, und das fertige Symbol ersetzt ihn
danach (spekulativ). Ebenso: eine gewöhnliche Taste nach dem Symbol kommt verzögert.

## 2. Befund (aus dem Code, belegt)

| Was | Stelle | Wirkung |
| --- | --- | --- |
| Jede Zeichnung wartet nach dem letzten Strich `InkPace.pauseMillis` | `A/SymbolInk.kt` `schedule`, `C/InkTiming.kt` `InkPace` | anfangs 900 ms; ab drei gemessenen Lücken 2,5 × Median, aber nie unter **600 ms** (bis 1500) |
| Begründung im Code: „Es wird nie sofort nach einem Strich geschrieben, weil jeder Strich der erste eines mehrstrichigen Symbols sein kann“ | `docs/ARCHITECTURE.md` → Symbole malen | auch ✓, ∞, ≤ oder ein fertiges ÷ warten |
| Das Warten endet schon heute, sobald ein weiterer Strich aufsetzt (`onInkDown`) oder eine andere Taste gedrückt wird (`flush` in `beforeOtherInput`) | `SymbolInk.onInkDown`, `KeyboardActionListenerImpl` | richtig, bleibt |
| Reihenfolge und Richtung der Striche sind dem Erkenner egal (`$P`-Punktwolke) | `C/InkRecognizer.kt` | → aus „–“ und „>“ in jeder Reihenfolge ist schon heute ein → (Test unten), % aus „/“ und zwei getippten Punkten auch (Etappe 43) |
| Ein Tipp ohne wartende Zeichnung ist ein Buchstabe | `PointerTracker.startsInk` | % mit dem **Punkt zuerst** geht nicht (siehe 5) |
| „-“ allein: es gibt kein Symbol „-“ (Bindestrich), ein waagrechter Strich ergibt „–“ (Gedankenstrich); „_“, „<“, „>“ gibt es nicht | `C/InkSymbols.kt` | „>“ allein ergibt je nach Form „Nicht erkannt“ oder „)“ (Tests unten) |

Der Owner spürt „ca. eine halbe Sekunde“: das passt zur Untergrenze 600 ms, auf die ein geübter Finger nach drei Symbolen fällt.

## 3. Plan

### 3a. Sofort, wenn nichts mehr kommen kann

Nach jedem Strich (und jedem möglichen Punkt) fragt die Tastatur auf ihrem Hilfs-Thread (wie die Vorschau):

1. Der Erkenner nimmt die Zeichnung als vollständiges Symbol X an (Abstand ≤ `ACCEPT_DISTANCE` 1,8, nicht nur als Rückfall eines
   unvollständigen Symbols), und mögliche Punkte blieben Punkte (`InkDots.keeps`).
2. **X kann nicht weitergehen**: keine Vorlage von X liegt nahe (≤ `EXTEND_DISTANCE` 2,0) an einem **Teil** einer Vorlage mit mehr
   Strichen. Ein Teil ist jede echte Teilmenge der Striche einer Vorlage (Reihenfolge und Richtung egal), eingebaut, eigen oder
   persönlich. „–“ ist Teil von =, ÷, →, +; „/“ Teil von % und ×; „→“ Teil von ↔; „%“ Teil von ‰; „)“ Teil von ⇒ (Kopf); „~“ Teil
   von ≈; „°“ Teil von %. Das wird einmal je Satz Vorlagen berechnet, nicht je Strich.
3. **Die Zeichnung selbst** liegt nicht nahe (≤ `PART_DISTANCE` 2,0) an einem solchen Teil (Sicherheitsnetz für eine schlampige
   Zeichnung, die als X durchgeht, aber eigentlich der Anfang von etwas anderem ist).

Nur wenn 1–3 gelten, wird sofort geschrieben (Weg „sofort“). Sonst wartet die Zeichnung wie bisher, nur kürzer (3b). Dauert die
Prüfung länger als die Wartezeit, gilt die Wartezeit; die Antwort des Hilfs-Threads wird verworfen, wenn inzwischen ein Strich
begann, eine Taste kam oder die Zeichnung weg ist (Zähler je Zustand). Es gibt **kein** spekulatives Schreiben: was wartet, steht
nicht im Feld.

Kalibriert mit dem Finger-Modell (`CT/InkFingerModel.kt`, 150 Zeichnungen je Symbol, zufällige Reihenfolge und Richtung, alle echten
Anfänge jeder Zeichnung, 8619 Anfänge): bei 2,0/2,0 wird **kein** Anfang zu früh geschrieben; 2500 von 7950 fertigen Zeichnungen
(31 %) kommen sofort. Sofort kommen (Anteil im Modell): ÷ 100 %, # 100 %, ± 98 %, ≥ 98 %, } 98 %, ≤ 96 %, ? 96 %, ¿ 96 %, ∞ 95 %,
{ 93 %, √ 90 %, × 89 %, ♥ 83 %, & 79 %, ★ 76 %, ‰ 56 %; ÷, ‰, ?, ¿ auch mit getippten Punkten. Es warten weiter, weil sie Teil eines
anderen Symbols sein können: → ← ↑ ↓ ↗ ↘ ↔ ⇒ = ≠ ≈ ~ + – | / \ ° ✓ € $ § @ % ! ¡ ( ) [ ] π. Die Grenzen haben Abstand: bei 1,5/1,8
kämen 5 Anfänge zu früh.

### 3b. Wenn etwas kommen kann: so lange, wie er zwischen zwei Strichen braucht

`InkPace` neu (nach der Kritik, Befund 1): Wartezeit = max(2,2 × Median, 1,5 × längste) der letzten neun Lücken vor einem
**Strich** (Loslassen bis nächstes Aufsetzen innerhalb eines Symbols; Lücken zwischen getippten Punkten zählen nicht mehr, sie sind viel
kürzer), begrenzt auf **300–1500 ms**, anfangs (unter drei Lücken) weiter **900 ms** (Rückmeldung zu Build 14: 500 ms teilten seine
Symbole; der Test `aStrokeAfterMoreThanHalfASecondStillBelongsToTheSymbol` hält das fest). Vorher 2,5 × Median, 600–1500. Beispiel:
Lücken 150–300 ms, Median 220 → max(484, 450) = 484 ms statt 600. Der Faktor 1,5 auf die längste Lücke hält Abstand zu jeder gesehenen
Lücke, weil längere Lücken nie gemessen werden (sie teilen das Symbol).

Nachzügler: Beginnt bis zur doppelten Wartezeit nach einer **nach der Wartezeit** geschriebenen Zeichnung eine neue Zeichnung nahe an ihr
(`InkTap.nearDrawing`) **und ergeben beide zusammen ein anderes angenommenes Symbol** (nach der Kritik: „–“ und ein Kopf → „→“; „!“ nach
„!“ nicht), war die Wartezeit zu kurz: diese Lücke zählt als Lücke, die nächsten Wartezeiten werden länger (1,5 × längste). Das
Geschriebene bleibt, wie es ist (nie automatisch ersetzen). Gezählt wird das für die Diagnose („Nachzügler“).

### 3c. Messpunkte (zuerst, ohne Verhaltensänderung)

`A/SymbolPerf.kt` (neu, nur Zahlen, nur im Speicher): je geschriebener Zeichnung Weg (sofort, Wartezeit, Taste, Chip) und Zeit vom
Loslassen des letzten Strichs bis zum Ende des Schreibens (`SystemClock.uptimeMillis`), Dauer der Prüfung 3a, Nachzügler, die
jetzige Wartezeit. Ausgabe als Zeile `symbolPerf:` in `LatinIME.dump` (`dumpsys input_method`, wie 79b) und als Zeilen in der Karte
„Symbole“ der Diagnose. Keine Symbole, keine Feldinhalte.

Gerätetest mit echten Berührungen: Die Test-App zeichnet über die Gesten-Schnittstelle ihres Bedienungshilfe-Dienstes
(`dispatchGesture`, mehrere Striche mit echten Lücken, Tipps als Punkte; `input motionevent` startet je Ereignis einen Prozess und
ist für Striche zu langsam). Sie notiert, wann das Feld sich ändert; das Skript liest das und die Zeile `symbolPerf:` und schreibt die
Zeiten als INFO-Zeilen, dazu `dumpsys gfxinfo` (Frames während des Malens). Erst auf dem Stand ohne Änderung (vorher), dann mit 3a/3b.

## 4. Abnahme-Zeilen

Ausgangslage überall: Symbole malen an, Layout `de` (qwertz), dunkles Design des Owners, Textfeld, kein Wort davor (wenn nicht anders
gesagt). G = Gerätetest im Emulator mit echten Berührungen, U = Unit-Test mit synthetischen Strichen, O = nur der Owner am Telefon.

| # | Ausgangslage | Aktion | Erwartung | Gegenfall | Nachweis |
| --- | --- | --- | --- | --- | --- |
| 1 | wie oben | „<“ und Strich darunter (≤), Lücke 200 ms | ≤ steht im Feld kurz nach dem Loslassen (Weg „sofort“, unter 100 ms Rechenzeit) | wartet 600 ms | U, G (Zeit, Weg) |
| 2 | wie oben | ∞ in einem Zug | sofort | wartet | U, G |
| 3 | wie oben | „–“ dann „>“ (→), 200 ms Lücke | genau ein →, nach der Wartezeit (anfangs 900, gelernt ≈ 450–500 ms) | „–“ und ein zweites Zeichen; → zweimal | U, G (ein Feldwechsel, nicht vor dem Loslassen) |
| 4 | wie oben | „>“ zuerst, dann „–“ von rechts nach links | genau ein → | „Nicht erkannt“ nach „>“ | U, G |
| 5 | wie oben | „/“, dann zwei Punkte getippt (in beiden Reihenfolgen der Punkte) | genau ein %, nach der Wartezeit (‰ ist möglich) | „/“ und Buchstaben; % plus Buchstabe | U, G |
| 6 | wie oben | „–“ dann zwei Punkte (÷) | ÷ sofort nach dem zweiten Punkt | wartet; „–“ plus Buchstaben | U, G |
| 7 | wie oben | nur „–“, nichts weiter | „–“ nach der Wartezeit | nie; sofort (dann wird ein = unmöglich) | U, G |
| 8 | wie oben | „–“, Pause 1,5 s, dann „>“ | „–“, danach getrennt „)“ oder „Nicht erkannt“ (kein →); Nachzügler gezählt, nächste Wartezeiten länger | ein → aus zwei getrennten Zeichnungen | U, G |
| 9 | wie oben | „–“, dann sofort ein Buchstabe weit weg | „–“ und der Buchstabe, sofort beim Aufsetzen | Buchstabe wartet | G (vorhanden: `flush`) |
| 10 | wie oben | jede Zeichnung des Finger-Modells (alle 47 Symbole, Richtung, Reihenfolge, Größe 1,2–4 Tasten, Zittern) | kein echter Anfang wird sofort geschrieben | Früheingabe | U (`InkCompletionTest`, 8619 Anfänge) |
| 11 | wie oben | schnelle (80 ms) und langsame (700 ms) Striche, kleine (1,5 Tasten) und große (5 Tasten) Zeichnung | gleiches Ergebnis wie normal | schneller Strich wird Tipp | G, U |
| 12 | eigenes Symbol „→→“ (zwei Pfeile) | einen Pfeil malen | wartet (Teil des eigenen Symbols) | → sofort, zweiter Pfeil wird eigenes Symbol | U |
| 13 | Verwechslungen | „/“ allein; „/“ + ein Punkt; „–“ tief („_“); „o“ rund und hoch | „/“; „/“ dann der Buchstabe; „–“; ° | % aus „/“ allein | U, G („/“) |
| 14 | Passwortfeld / gesperrt / Eingabe gesperrt | malen | wie bisher (nicht malen bzw. nichts schreiben) | Schreiben trotz Sperre | vorhandene Tests |
| 15 | Owner am Telefon | seine üblichen Symbole | merklich schneller, keine geteilten Symbole | „–“ + „>“ getrennt | O (Karte „Symbole“ der Diagnose: Wege, Median, Nachzügler) |

## 5. Grenzen und Entscheidungen

- **% mit dem Punkt zuerst**: Ein Tipp ohne wartende Zeichnung ist ein Buchstabe und erscheint sofort; ihn zurückzuhalten, würde jedes
  normale Tippen verzögern, ihn später zu löschen wäre ein automatisches Ersetzen. Daher: % aus „/“ und zwei Punkten in jeder
  Reihenfolge der Punkte, aber der Strich zuerst; ein kleiner Kreis statt eines Punktes geht auch zuerst (er ist ein Strich). Wie
  bisher seit 43.
- **„>“ allein** ergibt weiter „Nicht erkannt“ oder, je nach Form, „)“ (der Kopf von ⇒ gleicht einer Klammer). Vorschlag an den Owner (nicht gebaut): „<“ und „>“ als eigene Symbole (stehen seit 83
  als fehlend in der Roadmap); das würde einen allein gemalten Kopf zu „>“ machen und kann ) und ✓ Treffer wegnehmen.
- **„-“ und „–“**: ein gemalter Strich ist der Gedankenstrich „–“. Der Bindestrich „-“ von der Symbolseite danach kollidiert nicht:
  der Wechsel zur Symbolseite ist eine Taste und schreibt das wartende „–“ zuerst; auf der Symbolseite wird nicht gemalt.
- **Warum nicht „sofort und notfalls ersetzen“**: Das wäre spekulatives Eingeben, das der Owner ausdrücklich nicht will, und ein
  automatisches Ersetzen von Editortext (`CLAUDE.md`).
- **Warum keine Wartezeit unter 300 ms**: Darunter fängt auch ein schneller Finger den nächsten Strich nicht mehr sicher; der Gewinn
  wäre klein, ein geteiltes Symbol teuer.
- **Ehrlich zum Gewinn** (Randnotiz der Kritik): Die drei Symbole, die der Owner am ehesten malt (→, %, =), können weitergehen (↔, ‰, ≠)
  und warten weiter, nur kürzer (3b). „Sofort“ gilt für ≤ ≥ ± ÷ ∞ # ? ¿, meist auch × √ ♥ ★ & ‰ { }.
- Eingriff: `PointerTracker`, `InputLogic`, `RichInputConnection` bleiben unberührt. Geändert werden `SymbolInk`, `SymbolDrawing`,
  `InkPace`, neu `InkCompletion`, `SymbolPerf`; `LatinIME.dump` bekommt eine Zeile.

## 6. Prüfplan

- U: `CT/InkCompletionTest.kt` (Teile, Kalibrierung über das Finger-Modell, keine Früheingabe, Anteil sofort je Symbol, eigene
  Symbole, Verwechslungen, Rechenzeit), `CT/InkTimingTest.kt` (neue Pause, Nachzügler), `AT/SymbolInkTest.kt` (sofort vs. warten mit
  echtem `PointerTracker`, verworfene Antwort nach neuem Strich, genau ein Schreiben), vorhandene Ink-Tests unverändert grün.
- G: `symbol_draw_latency` und `symbol_draw_variants` in `tools/devicetest/devicetest.py`; Kontrolllauf: der letzte Strich jeder
  Zeichnung fällt weg, dann muss jedes Szenario scheitern.
- Messung vorher (Stand `main` 9228c5a0 plus nur Messpunkte) und nachher, Zahlen in `docs/VALIDATION.md`.

## 7. Kritik vor dem Bau und was daraus wurde

Ein Prüfer (eigene Sitzung, Modell Opus, nur lesend) hat das Konzept gelesen. Befunde und Einarbeitung:

1. **hoch, Wartezeit lernt zu kurz** (Punkt-Lücken mischen sich ein; längere Lücken werden nie gemessen; Nachzügler-Regel schon bei
   bloßer Nähe): Punkt-Lücken zählen nicht mehr; Faktoren 2,2 × Median und 1,5 × längste, Untergrenze 300 statt 250, Anfang 900; ein
   Nachzügler zählt nur, wenn beide Zeichnungen zusammen ein anderes angenommenes Symbol ergeben (`Sink.joins`). Tests: `InkTimingTest`.
2. **mittel, Eichung nur am Finger-Modell**: zusätzlicher Test mit 40 persönlichen Symbolen aus Finger-Zeichnungen
   (`InkCompletionTest.noTrueBeginningIsTakenAsFinishedWithPersonalTemplatesOfAShakyHand`): 0 von 2690 Anfängen zu früh; der Anfang, den
   nur die Teile-Prüfung zurückhielt und der am weitesten von einem Teil lag, lag bei 1,57 (Grenze 2,0). ⇒ mit dem Kopf zuerst ist im
   Finger-Modell enthalten (Reihenfolge zufällig). Ein eigenes Symbol, das ein sonst fertiges Symbol fortsetzt, lässt es warten (Test).
3. **niedrig, Warteschlange**: eine Prüfung für einen älteren Zustand wird auf dem Hilfs-Thread übersprungen (`SymbolInk.currentSerial`);
   Doppel- oder Verlust-Eingaben sah der Prüfer nicht (Zähler bei jedem Strichbeginn, Schreiben, Verwerfen, Chip).
