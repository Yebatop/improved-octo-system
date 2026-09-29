package dev.skirmish.module.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPureTest {
    private static final String RULE = " ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";

    /** Real lines (game log, 2026-09-29), nicknames replaced. */
    @Test
    void tabsOfHolyWorldLines() {
        assertEquals(ChatTabs.Tab.TRADE, ChatTabs.classify("ɢ | «ᴇᴛᴇʀɴɪᴛʏ» Player_1 сигма свинка: меч infinity на ставках 1 минута"));
        assertEquals(ChatTabs.Tab.TRADE, ChatTabs.classify("ɢ | Player_2 : обменяю тнт пушку на дк лс"));
        assertEquals(ChatTabs.Tab.PLAYERS, ChatTabs.classify("ʟ | «ᴡɪᴛʜᴇʀ» Player_3 : окуп"));
        assertEquals(ChatTabs.Tab.TRADE, ChatTabs.classify("[Объявление] кто купит премку через /garant (Автор: Player_4)"));
        assertEquals(ChatTabs.Tab.EVENTS, ChatTabs.classify("▶ Ценный груз находится на координатах 1447 64 -1581 [+метка] и взорвется через 235 секунд."));
        assertEquals(ChatTabs.Tab.EVENTS, ChatTabs.classify(" ▍ Опытный Тыпо"));
        assertEquals(ChatTabs.Tab.EVENTS, ChatTabs.classify("▶ Началось голосование за следующее мероприятие."));
        assertEquals(ChatTabs.Tab.SYSTEM, ChatTabs.classify("▶ Вы вошли в режим PVP!"));
        assertEquals(ChatTabs.Tab.SYSTEM, ChatTabs.classify("▶ Ваш баланс пополнен на 500 ¤"));
        assertEquals(" окуп", ChatTabs.body("ʟ | «ᴡɪᴛʜᴇʀ» Player_3 : окуп"));
        assertNull(ChatTabs.body("▶ Вы вошли в режим PVP!"));
    }

    @Test
    void stackCounts() {
        assertEquals("меч на ставках", ChatStack.base("меч на ставках ×3"));
        assertEquals(3, ChatStack.count("меч на ставках ×3"));
        assertEquals(1, ChatStack.count("меч на ставках"));
        assertFalse(ChatStack.stackable(RULE));
        assertFalse(ChatStack.stackable(" ▍"));
        assertTrue(ChatStack.stackable("окуп"));
    }

    private static CardCompactor<String> compactor() {
        return new CardCompactor<>(card -> card.title().strip().substring(1).strip() + " · "
                + String.join(" · ", card.fieldTexts()) + " · " + card.coords().strip().substring(1).strip());
    }

    @Test
    void cardBecomesOneLine() {
        CardCompactor<String> c = compactor();
        for (String line : List.of(RULE, " ▍ Опытный Тыпо", " ▍", " ▍ Редкость: Эпическая", " ▍ Координаты: 109 69 356 [+метка]")) {
            CardCompactor.Out<String> out = c.offer(line, line, 5);
            assertTrue(out.taken());
            assertTrue(out.add().isEmpty());
        }
        CardCompactor.Out<String> end = c.offer(RULE, RULE, 5);
        assertTrue(end.taken());
        assertEquals(List.of("Опытный Тыпо · Эпическая · Координаты: 109 69 356 [+метка]"), end.add());
        assertFalse(c.holding());
    }

    @Test
    void otherBlocksGoOutAsTheyCame() {
        CardCompactor<String> c = compactor();
        c.offer(RULE, RULE, 0);
        c.offer(" ▍ Защищай сервер", " ▍ Защищай сервер", 0);
        c.offer(" ▍ подать заявку через Telegram-бота:", " ▍ подать заявку через Telegram-бота:", 0);
        CardCompactor.Out<String> end = c.offer(RULE, RULE, 0);
        assertEquals(4, end.add().size());

        // A card cut short by another line: held lines first, then that line as usual.
        c.offer(RULE, RULE, 0);
        c.offer(" ▍ Кубик", " ▍ Кубик", 0);
        CardCompactor.Out<String> cut = c.offer("ɢ | Player_1: привет", "ɢ | Player_1: привет", 0);
        assertFalse(cut.taken());
        assertEquals(List.of(RULE, " ▍ Кубик"), cut.add());

        // Or by time.
        c.offer(RULE, RULE, 0);
        c.offer(" ▍ Кубик", " ▍ Кубик", 0);
        assertTrue(c.flushIfStale(CardCompactor.STALE_TICKS).isEmpty());
        assertEquals(2, c.flushIfStale(CardCompactor.STALE_TICKS + 1).size());
    }
}
