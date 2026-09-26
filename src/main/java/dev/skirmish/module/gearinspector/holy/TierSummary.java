package dev.skirmish.module.gearinspector.holy;

import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The armour tier shown on the target card: the tier worn on most armour pieces (the higher one on a tie) and on how
 * many of the four. Pure Java, covered by tests.
 *
 * @param tier  dominant tier
 * @param count armour pieces of that tier
 */
public record TierSummary(DonorTier tier, int count) {
    /** @param armour tiers of the worn armour pieces, null for pieces without a recognised tier */
    public static @Nullable TierSummary of(List<@Nullable DonorTier> armour) {
        Map<DonorTier, Integer> counts = new EnumMap<>(DonorTier.class);
        for (DonorTier tier : armour) {
            if (tier != null) {
                counts.merge(tier, 1, Integer::sum);
            }
        }
        TierSummary best = null;
        for (Map.Entry<DonorTier, Integer> entry : counts.entrySet()) {
            // EnumMap iterates lowest tier first, so ">=" keeps the higher tier on equal counts.
            if (best == null || entry.getValue() >= best.count) {
                best = new TierSummary(entry.getKey(), entry.getValue());
            }
        }
        return best;
    }
}
