# Leiste v2: Konzept (Etappe 88)

Referenz für die feste Leiste und das Anpassen in der Tastatur. Geschrieben vor dem Code, danach gepflegt: Wer die
Leiste ändert, ändert zuerst dieses Dokument. Die Code-Landkarte steht in `docs/ARCHITECTURE.md` → „Feste Leiste“,
die Prüfnachweise in `docs/VALIDATION.md` → „Etappe 88“.

## 1. Was der Owner will, und was dem widerspricht

**Will (Build 41, Xiaomi 15, eigenes dunkles Design):** Die Leiste zeigt jedes Werkzeug deutlich, die Symbole liegen je
nach Anzahl in gleichmäßigen Abständen (mehr Symbole, weniger Abstand; weniger, mehr Abstand). Anpassen heißt: ein
Symbol mit dem Finger anfassen, dorthin ziehen, wo es hin soll, loslassen, und es bleibt dort. Das Ziehen fühlt sich an
wie das Verschieben eines Symbols auf dem Startbildschirm.

**Widerspricht dem (Gegenanforderungen):**
- Scrollen in der Leiste, ein Aufklapp-Pfeil.
- Antippen als Hauptweg zum Platzieren (Antippen bleibt nur als Ersatz für Screenreader und Hände, die nicht ziehen können).
- Ein Symbol, das unsichtbar ist oder wie ein Loch aussieht (Build 41: AGENT und SKILL_1 schwarz auf fast schwarz).
- Ein Element, das von selbst die Position wechselt (kein Springen beim Feldwechsel, beim Tippen, beim Ziehen eines
  anderen Symbols; Plätze hängen nur von Anzahl und Kreis-Ende ab).
- Der System-Ziehschatten, der zurückschnappt (Systemziehen im Tastaturfenster, siehe 6).
- Alles, was nur in einem simulierten Test funktioniert: das Ziehen wird mit echten Berührungen geprüft.

## 2. Aussehen: Sichtbarkeit und Abstände

**Ursache der schwarzen Symbole (Befund 1):** Die Symbole der Leiste werden mit `Colors.setColor(…, TOOL_BAR_KEY)` gefärbt,
das ist eine Farbliste im Modus MULTIPLY. Alle HeliBoard-Symbole sind weiß gefüllt (weiß mal Farbe = Farbe). Die
Symbole der Fork `ic_agent_actions` (Füllung `#FF000000`), `ic_skill_1…3` und `ic_agent_dictate` (Strich `#FF000000`)
sind schwarz: schwarz mal Farbe = schwarz, in jedem Design. Auf hellem Grund fällt das nicht auf, auf dunklem Grund ist
das Symbol ein Loch. `ic_edit` (Zeichen-Kunst) hat eine graue Füllung (`@color/foreground_weak`), also dunkelgrau.
Behebung: alle Symbole der Leiste sind weiß gefüllt; zusätzlich gilt die Kontrastregel unten.

**Kontrastregel (jedes Design):** Der Grund ist die Streifenfarbe (`STRIP_BACKGROUND`, über `MAIN_BACKGROUND`
gelegt). Jede Farbe der Farbliste der Symbole (normal, gedrückt, aktiviert) wird so verschoben (zu Weiß oder Schwarz, der
Farbton bleibt, solange es geht), dass der WCAG-Kontrast mindestens 4,5:1 beträgt; nie unter 3:1. Die Rechnung nutzt
`RgbColors.contrast` (dieselbe wie die Beschriftungen der RGB-Beleuchtung), und die RGB-Beleuchtung der Leistenknöpfe
nimmt dieselbe Farbliste als Grundlage (`RgbStrip.tintOf`).

**Nicht verfügbare Werkzeuge:** Sie bleiben am Platz (Positionen wechseln nie zwischen Feldern). Aussehen „aus“: die
echte Farbe aus 50 % der normalen Symbolfarbe über dem Grund gemischt (kein Alpha der Ansicht), mindestens 2:1 gegen den
Grund; dazu ein kleines Schloss unten rechts am Symbol (zweites Merkmal, nicht nur Farbe). Größe und Platz bleiben
gleich. Der linke Knopf und der Kreis (Sprache) folgen derselben Kontrastregel.

**Tipp auf ein nicht verfügbares Werkzeug:** kein toter Tipp. In der Leiste erscheint 2,5 s eine Zeile (anstelle der
Werkzeuge, nicht blockierend, nächste Berührung räumt sie weg) mit dem echten Grund, aus derselben Stelle wie die
Sperre (`BarTools.unavailableReason`): „In diesem Feld nicht verfügbar: privates Feld“ (Passwortfeld, Inkognito,
Feld ohne persönliches Lernen), „… gesperrtes Gerät“, „… Zahlenfeld“. TalkBack liest dieselbe Zeile
(Live-Region) und die Beschreibung des Knopfs enthält den Grund.

**Abstände (Entscheidung):** Gleich sind die **Mitten** der Plätze von Rand zu Rand (links Raster-Knopf, dann die Werkzeuge, links und rechts 6 dp Rand
der Zellen), und alle Symbole werden in dieselbe Fläche skaliert (24 dp, ab 6 Werkzeugen 20 dp; im flachen Stil bleiben die eigenen, für den ganzen 34-dp-Knopf
gezeichneten Symbole, wie in den Referenzbildern). Damit sind auch die sichtbaren Lücken zwischen gleich großen Symbolen gleich; nur der Kreis am Ende (34 dp) ist breiter als ein
Symbol, die Lücke daneben ist darum 5 dp kleiner. **Erst anders gebaut, dann verworfen:** Ich hatte gleiche Lücken zwischen den sichtbaren Rändern gebaut (Plätze
dann je nach Kreis am Ende verschoben). Die Referenzbilder des Owners (flacher Stil, `FlatStyleRenderTest`: Mitten bei 75,5, 338, 600, 861, 1124,5 px, der Kreis
am Ende eingerechnet) zeigen gleiche **Mitten**; die Lösung der „Löcher“ ist die Sichtbarkeit, nicht ein anderer Abstand. Plätze hängen nur von der Anzahl ab (nie vom
Werkzeug, nie vom Feld), das bleibt eine Zusage an die Muskelerinnerung. 0 Werkzeuge: nur der Raster-Knopf, nichts sieht kaputt aus; 1 bis 2 Werkzeuge sind über die ganze
Breite verteilt, nicht links gehäuft. Berührflächen bleiben mindestens 40 × 40 dp (voll; kompakt 30 dp, wie bisher).

## 3. Anpassen: die vollständige Bedienung

Alles läuft im selben Ansichtsbaum (der Behälter ist `ToolGridView`, das ganze Tastaturfenster), kein Systemziehen, kein
neues Fenster. Die reine Logik steht in `BarDrag` (ohne Ansicht, mit Uhr zum Prüfen).

**B1 Einstieg.** „Anpassen“ im Raster; Langdruck (≥ 350 ms) auf einen Platz der Leiste ohne eigene Langdruck-Aktion;
Langdruck auf den linken Knopf. Der Modus zeigt: die Leiste mit umrandeten Plätzen, den Hinweis „Zum Personalisieren
halten und ziehen“, das Raster aller Werkzeuge (Pillen; was in der Leiste ist, ist markiert), links das Häkchen zum
Beenden, im Hinweisbereich „Rückgängig“ und „Zurücksetzen“. Zurück-Taste und Ausblenden der Tastatur beenden ebenfalls
(alles ist schon gespeichert).

**B2 Aufnehmen.** Auf einer Pille oder einem Symbol der Leiste: Halten ≈ 180 ms (kurzes Haptik-Tick) **oder** Bewegung ≥
Touch-Slop auf einem Leistensymbol (jede Richtung) **oder** seitliche Bewegung ≥ Slop auf einer Pille. Eine senkrechte
Bewegung auf dem Raster vor dem Halten scrollt das Raster (Scrollen und Ziehen streiten nicht). Nach dem Aufnehmen folgt
eine Kopie des Symbols (um 15 % größer, mit Schatten) dem Finger genau (Versatz bleibt, auf das Tastaturfenster begrenzt),
gezeichnet über Leiste und Raster, als Kind desselben Behälters.

**B3 Beim Ziehen.** Ist der Finger in der „Leistenzone“ (Leiste plus 40 dp nach unten, nach oben bis zum Fensterrand),
öffnet sich eine Lücke an der Einfügestelle, die dem Finger-x am nächsten liegt; die anderen Symbole rücken in ≤ 150 ms
beiseite. Einfügen geht an jeder Stelle (vor dem ersten, zwischen zwei, nach dem letzten). Die Einfügestelle ist der
Platz, dessen Mitte dem Finger am nächsten liegt, in der Anordnung der Leiste mit der neuen Anzahl (kein Rückkopplungs-
Flimmern: die Anordnung hängt nur von der Anzahl ab). Verlassen der Zone schließt die Lücke (ein Symbol aus der Leiste
ist dann „draußen“: die anderen rücken zusammen). Der linke Knopf bleibt vorn; die Sprachtaste ist ein normales Werkzeug.

**B4 Ablegen (ACTION_UP).** In der Zone: einfügen/verschieben an die Vorschau-Stelle, sofort gespeichert, die Kopie
gleitet in den Platz. Außerhalb: ein Werkzeug aus der Leiste wird aus der Leiste genommen (gleitet zur Pille, Entfernen
gespeichert); eins aus dem Raster geht zur Pille zurück (nichts ändert sich). ACTION_CANCEL (System nimmt die Berührung,
Tastatur wird mitten im Ziehen versteckt): zurück zum Stand vor dem Ziehen, nichts gespeichert.

**B5 Grenzen und Rückmeldung.** Höchstens 8 Werkzeuge. Wird ein neuntes über die Leiste gezogen, zeigt die Leiste „voll“ (die
Plätze wackeln kurz, Hinweiszeile „Die Leiste ist voll – zieh erst ein Werkzeug heraus“), das Ablegen tut nichts. Eine leere
Leiste ist erlaubt (nur der Raster-Knopf). Haptik: Aufnehmen, jeder Wechsel der Lücke, Ablegen. Ein im Feld nicht verfügbares
Werkzeug lässt sich trotzdem anordnen (Anordnen führt nie ein Werkzeug aus).

**B6 Antippen (Ersatz).** Antippen einer Pille, die nicht in der Leiste ist, hängt sie hinten an; Antippen einer Pille in
der Leiste oder eines Leistensymbols nimmt sie heraus. TalkBack-Aktionen wie bisher (hinzufügen, entfernen, nach links,
nach rechts), Änderungen werden angesagt.

**B7 Mehrere Finger, Drehen, Schreibrichtung.** Ein zweiter Finger während des Ziehens wird ignoriert. Drehen oder
Neuaufbau der Tastatur im Anpassen-Modus beendet den Modus, das Gespeicherte bleibt. Bei Rechts-nach-links zählen die Stellen
in sichtbarer Reihenfolge von links.

**B8 Speicher.** Weiter `agent_bar_tools`; der Sortier-Dialog der Einstellungen arbeitet unverändert.

**Zusätze (Befund 3).** „Rückgängig“ nimmt die letzte Änderung zurück (eine Ebene; Ablegen, Antippen, Zurücksetzen zählen);
„Zurücksetzen“ stellt die Standard-Leiste her, nach einer Bestätigung im Anpassen-Bereich selbst (Zeile „Standard
wiederherstellen? Ja / Nein“, kein Systemdialog), und ist mit „Rückgängig“ wieder aufhebbar. Die erste Hilfe ist der Hinweis
„Zum Personalisieren halten und ziehen“.

## 4. Die Zustände einer Berührung (Zustandsmaschine von `BarDrag`)

`IDLE` → `down` auf Pille/Leistensymbol → `PRESSED`. Aus `PRESSED`: Zeit ≥ Haltezeit → `DRAGGING`; Bewegung ≥ Slop auf
Leistensymbol → `DRAGGING`; seitlich ≥ Slop auf Pille → `DRAGGING`; senkrecht ≥ Slop auf Pille → `SCROLLING` (die Logik
ist fertig, das Raster scrollt); `up` vor allem → Antippen (Leistensymbol: herausnehmen; Pille: das normale Klicken
der Pille). `DRAGGING`: Finger in Zone → Lücke an Index; außerhalb → Lücke zu; `up` → Ablegen; `cancel` → zurück.
Ein zweiter Zeiger wird ignoriert. Jede Berührung endet in `IDLE`.

## 4a. Zusätze zum Anpassen (Entscheidungen beim Bau)

- **Hochheben vom Raster:** Die Kopie sitzt unter dem Finger (nicht mit dem Versatz zur Pille, denn die Pille ist halb so breit wie
  der Bildschirm). Vom Leistensymbol aus bleibt der Versatz klein und wird nicht gesondert gerechnet.
- **Halten und auf der Stelle loslassen** (ohne dass der Finger sich um den Touch-Slop bewegt hat) legt das Werkzeug zurück:
  nichts ändert sich. Grund: Eine Pille, die durch Scrollen nah an der Leiste steht, läge sonst in der Zone und würde ohne
  Absicht eingefügt.
- **Antippen** (kurze Berührung) einer Pille oder eines Leistensymbols wird im Behälter selbst erledigt (anhängen/herausnehmen),
  nicht über den Klick der Pille: der Klick läuft über einen `post`, den eine Ansicht ohne Fenster nie ausführt (Test), und ein
  vom Behälter abgebrochenes Kind klickt nicht zweimal. Die Klicks der Pillen bleiben für Screenreader.
- **Rückgängig und Zurücksetzen** im Hinweisbereich des Anpassen-Modus (Befund 3), Zurücksetzen mit Frage „Ja / Nein“ im Raster.
- **Tippen auf ein nicht verfügbares Werkzeug** sagt den Grund (Abschnitt 2), in der Leiste und im Raster.
- **Gleiten:** beim Öffnen und Schließen der Lücke gleiten die Symbole in 140 ms an ihren Platz (nur Bewegung, kein Zustand).
- **Das Raster scrollt weiter**, wenn der Finger auf einer Pille senkrecht zieht, bevor die Haltezeit um ist; ein Ziehen von Hand in die Leiste
  braucht darum Halten oder eine seitliche Bewegung.

## 4b. Kontextwerkzeuge: Rückgängig bietet Wiederholen an

**Auftrag (Owner):** „Rückgängig sollte beim Benutzen zu ‚Wiederholen‘ werden, denn es wurde ja schon rückgängig gemacht.“
**Widerspricht:** ein Umschalten des Symbols (dann ginge wiederholtes Rückgängig nicht mehr: Tippen, Tippen, Tippen); ein Werkzeug,
das den Platz wechselt oder breiter wird; ein Wiederholen, das auch bei einer Taste angeboten wird, die der Nutzer schon in der Leiste hat.

**Entscheidung (Korrektur am Entwurf des Leads):** Der Entwurf teilte den Platz in zwei Hälften (↶ ↷). Ein Platz ist 40 dp breit (Kompakt
30 dp), eine Hälfte also 20 dp, weit unter den geforderten 36 bis 40 dp; verbreitern darf man ihn nicht (nichts darf wandern). Darum
gilt überall die Rückfalllösung des Entwurfs: **Nach einem Rückgängig trägt das Symbol einen kleinen Wiederholen-Pfeil (oben rechts am
Symbol)**; Wiederholen ist der **Langdruck auf Rückgängig** (der besteht seit jeher, `getCodeForToolbarKeyLongClick`: UNDO → REDO) und
für Screenreader die Aktion „Wiederholen“; die Beschreibung sagt „…, Halten zum Wiederholen“. Ein Tippen bleibt Rückgängig (so
geht wiederholtes Rückgängig). Wiederholen und Rückgängig wechseln sich damit beliebig ab, der Pfeil bleibt.
**Zwei getrennte Knoten** (Entwurf, Screenreader) gibt es nicht: ein Knoten mit der Aktion; zwei Knoten bräuchten einen virtuellen
Ansichtsbaum für ein 20 dp breites Ziel.
**Heuristik:** Die Tastatur weiß nicht, ob die App etwas zu wiederholen hat (Strg+Z und Strg+Umschalt+Z sind Tastenereignisse, die App
entscheidet). Das Angebot beginnt mit einem Rückgängig und endet mit jeder Eingabe, die den Text ändert (Zeichen, Löschen,
Einfügen, Textaktion), einem neuen Feld oder dem Verstecken der Tastatur (`UndoOffer`). Hat der Nutzer Wiederholen selbst in der
Leiste, geschieht nichts. Außerhalb der Leiste ändert sich nichts (das Raster hat Rückgängig und Wiederholen getrennt).
**Offene Frage (Vorschlag mit Rückfrage):** Nach einem Rückgängig zeigt die Leiste oft Wörter statt der Werkzeuge (der Text hat sich
geändert); dann ist das Symbol bis zum Tippen auf den Pfeil „Werkzeuge zeigen“ nicht sichtbar. Die Werkzeuge solange stehen zu lassen,
wie Wiederholen angeboten wird, wäre bequem, ändert aber die Rangfolge von Wörtern und Werkzeugen und bleibt darum aus.

## 4c. Zusätze, die nicht bestellt waren

Gebaut (harmlos, in sich geschlossen): Rückgängig und Zurücksetzen im Anpassen-Modus; der Grund bei einem nicht verfügbaren Werkzeug;
das Gleiten der Plätze; das Schloss am nicht verfügbaren Werkzeug; der Wiederholen-Pfeil (4b). Nicht gebaut, mit Rückfrage im Bericht:
Werkzeuge sichtbar lassen, solange Wiederholen angeboten wird; Zwei-Hälften-Platz bei höchstens drei Werkzeugen; das Raster
nach zuletzt benutzten Werkzeugen ordnen.

## 5. Zu prüfen (Konsistenzliste)

Einstellungsseite der Leiste (Sortier-Dialog), Skill-Taste anheften, flache Voreinstellung, Schalter „Feste Leiste“ aus,
Inhalte der Leiste (Vorschläge, Chips, Autofill): in `docs/VALIDATION.md` → „Etappe 88“ mit Nachweis je Punkt.

## 6. Warum kein Systemziehen

`startDragAndDrop` im Tastaturfenster (IME-Fenster) braucht ein Zielfenster, das `ACTION_DRAG_STARTED/DROP` bekommt. Das
Fenster einer Tastatur ist nicht fokussierbar und liegt neben dem App-Fenster; das System verteilt das Ziehen an das
Fenster unter dem Finger und kann es dort verwerfen; auf dem Gerät des Owners folgte der Schatten dem Finger und schnappte
zurück, ohne dass ein Ablegen ankam (Build 41). Das Ziehen mit der eigenen Berührungsverarbeitung braucht kein anderes
Fenster und läuft in einem Behälter.
