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
