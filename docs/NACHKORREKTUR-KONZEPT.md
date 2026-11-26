# Nachkorrektur im Satz und automatische Satzzeichen (Etappen 104 und 105): Konzept

Geschrieben vor dem Code, danach gepflegt. Code-Landkarte: `docs/ARCHITECTURE.md` → „Kern“ (Satzprüfung) und „Geänderte
Upstream-Dateien“ (`InputLogic`), Prüfnachweise: `docs/VALIDATION.md` → „Etappen 104 und 105“. Arbeitsweise:
`.claude/skills/product-initiative/SKILL.md`.

**Auftrag 104 (Owner, Runde 5):** „Korrigiere rückwirkend, sobald der Satzkontext Fehler eindeutig auflöst“, mit sieben
Beispielen (Bruchteil realer Fehlerketten, keine Sonderregeln), „Keine geratenen Korrekturen, Bedeutungsänderungen oder
Stilumschreibungen“, Schutz bewusster Eingaben, Eigennamen, Jugendsprache, Mehrsprachigkeit; Undo, manuelle Rückkorrektur,
Cursor, sensible Felder, offline; „Kontext vor Änderung validieren; keine veralteten Ersetzungen beim Weitertippen.“
**Auftrag 105:** notwendige Nebensatzkommas, Satzabschlüsse, Fragezeichen, fehlende Punkte direkt am letzten Wort vor
Absatzumbrüchen; keine pauschalen Schlüsselwortregeln; Schutz von Listen, Überschriften, URLs, Zahlen, Abkürzungen, Code und
bewusster Zeichensetzung; keine doppelten Zeichen, keine Konflikte mit 104.

**Einrichtung des Owners (Grundlage jeder Zeile):** Xiaomi 15, Android 16; Layouts `de` (QWERTZ), `en-US`, `es`, keines mit
zweiter Sprache; Englisch tippt er meist auf `de`. Korrekturstufe „Behutsam“ (**„Richtige Wörter nie ändern“ an**),
satzbezogene Autokorrektur an, „Rückgängig gemachte Korrekturen merken“ an, personalisierte Vorschläge an, großer deutscher
Tipp-Verlauf. Leertaste ↑ = Rückgängig (sendet Strg+Z an die App), ← → Cursor.

## 1. Was der Owner will, und was dem widerspricht

**Will:** Fehler, die erst der Rest des Satzes zeigt („Ich haben“ ist erst falsch, wenn klar ist, dass „Ich“ das Subjekt
ist), verschwinden ohne eigenes Zutun, so wie die Autokorrektur ein einzelnes Wort repariert. Satzzeichen, die er beim
schnellen Tippen weglässt (Komma vor „dass“, Punkt vor dem Absatz), stehen danach richtig da.

**Widerspricht dem (Antiprompt):**
- Text, der sich ändert, während er weitertippt, oder ein Wort, das er gerade überschreibt, wird von einer alten Prüfung
  zurückgesetzt.
- Eine Änderung, die er nicht bemerkt und nicht rückgängig machen kann, oder die nach dem Rückgängig-Machen wiederkommt.
- „Korrekturen“ an Namen, Slang („Digga“, „wallah“), englischen oder spanischen Einschüben, Dialekt oder Stil
  („Das macht kein Sinn“ ist Umgangssprache, aber nicht jede Umgangssprache ist ein Fehler).
- Ein Komma oder Punkt doppelt, in einer Liste, in einer URL, nach einer Zahl, im Chat-Eingabefeld beim Senden.
- Spürbar langsameres Tippen, ruckelnde Leertaste.
- Eine Liste von Sonderfällen, die genau die sieben Beispiele abdeckt und sonst nichts.

## 2. Der gemeinsame Mechanismus (rückwirkende, geprüfte Änderung)

**Auslöser:** Nach jedem Tastendruck, der ein Wort abschließt (Leerzeichen, Satzzeichen, Enter mit Zeilenumbruch), liest
`LatinIME.onEvent` nach der Verarbeitung der Taste den Text vor der Schreibmarke aus dem Zwischenspeicher von
`RichInputConnection` (bis 300 Zeichen, nur der aktuelle Absatz zählt). Das ist eine Kopie im Speicher, kein Aufruf an die
App, solange der Zwischenspeicher reicht. Nichts wird geloggt.

**Prüfung im Hintergrund:** Die Satzprüfung (rein, `agent-core`) läuft auf dem Vorschlags-Thread (`InputLogicHandler`,
derselbe Thread wie die Wortvorschläge, also kein gleichzeitiger Zugriff auf Wörterbücher). Ergebnis: höchstens eine
Änderung, beschrieben als „ab Position s steht heute A bis zur Schreibmarke, danach soll dort B stehen“ (A und B enden beide
an der Schreibmarke). 104 und 105 laufen nacheinander auf demselben Text: 105 sieht den von 104 schon korrigierten Satz,
beide Änderungen werden zu **einer** Ersetzung zusammengefasst (eine Rücknahme nimmt beide zurück).

**Anwenden nur bei unverändertem Stand:** Das Ergebnis geht zurück auf den Haupt-Thread. Angewendet wird nur, wenn seit dem
Auslöser keine Taste gedrückt wurde (Zähler je Taste), kein Wort zusammengesetzt wird, nichts markiert ist, die Schreibmarke
dort steht, wo sie stand, und die App an der Stelle noch genau A zeigt. Ersetzt wird mit
`RichInputConnection.replaceTextBeforeCursorIfUnchanged` (bestehend, Etappe 24f/42: liest den Text in der App selbst noch
einmal, ersetzt mit einer Region und einem Commit, prüft danach). Sonst wird verworfen; beim nächsten Wortende wird der
Satz neu geprüft. Die Schreibmarke steht danach am Ende von B, also an derselben logischen Stelle; Text nach der
Schreibmarke bleibt unberührt (Cursor mitten im Text).

**Rücknahme:** Die Rücktaste **direkt danach** stellt A wieder her (eine Einheit, wie die Rücknahme einer Autokorrektur;
Einstellung „Rücktaste macht Autokorrektur rückgängig“ gilt auch hier). Das zählt als Ablehnung: jedes geänderte Wortpaar
geht an `RejectedCorrections.remember` (Gewichte von 107, mit „Rückgängig gemachte Korrekturen merken“ oder personalisierten
Vorschlägen) und in ein Feldgedächtnis (nie wieder dasselbe Paar in diesem Feld). Jede andere Taste oder eine
Cursorbewegung beendet das Rücknahmefenster. Eine spätere eigene Rückänderung (Owner tippt das alte Wort wieder hin) sieht
die nächste Prüfung als „Paar schon einmal angewendet, jetzt wieder alt“ und zählt sie ebenfalls als Ablehnung, statt neu zu
korrigieren. Strg+Z/↑ (Undo der App) bleibt Sache der App.

**Nie:** in Passwort- und anderen sensiblen Feldern (wie die Autokorrektur: `mInputAttributes`, `mIsPasswordField`), ohne
eingeschaltete Autokorrektur, bei Inkognito, im Sperrzustand (`LockState`), beim Gestenschreiben in diesem Wort, in einem
privaten Text (Etappe 44/45), wenn der Satz zur anderen Sprache neigt, in Feldern mit URL, E-Mail, Zahl. Alles offline.

**Kosten:** Haupt-Thread: Kopie des Texts und ein Post je Wortende; Prüfung typisch unter 1 ms im Hintergrund. Ziel:
Leerzeichen-Latenz Median +<2 ms, p95 +<8 ms gegenüber `main` 9228c5a0.

## 3. Etappe 104: Regeln (allgemein, je mit Nachweis „eindeutig“)

Daten: Verbformen aus UniMorph Deutsch (dieselbe festgeschriebene Quelle wie die Verbliste von Etappe 72, CC BY-SA 3.0;
nur die vom regelmäßigen Muster abweichenden Formen, ergänzt um „sein“ und „tun“, die UniMorph fehlen), Nomen mit Genus,
Kasus und Numerus aus dem deutschen Wiktionary (Datensatz `german-nouns`, CC BY-SA 4.0, nur Grundformen, die das deutsche
Hauptwörterbuch kennt). Geschlossene Wortklassen (Pronomen, Artikel, Präpositionen mit Kasus, Nebensatzeinleiter) stehen im
Code; das ist Grammatik, keine Liste von Fehlern. Ein Wort, das in den Daten fehlt oder mehrdeutig ist, wird nie geändert.

**Gemeinsame Bedingungen jeder Änderung:** nur Deutsch als Tastatursprache; die Satzsprache neigt nicht zur anderen Sprache
(`SentenceLanguage`); das geänderte Wort ist kein eigenes Wort (`Memory`), kein Slang (`InformalWordsStore`), kein
behaltenes Fremdwort (`ForeignWords`), kein gewöhnliches Wort einer anderen aktivierten Sprache, kein abgelehntes Paar; es
gibt genau einen Kandidaten. Entscheidung frühestens, wenn nach dem betroffenen Wort ein weiteres Wort oder ein Satzzeichen
steht (ein Wort Vorlauf).

- **R1 Subjekt–Verb-Kongruenz.** Ein Satz (ab Satzanfang) mit Verb an zweiter Stelle: Vorfeld genau ein Wort (Pronomen,
  Adverb, „das/dies“), dann das finite Verb V. Subjektkandidaten sind alle Wörter des Satzes, die Nominativ sein können
  (Personalpronomen; Artikel+Nomen mit Nominativ-Lesart; „das“, „es“, „sie“ mit allen Lesarten). Ist ein Kandidat **nur**
  Nominativ (ich, du, er, wir, ihr als Pronomen), ist er das Subjekt. Sonst müssen alle Kandidaten auf dieselbe Person und
  Zahl zeigen. Passt V zu keinem Kandidaten und ergibt sich genau eine passende Form desselben Verbs, wird V ersetzt.
  Hängt die Entscheidung an einem mehrdeutigen „es/sie/das“ nach dem Verb, braucht es danach ein Wort, das kein Nomen,
  Artikel oder Pronomen ist (sonst könnte das echte Subjekt noch kommen: „Dann machen es die anderen“). Kopula (sein, werden,
  bleiben, heißen) nur mit Personalpronomen 1./2. Person oder er. Konjunktiv I („Er habe“) gilt nur als richtig, wenn der
  vorige Satz ein Verb des Sagens enthält (indirekte Rede). Beispiele: „Ich haben das“ → „habe“, „Mir gehen es gut“ →
  „geht“, „Er habe mir“ → „hat“, „Das machen kein Sinn“ → „macht“.
- **R2 Gleich klingendes Wort am Verbplatz.** Steht nach einem Satzanfangs-Pronomen statt eines Verbs eine Präposition oder
  Konjunktion, und gibt es genau eine gleich klingende Form (Auslaut d/t, s/ß/ss, ä/e), die die passende Verbform ist,
  wird ersetzt: „Ihr seit schon“ → „seid“. Nicht bei Ellipsen ohne passende Form („Wir seit gestern hier“ bleibt).
- **R3 das/dass.** „das“ direkt gefolgt von einem Personalpronomen und vor sich ein kleingeschriebenes Verb oder Komma
  nach einem Verb (also weder Artikel noch Relativpronomen möglich), mit einem finiten Verb später im Nebensatz → „dass“.
- **R4 Kasus und Genus nach Präposition.** Präposition mit festem Kasus (mit, nach, bei, seit, von, zu, aus → Dativ; durch,
  für, gegen, ohne, um → Akkusativ; Wechselpräpositionen Dativ oder Akkusativ), danach Artikel und **direkt** das Nomen (kein
  Adjektiv dazwischen): passt der Artikel zu keiner Lesart des Nomens im verlangten Kasus und gibt es genau eine passende
  Artikelform, wird der Artikel ersetzt. Ohne Präposition nur, wenn der Satz ein reines Nominativ-Subjekt und ein
  Dativpronomen hat und das Verb keine Kopula ist (dann ist das Nomen Akkusativ). Genus mehrdeutig (Wiktionary nennt zwei)
  → nichts.
- **R5 Großschreibung nach Präposition.** Kleines Wort direkt nach einer Präposition (oder Präposition + Artikel), das das
  Hauptwörterbuch so nicht kennt oder das nur eine finite Verbform ist, dessen großgeschriebene Form aber ein Nomen mit
  Lesart im verlangten Kasus ist: „zu hause“ → „zu Hause“.
- **R6 Fehlerkette über zwei Wörter.** Artikel + zwei großgeschriebene Wörter, bei denen der Artikel zum ersten nur als
  Genitiv passt, obwohl Präposition oder Satz einen anderen Kasus verlangen (vorangestellter Genitiv mit Artikel kommt im
  Chat praktisch nicht vor): Zusammenschluss beider Wörter gegen die Nomen-Daten, Tippabstand (Damerau-Levenshtein ohne
  Leerzeichen und Groß/klein) höchstens 1 je 3 Buchstaben, gleicher Anfang (2 Buchstaben), genau ein Kandidat, Artikel
  passt im verlangten Kasus, Kandidat im Hauptwörterbuch mindestens 100. „mit der Polen Zeit“ → „mit der Polizei“,
  „die Haus Tür“ → „die Haustür“.

**Stand der sieben Beispiele (vor dem Bau, aus den Daten):**

| Beispiel | Ergebnis | Warum |
| --- | --- | --- |
| Mir gehen es gut | Mir geht es gut | R1 („es“ einziges Subjekt, „gut“ schließt ab) |
| Ich haben das nicht gewusst | Ich habe … | R1 („Ich“ nur Nominativ) |
| Er habe mir ein Gefallen getan | Er hat mir ein Gefallen getan | R1 ja; „einen“ **offen**: Wiktionary kennt „der Gefallen“ und „das Gefallen“, „ein Gefallen“ ist als Akkusativ Neutrum grammatisch |
| Ich bin Zeit gestern wieder zu hause | Ich bin Zeit gestern wieder zu Hause | R5 ja; „Zeit“ → „seit“ **offen**: grammatisch möglich („Zeit“ als Nomen), nur eine Wortpaar-Wahrscheinlichkeit („seit gestern“) könnte es entscheiden, die die Wörterbücher nicht enthalten (geprüft: `main_de.dict` hat keine Bigramme) |
| Ihr seit schon da | Ihr seid schon da | R2 |
| Das machen kein Sinn | Das macht kein Sinn | R1 ja; „keinen“ **offen**: „Das macht kein Mensch“ ist dieselbe Struktur und richtig (Objekt vorn) |
| mit der Polen Zeit | mit der Polizei | R6 |

Die drei offenen Teile sind Kollokationen, keine Grammatik. Lösbar nur mit Wortpaar-Häufigkeiten: entweder aus dem Tipp-
Verlauf des Owners (enthält Paare, aber das eben getippte falsche Paar wird beim Abschicken mitgelernt; die Schwelle müsste
auf seinem Gerät geeicht werden, dazu bräuchte es eine neue Lese-Schnittstelle in `ExpandableBinaryDictionary`) oder aus
einem mitgelieferten Bigramm-Bestand (z. B. Wikipedia-Auszug, CC BY-SA, geschätzt 1–3 MB für Funktionswort-Paare).
Vorschlag an den Owner, nicht gebaut.

## 4. Etappe 105: Satzzeichen

- **P1 Komma vor Nebensatz.** Ein Nebensatzeinleiter (dass, weil, ob, obwohl, wenn, falls, sobald, nachdem, bevor, damit,
  während, seit/seitdem, bis, als, wie, indem, sodass, da) mitten im Satz ohne Komma davor, **und** der Nebensatz zeigt
  Verbletztstellung: direkt nach dem Einleiter ein Subjekt (Personalpronomen oder Artikel+Nomen) und als bisher letztes Wort
  ein finites Verb, das zu diesem Subjekt passt. Dann Komma direkt vor dem Einleiter (bei „ohne dass“, „als ob“, „so dass“,
  „statt dass“ vor dem ersten Wort). Nicht nach „und/oder/sowie“, nicht am Satzanfang. „Ich weiß dass er kommt“ → „Ich weiß,
  dass er kommt“; „Ich bin damit zufrieden“ bleibt (kein Subjekt, kein finites Verb danach).
- **P2 Komma nach vorangestelltem Nebensatz.** Satz beginnt mit Einleiter, Subjekt, …, finites Verb V1, direkt danach ein
  weiteres finites Verb V2 → Komma zwischen V1 und V2: „Wenn du kommst bringe Kuchen mit“ → „…kommst, bringe…“.
- **P3 Punkt oder Fragezeichen vor dem Absatz.** Nur wenn Enter in einem mehrzeiligen Feld einen Zeilenumbruch eingefügt hat
  (kein Senden: Felder mit Aktion bekommen nie etwas). Die abgeschlossene Zeile ist ein Satz: mindestens drei Wörter, ein
  finites Verb, letztes Zeichen ein Buchstabe. Fragezeichen, wenn die Zeile mit einem finiten Verb gefolgt von einem
  Subjektpronomen beginnt („Kommst du morgen“) oder mit einem Fragewort direkt vor dem finiten Verb („Wie geht es dir“);
  sonst Punkt. Eingefügt **direkt am letzten Wort** (vor einem etwaigen Leerzeichen).
- **Schutz:** Zeile beginnt mit Aufzählungszeichen (-, *, •, 1., 1), a)); Zeile ohne finites Verb (Überschrift, Gruß,
  „Liebe Grüße“); letztes Wort ist Zahl, URL, E-Mail, Abkürzung (z. B., usw., Dr., Großbuchstabenkürzel), Emoji oder
  enthält Ziffern; Code (Backticks, Klammern, =, ;, ->, {}); schon ein Satzzeichen am Ende oder an der Kommastelle (nie
  doppelt); ein Zeichen, das der Owner an dieser Stelle gelöscht hat (Feldgedächtnis), kommt nicht wieder; einzeilige
  Felder (Suche, Chat-Eingabe mit Senden) nie. Englisch/Spanisch: nur P3 mit Punkt (Fragezeichen nur Englisch bei
  Hilfsverb/Fragewort am Anfang), keine Kommaregeln.

## 5. Zusammenspiel mit vorhandenen Funktionen

- **Autokorrektur (Wort) und Etappe 72/101/107:** unverändert. Die Nachkorrektur läuft danach auf dem schon korrigierten
  Text. Die Rücknahme der Autokorrektur des letzten Worts (`mLastComposedWord`) bleibt; eine Nachkorrektur, die nach dem
  Leerzeichen kam, ist die jüngere Änderung und wird von der ersten Rücktaste zurückgenommen.
- **„Richtige Wörter nie ändern“:** gilt für die Wortkorrektur beim Tippen. Die Nachkorrektur ändert ein richtiges Wort nur,
  wenn der Satz es grammatisch erzwingt; sie hat einen eigenen Schalter (Entscheidung, weil alle sieben Beispiele aus
  richtigen Wörtern bestehen und der Owner sonst nichts davon hätte).
- **Feldbeobachter (Etappe 49):** eine eigene Änderung der Tastatur ist keine Korrektur des Owners: nach dem Ersetzen
  `untrusted()`, damit „gehen → geht“ nicht als Tippfehler gelernt wird.
- **Doppel-Leertaste, Satzzeichen-Tausch, Phantom-Leerzeichen:** der Leerzeichen-Zustand bleibt erhalten (die Ersetzung
  ändert nur Text vor der Schreibmarke, die Endung bleibt gleich).
- **Befehle (`@ki` …), Ablage, Diktat, Zeichen-Kunst:** eingefügter Text ist kein getippter Satz; geprüft wird nur nach
  einer getippten Wortende-Taste.

## 6. Einstellungen

„Tippen & Korrektur → Regeln“: **„Nachkorrektur im Satz“** (104) und **„Satzzeichen ergänzen“** (105), beide Standard an
(Owner zu 105: „Es wäre schön, wenn zukünftig noch Satzzeichen automatisch ersetzt werden“), beide brauchen die
Autokorrektur. Diagnose „Korrektur & Sprache“: Zähler angewendet / zurückgenommen / verworfen (veraltet), nur Zahlen.

## 7. Messung

Messpunkte nur mit `adb shell setprop log.tag.SHLatency DEBUG` aktiv (sonst eine Abfrage je Feldstart): je Taste Zeit vom
Berührungsende (`MotionEvent.getEventTime`) bis zum Ende von `onEvent`, getrennt für Buchstabe und Leerzeichen, und die Dauer
der Prüfung im Hintergrund; nur Zahlen. Frames mit `dumpsys gfxinfo <paket> framestats` beim Tippen eines langen Texts im
Emulator. Erster Commit nur Messpunkte → Vorher-Werte auf `main`-Stand.

## 8. Abnahme-Zeilen

| # | Ausgangslage beim Owner | Aktion | Erwartung | Gegenfall | Nachweis |
| --- | --- | --- | --- | --- | --- |
| 1 | de, Behutsam, Textfeld | „Ich haben das nicht gewusst“ Taste für Taste | „Ich habe das nicht gewusst“, Schreibmarke am Ende | „Sie haben das“ bleibt | Kern `SentenceRepairTest`, Robolectric `RetroCorrectionTest`, Emulator `retro_examples` |
| 2 | dasselbe | „Mir gehen es gut “ | „Mir geht es gut “ | „Dann machen es die anderen“ bleibt | Kern, Robolectric, Emulator |
| 3 | dasselbe | „Ihr seit schon da “ | „Ihr seid schon da “ | „Wir seit gestern hier“ bleibt | Kern, Robolectric, Emulator |
| 4 | dasselbe | „Er habe mir ein Gefallen getan “ | „Er hat mir ein Gefallen getan “ („einen“ offen) | „Sie sagte, er habe …“ bleibt | Kern, Robolectric |
| 5 | dasselbe | „Ich bin Zeit gestern wieder zu hause “ | „… zu Hause “ („seit“ offen) | „zu hausen“ (Infinitiv) bleibt | Kern, Robolectric |
| 6 | dasselbe | „Das machen kein Sinn “ | „Das macht kein Sinn “ („keinen“ offen) | „Das machen wir“ bleibt | Kern, Robolectric |
| 7 | dasselbe | „Ich war mit der Polen Zeit “ | „Ich war mit der Polizei “ | „mit den Polen“ bleibt | Kern, Robolectric, Emulator |
| 8 | Nachkorrektur kam eben | Rücktaste | alter Text zurück, Schreibmarke am Ende, Paar abgelehnt; nächstes Wortende ändert es nicht wieder | zweite Rücktaste löscht normal | Robolectric, Emulator |
| 9 | Owner tippt schnell weiter | nächster Buchstabe vor dem Ergebnis | keine Ersetzung mitten im neuen Wort; beim nächsten Leerzeichen neu geprüft | – | Robolectric (Ergebnis verzögert) |
| 10 | Cursor mitten im Text | Satz vor dem Cursor tippen | nur Text vor dem Cursor ändert sich, Text danach bleibt | – | Robolectric |
| 11 | Passwortfeld, Inkognito, gesperrt | „Ich haben das “ | nichts | – | Robolectric |
| 12 | Slang, Namen, Englisch | „Digga was geht“, „Max haben wir gesehen“?, „I have das“ | nichts | – | Kern, Robolectric |
| 13 | mehrzeiliges Feld | „Ich weiß dass er kommt“ + Enter | „Ich weiß, dass er kommt.⏎“ (eine Einheit) | „Ich weiß, dass er kommt.“ getippt: nichts doppelt | Kern `AutoPunctuationTest`, Robolectric, Emulator |
| 14 | mehrzeiliges Feld | „Kommst du morgen“ + Enter | „Kommst du morgen?⏎“ | „- Milch kaufen“ + Enter bleibt | Kern, Robolectric, Emulator |
| 15 | einzeiliges Feld (Senden) | „Ich komme morgen“ + Senden | kein Punkt | – | Robolectric, Emulator |
| 16 | 104 und 105 zusammen | „Ich glaube das er kommt“ + Enter | „Ich glaube, dass er kommt.“ | – | Kern, Robolectric |
| 17 | Owner löscht den Punkt | Rücktaste nach Enter | Punkt weg (Umbruch bleibt), kommt in diesem Feld nicht wieder | – | Robolectric |
| 18 | langer Text | 300 Zeichen tippen | Leerzeichen Median +<2 ms, p95 +<8 ms; keine zusätzlichen verworfenen Frames | – | Emulator-Messung vorher/nachher |

## 9. Grenzen (bewusst)

- Keine Adjektivendungen (Artikel nur direkt vor dem Nomen), keine Relativsatz- und Infinitivkommas, keine Kommas in
  Aufzählungen, keine Großschreibung außerhalb von Präpositionalgruppen, keine Satzprüfung im Spanischen und Englischen
  außer P3.
- Die drei offenen Kollokationen (Abschnitt 3).
- Gestenschreiben: geprüft wird nach dem Wortende genauso; die Rücknahme gilt nur für die Nachkorrektur.
