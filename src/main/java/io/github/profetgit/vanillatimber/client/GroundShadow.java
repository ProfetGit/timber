package io.github.profetgit.vanillatimber.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * The sky light a falling tree takes from the world, for vanilla rendering (a shader pack casts the real shadow). Every
 * other tick each live fall's light-blocking blocks, where its mesh draws them, are put into a small grid of cells next
 * to the real world's dampening, and the sky light is computed in that grid the way the light engine does it: columns
 * open to the sky are 15, every cell passes on its level minus the target's dampening (at least 1), the grid's rim keeps
 * the engine's values. The engine's own values (which already have the tree gone) are then limited to the result by
 * {@link LightHold}, so terrain, plants, block sides and entities are lit by vanilla's smooth lighting as if the tree
 * stood where the mesh is. Cells a tree block occupies keep the engine's value: the mesh is lit by its own cell.
 */
public final class GroundShadow {
    /** Off with -Dvanilla_timber.groundShadow=false (the tests switch it for before/after sheets). */
    public static volatile boolean on = !"false".equals(System.getProperty("vanilla_timber.groundShadow"));
    private static final int EVERY = 2, FADE_TICKS = 10, REFRESH = 20, MAX_CELLS = 600_000;
    private static final int MARGIN = Integer.getInteger("vanilla_timber.shadowMargin", 8);
    private static final byte[] SOLID = new byte[4096];
    private static final java.util.function.Predicate<BlockState> BLOCKS = s -> s.getLightDampening() > 0;
    private static final List<Zone> ZONES = new ArrayList<>();
    private static final Long2ObjectOpenHashMap<WorldSection> WORLD = new Long2ObjectOpenHashMap<>();
    private static final Voxels VOX = new Voxels();
    private static byte[] damp = new byte[0], lvl = new byte[0], eng = new byte[0];
    private static final int[][] QUEUE = new int[16][];
    private static final int[] QN = new int[16];
    private static boolean suspended;
    /** Tests: how often the field was computed, and the last first-compute's fit to the engine (cells darker / brighter than it). */
    public static volatile int updates, darker, brighter, maxDarker, maxBrighter, region;
    public static volatile boolean stats;
    public static volatile long totalNanos, maxNanos;

    static {
        Arrays.fill(SOLID, (byte) 15);
        for (int i = 0; i < 16; i++) QUEUE[i] = new int[1024];
    }

    private GroundShadow() {}

    static boolean pending() {
        return !ZONES.isEmpty();
    }

    /** Stops the zones at the next tick (a shader pack took over). */
    static void suspend() {
        if (!ZONES.isEmpty()) suspended = true;
    }

    static void start(ClientLevel level, MeshFall f, long ticks) {
        if (!on || suspended || !level.dimensionType().hasSkyLight()) return;
        long t0 = System.nanoTime();
        Zone z = new Zone(f);
        ZONES.add(z);
        z.last = ticks;
        if (z.compute(level, ticks)) LightHold.publish(level);
        long dt = System.nanoTime() - t0;
        totalNanos += dt;
        maxNanos = Math.max(maxNanos, dt);
        ClientFalls.spent += dt;
    }

    static void clear() {
        ZONES.clear();
        WORLD.clear();
        suspended = false;
        LightHold.clear();
    }

    /** Once per client tick, after the falls were ticked. */
    static void tick(ClientLevel level, long ticks) {
        if (ZONES.isEmpty()) return;
        long t0 = System.nanoTime();
        boolean changed = false;
        if (suspended || !on) {
            ZONES.clear();
            suspended = false;
            changed = true;
        }
        for (Iterator<Zone> it = ZONES.iterator(); it.hasNext();) {
            Zone z = it.next();
            if (!z.ending && z.fall.dead) {
                z.ending = true;
                z.endTick = ticks;
            }
            if (z.ending) {
                float w = (float) Math.ceil(Math.max(0, Math.min(1, 1 - (ticks - z.endTick) / (double) FADE_TICKS)) * 4) / 4;
                if (w != z.weight) {
                    z.weight = w;
                    changed = true;
                }
                if (w <= 0) {
                    it.remove();
                    changed = true;
                }
            } else if (ticks - z.last >= EVERY) {
                z.last = ticks;
                changed |= z.compute(level, ticks);
            }
        }
        if (changed) LightHold.publish(level);
        if (ZONES.isEmpty()) WORLD.clear();
        long dt = System.nanoTime() - t0;
        totalNanos += dt;
        maxNanos = Math.max(maxNanos, dt);
        ClientFalls.spent += dt;
    }

    /** The sections the zones limit. */
    static LongOpenHashSet nodes() {
        LongOpenHashSet all = new LongOpenHashSet();
        for (Zone z : ZONES) if (z.weight > 0) all.addAll(z.limits.keySet());
        return all;
    }

    /** Applies every zone's limits for {@code node} to {@code out}, an own copy of the engine's layer; false when none applies. */
    static boolean limit(long node, DataLayer out) {
        boolean any = false;
        for (Zone z : ZONES) {
            byte[] lim = z.limits.get(node);
            if (lim == null || z.weight <= 0) continue;
            any = true;
            float w = z.weight;
            byte[] d = out.getData();
            for (int i = 0; i < 4096; i++) {
                int l = lim[i];
                if (l >= 15) continue;
                int sh = (i & 1) << 2, p = i >> 1;
                int cur = d[p] >> sh & 15;
                if (l >= cur) continue;
                int v = w >= 1 ? l : cur - Math.round((cur - l) * w);
                d[p] = (byte) (d[p] & ~(15 << sh) | v << sh);
            }
        }
        return any;
    }

    /** Light-blocking cells of one fall at one instant. */
    static final class Voxels {
        int[] x = new int[256], y = new int[256], z = new int[256];
        byte[] d = new byte[256];
        int n, x0, x1, y0, y1, z0, z1;

        void clear() {
            n = 0;
            x0 = y0 = z0 = Integer.MAX_VALUE;
            x1 = y1 = z1 = Integer.MIN_VALUE;
        }

        void add(int cx, int cy, int cz, int dampening) {
            if (n == x.length) {
                x = Arrays.copyOf(x, n * 2);
                y = Arrays.copyOf(y, n * 2);
                z = Arrays.copyOf(z, n * 2);
                d = Arrays.copyOf(d, n * 2);
            }
            x[n] = cx;
            y[n] = cy;
            z[n] = cz;
            d[n++] = (byte) dampening;
            x0 = Math.min(x0, cx);
            x1 = Math.max(x1, cx);
            y0 = Math.min(y0, cy);
            y1 = Math.max(y1, cy);
            z0 = Math.min(z0, cz);
            z1 = Math.max(z1, cz);
        }
    }

    private record WorldSection(byte[] damp, long tick) {}

    /** Dampening of the world's blocks in a section (null: none), cached for a second. */
    private static byte[] worldDamp(ClientLevel level, int sx, int sy, int sz, long ticks) {
        long key = SectionPos.asLong(sx, sy, sz);
        WorldSection e = WORLD.get(key);
        if (e != null && ticks - e.tick < REFRESH) return e.damp;
        LevelChunk chunk = level.getChunkSource().getChunkNow(sx, sz);
        byte[] out = null;
        if (chunk == null) {
            out = SOLID;
        } else {
            LevelChunkSection sec = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
            if (!sec.hasOnlyAir() && sec.maybeHas(BLOCKS)) {
                out = new byte[4096];
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) out[y << 8 | z << 4 | x] = (byte) sec.getBlockState(x, y, z).getLightDampening();
            }
        }
        WORLD.put(key, new WorldSection(out, ticks));
        return out;
    }

    private static void push(int level, int idx) {
        int n = QN[level];
        if (n == QUEUE[level].length) QUEUE[level] = Arrays.copyOf(QUEUE[level], n * 2);
        QUEUE[level][n] = idx;
        QN[level] = n + 1;
    }

    /** One fall's light field. */
    static final class Zone {
        final MeshFall fall;
        final int[] ox, oy, oz;
        final BlockState[] os;
        Long2ObjectOpenHashMap<byte[]> limits = new Long2ObjectOpenHashMap<>();
        float weight = 1;
        boolean ending;
        long last, endTick;

        Zone(MeshFall f) {
            fall = f;
            int n = f.msg.blocks();
            int[] cells = f.msg.cells();
            BlockPos o = f.msg.origin();
            ox = new int[n];
            oy = new int[n];
            oz = new int[n];
            os = new BlockState[n];
            for (int i = 0; i < n; i++) {
                ox[i] = o.getX() + cells[i * 4];
                oy[i] = o.getY() + cells[i * 4 + 1];
                oz[i] = o.getZ() + cells[i * 4 + 2];
                os[i] = f.msg.palette().get(cells[i * 4 + 3] >> 1);
            }
        }

        /** Recomputes the limits from the fall as it is now; true when they changed. */
        boolean compute(ClientLevel level, long ticks) {
            Voxels v = VOX;
            v.clear();
            fall.shadowCells(ticks, v);
            if (v.n == 0) {
                boolean had = !limits.isEmpty();
                limits = new Long2ObjectOpenHashMap<>();
                return had;
            }
            int m = MARGIN;
            int x0 = v.x0 - m, x1 = v.x1 + m, z0 = v.z0 - m, z1 = v.z1 + m;
            int y0 = Math.max(level.getMinY(), v.y0 - m), y1 = Math.min(level.getMaxY(), v.y1 + 2);
            int nx = x1 - x0 + 1, ny = y1 - y0 + 1, nz = z1 - z0 + 1;
            if (ny < 3 || (long) nx * ny * nz > MAX_CELLS) return false;
            int size = nx * ny * nz, nxz = nx * nz;
            if (damp.length < size) {
                damp = new byte[size];
                lvl = new byte[size];
                eng = new byte[size];
            }
            updates++;
            region = size;
            fill(level, ticks, x0, y0, z0, x1, y1, z1, nx, nz);
            mask(level, x0, y0, z0, nx, ny, nz);
            for (int i = 0; i < v.n; i++) {
                if (v.y[i] < y0 || v.y[i] > y1) continue;
                int idx = ((v.y[i] - y0) * nz + (v.z[i] - z0)) * nx + (v.x[i] - x0);
                if (v.d[i] > damp[idx]) damp[idx] = v.d[i];
            }
            Arrays.fill(lvl, 0, size, (byte) 0);
            Arrays.fill(QN, 0);
            shell(nx, ny, nz);
            for (int x = 1; x < nx - 1; x++) {
                for (int z = 1; z < nz - 1; z++) {
                    if (eng[((ny - 1) * nz + z) * nx + x] != 15) continue;
                    for (int y = ny - 2; y >= 1; y--) {
                        int idx = (y * nz + z) * nx + x;
                        if (damp[idx] > 0) break;
                        lvl[idx] = 15;
                        push(15, idx);
                    }
                }
            }
            spread(nx, ny, nz, nxz);
            for (int i = 0; i < v.n; i++) {
                if (v.y[i] < y0 || v.y[i] > y1) continue;
                int idx = ((v.y[i] - y0) * nz + (v.z[i] - z0)) * nx + (v.x[i] - x0);
                lvl[idx] = 15;
                eng[idx] = 15;
            }
            if (stats) fit(x0, y0, z0, nx, ny, nz, size);
            return store(x0, y0, z0, x1, y1, z1, nx, nz);
        }

        /** The world's dampening and the engine's sky light for the box, section by section. */
        private void fill(ClientLevel level, long ticks, int x0, int y0, int z0, int x1, int y1, int z1, int nx, int nz) {
            for (int sy = y0 >> 4; sy <= y1 >> 4; sy++) {
                for (int sz = z0 >> 4; sz <= z1 >> 4; sz++) {
                    for (int sx = x0 >> 4; sx <= x1 >> 4; sx++) {
                        long node = SectionPos.asLong(sx, sy, sz);
                        byte[] wd = worldDamp(level, sx, sy, sz, ticks);
                        DataLayer layer = LightHold.live(node);
                        DataLayer above = layer == null ? LightHold.above(node) : null;
                        int ax0 = Math.max(x0, sx << 4), ax1 = Math.min(x1, sx << 4 | 15);
                        for (int y = Math.max(y0, sy << 4); y <= Math.min(y1, sy << 4 | 15); y++) {
                            for (int z = Math.max(z0, sz << 4); z <= Math.min(z1, sz << 4 | 15); z++) {
                                int row = ((y - y0) * nz + (z - z0)) * nx + (ax0 - x0), len = ax1 - ax0 + 1;
                                int ly = y & 15, lz = z & 15, lx = ax0 & 15;
                                if (wd == null) Arrays.fill(damp, row, row + len, (byte) 0);
                                else System.arraycopy(wd, ly << 8 | lz << 4 | lx, damp, row, len);
                                if (layer == null) {
                                    for (int i = 0; i < len; i++) eng[row + i] = (byte) (above == null ? 15 : above.get(lx + i, 0, lz));
                                } else if (layer.isDefinitelyHomogenous()) {
                                    Arrays.fill(eng, row, row + len, (byte) layer.get(0, 0, 0));
                                } else {
                                    for (int i = 0; i < len; i++) eng[row + i] = (byte) layer.get(lx + i, ly, lz);
                                }
                            }
                        }
                    }
                }
            }
        }

        /** Cells every live fall's blocks stood in are the world's now, not what the cache saw while they stood. */
        private void mask(ClientLevel level, int x0, int y0, int z0, int nx, int ny, int nz) {
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
            for (Zone z : ZONES) {
                for (int i = 0; i < z.ox.length; i++) {
                    int x = z.ox[i] - x0, y = z.oy[i] - y0, zz = z.oz[i] - z0;
                    if (x < 0 || y < 0 || zz < 0 || x >= nx || y >= ny || zz >= nz) continue;
                    BlockState s = level.getBlockState(mp.set(z.ox[i], z.oy[i], z.oz[i]));
                    damp[(y * nz + zz) * nx + x] = (byte) (s == z.os[i] ? 0 : s.getLightDampening());
                }
            }
        }

        /** The box's rim keeps the engine's values and is marked 16 in {@link #damp} so nothing spreads into it. */
        private void shell(int nx, int ny, int nz) {
            for (int y = 0; y < ny; y++) {
                for (int z = 0; z < nz; z++) {
                    boolean whole = y == 0 || y == ny - 1 || z == 0 || z == nz - 1;
                    int row = (y * nz + z) * nx;
                    for (int x = 0; x < nx; x += whole || x == nx - 1 ? 1 : nx - 1) {
                        int idx = row + x;
                        damp[idx] = 16;
                        lvl[idx] = eng[idx];
                        if (eng[idx] >= 2) push(eng[idx], idx);
                    }
                }
            }
        }

        private static void spread(int nx, int ny, int nz, int nxz) {
            for (int l = 15; l >= 2; l--) {
                int[] q = QUEUE[l];
                for (int qi = 0; qi < QN[l]; qi++) {
                    int c = q[qi];
                    if (lvl[c] != l) continue;
                    if (damp[c] == 16) {
                        int x = c % nx, z = c / nx % nz, y = c / nxz;
                        if (x > 0) relax(c - 1, l);
                        if (x < nx - 1) relax(c + 1, l);
                        if (z > 0) relax(c - nx, l);
                        if (z < nz - 1) relax(c + nx, l);
                        if (y > 0) relax(c - nxz, l);
                        if (y < ny - 1) relax(c + nxz, l);
                    } else {
                        relax(c - 1, l);
                        relax(c + 1, l);
                        relax(c - nx, l);
                        relax(c + nx, l);
                        relax(c - nxz, l);
                        relax(c + nxz, l);
                    }
                }
                QN[l] = 0;
            }
        }

        private static void relax(int n, int from) {
            int d = damp[n];
            int nl = from - (d < 1 ? 1 : d);
            if (nl > lvl[n]) {
                lvl[n] = (byte) nl;
                if (nl >= 2) push(nl, n);
            }
        }

        private void fit(int rx0, int ry0, int rz0, int nx, int ny, int nz, int size) {
            int dumped = 0;
            int dk = 0, br = 0, mdk = 0, mbr = 0;
            for (int y = 1; y < ny - 1; y++) {
                for (int z = 1; z < nz - 1; z++) {
                    for (int x = 1; x < nx - 1; x++) {
                        int idx = (y * nz + z) * nx + x;
                        if (damp[idx] >= 15) continue;
                        int d = lvl[idx] - eng[idx];
                        if (Math.abs(d) >= 4 && dumped++ < 12) TimberClient.LOG.info("[fit] cell {} {} {} (rel {} {} {}) field {} engine {} damp {}", rx0 + x, ry0 + y, rz0 + z, x, y, z, lvl[idx], eng[idx], damp[idx]);
                        if (d < 0) {
                            dk++;
                            mdk = Math.max(mdk, -d);
                        } else if (d > 0) {
                            br++;
                            mbr = Math.max(mbr, d);
                        }
                    }
                }
            }
            darker = dk;
            brighter = br;
            maxDarker = mdk;
            maxBrighter = mbr;
            stats = false;
        }

        /** Writes the computed levels as per-section limits (15: free); true when they differ from the last ones. */
        private boolean store(int x0, int y0, int z0, int x1, int y1, int z1, int nx, int nz) {
            Long2ObjectOpenHashMap<byte[]> next = new Long2ObjectOpenHashMap<>();
            for (int sy = y0 >> 4; sy <= y1 >> 4; sy++) {
                for (int sz = z0 >> 4; sz <= z1 >> 4; sz++) {
                    for (int sx = x0 >> 4; sx <= x1 >> 4; sx++) {
                        byte[] lim = null;
                        for (int y = Math.max(y0, sy << 4); y <= Math.min(y1, sy << 4 | 15); y++) {
                            for (int z = Math.max(z0, sz << 4); z <= Math.min(z1, sz << 4 | 15); z++) {
                                int row = ((y - y0) * nz + (z - z0)) * nx - x0;
                                for (int x = Math.max(x0, sx << 4); x <= Math.min(x1, sx << 4 | 15); x++) {
                                    int idx = row + x;
                                    if (lvl[idx] >= 15 || damp[idx] >= 15) continue;
                                    if (lim == null) {
                                        lim = new byte[4096];
                                        Arrays.fill(lim, (byte) 15);
                                    }
                                    lim[(y & 15) << 8 | (z & 15) << 4 | (x & 15)] = lvl[idx];
                                }
                            }
                        }
                        if (lim != null) next.put(SectionPos.asLong(sx, sy, sz), lim);
                    }
                }
            }
            boolean differs = next.size() != limits.size();
            if (!differs) {
                for (var e : next.long2ObjectEntrySet()) {
                    byte[] old = limits.get(e.getLongKey());
                    if (old == null || !Arrays.equals(old, e.getValue())) {
                        differs = true;
                        break;
                    }
                }
            }
            limits = next;
            return differs;
        }
    }
}
