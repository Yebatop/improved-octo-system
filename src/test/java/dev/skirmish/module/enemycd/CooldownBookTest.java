package dev.skirmish.module.enemycd;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownBookTest {
    @Test
    void learnsFromOwnCooldowns() {
        CooldownBook book = new CooldownBook();
        assertEquals(-1, book.durationMs(CooldownBook.Kind.PEARL, 0));
        assertTrue(book.learn("minecraft:ender_pearl", 120));
        assertFalse(book.learn("minecraft:ender_pearl", 120));
        assertFalse(book.learn("minecraft:diamond", 40));
        assertEquals(6000, book.durationMs(CooldownBook.Kind.PEARL, 0));
        assertEquals(5000, book.durationMs(CooldownBook.Kind.SHIELD, 100));
        assertNull(CooldownBook.byGroup("minecraft:stone"));
    }

    @Test
    void marksExpire() {
        CooldownBook book = new CooldownBook();
        UUID bob = UUID.randomUUID();
        book.mark(bob, CooldownBook.Kind.PEARL, 1000, 6000);
        book.mark(bob, CooldownBook.Kind.GAPPLE, 1000, -1);
        assertEquals(2, book.active(bob, 2000, 20000).size());
        assertEquals(5000, book.active(bob, 2000, 20000).stream().filter(m -> m.kind() == CooldownBook.Kind.PEARL).findFirst().orElseThrow().leftMs(2000));
        assertEquals(1, book.active(bob, 7500, 20000).size());
        assertEquals(0, book.active(bob, 30000, 20000).size());
        assertFalse(book.hasMarks());
    }

    @Test
    void rowText() {
        assertEquals("5", CooldownRow.text(new CooldownBook.Mark(CooldownBook.Kind.PEARL, 0, 6000), 1500));
        assertEquals("+4", CooldownRow.text(new CooldownBook.Mark(CooldownBook.Kind.PEARL, 0, -1), 4200));
    }
}
