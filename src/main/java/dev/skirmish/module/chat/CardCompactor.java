package dev.skirmish.module.chat;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Folds HolyWorld's event cards into one chat line (captured 2026-09):
 * <pre>
 *  ▬▬▬▬▬▬▬▬                    ⚡ Опытный Тыпо · Эпическая · 109 69 356 [+метка]
 *  ▍ Опытный Тыпо
 *  ▍                      →
 *  ▍ Редкость: Эпическая
 *  ▍ Координаты: 109 69 356 [+метка]
 *  ▬▬▬▬▬▬▬▬
 * </pre>
 * The card's lines are held as they come (they arrive in the same tick) and the one line goes out at the closing
 * rule. A card with a line it does not know, or with no title, goes out as it came; so does one cut short (a
 * different line arrives, or {@link #STALE_TICKS} pass). Generic over the line type, so it is tested with strings.
 */
final class CardCompactor<T> {
    static final int STALE_TICKS = 10;
    private static final Pattern FIELD = Pattern.compile("^(тип|редкость|сложность)\\s*:.*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern COORDS = Pattern.compile("^координат\\p{L}*\\s*:.*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** A held card: its title line, «Тип»/«Редкость» lines and the coordinates line (with their texts). */
    record Card<T>(T title, List<T> fields, List<String> fieldTexts, @Nullable T coords) {
    }

    /** What to add now (in order), and whether the offered line was taken (held, or already among {@code add}). */
    record Out<T>(List<T> add, boolean taken) {
    }

    private final Function<Card<T>, T> compose;
    private final List<T> raw = new ArrayList<>();
    private @Nullable T title;
    private final List<T> fields = new ArrayList<>();
    private final List<String> fieldTexts = new ArrayList<>();
    private @Nullable T coords;
    private boolean unknown;
    private long openedAt;

    CardCompactor(Function<Card<T>, T> compose) {
        this.compose = compose;
    }

    Out<T> offer(T line, String text, long tick) {
        String t = text.strip();
        boolean rule = t.startsWith("▬");
        boolean bar = t.startsWith("▍");
        if (!rule && !bar) {
            return new Out<>(raw.isEmpty() ? List.of() : release(false), false);
        }
        if (raw.isEmpty()) {
            openedAt = tick;
        }
        if (rule && !raw.isEmpty()) {
            raw.add(line);
            return new Out<>(release(true), true);
        }
        raw.add(line);
        if (bar) {
            String body = t.substring(1).strip();
            if (body.isEmpty()) {
                return new Out<>(List.of(), true);
            }
            if (title == null && fields.isEmpty() && coords == null && !body.contains(":")) {
                title = line;
            } else if (FIELD.matcher(body).matches()) {
                fields.add(line);
                fieldTexts.add(body.substring(body.indexOf(':') + 1).strip());
            } else if (COORDS.matcher(body).matches() && coords == null) {
                coords = line;
            } else {
                unknown = true;
            }
        }
        return new Out<>(List.of(), true);
    }

    /** Lets out a card held too long (as one line when it is complete enough, else as it came). */
    List<T> flushIfStale(long tick) {
        return !raw.isEmpty() && tick - openedAt > STALE_TICKS ? release(true) : List.of();
    }

    boolean holding() {
        return !raw.isEmpty();
    }

    private List<T> release(boolean mayCompose) {
        List<T> out;
        if (mayCompose && title != null && coords != null && !unknown) {
            out = List.of(compose.apply(new Card<>(title, List.copyOf(fields), List.copyOf(fieldTexts), coords)));
        } else {
            out = List.copyOf(raw);
        }
        raw.clear();
        title = null;
        fields.clear();
        fieldTexts.clear();
        coords = null;
        unknown = false;
        return out;
    }
}
