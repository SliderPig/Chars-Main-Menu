package com.example.charmenu.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LightBakeTest {
    private static final int N = 24;

    private static int at(int x, int y, int z) {
        return (x * N + z) * N + y;
    }

    private static byte[] floorOnly() {
        byte[] o = new byte[N * N * N];
        for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) o[at(x, 0, z)] = LightBake.SOLID;
        return o;
    }

    private static final double[] SUN = {0, 1, 0};

    @Test
    void openGroundIsInFullSun() {
        LightBake b = new LightBake(N, N, N, floorOnly());
        b.bakeSky(SUN[0], SUN[1], SUN[2]);
        assertEquals(15, b.sky[at(5, 1, 5)]);
    }

    @Test
    void underARoofThereIsShadeButStillSomeSkyFromTheSides() {
        byte[] o = floorOnly();
        for (int x = 8; x < 14; x++) for (int z = 8; z < 14; z++) o[at(x, 5, z)] = LightBake.SOLID;
        LightBake b = new LightBake(N, N, N, o);
        b.bakeSky(SUN[0], SUN[1], SUN[2]);
        int under = b.sky[at(11, 1, 11)];
        assertTrue(under <= 8, "no direct sun under a roof, got " + under);
        assertTrue(under > 0, "some sky comes in from the sides");
    }

    @Test
    void leavesGiveDappledShadeLighterThanARoof() {
        byte[] o = floorOnly();
        for (int x = 8; x < 14; x++) for (int z = 8; z < 14; z++) o[at(x, 5, z)] = LightBake.PART;
        LightBake b = new LightBake(N, N, N, o);
        b.bakeSky(SUN[0], SUN[1], SUN[2]);
        int under = b.sky[at(11, 1, 11)];
        assertTrue(under > 8 && under < 15, "partly sunny under leaves, got " + under);
    }

    @Test
    void lampLightFadesWithDistanceAndStopsAtWalls() {
        byte[] o = new byte[N * N * N];
        for (int y = 0; y < N; y++) for (int z = 0; z < N; z++) o[at(12, y, z)] = LightBake.SOLID;
        LightBake b = new LightBake(N, N, N, o);
        b.floodBlockLight(new int[] {at(6, 6, 6)}, new byte[] {15});
        assertEquals(15, b.block[at(6, 6, 6)]);
        assertEquals(12, b.block[at(9, 6, 6)]);
        assertEquals(0, b.block[at(13, 6, 6)]);
    }

    @Test
    void aNearbyWallThrowsAShadowAcrossTheGround() {
        byte[] o = floorOnly();
        for (int y = 1; y < 12; y++) for (int z = 0; z < N; z++) o[at(12, y, z)] = LightBake.SOLID;
        LightBake b = new LightBake(N, N, N, o);
        double len = Math.sqrt(0.5);
        b.bakeSky(len, len, 0);
        assertTrue(b.sky[at(8, 1, 12)] <= 8, "the side away from the sun is shaded, got " + b.sky[at(8, 1, 12)]);
        assertEquals(15, b.sky[at(16, 1, 12)], "the sunny side is lit");
    }
}
