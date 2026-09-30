package dev.skirmish.module.damagenumbers;

import dev.skirmish.combat.CombatListener;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.Combatant;
import dev.skirmish.combat.DamageInfo;
import dev.skirmish.combat.Fight;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.ui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * «Цифры урона»: floating numbers over entities near me whose health (plus absorption) changes: damage in white,
 * crits in yellow, the drop of a totem pop in its own color, heals in green. Read from the health the server syncs
 * anyway (the same data as the HP indicators); drawn in the world with depth test, so never through blocks.
 * Entities invisible to me get no numbers at all (HolyWorld bans invisibility indicators). "Only my hits" uses the
 * combat tracker's attribution of damage to me (including its inference on servers that send no attacker).
 */
public final class DamageNumbersModule extends Module {
    public static final String ID = "damage_numbers";
    /** Entities farther away get no numbers. */
    static final double RANGE = 32;
    /** Changes of one entity this close together add up into one number. */
    static final long DAMAGE_MERGE_MS = 150;
    static final long HEAL_MERGE_MS = 1_500;

    final NumberSetting scale = add(new NumberSetting("scale", 1.0, 0.5, 2.5, 0.1).unit("×"));
    final NumberSetting lifetime = add(new NumberSetting("lifetime", 1.2, 0.5, 3.0, 0.1).unit(" s"));
    final BoolSetting onlyMine = add(new BoolSetting("only_mine", true));
    final BoolSetting heals = add(new BoolSetting("heals", true));
    final BoolSetting players = add(new BoolSetting("players", true));
    final BoolSetting mobs = add(new BoolSetting("mobs", true));

    final NumberField field = new NumberField();
    private final DropRules rules = new DropRules();
    private final Map<Integer, Long> myHits = new HashMap<>();
    private final Map<Integer, Long> crits = new HashMap<>();
    private final Map<Integer, Long> totems = new HashMap<>();
    private final Random random = new Random();
    private int pruneTicks;

    public DamageNumbersModule() {
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
        CombatTracker.get().addListener(new Listener());
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            if (isEnabled() && !field.isEmpty()) {
                NumberRenderer.render(this, context);
            }
        });
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(this::reset));
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        field.clear();
        rules.clear();
        myHits.clear();
        crits.clear();
        totems.clear();
    }

    long lifetimeMs() {
        return Math.round(lifetime.get() * 1000);
    }

    private static boolean hidden(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && entity.isInvisibleTo(mc.player);
    }

    private static long time(Map<Integer, Long> map, int id) {
        Long t = map.get(id);
        return t == null ? -1 : t;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        field.prune(now, lifetimeMs());
        if (mc.player == null || mc.level == null) {
            return;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == mc.player) {
                continue;
            }
            int id = living.getId();
            boolean player = living instanceof Player;
            if (hidden(living) || (player ? !players.get() : !mobs.get()) || living.distanceToSqr(mc.player) > RANGE * RANGE) {
                // Forget the baseline: no number may later reveal what changed while it was hidden.
                rules.forget(id);
                field.removeEntity(id);
                continue;
            }
            float delta = rules.sample(id, living.getHealth() + living.getAbsorptionAmount());
            if (Float.isNaN(delta) || delta == 0f) {
                continue;
            }
            if (delta < 0 && DropRules.showDrop(onlyMine.get(), now, time(myHits, id))) {
                spawn(living, DropRules.kindOfDrop(now, time(crits, id), time(totems, id)), -delta, now, DAMAGE_MERGE_MS);
            } else if (delta > 0 && DropRules.showHeal(heals.get(), onlyMine.get(), now, time(myHits, id))) {
                spawn(living, NumberField.Kind.HEAL, delta, now, HEAL_MERGE_MS);
            }
        }
        if (++pruneTicks >= 20) {
            pruneTicks = 0;
            rules.retain(id -> mc.level.getEntity(id) != null);
            myHits.values().removeIf(t -> now - t > DropRules.MY_TARGET_MS);
            crits.values().removeIf(t -> now - t > DropRules.TOTEM_WINDOW_MS);
            totems.values().removeIf(t -> now - t > DropRules.TOTEM_WINDOW_MS);
        }
    }

    private void spawn(LivingEntity entity, NumberField.Kind kind, double amount, long now, long mergeMs) {
        double spread = Theme.get().num("layout.damage_numbers.spread");
        double x = entity.getX() + (random.nextDouble() - 0.5) * spread;
        double z = entity.getZ() + (random.nextDouble() - 0.5) * spread;
        double y = entity.getY() + entity.getBbHeight() + Theme.get().num("layout.damage_numbers.lift");
        NumberField.Num n = field.add(entity.getId(), kind, amount, x, y, z, now, mergeMs);
        log("%s %s %s", entity.getName().getString(), kind, n.text());
    }

    private final class Listener implements CombatListener {
        @Override
        public void onDamage(DamageInfo info) {
            if (isEnabled() && info.byMe()) {
                myHits.put(info.victim().entityId(), info.timeMs());
            }
        }

        @Override
        public void onCrit(Entity target, boolean magic) {
            if (!isEnabled() || magic) {
                return;
            }
            long now = System.currentTimeMillis();
            crits.put(target.getId(), now);
            field.upgrade(target.getId(), NumberField.Kind.CRIT, now, DropRules.CRIT_WINDOW_MS);
        }

        @Override
        public void onTotemPop(Combatant entity, @Nullable Fight fight) {
            if (!isEnabled()) {
                return;
            }
            long now = System.currentTimeMillis();
            totems.put(entity.entityId(), now);
            field.upgrade(entity.entityId(), NumberField.Kind.TOTEM, now, DropRules.TOTEM_WINDOW_MS);
        }
    }
}
