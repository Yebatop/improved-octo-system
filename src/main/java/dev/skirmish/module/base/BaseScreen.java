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
 * region in its real textures you can turn, move, zoom and cut open, with markers over the chests (what is inside on
 * hover, a route on click, the storage search lit up).
 */
public final class BaseScreen extends UiScreen {
    private static final String L = BaseModule.L;
    private static int tab;
    /** Storage as a grid of items (0) or a list (1). */
    private static int storageView;

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
    private final List<Button> views = List.of(
            new Button(() -> Ui.tr("skirmish.base.model.view_iso"), false, () -> model.preset(0)),
            new Button(() -> Ui.tr("skirmish.base.model.view_top"), false, () -> model.preset(1)),
            new Button(() -> Ui.tr("skirmish.base.model.view_side"), false, () -> model.preset(2)));
    /** Left-drag turns the model, right-drag moves it, a click on a chest marker routes to that chest. */
    private final Widget modelArea = new Widget() {
        private double lastX;
        private double lastY;
        private double downX;
        private double downY;
        private int button;

        @Override
        protected void draw(Ui ui, double mx, double my) {
        }

        @Override
        public boolean clickable() {
            return hoverPin != null;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            lastX = mx;
            lastY = my;
            downX = mx;
            downY = my;
            button = b;
            return b == 0 || b == 1;
        }

        @Override
        public void mouseDragged(double mx, double my, int b) {
            if (button == 1) {
                model.panX += (float) ((mx - lastX) / Math.max(1f, w));
                model.panY += (float) ((my - lastY) / Math.max(1f, h));
            } else {
                model.yaw += (float) (mx - lastX) * 0.012f;
                model.pitch = Math.max(-0.2f, Math.min(1.55f, model.pitch + (float) (my - lastY) * 0.012f));
            }
            lastX = mx;
            lastY = my;
        }

        @Override
        public void mouseReleased(double mx, double my, int b) {
            Pin pin = hoverPin;
            if (button == 0 && pin != null && Math.hypot(mx - downX, my - downY) < 4) {
                routeChest(pin.chest());
            }
        }
    };
    private @Nullable Pin hoverPin;
    private int pinCount;
    private int pinMatches;
    private final Segmented storageViews = new Segmented(Segmented.Spec.MENU,
            () -> List.of(Ui.tr("skirmish.base.view.grid"), Ui.tr("skirmish.base.view.list")), () -> storageView, i -> {
        storageView = i;
        scroll = 0;
    });
    private final Map<String, Cell> cells = new HashMap<>();

    /** A storage grid cell: left click routes to the chest with the most, right click sets or clears the minimum. */
    private final class Cell extends Widget {
        private final String key;

        Cell(String key) {
            this.key = key;
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                route(key);
                return true;
            }
            BaseModule m = module();
            if (button == 1 && m != null) {
                m.totals().stream().filter(t -> t.key().equals(key)).findFirst().ifPresent(m::toggleMinimum);
                return true;
            }
            return false;
        }
    }

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
        float crop = ui.num(L + "crop_icon");
        float cropGap = ui.num(L + "icon_gap");
        for (BaseModule.Farm f : farms) {
            if (ry > bottom - 30) {
                break;
            }
            boolean ripe = f.ripe() >= f.total();
            // The crop's own item beside its line, bar and time.
            float blockH = ui.lineHeight("base_list") + 3 + ui.num(L + "bar") + 3 + ui.lineHeight("menu_row_desc");
            ItemStack cropStack = icons.computeIfAbsent("minecraft:" + BaseText.cropItem(f.kind()), BaseScreen::stackOf);
            var pose = ui.graphics().pose();
            pose.pushMatrix();
            pose.translate(Math.round(rx), Math.round(ry + (blockH - crop) / 2f));
            pose.scale(crop / 16f, crop / 16f);
            ui.graphics().renderItem(cropStack, 0, 0);
            pose.popMatrix();
            float fx = rx + crop + cropGap;
            float fw = colW - crop - cropGap;
            String right = f.ripe() + " / " + f.total();
            float vw = ui.textWidth("base_list_value", right);
            ui.text("base_list_value", right, fx + fw - vw, ry, ui.color(ripe ? "good" : "text"));
            ui.text("base_list", ui.ellipsize("base_list", f.name(), fw - vw - 12), fx, ry);
            ry += ui.lineHeight("base_list") + 3;
            float bar = ui.num(L + "bar");
            ui.rect(fx, ry, fw, bar, bar / 2f, ui.color("track"));
            ui.rect(fx, ry, fw * f.ripe() / (float) Math.max(1, f.total()), bar, bar / 2f, ui.color(ripe ? "good" : "base_tone"));
            ry += bar + 3;
            String eta = ripe ? Ui.tr("skirmish.base.farm_ready") : f.etaMs() > 0 ? Ui.tr("skirmish.base.farm_eta", BaseModule.left(f.etaMs()))
                    : Ui.tr("skirmish.base.farm_growing");
            ui.text("menu_row_desc", eta, fx, ry, ui.color(ripe ? "good" : "text_3"));
            ry += ui.lineHeight("menu_row_desc") + 10;
        }
    }

    private static float section(Ui ui, String title, float x, float y) {
        ui.text("menu_section", title.toUpperCase(java.util.Locale.ROOT), x, y);
        return y + ui.lineHeight("menu_section") + 6;
    }

    // ---- storage ----

    private void storage(Ui ui, BaseModule m, float x, float y, float cw, float bottom, double mx, double my) {
        float fieldH = ui.num("layout.menu.keybind_height");
        float segW = storageViews.preferredWidth(ui);
        float segH = storageViews.preferredHeight(ui);
        float gapX = ui.num(L + "col_gap");
        search.bounds(x, y, cw - segW - gapX, fieldH);
        widget(ui, search, mx, my);
        storageViews.bounds(x + cw - segW, y + (fieldH - segH) / 2f, segW, segH);
        widget(ui, storageViews, mx, my);
        y += fieldH + ui.num("layout.menu.content_gap");
        List<StorageIndex.Total> all = m.totals();
        List<StorageIndex.Total> rows = StorageIndex.search(all, query.get());
        Map<String, Integer> minimums = m.server().minimums;
        if (storageView == 0 && !rows.isEmpty()) {
            storageGrid(ui, rows, minimums, x, y, cw, bottom, mx, my);
            return;
        }
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

    /**
     * The storage as a grid like an inventory: each item's icon with a short count, a red frame when it is under its
     * minimum, an accent one when a minimum is set; hover for the details.
     */
    private void storageGrid(Ui ui, List<StorageIndex.Total> rows, Map<String, Integer> minimums, float x, float top, float cw, float bottom,
                             double mx, double my) {
        float hintH = ui.lineHeight("menu_hint") + 6;
        float listBottom = bottom - hintH;
        float size = ui.num(L + "cell");
        float gap = ui.num(L + "cell_gap");
        int cols = Math.max(1, (int) ((cw + gap) / (size + gap)));
        float cellW = (cw - gap * (cols - 1)) / cols;
        float icon = ui.num(L + "cell_icon");
        float pad = ui.num(L + "cell_pad");
        char decimal = Ui.decimal(1.5, 1).charAt(1);
        StorageIndex.Total hovered = null;
        pushClip(ui, x - 4, top, x + cw + 4, listBottom);
        for (int i = 0; i < rows.size(); i++) {
            StorageIndex.Total t = rows.get(i);
            float cx = x + (i % cols) * (cellW + gap);
            float cy = top - scroll + (i / cols) * (size + gap);
            if (cy + size < top || cy > listBottom) {
                continue;
            }
            Cell cell = cells.computeIfAbsent(t.key(), Cell::new);
            cell.bounds(cx, cy, cellW, size);
            widget(ui, cell, mx, my);
            boolean hover = cell.contains(mx, my) && my >= top && my < listBottom;
            Integer min = minimums.get(t.key());
            boolean low = min != null && t.count() < min;
            ui.box(cx, cy, cellW, size, ui.theme().radius("tile"), ui.color(hover ? "fill_08" : "fill_04"),
                    ui.color(low ? "warn" : min != null ? "accent" : hover ? "stroke_07" : "stroke"));
            ItemStack stack = icons.computeIfAbsent(t.id(), BaseScreen::stackOf);
            var pose = ui.graphics().pose();
            pose.pushMatrix();
            pose.translate(Math.round(cx + (cellW - icon) / 2f), Math.round(cy + pad));
            pose.scale(icon / 16f, icon / 16f);
            ui.graphics().renderItem(stack, 0, 0);
            pose.popMatrix();
            String count = BaseText.shortCount(t.count(), decimal);
            float tw = ui.textWidth("base_cell_count", count);
            ui.text("base_cell_count", count, cx + cellW - pad - tw, cy + size - pad - ui.lineHeight("base_cell_count"),
                    ui.color(low ? "warn" : "text"));
            if (hover) {
                hovered = t;
            }
        }
        popClip(ui);
        int lines = (rows.size() + cols - 1) / cols;
        maxScroll = Math.max(0f, lines * (size + gap) - gap - (listBottom - top));
        scroll = Math.max(0f, Math.min(scroll, maxScroll));
        ui.text("menu_hint", ui.ellipsize("menu_hint", Ui.tr("skirmish.base.grid_hint"), cw), x, bottom - ui.lineHeight("menu_hint"));
        if (hovered != null) {
            cellTip(ui, hovered, minimums.get(hovered.key()), mx, my);
        }
    }

    /** The details of a grid cell: name, exact count, how many chests, value, minimum, what the clicks do. */
    private void cellTip(Ui ui, StorageIndex.Total t, @Nullable Integer min, double mx, double my) {
        ItemStack stack = icons.computeIfAbsent(t.id(), BaseScreen::stackOf);
        List<String> lines = new java.util.ArrayList<>();
        lines.add(StorageIndex.stacks(t.count(), stack.getMaxStackSize()) + " · " + Ui.tr("skirmish.base.in_chests", t.where().size()));
        OptionalDouble price = MarketModule.usualPrice(t.key());
        if (price.isPresent()) {
            lines.add(Ui.tr("skirmish.base.tip_value", BaseHud.money(price.getAsDouble() * t.count())));
        }
        if (min != null) {
            lines.add(Ui.tr("skirmish.base.tip_min", min));
        }
        float pad = 10;
        float w = ui.textWidth("menu_row_title", t.name());
        for (String l : lines) {
            w = Math.max(w, ui.textWidth("menu_row_desc", l));
        }
        String hint = Ui.tr("skirmish.base.grid_tip_hint");
        w = Math.max(w, ui.textWidth("menu_hint", hint)) + pad * 2;
        float h = pad * 2 + ui.lineHeight("menu_row_title") + 4 + lines.size() * ui.lineHeight("menu_row_desc") + 6 + ui.lineHeight("menu_hint");
        float tx = (float) mx + 14;
        float ty = (float) my + 12;
        if (tx + w > ui.width() - 8) {
            tx = (float) mx - 14 - w;
        }
        if (ty + h > ui.height() - 8) {
            ty = ui.height() - 8 - h;
        }
        ui.box(tx, ty, w, h, ui.theme().radius("tile"), ui.color("window"), ui.color(min != null && t.count() < min ? "warn" : "base_tone"));
        float cy = ty + pad;
        ui.text("menu_row_title", t.name(), tx + pad, cy);
        cy += ui.lineHeight("menu_row_title") + 4;
        for (String l : lines) {
            ui.text("menu_row_desc", l, tx + pad, cy);
            cy += ui.lineHeight("menu_row_desc");
        }
        ui.text("menu_hint", hint, tx + pad, cy + 6, ui.color("base_tone"));
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

    /** Route to a chest picked on the model, named after its biggest stack. */
    private void routeChest(BaseData.Chest chest) {
        BaseModule m = module();
        if (m == null) {
            return;
        }
        BaseData.Stack best = null;
        for (BaseData.Stack s : chest.items) {
            if (best == null || s.count > best.count) {
                best = s;
            }
        }
        m.routeToChest(chest, best != null ? best.name : BaseModule.chestName(chest));
        onClose();
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

    /** A chest marker on the model: where it is on screen, whether something hides it, whether it has the search. */
    private record Pin(BaseData.Chest chest, float x, float y, float depth, boolean hidden, boolean match) {
    }

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
        float aw = cw - side - ui.num(L + "col_gap");
        float ah = bottom - y;
        modelArea.bounds(x, y, aw, ah);
        widget(ui, modelArea, mx, my);
        float px = Ui.designScale() * minecraft.getWindow().getGuiScale();
        ModelRaster.Look look = new ModelRaster.Look(ui.color("base_tone") & 0xFFFFFF, ui.color("base_model_bg") & 0xFFFFFF,
                ui.color("base_model_edge") & 0xFFFFFF);
        Identifier tex = model.texture(Math.round(aw * px), Math.round(ah * px), look);
        float radius = ui.theme().radius("tile");
        ModelRaster.Frame frame = model.frame();
        if (!model.ready() || frame == null) {
            ui.box(x, y, aw, ah, radius, ui.color("base_model_edge"), ui.color("stroke"));
            ui.textCentered("menu_row_desc", Ui.tr("skirmish.base.model.drawing"), x, y, ah);
        } else {
            var pose = ui.graphics().pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(aw / frame.w(), ah / frame.h());
            ui.graphics().blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, frame.w(), frame.h(), frame.w(), frame.h(), ui.fade(0xFFFFFFFF));
            pose.popMatrix();
            ui.cornerMask(x, y, aw, ah, radius, ui.color("window"));
            ui.border(x, y, aw, ah, radius, ui.num("stroke.width"), ui.color("stroke"));
            pins(ui, m, region, frame, x, y, aw, ah, mx, my);
            compass(ui, frame, x, y);
        }

        float sx = x + aw + ui.num(L + "col_gap");
        float sy = y;
        sy = section(ui, Ui.tr("skirmish.base.model.title"), sx, sy);
        for (String line : ui.wrap("menu_row_desc", Ui.tr("skirmish.base.model.info", region.sizeX(), region.sizeY(), region.sizeZ(), model.blocks()), side)) {
            ui.text("menu_row_desc", line, sx, sy);
            sy += ui.lineHeight("menu_row_desc");
        }
        sy += 10;
        sy = section(ui, Ui.tr("skirmish.base.model.view"), sx, sy);
        float bh = ui.num("layout.menu.small_button_height");
        float bx = sx;
        for (Button b : views) {
            b.layout("layout.menu.small_");
            float bw = b.preferredWidth(ui);
            if (bx > sx && bx + bw > sx + side) {
                bx = sx;
                sy += bh + 6;
            }
            b.bounds(bx, sy, bw, bh);
            widget(ui, b, mx, my);
            bx += bw + 6;
        }
        sy += bh + 12;
        sy = section(ui, Ui.tr("skirmish.base.model.cut"), sx, sy);
        int layer = Math.min(model.layers() - 1, model.cut);
        ui.text("base_list", Ui.tr("skirmish.base.model.cut_value", region.y0 + layer), sx, sy);
        sy += ui.lineHeight("base_list") + 6;
        float sh = ui.num(L + "slider_height");
        cutSlider.bounds(sx, sy, side, sh);
        widget(ui, cutSlider, mx, my);
        sy += sh + 12;
        sy = section(ui, Ui.tr("skirmish.base.model.chests"), sx, sy);
        String chests = Ui.tr("skirmish.base.model.chests_info", pinCount);
        if (!query.get().isBlank()) {
            chests = Ui.tr("skirmish.base.model.search", query.get().strip(), pinMatches) + " " + chests;
        }
        for (String line : ui.wrap("menu_row_desc", chests, side)) {
            ui.text("menu_row_desc", line, sx, sy);
            sy += ui.lineHeight("menu_row_desc");
        }
        sy += 12;
        refresh.layout("layout.menu.small_");
        float rw = refresh.preferredWidth(ui);
        refresh.bounds(sx, sy, rw, bh);
        widget(ui, refresh, mx, my);
        sy += bh + 12;
        for (String line : ui.wrap("menu_hint", Ui.tr("skirmish.base.model.hint"), side)) {
            ui.text("menu_hint", line, sx, sy);
            sy += ui.lineHeight("menu_hint");
        }
        if (hoverPin != null) {
            pinTooltip(ui, hoverPin, mx, my);
        }
    }

    /**
     * Markers over the chests you have looked into: an item bubble where there is room (nearest first), a dot
     * elsewhere; dimmed when something is in front, highlighted when it holds what the storage search looks for.
     */
    private void pins(Ui ui, BaseModule m, BaseData.Region region, ModelRaster.Frame frame, float x, float y, float aw, float ah,
                      double mx, double my) {
        float kx = aw / frame.w();
        float ky = ah / frame.h();
        java.util.Set<String> matching = new java.util.HashSet<>();
        boolean searching = !query.get().isBlank();
        if (searching) {
            for (StorageIndex.Total t : StorageIndex.search(m.totals(), query.get())) {
                for (StorageIndex.Where w : t.where()) {
                    matching.add(w.chest());
                }
            }
        }
        List<Pin> pins = new java.util.ArrayList<>();
        for (BaseData.Chest c : m.indexedChests()) {
            if (!region.contains(c.dim, c.x, c.y, c.z) || c.y < region.y0 || c.y > region.y1 || c.y - region.y0 > model.cut) {
                continue;
            }
            float[] p = frame.view().project(c.x - region.x0 + 0.5f, c.y - region.y0 + 0.9f, c.z - region.z0 + 0.5f);
            int ix = (int) p[0];
            int iy = (int) p[1];
            if (ix < 0 || iy < 0 || ix >= frame.w() || iy >= frame.h()) {
                continue;
            }
            boolean hidden = frame.depth()[iy * frame.w() + ix] < p[2] - 0.6f;
            pins.add(new Pin(c, x + p[0] * kx, y + p[1] * ky, p[2], hidden, matching.contains(c.key())));
        }
        pinCount = pins.size();
        pinMatches = (int) pins.stream().filter(Pin::match).count();
        // Hover: the nearest marker under the mouse.
        hoverPin = null;
        float pinSize = ui.num(L + "model_pin");
        if (modelArea.contains(mx, my)) {
            double best = pinSize * 0.75;
            for (Pin p : pins) {
                double d = Math.hypot(mx - p.x(), my - (p.y() - pinSize * 0.6));
                if (d < best) {
                    best = d;
                    hoverPin = p;
                }
            }
        }
        // Bubbles where they do not overlap, nearest first; the rest are dots.
        pins.sort(java.util.Comparator.comparingDouble(Pin::depth));
        List<float[]> placed = new java.util.ArrayList<>();
        List<Pin> bubbles = new java.util.ArrayList<>();
        for (Pin p : pins) {
            float bx0 = p.x() - pinSize / 2f;
            float by0 = p.y() - pinSize - 5;
            boolean free = true;
            for (float[] r : placed) {
                if (bx0 < r[0] + pinSize + 2 && bx0 + pinSize + 2 > r[0] && by0 < r[1] + pinSize + 2 && by0 + pinSize + 2 > r[1]) {
                    free = false;
                    break;
                }
            }
            if (free || p == hoverPin) {
                placed.add(new float[]{bx0, by0});
                bubbles.add(p);
            }
        }
        pushClip(ui, x, y, x + aw, y + ah);
        int accent = ui.color("base_tone");
        for (Pin p : pins) {
            if (!bubbles.contains(p)) {
                float a = searching && !p.match() ? 0.35f : p.hidden() ? 0.5f : 1f;
                ui.pushAlpha(a);
                ui.circle(p.x(), p.y(), 7, 0xFF000000 | ui.color("window"));
                ui.circle(p.x(), p.y(), 5, p.match() ? ui.color("good") : accent);
                ui.popAlpha();
            }
        }
        for (int i = bubbles.size() - 1; i >= 0; i--) {
            Pin p = bubbles.get(i);
            boolean hover = p == hoverPin;
            float a = hover ? 1f : searching && !p.match() ? 0.35f : p.hidden() ? 0.55f : 1f;
            float size = hover ? pinSize + 4 : pinSize;
            float bx0 = p.x() - size / 2f;
            float by0 = p.y() - size - 5;
            int stroke = p.match() ? ui.color("good") : accent;
            ui.pushAlpha(a);
            ui.triangle(p.x() - 4, by0 + size - 1, p.x() + 4, by0 + size - 1, p.x(), p.y(), 1f, stroke);
            ui.box(bx0, by0, size, size, size * 0.3f, ui.color("window"), stroke);
            ItemStack icon = topIcon(p.chest());
            if (!icon.isEmpty()) {
                float is = size - 6;
                var pose = ui.graphics().pose();
                pose.pushMatrix();
                pose.translate(bx0 + 3, by0 + 3);
                pose.scale(is / 16f, is / 16f);
                ui.graphics().renderItem(icon, 0, 0);
                pose.popMatrix();
            }
            ui.popAlpha();
        }
        popClip(ui);
    }

    private ItemStack topIcon(BaseData.Chest chest) {
        BaseData.Stack best = null;
        for (BaseData.Stack s : chest.items) {
            if (best == null || s.count > best.count) {
                best = s;
            }
        }
        return best == null ? ItemStack.EMPTY : icons.computeIfAbsent(best.id, BaseScreen::stackOf);
    }

    /** What is in the chest under the mouse: its biggest stacks, then the route hint. */
    private void pinTooltip(Ui ui, Pin pin, double mx, double my) {
        BaseData.Chest chest = pin.chest();
        List<BaseData.Stack> items = new java.util.ArrayList<>(chest.items);
        items.sort(java.util.Comparator.comparingInt((BaseData.Stack s) -> s.count).reversed());
        int shown = Math.min(items.size(), 6);
        float pad = 10;
        float w = ui.num(L + "model_tip_width");
        float line = Math.max(18, ui.lineHeight("base_list") + 4);
        float h = pad * 2 + ui.lineHeight("menu_row_title") + 6 + Math.max(1, shown) * line
                + (items.size() > shown ? ui.lineHeight("menu_row_desc") : 0) + 6 + ui.lineHeight("menu_hint");
        float tx = (float) mx + 16;
        float ty = (float) my + 12;
        if (tx + w > ui.width() - 8) {
            tx = (float) mx - 16 - w;
        }
        if (ty + h > ui.height() - 8) {
            ty = ui.height() - 8 - h;
        }
        ui.box(tx, ty, w, h, ui.theme().radius("tile"), ui.color("window"), ui.color(pin.match() ? "good" : "base_tone"));
        float cy = ty + pad;
        ui.text("menu_row_title", ui.ellipsize("menu_row_title", BaseModule.chestName(chest), w - pad * 2), tx + pad, cy);
        cy += ui.lineHeight("menu_row_title") + 6;
        if (items.isEmpty()) {
            ui.text("menu_row_desc", Ui.tr("skirmish.base.model.empty_chest"), tx + pad, cy);
            cy += line;
        }
        for (int i = 0; i < shown; i++) {
            BaseData.Stack s = items.get(i);
            ItemStack stack = icons.computeIfAbsent(s.id, BaseScreen::stackOf);
            var pose = ui.graphics().pose();
            pose.pushMatrix();
            pose.translate(tx + pad, cy + (line - 16) / 2f - 1);
            ui.graphics().renderItem(stack, 0, 0);
            pose.popMatrix();
            String count = StorageIndex.stacks(s.count, stack.getMaxStackSize());
            float cw = ui.textWidth("base_list_value", count);
            float ly = cy + (line - ui.lineHeight("base_list")) / 2f;
            ui.text("base_list_value", count, tx + w - pad - cw, ly);
            ui.text("base_list", ui.ellipsize("base_list", s.name, w - pad * 2 - 22 - cw - 8), tx + pad + 22, ly);
            cy += line;
        }
        if (items.size() > shown) {
            ui.text("menu_row_desc", Ui.tr("skirmish.base.model.more", items.size() - shown), tx + pad, cy);
            cy += ui.lineHeight("menu_row_desc");
        }
        cy += 6;
        ui.text("menu_hint", Ui.tr("skirmish.base.model.route_hint"), tx + pad, cy, ui.color("base_tone"));
    }

    /** A small compass in the corner: where north is in this view (just the letter when north points into it). */
    private static void compass(Ui ui, ModelRaster.Frame frame, float x, float y) {
        ModelRaster.View v = frame.view();
        float[] a = v.project(0, 0, 0);
        float[] b = v.project(0, 0, -1);
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        float len = (float) Math.hypot(dx, dy);
        float d = ui.num(L + "model_compass");
        float th = ui.lineHeight("menu_hint");
        float cx = x + 12 + th + d / 2f;
        float cy = y + 12 + th + d / 2f;
        ui.circle(cx, cy, d, 0xC0000000 | ui.color("window") & 0xFFFFFF);
        ui.ring(cx, cy, d, 1f, ui.color("stroke"));
        String n = Ui.tr("skirmish.base.model.north");
        float tw = ui.textWidth("menu_hint", n);
        if (len < v.scale() * 0.3f) {
            ui.text("menu_hint", n, cx - tw / 2f, cy - th / 2f, ui.color("text"));
            return;
        }
        float nx = dx / len;
        float ny = dy / len;
        float r = d / 2f - 4;
        ui.triangle(cx + nx * r, cy + ny * r, cx - ny * 3.5f, cy + nx * 3.5f, cx + ny * 3.5f, cy - nx * 3.5f, 0.5f, ui.color("bad"));
        ui.triangle(cx - nx * r, cy - ny * r, cx + ny * 3.5f, cy - nx * 3.5f, cx - ny * 3.5f, cy + nx * 3.5f, 0.5f, ui.color("text_3"));
        float lx = cx + nx * (d / 2f + th * 0.7f);
        float ly = cy + ny * (d / 2f + th * 0.7f);
        ui.text("menu_hint", n, lx - tw / 2f, ly - th / 2f, ui.color("text"));
    }

    // ---- input ----

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == 3) {
            model.zoom = Math.max(0.5f, Math.min(8f, model.zoom * (scrollY > 0 ? 1.15f : 1 / 1.15f)));
            return true;
        }
        scroll = Math.max(0f, Math.min(maxScroll, scroll - (float) scrollY * Theme.get().num("layout.menu.scroll_step")));
        return true;
    }
}
