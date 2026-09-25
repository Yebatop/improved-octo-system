package dev.skirmish.module.runewindow;

import dev.skirmish.combat.CombatListener;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.Combatant;
import dev.skirmish.combat.Fight;
import dev.skirmish.combat.OwnDeath;
import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.gearinspector.holy.HolyGear;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.ui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * «Окно неуязвимости»: when my fight opponent pops a totem that carries the «Бессмертие» rune, a HUD chip counts
 * down the 3 s of invulnerability (wiki), so no hits are wasted. The rune is read from the totem the player held
 * right before the pop (sampled every tick, since the pop consumes it); an item that was never seen uses the
 * fallback length and is marked as a guess. A «Восстановление» rune shows a short «full HP» notice instead.
 * Everything comes from what the client already shows (the item in hand, the pop animation); players invisible to
 * me are neither sampled nor shown.
 */
public final class RuneWindowModule extends Module {
    public static final String ID = "rune_window";
    /** Hands are sampled for players this close (a bit beyond melee and bow range in a fight). */
    private static final double SAMPLE_RANGE = 48;

    final BoolSetting useFallback = add(new BoolSetting("use_fallback", true));
    final NumberSetting fallback = (NumberSetting) add(new NumberSetting("fallback", RuneEffect.IMMORTALITY_SECONDS, 0.5, 10, 0.5).unit(" s"))
            .under(useFallback).visibleWhen(useFallback::get);
    final BoolSetting restored = add(new BoolSetting("restored", true));
    final BoolSetting self = add(new BoolSetting("self", false));
    final BoolSetting endSound = add(new BoolSetting("end_sound", true));
    final NumberSetting volume = (NumberSetting) add(new NumberSetting("volume", 60, 5, 100, 5).unit("%"))
            .under(endSound).visibleWhen(endSound::get);

    final RuneTimers timers = new RuneTimers();
    private final HandCache<RuneEffect> hands = new HandCache<>();
    /** Last parsed stack per player, so lore is parsed only when the item changes. */
    private final Map<UUID, ItemStack> lastStack = new HashMap<>();
    private final Map<UUID, RuneEffect> lastEffect = new HashMap<>();

    public RuneWindowModule() {
        super(ID, true);
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
        Hud.get().register(new RuneWindowHud(this));
        CombatTracker.get().addListener(new Listener());
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(this::reset));
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        timers.clear();
        hands.clear();
        lastStack.clear();
        lastEffect.clear();
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Set<UUID> present = new HashSet<>();
        for (AbstractClientPlayer player : mc.level.players()) {
            boolean me = player == mc.player;
            if (!me && (hidden(player) || player.distanceToSqr(mc.player) > SAMPLE_RANGE * SAMPLE_RANGE)) {
                continue;
            }
            present.add(player.getUUID());
            hands.update(player.getUUID(), read(player), now);
        }
        hands.retain(present);
        lastStack.keySet().retainAll(present);
        lastEffect.keySet().retainAll(present);

        // Never keep a countdown over a player who turned invisible.
        for (RuneTimers.Timer t : timers.live()) {
            Entity e = mc.level.getEntity(t.entityId());
            if (e != null && e != mc.player && hidden(e)) {
                timers.remove(t.uuid());
            }
        }
        List<RuneTimers.Timer> ended = timers.expire(now);
        if (endSound.get() && ended.stream().anyMatch(t -> t.kind() == RuneEffect.Kind.INVULNERABLE)) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f,
                    (float) (volume.get() / 100.0)));
        }
    }

    /** The totem in the hands (main hand first, like vanilla), classified; null without one. */
    private @Nullable RuneEffect read(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.has(DataComponents.DEATH_PROTECTION)) {
            stack = player.getOffhandItem();
            if (!stack.has(DataComponents.DEATH_PROTECTION)) {
                return null;
            }
        }
        UUID id = player.getUUID();
        ItemStack previous = lastStack.get(id);
        RuneEffect cached = lastEffect.get(id);
        if (previous != null && cached != null && ItemStack.isSameItemSameComponents(previous, stack)) {
            return cached;
        }
        RuneEffect effect = RuneEffect.of(HolyGear.customName(stack), HolyGear.lore(stack));
        lastStack.put(id, stack.copy());
        lastEffect.put(id, effect);
        if (isDebug()) {
            log("%s holds %s \"%s\" %s → %s", player.getGameProfile().name(), stack.getItem(), HolyGear.customName(stack),
                    HolyGear.lore(stack), effect);
        }
        return effect;
    }

    private static boolean hidden(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && entity != mc.player && entity.isInvisibleTo(mc.player);
    }

    /** What the pop of {@code who} grants, with the fallback applied. */
    private RuneEffect effectOf(Combatant who, long now) {
        RuneEffect effect = hands.popped(who.uuid(), now);
        if (effect == null) {
            effect = RuneEffect.UNKNOWN;
        }
        if (effect.kind() == RuneEffect.Kind.UNKNOWN && useFallback.get()) {
            return RuneEffect.fallback(fallback.get());
        }
        return effect;
    }

    private final class Listener implements CombatListener {
        @Override
        public void onTotemPop(Combatant entity, @Nullable Fight fight) {
            if (!isEnabled()) {
                return;
            }
            if (entity.self() ? !self.get() : fight == null || !entity.player()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            Entity e = mc.level == null ? null : mc.level.getEntity(entity.entityId());
            if (e != null && hidden(e)) {
                return;
            }
            long now = System.currentTimeMillis();
            RuneEffect effect = effectOf(entity, now);
            log("%s popped a totem: %s", entity.name(), effect);
            switch (effect.kind()) {
                case INVULNERABLE -> timers.start(new RuneTimers.Timer(entity.uuid(), entity.entityId(), entity.name(),
                        RuneEffect.Kind.INVULNERABLE, now, Math.round(effect.seconds() * 1000), effect.guessed()));
                case RESTORED -> {
                    if (restored.get()) {
                        timers.start(new RuneTimers.Timer(entity.uuid(), entity.entityId(), entity.name(), RuneEffect.Kind.RESTORED,
                                now, Math.round(Theme.get().num("layout.rune_window.restored_ms")), false));
                    }
                }
                default -> timers.remove(entity.uuid());
            }
        }

        @Override
        public void onEntityDeath(Combatant entity, @Nullable Fight fight) {
            timers.remove(entity.uuid());
        }

        @Override
        public void onFightEnd(Fight fight) {
            if (fight.endReason() != null && fight.endReason() != dev.skirmish.combat.FightEndReason.TIMEOUT) {
                timers.remove(fight.opponent().uuid());
            }
        }

        @Override
        public void onOwnDeath(OwnDeath death) {
            timers.clear();
        }
    }
}
