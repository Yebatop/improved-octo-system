package dev.skirmish.ui.widget;

import dev.skirmish.setting.StringSetting;
import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Icons;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/** Single-line input bound to a {@link StringSetting}; password settings are masked with an eye toggle. */
public final class TextField extends Widget {
    private final StringSetting setting;
    private final Runnable changed;
    private boolean revealed;
    private int cursor = -1;
    private long focusTime;

    public TextField(StringSetting setting, Runnable changed) {
        this.setting = setting;
        this.changed = changed;
    }

    private String display(Ui ui) {
        String value = setting.get();
        return setting.password() && !revealed ? Ui.tr("skirmish.ui.password_dot").repeat(value.length()) : value;
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        String l = "layout.menu.";
        h = ui.num(l + "keybind_height");
        float r = ui.theme().radius("button_sm");
        ui.rect(x, y, w, h, r, Anim.lerpColor(ui.color("fill_05"), ui.color("fill_08"), isFocused() ? 1f : hovered()));
        ui.border(x, y, w, h, r, ui.num("stroke.width"), isFocused() ? ui.color("accent") : ui.color("stroke_10"));
        float pad = ui.num(l + "keybind_pad_x");
        float icon = setting.password() ? ui.num(l + "input_icon") : 0f;
        float textRight = x + w - pad - (icon > 0 ? icon + ui.num(l + "input_icon_gap") : 0f);
        String text = display(ui);
        int caret = Math.max(0, Math.min(cursor < 0 ? setting.get().length() : cursor, setting.get().length()));
        String before = text.substring(0, Math.min(caret, text.length()));
        // Keep the caret visible: scroll the text left when it is longer than the field.
        float offset = Math.max(0f, ui.textWidth("input", before) - (textRight - x - pad));
        var graphics = ui.graphics();
        graphics.enableScissor((int) Math.floor(x + pad), (int) Math.floor(y), (int) Math.ceil(textRight), (int) Math.ceil(y + h));
        if (text.isEmpty() && !isFocused()) {
            ui.textCentered("input_hint", Ui.tr("skirmish.ui.input_empty"), x + pad, y, h);
        } else {
            ui.textCentered("input", text, x + pad - offset, y, h);
        }
        if (isFocused() && ((Util.getMillis() - focusTime) / 500) % 2 == 0) {
            float cx = x + pad - offset + ui.textWidth("input", before);
            float lh = ui.lineHeight("input");
            ui.rect(cx, y + (h - lh) / 2f, ui.num("stroke.width"), lh, 0f, ui.color("text"));
        }
        graphics.disableScissor();
        if (icon > 0) {
            float ix = x + w - pad - icon;
            float iy = y + (h - icon) / 2f;
            int color = ui.color(revealed ? "text" : "text_3");
            if (revealed) {
                Icons.eye(ui, ix, iy, icon, color);
            } else {
                Icons.eyeOff(ui, ix, iy, icon, color);
            }
        }
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        if (focused && !isFocused()) {
            cursor = setting.get().length();
            focusTime = Util.getMillis();
        }
        super.setFocused(focused);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        if (setting.password()) {
            float icon = dev.skirmish.ui.Theme.get().num("layout.menu.input_icon");
            float pad = dev.skirmish.ui.Theme.get().num("layout.menu.keybind_pad_x");
            if (mx >= x + w - pad - icon - 4 && mx < x + w) {
                revealed = !revealed;
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        String value = setting.get();
        int c = Math.max(0, Math.min(cursor, value.length()));
        focusTime = Util.getMillis();
        if (event.isPaste()) {
            insert(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        if (event.isSelectAll()) {
            cursor = value.length();
            return true;
        }
        switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (c > 0) {
                    int from = event.hasControlDown() ? 0 : value.offsetByCodePoints(c, -1);
                    set(value.substring(0, from) + value.substring(c), from);
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (c < value.length()) {
                    set(value.substring(0, c) + value.substring(value.offsetByCodePoints(c, 1)), c);
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                cursor = c > 0 ? value.offsetByCodePoints(c, -1) : 0;
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursor = c < value.length() ? value.offsetByCodePoints(c, 1) : c;
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursor = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursor = value.length();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                setFocused(false);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!StringUtil.isAllowedChatCharacter(event.codepoint())) {
            return false;
        }
        insert(Character.toString(event.codepoint()));
        return true;
    }

    private void insert(String text) {
        String filtered = StringUtil.filterText(text);
        String value = setting.get();
        int c = Math.max(0, Math.min(cursor, value.length()));
        String next = value.substring(0, c) + filtered + value.substring(c);
        if (next.length() > setting.maxLength()) {
            return;
        }
        set(next, c + filtered.length());
    }

    private void set(String value, int caret) {
        setting.set(value);
        cursor = Math.min(caret, setting.get().length());
        changed.run();
    }
}
