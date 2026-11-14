# Agent Keyboard 0.1.0 – neu seit Build 21

Alles hier ist nur mit Tests geprüft, noch auf keinem Gerät ausprobiert.

- Updates: Builds kommen jetzt aus dem öffentlichen Repository `SecondHandAgenticKeyboard`, und
  nur geprüfte Builds werden dort Updates (Test-Builds nicht). Hat „Jetzt prüfen“ oder die
  tägliche Suche einen neuen Build gefunden, zeigen die Einstellungen „Build N ist da“ mit seinen
  Notizen („Installieren“ oder „Später“). In der leeren Leiste der Tastatur steht dann
  „⬆ Build N verfügbar“, nie statt Wortvorschlägen und nie bei gesperrtem Gerät. Nach dem Update
  kommt einmal „Neu in Build N“ mit diesen Notizen, ohne Netz.
- Einheitliches Befehlssystem für deine eigenen Einstellungen: `@lang duolingo spanish`,
  `@lang whatsapp german`, `@lang apps`, `@guard banking off`, `@mem whatsapp off`,
  `@group messenger whatsapp signal`, `@snip add gruss Viele Grüße`, `@clip on`/`off`. Die Wörter
  dürfen in beliebiger Reihenfolge und mit Tippfehlern kommen. Deutsch geht auch (`@sprache`,
  `@wächter`, `an`, `aus` …). Ketten gehen mit `;`, `dann`/`then` oder `→`. Die Leiste zeigt vorher
  farbig und in Klartext, was passiert. Erst dein Tipp führt aus, danach 8 Sekunden „Rückgängig“.
  Enter führt nur eine eindeutige, harmlose Deutung aus. Ein angefangener Befehl wird nie als
  Nachricht abgeschickt, normaler Text mit @-Wort („Hallo @gruppe …“, „@mem ich bin aus Berlin“)
  schon. Scheitert etwas,
  bleibt der Text stehen und nichts ist geändert. `@?` zeigt alle Bereiche. Die alten Befehle
  (`@deutsch`, `@snip adr`, `@clip Termin`) gehen weiter. Ein Wechsel mit der Sprachtaste bleibt im
  Feld. In WhatsApp teilen sich alle Chats ein Feld, dort gilt er also für alle Chats.
- Eigene RGB-Lichtmuster: Auf der RGB-Seite macht „Eigenes Muster aus …“ das gewählte Muster zu
  deinem Ausgangspunkt. Bis zu vier Ebenen mit Mischart, Form, 2–8 Farben, Tempo, Richtung,
  Größe, Bewegungskurve und Reaktion aufs Tippen; Vorschau live. Duplizieren, umbenennen,
  löschen, exportieren und importieren. Designprofile merken sich das Muster sicher. In
  Passwortfeldern und im Inkognito-Modus zeigt das Licht nicht, welche Taste du tippst.
- Bildwiederholrate als Karten: „30 HERTZ“ gelb, „60 HERTZ“ grün. In beiden läuft ein Tropfen
  genau übereinander, oben mit 30, unten mit 60 Bildern pro Sekunde, so siehst du den
  Unterschied.
- Zeichen-Kunst: Emoji-Taste lange drücken → Stift „✎“. Aus Zeichnung, Foto, Feldtext und Rahmen
  wird Text aus Zeichen (Braille, Blöcke, ASCII, Emoji-Pixel, eigene Zeichen), frei kombinierbar
  mit Live-Vorschau. Dazu große Schriften und Unicode-Stile (𝐟𝐞𝐭𝐭, 𝑘𝑢𝑟𝑠𝑖𝑣, 𝔉𝔯𝔞𝔨𝔱𝔲𝔯 …), Breite
  passend für WhatsApp, Telegram, Discord und Signal. Ins Feld nur per „Einfügen“ oder „Ersetzen“,
  danach 8 Sekunden „Rückgängig“. Alles offline.
- Smart Paste (anfangs an): Nach dem Kopieren stehen passende Chips bereit. Adresse → Karten oder
  `@ort`; Telefonnummer → anrufen oder Kontakt; IBAN in Viererblöcken (nur mit gültiger
  Prüfziffer); Sendungsnummer → Sendungsverfolgung; Datum → Kalender-Entwurf; E-Mail → Mail; Link
  ohne Tracking-Anhängsel; SMS-Code → nur der Code. Nichts passiert ohne deinen Tipp, nie für
  Geheimnisse oder in Passwortfeldern. Die Erkennung läuft lokal.
- Behoben (deine Rückmeldung zu Build 21): Die Autokorrektur stellt mit der Sprache um. Nach `@sp`
  korrigiert sie spanisch, nach `@deutsch` deutsch, auch mit „Mehrsprachigem Tippen“. Gelernte
  Tippfehler gelten nur in ihrer Sprache.
- Neu: Wörter aus anderen Sprachen. Nimmst du die Korrektur von „hola“ in einem deutschen Text
  einmal zurück, bleibt „hola“ dort ab dann stehen und wird vorgeschlagen, nie automatisch
  eingesetzt. Die Liste mit „Löschen“ steht unter „Was ich mir gemerkt habe“. Alles lokal.
- Die grauen Auswahlkarten (AUS, NICHTS …) haben helle, gut lesbare Schrift.
