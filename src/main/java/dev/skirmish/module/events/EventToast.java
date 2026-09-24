package dev.skirmish.module.events;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.minecraft.util.Util;

import java.util.ArrayDeque;

/** Short toast when a new event instance appears (same surface as the KillCard toast). Queued, one at a time. */
final class EventToast extends HudBlock {
    private record Shown(String name, Rarity rarity, String chip, String server) {
    }

    private final EventsModule module;
    private final ArrayDeque<Shown> queue = new ArrayDeque<>();
    private Shown current;
    private long shownAt = -1;

    EventToast(EventsModule module) {
        super("event_toast", "skirmish.hud.element.event_toast", new Placement(1, 0, 1, 0, -Theme.get().num("layout.screen_edge"),
                Theme.get().num(EventRows.L + "toast_default_y")));
        this.module = module;
    }

    void push(String name, Rarity rarity, String rareRaw, String server) {
        if (queue.size() >= Theme.get().integer(EventRows.L + "toast_queue")) {
            queue.pollFirst();
        }
        queue.addLast(new Shown(name, rarity, EventRows.chipText(rarity, rareRaw), server));
    }

    void clear() {
        queue.clear();
        current = null;
        shownAt = -1;
    }

    private long duration() {
        return Math.round(Theme.get().num(EventRows.L + "toast_ms"));
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.toastScope() != EventsModule.ToastScope.OFF;
    }

    @Override
    public boolean shown() {
        long now = Util.getMillis();
        if ((current == null || now - shownAt >= duration()) && !queue.isEmpty()) {
            current = queue.pollFirst();
            shownAt = now;
        }
        return current != null && now - shownAt < duration();
    }

    @Override
    public boolean hasContent() {
        return current != null;
    }

    private Shown data(boolean preview) {
        return preview || current == null ? new Shown("Контейнер", Rarity.LEGENDARY, EventRows.chipText(Rarity.LEGENDARY, ""), "ДуоЛайт #17") : current;
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(EventRows.L + "toast_width");
    }

    @Override
    public float height(Ui ui, boolean preview) {
        float line = Math.max(ui.lineHeight("event_name"), EventRows.chipHeight(ui));
        return HudStyle.insetY(ui) * 2 + ui.lineHeight("toast_title") + ui.num(EventRows.L + "sub_gap") + line
                + ui.num(EventRows.L + "sub_gap") + ui.lineHeight("toast_sub");
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        Shown s = data(preview);
        float w = width(ui, preview);
        HudStyle.panel(ui, x, y, w, height(ui, preview));
        float cx = x + HudStyle.insetX(ui);
        float cw = w - HudStyle.insetX(ui) * 2;
        float cy = y + HudStyle.insetY(ui);
        ui.text("toast_title", Ui.tr("skirmish.events.toast.title"), cx, cy);
        cy += ui.lineHeight("toast_title") + ui.num(EventRows.L + "sub_gap");
        float line = Math.max(ui.lineHeight("event_name"), EventRows.chipHeight(ui));
        boolean chip = !s.chip().isEmpty();
        float chipW = chip ? EventRows.chipWidth(ui, s.chip()) + ui.num(EventRows.L + "chip_gap") : 0f;
        String name = ui.ellipsize("event_name", s.name(), cw - chipW);
        float after = ui.textCentered("event_name", name, cx, cy, line);
        if (chip) {
            EventRows.chip(ui, s.rarity(), s.chip(), after + ui.num(EventRows.L + "chip_gap"), cy + (line - EventRows.chipHeight(ui)) / 2f);
        }
        cy += line + ui.num(EventRows.L + "sub_gap");
        ui.text("toast_sub", ui.ellipsize("toast_sub", s.server(), cw), cx, cy);
    }
}
