# Agent Keyboard – Anleitung

Für den Owner: installieren, einrichten, benutzen. Was es gibt und wie gut es geprüft ist, steht
in [FEATURES.md](FEATURES.md). Diese Anleitung beschreibt die Debug-APK (sie heißt auf dem Telefon
„Agent Keyboard Lab“). Wird bei jeder Änderung der Bedienung im selben Push aktualisiert.

Stand der Prüfung: Die Tastatur wurde bisher nur in einem Android-15-Emulator ausprobiert
(Tippen, Aktionsrad, Panel, Ersetzen, Schlüssel speichern). Auf einem echten Telefon ist noch
nichts geprüft. Rechne beim ersten Versuch mit Fehlern und behalte deine bisherige Tastatur.

## Installieren

1. APK bauen lassen oder die fertige Datei `AgentKeyboard_0.1.0-debugNoMinify-universal.apk` aufs
   Telefon kopieren. Sie ist für Android 5 und neuer gebaut und enthält alle üblichen
   Prozessor-Varianten; KI-Schlüssel brauchen Android 6, das Diktat Android 12.
2. Die Datei auf dem Telefon antippen. Android fragt, ob die App, mit der du die Datei öffnest
   (z. B. der Dateimanager), unbekannte Apps installieren darf. Erlaube es für diesen einen Zweck.
3. Die App erscheint als „Agent Keyboard Lab“. Sie ersetzt keine andere Tastatur und kann neben
   HeliBoard installiert sein.

Prüfsumme: Neben der APK liegt eine Datei `.apk.sha256`. Wer sichergehen will, vergleicht sie mit
`sha256sum` der kopierten Datei.

## Als Tastatur einschalten

1. Android-Einstellungen → System → Tastatur → Bildschirmtastatur → Tastaturen verwalten
   (der Pfad heißt je nach Hersteller etwas anders) → „Agent Keyboard Lab“ einschalten.
   Android zeigt dabei für jede Tastatur dieselbe Warnung, dass sie alles Getippte lesen könnte.
2. In ein Textfeld tippen und über das Tastatur-Symbol in der Navigationsleiste zu
   „Agent Keyboard Lab“ wechseln.
3. Zurück zur alten Tastatur kommst du jederzeit über dasselbe Symbol.

Die Einstellungen der Tastatur öffnest du über das App-Symbol „Agent Keyboard Lab Settings“ oder
in der Tastatur über den Pfeil links in der Symbolleiste und dann das Zahnrad.

## Textaktionen (ohne Internet)

1. Text im Textfeld markieren.
2. Die Taste mit dem Stern rechts oben in der Symbolleiste antippen. Es öffnet sich das Aktionsrad.
3. „Großschrift“, „Kleinschrift“ oder „Abstände“ zeigt das Ergebnis als Vorschau im Panel.
   „Textaktionen“ öffnet das Panel mit allen Aktionen.
4. Erst „Auswahl ersetzen“ ändert den Text. „Ergebnis kopieren“ legt ihn in die Zwischenablage.

Wenn du zwischen Vorschau und Ersetzen im Textfeld etwas änderst oder woanders hintippst,
verwirft die Tastatur das Ergebnis. Das ist Absicht: Öffne die Textaktionen dann neu.

In Passwortfeldern, im Inkognitomodus und bei gesperrtem Gerät sind alle Aktionen aus.

Optional: Unter Einstellungen → Tastaturprofile → Gesten lässt sich „Leertaste halten öffnet das
Rad“ einschalten.

## KI-Aktionen (mit eigenem Schlüssel)

1. Einstellungen → KI-Anbieter. Anbieter wählen: OpenAI, Anthropic oder Google Gemini.
2. Den API-Schlüssel dieses Anbieters eintragen und „Schlüssel speichern“. Der Schlüssel wird
   verschlüsselt auf dem Gerät abgelegt und nie wieder angezeigt.
3. Modell-ID prüfen. Für OpenAI und Anthropic ist eine voreingestellt. Für Gemini musst du die
   Modell-ID aus der Dokumentation von Google selbst eintragen.
4. Im Panel eine KI-Aktion wählen (Korrigieren, Übersetzen, Umformulieren, Zusammenfassen,
   Antwort entwerfen, Analysieren). Die Tastatur zeigt, was an wen gesendet wird. Erst „Auswahl
   an … senden“ schickt den markierten Text los.
5. Das Ergebnis erscheint als Vorschau. Ersetzen ist wieder ein eigener Klick. Eine Analyse
   lässt sich nur lesen und kopieren.

Jede Anfrage kostet beim Anbieter Geld nach dessen Preisliste. Mit einem echten Schlüssel wurde
bisher keine einzige Anfrage ausprobiert; die erste ist ein Test.

Unter KI-Anbieter stehen auch die Zielsprache fürs Übersetzen, der Ton fürs Umformulieren und der
„Auftragsstatus“: eine Liste der letzten Läufe ohne Texte, die du löschen kannst.

## Skill-Tasten und Workflows

- Einstellungen → Skill-Tasten: drei Tasten mit je einer lokalen Aktion, einer KI-Aktion oder
  einem Workflow belegen. Damit sie in der Tastatur erscheinen, unter Einstellungen →
  Symbolleiste die Tasten „Skill-Taste 1–3“ einschalten.
- Einstellungen → Workflows: eigene Abläufe aus bis zu acht Schritten anlegen, z. B. erst Abstände
  aufräumen, dann korrigieren, dann übersetzen. Jeder KI-Schritt nennt Anbieter und Modell.
  Beim Start zeigt die Tastatur alle Schritte und fragt einmal nach der Freigabe.
- „Mit KI entwerfen“ (Act Builder): Du beschreibst den Ablauf in Worten, OpenAI schlägt einen
  Workflow vor, du prüfst und speicherst ihn. Das geht nur mit einem OpenAI-Schlüssel.
- Workflows, Skill-Belegungen und Tastaturprofile lassen sich als Datei exportieren und
  importieren. Ein Import führt nichts aus.

## Werkzeuge

Im Panel unter „Werkzeuge“:

- „Testwerkzeug: Zählen“ zählt Zeichen, Wörter und Zeilen des markierten Texts. Nur zum
  Ausprobieren des Ablaufs.
- „Kalenderentwurf“ öffnet nach deiner Bestätigung die Kalender-App mit einem neuen Termin: erste
  Zeile als Titel, ganzer Text als Beschreibung. Datum und Uhrzeit stellst du dort ein. Der
  Termin ist erst gespeichert, wenn du ihn in der Kalender-App speicherst.

## Diktieren

1. Einstellungen → Diktat → „Mikrofon erlauben“.
2. Schreibmarke dorthin setzen, wo der Text hin soll. Es darf nichts markiert sein, und ein
   angefangenes Wort muss beendet sein (z. B. mit einem Leerzeichen).
3. Im Panel „Diktieren (auf diesem Gerät)“ antippen. Wer oft diktiert, schaltet unter
   Einstellungen → Symbolleiste die Taste „Diktieren (auf dem Gerät)“ ein.
4. Sprechen, dann „Fertig – Text anzeigen“.
5. Text prüfen und „An Schreibmarke einfügen“.

Das Diktat nutzt nur die Spracherkennung, die Android auf dem Gerät bereitstellt (ab Android 12).
Fehlt sie oder das Sprachpaket für die Tastatursprache, meldet die Tastatur das und nimmt nichts
auf. Mit echtem Mikrofon wurde das Diktat noch nie ausprobiert.

Die Mikrofon-Taste, die es in HeliBoard schon gab, ist etwas anderes: Sie wechselt zu einer
Sprach-Tastatur des Systems, falls eine installiert ist.

## Aussehen: flacher Look

Einstellungen → Tastaturprofile → „Flacher Look (Vorlage)“ → „Vorlage anwenden“. Das stellt
Stil, Farben, Icons, Zahlenreihe, Symbolhinweise und die Größen für das Hochformat in einem
Schritt ein. „Vorherigen Look wiederherstellen“ setzt genau diese Einstellungen auf den Stand
vor dem Anwenden zurück.

- Die Tastenhöhe wird aus der Bildschirmbreite berechnet (Tastatur ohne Leiste = 66 % der Breite).
  Feinjustieren: Einstellungen → Aussehen → „Tastaturgröße“.
- „DE • EN • ES“ auf der Leertaste erscheint, wenn du unter Sprachen & Layouts bei Deutsch
  „Mehrsprachiges Tippen“ mit Englisch und Spanisch einschaltest.
- Die Schrift ist die Systemschrift deines Telefons. Eine eigene Schriftdatei lässt sich unter
  Aussehen → „Benutzerdefinierte Schriftart aus Datei festlegen“ laden.
- Stil „Flach“ und Farben „Flach schwarz“ lassen sich unter Aussehen auch einzeln wählen.
- Leiste über den Tasten, von links: Menü (Raster), Zwischenablage, Emoji, Aktionen (Stift,
  öffnet das Aktionspanel) und im Kreis das Diktat. Beim Tippen stehen in der Mitte die
  Wortvorschläge. Welche Tasten dort liegen, änderst du unter Einstellungen → Symbolleiste oder
  über den Stift-Knopf im Menü.
- Menü: Raster antippen. Design öffnet die Einstellungen zum Aussehen, die anderen Pillen
  schalten Rückgängig, Einhandmodus, Emoji, Inkognito und die frei verschiebbare Tastatur.
  Das X schließt das Menü.
- Die Zwischenablage-Taste zeigt erst etwas, wenn du den Zwischenablageverlauf einschaltest
  (Einstellungen → Optionen → „Zwischenablage-Verlauf aktivieren“); er ist anfangs aus. In der
  Ansicht: Karte antippen fügt ein, lange drücken heftet an, zur Seite wischen entfernt. Oben
  links geht es zurück zu den Tasten, oben rechts leerst du den Verlauf.
- Die Pille mit kopiertem Text oder Bild in der Leiste erscheint nur, wenn
  „Zwischenablagen-Inhalt vorschlagen“ (Einstellungen → Textkorrektur) an ist. Antippen fügt ein, lange
  drücken blendet sie aus.
- Das Aktionsrad erreichst du im flachen Look über den Leertasten-Langdruck (Einstellungen →
  Tastaturprofile → Gesten), die Stift-Taste öffnet das Panel.

## Gelernte Wörter

Lernen aus dem Tippen ist anfangs aus. Einschalten: Einstellungen → Textkorrektur →
„Personalisierte Vorschläge“. Unter Einstellungen → Gelernte Wörter kannst du das Lernen
pausieren (das Gelernte bleibt), die gelernten Wörter je Sprache zählen, als Datei exportieren
oder löschen. Wer „Personalisierte Vorschläge“ ausschaltet, löscht dabei alles Gelernte.

## Wenn etwas nicht klappt

Schreib dazu auf, was hilft, den Fehler zu finden:

- Telefon und Android-Version,
- in welcher App und in welchem Feld,
- was du getan hast, was du erwartet hast und was stattdessen passiert ist,
- bei KI-Aktionen die angezeigte Meldung (keinen Schlüssel und keinen privaten Text mitschicken).

Bekannte Lücken stehen in [HANDOFF.md](HANDOFF.md) unter „Offene Punkte“.

## Entfernen

Android-Einstellungen → Apps → „Agent Keyboard Lab“ → Deinstallieren. Dabei werden Schlüssel,
Workflows, Profile und gelernte Wörter gelöscht. Vorher wechselt Android automatisch zu einer
anderen Tastatur.
