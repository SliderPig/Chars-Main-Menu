package com.example.charmenu.mixin;

import com.example.charmenu.client.Glass;
import com.example.charmenu.client.MenuHooks;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.widget.ScrollPanel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Forge's scrolling info panel (the right side of the Mods list): glass instead of dirt.
@Mixin(value = ScrollPanel.class, remap = false)
abstract class ScrollPanelMixin {
    @Shadow
    @Final
    protected int width;
    @Shadow
    @Final
    protected int height;
    @Shadow
    @Final
    protected int top;
    @Shadow
    @Final
    protected int left;

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void charmenu$glass(GuiGraphics g, Tesselator tess, float partial, CallbackInfo ci) {
        if (!MenuHooks.active()) return;
        Glass.panel(g, left + 1, top + 1, width - 2, height - 2, 10, 1f);
        ci.cancel();
    }
}
