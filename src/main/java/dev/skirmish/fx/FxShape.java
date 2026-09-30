package dev.skirmish.fx;

/** How an effect particle is drawn. Glowing shapes add light (additive), the others are painted over (alpha). */
public enum FxShape {
    /** A soft round glow, bright in the middle. */
    GLOW(true),
    /** A small crisp dot with a soft rim. */
    DOT(true),
    /** A streak along its motion, bright head and fading tail. */
    SPARK(true),
    /** A four-pointed star with a glowing core. */
    STAR(true),
    /** A flat ring lying on the ground (it grows with {@code size}). */
    RING(true),
    /** A ring facing the camera. */
    FACE_RING(true),
    /** A tumbling triangle, painted (glass, crystals, petals). */
    SHARD(false),
    /** A tumbling square, painted (confetti). */
    SQUARE(false),
    /** A large soft puff, painted (smoke, mist). */
    SMOKE(false);

    public final boolean additive;

    FxShape(boolean additive) {
        this.additive = additive;
    }
}
