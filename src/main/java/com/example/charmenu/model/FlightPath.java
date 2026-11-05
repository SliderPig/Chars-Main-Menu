package com.example.charmenu.model;

import java.util.List;

// A smooth line through camera keys. Each key is x, y, z, look x, look y, look z, field of view.
// The line is a centripetal Catmull-Rom spline (it cannot loop or overshoot at uneven spacing) measured by distance along it,
// so at(0.5) is really half way there however the keys are spaced.
public final class FlightPath {
    private static final int STEPS = 48;
    public static final int CHANNELS = 7;

    private final double[][] keys;
    private final double[] knots;
    private final double[] lengths;
    private final double total;

    public FlightPath(List<double[]> keyList) {
        if (keyList.size() < 2) throw new IllegalArgumentException("a path needs two keys");
        int n = keyList.size();
        keys = new double[n + 2][];
        for (int i = 0; i < n; i++) keys[i + 1] = keyList.get(i);
        keys[0] = beyond(keys[1], keys[2]);
        keys[n + 1] = beyond(keys[n], keys[n - 1]);
        knots = new double[n + 2];
        for (int i = 1; i < n + 2; i++) {
            knots[i] = knots[i - 1] + Math.max(1e-4, Math.sqrt(distance(keys[i - 1], keys[i])));
        }
        int segments = n - 1;
        lengths = new double[segments * STEPS + 1];
        double[] last = on(1, 0);
        double sum = 0;
        for (int s = 0; s < segments; s++) {
            for (int j = 1; j <= STEPS; j++) {
                double[] p = on(s + 1, j / (double) STEPS);
                sum += distance(last, p);
                lengths[s * STEPS + j] = sum;
                last = p;
            }
        }
        total = sum;
    }

    private static double[] beyond(double[] a, double[] b) {
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; i++) out[i] = 2 * a[i] - b[i];
        return out;
    }

    private static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double length() {
        return total;
    }

    // The camera key at this fraction of the distance along the line.
    public double[] at(double fraction) {
        fraction = Ease.clamp(fraction);
        double want = fraction * total;
        int lo = 0, hi = lengths.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (lengths[mid] <= want) lo = mid;
            else hi = mid;
        }
        double span = lengths[hi] - lengths[lo];
        double inside = span <= 1e-9 ? 0 : (want - lengths[lo]) / span;
        double place = (lo + inside) / STEPS;
        int segment = Math.min((int) place, keys.length - 4);
        return on(segment + 1, place - segment);
    }

    // Barry-Goldman form of the spline on key segment i..i+1.
    private double[] on(int i, double u) {
        double t0 = knots[i - 1], t1 = knots[i], t2 = knots[i + 1], t3 = knots[i + 2];
        double t = t1 + (t2 - t1) * u;
        double[] out = new double[CHANNELS];
        for (int c = 0; c < CHANNELS; c++) {
            double p0 = keys[i - 1][c], p1 = keys[i][c], p2 = keys[i + 1][c], p3 = keys[i + 2][c];
            double a1 = lerp(p0, p1, (t - t0) / (t1 - t0));
            double a2 = lerp(p1, p2, (t - t1) / (t2 - t1));
            double a3 = lerp(p2, p3, (t - t2) / (t3 - t2));
            double b1 = lerp(a1, a2, (t - t0) / (t2 - t0));
            double b2 = lerp(a2, a3, (t - t1) / (t3 - t1));
            out[c] = lerp(b1, b2, (t - t1) / (t2 - t1));
        }
        return out;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // Finds where along the line (as a fraction of distance) the camera first crosses this z. -1 if it never does.
    public double crossingZ(double z) {
        boolean above = at(0)[2] > z;
        for (int i = 1; i <= 800; i++) {
            double f = i / 800.0;
            if ((at(f)[2] > z) != above) return f;
        }
        return -1;
    }
}
