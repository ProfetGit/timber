package io.github.profetgit.vanillatimber.server;

//? if <26.1 {
/*import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

// the 26.x SavedDataType codecs, run by hand: before 1.21.5 a SavedData reads and writes its own CompoundTag
final class SavedCodec {
    private SavedCodec() {}

    static <T> T load(Codec<T> codec, CompoundTag tag, HolderLookup.Provider lookup, Supplier<T> fallback) {
        return codec.parse(ops(lookup), tag).result().orElseGet(fallback);
    }

    static <T> CompoundTag save(Codec<T> codec, T value, CompoundTag out, HolderLookup.Provider lookup) {
        Tag encoded = codec.encodeStart(ops(lookup), value).result().orElse(null);
        if (encoded instanceof CompoundTag c) out.merge(c);
        return out;
    }
*///?}
//? if >=1.20.5 <26.1 {
/*
    private static DynamicOps<Tag> ops(HolderLookup.Provider lookup) {
        return lookup.createSerializationContext(NbtOps.INSTANCE);
    }
*///?}
//? if <1.20.5 {
/*
    private static DynamicOps<Tag> ops(HolderLookup.Provider lookup) {
        return lookup == null ? NbtOps.INSTANCE : net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE, lookup);
    }

    // 1.20.1 has no SavedData.Factory (dev/renames.json points it here): the storage takes the loader, the maker and the file name
    record Factory<T>(Supplier<T> create, java.util.function.BiFunction<CompoundTag, HolderLookup.Provider, T> read, net.minecraft.util.datafix.DataFixTypes fix) {
        java.util.function.Function<CompoundTag, T> load() {
            return tag -> read.apply(tag, null);
        }
    }
*///?}
//? if <26.1 {
/*}
*///?}
