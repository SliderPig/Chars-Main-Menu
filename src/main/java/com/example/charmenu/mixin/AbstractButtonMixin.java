package com.example.charmenu.mixin;

import com.example.charmenu.client.Director;
import com.example.charmenu.client.Glass;
import com.example.charmenu.client.MenuHooks;
import com.example.charmenu.model.Ease;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// While the scene is showing, ordinary menu buttons are glass pills too. Buttons with their own drawing (icons and such) don't come through here.
@Mixin(AbstractButton.class)
abstract class AbstractButtonMixin extends AbstractWidget {
    @Shadow
    public abstract void renderString(GuiGraphics g, net.minecraft.client.gui.Font font, int colour);

    @Unique
    private float charmenu$hover;

    private AbstractButtonMixin(int x, int y, int w, int h, Component message) {
        super(x, y, w, h, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
    private void charmenu$glass(GuiGraphics g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (!MenuHooks.active()) return;
        charmenu$hover = (float) Ease.toward(charmenu$hover, active && isHoveredOrFocused() ? 1 : 0, Director.get().frameSeconds(), 11);
        Glass.pill(g, getX(), getY(), getWidth(), getHeight(), charmenu$hover, alpha, active);
        int colour = (active ? 0xF8EAF2 : 0xB9A8B2) | (int) (alpha * 255) << 24;
        renderString(g, Minecraft.getInstance().font, colour);
        ci.cancel();
    }
}
