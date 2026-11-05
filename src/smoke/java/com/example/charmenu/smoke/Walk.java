package com.example.charmenu.smoke;

import com.example.charmenu.CharMenu;
import com.example.charmenu.client.CharTitleScreen;
import com.example.charmenu.client.Director;
import com.example.charmenu.client.GlassButton;
import com.example.charmenu.client.SceneEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.client.gui.ModListScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

// Only in test builds (gradlew build -Psmoke). Walks through the whole menu by itself, saves screenshots to <game>/screenshots and quits.
// charmenu.walk=full: everything. charmenu.walk=look: only the views that show how the scene looks, faster to repeat while working on it.
// charmenu.walk=lab: stays open; each time <game>/lab/go appears, the scene is reloaded from charmenu.scenedir and the views are retaken.
@Mod.EventBusSubscriber(modid = CharMenu.MODID, value = Dist.CLIENT)
public final class Walk {
    private static final String MODE = System.getProperty("charmenu.walk", "");
    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static int wait;
    private static boolean started;
    private static long framesInWindow;
    private static long windowStart;
    private static long nextFps;
    private static int lastFps;
    private static boolean labArmed;

    private record Step(int ticks, Runnable what) {}

    private Walk() {}

    private static void later(int ticks, Runnable what) {
        STEPS.add(new Step(ticks, what));
    }

    private static void shot(int ticks, String name) {
        later(ticks, () -> {
            Minecraft mc = Minecraft.getInstance();
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {});
            CharMenu.LOG.info("[walk] shot {} screen={} fps={}", name, mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(), mc.getFps());
        });
    }

    private static Consumer<Double> freeze() {
        return p -> Director.get().debugProgress = p;
    }

    private static void views(String tag) {
        // hold the flight at exact spots so the pictures show precisely what they should
        shot(20, tag + "01_front");
        later(5, () -> {
            CharTitleScreen t = title();
            if (t != null && !t.pills().isEmpty()) t.pills().get(1).setFocused(true);
        });
        shot(25, tag + "02_hover");
        later(1, () -> {
            CharTitleScreen t = title();
            if (t != null && !t.pills().isEmpty()) t.pills().get(1).setFocused(false);
            Director.get().debugProgress = 0;
            Director.get().depart(() -> {});
        });
        double[] spots = {0.12, 0.22, 0.30, 0.38, 0.46, 0.54, 0.62, 0.70, 0.78, 0.86, 0.94};
        for (double s : spots) {
            later(3, () -> Director.get().debugProgress = s);
            shot(6, tag + "03_out_" + (int) (s * 100));
        }
        later(2, () -> Director.get().debugProgress = -1);
    }

    private static void arrival(String tag) {
        later(1, () -> Director.get().skip());
        shot(45, tag + "04_tree");
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (MODE.isEmpty() || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AccessibilityOnboardingScreen) {
            mc.setScreen(null);
            return;
        }
        long now = System.currentTimeMillis();
        if (now > nextFps) {
            nextFps = now + 2000;
            if (mc.level == null) CharMenu.LOG.info("[walk] fps={} state={} ready={} screen={}", mc.getFps(), Director.get().state(), SceneEngine.ready(), mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
        }
        if (!started) {
            if (mc.screen instanceof CharTitleScreen && mc.getOverlay() == null && SceneEngine.ready()) {
                started = true;
                wait = 60;
                script();
            }
            return;
        }
        if ("lab".equals(MODE) && STEPS.isEmpty() && wait <= 0) {
            Path go = mc.gameDirectory.toPath().resolve("lab/go");
            if (Files.exists(go)) {
                try {
                    Files.deleteIfExists(go);
                } catch (Exception ignored) {
                }
                labRound();
            }
            return;
        }
        if (--wait > 0) return;
        Step s = STEPS.poll();
        if (s == null) return;
        wait = s.ticks();
        try {
            s.what().run();
        } catch (Throwable t) {
            CharMenu.LOG.error("[walk] step failed", t);
        }
    }

    private static CharTitleScreen title() {
        return Minecraft.getInstance().screen instanceof CharTitleScreen t ? t : null;
    }

    private static void labRound() {
        later(1, () -> {
            SceneEngine.freeAll();
            Director.get().resetToFront();
            SceneEngine.ensure();
        });
        later(160, () -> {});
        views("lab_");
        arrival("lab_");
        later(1, () -> {
            Director.get().resetToFront();
            CharMenu.LOG.info("[walk] lab round done");
        });
    }

    private static void script() {
        Minecraft mc = Minecraft.getInstance();
        if ("lab".equals(MODE)) {
            labRound();
            return;
        }
        views("");
        arrival("");
        if ("look".equals(MODE)) {
            later(10, Walk::quit);
            return;
        }
        // real time flight with a real button press, timing frame rates
        later(60, () -> mc.setScreen(new SelectWorldScreen(mc.screen)));
        shot(40, "05_singleplayer");
        later(5, () -> net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(mc, new CharTitleScreen(false)));
        shot(50, "05_create_game");
        later(2, () -> pressTab(mc, 1));
        shot(20, "05_create_world");
        later(2, () -> pressTab(mc, 2));
        shot(20, "05_create_more");
        later(5, () -> mc.setScreen(new OptionsScreen(new CharTitleScreen(false), mc.options)));
        shot(30, "06_options");
        later(5, () -> mc.setScreen(new VideoSettingsScreen(mc.screen, mc.options)));
        shot(30, "07_video");
        later(5, () -> mc.setScreen(new ModListScreen(new CharTitleScreen(false))));
        shot(30, "08_mods");
        later(5, () -> {
            mc.options.skipMultiplayerWarning = true;
            mc.setScreen(new JoinMultiplayerScreen(new CharTitleScreen(false)));
        });
        shot(30, "09_multiplayer");
        // back to the title: the camera comes back round
        later(5, () -> mc.setScreen(new CharTitleScreen(false)));
        later(2, () -> Director.get().debugProgress = 0.12);
        double[] spots = {0.12, 0.3, 0.45, 0.6, 0.72, 0.84, 0.94};
        for (double s : spots) {
            later(3, () -> Director.get().debugProgress = s);
            shot(6, "10_back_" + (int) (s * 100));
        }
        later(2, () -> Director.get().debugProgress = -1);
        later(1, () -> Director.get().skip());
        shot(70, "11_front_again");
        // a real press of the first pill in real time, watching the frame rate
        later(40, () -> {
            CharMenu.LOG.info("[walk] real flight starts, fps={}", mc.getFps());
            CharTitleScreen t = title();
            if (t != null) t.pills().get(3).onPress();
        });
        shot(50, "12_real_flight");
        later(50, () -> CharMenu.LOG.info("[walk] mid flight fps={}", mc.getFps()));
        shot(60, "13_real_after");
        later(40, () -> {
            mc.setScreen(new CharTitleScreen(false));
        });
        later(120, () -> {
            Director.get().skip();
            CharMenu.LOG.info("[walk] making a world to check everything gets out of the way");
            LevelSettings settings = new LevelSettings("charmenu-check", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("charmenu-check", settings, new WorldOptions(1L, false, false), WorldPresets::createNormalWorldDimensions);
        });
        STEPS.add(new Step(5, Walk::waitForWorld));
    }

    private static void pressTab(Minecraft mc, int index) {
        if (mc.screen == null) return;
        for (var child : mc.screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.tabs.TabNavigationBar bar) {
                var tabs = bar.children();
                if (index < tabs.size()) bar.selectTab(index, false);
            }
        }
    }

    private static int worldWait;

    private static void waitForWorld() {
        Minecraft mc = Minecraft.getInstance();
        worldWait++;
        if (mc.level == null && worldWait < 240) {
            STEPS.addFirst(new Step(10, Walk::waitForWorld));
            return;
        }
        CharMenu.LOG.info("[walk] in world={} after {} checks, scene still held={}", mc.level != null, worldWait, SceneEngine.ready());
        later(60, () -> {
            if (mc.level != null) {
                CharMenu.LOG.info("[walk] leaving the world, scene ready={}", SceneEngine.ready());
                mc.level.disconnect();
                mc.clearLevel();
                mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
            }
        });
        shot(160, "14_back_from_world");
        later(5, Walk::quit);
    }

    private static void quit() {
        CharMenu.LOG.info("[walk] finished");
        Minecraft.getInstance().stop();
    }
}
