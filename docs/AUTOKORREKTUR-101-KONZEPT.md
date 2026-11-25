# Autokorrektur nach echten Fehlfällen (Etappe 101): Konzept

Auftrag des Owners (2026-11-25): „Die Autokorrektur spackt immer noch, fast alles gefühlt zufällig“ mit zehn Fehlfällen (Wortlaut im Auftrag des
Leads) und dem Gegenbefund „korrigiert auch weit nicht alles“. Baut auf 90 (Konzept), 91 (`CorrectionGuards`), 92 (`passiveOnlyTarget`),
51 (`ForeignWords`) und 60 (`SentenceLanguage`) auf. Arbeitsweise: `.claude/skills/product-initiative/SKILL.md`.

**Einrichtung des Owners (Grundlage jeder Zeile unten):** Stufe BEHUTSAM („Richtige Wörter nie ändern“ an, Umlaut-Wörter behalten an, Stärke
0,24), Buchstabendreher an, satzbezogene Autokorrektur an; Layouts `de` (QWERTZ), `en-US`, `es`, keines mit zweiter Sprache; Englisch und Slang
tippt er meist auf `de`; „Personalisierte Vorschläge“ an, „Automatisch zum persönlichen Wörterbuch hinzufügen“ an, „Meine Tippfehler lernen“ an.
Auf `de` ist damit **nur das deutsche Hauptwörterbuch** in der Engine (`DictionaryFacilitatorImpl.getUsedLocales`, Zeile 677: Layoutsprache plus
zweite Sprachen des Layouts).

**Wörterbuch-Befunde** (gelesen aus `app/src/main/assets/dicts/main_{de,en-US,es}.dict` mit dem Leser aus `tools/build_informal_words.py`,
Häufigkeit 0–255, „–“ = unbekannt):

| Wort | de | en-US | es | | Wort | de | en-US | es |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| übermittle | – | – | – | | Übermittler | 44 | – | – |
| marschiere | – | – | – | | marschierte | 107 | – | – |
| faselte | – | – | – | | fädelte | 59 | – | – |
| fiedel | – | – | – | | Friedel | 78 | – | – |
| god / God | – | 124 / 129 | – | | Tod | 142 | 76 | – |
| sucks | – | 79 | – | | Ducks / ducks | 89 | 102 | – |
| cooled | – | 109 | – | | cooler | 57 | 105 | – |
| lame | – | 86 | 68 | | Lamé | 38 | – | – |
| alr | nur „ALR“ 69 | – | – | | aif | nur „AIF“ 77 | nur „AIF“ 87 | – |
| gah | – | – | – | | gab | 162 | 53 | – |
| icj | – | nur „ICJ“ 78 | – | | ich | 139 | – | – |
| mur | **56** | – | – | | nur | 177 | – | – |
| mocb, löschr | – | – | – | | noch / lösche / löscht | 176 / 34 / 80 | – | – |
| i | – | 0 (+ „I“ 196) | – | | but / story / cool | 97 / 75 / 78 | 181 / 151 / 118 | – |

## 1. Muster und belegte Ursachen

### A. Deutsche Verbformen (Fälle 1, 2, 4, 5)

**Ursache (Hypothese des Leads bestätigt):** „übermittle“, „marschiere“, „faselte“, „fiedel“ kennt kein Hauptwörterbuch. Ein unbekanntes getipptes
Wort darf die Engine immer ersetzen (`Suggest.shouldBeAutoCorrected`, `Suggest.kt:499-505`, `typedWordInfo?.mSourceDict == null`). Der vorhandene
Verbschutz von Etappe 72 greift nicht: `GermanVerbAgreement.prefer` leitet die Stämme aus dem **ersten Vorschlag** ab (`GermanVerbAgreement.kt:80-81`,
nur ganz klein geschriebene Wörter) und kennt kein Präteritum: „Übermittler“ (groß) liefert keinen Stamm, „marschierte“ liefert den Stamm
„marschiert“, „fädelte“ einen anderen Stamm als „faselte“. `CorrectionGuard.allowsAutoCorrection` (`CorrectionMemory.kt:40-49`) schützt nur
Wörterbuchwörter.

**Regel A (nur Schutz, gilt immer):** Ist die Tastatursprache Deutsch und kennt das deutsche Hauptwörterbuch das getippte Wort nicht, ist es aber eine
**regelmäßige Form eines Infinitivs der mitgelieferten Verbliste** (`german_verb_lemmas.txt`, 6.588 Verben), wird es nicht still ersetzt; die
Korrektur bleibt Vorschlag. Erkannte Formen (`GermanVerbForms`, rein, in `agent-core`): Stamm + e/st/t/en/te/test/ten/tet; bei Stämmen auf d/t
(und m/n nach Konsonant) + e/est/et/ete/etest/eten/etet; bei -eln/-ern-Verben Stamm (umgangssprachlich „ich fiedel“, „ich wander“), + e/st/t/n/te/
test/ten/tet und die Kurzform „fiedle“, „wandre“. Großer Anfangsbuchstabe am Satzanfang zählt wie klein. Ausnahme: Kürzel und Whitelist-Einträge.
Auch die Buchstabendreher-Regel (75) und die Nachbartasten-Regel (D) lassen solche Formen stehen.

**Gegenfälle:** „machre“ (kein Infinitiv „machren“/„machern“) und „gehte“ … „gehte“ ist die einzige bekannte Schwäche: Die Regel kennt keine
unregelmäßigen Verben und hält „gehte“ deshalb für ein Präteritum von „gehen“. Es wird **geschützt, nicht erzeugt** (bleibt stehen, „ging“ wird nicht
vorgeschlagen). Sie macht nie ein Wort zu einer Verbform.

### B. Wörter der anderen eigenen Sprachen (Fälle 3, 6, 7)

**Ursache (bestätigt):** Auf `de` gibt es kein englisches Wörterbuch. „god“, „sucks“, „cooled“, „lame“ sind für die Engine unbekannt und werden ersetzt.
Etappe 60 (`sentenceIsOfOtherLanguage`, `Suggest.kt:455`) fragt nur die **aktiven** Wörterbücher (`mainLocalesKnowing`, `DictionaryFacilitatorImpl.kt:590`),
Etappe 51 (`ForeignWords`) schützt erst, nachdem der Owner ein Wort zweimal an verschiedenen Stellen behalten oder einmal zurückgenommen hat.

**Nachschlage-Zugriff:** neues `agent/OtherLanguageDictionaries.kt`. Es öffnet die Hauptwörterbücher der **anderen aktivierten Layouts** (Sprachen aus
`ForeignWords.enabledLocales`, ohne Layoutsprache und zweite Sprachen des Layouts) nur lesend mit `DictionaryFactory.createMainDictionaryCollection`
(ohne Emoji), **auf dem Hintergrund-Thread von `ForeignWords`** (`ForeignWords.worker`), nie auf dem Tipp-Thread. Bis sie offen sind, gilt das alte
Verhalten (jede Abfrage sagt „unbekannt“). Speicher: die Wörterbücher sind per `mmap` eingeblendet (en-US 2,9 MB, es 1,4 MB), das Ablesen kostet eine
native Abfrage je Form. Geschlossen wird mit `ForeignWords.closeDictionaries` (wenig Speicher, Tastatur im Hintergrund, Wörterbuch geändert, lange
unbenutzt); danach wird bei Bedarf neu geöffnet. Lesen nach dem Schließen ist sicher (`ReadOnlyBinaryDictionary` sperrt, ein geschlossenes Wörterbuch
antwortet „unbekannt“).

**Satzbeleg (neu, für B, D und Fall 10):** Für jedes der bis zu drei vorigen Wörter des Satzes zählt, welche Sprache es **deutlich** führt
(`SentenceLanguage.evidenceOfFrequencies`): nur eine Sprache kennt es, oder beide und eine mit mindestens 60 Punkten mehr. Gemessen an den echten
Wörterbüchern führen dann Englisch z. B. „and, is, it, me, not, are, can, he, but, love, just, go, its“, Deutsch „die, den“; „was“ (160/201) und „cool“
(78/118) bleiben neutral. Etappe 60 selbst bleibt unverändert.

**Regel B (nur Schutz, gilt immer):** Das getippte Wort kennt kein aktives Hauptwörterbuch (wie getippt, klein oder mit großem Anfangsbuchstaben),
aber das Hauptwörterbuch einer anderen aktivierten Sprache kennt es (wie getippt, klein oder groß am Anfang; eine reine Abkürzung „ICJ“ zählt für
„icj“ nicht). Es wird **nicht still ersetzt**, wenn
1. die Korrektur kein Umlaut-/Akzent-Zwilling ist (ohne Akzente gleich: „fur“→„für“ bleibt erlaubt, Etappe 46/51), und
2. der Satz nicht eher deutsch ist (mehr vorige Wörter führt die Tastatursprache als die andere Sprache → kein Schutz), und
3. das Wort in der anderen Sprache gewöhnlich ist: Häufigkeit ≥ 60, wenn der Satz schon zur anderen Sprache neigt, sonst ≥ 100.

So: „God“ (124, erstes Wort) bleibt, „sucks“ (79) nach „God … that“ bleibt (zwei englische gegen „damit“), „cooled“ (109) nach dem neutralen „thats“
bleibt. „lame“ (86) ohne Satzbeleg schützt B nicht; „Lamé“ verhindert Regel C.

### C. Großbuchstaben- und Akzent-Ziele (Fälle 7, 8 und „auf → AIF“)

**Ursache (Hypothese des Leads widerlegt):** „AIF“ kommt nicht aus dem Verlauf oder dem persönlichen Wörterbuch, sondern aus dem **deutschen
Hauptwörterbuch** (Abkürzung „AIF“, 77; ebenso „ALR“, 69, und „Lamé“, 38). Getippt hat der Owner „aif“ (Tippfehler für „auf“); die Korrektur
„aif“→„AIF“ ändert nur die Groß-/Kleinschreibung. Etappe 48 (`Suggest.kt:418-421`) sperrt so eine Korrektur nur, wenn das getippte Wort selbst bekannt
ist; Etappe 92 (`passiveOnlyTarget`) betrifft nur Ziele aus `TYPE_USER_HISTORY`/`TYPE_USER`, hier ist das Ziel `TYPE_MAIN`, und die Slang-Liste
schützt keine reinen Groß-/Kleinkorrekturen (`Suggest.kt:412-414`).

**Regel C (nur Schutz, gilt immer, in `CorrectionGuards.blocksTarget`, Veto in `shouldBeAutoCorrected` wie Etappe 91):** Kein stilles Ersetzen
- eines Worts mit Kleinbuchstaben durch ein Wort nur aus Großbuchstaben (mindestens zwei Buchstaben): „aif“, „alr“, „ok“ bleiben;
- durch ein deutsches Wort mit einem Akzent, den es im Deutschen nicht gibt (alles außer ä/ö/ü), wenn das getippte Wort diesen Akzent nicht hat:
  „lame“ bleibt, „Café“ getippt bleibt korrigierbar.

Ausnahme: Kürzel des Owners und Whitelist-Einträge des Wörterbuchs (die verlangt das Wörterbuch ausdrücklich, „i“→„I“).

**Folge für „aif“:** Es bleibt „aif“, „auf“ steht in der Leiste. Ein automatisches „aif“→„auf“ baut 101 bewusst nicht: Kennt ein Wörterbuch das Wort als
Abkürzung, gilt es für die Buchstabendreher (Etappe 75, Test „bsi“/„BSI“) und für Regel D als bekannt; „alr“ würde sonst zu „alt“ (135).

### D. Zu wenig Korrektur („icj“, „mur“, „mocb“, „löschr“)

**Ursachen (teils belegt, teils offen):**
- **„mur“** steht im deutschen Hauptwörterbuch (56). Mit „Richtige Wörter nie ändern“ ist es damit ein richtiges Wort (`Suggest.kt:403-404`,
  `CorrectionMemory.kt:46`). Nicht der Verlauf schützt es, sondern BEHUTSAM.
- **„icj“** kennt kein Hauptwörterbuch auf `de`. Steht es im Tipp-Verlauf (jedes abgeschickte Wort wird gelernt) oder, nach drei Mal, im persönlichen
  Wörterbuch (`DictionaryFacilitatorImpl.addToPersonalDictionaryIfInvalidButInHistory`, Zeile 402-424, Gewicht 250), ist es für die Engine
  „bekannt“: `typedWordInfo` ist dann ein Treffer aus diesen Quellen, `allowsToBeAutoCorrected` geht über die Punkte (`Suggest.kt:508-517`) und
  `hasAutoCorrection` über den Vergleich mit dem getippten Wort (`Suggest.kt:561-584`). Ob das bei ihm so ist, zeigt nur sein Gerät.
- **„mocb“** (zwei Tasten daneben) und **„löschr“** (Kandidaten „löscht“ 80, „lösche“ 34, beide selten) haben keinen eindeutigen häufigen Kandidaten.

**Herkunft im persönlichen Wörterbuch:** Automatisch hinzugefügte und von Hand eingetragene Wörter sind nicht zu unterscheiden (beide Gewicht 250 über
`UserDictionary.Words.addWord`; `PersonalDictionaryScreen.kt:109-110`, `DictionaryFacilitatorImpl.kt:422`). Darum die vorsichtigere Regel: Was das
persönliche Wörterbuch kennt, bleibt geschützt; nur der Tipp-Verlauf schützt nicht mehr.

**Regel D (korrigiert, an „satzbezogene Autokorrektur“):** Neue Nachbartasten-Regel neben den Buchstabendrehern (`NeighborKeys`, rein, `agent-core`;
`Suggest.applyNeighborKey`). Ein Wort aus 3–5 Buchstaben (klein; groß nur der erste am Satzanfang), das **kein** Hauptwörterbuch irgendeiner aktiven
Sprache in irgendeiner Schreibung kennt (auch nicht als Abkürzung), auch nicht das persönliche Wörterbuch, die Kontakte, die Apps oder das Wörterbuch
einer anderen aktivierten Sprache (wie getippt, klein, groß am Anfang), wird korrigiert, wenn genau **ein** Wort der Tastatursprache entsteht, indem ein
Buchstabe durch eine Nachbartaste ersetzt wird (QWERTZ für Deutsch, sonst QWERTY), und dieses Wort sehr häufig ist (Deutsch ≥ 130: „ich“ 139, „nur“
177, „noch“, „und“, „auf“; Englisch ≥ 150; sonst ≥ 140). Nicht bei eigenen Wörtern, Slang, behaltenen Fremdwörtern, gemerkten Ablehnungen, Verbformen
(A), wenn der Satz zur anderen Sprache neigt, und nicht gegen eine andere Korrektur der Engine. Der Tipp-Verlauf zählt nicht als „bekannt“ (wie bei 75).

**Nicht gebaut: „mur“ → „nur“ trotz BEHUTSAM.** Gegenprobe am echten Wörterbuch: 68 seltene deutsche Wörter (Häufigkeit ≤ 60) haben genau einen sehr
häufigen Nachbartasten-Zwilling, darunter echte Wörter wie „gebt“→„geht“, „hafte“→„hatte“, „beben“→„neben“, „riesen“→„diesen“, „Bein“-klein
„bein“→„beim“, „widder“→„wieder“. Eine Ausnahme von „Richtige Wörter nie ändern“ würde genau das verletzen, was BEHUTSAM verspricht. „mur“ bleibt,
„nur“ steht in der Leiste. Entscheidung des Owners nötig, falls er das anders will.

### Fall 9 „gah“ → „gab“

„gah“ kennt kein Wörterbuch (auch nicht die Slang-Liste); es steht am Satzanfang, ein Satzbeleg ist noch nicht möglich. Die Engine korrigiert es heute,
Regel D täte es ebenso („gab“ 162). Offen: nur eine Liste kann es schützen (Slang-Liste wird aus einem Datenpaket erzeugt, nicht von Hand ergänzt)
oder das Gedächtnis (eigenes Wort nach drei Mal) bzw. eine Rücknahme mit „Rückgängig gemachte Korrekturen merken“.

### Fall 10 „i“ bleibt klein (Nachtrag des Owners)

**Ursache:** Auf `de` kennt kein aktives Wörterbuch „i“; auf `en-US` sperrt BEHUTSAM die Whitelist-Korrektur „i“→„I“ (`CorrectionMemory.kt:46`,
Behebung 1 aus Etappe 90 ist nicht gebaut, Test „reported finding 3“).

**Regel (korrigiert, an „satzbezogene Autokorrektur“, `EnglishI` in `agent-core`, `Suggest.applyEnglishI`):** Getippt „i“, „i'm“, „i'll“, „i'd“,
„i've“ (auch mit ’) wird zu „I“, „I'm“ … auf dem englischen Layout immer, auf jedem anderen Layout nur, wenn Englisch eine aktive oder andere
aktivierte Sprache ist und der Satz Englisch belegt: mindestens ein voriges Wort führt Englisch (Satzbeleg oben), keines die Tastatursprache.
„im“, „ill“, „id“ werden nie berührt. Am Satzanfang und nach „ich“ bleibt „i“ klein (Dialekt „i hob“).

## 2. Abnahme-Zeilen

Ausgangslage aller Zeilen: Einrichtung des Owners wie oben, Layout `de`, wenn nicht anders genannt. Nachweis „Robolectric“ = `AutocorrectReportTest`
mit festen Engine-Treffern (Wörterbuchtyp, Sprache, Häufigkeiten wie oben) und eingesetztem Nachschlagen der anderen Sprachen; kein Gerätetest.

| # | Ausgangslage | Aktion | Erwartung | Gegenfall | Nachweis |
| --- | --- | --- | --- | --- | --- |
| 1 | Engine bietet „Übermittler“ (de) | „Ich übermittle“ + Leertaste | „übermittle“ bleibt, „Übermittler“ in der Leiste | „machre“ → Engine-Korrektur | Robolectric, `GermanVerbFormsTest` |
| 2 | Engine bietet „marschierte“ | „ich marschiere“ | bleibt | – | Robolectric |
| 4 | Engine bietet „fädelte“ | „ich faselte“ | bleibt | – | Robolectric |
| 5 | Engine bietet „Friedel“ | „ich fiedel“ | bleibt | „ich wander“ bleibt ebenso | Robolectric, `GermanVerbFormsTest` |
| 3a | Engine bietet „Tod“, en-US aktiviert | „God“ am Satzanfang | bleibt | „fur“ → „für“ (Zwilling) | Robolectric |
| 3b | dasselbe | „God damit that sucks“ | „sucks“ bleibt | „das its gut“: kein Schutz (Satz deutsch) | Robolectric, `OtherLanguageWordTest` |
| 6 | Engine bietet „cooler“ | „thats cooled“ | bleibt | – | Robolectric |
| 7 | Engine bietet „Lamé“ (de) | „thats lame“ | bleibt | „cafe“→„Café“ bei getipptem Akzent bleibt erlaubt | Robolectric, `CorrectionGuardsTest` |
| 8 | Engine bietet „ALR“ (de) | „alr“ | bleibt, auch nicht „alt“ | „ALR“ mit Feststelltaste: unverändert | Robolectric, `CorrectionGuardsTest` |
| C | Engine bietet „AIF“ (de) | „aif“ | bleibt „aif“, „auf“ in der Leiste | Kürzel/Whitelist in Großbuchstaben wird gesetzt | Robolectric |
| 9 | Engine bietet „gab“ | „gah“ am Satzanfang | **wie heute „gab“** (offen, siehe oben) | – | Robolectric hält das heutige Verhalten fest |
| D1 | „icj“ nur im Tipp-Verlauf, Engine ohne Korrektur | „tippe icj“ | „ich“ | „icj“ im persönlichen Wörterbuch: bleibt | Robolectric, `NeighborKeysTest` |
| D2 | „mur“ im Hauptwörterbuch (56) | „ich sehe mur“ | **bleibt** (BEHUTSAM) | – | Robolectric hält es fest |
| D3 | – | „mocb“, „löschr“ | keine neue Regel greift | – | `NeighborKeysTest` |
| D4 | en-US aktiviert, englischer Satz | „the cat is gah“ | D korrigiert nicht | deutscher Satz „und icj“ → „ich“ | Robolectric |
| 10a | de, en-US aktiviert | „cool story but i“ | „I“ | „i“ am Satzanfang und „ich glaube i“ bleiben klein | Robolectric, `EnglishITest` |
| 10b | en-US-Layout, BEHUTSAM | „and i“, „i'm“ | „I“, „I'm“ | „im“, „ill“, „cant“ bleiben | Robolectric (Test „reported finding 3“ dreht) |
| N | Netz | `SuggestTest`, `InputLogicTest` (Etappen 51/60/75), `TypingEquivalenceTest` | unverändert grün | – | GitHub-Lauf nach dem Push |

## 3. Kritiker vor dem Bau (selbst gespielt)

1. **Englisches Tippfehler-Wort am deutschen Satzanfang** („Wine Frage“, „Dad ist“, „His“ statt „Bis“): Regel B schützt ohne Satzbeleg ab Häufigkeit
   100; am echten Wörterbuch sind das 35 englische Wörter, die einen sehr häufigen deutschen Nachbartasten-Zwilling haben (his, have, did, due, air,
   wine, sins, dad …). Der Owner tippt so einen Tippfehler am Satzanfang und er bleibt stehen. **Entscheidung:** hingenommen; mitten im deutschen Satz
   gibt es fast immer ein Wort, das Deutsch führt, und dann greift B nicht. Die Schwelle 100 statt 60 hält die Zahl klein (bei 60 wären es 84).
2. **„aif“ bleibt stehen.** Der Owner wollte „auf“. Wortlaut „nie AIF“ erfüllt, Ergebnis trotzdem ein Tippfehler. **Entscheidung:** hingenommen und
   offen genannt (siehe C); die Alternative macht „alr“ zu „alt“ und bricht die Abkürzungsregel von 75.
3. **„icj“ steht beim Owner schon im persönlichen Wörterbuch** (drei Mal getippt, automatisch hinzugefügt): Regel D greift dann nicht, der Owner sieht
   keine Besserung. **Entscheidung:** vorsichtig bleiben (Herkunft nicht unterscheidbar). Die Diagnose-Karte „Korrektur & Sprache“ entscheidet es:
   Zeile **„Wörter im persönlichen Wörterbuch“** (Zahl > 0 bei „Automatisch zum persönlichen Wörterbuch hinzufügen: an“) und dann die Liste unter
   Einstellungen → Persönliches Wörterbuch: Steht „icj“/„mocb“ dort, ist das die Ursache; Abhilfe dort löschen oder (Entscheidung des Owners) die
   automatische Aufnahme abschalten.

## 4. Prüfplan und was nur der Owner prüfen kann

- Robolectric und JVM-Tests wie in den Abnahme-Zeilen; laufen auf GitHub nach dem Push (lokal keine Android-Werkzeuge).
- Nur am Telefon: echte Wörterbücher (die Tests setzen Treffer und Häufigkeiten fest), ob das Nachschlagen der anderen Sprachen rechtzeitig geladen ist
  (erste Wörter nach dem Start: altes Verhalten), Gefühl beim Tippen, und die Zeile „Wörter im persönlichen Wörterbuch“.
