package dev.skirmish.module.market.parse;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Recognizes the auction GUI by its container title. HolyWorld documents {@code /ah} ("Главное меню Аукциона"), but
 * the exact titles are not public, so matching is tolerant: any title mentioning the auction or the market, in
 * Russian or English, with colors, small caps, page counters or look-alike letters. The buy-confirmation window
 * ("Покупка предмета") is not the lot list and is excluded.
 */
public final class AuctionTitle {
    private static final Pattern AUCTION = Pattern.compile(
            "аукцион|auction|(^|[^a-zа-я])/?(ah|auc)([^a-zа-я]|$)|рынок|рынка|рынке|маркет|market|барахолк");
    private static final List<String> EXCLUDED = List.of("покупка предмета", "подтвержд", "confirm", "помощь по маркету");

    private AuctionTitle() {
    }

    public static boolean isAuction(String title) {
        String key = HwText.normalize(title);
        if (key.isEmpty()) {
            return false;
        }
        for (String excluded : EXCLUDED) {
            if (key.contains(excluded)) {
                return false;
            }
        }
        return AUCTION.matcher(key).find();
    }
}
