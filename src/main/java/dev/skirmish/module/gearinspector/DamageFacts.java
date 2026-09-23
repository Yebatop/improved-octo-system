package dev.skirmish.module.gearinspector;

import org.jspecify.annotations.Nullable;

/**
 * What the client actually holds about the durability of one stack, read from its data components.
 *
 * @param empty                 the slot holds {@code ItemStack.EMPTY}
 * @param unbreakable           the stack has the {@code unbreakable} component
 * @param prototypeHasMaxDamage the item type is damageable by default ({@code max_damage} in its default components)
 * @param maxDamage             effective {@code max_damage} of the stack, null when absent or removed
 * @param damage                effective {@code damage} of the stack, null when absent or removed
 * @param damageSent            {@code damage} is part of the stack's component patch, i.e. the server sent a value.
 *                              Vanilla never puts {@code damage=0} into the patch (it equals the item default), so an
 *                              undamaged item and an item whose damage the server hid look the same here.
 */
public record DamageFacts(boolean empty, boolean unbreakable, boolean prototypeHasMaxDamage,
                          @Nullable Integer maxDamage, @Nullable Integer damage, boolean damageSent) {
    public static final DamageFacts EMPTY = new DamageFacts(true, false, false, null, null, false);
}
