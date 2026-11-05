package com.example.charmenu.mixin;

import com.example.charmenu.client.MenuHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The Create World screen has its own lighter dirt: the scene goes behind that one too.
@Mixin(CreateWorldScreen.class)
abstract class CreateWorldScreenMixin {
    @Inject(method = "renderDirtBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void charmenu$scene(GuiGraphics g, CallbackInfo ci) {
        if (MenuHooks.background(g, (Screen) (Object) this)) ci.cancel();
    }
}
