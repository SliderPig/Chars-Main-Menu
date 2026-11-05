package com.example.charmenu.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Turns the scene into plain vertex arrays using the game's real block models, on a background thread.
// The drawing thread only has to copy the finished arrays to the graphics card.
final class MeshBuilder {
    static final int SOLID = 0, CUTOUT = 1, GLASS = 2, LAYERS = 3;
    private static final int SECTION = 32;

    static final class Part {
        final int layer;
        int[] data = new int[8 * 4 * 64];
        int ints;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        Part(int layer) {
            this.layer = layer;
        }

        void add(int a, int b, int c, int d, int e, int f, int g, int h) {
            if (ints + 8 > data.length) data = Arrays.copyOf(data, data.length * 2);
            data[ints] = a;
            data[ints + 1] = b;
            data[ints + 2] = c;
            data[ints + 3] = d;
            data[ints + 4] = e;
            data[ints + 5] = f;
            data[ints + 6] = g;
            data[ints + 7] = h;
            ints += 8;
        }

        int vertices() {
            return ints / 8;
        }
    }

    static final class DoorPart {
        final SceneData.Door door;
        final Part mesh;

        DoorPart(SceneData.Door door, Part mesh) {
            this.door = door;
            this.mesh = mesh;
        }
    }

    static final class Result {
        final List<Part> parts = new ArrayList<>();
        final List<DoorPart> doors = new ArrayList<>();
        final List<float[]> lamps = new ArrayList<>();
        SceneData data;
        int quads;
    }

    // Writes what the game's block renderer hands out: position, colour, texture spot, light, facing. Adds the block's tint and how much the wind moves it.
    private static final class Writer implements VertexConsumer {
        Part part;
        float x, y, z, u, v;
        int r = 255, g = 255, b = 255, a = 255, uv2;
        int normal;
        float offX, offY, offZ;
        float tintR = 1, tintG = 1, tintB = 1;
        float swayBase, swayGain, cellY;
        boolean bothLowered;

        @Override
        public VertexConsumer vertex(double px, double py, double pz) {
            x = (float) px + offX;
            y = (float) py + offY;
            z = (float) pz + offZ;
            return this;
        }

        @Override
        public VertexConsumer color(int cr, int cg, int cb, int ca) {
            r = cr;
            g = cg;
            b = cb;
            a = ca;
            return this;
        }

        @Override
        public VertexConsumer uv(float cu, float cv) {
            u = cu;
            v = cv;
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int cu, int cv) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int cu, int cv) {
            uv2 = (cu & 0xFFFF) | (cv & 0xFFFF) << 16;
            return this;
        }

        @Override
        public VertexConsumer normal(float nx, float ny, float nz) {
            normal = ((int) (nx * 127) & 255) | ((int) (ny * 127) & 255) << 8 | ((int) (nz * 127) & 255) << 16;
            return this;
        }

        @Override
        public void endVertex() {
            Part p = part;
            float sway = swayBase == 0 && swayGain == 0 ? 0 : Math.max(0, Math.min(1, swayBase + (y - cellY) * swayGain));
            int colour = Math.min(255, (int) (r * tintR)) | Math.min(255, (int) (g * tintG)) << 8 | Math.min(255, (int) (b * tintB)) << 16 | (int) (sway * 255) << 24;
            p.add(Float.floatToRawIntBits(x), Float.floatToRawIntBits(y), Float.floatToRawIntBits(z), colour, Float.floatToRawIntBits(u), Float.floatToRawIntBits(v), uv2, normal);
            if (x < p.minX) p.minX = x;
            if (y < p.minY) p.minY = y;
            if (z < p.minZ) p.minZ = z;
            if (x > p.maxX) p.maxX = x;
            if (y > p.maxY) p.maxY = y;
            if (z > p.maxZ) p.maxZ = z;
        }

        @Override
        public void defaultColor(int cr, int cg, int cb, int ca) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }

    static Result build(SceneData d) {
        Result out = new Result();
        out.data = d;
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        ModelBlockRenderer blocks = dispatcher.getModelRenderer();
        SceneLevel level = new SceneLevel(d);
        Writer w = new Writer();
        PoseStack pose = new PoseStack();
        RandomSource rand = RandomSource.create();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Map<Long, Part> parts = new HashMap<>();
        ModelBlockRenderer.enableCaching();
        try {
            for (int i = 0; i < d.nx; i++) {
                int x = d.x0 + i;
                for (int k = 0; k < d.nz; k++) {
                    int z = d.z0 + k;
                    int columnBase = (i * d.nz + k) * d.ny;
                    for (int j = 0; j < d.ny; j++) {
                        int id = d.cells[columnBase + j] & 0xFFFF;
                        if (id == 0) continue;
                        BlockState state = d.states[id];
                        if (state.isAir()) continue;
                        int y = d.y0 + j;
                        pos.set(x, y, z);

                        boolean leaf = state.getBlock() instanceof LeavesBlock;
                        if (leaf) {
                            boolean top = level.touchesAir(x, y, z);
                            boolean second = !top && level.secondLayer(x, y, z);
                            if (!top && !second) continue;
                            level.leafMode(x, y, z, second);
                        }
                        int tint = d.tints[id];
                        if (tint >= 0) {
                            w.tintR = (tint >> 16 & 255) / 255f;
                            w.tintG = (tint >> 8 & 255) / 255f;
                            w.tintB = (tint & 255) / 255f;
                        } else {
                            w.tintR = w.tintG = w.tintB = 1;
                        }
                        setSway(w, state, x, y, z);
                        if (state.is(Blocks.LANTERN)) {
                            out.lamps.add(new float[] {x + .5f, y + .4f, z + .5f});
                        }

                        FluidState fluid = state.getFluidState();
                        if (!fluid.isEmpty()) {
                            w.part = partFor(parts, x, z, GLASS);
                            w.offX = x & ~15;
                            w.offY = y & ~15;
                            w.offZ = z & ~15;
                            dispatcher.renderLiquid(pos, level, w, state, fluid);
                            w.offX = w.offY = w.offZ = 0;
                        }

                        BakedModel model = dispatcher.getBlockModel(state);
                        long seed = state.getSeed(pos);
                        rand.setSeed(seed);
                        ModelData md = model.getModelData(level, pos, state, ModelData.EMPTY);
                        for (RenderType rt : model.getRenderTypes(state, rand, md)) {
                            w.part = partFor(parts, x, z, leaf ? CUTOUT : layerOf(rt));
                            pose.pushPose();
                            pose.translate(x, y, z);
                            Vec3 off = state.getOffset(level, pos);
                            pose.translate(off.x, off.y, off.z);
                            rand.setSeed(seed);
                            tesselate(blocks, level, model, state, pos, pose, w, true, rand, seed, md, rt);
                            pose.popPose();
                        }
                        if (leaf) level.leafModeOff();
                    }
                }
            }
            for (SceneData.Door door : d.doors) out.doors.add(new DoorPart(door, doorMesh(door, dispatcher, blocks, level, w, rand)));
        } finally {
            ModelBlockRenderer.clearCache();
        }
        for (Part p : parts.values()) {
            if (p.ints > 0) {
                out.parts.add(p);
                out.quads += p.vertices() / 4;
            }
        }
        return out;
    }

    private static void setSway(Writer w, BlockState state, int x, int y, int z) {
        w.cellY = y;
        if (state.getBlock() instanceof LeavesBlock) {
            int h = x * 31 + y * 17 + z * 7;
            w.swayBase = 0.6f + (h & 3) * 0.13f;
            w.swayGain = 0;
        } else if (state.getBlock() instanceof BushBlock && !(state.getBlock() instanceof PinkPetalsBlock)) {
            if (state.getBlock() instanceof DoublePlantBlock) {
                boolean upper = state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
                w.swayBase = upper ? 0.5f : 0f;
                w.swayGain = 0.5f;
            } else {
                w.swayBase = 0;
                w.swayGain = 0.8f;
            }
        } else {
            w.swayBase = 0;
            w.swayGain = 0;
        }
    }

    private static Part partFor(Map<Long, Part> parts, int x, int z, int layer) {
        long key = ((long) Math.floorDiv(x, SECTION) & 0xFFFFF) << 24 | ((long) Math.floorDiv(z, SECTION) & 0xFFFFF) << 4 | layer;
        return parts.computeIfAbsent(key, k -> new Part(layer));
    }

    private static int layerOf(RenderType rt) {
        if (rt == RenderType.solid()) return SOLID;
        if (rt == RenderType.translucent()) return GLASS;
        return CUTOUT;
    }

    // Smooth lighting always, even if it's turned off in the options: the menu is a picture, it should always look its best.
    private static void tesselate(ModelBlockRenderer blocks, SceneLevel level, BakedModel model, BlockState state, BlockPos pos, PoseStack pose, Writer w, boolean checkSides, RandomSource rand, long seed, ModelData md, RenderType rt) {
        if (state.getLightEmission() == 0 && model.useAmbientOcclusion(state, rt)) {
            blocks.tesselateWithAO(level, model, state, pos, pose, w, checkSides, rand, seed, OverlayTexture.NO_OVERLAY, md, rt);
        } else {
            blocks.tesselateWithoutAO(level, model, state, pos, pose, w, checkSides, rand, seed, OverlayTexture.NO_OVERLAY, md, rt);
        }
    }

    // A door leaf is drawn around its hinge so the menu can swing it: its vertices are relative to the hinge line.
    private static Part doorMesh(SceneData.Door door, BlockRenderDispatcher dispatcher, ModelBlockRenderer blocks, SceneLevel level, Writer w, RandomSource rand) {
        Part part = new Part(CUTOUT);
        w.part = part;
        w.swayBase = w.swayGain = 0;
        w.tintR = w.tintG = w.tintB = 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        DoorHingeSide hinge = "left".equals(door.hinge()) ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
        for (int h = 0; h < 2; h++) {
            BlockState state = Blocks.CHERRY_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HINGE, hinge)
                .setValue(DoorBlock.HALF, h == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER).setValue(DoorBlock.OPEN, false);
            int y = door.y() + h;
            pos.set(door.x(), y, door.z());
            BakedModel model = dispatcher.getBlockModel(state);
            long seed = state.getSeed(pos);
            ModelData md = model.getModelData(level, pos, state, ModelData.EMPTY);
            for (RenderType rt : model.getRenderTypes(state, rand, md)) {
                PoseStack pose = new PoseStack();
                pose.translate(door.x() - door.pivotX(), y, door.z() - door.pivotZ());
                tesselate(blocks, level, model, state, pos, pose, w, false, rand, seed, md, rt);
            }
        }
        return part;
    }
}
