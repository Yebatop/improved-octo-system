package dev.skirmish.module.market.parse;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketParseTest {
    @Test
    void textNormalization() {
        assertEquals("Продавец: Steve", HwText.plain("§7▍ §fПродавец§7: §eSteve ▶"));
        assertEquals("продавец", HwText.key("Продaвeц"));
        assertEquals("- рюкзак infinity -", HwText.normalize("§a- Рюкзак Iɴғɪɴɪᴛʏ -"));
        // Latin words keep their letters: only words with Cyrillic are folded.
        assertEquals("steve", HwText.key("Steve"));
    }

    @Test
    void auctionTitles() {
        for (String title : List.of("Аукцион", "Аукционы", "§8Аукцион [1/12]", "ᴀукцион", "Аукцион » Торги", "Аукцион: ставки",
                "/ah", "Меню /ah", "AH", "Рынок", "Рынок игроков", "Auction House", "Aукциoн")) {
            assertTrue(AuctionTitle.isAuction(title), title);
        }
        for (String title : List.of("", "Сундук", "Chest", "Большой сундук", "Покупка предмета", "Подтверждение покупки",
                "Скупщик", "Биржа", "Ahead", "Рюкзак", "Shop")) {
            assertFalse(AuctionTitle.isAuction(title), title);
        }
    }

    @Test
    void amounts() {
        assertEquals(OptionalLong.of(1500), PriceParser.parse("1500"));
        assertEquals(OptionalLong.of(1_000_000), PriceParser.parse("1 000 000 ¤"));
        assertEquals(OptionalLong.of(1_000_000), PriceParser.parse("1\u00A0000\u00A0000$"));
        assertEquals(OptionalLong.of(1_500_000), PriceParser.parse("1,500,000"));
        assertEquals(OptionalLong.of(1_500_000), PriceParser.parse("1.500.000 монет"));
        assertEquals(OptionalLong.of(1000), PriceParser.parse("1k"));
        assertEquals(OptionalLong.of(1_000_000), PriceParser.parse("1kk"));
        assertEquals(OptionalLong.of(1_000_000), PriceParser.parse("1m"));
        assertEquals(OptionalLong.of(1_500_000), PriceParser.parse("1.5m"));
        assertEquals(OptionalLong.of(2_500_000), PriceParser.parse("2,5кк"));
        assertEquals(OptionalLong.of(5_000_000), PriceParser.parse("5KK"));
        assertEquals(OptionalLong.of(12_500), PriceParser.parse("12.5 к"));
        assertEquals(OptionalLong.of(3_000_000), PriceParser.parse("3 млн"));
        assertEquals(OptionalLong.of(750), PriceParser.parse("750 монет"));
        assertEquals(OptionalLong.of(13), PriceParser.parse("12.5$"));
        assertEquals(OptionalLong.empty(), PriceParser.parse("нет цены"));
        assertEquals(OptionalLong.of(1_000_000), PriceParser.parseExact("1kk"));
        assertEquals(OptionalLong.empty(), PriceParser.parseExact("1kk монет"));
    }

    @Test
    void lotLore() {
        LotParser.Lot lot = LotParser.parse(List.of("§7Особенности:", "§7▍ Продавец: §eNotch", "§7▍ Цена: §a1 250 000¤", "§7▍ Истекает: 23 ч."));
        assertNotNull(lot);
        assertEquals(1_250_000, lot.price());
        assertEquals(LotParser.Kind.TOTAL, lot.kind());
        assertEquals("Notch", lot.seller());

        lot = LotParser.parse(List.of("▎ Продaвeц ▶ Dream_2", "▎ Стоимость: 5kk"));
        assertNotNull(lot);
        assertEquals(5_000_000, lot.price());
        assertEquals("Dream_2", lot.seller());

        lot = LotParser.parse(List.of("Seller: abc", "$ 1,500"));
        assertNotNull(lot);
        assertEquals(1500, lot.price());

        lot = LotParser.parse(List.of("Продавец: x", "Цена за 1 шт: 40", "Количество: 64"));
        assertNotNull(lot);
        assertEquals(LotParser.Kind.UNIT, lot.kind());
        assertEquals(40, lot.price());

        lot = LotParser.parse(List.of("Начальная цена: 1000", "Текущая ставка: 4 500", "Шаг ставки: 100"));
        assertNotNull(lot);
        assertEquals(LotParser.Kind.BID, lot.kind());
        assertEquals(4500, lot.price());

        assertNull(LotParser.parse(List.of("Острота VII", "Прочность V")));
        assertNull(LotParser.parse(List.of()));
    }

    @Test
    void purchaseLines() {
        PurchaseLine.Purchase p = PurchaseLine.parse("§aВы купили [Алмаз] x64 у Steve за 12 800¤");
        assertNotNull(p);
        assertEquals("Алмаз", p.item());
        assertEquals(64, p.count());
        assertEquals("Steve", p.seller());
        assertEquals(12_800, p.price());

        p = PurchaseLine.parse("[AH] Вы успешно купили Незеритовый слиток за 1,500,000");
        assertNotNull(p);
        assertEquals("Незеритовый слиток", p.item());
        assertEquals(1, p.count());
        assertNull(p.seller());
        assertEquals(1_500_000, p.price());

        assertNull(PurchaseLine.parse("У Вас купили Алмаз на /ah"));
        assertNull(PurchaseLine.parse("Steve: вы купили что-то"));
        assertTrue(PurchaseLine.looksLikePurchase("Игрок Steve купил у вас Алмаз за 100¤"));
        assertFalse(PurchaseLine.looksLikePurchase("Привет всем"));
    }

    @Test
    void history() {
        PriceHistory h = new PriceHistory(5, 2);
        assertTrue(h.recordLot("a", "s", 100, 10, 1000, 60_000));
        assertFalse(h.recordLot("a", "s", 100, 10, 2000, 60_000), "same lot within the dedup window");
        assertTrue(h.recordLot("a", "s", 100, 10, 70_000, 60_000));
        h.add("a", new PriceHistory.Sample(3000, 30, "buy"));
        assertEquals(10.0, h.median("a", 80_000, 0, 1).getAsDouble());
        assertTrue(h.median("a", 80_000, 0, 4).isEmpty());
        for (int i = 0; i < 10; i++) {
            h.add("a", new PriceHistory.Sample(100_000 + i, 1 + i, "ah"));
        }
        assertEquals(5, h.samples("a").size(), "capped per item");
        assertEquals(8.0, h.median("a", 200_000, 0, 1).getAsDouble());
        assertEquals(9.0, h.median("a", 100_009, 2, 1).getAsDouble(), "only samples within maxAge");
        h.add("b", new PriceHistory.Sample(1, 1, "ah"));
        h.samples("a");
        h.add("c", new PriceHistory.Sample(1, 1, "ah"));
        assertEquals(2, h.size());
        assertTrue(h.samples("b").isEmpty(), "least recently used item evicted");

        PriceHistory copy = new PriceHistory(5, 2);
        copy.loadJson(h.toJson());
        assertEquals(h.samples("a"), copy.samples("a"));
        assertEquals(h.samples("c"), copy.samples("c"));

        assertEquals(PriceHistory.Verdict.CHEAP, PriceHistory.verdict(50, 100, 0.4));
        assertEquals(PriceHistory.Verdict.NORMAL, PriceHistory.verdict(90, 100, 0.4));
        assertEquals(PriceHistory.Verdict.DEAR, PriceHistory.verdict(150, 100, 0.4));
    }

    @Test
    void formatting() {
        assertEquals("950", PriceFormat.compact(950, ',', "к", "кк", "ккк"));
        assertEquals("12,5", PriceFormat.compact(12.5, ',', "к", "кк", "ккк"));
        assertEquals("1,25к", PriceFormat.compact(1250, ',', "к", "кк", "ккк"));
        assertEquals("125к", PriceFormat.compact(125_000, ',', "к", "кк", "ккк"));
        assertEquals("1кк", PriceFormat.compact(999_700, ',', "к", "кк", "ккк"));
        assertEquals("3.4kk", PriceFormat.compact(3_400_000, '.', "k", "kk", "kkk"));
        assertEquals("1 234 567", PriceFormat.full(1_234_567, ','));
        assertEquals("12,5", PriceFormat.full(12.5, ','));
        assertEquals("100", PriceFormat.full(100, ','));
    }
}
