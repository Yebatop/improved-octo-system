package dev.skirmish.studio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skirmish.fx.FxColor;
import dev.skirmish.fx.FxGeometry;
import dev.skirmish.fx.FxPipelines;
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
        pose.translate(0f, -1.0f, 0f);

        floor(pose, stage);
        bufferSource.endBatch();

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        FeatureRenderDispatcher features = mc.gameRenderer.getFeatureRenderDispatcher();
        CameraRenderState camera = new CameraRenderState();
        camera.orientation = new Quaternionf(view).conjugate().rotateY((float) Math.PI);
        AvatarRenderState me = stage.me(s.partialTick());
        if (me != null) {
            dispatcher.submit(me, camera, stage.meX(), stage.feetY(), 0.0, pose, features.getSubmitNodeStorage());
        }
        if (!stage.dummyGone()) {
            dispatcher.submit(stage.dummy(), camera, StudioStage.DUMMY_X, 0.0, 0.0, pose, features.getSubmitNodeStorage());
        }
        features.renderAllFeatures();

        Quaternionf back = new Quaternionf(view).conjugate();
        Vector3f right = back.transform(new Vector3f(1, 0, 0));
        Vector3f up = back.transform(new Vector3f(0, 1, 0));
        Vector3f forward = back.transform(new Vector3f(0, 0, 1));
        Matrix4f m = pose.last().pose();
        var live = stage.field.live();
        FxGeometry.draw(live, false, m, bufferSource.getBuffer(FxPipelines.paint()), 0, 0, 0, right, up, forward);
        FxGeometry.draw(live, true, m, bufferSource.getBuffer(FxPipelines.GLOW), 0, 0, 0, right, up, forward);

        float boltAge = stage.clock - stage.boltAt;
        if (boltAge >= 0 && boltAge < 0.45f) {
            float t = boltAge / 0.45f;
            float alpha = t < 0.15f ? 1f : t < 0.25f ? 0.35f : (1f - t) / 0.75f;
            pose.pushPose();
            pose.translate(StudioStage.DUMMY_X, 0, 0);
            BoltRenderer.geometry(pose.last().pose(), bufferSource.getBuffer(RenderTypes.lightning()), stage.boltSeed, 0.6f,
                    0.55f, 0.6f, 1f, 0.9f * alpha);
            pose.popPose();
        }
    }

    /** A round floor fading out at its edge, lines that slide back under a running model, and a glowing ring. */
    private void floor(PoseStack pose, StudioStage stage) {
        int[] c = StudioStage.colors(stage.scene);
        Matrix4f m = pose.last().pose();
        float radius = 2.9f;
        if ((c[2] >>> 24) > 0) {
            VertexConsumer v = bufferSource.getBuffer(FxPipelines.paint());
            int segments = 48;
            for (int i = 0; i < segments; i++) {
                double a0 = i * Math.PI * 2 / segments;
                double a1 = (i + 1) * Math.PI * 2 / segments;
                float x0 = (float) Math.cos(a0);
                float z0 = (float) Math.sin(a0);
                float x1 = (float) Math.cos(a1);
                float z1 = (float) Math.sin(a1);
                int mid = FxColor.alpha(c[2], 1f);
                int edge = FxColor.alpha(c[2], 0f);
                float r0 = radius * 0.62f;
                v.addVertex(m, 0, 0, 0).setColor(mid);
                v.addVertex(m, x0 * r0, 0, z0 * r0).setColor(mid);
                v.addVertex(m, x1 * r0, 0, z1 * r0).setColor(mid);
                v.addVertex(m, 0, 0, 0).setColor(mid);
                v.addVertex(m, x0 * r0, 0, z0 * r0).setColor(mid);
                v.addVertex(m, x0 * radius, 0, z0 * radius).setColor(edge);
                v.addVertex(m, x1 * radius, 0, z1 * radius).setColor(edge);
                v.addVertex(m, x1 * r0, 0, z1 * r0).setColor(mid);
            }
            // Grid lines along the run, sliding with the treadmill.
            if ((c[3] >>> 24) > 0) {
                double[] f = StudioStage.facing(StudioStage.ME_YAW);
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
                    // Across the run: lines perpendicular to the facing, moving backwards.
                    float cx = -fx * d;
                    float cz = -fz * d;
                    v.addVertex(m, cx - sx * half - fx * w, 0.002f, cz - sz * half - fz * w).setColor(col);
                    v.addVertex(m, cx + sx * half - fx * w, 0.002f, cz + sz * half - fz * w).setColor(col);
                    v.addVertex(m, cx + sx * half + fx * w, 0.002f, cz + sz * half + fz * w).setColor(col);
                    v.addVertex(m, cx - sx * half + fx * w, 0.002f, cz - sz * half + fz * w).setColor(col);
                }
            }
        }
        // The glowing rim.
        VertexConsumer g = bufferSource.getBuffer(FxPipelines.GLOW);
        int rim = c[4];
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2 / segments;
            double a1 = (i + 1) * Math.PI * 2 / segments;
            float x0 = (float) Math.cos(a0);
            float z0 = (float) Math.sin(a0);
            float x1 = (float) Math.cos(a1);
            float z1 = (float) Math.sin(a1);
            float r = radius * 0.78f;
            float w = 0.05f;
            int on = FxColor.alpha(rim, 0.55f);
            int off = FxColor.alpha(rim, 0f);
            g.addVertex(m, x0 * (r - w), 0.004f, z0 * (r - w)).setColor(off);
            g.addVertex(m, x0 * r, 0.004f, z0 * r).setColor(on);
            g.addVertex(m, x1 * r, 0.004f, z1 * r).setColor(on);
            g.addVertex(m, x1 * (r - w), 0.004f, z1 * (r - w)).setColor(off);
            g.addVertex(m, x0 * r, 0.004f, z0 * r).setColor(on);
            g.addVertex(m, x0 * (r + w * 3), 0.004f, z0 * (r + w * 3)).setColor(off);
            g.addVertex(m, x1 * (r + w * 3), 0.004f, z1 * (r + w * 3)).setColor(off);
            g.addVertex(m, x1 * r, 0.004f, z1 * r).setColor(on);
        }
    }
}
