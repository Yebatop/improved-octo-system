package dev.skirmish.module.gearinspector;

import dev.skirmish.combat.EquipmentSnapshot;
import dev.skirmish.gui.Texts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Which player the panel shows: the locked one, otherwise the one under the crosshair (kept for the linger time after
 * looking away). Runs on the client tick; also writes the target and per-slot diagnostics to debug.log on change.
 */
final class TargetTracker {
    private final GearInspectorModule module;
    private @Nullable ClientLevel level;
    private @Nullable Player locked;
    private @Nullable Player aimed;
    private @Nullable Player recent;
    private long recentUntilMs;
    private @Nullable Player shown;
    private final Map<EquipmentSlot, String> loggedSlots = new EnumMap<>(EquipmentSlot.class);
    private @Nullable Boolean loggedAllEmpty;

    TargetTracker(GearInspectorModule module) {
        this.module = module;
    }

    @Nullable Player shown() {
        return shown;
    }

    boolean isLocked() {
        return locked != null && locked == shown;
    }

    void tick(Minecraft mc) {
        ClientLevel currentLevel = mc.level;
        if (currentLevel != level) {
            level = currentLevel;
            reset("world changed");
        }
        Player self = mc.player;
        if (currentLevel == null || self == null) {
            return;
        }

        double maxDistance = module.maxDistance.get();
        TargetFinder.Hit hit = TargetFinder.find(mc, maxDistance, module.hitboxMargin.get());
        aimed = hit == null ? null : hit.player();
        long now = Util.getMillis();
        String dropReason = null;
        if (aimed != null) {
            recent = aimed;
            recentUntilMs = now + Math.round(module.linger.get() * 1000);
        } else if (recent != null) {
            String invalid = invalidReason(recent, self, maxDistance);
            if (invalid != null) {
                dropReason = invalid;
                recent = null;
            } else if (now >= recentUntilMs) {
                dropReason = "not under the crosshair for " + module.linger.format();
                recent = null;
            }
        }

        while (module.lockKey().consumeClick()) {
            toggleLock(mc, self);
        }

        Player lockedNow = locked;
        if (lockedNow != null) {
            String lost = invalidReason(lockedNow, self, lockRange());
            if (lost != null) {
                module.log("lock lost: %s (%s)", name(lockedNow), lost);
                message(mc, Component.translatable("skirmish.gearinspector.msg.lock_lost", name(lockedNow)));
                locked = null;
                dropReason = lost;
            }
        }

        Player next = locked != null ? locked : recent;
        if (next != shown) {
            if (shown != null) {
                module.log("target lost: %s (%s)", name(shown), next != null ? "switched to " + name(next)
                        : dropReason != null ? dropReason : "lock released");
            }
            if (next != null) {
                module.log("target acquired: %s at %s m (%s)", name(next), GearFormat.distance(self.distanceTo(next)),
                        next == locked ? "locked" : "crosshair");
            }
            shown = next;
            loggedSlots.clear();
            loggedAllEmpty = null;
        }
        if (shown != null && module.isDebug()) {
            logSlots(shown);
        }
    }

    private void toggleLock(Minecraft mc, Player self) {
        Player current = locked;
        if (current != null) {
            module.log("lock released by key: %s", name(current));
            message(mc, Component.translatable("skirmish.gearinspector.msg.unlocked", name(current)));
            locked = null;
            return;
        }
        Player candidate = aimed != null ? aimed : recent;
        if (candidate == null) {
            module.log("lock key pressed, but no player under the crosshair within %s", module.maxDistance.format());
            message(mc, Component.translatable("skirmish.gearinspector.msg.no_target",
                    module.maxDistance.formatValue() + " " + Texts.unit("m")));
            return;
        }
        locked = candidate;
        module.log("target locked: %s at %s m (lock kept up to %s m)", name(candidate),
                GearFormat.distance(self.distanceTo(candidate)), GearFormat.distance(lockRange()));
        message(mc, Component.translatable("skirmish.gearinspector.msg.locked", name(candidate)));
    }

    /** The lock never drops closer than the aiming distance, whatever the lock range slider says. */
    private double lockRange() {
        return Math.max(module.lockDistance.get(), module.maxDistance.get());
    }

    private @Nullable String invalidReason(Player target, Player self, double range) {
        if (target.isRemoved() || level == null || level.getEntity(target.getId()) != target) {
            return "removed from the world (disconnected, respawned or left tracking range)";
        }
        if (!target.isAlive()) {
            return "died";
        }
        double distance = self.distanceTo(target);
        if (distance > range) {
            return "out of range " + GearFormat.distance(distance) + " m > " + GearFormat.distance(range) + " m";
        }
        return null;
    }

    void reset(String reason) {
        if (locked != null) {
            module.log("lock cleared: %s (%s)", name(locked), reason);
        }
        if (shown != null) {
            module.log("target lost: %s (%s)", name(shown), reason);
        }
        locked = null;
        aimed = null;
        recent = null;
        shown = null;
        loggedSlots.clear();
        loggedAllEmpty = null;
    }

    private void logSlots(Player target) {
        boolean assume = module.assumeUndamaged.get();
        boolean holy = module.holyActive();
        boolean allEmpty = true;
        for (EquipmentSlot slot : EquipmentSnapshot.SLOTS) {
            var stack = target.getItemBySlot(slot);
            allEmpty &= stack.isEmpty();
            String description = GearReader.describe(slot, stack, assume, holy);
            if (!description.equals(loggedSlots.put(slot, description))) {
                module.log("slot %s of %s: %s", GearReader.slotName(slot), name(target), description);
            }
        }
        if (loggedAllEmpty == null || loggedAllEmpty != allEmpty) {
            loggedAllEmpty = allEmpty;
            if (allEmpty) {
                module.log("%s: all six slots empty, panel shows \"no data\" everywhere (no equipment received)", name(target));
            }
        }
    }

    private void message(Minecraft mc, Component text) {
        if (module.lockMessages.get() && mc.player != null) {
            mc.player.displayClientMessage(text, true);
        }
    }

    static String name(Player player) {
        return player.getGameProfile().name();
    }
}
