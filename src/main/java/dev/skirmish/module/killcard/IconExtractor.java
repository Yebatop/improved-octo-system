package dev.skirmish.module.killcard;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skirmish.module.killcard.mixin.SpriteContentsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-thread half of the icon pipeline. The stack goes through the game's own item model resolution
 * ({@code items/<id>.json} → select/condition/composite → baked quads), exactly as for the inventory, so trims, dyes,
 * potion colours and resource packs are respected. The textures of the resulting quads are copied out of the
 * already-stitched sprites (CPU copy kept by {@link SpriteContents}); no file IO happens here.
 * Items drawn by special renderers (shields, heads, banners, chests, tridents...) have no quads and get a placeholder.
 */
final class IconExtractor {
    private static final float FLAT_DEPTH = 0.2f;

    private IconExtractor() {
    }

    /** Records every layer the model appends, since {@code ItemStackRenderState} has no public layer getter. */
    private static final class CapturingState extends ItemStackRenderState {
        final List<LayerRenderState> captured = new ArrayList<>();

        @Override
        public LayerRenderState newLayer() {
            LayerRenderState layer = super.newLayer();
            captured.add(layer);
            return layer;
        }
    }

    private record TintedQuad(BakedQuad quad, int tint) {
    }

    static CardItem extract(Minecraft mc, ItemStack stack) {
        if (stack.isEmpty()) {
            return CardItem.EMPTY;
        }
        String id = stack.getItemHolder().getRegisteredName();
        String name = stack.getHoverName().getString();
        float durability = stack.isDamageableItem() && stack.isDamaged() && stack.getMaxDamage() > 0
                ? 1f - stack.getDamageValue() / (float) stack.getMaxDamage() : -1f;
        boolean foil = stack.hasFoil();
        IconSource icon = null;
        String info;
        try {
            CapturingState state = new CapturingState();
            mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, mc.level, null, 0);
            List<TintedQuad> quads = new ArrayList<>();
            for (ItemStackRenderState.LayerRenderState layer : state.captured) {
                int[] tints = layer.prepareTintLayers(0);
                for (BakedQuad quad : layer.prepareQuadList()) {
                    int tint = quad.isTinted() && quad.tintIndex() < tints.length ? tints[quad.tintIndex()] : -1;
                    quads.add(new TintedQuad(quad, tint));
                }
            }
            if (state.captured.isEmpty()) {
                info = "missing: no item model";
            } else if (quads.isEmpty()) {
                info = "missing: special renderer or custom mesh (" + state.captured.size() + " layer(s), no quads)";
            } else if (depth(quads) <= FLAT_DEPTH) {
                icon = flat(quads);
                info = "flat " + ((IconSource.Flat) icon).layers();
            } else {
                icon = cube(quads);
                IconSource.Cube cube = (IconSource.Cube) icon;
                info = "cube top=" + cube.top() + ", left=" + cube.left() + ", right=" + cube.right();
            }
        } catch (MissingSpriteException e) {
            info = "missing: " + e.getMessage();
            icon = null;
        } catch (RuntimeException e) {
            info = "missing: " + e;
            icon = null;
        }
        return new CardItem(id, name, stack.getCount(), durability, foil, icon, info);
    }

    private static float depth(List<TintedQuad> quads) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (TintedQuad tinted : quads) {
            for (int i = 0; i < 4; i++) {
                float z = tinted.quad().position(i).z();
                min = Math.min(min, z);
                max = Math.max(max, z);
            }
        }
        return max - min;
    }

    /** item/generated: every layer contributes front, back and edge quads with the same sprite. */
    private static IconSource flat(List<TintedQuad> quads) {
        Map<String, IconSource.Layer> layers = new LinkedHashMap<>();
        for (TintedQuad tinted : quads) {
            TextureAtlasSprite sprite = tinted.quad().sprite();
            String key = sprite.contents().name() + "#" + tinted.tint();
            if (!layers.containsKey(key)) {
                layers.put(key, layer(sprite, tinted.tint()));
            }
        }
        return new IconSource.Flat(List.copyOf(layers.values()));
    }

    private static IconSource cube(List<TintedQuad> quads) {
        TintedQuad top = largest(quads, Direction.UP);
        TintedQuad left = largest(quads, Direction.SOUTH);
        TintedQuad right = largest(quads, Direction.EAST);
        TintedQuad any = largest(quads, null);
        return new IconSource.Cube(layer(top != null ? top : any), layer(left != null ? left : any), layer(right != null ? right : any));
    }

    private static IconSource.Layer layer(TintedQuad quad) {
        return layer(quad.quad().sprite(), quad.tint());
    }

    private static @Nullable TintedQuad largest(List<TintedQuad> quads, @Nullable Direction direction) {
        TintedQuad best = null;
        float bestArea = -1;
        for (TintedQuad tinted : quads) {
            if (direction != null && tinted.quad().direction() != direction) {
                continue;
            }
            float area = area(tinted.quad());
            if (area > bestArea) {
                bestArea = area;
                best = tinted;
            }
        }
        return best;
    }

    private static float area(BakedQuad quad) {
        Vector3fc a = quad.position(0);
        Vector3fc b = quad.position(1);
        Vector3fc c = quad.position(2);
        float abx = b.x() - a.x(), aby = b.y() - a.y(), abz = b.z() - a.z();
        float acx = c.x() - a.x(), acy = c.y() - a.y(), acz = c.z() - a.z();
        float cx = aby * acz - abz * acy;
        float cy = abz * acx - abx * acz;
        float cz = abx * acy - aby * acx;
        return (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
    }

    /** Copies the first animation frame of the sprite (frames are laid out left to right, top to bottom). */
    private static IconSource.Layer layer(TextureAtlasSprite sprite, int tint) {
        SpriteContents contents = sprite.contents();
        String name = contents.name().toString();
        if (contents.name().equals(MissingTextureAtlasSprite.getLocation())) {
            throw new MissingSpriteException("missing texture sprite");
        }
        NativeImage image = ((SpriteContentsAccessor) contents).skirmish$originalImage();
        int w = contents.width();
        int h = contents.height();
        if (image == null || image.format() != NativeImage.Format.RGBA || image.getWidth() < w || image.getHeight() < h) {
            throw new MissingSpriteException("sprite " + name + " has no readable RGBA image");
        }
        int[] all = image.getPixels();
        int[] frame = new int[w * h];
        for (int y = 0; y < h; y++) {
            System.arraycopy(all, y * image.getWidth(), frame, y * w, w);
        }
        return new IconSource.Layer(name, w, h, frame, tint);
    }

    private static final class MissingSpriteException extends RuntimeException {
        MissingSpriteException(String message) {
            super(message, null, false, false);
        }
    }
}
