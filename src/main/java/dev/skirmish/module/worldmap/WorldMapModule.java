package dev.skirmish.module.worldmap;

import dev.skirmish.SkirmishClient;
import dev.skirmish.SkirmishKeys;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.menus.SkirmishPauseScreen;
import dev.skirmish.setting.KeySetting;
import dev.skirmish.ui.Theme;
import dev.skirmish.util.ServerContext;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * «World Map»: a full-screen map (key M) of the surface you have loaded, coloured and shaded like vanilla map items,
 * with your waypoints and your own position. It is saved per server and dimension. Only the top surface is drawn —
 * no caves, no players or mobs — and dimensions with a roof (the Nether) are not mapped. Read-only; Feature Control
 * id {@code world_map}.
 */
public final class WorldMapModule extends Module {
    public static final String ID = "world_map";
    static final String L = "layout.worldmap.";
    private static volatile @Nullable WorldMapModule instance;

    final KeySetting key = add(new KeySetting("key", "key.skirmish.world_map"));

    private @Nullable MapStore store;
    private final ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private int ticks;
    private long lastSave = Util.getMillis();

    public WorldMapModule() {
        super(ID, true);
    }

    public static @Nullable WorldMapModule instance() {
        return instance;
    }

    @Override
    public Category category() {
        return Category.WORLD;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        instance = this;
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (isEnabled()) {
                enqueue(chunk.getPos());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> closeStore());
        SkirmishPauseScreen.registerShortcut(new SkirmishPauseScreen.Shortcut("skirmish.worldmap.title",
                WorldMapScreen::new, this::isEnabled));
    }

    @Override
    protected void onDisable() {
        closeStore();
    }

    private void closeStore() {
        if (store != null) {
            int saved = store.flush();
            log("saved %d tile(s) of %s", saved, store.key());
            store.close();
            store = null;
        }
        queue.clear();
        queued.clear();
    }

    private void enqueue(ChunkPos pos) {
        if (queued.add(pos.toLong())) {
            queue.add(pos);
        }
    }

    /** The store of the current server and dimension (switching when either changes), or null without a world. */
    @Nullable MapStore store() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        String server = ServerContext.serverKey();
        String dim = ServerContext.dimension();
        String key = server + "|" + dim;
        if (store == null || !store.key().equals(key)) {
            if (store != null) {
                closeStore();
            }
            store = new MapStore(SkirmishClient.configDir().resolve("map"), server, dim);
            log("map context %s", key);
        }
        return store;
    }

    /** Whether this dimension is mapped (no roof). */
    static boolean mapped(ClientLevel level) {
        return !level.dimensionType().hasCeiling();
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        while (SkirmishKeys.WORLD_MAP.consumeClick()) {
            if (mc.screen == null && mc.level != null) {
                mc.setScreen(new WorldMapScreen(null));
            }
        }
        if (mc.level == null || mc.player == null) {
            return;
        }
        MapStore s = store();
        if (s == null || !mapped(mc.level)) {
            queue.clear();
            queued.clear();
            return;
        }
        ticks++;
        Theme t = Theme.get();
        if (ticks % t.integer(L + "rescan_ticks") == 0) {
            ChunkPos here = mc.player.chunkPosition();
            int r = t.integer(L + "rescan_radius");
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    enqueue(new ChunkPos(here.x + dx, here.z + dz));
                }
            }
        }
        long now = Util.getMillis();
        int budget = t.integer(L + "chunks_per_tick");
        while (budget-- > 0 && !queue.isEmpty()) {
            ChunkPos pos = queue.poll();
            queued.remove(pos.toLong());
            LevelChunk chunk = mc.level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
            if (chunk != null) {
                scan(mc.level, chunk, s, now);
            }
        }
        if (now - lastSave > t.num(L + "save_ms")) {
            lastSave = now;
            s.flush();
            s.trim(t.integer(L + "max_tiles"));
        }
    }

    /** Colours one chunk's 16×16 columns into its tile, like a vanilla map. */
    static void scan(ClientLevel level, LevelChunk chunk, MapStore store, long now) {
        ChunkPos cp = chunk.getPos();
        MapTile tile = store.tile(MapShade.tileOf(cp.x), MapShade.tileOf(cp.z), true, now);
        if (tile == null) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinY();
        boolean northLoaded = level.getChunkSource().getChunk(cp.x, cp.z - 1, ChunkStatus.FULL, false) != null;
        for (int x = 0; x < 16; x++) {
            int wx = cp.getMinBlockX() + x;
            int north = northLoaded ? level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, cp.getMinBlockZ() - 1) - 1 : Integer.MIN_VALUE;
            for (int z = 0; z < 16; z++) {
                int wz = cp.getMinBlockZ() + z;
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int argb = 0;
                int height = y;
                if (y >= minY) {
                    pos.set(wx, y, wz);
                    BlockState state = chunk.getBlockState(pos);
                    MapColor color = state.getMapColor(level, pos);
                    int down = 0;
                    while (color == MapColor.NONE && y - down > minY && down < 8) {
                        down++;
                        pos.set(wx, y - down, wz);
                        state = chunk.getBlockState(pos);
                        color = state.getMapColor(level, pos);
                    }
                    height = y - down;
                    FluidState fluid = state.getFluidState();
                    int shade;
                    if (!fluid.isEmpty() && fluid.getType().isSame(Fluids.WATER)) {
                        int depth = 1;
                        while (depth < 16) {
                            pos.set(wx, height - depth, wz);
                            if (!chunk.getBlockState(pos).getFluidState().getType().isSame(Fluids.WATER)) {
                                break;
                            }
                            depth++;
                        }
                        shade = MapShade.water(depth, wx, wz);
                    } else {
                        shade = MapShade.land(height, north == Integer.MIN_VALUE ? height : north);
                    }
                    if (color != MapColor.NONE) {
                        argb = color.calculateARGBColor(MapColor.Brightness.byId(shade));
                    }
                }
                tile.set(MapShade.pixelOf(wx), MapShade.pixelOf(wz), argb);
                north = height;
            }
        }
    }
}
