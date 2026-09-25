package dev.skirmish.module.invtheme.mixin;

import dev.skirmish.module.invtheme.InventoryTheme;
import dev.skirmish.module.invtheme.InventoryThemeModule;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Light titles on the themed (dark) container panels; vanilla's dark grey elsewhere. */
@Mixin({AbstractContainerScreen.class, InventoryScreen.class})
abstract class ContainerLabelsMixin {
    @ModifyArg(method = "renderLabels", index = 4, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"))
    private int skirmish$labelColor(int color) {
        return (Object) this instanceof AbstractContainerScreen<?> screen && InventoryThemeModule.themes(screen) ? InventoryTheme.labelColor() : color;
    }
}
