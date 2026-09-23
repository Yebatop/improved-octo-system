package dev.skirmish.module.killcam;

/**
 * One player's state in one tick, as plain fields. Reused by the recorder (fill, then {@link ReplayBuffer#write})
 * and by playback ({@link ReplayBuffer#sample}), so neither allocates per tick. No Minecraft types: the
 * entity flags are the raw synced bytes, the pose is {@code Pose.id()}.
 */
public final class TrackSample {
    /** {@link #anim} bit: arm swing in progress. */
    public static final int ANIM_SWINGING = 1;
    /** {@link #anim} bit: the swinging arm is the off hand. */
    public static final int ANIM_OFF_HAND = 2;
    /** {@link #anim} bit: on ground. */
    public static final int ANIM_ON_GROUND = 4;

    public double x;
    public double y;
    public double z;
    public float headYaw;
    public float bodyYaw;
    public float pitch;
    /** WalkAnimationState position and speed (limb swing phase and amplitude). */
    public float walkPos;
    public float walkSpeed;
    /** LivingEntity.attackAnim in [0, 1). */
    public float attackAnim;
    public float health;
    public float absorption;
    /** Entity shared flags byte (fire, sneak, sprint, swim, invisible, glowing, elytra). */
    public byte sharedFlags;
    /** LivingEntity flags byte (using item, off hand, spin attack). */
    public byte livingFlags;
    public byte pose;
    public byte hurtTime;
    public byte deathTime;
    public byte anim;
    public short useTicks;

    public void copyFrom(TrackSample other) {
        x = other.x;
        y = other.y;
        z = other.z;
        headYaw = other.headYaw;
        bodyYaw = other.bodyYaw;
        pitch = other.pitch;
        walkPos = other.walkPos;
        walkSpeed = other.walkSpeed;
        attackAnim = other.attackAnim;
        health = other.health;
        absorption = other.absorption;
        sharedFlags = other.sharedFlags;
        livingFlags = other.livingFlags;
        pose = other.pose;
        hurtTime = other.hurtTime;
        deathTime = other.deathTime;
        anim = other.anim;
        useTicks = other.useTicks;
    }

    public boolean swinging() {
        return (anim & ANIM_SWINGING) != 0;
    }

    public boolean swingOffHand() {
        return (anim & ANIM_OFF_HAND) != 0;
    }
}
