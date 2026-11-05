package com.example.charmenu.mixin;

import com.example.charmenu.client.Glass;
import com.example.charmenu.client.MenuHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Create World's tabs as glass pills while the scene is showing. The open tab is the bright one.
@Mixin(TabButton.class)
abstract class TabButtonMixin extends AbstractWidget {
    @Shadow
    public abstract boolean isSelected();

    @Shadow
    public abstract void renderString(GuiGraphics g, net.minecraft.client.gui.Font font, int colour);

    private TabButtonMixin(int x, int y, int w, int h, Component message) {
        super(x, y, w, h, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
    private void charmenu$glass(GuiGraphics g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (!MenuHooks.active()) return;
        float glow = isSelected() ? 1f : isHoveredOrFocused() ? 0.55f : 0f;
        Glass.pill(g, getX() + 1, getY() + 2, getWidth() - 2, getHeight() - 4, glow, 1f, true);
        renderString(g, Minecraft.getInstance().font, isSelected() ? 0xFFFFFFFF : 0xFFF0DCE8);
        ci.cancel();
    }
}
