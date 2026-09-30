package dev.skirmish.module.playerfx;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skirmish.fx.FxColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import java.util.EnumMap;
import java.util.Map;

/**
 * Cape designs drawn in code at twice the vanilla size (128×64; the model's UVs are relative, so the cape just looks
 * sharper). The whole cape box and the elytra area get the pattern, the two big faces also the emblem. Made once per
 * style on first use and kept as textures.
 */
final class CapeArt {
    public enum Style {
        OFF, SKIRMISH, GALAXY, FLAME, AURORA, VOID, GOLD, SAKURA, ICE
    }

    private static final int W = 128;
    private static final int H = 64;
    private static final Map<Style, Identifier> MADE = new EnumMap<>(Style.class);
    private static final String[] S_GLYPH = {" ### ", "#   #", "#    ", " ### ", "    #", "#   #", " ### "};

    private CapeArt() {
    }

    /** The texture of a style, drawing it the first time (render thread). */
    static Identifier texture(Style style) {
        return MADE.computeIfAbsent(style, s -> {
            NativeImage image = new NativeImage(W, H, true);
            paint(image, s);
            Identifier id = Identifier.fromNamespaceAndPath("skirmish", "cape/" + s.name().toLowerCase(java.util.Locale.ROOT));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Skirmish cape " + s, image));
            return id;
        });
    }

    private static void paint(NativeImage img, Style style) {
        RandomSource r = RandomSource.create(style.ordinal() * 7919L + 17);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                img.setPixel(x, y, 0);
            }
        }
        // Cape box (vanilla 0..22 × 0..17) and elytra (22..46 × 0..22), doubled.
        fill(img, style, r, 0, 0, 44, 34);
        fill(img, style, r, 44, 0, 92, 44);
        emblem(img, style, 2, 2);
        emblem(img, style, 24, 2);
    }

    private static void fill(NativeImage img, Style style, RandomSource r, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                float u = (x - x0) / (float) (x1 - x0);
                float v = (y - y0) / (float) (y1 - y0);
                img.setPixel(x, y, color(style, u, v, x, y, r));
            }
        }
    }

    private static int color(Style style, float u, float v, int x, int y, RandomSource r) {
        return switch (style) {
            case OFF -> 0;
            case SKIRMISH -> FxColor.lerp(0xFF8A6BFF, 0xFF24175E, v) | 0xFF000000;
            case GALAXY -> {
                int base = FxColor.lerp(0xFF0B0F2E, 0xFF3A1760, v);
                float neb = (float) (0.5 + 0.5 * Math.sin(u * 7 + v * 5) * Math.cos(v * 9 - u * 3));
                base = FxColor.lerp(base, 0xFF6B3FD4, neb * 0.35f);
                yield r.nextInt(38) == 0 ? FxColor.lerp(0xFFFFFFFF, 0xFFBFD8FF, r.nextFloat()) : base;
            }
            case FLAME -> {
                float tongue = (float) (Math.sin(u * 18 + v * 4) * 0.08 + Math.sin(u * 7) * 0.06);
                float k = Math.max(0f, Math.min(1f, 1f - v + tongue));
                int c = k > 0.66f ? FxColor.lerp(0xFFFF8A1E, 0xFFFFE066, (k - 0.66f) / 0.34f)
                        : k > 0.33f ? FxColor.lerp(0xFFC21807, 0xFFFF8A1E, (k - 0.33f) / 0.33f)
                        : FxColor.lerp(0xFF2A0505, 0xFFC21807, k / 0.33f);
                yield c | 0xFF000000;
            }
            case AURORA -> {
                int base = FxColor.lerp(0xFF04161E, 0xFF0A2A3A, v);
                float b1 = (float) Math.exp(-Math.pow((v - 0.35 - 0.12 * Math.sin(u * 6)) * 7, 2));
                float b2 = (float) Math.exp(-Math.pow((v - 0.62 - 0.1 * Math.cos(u * 5 + 1)) * 8, 2));
                base = FxColor.lerp(base, 0xFF3CFFB0, b1 * 0.85f);
                yield FxColor.lerp(base, 0xFFA66BFF, b2 * 0.75f) | 0xFF000000;
            }
            case VOID -> {
                float edge = Math.min(Math.min(u, 1 - u), Math.min(v, 1 - v));
                float glow = (float) Math.exp(-edge * 14);
                yield FxColor.lerp(0xFF050208, 0xFF8B2CFF, glow) | 0xFF000000;
            }
            case GOLD -> {
                int base = FxColor.lerp(0xFFFFD86B, 0xFFB47A12, v);
                boolean diamond = ((x + y) % 8 == 0) || ((x - y + 64) % 8 == 0);
                yield (diamond ? FxColor.lerp(base, 0xFF7A4E05, 0.45f) : base) | 0xFF000000;
            }
            case SAKURA -> {
                int base = FxColor.lerp(0xFFFFE3EE, 0xFFFF9EC4, v);
                yield r.nextInt(22) == 0 ? 0xFFFF5FA2 : r.nextInt(30) == 0 ? 0xFFFFFFFF : base;
            }
            case ICE -> {
                int base = FxColor.lerp(0xFFE6F8FF, 0xFF5AC8FA, v);
                boolean crack = Math.abs(((x * 3 + y * 2) % 23) - 11) < 1 || Math.abs(((x * 2 - y * 3 + 400) % 29) - 14) < 1;
                yield (crack ? 0xFFFFFFFF : base) | 0xFF000000;
            }
        };
    }

    /** The «S» mark on a face whose top-left (vanilla units, doubled here) is {@code fx, fy}; 20×32 px. */
    private static void emblem(NativeImage img, Style style, int fx, int fy) {
        if (style != Style.SKIRMISH && style != Style.VOID && style != Style.GOLD) {
            return;
        }
        int ink = style == Style.GOLD ? 0xFF6B4305 : style == Style.VOID ? 0xFFB98CFF : 0xFFFFFFFF;
        int scale = 3;
        int gx = fx + (20 - 5 * scale) / 2;
        int gy = fy + (32 - 7 * scale) / 2;
        for (int row = 0; row < S_GLYPH.length; row++) {
            for (int col = 0; col < 5; col++) {
                if (S_GLYPH[row].charAt(col) == '#') {
                    for (int dy = 0; dy < scale; dy++) {
                        for (int dx = 0; dx < scale; dx++) {
                            img.setPixel(gx + col * scale + dx, gy + row * scale + dy, ink);
                        }
                    }
                }
            }
        }
    }
}
