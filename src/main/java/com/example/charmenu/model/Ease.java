package com.example.charmenu.model;

// Easing curves. Everything takes 0..1 and gives 0..1, nothing here knows about the game.
public final class Ease {
    private Ease() {}

    public static double clamp(double t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    // Gentle start and stop.
    public static double smooth(double t) {
        t = clamp(t);
        return t * t * (3 - 2 * t);
    }

    // Even gentler: no sudden change in speed at either end.
    public static double smoother(double t) {
        t = clamp(t);
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    // A flight: speeds up over the first 'in' of the time, holds its speed, slows down over the last 'out'.
    // The speed follows a smooth step up and down, so there is never a jolt.
    public static double cruise(double t, double in, double out) {
        t = clamp(t);
        double whole = 1 - in / 2 - out / 2;
        if (t < in) {
            double x = t / in;
            return in * (x * x * x - x * x * x * x / 2) / whole;
        }
        if (t <= 1 - out) {
            return (in / 2 + (t - in)) / whole;
        }
        double y = (1 - t) / out;
        return (whole - out * (y * y * y - y * y * y * y / 2)) / whole;
    }

    // A door leaf: swings out with a little over-travel and settles.
    public static double swing(double t) {
        t = clamp(t);
        double s = smoother(t);
        return s + 0.05 * Math.sin(t * Math.PI) * t * t;
    }

    // Smooths a value towards a target the same amount per second at any frame rate.
    public static double toward(double value, double target, double seconds, double rate) {
        return target + (value - target) * Math.exp(-seconds * rate);
    }
}
