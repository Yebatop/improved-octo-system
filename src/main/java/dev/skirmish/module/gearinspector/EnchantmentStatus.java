package dev.skirmish.module.gearinspector;

/** What the panel can say about the enchantments of one stack. Pure Java, covered by tests. */
public enum EnchantmentStatus {
    /** At least one enchantment (or stored enchantment of a book) was received: list them. */
    LISTED,
    /** Enchantable item, no enchantments received. Unenchanted and hidden look the same, so this is "no data". */
    NO_DATA,
    /** No enchantments received, but the stack forces the enchantment glint: the server likely stripped them. */
    GLINT_ONLY,
    /** Empty slot or an item that cannot be enchanted and has none: no enchantment line at all. */
    NOT_APPLICABLE;

    public static EnchantmentStatus classify(boolean empty, int count, boolean enchantable, boolean glintOverride) {
        if (empty) {
            return NOT_APPLICABLE;
        }
        if (count > 0) {
            return LISTED;
        }
        if (glintOverride) {
            return GLINT_ONLY;
        }
        return enchantable ? NO_DATA : NOT_APPLICABLE;
    }

    /** English explanation for debug.log (the list itself is appended by the caller for {@link #LISTED}). */
    public String describe() {
        return switch (this) {
            case LISTED -> "enchantments";
            case NO_DATA -> "enchantments NO DATA (enchantable item, none received: unenchanted or hidden by the server)";
            case GLINT_ONLY -> "enchantments NO DATA (glint override without enchantments: probably stripped by the server)";
            case NOT_APPLICABLE -> "no enchantment line (not enchantable, none received)";
        };
    }
}
