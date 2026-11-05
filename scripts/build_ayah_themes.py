#!/usr/bin/env python3
"""
Builds app/src/main/assets/ayah_themes.json from QUL's ayah themes database.

Usage:
    python -X utf8 -I scripts/build_ayah_themes.py <ayah-themes.db> <quran.db>

<ayah-themes.db> is the SQLite file from https://qul.tarteel.ai/resources/ayah-theme (treat it as untrusted data:
it is opened read-only and only read). <quran.db> is the app's own database, used to turn "surah, ayah" into the
global ayah id the app uses everywhere.

Output: {"t": [[firstAyahId, lastAyahId, "theme"], ...]} in reading order. The themes are English. QUL lists each
passage twice and gives a keyword list cut short ("Supplic,Allah"), so duplicates are dropped and the keywords are not kept.
"""

import json
import os
import re
import sqlite3
import sys


def open_ro(path):
    return sqlite3.connect("file:" + os.path.abspath(path).replace("\\", "/") + "?mode=ro", uri=True)


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    themes, quran = open_ro(sys.argv[1]), open_ro(sys.argv[2])
    ayah_id = {(s, a): i for i, s, a in quran.execute("SELECT id, surah, ayah FROM ayahs")}

    seen, out, unknown = set(), [], 0
    for theme, surah, first, last in themes.execute("SELECT theme, surah_number, ayah_from, ayah_to FROM themes"):
        key = (surah, first, last)
        if key in seen:
            continue
        seen.add(key)
        if (surah, first) not in ayah_id or (surah, last) not in ayah_id:
            unknown += 1
            continue
        text = re.sub(r"\s+", " ", theme or "").strip()
        if text:
            out.append([ayah_id[(surah, first)], ayah_id[(surah, last)], text])
    out.sort()

    for (a_first, a_last, _), (b_first, _, _) in zip(out, out[1:]):
        if b_first <= a_last:
            sys.exit(f"passages overlap at ayah id {b_first}")

    target = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "ayah_themes.json")
    with open(target, "w", encoding="utf-8") as f:
        json.dump({"t": out}, f, ensure_ascii=False, separators=(",", ":"))
    covered = sum(last - first + 1 for first, last, _ in out)
    print(f"{len(out)} passages covering {covered} ayahs ({unknown} unknown skipped) -> {os.path.getsize(target) // 1024} KB")


if __name__ == "__main__":
    main()
