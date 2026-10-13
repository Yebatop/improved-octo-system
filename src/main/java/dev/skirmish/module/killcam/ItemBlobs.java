package dev.skirmish.module.killcam;

import dev.skirmish.module.killcam.library.ReplayRecording;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * Equipment stacks of saved replays: the full stack as NBT ({@code ItemStack.CODEC} with the connection's
 * registries) plus id, count and glint as a fallback. Decoding falls back to a plain stack of the same item (with
 * the glint) when the stored components cannot be read, e.g. the replay comes from a server with other
 * enchantments; unknown items become empty slots.
 */
final class ItemBlobs {
    private static final long MAX_NBT_BYTES = 2L << 20;

    private ItemBlobs() {
    }

    /** Null for empty stacks. {@code registries} null (world already gone) stores the fallback only. */
    static ReplayRecording.@Nullable Item encode(ItemStack stack, @Nullable RegistryAccess registries, KillCamModule module) {
        if (stack.isEmpty()) {
            return null;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        byte[] data = new byte[0];
        if (registries != null) {
            try {
                Tag tag = ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
                try (DataOutputStream out = new DataOutputStream(bytes)) {
                    NbtIo.writeAnyTag(tag, out);
                }
                data = bytes.toByteArray();
            } catch (IOException | RuntimeException e) {
                module.log("replay save: %s stored without components (%s)", id, e.getMessage());
            }
        }
        return new ReplayRecording.Item(id, stack.getCount(), stack.hasFoil(), data);
    }

    static ItemStack decode(ReplayRecording.Item item, RegistryAccess registries, KillCamModule module) {
        if (item.data().length > 0) {
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(item.data()))) {
                Tag tag = NbtIo.readAnyTag(in, NbtAccounter.create(MAX_NBT_BYTES));
                Optional<ItemStack> stack = ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result();
                if (stack.isPresent()) {
                    return stack.get();
                }
                module.log("replay load: %s components not readable here, showing a plain copy", item.id());
            } catch (IOException | RuntimeException e) {
                module.log("replay load: %s components damaged (%s), showing a plain copy", item.id(), e.getMessage());
            }
        }
        Identifier id = Identifier.tryParse(item.id());
        Optional<Item> known = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
        if (known.isEmpty()) {
            module.log("replay load: unknown item %s, slot left empty", item.id());
            return ItemStack.EMPTY;
        }
        ItemStack plain = new ItemStack(known.get(), Math.max(1, Math.min(item.count(), 99)));
        if (item.foil()) {
            plain.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return plain;
    }
}
