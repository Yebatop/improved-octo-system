package dev.skirmish.module.bosscoach;

import dev.skirmish.SkirmishClient;
import dev.skirmish.combat.CombatListener;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.DamageInfo;
import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.bosscoach.mixin.LerpingBossEventAccessor;
import dev.skirmish.module.hwtimers.JsonTables;
import dev.skirmish.module.pvp.mixin.BossHealthOverlayAccessor;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * «Boss Coach»: while a HolyWorld boss bar is on screen, a small panel with the boss's phase by HP percent, counter
 * tips from the wiki and an estimate of my share of the damage and my DPS ({@link BossMeter}). Everything is read
 * from what the client already shows: boss bars (via the PvP module's accessor) and server-confirmed damage events.
 */
public final class BossCoachModule extends Module {
    public static final String ID = "boss_coach";
    public static final String METER_FEATURE = "boss_meter";
    /** A bar that vanished is kept this long (a bar can flicker between phases). */
    private static final long FORGET_MS = 10_000;

    final BoolSetting phases = add(new BoolSetting("phases", true));
    final BoolSetting tips = add(new BoolSetting("tips", true));
    final BoolSetting meter = (BoolSetting) add(new BoolSetting("meter", true)).feature(METER_FEATURE);
    final NumberSetting attributionMs = (NumberSetting) add(new NumberSetting("attribution_ms", 450, 150, 1000, 50).unit(" ms"))
            .under(meter).visibleWhen(meter::get);
    final NumberSetting dpsWindow = (NumberSetting) add(new NumberSetting("dps_window", 10, 5, 30, 1).unit(" s"))
            .under(meter).visibleWhen(meter::get);

    private BossTable table = BossTable.bundled();
    private final Map<UUID, Tracked> tracked = new LinkedHashMap<>();
    private final Set<String> loggedBars = new HashSet<>();

    /** One boss bar being followed. */
    static final class Tracked {
        final UUID id;
        final BossTable.Boss boss;
        final BossMeter meter = new BossMeter();
        String name;
        float progress;
        long lastSeenMs;
        boolean onScreen;

        Tracked(UUID id, BossTable.Boss boss, String name) {
            this.id = id;
            this.boss = boss;
            this.name = name;
        }
    }

    public BossCoachModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.WORLD;
    }

    @Override
    public void onInitialize() {
        JsonTables.Loaded loaded = JsonTables.load(SkirmishClient.configDir(), BossTable.OVERRIDE_NAME, BossTable.RESOURCE);
        table = BossTable.parse(loaded.root());
        if (loaded.problem() != null) {
            error("boss table override ignored: " + loaded.problem(), null);
        }
        table.problems().forEach(p -> error("boss table: " + p, null));
        Hud.get().register(new BossCoachHud(this));
        CombatTracker.get().addListener(new CombatListener() {
            @Override
            public void onDamage(DamageInfo info) {
                if (isEnabled() && info.byMe() && !info.victim().player()) {
                    onMyHit(info.victim().name(), info.timeMs());
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> tracked.clear());
    }

    @Override
    protected void onDisable() {
        tracked.clear();
    }

    BossTable table() {
        return table;
    }

    private void onMyHit(String victimName, long timeMs) {
        if (tracked.isEmpty()) {
            return;
        }
        for (Tracked t : tracked.values()) {
            BossTable.Boss named = table.match(victimName);
            boolean onBoss = named != null && named.id().equals(t.boss.id());
            t.meter.myHit(timeMs, onBoss);
            log("my hit on \"%s\" (%s) during %s", victimName, onBoss ? "the boss" : "another mob", t.boss.id());
        }
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        for (Tracked t : tracked.values()) {
            t.onScreen = false;
        }
        if (mc.player != null && mc.level != null) {
            for (LerpingBossEvent bar : ((BossHealthOverlayAccessor) mc.gui.getBossOverlay()).skirmish$events().values()) {
                read(bar, now);
            }
        }
        tracked.values().removeIf(t -> {
            if (now - t.lastSeenMs > FORGET_MS) {
                log("%s left: my share ≈ %.0f%% of %.0f%% seen, %d hit(s)", t.boss.id(), share(t) * 100, t.meter.total() * 100,
                        t.meter.myHits());
                return true;
            }
            return false;
        });
    }

    private void read(LerpingBossEvent bar, long now) {
        String name = bar.getName().getString();
        float target = ((LerpingBossEventAccessor) bar).skirmish$targetPercent();
        Tracked t = tracked.get(bar.getId());
        if (t == null) {
            BossTable.Boss boss = table.match(name);
            if (boss == null) {
                if (isDebug() && loggedBars.add(name) && loggedBars.size() < 256) {
                    log("boss bar not in the table: \"" + name + "\"");
                }
                return;
            }
            t = new Tracked(bar.getId(), boss, name);
            tracked.put(bar.getId(), t);
            log("boss bar \"%s\" → %s at %.1f%%", name, boss.id(), target * 100);
        }
        t.name = name;
        t.progress = target;
        t.lastSeenMs = now;
        t.onScreen = true;
        t.meter.progress(target, now, Math.round(attributionMs.get()));
    }

    /** Bosses on screen now, in bar order. */
    List<Tracked> onScreen() {
        List<Tracked> out = new ArrayList<>();
        for (Tracked t : tracked.values()) {
            if (t.onScreen) {
                out.add(t);
            }
        }
        return out;
    }

    static float share(Tracked t) {
        float s = t.meter.share();
        return Float.isNaN(s) ? 0f : s;
    }

    long dpsWindowMs() {
        return Math.round(dpsWindow.get() * 1000);
    }

    @Nullable Tracked primary() {
        List<Tracked> list = onScreen();
        return list.isEmpty() ? null : list.getFirst();
    }
}
