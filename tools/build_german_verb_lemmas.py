#!/usr/bin/env python3
"""Extract lowercase German infinitives from the pinned UniMorph data."""

import hashlib
from pathlib import Path
import sys


SOURCE = "https://github.com/unimorph/deu/blob/d226d2112d3490d8f04ece10d4538123d4297a39/deu"
SHA256 = "8bc98487e3caf86188e2c072ebf2dc734f2ca35afef016bb399eb5a43e69d7c0"
OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/assets/agent/german_verb_lemmas.txt"
COMMON_VERBS = {
    "arbeiten", "antworten", "danken", "erklären", "fragen", "glauben", "holen", "hören",
    "kaufen", "kochen", "lachen", "lernen", "lieben", "machen", "meinen", "öffnen", "packen",
    "planen", "reden", "reisen", "räumen", "sagen", "schicken", "schreiben", "schützen",
    "spielen", "stellen", "suchen", "teilen", "tippen", "tragen", "üben", "wohnen", "zählen",
}


def main(source: Path) -> None:
    data = source.read_bytes()
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise SystemExit("Use the pinned UniMorph deu file: " + SOURCE)
    lemmas = set(COMMON_VERBS)
    for line in data.decode("utf-8").splitlines():
        parts = line.split("\t")
        if len(parts) != 3:
            continue
        lemma, word, tags = parts
        if tags == "V;NFIN" and lemma == word and word.isalpha() and word.islower():
            lemmas.add(word)
    lines = [
        "# UniMorph German contributors, based on English Wiktionary contributors",
        "# Source: " + SOURCE,
        "# Adaptation: lowercase alphabetic verb infinitives, supplemented with common verbs, sorted.",
        "# CC BY-SA 3.0: https://creativecommons.org/licenses/by-sa/3.0/",
        *sorted(lemmas),
    ]
    OUTPUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{len(lemmas)} German verb lemmas")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: build_german_verb_lemmas.py <unimorph-deu-file>")
    main(Path(sys.argv[1]))
