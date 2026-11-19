# W3: Google-Funktionen, Bestandsaufnahme (Stand 2026-11-19)

Methode und Grenzen: In dieser Sitzung waren nur developer.android.com per WebFetch erreichbar. Die Hosts
developers.google.com, support.google.com und android-developers.googleblog.com waren gesperrt (nicht
auflösbar). Aussagen zu ML Kit und Gboard stützen sich daher auf Treffertexte der Websuche (Seitenauszüge
der genannten URLs, nicht von mir geöffnet) und sind so gekennzeichnet. Was ich nicht belegen konnte, steht als
"nicht verifiziert". Gesperrte Hosts müssten freigeschaltet werden, wenn Primärbelege nachgeholt werden sollen.

## 1. Kurzfassung (beste Wirkung je Aufwand, empfohlene Reihenfolge)

1. Autofill-Chips in der Leiste zuverlässig machen (W1a): Plattform-API, kein Google-Dienst nötig, Grundlage fehlt nur an Robustheit (73c).
2. Feldbezogene Vorschläge und Übergänge (W1d): rein lokal, nutzt vorhandene Bausteine (Tresor, Prompt-Ablage, Kontext-Chips), keine neue Abhängigkeit.
3. Texterkennung aus Screenshot/Bild per ML Kit Text Recognition (gebündelt, offline): ergänzt die Screenshot-Pille, kleine Abhängigkeit, aber Play-Services-Frage (Abschnitt 4).
4. Textbearbeitungs-Leiste und Einhandmodus (Gboard-Standard, HeliBoard hat Einhandmodus bereits teilweise): lokal, S bis M.
5. Lokales Smart-Reply-artiges Antworten über den eigenen Schlüssel (Antwortvorschläge als KI-Aktion mit Freigabe): fast fertig, S.
6. AICore als zweiter Provider (W2) nur als optionaler Pfad hinter Geräteprüfung: M bis L, nur wenn das Telefon des Owners unterstützt ist (offene Frage 1).
7. Handschrift (ML Kit Digital Ink, 300+ Sprachen laut Suchauszug): M bis L, sinnvoll erst nach der Play-Services-Entscheidung.
8. Rest (Gboard-Modelle, Assistant-Diktat, Smart Compose in Gmail, Kontodaten): nicht zugänglich, nicht anstreben.

## 2. Tabellen je Bereich

Spalten: Funktion bei Google | Status in SecondHand | Machbarkeit | Aufwand | Anmerkung.
Status-Namen entsprechen den Zeilennamen in docs/FEATURES.md. Gboard-Funktionen sind aus allgemeiner Kenntnis
beschrieben, soweit nicht anders vermerkt (Primärseiten support.google.com/gboard waren nicht abrufbar).

### Tippen

| Funktion bei Google | Status in SecondHand | Machbarkeit | Aufwand | Anmerkung |
| --- | --- | --- | --- | --- |
| Gleittippen | vorhanden über "HeliBoard-Tastatur" (Gesten-Bibliothek; Zeile "Gesten und Gefühl" in den Einstellungen) | lokal nachbaubar (vorhanden) | S | Gesten-Bibliothek wird geladen, auf Gerät nicht belegt geprüft. Quelle Gboard: support.google.com/nexus/answer/2811346 (nur Titel im Suchtreffer). |
| Nächstes-Wort-Vorhersage, Autokorrektur | vorhanden ("HeliBoard-Tastatur", "Gedächtnis: Wortfolgen", "Autokorrektur behält Umlaut-Wörter") | lokal nachbaubar | S | Gboards eigene Modelle nicht zugänglich (Bereich Sonstiges), Qualität bleibt HeliBoard plus eigenes Lernen. |
| Mehrsprachiges Tippen ohne Umschalten | teilweise ("Satzsprache", "Wörter aus anderen Sprachen", "Autokorrektur folgt der Sprache") | lokal nachbaubar | M | Es gibt keine automatische Spracherkennung beim Tippen wie bei Gboard; ML Kit Language ID (100+ Sprachen inkl. Deutsch, Suchauszug developers.google.com/ml-kit/language/identification/langid-support) wäre Option, aber Play-Services-Frage. |
| Rechtschreib- und Grammatikprüfung | teilweise ("HeliBoard-Tastatur", Rechtschreibprüfung "SecondHand", KI-Aktion "Korrigieren") | lokal nachbaubar / mit eigenem Schlüssel | M | Grammatik nur per KI-Schlüssel oder AICore. |
| Rückgängig bei Autokorrektur | vorhanden ("Rückgängig gemachte Korrekturen merken") | lokal | S | |
| Textbearbeitungs-Panel (Pfeile, Auswahl, Ausschneiden) | teilweise ("Leertasten-Gesten", Aktionspanel) | lokal nachbaubar | M | Kein eigenes Panel mit Pfeil/Alles-markieren-Block nachgewiesen. |
| Einhandmodus, schwebende Tastatur | teilweise (HeliBoard-Einhandmodus; schwebend: nicht verifiziert in FEATURES.md) | lokal nachbaubar | M | Schwebendes Fenster ist bei IME aufwendig, Gboard-Floating ist laut Presse eigene Funktion (androidcentral.com, Suchtreffer). |
| Themes | vorhanden ("Design-Seite mit Kacheln", "Eigene Designs mit Editor") | lokal | S | |
| Stylus-Handschrift ins Feld (Stylus-Eingabe) | fehlt | nicht verifiziert | L | Keine belastbare Quelle gefunden. |

### Sprache und Diktat

| Funktion bei Google | Status | Machbarkeit | Aufwand | Anmerkung |
| --- | --- | --- | --- | --- |
| Sprachliches Diktat (Standard-Erkenner) | vorhanden ("Diktieren auf dem Gerät", "Vorschau vor dem Einfügen", "Mikrofon erlauben") | lokal nachbaubar (Android SpeechRecognizer, auf Gerät) | S | Einfügen erst nach Klick, regelkonform. |
| "Assistant Voice Typing" / Advanced features auf Pixel | fehlt | nicht zugänglich | - | Laut support.google.com/gboard/answer/11197787 (Suchauszug) nur Pixel 6 und höher, Sprachen Englisch, Französisch, Deutsch, Italienisch, Japanisch, Spanisch, Text bleibt auf dem Gerät außer bei "Fix it". 9to5google (2025-04, 2024-08) nennt es Pixel-exklusiv. Für Drittanbieter-Tastaturen keine API gefunden: nicht verifiziert, ob es eine gibt. |
| Rambler / Gemini-Diktatbereinigung | fehlt | nicht zugänglich | - | support.google.com/gboard/answer/17468539 (nur Titel im Suchtreffer); Gemini-Nano-v3-Angabe stammt nur von beebom, nicht verifiziert. |
| GenAI Speech Recognition (ML Kit) | fehlt | nur mit Google-Diensten auf dem Gerät | M | In der Übersicht aufgeführt: developer.android.com/ai/gemini-nano. Geräte, Sprachen, Hintergrundverbot: nicht verifiziert. |

### Zwischenablage und Einfügen

| Funktion bei Google | Status | Machbarkeit | Aufwand | Anmerkung |
| --- | --- | --- | --- | --- |
| Zwischenablage mit Verlauf, Anheften | vorhanden ("Zwischenablage im flachen Look", "Zwischenablage-Wächter und Tresor") | lokal | S | Verlauf opt-in laut Regeln. |
| Vorschlagsleiste für Kopiertes | vorhanden ("Smart Paste", "Feld- und Kontext-Vorschläge") | lokal | S | Gboard blendet Passwörter als Punkte aus (9to5google 2020, Suchtreffer). Hier: Wächter. |
| Screenshot einfügen | vorhanden ("Screenshot-Pille") | lokal | S | |
| Text aus Bild/Screenshot (Scan text) | fehlt | nur mit Google-Diensten (ML Kit Text Recognition) oder lokal mit eigener OCR | M | Gboard hat laut Pressetext "Scan text"; Quelle dafür nicht verifiziert. ML Kit Text Recognition v2 Spracheinschränkung (Deutsch/Latein): nicht verifiziert. |
| Bildbeschreibung | fehlt | nur mit AICore (GenAI Image Description) | M | Beschrieben in developer.android.com/ai/gemini-nano. |

### Emoji und Medien

| Funktion bei Google | Status | Machbarkeit | Aufwand | Anmerkung |
| --- | --- | --- | --- | --- |
| Emoji-Vorschläge und -Suche | vorhanden ("Emoji-Wörterbücher eingebaut", "Tolerantere Emoji-Vorschläge", `@emoji`) | lokal | S | |
| Emoji Kitchen | vorhanden ("Emoji-Mix") | Abruf der Google-Bilder (www.gstatic.com) | S | Der Dienst ist kein dokumentierter Entwickler-Dienst; die Pfade stammen aus Gboards Bestand (docs/ARCHITECTURE.md Zeile 1721, docs/PROVIDERS.md Abschnitt Emoji-Mix). Nutzungsbedingungen nicht verifiziert, ein Mensch sollte die rechtliche Lage prüfen. Cloud-Aufruf erst nach Tipp, regelkonform. |
| GIF-Suche | fehlt (FEATURES.md Zeile "Flacher Look": "GIF ... gibt es nicht") | mit eigenem Schlüssel/Dienst (Tenor etc.) | M | Neuer Cloud-Provider, Freigabe je Inhalt nötig. Tenor-Bedingungen nicht verifiziert. |
| Sticker | teilweise (Emoji-Mix liefert Sticker) | siehe Emoji Kitchen | S | |
| Emoji-Zeichenkunst | vorhanden ("Zeichen-Kunst-Fenster", "Symbole malen") | lokal | - | |

### KI-Schreibhilfen

| Funktion bei Google | Status | Machbarkeit | Aufwand | Anmerkung |
| --- | --- | --- | --- | --- |
| Proofread / Korrigieren mit einem Tipp | vorhanden ("KI-Textaktionen", Aktion Korrigieren; lokal nur Großschreibung etc.) | mit eigenem Schlüssel; per AICore lokal | S bzw. M | Gboard-Proofread: support.google.com/gboard/answer/14164531 (Suchauszug): ursprünglich nur US-Englisch. Spätere Writing Tools: Pixel 9/10 plus einzelne OnePlus/Xiaomi/Samsung, Sprachen ohne Deutsch (androidauthority.com, Suchauszug; support.google.com/gboard/answer/16515540). Stand 2026 für Deutsch: nicht verifiziert. |
| Umformulieren / Ton ändern ("Writing Tools") | vorhanden (Umformulieren mit Ton) | mit eigenem Schlüssel; AICore | S | |
| Übersetzen in der Tastatur | vorhanden (Übersetzen, `@translate`) | mit eigenem Schlüssel; lokal per ML Kit Translation | S bzw. M | ML Kit Translation lädt Sprachmodelle nach (Suchauszug developers.google.com/ml-kit/tips/installation-paths). |
| Zusammenfassen | vorhanden | mit eigenem Schlüssel; AICore | S | |
| Smart Reply (Antwortvorschläge) | teilweise (KI-Aktion, kein Gesprächskontext) | mit eigenem Schlüssel; ML Kit Smart Reply | S | ML Kit Smart Reply laut Ankündigung 2019 zunächst nur Englisch (Suchauszug); heutiger Stand: nicht verifiziert. Würde Gesprächskontext brauchen, den die Tastatur nicht hat (Bildschirmlesen ist ausgeschlossen, ROADMAP "Bewusst nicht im Umfang"). |
| Smart Compose / Gmail-Vorschläge | fehlt | nicht zugänglich | - | Gmail-interne Funktion, keine API gefunden: nicht verifiziert, plausibel nicht zugänglich. |

### Passwörter und Autofill

| Funktion bei Google | Status | Machbarkeit | Aufwand | Anmerkung |
| --- | --- | --- | --- | --- |
| Inline-Autofill-Chips (Passwortmanager in der Leiste) | teilweise (Autofill-Vorschläge vorhanden laut "Feld- und Kontext-Vorschläge"; Schwächen in 73c, ROADMAP W1a) | lokal nachbaubar (Plattform-API) | M | API ab Android 11: `supportsInlineSuggestions`, `onCreateInlineSuggestionsRequest`, `onInlineSuggestionsResponse`; die Tastatur sieht den Inhalt erst nach Auswahl (developer.android.com/identity/autofill/ime-autofill, geöffnet). Passt zu den Regeln. |
| Passkey-Chips | fehlt | nicht verifiziert | - | Keine Aussage der Autofill-Seite zu Credential Manager aus IME-Sicht; nicht verifiziert, ob die Tastatur etwas tun muss. |
| Eigener Passwortspeicher | fehlt (nicht gewünscht, W1a) | nicht anstreben | - | Autofill-Dienst des Owners (z. B. Google Passwortmanager) liefert die Daten. |
| Kreditkarten/Adressen aus Google-Konto | fehlt | nur über den Autofill-Dienst des Geräts | - | Läuft über dieselbe Inline-Schnittstelle. |

### Lernen

| Funktion bei Google | Status | Machbarkeit | Aufwand | Anmerkung |
| --- | --- | --- | --- | --- |
| Lernen aus dem eigenen Tippen auf dem Gerät | vorhanden, opt-in ("Gedächtnis: Wortfolgen/eigene Wörter/Abkürzungen", "Eigene Tippfehler lernen", "Persönliche Trefferzonen", "Gelernte Wörter ...") | lokal | S | Entspricht dem, was Nutzer an Gboard bemerken (Vorschläge, eigene Wörter). |
| Föderiertes Lernen (Gboard) | fehlt | nicht zugänglich und nicht gewollt | - | Setzt Server und Google-Modelle voraus. Dass Gboard föderiertes Lernen verwendet, ist allgemein bekannt, hier nicht belegt: nicht verifiziert. |
| Stilprofil / Persönliche Stimme | fehlt (ROADMAP 17b) | lokal plus Hinweis an eigenen Schlüssel | M | Opt-in, Freigabe; bereits geplant. |
| Sicherung der Lerndaten | teilweise (Export gelernter Wörter) | lokal | S | Kein Konten-Abgleich, passt zur Regel. |

### Sonstiges

| Funktion bei Google | Status | Machbarkeit | Aufwand | Anmerkung |
| --- | --- | --- | --- | --- |
| Gboards eigene Modelle und Wörterbücher | fehlt | nicht zugänglich | - | Proprietär; HeliBoard nutzt eigene Wörterbücher (Lizenz Upstream). |
| Handschrift (Handwriting) | fehlt | nur mit Google-Diensten (ML Kit Digital Ink) | M bis L | Suchauszug: 300+ Sprachen, BCP-47 (developers.google.com/ml-kit/vision/digital-ink-recognition/base-models). Modelle werden nachgeladen. |
| Gboard Kontakte-/Kalender-/Orts-Vorschläge | teilweise (`@contact`, `@ort`, Kalenderentwurf) | lokal | S | |
| Gmail/Kalender-Zugriff | fehlt (ROADMAP 16c) | nur mit eigenen OAuth-Zugängen | L | Blockiert bis zu eigenen OAuth-Zugängen des Owners. Widerspricht "keine Konten", Ausnahme nur mit expliziter Entscheidung. |
| Entity Extraction (Datum, Adresse, Telefon erkennen) | teilweise (Smart Paste) | lokal nachbaubar oder ML Kit | M | ML Kit: 15 Sprachen laut Suchauszug. Android TextClassifier als Alternative: nicht verifiziert. |
| Sprachen-Erkennung | teilweise (Satzsprache) | lokal oder ML Kit | M | siehe Tippen |

## 3. AICore im Detail

Verifiziert (developer.android.com/ai/gemini-nano, geöffnet):
- Gemini Nano läuft im Systemdienst AICore. Der Zugriff für Apps läuft über die ML Kit GenAI APIs.
- ML Kit GenAI APIs laut Seite: Prompt API (direkter Modellzugriff über `GenerativeModel`, Text und multimodal, strukturierte Ausgabe), Agent-Workflows (`AgentRunner`), Speech Recognition, Speech Translation, Text to Speech, Image Description, Summarization ("Artikel oder Unterhaltungen als Stichpunkte"), Proofreading ("kurze Chatnachrichten"), Rewriting ("kurze Chatnachrichten in anderen Tönen"), Semantic Search (Embeddings). Zusatzbedingungen: "ML Kit GenAI API Additional Terms of Service" gelten.
- AICore hat keinen direkten Internetzugang; Modell-Downloads laufen über die Private Compute Services (Open-Source-APK). AICore speichert keine Ein- oder Ausgaben nach der Verarbeitung (laut derselben Seite).
- Gemini Nano hat Tokenlimits und Hardwaregrenzen; als Ausweichen nennt die Seite Hybrid-Inferenz über Firebase AI Logic (Cloud-Modelle).
- Das Google AI Edge SDK: Die Seite developer.android.com/ai/gemini-nano/ai-edge-sdk lieferte HTTP 404; ein Suchtreffer nennt "experimentellen Zugang". Aktueller Status (veraltet/abgelöst): nicht verifiziert.

Aus Suchauszügen (Seiten nicht selbst geöffnet, deshalb weniger belastbar):
- Geräte laut developers.google.com/ml-kit/genai: Pixel 9 und 10 (alle Varianten), Galaxy S25/S26-Serie, Z Fold7, außerdem Geräte von Xiaomi, vivo, OPPO, OnePlus, Honor, Motorola; Liste wächst und je API unterschiedlich. Prompt API hat eine eigene, kürzere Liste (nano-v2 und nano-v3). Welche Geräteliste für den Owner gilt: nicht verifiziert, siehe offene Frage 1.
- Chipsätze: MediaTek Dimensity, Qualcomm Snapdragon, Google Tensor (gleiche Seite).
- Quoten: Pro App eine Inferenzquote, zu viele Anfragen in kurzer Zeit liefern `ErrorCode.BUSY`; `PER_APP_BATTERY_USE_QUOTA_EXCEEDED` bei Tagesquote (gleiche Seite, englische Fassung im Suchauszug).
- Vordergrundpflicht: Nur die oberste Vordergrund-App darf inferieren; Hintergrund inklusive Vordergrunddienst liefert `BACKGROUND_USE_BLOCKED`. Gelesen nur in der chinesischen Seitenfassung des Suchauszugs.
- Sprachen Proofreading und Rewriting: Englisch, Japanisch, Französisch, Deutsch, Italienisch, Spanisch, Koreanisch; Eingabe unter 256 Tokens; Proofreading war 1.0.0-beta1 (Suchauszug von developers.google.com/ml-kit/genai/proofreading/android und .../rewriting/android, teils über die chinesische Fassung). Deutsch ist also genannt, aber nur über Suchauszüge.
- Gboard-eigene Writing Tools: Pixel 9 und neuer, Sprachen ohne Deutsch (Englisch, Französisch, Chinesisch, Italienisch, Koreanisch, Japanisch, Portugiesisch, Spanisch); später auf einzelnen OnePlus/Xiaomi/Samsung (androidauthority.com, androidheadlines.com 2025-09; support.google.com/gboard/answer/16515540: laut Suchauszug läuft es "auf dem Gerät oder in der Cloud"). Heutiger Stand 2026: nicht verifiziert.

Nicht verifiziert (kein Beleg gefunden):
- Ob ein Input-Method-Dienst die GenAI-APIs aufrufen darf. Plausibel kritisch wegen der Vordergrundpflicht (eine IME ist kein normaler Vordergrund-Activity-Fall) und möglicher Prüfungen des Aufrufers; nur ein Gerätetest klärt das. Der Google-Blog (2024-10) nennt Gboards Antwortvorschläge als Anwendungsfall von Gemini Nano, das ist Googles eigene Tastatur, kein Beleg für Dritte.
- Anforderung "gesperrter Bootloader" und Mindestversion Google Play services/AICore-App: keine Fundstelle. Die Docs nennen laut Suchauszug, dass bei frisch eingerichteten Geräten Setup-Fehler auftreten, weil die Android-AICore-App nötig ist.
- Tokenlimits der Prompt API, Datenschutzbedingungen der GenAI-Zusatz-ToS im Wortlaut.
- Liste aller Geräte mit Gemini Nano in Deutschland und Verfügbarkeit des Modells in Deutsch auf diesen Geräten.

Fazit zu W2: Als optionaler zweiter Provider technisch plausibel für Korrigieren, Umformulieren, Zusammenfassen mit kurzen Eingaben (unter etwa 256 Tokens je Aufruf, falls die Suchauszüge stimmen). Zwei Fragen entscheiden: Läuft es aus der IME? Hat das Telefon des Owners das Modell? Beides nur per Probe auf dem Gerät klärbar (Prüfstand-Etappe, S).

## 4. Folgen für das Projekt

Verifiziert:
- ML Kit-Modelle gibt es "unbundled" (über Google Play services, zählen nicht zur APK-Größe), "bundled" (statisch gelinkt) und per Nachladen (Digital Ink, Translation, Entity Extraction) (Suchauszug developers.google.com/ml-kit/tips/installation-paths).
- ML Kit-Bedingungen (developers.google.com/ml-kit/terms, Suchauszug, Stand 2025-05-14): Verarbeitung der Eingaben vollständig auf dem Gerät, aber die APIs können Google-Server für Fehlerbehebungen und Modelle kontaktieren und senden Leistungsmetriken an Google; Reverse Engineering eingeschränkt; Google APIs Terms of Service eingebunden.

Folgen:
- Play services: Die unbundled-Varianten und AICore setzen Geräte mit Google-Diensten voraus. Das Projekt hat bisher keine solche Abhängigkeit. Geräte ohne Google-Dienste (GrapheneOS ohne Sandboxed Play, de-Googled): die Funktionen fallen weg; Fallback nötig.
- Datenschutzaussage "offline": Der Satz "normales Tippen offline" bleibt wahr, solange ML Kit nur auf Aufruf läuft. Aber ML Kit sendet laut Bedingungen Metriken und lädt Modelle nach; das ist Netzverkehr ohne Freigabe des angezeigten Inhalts und kollidiert mit der Regel "Cloud-Aufrufe nur nach einmaliger Freigabe". Eine Beschränkung auf bundled-Modelle und Abschalten von Metriken ist nicht verifiziert (nicht geprüft, ob ML Kit das zulässt). Dokumentation (README, ANLEITUNG, PROVIDERS) müsste das sauber benennen.
- APK-Größe: Bundled-Modelle vergrößern die APK; konkrete Zahlen für Smart Reply, Digital Ink, Text Recognition, Entity Extraction, Translation: nicht verifiziert (Suche ergab keine Größen).
- Lizenz: HeliBoard ist GPL-3.0. ML Kit/Play-services-Bibliotheken sind proprietär (Google APIs ToS, Reverse-Engineering-Klausel). Ob eine proprietäre Bibliothek mit der GPL-3.0 kombinierbar ist (GPL-Systembibliotheken-Ausnahme, Weitergabe per GitHub-Release, F-Droid-Regeln) ist nicht verifiziert. Ein Mensch mit Rechtskenntnis muss das prüfen, bevor die erste Abhängigkeit hinzukommt. Das Projekt ist privat, die Weitergabe aber über Vorab-Releases möglich.
- Regeln: Das Projekt hält an "kein Konto, kein Backend" fest. ML Kit/AICore brauchen keines, wohl aber Google-Gerätedienste. Kein Widerspruch zu den Konten-Regeln, aber zur Haltung "kein Google-Zwang".
- Unabhängig davon: Autofill-Chips, Spracherkennung, feldbezogene Vorschläge brauchen nur Plattform-APIs.

Offene Fragen an den Owner (konkret):
1. Welches Telefon (Modell, Android-Version, Google-Dienste vorhanden, Bootloader gesperrt?) nutzt du für die Prüfung? Davon hängt ab, ob AICore (W2) überhaupt testbar ist.
2. Ist eine Abhängigkeit von Google Play services akzeptabel (ML Kit, AICore), oder soll alles ohne laufen und Google-Funktionen nur über Plattform-APIs und den eigenen Schlüssel?
3. Welche drei Gboard-Funktionen vermisst du im Alltag zuerst (z. B. Autofill, Handschrift, Texterkennung aus Bildern, GIF-Suche, Einhandmodus)?
4. Darf eine Funktion beim ersten Gebrauch Modelle nachladen (ML Kit), wenn der Download mit klarer Freigabe angezeigt wird, oder gilt "nie ohne den angezeigten Inhalt"?
5. Soll die Rechtslage zu GPL-3.0 mit ML Kit und zu Emoji Kitchen und GIF-Diensten vor dem Bau von einem Menschen geklärt werden (ja/nein)?

## 5. Quellen (tatsächlich geöffnet)

- https://developer.android.com/ai/gemini-nano (geöffnet, gleicher Inhalt auch unter https://developer.android.com/ai/aicore)
- https://developer.android.com/identity/autofill/ime-autofill (geöffnet)
- https://developer.android.com/ai/gemini-nano/ai-edge-sdk (404)
- Nicht abrufbar (Host gesperrt): developers.google.com, support.google.com, android-developers.googleblog.com. Deren Inhalte stammen nur aus Suchauszügen der Websuche:
  - https://developers.google.com/ml-kit/genai
  - https://developers.google.com/ml-kit/genai/proofreading/android
  - https://developers.google.com/ml-kit/genai/rewriting/android
  - https://developers.google.com/ml-kit/terms
  - https://developers.google.com/ml-kit/tips/installation-paths
  - https://developers.google.com/ml-kit/language/identification/langid-support
  - https://developers.google.com/ml-kit/vision/digital-ink-recognition/base-models
  - https://support.google.com/gboard/answer/14164531 (Proofread)
  - https://support.google.com/gboard/answer/16515540 (Writing tools)
  - https://support.google.com/gboard/answer/11197787 (Advanced voice typing)
  - https://support.google.com/gboard/answer/17468539 (Rambler)
  - https://android-developers.googleblog.com/2024/10/introduction-to-privacy-and-safety-gemini-nano.html
  - https://www.androidauthority.com/gboard-writing-tools-other-android-phones-3593589
  - https://9to5google.com/2025/04/09/gboard-advanced-voice-typing-pixel/
  - https://9to5google.com/2020/04/23/gboard-android-clipboard-suggestions/
- Intern: /home/user/SecondHand/docs/FEATURES.md, /home/user/SecondHand/docs/ROADMAP.md (W1, W2, W3, 16c, 17b), docs/ARCHITECTURE.md und docs/PROVIDERS.md (nur Emoji-Mix-Treffer).

Anzahl der als "nicht verifiziert" markierten Aussagen: 24 Vorkommen des Wortes (einzelne Aussagen mehrfach).
