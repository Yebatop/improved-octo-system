package dev.skirmish.module.killcard;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

/** Reads a player's 8×8 face (with the hat layer) from the skin already loaded by the client. Client thread. */
final class FaceReader {
    private FaceReader() {
    }

    static int @Nullable [] face(Minecraft mc, @Nullable UUID uuid) {
        if (uuid == null || mc.getConnection() == null) {
            return null;
        }
        PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
        if (info == null) {
            return null;
        }
        Identifier id = info.getSkin().body().texturePath();
        try {
            Optional<Resource> resource = mc.getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                    return crop(image);
                }
            }
            AbstractTexture texture = mc.getTextureManager().getTexture(id);
            if (texture instanceof DynamicTexture dynamic && dynamic.getPixels() != null) {
                return crop(dynamic.getPixels());
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /** Face (8,8)-(16,16) with the hat (40,8)-(48,16) blended over it; ARGB. */
    private static int[] crop(NativeImage skin) {
        int[] out = new int[64];
        float scale = skin.getWidth() / 64f;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int base = skin.getPixel((int) ((8 + x) * scale), (int) ((8 + y) * scale)) | 0xFF000000;
                int hat = skin.getPixel((int) ((40 + x) * scale), (int) ((8 + y) * scale));
                out[y * 8 + x] = blend(base, hat);
            }
        }
        return out;
    }

    private static int blend(int under, int over) {
        int a = over >>> 24;
        if (a == 0) {
            return under;
        }
        int r = (((over >> 16) & 0xFF) * a + ((under >> 16) & 0xFF) * (255 - a)) / 255;
        int g = (((over >> 8) & 0xFF) * a + ((under >> 8) & 0xFF) * (255 - a)) / 255;
        int b = ((over & 0xFF) * a + (under & 0xFF) * (255 - a)) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
