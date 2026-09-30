package dev.skirmish.module.playerfx;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Ui;
import net.minecraft.util.Util;

/** «−1 тотем · осталось 3» under the crosshair for a moment after my totem saves me; red when none are left. */
final class TotemCounterHud extends HudBlock {
    private static final String L = "layout.player_fx.";
    private static final long SHOW_MS = 2600;
    private final PlayerFxModule module;

    TotemCounterHud(PlayerFxModule module) {
        super("totem_counter", "skirmish.hud.element.totem_counter", new Placement(0.5f, 0.5f, 0.5f, 0f, 0, 46));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.totemCounter.get();
    }

    @Override
    public boolean shown() {
        return module.totemAt > 0 && Util.getMillis() - module.totemAt < SHOW_MS;
    }

    private String text(boolean preview) {
        int left = preview ? 3 : module.totemsLeft;
        return left > 0 ? Ui.tr("skirmish.player_fx.totem_left", left) : Ui.tr("skirmish.player_fx.totem_none");
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "pill_pad") * 2 + ui.num(L + "pill_icon") + ui.num(L + "pill_gap")
                + ui.textWidth("pfx_totem_title", Ui.tr("skirmish.player_fx.totem_used")) + ui.num(L + "pill_gap")
                + ui.textWidth("pfx_totem_left", text(preview));
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return ui.num(L + "pill_h");
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        float w = width(ui, preview);
        float h = height(ui, preview);
        long age = preview ? 400 : Util.getMillis() - module.totemAt;
        float pop = age < 180 ? 1f + 0.12f * (1f - age / 180f) : 1f;
        boolean none = !preview && module.totemsLeft <= 0;
        int tone = ui.color(none ? "bad" : "totem_gold");
        ui.box(x, y, w, h, h / 2f, ui.color("panel"), (tone & 0xFFFFFF) | 0xA0000000);
        float icon = ui.num(L + "pill_icon") * pop;
        float cx = x + ui.num(L + "pill_pad") + ui.num(L + "pill_icon") / 2f;
        float cy = y + h / 2f;
        // A small totem: head, body and wings.
        ui.rect(cx - icon * 0.22f, cy - icon * 0.5f, icon * 0.44f, icon * 0.36f, icon * 0.08f, tone);
        ui.rect(cx - icon * 0.18f, cy - icon * 0.1f, icon * 0.36f, icon * 0.6f, icon * 0.08f, tone);
        ui.rect(cx - icon * 0.5f, cy - icon * 0.05f, icon * 0.26f, icon * 0.16f, icon * 0.06f, tone);
        ui.rect(cx + icon * 0.24f, cy - icon * 0.05f, icon * 0.26f, icon * 0.16f, icon * 0.06f, tone);
        float tx = x + ui.num(L + "pill_pad") + ui.num(L + "pill_icon") + ui.num(L + "pill_gap");
        String title = Ui.tr("skirmish.player_fx.totem_used");
        ui.textCentered("pfx_totem_title", title, tx, y, h, tone);
        tx += ui.textWidth("pfx_totem_title", title) + ui.num(L + "pill_gap");
        ui.textCentered("pfx_totem_left", text(preview), tx, y, h);
    }
}
