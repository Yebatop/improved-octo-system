package dev.skirmish.module.scoreboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidebarTextTest {
    @Test
    void labelValueLinesSplitAtTheColon() {
        assertEquals(6, SidebarText.split("Баланс: 12 500"));
        assertEquals(3, SidebarText.split("Ник: Player"));
        assertEquals(-1, SidebarText.split("12:30"));
        assertEquals(-1, SidebarText.split("Сайт: "));
        assertEquals(-1, SidebarText.split("https://holyworld.ru"));
        assertEquals(-1, SidebarText.split("ДуоЛайт #17"));
    }

    @Test
    void iconsByLabel() {
        assertEquals("coin", SidebarText.icon("Баланс"));
        assertEquals("shield", SidebarText.icon("Клан"));
        assertEquals("signal", SidebarText.icon("Пинг"));
        assertEquals("sword", SidebarText.icon("Убийств"));
        assertEquals("skull", SidebarText.icon("Смертей"));
        assertEquals("clock", SidebarText.icon("КТ"));
        assertEquals("dot", SidebarText.icon("Что-то"));
    }

    @Test
    void modFontCoversTextButNotSymbols() {
        assertTrue(SidebarText.modFont("Баланс: 12 500¤ — ok"));
        assertFalse(SidebarText.modFont("Bob (27 \u231A) 16/20 \u2764"));
        assertFalse(SidebarText.modFont("\uE123"));
    }

    @Test
    void privateUseGlyphs() {
        assertTrue(SidebarText.customGlyphs(" Баланс"));
        assertFalse(SidebarText.customGlyphs("Баланс"));
    }
}
