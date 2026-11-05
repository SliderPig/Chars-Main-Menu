package com.example.charmenu.client;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

// A tiny pretend world over the scene's blocks, so the game's own block models, colours and smooth lighting work with no real world loaded.
final class SceneLevel implements BlockAndTintGetter {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState HIDER = Blocks.STONE.defaultBlockState();

    private final SceneData d;
    private int leafX, leafY, leafZ;
    private boolean leafMode, leafDeep;

    SceneLevel(SceneData d) {
        this.d = d;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (!d.inside(x, y, z)) return y < d.y0 ? HIDER : AIR;
        BlockState s = d.states[d.cells[d.index(x, y, z)] & 0xFFFF];
        if (leafMode && s.getBlock() instanceof LeavesBlock && Math.abs(x - leafX) + Math.abs(y - leafY) + Math.abs(z - leafZ) == 1) {
            // while drawing leaves: the faces between leaves are hidden, except the second layer down, which still shows through the gaps in the top layer
            if (leafDeep && touchesAir(x, y, z)) return s;
            return HIDER;
        }
        return s;
    }

    boolean touchesAir(int x, int y, int z) {
        return open(x + 1, y, z) || open(x - 1, y, z) || open(x, y + 1, z) || open(x, y - 1, z) || open(x, y, z + 1) || open(x, y, z - 1);
    }

    // Anything that isn't a leaf or a full block counts as open: that face can be seen.
    private boolean open(int x, int y, int z) {
        if (!d.inside(x, y, z)) return true;
        BlockState s = d.states[d.cells[d.index(x, y, z)] & 0xFFFF];
        return !(s.getBlock() instanceof LeavesBlock) && !s.canOcclude();
    }

    // The second layer of a leaf cloud: nothing open beside it, but a neighbour of something that is.
    boolean secondLayer(int x, int y, int z) {
        return leafAndOpen(x + 1, y, z) || leafAndOpen(x - 1, y, z) || leafAndOpen(x, y + 1, z) || leafAndOpen(x, y - 1, z) || leafAndOpen(x, y, z + 1) || leafAndOpen(x, y, z - 1);
    }

    private boolean leafAndOpen(int x, int y, int z) {
        if (!d.inside(x, y, z)) return false;
        return d.states[d.cells[d.index(x, y, z)] & 0xFFFF].getBlock() instanceof LeavesBlock && touchesAir(x, y, z);
    }

    void leafMode(int x, int y, int z, boolean deep) {
        leafMode = true;
        leafDeep = deep;
        leafX = x;
        leafY = y;
        leafZ = z;
    }

    void leafModeOff() {
        leafMode = false;
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return 384;
    }

    @Override
    public int getMinBuildHeight() {
        return -64;
    }

    // The shader lights faces by where the sun really is, so the game's fixed side shading stays out of it.
    @Override
    public float getShade(Direction dir, boolean shade) {
        return 1f;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return null;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        if (resolver == BiomeColors.WATER_COLOR_RESOLVER) return 0x4DA6C8;
        // soft patches between the cherry grove's yellow green and a deeper meadow green
        double n = patches(pos.getX() * 0.045, pos.getZ() * 0.045) * 0.65 + patches(pos.getX() * 0.17 + 40, pos.getZ() * 0.17) * 0.35;
        double t = Math.max(0, Math.min(1, n));
        if (resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER) return mix(0x7FB238, 0x5C9E31, t);
        return mix(0xB6DB61, 0x8AC254, t);
    }

    private static int mix(int a, int b, double t) {
        int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
        int g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    private static double patches(double x, double z) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double fx = x - xi, fz = z - zi;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(xi, zi), b = hash(xi + 1, zi), c = hash(xi, zi + 1), e = hash(xi + 1, zi + 1);
        return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + e * fx) * fz;
    }

    private static double hash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (!d.inside(x, y, z)) return layer == LightLayer.SKY && y >= d.y0 ? 15 : 0;
        return (layer == LightLayer.SKY ? d.sky : d.block)[d.index(x, y, z)];
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darken) {
        return Math.max(0, Math.max(getBrightness(LightLayer.SKY, pos) - darken, getBrightness(LightLayer.BLOCK, pos)));
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        return getBrightness(LightLayer.SKY, pos) >= 15;
    }
}
