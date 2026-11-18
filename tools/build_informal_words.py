#!/usr/bin/env python3
"""Curates the protected informal words of the keyboard from the SecondHand DE/EN data package.

Input: the unpacked data package (SecondHand_DE_EN_Datensaetze_2026-11-17.zip, version 1.0).
Output: app/src/main/assets/agent/informal_words.txt (one normalised word per line) and a report
(docs/referenzen/datensaetze/AUSWAHL.md) that says what was kept, what was left out and why.

Only `data/informal_lexicon.jsonl` (own editorial work of the package, CC0-1.0) ends up in the output.
Word lists and frequencies of the package are read only to decide what to leave out; nothing derived
from them (no frequency, no dictionary word) is written. Run with `python3 -I` and the package folder:

    python3 -I tools/build_informal_words.py /path/to/unpacked/package

The normal form of a word (NFC, lower case, ' for the curly apostrophe) is the one the app uses
(`InformalWords.normalize`); keep both in step.
"""
import gzip
import json
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUT_WORDS = REPO / "app/src/main/assets/agent/informal_words.txt"
OUT_REPORT = REPO / "docs/referenzen/datensaetze/AUSWAHL.md"

MIN_LENGTH = 3            # shorter tokens are too ambiguous (package rule 7)
COLLISION_MAX_LENGTH = 4  # a collision with a frequent word only matters for short forms
COLLISION_NEIGHBOUR_ZIPF = 5.5
COLLISION_OWN_ZIPF = 3.5
WORD_CHARS = set("'-")


def normalize(word: str) -> str:
    return unicodedata.normalize("NFC", word).lower().replace("’", "'")


def is_word_token(key: str) -> bool:
    return bool(key) and all(c.isalpha() or c.isdigit() or c in WORD_CHARS for c in key) and any(c.isalpha() for c in key)


def read_zipf(path: Path) -> dict:
    best = {}
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        head = fh.readline().rstrip("\n").split("\t")
        zi, ni = head.index("zipf"), head.index("lookup_norm")
        for line in fh:
            p = line.rstrip("\n").split("\t")
            z = float(p[zi])
            if z > best.get(p[ni], 0.0):
                best[p[ni]] = z
    return best


def keyboard_adjacency(rows, indents):
    boxes = {}
    for r, row in enumerate(rows):
        for i, c in enumerate(row):
            boxes.setdefault(c, (indents[r] + i + 0.5, r + 0.5))

    def adjacent(a, b):
        if a not in boxes or b not in boxes or a == b:
            return False
        dx = abs(boxes[a][0] - boxes[b][0])
        dy = abs(boxes[a][1] - boxes[b][1])
        return dx <= 1.5 if dy < 0.5 else (dx <= 1.0 if dy <= 1.5 else False)

    return adjacent


ADJ = [keyboard_adjacency(["qwertzuiopü", "asdfghjklöä", "yxcvbnm"], [0, 0, 1.5]),
       keyboard_adjacency(["qwertyuiop", "asdfghjkl", "zxcvbnm"], [0, 0.5, 1.5])]


def one_typo_apart(typo: str, word: str) -> bool:
    """Whether [typo] could be [word] typed with one slip: a letter missing, a letter too many that sits
    next to a neighbouring letter on the keyboard (or doubles it), two letters swapped, or the key next
    to the right one."""
    n, m = len(typo), len(word)
    if abs(n - m) > 1:
        return False
    if n == m:
        diff = [i for i in range(n) if typo[i] != word[i]]
        if len(diff) == 1:
            i = diff[0]
            return any(a(typo[i], word[i]) for a in ADJ)
        if len(diff) == 2 and diff[1] == diff[0] + 1:
            i = diff[0]
            return typo[i] == word[i + 1] and typo[i + 1] == word[i]
        return False
    if n < m:  # a letter missing in the typo: any letter can be skipped
        return any(word[:i] + word[i + 1:] == typo for i in range(m))
    # a letter too many in the typo: it was the same key again or a key next to a neighbouring letter
    for i in range(n):
        if typo[:i] + typo[i + 1:] != word:
            continue
        around = [c for c in (typo[i - 1] if i > 0 else "", typo[i + 1] if i + 1 < n else "") if c]
        if any(typo[i] == c or any(a(typo[i], c) for a in ADJ) for c in around):
            return True
    return False


FIXTURE = REPO / "agent-core/src/test/resources/informal_cases.tsv"
INFORMAL_CATEGORIES = {"slang", "denglish", "colloquial", "abbreviation", "regional", "domain", "code_switch"}
STANDARD_CATEGORIES = {"valid_context", "identity_control", "proper_name"}


def write_fixture(data: Path, listed: set) -> None:
    """Test fixture from the package's editorial cases (CC0): `protect` = informal inputs the list covers,
    `open` = misspelled inputs and standard words that must never be on the list."""
    cases = [json.loads(line) for line in open(data / "correction_cases_editorial.jsonl", encoding="utf-8")]

    def single(w):
        return " " not in w and w.replace("'", "").replace("-", "").isalnum()

    protect, keep_open = set(), set()
    for c in cases:
        w = c["input"]
        if not single(w):
            continue
        if c["expected_action"] == "KEEP" and c["category"] in INFORMAL_CATEGORIES and normalize(w) in listed:
            protect.add(normalize(w))
        elif c["expected_action"] == "SUGGEST" or c["category"] in STANDARD_CATEGORIES:
            keep_open.add(normalize(w))
    FIXTURE.parent.mkdir(parents=True, exist_ok=True)
    rows = [f"protect\t{w}" for w in sorted(protect)] + [f"open\t{w}" for w in sorted(keep_open)]
    FIXTURE.write_text("# made by tools/build_informal_words.py from correction_cases_editorial.jsonl (CC0-1.0)\n"
                       + "\n".join(rows) + "\n", encoding="utf-8")


def main(package: Path) -> None:
    data = package / "data"
    lexicon = [json.loads(line) for line in open(data / "informal_lexicon.jsonl", encoding="utf-8")]
    dictionary = {}
    for lang in ("de", "en"):
        dictionary[lang] = {normalize(w.strip()) for w in open(data / f"words_dictionary_{lang}.txt", encoding="utf-8") if w.strip()}
    known = dictionary["de"] | dictionary["en"]
    # "dont", "cant", "youre": a standard word with its apostrophe left out; the engine's correction to the
    # apostrophe form is wanted, so these stay unprotected (package rule 18/19: formal form on explicit choice only)
    with_apostrophe = {w.replace("'", "") for w in known if "'" in w}
    zipf = {lang: read_zipf(data / f"frequency_priors_{lang}.tsv.gz") for lang in ("de", "en")}
    frequent = {}  # normalised dictionary word -> zipf, only the frequent ones of either language
    for lang in ("de", "en"):
        for word, z in zipf[lang].items():
            if z >= COLLISION_NEIGHBOUR_ZIPF and word in dictionary[lang] and len(word) >= 3:
                frequent[word] = max(frequent.get(word, 0.0), z)
    by_length = defaultdict(list)
    for word in frequent:
        by_length[len(word)].append(word)

    kept, left_out = {}, defaultdict(list)
    categories = defaultdict(set)
    for entry in lexicon:
        surface = entry["surface"]
        key = normalize(surface)
        if " " in key:
            left_out["phrase"].append(key)
        elif not is_word_token(key):
            left_out["symbols"].append(key)
        elif len(key) < MIN_LENGTH:
            left_out["too_short"].append(key)
        elif key in known:
            left_out["known_word"].append(key)
        elif key in with_apostrophe:
            left_out["apostrophe_omission"].append(key)
        else:
            own = max(zipf["de"].get(key, 0.0), zipf["en"].get(key, 0.0))
            clash = None
            if len(key) <= COLLISION_MAX_LENGTH and own < COLLISION_OWN_ZIPF:
                for length in (len(key) - 1, len(key), len(key) + 1):
                    clash = next((w for w in by_length[length] if one_typo_apart(key, w)), None)
                    if clash:
                        break
            if clash:
                left_out["typo_collision"].append(f"{key} ({clash})")
            else:
                kept[key] = True
                categories[key].update(entry["categories"])
    left_out = {reason: sorted(set(values)) for reason, values in left_out.items()}
    words = sorted(kept)
    OUT_WORDS.parent.mkdir(parents=True, exist_ok=True)
    OUT_WORDS.write_text("\n".join(words) + "\n", encoding="utf-8")

    write_fixture(data, set(words))
    cat_count = Counter(c for k in words for c in categories[k])
    lines = [
        "# Auswahl aus den Datensätzen (Stand 2026-11-17)",
        "",
        "Erzeugt von `tools/build_informal_words.py` aus dem Paket „SecondHand DE/EN-Datensätze 1.0“ "
        "(17.11.2026). Dieses Dokument wird vom Skript geschrieben und nicht von Hand bearbeitet.",
        "",
        f"Aus `informal_lexicon.jsonl` ({len(lexicon)} Einträge, eigene redaktionelle Arbeit des Pakets, "
        f"CC0-1.0) bleiben **{len(words)}** geschützte Schreibformen übrig: "
        "`app/src/main/assets/agent/informal_words.txt`.",
        "",
        "## Warum diese Auswahl",
        "",
        "Die Tastatur ersetzt ein getipptes Wort nicht mehr automatisch, wenn es in dieser Liste steht "
        "(Slang, Chat-Abkürzungen, eingedeutschte Formen, Spiele-, Technik- und Musikbegriffe). Der "
        "Vorschlag bleibt in der Leiste. Das ist die einzige Wirkung; die Liste schlägt nichts vor und "
        "lernt nichts. Weggelassen wird, was die Wirkung nicht braucht oder was sie schaden würde:",
        "",
        "| Grund | Anzahl | Bedeutung |",
        "| --- | --- | --- |",
        f"| Phrase | {len(left_out['phrase'])} | Mehrere Wörter („zu gut“): ein getipptes Wort kann sie nie treffen. |",
        f"| Sonderzeichen | {len(left_out['symbols'])} | Emoticons und Formen wie „f*ck“ sind kein Wort der Tastatur. |",
        f"| Zu kurz | {len(left_out['too_short'])} | Höchstens zwei Zeichen: zu mehrdeutig (Regel 7 des Pakets). |",
        f"| Schon bekannt | {len(left_out['known_word'])} | Ein Wörterbuch der Datensätze kennt die Form; ein Schutz würde "
        "nur echte Korrekturen verhindern (Regel 20: zwei gültige Wörter braucht Satzbeleg). |",
        f"| Apostroph weggelassen | {len(left_out['apostrophe_omission'])} | „dont“, „cant“, „youre“: Die Korrektur zu „don't“ "
        "ist erwünscht und bleibt (Regel 18/19 des Pakets: die förmliche Form nur auf Wunsch). |",
        f"| Tippfehler-Nähe | {len(left_out['typo_collision'])} | Höchstens vier Zeichen, selten im Netz und einen Tippfehler "
        "(fehlender oder überzähliger Buchstabe, Dreher, Nachbartaste) von einem sehr häufigen Wort entfernt: "
        "hier wäre der Tippfehler wahrscheinlicher als der Slang. |",
        "",
        "Aus Quellen des Pakets, die nur zur Entscheidung gelesen wurden (Wörterbuchlisten, Häufigkeiten), "
        "steht nichts in der Ausgabe.",
        "",
        "## Was die Liste enthält",
        "",
        "| Bereich im Paket | Formen |",
        "| --- | --- |",
    ]
    for cat, n in cat_count.most_common():
        lines.append(f"| {cat} | {n} |")
    lines += [
        "",
        "(Eine Form kann in mehreren Bereichen stehen.) Die Einträge sind „redaktionell gesammelt, nicht an "
        "Gebrauch geprüft“ (`verification` im Paket): Sie schützen vor falschen Korrekturen, sie belegen "
        "keine Verbreitung.",
        "",
        "## Weggelassene Tippfehler-Nähe (Stichprobe)",
        "",
        ", ".join(sorted(left_out["typo_collision"])[:60]) + (" …" if len(left_out["typo_collision"]) > 60 else ""),
        "",
        "## Nicht verwendet",
        "",
        "| Datei des Pakets | Grund |",
        "| --- | --- |",
        "| `frequency_priors*`, `context_ngrams*`, `morphology_observed*`, `context_morphology.sqlite` | "
        "Ableitungen unter CC BY-SA 4.0 (wordfreq, Universal Dependencies), viele MB; Häufigkeit ist kein "
        "Nachweis der Schreibweise, die Wörterbücher der Tastatur haben eigene Häufigkeiten. |",
        "| `dictionary_stems*`, `words_dictionary_*`, `words_frequency_matched_*`, `raw/hunspell/*` | "
        "Ableitungen der Hunspell-Wörterbücher (GPL); die Tastatur bringt eigene Hauptwörterbücher mit, "
        "ein zweites Wörterbuch würde Doppelte erzeugen. Nur zum Entscheiden gelesen. |",
        "| `typo_candidates_synthetic.jsonl` | Künstlich erzeugte Tippfehler (20 MB), keine gemessene Fehlerhäufigkeit; "
        "die Engine erzeugt solche Kandidaten selbst. |",
        "| `correction_rules.jsonl`, `confusion_sets.jsonl`, `keyboard_adjacency.jsonl` | Beschreibungen ohne "
        "Laufzeitwert für die Engine (Regeln sind Text, Verwechslungsgruppen brauchen Satzkontext, die Tastatur "
        "kennt die echte Tastengeometrie). Die Regeln stehen als Prüfmaßstab in den Tests. |",
        "| `correction_cases_editorial.*`, `code_switch_cases.jsonl` | Als Testfälle verwendet (`InformalWordsTest`), nicht eingebaut. |",
        "| `abbreviations.jsonl`, `denglish_forms.jsonl` | Schon Teil von `informal_lexicon.jsonl`. |",
        "| `emoji_*`, `raw/unicode`, `raw/cldr` | Gehören nicht zur Autokorrektur (Emoji-Suche hat eigene Daten). |",
        "",
        "## Rechte",
        "",
        "`informal_lexicon.jsonl` ist eigene redaktionelle Zusammenstellung des Pakets unter CC0-1.0; "
        "die Ausgabe enthält nur davon abgeleitete Schreibformen. Das Paket selbst wird nicht ins "
        "Repository gelegt (136 MB, gemischte Lizenzen); die Quelle ist die Datei "
        "`SecondHand_DE_EN_Datensaetze_2026-11-17.zip` des Owners.",
        "",
    ]
    OUT_REPORT.parent.mkdir(parents=True, exist_ok=True)
    OUT_REPORT.write_text("\n".join(lines), encoding="utf-8")
    print(f"kept {len(words)} of {len(lexicon)}; left out:", {k: len(v) for k, v in left_out.items()})


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(Path(sys.argv[1]))
