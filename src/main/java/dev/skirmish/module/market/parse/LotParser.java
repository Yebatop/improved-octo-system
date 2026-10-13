package dev.skirmish.module.market.parse;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an auction lot from its lore lines (strings as shown, § codes allowed). Pure Java (unit tested).
 * <p>Evidence (public sources, not captured in game yet): the lot lore has {@code Продавец: <nick>} decorated with
 * {@code ▍ ▶ ▎}, and a price line with {@code Цена:} or {@code Стоимость:} and/or {@code ¤}/{@code $}. Bid lots
 * ("Ставки") show the current bid, which is not a final price.
 */
public final class LotParser {
    /** What the price line means. */
    public enum Kind {
        /** Price of the whole stack. */
        TOTAL,
        /** The server already shows a price per item. */
        UNIT,
        /** Current bid of a "Ставки" lot: may still rise. */
        BID
    }

    /** A parsed lot; {@code price} in coins. */
    public record Lot(long price, Kind kind, @Nullable String seller) {
    }

    private static final Pattern PRICE_KEY = Pattern.compile("цена|стоимость|price|cost");
    private static final Pattern BID_KEY = Pattern.compile("ставк|\\bbid");
    /** "Шаг ставки": the bid increment, not a price. */
    private static final Pattern STEP_KEY = Pattern.compile("шаг|step|increment");
    private static final Pattern UNIT_KEY = Pattern.compile("за\\s*(1\\s*)?(шт|ед)|/\\s*шт|per\\s*(item|unit|1)|each");
    private static final Pattern CURRENCY = Pattern.compile("[¤$₽]|монет|coins?\\b");
    private static final Pattern SELLER_KEY = Pattern.compile("продавец|seller|владелец");
    /** Nick: first run of name characters after the key. */
    private static final Pattern NICK = Pattern.compile("[\\p{L}\\p{N}_.\\-]+");

    private LotParser() {
    }

    public static @Nullable Lot parse(List<String> loreLines) {
        Long total = null;
        Long unit = null;
        Long bid = null;
        Long loose = null;
        String seller = null;
        for (String raw : loreLines) {
            String plain = HwText.plain(raw);
            String key = HwText.key(plain);
            if (seller == null) {
                seller = seller(plain, key);
                if (seller != null) {
                    continue;
                }
            }
            Matcher price = PRICE_KEY.matcher(key);
            boolean hasKey = price.find();
            boolean hasCurrency = CURRENCY.matcher(key).find();
            boolean isBid = BID_KEY.matcher(key).find();
            if (!hasKey && !hasCurrency && !isBid) {
                continue;
            }
            if (isBid && STEP_KEY.matcher(key).find()) {
                continue;
            }
            // Read the amount after the keyword's colon (or the keyword) when there is one, else anywhere.
            int from = 0;
            Matcher keyword = hasKey ? price : null;
            if (keyword == null && isBid) {
                keyword = BID_KEY.matcher(key);
                keyword = keyword.find() ? keyword : null;
            }
            if (keyword != null) {
                int colon = plain.indexOf(':', keyword.end());
                from = colon >= 0 ? colon + 1 : keyword.end();
            }
            var amount = PriceParser.parse(plain.substring(from));
            if (amount.isEmpty() || amount.getAsLong() <= 0) {
                continue;
            }
            long value = amount.getAsLong();
            if (isBid) {
                bid = bid == null ? value : bid;
            } else if (UNIT_KEY.matcher(key).find()) {
                unit = unit == null ? value : unit;
            } else if (hasKey) {
                total = total == null ? value : total;
            } else {
                loose = loose == null ? value : loose;
            }
        }
        // A lot showing a bid is a "Ставки" lot even if it also shows its starting price.
        if (bid != null) {
            return new Lot(bid, Kind.BID, seller);
        }
        if (total != null) {
            return new Lot(total, Kind.TOTAL, seller);
        }
        if (loose != null) {
            return new Lot(loose, Kind.TOTAL, seller);
        }
        if (unit != null) {
            return new Lot(unit, Kind.UNIT, seller);
        }
        return null;
    }

    /** The nick after {@code Продавец:} (look-alike letters tolerated), or null. */
    static @Nullable String seller(String plain, String key) {
        Matcher m = SELLER_KEY.matcher(key);
        if (!m.find()) {
            return null;
        }
        String rest = plain.substring(m.end()).replaceFirst("^[\\s:：\\-–—]+", "");
        Matcher nick = NICK.matcher(rest);
        return nick.lookingAt() ? nick.group() : null;
    }
}
