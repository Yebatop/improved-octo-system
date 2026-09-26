package dev.skirmish.module.evtimers;

import dev.skirmish.ui.Ui;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.Map;

/**
 * Countdown pills over Pandora Box chests: seconds left of the chest's 10 s life with a draining bar, green → amber
 * → red. Projected from the world like waypoint labels; text stays at the HUD's own size at any distance.
 */
final class PandoraLabels implements HudElement {
    static final long LIFE_MS = 10_000L;
    private static final String L = EventTimersModule.L;
    private final EventTimersModule module;

    PandoraLabels(EventTimersModule module) {
        this.module = module;
    }

    /** Seconds left with one decimal, never below 0 ("7,3" in Russian). */
    static String secondsText(long leftMs) {
        return Ui.decimal(Math.max(0, leftMs) / 1000.0, 1);
    }

    /** Colour token for the time left: good above 5 s, warn above 2 s, then bad. */
    static String tone(long leftMs) {
        return leftMs > 5_000 ? "good" : leftMs > 2_000 ? "warn" : "bad";
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Map<BlockPos, Long> chests = module.chests();
        if (!module.isEnabled() || chests.isEmpty() || mc.options.hideGui || mc.player == null) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }
        Vec3 cam = camera.position();
        Vector3fc forward = camera.forwardVector();
        long now = Util.getMillis();
        Ui ui = Ui.begin(graphics);
        try {
            for (Map.Entry<BlockPos, Long> e : chests.entrySet()) {
                BlockPos pos = e.getKey();
                Vec3 at = new Vec3(pos.getX() + 0.5, pos.getY() + ui.num(L + "pandora_lift"), pos.getZ() + 0.5);
                Vec3 rel = at.subtract(cam);
                if (rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z() < 0.1) {
                    continue;
                }
                Vec3 ndc = mc.gameRenderer.projectPointToScreen(at);
                if (!Double.isFinite(ndc.x) || !Double.isFinite(ndc.y) || Math.abs(ndc.x) > 1.2 || Math.abs(ndc.y) > 1.2) {
                    continue;
                }
                float sx = (float) ((ndc.x + 1.0) * 0.5 * ui.width());
                float sy = (float) ((1.0 - ndc.y) * 0.5 * ui.height());
                draw(ui, sx, sy, LIFE_MS - (now - e.getValue()));
            }
        } finally {
            ui.end();
        }
    }

    private static void draw(Ui ui, float cx, float bottom, long leftMs) {
        String text = secondsText(leftMs);
        int tone = ui.color(tone(leftMs));
        float padX = ui.num(L + "pandora_pad_x");
        float padY = ui.num(L + "pandora_pad_y");
        float bar = ui.num(L + "pandora_bar");
        float w = Math.max(ui.num(L + "pandora_min_width"), ui.textWidth("ev_pandora", text) + padX * 2);
        float h = padY * 2 + ui.lineHeight("ev_pandora") + bar + ui.num(L + "pandora_bar_gap");
        float x = Math.round(cx - w / 2f);
        float y = Math.round(bottom - h);
        ui.box(x, y, w, h, ui.theme().radius("tile"), ui.color("panel"), tone);
        ui.text("ev_pandora", text, x + (w - ui.textWidth("ev_pandora", text)) / 2f, y + padY, tone);
        float by = y + padY + ui.lineHeight("ev_pandora") + ui.num(L + "pandora_bar_gap");
        float bw = w - padX * 2;
        float frac = Math.max(0f, Math.min(1f, leftMs / (float) LIFE_MS));
        ui.rect(x + padX, by, bw, bar, bar / 2f, ui.color("track"));
        ui.rect(x + padX, by, bw * frac, bar, bar / 2f, tone);
    }
}
