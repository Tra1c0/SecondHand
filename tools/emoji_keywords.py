#!/usr/bin/env python3
"""Builds the emoji keyword table for tolerant emoji suggestions (stage 35).

Input: the npm packages cldr-annotations-full and cldr-annotations-derived-full (CLDR JSON,
Unicode License V3), unpacked by hand; version and checksums of the input used for the committed
asset: docs/BUILD.md -> Emoji-Stichwörter. Only the emojis of the app's own emoji palette
(app/src/main/assets/emoji/*.txt, without EMOTICONS) are kept, in palette order and in the palette's
spelling (with variation selectors), so every suggestion is an emoji the keyboard can show.

Output: app/src/main/assets/emoji_keywords.bin (gzip data, but no ".gz" suffix: the Android build
unpacks assets ending in .gz and drops the suffix), read by EmojiKeywords.read (agent-core).
Format (UTF-8 text, then gzip -9 without file name and with mtime 0, so the output is reproducible):

    EKW1
    @<language>                      de, en, es
    <emoji>\t<name>\t<keyword>|<keyword>|...     one line per emoji, in palette order

<name> is the CLDR short name (tts). Emojis missing from the base annotations come from the derived
ones (flags, families, hair styles, directions); a flag's only keyword is the country part of its
name ("Flagge: Deutschland" -> "Deutschland"). Skin tone variants are left out (suggestions use the
default skin tone). Tabs, line breaks and "|" never occur in the data; the
script stops if they do.

Run: python3 -I tools/emoji_keywords.py <cldr-annotations-full dir> <cldr-annotations-derived-full dir> [--out <asset>]
"""
import argparse
import gzip
import hashlib
import io
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DEFAULT_OUT = ROOT / "app/src/main/assets/emoji_keywords.bin"
PALETTE_DIR = ROOT / "app/src/main/assets/emoji"
PALETTES = ["SMILEYS_AND_EMOTION", "PEOPLE_AND_BODY", "ANIMALS_AND_NATURE", "FOOD_AND_DRINK",
            "TRAVEL_AND_PLACES", "ACTIVITIES", "OBJECTS", "SYMBOLS", "FLAGS"]
LANGUAGES = ["de", "en", "es"]
FE0F = "️"


def palette() -> list[str]:
    emojis = []
    for name in PALETTES:
        for line in (PALETTE_DIR / f"{name}.txt").read_text(encoding="utf-8").splitlines():
            if line.strip():
                emojis.append(line.split(" ")[0])
    return emojis


def package_dir(path: pathlib.Path) -> pathlib.Path:
    return path / "package" if (path / "package").is_dir() else path


def check(text: str) -> str:
    if any(c in text for c in "\t\n\r|"):
        sys.exit(f"unexpected separator in {text!r}")
    return text.strip()


def is_flag(key: str) -> bool:
    return any(0x1F1E6 <= ord(c) <= 0x1F1FF for c in key) or "\U000E0067" in key


def build(full: pathlib.Path, derived: pathlib.Path) -> bytes:
    emojis = palette()
    out = io.StringIO()
    out.write("EKW1\n")
    for lang in LANGUAGES:
        base = json.loads((package_dir(full) / f"annotations/{lang}/annotations.json")
                          .read_text(encoding="utf-8"))["annotations"]["annotations"]
        der = json.loads((package_dir(derived) / f"annotationsDerived/{lang}/annotations.json")
                         .read_text(encoding="utf-8"))["annotationsDerived"]["annotations"]
        by_key = {k.replace(FE0F, ""): v for k, v in base.items()}
        extra = {k.replace(FE0F, ""): v for k, v in der.items()
                 if not any(0x1F3FB <= ord(c) <= 0x1F3FF for c in k)}
        out.write(f"@{lang}\n")
        count = 0
        for emoji in emojis:
            key = emoji.replace(FE0F, "")
            entry = by_key.get(key)
            keywords: list[str]
            if entry is not None:
                name = check(entry.get("tts", [""])[0])
                keywords = [check(k) for k in entry.get("default", [])]
            elif key in extra and is_flag(key):
                name = check(extra[key].get("tts", [""])[0])
                keywords = [name.split(":", 1)[1].strip() if ":" in name else name]
            elif key in extra:
                name = check(extra[key].get("tts", [""])[0])
                keywords = [check(k) for k in extra[key].get("default", [])]
            else:
                continue
            keywords = [k for k in dict.fromkeys(keywords) if k]
            if not name and not keywords:
                continue
            out.write(f"{emoji}\t{name}\t{'|'.join(keywords)}\n")
            count += 1
        print(f"{lang}: {count} of {len(emojis)} palette emojis", file=sys.stderr)
    raw = out.getvalue().encode("utf-8")
    buffer = io.BytesIO()
    with gzip.GzipFile(filename="", mode="wb", fileobj=buffer, compresslevel=9, mtime=0) as gz:
        gz.write(raw)
    return buffer.getvalue()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("full", type=pathlib.Path)
    parser.add_argument("derived", type=pathlib.Path)
    parser.add_argument("--out", type=pathlib.Path, default=DEFAULT_OUT)
    args = parser.parse_args()
    data = build(args.full, args.derived)
    args.out.write_bytes(data)
    print(f"{args.out}: {len(data)} bytes, sha256 {hashlib.sha256(data).hexdigest()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
