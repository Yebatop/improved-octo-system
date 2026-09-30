package dev.skirmish.module.analytics.feed;

import dev.skirmish.combat.CombatListener;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.Combatant;
import dev.skirmish.combat.Fight;
import dev.skirmish.combat.OwnDeath;
import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.analytics.AnalyticsHub;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * «Лента убийств»: deaths of players around me (killer → victim with the killer's weapon when seen), my own
 * death (killer from the hits or the server's chat line), optional totem pops; rows fade after a few seconds.
 * For others the killer is the attacker the server named, the combat tracker inferred, or the player next to the
 * victim whose swing was last; "?" when nothing points at anyone. Players invisible to me never appear (neither as
 * victim nor as killer), except a killer the server names in chat. Read and render only.
 */
public final class KillFeedModule extends Module {
    public static final String ID = "kill_feed";
    /** A death counts for the last attacker at most this long after the hit. */
    private static final long KILL_ATTRIBUTION_MS = 10_000;

    final NumberSetting duration = add(new NumberSetting("duration", 8, 3, 30, 1).unit(" s"));
    final NumberSetting maxRows = add(new NumberSetting("max_rows", 5, 1, 10, 1));
    final BoolSetting totems = add(new BoolSetting("totems", true));
    final BoolSetting weapons = add(new BoolSetting("weapons", true));

    final KillFeed<ItemStack> feed = new KillFeed<>();

    public KillFeedModule() {
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
        AnalyticsHub.get().require(this::isEnabled);
        Hud.get().register(new KillFeedHud(this));
        CombatTracker.get().addListener(new Listener());
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) -> feed.clear());
    }

    @Override
    protected void onDisable() {
        feed.clear();
    }

    @Override
    public void tick() {
        AnalyticsHub.get().tick();
        feed.prune(System.currentTimeMillis(), lifetimeMs());
    }

    long lifetimeMs() {
        return Math.round(duration.get() * 1000);
    }

    List<KillFeed.Entry<ItemStack>> live(long now) {
        return feed.live(now, lifetimeMs(), maxRows.getInt());
    }

    private @Nullable ItemStack weaponOf(Combatant who) {
        if (!weapons.get()) {
            return null;
        }
        Entity entity = AnalyticsHub.find(who.uuid(), who.entityId());
        if (who.self()) {
            entity = Minecraft.getInstance().player;
        }
        if (!(entity instanceof LivingEntity living) || AnalyticsHub.hidden(living)) {
            return null;
        }
        ItemStack held = living.getMainHandItem();
        return held.isEmpty() ? null : held.copy();
    }

    private final class Listener implements CombatListener {
        @Override
        public void onEntityDeath(Combatant entity, @Nullable Fight fight) {
            if (!isEnabled() || !entity.player() || AnalyticsHub.hidden(entity.uuid(), entity.entityId())) {
                return;
            }
            long now = System.currentTimeMillis();
            AnalyticsHub.Attribution by = AnalyticsHub.get().attackerOf(entity.uuid(), now, KILL_ATTRIBUTION_MS);
            String killer = null;
            ItemStack weapon = null;
            boolean me = false;
            if (by != null && !AnalyticsHub.hidden(by.uuid(), by.entityId())) {
                Combatant who = new Combatant(by.entityId(), by.uuid(), by.name(), true, by.self());
                killer = by.name();
                me = by.self();
                weapon = weaponOf(who);
            }
            feed.add(KillFeed.Kind.KILL, entity.uuid(), entity.name(), false, killer, me, weapon, now);
            log("death of %s, killer %s%s", entity.name(), killer == null ? "?" : killer, by != null && by.guessed() ? " (inferred)" : "");
        }

        @Override
        public void onKill(Fight fight) {
            if (!isEnabled()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            Combatant me = CombatTracker.combatant(mc.player);
            if (!feed.setKiller(fight.opponent().uuid(), me.name(), true, weaponOf(me), System.currentTimeMillis())) {
                log("kill of %s had no feed row (invisible or not a player)", fight.opponent().name());
            }
        }

        @Override
        public void onOwnDeath(OwnDeath death) {
            Minecraft mc = Minecraft.getInstance();
            if (!isEnabled() || mc.player == null) {
                return;
            }
            AnalyticsHub hub = AnalyticsHub.get();
            Combatant killer = death.killer();
            String name = null;
            ItemStack weapon = null;
            if (killer != null && hub.mayShow(killer, death.timeMs())) {
                name = killer.name();
                weapon = weaponOf(killer);
            } else if (hub.chatKiller(death.timeMs()) != null) {
                name = hub.chatKiller(death.timeMs());
            }
            feed.add(KillFeed.Kind.KILL, mc.player.getUUID(), mc.player.getGameProfile().name(), true, name, false, weapon, death.timeMs());
        }

        @Override
        public void onTotemPop(Combatant entity, @Nullable Fight fight) {
            if (!isEnabled() || !totems.get() || !entity.player() || !entity.self() && AnalyticsHub.hidden(entity.uuid(), entity.entityId())) {
                return;
            }
            feed.add(KillFeed.Kind.TOTEM, entity.uuid(), entity.name(), entity.self(), null, false, null, System.currentTimeMillis());
        }
    }
}
