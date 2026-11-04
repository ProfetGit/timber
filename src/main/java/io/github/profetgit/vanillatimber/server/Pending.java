package io.github.profetgit.vanillatimber.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.profetgit.vanillatimber.Timber;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21.5
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * PRD 8: the loot of each fall in progress, per dimension. A fall writes its entry on the tick its blocks leave the world
 * and strikes each stack as it drops; what is left after a crash spawns at the stump on the next load.
 */
public final class Pending extends SavedData {
    static final int FORMAT = 1;

    /** One fall's undropped stacks and the stump they drop at. */
    public static final class Entry {
        final double x, y, z;
        final List<ItemStack> items;
        private Pending owner;

        Entry(double x, double y, double z, List<ItemStack> items) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.items = new ArrayList<>(items);
        }

        /** The stack at {@code stack} (by identity) dropped; the entry goes with its last stack. */
        void drop(ItemStack stack) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) != stack) continue;
                items.remove(i);
                if (items.isEmpty()) close();
                else if (owner != null) owner.setDirty();
                return;
            }
        }

        void close() {
            if (owner == null) return;
            owner.entries.remove(this);
            owner.setDirty();
            owner = null;
        }
    }

    private static final Codec<Entry> ENTRY = RecordCodecBuilder.create(i -> i.group(
        Codec.DOUBLE.fieldOf("x").forGetter(e -> e.x),
        Codec.DOUBLE.fieldOf("y").forGetter(e -> e.y),
        Codec.DOUBLE.fieldOf("z").forGetter(e -> e.z),
        ItemStack.CODEC.listOf().fieldOf("items").forGetter(e -> e.items)
    ).apply(i, Entry::new));
    private static final Codec<Pending> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("format", FORMAT).forGetter(d -> FORMAT),
        ENTRY.listOf().optionalFieldOf("falls", List.of()).forGetter(d -> d.entries)
    ).apply(i, (format, falls) -> new Pending(falls)));
//? if >=1.21.5 {
    private static final SavedDataType<Pending> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Timber.ID, "pending"),
        () -> new Pending(List.of()), CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
//?} else {
/*    private static final SavedData.Factory<Pending> TYPE = new SavedData.Factory<>(() -> new Pending(List.of()),
        (tag, lookup) -> SavedCodec.load(CODEC, tag, lookup, () -> new Pending(List.of())), DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        return SavedCodec.save(CODEC, this, tag, lookup);
    }
*///?}

    private final List<Entry> entries = new ArrayList<>();

    private Pending(List<Entry> saved) {
        for (Entry e : saved) {
            e.owner = this;
            entries.add(e);
        }
    }

    static Pending get(ServerLevel level) {
//? if >=1.21.5 {
        return level.getDataStorage().computeIfAbsent(TYPE);
//?} else {
/*        return level.getDataStorage().computeIfAbsent(TYPE, "vanilla_timber_pending");
*///?}
    }

    /** A fall's entry at its stump, or null when nothing is left to drop. */
    static Entry open(ServerLevel level, double x, double y, double z, List<ItemStack> stacks) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack s : stacks) if (!s.isEmpty()) left.add(s);
        if (left.isEmpty()) return null;
        Pending d = get(level);
        Entry e = new Entry(x, y, z, left);
        e.owner = d;
        d.entries.add(e);
        d.setDirty();
        return e;
    }

    /** On a server start: every entry left by a crash spawns its items at the stump and goes. */
    static void returnAll(MinecraftServer server) {
        int items = 0, falls = 0;
        for (ServerLevel level : server.getAllLevels()) {
            Pending d = get(level);
            if (d.entries.isEmpty()) continue;
            for (Entry e : d.entries) {
                level.getChunk(BlockPos.containing(e.x, e.y, e.z));
                for (ItemStack s : e.items) {
                    if (s.isEmpty()) continue;
                    items += s.getCount();
                    ItemEntity item = new ItemEntity(level, e.x, e.y + 0.5, e.z, s, 0, 0.25, 0);
                    item.setPickUpDelay(10);
                    level.addFreshEntity(item);
                }
                falls++;
            }
            d.entries.clear();
            d.setDirty();
        }
        if (falls > 0) Timber.LOG.info("Timber: returned {} items from {} falls interrupted by a crash", items, falls);
    }

    /** Item id -> count still pending in the level (tests). */
    public static Map<String, Integer> items(ServerLevel level) {
        Map<String, Integer> out = new TreeMap<>();
        for (Entry e : get(level).entries) {
            for (ItemStack s : e.items) out.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        return out;
    }

    /** Entries in the level (tests). */
    public static int falls(ServerLevel level) {
        return get(level).entries.size();
    }
}
