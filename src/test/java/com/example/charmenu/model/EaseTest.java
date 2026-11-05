package com.example.charmenu.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EaseTest {
    @Test
    void curvesStartAtZeroAndEndAtOne() {
        assertEquals(0, Ease.smooth(0), 1e-12);
        assertEquals(1, Ease.smooth(1), 1e-12);
        assertEquals(0, Ease.smoother(0), 1e-12);
        assertEquals(1, Ease.smoother(1), 1e-12);
        assertEquals(0, Ease.cruise(0, 0.25, 0.4), 1e-12);
        assertEquals(1, Ease.cruise(1, 0.25, 0.4), 1e-12);
    }

    @Test
    void valuesOutsideTheRangeStayClamped() {
        assertEquals(0, Ease.cruise(-3, 0.25, 0.4), 1e-12);
        assertEquals(1, Ease.cruise(9, 0.25, 0.4), 1e-12);
        assertEquals(1, Ease.smoother(1.5), 1e-12);
    }

    @Test
    void cruiseAlwaysMovesForwardWithNoJumps() {
        double last = 0;
        double lastStep = 0;
        for (int i = 1; i <= 2000; i++) {
            double v = Ease.cruise(i / 2000.0, 0.26, 0.42);
            double step = v - last;
            assertTrue(step >= -1e-12, "never goes backwards");
            assertTrue(Math.abs(step - lastStep) < 1e-4, "speed never jolts, at " + i);
            last = v;
            lastStep = step;
        }
    }

    @Test
    void cruiseStartsAndStopsAtRest() {
        double e = 1e-4;
        assertTrue(Ease.cruise(e, 0.26, 0.42) / e < 1e-3, "starts from rest");
        assertTrue((1 - Ease.cruise(1 - e, 0.26, 0.42)) / e < 1e-3, "stops at rest");
    }

    @Test
    void cruisePartsJoinUp() {
        double in = 0.3, out = 0.3;
        double a = Ease.cruise(in - 1e-9, in, out), b = Ease.cruise(in + 1e-9, in, out);
        assertEquals(a, b, 1e-6);
        double c = Ease.cruise(1 - out - 1e-9, in, out), d = Ease.cruise(1 - out + 1e-9, in, out);
        assertEquals(c, d, 1e-6);
    }

    @Test
    void towardGivesTheSameAnswerAtAnyFrameRate() {
        double slow = 0;
        for (int i = 0; i < 30; i++) slow = Ease.toward(slow, 1, 1 / 30.0, 6);
        double fast = 0;
        for (int i = 0; i < 240; i++) fast = Ease.toward(fast, 1, 1 / 240.0, 6);
        assertEquals(slow, fast, 1e-9);
    }

    @Test
    void doorSwingEndsOpenAndOverTravelsOnlyALittle() {
        assertEquals(0, Ease.swing(0), 1e-9);
        assertEquals(1, Ease.swing(1), 1e-9);
        for (int i = 0; i <= 100; i++) assertTrue(Ease.swing(i / 100.0) < 1.08);
    }
}
