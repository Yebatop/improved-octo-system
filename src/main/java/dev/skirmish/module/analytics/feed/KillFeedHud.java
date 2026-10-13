package dev.skirmish.module.analytics.feed;

import dev.skirmish.hud.Hud;
import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Icons;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Kill feed rows, newest on top: «killer [weapon] victim» pills (or «name [totem] тотем»). Default place: top right,
 * right under the KillCam indicator (below the kill card toast while it shows); the HUD steps it out of the
 * scoreboard. Rows align to whichever side the element is anchored to.
 */
final class KillFeedHud extends HudBlock {
    private static final String L = "layout.analytics.feed.";
    private static final ItemStack TOTEM = new ItemStack(Items.TOTEM_OF_UNDYING);

    private final KillFeedModule module;
    private List<KillFeed.Entry<ItemStack>> rows = List.of();
    private final KillFeed<ItemStack> sample = new KillFeed<>();

    KillFeedHud(KillFeedModule module) {
        super("kill_feed", "skirmish.hud.element.kill_feed", new Placement(1, 0, 1, 0,
                -Theme.get().num("layout.screen_edge"), Theme.get().num(L + "default_y")));
        this.module = module;
    }

    @Override
    public @Nullable String stackUnder() {
        return "killcard";
    }

    @Override
    public boolean enabled() {
        return module.isEnabled();
    }

    @Override
    public void update(boolean preview) {
        long now = System.currentTimeMillis();
        rows = module.live(now);
        if (preview && rows.isEmpty()) {
            if (sample.isEmpty()) {
                sample.add(KillFeed.Kind.KILL, UUID.randomUUID(), "Enemy_3", false, "You", true, new ItemStack(Items.NETHERITE_SWORD), now);
                sample.add(KillFeed.Kind.TOTEM, UUID.randomUUID(), "Rival", false, null, false, null, now);
                sample.add(KillFeed.Kind.KILL, UUID.randomUUID(), "Rival_2", false, null, false, null, now);
            }
            rows = sample.live(now, Long.MAX_VALUE / 4, module.maxRows.getInt());
        }
    }

    @Override
    public boolean shown() {
        update(false);
        return !rows.isEmpty();
    }

    @Override
    public boolean hasContent() {
        return !rows.isEmpty();
    }

    private float rowHeight(Ui ui) {
        return ui.num("stroke.width") * 2 + ui.num(L + "pad_y") * 2 + Math.max(ui.lineHeight("fa_feed_name"), ui.num(L + "icon"));
    }

    private String killer(KillFeed.Entry<ItemStack> e) {
        return e.killer() == null ? "?" : e.killer();
    }

    private float rowWidth(Ui ui, KillFeed.Entry<ItemStack> e) {
        float padX = ui.num(L + "pad_x");
        float gap = ui.num(L + "gap");
        float icon = ui.num(L + "icon");
        float max = ui.num(L + "name_max");
        float stroke = ui.num("stroke.width");
        if (e.kind() == KillFeed.Kind.TOTEM) {
            return stroke * 2 + padX * 2 + Math.min(max, ui.textWidth("fa_feed_name", e.victim())) + gap * 2 + icon
                    + ui.textWidth("fa_feed_note", Ui.tr("skirmish.analytics.feed.totem"));
        }
        return stroke * 2 + padX * 2 + Math.min(max, ui.textWidth("fa_feed_name", killer(e))) + gap * 2 + icon
                + Math.min(max, ui.textWidth("fa_feed_name", e.victim()));
    }

    @Override
    public float width(Ui ui, boolean preview) {
        float w = ui.num(L + "min_width");
        for (KillFeed.Entry<ItemStack> e : rows) {
            w = Math.max(w, rowWidth(ui, e));
        }
        return w;
    }

    @Override
    public float height(Ui ui, boolean preview) {
        int n = Math.max(1, rows.size());
        return n * rowHeight(ui) + (n - 1) * ui.num(L + "row_gap");
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        float w = width(ui, preview);
        float rowH = rowHeight(ui);
        boolean right = Hud.get().placement(this).ax() >= 0.5f;
        long now = System.currentTimeMillis();
        float ry = y;
        for (KillFeed.Entry<ItemStack> e : rows) {
            float rw = rowWidth(ui, e);
            float rx = right ? x + w - rw : x;
            float alpha = preview ? 1f : KillFeed.alpha(now - e.timeMs(), module.lifetimeMs(), Math.round(ui.num(L + "fade_ms")));
            ui.pushAlpha(alpha);
            drawRow(ui, e, rx, ry, rw, rowH);
            ui.popAlpha();
            ry += rowH + ui.num(L + "row_gap");
        }
    }

    private void drawRow(Ui ui, KillFeed.Entry<ItemStack> e, float x, float y, float w, float h) {
        boolean mine = e.killerMe() || e.victimMe();
        ui.box(x, y, w, h, h / 2f, ui.color("panel"), ui.color(mine ? "accent_60" : "stroke"));
        float gap = ui.num(L + "gap");
        float icon = ui.num(L + "icon");
        float max = ui.num(L + "name_max");
        float cx = x + ui.num("stroke.width") + ui.num(L + "pad_x");
        if (e.kind() == KillFeed.Kind.TOTEM) {
            cx = name(ui, e.victim(), e.victimMe() ? "accent" : "text", cx, y, h, max) + gap;
            item(ui, TOTEM, cx, y + (h - icon) / 2f, icon);
            cx += icon + gap;
            ui.textCentered("fa_feed_note", Ui.tr("skirmish.analytics.feed.totem"), cx, y, h, ui.color("warn"));
            return;
        }
        String killerColor = e.killer() == null ? "text_3" : e.killerMe() ? "accent" : "text";
        cx = name(ui, killer(e), killerColor, cx, y, h, max) + gap;
        ItemStack weapon = e.weapon();
        if (weapon != null && !weapon.isEmpty()) {
            item(ui, weapon, cx, y + (h - icon) / 2f, icon);
        } else {
            float s = icon * 0.8f;
            Icons.sword(ui, cx + (icon - s) / 2f, y + (h - s) / 2f, s, 1.8f, ui.color("text_3"), true);
        }
        cx += icon + gap;
        name(ui, e.victim(), e.victimMe() ? "bad" : "text_2", cx, y, h, max);
    }

    private static float name(Ui ui, String text, String color, float x, float y, float h, float max) {
        return ui.textCentered("fa_feed_name", ui.ellipsize("fa_feed_name", text, max), x, y, h, ui.color(color));
    }

    private static void item(Ui ui, ItemStack stack, float x, float y, float size) {
        var pose = ui.graphics().pose();
        pose.pushMatrix();
        pose.translate(Math.round(x), Math.round(y));
        pose.scale(size / 16f, size / 16f);
        ui.graphics().renderItem(stack, 0, 0);
        pose.popMatrix();
    }
}
