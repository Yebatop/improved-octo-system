package dev.skirmish.module.anvilcalc.calc;

import java.util.List;
import java.util.Locale;

/**
 * Recognises HolyWorld Prime «сомнительные» enchanted books ({@code Сомнительная добыча III},
 * {@code Сомнительный разящий клинок}) and items that already carry such an enchantment, from the name and lore the
 * client received. Pure Java, covered by tests.
 */
public final class DubiousBook {
    private DubiousBook() {
    }

    public static boolean matches(String name, List<String> lore) {
        if (mentions(name)) {
            return true;
        }
        for (String line : lore) {
            if (mentions(line)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentions(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.replaceAll("§.", "").toLowerCase(Locale.ROOT).replace('ё', 'е');
        return lower.contains("сомнительн") || lower.contains("dubious");
    }
}
