#!/usr/bin/env python3
# Makes the menu's small textures: tiling soft clouds, a petal, a lantern glow, the cat icon for the settings button.
#   python tools/make_art.py
import json
import os

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'charmenu', 'textures', 'menu')


def tiling_noise(size, cells, rng):
    # Smooth noise that wraps at the edges, made from a coarse random grid.
    grid = rng.random((cells, cells))
    xs = np.arange(size) * cells / size
    xi = xs.astype(int)
    xf = xs - xi
    xf = xf * xf * (3 - 2 * xf)
    a = grid[np.ix_(xi % cells, xi % cells)]
    b = grid[np.ix_((xi + 1) % cells, xi % cells)]
    c = grid[np.ix_(xi % cells, (xi + 1) % cells)]
    d = grid[np.ix_((xi + 1) % cells, (xi + 1) % cells)]
    fx = xf[:, None]
    fy = xf[None, :]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(size, base, octaves, rng, gain=0.55):
    v = np.zeros((size, size))
    amp = 1.0
    tot = 0.0
    cells = base
    for _ in range(octaves):
        v += amp * tiling_noise(size, cells, rng)
        tot += amp
        amp *= gain
        cells *= 2
    return v / tot


def clouds():
    # Red = big soft cloud masses, green = finer wisps. The sky shader sets the coverage, so only the shapes matter here.
    rng = np.random.default_rng(11)
    size = 512
    big = fbm(size, 5, 6, rng, 0.52)
    big = (big - big.min()) / (big.max() - big.min())
    fine = fbm(size, 11, 5, rng, 0.6)
    fine = (fine - fine.min()) / (fine.max() - fine.min())
    img = np.zeros((size, size, 4), dtype=np.uint8)
    img[..., 0] = (big * 255).astype(np.uint8)
    img[..., 1] = (fine * 255).astype(np.uint8)
    img[..., 2] = 0
    img[..., 3] = 255
    Image.fromarray(img, 'RGBA').save(os.path.join(OUT, 'clouds.png'))
    with open(os.path.join(OUT, 'clouds.png.mcmeta'), 'w') as f:
        json.dump({'texture': {'blur': True, 'clamp': False}}, f)


def petal():
    # One soft petal: a rounded teardrop with a notch at the tip and a slightly deeper middle.
    size = 32
    img = np.zeros((size, size, 4), dtype=np.float64)
    ys, xs = np.mgrid[0:size, 0:size]
    u = (xs + 0.5 - size / 2) / (size / 2)
    v = (ys + 0.5 - size / 2) / (size / 2)
    width = 0.72 * np.sqrt(np.clip(1 - ((v + 0.1) / 1.0) ** 2, 0, 1)) * (0.75 + 0.3 * v)
    d = np.abs(u) - width
    notch = (np.abs(u) < 0.16) & (v < -0.72 + np.abs(u) * 1.2)
    alpha = np.clip(0.5 - d * 12, 0, 1)
    alpha[notch] = 0
    alpha[np.abs(v + 0.1) > 1.0] = 0
    shade = 0.82 + 0.18 * np.clip(np.abs(u) / 0.6, 0, 1)
    img[..., 0] = 255
    img[..., 1] = 255 * shade
    img[..., 2] = 255 * shade
    img[..., 3] = alpha * 255
    Image.fromarray(img.astype(np.uint8), 'RGBA').save(os.path.join(OUT, 'petal.png'))
    with open(os.path.join(OUT, 'petal.png.mcmeta'), 'w') as f:
        json.dump({'texture': {'blur': True, 'clamp': True}}, f)


def glow():
    size = 64
    ys, xs = np.mgrid[0:size, 0:size]
    r = np.hypot(xs + 0.5 - size / 2, ys + 0.5 - size / 2) / (size / 2)
    a = np.clip(1 - r, 0, 1) ** 2.4
    img = np.zeros((size, size, 4), dtype=np.uint8)
    img[..., :3] = 255
    img[..., 3] = (a * 255).astype(np.uint8)
    Image.fromarray(img, 'RGBA').save(os.path.join(OUT, 'glow.png'))
    with open(os.path.join(OUT, 'glow.png.mcmeta'), 'w') as f:
        json.dump({'texture': {'blur': True, 'clamp': True}}, f)


def cat():
    # A little black cat head with a pale outline, so it reads on bright glass. 14 x 14.
    rows = [
        '..............',
        '.x..........x.',
        '.xx........xx.',
        '.xxx......xxx.',
        '.xxxxxxxxxxxx.',
        '.xxxxxxxxxxxx.',
        '.xxgxxxxxxgxx.',
        '.xxgxxxxxxgxx.',
        '.xxxxxxxxxxxx.',
        '.xxxxxpxxxxxx.',
        '..xxxxxxxxxx..',
        '...xxxxxxxx...',
        '..............',
        '..............',
    ]
    img = Image.new('RGBA', (14, 14), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == 'x':
                px[x, y] = (24, 18, 26, 255)
            elif c == 'g':
                px[x, y] = (255, 214, 232, 255)
            elif c == 'p':
                px[x, y] = (255, 150, 190, 255)
    # pale outline
    src = img.copy()
    sp = src.load()
    for y in range(14):
        for x in range(14):
            if sp[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    ax, ay = x + dx, y + dy
                    if 0 <= ax < 14 and 0 <= ay < 14 and sp[ax, ay][3] > 0:
                        px[x, y] = (255, 232, 242, 190)
                        break
    img.save(os.path.join(OUT, 'cat.png'))


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    clouds()
    petal()
    glow()
    cat()
    print('art written')
