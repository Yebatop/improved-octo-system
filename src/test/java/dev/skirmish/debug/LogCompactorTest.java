package dev.skirmish.debug;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogCompactorTest {
    private static List<String> all(LogCompactor c, long t, String tag, String msg) {
        List<String> out = new ArrayList<>();
        for (LogCompactor.Line l : c.accept(t, tag, msg)) {
            out.add(l.tag() + " " + l.message());
        }
        return out;
    }

    /** The server re-sending the same effect every tick (user's log, 2026-09-29): one line, then a count. */
    @Test
    void repeatsFold() {
        LogCompactor c = new LogCompactor();
        String effect = "effect applied: minecraft:speed amplifier=2 ticks=2147483647";
        assertEquals(List.of("hw_item_timers " + effect), all(c, 0, "hw_item_timers", effect));
        for (int i = 1; i <= 40; i++) {
            assertTrue(all(c, i * 50L, "hw_item_timers", effect).isEmpty());
        }
        // Other modules in between do not break the fold.
        assertEquals(List.of("killcam stats"), all(c, 2100, "killcam", "stats"));
        assertEquals(List.of("hw_item_timers ↑ ещё 40 раз за 1 с", "hw_item_timers raw chat: hi"),
                all(c, 2200, "hw_item_timers", "raw chat: hi"));
    }

    @Test
    void longRepeatsAndFloodsAreSummed() {
        LogCompactor c = new LogCompactor();
        all(c, 0, "a", "x");
        all(c, 10, "a", "x");
        assertTrue(c.tick(1000).isEmpty());
        assertEquals("↑ ещё 1 раз за 0 с", c.tick(LogCompactor.REPEAT_FLUSH_MS + 10).getFirst().message());

        LogCompactor f = new LogCompactor();
        int written = 0;
        for (int i = 0; i < LogCompactor.PER_MINUTE + 50; i++) {
            written += all(f, i, "chat", "line " + i).size();
        }
        assertEquals(LogCompactor.PER_MINUTE, written);
        assertEquals(1, all(f, 5, "chat", "ERROR boom").size());
        assertTrue(f.tick(60_001).getFirst().message().contains("50 строк"));
    }
}
