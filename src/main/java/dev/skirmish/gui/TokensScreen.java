package dev.skirmish.gui;

import dev.skirmish.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Debug view of theme.json laid out like the mockup's «Токены темы» artboard ({@code /skirmish theme}). */
public final class TokensScreen extends Screen {
    private record Swatch(String name, String token, boolean outlined) {
    }

    private static final List<Swatch> SWATCHES = List.of(
            new Swatch("panel", "panel", true), new Swatch("accent", "accent", false),
            new Swatch("text", "text", false), new Swatch("text-2", "text_2", false),
            new Swatch("text-3", "text_3", false), new Swatch("good", "good", false),
            new Swatch("bad", "bad", false), new Swatch("warn", "warn", false),
            new Swatch("stroke", "stroke", true), new Swatch("selected", "selected", false));

    private final @Nullable Screen parent;

    public TokensScreen(@Nullable Screen parent) {
        super(Component.literal("theme.json"));
        this.parent = parent;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui ui = Ui.begin(graphics);
        try {
            draw(ui);
        } finally {
            ui.end();
        }
    }

    private void draw(Ui ui) {
        String l = "layout.tokens.";
        float w = ui.num(l + "width");
        float h = ui.num(l + "height");
        float pad = ui.num(l + "pad");
        float ox = Math.round((ui.width() - w) / 2f);
        float oy = Math.round((ui.height() - h) / 2f);
        ui.rect(0, 0, ui.width(), ui.height(), 0, ui.color("page"));

        float x = ox + pad;
        float y = oy + pad;
        float inner = w - pad * 2;
        ui.text("tokens_title", "theme.json — значения для мода", x, y);
        y += ui.lineHeight("tokens_title") + ui.num(l + "head_gap");
        ui.text("tokens_sub", "Агенты переносят эти цифры один в один. Размеры — в пикселях GUI при масштабе интерфейса 2.", x, y);
        y += ui.lineHeight("tokens_sub") + ui.num(l + "gap");

        int columns = Math.round(ui.num(l + "columns"));
        float gridGap = ui.num(l + "grid_gap");
        float cellW = (inner - gridGap * (columns - 1)) / columns;
        float swatchH = ui.num(l + "swatch_height");
        float swatchGap = ui.num(l + "swatch_gap");
        float stroke = ui.num("stroke.width");
        float rowH = 0;
        for (int i = 0; i < SWATCHES.size(); i++) {
            Swatch s = SWATCHES.get(i);
            float cx = x + (i % columns) * (cellW + gridGap);
            float cy = y + (i / columns) * (rowH + gridGap);
            float sh = swatchH + (s.outlined() ? stroke * 2 : 0);
            int color = ui.color(s.token());
            if (s.outlined()) {
                ui.box(cx, cy, cellW, sh, ui.num(l + "swatch_radius"), s.token().equals("panel") ? ui.color("card") : color, ui.color("stroke_10"));
            } else {
                ui.rect(cx, cy, cellW, sh, ui.num(l + "swatch_radius"), color);
            }
            float ty = cy + sh + swatchGap;
            ui.text("tokens_name", s.name(), cx, ty);
            ty += ui.lineHeight("tokens_name") + swatchGap;
            ui.text("tokens_value", hex(color), cx, ty);
            rowH = Math.max(rowH, sh + swatchGap * 2 + ui.lineHeight("tokens_name") + ui.lineHeight("tokens_value"));
        }
        y += rowH * 2 + gridGap + ui.num(l + "gap");

        float colGap = ui.num(l + "column_gap");
        float colW = (inner - colGap) / 2f;
        column(ui, x, y, colW, "ФОРМА", new String[][]{
                {"Скругление панели", fmt(ui.num("radius.panel")), "m"},
                {"Скругление кнопки", fmt(ui.num("radius.button_sm")) + "–" + fmt(ui.num("radius.button")), "m"},
                {"Внутренний отступ панели", fmt(ui.num("layout.panel_pad_y")) + " × " + fmt(ui.num("layout.panel_pad_x")), "m"},
                {"Отступ от края экрана", fmt(ui.num("layout.screen_edge")), "m"},
                {"Тень", "нет", "m"}});
        column(ui, x + colW + colGap, y, colW, "ШРИФТ И ДВИЖЕНИЕ", new String[][]{
                {"Шрифт", "Manrope (TTF в ресурсах)", "s"},
                {"Заголовок панели", fmt(ui.style("panel_title").size()) + " / " + ui.style("panel_title").weight(), "m"},
                {"Строка", fmt(ui.style("row").size()) + " / " + ui.style("row").weight() + "–" + ui.style("row_value").weight(), "m"},
                {"Появление / скрытие", fmt(ui.num("motion.appear_ms")) + " мс " + ui.theme().string("motion.easing"), "m"},
                {"Полоска HP", "сглаживание " + fmt(ui.num("motion.hp_smooth_ms")) + " мс", "m"}});
    }

    private static void column(Ui ui, float x, float y, float w, String title, String[][] rows) {
        float gap = ui.num("layout.tokens.row_gap");
        ui.text("tokens_section", title, x, y);
        y += ui.lineHeight("tokens_section") + gap;
        for (String[] row : rows) {
            String valueStyle = row[2].equals("m") ? "tokens_val" : "tokens_val_sans";
            float lh = Math.max(ui.lineHeight("tokens_key"), ui.lineHeight(valueStyle));
            ui.text("tokens_key", row[0], x, y);
            ui.text(valueStyle, row[1], x + w - ui.textWidth(valueStyle, row[1]), y);
            y += lh + gap;
        }
    }

    private static String hex(int argb) {
        String rgb = String.format("#%06X", argb & 0xFFFFFF);
        int a = argb >>> 24;
        return a == 255 ? rgb : rgb + " · " + Math.round(a / 2.55f) + "%";
    }

    private static String fmt(float v) {
        return v == Math.rint(v) ? Integer.toString(Math.round(v)) : Float.toString(v);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
