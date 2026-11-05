package com.example.charmenu.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

// Rounded see-through glass shapes, drawn by a small shader so the corners are perfectly smooth at any size.
public final class Glass {
    private Glass() {}

    // A button: almost see-through, a thin bright rim, a soft pink glow when the mouse is on it.
    public static void pill(GuiGraphics g, float x, float y, float w, float h, float hover, float alpha, boolean enabled) {
        float round = h / 2;
        float lift = hover * 0.10f;
        float dim = enabled ? 1f : 0.45f;
        draw(g, x, y, w, h, round,
            new float[] {0.20f + lift, 0.13f + lift, 0.20f + lift, (0.20f + hover * 0.10f) * alpha * dim},
            new float[] {0.10f + lift, 0.06f + lift, 0.11f + lift, (0.27f + hover * 0.10f) * alpha * dim},
            new float[] {1f, 0.93f, 0.97f, (0.44f + hover * 0.40f) * alpha * dim},
            new float[] {1f, 0.62f, 0.80f, hover * 0.34f * alpha},
            7f);
    }

    // A soft panel behind lists: a darker, quieter piece of glass so the text on it stays easy to read.
    public static void panel(GuiGraphics g, float x, float y, float w, float h, float round, float alpha) {
        draw(g, x, y, w, h, round,
            new float[] {0.10f, 0.05f, 0.11f, 0.22f * alpha},
            new float[] {0.08f, 0.04f, 0.09f, 0.30f * alpha},
            new float[] {1f, 0.93f, 0.97f, 0.18f * alpha},
            new float[] {0f, 0f, 0f, 0.08f * alpha},
            12f);
    }

    // A text box: a little darker so typing stays easy to read, brighter rim when it has the cursor.
    public static void field(GuiGraphics g, float x, float y, float w, float h, boolean focused) {
        draw(g, x, y, w, h, Math.min(5, h / 2),
            new float[] {0.07f, 0.03f, 0.08f, 0.50f},
            new float[] {0.07f, 0.03f, 0.08f, 0.42f},
            new float[] {1f, 0.86f, 0.93f, focused ? 0.95f : 0.45f},
            new float[] {1f, 0.6f, 0.8f, focused ? 0.28f : 0f},
            6f);
    }

    public static void draw(GuiGraphics g, float x, float y, float w, float h, float round, float[] fillTop, float[] fillBottom, float[] rim, float[] glow, float glowSizePx) {
        ShaderInstance s = Shaders.glass;
        if (s == null) return;
        g.flush();
        float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        // keep whole real pixels so edges stay crisp
        float px = Math.round(x * scale) / scale, py = Math.round(y * scale) / scale;
        float halfW = w * scale / 2f, halfH = h * scale / 2f;
        float margin = (glowSizePx * 3f + 2f) / scale;
        s.getUniform("Half").set(halfW, halfH);
        s.getUniform("Radius").set(Math.min(round * scale, Math.min(halfW, halfH)));
        s.getUniform("FillTop").set(fillTop[0], fillTop[1], fillTop[2], fillTop[3]);
        s.getUniform("FillBottom").set(fillBottom[0], fillBottom[1], fillBottom[2], fillBottom[3]);
        s.getUniform("Rim").set(rim[0], rim[1], rim[2], rim[3]);
        s.getUniform("Glow").set(glow[0], glow[1], glow[2], glow[3]);
        s.getUniform("GlowSize").set(glowSizePx);
        RenderSystem.setShader(() -> s);
        // some screens leave the blending switched round; set it plainly every time so glass always looks the same
        RenderSystem.enableBlend();
        GL11.glEnable(GL11.GL_BLEND);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float x0 = px - margin, y0 = py - margin, x1 = px + w + margin, y1 = py + h + margin;
        float m2 = margin * scale;
        b.vertex(m, x0, y1, 0).uv(-halfW - m2, halfH + m2).endVertex();
        b.vertex(m, x1, y1, 0).uv(halfW + m2, halfH + m2).endVertex();
        b.vertex(m, x1, y0, 0).uv(halfW + m2, -halfH - m2).endVertex();
        b.vertex(m, x0, y0, 0).uv(-halfW - m2, -halfH - m2).endVertex();
        BufferUploader.drawWithShader(b.end());
        RenderSystem.defaultBlendFunc();
    }
}
