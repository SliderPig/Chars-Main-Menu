package com.example.charmenu.client;

import com.example.charmenu.CharMenu;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

// Holds the scene on the graphics card and draws it: sky, far land, blocks, doors, the sleeper, petals. Nothing else in the mod touches the graphics card.
public final class SceneEngine {
    private static final ResourceLocation CLOUDS = new ResourceLocation(CharMenu.MODID, "textures/menu/clouds.png");
    private static final float FRONT_SWING_DEGREES = 104;

    private record DoorDraw(GpuMesh mesh, SceneData.Door door) {}

    private static boolean failed;
    private static Thread worker;
    private static volatile boolean working;
    private static volatile MeshBuilder.Result built;
    private static volatile Throwable workerError;
    private static ArrayDeque<MeshBuilder.Part> toUpload;
    private static final List<GpuMesh>[] layers = newLayers();
    private static final List<DoorDraw> doors = new ArrayList<>();
    private static GpuMesh far;
    private static SceneData data;
    private static List<float[]> lamps = List.of();
    private static boolean ready;

    @SuppressWarnings("unchecked")
    private static List<GpuMesh>[] newLayers() {
        List<GpuMesh>[] l = new List[MeshBuilder.LAYERS];
        for (int i = 0; i < l.length; i++) l[i] = new ArrayList<>();
        return l;
    }

    private SceneEngine() {}

    public static boolean failed() {
        return failed || !Shaders.ok;
    }

    public static boolean ready() {
        return ready;
    }

    public static void fail(Throwable t) {
        if (!failed) CharMenu.LOG.warn("The menu scene hit a problem, going back to the vanilla menu: {}", t.toString());
        failed = true;
        freeAll();
        try {
            restoreState();
        } catch (Throwable ignored) {
        }
    }

    // Starts the background work if nothing is built yet. Cheap to call every frame.
    public static void ensure() {
        if (ready || failed || toUpload != null || working || built != null) return;
        working = true;
        worker = new Thread(() -> {
            try {
                long t0 = System.nanoTime();
                SceneData d = SceneData.load();
                long t1 = System.nanoTime();
                MeshBuilder.Result r = MeshBuilder.build(d);
                CharMenu.LOG.info("Menu scene ready: load+light {} ms, meshes {} ms, {} quads, {} parts", (t1 - t0) / 1_000_000, (System.nanoTime() - t1) / 1_000_000, r.quads, r.parts.size());
                // a world may have loaded in the meantime: then it's not wanted any more
                if (Minecraft.getInstance().level == null) built = r;
            } catch (Throwable t) {
                workerError = t;
            } finally {
                working = false;
            }
        }, "charmenu-scene");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    private static void takeBuilt() {
        Throwable err = workerError;
        if (err != null) {
            workerError = null;
            fail(err);
            return;
        }
        MeshBuilder.Result r = built;
        if (r != null && toUpload == null) {
            built = null;
            data = r.data;
            lamps = r.lamps;
            toUpload = new ArrayDeque<>(r.parts);
            for (MeshBuilder.DoorPart dp : r.doors) {
                doors.add(new DoorDraw(new GpuMesh(dp.mesh.data, dp.mesh.ints, 8, DefaultVertexFormat.BLOCK), dp.door));
            }
            far = new GpuMesh(data.farQuads, data.farCount * 16, 4, DefaultVertexFormat.POSITION_COLOR);
            data.farQuads = null;
            data.cells = null;
            data.sky = null;
            data.block = null;
            data.states = null;
            data.tints = null;
        }
        if (toUpload != null) {
            // a little each frame so the menu never hitches while the scene arrives
            int budget = 3_000_000;
            while (budget > 0 && !toUpload.isEmpty()) {
                MeshBuilder.Part p = toUpload.poll();
                layers[p.layer].add(new GpuMesh(p.data, p.ints, 8, DefaultVertexFormat.BLOCK).bounds(p.minX, p.minY, p.minZ, p.maxX, p.maxY, p.maxZ));
                budget -= p.ints * 4;
                p.data = null;
            }
            if (toUpload.isEmpty()) {
                toUpload = null;
                ready = true;
                Director.get().attach(data);
                Director.get().sceneReady();
                VertexBuffer.unbind();
            }
        }
    }

    public static void freeAll() {
        for (List<GpuMesh> l : layers) {
            for (GpuMesh m : l) m.free();
            l.clear();
        }
        for (DoorDraw d : doors) d.mesh.free();
        doors.clear();
        if (far != null) far.free();
        far = null;
        data = null;
        lamps = List.of();
        toUpload = null;
        built = null;
        ready = false;
        Director.get().attach(null);
    }

    // Draws the whole picture behind the current screen.
    public static void draw() {
        Director dir = Director.get();
        takeBuilt();
        if (failed) return;
        dir.update();
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        float aspect = (float) window.getWidth() / Math.max(1, window.getHeight());
        Director.Cam cam = dir.camera();
        if (data == null) {
            // no scene yet: look at the sky above the house
            cam.x = 0;
            cam.y = 5;
            cam.z = 40;
            cam.lx = 0;
            cam.ly = 12;
            cam.lz = 0;
            cam.fov = 56;
        }

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        PoseStack stack = RenderSystem.getModelViewStack();
        stack.pushPose();
        stack.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderColor(1, 1, 1, 1);

        Vector3f eye = new Vector3f((float) cam.x, (float) cam.y, (float) cam.z);
        Vector3f look = new Vector3f((float) cam.lx, (float) cam.ly, (float) cam.lz);
        Vector3f forward = new Vector3f(look).sub(eye).normalize();
        Vector3f right = new Vector3f(forward).cross(0, 1, 0).normalize();
        Vector3f up = new Vector3f(right).cross(forward).normalize();
        float tanY = (float) Math.tan(Math.toRadians(cam.fov) / 2), tanX = tanY * aspect;
        float time = (float) (dir.time() % 7200.0);
        float[] sun = data == null ? new float[] {-0.56f, 0.66f, 0.5f} : new float[] {(float) data.sun[0], (float) data.sun[1], (float) data.sun[2]};

        drawSky(forward, right, up, tanX, tanY, time, sun);

        if (ready) {
            Matrix4f view = new Matrix4f().lookAt(eye, look, new Vector3f(0, 1, 0));
            Matrix4f farProjection = new Matrix4f().perspective((float) Math.toRadians(cam.fov), aspect, 60f, 11000f);
            Matrix4f nearProjection = new Matrix4f().perspective((float) Math.toRadians(cam.fov), aspect, 0.06f, 900f);

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            drawFar(view, farProjection, eye, time, sun);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

            boolean high = MenuConfig.highQuality;
            drawBlocks(view, nearProjection, eye, time, sun, high);
            VertexBuffer.unbind();

            // what follows goes through the game's normal way of drawing, which reads these two
            RenderSystem.setProjectionMatrix(new Matrix4f(nearProjection), VertexSorting.DISTANCE_TO_ORIGIN);
            stack.setIdentity();
            stack.mulPoseMatrix(view);
            RenderSystem.applyModelViewMatrix();

            RenderSystem.disableCull();
            setCommon(Shaders.figure, view, nearProjection, eye, time, sun);
            Figure.draw(data, time);
            RenderSystem.enableCull();

            Petals.lanternGlow(lamps, cam, right.x, right.y, right.z, up.x, up.y, up.z, time);
            Petals.draw(data, cam, time, high);
        }

        stack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
        restoreState();
    }

    private static void restoreState() {
        VertexBuffer.unbind();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private static void drawSky(Vector3f forward, Vector3f right, Vector3f up, float tanX, float tanY, float time, float[] sun) {
        ShaderInstance s = Shaders.sky;
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        set3(s, "Forward", forward.x, forward.y, forward.z);
        set3(s, "Right", right.x * tanX, right.y * tanX, right.z * tanX);
        set3(s, "Up", up.x * tanY, up.y * tanY, up.z * tanY);
        set3(s, "SunDir", sun[0], sun[1], sun[2]);
        set1(s, "Time", time);
        RenderSystem.setShader(() -> s);
        Minecraft.getInstance().getTextureManager().getTexture(CLOUDS).setFilter(true, false);
        RenderSystem.setShaderTexture(0, CLOUDS);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        b.vertex(-1, -1, 0).endVertex();
        b.vertex(1, -1, 0).endVertex();
        b.vertex(1, 1, 0).endVertex();
        b.vertex(-1, 1, 0).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    private static void drawFar(Matrix4f view, Matrix4f projection, Vector3f eye, float time, float[] sun) {
        ShaderInstance s = Shaders.far;
        setCommon(s, view, projection, eye, time, sun);
        RenderSystem.disableBlend();
        s.apply();
        far.draw();
        s.clear();
    }

    private static void drawBlocks(Matrix4f view, Matrix4f projection, Vector3f eye, float time, float[] sun, boolean high) {
        ShaderInstance s = Shaders.scene;
        setCommon(s, view, projection, eye, time, sun);
        set1(s, "Sway", high ? 1 : 0.5f);
        Minecraft mc = Minecraft.getInstance();
        AbstractTexture atlas = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        atlas.setFilter(false, true);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        s.setSampler("Sampler0", atlas.getId());
        FrustumIntersection frustum = new FrustumIntersection(new Matrix4f(projection).mul(view));

        set1(s, "AlphaCut", 0);
        RenderSystem.disableBlend();
        s.apply();
        RenderSystem.disableBlend();
        drawLayer(layers[MeshBuilder.SOLID], frustum);

        set1(s, "AlphaCut", 0.5f);
        s.apply();
        RenderSystem.disableBlend();
        drawLayer(layers[MeshBuilder.CUTOUT], frustum);
        drawDoors(s, view);
        s.MODEL_VIEW_MATRIX.set(view);
        s.MODEL_VIEW_MATRIX.upload();

        set1(s, "AlphaCut", 0.004f);
        s.apply();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        drawLayer(layers[MeshBuilder.GLASS], frustum);
        RenderSystem.depthMask(true);
        s.clear();
        atlas.setFilter(false, false);
    }

    private static void drawLayer(List<GpuMesh> layer, FrustumIntersection frustum) {
        for (int i = 0; i < layer.size(); i++) {
            GpuMesh m = layer.get(i);
            if (frustum.testAab(m.minX, m.minY, m.minZ, m.maxX, m.maxY, m.maxZ)) m.draw();
        }
    }

    private static void drawDoors(ShaderInstance s, Matrix4f view) {
        Director dir = Director.get();
        for (DoorDraw d : doors) {
            float open = (float) ("front".equals(d.door.group()) ? dir.frontDoor() : dir.backDoor());
            Matrix4f m = new Matrix4f(view).translate((float) d.door.pivotX(), 0, (float) d.door.pivotZ()).rotateY((float) Math.toRadians(open * FRONT_SWING_DEGREES * d.door.turn()));
            s.MODEL_VIEW_MATRIX.set(m);
            s.MODEL_VIEW_MATRIX.upload();
            d.mesh.draw();
        }
    }

    private static void setCommon(ShaderInstance s, Matrix4f view, Matrix4f projection, Vector3f eye, float time, float[] sun) {
        if (s.MODEL_VIEW_MATRIX != null) s.MODEL_VIEW_MATRIX.set(view);
        if (s.PROJECTION_MATRIX != null) s.PROJECTION_MATRIX.set(projection);
        set3(s, "CameraPos", eye.x, eye.y, eye.z);
        set3(s, "SunDir", sun[0], sun[1], sun[2]);
        set1(s, "Time", time);
    }

    private static void set1(ShaderInstance s, String name, float v) {
        com.mojang.blaze3d.shaders.Uniform u = s.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void set3(ShaderInstance s, String name, float x, float y, float z) {
        com.mojang.blaze3d.shaders.Uniform u = s.getUniform(name);
        if (u != null) u.set(x, y, z);
    }

}
