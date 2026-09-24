package dev.skirmish.module.events;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatCoordsTest {
    private static ChatCoords.Coords one(String text) {
        List<ChatCoords.Coords> found = ChatCoords.find(text);
        assertEquals(1, found.size(), text + " -> " + found);
        return found.getFirst();
    }

    private static void none(String text) {
        assertTrue(ChatCoords.find(text).isEmpty(), text + " -> " + ChatCoords.find(text));
    }

    @Test
    void labeled() {
        ChatCoords.Coords c = one("Контейнер появился! x: 123 y: 64 z: -50");
        assertEquals(123, c.x());
        assertEquals(64, c.y());
        assertEquals(-50, c.z());
        assertEquals("Контейнер появился! x: 123 y: 64 z: -50".length(), c.end());
        c = one("Координаты: X=1500, Y=70, Z=-2300.");
        assertEquals(1500, c.x());
        assertEquals(-2300, c.z());
        c = one("х: -10 у: 80 z: 7");
        assertEquals(-10, c.x());
        assertEquals(80, c.y());
    }

    @Test
    void labeledWithoutHeight() {
        ChatCoords.Coords c = one("Груз упал на X 123, Z -50");
        assertEquals(123, c.x());
        assertNull(c.y());
        assertEquals(-50, c.z());
        assertEquals("123 ~ -50", c.text());
    }

    @Test
    void bareTriples() {
        ChatCoords.Coords c = one("Ивент на 123 64 -50");
        assertEquals(123, c.x());
        assertEquals(64, c.y());
        assertEquals(-50, c.z());
        one("корабль [-1520, 71, 830]");
        one("иду на -1520 71 830.");
        one("0 64 1000");
        assertEquals(2, ChatCoords.find("то ли 100 64 100, то ли 200 70 -200").size());
    }

    @Test
    void notCoordinates() {
        none("Продам за 1 250 000 монет");
        none("Цена 2 100 500 ¤");
        none("1 2 3 4");
        none("Рестарт в 04:30, осталось 5 мин");
        none("Версии 1.16.5 - 1.21.11");
        none("123 999 -50");
        none("Дата 23.09.2026 12 64 18:00");
    }

    @Test
    void eventNames() {
        Set<String> live = Set.of("Контейнер", "Опытный Тыпо");
        assertEquals("Контейнер", ChatCoords.eventName("[Ивент] КОНТЕЙНЕР появился на 1 64 2", live));
        assertEquals("Опытный Тыпо", ChatCoords.eventName("Опытный Тыпо ждёт вас", live));
        assertEquals("Таинственный груз", ChatCoords.eventName("Таинственный груз упадёт через 5 минут", Set.of()));
        assertEquals("Золотая лихорадка", ChatCoords.eventName("Началась золотая лихорадка!", Set.of()));
        assertNull(ChatCoords.eventName("Вася: го 100 64 100", live));
    }

    @Test
    void dimensionsAndVotes() {
        assertEquals("minecraft:the_nether", ChatCoords.dimensionHint("Босс в аду на 1 64 2"));
        assertEquals("minecraft:the_end", ChatCoords.dimensionHint("Захват Энда начался"));
        assertNull(ChatCoords.dimensionHint("Контейнер 1 64 2"));
        assertTrue(ChatCoords.isVoteStart("Началось голосование за следующий ивент! /vote"));
        assertFalse(ChatCoords.isVoteStart("Вася: кто за голосование?"));
    }
}
