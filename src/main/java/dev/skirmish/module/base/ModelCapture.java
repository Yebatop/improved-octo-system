package dev.skirmish.module.base;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the base region into a {@link ModelRaster.Scene}: every block state once as its baked model's quads with
 * their textures' pixels and biome tint, water and lava as boxes of their height, chests (drawn by the game as
 * entities) as boxes with the chest's own texture, other entity-drawn blocks as their shape with their particle
 * texture. Also the faces neighbours cover and the light levels. Client thread; loaded chunks only.
 */
final class ModelCapture {
    private static final Direction[] SIDES = Direction.values();

    private final Minecraft mc = Minecraft.getInstance();
    private final Level level;
    private final List<ModelRaster.Kind> kinds = new ArrayList<>();
    private final List<ModelRaster.Tex> textures = new ArrayList<>();
    private final Map<Object, Integer> kindIds = new HashMap<>();
    private final Map<Identifier, Integer> texIds = new HashMap<>();
    private final Map<String, int @Nullable []> chestTex = new HashMap<>();
    private final RandomSource random = RandomSource.create();

    /** A fluid kind: the state and its surface height in sixteenths. */
    private record FluidKey(BlockState state, int height) {
    }

    private ModelCapture(Level level) {
        this.level = level;
    }

    static ModelRaster.Scene capture(Level level, BaseData.Region r) {
        return new ModelCapture(level).read(r);
    }

    private ModelRaster.Scene read(BaseData.Region r) {
        int sx = r.sizeX();
        int sy = r.sizeY();
        int sz = r.sizeZ();
        int n = sx * sy * sz;
        int[] cells = new int[n];
        byte[] light = new byte[n];
        BlockState[] states = new BlockState[n];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int i = (y * sz + z) * sx + x;
                    pos.set(r.x0 + x, r.y0 + y, r.z0 + z);
                    if (!level.isLoaded(pos)) {
                        light[i] = 15;
                        continue;
                    }
                    light[i] = (byte) Math.max(level.getBrightness(LightLayer.SKY, pos), level.getBrightness(LightLayer.BLOCK, pos));
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    states[i] = state;
                    int kind = kindOf(state, pos.immutable());
                    if (kind >= 0) {
                        cells[i] = kind + 1;
                    }
                }
            }
        }
        // Faces covered by a neighbour inside the region (the region's sides stay open, like a cut-away).
        byte[] hidden = new byte[n];
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int i = (y * sz + z) * sx + x;
                    BlockState state = states[i];
                    if (cells[i] == 0 || state == null) {
                        continue;
                    }
                    boolean fluid = state.getBlock() instanceof LiquidBlock;
                    int mask = 0;
                    for (Direction d : SIDES) {
                        int nx = x + d.getStepX();
                        int ny = y + d.getStepY();
                        int nz = z + d.getStepZ();
                        if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) {
                            continue;
                        }
                        BlockState next = states[(ny * sz + nz) * sx + nx];
                        if (next == null) {
                            continue;
                        }
                        boolean covered = fluid
                                ? next.getFluidState().getType().isSame(state.getFluidState().getType()) || next.isSolidRender()
                                : !Block.shouldRenderFace(state, next, d);
                        if (covered) {
                            mask |= 1 << d.get3DDataValue();
                        }
                    }
                    hidden[i] = (byte) mask;
                }
            }
        }
        return new ModelRaster.Scene(sx, sy, sz, cells, hidden, light, List.copyOf(kinds), List.copyOf(textures));
    }

    /** The kind index of a block (built on first sight), or −1 for one with nothing to draw. */
    private int kindOf(BlockState state, BlockPos pos) {
        Object key = state;
        if (state.getBlock() instanceof LiquidBlock) {
            key = new FluidKey(state, Math.max(2, Math.min(16, Math.round(state.getFluidState().getHeight(level, pos) * 16))));
        }
        Integer known = kindIds.get(key);
        if (known != null) {
            return known;
        }
        ModelRaster.Kind kind = key instanceof FluidKey f ? fluid(state, pos, f.height() / 16f) : build(state, pos);
        int id = -1;
        if (kind != null && kind.quads().length > 0) {
            id = kinds.size();
            kinds.add(kind);
        }
        kindIds.put(key, id);
        return id;
    }

    private ModelRaster.@Nullable Kind build(BlockState state, BlockPos pos) {
        Block block = state.getBlock();
        boolean glow = state.getLightEmission() > 0;
        if (block instanceof AbstractChestBlock<?>) {
            ModelRaster.Kind chest = chest(state, pos);
            if (chest != null) {
                return chest;
            }
        }
        BlockStateModel model = mc.getBlockRenderer().getBlockModel(state);
        random.setSeed(42L);
        List<BlockModelPart> parts = model.collectParts(random);
        List<ModelRaster.Quad> quads = new ArrayList<>();
        for (BlockModelPart part : parts) {
            boolean ao = part.useAmbientOcclusion();
            for (BakedQuad q : part.getQuads(null)) {
                quads.add(quad(q, null, state, pos, ao));
            }
            for (Direction d : SIDES) {
                for (BakedQuad q : part.getQuads(d)) {
                    quads.add(quad(q, d, state, pos, ao));
                }
            }
        }
        if (quads.isEmpty()) {
            // Signs, beds, heads, shulker boxes…: drawn by the game as entities; here their shape with their texture.
            if (!state.hasBlockEntity()) {
                return null;
            }
            VoxelShape shape = state.getShape(level, pos);
            if (shape.isEmpty()) {
                return null;
            }
            AABB b = shape.bounds();
            int t = tex(model.particleIcon());
            int[] faces = {t, t, t, t, t, t};
            return new ModelRaster.Kind(ModelRaster.box((float) b.minX, (float) b.minY, (float) b.minZ, (float) b.maxX, (float) b.maxY,
                    (float) b.maxZ, faces, 0xFFFFFF, true), false, glow);
        }
        return new ModelRaster.Kind(quads.toArray(ModelRaster.Quad[]::new), state.isSolidRender(), glow);
    }

    private ModelRaster.Quad quad(BakedQuad q, @Nullable Direction cull, BlockState state, BlockPos pos, boolean ao) {
        TextureAtlasSprite sprite = q.sprite();
        float u0 = sprite.getU0();
        float du = sprite.getU1() - u0;
        float v0 = sprite.getV0();
        float dv = sprite.getV1() - v0;
        float[] xyz = new float[12];
        float[] uv = new float[8];
        for (int i = 0; i < 4; i++) {
            Vector3fc p = q.position(i);
            xyz[i * 3] = p.x();
            xyz[i * 3 + 1] = p.y();
            xyz[i * 3 + 2] = p.z();
            long packed = q.packedUV(i);
            uv[i * 2] = du == 0f ? 0f : (UVPair.unpackU(packed) - u0) / du;
            uv[i * 2 + 1] = dv == 0f ? 0f : (UVPair.unpackV(packed) - v0) / dv;
        }
        int tint = 0xFFFFFF;
        if (q.isTinted()) {
            int c = mc.getBlockColors().getColor(state, level, pos, q.tintIndex());
            if (c != -1) {
                tint = c & 0xFFFFFF;
            }
        }
        return new ModelRaster.Quad(xyz, uv, tex(sprite), tint, q.direction().get3DDataValue(), cull == null ? -1 : cull.get3DDataValue(),
                q.shade(), ao && state.getLightEmission() == 0);
    }

    /** Water or lava: a box up to the surface with the still texture, water in the biome's colour. */
    private ModelRaster.Kind fluid(BlockState state, BlockPos pos, float height) {
        FluidState fluid = state.getFluidState();
        int t = tex(mc.getBlockRenderer().getBlockModel(state).particleIcon());
        int tint = fluid.is(FluidTags.WATER) ? BiomeColors.getAverageWaterColor(level, pos) & 0xFFFFFF : 0xFFFFFF;
        ModelRaster.Quad[] quads = ModelRaster.box(0f, 0f, 0f, 1f, height, 1f, new int[]{t, t, t, t, t, t}, tint, false);
        // Every face of a fluid is hidden by the same fluid or a solid block next to it, the surface included.
        for (int f = 0; f < quads.length; f++) {
            ModelRaster.Quad q = quads[f];
            quads[f] = new ModelRaster.Quad(q.xyz(), q.uv(), q.tex(), q.tint(), q.face(), f, q.shade(), false);
        }
        return new ModelRaster.Kind(quads, false, state.getLightEmission() > 0);
    }

    /** A chest as its box: lid on top, the lock on the front, plank sides, from the chest's entity texture. */
    private ModelRaster.@Nullable Kind chest(BlockState state, BlockPos pos) {
        String name = BaseText.chestTexture(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
        int[] tex = chestTex.computeIfAbsent(name, this::loadChest);
        if (tex == null) {
            return null;
        }
        Direction facing = state.getValueOrElse(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        int[] faces = new int[6];
        for (int f = 0; f < 6; f++) {
            faces[f] = f == ModelRaster.UP ? tex[0] : f == ModelRaster.DOWN ? tex[1] : f == facing.get3DDataValue() ? tex[2] : tex[3];
        }
        VoxelShape shape = state.getShape(level, pos);
        AABB b = shape.isEmpty() ? new AABB(1 / 16.0, 0, 1 / 16.0, 15 / 16.0, 14 / 16.0, 15 / 16.0) : shape.bounds();
        return new ModelRaster.Kind(ModelRaster.box((float) b.minX, (float) b.minY, (float) b.minZ, (float) b.maxX, (float) b.maxY,
                (float) b.maxZ, faces, 0xFFFFFF, true), false, false);
    }

    /**
     * Cuts the chest's 64×64 entity texture into block faces: [top, bottom, front, side]. The texture is stored
     * upside down; a side is the lid strip over the base strip, the front also gets the lock.
     */
    private int @Nullable [] loadChest(String name) {
        Optional<Resource> res = mc.getResourceManager().getResource(Identifier.withDefaultNamespace("textures/entity/chest/" + name + ".png"));
        if (res.isEmpty()) {
            return null;
        }
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            int s = img.getWidth() / 64;
            if (s < 1 || img.getHeight() < 64 * s) {
                return null;
            }
            int top = add(region(img, s, 28, 0, false));
            int bottom = add(region(img, s, 14, 19, false));
            int[] front = strip(img, s, 42);
            // The lock (2×4 on the texture's corner) sits in the middle, across the lid's edge.
            int size = 14 * s;
            for (int y = 0; y < 4 * s; y++) {
                for (int x = 0; x < 2 * s; x++) {
                    int c = img.getPixel(s + x, s + y);
                    if ((c >>> 24) > 0) {
                        front[(3 * s + y) * size + 6 * s + x] = c | 0xFF000000;
                    }
                }
            }
            int frontId = add(ModelRaster.Tex.of(size, size, front));
            int side = add(ModelRaster.Tex.of(size, size, strip(img, s, 0)));
            return new int[]{top, bottom, frontId, side};
        } catch (Exception e) {
            return null;
        }
    }

    /** A 14×14 square of the texture from (u, v). */
    private static ModelRaster.Tex region(NativeImage img, int s, int u, int v, boolean flip) {
        int size = 14 * s;
        int[] px = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                px[y * size + x] = img.getPixel(u * s + x, v * s + (flip ? size - 1 - y : y)) | 0xFF000000;
            }
        }
        return ModelRaster.Tex.of(size, size, px);
    }

    /** A side of the chest from column {@code u}: the lid strip (5 rows) over the base strip (9 of its 10 rows), flipped. */
    private static int[] strip(NativeImage img, int s, int u) {
        int size = 14 * s;
        int[] px = new int[size * size];
        for (int y = 0; y < size; y++) {
            int row = y / s;
            int sub = s - 1 - y % s;
            int src = row < 5 ? 18 - row : 42 - (row - 5);
            for (int x = 0; x < size; x++) {
                px[y * size + x] = img.getPixel(u * s + x, src * s + sub) | 0xFF000000;
            }
        }
        return px;
    }

    private int add(ModelRaster.Tex tex) {
        textures.add(tex);
        return textures.size() - 1;
    }

    /** A block texture's first frame, read once. */
    private int tex(TextureAtlasSprite sprite) {
        SpriteContents contents = sprite.contents();
        Identifier name = contents.name();
        Integer known = texIds.get(name);
        if (known != null) {
            return known;
        }
        int w = Math.max(1, contents.width());
        int h = Math.max(1, contents.height());
        int[] px = dev.skirmish.util.SpritePixels.firstFrame(contents);
        int id = add(ModelRaster.Tex.of(w, h, px));
        texIds.put(name, id);
        return id;
    }
}
