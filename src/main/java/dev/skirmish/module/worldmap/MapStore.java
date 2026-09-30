package dev.skirmish.module.worldmap;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skirmish.debug.DebugLog;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Map tiles of one server + dimension, saved as PNG files under {@code config/skirmish/map/<server>/<dimension>/}.
 * Tiles load on first use and the least recently used ones are saved and dropped beyond {@code maxTiles}.
 */
final class MapStore {
    private final Path dir;
    private final String key;
    private final Map<Long, MapTile> tiles = new HashMap<>();
    /** Tiles whose file could not be read (see {@link #setAside}). */
    private final Set<Long> broken = new HashSet<>();

    MapStore(Path root, String server, String dimension) {
        this.dir = root.resolve(MapShade.safe(server)).resolve(MapShade.safe(dimension));
        this.key = server + "|" + dimension;
    }

    String key() {
        return key;
    }

    private static long pack(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    private Path file(int rx, int rz) {
        return dir.resolve("r." + rx + "." + rz + ".png");
    }

    /** The tile, loaded from disk or created empty; null only if {@code create} is false and it does not exist. */
    @Nullable MapTile tile(int rx, int rz, boolean create, long now) {
        MapTile t = tiles.get(pack(rx, rz));
        if (t == null) {
            Path f = file(rx, rz);
            NativeImage image = null;
            if (!broken.contains(pack(rx, rz)) && Files.exists(f)) {
                try (InputStream in = Files.newInputStream(f)) {
                    image = NativeImage.read(in);
                    if (image.getWidth() != MapTile.SIZE || image.getHeight() != MapTile.SIZE) {
                        image.close();
                        image = null;
                        setAside(rx, rz, new IOException("size " + MapTile.SIZE + " expected"));
                    }
                } catch (IOException | RuntimeException e) {
                    setAside(rx, rz, e);
                }
            }
            if (image == null) {
                if (!create) {
                    return null;
                }
                image = new NativeImage(MapTile.SIZE, MapTile.SIZE, true);
            }
            t = new MapTile(rx, rz, image, key);
            tiles.put(pack(rx, rz), t);
        }
        t.lastUsed = now;
        return t;
    }

    /**
     * A tile file that can't be read (cut short when the game closed mid-save) is renamed to {@code .broken} and not
     * read again, so it is reported once instead of on every frame; the tile is drawn anew as the area is seen.
     */
    private void setAside(int rx, int rz, Exception why) {
        broken.add(pack(rx, rz));
        Path f = file(rx, rz);
        try {
            Files.move(f, f.resolveSibling(f.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            DebugLog.error("world_map", "tile unreadable, set aside as .broken: " + f, why);
        } catch (IOException | RuntimeException e) {
            DebugLog.error("world_map", "tile unreadable, skipped: " + f, why);
        }
    }

    /** Whether a tile exists in memory or on disk (so the screen doesn't create empty ones). */
    boolean exists(int rx, int rz) {
        return tiles.containsKey(pack(rx, rz)) || !broken.contains(pack(rx, rz)) && Files.exists(file(rx, rz));
    }

    /** Saves tiles changed since the last save. */
    int flush() {
        int saved = 0;
        for (MapTile t : tiles.values()) {
            if (t.unsaved) {
                try {
                    // Written aside and then moved in, so a save cut short never leaves a broken tile.
                    Files.createDirectories(dir);
                    Path f = file(t.rx, t.rz);
                    Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
                    t.image.writeToFile(tmp);
                    try {
                        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException e) {
                        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
                    }
                    broken.remove(pack(t.rx, t.rz));
                    t.unsaved = false;
                    saved++;
                } catch (IOException | RuntimeException e) {
                    DebugLog.error("world_map", "tile not saved: " + file(t.rx, t.rz), e);
                }
            }
        }
        return saved;
    }

    /** Saves and drops the least recently used tiles beyond {@code max}. */
    void trim(int max) {
        if (tiles.size() <= max) {
            return;
        }
        List<MapTile> byAge = new ArrayList<>(tiles.values());
        byAge.sort(Comparator.comparingLong(t -> t.lastUsed));
        flush();
        for (int i = 0; i < byAge.size() - max; i++) {
            MapTile t = byAge.get(i);
            tiles.remove(pack(t.rx, t.rz));
            t.close();
        }
    }

    void close() {
        flush();
        for (MapTile t : tiles.values()) {
            t.close();
        }
        tiles.clear();
    }
}
