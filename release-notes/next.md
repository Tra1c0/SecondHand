# SecondHand 0.1.0 – neu seit Build 27

Nur mit Tests geprüft, noch auf keinem Gerät ausprobiert.

- Neuer Name und neues Logo: Die App heißt jetzt „SecondHand“ (bisher „Agent Keyboard“) und hat ein
  neues Symbol, eine weiße Taste mit „S“ vor einer roten Umriss-Taste. Ein Update von Build 27 behält
  alle Einstellungen, Schlüssel und Gelerntes.
- Schneller: Die App ist nicht mehr als „debuggable“ gebaut, und der Debug-Modus ist aus. Bisher lief
  dadurch auch der Kern der Vorschläge (Wörterbuchsuche, Wischtippen) ohne Optimierung. Das soll
  Vorschläge, Tippen und Einstellungen deutlich schneller machen; gemessen habe ich es noch nicht.
  Diagnose → „App und Telefon“ zeigt „Debugfähige App“ und „Debug-Modus“; bitte bei weiteren Rucklern
  einen Screenshot schicken. Die Debug-Einstellungen findest du nach fünf Tipps auf die Version unter
  „Über“.
- KI mit höheren Grenzen: `@ki` und `@translate` nehmen bis 2000 Zeichen (bisher 200) und mehrere
  Zeilen. Enter schickt eine laufende Frage nie als Nachricht ab: in mehrzeiligen Feldern fügt es
  einen Zeilenumbruch ein, in einzeiligen passiert nichts und ein Hinweis erscheint; gesendet wird nur
  per Tipp auf den Chip. Zwei Leerzeilen beenden die Frage. Ab 2000 Zeichen zeigt ein grauer Chip
  „Zu lang …“. Markierter Text für KI-Aktionen bis 32.768 Zeichen, Ergebnis bis 65.536. Lange
  Antworten werden nicht mehr nach 10 Sekunden abgebrochen (bis 150 Sekunden, „Abbrechen“ bleibt); eine
  abgeschnittene Antwort wird nie eingefügt, sondern gemeldet. Die KI-Aktionen antworten in der
  Sprache deines Textes. Noch mit keiner echten Anfrage geprüft.
- Englische Sätze bleiben englisch (deutsche Tastatur mit Englisch oder Spanisch als zweiter
  Sprache): „what is your name“, „I went to the bank“, „my new job“ werden nicht mehr zu „Name“, „Bank“,
  „Job“. Die Tastatur schaut auf die bis zu drei Wörter davor im selben Satz; das erste Wort eines
  Satzes wird wie bisher korrigiert („hand“ → „Hand“).
- Neu: „Korrektur in Apps erzwingen“ (Einstellungen → Tippen & Korrektur → Regeln, anfangs nur
  Instagram): In Textfeldern dieser Apps gibt es Vorschläge, Autokorrektur und Großschreibung am
  Satzanfang auch dort, wo die App sie verbietet. Nicht in Passwort-, E-Mail-, Adress-, Suchfeldern
  und einigen weiteren Feldarten; deine Schalter und das Lernen bleiben, wie sie sind. Die Karte
  „Letztes Feld“ (Diagnose) zeigt, ob erzwungen wurde und wenn nicht, warum, und hat den Knopf „In
  dieser App erzwingen“. Nimmt eine App den Text der Tastatur nicht unverändert an, bleibt er stehen,
  nur Vorschläge und Autokorrektur setzen für dieses Wort aus; die Karte zählt das mit. Korrigiert
  Instagram trotzdem nicht oder verhält sich das Tippen dort seltsam: bitte einen Screenshot der Karte
  schicken und die App im Zweifel aus der Liste nehmen.
- Das Veröffentlichen eines Builds prüft sich jetzt selbst (Signatur, Schlüssel, Versionsnummer, grüne
  Tests, Auffindbarkeit für die Update-Prüfung); für dich ändert sich nichts.
