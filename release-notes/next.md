# SecondHand 0.1.0 – neu seit Build 29

Arbeitsstand von Runde 2, noch kein geprüfter Build: nur mit Tests geprüft, teils noch ohne Nachprüfung,
auf keinem Gerät ausprobiert.

- Das RGB-Licht pausiert, solange Aktionspanel, Aktionsrad, Rechner, Zeichen-Kunst oder das Menü des flachen Looks die Tasten verdecken, und läuft danach weiter (spart Akku, nicht gemessen). Wirft das Zeichnen des Lichts je einen Fehler, bleibt das Licht für diese Sitzung aus, die Tastatur tippt weiter, und die Diagnose-Karte „RGB-Beleuchtung“ nennt die Fehlerklasse.
- Die Vorschau der Zeichen-Kunst rechnet beim Zeichnen im Hintergrund und folgt dem Finger; der Lauftext im Menü des flachen Looks läuft nur noch dreimal; der Lade-Puls des Panels hält an, solange es geschlossen ist.
- Beim Feldstart überschreibt die Zwischenablage-Pille keine Chips mehr, die schon in der Leiste stehen; ist „Werkzeugleiste automatisch zeigen“ an und stehen Chips da, öffnet sich die Werkzeugleiste dort nicht mehr von selbst.
- Unter dem geöffneten Menü des flachen Looks leuchtet die Leiste weiter. Nach einem langen Druck auf die Leertaste (Sprachwahl), den das System mit einem Abbruch beendet, kann das nächste Wort wieder durch Wischen geschrieben werden (vorher konnte es verloren gehen).
- Neu: Diagnose → Karte „Tippen“ misst, wie lange die Zusatzfunktionen der Tastatur je Taste brauchen und wie oft sie die App und Android fragen (nur Zahlen, nie Text). Wenn das Tippen irgendwo ruckelt: eine Weile dort tippen, Diagnose öffnen und einen Screenshot dieser Karte schicken. Gut ist unter 16 ms je Taste.
- Die Vorschlagsleiste fragt Android und die App seltener: ein gemeinsamer, höchstens eine Sekunde alter Sperrzustand (bei Bildschirm aus, Entsperren, neuem Feld und jedem Tipp auf einen Chip sofort neu), Text hinter der Schreibmarke und Zwischenablage in Feldern für E-Mail, Telefon, Zahl und Name nicht mehr bei jeder Taste, Chip-Zeilen bei gleichem Inhalt nicht neu gebaut. Es soll das Tippen in diesen Feldern weniger ruckeln lassen; ob es das tut, zeigt die Karte „Tippen“ auf dem Gerät.
- Die Suche in den Einstellungen findet jetzt auch die Seiten selbst („KI“, „Beleuchtung“, „Updates“,
  „Diagnose“, „Tippen“ …), auf Deutsch und auf Englisch.
- „Sichern und Wiederherstellen“ sagt jetzt genau, was in der Datei steht (Tastatureinstellungen,
  eigene Layouts, Wörterbücher und gelernte Wörter, Hintergrundbild, Schriften, Zwischenablage-Verlauf),
  was nicht (Profile, Gedächtnis, Bausteine, Orte, Korrekturen, Trefferzonen, gemalte Symbole,
  KI-Einstellungen) und dass die Datei nicht verschlüsselt ist.
- Tippflächen der neuen Einstellungsseiten sind größer (48 statt 44 dp); auf „Tasten & Sprachen“
  bleiben abhängige Schalter sichtbar, grau und mit dem Grund; die Leiste unten auf der Startseite
  bricht bei großer Schrift um.
- Einige englische Texte in der deutschen Oberfläche sind übersetzt; „Über“ verweist auf das
  Repository dieses Projekts statt auf die Fehlerseiten von HeliBoard (Dank und Lizenz bleiben).
- Zwischenablage-Verlauf: Nichts wird aufgenommen, solange das Gerät gesperrt ist, „Immer Inkognito“ an ist oder das System ein Bild oder eine Datei als vertraulich markiert. Solange die Tastatur gerade in einem Passwortfeld oder einem Feld ohne Lernen (private Chats) aktiv ist, wird auch nichts aufgenommen. Beispiel: Text im privaten Chat kopiert: nicht im Verlauf. Tastatur ausblenden, im Browser einen Link kopieren: im Verlauf. Was nicht aufgenommen wurde, kommt später nicht nachträglich hinein.
- Sicherung wiederherstellen: Scheitert es, holt die App alles zurück (vorher konnte der geschützte Datenordner verloren gehen). „Es wurde nichts geändert“ steht nur da, wenn es stimmt; scheitert das Zurückholen selbst, sagt die Meldung, wo deine alten Daten liegen. Ein Rest einer abgebrochenen Wiederherstellung wird beim nächsten Versuch zurückgeholt. Stirbt die App mitten drin, geschieht das erst beim nächsten Versuch.
- Eine von außen geöffnete Wörterbuchdatei wird im Hintergrund kopiert (einmal, auch beim Drehen); schlägt das fehl, kommt eine Fehlermeldung statt einer leeren Seite.
- Intern: keine gelernten Wörter, gewählten Emojis und Tastenbeschriftungen mehr in Protokollzeilen, Tresor und Feldtext werden früher aus dem Speicher geräumt.
