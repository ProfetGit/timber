package io.github.profetgit.vanillatimber.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.profetgit.vanillatimber.Timber;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * PRD 5.13 / 8: chop mode's cuts per dimension ({@code vanilla_timber/chops}). A cut is one layer of one tree: the tree's
 * key (its lowest root cell), the cell last chopped, the chops so far, the chops the tree takes, and the layer's cells. A
 * tree can have several cuts, one per height. The saved shape is 2.1's first one ({@code base, last, chops, needed,
 * cells: [{pos, n}]}); an entry from before the layers (cells at several heights, or none) reads as a cut at its last
 * cell's height, and the next chop there gives it the full layer.
 */
public final class ChopData extends SavedData {
    static final int FORMAT = 2;

    static final class Entry {
        final long base;
        long last;
        int chops, needed;
        final LongLinkedOpenHashSet cells = new LongLinkedOpenHashSet();

        Entry(long base, long last, int chops, int needed, List<Cell> saved) {
            this.base = base;
            this.last = last;
            this.chops = chops;
            this.needed = needed;
            int y = BlockPos.getY(last);
            for (Cell c : saved) if (BlockPos.getY(c.pos()) == y) cells.add(c.pos());
            if (cells.isEmpty()) cells.add(last);
        }

        List<Cell> cellList() {
            List<Cell> out = new ArrayList<>(cells.size());
            for (long c : cells) out.add(new Cell(c, chops));
            return out;
        }

        boolean overlaps(long[] layer) {
            for (long c : layer) if (cells.contains(c)) return true;
            return false;
        }
    }

    record Cell(long pos, int n) {}

    private static final Codec<Cell> CELL = RecordCodecBuilder.create(i -> i.group(
        Codec.LONG.fieldOf("pos").forGetter(Cell::pos),
        Codec.INT.fieldOf("n").forGetter(Cell::n)
    ).apply(i, Cell::new));

    private static final Codec<Entry> ENTRY = RecordCodecBuilder.create(i -> i.group(
        Codec.LONG.fieldOf("base").forGetter(e -> e.base),
        Codec.LONG.fieldOf("last").forGetter(e -> e.last),
        Codec.INT.fieldOf("chops").forGetter(e -> e.chops),
        Codec.INT.fieldOf("needed").forGetter(e -> e.needed),
        CELL.listOf().optionalFieldOf("cells", List.of()).forGetter(Entry::cellList)
    ).apply(i, Entry::new));
    private static final Codec<ChopData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("format", FORMAT).forGetter(d -> FORMAT),
        ENTRY.listOf().optionalFieldOf("trees", List.of()).forGetter(ChopData::all)
    ).apply(i, (format, trees) -> new ChopData(trees)));
    private static final SavedDataType<ChopData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Timber.ID, "chops"),
        () -> new ChopData(List.of()), CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    /** Self-test: entries saved before the layers, decoded: {chops, cells...} of each. */
    public static List<long[]> selfTestOldFormat(String json) {
        List<long[]> out = new ArrayList<>();
        for (Entry e : CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(json)).getOrThrow().all()) {
            long[] row = new long[e.cells.size() + 1];
            row[0] = e.chops;
            int k = 1;
            for (long c : e.cells) row[k++] = c;
            out.add(row);
        }
        return out;
    }

    /** Cuts by tree key. */
    private final Long2ObjectOpenHashMap<List<Entry>> trees = new Long2ObjectOpenHashMap<>();

    private ChopData(List<Entry> saved) {
        for (Entry e : saved) {
            List<Entry> cuts = trees.computeIfAbsent(e.base, k -> new ArrayList<>());
            // two old entries can't share a key, but two cuts read from them could share a layer: the first wins
            boolean clash = false;
            for (Entry o : cuts) clash |= o.overlaps(e.cells.toLongArray());
            if (!clash) cuts.add(e);
        }
    }

    static ChopData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** The cut of tree {@code base} that shares a cell with {@code layer}, or null. */
    Entry find(long base, long[] layer) {
        List<Entry> cuts = trees.get(base);
        if (cuts == null) return null;
        for (Entry e : cuts) if (e.overlaps(layer)) return e;
        return null;
    }

    List<Entry> of(long base) {
        List<Entry> cuts = trees.get(base);
        return cuts == null ? List.of() : new ArrayList<>(cuts);
    }

    Entry create(long base, long last) {
        Entry e = new Entry(base, last, 0, 1, List.of());
        trees.computeIfAbsent(base, k -> new ArrayList<>()).add(e);
        setDirty();
        return e;
    }

    void remove(Entry e) {
        List<Entry> cuts = trees.get(e.base);
        if (cuts == null || !cuts.remove(e)) return;
        if (cuts.isEmpty()) trees.remove(e.base);
        setDirty();
    }

    List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        for (List<Entry> cuts : trees.values()) out.addAll(cuts);
        return out;
    }
}
