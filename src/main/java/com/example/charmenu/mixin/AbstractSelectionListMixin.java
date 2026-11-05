package com.example.charmenu.mixin;

import com.example.charmenu.client.Glass;
import com.example.charmenu.client.MenuHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Scrolling lists with no world loaded: no dirt panel and no dirt bars, just a quiet rounded piece of glass so the text stays readable.
@Mixin(AbstractSelectionList.class)
abstract class AbstractSelectionListMixin {
    @Shadow
    protected int x0;
    @Shadow
    protected int x1;
    @Shadow
    protected int y0;
    @Shadow
    protected int y1;

    @Shadow
    public abstract void setRenderBackground(boolean on);

    @Shadow
    public abstract void setRenderTopAndBottom(boolean on);

    @Shadow
    public abstract int getRowWidth();

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void charmenu$seeThrough(GuiGraphics g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (!MenuHooks.active()) return;
        setRenderBackground(false);
        setRenderTopAndBottom(false);
        float cx = (x0 + x1) / 2f;
        float half = Math.min((x1 - x0) / 2f - 2, getRowWidth() / 2f + 16);
        Glass.panel(g, cx - half, y0 + 1, half * 2, y1 - y0 - 2, 10, 1f);
    }
}
