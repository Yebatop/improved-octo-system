package dev.skirmish.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import dev.skirmish.ui.render.UiFonts;
import org.lwjgl.util.freetype.FT_Face;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Glyph metrics of the UI fonts: unhinted outlines, whole-pixel advances (see {@link UiFonts}). Rendering only. */
@Mixin(TrueTypeGlyphProvider.class)
abstract class TrueTypeGlyphProviderMixin {
    @ModifyArg(method = "loadGlyph", index = 2, at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/util/freetype/FreeType;FT_Load_Glyph(Lorg/lwjgl/util/freetype/FT_Face;II)I"))
    private int skirmish$uiFontFlags(FT_Face face, int glyph, int flags) {
        return UiFonts.loadFlags(face, flags);
    }

    @ModifyExpressionValue(method = "loadGlyph", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/font/providers/FreeTypeUtil;x(Lorg/lwjgl/util/freetype/FT_Vector;)F"))
    private float skirmish$uiFontAdvance(float advance, @Local(argsOnly = true) FT_Face face) {
        return UiFonts.advance(face, advance);
    }
}
