package dev.skirmish.module.sprint;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Ui;

/** Small «Спринт» pill while vanilla's toggled sprint is on. Bottom-left corner, below the chat lines. */
final class SprintIndicator extends HudBlock {
    private static final String L = "layout.qol.";
    private final ToggleSprintModule module;

    SprintIndicator(ToggleSprintModule module) {
        super("toggle_sprint", "skirmish.hud.element.toggle_sprint", new Placement(0f, 1f, 0f, 1f, 18, -12));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.indicator.get();
    }

    @Override
    public boolean shown() {
        return module.sprintToggledOn();
    }

    @Override
    public boolean hasContent() {
        return true;
    }

    private static String label() {
        return Ui.tr("skirmish.toggle_sprint.indicator");
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "pill_pad_x") * 2 + ui.num(L + "dot") + ui.num(L + "dot_gap") + ui.textWidth("qol_pill", label());
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return ui.num(L + "pill_height");
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        float w = width(ui, preview);
        float h = height(ui, preview);
        HudStyle.pill(ui, x, y, w, h);
        float dot = ui.num(L + "dot");
        float dx = x + ui.num(L + "pill_pad_x");
        ui.circle(dx + dot / 2f, y + h / 2f, dot, ui.color("good"));
        ui.textCentered("qol_pill", label(), dx + dot + ui.num(L + "dot_gap"), y, h);
    }
}
