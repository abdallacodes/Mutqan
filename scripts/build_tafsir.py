#!/usr/bin/env python3
"""
Builds app/src/main/assets/tafsir.db from QUL tafsir databases.

Usage:
    python -X utf8 -I scripts/build_tafsir.py <quran.db> saadi=<file> mukhtasar=<file> aysar=<file> irab=<file>

Each <file> is a SQLite tafsir download from https://qul.tarteel.ai/resources (table `tafsir` with ayah_key,
group_ayah_key, from_ayah, to_ayah, text). Treat them as untrusted data: they are opened read-only and only read.
<quran.db> is the app's own database; it turns "surah:ayah" into the global ayah id the app uses everywhere, and
supplies the verse text that Saadi's "{12}" placeholders stand for.

Output: one table, chunks(source, surah, data). `data` is zlib-compressed JSON, [[fromId, toId, body], ...] in
reading order: a tafsir entry covers the verses fromId..toId (QUL groups verses that are explained together). One
chunk per source and surah compresses far better than one blob per entry. The body text is plain: paragraphs are
separated by a newline, and a quotation from the Quran is wrapped in U+0001 ... U+0002.
"""

import html.parser
import json
import os
import re
import sqlite3
import sys
import zlib

SOURCES = {"saadi": 1, "mukhtasar": 2, "aysar": 3, "irab": 4}
QUOTE_OPEN, QUOTE_CLOSE = "\u0001", "\u0002"
BLOCK = {"p", "div", "br", "h1", "h2", "h3", "h4", "li"}


def open_ro(path):
    return sqlite3.connect("file:" + os.path.abspath(path).replace("\\", "/") + "?mode=ro", uri=True)


class Converter(html.parser.HTMLParser):
    """HTML of a QUL tafsir to plain paragraphs; spans in the Quran font become quotations."""

    def __init__(self, verse_text):
        super().__init__(convert_charrefs=True)
        self.verse_text = verse_text
        self.out = []
        self.stack = []  # for each open tag: whether it opened a quotation
        self.depth = 0   # nesting of quotation spans
        self.numbers = 0

    def handle_starttag(self, tag, attrs):
        if tag in BLOCK:
            self.out.append("\n")
        if tag == "span":
            cls = dict(attrs).get("class") or ""
            quote = "qpc-hafs" in cls
            self.stack.append(quote)
            if quote:
                if self.depth == 0:
                    self.out.append(QUOTE_OPEN)
                self.depth += 1
        else:
            self.stack.append(False)

    def handle_endtag(self, tag):
        if tag in BLOCK:
            self.out.append("\n")
        if not self.stack:
            return
        quote = self.stack.pop()
        if tag == "span" and quote:
            self.depth -= 1
            if self.depth == 0:
                self.out.append(QUOTE_CLOSE)

    def handle_data(self, data):
        if self.depth:
            data = self.quotation(data)
        self.out.append(data)

    def quotation(self, text):
        """`{12}` stands for verse 12 of the surah; `{words}` is a quotation in braces."""
        m = re.fullmatch(r"\s*\{\s*(\d+)\s*\}\s*", text)
        if m and self.verse_text(int(m.group(1))):
            self.numbers += 1
            return self.verse_text(int(m.group(1)))
        return text.replace("{", "﴿").replace("}", "﴾")


def paragraphs(markup):
    text = markup.replace("\xa0", " ")
    lines = [re.sub(r"[ \t\r\f\v]+", " ", line).strip() for line in text.split("\n")]
    return "\n".join(line for line in lines if line)


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    quran = open_ro(sys.argv[1])
    ayah_id = {(s, a): i for i, s, a in quran.execute("SELECT id, surah, ayah FROM ayahs")}
    ayah_text = {(s, a): t for s, a, t in quran.execute("SELECT surah, ayah, text FROM ayahs")}
    sources = {}
    for arg in sys.argv[2:]:
        key, _, path = arg.partition("=")
        if key not in SOURCES or not path:
            sys.exit(f"unknown source {arg!r}; expected one of {sorted(SOURCES)}")
        sources[key] = path

    target = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "tafsir.db")
    if os.path.exists(target):
        os.remove(target)
    out = sqlite3.connect(target)
    out.executescript(
        "CREATE TABLE chunks (source INTEGER NOT NULL, surah INTEGER NOT NULL, data BLOB NOT NULL, PRIMARY KEY (source, surah));"
    )

    surah_of = {i: s for i, s in quran.execute("SELECT id, surah FROM ayahs")}
    for key, path in sources.items():
        src = open_ro(path)
        by_surah = {}
        count = chars = placeholders = 0
        covered = set()
        for ayah_key, group_key, from_key, to_key, text in src.execute(
            "SELECT ayah_key, group_ayah_key, from_ayah, to_ayah, text FROM tafsir ORDER BY rowid"
        ):
            if ayah_key != group_key or not (text or "").strip():
                continue
            surah = int(from_key.split(":")[0])
            first, last = (ayah_id[tuple(int(x) for x in k.split(":"))] for k in (from_key, to_key))
            converter = Converter(lambda n, s=surah: ayah_text.get((s, n)))
            converter.feed(text)
            converter.close()
            body = paragraphs("".join(converter.out))
            if not body:
                continue
            placeholders += converter.numbers
            if surah_of[first] != surah_of[last]:
                sys.exit(f"{key}: the entry {from_key}..{to_key} spans two surahs")
            by_surah.setdefault(surah_of[first], []).append([first, last, body])
            covered.update(range(first, last + 1))
            count += 1
            chars += len(body)
        for surah, entries in by_surah.items():
            data = json.dumps(entries, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            out.execute("INSERT INTO chunks VALUES (?, ?, ?)", (SOURCES[key], surah, zlib.compress(data, 9)))
        missing = 6236 - len(covered)
        print(f"{key}: {count} entries, {chars // 1000}k characters, {placeholders} verse placeholders filled, {missing} verses without text")
        src.close()

    out.execute("PRAGMA user_version = 1")
    out.commit()
    out.execute("VACUUM")
    out.close()
    print(f"-> {os.path.getsize(target) // 1024} KB")


if __name__ == "__main__":
    main()
