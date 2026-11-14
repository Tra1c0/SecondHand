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

1. Einstellungen → „Updates“ (unten auf der Startseite) oder System & Daten → Überschrift der
   Karte „Updates“. Die Seite zeigt den installierten Build und das Repository. „Jetzt prüfen“
   gibt es auch direkt auf der Karte in System & Daten.
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
in der Tastatur über den Pfeil links in der Leiste und dann das Zahnrad.

## Einstellungen finden

Die Startseite der Einstellungen (dunkel, mit farbigen Kacheln) hat oben die Lupe für die Suche,
darunter „Dein Look“ mit „Ändern“ (öffnet Aussehen), die Schnellschalter Inkognito, RGB-Licht,
Vibration und Symbole malen (ein Tipp schaltet um) und sieben Bereiche. Ganz unten: Diagnose,
Updates (mit deiner Build-Nummer) und Über. Jeder Bereich außer Tippen & Korrektur zeigt vorerst
eine Karte „Seiten“, die die bisherigen Seiten öffnet. Wo diese Anleitung „Einstellungen → X“ schreibt, liegt X jetzt in
diesem Bereich:
Updates (mit deiner Build-Nummer) und Über. Die Bereiche ohne eigenen Aufbau zeigen vorerst eine
Karte „Seiten“, die die bisherigen Seiten öffnet. Wo diese Anleitung „Einstellungen → X“ schreibt,
liegt X jetzt in diesem Bereich:

| Bereich | Seiten |
| --- | --- |
| 01 Aussehen | eigene Seite (siehe unten): Designs, RGB-Licht, Größe & Form, Schrift & Emoji, Hintergrund & Farben; „Alle Aussehen-Einstellungen“ = bisherige Seite Aussehen |
| 02 Tippen & Korrektur | eigene Seite (siehe unten); Textkorrektur = „Alle Korrektur-Einstellungen“, Gelernte Wörter, Wörterbücher, Persönliches Wörterbuch |
| 03 Tasten & Sprachen | eigene Seite (siehe unten): Sprachen & Layouts, Sekundäre Layouts, Optionen (über „Weitere Optionen“) |
| 04 Gesten & Gefühl | eigene Seite (siehe unten): Leertaste, Wischen über die Buchstaben, Symbole malen, Vibration & Ton, Treffen & Wischen |
| 05 Leiste & Befehle | eigene Seite (siehe unten); Leiste = „Knöpfe wählen und sortieren“, Befehle = „Alle Befehls-Einstellungen“, Screenshots |
| 06 KI & Automationen | eigene Seite (siehe unten): Anbieter und Schlüsselstatus, Skill-Tasten, Texte, Workflows, Diktat & Verlauf |
| 07 System & Daten | eigene Seite (siehe unten): Updates, Profile, Sichern, Für Fortgeschrittene, Über; dazu Tastaturprofile, Updates, Diagnose, Erweitert („Alle erweiterten Einstellungen“), Gestendaten-Erfassung (nur in der Sammelphase), Über („Alle Infos und Links“) |

Zurück geht es mit dem runden Pfeil oben links oder der Zurück-Geste.

### Tippen & Korrektur

Oben wählst du die Autokorrektur in einem Tipp:

- **AUS**: nur Vorschläge, nie automatisch (schaltet „Autokorrektur“ aus).
- **BEHUTSAM**: korrigiert nur Wörter, die es nicht gibt, und lässt Umlaut-Wörter stehen
  (Autokorrektur an, „Richtige Wörter nie ändern“ und „Umlaut-Wörter behalten“ an, Stärke „Mäßig“).
- **MUTIG**: korrigiert auch knappe Fälle (Autokorrektur an, „Richtige Wörter nie ändern“ aus,
  Stärke „Sehr stark“).

Hervorgehoben ist die Stufe, die zu deinen Einstellungen passt. Passt keine (zum Beispiel nach dem
Installieren oder wenn du einen Schalter darunter änderst), steht „Eigene Einstellung“ darunter.
Darunter schalten Regeln, Vorschläge, Leerzeichen und Lernen direkt um. Ausgegraut heißt: Der
Schalter wirkt gerade nicht (die Regeln brauchen die Autokorrektur, „Wortvorschläge“ die
Vorschlagsleiste, „Doppeltipp = Punkt“ eine Leertaste ohne Doppeltipp-Geste). „Kontakte und
App-Namen“, „Alle Korrektur-Einstellungen“ (Stärke als Regler, personalisierte Vorschläge,
anstößige Wörter …), „Wörterbücher“, „Persönliches Wörterbuch“ und „Gelernte Wörter“ öffnen die
bisherigen Seiten, „Was ich mir gemerkt habe“ die Korrekturen, das Gedächtnis und die Wörter aus
anderen Sprachen (Abschnitte „Autokorrektur“ und „Gedächtnis“ unten). „Lernen pausieren“ behält das Gelernte; Lernen ganz
ausschalten (und löschen)
geht unter „Alle Korrektur-Einstellungen“ → „Personalisierte Vorschläge“.

Unter „Regeln“ steht auch **Formatwächter** (anfangs an). Er prüft E-Mail-, Telefon- und
Kommazahl-Felder sowie Textfelder, in denen nur eine IBAN steht, sobald die Schreibmarke am Ende
steht, und zeigt dann einen Chip in der Leiste, zum Beispiel „gmail.com?“ bei `max@gmial.com`,
„📞 +49 1512 3456789“ bei `0151 23456789`, „● IBAN gültig · in Viererblöcke“ (grüner Punkt; roter
Punkt: „IBAN-Prüfziffer falsch“) oder „Üblich: 12,5“. Erst dein Tipp auf den Chip ersetzt den Text
im Feld; graue Chips sind nur Hinweise und ändern nichts. Echte Domains anderer Länder wie
`hotmail.fr` oder `posteo.at` lässt er stehen. Telefonnummern ohne Ländervorwahl liest er nach dem
Land der Tastatursprache, sonst nach dem Land deiner Systemsprache (in Österreich also `0664 …` →
`+43 664 …`). In Passwortfeldern, in Feldern ohne Lernen, im Inkognito-Modus und bei gesperrtem
Gerät bleibt er still.
**04 Gesten & Gefühl** hat seinen eigenen Aufbau, alle Werte sind deine echten Einstellungen:

- Karte „Leertaste“: Das Bild zeigt die Rastpunkte eines seitlichen Wischs (Tick, Tick, am
  letzten Punkt die Aktion). Darunter die sechs Gesten (← → ↑ ↓ Wischen, Halten, Doppeltipp) mit
  ihrer Aktion; ein Tipp öffnet dieselbe Liste wie die bisherige Seite „Leertaste“, „Standard“
  nimmt die eigene Wahl zurück. Doppeltipp ohne Aktion zeigt „Punkt + Leer“, wenn
  „Punkt plus Leerzeichen“ (Textkorrektur) an ist.
- „Über die Buchstaben wischen“: **Nichts** (Symbole malen und Gestenschreiben aus),
  **Symbole malen** oder **Gestenschreiben** (nur mit geladener Gesten-Bibliothek; sonst
  ausgegraut mit Hinweis auf System & Daten → Erweitert). Mit Bibliothek führt die Zeile
  „Gestenschreiben“ darunter zu Spur, Vorschau und Tempo.
- „Symbole malen“: Eigene Symbole (mit Anzahl) und Kalibrieren öffnen die Seite „Symbole malen“,
  „Aus Korrekturen lernen“ ist ein Schalter, „Spur anzeigen“ stellt ein, wie lange die Spur bleibt.
- „Vibration & Ton“: Vibration bei Tastendruck; solange sie an ist, Vibrationsdauer und „Auch bei
  ‚Bitte nicht stören‘“; Ton bei Tastendruck, solange er an ist mit „Lautstärke“.
- „Treffen & Wischen“: Trefferzonen anpassen, Trefferzonen kalibrieren (öffnet das Abtippen wie
  unter „Trefferzonen“), Löschen durch Wischen, Touchpad-Empfindlichkeit; liegt „Touchpad-Modus“
  bzw. „Sprache wechseln“ auf einem Leertasten-Wisch, auch „Scrollen am Touchpad-Rand“ bzw.
  „Wischweite für Sprachwechsel“.
**03 Tasten & Sprachen** ist schon nach dem Entwurf gebaut: Oben steht je eingeschaltete Sprache
eine farbige Karte mit Layout und Wörterbüchern; die Sprache, die du gerade benutzt, ist hell
mit weißem Rahmen und „aktiv“. Ein Tipp auf eine Karte öffnet die Seite dieser Sprache (Layout,
weitere Sprachen, Wörterbücher). „Sprache hinzufügen“ öffnet Sprachen & Layouts. Darunter die
Schalter und Zeilen der früheren Seite Optionen (Zahlenzeile, Emoji-Taste, Popups, Hinweise …)
und aus Erweitert Langdruck-Dauer, Währungen, Zeitstempel-Format und „Zurück zu den Buchstaben“
(ein Tipp klappt die vier Schalter auf). Alles Übrige der alten Seite Optionen steht unter
„Weitere Optionen“; Vibration und Ton liegen bei Gesten & Gefühl, die Zwischenablage bei
Leiste & Befehle.

### Leiste & Befehle

„Leiste“ heißt hier, was HeliBoard „Symbolleiste“ nannte: die Zeile über den Tasten. Oben wählst
du in einem Tipp, was sie zeigt:

- **TASTEN + VORSCHLÄGE**: ausklappbar, angeheftete Knöpfe bleiben (HeliBoard: „Leisten-Tasten und
  Vorschläge“).
- **NUR VORSCHLÄGE**: nur Vorschläge und angeheftete Knöpfe.
- **VERSTECKT**: keine Leiste, mehr Platz für Tasten.

Steht „Eigene Einstellung“ darunter, ist der vierte Modus „Nur Leisten-Tasten“ gewählt (unter
„Knöpfe wählen und sortieren“ → „Leisten-Modus“). Die Karte „@-Befehle“ zeigt das Präfix und alle
Befehle; ein Tipp auf einen Befehl schaltet ihn einzeln an oder aus, der Schalter oben rechts
schaltet das Erkennen getippter Befehle ganz aus. `@contact` fragt beim Einschalten nach dem
Zugriff auf Kontakte. Darunter: „Knöpfe wählen und sortieren“ (die bisherige Leisten-Seite mit
Modus, Tasten, angehefteten Tasten, Zwischenablage-Tasten), „Automatisch zeigen“ (nur bei TASTEN +
VORSCHLÄGE), „Wischen blendet aus“, „Skill-Tasten anheften“ (zeigt, wie viele der drei angeheftet
sind) sowie Zwischenablage-Verlauf, Aufbewahrung (Regler), Bilder und Dateien, Screenshots
anbieten (fragt nach dem Zugriff auf Bilder), Emoji-Mix in der Leiste und Smart Paste (siehe
„Smart Paste: Aktionen für Kopiertes“). Präfix, Textbausteine,
Orte, Bildschirm lesen und den Emoji-Mix-Speicher findest du unter „Alle Befehls-Einstellungen“,
angeheftete Einträge zuerst und Dateigröße unter „Weitere Zwischenablage-Einstellungen“. „Meine
Daten & Vorschläge“ schaltet die Feld- und Kontext-Vorschläge und enthält deine eigenen Daten (siehe
„Vorschläge fürs Feld und Meine Daten“).

**06 KI & Automationen** hat seinen eigenen Aufbau, alle Werte sind deine echten Einstellungen.
Wie die Seite „KI-Anbieter“ lässt sie keine Bildschirmfotos zu. Bei gesperrtem Gerät zeigt sie nur
„Gerät entsperren …“ und eine Karte „Seiten“ zu KI-Anbieter, Skill-Tasten, Workflows und Diktat.

- „Anbieter“: drei Karten OpenAI, Anthropic, Google Gemini mit dem eingestellten Modell (bei
  Gemini „Modell-ID selbst eintragen“). Ein Tipp wählt den Anbieter für die KI-Aktionen der
  Tastatur. Darunter steht, ob für diesen Anbieter ein Schlüssel gespeichert ist (der Schlüssel
  selbst wird nie angezeigt). „Ändern“ öffnet die Seite „KI-Anbieter“ zum Eintragen oder Löschen
  des Schlüssels und der Modell-ID.
- „Skill-Tasten“: drei Kacheln mit Nummer und Namen deiner Skill-Tasten („Nicht belegt“, wenn
  leer); ein Tipp öffnet die Seite „Skill-Tasten“.
- „Texte“: Zielsprache für Übersetzungen und Tonfall beim Umformulieren; ein Tipp öffnet die
  Auswahl.
- „Workflows“: „Meine Workflows“ (mit Anzahl), „Neuer Workflow“ und „Import & Export“ öffnen die
  Seite „Workflows“.
- „Diktat & Verlauf“: „Diktat auf dem Gerät“ zeigt „Bereit“, „Erlauben“ (Mikrofon fehlt) oder
  „Nicht verfügbar“ (kein On-device-Erkenner, vor Android 12) und öffnet die Seite „Diktat“;
  „Auftragsstatus“ öffnet die Seite „KI-Anbieter“, wo die Liste unten steht.
### System & Daten

- Karte **UPDATES**: zeigt das letzte Ergebnis („Build 16 ist aktuell“, „Build 17 verfügbar“,
  „Noch nicht geprüft“, „Prüfung fehlgeschlagen“ oder „Installation fehlgeschlagen“ mit Grund),
  wann zuletzt geprüft wurde und ob täglich automatisch geprüft wird. „Jetzt prüfen“ fragt
  GitHub, nur bei diesem Tipp. „Diagnose“ öffnet die Diagnose-Seite. Ein Tipp auf die Überschrift öffnet die Seite „Updates“ (Schalter
  „Täglich nach Updates suchen“); ist ein neuer Build da, führt „Build 17 installieren“ dorthin
  zum Laden. Bei gesperrtem Gerät steht dort nur der Hinweis zum Entsperren.
- Karte **PROFILE**: je gesicherter Bereich (Design, Tastenanordnung, Gesten) eine Karte, dazu
  „Flacher Look“ und „+ Neues Profil“. Ein Tipp auf eine Profilkarte fragt nach und wendet sie mit
  „Anwenden“ an. Hell mit weißem Rahmen ist eine Karte, solange deine Einstellungen ihr
  entsprechen. „+ Neues Profil“ öffnet Tastaturprofile (sichern, exportieren, importieren,
  zurücksetzen, Flacher Look zurücknehmen).
- **Sichern**: „Sichern und Wiederherstellen“ (alle Einstellungen als Datei, derselbe Dialog wie
  bisher), „Profile exportieren“ (öffnet Tastaturprofile).
- **Für Fortgeschrittene**: Emoji-Version, URL-Erkennung, Hardware-Tastatur, Gesten-Bibliothek
  laden, Debug-Einstellungen (nur wenn freigeschaltet), Gestendaten (nur in der Sammelphase) und
  „Alle erweiterten Einstellungen“ für den Rest der alten Seite Erweitert (Symbole-Taste für den
  Ziffernblock, Emoji-Taste der Hardware-Tastatur, App-Symbol …).
- **Über**: Agent Keyboard mit Version und Build (fünfmal tippen schaltet die
  Debug-Einstellungen frei), Lizenz, Versteckte Funktionen, Protokoll speichern und „Alle Infos und
  Links“ (Wiki, Community, Quellcode).

**01 Aussehen** hat seinen eigenen Aufbau, alle Werte sind deine echten Einstellungen:

- „Designs“: Kachel antippen verwendet sie (deine eigenen Designs stehen vorne). „+ Neues Design“
  öffnet den Design-Editor mit deinem jetzigen Look, „Bearbeiten“ das gewählte eigene Design
  (oder lange auf eine eigene Kachel drücken). „Alle … Designs“ öffnet die Design-Seite mit allen
  Kacheln und Farbsätzen.
- „RGB-Licht“: Schalter rechts schaltet das Licht an oder aus, die Karte antippen öffnet die
  RGB-Seite.
- Größe & Form, Schrift & Emoji, Hintergrund & Farben: rechts steht der aktuelle Wert; antippen
  öffnet den bekannten Dialog. Ränder, Schriftgröße, Emoji und Hintergrundbild öffnen eine kleine
  Liste mit den einzelnen Einstellungen, Geteilte Tastatur die Schalter je Lage. Tag/Nacht und
  Navigationsleiste schaltest du direkt. „Tastenabstand“ erscheint nur mit Tastenrändern (unter
  „Alle Aussehen-Einstellungen“), wie bisher.
- „Alle Aussehen-Einstellungen“ ganz unten öffnet die bisherige Seite (Stil, Symbolstil,
  Tastenränder, Farben, Symbole anpassen, Leertastentext, Höhe der unteren Reihe).

Wo diese Anleitung „Einstellungen → Aussehen → X“ schreibt und X keine eigene Zeile hat, liegt X
unter „Alle Aussehen-Einstellungen“.

## Textaktionen (ohne Internet)

1. Text im Textfeld markieren.
2. Die Taste mit dem Stern rechts oben in der Leiste antippen. Es öffnet sich das Aktionsrad.
3. „Großschrift“, „Kleinschrift“ oder „Abstände“ zeigt das Ergebnis als Vorschau im Panel.
   „Textaktionen“ öffnet das Panel mit allen Aktionen.
4. Erst „Auswahl ersetzen“ ändert den Text. „Ergebnis kopieren“ legt ihn in die Zwischenablage.

Wenn du zwischen Vorschau und Ersetzen im Textfeld etwas änderst oder woanders hintippst,
verwirft die Tastatur das Ergebnis. Das ist Absicht: Öffne die Textaktionen dann neu.

In Passwortfeldern, im Inkognitomodus und bei gesperrtem Gerät sind alle Aktionen aus.

Optional: Die Leertaste kann das Rad öffnen, z. B. beim Halten (siehe „Leertaste“).

## Leertaste

Einstellungen → Gesten & Gefühl → Karte **Leertaste** (die bisherige Seite gibt es weiter unter
System & Daten → Erweitert → „Leertaste“ und Tastaturprofile → Gesten). Für jede Geste wählst du
eine Aktion:

- **Nach links / rechts / oben / unten wischen**: Wische auf der Leertaste los. Die Richtung des
  ersten Stücks entscheidet. „Cursor bewegen“, „Sprache wechseln“ und „Touchpad-Modus“ laufen
  mit, solange du wischst (auch zurück); alle anderen Aktionen passieren einmal pro Wisch: nach
  oben oder unten sofort, nach links oder rechts erst am letzten Rastpunkt (siehe unten).
- **Rastpunkte beim Wischen nach links oder rechts** (z. B. Rückgängig nach links, Wiederholen
  nach rechts): Auf dem Weg spürst du drei Rastpunkte, je einen leichten Tick bei etwa 0,8 und
  1,6 Tastenbreiten, den letzten etwas weiter (gut eine Tastenbreite danach, bei etwa 2,65). Der
  letzte fühlt sich wie Überdehnen an (stärker und länger) und führt erst dann die Aktion aus,
  genau einmal pro Wisch. Lässt du vorher los, passiert nichts. Ziehst du ein Stück zurück und
  wieder vor, tickt der Rastpunkt erneut. Rückgängig ist ein normaler Rückgängig-Schritt wie die
  Rückgängig-Taste. Die Ticks kommen nur, wenn unter Einstellungen → Optionen „Vibration bei
  Tastendruck“ an ist, und nicht bei „Bitte nicht stören“ (außer du hast Vibration dort
  erlaubt); der Android-Schalter für Berührungs-Haptik spielt keine Rolle. Kann dein Telefon die
  Stärke nicht steuern, ist der letzte Rastpunkt ein langer statt eines kurzen Pulses.
- **Halten**: Leertaste lange drücken, z. B. für das Aktionsrad oder „Tastatur auswählen“.
  Bewegst du den Finger dabei deutlich nach links oder rechts, ist das Halten sofort vorbei und
  nur der Wisch zählt; auch ein langsamer Wisch löst das Halten nie aus.
- **Doppeltippen**: Der erste Tipp schreibt wie immer ein Leerzeichen, ein zweiter Tipp direkt
  danach führt die Aktion aus statt ein zweites Leerzeichen zu schreiben. Solange Doppeltippen
  eine Aktion hat, ruht „Punkt plus Leerzeichen“.
- **Befehlsliste** tippt das Befehlspräfix (z. B. `@`) an einen Wortanfang; die Leiste zeigt dann
  alle Befehle. In Passwortfeldern, im Inkognito-Modus und bei ausgeschalteten Befehlen passiert
  nichts.
- **Standard** im Auswahlfenster stellt die Geste zurück: Sie macht dann wieder, was sie vor
  dieser Seite gemacht hat.
- **Rückgängig** und **Wiederholen** auf Halten oder Doppeltippen der Leertaste vibrieren wie ein
  normaler Tastendruck bzw. langer Druck, ohne eigene Ticks (die drei Ticks direkt nach der
  Aktion aus Build 12 gibt es nicht mehr). Die Rückgängig-/Wiederholen-Tasten in der
  Leiste vibrieren wie jede andere Taste.

Rad, Panel und Diktat behalten über jede Geste ihre Schutzregeln (Passwortfelder, Inkognito,
gesperrtes Gerät). Ein einzelner Tipp schreibt immer ein Leerzeichen.

## KI-Aktionen (mit eigenem Schlüssel)

1. Einstellungen → KI & Automationen → Karte „Anbieter“: OpenAI, Anthropic oder Google Gemini
   wählen, dann „Ändern“ (öffnet die Seite „KI-Anbieter“).
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

- Einstellungen → KI & Automationen → Skill-Tasten (eine Kachel antippen): drei Tasten mit je
  einer lokalen Aktion, einer KI-Aktion oder einem Workflow belegen. Damit sie in der Tastatur
  erscheinen, unter Einstellungen → Leiste & Befehle → „Skill-Tasten anheften“ einschalten
  (oder unter „Knöpfe wählen und sortieren“).
- Einstellungen → KI & Automationen → Meine Workflows: eigene Abläufe aus bis zu acht Schritten
  anlegen, z. B. erst Abstände aufräumen, dann korrigieren, dann übersetzen. Jeder KI-Schritt
  nennt Anbieter und Modell. Beim Start zeigt die Tastatur alle Schritte und fragt einmal nach der Freigabe.
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

1. Einstellungen → KI & Automationen → „Diktat auf dem Gerät“ → „Mikrofon erlauben“.
2. Schreibmarke dorthin setzen, wo der Text hin soll. Es darf nichts markiert sein, und ein
   angefangenes Wort muss beendet sein (z. B. mit einem Leerzeichen).
3. Im Panel „Diktieren (auf diesem Gerät)“ antippen. Wer oft diktiert, schaltet unter
   Einstellungen → Leiste & Befehle → „Knöpfe wählen und sortieren“ die Taste „Diktieren (auf dem Gerät)“ ein.
4. Sprechen, dann „Fertig – Text anzeigen“.
5. Text prüfen und „An Schreibmarke einfügen“.

Das Diktat nutzt nur die Spracherkennung, die Android auf dem Gerät bereitstellt (ab Android 12).
Fehlt sie oder das Sprachpaket für die Tastatursprache, meldet die Tastatur das und nimmt nichts
auf. Mit echtem Mikrofon wurde das Diktat noch nie ausprobiert.

Die Mikrofon-Taste, die es in HeliBoard schon gab, ist etwas anderes: Sie wechselt zu einer
Sprach-Tastatur des Systems, falls eine installiert ist.

## Screenshot einfügen

1. Einstellungen → Leiste & Befehle → „Screenshots anbieten“ einschalten (oder Einstellungen →
   Screenshots → „Neue Screenshots in der Leiste anbieten“) und den
   Zugriff auf Bilder erlauben. Wählst du nur „ausgewählte Fotos“, sieht die Tastatur neue
   Screenshots nicht.
2. Screenshot machen. Ist die Tastatur offen, steht er sofort als Pille in der Leiste, sonst
   beim nächsten Tippen in ein Textfeld; drei Minuten lang.
3. Die Pille steht zwischen den Wörtern statt der Vorschläge: im leeren Feld, nach einem
   Leerzeichen, einem Satzzeichen oder einer neuen Zeile. Tippst du den ersten Buchstaben eines
   Worts, zeigt die Leiste wieder die Vorschläge für dieses Wort; nach dem nächsten Leerzeichen
   ist die Pille wieder da. Steht die Schreibmarke direkt hinter einem Wort, bleibt die normale
   Leiste. Ein getippter Befehl (z. B. `@math`) zeigt seine Chips statt der Pille. Vorschläge des
   Passwortmanagers (Autofill) und eigene Vorschläge der App bleiben stehen; die Pille kommt erst,
   wenn sie weg sind.
4. Pille antippen: Das Bild wird eingefügt. Die Pille erscheint nur in Feldern, die Bilder
   annehmen. Langes Drücken (im flachen Look) oder das Kreuz blendet die Pille aus; danach
   kommen sofort wieder die normalen Vorschläge.

Die Tastatur gibt der App eine Kopie, die nach 10 Minuten gelöscht wird. Wechselst du das Feld,
bevor die Kopie fertig ist, wird nichts eingefügt.

Zwischenablage: Ist der Zwischenablage-Verlauf (Einstellungen → Leiste & Befehle) an, legt die
Tastatur jeden neuen Screenshot auch als Bild in ihren eigenen Verlauf (Zwischenablage-Taste).
Die System-Zwischenablage bleibt unberührt, andere Apps sehen ihn dort nicht. Es gelten die
Regeln des Verlaufs (Dateien erlaubt, Größengrenze, Aufbewahrungszeit, Anheften). Ein
Screenshot, den du bei gesperrtem Gerät oder in einem Inkognito-Feld machst, kommt nie in den
Verlauf, auch nicht nach dem Entsperren; ebenso keiner, solange die Tastatur noch in keinem Feld
war. Schaltest du die Screenshot-Pille aus, löscht die Tastatur diese Einträge wieder;
angeheftete bleiben, und ein Bild, das du danach selbst noch einmal kopiert hast, auch. Noch auf
keinem Gerät ausprobiert.

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
- Vorlagen: Tippst du einen Befehl ohne Argument (oder tippst in der `@`-Liste auf einen Namen),
  zeigt die Leiste nach den Chips des Befehls umrandete Vorlagen. Ein Tipp schreibt die Vorlage
  hinter den Befehl, sonst passiert nichts. Beispiele: `@zeichen` → „pfeile“ zeigt alle Pfeile;
  `@emoji` → „tiere“; `@conv` → „oz in g“ zeigt „1 oz = 28,35 g“, tippe dann die Zahl, z. B. `5`
  („5 oz = 141,7 g“); `@datum` → „morgen“; `@termin` → „morgen 18 Uhr“, danach den Titel tippen;
  `@translate` → `spanisch "`, dann den Text und das schließende `"` tippen.
- `@such Begriff`, `@zeichen pfeil`, `@emoji herz`, `@conv 5 oz` (auch `@conv 5 oz in g` oder
  `@conv oz in g 5`).
- `@snip name`: Bausteine legst du unter Einstellungen → Befehle → „Textbausteine“ an. Im Text
  gehen Platzhalter (die Knöpfe unter dem Textfeld fügen sie ein, darunter steht die Vorschau):
  `{datum}`, `{datum+3t}` / `{datum-1t}` (Tage, `w` = Wochen), `{uhrzeit}`, `{wochentag}`,
  `{zwischenablage}`, `{?Name}` und `{cursor}`; `{{` schreibt eine Klammer. Datum und Wochentag
  folgen der Tastatursprache (Deutsch, Englisch, Spanisch; sonst Deutsch). Unbekannte Platzhalter
  bleiben stehen, der Editor warnt rot. Beispiel: „Hallo {?Name}, danke für die Einladung am
  {datum+2t}, leider kann ich nicht. {cursor}“. Hat ein Baustein `{?…}`-Felder, wird der Befehl
  nach dem Tipp zu „@snip name Name: “: Wert tippen, Enter (oder „Weiter“/„Einfügen“ in der
  Leiste) springt zum nächsten Feld, nach dem letzten wird eingefügt; gleiche Namen fragt er
  einmal. „Abbrechen“ fügt nichts ein. Enter einer Hardware-Tastatur springt genauso weiter. Ein
  langer Wert geht bis etwa 450 Zeichen; kurz davor zeigt der Chip „(noch N Zeichen)“.
  `{zwischenablage}` setzt den aktuellen Clip nur ein, wenn der Zwischenablage-Verlauf an ist und
  der Clip nicht vertraulich war; sonst bleibt die Stelle leer und ein Hinweis erscheint. Unter
  „Nur in diesen Apps“ wählst du Apps („App wählen“) oder tippst Paketnamen; der Baustein
  erscheint dann nur dort. Bausteine von vor diesem Update werden wörtlich eingefügt, bis du sie
  einmal speicherst.
- `@clip wort`: braucht Einstellungen → Leiste & Befehle → Zwischenablage-Verlauf. Einträge, die als
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
  ist der Stil (Sprache, Ton, Slang); ohne Stil wird es natürliches Englisch. Der Text endet beim
  letzten passenden Anführungszeichen, Zitate im Text gehen also („… funktioniert "@translate"
  super“); im Stil dann keine Anführungszeichen derselben Art verwenden. Chip „🌐 Übersetzen
  mit Anbieter: Stil“ antippen – das sendet genau Stil und Text (eigener Schlüssel nötig); danach
  wie bei `@ki`: „Übersetze …“, dann die Übersetzung als Chip antippen, um den Befehl zu ersetzen. Höchstens 500 Zeichen nach
  `@translate`; bei mehr verschwindet der Chip und die Autokorrektur greift wieder. Für längere
  Texte: Text markieren, Stift, „Mit KI übersetzen“.
- `@qr Text` erzeugt einen QR-Code, z. B. `@qr https://…` oder für WLAN
  `@qr WIFI:T:WPA;S:Netzname;P:Passwort;;`. „QR-Code einfügen“ (in Chats mit Bildern) oder „QR-Code teilen“.
- `@datum`, `@datum morgen`; `@termin Samstag halb acht Kino` öffnet einen Kalenderentwurf (gespeichert
  ist er erst in der Kalender-App); `@ort Bahnhofstraße 1 Köln` oder `@ort zuhause` (Orte unter
  Einstellungen → Befehle → „Gespeicherte Orte“).
  `@termin` versteht Tage (`heute`, `morgen`, `übermorgen`, `+3`, `in 3 Tagen`, Wochentage,
  `24.12.`, `24. Dezember`, `2026-12-24`) und Uhrzeiten (`18:30`, `18 Uhr`, `6pm`, `halb acht`,
  `viertel nach acht`, `dreiviertel acht`, `zehn nach acht`, `Mittag`; eine nackte Zahl nur mit
  `um`/`gegen` davor oder `Uhr` dahinter), dazu `abends`/`nachmittags`. Auch Englisch und Spanisch.
  Der Rest wird der Titel. Ohne Uhrzeit ein Ganztagstermin, sonst eine Stunde lang.
- `@wecker` allein bietet die Vorlagen `6:30`, `7 Uhr`, `halb sieben`, `@timer` allein `5 min`, `10 min`,
  `halbe Stunde`, `1 h`.
- `@wecker 6:30 Training` oder `@wecker halb acht`: Chip „⏰ Wecker …“ antippen, die Uhr-App zeigt
  den neuen Wecker, dort bestätigen. Uhrzeiten wie bei `@termin`, dazu eine nackte Zahl
  (`@wecker 7`); `halb acht` ist hier 7:30, für 19:30 `abends halb acht` oder `19:30`. Die Uhr-App
  stellt immer das nächste Mal dieser Uhrzeit; ein anderer Tag (`@wecker Montag 7:00`) wird nicht
  übergeben, der Chip sagt dann „(Tag ignoriert)“.
- `@timer 10 min Nudeln`, `@timer 90 s`, `@timer 1:30` (1 Minute 30), `@timer 1 h 20`,
  `@timer halbe Stunde`: Chip „⏱ Timer …“ antippen, in der Uhr-App starten. Eine Zahl ohne
  Einheit am Anfang sind Minuten (`@timer 10 Nudeln`); höchstens 24 Stunden. Achtung: Eine Zahl
  direkt nach der Dauer zählt mit (`@timer 5 min 3 Eier` sind 5 Minuten 3 Sekunden); der Chip
  zeigt die erkannte Dauer, bevor du tippst.

### Sprachbefehle

Tippe `@` und eine deiner aktiven Tastatursprachen, ein Land oder ein Kürzel. Die Leiste zeigt
z. B. „🌐 Español (ES)“; ein Tipp darauf löscht den Befehl und stellt die Tastatur sofort auf
diese Sprache um.

- Kürzel: `@sp` (Spanisch), `@ger` (Deutsch), `@eng` (Englisch), dazu die Sprachcodes `@es`,
  `@de`, `@en`.
- Sprache: `@spanisch`, `@spanish`, `@español`/`@espanol`, `@castellano`; `@deutsch`, `@german`;
  `@englisch`, `@english`, `@inglés`.
- Land: `@spain`, `@spanien`, `@españa`, `@mexico`; `@germany`, `@deutschland`, `@österreich`
  (auch `@oesterreich`), `@schweiz`; `@usa`, `@uk`, `@england`. Ländercodes wie `@us`, `@ch`
  oder `@mx` zählen nicht, weil viele Namen so anfangen.
- Zwei Buchstaben zählen nur genau: `@sp`, `@es`, `@de`, `@en`, `@uk`. Ab drei Buchstaben
  reicht der Anfang eines Sprach- oder Ländernamens (`@spa`, `@deu`, `@mex`).
- Tippfehler ab vier Buchstaben, nur bei Sprachnamen und Kürzeln (nicht bei Ländern und Codes),
  und der erste Buchstabe muss stimmen: `@spnaish`, `@deustch`, `@englsih` gehen, `@psanish`
  nicht. Bis vier Buchstaben zählt nur ein Vertauschen zweier Nachbarn.
- Nach dem Leerzeichen bleibt der Chip nur für ein genaues Wort (`@sp `, `@spain `), nicht für
  einen Anfang oder Tippfehler (`@spnaish ` zeigt dann nichts mehr). Tippst du danach ein
  weiteres Wort (`@sp hola`), verschwindet der Chip und die Autokorrektur arbeitet normal.
- `@lang` zeigt alle aktiven Sprachen als Chips; `@lang spa` nur die passenden. Die Sprache, die
  du gerade benutzt, bekommt keinen Chip; trifft nur sie, steht da grau „Schon aktiv: Deutsch
  (DE)“, und ein Tipp darauf ändert nichts.
- Hat eine Sprache mehrere Layouts, gibt es einen Chip je Layout (z. B. „· QWERTY“).
- Nur Sprachen, die unter Einstellungen → Sprachen & Layouts aktiv sind. Ist nur eine aktiv,
  zeigt die Leiste „Nur eine Sprache aktiv“; ein Tipp öffnet die Spracheinstellungen. Schaltest
  du eine Sprache ab, während ihr Chip noch zu sehen ist, meldet ein Tipp „Diese Sprache ist
  nicht mehr aktiv“, und der Text bleibt.
- Sprachen in anderer Schrift (z. B. Russisch) erkennt die Tastatur nur über ihren Code und
  ihren Namen auf Englisch, Deutsch, Spanisch, Französisch, Italienisch oder Portugiesisch
  (`@russisch`, `@ru`), nicht in ihrer eigenen Schrift.
- Befehlsnamen haben Vorrang: `@url`, `@ki` usw. bleiben Befehle. Ein Sprachwort im Text eines
  Befehls (`@termin Treffen mit @sp`) bleibt Teil dieses Befehls. Erwähnungen wie `@lewin`,
  `@isa`, `@gerd` oder `@matt` lösen nichts aus. Beim Tippen mancher Namen blitzt nach drei
  Buchstaben kurz ein Sprach-Chip auf (`@ale…` über „alemán“, `@ing…` über „inglés“, `@col…`
  über „Colombia“, `@alle` über „allemand“); meist ist er mit dem nächsten Buchstaben,
  spätestens mit dem Leerzeichen wieder weg. Nur ein Name, der genau ein Sprach- oder
  Ländername ist (`@peru`), behält ihn.
- Vertippte Befehlsnamen: `@mtah` oder `@tranlate` zeigt den richtigen Namen als Chip; ein Tipp
  vervollständigt ihn nur. Auch hier ab vier Buchstaben und mit richtigem ersten Buchstaben
  (`@matt`, `@uri` bleiben Erwähnungen).
- Mit der Sprache stellt auch die Autokorrektur um: Wörterbuch, Vorschläge und gelernte Tippfehler
  sind ab dem nächsten Wort die der neuen Sprache (die ersten Buchstaben nach dem Umschalten
  können noch ohne Korrektur bleiben, solange das Wörterbuch lädt). Das gilt auch für Sprachen mit
  zweiter Sprache („Mehrsprachiges Tippen“): Die gewählte Sprache ist die bevorzugte.
- Die gewählte Sprache bleibt, solange du im selben Feld bleibst, auch wenn die App das Feld
  nach dem Löschen des Befehls neu startet. Wechselst du in ein anderes Feld, gilt dort wieder,
  was die App oder „Sprache pro App merken“ vorgibt (wie bei der Sprachtaste).
- Abschalten unter Einstellungen → Befehle → `@lang`; dort stehen auch die Kürzel deiner aktiven
  Sprachen.

### Emoji-Mix

Zwei Emojis werden zu einem Sticker aus Googles Emoji Kitchen gemischt (wie bei Gboard). Welche
Paare es gibt, weiß die Tastatur ohne Internet. Das Bild selbst lädt sie erst nach deinem Tipp
von Google (www.gstatic.com); gesendet werden dabei nur die beiden Emojis, kein anderer Text.

- `@mix 🐶🔥` (auch `@mix 🐶 + 🔥`): Chip „🐶 + 🔥 mischen · Google“ antippen. Während des
  Ladens wandern drei Punkte, dann erscheint der Sticker als Chip mit „einfügen“ (in Feldern,
  die Bilder annehmen) oder „teilen“. Ein Tipp darauf fügt ihn ein und löscht danach den Befehl.
- `@mix 🐶` schlägt bis zu sechs Partner vor, die es wirklich gibt; `@mix` allein bietet
  Vorlagen (😂 🥰 🐶 🔥 ❤️).
- Zwei Emojis direkt vor der Schreibmarke (z. B. du tippst 🐶🔥): Die Leiste bietet denselben
  Chip an. Nach dem Einfügen verschwinden die zwei Emojis, aber nur, wenn sie noch unverändert
  vor der Schreibmarke stehen. Beim Teilen bleiben sie stehen. Nimmt die App das Bild nicht an,
  bleiben Emojis bzw. Befehl stehen („Das Bild wurde nicht eingefügt“); setzt eine App das Bild
  selbst an die Schreibmarke, bleiben sie ebenfalls stehen („Bild eingefügt …“), dann von Hand
  löschen. Dasselbe gilt für `@qr`. Abschalten: Einstellungen →
  Leiste & Befehle → „Emoji-Mix in der Leiste“ (der Schalter gilt nur, solange Befehle an sind).
- In der Emoji-Ansicht hat die untere Reihe statt Leertaste und Lupe zwei gleich breite Tasten:
  „Emojis suchen“ (wie bisher die Lupe; ohne Emoji-Wörterbuch steht dort die Leertaste) und
  „Emoji-Mixer“. Der Mixer blendet über den Emojis eine schmale Leiste ein („2 Emojis wählen“).
  Tippe zwei Emojis an; sie werden dann nicht geschrieben. Mit dem zweiten lädt die Tastatur
  die Mischung („lädt von Google“), der Sticker gleitet groß herein. Sticker antippen fügt ihn
  ein (oder teilt ihn). Ein gewähltes Emoji antippen entfernt es; ein drittes ersetzt das
  zweite. Gibt es keine Mischung, steht dort „Keine Mischung“ mit Partnern, die eine haben.
  Nochmals „Emoji-Mixer“ oder das Verlassen der Emoji-Ansicht beendet den Mixer.
- „Zuletzt gemischt“: Solange im Mixer noch kein Paar gewählt ist, zeigt eine Reihe die letzten
  bis zu 12 Sticker, deren Bild noch auf dem Gerät liegt (ohne Internet). Antippen fügt den
  Sticker ein (oder teilt ihn), wie einen frisch gemischten. Neu in der Reihe ist ein Sticker,
  sobald er nach deinem Tipp geladen ist; Einfügen oder Teilen rückt ihn nach vorn. Im
  Inkognito-Modus merkt sich die Tastatur nichts. „Gespeicherte Mischungen löschen“ leert die
  Reihe; Sticker, die wegen der 20-MB-Grenze gelöscht wurden, verschwinden aus ihr.
- „Keine Mischung“ heißt: Google hat dieses Paar nicht. Bei „Laden fehlgeschlagen“ (kein Netz)
  versucht ein neuer Tipp es noch einmal. Steht im Mixer „Tippen, um die Mischung von Google zu
  laden“ (z. B. nachdem du die gespeicherten Mischungen gelöscht hast), lädt erst dieser Tipp.
- Geladene Sticker bleiben auf dem Gerät (höchstens 20 MB, die ältesten gehen zuerst) und
  erscheinen beim nächsten Mal sofort ohne Internet. Löschen: Einstellungen → Befehle →
  „Gespeicherte Mischungen löschen“.
- Nicht in Passwortfeldern, im Inkognito-Modus oder bei gesperrtem Gerät; dort bleibt der
  Mixer aus („Der Emoji-Mixer ist in diesem Feld aus“).

## Zeichen-Kunst

Bilder und Schriftzüge aus Zeichen (Braille-Punkte, Blöcke, ASCII, Emojis) und Text in
Unicode-Stilen wie 𝓢𝓬𝓱𝓻𝓲𝓯𝓽 oder 𝗙𝗲𝘁𝘁. Alles passiert auf dem Telefon, ohne Internet.

- Öffnen: die Emoji-Taste in der unteren Reihe lange drücken, im Popup auf den Stift „✎“ gleiten
  und loslassen (die Emoji-Taste muss da sein: Einstellungen → 03 Tasten & Sprachen →
  „Emoji-Taste“; der flache Look schaltet sie ein). Ein kurzer Tipp öffnet weiter die Emojis.
  Statt der Tasten erscheint das Fenster; das Feld bleibt unverändert.
- Oben steht der Text aus dem Feld („Text: …“, bis 300 Zeichen um die Schreibmarke, oder was du
  markiert hast). Antippen öffnet die Tasten des Fensters: tippen, ⌫ löscht, ↵ neue Zeile, „123“
  für Ziffern und Zeichen, „Fertig“ schließt sie. Diese Tasten schreiben nur in das Fenster.
- Reiter (oben):
  - **Zeichnen**: Stift, Radierer, Linie, Kreis (vom Aufsetzen bis zum Loslassen), Füllen (tippen
    in eine geschlossene Fläche), Stärke Dünn/Mittel/Dick, „Zurück“ nimmt den letzten Schritt
    zurück, „Leeren“ löscht alles.
  - **Bild**: die neuesten Fotos und Screenshots; ein Tipp legt das Bild unter die Zeichenfläche.
    „Foto umwandeln“ macht es selbst zu Zeichen, „Nur Vorlage“ zeigt es nur zum Nachzeichnen.
    Kontrast, Schwelle, Raster/Streuung (für Grautöne), Umkehren, Foto entfernen. Der Radierer
    nimmt auch Teile des Fotos weg. Braucht die Bild-Berechtigung („Bilder erlauben“ öffnet die
    App-Seite der Android-Einstellungen; dabei schließt sich das Fenster).
  - **Stil**: Braille (Standard), Blöcke, Halbblöcke, ASCII, Emoji (nochmals tippen wechselt die
    Farben ⬛⬜ → 🟥⬜ → ❤️🤍 → 🟩⬛), Eigene (nochmals tippen: eigene Zeichen von hell nach dunkel
    eingeben, z. B. „ .oO@“; zwei Zeichen = schwarz-weiß). Dazu Umriss, Umkehren, Zuschneiden
    (nur der gezeichnete Teil) und Größe 100/75/50 %.
  - **Text**: „Groß-Schrift“ zeichnet den Text als große Buchstaben (Schriften Mini, Standard, Fett;
    Größe auto oder 1–3; „Stil: …“ wählt, woraus die Buchstaben bestehen; Umriss = hohle
    Buchstaben). „Unicode-Stil“ schreibt ihn in Schriftzeichen-Stilen: Serif, Serifenlos,
    Schreibschrift, Fraktur, Doppelstrich, Monospace, Kreise, Quadrate, Kapitälchen, Kopfüber, dazu
    Fett, Kursiv, Durchgestrichen, Unterstrichen, frei kombinierbar. „Normal“ lässt ihn, wie er ist.
  - **Rahmen**: Linie, Doppelt, Rund, Emoji (nochmals tippen wechselt ✨ ⭐ ❤️ 🌸 🔥), Innenabstand.
  - **Anordnung**: Untereinander oder Nebeneinander, Text oben, Zentriert oder Links, Abstand,
    Spiegeln.
  - **App**: WhatsApp, Telegram, Discord, Signal, Andere. Die App des Felds wird erkannt. Der
    Regler stellt die Breite in Zeichen ein (8–60, je App gemerkt); die Chatblase darunter zeigt,
    ob es auf ein Handy passt: zu breite Zeilen brechen dort um wie im Chat. „Codeblock“ setzt
    ``` drumherum, damit die App eine Schrift mit gleich breiten Zeichen nimmt (WhatsApp und
    Discord an, Telegram wählbar, Signal kann es nicht). „⠀ statt Leerzeichen“ (an) füllt Lücken
    mit dem unsichtbaren Braille-Zeichen, damit die App sie nicht wegkürzt.
- Jede Änderung zeichnet die Vorschau sofort neu. Darunter steht, wie breit die Kunst ist, oder
  eine Warnung, wenn sie in der App vermutlich zerfällt (z. B. „Rahmen und Nebeneinander verrutschen
  ohne Codeblock“, „Emojis und Schrift-Stile sind im Codeblock nicht gleich breit“).
- Übernehmen, immer nur per Tipp: **Einfügen** setzt die Kunst an die Schreibmarke. **Ersetzen**
  (nur wenn hinter der Schreibmarke nichts steht) setzt sie an die Stelle des gelesenen Texts, so
  wird aus „Hallo“ z. B. „𝐇𝐚𝐥𝐥𝐨“. Danach heißt der Knopf 8 Sekunden lang **Rückgängig**: ein Tipp
  nimmt genau das wieder heraus (nur solange danach nichts im Feld geändert wurde). **Kopieren**
  legt die Kunst in die Zwischenablage. **Baustein** fragt nach einem Namen (nur Buchstaben) und
  speichert sie als Textbaustein, später mit `@snip Name`. „Schließen“ verwirft alles.
- Nicht in Passwortfeldern, im Inkognito-Modus oder bei gesperrtem Gerät: Dort fehlt der Stift
  bzw. es erscheint „Zeichen-Kunst gibt es in diesem Feld nicht.“
- Grenzen: Screenreader (TalkBack) lesen Zeichen-Kunst als Zeichensalat vor. Beim Empfänger
  können ein sehr kleiner Bildschirm oder eine große Schrift die Zeilen umbrechen lassen.

## Symbole malen

Statt Wörter zu wischen, malst du Sonderzeichen auf die Tastatur. Einschalten: Einstellungen →
Gesten & Gefühl → „Über die Buchstaben wischen“ → **Symbole malen** (oder auf der Seite „Symbole
malen“ der Schalter „Symbole malen (statt Gestenschreiben)“). Gestenschreiben ist dann aus; Tippen,
langes Drücken für Sonderzeichen und das Wischen auf Leer- und Löschtaste bleiben wie vorher.

- Male mit einem Finger auf den Buchstabentasten, zügig wie beim Wischen. Die Spur bleibt kurz
  sichtbar. Direkt nach dem Tippen eines Worts darfst du sofort malen: Der erste Strich zählt,
  sobald er etwa eine Dreivierteltaste vom Aufsetzpunkt weg ist; ein Tipp, der beim schnellen
  Tippen nur ein Stück rutscht, bleibt ein Buchstabe. Ist so ein erster Strich direkt nach dem
  Tippen kürzer als anderthalb Tasten und gerade, und folgt kein weiterer Strich, war es ein
  gerutschter Tipp: Es kommt der Buchstabe, auf dem du aufgesetzt hast, kein „–“ oder „/“. Dasselbe
  gilt, wenn so ein einzelner Strich nicht erkannt wird (statt „Nicht erkannt“). Einen kurzen
  Strich direkt nach einem Wort malst du darum etwas länger oder mit einem zweiten Strich (=).
  Liegt dabei ein zweiter Finger auf der Tastatur (zwei Daumen), beginnt direkt nach dem Tippen
  keine Zeichnung, beide Buchstaben kommen.
- Ein Symbol aus mehreren Strichen: den nächsten Strich innerhalb der Pause ansetzen. Die Pause
  ist anfangs 0,9 Sekunden und passt sich deinem Tempo an (etwa 2,5-mal so lang wie deine übliche
  Lücke zwischen zwei Strichen, mindestens 0,6 und höchstens 1,5 Sekunden). Kurze Striche zählen
  dann mit, auch wenn sie auf einer Satzzeichen- oder Zifferntaste beginnen, aber nur in der Nähe
  der Zeichnung (etwa ihre halbe Größe drumherum, mindestens drei Tasten breit). Tippst du weiter
  weg, kommt das Symbol und dann die Taste, so geht beim Weitertippen nichts verloren. Auf
  Leertaste, Enter, Löschen, Umschalt und „?123“ beginnt nie ein Strich. Nach der Pause wird das
  Symbol geschrieben; tippst du vorher eine andere Taste, kommt das Symbol zuerst.
  Direkt neben der Zeichnung (bis etwa drei Tasten) kann ein stark verwackelter Tipp noch als
  Strich zählen; dann fehlt der Buchstabe und das Symbol ändert sich. Ein Tipp auf den Chip in der
  Leiste schreibt das Symbol vorher.
- Hältst du kurz inne (etwa eine Zehntelsekunde), zeigt die Leiste, was jetzt geschrieben würde:
  „✎ →“ und bis zu zwei andere Symbole („✎ ?“, solange nichts passt). Ein Tipp darauf schreibt das Symbol sofort, ohne die Pause
  abzuwarten; ein Tipp auf ein anderes Symbol schreibt dieses.
- Ein Tipp in dieser Pause nahe der Zeichnung (der Finger bewegt sich kaum, weniger als etwa 8 dp, so lange er auch
  liegt) schreibt das Symbol und dann den Buchstaben. Bewegt er sich weiter, ist er ein Strich des
  Symbols. Punkte tippst du einfach: Liegt der Tipp ungefähr dort, wo ein Symbol einen Punkt hat
  (÷ über und unter dem Strich, % oben links und unten rechts vom Schrägstrich, ‰, ! und ? unter dem
  Strich, ¡ und ¿ darüber; bei eigenen Symbolen dort, wo du den Punkt gemalt hast), wartet er als
  möglicher Punkt. Nach der Pause entscheidet die ganze Zeichnung: Ergibt sie ein Symbol mit genau
  so vielen Punkten an ihren Plätzen, bleiben es Punkte; sonst kommt das Symbol ohne sie und danach
  die getippten Buchstaben in deiner Reihenfolge (sie erscheinen dann erst nach der Pause oder vor
  der nächsten Taste, ohne Tastenvorschau, Vibration und Ton; ebenso der Buchstabe eines gerutschten
  Tipps direkt nach dem Tippen). Male
  den Strich zuerst, ein Tipp allein ist immer ein Buchstabe. Zwei Buchstaben direkt über und unter
  der Mitte eines gemalten Strichs werden zu ÷, ein Buchstabe direkt unter einem senkrechten Strich
  zu !.
  Rutscht der Tipp beim Loslassen ein kleines Stück auf die Nachbartaste (z. B. Löschen), zählt
  die Taste, auf der du aufgesetzt hast.
- Tippst du in dieser Pause mit zwei Daumen (der zweite setzt auf, bevor der erste
  loslässt), kommt das Symbol und danach beide Buchstaben in deiner Reihenfolge. Ein solcher Tipp
  ist nie ein Punkt. Malt der erste Finger gerade einen Strich, wenn der zweite aufsetzt, wird das
  Symbol samt diesem Strich geschrieben.
- Tippst du in dieser Pause einen Wortvorschlag an, kommt das Symbol zuerst und das Wort
  danach. Ersetzt der Vorschlag das Wort, das du vor dem Malen getippt hast, bleibt das Symbol
  dahinter.
- Die Leiste zeigt danach bis zu drei andere Symbole; ein Tipp tauscht das geschriebene aus.
- Persönliche Vorlagen: Einstellungen → „Symbole malen“ → „Aus meinen Korrekturen lernen“
  (anfangs aus). Ist es an und wählst du in der Leiste ein anderes Symbol (beim Malen oder
  danach), merkt sich die Tastatur deine Zeichnung für dieses Symbol, bis zu drei je Symbol, die
  neuesten bleiben. Wählst du danach noch einmal um, wandert die Zeichnung mit; wählst du das
  zuerst erkannte Symbol zurück, ist alles wie vorher. Eine Zeichnung mit einem Punkt merkt sie sich
  nur für ein Symbol mit Punkten (÷). Nur, wenn auch „Personalisierte Vorschläge“ an ist (Einstellungen
  → Textkorrektur); nicht im Inkognito-Modus, bei „Lernen pausieren“ oder gesperrtem Telefon. „Gelernte Wörter löschen“ (Einstellungen → Gelernte Wörter, dort steht auch
  ihre Zahl) und das Ausschalten von „Personalisierte Vorschläge“ löschen sie mit.
- „Kalibrieren“ (dieselbe Seite): Die Seite zeigt nacheinander jedes eingebaute Symbol; male es
  zweimal in das Feld, je „Weiter“. „Überspringen“ geht zum nächsten Symbol, „Fertig“ hört auf;
  was du schon gemalt hast, bleibt gespeichert. Unter „Persönliche Vorlagen“ stehen alle
  gemerkten Zeichnungen mit Anzahl; „Löschen“ entfernt ein Symbol, „Alle persönlichen Vorlagen
  löschen“ alle.
- „Nicht erkannt“: nichts wurde geschrieben; ein Tipp öffnet die eigenen Symbole (bei gesperrtem
  Telefon nicht, dann verschwindet nur der Chip).
- Ein zweiter Finger während eines Strichs (nicht eines Tipps) verwirft die Zeichnung; ihre Spur
  malt dann nicht weiter. In Passwortfeldern geht Malen nicht, ebenso wenig ohne Hauptwörterbuch für die Sprache
  (wie beim Gestenschreiben). Ob beides passt, zeigt Einstellungen → Diagnose.
- Unter Einstellungen → Gestenschreiben ist der Schalter „Gestenschreiben“ ausgegraut, solange
  Symbole malen an ist („Aus, solange „Symbole malen“ an ist“); dein Wert bleibt gespeichert.

Eingebaute Symbole (Reihenfolge und Richtung der Striche sind egal; die Form jedes Strichs zählt,
eine Pfeilspitze darf ein Haken oder zwei einzelne Striche sein):

| Symbol | So malen |
| --- | --- |
| → ← ↑ ↓ | Strich in Pfeilrichtung, dann die Spitze als Haken (oder in einem Zug) |
| ↗ ↘ | schräger Strich, Spitze als rechter Winkel am Ende |
| ↔ | waagerechter Strich mit Spitzen an beiden Enden |
| ⇒ | zwei waagerechte Striche, dann eine große Spitze rechts |
| = | zwei waagerechte Striche untereinander |
| ≠ | = und ein Schrägstrich hindurch |
| ≈ | zwei Wellen untereinander |
| ~ | eine Welle |
| ≤ ≥ | < bzw. > und darunter ein Strich |
| + | senkrechter und waagerechter Strich |
| ± | + und darunter ein Strich |
| × | zwei Diagonalen (ein X) |
| ÷ | waagerechter Strich, dann ein Tupfer darüber und einer darunter (Reihenfolge egal) |
| – | ein waagerechter Strich (Gedankenstrich) |
| \| | ein senkrechter Strich |
| ∞ | liegende Acht |
| √ | kleiner Haken nach unten, dann lang nach oben und waagerecht nach rechts |
| π | Querbalken, darunter zwei Beine |
| ° | ein Kreis |
| ✓ | Häkchen |
| ♥ | Herz in einem Zug |
| ★ | fünfzackiger Stern in einem Zug |
| € | ein C, dann zwei kurze Querstriche |
| $ | ein S, dann ein senkrechter Strich |
| § | zwei S untereinander |
| & | Und-Zeichen in einem Zug |
| @ | kleiner Kreis und große Schlinge drumherum |
| # | zwei senkrechte und zwei waagerechte Striche |
| % | Kreis oben links, Schrägstrich, Kreis unten rechts; oder erst der Schrägstrich und die Kreise als zwei Tupfer |
| ‰ | wie %, unten rechts zwei Kreise (oder Schrägstrich und drei Tupfer) |
| ! ¡ | senkrechter Strich, dann ein Tupfer darunter (!) bzw. darüber (¡) |
| ? ¿ | Haken wie geschrieben, dann ein Tupfer darunter (?); ¿ auf dem Kopf |
| ( ) [ ] { } | die Klammer, wie geschrieben |

Eigene Symbole: Auf derselben Seite „Symbol hinzufügen“; darunter öffnet sich ein Malfeld. Das
Symbol dreimal hineinmalen (je „Weiter“, „Neu malen“ wischt das Feld leer, „Abbrechen“ verwirft
alles; mehr als 12 Striche oder sehr langes Malen: „Bitte neu malen“, „Weiter“ geht dann nicht), dann den Text eingeben, den es schreiben soll (bis 8 Zeichen, auch Emoji), und
„Speichern“. Höchstens 30. Löschen über „Löschen“ neben dem Symbol. Alles bleibt auf dem Telefon.

## Schieberegler

Alle Schieberegler der Einstellungen (z. B. Aussehen → Tastaturhöhe, Schriftgröße; Vibrationsdauer,
Lautstärke; Deckkraft und Helligkeit im Farbwähler; Eckenrundung, Abstand und Höhe im
Design-Editor) ticken beim Ziehen leicht. Am Standardwert, an den Enden und an runden Werten
(z. B. 50 %, 100 %, 150 %, Zehner bei Prozent und Millisekunden) rastet der Regler mit einem etwas
stärkeren Impuls ein; ziehst du weiter, löst er sich wieder. Einen Wert direkt neben einer Rast
(z. B. 49 %) erreichst du so: etwas über die Rast hinausziehen und langsam zurück, dann rastet sie
nicht sofort wieder ein. Mit TalkBack stellst du jeden Wert genau ein. Gespürt wird das nur, wenn
Einstellungen → Optionen → „Vibration bei Tastendruck“ an ist; bei „Bitte nicht stören“ nur, wenn
Vibrieren dort erlaubt ist. Die RGB-Seite rastet noch nicht.

## Design auswählen

Einstellungen → Aussehen → Karte „Designs“ (Kachel antippen) oder dort „Alle … Designs“ bzw. im
Tastaturmenü die Pille „Design“ für die ganze Design-Seite: Kachel antippen.
„Flacher Look“ setzt auch Reihen, Größen und Leiste; zurück geht es unter Tastaturprofile.
Mit dem flachen Tastenstil sehen auch die Einstellungen moderner aus.

Eigenes Design: oben unter „Meine Designs“ die Kachel „+“ antippen. Oben siehst du die Vorschau,
darunter stellst du ein: Tastenform, Tastenhintergrund, „Eigene Eckenrundung“, Abstand,
Tastenhöhe, die Farben (Kreis antippen, Farbe wählen, OK), die Schrift und unter
„Hintergrundbild“ → „Bild wählen“ ein Bild aus deinen Dateien. Jede Änderung zeigt die Vorschau
sofort; gespeichert wird erst mit „Speichern und verwenden“. „Abbrechen“ oder Zurück verwirft
nach Rückfrage. Später: Kachel antippen wählt das Design, langes Drücken oder der Stift oben
links auf der Kachel öffnet den Editor, dort unten „Design löschen“ (mit Rückfrage). Ein
Design mit Bild ersetzt dein bisheriges Hintergrundbild der Tastatur, solange es gewählt ist;
wählst du ein anderes Design oder eine eingebaute Kachel, kommt dein altes Bild zurück. Setzt du
währenddessen unter „Aussehen“ ein neues eigenes Bild, bleibt dieses neue Bild. Größe und
Abstand gelten für das Hochformat. Ein Design schaltet „Automatischer Tag-/Nachtmodus“ aus und
stellt Abstand und Tastenhöhe um; wählst du danach eine eingebaute Kachel, bleiben diese drei
Einstellungen so, bis du sie selbst änderst.

## RGB-Beleuchtung

Einstellungen → Aussehen → Karte „RGB-Licht“ antippen (oder unten auf der Design-Seite). Oben läuft eine
große Vorschau; tippe darauf, um die Effekte beim Tippen auszuprobieren. Darunter liegen die
Einstellungen in Karten:

- „RGB-Beleuchtung an“ schaltet alles ein (anfangs aus).
- Muster: Tippe auf ein Bild. Welle waagerecht, senkrecht oder diagonal, Kreis von der Mitte,
  Atmen (alle Tasten gleich, heller und dunkler), statischer Regenbogen (steht still) und die
  bewegten Texturen Tinte, Rauten, Lamellen, Raster, LED-Punkte und Höhenlinien.
- „Gleiches Muster überall“ (an): Alles leuchtet im selben Muster. Schaltest du es aus, erscheint
  eine Auswahl Tasten, Hintergrund, Beschriftung, Vorschläge, Leistenknöpfe. Was du darunter
  einstellst (Muster, Verlauf, Tempo, Farben), gilt nur für das gewählte Element; jedes startet
  mit dem bisherigen gemeinsamen Muster. Ob ein Element überhaupt leuchtet, stellst du weiter
  unter „Was leuchtet“ und „Wo“ ein; die Seite weist darauf hin, wenn es gerade aus ist.
  Beschriftung und Leiste nehmen nur die Farbe an ihrer Stelle; Linien, Punkte und Raster sieht
  man auf Tasten und Hintergrund.
- Verlauf: eine Kurve. Waagerecht ist die Stelle im Durchlauf (Zeit und Ort zusammen),
  senkrecht die Farbe im Farbverlauf (bei „Atmen“ die Helligkeit). Ziehe einen Punkt (Kreis mit
  Kreuz), um ihn zu verschieben; die beiden Enden bleiben links und rechts und gehen nur hoch und
  runter. Tippe auf eine freie Stelle für einen neuen Punkt oder auf „Punkt hinzufügen“ (kommt in
  die größte Lücke). Tippen oder langes Drücken auf einen Punkt wählt ihn (langes Drücken löscht
  nichts). Entfernen: Punkt wählen und „Punkt entfernen“ tippen, oder ihn aus dem Feld ziehen.
  Mit TalkBack: Punkt wählen, dann über die Aktionen des Kurvenfelds „Punkt nach oben/unten/links/
  rechts“ (in Zwanzigstel-Schritten) oder „Punkt entfernen“. Mindestens zwei, höchstens 24 Punkte. Vorlagen:
  Sinus (weich), Dreieck (Zickzack), Sägezahn, Stufen (mit Regler 2–12), An- und Abstieg; wer
  vorher einen Verlauf gewählt hatte, findet ihn als passende Kurve wieder.
- Tempo und Richtung: Dauer eines Durchlaufs 0,5–30 s (bei den Texturen auch das Tempo der
  Bewegung), „Richtung umkehren“, Wellenbreite (wie viele Farbdurchläufe über die Tastatur passen).
- Farben: „Regenbogen“ oder „Eigene Farben“ (Kreis antippen, Farbe wählen; 2 bis 6 Farben über
  „Farbe hinzufügen“ und „Letzte Farbe entfernen“), Sättigung, Helligkeit und Deckkraft
  gegenüber den Farben deines Designs (0 % = nur Design, 100 % = nur Licht). Für die Tinte wie in
  deiner Vorlage z. B. Türkis, Grün, Gelb und Rot als eigene Farben.
- Was leuchtet (mehrere wählbar): Tastenhintergrund, Tastaturhintergrund (hinter und zwischen
  den Tasten), Beschriftung. Beschriftungen bleiben immer lesbar: Ist der Kontrast zum
  Hintergrund kleiner als 3:1, werden sie heller oder dunkler. „Jede Taste einfarbig“: eine Farbe
  pro Taste wie bei LED-Tastaturen; aus (Vorgabe) laufen die Farben weich über die Tasten.
  Nach dem Update auf die überarbeitete Beleuchtung laufen die Farben auch bei einer schon
  gespeicherten Einstellung weich über die Tasten (vorher war jede Taste einfarbig). Wer das alte
  Bild zurückwill, schaltet „Jede Taste einfarbig“ ein.
- Wo (mehrere wählbar): Buchstabentasten, Funktionstasten (Umschalt, Löschen, Enter, ?123,
  Komma, Punkt), Leertaste, Zahlenreihe, Vorschlagsleiste (Text der Wortvorschläge),
  Leistenknöpfe. In der Leiste ändert sich nur die Farbe von Text und Symbolen.
- Beim Tippen: keine Reaktion, getippte Taste blitzt auf und verblasst, ein Ring breitet sich von
  der Taste aus, oder Tippen beschleunigt kurz. „Nur beim Tippen sichtbar“: sonst ist das Licht
  aus, nach jedem Tipp erscheint es und verblasst (mit „Taste blitzt auf“ nur an der getippten
  Taste). „Nachleuchten“ stellt ein, wie lange das dauert (0,2–5 s).
- Bildwiederholrate: zwei Farbflächen, „30 HERTZ“ (türkis, 30 Bilder pro Sekunde, spart Akku)
  und „60 HERTZ“ (orange, flüssiger). Tippe auf eine; die gewählte leuchtet voll und hat einen
  Rahmen. Der leuchtende Tropfen zeigt das Tempo, bei 60 Hz wandert er doppelt so schnell. Er
  steht still, wenn die Android-Animationen aus sind oder der Energiesparmodus an ist. Darunter
  „Im Energiesparmodus anhalten“ (an).

Jede Änderung wird sofort gespeichert (beim Ziehen eines Kurvenpunkts beim Loslassen) und gilt
beim nächsten Öffnen der Tastatur. Die Beleuchtung läuft nur, solange die Tastatur sichtbar ist;
die untere Reihe der Emoji- und der Zwischenablage-Ansicht leuchtet nicht. Schaltest du den
Energiesparmodus bei offener Tastatur ein oder aus, bleibt das Licht sofort stehen bzw. läuft
weiter. Hast du in den Android-Einstellungen (Entwickleroptionen oder Bedienungshilfen) die
Animationen ausgeschaltet, bleibt das Bild stehen (läuft es gerade, nach höchstens 2 s, sonst
beim nächsten Einblenden der Tastatur) und die Tipp-Effekte entfallen.
Bei Rauten, Lamellen, Raster, LED-Punkten und Höhenlinien können die feinen Linien und Punkte
beim ersten Öffnen und nach einem Musterwechsel einen Augenblick später erscheinen als die Farben:
Sie werden im Hintergrund vorbereitet, damit die Tastatur dabei nicht ruckelt. Ebenso werden
die Farben im Hintergrund gerechnet: Beim Öffnen der Tastatur erscheint das Licht ein Bild später.
Beim Ziehen eines Kurvenpunkts folgt nur die große Vorschau, gespeichert wird beim Loslassen. Die Bilder der
Mustergalerie zeigen Muster und Farben, nicht deine Kurve oder dein Tempo.
Leistenknöpfe: Nur eingeschaltete Werkzeuge leuchten, ausgeschaltete behalten ihre graue Farbe;
der runde Knopf im flachen Look leuchtet nicht. Mit eigenem Hintergrundbild prüft die Tastatur
die Lesbarkeit gegen die Hintergrundfarbe, nicht gegen das Bild. Die Einstellung steht im
Designprofil (Tastaturprofile → Design).

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
  Wortvorschläge. Welche Tasten dort liegen, änderst du unter Einstellungen → Leiste & Befehle → „Knöpfe wählen und sortieren“ oder
  über den Stift-Knopf im Menü.
- Menü: Raster antippen. Design öffnet die Einstellungen zum Aussehen, die anderen Pillen
  schalten Rückgängig, Einhandmodus, Emoji, Inkognito und die frei verschiebbare Tastatur.
  Das X schließt das Menü.
- Die Zwischenablage-Taste fügt den aktuellen Inhalt der Zwischenablage an der Schreibmarke
  ein, solange der Zwischenablageverlauf aus ist (so ist er anfangs). Erst mit eingeschaltetem
  Verlauf (Einstellungen → Leiste & Befehle → „Zwischenablage-Verlauf“) öffnet sie die
  Zwischenablage-Ansicht. In der Ansicht: Karte antippen fügt ein, lange drücken heftet an, zur Seite wischen entfernt. Oben
  links geht es zurück zu den Tasten, oben rechts leerst du den Verlauf.
- Tresor (nur mit eingeschaltetem Verlauf): Kopierst du ein Geheimnis (Passwort aus dem
  Passwortmanager, API-Schlüssel wie `sk-…`, Token), landet es nicht im Verlauf, sondern oben in
  der Zwischenablage-Ansicht unter „Tresor“, verdeckt (z. B. „sk-proj-…7f3a“). Antippen fügt es
  einmal ein; danach ist es weg, und die Tastatur leert die System-Zwischenablage, wenn dort noch
  genau dieses Geheimnis liegt. Nach 2 Minuten verschwindet es von selbst. Darunter
  „Einmalcodes“: ein kopierter Code oder eine kopierte SMS mit Code; Antippen fügt nur die Ziffern
  ein, nach 10 Minuten weg. Die kopierte SMS selbst bleibt als Text im Verlauf, ein Code allein
  nicht. Was du in einem Passwortfeld kopierst, schlägt die Tastatur nirgends vor. Eingefügte
  Geheimnisse und Codes lernt die Tastatur nicht. IBAN, Telefonnummer, E-Mail und Adresse stehen normal im Verlauf, mit
  einer kleinen Beschriftung darüber. Kein Tresor in Passwortfeldern, im Inkognito-Modus und bei
  gesperrtem Gerät. Geheimnisse erscheinen nie als Pille in der Leiste. Am Ende der Tresor-Reihen öffnet
  „Meine Daten“ die Seite „Meine Daten & Vorschläge“.
- Die Pille mit kopiertem Text oder Bild in der Leiste erscheint nur, wenn
  „Zwischenablagen-Inhalt vorschlagen“ (Einstellungen → Textkorrektur) an ist. Antippen fügt ein, lange
  drücken blendet sie aus. Hinter der Pille und auf den Karten stehen bei erkanntem Inhalt die Chips
  von Smart Paste (nächster Abschnitt).
- Das Aktionsrad erreichst du im flachen Look über eine Leertasten-Geste, z. B. Halten
  (Einstellungen → Gesten & Gefühl → Leertaste), die Stift-Taste öffnet das Panel.

## Vorschläge fürs Feld und Meine Daten

Die Leiste schlägt vor, was zum Feld passt, immer als Chip. Es wird nie etwas von selbst
eingefügt: erst dein Tipp auf den Chip fügt ein, genau einmal.

- E-Mail-Feld: deine Adresse aus „Meine Daten“ und eine gerade kopierte E-Mail-Adresse. Hast du
  schon angefangen („Ma“), steht nur, was dazu passt.
- Code-Feld (z. B. Hinweis „Bestätigungscode“, „Code“, „OTP“): der frisch kopierte Einmalcode aus
  dem Tresor, nur die Ziffern. Ein Zahlenfeld ohne solchen Hinweis bekommt ihn nur, wenn er höchstens
  3 Minuten alt ist.
- Telefon-, IBAN-, Adress- und Namensfelder: dein Eintrag aus „Meine Daten“ oder ein passender
  frisch kopierter Clip.
- In normalen Textfeldern nach Hinweiswörtern, z. B. „guck mal, hier dieses Video “: Hast du gerade
  einen Link kopiert, erscheint nach dem Leerzeichen „🔗 youtube.com/…“, ein Tipp hängt den Link an.
  Ebenso „meine Nummer ist “, „IBAN:“, „Adresse:“, „Code:“, „meine Mail ist “ (auch Englisch und
  Spanisch). Die Tastatur wägt Hinweiswort, wie frisch der Clip ist, App und Feld ab; ohne
  Hinweiswort erscheint nichts.
- Nie in Passwortfeldern, im Inkognito-Modus, in Feldern, die Lernen verbieten, und bei gesperrtem
  Gerät; nie Geheimnisse aus dem Tresor. Eingefügte Werte lernt die Tastatur nicht, und was du auf
  der Seite „Meine Daten“ eintippst, auch nicht.
- Einstellungen → Leiste & Befehle → „Meine Daten & Vorschläge“ (auch über den Chip „Meine Daten“
  im Tresor der Zwischenablage-Ansicht): Schalter „Feld- und Kontext-Vorschläge“ und die Karte
  „Meine Daten“ mit Name, je zwei E-Mails, Telefonnummern, Adressen und IBANs. Freiwillig, anfangs
  leer, „Speichern“ legt sie verschlüsselt nur auf diesem Gerät ab (nicht in Sicherungen), „Alles
  löschen“ entfernt sie. Bei gesperrtem Gerät zeigt die Karte nur einen Hinweis. Keine Passwörter
  eintragen.
- Ist das Lernen an („Personalisierte Vorschläge“ unter Tippen & Korrektur → „Alle
  Korrektur-Einstellungen“, nicht pausiert), merkt sich die Tastatur, welche Vorschläge du antippst oder übergehst, nur als kleine
  Zahlen ohne Text; „Gelernte Wörter löschen“ entfernt sie.

## Smart Paste: Aktionen für Kopiertes

Kopierst du eine Adresse, Nummer, IBAN, Sendungsnummer, ein Datum, eine E-Mail-Adresse oder einen
Link, bietet die Tastatur passende Chips an. Nichts passiert von selbst: Jeder Chip wirkt erst auf
deinen Tipp und genau einmal. Die Pille selbst und ein Tipp auf eine Karte fügen weiter den Clip so
ein, wie er ist.

- Wo: in der Leiste hinter der Zwischenablage-Pille (nur wenn „Zwischenablagen-Inhalt vorschlagen“
  unter Einstellungen → Textkorrektur an ist, in den ersten 3 Minuten nach dem Kopieren) und als
  kleine Chips unten auf den Karten der Zwischenablage-Ansicht (höchstens zwei je Karte).
- Adresse: „📍 In Karten öffnen“ öffnet deine Karten-App mit der Suche. „💾 Als Ort speichern“ legt
  sie als Ort für `@ort` an, benannt nach Straße und Hausnummer (z. B. `@ort musterstraße 12`);
  umbenennen unter Einstellungen → Leiste & Befehle → „Alle Befehls-Einstellungen“ → Orte. Dieselbe
  Adresse wird nur einmal gespeichert, höchstens 10 Orte.
- Telefonnummer: „📞 Anrufen“ öffnet die Telefon-App mit der Nummer; angerufen wird erst, wenn du
  dort auf Anrufen tippst. „👤 Kontakt anlegen“ öffnet einen neuen Kontakt in der Kontakte-App.
- IBAN: „🏦 Formatiert einfügen“ fügt sie in Viererblöcken ein, nur wenn die Prüfziffer stimmt.
  Stimmt sie nicht, steht dort „⚠️ IBAN-Prüfziffer falsch“ und nichts wird eingefügt.
- Sendungsnummer: „📦 DHL: Sendung verfolgen“ (ebenso Deutsche Post, Hermes, DPD, GLS, UPS) öffnet
  die Sendungsverfolgung im Browser. Die Seiten sind fest in der App, aus dem Kopierten kommt nur
  die geprüfte Nummer. Eindeutige Nummern erkennt sie allein (UPS „1Z…“, Deutsche Post „…DE“, DHL
  „JJD…“ oder „00340434…“, Hermes „H…“); eine reine Ziffernfolge nur, wenn der Paketdienst im Text
  steht, z. B. „DPD Paketnummer 01234567890123“.
- Datum oder Uhrzeit: „📅 Termin: So 15.11. 18:00“ öffnet den Entwurf in deiner Kalender-App wie
  `@termin`; gespeichert wird erst dort. Erkannt werden z. B. „am 15.03.2027 um 14:30“, „morgen um
  18 Uhr“, „15. Dezember“, „tomorrow at 6pm“, „mañana a las 9“. Versionsnummern („2.1“), Preise,
  „bis morgen“, „Guten Morgen“ und vergangene Daten bekommen keinen Chip.
- E-Mail-Adresse: „✉️ Mail schreiben“ öffnet deine Mail-App mit der Adresse als Empfänger.
- Link mit Tracking-Anhängseln (`utm_…`, `fbclid`, `gclid`, `mc_eid`, `igshid`, bei YouTube und
  Spotify `si` …): „🧹 Ohne Tracking einfügen“ fügt den Link ohne sie ein, alles andere bleibt genau
  so. Auch für Text mit Links darin.
- Kopierte Nachricht mit Einmalcode: „🔢 Code 123456 einfügen“ fügt nur die Ziffern ein. Codes
  kommen nur aus der Zwischenablage, nie aus Benachrichtigungen.
- Zeigt die Leiste schon einen Feld-Vorschlag (z. B. den Code im Code-Feld), kommen Pille und Smart
  Paste nicht dazu.
- Nie für Geheimnisse und als vertraulich markierte Clips, nie in Passwortfeldern, im
  Inkognito-Modus, in Feldern ohne Lernen, in Zahlen- und Telefonfeldern oder bei gesperrtem Gerät.
  Eingefügtes lernt die Tastatur nicht. Alles lokal, ohne Netz, ohne neue Berechtigung.
- Ausschalten: Einstellungen → Leiste & Befehle → Zwischenablage & Bilder → „Smart Paste“.

## Emoji-Vorschläge

Für Deutsch, Englisch und Spanisch ist das Emoji-Wörterbuch eingebaut; du musst nichts
herunterladen. „Emojis vorschlagen“, „Inline-Emoji-Suche“ (Emoji nach `:`, z. B. `:herz`) und die
Emoji-Beschreibungen sind in HeliBoard standardmäßig an und wirken damit sofort. Abschalten unter
Einstellungen → Textkorrektur. Für andere Sprachen gilt weiter der Hinweis mit dem Download-Link.

Die Emojis in der Leiste sind tolerant: „Katzen“, „KATZE“, „Katz“ und „Kaze“ zeigen 🐈, „Kaese“
zeigt 🧀, „party“ beim deutschen Tippen 🎉, „geil“ 🔥. Wortanfänge und Tippfehler zählen erst ab
4 Buchstaben und nur, wenn das Getippte kein richtiges Wort ist („habe“ zeigt keinen Hasen).
Ein Wort, das ein Wörterbuch kennt, bekommt nur Emojis aus dessen Sprache („Tag“ zeigt beim
deutschen Tippen kein Preisschild, „love“ mit englischem Wörterbuch trotzdem ❤️). Wörter wie „und“, „die“, „ich“ oder „alles“ bekommen nie ein Emoji. Es stehen höchstens zwei Emojis in der
Leiste, rechts neben den Wortvorschlägen (das zweite oft erst unter dem Pfeil für weitere Vorschläge). Ein
Emoji kommt nur durch deinen Tipp ins Feld. Aus mit „Emojis vorschlagen“; in Passwortfeldern,
bei Inkognito und bei gesperrtem Gerät ohnehin nicht.

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
  Mit diesem Schalter zählen auch Tippfehler, die du später im Feld verbesserst: nach weiteren
  Wörtern, nach Löschen zurück über Leerzeichen, über einen Vorschlag, nach Tippen ins Wort oder
  nach Wischen auf der Leertaste zum Wort (dann die Schreibmarke wieder wegsetzen oder
  weitertippen). Nur echte Vertipper eines Nicht-Worts zu einem echten Wort zählen; „das“ →
  „dass“, Markieren und Überschreiben oder Einfügen nie. Auch nicht: ein Wort fertig schreiben
  („Bahnho“ → „Bahnhof“; darum lernt sie fehlende Endbuchstaben wie „nich“ → „nicht“ nie),
  Korrekturen über das Rechtschreib-Menü der App, Änderungen der App selbst (ihre Autokorrektur
  oder Autofill), Rückgängig/Wiederholen (auch Strg+Z/Strg+Y einer Hardware-Tastatur) und alles,
  solange die App als langsam gilt. Ändert die App genau das Wort, an dem du gerade tippst, kann
  das trotzdem als deine Korrektur zählen. Ein per Gestenschreiben ersetztes Wort zählt nur, wenn
  du danach direkt am Wort eine Taste drückst (etwa das Leerzeichen). Beim ersten Mal ist es ein Kandidat, beim zweiten Mal an anderer Stelle gelernt;
  leert ein Messenger das Feld nach dem Senden, zählt die nächste Nachricht als andere Stelle.
  Einstellungen → Tippen & Korrektur → „Was ich mir gemerkt habe“ zeigt beides: „Übernehmen“
  lernt einen Kandidaten sofort, „Löschen“ entfernt einen Eintrag. „Gelernte Wörter löschen“
  leert die Seite mit. Machst du eine Autokorrektur später im Feld rückgängig, gilt mit
  „Rückgängig gemachte Korrekturen merken“ dasselbe wie die Löschtaste direkt danach.

Gelernte Tippfehler und später im Feld gelernte Korrekturen gelten nur in der Tastatursprache, in
der sie gelernt wurden: Was du auf Deutsch gelernt hast, ändert nichts beim Schreiben auf Spanisch
und umgekehrt. Ältere Einträge (vor dieser Version) gelten nur dort, wo das Wörterbuch der Sprache
das gemeinte Wort kennt. Gemerkte rückgängig gemachte Korrekturen gelten in allen Sprachen.

Wörter aus anderen Sprachen (mit „Personalisierte Vorschläge“ an, unter „Alle
Korrektur-Einstellungen“): Schreibst du ein Wort einer deiner anderen aktiven Sprachen, etwa „hola“
in einer deutschen Nachricht, und die Autokorrektur macht daraus etwas anderes, drück einmal die
Löschtaste (oder tippe das getippte Wort in der Leiste an). Ab dann bleibt „hola“ auf Deutsch so
stehen und wird beim Tippen von „ho…“ vorgeschlagen, hinter der Korrektur, nie als Korrektur. Kam
gar keine Korrektur, merkt sich die Tastatur das Wort beim zweiten Mal an anderer Stelle (anderes
Feld oder andere Stelle im Text). Das gilt nur für die Sprache, in der du es behalten hast; Wörter,
die es in dieser Sprache in anderer Groß-/Kleinschreibung gibt („hand“/„Hand“), zählen nie. Nichts
davon in Inkognito, Passwortfeldern, „Meine Daten“, bei Lernpause oder gesperrtem Gerät.
„Was ich mir gemerkt habe“ → „Wörter aus anderen Sprachen“ zeigt die Wörter mit „Löschen“;
„Gelernte Wörter löschen“ und das Ausschalten von „Personalisierte Vorschläge“ löschen alle.

Getipptes Wort zurückholen: Drückst du direkt nach einer Korrektur die Löschtaste oder tippst du
später in das korrigierte Wort, steht dein getipptes Wort in der Leiste ganz links (in deiner
Groß-/Kleinschreibung), daneben ähnliche Wörter. Ein Tipp darauf setzt es ein; mit „Rückgängig
gemachte Korrekturen merken“ gilt das wie die Löschtaste. Ein richtig geschriebenes Wort ändert die
Autokorrektur nie nur in der Großschreibung („essen“ bleibt, „Essen“ steht als Vorschlag da).

## Trefferzonen

Einstellungen → Gelernte Wörter → Abschnitt „Trefferzonen“ (Schalter und Kalibrieren auch unter
Gesten & Gefühl → „Treffen & Wischen“):

1. „Trefferzonen an mich anpassen“ einschalten.
2. „Kalibrieren“ antippen und den angezeigten Text so abtippen, wie du sonst tippst. Tippfehler
   sind egal, die Tastatur gleicht sie ab.
3. „Auswerten“: Du siehst pro Taste, wohin du daneben triffst. „Übernehmen“ speichert es.

Danach lernt die Tastatur beim normalen Tippen weiter: aus getippten (nicht gewischten) Wörtern,
auch wenn du einen Vertipper über die Vorschlagsleiste korrigierst. Gespeichert wird alle zehn
Wörter; wird die Tastatur vorher beendet, gehen bis zu neun Wörter verloren. „Trefferzonen zurücksetzen“ löscht alles. Umlaute werden nur vermessen,
wenn sie eigene Tasten haben (Layout QWERTZ+); ß hat keine eigene Taste.

„Auf vorherige Kalibrierung zurücksetzen“ erscheint unter den Knöpfen, sobald es einen Schritt
zurück gibt (nach einer Kalibrierung oder einer automatischen Anpassung, siehe unten), auch ohne
„Dynamisch anpassen“. Es nimmt genau den letzten Schritt zurück; danach verschwindet der Knopf,
bis du wieder kalibrierst.

### Dynamisch anpassen (Szenen)

Im selben Abschnitt, unter den Knöpfen: „Dynamisch anpassen (Szenen)“ einschalten (geht nur,
wenn „Trefferzonen an mich anpassen“ an ist; anfangs aus).

- Die Zonen folgen dann innerhalb weniger Dutzend Buchstaben, wie du gerade tippst, und vergessen
  das nach fünf Minuten Pause oder in einer anderen App. Ein anderes Feld derselben App halbiert
  die Anpassung.
- Ein Wort zählt erst, wenn du danach eine andere Taste als die Löschtaste drückst. Korrigierst du
  es sofort (Löschtaste, Rückgängig oder Ausschneiden), lernt die Tastatur nichts daraus.
  Automatische Korrekturen zählen nie. Hältst du die Löschtaste gedrückt, zählt das als eine
  Korrektur. Tippst du nur in den Text, um die Schreibmarke zu versetzen, merkt die Tastatur das
  nicht: dann entscheidet die nächste Taste über das Wort.
- Drehst du das Telefon, schaltest du auf Einhand- oder geteilte Tastatur oder auf ein anderes
  Layout, beginnt die Anpassung neu (wie nach einer Pause). Ein Wechsel zu Zahlen und Symbolen
  zählt nicht.
- Die kalibrierten Zonen lernen in diesem Modus nicht mehr bei jedem Wort. Sie wandern nur, wenn
  dieselbe Abweichung in mindestens fünf längeren Sitzungen an drei verschiedenen Tagen und zu
  zwei verschiedenen Tageszeiten auftritt, und dann nur ein kleines Stück.
- „Szenen“ listet, was sich die Tastatur gemerkt hat, z. B. „abends, ungenauer, trifft tiefer (in
  3 Sitzungen gesehen)“. „Szene löschen“ entfernt eine Szene samt ihren Sitzungen.
- „Auf vorherige Kalibrierung zurücksetzen“ nimmt den letzten Schritt zurück: die letzte
  Kalibrierung oder die letzte automatische Anpassung. Die Szenen bleiben und werden mit den Zonen
  zurückgerechnet (wie auch nach einer neuen Kalibrierung); die gesammelten Hinweise für die
  nächste automatische Anpassung beginnen neu.
- Schaltest du „Dynamisch anpassen“ oder „Trefferzonen an mich anpassen“ aus, wird die laufende
  Sitzung beim nächsten Feld verworfen: keine Szene, keine Anpassung der Zonen.
- „Trefferzonen zurücksetzen“ und „Gelernte Wörter löschen“ löschen auch die Szenen.
- Nichts davon in Passwortfeldern, im Inkognito-Modus, in Feldern, die kein Lernen erlauben, bei
  pausiertem Lernen oder bei gesperrtem Gerät.

## Gedächtnis

Einstellungen → Tippen & Korrektur → Wörter & Lernen → „Was ich mir gemerkt habe“, Abschnitt
„Gedächtnis“ unter den Korrekturen. Alles lokal, nur auf dem Gerät, nichts davon geht ins Internet.

- „Merken, wie ich schreibe“ (anfangs aus) schaltet das Lernen ein; gelernt wird nur, wenn auch
  „Personalisierte Vorschläge“ an ist (Alle Korrektur-Einstellungen). Danach merkt sich die
  Tastatur Wortfolgen aus deinen eigenen, getippten Sätzen. Hast du eine Folge oft genug
  geschrieben, erscheint nach dem Leerzeichen hinter den ersten zwei, drei Wörtern ein Chip mit
  dem ganzen Rest, z. B. nach „Ich melde mich “ der Chip „… sobald ich mehr weiß“. Erst dein Tipp
  hängt den Rest hinter der Schreibmarke an; nichts wird ersetzt, dein Satz bleibt, wie er ist.
  Ist sich die Tastatur nur beim nächsten Wort sicher, steht nur dieses Wort vorn in der Leiste.
  Eine Folge braucht einige Wiederholungen (etwa zehn für zwei Wörter Rest), bevor sie als Chip
  kommt; selten benutzte verblassen nach Wochen.
- Eigene Wörter: Schickst du ein Wort, das die Tastatur nicht kennt (Umgangssprache, eine
  bewusste Schreibweise), dreimal so ab, wie du es getippt hast, oder machst du seine Korrektur
  dreimal mit der Löschtaste rückgängig und lässt es danach stehen (du schreibst einfach weiter),
  wird es dein Wort: Es wird nicht mehr automatisch korrigiert und beim Tippen seines Anfangs
  angeboten. Dieselbe Rücknahme an derselben Stelle zählt nur einmal, und nur, wenn das Wort
  genau dort stehen bleibt. Die Autokorrektur ändert
  weiter immer nur ein einzelnes Wort.
- Abkürzungen: Ersetzt du eine Kurzform zweimal selbst durch das ganze Wort (zum Beispiel „vlt“
  löschen und „vielleicht“ tippen), bietet die Leiste einmal den Chip „Kürzel vlt → vielleicht
  merken?“ an. Ein Tipp merkt sich das Kürzel im Gedächtnis; ins persönliche Wörterbuch wird
  nichts geschrieben. Danach steht nach „vlt “ der Chip „vlt → vielleicht“ in der Leiste; erst
  dein Tipp ersetzt genau das gerade getippte Wort „vlt“ (am Satzanfang „Vlt“ → „Vielleicht“;
  mitten im Satz bleibt „Telefon“ groß, „mfg“ wird zu „mit freundlichen Grüßen“, auch wenn du es
  nur am Satzanfang gelernt hast), von allein wird nie etwas erweitert, und „vlt“ wird nicht mehr automatisch korrigiert. Tippst du nicht auf das
  Angebot, kommt es nicht wieder und steht nur noch auf der Seite unter „Abkürzungen“ („Merken“
  oder „Verwerfen“; „Verwerfen“ nimmt auch ein gemerktes Kürzel wieder heraus). Ein Wort, das du
  nur weiterschreibst („Haus“ → „Hausaufgaben“), und eine Kurzform, die das Wörterbuch kennt,
  zählen nicht als Abkürzung; „Mo“ → „Montag“ oder „Tel“ → „Telefon“ schon.
- Textbausteine: „Passende Textbausteine vorschlagen“ (anfangs an, lernt nichts) zeigt nach dem
  Leerzeichen einen Chip „📋 Name: …“, wenn deine letzten Wörter klar zu einem deiner `@snip`-
  Bausteine passen (z. B. „Schick mir deine Adresse “). Ein Tipp fügt ihn an der Schreibmarke
  ein; ein Baustein mit Ausfüllfeldern schreibt `@snip Name`, die Leiste fragt dann die Felder ab.
- Auf der Seite: Wortfolgen ab drei Wörtern (die stärksten 100) mit „Anpinnen“ (verblasst nie,
  wird nie verdrängt) und „Löschen“ (löscht die Folge und jede längere, die sie enthält), eigene
  Wörter mit „Löschen“, Abkürzungs-Angebote, „Ausgeschlossene Apps“ (dort lernt und zeigt das
  Gedächtnis nichts; „App ausschließen“ wählt aus den installierten Apps) und „Alles vergessen“.
  Auch „Gelernte Wörter löschen“ (Gelernte Wörter) und das Ausschalten von „Personalisierte
  Vorschläge“ löschen das ganze Gedächtnis; die ausgeschlossenen Apps bleiben.
- Nie in Passwortfeldern, im Inkognito-Modus, in Feldern ohne Lernen, in ausgeschlossenen Apps
  oder bei gesperrtem Gerät. „Lernen pausieren“ hält das Lernen an, die Chips bleiben.
- Chips kommen nur zwischen den Wörtern, nach einem Leerzeichen; nach einem angetippten
  Vorschlag erst, wenn du das Leerzeichen tippst.

## Gelernte Wörter

Lernen aus dem Tippen ist anfangs aus. Einschalten: Einstellungen → Textkorrektur →
„Personalisierte Vorschläge“. Unter Einstellungen → Gelernte Wörter kannst du das Lernen
pausieren (das Gelernte bleibt), die gelernten Wörter je Sprache zählen, als Datei exportieren
oder löschen. Wer „Personalisierte Vorschläge“ ausschaltet, löscht dabei alles Gelernte.

## Wenn etwas nicht klappt

Einstellungen → „Diagnose“ (unter „Erweitert“) zeigt, was dein Telefon wirklich macht, ohne Texte,
Schlüssel oder Kennungen: welche Haptik die Rastpunkte der Leertaste spielen, der leichte Tick und der letzte Punkt (Knopf
„Test“ spielt den letzten Punkt),
ob Symbole malen laufen kann, warum die Sprache zuletzt gewechselt hat (z. B. „Sprach-Hinweis der
App → en-US“; nur seit dem Start der Tastatur, nichts wird gespeichert), den Stand von Emoji-Mix,
was die RGB-Beleuchtung in den letzten 5 Sekunden mit Licht je Bild gekostet hat (Tastatur mit
RGB öffnen, kurz tippen, dann die Seite öffnen) und die Versionen. „gstatic erreichbar?“ lädt nur bei diesem Tipp einmal ein Testbild von Google.
„Kopieren“ legt genau die angezeigten Werte in die Zwischenablage, zum Weitergeben; ein
Screenshot geht auch.

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
