# Neu: Vorschlagsleiste einheitlich

- **Wörter oder nichts**: Während du schreibst, zeigt die Mitte der Leiste Wörter oder bleibt leer. Sie springt nicht mehr „mal nach 0,8 Sekunden, mal nicht“ zu den Werkzeugen.
- **Mikrofon bleibt stehen**: Links die Taste, rechts der Mikrofon-Kreis stehen fest an ihrem Platz, auch neben den Wörtern. Diktieren mitten im Satz ist ein Tipp.
- **Werkzeuge nach einer Pause**: Tippst du 4 Sekunden nichts, kommen die Werkzeuge in die Mitte; die nächste Taste bringt die Wörter zurück.

Nur mit automatischen Tests geprüft, noch nicht auf deinem Telefon.

# Neu: Feststelltaste behält die Zahlen

- **Zahlenreihe bei Feststelltaste**: Hast du die Großschreibung festgestellt, zeigt die Zahlenreihe wieder Zahlen. Die Symbole `! @ # …` kommen nur noch bei einmaligem Shift.

# Neu: zuletzt benutzte Emojis abgegrenzt

- **Klar getrennt**: Über deinen zuletzt benutzten Emojis steht „Zuletzt benutzt“, darunter trennt eine dünne Linie sie von den übrigen. Hast du noch keine benutzt, fehlt beides.

# Neu: Wort links beim Löschen

- **Altes Wort links**: Gehst du in ein Wort zurück, steht es links in der Leiste; war es automatisch korrigiert, steht dort dein getipptes Wort. Mitte und rechts zeigen Vorschläge.
- **Beim Löschen**: Löschst du Buchstaben aus einem Wort, steht links das Wort, wie es gerade ist („löschen“ → „lösch“).
- **Zurückholen**: Drück lange auf dieses Wort (so lange wie auf eine Buchstabentaste), dann steht wieder das ganze Wort da („löschen“).

Nur mit automatischen Tests geprüft, noch nicht auf deinem Telefon.

# Neu: Emojis in einem Zug

- **Durchgehend scrollen**: Die Emoji-Ansicht ist eine einzige Liste. Am Ende einer Kategorie geht es ohne seitliches Wischen in die nächste weiter; ein Tipp auf eine Kategorie springt dorthin, die Leiste zeigt beim Scrollen, wo du bist.

# Neu: das Wort, das du gerade bearbeitest

- **Altes Leistenlayout kommt nicht mehr zurück**: Wurde die Tastatur bei gesperrtem Telefon neu aufgebaut (Design- oder Dunkelmodus-Wechsel, Antwort vom Sperrbildschirm), blieb danach die alte, seitlich scrollende Leiste. Jetzt zählt nur deine Einstellung.

- **Beim Löschen sichtbar**: Die Leiste bleibt beim Löschen und beim Zurückgehen in ein Wort nicht mehr leer; wo das Wort steht, regelt jetzt „Wort links beim Löschen“ oben.

# Neu: Emoji-Suche

- **Findet, was du meinst**: Die Emoji-Suche sucht jetzt nach den offiziellen Namen und Stichwörtern (Deutsch, Englisch, Spanisch). „herz“ bringt ❤️ nach vorn, „daumen“ 👍, „flagge deutschland“ 🇩🇪; Umlaute und Groß/klein sind egal.
- **Keine Grenze bei 16**: Alle Treffer sind da, die Fläche scrollt.
- **Sofort eingefügt**: Ein Tipp setzt das Emoji ein, ohne dass das Suchfenster erst langsam zugeht.

# Neu: weniger falsche Korrekturen

- **Abkürzungen bleiben**: Wörter nur aus Großbuchstaben (2–5 Buchstaben, etwa ETF, AVD, ÖPNV) werden nicht mehr automatisch ersetzt.
- **Vorsilben bleiben**: Würde eine Korrektur die Vorsilbe tauschen („berstehen“ → „verstehen“), steht sie nur als Vorschlag in der Leiste; ein Tipp übernimmt sie.

- **Keine Korrektur zu eigenen Tippfehlern**: Ein Wort, das die Tastatur nur aus deinem eigenen Tippen kennt (Verlauf, automatisch ins persönliche Wörterbuch übernommen), ersetzt nichts mehr von selbst; „nichtmal“ bleibt „nichtmal“. Es steht weiter als Vorschlag in der Leiste.

Nur mit automatischen Tests geprüft, noch nicht auf deinem Telefon.

# Neu: Wort beim Zurückgehen, keine schwarze Leiste

- **Zurück ins Wort**: Löschst du zurück in ein Wort, zeigt die Leiste wieder Vorschläge dazu, auch in Apps, die einmal langsam geantwortet haben (bisher dort zehn Minuten lang nichts).
- **Autokorrektur zurücknehmen**: Rücktaste direkt nach einer Korrektur gibt dein getipptes Wort zurück; die Leiste zeigt es jetzt auch in Apps, die einmal langsam waren, und wird dabei nicht mehr leer.
- **Keine leere Leiste mehr**: Beim Schreiben und Löschen bleiben die letzten Wörter stehen, bis neue kommen. Erst nach knapp einer Sekunde Pause ohne Vorschlag erscheinen die Werkzeuge.

Geprüft mit automatischen Tests und im Emulator mit echten Tastendrücken; auf deinem Telefon noch nicht.

# Neu: ruhige Leiste beim Schreiben

- **Ruhige Leiste**: Sobald du in einem Feld mit Vorschlägen etwas getippt oder gelöscht hast, bleibt die Leiste im Bereich der Wörter, auch nach Leerzeichen, Satzzeichen, Absatz, Autokorrektur und beim Löschen. Ist nichts vorzuschlagen, bleibt der Bereich leer (der Pfeil links bleibt). Die Werkzeuge kommen zurück bei einem neuen Feld, einem leeren Feld, wieder geöffneter Tastatur und wenn du sie verlangst: Tipp auf den Pfeil links oder auf ein Werkzeug. Dann bleiben sie stehen, bis du das nächste Zeichen tippst (so geht auch ein zweites „Rückgängig“, und der Wiederholen-Pfeil bleibt sichtbar).
- **Keine Leerung zwischen zwei Wortlisten**: Beim Schreiben verschwinden die Wörter nicht mehr für einen Moment, wenn gleich neue kommen (Frist 250 ms). In dieser Frist sind die alten Wörter nicht antippbar, damit nie ein veraltetes Wort eingesetzt wird.
- **„Leiste automatisch anzeigen“** zeigt die Werkzeuge nach Enter nicht mehr, solange du schreibst; bei neuem Feld und Markierung bleibt alles wie vorher.
- **Gilt nicht**, wo die Leiste keine Wörter hat (Passwort-, Zahlen-, Telefonfelder, Vorschläge aus, andere Leistenmodi): dort wie bisher.
- **Neue Diagnose-Karte „Leiste“** (Einstellungen → Diagnose): Zahlen zu den Wechseln der Leiste, zu den Rücktasten (im Wort, Wort wiedergefunden, langsame Verbindung), zu Feldstarts und den Einstellungen, die es bestimmen. Nur Zahlen, nie Text.

Nur mit automatischen Tests geprüft, noch nicht auf einem Telefon: das Gefühl der Ruhe beim Schreiben und Löschen in deinen Apps (besonders der Claude-App), der leere Bereich nach Leerzeichen ohne Vorschlag, die Frist von 250 ms. Die Werkzeuge sind beim Schreiben einen Tipp weiter weg (Pfeil links, dann Werkzeug); der Mikrofon-Kreis folgt in einer zweiten Stufe.

Nur mit automatischen Tests geprüft, noch nicht auf einem Telefon: Langdruck auf die Komma-Taste, Aussehen von Symbol und Fenster (auch im Querformat), das Tempo mit sehr vielen langen Prompts, das Wiederherstellen der Prompts im echten Dialog und die Buchstabendreher mit den echten Wörterbüchern („ort“, „rest“, „titel“ dürfen nicht verändert werden). Die letzte Fassung der Buchstabendreher hat noch kein zweiter Prüfer gelesen.

Neu geprüft nur mit automatischen Tests: die Symbolseiten in vier Stilen (Material, Rund, Holo, Flach), mit Zahlenreihe an und aus, Deutsch und Englisch; auf dem Telefon noch nicht gesehen.
- **Symbolseiten, Nachbesserung**: Langdruck auf den Symbolseiten zeigt meist Varianten desselben Zeichens, bei einigen Tasten verwandte (`•` zeigt `♪ ♥ ♠ ♦ ♣`, `^` die Pfeile, `*` zeigt `† ‡ ★`, `°` zeigt `′ ″`). Mit eingeschalteter Zahlenreihe hat die Buchstabenseite fünf, jede Symbolseite vier höhere Reihen (gleiche Gesamthöhe). Die Zeile „Zahlenreihe in Symbolen“ sagt jetzt, dass sie nur für eigene Symbol-Layouts gilt. Auch die Tablet- und Khipro-Tasten haben auf Seite 2 „12/34“, `<` und `>`.
- **Leiste wie Gboard (Etappe 85)**: Im Leistenmodus „Tasten + Vorschläge“ (so ist die Tastatur eingestellt) hat die Leiste in jedem Stil feste, gleichmäßig verteilte Plätze: links der Raster-Knopf, dann deine Werkzeuge, rechts das Mikrofon im Kreis. Kein Pfeil zum Ausklappen, kein Scrollen. Beim Tippen ersetzen Vorschläge die Werkzeuge (Chips wie bisher in derselben Rangfolge); der Pfeil „<“ links zeigt die Werkzeuge, mit dem nächsten Tastendruck kommen die Vorschläge zurück. Das Raster öffnet alle Werkzeuge als Pillen (Symbol und Name, zwei Spalten) in den Tasten; ein Tipp führt das Werkzeug aus. Neu als Werkzeuge: Ablage, Zeichen-Kunst, Design. Was in der Leiste liegt, wenn du nie etwas eingestellt hast: die ersten fünf deiner bisherigen Werkzeuge (anfangs Textaktionen, Einstellungen, Zwischenablage, Rückgängig, Mikrofon) und die angehefteten; der Rest steht im Raster. Werkzeuge, die ein Passwortfeld oder Inkognito nicht erlaubt, bleiben abgedunkelt an ihrem Platz. Wer die alte aufklappbare Leiste will: Einstellungen → Leiste & Befehle → Knöpfe wählen und sortieren → „Feste Leiste wie Gboard“ ausschalten.
- **Leiste in der Tastatur anpassen (Etappe 86)**: Im Raster „Anpassen“ antippen (oder ein Werkzeug ohne Langdruck-Aktion lange drücken): Werkzeuge halten und in die Leiste ziehen, in der Leiste verschieben oder ins Raster zurückziehen; das Häkchen links beendet. Ohne Ziehen: Antippen fügt ein oder nimmt heraus. Höchstens 8 Werkzeuge, gespeichert wird beim Loslassen.
- Nur mit automatischen Tests geprüft (das Ziehen nur simuliert), noch nicht auf einem Telefon: Aussehen der Leiste in deinen bunten Stilen, das Ziehen mit dem Finger, Querformat und kleine Bildschirme.
- **Leiste, Nachbesserungen**: Bei markiertem Text zeigt der Pfeil „<“ links die Werkzeuge, und sie bleiben für diese Markierung stehen (Griffe ziehen ändert nichts); der Pfeil schaltet zwischen Auswahl-Aktionen und Werkzeugen um. Ein neues Feld schließt ein offenes Raster und das Anpassen. Das Design-Werkzeug öffnet bei gesperrtem Telefon nichts. „Zwischenablage leeren“ steht nur noch im Raster, wenn du es vorher in einer Liste hattest. Der Flache Look setzt auch die Leiste (und „Vorherigen Look wiederherstellen“ bringt deine Leiste zurück). Eine volle Leiste lässt „Skill-Taste anheften“ nicht zu und sagt es. Die Ablage ist in Zahlenfeldern abgedunkelt. Behoben: ein verstecktes Mikrofon verschob die Ablageposition beim Anpassen um eine Stelle.
- **Symbole ohne Verzögerung (Messung, nicht auf dem Telefon)**: Bei Zeichen, die ein Wort beenden (`. , ! ? ( ) : ; / * + = & _ " [ ] { } |` und weitere), baute die Tastatur die Leiste (Befehls-, Auswahl- und Zwischenablage-Chips, Vorschlagsfeld) noch innerhalb der Eingabe, bevor sie dem Textfeld das Ende der Eingabe meldete; viele Apps zeigen das Zeichen erst dann. Jetzt meldet die Tastatur das Ende zuerst und baut die Leiste danach; ebenso zwei Lesezugriffe auf das Textfeld bei Zeichen wie `-` nach einem Leerzeichen. Mit sichtbarer Zwischenablage-Pille fragt die Tastatur den Zwischenablage-Dienst einmal statt zweimal je Zeichen. Wer ein Zeichen unter 100 Millisekunden nach einem Buchstaben tippt, bekommt vor dem Zeichen keinen Umbau der Vorschlagsleiste mehr, der gleich wieder ersetzt wird. Was im Textfeld landet, ist unverändert (41 Tippfolgen mit Tippfehlern, Zeichen, Leerzeichen und Rücktaste gegen den alten Stand verglichen). Ob es auf dem Xiaomi 15 spürbar schneller ist, ist nicht gemessen.
- **Cursor als Trackpad (Leertaste)**: Wischen auf der Leertaste bewegt den Cursor jetzt in alle vier Richtungen, ohne den Finger abzusetzen: waagerecht ein Zeichen je Daumenbreite (Emojis, Flaggen und Buchstaben mit Akzent bleiben ganz), senkrecht eine Zeile; schneller Finger beschleunigt weich, langsamer bleibt zeichengenau. An Wortanfängen und -enden gibt es bei langsamer Bewegung einen leichten Widerstand und einen stärkeren Tick („Magnetismus“, anfangs 35 %, Regler und Schalter: Einstellungen → Gesten & Gefühl → Treffen & Wischen). Er hält nichts fest. Das Wischen auf der Löschtaste wählt mit derselben Kurve nach links aus und hält am Wortanfang; losgelassen wird genau das Markierte gelöscht. Nach oben und unten geht es nur in mehrzeiligen Feldern (in einzeiligen zählt nur die Seitwärts-Bewegung). Gilt nur, wenn die Wische nach links und rechts „Cursor bewegen“ sind (andere Belegungen bleiben), und nicht mit einer Markierung. Mit mehreren Fingern besitzt nur der erste die Spur; ein zweiter Finger tippt wie bisher (Löschen, Leertaste, Halten zum Wiederholen), wischt er, wird seine Berührung verworfen. Nach oben und unten hält die Bewegung an der ersten und letzten Zeile an. „Cursor als Trackpad“ aus = alles wie vorher.

Nur mit automatischen Tests geprüft: das Gefühl des Trackpads (die Schrittweiten, die Beschleunigung, der Magnetismus, die Ticks) ist auf keinem Telefon gespürt; alle Zahlen sind erste Schätzungen, die du am Telefon mit mir abstimmen kannst.
- **Autofill ohne leeren Kasten**: Der Kasten mit dem Kreuz für die Vorschläge des Passwortmanagers erscheint erst, wenn der erste Chip wirklich da ist; bleiben die Chips aus, ändert sich die Leiste nicht. Einstellungen → Diagnose hat die neue Karte „Autofill-Vorschläge“ (nur Zahlen, keine Titel): Antworten, angekommen, leer, verworfen, Zeit bis zum ersten Chip. Nur mit automatischen Tests geprüft, nicht auf einem Telefon.
- **Leiste, zwei Kleinigkeiten**: Ein neues Feld setzt die Leiste zurück (die für eine Markierung gewählten Werkzeuge bleiben nicht mehr in einem anderen Feld stehen). Die Ablage ist beim Wechsel zum Ziffernblock sofort abgedunkelt und öffnet sich in Zahlenlayouts auch über den Werkzeug-Knopf nicht.
- **Trackpad sichtbar machen (Etappe 87b)**: Die Tastatur sagt jetzt, was sie mit dem Cursor-Trackpad entschieden hat. Einstellungen → Diagnose hat eine Karte „Cursor-Trackpad“ (Einstellung in Worten, Zähler seit dem Start, letzter Wisch in einem Satz, nur Zahlen und Namen, nie Text oder App-Name). Unter dem Schalter „Cursor als Trackpad“ stehen die Zeilen „Leertaste: aktiv – Start nach ← oder → bewegt den Cursor frei; Start nach ↑ löst „Rückgängig“ aus, nach ↓ „Wiederholen““ (so für deine Belegung) und „Löschtaste: aktiv / nicht aktiv – …“; steht keine Richtung auf „Cursor bewegen“, sagt die Zeile auch, was zu tun ist, und der Erklärtext des Schalters ist immer derselbe. Ein Wisch, der in einer Richtung mit eigener Aktion beginnt, steht in der Karte als Entscheidung („Aktion …, kein Trackpad“), nicht als Fehler. Bei ausgeschaltetem Schalter steht eine Zeile für beide Tasten („Aus: Leertaste und Löschtaste arbeiten wie bisher“); hoch und runter gelten nur in mehrzeiligen Feldern, das steht jetzt dabei. In Passwort-, Inkognito- und gesperrten Feldern zählt die Karte nur, dass ein Wisch stattfand und wie entschieden wurde (keine Schritte, Pfeile, Rasten oder Markierungslängen). Das Verhalten des Trackpads ist unverändert. Nur mit automatischen Tests geprüft, am Telefon noch nicht gesehen.
- **Cursor-Trackpad startet, sobald eine Wischrichtung „Cursor bewegen“ ist**: Liegt mindestens eine Richtung der Leertaste (links, rechts, oben oder unten) auf „Cursor bewegen“, steuert ein Wisch, der in einer solchen Richtung **beginnt**, den Cursor frei in alle vier Richtungen (nach oben und unten in mehrzeiligen Feldern). Beginnt er in einer Richtung mit einer anderen Aktion (zum Beispiel Rückgängig, Sprachwechsel), läuft diese wie bisher. Die Entscheidung fällt nach 10 dp Bewegung, wie beim alten Wischen: ist die Strecke nach oben/unten dann länger als die seitliche, zählt oben/unten, sonst seitlich. Ein Finger, der in dieser Zone ruht, löst kein Halten aus, und Loslassen dort schreibt kein Leerzeichen, wie vorher. Ein zweiter Finger löst keine Aktion aus, solange ein anderer den Cursor führt. Ist Text markiert (zum Beispiel nach „Alles auswählen“ durch Halten), zieht der Wisch die Markierung zusammen (nach links oder oben an den Anfang, nach rechts oder unten ans Ende) und der Cursor bewegt sich von dort; nichts wird gelöscht oder ersetzt. Unter dem Schalter steht die Regel; liegt keine Richtung auf „Cursor bewegen“, steht dort, was zu tun ist. Auf dem Telefon noch nicht ausprobiert.

## Leiste v2: sichtbare Symbole, echtes Ziehen

- **Alle Symbole sind zu sehen.** In deinem dunklen Design waren die Textaktionen (Funken) und die Skill-Taste schwarz auf fast schwarz, daher die „Löcher“. Jetzt hat jedes Symbol in jedem Design einen Kontrast von mindestens 4,5:1 zum Hintergrund. Alle werden gleich groß gezeichnet und liegen gleichmäßig verteilt: weniger Symbole, größere Abstände.
- **Nicht verfügbare Werkzeuge** (privates Feld, Zahlenfeld) bleiben am Platz, blasser, mit einem kleinen Schloss; ein Tipp darauf sagt den Grund.
- **Anpassen mit echtem Ziehen:** Raster-Knopf, „Anpassen“, eine Pille kurz halten und in die Leiste ziehen: Eine Kopie folgt dem Finger, die Leiste öffnet unter dem Finger eine Lücke, loslassen setzt das Werkzeug dort ein. Umordnen: Symbol der Leiste anfassen und verschieben. Herausnehmen: nach unten ins Raster ziehen. Voll (8): die Leiste wackelt und sagt es. **Rückgängig** und **Zurücksetzen** (mit Frage) im Anpassen-Bereich. Das Systemziehen von Build 41 ist weg; es schnappte zurück.
- **Pillen im Raster lesbar (Etappe 88c)**: Im Raster und im Anpassen-Modus waren Text und Symbole der Pillen in deinem Design kaum zu lesen (grünlichgrau auf grau; die Tastenfarbe deines Designs ist durchscheinend, die Kontrastrechnung kannte das nicht). Jetzt sind Text und Symbol jeder Pille mindestens 4,5:1 gegen die Fläche, die du wirklich siehst; Werkzeuge, die schon in der Leiste sind, haben eine dunklere Pille mit dünnem Rand (kein Durchscheinen mehr); nicht verfügbare sind blasser, aber lesbar. Lange Namen („Diktieren (auf dem Gerät)“) werden kleiner und brechen auf zwei Zeilen um, nichts wird abgeschnitten; „Rückgängig“ und „Zurücksetzen“ haben den Text mittig. Nur mit Bildern und Pixeltests geprüft (mit den Farben aus deinem Foto), nicht auf dem Telefon. Der Knopf „Anpassen“ im offenen Raster ist ein normaler, mittig stehender Knopf (vorher ein flacher Balken über die ganze Breite). Auch die schwebende Kopie beim Ziehen (Symbol auf einer deckenden Scheibe), der Hinweis „Zum Personalisieren halten und ziehen“ und die Frage beim Zurücksetzen sind gegen das gerechnet, was wirklich gemalt wird. Hat dein Design einen durchscheinenden Hintergrund, malt das Raster seinen Grund jetzt deckend.
- **Rückgängig bietet Wiederholen an:** nach einem Rückgängig trägt das Symbol einen kleinen Pfeil; Wiederholen ist der Langdruck darauf. Verschwindet beim Tippen. Eine Vermutung der Tastatur, die App entscheidet.
- Geprüft mit echten Berührungen in Tests und im Emulator; auf deinem Telefon noch nicht (Haltezeit 180 ms, Zone 40 dp unter der Leiste sind erste Schätzungen).

- **Diagnose: Karte „Korrektur & Sprache“** (Einstellungen → Diagnose, kopierbar): Korrekturstufe, „Richtige Wörter nie ändern“, Layout und zweite Sprachen, was gelernt werden darf und wie viel gelernt wurde (nur Zahlen, nie Wörter). Hilft, wenn die Tastatur etwas falsch korrigiert.
