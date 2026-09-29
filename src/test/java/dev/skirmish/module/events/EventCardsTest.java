package dev.skirmish.module.events;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventCardsTest {
    private static final String RULE = " ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";

    /** A real card (game log, 2026-09-29), its lines a millisecond apart. */
    @Test
    void cardLinesBelongToTheTitlesEvent() {
        EventCards cards = new EventCards();
        List<String> live = List.of("Опытный Тыпо", "Контейнер");
        assertNull(cards.feed(RULE, live, 1000));
        assertEquals("Опытный Тыпо", cards.feed(" ▍ Опытный Тыпо", live, 1000));
        assertEquals("Опытный Тыпо", cards.feed(" ▍", live, 1000));
        assertEquals("Опытный Тыпо", cards.feed(" ▍ Редкость: Эпическая", live, 1001));
        assertEquals("Опытный Тыпо", cards.feed(" ▍ Координаты: 109 69 356 [+метка]", live, 1001));
        assertNull(cards.feed(RULE, live, 1001));
        // After the closing rule, a stray ▍ line is no longer the card's.
        assertNull(cards.feed(" ▍ Координаты: 1 2 3", live, 1002));
    }

    @Test
    void cardFieldsAndOtherBlocks() {
        assertEquals(new EventCards.Field("Ценный груз", "Взрывной"), EventCards.field(" ▍ Тип: Взрывной", "Ценный груз"));
        assertEquals(new EventCards.Field("Опытный Тыпо", "Эпическая"), EventCards.field(" ▍ Редкость: Эпическая", "Опытный Тыпо"));
        assertNull(EventCards.field(" ▍ Координаты: 109 69 356", "Опытный Тыпо"));
        assertNull(EventCards.field(" ▍ Тип: Взрывной", null));

        // An ad block drawn the same way names no event, so its lines are nobody's.
        EventCards cards = new EventCards();
        assertNull(cards.feed(" ▍ подать заявку на стажёра через Telegram-бота:", List.of(), 0));
        assertNull(cards.feed(" ▍ Координаты: 1 2 3", List.of(), 0));
        // Too late after the title: not the same card.
        assertEquals("Кубик", cards.feed(" ▍ Кубик", List.of(), 0));
        assertNull(cards.feed(" ▍ Координаты: 1 2 3", List.of(), EventCards.WINDOW + 1));
    }

    @Test
    void chipsAreTranslatedNotRaw() {
        assertEquals(Rarity.RARE, Rarity.parse("ship_zajit_f"));
        assertEquals(Rarity.EPIC, Rarity.parse("ship_roskoshni_f"));
        assertEquals(Rarity.EPIC, Rarity.parse("Эпическая"));
        assertEquals(Rarity.UNKNOWN, Rarity.parse("EXPLOSIVE"));
        assertEquals("explosive", EventRows.variantKey("EXPLOSIVE"));
        assertEquals("medium", EventRows.variantKey("MEDIUM"));
        assertNull(EventRows.variantKey("default"));
    }
}
