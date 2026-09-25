package dev.skirmish.module.friends;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * The player under the crosshair for the «добавить/убрать друга» key: a local ray from the camera that stops at the
 * first block (never through walls) and ignores invisible, spectating and dead players.
 */
public final class PlayerPick {
    private PlayerPick() {
    }

    public static @Nullable Player find(Minecraft mc, double maxDistance) {
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
            if (candidate == self || candidate == camera || candidate.isRemoved() || !candidate.isAlive()
                    || candidate.isSpectator() || candidate.isInvisibleTo(self)) {
                continue;
            }
            AABB box = candidate.getBoundingBox().inflate(candidate.getPickRadius());
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
        if (bestDistance > 0) {
            HitResult block = camera.pick(bestDistance, 1.0F, false);
            if (block.getType() != HitResult.Type.MISS && block.getLocation().distanceTo(eye) < bestDistance - 1e-3) {
                return null;
            }
        }
        return best;
    }
}
