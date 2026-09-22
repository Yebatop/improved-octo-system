package dev.skirmish.gui;

import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.setting.Setting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Translation helpers with readable fallbacks when a key is missing. */
public final class Texts {
    private Texts() {
    }

    public static Component tr(String key, String fallback) {
        return Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(fallback);
    }

    public static boolean has(String key) {
        return Language.getInstance().has(key);
    }

    public static Component settingName(Setting<?> setting) {
        return tr(setting.translationKey(), humanize(setting.id()));
    }

    public static Component settingTooltip(Setting<?> setting) {
        String key = setting.translationKey() + ".tooltip";
        return has(key) ? Component.translatable(key) : null;
    }

    public static <E extends Enum<E>> Component enumValue(EnumSetting<E> setting, E value) {
        return tr(setting.valueTranslationKey(value), humanize(value.name()));
    }

    /** Value plus unit; the unit is translated via {@code skirmish.unit.<unit>} when such a key exists. */
    public static Component number(NumberSetting setting) {
        String unit = setting.unit().trim();
        if (unit.isEmpty()) {
            return Component.literal(setting.formatValue());
        }
        String separator = setting.unit().startsWith(" ") ? " " : "";
        return Component.literal(setting.formatValue() + separator).append(tr("skirmish.unit." + unit, unit));
    }

    /** Translated short unit ({@code skirmish.unit.<unit>}), or the unit itself. */
    public static String unit(String unit) {
        return Language.getInstance().getOrDefault("skirmish.unit." + unit, unit);
    }

    /** {@code max_label_distance -> Max label distance}. */
    public static String humanize(String id) {
        String text = id.replace('_', ' ').toLowerCase(Locale.ROOT);
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
