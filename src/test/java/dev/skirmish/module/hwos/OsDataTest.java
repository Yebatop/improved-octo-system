package dev.skirmish.module.hwos;

import dev.skirmish.module.market.parse.PriceHistory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsDataTest {
    @Test
    void groupsAnarchies() {
        List<String> names = List.of("ДуоЛайт #18", "СолоЛайт #2", "Лайт (1.20) #2", "ДуоЛайт #17", "СолоЛайт #10", "ТриоЛайт #20");
        List<OsData.Group> groups = OsData.groups(names);
        assertEquals(List.of("СолоЛайт", "ДуоЛайт", "ТриоЛайт", "Лайт (1.20)"), groups.stream().map(OsData.Group::kind).toList());
        assertEquals(List.of(1, 4), groups.getFirst().members());
        assertEquals(List.of(3, 0), groups.get(1).members());
    }

    private static PriceHistory.Sample s(long t, double p) {
        return new PriceHistory.Sample(t, p, "ah");
    }

    @Test
    void findsDeals() {
        long now = 10_000_000L;
        Map<String, List<PriceHistory.Sample>> h = Map.of(
                "cheap", List.of(s(now - 5000, 100), s(now - 4000, 110), s(now - 3000, 90), s(now - 1000, 50)),
                "normal", List.of(s(now - 5000, 100), s(now - 4000, 100), s(now - 3000, 100), s(now - 1000, 95)),
                "stale", List.of(s(now - 9_000_000, 100), s(now - 8_000_000, 100), s(now - 7_000_000, 100), s(now - 6_000_000, 10)),
                "few", List.of(s(now - 1000, 1)));
        List<OsData.Deal> deals = OsData.deals(h, now, 20_000_000L, 3_600_000L, 0.75, 3);
        assertEquals(1, deals.size());
        assertEquals("cheap", deals.getFirst().key());
        assertEquals(95, deals.getFirst().median(), 1e-9);
        assertTrue(deals.getFirst().off() > 0.4);
    }

    @Test
    void spans() {
        assertEquals("1д 4ч", OsData.span((28 * 60 + 11) * 60_000L, "x", "д", "ч", "м"));
        assertEquals("16ч 41м", OsData.span((16 * 60 + 41) * 60_000L + 36_000, "x", "д", "ч", "м"));
        assertEquals("2ч", OsData.span(120 * 60_000L, "x", "д", "ч", "м"));
        assertEquals("12:30", OsData.span(750_000L, "12:30", "д", "ч", "м"));
    }

    @Test
    void readsSidebarPairs() {
        List<OsData.Pair> pairs = OsData.pairs(List.of("§fНик: §aPlayer", "\uE001 Баланс: 12 500", "HolyWorld.ru", "Клан:", "Пинг: 12"));
        assertEquals(3, pairs.size());
        assertEquals("Ник", pairs.getFirst().label());
        assertEquals("Player", pairs.getFirst().value());
        assertEquals("Баланс", pairs.get(1).label());
        assertEquals("12 500", pairs.get(1).value());
        assertEquals("minecraft:player_head|сфера", "minecraft:player_head|" + OsData.customName("minecraft:player_head|сфера"));
        assertNull(OsData.customName("minecraft:diamond"));
    }
}
