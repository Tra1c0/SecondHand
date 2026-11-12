# Agent Keyboard – Anleitung

Für den Owner: installieren, einrichten, benutzen. Was es gibt und wie gut es geprüft ist, steht
in [FEATURES.md](FEATURES.md). Diese Anleitung beschreibt die Debug-APK (sie heißt auf dem Telefon
„Agent Keyboard Lab“). Wird bei jeder Änderung der Bedienung im selben Push aktualisiert.

Stand der Prüfung: Die Tastatur wurde bisher nur in einem Android-15-Emulator ausprobiert
(Tippen, Aktionsrad, Panel, Ersetzen, Schlüssel speichern). Auf einem echten Telefon ist noch
nichts geprüft. Rechne beim ersten Versuch mit Fehlern und behalte deine bisherige Tastatur.

## Installieren

1. Einmalig: Lege in GitHub unter deinem Repo → Settings → Secrets and variables → Actions zwei
   „Repository secrets“ an: `AGENT_KEYSTORE_BASE64` und `AGENT_KEYSTORE_PASSWORD` (Inhalte aus
   den beiden Dateien, die du bekommen hast). Bewahre beide zusätzlich im Passwortmanager auf.
2. Für jede neue Version: GitHub → Actions → „Agent Keyboard APK“ → „Run workflow“, Branch
   wählen. Nach etwa 10–20 Minuten steht unter „Releases“ ein Eintrag `build-<Nummer>` mit der
   Datei `AgentKeyboard-build-<Nummer>.apk`. Lade sie auf dem Telefon herunter. Sie ist für
   Android 5 und neuer gebaut; KI-Schlüssel brauchen Android 6, das Diktat Android 12.
2. Die Datei auf dem Telefon antippen. Android fragt, ob die App, mit der du die Datei öffnest
   (z. B. der Dateimanager), unbekannte Apps installieren darf. Erlaube es für diesen einen Zweck.
3. Die App erscheint als „Agent Keyboard Lab“. Sie ersetzt keine andere Tastatur und kann neben
   HeliBoard installiert sein.
4. Neue Versionen installierst du genauso: Android zeigt „Aktualisieren“, Einstellungen,
   Schlüssel und Gelerntes bleiben. Eine APK aus einer anderen Quelle (etwa ein Test-Build aus
   einer Cloud-Sitzung) hat eine andere Signatur; Android lehnt sie dann als Update ab.

Prüfsumme: Neben der APK liegt im Release eine Datei `.apk.sha256`. Wer sichergehen will, vergleicht sie mit
`sha256sum` der kopierten Datei.

## Updates in der App

Jeder Build erscheint zusätzlich im öffentlichen Repository
[github.com/Tra1c0/AgenticKeyboard/releases](https://github.com/Tra1c0/AgenticKeyboard/releases)
(Einrichtung einmalig: `docs/BUILD.md` → Installierbare APK). Die App braucht dafür kein Konto
und keinen Token.

1. Einstellungen → „Updates“. Die Seite zeigt den installierten Build und das Repository.
2. „Jetzt prüfen“: Darunter steht „Aktuell: Build 6“, „Build 7 verfügbar“ oder eine Meldung.
3. Ist ein neuer Build da, tippe „Build 7 herunterladen und installieren“. Darf die App noch
   keine Apps installieren, zeigt die Seite „Einstellung öffnen“: dort „Agent Keyboard Lab“
   erlauben und zurückkommen. Die App lädt die Datei, prüft Prüfsumme, Paketname und Signatur und
   übergibt sie an Android; du bestätigst die Installation im Systemdialog. Passt etwas nicht,
   wird die Datei gelöscht und nichts installiert.
4. Optional „Täglich nach Updates suchen“ (anfangs aus): Etwa einmal am Tag und nur mit Netz
   fragt die App nach einem neuen Build. Ab Android 13 fragt Android beim Einschalten, ob die App
   Benachrichtigungen zeigen darf. Ein neuer Build meldet sich einmal als „Agent Keyboard: Build 7
   verfügbar“; ein Tipp öffnet die Seite „Updates“, geladen wird erst nach deinem Tipp dort.
   Ausschalten beendet die tägliche Suche sofort.

Für Freunde: Die APK einmal von der Release-Seite oben im Browser des Telefons herunterladen und
installieren (Android fragt, ob der Browser Apps installieren darf). Danach kommen Updates wie
oben in der App. Wer lieber eine Update-App nutzt, kann in Obtainium die Adresse der
Release-Seite eintragen. Updates gehen nur, wenn die APK mit demselben Schlüssel signiert ist wie
die installierte App.

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

## Screenshot einfügen

1. Einstellungen → Screenshots → „Neue Screenshots in der Leiste anbieten“ einschalten und den
   Zugriff auf Bilder erlauben. Wählst du nur „ausgewählte Fotos“, sieht die Tastatur neue
   Screenshots nicht.
2. Screenshot machen, dann in ein Textfeld tippen. Drei Minuten lang steht der Screenshot als
   Pille in der Leiste.
3. Pille antippen: Das Bild wird eingefügt. Die Pille erscheint nur in Feldern, die Bilder
   annehmen. Langes Drücken (im
   flachen Look) oder das Kreuz blendet die Pille aus.

Die Tastatur gibt der App eine Kopie, die nach 10 Minuten gelöscht wird. Wechselst du das Feld,
bevor die Kopie fertig ist, wird nichts eingefügt. Noch auf keinem Gerät ausprobiert.

## Befehle (z. B. Rechner)

1. Tippe in ein Textfeld `@math` oder `@math 12*3,5`. Ein einzelnes `@` zeigt in der Leiste alle
   Befehle; ein Tipp auf einen Namen vervollständigt ihn.
2. Mit Rechnung zeigt die Leiste das Ergebnis als Chip, z. B. „12 × 3,5 = 42“. Antippen ersetzt
   `@math 12*3,5` durch genau diesen Text.
3. Der Chip „Rechner“ öffnet statt der Tasten einen Rechner; dein Text bleibt dabei unverändert. Oben siehst
   du die Rechnung und darunter, was eingefügt würde. Darunter wählst du die Ausgabe: Rechnung,
   nur Ergebnis, LaTeX oder „x² ½“ (Hochzahlen, Brüche). „Einfügen“ ersetzt `@math …` durch das
   Ergebnis (ist der Befehl inzwischen weg, landet es an der Schreibmarke). Die Taste „fx“ zeigt
   Funktionen: `sin cos tan` (in Grad), `asin acos atan`, `ln log`, `√ ∛`, `root` (n-te Wurzel,
   z. B. `root(32;5)`), `!`, `π`, `e`. Auch tippbar: `@math wurzel(16)`, `@math 2pi`. „Schließen“ bricht ab,
   `@math` bleibt dann stehen.

Solange du nichts antippst, passiert nichts; der Befehl bleibt einfach als Text stehen. In
Passwortfeldern, im Inkognito-Modus und bei gesperrtem Gerät gibt es keine Befehle. Unter
Einstellungen → Befehle schaltest du Befehle ab oder wählst statt `@` ein anderes Zeichen (`#`,
`$`, `%`, `~`, `§`), etwa wenn du in einer App oft Leute mit `@` erwähnst. Weitere Befehle
stehen unten. Noch auf keinem Gerät ausprobiert.

### Weitere Befehle

- `@contact Ann`: Kontakt antippen, dann Nummer, E-Mail oder Adresse. Beim ersten Mal erscheint
  „Kontakte erlauben“; das öffnet Einstellungen → Befehle, dort den Zugriff erlauben.
- `@url heise.de` öffnet die Seite. `@url` allein schlägt Adressen aus Zwischenablage und Feld vor.
  „Bildschirm lesen“: einmal einschalten unter Einstellungen → Befehle → „Bildschirm lesen“ →
  „Bedienungshilfen öffnen“. Auf dem Xiaomi vorher: App-Info von „Agent Keyboard Lab“ → ⋮ →
  „Eingeschränkte Einstellungen zulassen“. Der Dienst liest nur, wenn du den Chip antippst, und
  speichert nichts. Er liest auch Apps mit Bildschirmschutz (z. B. Banking-Apps), Passwortfelder
  aber nie. Vor Android 14 erkennt er andere vertrauliche Felder wie Einmalcodes nicht; tippe den
  Chip dort nicht an.
- `@such Begriff`, `@zeichen pfeil`, `@emoji herz`, `@conv 5 oz` (auch `@conv 5 oz in g`).
- `@snip name`: Bausteine legst du unter Einstellungen → Befehle → „Textbausteine“ an.
- `@clip wort`: braucht Einstellungen → Optionen → Zwischenablage-Verlauf. Einträge, die als
  vertraulich markiert waren oder in einem Passwortfeld kopiert wurden, zeigt er nicht. Ausnahme:
  vor diesem Update kopierte Einträge, die die Tastatur nicht mehr als aktuellen Clip sieht.
- `@wf name`: Workflow antippen; das Panel sagt „Workflow vorgemerkt“. Panel schließen, Text
  markieren, Textaktionen wieder öffnen (Stift): Der Workflow ist vorbereitet, du gibst ihn wie
  gewohnt frei. Ist schon Text markiert, wird er sofort vorbereitet. Ein Feldwechsel verwirft die
  Wahl.
- `@ki Frage`: Chip „✦ KI fragen (Anbieter): …“ antippen – das sendet genau die getippte Frage
  (eigener Schlüssel nötig). Drei wandernde Punkte zeigen „Frage läuft …“; „Abbrechen“ stoppt.
  Die Antwort erscheint als Chip in der Leiste (lange Antworten seitlich wischen); antippen
  ersetzt `@ki …` durch die Antwort. „↻“ fragt noch einmal (neue Anfrage), „✕“ verwirft. Tippst
  du weiter oder setzt die Schreibmarke um, wird die Anfrage abgebrochen. Ein grauer Fehler-Chip
  öffnet bei fehlendem Schlüssel die KI-Einstellungen, sonst das Panel mit dem Grund.
- `@translate "Text" Stil`: z. B. `@translate "wann treffen wir uns morgen" natürliches slang
  spanisch`. Der Text steht in Anführungszeichen ("…", „…“, “…”, «…», »…«, '…'), alles danach
  ist der Stil (Sprache, Ton, Slang); ohne Stil wird es natürliches Englisch. Chip „🌐 Übersetzen
  mit Anbieter: Stil“ antippen – das sendet genau Stil und Text (eigener Schlüssel nötig); danach
  wie bei `@ki`: „Übersetze …“, dann die Übersetzung als Chip antippen, um den Befehl zu ersetzen. Höchstens 500 Zeichen nach
  `@translate`; bei mehr verschwindet der Chip und die Autokorrektur greift wieder. Für längere
  Texte: Text markieren, Stift, „Mit KI übersetzen“.
- `@qr Text` erzeugt einen QR-Code, z. B. `@qr https://…` oder für WLAN
  `@qr WIFI:T:WPA;S:Netzname;P:Passwort;;`. „QR-Code einfügen“ (in Chats mit Bildern) oder „QR-Code teilen“.
- `@datum`, `@datum morgen`; `@termin Samstag halb acht Kino` öffnet einen Kalenderentwurf (gespeichert
  ist er erst in der Kalender-App); `@ort Bahnhofstraße 1 Köln` oder `@ort zuhause` (Orte unter
  Einstellungen → Befehle → „Gespeicherte Orte“).

## Design auswählen

Einstellungen → Aussehen → „Design“ oder im Tastaturmenü die Pille „Design“: Kachel antippen.
„Flacher Look“ setzt auch Reihen, Größen und Leiste; zurück geht es unter Tastaturprofile.
Mit dem flachen Tastenstil sehen auch die Einstellungen moderner aus.

## Aussehen: flacher Look

Einstellungen → Tastaturprofile → „Flacher Look (Vorlage)“ → „Vorlage anwenden“. Das stellt
Stil, Farben, Icons, Zahlenreihe, Symbolhinweise und die Größen für das Hochformat in einem
Schritt ein. „Vorherigen Look wiederherstellen“ setzt genau diese Einstellungen auf den Stand
vor dem ersten Anwenden zurück; was du zwischen zwei Anwendungen von Hand geändert hast, wird
dabei nicht gesichert.

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
- Die Zwischenablage-Taste fügt den aktuellen Inhalt der Zwischenablage an der Schreibmarke
  ein, solange der Zwischenablageverlauf aus ist (so ist er anfangs). Erst mit eingeschaltetem
  Verlauf (Einstellungen → Optionen → „Zwischenablage-Verlauf aktivieren“) öffnet sie die
  Zwischenablage-Ansicht. In der Ansicht: Karte antippen fügt ein, lange drücken heftet an, zur Seite wischen entfernt. Oben
  links geht es zurück zu den Tasten, oben rechts leerst du den Verlauf.
- Die Pille mit kopiertem Text oder Bild in der Leiste erscheint nur, wenn
  „Zwischenablagen-Inhalt vorschlagen“ (Einstellungen → Textkorrektur) an ist. Antippen fügt ein, lange
  drücken blendet sie aus.
- Das Aktionsrad erreichst du im flachen Look über den Leertasten-Langdruck (Einstellungen →
  Tastaturprofile → Gesten), die Stift-Taste öffnet das Panel.

## Autokorrektur

Einstellungen → Gelernte Wörter → Abschnitt „Autokorrektur“:

- „Umlaut-Wörter behalten“ (an): Aus „mochte“ wird nicht mehr automatisch „möchte“, wenn beide
  Wörter existieren. „möchte“ steht dann als Vorschlag in der Leiste.
- „Richtige Wörter nie ändern“ (aus): Automatisch korrigiert werden nur noch Wörter, die nicht im
  Wörterbuch stehen.
- „Rückgängig gemachte Korrekturen merken“ (aus): Korrigiert die Tastatur ein Wort falsch und du
  drückst sofort die Löschtaste, kommt dein Wort zurück, und dieses Paar wird nie wieder
  automatisch angewendet. „Gemerkte Paare vergessen“ löscht alle.
- „Meine Tippfehler lernen“ (aus): Wenn du nach einem Tippfehler das richtige Wort in der
  Vorschlagsleiste antippst, merkt sich die Tastatur das. Nach dem dritten Mal korrigiert sie
  diesen Tippfehler selbst. Gibt es das getippte Wort wirklich (mochte), lernt sie es nur für
  das Wort davor (ich möchte). „Gelernte Tippfehler vergessen“ löscht alles.

## Trefferzonen

Einstellungen → Gelernte Wörter → Abschnitt „Trefferzonen“:

1. „Trefferzonen an mich anpassen“ einschalten.
2. „Kalibrieren“ antippen und den angezeigten Text so abtippen, wie du sonst tippst. Tippfehler
   sind egal, die Tastatur gleicht sie ab.
3. „Auswerten“: Du siehst pro Taste, wohin du daneben triffst. „Übernehmen“ speichert es.

Danach lernt die Tastatur beim normalen Tippen weiter: aus getippten (nicht gewischten) Wörtern,
auch wenn du einen Vertipper über die Vorschlagsleiste korrigierst. Gespeichert wird alle zehn
Wörter; wird die Tastatur vorher beendet, gehen bis zu neun Wörter verloren. „Trefferzonen zurücksetzen“ löscht alles. Umlaute werden nur vermessen,
wenn sie eigene Tasten haben (Layout QWERTZ+); ß hat keine eigene Taste.

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
