package io.github.profetgit.vanillatimber.client;

import io.github.profetgit.vanillatimber.net.TreeFall;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;

/**
 * Keeps the sky light the ground had under the standing tree visible for a few ticks after the chop. The client light
 * engine keeps computing the real light underneath; only the published layers (what chunk meshes read) are swapped for
 * snapshots taken when the payload arrives, before the blocks go, so the canopy shadow does not pop away. Then the held
 * light hands over to {@link GroundShadow} in steps (SeamlessDisplays' LightPin, sky only).
 */
public final class LightHold {
    public interface Storage {
        void vanilla_timber$republish();

        void vanilla_timber$restore(LongOpenHashSet nodes);

        DataLayer vanilla_timber$live(long node);
    }

    public static volatile boolean on = !"false".equals(System.getProperty("vanilla_timber.lightHold"));
    private static final int HOLD_TICKS = 4, FADE_TICKS = 8, MAX_SECTIONS = 300, MARGIN = 8;
    private static final Long2ObjectOpenHashMap<DataLayer> PINS = new Long2ObjectOpenHashMap<>(), CUR = new Long2ObjectOpenHashMap<>();
    private static float weight = 1;
    private static Storage sky;
    private static long armed;

    private LightHold() {}

    public static void register(LightLayer layer, Storage storage) {
        if (layer == LightLayer.SKY) sky = storage;
    }

    /** Called by the storage mixin while it publishes a new map. */
    public static void apply(LightLayer layer, DataLayerStorageMap<?> map) {
        if (layer != LightLayer.SKY || PINS.isEmpty()) return;
        for (Long2ObjectMap.Entry<DataLayer> e : (weight >= 0.999f ? PINS : CUR).long2ObjectEntrySet()) map.setLayer(e.getLongKey(), e.getValue());
    }

    static boolean active() {
        return !PINS.isEmpty();
    }

    static float weight() {
        return weight;
    }

    /** Pins the sections around the standing tree as the client shows them now. */
    static void arm(ClientLevel level, TreeFall msg, long tick) {
        if (!on || sky == null || !level.dimensionType().hasSkyLight()) return;
        if (active()) release(level);
        int[] cells = msg.cells();
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (int i = 0; i < msg.blocks(); i++) {
            x0 = Math.min(x0, cells[i * 4]);
            x1 = Math.max(x1, cells[i * 4]);
            y0 = Math.min(y0, cells[i * 4 + 1]);
            y1 = Math.max(y1, cells[i * 4 + 1]);
            z0 = Math.min(z0, cells[i * 4 + 2]);
            z1 = Math.max(z1, cells[i * 4 + 2]);
        }
        net.minecraft.core.BlockPos o = msg.origin();
        int sx0 = SectionPos.blockToSectionCoord(o.getX() + x0 - MARGIN), sx1 = SectionPos.blockToSectionCoord(o.getX() + x1 + MARGIN);
        int sy0 = SectionPos.blockToSectionCoord(o.getY() + y0 - 16), sy1 = SectionPos.blockToSectionCoord(o.getY() + y1);
        int sz0 = SectionPos.blockToSectionCoord(o.getZ() + z0 - MARGIN), sz1 = SectionPos.blockToSectionCoord(o.getZ() + z1 + MARGIN);
        if ((long) (sx1 - sx0 + 1) * (sy1 - sy0 + 1) * (sz1 - sz0 + 1) > MAX_SECTIONS) return;
        var listener = level.getLightEngine().getLayerListener(LightLayer.SKY);
        for (int sx = sx0; sx <= sx1; sx++) {
            for (int sy = sy0; sy <= sy1; sy++) {
                for (int sz = sz0; sz <= sz1; sz++) {
                    DataLayer d = listener.getDataLayerData(SectionPos.of(sx, sy, sz));
                    if (d != null) PINS.put(SectionPos.asLong(sx, sy, sz), d);
                }
            }
        }
        weight = 1;
        armed = tick;
    }

    /** Once per client tick: steps the held light down to the live light, or lets go when no fall is left. */
    static void tick(ClientLevel level, long tick, boolean falls) {
        if (!active()) return;
        if (!falls) {
            release(level);
            return;
        }
        float w = Math.max(0, Math.min(1, 1 - (tick - armed - HOLD_TICKS) / (float) FADE_TICKS));
        w = (float) Math.ceil(w * 4) / 4;
        if (w <= 0) {
            release(level);
        } else if (w != weight) {
            weight = w;
            CUR.clear();
            if (w < 0.999f) blend(w);
            sky.vanilla_timber$republish();
            dirty(level, PINS.keySet());
        }
    }

    static void release(ClientLevel level) {
        if (!active()) return;
        LongOpenHashSet all = new LongOpenHashSet(PINS.keySet());
        LongOpenHashSet changed = new LongOpenHashSet();
        for (Long2ObjectMap.Entry<DataLayer> e : PINS.long2ObjectEntrySet()) {
            DataLayer live = sky.vanilla_timber$live(e.getLongKey());
            if (live != null && live != e.getValue() && differs(live, e.getValue())) changed.add(e.getLongKey());
        }
        PINS.clear();
        CUR.clear();
        weight = 1;
        sky.vanilla_timber$restore(all);
        if (level != null) dirty(level, changed);
    }

    static void clear() {
        PINS.clear();
        CUR.clear();
        weight = 1;
    }

    private static boolean differs(DataLayer a, DataLayer b) {
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) if (a.get(x, y, z) != b.get(x, y, z)) return true;
        return false;
    }

    private static void blend(float w) {
        for (Long2ObjectMap.Entry<DataLayer> e : PINS.long2ObjectEntrySet()) {
            DataLayer live = sky.vanilla_timber$live(e.getLongKey());
            DataLayer old = e.getValue();
            if (live == null) {
                CUR.put(e.getLongKey(), old);
                continue;
            }
            DataLayer out = new DataLayer();
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        int a = old.get(x, y, z), b = live.get(x, y, z);
                        out.set(x, y, z, a == b ? a : Math.round(b + (a - b) * w));
                    }
                }
            }
            CUR.put(e.getLongKey(), out);
        }
    }

    /** Re-meshes the sections and their neighbours (faces sample across the border). */
    private static void dirty(ClientLevel level, Iterable<Long> nodes) {
        LongOpenHashSet done = new LongOpenHashSet();
        for (long node : nodes) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long n = SectionPos.offset(node, dx, dy, dz);
                        if (done.add(n)) level.getChunkSource().onLightUpdate(LightLayer.SKY, SectionPos.of(n));
                    }
                }
            }
        }
    }
}
