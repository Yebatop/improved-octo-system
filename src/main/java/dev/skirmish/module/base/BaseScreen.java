package dev.skirmish.module.base;

import dev.skirmish.module.market.MarketModule;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.setting.StringSetting;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.Button;
import dev.skirmish.ui.widget.Segmented;
import dev.skirmish.ui.widget.Slider;
import dev.skirmish.ui.widget.TextField;
import dev.skirmish.ui.widget.UiScreen;
import dev.skirmish.ui.widget.Widget;
import dev.skirmish.ui.widget.WindowFrame;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Base OS: the base as an app in a movable window. Tabs: Overview (numbers, alerts, region countdowns, farms),
 * Storage (search every item you keep, set a minimum, click for a route to the chest), Log and a 3D model of the
 * region you can turn, zoom and cut open.
 */
public final class BaseScreen extends UiScreen {
    private static final String L = BaseModule.L;
    private static int tab;

    private final WindowFrame frame = new WindowFrame("base_os", L);
    private final Segmented tabs = new Segmented(Segmented.Spec.MENU,
            () -> List.of(Ui.tr("skirmish.base.tab.overview"), Ui.tr("skirmish.base.tab.storage"),
                    Ui.tr("skirmish.base.tab.log"), Ui.tr("skirmish.base.tab.model")),
            () -> tab, i -> tab = i);
    private final Button here = new Button(() -> Ui.tr("skirmish.base.set_here"), false, this::setHere);
    private final Button forget = new Button(() -> Ui.tr("skirmish.base.forget"), false, this::forget);
    private final StringSetting query = new StringSetting("query", "", 48, false);
    private final TextField search = new TextField(query, () -> scroll = 0).placeholder(() -> Ui.tr("skirmish.base.search"));
    private final NumberSetting cutSetting = new NumberSetting("cut", 100, 0, 100, 1);
    private final Slider cutSlider = new Slider(cutSetting, this::cutChanged);
    private final Button refresh = new Button(() -> Ui.tr("skirmish.base.model.refresh"), false, this::capture);
    private final Map<String, Button> minButtons = new HashMap<>();
    private final Map<String, ItemStack> icons = new HashMap<>();
    private final BaseModelView model = new BaseModelView();
    /** Click areas of the storage rows, by item key. */
    private final Map<String, Area> rowAreas = new HashMap<>();
    private final Widget modelArea = new Widget() {
        private double lastX;
        private double lastY;

        @Override
        protected void draw(Ui ui, double mx, double my) {
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            lastX = mx;
            lastY = my;
            return button == 0;
        }

        @Override
        public void mouseDragged(double mx, double my, int button) {
            model.yaw += (float) (mx - lastX) * 0.012f;
            model.pitch = Math.max(-0.2f, Math.min(1.5f, model.pitch + (float) (my - lastY) * 0.012f));
            lastX = mx;
            lastY = my;
        }
    };
    private float scroll;
    private float maxScroll;

    /** An invisible clickable area (a storage row). */
    private static final class Area extends Widget {
        private final Runnable click;

        Area(Runnable click) {
            this.click = click;
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                click.run();
                return true;
            }
            return false;
        }
    }

    public BaseScreen(@Nullable Screen parent) {
        super(Component.translatable("skirmish.base.title"), parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static @Nullable BaseModule module() {
        return BaseModule.instance();
    }

    private void setHere() {
        BaseModule m = module();
        if (m != null) {
            m.setBaseHere();
            capture();
        }
    }

    private void forget() {
        BaseModule m = module();
        if (m != null) {
            m.forgetBase();
        }
    }

    private void capture() {
        BaseModule m = module();
        BaseData.Region r = m == null ? null : m.region();
        if (r != null && minecraft.level != null && r.dim.equals(dev.skirmish.util.ServerContext.dimension())) {
            model.cut = Integer.MAX_VALUE;
            model.capture(minecraft.level, r);
            cutSetting.set(100.0);
        }
    }

    private void cutChanged() {
        int layers = model.layers();
        model.cut = layers <= 0 ? Integer.MAX_VALUE : (int) Math.round(cutSetting.get() / 100.0 * (layers - 1));
    }

    @Override
    protected void onClosing() {
        model.close();
    }

    // ---- layout ----

    @Override
    protected void draw(Ui ui, double mx, double my) {
        ui.rect(0, 0, ui.width(), ui.height(), 0, ui.color("backdrop"));
        BaseModule m = module();
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
        if (m == null) {
            return;
        }
        BaseData.Region region = m.region();

        // Header: title, where the base is, buttons.
        here.layout("layout.menu.small_");
        forget.layout("layout.menu.small_");
        float bh = ui.num("layout.menu.small_button_height");
        float bx = x + cw;
        if (region != null) {
            float fw = forget.preferredWidth(ui);
            bx -= fw;
            forget.bounds(bx, y, fw, bh);
            widget(ui, forget, mx, my);
            bx -= ui.num("layout.menu.footer_gap");
        }
        float hw = here.preferredWidth(ui);
        bx -= hw;
        here.bounds(bx, y, hw, bh);
        widget(ui, here, mx, my);
        ui.text("menu_title", Ui.tr("skirmish.base.title"), x, y);
        y += ui.lineHeight("menu_title") + ui.num("layout.menu.header_text_gap");
        String sub = region == null ? Ui.tr("skirmish.base.none")
                : Ui.tr(region.manual ? "skirmish.base.where_manual" : "skirmish.base.where", BaseModule.regionName(region),
                region.sizeX() + "×" + region.sizeZ(), region.x, region.y, region.z);
        for (String line : ui.wrap("menu_desc", sub, cw)) {
            ui.text("menu_desc", line, x, y, ui.color(region == null ? "warn" : "text_2"));
            y += ui.lineHeight("menu_desc");
        }
        y += gap;
        float th = tabs.preferredHeight(ui);
        tabs.bounds(x, y, tabs.preferredWidth(ui), th);
        widget(ui, tabs, mx, my);
        y += th + gap;
        ui.hline(x, y, cw, ui.color("stroke"));
        y += stroke + gap;
        float bottom = oy + h - stroke - padY;
        switch (tab) {
            case 0 -> overview(ui, m, x, y, cw, bottom);
            case 1 -> storage(ui, m, x, y, cw, bottom, mx, my);
            case 2 -> log(ui, m, x, y, cw, bottom);
            default -> model(ui, m, x, y, cw, bottom, mx, my);
        }
        widget(ui, frame.grip, mx, my);
    }

    // ---- overview ----

    private void overview(Ui ui, BaseModule m, float x, float y, float cw, float bottom) {
        long now = System.currentTimeMillis();
        List<BaseData.Chest> chests = m.indexedChests();
        long items = m.totals().stream().mapToLong(StorageIndex.Total::count).sum();
        long day = m.server().log.stream().filter(e -> "intruder".equals(e.kind) && now - e.at < 86_400_000L).count();
        String[][] stats = {
                {Integer.toString(chests.size()), Ui.tr("skirmish.base.stat.chests")},
                {Long.toString(items), Ui.tr("skirmish.base.stat.items", m.totals().size())},
                {m.pricedKinds() > 0 ? BaseHud.money(m.value()) : "—", Ui.tr(m.pricedKinds() > 0 ? "skirmish.base.stat.value" : "skirmish.base.stat.no_prices")},
                {Long.toString(day), Ui.tr("skirmish.base.stat.intruders")},
        };
        float tileGap = ui.num(L + "tile_gap");
        float tw = (cw - tileGap * 3) / 4f;
        float tileH = ui.num(L + "tile_height");
        for (int i = 0; i < 4; i++) {
            float tx = x + i * (tw + tileGap);
            ui.box(tx, y, tw, tileH, ui.theme().radius("tile"), ui.color("fill_05"), ui.color("stroke"));
            float pad = ui.num(L + "tile_pad");
            ui.text("stat_value", ui.ellipsize("stat_value", stats[i][0], tw - pad * 2), tx + pad, y + pad,
                    ui.color(i == 3 && day > 0 ? "bad" : i == 2 && m.pricedKinds() > 0 ? "base_tone" : "text"));
            ui.text("stat_label", ui.ellipsize("stat_label", stats[i][1], tw - pad * 2), tx + pad, y + tileH - pad - ui.lineHeight("stat_label"));
        }
        y += tileH + ui.num("layout.menu.content_gap");
        float colGap = ui.num(L + "col_gap");
        float colW = (cw - colGap) / 2f;
        float ly = y;
        ly = section(ui, Ui.tr("skirmish.base.section.alerts"), x, ly);
        List<BaseModule.Alert> alerts = m.alerts();
        if (alerts.isEmpty()) {
            ui.text("menu_row_desc", Ui.tr("skirmish.base.all_quiet"), x, ly);
            ly += ui.lineHeight("menu_row_desc") + 4;
        }
        for (BaseModule.Alert a : alerts) {
            if (ly > bottom - 20) {
                break;
            }
            ui.circle(x + 4, ly + ui.lineHeight("base_list") / 2f, 6, ui.color(a.tone()));
            ui.text("base_list", ui.ellipsize("base_list", a.text(), colW - 16), x + 14, ly);
            ly += ui.lineHeight("base_list") + 4;
        }
        ly += 8;
        ly = section(ui, Ui.tr("skirmish.base.section.timers"), x, ly);
        List<BaseData.Timer> timers = m.server().timers;
        if (timers.isEmpty()) {
            for (String line : ui.wrap("menu_row_desc", Ui.tr("skirmish.base.no_timers"), colW)) {
                ui.text("menu_row_desc", line, x, ly);
                ly += ui.lineHeight("menu_row_desc");
            }
        }
        for (BaseData.Timer t : timers) {
            long left = t.endsAt - now;
            String value = left > 0 ? BaseModule.left(left) : Ui.tr("skirmish.base.timer_done");
            float vw = ui.textWidth("base_list_value", value);
            ui.text("base_list_value", value, x + colW - vw, ly, ui.color(left > 0 ? "text" : "warn"));
            ui.text("base_list", ui.ellipsize("base_list", t.label, colW - vw - 12), x, ly);
            ly += ui.lineHeight("base_list") + 4;
        }

        float rx = x + colW + colGap;
        float ry = section(ui, Ui.tr("skirmish.base.section.farms"), rx, y);
        List<BaseModule.Farm> farms = m.farms();
        if (!m.farms.get()) {
            ui.text("menu_row_desc", Ui.tr("skirmish.base.farms_off"), rx, ry);
        } else if (farms.isEmpty()) {
            for (String line : ui.wrap("menu_row_desc", Ui.tr("skirmish.base.no_farms"), colW)) {
                ui.text("menu_row_desc", line, rx, ry);
                ry += ui.lineHeight("menu_row_desc");
            }
        }
        for (BaseModule.Farm f : farms) {
            if (ry > bottom - 30) {
                break;
            }
            boolean ripe = f.ripe() >= f.total();
            String right = f.ripe() + " / " + f.total();
            float vw = ui.textWidth("base_list_value", right);
            ui.text("base_list_value", right, rx + colW - vw, ry, ui.color(ripe ? "good" : "text"));
            ui.text("base_list", ui.ellipsize("base_list", f.name(), colW - vw - 12), rx, ry);
            ry += ui.lineHeight("base_list") + 3;
            float bar = ui.num(L + "bar");
            ui.rect(rx, ry, colW, bar, bar / 2f, ui.color("track"));
            ui.rect(rx, ry, colW * f.ripe() / (float) Math.max(1, f.total()), bar, bar / 2f, ui.color(ripe ? "good" : "base_tone"));
            ry += bar + 3;
            String eta = ripe ? Ui.tr("skirmish.base.farm_ready") : f.etaMs() > 0 ? Ui.tr("skirmish.base.farm_eta", BaseModule.left(f.etaMs()))
                    : Ui.tr("skirmish.base.farm_growing");
            ui.text("menu_row_desc", eta, rx, ry, ui.color(ripe ? "good" : "text_3"));
            ry += ui.lineHeight("menu_row_desc") + 8;
        }
    }

    private static float section(Ui ui, String title, float x, float y) {
        ui.text("menu_section", title.toUpperCase(java.util.Locale.ROOT), x, y);
        return y + ui.lineHeight("menu_section") + 6;
    }

    // ---- storage ----

    private void storage(Ui ui, BaseModule m, float x, float y, float cw, float bottom, double mx, double my) {
        float fieldH = ui.num("layout.menu.keybind_height");
        search.bounds(x, y, cw, fieldH);
        widget(ui, search, mx, my);
        y += fieldH + ui.num("layout.menu.content_gap");
        List<StorageIndex.Total> all = m.totals();
        List<StorageIndex.Total> rows = StorageIndex.search(all, query.get());
        Map<String, Integer> minimums = m.server().minimums;
        float top = y;
        float hintH = ui.lineHeight("menu_hint") + 6;
        float listBottom = bottom - hintH;
        pushClip(ui, x - 4, top, x + cw + 4, listBottom);
        float rowH = ui.num(L + "row_height");
        float ry = top - scroll;
        if (rows.isEmpty()) {
            String empty = all.isEmpty() ? Ui.tr(m.region() == null && m.scope.get() == BaseModule.Scope.BASE
                    ? "skirmish.base.storage_no_base" : "skirmish.base.storage_empty") : Ui.tr("skirmish.base.nothing_found");
            for (String line : ui.wrap("menu_row_desc", empty, cw)) {
                ui.text("menu_row_desc", line, x, ry + 6);
                ry += ui.lineHeight("menu_row_desc");
            }
        }
        float icon = ui.num(L + "icon");
        for (StorageIndex.Total t : rows) {
            if (ry + rowH >= top && ry <= listBottom) {
                Area area = rowAreas.computeIfAbsent(t.key(), k -> new Area(() -> route(k)));
                area.bounds(x - 4, ry, cw + 8, rowH);
                widget(ui, area, mx, my);
                boolean hover = area.contains(mx, my) && my >= top && my < listBottom;
                if (hover) {
                    ui.rect(x - 4, ry, cw + 8, rowH, ui.theme().radius("button_sm"), ui.color("fill_05"));
                }
                ItemStack stack = icons.computeIfAbsent(t.id(), BaseScreen::stackOf);
                var pose = ui.graphics().pose();
                pose.pushMatrix();
                pose.translate(Math.round(x), Math.round(ry + (rowH - icon) / 2f));
                pose.scale(icon / 16f, icon / 16f);
                ui.graphics().renderItem(stack, 0, 0);
                pose.popMatrix();
                float tx = x + icon + ui.num(L + "icon_gap");
                Integer min = minimums.get(t.key());
                Button minButton = minButtons.computeIfAbsent(t.key(), k -> new Button(
                        () -> minimums.containsKey(k) ? "≥ " + minimums.get(k) : Ui.tr("skirmish.base.min"), false,
                        () -> {
                            BaseModule mm = module();
                            StorageIndex.Total now = mm == null ? null : mm.totals().stream().filter(tt -> tt.key().equals(k)).findFirst().orElse(null);
                            if (mm != null && now != null) {
                                mm.toggleMinimum(now);
                            }
                        }).layout("layout.menu.small_").selected(() -> minimums.containsKey(k)));
                float mw = minButton.preferredWidth(ui);
                float mbh = ui.num("layout.menu.small_button_height");
                minButton.bounds(x + cw - mw, ry + (rowH - mbh) / 2f, mw, mbh);
                String count = StorageIndex.stacks(t.count(), stack.getMaxStackSize());
                OptionalDouble price = MarketModule.usualPrice(t.key());
                float valueRight = x + cw - mw - ui.num(L + "col_gap");
                float cwid = ui.textWidth("base_count", count);
                ui.text("base_count", count, valueRight - cwid, ry + (rowH - ui.lineHeight("base_count")) / 2f,
                        ui.color(min != null && t.count() < min ? "warn" : "text"));
                float nameW = valueRight - cwid - ui.num(L + "col_gap") - tx;
                float textH = ui.lineHeight("menu_row_title") + 2 + ui.lineHeight("menu_row_desc");
                float ty = ry + (rowH - textH) / 2f;
                ui.text("menu_row_title", ui.ellipsize("menu_row_title", t.name(), nameW), tx, ty);
                String where = Ui.tr("skirmish.base.in_chests", t.where().size());
                if (price.isPresent()) {
                    where += " · " + BaseHud.money(price.getAsDouble() * t.count());
                }
                ui.text("menu_row_desc", ui.ellipsize("menu_row_desc", where, nameW), tx, ty + ui.lineHeight("menu_row_title") + 2);
                widget(ui, minButton, mx, my);
            }
            ry += rowH;
        }
        popClip(ui);
        maxScroll = Math.max(0f, ry + scroll - top - (listBottom - top));
        scroll = Math.max(0f, Math.min(scroll, maxScroll));
        ui.text("menu_hint", ui.ellipsize("menu_hint", Ui.tr("skirmish.base.storage_hint"), cw), x, bottom - ui.lineHeight("menu_hint"));
    }

    /** Route to the chest with the most of an item, then close so you can walk there. */
    private void route(String key) {
        BaseModule m = module();
        if (m == null) {
            return;
        }
        for (StorageIndex.Total t : m.totals()) {
            if (t.key().equals(key)) {
                m.routeTo(t);
                onClose();
                return;
            }
        }
    }

    private static ItemStack stackOf(String id) {
        try {
            return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    // ---- log ----

    private void log(Ui ui, BaseModule m, float x, float y, float cw, float bottom) {
        List<BaseData.LogEntry> log = m.server().log;
        float top = y;
        pushClip(ui, x - 4, top, x + cw + 4, bottom);
        float ry = top - scroll;
        if (log.isEmpty()) {
            ui.text("menu_row_desc", Ui.tr("skirmish.base.log_empty"), x, ry + 6);
        }
        SimpleDateFormat day = new SimpleDateFormat("dd.MM");
        SimpleDateFormat time = new SimpleDateFormat("HH:mm");
        String today = day.format(new Date());
        float lh = ui.lineHeight("base_list");
        float timeW = ui.num(L + "log_time_width");
        for (int i = log.size() - 1; i >= 0; i--) {
            BaseData.LogEntry e = log.get(i);
            List<String> lines = ui.wrap("base_list", e.text, cw - timeW - 16);
            float rh = lines.size() * lh + 8;
            if (ry + rh >= top && ry <= bottom) {
                Date at = new Date(e.at);
                String stamp = day.format(at).equals(today) ? time.format(at) : day.format(at) + " " + time.format(at);
                ui.text("base_list_value", stamp, x, ry, ui.color("text_3"));
                ui.circle(x + timeW + 4, ry + lh / 2f, 6, ui.color(toneOf(e.kind)));
                float ly = ry;
                for (String line : lines) {
                    ui.text("base_list", line, x + timeW + 14, ly);
                    ly += lh;
                }
            }
            ry += rh;
        }
        popClip(ui);
        maxScroll = Math.max(0f, ry + scroll - top - (bottom - top));
        scroll = Math.max(0f, Math.min(scroll, maxScroll));
    }

    private static String toneOf(String kind) {
        return switch (kind) {
            case "intruder" -> "bad";
            case "stock", "timer" -> "warn";
            case "farm" -> "good";
            case "base" -> "accent";
            default -> "base_tone";
        };
    }

    // ---- 3D model ----

    private void model(Ui ui, BaseModule m, float x, float y, float cw, float bottom, double mx, double my) {
        BaseData.Region region = m.region();
        if (region == null) {
            for (String line : ui.wrap("menu_row_desc", Ui.tr("skirmish.base.model.no_base"), cw)) {
                ui.text("menu_row_desc", line, x, y);
                y += ui.lineHeight("menu_row_desc");
            }
            return;
        }
        if (!model.captured()) {
            capture();
        }
        if (!model.captured()) {
            ui.text("menu_row_desc", Ui.tr("skirmish.base.model.far"), x, y);
            return;
        }
        float side = ui.num(L + "model_side");
        float size = Math.min(cw - side - ui.num(L + "col_gap"), bottom - y);
        modelArea.bounds(x, y, size, size);
        widget(ui, modelArea, mx, my);
        ui.box(x, y, size, size, ui.theme().radius("tile"), ui.color("map_bg"), ui.color("stroke"));
        Identifier tex = model.texture();
        var pose = ui.graphics().pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(size / BaseModelView.SIZE, size / BaseModelView.SIZE);
        ui.graphics().blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, BaseModelView.SIZE, BaseModelView.SIZE,
                BaseModelView.SIZE, BaseModelView.SIZE, ui.fade(0xFFFFFFFF));
        pose.popMatrix();

        float sx = x + size + ui.num(L + "col_gap");
        float sy = y;
        sy = section(ui, Ui.tr("skirmish.base.model.title"), sx, sy);
        for (String line : ui.wrap("menu_row_desc", Ui.tr("skirmish.base.model.info", region.sizeX(), region.sizeY(), region.sizeZ(), model.blocks()), side)) {
            ui.text("menu_row_desc", line, sx, sy);
            sy += ui.lineHeight("menu_row_desc");
        }
        sy += 10;
        sy = section(ui, Ui.tr("skirmish.base.model.cut"), sx, sy);
        int layer = Math.min(model.layers() - 1, model.cut);
        ui.text("base_list", Ui.tr("skirmish.base.model.cut_value", region.y0 + layer), sx, sy);
        sy += ui.lineHeight("base_list") + 6;
        float sh = ui.num(L + "slider_height");
        cutSlider.bounds(sx, sy, side, sh);
        widget(ui, cutSlider, mx, my);
        sy += sh + 14;
        refresh.layout("layout.menu.small_");
        float rw = refresh.preferredWidth(ui);
        refresh.bounds(sx, sy, rw, ui.num("layout.menu.small_button_height"));
        widget(ui, refresh, mx, my);
        sy += ui.num("layout.menu.small_button_height") + 14;
        for (String line : ui.wrap("menu_hint", Ui.tr("skirmish.base.model.hint"), side)) {
            ui.text("menu_hint", line, sx, sy);
            sy += ui.lineHeight("menu_hint");
        }
    }

    // ---- input ----

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == 3) {
            model.zoom = Math.max(0.5f, Math.min(4f, model.zoom * (scrollY > 0 ? 1.15f : 1 / 1.15f)));
            return true;
        }
        scroll = Math.max(0f, Math.min(maxScroll, scroll - (float) scrollY * Theme.get().num("layout.menu.scroll_step")));
        return true;
    }
}
