package dev.skirmish.studio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skirmish.fx.FxColor;
import dev.skirmish.fx.FxGeometry;
import dev.skirmish.fx.FxPipelines;
import dev.skirmish.fx.FxSprite;
import dev.skirmish.module.killfx.BoltRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the Studio scene into its own texture, like the inventory draws the player, but with everything in one 3D
 * pass: the floor, my model and the mannequin, and the effect particles with depth, so a spark flying behind a model
 * is hidden by it.
 */
final class StudioSceneRenderer extends PictureInPictureRenderer<StudioSceneState> {
    StudioSceneRenderer(MultiBufferSource.BufferSource buffers) {
        super(buffers);
    }

    @Override
    public Class<StudioSceneState> getRenderStateClass() {
        return StudioSceneState.class;
    }

    @Override
    protected String getTextureLabel() {
        return "skirmish_studio";
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected void renderToTexture(StudioSceneState s, PoseStack pose) {
        Minecraft mc = Minecraft.getInstance();
        StudioStage stage = s.stage();
        mc.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        Quaternionf view = new Quaternionf().rotateZ((float) Math.PI)
                .rotateX((float) Math.toRadians(stage.pitch))
                .rotateY((float) Math.toRadians(stage.yaw));
        pose.mulPose(view);
        pose.translate(0f, -stage.centerY, 0f);

        Quaternionf back = new Quaternionf(view).conjugate();
        Vector3f right = back.transform(new Vector3f(1, 0, 0));
        Vector3f up = back.transform(new Vector3f(0, 1, 0));
        Vector3f forward = back.transform(new Vector3f(0, 0, 1));
        backlight(pose, stage, right, up, forward);
        floor(pose, stage);
        bufferSource.endBatch();

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        FeatureRenderDispatcher features = mc.gameRenderer.getFeatureRenderDispatcher();
        CameraRenderState camera = new CameraRenderState();
        camera.orientation = new Quaternionf(view).conjugate().rotateY((float) Math.PI);
        AvatarRenderState me = stage.showMe ? stage.me(s.partialTick()) : null;
        if (me != null) {
            dispatcher.submit(me, camera, stage.meX(), stage.feetY(), 0.0, pose, features.getSubmitNodeStorage());
        }
        if (stage.showDummy && !stage.dummyGone()) {
            dispatcher.submit(stage.dummy(), camera, stage.dummyX, 0.0, 0.0, pose, features.getSubmitNodeStorage());
        }
        features.renderAllFeatures();

        Matrix4f m = pose.last().pose();
        FxGeometry.draw(stage.field, false, m, bufferSource.getBuffer(FxPipelines.PAINT), 0, 0, 0, right, up, forward, null);
        FxGeometry.draw(stage.field, true, m, bufferSource.getBuffer(FxPipelines.GLOW), 0, 0, 0, right, up, forward, null);

        float boltAge = stage.clock - stage.boltAt;
        if (boltAge >= 0 && boltAge < 0.45f) {
            float t = boltAge / 0.45f;
            float alpha = t < 0.15f ? 1f : t < 0.25f ? 0.35f : (1f - t) / 0.75f;
            pose.pushPose();
            pose.translate(stage.dummyX, 0, 0);
            BoltRenderer.geometry(pose.last().pose(), bufferSource.getBuffer(RenderTypes.lightning()), stage.boltSeed, 0.6f,
                    0.55f, 0.6f, 1f, 0.9f * alpha);
            pose.popPose();
        }
    }

    /**
     * A round floor fading out at its edge, lines that slide back under a running model, a soft shadow under each
     * model, a slowly turning ring of light around the stage.
     */
    private void floor(PoseStack pose, StudioStage stage) {
        int[] c = StudioStage.colors(stage.scene);
        Matrix4f m = pose.last().pose();
        float radius = stage.floorRadius;
        VertexConsumer v = bufferSource.getBuffer(FxPipelines.PAINT);
        if (stage.floorDisc && (c[2] >>> 24) > 0) {
            int segments = 48;
            int mid = FxColor.alpha(c[2], 1f);
            int edge = FxColor.alpha(c[2], 0f);
            float r0 = radius * 0.62f;
            for (int i = 0; i < segments; i++) {
                double a0 = i * Math.PI * 2 / segments;
                double a1 = (i + 1) * Math.PI * 2 / segments;
                float x0 = (float) Math.cos(a0);
                float z0 = (float) Math.sin(a0);
                float x1 = (float) Math.cos(a1);
                float z1 = (float) Math.sin(a1);
                solid(v, m, 0, 0, 0, mid);
                solid(v, m, x0 * r0, 0, z0 * r0, mid);
                solid(v, m, x1 * r0, 0, z1 * r0, mid);
                solid(v, m, 0, 0, 0, mid);
                solid(v, m, x0 * r0, 0, z0 * r0, mid);
                solid(v, m, x0 * radius, 0, z0 * radius, edge);
                solid(v, m, x1 * radius, 0, z1 * radius, edge);
                solid(v, m, x1 * r0, 0, z1 * r0, mid);
            }
            // Grid lines across the run, sliding with the treadmill.
            if ((c[3] >>> 24) > 0) {
                double[] f = StudioStage.facing(stage.meYaw);
                float fx = (float) f[0];
                float fz = (float) f[1];
                float sx = -fz;
                float sz = fx;
                float w = 0.012f;
                float offset = (float) (stage.scroll % 0.5);
                for (int k = -6; k <= 6; k++) {
                    float d = k * 0.5f - offset;
                    float half = (float) Math.sqrt(Math.max(0, radius * radius * 0.9 - d * d));
                    if (half <= 0) {
                        continue;
                    }
                    int col = FxColor.alpha(c[3], 1f - Math.abs(d) / radius);
                    float cx = -fx * d;
                    float cz = -fz * d;
                    solid(v, m, cx - sx * half - fx * w, 0.002f, cz - sz * half - fz * w, col);
                    solid(v, m, cx + sx * half - fx * w, 0.002f, cz + sz * half - fz * w, col);
                    solid(v, m, cx + sx * half + fx * w, 0.002f, cz + sz * half + fz * w, col);
                    solid(v, m, cx - sx * half + fx * w, 0.002f, cz - sz * half + fz * w, col);
                }
            }
        }
        // Contact shadows: soft dark pools under the feet, smaller and fainter the higher the model is.
        int shade = stage.scene == StudioStage.Scene.VOID ? 0x00000000 : 0xFF000000;
        if (stage.showMe) {
            float lift = (float) stage.feetY();
            float k = Math.max(0.25f, 1f - lift * 0.55f);
            flat(v, m, FxSprite.GLOW, (float) stage.meX(), 0.003f, 0f, 0.62f * k, 0.48f * k, 0f,
                    FxColor.alpha(shade, 0.62f * k));
        }
        if (stage.showDummy && !stage.dummyGone()) {
            float k = stage.dummyDead() ? 1.25f : 1f;
            flat(v, m, FxSprite.GLOW, (float) stage.dummyX, 0.003f, 0f, 0.62f * k, 0.48f * k, 0f, FxColor.alpha(shade, 0.62f));
        }
        // The stage ring: a crisp line of light with a soft glow, and a faint magic circle turning under the models.
        VertexConsumer g = bufferSource.getBuffer(FxPipelines.GLOW);
        int rim = c[4];
        float r = radius * 0.8f;
        flat(g, m, FxSprite.HALO, 0f, 0.004f, 0f, r * 1.04f, r * 1.04f, stage.clock * 0.05f, FxColor.alpha(rim, 0.55f));
        flat(g, m, FxSprite.THIN_RING, 0f, 0.005f, 0f, r * 0.94f, r * 0.94f, 0f, FxColor.alpha(rim, 0.5f));
        if (stage.floorDisc) {
            flat(g, m, FxSprite.MAGIC_CIRCLE, 0f, 0.003f, 0f, r * 0.9f, r * 0.9f, -stage.clock * 0.08f,
                    FxColor.alpha(rim, 0.13f));
        }
    }

    /** A soft light behind the models in the scene's colour, so they stand out of the backdrop. */
    private void backlight(PoseStack pose, StudioStage stage, Vector3f right, Vector3f up, Vector3f forward) {
        int[] c = StudioStage.colors(stage.scene);
        Matrix4f m = pose.last().pose();
        VertexConsumer g = bufferSource.getBuffer(FxPipelines.GLOW);
        float k = stage.floorRadius / 2.9f;
        float d = 3.5f * k;
        float x = forward.x * d;
        float y = stage.centerY + 0.15f + forward.y * d;
        float z = forward.z * d;
        billboard(g, m, FxSprite.GLOW, x, y, z, 2.3f * k, 2.0f * k, right, up, FxColor.alpha(c[4], 0.2f));
        billboard(g, m, FxSprite.GLOW, x, y - 0.3f * k, z, 1.2f * k, 1.1f * k, right, up, FxColor.alpha(c[4], 0.12f));
    }

    private static void billboard(VertexConsumer v, Matrix4f m, FxSprite sp, float x, float y, float z, float hw, float hh,
                                  Vector3f r, Vector3f u, int color) {
        float u0 = sp.u0;
        float u1 = sp.u0 + sp.du;
        float v0 = sp.v0;
        float v1 = sp.v0 + sp.dv;
        v.addVertex(m, x - r.x * hw - u.x * hh, y - r.y * hw - u.y * hh, z - r.z * hw - u.z * hh).setUv(u0, v1).setColor(color);
        v.addVertex(m, x + r.x * hw - u.x * hh, y + r.y * hw - u.y * hh, z + r.z * hw - u.z * hh).setUv(u1, v1).setColor(color);
        v.addVertex(m, x + r.x * hw + u.x * hh, y + r.y * hw + u.y * hh, z + r.z * hw + u.z * hh).setUv(u1, v0).setColor(color);
        v.addVertex(m, x - r.x * hw + u.x * hh, y - r.y * hw + u.y * hh, z - r.z * hw + u.z * hh).setUv(u0, v0).setColor(color);
    }

    private static void solid(VertexConsumer v, Matrix4f m, float x, float y, float z, int color) {
        v.addVertex(m, x, y, z).setUv(FxGeometry.SOLID_U, FxGeometry.SOLID_V).setColor(color);
    }

    /** A sprite lying on the floor, {@code hw × hd} half-size, turned by {@code rot}. */
    private static void flat(VertexConsumer v, Matrix4f m, FxSprite sp, float x, float y, float z, float hw, float hd, float rot,
                             int color) {
        float cos = (float) Math.cos(rot);
        float sin = (float) Math.sin(rot);
        float ax = cos * hw;
        float az = sin * hw;
        float bx = -sin * hd;
        float bz = cos * hd;
        float u0 = sp.u0;
        float u1 = sp.u0 + sp.du;
        float v0 = sp.v0;
        float v1 = sp.v0 + sp.dv;
        v.addVertex(m, x - ax - bx, y, z - az - bz).setUv(u0, v1).setColor(color);
        v.addVertex(m, x + ax - bx, y, z + az - bz).setUv(u1, v1).setColor(color);
        v.addVertex(m, x + ax + bx, y, z + az + bz).setUv(u1, v0).setColor(color);
        v.addVertex(m, x - ax + bx, y, z - az + bz).setUv(u0, v0).setColor(color);
    }
}
