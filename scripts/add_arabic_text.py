"""
add_arabic_text.py
------------------
Fetches the full Quran (Uthmani script) from api.alquran.cloud and merges
the Arabic text into the app's quran_metadata.json asset.

Run once from the project root:
    python scripts/add_arabic_text.py
"""

import json
import urllib.request
import os
import sys

ASSET_PATH = os.path.join(
    os.path.dirname(__file__),
    "..", "app", "src", "main", "assets", "quran_metadata.json"
)

API_URL = "https://api.alquran.cloud/v1/quran/quran-uthmani"


def fetch_arabic_text() -> dict[tuple[int, int], str]:
    """Returns a mapping of (surah_id, ayah_number) -> Arabic text."""
    print(f"Fetching Quran text from {API_URL} …")
    req = urllib.request.Request(API_URL, headers={"User-Agent": "QMemo/1.0"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        payload = json.loads(resp.read().decode("utf-8"))

    if payload.get("code") != 200:
        raise RuntimeError(f"API returned unexpected code: {payload.get('code')}")

    text_map: dict[tuple[int, int], str] = {}
    for surah in payload["data"]["surahs"]:
        surah_id = surah["number"]
        for ayah in surah["ayahs"]:
            text_map[(surah_id, ayah["numberInSurah"])] = ayah["text"]

    print(f"  Loaded {len(text_map)} Arabic verses from API.")
    return text_map


def update_metadata(text_map: dict[tuple[int, int], str]) -> None:
    asset = os.path.abspath(ASSET_PATH)
    print(f"Reading {asset} …")

    with open(asset, encoding="utf-8-sig") as f:
        verses: list[dict] = json.load(f)

    missing = 0
    for verse in verses:
        key = (verse["surah_id"], verse["ayah_number"])
        arabic = text_map.get(key, "")
        verse["text_arabic"] = arabic
        if not arabic:
            missing += 1
            print(f"  WARNING: no text for surah {verse['surah_id']} ayah {verse['ayah_number']}")

    print(f"Writing updated JSON ({len(verses)} verses, {missing} missing) …")
    with open(asset, "w", encoding="utf-8") as f:
        json.dump(verses, f, ensure_ascii=False, separators=(",", ":"))

    size_kb = os.path.getsize(asset) / 1024
    print(f"Done. {asset} updated ({size_kb:.0f} KB).")


if __name__ == "__main__":
    try:
        text_map = fetch_arabic_text()
        update_metadata(text_map)
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
