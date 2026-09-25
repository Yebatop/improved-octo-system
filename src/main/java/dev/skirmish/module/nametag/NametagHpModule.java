package dev.skirmish.module.nametag;

import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.survival.PvpState;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import org.jspecify.annotations.Nullable;

/**
 * «ХП над головой»: other players' health (and absorption) as an extra nametag line, colored by percent, with an
 * optional small bar. Uses the health the server already syncs to the client ({@code LivingEntity#getHealth}); drawn
 * through the vanilla nametag path only when vanilla shows that nametag itself, so distance, sneaking and
 * see-through behaviour stay vanilla. Invisible players are always skipped. Render-only.
 */
public final class NametagHpModule extends Module {
    public static final String ID = "nametag_hp";
    private static @Nullable NametagHpModule instance;

    /** Where the HP goes relative to the nick. */
    public enum Position {
        ABOVE, BELOW, INLINE
    }

    final EnumSetting<NametagPolicy.Show> show = add(new EnumSetting<>("show", NametagPolicy.Show.ALWAYS));
    final EnumSetting<HpText.Units> units = add(new EnumSetting<>("units", HpText.Units.HP));
    final EnumSetting<Position> position = add(new EnumSetting<>("position", Position.ABOVE));
    final BoolSetting absorption = add(new BoolSetting("absorption", true));
    final BoolSetting bar = (BoolSetting) add(new BoolSetting("bar", true))
            .visibleWhen(() -> position.get() != Position.INLINE);

    /** PvP state sampled once per tick instead of per nametag per frame. */
    private boolean inPvp;

    public NametagHpModule() {
        super(ID, true);
        instance = this;
    }

    static @Nullable NametagHpModule instance() {
        return instance;
    }

    @Override
    public Category category() {
        return Category.VISUAL;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void tick() {
        inPvp = show.get() == NametagPolicy.Show.COMBAT && PvpState.inPvp();
    }

    @Override
    protected void onDisable() {
        inPvp = false;
    }

    boolean inPvp() {
        return inPvp;
    }
}
