package dev.skirmish.module.evtimers;

import org.jspecify.annotations.Nullable;

/**
 * Castle shulkers at x 0, z 0 (every anarchy): the wiki's rarities by colour and how many times each must be broken
 * — grey 5, light blue 7, purple 12. Pure Java; colours are dye names ("gray", "light_blue", ...).
 */
final class CastleShulkers {
    enum Rarity {
        COMMON(5), RARE(7), EPIC(12);

        final int breaks;

        Rarity(int breaks) {
            this.breaks = breaks;
        }
    }

    private CastleShulkers() {
    }

    /** Rarity of a shulker box of that dye colour (null: not one of the castle's). */
    static @Nullable Rarity rarity(@Nullable String dye) {
        if (dye == null) {
            return null;
        }
        return switch (dye) {
            case "gray", "light_gray" -> Rarity.COMMON;
            case "light_blue", "cyan" -> Rarity.RARE;
            case "purple", "magenta" -> Rarity.EPIC;
            default -> null;
        };
    }

    /** Within the castle's area: horizontally within {@code radius} of x 0, z 0. */
    static boolean nearCastle(double x, double z, double radius) {
        return x * x + z * z <= radius * radius;
    }

    /** Breaks left after {@code done} of yours (never below 1 while the box stands). */
    static int left(Rarity rarity, int done) {
        return Math.max(1, rarity.breaks - done);
    }
}
