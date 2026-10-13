package dev.skirmish.module.survival;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The survival warning: the most important active warning as a big title, the others on one smaller line. Default
 * place: centered above the crosshair and above the combat tag panel (which sits right over the crosshair), below
 * the top-center waypoint pill and shulker banner.
 */
public final class SurvivalBanner extends HudBlock {
    public static final String ID = "survival_alerts";
    private static final String L = "layout.awareness.";
    private static final Set<SurvivalRules.Alert> PREVIEW = EnumSet.of(SurvivalRules.Alert.LOW_HP, SurvivalRules.Alert.PEARLS);

    private final SurvivalAlertsModule module;
    private Set<SurvivalRules.Alert> shown = EnumSet.noneOf(SurvivalRules.Alert.class);
    private String title = "";
    private List<String> lines = List.of();

    SurvivalBanner(SurvivalAlertsModule module) {
        super(ID, "skirmish.hud.element.survival_alerts", bannerPlacement());
        this.module = module;
    }

    /** Horizontally centered, bottom edge {@code alert_default_dy} above the screen center. */
    public static Placement bannerPlacement() {
        return new Placement(0.5f, 0f, 0.5f, 0f, 0, Theme.get().num("layout.hud.top_column_dy"));
    }

    @Override
    public @org.jspecify.annotations.Nullable String stackUnder() {
        return "alerts_logout";
    }

    @Override
    public boolean enabled() {
        return module.isEnabled();
    }

    @Override
    public boolean shown() {
        boolean on = !module.active().isEmpty();
        if (on) {
            update(false);
        }
        return on;
    }

    @Override
    public boolean hasContent() {
        return !shown.isEmpty();
    }

    @Override
    public void update(boolean preview) {
        Set<SurvivalRules.Alert> now = module.active();
        if (!now.isEmpty()) {
            shown = EnumSet.copyOf(now);
        } else if (preview) {
            shown = PREVIEW;
        }
        List<String> texts = new ArrayList<>();
        for (SurvivalRules.Alert alert : shown) {
            texts.add(text(alert, preview && now.isEmpty()));
        }
        title = texts.isEmpty() ? "" : texts.getFirst();
        lines = texts.size() > 1 ? List.of(String.join("  ·  ", texts.subList(1, texts.size()))) : List.of();
    }

    private String text(SurvivalRules.Alert alert, boolean sample) {
        SurvivalRules.Snapshot s = sample ? new SurvivalRules.Snapshot(6.5f, 20, true, true, 1, 1, 4) : module.snapshot();
        return switch (alert) {
            case LOW_HP -> Ui.tr("skirmish.survival.alert.low_hp", Ui.decimal(Math.round(s.health() * 10) / 10.0, 1));
            case NO_TOTEM -> Ui.tr("skirmish.survival.alert.no_totem");
            case ARMOR -> Ui.tr("skirmish.survival.alert.armor", (int) Math.floor(s.worstArmor() * 100));
            case FOOD -> Ui.tr("skirmish.survival.alert.food", s.food());
            case PEARLS -> Ui.tr("skirmish.survival.alert.pearls", s.pearls());
            case GAPPLES -> Ui.tr("skirmish.survival.alert.gapples", s.gapples());
        };
    }

    private String tone() {
        return SurvivalRules.anyCritical(shown) ? "bad" : "warn";
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return WarningPanel.width(ui, title, lines);
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return WarningPanel.height(ui, lines);
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        boolean flash = module.flash.get() && SurvivalRules.anyCritical(shown) && !preview;
        WarningPanel.render(ui, x, y, title, lines, tone(), flash);
    }
}
