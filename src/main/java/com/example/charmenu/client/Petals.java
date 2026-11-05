package com.example.charmenu.client;

import com.example.charmenu.CharMenu;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Random;

// Falling petals and the soft glow round the lanterns. Each petal's position comes straight from the clock (nothing is stored per frame), so there's nothing to simulate.
// One cloud always surrounds the camera, so the air is full of petals everywhere on the flight, even indoors. A second one falls from the big tree's crown.
final class Petals {
    private static final ResourceLocation PETAL = new ResourceLocation(CharMenu.MODID, "textures/menu/petal.png");
    private static final ResourceLocation GLOW = new ResourceLocation(CharMenu.MODID, "textures/menu/glow.png");

    private static final int NEAR = 420, TREE = 340;
    private static final float[] seeds = new float[(NEAR + TREE) * 7];

    static {
        Random r = new Random(4);
        for (int i = 0; i < seeds.length; i++) seeds[i] = r.nextFloat();
    }

    private Petals() {}

    private static float wrap(float v, float size) {
        v %= size;
        return v < 0 ? v + size : v;
    }

    static void draw(Director.Cam cam, float time, boolean high) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, PETAL);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int near = high ? NEAR : NEAR / 3;
        int tree = high ? TREE : TREE / 3;
        float cx = (float) cam.x, cy = (float) cam.y, cz = (float) cam.z;
        // the whole cloud leans with a slow breeze, so everything sways together
        float gust = (float) Math.sin(time * 0.31) * 1.3f + (float) Math.sin(time * 0.13 + 1.0) * 1.5f;
        for (int i = 0; i < near; i++) {
            float w = 34, h = 18;
            int s = i * 7;
            float swirlAt = time * (0.5f + seeds[s + 3] * 0.5f) + seeds[s + 4] * 40f;
            float swirl = 0.55f + seeds[s + 5] * 1.1f;
            float x = wrap(seeds[s] * w + time * (0.65f + seeds[s + 3] * 0.4f) + gust + (float) Math.sin(swirlAt) * swirl - cx, w) - w / 2;
            float y = wrap(seeds[s + 1] * h - time * (0.42f + seeds[s + 5] * 0.42f) + (float) Math.sin(swirlAt * 0.7f) * 0.25f - cy, h) - h / 2;
            float z = wrap(seeds[s + 2] * w - time * 0.3f + gust * 0.4f + (float) Math.cos(swirlAt) * swirl - cz, w) - w / 2;
            float edge = Math.max(Math.abs(x) / (w / 2), Math.max(Math.abs(y) / (h / 2), Math.abs(z) / (w / 2)));
            float fade = 1 - edge * edge * edge * edge;
            // right against the camera they would flash past as big blurs
            float close = x * x + y * y + z * z;
            if (close < 1.2f) fade *= close / 1.2f;
            petal(b, cx + x, cy + y, cz + z, s, time, fade);
        }
        for (int i = 0; i < tree; i++) {
            float w = 92, h = 33, depth = 40;
            int s = (near + i) * 7 % (seeds.length - 7);
            float swirlAt = time * (0.4f + seeds[s + 3] * 0.5f) + seeds[s + 4] * 40f;
            float swirl = 0.7f + seeds[s + 5] * 1.4f;
            float x = wrap(seeds[s] * w + time * (0.55f + seeds[s + 3] * 0.4f) + gust + (float) Math.sin(swirlAt) * swirl, w) - 40;
            float y = wrap(seeds[s + 1] * h - time * (0.45f + seeds[s + 5] * 0.5f), h) + 1;
            float z = wrap(seeds[s + 2] * depth - time * 0.28f + (float) Math.cos(swirlAt) * swirl, depth) - 84;
            float fade = Math.min(1, Math.min(y, 34 - y) / 3f);
            petal(b, x, y, z, s, time, fade);
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    private static void petal(BufferBuilder b, float x, float y, float z, int s, float time, float fade) {
        if (fade <= 0.01f) return;
        float size = 0.09f + seeds[s + 4] * 0.1f;
        float a1 = time * (0.7f + seeds[s + 5]) + seeds[s] * 20f;
        float a2 = time * (0.4f + seeds[s + 6] * 0.6f) + seeds[s + 1] * 20f;
        float ca = (float) Math.cos(a1), sa = (float) Math.sin(a1), cb = (float) Math.cos(a2), sb = (float) Math.sin(a2);
        float ux = ca * size, uy = sa * cb * size, uz = sa * sb * size;
        float vx = -sb * size * 0.8f, vy = 0, vz = cb * size * 0.8f;
        int tone = (int) (seeds[s + 2] * 3);
        int r = 255, g = tone == 0 ? 160 : tone == 1 ? 182 : 205, bl = tone == 0 ? 186 : tone == 1 ? 204 : 222;
        int alpha = (int) (240 * fade);
        b.vertex(x - ux - vx, y - uy - vy, z - uz - vz).uv(0, 1).color(r, g, bl, alpha).endVertex();
        b.vertex(x + ux - vx, y + uy - vy, z + uz - vz).uv(1, 1).color(r, g, bl, alpha).endVertex();
        b.vertex(x + ux + vx, y + uy + vy, z + uz + vz).uv(1, 0).color(r, g, bl, alpha).endVertex();
        b.vertex(x - ux + vx, y - uy + vy, z - uz + vz).uv(0, 0).color(r, g, bl, alpha).endVertex();
    }

    // Soft warm halos on the lanterns: a flat picture facing the camera, added on top of whatever's behind it.
    static void lanternGlow(List<float[]> lamps, Director.Cam cam, float rightX, float rightY, float rightZ, float upX, float upY, float upZ, float time) {
        if (lamps.isEmpty()) return;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, GLOW);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < lamps.size(); i++) {
            float[] l = lamps.get(i);
            float dx = l[0] - (float) cam.x, dy = l[1] - (float) cam.y, dz = l[2] - (float) cam.z;
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 90) continue;
            float flick = 0.88f + 0.12f * (float) Math.sin(time * 2.1f + i * 1.7f) * (float) Math.sin(time * 3.3f + i);
            float size = 0.95f * flick;
            int alpha = (int) (95 * flick * Math.min(1f, (90 - dist) / 30f));
            float rx = rightX * size, ry = rightY * size, rz = rightZ * size, ux = upX * size, uy = upY * size, uz = upZ * size;
            b.vertex(l[0] - rx - ux, l[1] - ry - uy, l[2] - rz - uz).uv(0, 1).color(255, 196, 120, alpha).endVertex();
            b.vertex(l[0] + rx - ux, l[1] + ry - uy, l[2] + rz - uz).uv(1, 1).color(255, 196, 120, alpha).endVertex();
            b.vertex(l[0] + rx + ux, l[1] + ry + uy, l[2] + rz + uz).uv(1, 0).color(255, 196, 120, alpha).endVertex();
            b.vertex(l[0] - rx + ux, l[1] - ry + uy, l[2] - rz + uz).uv(0, 0).color(255, 196, 120, alpha).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
    }
}
