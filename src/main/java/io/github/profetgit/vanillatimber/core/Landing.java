package io.github.profetgit.vanillatimber.core;

/**
 * Which part of a felled tree stays on the ground as blocks ({@code land_blocks}), decided from the tree frame alone, so
 * the server that places the blocks and the modded client that stops drawing them at that tick agree without more data
 * than the landed-ring mask: a share of the log rings, spread evenly from the stump up, and the leaves next to them.
 */
public final class Landing {
    /**
     * A leaf lands when a log of a landed ring is within this many thousandths of it on every axis of the tree frame (1
     * block: the leaves touching the trunk). 2 blocks kept nearly the whole crown and buried the trunk (critic
     * 2026-11-17; the user asked to "keep some of its leafs").
     */
    public static final int LEAF_REACH = 1000;

    private Landing() {}

    /**
     * The landed rings: of the {@code R} rings with logs, {@code round(pct * R / 100)}, one per Bresenham step from the
     * stump up (ring order), so any share keeps the stump ring first.
     */
    public static boolean[] rings(boolean[] logRings, int pct) {
        boolean[] out = new boolean[logRings.length];
        int r = 0;
        for (boolean b : logRings) if (b) r++;
        int n = Math.min(r, (Math.max(0, pct) * r + 50) / 100);
        if (n <= 0) return out;
        int j = 0;
        for (int k = 0; k < logRings.length; k++) {
            if (!logRings[k]) continue;
            out[k] = -Math.floorDiv(-(j + 1) * n, r) > -Math.floorDiv(-j * n, r);
            j++;
        }
        return out;
    }

    /** Per leaf, the ring of the nearest landed log within {@link #LEAF_REACH} (tree-frame thousandths), or -1: the leaf doesn't land. */
    public static int[] leafRings(int[] lx, int[] ly, int[] lz, int[] gx, int[] gy, int[] gz, int[] gk) {
        int[] out = new int[lx.length];
        for (int i = 0; i < out.length; i++) {
            int best = -1;
            long bd = Long.MAX_VALUE;
            for (int j = 0; j < gx.length; j++) {
                long dx = gx[j] - lx[i], dy = gy[j] - ly[i], dz = gz[j] - lz[i];
                if (Math.abs(dx) > LEAF_REACH || Math.abs(dy) > LEAF_REACH || Math.abs(dz) > LEAF_REACH) continue;
                long d = dx * dx + dy * dy + dz * dz;
                if (d < bd || d == bd && gk[j] < best) {
                    bd = d;
                    best = gk[j];
                }
            }
            out[i] = best;
        }
        return out;
    }
}
