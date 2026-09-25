package dev.skirmish.module.gearinspector;

import dev.skirmish.combat.EquipmentSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Local raycast from the camera over the players the client knows about. The vanilla crosshair target is limited
 * to the interaction range, so this uses its own ray of the configured length.
 */
final class TargetFinder {
    record Hit(Player player, double distance) {
    }

    private TargetFinder() {
    }

    static @Nullable Hit find(Minecraft mc, double maxDistance, double margin) {
        Entity camera = mc.getCameraEntity();
        ClientLevel level = mc.level;
        Player self = mc.player;
        if (camera == null || level == null || self == null) {
            return null;
        }
        Vec3 eye = camera.getEyePosition(1.0F);
        Vec3 end = eye.add(camera.getViewVector(1.0F).scale(maxDistance));

        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AbstractClientPlayer candidate : level.players()) {
            if (candidate == self || candidate == camera || !isInspectable(candidate, self)) {
                continue;
            }
            AABB box = candidate.getBoundingBox().inflate(candidate.getPickRadius() + margin);
            double distance;
            if (box.contains(eye)) {
                distance = 0;
            } else {
                Optional<Vec3> hit = box.clip(eye, end);
                if (hit.isEmpty()) {
                    continue;
                }
                distance = eye.distanceTo(hit.get());
            }
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return null;
        }
        // Never through blocks (HolyWorld rule 2.4): a player behind a wall is not picked.
        if (bestDistance > 0) {
            HitResult block = camera.pick(bestDistance, 1.0F, false);
            if (block.getType() != HitResult.Type.MISS && block.getLocation().distanceTo(eye) < bestDistance - 1e-3) {
                return null;
            }
        }
        return new Hit(best, bestDistance);
    }

    /**
     * Spectators and dead players are skipped. An invisible player only counts when some equipment is visible on
     * them, so the panel never reveals a fully invisible player.
     */
    static boolean isInspectable(Player candidate, Player viewer) {
        if (candidate.isRemoved() || !candidate.isAlive() || candidate.isSpectator()) {
            return false;
        }
        if (!candidate.isInvisibleTo(viewer)) {
            return true;
        }
        for (EquipmentSlot slot : EquipmentSnapshot.SLOTS) {
            if (!candidate.getItemBySlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
