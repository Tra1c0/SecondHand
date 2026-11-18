# Auswahl aus den Datensätzen (Stand 2026-11-17)

Erzeugt von `tools/build_informal_words.py` aus dem Paket „SecondHand DE/EN-Datensätze 1.0“ (17.11.2026). Dieses Dokument wird vom Skript geschrieben und nicht von Hand bearbeitet.

Aus `informal_lexicon.jsonl` (3893 Einträge, eigene redaktionelle Arbeit des Pakets, CC0-1.0) bleiben **1750** geschützte Schreibformen übrig: `app/src/main/assets/agent/informal_words.txt`.

## Warum diese Auswahl

Die Tastatur ersetzt ein getipptes Wort nicht mehr automatisch, wenn es in dieser Liste steht (Slang, Chat-Abkürzungen, eingedeutschte Formen, Spiele-, Technik- und Musikbegriffe). Der Vorschlag bleibt in der Leiste. Das ist die einzige Wirkung; die Liste schlägt nichts vor und lernt nichts. Weggelassen wird, was die Wirkung nicht braucht oder was sie schaden würde:

| Grund | Anzahl | Bedeutung |
| --- | --- | --- |
| Phrase | 666 | Mehrere Wörter („zu gut“): ein getipptes Wort kann sie nie treffen. |
| Sonderzeichen | 69 | Emoticons und Formen wie „f*ck“ sind kein Wort der Tastatur. |
| Zu kurz | 104 | Höchstens zwei Zeichen: zu mehrdeutig (Regel 7 des Pakets). |
| Schon bekannt | 1109 | Ein Wörterbuch der App (Deutsch, Englisch US und GB) kennt die Form, auch groß geschrieben („jungs“ ist das Hauptwort „Jungs“); ein Schutz würde nur echte Korrekturen verhindern, auch die Großschreibung (Regel 20: zwei gültige Wörter braucht Satzbeleg). |
| Apostroph weggelassen | 21 | „dont“, „cant“, „youre“: Die Korrektur zu „don't“ ist erwünscht und bleibt (Regel 18/19 des Pakets: die förmliche Form nur auf Wunsch). |
| Tippfehler-Nähe | 31 | Selten im Netz (Zipf unter 3,0) und einen Tippfehler (fehlender oder überzähliger Buchstabe, Dreher, Nachbartaste) von einem sehr häufigen Wort der App-Wörterbücher entfernt: hier wäre der Tippfehler wahrscheinlicher als der Slang. |

Was „bekannt“ und „sehr häufig“ heißt, entscheiden die Wörterbücher der App (`app/src/main/assets/dicts/main_de.dict`, `main_en-US.dict`, `main_en-GB.dict`, gelesen mit dem Leser im Skript), nicht die Wortlisten des Pakets: Die Engine benutzt nur jene. Aus den gelesenen Quellen (App-Wörterbücher, Häufigkeitsliste des Pakets) steht nichts in der Ausgabe.

## Was die Liste enthält

| Bereich im Paket | Formen |
| --- | --- |
| denglish_form | 477 |
| colloquial | 460 |
| gaming_tech | 325 |
| internet_slang | 253 |
| music | 133 |
| abbreviation | 107 |
| youth_phrase | 87 |

(Eine Form kann in mehreren Bereichen stehen.) Die Einträge sind „redaktionell gesammelt, nicht an Gebrauch geprüft“ (`verification` im Paket): Sie schützen vor falschen Korrekturen, sie belegen keine Verbreitung.

## Weggelassene Tippfehler-Nähe (Stichprobe)

bsing (being), dadrauf (darauf), daws (das), defs (des), dya (day), dyou (you), faken (taken), fook (took), fst (fast), gng (ging), haten (hatten), hatet (hatte), ight (right), ild (old), ime (time), imes (times), istg (ist), joine (joined), mosht (most), noice (nice), ooff (off), otp (top), recorde (recorded), tilte (title), vibt (gibt), wayy (way), wth (with), wya (way), yas (das), yass (dass), yess (yes)

## Nicht verwendet

| Datei des Pakets | Grund |
| --- | --- |
| `context_ngrams*`, `morphology_observed*`, `context_morphology.sqlite` | Ableitungen unter CC BY-SA 4.0 (Universal Dependencies), viele MB; ein Korpusbeleg beweist keine Schreibweise. Nicht verwendet. |
| `frequency_priors_*` | Ableitung der wordfreq-Daten (CC BY-SA 4.0). Nur beim Auswählen gelesen (wie selten eine Form im Netz vorkommt, für die Tippfehler-Nähe); nichts davon steht in der Ausgabe. |
| `dictionary_stems*`, `words_dictionary_*`, `words_frequency_matched_*`, `raw/hunspell/*` | Ableitungen der Hunspell-Wörterbücher (GPL), Stammformen ohne Beugung. Die Tastatur bringt eigene Hauptwörterbücher mit, nur diese kennt die Engine; die Listen wurden zuerst zum Entscheiden gelesen und sind seit der Nachprüfung 71 ersetzt durch die App-Wörterbücher (Stämme kennen „gefixt“ und „texte“ anders als die Engine). Nicht verwendet. |
| `typo_candidates_synthetic.jsonl` | Künstlich erzeugte Tippfehler (20 MB), keine gemessene Fehlerhäufigkeit; die Engine erzeugt solche Kandidaten selbst. |
| `correction_rules.jsonl`, `confusion_sets.jsonl`, `keyboard_adjacency.jsonl` | Beschreibungen ohne Laufzeitwert für die Engine (Regeln sind Text, Verwechslungsgruppen brauchen Satzkontext, die Tastatur kennt die echte Tastengeometrie). Die Regeln stehen als Prüfmaßstab in den Tests. |
| `correction_cases_editorial.*`, `code_switch_cases.jsonl` | Als Testfälle verwendet (`InformalWordsTest`), nicht eingebaut. |
| `abbreviations.jsonl`, `denglish_forms.jsonl` | Schon Teil von `informal_lexicon.jsonl`. |
| `emoji_*`, `raw/unicode`, `raw/cldr` | Gehören nicht zur Autokorrektur (Emoji-Suche hat eigene Daten). |

## Rechte

`informal_lexicon.jsonl` ist eigene redaktionelle Zusammenstellung des Pakets unter CC0-1.0; die Ausgabe enthält nur davon abgeleitete Schreibformen. Das Paket selbst wird nicht ins Repository gelegt (136 MB, gemischte Lizenzen); die Quelle ist die Datei `SecondHand_DE_EN_Datensaetze_2026-11-17.zip` des Owners.
