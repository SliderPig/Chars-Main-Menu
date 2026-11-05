package com.example.charmenu.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

// Your own skin on a real player model, fast asleep on the branch: arm under the head, one leg dangling, breathing slowly.
final class Figure {
    private static final float SCALE = 1.25f;

    private static PlayerModel<LivingEntity> model;
    private static boolean slim;
    private static ResourceLocation skin;
    private static boolean asked;

    private Figure() {}

    private static void ask() {
        if (asked) return;
        asked = true;
        Minecraft mc = Minecraft.getInstance();
        GameProfile profile = mc.getUser().getGameProfile();
        UUID id = profile.getId() != null ? profile.getId() : new UUID(0, 0);
        skin = DefaultPlayerSkin.getDefaultSkin(id);
        slim = "slim".equals(DefaultPlayerSkin.getSkinModelName(id));
        String testSkin = System.getProperty("charmenu.testskin");
        if (testSkin != null) return;
        // the game fetches it in the background and calls back on the main thread when the picture is ready, the default skin shows until then
        mc.getSkinManager().registerSkins(profile, (type, location, texture) -> {
            if (type == MinecraftProfileTexture.Type.SKIN) {
                skin = location;
                String kind = texture.getMetadata("model");
                boolean wantSlim = "slim".equals(kind);
                if (wantSlim != slim) {
                    slim = wantSlim;
                    model = null;
                }
            }
        }, true);
    }

    private static PlayerModel<LivingEntity> model() {
        if (model == null) {
            Minecraft mc = Minecraft.getInstance();
            model = new PlayerModel<>(mc.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
        }
        return model;
    }

    static void draw(SceneData d, double time) {
        ask();
        PlayerModel<LivingEntity> m = model();
        float breath = (float) Math.sin(time * 1.05);
        pose(m, breath, time);

        PoseStack pose = new PoseStack();
        // lying on the back along the branch, head towards the trunk, left side towards the camera
        pose.translate(d.sleeper[0], d.sleeper[1] + 0.125 * SCALE, d.sleeper[2]);
        pose.mulPose(Axis.YP.rotationDegrees(-90));
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(SCALE, SCALE, SCALE);
        pose.scale(-1, -1, 1);
        pose.translate(0, -0.75, 0);

        RenderSystem.setShader(() -> Shaders.figure);
        RenderSystem.setShaderTexture(0, skin);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        m.renderToBuffer(pose, b, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        BufferUploader.drawWithShader(b.end());
    }

    private static void pose(PlayerModel<LivingEntity> m, float breath, double time) {
        m.setAllVisible(true);
        m.crouching = false;
        m.riding = false;
        m.young = false;
        m.attackTime = 0;
        float d = (float) Math.PI / 180;

        // head rests on the arm, turned towards the camera
        m.head.setPos(0, 0, 1.2f);
        m.head.setRotation(-14 * d, -38 * d, 0);

        // chest rises and falls
        m.body.setPos(0, 0, 0);
        m.body.setRotation(0, 0, 0);
        m.body.zScale = 1 + 0.03f * breath;

        // right arm behind the head
        m.rightArm.setPos(-5, 2, 2.6f);
        m.rightArm.setRotation(-172 * d, 0, 18 * d);
        // left arm lies across the belly and moves with the breath
        m.leftArm.setPos(5, 2, -0.2f - 0.06f * breath);
        m.leftArm.setRotation(-16 * d, 0, 32 * d);

        // left leg dangles off the branch on the camera's side and rocks a tiny bit
        float rock = (float) Math.sin(time * 0.45) * 2.5f * d;
        m.leftLeg.setPos(1.9f, 12, 0);
        m.leftLeg.setRotation(76 * d + rock, 36 * d, 0);
        // right leg lies along the branch, a little relaxed outwards
        m.rightLeg.setPos(-1.9f, 12, 0);
        m.rightLeg.setRotation(-4 * d, 0, 6 * d);

        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.jacket.zScale = m.body.zScale;
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftPants.copyFrom(m.leftLeg);
        m.rightPants.copyFrom(m.rightLeg);
    }
}
