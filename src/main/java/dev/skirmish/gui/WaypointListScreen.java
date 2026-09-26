package dev.skirmish.gui;

import dev.skirmish.setting.StringSetting;
import dev.skirmish.ui.Icons;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.Button;
import dev.skirmish.ui.widget.IconButton;
import dev.skirmish.ui.widget.Segmented;
import dev.skirmish.ui.widget.TextField;
import dev.skirmish.ui.widget.Toggle;
import dev.skirmish.ui.widget.UiScreen;
import dev.skirmish.ui.widget.WindowFrame;
import dev.skirmish.waypoint.Waypoint;
import dev.skirmish.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The current server's waypoints in a movable, resizable window: add one at typed coordinates (or at my position
 * when they are left empty), choose the one the HUD pill points to, delete.
 */
public final class WaypointListScreen extends UiScreen {
    private static final String L = "layout.waypoints.";
    private static final List<String> VANILLA_DIMENSIONS = List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");

    private final WindowFrame frame = new WindowFrame("waypoints", L);
    private final StringSetting name = new StringSetting("name", "", 64, false);
    private final TextField nameField = new TextField(name, () -> { }).placeholder(() -> Ui.tr("skirmish.waypoints.name"));
    private final StringSetting xs = new StringSetting("x", "", 12, false);
    private final StringSetting ys = new StringSetting("y", "", 12, false);
    private final StringSetting zs = new StringSetting("z", "", 12, false);
    private final TextField xField = new TextField(xs, () -> { }).placeholder(() -> "X");
    private final TextField yField = new TextField(ys, () -> { }).placeholder(() -> "Y");
    private final TextField zField = new TextField(zs, () -> { }).placeholder(() -> "Z");
    private final List<String> dimensions = new ArrayList<>();
    private int dimension;
    private final Segmented dimensionPicker = new Segmented(Segmented.Spec.MENU,
            () -> dimensions.stream().map(WaypointListScreen::dimensionLabel).toList(), () -> dimension, i -> dimension = i);
    private final Button here = new Button(() -> Ui.tr("skirmish.waypoints.fill_here"), false, this::fillHere);
    private final Button add = new Button(() -> Ui.tr("skirmish.waypoints.add"), true, this::add);
    private @Nullable String error;
    private long errorUntil;
    private final Button done = new Button(() -> Ui.tr("skirmish.menu.done"), true, this::onClose);
    private final Map<String, Toggle> toggles = new HashMap<>();
    private final Map<String, IconButton> deletes = new HashMap<>();
    private float scroll;
    private float maxScroll;

    public WaypointListScreen(@Nullable Screen parent) {
        super(Component.translatable("skirmish.waypoints.title"), parent);
        Player player = Minecraft.getInstance().player;
        String current = player == null ? "minecraft:overworld" : player.level().dimension().identifier().toString();
        if (!VANILLA_DIMENSIONS.contains(current)) {
            dimensions.add(current);
        }
        dimensions.addAll(VANILLA_DIMENSIONS);
        dimension = dimensions.indexOf(current);
    }

    private static String dimensionLabel(String id) {
        String key = "skirmish.waypoints.dimension." + id.replace("minecraft:", "");
        return Texts.has(key) ? Ui.tr(key) : id.substring(id.indexOf(':') + 1);
    }

    /** Puts my block position into the X/Y/Z fields and selects my dimension. */
    private void fillHere() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        xs.set(Integer.toString(player.getBlockX()));
        ys.set(Integer.toString(player.getBlockY()));
        zs.set(Integer.toString(player.getBlockZ()));
        int i = dimensions.indexOf(player.level().dimension().identifier().toString());
        if (i >= 0) {
            dimension = i;
        }
    }

    /** Typed coordinates → waypoint; all three empty → at my position. */
    private void add() {
        String value = name.get().isBlank() ? Ui.tr("skirmish.waypoints.default_name") : name.get().strip();
        if (xs.get().isBlank() && ys.get().isBlank() && zs.get().isBlank()) {
            if (WaypointManager.get().addHere(value) != null) {
                name.set("");
            }
            return;
        }
        Double x = parse(xs.get());
        Double z = parse(zs.get());
        Double y = ys.get().isBlank() ? Double.valueOf(64) : parse(ys.get());
        if (x == null || y == null || z == null) {
            error = Ui.tr("skirmish.waypoints.bad_coords");
            errorUntil = System.currentTimeMillis() + 3000;
            return;
        }
        WaypointManager.get().add(value, x, y, z, dimensions.get(dimension), "menu");
        name.set("");
        xs.set("");
        ys.set("");
        zs.set("");
        error = null;
    }

    private static @Nullable Double parse(String text) {
        try {
            double v = Double.parseDouble(text.strip().replace(',', '.').replace('−', '-'));
            return Double.isFinite(v) && Math.abs(v) <= 30_000_000 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

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

        float padX = ui.num("layout.menu.content_pad_x");
        float padY = ui.num("layout.menu.content_pad_y");
        float gap = ui.num("layout.menu.content_gap");
        float x = ox + stroke + padX;
        float cw = w - (stroke + padX) * 2;
        float y = oy + stroke + padY;
        ui.text("menu_title", Ui.tr("skirmish.waypoints.title"), x, y);
        y += ui.lineHeight("menu_title") + ui.num("layout.menu.header_text_gap");
        for (String line : ui.wrap("menu_desc", Ui.tr("skirmish.waypoints.subtitle"), cw)) {
            ui.text("menu_desc", line, x, y);
            y += ui.lineHeight("menu_desc");
        }
        y += gap;

        // Add form. Row 1: name + «сюда» (fills my coordinates). Row 2: X Y Z, dimension, «добавить».
        float fieldH = ui.num("layout.menu.keybind_height");
        float fgap = ui.num("layout.menu.footer_gap");
        here.layout("layout.menu.small_");
        add.layout("layout.menu.small_");
        float hereW = here.preferredWidth(ui);
        nameField.bounds(x, y, cw - hereW - fgap, fieldH);
        here.bounds(x + cw - hereW, y, hereW, fieldH);
        widget(ui, nameField, mx, my);
        widget(ui, here, mx, my);
        y += fieldH + fgap;
        float addW = add.preferredWidth(ui);
        float dimW = dimensionPicker.preferredWidth(ui);
        float coordW = Math.max(ui.num(L + "coord_min_width"), (cw - addW - dimW - fgap * 4) / 3f);
        float cxw = x;
        for (TextField f : List.of(xField, yField, zField)) {
            f.bounds(cxw, y, coordW, fieldH);
            widget(ui, f, mx, my);
            cxw += coordW + fgap;
        }
        float dh = dimensionPicker.preferredHeight(ui);
        dimensionPicker.bounds(cxw, y + (fieldH - dh) / 2f, dimW, dh);
        widget(ui, dimensionPicker, mx, my);
        add.bounds(x + cw - addW, y, addW, fieldH);
        widget(ui, add, mx, my);
        y += fieldH + gap;
        if (error != null && System.currentTimeMillis() < errorUntil) {
            ui.text("menu_row_desc", error, x, y - gap + ui.num(L + "error_gap"), ui.color("bad"));
        }
        ui.hline(x, y, cw, ui.color("stroke"));
        y += stroke;

        float bh = done.preferredHeight(ui);
        float fy = oy + h - stroke - padY - bh;
        float dw = done.preferredWidth(ui);
        done.bounds(x + cw - dw, fy, dw, bh);
        widget(ui, done, mx, my);

        float top = y;
        float bottom = fy - gap;
        List<Waypoint> waypoints = WaypointManager.get().currentServer();
        pushClip(ui, ox, top, ox + w, bottom);
        float ry = top - scroll + ui.num("layout.menu.rows_gap");
        float rowH = ui.num("layout.menu.row_min_height");
        if (waypoints.isEmpty()) {
            ui.text("menu_row_desc", Ui.tr("skirmish.waypoints.empty"), x, ry + ui.num("layout.menu.rows_gap") * 2);
        }
        Waypoint selected = WaypointManager.get().selected();
        Player player = Minecraft.getInstance().player;
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint wp = waypoints.get(i);
            if (i > 0) {
                ui.hline(x, ry, cw, ui.color("divider"));
                ry += stroke + ui.num("layout.menu.rows_gap");
            }
            float dot = ui.num("layout.menu.dot");
            ui.circle(x + dot / 2f, ry + rowH / 2f, dot, 0xFF000000 | wp.color());
            float tx = x + dot + ui.num("layout.menu.module_gap");
            String sub = wp.coordsText().replace('-', '−') + " · " + wp.dimension().replace("minecraft:", "");
            if (player != null && wp.dimension().equals(player.level().dimension().identifier().toString())) {
                sub += " · " + Math.round(Math.sqrt(wp.distanceSq(player.getX(), player.getY(), player.getZ()))) + " " + Texts.unit("m");
            }
            if (wp.source().startsWith("clanshare:")) {
                sub += " · " + Ui.tr("skirmish.waypoints.from", wp.source().substring("clanshare:".length()));
            }
            float textH = ui.lineHeight("menu_row_title") + ui.num("layout.menu.row_text_gap") + ui.lineHeight("menu_row_desc");
            float controls = ui.num("layout.menu.toggle_width") + ui.num("layout.menu.row_gap") + ui.num(L + "delete_size");
            float textW = cw - (tx - x) - controls - ui.num("layout.menu.row_gap");
            float ty = ry + (rowH - textH) / 2f;
            ui.text("menu_row_title", ui.ellipsize("menu_row_title", wp.name(), textW), tx, ty);
            ui.text("menu_row_desc", ui.ellipsize("menu_row_desc", sub, textW), tx, ty + ui.lineHeight("menu_row_title") + ui.num("layout.menu.row_text_gap"));

            String id = wp.id();
            boolean on = selected != null && selected.id().equals(id);
            Toggle toggle = toggles.computeIfAbsent(id, k -> new Toggle(
                    () -> { Waypoint s = WaypointManager.get().selected(); return s != null && s.id().equals(k); },
                    v -> WaypointManager.get().select(v ? k : null)));
            IconButton delete = deletes.computeIfAbsent(id, k -> new IconButton("fill_06", "rec_16", "button_sm", (u, b) -> {
                float s = ui.num(L + "delete_icon");
                float[] at = b.iconAt(s);
                Icons.close(u, at[0], at[1], s, 2.2f, u.color("text_2"));
            }, () -> WaypointManager.get().remove(k)));
            float ds = ui.num(L + "delete_size");
            delete.bounds(x + cw - ds, ry + (rowH - ds) / 2f, ds, ds);
            toggle.at(delete.x - ui.num("layout.menu.row_gap") - ui.num("layout.menu.toggle_width"),
                    ry + (rowH - ui.num("layout.menu.toggle_height")) / 2f);
            widget(ui, toggle, mx, my);
            widget(ui, delete, mx, my);
            ry += rowH + ui.num("layout.menu.rows_gap");
        }
        popClip(ui);
        maxScroll = Math.max(0f, ry + scroll - top - (bottom - top));
        scroll = Math.max(0f, Math.min(scroll, maxScroll));
        if (!waypoints.isEmpty()) {
            ui.text("menu_hint", Ui.tr("skirmish.waypoints.toggle_hint"), x, fy + (bh - ui.lineHeight("menu_hint")) / 2f);
        }
        widget(ui, frame.grip, mx, my);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0f, Math.min(maxScroll, scroll - (float) scrollY * dev.skirmish.ui.Theme.get().num("layout.menu.scroll_step")));
        return true;
    }
}
