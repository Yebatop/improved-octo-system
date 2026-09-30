package dev.skirmish.module.hwos;

import dev.skirmish.module.base.BaseModule;
import dev.skirmish.module.market.MarketModule;
import dev.skirmish.module.market.parse.PriceFormat;
import dev.skirmish.module.market.parse.PriceHistory;
import dev.skirmish.setting.StringSetting;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.TextField;
import dev.skirmish.ui.widget.Widget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Economy app: every item you have auction prices for (from the pages you browsed), its price chart with the median,
 * lots well under the usual price seen lately, and what your base storage is worth.
 */
final class EconomyApp implements HwOsScreen.OsApp {
    private static final long WINDOW = 14L * 24 * 60 * 60 * 1000;
    private final StringSetting query = new StringSetting("query", "", 48, false);
    private final TextField search = new TextField(query, () -> listScroll = 0).placeholder(() -> Ui.tr("skirmish.hwos.economy.search"));
    private final Map<String, Widget> rows = new HashMap<>();
    private final Map<String, ItemStack> icons = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private @Nullable String selected;
    private float listScroll;
    private float listMax;
    private float listX;
    private float listY;
    private float listW;
    private float listH;
    private List<String> keys = List.of();
    private long keysAt;

    @Override
    public String id() {
        return "economy";
    }

    private String name(String key) {
        return names.computeIfAbsent(key, k -> {
            String custom = OsData.customName(k);
            if (custom != null) {
                return custom.substring(0, 1).toUpperCase(Locale.ROOT) + custom.substring(1);
            }
            ItemStack stack = icon(k);
            return stack.isEmpty() ? k : stack.getHoverName().getString();
        });
    }

    private ItemStack icon(String key) {
        return icons.computeIfAbsent(key, k -> {
            String id = k.contains("|") ? k.substring(0, k.indexOf('|')) : k;
            try {
                return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
            } catch (RuntimeException e) {
                return ItemStack.EMPTY;
            }
        });
    }

    static String money(double v) {
        char decimal = Ui.decimal(0.5, 1).contains(",") ? ',' : '.';
        return PriceFormat.compact(v, decimal, Ui.tr("skirmish.market.suffix.k"), Ui.tr("skirmish.market.suffix.kk"),
                Ui.tr("skirmish.market.suffix.kkk"));
    }

    @Override
    public void draw(HwOsScreen s, Ui ui, float x, float y, float w, float h, double mx, double my) {
        String L = HwOsScreen.L;
        long now = System.currentTimeMillis();
        if (now - keysAt > 2000) {
            keys = new ArrayList<>(MarketModule.historyKeys());
            keysAt = now;
        }
        Map<String, List<PriceHistory.Sample>> history = new LinkedHashMap<>();
        for (String k : keys) {
            history.put(k, MarketModule.samples(k));
        }
        Theme t = Theme.get();
        List<OsData.Deal> deals = OsData.deals(history, now, WINDOW, (long) t.num(L + "deal_fresh_ms"), t.num(L + "deal_ratio"), 3);
        BaseModule.Summary base = BaseModule.summary();

        // Tiles.
        float gap = ui.num(L + "col_gap");
        float tileH = ui.num(L + "tile_height");
        float tw = (w - gap * 2) / 3f;
        HwOsScreen.tile(ui, x, y, tw, tileH, Integer.toString(keys.size()), Ui.tr("skirmish.hwos.economy.items"), "text");
        HwOsScreen.tile(ui, x + tw + gap, y, tw, tileH, Integer.toString(deals.size()), Ui.tr("skirmish.hwos.economy.deals_now"),
                deals.isEmpty() ? "text" : "good");
        HwOsScreen.tile(ui, x + (tw + gap) * 2, y, tw, tileH, base == null ? "—" : base.priced() > 0 ? money(base.value()) : "—",
                Ui.tr(base == null ? "skirmish.hwos.economy.no_base" : "skirmish.hwos.economy.base_value"), base != null && base.priced() > 0 ? "good" : "text");
        y += tileH + gap;
        h -= tileH + gap;

        // Left: search and the item list.
        float leftW = (w - gap) * 0.42f;
        float fieldH = ui.num("layout.menu.keybind_height");
        search.bounds(x, y, leftW, fieldH);
        s.use(ui, search, mx, my);
        List<String> shown = new ArrayList<>();
        String q = query.get().toLowerCase(Locale.ROOT).replace('ё', 'е').strip();
        for (String k : keys) {
            if (q.isEmpty() || name(k).toLowerCase(Locale.ROOT).replace('ё', 'е').contains(q) || k.contains(q)) {
                shown.add(k);
            }
        }
        shown.sort(Comparator.comparingInt((String k) -> -history.get(k).size()).thenComparing(this::name));
        if (selected == null && !shown.isEmpty()) {
            selected = shown.getFirst();
        }
        listX = x;
        listY = y + fieldH + 8;
        listW = leftW;
        listH = y + h - listY;
        float rowH = ui.num(L + "item_row");
        float icon = ui.num(L + "icon");
        s.clip(ui, listX - 4, listY, listX + listW + 4, listY + listH);
        float ry = listY - listScroll;
        if (shown.isEmpty()) {
            HwOsScreen.para(ui, "menu_row_desc", Ui.tr(keys.isEmpty() ? "skirmish.hwos.economy.empty" : "skirmish.hwos.nothing"),
                    listX, ry + 4, listW, ui.color("text_3"));
        }
        for (String k : shown) {
            if (ry + rowH >= listY && ry <= listY + listH) {
                Widget area = rows.computeIfAbsent(k, key -> new EventsApp.ClickArea(() -> selected = key));
                area.bounds(listX - 4, ry, listW + 8, rowH);
                s.use(ui, area, mx, my);
                boolean on = k.equals(selected);
                if (on || area.contains(mx, my) && my >= listY && my < listY + listH) {
                    ui.rect(listX - 4, ry, listW + 8, rowH, ui.theme().radius("button_sm"), ui.color(on ? "accent_16" : "fill_05"));
                }
                drawIcon(ui, icon(k), listX, ry + (rowH - icon) / 2f, icon);
                List<PriceHistory.Sample> samples = history.get(k);
                var median = MarketModule.usualPrice(k);
                String value = median.isPresent() ? money(median.getAsDouble()) : money(samples.getLast().unitPrice());
                float vw = ui.textWidth("hwos_value", value);
                float tx = listX + icon + 10;
                ui.text("hwos_value", value, listX + listW - vw, ry + (rowH - ui.lineHeight("hwos_value")) / 2f);
                ui.text("hwos_row", ui.ellipsize("hwos_row", name(k), listX + listW - vw - 10 - tx), tx, ry + 4);
                ui.text("menu_row_desc", Ui.tr("skirmish.hwos.economy.samples", samples.size()), tx, ry + 4 + ui.lineHeight("hwos_row"));
            }
            ry += rowH;
        }
        s.unclip(ui);
        listMax = Math.max(0f, ry + listScroll - listY - listH);
        listScroll = Math.min(listScroll, listMax);

        // Right: chart of the selected item, deals, base top.
        float rx = x + leftW + gap;
        float rw = w - leftW - gap;
        float cy = y;
        if (selected != null && history.containsKey(selected)) {
            cy = chart(ui, selected, history.get(selected), rx, cy, rw, ui.num(L + "chart_height"), now);
        }
        cy += gap / 2;
        cy = HwOsScreen.section(ui, Ui.tr("skirmish.hwos.economy.deals"), rx, cy);
        if (deals.isEmpty()) {
            cy = HwOsScreen.para(ui, "menu_row_desc", Ui.tr("skirmish.hwos.economy.no_deals"), rx, cy, rw, ui.color("text_3"));
        }
        float lineH = ui.lineHeight("hwos_row") + 4;
        for (OsData.Deal d : deals.subList(0, Math.min(4, deals.size()))) {
            String value = money(d.price()) + "  −" + Math.round(d.off() * 100) + "%";
            float vw = ui.textWidth("hwos_value", value);
            ui.text("hwos_value", value, rx + rw - vw, cy, ui.color("good"));
            ui.text("hwos_row", ui.ellipsize("hwos_row", name(d.key()) + " · " + Ui.tr("skirmish.hwos.economy.usual", money(d.median())),
                    rw - vw - 10), rx, cy);
            cy += lineH;
        }
        if (base != null && !base.top().isEmpty()) {
            cy += gap / 2;
            cy = HwOsScreen.section(ui, Ui.tr("skirmish.hwos.economy.base_top"), rx, cy);
            for (BaseModule.TopItem item : base.top()) {
                String value = Double.isNaN(item.value()) ? "×" + item.count() : money(item.value());
                float vw = ui.textWidth("hwos_value", value);
                ui.text("hwos_value", value, rx + rw - vw, cy);
                ui.text("hwos_row", ui.ellipsize("hwos_row", item.name() + " ×" + item.count(), rw - vw - 10), rx, cy);
                cy += lineH;
            }
        }
    }

    private static void drawIcon(Ui ui, ItemStack stack, float x, float y, float size) {
        var pose = ui.graphics().pose();
        pose.pushMatrix();
        pose.translate(Math.round(x), Math.round(y));
        pose.scale(size / 16f, size / 16f);
        ui.graphics().renderItem(stack, 0, 0);
        pose.popMatrix();
    }

    /** The price chart: samples as dots joined by a line, the median dashed, min/max on the axis. Returns the y under it. */
    private float chart(Ui ui, String key, List<PriceHistory.Sample> all, float x, float y, float w, float h, long now) {
        String L = HwOsScreen.L;
        List<PriceHistory.Sample> samples = new ArrayList<>();
        for (PriceHistory.Sample s : all) {
            if (now - s.time() <= WINDOW) {
                samples.add(s);
            }
        }
        if (samples.isEmpty()) {
            samples.addAll(all);
        }
        samples.sort(Comparator.comparingLong(PriceHistory.Sample::time));
        ui.text("hwos_row", ui.ellipsize("hwos_row", name(key), w), x, y);
        y += ui.lineHeight("hwos_row") + 4;
        double min = Double.MAX_VALUE;
        double max = 0;
        for (PriceHistory.Sample s : samples) {
            min = Math.min(min, s.unitPrice());
            max = Math.max(max, s.unitPrice());
        }
        var median = MarketModule.usualPrice(key);
        PriceHistory.Sample last = samples.getLast();
        String stats = Ui.tr("skirmish.hwos.economy.stats", median.isPresent() ? money(median.getAsDouble()) : "—", money(min), money(max),
                money(last.unitPrice()), new SimpleDateFormat("dd.MM HH:mm").format(new Date(last.time())));
        y = HwOsScreen.para(ui, "menu_row_desc", stats, x, y, w, ui.color("text_2")) + 6;
        ui.box(x, y, w, h, ui.theme().radius("tile"), ui.color("fill_05"), ui.color("stroke"));
        float pad = ui.num(L + "chart_pad");
        float px0 = x + pad;
        float px1 = x + w - pad;
        float py0 = y + pad;
        float py1 = y + h - pad;
        double lo = min;
        double hi = max;
        if (hi - lo < 1e-9) {
            lo = lo * 0.9;
            hi = hi * 1.1 + 1;
        }
        double span = hi - lo;
        lo -= span * 0.1;
        hi += span * 0.1;
        long t0 = samples.getFirst().time();
        long t1 = Math.max(t0 + 1, last.time());
        int grid = ui.color("stroke");
        for (int i = 0; i <= 2; i++) {
            float gy = py0 + (py1 - py0) * i / 2f;
            ui.rect(px0, gy, px1 - px0, 1f, 0f, grid);
            String label = money(hi - (hi - lo) * i / 2.0);
            ui.text("hwos_axis", label, px0 + 2, gy + 2, ui.color("text_4"));
        }
        if (median.isPresent()) {
            float my = (float) (py1 - (median.getAsDouble() - lo) / (hi - lo) * (py1 - py0));
            for (float dx = px0; dx < px1; dx += 10) {
                ui.rect(dx, my, Math.min(5, px1 - dx), 1.5f, 0f, ui.color("warn"));
            }
        }
        float prevX = Float.NaN;
        float prevY = Float.NaN;
        int accent = ui.color("accent");
        for (PriceHistory.Sample s : samples) {
            float sx = samples.size() == 1 ? (px0 + px1) / 2f : (float) (px0 + (s.time() - t0) / (double) (t1 - t0) * (px1 - px0));
            float sy = (float) (py1 - (s.unitPrice() - lo) / (hi - lo) * (py1 - py0));
            if (!Float.isNaN(prevX)) {
                ui.line(prevX, prevY, sx, sy, 1.5f, (accent & 0x00FFFFFF) | 0x99000000);
            }
            ui.circle(sx, sy, s == last ? 7f : 4f, accent);
            prevX = sx;
            prevY = sy;
        }
        return y + h;
    }

    @Override
    public boolean scrolled(double mx, double my, double amount) {
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            listScroll = Math.max(0f, Math.min(listMax, listScroll - (float) amount * Theme.get().num("layout.menu.scroll_step")));
            return true;
        }
        return false;
    }
}
