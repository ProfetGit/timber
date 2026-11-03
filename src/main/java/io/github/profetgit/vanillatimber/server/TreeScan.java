package io.github.profetgit.vanillatimber.server;

import io.github.profetgit.vanillatimber.core.FallSpec;
import io.github.profetgit.vanillatimber.core.Layout;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * The chop's scan on the live level, ported from the 1.3.6 job ({@code job/p2..p8}, {@code flood/*}, {@code own/*},
 * {@code leaves/*}, {@code fall/*}). Unlike the pack it never removes blocks to look: the tree's own cells count as
 * open wherever 1.3.6 had already taken them out.
 *
 * <p>M1: one trunk (no ownership split between touching trees), no decor, no creaking heart.
 */
public final class TreeScan {
    public static final TagKey<Block> LOGS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("timber", "logs"));
    public static final TagKey<Block> PASSABLE = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("timber", "passable"));
    public static final TagKey<Block> GAP_OPEN = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("timber", "gap_open"));
    static final int RADIUS = 12, MAX_LOGS = 256, MAX_BLOCKS = 900;
    static final int[] TRIES = {3375, -3375, 0, 6750, -6750, 9000, -9000};

    private TreeScan() {}

    static boolean natural(BlockState s) {
        return s.is(BlockTags.LEAVES) && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT);
    }

    /** The tree the chop at {@code origin} (already broken; it was {@code chopped}) would fell, or null. */
    public static Tree scan(ServerLevel level, BlockPos origin, BlockState chopped, float playerYaw) {
        Block log = chopped.getBlock();
        // flood: 26 neighbours of the same log, within the radius across
        List<BlockPos> flood = new ArrayList<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        seen.add(origin.asLong());
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                BlockPos q = p.offset(dx, dy, dz);
                if (Math.abs(q.getX() - origin.getX()) > RADIUS || Math.abs(q.getZ() - origin.getZ()) > RADIUS) continue;
                if (!seen.add(q.asLong()) || !level.getBlockState(q).is(log)) continue;
                if (flood.size() >= MAX_LOGS) return null;
                flood.add(q);
                queue.add(q);
            }
        }
        if (flood.isEmpty()) return null;
        // crown rule: attached natural leaves (x8) at least the logs, natural leaves just above the top
        List<BlockPos> ours = new ArrayList<>(flood);
        ours.add(origin);
        int leafCount = 0, top = Integer.MIN_VALUE;
        for (BlockPos p : ours) {
            for (Direction d : Direction.values()) if (natural(level.getBlockState(p.relative(d)))) leafCount++;
            top = Math.max(top, p.getY());
        }
        if (leafCount <= 0 || leafCount * 8 < flood.size()) return null;
        boolean crown = false;
        for (BlockPos p : ours) {
            if (p.getY() >= top - 1 && (natural(level.getBlockState(p.above())) || natural(level.getBlockState(p.above(2))))) {
                crown = true;
                break;
            }
        }
        if (!crown) return null;
        int oy = origin.getY();
        ours.removeIf(p -> p.getY() < oy);
        List<BlockPos> logs = new ArrayList<>(ours);
        logs.remove(origin);
        if (logs.isEmpty()) return null;
        LongOpenHashSet treeLogs = new LongOpenHashSet();
        for (BlockPos p : logs) treeLogs.add(p.asLong());

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

        // leaves by the distance rule: from each log, leaves of distance exactly d+1
        List<BlockPos> leaves = new ArrayList<>();
        LongOpenHashSet taken = new LongOpenHashSet();
        int ymax = by, lymin = Integer.MAX_VALUE;
        for (BlockPos p : ours) walk(level, p, 1, leaves, taken);
        for (BlockPos p : leaves) {
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
            for (BlockPos p : pass == 0 ? leaves : logs) {
                int wx = p.getX() * 1000 - px, wy = p.getY() * 1000 - py, wz = p.getZ() * 1000 - pz;
                int k = Layout.ring(wy), tx = Layout.tx(wx, wz, bin);
                if (pass == 1) km = Math.max(km, k);
                else cw = Math.max(cw, Math.abs(tx) + 500);
                parts.add(new Tree.Part(p, level.getBlockState(p), pass == 0, tx, wy, Layout.tz(wx, wz, bin), k));
                if (p.equals(anchorCell)) anchor = p;
            }
        }
        boolean[] logRings = new boolean[km + 1];
        for (Tree.Part part : parts) if (!part.leaf()) logRings[part.k()] = true;
        int rs = lymin == Integer.MAX_VALUE ? h : lymin - by - (hang ? 1 : 0);
        FallSpec spec = FallSpec.derive(h, best / 100.0, gap, hang, dr, km, rs);
        BlockState leaf = leaves.isEmpty() ? null : level.getBlockState(leaves.getLast());
        return new Tree(origin, parts, px, py, pz, bin, spec, logRings, anchor, gap, hang, logs.size(), leaves.size(), hw,
            Math.min(cw, 4000), chopped, leaf);
    }

    private static void walk(ServerLevel level, BlockPos from, int d, List<BlockPos> out, LongOpenHashSet taken) {
        if (d > LeavesBlock.DECAY_DISTANCE) return;
        for (Direction dir : Direction.values()) {
            BlockPos q = from.relative(dir);
            BlockState s = level.getBlockState(q);
            if (!natural(s) || !s.hasProperty(LeavesBlock.DISTANCE) || s.getValue(LeavesBlock.DISTANCE) != d) continue;
            if (taken.contains(q.asLong()) || out.size() >= MAX_BLOCKS) continue;
            taken.add(q.asLong());
            out.add(q);
            walk(level, q, d + 1, out, taken);
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
