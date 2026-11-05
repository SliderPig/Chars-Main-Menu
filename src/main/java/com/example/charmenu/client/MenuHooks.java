package com.example.charmenu.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

// What the small patches into vanilla screens call. Every one of these answers "no, carry on as normal" straight away while a world is loaded.
public final class MenuHooks {
    private static long frame;
    private static long drawnOn = -1;

    private MenuHooks() {}

    public static void newFrame() {
        frame++;
    }

    // True when the menu's look is in charge: no world loaded and nothing has gone wrong.
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null && MenuConfig.enabled && !SceneEngine.failed();
    }

    // A screen wants its dirt background: give it the scene instead. Returns false to let vanilla draw its dirt.
    public static boolean background(GuiGraphics g, Screen screen) {
        if (!active()) return false;
        // saving or loading a world: don't start building a whole scene for a screen that's about to vanish
        if (!SceneEngine.ready() && isPassing(screen)) return false;
        if (drawnOn == frame) return true;
        drawnOn = frame;
        if (!(screen instanceof TitleScreen) && SceneEngine.ready()) Director.get().showTree();
        return drawScene(g, 0.06f);
    }

    public static boolean drawScene(GuiGraphics g, float darken) {
        try {
            g.flush();
            SceneEngine.ensure();
            SceneEngine.draw();
            if (SceneEngine.failed()) return false;
            MenuSounds.frame();
            Director dir = Director.get();
            int w = g.guiWidth(), h = g.guiHeight();
            if (darken > 0) {
                // a very soft darkening from the top and bottom edges only, the middle stays clear
                int edge = (int) (darken * 255 * 2.2f) << 24;
                g.fillGradient(0, 0, w, h / 5, edge | 0x180C18, 0x00180C18);
                g.fillGradient(0, h - h / 5, w, h, 0x00180C18, edge | 0x180C18);
            }
            overlays(g, w, h, dir);
            return true;
        } catch (Throwable t) {
            SceneEngine.fail(t);
            return false;
        }
    }

    private static void overlays(GuiGraphics g, int w, int h, Director dir) {
        float haze = dir.haze();
        if (!SceneEngine.ready()) haze = 0;
        if (haze > 0.003f) g.fill(0, 0, w, h, (int) (haze * 255) << 24 | 0xBBD7F0);
        float flash = dir.flash();
        if (flash > 0.003f) g.fill(0, 0, w, h, (int) (flash * 255) << 24 | 0xFFE9F1);
        float black = dir.black();
        if (black > 0.003f) g.fill(0, 0, w, h, (int) (black * 255) << 24 | 0x0B060B);
    }

    private static boolean isPassing(Screen s) {
        return s instanceof GenericDirtMessageScreen || s instanceof ProgressScreen || s instanceof LevelLoadingScreen || s instanceof ConnectScreen || s instanceof ReceivingLevelScreen;
    }
}
