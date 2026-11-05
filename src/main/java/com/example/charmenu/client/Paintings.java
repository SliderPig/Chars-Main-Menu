package com.example.charmenu.client;

import com.example.charmenu.CharMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.PaintingVariant;

import java.util.List;

// The real game paintings on the hall's walls: flat pictures in warm lamp light, a handful of quads a frame.
final class Paintings {
    // variant, x (the wall plane), y (bottom), z (one side), width along the wall, height, which way it faces (+1 towards +x, -1 towards -x)
    record Hung(String variant, double x, double y, double z, double w, double h, int facing) {}

    private Paintings() {}

    static void draw(List<Hung> hung) {
        if (hung.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.disableBlend();
        for (Hung h : hung) {
            PaintingVariant v = BuiltInRegistries.PAINTING_VARIANT.get(new ResourceLocation(h.variant()));
            if (v == null) {
                CharMenu.LOG.debug("No painting called {}", h.variant());
                continue;
            }
            TextureAtlasSprite sprite = mc.getPaintingTextures().get(v);
            RenderSystem.setShaderTexture(0, sprite.atlasLocation());
            BufferBuilder b = Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float x = (float) h.x(), y0 = (float) h.y(), y1 = (float) (h.y() + h.h());
            float z0 = (float) h.z(), z1 = (float) (h.z() + h.w());
            float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
            int r = 255, g = 214, bl = 170;
            if (h.facing() > 0) {
                b.vertex(x, y0, z1).uv(u0, v1).color(r, g, bl, 255).endVertex();
                b.vertex(x, y0, z0).uv(u1, v1).color(r, g, bl, 255).endVertex();
                b.vertex(x, y1, z0).uv(u1, v0).color(r, g, bl, 255).endVertex();
                b.vertex(x, y1, z1).uv(u0, v0).color(r, g, bl, 255).endVertex();
            } else {
                b.vertex(x, y0, z0).uv(u0, v1).color(r, g, bl, 255).endVertex();
                b.vertex(x, y0, z1).uv(u1, v1).color(r, g, bl, 255).endVertex();
                b.vertex(x, y1, z1).uv(u1, v0).color(r, g, bl, 255).endVertex();
                b.vertex(x, y1, z0).uv(u0, v0).color(r, g, bl, 255).endVertex();
            }
            BufferUploader.drawWithShader(b.end());
        }
    }
}
