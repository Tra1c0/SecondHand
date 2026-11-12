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
| Tastaturmenü | Das Raster-Symbol öffnet über den Tasten ein Menü mit sechs Pillen: Design (öffnet die Einstellungen zum Aussehen), Rückgängig, Einhändig, Emoji, Inkognito, Unverankert; der Stift-Knopf unten rechts öffnet die Einstellungen der Leiste. Das Symbol wird zum X und schließt das Menü wieder. Ohne Animation. GIF und „Tastatur teilen“ aus der Vorlage gibt es nicht. (19c) | T |
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
| KI-Textaktionen | 6 KI-Textaktionen: Korrigieren, Übersetzen, Umformulieren (Ton wählbar), Zusammenfassen, Antwort entwerfen, Analysieren (nur lesen, ersetzt nie). Vor dem Senden siehst du genau, was rausgeht, und bestätigst einmalig. (9–10) | T |
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

## Act Builder

| Funktion | Was sie macht | Status |
| --- | --- | --- |
| Workflow aus Beschreibung | Du beschreibst einen Ablauf in eigenen Worten. Nach Freigabe macht OpenAI (nur dieser Anbieter) daraus einen Entwurf, den du vollständig prüfst und bearbeitest und erst dann speicherst. Der Entwurf kann keine neuen Werkzeuge, Links oder Zugangsdaten einschleusen. (14) | T |
| Privater Entwurf | Bildschirmfotos sind gesperrt. App-Wechsel oder Inkognito verwerfen Beschreibung und Entwurf. (14c) | T |

## Einstellungen

| Funktion | Was sie macht | Status |
| --- | --- | --- |
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
