package com.example.charmenu.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

// A block of vertices on the graphics card, drawn as quads with the game's shared index buffer.
final class GpuMesh {
    private int vao, vbo;
    private final int indexCount;
    float minX, minY, minZ, maxX, maxY, maxZ;

    GpuMesh(int[] data, int ints, int intsPerVertex, VertexFormat format) {
        int vertices = ints / intsPerVertex;
        indexCount = vertices / 4 * 6;
        vao = GlStateManager._glGenVertexArrays();
        vbo = GlStateManager._glGenBuffers();
        GlStateManager._glBindVertexArray(vao);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        ByteBuffer buffer = MemoryUtil.memAlloc(ints * 4);
        try {
            buffer.asIntBuffer().put(data, 0, ints);
            buffer.limit(ints * 4);
            GlStateManager._glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(buffer);
        }
        format.setupBufferState();
        GlStateManager._glBindVertexArray(0);
    }

    GpuMesh bounds(float x0, float y0, float z0, float x1, float y1, float z1) {
        minX = x0;
        minY = y0;
        minZ = z0;
        maxX = x1;
        maxY = y1;
        maxZ = z1;
        return this;
    }

    void draw() {
        if (vao == 0 || indexCount == 0) return;
        GlStateManager._glBindVertexArray(vao);
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        indices.bind(indexCount);
        GlStateManager._drawElements(GL11.GL_TRIANGLES, indexCount, indices.type().asGLType, 0L);
    }

    void free() {
        if (vao != 0) {
            GlStateManager._glDeleteVertexArrays(vao);
            GlStateManager._glDeleteBuffers(vbo);
            vao = vbo = 0;
        }
    }
}
