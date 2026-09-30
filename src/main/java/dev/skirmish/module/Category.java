package dev.skirmish.module;

/** Group a module is listed under in the menu's sidebar, in this order. */
public enum Category {
    COMBAT, VISUAL, WORLD, UTILITY, INTERFACE;

    public String translationKey() {
        return "skirmish.category." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
