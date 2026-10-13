package dev.skirmish.module.effects;

import com.google.common.collect.Ordering;
import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.List;

/**
 * List of the local player's active effects. Default place: right of the hotbar (past vanilla's hotbar attack
 * indicator), growing upwards, clear of the chat (left) and the scoreboard (right edge); mirrors the armor HUD.
 */
final class EffectHud extends HudBlock {
    private static final String L = "layout.qol.";
    /** Vanilla's effect sprites are 18×18. */
    private static final float SPRITE = 18f;

    private final EffectHudModule module;
    private List<Row> current = List.of();
    private List<Row> last = List.of();

    private record Row(Holder<MobEffect> effect, String name, String time, boolean harmful, boolean expiring) {
    }

    EffectHud(EffectHudModule module) {
        super("effects", "skirmish.hud.element.effects", new Placement(0.5f, 1f, 0f, 1f, 252, -8));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled();
    }

    @Override
    public boolean shown() {
        update(false);
        return !current.isEmpty();
    }

    @Override
    public boolean hasContent() {
        return !last.isEmpty();
    }

    @Override
    public void update(boolean preview) {
        LocalPlayer player = Minecraft.getInstance().player;
        current = player == null ? List.of() : read(player);
        if (!current.isEmpty()) {
            last = current;
        }
    }

    private List<Row> read(LocalPlayer player) {
        if (player.getActiveEffects().isEmpty()) {
            return List.of();
        }
        int warnTicks = module.warnSeconds.getInt() * 20;
        List<Row> rows = new ArrayList<>();
        for (MobEffectInstance instance : Ordering.natural().reverse().sortedCopy(player.getActiveEffects())) {
            if (!instance.showIcon() || (instance.isAmbient() && !module.showAmbient.get())) {
                continue;
            }
            rows.add(row(instance, warnTicks));
        }
        return rows;
    }

    private static Row row(MobEffectInstance instance, int warnTicks) {
        Holder<MobEffect> effect = instance.getEffect();
        String level = EffectText.level(instance.getAmplifier());
        String name = effect.value().getDisplayName().getString() + (level.isEmpty() ? "" : " " + level);
        boolean infinite = instance.isInfiniteDuration();
        boolean expiring = !infinite && warnTicks > 0 && instance.getDuration() <= warnTicks;
        return new Row(effect, name, EffectText.duration(instance.getDuration(), infinite), !effect.value().isBeneficial(), expiring);
    }

    private List<Row> rows(boolean preview) {
        if (preview && current.isEmpty() || last.isEmpty()) {
            int warn = module.warnSeconds.getInt() * 20;
            return List.of(
                    row(new MobEffectInstance(MobEffects.STRENGTH, 20 * 95, 1), warn),
                    row(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 470), warn),
                    row(new MobEffectInstance(MobEffects.POISON, 20 * 6), warn));
        }
        return last;
    }

    private static float rowHeight(Ui ui) {
        return Math.max(ui.num(L + "effect_icon"), Math.max(ui.lineHeight("qol_effect_name"), ui.lineHeight("qol_effect_time")));
    }

    @Override
    public float width(Ui ui, boolean preview) {
        float name = 0f;
        float time = 0f;
        for (Row row : rows(preview)) {
            name = Math.max(name, ui.textWidth("qol_effect_name", row.name()));
            time = Math.max(time, ui.textWidth("qol_effect_time", row.time()));
        }
        float content = ui.num(L + "effect_icon") + ui.num(L + "effect_icon_gap") + name + ui.num(L + "effect_time_gap") + time;
        return HudStyle.insetX(ui) * 2 + Math.max(ui.num(L + "effect_min_width"), content);
    }

    @Override
    public float height(Ui ui, boolean preview) {
        int n = rows(preview).size();
        return HudStyle.insetY(ui) * 2 + n * rowHeight(ui) + Math.max(0, n - 1) * ui.num(L + "effect_row_gap");
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        float w = width(ui, preview);
        HudStyle.panel(ui, x, y, w, height(ui, preview));
        float icon = ui.num(L + "effect_icon");
        float rh = rowHeight(ui);
        float left = x + HudStyle.insetX(ui);
        float right = x + w - HudStyle.insetX(ui);
        float ry = y + HudStyle.insetY(ui);
        for (Row row : rows(preview)) {
            drawIcon(ui, row.effect(), left, ry + (rh - icon) / 2f, icon);
            float tx = left + icon + ui.num(L + "effect_icon_gap");
            ui.textCentered("qol_effect_name", row.name(), tx, ry, rh, ui.color(row.harmful() ? "bad" : "text"));
            float timeW = ui.textWidth("qol_effect_time", row.time());
            ui.textCentered("qol_effect_time", row.time(), right - timeW, ry, rh, ui.color(row.expiring() ? "warn" : "text_2"));
            ry += rh + ui.num(L + "effect_row_gap");
        }
    }

    private static void drawIcon(Ui ui, Holder<MobEffect> effect, float x, float y, float size) {
        var pose = ui.graphics().pose();
        pose.pushMatrix();
        pose.translate(Math.round(x), Math.round(y));
        pose.scale(size / SPRITE, size / SPRITE);
        ui.graphics().blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(effect), 0, 0, (int) SPRITE, (int) SPRITE,
                ARGB.white(ui.alpha()));
        pose.popMatrix();
    }
}
