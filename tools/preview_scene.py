#!/usr/bin/env python3
# A quick look at the scene without the game: rough perspective pictures from make_scene's blocks, to judge shapes.
#   python tools/preview_scene.py <output folder>
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.argv, real_argv = [sys.argv[0], os.path.join(sys.argv[1] if len(sys.argv) > 1 else '.', 'scene')], sys.argv
import make_scene as ms

COLOURS = {
    'air': None, 'grass_block': (116, 168, 72), 'dirt': (121, 85, 58), 'stone': (125, 125, 125), 'cherry_planks': (226, 178, 172), 'cherry_log': (54, 33, 44), 'cherry_wood': (54, 33, 44),
    'stripped_cherry_log': (215, 145, 148), 'stripped_cherry_wood': (215, 145, 148), 'cherry_stairs': (226, 178, 172), 'cherry_slab': (226, 178, 172), 'cherry_leaves': (240, 170, 195), 'stone_bricks': (122, 122, 122),
    'stone_brick_stairs': (122, 122, 122), 'mossy_stone_bricks': (110, 125, 95), 'cracked_stone_bricks': (112, 112, 112), 'polished_andesite': (132, 134, 133), 'andesite': (136, 136, 137), 'tuff': (108, 109, 102), 'cobblestone': (127, 127, 127),
    'moss_block': (89, 109, 45), 'coarse_dirt': (119, 85, 59), 'azalea_leaves': (90, 115, 44), 'flowering_azalea_leaves': (112, 121, 64), 'pink_petals': (235, 165, 200), 'grass': (100, 150, 60), 'fern': (90, 140, 55),
    'tall_grass': (100, 150, 60), 'ochre_froglight': (250, 245, 206), 'white_stained_glass_pane': (240, 240, 240), 'cherry_fence': (200, 150, 150), 'lantern': (240, 190, 90), 'chain': (60, 60, 70), 'pink_carpet': (240, 140, 170), 'magenta_carpet': (190, 70, 180),
}


def base_colour(name):
    key = name.split('|')[0].split('[')[0].split(':')[-1]
    c = COLOURS.get(key, (200, 90, 120))
    if c is None:
        return None
    c = np.array(c, dtype=float)
    if '|' in name:
        t = name.split('|')[1]
        c = c * np.array([int(t[i:i + 2], 16) / 255 for i in (0, 2, 4)])
    return c


def render(cam, look, fov, w=800, h=450, path='out.png', zmax=9999):
    # Every exposed block drawn as a square, far ones first, shaded by which way is up and open to the sun.
    grid = ms.S.grid
    solid = grid != 0
    ex = np.zeros_like(solid)
    ex[1:, :, :] |= ~solid[:-1, :, :]
    ex[:-1, :, :] |= ~solid[1:, :, :]
    ex[:, 1:, :] |= ~solid[:, :-1, :]
    ex[:, :-1, :] |= ~solid[:, 1:, :]
    ex[:, :, 1:] |= ~solid[:, :, :-1]
    ex[:, :, :-1] |= ~solid[:, :, 1:]
    ex &= solid
    xs, ys, zs = np.nonzero(ex)
    pos = np.stack([xs + ms.X0 + .5, ys + ms.Y0 + .5, zs + ms.Z0 + .5], axis=1)
    near_ok = pos[:, 2] < zmax
    pal = np.zeros((len(ms.S.palette), 3))
    for i, n in enumerate(ms.S.palette):
        c = base_colour(n)
        pal[i] = c if c is not None else 0
    col = pal[grid[xs, ys, zs]]
    up_open = ~solid[xs, np.minimum(ys + 1, ms.NY - 1), zs]
    south_open = ~solid[xs, ys, np.minimum(zs + 1, ms.NZ - 1)]
    west_open = ~solid[np.maximum(xs - 1, 0), ys, zs]
    light = .55 + .4 * up_open + .22 * south_open + .22 * west_open
    col = np.clip(col * light[:, None], 0, 255)
    cam, look = np.array(cam, float), np.array(look, float)
    fw = look - cam
    fw /= np.linalg.norm(fw)
    right = np.cross(fw, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fw)
    rel = pos - cam
    dz = rel @ fw
    keep = (dz > .5) & near_ok
    rel, dz, col = rel[keep], dz[keep], col[keep]
    f = (h / 2) / np.tan(np.radians(fov / 2))
    sx = (rel @ right) * f / dz + w / 2
    sy = h / 2 - (rel @ up) * f / dz
    size = np.maximum(1, (f * 1.05 / dz)).astype(int)
    order = np.argsort(-dz)
    img = np.zeros((h, w, 3), dtype=np.uint8)
    # sky
    t = np.linspace(0, 1, h)[:, None, None]
    img[:] = (np.array([88, 148, 222]) * (1 - t) + np.array([210, 228, 240]) * t).astype(np.uint8)
    for i in order:
        s = size[i]
        x0, y0 = int(sx[i] - s / 2), int(sy[i] - s / 2)
        x1, y1 = x0 + s + 1, y0 + s + 1
        if x1 < 0 or y1 < 0 or x0 >= w or y0 >= h:
            continue
        img[max(y0, 0):max(y1, 0), max(x0, 0):max(x1, 0)] = col[i]
    Image.fromarray(img).save(path)


if __name__ == '__main__':
    out = real_argv[1] if len(real_argv) > 1 else '.'
    os.makedirs(out, exist_ok=True)
    ms.make_land()
    ms.make_house()
    ms.make_yard()
    ms.make_big_tree()
    ms.make_other_trees()
    ms.make_plants()
    ms.join_up()
    render((-2.5, 5, 42), (-8, 9.5, -6), 56, w=1000, h=562, path=os.path.join(out, 'front.png'))
    render((2, 8.4, ms.TREE_Z + 27), (5, 15.2, ms.TREE_Z), 54, w=1000, h=562, path=os.path.join(out, 'tree.png'))
    render((6, 13, ms.TREE_Z + 62), (6, 15, ms.TREE_Z), 54, w=1000, h=562, path=os.path.join(out, 'tree_far.png'), zmax=-26)
    render((-60, 40, 60), (0, 6, -20), 50, w=1000, h=562, path=os.path.join(out, 'high.png'))
