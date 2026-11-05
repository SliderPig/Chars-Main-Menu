package com.example.charmenu.client;

import com.example.charmenu.CharMenu;
import com.example.charmenu.model.Ease;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.gui.TitleScreenModUpdateIndicator;
import net.minecraftforge.internal.BrandingControl;

import java.util.ArrayList;
import java.util.List;

// The title screen. It really is vanilla's title screen underneath, so every button vanilla, Forge and other mods add is still there:
// the five main ones get a glass pill that runs them after the flight, the rest stay reachable.
public final class CharTitleScreen extends TitleScreen {
    private static final ResourceLocation CAT = new ResourceLocation(CharMenu.MODID, "textures/menu/cat.png");
    private static final int PILL_W = 122, PILL_H = 20, GAP = 5;

    private final LogoRenderer logo = new LogoRenderer(false);
    private SplashRenderer splash;
    private final List<GlassButton> pills = new ArrayList<>();
    private final List<AbstractWidget> others = new ArrayList<>();
    private TitleScreenModUpdateIndicator updateNotice;
    private AbstractWidget copyright;
    private boolean adopted;
    private int pillsTop;
    private float shown;

    public CharTitleScreen(boolean fading) {
        super(fading);
    }

    @Override
    protected void init() {
        adopted = false;
        super.init();
        if (splash == null) splash = minecraft.getSplashManager().getSplash();
        if (Director.get().state() == Director.State.TREE) Director.get().comeBack();
    }

    private static String keyOf(AbstractWidget w) {
        return w.getMessage().getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    // Runs after vanilla, Forge and every other mod have added their buttons.
    void adopt() {
        if (adopted) return;
        adopted = true;
        pills.clear();
        others.clear();
        copyright = null;
        AbstractWidget single = null, multi = null, mods = null, options = null, quit = null, language = null, access = null;
        List<AbstractWidget> main = new ArrayList<>();
        for (GuiEventListener l : List.copyOf(children())) {
            if (!(l instanceof AbstractWidget w)) continue;
            String key = keyOf(w);
            switch (key) {
                case "menu.singleplayer" -> single = w;
                case "menu.multiplayer" -> multi = w;
                case "fml.menu.mods" -> mods = w;
                case "menu.options" -> options = w;
                case "menu.quit" -> quit = w;
                case "narrator.button.language" -> language = w;
                case "narrator.button.accessibility" -> access = w;
                case "menu.online" -> {
                    // Realms stays out of the way
                    w.visible = false;
                    w.active = false;
                }
                case "menu.playdemo", "menu.resetdemo" -> main.add(w);
                default -> {
                    if (w instanceof PlainTextButton) copyright = w;
                    else others.add(w);
                }
            }
        }
        List<AbstractWidget> ordered = new ArrayList<>();
        if (single != null) ordered.add(single);
        ordered.addAll(main);
        if (multi != null) ordered.add(multi);
        if (mods != null) ordered.add(mods);
        if (options != null) ordered.add(options);
        if (quit != null) ordered.add(quit);

        int x = Math.max(14, width / 16);
        int total = ordered.size() * (PILL_H + GAP) - GAP;
        pillsTop = Math.max(58, height / 2 - total / 2 + 8);
        int y = pillsTop;
        for (AbstractWidget real : ordered) {
            boolean isQuit = real == quit;
            final AbstractWidget target = real;
            GlassButton pill = new GlassButton(x, y, PILL_W, PILL_H, real.getMessage(), () -> pressed(target, isQuit)).mirrors(real);
            real.visible = false;
            // the real button stands where the pill is, so Forge's update notice lands in the right place
            real.setX(x);
            real.setY(y);
            real.setWidth(PILL_W);
            pills.add(addRenderableWidget(pill));
            y += PILL_H + GAP;
        }

        // the small round ones under the stack: language, accessibility, and the menu's own settings
        int sx = x, sy = y + 2, size = 20;
        if (language != null) {
            AbstractWidget target = language;
            language.visible = false;
            pills.add(addRenderableWidget(new GlassButton(sx, sy, size, size, language.getMessage(), () -> pressed(target, false)).icon(Button.WIDGETS_LOCATION, 3, 109, 14, 14, 256, 256)));
            sx += size + GAP;
        }
        if (access != null) {
            AbstractWidget target = access;
            access.visible = false;
            pills.add(addRenderableWidget(new GlassButton(sx, sy, size, size, access.getMessage(), () -> pressed(target, false)).icon(Button.ACCESSIBILITY_TEXTURE, 3, 3, 14, 14, 32, 64)));
            sx += size + GAP;
        }
        pills.add(addRenderableWidget(new GlassButton(sx, sy, size, size, Component.translatable("charmenu.settings.open"), () -> flyThen(() -> minecraft.setScreen(new MenuSettingsScreen(this)))).icon(CAT, 0, 0, 14, 14, 14, 14)));

        // buttons from other mods stack up in the bottom right corner, whatever they are
        int ox = width - 6, oy = height - 18;
        for (AbstractWidget w : others) {
            w.setX(ox - w.getWidth());
            w.setY(oy - w.getHeight());
            oy -= w.getHeight() + 3;
        }
        updateNotice = mods instanceof Button b ? TitleScreenModUpdateIndicator.init(this, b) : null;
    }

    private void pressed(AbstractWidget real, boolean isQuit) {
        Director dir = Director.get();
        if (dir.flying() || dir.state() != Director.State.FRONT) return;
        if (isQuit) {
            dir.fadeToBlack();
            final AbstractWidget target = real;
            new Thread(() -> {
                try {
                    Thread.sleep(460);
                } catch (InterruptedException ignored) {
                }
                minecraft.tell(() -> ((AbstractButton) target).onPress());
            }, "charmenu-quit").start();
            return;
        }
        // real.active can be false while pills are grey, and those never reach here
        dir.depart(() -> ((AbstractButton) real).onPress());
    }

    private void flyThen(Runnable action) {
        Director dir = Director.get();
        if (dir.state() == Director.State.FRONT) dir.depart(action);
    }

    // Any click while the camera is flying gets you there straight away.
    @Override
    public boolean mouseClicked(double x, double y, int button) {
        Director dir = Director.get();
        if (dir.flying()) {
            dir.skip();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (SceneEngine.failed() || !MenuConfig.enabled) {
            // something went wrong or it was turned off: plain vanilla from here on
            minecraft.tell(() -> minecraft.setScreen(new TitleScreen(false)));
            super.render(g, mouseX, mouseY, partial);
            return;
        }
        if (!MenuHooks.drawScene(g, 0)) {
            minecraft.tell(() -> minecraft.setScreen(new TitleScreen(false)));
            return;
        }
        Director dir = Director.get();
        // the buttons fade out quickly when a flight starts and come back gently after one ends
        float target = SceneEngine.ready() ? dir.titleUi() : 0;
        shown = (float) Ease.toward(shown, target, dir.frameSeconds(), target > shown ? 5 : 11);
        if (shown < 0.004f) shown = 0;
        float ui = shown;
        for (int i = 0; i < pills.size(); i++) {
            float own = (float) Math.max(0, Math.min(1, ui * 1.6f - i * 0.06f));
            pills.get(i).setAlpha(own * own * (3 - 2 * own));
        }
        if (ui > 0.01f) {
            drawLogo(g, ui);
            drawBranding(g, ui);
        }
        for (AbstractWidget w : others) {
            w.setAlpha(ui);
            w.visible = ui > 0.005f;
        }
        if (copyright != null) {
            copyright.setAlpha(ui);
            copyright.visible = ui > 0.005f;
        }
        for (GlassButton p : pills) p.visible = ui > 0.005f;
        for (Renderable r : renderables) r.render(g, mouseX, mouseY, partial);
        if (updateNotice != null && ui > 0.6f) updateNotice.render(g, mouseX, mouseY, partial);
    }

    private void drawLogo(GuiGraphics g, float ui) {
        float scale = 0.5f;
        int cx = Math.max(14, width / 16) + PILL_W / 2;
        int top = pillsTop - 52;
        RenderSystem.enableBlend();
        g.pose().pushPose();
        g.pose().translate(cx - width * scale / 2f, top - 30 * scale, 0);
        g.pose().scale(scale, scale, 1);
        logo.renderLogo(g, width, ui);
        if (splash != null) {
            splash.render(g, width, font, (int) (ui * 255) << 24);
        }
        g.pose().popPose();
    }

    private void drawBranding(GuiGraphics g, float ui) {
        int alpha = (int) (ui * 255) << 24;
        if (alpha >>> 24 < 5) return;
        ForgeHooksClient.renderMainMenu(this, g, font, width, height, alpha);
        BrandingControl.forEachLine(true, true, (line, text) -> g.drawString(font, text, 2, height - (10 + line * (font.lineHeight + 1)), 0xFFFFFF | alpha));
        BrandingControl.forEachAboveCopyrightLine((line, text) -> g.drawString(font, text, width - font.width(text), height - (10 + (line + 1) * (font.lineHeight + 1)), 0xFFFFFF | alpha));
    }

    // used by the walk-through test
    public List<GlassButton> pills() {
        return pills;
    }
}
