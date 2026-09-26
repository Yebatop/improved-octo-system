package dev.skirmish.module.hwitems.badges;

import dev.skirmish.holyworld.HolyWorld;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.DrawItemStackOverlayCallback;

/**
 * «Item Badges»: small labels over inventory and hotbar slots of HolyWorld items — the effects of a sphere or
 * talisman ({@code У3 Б2}), its rune, the backpack level and the TNT type ({@code A}, {@code B}, {@code C4},
 * {@code РВ}, {@code Б2}…). Read from the item's own name and lore (what its tooltip shows); drawn through Fabric's
 * {@link DrawItemStackOverlayCallback} after vanilla's slot decorations. Render only.
 */
public final class TalismanBadgesModule extends Module {
    public static final String ID = "talisman_badges";

    final BoolSetting holyworldOnly = add(new BoolSetting("holyworld_only", true));
    final BoolSetting stats = add(new BoolSetting("stats", true));
    final BoolSetting runes = add(new BoolSetting("runes", true));
    final BoolSetting backpacks = add(new BoolSetting("backpacks", true));
    final BoolSetting tnt = add(new BoolSetting("tnt", true));
    final BoolSetting hotbar = add(new BoolSetting("hotbar", true));
    final NumberSetting textScale = add(new NumberSetting("text_scale", 1.0, 0.9, 1.6, 0.1).unit("×"));

    private final BadgeRenderer renderer = new BadgeRenderer(this);

    public TalismanBadgesModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.UTILITY;
    }

    @Override
    public void onInitialize() {
        DrawItemStackOverlayCallback.EVENT.register((graphics, font, stack, x, y) -> {
            if (isEnabled()) {
                renderer.draw(graphics, stack, x, y);
            }
        });
    }

    /** Badges apply here: on HolyWorld, or anywhere with «Только HolyWorld» off. */
    boolean active() {
        return !holyworldOnly.get() || HolyWorld.isConnected();
    }

    BadgeText.Options options() {
        return new BadgeText.Options(stats.get(), runes.get(), backpacks.get(), tnt.get());
    }
}
