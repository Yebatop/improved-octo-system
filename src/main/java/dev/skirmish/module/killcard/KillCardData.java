package dev.skirmish.module.killcard;

import java.util.List;

/**
 * Everything the card needs, detached from Minecraft: built on the client thread, rendered on the card thread.
 *
 * @param gear  six slots in {@code EquipmentSnapshot.SLOTS} order (head, chest, legs, feet, main hand, off hand)
 * @param scale output size multiplier of the 960x540 layout
 */
public record KillCardData(CardStats stats, CardText text, CardTheme theme, List<CardItem> gear, double scale) {
    public KillCardData {
        gear = List.copyOf(gear);
        if (gear.size() != 6) {
            throw new IllegalArgumentException("Expected 6 gear slots, got " + gear.size());
        }
        if (!(scale > 0)) {
            throw new IllegalArgumentException("Bad scale " + scale);
        }
    }
}
