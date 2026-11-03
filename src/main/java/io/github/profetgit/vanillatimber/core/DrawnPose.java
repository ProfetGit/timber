package io.github.profetgit.vanillatimber.core;

/**
 * A fall as a vanilla client draws 1.3.6's displays of it, frame by frame, so the mesh looks the same at sharp keys
 * (the hang tree's tick-5 landing squash, the last shiver key). What the entity carries moves linearly between ticks:
 * the pitch's byte step ({@link Rig#step}) and the height. What the transformation carries follows {@link Trail}: the
 * residual pitch, the group squashes, the ring origins (bend and squash), the spawn inflate, and each ring's pop keys.
 * Updates come on the frames the server sends them ({@code Fall.pose}: only when the pose changed; pops at u = 1, 4, 5).
 * Call {@link #draw} once per drawn frame.
 */
public final class DrawnPose {
    static final int[][] POP = {{0, 1250, 700, 3}, {3, 800, 1450, 1}, {4, 0, 0, 2}};

    public final Timeline tl;
    public final int kn, kt;
    /** This frame: the entity's pitch, the residual on top (degrees), height (blocks), group scales, spawn inflate. */
    public double tau, rot, res, dy, gx, gy, gz, lx, lz, inflate;
    public boolean leaves;
    /** Ring origins (y, z) of rings kn..kt at index k - kn, in the entity's frame (below the byte step). */
    public final double[] oy, oz;
    private final Trail main;
    private final Trail[] pops;
    private final int[] popNext;
    private final double[] v, ty, tz, key = new double[2];
    private int next = 1;

    public DrawnPose(Timeline tl, int kn, int kt) {
        this.tl = tl;
        this.kn = kn;
        this.kt = kt;
        int nr = kt - kn + 1;
        oy = new double[nr];
        oz = new double[nr];
        ty = new double[nr];
        tz = new double[nr];
        v = new double[7 + 2 * nr];
        main = new Trail(v.length);
        values(0, v);
        main.reset(v);
        pops = new Trail[tl.popTick.length];
        popNext = new int[tl.popTick.length];
        for (int k = 0; k < pops.length; k++) {
            if (tl.popTick[k] < 0) continue;
            pops[k] = new Trail(2);
            pops[k].reset(new double[] {1, 1});
        }
        read(0);
    }

    /** Frame n's transformation as the server sends it (residual, scales, inflate, origins at the residual). */
    private void values(int n, double[] out) {
        double pitch = tl.p[n] / 100.0, r = pitch - Rig.step(pitch);
        out[0] = r;
        out[1] = tl.gx[n] / 1000;
        out[2] = tl.gy[n] / 1000;
        out[3] = tl.gz[n] / 1000;
        out[4] = tl.lx[n] / 1000;
        out[5] = tl.lz[n] / 1000;
        out[6] = n == 0 ? 0.006 : 0;
        Rig.origins(r, tl.bn[n] / 100.0, tl.gy[n] / 1000.0, tl.spec.h(), kn, kt, ty, tz);
        System.arraycopy(ty, 0, out, 7, ty.length);
        System.arraycopy(tz, 0, out, 7 + ty.length, tz.length);
    }

    private boolean changed(int n) {
        return tl.p[n] != tl.p[n - 1] || tl.bn[n] != tl.bn[n - 1] || tl.gx[n] != tl.gx[n - 1] || tl.gy[n] != tl.gy[n - 1]
            || tl.gz[n] != tl.gz[n - 1] || tl.lx[n] != tl.lx[n - 1] || tl.lz[n] != tl.lz[n - 1];
    }

    /** The pose drawn at {@code tau}: starts the updates due by then, then draws (keeps each trail's progress). */
    public DrawnPose draw(double tau) {
        this.tau = tau;
        double t = Math.max(0, Math.min(tl.end, tau));
        for (; next <= tl.end && next - 1 <= t; next++) {
            if (!changed(next)) continue;
            values(next, v);
            main.update(next - 1, 1, v);
        }
        for (int k = 0; k < pops.length; k++) {
            Trail p = pops[k];
            if (p == null) continue;
            for (; popNext[k] < POP.length && tl.popTick[k] + POP[popNext[k]][0] - 1 <= t; popNext[k]++) {
                int[] q = POP[popNext[k]];
                key[0] = q[1] / 1000.0;
                key[1] = q[2] / 1000.0;
                p.update(tl.popTick[k] + q[0] - 1, q[3], key);
            }
            if (popNext[k] > 0) p.draw(t);
        }
        main.draw(t);
        read(t);
        return this;
    }

    private void read(double t) {
        double[] c = main.cur;
        res = c[0];
        gx = c[1];
        gy = c[2];
        gz = c[3];
        lx = c[4];
        lz = c[5];
        inflate = c[6];
        System.arraycopy(c, 7, oy, 0, oy.length);
        System.arraycopy(c, 7 + oy.length, oz, 0, oz.length);
        int a = (int) Math.floor(t), b = Math.min(tl.end, a + 1);
        double f = t - a;
        double sa = Rig.step(tl.p[a] / 100.0), sb = Rig.step(tl.p[b] / 100.0);
        rot = sa + (sb - sa) * f;
        dy = tl.dy[a] + (tl.dy[b] - tl.dy[a]) * f;
        leaves = tl.leavesGone < 0 || tau < tl.leavesGone - 1;
    }

    /**
     * A popping log ring's scale this frame, {xz, y} about each block's centre: null before its first key, {0, 0} once it
     * is gone (the server kills it at u = 7).
     */
    public float[] pop(int ring, float[] out) {
        if (ring < 0 || ring >= pops.length || pops[ring] == null || popNext[ring] == 0) return null;
        if (tau >= tl.popTick[ring] + 5) {
            out[0] = 0;
            out[1] = 0;
        } else {
            out[0] = (float) pops[ring].cur[0];
            out[1] = (float) pops[ring].cur[1];
        }
        return out;
    }
}
