package com.example.charmenu.mixin;

import com.example.charmenu.client.Glass;
import com.example.charmenu.client.MenuHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    @Shadow
    protected int width;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void charmenu$seeThrough(GuiGraphics g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (!MenuHooks.active()) return;
        setRenderBackground(false);
        setRenderTopAndBottom(false);
        float cx = (x0 + x1) / 2f;
        float half = Math.min((x1 - x0) / 2f - 2, getRowWidth() / 2f + 16);
        Glass.panel(g, cx - half, y0 + 1, half * 2, y1 - y0 - 2, 10, 1f);
    }

    // the chosen row: a soft bright pill rather than a white outline round a black box
    @Inject(method = "renderSelection", at = @At("HEAD"), cancellable = true, require = 0)
    private void charmenu$selection(GuiGraphics g, int top, int rowWidth, int rowHeight, int outer, int inner, CallbackInfo ci) {
        if (!MenuHooks.active()) return;
        float left = x0 + (width - rowWidth) / 2f;
        Glass.pill(g, left, top - 2, rowWidth, rowHeight + 4, 0.7f, 0.8f, true);
        ci.cancel();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"), require = 0)
    private void charmenu$scrollBar(GuiGraphics g, int ax, int ay, int bx, int by, int colour) {
        if (!MenuHooks.active()) {
            g.fill(ax, ay, bx, by, colour);
            return;
        }
        // vanilla draws a black strip, then the handle twice in two greys: only the first grey becomes the pill
        if (colour == 0xFF808080) {
            float w = bx - ax - 1;
            Glass.draw(g, ax + 0.5f, ay, w, by - ay, w / 2,
                new float[] {1f, 0.86f, 0.93f, 0.7f}, new float[] {1f, 0.78f, 0.88f, 0.6f},
                new float[] {1f, 1f, 1f, 0.5f}, new float[] {0f, 0f, 0f, 0f}, 3f);
        }
    }
}
