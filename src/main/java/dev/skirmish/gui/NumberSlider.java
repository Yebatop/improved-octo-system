package dev.skirmish.gui;

import dev.skirmish.setting.NumberSetting;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

final class NumberSlider extends AbstractSliderButton {
    private final NumberSetting setting;
    private final Runnable onChange;

    NumberSlider(int x, int y, int width, int height, NumberSetting setting, Runnable onChange) {
        super(x, y, width, height, Component.literal(setting.format()), setting.toSlider());
        this.setting = setting;
        this.onChange = onChange;
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(setting.format()));
    }

    @Override
    protected void applyValue() {
        setting.setFromSlider(value);
        value = setting.toSlider();
        onChange.run();
    }
}
