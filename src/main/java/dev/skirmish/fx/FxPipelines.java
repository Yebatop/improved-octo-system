package dev.skirmish.fx;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Render types of the effects: sprites from the effect atlas, smoothly filtered; glowing ones add their light
 * (source alpha, one), painted ones blend normally. Both are depth-tested without writing depth, so blocks and
 * players in front hide them and they never show through walls.
 */
public final class FxPipelines {
    public static final Identifier ATLAS = Identifier.fromNamespaceAndPath("skirmish", "textures/fx/atlas.png");

    static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("skirmish", "pipeline/fx_glow"))
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());
    static final RenderPipeline PAINT_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("skirmish", "pipeline/fx_paint"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());
    public static final RenderType GLOW = RenderType.create("skirmish_fx_glow", RenderSetup.builder(GLOW_PIPELINE)
            .withTexture("Sampler0", ATLAS, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            .createRenderSetup());
    public static final RenderType PAINT = RenderType.create("skirmish_fx_paint", RenderSetup.builder(PAINT_PIPELINE)
            .withTexture("Sampler0", ATLAS, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            .sortOnUpload()
            .createRenderSetup());

    private FxPipelines() {
    }

    public static RenderType paint() {
        return PAINT;
    }

    /** Loads the class (registers the pipelines) during mod init. */
    public static void init() {
    }
}
