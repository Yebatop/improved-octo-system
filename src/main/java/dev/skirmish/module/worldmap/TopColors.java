package dev.skirmish.module.worldmap;

import dev.skirmish.util.SpritePixels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The colour a block shows from above on the map: the average of its top texture (what the game draws), tinted by
 * the biome where the block is tinted (grass, leaves, water). Computed once per block state. Client thread.
 */
final class TopColors {
    /** Average RGB of the top texture and the tint index (−1: not tinted). */
    private record Top(int rgb, int tint) {
    }

    private final Map<BlockState, Top> cache = new HashMap<>();
    private final RandomSource random = RandomSource.create();

    /** Opaque ARGB of {@code state} at {@code pos}, as seen from above. */
    int color(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        Top top = cache.computeIfAbsent(state, this::top);
        int rgb = top.rgb() >= 0 ? top.rgb() : state.getMapColor(level, pos).col;
        if (top.tint() >= 0) {
            int tint = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, top.tint());
            if (tint != -1) {
                rgb = MapShade.multiply(rgb, tint);
            }
        }
        return 0xFF000000 | rgb;
    }

    private Top top(BlockState state) {
        BlockStateModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        random.setSeed(42L);
        BakedQuad best = null;
        for (BlockModelPart part : model.collectParts(random)) {
            best = pick(best, part.getQuads(Direction.UP));
            best = pick(best, part.getQuads(null));
        }
        TextureAtlasSprite sprite = best != null ? best.sprite() : model.particleIcon();
        // −1 (all see-through): the block's map colour is used instead.
        int rgb = SpritePixels.average(SpritePixels.firstFrame(sprite.contents()));
        // Water has no quads; the game tints it by the biome (tint index 0), lava is not tinted.
        int tint = best != null && best.isTinted() ? best.tintIndex() : state.getBlock() instanceof LiquidBlock ? 0 : -1;
        return new Top(rgb, tint);
    }

    /** The first quad facing up (the top), preferring one that is tinted (the grass over the dirt). */
    private static BakedQuad pick(BakedQuad best, List<BakedQuad> quads) {
        for (BakedQuad q : quads) {
            if (q.direction() != Direction.UP) {
                continue;
            }
            if (best == null || !best.isTinted() && q.isTinted()) {
                best = q;
            }
        }
        return best;
    }

    void clear() {
        cache.clear();
    }
}
