package dev.skirmish.module.market.parse;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Price text for chips and tooltips. Pure Java (unit tested). */
public final class PriceFormat {
    private PriceFormat() {
    }

    /**
     * Short form for a slot chip in the server's own shorthand: {@code 950}, {@code 12,5}, {@code 1,2к},
     * {@code 125к}, {@code 3,4кк}, {@code 1,1ккк}. At most three significant digits.
     */
    public static String compact(double value, char decimal, String k, String kk, String kkk) {
        if (!(value >= 0) || Double.isInfinite(value)) {
            return "?";
        }
        String suffix = "";
        double v = value;
        if (value >= 999_500_000) {
            v = value / 1e9;
            suffix = kkk;
        } else if (value >= 999_500) {
            v = value / 1e6;
            suffix = kk;
        } else if (value >= 1000) {
            v = value / 1e3;
            suffix = k;
        }
        int decimals = v >= 100 ? 0 : v >= 10 ? 1 : 2;
        String text = new BigDecimal(v).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return text.replace('.', decimal) + suffix;
    }

    /** Full amount with space-separated groups: {@code 1 234 567}, fractions below 100 kept ({@code 12,5}). */
    public static String full(double value, char decimal) {
        if (!(value >= 0) || Double.isInfinite(value)) {
            return "?";
        }
        int decimals = value >= 100 ? 0 : 2;
        BigDecimal rounded = new BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros();
        String plain = rounded.toPlainString();
        int dot = plain.indexOf('.');
        String whole = dot >= 0 ? plain.substring(0, dot) : plain;
        String fraction = dot >= 0 ? plain.substring(dot + 1) : "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < whole.length(); i++) {
            if (i > 0 && (whole.length() - i) % 3 == 0) {
                out.append(' ');
            }
            out.append(whole.charAt(i));
        }
        if (!fraction.isEmpty()) {
            out.append(decimal).append(fraction);
        }
        return out.toString();
    }
}
