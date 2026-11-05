package com.example.charmenu.client;

import com.example.charmenu.CharMenu;
import com.example.charmenu.model.LightBake;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

// The scene as tools/make_scene.py wrote it: every block in the box, the far land, and where things are.
final class SceneData {
    record Door(String group, int x, int y, int z, String hinge, double pivotX, double pivotZ, int turn) {}

    int x0, y0, z0, nx, ny, nz;
    short[] cells;
    BlockState[] states;
    int[] tints;        // 0xRRGGBB multiplied over the block, or -1
    byte[] sky, block;
    int[] farQuads;     // four vertices a quad, four ints a vertex: x y z colour
    int farCount;

    double[] sun = {-0.56, 0.66, 0.5};
    double[] front, tree;
    double[] sleeper = {0, 0, 0};
    double[] indoors = {0, 0, 0, 0, 0, 0};
    final List<double[]> flyOut = new ArrayList<>(), flyBack = new ArrayList<>();
    final List<Door> doors = new ArrayList<>();
    final List<Paintings.Hung> paintings = new ArrayList<>();

    int index(int x, int y, int z) {
        return ((x - x0) * nz + (z - z0)) * ny + (y - y0);
    }

    boolean inside(int x, int y, int z) {
        return x >= x0 && y >= y0 && z >= z0 && x < x0 + nx && y < y0 + ny && z < z0 + nz;
    }

    // Everything that can happen off the main thread: the game's block models are only read, never changed.
    static SceneData load() throws IOException {
        SceneData d = new SceneData();
        d.readMeta();
        d.readWorld();
        d.readFar();
        d.bakeLight();
        return d;
    }

    private static InputStream open(String name) throws IOException {
        String over = System.getProperty("charmenu.scenedir");
        if (over != null) {
            Path p = Path.of(over).resolve(name);
            if (Files.exists(p)) return new ByteArrayInputStream(Files.readAllBytes(p));
        }
        ResourceLocation rl = new ResourceLocation(CharMenu.MODID, "scene/" + name);
        return Minecraft.getInstance().getResourceManager().getResourceOrThrow(rl).open();
    }

    private void readMeta() throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(open("meta.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] w = line.trim().split("\\s+");
                switch (w[0]) {
                    case "sun" -> sun = numbers(w, 1);
                    case "sleeper" -> sleeper = numbers(w, 1);
                    case "indoors" -> indoors = numbers(w, 1);
                    case "front" -> front = numbers(w, 1);
                    case "tree" -> tree = numbers(w, 1);
                    case "flyout" -> flyOut.add(numbers(w, 1));
                    case "flyback" -> flyBack.add(numbers(w, 1));
                    case "door" -> doors.add(new Door(w[1], Integer.parseInt(w[2]), Integer.parseInt(w[3]), Integer.parseInt(w[4]), w[5], Double.parseDouble(w[6]), Double.parseDouble(w[7]), Integer.parseInt(w[8])));
                    case "painting" -> paintings.add(new Paintings.Hung(w[1], Double.parseDouble(w[2]), Double.parseDouble(w[3]), Double.parseDouble(w[4]), Double.parseDouble(w[5]), Double.parseDouble(w[6]), Integer.parseInt(w[7])));
                    default -> {
                    }
                }
            }
        }
        if (front == null || tree == null) throw new IOException("scene meta is missing its camera spots");
    }

    private static double[] numbers(String[] w, int from) {
        double[] out = new double[w.length - from];
        for (int i = from; i < w.length; i++) out[i - from] = Double.parseDouble(w[i]);
        return out;
    }

    private void readWorld() throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(open("world.bin.gz")), 1 << 16))) {
            x0 = in.readInt();
            y0 = in.readInt();
            z0 = in.readInt();
            nx = in.readInt();
            ny = in.readInt();
            nz = in.readInt();
            int count = in.readInt();
            states = new BlockState[count];
            tints = new int[count];
            for (int i = 0; i < count; i++) {
                int len = in.readUnsignedShort();
                byte[] raw = new byte[len];
                in.readFully(raw);
                String name = new String(raw, StandardCharsets.UTF_8);
                int bar = name.indexOf('|');
                tints[i] = bar < 0 ? -1 : Integer.parseInt(name.substring(bar + 1), 16);
                states[i] = parse(bar < 0 ? name : name.substring(0, bar));
            }
            cells = new short[nx * ny * nz];
            byte[] raw = new byte[cells.length * 2];
            in.readFully(raw);
            for (int i = 0; i < cells.length; i++) {
                cells[i] = (short) (((raw[2 * i] & 255) << 8) | (raw[2 * i + 1] & 255));
            }
        }
    }

    private static BlockState parse(String state) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false).blockState();
        } catch (Exception e) {
            CharMenu.LOG.warn("Scene block {} not understood: {}", state, e.toString());
            return Blocks.AIR.defaultBlockState();
        }
    }

    private void readFar() throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(open("far.bin.gz")), 1 << 16))) {
            farCount = in.readInt();
            farQuads = new int[farCount * 16];
            byte[] raw = new byte[farCount * 32];
            in.readFully(raw);
            int p = 0, out = 0;
            int[] xyz = new int[12];
            for (int q = 0; q < farCount; q++) {
                for (int i = 0; i < 12; i++) {
                    xyz[i] = (short) (((raw[p] & 255) << 8) | (raw[p + 1] & 255));
                    p += 2;
                }
                int r = raw[p + 1] & 255, g = raw[p + 3] & 255, b = raw[p + 5] & 255;
                p += 8;
                int colour = r | g << 8 | b << 16 | 0xFF000000;
                for (int v = 0; v < 4; v++) {
                    farQuads[out++] = Float.floatToRawIntBits(xyz[v * 3]);
                    farQuads[out++] = Float.floatToRawIntBits(xyz[v * 3 + 1]);
                    farQuads[out++] = Float.floatToRawIntBits(xyz[v * 3 + 2]);
                    farQuads[out++] = colour;
                }
            }
        }
    }

    private void bakeLight() {
        byte[] opacityOf = new byte[states.length];
        byte[] lampOf = new byte[states.length];
        for (int i = 0; i < states.length; i++) {
            BlockState s = states[i];
            if (s.isAir()) opacityOf[i] = LightBake.AIR;
            else if (s.getBlock() instanceof LeavesBlock) opacityOf[i] = LightBake.PART;
            else if (s.canOcclude() && s.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) opacityOf[i] = LightBake.SOLID;
            else opacityOf[i] = LightBake.AIR;
            lampOf[i] = (byte) s.getLightEmission();
        }
        byte[] opacity = new byte[cells.length];
        int lamps = 0;
        for (int i = 0; i < cells.length; i++) {
            int id = cells[i] & 0xFFFF;
            opacity[i] = opacityOf[id];
            if (lampOf[id] > 0) lamps++;
        }
        int[] lampCells = new int[lamps];
        byte[] lampLevels = new byte[lamps];
        int n = 0;
        for (int i = 0; i < cells.length; i++) {
            int id = cells[i] & 0xFFFF;
            if (lampOf[id] > 0) {
                lampCells[n] = i;
                lampLevels[n++] = lampOf[id];
            }
        }
        LightBake bake = new LightBake(nx, ny, nz, opacity);
        bake.floodBlockLight(lampCells, lampLevels);
        double len = Math.sqrt(sun[0] * sun[0] + sun[1] * sun[1] + sun[2] * sun[2]);
        bake.bakeSky(sun[0] / len, sun[1] / len, sun[2] / len);
        sky = bake.sky;
        block = bake.block;
    }
}
