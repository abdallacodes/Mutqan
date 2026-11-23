"""Makes the sounds of «فوانيس» (the Practice game) from scratch, as sounds of nature: no musical tones, no music.

    python scripts/make_game_sounds.py

Writes 16-bit mono WAV files to app/src/main/res/raw/game_*.wav. Every sound is shaped here from noise and the rising
"plip" of a water drop (no recordings, nothing to license); the same seed gives the same files. No notes, scales, chords
or melodies: water drops, a twig snapping, a candle blown out, wings, wind, sand, a heartbeat, a flame, a knock on wood,
a splash, a stone into water, rain. The game plays them through SoundPool (`ui/game/GameSounds.kt`).
"""
import array
import math
import os
import random
import wave

RATE = 22050
OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res', 'raw')
random.seed(1447)


def silence(seconds):
    return [0.0] * int(seconds * RATE)


def add(into, sound, at=0.0, gain=1.0):
    """Mixes [sound] into [into] starting [at] seconds in, growing [into] as needed."""
    start = int(at * RATE)
    if len(into) < start + len(sound):
        into.extend([0.0] * (start + len(sound) - len(into)))
    for i, v in enumerate(sound):
        into[start + i] += v * gain
    return into


def white(seconds):
    return [random.uniform(-1, 1) for _ in range(int(seconds * RATE))]


def envelope(sound, attack, decay, hold=0.0):
    """Rises over [attack] seconds, holds, then falls away with time constant [decay]."""
    out = []
    for i, v in enumerate(sound):
        t = i / RATE
        if t < attack:
            g = t / attack
        elif t < attack + hold:
            g = 1.0
        else:
            g = math.exp(-(t - attack - hold) / decay)
        out.append(v * g)
    return out


def lowpass(sound, cutoff):
    a = 1 - math.exp(-2 * math.pi * cutoff / RATE)
    out, y = [], 0.0
    for v in sound:
        y += a * (v - y)
        out.append(y)
    return out


def highpass(sound, cutoff):
    low = lowpass(sound, cutoff)
    return [v - l for v, l in zip(sound, low)]


def bandpass(sound, center, q, center_end=None):
    """A resonant band of [sound] around [center] Hz (gliding to [center_end] over the sound, if given)."""
    out = []
    x1 = x2 = y1 = y2 = 0.0
    n = len(sound)
    coeffs = None
    for i, x in enumerate(sound):
        if coeffs is None or i % 32 == 0:
            f = center if center_end is None else center * (center_end / center) ** (i / max(1, n - 1))
            w = 2 * math.pi * f / RATE
            alpha = math.sin(w) / (2 * q)
            a0 = 1 + alpha
            coeffs = (alpha / a0, 0.0, -alpha / a0, -2 * math.cos(w) / a0, (1 - alpha) / a0)
        b0, b1, b2, a1, a2 = coeffs
        y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, x, y1, y
        out.append(y)
    return out


def drop(f0, rise=2.4, glide=0.045, decay=0.03, seconds=0.14, click=0.25):
    """A water drop: the bubble it leaves rings at a pitch that rises fast as it shrinks ("plip"), with a tiny tap."""
    n = int(seconds * RATE)
    out = [0.0] * n
    k = math.log(rise) / glide
    phase = 0.0
    for i in range(n):
        t = i / RATE
        f = f0 * math.exp(k * min(t, glide * 1.6))
        phase += 2 * math.pi * f / RATE
        out[i] = math.sin(phase) * math.exp(-t / decay) * min(1.0, t / 0.0008)
    if click:
        add(out, envelope(highpass(white(0.004), 1500), 0.0002, 0.0012), 0, click)
    return out


def finish(sound, peak, fade_out=0.012):
    """Normalises to [peak] (of full scale) and fades the very ends so nothing clicks."""
    top = max(abs(v) for v in sound) or 1.0
    k = peak / top
    n = len(sound)
    fo = int(fade_out * RATE)
    # A tenth of a millisecond or two: enough against a click, short enough to keep a snap sharp.
    fi = max(1, int(0.0002 * RATE))
    out = []
    for i, v in enumerate(sound):
        g = k
        if i < fi:
            g *= i / fi
        if i > n - fo:
            g *= max(0.0, (n - i) / fo)
        out.append(v * g)
    return out


def write(name, sound):
    path = os.path.join(OUT, name + '.wav')
    pcm = array.array('h', (int(max(-1.0, min(1.0, v)) * 32767) for v in sound))
    with wave.open(path, 'wb') as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(pcm.tobytes())
    peak = max(abs(v) for v in sound)
    rms = math.sqrt(sum(v * v for v in sound) / len(sound))
    dc = sum(sound) / len(sound)
    print(f'{name:14s} {len(sound) / RATE:5.2f} s  peak {peak:4.2f}  rms {rms:5.3f}  dc {dc:+.4f}  {os.path.getsize(path) // 1024} KB')


def wind(seconds, start, end, q=1.1, attack=0.08, decay=0.25, hold=0.0):
    """A breath of wind: noise in a band gliding from [start] to [end] Hz, swelling and dying away."""
    return envelope(bandpass(white(seconds), start, q, end), attack, decay, hold)


def flap(seconds=0.05):
    """One beat of a bird's wings: a soft "whup" of air."""
    band = bandpass(white(seconds), random.uniform(700, 1100), 0.7)
    return envelope(lowpass(band, 2200), 0.006, 0.014)


def main():
    os.makedirs(OUT, exist_ok=True)

    # A right word: one clear water drop (each one a little different in the game).
    write('game_right', finish(drop(1000), 0.85))

    # A wrong lantern: a dry twig snapping, with a woody knock under it and a few splinters after.
    snap = envelope(highpass(white(0.006), 1500), 0.0002, 0.0016)
    add(snap, envelope(bandpass(white(0.08), 450, 2.5), 0.001, 0.035), 0, 2.5)
    for at, gain in ((0.012, 0.45), (0.027, 0.3), (0.045, 0.2)):
        add(snap, envelope(highpass(white(0.003), 2000), 0.0002, 0.0009), at, gain)
    write('game_wrong', finish(snap + silence(0.12), 0.8, fade_out=0.05))

    # The wanted word floating away: the lantern's light blown out, "pff".
    puff = wind(0.45, 950, 480, q=0.9, attack=0.025, decay=0.11)
    add(puff, envelope(lowpass(white(0.45), 350), 0.025, 0.11), 0, 0.6)
    write('game_missed', finish(puff, 0.7, fade_out=0.08))

    # A verse built: birds taking off, their wings beating away into the sky, over a soft rush of air.
    verse = wind(1.0, 1200, 1700, q=0.6, attack=0.3, decay=0.25, hold=0.1)
    verse = [v * 0.25 for v in verse]
    t = 0.0
    for i in range(13):
        add(verse, flap(), t, 0.95 * 0.88 ** i)
        t += 0.055 + 0.006 * i + random.uniform(-0.005, 0.005)
    write('game_verse', finish(verse, 0.8, fade_out=0.12))

    # No mistake in it: a few drops dripping after, played with the wings.
    perfect = silence(0.95)
    for _ in range(6):
        add(perfect, drop(random.uniform(1300, 2600), decay=random.uniform(0.018, 0.03), click=0.15),
            random.uniform(0.1, 0.8), random.uniform(0.3, 0.55))
    write('game_perfect', finish(perfect, 0.55, fade_out=0.1))

    # A longer run (×2, ×3…): a short gust of wind rising.
    gust = wind(0.45, 400, 1800, q=1.2, attack=0.2, decay=0.08)
    write('game_combo', finish(gust, 0.6, fade_out=0.06))

    # The hourglass: sand trickling, thick at first and thinning out, over a faint hiss.
    sand = silence(1.0)
    t = 0.0
    while t < 0.95:
        p = t / 0.95
        grain = envelope(highpass(white(0.0015), 2200), 0.0001, 0.0005)
        loud = random.uniform(0.1, 0.6) * (1.6 if random.random() < 0.05 else 1.0)
        add(sand, grain, t, loud * min(1.0, t / 0.06) * (1 - p) ** 0.6)
        t += random.expovariate(900 - 500 * p)
    hiss = highpass(white(1.0), 3000)
    add(sand, [v * 0.08 * min(1.0, i / (0.06 * RATE)) * max(0.0, 1 - i / len(hiss)) for i, v in enumerate(hiss)])
    write('game_slow', finish(sand, 0.6, fade_out=0.1))

    # A heart back: a heartbeat, "lub-dub" (a deep body and a knock a phone speaker can carry).
    def beat(body, knock, decay):
        b = envelope(bandpass(white(0.25), body, 1.5), 0.006, decay)
        add(b, envelope(bandpass(white(0.25), knock, 2.0), 0.004, decay * 0.8), 0, 0.7)
        return b
    heart = silence(0.6)
    add(heart, beat(110, 280, 0.045), 0, 1.0)
    add(heart, beat(140, 340, 0.035), 0.22, 0.75)
    write('game_life', finish(heart, 0.9, fade_out=0.08))

    # A golden lantern: its flame flaring up with a few crackles.
    flare = []
    n = int(0.7 * RATE)
    y = 0.0
    for i in range(n):
        t = i / RATE
        cutoff = 300 + 2200 * min(1.0, t / 0.08) if t < 0.08 else 600 + 1900 * math.exp(-(t - 0.08) / 0.12)
        a = 1 - math.exp(-2 * math.pi * cutoff / RATE)
        y += a * (random.uniform(-1, 1) - y)
        flare.append(y * min(1.0, t / 0.03) * math.exp(-max(0.0, t - 0.03) / 0.18))
    for _ in range(10):
        add(flare, envelope(highpass(white(0.002), 2500), 0.0001, 0.0004), random.uniform(0.05, 0.6), random.uniform(0.2, 0.7))
    write('game_golden', finish(flare, 0.75, fade_out=0.08))

    # The countdown: a knock on wood for each number, and a splash to go.
    knock = envelope(bandpass(white(0.1), 650, 4.0), 0.0005, 0.018)
    add(knock, envelope(bandpass(white(0.1), 1500, 3.0), 0.0005, 0.012), 0, 0.4)
    write('game_tick', finish(knock, 0.6, fade_out=0.03))
    splash = envelope(bandpass(white(0.5), 1800, 0.7), 0.004, 0.09)
    add(splash, envelope(lowpass(white(0.4), 400), 0.004, 0.07), 0, 0.8)
    for _ in range(4):
        add(splash, drop(random.uniform(1200, 2400), decay=0.02, click=0.1), random.uniform(0.15, 0.5), random.uniform(0.25, 0.45))
    write('game_go', finish(splash, 0.7, fade_out=0.1))

    # A star landing at the end of a level: a stone dropping into water, "plunk".
    stone = drop(430, rise=2.1, glide=0.07, decay=0.06, seconds=0.4, click=0.0)
    add(stone, envelope(lowpass(white(0.05), 900), 0.001, 0.012), 0, 0.5)
    add(stone, envelope(bandpass(white(0.15), 2000, 0.8), 0.002, 0.03), 0.004, 0.3)
    write('game_star', finish(stone, 0.85, fade_out=0.08))

    # A level won (or a new best): a short shower of rain, swelling and passing, after a breath of wind.
    rain = wind(1.7, 500, 900, q=0.8, attack=0.25, decay=0.3)
    rain = [v * 0.35 for v in rain]
    t = 0.05
    while t < 1.55:
        density = 70 * math.sin(math.pi * min(1.0, t / 1.5)) ** 1.5 + 4
        add(rain, drop(random.uniform(1500, 4200), decay=random.uniform(0.008, 0.02), seconds=0.06, click=0.2),
            t, random.uniform(0.08, 0.35))
        t += random.expovariate(density)
    hiss = bandpass(white(1.7), 4000, 0.7)
    add(rain, [v * 0.12 * math.sin(math.pi * min(1.0, i / (1.6 * RATE))) for i, v in enumerate(hiss)])
    write('game_clear', finish(rain, 0.8, fade_out=0.15))

    # A level lost: the wind dying down, as the lanterns go out.
    fail = wind(1.2, 900, 240, q=1.0, attack=0.08, decay=0.4)
    write('game_fail', finish(fail, 0.65, fade_out=0.15))

    # A button: a pebble's soft tap.
    tap = envelope(bandpass(white(0.04), 2200, 3.0), 0.0003, 0.006)
    write('game_tap', finish(tap, 0.45, fade_out=0.01))


if __name__ == '__main__':
    main()
