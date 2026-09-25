package dev.skirmish.module.hwtimers.mixin;

import dev.skirmish.module.hwtimers.ItemTimersModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Read-only: every server block update ({@code handleBlockUpdate} and {@code handleChunkBlocksUpdate} both end in
 * {@code setServerVerifiedBlockState}) is shown to the item timers together with the block it replaces. Runs on
 * the client thread; the update itself is untouched.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelBlockMixin {
    @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"))
    private void skirmish$hwTimersBlockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        ItemTimersModule module = ItemTimersModule.instance();
        if (module != null && module.isEnabled()) {
            module.onBlockUpdate(pos, ((ClientLevel) (Object) this).getBlockState(pos), state);
        }
    }
}
