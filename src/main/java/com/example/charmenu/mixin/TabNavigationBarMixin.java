package com.example.charmenu.mixin;

import com.example.charmenu.client.MenuHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// The tab bar on the Create World screen paints a solid black band: with the scene showing, it doesn't.
@Mixin(TabNavigationBar.class)
abstract class TabNavigationBarMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"), require = 0)
    private void charmenu$noBand(GuiGraphics g, int x0, int y0, int x1, int y1, int colour) {
        if (!MenuHooks.active()) g.fill(x0, y0, x1, y1, colour);
    }
}
