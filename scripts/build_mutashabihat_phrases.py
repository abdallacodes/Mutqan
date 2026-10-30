"""Build app/src/main/assets/mutashabihat_phrases.json from QUL's "Mutashabihat ul Quran" phrases.

Usage: python scripts/build_mutashabihat_phrases.py <phrases.json>

Each phrase becomes {"id", "t": text, "v": [[ayahId, fromWord, toWord], ...]} where ayahId is the
global ayah id of the :quran module's quran.db and the word range is 1-based and inclusive.
Phrases are ordered by how many ayahs share them (most first), then by id.
"""
import json
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB = ROOT / "quran" / "src" / "main" / "assets" / "quran.db"
OUT = ROOT / "app" / "src" / "main" / "assets" / "mutashabihat_phrases.json"


def main(src: str) -> None:
    phrases = json.loads(Path(src).read_text(encoding="utf-8"))
    db = sqlite3.connect(DB)
    ayah_id = {f"{s}:{a}": i for i, s, a in db.execute("SELECT id, surah, ayah FROM ayahs")}
    words = dict(db.execute("SELECT id, words_count FROM ayahs"))

    def text(key: str, first: int, last: int) -> str:
        s, a = map(int, key.split(":"))
        rows = db.execute(
            "SELECT hafs FROM words WHERE surah=? AND ayah=? AND word BETWEEN ? AND ? ORDER BY word",
            (s, a, first, last),
        )
        return " ".join(r[0] for r in rows)

    out = []
    for pid, p in phrases.items():
        src_ref = p["source"]
        verses = []
        for key, ranges in p["ayah"].items():
            for first, last in ranges[:1]:  # a verse is listed once; its first occurrence
                verses.append([ayah_id[key], first, min(last, words[ayah_id[key]])])  # QUL has one range past the ayah end (48:10)
        verses.sort()
        out.append({"id": int(pid), "t": text(src_ref["key"], src_ref["from"], src_ref["to"]), "v": verses})
    out.sort(key=lambda p: (-len(p["v"]), p["id"]))
    OUT.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"{len(out)} phrases, {OUT.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main(sys.argv[1])
