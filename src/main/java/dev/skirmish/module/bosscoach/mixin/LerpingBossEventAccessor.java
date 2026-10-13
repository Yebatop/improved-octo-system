package dev.skirmish.module.bosscoach.mixin;

import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The value the server last set on a boss bar ({@code getProgress} eases towards it over 100 ms). Read only. */
@Mixin(LerpingBossEvent.class)
public interface LerpingBossEventAccessor {
    @Accessor("targetPercent")
    float skirmish$targetPercent();
}
