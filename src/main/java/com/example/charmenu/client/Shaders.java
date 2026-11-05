package com.example.charmenu.client;

import com.example.charmenu.CharMenu;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;

// The menu's own small shaders. If any of them fails to load the menu quietly steps aside and the vanilla one shows.
final class Shaders {
    static ShaderInstance scene, far, sky, figure, glass;
    static boolean ok;

    private Shaders() {}

    static void register(RegisterShadersEvent event) {
        ok = false;
        try {
            add(event, "scene", DefaultVertexFormat.BLOCK, s -> scene = s);
            add(event, "far", DefaultVertexFormat.POSITION_COLOR, s -> far = s);
            add(event, "sky", DefaultVertexFormat.POSITION, s -> sky = s);
            add(event, "figure", DefaultVertexFormat.NEW_ENTITY, s -> figure = s);
            add(event, "glass", DefaultVertexFormat.POSITION_TEX, s -> glass = s);
            ok = true;
        } catch (Throwable t) {
            CharMenu.LOG.warn("Menu shaders failed to load, using the vanilla menu: {}", t.toString());
        }
    }

    private static void add(RegisterShadersEvent event, String name, VertexFormat format, java.util.function.Consumer<ShaderInstance> done) throws java.io.IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(CharMenu.MODID, name), format), done);
    }
}
