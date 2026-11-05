package com.example.charmenu.mixin;

import com.example.charmenu.client.Glass;
import com.example.charmenu.client.MenuHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Text boxes are a dark rounded piece of glass instead of a black box while the scene is showing. The two fills are the border and then the black inside.
@Mixin(EditBox.class)
abstract class EditBoxMixin {
    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0), require = 0)
    private void charmenu$border(GuiGraphics g, int x0, int y0, int x1, int y1, int colour) {
        if (!MenuHooks.active()) {
            g.fill(x0, y0, x1, y1, colour);
            return;
        }
        boolean focused = colour == -1;
        Glass.field(g, x0, y0, x1 - x0, y1 - y0, focused);
    }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 1), require = 0)
    private void charmenu$inside(GuiGraphics g, int x0, int y0, int x1, int y1, int colour) {
        if (!MenuHooks.active()) g.fill(x0, y0, x1, y1, colour);
    }
}
