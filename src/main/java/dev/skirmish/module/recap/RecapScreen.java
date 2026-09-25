package dev.skirmish.module.recap;

import dev.skirmish.module.analytics.dossier.DossierModule;
import dev.skirmish.ui.Icons;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.Button;
import dev.skirmish.ui.widget.IconButton;
import dev.skirmish.ui.widget.UiScreen;
import dev.skirmish.ui.widget.WindowFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;

/**
 * «Итоги сессии» window: eight stat tiles, the best fight (with the dossier score against that opponent when the
 * Dossier module knows them), «Сохранить PNG» and «Открыть папку». Movable and resizable like the other windows.
 */
final class RecapScreen extends UiScreen {
    private static final String L = "layout.recap.";
    private static final String P = RecapLines.P;

    private final SessionRecapModule module;
    private final @Nullable SessionRecap recap;
    private final WindowFrame frame = new WindowFrame("session_recap", L);
    private final IconButton close = new IconButton("fill_06", "rec_16", "button_sm", (u, b) -> {
        float s = u.num(L + "close_icon");
        float[] at = b.iconAt(s);
        Icons.close(u, at[0], at[1], s, 2.2f, u.color("text_2"));
    }, this::onClose);
    private final Button save = new Button(() -> Ui.tr(P + "save"), true, this::save);
    private final Button folder = new Button(() -> Ui.tr(P + "open_folder"), false, this::openFolder);
    private @Nullable String status;
    private boolean statusError;
    private boolean saving;

    RecapScreen(SessionRecapModule module, @Nullable SessionRecap recap, @Nullable Screen parent) {
        super(Component.translatable(P + "title"), parent);
        this.module = module;
        this.recap = recap;
    }

    private static char decimal() {
        return Ui.decimal(0.5, 1).contains(",") ? ',' : '.';
    }

    private static String dateText(long ms) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern(Ui.tr(P + "date_pattern"), Locale.ROOT));
    }

    private static String subtitle(SessionRecap r) {
        String when = r.finished() ? dateText(r.endMs()) : Ui.tr(P + "live");
        return r.player() + " · " + r.server() + " · " + when;
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        ui.rect(0, 0, ui.width(), ui.height(), 0, ui.color("backdrop"));
        frame.layout(ui);
        widget(ui, frame.mover, mx, my);
        float stroke = ui.num("stroke.width");
        float w = frame.w();
        float h = frame.h();
        float ox = frame.x();
        float oy = frame.y() + Math.round((1f - appearProgress()) * ui.num("layout.appear_offset"));
        ui.box(ox, oy, w, h, ui.theme().radius("window"), ui.color("window"), ui.color("stroke_07"));

        float padX = ui.num(L + "pad_x");
        float padY = ui.num(L + "pad_y");
        float x = ox + stroke + padX;
        float cw = w - (stroke + padX) * 2;
        float y = oy + stroke + padY;
        float closeS = ui.num(L + "close_size");
        close.bounds(x + cw - closeS + ui.num(L + "close_inset"), y - ui.num(L + "close_inset"), closeS, closeS);
        widget(ui, close, mx, my);

        ui.text("recap_title", ui.ellipsize("recap_title", Ui.tr(P + "title"), cw - closeS), x, y);
        y += ui.lineHeight("recap_title") + ui.num(L + "header_gap");
        SessionRecap r = recap;
        if (r == null) {
            for (String line : ui.wrap("recap_sub", Ui.tr(P + "empty"), cw)) {
                ui.text("recap_sub", line, x, y);
                y += ui.lineHeight("recap_sub");
            }
            widget(ui, frame.grip, mx, my);
            return;
        }
        ui.text("recap_sub", ui.ellipsize("recap_sub", subtitle(r), cw), x, y);
        y += ui.lineHeight("recap_sub") + ui.num(L + "section_gap");

        y = tiles(ui, RecapLines.tiles(r, Ui::tr, decimal()), x, y, cw) + ui.num(L + "section_gap");
        y = best(ui, r.best(), x, y, cw) + ui.num(L + "section_gap");

        // Buttons at the bottom.
        float bh = save.preferredHeight(ui);
        float by = oy + h - stroke - padY - bh;
        float sw = save.preferredWidth(ui);
        save.enabled = !saving;
        save.bounds(x, by, sw, bh);
        widget(ui, save, mx, my);
        float fw = folder.preferredWidth(ui);
        folder.bounds(x + sw + ui.num(L + "button_gap"), by, fw, bh);
        widget(ui, folder, mx, my);
        String note = saving ? Ui.tr(P + "saving") : status;
        if (note != null) {
            float nx = x + sw + fw + ui.num(L + "button_gap") * 2;
            ui.textCentered("recap_status", ui.ellipsize("recap_status", note, x + cw - nx), nx, by, bh,
                    ui.color(statusError ? "bad" : "text_3"));
        }
        widget(ui, frame.grip, mx, my);
    }

    private float tiles(Ui ui, List<RecapLines.Tile> tiles, float x, float y, float w) {
        int cols = 4;
        float gap = ui.num(L + "tile_gap");
        float tw = (w - gap * (cols - 1)) / cols;
        float padX = ui.num(L + "tile_pad_x");
        float padY = ui.num(L + "tile_pad_y");
        float th = padY * 2 + ui.lineHeight("recap_tile_value") + ui.num(L + "tile_value_gap") + ui.lineHeight("recap_tile_label");
        for (int i = 0; i < tiles.size(); i++) {
            RecapLines.Tile t = tiles.get(i);
            float tx = x + (i % cols) * (tw + gap);
            float ty = y + (i / cols) * (th + gap);
            ui.box(tx, ty, tw, th, ui.theme().radius("tile"), ui.color("tile"), ui.color("stroke"));
            ui.text("recap_tile_value", ui.ellipsize("recap_tile_value", t.value(), tw - padX * 2), tx + padX, ty + padY, ui.color(t.color()));
            ui.text("recap_tile_label", ui.ellipsize("recap_tile_label", t.label(), tw - padX * 2), tx + padX,
                    ty + padY + ui.lineHeight("recap_tile_value") + ui.num(L + "tile_value_gap"));
        }
        int rows = (tiles.size() + cols - 1) / cols;
        return y + rows * th + (rows - 1) * gap;
    }

    private float best(Ui ui, @Nullable RecapFight f, float x, float y, float w) {
        float pad = ui.num(L + "tile_pad_x");
        float padY = ui.num(L + "tile_pad_y");
        String detail = f == null ? "" : RecapLines.bestDetails(f, Ui::tr, decimal());
        String dossier = f == null || f.opponentUuid() == null ? null : DossierModule.summary(f.opponentUuid());
        float gap = ui.num(L + "tile_value_gap");
        float h = padY * 2 + ui.lineHeight("recap_section") + gap + ui.lineHeight("recap_best");
        if (!detail.isEmpty()) {
            h += gap + ui.lineHeight("recap_sub");
        }
        if (dossier != null) {
            h += gap + ui.lineHeight("recap_sub");
        }
        ui.box(x, y, w, h, ui.theme().radius("tile"), ui.color("tile"), ui.color("stroke"));
        float cy = y + padY;
        ui.text("recap_section", Ui.tr(P + "best.label").toUpperCase(Locale.ROOT), x + pad, cy);
        cy += ui.lineHeight("recap_section") + gap;
        ui.text("recap_best", ui.ellipsize("recap_best", RecapLines.bestTitle(f, Ui::tr), w - pad * 2), x + pad, cy);
        cy += ui.lineHeight("recap_best") + gap;
        if (!detail.isEmpty()) {
            ui.text("recap_sub", ui.ellipsize("recap_sub", detail, w - pad * 2), x + pad, cy);
            cy += ui.lineHeight("recap_sub") + gap;
        }
        if (dossier != null) {
            ui.text("recap_sub", ui.ellipsize("recap_sub", dossier, w - pad * 2), x + pad, cy, ui.color("text_3"));
        }
        return y + h;
    }

    /** Builds the card on the client thread, renders and saves it on the export thread. */
    private void save() {
        SessionRecap r = recap;
        if (r == null || saving) {
            return;
        }
        char decimal = decimal();
        String played = Ui.tr(P + "card.played", RecapFormat.playtime(r.playtimeMs()));
        RecapFight f = r.best();
        RecapCard.Data data = new RecapCard.Data(r.player(), r.server() + " · " + dateText(r.endMs()) + " · " + played,
                Ui.tr(P + "card.badge"), RecapLines.tiles(r, Ui::tr, decimal), Ui.tr(P + "best.label").toUpperCase(Locale.ROOT),
                RecapLines.bestTitle(f, Ui::tr), f == null ? "" : RecapLines.bestDetails(f, Ui::tr, decimal),
                Ui.tr(P + "card.footer"), 2.0);
        Path dir = SessionRecapModule.directory();
        String base = RecapExporter.baseName(r.player(), ZonedDateTime.ofInstant(Instant.ofEpochMilli(r.endMs()), ZoneId.systemDefault()));
        Minecraft mc = Minecraft.getInstance();
        saving = true;
        try {
            module.executor().execute(() -> {
                try {
                    Path file = RecapExporter.export(data, dir, base);
                    module.log("recap saved: %s", file);
                    mc.execute(() -> done(Ui.tr(P + "saved", file.getFileName().toString()), false));
                } catch (Throwable t) {
                    module.error("recap PNG failed", t);
                    mc.execute(() -> done(Ui.tr(P + "save_failed", String.valueOf(t.getMessage())), true));
                }
            });
        } catch (RejectedExecutionException e) {
            done(Ui.tr(P + "save_failed", String.valueOf(e.getMessage())), true);
        }
    }

    private void openFolder() {
        module.openFolder();
    }

    private void done(String text, boolean error) {
        saving = false;
        status = text;
        statusError = error;
    }
}
