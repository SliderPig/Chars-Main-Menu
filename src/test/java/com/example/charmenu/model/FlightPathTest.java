package com.example.charmenu.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FlightPathTest {
    private static double[] key(double x, double y, double z, double fov) {
        return new double[] {x, y, z, x, y, z - 10, fov};
    }

    // keys spaced very unevenly, on purpose: like the real path (long approach, tight doors)
    private static final List<double[]> KEYS = List.of(
        key(-7, 5, 45, 56), key(-3, 5, 30, 57), key(0, 3.4, 6, 68), key(0, 3.4, -2, 72),
        key(0, 3.4, -12, 72), key(0, 3.4, -24, 72), key(-12, 7, -39, 62), key(2, 8.4, -37, 54));

    @Test
    void endsExactlyOnTheFirstAndLastKeys() {
        FlightPath p = new FlightPath(KEYS);
        double[] a = p.at(0), b = p.at(1);
        for (int c = 0; c < FlightPath.CHANNELS; c++) {
            assertEquals(KEYS.get(0)[c], a[c], 1e-6);
            assertEquals(KEYS.get(KEYS.size() - 1)[c], b[c], 1e-6);
        }
    }

    @Test
    void passesThroughEveryKey() {
        FlightPath p = new FlightPath(KEYS);
        for (double[] k : KEYS) {
            double best = Double.MAX_VALUE;
            for (int i = 0; i <= 3000; i++) {
                double[] q = p.at(i / 3000.0);
                double d = Math.hypot(Math.hypot(q[0] - k[0], q[1] - k[1]), q[2] - k[2]);
                best = Math.min(best, d);
            }
            assertTrue(best < 0.1, "misses a key by " + best);
        }
    }

    @Test
    void evenStepsAlongTheLineCoverEvenDistances() {
        FlightPath p = new FlightPath(KEYS);
        double step = p.length() / 500;
        double[] last = p.at(0);
        for (int i = 1; i <= 500; i++) {
            double[] q = p.at(i / 500.0);
            double d = Math.hypot(Math.hypot(q[0] - last[0], q[1] - last[1]), q[2] - last[2]);
            assertTrue(Math.abs(d - step) < step * 0.15, "uneven at step " + i + ": " + d + " vs " + step);
            last = q;
        }
    }

    @Test
    void doesNotOvershootThroughTheDoor() {
        // straight through the hall the line has to stay in the middle: the doorway is only two blocks wide
        FlightPath p = new FlightPath(KEYS);
        for (int i = 0; i <= 2000; i++) {
            double[] q = p.at(i / 2000.0);
            if (q[2] < 5 && q[2] > -23) assertTrue(Math.abs(q[0]) < 0.9, "x=" + q[0] + " at z=" + q[2]);
        }
    }

    @Test
    void findsWhereItCrossesAPlane() {
        FlightPath p = new FlightPath(KEYS);
        double f = p.crossingZ(1.0);
        assertTrue(f > 0 && f < 1);
        assertEquals(1.0, p.at(f)[2], 0.2);
        assertEquals(-1, p.crossingZ(500), 0);
    }

    @Test
    void refusesAPathWithOneKey() {
        assertThrows(IllegalArgumentException.class, () -> new FlightPath(List.of(key(0, 0, 0, 50))));
    }

    @Test
    void doorsAreOpenBeforeTheCameraGetsThere() {
        Flight f = new Flight(KEYS, 4.7, 0.26, 0.40, 1.0, -21.0);
        double frontDone = f.firstDoorStart() + Flight.DOOR_SWING;
        double backDone = f.secondDoorStart() + Flight.DOOR_SWING;
        for (double t = 0; t < 4.7; t += 0.01) {
            double z = f.key(t)[2];
            if (z < 1.0) assertTrue(t >= frontDone, "front doors still moving at " + t);
            if (z < -21.0) assertTrue(t >= backDone, "back doors still moving at " + t);
        }
        assertTrue(f.done(4.7));
    }

    @Test
    void flightRunsTheSameAtAnyFrameRate() {
        Flight f = new Flight(KEYS, 4.7, 0.26, 0.40, 1.0, -21.0);
        double[] a = f.key(2.0), b = f.key(2.0);
        for (int c = 0; c < FlightPath.CHANNELS; c++) assertEquals(a[c], b[c], 1e-12);
        // a small step in time is always a small step in space
        double[] last = f.key(0);
        for (double t = 0.01; t < 4.7; t += 0.01) {
            double[] q = f.key(t);
            double d = Math.hypot(Math.hypot(q[0] - last[0], q[1] - last[1]), q[2] - last[2]);
            assertTrue(d < 0.9, "jump of " + d + " at " + t);
            last = q;
        }
    }
}
