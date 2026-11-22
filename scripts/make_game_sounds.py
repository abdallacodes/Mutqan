"""Makes the sounds of «فوانيس» (the Practice game) from scratch: soft chimes, a muted crack, shimmers and dings, no music.

    python scripts/make_game_sounds.py

Writes 16-bit mono WAV files to app/src/main/res/raw/game_*.wav. Every sound is made here from sine waves and noise
(no recordings, nothing to license); the same seed gives the same files. The game plays them through SoundPool
(`ui/game/GameSounds.kt`), the right-word chime at a higher pitch as a run of right words grows.
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


def chime(freq, seconds, partials=((1.0, 1.0, 1.0), (2.0, 0.42, 0.62), (3.0, 0.2, 0.42), (4.2, 0.11, 0.3), (5.4, 0.06, 0.22)),
          decay=0.45, attack=0.002):
    """A struck glass or bell tone: partials (ratio, amplitude, share of [decay]) fading on their own."""
    n = int(seconds * RATE)
    out = [0.0] * n
    for ratio, amp, share in partials:
        f = freq * ratio
        if f >= RATE / 2 * 0.92:
            continue
        tau = decay * share
        w = 2 * math.pi * f / RATE
        phase = random.random() * 2 * math.pi
        for i in range(n):
            t = i / RATE
            env = math.exp(-t / tau) * min(1.0, t / attack)
            out[i] += amp * env * math.sin(w * i + phase)
    return out


def sweep(f0, f1, seconds, attack=0.02, curve=1.0, vibrato=0.0, vibrato_rate=6.0):
    """A sine gliding from [f0] to [f1], swelling in and fading out."""
    n = int(seconds * RATE)
    out = [0.0] * n
    phase = 0.0
    for i in range(n):
        p = i / n
        f = f0 + (f1 - f0) * (p ** curve)
        f *= 1 + vibrato * p * math.sin(2 * math.pi * vibrato_rate * i / RATE)
        phase += 2 * math.pi * f / RATE
        env = min(1.0, (i / RATE) / attack) * (1 - p) ** 1.6
        out[i] = env * math.sin(phase)
    return out


def noise(seconds, low=0.0, high=1.0, fade=0.05):
    """Noise between two one-pole filters ([low] and [high] as fractions 0..1 of how much passes), fading out over [fade]."""
    n = int(seconds * RATE)
    out = [0.0] * n
    lp = 0.0
    lp2 = 0.0
    for i in range(n):
        x = random.uniform(-1, 1)
        lp += high * (x - lp)          # keeps the low part up to [high]
        lp2 += low * (lp - lp2)        # what is below [low]
        env = math.exp(-(i / RATE) / fade)
        out[i] = (lp - lp2) * env
    return out


def sparkle(seconds, grains, lo=3000, hi=7500, gain=0.12, density=None):
    """Tiny high pings scattered over [seconds]; [density] (0..1 -> 0..1) shapes when they come."""
    out = silence(seconds)
    for _ in range(grains):
        u = random.random()
        if density:
            u = density(u)
        at = u * seconds * 0.85
        f = random.uniform(lo, hi)
        add(out, chime(f, 0.12, partials=((1.0, 1.0, 1.0),), decay=random.uniform(0.025, 0.07)), at, gain * random.uniform(0.5, 1.0))
    return out


def lowpass(sound, a):
    out = []
    y = 0.0
    for v in sound:
        y += a * (v - y)
        out.append(y)
    return out


def finish(sound, peak, fade_out=0.012):
    """Normalises to [peak] (of full scale) and fades the very ends so nothing clicks."""
    top = max(abs(v) for v in sound) or 1.0
    k = peak / top
    n = len(sound)
    fo = int(fade_out * RATE)
    fi = int(0.0015 * RATE)
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
    print(f'{name:14s} {len(sound) / RATE:5.2f} s  peak {peak:4.2f}  rms {rms:5.3f}  {os.path.getsize(path) // 1024} KB')


def note(semitones_from_a4):
    return 440.0 * 2 ** (semitones_from_a4 / 12)


C5, D5, E5, G5, A5 = note(3), note(5), note(7), note(10), note(12)
B5, C6, D6, E6, G6, A6 = note(14), note(15), note(17), note(19), note(22), note(24)
B6, C7, E7, G7 = note(26), note(27), note(31), note(34)


def main():
    os.makedirs(OUT, exist_ok=True)

    # A right word: a clear glass chime with a soft tap at its start (pitched up in the game as the run grows).
    right = chime(A5, 0.75, decay=0.42)
    add(right, noise(0.01, low=0.25, high=0.9, fade=0.002), 0, 0.22)
    write('game_right', finish(right, 0.82))

    # A wrong lantern: a muted thump, the glass cracking a little.
    wrong = sweep(190, 105, 0.22, attack=0.003, curve=0.5)
    for k, at in enumerate((0.0, 0.018, 0.041, 0.07)):
        add(wrong, noise(0.012, low=0.35, high=0.95, fade=0.004), at, 0.5 * (0.7 ** k))
    wrong = lowpass(wrong, 0.55)
    write('game_wrong', finish(wrong, 0.8, fade_out=0.04))

    # The wanted word floating away: a breath and a falling tone, like a light going out.
    missed = sweep(640, 300, 0.48, attack=0.03, curve=0.8)
    add(missed, lowpass(noise(0.35, low=0.02, high=0.25, fade=0.12), 0.5), 0, 0.5)
    write('game_missed', finish(missed, 0.62, fade_out=0.06))

    # A verse built: a quick upward shimmer of chimes over a scatter of sparkles.
    verse = silence(1.25)
    for k, f in enumerate((C6, D6, E6, G6, A6, C7)):
        add(verse, chime(f, 0.9, decay=0.32), 0.04 * k, 0.55 - 0.03 * k)
    add(verse, sparkle(1.1, 34), 0.05)
    write('game_verse', finish(verse, 0.8, fade_out=0.15))

    # No mistake in it: brighter sparkles and two high chimes, played with the verse.
    perfect = silence(1.0)
    add(perfect, chime(E7, 0.8, decay=0.3), 0.2, 0.5)
    add(perfect, chime(G7, 0.7, decay=0.28), 0.27, 0.45)
    add(perfect, sparkle(0.9, 40, lo=5000, hi=9000, gain=0.14), 0.12)
    write('game_perfect', finish(perfect, 0.6, fade_out=0.12))

    # A longer run (×2, ×3…): a rising rush and a bright ting.
    combo = silence(0.65)
    rush = []
    lp = 0.0
    n = int(0.26 * RATE)
    for i in range(n):
        p = i / n
        a = 0.05 + 0.45 * p
        lp += a * (random.uniform(-1, 1) - lp)
        rush.append(lp * math.sin(math.pi * p) ** 0.8)
    add(combo, rush, 0, 0.6)
    add(combo, chime(C7, 0.4, decay=0.22), 0.22, 0.6)
    write('game_combo', finish(combo, 0.62, fade_out=0.08))

    # The hourglass: everything slows, a gliding tone falling with a slow wobble, two voices a hair apart.
    slow = sweep(900, 190, 0.85, attack=0.04, curve=0.7, vibrato=0.025, vibrato_rate=5)
    add(slow, sweep(906, 191, 0.85, attack=0.04, curve=0.7, vibrato=0.025, vibrato_rate=4.6), 0, 0.8)
    add(slow, lowpass(noise(0.7, low=0.01, high=0.08, fade=0.3), 0.3), 0, 1.2)
    write('game_slow', finish(slow, 0.6, fade_out=0.1))

    # A heart back: two warm notes going up.
    warm = ((1.0, 1.0, 1.0), (2.0, 0.28, 0.6), (3.0, 0.08, 0.4))
    life = silence(1.0)
    add(life, chime(E5, 0.9, partials=warm, decay=0.5, attack=0.006), 0, 0.8)
    add(life, chime(B5, 0.9, partials=warm, decay=0.55, attack=0.006), 0.11, 0.8)
    write('game_life', finish(life, 0.7, fade_out=0.12))

    # A golden lantern: two quick bright chimes and a glitter, like a coin.
    golden = silence(0.8)
    add(golden, chime(B6, 0.6, decay=0.25), 0, 0.6)
    add(golden, chime(E7, 0.7, decay=0.3), 0.065, 0.65)
    add(golden, sparkle(0.7, 26, lo=5000, hi=9500, gain=0.16), 0.05)
    write('game_golden', finish(golden, 0.75, fade_out=0.1))

    # The countdown: a soft wooden tick for each number, and a brighter chime to go.
    tick = chime(1250, 0.12, partials=((1.0, 1.0, 1.0), (2.7, 0.3, 0.5)), decay=0.03)
    add(tick, noise(0.004, low=0.3, high=0.9, fade=0.001), 0, 0.3)
    write('game_tick', finish(tick, 0.55, fade_out=0.03))
    go = silence(0.7)
    add(go, chime(C6, 0.7, decay=0.3), 0, 0.6)
    add(go, chime(G6, 0.7, decay=0.3), 0.012, 0.5)
    write('game_go', finish(go, 0.7, fade_out=0.1))

    # A star landing at the end of a level (played higher for the second and third).
    star = chime(E6, 1.1, decay=0.6)
    add(star, sparkle(0.8, 16, gain=0.1), 0.02)
    write('game_star', finish(star, 0.78, fade_out=0.15))

    # A level won: sparkles thickening, then a bright cluster of chimes.
    clear = silence(1.7)
    add(clear, sparkle(0.7, 60, gain=0.13, density=lambda u: u ** 0.5), 0)
    for k, f in enumerate((C6, E6, G6, C7)):
        add(clear, chime(f, 1.1, decay=0.6), 0.55 + 0.035 * k, 0.5)
    write('game_clear', finish(clear, 0.8, fade_out=0.2))

    # A level lost: three soft notes stepping down, muffled.
    muted = ((1.0, 1.0, 1.0), (2.0, 0.15, 0.5))
    fail = silence(1.1)
    for k, f in enumerate((G5, E5, C5)):
        add(fail, chime(f, 0.7, partials=muted, decay=0.35, attack=0.008), 0.17 * k, 0.7)
    write('game_fail', finish(lowpass(fail, 0.45), 0.62, fade_out=0.12))

    # A button: a very small soft pop.
    tap = sweep(760, 480, 0.05, attack=0.002, curve=0.6)
    write('game_tap', finish(tap, 0.42, fade_out=0.015))


if __name__ == '__main__':
    main()
