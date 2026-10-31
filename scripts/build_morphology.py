#!/usr/bin/env python3
"""
Builds app/src/main/assets/morphology.json from QUL's word-by-word root and lemma databases.

Usage:
    python -X utf8 -I scripts/build_morphology.py <word-root.db> <word-lemma.db> [<quran.db>]

The inputs are the SQLite files from https://qul.tarteel.ai/resources/morphology (Word root,
Word lemma). Treat them as untrusted data: they are opened read-only and only read.

Output (compact, parallel arrays sorted by location):
    roots   - root strings with the letters joined, e.g. "كتب"
    lemmas  - lemma strings with diacritics
    loc     - surah * 1_000_000 + ayah * 1000 + word position (1-based, end marker excluded)
    r, l    - index into roots / lemmas for that word, or -1 when the word has none

When <quran.db> (the app's own database) is given, the script checks that QUL's word positions
line up with the app's: every located word must exist there (a few stray end-marker entries are dropped).
"""

import json
import os
import sqlite3
import sys


def open_ro(path):
    return sqlite3.connect("file:" + os.path.abspath(path).replace("\\", "/") + "?mode=ro", uri=True)


def code(location):
    surah, ayah, pos = (int(x) for x in location.split(":"))
    return surah * 1_000_000 + ayah * 1000 + pos


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    root_db, lemma_db = open_ro(sys.argv[1]), open_ro(sys.argv[2])

    roots, root_index = [], {}
    for rid, text in root_db.execute("SELECT id, arabic_trilateral FROM roots ORDER BY id"):
        root_index[rid] = len(roots)
        roots.append("".join(text.split()))

    lemmas, lemma_index = [], {}
    for lid, text in lemma_db.execute("SELECT id, text FROM lemmas ORDER BY id"):
        lemma_index[lid] = len(lemmas)
        lemmas.append(text)

    word_root = {code(loc): root_index[rid] for rid, loc in root_db.execute("SELECT root_id, word_location FROM root_words")}
    word_lemma = {code(loc): lemma_index[lid] for lid, loc in lemma_db.execute("SELECT lemma_id, word_location FROM lemma_words")}

    locs = sorted(set(word_root) | set(word_lemma))

    if len(sys.argv) > 3:
        quran = open_ro(sys.argv[3])
        have = {r[0] * 1_000_000 + r[1] * 1000 + r[2] for r in quran.execute("SELECT surah, ayah, word FROM words WHERE is_end = 0")}
        missing = [c for c in locs if c not in have]
        print(f"words in app: {len(have)}, located here: {len(locs)}, missing in app: {len(missing)}")
        # QUL puts a few entries on a verse's end marker (2:275, 4:176, 13:5); those are dropped. Anything
        # more than a handful means the numbering differs and the data cannot be used as it is.
        if len(missing) > 10:
            sys.exit(f"QUL positions do not line up with the app's words, e.g. {missing[:5]}")
        locs = [c for c in locs if c in have]

    out = {
        "roots": roots,
        "lemmas": lemmas,
        "loc": locs,
        "r": [word_root.get(c, -1) for c in locs],
        "l": [word_lemma.get(c, -1) for c in locs],
    }
    target = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "morphology.json")
    with open(target, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(roots)} roots, {len(lemmas)} lemmas, {len(locs)} words -> {os.path.getsize(target) // 1024} KB")


if __name__ == "__main__":
    main()
