package dev.skirmish.module.killfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skirmish.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * A lightning bolt drawn only on my screen over a victim: the vanilla bolt's shape (a jagged main strand and two
 * branches, four nested glow layers, {@code RenderTypes.lightning()}), shorter and fading out. No entity is created,
 * nothing is sent, no sound or sky flash is caused; it is geometry submitted from {@code BEFORE_ENTITIES}.
 */
final class BoltRenderer {
    private static final String L = "layout.kill_fx.";

    private record Bolt(double x, double y, double z, long seed, long bornMs) {
    }

    private final List<Bolt> bolts = new ArrayList<>();

    void strike(double x, double y, double z, long now) {
        bolts.add(new Bolt(x, y, z, RandomSource.create().nextLong(), now));
        while (bolts.size() > 4) {
            bolts.removeFirst();
        }
    }

    boolean isEmpty() {
        return bolts.isEmpty();
    }

    void clear() {
        bolts.clear();
    }

    void render(WorldRenderContext context) {
        Theme theme = Theme.get();
        long now = System.currentTimeMillis();
        long life = Math.round(theme.num(L + "bolt_ms"));
        bolts.removeIf(b -> now - b.bornMs() >= life);
        Vec3 cam = context.worldState().cameraRenderState.pos;
        PoseStack pose = context.matrices();
        float segment = theme.num(L + "bolt_height") / 8f;
        int color = theme.color("kfx_bolt");
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float baseAlpha = ((color >>> 24) & 0xFF) / 255f;
        for (Bolt bolt : bolts) {
            float t = (now - bolt.bornMs()) / (float) life;
            // Two quick flashes like the vanilla bolt, then a fade.
            float alpha = baseAlpha * (t < 0.15f ? 1f : t < 0.25f ? 0.35f : (1f - t) / 0.75f);
            pose.pushPose();
            pose.translate(bolt.x() - cam.x, bolt.y() - cam.y, bolt.z() - cam.z);
            long seed = bolt.seed();
            context.commandQueue().submitCustomGeometry(pose, RenderTypes.lightning(),
                    (p, consumer) -> geometry(p.pose(), consumer, seed, segment, r, g, b, alpha));
            pose.popPose();
        }
    }

    /** The vanilla LightningBoltRenderer geometry with a configurable segment height (vanilla: 16 blocks). */
    private static void geometry(Matrix4f matrix, VertexConsumer consumer, long seed, float segment, float r, float g, float b, float a) {
        float[] xs = new float[8];
        float[] zs = new float[8];
        float x = 0f;
        float z = 0f;
        RandomSource shape = RandomSource.create(seed);
        for (int i = 7; i >= 0; i--) {
            xs[i] = x;
            zs[i] = z;
            x += shape.nextInt(11) - 5;
            z += shape.nextInt(11) - 5;
        }
        float endX = x;
        float endZ = z;
        float jitter = segment / 16f;
        for (int layer = 0; layer < 4; layer++) {
            RandomSource random = RandomSource.create(seed);
            for (int branch = 0; branch < 3; branch++) {
                int top = branch > 0 ? 7 - branch : 7;
                int bottom = branch > 0 ? top - 2 : 0;
                float hx = (xs[top] - endX) * jitter;
                float hz = (zs[top] - endZ) * jitter;
                for (int n = top; n >= bottom; n--) {
                    float px = hx;
                    float pz = hz;
                    if (branch == 0) {
                        hx += (random.nextInt(11) - 5) * jitter;
                        hz += (random.nextInt(11) - 5) * jitter;
                    } else {
                        hx += (random.nextInt(31) - 15) * jitter;
                        hz += (random.nextInt(31) - 15) * jitter;
                    }
                    float w0 = 0.1f + layer * 0.2f;
                    float w1 = w0;
                    if (branch == 0) {
                        w0 *= n * 0.1f + 1.0f;
                        w1 *= (n - 1.0f) * 0.1f + 1.0f;
                    }
                    quad(matrix, consumer, hx, hz, n, px, pz, segment, r, g, b, a, w0, w1, false, false, true, false);
                    quad(matrix, consumer, hx, hz, n, px, pz, segment, r, g, b, a, w0, w1, true, false, true, true);
                    quad(matrix, consumer, hx, hz, n, px, pz, segment, r, g, b, a, w0, w1, true, true, false, true);
                    quad(matrix, consumer, hx, hz, n, px, pz, segment, r, g, b, a, w0, w1, false, true, false, false);
                }
            }
        }
    }

    private static void quad(Matrix4f m, VertexConsumer c, float x0, float z0, int n, float x1, float z1, float segment,
                             float r, float g, float b, float a, float w0, float w1,
                             boolean s1, boolean s2, boolean s3, boolean s4) {
        c.addVertex(m, x0 + (s1 ? w1 : -w1), n * segment, z0 + (s2 ? w1 : -w1)).setColor(r, g, b, a);
        c.addVertex(m, x1 + (s1 ? w0 : -w0), (n + 1) * segment, z1 + (s2 ? w0 : -w0)).setColor(r, g, b, a);
        c.addVertex(m, x1 + (s3 ? w0 : -w0), (n + 1) * segment, z1 + (s4 ? w0 : -w0)).setColor(r, g, b, a);
        c.addVertex(m, x0 + (s3 ? w1 : -w1), n * segment, z0 + (s4 ? w1 : -w1)).setColor(r, g, b, a);
    }
}
