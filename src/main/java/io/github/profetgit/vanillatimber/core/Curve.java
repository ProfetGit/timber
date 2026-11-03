package io.github.profetgit.vanillatimber.core;

/**
 * The fall curve of 1.3.x ({@code dev/gen.py} {@code rod_from_rest}): a rod released at rest at -10 degrees with torque
 * ~ sin(angle + 25 degrees), run to 90 degrees and normalised to 0..10000 in time and angle. One exact per-tick table
 * per fall length, built the way gen.py built them (Python's half-even rounding included), so 2.0 keys the same values.
 */
public final class Curve {
    public static final double LEAN = 10.0;
    static final double A0 = 25.0;
    public static final int MIN_DUR = 10, MAX_DUR = 26;
    private static final int FINE = 2000;
    private static final int[][] TABLES = new int[MAX_DUR + 1][];

    static {
        int[] fine = rodFromRest(-LEAN, 90.0, A0, FINE);
        for (int d = MIN_DUR; d <= MAX_DUR; d++) {
            int[] t = new int[d + 1];
            for (int i = 0; i <= d; i++) t[i] = fine[(int) Math.rint(FINE * (double) i / d)];
            TABLES[d] = t;
        }
    }

    private Curve() {}

    /** Normalised fall progress 0..10000 at tick {@code t} (clamped to 0..dur) of a fall {@code dur} ticks long. */
    public static int at(int dur, int t) {
        int[] table = TABLES[Math.max(MIN_DUR, Math.min(MAX_DUR, dur))];
        return table[Math.max(0, Math.min(table.length - 1, t))];
    }

    static int[] rodFromRest(double aStart, double aEnd, double a0, int n) {
        double th = Math.toRadians(aStart), w = 0, t = 0, dt = 1e-5, end = Math.toRadians(aEnd), off = Math.toRadians(a0);
        int cap = 1 << 16, size = 0;
        double[] ts = new double[cap], ths = new double[cap];
        while (th < end) {
            if (size == ts.length) {
                ts = java.util.Arrays.copyOf(ts, size * 2);
                ths = java.util.Arrays.copyOf(ths, size * 2);
            }
            ts[size] = t;
            ths[size++] = th;
            w += Math.sin(th + off) * dt;
            th += w * dt;
            t += dt;
        }
        ts = java.util.Arrays.copyOf(ts, size + 1);
        ths = java.util.Arrays.copyOf(ths, size + 1);
        ts[size] = t;
        ths[size++] = end;
        double total = t, span = end - Math.toRadians(aStart), start = Math.toRadians(aStart);
        int[] out = new int[n + 1];
        int j = 0;
        for (int i = 0; i <= n; i++) {
            double tt = total * i / n;
            while (j + 1 < size && ts[j + 1] <= tt) j++;
            out[i] = (int) Math.rint((ths[j] - start) / span * 10000);
        }
        out[n] = 10000;
        return out;
    }
}
