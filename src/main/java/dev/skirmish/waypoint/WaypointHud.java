package dev.skirmish.waypoint;

import dev.skirmish.gui.Texts;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.List;
import java.util.Locale;

/**
 * Waypoint labels are drawn on the HUD at the projected screen position of the waypoint
 * ({@code GameRenderer.projectPointToScreen}), so they show at any distance and do not depend on the world render
 * pipeline. The arrow at the top points to the selected waypoint relative to the player's yaw.
 */
final class WaypointHud implements HudElement {
    private static final int TEXT = 0xFFFFFFFF;
    private static final int BACKGROUND = 0x80000000;

    private final WaypointsModule module;
    private final WaypointManager manager;

    WaypointHud(WaypointsModule module, WaypointManager manager) {
        this.module = module;
        this.manager = manager;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (!module.isEnabled() || mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        List<Waypoint> here = manager.current();
        if (module.showLabels.get() && !here.isEmpty()) {
            renderLabels(mc, graphics, here);
        }
        Waypoint selected = manager.selected();
        if (module.showArrow.get() && selected != null && here.contains(selected)) {
            renderArrow(mc, graphics, mc.player, selected, tickCounter.getGameTimeDeltaPartialTick(true));
        }
    }

    private void renderLabels(Minecraft mc, GuiGraphics graphics, List<Waypoint> waypoints) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }
        Vec3 cam = camera.position();
        Vector3fc forward = camera.forwardVector();
        double maxDistance = module.maxLabelDistance.get();
        float scale = module.labelScale.getFloat();
        Font font = mc.font;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        Waypoint selected = manager.selected();

        for (Waypoint waypoint : waypoints) {
            Vec3 target = new Vec3(waypoint.x(), waypoint.y() + 1.0, waypoint.z());
            Vec3 rel = target.subtract(cam);
            double distance = rel.length();
            if (maxDistance > 0 && distance > maxDistance) {
                continue;
            }
            double depth = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
            if (depth < 0.1) {
                continue;
            }
            Vec3 ndc = mc.gameRenderer.projectPointToScreen(target);
            if (!Double.isFinite(ndc.x) || !Double.isFinite(ndc.y) || Math.abs(ndc.x) > 1.1 || Math.abs(ndc.y) > 1.1) {
                continue;
            }
            float sx = (float) ((ndc.x + 1.0) * 0.5 * width);
            float sy = (float) ((1.0 - ndc.y) * 0.5 * height);

            String name = waypoint.name();
            String dist = formatDistance(distance);
            int nameWidth = font.width(name);
            int distWidth = font.width(dist);
            int boxWidth = Math.max(nameWidth, distWidth) + 6;
            int color = 0xFF000000 | waypoint.color();

            graphics.pose().pushMatrix();
            graphics.pose().translate(sx, sy);
            graphics.pose().scale(scale, scale);
            int top = -font.lineHeight * 2 - 6;
            graphics.fill(-boxWidth / 2, top, boxWidth / 2, 0, BACKGROUND);
            if (waypoint.equals(selected)) {
                graphics.renderOutline(-boxWidth / 2 - 1, top - 1, boxWidth + 2, -top + 2, color);
            }
            graphics.drawString(font, name, -nameWidth / 2, top + 2, color, true);
            graphics.drawString(font, dist, -distWidth / 2, top + 3 + font.lineHeight, TEXT, true);
            graphics.fill(-1, 0, 1, 3, color);
            graphics.pose().popMatrix();
        }
    }

    private void renderArrow(Minecraft mc, GuiGraphics graphics, Player player, Waypoint waypoint, float partialTick) {
        Vec3 pos = player.getPosition(partialTick);
        double dx = waypoint.x() - pos.x;
        double dz = waypoint.z() - pos.z;
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float relative = Mth.wrapDegrees(targetYaw - player.getViewYRot(partialTick));
        double distance = Math.sqrt(waypoint.distanceSq(pos.x, pos.y, pos.z));

        int cx = graphics.guiWidth() / 2;
        int cy = module.arrowY.getInt();
        int color = 0xFF000000 | waypoint.color();

        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.pose().rotate((float) Math.toRadians(relative));
        // Stepped triangle (tip up) plus a shaft; rotated with the pose.
        for (int row = 0; row < 7; row++) {
            graphics.fill(-row, -10 + row, row + 1, -9 + row, color);
        }
        graphics.fill(-2, -3, 3, 9, color);
        graphics.pose().popMatrix();

        Font font = mc.font;
        String text = waypoint.name() + "  " + formatDistance(distance);
        int textWidth = font.width(text);
        graphics.fill(cx - textWidth / 2 - 3, cy + 12, cx + textWidth / 2 + 3, cy + 14 + font.lineHeight, BACKGROUND);
        graphics.drawString(font, text, cx - textWidth / 2, cy + 14, TEXT, true);
    }

    static String formatDistance(double distance) {
        if (distance >= 10_000) {
            return String.format(Locale.ROOT, "%.1f ", distance / 1000.0) + Texts.unit("km");
        }
        return Math.round(distance) + " " + Texts.unit("m");
    }
}
