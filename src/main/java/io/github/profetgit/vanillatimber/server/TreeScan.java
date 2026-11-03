package io.github.profetgit.vanillatimber.server;

import io.github.profetgit.vanillatimber.core.FallSpec;
import io.github.profetgit.vanillatimber.core.Layout;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.commands.arguments.coordinates.LocalCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * The chop's scan on the live level, ported from the 1.3.6 job ({@code job/p2..p8}, {@code flood/*}, {@code own/*},
 * {@code leaves/*}, {@code fall/*}). Unlike the pack it never removes blocks to look: the tree's own cells count as
 * open wherever 1.3.6 had already taken them out.
 *
 * <p>Ownership between touching trees, placed logs, the build rules, decor and the creaking heart as 1.3.6; a tree over
 * {@code max_logs} is left alone (1.3.6 felled its first logs).
 */
public final class TreeScan {
    public static final TagKey<Block> LOGS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("timber", "logs"));
    public static final TagKey<Block> PASSABLE = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("timber", "passable"));
    public static final TagKey<Block> GAP_OPEN = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("timber", "gap_open"));
    /** Set by the last {@link #scan}: it gave up because the tree has more logs than {@code max_logs}. */
    public static volatile boolean tooBig;
    public static final TagKey<Block> NOT_GROUND = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("timber", "not_ground"));
    public static final TagKey<Block> DECOR = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("timber", "decor"));
    public static final TagKey<Block> HANGING = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("timber", "hanging"));
    static final int[] TRIES = {3375, -3375, 0, 6750, -6750, 9000, -9000};
    /** 1.3.6's face order (+x, -x, +y, -y, +z, -z), which decides what the walk takes first at the cap. */
    private static final Direction[] FACES = {Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN, Direction.SOUTH, Direction.NORTH};
    /** Offsets within 1.5 blocks (faces and edges): ground logs this close are one seed ({@code own/seed_grow}). */
    private static final int[][] NEAR;

    static {
        List<int[]> near = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            int d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > 0 && d2 <= 2) near.add(new int[] {dx, dy, dz});
        }
        NEAR = near.toArray(new int[0][]);
    }

    private TreeScan() {}

    static boolean natural(BlockState s) {
        return s.is(BlockTags.LEAVES) && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT);
    }

    /** The tree the chop at {@code origin} (already broken; it was {@code chopped}) would fell, or null. */
    public static Tree scan(ServerLevel level, BlockPos origin, BlockState chopped, float playerYaw) {
        Block log = chopped.getBlock();
        tooBig = false;
        int radius = Config.radius(), maxLogs = Config.maxLogs(), maxBlocks = Config.maxBlocks();
        boolean hideLeaves = Config.on(Config.Key.HIDE_LEAVES);
        Placed placed = Config.on(Config.Key.PROTECT) ? Placed.get(level) : null;
        // flood (1.3.6 flood/*): 26 neighbours of the same log within the radius across, plus creaking hearts on a
        // face; node 0 is the chopped cell. Placed logs are walked through but never owned.
        List<BlockPos> nodes = new ArrayList<>();
        Long2IntOpenHashMap index = new Long2IntOpenHashMap();
        index.defaultReturnValue(-1);
        LongOpenHashSet seen = new LongOpenHashSet();
        BitSet ground = new BitSet(), mine = new BitSet(), heart = new BitSet();
        nodes.add(origin);
        index.put(origin.asLong(), 0);
        seen.add(origin.asLong());
        if (!level.getBlockState(origin.below()).is(NOT_GROUND)) ground.set(0);
        int floodLogs = 0;
        for (int i = 0; i < nodes.size(); i++) {
            BlockPos p = nodes.get(i);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                BlockPos q = p.offset(dx, dy, dz);
                if (seen.contains(q.asLong())) continue;
                BlockState s = level.getBlockState(q);
                boolean face = Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1;
                if (s.is(Blocks.CREAKING_HEART)) {
                    if (!face) continue;
                    seen.add(q.asLong());
                    heart.set(nodes.size());
                } else {
                    seen.add(q.asLong());
                    if (!s.is(log) || Math.abs(q.getX() - origin.getX()) > radius || Math.abs(q.getZ() - origin.getZ()) > radius) continue;
                    if (floodLogs >= maxLogs) {
                        tooBig = true;
                        return null;
                    }
                    floodLogs++;
                    if (!level.getBlockState(q.below()).is(NOT_GROUND)) ground.set(nodes.size());
                    if (placed != null && placed.holds(level, q)) mine.set(nodes.size());
                }
                index.put(q.asLong(), nodes.size());
                nodes.add(q);
            }
        }
        if (floodLogs == 0) return null;

        // ownership (own/*): ground logs within 1.5 of each other seed one label, then every label spreads one 26-step
        // layer at a time and the first to reach a log owns it; the tree is the chopped cell's label
        int n = nodes.size();
        int[] label = new int[n];
        IntArrayList next = new IntArrayList();
        IntArrayList stack = new IntArrayList();
        int labels = 0;
        for (int i = 0; i < n; i++) {
            if (!ground.get(i) || mine.get(i) || label[i] != 0) continue;
            label[i] = ++labels;
            next.add(i);
            stack.add(i);
            while (!stack.isEmpty()) {
                BlockPos p = nodes.get(stack.popInt());
                for (int[] o : NEAR) {
                    int k = index.get(BlockPos.asLong(p.getX() + o[0], p.getY() + o[1], p.getZ() + o[2]));
                    if (k < 0 || !ground.get(k) || mine.get(k) || label[k] != 0) continue;
                    label[k] = labels;
                    next.add(k);
                    stack.add(k);
                }
            }
        }
        spread(nodes, index, label, mine, next);
        int tree = label[0];
        if (tree == 0) {
            tree = label[0] = -1;
            spread(nodes, index, label, mine, IntArrayList.of(0));
        }
        List<BlockPos> ours = new ArrayList<>();
        BitSet oursHeart = new BitSet();
        int owned = 0;
        for (int i = 0; i < n; i++) {
            if (label[i] != tree) continue;
            if (heart.get(i)) oursHeart.set(ours.size());
            else if (i > 0) owned++;
            ours.add(nodes.get(i));
        }
        if (owned == 0) return null;
        // build rules (job/p2): leaves touching the owned logs, at least one per 8 logs, and natural leaves 1-2 above the top
        int leafCount = 0, top = Integer.MIN_VALUE;
        for (BlockPos p : ours) {
            for (Direction d : Direction.values()) if (natural(level.getBlockState(p.relative(d)))) leafCount++;
            top = Math.max(top, p.getY());
        }
        if (leafCount <= 0 || leafCount * 8 < owned) return null;
        boolean crown = false;
        for (BlockPos p : ours) {
            if (p.getY() >= top - 1 && (natural(level.getBlockState(p.above())) || natural(level.getBlockState(p.above(2))))) {
                crown = true;
                break;
            }
        }
        if (!crown) return null;
        int oy = origin.getY();
        List<BlockPos> logs = new ArrayList<>();
        List<BlockPos> kept = new ArrayList<>();
        for (int j = 0; j < ours.size(); j++) {
            BlockPos p = ours.get(j);
            if (p.getY() < oy) continue;
            kept.add(p);
            if (!oursHeart.get(j) && !p.equals(origin)) logs.add(p);
        }
        ours = kept;
        if (logs.isEmpty()) return null;

        // footprint: our cells at the cut level next to the chop; gap: open cells under it
        int fx0 = Integer.MAX_VALUE, fx1 = Integer.MIN_VALUE, fz0 = Integer.MAX_VALUE, fz1 = Integer.MIN_VALUE, by = oy;
        int gap = 99, low = 0;
        for (BlockPos p : ours) {
            if (p.getY() != oy) continue;
            low++;
            if (Math.abs(p.getX() - origin.getX()) > 1 || Math.abs(p.getZ() - origin.getZ()) > 1) continue;
            fx0 = Math.min(fx0, p.getX());
            fx1 = Math.max(fx1, p.getX());
            fz0 = Math.min(fz0, p.getZ());
            fz1 = Math.max(fz1, p.getZ());
            int c = 0;
            BlockPos.MutableBlockPos m = p.mutable().move(0, -1, 0);
            while (level.getBlockState(m).is(GAP_OPEN) && c < 17) {
                c++;
                m.move(0, -1, 0);
            }
            gap = Math.min(gap, c);
        }
        if (gap >= 17) gap = 0;

        // leaves by the distance rule (leaves/*), then the logs, each taking its decor (decor/*) as it goes
        Take take = new Take(level, maxBlocks);
        for (BlockPos p : ours) take.leaves(p, 1);
        for (BlockPos p : logs) {
            take.blocks++;
            take.around(p);
        }
        List<BlockPos> leaves = take.crown;
        LongOpenHashSet treeLogs = new LongOpenHashSet();
        for (BlockPos p : logs) treeLogs.add(p.asLong());
        for (int i = take.decor.nextSetBit(0); i >= 0; i = take.decor.nextSetBit(i + 1)) treeLogs.add(leaves.get(i).asLong());
        int ymax = by, lymin = Integer.MAX_VALUE;
        for (int i = 0; i < leaves.size(); i++) {
            if (take.decor.get(i)) continue;
            BlockPos p = leaves.get(i);
            ymax = Math.max(ymax, p.getY());
            lymin = Math.min(lymin, p.getY());
        }
        for (BlockPos p : logs) ymax = Math.max(ymax, p.getY());

        // pivot and the fall direction
        int fcx = (fx0 + fx1 + 1) * 500, fcz = (fz0 + fz1 + 1) * 500;
        int w = Math.min(Math.max(fx1 - fx0, fz1 - fz0) + 1, 2);
        int hw = w * 500 - 10, lz = -hw;
        int py0 = by * 1000 + 2 - gap * 1000;
        int h = ymax - by + 1;
        int pyaw = Layout.snapYaw(playerYaw);
        Sweep sw = new Sweep(level, treeLogs, fcx, py0, fcz, hw, lz, h);
        int best = -1, byaw = pyaw;
        boolean done = false;
        for (int i = 0; i < TRIES.length && !(i >= 2 && done); i++) {
            int yaw = Math.floorMod(pyaw + TRIES[i], 36000);
            int th = sw.sweep(yaw);
            if (th > best) {
                best = th;
                byaw = yaw;
            }
            if (th >= 8500) done = true;
        }
        int dr = 0;
        if (best >= 13500) {
            best = 10500;
            dr = sw.drop(byaw);
        }

        int bin = byaw / 1125;
        int px = Layout.pivotX(fcx, hw, bin), pz = Layout.pivotZ(fcz, hw, bin);
        boolean hang = low == 1 && lymin > oy;
        int py = py0 + gap * 1000 + (hang ? 1000 : 0);
        BlockPos anchorCell = BlockPos.containing(px / 1000.0, py / 1000.0, pz / 1000.0);
        BlockPos anchor = null;
        List<Tree.Part> parts = new ArrayList<>(leaves.size() + logs.size());
        int km = 0, cw = 600;
        for (int pass = 0; pass < 2; pass++) {
            List<BlockPos> list = pass == 0 ? leaves : logs;
            for (int i = 0; i < list.size(); i++) {
                BlockPos p = list.get(i);
                int wx = p.getX() * 1000 - px, wy = p.getY() * 1000 - py, wz = p.getZ() * 1000 - pz;
                int k = Layout.ring(wy), tx = Layout.tx(wx, wz, bin);
                if (pass == 1) km = Math.max(km, k);
                else cw = Math.max(cw, Math.abs(tx) + 500);
                parts.add(new Tree.Part(p, level.getBlockState(p), pass == 0, tx, wy, Layout.tz(wx, wz, bin), k, pass == 0 && hideLeaves && !take.decor.get(i) && shut(level, p)));
                if (p.equals(anchorCell)) anchor = p;
            }
        }
        boolean[] logRings = new boolean[km + 1];
        for (Tree.Part part : parts) if (!part.leaf()) logRings[part.k()] = true;
        int rs = lymin == Integer.MAX_VALUE ? h : lymin - by - (hang ? 1 : 0);
        FallSpec spec = FallSpec.derive(h, best / 100.0, gap, hang, dr, km, rs);
        BlockState leaf = take.lastLeaf == null ? null : level.getBlockState(take.lastLeaf);
        return new Tree(origin, parts, px, py, pz, bin, spec, logRings, anchor, gap, hang, logs.size(), take.leafCount, hw,
            Math.min(cw, 4000), chopped, leaf);
    }

    /** 1.3.6 {@code leaves/hide} ({@code #timber:shell}): a leaf with leaves, logs or a creaking heart on all six sides gets no display. */
    private static boolean shut(ServerLevel level, BlockPos p) {
        for (Direction d : Direction.values()) {
            BlockState s = level.getBlockState(p.relative(d));
            if (!s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS) && !s.is(Blocks.CREAKING_HEART)) return false;
        }
        return true;
    }

    /** One 26-step layer at a time from {@code front}: unowned, unplaced nodes take the label of the node that reaches them first. */
    private static void spread(List<BlockPos> nodes, Long2IntOpenHashMap index, int[] label, BitSet mine, IntArrayList front) {
        while (!front.isEmpty()) {
            IntArrayList next = new IntArrayList();
            for (int j = 0; j < front.size(); j++) {
                int from = front.getInt(j);
                BlockPos p = nodes.get(from);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    int k = index.get(BlockPos.asLong(p.getX() + dx, p.getY() + dy, p.getZ() + dz));
                    if (k < 0 || label[k] != 0 || mine.get(k)) continue;
                    label[k] = label[from];
                    next.add(k);
                }
            }
            front = next;
        }
    }

    /** The crown walk: leaves (in order, decor interleaved where 1.3.6 took it) under the {@code max_blocks} cap. */
    private static final class Take {
        final ServerLevel level;
        final int max;
        final List<BlockPos> crown = new ArrayList<>();
        final BitSet decor = new BitSet();
        final LongOpenHashSet taken = new LongOpenHashSet();
        int blocks, leafCount;
        BlockPos lastLeaf;

        Take(ServerLevel level, int max) {
            this.level = level;
            this.max = max;
        }

        /** Natural leaves of distance exactly {@code d} next to {@code from}, then theirs of d + 1, up to 7. */
        void leaves(BlockPos from, int d) {
            if (d > LeavesBlock.DECAY_DISTANCE) return;
            for (Direction dir : FACES) {
                BlockPos q = from.relative(dir);
                BlockState s = level.getBlockState(q);
                if (!natural(s) || !s.hasProperty(LeavesBlock.DISTANCE) || s.getValue(LeavesBlock.DISTANCE) != d) continue;
                if (blocks >= max || !taken.add(q.asLong())) continue;
                blocks++;
                leafCount++;
                lastLeaf = q;
                crown.add(q);
                around(q);
                BlockPos up = q.above();
                if (level.getBlockState(up).is(Blocks.SNOW)) decor(up);
                leaves(q, d + 1);
            }
        }

        /** Decor on the six faces of a taken block. */
        void around(BlockPos p) {
            for (Direction dir : FACES) {
                BlockPos q = p.relative(dir);
                if (level.getBlockState(q).is(DECOR)) decor(q);
            }
        }

        /** One decor block, and the vines or moss hanging straight below it. */
        void decor(BlockPos q) {
            while (blocks < max && taken.add(q.asLong())) {
                blocks++;
                decor.set(crown.size());
                crown.add(q);
                q = q.below();
                if (!level.getBlockState(q).is(HANGING)) return;
            }
        }
    }

    /** The pitch sweep along the tilted trunk line, {@code execute positioned/rotated} arithmetic and all. */
    private record Sweep(ServerLevel level, LongOpenHashSet tree, int fcx, int py0, int fcz, int hw, int lz, int h) {
        /** Last free pitch x100 from 6 degrees up in steps of 3 (capped 135), as {@code fall/sweep}. */
        int sweep(int yaw) {
            int th = 600;
            while (true) {
                if (th >= 13501) return 13500;
                if (hits(yaw, th)) return th - 300;
                th += 300;
            }
        }

        Vec3 lane0(int yaw, int pitch) {
            float y = (float) (yaw * 0.01), x = (float) (pitch * 0.01);
            Vec3 p = new Vec3(fcx * 0.001, py0 * 0.001, fcz * 0.001);
            p = new LocalCoordinates(0, 0, hw * 0.001).apply(p, new Vec2(0, y));
            p = new LocalCoordinates(0, 0, lz * 0.001).apply(p, new Vec2(x, y));
            return new LocalCoordinates(0, 0.5, 0).apply(p, new Vec2(x, y));
        }

        boolean hits(int yaw, int pitch) {
            Vec3 p = lane0(yaw, pitch);
            Vec2 rot = new Vec2((float) (pitch * 0.01), (float) (yaw * 0.01));
            LocalCoordinates up = new LocalCoordinates(0, 1, 0);
            for (int r = h; ; ) {
                if (!open(BlockPos.containing(p))) return true;
                if (--r < 1) return false;
                p = up.apply(p, rot);
            }
        }

        /** Blocks the lying tree drops when it tips over an edge: the fewest open cells under its lanes, cap 16. */
        int drop(int yaw) {
            Vec3 p = lane0(yaw, 10500);
            Vec2 rot = new Vec2(105f, (float) (yaw * 0.01));
            LocalCoordinates up = new LocalCoordinates(0, 1, 0);
            int drop = 16;
            for (int r = h; ; ) {
                int c = 0;
                Vec3 q = p;
                while (true) {
                    q = q.add(0, -1, 0);
                    if (!open(BlockPos.containing(q))) break;
                    if (++c >= 16) break;
                }
                drop = Math.min(drop, c);
                if (--r < 1) return drop;
                p = up.apply(p, rot);
            }
        }

        boolean open(BlockPos p) {
            BlockState s = level.getBlockState(p);
            return s.is(PASSABLE) || s.is(BlockTags.LEAVES) || tree.contains(p.asLong());
        }
    }
}
