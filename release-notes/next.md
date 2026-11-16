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

- KI ohne Zeichenlimit: `@ki` und `@translate` nehmen bis 2000 Zeichen und mehrere Zeilen (Enter fügt
  einen Umbruch ein, gesendet wird nur per Tipp auf den Chip). Markierter Text für KI-Aktionen bis
  32.768 Zeichen, Ergebnis bis 65.536. Lange Antworten werden nicht mehr nach 10 Sekunden abgebrochen
  (bis 150 Sekunden, „Abbrechen“ bleibt), eine abgeschnittene Antwort wird nie eingefügt, sondern
  gemeldet. Die KI-Aktionen antworten in der Sprache deines Textes. Nur mit Tests geprüft, noch auf
  keinem Gerät und mit keiner echten Anfrage.
