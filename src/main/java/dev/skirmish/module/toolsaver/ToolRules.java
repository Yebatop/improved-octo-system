package dev.skirmish.module.toolsaver;

/** Tool Saver thresholds. Pure Java. */
final class ToolRules {
    private ToolRules() {
    }

    static int usesLeft(int maxDamage, int damage) {
        return Math.max(0, maxDamage - damage);
    }

    /** At or under the protection threshold: hits with it are held back. */
    static boolean protect(int usesLeft, int threshold) {
        return usesLeft <= threshold;
    }

    /** Worth a warning: under {@code warnPercent} of the durability or within twice the threshold. */
    static boolean warn(int usesLeft, int maxDamage, double warnPercent, int threshold) {
        return usesLeft <= Math.max(threshold * 2L, Math.round(maxDamage * warnPercent / 100.0));
    }

    /** A second press this soon after being held back lets the hit through. */
    static boolean bypass(long now, long blockedAt, long windowMs) {
        return blockedAt >= 0 && now - blockedAt <= windowMs;
    }
}
