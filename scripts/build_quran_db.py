"""
build_quran_db.py
-----------------
Builds quran/src/main/assets/quran.db from the QUL (qul.tarteel.ai) downloads
for the KFGQPC V2 (1421H) Mushaf: layout, glyph script, Hafs text and metadata.

Inputs (zip files, as downloaded from QUL) are read from --src:
    qpc-v2-15-lines.db.zip            layout (pages table)
    qpc-v2.db.zip                     "QPC V2 Glyph - Word by Word" (words table)
    qpc-hafs-word-by-word.json.zip    plain Hafs text per word
    quran-metadata-{surah-name,ayah,juz,hizb,rub,manzil,ruku,sajda}.json.zip

Run from the project root:
    python scripts/build_quran_db.py --src "C:/Users/<you>/Downloads"
"""

import argparse
import json
import os
import sqlite3
import tempfile
import zipfile

OUT_PATH = os.path.join(
    os.path.dirname(__file__), "..", "quran", "src", "main", "assets", "quran.db"
)

EXPECTED_PAGES = 604
EXPECTED_AYAHS = 6236
EXPECTED_WORDS = 83668

SCHEMA = """
CREATE TABLE surahs (
    id INTEGER PRIMARY KEY,
    name_arabic TEXT NOT NULL,
    name_simple TEXT NOT NULL,
    name_complex TEXT NOT NULL,
    revelation_place TEXT NOT NULL,
    revelation_order INTEGER NOT NULL,
    verses_count INTEGER NOT NULL,
    bismillah_pre INTEGER NOT NULL
);
CREATE TABLE words (
    id INTEGER PRIMARY KEY,
    surah INTEGER NOT NULL,
    ayah INTEGER NOT NULL,
    word INTEGER NOT NULL,
    glyph TEXT NOT NULL,
    hafs TEXT NOT NULL,
    page INTEGER NOT NULL,
    line INTEGER NOT NULL,
    is_end INTEGER NOT NULL
);
CREATE TABLE lines (
    page INTEGER NOT NULL,
    line INTEGER NOT NULL,
    type TEXT NOT NULL,
    is_centered INTEGER NOT NULL,
    first_word_id INTEGER,
    last_word_id INTEGER,
    surah INTEGER,
    PRIMARY KEY (page, line)
);
CREATE TABLE ayahs (
    id INTEGER PRIMARY KEY,
    surah INTEGER NOT NULL,
    ayah INTEGER NOT NULL,
    words_count INTEGER NOT NULL,
    first_word_id INTEGER NOT NULL,
    last_word_id INTEGER NOT NULL,
    text TEXT NOT NULL,
    first_page INTEGER NOT NULL,
    last_page INTEGER NOT NULL,
    juz INTEGER NOT NULL,
    hizb INTEGER NOT NULL,
    rub INTEGER NOT NULL,
    manzil INTEGER NOT NULL,
    ruku INTEGER NOT NULL,
    sajda TEXT
);
CREATE TABLE info (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE INDEX idx_words_page ON words(page, line);
CREATE INDEX idx_words_ayah ON words(surah, ayah, word);
CREATE UNIQUE INDEX idx_ayahs_key ON ayahs(surah, ayah);
CREATE INDEX idx_ayahs_page ON ayahs(first_page);
"""


def unzip(src: str, name: str, dest: str) -> str:
    """Extracts the single data file of <name> into dest and returns its path."""
    with zipfile.ZipFile(os.path.join(src, name)) as zf:
        zf.extractall(dest)
        return os.path.join(dest, zf.namelist()[0])


def load_json(src: str, name: str, dest: str):
    with open(unzip(src, name, dest), encoding="utf-8") as f:
        return json.load(f)


def expand_mapping(units: dict, number_key: str) -> dict[tuple[int, int], int]:
    """(surah, ayah) -> unit number, from QUL 'verse_mapping' ranges like '1-7'."""
    out: dict[tuple[int, int], int] = {}
    for unit in units.values():
        for surah, rng in unit["verse_mapping"].items():
            first, last = (int(x) for x in rng.split("-"))
            for ayah in range(first, last + 1):
                key = (int(surah), ayah)
                assert key not in out, f"{number_key}: {key} mapped twice"
                out[key] = unit[number_key]
    return out


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--src", required=True, help="folder holding the QUL zip files")
    parser.add_argument("--out", default=OUT_PATH)
    args = parser.parse_args()

    with tempfile.TemporaryDirectory() as tmp:
        def sub(name: str) -> str:
            path = os.path.join(tmp, name)
            os.makedirs(path)
            return path

        layout = sqlite3.connect(unzip(args.src, "qpc-v2-15-lines.db.zip", sub("layout")))
        glyphs = sqlite3.connect(unzip(args.src, "qpc-v2.db.zip", sub("glyph")))
        hafs = load_json(args.src, "qpc-hafs-word-by-word.json.zip", sub("hafs"))
        surah_names = load_json(args.src, "quran-metadata-surah-name.json.zip", sub("surah"))
        ayah_meta = load_json(args.src, "quran-metadata-ayah.json.zip", sub("ayah"))
        mappings = {
            unit: expand_mapping(
                load_json(args.src, f"quran-metadata-{unit}.json.zip", sub(unit)),
                f"{unit}_number",
            )
            for unit in ("juz", "hizb", "rub", "manzil", "ruku")
        }
        sajdas = {
            tuple(int(x) for x in s["verse_key"].split(":")): s["sajdah_type"]
            for s in load_json(args.src, "quran-metadata-sajda.json.zip", sub("sajda")).values()
        }

        # --- layout lines; word -> (page, line)
        lines = []
        word_pos: dict[int, tuple[int, int]] = {}
        for page, line, ltype, centered, first, last, surah in layout.execute(
            "SELECT page_number, line_number, line_type, is_centered, first_word_id,"
            " last_word_id, surah_number FROM pages ORDER BY page_number, line_number"
        ):
            first = int(first) if first != "" else None
            last = int(last) if last != "" else None
            surah = int(surah) if surah != "" else None
            lines.append((page, line, ltype, int(centered), first, last, surah))
            if ltype == "ayah":
                for wid in range(first, last + 1):
                    assert wid not in word_pos, f"word {wid} placed twice"
                    word_pos[wid] = (page, line)

        # --- words
        # the last word of every ayah is its end marker (is_end = 1)
        last_word = {
            (s, a): w for s, a, w in glyphs.execute("SELECT surah, ayah, MAX(word) FROM words GROUP BY surah, ayah")
        }
        words = []
        for wid, surah, ayah, word, glyph in glyphs.execute(
            "SELECT id, surah, ayah, word, text FROM words ORDER BY id"
        ):
            h = hafs[f"{surah}:{ayah}:{word}"]
            assert h["id"] == wid, f"hafs/glyph id mismatch at {surah}:{ayah}:{word}"
            page, line = word_pos[wid]
            words.append((wid, surah, ayah, word, glyph, h["text"], page, line, int(word == last_word[(surah, ayah)])))

        # --- ayahs (id = global ayah number 1..6236); text = plain Hafs words, no end marker
        hafs_text = {w[0]: w[5] for w in words}
        span: dict[tuple[int, int], list[int]] = {}
        for w in words:
            span.setdefault((w[1], w[2]), []).append(w[0])
        ayahs = []
        mismatched = []
        for a in sorted(ayah_meta.values(), key=lambda x: x["id"]):
            key = (a["surah_number"], a["ayah_number"])
            ids = span[key]
            # the last word of every ayah is its end marker
            if len(ids) - 1 != a["words_count"]:
                mismatched.append((key, a["words_count"], len(ids) - 1))
            pages = [word_pos[i][0] for i in ids]
            ayahs.append((
                a["id"], key[0], key[1], len(ids) - 1, min(ids), max(ids),
                " ".join(hafs_text[i] for i in ids[:-1]), min(pages), max(pages),
                mappings["juz"][key], mappings["hizb"][key], mappings["rub"][key],
                mappings["manzil"][key], mappings["ruku"][key], sajdas.get(key),
            ))

        layout.close()
        glyphs.close()
        for key, meta, actual in mismatched:
            print(f"  note: {key[0]}:{key[1]} metadata says {meta} words, script has {actual}")

        # --- checks
        assert len(lines) == 9046 and len({l[0] for l in lines}) == EXPECTED_PAGES
        assert len(words) == EXPECTED_WORDS and len(word_pos) == EXPECTED_WORDS
        assert len(ayahs) == EXPECTED_AYAHS
        assert len(surah_names) == 114

        # --- write
        if os.path.exists(args.out):
            os.remove(args.out)
        db = sqlite3.connect(args.out)
        db.executescript(SCHEMA)
        db.executemany(
            "INSERT INTO surahs VALUES (?,?,?,?,?,?,?,?)",
            [(s["id"], s["name_arabic"], s["name_simple"], s["name"],
              s["revelation_place"], s["revelation_order"], s["verses_count"],
              int(s["bismillah_pre"])) for s in surah_names.values()],
        )
        db.executemany("INSERT INTO words VALUES (?,?,?,?,?,?,?,?,?)", words)
        db.executemany("INSERT INTO lines VALUES (?,?,?,?,?,?,?)", lines)
        db.executemany("INSERT INTO ayahs VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", ayahs)
        db.executemany("INSERT INTO info VALUES (?,?)", [
            ("source", "QUL (qul.tarteel.ai) - KFGQPC QCF V2 (1421H)"),
            ("pages", str(EXPECTED_PAGES)),
            ("lines_per_page", "15"),
            ("font_pattern", "p{page}.ttf"),
        ])
        db.commit()
        db.execute("VACUUM")
        db.close()

    print(f"Wrote {os.path.abspath(args.out)} ({os.path.getsize(args.out) / 1e6:.2f} MB): "
          f"{len(ayahs)} ayahs, {len(words)} words, {len(lines)} lines.")


if __name__ == "__main__":
    main()
