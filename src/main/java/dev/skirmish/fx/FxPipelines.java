package dev.skirmish.fx;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Render types of the effects: glowing shapes add their light (source alpha, one) and painted ones blend normally;
 * both are depth-tested but don't write depth, so blocks and players in front hide them and they never show through
 * walls.
 */
public final class FxPipelines {
    static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("skirmish", "pipeline/fx_glow"))
            .withBlend(BlendFunction.LIGHTNING)
            .withCull(false)
            .build());
    public static final RenderType GLOW = RenderType.create("skirmish_fx_glow", RenderSetup.builder(GLOW_PIPELINE).createRenderSetup());

    private FxPipelines() {
    }

    public static RenderType paint() {
        return RenderTypes.debugQuads();
    }

    /** Loads the class (registers the pipeline) during mod init. */
    public static void init() {
    }
}
