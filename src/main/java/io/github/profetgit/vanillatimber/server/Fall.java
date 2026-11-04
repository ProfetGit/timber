package io.github.profetgit.vanillatimber.server;

import com.mojang.math.Transformation;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One fall in progress on the server, stepped once a tick at the start of the level tick: frame 0 is the tick after the
 * chop. It poses the displays vanilla viewers see, removes the tree's blocks, and fires the timeline's sounds,
 * particles and drops, all on the same frame so everything a viewer gets for frame n leaves in one tick.
 */
public final class Fall {
    /** 1.3.6 {@code #budget}: displays per fall (logs first). Over it, the rest of the crown only bursts at the slam. */
    static final int DISPLAY_CAP = 600;
//? if >=1.21.2 {
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
    private Pending.Entry owed;
    private int held = -1;
    private final Set<UUID> sentTo = new HashSet<>();
    private TreeFall payload;
    public final int id;
    public int n = -1, rolled, dropped, payloads;
    /** Item id -> count rolled at the chop (tests). */
    public final java.util.Map<String, Integer> loot = new java.util.TreeMap<>();
    /** Server time of the chop and of each frame, with {@link Falls#PERF} only. */
    long chopNs;
    final long[] stepNs;
    /** Called after every frame (self-test probes), or null. */
    public static volatile java.util.function.Consumer<Fall> probe;

    @SuppressWarnings("unchecked")
    Fall(int id, ServerLevel level, Tree tree, ServerPlayer cutter, boolean drops) {
        this.id = id;
        this.level = level;
        this.tree = tree;
        this.cutter = cutter;
        this.tl = new Timeline(tree.spec(), tree.logRings());
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
        int lo = 0, hi = 0;
        ItemStack tool = cutter.getMainHandItem();
        for (Tree.Part part : tree.parts()) {
            lo = Math.min(lo, part.k());
            hi = Math.max(hi, part.k());
            if (!drops) continue;
            List<ItemStack> loot = Block.getDrops(part.state(), level, part.pos(), level.getBlockEntity(part.pos()), cutter, tool);
            for (ItemStack st : loot) {
                rolled += st.getCount();
                this.loot.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(), Integer::sum);
            }
            if (part.leaf()) leafDrops.addAll(loot);
            else ringDrops[part.k()].addAll(loot);
        }
        kn = lo;
        kt = hi;
        oy = new double[kt - kn + 1];
        oz = new double[kt - kn + 1];
        sent[0] = Double.NaN;
    }

    /** Frame 1, the blocks are gone: the loot not yet dropped is written down, so a crash can give it back (PRD 8). */
    private void owe() {
        List<ItemStack> left = new ArrayList<>();
        for (List<ItemStack> ring : ringDrops) left.addAll(ring);
        left.addAll(leafDrops);
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
            owe();
        }
        send();
        if (!spawned && vanillaViewer()) spawn();
        pops();
        if (n == tl.leavesGone) {
            for (int i = 0; i < shown.length; i++) if (shown[i] != null && tree.parts().get(i).leaf()) discard(i);
        }
        Vec3 at = new Vec3(x, y0 + tl.dy[n], z);
        double pitch = tl.p[n] / 100.0;
        for (Timeline.Event e : tl.eventsAt(n)) fire(e, at, pitch);
        move(at, pitch);
        pose(pitch);
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
        Vec3 at = new Vec3(x, y0, z);
        Fx.instant(level, at);
        Vec3 p = at.add(0, 0.5, 0);
        for (List<ItemStack> ring : ringDrops) {
            for (ItemStack s : ring) drop(s, p, false);
            ring.clear();
        }
        for (ItemStack s : leafDrops) drop(s, p, false);
        leafDrops.clear();
        n = tl.end;
    }

    /** Ends the fall now (server stop, chunk unload): every display goes and every drop pops at the stump. */
    void abort() {
        if (n < 1) for (Tree.Part p : tree.parts()) remove(p.pos(), p.state());
        finish(new Vec3(x, y0, z));
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
            if (payload == null || payload.e() != n) payload = TreeFall.of(id, tree, n);
            payload.send(p);
            payloads++;
        }
    }

    /** Displays for every block (logs first, up to the cap), at rest on frame 0, at the current pose after that. */
    private void spawn() {
        spawned = true;
        cells = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(tree.parts().size());
        for (Tree.Part p : tree.parts()) cells.add(p.pos().asLong());
        int bin = tree.bin(), ox = Layout.ox(bin), oz = Layout.oz(bin), budget = DISPLAY_CAP;
        float rot = (float) Rig.step(tl.p[n] / 100.0);
        Quaternionf id = new Quaternionf();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < shown.length && budget > 0; i++) {
                Tree.Part p = tree.parts().get(i);
                if (p.leaf() == (pass == 0) || p.state().isAir() || p.hidden()) continue;
                if (p.leaf() ? n >= tl.leavesGone && tl.leavesGone >= 0 : tl.popTick[p.k()] >= 0 && n >= tl.popTick[p.k()]) continue;
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
                if (ringShown(e.ring()) && popping[e.ring()]) Fx.ringPop(level, this, p, e.pitch());
                for (ItemStack s : ringDrops[e.ring()]) drop(s, p, true);
                ringDrops[e.ring()].clear();
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
        if (owed != null) {
            owed.close();
            owed = null;
        }
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

    public float yaw() {
        return yaw;
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
