package com.example.charmenu.model;

import java.util.List;

// One flight of the camera along a path, in seconds. Knows when each pair of doors has to start opening so they are out of the way in time.
public final class Flight {
    public static final double DOOR_SWING = 0.95;

    private final FlightPath path;
    private final double seconds, in, out;
    private final double firstDoor, secondDoor;

    public Flight(List<double[]> keys, double seconds, double in, double out, double firstZ, double secondZ) {
        this.path = new FlightPath(keys);
        this.seconds = seconds;
        this.in = in;
        this.out = out;
        this.firstDoor = doorStart(firstZ);
        this.secondDoor = doorStart(secondZ);
    }

    // The doors finish opening a moment before the camera gets there.
    private double doorStart(double z) {
        double f = path.crossingZ(z);
        if (f < 0) return Double.MAX_VALUE;
        double lo = 0, hi = seconds;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (Ease.cruise(mid / seconds, in, out) < f) lo = mid;
            else hi = mid;
        }
        return Math.max(0.05, lo - DOOR_SWING - 0.12);
    }

    public double seconds() {
        return seconds;
    }

    public double[] key(double elapsed) {
        return path.at(Ease.cruise(elapsed / seconds, in, out));
    }

    public double firstDoorStart() {
        return firstDoor;
    }

    public double secondDoorStart() {
        return secondDoor;
    }

    public boolean done(double elapsed) {
        return elapsed >= seconds;
    }
}
