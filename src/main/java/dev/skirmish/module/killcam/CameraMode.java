package dev.skirmish.module.killcam;

/** Replay cameras; also the values of the {@code default_camera} setting. */
public enum CameraMode {
    /** First-person view from the killer's eyes (their head yaw and pitch). */
    KILLER,
    /** Orbit around the killer: drag to rotate, wheel to zoom. */
    ORBIT_KILLER,
    /** Orbit around me (the victim). */
    ORBIT_VICTIM,
    /** Free flight: movement keys, jump/sneak for up/down, drag to look. */
    FREE;

    CameraMode next() {
        CameraMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
