#!/usr/bin/env python3
# Builds the menu's scene: the cherry wood house, the yard in front, the garden behind with the giant cherry tree,
# rocks, meadow, flowers and the far hills and mountains. Pure Python + numpy, no game needed.
#   python tools/make_scene.py          writes src/main/resources/assets/charmenu/scene/
#   python tools/make_scene.py <folder> writes to another folder (for trying things out while the game is open)
# Files written:
#   world.bin.gz  every block in the box: palette of block state strings, then one short per cell
#   far.bin.gz    the low detail land out to the horizon: flat coloured quads with the sun already baked in
#   meta.txt      where the doors, the sleeper and the camera paths are
import gzip
import math
import os
import random
import struct
import sys

import numpy as np

X0, X1 = -128, 128   # the box of real blocks
Y0, Y1 = -26, 70
Z0, Z1 = -176, 80
NX, NY, NZ = X1 - X0, Y1 - Y0, Z1 - Z0

SUN = np.array([-0.56, 0.66, 0.50])
SUN = SUN / np.linalg.norm(SUN)

# Where the tree sits. Everything about the garden hangs off this.
TREE_Z = -64

# Colours multiplied over the real block textures. Multiplying can only darken, so these make the richer shades.
ROOF = 'D9A0AC'       # dusty rose roof over the pale walls
ROOF2 = 'C98C9C'      # a touch deeper, for the edge rows
BEAM = None
LEAF_SHADES = ['F07F96', 'F58CA2', 'F99AAE', 'FCA9BA', 'FFB9C8', 'FFC9D4', 'FFD8E0']
HEDGE = 'A8C890'


def ss(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


# ------------------------------------------------------------------ noise
_tables = {}


def _table(seed):
    if seed not in _tables:
        _tables[seed] = np.random.default_rng(seed).random((256, 256)) * 2 - 1
    return _tables[seed]


def vnoise(x, z, seed=1):
    x = np.asarray(x, dtype=np.float64)
    z = np.asarray(z, dtype=np.float64)
    t = _table(seed)
    xi = np.floor(x).astype(np.int64)
    zi = np.floor(z).astype(np.int64)
    fx = x - xi
    fz = z - zi
    fx = fx * fx * (3 - 2 * fx)
    fz = fz * fz * (3 - 2 * fz)
    a = t[xi & 255, zi & 255]
    b = t[(xi + 1) & 255, zi & 255]
    c = t[xi & 255, (zi + 1) & 255]
    d = t[(xi + 1) & 255, (zi + 1) & 255]
    return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz


def fbm(x, z, octaves=3, seed=1):
    v = 0.0
    amp = 1.0
    tot = 0.0
    f = 1.0
    for i in range(octaves):
        v = v + amp * vnoise(x * f + i * 17.3, z * f - i * 9.1, seed + i)
        tot += amp
        amp *= 0.5
        f *= 2.03
    return v / tot


_lists = {}


def snoise(x, z, seed=1):
    # The same noise for one point at a time, much quicker than going through numpy.
    t = _lists.get(seed)
    if t is None:
        t = _table(seed).tolist()
        _lists[seed] = t
    xi = math.floor(x)
    zi = math.floor(z)
    fx = x - xi
    fz = z - zi
    fx = fx * fx * (3 - 2 * fx)
    fz = fz * fz * (3 - 2 * fz)
    xa, xb, za, zb = xi & 255, (xi + 1) & 255, zi & 255, (zi + 1) & 255
    top = t[xa][za] * (1 - fx) + t[xb][za] * fx
    bot = t[xa][zb] * (1 - fx) + t[xb][zb] * fx
    return top * (1 - fz) + bot * fz


def sfbm(x, z, octaves=3, seed=1):
    v = 0.0
    amp = 1.0
    tot = 0.0
    f = 1.0
    for i in range(octaves):
        v += amp * snoise(x * f + i * 17.3, z * f - i * 9.1, seed + i)
        tot += amp
        amp *= 0.5
        f *= 2.03
    return v / tot


# ------------------------------------------------------------------ the land
def land_height(x, z):
    # Height of the ground. The middle is gentle meadow, walled by soft hills left and right, falling away behind the tree so the view opens up.
    x = np.asarray(x, dtype=np.float64)
    z = np.asarray(z, dtype=np.float64)
    h = 2.6 * fbm(x / 46.0, z / 46.0, 3, 11) + 0.9 * fbm(x / 13.0, z / 13.0, 2, 12)
    side = ss(58.0, 170.0, np.abs(x))
    h = h + side * (13.0 + 11.0 * fbm(x / 70.0, z / 70.0, 3, 13))
    # the yard falls away a little towards the camera so the house sits up on a low rise
    front = ss(14.0, 90.0, z)
    h = h - front * 1.6
    # behind the garden the ground drops into a wide valley
    back = ss(112.0, 300.0, -z)
    h = h - back * 26.0
    # a low mound under the tree
    m = np.exp(-(((x - 34.0) / 26.0) ** 2 + ((z - TREE_Z) / 21.0) ** 2))
    h = h + 3.2 * m
    # flat pad for the house, its yard and the path
    dx = np.maximum(np.abs(x + 0.5) - 27.0, 0)
    dz = np.maximum(np.maximum(-30.0 - z, z - 16.0), 0)
    pad = 1 - ss(0.0, 20.0, np.sqrt(dx * dx + dz * dz))
    dx2 = np.maximum(np.abs(x + 0.5) - 5.0, 0)
    dz2 = np.maximum(np.maximum(14.0 - z, z - 62.0), 0)
    path = 1 - ss(0.0, 9.0, np.sqrt(dx2 * dx2 + dz2 * dz2))
    flat = np.maximum(pad, path)
    return h * (1 - flat)


# ------------------------------------------------------------------ the block box
class Scene:
    def __init__(self):
        self.grid = np.zeros((NX, NY, NZ), dtype=np.uint16)
        self.palette = ['minecraft:air']
        self.index = {'minecraft:air': 0}

    def pal(self, state, tint=None):
        if ':' not in state:
            state = 'minecraft:' + state
        key = state if tint is None else state + '|' + tint
        i = self.index.get(key)
        if i is None:
            i = len(self.palette)
            self.palette.append(key)
            self.index[key] = i
        return i

    def set(self, x, y, z, state, tint=None):
        if X0 <= x < X1 and Y0 <= y < Y1 and Z0 <= z < Z1:
            self.grid[x - X0, y - Y0, z - Z0] = self.pal(state, tint)

    def air(self, x, y, z):
        self.set(x, y, z, 'air')

    def name(self, x, y, z):
        if X0 <= x < X1 and Y0 <= y < Y1 and Z0 <= z < Z1:
            return self.palette[self.grid[x - X0, y - Y0, z - Z0]]
        return 'minecraft:air'

    def is_air(self, x, y, z):
        if X0 <= x < X1 and Y0 <= y < Y1 and Z0 <= z < Z1:
            return self.grid[x - X0, y - Y0, z - Z0] == 0
        return True

    def fill(self, x0, y0, z0, x1, y1, z1, state, tint=None):
        i = self.pal(state, tint)
        ax0, ax1 = sorted((x0, x1))
        ay0, ay1 = sorted((y0, y1))
        az0, az1 = sorted((z0, z1))
        self.grid[max(ax0, X0) - X0:min(ax1 + 1, X1) - X0, max(ay0, Y0) - Y0:min(ay1 + 1, Y1) - Y0, max(az0, Z0) - Z0:min(az1 + 1, Z1) - Z0] = i


S = Scene()
GROUND = np.zeros((NX, NZ), dtype=np.int32)  # y of the top ground block


def ground_y(x, z):
    if X0 <= x < X1 and Z0 <= z < Z1:
        return int(GROUND[x - X0, z - Z0])
    return int(round(float(land_height(np.array(x + 0.5), np.array(z + 0.5))))) - 1


def stair(mat, facing, half='bottom', shape='straight'):
    return f'{mat}_stairs[facing={facing},half={half},shape={shape}]'


def slab(mat, kind='bottom'):
    return f'{mat}_slab[type={kind}]'


def log(mat='stripped_cherry_log', axis='y'):
    return f'{mat}[axis={axis}]'


def make_land():
    xs = np.arange(X0, X1) + 0.5
    zs = np.arange(Z0, Z1) + 0.5
    gx, gz = np.meshgrid(xs, zs, indexing='ij')
    h = land_height(gx, gz)
    GROUND[:, :] = np.round(h).astype(np.int32) - 1
    moss = fbm(gx / 9.0, gz / 9.0, 2, 21)
    for i in range(NX):
        for k in range(NZ):
            top = int(GROUND[i, k])
            low = top
            for di, dk in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                ni, nk = i + di, k + dk
                if 0 <= ni < NX and 0 <= nk < NZ:
                    low = min(low, int(GROUND[ni, nk]))
                else:
                    low = min(low, top - 3)
            yy = top - Y0
            if yy < 0:
                continue
            S.grid[i, yy, k] = S.pal('grass_block')
            for y in range(max(low - 1, Y0), top):
                S.grid[i, y - Y0, k] = S.pal('dirt' if top - y < 3 else 'stone')


# ------------------------------------------------------------------ house parts
def posts_and_panels(x0, x1, z0, z1, y0, y1, every=4, panel='cherry_planks', post='stripped_cherry_log'):
    # A framed wall all round a rectangle: posts, panels, a sill along the bottom and a dark beam along the top.
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                on_post = x in (x0, x1) or (x - x0) % every == 0
                wall_cell(x, y, z, y0, y1, on_post, 'x', panel, post)
        for z in range(z0 + 1, z1):
            for x in (x0, x1):
                on_post = (z - z0) % every == 0
                wall_cell(x, y, z, y0, y1, on_post, 'z', panel, post)


def wall_cell(x, y, z, y0, y1, on_post, along, panel, post):
    if on_post:
        S.set(x, y, z, log(post, 'y'))
    elif y == y0:
        S.set(x, y, z, log('stripped_cherry_log', along))
    elif y == y1:
        S.set(x, y, z, log('cherry_log', along))
    else:
        S.set(x, y, z, panel)


def hip_profile():
    # (block kind, height) per ring from the eave inwards: shallow at the edge, steeper higher up, the curve of an old temple roof.
    return [('slab_b', 0), ('slab_t', 0), ('stair', 1), ('stair', 2), ('stair', 3), ('stair', 4), ('stair', 5), ('stair', 7), ('stair', 9), ('stair', 11), ('stair', 13), ('stair', 15)]


def ring_cells(x0, x1, z0, z1):
    # Cells round a rectangle with the way their slope faces, and how far along from the nearest corner they are.
    cells = []
    if x0 > x1 or z0 > z1:
        return cells
    for x in range(x0, x1 + 1):
        d = min(x - x0, x1 - x)
        cells.append((x, z0, 'south', d))
        if z1 != z0:
            cells.append((x, z1, 'north', d))
    for z in range(z0 + 1, z1):
        d = min(z - z0, z1 - z)
        cells.append((x0, z, 'east', d))
        if x1 != x0:
            cells.append((x1, z, 'west', d))
    return cells


def corner_shape(x, z, x0, x1, z0, z1):
    # The corner stair of a hip roof: facing and shape so its tall quarter points inwards.
    if z == z0 and x == x1:
        return 'south', 'outer_right'
    if z == z0 and x == x0:
        return 'south', 'outer_left'
    if z == z1 and x == x0:
        return 'north', 'outer_right'
    if z == z1 and x == x1:
        return 'north', 'outer_left'
    return None, None


def roof_hip(x0, x1, z0, z1, y, over, rings=None, mat='cherry', tint=ROOF, ridge_along=None):
    # A hip roof over a rectangle, starting 'over' blocks out from the walls, stopping after 'rings' rings (a skirt round a higher floor) or at the top.
    prof = hip_profile()
    rx0, rx1, rz0, rz1 = x0 - over, x1 + over, z0 - over, z1 + over
    k = 0
    last_h = -1
    while rx0 <= rx1 and rz0 <= rz1 and k < len(prof) and (rings is None or k < rings):
        kind, h = prof[k]
        t = ROOF2 if k == 0 else tint
        for x, z, face, d in ring_cells(rx0, rx1, rz0, rz1):
            lift = 0
            if k == 0:
                lift = {0: 3, 1: 2, 2: 1}.get(d, 0)
            elif k == 1:
                lift = {0: 2, 1: 1}.get(d, 0)
            elif k == 2 and d == 0:
                lift = 1
            if kind == 'stair':
                cf, cs = corner_shape(x, z, rx0, rx1, rz0, rz1)
                # any gap down to the ring below gets filled so the roof has no holes
                for yy in range(y + last_h + 1, y + h):
                    S.set(x, yy, z, f'{mat}_planks', t)
                if cf:
                    S.set(x, y + h, z, stair(mat, cf, 'bottom', cs), t)
                else:
                    S.set(x, y + h, z, stair(mat, face), t)
            else:
                half = (0 if kind == 'slab_b' else 1) + lift
                S.set(x, y + h + half // 2, z, slab(mat, 'bottom' if half % 2 == 0 else 'top'), t)
                if half // 2 > 0:
                    S.set(x, y + h, z, slab(mat, 'top'), t)
        last_h = h
        rx0 += 1
        rx1 -= 1
        rz0 += 1
        rz1 -= 1
        k += 1
    return rx0, rx1, rz0, rz1, y + last_h


def rafters(x0, x1, z0, z1, y, over, every=2):
    # Log ends sticking out under the eaves: they make the roof feel heavy and real.
    for x in range(x0, x1 + 1):
        if (x - x0) % every == 0:
            for z in range(z0 - over + 1, z0):
                S.set(x, y, z, log('stripped_cherry_log', 'z'))
            for z in range(z1 + 1, z1 + over):
                S.set(x, y, z, log('stripped_cherry_log', 'z'))
    for z in range(z0, z1 + 1):
        if (z - z0) % every == 0:
            for x in range(x0 - over + 1, x0):
                S.set(x, y, z, log('stripped_cherry_log', 'x'))
            for x in range(x1 + 1, x1 + over):
                S.set(x, y, z, log('stripped_cherry_log', 'x'))


def roof_gable(x0, x1, z0, z1, y, over, end_over, mat='cherry', tint=ROOF, front=True, back=True, wall='cherry_planks'):
    # A gable roof with its ridge running front to back. Walls below are closed off at both ends.
    rows = (x1 - x0 + 1 + 2 * over) // 2
    heights = [0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15]
    zs0, zs1 = z0 - end_over, z1 + end_over
    last = -1
    for r in range(rows):
        h = heights[r]
        lx, rx = x0 - over + r, x1 + over - r
        for z in range(zs0, zs1 + 1):
            end = z in (zs0, zs1)
            t = ROOF2 if end or r == 0 else tint
            for x, face in ((lx, 'east'), (rx, 'west')):
                if r == 0:
                    S.set(x, y, z, slab(mat, 'bottom'), t)
                elif r == 1:
                    S.set(x, y, z, slab(mat, 'top'), t)
                else:
                    S.set(x, y + h, z, stair(mat, face), t)
        last = h
    # ridge cap
    ridge = y + last + 1
    cx0, cx1 = x0 - over + rows - 1, x1 + over - rows + 1
    for z in range(zs0 - 1, zs1 + 2):
        S.set(cx0, ridge, z, slab(mat, 'bottom'), ROOF2)
        S.set(cx1, ridge, z, slab(mat, 'bottom'), ROOF2)
    # the ridge ends curl up
    for z in (zs0 - 1, zs1 + 1):
        S.set(cx0, ridge, z, stair(mat, 'south' if z == zs0 - 1 else 'north'), ROOF2)
        S.set(cx1, ridge, z, stair(mat, 'south' if z == zs0 - 1 else 'north'), ROOF2)
    # close the gable triangles
    for z, on in ((z0, back), (z1, front)):
        if not on:
            continue
        for r in range(rows):
            h = heights[r]
            for x in range(x0 - over + r + 1, x1 + over - r):
                if x0 <= x <= x1:
                    top = y + h
                    if S.is_air(x, top, z):
                        S.set(x, top, z, wall)
        for x in range(x0, x1 + 1):
            for yy in range(y, ridge):
                if S.is_air(x, yy, z):
                    # stays under the roof line only
                    r = min(x - (x0 - over), (x1 + over) - x)
                    if r < rows and yy < y + heights[r]:
                        S.set(x, yy, z, wall)
    return ridge


def window(x0, x1, y0, y1, z, axis='x', backing=True):
    # A lit paper window: glass in a wooden lattice with a warm glow behind it. It reads as a lamp-lit room without building the room.
    a0, a1 = (x0, x1)
    for a in range(a0, a1 + 1):
        for y in range(y0, y1 + 1):
            cx, cz = (a, z) if axis == 'x' else (z, a)
            mid = a0 < a < a1 and (a - a0) % 2 == 1
            if y == (y0 + y1) // 2 + 1 or mid:
                S.set(cx, y, cz, 'cherry_fence')
            else:
                S.set(cx, y, cz, 'white_stained_glass_pane')


def glow_behind(x0, x1, y0, y1, z):
    S.fill(x0, y0, z, x1, y1, z, 'ochre_froglight')


def flower_box(x0, x1, y, z, out=1):
    for x in range(x0, x1 + 1):
        S.set(x, y, z + out, stair('cherry', 'south' if out > 0 else 'north', 'top'))
        S.set(x, y + 1, z + out, 'azalea' if (x % 2 == 0) else 'flowering_azalea')


def stone_lantern(x, z):
    y = ground_y(x, z) + 1
    S.set(x, y, z, 'chiseled_stone_bricks')
    S.set(x, y + 1, z, 'stone_brick_wall')
    S.set(x, y + 2, z, slab('stone_brick', 'top'))
    S.set(x, y + 3, z, 'lantern')
    S.set(x, y + 4, z, slab('stone_brick', 'bottom'))


def hanging_lantern(x, y_top, z, links=1):
    for i in range(links):
        S.set(x, y_top - i, z, 'chain')
    S.set(x, y_top - links, z, 'lantern[hanging=true]')


DOORS = []


def double_door(z, y, group):
    # The doors are not in the block box, the menu draws them itself so they can swing. Each leaf turns about its outer edge.
    # (cell x, y, z, hinge, pivot x, pivot z, turning direction, group)
    plate = z + 1 - 1.5 / 16
    DOORS.append((-1, y, z, 'left', -1 + 1.5 / 16, plate, 1, group))
    DOORS.append((0, y, z, 'right', 1 - 1.5 / 16, plate, -1, group))
    for x in (-1, 0):
        for yy in (y, y + 1):
            S.air(x, yy, z)


# ------------------------------------------------------------------ the house
FLOOR = 2     # the plane people walk on
HOUSE = dict(x0=-12, x1=11, z0=-22, z1=0)
LEFT_WALL, RIGHT_WALL = -6, 5   # the hall's side walls
HALL_TOP = 9


def make_house():
    x0, x1, z0, z1 = HOUSE['x0'], HOUSE['x1'], HOUSE['z0'], HOUSE['z1']
    # stone footing, a little wider than the walls
    S.fill(x0 - 1, -3, z0 - 1, x1 + 1, 0, z1 + 1, 'stone_bricks')
    for x in range(x0 - 1, x1 + 2):
        S.set(x, 0, z1 + 1, stair('stone_brick', 'north'))
        S.set(x, 0, z0 - 1, stair('stone_brick', 'south'))
    for z in range(z0 - 1, z1 + 2):
        S.set(x0 - 1, 0, z, stair('stone_brick', 'east'))
        S.set(x1 + 1, 0, z, stair('stone_brick', 'west'))
    S.fill(x0, 1, z0, x1, 1, z1, 'cherry_planks')

    # ground floor walls
    posts_and_panels(x0, x1, z0, z1, FLOOR, 8)
    # upper floor, set back
    ux0, ux1, uz0, uz1 = x0 + 3, x1 - 3, z0 + 3, z1 - 3
    S.fill(x0 + 1, 8, z0 + 1, x1 - 1, 9, z1 - 1, 'cherry_planks')
    posts_and_panels(ux0, ux1, uz0, uz1, 10, 15)

    # the skirt roof round the ground floor
    roof_hip(x0, x1, z0, z1, 9, 4, rings=7)
    rafters(x0, x1, z0, z1, 8, 4)

    # upper gable roof with its face to the front
    roof_gable(ux0, ux1, uz0, uz1, 16, 4, 3)
    rafters(ux0, ux1, uz0, uz1, 15, 3, every=3)

    make_front()
    make_back()
    make_wings()
    make_porch()
    make_hall()


def make_front():
    x0, x1, z1 = HOUSE['x0'], HOUSE['x1'], HOUSE['z1']
    # door surround
    for x in (-2, 1):
        for y in range(FLOOR, 8):
            S.set(x, y, z1, log('stripped_cherry_log', 'y'))
    S.fill(-1, FLOOR + 2, z1, 0, FLOOR + 2, z1, log('cherry_log', 'x'))
    S.fill(-1, FLOOR + 3, z1, 0, FLOOR + 4, z1, 'white_stained_glass_pane')
    S.fill(-1, FLOOR + 5, z1, 0, FLOOR + 5, z1, 'cherry_planks')
    double_door(z1, FLOOR, 'front')
    # big windows either side of the door
    for side in (-1, 1):
        a0, a1 = (-10, -5) if side < 0 else (4, 9)
        window(a0, a1, FLOOR + 1, FLOOR + 4, z1)
        glow_behind(a0, a1, FLOOR + 1, FLOOR + 4, z1 - 2)
        flower_box(a0, a1, FLOOR, z1)
    # shutters of the upper floor: two windows with a balcony rail
    uz1 = z1 - 3
    for a0, a1 in ((-8, -5), (4, 7)):
        window(a0, a1, 11, 13, uz1)
        glow_behind(a0, a1, 11, 13, uz1 - 2)
    # the big gable: dark timber lines and a lit window up above the porch roof, so it isn't one flat wall
    def plank(x, y, z):
        return 'cherry_planks' in S.name(x, y, z)
    for x in range(-14, 14):
        for y in (19, 24):
            if plank(x, y, uz1):
                S.set(x, y, uz1, log('cherry_log', 'x'))
    for x in (-6, -3, 2, 5):
        for y in range(16, 25):
            if plank(x, y, uz1):
                S.set(x, y, uz1, log('stripped_cherry_log', 'y'))
    window(-2, 1, 20, 23, uz1)
    glow_behind(-2, 1, 20, 23, uz1 - 1)
    # a lantern hanging from the peak
    hanging_lantern(-1, 26, uz1 + 1, links=1)
    hanging_lantern(0, 26, uz1 + 1, links=1)


def make_back():
    x0, x1, z0 = HOUSE['x0'], HOUSE['x1'], HOUSE['z0']
    for x in (-2, 1):
        for y in range(FLOOR, 8):
            S.set(x, y, z0, log('stripped_cherry_log', 'y'))
    S.fill(-1, FLOOR + 2, z0, 0, FLOOR + 2, z0, log('cherry_log', 'x'))
    S.fill(-1, FLOOR + 3, z0, 0, FLOOR + 4, z0, 'white_stained_glass_pane')
    double_door(z0, FLOOR, 'back')
    for a0, a1 in ((-10, -5), (4, 9)):
        window(a0, a1, FLOOR + 1, FLOOR + 4, z0)
    uz0 = z0 + 3
    for a0, a1 in ((-8, -5), (4, 7)):
        window(a0, a1, 11, 13, uz0)
    # back steps
    S.fill(-5, -2, z0 - 5, 4, 1, z0 - 1, 'cherry_planks')
    for x in range(-5, 5):
        for z in range(z0 - 5, z0 - 1):
            if x in (-5, 4) and z == z0 - 5:
                continue
        S.set(x, 0, z0 - 6, stair('cherry', 'south'))
        S.set(x, -1, z0 - 7, stair('cherry', 'south'))
    for z in range(z0 - 5, z0):
        for x in (-5, 4):
            if z != z0 - 5:
                S.set(x, FLOOR, z, 'cherry_fence')
    for x in (-5, 4):
        for y in range(FLOOR, 7):
            S.set(x, y, z0 - 5, log('stripped_cherry_log', 'y'))
    S.fill(-5, 7, z0 - 5, 4, 7, z0 - 5, log('cherry_log', 'x'))
    S.fill(-5, 7, z0 - 4, -5, 7, z0 - 1, log('cherry_log', 'z'))
    S.fill(4, 7, z0 - 4, 4, 7, z0 - 1, log('cherry_log', 'z'))
    roof_gable(-5, 4, z0 - 5, z0 - 1, 8, 2, 1, front=False, back=True)
    hanging_lantern(-1, 7, z0 - 3)
    hanging_lantern(0, 7, z0 - 3)


def make_wings():
    # Lower wings either side make the front wide and layered.
    for wx0, wx1 in ((-22, -13), (12, 21)):
        wz0, wz1 = -18, -5
        S.fill(wx0 - 1, -3, wz0 - 1, wx1 + 1, 0, wz1 + 1, 'stone_bricks')
        S.fill(wx0, 1, wz0, wx1, 1, wz1, 'cherry_planks')
        posts_and_panels(wx0, wx1, wz0, wz1, FLOOR, 6)
        roof_hip(wx0, wx1, wz0, wz1, 7, 3)
        rafters(wx0, wx1, wz0, wz1, 6, 3)
        a0, a1 = wx0 + 2, wx1 - 2
        window(a0, a1, FLOOR + 1, FLOOR + 3, wz1)
        glow_behind(a0, a1, FLOOR + 1, FLOOR + 3, wz1 - 2)
        flower_box(a0, a1, FLOOR, wz1)
        # the outer end wall gets a window too
        ex = wx0 if wx0 < 0 else wx1
        window(wz0 + 3, wz1 - 3, FLOOR + 1, FLOOR + 3, ex, axis='z')
        S.fill(ex + (2 if wx0 < 0 else -2), FLOOR + 1, wz0 + 3, ex + (2 if wx0 < 0 else -2), FLOOR + 3, wz1 - 3, 'ochre_froglight')
        hanging_lantern(wx0 - 2 if wx0 < 0 else wx1 + 2, 5, wz1 + 2)


def make_porch():
    z1 = HOUSE['z1']
    px0, px1, pz0, pz1 = -6, 5, z1 + 1, z1 + 7
    S.fill(px0, -3, pz0, px1, 1, pz1, 'cherry_planks')
    # edging logs round the deck
    for x in range(px0, px1 + 1):
        S.set(x, 1, pz1, log('stripped_cherry_log', 'x'))
    for z in range(pz0, pz1 + 1):
        S.set(px0, 1, z, log('stripped_cherry_log', 'z'))
        S.set(px1, 1, z, log('stripped_cherry_log', 'z'))
    # wide steps down to the path
    for x in range(px0 + 1, px1):
        S.set(x, 1, pz1, 'cherry_planks')
        S.set(x, 0, pz1 + 1, stair('cherry', 'north'))
        S.set(x, -1, pz1 + 2, stair('cherry', 'north'))
    # pillars and beams
    for x in (px0, px1):
        for z in (pz1, pz0):
            for y in range(FLOOR, 8):
                S.set(x, y, z, log('stripped_cherry_log', 'y'))
    S.fill(px0, 8, pz1, px1, 8, pz1, log('cherry_log', 'x'))
    S.fill(px0, 8, pz0, px0, 8, pz1, log('cherry_log', 'z'))
    S.fill(px1, 8, pz0, px1, 8, pz1, log('cherry_log', 'z'))
    # corner brackets under the beam
    for x, f in ((px0 + 1, 'east'), (px1 - 1, 'west')):
        S.set(x, 7, pz1, stair('cherry', f, 'top'))
    # side rails
    for z in range(pz0 + 1, pz1):
        for x in (px0, px1):
            S.set(x, FLOOR, z, 'cherry_fence')
    for x in range(px0 + 1, px0 + 3):
        S.set(x, FLOOR, pz1, 'cherry_fence')
    for x in range(px1 - 2, px1):
        S.set(x, FLOOR, pz1, 'cherry_fence')
    # the porch has its own little gable reaching out from the skirt roof
    roof_gable(px0, px1, pz0 - 1, pz1, 9, 3, 1, front=True, back=False)
    # front of that gable: a sun-ray pattern of logs
    gz = pz1
    for x in range(px0 + 1, px1):
        S.set(x, 9, gz, log('stripped_cherry_log', 'x'))
    hanging_lantern(-1, 8, pz1 - 1)
    hanging_lantern(0, 8, pz1 - 1)
    hanging_lantern(-1, 8, pz0 + 1)
    hanging_lantern(0, 8, pz0 + 1)
    # plants in pots at the door
    for x, z in ((-4, pz0), (3, pz0), (-5, pz1 - 1), (4, pz1 - 1)):
        S.set(x, FLOOR, z, 'potted_cherry_sapling')
    S.fill(px0 + 1, 8, pz0, px1 - 1, 8, pz1 - 1, 'cherry_planks')


PAINTINGS = []


def make_hall():
    # Inside: long and warm. Arches slide past as you fly through, lanterns down the middle, and each bay between the arches is furnished:
    # glowing paper screens, shelves of books and plants, real paintings in dark frames, a patterned rug down the floor.
    z0, z1 = HOUSE['z0'], HOUSE['z1']
    x0, x1 = LEFT_WALL, RIGHT_WALL
    S.fill(x0 + 1, FLOOR, z0 + 1, x1 - 1, HALL_TOP - 1, z1 - 1, 'air')
    # side walls
    for z in range(z0 + 1, z1):
        for y in range(FLOOR, HALL_TOP):
            for x in (x0, x1):
                on = (z - z1) % 5 == 0
                if on:
                    S.set(x, y, z, log('stripped_cherry_log', 'y'))
                elif y == FLOOR:
                    S.set(x, y, z, log('cherry_log', 'z'))
                elif y == HALL_TOP - 1:
                    S.set(x, y, z, log('stripped_cherry_log', 'z'))
                else:
                    S.set(x, y, z, 'cherry_planks')
    # ceiling and floor: boards with a darker border
    S.fill(x0 + 1, HALL_TOP, z0 + 1, x1 - 1, HALL_TOP, z1 - 1, 'cherry_planks')
    for z in range(z0 + 1, z1):
        S.set(-1, HALL_TOP, z, log('cherry_log', 'z'))
        S.set(0, HALL_TOP, z, log('cherry_log', 'z'))
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            S.set(x, 1, z, 'cherry_planks' if (x in (-1, 0) or x in (-4, 3)) else 'stripped_cherry_wood')
    # the rug: a pink border, deep magenta centre, a pale medallion in each bay and little stars between
    for z in range(z0 + 1, z1):
        bay = (z - z1) % 5
        for x in range(-3, 3):
            edge = x in (-3, 2)
            centre = x in (-1, 0)
            if edge:
                c = 'white_carpet' if bay == 0 else 'pink_carpet'
            elif centre and bay in (2, 3):
                c = 'white_carpet'
            elif centre and bay in (1, 4):
                c = 'pink_carpet'
            elif bay in (2, 3):
                c = 'pink_carpet'
            else:
                c = 'magenta_carpet'
            S.set(x, FLOOR, z, c)
    # arches every five blocks: stone feet, a dark band near the top, and brackets
    for z in range(z1 - 5, z0, -5):
        for x in range(x0 + 1, x1):
            S.set(x, HALL_TOP - 1, z, log('cherry_log', 'x'))
        for x, f in ((x0 + 1, 'east'), (x1 - 1, 'west')):
            S.set(x, FLOOR, z, 'chiseled_stone_bricks')
            for y in range(FLOOR + 1, HALL_TOP - 2):
                S.set(x, y, z, log('stripped_cherry_log', 'y'))
            S.set(x, HALL_TOP - 3, z, log('cherry_log', 'y'))
            S.set(x, HALL_TOP - 2, z, stair('cherry', f, 'top'))
            S.set(x + (1 if f == 'east' else -1), HALL_TOP - 2, z, stair('cherry', f, 'top'))
    # lanterns down the middle, spore blossoms hanging between them, and a small lantern at each side of the arches
    for z in range(z1 - 3, z0, -5):
        for x in (-1, 0):
            hanging_lantern(x, HALL_TOP - 1, z, links=2)
        S.set(-3, HALL_TOP - 1, z, 'spore_blossom')
        S.set(2, HALL_TOP - 1, z, 'spore_blossom')
    for z in range(z1 - 5, z0, -5):
        hanging_lantern(x0 + 3, HALL_TOP - 2, z + 1, links=1)
        hanging_lantern(x1 - 3, HALL_TOP - 2, z + 1, links=1)

    # the bays: (first z, last z) of each, and what's on each wall
    bays = [(z1 - 4, z1 - 1), (z1 - 9, z1 - 6), (z1 - 14, z1 - 11), (z1 - 19, z1 - 16)]
    left = ['screen', 'painting', 'shelves', 'screen']
    right = ['screen', 'shelves', 'painting', 'screen']
    arts = {(-1, 1): ('minecraft:bust', 2, 2), (1, 2): ('minecraft:sunset', 2, 1)}
    for i, (za, zb) in enumerate(bays):
        for side, kinds in ((-1, left), (1, right)):
            kind = kinds[i]
            wall = x0 if side < 0 else x1
            inner = wall + (1 if side < 0 else -1)
            if kind == 'screen':
                window(za, zb, FLOOR + 1, HALL_TOP - 3, wall, axis='z')
                behind = wall + (-1 if side < 0 else 1)
                S.fill(behind, FLOOR + 1, za, behind, HALL_TOP - 3, zb, 'ochre_froglight')
                # low planters along the foot of the screen
                for z in range(za, zb + 1):
                    S.set(inner, FLOOR, z, stair('cherry', 'east' if side < 0 else 'west', 'top') if z not in (za, zb) else 'potted_flowering_azalea_bush')
                    if z not in (za, zb):
                        S.set(inner, FLOOR + 1, z, 'flowering_azalea' if z % 2 == 0 else 'azalea')
            elif kind == 'shelves':
                for z in range(za, zb + 1):
                    for y in range(FLOOR, FLOOR + 4):
                        S.set(inner, y, z, 'bookshelf')
                    S.set(inner, FLOOR + 4, z, slab('cherry', 'bottom'))
                S.set(inner, FLOOR + 4, za, 'cherry_planks')
                S.set(inner, FLOOR + 4, zb, 'cherry_planks')
                S.set(inner, FLOOR + 5, za, 'potted_azure_bluet')
                S.set(inner, FLOOR + 5, zb, 'potted_pink_tulip')
                S.set(inner, FLOOR + 4, za + 1, 'pink_candle[candles=3,lit=true]')
                S.set(inner, FLOOR + 4, za + 2, 'white_candle[candles=2,lit=true]')
            elif kind == 'painting':
                # a dark frame in the wall itself, and a bench under it
                for z in range(za, zb + 1):
                    for y in range(FLOOR + 2, FLOOR + 6):
                        S.set(wall, y, z, log('cherry_log', 'y') if z in (za, zb) or y in (FLOOR + 2, FLOOR + 5) else 'stripped_cherry_wood')
                plane = (wall + 1.02) if side < 0 else (wall - 0.02)
                name, pw, ph = arts[(side, i)]
                PAINTINGS.append((name, plane, FLOOR + 3 + (2 - ph) / 2, za + 1 + (2 - pw) / 2, pw, ph, 1 if side < 0 else -1))
                for z in range(za, zb + 1):
                    S.set(inner, FLOOR, z, stair('cherry', 'west' if side < 0 else 'east'))
                S.set(inner, FLOOR, za, 'potted_azalea_bush')
                S.set(inner, FLOOR, zb, 'potted_azalea_bush')
    # the two end walls inside, around the door frames (a clean look when looking down the hall)
    for z in (z1, z0):
        for x in range(x0 + 1, x1):
            if x in (-1, 0):
                continue
            for y in range(FLOOR, HALL_TOP):
                if x in (-2, 1):
                    S.set(x, y, z, log('stripped_cherry_log', 'y'))
                elif y >= FLOOR + 6:
                    S.set(x, y, z, 'cherry_planks')
    for z in (z1, z0):
        for x in (-1, 0):
            for y in range(FLOOR + 6, HALL_TOP):
                S.set(x, y, z, 'cherry_planks')


# ------------------------------------------------------------------ yard
def make_yard():
    z_porch = HOUSE['z1'] + 9
    # the stone path to the steps
    rnd = random.Random(7)
    for z in range(z_porch, 74):
        wob = 0.7 * math.sin(z * 0.13)
        for x in range(-4, 4):
            xw = x + 0.5 + wob * 0.0
            y = ground_y(x, z)
            edge = x in (-4, 3)
            r = rnd.random()
            if edge and r < 0.45:
                S.set(x, y, z, 'mossy_stone_bricks')
            elif r < 0.55:
                S.set(x, y, z, 'stone_bricks')
            elif r < 0.8:
                S.set(x, y, z, 'cracked_stone_bricks')
            else:
                S.set(x, y, z, 'polished_andesite')
    # gravel and moss on the path's edges
    for z in range(z_porch, 74):
        for x in (-5, 4):
            y = ground_y(x, z)
            r = rnd.random()
            S.set(x, y, z, 'moss_block' if r < 0.5 else 'coarse_dirt' if r < 0.7 else 'grass_block')
    # hedges along the path, with gaps
    for z in range(z_porch + 2, 34):
        for x in (-8, 7):
            if (z - z_porch) % 12 >= 10:
                continue
            y = ground_y(x, z)
            for dx in (0, -1 if x < 0 else 1):
                S.set(x + dx, y + 1, z, 'azalea_leaves[persistent=true]' if rnd.random() < .7 else 'flowering_azalea_leaves[persistent=true]')
    # stone lanterns in pairs
    for z in (z_porch + 5, z_porch + 17):
        stone_lantern(-6, z)
        stone_lantern(5, z)
    # hedges tight to the house
    for x in list(range(-24, -6)) + list(range(6, 24)):
        for z in (HOUSE['z1'] + 4,):
            if -12 <= x <= 11 or abs(x) > 12:
                y = ground_y(x, z)
                if S.is_air(x, y + 1, z):
                    S.set(x, y + 1, z, 'azalea_leaves[persistent=true]' if rnd.random() < .7 else 'flowering_azalea_leaves[persistent=true]')


# ------------------------------------------------------------------ trees
def catmull(points, step=0.2):
    # Sample a smooth line through (x, y, z, r) points.
    out = []
    pts = [points[0]] + list(points) + [points[-1]]
    for i in range(1, len(pts) - 2):
        p0, p1, p2, p3 = pts[i - 1], pts[i], pts[i + 1], pts[i + 2]
        d = math.dist(p1[:3], p2[:3])
        n = max(2, int(d / step))
        for j in range(n):
            t = j / n
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * ((2 * p1[a]) + (-p0[a] + p2[a]) * t + (2 * p0[a] - 5 * p1[a] + 4 * p2[a] - p3[a]) * t2 + (-p0[a] + 3 * p1[a] - 3 * p2[a] + p3[a]) * t3) for a in range(4)))
    out.append(tuple(points[-1]))
    return out


def limb(points, cells, knots=0.0, seed=3):
    # Stamps a tapered, slightly gnarly round branch into a set of cells.
    rnd = random.Random(seed)
    phase = rnd.random() * 100
    for idx, (x, y, z, r) in enumerate(catmull(points)):
        rr = max(r, 0.45) * (1 + knots * math.sin(idx * 0.13 + phase) * 0.5 + knots * 0.5 * math.sin(idx * 0.031 + phase * 2))
        ri = int(math.ceil(rr)) + 1
        for ax in range(int(x) - ri, int(x) + ri + 1):
            for ay in range(int(y) - ri, int(y) + ri + 1):
                for az in range(int(z) - ri, int(z) + ri + 1):
                    dx, dy, dz = ax + 0.5 - x, ay + 0.5 - y, az + 0.5 - z
                    if dx * dx + dy * dy + dz * dz <= rr * rr:
                        cells.add((ax, ay, az))


def blossom(cx, cy, cz, rx, ry, rz, seed, shades=LEAF_SHADES, bumps=0.55, keep_out=None):
    # A cloud of cherry leaves: lumpy flat-bottomed cushion, deeper rose underneath, pale on top where the sun is.
    rnd = random.Random(seed)
    lumps = [(cx + rnd.uniform(-rx, rx) * .85, cy + rnd.uniform(-ry, ry) * .75, cz + rnd.uniform(-rz, rz) * .85, rnd.uniform(.3, .62)) for _ in range(12)]
    for x in range(int(cx - rx - 3), int(cx + rx + 4)):
        for y in range(int(cy - ry - 3), int(cy + ry + 4)):
            for z in range(int(cz - rz - 3), int(cz + rz + 4)):
                dx, dy, dz = (x + .5 - cx) / rx, (y + .5 - cy) / ry, (z + .5 - cz) / rz
                d = dx * dx + dy * dy * (1.5 if dy < 0 else 1.0) + dz * dz
                n = sfbm((x + y * .5) / 4.3, (z - y * .7) / 4.3, 2, seed % 50 + 30)
                d += n * bumps
                for lx, ly, lz, lr in lumps:
                    ldx, ldy, ldz = (x + .5 - lx) / (rx * lr), (y + .5 - ly) / (ry * lr), (z + .5 - lz) / (rz * lr)
                    d = min(d, ldx * ldx + ldy * ldy + ldz * ldz + n * bumps)
                if d > 1.0:
                    continue
                if keep_out and keep_out(x, y, z):
                    continue
                if rnd.random() < 0.02:
                    continue
                if S.is_air(x, y, z):
                    shade = (dy + 1) * .5 + rnd.uniform(-.17, .17)
                    i = max(0, min(len(shades) - 1, int(shade * len(shades))))
                    S.set(x, y, z, 'cherry_leaves[persistent=true]', shades[i])


def wood_from_cells(cells, seed=9):
    rnd = random.Random(seed)
    pale = 'stripped_cherry_wood[axis=y]'
    bark = 'cherry_wood[axis=y]'
    for (x, y, z) in cells:
        n = sfbm(x / 5.5 + y * .15, (y * 1.1 + z * .9) / 2.3, 2, seed) + rnd.uniform(-.14, .14)
        if n < -.3:
            S.set(x, y, z, bark)
        elif n < .1:
            S.set(x, y, z, pale, 'E0C9CC')
        else:
            S.set(x, y, z, pale, 'F4E2E2')


SLEEPER = dict(x=6.3, y=0.0, z=TREE_Z + 2.5, yaw=0.0)


def make_big_tree():
    # Shaped after the picture: a thick trunk rising from rocks on the right, arching over, then one long limb reaching left, dipping level in the middle
    # (where the sleeper lies) and lifting again at its tip. Blossom sits in separate cushions above it with sky showing between them, and a few low ones hang below.
    z = TREE_Z
    k = .88  # squeezes the whole tree sideways a little so it fits the view with the sleeper still big enough

    def X(pts):
        return [(p[0] * k, p[1], p[2], p[3]) for p in pts]

    trunk = [
        (37, -5, z, 8.0), (39, 3, z, 6.5), (39, 9, z + .5, 5.2), (36, 15, z + .5, 4.3), (30, 19.5, z + 1, 3.6),
        (22.5, 20.8, z + 1.5, 3.1), (15.5, 18.2, z + 2, 2.65), (9, 15.2, z + 2.5, 2.35), (3, 13.8, z + 2.5, 2.1), (-4, 13.4, z + 2.5, 1.8),
        (-11, 14, z + 2, 1.45), (-18, 15.6, z + 1.5, 1.1), (-25, 18.3, z + 1, .8), (-32, 21.8, z + .5, .55),
    ]
    cells = set()
    limb(X(trunk), cells, knots=.17, seed=2)
    # roots flare out across the rocks
    limb(X([(34, 1, z - 1, 3.6), (26, -1.5, z + 2, 2.3), (19, -3, z + 3, 1.2)]), cells, knots=.2, seed=3)
    limb(X([(42, 2, z + 3, 3.6), (46, -1, z + 9, 2.3), (50, -3, z + 13, 1.1)]), cells, knots=.2, seed=4)
    limb(X([(41, 1, z - 5, 3.4), (46, -2, z - 10, 2.1), (51, -4, z - 13, 1.0)]), cells, knots=.2, seed=5)
    limb(X([(34, 1, z + 5, 3.2), (29, -2, z + 10, 2.0), (25, -4, z + 13, 1.0)]), cells, knots=.2, seed=6)
    # up and right from the shoulder of the trunk
    limb(X([(37, 14, z, 2.9), (41, 20, z - 1, 2.1), (44.5, 25, z - 2, 1.3), (48, 28.5, z - 2.5, .8), (52, 30, z - 2.5, .5)]), cells, knots=.15, seed=7)
    # up from the top of the arch
    limb(X([(27, 20, z + 1, 2.0), (24, 25, z, 1.45), (20.5, 29.5, z - 1.5, .95), (17, 32.5, z - 3, .55)]), cells, knots=.15, seed=8)
    # rising off the long limb
    limb(X([(12, 16.6, z + 2.2, 1.25), (10, 21, z + 1.5, .9), (6.5, 25.5, z, .65), (3, 28.5, z - 1, .45)]), cells, knots=.12, seed=9)
    limb(X([(-3, 13.8, z + 2.4, 1.05), (-6, 18, z + 2, .8), (-9.5, 22.5, z + 1, .55), (-13, 25.5, z, .45)]), cells, knots=.12, seed=10)
    limb(X([(-17, 15.6, z + 1.5, .8), (-20, 19, z + 1, .6), (-23, 23, z + .5, .45)]), cells, seed=11)
    # towards and away from the camera, for depth
    limb(X([(18, 19.4, z + 1.8, 1.1), (16, 22.5, z + 7, .75), (13, 24.5, z + 10.5, .45)]), cells, seed=12)
    limb(X([(7, 15, z + 2.4, .9), (7, 20, z - 4, .65), (4.5, 24, z - 8.5, .45)]), cells, seed=13)
    limb(X([(32, 18, z + .5, 1.3), (31, 23, z - 4, .9), (28, 26, z - 8, .5)]), cells, seed=14)
    # twigs hanging below the limb, carrying the low blossom
    limb(X([(1, 13, z + 2.4, .75), (-1.5, 10.5, z + 3, .55), (-3, 8.8, z + 3, .45)]), cells, seed=15)
    limb(X([(-13, 14.2, z + 1.8, .65), (-15, 12, z + 2.4, .5), (-16, 10.5, z + 2.6, .45)]), cells, seed=16)
    limb(X([(15, 17, z + 2, .75), (17.5, 13.5, z + 2.6, .55), (18, 11.5, z + 3, .45)]), cells, seed=17)

    # twigs carrying blossom just behind the limb, right behind where the sleeper lies
    limb(X([(-8, 14, z + 2, .6), (-8, 17.5, z - 2, .5), (-8, 18.5, z - 5, .45)]), cells, seed=18)
    limb(X([(1, 14, z + 2, .6), (1.5, 18, z - 2.5, .5), (1.5, 19.5, z - 5.5, .45)]), cells, seed=19)
    limb(X([(11, 16, z + 2, .6), (11, 19, z - 2, .5), (11, 20.5, z - 5, .45)]), cells, seed=20)
    limb(X([(-6, 14.4, z + 3, .55), (-7, 17.5, z + 5, .45), (-7.5, 20, z + 6.5, .45)]), cells, seed=22)

    # a good flat bed on top of the limb where the sleeper lies
    bed_y = 16
    bed0, bed1 = 1, 9
    for x in range(bed0 - 2, bed1 + 3):
        for dz in range(int(z) - 2, int(z) + 8):
            for y in range(bed_y, bed_y + 4):
                cells.discard((x, y, dz))
    for x in range(bed0 - 1, bed1 + 1):
        for dz in range(int(z) + 1, int(z) + 4):
            cells.add((x, bed_y - 1, dz))
            cells.add((x, bed_y - 2, dz))
    SLEEPER['x'] = 5.2
    SLEEPER['y'] = float(bed_y)
    SLEEPER['z'] = z + 3.55
    wood_from_cells(cells)
    rnd = random.Random(21)
    for x in range(bed0, bed1 + 1):
        for dz in (int(z) + 1,):
            if rnd.random() < .4:
                S.set(x, bed_y, dz, f'pink_petals[flower_amount={rnd.randint(1, 2)},facing={rnd.choice(["north", "south", "east", "west"])}]')

    keep = lambda x, y, zz: (-3 <= x <= 13 and 15 <= y <= 24 and z - 3 <= zz <= z + 12)  # clear air round the sleeper
    clouds = [
        (18, 33.5, z - 3, 10, 4.2, 8), (3, 29.8, z - 1, 8.5, 3.6, 6.5), (-13, 26.2, z, 7.5, 3.4, 6), (-33, 22, z + .5, 8.5, 4.8, 6.5),
        (46, 31, z - 2.5, 9, 4.6, 7), (56, 26.5, z - 2, 5, 4.4, 4.5), (31, 27, z - 8.5, 6.5, 3.4, 5), (13, 25.4, z + 11, 5, 2.6, 4),
        (4.5, 25.2, z - 9.5, 6, 3, 4.5), (-23, 24.4, z + 1, 5, 2.6, 4.2),
        # low ones under the limb
        (-3, 8.6, z + 3, 4.2, 2.4, 3.2), (-16, 10.8, z + 3, 4.6, 2.6, 3.6), (18, 10.6, z + 3.2, 3.6, 2.2, 3),
        # right behind the sleeper, and one nearer the camera in the top corner of the view
        (-8, 19.4, z - 6, 5, 2.6, 3.5), (1.5, 20.4, z - 7, 5.5, 2.8, 4), (11, 21.2, z - 6, 4.5, 2.6, 3.5), (-8, 21.2, z + 7, 3.6, 2, 3),
    ]
    for i, (cx, cy, cz, rx, ry, rz) in enumerate(clouds):
        blossom(cx * k, cy, cz, rx * k, ry, rz, 100 + i, keep_out=keep)

    # the rocks the trunk grows out of
    boulder(40 * k, -2, z + 6, 11, 8.5, 9, 41)
    boulder(29 * k, -3, z + 8, 7, 4.5, 5.5, 42)
    boulder(48 * k, -1, z - 5, 9, 8, 9, 43)
    boulder(37 * k, -2, z - 10, 8, 6, 6, 44)
    boulder(20 * k, -3, z + 3, 3.6, 2.2, 3.6, 45)
    # lone rocks around the meadow, like the big one on the left of the picture
    boulder(-41, 0, z + 2, 7, 5, 5.5, 46)
    boulder(-48, 0, z + 5, 3.5, 2.4, 3, 47)
    boulder(-30, -1, z + 20, 2.6, 1.8, 2.2, 48)
    boulder(-14, -1, z - 18, 3, 2, 2.6, 49)
    boulder(14, -1, z + 20, 2, 1.3, 1.8, 50)
    boulder(52, 0, z + 18, 4, 3, 3.5, 51)
    boulder(-26, -2, -6, 3, 2, 2.6, 52)
    boulder(32, -2, 22, 3.2, 2.2, 2.8, 53)


def boulder(cx, cy, cz, rx, ry, rz, seed):
    rnd = random.Random(seed)
    lumps = [(cx + rnd.uniform(-rx, rx) * .55, cy + rnd.uniform(-ry, ry) * .4, cz + rnd.uniform(-rz, rz) * .55, rnd.uniform(.35, .75)) for _ in range(6)]
    for x in range(int(cx - rx - 2), int(cx + rx + 3)):
        for z in range(int(cz - rz - 2), int(cz + rz + 3)):
            for y in range(int(cy - ry - 2), int(cy + ry + 3)):
                d = min(((x + .5 - lx) / (rx * lr)) ** 2 + ((y + .5 - ly) / (ry * lr)) ** 2 + ((z + .5 - lz) / (rz * lr)) ** 2 for lx, ly, lz, lr in lumps)
                d = min(d, ((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2 + ((z + .5 - cz) / rz) ** 2)
                n = sfbm((x + y * .4) / 4.0, (z - y * .5) / 4.0, 2, seed)
                if d + n * .28 > 1.0:
                    continue
                name = S.name(x, y, z)
                if 'cherry_wood' in name:
                    continue
                n2 = snoise((x + y) / 2.2, (z - y) / 2.2, seed + 1)
                st = 'stone' if n2 > .15 else 'andesite' if n2 > -.25 else 'tuff' if n2 > -.5 else 'cobblestone'
                S.set(x, y, z, st)
    # moss on the tops
    for x in range(int(cx - rx - 2), int(cx + rx + 3)):
        for z in range(int(cz - rz - 2), int(cz + rz + 3)):
            for y in range(int(cy + ry + 2), int(cy - ry - 3), -1):
                name = S.name(x, y, z)
                if name.endswith(('stone', 'andesite', 'tuff', 'cobblestone')):
                    if snoise(x / 3.0, z / 3.0, seed + 2) > .1 and S.is_air(x, y + 1, z):
                        S.set(x, y, z, 'moss_block')
                    break
                if name != 'minecraft:air':
                    break


def small_cherry(x, z, h, seed, spread=1.0):
    rnd = random.Random(seed)
    y = ground_y(x, z) + 1
    lean = rnd.uniform(-1, 1) * .35
    lz = rnd.uniform(-1, 1) * .35
    tops = []
    cells = set()
    limb([(x + .5, y - 1, z + .5, 1.1), (x + .5 + lean * h * .3, y + h * .4, z + .5 + lz * h * .3, .85), (x + .5 + lean * h * .55, y + h * .75, z + .5 + lz * h * .55, .6)], cells, seed=seed)
    for a in range(5):
        ang = a * 2.4 + rnd.random()
        l = h * .55 * spread
        tx, ty, tz = x + .5 + lean * h * .55 + math.cos(ang) * l, y + h * (.8 + rnd.uniform(0, .35)), z + .5 + lz * h * .55 + math.sin(ang) * l
        limb([(x + .5 + lean * h * .5, y + h * .68, z + .5 + lz * h * .5, .5), (tx, ty, tz, .45)], cells, seed=seed + a)
        tops.append((tx, ty, tz))
    for (cx, cy, cz) in cells:
        S.set(cx, cy, cz, 'cherry_wood[axis=y]')
    for i, (tx, ty, tz) in enumerate(tops):
        blossom(tx, ty + 1, tz, h * .42 * spread, h * .27, h * .42 * spread, seed + 200 + i)


def green_tree(x, z, h, seed, kind='oak'):
    rnd = random.Random(seed)
    y = ground_y(x, z) + 1
    wood = f'{kind}_log[axis=y]'
    leaf = f'{kind}_leaves[persistent=true]'
    for i in range(h):
        S.set(x, y + i, z, wood)
    r = max(3, h // 2)
    for ax in range(-r - 1, r + 2):
        for az in range(-r - 1, r + 2):
            for ay in range(-2, r + 1):
                d = (ax / r) ** 2 + (az / r) ** 2 + ((ay - 1) / (r * .8)) ** 2
                if d + rnd.uniform(-.1, .1) < 1 and S.is_air(x + ax, y + h + ay - 1, z + az):
                    S.set(x + ax, y + h + ay - 1, z + az, leaf)


def make_other_trees():
    # A pair of cherries framing the house, more behind it, then a scattering further out.
    small_cherry(-33, 6, 11, 61, 1.15)
    small_cherry(33, 8, 12, 62, 1.15)
    small_cherry(-44, -14, 10, 63)
    small_cherry(47, -8, 10, 64)
    small_cherry(-23, 22, 7, 65)
    small_cherry(25, 26, 7, 66)
    rnd = random.Random(77)
    placed = []
    tries = 0
    while len(placed) < 70 and tries < 4000:
        tries += 1
        x, z = rnd.randint(X0 + 6, X1 - 7), rnd.randint(Z0 + 6, Z1 - 7)
        # keep the meadow in front of the camera clear
        if -60 < x < 62 and -106 < z < 0:
            continue
        if -30 < x < 30 and -30 < z < 60:
            continue
        if any((x - a) ** 2 + (z - b) ** 2 < 170 for a, b in placed):
            continue
        placed.append((x, z))
        r = rnd.random()
        if r < .42:
            small_cherry(x, z, rnd.randint(7, 10), 300 + tries)
        elif r < .74:
            green_tree(x, z, rnd.randint(6, 8), 300 + tries, 'oak')
        else:
            green_tree(x, z, rnd.randint(7, 9), 300 + tries, 'birch')
    # a few green trees on the far side of the meadow, like the ones in the picture
    for x, z, h, kind in ((-22, -110, 9, 'oak'), (-13, -106, 7, 'birch'), (9, -112, 8, 'oak'), (21, -108, 7, 'oak'), (-40, -112, 8, 'birch'), (42, -113, 9, 'oak'), (-62, -104, 8, 'oak'), (66, -102, 8, 'birch')):
        green_tree(x, z, h, x * 7 + z, kind)


# ------------------------------------------------------------------ meadow plants
SINGLE = ['pink_tulip', 'allium', 'oxeye_daisy', 'azure_bluet', 'cornflower', 'lily_of_the_valley', 'poppy', 'white_tulip', 'red_tulip']
TALL = ['peony', 'rose_bush', 'lilac']


def dist_to_flight(x, z):
    # Roughly how close the camera ever comes, so far corners get sparser plants.
    d = min(math.hypot(x, z - 30) if z > 0 else 999, abs(x) if -30 < z < 60 else 999, math.hypot(x - 3, z + 42), math.hypot(x + 14, z + 40))
    return d


def make_plants():
    rnd = random.Random(1234)
    NAME_GRASS = S.pal('grass_block')
    for x in range(X0, X1):
        for z in range(Z0, Z1):
            top = ground_y(x, z)
            yy = top - Y0
            if yy < 0 or yy + 2 >= NY:
                continue
            i, k = x - X0, z - Z0
            if S.grid[i, yy, k] != NAME_GRASS or S.grid[i, yy + 1, k] != 0:
                continue
            d = dist_to_flight(x, z)
            keep = 1.0 if d < 30 else .5 if d < 55 else .16
            if rnd.random() > keep:
                continue
            f = sfbm(x / 11.0, z / 11.0, 2, 61)    # where the flower drifts are
            p = sfbm(x / 7.0, z / 7.0, 2, 62)  # where the petals lie
            under_tree = math.hypot((x - 8) / 40, (z - TREE_Z - 2) / 22) < 1
            r = rnd.random()
            # petals on the ground under the tree and in pink drifts along the yard
            if (under_tree and r < .3) or (p > .3 and r < .28):
                amount = rnd.choice((1, 1, 2, 2, 3, 4))
                S.set(x, top + 1, z, f'pink_petals[flower_amount={amount},facing={rnd.choice(["north", "south", "east", "west"])}]')
                continue
            if f > .2 and r < .34:
                # flower drifts: mostly pinks and reds, some white and blue for life
                sh = rnd.random()
                if sh < .13 and S.grid[i, yy + 2, k] == 0:
                    t = rnd.choice(TALL)
                    S.set(x, top + 1, z, f'{t}[half=lower]')
                    S.set(x, top + 2, z, f'{t}[half=upper]')
                else:
                    S.set(x, top + 1, z, rnd.choice(SINGLE[:4] if sh < .66 else SINGLE))
                continue
            if r < .36:
                if r < .03 and S.grid[i, yy + 2, k] == 0:
                    S.set(x, top + 1, z, 'tall_grass[half=lower]')
                    S.set(x, top + 2, z, 'tall_grass[half=upper]')
                elif r < .05:
                    S.set(x, top + 1, z, 'fern')
                else:
                    S.set(x, top + 1, z, 'grass')
            elif r < .375:
                S.set(x, top + 1, z, rnd.choice(SINGLE))


# ------------------------------------------------------------------ joining fences, panes and walls
def join_up():
    grid = S.grid
    joins = {}
    for i, name in enumerate(S.palette):
        base = name.split('|')[0].split('[')[0].split(':')[-1]
        if base.endswith('_fence'):
            joins[i] = 'fence'
        elif base.endswith('_pane') or base == 'iron_bars':
            joins[i] = 'pane'
        elif base.endswith('_wall'):
            joins[i] = 'wall'

    def solid(name):
        base = name.split('|')[0].split('[')[0].split(':')[-1]
        if base == 'air':
            return False
        for bit in ('stairs', 'slab', 'fence', 'pane', 'wall', 'lantern', 'chain', 'leaves', 'flower', 'grass', 'petals', 'tulip', 'allium', 'potted', 'carpet', 'azalea', 'door', 'daisy', 'bluet', 'poppy', 'fern', 'bush', 'lilac', 'peony', 'cornflower', 'valley'):
            if bit in base:
                return False
        return True

    def family(a, b):
        return joins.get(a) == joins.get(b) and a in joins

    where = np.isin(grid, list(joins.keys()))
    xs, ys, zs = np.nonzero(where)
    for ax, ay, az in zip(xs.tolist(), ys.tolist(), zs.tolist()):
        me = int(grid[ax, ay, az])
        kind = joins[me]
        base = S.palette[me].split('|')[0].split('[')[0]
        bits = {}
        count = 0
        for key, dx, dz in (('north', 0, -1), ('south', 0, 1), ('east', 1, 0), ('west', -1, 0)):
            nx, nz = ax + dx, az + dz
            on = False
            if 0 <= nx < NX and 0 <= nz < NZ:
                other = int(grid[nx, ay, nz])
                on = family(me, other) or solid(S.palette[other])
            if kind == 'wall':
                bits[key] = 'low' if on else 'none'
            else:
                bits[key] = 'true' if on else 'false'
            count += on
        if kind == 'wall':
            bits['up'] = 'true'
        state = base + '[' + ','.join(f'{k}={v}' for k, v in sorted(bits.items())) + ']'
        grid[ax, ay, az] = S.pal(state)


# ------------------------------------------------------------------ far land
def far_height(x, z):
    # The land beyond the box: same ground as inside it near the edge, then hills, then mountains with a tall peak off to the left.
    x = np.asarray(x, dtype=np.float64)
    z = np.asarray(z, dtype=np.float64)
    h = land_height(x, z)
    r = np.sqrt(x * x + z * z)
    far = ss(240.0, 1100.0, r)
    n = fbm(x / 260.0, z / 260.0, 5, 71)
    ridged = 1 - np.abs(fbm(x / 520.0, z / 520.0, 5, 72))
    h = h + far * (40 * n + 110 * ridged * ridged)
    # forest hills in the middle distance
    mid = ss(190.0, 520.0, r) * (1 - ss(800.0, 1300.0, r))
    h = h + mid * (46 * np.abs(fbm(x / 120.0, z / 120.0, 4, 73)))
    # the great peak on the left of the view from the tree, and its neighbours
    for px, pz, ph, w in ((-610, -1120, 300, 150), (-760, -1020, 190, 130), (-430, -1240, 170, 120), (240, -1380, 210, 220), (640, -1180, 170, 190), (-1200, -640, 200, 260), (1200, -540, 180, 260)):
        d = np.sqrt((x - px) ** 2 + (z - pz) ** 2)
        lump = ph * np.exp(-(d / w) ** 1.5)
        h = h + lump * (1 + .28 * fbm(x / 60.0, z / 60.0, 3, 74))
    return h


def far_colour(x, z, h, slope, size=8):
    # Returns rgb 0..1 for a column: meadow greens, darker forest, cherry groves dotted in pink, rock high up and on steep sides.
    g = np.array([.46, .63, .27])
    forest = np.array([.20, .38, .17])
    rock = np.array([.40, .46, .52])
    n = sfbm(x / 130.0, z / 130.0, 3, 81)
    speck = snoise(x / 19.0, z / 19.0, 82)
    c = g * (.95 + .2 * n)
    if n > -.15:
        c = forest * (1 + .3 * speck)
    if size >= 48 and speck > .32 and n < .3 and h < 60:
        c = np.array([.86, .62, .70])
    if slope > 1.2 or h > 230:
        t = min(.8, max(0.0, (max(slope - 1.2, (h - 230) / 90)) * .7))
        c = c * (1 - t) + rock * t
    return c


def make_far():
    # Rings of square columns, wider the further out they are. Only what the camera can look at: the land behind the start point stops early.
    quads = []

    def add(p0, p1, p2, p3, normal, col):
        lit = .62 + .05 * normal[1] + .55 * max(0.0, float(np.dot(normal, SUN)))
        rgb = np.clip(col * lit * np.array([1.0, .98, .95]), 0, 1)
        quads.append((p0, p1, p2, p3, tuple(int(v * 255) for v in rgb)))

    rings = [(8, -420, 420, -560, 300), (16, -1000, 1000, -1000, 640), (48, -2600, 2600, -2600, 1300), (112, -5600, 5600, -5400, 2200)]
    done = []
    for size, ax0, ax1, az0, az1 in rings:
        xs = np.arange(ax0, ax1, size)
        zs = np.arange(az0, az1, size)
        gx, gz = np.meshgrid(xs + size / 2, zs + size / 2, indexing='ij')
        h = far_height(gx, gz)
        spk = vnoise(gx / 14.0, gz / 14.0, 83)
        # trees: bumps on the forest and cherry columns
        h = h + np.where(fbm(gx / 130.0, gz / 130.0, 3, 81) > -.15, spk * 7 + 3, 0)
        h = np.round(h / max(1, size // 8)) * max(1, size // 8)
        for i in range(len(xs)):
            for k in range(len(zs)):
                x, z = int(xs[i]), int(zs[k])
                # skip anything inside the block box or a finer ring already made
                if x + size > X0 + 1 and x < X1 - 1 and z + size > Z0 + 1 and z < Z1 - 1:
                    continue
                inner = False
                for s0, a0, a1, b0, b1 in done:
                    if x + size > a0 and x < a1 and z + size > b0 and z < b1:
                        inner = True
                        break
                if inner:
                    continue
                y = float(h[i, k])
                nb = [h[i + 1, k] if i + 1 < len(xs) else y - 200, h[i - 1, k] if i > 0 else y - 200, h[i, k + 1] if k + 1 < len(zs) else y - 200, h[i, k - 1] if k > 0 else y - 200]
                slope = max(abs(y - float(v)) for v in nb[:4] if v > y - 150) / size if any(v > y - 150 for v in nb) else 0
                col = far_colour(x, z, y, slope, size)
                x1, z1 = x + size, z + size
                add((x, y, z), (x, y, z1), (x1, y, z1), (x1, y, z), np.array([0, 1, 0]), col)
                side = col * .86 if size < 48 else col * .92
                if nb[0] < y:
                    add((x1, y, z1), (x1, nb[0], z1), (x1, nb[0], z), (x1, y, z), np.array([1, 0, 0]), side)
                if nb[1] < y:
                    add((x, y, z), (x, nb[1], z), (x, nb[1], z1), (x, y, z1), np.array([-1, 0, 0]), side)
                if nb[2] < y:
                    add((x, y, z1), (x, nb[2], z1), (x1, nb[2], z1), (x1, y, z1), np.array([0, 0, 1]), side)
                if nb[3] < y:
                    add((x1, y, z), (x1, nb[3], z), (x, nb[3], z), (x, y, z), np.array([0, 0, -1]), side)
        done.append((size, ax0, ax1, az0, az1))
    return quads


# ------------------------------------------------------------------ writing
def write_world(path):
    with gzip.open(path, 'wb', compresslevel=9) as f:
        f.write(struct.pack('>6i', X0, Y0, Z0, NX, NY, NZ))
        f.write(struct.pack('>i', len(S.palette)))
        for name in S.palette:
            b = name.encode('utf-8')
            f.write(struct.pack('>H', len(b)))
            f.write(b)
        # x slowest, then z, then y: columns stay together, it packs far better
        f.write(S.grid.transpose(0, 2, 1).astype('>u2').tobytes())


def write_far(path, quads):
    arr = np.zeros((len(quads), 16), dtype='>i2')
    cols = bytearray()
    for i, (p0, p1, p2, p3, c) in enumerate(quads):
        arr[i, 0:3] = p0
        arr[i, 3:6] = p1
        arr[i, 6:9] = p2
        arr[i, 9:12] = p3
        arr[i, 12] = c[0]
        arr[i, 13] = c[1]
        arr[i, 14] = c[2]
    with gzip.open(path, 'wb', compresslevel=9) as f:
        f.write(struct.pack('>i', len(quads)))
        f.write(arr.tobytes())


def make_meta():
    lines = []
    lines.append(f'sun {SUN[0]:.4f} {SUN[1]:.4f} {SUN[2]:.4f}')
    for (cx, cy, cz, hinge, px, pz, turn, group) in DOORS:
        lines.append(f'door {group} {cx} {cy} {cz} {hinge} {px:.4f} {pz:.4f} {turn}')
    lines.append(f'sleeper {SLEEPER["x"]:.3f} {SLEEPER["y"]:.3f} {SLEEPER["z"]:.3f}')
    for name, x, y, z, w, h, facing in PAINTINGS:
        lines.append(f'painting {name} {x:.3f} {y:.3f} {z:.3f} {w} {h} {facing}')
    # house box: no petals fall inside it
    lines.append(f'indoors {HOUSE["x0"]} 0 {HOUSE["z0"]} {HOUSE["x1"] + 1} 26 {HOUSE["z1"] + 1}')
    eye = FLOOR + 1.42
    # camera keys: x y z, look x y z, field of view
    front = (-2.5, 5.0, 43.0, -8.0, 9.8, -6.0, 56.0)
    lines.append('front ' + ' '.join(f'{v:.2f}' for v in front))
    tree = (0.9, 20.2, TREE_Z + 14.55, 0.9, 16.4, TREE_Z + 3.55, 46.0)
    lines.append('tree ' + ' '.join(f'{v:.2f}' for v in tree))
    fly_out = [
        (-1.6, 4.6, 30.0, -2.0, 6.4, -6.0, 57.0),
        (-0.6, eye + .4, 16.0, 0.0, eye + .4, -6.0, 60.0),
        (0.0, eye, 6.0, 0.0, eye, -10.0, 68.0),
        (0.0, eye, -2.0, 0.0, eye, -22.0, 72.0),
        (0.0, eye, -12.0, 0.0, eye, -36.0, 72.0),
        (0.0, eye, -24.0, 0.0, eye + 1.0, -50.0, 72.0),
        (-3.0, eye + 1.0, -31.0, 3.0, eye + 6.0, -62.0, 68.0),
        (-13.0, 8.5, -38.0, 6.0, 15.0, -64.0, 62.0),
        (-6.0, 15.5, -46.0, 6.0, 16.0, -62.0, 54.0),
    ]
    for k in fly_out:
        lines.append('flyout ' + ' '.join(f'{v:.2f}' for v in k))
    fly_back = [
        (-2.0, 14.0, -44.0, -6.0, 10.0, -26.0, 56.0),
        (-13.0, 7.2, -38.0, -2.0, 6.0, -22.0, 62.0),
        (-5.0, eye + 1.0, -32.0, 0.0, eye, -10.0, 66.0),
        (0.0, eye, -25.0, 0.0, eye, 0.0, 72.0),
        (0.0, eye, -12.0, 0.0, eye, 6.0, 72.0),
        (0.0, eye, -2.0, 0.0, eye, 16.0, 72.0),
        (0.0, eye + .2, 8.0, 0.0, eye + 1, 24.0, 72.0),
        (-6.0, 5.2, 19.0, -14.0, 6.0, 30.0, 66.0),
        (-15.0, 6.5, 32.0, -18.0, 8.0, 6.0, 60.0),
    ]
    for k in fly_back:
        lines.append('flyback ' + ' '.join(f'{v:.2f}' for v in k))
    return '\n'.join(lines) + '\n'


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'charmenu', 'scene')
    os.makedirs(out, exist_ok=True)
    make_land()
    make_house()
    make_yard()
    make_big_tree()
    make_other_trees()
    make_plants()
    join_up()
    write_world(os.path.join(out, 'world.bin.gz'))
    quads = make_far()
    write_far(os.path.join(out, 'far.bin.gz'), quads)
    with open(os.path.join(out, 'meta.txt'), 'w', encoding='utf-8') as f:
        f.write(make_meta())
    used = int((S.grid != 0).sum())
    print(f'cells used {used}, palette {len(S.palette)}, far quads {len(quads)}')
    for fn in ('world.bin.gz', 'far.bin.gz', 'meta.txt'):
        print(fn, os.path.getsize(os.path.join(out, fn)) // 1024, 'KB')
    return S


if __name__ == '__main__':
    main()
