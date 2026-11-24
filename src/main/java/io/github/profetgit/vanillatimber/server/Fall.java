package io.github.profetgit.vanillatimber.server;

import com.mojang.math.Transformation;
import io.github.profetgit.vanillatimber.core.Landing;
import io.github.profetgit.vanillatimber.core.Layout;
import io.github.profetgit.vanillatimber.core.Rig;
import io.github.profetgit.vanillatimber.core.Timeline;
import io.github.profetgit.vanillatimber.net.TreeFall;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One fall in progress on the server, stepped once a tick at the start of the level tick: frame 0 is the tick after the
 * chop. It poses the displays vanilla viewers see, removes the tree's blocks, and fires the timeline's sounds,
 * particles and drops, all on the same frame so everything a viewer gets for frame n leaves in one tick.
 */
public final class Fall {
//? if >=1.21.5 {
    static final int STRICT = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
        | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SKIP_ON_PLACE;
//?} else {
/*    static final int STRICT = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
*///?}
    static final int[][] POP = {{1, -125, 150, 1250, 700, 3}, {4, 100, -225, 800, 1450, 1}, {5, 500, 500, 0, 0, 2}};

    final ServerLevel level;
    public final Tree tree;
    public final Timeline tl;
    final ServerPlayer cutter;
    final double x, y0, z, lz, cy, cr;
    final float yaw;
    final ParticleOptions leafParticle;
    final ChunkPos chunk;
    private final Quaternionf right;
    private final List<ItemStack>[] ringDrops;
    private final List<ItemStack> leafDrops = new ArrayList<>();
    /** Rings that turn into blocks ({@code land_blocks}), per part the ring it lands with (-1: it drops or vanishes), and the loot a landing part holds back. */
    private final boolean[] landed;
    private final int[] landRing;
    private final List<List<ItemStack>> partLoot = new ArrayList<>();
    private final boolean[] resolved;
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet takenCells = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private final Display.BlockDisplay[] shown;
    /** Each display's light, eased like the mesh's path light, and the packed level last sent (-1: none). */
    private final float[] lb, ls;
    private final int[] lit;
    /** The tree's own cells: their light is stale for a few ticks after frame 1 (the light engine catches up late). */
    private it.unimi.dsi.fastutil.longs.LongOpenHashSet cells;
    private final boolean[] popping;
    private final double[] popRes;
    private final int kn, kt;
    private final double[] oy, oz, corner = new double[3];
    private final double[] sent = new double[7];
    private double sentRes, lastY = Double.NaN;
    private float lastRot = Float.NaN;
    private boolean spawned;
    private final Knock knock;
    private Pending.Entry owed;
    private int held = -1;
    private final Set<UUID> sentTo = new HashSet<>();
    private TreeFall payload;
    public final int id;
    /** The poof style (config {@code style} 1 when the tree was chopped). */
    public final boolean poof;
    public int n = -1, rolled, dropped, payloads;
    /** Items of landed parts (they became blocks, so they never drop), and the blocks placed (tests); {@code rolled == dropped + placed}. */
    public int placed, landedLogs, landedLeaves, landFailed;
    /** Item id -> count rolled at the chop (tests). */
    public final java.util.Map<String, Integer> loot = new java.util.TreeMap<>();
    /** Server time of the chop and of each frame, with {@link Falls#PERF} only. */
    long chopNs;
    final long[] stepNs;
    /** Called after every frame (self-test probes), or null. */
    public static volatile java.util.function.Consumer<Fall> probe;

    @SuppressWarnings("unchecked")
    Fall(int id, ServerLevel level, Tree tree, ServerPlayer cutter, ItemStack tool, boolean drops, boolean[] land) {
        this.id = id;
        this.level = level;
        this.tree = tree;
        this.cutter = cutter;
        this.poof = Config.get(Config.Key.STYLE) == 1;
        this.tl = new Timeline(tree.spec(), tree.logRings(), land, poof);
        landed = tl.landed;
        stepNs = Falls.timed() ? new long[tl.end + 1] : null;
        x = tree.px() / 1000.0;
        y0 = tree.py() / 1000.0;
        z = tree.pz() / 1000.0;
        yaw = (float) tree.yaw();
        lz = -tree.hw() / 1000.0;
        cy = (tree.spec().rs() + tree.spec().h()) * 500 / 1000.0;
        cr = tree.cw() * 9 / 20 / 1000.0;
        leafParticle = Fx.leafParticle(tree.leaf());
        chunk = ChunkPos.containing(BlockPos.containing(x, y0, z));
        right = new Quaternionf(0, Layout.qs(tree.bin()), 0, Layout.qc(tree.bin()));
        int km = tree.spec().km();
        ringDrops = new List[km + 1];
        for (int k = 0; k <= km; k++) ringDrops[k] = new ArrayList<>();
        popping = new boolean[km + 1];
        popRes = new double[km + 1];
        shown = new Display.BlockDisplay[tree.parts().size()];
        lb = new float[shown.length];
        ls = new float[shown.length];
        lit = new int[shown.length];
        landRing = landRings(tree, landed);
        // only full blocks land beside the trunk (leaves, wart blocks, mushroom caps): vines, cocoa and moss keep the
        // faces they had standing, which point the wrong way once the tree lies down (user 2026-11-18, jungle vines)
        for (int i = 0; i < landRing.length; i++) {
            Tree.Part p = tree.parts().get(i);
            if (landRing[i] >= 0 && p.leaf() && !(p.state().getBlock() instanceof LeavesBlock) && !p.state().isCollisionShapeFullBlock(level, p.pos())) landRing[i] = -1;
        }
        resolved = new boolean[landRing.length];
        int lo = 0, hi = 0, idx = -1;
        for (Tree.Part part : tree.parts()) {
            idx++;
            partLoot.add(null);
            lo = Math.min(lo, part.k());
            hi = Math.max(hi, part.k());
            if (!drops) continue;
            List<ItemStack> loot = Block.getDrops(part.state(), level, part.pos(), level.getBlockEntity(part.pos()), cutter, tool);
            for (ItemStack st : loot) {
                rolled += st.getCount();
                this.loot.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(), Integer::sum);
            }
            if (landRing[idx] >= 0) partLoot.set(idx, loot);
            else if (part.leaf()) leafDrops.addAll(loot);
            else ringDrops[part.k()].addAll(loot);
        }
        kn = lo;
        kt = hi;
        oy = new double[kt - kn + 1];
        oz = new double[kt - kn + 1];
        sent[0] = Double.NaN;
        knock = new Knock(this);
    }

    /** Per part the ring it lands with: a log of a landed ring its own, a leaf the ring of the nearest landed log within {@link Landing#LEAF_REACH}. */
    private static int[] landRings(Tree tree, boolean[] landed) {
        List<Tree.Part> parts = tree.parts();
        int[] out = new int[parts.size()];
        java.util.Arrays.fill(out, -1);
        List<Integer> logs = new ArrayList<>(), leaves = new ArrayList<>();
        for (int i = 0; i < out.length; i++) {
            Tree.Part p = parts.get(i);
            if (p.state().isAir()) continue;
            if (p.leaf()) leaves.add(i);
            else if (p.k() < landed.length && landed[p.k()]) {
                out[i] = p.k();
                logs.add(i);
            }
        }
        if (logs.isEmpty() || leaves.isEmpty()) return out;
        int[] gx = new int[logs.size()], gy = new int[gx.length], gz = new int[gx.length], gk = new int[gx.length];
        for (int j = 0; j < gx.length; j++) {
            Tree.Part p = parts.get(logs.get(j));
            gx[j] = p.tx();
            gy[j] = p.wy();
            gz[j] = p.tz();
            gk[j] = p.k();
        }
        int[] lx = new int[leaves.size()], ly = new int[lx.length], lz = new int[lx.length];
        for (int j = 0; j < lx.length; j++) {
            Tree.Part p = parts.get(leaves.get(j));
            lx[j] = p.tx();
            ly[j] = p.wy();
            lz[j] = p.tz();
        }
        int[] r = Landing.leafRings(lx, ly, lz, gx, gy, gz, gk);
        for (int j = 0; j < r.length; j++) out[leaves.get(j)] = r[j];
        return out;
    }

    /** Frame 1, the blocks are gone: the loot not yet dropped is written down, so a crash can give it back (PRD 8). */
    private void owe() {
        List<ItemStack> left = new ArrayList<>();
        for (List<ItemStack> ring : ringDrops) left.addAll(ring);
        left.addAll(leafDrops);
        for (List<ItemStack> l : partLoot) if (l != null) left.addAll(l);
        owed = Pending.open(level, x, y0, z, left);
    }

    /** Chunk ticket around the stump for the fall's lifetime, released by {@link #finish}. */
    void hold() {
        held = Hold.radius(tree.spec().h());
        Hold.add(level, chunk, held);
    }

    private void unhold() {
        if (held < 0) return;
        Hold.release(level, chunk, held);
        held = -1;
    }

    /** A crash as the next autosave left it (tests): displays and ticket go, the pending entry stays as saved. */
    void forget() {
        for (int i = 0; i < shown.length; i++) if (shown[i] != null) discard(i);
        unhold();
    }

    /** The world cells this fall still owns (a chop there does nothing). */
    boolean holds(BlockPos pos) {
        if (n >= 1) return false;
        for (Tree.Part p : tree.parts()) if (p.pos().equals(pos)) return true;
        return false;
    }

    /** One frame; true when the fall is over. */
    boolean step() {
        n++;
        if (n == 0) {
            if (tree.anchor() != null) remove(tree.anchor(), null);
            for (Tree.Part p : tree.parts()) if (p.hidden()) remove(p.pos(), p.state());
        } else if (n == 1) {
            for (Tree.Part p : tree.parts()) if (!p.pos().equals(tree.anchor())) remove(p.pos(), p.state());
            settle();
            owe();
        }
        send();
        if (!spawned && vanillaViewer()) spawn();
        pops();
        if (n == tl.leavesGone) {
            for (int i = 0; i < shown.length; i++) if (shown[i] != null && tree.parts().get(i).leaf() && landRing[i] < 0) discard(i);
        }
        Vec3 at = new Vec3(x, y0 + tl.dy[n], z);
        double pitch = tl.p[n] / 100.0;
        for (Timeline.Event e : tl.eventsAt(n)) fire(e, at, pitch);
        move(at, pitch);
        pose(pitch);
        knock.step(n);
        boolean done = n >= tl.end;
        if (done) finish(at);
        var pr = probe;
        if (pr != null) pr.accept(this);
        return done;
    }

    /**
     * {@code animation} 0 ({@code disp/instant}): every block goes at once with a puff and a creak of wood, and all the loot
     * drops at the stump (or at the player, with {@code drops} 1).
     */
    void instantly() {
        for (Tree.Part p : tree.parts()) remove(p.pos(), p.state());
        settle();
        Vec3 at = new Vec3(x, y0, z);
        Fx.instant(level, at);
        Vec3 p = at.add(0, 0.5, 0);
        for (List<ItemStack> ring : ringDrops) {
            for (ItemStack s : ring) drop(s, p, false);
            ring.clear();
        }
        for (ItemStack s : leafDrops) drop(s, p, false);
        leafDrops.clear();
        Replant.plant(this);
        n = tl.end;
    }

    /** Ends the fall now (server stop, chunk unload): every display goes and every drop pops at the stump. */
    void abort() {
        if (n < 1) {
            for (Tree.Part p : tree.parts()) remove(p.pos(), p.state());
            settle();
        }
        finish(new Vec3(x, y0, z));
    }

    /**
     * Pop, never float: a block that stood on the tree and whose support check could not be run without it ({@link Support})
     * pops now if it cannot stay, as vanilla would have done with a neighbour update.
     */
    private void settle() {
        for (BlockPos q : tree.watch()) {
            BlockState s = level.getBlockState(q);
            if (!s.isAir() && !s.canSurvive(level, q)) level.destroyBlock(q, true);
        }
    }

    /** Clears a tree cell unless something else stands there now; same block is enough (a leaf's distance or a vine's faces change when the anchor goes). */
    private void remove(BlockPos pos, BlockState expect) {
        BlockState s = level.getBlockState(pos);
        if (expect != null && s.getBlock() != expect.getBlock()) return;
        boolean wet = s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProperties.WATERLOGGED);
        level.setBlock(pos, wet ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), STRICT);
    }

    private boolean vanillaViewer() {
        for (ServerPlayer p : level.getChunkSource().chunkMap.getPlayers(chunk, false)) if (!Viewers.hidden(p)) return true;
        return false;
    }

    /**
     * The payload to every modded player with the tree's chunk loaded, once: at frame 0 it leaves before this tick's block
     * and entity packets; a player who comes into range later gets it with the frames already played.
     */
    private void send() {
        for (ServerPlayer p : level.getChunkSource().chunkMap.getPlayers(chunk, false)) {
            if (!Viewers.local(p) || sentTo.contains(p.getUUID()) || !TreeFall.canSend(p)) continue;
            sentTo.add(p.getUUID());
            if (payload == null || payload.e() != n) payload = TreeFall.of(id, tree, n, landed, landRing, poof);
            payload.send(p);
            payloads++;
        }
    }

    /** Displays for every block (logs first, up to the cap), at rest on frame 0, at the current pose after that. */
    private void spawn() {
        spawned = true;
        cells = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(tree.parts().size());
        for (Tree.Part p : tree.parts()) cells.add(p.pos().asLong());
        int bin = tree.bin(), ox = Layout.ox(bin), oz = Layout.oz(bin), budget = Config.get(Config.Key.DISPLAY_CAP);
        float rot = (float) Rig.step(tl.p[n] / 100.0);
        Quaternionf id = new Quaternionf();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < shown.length && budget > 0; i++) {
                Tree.Part p = tree.parts().get(i);
                if (p.leaf() == (pass == 0) || p.state().isAir() || p.hidden()) continue;
                int at = landRing[i];
                if (at >= 0 ? n > tl.landTick() : p.leaf() ? n >= tl.leavesGone && tl.leavesGone >= 0
                    : tl.popTick[p.k()] >= 0 && n >= tl.popTick[p.k()]) continue;
                budget--;
                Transformation rest = Displays.of((p.tx() + ox) / 1000.0, (p.wy() - 3) / 1000.0, (p.tz() + oz) / 1000.0, id, 1.006, 1.006,
                    1.006, right);
                shown[i] = Displays.spawn(level, p.state(), x, y0 + tl.dy[n], z, yaw, rot, rest);
                lit[i] = -1;
                light(i, p.pos(), 1);
            }
        }
        lastY = y0 + tl.dy[n];
        lastRot = rot;
        if (n > 0) sent[0] = Double.NaN;
    }

    private void discard(int i) {
        shown[i].discard();
        shown[i] = null;
    }

    /** Ripple pops ({@code pop_tick}, {@code pop_key}): squash 3 ticks, stretch 1, vanish 2, gone after 6. */
    private void pops() {
        int bin = tree.bin(), pc = Layout.cos(bin) + Layout.sin(bin), mc = Layout.cos(bin) - Layout.sin(bin);
        for (int k = 0; k < popping.length; k++) {
            int nk = tl.popTick[k];
            if (landed[k]) {
                if (n == tl.landTick()) land(k);
                // the displays go a tick after the blocks, once vanilla clients have drawn them (no blank frame)
                if (n == tl.landTick() + (int) Timeline.HOLD) for (int i = 0; i < shown.length; i++) if (shown[i] != null && landRing[i] == k) discard(i);
                continue;
            }
            if (nk < 0 || n < nk) continue;
            int u = n - nk + 1;
            if (u == 1) {
                popping[k] = true;
                popRes[k] = sentRes;
            }
            if (u == 7) {
                for (int i = 0; i < shown.length; i++) {
                    if (shown[i] != null && !tree.parts().get(i).leaf() && tree.parts().get(i).k() == k) discard(i);
                }
                continue;
            }
            for (int[] key : POP) {
                if (key[0] != u) continue;
                double c = Math.cos(Math.toRadians(sentRes)), s = Math.sin(Math.toRadians(sentRes));
                int dx = Math.floorDiv(key[1] * pc, 10000), dz = Math.floorDiv(key[1] * mc, 10000);
                Quaternionf left = new Quaternionf().rotationX((float) Math.toRadians(popRes[k]));
                for (int i = 0; i < shown.length; i++) {
                    Tree.Part p = tree.parts().get(i);
                    if (shown[i] == null || p.leaf() || p.k() != k) continue;
                    double qx = p.tx() + dx, qy = p.wy() + key[2], qz = p.tz() + dz;
                    Displays.pose(shown[i], Displays.of(qx / 1000, (qy * c - qz * s) / 1000, (qy * s + qz * c) / 1000, left,
                        key[3] / 1000.0, key[4] / 1000.0, key[3] / 1000.0, right), key[5]);
                }
            }
        }
    }

    /**
     * Ring {@code k} lands (its pop tick): its displays go and its logs, and the leaves landing with it, become blocks in
     * the cells their rest-pose centres are over, in the pose the displays showed (frame n - 1), all in this tick's packets.
     * A part whose cell is taken, claimed or out of the world drops its items at the ring's RING tick as a popped one does.
     */
    private void land(int k) {
        Pose pose = new Pose(Math.max(0, n - 1));
        // the first ring to land reserves a cell for every landed log of the tree (own cell, else the nearest free
        // neighbour), so leaves landing with an earlier ring never take the cell a later ring's log needs
        if (logCell == null) {
            logCell = new long[landRing.length];
            java.util.Arrays.fill(logCell, Long.MIN_VALUE);
            for (int i = 0; i < landRing.length; i++) {
                Tree.Part p = tree.parts().get(i);
                if (landRing[i] < 0 || p.leaf()) continue;
                double[] e = pose.centre(p);
                BlockPos at = nearestFree(e[0], e[1], e[2], pose.axis);
                if (at == null) continue;
                logCell[i] = at.asLong();
                takenCells.add(at.asLong());
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < landRing.length; i++) {
                Tree.Part p = tree.parts().get(i);
                if (landRing[i] != k || resolved[i] || p.leaf() != (pass == 1)) continue;
                resolved[i] = true;
                BlockState st = p.state();
                if (!p.leaf() && pose.lying && st.hasProperty(BlockStateProperties.AXIS)) st = st.setValue(BlockStateProperties.AXIS, pose.axis);
                if (p.leaf() && st.hasProperty(LeavesBlock.PERSISTENT)) st = st.setValue(LeavesBlock.PERSISTENT, false);
                boolean ok;
                if (p.leaf()) {
                    // a leaf takes its own cell or none: moving leaves around stacked them into a mound (critic 2026-11-17)
                    double[] e = pose.centre(p);
                    BlockPos at = BlockPos.containing(e[0], e[1], e[2]);
                    ok = free(at) && put(at, st, true);
                } else ok = logCell[i] != Long.MIN_VALUE && put(BlockPos.of(logCell[i]), st, false);
                if (!ok) {
                    landFailed++;
                    List<ItemStack> loot = partLoot.get(i);
                    if (loot != null) ringDrops[k].addAll(loot);
                    continue;
                }
                if (p.leaf()) landedLeaves++;
                else landedLogs++;
                List<ItemStack> loot = partLoot.get(i);
                if (loot == null) continue;
                for (ItemStack st2 : loot) {
                    placed += st2.getCount();
                    if (owed != null) owed.drop(st2);
                }
            }
        }
    }

    /** Per part, the cell reserved for a landed log at the first landing (-: none), or null before it. */
    private long[] logCell;

    /** The pose the displays showed on frame {@code f}: where each part's centre is in the world. */
    private final class Pose {
        final double res, gy, top;
        final int f;
        final double[] loy = new double[kt - kn + 1], loz = new double[kt - kn + 1];
        final Quaternionf left, orient;
        final net.minecraft.core.Direction.Axis axis;
        final boolean lying;
        final Vector3f c = new Vector3f();

        Pose(int f) {
            this.f = f;
            double pitch = tl.p[f] / 100.0, rot = Rig.step(pitch);
            res = pitch - rot;
            gy = tl.gy[f] / 1000.0;
            Rig.origins(res, tl.bn[f] / 100.0, gy, tree.spec().h(), kn, kt, loy, loz);
            left = new Quaternionf().rotationX((float) Math.toRadians(res));
            orient = new Quaternionf().rotationYXZ((float) Math.toRadians(-yaw), (float) Math.toRadians(rot), 0);
            axis = Math.abs(Layout.sin(tree.bin())) > Math.abs(Layout.cos(tree.bin())) ? net.minecraft.core.Direction.Axis.X : net.minecraft.core.Direction.Axis.Z;
            lying = Math.abs(pitch) >= 45;
            top = y0 + tl.dy[f];
        }

        double[] centre(Tree.Part p) {
            double sx = (p.leaf() ? tl.lx[f] : tl.gx[f]) / 1000.0, sz = (p.leaf() ? tl.lz[f] : tl.gz[f]) / 1000.0;
            Rig.corner(p.tx() / 1000.0, p.wy() / 1000.0, p.tz() / 1000.0, p.k(), sx, gy, sz, res, loy[p.k() - kn], loz[p.k() - kn], corner);
            Transformation t = Displays.of(corner[0], corner[1], corner[2], left, sx, gy, sz, right);
            orient.transform(t.getMatrix().transformPosition(0.5f, 0.5f, 0.5f, c));
            return new double[] {x + c.x, top + c.y, z + c.z};
        }
    }

    /** Cells a landing part may move to when its own cell is taken: up to 2 blocks away, nearest first per part. */
    private static final int[][] NEAR;

    static {
        List<int[]> l = new ArrayList<>();
        for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > 0 && d2 <= 4) l.add(new int[] {dx, dy, dz});
        }
        NEAR = l.toArray(new int[0][]);
    }

    /**
     * The cell for a landing part whose rest-pose centre is at ({@code ex, ey, ez}): its own, else the free neighbour
     * nearest that centre (a diagonal fall snaps two parts to one cell), or null when all are taken.
     */
    private BlockPos nearestFree(double ex, double ey, double ez, net.minecraft.core.Direction.Axis along) {
        BlockPos at = BlockPos.containing(ex, ey, ez);
        if (free(at)) return at;
        int[][] order = NEAR.clone();
        double fx = ex - at.getX() - 0.5, fy = ey - at.getY() - 0.5, fz = ez - at.getZ() - 0.5;
        // moving across the fall comes last, so the lying trunk stays a straight row (user 2026-11-18)
        java.util.Arrays.sort(order, java.util.Comparator.comparingDouble(d -> sq(d[0] - fx) + sq(d[1] - fy) + sq(d[2] - fz)
            + ((along == net.minecraft.core.Direction.Axis.X ? d[2] : d[0]) != 0 ? 4.5 : 0)));
        for (int[] d : order) if (free(at.offset(d[0], d[1], d[2]))) return at.offset(d[0], d[1], d[2]);
        return null;
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Not reserved by this fall, in the world, loaded, replaceable and not claimed against the cutter. */
    private boolean free(BlockPos at) {
        return !takenCells.contains(at.asLong()) && open(at);
    }

    private boolean open(BlockPos at) {
        return !level.isOutsideBuildHeight(at.getY()) && level.hasChunkAt(at) && level.getBlockState(at).canBeReplaced() && level.mayInteract(cutter, at);
    }

    private boolean put(BlockPos at, BlockState st, boolean leaf) {
        if (!open(at)) return false;
        BlockState now = level.getBlockState(at);
        if (st.hasProperty(BlockStateProperties.WATERLOGGED)) st = st.setValue(BlockStateProperties.WATERLOGGED, now.getFluidState().is(Fluids.WATER));
        level.setBlock(at, st, Block.UPDATE_ALL);
        takenCells.add(at.asLong());
        if (leaf) {
            if (st.getBlock() instanceof LeavesBlock) level.scheduleTick(at, st.getBlock(), 1);
        } else Placed.addLanded(level, at);
        return true;
    }

    /** Whether the tree has log parts in ring {@code k}: with no displays (only modded viewers) there is nothing shown to ask. */
    private boolean ringExists(int k) {
        for (Tree.Part p : tree.parts()) if (!p.leaf() && p.k() == k) return true;
        return false;
    }

    private boolean ringShown(int k) {
        for (int i = 0; i < shown.length; i++) {
            Tree.Part p = tree.parts().get(i);
            if (shown[i] != null && !p.leaf() && p.k() == k) return true;
        }
        return false;
    }

    private void fire(Timeline.Event e, Vec3 at, double pitch) {
        switch (e.kind()) {
            case CHOP -> Fx.chop(level, this, at);
            case LAND -> Fx.land(level, this, at);
            case CREAK -> Fx.creak(level, at);
            case CRACK -> Fx.crack(level, this, at);
            case WHOOSH -> Fx.whoosh(level, at);
            case IMPACT -> {
                List<Vec3> points = Fx.impact(level, this, at, pitch);
                if (points.isEmpty()) points.add(at.add(0, 0.5, 0));
                for (ItemStack s : leafDrops) drop(s, points.get(level.getRandom().nextInt(points.size())), false);
                leafDrops.clear();
            }
            case THUD -> Fx.thud(level, at, e.volume(), e.pitch());
            case RING -> {
                Vec3 p = Fx.local(at, pitch, yaw, e.ring() + 0.5, lz);
                if ((spawned ? ringShown(e.ring()) : ringExists(e.ring())) && popping[e.ring()]) Fx.ringPop(level, this, p, e.pitch());
                for (ItemStack s : ringDrops[e.ring()]) drop(s, p, true);
                ringDrops[e.ring()].clear();
            }
            case INHALE -> Fx.inhale(level, at);
            case POOF -> {
                for (int i = 0; i < shown.length; i++) if (shown[i] != null) discard(i);
                Fx.poof(level, this, at, pitch);
                for (int k = 0; k < ringDrops.length; k++) {
                    if (ringDrops[k].isEmpty()) continue;
                    Vec3 p = Fx.local(at, pitch, yaw, k + 0.5, lz);
                    for (ItemStack s : ringDrops[k]) drop(s, p, true);
                    ringDrops[k].clear();
                }
            }
            case LEAVES_GONE, FINISH -> { }
        }
    }

    /** The entities carry the yaw and the pitch's byte step ({@code anim/move}); y moves on plunges and drops. */
    private void move(Vec3 at, double pitch) {
        float rot = (float) Rig.step(pitch);
        if (at.y == lastY && rot == lastRot) return;
        lastY = at.y;
        lastRot = rot;
        for (Display.BlockDisplay d : shown) if (d != null) d.snapTo(at.x, at.y, at.z, yaw, rot);
    }

    /**
     * Every live, non-popping display's transformation when the frame's pose changed ({@code anim/xf}): the residual
     * pitch, the bend as a chain of ring origins, each group's squash, reached over one tick.
     */
    private void pose(double pitch) {
        double gx = tl.gx[n], gy = tl.gy[n], gz = tl.gz[n], lx = tl.lx[n], lz = tl.lz[n], bend = tl.bn[n];
        if (sent[0] == pitch && sent[1] == bend && sent[2] == gx && sent[3] == gy && sent[4] == gz && sent[5] == lx && sent[6] == lz) return;
        boolean first = Double.isNaN(sent[0]);
        sent[0] = pitch;
        sent[1] = bend;
        sent[2] = gx;
        sent[3] = gy;
        sent[4] = gz;
        sent[5] = lx;
        sent[6] = lz;
        if (first && n == 0) return;
        double res = pitch - Rig.step(pitch);
        sentRes = res;
        Rig.origins(res, bend / 100.0, gy / 1000.0, tree.spec().h(), kn, kt, oy, oz);
        Quaternionf left = new Quaternionf().rotationX((float) Math.toRadians(res));
        // the renderer's orientation (DisplayRenderer.calculateOrientation, FIXED): each block's centre in the world for its light
        Quaternionf orient = new Quaternionf().rotationYXZ((float) Math.toRadians(-yaw), (float) Math.toRadians(lastRot), 0);
        Vector3f c = new Vector3f();
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        for (int i = 0; i < shown.length; i++) {
            Tree.Part p = tree.parts().get(i);
            if (shown[i] == null || !p.leaf() && popping[p.k()]) continue;
            double sx = (p.leaf() ? lx : gx) / 1000.0, sz = (p.leaf() ? lz : gz) / 1000.0, sy = gy / 1000.0;
            Rig.corner(p.tx() / 1000.0, p.wy() / 1000.0, p.tz() / 1000.0, p.k(), sx, sy, sz, res, oy[p.k() - kn], oz[p.k() - kn], corner);
            Transformation t = Displays.of(corner[0], corner[1], corner[2], left, sx, sy, sz, right);
            Displays.pose(shown[i], t, 1);
            orient.transform(t.getMatrix().transformPosition(0.5f, 0.5f, 0.5f, c));
            cell.set(Math.floor(x + c.x), Math.floor(lastY + c.y), Math.floor(z + c.z));
            if (!cells.contains(cell.asLong())) light(i, cell, 0.2f);
        }
    }

    /** Eases display i's light toward the cell's by {@code ease} (1: at once) and resends it when a level changes. */
    private void light(int i, BlockPos cell, float ease) {
        int l = Displays.lightAt(level, cell);
        lb[i] += (LightCoordsUtil.block(l) - lb[i]) * ease;
        ls[i] += (LightCoordsUtil.sky(l) - ls[i]) * ease;
        int b = Math.round(lb[i]), sk = Math.round(ls[i]), packed = LightCoordsUtil.pack(b, sk);
        if (packed == lit[i]) return;
        lit[i] = packed;
        Displays.light(shown[i], b, sk);
    }

    /** The last tick ({@code anim/finish}): every display goes, what is left drops at the pivot. */
    private void finish(Vec3 at) {
        for (int i = 0; i < shown.length; i++) if (shown[i] != null) discard(i);
        Vec3 p = at.add(0, 0.5, 0);
        for (List<ItemStack> ring : ringDrops) {
            for (ItemStack s : ring) drop(s, p, true);
            ring.clear();
        }
        for (ItemStack s : leafDrops) drop(s, p, true);
        leafDrops.clear();
        for (int i = 0; i < partLoot.size(); i++) {
            List<ItemStack> loot = partLoot.get(i);
            if (loot == null || resolved[i]) continue;
            resolved[i] = true;
            for (ItemStack s : loot) drop(s, p, true);
        }
        if (owed != null) {
            owed.close();
            owed = null;
        }
        Replant.plant(this);
        unhold();
    }

    /** An item: a small hop out of a popping ring, or a burst out of the crown at the slam ({@code anim/drop_motion}). */
    private void drop(ItemStack stack, Vec3 at, boolean hop) {
        if (stack.isEmpty()) return;
        dropped += stack.getCount();
        if (owed != null) owed.drop(stack);
        if (Config.get(Config.Key.DROPS) == 1 && !cutter.isRemoved() && cutter.level() == level) {
            ItemEntity item = new ItemEntity(level, cutter.getX(), cutter.getY(), cutter.getZ(), stack, 0, 0, 0);
            item.setPickUpDelay(0);
            level.addFreshEntity(item);
            return;
        }
        RandomSource r = level.getRandom();
        double dx = hop ? r.nextInt(121) - 60 : r.nextInt(281) - 140, dy = hop ? 200 + r.nextInt(61) : 180 + r.nextInt(141);
        double dz = hop ? r.nextInt(121) - 60 : r.nextInt(281) - 140;
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack, dx / 1000, dy / 1000, dz / 1000);
        item.setPickUpDelay(10);
        level.addFreshEntity(item);
    }

    /** The landed-ring mask (tests). */
    public boolean[] landed() {
        return landed;
    }

    /** Parts that land with their ring, by part index (tests). */
    public int[] landRing() {
        return landRing;
    }

    public float yaw() {
        return yaw;
    }

    /** Mean and worst server time of one knockback frame in ms, and how many frames ran, when timed (tests). */
    public double knockMs() {
        return knock.meanMs();
    }

    public double knockMaxMs() {
        return knock.maxMs();
    }

    public int knockFrames() {
        return knock.frames();
    }

    /** The timeline's true pitch at the current frame (tests). */
    public double pitch() {
        return tl.p[Math.max(n, 0)] / 100.0;
    }

    public double pivotY() {
        return y0 + tl.dy[Math.max(n, 0)];
    }

    public Vec3 pivot() {
        return new Vec3(x, pivotY(), z);
    }

    /** Server time of the chop (scan, loot, set-up), when timed (tests, perf log). */
    public double chopMs() {
        return chopNs / 1e6;
    }

    /** Server time of frame i, when timed; frame 0 spawns the displays for vanilla viewers. */
    public double frameMs(int i) {
        return stepNs == null || i < 0 || i >= stepNs.length ? Double.NaN : stepNs[i] / 1e6;
    }

    /** A live display and the block it stands for (tests). */
    public record Live(Display.BlockDisplay display, Tree.Part part) {}

    public List<Live> live() {
        List<Live> out = new ArrayList<>();
        for (int i = 0; i < shown.length; i++) if (shown[i] != null && !shown[i].isRemoved()) out.add(new Live(shown[i], tree.parts().get(i)));
        return out;
    }

    /** Whether vanilla viewers got displays. */
    public boolean spawned() {
        return spawned;
    }

    /** Tree blocks still standing (tests). */
    public int standing() {
        int c = 0;
        for (Tree.Part p : tree.parts()) if (level.getBlockState(p.pos()) == p.state()) c++;
        return c;
    }

    /** Displays still in the world (tests). */
    public int displays() {
        int c = 0;
        for (Entity d : shown) if (d != null && !d.isRemoved()) c++;
        return c;
    }
}
