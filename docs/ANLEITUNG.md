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

Optional: Die Leertaste kann das Rad öffnen, z. B. beim Halten (siehe „Leertaste“).

## Leertaste

Einstellungen → Erweitert → **Leertaste** (oder Einstellungen → Tastaturprofile → Gesten →
„Leertaste“). Für jede Geste wählst du eine Aktion:

- **Nach links / rechts / oben / unten wischen**: Wische auf der Leertaste los. Die Richtung des
  ersten Stücks entscheidet. „Cursor bewegen“, „Sprache wechseln“ und „Touchpad-Modus“ laufen
  mit, solange du wischst (auch zurück); alle anderen Aktionen passieren einmal pro Wisch.
- **Halten**: Leertaste lange drücken, z. B. für das Aktionsrad oder „Tastatur auswählen“.
- **Doppeltippen**: Der erste Tipp schreibt wie immer ein Leerzeichen, ein zweiter Tipp direkt
  danach führt die Aktion aus statt ein zweites Leerzeichen zu schreiben. Solange Doppeltippen
  eine Aktion hat, ruht „Punkt plus Leerzeichen“.
- **Befehlsliste** tippt das Befehlspräfix (z. B. `@`) an einen Wortanfang; die Leiste zeigt dann
  alle Befehle. In Passwortfeldern, im Inkognito-Modus und bei ausgeschalteten Befehlen passiert
  nichts.
- **Standard** im Auswahlfenster stellt die Geste zurück: Sie macht dann wieder, was sie vor
  dieser Seite gemacht hat.

Rad, Panel und Diktat behalten über jede Geste ihre Schutzregeln (Passwortfelder, Inkognito,
gesperrtes Gerät). Ein einzelner Tipp schreibt immer ein Leerzeichen.

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
2. Screenshot machen. Ist die Tastatur offen, steht er sofort als Pille in der Leiste, sonst
   beim nächsten Tippen in ein Textfeld; drei Minuten lang.
3. Die Pille steht zwischen den Wörtern statt der Vorschläge: im leeren Feld, nach einem
   Leerzeichen, einem Satzzeichen oder einer neuen Zeile. Tippst du den ersten Buchstaben eines
   Worts, zeigt die Leiste wieder die Vorschläge für dieses Wort; nach dem nächsten Leerzeichen
   ist die Pille wieder da. Ein getippter Befehl (z. B. `@math`) zeigt seine Chips statt der
   Pille.
4. Pille antippen: Das Bild wird eingefügt. Die Pille erscheint nur in Feldern, die Bilder
   annehmen. Langes Drücken (im flachen Look) oder das Kreuz blendet die Pille aus; danach
   kommen sofort wieder die normalen Vorschläge.

Die Tastatur gibt der App eine Kopie, die nach 10 Minuten gelöscht wird. Wechselst du das Feld,
bevor die Kopie fertig ist, wird nichts eingefügt.

Zwischenablage: Ist „Zwischenablage-Verlauf aktivieren“ (Einstellungen → Optionen) an, legt die
Tastatur jeden neuen Screenshot auch als Bild in ihren eigenen Verlauf (Zwischenablage-Taste).
Die System-Zwischenablage bleibt unberührt, andere Apps sehen ihn dort nicht. Es gelten die
Regeln des Verlaufs (Dateien erlaubt, Größengrenze, Aufbewahrungszeit, Anheften). Nicht bei
gesperrtem Gerät und nicht im Inkognito-Modus. Schaltest du die Screenshot-Pille aus, löscht die
Tastatur diese Einträge wieder; angeheftete bleiben. Noch auf keinem Gerät ausprobiert.

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
- Abschalten unter Einstellungen → Befehle → `@lang`; dort stehen auch die Kürzel deiner aktiven
  Sprachen.

## Design auswählen

Einstellungen → Aussehen → „Design“ oder im Tastaturmenü die Pille „Design“: Kachel antippen.
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

Einstellungen → Aussehen → „RGB-Beleuchtung“ (oder unten auf der Design-Seite). Oben läuft eine
Vorschau; tippe darauf, um die Effekte beim Tippen auszuprobieren. Darunter:

- „RGB-Beleuchtung an“ schaltet alles ein (anfangs aus).
- Muster: Welle waagerecht, senkrecht oder diagonal, Kreis von der Mitte, Atmen (alle Tasten
  gleich, heller und dunkler) oder statischer Regenbogen (steht still).
- Verlauf: Sinus (weich hin und zurück), Dreieck (Zickzack), Sägezahn (läuft durch alle Farben
  und fängt wieder an), Stufen (zackig, mit Regler für die Anzahl), An- und Abstieg (sanft hoch,
  kurz halten, sanft runter, Pause).
- Tempo und Richtung: Dauer eines Durchlaufs 0,5–30 s, „Richtung umkehren“, Wellenbreite (wie
  viele Farbdurchläufe über die Tastatur passen).
- Farben: „Regenbogen“ oder „Eigene Farben“ (Kreis antippen, Farbe wählen; 2 bis 6 Farben über
  „Farbe hinzufügen“ und „Letzte Farbe entfernen“), Sättigung, Helligkeit und Deckkraft
  gegenüber den Farben deines Designs (0 % = nur Design, 100 % = nur Licht).
- Was leuchtet (mehrere wählbar): Tastenhintergrund, Tastaturhintergrund (hinter und zwischen
  den Tasten), Beschriftung. Beschriftungen bleiben immer lesbar: Ist der Kontrast zum
  Hintergrund kleiner als 3:1, werden sie heller oder dunkler.
- Wo (mehrere wählbar): Buchstabentasten, Funktionstasten (Umschalt, Löschen, Enter, ?123,
  Komma, Punkt), Leertaste, Zahlenreihe, Vorschlagsleiste (Text der Wortvorschläge),
  Leistenknöpfe. In der Leiste ändert sich nur die Farbe von Text und Symbolen.
- Beim Tippen: keine Reaktion, getippte Taste blitzt auf und verblasst, ein Ring breitet sich von
  der Taste aus, oder Tippen beschleunigt kurz. „Nur beim Tippen sichtbar“: sonst ist das Licht
  aus, nach jedem Tipp erscheint es und verblasst (mit „Taste blitzt auf“ nur an der getippten
  Taste). „Nachleuchten“ stellt ein, wie lange das dauert (0,2–5 s).
- Akku: 30 oder 60 Bilder pro Sekunde (30 spart Akku), „Im Energiesparmodus anhalten“ (an).

Jede Änderung wird sofort gespeichert und gilt beim nächsten Öffnen der Tastatur. Die
Beleuchtung läuft nur, solange die Tastatur sichtbar ist. Hast du in den Android-Einstellungen
(Entwickleroptionen oder Bedienungshilfen) die Animationen ausgeschaltet, bleibt das Bild stehen
und die Tipp-Effekte entfallen. Die Einstellung steht im Designprofil (Tastaturprofile →
Design).

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
- Das Aktionsrad erreichst du im flachen Look über eine Leertasten-Geste, z. B. Halten
  (Einstellungen → Erweitert → Leertaste), die Stift-Taste öffnet das Panel.

## Emoji-Vorschläge

Für Deutsch, Englisch und Spanisch ist das Emoji-Wörterbuch eingebaut; du musst nichts
herunterladen. „Emojis vorschlagen“, „Inline-Emoji-Suche“ (Emoji nach `:`, z. B. `:herz`) und die
Emoji-Beschreibungen sind in HeliBoard standardmäßig an und wirken damit sofort. Abschalten unter
Einstellungen → Textkorrektur. Für andere Sprachen gilt weiter der Hinweis mit dem Download-Link.

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
