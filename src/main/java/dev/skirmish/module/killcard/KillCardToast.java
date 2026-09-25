package dev.skirmish.module.killcard;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skirmish.SkirmishKeys;
import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.KeyNames;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * HUD toast shown for a few seconds after a card is saved: a thumbnail of the card, "saved", and the key that opens
 * the folder. Replaces copying to the clipboard (which needed external OS tools).
 */
final class KillCardToast extends HudBlock {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("skirmish", "killcard/last");
    private static final String L = "layout.hud.";

    private final KillCardModule module;
    private long shownAt = -1;
    private int texW;
    private int texH;
    private String fileName = "";

    KillCardToast(KillCardModule module) {
        super("killcard", "skirmish.hud.element.killcard", new Placement(1, 0, 1, 0, -18, 60));
        this.module = module;
    }

    /** Client thread: uploads a thumbnail of {@code card} and starts the toast. */
    void show(BufferedImage card, String fileName) {
        dev.skirmish.ui.Theme t = dev.skirmish.ui.Theme.get();
        float thumbW = t.num(L + "toast_thumb_width");
        texW = Math.round(thumbW);
        texH = Math.round(thumbW * card.getHeight() / card.getWidth());
        BufferedImage scaled = new BufferedImage(texW, texH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(card, 0, 0, texW, texH, null);
        g.dispose();
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, texW, texH, false);
        for (int y = 0; y < texH; y++) {
            for (int x = 0; x < texW; x++) {
                image.setPixel(x, y, scaled.getRGB(x, y));
            }
        }
        Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(TEXTURE::toString, image));
        this.fileName = fileName;
        shownAt = Util.getMillis();
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.toast.get();
    }

    @Override
    public boolean shown() {
        return shownAt >= 0 && Util.getMillis() - shownAt < dev.skirmish.ui.Theme.get().num(L + "toast_ms");
    }

    @Override
    public boolean hasContent() {
        return shownAt >= 0;
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "toast_width");
    }

    private float thumbHeight(Ui ui) {
        return Math.round(ui.num(L + "toast_thumb_width") * ui.num("layout.card.height") / ui.num("layout.card.width"));
    }

    /** A compact row: the card thumbnail on the left, «Карточка сохранена» and the file (or key hint) beside it. */
    @Override
    public float height(Ui ui, boolean preview) {
        float text = ui.lineHeight("toast_title") + ui.lineHeight("toast_sub");
        return ui.num("stroke.width") * 2 + ui.num(L + "toast_pad") * 2 + Math.max(thumbHeight(ui), text);
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        float w = width(ui, preview);
        float h = height(ui, preview);
        HudStyle.panel(ui, x, y, w, h);
        float inset = ui.num("stroke.width") + ui.num(L + "toast_pad");
        float thumbW = ui.num(L + "toast_thumb_width");
        float thumbH = thumbHeight(ui);
        float tx = x + inset;
        float ty = y + (h - thumbH) / 2f;
        if (shownAt >= 0 && !preview) {
            ui.graphics().blit(RenderPipelines.GUI_TEXTURED, TEXTURE, Math.round(tx), Math.round(ty), 0, 0,
                    Math.round(thumbW), Math.round(thumbH), texW, texH);
        } else {
            ui.rect(tx, ty, thumbW, thumbH, 0, ui.color("card"));
        }
        ui.cornerMask(Math.round(tx), Math.round(ty), Math.round(thumbW), Math.round(thumbH), ui.theme().radius("chip"), ui.color("panel"));
        float textX = tx + thumbW + ui.num(L + "toast_gap");
        float textW = x + w - inset - textX;
        float textY = y + (h - ui.lineHeight("toast_title") - ui.lineHeight("toast_sub")) / 2f;
        ui.text("toast_title", ui.ellipsize("toast_title", Ui.tr("skirmish.killcard.toast.title"), textW), textX, textY);
        String sub = SkirmishKeys.KILLCARD_OPEN_FOLDER.isUnbound()
                ? ui.ellipsize("toast_sub", preview ? "killcard_23.09.2026.png" : fileName, textW)
                : Ui.tr("skirmish.killcard.toast.key", KeyNames.shortName(SkirmishKeys.KILLCARD_OPEN_FOLDER));
        ui.text("toast_sub", ui.ellipsize("toast_sub", sub, textW), textX, textY + ui.lineHeight("toast_title"));
    }

}
