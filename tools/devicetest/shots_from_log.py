#!/usr/bin/env python3
"""Rebuilds the PNG pictures that the device tests print into the job log (stage 88).

The device tests cannot hand pictures to a reader of the log, so devicetest.py prints small ones as lines

    SHOT <name> <i>/<n> <base64 chunk>

(the keyboard and 200 px above it, at most 540 px wide). This script reads a saved job log, joins the chunks of every
name and writes <name>.png into the output folder. Log prefixes (timestamps) before "SHOT" are ignored; a name with a
missing chunk is reported and skipped.

    python3 -I tools/devicetest/shots_from_log.py <job.log> <out-dir>
"""

import base64
import os
import re
import sys

LINE = re.compile(r"SHOT (\S+) (\d+)/(\d+) ([A-Za-z0-9+/=]*)")


def collect(text):
    """name -> {index: chunk}, and name -> expected count."""
    parts, counts = {}, {}
    for match in LINE.finditer(text):
        name, index, count, chunk = match.group(1), int(match.group(2)), int(match.group(3)), match.group(4)
        parts.setdefault(name, {})[index] = chunk
        counts[name] = count
    return parts, counts


def rebuild(text):
    """name -> PNG bytes for every complete picture; the incomplete names are listed second."""
    parts, counts = collect(text)
    pictures, broken = {}, []
    for name, chunks in parts.items():
        if sorted(chunks) != list(range(1, counts[name] + 1)):
            broken.append(name)
            continue
        data = base64.b64decode("".join(chunks[i] for i in sorted(chunks)))
        if data[:4] != b"\x89PNG":
            broken.append(name)
            continue
        pictures[name] = data
    return pictures, broken


def main(argv):
    if len(argv) != 3:
        print(__doc__)
        return 2
    with open(argv[1], encoding="utf-8", errors="replace") as handle:
        pictures, broken = rebuild(handle.read())
    os.makedirs(argv[2], exist_ok=True)
    for name, data in sorted(pictures.items()):
        with open(os.path.join(argv[2], re.sub(r"[^A-Za-z0-9_.-]", "_", name) + ".png"), "wb") as out:
            out.write(data)
        print("wrote %s (%d bytes)" % (name, len(data)))
    for name in broken:
        print("incomplete: %s" % name)
    return 0 if pictures and not broken else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
