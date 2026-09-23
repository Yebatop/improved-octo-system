package dev.skirmish.module.killcard;

import org.jspecify.annotations.Nullable;

/**
 * One equipment slot as shown on the card.
 *
 * @param name       item display name, empty for an empty slot
 * @param durability remaining durability in [0, 1], or -1 when the item has none or is undamaged
 * @param icon       null when the icon could not be resolved (a placeholder with the name is drawn instead)
 * @param iconInfo   how the icon was resolved, for the debug log
 */
public record CardItem(String itemId, String name, int count, float durability, boolean foil, @Nullable IconSource icon, String iconInfo) {
    public static final CardItem EMPTY = new CardItem("", "", 0, -1, false, null, "empty");

    public boolean isEmpty() {
        return count <= 0;
    }
}
