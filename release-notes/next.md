# Agent Keyboard 0.1.0 – nächster Stand

- Behoben: Sprachbefehle (`@deutsch`, `@sp` …) schalten jetzt zuverlässig um. Leerte der Befehl
  das Feld und startete die App es neu, sprang die Tastatur auf die Sprache der App zurück (z. B.
  fehlte nach `@deutsch` das „ä“ unter „a“). Die gewählte Sprache bleibt jetzt im selben Feld.
  Noch auf keinem Gerät ausprobiert.
- Neu: Rückgängig und Wiederholen per Leertasten-Geste vibrieren als drei kurze Ticks:
  Wiederholen wird stärker, Rückgängig schwächer. „Links nach rechts“ geht nicht, weil das
  Telefon nur einen Vibrationsmotor hat. Nur mit eingeschalteter Tastenvibration. Noch auf keinem
  Gerät ausprobiert.
- Behoben: Zwei Screenshots kurz nacheinander (innerhalb einer halben Sekunde) landen jetzt
  beide im Zwischenablageverlauf, nicht nur der zweite. Noch auf keinem Gerät ausprobiert.
- Behoben: Reste halb kopierter Screenshots für den Verlauf werden nach 10 Minuten gelöscht,
  und beim Beenden der Tastatur hört sie auch wirklich auf, nach Screenshots zu schauen.
- Behoben: Beim Entpacken der mitgelieferten Emoji-Wörterbücher liegt die Zwischendatei nicht
  mehr im Wörterbuchordner und wird bei einem Fehler gelöscht.
- Neu: Symbole malen (anfangs aus). Einstellungen → „Symbole malen“: Wischen über die Buchstaben
  malt ein Sonderzeichen (→ ≠ € ✓ ♥ und 35 weitere) statt eines Gestenworts; eigene Symbole
  malst du dreimal und gibst den Text dazu ein. Erkennung nur auf dem Telefon. Auf einem Gerät
  noch nicht ausprobiert.
- Neu: Emoji-Mix wie Gboards Emoji Kitchen. `@mix 🐶🔥` oder zwei getippte Emojis direkt vor der
  Schreibmarke zeigen in der Leiste „🐶 + 🔥 mischen · Google“; erst dein Tipp lädt genau dieses
  Bild von Google, dann fügt ein Tipp auf den Sticker ihn ein (oder teilt ihn) und löscht die
  zwei Emojis bzw. den Befehl. `@mix 🐶` schlägt Partner vor. In der Emoji-Ansicht teilen sich
  „Emojis suchen“ und „Emoji-Mixer“ die untere Reihe (statt Leertaste und Lupe): Im Mixer wählst
  du zwei Emojis, die Mischung lädt und gleitet groß herein. Geladene Sticker bleiben auf dem
  Gerät (höchstens 20 MB). Gesendet werden nur die beiden Emojis. Einstellungen → Befehle
  („Emoji-Mix in der Leiste anbieten“, gespeicherte Mischungen löschen). Nicht in Passwortfeldern,
  im Inkognito-Modus oder bei gesperrtem Gerät. Noch auf keinem Gerät ausprobiert.
- Behoben: Nach einem Update hatte ein eigenes Design plötzlich große Tastenabstände (die App
  spielte bei jedem Update alte Umstellungen von HeliBoard ab). Beim Update auf diesen Build kommen
  die Abstände deines gewählten Designs zurück. Dieselben Umstellungen haben bei jedem Update auch
  die angepinnten Knöpfe der Leiste zurückgesetzt und „Satzzeichen vorschlagen“ verstellt: Bitte
  einmal prüfen und neu einstellen, ab jetzt bleibt es.
- Behoben: Emoji-Mix und `@qr` löschen die getippten Emojis bzw. den Befehl nur noch, wenn die
  App das Bild wirklich angenommen hat; sonst bleiben sie stehen und ein Hinweis erscheint.
  Setzt eine App das Bild selbst an die Schreibmarke, heißt der Hinweis jetzt „Bild eingefügt …“
  statt „Text geändert“. Noch auf keinem Gerät ausprobiert.

## Nächster Build

- Neu: Gedächtnis (anfangs aus). Einstellungen → Tippen & Korrektur → Wörter & Lernen → „Was
  ich mir gemerkt habe“ → Abschnitt „Gedächtnis“ → „Merken, wie ich schreibe“; gelernt wird nur,
  wenn auch „Personalisierte Vorschläge“ an ist. Die Tastatur merkt sich Wortfolgen aus deinen eigenen
  Sätzen; schreibst du eine oft, bietet die Leiste nach den ersten Wörtern den ganzen Rest als
  einen Chip an („… sobald ich mehr weiß“), ein Tipp hängt ihn hinter der Schreibmarke an.
  Umgangssprache, die du dreimal so abschickst, wird dein Wort und nicht mehr korrigiert.
  Ersetzt du eine Abkürzung zweimal selbst durchs ganze Wort, bietet die Leiste einmal an, sie
  dir zu merken; danach steht nach der Kurzform die Erweiterung als Chip, erst dein Tipp ersetzt
  das eine gerade getippte Wort, von allein wird nie etwas erweitert. Mitten im Satz behält
  „Telefon“ sein großes T, „mit freundlichen Grüßen“ beginnt klein; „Mo“ → „Montag“ zählt jetzt
  auch als Abkürzung. Neu auch: passende Textbausteine als Chip, wenn deine Wörter klar zu einem
  `@snip`-Baustein passen. Auf der Seite siehst, pinnst und löschst du alles, schließt Apps aus
  oder vergisst alles; auch „Gelernte Wörter löschen“ und das Ausschalten von „Personalisierte
  Vorschläge“ löschen das Gedächtnis. Alles lokal; nie in Passwortfeldern, im Inkognito-Modus
  oder bei gesperrtem Gerät. Ändert nie deinen Text. Geändert seit dem Test-Build 20: Abkürzungen
  landen nicht mehr im persönlichen Wörterbuch (dort angelegte bleiben, bis du sie dort löschst),
  eine zurückgenommene Korrektur zählt erst, wenn das Wort genau dort stehen bleibt, und nur
  einmal; beim Schließen der Tastatur wird Gelerntes sofort gespeichert.
  Noch auf keinem Gerät ausprobiert.

- Neu: Dynamische Trefferzonen (anfangs aus). Einstellungen → Gelernte Wörter → Trefferzonen →
  „Dynamisch anpassen (Szenen)“, nur mit „Trefferzonen an mich anpassen“. Die Zonen folgen in
  wenigen Dutzend Buchstaben, wie du gerade tippst (im Liegen, unterwegs), und vergessen es nach
  fünf Minuten Pause oder in einer anderen App. Länger anhaltende Abweichungen merkt sich die
  Tastatur als Szenen („abends, ungenauer, trifft tiefer“) und startet dort, wenn du wieder so
  tippst. Deine Kalibrierung bleibt; nur was an mehreren Tagen und Tageszeiten wiederkommt,
  verschiebt sie ein kleines Stück, „Auf vorherige Kalibrierung zurücksetzen“ nimmt das zurück
  (der Knopf erscheint nur, wenn es einen Schritt zurück gibt). Ausgeschaltet ändert sich nichts
  mehr an deinen Zonen. Eine gehaltene Löschtaste zählt als eine Korrektur, Rückgängig und
  Ausschneiden verwerfen das Wort wie die Löschtaste, Drehen oder Einhand-Modus beginnt neu, und
  eine neue Kalibrierung rechnet die Szenen mit um. Nie Text, nie Ort; nichts in Passwortfeldern,
  im Inkognito-Modus oder bei gesperrtem Gerät. Noch auf keinem Gerät ausprobiert.

- Neu: Zwischenablage-Wächter und Tresor (nur mit eingeschaltetem Zwischenablage-Verlauf).
  Kopierte Passwörter, API-Schlüssel und Tokens landen nicht mehr im Verlauf, sondern oben in der
  Zwischenablage-Ansicht unter „Tresor“, verdeckt (z. B. „sk-proj-…7f3a“): ein Tipp fügt sie
  einmal ein, dann sind sie weg und die System-Zwischenablage wird geleert, spätestens nach
  2 Minuten verschwinden sie. Einmalcodes (auch aus kopierten SMS wie „Dein Code lautet 123456“)
  stehen 10 Minuten unter „Einmalcodes“, ein Tipp fügt nur die Ziffern ein. IBAN, Telefon, E-Mail
  und Adresse bekommen im Verlauf eine Beschriftung. Eine kopierte Nachricht mit Code bleibt als
  Text im Verlauf, ein Code allein nicht; Wörter wie „noch einmal“ oder „Barcode“ machen aus einer
  Zahl keinen Code mehr. „Das WLAN-Passwort ist Sommer2024“ gilt als Geheimnis, eine Notiz wie
  „Passwort: bitte beim Admin erfragen“ oder „Passwort: Erfragen Sie“ nicht. Ältere Passwörter im Verlauf verschwinden einmal
  nach dem Update, aussortiert im Hintergrund (angeheftete bleiben, verdeckt), gelöschte Einträge
  werden in der Datenbank überschrieben. Was du in einem Passwortfeld kopierst, wird nirgends
  vorgeschlagen; eingefügte Geheimnisse, Codes und Werte aus „Meine Daten“ (bis zu drei je Feld)
  werden nie gelernt und nie zum bearbeiteten Wort, auch nicht nach der Löschtaste, mit einem
  angehängten Buchstaben oder wenn du davor weiterschreibst, auch nach einem gewählten Vorschlag
  davor; korrigierst du darin einen Buchstaben, merkt sich die Tastatur das nicht als Tippfehler;
  die Wörter direkt danach lernt sie ab dem vierten wieder. Die Tastatur merkt sich dafür nur Position und Länge, nicht den Text. Die Tastatur speichert keine
  Hashes vertraulicher Clips mehr auf dem Gerät. Nicht in Passwortfeldern, im Inkognito-Modus oder
  bei gesperrtem Gerät. Alles lokal. Noch auf keinem Gerät ausprobiert.
- Neu: Feld- und Kontext-Vorschläge. Die Leiste schlägt vor, was zum Feld passt, und erst dein
  Tipp fügt es ein: im E-Mail-Feld deine Adresse oder eine gerade kopierte, im Code-Feld den frisch
  kopierten Einmalcode (nur die Ziffern), in Telefon- und IBAN-Feldern deinen Eintrag oder einen
  passenden Clip. Beim Schreiben nach Wörtern wie „guck mal, hier dieses Video “, „meine Nummer
  ist “, „IBAN:“ oder „Code:“ erscheint z. B. „🔗 youtube.com/…“ mit dem gerade kopierten Link.
  Deine eigenen Daten trägst du unter Einstellungen → Leiste & Befehle → „Meine Daten &
  Vorschläge“ ein (freiwillig, anfangs leer, verschlüsselt nur auf dem Gerät); dort schaltest du
  die Vorschläge auch ab. Mit eingeschaltetem Lernen merkt sich die Tastatur, welche Vorschläge du
  nimmst, nur als Zahlen ohne Text (gelöscht auch beim Ausschalten von „Personalisierte
  Vorschläge“). Eingefügte Werte und was du auf der Seite „Meine Daten“ tippst, lernt die Tastatur
  nicht. Nie in Passwortfeldern, im Inkognito-Modus oder bei gesperrtem Gerät, nie Geheimnisse aus
  dem Tresor. Alles lokal. Noch auf keinem Gerät ausprobiert.

- Neu: Textbausteine 2.0. In `@snip`-Bausteinen gehen jetzt Platzhalter: `{datum}`,
  `{datum+2t}`, `{uhrzeit}`, `{wochentag}`, `{zwischenablage}`, Ausfüllfelder `{?Name}` (die
  Leiste fragt den Wert ab, Enter springt weiter, auch das einer Hardware-Tastatur, und schickt
  nie den Befehl ab) und `{cursor}` für die Schreibmarke danach. Ein
  Baustein kann auf bestimmte Apps beschränkt werden. Der Editor zeigt eine Vorschau, Knöpfe für
  die Platzhalter und warnt bei unbekannten. Alles lokal; die Zwischenablage nur mit Verlauf an
  und nie vertrauliche Clips. Alte Bausteine bleiben, wie sie sind, bis du sie neu speicherst.
  Noch auf keinem Gerät ausprobiert.

- Neu: Wischen auf der Leertaste nach links oder rechts (z. B. Rückgängig, Wiederholen) hat jetzt
  Rastpunkte auf dem Weg: ein leichter Tick bei etwa 0,8 und 1,6 Tastenbreiten, der letzte etwas
  weiter fühlt sich wie Überdehnen an (stärker, länger) und führt erst dann die Aktion aus, einmal
  pro Wisch. Vorher loslassen tut nichts, zurück und wieder vor tickt erneut. Die drei Ticks
  direkt nach Rückgängig/Wiederholen gibt es nicht mehr. Rückgängig ist ein normaler
  Rückgängig-Schritt. Noch auf keinem Gerät ausprobiert.
- Neu: Bewegst du den Finger beim Halten der Leertaste deutlich nach links oder rechts, ist das
  Halten sofort vorbei; ein langsamer Wisch öffnet nie mehr die Halten-Aktion.
- Neu: Alle Schieberegler der Einstellungen ticken beim Ziehen leicht und rasten am Standardwert,
  an den Enden und an runden Werten (z. B. 100 %, Zehner bei Prozent und Millisekunden) mit einem
  etwas stärkeren Impuls ein. Werte direkt neben einer Rast: etwas darüber hinaus und langsam
  zurück ziehen. Gespürt nur mit „Vibration bei Tastendruck“. Die RGB-Seite folgt später. Noch auf
  keinem Gerät ausprobiert.
- Neu: Emoji-Mixer mit „Zuletzt gemischt“: Solange kein Paar gewählt ist, zeigt der Mixer die
  letzten bis zu 12 Sticker, die noch auf dem Telefon liegen; ein Tipp fügt sie ohne Internet
  ein. Im Inkognito-Modus wird nichts gemerkt; „Gespeicherte Mischungen löschen“ leert auch diese
  Reihe. Noch auf keinem Gerät ausprobiert.
- Neu: Einstellungen → „Diagnose“ zeigt, was dein Telefon wirklich macht, ohne Texte, Schlüssel
  oder Kennungen: welche Vibration die Rückgängig/Wiederholen-Kette spielt (mit „Test“), ob
  Symbole malen laufen kann (Wörterbuch, TalkBack), warum die Sprache zuletzt gewechselt hat (z. B.
  „Sprach-Hinweis der App“ oder „Sprachbefehl“), den Stand von Emoji-Mix und die Versionen.
  „gstatic erreichbar?“ lädt nur bei diesem Tipp einmal ein Testbild von Google. „Kopieren“ legt
  die Werte in die Zwischenablage, zum Schicken. Noch auf keinem Gerät ausprobiert.
- Behoben: Symbole malen: Ein Buchstabe, den du in der halben Sekunde nach einem Strich tippst,
  geht nicht mehr verloren; erst kommt das Symbol, dann der Buchstabe. Als Punkt (÷) zählt ein
  Tipp nur noch, wo ein Symbol einen Punkt braucht. Ein Wortvorschlag in dieser halben Sekunde
  kommt nach dem Symbol. „Gestenschreiben“ ist ausgegraut, solange Symbole malen an ist. Noch auf
  keinem Gerät ausprobiert.
- Neu: RGB-Beleuchtung überarbeitet. Die Seite (Einstellungen → Aussehen → „RGB-Beleuchtung“)
  hat oben eine große Live-Vorschau und darunter Karten; die Muster wählst du als Bilder.
- Neu: sechs bewegte Muster nach deinen Vorlagen: Tinte, Rauten, Lamellen, Raster, LED-Punkte,
  Höhenlinien. Sie fließen bzw. wandern langsam (Tempo über „Dauer eines Durchlaufs“).
- Neu: Schaltest du „Gleiches Muster überall“ aus, bekommen Tasten, Hintergrund, Beschriftung,
  Vorschläge und Leistenknöpfe je ein eigenes Muster mit eigenem Verlauf, Tempo und eigenen Farben.
- Neu: Der Verlauf ist eine eigene Kurve: Punkte ziehen, durch Tippen hinzufügen, durch Tippen
  oder langes Drücken wählen und mit „Punkt entfernen“ oder Hinausziehen entfernen (langes
  Drücken löscht nichts). Mit TalkBack lässt sich der gewählte Punkt über Aktionen verschieben
  und entfernen. Die bisherigen Verläufe gibt es als Vorlagen; deine bisherige Wahl wird
  automatisch zur passenden Kurve.
- Weicher: Die Farben laufen jetzt ohne Stufen über die Tasten, auch bei deiner schon
  gespeicherten Einstellung. Wer jede Taste wie bisher in einer Farbe will, schaltet „Jede Taste
  einfarbig“ ein. Eigene Farben werden ohne graue Mitte gemischt, der Regenbogen hat keine hellen
  Kanten mehr.
- Flüssiger: Die feinen Linien und Punkte der Texturen werden im Hintergrund vorbereitet und
  erscheinen beim ersten Öffnen einen Augenblick nach den Farben, statt die Tastatur kurz
  anzuhalten. Die Mustergalerie der RGB-Seite rechnet nicht mehr bei jeder Bewegung eines
  Kurvenpunkts oder Reglers neu. Die Bewegung der Texturen springt auch nach langer Laufzeit
  nicht mehr.
- Flüssiger (deine Rückmeldung zu Build 14): Die Farben der Beleuchtung werden jetzt im
  Hintergrund gerechnet, die Tastatur übernimmt nur das fertige Bild; ein stehendes Muster
  rechnet gar nicht mehr nach. Beim Ziehen eines Kurvenpunkts baut sich die RGB-Seite nicht mehr
  bei jeder Bewegung neu auf, nur die Vorschau folgt. Beim Öffnen erscheint das Licht ein Bild
  später. Noch auf keinem Gerät ausprobiert; ob es auf deinem Telefon jetzt flüssig ist, ist nicht
  gemessen.
- Noch auf keinem Gerät ausprobiert; Akkuverbrauch und Ruckelfreiheit auf dem Telefon sind nicht
  gemessen.
- Neu: Emoji-Vorschläge in der Leiste treffen deutlich mehr. Wortformen („Katzen“ → 🐈),
  Groß-/Kleinschreibung und Umlaute („KAESE“ → 🧀), Wortanfänge ab 4 Buchstaben („Schmetterl“
  → 🦋), ein Tippfehler ab 4 Buchstaben („Kafee“ → ☕), Englisch beim deutschen Tippen
  („party“ → 🎉) und eigene Synonyme („geil“ → 🔥). Höchstens zwei Emojis, hinter den ersten zwei
  Wortvorschlägen, nie als Autokorrektur. Alles lokal; nicht in Passwortfeldern, bei Inkognito
  oder bei gesperrtem Gerät. Noch auf keinem Gerät ausprobiert.
- Behoben: Symbole malen: Schnelles Tippen direkt nach einem gemalten Strich (z. B. „E-Mail“)
  verliert keine Buchstaben mehr. Als Punkt des ÷ zählt nur noch ein Tipp ungefähr senkrecht über
  oder unter der Mitte des Strichs, die Tasten daneben bleiben Buchstaben. Zwei Daumen
  gleichzeitig tippen beide Buchstaben nach dem Symbol, statt die Zeichnung und den ersten
  Buchstaben zu verwerfen. Ein Tipp, der beim Loslassen auf die Nachbartaste rutscht, tippt die
  Taste, auf der er aufsetzte. Das Malfeld eigener Symbole nimmt jetzt genau so viele Punkte wie
  die Tastatur. Noch auf keinem Gerät ausprobiert.
- Neu auf der Diagnose-Seite: was die RGB-Beleuchtung je Bild kostet (die letzten 5 Sekunden mit
  Licht). Tastatur mit RGB öffnen, kurz tippen, dann Einstellungen → Diagnose → „Kopieren“ und mir
  schicken.
- Behoben: Symbole malen (Rückmeldung zu Build 14). Ein kurzer Strich nahe der Zeichnung zählt
  jetzt zum Symbol, statt es zu beenden und einen Buchstaben zu tippen; nur ein Tipp, bei dem sich
  der Finger kaum bewegt, ist ein Buchstabe, und alles, was du weiter weg tippst, wird normal
  getippt. Direkt neben der Zeichnung kann ein verwackelter Tipp noch als Strich zählen. Die Pause zwischen zwei Strichen ist länger (anfangs 0,9 Sekunden) und passt sich deinem
  Tempo an. Ein Strich darf auch auf Punkt, Komma oder einer Ziffer beginnen. Hältst du beim Malen
  kurz inne, zeigt die Leiste, was geschrieben würde („✎ →“); ein Tipp
  darauf schreibt es sofort. Die Erkennung achtet jetzt auch auf die Richtung und Zahl der
  Striche. Neu: „Aus meinen Korrekturen lernen“ (anfangs aus) merkt sich deine Zeichnung,
  wenn du in der Leiste ein anderes Symbol wählst, und „Kalibrieren“ fragt jedes Symbol zweimal ab;
  beides unter Einstellungen → „Symbole malen“, nur auf dem Telefon, jederzeit löschbar (auch mit
  „Gelernte Wörter löschen“); das Lernen braucht zusätzlich „Personalisierte Vorschläge“. In einem künstlichen Test, dessen Malarten den neuen Vorlagen ähneln,
  96 % statt 86 % richtig; wie gut es deine Handschrift erkennt und ob ⇒ nicht mehr zu ≠ wird, ist
  offen. Noch auf keinem Gerät ausprobiert.
- Neu: Die Einstellungen haben eine neue Startseite im dunklen Look der RGB-Seite (für jeden
  Tastenstil): oben „Dein Look“ mit „Ändern“, darunter die Schnellschalter Inkognito, RGB-Licht,
  Vibration und Symbole malen, dann sieben farbige Bereiche (Aussehen, Tippen & Korrektur, Tasten
  & Sprachen, Gesten & Gefühl, Leiste & Befehle, KI & Automationen, System & Daten) und unten
  Diagnose, Updates mit der Build-Nummer und Über. Die Lupe sucht wie bisher. Jeder Bereich führt
  vorerst über eine Karte „Seiten“ zu den bisherigen Seiten; die Bereiche werden in den nächsten
  Schritten einzeln nach dem Entwurf umgebaut. Welche alte Seite wo liegt, steht in der Anleitung
  unter „Einstellungen finden“. Noch auf keinem Gerät ausprobiert.
- Neu: Einstellungen → „Tippen & Korrektur“ ist jetzt eine eigene Seite im neuen Look. Oben wählst
  du die Autokorrektur mit einem Tipp: AUS (nur Vorschläge), BEHUTSAM (nur Wörter, die es nicht
  gibt, Umlaut-Wörter bleiben) oder MUTIG (korrigiert auch knappe Fälle). Passt keine Stufe zu
  deinen Einstellungen, steht dort „Eigene Einstellung“. Darunter die Schalter für Regeln,
  Vorschläge, Leerzeichen und Lernen, die Wörterbücher mit Zahl, das persönliche Wörterbuch und die
  gelernten Wörter. Alles Weitere der bisherigen Textkorrektur unter „Alle Korrektur-Einstellungen“.
  Noch auf keinem Gerät ausprobiert.
- Neu: Einstellungen → „Gesten & Gefühl“ ist nach dem Entwurf umgebaut. Oben die Leertaste als
  Bild (Tick, Tick, am letzten Punkt die Aktion) und darunter die sechs Gesten mit ihrer
  aktuellen Aktion; ein Tipp ändert sie. Darunter wählst du mit großen Karten, was Wischen über
  die Buchstaben tut: nichts, Symbole malen oder Gestenschreiben (nur mit Gesten-Bibliothek).
  Dann „Symbole malen“ (eigene Symbole, lernen, kalibrieren, wie lange die Spur bleibt),
  „Vibration & Ton“ und „Treffen & Wischen“ (Trefferzonen, Löschen durch Wischen,
  Touchpad-Empfindlichkeit). Alles schaltet deine echten Einstellungen; die bisherigen Seiten
  gibt es weiter. Noch auf keinem Gerät ausprobiert.
- Neu: Einstellungen → „Tasten & Sprachen“ ist neu gestaltet. Oben eine farbige Karte je Sprache
  mit Layout und Wörterbüchern, die gerade benutzte ist hell und „aktiv“; ein Tipp öffnet die
  Seite der Sprache. Darunter Sprachen, Zusatztasten, Popups & Hinweise und Layouts mit Schaltern
  und den gewohnten Dialogen, dazu aus „Erweitert“ Langdruck-Dauer, Währungen, Zeitstempel-Format
  und „Zurück zu den Buchstaben“. Zwischenablage, Vibration und Ton liegen vorerst unter „Weitere
  Optionen“. Noch auf keinem Gerät ausprobiert.
- Neu: Einstellungen → „Leiste & Befehle“ ist jetzt eine eigene Seite im neuen Look. Oben wählst
  du mit einem Tipp, was die Leiste zeigt: TASTEN + VORSCHLÄGE, NUR VORSCHLÄGE oder VERSTECKT.
  Darunter alle @-Befehle als Chips (Antippen schaltet einen Befehl an oder aus, der Schalter
  oben schaltet alle ab), dann Knöpfe wählen und sortieren, Automatisch zeigen, Wischen blendet
  aus, Skill-Tasten anheften („2 von 3“), Zwischenablage-Verlauf, Aufbewahrung, Bilder und
  Dateien, Screenshots anbieten und Emoji-Mix in der Leiste. Textbausteine, Orte, Bildschirm lesen
  und das Präfix unter „Alle Befehls-Einstellungen“. „Symbolleiste“ und „Werkzeugleiste“ heißen
  überall „Leiste“. Noch auf keinem Gerät ausprobiert.
- Neu: Einstellungen → „KI & Automationen“ ist nach dem Entwurf umgebaut. Oben wählst du mit
  großen Karten den Anbieter (OpenAI, Anthropic, Google Gemini, mit dem eingestellten Modell);
  darunter steht, ob ein Schlüssel gespeichert ist (der Schlüssel selbst wird nie gezeigt), und
  „Ändern“ führt zur Schlüsseleingabe. Dann deine drei Skill-Tasten mit ihren Namen, Zielsprache
  und Tonfall, deine Workflows (mit Anzahl) sowie Diktat (Bereit/Erlauben/Nicht verfügbar) und
  Auftragsstatus. Bei gesperrtem Gerät zeigt die Seite nur den Hinweis zum Entsperren. Die
  bisherigen Seiten gibt es weiter. Noch auf keinem Gerät ausprobiert.
- Neu: Einstellungen → „System & Daten“ ist nach dem Entwurf umgebaut. Oben die Karte „Updates“:
  welcher Build aktuell oder verfügbar ist, wann zuletzt geprüft wurde und ob täglich automatisch;
  „Jetzt prüfen“ fragt GitHub nur bei diesem Tipp, „Diagnose“ öffnet die Diagnose. Darunter
  „Profile“: gesicherte Profile und der flache Look als große Karten, ein Tipp wendet sie nach
  Rückfrage an; hell ist, was gerade eingestellt ist; „+ Neues Profil“ öffnet die Tastaturprofile.
  Dann Sichern, Für Fortgeschrittene (Emoji-Version, URL-Erkennung, Hardware-Tastatur,
  Gesten-Bibliothek …) und Über (Version, Lizenz, Versteckte Funktionen, Protokoll speichern). Der
  Rest von „Erweitert“ liegt unter „Alle erweiterten Einstellungen“. Vor dem ersten Entsperren
  nach dem Neustart zeigt die Update-Karte nur einen Hinweis. Noch auf keinem Gerät ausprobiert.
- Neu: Einstellungen → Aussehen ist nach dem Entwurf umgebaut. Oben die Karte „Designs“ mit
  sechs Kacheln (deine eigenen Designs zuerst, die gewählte gelb umrandet): antippen verwendet,
  „+ Neues Design“ und „Bearbeiten“ öffnen den Design-Editor, „Alle … Designs“ die ganze
  Design-Seite. Darunter „RGB-Licht“ mit Schalter und Zusammenfassung (Muster · Hz · was leuchtet),
  dann Größe & Form, Schrift & Emoji und Hintergrund & Farben; jede Zeile zeigt den aktuellen Wert
  und öffnet den gewohnten Dialog (auch direkt nach dem Laden einer Schrift oder eines Bilds),
  „Tastenabstand“ nur mit Tastenrändern; Tag/Nacht und Navigationsleiste schaltest du direkt. Alles
  Übrige (Stil, Symbole, Tastenränder, Farben, Leertastentext) liegt unter „Alle
  Aussehen-Einstellungen“. Noch auf keinem Gerät ausprobiert.
- Neu: Formatwächter (Einstellungen → Tippen & Korrektur → Regeln, anfangs an). In E-Mail-Feldern
  schlägt die Leiste bei Tippfehlern in der Domain den richtigen Anbieter vor (`max@gmial.com` →
  „gmail.com?“), ebenso bei fehlendem `@` oder Leerzeichen; in Telefonfeldern das internationale
  Format (`0151 23456789` → „+49 1512 3456789“); bei einer IBAN zeigt ein grüner oder roter Punkt,
  ob die Prüfziffer stimmt, ein Tipp setzt Viererblöcke; in Kommazahl-Feldern ein Hinweis, wenn
  Punkt und Komma nicht zur Sprache passen. Nichts ändert sich von selbst, erst dein Tipp. Echte
  Domains anderer Länder (`hotmail.fr`, `posteo.at`) bleiben ohne Hinweis; Telefonnummern ohne
  Vorwahl liest er auch nach dem Land deiner Systemsprache. Nie in Passwortfeldern, in Feldern
  ohne Lernen, im Inkognito-Modus oder bei gesperrtem Gerät. Noch auf keinem Gerät ausprobiert.
- Behoben: Symbole malen (deine Rückmeldung zu Build 19). Getippte Punkte gehen nicht mehr
  verloren: Ein Tipp ungefähr dort, wo ein Symbol einen Punkt hat, wartet als möglicher Punkt;
  nach der Pause entscheidet die ganze Zeichnung. Ergibt sie kein Symbol mit Punkten, kommt das
  Symbol und danach der Buchstabe (dann erst nach der Pause). % geht jetzt auch mit zwei Tupfern
  statt Kreisen (erst der Schrägstrich), neu sind ‰, !, ¡, ? und ¿. Direkt nach dem Tippen eines
  Worts beginnt ein Strich jetzt als Zeichnung, sobald er etwa eine Dreivierteltaste lang ist;
  ein beim schnellen Tippen rutschender Tipp bleibt Buchstabe. Rutscht er doch so weit, aber
  bleibt es ein kurzer gerader Strich (unter anderthalb Tasten) ohne zweiten Strich, kommt der
  Buchstabe, auf dem du aufgesetzt hast, statt „–“ oder „/“; einen kurzen Strich direkt nach einem
  Wort malst du darum etwas länger oder mit zweitem Strich (=). Liegt ein zweiter Daumen auf,
  beginnt direkt nach dem Tippen keine Zeichnung. Solche Buchstaben und die möglicher Punkte
  erscheinen erst nach der Pause oder vor der nächsten Taste, ohne Tastenvorschau, Vibration und
  Ton. Im künstlichen Finger-Test ÷ 99 % statt 71 %, % mit Tupfern 98 % statt 0 %, mit winzigen
  Kreisen erst 53 %. Noch auf keinem Gerät ausprobiert.
- Neu: Gehst du zu einem automatisch korrigierten Wort zurück (Löschtaste direkt danach oder
  Schreibmarke ins Wort), steht dein getipptes Wort ganz links in der Leiste, in deiner Groß- und
  Kleinschreibung; Mitte und rechts ähnliche Wörter. Ein Tipp setzt es ein und zählt wie die
  Löschtaste. Noch auf keinem Gerät ausprobiert.
- Behoben: Die Autokorrektur macht aus einem richtigen Wort nicht mehr nur eine andere
  Großschreibung („essen“ bleibt, „Essen“ steht als Vorschlag da). Nach „ca.“, „bzw.“, „vgl.“,
  „z. B.“ und ähnlichen Abkürzungen schreibt die Tastatur nicht mehr groß weiter. Noch auf keinem
  Gerät ausprobiert.
- Neu: Die Tastatur lernt auch Tippfehler, die du später verbesserst (mit „Meine Tippfehler
  lernen“): nach weiteren Wörtern, nach Zurücklöschen, über einen Vorschlag, nach Tippen ins
  Wort oder nach Wischen auf der Leertaste zum Wort, solange du im Feld bleibst. Nur echte
  Vertipper eines Nicht-Worts zu einem echten Wort zählen (Nachbartaste, fehlender, doppelter oder
  vertauschter Buchstabe, Umlaut); „das“ → „dass“, Markieren und Überschreiben, Einfügen, ein
  fertig geschriebenes Wort („Bahnho“ → „Bahnhof“, also auch fehlende Endbuchstaben wie „nich“ →
  „nicht“), die Autokorrektur selbst, das Rechtschreib-Menü der App und Änderungen der App (ihre
  Autokorrektur, Autofill, Rückgängig, auch Strg+Z einer Hardware-Tastatur) nie, nur Wörter, an
  denen du selbst getippt oder gelöscht hast. Ändert die App das Wort, an dem du gerade tippst,
  kann das trotzdem zählen. In langsamen Apps liest die Tastatur
  dafür nichts. Beim ersten Mal nur gemerkt, beim zweiten Mal an anderer Stelle gelernt (leert ein
  Messenger das Feld nach dem Senden, ist die nächste Nachricht eine andere Stelle), danach
  korrigiert die Autokorrektur das Wort. Machst du eine
  Autokorrektur später rückgängig, wird sie mit „Rückgängig gemachte Korrekturen merken“ sofort
  nicht mehr angewendet. Alles sehen, übernehmen und löschen: Einstellungen → Tippen & Korrektur →
  „Was ich mir gemerkt habe“. Keine Rückfragen beim Tippen; nie in Passwortfeldern, Inkognito,
  bei Lernpause oder gesperrtem Gerät. Noch auf keinem Gerät ausprobiert.
- Behoben: Tippst du Deutsch und Englisch, korrigiert die Autokorrektur „hand“, „name“, „bank“,
  „kind“ und ähnliche Wörter wieder zu „Hand“, „Name“ …; nur ein Wort, das deine Sprache selbst
  klein kennt („essen“), bleibt. Nach „Fr.“, „Jh.“, „Jr.“ und Initialen („J. K. Rowling“) schreibt
  die Tastatur wieder groß weiter, nach „bzgl.“, „insb.“, „lt.“, „mind.“, „ugs.“, „eigtl.“ nicht.
  Nach einem geleerten Feld zeigt die Leiste kein altes getipptes Wort mehr. „Z. B.“, „D. h.“,
  „U. a.“ und „I. d. R.“ am Satzanfang schreiben das nächste Wort wieder klein. Noch auf keinem
  Gerät ausprobiert.
- Neu: Auf der RGB-Seite ist die Bildrate jetzt die Karte „Bildwiederholrate“ nach deiner Skizze:
  zwei große Farbflächen untereinander, „30 HERTZ“ (gelb-orange, spart Akku) und „60 HERTZ“
  (türkis-grün, flüssiger). In beiden wandert ein leuchtender Tropfen gleich schnell und genau
  übereinander hin und her; der obere springt in 30 Schritten pro Sekunde, der untere gleitet mit
  60, so siehst du den Unterschied. Die gewählte Fläche hat volle Farbe und einen Rahmen, die
  andere ist blasser, ihre Schrift bleibt dabei gut lesbar (auch im hellen Design). Die Überschrift
  bricht auf schmalen Telefonen mit großer Schrift nicht mehr mitten im Wort um, sie wird dort
  etwas kleiner. Ohne Android-Animationen (beim Öffnen der Seite) und im Energiesparmodus (sofort)
  stehen die Tropfen still. „Im Energiesparmodus anhalten“ steht darunter. Die Einstellung selbst
  ist dieselbe wie vorher. Auch die Auswahl- und Sprachkarten der neuen Einstellungen behalten bei
  den nicht gewählten Karten jetzt dunkle, gut lesbare Schrift. Noch auf keinem Gerät ausprobiert.
- Neu: Smart Paste (anfangs an, Einstellungen → Leiste & Befehle → Zwischenablage & Bilder). Hast
  du etwas kopiert, stehen hinter der Zwischenablage-Pille (wenn „Zwischenablagen-Inhalt
  vorschlagen“ an ist) und auf den Karten der Zwischenablage-Ansicht passende Chips: Adresse → in
  Karten öffnen (gesucht wird nur mit Straße, PLZ und Ort) oder als `@ort`-Ort speichern (nicht,
  wenn die Adresse mitten in einem Satz steht); Telefonnummer, die mit „+“, „00“ oder „0“ beginnt →
  Telefon-App mit der Nummer oder neuer Kontakt (eine bloße Ziffernfolge wie eine Paket- oder
  Bestellnummer bekommt keinen Anruf-Chip); IBAN → in Viererblöcken einfügen, nur mit richtiger
  Prüfziffer (sonst ein Hinweis); Sendungsnummer von DHL, Deutsche Post, Hermes, DPD, GLS oder UPS
  → Sendungsverfolgung (nichts direkt hinter „Kundennummer“, „Bestellung“ oder
  „Rechnungsnummer“); Datum oder Uhrzeit → Termin-Entwurf im Kalender (nicht für Versionsnummern
  wie „1.2.30“ oder „PHP 8.3.28“ und nicht für Öffnungszeiten); E-Mail-Adresse → Mail schreiben;
  Link → ohne Tracking-Anhängsel (`utm_…`, `fbclid`, `si` …) einfügen; kopierte SMS mit Code → nur
  den Code einfügen. Nichts passiert von selbst, jeder Chip wirkt nur auf deinen Tipp; tat ein Tipp
  nichts, kannst du ihn wiederholen. Nie für Geheimnisse, nie in Passwortfeldern, im
  Inkognito-Modus oder bei gesperrtem Gerät. Die Erkennung läuft lokal, die Tastatur sendet nichts;
  „Sendung verfolgen“ öffnet die Seite des Paketdienstes im Browser. Keine neue Berechtigung. Noch
  auf keinem Gerät ausprobiert.

- Neu: Zeichen-Kunst. Emoji-Taste lange drücken → Stift „✎“: Statt der Tasten erscheint ein
  Fenster, das aus deiner Zeichnung, einem Foto oder Screenshot als Vorlage, dem Text aus dem Feld
  und einem Rahmen Text aus Zeichen macht (Braille-Punkte, Blöcke, Halbblöcke, ASCII, Emoji-Pixel
  oder eigene Zeichen), alles frei kombinierbar, jede Änderung sofort in der Vorschau. Text als
  große Buchstaben in drei Schriften oder in Unicode-Stilen (𝐟𝐞𝐭𝐭, 𝑘𝑢𝑟𝑠𝑖𝑣, 𝓢𝓬𝓱𝓻𝓲𝓯𝓽, 𝔉𝔯𝔞𝔨𝔱𝔲𝔯,
  Ⓚⓡⓔⓘⓢⓔ, d̶u̶r̶c̶h̶ …, kombinierbar). Für WhatsApp, Telegram, Discord und Signal: Breite mit
  Chatblase „passt aufs Handy“, Codeblock, unsichtbare Lücken (⠀) und eine Warnung, wenn es in der
  App vermutlich zerfällt. Ins Feld nur per Tipp auf „Einfügen“ oder „Ersetzen“, danach 8 Sekunden
  „Rückgängig“; außerdem Kopieren und als Textbaustein speichern. Alles auf dem Telefon, ohne
  Internet; nie in Passwortfeldern, im Inkognito-Modus oder bei gesperrtem Gerät. Screenreader
  lesen Zeichen-Kunst als Zeichensalat. Noch auf keinem Gerät ausprobiert.
- Verbessert (deine Entscheidung): Die grauen Auswahlkarten in den Einstellungen (AUS, NICHTS,
  AUSGEBLENDET, FLACHER LOOK) haben jetzt helle Schrift und sind gut lesbar, auch wenn sie nicht
  gewählt sind. Noch auf keinem Gerät ausprobiert.
- Verbessert (Prüfung der Zeichen-Kunst): In Apps ohne eigenes Profil gilt „Andere“ statt
  WhatsApp, und reiner Text in Unicode-Stilen (𝐟𝐞𝐭𝐭 …) bekommt nie mehr einen Codeblock; die
  Vorschau zeigt genau das, was eingefügt wird, samt ```. Beim Tippen im Fenster nehmen die Tasten
  die ganze Breite ein (auch im Querformat groß genug). Malen bleibt flüssig, auch mit vielen
  Strichen; Füllen rechnet erst beim Loslassen. Hochkant fotografierte Bilder stehen aufrecht.
  Mit markiertem Text gibt es „Markierung ersetzen“. „Ersetzen“ prüft, dass sich das Feld nicht
  geändert hat. Durchgestrichener Text bricht sauber um, Kopfüber lässt Emojis ganz, Rahmen werden
  nie breiter als eingestellt, Groß-Schrift endet nach 80 Zeilen, zu große Kunst sagt es statt
  „Das Feld hat sich geändert“. Umschalt + ß ergibt ẞ. TalkBack nennt den Stift. Das kleine „✎“
  auf der Emoji-Taste bleibt als Hinweis auf den Langdruck. Noch auf keinem Gerät ausprobiert.
- Behoben (deine Rückmeldung zu Build 21): Die Autokorrektur stellt mit der Sprache um. Nach
  `@sp` korrigiert sie spanisch, nach `@deutsch` wieder deutsch. Bei Sprachen mit zweiter Sprache
  („Mehrsprachiges Tippen“, z. B. Spanisch mit Deutsch) blieb bisher oft die andere Sprache die
  bevorzugte, beim Wechsel zwischen Deutsch-mit-Spanisch und Spanisch-mit-Deutsch sogar ganz.
  Gelernte Tippfehler gelten jetzt nur in der Sprache, in der du sie gelernt hast; ältere nur, wo
  das Wörterbuch der Sprache das gemeinte Wort kennt. Noch auf keinem Gerät ausprobiert.
- Neu: Wörter aus deinen anderen Sprachen (mit „Personalisierte Vorschläge“). Schreibst du z. B.
  „hola“ in einer deutschen Nachricht und nimmst die Korrektur einmal mit der Löschtaste zurück,
  bleibt „hola“ auf Deutsch ab dann stehen und wird beim Tippen von „ho…“ vorgeschlagen, nie
  automatisch eingesetzt. Ohne Korrektur reicht das zweite Mal an anderer Stelle. Gilt nur für
  die Sprache, in der du das Wort behalten hast. Ein Wort, das die Sprache in anderer Groß- oder
  Kleinschreibung kennt („hand“/„Hand“), wird nie gemerkt; ersetzt du ein gemerktes Wort durch ein
  anderes, ist es wieder vergessen. Die zweite Sprache der gerade benutzten zählt nicht mit (die
  kennt die Tastatur dort ohnehin). Liste mit „Löschen“ unter „Was ich mir gemerkt habe“ → „Wörter
  aus anderen Sprachen“; unter „Korrekturen“ steht jetzt die Sprache dabei. Alles lokal; nichts in
  Passwortfeldern, im Inkognito-Modus, bei Lernpause oder gesperrtem Gerät. Noch auf keinem Gerät
  ausprobiert.
  die Sprache, in der du das Wort behalten hast. Liste mit „Löschen“ unter „Was ich mir gemerkt
  habe“ → „Wörter aus anderen Sprachen“. Alles lokal; nichts in Passwortfeldern, im
  Inkognito-Modus, bei Lernpause oder gesperrtem Gerät. Noch auf keinem Gerät ausprobiert.
- Neu: Einheitliches Befehlssystem. Ein Aufbau für deine eigenen Einstellungen, ohne `&`:
  `@lang duolingo spanish` (Duolingo startet ab jetzt auf Spanisch, auch wenn „Sprache pro App
  merken“ aus ist), `@lang whatsapp german`, `@lang apps` (alle Sprachen je App), `@lang whatsapp
  del`, `@guard banking off` (Formatwächter je App), `@mem whatsapp off` (Gedächtnis bleibt aus
  der App heraus), `@group messenger whatsapp signal` und danach `@lang messenger german`,
  `@snip add gruss Viele Grüße`, `@snip del gruss`, `@clip on`/`off`. Die Wörter dürfen in jeder
  Reihenfolge und mit Tippfehlern kommen; Deutsch geht auch (`@sprache`, `@wächter`,
  `@gedächtnis`, `@gruppe`, `@baustein`, `@ablage`; `zeig`, `weg`, `an`, `aus`, `neu`). Mehrere
  Befehle hintereinander mit `;`, `dann`/`then` oder `→`: `@lang whatsapp german; duolingo
  spanish; @guard banking off`. Die Leiste zeigt vorher in Klartext und farbig wie im Code-Editor,
  was passiert; bei zwei Deutungen zwei Chips. Erst dein Tipp führt aus, danach 8 Sekunden
  „↶ Rückgängig“. Enter führt nur genau eine harmlose Deutung aus; Löschen und `all` nur per Tipp,
  Enter schickt den Befehl dann nicht ab. `@?` zeigt alle Bereiche, `@lang ?` den Aufbau; beim
  Tippen schlägt die Leiste Apps, Sprachen und Bausteine vor. Die alten Befehle (`@deutsch`,
  `@lang spanisch`, `@snip adr`, `@clip Termin`) gehen wie bisher; `@deutsch` schaltet jetzt auch
  mit Enter um (nur mit dem genauen Namen der Sprache; ist sie schon aktiv, sagt Enter das nur).
  Normaler Text, der bloß mit einem Bereichswort beginnt („Hallo @gruppe wer kommt heute?“),
  ist kein Befehl: Enter schickt ihn ab, die Autokorrektur arbeitet. Ohne `;` geht eine Kette nur
  hinter einem vollständigen Befehl weiter, `@snip adr` hinter anderem Text bleibt ein Baustein.
  Ist eine Liste voll oder lässt sich etwas nicht ändern, bleibt der Befehlstext stehen und nichts
  ist halb geändert. Hat eine App eine Befehlssprache, bleibt ein Wechsel mit der Sprachtaste im
  Feld auch nach dem Senden (mit „Sprache pro App merken“ aus). Ändert nur deine eigenen
  Einstellungen und Daten; nie in Passwortfeldern, im Inkognito-Modus oder bei gesperrtem Gerät.
  Die Farben gibt es nur in der Leiste, nicht im Textfeld. Noch auf keinem Gerät ausprobiert.
- Neu: Update-Fenster. Hat „Jetzt prüfen“ oder die tägliche Suche einen neuen Build gefunden,
  zeigen die Einstellungen beim Öffnen „Build N ist da“ mit seinen Notizen; „Installieren“ öffnet
  die Seite Updates und startet dort den Download, „Später“ fragt für diesen Build nicht mehr. In
  der leeren Leiste der Tastatur steht dann „⬆ Build N verfügbar“ (nie statt Wortvorschlägen, nie
  bei gesperrtem Gerät), ein Tipp öffnet die Seite Updates. Nach dem Update kommt einmal „Neu in
  Build N“ mit den Notizen aus der App selbst, ohne Netz. Gesucht wird weiter nur auf deinen Tipp
  oder mit „täglich automatisch“. Noch auf keinem Gerät ausprobiert.
