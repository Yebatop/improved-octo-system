package dev.skirmish.module.analytics;

import dev.skirmish.combat.CombatListener;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.Combatant;
import dev.skirmish.combat.DamageInfo;
import dev.skirmish.combat.Fight;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared bookkeeping of the fight analytics modules, fed by one {@link CombatListener}: my attack attempts (for CPS
 * and the reach of each hit), arm swings of nearby players and the last known attacker of every victim (named by
 * the server, inferred by the combat tracker, or guessed from swings for other players on servers that name
 * nobody). Everything comes from packets the client already handled; nothing is sent.
 */
public final class AnalyticsHub {
    /** My click to the server's damage event on that target (round trip plus a tick or two). */
    public static final long ATTEMPT_TO_HIT_MS = 1_000;
    private static final long MEMORY_MS = 30_000;
    /** Same line format as the combat tracker reads (the chat is shown to the player by the server anyway). */
    private static final Pattern CHAT_KILLER = Pattern.compile("(?iu)убит[аы]?\\s+игроком\\s+([A-Za-z0-9_]{3,16})");
    private static final long CHAT_KILLER_MAX_AGE_MS = 5_000;
    private static @Nullable AnalyticsHub instance;

    /** A known or guessed attacker of a victim. */
    public record Attribution(UUID uuid, String name, int entityId, boolean self, boolean guessed, long timeMs) {
    }

    private record Attempt(long timeMs, double reach) {
    }

    private final List<BooleanSupplier> users = new CopyOnWriteArrayList<>();
    private final ClickWindow clicks = new ClickWindow(1_000);
    private final Map<Integer, Attempt> attempts = new HashMap<>();
    private final Map<Integer, Long> swings = new HashMap<>();
    private final Map<UUID, Attribution> attackers = new HashMap<>();
    /** Fight id → the opponent was seen visible at least once (fights against a player invisible throughout are not shown). */
    private final Map<Integer, Boolean> seenVisible = new HashMap<>();
    private @Nullable String chatKiller;
    private long chatKillerMs;
    private long lastPrune;
    private long lastTick;

    private AnalyticsHub() {
    }

    /** The hub; the first call subscribes it to the combat tracker. */
    public static AnalyticsHub get() {
        if (instance == null) {
            AnalyticsHub hub = new AnalyticsHub();
            instance = hub;
            CombatTracker.get().addListener(hub.new Listener());
            ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) -> hub.reset());
            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(hub::reset));
            ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
                if (!overlay) {
                    hub.onSystemChat(message.getString());
                }
            });
        }
        return instance;
    }

    /** HolyWorld death line «▶ Вы были убиты игроком Enemy_3 …»: the server itself names my killer there. */
    void onSystemChat(String text) {
        String name = chatKillerName(text);
        if (name != null) {
            chatKiller = name;
            chatKillerMs = System.currentTimeMillis();
        }
    }

    /** The killer named in a HolyWorld death line, or null. Pure. */
    public static @Nullable String chatKillerName(String text) {
        Matcher m = CHAT_KILLER.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /** My killer as named in chat by the server within the last few seconds, or null. */
    public @Nullable String chatKiller(long nowMs) {
        return chatKiller != null && Math.abs(nowMs - chatKillerMs) <= CHAT_KILLER_MAX_AGE_MS ? chatKiller : null;
    }

    /** Call from module ticks (cheap, runs once per tick): notes which fight opponents were seen visible. */
    public void tick() {
        long now = System.currentTimeMillis();
        if (now - lastTick < 40) {
            return;
        }
        lastTick = now;
        for (Fight fight : CombatTracker.get().activeFights()) {
            noteVisibility(fight);
        }
        if (seenVisible.size() > 64) {
            java.util.Set<Integer> keep = new java.util.HashSet<>();
            CombatTracker.get().activeFights().forEach(f -> keep.add(f.id()));
            CombatTracker.get().finishedFights().forEach(f -> keep.add(f.id()));
            seenVisible.keySet().retainAll(keep);
        }
    }

    private void noteVisibility(Fight fight) {
        if (Boolean.TRUE.equals(seenVisible.get(fight.id()))) {
            return;
        }
        Entity entity = find(fight.opponent().uuid(), fight.opponent().entityId());
        seenVisible.put(fight.id(), entity != null && !hidden(entity));
    }

    /**
     * Whether {@code who} may be named on screen: not while their loaded entity is invisible to me, unless the server
     * itself named them in chat as my killer just now.
     */
    public boolean mayShow(Combatant who, long nowMs) {
        if (who.self() || !hidden(who.uuid(), who.entityId())) {
            return true;
        }
        String named = chatKiller(nowMs);
        return named != null && named.equalsIgnoreCase(who.name());
    }

    /** Whether the opponent of {@code fight} was visible to me at some point of it. */
    public boolean seenVisible(Fight fight) {
        return Boolean.TRUE.equals(seenVisible.get(fight.id()));
    }

    /** Registers a module that needs the hub; the hub records only while at least one of them is enabled. */
    public void require(BooleanSupplier enabled) {
        users.add(enabled);
    }

    private boolean active() {
        for (BooleanSupplier user : users) {
            if (user.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    /** My attack attempts in the last second. */
    public int cps(long nowMs) {
        return clicks.count(nowMs);
    }

    public long lastClickMs() {
        return clicks.lastMs();
    }

    /** Reach of my latest attack attempt on {@code entityId} shortly before {@code timeMs}, NaN if none. */
    public double reachFor(int entityId, long timeMs) {
        Attempt attempt = attempts.get(entityId);
        if (attempt == null || timeMs - attempt.timeMs() > ATTEMPT_TO_HIT_MS || attempt.timeMs() - timeMs > 50) {
            return Double.NaN;
        }
        return attempt.reach();
    }

    /** Latest attacker of {@code victim} not older than {@code maxAgeMs}. */
    public @Nullable Attribution attackerOf(UUID victim, long nowMs, long maxAgeMs) {
        Attribution a = attackers.get(victim);
        return a == null || nowMs - a.timeMs() > maxAgeMs ? null : a;
    }

    /**
     * Whether {@code entity} is invisible to me. HolyWorld bans revealing invisible players "directly or indirectly",
     * so everything the analytics modules show about a nearby entity skips these.
     */
    public static boolean hidden(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && entity != mc.player && entity.isInvisibleTo(mc.player);
    }

    /** The loaded entity of {@code uuid} (players first), or null. */
    public static @Nullable Entity find(UUID uuid, int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        Entity byId = entityId >= 0 ? mc.level.getEntity(entityId) : null;
        if (byId != null && byId.getUUID().equals(uuid)) {
            return byId;
        }
        for (Player p : mc.level.players()) {
            if (p.getUUID().equals(uuid)) {
                return p;
            }
        }
        return null;
    }

    /** Whether the loaded entity of {@code uuid} is invisible to me (false when it is not loaded). */
    public static boolean hidden(UUID uuid, int entityId) {
        Entity entity = find(uuid, entityId);
        return entity != null && hidden(entity);
    }

    /** My eye to the target's hitbox, as the server checks melee reach; NaN without a loaded target. */
    public static double reachTo(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return Double.NaN;
        }
        Vec3 eye = mc.player.getEyePosition();
        AABB box = target.getBoundingBox();
        return ReachMath.distanceToBox(eye.x, eye.y, eye.z, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private void prune(long now) {
        if (now - lastPrune < 5_000) {
            return;
        }
        lastPrune = now;
        attempts.values().removeIf(a -> now - a.timeMs() > ATTEMPT_TO_HIT_MS * 5);
        swings.values().removeIf(t -> now - t > MEMORY_MS);
        attackers.values().removeIf(a -> now - a.timeMs() > MEMORY_MS);
    }

    /** Clears entity ids (world change or disconnect). */
    public void reset() {
        attempts.clear();
        swings.clear();
        attackers.clear();
        clicks.clear();
    }

    private final class Listener implements CombatListener {
        @Override
        public void onFightStart(Fight fight) {
            noteVisibility(fight);
        }

        @Override
        public void onAttackAttempt(int targetId) {
            if (!active()) {
                return;
            }
            long now = System.currentTimeMillis();
            clicks.click(now);
            Minecraft mc = Minecraft.getInstance();
            if (targetId >= 0 && mc.level != null) {
                Entity target = mc.level.getEntity(targetId);
                if (target != null) {
                    // Never measure anything about an invisible entity (HolyWorld: no invisibility indicators).
                    attempts.put(targetId, new Attempt(now, hidden(target) ? Double.NaN : reachTo(target)));
                }
            }
            prune(now);
        }

        @Override
        public void onSwing(Entity entity, InteractionHand hand) {
            if (entity instanceof Player && active()) {
                swings.put(entity.getId(), System.currentTimeMillis());
            }
        }

        @Override
        public void onDamage(DamageInfo info) {
            if (!active()) {
                return;
            }
            Combatant victim = info.victim();
            Combatant attacker = info.attacker();
            if (attacker != null) {
                if (!attacker.self() && hidden(attacker.uuid(), attacker.entityId())) {
                    attackers.remove(victim.uuid());
                    return;
                }
                attackers.put(victim.uuid(), new Attribution(attacker.uuid(), attacker.name(), attacker.entityId(), attacker.self(),
                        DamageTypes.inferred(info.damageType()), info.timeMs()));
            } else if (victim.player() && !victim.self()) {
                Attribution guess = guess(victim, info.timeMs());
                if (guess != null) {
                    attackers.put(victim.uuid(), guess);
                }
            }
        }
    }

    /** Anonymous damage on another player: the nearby player who swung last. */
    private @Nullable Attribution guess(Combatant victim, long now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        Entity victimEntity = mc.level.getEntity(victim.entityId());
        if (victimEntity == null) {
            return null;
        }
        List<KillerGuess.Candidate> candidates = new ArrayList<>();
        Map<UUID, AbstractClientPlayer> byUuid = new HashMap<>();
        for (AbstractClientPlayer other : mc.level.players()) {
            if (other.getId() == victim.entityId() || !other.isAlive() || hidden(other)) {
                continue;
            }
            Long swing = swings.get(other.getId());
            candidates.add(new KillerGuess.Candidate(other.getUUID(), other.getGameProfile().name(), other.distanceTo(victimEntity),
                    swing == null ? -1 : now - swing));
            byUuid.put(other.getUUID(), other);
        }
        KillerGuess.Candidate best = KillerGuess.best(candidates);
        if (best == null) {
            return null;
        }
        AbstractClientPlayer who = byUuid.get(best.uuid());
        boolean self = mc.player != null && who == mc.player;
        return new Attribution(best.uuid(), best.name(), who.getId(), self, true, now);
    }
}
