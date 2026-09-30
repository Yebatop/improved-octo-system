package dev.skirmish.module.analytics.combo;

import dev.skirmish.combat.CombatListener;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.Combatant;
import dev.skirmish.combat.DamageInfo;
import dev.skirmish.combat.OwnDeath;
import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.analytics.AnalyticsHub;
import dev.skirmish.module.analytics.ComboCounter;
import dev.skirmish.module.analytics.DamageTypes;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * «Комбо и дистанция»: my current combo (hits in a row without being hit), the reach of my last hit (eye to the
 * target's hitbox at my click) and my clicks per second (attack attempts). Read-only: nothing is sent or pressed.
 * Hits on entities invisible to me are ignored (HolyWorld bans invisibility indicators).
 */
public final class ComboHudModule extends Module {
    public static final String ID = "combo_hud";

    final BoolSetting combo = add(new BoolSetting("combo", true));
    final NumberSetting resetSeconds = add(new NumberSetting("reset_seconds", 3, 1, 10, 0.5).unit(" s"));
    final BoolSetting reach = add(new BoolSetting("reach", true));
    final BoolSetting cps = add(new BoolSetting("cps", true));
    final BoolSetting onlyInFight = add(new BoolSetting("only_in_fight", true));

    private final ComboCounter counter = new ComboCounter(3_000);
    private double lastReach = Double.NaN;
    private long lastHitMs = -1;

    public ComboHudModule() {
        super(ID, false);
        resetSeconds.under(combo).visibleWhen(combo::get);
    }

    @Override
    public Category category() {
        return Category.COMBAT;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        AnalyticsHub.get().require(this::isEnabled);
        Hud.get().register(new ComboHud(this));
        CombatTracker.get().addListener(new CombatListener() {
            @Override
            public void onDamage(DamageInfo info) {
                if (isEnabled()) {
                    onHit(info);
                }
            }

            @Override
            public void onOwnDeath(OwnDeath death) {
                counter.taken(death.timeMs());
            }
        });
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) -> clear());
    }

    private void onHit(DamageInfo info) {
        long now = info.timeMs();
        counter.setResetAfterMs(Math.round(resetSeconds.get() * 1000));
        if (info.byMe() && !info.victim().self()) {
            Minecraft mc = Minecraft.getInstance();
            Entity victim = mc.level == null ? null : mc.level.getEntity(info.victim().entityId());
            if (victim != null && AnalyticsHub.hidden(victim)) {
                return;
            }
            int n = counter.hit(now);
            lastReach = AnalyticsHub.get().reachFor(info.victim().entityId(), now);
            lastHitMs = now;
            log("hit on %s: combo %d, reach %.2f", info.victim().name(), n, lastReach);
        } else if (info.onMe()) {
            Combatant attacker = info.attacker();
            if (DamageTypes.isHit(info.damageType(), attacker != null && !attacker.self())) {
                if (counter.current(now) > 0) {
                    log("combo %d broken by %s", counter.current(now), info.damageType());
                }
                counter.taken(now);
            }
        }
    }

    private void clear() {
        counter.reset();
        lastReach = Double.NaN;
        lastHitMs = -1;
    }

    @Override
    protected void onDisable() {
        clear();
    }

    @Override
    public void tick() {
        AnalyticsHub.get().tick();
    }

    int combo(long nowMs) {
        return counter.current(nowMs);
    }

    double lastReach() {
        return lastReach;
    }

    long lastHitMs() {
        return lastHitMs;
    }
}
