package dev.skirmish.module.events;

import dev.skirmish.hud.HudStyle;
import dev.skirmish.ui.Ui;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Shared drawing for the event panels: a list of items (section labels, event rows with a rarity chip and a
 * right-aligned time, empty-state lines) inside the standard HUD panel. All sizes and colors are theme tokens.
 */
final class EventRows {
    static final String L = "layout.events.";

    sealed interface Item permits Section, Row, Note {
    }

    /** Small caps label above a group ("ЛАЙТ", "ПРАЙМ"). */
    record Section(String label) implements Item {
    }

    /**
     * One event: name (+ chip), a right-aligned value in color token {@code rightColor}, and an optional second
     * line ({@code subMono}: coordinates in the mono face).
     */
    record Row(String name, @Nullable Rarity rarity, String chip, String right, String rightColor,
               @Nullable String sub, boolean subMono) implements Item {
    }

    /** Muted single line (no data, nothing running). */
    record Note(String text) implements Item {
    }

    private EventRows() {
    }

    /**
     * The collapsed list: everything up to and including the {@code rows}-th event row or note (section labels in
     * between stay), so it is always a prefix of {@code items}.
     */
    static List<Item> collapsed(List<Item> items, int rows) {
        int seen = 0;
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof Section) && ++seen >= rows) {
                return items.subList(0, i + 1);
            }
        }
        return items;
    }

    /** Event rows of {@code items} that the collapsed list leaves out. */
    static int hiddenRows(List<Item> items, List<Item> collapsed) {
        int hidden = 0;
        for (Item item : items.subList(collapsed.size(), items.size())) {
            if (item instanceof Row) {
                hidden++;
            }
        }
        return hidden;
    }

    static float height(Ui ui, List<Item> items) {
        float h = HudStyle.insetY(ui) * 2 + HudStyle.headerHeight(ui);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            h += i == 0 || item instanceof Section ? ui.num("layout.panel_gap") : ui.num(L + "row_gap");
            if (i > 0 && item instanceof Section) {
                h += ui.num(L + "section_gap") - ui.num("layout.panel_gap");
            }
            h += itemHeight(ui, item);
        }
        return h;
    }

    private static float itemHeight(Ui ui, Item item) {
        return switch (item) {
            case Section s -> ui.lineHeight("event_section");
            case Note n -> ui.lineHeight("event_empty");
            case Row r -> Math.max(ui.lineHeight("event_name"), Math.max(ui.lineHeight("event_time"), chipHeight(ui)))
                    + (r.sub() == null ? 0f : ui.num(L + "sub_gap") + ui.lineHeight(r.subMono() ? "event_coords" : "event_sub"));
        };
    }

    /** Panel with header ({@code icon} draws at the returned slot) and the items. */
    static void render(Ui ui, float x, float y, float w, String title, @Nullable String meta, List<Item> items, boolean clockIcon) {
        render(ui, x, y, w, height(ui, items), title, meta, items, clockIcon);
    }

    /**
     * As {@link #render(Ui, float, float, float, String, String, List, boolean)} in a panel of height {@code h}
     * (animated expand/collapse): items below the panel's inner bottom edge are clipped.
     */
    static void render(Ui ui, float x, float y, float w, float h, String title, @Nullable String meta, List<Item> items,
                       boolean clockIcon) {
        HudStyle.panel(ui, x, y, w, h);
        boolean clip = h < height(ui, items);
        if (clip) {
            ui.graphics().enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w),
                    (int) Math.ceil(y + h - HudStyle.insetY(ui)));
        }
        try {
            content(ui, x, y, w, title, meta, items, clockIcon);
        } finally {
            if (clip) {
                ui.graphics().disableScissor();
            }
        }
    }

    private static void content(Ui ui, float x, float y, float w, String title, @Nullable String meta, List<Item> items, boolean clockIcon) {
        float cx = x + HudStyle.insetX(ui);
        float cw = w - HudStyle.insetX(ui) * 2;
        float cy = y + HudStyle.insetY(ui);
        String shownMeta = meta == null ? null : ui.ellipsize("panel_meta", meta, cw * 0.5f);
        float[] icon = HudStyle.header(ui, cx, cy, cw, title, shownMeta);
        float size = ui.num("layout.panel_icon");
        if (clockIcon) {
            dev.skirmish.ui.Icons.clock(ui, icon[0], icon[1], size, ui.num(L + "icon_stroke"), ui.color("accent"));
        } else {
            bolt(ui, icon[0], icon[1], size, ui.color("accent"));
        }
        cy += HudStyle.headerHeight(ui);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            cy += i == 0 || item instanceof Section ? ui.num("layout.panel_gap") : ui.num(L + "row_gap");
            if (i > 0 && item instanceof Section) {
                cy += ui.num(L + "section_gap") - ui.num("layout.panel_gap");
            }
            switch (item) {
                case Section s -> ui.text("event_section", s.label().toUpperCase(Locale.ROOT), cx, cy);
                case Note n -> ui.text("event_empty", ui.ellipsize("event_empty", n.text(), cw), cx, cy);
                case Row r -> row(ui, r, cx, cy, cw);
            }
            cy += itemHeight(ui, item);
        }
    }

    private static void row(Ui ui, Row r, float x, float y, float w) {
        float lineH = Math.max(ui.lineHeight("event_name"), Math.max(ui.lineHeight("event_time"), chipHeight(ui)));
        float chipW = r.rarity() == null || r.chip().isEmpty() ? 0f : chipWidth(ui, r.chip());
        float gap = ui.num(L + "name_gap");
        // A long server label gives way to the event name first: it keeps at least 40 % of the row.
        String right = r.right();
        if (!right.isEmpty()) {
            float nameNeed = ui.textWidth("event_name", r.name()) + (chipW > 0 ? chipW + ui.num(L + "chip_gap") : 0f) + gap;
            right = ui.ellipsize("event_time", right, Math.max(w * 0.4f, w - nameNeed));
        }
        float rightW = right.isEmpty() ? 0f : ui.textWidth("event_time", right);
        float nameMax = w - rightW - (rightW > 0 ? gap : 0f) - (chipW > 0 ? chipW + ui.num(L + "chip_gap") : 0f);
        String name = ui.ellipsize("event_name", r.name(), Math.max(0f, nameMax));
        float after = ui.textCentered("event_name", name, x, y, lineH);
        if (chipW > 0) {
            chip(ui, r.rarity(), r.chip(), after + ui.num(L + "chip_gap"), y + (lineH - chipHeight(ui)) / 2f);
        }
        if (rightW > 0) {
            ui.textCentered("event_time", right, x + w - rightW, y, lineH, ui.color(r.rightColor()));
        }
        if (r.sub() != null) {
            String style = r.subMono() ? "event_coords" : "event_sub";
            ui.text(style, ui.ellipsize(style, r.sub(), w), x, y + lineH + ui.num(L + "sub_gap"));
        }
    }

    static float chipHeight(Ui ui) {
        return ui.lineHeight("event_chip") + ui.num(L + "chip_pad_y") * 2;
    }

    static float chipWidth(Ui ui, String text) {
        return ui.textWidth("event_chip", text.toUpperCase(Locale.ROOT)) + ui.num(L + "chip_pad_x") * 2;
    }

    /** Rarity chip; colors from {@code events.rarity.<tier>.fg/bg} in theme/events.json. */
    static void chip(Ui ui, @Nullable Rarity rarity, String text, float x, float y) {
        String tier = (rarity == null ? Rarity.UNKNOWN : rarity).key();
        String label = text.toUpperCase(Locale.ROOT);
        float w = chipWidth(ui, text);
        float h = chipHeight(ui);
        ui.rect(x, y, w, h, ui.theme().radius("chip"), ui.color(ui.theme().string("events.rarity." + tier + ".bg")));
        ui.text("event_chip", label, x + ui.num(L + "chip_pad_x"), y + ui.num(L + "chip_pad_y"),
                ui.color(ui.theme().string("events.rarity." + tier + ".fg")));
    }

    /** Chip label: the translated tier, or the raw API value when the tier is unknown. */
    static String chipText(Rarity rarity, String raw) {
        if (rarity != Rarity.UNKNOWN) {
            return Ui.tr("skirmish.events.rarity." + rarity.key());
        }
        String r = raw == null ? "" : raw.trim().replace('_', ' ');
        return r.length() > 10 ? r.substring(0, 10) : r;
    }

    /** Theme color token of a state ({@code events.state.<key>}). */
    static String stateColor(Ui ui, String key) {
        return ui.theme().string("events.state." + key);
    }

    /** Lightning bolt icon {@code M13 2L4 14h7l-1 8 9-12h-7z} in the 24-unit view box of the UI kit's icons. */
    static void bolt(Ui ui, float x, float y, float size, int color) {
        float s = size / 24f;
        float stroke = ui.num(L + "icon_stroke") * s;
        ui.polyline(stroke, color, x + 13 * s, y + 2 * s, x + 4 * s, y + 14 * s, x + 11 * s, y + 14 * s,
                x + 10 * s, y + 22 * s, x + 19 * s, y + 10 * s, x + 12 * s, y + 10 * s, x + 13 * s, y + 2 * s);
    }

    // ---- time text ----

    /** Countdown: "1:02:03" / "4:05", or "3 д 4 ч" from one day. */
    static String countdown(long ms) {
        if (ms >= 86_400_000L) {
            long hours = ms / 3_600_000L;
            return Ui.tr("skirmish.events.time.days", hours / 24, hours % 24);
        }
        return EventSchedule.clock(ms);
    }

    /** Local wall time "21:05", with a weekday when it is not today ("вс 16:00"). */
    static String local(Instant at) {
        ZonedDateTime local = at.atZone(ZoneId.systemDefault());
        String time = String.format(Locale.ROOT, "%02d:%02d", local.getHour(), local.getMinute());
        if (local.toLocalDate().equals(LocalDate.now(ZoneId.systemDefault()))) {
            return time;
        }
        return Ui.tr("skirmish.events.weekday." + local.getDayOfWeek().getValue()) + " " + time;
    }
}
