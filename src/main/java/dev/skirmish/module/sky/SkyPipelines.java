package dev.skirmish.module.sky;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * The sky's render type: position + colour quads, additive (source alpha, one), depth-tested but not written, no
 * culling — so terrain in front hides it and it only lights up the sky behind.
 */
public final class SkyPipelines {
    static final RenderPipeline ADD = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("skirmish", "pipeline/sky_add"))
            .withBlend(BlendFunction.LIGHTNING)
            .withCull(false)
            .build());
    public static final RenderType SKY = RenderType.create("skirmish_sky", RenderSetup.builder(ADD).createRenderSetup());

    private SkyPipelines() {
    }

    /** Loads the class (registers the pipeline) during mod init. */
    static void init() {
    }
}
