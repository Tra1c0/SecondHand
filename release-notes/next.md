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
- Neu: Der Verlauf ist eine eigene Kurve: Punkte ziehen, durch Tippen hinzufügen, durch langes
  Drücken oder Hinausziehen entfernen. Die bisherigen Verläufe gibt es als Vorlagen; deine
  bisherige Wahl wird automatisch zur passenden Kurve.
- Weicher: Die Farben laufen jetzt ohne Stufen über die Tasten („Jede Taste einfarbig“ stellt
  das alte Verhalten wieder her), eigene Farben werden ohne graue Mitte gemischt, der Regenbogen
  hat keine hellen Kanten mehr.
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
