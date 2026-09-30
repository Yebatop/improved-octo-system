package dev.skirmish.module.market.parse;

import org.jspecify.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The chat line after buying a lot. Not captured on HolyWorld yet; the shapes come from a public third-party parser
 * ({@code Вы купили [<item>] x<N> у <seller> за <price>¤}) and a variant without the seller
 * ({@code Вы успешно купили <item> за <price>}). Lines that mention buying but match neither are reported by
 * {@link #looksLikePurchase} so the module can log them as capture candidates. Pure Java (unit tested).
 */
public final class PurchaseLine {
    /** {@code count} is 1 when the line has no {@code xN}; {@code seller} may be null. */
    public record Purchase(String item, int count, @Nullable String seller, long price) {
    }

    private static final Pattern FULL = Pattern.compile(
            "(?:^|[^\\p{L}])вы\\s+(?:успешно\\s+)?купили\\s+(?:[-–—]\\s*)?(?:\\[(?<b>[^\\]]+)]|(?<n>.+?))\\s*(?:[-–—]?\\s*[xх×]\\s?(?<c>\\d+))?"
                    + "(?:\\s+у\\s+(?:игрока\\s+)?(?<s>[\\p{L}\\p{N}_.\\-]+))?\\s+за\\s+(?<p>.+)$");
    private static final Pattern HINT = Pattern.compile("купил|куплен|покупк|purchased|bought");

    private PurchaseLine() {
    }

    public static @Nullable Purchase parse(String line) {
        String plain = HwText.plain(line);
        String key = HwText.fold(plain);
        Matcher m = FULL.matcher(key);
        if (!m.find()) {
            return null;
        }
        var price = PriceParser.parse(plain.substring(m.start("p")));
        if (price.isEmpty() || price.getAsLong() <= 0) {
            return null;
        }
        String item = m.group("b") != null ? plain.substring(m.start("b"), m.end("b")) : plain.substring(m.start("n"), m.end("n"));
        int count = m.group("c") == null ? 1 : Math.max(1, Integer.parseInt(m.group("c")));
        String seller = m.group("s") == null ? null : plain.substring(m.start("s"), m.end("s"));
        return new Purchase(item.strip(), count, seller, price.getAsLong());
    }

    /** Mentions buying and a number: worth logging when {@link #parse} failed. */
    public static boolean looksLikePurchase(String line) {
        String key = HwText.normalize(line);
        return HINT.matcher(key).find() && key.matches(".*\\d.*");
    }
}
