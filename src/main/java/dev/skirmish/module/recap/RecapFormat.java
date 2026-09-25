package dev.skirmish.module.recap;

import java.util.Locale;

/** Number formats of the recap (screen and PNG). Pure Java, covered by tests. */
public final class RecapFormat {
    private RecapFormat() {
    }

    /** Up to two decimals with the given separator, trailing zeros dropped: {@code 2,33}, {@code 2,5}, {@code 3}. */
    public static String kd(double kd, char decimal) {
        String text = String.format(Locale.ROOT, "%.2f", kd);
        text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        return text.replace('.', decimal);
    }

    /** {@code 1:05:07} or {@code 5:07}. */
    public static String playtime(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600;
        long m = s / 60 % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s % 60) : String.format(Locale.ROOT, "%d:%02d", m, s % 60);
    }

    /** Signed with thin grouping: {@code +12 345}, {@code −800}, {@code 0}. */
    public static String signed(long value) {
        String digits = group(Math.abs(value));
        return value > 0 ? "+" + digits : value < 0 ? "−" + digits : digits;
    }

    static String group(long value) {
        String raw = Long.toString(value);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            if (i > 0 && (raw.length() - i) % 3 == 0) {
                out.append(' ');
            }
            out.append(raw.charAt(i));
        }
        return out.toString();
    }

    /** One decimal place with the given separator ({@code 18,5}). */
    public static String hp(float value, char decimal) {
        return String.format(Locale.ROOT, "%.1f", value).replace('.', decimal);
    }

    /** Russian plural form of {@code n}: one / few / many (languages without "few" map it to "many" in their files). */
    public static String plural(long n) {
        long mod10 = Math.abs(n) % 10;
        long mod100 = Math.abs(n) % 100;
        if (mod10 == 1 && mod100 != 11) {
            return "one";
        }
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return "few";
        }
        return "many";
    }
}
