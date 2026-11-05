package com.example.charmenu.client;

import com.example.charmenu.model.Ease;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// One of the see-through pill buttons. Slides a little and glows softly when the mouse is on it.
public final class GlassButton extends AbstractButton {
    private final Runnable press;
    private AbstractWidget mirror;   // stays greyed out when the real button it stands for is greyed out
    private float hover;
    private ResourceLocation icon;
    private int iconU, iconV, iconW, iconH, texW, texH;
    private boolean leftText;

    public GlassButton(int x, int y, int w, int h, Component label, Runnable press) {
        super(x, y, w, h, label);
        this.press = press;
    }

    public GlassButton mirrors(AbstractWidget real) {
        this.mirror = real;
        return this;
    }

    public GlassButton leftText() {
        this.leftText = true;
        return this;
    }

    public GlassButton icon(ResourceLocation texture, int u, int v, int w, int h, int textureW, int textureH) {
        this.icon = texture;
        this.iconU = u;
        this.iconV = v;
        this.iconW = w;
        this.iconH = h;
        this.texW = textureW;
        this.texH = textureH;
        return this;
    }

    @Override
    public void onPress() {
        press.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (mirror != null) active = mirror.active;
        double dt = Director.get().frameSeconds();
        hover = (float) Ease.toward(hover, active && isHoveredOrFocused() ? 1 : 0, dt, 11);
        float a = alpha;
        if (a <= 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        float scale = (float) mc.getWindow().getGuiScale();
        float slide = Math.round(hover * 3f * scale) / scale;
        Glass.pill(g, getX() + slide, getY(), width, height, hover, a, active);

        g.pose().pushPose();
        g.pose().translate(slide, 0, 0);
        if (icon != null) {
            RenderSystem.enableBlend();
            g.setColor(1, 1, 1, a);
            g.blit(icon, getX() + (width - iconW) / 2, getY() + (height - iconH) / 2, iconU, iconV, iconW, iconH, texW, texH);
            g.setColor(1, 1, 1, 1);
        } else {
            int colour = active ? 0xFFF8EAF2 : 0xFFB9A8B2;
            int shown = (int) (a * 255) << 24 | colour & 0xFFFFFF;
            int tw = mc.font.width(getMessage());
            int tx = leftText ? getX() + 12 : getX() + (width - tw) / 2;
            if ((shown >>> 24) > 4) g.drawString(mc.font, getMessage(), tx, getY() + (height - 8) / 2, shown, true);
        }
        g.pose().popPose();
    }

    @Override
    public void playDownSound(SoundManager sounds) {
        MenuSounds.tap();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
