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

- Neu: Alle Schieberegler der Einstellungen ticken beim Ziehen leicht und rasten am Standardwert,
  an den Enden und an runden Werten (z. B. 100 %, Zehner bei Prozent und Millisekunden) mit einem
  etwas stärkeren Impuls ein. Werte direkt neben einer Rast: etwas darüber hinaus und langsam
  zurück ziehen. Gespürt nur mit „Vibration bei Tastendruck“. Die RGB-Seite folgt später. Noch auf
  keinem Gerät ausprobiert.
- Neu: Emoji-Mixer mit „Zuletzt gemischt“: Solange kein Paar gewählt ist, zeigt der Mixer die
  letzten bis zu 12 Sticker, die noch auf dem Telefon liegen; ein Tipp fügt sie ohne Internet
  ein. Im Inkognito-Modus wird nichts gemerkt; „Gespeicherte Mischungen löschen“ leert auch diese
  Reihe. Noch auf keinem Gerät ausprobiert.
