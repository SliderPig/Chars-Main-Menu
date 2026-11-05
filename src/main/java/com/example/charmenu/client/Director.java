package com.example.charmenu.client;

import com.example.charmenu.model.Ease;
import com.example.charmenu.model.Flight;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

// Decides where the camera is at any moment: resting at the house, flying through it, or resting at the tree.
// Everything comes straight from the clock, so it runs at the same speed at any frame rate and can never jump.
public final class Director {
    public enum State { FRONT, FLYING_OUT, TREE, FLYING_BACK }

    private static final double OUT_SECONDS = 4.7, BACK_SECONDS = 4.6;
    // the z the camera crosses at each pair of doors
    private static final double FRONT_DOOR_Z = 1.0, BACK_DOOR_Z = -21.0;

    public static final class Cam {
        public double x, y, z, lx, ly, lz, fov, roll;

        void set(double[] k) {
            x = k[0];
            y = k[1];
            z = k[2];
            lx = k[3];
            ly = k[4];
            lz = k[5];
            fov = k[6];
            roll = 0;
        }

        double[] asKey() {
            return new double[] {x, y, z, lx, ly, lz, fov};
        }
    }

    private static final Director INSTANCE = new Director();

    public static Director get() {
        return INSTANCE;
    }

    private State state = State.FRONT;
    private final Cam cam = new Cam();
    private double now = clock(), stateStart = now, firstFrame = now;
    private double hazeStart = now;
    private Flight flight;
    private Runnable arrive;
    private double frontDoor, backDoor;
    private boolean firstCreak, secondCreak, whooshed;
    private double cutAt = -1;
    private Runnable cut;
    private double flashStart = -1;
    private double blackStart = -1;
    private double lastFrame = now;
    private double frameSeconds;
    private SceneData data;

    // Set by the walk-through test to hold the flight at an exact spot.
    public double debugProgress = -1;

    private static double clock() {
        return Util.getNanos() * 1e-9;
    }

    public State state() {
        return state;
    }

    public boolean flying() {
        return state == State.FLYING_OUT || state == State.FLYING_BACK;
    }

    public double time() {
        return now;
    }

    public double frameSeconds() {
        return frameSeconds;
    }

    public double frontDoor() {
        return frontDoor;
    }

    public double backDoor() {
        return backDoor;
    }

    void attach(SceneData d) {
        data = d;
    }

    // The scene just finished loading: fade up from the sky.
    void sceneReady() {
        hazeStart = clock();
    }

    // Back from a world, or the game just opened.
    public void resetToFront() {
        state = State.FRONT;
        stateStart = clock();
        hazeStart = stateStart;
        frontDoor = backDoor = 0;
        flight = null;
        arrive = null;
        cut = null;
    }

    public void update() {
        double t = clock();
        frameSeconds = Math.min(0.25, t - lastFrame);
        lastFrame = t;
        now = t;
        if (cutAt >= 0 && now >= cutAt) {
            cutAt = -1;
            Runnable r = cut;
            cut = null;
            if (r != null) r.run();
        }
        if (data == null) return;
        switch (state) {
            case FRONT -> {
                frontDoor = backDoor = 0;
                rest(data.front, now - stateStart, 1.0);
            }
            case TREE -> {
                frontDoor = 1;
                backDoor = 1;
                rest(data.tree, now - stateStart, 0.55);
            }
            case FLYING_OUT, FLYING_BACK -> fly();
        }
    }

    // A slow floating drift that starts from nothing, so arriving at a resting spot has no bump.
    private void rest(double[] key, double since, double size) {
        cam.set(key);
        double grow = Ease.smooth(since / 5.0) * size;
        cam.x += grow * (0.9 * Math.sin(now * 0.14) + 0.4 * Math.sin(now * 0.31 + 1.2));
        cam.y += grow * (0.25 * Math.sin(now * 0.19 + 0.5));
        cam.z += grow * (0.5 * Math.cos(now * 0.11));
        cam.lx += grow * 0.35 * Math.sin(now * 0.09 + 2.0);
        cam.ly += grow * 0.18 * Math.sin(now * 0.13);
    }

    private void fly() {
        double elapsed = now - stateStart;
        if (debugProgress >= 0) elapsed = debugProgress * flight.seconds();
        double[] key = flight.key(elapsed);
        cam.set(key);
        double swing = Flight.DOOR_SWING;
        if (state == State.FLYING_OUT) {
            frontDoor = Ease.swing((elapsed - flight.firstDoorStart()) / swing);
            backDoor = Ease.swing((elapsed - flight.secondDoorStart()) / swing);
            if (!firstCreak && elapsed >= flight.firstDoorStart()) {
                firstCreak = true;
                MenuSounds.door(1.0f);
            }
            if (!whooshed && elapsed >= flight.firstDoorStart() + 0.4) {
                whooshed = true;
                MenuSounds.whoosh();
            }
            if (!secondCreak && elapsed >= flight.secondDoorStart()) {
                secondCreak = true;
                MenuSounds.door(0.7f);
            }
        } else {
            // both pairs are open when this flight starts: they close softly behind the camera
            backDoor = 1 - Ease.smoother((elapsed - flight.firstDoorStart() - swing - 0.5) / swing);
            frontDoor = 1 - Ease.smoother((elapsed - flight.secondDoorStart() - swing - 0.45) / swing);
            if (!firstCreak && elapsed >= flight.secondDoorStart() + swing + 0.45) {
                firstCreak = true;
                MenuSounds.door(0.55f);
            }
            if (!whooshed && elapsed >= 0.2) {
                whooshed = true;
                MenuSounds.whoosh();
            }
        }
        if (debugProgress < 0 && flight.done(elapsed)) finishFlight();
    }

    private void finishFlight() {
        State end = state == State.FLYING_OUT ? State.TREE : State.FRONT;
        Runnable r = arrive;
        arrive = null;
        state = end;
        stateStart = now;
        cam.set(end == State.TREE ? data.tree : data.front);
        if (r != null) Minecraft.getInstance().tell(r);
    }

    // A menu button was pressed: open the doors and fly, then run what the button does.
    public void depart(Runnable action) {
        if (data == null || !MenuConfig.animation) {
            softCut(State.TREE, action);
            return;
        }
        List<double[]> keys = new ArrayList<>();
        keys.add(cam.asKey());
        keys.addAll(data.flyOut);
        keys.add(data.tree);
        flight = new Flight(keys, OUT_SECONDS, 0.26, 0.40, FRONT_DOOR_Z, BACK_DOOR_Z);
        beginFlight(State.FLYING_OUT, action);
    }

    // The title screen is showing again and the camera is still at the tree: come back round.
    public void comeBack() {
        if (state != State.TREE) return;
        if (data == null || !MenuConfig.animation) {
            softCut(State.FRONT, null);
            return;
        }
        List<double[]> keys = new ArrayList<>();
        keys.add(cam.asKey());
        keys.addAll(data.flyBack);
        keys.add(data.front);
        flight = new Flight(keys, BACK_SECONDS, 0.30, 0.42, BACK_DOOR_Z, FRONT_DOOR_Z);
        beginFlight(State.FLYING_BACK, null);
    }

    private void beginFlight(State s, Runnable action) {
        state = s;
        stateStart = now;
        arrive = action;
        firstCreak = secondCreak = whooshed = false;
    }

    // Another screen opened some other way (a mod's button, being disconnected...): the tree is always what's behind a menu.
    public void showTree() {
        if (state == State.FRONT && cutAt < 0) softCut(State.TREE, null);
    }

    // Any click while flying: a quick soft flash and we're there.
    public void skip() {
        if (flying() && cutAt < 0) {
            State end = state == State.FLYING_OUT ? State.TREE : State.FRONT;
            Runnable r = arrive;
            arrive = null;
            softCut(end, r);
        }
    }

    private void softCut(State end, Runnable action) {
        flashStart = now;
        cutAt = now + 0.14;
        cut = () -> {
            state = end;
            stateStart = clock();
            flight = null;
            if (data != null) cam.set(end == State.TREE ? data.tree : data.front);
            frontDoor = backDoor = end == State.TREE ? 1 : 0;
            if (action != null) Minecraft.getInstance().tell(action);
        };
    }

    public void fadeToBlack() {
        blackStart = now;
    }

    public Cam camera() {
        return cam;
    }

    // How strongly the pale sky haze covers everything right now (fade in).
    public float haze() {
        return (float) (1 - Ease.smoother((now - hazeStart) / 1.5));
    }

    public float flash() {
        if (flashStart < 0) return 0;
        double e = now - flashStart;
        if (e < 0.14) return (float) Ease.smooth(e / 0.14);
        if (e < 0.6) return (float) (1 - Ease.smooth((e - 0.14) / 0.46));
        flashStart = -1;
        return 0;
    }

    public float black() {
        if (blackStart < 0) return 0;
        return (float) Ease.smooth((now - blackStart) / 0.45);
    }

    // How present the title buttons should be: gone while flying, back shortly after the camera settles at the house.
    public float titleUi() {
        if (state != State.FRONT) return 0;
        return (float) Ease.clamp((now - stateStart - 0.15) / 0.6);
    }
}
