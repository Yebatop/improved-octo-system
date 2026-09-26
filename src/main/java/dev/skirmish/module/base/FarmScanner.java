package dev.skirmish.module.base;

import dev.skirmish.ui.Ui;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counts the crops of the base a slice at a time (a few thousand blocks per tick, so a big region costs no frame
 * time): per kind how many are ripe and how many there are. Crops by age, nether wart, cocoa, sweet berries, melon
 * and pumpkin stems (ripe when the fruit has grown) and sugar cane (a stalk two or more high). Only loaded chunks.
 */
final class FarmScanner {
    private long cursor;
    private final Map<String, int[]> acc = new LinkedHashMap<>();
    private Map<String, int[]> last = Map.of();

    void reset() {
        cursor = 0;
        acc.clear();
        last = Map.of();
    }

    /** Kind → {ripe, total} from the last full pass. */
    Map<String, int[]> last() {
        return last;
    }

    /** Scans up to {@code budget} blocks; true when a full pass over the region just finished. */
    boolean step(Level level, BaseData.Region r, int budget) {
        long sx = r.sizeX();
        long sz = r.sizeZ();
        long total = sx * sz * r.sizeY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < budget; i++) {
            if (cursor >= total) {
                last = Map.copyOf(acc);
                acc.clear();
                cursor = 0;
                return true;
            }
            long c = cursor++;
            int x = (int) (c % sx);
            int z = (int) ((c / sx) % sz);
            int y = (int) (c / (sx * sz));
            pos.set(r.x0 + x, r.y0 + y, r.z0 + z);
            if (!level.isLoaded(pos)) {
                continue;
            }
            count(level, pos, level.getBlockState(pos));
        }
        return false;
    }

    private void count(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            add(path(block), crop.isMaxAge(state));
        } else if (block instanceof NetherWartBlock) {
            add("nether_wart", state.getValue(NetherWartBlock.AGE) >= 3);
        } else if (block instanceof CocoaBlock) {
            add("cocoa", state.getValue(CocoaBlock.AGE) >= 2);
        } else if (block instanceof SweetBerryBushBlock) {
            add("sweet_berry_bush", state.getValue(SweetBerryBushBlock.AGE) >= 3);
        } else if (block instanceof AttachedStemBlock) {
            add(path(block).replace("attached_", ""), true);
        } else if (block instanceof StemBlock) {
            add(path(block), false);
        } else if (block == Blocks.SUGAR_CANE && !level.getBlockState(pos.below()).is(Blocks.SUGAR_CANE)) {
            add("sugar_cane", level.getBlockState(pos.above()).is(Blocks.SUGAR_CANE));
        }
    }

    private void add(String kind, boolean ripe) {
        int[] v = acc.computeIfAbsent(kind, k -> new int[2]);
        if (ripe) {
            v[0]++;
        }
        v[1]++;
    }

    private static String path(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    /** Shown name of a kind: our own words for the common ones, the block's name otherwise. */
    static String name(String kind) {
        String key = "skirmish.base.farm." + kind;
        String text = Ui.tr(key);
        if (!text.equals(key)) {
            return text;
        }
        Block block = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(kind));
        return block.getName().getString();
    }
}
