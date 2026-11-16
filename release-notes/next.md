# SecondHand 0.1.0 – neu seit Build 25

Nur mit Tests geprüft, noch auf keinem Gerät ausprobiert.

- Neu: „Neue Builds sofort melden“ (Einstellungen → Updates, anfangs an). Wird die Tastatur in
  einem Feld geöffnet, fragt sie im Hintergrund höchstens alle 15 Minuten GitHub nach einem neuen
  Build und zeigt dann ohne weiteres Zutun „⬆ Build N verfügbar“ in der leeren Leiste, nie statt
  Wortvorschlägen. Gesendet wird nur die Anfrage nach der Release-Liste, nie etwas Getipptes (GitHub
  sieht wie bei jedem Abruf deine IP-Adresse). Nicht bei gesperrtem Gerät, in Inkognito- und
  Passwortfeldern, mit Datensparmodus oder ohne Netz; nach Fehlschlägen seltener (bis 4 Stunden).
  Laden und Installieren weiter nur nach deinem Tipp. Ausschalten auf derselben Seite.
- Diagnose → „Letztes Feld“ (seit Build 25): Fehlen Vorschläge, Großschreibung oder Autokorrektur,
  bitte direkt danach einen Screenshot der ersten Karte schicken.

## Nächster Build

- Neuer Name und neues Logo: Die App heißt jetzt „SecondHand“ (bisher „Agent Keyboard“) und hat ein neues Symbol, eine weiße Taste mit „S“ vor einer roten Umriss-Taste. Ein Update von Build 27 behält alle Einstellungen, Schlüssel und Gelerntes. Jeder Build liegt auf GitHub zusätzlich unter dem alten Dateinamen `AgentKeyboard-build-<N>.apk`, damit ältere Installationen weiter Updates finden.
- Neu: „Korrektur in Apps erzwingen“ (Einstellungen → Tippen & Korrektur → Regeln, anfangs nur
  Instagram): In Textfeldern dieser Apps gibt es Vorschläge, Autokorrektur und Großschreibung am
  Satzanfang auch dort, wo die App sie verbietet. Nicht in Passwort-, E-Mail-, Adress-, Namen- und
  Suchfeldern; deine Schalter und das Lernen bleiben, wie sie sind. Auf der Karte „Letztes Feld“
  (Diagnose) gibt es dazu den Knopf „In dieser App erzwingen“; in Feldern ohne Lernen (private Chats)
  zeigt die Karte jetzt Feldtyp, Merkmale und Urteil, nur nicht die App. Gilt ab dem nächsten Feld.
- Englische Sätze auf der deutschen Tastatur (mit Englisch oder Spanisch als zweiter Sprache) bleiben
  englisch: „what is your name“, „I went to the bank“, „my new job“ werden nicht mehr zu „Name“, „Bank“,
  „Job“. Die Tastatur schaut auf die bis zu drei Wörter davor im selben Satz; das erste Wort eines
  Satzes hat noch keinen Beweis und wird wie bisher korrigiert („hand“ → „Hand“). Nur mit Tests geprüft.
- KI ohne Zeichenlimit: `@ki` und `@translate` nehmen bis 2000 Zeichen und mehrere Zeilen (Enter fügt
  einen Umbruch ein, gesendet wird nur per Tipp auf den Chip). Markierter Text für KI-Aktionen bis
  32.768 Zeichen, Ergebnis bis 65.536. Lange Antworten werden nicht mehr nach 10 Sekunden abgebrochen
  (bis 150 Sekunden, „Abbrechen“ bleibt), eine abgeschnittene Antwort wird nie eingefügt, sondern
  gemeldet. Die KI-Aktionen antworten in der Sprache deines Textes. Nur mit Tests geprüft, noch auf
  keinem Gerät und mit keiner echten Anfrage.
