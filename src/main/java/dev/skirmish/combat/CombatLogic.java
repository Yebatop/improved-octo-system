package dev.skirmish.combat;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * Fight bookkeeping without Minecraft types (unit tested). {@link CombatTracker} feeds it from packets.
 * Time is passed in explicitly so tests can drive it.
 */
public final class CombatLogic {
    /** A health drop is attributed to the damage event on the same entity at most this long before it. */
    public static final long DAMAGE_TO_HEALTH_WINDOW_MS = 600;
    /** Event 3 and "health reached 0" for the same death are merged within this window. */
    public static final long DEATH_DEDUP_MS = 3_000;
    private static final long DAMAGE_MEMORY_MS = 60_000;
    private static final int FINISHED_KEPT = 16;

    public interface Config {
        long killWindowMs();

        long fightTimeoutMs();

        boolean playersOnly();
    }

    private final Config config;
    private final Consumer<String> log;
    private final BiConsumer<String, Throwable> errors;
    private final List<CombatListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<UUID, Fight> active = new LinkedHashMap<>();
    private final ArrayDeque<Fight> finished = new ArrayDeque<>();
    private final Map<Integer, DamageInfo> lastDamage = new HashMap<>();
    private final Map<Integer, float[]> lastHealth = new HashMap<>();
    private final Map<UUID, Long> recentDeaths = new HashMap<>();
    private @Nullable DamageInfo lastDamageOnMe;
    private int nextFightId = 1;

    public CombatLogic(Config config, Consumer<String> log, BiConsumer<String, Throwable> errors) {
        this.config = config;
        this.log = log;
        this.errors = errors;
    }

    public void addListener(CombatListener listener) {
        listeners.add(listener);
    }

    public void removeListener(CombatListener listener) {
        listeners.remove(listener);
    }

    public void dispatch(Consumer<CombatListener> call) {
        for (CombatListener listener : listeners) {
            try {
                call.accept(listener);
            } catch (Throwable t) {
                errors.accept("listener " + listener.getClass().getName() + " failed", t);
            }
        }
    }

    public Collection<Fight> activeFights() {
        return List.copyOf(active.values());
    }

    public @Nullable Fight fightWith(UUID opponent) {
        return active.get(opponent);
    }

    /** Finished fights, newest first (last {@value FINISHED_KEPT}). */
    public List<Fight> finishedFights() {
        return List.copyOf(finished);
    }

    public @Nullable Fight lastFinished() {
        return finished.peekFirst();
    }

    private boolean eligible(@Nullable Combatant c) {
        return c != null && !c.self() && (c.player() || !config.playersOnly());
    }

    public void onDamage(DamageInfo info) {
        long now = info.timeMs();
        lastDamage.put(info.victim().entityId(), info);
        if (info.onMe()) {
            lastDamageOnMe = info;
        }
        dispatch(l -> l.onDamage(info));

        Combatant opponent;
        boolean dealt;
        if (info.byMe() && eligible(info.victim())) {
            opponent = info.victim();
            dealt = true;
        } else if (info.onMe() && eligible(info.attacker())) {
            opponent = info.attacker();
            dealt = false;
        } else {
            return;
        }

        Fight fight = active.get(opponent.uuid());
        if (fight == null) {
            fight = new Fight(nextFightId++, opponent, now);
            active.put(opponent.uuid(), fight);
            log.accept("fight #" + fight.id() + " started with " + opponent.name() + " (" + (dealt ? "I hit first" : "they hit first") + ", " + info.damageType() + ")");
            Fight started = fight;
            dispatch(l -> l.onFightStart(started));
        } else {
            fight.updateOpponent(opponent);
        }
        if (dealt) {
            fight.hitsDealt++;
            fight.lastHitDealtMs = now;
            fight.lastDamageTypeDealt = info.damageType();
            log.accept("hit -> " + opponent.name() + " #" + fight.hitsDealt + " (" + info.damageType() + ")");
        } else {
            fight.hitsTaken++;
            fight.lastHitTakenMs = now;
            log.accept("hit <- " + opponent.name() + " #" + fight.hitsTaken + " (" + info.damageType() + ")");
        }
    }

    /** Synced health/absorption of a living entity; only decreases are reported to listeners. */
    public void onHealth(Combatant entity, float health, float absorption, float maxHealth, long now) {
        if (!entity.self() && !eligible(entity)) {
            return;
        }
        float[] prev = lastHealth.get(entity.entityId());
        float prevHealth = prev == null ? Float.NaN : prev[0];
        float prevAbsorption = prev == null ? Float.NaN : prev[1];
        if (prev == null) {
            lastHealth.put(entity.entityId(), new float[]{health, absorption});
        } else {
            prev[0] = health;
            prev[1] = absorption;
        }

        Fight fight = entity.self() ? null : active.get(entity.uuid());
        if (fight != null) {
            fight.opponentHealth = health;
            fight.opponentAbsorption = absorption;
            fight.opponentMaxHealth = maxHealth;
        }
        if (prev == null) {
            return;
        }

        float lost = (prevHealth + prevAbsorption) - (health + absorption);
        if (lost > 0.001f) {
            DamageInfo cause = lastDamage.get(entity.entityId());
            Combatant attributed = cause != null && now - cause.timeMs() <= DAMAGE_TO_HEALTH_WINDOW_MS ? cause.attacker() : null;
            if (fight != null && attributed != null && attributed.self()) {
                fight.damageDealt += lost;
                fight.healthDropsObserved++;
                log.accept(String.format(Locale.ROOT, "%s lost %.1f hp after my hit (%.1f+%.1f left), total dealt %.1f",
                        entity.name(), lost, health, absorption, fight.damageDealt));
            }
            if (entity.self() && attributed != null && !attributed.self()) {
                Fight against = active.get(attributed.uuid());
                if (against != null) {
                    against.damageTaken += lost;
                }
            }
            HealthChange change = new HealthChange(entity, prevHealth, prevAbsorption, health, absorption, maxHealth, attributed, now);
            dispatch(l -> l.onHealthChange(change));
        }
        if (health <= 0 && prevHealth > 0 && !entity.self()) {
            onDeath(entity, "health synced to 0", now);
        }
    }

    public void onTotem(Combatant entity, long now) {
        Fight fight;
        if (entity.self()) {
            fight = mostRecentlyHitMe(now, config.fightTimeoutMs());
            if (fight != null) {
                fight.myTotems++;
            }
            log.accept("my totem popped" + (fight == null ? "" : " (fight with " + fight.opponent().name() + ")"));
        } else {
            fight = active.get(entity.uuid());
            if (fight != null) {
                fight.opponentTotems++;
                log.accept(entity.name() + " popped a totem (#" + fight.opponentTotems + " this fight)");
            } else {
                log.accept(entity.name() + " popped a totem (not in a fight with me)");
            }
        }
        Fight f = fight;
        dispatch(l -> l.onTotemPop(entity, f));
    }

    /** Death of another entity; {@code signal} says which packet reported it (for debug.log). */
    public void onDeath(Combatant entity, String signal, long now) {
        if (entity.self()) {
            return;
        }
        Long previous = recentDeaths.get(entity.uuid());
        if (previous != null && now - previous < DEATH_DEDUP_MS) {
            return;
        }
        recentDeaths.put(entity.uuid(), now);
        Fight fight = active.get(entity.uuid());
        dispatch(l -> l.onEntityDeath(entity, fight));
        if (fight == null) {
            if (entity.player()) {
                log.accept(entity.name() + " died (" + signal + "): no fight with me, not a kill");
            }
            return;
        }
        long window = config.killWindowMs();
        if (fight.lastHitDealtMs >= 0 && now - fight.lastHitDealtMs <= window) {
            DamageInfo last = lastDamage.get(entity.entityId());
            String lastHitter = last == null || last.attacker() == null ? "unknown" : last.attacker().name();
            log.accept("KILL " + entity.name() + " (" + signal + "): my last hit " + (now - fight.lastHitDealtMs)
                    + " ms ago <= " + window + " ms, last damage by " + lastHitter + "; " + fight);
            endFight(fight, FightEndReason.KILL, now);
        } else {
            String why = fight.lastHitDealtMs < 0 ? "none of my hits landed"
                    : "my last hit was " + (now - fight.lastHitDealtMs) + " ms ago > kill window " + window + " ms";
            log.accept(entity.name() + " died (" + signal + ") but kill NOT counted: " + why);
            endFight(fight, FightEndReason.OPPONENT_DIED, now);
        }
    }

    public OwnDeath onOwnDeath(@Nullable Component message, long now) {
        Combatant killer = null;
        long window = config.killWindowMs();
        if (lastDamageOnMe != null && now - lastDamageOnMe.timeMs() <= window && lastDamageOnMe.attacker() != null
                && !lastDamageOnMe.attacker().self()) {
            killer = lastDamageOnMe.attacker();
        } else {
            Fight recent = mostRecentlyHitMe(now, window);
            if (recent != null) {
                killer = recent.opponent();
            }
        }
        List<Fight> fights = new ArrayList<>(active.values());
        OwnDeath death = new OwnDeath(message, killer, List.copyOf(fights), now);
        log.accept("I died" + (killer == null ? " (no attacker within " + window + " ms)" : ", killer " + killer.name())
                + ", active fights: " + fights.size());
        dispatch(l -> l.onOwnDeath(death));
        for (Fight fight : fights) {
            endFight(fight, FightEndReason.OWN_DEATH, now);
        }
        lastDamageOnMe = null;
        return death;
    }

    private @Nullable Fight mostRecentlyHitMe(long now, long maxAgeMs) {
        Fight best = null;
        for (Fight fight : active.values()) {
            if (fight.lastHitTakenMs >= 0 && now - fight.lastHitTakenMs <= maxAgeMs
                    && (best == null || fight.lastHitTakenMs > best.lastHitTakenMs)) {
                best = fight;
            }
        }
        return best;
    }

    public void tick(long now) {
        long timeout = config.fightTimeoutMs();
        for (Fight fight : List.copyOf(active.values())) {
            if (now - fight.lastActivityMs() > timeout) {
                log.accept("fight #" + fight.id() + " with " + fight.opponent().name() + " timed out after "
                        + (now - fight.lastActivityMs()) + " ms without hits");
                endFight(fight, FightEndReason.TIMEOUT, now);
            }
        }
        lastDamage.values().removeIf(d -> now - d.timeMs() > DAMAGE_MEMORY_MS);
        recentDeaths.values().removeIf(t -> now - t > DAMAGE_MEMORY_MS);
    }

    /** Drops health baselines of entities that are gone. */
    public void retainHealth(IntPredicate present) {
        lastHealth.keySet().removeIf(id -> !present.test(id));
    }

    /** Disconnect or dimension change: ends everything, forgets entity ids. */
    public void reset(String reason, long now) {
        if (!active.isEmpty()) {
            log.accept("ending " + active.size() + " fight(s): " + reason);
        }
        for (Fight fight : List.copyOf(active.values())) {
            endFight(fight, FightEndReason.WORLD_CHANGE, now);
        }
        lastDamage.clear();
        lastHealth.clear();
        recentDeaths.clear();
        lastDamageOnMe = null;
    }

    private void endFight(Fight fight, FightEndReason reason, long now) {
        if (!fight.isActive()) {
            return;
        }
        fight.end(reason, now);
        active.remove(fight.opponent().uuid());
        finished.addFirst(fight);
        while (finished.size() > FINISHED_KEPT) {
            finished.removeLast();
        }
        if (reason == FightEndReason.KILL) {
            dispatch(l -> l.onKill(fight));
        }
        log.accept("fight #" + fight.id() + " ended: " + fight + ", " + fight.durationMs(now) + " ms");
        dispatch(l -> l.onFightEnd(fight));
    }

    /** Test helper: number of entities with a health baseline. */
    int trackedHealthCount() {
        return lastHealth.size();
    }
}
