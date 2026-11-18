#!/usr/bin/env python3
"""Curates the protected informal words of the keyboard from the SecondHand DE/EN data package.

Input: the unpacked data package (SecondHand_DE_EN_Datensaetze_2026-11-17.zip, version 1.0).
Output: app/src/main/assets/agent/informal_words.txt (one normalised word per line) and a report
(docs/referenzen/datensaetze/AUSWAHL.md) that says what was kept, what was left out and why.

Only `data/informal_lexicon.jsonl` (own editorial work of the package, CC0-1.0) ends up in the output.
What leaves an entry out is decided against the app's own dictionaries (`app/src/main/assets/dicts/main_de.dict`,
`main_en-US.dict`, `main_en-GB.dict`: the words the engine really knows) and the package's frequency list (how
common a form is); nothing derived from them (no frequency, no dictionary word) is written. Run with `python3 -I`
and the package folder:

    python3 -I tools/build_informal_words.py /path/to/unpacked/package

The normal form of a word (NFC, lower case, ' for the curly apostrophe) is the one the app uses
(`InformalWords.normalize`); keep both in step.
"""
import gzip
import json
import struct
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUT_WORDS = REPO / "app/src/main/assets/agent/informal_words.txt"
OUT_REPORT = REPO / "docs/referenzen/datensaetze/AUSWAHL.md"

DICTS = REPO / "app/src/main/assets/dicts"
APP_DICTIONARIES = ("main_de.dict", "main_en-US.dict", "main_en-GB.dict")
MIN_LENGTH = 3             # shorter tokens are too ambiguous (package rule 7)
COMMON_PROBABILITY = 150   # of 255 in the app's dictionaries: a "very common word" for the typo test
COLLISION_OWN_ZIPF = 3.0   # a form used about this rarely in running text is more likely a typo than slang
WORD_CHARS = set("'-")
ALPHABET = "abcdefghijklmnopqrstuvwxyzäöüß'"

# --- reader of the app's binary dictionaries (AOSP/HeliBoard format version 202, static) ---
_CHILDREN_MASK, _MULTI, _TERMINAL, _SHORTCUTS, _BIGRAMS, _NOT_A_WORD, _BLACKLIST = 0xC0, 0x20, 0x10, 0x08, 0x04, 0x02, 0x01


def read_app_dictionary(path: Path) -> dict:
    """word (as stored) -> probability 0..255 for every word the engine treats as valid (not blacklisted, not "not a word")."""
    b = path.read_bytes()
    magic, _version, _flags, header = struct.unpack(">IHHI", b[:12])
    if magic != 0x9BC13AFE:
        raise SystemExit(f"{path}: not a binary dictionary")

    def codepoint(p):
        c = b[p]
        if c == 0x1F:
            return None, p + 1
        if c < 0x20:
            return (c << 16) | (b[p + 1] << 8) | b[p + 2], p + 3
        return c, p + 1

    words, stack, seen = {}, [(header, "")], set()
    while stack:
        pos, prefix = stack.pop()
        if (pos, prefix) in seen:
            continue
        seen.add((pos, prefix))
        count = b[pos]
        pos += 1
        if count & 0x80:
            count = ((count & 0x7F) << 8) | b[pos]
            pos += 1
        for _ in range(count):
            flags = b[pos]
            pos += 1
            chars = []
            if flags & _MULTI:
                while True:
                    cp, pos = codepoint(pos)
                    if cp is None:
                        break
                    chars.append(cp)
            else:
                cp, pos = codepoint(pos)
                chars.append(cp)
            word = prefix + "".join(chr(c) for c in chars)
            probability = None
            if flags & _TERMINAL:
                probability = b[pos]
                pos += 1
            children = flags & _CHILDREN_MASK
            child = None
            if children:
                size = {0x40: 1, 0x80: 2, 0xC0: 3}[children]
                child = pos + int.from_bytes(b[pos:pos + size], "big")
                pos += size
            if flags & _SHORTCUTS:
                pos += (b[pos] << 8) | b[pos + 1]
            if flags & _BIGRAMS:
                while True:
                    f = b[pos]
                    pos += 1
                    pos += (f >> 4) & 3
                    if not f & 0x80:
                        break
            if probability is not None and not flags & (_NOT_A_WORD | _BLACKLIST):
                words[word] = max(words.get(word, 0), probability)
            if child is not None:
                stack.append((child, word))
    return words


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


def intended_words(typo: str):
    """The words [typo] could be a one-slip typing of: a letter missing, a letter too many that sits next to a
    neighbouring letter on the keyboard (or doubles it), the key next to the right one, two letters swapped."""
    for i in range(len(typo) + 1):                       # a letter missing in the typo
        for c in ALPHABET:
            yield typo[:i] + c + typo[i:]
    for i in range(len(typo)):                           # a letter too many
        around = [c for c in (typo[i - 1] if i > 0 else "", typo[i + 1] if i + 1 < len(typo) else "") if c]
        if any(typo[i] == c or any(a(typo[i], c) for a in ADJ) for c in around):
            yield typo[:i] + typo[i + 1:]
    for i in range(len(typo)):                           # the neighbouring key instead of the right one
        for c in ALPHABET:
            if c != typo[i] and any(a(typo[i], c) for a in ADJ):
                yield typo[:i] + c + typo[i + 1:]
    for i in range(len(typo) - 1):                       # two letters swapped
        yield typo[:i] + typo[i + 1] + typo[i] + typo[i + 2:]


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
    app = {}  # lower-case word -> highest probability in the app's dictionaries
    for name in APP_DICTIONARIES:
        for word, probability in read_app_dictionary(DICTS / name).items():
            key = normalize(word)
            app[key] = max(app.get(key, 0), probability)
    known = set(app)  # a capitalised dictionary word counts too: "jungs" is the noun "Jungs", the engine capitalises it
    # "dont", "cant", "youre": a standard word with its apostrophe left out; the engine's correction to the
    # apostrophe form is wanted, so these stay unprotected (package rule 18/19: formal form on explicit choice only)
    with_apostrophe = {w.replace("'", "") for w in known if "'" in w}
    zipf = {lang: read_zipf(data / f"frequency_priors_{lang}.tsv.gz") for lang in ("de", "en")}
    common = {w for w, p in app.items() if p >= COMMON_PROBABILITY and len(w) >= 3}

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
            if own < COLLISION_OWN_ZIPF:
                clash = next((w for w in intended_words(key) if w in common), None)
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
        f"| Schon bekannt | {len(left_out['known_word'])} | Ein Wörterbuch der App (Deutsch, Englisch US und GB) kennt die Form, "
        "auch groß geschrieben („jungs“ ist das Hauptwort „Jungs“); ein Schutz würde nur echte Korrekturen verhindern, auch die "
        "Großschreibung (Regel 20: zwei gültige Wörter braucht Satzbeleg). |",
        f"| Apostroph weggelassen | {len(left_out['apostrophe_omission'])} | „dont“, „cant“, „youre“: Die Korrektur zu „don't“ "
        "ist erwünscht und bleibt (Regel 18/19 des Pakets: die förmliche Form nur auf Wunsch). |",
        f"| Tippfehler-Nähe | {len(left_out['typo_collision'])} | Selten im Netz (Zipf unter 3,0) und einen Tippfehler "
        "(fehlender oder überzähliger Buchstabe, Dreher, Nachbartaste) von einem sehr häufigen Wort der App-Wörterbücher "
        "entfernt: hier wäre der Tippfehler wahrscheinlicher als der Slang. |",
        "",
        "Was „bekannt“ und „sehr häufig“ heißt, entscheiden die Wörterbücher der App (`app/src/main/assets/dicts/main_de.dict`, "
        "`main_en-US.dict`, `main_en-GB.dict`, gelesen mit dem Leser im Skript), nicht die Wortlisten des Pakets: Die Engine "
        "benutzt nur jene. Aus den gelesenen Quellen (App-Wörterbücher, Häufigkeitsliste des Pakets) steht nichts in der Ausgabe.",
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
        "| `context_ngrams*`, `morphology_observed*`, `context_morphology.sqlite` | Ableitungen unter CC BY-SA 4.0 "
        "(Universal Dependencies), viele MB; ein Korpusbeleg beweist keine Schreibweise. Nicht verwendet. |",
        "| `frequency_priors_*` | Ableitung der wordfreq-Daten (CC BY-SA 4.0). Nur beim Auswählen gelesen (wie selten eine Form im Netz vorkommt, "
        "für die Tippfehler-Nähe); nichts davon steht in der Ausgabe. |",
        "| `dictionary_stems*`, `words_dictionary_*`, `words_frequency_matched_*`, `raw/hunspell/*` | "
        "Ableitungen der Hunspell-Wörterbücher (GPL), Stammformen ohne Beugung. Die Tastatur bringt eigene Hauptwörterbücher mit, "
        "nur diese kennt die Engine; die Listen wurden zuerst zum Entscheiden gelesen und sind seit der Nachprüfung 71 ersetzt "
        "durch die App-Wörterbücher (Stämme kennen „gefixt“ und „texte“ anders als die Engine). Nicht verwendet. |",
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
