# Agent Keyboard – Funktionen

Jede Funktion mit kurzer Erklärung, für den Owner geschrieben. Wird im selben Push wie jede
Änderung aktualisiert (siehe `CLAUDE.md`). Technische Details: [ARCHITECTURE.md](ARCHITECTURE.md),
Prüfnachweise je Etappe: [VALIDATION.md](VALIDATION.md). Die Liste beschreibt den Stand des
zuletzt gepushten Branches. In Klammern steht die Etappe, in der die Funktion entstand.

Status: **E** = auf einem Gerät oder Emulator von Anfang bis Ende geprüft · **T** = durch
automatische Tests abgedeckt (Robolectric simuliert Android, das ist kein Gerätetest) ·
**–** = noch nicht geprüft. Nutzung auf einem echten Gerät zählt nur, wenn der Owner es sagt.
E stammt bisher nur aus dem Emulator-Test vom 2026-11-11 (Android 15, Protokoll in VALIDATION.md → G1); auf einem echten Telefon ist noch nichts geprüft.

## Tastatur

| Funktion | Was sie macht | Status |
| --- | --- | --- |
| HeliBoard-Tastatur | Tippen, Layouts, Wörterbücher, Autokorrektur, Vorschläge, Themes und Emoji wie in HeliBoard 4.2. Die Upstream-Tests laufen bis auf fünf geerbte Fehler durch. (0) Im Emulator geprüft: Tippen, Löschen, Großschreibung, Vorschläge. | E |
| Eigene App | Heißt „Agent Keyboard“ (Debug: „Agent Keyboard Lab“), eigene App-ID, damit sie neben HeliBoard installiert werden kann. (1) | E |
| Sichere Voreinstellungen | Lernen aus deinem Tippen, Zwischenablage-Vorschläge und Zwischenablageverlauf sind anfangs aus. Keine automatischen Backups. (1) | T |
| Flacher Look (Vorlage) | Ein Tipp unter Einstellungen → Tastaturprofile stellt das Aussehen nach deinen Referenz-Screenshots ein: schwarzer Hintergrund, flache graue Tasten mit 6-dp-Ecken, Enter und „?123“ als Pille, Zahlenreihe, Symbolhinweise, eigene Icons, Sprachkürzel mit Punkten auf der Leertaste. Ein zweiter Knopf stellt den vorherigen Look wieder her. Tastenpositionen stimmen im gerenderten Bild bis auf 1–2 Pixel mit dem Screenshot überein; die Schrift ist die Systemschrift des Telefons, nicht Google Sans. Auf einem Gerät noch nicht geprüft. (19a) | T |
| Leiste im flachen Look | Über den Tasten stehen fünf gleichmäßig verteilte Knöpfe: Menü (Raster), Zwischenablage, Emoji, Aktionen (Stift) und rechts im Kreis das Diktat. Solange nichts vorgeschlagen wird, siehst du die Werkzeuge; beim Tippen erscheinen an ihrer Stelle die Wortvorschläge, Menü und Diktat bleiben stehen. Statt der Sticker der Vorlage sitzt dort die Emoji-Taste. (19b) | T |
| Symbolseiten wie Gboard | Mit der Vorlage „Flacher Look“: Seite 1 zeigt Zahlenreihe, `@ # € _ & - + ( ) /` und `* " ' : ; ! ?`; Seite 2 (über `=\<`) statt der Zahlen `~ ` | • √ π ÷ × § Δ`, dann `£ ¥ $ ¢ ^ ° = { } \` und `% © ® ™ ✓ [ ]`, unten `<` und `>`. Auf beiden Seiten die helle Taste „1234“ für den Ziffernblock. Die kleinen Symbol-Hinweise auf den Buchstaben bleiben wie bisher. (23a) | T |
| Emojis auf den Symbolseiten | Im flachen Look zeigt die Leiste auf beiden Symbolseiten statt der Werkzeuge sechs zuletzt benutzte Emojis (aufgefüllt mit 👍 😂 ❤️ 🙏 😊 🔥). Ein Tipp fügt das Emoji ein und rückt es nach vorn; Menü- und Diktat-Knopf bleiben. Zurück auf den Buchstaben kommen die Werkzeuge wieder. (23b) | T |
| Tastaturmenü | Das Raster-Symbol öffnet über den Tasten ein Menü mit sechs Pillen: Design (öffnet die Einstellungen zum Aussehen), Rückgängig, Einhändig, Emoji, Inkognito, Unverankert; der Stift-Knopf unten rechts öffnet die Einstellungen der Leiste. Das Symbol wird zum X und schließt das Menü wieder. Ohne Animation. GIF und „Tastatur teilen“ aus der Vorlage gibt es nicht. (19c) | T |
| Laufschrift im Menü | Passt der Text einer Menü-Pille nicht hinein, läuft er wie ein Nachrichtenticker durch. Hast du in den Android-Einstellungen Animationen ausgeschaltet, wird er stattdessen mit „…“ abgeschnitten. (23c) Das Laufen selbst ist nur im Test eingeschaltet, nicht auf einem Gerät gesehen. | T |
| Screenshot-Pille | Nach einem Screenshot erscheint er drei Minuten lang als Pille mit Vorschaubild in der Leiste; nur in Feldern, die Bilder annehmen; ein Tipp fügt das Bild ein, langes Drücken oder das Kreuz blendet sie aus. Aus, bis du sie unter Einstellungen → Screenshots einschaltest und den Zugriff auf Bilder erlaubst. Nie in Passwort- oder Zahlenfeldern, im Inkognito-Modus oder bei gesperrtem Gerät. (23d) Auf keinem Gerät ausprobiert. | T |
| Zwischenablage im flachen Look | Die Zwischenablage-Ansicht hat eine Kopfzeile (runder Zurück-Knopf, Titel, runde Knöpfe für „Verlauf leeren“ und Einstellungen), darunter „Letzte“ und zwei Spalten Karten mit runden Ecken bis zum unteren Rand; Bilder füllen quadratische Karten. Kopierte Bilder erscheinen in der Leiste als dunkle Pille mit rundem Vorschaubild; langes Drücken blendet sie aus. Der Verlauf bleibt anfangs aus, du schaltest ihn selbst ein. (19d) | T |
| Flüssigere Zwischenablage | In allen Stilen: Bilder werden einmal in Anzeigegröße geladen und im Speicher behalten, statt bei jedem Scrollen und jeder Aktualisierung der Leiste in voller Auflösung. Vorher lud ein 1200 px breites Bildschirmfoto wegen eines Rechenfehlers immer ungekürzt. (19d) | T |
| Aktionspanel im flachen Look | Das Panel bekommt eine Kopfzeile mit rundem Zurück-Knopf, das Ergebnis steht in einer Karte mit farbigem Rand direkt unter dem markierten Text, alle Aktionen sind Chips. Während eine KI-Anfrage läuft, zeigt die Karte vier langsam pulsierende Balken. Die Stift-Taste der Leiste öffnet das Panel direkt. Ablauf und Freigaben sind unverändert: nichts wird ohne eigenen Klick gesendet oder übernommen. (19e) | T |
| Autokorrektur behält Umlaut-Wörter | Ein richtiges Wort wird nicht mehr automatisch durch eines ersetzt, das sich nur im Umlaut unterscheidet (mochte/möchte, schon/schön, wurde/würde); das andere Wort steht weiter als Vorschlag da. Spanische und französische Akzente (esta/está) korrigiert die Tastatur weiter. Optional: richtige Wörter nie automatisch ändern. Einstellungen → Gelernte Wörter → Autokorrektur. (20a) | T |
| Rückgängig gemachte Korrekturen merken | Optional, anfangs aus: Drückst du direkt nach einer automatischen Korrektur die Löschtaste, wendet die Tastatur genau dieses Paar nie wieder automatisch an. Nur auf dem Gerät, höchstens 500 Paare, nicht im Inkognito-Modus, bei pausiertem Lernen oder gesperrtem Gerät; Anzahl ansehen und alles vergessen in den Einstellungen. (20a) | T |
| Eigene Tippfehler lernen | Optional, anfangs aus: Wählst du nach dem Tippen ein anderes Wort aus der Vorschlagsleiste, zählt die Tastatur das Paar (z. B. „nciht“ → „nicht“). Nach drei gleichen Auswahlen korrigiert sie es automatisch, auch zu Wörtern, die kein Wörterbuch kennt. Ein getipptes Wort, das es selbst gibt, korrigiert sie nur nach demselben vorherigen Wort (z. B. „ich mochte“ → „ich möchte“). Rückgängig mit der Löschtaste vergisst das Paar. Nur auf dem Gerät, nicht im Inkognito-Modus oder bei pausiertem Lernen. (20b) | T |
| Persönliche Trefferzonen | Optional, anfangs aus: Unter Einstellungen → Gelernte Wörter → Trefferzonen tippst du einen vorgegebenen Text ab. Die Tastatur misst, wohin du jede Buchstabentaste wirklich triffst (z. B. „E: 12 % zu tief“), und verschiebt nach „Übernehmen“ die unsichtbare Trefferfläche ein Stück dorthin, höchstens ein Drittel Taste. Die sichtbaren Tasten bleiben stehen; ohne gelernte Werte erkennt die Tastatur jede Berührung genau wie vorher. Danach lernt sie beim normalen Tippen weiter, aus getippten (nicht gewischten) Wörtern, auch aus Vertippern, die du selbst über die Leiste korrigierst; nicht aus automatischen Korrekturen, die du noch rückgängig machen könntest (22b). Gespeichert werden nur Durchschnittswerte pro Taste. Die Autokorrektur rechnet noch mit den normalen Tastenpositionen. (22a, 22b) | T |
| Normales Tippen offline | Die Internetverbindung wird erst nach einer ausdrücklichen Sendefreigabe aufgebaut, Tippen und lokale Werkzeuge brauchen sie nicht. Getestet ist der späte Aufbau; eine Messung auf dem Gerät fehlt. (9c) | T |

## Aktionen im Textfeld

| Funktion | Was sie macht | Status |
| --- | --- | --- |
| Aktionspanel | Toolbar-Taste öffnet ein Panel über der Tastatur: markierten Text ansehen, Aktion wählen, Ergebnis prüfen, dann bewusst übernehmen oder kopieren. (5) | E |
| Aktionsrad | Rundes Menü mit denselben Aktionen, auch mit TalkBack bedienbar. (6) TalkBack nicht geprüft. | E |
| Leertasten-Langdruck | Optional: Langes Drücken der Leertaste öffnet das Rad. Standardmäßig aus, einschaltbar im Gestenprofil. (7) | T |
| Lokale Werkzeuge | 3 lokale Textwerkzeuge: Großschreibung, Kleinschreibung, Leerzeichen aufräumen. Laufen ohne Internet und sind als lokal gekennzeichnet. (5) Im Emulator geprüft: Großschreibung. | E |
| Schutz beim Ersetzen | Ersetzt wird nur, wenn sich Feld, Auswahl und Text seit der Vorschau nicht geändert haben. Sonst bleibt die Übernahme gesperrt. (4) | E |
| Schutzbereiche | Passwortfelder, Inkognito, gesperrtes Gerät und Felder mit Lernverbot sperren alle Aktionen. Im Emulator geprüft: Passwortfeld. (4) | T |

## KI (eigene API-Schlüssel)

| Funktion | Was sie macht | Status |
| --- | --- | --- |
| Schlüssel speichern | Je Anbieter ein eigener Schlüssel, verschlüsselt auf dem Gerät gespeichert, nie angezeigt, nicht gesichert. (8, 16) Im Emulator mit dem echten Android-Keystore geprüft (OpenAI-Platz). | E |
| Anbieter und Modell wählen | In den KI-Einstellungen wählst du OpenAI, Anthropic oder Google Gemini für die KI-Aktionen der Tastatur und je Anbieter die Modellkennung. Für Gemini musst du die Modellkennung selbst eintragen. Workflow-Schritte können je Schritt einen anderen Anbieter nennen. (9, 16) | T |
| KI-Textaktionen | 8 KI-Textaktionen: Korrigieren, Übersetzen, Umformulieren (Ton wählbar), Zusammenfassen, Antwort entwerfen, Analysieren (nur lesen, ersetzt nie), dazu die freie Frage, die es nur über `@ki` gibt, und die Übersetzung mit eigenem Stil, die es nur über `@translate` gibt (beide nicht im Panel, nicht als Skill oder Workflow-Schritt). Vor dem Senden siehst du genau, was rausgeht, und bestätigst einmalig. (9–10) | T |
| Übersetzen | 14 Zielsprachen, darunter Deutsch, Englisch und Spanisch. (10a) | T |
| Echte Anfrage an einen Anbieter | Mit echtem Schlüssel noch bei keinem der drei Anbieter ausprobiert. | – |

## Skill Keys und Workflows

| Funktion | Was sie macht | Status |
| --- | --- | --- |
| Skill Keys | Drei Toolbar-Tasten, frei mit einer lokalen Aktion, einer KI-Aktion oder einem gespeicherten Workflow belegbar. Fehlt der Workflow, gibt es keinen stillen Ersatz. (11, 13d) | T |
| Workflows | Eigene Abläufe aus bis zu acht Schritten mit höchstens drei KI-Aufrufen. Ein Schritt kann die Auswahl oder ein früheres Zwischenergebnis verwenden. (13) | T |
| Bedingungen | Ein Schritt läuft nur, wenn der Text nicht leer ist, ein bestimmtes Wort enthält oder lang genug ist (auch umkehrbar). Sonst wird der Text unverändert weitergereicht. (13c) | T |
| Import und Export | Workflows, Skill-Belegungen und Tastaturprofile als Datei austauschen. Ein Import führt nichts aus. (11c, 13b) | T |
| Laufstatus | Die Einstellungen zeigen, welche Läufe erfolgreich, abgebrochen oder unklar endeten (ohne Texte). Löschbar. (12, 13f) | T |
| Reparatur | Eine beschädigte Workflow-Datei lässt sich nach Bestätigung zurücksetzen; Schlüssel, Profile und Skill-Belegungen bleiben. (13g) | T |

## Werkzeuge

| Funktion | Was sie macht | Status |
| --- | --- | --- |
| Werkzeuge im Panel | 2 Werkzeuge im Aktionspanel unter „Werkzeuge“. Vor jedem Lauf siehst du Ziel und alle Werte und bestätigst einmal. (15) | T |
| Testwerkzeug | Zählt Zeichen, Wörter und Zeilen des markierten Texts. Läuft nur auf dem Gerät, ersetzt nichts. Dient zum Ausprobieren des Werkzeug-Ablaufs. (15a) | E |
| Kalenderentwurf | Öffnet die Kalender-App mit einem neuen Termin: erste Zeile als Titel, ganzer Text als Beschreibung. Datum und Uhrzeit stellst du dort ein. Gespeichert ist der Termin erst, wenn du ihn dort speicherst; die Tastatur liest den Kalender nicht. Im Emulator startet die Übergabe; ob die Daten in einer Kalender-App ankommen, ist offen (die App des Emulators war defekt). (15b) | T |

## Befehle

| Funktion | Was sie macht | Status |
| --- | --- | --- |
| Getippte Befehle | Tippst du am Wortanfang `@` und einen Befehlsnamen, zeigt die Leiste Chips statt Wortvorschlägen. Ein einzelnes `@` listet alle Befehle, ein angefangener Name die passenden; ein Tipp vervollständigt den Namen. Erst ein Tipp auf einen Chip löscht den Befehlstext und führt etwas aus. E-Mail-Adressen und Erwähnungen (`a@b.de`, `@lewin`) lösen nichts aus. Befehlswörter werden weder autokorrigiert noch gelernt, die Wörter danach auf derselben Zeile auch nicht autokorrigiert. Aus in Passwortfeldern, im Inkognito-Modus und bei gesperrtem Gerät. Präfix (`@ # $ % ~ §`) und jeder Befehl einstellbar unter Einstellungen → Befehle. (24a) Auf keinem Gerät ausprobiert. | T |
| `@math` | `@math 12*3,5` zeigt den Chip „12 × 3,5 = 42“; ein Tipp ersetzt den Befehl durch die Rechnung. Der Chip „Rechner“ öffnet statt der Tasten einen Rechner mit Live-Ergebnis, ohne den Text zu ändern; „Einfügen“ ersetzt den Befehl durch das Ergebnis. Rechnet exakt mit Dezimalzahlen (0,1 + 0,2 = 0,3), kann `+ − × ÷ ^ √ ∛ % !`, Klammern, `²`, `³`, `π`, `e`, Funktionen (`sin cos tan asin acos atan` in Grad, `ln log exp abs`, `sqrt`/`wurzel`, `root(x; n)`) und Malnehmen ohne Zeichen (`2π`); im Rechner über die Taste „fx“; gerundete Ergebnisse (1 ÷ 3, √2) stehen mit „≈“. Vier Ausgaben, die sich die Tastatur merkt: Rechnung, nur Ergebnis, LaTeX, Hochzahlen und Brüche (`2¹⁰`, `½`). Komma oder Punkt nach der Tastatursprache. Nur auf dem Gerät. (24b) Auf keinem Gerät ausprobiert. | T |
| `@contact` | `@contact Ann` zeigt bis zu 6 passende Kontakte aus dem Adressbuch des Telefons (Google-Kontakte inbegriffen). Ein Tipp zeigt Name, Nummern, E-Mail- und Postadressen, ein weiterer fügt eins als Text ein (Adressen einzeilig); „←“ führt zurück. Die Suche läuft im Hintergrund, kurz nach dem letzten Tastendruck. Braucht die Kontakte-Berechtigung (Einstellungen → Befehle); sie gilt auch für das Kontakte-Wörterbuch. Nur lokal. (24c) Auf keinem Gerät ausprobiert. | T |
| `@url` | `@url heise.de` öffnet die Adresse im Browser. `@url` allein schlägt echte Adressen aus Zwischenablage und Feld vor; ein Tipp fügt sie ein, „↗“ öffnet. Mit eingeschaltetem Bildschirmlesen liest „Bildschirm lesen“ einmal den sichtbaren Text der App und schlägt die Adressen darin vor, die noch nicht in der Leiste stehen; der Dienst wird sonst nie aktiv, liest keine Passwortfelder und speichert nichts. Er liest auch Apps mit Bildschirmschutz (z. B. Banking-Apps), aber nur nach dem Tipp. Vor Android 14 sind vertrauliche Felder ohne Passwort-Typ (z. B. Einmalcodes) nicht geschützt. (24d, 24e) Auf keinem Gerät ausprobiert. | T |
| `@such`, `@zeichen`, `@emoji`, `@conv` | Websuche im Browser (`@such Begriff`, Standard-Such-App, sonst DuckDuckGo); Sonderzeichen per Name (`@zeichen pfeil` → → ← ↑ ↓); Emojis per Stichwort (`@emoji daumen`); Einheiten (`@conv 5 oz` → „5 oz = 141,7 g“; Länge, Gewicht, Volumen in US-Maßen, Temperatur, Geschwindigkeit; „Pfund“ = lb; keine Währungen). (24f) Auf keinem Gerät ausprobiert. | T |
| `@snip`, `@clip`, `@wf` | Eigene Textbausteine (`@snip name`, anlegen unter Einstellungen → Befehle → Textbausteine, höchstens 50); Zwischenablage-Verlauf durchsuchen (`@clip wort`, nur wenn der Verlauf an ist; Einträge, die beim Kopieren als vertraulich markiert waren oder aus einem Passwortfeld kamen, erscheinen nie; vor diesem Stand kopierte nur, solange sie der aktuelle Clip sind); Workflow per Name wählen (`@wf name`: ohne Markierung merkt sich das Panel den Workflow, bis du Text markierst und die Textaktionen wieder öffnest, dann wird er wie bei einer Skill-Taste vorbereitet; ein Feldwechsel verwirft die Wahl). (24f) Auf keinem Gerät ausprobiert. | T |
| `@datum`, `@termin`, `@ort` | Datum und Uhrzeit in fünf Formaten einfügen (`@datum`, auch `morgen`, `+3`, `freitag`, `24.12.`); Termin als Entwurf an die Kalender-App (`@termin morgen 18 Uhr Zahnarzt`, Deutsch, Englisch, Spanisch); Maps-Link einfügen oder Karte öffnen (`@ort Adresse`, gespeicherte Orte wie `@ort zuhause` unter Einstellungen → Befehle). (24f) Auf keinem Gerät ausprobiert. | T |
| `@ki` | `@ki Frage` in zwei Tipps, ohne Panel: Der Chip „✦ KI fragen (Anbieter): Frage“ ist der Senden-Knopf; erst sein Tipp schickt genau die getippte Frage mit deinem Schlüssel. Während der Anfrage zeigt die Leiste drei wandernde Punkte mit „Frage läuft …“ und „Abbrechen“. Die Antwort erscheint als Chip in der Leiste (lange Antworten gekürzt angezeigt, ganz eingefügt); erst ihr Tipp ersetzt den Befehl, „↻“ fragt erneut (neue Anfrage), „✕“ verwirft. Tippen, Schreibmarke bewegen oder Feldwechsel brechen ab, eine späte Antwort erscheint nie. Fehler zeigen einen grauen Chip; ohne Schlüssel oder Modell öffnet er die KI-Einstellungen, sonst das Panel mit Grund und derselben Anfrage. (24f, Variante A) Nur mit Test-Anbieter geprüft, keine echte Anfrage, kein Gerät. | T |
| `@qr` | `@qr Text` (Adresse, WLAN-Daten, beliebiger Text bis 200 Zeichen) erzeugt einen QR-Code auf dem Gerät. In Feldern, die Bilder annehmen, fügt „QR-Code einfügen“ ihn als Bild ein; sonst öffnet „QR-Code teilen“ das Teilen-Menü. Der Befehlstext verschwindet erst danach. (24f) Auf keinem Gerät ausprobiert. | T |
| `@translate` | `@translate "Text" Stil` übersetzt den Text in Anführungszeichen so, wie der Rest es sagt, z. B. `@translate "wann treffen wir uns morgen" natürliches slang spanisch` (ohne Stil: natürliches Englisch). Erlaubt sind "…", „…“, “…”, «…», »…«, '…'. Der Chip „🌐 Übersetzen mit Anbieter: Stil“ ist der Senden-Knopf; erst sein Tipp schickt genau Stil und Text mit deinem Schlüssel, dann wie bei `@ki`: Punkte mit „Übersetze …“ und „Abbrechen“, die Übersetzung als Chip, erst ihr Tipp ersetzt den Befehl. Ohne Anführungszeichen nur der Hinweis-Chip „Text in "…" setzen“. Bis 500 Zeichen nach dem Befehlsnamen (andere Befehle 200). (24f, Variante A) Nur mit Test-Anbieter geprüft, keine echte Anfrage, kein Gerät. | T |

## Act Builder

| Funktion | Was sie macht | Status |
| --- | --- | --- |
| Workflow aus Beschreibung | Du beschreibst einen Ablauf in eigenen Worten. Nach Freigabe macht OpenAI (nur dieser Anbieter) daraus einen Entwurf, den du vollständig prüfst und bearbeitest und erst dann speicherst. Der Entwurf kann keine neuen Werkzeuge, Links oder Zugangsdaten einschleusen. (14) | T |
| Privater Entwurf | Bildschirmfotos sind gesperrt. App-Wechsel oder Inkognito verwerfen Beschreibung und Entwurf. (14c) | T |

## Einstellungen

| Funktion | Was sie macht | Status |
| --- | --- | --- |
| Einstellungen im neuen Look | Mit dem Tastenstil „Flat“ zeigt die Hauptseite der Einstellungen einen großen, beim Scrollen einklappenden Titel und große einzeilige Einträge mit schlichten Icons, ohne Trennlinien und Pfeile (Untertitel nur bei Sprachen). Ab Android 12 Systemfarben, sonst warm-dunkler Hintergrund. Andere Stile behalten die alte Seite. (23e) Auf keinem Gerät ausprobiert. | T |
| Design-Seite mit Kacheln | Einstellungen → Aussehen → „Design“ oder die Pille „Design“ im Tastaturmenü: ein Raster aus Vorschau-Kacheln (Hintergrund, Leertaste, Enter-Punkt) für Systemeinstellung, Hell, Dunkel, die Vorlage „Flacher Look“ und alle Farbsätze. Antippen wählt, die Auswahl hat Rahmen und Häkchen. (23f1) Auf keinem Gerät ausprobiert. | T |
| Getrennte Profile | Design, Tastenanordnung und Gesten getrennt speichern, laden und teilen. (3) | T |

## Diktat

| Funktion | Was sie macht | Status |
| --- | --- | --- |
| Diktieren auf dem Gerät | Knopf „Diktieren“ im Aktionspanel oder die Toolbar-Taste „Diktieren (auf dem Gerät)“. Die Tastatur nutzt nur die Erkennung, die Android für Spracherkennung auf dem Gerät bereitstellt (ab Android 12); fehlt sie, gibt es kein Diktat. Getestet ist der Ablauf mit einem nachgebauten Erkenner; mit echtem Mikrofon und echter Erkennung noch nie ausprobiert. (18a) | T |
| Vorschau vor dem Einfügen | Das Gehörte wird erst angezeigt. Eingefügt wird nur nach deinem Klick, an der Schreibmarke, und nur wenn sich im Textfeld nichts geändert hat. Eine Auswahl wird nie ersetzt. (18a) | T |
| Mikrofon erlauben | Unter Einstellungen → Diktat erlaubst du das Mikrofon. Ohne Erlaubnis startet keine Aufnahme. (18a) | T |

## Gelernte Wörter

| Funktion | Was sie macht | Status |
| --- | --- | --- |
| Lernen pausieren | Schalter unter „Gelernte Wörter“: Solange pausiert ist, lernt die Tastatur nichts Neues, das bisher Gelernte bleibt. (Das Ausschalten der personalisierten Vorschläge löscht dagegen alles.) Die Einstellung ist getestet; dass dabei wirklich kein Wort gespeichert wird, ist bisher nur am Code geprüft. (17a) | T |
| Gelernte Wörter zählen | Zeigt je Sprache, wie viele Wörter gelernt wurden. (17a) | T |
| Gelernte Wörter exportieren | Schreibt alle gelernten Wörter als lesbare JSON-Datei an einen Ort deiner Wahl. Mit echten Lerndaten noch nicht ausprobiert. (17a) | T |
| Gelernte Wörter löschen | Löscht nach Rückfrage alles Gelernte für alle Sprachen. Das persönliche Wörterbuch bleibt. (17a) | T |

## Ideen (nicht gebaut)

Alle offenen Ideen stehen in [ROADMAP.md](ROADMAP.md).
