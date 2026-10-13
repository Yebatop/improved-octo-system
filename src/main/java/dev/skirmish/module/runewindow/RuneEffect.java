package dev.skirmish.module.runewindow;

import dev.skirmish.module.gearinspector.holy.HolyText;
import dev.skirmish.module.gearinspector.holy.Talisman;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a popped totem grants, read from the item that was in the player's hands right before the pop. HolyWorld
 * wiki («Особые предметы»): the rune «Бессмертие» combined with a totem gives <b>3 seconds</b> of invulnerability
 * after the totem activates; the rune «Восстановление» fully restores health instead. The rune shows in the
 * talisman's lore; {@link Talisman#parse} recognises it. Pure Java, unit tested.
 *
 * @param kind    what the pop grants
 * @param seconds invulnerability length (0 unless {@link Kind#INVULNERABLE})
 * @param source  where {@code seconds} came from
 */
public record RuneEffect(Kind kind, double seconds, Source source) {
    /** Wiki value for the «Бессмертие» rune. */
    public static final double IMMORTALITY_SECONDS = 3.0;
    /** A number of seconds written in the lore is trusted only within this range. */
    static final double MIN_SECONDS = 0.5;
    static final double MAX_SECONDS = 10.0;
    private static final Pattern SECONDS = Pattern.compile("(\\d{1,2}(?:[.,]\\d{1,2})?)\\s*(?:секунд[аы]?|сек|с|sec|s)(?!\\p{L})");

    public enum Kind {
        /** «Бессмертие»: no damage for {@link #seconds()}. */
        INVULNERABLE,
        /** «Восстановление»: full health, no invulnerability. */
        RESTORED,
        /** A totem without a rune: nothing to wait for. */
        NONE,
        /** The item was not seen (out of view before the pop, or no totem visible in the hands). */
        UNKNOWN
    }

    public enum Source {
        /** A duration written in the item's lore. */
        LORE,
        /** The wiki value of the recognised rune. */
        WIKI,
        /** The user's fallback for unknown items. */
        FALLBACK,
        NONE
    }

    public static final RuneEffect NOTHING = new RuneEffect(Kind.NONE, 0, Source.NONE);
    public static final RuneEffect UNKNOWN = new RuneEffect(Kind.UNKNOWN, 0, Source.NONE);

    /**
     * Classifies the totem item that popped.
     *
     * @param name custom name of the item ("" for a plain totem)
     * @param lore lore lines as plain text
     */
    public static RuneEffect of(String name, List<String> lore) {
        Talisman talisman = Talisman.parse(name, lore);
        Talisman.Rune rune = talisman == null ? null : talisman.rune();
        if (rune == null) {
            rune = looseRune(lore);
        }
        if (rune == Talisman.Rune.RESTORATION) {
            return new RuneEffect(Kind.RESTORED, 0, Source.WIKI);
        }
        if (rune == Talisman.Rune.IMMORTALITY) {
            double fromLore = loreSeconds(lore);
            return Double.isNaN(fromLore) ? new RuneEffect(Kind.INVULNERABLE, IMMORTALITY_SECONDS, Source.WIKI)
                    : new RuneEffect(Kind.INVULNERABLE, fromLore, Source.LORE);
        }
        return NOTHING;
    }

    /** The unknown case resolved with the user's fallback length. */
    public static RuneEffect fallback(double seconds) {
        return new RuneEffect(Kind.INVULNERABLE, seconds, Source.FALLBACK);
    }

    /** Whether the chip for this effect is a guess (shown muted, with a question mark). */
    public boolean guessed() {
        return source == Source.FALLBACK;
    }

    /**
     * A rune named without the word «руна»: an «Бессмертие» (nominative, so not the «тотем бессмертия» item name)
     * or «неуязвимость» line means immortality, a «Восстановление» line restoration.
     */
    static Talisman.@Nullable Rune looseRune(List<String> lore) {
        for (String line : lore) {
            List<String> words = HolyText.words(line);
            if (words.contains("бессмертие") || HolyText.normalize(line).contains("неуязвим")) {
                return Talisman.Rune.IMMORTALITY;
            }
            if (words.contains("восстановление") && !words.contains("здоровья")) {
                return Talisman.Rune.RESTORATION;
            }
        }
        return null;
    }

    /**
     * Seconds written next to the rune ({@code «Неуязвимость на 3 сек»}, {@code «Бессмертие (2.5с)»}); NaN when no
     * line about the rune carries a plausible number.
     */
    static double loreSeconds(List<String> lore) {
        for (String line : lore) {
            String text = HolyText.normalize(line);
            if (!(text.contains("бессмерт") || text.contains("неуязвим") || text.contains("руна"))) {
                continue;
            }
            Matcher m = SECONDS.matcher(text);
            while (m.find()) {
                double value = Double.parseDouble(m.group(1).replace(',', '.'));
                if (value >= MIN_SECONDS && value <= MAX_SECONDS) {
                    return value;
                }
            }
        }
        return Double.NaN;
    }

    @Override
    public String toString() {
        return kind + (kind == Kind.INVULNERABLE ? String.format(Locale.ROOT, " %.1fs (%s)", seconds, source) : "");
    }
}
