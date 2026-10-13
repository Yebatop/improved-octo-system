package dev.skirmish.module.toolsaver;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Pill for a main-hand tool or weapon close to breaking: item name, uses left and a durability bar; red with
 * «защита» once hits are held back. In the top-centre alert column.
 */
final class ToolSaverHud extends HudBlock {
    private static final String L = ToolSaverModule.L;
    private final ToolSaverModule module;
    private String name = "";
    private int @Nullable [] state;

    ToolSaverHud(ToolSaverModule module) {
        super(ToolSaverModule.ID, "skirmish.hud.element.tool_saver", new Placement(0.5f, 0f, 0.5f, 0f, 0,
                Theme.get().num("layout.hud.top_column_dy")));
        this.module = module;
    }

    @Override
    public @Nullable String stackUnder() {
        return "region_bounds";
    }

    @Override
    public boolean enabled() {
        return module.isEnabled();
    }

    @Override
    public boolean shown() {
        update(false);
        return live();
    }

    private boolean live() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && module.warning(mc.player.getMainHandItem()) != null;
    }

    @Override
    public boolean hasContent() {
        return state != null;
    }

    @Override
    public void update(boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack stack = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
        int[] live = module.warning(stack);
        if (live != null) {
            state = live;
            name = stack.getHoverName().getString();
        } else if (preview && state == null) {
            state = new int[]{7, 1561, 1};
            name = Ui.tr("skirmish.tool_saver.sample");
        }
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "width");
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return ui.num(L + "pad_y") * 2 + ui.lineHeight("ts_title") + ui.num(L + "bar_gap") + ui.num(L + "bar");
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        int[] s = state;
        if (s == null) {
            return;
        }
        float w = width(ui, preview);
        float h = height(ui, preview);
        boolean guards = s[2] == 1;
        int tone = ui.color(guards ? "bad" : "warn");
        ui.box(x, y, w, h, ui.theme().radius("tile"), ui.color("panel"), guards ? tone : ui.color("stroke"));
        float padX = ui.num(L + "pad_x");
        float padY = ui.num(L + "pad_y");
        String value = guards ? Ui.tr("skirmish.tool_saver.guard", s[0]) : Ui.tr("skirmish.tool_saver.left", s[0]);
        float vw = ui.textWidth("ts_value", value);
        ui.text("ts_value", value, x + w - padX - vw, y + padY, tone);
        ui.text("ts_title", ui.ellipsize("ts_title", name, w - padX * 3 - vw), x + padX, y + padY);
        float by = y + padY + ui.lineHeight("ts_title") + ui.num(L + "bar_gap");
        float bar = ui.num(L + "bar");
        float bw = w - padX * 2;
        ui.rect(x + padX, by, bw, bar, bar / 2f, ui.color("track"));
        ui.rect(x + padX, by, bw * Math.max(0.02f, s[0] / (float) Math.max(1, s[1])), bar, bar / 2f, tone);
    }
}
