package dev.skirmish.module.analytics.review;

import dev.skirmish.combat.CombatListener;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.Combatant;
import dev.skirmish.combat.DamageInfo;
import dev.skirmish.combat.Fight;
import dev.skirmish.combat.FightEndReason;
import dev.skirmish.combat.HealthChange;
import dev.skirmish.combat.OwnDeath;
import dev.skirmish.module.analytics.AnalyticsHub;
import dev.skirmish.module.analytics.DamageTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Builds the fight reviews from combat tracker events: a {@link FightLog} per active fight (hits with reach and
 * crits, totems, both health curves), the damage I took recently and my health, and on my death a
 * {@link DeathRecap}. Finished fights and deaths become {@link ReviewEntry} rows (newest first, at most
 * {@link #KEPT}). Client thread only.
 */
final class FightRecorder implements CombatListener {
    static final int KEPT = 16;
    private static final long RECENT_MS = 15_000;

    private final FightReviewModule module;
    private final Map<Integer, FightLog> logs = new HashMap<>();
    private final ArrayDeque<ReviewEntry> entries = new ArrayDeque<>();
    private final RecentDamage recentDamage = new RecentDamage(RECENT_MS);
    private final RecentHp recentHp = new RecentHp(RECENT_MS);
    /** The damage event being dispatched: the one that opens a fight arrives before {@link #onFightStart}. */
    private @Nullable DamageInfo current;
    private @Nullable FightLog lastHitOnMe;
    private @Nullable DeathRecap pendingDeath;
    private @Nullable ItemStack pendingWeapon;
    private final Consumer<ReviewEntry> onEntry;

    FightRecorder(FightReviewModule module, Consumer<ReviewEntry> onEntry) {
        this.module = module;
        this.onEntry = onEntry;
    }

    /** Newest first. */
    List<ReviewEntry> entries() {
        return List.copyOf(entries);
    }

    @Nullable FightLog log(Fight fight) {
        return logs.get(fight.id());
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    // ---- per tick ----

    void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        long now = now();
        float myHp = Math.max(0, player.getHealth()) + player.getAbsorptionAmount();
        if (player.isAlive()) {
            recentHp.add(now, myHp);
        }
        for (FightLog log : logs.values()) {
            log.sample(true, now, myHp, player.getMaxHealth());
            LivingEntity living = find(mc, log.opponent());
            if (living != null && living.isAlive() && !AnalyticsHub.hidden(living)) {
                log.sample(false, now, Math.max(0, living.getHealth()) + living.getAbsorptionAmount(), living.getMaxHealth());
            }
        }
        recentDamage.prune(now);
    }

    /** Entity ids change on world change: drop what refers to them. */
    void resetIds() {
        lastHitOnMe = null;
        current = null;
        recentDamage.clear();
        recentHp.clear();
    }

    // ---- combat events ----

    @Override
    public void onDamage(DamageInfo info) {
        current = info;
        if (!module.isEnabled()) {
            return;
        }
        CombatTracker tracker = CombatTracker.get();
        if (info.byMe() && !info.victim().self()) {
            Fight fight = tracker.fightWith(info.victim().uuid());
            FightLog log = fight == null ? null : logs.get(fight.id());
            if (log != null) {
                recordHit(log, info);
            }
        } else if (info.onMe()) {
            Combatant attacker = info.attacker();
            // An attacker invisible to me stays unnamed (HolyWorld: no invisibility indicators).
            Combatant named = attacker != null && !attacker.self() && AnalyticsHub.hidden(attacker.uuid(), attacker.entityId()) ? null : attacker;
            recentDamage.add(new DamageTaken(info.timeMs(), named == null ? null : named.name(),
                    named == null ? null : named.uuid(), info.damageType(), DamageTypes.inferred(info.damageType())));
            if (attacker != null && !attacker.self()) {
                Fight fight = tracker.fightWith(attacker.uuid());
                FightLog log = fight == null ? null : logs.get(fight.id());
                if (log != null) {
                    recordHit(log, info);
                }
            }
        }
    }

    private void recordHit(FightLog log, DamageInfo info) {
        if (info.byMe()) {
            log.hit(true, info.timeMs(), AnalyticsHub.get().reachFor(info.victim().entityId(), info.timeMs()));
        } else {
            log.hit(false, info.timeMs(), Double.NaN);
            lastHitOnMe = log;
        }
    }

    @Override
    public void onFightStart(Fight fight) {
        if (!module.isEnabled()) {
            return;
        }
        FightLog log = new FightLog(fight.id(), fight.opponent(), fight.startMs());
        logs.put(fight.id(), log);
        DamageInfo first = current;
        UUID opponent = fight.opponent().uuid();
        if (first != null && (first.byMe() && first.victim().uuid().equals(opponent)
                || first.onMe() && first.attacker() != null && first.attacker().uuid().equals(opponent))) {
            recordHit(log, first);
        }
        module.log("review: fight #%d with %s recorded from %s", fight.id(), fight.opponent().name(),
                first == null ? "?" : first.byMe() ? "my hit" : "their hit");
    }

    @Override
    public void onHealthChange(HealthChange change) {
        if (!module.isEnabled()) {
            return;
        }
        float lost = change.lost();
        if (change.entity().self()) {
            recentDamage.amount(lost, change.timeMs());
            FightLog log = lastHitOnMe;
            if (log != null) {
                log.damage(false, lost, change.timeMs());
            }
            return;
        }
        Combatant by = change.attributedTo();
        if (by != null && by.self()) {
            Fight fight = CombatTracker.get().fightWith(change.entity().uuid());
            FightLog log = fight == null ? null : logs.get(fight.id());
            if (log != null) {
                log.damage(true, lost, change.timeMs());
            }
        }
    }

    @Override
    public void onCrit(Entity target, boolean magic) {
        if (magic || !module.isEnabled()) {
            return;
        }
        long now = now();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && target.getId() == mc.player.getId()) {
            recentDamage.crit(now);
            FightLog log = lastHitOnMe;
            if (log == null && logs.size() == 1) {
                log = logs.values().iterator().next();
            }
            if (log != null) {
                log.crit(false, now);
            }
            return;
        }
        for (FightLog log : logs.values()) {
            if (log.opponent().uuid().equals(target.getUUID())) {
                log.crit(true, now);
            }
        }
    }

    @Override
    public void onTotemPop(Combatant entity, @Nullable Fight fight) {
        if (fight == null || !module.isEnabled()) {
            return;
        }
        FightLog log = logs.get(fight.id());
        if (log != null) {
            log.totem(entity.self(), now());
        }
    }

    @Override
    public void onOwnDeath(OwnDeath death) {
        if (!module.isEnabled()) {
            return;
        }
        long now = death.timeMs();
        Minecraft mc = Minecraft.getInstance();
        Combatant killer = death.killer();
        if (killer != null && !AnalyticsHub.get().mayShow(killer, now)) {
            module.log("review: killer %s is invisible to me and not named in chat: shown as unknown", killer.name());
            killer = null;
        }
        float maxHp = mc.player == null ? 20f : mc.player.getMaxHealth();
        List<DamageTaken> damage = recentDamage.window(now - DeathRecap.WINDOW_MS, now);
        HpSeries hp = recentHp.series(now - DeathRecap.WINDOW_MS, now, 0f);
        String message = death.message() == null ? "" : death.message().getString();
        DeathRecap recap = new DeathRecap(now, killer == null ? null : killer.name(), killer == null ? null : killer.uuid(),
                message, damage, hp, maxHp);
        ItemStack weapon = killer == null ? ItemStack.EMPTY : heldBy(mc, killer);
        module.log("review: death recap, killer %s, %d damage events in %d s, weapon %s", killer == null ? "?" : killer.name(),
                damage.size(), DeathRecap.WINDOW_MS / 1000, weapon.isEmpty() ? "-" : weapon.getItem().toString());
        recentDamage.clear();
        recentHp.clear();
        lastHitOnMe = null;
        if (death.fights().isEmpty()) {
            add(new ReviewEntry(null, null, null, recap, weapon, now));
        } else {
            pendingDeath = recap;
            pendingWeapon = weapon;
        }
    }

    /** Main-hand item of a loaded entity (the killer), copied; empty when not loaded. */
    static ItemStack heldBy(Minecraft mc, Combatant who) {
        LivingEntity living = find(mc, who);
        return living == null || AnalyticsHub.hidden(living) ? ItemStack.EMPTY : living.getMainHandItem().copy();
    }

    /** The loaded entity of a combatant: by entity id when it still matches, else a player with that UUID. */
    static @Nullable LivingEntity find(Minecraft mc, Combatant who) {
        if (mc.level == null) {
            return null;
        }
        Entity entity = who.entityId() >= 0 ? mc.level.getEntity(who.entityId()) : null;
        if (entity instanceof LivingEntity living && entity.getUUID().equals(who.uuid())) {
            return living;
        }
        for (Player p : mc.level.players()) {
            if (p.getUUID().equals(who.uuid())) {
                return p;
            }
        }
        return null;
    }

    @Override
    public void onFightEnd(Fight fight) {
        FightLog log = logs.remove(fight.id());
        if (!module.isEnabled()) {
            return;
        }
        if (!AnalyticsHub.get().seenVisible(fight)) {
            module.log("review: fight #%d not kept, %s was invisible to me throughout", fight.id(), fight.opponent().name());
            return;
        }
        DeathRecap death = fight.endReason() == FightEndReason.OWN_DEATH ? pendingDeath : null;
        ItemStack weapon = death == null || pendingWeapon == null ? ItemStack.EMPTY : pendingWeapon;
        add(new ReviewEntry(fight, log, CombatTracker.get().equipment(fight), death, weapon, fight.endMs()));
    }

    private void add(ReviewEntry entry) {
        entries.addFirst(entry);
        while (entries.size() > KEPT) {
            entries.removeLast();
        }
        onEntry.accept(entry);
    }
}
