package dev.skirmish.module.market;

import dev.skirmish.module.market.mixin.ContainerScreenAccessor;
import dev.skirmish.module.market.parse.LotParser;
import dev.skirmish.module.market.parse.PriceHistory;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.ScreenWidgets;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Price chips over the lots of an open auction page and a ring around the cheapest lot (per unit) of each item.
 * Reads slots only: never clicks, never moves items. The page is re-read on every screen tick; drawing uses the
 * last result.
 */
final class AuctionOverlay {
    /** GUI px size of a slot's item area. */
    private static final int SLOT_SIZE = 16;
    /** The same lot seen again within this window is not recorded again. */
    private static final long DEDUP_MS = 6L * 60 * 60 * 1000;
    static final long MEDIAN_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000;
    static final int MEDIAN_MIN_SAMPLES = 3;

    private record Parsed(ItemStack stack, LotParser.@Nullable Lot lot) {
    }

    private final MarketModule module;
    private final AbstractContainerScreen<?> screen;
    /** Last parse per slot index, reused while the slot holds the same stack object. */
    private final Map<Integer, Parsed> parsed = new HashMap<>();
    private Map<Slot, LotInfo> page = Map.of();
    private Map<ItemStack, LotInfo> byStack = new IdentityHashMap<>();
    private boolean loggedPage;

    AuctionOverlay(MarketModule module, AbstractContainerScreen<?> screen) {
        this.module = module;
        this.screen = screen;
    }

    AbstractContainerScreen<?> screen() {
        return screen;
    }

    /** Called after every init/resize (Fabric recreates the per-screen events then). */
    void attach() {
        ScreenWidgets.attach(screen, this::draw);
        ScreenEvents.afterTick(screen).register(s -> refresh());
        refresh();
    }

    /** The lot under a tooltip, if the stack is on this page. */
    @Nullable LotInfo lotFor(ItemStack stack) {
        return byStack.get(stack);
    }

    private void refresh() {
        if (!module.isEnabled()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        PriceHistory history = module.history();
        Map<Slot, LotInfo> next = new java.util.LinkedHashMap<>();
        Map<String, Double> cheapest = new HashMap<>();
        Map<String, Integer> perItem = new HashMap<>();
        boolean recorded = false;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == player.getInventory() || !slot.isActive()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                parsed.remove(slot.index);
                continue;
            }
            Parsed p = parsed.get(slot.index);
            boolean fresh = p == null || p.stack() != stack;
            if (fresh) {
                p = new Parsed(stack, LotInfo.read(stack));
                parsed.put(slot.index, p);
            }
            LotParser.Lot lot = p.lot();
            if (lot == null) {
                continue;
            }
            int count = Math.max(1, stack.getCount());
            String key = LotInfo.itemKey(stack);
            double unit = lot.kind() == LotParser.Kind.UNIT ? lot.price() : lot.price() / (double) count;
            if (fresh) {
                module.rememberName(LotInfo.nameKey(stack), key);
                if (lot.kind() != LotParser.Kind.BID && module.recordHistory()) {
                    long total = lot.kind() == LotParser.Kind.UNIT ? lot.price() * count : lot.price();
                    String seller = lot.seller() == null ? "?" : lot.seller();
                    recorded |= history.recordLot(key, seller, total, count, now, DEDUP_MS);
                }
            }
            var median = history.median(key, now, MEDIAN_MAX_AGE_MS, MEDIAN_MIN_SAMPLES);
            double m = median.isPresent() ? median.getAsDouble() : LotInfo.NO_MEDIAN;
            PriceHistory.Verdict verdict = median.isPresent() && lot.kind() != LotParser.Kind.BID
                    ? PriceHistory.verdict(unit, m, module.tolerance()) : PriceHistory.Verdict.NORMAL;
            next.put(slot, new LotInfo(key, stack.getHoverName().getString(), lot, count, unit, m, verdict, false));
            if (lot.kind() != LotParser.Kind.BID) {
                perItem.merge(key, 1, Integer::sum);
                cheapest.merge(key, unit, Math::min);
            }
        }
        Map<ItemStack, LotInfo> stacks = new IdentityHashMap<>();
        for (Map.Entry<Slot, LotInfo> e : next.entrySet()) {
            LotInfo info = e.getValue();
            if (!info.isBid() && perItem.getOrDefault(info.itemKey(), 0) >= 2 && info.unitPrice() <= cheapest.get(info.itemKey())) {
                info = info.withCheapest(true);
                e.setValue(info);
            }
            stacks.put(e.getKey().getItem(), info);
        }
        page = next;
        byStack = stacks;
        if (recorded) {
            module.historyChanged();
        }
        if (!loggedPage && module.isDebug() && !next.isEmpty()) {
            loggedPage = true;
            module.log("auction page \"%s\": %d lots parsed", screen.getTitle().getString(), next.size());
            for (LotInfo info : next.values()) {
                module.log("  %s x%d: %s %d (%s), seller %s, per unit %.2f", info.name(), info.count(),
                        info.lot().kind(), info.lot().price(), info.itemKey(), info.lot().seller(), info.unitPrice());
            }
        }
    }

    private void draw(Ui ui, ScreenWidgets widgets, double mx, double my) {
        if (!module.isEnabled() || !module.overlayOn() || page.isEmpty()) {
            return;
        }
        ContainerScreenAccessor pos = (ContainerScreenAccessor) screen;
        float left = (float) Ui.toDesign(pos.skirmish$leftPos());
        float top = (float) Ui.toDesign(pos.skirmish$topPos());
        float size = (float) Ui.toDesign(SLOT_SIZE);
        String l = "layout.market.";
        char decimal = Ui.decimal(0.5, 1).contains(",") ? ',' : '.';
        String k = Ui.tr("skirmish.market.suffix.k");
        String kk = Ui.tr("skirmish.market.suffix.kk");
        String kkk = Ui.tr("skirmish.market.suffix.kkk");
        for (Map.Entry<Slot, LotInfo> e : page.entrySet()) {
            Slot slot = e.getKey();
            LotInfo info = e.getValue();
            float sx = left + (float) Ui.toDesign(slot.x);
            float sy = top + (float) Ui.toDesign(slot.y);
            if (info.cheapest() && module.highlightOn()) {
                float pad = ui.num(l + "best_pad");
                ui.rect(sx - pad, sy - pad, size + pad * 2, size + pad * 2, ui.theme().radius("slot"), ui.color("market_best_fill"));
                ui.border(sx - pad, sy - pad, size + pad * 2, size + pad * 2, ui.theme().radius("slot"), ui.num(l + "best_width"),
                        ui.color("market_best"));
            }
            String text = dev.skirmish.module.market.parse.PriceFormat.compact(info.unitPrice(), decimal, k, kk, kkk);
            if (info.isBid()) {
                text = text + "↑";
            }
            // Chip fill and text style share the token name.
            String style = switch (info.verdict()) {
                case CHEAP -> "market_chip_cheap";
                case DEAR -> "market_chip_dear";
                case NORMAL -> "market_chip";
            };
            float padX = ui.num(l + "chip_pad_x");
            float ch = ui.num(l + "chip_height");
            float cw = ui.textWidth(style, text) + padX * 2;
            float off = ui.num(l + "chip_offset");
            float cx = sx - off;
            float cy = sy - off;
            ui.box(cx, cy, cw, ch, ui.theme().radius("chip"), ui.color(style), ui.color("stroke_10"));
            ui.textCentered(style, text, cx + padX, cy, ch);
        }
    }
}
