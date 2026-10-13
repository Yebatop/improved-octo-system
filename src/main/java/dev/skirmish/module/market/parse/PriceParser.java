package dev.skirmish.module.market.parse;

import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coin amounts as HolyWorld writes and accepts them: plain {@code 1500}, grouped {@code 1 000 000},
 * {@code 1,500,000} or {@code 1.500.000}, and the {@code /ah sell} shorthand {@code 1k} = 1 000,
 * {@code 1kk} = {@code 1m} = 1 000 000 (Cyrillic {@code к}/{@code кк}/{@code м}, {@code тыс}, {@code млн},
 * {@code млрд} too), with a decimal part before a suffix ({@code 1.5m}, {@code 2,5кк}). Pure Java (unit tested).
 */
public final class PriceParser {
    /**
     * Grouped thousands (space-like, comma or dot separators, all groups of three), or plain digits; an optional
     * decimal part; an optional shorthand suffix that is not the start of a longer word ("м" vs "монет").
     */
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![\\d.,])(\\d{1,3}(?:[ '\\u00A0\\u202F]\\d{3})+(?!\\d)|\\d{1,3}(?:,\\d{3})+(?![\\d])(?!,\\d)|\\d{1,3}(?:\\.\\d{3})+(?![\\d])(?!\\.\\d)|\\d+)"
                    + "(?:[.,](\\d+))?"
                    + "(?:\\s?(ккк|kkk|кк|kk|млрд|млн|тыс|k|к|m|м|b|б)(?![a-zа-яё]))?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private PriceParser() {
    }

    /** The first amount in {@code text}, or empty. Fractions of a coin are rounded. */
    public static OptionalLong parse(String text) {
        if (text == null) {
            return OptionalLong.empty();
        }
        Matcher m = AMOUNT.matcher(text);
        return m.find() ? toValue(m) : OptionalLong.empty();
    }

    /** Parses a whole token such as {@code 1.5kk}; anything else around the number makes it invalid. */
    public static OptionalLong parseExact(String token) {
        if (token == null) {
            return OptionalLong.empty();
        }
        Matcher m = AMOUNT.matcher(token.strip());
        return m.matches() ? toValue(m) : OptionalLong.empty();
    }

    private static OptionalLong toValue(Matcher m) {
        String digits = m.group(1).replaceAll("[^0-9]", "");
        String fraction = m.group(2);
        double multiplier = multiplier(m.group(3));
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(fraction == null ? digits : digits + "." + fraction)
                    .multiply(java.math.BigDecimal.valueOf(multiplier));
            if (value.compareTo(java.math.BigDecimal.valueOf(Long.MAX_VALUE / 2)) > 0) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(value.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact());
        } catch (NumberFormatException | ArithmeticException e) {
            return OptionalLong.empty();
        }
    }

    private static double multiplier(String suffix) {
        if (suffix == null) {
            return 1;
        }
        return switch (suffix.toLowerCase(java.util.Locale.ROOT)) {
            case "k", "к", "тыс" -> 1e3;
            case "kk", "кк", "m", "м", "млн" -> 1e6;
            case "kkk", "ккк", "b", "б", "млрд" -> 1e9;
            default -> 1;
        };
    }
}
