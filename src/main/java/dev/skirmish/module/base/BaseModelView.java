package dev.skirmish.module.base;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The 3D model of the base: the region's blocks read once into a voxel grid (map colours), drawn by
 * {@link VoxelRaster} on a worker thread whenever the view changes, and shown as a texture. Drag turns it, the wheel
 * zooms, the slider cuts the top layers off to look inside.
 */
final class BaseModelView {
    static final int SIZE = 512;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Skirmish base model");
        t.setDaemon(true);
        return t;
    });

    private final Identifier id = Identifier.fromNamespaceAndPath("skirmish", "base_os/model");
    private @Nullable NativeImage image;
    private @Nullable DynamicTexture texture;
    private VoxelRaster.@Nullable Grid grid;
    private int blocks;
    float yaw = 0.75f;
    float pitch = 0.55f;
    float zoom = 1f;
    /** Highest layer drawn (grid y). */
    int cut = Integer.MAX_VALUE;
    private volatile int @Nullable [] pending;
    private volatile boolean busy;
    private String shown = "";

    /** Reads the region's blocks (loaded chunks only; unloaded ones stay empty). Client thread. */
    void capture(Level level, BaseData.Region r) {
        int sx = r.sizeX();
        int sy = r.sizeY();
        int sz = r.sizeZ();
        int[] colors = new int[sx * sy * sz];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int count = 0;
        int accent = dev.skirmish.ui.Theme.get().color("accent") | 0xFF000000;
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    pos.set(r.x0 + x, r.y0 + y, r.z0 + z);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    MapColor color = state.getMapColor(level, pos);
                    if (color == MapColor.NONE) {
                        continue;
                    }
                    boolean regionBlock = !r.manual && pos.getX() == r.x && pos.getY() == r.y && pos.getZ() == r.z;
                    colors[(y * sz + z) * sx + x] = regionBlock ? accent : color.calculateARGBColor(MapColor.Brightness.HIGH) | 0xFF000000;
                    count++;
                }
            }
        }
        grid = new VoxelRaster.Grid(sx, sy, sz, colors);
        blocks = count;
        cut = Math.min(cut, sy - 1);
        shown = "";
    }

    boolean captured() {
        return grid != null;
    }

    int blocks() {
        return blocks;
    }

    int layers() {
        VoxelRaster.Grid g = grid;
        return g == null ? 0 : g.sy();
    }

    /** The texture to draw, starting a new render when the view changed. Render thread. */
    Identifier texture() {
        VoxelRaster.Grid g = grid;
        String view = yaw + "|" + pitch + "|" + zoom + "|" + cut + "|" + System.identityHashCode(g);
        if (g != null && !busy && !view.equals(shown)) {
            busy = true;
            shown = view;
            float y = yaw;
            float p = pitch;
            float z = zoom;
            int c = cut;
            WORKER.execute(() -> {
                try {
                    pending = VoxelRaster.render(g, y, p, z, c, SIZE, SIZE);
                } finally {
                    busy = false;
                }
            });
        }
        if (image == null) {
            image = new NativeImage(SIZE, SIZE, true);
            texture = new DynamicTexture(() -> "Skirmish base model", image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
        int[] px = pending;
        if (px != null && texture != null) {
            pending = null;
            for (int yy = 0; yy < SIZE; yy++) {
                for (int xx = 0; xx < SIZE; xx++) {
                    image.setPixel(xx, yy, px[yy * SIZE + xx]);
                }
            }
            texture.upload();
        }
        return id;
    }

    void close() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(id);
            texture = null;
            image = null;
        }
    }
}
