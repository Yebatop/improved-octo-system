package dev.skirmish.module.hwtimers;

import dev.skirmish.SkirmishClient;
import dev.skirmish.combat.CombatListener;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.Combatant;
import dev.skirmish.combat.Fight;
import dev.skirmish.holyworld.HolyWorld;
import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.gearinspector.holy.Talisman;
import dev.skirmish.module.hwtimers.TimerBoard.Chip;
import dev.skirmish.module.hwtimers.TimerBoard.Source;
import dev.skirmish.module.hwtimers.TimerTable.BlockSig;
import dev.skirmish.module.hwtimers.TimerTable.TimerDef;
import dev.skirmish.module.pvp.mixin.BossHealthOverlayAccessor;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

/**
 * «Item Timers»: chips for HolyWorld items acting on me (Стан and its pearl/chorus ban, Ледяная волна, Трапка, raid
 * block, rune «Бессмертие», …) with a countdown and a short note. Sources, all read-only: my own mob effects, my own
 * totem pops, system chat / titles / action bar, server block updates and explosions around me, and boss bars.
 * Only my own state and public world events; other players' cooldowns are never tracked or estimated. The table is
 * {@code assets/skirmish_hwtimers/items.json}; with the debug log on every server line and burst is logged so the
 * unconfirmed patterns can be tuned from a capture.
 */
public final class ItemTimersModule extends Module {
    public static final String ID = "hw_item_timers";
    /** Appear events farther than this from me are not even recorded. */
    private static final double APPEAR_RECORD_RADIUS = 6.0;
    private static final long RAW_LOG_REPEAT_MS = 30_000;
    private static final int RAW_LOG_MAX = 256;

    private static @Nullable ItemTimersModule instance;

    final BoolSetting chatTriggers = add(new BoolSetting("chat_triggers", true));
    final BoolSetting effectTriggers = add(new BoolSetting("effect_triggers", true));
    final BoolSetting blockTriggers = add(new BoolSetting("block_triggers", true));
    final NumberSetting raidRadius = (NumberSetting) add(new NumberSetting("raid_radius", 24, 8, 64, 1).unit(" m"))
            .under(blockTriggers).visibleWhen(blockTriggers::get);
    final BoolSetting notes = add(new BoolSetting("notes", true));
    final BoolSetting holyworldOnly = add(new BoolSetting("holyworld_only", true));

    private TimerTable table = TimerTable.bundled();
    private final TimerBoard board = new TimerBoard();
    private final BlockBursts bursts = new BlockBursts();
    /** Own effects seen last tick: id → {amplifier, duration}. */
    private final Map<String, int[]> lastEffects = new HashMap<>();
    /** Chips started by an effect: timer id → effect id (they end when the effect goes). */
    private final Map<String, String> effectChips = new HashMap<>();
    private Talisman.@Nullable Rune heldRune;
    private boolean raidNear = true;
    private int ticks;
    private final Map<String, Long> rawLogged = new LinkedHashMap<>();

    public ItemTimersModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.COMBAT;
    }

    public static @Nullable ItemTimersModule instance() {
        return instance;
    }

    @Override
    public void onInitialize() {
        instance = this;
        JsonTables.Loaded loaded = JsonTables.load(SkirmishClient.configDir(), TimerTable.OVERRIDE_NAME, TimerTable.RESOURCE);
        table = TimerTable.parse(loaded.root());
        if (loaded.problem() != null) {
            error("item timer table override ignored: " + loaded.problem(), null);
        }
        table.problems().forEach(p -> error("item timer table: " + p, null));
        Hud.get().register(new ItemTimersHud(this));
        CombatTracker.get().addListener(new CombatListener() {
            @Override
            public void onTotemPop(Combatant entity, @Nullable Fight fight) {
                if (isEnabled() && entity.self()) {
                    onOwnTotemPop();
                }
            }
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            // Overlay lines reach setOverlayMessage too, where the Gui hook reads them.
            if (!overlay) {
                onServerText("chat", message);
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    @Override
    protected void onEnable() {
        log("table: " + table.timers().size() + " timers " + table.timers().stream().map(TimerDef::id).toList());
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        board.clear();
        bursts.clear();
        lastEffects.clear();
        effectChips.clear();
        heldRune = null;
    }

    TimerTable table() {
        return table;
    }

    /** Chips to draw now; the raid-block chip only while one of its blocks is near me. */
    List<Chip> visibleChips(long now) {
        List<Chip> out = new ArrayList<>();
        for (Chip chip : board.active(now)) {
            if (chip.def().breaks() != null && !chip.positions().isEmpty() && !raidNear) {
                continue;
            }
            out.add(chip);
        }
        return out;
    }

    private boolean triggersAllowed() {
        return isEnabled() && (!holyworldOnly.get() || HolyWorld.isConnected());
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    // ---- tick ----

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        long now = now();
        if (player == null || mc.level == null) {
            return;
        }
        ticks++;
        boolean allowed = triggersAllowed();
        pollEffects(player, now, allowed);
        heldRune = heldTotemRune(player);
        if (allowed && blockTriggers.get()) {
            for (BlockBursts.Burst burst : bursts.evaluate(table.timers(), id -> board.get(id) != null && board.get(id).endMs() > now, now)) {
                boolean fresh = board.get(burst.def().id()) == null;
                Chip chip = board.start(burst.def(), now, null, Source.BLOCKS, !burst.def().confirmed(), false);
                board.track(chip, burst.positions());
                if (fresh) {
                    log("block burst → " + burst.def().id() + ": " + burst.positions().size() + " block(s)");
                }
            }
        }
        if (ticks % 5 == 0) {
            endGoneChips(mc.level, now);
            raidNear = raidNear(player);
        }
        if (allowed) {
            holdBossBarStatuses(mc, now);
        }
        board.expire(now);
    }

    private void pollEffects(LocalPlayer player, long now, boolean allowed) {
        Map<String, int[]> current = new HashMap<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            String id = effect.getEffect().unwrapKey().map(k -> k.identifier().toString()).orElse("?");
            int amplifier = effect.getAmplifier();
            int duration = effect.isInfiniteDuration() ? Integer.MAX_VALUE : effect.getDuration();
            current.put(id, new int[]{amplifier, duration});
            int[] before = lastEffects.get(id);
            boolean applied = before == null || before[0] != amplifier || duration > before[1] + 10;
            if (!applied) {
                continue;
            }
            log("effect applied: %s amplifier=%d ticks=%d", id, amplifier, duration);
            if (!allowed || !effectTriggers.get()) {
                continue;
            }
            TimerDef def = table.matchEffect(id, amplifier, duration);
            if (def == null) {
                continue;
            }
            if (def.head() != null && !def.head().equals(itemId(player.getItemBySlot(EquipmentSlot.HEAD)))) {
                log("effect %s fits %s but the head item is %s", id, def.id(), itemId(player.getItemBySlot(EquipmentSlot.HEAD)));
                continue;
            }
            board.start(def, now, duration * 50L, Source.EFFECT, !def.confirmed(), true);
            effectChips.put(def.id(), id);
            log("effect → " + def.id());
        }
        effectChips.entrySet().removeIf(e -> {
            Chip chip = board.get(e.getKey());
            if (chip == null) {
                return true;
            }
            if (!current.containsKey(e.getValue()) && chip.source() == Source.EFFECT) {
                board.end(e.getKey());
                log("effect gone → " + e.getKey() + " ended");
                return true;
            }
            return false;
        });
        lastEffects.clear();
        lastEffects.putAll(current);
    }

    /** The rune of the totem vanilla would use first (main hand, then off hand), cached before a pop removes it. */
    private static Talisman.@Nullable Rune heldTotemRune(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                Talisman talisman = Talisman.parse(stack.getHoverName().getString(), lore(stack));
                return talisman == null ? null : talisman.rune();
            }
        }
        return null;
    }

    private void onOwnTotemPop() {
        Talisman.Rune rune = heldRune;
        log("own totem popped, rune=" + rune);
        if (rune == null || !triggersAllowed()) {
            return;
        }
        String name = rune.name().toLowerCase(Locale.ROOT);
        for (TimerDef def : table.timers()) {
            if (def.totemRune() != null && def.totemRune().equalsIgnoreCase(name)) {
                board.start(def, now(), null, Source.TOTEM, !def.confirmed(), false);
            }
        }
    }

    private void endGoneChips(ClientLevel level, long now) {
        for (Chip chip : board.active(now)) {
            BlockSig sig = chip.def().blocks();
            if (sig == null || !sig.endsWhenGone() || chip.trackedPositions() == 0) {
                continue;
            }
            int remaining = 0;
            for (long p : chip.positions()) {
                BlockState state = level.getBlockState(BlockPos.of(p));
                if (!state.isAir() && sig.accepts(blockId(state))) {
                    remaining++;
                }
            }
            if (TimerBoard.mostlyGone(remaining, chip.trackedPositions())) {
                log("%s ended: %d of %d block(s) left after %.1f s", chip.id(), remaining, chip.trackedPositions(),
                        chip.elapsedMs(now) / 1000.0);
                board.end(chip.id());
            }
        }
    }

    private boolean raidNear(LocalPlayer player) {
        double radius = raidRadius.get();
        for (Chip chip : board.active(now())) {
            if (chip.def().breaks() == null) {
                continue;
            }
            for (long p : chip.positions()) {
                if (player.position().distanceToSqr(Vec3.atCenterOf(BlockPos.of(p))) <= radius * radius) {
                    return true;
                }
            }
        }
        return false;
    }

    private void holdBossBarStatuses(Minecraft mc, long now) {
        for (LerpingBossEvent bar : ((BossHealthOverlayAccessor) mc.gui.getBossOverlay()).skirmish$events().values()) {
            String name = TimerText.normalize(bar.getName().getString());
            for (TimerDef def : table.timers()) {
                if (def.matchesBossBar(name)) {
                    boolean fresh = board.get(def.id()) == null;
                    board.hold(def, now, now + 1500, Source.BOSS_BAR, !def.confirmed());
                    if (fresh) {
                        log("boss bar \"" + bar.getName().getString() + "\" → " + def.id());
                    }
                }
            }
        }
    }

    // ---- server text: chat, title, subtitle, action bar ----

    /** Called for system chat and (from the Gui hook) titles, subtitles and action-bar lines. */
    public void onServerText(String kind, Component message) {
        if (!isEnabled()) {
            return;
        }
        String raw = message.getString();
        if (raw.isBlank()) {
            return;
        }
        logRaw(kind, raw);
        if (!triggersAllowed() || !chatTriggers.get()) {
            return;
        }
        String norm = TimerText.normalize(raw);
        List<TimerDef> matched = table.matchText(norm);
        if (matched.isEmpty()) {
            return;
        }
        if (TimerText.looksLikePlayerChat(raw, onlineNames())) {
            log(kind + " line looks like player chat, ignored: \"" + raw + "\"");
            return;
        }
        long now = now();
        boolean ends = TimerText.announcesEnd(norm);
        OptionalInt seconds = TimerText.seconds(norm);
        for (TimerDef def : matched) {
            if (ends) {
                board.end(def.id());
                log(kind + " → " + def.id() + " ended: \"" + raw + "\"");
            } else if (def.countsDown() && seconds.isPresent()) {
                board.start(def, now, seconds.getAsInt() * 1000L, Source.CHAT, !def.confirmed(), true);
                log(kind + " → " + def.id() + " (" + seconds.getAsInt() + " s named): \"" + raw + "\"");
            } else {
                board.start(def, now, null, Source.CHAT, !def.confirmed(), false);
                log(kind + " → " + def.id() + ": \"" + raw + "\"");
            }
        }
    }

    private void logRaw(String kind, String raw) {
        if (!isDebug()) {
            return;
        }
        long now = now();
        String key = kind + "|" + raw;
        Long last = rawLogged.get(key);
        if (last != null && now - last < RAW_LOG_REPEAT_MS) {
            return;
        }
        rawLogged.remove(key);
        rawLogged.put(key, now);
        while (rawLogged.size() > RAW_LOG_MAX) {
            rawLogged.remove(rawLogged.keySet().iterator().next());
        }
        log("raw " + kind + ": \"" + raw + "\"");
    }

    private static List<String> onlineNames() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            names.add(info.getProfile().name());
        }
        return names;
    }

    // ---- world hooks (client thread, from the mixins) ----

    /** A server block update is about to replace {@code before} with {@code after} at {@code pos}. */
    public void onBlockUpdate(BlockPos pos, BlockState before, BlockState after) {
        if (!triggersAllowed() || !blockTriggers.get() || before == after) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        double distance = Math.sqrt(player.position().distanceToSqr(center));
        long now = now();
        if (!after.isAir() && (before.isAir() || before.canBeReplaced()) && distance <= APPEAR_RECORD_RADIUS) {
            bursts.appear(pos.asLong(), blockId(after), distance, now);
            return;
        }
        if (after.isAir() && !before.isAir()) {
            TimerDef def = bursts.broken(pos.asLong(), center.x, center.y, center.z, blockId(before), distance, now, table.timers());
            if (def != null) {
                blownUp(def, pos, blockId(before), distance, now);
            }
        }
    }

    private void blownUp(TimerDef def, BlockPos pos, String blockId, double distance, long now) {
        boolean fresh = board.get(def.id()) == null;
        Chip chip = board.start(def, now, null, Source.EXPLOSION, !def.confirmed(), false);
        board.track(chip, List.of(pos.asLong()));
        raidNear = raidNear || distance <= raidRadius.get();
        if (fresh) {
            log("%s broken by an explosion %.1f blocks away → %s", blockId, distance, def.id());
        }
    }

    public void onExplosion(Vec3 center, float radius, int blockCount) {
        if (!triggersAllowed() || !blockTriggers.get()) {
            return;
        }
        long now = now();
        List<BlockBursts.Break> early = bursts.explosion(center.x, center.y, center.z, radius, now);
        LocalPlayer player = Minecraft.getInstance().player;
        for (BlockBursts.Break b : early) {
            BlockPos pos = BlockPos.of(b.pos());
            double distance = player == null ? 0 : Math.sqrt(player.position().distanceToSqr(Vec3.atCenterOf(pos)));
            blownUp(b.def(), pos, "watched block (update before the explosion packet)", distance, now);
        }
        if (player != null && isDebug()) {
            double distance = Math.sqrt(player.position().distanceToSqr(center));
            if (distance <= 48) {
                log("explosion %.1f blocks away, radius %.1f, %d block(s)", distance, radius, blockCount);
            }
        }
    }

    // ---- helpers ----

    static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static String itemId(ItemStack stack) {
        return stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static List<String> lore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(lore.lines().size());
        for (Component line : lore.lines()) {
            lines.add(line.getString());
        }
        return lines;
    }
}
