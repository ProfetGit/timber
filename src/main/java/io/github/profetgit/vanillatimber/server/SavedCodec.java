package io.github.profetgit.vanillatimber.server;

//? if <1.21.2 {
/*import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

// the 26.x SavedDataType codecs, run by hand: before 1.21.2 a SavedData reads and writes its own CompoundTag
final class SavedCodec {
    private SavedCodec() {}

    static <T> T load(Codec<T> codec, CompoundTag tag, HolderLookup.Provider lookup, Supplier<T> fallback) {
        return codec.parse(lookup.createSerializationContext(NbtOps.INSTANCE), tag).result().orElseGet(fallback);
    }

    static <T> CompoundTag save(Codec<T> codec, T value, CompoundTag out, HolderLookup.Provider lookup) {
        Tag encoded = codec.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE), value).result().orElse(null);
        if (encoded instanceof CompoundTag c) out.merge(c);
        return out;
    }
}
*///?}
