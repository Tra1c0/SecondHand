# Leertaste als bewegte Höhenlinien – Entscheidungsvorlage

Stand 2026-11-20. Auftrag des Owners: den Architekturvorschlag eines anderen KI-Beraters (ChatGPT) für eine
reaktive, sparsame Höhenlinien-Animation auf der Leertaste unabhängig prüfen. Nichts hiervon ist gebaut oder
gemessen.

Grundlagen dieser Vorlage:

- `docs/LEERTASTE-BESTAND.md`: was der Code heute macht (gelesen am Stand f8ca092, mit Datei und Zeile).
- `docs/LEERTASTE-RECHERCHE.md`: Recherche mit Quellen. Dort ist jede Aussage als belegt (Primärquelle),
  plausibel oder unbelegt markiert. Viele Seiten waren aus der Arbeitsumgebung nicht erreichbar
  (source.android.com, perfetto.dev, skia.org, Qualcomm-Doku); solche Punkte sind dort „nur Suchauszug“.

Kennzeichnung in diesem Text: **[belegt]** = Primärquelle oder eigener Code gelesen, **[plausibel]** = Schluss
ohne direkte Quelle, **[offen]** = nur am Gerät entscheidbar.

## 1. Kurzfassung

1. Der Vorschlag optimiert die falsche Größe. Er dreht sich um die Kosten je Pixel (Shader, Präzision, Textur).
   Bei rund 70.000 Pixeln (etwa 2 % des Bildschirms) entscheiden voraussichtlich die festen Kosten je Bild und
   die Bildrate, die das Panel deswegen halten muss **[plausibel; eine direkte Quelle dafür gibt es nicht]**.
2. Der größte nachgewiesene Verschwender steht im heutigen Code und kommt im Vorschlag nicht vor: Für das Licht
   wird bei jedem Bild die ganze Tastatur neu gezeichnet (`invalidateAllKeys`, ganze Ebene), 30- oder 60-mal
   pro Sekunde **[belegt]**. Das zu beheben hilft jeder Variante und der vorhandenen RGB-Beleuchtung.
3. Die Leerlauf-Idee „15–20 Bilder pro Sekunde“ ist vermutlich die schlechteste Wahl im Vorschlag: sichtbar
   gestuft, und laut Android-Quelltext über der Schwelle (10 Hz), ab der eine Ebene als „häufig aktualisiert“
   gilt, sowie über der Rate, bei der der Grafikchip zwischen zwei Bildern einschlafen kann **[belegt für den
   Quelltext und einen älteren Qualcomm-Treiber; Wirkung auf dem Xiaomi 15 offen]**.
4. AGSL ist als Zeichenweg vertretbar, aber aus anderen Gründen als behauptet, und mit zwei Fehlern im
   Vorschlag: Ableitungsfunktionen gibt es in AGSL nicht **[belegt]**, und „half“-Präzision ist für Zeit und
   Koordinaten schädlich **[belegt]**.
5. Empfehlung: in Stufen und mit Messung zuerst. Erst messen können, dann das Neuzeichnen auf die Leertaste
   begrenzen, dann das Bewegungsmodell (rein rechnerisch, testbar), zuletzt der Zeichenweg – AGSL gegen eine
   Vektor-Variante auf dem Prozessor am Gerät vergleichen statt vorab festlegen.
6. Eine Entscheidung braucht den Owner: „im Leerlauf ununterbrochen bewegt“ ist das Teuerste an dem Wunsch. Die
   Messung soll zeigen, was es kostet; bis dahin empfehle ich „lebt beim Tippen und einige Sekunden danach,
   kommt dann zur Ruhe“.

## 2. Was heute im Code steht

Alles **[belegt]**, Einzelheiten in `docs/LEERTASTE-BESTAND.md`:

- Die Höhenlinien der Leertaste sind das Muster „Höhenlinien“ der RGB-Beleuchtung: eine feste Kachel von
  384 × 384 Pixeln, die nur verschoben wird. Die Linien verformen sich nie; bewegt wird das Farbfeld darunter
  (Raster 48 × 32, auf einem Hintergrund-Thread gerechnet).
- Es gibt kein eigenes Zeichenelement für die Leertaste. Das Licht hängt in `KeyboardView.onDrawKey`; die
  Tastatur ist eine Hardware-Ebene, und je Bild wird die ganze Ansicht ungültig gemacht.
- Bilder kommen über `Choreographer`, nur 30 oder 60 Hz; Pausen bei unsichtbarer Tastatur, Energiesparmodus,
  ausgeschalteten Android-Animationen, offenen Panels.
- Tipp-Reaktionen: bis zu acht gleichzeitige Impulse als reine Zeitfunktionen (Aufblitzen, Ring, kurz
  schneller), ohne Dämpfung oder Federverhalten, nur auf die Farbe. Übergeben wird die Taste, nicht die
  Berührungsposition; die Position liegt in `MainKeyboardView.onTouchEvent` vor.
- Moderne Grafik-Schnittstellen (AGSL, eigene Oberflächen) nutzt die App nicht. Gemessen wird nur Rechenzeit,
  kein Grafikchip, kein Strom.

## 3. Audit des Vorschlags

### A. Vorberechnete Höhenkarte

- Energie: bei dieser Fläche voraussichtlich unerheblich, ob Textur oder Formel **[plausibel]**. Die im Auftrag
  genannten Größen (Cache, Speicherbandbreite, ALU) spielen erst bei viel mehr Pixeln eine Rolle; belegen lässt
  sich das hier nur durch Messung **[offen]**.
- Qualität spricht gegen die Textur: AGSL bietet für Eingabe-Texturen nur nächste oder lineare Filterung, keine
  Mipmaps **[belegt]**. Lineare Zwischenwerte geben Höhenlinien mit sichtbaren Knicken an den Texelgrenzen; eine
  8-Bit-Höhenkarte kann Stufen zeigen **[plausibel]**.
- Besser: ein analytisches Feld (Summe weniger Sinuswellen oder zwei Oktaven Wertrauschen) mit analytisch
  berechnetem Gefälle. Das Gefälle wird ohnehin gebraucht (siehe D).

### B. Verformung des Definitionsbereichs (Domain Warping)

- Als Prinzip tragfähig und ohne Simulationszustand auswertbar.
- Übersehen: Ist die Verformung zu steil (Betrag mal Ortsfrequenz in der Nähe von 1), faltet sich das Feld;
  Linien reißen oder kreuzen sich. Amplituden müssen begrenzt werden, auch in der Summe mit den Impulsen.
- „Keine erkennbaren Schleifen“ gelingt mit nicht ganzzahlig verwandten Frequenzen. Die Phasen müssen auf dem
  Prozessor mit hoher Genauigkeit gerechnet, auf eine Periode zurückgeführt und als Parameter übergeben werden;
  eine stetig wachsende Zeit im Shader verliert Genauigkeit (siehe D).

### C. Analytische Berührungsimpulse

- Für das Antippen ausreichend: Lineare Wellen überlagern sich ohne Fehler, eine Summe gedämpfter Ringwellen ist
  dafür das richtige Modell, und „mehrere Berührungen überlagern sich“ ergibt sich von selbst. Sprünge entstehen
  nur, wenn ein Impuls nicht bei null mit Steigung null beginnt oder beim Entfernen aus der Liste noch sichtbar
  ist.
- Nicht ausreichend für den aufliegenden oder wandernden Finger („Anziehung“): Das ist keine Funktion eines
  einzelnen Zeitpunkts. Nötig ist ein kleiner Zustand je Berührung (Position, Auslenkung, Geschwindigkeit), als
  gedämpfte Feder in geschlossener Form fortgeschrieben, also unabhängig von der Bildrate. Das sind wenige
  Zahlen, kein Simulationsgitter.
- Die Obergrenze 4–8 ist unkritisch; der Code begrenzt schon heute auf acht **[belegt]**.

### D. Zeichnen mit AGSL

- Verfügbar ab Android 13 (API 33) **[belegt]**; die App unterstützt ältere Versionen, es braucht also einen
  zweiten Weg oder den heutigen als Rückfall.
- Fehler im Vorschlag: Die Ableitungsfunktionen (`dFdx`, `dFdy`, `fwidth`), mit denen man üblicherweise gleich
  breite, geglättete Höhenlinien zeichnet, gibt es in AGSL nicht **[belegt aus dem Skia-Quelltext]**. Die
  Linienbreite muss über das selbst berechnete Gefälle normiert werden.
- Fehler im Vorschlag: `half` ist mittlere Präzision **[belegt]**. Für eine wachsende Zeit und für
  Pixelkoordinaten reicht sie nicht; die Bewegung würde nach kurzer Laufzeit stufig. Ein Energiegewinn daraus
  ist laut Android-Doku nicht garantiert. Wenn überhaupt, dann nur für Farbwerte.
- Richtig am Vorschlag: ein Durchgang, keine Unschärfe, Parameter je Bild neu setzen, ohne das Programm neu zu
  übersetzen (das übersetzte Programm bleibt erhalten **[belegt]**).
- Entscheidend und im Vorschlag nicht erwähnt: Wo gezeichnet wird. Im bestehenden Tastatur-Zeichnen bliebe es
  beim Neuzeichnen der ganzen Tastatur je Bild. Nötig ist ein eigenes kleines Zeichenelement über der Leertaste
  (oder ein eng begrenztes Rechteck). Android zeichnet dann nur dessen Zeichenliste neu und begrenzt die Arbeit
  des Grafikchips auf das geänderte Rechteck **[belegt]**; ob die Bildschirm-Zusammensetzung daraus auch Energie
  spart, ist **[offen]**.

### E. Angepasste Bildrate

- Leerlauf 15–20 Bilder/s: Android behandelt eine Ebene ab 10 Hz als häufig aktualisiert **[belegt]**; damit
  kann sie das Panel über seiner Ruherate halten **[plausibel]**. Ein älterer Qualcomm-Treiber legt den
  Grafikchip erst nach 80 ms ohne Arbeit schlafen **[belegt für SM8450, nicht für den 8 Elite]**; bei mehr als
  12,5 Bildern/s schliefe er dann nie. Gleichzeitig sind 15–20 Bilder/s bei dünnen, kontrastreichen Linien
  sichtbar gestuft, sobald sich eine Linie mehr als etwa einen Pixel je Bild bewegt.
- „Weniger Bilder heißt weniger Verbrauch“ gilt also nicht gleichmäßig: Zwischen 60 und 20 spart man Rechen-
  und Zeichenarbeit, aber Panel und Grafikchip bleiben wach; erst unter den genannten Schwellen oder bei
  Stillstand können sie ruhen **[plausibel, am Gerät zu messen]**.
- Beim Tippen ist die Lage anders: Nach jeder Berührung hält Android die Bildrate etwa drei Sekunden hoch
  **[belegt im Quelltext; ob das für Tastaturfenster auf HyperOS gilt, ist offen]**. Während einer Tippfolge
  kostet die Animation dann vor allem Rechen- und Zeichenarbeit, das Panel läuft ohnehin schnell. Teuer ist die
  Zeit, in der die Tastatur sichtbar ist und niemand tippt.
- Steuermöglichkeit: Ab Android 15 kann ein Zeichenelement eine gewünschte Bildrate anmelden; kleine Elemente
  (bis 7 % der Fläche) bekommen laut Quelltext von sich aus die niedrige Kategorie **[belegt; ob das Xiaomi 15
  die nötige Gerätefunktion hat, ist offen]**. Die Tastatur stimmt dabei nur mit; die App im Vordergrund kann
  eine höhere Rate verlangen.

## 4. Antworten auf die zehn Fragen

1. **AGSL die effizienteste Lösung?** Nicht belegbar. Für die Linienqualität ist es der beste Weg innerhalb des
   normalen Android-Zeichnens; energetisch unterscheiden sich die vernünftigen Wege bei dieser Fläche
   voraussichtlich wenig **[plausibel]**. Eigene OpenGL- oder Vulkan-Oberflächen sind hier schlechter: zusätzliche
   Ebene, für eine `SurfaceView` zusätzliche Überblendung bei jeder Änderung **[belegt]**, Nutzbarkeit im
   Tastaturfenster unbelegt, viel Code.
2. **Vorberechnete Höhenkarte effizienter?** Energetisch voraussichtlich egal, qualitativ schlechter (siehe 3 A).
3. **Analytische Impulse ausreichend?** Für Antippen ja, für Halten und Ziehen nein (siehe 3 C).
4. **Niedrige Leerlauf-Bildrate sinnvoll?** In der vorgeschlagenen Höhe nein. Besser: Bewegung so langsam, dass
   wenige Bilder genügen (unter 10 je Sekunde, weniger als ein Pixel Weg je Bild), oder Stillstand nach einer
   Auslaufzeit.
5. **Wie arbeitet Androids Bildplanung?** Siehe 3 E und `docs/LEERTASTE-RECHERCHE.md` Abschnitt C. Die
   Panel-Rate wird aus den Wünschen aller sichtbaren Ebenen gebildet; HyperOS-Sonderregeln sind unbelegt.
6. **Ist der Grafikchip sparsamer?** Nicht allgemein. Für winzige Lasten gibt es keine Quelle; Aufwecken und
   Wachbleiben des Chips können mehr kosten als die Rechnung selbst **[plausibel]**. Deshalb wird die
   Prozessor-Variante (Abschnitt 5, V3) mitgemessen.
7. **Wird die ganze Tastatur neu gezeichnet?** Heute ja **[belegt]**. Mit eigenem Zeichenelement nein
   **[belegt für Android allgemein]**.
8. **Wie groß ist der Mehrverbrauch?** Unbekannt. Es gibt keine belastbare Zahl für diese Fläche und dieses
   Panel; jede Watt- oder Prozentangabe wäre erfunden.
9. **Welche Messwerkzeuge gehen auf dem Xiaomi 15?** Fein aufgelöste Energie-Schienen gibt es laut Google nur
   auf Pixel-Geräten. Android GPU Inspector führt kein Xiaomi und keinen Snapdragon 8 Elite in seiner Liste
   (Stand Februar 2026) **[belegt]**; Snapdragon Profiler ist unbelegt. Ohne Root bleiben: der Batteriestrom
   über `BatteryManager` (grob, herstellerabhängig), Bildstatistiken (`dumpsys gfxinfo`, Perfetto) und eigene
   Zähler in der App. Treiberdateien des Grafikchips ohne Root: ausprobieren.
10. **Hybridmodell überlegen?** Ja, in diesem Sinn: analytisches Feld und analytische Impulse für das Bild, ein
    winziger Federzustand je Berührung für Halten und Ziehen. Vorberechnete Strömungsfelder bringen hier nichts.

## 5. Varianten im Vergleich

| | Kern | Energie (Erwartung) | Linienqualität | Aufwand | Wartung |
| --- | --- | --- | --- | --- | --- |
| V0 | heute: starre Kachel, ganze Tastatur je Bild | am höchsten | Linien starr | – | – |
| V1 | wie heute, aber nur die Leertaste neu zeichnen | deutlich besser als V0 **[plausibel]** | starr | klein | gering |
| V2 | eigenes Element + AGSL, analytisches Feld | wie V1 oder etwas darüber **[offen]** | beste, je Pixel exakt | mittel | zwei Wege (ab/unter Android 13) |
| V3 | eigenes Element, Höhenlinien als Vektoren auf dem Prozessor (grobes Raster, Linienzüge) | **[offen]**, kein Aufwecken durch Shader, aber Linien müssen gerastert werden | gut, vom Raster begrenzt | mittel | ein Weg für alle Versionen |
| V4 | eigene OpenGL-/Vulkan-Oberfläche | schlechter **[plausibel]** | wie V2 | groß | hoch |
| V5 | vorberechnete Bildfolge | gering je Bild, aber Schleife sichtbar, keine echte Reaktion | begrenzt | klein | gering |

Berührungslatenz ist in V1 bis V3 unkritisch, solange die Animation nichts im Eingabeweg rechnet: Die Berührung
setzt nur ein paar Zahlen und fordert ein Bild an. V4 und V5 scheiden aus.

## 6. Empfehlung

1. Messen können, bevor etwas umgebaut wird (Abschnitt 7).
2. V1 zuerst: das Neuzeichnen auf die Leertaste begrenzen. Das ist unabhängig vom späteren Zeichenweg richtig
   und verbessert die vorhandene Beleuchtung.
3. Bewegungsmodell als reine Rechnung in `agent-core`: Feld, Verformung, Impulse, Federzustand, Zeit in
   geschlossener Form. Vollständig testbar ohne Gerät (Stetigkeit, keine Faltung, Unabhängigkeit von der
   Bildrate, Überlagerung).
4. Zeichenweg am Gerät entscheiden: V2 und V3 mit demselben Bewegungsmodell bauen und messen. Meine Erwartung
   ist V2 wegen der Linienqualität; sicher bin ich mir bei der Energie nicht.
5. Bildplanung: volle Rate nur während einer Reaktion; danach auslaufen; im Leerlauf entweder sehr langsam mit
   wenigen Bildern oder Stillstand. Die vom Owner gewünschte Dauerbewegung bleibt als wählbare Stufe, mit dem
   gemessenen Preis daneben.

Offengelegte Unsicherheiten: die Dominanz der festen Kosten je Bild, das Verhalten des Panels bei niedrigen
Raten, HyperOS-Sonderregeln, das Zeichen-Backend des Geräts, ob der Batteriestrom fein genug auflöst, um die
Varianten überhaupt zu unterscheiden. Wenn die Unterschiede im Rauschen liegen, ist das selbst das Ergebnis:
dann entscheidet die Qualität.

## 7. Messkonzept

Ziel ist Energie je Nutzungszeit, getrennt nach „Tastatur sichtbar, niemand tippt“ und „aktives Tippen“.

- **Messgrößen in der App (ohne PC):** mittlerer Batteriestrom und Spannung über `BatteryManager` (Vorzeichen
  und Einheit je Hersteller prüfen), gezeichnete Bilder je Sekunde, Zeit je Bild im Oberflächen-Thread, Anzahl
  und Fläche der Neuzeichnungen, Akkutemperatur.
- **Zusätzlich mit PC, falls vorhanden:** `dumpsys gfxinfo framestats`, Perfetto (Bildzeitleiste,
  Batteriezähler), tatsächliche Panel-Rate, Treiberdateien des Grafikchips.
- **Ablauf:** nicht am Ladegerät, Flugmodus, feste Helligkeit, dieselbe App im Vordergrund, zehn Minuten
  Vorlauf bis die Temperatur steht. Jede Variante fünf Minuten, abwechselnd A B A B, mindestens vier Paare.
  Ausgewertet werden Median und Streuung der Paar-Differenzen. Liegt die Differenz innerhalb der Streuung,
  lautet das Ergebnis „nicht messbar“.
- **Szenarien:** (a) Tastatur offen, keine Berührung; (b) gleichmäßiges Tippen auf der Leertaste und daneben,
  automatisch eingespielt, damit die Läufe vergleichbar sind.
- **Varianten:** Animation aus (Nulllinie), V0 bei 30 und 60 Hz, V1, V2, V3, jeweils mit den Leerlauf-Stufen
  Stillstand, sehr langsam, Dauerbewegung.
- **Nur am Gerät entscheidbar:** die 14 Punkte am Ende von `docs/LEERTASTE-RECHERCHE.md`.

## 8. Umsetzung in SecondHand

Als eigene Etappen, jede einzeln prüfbar; nichts davon ist beauftragt, solange der Owner nicht zustimmt.

1. **Messmodus:** Zähler und ein A-B-Lauf auf der Diagnose-Seite. Tests für die Auswertung.
2. **Leertaste getrennt neu zeichnen (V1):** eigenes Zeichenelement oder eng begrenztes Rechteck; Bestätigung
   über die Zähler, dass je Bild nur noch die Leertaste gezeichnet wird.
3. **Berührungsposition:** aus `MainKeyboardView.onTouchEvent` abgreifen, nur für die Leertaste, andere Tasten
   lösen nichts aus. Keine Änderung an `PointerTracker`.
4. **Bewegungsmodell** in `agent-core` mit Tests.
5. **Zeichenweg V2 und V3**, Auswahl nach Messung; unter Android 13 bleibt V1 oder V3.
6. **Bildplanung** mit den Leerlauf-Stufen.

Bestehende Regeln gelten weiter: keine Bewegung bei unsichtbarer Tastatur, im Energiesparmodus und bei
ausgeschalteten Android-Animationen; keine Impulse in Passwortfeldern und im Inkognito-Modus; die Eingabe darf
durch die Animation nie langsamer werden. Der zeitabhängige Test `RgbOwnPatternTest` zeigt, dass Zeitgrenzen in
Tests wackeln; neue Tests prüfen Zählwerte statt Zeiten.
