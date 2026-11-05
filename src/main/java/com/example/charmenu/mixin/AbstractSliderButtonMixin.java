package com.example.charmenu.mixin;

import com.example.charmenu.client.Glass;
import com.example.charmenu.client.MenuHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Option sliders as a glass pill with a small round handle while the scene is showing.
@Mixin(AbstractSliderButton.class)
abstract class AbstractSliderButtonMixin extends AbstractWidget {
    @Shadow
    protected double value;

    private AbstractSliderButtonMixin(int x, int y, int w, int h, Component message) {
        super(x, y, w, h, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
    private void charmenu$glass(GuiGraphics g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (!MenuHooks.active()) return;
        boolean hot = isHoveredOrFocused();
        Glass.pill(g, getX(), getY(), getWidth(), getHeight(), hot ? 0.6f : 0, alpha, active);
        float knob = getHeight() - 6;
        float x = (float) (getX() + 3 + value * (getWidth() - 6 - knob));
        Glass.draw(g, x, getY() + 3, knob, knob, knob / 2,
            new float[] {1f, 0.9f, 0.95f, 0.75f * alpha}, new float[] {1f, 0.78f, 0.88f, 0.65f * alpha},
            new float[] {1f, 1f, 1f, 0.9f * alpha}, new float[] {1f, 0.6f, 0.8f, 0.3f * alpha}, 4f);
        int colour = (active ? 0xF8EAF2 : 0xB9A8B2) | (int) (alpha * 255) << 24;
        renderScrollingString(g, Minecraft.getInstance().font, 2, colour);
        ci.cancel();
    }
}
