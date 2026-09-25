package dev.skirmish.module.base;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skirmish.SkirmishClient;
import dev.skirmish.SkirmishKeys;
import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.friends.Friends;
import dev.skirmish.module.market.MarketModule;
import dev.skirmish.module.nametag.NametagHpModule;
import dev.skirmish.module.regions.RegionBoundsModule;
import dev.skirmish.module.regions.RegionTable;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.KeySetting;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import dev.skirmish.util.ServerContext;
import dev.skirmish.waypoint.Waypoint;
import dev.skirmish.waypoint.WaypointManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * «Base OS»: your base as an app. The base is the region whose hologram names you (or one you set by hand). Every
 * container you open there is indexed (with shulker contents), so you can search your storage, see totals with
 * market prices and get a route to the chest that has an item. It warns when stock drops below a minimum, when a
 * stranger you can see walks into the base, when a farm is ripe and when a countdown on the region's hologram runs
 * out, keeps a base log and draws a 3D model of the region. Read-only: it only reads what the game already shows
 * you (opened containers, hologram text, visible players, blocks of your own region). Feature Control id
 * {@code base_os}.
 */
public final class BaseModule extends Module {
    public static final String ID = "base_os";
    static final String L = "layout.base.";
    static final String ENDER = "ender";
    private static final String ROUTE_SOURCE = "base";
    private static volatile @Nullable BaseModule instance;

    public enum Scope {
        BASE, EVERYWHERE
    }

    /** A line for the HUD: text and a theme colour token. */
    record Alert(String text, String tone) {
    }

    /** One crop kind in the base: ripe and total now, and when it will all be ripe (-1 unknown, 0 ripe). */
    record Farm(String kind, String name, int ripe, int total, long etaMs) {
    }

    final EnumSetting<Scope> scope = add(new EnumSetting<>("scope", Scope.BASE));
    final BoolSetting intruders = add(new BoolSetting("intruders", true));
    final BoolSetting sound = add(new BoolSetting("sound", true));
    final BoolSetting stock = add(new BoolSetting("stock", true));
    final BoolSetting farms = add(new BoolSetting("farms", true));
    final BoolSetting hud = add(new BoolSetting("hud", true));
    final KeySetting openKey = add(new KeySetting("open_key", "key.skirmish.base_os.open"));

    private final BaseStore store = new BaseStore(SkirmishClient.configDir().resolve("base.json"));
    private final FarmScanner scanner = new FarmScanner();

    private @Nullable BlockPos usedPos;
    private String usedBlock = "";
    private String usedDim = "";
    private long usedAt;
    private @Nullable AbstractContainerScreen<?> tracked;
    private @Nullable BlockPos trackedPos;
    private String trackedBlock = "";
    private String trackedDim = "";

    private final Map<String, Long> intruderSeen = new LinkedHashMap<>();
    private final Map<String, Long> intruderLogged = new HashMap<>();
    private final Map<String, ArrayDeque<FarmMath.Sample>> farmSamples = new HashMap<>();
    private List<Farm> farmsNow = List.of();
    private Set<String> lowKeys = Set.of();
    private List<StorageIndex.Low> lowNow = List.of();
    private List<StorageIndex.Total> totalsNow = List.of();
    private double valueNow;
    private int pricedKinds;

    private BaseData.@Nullable Chest highlight;
    private long highlightUntil;
    private int ticks;
    private long lastSave;

    public BaseModule() {
        super(ID, true);
    }

    static @Nullable BaseModule instance() {
        return instance;
    }

    @Override
    public Category category() {
        return Category.WORLD;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        instance = this;
        store.load();
        Hud.get().register(new BaseHud(this));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClientSide() && isEnabled()) {
                usedPos = hit.getBlockPos().immutable();
                usedBlock = blockId(world, usedPos);
                usedDim = ServerContext.dimension();
                usedAt = System.currentTimeMillis();
            }
            return InteractionResult.PASS;
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!isEnabled() || !(screen instanceof AbstractContainerScreen<?> container)) {
                return;
            }
            if (tracked != container && !startTracking(container)) {
                return;
            }
            // Per-screen events are recreated on every init/resize, so the close hook is registered again here.
            ScreenEvents.remove(container).register(removed -> {
                if (removed == tracked) {
                    snapshot(container);
                    tracked = null;
                }
            });
        });
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            if (isEnabled() && highlight != null) {
                renderHighlight(context);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            store.saveIfDirty();
            intruderSeen.clear();
            farmSamples.clear();
            farmsNow = List.of();
            scanner.reset();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> store.saveIfDirty());
    }

    // ---- data access for the HUD and the screen ----

    BaseData.Server server() {
        return store.data().server(ServerContext.serverKey());
    }

    BaseData.@Nullable Region region() {
        return server().region;
    }

    List<StorageIndex.Total> totals() {
        return totalsNow;
    }

    List<StorageIndex.Low> low() {
        return lowNow;
    }

    double value() {
        return valueNow;
    }

    int pricedKinds() {
        return pricedKinds;
    }

    List<Farm> farms() {
        return farmsNow;
    }

    Map<String, Long> intrudersSeen() {
        return intruderSeen;
    }

    /** Chests counted by the index now: the base's (or all, by the setting) and the ender chest. */
    List<BaseData.Chest> indexedChests() {
        BaseData.Server srv = server();
        List<BaseData.Chest> out = new ArrayList<>();
        for (BaseData.Chest c : srv.chests.values()) {
            if (ENDER.equals(c.dim) || scope.get() == Scope.EVERYWHERE || srv.region != null && srv.region.contains(c.dim, c.x, c.y, c.z)) {
                out.add(c);
            }
        }
        return out;
    }

    boolean insideBase() {
        LocalPlayer p = Minecraft.getInstance().player;
        BaseData.Region r = region();
        return p != null && r != null && r.contains(ServerContext.dimension(), p.getX(), p.getY(), p.getZ());
    }

    /** HUD lines, most urgent first. */
    List<Alert> alerts() {
        long now = System.currentTimeMillis();
        List<Alert> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : intruderSeen.entrySet()) {
            if (now - e.getValue() < Theme.get().num(L + "intruder_show_ms")) {
                out.add(new Alert(Ui.tr("skirmish.base.alert.intruder", e.getKey()), "bad"));
            }
        }
        if (stock.get()) {
            for (StorageIndex.Low low : lowNow) {
                out.add(new Alert(Ui.tr("skirmish.base.alert.low", low.name(), low.have(), low.min()), "warn"));
            }
        }
        for (BaseData.Timer t : server().timers) {
            long left = t.endsAt - now;
            if (left > 0 && left < Theme.get().num(L + "timer_warn_ms")) {
                out.add(new Alert(Ui.tr("skirmish.base.alert.timer", t.label, left(left)), "warn"));
            }
        }
        if (farms.get()) {
            for (Farm f : farmsNow) {
                if (f.total() > 0 && f.ripe() >= f.total()) {
                    out.add(new Alert(Ui.tr("skirmish.base.alert.farm", f.name(), f.total()), "good"));
                }
            }
        }
        return out;
    }

    // ---- actions from the screen ----

    /** Makes the region around me the base (for a clan base or a hologram without my nick). */
    void setBaseHere() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return;
        }
        int half = (Theme.get().integer(L + "manual_size") - 1) / 2;
        BaseData.Region r = new BaseData.Region();
        r.dim = ServerContext.dimension();
        r.x = p.getBlockX();
        r.y = p.getBlockY();
        r.z = p.getBlockZ();
        r.x0 = r.x - half;
        r.x1 = r.x + half;
        r.z0 = r.z - half;
        r.z1 = r.z + half;
        window(r, p.level());
        r.type = "manual";
        r.manual = true;
        r.column = true;
        r.found = System.currentTimeMillis();
        server().region = r;
        scanner.reset();
        addLog("base", Ui.tr("skirmish.base.log.manual", r.x, r.y, r.z));
        store.markDirty();
    }

    void forgetBase() {
        server().region = null;
        server().timers.clear();
        scanner.reset();
        farmsNow = List.of();
        store.markDirty();
    }

    /** Toggles a stock minimum: on at the current total (warn when it drops), off when set. */
    void toggleMinimum(StorageIndex.Total total) {
        BaseData.Server srv = server();
        if (srv.minimums.remove(total.key()) == null) {
            srv.minimums.put(total.key(), (int) Math.max(1, Math.min(Integer.MAX_VALUE, total.count())));
            srv.names.put(total.key(), total.name());
        } else {
            srv.names.remove(total.key());
        }
        store.markDirty();
        refreshStock(false);
    }

    /** Selects a waypoint at the chest holding most of an item and outlines that chest for a while. */
    void routeTo(StorageIndex.Total total) {
        BaseData.Server srv = server();
        for (StorageIndex.Where where : total.where()) {
            BaseData.Chest chest = srv.chests.get(where.chest());
            if (chest == null || ENDER.equals(chest.dim)) {
                continue;
            }
            WaypointManager wm = WaypointManager.get();
            for (Waypoint w : List.copyOf(wm.currentServer())) {
                if (ROUTE_SOURCE.equals(w.source())) {
                    wm.remove(w.id());
                }
            }
            Waypoint wp = wm.add(Ui.tr("skirmish.base.route_name", total.name()), chest.x + 0.5, chest.y + 1, chest.z + 0.5, chest.dim, ROUTE_SOURCE);
            wm.select(wp.id());
            highlight = chest;
            highlightUntil = System.currentTimeMillis() + (long) Theme.get().num(L + "highlight_ms");
            return;
        }
    }

    void forgetChest(String key) {
        server().chests.remove(key);
        store.markDirty();
        refreshStock(false);
    }

    private void addLog(String kind, String text) {
        List<BaseData.LogEntry> log = server().log;
        log.add(new BaseData.LogEntry(System.currentTimeMillis(), kind, text));
        int max = Theme.get().integer(L + "log_max");
        while (log.size() > max) {
            log.removeFirst();
        }
        store.markDirty();
        log("log %s: %s", kind, text);
    }

    // ---- ticking ----

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        ticks++;
        while (SkirmishKeys.BASE_OS.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new BaseScreen(null));
            }
        }
        String dim = ServerContext.dimension();
        long now = System.currentTimeMillis();
        if (ticks % 40 == 0) {
            findByHologram(mc, player, dim);
        }
        BaseData.Region region = region();
        if (region != null && region.dim.equals(dim)) {
            if (intruders.get() && ticks % 10 == 0) {
                checkIntruders(mc, player, region, now);
            }
            if (farms.get() && scanner.step(mc.level, region, Theme.get().integer(L + "scan_per_tick"))) {
                farmPass(now);
            }
            if (ticks % 40 == 0) {
                readTimers(mc.level, region, now);
            }
            if (ticks % 100 == 0) {
                dropBrokenChests(mc.level, dim);
            }
        }
        if (ticks % 20 == 0) {
            refreshStock(true);
        }
        if (highlight != null && now > highlightUntil) {
            highlight = null;
        }
        if (now - lastSave > 30_000) {
            store.saveIfDirty();
            lastSave = now;
        }
    }

    /** The base is the region under a hologram that names me. */
    private void findByHologram(Minecraft mc, LocalPlayer player, String dim) {
        String nick = player.getGameProfile().name();
        AABB box = player.getBoundingBox().inflate(Theme.get().num(L + "holo_scan"));
        for (Entity e : mc.level.getEntities((Entity) null, box, RegionBoundsModule::isHologram)) {
            String text = e instanceof Display.TextDisplay t ? t.getText().getString()
                    : e.getCustomName() == null ? "" : e.getCustomName().getString();
            if (!BaseText.mentions(text, nick)) {
                continue;
            }
            BlockPos block = regionBlockBelow(mc.level, e.blockPosition());
            if (block == null) {
                continue;
            }
            RegionTable.Type type = RegionBoundsModule.typeFor(blockId(mc.level, block));
            if (type != null) {
                adopt(mc.level, dim, block, type);
                return;
            }
        }
    }

    private static @Nullable BlockPos regionBlockBelow(Level level, BlockPos from) {
        for (int dy = 0; dy >= -5; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = from.offset(dx, dy, dz);
                    if (RegionTable.anyServer(blockId(level, p))) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    private void adopt(Level level, String dim, BlockPos block, RegionTable.Type type) {
        BaseData.Server srv = server();
        BaseData.Region old = srv.region;
        String holo = RegionBoundsModule.hologramText(level, block);
        if (old != null && !old.manual && old.dim.equals(dim) && old.x == block.getX() && old.y == block.getY() && old.z == block.getZ()) {
            if (holo != null && !holo.equals(old.holo)) {
                old.holo = holo;
                store.markDirty();
            }
            return;
        }
        if (old != null && old.manual) {
            // A base set by hand stays until it is cleared in the screen.
            return;
        }
        int[] b = RegionTable.bounds(type, block.getX(), block.getY(), block.getZ());
        BaseData.Region r = new BaseData.Region();
        r.dim = dim;
        r.x = block.getX();
        r.y = block.getY();
        r.z = block.getZ();
        r.x0 = b[0];
        r.z0 = b[2];
        r.x1 = b[3] - 1;
        r.z1 = b[5] - 1;
        r.column = !type.cube();
        if (type.cube()) {
            r.y0 = b[1];
            r.y1 = b[4] - 1;
        } else {
            window(r, level);
        }
        r.type = type.id();
        r.holo = holo == null ? "" : holo;
        r.found = System.currentTimeMillis();
        srv.region = r;
        scanner.reset();
        farmSamples.clear();
        addLog("base", Ui.tr("skirmish.base.log.found", regionName(r), RegionTable.sizeText(type), r.x, r.y, r.z));
        Minecraft.getInstance().player.displayClientMessage(Component.translatable("skirmish.base.found_toast"), true);
    }

    /** The height window a column region shows in the model and scans for farms. */
    private static void window(BaseData.Region r, Level level) {
        r.y0 = Math.max(level.getMinY(), r.y - Theme.get().integer(L + "column_below"));
        r.y1 = Math.min(level.getMaxY(), r.y + Theme.get().integer(L + "column_above"));
    }

    /** "2д 3ч" for long countdowns, a clock under an hour. */
    static String left(long ms) {
        String words = BaseText.span(ms, new String[]{Ui.tr("skirmish.base.unit.d"), Ui.tr("skirmish.base.unit.h"), Ui.tr("skirmish.base.unit.m")});
        return words != null ? words : Ui.duration(ms);
    }

    /** "Бочка 48 −60 45", "Эндер-сундук". */
    static String chestName(BaseData.Chest chest) {
        if (ENDER.equals(chest.dim)) {
            return Ui.tr("skirmish.base.ender");
        }
        String name;
        try {
            name = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse(chest.block)).getName().getString();
        } catch (RuntimeException e) {
            name = Ui.tr("skirmish.base.container");
        }
        return Ui.tr("skirmish.base.chest_at", name, chest.x, chest.y, chest.z);
    }

    static String regionName(BaseData.Region r) {
        String key = r.manual ? "skirmish.base.region.manual" : "skirmish.regions.type." + r.type;
        String text = Ui.tr(key);
        return text.equals(key) ? r.type : text;
    }

    /** Strangers I can see inside the base: not me, not a friend, never an invisible player. */
    private void checkIntruders(Minecraft mc, LocalPlayer me, BaseData.Region region, long now) {
        Vec3 eye = me.getEyePosition();
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == me || p.isInvisible() || p.isSpectator() || Friends.isFriend(p)) {
                continue;
            }
            if (!region.contains(region.dim, p.getX(), p.getY(), p.getZ()) || !NametagHpModule.inPlainSight(mc, eye, p)) {
                continue;
            }
            String name = p.getGameProfile().name();
            intruderSeen.put(name, now);
            Long logged = intruderLogged.get(name);
            if (logged == null || now - logged > Theme.get().num(L + "intruder_repeat_ms")) {
                intruderLogged.put(name, now);
                addLog("intruder", Ui.tr("skirmish.base.log.intruder", name));
                if (sound.get()) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.4f, 0.9f));
                }
            }
        }
        intruderSeen.values().removeIf(t -> now - t > Theme.get().num(L + "intruder_show_ms"));
    }

    /** Countdowns on the region's hologram («Налог через 2д 3ч»), kept counting while it is out of sight. */
    private void readTimers(Level level, BaseData.Region region, long now) {
        BlockPos block = new BlockPos(region.x, region.y, region.z);
        if (region.manual || !level.isLoaded(block)) {
            return;
        }
        String holo = RegionBoundsModule.hologramText(level, block);
        if (holo == null) {
            return;
        }
        List<BaseData.Timer> found = new ArrayList<>();
        for (String line : holo.split(" \\| ")) {
            DurationText.Found d = DurationText.find(line);
            if (d != null) {
                String label = d.label().isEmpty() ? Ui.tr("skirmish.base.timer") : d.label();
                found.add(new BaseData.Timer(label, now + d.millis(), now));
            }
        }
        BaseData.Server srv = server();
        if (!found.isEmpty() || !srv.timers.isEmpty()) {
            srv.timers = found;
            store.markDirty();
        }
    }

    private void farmPass(long now) {
        List<Farm> out = new ArrayList<>();
        long window = (long) Theme.get().num(L + "farm_window_ms");
        Set<String> kinds = new HashSet<>(scanner.last().keySet());
        for (Map.Entry<String, int[]> e : scanner.last().entrySet()) {
            String kind = e.getKey();
            int ripe = e.getValue()[0];
            int total = e.getValue()[1];
            ArrayDeque<FarmMath.Sample> samples = farmSamples.computeIfAbsent(kind, k -> new ArrayDeque<>());
            FarmMath.Sample prev = samples.peekLast();
            samples.add(new FarmMath.Sample(now, ripe, total));
            while (samples.size() > 2 && now - samples.getFirst().at() > window) {
                samples.removeFirst();
            }
            String name = FarmScanner.name(kind);
            if (prev != null && total >= 4 && ripe >= total && prev.ripe() < prev.total()) {
                addLog("farm", Ui.tr("skirmish.base.log.farm", name, total));
            }
            out.add(new Farm(kind, name, ripe, total, FarmMath.etaMs(List.copyOf(samples), window)));
        }
        farmSamples.keySet().retainAll(kinds);
        out.sort((a, b) -> Integer.compare(b.total(), a.total()));
        farmsNow = out;
    }

    private void refreshStock(boolean logNew) {
        BaseData.Server srv = server();
        List<StorageIndex.Total> totals = StorageIndex.totals(indexedChests());
        totalsNow = totals;
        double value = 0;
        int priced = 0;
        for (StorageIndex.Total t : totals) {
            var price = MarketModule.usualPrice(t.key());
            if (price.isPresent()) {
                value += price.getAsDouble() * t.count();
                priced++;
            }
        }
        valueNow = value;
        pricedKinds = priced;
        List<StorageIndex.Low> low = StorageIndex.low(totals, srv.minimums, srv.names);
        Set<String> keys = new HashSet<>();
        for (StorageIndex.Low l : low) {
            keys.add(l.key());
            if (logNew && stock.get() && !lowKeys.contains(l.key())) {
                addLog("stock", Ui.tr("skirmish.base.log.low", l.name(), l.have(), l.min()));
            }
        }
        lowKeys = keys;
        lowNow = low;
    }

    private void dropBrokenChests(Level level, String dim) {
        boolean changed = server().chests.values().removeIf(c -> c.dim.equals(dim)
                && level.isLoaded(new BlockPos(c.x, c.y, c.z)) && !BaseText.container(blockId(level, new BlockPos(c.x, c.y, c.z))));
        if (changed) {
            store.markDirty();
        }
    }

    // ---- containers ----

    private boolean startTracking(AbstractContainerScreen<?> screen) {
        BlockPos pos = usedPos;
        if (pos == null || System.currentTimeMillis() - usedAt > 2500 || !BaseText.container(usedBlock)) {
            return false;
        }
        AbstractContainerMenu menu = screen.getMenu();
        boolean storage = menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof DispenserMenu || menu instanceof HopperMenu;
        if (!storage || BaseText.customGlyphs(screen.getTitle().getString())) {
            return false;
        }
        tracked = screen;
        trackedPos = pos;
        trackedBlock = usedBlock;
        trackedDim = usedDim;
        usedPos = null;
        return true;
    }

    private void snapshot(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = trackedPos;
        if (mc.level == null || pos == null) {
            return;
        }
        boolean ender = trackedBlock.endsWith(":ender_chest");
        BlockPos at = ender ? BlockPos.ZERO : canonical(mc.level, pos);
        String dim = ender ? ENDER : trackedDim;
        BaseData.Server srv = server();
        if (!ender && scope.get() == Scope.BASE && (srv.region == null || !srv.region.contains(dim, at.getX(), at.getY(), at.getZ()))) {
            return;
        }
        Map<String, BaseData.Stack> merged = new LinkedHashMap<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory) {
                continue;
            }
            addStack(merged, slot.getItem());
        }
        BaseData.Chest chest = new BaseData.Chest();
        chest.dim = dim;
        chest.x = at.getX();
        chest.y = at.getY();
        chest.z = at.getZ();
        chest.block = trackedBlock;
        chest.seen = System.currentTimeMillis();
        chest.items = new ArrayList<>(merged.values());
        BaseData.Chest old = srv.chests.put(chest.key(), chest);
        String where = chestName(chest);
        if (old == null) {
            addLog("chest", Ui.tr("skirmish.base.log.new_chest", where, chest.items.size()));
        } else {
            List<StorageIndex.Change> changes = StorageIndex.diff(old.items, chest.items);
            if (!changes.isEmpty()) {
                addLog("chest", where + ": " + StorageIndex.changeText(changes, 3));
            }
        }
        store.markDirty();
        refreshStock(true);
    }

    /** Adds a stack, and what is inside it when it is a shulker box or bundle. */
    private static void addStack(Map<String, BaseData.Stack> merged, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        String key = MarketModule.itemKey(stack);
        merged.computeIfAbsent(key, k -> new BaseData.Stack(k, stack.getItemHolder().getRegisteredName(), stack.getHoverName().getString(), 0))
                .count += stack.getCount();
        ItemContainerContents inside = stack.get(DataComponents.CONTAINER);
        if (inside != null) {
            for (ItemStack inner : inside.nonEmptyItems()) {
                ItemStack copy = inner.copyWithCount(inner.getCount() * stack.getCount());
                addStack(merged, copy);
            }
        }
    }

    /** One key per double chest: the smaller of its two halves. */
    private static BlockPos canonical(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            if (other.getX() < pos.getX() || other.getX() == pos.getX() && other.getZ() < pos.getZ()) {
                return other;
            }
        }
        return pos;
    }

    static String blockId(Level level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
    }

    // ---- chest outline ----

    private void renderHighlight(WorldRenderContext context) {
        BaseData.Chest c = highlight;
        if (c == null || !c.dim.equals(ServerContext.dimension())) {
            return;
        }
        Vec3 cam = context.worldState().cameraRenderState.pos;
        float x0 = (float) (c.x - cam.x) - 0.02f;
        float y0 = (float) (c.y - cam.y) - 0.02f;
        float z0 = (float) (c.z - cam.z) - 0.02f;
        float s = 1.04f;
        int color = Theme.get().color("accent");
        float width = Theme.get().num(L + "outline_width");
        PoseStack pose = context.matrices();
        context.commandQueue().submitCustomGeometry(pose, RenderTypes.lines(), (p, v) -> {
            float[][] corners = {{0, 0, 0}, {s, 0, 0}, {s, 0, s}, {0, 0, s}};
            for (int i = 0; i < 4; i++) {
                float[] a = corners[i];
                float[] b = corners[(i + 1) % 4];
                seg(v, p, x0 + a[0], y0, z0 + a[2], x0 + b[0], y0, z0 + b[2], color, width);
                seg(v, p, x0 + a[0], y0 + s, z0 + a[2], x0 + b[0], y0 + s, z0 + b[2], color, width);
                seg(v, p, x0 + a[0], y0, z0 + a[2], x0 + a[0], y0 + s, z0 + a[2], color, width);
            }
        });
    }

    private static void seg(VertexConsumer c, PoseStack.Pose p, float xa, float ya, float za, float xb, float yb, float zb, int color, float width) {
        float dx = xb - xa;
        float dy = yb - ya;
        float dz = zb - za;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        c.addVertex(p, xa, ya, za).setColor(color).setNormal(p, dx / len, dy / len, dz / len).setLineWidth(width);
        c.addVertex(p, xb, yb, zb).setColor(color).setNormal(p, dx / len, dy / len, dz / len).setLineWidth(width);
    }
}
