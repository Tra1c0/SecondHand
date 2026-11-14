# Agent Keyboard 0.1.0 – nächster Stand

- Behoben: Sprachbefehle (`@deutsch`, `@sp` …) schalten jetzt zuverlässig um. Leerte der Befehl
  das Feld und startete die App es neu, sprang die Tastatur auf die Sprache der App zurück (z. B.
  fehlte nach `@deutsch` das „ä“ unter „a“). Die gewählte Sprache bleibt jetzt im selben Feld.
  Noch auf keinem Gerät ausprobiert.
- Neu: Rückgängig und Wiederholen per Leertasten-Geste vibrieren als drei kurze Ticks:
  Wiederholen wird stärker, Rückgängig schwächer. „Links nach rechts“ geht nicht, weil das
  Telefon nur einen Vibrationsmotor hat. Nur mit eingeschalteter Tastenvibration. Noch auf keinem
  Gerät ausprobiert.
- Behoben: Zwei Screenshots kurz nacheinander (innerhalb einer halben Sekunde) landen jetzt
  beide im Zwischenablageverlauf, nicht nur der zweite. Noch auf keinem Gerät ausprobiert.
- Behoben: Reste halb kopierter Screenshots für den Verlauf werden nach 10 Minuten gelöscht,
  und beim Beenden der Tastatur hört sie auch wirklich auf, nach Screenshots zu schauen.
- Behoben: Beim Entpacken der mitgelieferten Emoji-Wörterbücher liegt die Zwischendatei nicht
  mehr im Wörterbuchordner und wird bei einem Fehler gelöscht.
- Neu: Symbole malen (anfangs aus). Einstellungen → „Symbole malen“: Wischen über die Buchstaben
  malt ein Sonderzeichen (→ ≠ € ✓ ♥ und 35 weitere) statt eines Gestenworts; eigene Symbole
  malst du dreimal und gibst den Text dazu ein. Erkennung nur auf dem Telefon. Auf einem Gerät
  noch nicht ausprobiert.
- Neu: Emoji-Mix wie Gboards Emoji Kitchen. `@mix 🐶🔥` oder zwei getippte Emojis direkt vor der
  Schreibmarke zeigen in der Leiste „🐶 + 🔥 mischen · Google“; erst dein Tipp lädt genau dieses
  Bild von Google, dann fügt ein Tipp auf den Sticker ihn ein (oder teilt ihn) und löscht die
  zwei Emojis bzw. den Befehl. `@mix 🐶` schlägt Partner vor. In der Emoji-Ansicht teilen sich
  „Emojis suchen“ und „Emoji-Mixer“ die untere Reihe (statt Leertaste und Lupe): Im Mixer wählst
  du zwei Emojis, die Mischung lädt und gleitet groß herein. Geladene Sticker bleiben auf dem
  Gerät (höchstens 20 MB). Gesendet werden nur die beiden Emojis. Einstellungen → Befehle
  („Emoji-Mix in der Leiste anbieten“, gespeicherte Mischungen löschen). Nicht in Passwortfeldern,
  im Inkognito-Modus oder bei gesperrtem Gerät. Noch auf keinem Gerät ausprobiert.
- Behoben: Nach einem Update hatte ein eigenes Design plötzlich große Tastenabstände (die App
  spielte bei jedem Update alte Umstellungen von HeliBoard ab). Beim Update auf diesen Build kommen
  die Abstände deines gewählten Designs zurück. Dieselben Umstellungen haben bei jedem Update auch
  die angepinnten Knöpfe der Leiste zurückgesetzt und „Satzzeichen vorschlagen“ verstellt: Bitte
  einmal prüfen und neu einstellen, ab jetzt bleibt es.
- Behoben: Emoji-Mix und `@qr` löschen die getippten Emojis bzw. den Befehl nur noch, wenn die
  App das Bild wirklich angenommen hat; sonst bleiben sie stehen und ein Hinweis erscheint.
  Setzt eine App das Bild selbst an die Schreibmarke, heißt der Hinweis jetzt „Bild eingefügt …“
  statt „Text geändert“. Noch auf keinem Gerät ausprobiert.

## Nächster Build

- Neu: Wischen auf der Leertaste nach links oder rechts (z. B. Rückgängig, Wiederholen) hat jetzt
  Rastpunkte auf dem Weg: ein leichter Tick bei etwa 0,8 und 1,6 Tastenbreiten, der letzte etwas
  weiter fühlt sich wie Überdehnen an (stärker, länger) und führt erst dann die Aktion aus, einmal
  pro Wisch. Vorher loslassen tut nichts, zurück und wieder vor tickt erneut. Die drei Ticks
  direkt nach Rückgängig/Wiederholen gibt es nicht mehr. Rückgängig ist ein normaler
  Rückgängig-Schritt. Noch auf keinem Gerät ausprobiert.
- Neu: Bewegst du den Finger beim Halten der Leertaste deutlich nach links oder rechts, ist das
  Halten sofort vorbei; ein langsamer Wisch öffnet nie mehr die Halten-Aktion.
- Neu: Alle Schieberegler der Einstellungen ticken beim Ziehen leicht und rasten am Standardwert,
  an den Enden und an runden Werten (z. B. 100 %, Zehner bei Prozent und Millisekunden) mit einem
  etwas stärkeren Impuls ein. Werte direkt neben einer Rast: etwas darüber hinaus und langsam
  zurück ziehen. Gespürt nur mit „Vibration bei Tastendruck“. Die RGB-Seite folgt später. Noch auf
  keinem Gerät ausprobiert.
- Neu: Emoji-Mixer mit „Zuletzt gemischt“: Solange kein Paar gewählt ist, zeigt der Mixer die
  letzten bis zu 12 Sticker, die noch auf dem Telefon liegen; ein Tipp fügt sie ohne Internet
  ein. Im Inkognito-Modus wird nichts gemerkt; „Gespeicherte Mischungen löschen“ leert auch diese
  Reihe. Noch auf keinem Gerät ausprobiert.
- Neu: Einstellungen → „Diagnose“ zeigt, was dein Telefon wirklich macht, ohne Texte, Schlüssel
  oder Kennungen: welche Vibration die Rückgängig/Wiederholen-Kette spielt (mit „Test“), ob
  Symbole malen laufen kann (Wörterbuch, TalkBack), warum die Sprache zuletzt gewechselt hat (z. B.
  „Sprach-Hinweis der App“ oder „Sprachbefehl“), den Stand von Emoji-Mix und die Versionen.
  „gstatic erreichbar?“ lädt nur bei diesem Tipp einmal ein Testbild von Google. „Kopieren“ legt
  die Werte in die Zwischenablage, zum Schicken. Noch auf keinem Gerät ausprobiert.
- Behoben: Symbole malen: Ein Buchstabe, den du in der halben Sekunde nach einem Strich tippst,
  geht nicht mehr verloren; erst kommt das Symbol, dann der Buchstabe. Als Punkt (÷) zählt ein
  Tipp nur noch, wo ein Symbol einen Punkt braucht. Ein Wortvorschlag in dieser halben Sekunde
  kommt nach dem Symbol. „Gestenschreiben“ ist ausgegraut, solange Symbole malen an ist. Noch auf
  keinem Gerät ausprobiert.
- Neu: RGB-Beleuchtung überarbeitet. Die Seite (Einstellungen → Aussehen → „RGB-Beleuchtung“)
  hat oben eine große Live-Vorschau und darunter Karten; die Muster wählst du als Bilder.
- Neu: sechs bewegte Muster nach deinen Vorlagen: Tinte, Rauten, Lamellen, Raster, LED-Punkte,
  Höhenlinien. Sie fließen bzw. wandern langsam (Tempo über „Dauer eines Durchlaufs“).
- Neu: Schaltest du „Gleiches Muster überall“ aus, bekommen Tasten, Hintergrund, Beschriftung,
  Vorschläge und Leistenknöpfe je ein eigenes Muster mit eigenem Verlauf, Tempo und eigenen Farben.
- Neu: Der Verlauf ist eine eigene Kurve: Punkte ziehen, durch Tippen hinzufügen, durch Tippen
  oder langes Drücken wählen und mit „Punkt entfernen“ oder Hinausziehen entfernen (langes
  Drücken löscht nichts). Mit TalkBack lässt sich der gewählte Punkt über Aktionen verschieben
  und entfernen. Die bisherigen Verläufe gibt es als Vorlagen; deine bisherige Wahl wird
  automatisch zur passenden Kurve.
- Weicher: Die Farben laufen jetzt ohne Stufen über die Tasten, auch bei deiner schon
  gespeicherten Einstellung. Wer jede Taste wie bisher in einer Farbe will, schaltet „Jede Taste
  einfarbig“ ein. Eigene Farben werden ohne graue Mitte gemischt, der Regenbogen hat keine hellen
  Kanten mehr.
- Flüssiger: Die feinen Linien und Punkte der Texturen werden im Hintergrund vorbereitet und
  erscheinen beim ersten Öffnen einen Augenblick nach den Farben, statt die Tastatur kurz
  anzuhalten. Die Mustergalerie der RGB-Seite rechnet nicht mehr bei jeder Bewegung eines
  Kurvenpunkts oder Reglers neu. Die Bewegung der Texturen springt auch nach langer Laufzeit
  nicht mehr.
- Flüssiger (deine Rückmeldung zu Build 14): Die Farben der Beleuchtung werden jetzt im
  Hintergrund gerechnet, die Tastatur übernimmt nur das fertige Bild; ein stehendes Muster
  rechnet gar nicht mehr nach. Beim Ziehen eines Kurvenpunkts baut sich die RGB-Seite nicht mehr
  bei jeder Bewegung neu auf, nur die Vorschau folgt. Beim Öffnen erscheint das Licht ein Bild
  später. Noch auf keinem Gerät ausprobiert; ob es auf deinem Telefon jetzt flüssig ist, ist nicht
  gemessen.
- Noch auf keinem Gerät ausprobiert; Akkuverbrauch und Ruckelfreiheit auf dem Telefon sind nicht
  gemessen.
- Neu: Emoji-Vorschläge in der Leiste treffen deutlich mehr. Wortformen („Katzen“ → 🐈),
  Groß-/Kleinschreibung und Umlaute („KAESE“ → 🧀), Wortanfänge ab 4 Buchstaben („Schmetterl“
  → 🦋), ein Tippfehler ab 4 Buchstaben („Kafee“ → ☕), Englisch beim deutschen Tippen
  („party“ → 🎉) und eigene Synonyme („geil“ → 🔥). Höchstens zwei Emojis, hinter den ersten zwei
  Wortvorschlägen, nie als Autokorrektur. Alles lokal; nicht in Passwortfeldern, bei Inkognito
  oder bei gesperrtem Gerät. Noch auf keinem Gerät ausprobiert.
- Behoben: Symbole malen: Schnelles Tippen direkt nach einem gemalten Strich (z. B. „E-Mail“)
  verliert keine Buchstaben mehr. Als Punkt des ÷ zählt nur noch ein Tipp ungefähr senkrecht über
  oder unter der Mitte des Strichs, die Tasten daneben bleiben Buchstaben. Zwei Daumen
  gleichzeitig tippen beide Buchstaben nach dem Symbol, statt die Zeichnung und den ersten
  Buchstaben zu verwerfen. Ein Tipp, der beim Loslassen auf die Nachbartaste rutscht, tippt die
  Taste, auf der er aufsetzte. Das Malfeld eigener Symbole nimmt jetzt genau so viele Punkte wie
  die Tastatur. Noch auf keinem Gerät ausprobiert.
- Neu auf der Diagnose-Seite: was die RGB-Beleuchtung je Bild kostet (die letzten 5 Sekunden mit
  Licht). Tastatur mit RGB öffnen, kurz tippen, dann Einstellungen → Diagnose → „Kopieren“ und mir
  schicken.
- Behoben: Symbole malen (Rückmeldung zu Build 14). Ein kurzer Strich nahe der Zeichnung zählt
  jetzt zum Symbol, statt es zu beenden und einen Buchstaben zu tippen; nur ein Tipp, bei dem sich
  der Finger kaum bewegt, ist ein Buchstabe, und alles, was du weiter weg tippst, wird normal
  getippt. Direkt neben der Zeichnung kann ein verwackelter Tipp noch als Strich zählen. Die Pause zwischen zwei Strichen ist länger (anfangs 0,9 Sekunden) und passt sich deinem
  Tempo an. Ein Strich darf auch auf Punkt, Komma oder einer Ziffer beginnen. Hältst du beim Malen
  kurz inne, zeigt die Leiste, was geschrieben würde („✎ →“); ein Tipp
  darauf schreibt es sofort. Die Erkennung achtet jetzt auch auf die Richtung und Zahl der
  Striche. Neu: „Aus meinen Korrekturen lernen“ (anfangs aus) merkt sich deine Zeichnung,
  wenn du in der Leiste ein anderes Symbol wählst, und „Kalibrieren“ fragt jedes Symbol zweimal ab;
  beides unter Einstellungen → „Symbole malen“, nur auf dem Telefon, jederzeit löschbar (auch mit
  „Gelernte Wörter löschen“); das Lernen braucht zusätzlich „Personalisierte Vorschläge“. In einem künstlichen Test, dessen Malarten den neuen Vorlagen ähneln,
  96 % statt 86 % richtig; wie gut es deine Handschrift erkennt und ob ⇒ nicht mehr zu ≠ wird, ist
  offen. Noch auf keinem Gerät ausprobiert.
- Neu: Die Einstellungen haben eine neue Startseite im dunklen Look der RGB-Seite (für jeden
  Tastenstil): oben „Dein Look“ mit „Ändern“, darunter die Schnellschalter Inkognito, RGB-Licht,
  Vibration und Symbole malen, dann sieben farbige Bereiche (Aussehen, Tippen & Korrektur, Tasten
  & Sprachen, Gesten & Gefühl, Leiste & Befehle, KI & Automationen, System & Daten) und unten
  Diagnose, Updates mit der Build-Nummer und Über. Die Lupe sucht wie bisher. Jeder Bereich führt
  vorerst über eine Karte „Seiten“ zu den bisherigen Seiten; die Bereiche werden in den nächsten
  Schritten einzeln nach dem Entwurf umgebaut. Welche alte Seite wo liegt, steht in der Anleitung
  unter „Einstellungen finden“. Noch auf keinem Gerät ausprobiert.
- Neu: Einstellungen → „Tippen & Korrektur“ ist jetzt eine eigene Seite im neuen Look. Oben wählst
  du die Autokorrektur mit einem Tipp: AUS (nur Vorschläge), BEHUTSAM (nur Wörter, die es nicht
  gibt, Umlaut-Wörter bleiben) oder MUTIG (korrigiert auch knappe Fälle). Passt keine Stufe zu
  deinen Einstellungen, steht dort „Eigene Einstellung“. Darunter die Schalter für Regeln,
  Vorschläge, Leerzeichen und Lernen, die Wörterbücher mit Zahl, das persönliche Wörterbuch und die
  gelernten Wörter. Alles Weitere der bisherigen Textkorrektur unter „Alle Korrektur-Einstellungen“.
  Noch auf keinem Gerät ausprobiert.
- Neu: Einstellungen → „Gesten & Gefühl“ ist nach dem Entwurf umgebaut. Oben die Leertaste als
  Bild (Tick, Tick, am letzten Punkt die Aktion) und darunter die sechs Gesten mit ihrer
  aktuellen Aktion; ein Tipp ändert sie. Darunter wählst du mit großen Karten, was Wischen über
  die Buchstaben tut: nichts, Symbole malen oder Gestenschreiben (nur mit Gesten-Bibliothek).
  Dann „Symbole malen“ (eigene Symbole, lernen, kalibrieren, wie lange die Spur bleibt),
  „Vibration & Ton“ und „Treffen & Wischen“ (Trefferzonen, Löschen durch Wischen,
  Touchpad-Empfindlichkeit). Alles schaltet deine echten Einstellungen; die bisherigen Seiten
  gibt es weiter. Noch auf keinem Gerät ausprobiert.
- Neu: Einstellungen → „Tasten & Sprachen“ ist neu gestaltet. Oben eine farbige Karte je Sprache
  mit Layout und Wörterbüchern, die gerade benutzte ist hell und „aktiv“; ein Tipp öffnet die
  Seite der Sprache. Darunter Sprachen, Zusatztasten, Popups & Hinweise und Layouts mit Schaltern
  und den gewohnten Dialogen, dazu aus „Erweitert“ Langdruck-Dauer, Währungen, Zeitstempel-Format
  und „Zurück zu den Buchstaben“. Zwischenablage, Vibration und Ton liegen vorerst unter „Weitere
  Optionen“. Noch auf keinem Gerät ausprobiert.
- Neu: Einstellungen → „Leiste & Befehle“ ist jetzt eine eigene Seite im neuen Look. Oben wählst
  du mit einem Tipp, was die Leiste zeigt: TASTEN + VORSCHLÄGE, NUR VORSCHLÄGE oder VERSTECKT.
  Darunter alle @-Befehle als Chips (Antippen schaltet einen Befehl an oder aus, der Schalter
  oben schaltet alle ab), dann Knöpfe wählen und sortieren, Automatisch zeigen, Wischen blendet
  aus, Skill-Tasten anheften („2 von 3“), Zwischenablage-Verlauf, Aufbewahrung, Bilder und
  Dateien, Screenshots anbieten und Emoji-Mix in der Leiste. Textbausteine, Orte, Bildschirm lesen
  und das Präfix unter „Alle Befehls-Einstellungen“. „Symbolleiste“ und „Werkzeugleiste“ heißen
  überall „Leiste“. Noch auf keinem Gerät ausprobiert.
- Neu: Einstellungen → „KI & Automationen“ ist nach dem Entwurf umgebaut. Oben wählst du mit
  großen Karten den Anbieter (OpenAI, Anthropic, Google Gemini, mit dem eingestellten Modell);
  darunter steht, ob ein Schlüssel gespeichert ist (der Schlüssel selbst wird nie gezeigt), und
  „Ändern“ führt zur Schlüsseleingabe. Dann deine drei Skill-Tasten mit ihren Namen, Zielsprache
  und Tonfall, deine Workflows (mit Anzahl) sowie Diktat (Bereit/Erlauben/Nicht verfügbar) und
  Auftragsstatus. Bei gesperrtem Gerät zeigt die Seite nur den Hinweis zum Entsperren. Die
  bisherigen Seiten gibt es weiter. Noch auf keinem Gerät ausprobiert.
- Neu: Formatwächter (Einstellungen → Tippen & Korrektur → Regeln, anfangs an). In E-Mail-Feldern
  schlägt die Leiste bei Tippfehlern in der Domain den richtigen Anbieter vor (`max@gmial.com` →
  „gmail.com?“), ebenso bei fehlendem `@` oder Leerzeichen; in Telefonfeldern das internationale
  Format (`0151 23456789` → „+49 1512 3456789“); bei einer IBAN zeigt ein grüner oder roter Punkt,
  ob die Prüfziffer stimmt, ein Tipp setzt Viererblöcke; in Kommazahl-Feldern ein Hinweis, wenn
  Punkt und Komma nicht zur Sprache passen. Nichts ändert sich von selbst, erst dein Tipp. Nie in
  Passwortfeldern, im Inkognito-Modus oder bei gesperrtem Gerät. Noch auf keinem Gerät ausprobiert.
