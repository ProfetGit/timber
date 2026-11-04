package io.github.profetgit.vanillatimber.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;

/**
 * Publishes {@link GroundShadow}'s limits as the client light engine's sky layers. The engine keeps computing the real
 * light in its own maps; only the published map (what chunk meshes, Sodium's section copies and entity light read) holds
 * copies of the limited sections. The storage mixin applies them whenever the engine swaps a new map in, and
 * {@link #publish} swaps one in when a limit changed, re-meshing only the sections whose values differ.
 */
public final class LightHold {
    public interface Storage {
        void vanilla_timber$republish();

        DataLayer vanilla_timber$live(long node);
    }

    /** Tests: sections handed to the mesher by a publish. */
    public static volatile int dirtied;
    private static Long2ObjectOpenHashMap<DataLayer> published = new Long2ObjectOpenHashMap<>();
    private static Storage sky;
    private static ClientLevel marking;

    private LightHold() {}

    public static void register(LightLayer layer, Storage storage) {
        if (layer == LightLayer.SKY) sky = storage;
    }

    static DataLayer live(long node) {
        return sky == null ? null : sky.vanilla_timber$live(node);
    }

    /** The engine's value for a section without a layer: the bottom plane of the first layer above, else open sky. */
    static DataLayer above(long node) {
        for (int i = 0; i < 40; i++) {
            node = SectionPos.offset(node, 0, 1, 0);
            DataLayer d = live(node);
            if (d != null) return d;
        }
        return null;
    }

    /** The engine's sky light at a block (tests). */
    public static int liveAt(BlockPos pos) {
        long node = SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        DataLayer d = live(node);
        if (d == null) d = above(node);
        if (d == null) return 15;
        return d.get(pos.getX() & 15, live(node) == null ? 0 : pos.getY() & 15, pos.getZ() & 15);
    }

    static void clear() {
        published = new Long2ObjectOpenHashMap<>();
        sky = null;
    }

    /** Swaps a new published map in with the zones' limits, and re-meshes what changed. */
    static void publish(ClientLevel level) {
        if (sky == null) return;
        marking = level;
        try {
            sky.vanilla_timber$republish();
        } finally {
            marking = null;
        }
    }

    /** Called by the storage mixin on a map about to be published; {@code own}: it is a copy of the published one, not a fresh copy of the engine's. */
    public static void apply(LightLayer layer, DataLayerStorageMap<?> map, boolean own) {
        if (layer != LightLayer.SKY || sky == null || published.isEmpty() && GroundShadow.nodes().isEmpty()) return;
        long t0 = System.nanoTime();
        Long2ObjectOpenHashMap<DataLayer> next = new Long2ObjectOpenHashMap<>();
        LongOpenHashSet done = new LongOpenHashSet();
        for (long node : GroundShadow.nodes()) {
            DataLayer base = live(node);
            DataLayer out = base == null ? flat(node) : base.copy();
            if (!GroundShadow.limit(node, out)) continue;
            map.setLayer(node, out);
            next.put(node, out);
            if (own) compare(node, published.get(node), out, base, done);
        }
        if (own) {
            for (long node : published.keySet()) {
                if (next.containsKey(node)) continue;
                DataLayer base = live(node);
                if (base != null) map.setLayer(node, base);
                else map.removeLayer(node);
                compare(node, published.get(node), base, null, done);
            }
        }
        published = next;
        if (own && marking != null) {
            for (long n : done) marking.getChunkSource().onLightUpdate(LightLayer.SKY, SectionPos.of(n));
            dirtied += done.size();
        }
        ClientFalls.spent += System.nanoTime() - t0;
    }

    private static DataLayer flat(long node) {
        DataLayer above = above(node);
        DataLayer out = new DataLayer(15);
        if (above == null) return out;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int v = above.get(x, 0, z);
            if (v != 15) for (int y = 0; y < 16; y++) out.set(x, y, z, v);
        }
        return out;
    }

    /** Adds the section and the neighbours that sample across the border of the cells where {@code a} and {@code b} differ. */
    private static void compare(long node, DataLayer a, DataLayer b, DataLayer fallback, LongOpenHashSet done) {
        if (a == null) a = fallback;
        int flags = 0;
        if (a == null || b == null) {
            flags = 0x7F;
        } else if (a != b) {
            byte[] da = a.getData(), db = b.getData();
            for (int i = 0; i < 2048; i++) {
                if (da[i] == db[i]) continue;
                flags |= 1;
                for (int n = 0; n < 2; n++) {
                    if ((da[i] >> 4 * n & 15) == (db[i] >> 4 * n & 15)) continue;
                    int idx = i * 2 + n, x = idx & 15, z = idx >> 4 & 15, y = idx >> 8;
                    flags |= (x == 0 ? 2 : 0) | (x == 15 ? 4 : 0) | (y == 0 ? 8 : 0) | (y == 15 ? 16 : 0) | (z == 0 ? 32 : 0) | (z == 15 ? 64 : 0);
                }
            }
        }
        if (flags == 0) return;
        for (int dx = (flags & 2) != 0 ? -1 : 0; dx <= ((flags & 4) != 0 ? 1 : 0); dx++) {
            for (int dy = (flags & 8) != 0 ? -1 : 0; dy <= ((flags & 16) != 0 ? 1 : 0); dy++) {
                for (int dz = (flags & 32) != 0 ? -1 : 0; dz <= ((flags & 64) != 0 ? 1 : 0); dz++) done.add(SectionPos.offset(node, dx, dy, dz));
            }
        }
    }
}
