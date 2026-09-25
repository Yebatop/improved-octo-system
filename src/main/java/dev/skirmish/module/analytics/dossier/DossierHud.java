package dev.skirmish.module.analytics.dossier;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.module.analytics.AnalyticsText;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * «Вы против Nick 3–1 / последний бой 2 ч назад · 7 боёв» (+ the gear they wore last time, outside a fight). Sits right
 * under the target card by default, at its width; takes the card's place while the card is hidden.
 */
final class DossierHud extends HudBlock {
    private static final String L = "layout.analytics.dossier.";

    private final DossierModule module;
    private @Nullable UUID uuid;
    private String name = "";
    private @Nullable DossierRecord record;
    private boolean inFight;
    private List<ItemStack> gear = List.of();

    DossierHud(DossierModule module) {
        super("dossier", "skirmish.hud.element.dossier", new Placement(0.5f, 0.5f, 0f, 0f, dev.skirmish.ui.Theme.get().num("layout.hud.target_side_dx"), dev.skirmish.ui.Theme.get().num("layout.hud.target_side_dy")));
        this.module = module;
    }

    @Override
    public @Nullable String stackUnder() {
        return "target";
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.hud.get();
    }

    @Override
    public void update(boolean preview) {
        UUID now = module.shown();
        if (now != null) {
            if (!now.equals(uuid)) {
                gear = List.of();
            }
            uuid = now;
            name = module.shownName();
            inFight = module.shownInFight();
            record = module.record(now);
            gear = record == null || inFight || !module.showGear.get() ? List.of() : stacks(record.gear());
        }
    }

    private static List<ItemStack> stacks(List<String> ids) {
        List<ItemStack> out = new ArrayList<>();
        for (String id : ids) {
            Identifier key = id.isEmpty() ? null : Identifier.tryParse(id);
            Item item = key == null ? null : BuiltInRegistries.ITEM.getValue(key);
            if (item != null && item != Items.AIR) {
                out.add(new ItemStack(item));
            }
        }
        return out;
    }

    @Override
    public boolean shown() {
        update(false);
        return module.shown() != null;
    }

    @Override
    public boolean hasContent() {
        return uuid != null;
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num("layout.hud.target_width");
    }

    private float gearHeight(Ui ui, boolean preview) {
        return preview || !gear.isEmpty() ? ui.num(L + "gear_gap") + ui.num(L + "gear_icon") : 0f;
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return HudStyle.insetY(ui) * 2 + Math.max(ui.lineHeight("fa_dossier_name"), ui.lineHeight("fa_dossier_score"))
                + ui.num(L + "line_gap") + ui.lineHeight("fa_dossier_sub") + gearHeight(ui, preview);
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        float w = width(ui, preview);
        HudStyle.panel(ui, x, y, w, height(ui, preview));
        float cx = x + HudStyle.insetX(ui);
        float cw = w - HudStyle.insetX(ui) * 2;
        float cy = y + HudStyle.insetY(ui);
        DossierRecord r = preview ? null : record;
        int kills = preview ? 3 : r == null ? 0 : r.kills();
        int deaths = preview ? 1 : r == null ? 0 : r.deaths();
        String score = kills + "–" + deaths;
        String scoreColor = kills > deaths ? "good" : kills < deaths ? "bad" : "text";
        float lineH = Math.max(ui.lineHeight("fa_dossier_name"), ui.lineHeight("fa_dossier_score"));
        float scoreW = ui.textWidth("fa_dossier_score", score);
        String title = Ui.tr("skirmish.analytics.dossier.vs", preview ? "Enemy_3" : name);
        ui.textCentered("fa_dossier_name", ui.ellipsize("fa_dossier_name", title, cw - scoreW - ui.num(L + "score_gap")), cx, cy, lineH);
        ui.textCentered("fa_dossier_score", score, cx + cw - scoreW, cy, lineH, ui.color(scoreColor));
        cy += lineH + ui.num(L + "line_gap");

        String sub;
        if (preview) {
            sub = Ui.tr("skirmish.analytics.dossier.last_fight", Ui.tr("skirmish.analytics.ago.hours", 2)) + " · 4 "
                    + Ui.plural("skirmish.analytics.dossier.fights", 4);
        } else if (r == null || r.lastFightMs() < 0) {
            sub = Ui.tr("skirmish.analytics.dossier.first");
        } else {
            sub = Ui.tr("skirmish.analytics.dossier.last_fight", AnalyticsText.ago(System.currentTimeMillis() - r.lastFightMs()))
                    + " · " + r.fights() + " " + Ui.plural("skirmish.analytics.dossier.fights", r.fights());
        }
        ui.text("fa_dossier_sub", ui.ellipsize("fa_dossier_sub", sub, cw), cx, cy);
        cy += ui.lineHeight("fa_dossier_sub");

        List<ItemStack> items = preview && gear.isEmpty() ? List.of(new ItemStack(Items.NETHERITE_HELMET),
                new ItemStack(Items.NETHERITE_CHESTPLATE), new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.NETHERITE_SWORD)) : gear;
        if (!items.isEmpty()) {
            cy += ui.num(L + "gear_gap");
            float icon = ui.num(L + "gear_icon");
            float gx = cx;
            for (ItemStack stack : items) {
                var pose = ui.graphics().pose();
                pose.pushMatrix();
                pose.translate(Math.round(gx), Math.round(cy));
                pose.scale(icon / 16f, icon / 16f);
                ui.graphics().renderItem(stack, 0, 0);
                pose.popMatrix();
                gx += icon + ui.num(L + "gear_spacing");
            }
        }
    }
}
