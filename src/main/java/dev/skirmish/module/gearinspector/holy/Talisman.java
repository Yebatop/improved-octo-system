package dev.skirmish.module.gearinspector.holy;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * An off-hand sphere or talisman (HolyWorld Lite, wiki "Сферы и Талисманы"): a player head ("Сфера …") or a totem
 * ("Талисман …") whose effects work from the off hand. The effects are lore lines {@code • Урон II},
 * {@code • Броня II}, {@code • Скорость I}, {@code • Макс. здоровье II}, {@code • Спешка I}; a talisman may also carry
 * a rune ("Бессмертие" or "Восстановление"). Pure Java, covered by tests.
 *
 * @param kind  what the name says it is
 * @param name  the display name with decorations trimmed ({@code Талисман Infinity}, {@code Сфера ᴀʀᴍᴏʀᴛᴀʟɪᴛʏ})
 * @param stats effects in lore order
 * @param rune  rune on a talisman or totem, null when none was recognised
 */
public record Talisman(Kind kind, String name, List<Stat> stats, @Nullable Rune rune) {
    public enum Kind {
        SPHERE,
        TALISMAN,
        /** A totem (or other item) whose only HolyWorld trait is a rune. */
        TOTEM
    }

    public enum StatType {
        DAMAGE("урон"),
        ARMOR("броня"),
        ATTACK_SPEED("скорость атаки"),
        SPEED("скорость"),
        MAX_HEALTH("макс. здоровье", "максимальное здоровье", "здоровье"),
        HASTE("спешка");

        private final List<String> names;

        StatType(String... names) {
            this.names = List.of(names);
        }
    }

    public enum Rune {
        /** 3 s of invulnerability after the totem pops. */
        IMMORTALITY,
        /** Full heal after the totem pops. */
        RESTORATION
    }

    public record Stat(StatType type, int level) {
    }

    public Talisman {
        stats = List.copyOf(stats);
    }

    /**
     * Reads a sphere or talisman from the item's custom name and lore; null when neither the name nor the lore looks
     * like one (a plain totem, a vanilla head).
     */
    public static @Nullable Talisman parse(String name, List<String> lore) {
        List<String> words = HolyText.words(name);
        Kind kind = words.contains("сфера") || words.contains("sphere") ? Kind.SPHERE
                : words.contains("талисман") || words.contains("talisman") ? Kind.TALISMAN : null;
        List<Stat> stats = new ArrayList<>();
        Rune rune = null;
        for (String line : lore) {
            Stat stat = parseStat(line);
            if (stat != null && stats.stream().noneMatch(s -> s.type() == stat.type())) {
                stats.add(stat);
            }
            String text = HolyText.normalize(line);
            if (text.contains("руна") || text.contains("руны")) {
                if (text.contains("бессмерт")) {
                    rune = Rune.IMMORTALITY;
                } else if (text.contains("восстановлен")) {
                    rune = Rune.RESTORATION;
                }
            }
        }
        if (kind == null) {
            if (stats.isEmpty() && rune == null) {
                return null;
            }
            kind = stats.isEmpty() ? Kind.TOTEM : Kind.SPHERE;
        }
        return new Talisman(kind, cleanName(name), stats, rune);
    }

    /** {@code • Урон II} → DAMAGE 2; {@code • Скорость атаки 1} → ATTACK_SPEED 1; null for anything else. */
    public static @Nullable Stat parseStat(String line) {
        String text = HolyText.normalize(line);
        if (!text.startsWith("•") && !text.startsWith("●") && !text.startsWith("-")) {
            return null;
        }
        text = text.substring(1).trim();
        int space = text.lastIndexOf(' ');
        if (space <= 0) {
            return null;
        }
        int level = HolyText.level(text.substring(space + 1));
        if (level < 1) {
            return null;
        }
        String label = text.substring(0, space).trim();
        for (StatType type : StatType.values()) {
            if (type.names.contains(label)) {
                return new Stat(type, level);
            }
        }
        return null;
    }

    private static String cleanName(String name) {
        String stripped = name == null ? "" : name.replaceAll("§.", "").trim();
        while (!stripped.isEmpty() && "-–—•*".indexOf(stripped.charAt(0)) >= 0) {
            stripped = stripped.substring(1).trim();
        }
        while (!stripped.isEmpty() && "-–—•*".indexOf(stripped.charAt(stripped.length() - 1)) >= 0) {
            stripped = stripped.substring(0, stripped.length() - 1).trim();
        }
        return stripped;
    }
}
