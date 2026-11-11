# Agent Keyboard – Funktionen

Jede Funktion mit kurzer Erklärung, für den Owner geschrieben. Wird im selben Push wie jede
Änderung aktualisiert (siehe `CLAUDE.md`). Technische Details: [ARCHITECTURE.md](ARCHITECTURE.md),
Prüfnachweise je Etappe: [VALIDATION.md](VALIDATION.md). Die Liste beschreibt den Stand des
zuletzt gepushten Branches. In Klammern steht die Etappe, in der die Funktion entstand.

Status: **E** = auf einem Gerät oder Emulator von Anfang bis Ende geprüft · **T** = durch
automatische Tests abgedeckt (Robolectric simuliert Android, das ist kein Gerätetest) ·
**–** = noch nicht geprüft. Nutzung auf einem echten Gerät zählt nur, wenn der Owner es sagt.
Bisher hat keine Funktion den Status E, weil noch nie ein Gerätetest stattfand.

## Tastatur

| Funktion | Was sie macht | Status |
| --- | --- | --- |
| HeliBoard-Tastatur | Tippen, Layouts, Wörterbücher, Autokorrektur, Vorschläge, Themes und Emoji wie in HeliBoard 4.2. Die Upstream-Tests laufen bis auf fünf geerbte Fehler durch. (0) | T |
| Eigene App | Heißt „Agent Keyboard“ (Debug: „Agent Keyboard Lab“), eigene App-ID, damit sie neben HeliBoard installiert werden kann. (1) | T |
| Sichere Voreinstellungen | Lernen aus deinem Tippen, Zwischenablage-Vorschläge und Zwischenablageverlauf sind anfangs aus. Keine automatischen Backups. (1) | T |
| Normales Tippen offline | Die Internetverbindung wird erst nach einer ausdrücklichen Sendefreigabe aufgebaut, Tippen und lokale Werkzeuge brauchen sie nicht. Getestet ist der späte Aufbau; eine Messung auf dem Gerät fehlt. (9c) | T |

## Aktionen im Textfeld

| Funktion | Was sie macht | Status |
| --- | --- | --- |
| Aktionspanel | Toolbar-Taste öffnet ein Panel über der Tastatur: markierten Text ansehen, Aktion wählen, Ergebnis prüfen, dann bewusst übernehmen oder kopieren. (5) | T |
| Aktionsrad | Rundes Menü mit denselben Aktionen, auch mit TalkBack bedienbar. (6) | T |
| Leertasten-Langdruck | Optional: Langes Drücken der Leertaste öffnet das Rad. Standardmäßig aus, einschaltbar im Gestenprofil. (7) | T |
| Lokale Werkzeuge | 3 lokale Textwerkzeuge: Großschreibung, Kleinschreibung, Leerzeichen aufräumen. Laufen ohne Internet und sind als lokal gekennzeichnet. (5) | T |
| Schutz beim Ersetzen | Ersetzt wird nur, wenn sich Feld, Auswahl und Text seit der Vorschau nicht geändert haben. Sonst bleibt die Übernahme gesperrt. (4) | T |
| Schutzbereiche | Passwortfelder, Inkognito, gesperrtes Gerät und Felder mit Lernverbot sperren alle Aktionen. (4) | T |

## KI (eigener OpenAI-Schlüssel)

| Funktion | Was sie macht | Status |
| --- | --- | --- |
| Schlüssel speichern | Dein OpenAI-Schlüssel wird verschlüsselt auf dem Gerät gespeichert, nie angezeigt, nicht gesichert. (8) | T |
| Modell frei wählen | Die OpenAI-Modellkennung ist frei einstellbar. (9) | T |
| KI-Textaktionen | 6 KI-Textaktionen: Korrigieren, Übersetzen, Umformulieren (Ton wählbar), Zusammenfassen, Antwort entwerfen, Analysieren (nur lesen, ersetzt nie). Vor dem Senden siehst du genau, was rausgeht, und bestätigst einmalig. (9–10) | T |
| Übersetzen | 14 Zielsprachen, darunter Deutsch, Englisch und Spanisch. (10a) | T |
| Echte Anfrage an OpenAI | Mit echtem Schlüssel noch nie ausprobiert. | – |

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
| Testwerkzeug | Zählt Zeichen, Wörter und Zeilen des markierten Texts. Läuft nur auf dem Gerät, ersetzt nichts. Dient zum Ausprobieren des Werkzeug-Ablaufs. (15a) | T |
| Kalenderentwurf | Öffnet die Kalender-App mit einem neuen Termin: erste Zeile als Titel, ganzer Text als Beschreibung. Datum und Uhrzeit stellst du dort ein. Gespeichert ist der Termin erst, wenn du ihn dort speicherst; die Tastatur liest den Kalender nicht. (15b) | T |

## Act Builder

| Funktion | Was sie macht | Status |
| --- | --- | --- |
| Workflow aus Beschreibung | Du beschreibst einen Ablauf in eigenen Worten. Nach Freigabe macht OpenAI daraus einen Entwurf, den du vollständig prüfst und bearbeitest und erst dann speicherst. Der Entwurf kann keine neuen Werkzeuge, Links oder Zugangsdaten einschleusen. (14) | T |
| Privater Entwurf | Bildschirmfotos sind gesperrt. App-Wechsel oder Inkognito verwerfen Beschreibung und Entwurf. (14c) | T |

## Einstellungen

| Funktion | Was sie macht | Status |
| --- | --- | --- |
| Getrennte Profile | Design, Tastenanordnung und Gesten getrennt speichern, laden und teilen. (3) | T |

## Ideen (nicht gebaut)

Alle offenen Ideen stehen in [ROADMAP.md](ROADMAP.md).
