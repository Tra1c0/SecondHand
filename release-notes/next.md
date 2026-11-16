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
- Die App läuft jetzt ohne Debug-Modus: Sie ist nicht mehr als „debuggable“ gebaut, und der Debug-Modus ist aus. Das soll Tippen und Einstellungen schneller machen; gemessen habe ich es noch nicht. Ein früher gespeicherter „Debug-Modus an“ wird einmal zurückgesetzt, die Debug-Einstellungen findest du nach fünf Tipps auf die Version unter Über. Diagnose → App und Telefon zeigt jetzt „Debugfähige App“ und „Debug-Modus“, bitte bei weiteren Rucklern einen Screenshot schicken.
