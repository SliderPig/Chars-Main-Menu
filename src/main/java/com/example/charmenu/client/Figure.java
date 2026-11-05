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
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

// Your own skin on a real player model, fast asleep on the branch: one arm under the head, one leg dangling, breathing slowly,
// with a small black cat curled up on your chest and your other hand slowly stroking it.
final class Figure {
    private static final float SCALE = 1.7f;
    private static final float CAT_SCALE = 0.58f;
    private static final ResourceLocation CAT_SKIN = new ResourceLocation("textures/entity/cat/all_black.png");

    private static PlayerModel<LivingEntity> model;
    private static ModelPart cat;
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
        if (Boolean.getBoolean("charmenu.steve")) {
            // for testing with a bright skin when there's no real account
            skin = new ResourceLocation("textures/entity/player/wide/steve.png");
            slim = false;
            return;
        }
        // the game fetches it in the background and calls back on the main thread when the picture is ready, the default skin shows until then
        mc.getSkinManager().registerSkins(profile, (type, location, texture) -> {
            if (type == MinecraftProfileTexture.Type.SKIN) {
                skin = location;
                boolean wantSlim = "slim".equals(texture.getMetadata("model"));
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

    private static ModelPart cat() {
        if (cat == null) cat = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.CAT);
        return cat;
    }

    static void draw(SceneData d, double time) {
        ask();
        PlayerModel<LivingEntity> m = model();
        float breath = (float) Math.sin(time * 1.05);
        // slow strokes with a little rest at each end, about one every five seconds
        double stroke = 0.5 + 0.5 * Math.sin(time * 1.25);
        stroke = stroke * stroke * (3 - 2 * stroke);
        pose(m, breath, (float) stroke, time);

        PoseStack pose = new PoseStack();
        // lying on the back along the branch, head towards the trunk, left side towards the camera
        pose.translate(d.sleeper[0], d.sleeper[1] + 0.125 * SCALE, d.sleeper[2]);
        pose.mulPose(Axis.YP.rotationDegrees(-90));
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(SCALE, SCALE, SCALE);
        pose.scale(-1, -1, 1);
        pose.translate(0, -0.75, 0);

        Shaders.figure.getUniform("Lift").set(0f);
        RenderSystem.setShader(() -> Shaders.figure);
        RenderSystem.setShaderTexture(0, skin);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        m.renderToBuffer(pose, b, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        BufferUploader.drawWithShader(b.end());

        drawCat(pose, breath, (float) stroke, time);
    }

    // Everything in here is relative to the player model, so the cat rises and falls with the chest.
    private static void drawCat(PoseStack pose, float breath, float stroke, double time) {
        ModelPart c = cat();
        float d = (float) Math.PI / 180;
        for (String leg : new String[] {"left_hind_leg", "right_hind_leg", "left_front_leg", "right_front_leg"}) {
            // tucked underneath, out of sight
            c.getChild(leg).visible = false;
        }
        ModelPart body = c.getChild("body");
        ModelPart head = c.getChild("head");
        ModelPart tail1 = c.getChild("tail1");
        ModelPart tail2 = c.getChild("tail2");
        float catBreath = (float) Math.sin(time * 1.9);
        body.xScale = 1 + 0.035f * catBreath;
        body.zScale = 1 + 0.045f * catBreath;

        // chin resting on the chest, leaning into the hand as it comes past
        head.setPos(0, 17.4f, -8.0f);
        head.setRotation((14 - 6 * stroke) * d, (30 + 5 * stroke) * d, 6 * d);

        // the tail wraps round the near side of the body, and its end flicks now and then
        float flickWhen = (float) Math.max(0, Math.sin(time * 0.43) - 0.35) / 0.65f;
        float flick = (float) Math.sin(time * 5.1) * 26 * d * flickWhen + (float) Math.sin(time * 0.8) * 5 * d;
        tail1.setPos(0, 18.2f, 7.5f);
        tail1.setRotation(90 * d, -62 * d, 0);
        tail2.setPos(-7f, 18.2f, 11.2f);
        tail2.setRotation(90 * d, -128 * d + flick, 0);

        pose.pushPose();
        // the middle of the chest, on top of the shirt
        float lift = 0.0035f * breath * 16;
        pose.translate(0.02, 6.2 / 16.0, (-2.3 - lift) / 16.0 - CAT_SCALE * (20.0 / 16.0));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        // lying a little across the chest, not straight along it
        pose.mulPose(Axis.YP.rotationDegrees(24));
        pose.scale(CAT_SCALE, CAT_SCALE, CAT_SCALE);

        // a black cat on dark clothes would vanish: a touch of soft sheen keeps its shape readable
        Shaders.figure.getUniform("Lift").set(0.075f);
        RenderSystem.setShader(() -> Shaders.figure);
        RenderSystem.setShaderTexture(0, CAT_SKIN);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        c.render(pose, b, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        BufferUploader.drawWithShader(b.end());
        pose.popPose();
    }

    private static void pose(PlayerModel<LivingEntity> m, float breath, float stroke, double time) {
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

        // the arm on the camera's side is tucked under the head, so nothing hides the cat
        m.leftArm.setPos(5, 2, 3.6f);
        m.leftArm.setRotation(-176 * d, 0, -16 * d);
        // the other arm lies down along the body with its hand on the cat, stroking along its back, slowly
        m.rightArm.setPos(-5, 2, -0.2f - 0.06f * breath);
        m.rightArm.setRotation((-32 + 8 * stroke) * d, 0, -(36 - 14 * stroke) * d);

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
