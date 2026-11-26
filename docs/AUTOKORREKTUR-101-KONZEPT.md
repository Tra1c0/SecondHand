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

**Nicht gebaut (seit Etappe 107 nach Gewichten, Abschnitt 5): „mur“ → „nur“ trotz BEHUTSAM.** Gegenprobe am echten Wörterbuch: 68 seltene deutsche Wörter (Häufigkeit ≤ 60) haben genau einen sehr
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
„i've“ (auch mit ’) wird zu „I“, „I'm“ … auf dem englischen Layout, wenn „Richtige Wörter nie ändern“ an ist (sonst setzt das Wörterbuch
selbst „I“ über seine Whitelist, unverändert), auf jedem anderen Layout nur, wenn Englisch eine aktive oder andere
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
   Zeile **„Einträge im persönlichen Wörterbuch (Anzahl)“** (Zahl > 0 bei „Automatisch zum persönlichen Wörterbuch hinzufügen: an“) und dann die Liste unter
   Einstellungen → Persönliches Wörterbuch: Steht „icj“/„mocb“ dort, ist das die Ursache; Abhilfe dort löschen oder (Entscheidung des Owners) die
   automatische Aufnahme abschalten.

## 4. Prüfplan und was nur der Owner prüfen kann

- Robolectric und JVM-Tests wie in den Abnahme-Zeilen; laufen auf GitHub nach dem Push (lokal keine Android-Werkzeuge).
- Nur am Telefon: echte Wörterbücher (die Tests setzen Treffer und Häufigkeiten fest), ob das Nachschlagen der anderen Sprachen rechtzeitig geladen ist
  (erste Wörter nach dem Start: altes Verhalten), Gefühl beim Tippen, und die Zeile „Wörter im persönlichen Wörterbuch“.

## 5. Etappe 107: seltene Wörter nach Gewichten (Owner, 2026-11-26)

**Entscheidung des Owners (diktiert, sinngemäß):** „Mur habe ich noch nie benutzt. Ich würde da gerne mit Gewichten arbeiten … dieses AIF auch –
das würde ich dann einfach zu ‚auf‘ korrigieren lassen, dank Gewichten. Die Tastatur lernt ja meinen Schreibstil.“ Nachtrag: „Wenn ich ein Wort
mit der Rücktaste zurücknehme, dann soll es bitte nie wieder korrigiert werden, sondern die Gewichte sollen sich dynamisch anpassen.“

**Was lesbar ist (ohne Text zu loggen):** der Tipp-Verlauf (`UserHistoryDictionary.getFrequency`, neu `DictionaryFacilitatorImpl.historyFrequency`:
steigt mit jeder Nutzung, laut HeliBoard etwa 111 nach zwei und 120 nach drei Nutzungen, fällt mit der Zeit; enthält auch behaltene Tippfehler und
jede stehen gelassene Autokorrektur), die gemerkten Ablehnungen (`RejectedCorrections`), das Gedächtnis (`Memory.isOwnWord`). `TypoMemory` zählt
bewusst gewählte Paare aus der Leiste und setzt sie nach drei Mal selbst; automatische Korrekturen dort mitzuzählen, gäbe eine Schleife, die sich selbst
bestätigt (eine Regelkorrektur würde nach drei Mal ohne die Gewichtsregel weiterlaufen). Darum kein neuer Speicher und kein Missbrauch von
`TypoMemory`: Die eine Größe ist der Verlauf plus die Ablehnung.

**Regel (`RareWordWeights`, `Suggest.applyRareWord`; nur mit „satzbezogene Autokorrektur“; greift auch mit „Richtige Wörter nie ändern“):**
1. Getippt: 3–5 Buchstaben, das Hauptwörterbuch der Tastatursprache kennt es in irgendeiner Schreibung, aber selten: Häufigkeit ≤ **80**
   („mur“ 56, „ALR“ 69, „AIF“ 77, „BSI“ 78).
2. Kandidat: genau **ein** Wort durch eine Nachbartaste mit Häufigkeit ≥ **150** (Deutsch; sonst 170), oder die Korrektur der Engine, wenn sie
   dieses Wort ist. „alt“ (135) ist kein Kandidat: „alr“ wird nie „alt“.
3. Gewicht aus dem Verlauf (wenn „Personalisierte Vorschläge“ an und der Kandidat schon benutzt ist, Verlauf ≥ **111** ≈ zwei Nutzungen):
   korrigieren, wenn das getippte Wort nie abgeschickt wurde oder der Kandidat mindestens **20** Punkte vorn liegt. 20 Punkte im langsam
   steigenden Verlauf heißen ein Vielfaches an Nutzungen; ein paar behaltene „mur“ (111) gegen oft benutztes „nur“ (160) schützen also nicht,
   ein „BSI“, das er etwa so oft schreibt wie „bei“, schon. Annahme: Die genaue Zuordnung Punkte → Nutzungen ist nicht gemessen.
4. Ohne brauchbaren Verlauf (Lernen aus, Inkognito, neues Gerät, Kandidat noch nicht benutzt): nur, wenn der Satz davor den Kandidaten
   deutlich wahrscheinlicher macht (Folgewort-Wert der Engine mindestens **20** über dem des getippten Worts, dieselbe Schwelle wie die Engine
   selbst), sonst nicht. Begründung: Ohne Verlauf gibt es kein Gewicht des Owners, und ein seltenes richtiges Wort („gebt“) soll nicht auf bloßen
   Verdacht verschwinden.
5. Nie: eigenes Wort, Slang-Liste, behaltenes Fremdwort, ein gewöhnliches Wort einer anderen aktiven oder aktivierten Sprache (Regel B, „nut“),
   ein Satz, der zu einer anderen Sprache neigt, ein Großbuchstabe mitten im Satz, eine andere Korrektur der Engine.

**Rücknahme und Bestätigung je Paar (Präzisierung des Owners, 2026-11-26):** „erst bei mehrfachem Zurücknehmen … auf Häufigkeit
zurückführen … nicht endgültig“. `RejectedCorrections` hält jetzt je Paar (getippt → Korrektur) zwei Zähler statt einer Sperre: **bestätigt** (die
Korrektur wurde abgeschickt, automatisch oder aus der Leiste gewählt; Fertigschreiben eines Worts und reine Großschreibung zählen nicht) und
**abgelehnt** (Rücktaste direkt danach, Antippen der getippten Form, oder später im Feld geändert, siehe 107b). Formel (`CorrectionWeights`):
**zurückgehalten, solange abgelehnt > 0 und bestätigt < 3 × abgelehnt** (eine Rücknahme wiegt drei Bestätigungen). Beide Zähler verblassen mit einer
Halbwertszeit von **30 Tagen**; eine Ablehnung unter **0,25** ist vergessen. Dieselbe Rücknahme zweimal binnen **60 s** (Rücktaste und danach der
Feldvergleich) zählt einmal.

| Verlauf des Paars | bestätigt | abgelehnt | Ergebnis |
| --- | --- | --- | --- |
| 10-mal stehen gelassen, 1-mal zurückgenommen | 10 | 1 | angewendet (10 ≥ 3) |
| 1-mal angewendet, sofort Rücktaste | 1 | 1 | zurückgehalten (1 < 3), nur Vorschlag |
| 2-mal bestätigt, 2-mal zurückgenommen | 2 | 2 | zurückgehalten (2 < 6) |
| danach 4-mal die Korrektur selbst aus der Leiste gewählt | 6 | 2 | wieder angewendet (6 ≥ 6) |
| 1-mal zurückgenommen, 60 Tage nichts | 0 | 0,25 → vergessen | wieder angewendet |

**Bewusst geändert:** Bis Etappe 106 merkte `RejectedCorrections` eine Rücknahme sofort und für immer (Schalter „Rückgängig gemachte Korrekturen
merken“). Das gilt jetzt für alle Korrekturen nach dieser Formel; gespeicherte alte Paare zählen als eine Ablehnung. Gezählt wird weiter nur mit dem
Schalter (opt-in), für die Regelkorrekturen von 107/108 auch mit „Personalisierte Vorschläge“ allein; nie pausiert, inkognito oder gesperrt. Die
Schutzregeln von 91, 92 und 101 bleiben unverändert. Die Diagnose-Karte zeigt die Zahl der gerade zurückgehaltenen Paare (wie bisher „Zurückgenommene
Korrekturen (Anzahl)“) und neu „Seltene Wörter und Kurzformen korrigiert seit Start (behalten / zurückgenommen)“, nur Zahlen.

**107b – späte Korrektur zählt wie eine Rücknahme.** Belegt im Code: Der Feldvergleich von Etappe 49 (`FieldCorrectionWatcher.checkpoint`, aufgerufen
bei jedem abgeschickten Wort und jeder Cursorbewegung, InputLogic-Hooks von 48/49) kennt die automatischen Korrekturen dieses Felds
(`autoCorrected`) und erkannte schon „X → Y automatisch, später Y → X von Hand“ an jeder späteren Stelle (nach Leerzeichen, Satzzeichen, angefangenem
nächsten Wort, Zurücklöschen, Cursor zurück). Es fehlte „Y → drittes Wort Z“. Neu: Ändert der Owner genau dieses Wort zu einem dritten Wort (kein
Fertigschreiben, keine reine Großschreibung), zählt das als **abgelehnt** für „X → Y“, und „X → Z“ wird ein Kandidat der späten Tippfehler
(`FieldCorrectionMemory`, gelernt beim zweiten Mal an anderer Stelle, wie in 49). `TypedWordFirst` und `TypoMemory` bleiben unverändert (TypoMemory
lernt nur Auswahlen aus der Leiste). Es gilt alles, was 49 schützt: nur mit „Meine Tippfehler lernen“ oder „Rückgängig gemachte Korrekturen merken“,
nie in Passwort-, Inkognito- oder Lern-aus-Feldern, nicht gesperrt, nicht bei langsamer App, ausgewählt-und-überschrieben oder eingefügt; liefert die
App keinen Text, wird nichts gezählt. Kein Eingriff in `InputLogic`.

**Die 68 Paare aus Abschnitt 1 (D)** (seltene deutsche Wörter ≤ 60 mit genau einem Nachbartasten-Wort ≥ 150; „typischer Verlauf“ = der Owner benutzt
den Kandidaten oft, das getippte Wort nie oder viel seltener):

| getippt | Häufigkeit | Kandidat | Häufigkeit | ohne Verlauf | mit typischem Verlauf |
| --- | --- | --- | --- | --- | --- |
| def | 48 | der | 216 | bleibt, außer der Satz davor macht „der“ deutlich wahrscheinlicher | „der“, wenn du „der“ oft und „def“ nie oder viel seltener getippt hast |
| gon | 57 | von | 204 | bleibt, außer der Satz davor macht „von“ deutlich wahrscheinlicher | „von“, wenn du „von“ oft und „gon“ nie oder viel seltener getippt hast |
| fon | 53 | von | 204 | bleibt, außer der Satz davor macht „von“ deutlich wahrscheinlicher | „von“, wenn du „von“ oft und „fon“ nie oder viel seltener getippt hast |
| fes | 28 | des | 200 | bleibt, außer der Satz davor macht „des“ deutlich wahrscheinlicher | „des“, wenn du „des“ oft und „fes“ nie oder viel seltener getippt hast |
| dew | 34 | des | 200 | bleibt, außer der Satz davor macht „des“ deutlich wahrscheinlicher | „des“, wenn du „des“ oft und „dew“ nie oder viel seltener getippt hast; englisch 85: bleibt im englischen Satz |
| dey | 43 | des | 200 | bleibt, außer der Satz davor macht „des“ deutlich wahrscheinlicher | „des“, wenn du „des“ oft und „dey“ nie oder viel seltener getippt hast |
| kit | 45 | mit | 198 | bleibt, außer der Satz davor macht „mit“ deutlich wahrscheinlicher | „mit“, wenn du „mit“ oft und „kit“ nie oder viel seltener getippt hast; englisch 113: bleibt im englischen Satz und ohne Satzbeleg |
| sem | 58 | dem | 196 | bleibt, außer der Satz davor macht „dem“ deutlich wahrscheinlicher | „dem“, wenn du „dem“ oft und „sem“ nie oder viel seltener getippt hast |
| ras | 52 | das | 195 | bleibt, außer der Satz davor macht „das“ deutlich wahrscheinlicher | „das“, wenn du „das“ oft und „ras“ nie oder viel seltener getippt hast |
| ale | 60 | als | 194 | bleibt, außer der Satz davor macht „als“ deutlich wahrscheinlicher | „als“, wenn du „als“ oft und „ale“ nie oder viel seltener getippt hast; englisch 94: bleibt im englischen Satz |
| eich | 41 | sich | 193 | bleibt, außer der Satz davor macht „sich“ deutlich wahrscheinlicher | „sich“, wenn du „sich“ oft und „eich“ nie oder viel seltener getippt hast |
| aar | 43 | war | 191 | bleibt, außer der Satz davor macht „war“ deutlich wahrscheinlicher | „war“, wenn du „war“ oft und „aar“ nie oder viel seltener getippt hast |
| hach | 28 | nach | 187 | bleibt, außer der Satz davor macht „nach“ deutlich wahrscheinlicher | „nach“, wenn du „nach“ oft und „hach“ nie oder viel seltener getippt hast |
| bos | 53 | bis | 187 | bleibt, außer der Satz davor macht „bis“ deutlich wahrscheinlicher | „bis“, wenn du „bis“ oft und „bos“ nie oder viel seltener getippt hast |
| bek | 46 | bei | 186 | bleibt, außer der Satz davor macht „bei“ deutlich wahrscheinlicher | „bei“, wenn du „bei“ oft und „bek“ nie oder viel seltener getippt hast |
| hum | 60 | zum | 185 | bleibt, außer der Satz davor macht „zum“ deutlich wahrscheinlicher | „zum“, wenn du „zum“ oft und „hum“ nie oder viel seltener getippt hast; englisch 81: bleibt im englischen Satz |
| wire | 57 | wird | 184 | bleibt, außer der Satz davor macht „wird“ deutlich wahrscheinlicher | „wird“, wenn du „wird“ oft und „wire“ nie oder viel seltener getippt hast; englisch 122: bleibt im englischen Satz und ohne Satzbeleg |
| wirf | 49 | wird | 184 | bleibt, außer der Satz davor macht „wird“ deutlich wahrscheinlicher | „wird“, wenn du „wird“ oft und „wirf“ nie oder viel seltener getippt hast |
| sue | 47 | sie | 184 | bleibt, außer der Satz davor macht „sie“ deutlich wahrscheinlicher | „sie“, wenn du „sie“ oft und „sue“ nie oder viel seltener getippt hast; englisch 100: bleibt im englischen Satz und ohne Satzbeleg |
| sund | 38 | sind | 184 | bleibt, außer der Satz davor macht „sind“ deutlich wahrscheinlicher | „sind“, wenn du „sind“ oft und „sund“ nie oder viel seltener getippt hast |
| sid | 52 | sie | 184 | bleibt, außer der Satz davor macht „sie“ deutlich wahrscheinlicher | „sie“, wenn du „sie“ oft und „sid“ nie oder viel seltener getippt hast |
| dutch | 45 | durch | 184 | bleibt, außer der Satz davor macht „durch“ deutlich wahrscheinlicher | „durch“, wenn du „durch“ oft und „dutch“ nie oder viel seltener getippt hast; englisch 62: bleibt im englischen Satz |
| nut | 57 | nur | 177 | bleibt, außer der Satz davor macht „nur“ deutlich wahrscheinlicher | „nur“, wenn du „nur“ oft und „nut“ nie oder viel seltener getippt hast; englisch 100: bleibt im englischen Satz und ohne Satzbeleg |
| mur | 56 | nur | 177 | bleibt, außer der Satz davor macht „nur“ deutlich wahrscheinlicher | „nur“, wenn du „nur“ oft und „mur“ nie oder viel seltener getippt hast |
| bur | 47 | nur | 177 | bleibt, außer der Satz davor macht „nur“ deutlich wahrscheinlicher | „nur“, wenn du „nur“ oft und „bur“ nie oder viel seltener getippt hast; englisch 65: bleibt im englischen Satz |
| bor | 49 | vor | 177 | bleibt, außer der Satz davor macht „vor“ deutlich wahrscheinlicher | „vor“, wenn du „vor“ oft und „bor“ nie oder viel seltener getippt hast |
| hag | 51 | hat | 175 | bleibt, außer der Satz davor macht „hat“ deutlich wahrscheinlicher | „hat“, wenn du „hat“ oft und „hag“ nie oder viel seltener getippt hast; englisch 75: bleibt im englischen Satz |
| waden | 14 | waren | 173 | bleibt, außer der Satz davor macht „waren“ deutlich wahrscheinlicher | „waren“, wenn du „waren“ oft und „waden“ nie oder viel seltener getippt hast |
| hafte | 52 | hatte | 173 | bleibt, außer der Satz davor macht „hatte“ deutlich wahrscheinlicher | „hatte“, wenn du „hatte“ oft und „hafte“ nie oder viel seltener getippt hast |
| seim | 36 | sein | 172 | bleibt, außer der Satz davor macht „sein“ deutlich wahrscheinlicher | „sein“, wenn du „sein“ oft und „seim“ nie oder viel seltener getippt hast |
| fieser | 44 | dieser | 172 | bleibt (mehr als 5 Buchstaben) | bleibt (mehr als 5 Buchstaben) |
| widder | 51 | wieder | 169 | bleibt (mehr als 5 Buchstaben) | bleibt (mehr als 5 Buchstaben) |
| fiese | 51 | diese | 169 | bleibt, außer der Satz davor macht „diese“ deutlich wahrscheinlicher | „diese“, wenn du „diese“ oft und „fiese“ nie oder viel seltener getippt hast |
| kehr | 52 | mehr | 168 | bleibt, außer der Satz davor macht „mehr“ deutlich wahrscheinlicher | „mehr“, wenn du „mehr“ oft und „kehr“ nie oder viel seltener getippt hast |
| heure | 52 | heute | 168 | bleibt, außer der Satz davor macht „heute“ deutlich wahrscheinlicher | „heute“, wenn du „heute“ oft und „heure“ nie oder viel seltener getippt hast |
| haven | 48 | haben | 167 | bleibt, außer der Satz davor macht „haben“ deutlich wahrscheinlicher | „haben“, wenn du „haben“ oft und „haven“ nie oder viel seltener getippt hast; englisch 99: bleibt im englischen Satz |
| rann | 41 | dann | 166 | bleibt, außer der Satz davor macht „dann“ deutlich wahrscheinlicher | „dann“, wenn du „dann“ oft und „rann“ nie oder viel seltener getippt hast |
| ohm | 40 | ihm | 166 | bleibt, außer der Satz davor macht „ihm“ deutlich wahrscheinlicher | „ihm“, wenn du „ihm“ oft und „ohm“ nie oder viel seltener getippt hast; englisch 72: bleibt im englischen Satz |
| bein | 59 | beim | 166 | bleibt, außer der Satz davor macht „beim“ deutlich wahrscheinlicher | „beim“, wenn du „beim“ oft und „bein“ nie oder viel seltener getippt hast |
| kaj | 51 | kam | 164 | bleibt, außer der Satz davor macht „kam“ deutlich wahrscheinlicher | „kam“, wenn du „kam“ oft und „kaj“ nie oder viel seltener getippt hast |
| alls | 49 | alle | 163 | bleibt, außer der Satz davor macht „alle“ deutlich wahrscheinlicher | „alle“, wenn du „alle“ oft und „alls“ nie oder viel seltener getippt hast |
| tab | 46 | gab | 162 | bleibt, außer der Satz davor macht „gab“ deutlich wahrscheinlicher | „gab“, wenn du „gab“ oft und „tab“ nie oder viel seltener getippt hast; englisch 91: bleibt im englischen Satz |
| gag | 51 | gab | 162 | bleibt, außer der Satz davor macht „gab“ deutlich wahrscheinlicher | „gab“, wenn du „gab“ oft und „gag“ nie oder viel seltener getippt hast; englisch 101: bleibt im englischen Satz und ohne Satzbeleg |
| fab | 45 | gab | 162 | bleibt, außer der Satz davor macht „gab“ deutlich wahrscheinlicher | „gab“, wenn du „gab“ oft und „fab“ nie oder viel seltener getippt hast |
| viet | 57 | vier | 161 | bleibt, außer der Satz davor macht „vier“ deutlich wahrscheinlicher | „vier“, wenn du „vier“ oft und „viet“ nie oder viel seltener getippt hast |
| seren | 14 | deren | 161 | bleibt, außer der Satz davor macht „deren“ deutlich wahrscheinlicher | „deren“, wenn du „deren“ oft und „seren“ nie oder viel seltener getippt hast |
| fier | 28 | vier | 161 | bleibt, außer der Satz davor macht „vier“ deutlich wahrscheinlicher | „vier“, wenn du „vier“ oft und „fier“ nie oder viel seltener getippt hast |
| zier | 23 | hier | 160 | bleibt, außer der Satz davor macht „hier“ deutlich wahrscheinlicher | „hier“, wenn du „hier“ oft und „zier“ nie oder viel seltener getippt hast |
| weigere | 55 | weitere | 160 | bleibt (mehr als 5 Buchstaben) | bleibt (mehr als 5 Buchstaben) |
| aas | 40 | was | 160 | bleibt, außer der Satz davor macht „was“ deutlich wahrscheinlicher | „was“, wenn du „was“ oft und „aas“ nie oder viel seltener getippt hast |
| senn | 38 | wenn | 158 | bleibt, außer der Satz davor macht „wenn“ deutlich wahrscheinlicher | „wenn“, wenn du „wenn“ oft und „senn“ nie oder viel seltener getippt hast |
| ina | 55 | ins | 158 | bleibt, außer der Satz davor macht „ins“ deutlich wahrscheinlicher | „ins“, wenn du „ins“ oft und „ina“ nie oder viel seltener getippt hast |
| ting | 51 | ging | 157 | bleibt, außer der Satz davor macht „ging“ deutlich wahrscheinlicher | „ging“, wenn du „ging“ oft und „ting“ nie oder viel seltener getippt hast; englisch 65: bleibt im englischen Satz |
| riesen | 48 | diesen | 157 | bleibt (mehr als 5 Buchstaben) | bleibt (mehr als 5 Buchstaben) |
| fährte | 23 | führte | 157 | bleibt (mehr als 5 Buchstaben) | bleibt (mehr als 5 Buchstaben) |
| fiesen | 59 | diesen | 157 | bleibt (mehr als 5 Buchstaben) | bleibt (mehr als 5 Buchstaben) |
| bing | 43 | ging | 157 | bleibt, außer der Satz davor macht „ging“ deutlich wahrscheinlicher | „ging“, wenn du „ging“ oft und „bing“ nie oder viel seltener getippt hast; englisch 60: bleibt im englischen Satz |
| beben | 54 | neben | 157 | bleibt, außer der Satz davor macht „neben“ deutlich wahrscheinlicher | „neben“, wenn du „neben“ oft und „beben“ nie oder viel seltener getippt hast |
| stehr | 28 | steht | 156 | bleibt, außer der Satz davor macht „steht“ deutlich wahrscheinlicher | „steht“, wenn du „steht“ oft und „stehr“ nie oder viel seltener getippt hast |
| sill | 36 | soll | 156 | bleibt, außer der Satz davor macht „soll“ deutlich wahrscheinlicher | „soll“, wenn du „soll“ oft und „sill“ nie oder viel seltener getippt hast; englisch 78: bleibt im englischen Satz |
| doll | 52 | soll | 156 | bleibt, außer der Satz davor macht „soll“ deutlich wahrscheinlicher | „soll“, wenn du „soll“ oft und „doll“ nie oder viel seltener getippt hast; englisch 104: bleibt im englischen Satz und ohne Satzbeleg |
| nin | 55 | nun | 155 | bleibt, außer der Satz davor macht „nun“ deutlich wahrscheinlicher | „nun“, wenn du „nun“ oft und „nin“ nie oder viel seltener getippt hast |
| finder | 46 | findet | 153 | bleibt (mehr als 5 Buchstaben) | bleibt (mehr als 5 Buchstaben) |
| nez | 40 | neu | 152 | bleibt, außer der Satz davor macht „neu“ deutlich wahrscheinlicher | „neu“, wenn du „neu“ oft und „nez“ nie oder viel seltener getippt hast |
| heurigen | 45 | heutigen | 152 | bleibt (mehr als 5 Buchstaben) | bleibt (mehr als 5 Buchstaben) |
| stans | 36 | stand | 151 | bleibt, außer der Satz davor macht „stand“ deutlich wahrscheinlicher | „stand“, wenn du „stand“ oft und „stans“ nie oder viel seltener getippt hast |
| langs | 31 | lange | 151 | bleibt, außer der Satz davor macht „lange“ deutlich wahrscheinlicher | „lange“, wenn du „lange“ oft und „langs“ nie oder viel seltener getippt hast |
| gebt | 59 | geht | 150 | bleibt, außer der Satz davor macht „geht“ deutlich wahrscheinlicher | „geht“, wenn du „geht“ oft und „gebt“ nie oder viel seltener getippt hast |

Zusätzlich im Bereich der Regel: die Abkürzungsfälle „aif“ (AIF 77) → „auf“ (mit Verlauf), „bsi“ (BSI 78) → „bei“ nur, wenn er „bei“ weit öfter
schreibt als „BSI“, „alr“ (ALR 69) nie (kein Kandidat ≥ 150). Insgesamt erfüllen am echten Wörterbuch 165 Wörter aus 3–5 Buchstaben die
Bedingungen 1 und 2.

**Kritiker:** (1) Ein seltenes Wort, das er bewusst zum ersten Mal schreibt („gebt“), wird mit Verlauf korrigiert. Entscheidung: gewollt (Owner),
eine Rücktaste stellt es für immer ab. (2) Die Verlaufswerte zerfallen mit der Zeit; nach langer Pause greift Schritt 4. Hingenommen. (3) Ohne
„Personalisierte Vorschläge“ merkt sich die Tastatur keine Rücknahme, außer der Schalter „Rückgängig gemachte Korrekturen merken“ ist an. Hingenommen
(Lernen bleibt opt-in).

| Ausgangslage | Aktion | Erwartung | Gegenfall | Nachweis |
| --- | --- | --- | --- | --- |
| Behutsam, Verlauf „nur“ oft, „mur“ nie | „ich sehe mur“ | „nur“ | „mur“ etwa so oft wie „nur“: bleibt | Robolectric „stage 107 mur …“ |
| dasselbe, danach Rücktaste | „mur“ noch einmal | bleibt, „nur“ in der Leiste | – | Robolectric „stage 107 one Backspace …“ |
| Verlauf „auf“ oft | „aif“ | „auf“ | „alr“ bleibt, „bsi“ bleibt bei gleich häufigem „BSI“ | Robolectric „stage 107 aif …“ |
| kein Verlauf | „ihr gebt“ | bleibt | Satz macht „geht“ deutlich wahrscheinlicher: „geht“ | Robolectric „stage 107 gebt …“ |
| Unit | – | Formel | – | `CT/RareWordWeightsTest` |

## 6. Etappe 108: Kurzformen mit Apostroph automatisch (Owner, 2026-11-26)

Owner: „gibt's, wird's, dreht's … werden nicht automatisch korrigiert, nur vorgeschlagen.“ Ursache: Das deutsche Hauptwörterbuch kennt keine Kurzform
mit „'s“ und auch die Form ohne Apostroph nicht (Abschnitt 5.1 des Konzepts von Etappe 90); die Engine bietet „gibt's“ höchstens aus dem Verlauf an,
und Etappe 92 lässt ein Ziel nur aus dem Verlauf nicht automatisch setzen. Statt der Liste aus Etappe 90 eine produktive Regel (`GermanShortForms`,
`Suggest.applyShortForm`, nur mit „satzbezogene Autokorrektur“, auch mit „Richtige Wörter nie ändern“, Vorrang vor der Engine):

- getippt endet auf „s“, nur Buchstaben, klein (groß nur am Satzanfang), deutsches Layout;
- der Teil davor ist eine finite Verbform, die das deutsche Hauptwörterbuch kennt: eine der häufigen unregelmäßigen Formen (ist, war, wär, hat, hab,
  wird, gibt, gab, tut, kann, soll, will, darf, mag, muss, ging, kam, sieht, nimmt, läuft, steht …) oder eine regelmäßige Form auf „t“ eines Verbs der
  Liste (dreht, geht, klappt, passt);
- das getippte Wort selbst kennt kein aktives Hauptwörterbuch, keine Kontakte, keine Apps (nichts, rechts, stets, bereits, abseits, Amts, Guts,
  Rats, Geschäfts bleiben); Tipp-Verlauf und persönliches Wörterbuch zählen nicht (dort landen solche Formen automatisch);
- nicht in einem Satz, der zu einer anderen Sprache neigt („wars“ im englischen Satz bleibt; im deutschen oder neutralen Satz → „war's“), nicht bei
  einem eigenen Wort, einem behaltenen Fremdwort, einem zurückgenommenen Paar; @, Punkt und Ziffern schließen schon die Buchstabenprüfung aus;
- die Slang-Liste hält die Regel nicht auf („gehts“, „gibts“, „habs“ stehen dort, der Wunsch des Owners geht vor, wie in Etappe 90 geplant);
- Apostroph: der gerade `'`, den die Tastatur tippt und die Wörterbücher benutzen.

Rücknahme wie in 107: einmal Rücktaste, nie wieder. Gegenfall, den die Regel in Kauf nimmt: Eine Form, die das Wörterbuch nicht kennt, aber ein
Plural oder Genitiv eines Verbstamms sein könnte (am Satzanfang „Hauts“ → „Haut's“).

| Ausgangslage | Aktion | Erwartung | Gegenfall | Nachweis |
| --- | --- | --- | --- | --- |
| deutsches Layout, Behutsam | „und gibts“, „wirds“, „drehts“, „gehts“ | „gibt's“, „wird's“, „dreht's“, „geht's“ | „nichts“, „rechts“, „Amts“ bleiben | Robolectric „stage 108 …“ |
| dasselbe | „und wars“ | „war's“ | „the cat wars“ bleibt | Robolectric |
| Satzanfang | „Gibts“ | „Gibt's“ | „und Peters“ bleibt | Robolectric |
| nach einer Rücknahme | „gibts“ | bleibt | – | Robolectric |
| Unit | – | Ableitung | „dings“, „kaufts“ ohne Wörterbuch | `CT/GermanShortFormsTest` |
