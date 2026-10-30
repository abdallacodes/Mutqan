"""Build app/src/main/assets/mutashabihat_similar.json from QUL's "matching-ayah" data.

Usage: python scripts/build_mutashabihat_similar.py <matching-ayah.json>

Output:
  "groups": ready-made vault groups, each {"id", "d": description, "m": [ayahId, ...],
            "w": [[ayahId, first, last, first, last, ...], ...]} where "w" marks the matched
            word ranges (1-based, inclusive) that QUL gives for an ayah. A group is the set of ayahs one ayah
            matches plus that ayah; identical sets are kept once and sets contained in a larger
            one are dropped.
  "ayahs":  for every ayah that has a match, [[otherAyahId, score, first, last, first, last, ...], ...]
            (matches are made symmetric; the word ranges, none when unknown, are in the other ayah).
Ayah ids are the global ids of the :quran module's quran.db.
"""
import json
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB = ROOT / "quran" / "src" / "main" / "assets" / "quran.db"
OUT = ROOT / "app" / "src" / "main" / "assets" / "mutashabihat_similar.json"
SNIPPET_WORDS = 5


def main(src: str) -> None:
    data = json.loads(Path(src).read_text(encoding="utf-8"))
    db = sqlite3.connect(DB)
    ids = {f"{s}:{a}": i for i, s, a in db.execute("SELECT id, surah, ayah FROM ayahs")}
    surah_name = dict(db.execute("SELECT id, name_arabic FROM surahs"))
    words_count = dict(db.execute("SELECT id, words_count FROM ayahs"))
    key_of = {i: k for k, i in ids.items()}

    def snippet(ayah_id: int) -> str:
        s, a = map(int, key_of[ayah_id].split(":"))
        rows = db.execute(
            "SELECT hafs FROM words WHERE surah=? AND ayah=? AND is_end=0 AND word<=? ORDER BY word", (s, a, SNIPPET_WORDS)
        )
        return " ".join(r[0] for r in rows)

    ranges = {}   # (source, matched) -> (first, last) of the words in the matched ayah
    scores = {}   # frozenset({a, b}) -> best score
    sets = {}
    for src_key, matches in data.items():
        a = ids[src_key]
        members = {a}
        for m in matches:
            b = ids[m["matched_ayah_key"]]
            members.add(b)
            spans = [r if len(r) == 2 else [r[0], r[0]] for r in m["match_words"]]
            ranges[(a, b)] = [x for first, last in spans for x in (first, min(last, words_count[b]))]
            pair = frozenset((a, b))
            scores[pair] = max(scores.get(pair, 0), m["score"])
        sets.setdefault(frozenset(members), a)

    kept = []
    for s in sorted(sets, key=lambda s: (-len(s), min(s))):
        if not any(s < t for t in kept):
            kept.append(s)
    kept.sort(key=min)

    groups = []
    for n, s in enumerate(kept, 1):
        members = sorted(s)
        w = []
        for m in members:
            for (a, b), flat in ranges.items():
                if b == m and a in s:
                    w.append([m, *flat])
                    break
        first = members[0]
        sura, ayah = map(int, key_of[first].split(":"))
        groups.append({"id": n, "d": f"{surah_name[sura]} {ayah}: {snippet(first)}", "m": members, "w": w})

    ayahs = {}
    for pair, score in scores.items():
        x, y = tuple(pair)
        for a, b in ((x, y), (y, x)):
            ayahs.setdefault(a, []).append([b, score, *ranges.get((a, b), [])])
    for lst in ayahs.values():
        lst.sort(key=lambda r: (-r[1], r[0]))

    OUT.write_text(
        json.dumps({"groups": groups, "ayahs": {str(k): v for k, v in sorted(ayahs.items())}},
                   ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(f"{len(groups)} groups, {len(ayahs)} ayahs, {OUT.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main(sys.argv[1])
