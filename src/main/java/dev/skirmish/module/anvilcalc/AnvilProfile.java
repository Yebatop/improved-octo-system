package dev.skirmish.module.anvilcalc;

import dev.skirmish.module.anvilcalc.calc.RulesProfile;

/** Setting «Правила наковальни». */
public enum AnvilProfile {
    /**
     * HolyWorld Lite rules while connected to HolyWorld, vanilla elsewhere. Lite and Prime share hosts and nothing the
     * client receives is known to tell them apart reliably, so Prime players pick {@link #HOLYWORLD_PRIME} by hand.
     */
    AUTO,
    VANILLA,
    HOLYWORLD_LITE,
    HOLYWORLD_PRIME;

    RulesProfile resolve(boolean onHolyWorld) {
        return switch (this) {
            case AUTO -> onHolyWorld ? RulesProfile.HOLYWORLD_LITE : RulesProfile.VANILLA;
            case VANILLA -> RulesProfile.VANILLA;
            case HOLYWORLD_LITE -> RulesProfile.HOLYWORLD_LITE;
            case HOLYWORLD_PRIME -> RulesProfile.HOLYWORLD_PRIME;
        };
    }
}
