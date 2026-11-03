package io.github.profetgit.vanillatimber.core;

/**
 * A vanilla display's transformation interpolation (Display.java: {@code lastProgress}, {@code createInterpolatedRenderState}):
 * each update starts from what the last drawn frame showed, not from the previous target, so a key that is held one
 * tick and left again is rounded off instead of hit. Times are drawing times (tau, see {@code dev/m1/DESIGN.md} §
 * Timing model): an update for server frame m over d ticks runs from tau = m - 1 to m - 1 + d.
 */
public final class Trail {
    public final double[] from, to, cur;
    private double start, dur = 1, last = 1;

    public Trail(int n) {
        from = new double[n];
        to = new double[n];
        cur = new double[n];
    }

    /** Rests at {@code v}. */
    public void reset(double[] v) {
        System.arraycopy(v, 0, from, 0, from.length);
        System.arraycopy(v, 0, to, 0, to.length);
        System.arraycopy(v, 0, cur, 0, cur.length);
        last = 1;
    }

    /** An update toward {@code v} from tau {@code start} over {@code ticks}; it starts where the last drawn frame was. */
    public void update(double start, int ticks, double[] v) {
        for (int i = 0; i < from.length; i++) {
            from[i] += (to[i] - from[i]) * last;
            to[i] = v[i];
        }
        this.start = start;
        dur = ticks;
    }

    /** The values drawn at tau into {@link #cur}; the progress is kept for the next update, as the display keeps it. */
    public double[] draw(double tau) {
        last = Math.max(0, Math.min(1, (tau - start) / dur));
        for (int i = 0; i < cur.length; i++) cur[i] = from[i] + (to[i] - from[i]) * last;
        return cur;
    }
}
