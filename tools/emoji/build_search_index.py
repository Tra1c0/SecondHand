#!/usr/bin/env python3
"""Stage 93a: builds app/src/main/assets/emoji/search_<lang>.tsv from the Unicode CLDR annotations (Unicode License V3).

Input: the cldr-json files annotations/<lang>/annotations.json and annotationsDerived/<lang>/annotations.json
(https://github.com/unicode-org/cldr-json, cldr-annotations-full and cldr-annotations-derived-full), passed as a folder with
<lang>.json and d_<lang>.json. Output line: emoji TAB name TAB keyword|keyword|... (name = the tts entry, keywords = default).
Only the base emojis of the keyboard's category lists (assets/emoji/*.txt) are written, without U+FE0F; the app keeps only
those the device can draw (SupportedEmojis)."""
import json, sys, os

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
# only the base emojis of the keyboard's own category lists (first entry of a line), compared without U+FE0F
def norm(e): return e.replace("\ufe0f", "")
shown = {}  # normalized -> the form the keyboard shows, in the keyboard's order (the category files, sorted by name)
ORDER = ["SMILEYS_AND_EMOTION", "PEOPLE_AND_BODY", "ANIMALS_AND_NATURE", "FOOD_AND_DRINK", "TRAVEL_AND_PLACES", "ACTIVITIES",
         "OBJECTS", "SYMBOLS", "FLAGS"]
files = [f + ".txt" for f in ORDER if os.path.exists(os.path.join(out, f + ".txt"))]
files += sorted(n for n in os.listdir(out) if n.endswith(".txt") and n[:-4].replace("_", "").isupper() and n not in files)
for name in files:
    if True:
        for line in open(os.path.join(out, name), encoding="utf-8"):
            parts = line.split()
            if parts: shown.setdefault(norm(parts[0]), parts[0])
for lang in ("de", "en", "es"):
    rows = {}
    for name, key in ((f"{lang}.json", "annotations"), (f"d_{lang}.json", "annotationsDerived")):
        data = json.load(open(os.path.join(src, name), encoding="utf-8"))[key]["annotations"]
        for emoji, entry in data.items():
            tts = (entry.get("tts") or [""])[0]
            keywords = [k for k in entry.get("default", []) if k and k != tts]
            if norm(emoji) in shown: rows.setdefault(norm(emoji), (tts, keywords))
    with open(os.path.join(out, f"search_{lang}.tsv"), "w", encoding="utf-8", newline="\n") as f:
        f.write("# Unicode CLDR annotations (Unicode License V3, https://www.unicode.org/license.txt); built by tools/emoji/build_search_index.py\n")
        for key in shown:
            if key not in rows: continue
            emoji, (tts, keywords) = shown[key], rows[key]
            clean = [k.replace("\t", " ").replace("|", " ") for k in keywords]
            f.write(f"{emoji}\t{tts.replace(chr(9), ' ')}\t{'|'.join(clean)}\n")
    print(lang, len(rows))
