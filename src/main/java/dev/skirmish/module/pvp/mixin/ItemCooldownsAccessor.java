package dev.skirmish.module.pvp.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Active cooldown groups and the cooldown clock. The map values are vanilla's package-private
 * {@code CooldownInstance}; read them through {@link CooldownInstanceAccessor}.
 */
@Mixin(ItemCooldowns.class)
public interface ItemCooldownsAccessor {
    @Accessor("cooldowns")
    Map<Identifier, ?> skirmish$cooldowns();

    @Accessor("tickCount")
    int skirmish$tickCount();
}
