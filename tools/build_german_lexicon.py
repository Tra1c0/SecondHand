#!/usr/bin/env python3
"""Builds the two German lexicon files of the sentence check (stages 104/105) from pinned, freely licensed sources.

    python3 -I tools/build_german_lexicon.py <unimorph-deu-file> <german-nouns-csv>

- app/src/main/assets/agent/german_verb_forms.txt: the finite forms of German verbs that do not follow the regular
  pattern (the pattern itself is GermanVerbs.kt in agent-core; this script mirrors it). Source: UniMorph German, the same
  pinned file as tools/build_german_verb_lemmas.py (CC BY-SA 3.0). UniMorph lacks "sein" and "tun"; their forms are added
  from the standard paradigm below. Only verbs whose infinitive or a finite form the German main dictionary knows with a
  frequency of at least VERB_MIN are kept; the regular verbs need no line (the bundled lemma list covers them).
- app/src/main/assets/agent/german_nouns.txt: every case form of the German nouns of the German Wiktionary with gender
  and number, from the data set german-nouns (CC BY-SA 4.0), only nouns the main dictionary knows (the lemma or a form,
  any case) with a frequency of at least NOUN_MIN; names (first and last names, places) are left out. One line per form:
  form, a 16-bit mask of the readings in hex (bit = 4 * gender + case; genders m, f, n, plural; cases N, G, D, A) and the
  frequency of the form in the main dictionary (as written or in lower case, -1 unknown). Sorted by UTF-16 code units
  for a binary search.
"""

import csv
import hashlib
import importlib.util
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UNIMORPH_SOURCE = "https://github.com/unimorph/deu/blob/d226d2112d3490d8f04ece10d4538123d4297a39/deu"
UNIMORPH_SHA256 = "8bc98487e3caf86188e2c072ebf2dc734f2ca35afef016bb399eb5a43e69d7c0"
NOUNS_SOURCE = "https://github.com/gambolputty/german-nouns/blob/da71a2bc519b952b28c9b0b80971cff6efb508f6/german_nouns/nouns.csv"
NOUNS_SHA256 = "7b34ca76bb753fa5d3d2b683ecc987824ae874b375ac999a797074fafba2768e"
VERBS_OUT = ROOT / "app/src/main/assets/agent/german_verb_forms.txt"
NOUNS_OUT = ROOT / "app/src/main/assets/agent/german_nouns.txt"
MAIN_DICT = ROOT / "app/src/main/assets/dicts/main_de.dict"
LEMMAS = ROOT / "app/src/main/assets/agent/german_verb_lemmas.txt"
VERB_MIN = 60
NOUN_MIN = 70

SLOTS = {  # UniMorph tags -> slot names of GermanVerbs.kt (P present, T past, C subjunctive I, K subjunctive II)
    **{"V;IND;%s;%d;PRS" % (n, p): "P%d" % (p + (3 if n == "PL" else 0)) for n in ("SG", "PL") for p in (1, 2, 3)},
    **{"V;IND;%s;%d;PST" % (n, p): "T%d" % (p + (3 if n == "PL" else 0)) for n in ("SG", "PL") for p in (1, 2, 3)},
    **{"V;SBJV;%s;%d;PRS" % (n, p): "C%d" % (p + (3 if n == "PL" else 0)) for n in ("SG", "PL") for p in (1, 2, 3)},
    **{"V;SBJV;%s;%d;PST" % (n, p): "K%d" % (p + (3 if n == "PL" else 0)) for n in ("SG", "PL") for p in (1, 2, 3)},
}
ORDER = ["P%d" % i for i in range(1, 7)] + ["T%d" % i for i in range(1, 7)] + ["C%d" % i for i in range(1, 7)] \
    + ["K%d" % i for i in range(1, 7)]
# not in UniMorph; the standard paradigm (Duden grammar), every slot given
SUPPLEMENT = {
    "sein": {"P1": "bin", "P2": "bist", "P3": "ist", "P4": "sind", "P5": "seid", "P6": "sind",
             "T1": "war", "T2": "warst", "T3": "war", "T4": "waren", "T5": "wart", "T6": "waren",
             "C1": "sei", "C2": "seist", "C3": "sei", "C4": "seien", "C5": "seiet", "C6": "seien",
             "K1": "wäre", "K2": "wärst", "K3": "wäre", "K4": "wären", "K5": "wärt", "K6": "wären"},
    "tun": {"P1": "tue", "P2": "tust", "P3": "tut", "P4": "tun", "P5": "tut", "P6": "tun",
            "T1": "tat", "T2": "tatest", "T3": "tat", "T4": "taten", "T5": "tatet", "T6": "taten",
            "C1": "tue", "C2": "tuest", "C3": "tue", "C4": "tuen", "C5": "tuet", "C6": "tuen",
            "K1": "täte", "K2": "tätest", "K3": "täte", "K4": "täten", "K5": "tätet", "K6": "täten"},
}
NOUN_CASES = {"nominativ": 0, "genitiv": 1, "dativ": 2, "akkusativ": 3}
GENDERS = {"m": 0, "f": 1, "n": 2}
PLURAL = 3
NAME_POS = {"Toponym", "Vorname", "Nachname", "Eigenname"}


def checked(path: Path, sha: str, source: str) -> bytes:
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest() != sha:
        raise SystemExit("Use the pinned file: " + source)
    return data


def dictionary():
    spec = importlib.util.spec_from_file_location("informal", ROOT / "tools/build_informal_words.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    words = module.read_app_dictionary(MAIN_DICT)
    return lambda w: max(words.get(w, -1), words.get(w.lower(), -1))


# --- the regular pattern, the same as GermanVerbs.kt ---

def stem_of(inf):
    if len(inf) > 4 and (inf.endswith("eln") or inf.endswith("ern")):
        return inf[:-1], "EL"
    if inf.endswith("en") and len(inf) > 3:
        return inf[:-2], "EN"
    if inf.endswith("n") and len(inf) > 2:
        return inf[:-1], "N"
    return None, None


def needs_e(s, kind):
    if kind != "EN":
        return False
    if s[-1] in "dt":
        return True
    return s[-1] in "mn" and len(s) >= 2 and s[-2] not in "aeiouäöüylrmnh"


def past_forms(t):
    """T1..T6 (also K1..K6) from the first person singular."""
    if t.endswith("e"):
        return [t, t + "st", t, t + "n", t + "t", t + "n"]
    if t[-1] in "dt":
        return [t, t + "est", t, t + "en", t + "et", t + "en"]
    if t[-1] in "sßxz":
        return [t, t + "t", t, t + "en", t + "t", t + "en"]
    return [t, t + "st", t, t + "en", t + "t", t + "en"]


def derive(inf, over):
    s, kind = stem_of(inf)
    if s is None:
        return None
    e = "e" if needs_e(s, kind) else ""
    sib = s[-1] in "sßxz"
    p1 = s + "e"
    if kind == "EN":
        p2 = s + e + ("t" if sib and not e else "st")
        p3 = s + e + "t"
    else:
        p2, p3 = s + "st", s + "t"
    present = [over.get("p1", p1), over.get("p2", p2), over.get("p3", p3), inf, over.get("p5", p3), inf]
    past = past_forms(over.get("t", s + e + "te"))
    subj2 = past_forms(over.get("k", over.get("t", s + e + "te")))
    subj1 = [s + "e", s + "est", s + "e", inf, s + "et", inf]
    forms = dict(zip(ORDER, present + past + subj1 + subj2))
    for slot in ORDER:
        if slot in over:
            forms[slot] = over[slot]
    return forms


def build_verbs(unimorph: str, frequency) -> list:
    lemmas = {line for line in LEMMAS.read_text(encoding="utf-8").splitlines() if line and not line.startswith("#")}
    paradigms = {}
    for line in unimorph.splitlines():
        parts = [p.strip() for p in line.split("\t")]
        # separable verbs have their forms in two words ("komme an"): the base verb covers the finite part
        if len(parts) != 3 or parts[2] not in SLOTS or " " in parts[1] or not parts[1]:
            continue
        paradigms.setdefault(parts[0], {}).setdefault(SLOTS[parts[2]], set()).add(parts[1])
    for lemma, forms in SUPPLEMENT.items():
        paradigms[lemma] = {slot: {form} for slot, form in forms.items()}
    lines = []
    for lemma in sorted(paradigms):
        if lemma not in lemmas and lemma not in SUPPLEMENT:
            continue
        slots = paradigms[lemma]
        if max([frequency(lemma)] + [frequency(f) for forms in slots.values() for f in forms]) < VERB_MIN:
            continue

        def best(slot):
            return max(sorted(slots[slot]), key=frequency) if slot in slots else None

        regular = derive(lemma, {})
        if regular is None:
            continue
        over = {}
        for key, slot in (("p1", "P1"), ("p2", "P2"), ("p3", "P3"), ("p5", "P5"), ("t", "T1"), ("k", "K1")):
            if slot in slots and regular[slot] not in slots[slot]:
                over[key] = best(slot)
        if lemma in SUPPLEMENT:
            over = dict(SUPPLEMENT[lemma])
        derived = derive(lemma, over)
        for slot in ORDER:
            if slot in slots and derived[slot] not in slots[slot]:
                over[slot] = best(slot)
        if over:
            fields = ["%s=%s" % (k, over[k]) for k in sorted(over)]
            lines.append(lemma + "\t" + "\t".join(fields))
    return lines


def build_nouns(rows, frequency) -> list:
    masks = {}
    for row in rows:
        pos = set(row["pos"].split(","))
        if "Substantiv" not in pos or pos & NAME_POS:
            continue
        genders = [g for g in (row["genus"], row["genus 1"], row["genus 2"], row["genus 3"], row["genus 4"]) if g in GENDERS]
        forms = {}
        for key, value in row.items():
            value = (value or "").strip()
            if not key or not value:
                continue
            parts = key.split(" ")
            if len(parts) < 2 or parts[0] not in NOUN_CASES or parts[1].rstrip("*") not in ("singular", "plural"):
                continue
            if not value.isalpha():  # "-algie", phrases
                continue
            case = NOUN_CASES[parts[0]]
            if parts[1].startswith("plural"):
                forms[value] = forms.get(value, 0) | 1 << (4 * PLURAL + case)
            else:
                if not genders:
                    continue
                for g in genders:
                    forms[value] = forms.get(value, 0) | 1 << (4 * GENDERS[g] + case)
        if not forms or max(frequency(f) for f in list(forms) + [row["lemma"]]) < NOUN_MIN:
            continue
        for form, mask in forms.items():
            masks[form] = masks.get(form, 0) | mask
    return ["%s\t%x\t%d" % (form, masks[form], frequency(form))
            for form in sorted(masks, key=lambda w: w.encode("utf-16-be"))]


def main(unimorph_path: Path, nouns_path: Path) -> None:
    unimorph = checked(unimorph_path, UNIMORPH_SHA256, UNIMORPH_SOURCE).decode("utf-8")
    nouns = checked(nouns_path, NOUNS_SHA256, NOUNS_SOURCE).decode("utf-8")
    frequency = dictionary()
    verbs = build_verbs(unimorph, frequency)
    VERBS_OUT.write_text("\n".join([
        "# UniMorph German contributors, based on English Wiktionary contributors; sein and tun: standard paradigm",
        "# Source: " + UNIMORPH_SOURCE,
        "# Adaptation: the finite forms that differ from the regular pattern of GermanVerbs.kt (tools/build_german_lexicon.py)",
        "# CC BY-SA 3.0: https://creativecommons.org/licenses/by-sa/3.0/",
        *verbs]) + "\n", encoding="utf-8")
    rows = list(csv.DictReader(nouns.splitlines()))
    noun_lines = build_nouns(rows, frequency)
    NOUNS_OUT.write_text("\n".join([
        "# German Wiktionary contributors, compiled by german-nouns (gambolputty)",
        "# Source: " + NOUNS_SOURCE,
        "# Adaptation: case forms with gender/number mask and main dictionary frequency (tools/build_german_lexicon.py)",
        "# CC BY-SA 4.0: https://creativecommons.org/licenses/by-sa/4.0/",
        *noun_lines]) + "\n", encoding="utf-8")
    print("%d irregular German verbs, %d German noun forms" % (len(verbs), len(noun_lines)))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: build_german_lexicon.py <unimorph-deu-file> <german-nouns-csv>")
    main(Path(sys.argv[1]), Path(sys.argv[2]))
