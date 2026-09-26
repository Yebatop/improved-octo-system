package dev.skirmish.module.pvp;

import java.util.Comparator;
import java.util.List;

/**
 * Stable order of the own-cooldowns row: PvP items first in a fixed order (so icons do not jump around), then any
 * other cooldown group by id. HolyWorld customs use vanilla base items: трапка = popped chorus fruit, взрывная
 * трапка = prismarine shard (they share one cooldown), стан = nether star. Pure Java.
 */
final class CooldownOrder {
    static final List<String> PRIORITY = List.of(
            "minecraft:ender_pearl",
            "minecraft:golden_apple",
            "minecraft:enchanted_golden_apple",
            "minecraft:chorus_fruit",
            "minecraft:popped_chorus_fruit",
            "minecraft:prismarine_shard",
            "minecraft:nether_star",
            "minecraft:wind_charge",
            "minecraft:shield");

    static final Comparator<String> BY_GROUP = Comparator.comparingInt(CooldownOrder::rank).thenComparing(id -> id);

    private CooldownOrder() {
    }

    private static int rank(String group) {
        int i = PRIORITY.indexOf(group);
        return i < 0 ? PRIORITY.size() : i;
    }

    /** Seconds as the label shows them: tenths rounded up below 10 s (4.2), whole seconds rounded up above. */
    static double shownSeconds(float remainingTicks) {
        double seconds = Math.max(0.0, remainingTicks / 20.0);
        if (seconds < 10.0) {
            return Math.ceil(seconds * 10.0 - 1e-6) / 10.0;
        }
        return Math.ceil(seconds - 1e-6);
    }

    /** Decimal places for a value from {@link #shownSeconds}. */
    static int digits(double shownSeconds) {
        return shownSeconds < 10.0 ? 1 : 0;
    }
}
