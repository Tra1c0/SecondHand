#!/usr/bin/env python3
"""Builds the Emoji-Mix pair table (stage 25a) from Emoji Kitchen metadata.

Input: metadata.json of https://github.com/xsalazar/emoji-kitchen-backend (app/metadata.json),
downloaded by hand; the file is about 99 MB and is not committed. Licence status and the checksum
of the input used for the committed asset: docs/BUILD.md -> Emoji-Mix-Tabelle.

Output: app/src/main/assets/emoji_mix_pairs.bin (gzip data, but no ".gz" suffix: the Android build unpacks
assets ending in .gz and drops the suffix), read by EmojiMixTable.read (agent-core).
Format (big endian, then gzip -9 without file name and with mtime 0, so the output is reproducible):

    "EMX1"
    u16 N                       emojis, sorted by code points
    N x (u8 length, ASCII)      code points as in the metadata, lowercase hex joined by "-" (e.g. "2764-fe0f")
    u8 D                        dates
    D x 8 ASCII                 "YYYYMMDD"
    N x ceil(N / 8) bytes       bit (left i, right j) = byte [i * stride + j / 8], mask 1 << (j % 8): the pair exists
    u32 P                       pairs
    P x u8                      date index of each pair, in order of left then right

Only the newest image of each pair is kept (isLatest). Every pair is stored once, in the order of the
metadata (left, right); the app tries the reversed order itself. The script checks that every
gStaticUrl follows the pattern the app builds (EmojiMixUrls) and stops otherwise.

Run: python3 -I tools/emoji_mix_table.py <metadata.json> [--out <asset>]
"""
import argparse
import gzip
import hashlib
import io
import json
import pathlib
import struct
import sys

BASE = "https://www.gstatic.com/android/keyboard/emojikitchen/"
DEFAULT_OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/emoji_mix_pairs.bin"


def path_part(codepoints: str) -> str:
    return "-".join("u" + part for part in codepoints.split("-"))


def expected_url(date: str, left: str, right: str) -> str:
    return f"{BASE}{date}/{path_part(left)}/{path_part(left)}_{path_part(right)}.png"


def valid_codepoints(codepoints: str) -> bool:
    """Lowercase hex joined by "-", 1 to 6 digits each, each a Unicode scalar value (as EmojiMixTable.read checks)."""
    parts = codepoints.split("-")
    if not codepoints or any(not 1 <= len(p) <= 6 or any(c not in "0123456789abcdef" for c in p) for p in parts):
        return False
    return all(int(p, 16) <= 0x10FFFF and not 0xD800 <= int(p, 16) <= 0xDFFF for p in parts)


def sort_key(codepoints: str):
    return [int(part, 16) for part in codepoints.split("-")]


def build(metadata: dict) -> tuple[bytes, dict]:
    data = metadata["data"]
    pairs = {}
    for emoji in data.values():
        for entries in emoji["combinations"].values():
            for entry in entries:
                if not entry.get("isLatest"):
                    continue
                left, right, date = entry["leftEmojiCodepoint"], entry["rightEmojiCodepoint"], entry["date"]
                if entry["gStaticUrl"] != expected_url(date, left, right):
                    raise SystemExit(f"unexpected URL pattern: {entry['gStaticUrl']}")
                if len(date) != 8 or not date.isdigit():
                    raise SystemExit(f"unexpected date: {date}")
                old = pairs.get((left, right))
                if old is not None and old != date:
                    raise SystemExit(f"two newest dates for {left} {right}")
                pairs[(left, right)] = date
    emojis = sorted({code for pair in pairs for code in pair} | set(data.keys()), key=sort_key)
    for code in emojis:
        if not valid_codepoints(code) or len(code) > 255:
            raise SystemExit(f"unexpected code points: {code}")
    index = {code: i for i, code in enumerate(emojis)}
    dates = sorted(set(pairs.values()))
    date_index = {date: i for i, date in enumerate(dates)}
    if len(emojis) > 0xFFFF or len(dates) > 0xFF:
        raise SystemExit("table too large for the format")
    stride = (len(emojis) + 7) // 8
    bitmap = bytearray(stride * len(emojis))
    for left, right in pairs:
        i, j = index[left], index[right]
        bitmap[i * stride + j // 8] |= 1 << (j % 8)
    ordered = sorted(pairs, key=lambda pair: (index[pair[0]], index[pair[1]]))
    out = io.BytesIO()
    out.write(b"EMX1")
    out.write(struct.pack(">H", len(emojis)))
    for code in emojis:
        raw = code.encode("ascii")
        out.write(struct.pack(">B", len(raw)))
        out.write(raw)
    out.write(struct.pack(">B", len(dates)))
    for date in dates:
        out.write(date.encode("ascii"))
    out.write(bitmap)
    out.write(struct.pack(">I", len(ordered)))
    out.write(bytes(date_index[pairs[pair]] for pair in ordered))
    stats = {"emojis": len(emojis), "pairs": len(pairs), "dates": len(dates), "raw_bytes": out.tell()}
    return out.getvalue(), stats


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("metadata", type=pathlib.Path)
    parser.add_argument("--out", type=pathlib.Path, default=DEFAULT_OUT)
    args = parser.parse_args()
    raw = args.metadata.read_bytes()
    table, stats = build(json.loads(raw))
    compressed = io.BytesIO()
    with gzip.GzipFile(filename="", mode="wb", fileobj=compressed, compresslevel=9, mtime=0) as stream:
        stream.write(table)
    args.out.write_bytes(compressed.getvalue())
    print(f"input sha256 {hashlib.sha256(raw).hexdigest()} ({len(raw)} bytes)")
    print(f"{stats['emojis']} emojis, {stats['pairs']} pairs, {stats['dates']} dates, {stats['raw_bytes']} bytes raw")
    print(f"wrote {args.out} ({len(compressed.getvalue())} bytes, sha256 {hashlib.sha256(compressed.getvalue()).hexdigest()})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
