# Agent Keyboard 0.1.0 – nächster Stand

- Neu: Bereich „Werkzeuge“ im Aktionspanel.
  - Testwerkzeug: zählt Zeichen, Wörter und Zeilen des markierten Texts, nur auf dem Gerät.
  - Kalenderentwurf: übergibt Titel und Beschreibung an deine Kalender-App. Der Termin ist erst
    gespeichert, wenn du ihn dort speicherst.
- Neu: Anthropic und Google Gemini als weitere KI-Anbieter, jeweils mit eigenem Schlüssel und
  eigener Modellkennung. Wähle den Anbieter in den KI-Einstellungen; Workflow-Schritte können
  je Schritt einen Anbieter nennen. Für Gemini trägst du die Modellkennung selbst ein.
- Behoben: Die APK lässt sich jetzt normal installieren (sie war als „test-only“ markiert).
- Behoben (im Gerätetest gefunden): Im Aktionsrad und im Panel lagen die untersten Knöpfe unter
  der Navigationsleiste und ließen sich nicht antippen.
- Behoben: Nach einer Kalender-Übergabe bleibt das Panel bedienbar; der Abbruchknopf bei
  Werkzeugen heißt „Abbrechen“.
- Neu: Seite „Gelernte Wörter“ in den Einstellungen: Lernen pausieren (ohne zu löschen),
  gelernte Wörter je Sprache zählen, als Datei exportieren oder löschen.
- Behoben (nach Review): Zählen und Exportieren gelernter Wörter melden einen Fehler, statt
  bei einem langsamen Wörterbuch „0 Wörter“ anzuzeigen. Zu lange KI-Antworten von Anthropic und
  Gemini werden als „zu lang“ gemeldet.
- Neu: Diktieren auf dem Gerät. Knopf „Diktieren“ im Aktionspanel oder die Toolbar-Taste
  „Diktieren (auf dem Gerät)“. Das Gehörte wird erst angezeigt und nur nach deinem Klick an der
  Schreibmarke eingefügt. Erlaube vorher unter Einstellungen → Diktat das Mikrofon. Braucht
  Android 12 und die Spracherkennung auf dem Gerät; mit echtem Mikrofon noch nicht ausprobiert.
- Neue Berechtigung: Mikrofon (nur für das Diktat).
- Neu: „Flacher Look (Vorlage)“ unter Einstellungen → Tastaturprofile. Ein Tipp stellt das
  Aussehen nach deinen Screenshots ein (schwarzer Hintergrund, flache Tasten, Enter und „?123“
  als Pille, Zahlenreihe, Symbolhinweise, neue Icons), ein zweiter stellt den alten Look wieder
  her. Auf einem Gerät noch nicht geprüft.
- Neu im flachen Look: Leiste mit fünf Knöpfen (Menü, Zwischenablage, Emoji, Aktionen, Diktat im
  Kreis) und ein Menü hinter dem Raster-Symbol mit Design, Rückgängig, Einhändig, Emoji,
  Inkognito und Unverankert.
- Neu im flachen Look: Zwischenablage-Ansicht mit Kopfzeile und zweispaltigen Karten, kopierte
  Bilder als Pille mit rundem Vorschaubild in der Leiste, Aktionspanel mit Ergebnis-Karte und
  Chips. Die Stift-Taste öffnet das Panel direkt.
- Schneller: Bilder in der Zwischenablage werden einmal in Anzeigegröße geladen statt bei jedem
  Scrollen in voller Auflösung (alle Stile).
- Behoben (nach Review): Autofill-Vorschläge bleiben in Passwortfeldern nicht mehr hinter den
  Werkzeugen verborgen; das Menü scrollt im Querformat; die Zwischenablage-Ansicht hat auch bei
  ausgeblendeter Symbolleiste einen Weg zurück; kopierte Fotos stehen im Vorschaubild richtig
  herum; die Vorlage rechnet die Tastaturhöhe auch im Querformat richtig.
- Neu: Die Autokorrektur macht aus einem richtigen Wort nicht mehr automatisch eine
  Umlaut-Variante (mochte bleibt mochte, möchte wird vorgeschlagen). Optional merkt sie sich
  Korrekturen, die du mit der Löschtaste rückgängig machst. Einstellungen → Gelernte Wörter.
- Neu: „Meine Tippfehler lernen“ (aus): Wählst du nach einem Tippfehler dreimal dasselbe Wort aus
  der Leiste, korrigiert die Tastatur diesen Fehler danach selbst.
- Neu: Trefferzonen (aus): Nach einer kurzen Kalibrierung verschiebt die Tastatur die
  unsichtbaren Trefferflächen der Buchstaben ein Stück dorthin, wo du wirklich tippst.
- Behoben: „Gelernte Wörter löschen“ löscht jetzt auch gelernte Tippfehler, gemerkte Korrekturen
  und Trefferzonen.
- Neu im flachen Look: Symbolseiten wie bei Gboard (Seite 1 `@ # € _ & - + ( ) /`, Seite 2 mit
  `~ | • √ π ÷ × § Δ`, `< >` und der Taste „1234“).
- Neu im flachen Look: Auf den Symbolseiten zeigt die Leiste deine zuletzt benutzten Emojis; ein
  Tipp fügt sie ein.
- Neu im flachen Look: Zu lange Texte im Menü laufen als Laufschrift durch (aus, wenn du die
  Animationen in Android abgeschaltet hast).
- Neu: Screenshot-Pille (aus). Nach einem Screenshot zeigt die Leiste ihn drei Minuten lang an;
  ein Tipp fügt ihn ein. Einschalten unter Einstellungen → Screenshots.
- Neue Berechtigung: Zugriff auf Bilder (nur für die Screenshot-Pille, nur wenn du sie erlaubst).
- Neu: Befehle in der Tastatur. Tippe `@` am Wortanfang: Die Leiste zeigt die Befehle als Chips,
  erst dein Tipp führt einen aus. Präfix und Befehle unter Einstellungen → Befehle.
- Neu: `@math`. `@math 12*3,5` bietet „12 × 3,5 = 42“ zum Einsetzen an; der Chip „Rechner“
  öffnet einen Rechner im Platz der Tasten mit vier Ausgaben (Rechnung, Ergebnis, LaTeX,
  Hochzahlen/Brüche). Rechnet exakt und nur auf dem Gerät. Noch auf keinem Gerät ausprobiert.
- Neu: Installierbare Versionen kommen jetzt aus GitHub (Actions → „Agent Keyboard APK“, dann
  Releases) und sind mit deinem eigenen Schlüssel signiert. Jede neue Version installiert sich
  als Update; Einstellungen, Schlüssel und Gelerntes bleiben erhalten.
- Behoben: Der Chip „Rechner“ öffnet jetzt den Rechner. Dein Text bleibt dabei stehen;
  „Einfügen“ ersetzt `@math …` durch das Ergebnis.
- Neu im Rechner: Taste „fx“ mit sin, cos, tan (in Grad), asin, acos, atan, ln, log, √, ∛,
  n-te Wurzel, Fakultät, π und e. Auch tippbar, etwa `@math wurzel(16)` oder `@math 2pi`.
- Neu: Einstellungen im modernen Look (mit dem flachen Tastenstil) und eine Design-Seite mit
  Vorschau-Kacheln (Aussehen → Design oder Menü-Pille „Design“).
- Neue Befehle: `@contact` (Kontakte, Berechtigung unter Einstellungen → Befehle), `@url` (Adresse
  öffnen, Vorschläge aus Zwischenablage und Feld, optional Bildschirm lesen per Bedienungshilfe nur
  auf Tipp), `@such`, `@zeichen`, `@emoji`, `@conv`, `@snip` (eigene Textbausteine), `@clip`,
  `@wf`, `@datum`, `@termin` (Kalenderentwurf), `@ort` (Maps-Link, gespeicherte Orte).
- Neue Berechtigung möglich: Bedienungshilfe „Bildschirm lesen“ (nur wenn du sie einschaltest).
- Neu: `@ki Frage` fragt deinen KI-Anbieter in zwei Tipps: Chip antippen sendet genau die Frage,
  die Antwort erscheint als Chip in der Leiste und ersetzt den Befehl erst, wenn du sie antippst.
- Neu: `@qr Text` erzeugt einen QR-Code auf dem Gerät und fügt ihn als Bild ein oder teilt ihn.
- Neu: `@translate "Text" Stil` übersetzt den Text in Anführungszeichen im gewünschten Stil, z. B.
  `@translate "wann treffen wir uns morgen" natürliches slang spanisch`; wie `@ki` in zwei Tipps
  (Chip sendet, Übersetzungs-Chip ersetzt den Befehl). Bis 500 Zeichen.
- Neu: Während `@ki` und `@translate` warten, zeigen drei wandernde Punkte den Lauf („Abbrechen“
  stoppt ihn); ohne Animationen am Gerät stehen die Punkte still. Fehler zeigen einen grauen Chip
  mit kurzem Grund, der zu den Details oder den KI-Einstellungen führt.
- Besser: Die Vorschau von `@ki` zeigt die ganze Frage, nicht nur sechs Zeilen.
- Besser: `@clip` zeigt vertraulich kopierte Einträge nie; `@wf` merkt sich den Workflow, bis du
  Text markierst; `@contact` sucht im Hintergrund.
- Neu: Einstellungen → Updates. „Jetzt prüfen“ sucht im öffentlichen Repository
  `Tra1c0/AgenticKeyboard` nach einem neuen Build, ohne Konto und ohne Token. Optional (aus):
  „Täglich nach Updates suchen“ mit Benachrichtigung. „Build … herunterladen und installieren“
  prüft Prüfsumme, Paket und Signatur und übergibt an den Android-Installer, der nachfragt.
  Freunde installieren die APK einmal von github.com/Tra1c0/AgenticKeyboard/releases.
- Neue Berechtigungen: Benachrichtigungen (nur für die tägliche Update-Suche) und „Apps
  installieren“ (nur für Updates, Android fragt jedes Mal).
- Behoben: `@translate` beendet den Text erst beim letzten Anführungszeichen; Zitate im Text gehen.
- Neu: `@wecker 6:30 Training` und `@timer 10 min Nudeln`. Ein Tipp auf den Chip öffnet deine
  Uhr-App mit dem neuen Wecker bzw. Timer; gestellt ist er erst, wenn du dort bestätigst.
  `halb acht` ist beim Wecker 7:30. Einen Tag (z. B. Montag) kann die Uhr-App nicht übernehmen,
  der Chip sagt dann „Tag ignoriert“.
- Neue Berechtigung: „Wecker stellen“ (ohne Nachfrage, nur für `@wecker` und `@timer`).
- Neu: Befehlsvorlagen. Ein Befehl ohne Argument zeigt in der Leiste umrandete Vorlagen, z. B.
  `@zeichen` → „pfeile“ (alle Pfeile), `@emoji` → „tiere“, `@conv` → „oz in g“ (dann nur die
  Zahl tippen), `@datum` → „morgen“, `@termin` → „morgen 18 Uhr“, `@translate` → `spanisch "`.
  Ein Tipp schreibt nur die Vorlage hinter den Befehl.
- Neu: Eigene Designs. Auf der Design-Seite oben „Meine Designs“ mit „+“: Tastenform,
  Eckenrundung, Abstand, Tastenhöhe, sechs Farben, Schrift und ein eigenes Hintergrundbild, jede
  Änderung sofort in einer Vorschau. Langes Drücken auf eine Kachel bearbeitet, löschen mit
  Rückfrage. Eigene Farbsätze aus dem Farbeditor erscheinen jetzt auch als Kacheln. Noch auf
  keinem Gerät ausprobiert.
- Neu: Leertasten-Gesten frei belegen. Nach links, rechts, oben und unten wischen, Halten und
  Doppeltippen bekommen je eine Aktion, z. B. Aktionsrad, Panel, Diktat, Zwischenablage, Emoji,
  Befehlsliste, Rückgängig, Alles auswählen oder Tastatur ausblenden. Einstellungen → Erweitert →
  Leertaste. Ohne eigene Wahl bleibt alles wie bisher; ein einzelner Tipp schreibt immer ein
  Leerzeichen. Der Schalter „Leertaste halten öffnet das Rad“ ist jetzt die Geste „Halten“. Noch
  auf keinem Gerät ausprobiert.
