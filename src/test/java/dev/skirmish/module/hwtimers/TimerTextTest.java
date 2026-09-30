package dev.skirmish.module.hwtimers;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimerTextTest {
    private static OptionalInt seconds(String raw) {
        return TimerText.seconds(TimerText.normalize(raw));
    }

    @Test
    void readsDurations() {
        assertEquals(OptionalInt.of(15), seconds("Стан: 15с"));
        assertEquals(OptionalInt.of(15), seconds("осталось 15 секунд"));
        assertEquals(OptionalInt.of(299), seconds("Рейд-блок 4:59"));
        assertEquals(OptionalInt.of(65), seconds("1м 5с"));
        assertEquals(OptionalInt.of(300), seconds("на 5 минут"));
        assertEquals(OptionalInt.empty(), seconds("Стан активирован"));
        assertEquals(OptionalInt.empty(), seconds("12:75"));
    }

    @Test
    void normalizesCodesSmallCapsAndYo() {
        assertEquals("ледяная волна", TimerText.normalize("§bЛедяная  §lВолна"));
        assertEquals("pvp", TimerText.normalize("ᴘᴠᴘ"));
        assertEquals("надежный стиллер", TimerText.normalize("Надёжный Стиллер"));
    }

    @Test
    void recognisesEndAnnouncements() {
        assertTrue(TimerText.announcesEnd(TimerText.normalize("Действие стана закончилось")));
        assertTrue(TimerText.announcesEnd(TimerText.normalize("Стан спал")));
        assertFalse(TimerText.announcesEnd(TimerText.normalize("Вас оглушили станом на 15 секунд")));
        assertFalse(TimerText.announcesEnd(TimerText.normalize("Спальник")));
    }

    @Test
    void playerChatIsNotAServerEvent() {
        List<String> online = List.of("Steve_2010", "Alex");
        assertTrue(TimerText.looksLikePlayerChat("[G] Steve_2010: кинул стан", online));
        assertTrue(TimerText.looksLikePlayerChat("ᴀʟᴇx » стан у меня", online));
        assertFalse(TimerText.looksLikePlayerChat("Вы попали под Стан! Жемчуг заблокирован", online));
        // A nick after the separator (the server naming the activator) is not a speaker.
        assertFalse(TimerText.looksLikePlayerChat("Стан: активировал Steve_2010", online));
        // A nick that is only part of a longer word does not count.
        assertFalse(TimerText.looksLikePlayerChat("Alexandra: стан", online));
    }

    @Test
    void tradeLinesAreNotTimerTriggers() {
        assertTrue(TimerText.isTradeLine(TimerText.normalize("▶ Вы купили Трапка x1 у shwaigen67 за 180 000¤")));
        assertTrue(TimerText.isTradeLine(TimerText.normalize("Вы выставили Стан на аукцион за 5000¤")));
        assertTrue(TimerText.isTradeLine(TimerText.normalize("   IGOR_TOP4IKI выиграл «Трапка» из Донат-кейса")));
        assertTrue(TimerText.isTradeLine(TimerText.normalize("Вы продали Ледяная волна x2 скупщику")));
        assertFalse(TimerText.isTradeLine(TimerText.normalize("Вы оглушены на 15 сек. Жемчуг и хорус недоступны")));
        assertFalse(TimerText.isTradeLine(TimerText.normalize("Стан закончился")));
        assertFalse(TimerText.isTradeLine(TimerText.normalize("Режим PVP, не выходите из игры 30 сек.")));
    }
}
