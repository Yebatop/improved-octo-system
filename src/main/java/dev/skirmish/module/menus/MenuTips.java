package dev.skirmish.module.menus;

import dev.skirmish.ui.Ui;

/** Rotating HolyWorld and Skirmish tips (lang keys {@code skirmish.menus.tip.1..COUNT}). */
final class MenuTips {
    static final int COUNT = 12;

    private MenuTips() {
    }

    /** Index 1..COUNT shown at {@code now}, changing every {@code periodMs}. */
    static int index(long now, long periodMs, long seed) {
        return (int) (((now / periodMs) + seed) % COUNT) + 1;
    }

    static String text(int index) {
        return Ui.tr("skirmish.menus.tip." + index);
    }
}
