package dev.skirmish.module.weaponstats;

/** Melee numbers like vanilla computes them. Pure Java. */
final class WeaponMath {
    private WeaponMath() {
    }

    /** Sharpness: +1 at level I, +0.5 per level after (vanilla 1.21). */
    static double sharpness(int level) {
        return level <= 0 ? 0 : 0.5 * level + 0.5;
    }

    /** Smite and Bane of Arthropods: +2.5 per level against their mobs. */
    static double smite(int level) {
        return level <= 0 ? 0 : 2.5 * level;
    }

    /** Strength adds 3 per level, Weakness takes 4 per level (effect amplifier + 1). */
    static double effects(int strengthLevel, int weaknessLevel) {
        return 3.0 * strengthLevel - 4.0 * weaknessLevel;
    }

    /** A critical hit deals ×1.5. */
    static double crit(double damage) {
        return damage * 1.5;
    }

    /** Seconds between fully charged swings. */
    static double swingSeconds(double attackSpeed) {
        return attackSpeed <= 0 ? Double.POSITIVE_INFINITY : 1.0 / attackSpeed;
    }

    /** Damage per second hitting at full charge. */
    static double dps(double damage, double attackSpeed) {
        return damage * Math.max(0, attackSpeed);
    }

    /** Sweeping Edge damage to the side mobs: 1 + damage × level / (level + 1). */
    static double sweep(double damage, int level) {
        return 1.0 + damage * (level <= 0 ? 0 : level / (level + 1.0));
    }
}
