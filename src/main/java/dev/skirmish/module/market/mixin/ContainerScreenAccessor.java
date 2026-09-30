package dev.skirmish.module.market.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to where a container GUI sits on screen (GUI px), to put price chips over its slots. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos")
    int skirmish$leftPos();

    @Accessor("topPos")
    int skirmish$topPos();
}
