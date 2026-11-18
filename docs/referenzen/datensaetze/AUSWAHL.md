# Auswahl aus den Datensätzen (Stand 2026-11-17)

Erzeugt von `tools/build_informal_words.py` aus dem Paket „SecondHand DE/EN-Datensätze 1.0“ (17.11.2026). Dieses Dokument wird vom Skript geschrieben und nicht von Hand bearbeitet.

Aus `informal_lexicon.jsonl` (3893 Einträge, eigene redaktionelle Arbeit des Pakets, CC0-1.0) bleiben **2138** geschützte Schreibformen übrig: `app/src/main/assets/agent/informal_words.txt`.

## Warum diese Auswahl

Die Tastatur ersetzt ein getipptes Wort nicht mehr automatisch, wenn es in dieser Liste steht (Slang, Chat-Abkürzungen, eingedeutschte Formen, Spiele-, Technik- und Musikbegriffe). Der Vorschlag bleibt in der Leiste. Das ist die einzige Wirkung; die Liste schlägt nichts vor und lernt nichts. Weggelassen wird, was die Wirkung nicht braucht oder was sie schaden würde:

| Grund | Anzahl | Bedeutung |
| --- | --- | --- |
| Phrase | 666 | Mehrere Wörter („zu gut“): ein getipptes Wort kann sie nie treffen. |
| Sonderzeichen | 69 | Emoticons und Formen wie „f*ck“ sind kein Wort der Tastatur. |
| Zu kurz | 104 | Höchstens zwei Zeichen: zu mehrdeutig (Regel 7 des Pakets). |
| Schon bekannt | 715 | Ein Wörterbuch der Datensätze kennt die Form; ein Schutz würde nur echte Korrekturen verhindern (Regel 20: zwei gültige Wörter braucht Satzbeleg). |
| Apostroph weggelassen | 22 | „dont“, „cant“, „youre“: Die Korrektur zu „don't“ ist erwünscht und bleibt (Regel 18/19 des Pakets: die förmliche Form nur auf Wunsch). |
| Tippfehler-Nähe | 36 | Höchstens vier Zeichen, selten im Netz und einen Tippfehler (fehlender oder überzähliger Buchstabe, Dreher, Nachbartaste) von einem sehr häufigen Wort entfernt: hier wäre der Tippfehler wahrscheinlicher als der Slang. |

Aus Quellen des Pakets, die nur zur Entscheidung gelesen wurden (Wörterbuchlisten, Häufigkeiten), steht nichts in der Ausgabe.

## Was die Liste enthält

| Bereich im Paket | Formen |
| --- | --- |
| denglish_form | 566 |
| colloquial | 504 |
| gaming_tech | 457 |
| internet_slang | 313 |
| music | 178 |
| abbreviation | 124 |
| youth_phrase | 101 |

(Eine Form kann in mehreren Bereichen stehen.) Die Einträge sind „redaktionell gesammelt, nicht an Gebrauch geprüft“ (`verification` im Paket): Sie schützen vor falschen Korrekturen, sie belegen keine Verbreitung.

## Weggelassene Tippfehler-Nähe (Stichprobe)

bih (bin), bish (bist), bois (bis), daws (dass), defs (des), dya (day), dyou (you), eep (keep), fook (took), fsr (far), fst (fast), gell (tell), idr (dir), ight (right), ild (old), ime (time), istg (ist), ofc (off), ong (long), ooff (off), otp (top), sone (some), unc (und), usf (use), vibt (gibt), wayy (way), welp (well), whd (why), wsg (weg), wth (with), wya (way), yass (dass), yeet (yet), yeh (yet), yess (yes), yoo (too)

## Nicht verwendet

| Datei des Pakets | Grund |
| --- | --- |
| `frequency_priors*`, `context_ngrams*`, `morphology_observed*`, `context_morphology.sqlite` | Ableitungen unter CC BY-SA 4.0 (wordfreq, Universal Dependencies), viele MB; Häufigkeit ist kein Nachweis der Schreibweise, die Wörterbücher der Tastatur haben eigene Häufigkeiten. |
| `dictionary_stems*`, `words_dictionary_*`, `words_frequency_matched_*`, `raw/hunspell/*` | Ableitungen der Hunspell-Wörterbücher (GPL); die Tastatur bringt eigene Hauptwörterbücher mit, ein zweites Wörterbuch würde Doppelte erzeugen. Nur zum Entscheiden gelesen. |
| `typo_candidates_synthetic.jsonl` | Künstlich erzeugte Tippfehler (20 MB), keine gemessene Fehlerhäufigkeit; die Engine erzeugt solche Kandidaten selbst. |
| `correction_rules.jsonl`, `confusion_sets.jsonl`, `keyboard_adjacency.jsonl` | Beschreibungen ohne Laufzeitwert für die Engine (Regeln sind Text, Verwechslungsgruppen brauchen Satzkontext, die Tastatur kennt die echte Tastengeometrie). Die Regeln stehen als Prüfmaßstab in den Tests. |
| `correction_cases_editorial.*`, `code_switch_cases.jsonl` | Als Testfälle verwendet (`InformalWordsTest`), nicht eingebaut. |
| `abbreviations.jsonl`, `denglish_forms.jsonl` | Schon Teil von `informal_lexicon.jsonl`. |
| `emoji_*`, `raw/unicode`, `raw/cldr` | Gehören nicht zur Autokorrektur (Emoji-Suche hat eigene Daten). |

## Rechte

`informal_lexicon.jsonl` ist eigene redaktionelle Zusammenstellung des Pakets unter CC0-1.0; die Ausgabe enthält nur davon abgeleitete Schreibformen. Das Paket selbst wird nicht ins Repository gelegt (136 MB, gemischte Lizenzen); die Quelle ist die Datei `SecondHand_DE_EN_Datensaetze_2026-11-17.zip` des Owners.
