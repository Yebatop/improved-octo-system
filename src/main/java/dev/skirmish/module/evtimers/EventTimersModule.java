package dev.skirmish.module.evtimers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skirmish.holyworld.HolyWorld;
import dev.skirmish.hud.Hud;
import dev.skirmish.hud.SidebarBounds;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.ModuleManager;
import dev.skirmish.module.events.EventsModule;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.ui.Theme;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * «Event Timers»: timers for HolyWorld's standing events, from what the client already sees.
 * <ul>
 *   <li>Sun Core (PvP arena, /warp pvp): the respawn anchor is found nearby, its 12-block coin/XP zone is drawn on
 *   the floor, and loot appearing on it is timed (drops come every 20–60 s).</li>
 *   <li>Castle (x 0, z 0): shulkers loaded around it counted by rarity, and for the one under the crosshair how many
 *   of your breaks it still needs (grey 5, light blue 7, purple 12).</li>
 *   <li>Pandora Box (Prime): each chest that appears during the event gets its 10 s countdown above it.</li>
 *   <li>Auto-mine: time to the refill, from the sidebar line at the mines or learned from refills seen as a burst of
 *   block updates (10 min by default).</li>
 * </ul>
 * Read-only: block updates, entities, the sidebar and your own finished block breaks; nothing is sent. Feature
 * Control id {@code event_timers}.
 */
public final class EventTimersModule extends Module {
    public static final String ID = "event_timers";
    static final String L = "layout.evtimers.";
    private static volatile @Nullable EventTimersModule instance;

    public enum PandoraMode {
        AUTO, ALWAYS, OFF
    }

    final BoolSetting sunCore = add(new BoolSetting("sun_core", true));
    final BoolSetting sunRing = add(new BoolSetting("sun_ring", true));
    final BoolSetting castle = add(new BoolSetting("castle", true));
    final EnumSetting<PandoraMode> pandora = add(new EnumSetting<>("pandora", PandoraMode.AUTO));
    final BoolSetting mine = add(new BoolSetting("mine", true));

    // Sun Core
    private @Nullable BlockPos core;
    private long coreFoundAt;
    private final Set<Integer> seenItems = new HashSet<>();
    final DropClock drops = new DropClock();

    // Castle
    private final Map<BlockPos, CastleShulkers.Rarity> shulkers = new HashMap<>();
    private final Map<BlockPos, Integer> hits = new HashMap<>();
    private final Map<BlockPos, Integer> missing = new HashMap<>();
    private @Nullable BlockPos aimedShulker;
    private boolean nearCastle;

    // Pandora
    private final Map<BlockPos, Long> chests = new LinkedHashMap<>();

    // Auto-mine
    final MineClock mineClock = new MineClock();
    private MineClock.Burst burst = newBurst();
    private @Nullable BlockPos mineCentre;
    private long lastRefill = -1;
    private long mineLineAt = -1;

    private @Nullable ClientLevel lastLevel;
    private int ticks;

    public EventTimersModule() {
        super(ID, true);
        sunRing.under(sunCore).visibleWhen(sunCore::get);
    }

    public static @Nullable EventTimersModule instance() {
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
        Hud.get().register(new EventTimersHud(this));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("skirmish", "event_timers"),
                new PandoraLabels(this));
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            if (isEnabled() && sunCore.get() && sunRing.get() && core != null) {
                renderRing(context, core);
            }
        });
        ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> {
            if (isEnabled() && castle.get() && state.getBlock() instanceof ShulkerBoxBlock && shulkers.containsKey(pos)) {
                int n = hits.merge(pos.immutable(), 1, Integer::sum);
                log("castle shulker at %d %d %d broken by you: %d time(s)", pos.getX(), pos.getY(), pos.getZ(), n);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        core = null;
        seenItems.clear();
        drops.reset();
        shulkers.clear();
        hits.clear();
        missing.clear();
        aimedShulker = null;
        nearCastle = false;
        chests.clear();
        mineClock.reset();
        burst = newBurst();
        mineCentre = null;
        lastRefill = -1;
        mineLineAt = -1;
    }

    private static MineClock.Burst newBurst() {
        Theme t = Theme.get();
        return new MineClock.Burst(t.integer(L + "mine_burst_min"), 0L, t.integer(L + "mine_burst_xz"), t.integer(L + "mine_burst_y"));
    }

    // ---- state for the HUD ----

    @Nullable BlockPos core() {
        return core;
    }

    Map<BlockPos, CastleShulkers.Rarity> shulkers() {
        return shulkers;
    }

    boolean nearCastle() {
        return nearCastle;
    }

    @Nullable BlockPos aimedShulker() {
        return aimedShulker;
    }

    int hits(BlockPos pos) {
        return hits.getOrDefault(pos, 0);
    }

    Map<BlockPos, Long> chests() {
        return chests;
    }

    /** The mine countdown is worth showing: known, and the mine or its sidebar line was seen in the last minutes. */
    boolean mineRelevant(long now) {
        if (!mine.get() || !mineClock.known()) {
            return false;
        }
        long keep = Math.round(Theme.get().num(L + "mine_keep_ms"));
        Minecraft mc = Minecraft.getInstance();
        boolean near = mineCentre != null && mc.player != null
                && mc.player.blockPosition().distSqr(mineCentre) < Math.pow(Theme.get().num(L + "mine_near"), 2);
        return near || now - mineLineAt < keep || now - lastRefill < keep;
    }

    // ---- events ----

    /** Every server block update (client thread), from the mixin; {@code old} is the state it replaces. */
    public void onBlockUpdate(BlockPos pos, BlockState old, BlockState next) {
        if (old == next) {
            return;
        }
        long now = Util.getMillis();
        if (mine.get()) {
            burst.add(now, pos.getX(), pos.getY(), pos.getZ());
        }
        if (next.getBlock() instanceof ChestBlock && !(old.getBlock() instanceof ChestBlock) && pandoraActive()) {
            chests.put(pos.immutable(), now);
            log("pandora chest appeared at %d %d %d", pos.getX(), pos.getY(), pos.getZ());
        }
    }

    boolean pandoraActive() {
        return switch (pandora.get()) {
            case OFF -> false;
            case ALWAYS -> true;
            case AUTO -> ModuleManager.get().byId(EventsModule.ID) instanceof EventsModule events
                    && events.primeEventRunning("pandora_box");
        };
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            core = null;
            seenItems.clear();
            shulkers.clear();
            chests.clear();
            burst = newBurst();
        }
        long now = Util.getMillis();
        ticks++;
        if (mine.get()) {
            tickMine(mc, now);
        }
        if (sunCore.get()) {
            tickCore(mc, now);
        } else {
            core = null;
        }
        if (castle.get()) {
            tickCastle(mc);
        } else {
            shulkers.clear();
            aimedShulker = null;
        }
        tickPandora(mc, now);
    }

    private void tickMine(Minecraft mc, long now) {
        int[] refill = burst.poll(now);
        if (refill != null && (lastRefill < 0 || now - lastRefill > 30_000L)) {
            mineClock.onRefill(now);
            lastRefill = now;
            mineCentre = new BlockPos(refill[0], refill[1], refill[2]);
            log("auto-mine refill seen at %d %d %d, period %ds%s", refill[0], refill[1], refill[2],
                    mineClock.periodMs() / 1000, mineClock.learnedPeriod() ? " (learned)" : " (default)");
        }
        if (ticks % 10 == 0) {
            SidebarBounds.Sidebar sidebar = SidebarBounds.read(mc);
            if (sidebar != null) {
                for (SidebarBounds.Row row : sidebar.rows()) {
                    String line = row.name().getString() + " " + row.score().getString();
                    int seconds = MineClock.parseSidebar(line);
                    if (seconds >= 0) {
                        if (mineLineAt < 0 || now - mineLineAt > 60_000L) {
                            log("auto-mine sidebar line \"%s\" -> %ds", line.strip(), seconds);
                        }
                        mineClock.onSidebar(now, seconds);
                        mineLineAt = now;
                        break;
                    }
                }
            }
        }
    }

    private void tickCore(Minecraft mc, long now) {
        if (ticks % 20 == 0) {
            BlockPos found = mc.level.dimension() == Level.NETHER ? null
                    : findAnchor(mc.level, mc.player.blockPosition(), Theme.get().integer(L + "core_search"));
            if (found != null && !found.equals(core)) {
                log("sun core (respawn anchor) at %d %d %d", found.getX(), found.getY(), found.getZ());
                coreFoundAt = now;
                seenItems.clear();
            }
            core = found;
        }
        if (core == null) {
            return;
        }
        Theme t = Theme.get();
        double r = t.num(L + "core_drop_radius");
        AABB box = new AABB(core).inflate(r, 0, r).expandTowards(0, t.num(L + "core_drop_height"), 0);
        for (ItemEntity item : mc.level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (seenItems.add(item.getId()) && now - coreFoundAt > t.num(L + "core_prime_ms")) {
                long since = drops.sinceMs(now);
                if (drops.onItem(now)) {
                    log("sun core drop #%d: %s (%s since the previous)", drops.drops(),
                            item.getItem().getHoverName().getString(), since < 0 ? "first" : since / 1000 + "s");
                }
            }
        }
        if (seenItems.size() > 512) {
            seenItems.clear();
        }
    }

    /** Nearest respawn anchor within {@code r} blocks (only chunk sections that may hold one are searched). */
    static @Nullable BlockPos findAnchor(ClientLevel level, BlockPos around, int r) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int cx = (around.getX() - r) >> 4; cx <= (around.getX() + r) >> 4; cx++) {
            for (int cz = (around.getZ() - r) >> 4; cz <= (around.getZ() + r) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
                    if (baseY + 15 < around.getY() - r || baseY > around.getY() + r) {
                        continue;
                    }
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(s -> s.is(Blocks.RESPAWN_ANCHOR))) {
                        continue;
                    }
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (section.getBlockState(x, y, z).is(Blocks.RESPAWN_ANCHOR)) {
                                    BlockPos pos = new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z);
                                    double d = pos.distSqr(around);
                                    if (d <= (double) r * r && d < bestD) {
                                        bestD = d;
                                        best = pos;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private void tickCastle(Minecraft mc) {
        Theme t = Theme.get();
        nearCastle = mc.level.dimension() == Level.OVERWORLD
                && CastleShulkers.nearCastle(mc.player.getX(), mc.player.getZ(), t.num(L + "castle_near"))
                && (HolyWorld.isConnected() || Boolean.getBoolean("skirmish.holyworld"));
        if (!nearCastle) {
            shulkers.clear();
            aimedShulker = null;
            return;
        }
        if (ticks % 20 == 0) {
            scanShulkers(mc.level, (int) t.num(L + "castle_radius"));
        }
        HitResult hit = mc.hitResult;
        aimedShulker = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && shulkers.containsKey(block.getBlockPos()) ? block.getBlockPos().immutable() : null;
    }

    private void scanShulkers(ClientLevel level, int radius) {
        Map<BlockPos, CastleShulkers.Rarity> found = new HashMap<>();
        for (int cx = -radius >> 4; cx <= radius >> 4; cx++) {
            for (int cz = -radius >> 4; cz <= radius >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof ShulkerBoxBlockEntity && be.getBlockState().getBlock() instanceof ShulkerBoxBlock box) {
                        DyeColor color = box.getColor();
                        CastleShulkers.Rarity rarity = CastleShulkers.rarity(color == null ? null : color.getName());
                        if (rarity != null && CastleShulkers.nearCastle(be.getBlockPos().getX(), be.getBlockPos().getZ(), radius)) {
                            found.put(be.getBlockPos().immutable(), rarity);
                        }
                    }
                }
            }
        }
        if (!found.keySet().equals(shulkers.keySet())) {
            log("castle shulkers: %s", count(found));
        }
        // A box gone in two scans in a row was opened for good: forget your breaks on it.
        for (BlockPos pos : Set.copyOf(hits.keySet())) {
            if (found.containsKey(pos)) {
                missing.remove(pos);
            } else if (level.isLoaded(pos) && missing.merge(pos, 1, Integer::sum) >= 2) {
                hits.remove(pos);
                missing.remove(pos);
            }
        }
        shulkers.clear();
        shulkers.putAll(found);
    }

    static Map<CastleShulkers.Rarity, Integer> count(Map<BlockPos, CastleShulkers.Rarity> boxes) {
        Map<CastleShulkers.Rarity, Integer> out = new EnumMap<>(CastleShulkers.Rarity.class);
        for (CastleShulkers.Rarity r : CastleShulkers.Rarity.values()) {
            out.put(r, 0);
        }
        for (CastleShulkers.Rarity r : boxes.values()) {
            out.merge(r, 1, Integer::sum);
        }
        return out;
    }

    private void tickPandora(Minecraft mc, long now) {
        if (chests.isEmpty()) {
            return;
        }
        long life = PandoraLabels.LIFE_MS + Math.round(Theme.get().num(L + "pandora_grace_ms"));
        chests.entrySet().removeIf(e -> {
            boolean expired = now - e.getValue() > life;
            boolean gone = now - e.getValue() > 300 && mc.level.isLoaded(e.getKey())
                    && !(mc.level.getBlockState(e.getKey()).getBlock() instanceof ChestBlock);
            if (gone && !expired) {
                log("pandora chest at %d %d %d gone after %.1fs", e.getKey().getX(), e.getKey().getY(), e.getKey().getZ(),
                        (now - e.getValue()) / 1000.0);
            }
            return expired || gone;
        });
    }

    // ---- Sun Core ring ----

    private void renderRing(WorldRenderContext context, BlockPos at) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Theme t = Theme.get();
        Vec3 cam = context.worldState().cameraRenderState.pos;
        double cx = at.getX() + 0.5;
        double cz = at.getZ() + 0.5;
        float radius = t.num(L + "core_zone");
        boolean inside = inZone(mc.player.position(), at, radius);
        int tone = t.color(inside ? "good" : "ev_sun");
        int line = withAlpha(tone, t.integer(L + "ring_alpha"));
        int fill = withAlpha(tone, t.integer(L + "ring_fill_alpha"));
        int clear = withAlpha(tone, 0);
        float width = t.num(L + "ring_width");
        int n = t.integer(L + "ring_segments");
        PoseStack pose = context.matrices();
        pose.pushPose();
        pose.translate(cx - cam.x, at.getY() + t.num(L + "ring_lift") - cam.y, cz - cam.z);
        context.commandQueue().submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, c) -> {
            for (int i = 0; i < n; i++) {
                float a0 = (float) (Math.PI * 2 * i / n);
                float a1 = (float) (Math.PI * 2 * (i + 1) / n);
                float inner = radius * t.num(L + "ring_fill_inner");
                c.addVertex(p, inner * (float) Math.cos(a0), 0f, inner * (float) Math.sin(a0)).setColor(clear);
                c.addVertex(p, radius * (float) Math.cos(a0), 0f, radius * (float) Math.sin(a0)).setColor(fill);
                c.addVertex(p, radius * (float) Math.cos(a1), 0f, radius * (float) Math.sin(a1)).setColor(fill);
                c.addVertex(p, inner * (float) Math.cos(a1), 0f, inner * (float) Math.sin(a1)).setColor(clear);
            }
        });
        context.commandQueue().submitCustomGeometry(pose, RenderTypes.lines(), (p, c) -> {
            for (int i = 0; i < n; i++) {
                float a0 = (float) (Math.PI * 2 * i / n);
                float a1 = (float) (Math.PI * 2 * (i + 1) / n);
                seg(c, p, radius * (float) Math.cos(a0), radius * (float) Math.sin(a0),
                        radius * (float) Math.cos(a1), radius * (float) Math.sin(a1), line, width);
            }
        });
        pose.popPose();
    }

    /** Within the core's coin/XP radius (from the block's centre). */
    static boolean inZone(Vec3 player, BlockPos core, double radius) {
        double dx = player.x - (core.getX() + 0.5);
        double dy = player.y - (core.getY() + 0.5);
        double dz = player.z - (core.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private static void seg(VertexConsumer c, PoseStack.Pose p, float xa, float za, float xb, float zb, int color, float width) {
        float dx = xb - xa;
        float dz = zb - za;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4f) {
            return;
        }
        c.addVertex(p, xa, 0f, za).setColor(color).setNormal(p, dx / len, 0f, dz / len).setLineWidth(width);
        c.addVertex(p, xb, 0f, zb).setColor(color).setNormal(p, dx / len, 0f, dz / len).setLineWidth(width);
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
    }
}
