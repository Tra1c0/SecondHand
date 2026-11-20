# SecondHand – Anleitung

Für den Owner: installieren, einrichten, benutzen. Was es gibt und wie gut es geprüft ist, steht
in [FEATURES.md](FEATURES.md). Diese Anleitung beschreibt die Debug-APK (sie heißt auf dem Telefon
„SecondHand“). Wird bei jeder Änderung der Bedienung im selben Push aktualisiert.

Die App hieß bis Build 27 „Agent Keyboard“ und heißt jetzt „SecondHand“ (Etappe 57). Beim Update von
Build 27 oder früher ändern sich nur Name und Symbol; die App-ID bleibt, darum bleiben alle
Einstellungen, Schlüssel und Gelerntes erhalten. Wo in einer Android-Liste noch der alte Name steht,
hilft ein Neustart des Telefons oder erneutes Öffnen der Liste.

Stand der Prüfung: Die Tastatur wurde bisher nur in einem Android-15-Emulator ausprobiert
(Tippen, Aktionsrad, Panel, Ersetzen, Schlüssel speichern). Auf einem echten Telefon ist noch
nichts geprüft. Rechne beim ersten Versuch mit Fehlern und behalte deine bisherige Tastatur.

## Installieren

1. Einmalig: Lege in GitHub unter deinem Repo → Settings → Secrets and variables → Actions zwei
   „Repository secrets“ an: `AGENT_KEYSTORE_BASE64` und `AGENT_KEYSTORE_PASSWORD` (Inhalte aus
   den beiden Dateien, die du bekommen hast). Bewahre beide zusätzlich im Passwortmanager auf.
2. Für jede neue Version: GitHub → Actions → „SecondHand APK“ → „Run workflow“, Branch
   wählen. Nach etwa 10–20 Minuten steht unter „Releases“ ein Eintrag `build-<Nummer>` mit der
   Datei `SecondHand-build-<Nummer>.apk` (dieselbe Datei liegt zur Sicherheit auch als
   `AgentKeyboard-build-<Nummer>.apk` dort; nimm die mit „SecondHand“). Lade sie auf dem Telefon herunter. Sie ist für
   Android 5 und neuer gebaut; KI-Schlüssel brauchen Android 6, das Diktat Android 12.
2. Die Datei auf dem Telefon antippen. Android fragt, ob die App, mit der du die Datei öffnest
   (z. B. der Dateimanager), unbekannte Apps installieren darf. Erlaube es für diesen einen Zweck.
3. Die App erscheint als „SecondHand“. Sie ersetzt keine andere Tastatur und kann neben
   HeliBoard installiert sein.
4. Neue Versionen installierst du genauso: Android zeigt „Aktualisieren“, Einstellungen,
   Schlüssel und Gelerntes bleiben. Eine APK aus einer anderen Quelle (etwa ein Test-Build aus
   einer Cloud-Sitzung) hat eine andere Signatur; Android lehnt sie dann als Update ab.

Prüfsumme: Neben der APK liegt im Release eine Datei `.apk.sha256`. Wer sichergehen will, vergleicht sie mit
`sha256sum` der kopierten Datei.

## Updates in der App

Jeder Build erscheint zusätzlich im öffentlichen Repository
[github.com/Tra1c0/SecondHandAgenticKeyboard/releases](https://github.com/Tra1c0/SecondHandAgenticKeyboard/releases)
(Einrichtung einmalig: `docs/BUILD.md` → Installierbare APK). Die App braucht dafür kein Konto
und keinen Token.

1. Einstellungen → „Updates“ (unten auf der Startseite) oder System & Daten → Überschrift der
   Karte „Updates“. Die Seite zeigt den installierten Build und das Repository. „Jetzt prüfen“
   gibt es auch direkt auf der Karte in System & Daten.
2. „Jetzt prüfen“: Darunter steht „Aktuell: Build 6“, „Build 7 verfügbar“ oder eine Meldung.
3. Ist ein neuer Build da, tippe „Build 7 herunterladen und installieren“. Darf die App noch
   keine Apps installieren, zeigt die Seite „Einstellung öffnen“: dort „SecondHand“
   erlauben und zurückkommen. Die App lädt die Datei, prüft Prüfsumme, Paketname und Signatur und
   übergibt sie an Android; du bestätigst die Installation im Systemdialog. Passt etwas nicht,
   wird die Datei gelöscht und nichts installiert.
4. „Neue Builds sofort melden“ (anfangs an): Die Tastatur prüft beim Öffnen selbst, höchstens alle
   15 Minuten (nach Fehlschlägen seltener), ob es einen neuen Build gibt. Gesendet wird nur eine
   Anfrage nach der Release-Liste an api.github.com, nie etwas, das du tippst (GitHub sieht dabei wie
   bei jedem Abruf deine IP-Adresse). Nicht bei gesperrtem
   Gerät, in Inkognito- oder Passwortfeldern, mit Datensparmodus oder ohne Netz. Findet sie einen
   neuen Build, steht in der leeren Leiste sofort „⬆ Build 7 verfügbar“ (nie statt Wortvorschlägen);
   ein Tipp darauf öffnet die Seite „Updates“, geladen und installiert wird weiter nur nach deinem
   Tipp dort. Ausschalten: Einstellungen → „Updates“ → „Neue Builds sofort melden“; dann fragt die
   Tastatur nie selbst, nur „Jetzt prüfen“ und die tägliche Suche.
5. Optional „Täglich nach Updates suchen“ (anfangs aus): Etwa einmal am Tag und nur mit Netz
   fragt die App nach einem neuen Build. Ab Android 13 fragt Android beim Einschalten, ob die App
   Benachrichtigungen zeigen darf. Ein neuer Build meldet sich einmal als „SecondHand: Build 7
   verfügbar“; ein Tipp öffnet die Seite „Updates“, geladen wird erst nach deinem Tipp dort.
   Ausschalten beendet die tägliche Suche sofort.
6. Hat eine Prüfung einen neuen Build gefunden, erscheint beim nächsten Öffnen der Einstellungen
   „Build 7 ist da“ mit seinen Notizen („Alle Notizen anzeigen“ klappt lange Notizen auf).
   „Installieren“ öffnet die Seite „Updates“ und startet dort gleich den Download (weiter wie in
   Schritt 3); „Später“ fragt für diesen Build nicht mehr, erst für einen neueren wieder.
7. In der Tastatur steht dann in der leeren Leiste „⬆ Build 7 verfügbar“ (am Feldanfang oder
   nachdem du alles gelöscht hast; beim Tippen haben die Wortvorschläge Vorrang). Ein Tipp öffnet
   die Seite „Updates“. Bei gesperrtem Gerät fehlt der Chip. Nach der Installation verschwindet er.
8. Nach dem Update zeigen die Einstellungen beim ersten Öffnen einmal „Neu in Build 7“ mit den
   Notizen, die in der APK stecken (kein Netz nötig). „OK“ schließt den Dialog für immer.

Für Freunde: Die APK einmal von der Release-Seite oben im Browser des Telefons herunterladen und
installieren (Android fragt, ob der Browser Apps installieren darf). Danach kommen Updates wie
oben in der App. Wer lieber eine Update-App nutzt, kann in Obtainium die Adresse der
Release-Seite eintragen. Updates gehen nur, wenn die APK mit demselben Schlüssel signiert ist wie
die installierte App.

## Als Tastatur einschalten

1. Android-Einstellungen → System → Tastatur → Bildschirmtastatur → Tastaturen verwalten
   (der Pfad heißt je nach Hersteller etwas anders) → „SecondHand“ einschalten.
   Android zeigt dabei für jede Tastatur dieselbe Warnung, dass sie alles Getippte lesen könnte.
2. In ein Textfeld tippen und über das Tastatur-Symbol in der Navigationsleiste zu
   „SecondHand“ wechseln.
3. Zurück zur alten Tastatur kommst du jederzeit über dasselbe Symbol.

Die Einstellungen der Tastatur öffnest du über das App-Symbol „SecondHand“ oder
in der Tastatur über den Raster-Knopf links in der Leiste und dort „Einstellungen“ (das Zahnrad).

## Einstellungen finden

Die Startseite der Einstellungen (dunkel, mit farbigen Kacheln) hat oben die Lupe für die Suche,
darunter „Dein Look“ mit „Ändern“ (öffnet Aussehen), die Schnellschalter Inkognito, RGB-Licht,
Vibration und Symbole malen (ein Tipp schaltet um) und sieben Bereiche. Ganz unten: Diagnose,
Updates (mit deiner Build-Nummer) und Über. Jeder der sieben Bereiche hat seit Etappe 38 eine eigene
Seite (die Karte „Seiten“ der Übergangszeit gibt es nicht mehr); die bisherigen Seiten sind dort
über Zeilen erreichbar. Die Pfade in dieser Anleitung nennen zuerst den Bereich und dann die Zeile oder
Karte, zum Beispiel „Einstellungen → Leiste & Befehle → Alle Befehls-Einstellungen“.
Die Suche (Lupe) findet jede Seite auch unter ihrem Titel und unter deutschen und englischen
Stichwörtern (zum Beispiel „openai“, „Beleuchtung“, „Diagnose“, „autocorrect“).

| Bereich | Seiten |
| --- | --- |
| 01 Aussehen | eigene Seite (siehe unten): Designs, RGB-Licht, Größe & Form, Schrift & Emoji, Hintergrund & Farben; „Alle Aussehen-Einstellungen“ = bisherige Seite Aussehen |
| 02 Tippen & Korrektur | eigene Seite (siehe unten); die bisherige Seite Textkorrektur = „Alle Korrektur-Einstellungen“; im Abschnitt „Wörter & Lernen“: Wörterbücher, Persönliches Wörterbuch, Gelernte Wörter, Was ich mir gemerkt habe |
| 03 Tasten & Sprachen | eigene Seite (siehe unten): Sprachen & Layouts, Sekundäre Layouts, Optionen (über „Weitere Optionen“) |
| 04 Gesten & Gefühl | eigene Seite (siehe unten): Leertaste, Wischen über die Buchstaben (mit Bibliothek: Zeile „Gestenschreiben“), Symbole malen, Vibration & Ton, Treffen & Wischen |
| 05 Leiste & Befehle | eigene Seite (siehe unten); Leiste = „Knöpfe wählen und sortieren“, Befehle = „Alle Befehls-Einstellungen“, Screenshots, Meine Daten & Vorschläge |
| 06 KI & Automationen | eigene Seite (siehe unten): Anbieter und Schlüsselstatus, Skill-Tasten, Texte, Workflows, Diktat & Verlauf |
| 07 System & Daten | eigene Seite (siehe unten): Updates, Profile, Sichern, Für Fortgeschrittene, Über; dazu Tastaturprofile (über „+ Neues Profil“ oder „Profile exportieren“), Updates, Diagnose (Knopf auf der Karte UPDATES), Erweitert („Alle erweiterten Einstellungen“), Gestendaten-Erfassung (nur in der Sammelphase), Über („Alle Infos und Links“) |

Zurück geht es mit dem runden Pfeil oben links oder der Zurück-Geste.

### Tippen & Korrektur

Unter **Regeln → Satzbezogene Autokorrektur** kannst du die zusätzliche Prüfung deutscher
Verbformen abschalten. Die bisherige Autokorrektur ist dann weiter verfügbar.
Mit **Rücktaste macht Autokorrektur rückgängig** holst du direkt nach einer Korrektur mit
der Rücktaste dein getipptes Wort zurück. Beide Schalter sind anfangs an.

Unter **Regeln → Buchstabendreher** korrigiert die Tastatur sehr häufige kurze Wörter, in denen zwei
Nachbarbuchstaben vertauscht sind: „idr“ wird „dir“, „teh“ wird „the“. Sie greift nur bei Wörtern mit
3 bis 5 Buchstaben, die kein Wörterbuch kennt, nur wenn genau ein Tausch ein sehr häufiges Wort der
Tastatursprache ergibt, und nur, wenn die Tastatur nicht schon eine eigene Korrektur hat. Wörter, die es
in irgendeiner Schreibweise gibt („Ort“, „Rest“, „Titel“), Wörter mit einem Großbuchstaben mitten im Wort
(„idR“) und Wörter aus deinen eigenen Listen lässt sie stehen; beim Gestentippen greift sie nicht. Sie gilt
bei jeder Stärke der Autokorrektur, solange die Autokorrektur an ist. Zurücknehmen: direkt nach der
Korrektur die Rücktaste tippen (sofern „Rücktaste macht Autokorrektur rückgängig“ an ist), das getippte
Wort ist wieder da. Abschalten: Schalter „Buchstabendreher“ aus, er ist anfangs an. Im Emulator mit den
echten Wörterbüchern noch nicht bestätigt, auf einem Telefon nicht ausprobiert.

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
  „Doppeltipp = Punkt“ (Tippen & Korrektur → Leerzeichen & Satzzeichen) an ist.
- „Über die Buchstaben wischen“: **Nichts** (Symbole malen und Gestenschreiben aus),
  **Symbole malen** oder **Gestenschreiben** (nur mit geladener Gesten-Bibliothek; sonst
  ausgegraut mit Hinweis auf System & Daten → Für Fortgeschrittene → „Gesten-Bibliothek laden“). Mit Bibliothek führt die Zeile
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

**Symbolseiten („?123“ und „=\<“)** sehen in jedem Stil aus wie bei Gboard: Seite 1 mit den Ziffern oben, dann
`@ # € _ & - + ( ) /` und `* " ' : ; ! ?`; Seite 2 mit `~ ` | • √ π ÷ × § ∆`, `£ ¥ $ ¢ ^ ° = { } \` und
`% © ® ™ ✓ [ ]`. Unten steht auf beiden Seiten die Taste „12/34“ (Ziffernblock); auf Seite 2 sind `<` und `>`
die Tasten neben der Leertaste. Die Ziffern stehen auf Seite 1 immer, auch wenn „Zahlenreihe“ aus ist; der Schalter
„Zahlenreihe in Symbolen“ betrifft nur noch eigene Symbol-Layouts (so steht es auch in seiner Zeile). Das Steuerkreuz (vier Pfeile neben der Leertaste)
ist aus, solange du es nicht unter Tasten & Sprachen → „Steuerkreuz-Taste“ einschaltest; eine Einstellung, die du dort
schon einmal gesetzt hast, bleibt. Die Pfeile erreichst du sonst über eine Leertasten-Geste mit der Aktion „Steuerkreuz“ oder über die Werkzeug-Taste
„Steuerkreuz“ der Leiste (falls dort eingestellt). Langes Drücken zeigt meist Varianten desselben Zeichens (`<` zeigt `‹ ≤ «`, `%` zeigt `‰ ℅`, `-` die
Striche), bei ein paar Tasten verwandte Zeichen (`•` zeigt `♪ ♥ ♠ ♦ ♣`, `^` die Pfeile, `*` zeigt `† ‡ ★`, `°` zeigt `′ ″`). Bei eingeschalteter Zahlenreihe hat die Buchstabenseite fünf Reihen und die Symbolseiten vier höhere Reihen, die Gesamthöhe bleibt gleich. Eigene Symbol-Layouts (Tasten & Sprachen → Layouts → Symbole) und das mitgelieferte arabische bleiben unverändert.

### Leiste & Befehle

„Leiste“ heißt hier, was HeliBoard „Symbolleiste“ nannte: die Zeile über den Tasten. Oben wählst
du in einem Tipp, was sie zeigt:

- **TASTEN + VORSCHLÄGE**: die feste Leiste (siehe „Die Leiste wie Gboard“ unten): Werkzeuge an festen
  Plätzen, beim Tippen die Vorschläge (HeliBoard: „Leisten-Tasten und Vorschläge“).
- **NUR VORSCHLÄGE**: nur Vorschläge und angeheftete Knöpfe.
- **VERSTECKT**: keine Leiste, mehr Platz für Tasten.

Steht „Eigene Einstellung“ darunter, ist der vierte Modus „Nur Leisten-Tasten“ gewählt (unter
„Knöpfe wählen und sortieren“ → „Leisten-Modus“). Die Karte „@-Befehle“ zeigt das Präfix und alle
Befehle; ein Tipp auf einen Befehl schaltet ihn einzeln an oder aus, der Schalter oben rechts
schaltet das Erkennen getippter Befehle ganz aus. `@contact` fragt beim Einschalten nach dem
Zugriff auf Kontakte. Darunter: „Knöpfe wählen und sortieren“ (die bisherige Leisten-Seite mit
Modus, Tasten, angehefteten Tasten, Zwischenablage-Tasten; bei TASTEN + VORSCHLÄGE steht dort statt
Tasten und angehefteten Tasten „Werkzeuge in der Leiste“, die Liste der festen Leiste), „Automatisch zeigen“ (nur bei TASTEN +
VORSCHLÄGE), „Wischen blendet aus“, „Skill-Tasten anheften“ (zeigt, wie viele der drei angeheftet
sind) sowie Zwischenablage-Verlauf, Aufbewahrung (Regler), Bilder und Dateien, Screenshots
anbieten (fragt nach dem Zugriff auf Bilder), Emoji-Mix in der Leiste und Smart Paste (siehe
„Smart Paste: Aktionen für Kopiertes“). Präfix, Textbausteine,
Orte, Bildschirm lesen und den Emoji-Mix-Speicher findest du unter „Alle Befehls-Einstellungen“,
angeheftete Einträge zuerst und Dateigröße unter „Weitere Zwischenablage-Einstellungen“. „Meine
Daten & Vorschläge“ schaltet die Feld- und Kontext-Vorschläge und enthält deine eigenen Daten (siehe
„Vorschläge fürs Feld und Meine Daten“). Der Zwischenablage-Verlauf nimmt nichts auf, solange das
Gerät gesperrt ist (auch vor dem ersten Entsperren) oder „Immer Inkognito“ an ist, und auch kein
Bild oder keine Datei, die das System als vertraulich markiert. Solange die Tastatur gerade in einem
Passwortfeld, einem Feld ohne Lernen (private Chats) oder im Inkognito-Modus aktiv ist, nimmt er auch
nichts auf. Beispiel: Du kopierst in einem privaten Chat einen Text, er kommt nicht in den Verlauf.
Dann blendest du die Tastatur aus, öffnest den Browser und kopierst einen Link: Der kommt in den
Verlauf. Was nicht aufgenommen wurde, kommt später nicht nachträglich hinein. Ein Clip, der schon in der Zwischenablage lag, als die Tastatur startete (nach einem Neustart oder einem Wechsel der Tastatur), kommt nicht in den Verlauf: Die Tastatur weiß nicht, wo er kopiert wurde.

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
- **Sichern**: „Sichern und Wiederherstellen“ (Tastatureinstellungen und Wörter als Datei, derselbe
  Dialog wie bisher), „Profile exportieren“ (öffnet Tastaturprofile). Der Dialog sagt genau, was die
  ZIP-Datei enthält: Tastatureinstellungen (auch Designs und RGB-Licht, aber ohne die Bilder eigener
  Designs), eigene Layouts, von dir hinzugefügte Wörterbuchdateien, Listen gesperrter Wörter, gelernte Wörter (nur solange „Persönliches
  Wörterbuch des Gerätes verwenden“ aus ist, sonst liegen sie beim System), gesammelte Gestendaten (falls eingeschaltet), Hintergrundbild,
  Schriften, den Zwischenablage-Verlauf und deine Einträge (nur bei entsperrtem Telefon). Nicht enthalten sind Profile, Gedächtnis, Bausteine, Orte,
  gemerkte Korrekturen, Trefferzonen, gemalte Symbole, KI-Einstellungen und -Modelle, Zugangsschlüssel, das
  Persönliche Wörterbuch (es bleibt beim System), mitgelieferte Wörterbücher (werden bei Bedarf neu angelegt)
  und die Bilder eigener Designs. Beim Wiederherstellen wird alles im Datenordner entfernt, was nicht in der
  Datei steht, zum Beispiel Bilder eigener Designs und die geladene Gesten-Bibliothek (Gesten-Tippen danach
  neu laden). Die nicht gesicherten Daten bleiben unverändert auf diesem Telefon (Profile und Workflows lassen sich einzeln als Datei
  exportieren). Die Datei ist nicht verschlüsselt: Gelernte
  Wörter, gesammelte Gestendaten, Zwischenablage-Einträge und Ablage-Einträge sind darin lesbar, bewahre sie sicher auf.
  Wiederherstellen überschreibt vorhandene Daten, nur die Einträge nicht, wenn die Datei keine (oder eine leere
  Liste) enthält: Dann bleiben die Einträge auf dem Telefon. Zu den Einträgen siehe „Ablage“ unten.
- **Sichern**: „Sichern und Wiederherstellen“ (alle Einstellungen als Datei, derselbe Dialog wie
  bisher), „Profile exportieren“ (öffnet Tastaturprofile). Scheitert das Wiederherstellen,
  holt die App alles zurück, wie es war. Die Meldung sagt „Es wurde nichts geändert“, wenn die
  Einstellungen in der Datei beschädigt sind, wenn sie sich nicht speichern ließen oder wenn das
  Wiederherstellen gar nicht erst starten konnte. Bei jedem anderen Fehler (z. B. eine Datei ohne
  passenden Inhalt) zeigt sie den Text des Systems; die App hat auch dann alles zurückgeholt. Nur
  wenn sogar das Zurückholen scheitert, sagt die Meldung, dass deine alten Daten in einem Ordner neben
  dem Datenordner liegen (Name endet auf 2): nicht löschen, das Wiederherstellen noch einmal starten.
  Stirbt die App mitten in einem Wiederherstellen, holt erst der nächste Versuch den alten Stand zurück.
- **Für Fortgeschrittene**: Emoji-Version, URL-Erkennung, Hardware-Tastatur, Gesten-Bibliothek
  laden, Debug-Einstellungen (nur wenn freigeschaltet), Gestendaten (nur in der Sammelphase) und
  „Alle erweiterten Einstellungen“ für den Rest der alten Seite Erweitert (Symbole-Taste für den
  Ziffernblock, Emoji-Taste der Hardware-Tastatur, App-Symbol …).
- **Über**: SecondHand mit Version und Build (fünfmal tippen schaltet die
  Debug-Einstellungen frei), Lizenz, Versteckte Funktionen, Protokoll speichern und „Alle Infos und
  Links“ (SecondHand auf GitHub für Quellcode und Fehlermeldungen, „Basiert auf HeliBoard“ als Dank und
  Lizenz, Lizenz, Versteckte Funktionen, Protokoll speichern; die Seiten von HeliBoard für Wiki, Community
  und Fehler gibt es hier nicht mehr).

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
System & Daten → Alle erweiterten Einstellungen → „Leertaste“ und Tastaturprofile → Gesten). Für jede Geste wählst du
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
  Rückgängig-Taste. Die Ticks kommen nur, wenn unter Einstellungen → Gesten & Gefühl → Vibration & Ton „Vibration bei
  Tastendruck“ an ist, und nicht bei „Bitte nicht stören“ (außer du hast Vibration dort
  erlaubt); der Android-Schalter für Berührungs-Haptik spielt keine Rolle. Kann dein Telefon die
  Stärke nicht steuern, ist der letzte Rastpunkt ein langer statt eines kurzen Pulses.
- **Cursor als Trackpad** (Einstellungen → Gesten & Gefühl → Treffen & Wischen → „Cursor als Trackpad“, anfangs an):
  Liegt mindestens eine Wischrichtung der Leertaste (links, rechts, oben oder unten) auf „Cursor bewegen“, steuert ein Wisch, der in einer solchen Richtung beginnt, den Cursor in alle Richtungen (sind andere Richtungen mit Aktionen belegt, läuft die Aktion, wenn der Wisch in ihrer Richtung beginnt; „nichts“ zählt wie Cursor; die Richtung des Starts wird nach 10 dp entschieden: ist die Strecke nach oben/unten dann länger als die seitliche, zählt sie als oben/unten, sonst als seitlich), ohne dass du den Finger absetzt: Setze auf der Leertaste auf und ziehe nach links oder rechts (ein
  Zeichen je Daumenbreite etwa 13 dp, bei langsamer Bewegung zeichengenau), dann ohne Absetzen nach oben oder
  unten (eine Zeile je 26 dp); das Feld scrollt dabei selbst. Nach oben und unten geht es nur in mehrzeiligen Textfeldern und nur bis zur ersten und letzten Zeile: Dort hält die Bewegung an (ein weiterer Pfeil würde den Fokus in ein anderes Feld schicken), und die Tastatur schickt immer nur einen Pfeil und wartet, bis das Feld geantwortet hat. Ein zweiter Finger auf der Leer- oder Löschtaste kann nicht wischen, solange der erste die Spur hat: Er tippt oder hält nur (ein Wisch von ihm wird verworfen, auch das Wiederholen von Löschen; sein Heben beendet den Wisch des ersten Fingers, wie bei HeliBoard). in einzeiligen Feldern (und wo die Tastatur den Feldtyp nicht kennt) zählt nur das Seitwärts-Stück deines Wischs, damit „Pfeil runter“ nicht aus dem Feld springt. Je schneller du ziehst, desto mehr
  Zeichen je Weg, bis zum Vierfachen (senkrecht Doppelten); langsam bleibt es genau. Die Achse wechselt
  erst bei einer klaren Bewegung (etwa 20 dp): Ein leicht schräger Wisch nach links springt nie eine Zeile
  höher. An Wortanfang und Wortende (auch vor und nach Satzzeichen und Leerzeichen) merkst du einen leichten
  Widerstand und einen stärkeren Tick, wenn du langsam bist: der „Magnetismus“ (Regler darunter, anfangs
  35 %, 0 % schaltet ihn aus). Er hält nichts fest, er verlangt nur ein kleines Stück mehr Weg, um das Wort
  zu verlassen; wer schnell zieht, spürt ihn nicht. Der Cursor bleibt überall frei beweglich. Ehrlich im
  Vergleich zu iOS: Die Tastatur sieht das Textlayout der App nicht und kann den Cursor nicht auf einen
  Pixel setzen. Sie schickt Zeichenschritte und „Pfeil hoch/runter“ und hört bei jedem Schritt mit einem Tick
  zu; das Gleiten des Cursors, das Scrollen und der Zeilensprung sind Sache der App. Am Textrand hält die
  Bewegung an, ein Dauerlauf bei stehendem Finger fehlt. Ist eine Markierung da, gilt das alte Wischen (ein
  Zeichen je 10 dp, nur links/rechts); ebenso, wenn du eine andere Aktion auf den Wisch gelegt hast (z. B.
  Sprache wechseln) oder den Schalter ausschaltest („wie bisher“). Rechts-nach-links-Sprachen: Der Finger
  folgt der sichtbaren Richtung. Die Zahlen sind erste Schätzungen und werden am Telefon abgestimmt.
- **Löschen durch Wischen** (Schalter „Löschen durch Wischen“): Ziehe auf der Löschtaste nach links; die Auswahl
  wächst Zeichen für Zeichen und hält an Wortanfängen (gleiche Kurve und Magnetismus wie oben). Ziehst du
  zurück nach rechts, wird sie kleiner, nie über den Startpunkt hinaus. Erst beim Loslassen wird gelöscht,
  genau das Markierte. Mit „Cursor als Trackpad“ aus wie bisher (10 dp je Zeichen).
- **Halten**: Leertaste lange drücken, z. B. für das Aktionsrad oder „Tastatur auswählen“.
  Bewegst du den Finger dabei deutlich nach links oder rechts, ist das Halten sofort vorbei und
  nur der Wisch zählt; auch ein langsamer Wisch löst das Halten nie aus.
- **Doppeltippen**: Der erste Tipp schreibt wie immer ein Leerzeichen, ein zweiter Tipp direkt
  danach führt die Aktion aus statt ein zweites Leerzeichen zu schreiben. Solange Doppeltippen
  eine Aktion hat, ruht „Punkt plus Leerzeichen“.
- **Befehlsliste** tippt das Befehlspräfix (z. B. `@`) an einen Wortanfang; die Leiste zeigt dann
  alle Befehle. In Passwortfeldern, im Inkognito-Modus und bei ausgeschalteten Befehlen passiert
  nichts.
- **Alles auswählen** markiert den ganzen Text. Weil Android dabei das Menü der App nicht öffnet,
  zeigt die Leiste Kopieren, Ausschneiden, Einfügen (nur wenn Text in der Zwischenablage liegt) und
  Löschen, solange etwas markiert ist (auch bei „Wort markieren“, Umschalt und Pfeilen oder wenn du
  von Hand markierst). Ein Tipp auf einen Chip führt es aus; nach Kopieren bleibt die Markierung
  stehen. Die Chips verschwinden, wenn die Markierung weg ist oder du tippst. Nicht in
  Passwortfeldern, im Inkognito-Modus und bei gesperrtem Gerät. Seit Etappe 85 ersetzen die Chips die Werkzeuge der
  festen Leiste wie jeden Vorschlag (der Pfeil „<“ links zeigt die Werkzeuge); ist „Werkzeugleiste automatisch zeigen“ an,
  erscheint wie vorher die Werkzeugleiste und es gibt keine Chips. Ausschalten: Einstellungen → Leiste & Befehle →
  Zwischenablage & Bilder → „Aktionen bei markiertem Text“.
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
   Leiste & Befehle → Screenshots → „Neue Screenshots in der Leiste anbieten“) und den
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

Zwischenablage: Ist der Zwischenablage-Verlauf (Einstellungen → Leiste & Befehle → Zwischenablage &
Bilder) an, legt die
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
Einstellungen → Leiste & Befehle → Alle Befehls-Einstellungen schaltest du Befehle ab oder wählst statt `@` ein anderes Zeichen (`#`,
`$`, `%`, `~`, `§`), etwa wenn du in einer App oft Leute mit `@` erwähnst. Weitere Befehle
stehen unten. Noch auf keinem Gerät ausprobiert.

### Weitere Befehle

- `@contact Ann`: Kontakt antippen, dann Nummer, E-Mail oder Adresse. Beim ersten Mal erscheint
  „Kontakte erlauben“; das öffnet Einstellungen → Leiste & Befehle → Alle Befehls-Einstellungen, dort den Zugriff erlauben.
- `@url heise.de` öffnet die Seite. `@url` allein schlägt Adressen aus Zwischenablage und Feld vor.
  „Bildschirm lesen“: einmal einschalten unter Einstellungen → Leiste & Befehle → Alle Befehls-Einstellungen → „Bildschirm lesen (@url)“ →
  „Bedienungshilfen öffnen“. Auf dem Xiaomi vorher: App-Info von „SecondHand“ → ⋮ →
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
- `@snip name`: Bausteine legst du unter Einstellungen → Leiste & Befehle → Alle Befehls-Einstellungen → „Textbausteine“ an. Im Text
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
- `@clip wort`: braucht Einstellungen → Leiste & Befehle → Zwischenablage & Bilder → Zwischenablage-Verlauf. Einträge, die als
  vertraulich markiert waren oder in einem Passwortfeld kopiert wurden, zeigt er nicht. Ausnahme:
  vor diesem Update kopierte Einträge, die die Tastatur nicht mehr als aktuellen Clip sieht.
- `@wf name`: Workflow antippen; das Panel sagt „Workflow vorgemerkt“. Panel schließen, Text
  markieren, Textaktionen wieder öffnen (Stift): Der Workflow ist vorbereitet, du gibst ihn wie
  gewohnt frei. Ist schon Text markiert, wird er sofort vorbereitet. Ein Feldwechsel verwirft die
  Wahl.
- `@ki Frage`: Chip „✦ KI fragen (Anbieter): …“ antippen – das sendet genau die getippte Frage
  (eigener Schlüssel nötig). Die Frage darf bis 2000 Zeichen lang sein und mehrere Absätze haben.
  Enter in der Frage: Hat das Feld keine Aktion
  (kein Senden/Los/Fertig, oder die App unterdrückt sie bei Mehrzeilenfeldern), fügt Enter einen
  Zeilenumbruch ein wie sonst. Hat das Feld eine Aktion (z. B. Senden im Chat), führt die Tastatur sie
  bei einer laufenden Frage NICHT aus, die Frage geht also nicht als Nachricht raus, solange die
  Tastatur sie als Frage erkennt (der Chip ist zu sehen): In einem Mehrzeilenfeld fügt Enter dann den
  Zeilenumbruch ein, in einem einzeiligen Feld passiert nichts, und ein Hinweis (einmal je Frage) sagt,
  warum. Gesendet wird die Frage nur per Tipp auf den Chip. Nicht erkannt wird eine Frage, wenn Text
  markiert ist oder die Schreibmarke vor dem Befehl steht, wenn sie länger als etwa 2500 Zeichen ist
  und wenn eine lange Frage (über etwa 500 Zeichen) nach einem Wechsel in ein anderes Feld oder einem
  Neustart der Tastatur noch im Feld steht: dann tut Enter, was es im Feld sonst tut. Umgekehrt hält
  die Tastatur Enter auch fest, wenn du „@ki“ mit Text dahinter als normale Nachricht meinst; sende
  sie dann über den Knopf der App. Hinter
  einem älteren `@ki` gilt bis 2000 Zeichen später noch alles als Teil der Frage. Zwei Leerzeilen
  beenden die Frage (eine Leerzeile zwischen Absätzen nicht); danach ist es wieder normaler Text. Das gilt
  auch für den Text von `@translate`. Wird die Frage länger als 2000 Zeichen, zeigt ein grauer Chip
  „Zu lang: höchstens 2000 Zeichen nach dem Befehl“. Dauert die Antwort lange (bis 2,5 Minuten), bleibt „Abbrechen“
  erreichbar. Wurde die Antwort abgeschnitten, steht „Antwort abgeschnitten“: die Frage enger stellen (bei
  markiertem Text: weniger Text oder eine kürzere Aufgabe wählen). Drei wandernde Punkte zeigen „Frage läuft …“; „Abbrechen“ stoppt.
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
  wie bei `@ki`: „Übersetze …“, dann die Übersetzung als Chip antippen, um den Befehl zu ersetzen. Höchstens 2000 Zeichen nach
  `@translate`, der Text in Anführungszeichen darf mehrere Zeilen haben (Enter wie bei `@ki`);
  bei mehr zeigt ein grauer Chip „Zu lang: höchstens 2000 Zeichen nach dem Befehl“ (bis etwa 2500 Zeichen,
  danach ist es einfach normaler Text, und die Autokorrektur greift wieder). Ein sehr langer Text, der auf einmal
  eingefügt wird (mehr als etwa 2500 Zeichen hinter `@ki `, oder eine ganze lange Frage in ein leeres Feld),
  wird nicht als Befehl erkannt, auch ohne Hinweis. Für lange Texte (bis 32.768 Zeichen): Text markieren,
  Stift, KI-Aktion wählen.
- `@qr Text` erzeugt einen QR-Code, z. B. `@qr https://…` oder für WLAN
  `@qr WIFI:T:WPA;S:Netzname;P:Passwort;;`. „QR-Code einfügen“ (in Chats mit Bildern) oder „QR-Code teilen“.
- `@datum`, `@datum morgen`; `@termin Samstag halb acht Kino` öffnet einen Kalenderentwurf (gespeichert
  ist er erst in der Kalender-App); `@ort Bahnhofstraße 1 Köln` oder `@ort zuhause` (Orte unter
  Einstellungen → Leiste & Befehle → Alle Befehls-Einstellungen → „Gespeicherte Orte“).
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
- Nur Sprachen, die unter Einstellungen → Tasten & Sprachen → „Sprache hinzufügen“ (Sprachen & Layouts) aktiv sind. Ist nur eine aktiv,
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
  zweiter Sprache („Mehrsprachiges Tippen“): Die gewählte Sprache ist die Hauptsprache. Nach dem
  Umschalten zählen beide Sprachen gleich viel, bei Gleichstand gewinnt die Hauptsprache; ihre
  Regeln gelten für die Großschreibung, und gelernte Tippfehler gehören zu ihr. Welche Sprache
  danach vorne liegt, entscheidet wie bei HeliBoard, in welcher du zuletzt Wörter geschrieben hast;
  dorthin lernt auch der Lernverlauf. Ein Wort, das in beiden Sprachen etwa gleich häufig ist,
  kann deshalb direkt nach dem Umschalten auch aus der zweiten Sprache kommen.
- Die gewählte Sprache bleibt, solange du im selben Feld bleibst, auch wenn die App das Feld
  nach dem Löschen des Befehls neu startet. Wechselst du in ein anderes Feld, gilt dort wieder,
  was die App, „Sprache pro App merken“ oder eine mit `@lang app sprache` gesetzte Sprache
  vorgibt (wie bei der Sprachtaste).
- Hat eine App eine mit `@lang app sprache` gesetzte Sprache und ist „Sprache pro App merken“ aus,
  bleibt ein Wechsel mit der Sprachtaste (oder einem Sprachbefehl) in einem Feld dieser App
  ebenfalls in diesem Feld, auch wenn die App es nach dem Senden neu startet. Erst ein anderes Feld
  der App beginnt wieder in ihrer Sprache. Der automatische Wechsel beim Start eines Felds (eine
  Sprache, die das Feld selbst vorgibt) zählt dafür nicht. Achtung WhatsApp und ähnliche
  Messenger: Alle Chats teilen sich dort ein Eingabefeld. Ein Wechsel mit der Sprachtaste gilt
  dann für alle Chats, bis du ein anderes Feld antippst (z. B. die Suche).
- Abschalten unter Einstellungen → Leiste & Befehle → Alle Befehls-Einstellungen → Befehle → `@lang`; dort stehen auch die Kürzel deiner aktiven
  Sprachen.

**Englischer Satz auf der deutschen Tastatur.** Hat die Tastatur Englisch (oder Spanisch) als zweite Sprache, bleiben Wörter, die es in beiden Sprachen gibt („name“, „bank“, „job“, „hand“), in einem Satz, der bisher englisch war, so stehen, wie du sie tippst. Die Tastatur sieht das an den bis zu drei Wörtern davor im selben Satz. Das erste Wort eines Satzes hat noch keinen Beweis: dort gilt weiter die deutsche Korrektur („hand“ → „Hand“); tippe das Wort dann trotzdem so, tippst du auf die getippte Form in der Leiste oder nimmst die Korrektur mit der Löschtaste zurück. Ebenso fehlt der Beweis nach einem Komma (die Wörter davor zählen nicht) und nach einem Wort mit angehängten Anführungszeichen, Klammern oder einem Emoji; ein einziges nur deutsches Wort unter den letzten drei beendet den Schutz; es zählen nur die zweiten Sprachen der Tastatursprache, nicht andere aktivierte Tastatursprachen. Die Regel gilt in beide Richtungen: Eine englische Tastatur mit Deutsch als zweiter Sprache lässt deutsche Wörter in einem deutschen Satz stehen.

**Slang bleibt, wie du ihn tippst.** Die Tastatur bringt eine Liste mit etwa 1.700 Wörtern der Umgangs- und Chat-Sprache mit (Abkürzungen, eingedeutschte Wörter wie „gecovert“ oder „gefixt“, Spiele-, Technik- und Musikbegriffe wie „lowkey“ oder „metalcore“), die ihre Wörterbücher nicht kennen. Tippst du so ein Wort auf einer deutschen oder englischen Tastatur, ersetzt die Autokorrektur es nicht durch ein anderes; ihr Vorschlag steht weiter in der Leiste. Ebenso bleiben gedehnte Wörter („neee“, „jaaaa“, „heyyyy“) und Lacher („hahaha“) stehen. Das ist immer an, ohne Schalter, und es wird nichts dazu gelernt. Nicht davon betroffen: die Großschreibung (ein kleines „jungs“ wird weiter „Jungs“), Kürzel aus dem Persönlichen Wörterbuch (sie werden weiter aufgelöst) und Tippfehler-Paare, die du dir beigebracht hast. Sehr kurze Formen und Wörter, die ein Wörterbuch ohnehin kennt, stehen nicht auf der Liste; „dont“ wird wie bisher zu „don't“. Fehlt dir ein Wort, hilft das Gedächtnis, wenn du es eingeschaltet hast (es ist aus, bis du es einschaltest): eigene Wörter nach dreimal Tippen.

### Einheitliches Befehlssystem

Sechs Bereiche, ein Aufbau: `@bereich [aktion] [angaben …]`. Damit änderst du deine eigenen
Einstellungen und Daten direkt beim Tippen. Du brauchst keine Zeichen wie `&`: Die Tastatur
weiß, welche Angaben ein Bereich hat (App, Gruppe, Sprache, Name, Text, an/aus), und ordnet deine
Wörter zu, in beliebiger Reihenfolge und mit Tippfehlern (dieselbe Toleranz wie bei den
Sprachwörtern: ab drei Buchstaben reicht der Anfang, ab vier ein Tippfehler mit richtigem ersten
Buchstaben). App-Namen sind die Namen auf dem Startbildschirm (`whatsapp`, `duolingo`,
`google maps`).

So läuft es ab:

1. Tippe z. B. `@lang whatsapp german`. Die Leiste zeigt in Klartext, was passieren würde:
   „Sprache WhatsApp → Deutsch (DE)“, farbig wie in einem Code-Editor: Bereich blau, Aktion lila,
   Ziel gelb, Wert grün, Trenner türkis, Unbekanntes rot mit „?“ (auf hellem Design dunklere
   Töne).
2. Ein Tipp auf die Vorschau führt aus und löscht den Befehlstext. Danach steht 8 Sekunden lang
   „↶ Rückgängig“ in der Leiste; ein Tipp stellt alles wieder her (der Text kommt nicht zurück).
3. Passt ein Wort zu zwei Dingen (`@lang whatsap german` bei WhatsApp und WhatsApp Business),
   zeigt die Leiste beide Deutungen; du tippst die richtige an. `app:` erzwingt eine App
   (`@lang app:english deutsch`, wenn eine App „English …“ heißt).
4. Enter führt nur aus, wenn es genau eine Deutung gibt und sie harmlos ist (z. B. eine Sprache
   für eine App setzen, Formatwächter für eine App aus). Löschen, alles mit `all`, Gedächtnis an,
   der Zwischenablage-Schalter, das Überschreiben eines Bausteins und das Überschreiben einer
   Gruppe mit weniger Apps gehen nur per Tipp; Enter zeigt dann „Tippe auf die Vorschau, um den
   Befehl auszuführen“ und schickt nichts ab. Ist ein Wort unbekannt, zeigt nur an oder ist eine
   Liste voll, sagt Enter das (z. B. „Unbekannt: foo (… oder die Apps sind noch nicht geladen)“,
   „Das zeigt nur an; tippe auf einen Eintrag“) und schickt ebenfalls nichts ab.
5. Ein graues Vorschau-Chip heißt: noch nicht ausführbar (ein Wort ist unbekannt, es fehlt etwas
   oder eine Liste ist voll: höchstens 100 Apps mit eigener Sprache oder eigenem Formatwächter,
   50 ausgeschlossene Apps, 20 Gruppen mit je 30 Apps, 50 Bausteine). Ein Tipp sagt, was fehlt.
   Lässt sich beim Ausführen etwas nicht ändern, wird alles schon Geänderte zurückgenommen und der
   Befehlstext bleibt stehen („Nichts geändert: … ließ sich nicht ändern“).
6. Normaler Text, der nur mit einem Bereichswort beginnt („Hallo @gruppe wer kommt heute?“, ein
   bloßes `@mem`), ist kein Befehl: Enter schickt ihn wie gewohnt ab, und die Autokorrektur
   arbeitet ab dem zweiten Wort hinter dem Bereichswort. Ein Befehl ist es, sobald ein Wort eine
   App, Gruppe, Sprache oder Aktion ist und das erste Wort dazu passt oder höchstens ein Wort
   unbekannt ist (`@lang foo german` ist rot und Enter schickt es nicht ab). Ein genau getipptes
   Aktionswort (`off`, `aus`, `del` …) oder ein genauer Sprachname als erstes oder letztes Wort
   reicht auch bei bis zu drei unbekannten Wörtern davor oder dahinter: `@guard google maps off`,
   bevor die Tastatur die Apps kennt, ist rot, und Enter schickt es nicht ab. Sätze wie „@mem ich
   bin aus Berlin“, „@sprache ist weg“ oder „@group we set off at 9“ bleiben Text (häufige
   Satzwörter wie „ist“, „ich“, „we“ sind nie ein App-Name).

Aktionen (Englisch zuerst, Deutsch geht genauso):

| Aktion | Englisch | Deutsch | Bedeutung |
| --- | --- | --- | --- |
| zeigen | (keine) | (keine) | ohne Wert: zeigt, was gilt (`@lang whatsapp`) |
| setzen | (keine) oder `set` | (keine) oder `setze` | mit Wert: setzt und überschreibt (`@lang whatsapp german`) |
| auflisten | `list` oder `?` | `zeig`, `zeige`, `liste` | alles, was festgelegt ist; `?` zeigt zuerst den Aufbau |
| löschen | `del`, `delete`, `remove` oder `-` | `weg`, `lösch`, `löschen`, `entfernen` | zurück auf normal (nur per Tipp) |
| an / aus | `on` / `off` | `an` / `aus` | schaltet etwas für eine App oder alle an oder aus |
| dazu | `add`, `new` oder `+` | `neu`, `dazu` | neuer Baustein, Apps zu einer Gruppe |

Ziele: eine App (`whatsapp`), eine eigene Gruppe (`messenger`), alle Apps (`all`, `apps`, `alle`,
`überall`). Ohne Ziel gilt die App, in der du gerade tippst. Mit `all` setzt du für jede App
denselben Wert; eigene Einträge der Apps fallen dabei weg (nur per Tipp, Rückgängig holt sie
zurück).

Alle Befehle:

| Befehl | Was er macht |
| --- | --- |
| `@lang` | wie bisher: deine aktiven Sprachen als Chips |
| `@lang spanisch`, `@deutsch`, `@sp` | wie bisher: dieses Feld auf die Sprache umstellen; per Enter nur mit einem genauen Namen der Sprache (`@deutsch`, nicht `@deu`; angefangene Wörter wie `@Fran` schickt Enter als Text ab) und genau einem Chip; ist sie schon aktiv oder gibt es mehrere Layouts, sagt Enter das nur und schickt nichts ab |
| `@lang duolingo spanish` | Duolingo startet ab jetzt auf Spanisch (bei jedem neuen Feld der App), auch wenn „Sprache pro App merken“ aus ist |
| `@lang whatsapp german` | ersetzt die Sprache von WhatsApp; tippst du das in WhatsApp selbst, stellt die Tastatur sofort um |
| `@lang whatsapp` | zeigt die Sprache von WhatsApp („keine eigene“, wenn keine festgelegt ist) |
| `@lang apps`, `@lang list`, `@lang ?` | alle festgelegten Sprachen je App; ein Tipp auf einen Eintrag schreibt `@lang app:… `, du tippst die neue Sprache dahinter |
| `@lang whatsapp del` | WhatsApp hat wieder keine eigene Sprache (nur per Tipp) |
| `@lang all german` | jede App startet auf Deutsch, eigene Einträge fallen weg (nur per Tipp) |
| `@lang messenger german` | alle Apps der Gruppe „messenger“ auf Deutsch |
| `@guard banking off` | Formatwächter in der Banking-App aus (keine Hinweise für E-Mail, Telefon, IBAN dort) |
| `@guard banking on` | dort wieder an, auch wenn der Formatwächter in den Einstellungen aus ist |
| `@guard off`, `@guard on` | für die App, in der du tippst |
| `@guard all off`, `@guard all on` | der Schalter in den Einstellungen; eigene Einträge fallen weg (nur per Tipp) |
| `@guard banking del` | Banking folgt wieder dem Schalter (nur per Tipp) |
| `@guard list` | Schalter und alle Apps mit eigenem Eintrag |
| `@mem whatsapp off` | das Gedächtnis lernt in WhatsApp nicht und zeigt dort nichts (wie „Apps ausschließen“) |
| `@mem whatsapp on` | wieder einschließen (nur per Tipp, weil es das Lernen einschaltet) |
| `@mem all off`, `@mem all on` | der Schalter „Merken, wie ich schreibe“; `all on` nimmt auch alle ausgeschlossenen Apps zurück (nur per Tipp) |
| `@mem list` | Schalter und ausgeschlossene Apps |
| `@group messenger whatsapp signal` | legt die Gruppe „messenger“ an oder ersetzt ihre Apps (fallen dabei Apps weg, nur per Tipp) |
| `@group messenger add telegram` | Telegram dazu |
| `@group messenger del signal` | Signal raus (nur per Tipp) |
| `@group messenger del` | Gruppe löschen (nur per Tipp) |
| `@group messenger`, `@group list` | Apps einer Gruppe, alle Gruppen |
| `@snip add gruss Viele Grüße, Lewin` | neuer Baustein „gruss“ mit diesem Text (Platzhalter wie `{datum}` gehen); gibt es den Namen schon, ersetzt er ihn (nur per Tipp) |
| `@snip del gruss` | Baustein löschen (nur per Tipp, Rückgängig holt ihn zurück) |
| `@snip list`, `@snip ?` | alle Bausteine wie `@snip`; ein Tipp fügt einen ein |
| `@snip gruss` | wie bisher: einfügen (heißt ein Baustein wie eine Aktion, z. B. „neu“, gewinnt der Baustein) |
| `@clip on`, `@clip off` | Zwischenablage-Verlauf an oder aus, wie der Schalter in den Einstellungen (ungepinnte Einträge gehen dabei; nur per Tipp) |
| `@clip`, `@clip wort`, `@clip list` | wie bisher: Verlauf durchsuchen |
| `@?` | alle Bereiche; ein Tipp schreibt `@lang ` usw. |

Deutsche Namen der Bereiche: `@sprache`, `@baustein`, `@ablage`, `@wächter` (auch
`@waechter`), `@gedächtnis`, `@gruppe`; z. B. `@wächter banking aus` oder `@gruppe messenger neu
telegram`.

Ketten: Mehrere Befehle in einer Zeile, getrennt durch `;`, `then`, `dann` oder `→` (auch `->`).
Der Bereich gilt weiter bis zum nächsten `@`: `@lang whatsapp german; duolingo spanish; @guard
banking off` sind drei Schritte in einer Vorschau, ein Tipp führt alle aus, ein „Rückgängig“ nimmt
alle zurück. Bei `@snip add` und bei der Suche `@clip wort` gehört alles bis zum nächsten `@` zum
Text (auch „; bis dann“). Ohne Trenner geht eine Kette nur weiter, wenn der Teil davor schon ein
vollständiger Befehl ohne unbekanntes Wort ist; in „@gruppe hey, hier meine Adresse: @snip adr“
ist nur `@snip adr` der Befehl (und seine Felder gehen mit Enter weiter). Einfügen (`@snip name`,
`@clip wort`) und Anzeigen (`@lang whatsapp`) gehen nur einzeln, nicht in einer Kette; die
Vorschau ist dann grau. `@clip on dann @lang …` ist eine Kette (vor dem Trennwort steht nur das
Aktionswort), `@clip pizza dann pasta` bleibt eine Suche. `@deutsch @guard banking off` geht wie
`@lang deutsch @guard banking off` auch ohne Trenner. Steht am Ende einer Kette nur ein
Bereichswort (`@lang whatsapp german; @guard `), ist die Vorschau grau und Enter sagt „In diesem
Befehl fehlt noch etwas“.

Vorschläge: Beim Tippen zeigt die Leiste hinter der Vorschau passende Apps, Gruppen und Sprachen
(`@lang whats` → „WhatsApp“, „WhatsApp Business“), nach einer App mit Leerzeichen deine Sprachen,
bei `@guard`/`@mem` nach der App „on“ und „off“, bei `@snip del` deine Bausteine. Ein Tipp
vervollständigt nur das Wort.

Grenzen: Befehle ändern nur deine eigenen Einstellungen und Daten, nie Werkzeuge, Adressen,
Anbieter oder Berechtigungen. Nichts in Passwortfeldern, im Inkognito-Modus oder bei gesperrtem
Gerät. Die Farben stehen nur in der Leiste, nicht im Textfeld. Direkt nach dem Start der Tastatur
kennt sie die App-Namen einen Augenblick lang noch nicht (das Wort ist dann rot). Abschalten
einzeln unter Einstellungen → Leiste & Befehle → Alle Befehls-Einstellungen → Befehle (`@guard`, `@mem`, `@group` sind neu). Noch auf
keinem Gerät ausprobiert.

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
  erscheinen beim nächsten Mal sofort ohne Internet. Löschen: Einstellungen → Leiste & Befehle →
  Alle Befehls-Einstellungen → „Gespeicherte Mischungen löschen“.
- Nicht in Passwortfeldern, im Inkognito-Modus oder bei gesperrtem Gerät; dort bleibt der
  Mixer aus („Der Emoji-Mixer ist in diesem Feld aus“).

## Zeichen-Kunst

Bilder und Schriftzüge aus Zeichen (Braille-Punkte, Blöcke, ASCII, Emojis) und Text in
Unicode-Stilen wie 𝓢𝓬𝓱𝓻𝓲𝓯𝓽 oder 𝗙𝗲𝘁𝘁. Alles passiert auf dem Telefon, ohne Internet.

- Öffnen: die Emoji-Taste in der unteren Reihe lange drücken, im Popup auf den Stift „✎“ gleiten
  und loslassen (die Emoji-Taste muss da sein: Einstellungen → Tasten & Sprachen →
  Zusatztasten → „Emoji-Taste“; der flache Look schaltet sie ein). Ein kurzer Tipp öffnet weiter die Emojis. Das
  kleine „✎“ oben auf der Emoji-Taste erinnert an den Langdruck (es erscheint mit „Tasten-Hinweise
  anzeigen“, Standard an). Statt der Tasten erscheint das Fenster; das Feld bleibt unverändert.
- Oben steht der Text aus dem Feld („Text: …“, bis 300 Zeichen um die Schreibmarke, oder was du
  markiert hast; eine sehr lange Markierung wird nicht übernommen, dann erscheint ein Hinweis).
  Antippen öffnet die Tasten des Fensters: tippen, ⌫ löscht, ↵ neue Zeile, „123“ für Ziffern und
  Zeichen, ⇧ und ß ergibt ẞ, „Fertig“ schließt sie. Solange du tippst, nehmen die Tasten die ganze
  Breite ein; Vorschau, Optionen und Knöpfe kommen mit „Fertig“ zurück (im Querformat auch die
  Reiter und „Schließen“). Diese Tasten schreiben nur in das Fenster.
- Reiter (oben):
  - **Zeichnen**: Stift, Radierer, Linie, Kreis (vom Aufsetzen bis zum Loslassen), Füllen (tippen
    in eine geschlossene Fläche; gefüllt wird beim Loslassen), Stärke Dünn/Mittel/Dick, „Zurück“
    nimmt den letzten Schritt zurück, „Leeren“ löscht alles.
  - **Bild**: die neuesten Fotos und Screenshots (einmal je Öffnen des Fensters gelesen; neue
    Bilder erscheinen nach erneutem Öffnen); ein Tipp legt das Bild unter die Zeichenfläche,
    hochkant fotografierte Bilder stehen dabei aufrecht.
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
  - **App**: WhatsApp, Telegram, Discord, Signal, Andere. Die App des Felds wird erkannt; jede
    andere App bekommt „Andere“ (deine Wahl hier gilt, bis du das Fenster schließt). Der Regler
    stellt die Breite in Zeichen ein (8–60, je App gemerkt, sobald du ihn loslässt); die Chatblase
    darunter zeigt, ob es auf ein Handy passt: zu breite Zeilen brechen dort um wie im Chat.
    „Codeblock“ setzt ``` drumherum, damit die App eine Schrift mit gleich breiten Zeichen nimmt
    (WhatsApp und Discord an, Telegram wählbar, Signal kann es nicht). Das gilt nur für Bilder,
    Groß-Schrift, Rahmen und Nebeneinander; reiner Text in Unicode-Stilen bekommt nie einen
    Codeblock. „⠀ statt Leerzeichen“ (an) füllt Lücken mit dem unsichtbaren Braille-Zeichen, damit
    die App sie nicht wegkürzt.
- Jede Änderung zeichnet die Vorschau sofort neu. Sie zeigt genau den Text, den „Einfügen“
  einsetzt, also auch die ``` eines Codeblocks. Darunter steht, wie breit die Kunst ist, oder eine
  Warnung, wenn sie in der App vermutlich zerfällt (z. B. „Rahmen und Nebeneinander verrutschen
  ohne Codeblock“, „Emojis und Schrift-Stile sind im Codeblock nicht gleich breit“). Groß-Schrift
  endet nach 80 Zeilen („Groß-Schrift nach 80 Zeilen gekürzt“). Kunst mit mehr als 8192 Zeichen
  lässt sich nicht einfügen („Zu groß für das Feld …“), nur kopieren.
- Übernehmen, immer nur per Tipp: **Einfügen** setzt die Kunst an die Schreibmarke. **Ersetzen**
  (nur wenn hinter der Schreibmarke nichts steht und sich das Feld seit dem Öffnen nicht geändert
  hat) setzt sie an die Stelle des gelesenen Texts, so wird aus „Hallo“ z. B. „𝐇𝐚𝐥𝐥𝐨“. Hast du
  Text markiert, heißt der Knopf **Markierung ersetzen** und steht statt „Einfügen“ da: er setzt
  die Kunst an die Stelle der Markierung. Danach heißt der Knopf 8 Sekunden lang **Rückgängig**:
  ein Tipp nimmt genau das wieder heraus (nur solange danach nichts im Feld geändert wurde; eine
  Markierung kommt als Text zurück, nicht markiert). **Kopieren** legt die Kunst in die
  Zwischenablage. **Baustein** fragt nach einem Namen (nur Buchstaben) und speichert sie als
  Textbaustein, später mit `@snip Name`. „Schließen“ verwirft alles.
- Das Fenster schließt sich und verwirft Zeichnung, Foto und Text auch, wenn du die Tastatur mit
  Zurück einklappst, das Telefon drehst, das Design der Tastatur wechselst oder in ein anderes
  Feld tippst.
- Nicht in Passwortfeldern, im Inkognito-Modus oder bei gesperrtem Gerät: Dort fehlt der Stift
  bzw. es erscheint „Zeichen-Kunst gibt es in diesem Feld nicht.“ Das gilt auch für Kopieren und
  Baustein, wenn das Gerät sperrt, während das Fenster offen ist.
- Grenzen: Screenreader (TalkBack) lesen Zeichen-Kunst als Zeichensalat vor (den Stift im Popup
  liest TalkBack als „Stift in der Emoji-Taste: Zeichen-Kunst“). Beim Empfänger
  können ein sehr kleiner Bildschirm oder eine große Schrift die Zeilen umbrechen lassen.

## Ablage

(Bis Build 36 hieß sie „Prompt-Ablage“.)

Deine eigene Ablage für Texte, die du oft in KI-Chats einfügst. Sie bleibt, bis du einen
Eintrag löschst, und hat die Reihenfolge, die du wählst. Alles passiert auf dem Telefon, es wird nichts
gesendet. Stand der Prüfung: nur mit automatischen Tests, auf keinem Gerät ausprobiert.

- **Eintrag anlegen** (Einstellungen): Einstellungen → Leiste & Befehle → „Ablage“ (die Suche
  findet sie auch). „Neuer Eintrag“ öffnet eine Karte mit „Titel (optional)“ und „Text“. Ohne Titel
  zeigt die Liste die erste Zeile des Texts. „Aus Zwischenablage einfügen“ setzt den kopierten Text an die
  Schreibmarke im Feld; die Zwischenablage wird nur auf diesen Tipp gelesen. Unter den Feldern steht, wie
  viele Zeichen du hast (Titel höchstens 60, Text höchstens 8000). „Speichern“ legt den Eintrag ganz oben
  ab. Passt etwas nicht, wird es nicht gekürzt: Du bekommst einen Hinweis und der Eintrag wird nicht
  gespeichert.
- **Eintrag einfügen** (Tastatur): In einem Textfeld die Komma-Taste lange drücken und im Popup auf das
  Symbol der Ablage gleiten (neben dem Symbol der Zwischenablage), dann loslassen. Statt der Tasten
  erscheint das Fenster „Ablage“ mit deinen Einträgen (Titel oder erste Zeile, darunter bis zu
  zwei Zeilen Text). Ein Tipp auf eine Zeile fügt den gespeicherten Text einmal an der Schreibmarke ein und
  schließt das Fenster. Er wird dabei nicht autokorrigiert und nicht gelernt. Steht im Feld etwas
  markiert, ersetzt der Eintrag es nicht: Es erscheint „Erst die Schreibmarke setzen: eine Markierung wird
  nicht ersetzt.“ Setze die Schreibmarke und tippe noch einmal. „Schließen“ schließt das Fenster, ohne
  etwas einzufügen; dasselbe tut Zurück.
- **Kopiertes in der Ablage speichern**: Kopiere in einer beliebigen App einen Text, öffne das Fenster und tippe
  die erste Zeile „Kopiertes in der Ablage speichern“. Der Text kommt als neuer erster Eintrag (ohne Titel) in
  die Liste: „Als erster Eintrag gespeichert.“ Die Zwischenablage wird nur auf diesen Tipp gelesen, nicht
  beim Öffnen. Mögliche Meldungen: „In der Zwischenablage ist kein Text.“; „Der kopierte Text ist zu lang
  (höchstens 8000 Zeichen). Nichts gespeichert.“; „Schon gespeichert.“ (der oberste Eintrag ist genau dieser
  Text); „Die Ablage ist voll (200 Einträge). Lösche zuerst einen.“; und „Der kopierte Text sieht nach
  einem Geheimnis aus (Passwort, Schlüssel oder Code). Er wird nicht gespeichert.“ Diese Sperre folgt der
  Regel des Zwischenablage-Verlaufs: abgelehnt wird, was als vertraulich markiert ist oder beim Kopieren
  als Geheimnis sortiert wurde, ein Geheimnis und ein bloßer Einmalcode. Ein Text, der nur eine
  codeähnliche Zahl in einem Satz enthält (etwa „Schreibe Python-Code, der 10000 Zahlen ausgibt“), wird
  gespeichert. Grenze: Nach einem Neustart der Tastatur weiß sie nicht mehr, wo etwas kopiert wurde; ein
  harmlos aussehender Text, der vorher in einem Passwortfeld kopiert wurde, kann dann gespeichert werden
  (der Verlauf hat dieselbe Schwäche). Prüfe den Eintrag im Zweifel auf der Seite und lösche ihn.
- **Sortieren**: Auf der Seite „Ablage“ gibt es „Sortieren“ (erst ab zwei Einträgen). Im Dialog „Einträge
  sortieren“ ziehst du die Zeilen in die gewünschte Reihenfolge und bestätigst. Diese Reihenfolge gilt dann
  auch im Fenster der Tastatur. Sortieren geht nur auf der Seite, nicht im Fenster. Hat sich die Liste
  inzwischen geändert, steht „Die Liste hat sich inzwischen geändert und wurde neu geladen; die
  Reihenfolge wurde nicht gespeichert.“
- **Bearbeiten und löschen**: Je Zeile gibt es „Bearbeiten“ (der Eintrag behält seine Stelle) und „Löschen“.
  Löschen fragt „Diesen Eintrag löschen? Das lässt sich nicht rückgängig machen.“ Beim Bearbeiten und
  Neuanlegen fragt Zurück oder „Abbrechen“ bei ungespeicherten Änderungen „Änderungen verwerfen?“ („Verwerfen“
  oder „Weiter bearbeiten“); ohne Änderungen schließt es gleich.
- **Ordner** (seit Etappe 77): Du kannst Einträge in Ordnern ablegen, eine Ebene, höchstens 50 Ordner. Auf der Seite
  „Ablage“ legt „Neuer Ordner“ einen Ordner an (Name 1 bis 40 Zeichen; zwei Ordner dürfen nicht gleich heißen,
  auch nicht in anderer Groß-/Kleinschreibung; „Neuer Ordner“ und „Ordner umbenennen“ fragen bei einem geänderten Namen vor dem Verwerfen nach, wie der Editor). Der neue Ordner wird gleich gezeigt, ein neuer Eintrag landet dann darin.
  Sobald es Ordner gibt, steht über der Liste eine Reihe Chips: „Alle“, deine Ordner und „Ohne Ordner“ (nur, wenn ein Eintrag
  keinen Ordner hat). Ein Tipp auf einen Chip zeigt nur diese Einträge. Ist ein Ordner gewählt, gibt es „Ordner umbenennen“
  und „Ordner löschen“. Löschen fragt und sagt, wie viele Einträge im Ordner sind: Sie werden nicht gelöscht, sie
  wandern nach „Ohne Ordner“. „Ordner sortieren“ (ab zwei Ordnern) bestimmt die Reihenfolge der Chips, auch im Fenster der
  Tastatur. Welchem Ordner ein Eintrag gehört, stellst du im Editor des Eintrags unter „Ordner“ ein (dort stehen „Ohne Ordner“
  und deine Ordner); in „Alle“ steht unter jedem Eintrag sein Ordner. „Sortieren“ ordnet in einem gewählten Ordner nur dessen
  Einträge; alle anderen behalten ihren Platz. Im Fenster der Tastatur steht dieselbe Chip-Reihe oben, aber nur, wenn es Ordner
  gibt; ohne Ordner sieht das Fenster aus wie vorher. „Kopiertes in der Ablage speichern“ legt den Text in den Ordner, der
  gerade gezeigt wird („Alle“ und „Ohne Ordner“: ohne Ordner; die Meldung nennt den Ordner). Der zuletzt gewählte Chip bleibt
  nur, solange die Tastatur läuft; nach einem Neustart steht das Fenster wieder auf „Alle“. Ist der gezeigte Ordner inzwischen
  gelöscht, wird nichts gespeichert: „Diesen Ordner gibt es nicht mehr.“ Verschieben in einen anderen Ordner geht nur auf der Seite.
  Stand der Prüfung: nur mit automatischen Tests, auf keinem Gerät ausprobiert (auch nicht, wie die Chip-Reihe aussieht und
  scrollt).
- **Verwalten**: Im Fenster der Tastatur öffnet „Verwalten“ die Seite „Ablage“ (bei gesperrtem Gerät
  nicht).
- **Ablage zurücksetzen**: Nur wenn die Datei der Ablage sich nicht lesen lässt, zeigt die Seite „Die Einträge
  sind gerade nicht les- oder speicherbar“ und dazu „Ablage zurücksetzen“. Nach der Rückfrage werden die
  Datei und alle darin gespeicherten Einträge gelöscht; das lässt sich nicht rückgängig machen. Eine lesbare
  Datei löscht die Tastatur nie. Ist nur das Gerät gesperrt, erscheint der Hinweis ohne diesen Knopf: Entsperre
  das Telefon. Im Fenster steht bei unlesbarer Ablage „Die Ablage lässt sich gerade nicht lesen. Öffne
  Verwalten, um sie zurückzusetzen.“
- **Wo es sie nicht gibt**: In Passwortfeldern, im Inkognito-Modus, in Feldern, die Lernen verbieten
  (private Chats), in Zahlen-Layouts und bei gesperrtem Gerät fehlt das Symbol im Popup; wird das Fenster
  dennoch erreicht (etwa weil das Telefon sperrte, während es offen war), erscheint „Die Ablage gibt
  es in diesem Feld nicht.“ und es wird nichts eingefügt. Hat sich das Feld geändert, steht „Das Feld hat sich
  geändert. Nichts eingefügt.“; ein Eintrag, den du inzwischen auf der Seite gelöscht hast: „Diesen Eintrag
  gibt es nicht mehr.“
- **Wo die Einträge liegen und wie sie verloren gehen**: In einer Datei im geschützten Speicher der App, nur
  auf dem Telefon und nur nach dem Entsperren lesbar. Sie bleiben über Neustarts und App-Updates erhalten.
  Sie sind in „Sichern und Wiederherstellen“ (Einstellungen → System & Daten) enthalten, wenn das Telefon beim
  Sichern entsperrt ist und mindestens ein Eintrag da ist (die Ordner der Einträge gehören dazu; Ordner ohne einen einzigen Eintrag werden
  nicht gesichert, und ein Backup-Eintrag ohne Einträge ersetzt beim Wiederherstellen nie etwas, auch wenn er Ordner enthält; eine Sicherung aus
  der Zeit vor den Ordnern stellt Einträge ohne Ordner her und ersetzt vorhandene Ordner); ohne Sicherung gehen sie verloren, wenn du die App
  deinstallierst oder ihre Daten löschst. Beim Wiederherstellen werden sie ganz zuletzt durch die Einträge
  der Datei ersetzt (die Einträge auf dem Telefon, die nicht in der Datei stehen, verschwinden dann). Eine
  Datei ohne Einträge (zum Beispiel von einer älteren Version) und eine Datei mit leerer Liste lassen die
  Einträge auf dem Telefon, ohne Meldung. In drei Fällen wird die Wiederherstellung der Einträge
  übersprungen und eine zweite Meldung erscheint, die übrige Wiederherstellung gilt trotzdem: „… Der Eintrag
  ist beschädigt.“ (die Einträge im Backup sind unbrauchbar oder zu groß), „… Das Gerät ist gesperrt.“ (erst
  entsperren, dann erneut wiederherstellen) und „… Die Ablage auf diesem Telefon ist nicht lesbar.“
  (erst unter Einstellungen → Leiste & Befehle → Ablage zurücksetzen und das Backup erneut
  wiederherstellen). In allen dreien bleiben die Einträge auf dem Telefon unverändert. Die echte
  Wiederherstellung im Dialog ist nicht auf einem Gerät ausprobiert. Grenzen: 200 Einträge, Titel 60, Text 8000 Zeichen, Datei höchstens 2 MiB (bei vielen
  Emojis kann die Datei vor 200 Einträgen voll sein: „Nicht genug Platz …“).
- Ungeprüft: Langdruck auf die Komma-Taste und Aussehen des Popups am echten Gerät, das Fenster im
  Querformat, auf Tablets und im flachen Stil, und die Geschwindigkeit mit sehr vielen langen Einträgen.

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
- Ein einzelner Strich schräg nach oben rechts ergibt „/“, schräg nach unten rechts „\“, ein
  flacher „–“, ein senkrechter „|“. Ein Symbol bekommst du nur, wenn du alle seine Striche malst:
  „%“ mit Strich und zwei Punkten (oder zwei Kreisen), „÷“ mit Strich und zwei Punkten, „!“ mit
  Strich und Punkt. Ein flacher Strich (bis etwa 15 Grad) bleibt „–“, ein fast senkrechter (ab etwa
  78 Grad) „|“. Passt ein einfacheres Symbol, gewinnt es: Ein Strich mit nur einem Punkt bleibt „–“
  oder „/“, die Leiste bietet „÷“ oder „%“ höchstens als zweite Wahl an. Passt keines, nimmt die
  Tastatur das nächste Symbol, dem Teile fehlen (z. B. „?“ ohne Punkt oder „+“ in einem Zug).
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
- Persönliche Vorlagen: Einstellungen → Gesten & Gefühl → Symbole malen → „Aus Korrekturen lernen“
  (anfangs aus). Ist es an und wählst du in der Leiste ein anderes Symbol (beim Malen oder
  danach), merkt sich die Tastatur deine Zeichnung für dieses Symbol, bis zu drei je Symbol, die
  neuesten bleiben. Wählst du danach noch einmal um, wandert die Zeichnung mit; wählst du das
  zuerst erkannte Symbol zurück, ist alles wie vorher. Eine Zeichnung mit einem Punkt merkt sie sich
  nur für ein Symbol mit Punkten (÷). Nur, wenn auch „Personalisierte Vorschläge“ an ist (Einstellungen
  → Tippen & Korrektur → Alle Korrektur-Einstellungen); nicht im Inkognito-Modus, bei „Lernen pausieren“ oder gesperrtem Telefon. „Gelernte Wörter löschen“ (Einstellungen → Tippen & Korrektur → Wörter & Lernen → Gelernte Wörter, dort steht auch
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
- Unter Einstellungen → Gesten & Gefühl → Gestenschreiben (nur mit Gesten-Bibliothek) ist der Schalter „Gestenschreiben“ ausgegraut, solange
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

Alle Schieberegler der Einstellungen (z. B. Aussehen → Größe & Form → Tastaturhöhe, Schriftgröße; Vibrationsdauer,
Lautstärke; Deckkraft und Helligkeit im Farbwähler; Eckenrundung, Abstand und Höhe im
Design-Editor) ticken beim Ziehen leicht. Am Standardwert, an den Enden und an runden Werten
(z. B. 50 %, 100 %, 150 %, Zehner bei Prozent und Millisekunden) rastet der Regler mit einem etwas
stärkeren Impuls ein; ziehst du weiter, löst er sich wieder. Einen Wert direkt neben einer Rast
(z. B. 49 %) erreichst du so: etwas über die Rast hinausziehen und langsam zurück, dann rastet sie
nicht sofort wieder ein. Mit TalkBack stellst du jeden Wert genau ein. Gespürt wird das nur, wenn
Einstellungen → Gesten & Gefühl → Vibration & Ton → „Vibration bei Tastendruck“ an ist; bei „Bitte nicht stören“ nur, wenn
Vibrieren dort erlaubt ist. Die RGB-Seite rastet noch nicht.

## Design auswählen

Einstellungen → Aussehen → Karte „Designs“ (Kachel antippen) oder dort „Alle … Designs“ bzw. im
Tastaturmenü die Pille „Design“ für die ganze Design-Seite: Kachel antippen.
„Flacher Look“ setzt auch Reihen, Größen und Leiste; zurück geht es unter System & Daten → „+ Neues Profil“ (öffnet Tastaturprofile).
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
- Eigene Muster (unter der Galerie): deine selbst gestalteten Lichtmuster als Bilder, Antippen wählt
  eins wie ein eingebautes (mit „Gleiches Muster überall“ aus nur für das gewählte Element). Siehe
  „Eigene Lichtmuster“ unten.
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
  Taste). „Nachleuchten“ stellt ein, wie lange das dauert (0,2–5 s). In Passwort- und PIN-Feldern
  und im Inkognito-Modus gibt es keine Reaktion aufs Tippen (das Licht soll nicht zeigen, welche
  Taste es war); in einem neuen Feld beginnt sie frisch.
- Bildwiederholrate: zwei Farbflächen untereinander, „30 HERTZ“ (gelb-orange, 30 Bilder pro
  Sekunde, spart Akku) und „60 HERTZ“ (türkis-grün, flüssiger). Tippe auf eine; die gewählte hat
  einen Rahmen und volle Farbe, die andere ist blasser, ihre Schrift bleibt gut lesbar. In jeder
  Fläche wandert ein leuchtender Tropfen, beide gleich schnell und genau übereinander: Der obere
  springt in 30 Schritten pro Sekunde, der untere gleitet mit 60, so siehst du den Unterschied.
  Die Tropfen stehen still, wenn beim Öffnen der Seite die Android-Animationen aus sind oder
  solange der Energiesparmodus an ist (das merkt die Seite sofort). Darunter „Im
  Energiesparmodus anhalten“ (an).

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
Designprofil (System & Daten → „+ Neues Profil“ → Tastaturprofile → Design).

## Eigene Lichtmuster

Auf der RGB-Seite unter der Mustergalerie, Abschnitt „Eigene Muster“:

- „Eigenes Muster aus „…““ macht aus dem gerade gewählten Muster ein eigenes (Wellen und Kreis
  genau, die Texturen angenähert; Name „… (eigen)“; bei einem eigenen Muster eine Kopie „… (Kopie)“,
  der Name wird so gekürzt, dass er in 32 Zeichen passt) und öffnet den Editor. Das neue Muster ist
  gleich für das gewählte Element eingestellt.
- Bild antippen wählt ein eigenes Muster, „Bearbeiten“ öffnet das gewählte im Editor.
- „Importieren“ liest eine Musterdatei (`.json`, höchstens 16 KB). Sie bekommt einen eigenen Namen,
  falls es den schon gibt („Name 2“). Eine Datei mit unbekannten Feldern, falschen Werten oder
  einer Adresse im Namen wird ganz abgelehnt; die Meldung sagt, warum. Zu große oder zu kleine
  Zahlen werden auf den erlaubten Bereich gesetzt. Kann die Datei nicht gelesen werden („Datei
  nicht lesbar“) oder hat die App keinen Zugriff mehr (verschoben, gelöscht), sagt die Meldung das.
- Stammen die gespeicherten eigenen Muster von einer neueren Version (oder sind sie beschädigt),
  steht dort ein Hinweis statt der Bilder; sie bleiben unverändert, Neuanlegen und Importieren
  fehlen, bis eine passende Version sie lesen kann.

Im Editor (Zurück führt zur RGB-Seite):

- Oben die Live-Vorschau mit deiner Bildwiederholrate; tippe darauf, um die Reaktionen aufs Tippen
  zu sehen.
- Name (1–32 Zeichen, ohne Zeilenumbruch, `<`, `>` oder Adressen): gespeichert wird er bei
  „Fertig“ auf der Tastatur, beim Verlassen des Feldes, mit Zurück und vor „Duplizieren“ oder
  „Exportieren“. Gibt es den Namen schon, steht danach „Name 2“ im Feld; ein ungültiger Name geht
  auf den gespeicherten zurück. „Duplizieren“, „Exportieren“ (du wählst, wo die Datei hin soll),
  „Löschen“ (mit Rückfrage; Elemente mit diesem Muster zeigen wieder ihr eingebautes Muster).
- Ebenen: bis zu vier, Ebene 1 liegt unten. „Ebene hinzufügen“ legt ein weißes Funkeln obenauf,
  „Nach oben“/„Nach unten“ ändern die Reihenfolge, „Ebene entfernen“ (mindestens eine bleibt).
- Form der gewählten Ebene: Fläche (eine Farbe, die durch die Stopps läuft), linearer, radialer
  oder Kegelverlauf, Wellen, Plasma, Rauschen, Streifen, Ringe vom Tippen (nur nach einem Tipp
  sichtbar), Spot auf der letzten Taste (gleitet zur zuletzt getippten Taste), Höhenlinien,
  Funkeln. „Mischen mit den Ebenen darunter“ (ab Ebene 2; Ebene 1 hat nichts darunter): Normal,
  Addieren, Multiplizieren, Aufhellen.
- Farben: der Streifen zeigt den Verlauf, darunter die Farbstopps als Kreise (TalkBack liest
  „Farbstopp 1, #FF0050“). Antippen wählt einen Stopp, noch einmal antippen (oder „Farbe ändern“)
  öffnet den Farbwähler; „Stopp hinzufügen“ setzt einen zwischen den gewählten und den nächsten,
  „Stopp entfernen“ (2–8 Stopps), der Regler setzt die Position (schiebst du den Stopp an einem
  anderen vorbei, bleibt er gewählt). „Wie gewählt“ oder „Farbkreis-Durchlauf“ (die Farben drehen sich einmal je Dauer).
- Bewegung: Tempo (−4 bis 4 in Schritten von 0,05, mal die „Dauer“ der RGB-Seite; minus läuft
  rückwärts, 0 steht), Richtung in Grad, Größe (0,25 bis 8 in Schritten von 0,05: wie oft sich die
  Form wiederholt), seitlich und hoch/runter verschieben (in Schritten von 1 %), Deckkraft, die
  Bewegungskurve wie beim Verlauf der RGB-Seite.
- Tippen: keine, Puls bei jeder Taste, Ring von der Taste, getippte Taste leuchtet auf. Die Ebene
  ist dann nur während des Nachleuchtens zu sehen (Länge: „Nachleuchten“ auf der RGB-Seite).

Jede Änderung wird sofort gespeichert, Regler und Kurvenpunkte beim Loslassen (beim Ziehen folgt
nur die Vorschau). Dauer, Rückwärts, Sättigung, Helligkeit und Deckkraft der RGB-Seite gelten auch
für eigene Muster; Verlauf, Farben und Wellenbreite der RGB-Seite sind für ein eigenes Muster
ausgeblendet. Eigene Muster haben keine feinen Linien und Punkte wie die eingebauten Texturen:
Höhenlinien und Funken sind weicher. Höchstens 24 eigene Muster. Alles lokal; ein Designprofil
merkt sich nur, welches eigene Muster gewählt war, nicht das Muster selbst. Bearbeitest du das
Muster, zieht das gespeicherte Designprofil mit; ein Profil von einem anderen Telefon oder mit
einem inzwischen gelöschten Muster zeigt das eingebaute Muster, nie ein anderes eigenes. Ringe,
Spot und Reaktionen eigener Muster ruhen wie die übrigen Tipp-Reaktionen in Passwortfeldern und im
Inkognito-Modus.

## Die Leiste wie Gboard

Im Leistenmodus „Tasten + Vorschläge“ (so ist die Tastatur anfangs eingestellt) sieht die Leiste in jedem Stil gleich
aufgebaut aus, wie in Gboard: links der **Raster-Knopf**, dann deine **Werkzeuge** in festen, gleichmäßig verteilten
Plätzen, ganz rechts das **Mikrofon** im Kreis (die Sprach- oder die Diktat-Taste, je nachdem, was in der Leiste liegt).
Nichts scrollt, es gibt keinen Pfeil zum Ein- und Ausblenden. Ein Werkzeug sitzt immer am selben Platz; die Plätze hängen nur
davon ab, **wie viele** Werkzeuge in der Leiste liegen (1 bis 5 in voller Größe, 6 bis 8 als kompakte Reihe mit kleineren
Symbolen, mehr als 8 passen nicht, die übrigen stehen im Raster). Jedes Symbol ist in jedem Design gut zu erkennen, und die Symbole liegen gleichmäßig verteilt (weniger Symbole: größere Abstände).

- **Was die Leiste zeigt:** Solange nichts getippt wird, die Werkzeuge. Tippst du, ersetzen die Wortvorschläge sie; ebenso
  Befehls-Chips, Auswahl-Chips, die Zwischenablage-Pille, Autofill und die Hinweise auf neue Builds (die Reihenfolge, in der
  diese sich gegenseitig verdrängen, ist unverändert). Der Pfeil „<“ ganz links zeigt dann die Werkzeuge wieder; mit dem nächsten
  Tastendruck kommen die Vorschläge zurück. Ist das Wort fertig oder das Feld leer, stehen die Werkzeuge wieder da. Schließt du die Tastatur,
  beginnt sie beim nächsten Öffnen mit den Werkzeugen.
- **Raster aller Werkzeuge:** Den Raster-Knopf antippen. Über den Tasten erscheinen alle Werkzeuge als Pillen mit Symbol und Namen
  (zwei Spalten, nach unten scrollbar): Textaktionen, Diktat, Skill-Tasten, Ablage, Zeichen-Kunst, Zwischenablage, Emoji, Rückgängig,
  Kopieren, Einfügen, Cursor-Tasten, Einhandmodus, Inkognito, Einstellungen, Design und alle weiteren. Ein Tipp auf eine Pille
  schließt das Raster und führt das Werkzeug aus, genau wie seine Taste. Der Knopf links oben ist jetzt ein Kreuz und schließt
  das Raster ohne etwas auszuführen. Das Mikrofon (Spracheingabe) erscheint nur, wenn dein Telefon eine Spracheingabe hat. „Zwischenablage leeren“ (löscht Verlauf, Tresor und Zwischenablage mit einem Tipp) steht nur im
  Raster, wenn du es vorher in einer deiner Listen hattest. Die Ablage ist in Zahlenfeldern abgedunkelt, wie ihr Symbol im Komma-Popup.
- **Was bei dir in der Leiste liegt, wenn du nie etwas eingestellt hast:** die ersten fünf Werkzeuge deiner bisherigen Reihenfolge
  (anfangs Textaktionen, Einstellungen, Zwischenablage, Rückgängig und die Mikrofon-Taste, die an das rechte Ende rückt), dazu
  die bisher angehefteten Tasten, wenn sie nicht darunter sind. Alles andere, was du bisher in der Leiste hattest (Wiederholen, Wort
  markieren, Kopieren, Einfügen, Pfeile), steht jetzt im Raster. Hast du die Reihenfolge schon in den Einstellungen verändert, gilt
  deine Reihenfolge. Die Tastatur schreibt dafür nichts weg, bevor du selbst etwas änderst.
- **Werkzeug nicht verfügbar:** In privaten Feldern (Passwort, Inkognito) und in Zahlenfeldern bleiben manche Werkzeuge an ihrem Platz, blasser und mit
  einem kleinen Schloss. Tippst du darauf, sagt die Leiste kurz warum („In diesem Feld nicht verfügbar: privates Feld“); ein Tipp irgendwo anders räumt die Zeile weg.
- **Leiste anpassen (Ziehen):** Raster-Knopf, dann „Anpassen“ (oder ein Werkzeug der Leiste ohne eigene Langdruck-Aktion lange drücken). Eine Pille im Raster
  kurz halten (oder seitlich ziehen): Eine Kopie des Symbols folgt dem Finger. Hältst du sie über die Leiste (oder knapp darunter), öffnet sich an der Stelle unter dem
  Finger eine Lücke, die anderen Symbole rücken beiseite; loslassen setzt das Werkzeug dort ein, gespeichert ist es sofort. Ein Symbol der Leiste anfassen und anders hin
  ziehen ordnet um; nach unten ins Raster ziehen nimmt es heraus. Loslassen außerhalb der Leiste legt ein Werkzeug aus dem Raster zurück. Ist die Leiste voll (acht),
  wackelt sie und sagt es. Senkrecht auf dem Raster wischen scrollt es wie immer. „Rückgängig“ nimmt die letzte Änderung zurück, „Zurücksetzen“ stellt die
  Standard-Leiste wieder her (nach „Ja“). Das Häkchen links beendet das Anpassen; die Zurück-Taste oder das Schließen der Tastatur auch (alles ist schon gespeichert).
- **Rückgängig und Wiederholen:** Nach einem Rückgängig trägt das Rückgängig-Symbol einen kleinen Pfeil: Wiederholen ist dann der Langdruck darauf. Der Pfeil
  verschwindet, sobald du tippst, löschst oder einfügst.
- **Markierter Text und die Werkzeuge:** Markierst du Text, zeigt die Leiste Kopieren, Ausschneiden, Einfügen und Löschen statt der Werkzeuge. Zu den
  Werkzeugen (zum Beispiel den Textaktionen) kommst du mit dem Pfeil „<“ ganz links („Werkzeuge zeigen“). Das gilt **für diese Markierung**: Ziehst du die Griffe oder
  meldet die App die Markierung neu, bleiben die Werkzeuge stehen, bis die Markierung endet oder du tippst. Der Pfeil links heißt dann „Auswahl-Aktionen zeigen“ und
  bringt Kopieren und Co. zurück; ein weiterer Tipp zeigt wieder die Werkzeuge. Während die Werkzeuge für eine Markierung stehen, öffnest du das Raster, indem du den Pfeil
  lange drückst (Anpassen) oder die Markierung aufhebst; dann ist es wieder der Raster-Knopf.
- **Passwortfelder und Inkognito:** Werkzeuge, die dort nicht erlaubt sind (Textaktionen, Diktat, Skill-Tasten, Ablage, Zeichen-Kunst),
  bleiben an ihrem Platz, sind aber abgedunkelt und reagieren nicht; im Raster ebenso. Beim gesperrten Telefon gibt es die Leiste nicht.
  Ist Inkognito an, zeigt der Knopf links wie bisher das Inkognito-Symbol; er tut dasselbe wie sonst.
- **Die Leiste anpassen:** Einstellungen → Leiste & Befehle → „Knöpfe wählen und sortieren“ → „Werkzeuge in der Leiste“ (Schalter und
  Reihenfolge; höchstens 8 schalten sich ein, die ersten 8 gelten). „Standard“ im Dialog stellt wieder die Reihenfolge von vorher her.
  Genauso, bequemer, direkt in der Tastatur: siehe nächster Abschnitt. Auch die Skill-Tasten-Schalter („Skill-Tasten anheften“) legen
  die Taste in die Leiste (auf den ersten freien Platz; ist die Leiste voll, passiert nichts).
- **Zurück zur alten Leiste:** Einstellungen → Leiste & Befehle → „Knöpfe wählen und sortieren“ → Schalter „Feste Leiste wie Gboard“ aus
  (nur bei „Tasten + Vorschläge“ sichtbar). Dann gilt wieder die ausklappbare Leiste mit Pfeil, scrollbaren Werkzeugen, angehefteten Knöpfen und,
  im flachen Look, dem kleinen Menü hinter dem Raster-Symbol; die Liste „Werkzeuge in der Leiste“ ist dann nicht in Gebrauch. Der Schalter ist
  der Rückweg, falls die feste Leiste auf deinem Telefon nicht gefällt (sie wurde noch nicht auf einem Gerät gesehen).
- Die anderen Modi sehen aus wie bisher: „Nur Vorschläge“ (nur Wortvorschläge und angeheftete Knöpfe), „Versteckt“, „Nur Leisten-Tasten“.

### In der Tastatur anpassen (halten und ziehen)

1. Raster-Knopf antippen, oben im Raster „Anpassen“ antippen. Oder: ein Werkzeug in der Leiste lange drücken, das keine eigene
   Langdruck-Aktion hat (Textaktionen, Einstellungen, Ablage …), oder den Raster-Knopf lange drücken. (Rückgängig, Zwischenablage, Pfeiltasten
   und andere behalten ihre Langdruck-Aktion.)
2. Die Leiste zeigt ihre Plätze als Umrandungen, im Raster steht „Zum Personalisieren halten und ziehen“ und der Raster-Knopf ist ein
   Häkchen. Werkzeuge, die schon in der Leiste liegen, sind im Raster abgedunkelt.
3. **Ein Werkzeug in die Leiste:** eine Pille lange drücken und auf einen Platz der Leiste ziehen. Die anderen Werkzeuge rücken schon
   beim Ziehen zur Seite; loslassen legt es ab. **Reihenfolge ändern:** ein Werkzeug der Leiste lange drücken und an einen anderen Platz
   ziehen. **Herausnehmen:** ein Werkzeug der Leiste lange drücken und ins Raster ziehen. Jedes Ablegen speichert sofort; wird die Tastatur
   mitten im Ziehen geschlossen oder lässt du außerhalb los, ändert sich nichts.
4. **Ohne Ziehen:** im Anpassen-Modus eine Pille antippen legt das Werkzeug auf den ersten freien Platz, eine schon eingebaute Pille
   antippen nimmt es heraus; ein Werkzeug der Leiste antippen nimmt es heraus. Für Screenreader haben die Pillen die Aktionen „Zur Leiste
   hinzufügen“ und „Aus der Leiste entfernen“, die Plätze „Aus der Leiste entfernen“, „Nach links“ und „Nach rechts“.
5. Voll ist die Leiste bei 8 Werkzeugen: eine neunte Pille wird abgelehnt, die Zeile oben im Raster sagt es. Nimm zuerst eines heraus.
6. Das **Häkchen** links beendet das Anpassen und schließt das Raster. Das Anpassen geht auch in Passwortfeldern (es liest und schreibt
   nichts im Feld); abgedunkelte Werkzeuge lassen sich ebenso verschieben und herausnehmen.
7. **Zurück zu deiner alten Reihenfolge:** Einstellungen → Leiste & Befehle → „Knöpfe wählen und sortieren“ → „Werkzeuge in der
   Leiste“ → „Standard“.

## Aussehen: flacher Look

Einstellungen → System & Daten → Karte „Profile“ → „+ Neues Profil“ (öffnet Tastaturprofile) →
„Flacher Look (Vorlage)“ → „Vorlage anwenden“ (oder auf der Karte „Flacher Look“ antippen und „Anwenden“). Das stellt
Stil, Farben, Icons, Zahlenreihe, Symbolhinweise und die Größen für das Hochformat in einem
Schritt ein. „Vorherigen Look wiederherstellen“ setzt genau diese Einstellungen auf den Stand
vor dem ersten Anwenden zurück; was du zwischen zwei Anwendungen von Hand geändert hast, wird
dabei nicht gesichert.

- Die Tastenhöhe wird aus der Bildschirmbreite berechnet (Tastatur ohne Leiste = 66 % der Breite).
  Feinjustieren: Einstellungen → Aussehen → Größe & Form → Tastaturhöhe.
- „DE • EN • ES“ auf der Leertaste erscheint, wenn du unter Tasten & Sprachen → „Sprache hinzufügen“ (Sprachen & Layouts) bei Deutsch
  „Mehrsprachiges Tippen“ mit Englisch und Spanisch einschaltest.
- Die Schrift ist die Systemschrift deines Telefons. Eine eigene Schriftdatei lässt sich unter
  Aussehen → „Benutzerdefinierte Schriftart aus Datei festlegen“ laden.
- Stil „Flach“ und Farben „Flach schwarz“ lassen sich unter Aussehen auch einzeln wählen.
- Leiste über den Tasten, von links: Raster-Knopf, Zwischenablage, Emoji, Aktionen (Stift,
  öffnet das Aktionspanel) und im Kreis das Diktat. Beim Tippen stehen an ihrer Stelle die
  Wortvorschläge. Welche Werkzeuge dort liegen und wie du sie anpasst, steht oben unter „Die Leiste wie Gboard“
  (Einstellungen → Leiste & Befehle → „Knöpfe wählen und sortieren“ oder halten und ziehen in der Tastatur).
- Raster-Knopf: öffnet das Raster aller Werkzeuge (siehe „Die Leiste wie Gboard“), darunter Design (öffnet die Einstellungen zum
  Aussehen), Rückgängig, Einhandmodus, Emoji, Inkognito und die frei verschiebbare Tastatur. Das X schließt das Raster. Das
  kleine Menü mit sechs Pillen und dem Stift-Knopf gibt es nur noch, wenn du „Feste Leiste wie Gboard“ ausgeschaltet hast.
- Die Zwischenablage-Taste fügt den aktuellen Inhalt der Zwischenablage an der Schreibmarke
  ein, solange der Zwischenablageverlauf aus ist (so ist er anfangs). Erst mit eingeschaltetem
  Verlauf (Einstellungen → Leiste & Befehle → Zwischenablage & Bilder → „Zwischenablage-Verlauf“) öffnet sie die
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
  „Zwischenablagen-Inhalt vorschlagen“ (Einstellungen → Tippen & Korrektur → Alle Korrektur-Einstellungen) an ist. Antippen fügt ein, lange
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
deinen Tipp und genau einmal. Tat ein Tipp nichts (Schreibmarke inzwischen verschoben, Gerät gerade
gesperrt, keine passende App), bleibt der Chip nutzbar; beim Einfügen sagt ein Hinweis „Gerade nicht
einfügbar“. Die Pille selbst und ein Tipp auf eine Karte fügen weiter den Clip so ein, wie er ist.

- Wo: in der Leiste hinter der Zwischenablage-Pille (nur wenn „Zwischenablagen-Inhalt vorschlagen“
  unter Einstellungen → Tippen & Korrektur → Alle Korrektur-Einstellungen an ist, in den ersten 3 Minuten nach dem Kopieren) und als
  kleine Chips unten auf den Karten der Zwischenablage-Ansicht (höchstens zwei je Karte).
- Adresse: „📍 In Karten öffnen“ öffnet deine Karten-App mit der Suche nach Straße und Hausnummer,
  PLZ und Ort; ein Name, „Lieferadresse:“, Telefon- und Mail-Zeilen kommen nicht mit. „💾 Als Ort
  speichern“ legt sie als Ort für `@ort` an, benannt nach Straße und Hausnummer (z. B. `@ort
  musterstraße 12`); umbenennen unter Einstellungen → Leiste & Befehle → „Alle Befehls-Einstellungen“
  → „Gespeicherte Orte“. Dieselbe Adresse wird nur einmal gespeichert, höchstens 10 Orte. Steht die Adresse mitten
  in einem Satz („Treffen am Marktplatz 5, 10115 Berlin“), gibt es nur „In Karten öffnen“.
- Telefonnummer: „📞 Anrufen“ öffnet die Telefon-App mit der Nummer; angerufen wird erst, wenn du
  dort auf Anrufen tippst. „👤 Kontakt anlegen“ öffnet einen neuen Kontakt in der Kontakte-App. Beide
  bekommen die Nummer in internationaler Form („+49 30 12345678“ → „+493012345678“). Nur für
  Nummern, die mit „+“, „00“ oder „0“ beginnen; eine bloße Ziffernfolge (Paket-, Bestell- oder
  Kundennummer) bekommt keinen Anruf-Chip, auch wenn der Verlauf sie „Telefon“ nennt.
- IBAN: „🏦 Formatiert einfügen“ fügt sie in Viererblöcken ein, nur wenn die Prüfziffer stimmt.
  Stimmt sie nicht, steht dort „⚠️ IBAN-Prüfziffer falsch“ und nichts wird eingefügt.
- Sendungsnummer: „📦 DHL: Sendung verfolgen“ (ebenso Deutsche Post, Hermes, DPD, GLS, UPS) öffnet
  die Seite des Paketdienstes im Browser; dabei geht die Nummer an den Dienst. Die Seiten sind fest
  in der App, aus dem Kopierten kommt nur die geprüfte Nummer. Eindeutige Nummern erkennt sie allein
  (UPS „1Z…“, Deutsche Post „…DE“, DHL „JJD…“ oder „00340434…“, Hermes „H…“); eine reine
  Ziffernfolge nur, wenn der Paketdienst im Text steht, z. B. „DPD Paketnummer 01234567890123“, und
  nie direkt hinter „Kundennummer“, „Bestellung“, „Auftrags-“ oder „Rechnungsnummer“. Ein
  GLS-Kürzel aus 8 Zeichen nur mit „Track-ID“ davor.
- Datum oder Uhrzeit: „📅 Termin: So 15.11. 18:00“ öffnet den Entwurf in deiner Kalender-App wie
  `@termin`; gespeichert wird erst dort. Erkannt werden z. B. „am 15.03.2027 um 14:30“, „morgen um
  18 Uhr“, „15. Dezember“, „tomorrow at 6pm“, „mañana a las 9“. Keinen Chip bekommen
  Versionsnummern („2.1“, „1.2.30“, „PHP 8.3.28“, alles direkt nach „Version“, „Update“, „Release“
  oder „Firmware“), Öffnungszeiten und Erreichbarkeit („Montag bis Freitag 9 bis 18 Uhr“, „Hotline
  ab 8 Uhr erreichbar“), Preise, „bis morgen“, „Guten Morgen“ und vergangene Daten. Eine Zahl mit
  zweistelligem Tag und Monat („10.11.30“) gilt weiter als Datum.
- E-Mail-Adresse: „✉️ Mail schreiben“ öffnet deine Mail-App mit der Adresse als Empfänger.
- Link mit Tracking-Anhängseln (`utm_…`, `fbclid`, `gclid`, `mc_eid`, `igshid`, bei YouTube und
  Spotify `si` …): „🧹 Ohne Tracking einfügen“ fügt den Link ohne sie ein, alles andere bleibt genau
  so. Auch für Text mit Links darin.
- Kopierte Nachricht mit Einmalcode: „🔢 Code 123456 einfügen“ fügt nur die Ziffern ein. Codes
  kommen nur aus der Zwischenablage, nie aus Benachrichtigungen.
- Zeigt die Leiste schon einen Feld-Vorschlag (z. B. den Code im Code-Feld), kommen Pille und Smart
  Paste nicht dazu.
- Nie für Geheimnisse und als vertraulich markierte Clips (im Verlauf auch nicht für alte
  angeheftete Einträge, die beim Kopieren vertraulich waren), nie in Passwortfeldern, im
  Inkognito-Modus, in Feldern ohne Lernen, in Zahlen- und Telefonfeldern oder bei gesperrtem Gerät.
  Codes und IBANs fügt die Tastatur als privaten Text ein (nie gelernt, nie in der Leiste gezeigt),
  einen bereinigten Link wie die Pille. Die Erkennung läuft lokal, die Tastatur selbst sendet
  nichts; nur „Sendung verfolgen“ öffnet eine Seite im Netz. Keine neue Berechtigung.
- Ausschalten: Einstellungen → Leiste & Befehle → Zwischenablage & Bilder → „Smart Paste“.

## Emoji-Vorschläge

Für Deutsch, Englisch und Spanisch ist das Emoji-Wörterbuch eingebaut; du musst nichts
herunterladen. „Emojis vorschlagen“, „Inline-Emoji-Suche“ (Emoji nach `:`, z. B. `:herz`) und die
Emoji-Beschreibungen sind in HeliBoard standardmäßig an und wirken damit sofort. Abschalten unter
Einstellungen → Tippen & Korrektur → Vorschläge → „Emoji-Vorschläge“ (die Inline-Emoji-Suche unter „Alle
Korrektur-Einstellungen“). Für andere Sprachen gilt weiter der Hinweis mit dem Download-Link.

Die Emojis in der Leiste sind tolerant: „Katzen“, „KATZE“, „Katz“ und „Kaze“ zeigen 🐈, „Kaese“
zeigt 🧀, „party“ beim deutschen Tippen 🎉, „geil“ 🔥. Wortanfänge und Tippfehler zählen erst ab
4 Buchstaben und nur, wenn das Getippte kein richtiges Wort ist („habe“ zeigt keinen Hasen).
Ein Wort, das ein Wörterbuch kennt, bekommt nur Emojis aus dessen Sprache („Tag“ zeigt beim
deutschen Tippen kein Preisschild, „love“ mit englischem Wörterbuch trotzdem ❤️). Wörter wie „und“, „die“, „ich“ oder „alles“ bekommen nie ein Emoji. Es stehen höchstens zwei Emojis in der
Leiste, rechts neben den Wortvorschlägen (das zweite oft erst unter dem Pfeil für weitere Vorschläge). Ein
Emoji kommt nur durch deinen Tipp ins Feld. Aus mit „Emojis vorschlagen“; in Passwortfeldern,
bei Inkognito und bei gesperrtem Gerät ohnehin nicht.

## Autokorrektur

Einstellungen → Tippen & Korrektur → Wörter & Lernen → Gelernte Wörter → Abschnitt „Autokorrektur“ (drei
der Schalter stehen auch direkt unter „Regeln“ auf der Seite Tippen & Korrektur):

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
  Einstellungen → Tippen & Korrektur → Wörter & Lernen → „Was ich mir gemerkt habe“ zeigt beides: „Übernehmen“
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
die es in dieser Sprache in anderer Groß-/Kleinschreibung gibt („hand“/„Hand“), zählen nie. Gemerkt
wird das Wort so, wie das Wörterbuch der anderen Sprache es schreibt („haus“ in spanischem Text
wird zu „Haus“). Die zweite Sprache der gerade benutzten Sprache („Mehrsprachiges Tippen“) zählt
nicht als andere Sprache, ihre Wörter kennt die Tastatur dort ohnehin. Wörter, die das Wörterbuch
als möglicherweise anstößig markiert, merkt sie sich nur, wenn „Anstößige Wörter sperren“ aus
ist. Ersetzt du ein gemerktes Wort durch ein anderes (anderen Vorschlag in der Leiste antippen oder
später im Feld ändern), vergisst sie es für diese Sprache wieder. Eine zurückgeholte Korrektur zählt
erst, wenn du danach weiterschreibst (nach der Löschtaste genügt ein Leerzeichen, nach dem Antippen
oder einer späteren Änderung im Feld braucht es ein weiteres Wort). Schickst du die Nachricht
direkt danach ab, merkt sich die Tastatur das Wort diesmal nicht. Nichts davon in Inkognito,
Passwortfeldern, „Meine Daten“, bei Lernpause oder gesperrtem Gerät.
„Was ich mir gemerkt habe“ → „Wörter aus anderen Sprachen“ zeigt die Wörter mit „Löschen“;
„Gelernte Wörter löschen“ und das Ausschalten von „Personalisierte Vorschläge“ löschen alle. Unter
„Korrekturen“ steht bei jeder Korrektur die Sprache, in der sie gilt (ältere ohne Sprache gelten
überall, wo das Wörterbuch das gemeinte Wort kennt).

Getipptes Wort zurückholen: Drückst du direkt nach einer Korrektur die Löschtaste oder tippst du
später in das korrigierte Wort, steht dein getipptes Wort in der Leiste ganz links (in deiner
Groß-/Kleinschreibung), daneben ähnliche Wörter. Ein Tipp darauf setzt es ein; mit „Rückgängig
gemachte Korrekturen merken“ gilt das wie die Löschtaste. Ein richtig geschriebenes Wort ändert die
Autokorrektur nie nur in der Großschreibung („essen“ bleibt, „Essen“ steht als Vorschlag da).

## Trefferzonen

Einstellungen → Tippen & Korrektur → Wörter & Lernen → Gelernte Wörter → Abschnitt „Trefferzonen“
(Schalter und Kalibrieren auch unter Gesten & Gefühl → „Treffen & Wischen“):

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

Lernen aus dem Tippen ist anfangs aus. Einschalten: Einstellungen → Tippen & Korrektur →
Alle Korrektur-Einstellungen → „Personalisierte Vorschläge“. Unter Einstellungen → Tippen & Korrektur →
Wörter & Lernen → Gelernte Wörter kannst du das Lernen
pausieren (das Gelernte bleibt), die gelernten Wörter je Sprache zählen, als Datei exportieren
oder löschen. Wer „Personalisierte Vorschläge“ ausschaltet, löscht dabei alles Gelernte.

## Begleit-App „Spike“ (Test des KI-Chips, nur als Datei)

Eine kleine, getrennte App, die nur ausprobiert, ob Qualcomms fertiges Sprachmodell (Qwen3-4B) auf dem KI-Chip
deines Xiaomi 15 läuft und wie schnell. Sie ist keine Tastaturfunktion, steht nicht bei „Releases“ und wird dir
vom Lead als Datei geschickt (`app-debug.apk`, etwa 97 MB; Größe und SHA-256 stehen in der Nachricht).

1. Datei auf dem Telefon antippen und installieren (wie bei der Tastatur: Android fragt einmal nach der Erlaubnis
   für unbekannte Apps). Die App heißt „SecondHand Begleit-Spike“ und ersetzt nichts.
2. **WLAN** einschalten und etwa **7 GB freien Speicher** sicherstellen. App öffnen. Oben stehen Gerät, Chip und Speicher.
   Steht dort in Rot „Auf diesem Gerät nicht unterstützt …“, tippe nur **Bericht kopieren** (Schritt 6).
3. **1. Modell laden** tippen und die Frage mit „Jetzt laden“ bestätigen (etwa 2,7 GB von Qualcomms Server; die App
   bleibt dabei im Vordergrund, der Bildschirm bleibt an; der Dialog nennt auch, ob du im WLAN bist). Die App kennt zwei
   Pakete in fester Reihenfolge (Liste auf dem Bildschirm): zuerst Qwen3-4B-Instruct-2507, dann Qwen3-4B, beide in der
   Fassung „SM8750-AC“, die für die Galaxy-Variante des Chips gebaut ist und auf diesem Telefon vielleicht nicht läuft.
   Scheitert ein Download, zeigt die App den Fehler und bietet das **nächste Paket** (mit Name, Größe, Unterschied) oder
   „Dasselbe nochmal“ an; geladen wird immer erst nach deinem Tipp. Ein Abbruch durch dich ist kein Fehler: Knopf 1 setzt
   den Download fort. **Modell / angefangenen Download löschen** brauchst du nur, wenn ein Download „unvollständig oder
   beschädigt“ meldet, Knopf 1 nach einem Abbruch immer wieder scheitert, ein Paket sich nicht laden lässt (dann wird danach
   das nächste angeboten) oder um Speicher freizugeben. **Paketliste bei Qualcomm abfragen** holt nur eine kleine Liste
   (kein Modell) und schreibt sie in den Bericht.
4. **2. Test: festen Satz korrigieren** tippen. Die App lädt das Modell auf den KI-Chip (das kann eine Weile dauern),
   lässt es einen festen Satz mit zwei Tippfehlern korrigieren und zeigt Antwort, Ladezeit, Tempo, Speicher und Akku-
   Temperatur. Optional danach **3. Test: längerer Absatz** (zeigt das Tempo über längere Zeit).
   **Wenn Test 2 beim Laden scheitert (Fassung 0.4):** Die App sammelt dabei von selbst Befunde in den Bericht (Dateien des Pakets, Versionen, Systembibliotheken, Speicher, das Protokoll der Laufzeit). Liegt es am neuen
   Metadaten-Format des Pakets, fragt sie, was sie tun soll: **Metadaten anpassen und neu testen** (kein Download; die App schreibt die Beschreibung im alten Format und sichert das Original; es kann trotzdem scheitern) oder
   **Ältere Ausgabe laden** (etwa 2,7 GB, lädt die Ausgabe v0.66.0 selbst herunter, prüft sie und übernimmt sie; das vorhandene Paket bleibt, bis du es löschst). Beides nur nach deiner Bestätigung. Danach Test 2 noch einmal
   und **Bericht kopieren**. Für den Download brauchst du etwa 8 GB freien Speicher und am besten WLAN; ein Abbruch ist harmlos, Knopf 1 setzt fort.
   **Speicher (Fassung 0.5):** Das große Modell (4B) hat dein Telefon beim Laden in Speichermangel gebracht (App und Launcher wurden beendet). Darum prüft die App vor jedem Laden, ob genug Speicher frei ist, und lädt sonst
   nicht (mit Hinweis: andere Apps schließen oder das Telefon neu starten). **Trotzdem laden** gibt es nur hinter einer zweiten Frage, denn dabei kann der Bildschirm neu laden. Fällt der freie Speicher beim Laden zu tief, beendet sich die App selbst, um das Telefon zu schützen; beim
   nächsten Öffnen steht die letzte Messung im Bericht. **Knopf 1 lädt jetzt zuerst das kleine Modell Qwen3-1.7B (etwa 1,5 GB)**, damit sich alles bis zum Chip beweisen lässt; das 4B-Paket bleibt auf dem Telefon. Mit **Test-Modell wählen** kannst du zwischen den vorhandenen Paketen wechseln (die App zeigt für jedes, ob der Speicher reicht).
   Tipp für das 4B: Telefon neu starten, keine anderen Apps öffnen, dann erst testen.
   **Fassung 0.6 (Messversuch):** HyperOS beendet Apps, die mehr als 1,5 GB Chip-Speicher (dma-buf) belegen; das 1,7B-Modell belegte 3,8 GB. Darum lädt die App kein Modell mehr einfach so, sondern sperrt es mit einer Erklärung. Du kannst **Messversuch** tippen (zweimal bestätigen): Die App lädt das 1,7B-Modell und **beendet sich dabei voraussichtlich selbst**, sobald ihr Chip-Speicher
   1,3 GB erreicht, bevor HyperOS eingreift. Das ist die Messung. **Öffne die App danach wieder:** Unter der Überschrift steht „Bericht des vorherigen Laufs anzeigen“ (aufklappbar). Berichte bleiben jetzt als Datei erhalten (die letzten drei Läufe); **Bericht kopieren** nimmt den vorherigen und den aktuellen Lauf mit. Kein neuer Download nötig.
5. Hängt etwas, hilft **Stopp**. **Modell entladen** gibt den Arbeitsspeicher frei, **Modell / angefangenen Download löschen** die Modelle
   vom Telefon. Verschwindet die App mitten im Test, öffne sie wieder: Der Bericht sagt dann, in welchem Schritt es passierte.
6. **Bericht kopieren** tippen, dann in den Chat mit dem Lead einfügen (oder einen Screenshot des Berichts schicken).
   Der Bericht enthält Gerätedaten, Zahlen und Fehlertexte, keine privaten Texte.

Datenschutz: Die App nutzt das Internet nur für den Download aus Schritt 3, nur nach deiner Bestätigung. Es gibt kein
Textfeld, nur zwei feste Beispieltexte, keine Statistik und keine Anmeldung. Stand: nichts davon lief bisher auf einem Gerät.

## Gerätetests ansehen (Video und Ergebnis)

Die Tastatur wird auf GitHub in einem Android-Emulator automatisch bedient (bisher sechs einfache
Tipp-Szenarien). So siehst du das Ergebnis, im Browser, angemeldet bei GitHub:

1. `github.com/Tra1c0/SecondHand` öffnen → oben **Actions**.
2. Links **SecondHand Device Tests** wählen. Jede Zeile ist ein Lauf: grüner Haken = alle Szenarien
   bestanden, rotes Kreuz = mindestens eines nicht (oder der Emulator kam nicht hoch).
3. Auf einen Lauf tippen. Unter **Summary** steht die Tabelle „Device tests“: je Szenario PASS oder FAIL
   und was erwartet und was im Feld gefunden wurde.
4. Ganz unten auf derselben Seite unter **Artifacts** liegt `device-test-1` (bei drei Emulatoren auch
   `-2`, `-3`). Antippen lädt eine ZIP-Datei: darin `video-01.mp4` (die Aufnahme des ganzen Laufs; oben im
   Bild steht der Name des laufenden Szenarios) und je Szenario ein Screenshot. Die Artefakte bleiben
   14 Tage.

Selbst starten: Actions → SecondHand Device Tests → **Run workflow** → Branch wählen → „Run workflow“
(den Knopf zeigt GitHub vermutlich erst, wenn der Workflow auf `main` liegt; bis dahin startet ihn der Lead).
„repeats“ startet 1, 3 oder 5 frische Emulatoren nebeneinander (um wacklige Tests zu finden). „control“
lässt absichtlich das letzte Zeichen weg und zeigt nur, dass die Tests Fehler bemerken: Er ist grün, wenn
**jedes** Szenario in der Tabelle FAIL zeigt (Überschrift „CONTROL RUN“). Ein Lauf dauert etwa 10 Minuten. Scheitert er schon im Job „Build the APKs“, hat GitHub
beim Laden von Bibliotheken gehakt: oben rechts „Re-run failed jobs“.

Grün heißt nur: diese Szenarien, in diesem Emulator. Tippgefühl, Tempo, Vibration und echte Apps prüft das
nicht.

## Wenn etwas nicht klappt

Einstellungen → „Diagnose“ (unten auf der Startseite, oder der Knopf „Diagnose“ auf der Karte UPDATES in
System & Daten) zeigt, was dein Telefon wirklich macht, ohne Texte,
Schlüssel oder Kennungen: welche Haptik die Rastpunkte der Leertaste spielen, der leichte Tick und der letzte Punkt (Knopf
„Test“ spielt den letzten Punkt),
ob Symbole malen laufen kann, warum die Sprache zuletzt gewechselt hat (z. B. „Sprach-Hinweis der
App → en-US“; nur seit dem Start der Tastatur, nichts wird gespeichert), den Stand von Emoji-Mix,
was die RGB-Beleuchtung in den letzten 5 Sekunden mit Licht je Bild gekostet hat (Tastatur mit
RGB öffnen, kurz tippen, dann die Seite öffnen), welche Muster gerade benutzt werden (eigene mit
Namen und „eigen“) und was ein Licht-Raster jedes Musters auf deinem Telefon kostet (beim Öffnen
gemessen), die Autofill-Vorschläge des Passwortmanagers (nur Zahlen: wie viele Antworten gekommen, leer oder abgelehnt waren, wie viele Chips der letzten Antwort ankamen, leer blieben, verworfen wurden oder noch fehlten, Zeit bis zum ersten Chip, ob der Kasten gezeigt wurde) und die Versionen. „gstatic erreichbar?“ lädt nur bei diesem Tipp einmal ein Testbild von Google.
„Kopieren“ legt genau die angezeigten Werte in die Zwischenablage, zum Weitergeben; ein
Screenshot geht auch.

**Ruckelt das Tippen?** Tippe eine Weile in einem Feld (am besten dort, wo es ruckelt), öffne dann
Einstellungen → Diagnose und schick einen Screenshot der Karte „Tippen“ (vor „App und Telefon“). Sie
zeigt, wie lange die Zusatzfunktionen der Tastatur je Taste brauchen (Median, 95. Perzentil, Maximum;
unter 16 ms ist gut, das ist ein Bild bei 60 Hz), wie oft sie dabei die App und Android fragen und
welche zehn Stellen am langsamsten waren. Es sind nur Zahlen, nie Text, und sie bleiben nur im
Speicher, bis die Tastatur beendet wird. „Zahlen zurücksetzen“ fängt neu an; „Kopieren“ nimmt die Karte
mit.

**Kommen die Passwortmanager-Vorschläge nicht?** Öffne das Feld, warte kurz (das Feld mit dem Kreuz erscheint erst, wenn der erste Chip da ist), öffne dann Einstellungen → Diagnose und schick einen Screenshot der Karte „Autofill-Vorschläge“. „0 angekommen, 3 angefragt“ heißt: Die Tastatur hat Vorschläge bekommen, das System hat die Chips aber nicht geliefert; „3 angekommen“ bei nicht sichtbarem Kasten wäre ein Fehler der Tastatur. Es sind nur Zahlen, nie Titel oder Texte.

**Keine Vorschläge, keine Großschreibung oder keine Autokorrektur?** Lass die App, in der es
passiert, offen, wechsle direkt zu Einstellungen → Diagnose (ohne vorher in einer anderen App in ein
Textfeld zu tippen; die Suche der Einstellungen zählt nicht) und mach einen Screenshot der ersten
Karte „Letztes Feld“. Ganz oben steht das Urteil in einem Satz, z. B. „Keine Autokorrektur: Die App
verlangt sie in diesem Feld nicht, und „Mehr Autokorrektur“ ist aus.“ oder „Keine Vorschläge: Die
Tastatur schaltet sie in Webfeldern von Firefox selbst ab …“, darunter das Ergebnis (Vorschläge,
Autokorrektur, Großschreibung an oder aus, und ob zuletzt etwas die Leiste verdeckte). Danach folgen
für das letzte Feld, in dem die Tastatur offen war: die App, der Feldtyp (z. B. „Suche“, „Adresse
(URL)“) und seine Zahlen, was die App erlaubt (Vorschläge, Großschreibung, Autokorrektur), die
Sprache und ob ihr Wörterbuch geladen ist, was die Tastatur selbst abgeschaltet hat (auch „Immer
Vorschläge anzeigen“) und was die Leiste zuletzt zeigte (Wörter, Symbolleiste, Emoji-Reihe, Chips
oder eine Pille). Schick den Screenshot (oder „Kopieren“) an den Lead. Feldtexte stehen dort nie.
In einem
Passwortfeld steht nur „Passwortfeld“ und bei gesperrtem Telefon nur „Gesperrt: nichts gemerkt“. In
Feldern, die das Lernen verbieten (z. B. private Chats oder Tabs), und im Inkognito-Modus zeigt die
Karte Feldtyp, Merkmale und Urteil, aber nicht die App („App: nicht gemerkt (Feld ohne Lernen)“); das
Urteil sagt dann dazu, dass das Feld kein Lernen verlangt. Die Karte lebt nur im Speicher; nach einem
Neustart der Tastatur steht dort „noch keins“.

**Korrektur in einer App erzwingen.** Manche Apps (z. B. Instagram) melden ihr Nachrichtenfeld mit
„keine Vorschläge“ und ohne Großschreibung. Für Apps in einer Liste behandelt die Tastatur solche
Textfelder wie ein normales Nachrichtenfeld: Vorschläge, Autokorrektur auch im einzeiligen Feld und
Satzanfänge groß. Nicht in Passwort-, E-Mail-, Adress-, Namen- und Suchfeldern, nicht in einzeiligen Feldern mit den Aktionen „Los“ oder „Weiter“
(Anmelde- und Formularfelder) und nicht in Feldern mit ASCII-Zwang. Steht ein Feld einer gelisteten App nicht in der Liste der
erzwungenen Felder, nennt die Karte den Grund (Zeile „Korrektur erzwungen“). Deine eigenen Schalter
(„Änderungsvorschläge“, „Autokorrektur“, automatische Großschreibung) gelten weiter, und Lernen wird
dadurch nie eingeschaltet. Instagram steht anfangs in der Liste. So änderst du sie:

- Auf der Karte „Letztes Feld“ (Einstellungen → Diagnose) steht unter den Werten der Knopf „In dieser
  App erzwingen“ oder „Nicht mehr erzwingen“, sobald die Karte die App kennt (nicht in Feldern ohne
  Lernen). Er speichert sofort und sagt „Gilt ab dem nächsten Feld.“
- Einstellungen → Tippen & Korrektur → Regeln → „Korrektur in Apps erzwingen“: die Liste der Apps
  (Name, wenn Android ihn verrät, sonst der Paketname), „Entfernen“ und „App hinzufügen“ aus den
  installierten Apps.

Die Karte sagt auch, was erzwungen wurde, z. B. „Die App verbietet Vorschläge; für diese App
erzwungen.“ Korrigiert die Tastatur in Instagram trotzdem nicht, tippe im Chat ein paar Wörter mit
einem Tippfehler, öffne dann direkt Einstellungen → Diagnose und schick dem Lead einen Screenshot der
Karte „Letztes Feld“ (sie zeigt auch in privaten Chats Feldtyp, Merkmale und Urteil, nur nicht Paketname und App-Name; sie
zeigt aber „Korrektur erzwungen: ja“, wenn erzwungen wurde). Neu: Steht dort „App nahm getippten Text nicht unverändert an: n-mal“
und im Urteil, dass der Text stehen bleibt, es aber für das Wort keine Vorschläge und keine Autokorrektur gibt, ändert die App den
Text, den die Tastatur beim Tippen setzt (z. B. nur Großbuchstaben), oder übernimmt ihn verspätet; dann liegt es an der App, nicht an
der Liste. Nimmt eine App diesen Text gar nicht an, können beim Erzwingen Buchstaben fehlen (so eine App ist nicht bekannt, auf einem
Telefon ist das nicht geprüft). Verhält sich das Tippen in einer App seltsam oder fehlen Buchstaben, nimm sie unter Einstellungen →
Tippen & Korrektur → Regeln → „Korrektur in Apps erzwingen“ aus der Liste und schick den Screenshot der Karte. „nicht gefunden“ in der Liste heißt: Die App sieht dieses Paket
nicht (nicht installiert oder für sie unsichtbar). Ist die Liste voll (50 Apps), sagt der Knopf „Liste voll“ und ändert nichts.

Schreib dazu auf, was hilft, den Fehler zu finden:

- Telefon und Android-Version,
- in welcher App und in welchem Feld,
- was du getan hast, was du erwartet hast und was stattdessen passiert ist,
- bei KI-Aktionen die angezeigte Meldung (keinen Schlüssel und keinen privaten Text mitschicken).

Bekannte Lücken stehen in [HANDOFF.md](HANDOFF.md) unter „Offene Punkte“.

## Entfernen

Android-Einstellungen → Apps → „SecondHand“ → Deinstallieren. Dabei werden Schlüssel,
Workflows, Profile und gelernte Wörter gelöscht. Vorher wechselt Android automatisch zu einer
anderen Tastatur.
