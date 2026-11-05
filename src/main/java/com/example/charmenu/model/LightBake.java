package com.example.charmenu.model;

import java.util.Arrays;

// Works out the light over the scene's grid of cells once, when the scene loads.
// Sky light is one number per cell, used twice: 0..8 is how much open sky it can reach, anything above 8 is how much direct sun gets there.
// That way the game's own smooth lighting blends soft shadows from the house and the tree with no extra work per frame.
public final class LightBake {
    public static final byte AIR = 0, PART = 1, SOLID = 2;

    private final int nx, ny, nz;
    private final byte[] opacity;   // AIR, PART (leaves) or SOLID per cell
    public final byte[] sky, block;

    public LightBake(int nx, int ny, int nz, byte[] opacity) {
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.opacity = opacity;
        this.sky = new byte[opacity.length];
        this.block = new byte[opacity.length];
    }

    private int at(int x, int y, int z) {
        return (x * nz + z) * ny + y;
    }

    // Lamps: flood outwards from each lamp losing one level a step, stopped by solid blocks.
    public void floodBlockLight(int[] lampCells, byte[] lampLevels) {
        Flood f = new Flood(block);
        for (int i = 0; i < lampCells.length; i++) f.seed(lampCells[i], lampLevels[i]);
        f.run();
    }

    // Open sky: straight down from the top, leaves take a little off, solid blocks stop it. Then it spreads sideways under roofs like normal light.
    public void bakeSky(double sunX, double sunY, double sunZ) {
        byte[] open = new byte[opacity.length];
        int top = 0;
        for (int x = 0; x < nx; x++) {
            for (int z = 0; z < nz; z++) {
                int level = 15;
                for (int y = ny - 1; y >= 0; y--) {
                    int i = at(x, y, z);
                    byte o = opacity[i];
                    if (o == SOLID) level = 0;
                    else if (o == PART) level = Math.max(0, level - 2);
                    if (o != AIR && y > top) top = y;
                    open[i] = (byte) level;
                }
            }
        }
        Flood f = new Flood(open);
        for (int i = 0; i < open.length; i++) if (open[i] > 1) f.seed(i, open[i]);
        f.run();

        double step = 0.7;
        double sx = sunX * step, sy = sunY * step, sz = sunZ * step;
        for (int x = 0; x < nx; x++) {
            for (int z = 0; z < nz; z++) {
                for (int y = 0; y < ny; y++) {
                    int i = at(x, y, z);
                    if (opacity[i] == SOLID) continue;
                    int ambient = Math.round(open[i] * 8 / 15f);
                    double vis;
                    if (y > top) vis = 1;
                    else if (!nearSomething(x, y, z)) vis = open[i] == 15 ? 1 : 0;
                    else vis = sunReaches(x, y, z, sx, sy, sz, top);
                    sky[i] = (byte) Math.min(15, ambient + Math.round(vis * 7));
                }
            }
        }
    }

    // Only cells touching something are ever looked at for lighting: no need to trace from empty air.
    private boolean nearSomething(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int ax = x + dx, ay = y + dy, az = z + dz;
                    if (ax < 0 || ay < 0 || az < 0 || ax >= nx || ay >= ny || az >= nz) continue;
                    if (opacity[at(ax, ay, az)] != AIR) return true;
                }
            }
        }
        return false;
    }

    private double sunReaches(int x, int y, int z, double sx, double sy, double sz, int top) {
        double px = x + 0.5, py = y + 0.5, pz = z + 0.5, vis = 1;
        int lastCell = at(x, y, z);
        for (int n = 0; n < 200; n++) {
            px += sx;
            py += sy;
            pz += sz;
            int ax = (int) Math.floor(px), ay = (int) Math.floor(py), az = (int) Math.floor(pz);
            if (ay > top || ax < 0 || az < 0 || ax >= nx || az >= nz || ay >= ny) return vis;
            if (ay < 0) return 0;
            int cell = at(ax, ay, az);
            if (cell == lastCell) continue;
            lastCell = cell;
            byte o = opacity[cell];
            if (o == SOLID) return 0;
            if (o == PART) {
                // each leaf block lets a varying amount through, so the shade under the tree is dappled
                int h = (cell * 73856093) ^ (cell >>> 7);
                vis *= 0.45 + 0.4 * ((h >>> 3 & 255) / 255.0);
                if (vis < 0.04) return 0;
            }
        }
        return vis;
    }

    // Spreads light outwards by levels, brightest first. Uses arrays only, no boxes, so it stays fast over millions of cells.
    private final class Flood {
        private final byte[] target;
        private final int[][] buckets = new int[16][];
        private final int[] sizes = new int[16];

        Flood(byte[] target) {
            this.target = target;
            for (int i = 0; i < 16; i++) buckets[i] = new int[256];
        }

        void seed(int cell, int level) {
            if (level <= 0) return;
            target[cell] = (byte) Math.max(target[cell], level);
            push(level, cell);
        }

        private void push(int level, int cell) {
            int[] b = buckets[level];
            if (sizes[level] == b.length) {
                b = Arrays.copyOf(b, b.length * 2);
                buckets[level] = b;
            }
            b[sizes[level]++] = cell;
        }

        void run() {
            for (int level = 15; level > 1; level--) {
                int[] b = buckets[level];
                int n = sizes[level];
                for (int q = 0; q < n; q++) {
                    int cell = b[q];
                    if (target[cell] != level) continue;
                    int y = cell % ny;
                    int rest = cell / ny;
                    int z = rest % nz;
                    int x = rest / nz;
                    spread(x + 1, y, z, level);
                    spread(x - 1, y, z, level);
                    spread(x, y + 1, z, level);
                    spread(x, y - 1, z, level);
                    spread(x, y, z + 1, level);
                    spread(x, y, z - 1, level);
                }
                sizes[level] = 0;
                buckets[level] = new int[16];
            }
        }

        private void spread(int x, int y, int z, int level) {
            if (x < 0 || y < 0 || z < 0 || x >= nx || y >= ny || z >= nz) return;
            int cell = at(x, y, z);
            if (opacity[cell] == SOLID) return;
            int next = level - 1;
            if (target[cell] < next) {
                target[cell] = (byte) next;
                push(next, cell);
            }
        }
    }
}
