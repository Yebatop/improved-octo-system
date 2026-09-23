package dev.skirmish.module.killcam;

/** Replay cameras (menu and replay bar order); also the values of the {@code default_camera} setting. */
public enum CameraMode {
    /** Free flight: movement keys, jump/sneak for up/down, drag to look. */
    FREE,
    /** First-person view from the killer's eyes (their head yaw and pitch). */
    KILLER,
    /** Orbit around the killer (around me when the killer is not recorded): drag to rotate, wheel to zoom. */
    ORBIT;

    CameraMode next() {
        CameraMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
