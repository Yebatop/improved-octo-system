package dev.skirmish.module.killcard;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/** Turns {@link IconSource} pixels into an image. Pure Java2D, runs on the card thread. */
public final class IconComposer {
    private static final int GLINT = 0x9C5CFF;

    private IconComposer() {
    }

    /**
     * Flat icons come back at texture resolution (the caller scales them with nearest neighbour);
     * cubes are drawn directly at {@code cubeSize} pixels.
     */
    public static BufferedImage compose(IconSource source, boolean foil, int cubeSize) {
        BufferedImage image = switch (source) {
            case IconSource.Flat flat -> flat(flat);
            case IconSource.Cube cube -> cube(cube, Math.max(8, cubeSize));
        };
        if (foil) {
            applyGlint(image);
        }
        return image;
    }

    private static BufferedImage flat(IconSource.Flat flat) {
        int size = 1;
        for (IconSource.Layer layer : flat.layers()) {
            size = Math.max(size, Math.max(layer.width(), layer.height()));
        }
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (IconSource.Layer layer : flat.layers()) {
            g.drawImage(toImage(layer, 1f), 0, 0, size, size, null);
        }
        g.dispose();
        return out;
    }

    private static BufferedImage cube(IconSource.Cube cube, int s) {
        BufferedImage out = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        float h = s / 2f;
        float q = s / 4f;
        // Each face: origin, u axis (texture x), v axis (texture y) of the parallelogram.
        face(g, toImage(cube.top(), 1f), h, 0, h, q, -h, q);
        face(g, toImage(cube.left(), 0.78f), 0, q, h, q, 0, h);
        face(g, toImage(cube.right(), 0.6f), h, h, h, -q, 0, h);
        g.dispose();
        return out;
    }

    private static void face(Graphics2D g, BufferedImage texture, float ox, float oy, float ux, float uy, float vx, float vy) {
        int w = texture.getWidth();
        int h = texture.getHeight();
        AffineTransform transform = new AffineTransform(ux / w, uy / w, vx / h, vy / h, ox, oy);
        g.drawImage(texture, transform, null);
    }

    /** Layer pixels with the tint multiplied in and RGB scaled by {@code shade}. */
    static BufferedImage toImage(IconSource.Layer layer, float shade) {
        int w = layer.width();
        int h = layer.height();
        int[] pixels = new int[w * h];
        int tint = layer.tint();
        float ta = ((tint >>> 24) & 0xFF) / 255f;
        float tr = ((tint >> 16) & 0xFF) / 255f * shade;
        float tg = ((tint >> 8) & 0xFF) / 255f * shade;
        float tb = (tint & 0xFF) / 255f * shade;
        int[] src = layer.argb();
        for (int i = 0; i < pixels.length; i++) {
            int p = src[i];
            int a = Math.round(((p >>> 24) & 0xFF) * ta);
            int r = Math.round(((p >> 16) & 0xFF) * tr);
            int gr = Math.round(((p >> 8) & 0xFF) * tg);
            int b = Math.round((p & 0xFF) * tb);
            pixels[i] = (a << 24) | (clamp(r) << 16) | (clamp(gr) << 8) | clamp(b);
        }
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, w, h, pixels, 0, w);
        return image;
    }

    /** Static stand-in for the animated enchantment glint: a purple sheen with a diagonal highlight. */
    static void applyGlint(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int gr = (GLINT >> 16) & 0xFF;
        int gg = (GLINT >> 8) & 0xFF;
        int gb = GLINT & 0xFF;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = image.getRGB(x, y);
                int a = p >>> 24;
                if (a == 0) {
                    continue;
                }
                float diagonal = ((x + y) / (float) (w + h) * 3f) % 1f;
                float k = 0.22f + (Math.abs(diagonal - 0.5f) < 0.12f ? 0.2f : 0f);
                int r = Math.round(((p >> 16) & 0xFF) * (1 - k) + gr * k);
                int g = Math.round(((p >> 8) & 0xFF) * (1 - k) + gg * k);
                int b = Math.round((p & 0xFF) * (1 - k) + gb * k);
                image.setRGB(x, y, (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b));
            }
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
