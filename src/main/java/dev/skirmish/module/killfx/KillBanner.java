package dev.skirmish.module.killfx;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import org.jspecify.annotations.Nullable;

/**
 * «УБИЙСТВО · Nick» in the middle of the screen with the streak chip («СЕРИЯ ×3» from the second kill on) and a line
 * that drains while the banner lasts. Unlike the kill feed (a corner list of everyone's deaths), this is only about
 * my kill, big and short. Default place: centered, above the survival banner (which sits above the combat tag).
 */
final class KillBanner extends HudBlock {
    private static final String L = "layout.kill_fx.";

    private final KillFxModule module;
    private KillFxModule.@Nullable Shown current;

    KillBanner(KillFxModule module) {
        super(KillFxModule.ID, "skirmish.hud.element.kill_fx", new Placement(0.5f, 0f, 0.5f, 0f, 0, dev.skirmish.ui.Theme.get().num("layout.hud.top_column_dy")));
        this.module = module;
    }

    @Override
    public @org.jspecify.annotations.Nullable String stackUnder() {
        return "lag_warning";
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.banner.get();
    }

    @Override
    public boolean shown() {
        return module.banner(System.currentTimeMillis()) != null;
    }

    @Override
    public boolean hasContent() {
        return current != null;
    }

    @Override
    public void update(boolean preview) {
        long now = System.currentTimeMillis();
        KillFxModule.Shown live = module.banner(now);
        if (live != null) {
            current = live;
        } else if (preview) {
            current = new KillFxModule.Shown("Enemy_3", 3, now - (now % module.bannerMs()));
        }
    }

    private String title() {
        return Ui.tr("skirmish.kill_fx.kill");
    }

    private @Nullable String chip() {
        KillFxModule.Shown s = current;
        return s != null && module.streak.get() && s.streak() >= 2 ? Ui.tr("skirmish.kill_fx.streak", s.streak()) : null;
    }

    private float lineWidth(Ui ui) {
        float w = ui.textWidth("kfx_title", title());
        KillFxModule.Shown s = current;
        if (s != null && s.victim() != null) {
            w += ui.textWidth("kfx_name", " · " + s.victim());
        }
        return w;
    }

    private float chipWidth(Ui ui, String chip) {
        return ui.textWidth("kfx_chip", chip) + ui.num(L + "chip_pad_x") * 2;
    }

    @Override
    public float width(Ui ui, boolean preview) {
        float w = lineWidth(ui);
        String chip = chip();
        if (chip != null) {
            w = Math.max(w, chipWidth(ui, chip));
        }
        return Math.max(ui.num(L + "min_width"), w + ui.num(L + "pad_x") * 2 + ui.num("stroke.width") * 2);
    }

    @Override
    public float height(Ui ui, boolean preview) {
        float h = ui.num("stroke.width") * 2 + ui.num(L + "pad_y") * 2 + ui.lineHeight("kfx_title") + ui.num(L + "bar_gap") + ui.num(L + "bar");
        if (chip() != null) {
            h += ui.num(L + "chip_gap") + ui.num(L + "chip_height");
        }
        return h;
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        KillFxModule.Shown s = current;
        if (s == null) {
            return;
        }
        float w = width(ui, preview);
        float h = height(ui, preview);
        HudStyle.panel(ui, x, y, w, h);
        float stroke = ui.num("stroke.width");
        float top = y + stroke + ui.num(L + "pad_y");
        float lx = x + (w - lineWidth(ui)) / 2f;
        lx = ui.text("kfx_title", title(), lx, top);
        if (s.victim() != null) {
            ui.text("kfx_name", " · " + s.victim(), lx, top + (ui.lineHeight("kfx_title") - ui.lineHeight("kfx_name")) / 2f);
        }
        float cy = top + ui.lineHeight("kfx_title");
        String chip = chip();
        if (chip != null) {
            cy += ui.num(L + "chip_gap");
            float cw = chipWidth(ui, chip);
            float ch = ui.num(L + "chip_height");
            float cx = x + (w - cw) / 2f;
            ui.rect(cx, cy, cw, ch, ui.theme().radius("chip"), ui.color("kfx_chip_fill"));
            ui.textCentered("kfx_chip", chip, cx + ui.num(L + "chip_pad_x"), cy, ch);
            cy += ch;
        }
        float bar = ui.num(L + "bar");
        float barW = ui.num(L + "bar_width");
        float by = cy + ui.num(L + "bar_gap");
        float left = preview ? 0.6f : 1f - Math.min(1f, (System.currentTimeMillis() - s.startMs()) / (float) module.bannerMs());
        float fw = barW * Math.max(0f, left);
        if (fw > 0f) {
            ui.rect(x + (w - fw) / 2f, by, fw, bar, bar / 2f, ui.color("kfx_accent"));
        }
    }
}
