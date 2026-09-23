package dev.skirmish.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skirmish.ui.render.UiFonts;
import org.lwjgl.util.freetype.FT_Face;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Glyph bitmaps of the UI fonts: no hinting, browser-like coverage (see {@link UiFonts}). Rendering only. */
@Mixin(NativeImage.class)
abstract class NativeImageMixin {
    @Shadow
    @Final
    private int width;
    @Shadow
    @Final
    private int height;
    @Shadow
    private long pixels;

    @ModifyArg(method = "copyFromFont", index = 2, at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/util/freetype/FreeType;FT_Load_Glyph(Lorg/lwjgl/util/freetype/FT_Face;II)I"))
    private int skirmish$uiFontFlags(FT_Face face, int glyph, int flags) {
        return UiFonts.loadFlags(face, flags);
    }

    @Inject(method = "copyFromFont", at = @At("RETURN"))
    private void skirmish$uiFontCoverage(FT_Face face, int glyph, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && UiFonts.isUiFont(face)) {
            UiFonts.adjustCoverage(pixels, width * height);
        }
    }
}
