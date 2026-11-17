# SecondHand 0.1.0 – neu seit Build 27

Test-Build. Nur mit Tests geprüft, noch auf keinem Gerät ausprobiert.

Bekannt in diesem Test-Build (Korrektur folgt im nächsten Build):
- Enter in einer `@ki`- oder `@translate`-Frage macht nur dort einen Zeilenumbruch, wo Enter das auch
  sonst tut. In Feldern, in denen Enter sendet, schickt es die Nachricht samt Frage ab.
- Sollten in Instagram beim Tippen Buchstaben verschwinden oder ersetzt werden: Instagram unter
  Einstellungen → Tippen & Korrektur → Regeln → „Korrektur in Apps erzwingen“ aus der Liste nehmen und
  mir einen Screenshot der Karte „Letztes Feld“ (Diagnose) schicken.

## Nächster Build

- Neuer Name und neues Logo: Die App heißt jetzt „SecondHand“ (bisher „Agent Keyboard“) und hat ein neues Symbol, eine weiße Taste mit „S“ vor einer roten Umriss-Taste. Ein Update von Build 27 behält alle Einstellungen, Schlüssel und Gelerntes. Jeder Build liegt auf GitHub zusätzlich unter dem alten Dateinamen `AgentKeyboard-build-<N>.apk`, damit ältere Installationen weiter Updates finden.
- Neu: „Korrektur in Apps erzwingen“ (Einstellungen → Tippen & Korrektur → Regeln, anfangs nur
  Instagram): In Textfeldern dieser Apps gibt es Vorschläge, Autokorrektur und Großschreibung am
  Satzanfang auch dort, wo die App sie verbietet. Nicht in Passwort-, E-Mail-, Adress-, Namen- und
  Suchfeldern; deine Schalter und das Lernen bleiben, wie sie sind. Auf der Karte „Letztes Feld“
  (Diagnose) gibt es dazu den Knopf „In dieser App erzwingen“; in Feldern ohne Lernen (private Chats)
  zeigt die Karte jetzt Feldtyp, Merkmale und Urteil, nur nicht die App. Gilt ab dem nächsten Feld.
  Korrigiert Instagram trotzdem nicht, zeigt die Karte den Grund: „Korrektur erzwungen: ja/nein“ und
  neu „App nahm getippten Text nicht sofort an: n-mal“. Bitte dann einen Screenshot schicken.
- Englische Sätze auf der deutschen Tastatur (mit Englisch oder Spanisch als zweiter Sprache) bleiben
  englisch: „what is your name“, „I went to the bank“, „my new job“ werden nicht mehr zu „Name“, „Bank“,
  „Job“. Die Tastatur schaut auf die bis zu drei Wörter davor im selben Satz; das erste Wort eines
  Satzes hat noch keinen Beweis und wird wie bisher korrigiert („hand“ → „Hand“). Nur mit Tests geprüft.
- KI mit viel höheren Grenzen: `@ki` und `@translate` nehmen bis 2000 Zeichen (bisher 200) und mehrere Zeilen (Enter fügt
  einen Umbruch ein, gesendet wird nur per Tipp auf den Chip). Zwei leere Zeilen hintereinander
  beenden die Frage. Ab 2000 Zeichen zeigt ein grauer Chip „Zu lang …“, statt dass der Chip einfach
  verschwindet. Markierter Text für KI-Aktionen bis
  32.768 Zeichen, Ergebnis bis 65.536. Lange Antworten werden nicht mehr nach 10 Sekunden abgebrochen
  (bis 150 Sekunden, „Abbrechen“ bleibt), eine abgeschnittene Antwort wird nie eingefügt, sondern
  gemeldet. Die KI-Aktionen antworten in der Sprache deines Textes. Nur mit Tests geprüft, noch auf
  keinem Gerät und mit keiner echten Anfrage.
- Die App läuft jetzt ohne Debug-Modus: Sie ist nicht mehr als „debuggable“ gebaut, und der Debug-Modus ist aus. Bisher lief dadurch auch der Kern der Vorschläge (Wörterbuchsuche, Wischtippen) ohne Optimierung. Das soll Vorschläge, Tippen und Einstellungen deutlich schneller machen; gemessen habe ich es noch nicht. Ein früher gespeicherter „Debug-Modus an“ wird einmal zurückgesetzt, die Debug-Einstellungen findest du nach fünf Tipps auf die Version unter Über. Diagnose → App und Telefon zeigt jetzt „Debugfähige App“ und „Debug-Modus“, bitte bei weiteren Rucklern einen Screenshot schicken.
- Das Veröffentlichen eines Builds prüft sich jetzt selbst (Signatur, Schlüssel, Versionsnummer, grüne Tests, Auffindbarkeit für die Update-Prüfung); für dich ändert sich nichts.
- Das RGB-Licht pausiert, solange Aktionspanel, Aktionsrad, Rechner, Zeichen-Kunst oder das Menü des flachen Looks die Tasten verdecken, und läuft danach weiter (spart Akku, nicht gemessen). Wirft das Zeichnen des Lichts je einen Fehler, bleibt das Licht für diese Sitzung aus, die Tastatur tippt weiter, und die Diagnose-Karte „RGB-Beleuchtung“ nennt die Fehlerklasse.
- Die Vorschau der Zeichen-Kunst rechnet beim Zeichnen im Hintergrund und folgt dem Finger; der Lauftext im Menü des flachen Looks läuft nur noch dreimal; der Lade-Puls des Panels hält an, solange es geschlossen ist.
- Beim Feldstart überschreibt die Zwischenablage-Pille keine Chips mehr, die schon in der Leiste stehen.
