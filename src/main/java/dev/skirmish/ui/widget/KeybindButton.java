package dev.skirmish.ui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.skirmish.ui.Anim;
import dev.skirmish.ui.KeyNames;
import dev.skirmish.ui.Ui;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

/**
 * Shows and rebinds a vanilla {@link KeyMapping} (the same binding as in Options → Controls, saved to
 * options.txt). Click, then press a key or mouse button; Escape clears the binding.
 */
public final class KeybindButton extends Widget {
    private final KeyMapping mapping;
    private boolean listening;

    public KeybindButton(KeyMapping mapping) {
        this.mapping = mapping;
    }

    private String label(Ui ui) {
        if (listening) {
            return Ui.tr("skirmish.ui.keybind_listening");
        }
        return mapping.isUnbound() ? Ui.tr("skirmish.ui.keybind_none") : KeyNames.shortName(mapping);
    }

    @Override
    public float preferredWidth(Ui ui) {
        String l = "layout.menu.";
        return Math.max(ui.num(l + "keybind_min_width"),
                ui.textWidth("keybind", label(ui)) + ui.num(l + "keybind_pad_x") * 2 + ui.num("stroke.width") * 2);
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        float r = ui.theme().radius("button_sm");
        h = ui.num("layout.menu.keybind_height");
        ui.rect(x, y, w, h, r, Anim.lerpColor(ui.color("fill_05"), ui.color("fill_08"), hovered()));
        ui.border(x, y, w, h, r, ui.num("stroke.width"), listening ? ui.color("accent") : ui.color("stroke_10"));
        String text = label(ui);
        float tw = ui.textWidth("keybind", text);
        ui.textCentered("keybind", text, x + (w - tw) / 2f, y, h, listening ? ui.color("text_2") : ui.color("text"));
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            listening = false;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (listening) {
            bind(InputConstants.Type.MOUSE.getOrCreate(button));
            return true;
        }
        if (button == 0) {
            listening = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!listening) {
            return false;
        }
        bind(event.isEscape() ? InputConstants.UNKNOWN : InputConstants.getKey(event));
        return true;
    }

    public boolean isListening() {
        return listening;
    }

    private void bind(InputConstants.Key key) {
        Minecraft minecraft = Minecraft.getInstance();
        mapping.setKey(key);
        KeyMapping.resetMapping();
        minecraft.options.save();
        listening = false;
    }
}
