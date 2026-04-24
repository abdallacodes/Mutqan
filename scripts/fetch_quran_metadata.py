"""
Fetches Quranic verse metadata for all 6,236 verses from the AlQuran.cloud API
and writes a JSON file formatted for direct import into the Room VerseEntity table.

Output JSON shape (array of objects):
  [
    { "id": 1, "surah_id": 1, "ayah_number": 1, "page_number": 1, "juz_id": 1 },
    ...
  ]

Usage:
  python scripts/fetch_quran_metadata.py

The file is written to: app/src/main/assets/quran_metadata.json
AlQuran.cloud API docs: https://alquran.cloud/api
"""

import json
import time
import urllib.request
import urllib.error
from pathlib import Path

API_BASE = "https://api.alquran.cloud/v1/surah"
OUTPUT_PATH = Path(__file__).parent.parent / "app" / "src" / "main" / "assets" / "quran_metadata.json"
TOTAL_SURAHS = 114
REQUEST_DELAY_S = 0.15   # polite delay between requests (~17 s total)
MAX_RETRIES = 3


def fetch_surah(surah_number: int) -> list[dict]:
    url = f"{API_BASE}/{surah_number}"
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            with urllib.request.urlopen(url, timeout=15) as response:
                payload = json.loads(response.read().decode())
            ayahs = payload["data"]["ayahs"]
            return ayahs
        except (urllib.error.URLError, KeyError, json.JSONDecodeError) as exc:
            print(f"  Attempt {attempt}/{MAX_RETRIES} failed for surah {surah_number}: {exc}")
            if attempt < MAX_RETRIES:
                time.sleep(2 ** attempt)   # exponential back-off
    raise RuntimeError(f"Failed to fetch surah {surah_number} after {MAX_RETRIES} attempts")


def main() -> None:
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)

    all_verses: list[dict] = []
    verse_id = 0

    print(f"Fetching metadata for {TOTAL_SURAHS} surahs from {API_BASE} …\n")

    for surah_number in range(1, TOTAL_SURAHS + 1):
        print(f"  Surah {surah_number:3d}/{TOTAL_SURAHS} … ", end="", flush=True)
        ayahs = fetch_surah(surah_number)

        for ayah in ayahs:
            verse_id += 1
            all_verses.append({
                "id":          verse_id,
                "surah_id":    surah_number,
                "ayah_number": ayah["numberInSurah"],
                "page_number": ayah["page"],
                "juz_id":      ayah["juz"],
            })

        print(f"{len(ayahs)} ayahs  (running total: {verse_id})")
        time.sleep(REQUEST_DELAY_S)

    print(f"\nTotal verses fetched: {verse_id}")
    assert verse_id == 6236, f"Expected 6236 verses, got {verse_id}"

    with OUTPUT_PATH.open("w", encoding="utf-8") as f:
        json.dump(all_verses, f, indent=2, ensure_ascii=False)

    size_kb = OUTPUT_PATH.stat().st_size / 1024
    print(f"Written to: {OUTPUT_PATH}  ({size_kb:.1f} KB)")


if __name__ == "__main__":
    main()
