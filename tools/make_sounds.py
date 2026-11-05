#!/usr/bin/env python3
# The menu's sounds, all made here from nothing: a breeze with far off birds, a soft door creak, a whoosh, a little tap, and a gentle tune.
#   python tools/make_sounds.py
import os

import numpy as np
import soundfile as sf

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'charmenu', 'sounds')
SR = 44100
rng = np.random.default_rng(2026)


def save(name, x, sr=SR):
    x = np.asarray(x, dtype=np.float64)
    peak = np.max(np.abs(x))
    if peak > 0.98:
        x = x / peak * 0.98
    channels = 1 if x.ndim == 1 else x.shape[1]
    # written in small pieces: the Vorbis writer falls over when handed a long sound in one go
    with sf.SoundFile(os.path.join(OUT, name + '.ogg'), 'w', sr, channels, format='OGG', subtype='VORBIS') as f:
        for i in range(0, len(x), 8192):
            f.write(x[i:i + 8192])
    print(name, round(os.path.getsize(os.path.join(OUT, name + '.ogg')) / 1024), 'KB')


def lowpass_noise(n, cut, seed=None):
    # Noise through a gentle filter, made in frequency space so it can wrap round perfectly for looping.
    r = rng if seed is None else np.random.default_rng(seed)
    spec = np.fft.rfft(r.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    spec *= 1 / (1 + (f / cut) ** 2)
    out = np.fft.irfft(spec, n)
    return out / np.max(np.abs(out))


def bird(duration, base, sweep, wobble, seed):
    # One little chirp: a pure tone that bends with an amplitude bump.
    r = np.random.default_rng(seed)
    t = np.arange(int(duration * SR)) / SR
    freq = base + sweep * np.sin(np.pi * t / duration) + wobble * np.sin(2 * np.pi * r.uniform(18, 30) * t)
    phase = 2 * np.pi * np.cumsum(freq) / SR
    env = np.sin(np.pi * t / duration) ** 1.5
    return np.sin(phase) * env


def ambience():
    # 40 seconds that loop with no seam: slow-breathing wind in the leaves plus a few distant birds.
    seconds = 40
    n = seconds * SR
    wind = lowpass_noise(n, 420, 1)
    leaves = lowpass_noise(n, 3200, 2)
    t = np.arange(n) / SR
    swell = 0.55 + 0.25 * np.sin(2 * np.pi * t * 2 / seconds) + 0.2 * np.sin(2 * np.pi * t * 5 / seconds + 1)
    rustle = np.clip(0.28 + 0.4 * np.sin(2 * np.pi * t * 7 / seconds + 2) * np.sin(2 * np.pi * t * 3 / seconds), 0.02, 1)
    mix = wind * 0.34 * swell + leaves * 0.05 * rustle
    r = np.random.default_rng(9)
    for i in range(22):
        at = r.uniform(0, seconds - 2)
        base = r.uniform(2300, 4200)
        notes = r.integers(2, 6)
        pos = at
        for k in range(notes):
            d = r.uniform(0.06, 0.16)
            b = bird(d, base * r.uniform(0.92, 1.12), r.uniform(200, 900), r.uniform(0, 120), int(at * 100) + k)
            start = int(pos * SR)
            mix[start:start + len(b)] += b * r.uniform(0.010, 0.024)
            pos += d + r.uniform(0.02, 0.1)
    # stays mono so the game can place it anywhere
    save('ambience', mix * 0.8)


def door():
    # A soft old wooden door: stick-slip clicks that slowly drift in pitch, ringing the wood's few low notes, then a quiet settle.
    seconds = 1.05
    n = int(seconds * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    pos = 0.02
    rate = 34.0
    r = np.random.default_rng(3)
    pulses = np.zeros(n)
    while pos < 0.86:
        i = int(pos * SR)
        pulses[i] += r.uniform(0.55, 1.0) * (0.6 + 0.4 * np.sin(np.pi * pos / 0.86))
        rate += r.uniform(-1.5, 2.2) + 26 * (0.86 - pos) * 0.05
        pos += (1 / rate) * r.uniform(0.82, 1.2)
    # wood body: a few decaying resonances
    bodies = [(310, 0.018, 1.0), (520, 0.014, 0.8), (880, 0.009, 0.55), (1340, 0.006, 0.4), (170, 0.03, 0.8)]
    kn = int(0.09 * SR)
    kt = np.arange(kn) / SR
    kernel = sum(a * np.exp(-kt / d) * np.sin(2 * np.pi * f * kt) for f, d, a in bodies)
    x = np.convolve(pulses, kernel)[:n]
    x += lowpass_noise(n, 900, 5) * 0.03 * np.exp(-((t - 0.45) / 0.3) ** 2)
    # the leaf reaching its stop
    i = int(0.9 * SR)
    thud = np.exp(-np.arange(int(0.13 * SR)) / (0.028 * SR)) * np.sin(2 * np.pi * 95 * np.arange(int(0.13 * SR)) / SR)
    x[i:i + len(thud)] += thud[:n - i] * 1.1
    env = np.minimum(1, t / 0.02) * np.minimum(1, (seconds - t) / 0.05)
    x = x * env
    save('door', x / np.max(np.abs(x)) * 0.5)


def whoosh():
    # Air rushing past: noise through a band that sweeps up and back down.
    seconds = 1.9
    n = int(seconds * SR)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n)
    out = np.zeros(n)
    low = np.zeros(2)
    centre = 240 + 1500 * np.sin(np.pi * t / seconds) ** 2
    bw = 0.35
    # a simple moving band filter
    a1, a2 = 0.0, 0.0
    y1, y2 = 0.0, 0.0
    for i in range(n):
        w = 2 * np.pi * centre[i] / SR
        r = np.exp(-bw * w)
        c1 = 2 * r * np.cos(w)
        c2 = -r * r
        y = noise[i] * (1 - r) + c1 * y1 + c2 * y2
        y2, y1 = y1, y
        out[i] = y
    env = np.sin(np.pi * t / seconds) ** 1.7
    out = out * env
    save('whoosh', out / np.max(np.abs(out)) * 0.35)


def tap():
    # The button sound: a tiny wooden tock.
    n = int(0.09 * SR)
    t = np.arange(n) / SR
    x = np.exp(-t / 0.012) * np.sin(2 * np.pi * 900 * t) + 0.5 * np.exp(-t / 0.02) * np.sin(2 * np.pi * 460 * t)
    save('tap', x * 0.4)


def pluck(freq, seconds, bright=0.5, seed=1):
    # A plucked string (Karplus-Strong), close to a koto.
    n = int(seconds * SR)
    period = max(2, int(SR / freq))
    r = np.random.default_rng(seed)
    buf = r.uniform(-1, 1, period)
    # soften the start of the string so it isn't harsh
    for _ in range(2):
        buf = (buf + np.roll(buf, 1)) / 2
    out = np.zeros(n)
    idx = 0
    damp = 0.996 + 0.0025 * bright
    prev = 0.0
    for i in range(n):
        v = buf[idx]
        out[i] = v
        buf[idx] = damp * (0.5 * v + 0.5 * prev)
        prev = v
        idx = (idx + 1) % period
    out *= np.minimum(1, np.arange(n) / 80)
    return out


def music():
    # A slow, gentle tune on a pentatonic scale: soft plucks over a very quiet pad. Loops after 64 seconds.
    seconds = 64
    n = seconds * SR
    mix = np.zeros((n, 2))
    scale = [261.63, 293.66, 329.63, 392.0, 440.0]
    notes = [s * m for m in (0.5, 1, 2) for s in scale]
    r = np.random.default_rng(14)
    beat = 1.6
    # the tune walks up and down the scale, resting often
    pos = 7
    bar = 0
    t0 = 0.0
    while t0 < seconds - 6:
        if r.random() < 0.72:
            pos = int(np.clip(pos + r.choice([-2, -1, -1, 1, 1, 2]), 3, 13))
            f = notes[pos]
            d = pluck(f, 4.5, 0.4, int(t0 * 37) + pos) * r.uniform(0.16, 0.26)
            pan = r.uniform(0.3, 0.7)
            s = int(t0 * SR)
            e = min(n, s + len(d))
            mix[s:e, 0] += d[:e - s] * (1 - pan)
            mix[s:e, 1] += d[:e - s] * pan
        t0 += beat * r.choice([0.5, 0.5, 1, 1, 1.5, 2])
    t = np.arange(n) / SR
    pad_root = [130.81, 110.0, 146.83, 98.0]
    pad = np.zeros(n)
    part = n // len(pad_root)
    for i, root in enumerate(pad_root):
        tt = np.arange(part + SR * 2) / SR
        env = np.sin(np.pi * np.clip(tt / ((part + SR * 2) / SR), 0, 1)) ** 0.8
        tone = sum(a * np.sin(2 * np.pi * root * m * tt + m) for m, a in ((1, 1.0), (1.5, 0.45), (2, 0.5), (3, 0.14)))
        s = i * part
        e = min(n, s + len(tone))
        pad[s:e] += (tone * env)[:e - s] * 0.04
        if s + len(tone) > n:
            pad[:s + len(tone) - n] += (tone * env)[e - s:] * 0.04
    mix[:, 0] += pad
    mix[:, 1] += pad
    # a slow fade at both ends makes the loop point gentle
    fade = np.minimum(1, np.minimum(t / 1.5, (seconds - t) / 3))
    mix *= fade[:, None]
    save('music', mix / np.max(np.abs(mix)) * 0.7)


def purr():
    # A small cat's purr: a low rumble pulsing about 26 times a second, swelling as it breathes in and out. Loops after 8 seconds.
    seconds = 8
    n = seconds * SR
    t = np.arange(n) / SR
    noise = lowpass_noise(n, 420, 21)
    # a whole number of pulses and breaths so the end meets the start
    pulses = 0.5 + 0.5 * np.sin(2 * np.pi * t * 26)
    pulses = pulses ** 2.5
    breath = 0.55 + 0.45 * np.sin(2 * np.pi * t * 3 / seconds) ** 2
    tone = np.sin(2 * np.pi * t * 52) * 0.5 + np.sin(2 * np.pi * t * 78) * 0.25
    x = (noise * 0.9 + tone * 0.5) * pulses * breath
    save('purr', x / np.max(np.abs(x)) * 0.6)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    ambience()
    door()
    whoosh()
    tap()
    music()
    purr()
