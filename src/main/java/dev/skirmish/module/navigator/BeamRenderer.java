package dev.skirmish.module.navigator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skirmish.module.sky.SkyPipelines;
import dev.skirmish.ui.Theme;
import dev.skirmish.waypoint.Waypoint;
import dev.skirmish.waypoint.WaypointManager;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Light beams, like a beacon's, over the route target (or every waypoint) and the next portal, in the waypoint's
 * colour: a translucent column (so the colour shows against a bright day sky) with an additive bright centre.
 * Depth-tested, so terrain in front hides them; beyond the render distance or the fog (the Nether's is thick) a beam
 * is drawn at the edge of what you can see, in the right direction, so it still shows the way.
 */
final class BeamRenderer {
    private static final String L = NavigatorModule.L;

    private BeamRenderer() {
    }

    private record Beam(double x, double y, double z, int rgb) {
    }

    /** A beam in camera space: position, the camera-facing right vector, base height, core half-width. */
    private record Placed(float x, float z, float rx, float rz, float base, float core, int rgb) {
    }

    static void render(NavigatorModule module, WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Theme t = Theme.get();
        List<Beam> beams = new ArrayList<>();
        Waypoint selected = WaypointManager.get().selected();
        for (Waypoint wp : WaypointManager.get().current()) {
            boolean sel = selected != null && selected.id().equals(wp.id());
            if (sel || module.beams.get() == NavigatorModule.Beams.ALL) {
                beams.add(new Beam(wp.x(), wp.y(), wp.z(), wp.color()));
            }
        }
        double[] next = module.nextPoint();
        if (next != null && next[2] == 1) {
            beams.add(new Beam(next[0], mc.player.getY() - 4, next[1], t.color("nav_portal")));
        }
        if (beams.isEmpty()) {
            return;
        }
        Vec3 cam = context.worldState().cameraRenderState.pos;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float fogEnd = mc.gameRenderer.getMainCamera().attributeProbe().getValue(EnvironmentAttributes.FOG_END_DISTANCE, partial);
        float maxDist = Math.min(mc.gameRenderer.getRenderDistance(), fogEnd) * t.num(L + "beam_clamp");
        float height = t.num(L + "beam_height");
        float pulse = 0.85f + 0.15f * (float) Math.sin(Util.getMillis() / 400.0);
        PoseStack pose = context.matrices();
        List<Placed> placed = new ArrayList<>();
        for (Beam b : beams) {
            double dx = b.x() - cam.x;
            double dz = b.z() - cam.z;
            double d = Math.hypot(dx, dz);
            if (d < 1.5) {
                continue;
            }
            double k = d > maxDist ? maxDist / d : 1.0;
            float core = (float) Math.max(t.num(L + "beam_core"), d * k * t.num(L + "beam_core_per_block"));
            placed.add(new Placed((float) (dx * k), (float) (dz * k), (float) (-dz / d), (float) (dx / d),
                    (float) (b.y() - cam.y), core, b.rgb()));
        }
        if (placed.isEmpty()) {
            return;
        }
        float glowFactor = t.num(L + "beam_glow_factor");
        int glowAlpha = Math.round(t.num(L + "beam_glow_alpha") * pulse);
        int coreAlpha = Math.round(t.num(L + "beam_core_alpha") * pulse);
        int shineAlpha = Math.round(t.num(L + "beam_shine_alpha") * pulse);
        context.commandQueue().submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, c) -> {
            for (Placed b : placed) {
                column(c, p, b, height, b.core() * glowFactor, b.rgb(), glowAlpha);
                column(c, p, b, height, b.core(), b.rgb(), coreAlpha);
            }
        });
        context.commandQueue().submitCustomGeometry(pose, SkyPipelines.SKY, (p, c) -> {
            for (Placed b : placed) {
                column(c, p, b, height, b.core() * 0.4f, 0xFFFFFF & (b.rgb() | 0x606060), shineAlpha);
            }
        });
    }

    /** A camera-facing vertical strip from {@code base} up {@code height}, fading to the top. */
    private static void column(VertexConsumer c, PoseStack.Pose p, Placed b, float height, float half, int rgb, int alpha) {
        float x = b.x();
        float z = b.z();
        float rx = b.rx();
        float rz = b.rz();
        float base = b.base();
        float mid = base + height * 0.15f;
        float top = base + height;
        int a0 = (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
        int a1 = (Math.max(0, Math.min(255, alpha * 3 / 4)) << 24) | (rgb & 0xFFFFFF);
        int a2 = rgb & 0xFFFFFF;
        quad(c, p, x, z, rx, rz, half, base, a0, mid, a1);
        quad(c, p, x, z, rx, rz, half, mid, a1, top, a2);
    }

    private static void quad(VertexConsumer c, PoseStack.Pose p, float x, float z, float rx, float rz, float half,
                             float y0, int c0, float y1, int c1) {
        c.addVertex(p, x - rx * half, y0, z - rz * half).setColor(c0);
        c.addVertex(p, x + rx * half, y0, z + rz * half).setColor(c0);
        c.addVertex(p, x + rx * half, y1, z + rz * half).setColor(c1);
        c.addVertex(p, x - rx * half, y1, z - rz * half).setColor(c1);
    }
}
