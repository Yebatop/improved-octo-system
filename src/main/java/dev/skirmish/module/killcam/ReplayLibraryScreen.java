package dev.skirmish.module.killcam;

import dev.skirmish.module.killcam.library.ReplayFormatException;
import dev.skirmish.module.killcam.library.ReplayHeader;
import dev.skirmish.module.killcam.library.ReplayKind;
import dev.skirmish.module.killcam.library.ReplayStore;
import dev.skirmish.setting.StringSetting;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.Button;
import dev.skirmish.ui.widget.IconButton;
import dev.skirmish.ui.widget.Segmented;
import dev.skirmish.ui.widget.TextField;
import dev.skirmish.ui.widget.UiScreen;
import dev.skirmish.ui.widget.Widget;
import dev.skirmish.ui.widget.WindowFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * «Мои реплеи»: saved replays in a movable, resizable window. Filter (all / kills / deaths / clips / favourites),
 * search by name, and per replay: watch, star (protected from the cleanup), rename, delete (confirmed). The folder is
 * listed and replays are loaded on the store's thread; a spinner shows meanwhile. Watching needs a world: the replay
 * is drawn in the current level through the same replay screen as the live KillCam, and this screen comes back after.
 */
final class ReplayLibraryScreen extends UiScreen {
    private static final String L = "layout.replays.";
    private static final String M = "layout.menu.";

    enum Filter {
        ALL, KILLS, DEATHS, CLIPS, FAVOURITES;

        boolean accepts(ReplayHeader header) {
            return switch (this) {
                case ALL -> true;
                case KILLS -> header.kind == ReplayKind.KILL;
                case DEATHS -> header.kind == ReplayKind.DEATH;
                case CLIPS -> header.kind == ReplayKind.CLIP;
                case FAVOURITES -> header.favourite;
            };
        }
    }

    private enum Modal {
        NONE, RENAME, DELETE
    }

    private final KillCamModule module;
    private final ReplayStore store;
    private final WindowFrame frame = new WindowFrame("replays", L);
    private @Nullable List<ReplayStore.Entry> entries;
    private int skipped;
    private boolean loading;
    private Filter filter = Filter.ALL;
    private final Segmented filters = new Segmented(Segmented.Spec.MENU,
            () -> java.util.Arrays.stream(Filter.values()).map(f -> Ui.tr("skirmish.killcam.library.filter." + f.name().toLowerCase(Locale.ROOT))).toList(),
            () -> filter.ordinal(), i -> {
                filter = Filter.values()[i];
                scroll = 0;
            });
    private final StringSetting query = new StringSetting("query", "", 32, false);
    private final TextField search = new TextField(query, () -> scroll = 0).placeholder(() -> Ui.tr("skirmish.killcam.library.search"));
    private final Button folder = new Button(() -> Ui.tr("skirmish.killcam.library.folder"), false, this::openFolder);
    private final Button done = new Button(() -> Ui.tr("skirmish.menu.done"), true, this::onClose);
    private final Map<String, Row> rows = new HashMap<>();
    private @Nullable String opening;
    private String status = "";
    private boolean statusBad;
    private long statusUntil;
    private float scroll;
    private float maxScroll;

    private Modal modal = Modal.NONE;
    private ReplayStore.@Nullable Entry target;
    private final StringSetting newTitle = new StringSetting("title", "", ReplayHeader.MAX_TITLE, false);
    private final TextField titleField = new TextField(newTitle, () -> { });
    private final Button confirm = new Button(() -> Ui.tr(modal == Modal.DELETE ? "skirmish.killcam.library.delete.confirm"
            : "skirmish.killcam.library.rename.confirm"), true, this::confirmModal);
    private final Button cancel = new Button(() -> Ui.tr("skirmish.killcam.library.cancel"), false, this::closeModal);
    private final Widget blocker = new Widget() {
        @Override
        protected void draw(Ui ui, double mx, double my) {
            ui.rect(x, y, w, h, ui.theme().radius("window"), ui.color("backdrop"));
        }

        @Override
        public boolean clickable() {
            return false;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            return true;
        }
    };
    private final Runnable refresher = this::refresh;

    ReplayLibraryScreen(KillCamModule module, @Nullable Screen parent) {
        super(Component.translatable("skirmish.killcam.library.title"), parent);
        this.module = module;
        this.store = module.saver().store();
        titleField.placeholder(() -> target == null ? "" : ReplayTexts.title(autoTitled(target.header())));
    }

    private static ReplayHeader autoTitled(ReplayHeader header) {
        ReplayHeader copy = header.copy();
        copy.title = "";
        return copy;
    }

    @Override
    protected void init() {
        super.init();
        module.saver().removeListener(refresher);
        module.saver().onChange(refresher);
        refresh();
    }

    @Override
    public void removed() {
        module.saver().removeListener(refresher);
        super.removed();
    }

    // ---- data ----

    private void refresh() {
        loading = true;
        store.list().whenComplete((listing, error) -> Minecraft.getInstance().execute(() -> {
            loading = false;
            if (error != null) {
                module.error("replay library: listing failed", error);
                status(Ui.tr("skirmish.killcam.library.error.list", message(error)), true);
                if (entries == null) {
                    entries = new ArrayList<>();
                }
                return;
            }
            entries = new ArrayList<>(listing.entries());
            skipped = listing.skipped();
            module.log("replay library: %d replays (%.1f MiB), %d unreadable files skipped", entries.size(),
                    listing.totalBytes() / (1024.0 * 1024.0), skipped);
        }));
    }

    private List<ReplayStore.Entry> visible() {
        List<ReplayStore.Entry> out = new ArrayList<>();
        if (entries == null) {
            return out;
        }
        for (ReplayStore.Entry entry : entries) {
            if (filter.accepts(entry.header()) && entry.header().matches(query.get())) {
                out.add(entry);
            }
        }
        return out;
    }

    private long totalBytes() {
        long total = 0;
        if (entries != null) {
            for (ReplayStore.Entry entry : entries) {
                total += entry.bytes();
            }
        }
        return total;
    }

    private void replace(ReplayStore.Entry old, ReplayStore.Entry fresh) {
        if (entries != null) {
            int i = entries.indexOf(old);
            if (i >= 0) {
                entries.set(i, fresh);
            } else {
                entries.replaceAll(e -> e.id().equals(fresh.id()) ? fresh : e);
            }
        }
    }

    private void status(String text, boolean bad) {
        status = text;
        statusBad = bad;
        statusUntil = Util.getMillis() + 5000;
    }

    private static String message(Throwable error) {
        Throwable t = error;
        while ((t instanceof CompletionException || t instanceof UncheckedIOException) && t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof ReplayFormatException format) {
            return Ui.tr("skirmish.killcam.library.error." + format.problem().name().toLowerCase(Locale.ROOT));
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    // ---- actions ----

    private boolean canWatch() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && opening == null && !ReplaySession.isActive();
    }

    private void watch(ReplayStore.Entry entry) {
        if (!canWatch()) {
            return;
        }
        opening = entry.id();
        module.log("replay library: loading %s", entry.id());
        store.load(entry.file()).whenComplete((decoded, error) -> Minecraft.getInstance().execute(() -> {
            opening = null;
            if (error != null) {
                module.log("replay library: %s cannot be opened: %s", entry.id(), message(error));
                status(Ui.tr("skirmish.killcam.library.error.open", message(error)), true);
                refresh();
                return;
            }
            if (Minecraft.getInstance().screen != this) {
                module.log("replay library: %s loaded after the library was closed, not started", entry.id());
                return;
            }
            if (!module.watchSaved(decoded, this)) {
                status(Ui.tr("skirmish.killcam.library.error.start"), true);
            }
        }));
    }

    private void toggleFavourite(ReplayStore.Entry entry) {
        ReplayHeader header = entry.header().copy();
        header.favourite = !header.favourite;
        update(entry, header);
    }

    private void update(ReplayStore.Entry entry, ReplayHeader header) {
        ReplayStore.Entry optimistic = new ReplayStore.Entry(entry.file(), entry.bytes(), header);
        replace(entry, optimistic);
        store.update(entry.file(), header).whenComplete((saved, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                module.error("replay library: cannot update " + entry.id(), error);
                status(Ui.tr("skirmish.killcam.library.error.update", message(error)), true);
                refresh();
            } else {
                replace(optimistic, saved);
            }
        }));
    }

    private void delete(ReplayStore.Entry entry) {
        store.delete(entry.file()).whenComplete((deleted, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null || !Boolean.TRUE.equals(deleted)) {
                status(Ui.tr("skirmish.killcam.library.error.delete"), true);
            } else {
                if (entries != null) {
                    entries.removeIf(e -> e.id().equals(entry.id()));
                }
                rows.remove(entry.id());
                status(Ui.tr("skirmish.killcam.library.deleted", ReplayTexts.title(entry.header())), false);
            }
        }));
    }

    private void openFolder() {
        try {
            Files.createDirectories(store.directory());
        } catch (java.io.IOException e) {
            module.error("cannot create " + store.directory(), e);
            return;
        }
        Util.getPlatform().openPath(store.directory());
    }

    private void openModal(Modal kind, ReplayStore.Entry entry) {
        modal = kind;
        target = entry;
        if (kind == Modal.RENAME) {
            newTitle.set(entry.header().title);
            focus(titleField);
        } else {
            focus(null);
        }
    }

    private void closeModal() {
        modal = Modal.NONE;
        target = null;
        focus(null);
    }

    private void confirmModal() {
        ReplayStore.Entry entry = target;
        Modal kind = modal;
        closeModal();
        if (entry == null) {
            return;
        }
        if (kind == Modal.DELETE) {
            delete(entry);
        } else if (kind == Modal.RENAME) {
            ReplayHeader header = entry.header().copy();
            header.title = newTitle.get().strip();
            update(entry, header);
        }
    }

    // ---- drawing ----

    @Override
    protected void draw(Ui ui, double mx, double my) {
        ui.rect(0, 0, ui.width(), ui.height(), 0, ui.color("backdrop"));
        float stroke = ui.num("stroke.width");
        frame.layout(ui);
        widget(ui, frame.mover, mx, my);
        float w = frame.w();
        float h = frame.h();
        float ox = frame.x();
        float oy = frame.y() + Math.round((1f - appearProgress()) * ui.num("layout.appear_offset"));
        ui.box(ox, oy, w, h, ui.theme().radius("window"), ui.color("window"), ui.color("stroke_07"));

        float padX = ui.num(M + "content_pad_x");
        float padY = ui.num(M + "content_pad_y");
        float gap = ui.num(M + "content_gap");
        float x = ox + stroke + padX;
        float cw = w - (stroke + padX) * 2;
        float y = oy + stroke + padY;

        // Header: title, count and size; folder button on the right.
        folder.layout(M + "small_");
        float fw = folder.preferredWidth(ui);
        float fh = folder.preferredHeight(ui);
        folder.bounds(x + cw - fw, y, fw, fh);
        widget(ui, folder, mx, my);
        ui.text("menu_title", Ui.tr("skirmish.killcam.library.title"), x, y);
        y += ui.lineHeight("menu_title") + ui.num(M + "header_text_gap");
        ui.text("menu_desc", ui.ellipsize("menu_desc", summary(), cw), x, y);
        y += ui.lineHeight("menu_desc") + gap;

        // Toolbar: filter + search.
        float segH = filters.preferredHeight(ui);
        float fieldH = ui.num(M + "keybind_height");
        float barH = Math.max(segH, fieldH);
        float segW = filters.preferredWidth(ui);
        filters.bounds(x, y + (barH - segH) / 2f, segW, segH);
        widget(ui, filters, mx, my);
        float searchX = x + segW + ui.num(M + "footer_gap");
        float searchW = x + cw - searchX;
        if (searchW >= ui.num(L + "search_min_width")) {
            search.bounds(searchX, y + (barH - fieldH) / 2f, searchW, fieldH);
            widget(ui, search, mx, my);
        }
        y += barH + ui.num(L + "toolbar_gap");
        ui.hline(x, y, cw, ui.color("stroke"));
        y += stroke;

        // Footer: status or hint, «Готово».
        float bh = done.preferredHeight(ui);
        float fy = oy + h - stroke - padY - bh;
        float dw = done.preferredWidth(ui);
        done.bounds(x + cw - dw, fy, dw, bh);
        widget(ui, done, mx, my);
        String foot;
        int footColor;
        if (!status.isEmpty() && Util.getMillis() < statusUntil) {
            foot = status;
            footColor = ui.color(statusBad ? "bad" : "good");
        } else {
            foot = Ui.tr(Minecraft.getInstance().level == null ? "skirmish.killcam.library.hint_no_world" : "skirmish.killcam.library.hint");
            footColor = ui.color("text_3");
        }
        float hintW = cw - dw - ui.num(M + "footer_gap");
        ui.textCentered("menu_hint", ui.ellipsize("menu_hint", foot, hintW), x, fy, bh, footColor);

        float top = y;
        float bottom = fy - gap;
        drawList(ui, x, cw, ox, w, top, bottom, mx, my);
        widget(ui, frame.grip, mx, my);

        if (modal != Modal.NONE && target != null) {
            blocker.bounds(ox, oy, w, h);
            widget(ui, blocker, mx, my);
            drawModal(ui, ox, oy, w, h, mx, my);
        }
    }

    private String summary() {
        if (entries == null) {
            return Ui.tr("skirmish.killcam.library.loading");
        }
        String text = Ui.tr("skirmish.killcam.library.summary", entries.size() + " " + Ui.plural("skirmish.killcam.library.count.replay", entries.size()),
                ReplayTexts.megabytes(totalBytes()), module.maxReplays(), ReplayTexts.megabytes(module.maxBytes()));
        if (skipped > 0) {
            text += " · " + Ui.tr("skirmish.killcam.library.skipped", skipped);
        }
        return text;
    }

    private void drawList(Ui ui, float x, float cw, float ox, float w, float top, float bottom, double mx, double my) {
        float stroke = ui.num("stroke.width");
        float rowsGap = ui.num(M + "rows_gap");
        pushClip(ui, ox, top, ox + w, bottom);
        if (entries == null) {
            float size = ui.num(L + "spinner");
            float cy = top + (bottom - top) / 2f - size;
            LibraryIcons.spinner(ui, x + cw / 2f, cy, size, ui.num(L + "spinner_dot"), ui.color("text_2"));
            String text = Ui.tr("skirmish.killcam.library.loading");
            ui.text("menu_row_desc", text, x + (cw - ui.textWidth("menu_row_desc", text)) / 2f, cy + size);
            popClip(ui);
            return;
        }
        List<ReplayStore.Entry> shown = visible();
        float ry = top - scroll + rowsGap;
        if (shown.isEmpty()) {
            String key = entries.isEmpty() ? "skirmish.killcam.library.empty" : "skirmish.killcam.library.nothing_found";
            float ty = ry + rowsGap * 2;
            for (String line : ui.wrap("menu_row_desc", Ui.tr(key, dev.skirmish.ui.KeyNames.shortName(dev.skirmish.SkirmishKeys.KILLCAM_CLIP)), cw)) {
                ui.text("menu_row_desc", line, x, ty);
                ty += ui.lineHeight("menu_row_desc");
            }
        }
        float badgeW = 0;
        for (ReplayKind kind : ReplayKind.values()) {
            badgeW = Math.max(badgeW, ui.textWidth("replays_badge", Ui.tr("skirmish.killcam.library.badge." + kind.id())));
        }
        badgeW += ui.num(L + "badge_pad_x") * 2;
        float rowH = ui.num(L + "row_height");
        for (int i = 0; i < shown.size(); i++) {
            ReplayStore.Entry entry = shown.get(i);
            if (i > 0) {
                ui.hline(x, ry, cw, ui.color("divider"));
                ry += stroke + rowsGap;
            }
            if (ry + rowH >= top && ry <= bottom) {
                Row row = rows.computeIfAbsent(entry.id(), k -> new Row());
                row.entry = entry;
                row.draw(ui, x, ry, cw, rowH, badgeW, mx, my);
            }
            ry += rowH + rowsGap;
        }
        popClip(ui);
        maxScroll = Math.max(0f, ry + scroll - top - (bottom - top));
        scroll = Math.max(0f, Math.min(scroll, maxScroll));
        if (loading && !entries.isEmpty()) {
            float size = ui.num(L + "spinner_small");
            LibraryIcons.spinner(ui, x + cw - size / 2f, top - ui.num(L + "toolbar_gap") / 2f - size, size, ui.num(L + "spinner_dot"),
                    ui.color("text_3"));
        }
    }

    /** Widgets of one replay row (kept per file so hover animations survive re-layout). */
    private final class Row {
        ReplayStore.Entry entry;
        final Button watch = new Button(() -> Ui.tr(opening != null && entry != null && opening.equals(entry.id())
                ? "skirmish.killcam.library.opening" : "skirmish.killcam.library.watch"), true, () -> watch(entry));
        final IconButton star = new IconButton("fill_00", "fill_08", "button_sm", (ui, b) -> {
            float s = ui.num(L + "action_icon");
            float[] at = b.iconAt(s);
            boolean on = entry != null && entry.header().favourite;
            LibraryIcons.star(ui, at[0], at[1], s, on, on ? ui.color("warn") : ui.color("text_3"));
        }, () -> toggleFavourite(entry));
        final IconButton rename = new IconButton("fill_00", "fill_08", "button_sm", (ui, b) -> {
            float s = ui.num(L + "action_icon");
            float[] at = b.iconAt(s);
            LibraryIcons.pencil(ui, at[0], at[1], s, ui.color("text_2"));
        }, () -> openModal(Modal.RENAME, entry));
        final IconButton delete = new IconButton("fill_00", "rec_16", "button_sm", (ui, b) -> {
            float s = ui.num(L + "action_icon");
            float[] at = b.iconAt(s);
            LibraryIcons.trash(ui, at[0], at[1], s, ui.color("text_2"));
        }, () -> openModal(Modal.DELETE, entry));

        void draw(Ui ui, float x, float y, float w, float h, float badgeW, double mx, double my) {
            ReplayHeader header = entry.header();
            // Result badge.
            float bh = ui.num(L + "badge_height");
            String badge = ReplayTexts.badge(header);
            String[] colors = switch (header.kind) {
                case KILL -> new String[]{"good_16", "good"};
                case DEATH -> new String[]{"rec_16", "rec_text"};
                case CLIP -> new String[]{"accent_16", "accent"};
            };
            float by = y + (h - bh) / 2f;
            ui.rect(x, by, badgeW, bh, ui.theme().radius("chip"), ui.color(colors[0]));
            ui.textCentered("replays_badge", badge, x + (badgeW - ui.textWidth("replays_badge", badge)) / 2f, by, bh, ui.color(colors[1]));

            // Actions, right aligned: watch, star, rename, delete.
            float size = ui.num(L + "action_size");
            float gap = ui.num(L + "action_gap");
            float ax = x + w - size;
            float ay = y + (h - size) / 2f;
            delete.bounds(ax, ay, size, size);
            ax -= size + gap;
            rename.bounds(ax, ay, size, size);
            ax -= size + gap;
            star.bounds(ax, ay, size, size);
            watch.layout(M + "small_");
            float ww = watch.preferredWidth(ui);
            float wh = watch.preferredHeight(ui);
            ax -= ww + gap * 2;
            watch.bounds(ax, y + (h - wh) / 2f, ww, wh);
            watch.enabled = canWatch();
            widget(ui, watch, mx, my);
            widget(ui, star, mx, my);
            widget(ui, rename, mx, my);
            widget(ui, delete, mx, my);

            // Title and details.
            float tx = x + badgeW + ui.num(L + "badge_gap");
            float textW = ax - gap * 2 - tx;
            float textH = ui.lineHeight("menu_row_title") + ui.num(M + "row_text_gap") + ui.lineHeight("menu_row_desc");
            float ty = y + (h - textH) / 2f;
            float nx = tx;
            if (header.favourite) {
                float s = ui.num(L + "star");
                LibraryIcons.star(ui, nx, ty + (ui.lineHeight("menu_row_title") - s) / 2f, s, true, ui.color("warn"));
                nx += s + ui.num(L + "star_gap");
            }
            ui.text("menu_row_title", ui.ellipsize("menu_row_title", ReplayTexts.title(header), Math.max(0, tx + textW - nx)), nx, ty);
            ui.text("menu_row_desc", ui.ellipsize("menu_row_desc", ReplayTexts.details(header), Math.max(0, textW)), tx,
                    ty + ui.lineHeight("menu_row_title") + ui.num(M + "row_text_gap"));
            if (opening != null && opening.equals(entry.id())) {
                float s = ui.num(L + "spinner_small");
                LibraryIcons.spinner(ui, watch.x - gap - s / 2f, y + h / 2f, s, ui.num(L + "spinner_dot"), ui.color("text_2"));
            }
        }
    }

    private void drawModal(Ui ui, float ox, float oy, float ww, float wh, double mx, double my) {
        float stroke = ui.num("stroke.width");
        float padX = ui.num(M + "content_pad_x");
        float padY = ui.num(M + "content_pad_y");
        float gap = ui.num(M + "content_gap");
        float w = Math.min(ui.num(L + "modal_width"), ww - ui.num("layout.screen_edge") * 2);
        float textW = w - (stroke + padX) * 2;
        String titleKey = modal == Modal.DELETE ? "skirmish.killcam.library.delete.title" : "skirmish.killcam.library.rename.title";
        List<String> body = modal == Modal.DELETE
                ? ui.wrap("menu_desc", Ui.tr("skirmish.killcam.library.delete.body", ReplayTexts.title(target.header())), textW)
                : ui.wrap("menu_desc", Ui.tr("skirmish.killcam.library.rename.body"), textW);
        float fieldH = ui.num(M + "keybind_height");
        float bh = confirm.preferredHeight(ui);
        float h = stroke * 2 + padY * 2 + ui.lineHeight("menu_title") + ui.num(M + "header_text_gap")
                + body.size() * ui.lineHeight("menu_desc") + (modal == Modal.RENAME ? gap + fieldH : 0) + gap + bh;
        float x0 = Math.round(ox + (ww - w) / 2f);
        float y0 = Math.round(oy + (wh - h) / 2f);
        ui.box(x0, y0, w, h, ui.theme().radius("window"), ui.color("window"), ui.color("stroke_12"));
        float x = x0 + stroke + padX;
        float y = y0 + stroke + padY;
        ui.text("menu_title", Ui.tr(titleKey), x, y);
        y += ui.lineHeight("menu_title") + ui.num(M + "header_text_gap");
        for (String line : body) {
            ui.text("menu_desc", line, x, y);
            y += ui.lineHeight("menu_desc");
        }
        if (modal == Modal.RENAME) {
            y += gap;
            titleField.bounds(x, y, textW, fieldH);
            widget(ui, titleField, mx, my);
        }
        float by = y0 + h - stroke - padY - bh;
        float right = x0 + w - stroke - padX;
        float cw = confirm.preferredWidth(ui);
        float kw = cancel.preferredWidth(ui);
        confirm.bounds(right - cw, by, cw, bh);
        cancel.bounds(right - cw - ui.num(M + "footer_gap") - kw, by, kw, bh);
        widget(ui, cancel, mx, my);
        widget(ui, confirm, mx, my);
    }

    // ---- input ----

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (modal != Modal.NONE) {
            if (event.isEscape()) {
                closeModal();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                confirmModal();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (modal == Modal.NONE) {
            scroll = Math.max(0f, Math.min(maxScroll, scroll - (float) scrollY * Theme.get().num(M + "scroll_step")));
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
