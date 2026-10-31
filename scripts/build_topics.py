#!/usr/bin/env python3
"""
Builds app/src/main/assets/topics.json from QUL's topics (concepts) database.

Usage:
    python -X utf8 -I scripts/build_topics.py <topics.db> <quran.db>

<topics.db> is the SQLite file from https://qul.tarteel.ai/resources (topics); treat it as untrusted data:
it is opened read-only and only read. <quran.db> is the app's own database, used to turn "surah:ayah" into the
global ayah id the app uses everywhere.

Output: {"t": [[id, name, arabic, thematicParent, ontologyParent, parent, description, [related], [ayahIds]], ...]}
Parents are topic ids or -1. Names are English; `arabic` is "" when QUL has none. A few structural topics (the roots
of the two trees and the children of the thematic roots) get an Arabic name from ARABIC below, so the Arabic
interface can be browsed from the top. Ayah ids are sorted, i.e. in reading order.
"""

import json
import os
import re
import sqlite3
import sys

# Arabic names for the structural topics that QUL names only in English.
ARABIC = {
    "Doctraine": "العقيدة",
    "Stories": "القصص",
    "The Unseen": "الغيب",
    "Basic tenets": "أصول العقيدة",
    "Relationship with Allah": "العلاقة مع الله",
    "Relationship with people": "العلاقة مع الناس",
    "Prophets(25 mentioned by name)": "الأنبياء (المذكورون بأسمائهم)",
    "Other-Stories": "قصص أخرى",
    "Parabels": "الأمثال",
    "Unseen": "عالم الغيب",
    "Road to the hereafter": "الطريق إلى الآخرة",
    "Artifact": "مصنوعات",
    "Astronomical Body": "أجرام سماوية",
    "False Deity": "آلهة باطلة",
    "Holy Book": "كتب مقدسة",
    "Living Creation": "المخلوقات الحية",
    "Location": "أماكن",
    "Physical Attribute": "صفات جسمية",
    "Physical Substance": "مواد",
    "Weather Phenomena": "ظواهر جوية",
}

TAG = re.compile(r"<[^>]+>")


def open_ro(path):
    return sqlite3.connect("file:" + os.path.abspath(path).replace("\\", "/") + "?mode=ro", uri=True)


def clean(text):
    return re.sub(r"\s+", " ", TAG.sub("", text or "")).strip()


def ids(text):
    return [int(x) for x in (text or "").replace(" ", "").split(",") if x]


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    topics, quran = open_ro(sys.argv[1]), open_ro(sys.argv[2])
    ayah_id = {(s, a): i for i, s, a in quran.execute("SELECT id, surah, ayah FROM ayahs")}

    out, links, unknown = [], 0, 0
    for tid, name, arabic, parent, thematic_parent, ontology_parent, description, related, ayahs in topics.execute(
        "SELECT topic_id, name, arabic_name, parent_id, thematic_parent_id, ontology_parent_id, description, related_topics, ayahs "
        "FROM topics ORDER BY topic_id"
    ):
        verses = []
        for token in (ayahs or "").replace(" ", "").split(","):
            if not token:
                continue
            s, a = (int(x) for x in token.split(":"))
            if (s, a) in ayah_id:
                verses.append(ayah_id[(s, a)])
            else:
                unknown += 1
        verses = sorted(set(verses))
        links += len(verses)
        out.append([
            tid,
            name.strip(),
            (arabic or "").strip() or ARABIC.get(name.strip(), ""),
            thematic_parent if thematic_parent is not None else -1,
            ontology_parent if ontology_parent is not None else -1,
            parent if parent is not None else -1,
            clean(description),
            ids(related),
            verses,
        ])

    known = {t[0] for t in out}
    for t in out:
        for p in t[3:6]:
            if p != -1 and p not in known:
                sys.exit(f"topic {t[0]} points to a missing parent {p}")

    target = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "topics.json")
    with open(target, "w", encoding="utf-8") as f:
        json.dump({"t": out}, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(out)} topics, {links} verse links ({unknown} unknown verses skipped) -> {os.path.getsize(target) // 1024} KB")


if __name__ == "__main__":
    main()
