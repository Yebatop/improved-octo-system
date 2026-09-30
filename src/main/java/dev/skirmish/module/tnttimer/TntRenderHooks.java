package dev.skirmish.module.tnttimer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skirmish.debug.DebugLog;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.item.PrimedTnt;

/**
 * Called from {@code TntRendererMixin}: the label is computed while vanilla extracts the TNT's render state and
 * attached to it ({@link net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState}), then submitted in the
 * TNT's own submit pass as depth-tested text ({@code Font.DisplayMode.NORMAL}, never see-through), with the optional
 * ring as depth-tested lines.
 */
public final class TntRenderHooks {
    private static final RenderStateDataKey<TntTimerModule.Label> LABEL = RenderStateDataKey.create(() -> "skirmish:tnt_timer_label");
    /** Vanilla nametag scale and lift above the attachment point ({@code NameTagFeatureRenderer.Storage.add}). */
    private static final float TEXT_SCALE = 0.025F;
    private static final float LABEL_LIFT = 0.5F;
    /** One nametag line in world units, when a vanilla nametag already sits there. */
    private static final float LINE_STEP = 10.0F * TEXT_SCALE;
    private static final int RING_SEGMENTS = 64;
    private static final float RING_LIFT = 0.05F;
    private static boolean failureLogged;

    private TntRenderHooks() {
    }

    public static void extract(PrimedTnt tnt, TntRenderState state, float partialTick) {
        try {
            TntTimerModule module = TntTimerModule.instance();
            state.setData(LABEL, module == null ? null : module.label(tnt, partialTick));
        } catch (Throwable t) {
            state.setData(LABEL, null);
            fail(t);
        }
    }

    public static void submit(TntRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TntTimerModule.Label label = state.getData(LABEL);
        if (label == null) {
            return;
        }
        try {
            submitText(state, label, poseStack, collector, camera);
            if (label.ring() != null) {
                submitRing(label.ring(), label.ringColor(), poseStack, collector);
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static void submitText(TntRenderState state, TntTimerModule.Label label, PoseStack poseStack,
                                   SubmitNodeCollector collector, CameraRenderState camera) {
        Font font = Minecraft.getInstance().font;
        FormattedCharSequence text = label.text().getVisualOrderText();
        float lift = state.boundingBoxHeight + LABEL_LIFT + (state.nameTag != null ? LINE_STEP : 0f);
        int background = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        poseStack.pushPose();
        poseStack.translate(0.0F, lift, 0.0F);
        poseStack.mulPose(camera.orientation);
        poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        collector.submitText(poseStack, -font.width(text) / 2.0F, 0.0F, text, false, Font.DisplayMode.NORMAL,
                LightTexture.FULL_BRIGHT, 0xFFFFFFFF, background, 0);
        poseStack.popPose();
    }

    /** A flat circle (or square for cubic blasts) at the TNT's feet, depth-tested like block outlines. */
    private static void submitRing(TntTable.Ring ring, int color, PoseStack poseStack, SubmitNodeCollector collector) {
        float r = (float) ring.radius();
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, consumer) -> {
            if (ring.square()) {
                float[][] corners = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
                for (int i = 0; i < 4; i++) {
                    float[] a = corners[i];
                    float[] b = corners[(i + 1) % 4];
                    segment(consumer, pose, a[0], a[1], b[0], b[1], color);
                }
                return;
            }
            for (int i = 0; i < RING_SEGMENTS; i++) {
                double a0 = Math.PI * 2 * i / RING_SEGMENTS;
                double a1 = Math.PI * 2 * (i + 1) / RING_SEGMENTS;
                segment(consumer, pose, r * (float) Math.cos(a0), r * (float) Math.sin(a0),
                        r * (float) Math.cos(a1), r * (float) Math.sin(a1), color);
            }
        });
    }

    private static void segment(VertexConsumer consumer, PoseStack.Pose pose, float x0, float z0, float x1, float z1, int color) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len <= 0f) {
            return;
        }
        consumer.addVertex(pose, x0, RING_LIFT, z0).setColor(color).setNormal(pose, dx / len, 0f, dz / len).setLineWidth(2.0F);
        consumer.addVertex(pose, x1, RING_LIFT, z1).setColor(color).setNormal(pose, dx / len, 0f, dz / len).setLineWidth(2.0F);
    }

    private static void fail(Throwable t) {
        if (!failureLogged) {
            failureLogged = true;
            DebugLog.error(TntTimerModule.ID, "TNT label failed", t);
        }
    }
}
