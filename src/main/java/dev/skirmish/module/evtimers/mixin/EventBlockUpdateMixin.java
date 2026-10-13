package dev.skirmish.module.evtimers.mixin;

import dev.skirmish.module.evtimers.EventTimersModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Read-only: server block updates (single and section updates both end in {@code setServerVerifiedBlockState})
 * are shown to the event timers with the block they replace, for Pandora chests appearing and auto-mine refills.
 * The update itself is untouched.
 */
@Mixin(ClientLevel.class)
public abstract class EventBlockUpdateMixin {
    @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"))
    private void skirmish$eventTimersBlockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        EventTimersModule module = EventTimersModule.instance();
        if (module != null && module.isEnabled()) {
            module.onBlockUpdate(pos, ((ClientLevel) (Object) this).getBlockState(pos), state);
        }
    }
}
