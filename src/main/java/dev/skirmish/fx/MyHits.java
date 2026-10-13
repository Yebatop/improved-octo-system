package dev.skirmish.fx;

import dev.skirmish.combat.CombatListener;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.DamageInfo;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * My confirmed hits, each once, with whether it was a critical hit: the server's damage event names me as the
 * attacker, and its crit particles on the same target in the same tick mark it critical. Collected over a tick and
 * handed out at its end, so the crit is known whichever packet came first. Hits on something invisible to me are
 * left out.
 */
public final class MyHits {
    private static final List<BiConsumer<Entity, Boolean>> LISTENERS = new ArrayList<>();
    private static final Map<Integer, Boolean> PENDING = new LinkedHashMap<>();
    private static final Map<Integer, Long> CRITS = new HashMap<>();
    private static boolean installed;

    private MyHits() {
    }

    /** {@code listener} gets (victim, critical) for each hit. */
    public static void listen(BiConsumer<Entity, Boolean> listener) {
        install();
        LISTENERS.add(listener);
    }

    private static void install() {
        if (installed) {
            return;
        }
        installed = true;
        CombatTracker.get().addListener(new CombatListener() {
            @Override
            public void onDamage(DamageInfo info) {
                if (info.byMe() && !info.onMe()) {
                    PENDING.putIfAbsent(info.victim().entityId(), false);
                }
            }

            @Override
            public void onCrit(Entity target, boolean magic) {
                if (!magic) {
                    CRITS.put(target.getId(), Util.getMillis());
                }
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> flush());
    }

    private static void flush() {
        long now = Util.getMillis();
        CRITS.values().removeIf(at -> now - at > 250);
        if (PENDING.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        List<Integer> ids = new ArrayList<>(PENDING.keySet());
        PENDING.clear();
        if (mc.level == null || mc.player == null) {
            return;
        }
        for (int id : ids) {
            Entity victim = mc.level.getEntity(id);
            if (victim == null || victim.isInvisibleTo(mc.player)) {
                continue;
            }
            boolean crit = CRITS.remove(id) != null;
            for (BiConsumer<Entity, Boolean> l : LISTENERS) {
                l.accept(victim, crit);
            }
        }
    }
}
