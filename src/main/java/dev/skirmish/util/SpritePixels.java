package dev.skirmish.util;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skirmish.mixin.SpriteContentsAccessor;
import net.minecraft.client.renderer.texture.SpriteContents;

/** The pixels of a block texture's first frame (ARGB, row by row), read from the sprite the game keeps. */
public final class SpritePixels {
    private SpritePixels() {
    }

    /** {@code width × height} ARGB pixels; mid grey when the image cannot be read. */
    public static int[] firstFrame(SpriteContents contents) {
        int w = Math.max(1, contents.width());
        int h = Math.max(1, contents.height());
        int[] px = new int[w * h];
        try {
            NativeImage img = ((SpriteContentsAccessor) contents).skirmish$originalImage();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    px[y * w + x] = img.getPixel(x, y);
                }
            }
        } catch (RuntimeException e) {
            java.util.Arrays.fill(px, 0xFF7F7F7F);
        }
        return px;
    }

    /** The average colour (RGB) of the pixels that are not see-through, or −1 when all are. */
    public static int average(int[] argb) {
        long r = 0;
        long g = 0;
        long b = 0;
        long n = 0;
        for (int p : argb) {
            int a = p >>> 24;
            if (a < 128) {
                continue;
            }
            r += (p >> 16) & 0xFF;
            g += (p >> 8) & 0xFF;
            b += p & 0xFF;
            n++;
        }
        if (n == 0) {
            return -1;
        }
        return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }
}
