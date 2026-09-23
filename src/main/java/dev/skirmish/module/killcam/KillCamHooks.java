package dev.skirmish.module.killcam;

import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/** Entry points for the KillCam mixins (they live in another package and must not see the session internals). */
public final class KillCamHooks {
    private static final double[] POSE = new double[5];

    private KillCamHooks() {
    }

    public static boolean active() {
        return ReplaySession.isActive();
    }

    /**
     * Advances the replay for this frame and returns the camera {x, y, z, yaw, pitch} (a shared array), or null
     * when no replay runs.
     */
    public static double @Nullable [] frame(float partialTick) {
        ReplaySession session = ReplaySession.current();
        if (session == null) {
            return null;
        }
        try {
            session.onFrame(partialTick);
        } catch (Throwable t) {
            session.module.error("replay frame failed, replay stopped", t);
            session.stop("error in frame update: " + t, ReplaySession.SCREEN_KEEP);
            return null;
        }
        POSE[0] = session.camX;
        POSE[1] = session.camY;
        POSE[2] = session.camZ;
        POSE[3] = session.camYaw;
        POSE[4] = session.camPitch;
        return POSE;
    }

    public static boolean hides(Entity entity) {
        ReplaySession session = ReplaySession.current();
        return session != null && session.hides(entity);
    }
}
