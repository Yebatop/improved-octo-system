package dev.skirmish.module.market;

import dev.skirmish.SkirmishClient;
import dev.skirmish.module.Module;
import dev.skirmish.module.market.parse.AuctionTitle;
import dev.skirmish.module.market.parse.HwText;
import dev.skirmish.module.market.parse.PriceFormat;
import dev.skirmish.module.market.parse.PriceHistory;
import dev.skirmish.module.market.parse.PurchaseLine;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Market: helps read the auction ({@code /ah}). On an auction page each lot gets a chip with its price per item,
 * the cheapest lot of each item is ringed, and the tooltip adds «за 1 шт», the usual price from the local history
 * (config/skirmish/prices.json) and a cheap/dear flag. Read only: it never clicks, buys, sells or moves items and
 * sends nothing (HolyWorld rule 2.4 bans auto-trading).
 */
public final class MarketModule extends Module {
    public static final String ID = "market";
    private static final int NAME_MEMORY = 256;

    final BoolSetting overlay = (BoolSetting) add(new BoolSetting("overlay", true)).feature("auction_helper");
    final BoolSetting highlightCheapest = add(new BoolSetting("highlight_cheapest", true));
    final BoolSetting tooltip = add(new BoolSetting("tooltip", true));
    final BoolSetting history = add(new BoolSetting("history", true));
    final NumberSetting tolerance = add(new NumberSetting("tolerance", 40, 10, 90, 5).unit(" %"));

    private @Nullable PriceStore store;
    private @Nullable AuctionOverlay current;
    /** Displayed names (keyword form) of recent lots → history key, to file purchase chat lines. */
    private final Map<String, String> recentNames = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > NAME_MEMORY;
        }
    };

    public MarketModule() {
        super(ID, true);
    }

    @Override
    public void onInitialize() {
        PriceStore prices = new PriceStore(SkirmishClient.configDir().resolve("prices.json"));
        prices.load();
        store = prices;
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container) || !AuctionTitle.isAuction(screen.getTitle().getString())) {
                return;
            }
            AuctionOverlay overlayNow = current;
            if (overlayNow == null || overlayNow.screen() != container) {
                overlayNow = new AuctionOverlay(this, container);
                current = overlayNow;
                log("auction screen opened: \"%s\" (%s)", screen.getTitle().getString(), screen.getClass().getSimpleName());
            }
            // Per-screen events are recreated on every init/resize, so everything is registered again here.
            ScreenEvents.remove(container).register(removed -> {
                if (current != null && current.screen() == removed) {
                    current = null;
                }
                prices.saveIfDirty();
            });
            overlayNow.attach();
        });
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> addTooltip(stack, lines));
        ClientReceiveMessageEvents.GAME.register((message, overlayLine) -> {
            if (!overlayLine) {
                onSystemLine(message.getString());
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> prices.saveNow());
    }

    PriceHistory history() {
        PriceStore s = store;
        return s == null ? new PriceHistory(1, 1) : s.history();
    }

    boolean recordHistory() {
        return history.get();
    }

    void historyChanged() {
        if (store != null) {
            store.markDirty();
        }
    }

    boolean overlayOn() {
        return overlay.get();
    }

    boolean highlightOn() {
        return highlightCheapest.get();
    }

    double tolerance() {
        return tolerance.get() / 100.0;
    }

    void rememberName(String nameKey, String itemKey) {
        if (!nameKey.isEmpty()) {
            recentNames.put(nameKey, itemKey);
        }
    }

    private void addTooltip(ItemStack stack, List<Component> lines) {
        AuctionOverlay page = current;
        if (!isEnabled() || !tooltip.get() || page == null || Minecraft.getInstance().screen != page.screen()) {
            return;
        }
        LotInfo info = page.lotFor(stack);
        if (info == null) {
            return;
        }
        char decimal = Ui.decimal(0.5, 1).contains(",") ? ',' : '.';
        Theme theme = Theme.get();
        if (info.count() > 1 || info.lot().kind() == dev.skirmish.module.market.parse.LotParser.Kind.UNIT) {
            lines.add(colored(Component.translatable("skirmish.market.tooltip.unit",
                    PriceFormat.full(info.unitPrice(), decimal)), theme.color("warn")));
        }
        if (info.isBid()) {
            lines.add(colored(Component.translatable("skirmish.market.tooltip.bid"), theme.color("text_3")));
        }
        if (info.median() > 0) {
            MutableComponent usual = colored(Component.translatable("skirmish.market.tooltip.usual",
                    PriceFormat.full(info.median(), decimal), history().samples(info.itemKey()).size()), theme.color("text_2"));
            int percent = (int) Math.round(Math.abs(info.unitPrice() / info.median() - 1) * 100);
            switch (info.verdict()) {
                case CHEAP -> usual.append(colored(Component.translatable("skirmish.market.tooltip.cheaper", percent), theme.color("good")));
                case DEAR -> usual.append(colored(Component.translatable("skirmish.market.tooltip.dearer", percent), theme.color("bad")));
                case NORMAL -> {
                }
            }
            lines.add(usual);
        } else if (history.get()) {
            lines.add(colored(Component.translatable("skirmish.market.tooltip.no_history"), theme.color("text_3")));
        }
        if (info.cheapest() && highlightCheapest.get()) {
            lines.add(colored(Component.translatable("skirmish.market.tooltip.cheapest"), theme.color("good")));
        }
    }

    private static MutableComponent colored(MutableComponent text, int argb) {
        return text.withStyle(Style.EMPTY.withColor(argb & 0xFFFFFF));
    }

    /** Purchase lines go to the history (when the item was seen on a page); unknown buy-like lines are logged. */
    private void onSystemLine(String line) {
        if (!isEnabled() || !history.get()) {
            return;
        }
        PurchaseLine.Purchase purchase = PurchaseLine.parse(line);
        if (purchase == null) {
            if (isDebug() && PurchaseLine.looksLikePurchase(line)) {
                log("purchase-like chat line not understood (please send it to the developers): %s", line);
            }
            return;
        }
        String key = recentNames.get(HwText.normalize(purchase.item()));
        if (key == null) {
            log("purchase of \"%s\" x%d for %d not recorded: item not seen on an auction page this session",
                    purchase.item(), purchase.count(), purchase.price());
            return;
        }
        history().add(key, new PriceHistory.Sample(System.currentTimeMillis(), purchase.price() / (double) purchase.count(), "buy"));
        historyChanged();
        if (store != null) {
            store.saveIfDirty();
        }
        log("purchase recorded: %s x%d for %d (%s)", purchase.item(), purchase.count(), purchase.price(), key);
    }
}
