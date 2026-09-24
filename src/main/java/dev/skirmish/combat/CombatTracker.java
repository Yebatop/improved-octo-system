package dev.skirmish.combat;

import dev.skirmish.debug.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared combat tracker. The core mixin on {@code ClientPacketListener} is the only place that hooks
 * damage / entity event / animate / entity data / combat kill packets; modules subscribe via {@link #addListener}.
 */
public final class CombatTracker {
    private static final long HEALTH_PRUNE_INTERVAL_MS = 5_000;
    /** Test aid: ignore the attacker in damage packets, as HolyWorld sends none ({@code -Dskirmish.debug.anonymousDamage=true}). */
    private static final boolean SIMULATE_ANONYMOUS = Boolean.getBoolean("skirmish.debug.anonymousDamage");
    /** HolyWorld death line: «▶ Вы были убиты игроком Enemy_3 на координатах -541 53 -193». */
    private static final Pattern CHAT_KILLER = Pattern.compile("(?iu)убит[аы]?\\s+игроком\\s+([A-Za-z0-9_]{3,16})");
    private static final long CHAT_KILLER_MAX_AGE_MS = 3_000;
    private static @Nullable CombatTracker instance;

    private final CombatTrackerModule module;
    private final CombatLogic logic;
    private final Map<Integer, EquipmentSnapshot> equipmentByFight = new HashMap<>();
    private long lastHealthPrune;

    CombatTracker(CombatTrackerModule module) {
        this.module = module;
        this.logic = new CombatLogic(module, module::log, (msg, t) -> DebugLog.error(module.id(), msg, t));
        this.logic.addListener(new CombatListener() {
            @Override
            public void onFightStart(Fight fight) {
                countOpeningAttempt(fight);
            }
        });
    }

    static CombatTracker install(CombatTrackerModule module) {
        instance = new CombatTracker(module);
        return instance;
    }

    public static CombatTracker get() {
        if (instance == null) {
            throw new IllegalStateException("CombatTracker is created by CombatTrackerModule");
        }
        return instance;
    }

    private long lastAttackAttemptMs = -1;
    private int lastAttackTargetId = -1;
    private final Map<Integer, Long> lastSwingMs = new HashMap<>();
    private @Nullable String chatKiller;
    private long chatKillerMs;

    /**
     * Left-click attack attempt by the local player (read from Minecraft.startAttack, never sent anywhere).
     *
     * @param targetId the entity under the crosshair, or -1
     */
    public void onAttackAttempt(int targetId) {
        lastAttackAttemptMs = now();
        lastAttackTargetId = targetId;
        for (Fight fight : logic.activeFights()) {
            fight.attackAttempts++;
        }
    }

    /** The attempt that opened a fight happened before the fight existed; count it. */
    void countOpeningAttempt(Fight fight) {
        if (lastAttackAttemptMs >= 0 && fight.startMs() - lastAttackAttemptMs < 1000 && fight.attackAttempts == 0) {
            fight.attackAttempts = 1;
        }
    }

    public void addListener(CombatListener listener) {
        logic.addListener(listener);
    }

    public void removeListener(CombatListener listener) {
        logic.removeListener(listener);
    }

    public Collection<Fight> activeFights() {
        return logic.activeFights();
    }

    public @Nullable Fight fightWith(UUID opponent) {
        return logic.fightWith(opponent);
    }

    /** Finished fights, newest first. */
    public List<Fight> finishedFights() {
        return logic.finishedFights();
    }

    public @Nullable Fight lastFinished() {
        return logic.lastFinished();
    }

    /** Opponent equipment as last seen during the fight (kept for recent finished fights too). */
    public @Nullable EquipmentSnapshot equipment(Fight fight) {
        return equipmentByFight.get(fight.id());
    }

    public static Combatant combatant(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        boolean self = mc.player != null && entity.getId() == mc.player.getId();
        String name = entity instanceof Player player ? player.getGameProfile().name() : entity.getName().getString();
        return new Combatant(entity.getId(), entity.getUUID(), name, entity instanceof Player, self);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    // ---- called by dev.skirmish.mixin.ClientPacketListenerMixin (client thread, after vanilla handling) ----

    public void onDamagePacket(Entity victim, DamageSource source) {
        Entity cause = SIMULATE_ANONYMOUS ? null : source.getEntity();
        Entity direct = SIMULATE_ANONYMOUS ? null : source.getDirectEntity();
        String type = source.typeHolder().unwrapKey().map(key -> key.identifier().toString()).orElse(source.getMsgId());
        long now = now();
        Combatant attacker = cause == null ? null : combatant(cause);
        if (cause == null && direct == null) {
            attacker = inferAttacker(victim, now);
            if (attacker != null) {
                type = type + " (inferred)";
            }
        }
        logic.onDamage(new DamageInfo(combatant(victim), attacker, direct == null ? null : combatant(direct), type, now));
    }

    /** The server named no attacker: see {@link AttackerInference}. */
    private @Nullable Combatant inferAttacker(Entity victim, long now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        if (victim.getId() != mc.player.getId()) {
            if (AttackerInference.isMyHit(victim.getId(), lastAttackTargetId, now - lastAttackAttemptMs)) {
                return combatant(mc.player);
            }
            return null;
        }
        List<AttackerInference.Candidate> candidates = new ArrayList<>();
        for (Player other : mc.level.players()) {
            if (other == mc.player || !other.isAlive()) {
                continue;
            }
            double distance = other.distanceTo(mc.player);
            if (distance > AttackerInference.MELEE_RANGE) {
                continue;
            }
            Long swing = lastSwingMs.get(other.getId());
            candidates.add(new AttackerInference.Candidate(combatant(other), distance, swing == null ? -1 : now - swing,
                    logic.fightWith(other.getUUID()) != null));
        }
        Combatant found = AttackerInference.attackerOnMe(candidates);
        if (found != null) {
            module.log("hit on me without attacker in the packet: inferred " + found.name() + " from " + candidates.size() + " player(s) in range");
        }
        return found;
    }

    /** System chat line; HolyWorld names my killer there, not in the death packet. */
    public void onSystemChat(String text) {
        Matcher m = CHAT_KILLER.matcher(text);
        if (m.find()) {
            chatKiller = m.group(1);
            chatKillerMs = now();
        }
    }

    private @Nullable Combatant chatKiller(long now) {
        String name = chatKiller;
        if (name == null || now - chatKillerMs > CHAT_KILLER_MAX_AGE_MS) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Player player : mc.level.players()) {
                if (player.getGameProfile().name().equalsIgnoreCase(name)) {
                    return combatant(player);
                }
            }
        }
        if (mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(name);
            if (info != null) {
                return new Combatant(-1, info.getProfile().id(), info.getProfile().name(), true, false);
            }
        }
        return null;
    }

    public void onEntityEvent(Entity entity, byte eventId) {
        if (eventId == 35) {
            logic.onTotem(combatant(entity), now());
        } else if (eventId == 3 && entity instanceof LivingEntity) {
            logic.onDeath(combatant(entity), "entity event 3", now());
        }
    }

    public void onAnimate(Entity entity, int action) {
        if ((action == 0 || action == 3) && entity instanceof Player) {
            lastSwingMs.put(entity.getId(), now());
        }
        switch (action) {
            case 0 -> logic.dispatch(l -> l.onSwing(entity, InteractionHand.MAIN_HAND));
            case 3 -> logic.dispatch(l -> l.onSwing(entity, InteractionHand.OFF_HAND));
            case 4 -> logic.dispatch(l -> l.onCrit(entity, false));
            case 5 -> logic.dispatch(l -> l.onCrit(entity, true));
            default -> {
            }
        }
    }

    public void onEntityData(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (module.playersOnly() && !(entity instanceof Player)) {
            return;
        }
        logic.onHealth(combatant(living), living.getHealth(), living.getAbsorptionAmount(), living.getMaxHealth(), now());
    }

    public void onOwnDeath(Component message) {
        long now = now();
        logic.onOwnDeath(message, now, chatKiller(now));
        chatKiller = null;
    }

    // ---- lifecycle ----

    void tick() {
        long now = now();
        logic.tick(now);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        for (Fight fight : logic.activeFights()) {
            Entity entity = mc.level.getEntity(fight.opponent().entityId());
            if (entity instanceof LivingEntity living && living.isAlive() && entity.getUUID().equals(fight.opponent().uuid())) {
                EquipmentSnapshot snapshot = equipmentByFight.get(fight.id());
                if (snapshot == null || !snapshot.matches(living)) {
                    equipmentByFight.put(fight.id(), EquipmentSnapshot.capture(living, now));
                    if (snapshot == null) {
                        module.log("equipment of " + fight.opponent().name() + " captured");
                    }
                }
            }
        }
        if (equipmentByFight.size() > 32) {
            Set<Integer> keep = new HashSet<>();
            logic.activeFights().forEach(f -> keep.add(f.id()));
            logic.finishedFights().forEach(f -> keep.add(f.id()));
            equipmentByFight.keySet().retainAll(keep);
        }
        if (now - lastHealthPrune > HEALTH_PRUNE_INTERVAL_MS) {
            lastHealthPrune = now;
            lastSwingMs.values().removeIf(t -> now - t > HEALTH_PRUNE_INTERVAL_MS);
            logic.retainHealth(id -> mc.level.getEntity(id) != null);
        }
    }

    void reset(String reason) {
        lastSwingMs.clear();
        lastAttackTargetId = -1;
        logic.reset(reason, now());
    }
}
