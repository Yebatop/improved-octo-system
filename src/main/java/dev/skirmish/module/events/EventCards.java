package dev.skirmish.module.events;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HolyWorld announces an event on its own server with a card of separate chat lines (captured 2026-09):
 * <pre>
 *  ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬
 *  ▍ Опытный Тыпо
 *  ▍
 *  ▍ Редкость: Эпическая        (or «Тип: Взрывной», «Тип: Редкая»)
 *  ▍ Координаты: 109 69 356 [+метка]
 *  ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬
 * </pre>
 * Only the title line names the event, so the lines after it are tied to it here. Pure Java.
 */
final class EventCards {
    /** A card's lines arrive together; a later ▍ line is not part of it. */
    static final long WINDOW = 3_000;
    private static final Pattern FIELD = Pattern.compile("^(тип|редкость|сложность)\\s*:\\s*(.+)$");

    /** A «Тип: …» or «Редкость: …» line of a card: the event and the value as the server wrote it. */
    record Field(String event, String value) {
    }

    private @Nullable String event;
    private long at;

    /**
     * Feeds a chat line: the event a card's line belongs to (its title line included), or null for any other line.
     */
    @Nullable String feed(String text, Collection<String> liveNames, long now) {
        String t = text.strip();
        if (t.startsWith("▬")) {
            event = null;
            return null;
        }
        if (!t.startsWith("▍")) {
            return null;
        }
        String body = t.substring(1).strip();
        if (body.isEmpty() || body.contains(":")) {
            return event != null && now - at <= WINDOW ? event : null;
        }
        String name = ChatCoords.eventName(body, liveNames);
        if (name != null && body.length() <= name.length() + 2) {
            event = name;
            at = now;
            return name;
        }
        event = null;
        return null;
    }

    /** The «Тип» / «Редкость» field of a card line tied to {@code event}, else null. */
    static @Nullable Field field(String text, @Nullable String event) {
        if (event == null) {
            return null;
        }
        String t = text.strip();
        if (!t.startsWith("▍")) {
            return null;
        }
        String body = t.substring(1).strip();
        Matcher m = FIELD.matcher(body.toLowerCase(Locale.ROOT));
        if (!m.find()) {
            return null;
        }
        String value = body.substring(m.start(2)).strip();
        return value.isEmpty() ? null : new Field(event, value);
    }
}
