package dev.skirmish.module.worldmap;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * One 512×512-block region of the map: an RGBA image (1 px per block, transparent where nothing was seen) and its
 * GPU texture, created and re-uploaded on the render thread when the image changed.
 */
final class MapTile {
    static final int SIZE = 512;
    final int rx;
    final int rz;
    final NativeImage image;
    private @Nullable DynamicTexture texture;
    private final Identifier id;
    boolean textureDirty = true;
    boolean unsaved;
    long lastUsed;

    MapTile(int rx, int rz, NativeImage image, String contextKey) {
        this.rx = rx;
        this.rz = rz;
        this.image = image;
        this.id = Identifier.fromNamespaceAndPath("skirmish", "worldmap/" + Integer.toHexString(contextKey.hashCode()) + "/"
                + (rx < 0 ? "m" + -rx : rx) + "_" + (rz < 0 ? "m" + -rz : rz));
    }

    void set(int px, int pz, int argb) {
        if (image.getPixel(px, pz) != argb) {
            image.setPixel(px, pz, argb);
            textureDirty = true;
            unsaved = true;
        }
    }

    /** The texture id, uploading the image first if it changed. Render thread only. */
    Identifier texture() {
        if (texture == null) {
            texture = new DynamicTexture(() -> "Skirmish map " + rx + "," + rz, image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            textureDirty = false;
        } else if (textureDirty) {
            texture.upload();
            textureDirty = false;
        }
        return id;
    }

    /** Frees the texture (and the image with it). */
    void close() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(id);
            texture = null;
        } else {
            image.close();
        }
    }
}
