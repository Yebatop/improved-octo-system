package dev.skirmish.module.analytics.review;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Icons;
import dev.skirmish.ui.KeyNames;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import org.jspecify.annotations.Nullable;

/**
 * «Разбор боя — N» for a few seconds after a fight or (once respawned) after a death: the outcome, the opponent and
 * the key that opens the review. Default place: centred above the cooldown rings, clear of the hotbar and chat.
 */
final class ReviewHint extends HudBlock {
    private static final String L = "layout.analytics.hint.";

    private final FightReviewModule module;
    private @Nullable ReviewEntry entry;
    private @Nullable ReviewEntry last;
    private long shownAt;

    ReviewHint(FightReviewModule module) {
        super("fight_review_hint", "skirmish.hud.element.fight_review_hint", new Placement(1f, 1f, 1f, 1f, -Theme.get().num("layout.screen_edge"),
                -Theme.get().num("layout.hud.review_hint_bottom")));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.hint.get();
    }

    @Override
    public void update(boolean preview) {
        ReviewEntry now = module.hintEntry(System.currentTimeMillis());
        if (now != null && now != entry) {
            shownAt = System.currentTimeMillis();
        }
        entry = now;
        if (now != null) {
            last = now;
        }
    }

    @Override
    public boolean shown() {
        update(false);
        return entry != null;
    }

    @Override
    public boolean hasContent() {
        return last != null;
    }

    private String title(boolean preview) {
        ReviewEntry e = preview ? null : last;
        return Ui.tr(e != null && e.death() != null ? "skirmish.analytics.hint.death" : "skirmish.analytics.hint.fight");
    }

    private String sub(boolean preview) {
        ReviewEntry e = preview ? null : last;
        if (e == null) {
            return Ui.tr("skirmish.analytics.outcome.win") + " · Enemy_3 · 0:47";
        }
        String name = e.opponentName() == null ? "?" : e.opponentName();
        String text = Ui.tr(e.outcome().langKey()) + " · " + name;
        if (e.fight() != null) {
            text += " · " + Ui.duration(ReviewText.activeDuration(e));
        }
        return text;
    }

    private @Nullable String key() {
        return SkirmishKeys.FIGHT_REVIEW.isUnbound() ? null : KeyNames.shortName(SkirmishKeys.FIGHT_REVIEW);
    }

    private float textWidth(Ui ui, boolean preview) {
        String sub = SkirmishKeys.FIGHT_REVIEW.isUnbound() ? Ui.tr("skirmish.analytics.hint.in_menu") : sub(preview);
        return Math.min(ui.num(L + "text_max"), Math.max(ui.textWidth("fa_hint_title", title(preview)), ui.textWidth("fa_hint_sub", sub)));
    }

    private float chipWidth(Ui ui) {
        String key = key();
        return key == null ? 0f : Math.max(ui.num(L + "chip_min"), ui.textWidth("fa_key", key) + ui.num(L + "chip_pad_x") * 2);
    }

    @Override
    public float width(Ui ui, boolean preview) {
        float chip = chipWidth(ui);
        return HudStyle.insetX(ui) * 2 + ui.num(L + "icon_circle") + ui.num(L + "gap") + textWidth(ui, preview)
                + (chip > 0 ? ui.num(L + "gap") + chip : 0f);
    }

    @Override
    public float height(Ui ui, boolean preview) {
        float text = ui.lineHeight("fa_hint_title") + ui.lineHeight("fa_hint_sub");
        return ui.num("stroke.width") * 2 + ui.num(L + "pad_y") * 2 + Math.max(ui.num(L + "icon_circle"), text);
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        float w = width(ui, preview);
        float h = height(ui, preview);
        HudStyle.panel(ui, x, y, w, h);
        float cx = x + HudStyle.insetX(ui);
        float circle = ui.num(L + "icon_circle");
        ReviewEntry e = preview ? null : last;
        String tone = e == null ? "accent" : e.outcome().good() ? "good" : e.outcome().bad() ? "bad" : "accent";
        ui.circle(cx + circle / 2f, y + h / 2f, circle, ui.color(e == null || tone.equals("accent") ? "accent_16"
                : tone.equals("good") ? "fa_good_16" : "fa_bad_16"));
        float icon = ui.num(L + "icon");
        Icons.sword(ui, cx + (circle - icon) / 2f, y + (h - icon) / 2f, icon, ui.num(L + "icon_stroke"), ui.color(tone), true);
        cx += circle + ui.num(L + "gap");

        float tw = textWidth(ui, preview);
        float textH = ui.lineHeight("fa_hint_title") + ui.lineHeight("fa_hint_sub");
        float ty = y + (h - textH) / 2f;
        ui.text("fa_hint_title", ui.ellipsize("fa_hint_title", title(preview), tw), cx, ty);
        String sub = SkirmishKeys.FIGHT_REVIEW.isUnbound() ? Ui.tr("skirmish.analytics.hint.in_menu") : sub(preview);
        ui.text("fa_hint_sub", ui.ellipsize("fa_hint_sub", sub, tw), cx, ty + ui.lineHeight("fa_hint_title"));
        cx += tw;

        String key = key();
        if (key != null) {
            float chipW = chipWidth(ui);
            float chipH = ui.num(L + "chip_height");
            float chipX = cx + ui.num(L + "gap");
            float chipY = y + (h - chipH) / 2f;
            ui.box(chipX, chipY, chipW, chipH, ui.theme().radius("chip"), ui.color("fill_08"), ui.color("stroke_12"));
            ui.textCentered("fa_key", key, chipX + (chipW - ui.textWidth("fa_key", key)) / 2f, chipY, chipH);
        }

        // Remaining time as a thin line along the bottom edge.
        if (!preview && entry != null) {
            float total = module.hintSeconds.getFloat() * 1000f;
            float left = Math.max(0f, 1f - (System.currentTimeMillis() - shownAt) / total);
            float r = ui.theme().radius("panel");
            float bar = ui.num(L + "progress");
            ui.rect(x + r, y + h - ui.num("stroke.width") - bar, (w - r * 2) * left, bar, bar / 2f, ui.color("accent_60"));
        }
    }
}
