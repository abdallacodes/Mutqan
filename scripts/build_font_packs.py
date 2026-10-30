"""
build_font_packs.py
-------------------
Turns the QUL "QPC V2 Font - Page by Page" download (604 TTFs) into the per-juz
zip packs the app downloads on demand, plus a manifest.json describing them.

Each page font is subset to the glyphs its page uses and stripped of hinting.
Packs follow the juz start pages stored in quran/src/main/assets/quran.db, so every
page belongs to exactly one pack.

Needs fontTools (pip install fonttools). Run from the project root:
    python scripts/build_font_packs.py --fonts "C:/Users/<you>/Downloads/QPC V2 Font.ttf.bz2" --out <dir>

(The QUL file is named .bz2 but is a zip archive.)
"""

import argparse
import hashlib
import io
import json
import os
import sqlite3
import zipfile

from fontTools import subset
from fontTools.ttLib import TTFont

DB_PATH = os.path.join(
    os.path.dirname(__file__), "..", "quran", "src", "main", "assets", "quran.db"
)
PAGES = 604


def subset_font(data: bytes, codepoints: set[int]) -> bytes:
    font = TTFont(io.BytesIO(data))
    opts = subset.Options()
    opts.hinting = False
    opts.layout_features = []
    opts.glyph_names = False
    opts.notdef_outline = True
    opts.name_IDs = [1, 2, 4, 6]
    opts.legacy_kern = False
    opts.drop_tables += ["GSUB", "GPOS", "GDEF", "DSIG", "kern", "FFTM"]
    subsetter = subset.Subsetter(opts)
    subsetter.populate(unicodes=codepoints | {0x20, 0xA0})
    subsetter.subset(font)
    out = io.BytesIO()
    font.save(out)
    return out.getvalue()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fonts", required=True, help="QUL 'QPC V2 Font' TTF archive")
    parser.add_argument("--out", required=True, help="empty folder for packs + manifest")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)

    db = sqlite3.connect(DB_PATH)
    glyph_text = dict(db.execute("SELECT id, glyph FROM words"))
    page_words = {
        p: [w for a, b in db.execute(
            "SELECT first_word_id, last_word_id FROM lines WHERE page=? AND type='ayah'", (p,))
            for w in range(a, b + 1)]
        for p in range(1, PAGES + 1)
    }
    juz_start = dict(db.execute("SELECT juz, MIN(first_page) FROM ayahs GROUP BY juz"))
    db.close()
    assert len(juz_start) == 30 and juz_start[1] == 1

    packs = []
    with zipfile.ZipFile(args.fonts) as src:
        for juz in range(1, 31):
            first = juz_start[juz]
            last = juz_start[juz + 1] - 1 if juz < 30 else PAGES
            name = f"qpc-v2-juz{juz:02d}.zip"
            path = os.path.join(args.out, name)
            with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
                for page in range(first, last + 1):
                    used = {ord(c) for w in page_words[page] for c in glyph_text[w]}
                    zf.writestr(f"p{page}.ttf", subset_font(src.read(f"p{page}.ttf"), used))
            with open(path, "rb") as f:
                digest = hashlib.sha256(f.read()).hexdigest()
            packs.append({
                "file": name, "juz": juz, "first_page": first, "last_page": last,
                "bytes": os.path.getsize(path), "sha256": digest,
            })
            print(f"{name}: pages {first}-{last}, {os.path.getsize(path) / 1e6:.1f} MB")

    manifest = {
        "source": "QUL (qul.tarteel.ai) - KFGQPC QCF V2 (1421H), subset per page",
        "font_pattern": "p{page}.ttf",
        "packs": packs,
    }
    with open(os.path.join(args.out, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
    total = sum(p["bytes"] for p in packs)
    print(f"{len(packs)} packs, {total / 1e6:.1f} MB total")


if __name__ == "__main__":
    main()
