package dev.skirmish.module.invtheme.mixin;

import dev.skirmish.module.invtheme.InventoryTheme;
import dev.skirmish.module.invtheme.InventoryThemeModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * «Inventory Theme»: replaces the grey background texture of simple container screens with the Skirmish panel.
 * Slots, items and clicks stay vanilla; the player model is still drawn in the inventory.
 */
@Mixin({ContainerScreen.class, ShulkerBoxScreen.class, HopperScreen.class, DispenserScreen.class, InventoryScreen.class})
abstract class ContainerThemeMixin<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    ContainerThemeMixin(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "renderBg", at = @At("HEAD"), cancellable = true)
    private void skirmish$themedBackground(GuiGraphics graphics, float partial, int mouseX, int mouseY, CallbackInfo ci) {
        if (!InventoryThemeModule.themes(this)) {
            return;
        }
        boolean inventory = (Object) this instanceof InventoryScreen;
        InventoryTheme.draw(graphics, this, leftPos, topPos, imageWidth, imageHeight, inventory);
        if (inventory && minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, leftPos + 26, topPos + 8, leftPos + 75, topPos + 78, 30,
                    0.0625F, mouseX, mouseY, minecraft.player);
        }
        ci.cancel();
    }
}
