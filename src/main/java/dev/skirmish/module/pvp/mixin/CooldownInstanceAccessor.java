package dev.skirmish.module.pvp.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Start and end tick of one cooldown (the record is package-private in vanilla). */
@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface CooldownInstanceAccessor {
    @Accessor("startTime")
    int skirmish$startTime();

    @Accessor("endTime")
    int skirmish$endTime();
}
