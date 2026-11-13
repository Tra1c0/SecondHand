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
