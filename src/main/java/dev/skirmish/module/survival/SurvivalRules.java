package dev.skirmish.module.survival;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which survival warnings are active, from a snapshot of the local player's own state. Pure (unit tested). Each
 * warning switches on below its threshold and off only a little above it (hysteresis), so a value hovering at the
 * threshold does not make the banner blink or the sound repeat.
 */
public final class SurvivalRules {
    /** In priority order: the first active one is the banner's title. */
    public enum Alert {
        LOW_HP, NO_TOTEM, ARMOR, FOOD, PEARLS, GAPPLES;

        /** Life-threatening: flashes and uses the red tone. */
        public boolean critical() {
            return this == LOW_HP || this == NO_TOTEM;
        }
    }

    /** Extra margin before a warning clears. */
    static final float HP_RELEASE = 2f;
    static final int FOOD_RELEASE = 2;
    static final double ARMOR_RELEASE = 0.03;

    /**
     * @param health           current health (without absorption)
     * @param inPvp            combat tag or an active fight with a player
     * @param totemInOffhand   a totem of undying in the off hand
     * @param worstArmor       lowest remaining durability fraction of the worn armor pieces (1 without armor)
     * @param pearls           ender pearls in the inventory (incl. off hand)
     * @param gapples          golden + enchanted golden apples
     */
    public record Snapshot(float health, int food, boolean inPvp, boolean totemInOffhand, double worstArmor,
                           int pearls, int gapples) {
    }

    /**
     * Thresholds and switches from the settings. A disabled warning never fires; {@code suppliesPvpOnly} limits
     * the pearl / apple warnings to PvP (outside of it an empty pocket is normal).
     */
    public record Config(boolean lowHp, float hpThreshold, boolean noTotem, boolean armor, double armorFraction,
                         boolean food, int foodThreshold, boolean pearls, int pearlThreshold, boolean gapples,
                         int gappleThreshold, boolean suppliesPvpOnly) {
    }

    private SurvivalRules() {
    }

    public static Set<Alert> evaluate(Snapshot s, Config c, Set<Alert> previous) {
        EnumSet<Alert> out = EnumSet.noneOf(Alert.class);
        if (c.lowHp() && s.health() > 0f) {
            boolean held = previous.contains(Alert.LOW_HP) && s.health() < c.hpThreshold() + HP_RELEASE;
            if (s.health() <= c.hpThreshold() || held) {
                out.add(Alert.LOW_HP);
            }
        }
        if (c.noTotem() && s.inPvp() && !s.totemInOffhand()) {
            out.add(Alert.NO_TOTEM);
        }
        if (c.armor() && s.worstArmor() < 1.0) {
            double limit = c.armorFraction() + (previous.contains(Alert.ARMOR) ? ARMOR_RELEASE : 0.0);
            if (s.worstArmor() < limit) {
                out.add(Alert.ARMOR);
            }
        }
        if (c.food()) {
            int limit = c.foodThreshold() + (previous.contains(Alert.FOOD) ? FOOD_RELEASE : 0);
            if (s.food() <= limit) {
                out.add(Alert.FOOD);
            }
        }
        boolean supplies = !c.suppliesPvpOnly() || s.inPvp();
        if (supplies && c.pearls() && s.pearls() <= c.pearlThreshold() + (previous.contains(Alert.PEARLS) ? 1 : 0)) {
            out.add(Alert.PEARLS);
        }
        if (supplies && c.gapples() && s.gapples() <= c.gappleThreshold() + (previous.contains(Alert.GAPPLES) ? 1 : 0)) {
            out.add(Alert.GAPPLES);
        }
        return out;
    }

    /** Warnings that were not active before: these play the sound. */
    public static Set<Alert> started(Set<Alert> previous, Set<Alert> now) {
        EnumSet<Alert> out = EnumSet.noneOf(Alert.class);
        for (Alert a : now) {
            if (!previous.contains(a)) {
                out.add(a);
            }
        }
        return out;
    }

    public static boolean anyCritical(Set<Alert> alerts) {
        for (Alert a : alerts) {
            if (a.critical()) {
                return true;
            }
        }
        return false;
    }
}
