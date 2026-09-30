package dev.skirmish.module.analytics;

import dev.skirmish.gui.Texts;
import dev.skirmish.ui.Ui;

import java.util.Locale;

/** Shared text of the analytics modules (lang keys under {@code skirmish.analytics.*}). */
public final class AnalyticsText {
    private AnalyticsText() {
    }

    /** "2 ч назад", "только что". */
    public static String ago(long ageMs) {
        TimeAgo.Value v = TimeAgo.of(ageMs);
        String key = "skirmish.analytics.ago." + v.unit().name().toLowerCase(Locale.ROOT);
        return v.unit() == TimeAgo.Unit.NOW ? Ui.tr(key) : Ui.tr(key, v.amount());
    }

    /** Reach in blocks with two decimals, or a dash. */
    public static String reach(double blocks) {
        return Double.isFinite(blocks) ? Ui.decimal(blocks, 2) : "—";
    }

    /** Health points with one decimal, or a dash. */
    public static String hp(double value) {
        return Double.isFinite(value) ? Ui.decimal(value, 1) : "—";
    }

    /** Short label of a damage type ("удар", "стрела", "падение"). */
    public static String damageType(String type) {
        String label = DamageTypes.label(type);
        if (label != null) {
            return Ui.tr("skirmish.analytics.damage." + label);
        }
        return Texts.humanize(DamageTypes.path(type)).toLowerCase(Locale.ROOT);
    }
}
