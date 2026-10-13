package dev.skirmish.fx;

/**
 * Sprites of the effect atlas ({@code textures/fx/atlas.png}), made by {@code tools/fx/atlas.py}: white shapes tinted
 * by the particle colour. Animated ones are frames side by side. Generated; edit the script, not this file.
 */
public enum FxSprite {
    MAGIC_CIRCLE(0, 0, 512, 512, 1),
    SLASH(512, 0, 256, 256, 1),
    SHOCKWAVE(768, 0, 256, 256, 1),
    HALO(1024, 0, 256, 256, 1),
    SPLAT(1280, 0, 256, 256, 1),
    STARBURST(1536, 0, 256, 256, 1),
    BEAM(1792, 0, 128, 256, 1),
    GLOW(1920, 0, 128, 128, 1),
    DUST(1920, 128, 128, 128, 1),
    FLARE(512, 256, 128, 128, 1),
    STAR4(640, 256, 128, 128, 1),
    STAR6(768, 256, 128, 128, 1),
    TWINKLE(896, 256, 128, 128, 1),
    SPARK(1024, 256, 128, 128, 1),
    RING(1152, 256, 128, 128, 1),
    THIN_RING(1280, 256, 128, 128, 1),
    FLAME(512, 384, 128, 128, 8),
    SOUL(0, 512, 128, 128, 6),
    SMOKE(1408, 256, 128, 128, 4),
    PETAL(1536, 384, 128, 128, 2),
    SNOWFLAKE(1792, 384, 128, 128, 2),
    HEART(1920, 256, 128, 128, 1),
    SHARD(768, 512, 128, 128, 4),
    DROP(1280, 512, 128, 128, 3),
    NOTE(1664, 512, 128, 128, 1),
    FEATHER(1792, 512, 128, 128, 1),
    BUBBLE(1920, 512, 128, 128, 1),
    EMBER(0, 640, 128, 128, 1),
    RIBBON(128, 640, 128, 128, 1),
    CONFETTI(256, 640, 128, 128, 1),
    RUNE(384, 640, 128, 128, 6),
    LEAF(1152, 640, 128, 128, 1),
    TOON_STAR(1280, 640, 128, 128, 1),
    CRACK(1408, 640, 256, 256, 1);

    public static final float SIZE = 2048f;
    public final float u0;
    public final float v0;
    public final float du;
    public final float dv;
    public final int frames;

    FxSprite(int x, int y, int w, int h, int frames) {
        this.u0 = x / SIZE;
        this.v0 = y / SIZE;
        this.du = w / SIZE;
        this.dv = h / SIZE;
        this.frames = frames;
    }
}
