package dev.skirmish.module.base;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The 3D model of the base: the region's blocks read once ({@link ModelCapture}), drawn by {@link ModelRaster} on a
 * worker thread whenever the view changes (quickly while it moves, smooth once it stops) and shown as a texture the
 * size of its place on screen. Drag turns it, right-drag moves it, the wheel zooms, the slider cuts the top layers
 * off to look inside.
 */
final class BaseModelView {
    /** Ready-made views (yaw, pitch): from the south-east, from above with north up, from the south. */
    static final float[][] PRESETS = {{(float) Math.PI + 0.785f, 0.62f}, {(float) Math.PI, 1.5f}, {(float) Math.PI, 0.08f}};
    private static final int MAX_SIZE = 1200;
    private static final long SETTLE_MS = 160;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Skirmish base model");
        t.setDaemon(true);
        return t;
    });

    private final Identifier id = Identifier.fromNamespaceAndPath("skirmish", "base_os/model");
    private @Nullable NativeImage image;
    private @Nullable DynamicTexture texture;
    private int texW;
    private int texH;
    private ModelRaster.@Nullable Scene scene;
    private int blocks;
    float yaw = PRESETS[0][0];
    float pitch = PRESETS[0][1];
    float zoom = 1f;
    float panX;
    float panY;
    /** Highest layer drawn (grid y). */
    int cut = Integer.MAX_VALUE;
    private volatile ModelRaster.@Nullable Frame pending;
    private ModelRaster.@Nullable Frame shown;
    private volatile boolean busy;
    private String requested = "";
    private String lastView = "";
    private long lastChange;

    /** Reads the region's blocks (loaded chunks only; unloaded ones stay empty). Client thread. */
    void capture(Level level, BaseData.Region r) {
        ModelRaster.Scene s = ModelCapture.capture(level, r);
        int count = 0;
        for (int c : s.cells()) {
            if (c != 0) {
                count++;
            }
        }
        scene = s;
        blocks = count;
        cut = Math.min(cut, s.sy() - 1);
        requested = "";
    }

    boolean captured() {
        return scene != null;
    }

    int blocks() {
        return blocks;
    }

    int layers() {
        ModelRaster.Scene s = scene;
        return s == null ? 0 : s.sy();
    }

    void preset(int i) {
        yaw = PRESETS[i][0];
        pitch = PRESETS[i][1];
        zoom = 1f;
        panX = 0f;
        panY = 0f;
    }

    /** The picture now on the texture (its view places the markers), or null before the first one. */
    ModelRaster.@Nullable Frame frame() {
        return shown;
    }

    /** The texture for a {@code w}×{@code h} picture, starting a new render when the view changed. Render thread. */
    Identifier texture(int w, int h, ModelRaster.Look look) {
        w = Math.max(16, Math.min(MAX_SIZE, w));
        h = Math.max(16, Math.min(MAX_SIZE, h));
        ModelRaster.Scene s = scene;
        long now = Util.getMillis();
        String view = yaw + "|" + pitch + "|" + zoom + "|" + panX + "|" + panY + "|" + cut + "|" + w + "x" + h + "|"
                + System.identityHashCode(s) + "|" + look;
        if (!view.equals(lastView)) {
            lastView = view;
            lastChange = now;
        }
        boolean still = now - lastChange > SETTLE_MS;
        String want = view + (still ? "|smooth" : "|fast");
        if (s != null && !busy && !want.equals(requested)) {
            busy = true;
            requested = want;
            float yy = yaw;
            float pp = pitch;
            float zz = zoom;
            float px = panX;
            float py = panY;
            int c = cut;
            int fw = w;
            int fh = h;
            int ss = still ? 3 : 2;
            WORKER.execute(() -> {
                try {
                    pending = ModelRaster.render(s, yy, pp, zz, px, py, c, fw, fh, ss, look);
                } finally {
                    busy = false;
                }
            });
        }
        ModelRaster.Frame f = pending;
        if (f != null) {
            pending = null;
            if (image == null || texW != f.w() || texH != f.h()) {
                close();
                texW = f.w();
                texH = f.h();
                image = new NativeImage(texW, texH, false);
                texture = new DynamicTexture(() -> "Skirmish base model", image);
                Minecraft.getInstance().getTextureManager().register(id, texture);
            }
            int[] px = f.argb();
            for (int y = 0; y < texH; y++) {
                for (int x = 0; x < texW; x++) {
                    image.setPixel(x, y, px[y * texW + x]);
                }
            }
            texture.upload();
            shown = f;
        }
        return id;
    }

    boolean ready() {
        return texture != null && shown != null;
    }

    void close() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(id);
            texture = null;
            image = null;
        }
    }
}
