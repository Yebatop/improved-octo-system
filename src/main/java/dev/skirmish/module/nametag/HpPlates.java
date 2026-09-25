package dev.skirmish.module.nametag;

import dev.skirmish.module.friends.FriendsModule;
import dev.skirmish.module.survival.PvpState;
import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Icons;
import dev.skirmish.ui.Ui;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skirmish-style HP plates: for each player in plain line of sight (checked each tick with a block ray, glass and
 * other see-through blocks don't block it), a plate is drawn at the projected point above the head: face, nick
 * (friend colour), HP with a heart and absorption, and a bar coloured red → amber → green with a damage trail
 * ({@link HpTrail}). The plate shrinks with distance; far ones are drawn first.
 */
final class HpPlates implements HudElement {
    private static final String L = "layout.nametag.";
    private final NametagHpModule module;
    private final Map<Integer, HpTrail> trails = new HashMap<>();
    private Set<Integer> shown = Set.of();

    HpPlates(NametagHpModule module) {
        this.module = module;
    }

    boolean shows(int entityId) {
        return shown.contains(entityId);
    }

    void clear() {
        shown = Set.of();
        trails.clear();
    }

    /** Picks the players that get a plate (line of sight is a block ray, so it runs per tick, not per frame). */
    void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!module.isEnabled() || !module.skirmishStyle() || mc.player == null || mc.level == null) {
            clear();
            return;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().isInitialized() ? mc.gameRenderer.getMainCamera().position() : mc.player.getEyePosition();
        double max = module.distance.get();
        boolean allowInvisible = module.invisible.get();
        Set<Integer> out = new HashSet<>();
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) {
                continue;
            }
            boolean opponent = module.show.get() != NametagPolicy.Show.ALWAYS && PvpState.fightingWith(player.getUUID());
            boolean near = player.position().distanceTo(eye) <= max;
            boolean invisible = player.isInvisibleTo(mc.player);
            if (!NametagPolicy.showPlate(true, near, invisible, allowInvisible, module.show.get(), module.inPvp(), opponent)) {
                continue;
            }
            if (lineOfSight(mc, eye, player)) {
                out.add(player.getId());
            }
        }
        shown = out;
        trails.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    private static boolean lineOfSight(Minecraft mc, Vec3 from, Entity target) {
        double h = target.getBbHeight();
        for (double f : new double[]{0.9, 0.55, 0.15}) {
            Vec3 to = target.position().add(0, h * f, 0);
            HitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    private record Plate(AbstractClientPlayer player, float x, float y, double distance) {
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (shown.isEmpty() || mc.options.hideGui || mc.level == null || mc.player == null) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(true);
        Vec3 cam = camera.position();
        Vector3fc forward = camera.forwardVector();
        Ui ui = Ui.begin(graphics);
        try {
            List<Plate> plates = new ArrayList<>();
            for (int id : shown) {
                if (!(mc.level.getEntity(id) instanceof AbstractClientPlayer player)) {
                    continue;
                }
                Vec3 head = player.getPosition(partial).add(0, player.getBbHeight() + ui.num(L + "plate_lift"), 0);
                Vec3 rel = head.subtract(cam);
                if (rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z() < 0.1) {
                    continue;
                }
                Vec3 ndc = mc.gameRenderer.projectPointToScreen(head);
                if (!Double.isFinite(ndc.x) || !Double.isFinite(ndc.y) || Math.abs(ndc.x) > 1.2 || Math.abs(ndc.y) > 1.2) {
                    continue;
                }
                plates.add(new Plate(player, (float) ((ndc.x + 1.0) * 0.5 * ui.width()), (float) ((1.0 - ndc.y) * 0.5 * ui.height()),
                        rel.length()));
            }
            plates.sort(Comparator.comparingDouble(Plate::distance).reversed());
            long now = Util.getMillis();
            for (Plate plate : plates) {
                draw(ui, plate, now);
            }
        } finally {
            ui.end();
        }
    }

    /** Plate scale for a distance: full size up to {@code plate_near} blocks, down to {@code plate_min_scale} at far. */
    static float distanceScale(double distance, float near, float far, float min) {
        if (distance <= near) {
            return 1f;
        }
        float t = (float) Math.min(1.0, (distance - near) / Math.max(0.001, far - near));
        return 1f + (min - 1f) * t;
    }

    private void draw(Ui ui, Plate plate, long now) {
        AbstractClientPlayer player = plate.player();
        Minecraft mc = Minecraft.getInstance();
        float fraction = HpText.fraction(player.getHealth(), player.getMaxHealth());
        HpTrail trail = trails.computeIfAbsent(player.getId(), id -> new HpTrail()).update(fraction, now);
        float fill = trail.fill(now);
        float trailEnd = trail.trail(now);
        float flash = trail.hitFlash(now);
        boolean invisible = player.isInvisibleTo(mc.player);

        float scale = module.scale.getFloat() / 100f
                * distanceScale(plate.distance(), ui.num(L + "plate_near"), ui.num(L + "plate_far"), ui.num(L + "plate_min_scale"));
        float stroke = ui.num("stroke.width");
        float padX = ui.num(L + "plate_pad_x");
        float padY = ui.num(L + "plate_pad_y");
        float faceSize = module.face.get() ? ui.num(L + "plate_face") : 0f;
        float gap = ui.num(L + "plate_gap");
        float heart = ui.num(L + "plate_heart");
        char separator = mc.options.languageCode.startsWith("en") ? '.' : ',';
        String value = HpText.value(player.getHealth(), module.units.get(), separator);
        String extra = module.absorption.get() ? HpText.absorption(player.getAbsorptionAmount(), module.units.get(), separator) : "";
        String name = ui.ellipsize("hp_name", player.getGameProfile().name(), ui.num(L + "plate_name_max"));

        float valueW = ui.textWidth("hp_value", value) + ui.num(L + "plate_value_gap") + heart
                + (extra.isEmpty() ? 0f : ui.num(L + "plate_value_gap") + ui.textWidth("hp_abs", extra));
        float rowH = Math.max(ui.lineHeight("hp_name"), ui.lineHeight("hp_value"));
        float barH = ui.num(L + "plate_bar");
        float textBlockH = rowH + ui.num(L + "plate_bar_gap") + barH;
        float innerH = Math.max(faceSize, textBlockH);
        float textW = Math.max(ui.num(L + "plate_min_text"), ui.textWidth("hp_name", name) + gap * 2 + valueW);
        float w = Math.round(stroke * 2 + padX * 2 + faceSize + (faceSize > 0 ? gap : 0f) + textW);
        float h = Math.round(stroke * 2 + padY * 2 + innerH);
        float pointer = ui.num(L + "plate_pointer");

        var pose = ui.graphics().pose();
        pose.pushMatrix();
        pose.translate(Math.round(plate.x()), Math.round(plate.y()));
        pose.scale(scale, scale);
        ui.pushAlpha(invisible ? ui.num(L + "plate_invisible_alpha") : 1f);
        try {
            float x = -Math.round(w / 2f);
            float y = -h - pointer;
            int border = Anim.lerpColor(ui.color("stroke_10"), ui.color("bad"), flash);
            ui.box(x, y, w, h, ui.num(L + "plate_radius"), ui.color("panel"), border);
            ui.triangle(-pointer, y + h - stroke, pointer, y + h - stroke, 0, y + h + pointer - stroke, 0f, ui.color("panel"));

            float cx = x + stroke + padX;
            float cy = y + stroke + padY;
            if (faceSize > 0) {
                float fy = cy + (innerH - faceSize) / 2f;
                var connection = mc.getConnection();
                var info = connection == null ? null : connection.getPlayerInfo(player.getUUID());
                if (info != null) {
                    PlayerFaceRenderer.draw(ui.graphics(), info.getSkin(), Math.round(cx), Math.round(fy), Math.round(faceSize));
                    ui.cornerMask(Math.round(cx), Math.round(fy), faceSize, faceSize, ui.num(L + "plate_face_radius"), ui.color("panel"));
                } else {
                    ui.rect(cx, fy, faceSize, faceSize, ui.num(L + "plate_face_radius"), ui.color("slot_empty"));
                }
                cx += faceSize + gap;
            }
            float ty = cy + (innerH - textBlockH) / 2f;
            int friend = FriendsModule.nametagColor(player);
            int nameColor = friend >= 0 ? 0xFF000000 | friend : ui.color("text");
            float after = ui.textCentered("hp_name", name, cx, ty, rowH, nameColor);
            if (invisible) {
                float icon = heart;
                Icons.eyeOff(ui, after + gap / 2f, ty + (rowH - icon) / 2f, icon, ui.color("text_3"));
            }

            int hpColor = HpText.color(fraction, ui.color("bad"), ui.color("warn"), ui.color("good"));
            float vx = cx + textW - valueW;
            vx = ui.textCentered("hp_value", value, vx, ty, rowH, hpColor) + ui.num(L + "plate_value_gap");
            heart(ui, vx, ty + (rowH - heart) / 2f, heart, hpColor);
            vx += heart;
            if (!extra.isEmpty()) {
                ui.textCentered("hp_abs", extra, vx + ui.num(L + "plate_value_gap"), ty, rowH, ui.color("nametag_absorption"));
            }

            // Bar: track, damage trail, fill, absorption on top.
            float by = ty + rowH + ui.num(L + "plate_bar_gap");
            float r = barH / 2f;
            ui.rect(cx, by, textW, barH, r, ui.color("hp_track"));
            if (trailEnd > fill) {
                ui.rect(cx, by, Math.max(barH, textW * trailEnd), barH, r, ui.color("nametag_trail"));
            }
            if (fill > 0f) {
                ui.rect(cx, by, Math.max(barH, textW * fill), barH, r, hpColor);
            }
            float abs = player.getMaxHealth() > 0 ? Math.min(1f, player.getAbsorptionAmount() / player.getMaxHealth()) : 0f;
            if (module.absorption.get() && abs > 0.01f) {
                ui.rect(cx, by, Math.max(barH, textW * abs), barH / 2f, r / 2f, ui.color("nametag_absorption"));
            }
        } finally {
            ui.popAlpha();
            pose.popMatrix();
        }
    }

    /** Filled heart: two circles and a triangle in a {@code size} box. */
    private static void heart(Ui ui, float x, float y, float size, int color) {
        float d = size * 0.56f;
        ui.circle(x + size * 0.28f, y + size * 0.34f, d, color);
        ui.circle(x + size * 0.72f, y + size * 0.34f, d, color);
        ui.triangle(x + size * 0.02f, y + size * 0.42f, x + size * 0.98f, y + size * 0.42f, x + size * 0.5f, y + size * 0.96f, 0f, color);
    }
}
