# Agent Keyboard 0.1.0 – neu seit Build 25

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

- Englische Sätze auf der deutschen Tastatur (mit Englisch oder Spanisch als zweiter Sprache) bleiben
  englisch: „what is your name“, „I went to the bank“, „my new job“ werden nicht mehr zu „Name“, „Bank“,
  „Job“. Die Tastatur schaut auf die bis zu drei Wörter davor im selben Satz; das erste Wort eines
  Satzes hat noch keinen Beweis und wird wie bisher korrigiert („hand“ → „Hand“). Nur mit Tests geprüft.
