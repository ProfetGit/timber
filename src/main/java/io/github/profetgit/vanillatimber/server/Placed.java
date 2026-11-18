package io.github.profetgit.vanillatimber.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.profetgit.vanillatimber.Timber;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21.5
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * PRD 5.7 / 8: logs placed by players, per dimension ({@code vanilla_timber/placed}), packed positions keyed by chunk.
 * Recorded by {@code BlockItemMixin} at the placed position (1.3.6 marked the looked-at log instead). A key whose block
 * is no longer a log is dropped when it is next read, so a log that went by fire, explosion or piston forgets itself.
 */
public final class Placed extends SavedData {
    static final int FORMAT = 1;
    private static final Codec<Placed> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("format", FORMAT).forGetter(d -> FORMAT),
        Codec.unboundedMap(Codec.STRING, Codec.LONG_STREAM).optionalFieldOf("chunks", Map.of()).forGetter(d -> save(d.chunks)),
        Codec.unboundedMap(Codec.STRING, Codec.LONG_STREAM).optionalFieldOf("landed", Map.of()).forGetter(d -> save(d.landed))
    ).apply(i, (format, chunks, landed) -> new Placed(chunks, landed)));
//? if >=1.21.5 {
    private static final SavedDataType<Placed> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Timber.ID, "placed"),
        () -> new Placed(Map.of(), Map.of()), CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
//?} else {
/*    private static final SavedData.Factory<Placed> TYPE = new SavedData.Factory<>(() -> new Placed(Map.of(), Map.of()),
        (tag, lookup) -> SavedCodec.load(CODEC, tag, lookup, () -> new Placed(Map.of(), Map.of())), DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        return SavedCodec.save(CODEC, this, tag, lookup);
    }
*///?}

    private final Long2ObjectOpenHashMap<LongOpenHashSet> chunks = new Long2ObjectOpenHashMap<>();

    /** Logs a fall left lying on the ground ({@code land_blocks}): never felled again, whatever {@code protect} says. */
    private final Long2ObjectOpenHashMap<LongOpenHashSet> landed = new Long2ObjectOpenHashMap<>();

    private Placed(Map<String, LongStream> saved, Map<String, LongStream> lying) {
        load(saved, chunks);
        load(lying, landed);
    }

    private static void load(Map<String, LongStream> saved, Long2ObjectOpenHashMap<LongOpenHashSet> chunks) {
        saved.forEach((k, v) -> {
            int c = k.indexOf(',');
            if (c < 0) return;
            try {
                long key = ChunkPos.pack(Integer.parseInt(k.substring(0, c)), Integer.parseInt(k.substring(c + 1)));
                LongOpenHashSet set = new LongOpenHashSet();
                v.forEach(set::add);
                if (!set.isEmpty()) chunks.put(key, set);
            } catch (NumberFormatException ignored) {
            }
        });
    }

    private static Map<String, LongStream> save(Long2ObjectOpenHashMap<LongOpenHashSet> chunks) {
        Map<String, LongStream> out = new HashMap<>();
        chunks.long2ObjectEntrySet().fastForEach(e -> {
            if (e.getValue().isEmpty()) return;
            out.put(ChunkPos.getX(e.getLongKey()) + "," + ChunkPos.getZ(e.getLongKey()), LongStream.of(e.getValue().toLongArray()));
        });
        return out;
    }

    static Placed get(ServerLevel level) {
//? if >=1.21.5 {
        return level.getDataStorage().computeIfAbsent(TYPE);
//?} else {
/*        return level.getDataStorage().computeIfAbsent(TYPE, "vanilla_timber_placed");
*///?}
    }

    private static long chunk(BlockPos pos) {
        return ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static boolean isLog(BlockState s) {
        return TreeTypes.forTrunk(s) != null;
    }

    /** A player put a log at {@code pos}. */
    public static void add(ServerLevel level, BlockPos pos) {
        Placed d = get(level);
        if (d.chunks.computeIfAbsent(chunk(pos), k -> new LongOpenHashSet()).add(pos.asLong())) d.setDirty();
    }

    /** A fall left a log lying at {@code pos}. */
    public static void addLanded(ServerLevel level, BlockPos pos) {
        Placed d = get(level);
        if (d.landed.computeIfAbsent(chunk(pos), k -> new LongOpenHashSet()).add(pos.asLong())) d.setDirty();
    }

    /** Forgets {@code pos}, placed or landed; true if it was recorded either way. */
    public static boolean remove(ServerLevel level, BlockPos pos) {
        return take(level, pos) != 0;
    }

    /** As {@link #remove}, saying what it was: bit 0 placed by a player, bit 1 left lying by a fall. */
    public static int take(ServerLevel level, BlockPos pos) {
        Placed d = get(level);
        return (d.drop(d.chunks, pos) ? 1 : 0) | (d.drop(d.landed, pos) ? 2 : 0);
    }

    private boolean drop(Long2ObjectOpenHashMap<LongOpenHashSet> map, BlockPos pos) {
        long c = chunk(pos);
        LongOpenHashSet set = map.get(c);
        if (set == null || !set.remove(pos.asLong())) return false;
        if (set.isEmpty()) map.remove(c);
        setDirty();
        return true;
    }

    /** The log at {@code pos} was left lying by a fall; a stale key is dropped. */
    public static boolean landed(ServerLevel level, BlockPos pos) {
        return get(level).landedHolds(level, pos);
    }

    /** Whether the log at {@code pos} must never fell: placed by a player with {@code protect} on, or left lying by a fall. */
    public static boolean shielded(ServerLevel level, BlockPos pos) {
        Placed d = get(level);
        return Config.on(Config.Key.PROTECT) && d.holds(level, pos) || d.landedHolds(level, pos);
    }

    boolean landedHolds(ServerLevel level, BlockPos pos) {
        LongOpenHashSet set = landed.get(chunk(pos));
        if (set == null || !set.contains(pos.asLong())) return false;
        if (isLog(level.getBlockState(pos))) return true;
        drop(landed, pos);
        return false;
    }

    /** Every landed position in the level (tests). */
    public static LongOpenHashSet allLanded(ServerLevel level) {
        LongOpenHashSet out = new LongOpenHashSet();
        for (LongOpenHashSet s : get(level).landed.values()) out.addAll(s);
        return out;
    }

    /** The log at {@code pos} was placed by a player; a stale key (no log there any more) is dropped. */
    public static boolean has(ServerLevel level, BlockPos pos) {
        return get(level).holds(level, pos);
    }

    boolean holds(ServerLevel level, BlockPos pos) {
        LongOpenHashSet set = chunks.get(chunk(pos));
        if (set == null || !set.contains(pos.asLong())) return false;
        if (isLog(level.getBlockState(pos))) return true;
        remove(level, pos);
        return false;
    }

    /** Every recorded position in the level (tests). */
    public static LongOpenHashSet all(ServerLevel level) {
        LongOpenHashSet out = new LongOpenHashSet();
        for (LongOpenHashSet s : get(level).chunks.values()) out.addAll(s);
        return out;
    }
}
