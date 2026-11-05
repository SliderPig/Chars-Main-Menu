package com.example.charmenu.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

// The little settings page behind the cat button (and the Config button in the Mods list).
public final class MenuSettingsScreen extends Screen {
    private final Screen parent;
    private final List<Runnable> refreshers = new ArrayList<>();

    public MenuSettingsScreen(Screen parent) {
        super(Component.translatable("charmenu.settings.title"));
        this.parent = parent;
    }

    private void toggle(int y, String key, BooleanSupplier value, Consumer<Boolean> set) {
        GlassButton[] holder = new GlassButton[1];
        holder[0] = new GlassButton(width / 2 - 110, y, 220, 20, label(key, value.getAsBoolean()), () -> {
            set.accept(!value.getAsBoolean());
            MenuConfig.save();
            refreshers.forEach(Runnable::run);
        });
        addRenderableWidget(holder[0]);
        refreshers.add(() -> holder[0].setMessage(label(key, value.getAsBoolean())));
    }

    private static Component label(String key, boolean on) {
        Component state = key.endsWith("quality")
            ? Component.translatable(on ? "charmenu.settings.high" : "charmenu.settings.low")
            : (on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
        return Component.translatable(key).append(": ").append(state);
    }

    @Override
    protected void init() {
        refreshers.clear();
        int y = height / 2 - 66;
        toggle(y, "charmenu.settings.scene", () -> MenuConfig.enabled, v -> MenuConfig.enabled = v);
        toggle(y += 25, "charmenu.settings.animation", () -> MenuConfig.animation, v -> MenuConfig.animation = v);
        toggle(y += 25, "charmenu.settings.quality", () -> MenuConfig.highQuality, v -> {
            MenuConfig.highQuality = v;
            // low quality leaves out some of the plants and leaves, so the scene gets built again
            SceneEngine.freeAll();
        });
        toggle(y += 25, "charmenu.settings.ambience", () -> MenuConfig.ambience, v -> MenuConfig.ambience = v);
        toggle(y += 25, "charmenu.settings.music", () -> MenuConfig.music, v -> MenuConfig.music = v);
        addRenderableWidget(new GlassButton(width / 2 - 60, y + 36, 120, 20, CommonComponents.GUI_DONE, this::onClose));
        for (var c : children()) {
            if (c instanceof GlassButton b) b.setAlpha(1);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        if (MenuHooks.active()) Glass.panel(g, width / 2f - 124, height / 2f - 92, 248, 188, 12, 1f);
        g.drawCenteredString(font, title, width / 2, height / 2 - 83, 0xFFF8EAF2);
        Component note = Component.translatable("charmenu.settings.note").withStyle(ChatFormatting.GRAY);
        g.drawCenteredString(font, note, width / 2, height / 2 + 64 - 2, 0xFFE8D8E2);
        super.render(g, mouseX, mouseY, partial);
    }
}
