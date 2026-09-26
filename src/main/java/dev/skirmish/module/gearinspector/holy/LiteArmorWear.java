package dev.skirmish.module.gearinspector.holy;

/**
 * HolyWorld Lite's reworked Unbreaking on armour (wiki "Зачарования → Особенности зачарований Прочность и Починка"):
 * the chance that a hit costs no durability is {@code 47 + 10 × level} %, so Unbreaking V saves 97 % of hits. Armour
 * without Unbreaking loses durability on every hit, as in vanilla. Pure Java, covered by tests.
 * <p>
 * The estimate assumes one durability point per hit, which is what a hit up to 7.99 damage costs (vanilla armour loses
 * {@code max(1, damage / 4)} points per hit); heavier hits, explosions and Разрушитель make it shorter.
 */
public final class LiteArmorWear {
    private LiteArmorWear() {
    }

    /** Percent of hits that cost nothing, 0 without Unbreaking, capped at 100. */
    public static int savePercent(int unbreakingLevel) {
        if (unbreakingLevel <= 0) {
            return 0;
        }
        return (int) Math.min(100, 47L + 10L * unbreakingLevel);
    }

    /** Expected number of 1-point hits the piece survives, or -1 when it never wears out (100 % saved). */
    public static long hitsLeft(int remainingDurability, int unbreakingLevel) {
        int save = savePercent(unbreakingLevel);
        if (save >= 100) {
            return -1;
        }
        if (remainingDurability <= 0) {
            return 0;
        }
        return Math.round(remainingDurability * 100.0 / (100 - save));
    }
}
