package dev.skirmish.module.hwos;

import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.UiScreen;
import dev.skirmish.ui.widget.Widget;
import dev.skirmish.ui.widget.WindowFrame;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HolyWorld OS: HolyWorld in one app window. A rail of apps on the left — Events, Economy, Anarchies, Guide,
 * Profile — and the open app on the right. Everything comes from what Skirmish already knows (the public API,
 * the auction pages you browsed, your base, the sidebar); nothing is sent to the server.
 */
public final class HwOsScreen extends UiScreen {
    static final String L = HwOsModule.L;
    private static int current;

    private final WindowFrame frame = new WindowFrame("holyworld_os", L);
    private final List<OsApp> apps = List.of(new EventsApp(), new EconomyApp(), new AnarchiesApp(), new GuideApp(), new ProfileApp());
    private final Map<Integer, RailItem> rail = new HashMap<>();
    private final float[] scroll = new float[5];
    private final float[] maxScroll = new float[5];

    /** One app of the OS: draws itself into the content area and may keep its own state. */
    interface OsApp {
        String id();

        void draw(HwOsScreen s, Ui ui, float x, float y, float w, float h, double mx, double my);

        /** Scroll inside the app; false lets the screen scroll the app's content. */
        default boolean scrolled(double mx, double my, double amount) {
            return false;
        }
    }

    public HwOsScreen(@Nullable Screen parent) {
        super(Component.translatable("skirmish.hwos.title"), parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- helpers for the apps ----

    void use(Ui ui, Widget widget, double mx, double my) {
        widget(ui, widget, mx, my);
    }

    void clip(Ui ui, float x0, float y0, float x1, float y1) {
        pushClip(ui, x0, y0, x1, y1);
    }

    void unclip(Ui ui) {
        popClip(ui);
    }

    /** The current app's scroll offset. */
    float scroll() {
        return scroll[current];
    }

    /** Tells how tall the app's content is (the part below {@code visible} can be scrolled to). */
    void content(float height, float visible) {
        maxScroll[current] = Math.max(0f, height - visible);
        scroll[current] = Math.max(0f, Math.min(scroll[current], maxScroll[current]));
    }

    void close() {
        onClose();
    }

    /** A section title in caps with the gap under it; returns the y under it. */
    static float section(Ui ui, String title, float x, float y) {
        ui.text("menu_section", title.toUpperCase(java.util.Locale.ROOT), x, y);
        return y + ui.lineHeight("menu_section") + ui.num(L + "section_gap");
    }

    /** Wrapped text; returns the y under it. */
    static float para(Ui ui, String style, String text, float x, float y, float w, int color) {
        for (String line : ui.wrap(style, text, w)) {
            ui.text(style, line, x, y, color);
            y += ui.lineHeight(style);
        }
        return y;
    }

    /** A small stat tile: value over a label. */
    static void tile(Ui ui, float x, float y, float w, float h, String value, String label, String tone) {
        ui.box(x, y, w, h, ui.theme().radius("tile"), ui.color("fill_05"), ui.color("stroke"));
        float pad = ui.num(L + "tile_pad");
        ui.text("stat_value", ui.ellipsize("stat_value", value, w - pad * 2), x + pad, y + pad, ui.color(tone));
        ui.text("stat_label", ui.ellipsize("stat_label", label, w - pad * 2), x + pad, y + h - pad - ui.lineHeight("stat_label"));
    }

    /** Rarity colour token, as in the events panel. */
    static String rarityTone(dev.skirmish.module.events.Rarity rarity) {
        return switch (rarity) {
            case LEGENDARY -> "warn";
            case EPIC -> "event_epic";
            case RARE -> "accent";
            case COMMON -> "text_3";
            case UNKNOWN -> "text_4";
        };
    }

    // ---- layout ----

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

        // Rail: brand, then one row per app.
        float railW = ui.num(L + "rail_width");
        float pad = ui.num(L + "pad");
        ui.rect(ox + stroke, oy + stroke, railW, h - stroke * 2, ui.theme().radius("window"), ui.color("sidebar"));
        ui.rect(ox + railW, oy + stroke, stroke, h - stroke * 2, 0f, ui.color("stroke"));
        float ry = oy + pad;
        ui.text("hwos_brand", "HolyWorld", ox + pad, ry);
        ry += ui.lineHeight("hwos_brand");
        ui.text("hwos_brand_os", "OS", ox + pad, ry, ui.color("accent"));
        ry += ui.lineHeight("hwos_brand_os") + ui.num(L + "rail_gap");
        float itemH = ui.num(L + "rail_item");
        for (int i = 0; i < apps.size(); i++) {
            int index = i;
            RailItem item = rail.computeIfAbsent(i, k -> new RailItem(index));
            item.bounds(ox + stroke + 8, ry, railW - 16, itemH);
            widget(ui, item, mx, my);
            ry += itemH + 4;
        }
        String hint = Ui.tr("skirmish.hwos.readonly");
        para(ui, "menu_hint", hint, ox + pad, oy + h - pad - ui.lineHeight("menu_hint") * 3, railW - pad * 2, ui.color("text_4"));

        // Content.
        float cx = ox + railW + pad;
        float cy = oy + pad;
        float cw = w - railW - pad * 2;
        OsApp app = apps.get(current);
        ui.text("menu_title", Ui.tr("skirmish.hwos.app." + app.id()), cx, cy);
        cy += ui.lineHeight("menu_title") + ui.num(L + "title_gap");
        float ch = oy + h - pad - cy;
        app.draw(this, ui, cx, cy, cw, ch, mx, my);
        widget(ui, frame.grip, mx, my);
    }

    /** An app row of the rail: icon, name, the open one highlighted. */
    private final class RailItem extends Widget {
        private final int index;

        RailItem(int index) {
            this.index = index;
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
            boolean on = current == index;
            if (on || hovered() > 0f) {
                ui.rect(x, y, w, h, ui.theme().radius("button_sm"), ui.color(on ? "accent_16" : "fill_05"));
            }
            float icon = ui.num(L + "rail_icon");
            int color = ui.color(on ? "accent" : "text_2");
            OsIcons.draw(ui, apps.get(index).id(), x + 10, y + (h - icon) / 2f, icon, color);
            ui.textCentered("hwos_rail", Ui.tr("skirmish.hwos.app." + apps.get(index).id()), x + 10 + icon + 10, y, h,
                    ui.color(on ? "text" : "text_2"));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                current = index;
                return true;
            }
            return false;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double mx = Ui.toDesign(mouseX);
        double my = Ui.toDesign(mouseY);
        if (apps.get(current).scrolled(mx, my, scrollY)) {
            return true;
        }
        scroll[current] = Math.max(0f, Math.min(maxScroll[current], scroll[current] - (float) scrollY * Theme.get().num("layout.menu.scroll_step")));
        return true;
    }
}
