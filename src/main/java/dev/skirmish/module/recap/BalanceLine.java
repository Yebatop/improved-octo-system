package dev.skirmish.module.recap;

import dev.skirmish.module.gearinspector.holy.HolyText;
import dev.skirmish.module.market.parse.PriceParser;

import java.util.List;
import java.util.OptionalLong;

/**
 * Coin balance on a sidebar line. The wiki does not document the HolyWorld board's balance line, so any line naming
 * the game currency ({@code Баланс}, {@code Монеты}, {@code Монетки}, {@code Деньги}, {@code Balance}, {@code Money})
 * followed by an amount counts; lines about other currencies (сапфиры, коины, гемы, жетоны, аметисты) never do.
 * Amounts are read with the market's {@link PriceParser} ({@code 1 234 567}, {@code 1,2kk}). Pure Java, tested.
 */
public final class BalanceLine {
    private static final List<String> KEYWORDS = List.of("баланс", "монет", "деньги", "balance", "money");
    private static final List<String> OTHER = List.of("сапфир", "коин", "гем", "жетон", "аметист", "sapphire", "gem");

    private BalanceLine() {
    }

    public static OptionalLong parse(String line) {
        String text = HolyText.normalize(line);
        if (OTHER.stream().anyMatch(text::contains)) {
            return OptionalLong.empty();
        }
        int at = -1;
        for (String keyword : KEYWORDS) {
            int i = text.indexOf(keyword);
            if (i >= 0 && (at < 0 || i < at)) {
                at = i;
            }
        }
        if (at < 0) {
            return OptionalLong.empty();
        }
        return PriceParser.parse(text.substring(at));
    }

    /** The first line of the sidebar with a balance, or empty. */
    public static OptionalLong find(List<String> lines) {
        for (String line : lines) {
            OptionalLong value = parse(line);
            if (value.isPresent()) {
                return value;
            }
        }
        return OptionalLong.empty();
    }
}
