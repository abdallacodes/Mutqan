"""Frames the raw phone screenshots for the Google Play listing.

Input:   store/assets/screens/raw/<n>-<name>.png   straight from the phone (Pixel, 1080x2424)
Output:  store/assets/screens/<n>-<name>.png       1080x1920 (9:16, which Play accepts; 9:20 is refused)

Each output: deep green canvas, an Arabic caption on top, the screenshot below with the status bar cropped and
rounded corners. Chrome renders the page (it shapes Arabic correctly), like make_icon.py.

Run:  python store/assets/make_screenshots.py
Retaking a shot: put the new PNG over the raw one (same name) and run again. Take them in the Paper palette,
Arabic, Light appearance, and never from the Settings sheet (it shows the account name and email).
"""
import io
import os
import subprocess
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
RAW = os.path.join(HERE, "screens", "raw")
OUT = os.path.join(HERE, "screens")

W, H = 1080, 1920
SRC_W, SRC_H = 1080, 2424
CROP_TOP, CROP_BOTTOM = 125, 30      # status bar; a sliver of the gesture bar
PHONE_W = 750                        # the screenshot's width on the canvas
PHONE_TOP = 330

# (file, title, subtitle), in the order they appear in the listing
SHOTS = [
    ("1-today", "مراجعتك اليوم، واضحة ومحدّدة", "الصفحات المستحقة اليوم حسب قوة كل صفحة"),
    ("2-mushaf", "المصحف كاملًا، صفحة بصفحة", "قراءة هادئة بخط واضح وعلامات الآيات"),
    ("3-practice", "تدرّب واختبر حفظك", "أسئلة متنوعة، أغلبها من صفحاتك الأضعف"),
    ("4-progress", "تابع تقدّمك في الحفظ", "المحفوظ وما يُظهره تسميعك وانتظامك"),
    ("5-verse", "استمع وفسّر وعلّم كل آية", "تلاوة وعلامات ومجموعات وتفسير من الآية نفسها"),
    ("6-surahs", "تصفّح السور والأجزاء", "حمّل التلاوة لتستمع دون إنترنت"),
]

PAGE = """<!doctype html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<style>
  @font-face { font-family: "Amiri"; font-weight: 700; src: url("file:///@AMIRI@"); }
  @font-face { font-family: "Plex"; font-weight: 500; src: url("file:///@PLEX@"); }
  html, body { margin: 0; padding: 0; }
  .stage {
    width: @W@px; height: @H@px; overflow: hidden; position: relative; color: #F4EDDC;
    background: radial-gradient(circle at 50% 38%, #1F5A4A 0%, #14463A 55%, #0E3228 100%);
  }
  .ring { position: absolute; border-radius: 50%; border: 2px solid rgba(244,237,220,.07); left: 50%; }
  .ring.a { width: 1500px; height: 1500px; margin-left: -750px; top: -520px; }
  .ring.b { width: 1000px; height: 1000px; margin-left: -500px; top: -270px; border-color: rgba(244,237,220,.09); }
  .head { position: absolute; left: 60px; right: 60px; top: 62px; height: 236px; text-align: center; display: flex; flex-direction: column; justify-content: center; }
  h1 { font: 700 82px/1.2 "Amiri", serif; margin: 0; }
  p { font: 500 36px/1.5 "Plex", sans-serif; margin: 10px 0 0; color: #E4DAC2; }
  .phone {
    position: absolute; top: @PTOP@px; left: @PLEFT@px; width: @PW@px; height: @PH@px; overflow: hidden;
    border-radius: 54px; background: #000; box-shadow: 0 0 0 3px rgba(244,237,220,.28), 0 28px 60px rgba(0,0,0,.45);
  }
  .phone img { display: block; width: @PW@px; margin-top: -@CT@px; }
</style>
</head>
<body>
<div class="stage">
  <div class="ring a"></div><div class="ring b"></div>
  <div class="head"><h1>@TITLE@</h1><p>@SUB@</p></div>
  <div class="phone"><img src="file:///@IMG@"></div>
</div>
</body>
</html>
"""


def chrome_png(page, out):
    subprocess.run([
        CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
        "--window-size=%d,%d" % (W, H), "--screenshot=" + out, "file:///" + page.replace("\\", "/"),
    ], check=True, capture_output=True)


def main():
    scale = PHONE_W / SRC_W
    phone_h = round((SRC_H - CROP_TOP - CROP_BOTTOM) * scale)
    tokens = {
        "AMIRI": os.path.join(ROOT, "quran", "src", "main", "res", "font", "amiri_bold.ttf").replace("\\", "/"),
        "PLEX": os.path.join(ROOT, "app", "src", "main", "res", "font", "ibm_plex_sans_arabic_medium.ttf").replace("\\", "/"),
        "W": str(W), "H": str(H), "PTOP": str(PHONE_TOP), "PLEFT": str((W - PHONE_W) // 2),
        "PW": str(PHONE_W), "PH": str(phone_h), "CT": str(round(CROP_TOP * scale)),
    }
    tmp = tempfile.mkdtemp()
    for name, title, sub in SHOTS:
        raw = os.path.join(RAW, name + ".png")
        if not os.path.exists(raw):
            print("missing", raw)
            continue
        html = PAGE
        for key, value in dict(tokens, TITLE=title, SUB=sub, IMG=raw.replace("\\", "/")).items():
            html = html.replace("@%s@" % key, value)
        page = os.path.join(tmp, name + ".html")
        with io.open(page, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(html)
        out = os.path.join(OUT, name + ".png")
        chrome_png(page, out)
        print("wrote", os.path.relpath(out, ROOT))


if __name__ == "__main__":
    main()
