package dev.skirmish.module.runewindow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuneWindowLogicTest {
    @Test
    void immortalityRuneUsesTheWikiLength() {
        RuneEffect e = RuneEffect.of("Талисман Infinity", List.of("• Урон II", "• Броня I", "Руна: Бессмертие"));
        assertEquals(RuneEffect.Kind.INVULNERABLE, e.kind());
        assertEquals(3.0, e.seconds(), 1e-9);
        assertEquals(RuneEffect.Source.WIKI, e.source());
        assertFalse(e.guessed());
    }

    @Test
    void lengthWrittenInTheLoreWins() {
        RuneEffect e = RuneEffect.of("§6Тотем бессмертия", List.of("§7Руна «Бессмертие»", "§7Неуязвимость на 2,5 сек после активации"));
        assertEquals(RuneEffect.Kind.INVULNERABLE, e.kind());
        assertEquals(2.5, e.seconds(), 1e-9);
        assertEquals(RuneEffect.Source.LORE, e.source());
        // Implausible numbers are ignored.
        assertEquals(3.0, RuneEffect.of("", List.of("Руна Бессмертие: 99 сек")).seconds(), 1e-9);
        assertEquals(4.0, RuneEffect.loreSeconds(List.of("Бессмертие 4s")), 1e-9);
        assertTrue(Double.isNaN(RuneEffect.loreSeconds(List.of("Урон 3 сек"))));
    }

    @Test
    void runeWithoutTheWordRune() {
        assertEquals(RuneEffect.Kind.INVULNERABLE, RuneEffect.of("", List.of("Эффект: Бессмертие")).kind());
        assertEquals(RuneEffect.Kind.INVULNERABLE, RuneEffect.of("", List.of("Даёт неуязвимость")).kind());
        assertEquals(RuneEffect.Kind.RESTORED, RuneEffect.of("", List.of("Восстановление")).kind());
    }

    @Test
    void restorationAndPlainTotems() {
        assertEquals(RuneEffect.Kind.RESTORED, RuneEffect.of("Талисман", List.of("• Скорость I", "Руна Восстановление")).kind());
        // A plain totem (its own name says «бессмертия», genitive) grants nothing to wait for.
        assertEquals(RuneEffect.Kind.NONE, RuneEffect.of("Тотем бессмертия", List.of()).kind());
        assertEquals(RuneEffect.Kind.NONE, RuneEffect.of("Сфера Kraken", List.of("• Урон II")).kind());
        RuneEffect fb = RuneEffect.fallback(2.0);
        assertTrue(fb.guessed());
        assertEquals(2.0, fb.seconds(), 1e-9);
    }

    @Test
    void handCacheRemembersTheTotemBrieflyAfterItVanished() {
        HandCache<String> cache = new HandCache<>();
        UUID a = UUID.randomUUID();
        assertNull(cache.popped(a, 0));
        cache.update(a, "rune", 0);
        assertEquals("rune", cache.popped(a, 10));
        // The equipment update removed the totem before the pop arrived.
        cache.update(a, null, 50);
        assertEquals("rune", cache.popped(a, 100));
        assertNull(cache.popped(a, HandCache.GRACE_MS + 1));
        cache.update(a, "plain", 2_000);
        assertEquals("plain", cache.popped(a, 2_000));
        cache.retain(Set.of());
        assertFalse(cache.sampled(a));
        assertEquals(0, cache.size());
    }

    @Test
    void timersRestartExpireAndFormat() {
        RuneTimers timers = new RuneTimers();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        timers.start(new RuneTimers.Timer(a, 1, "A", RuneEffect.Kind.INVULNERABLE, 0, 3_000, false));
        timers.start(new RuneTimers.Timer(b, 2, "B", RuneEffect.Kind.INVULNERABLE, 500, 3_000, true));
        assertEquals(List.of("B", "A"), timers.live().stream().map(RuneTimers.Timer::name).toList());
        // A second pop of A restarts its window and moves it to the top.
        timers.start(new RuneTimers.Timer(a, 1, "A", RuneEffect.Kind.INVULNERABLE, 1_000, 3_000, false));
        assertEquals(2, timers.live().size());
        assertEquals("A", timers.live().getFirst().name());
        assertEquals(List.of("B"), timers.expire(3_600).stream().map(RuneTimers.Timer::name).toList());
        assertEquals(0.5f, timers.live().getFirst().fraction(2_500), 1e-6);
        assertTrue(timers.expire(3_999).isEmpty());
        assertEquals(1, timers.expire(4_000).size());
        assertTrue(timers.isEmpty());

        assertEquals("3.0", RuneTimers.seconds(3_000));
        assertEquals("2.5", RuneTimers.seconds(2_401));
        assertEquals("0.1", RuneTimers.seconds(1));
        assertEquals("0.0", RuneTimers.seconds(0));
    }

    @Test
    void atMostFourWindows() {
        RuneTimers timers = new RuneTimers();
        for (int i = 0; i < 6; i++) {
            timers.start(new RuneTimers.Timer(UUID.randomUUID(), i, "P" + i, RuneEffect.Kind.INVULNERABLE, i, 3_000, false));
        }
        assertEquals(RuneTimers.MAX, timers.live().size());
        assertEquals("P5", timers.live().getFirst().name());
    }
}
