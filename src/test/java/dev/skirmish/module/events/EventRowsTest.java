package dev.skirmish.module.events;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventRowsTest {
    private static EventRows.Row row(String name) {
        return new EventRows.Row(name, null, "", "", "text", null, false);
    }

    @Test
    void collapsedKeepsTheFirstRowsAndSections() {
        EventRows.Section lite = new EventRows.Section("Лайт");
        EventRows.Section prime = new EventRows.Section("Прайм");
        List<EventRows.Item> items = List.of(lite, row("a"), row("b"), row("c"), prime, row("d"));
        List<EventRows.Item> head = EventRows.collapsed(items, 2);
        assertEquals(List.of(lite, row("a"), row("b")), head);
        assertEquals(2, EventRows.hiddenRows(items, head));
    }

    @Test
    void notesCountAsRowsAndShortListsStayWhole() {
        EventRows.Note loading = new EventRows.Note("Загрузка…");
        List<EventRows.Item> items = List.of(loading, row("a"), row("b"));
        assertEquals(List.of(loading, row("a")), EventRows.collapsed(items, 2));
        assertEquals(1, EventRows.hiddenRows(items, EventRows.collapsed(items, 2)));

        List<EventRows.Item> shortList = List.of(new EventRows.Section("Лайт"), row("a"));
        assertEquals(shortList, EventRows.collapsed(shortList, 2));
        assertEquals(0, EventRows.hiddenRows(shortList, EventRows.collapsed(shortList, 2)));
        assertEquals(List.of(), EventRows.collapsed(List.of(), 2));
    }
}
