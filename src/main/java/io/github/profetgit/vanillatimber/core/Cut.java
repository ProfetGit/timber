package io.github.profetgit.vanillatimber.core;

/**
 * Chop mode's shrinking cut (PRD 5.13) as plain numbers: the box a chopped cell still fills, in sixteenths of the cell
 * on each axis, packed 5 bits per bound into one int ({@code x0 x1 y0 y1 z0 z1}). An upright layer shrinks as one
 * footprint around its centre, so only the trunk's outside faces move in (1x1 16 -> 4 px, 2x2 32 -> 8); a sideways
 * log shrinks around its own centre across its axis.
 */
public final class Cut {
    /** The most a side moves in per cell of the footprint, in sixteenths: a 1x1 ends at 16 - 2 x 6 = 4. */
    public static final int MAX_STEP = 6;
    public static final int WHOLE = pack(0, 16, 0, 16, 0, 16);

    private Cut() {}

    public static int pack(int x0, int x1, int y0, int y1, int z0, int z1) {
        return x0 | x1 << 5 | y0 << 10 | y1 << 15 | z0 << 20 | z1 << 25;
    }

    /** Bound {@code i} (0 x0, 1 x1, 2 y0, 3 y1, 4 z0, 5 z1). */
    public static int bound(int box, int i) {
        return box >>> (5 * i) & 31;
    }

    public static int lo(int box, int axis) {
        return bound(box, axis * 2);
    }

    public static int hi(int box, int axis) {
        return bound(box, axis * 2 + 1);
    }

    /** How far each side has moved in per footprint cell after {@code chops} of {@code needed}: round(6 n / (needed - 1)), so the chop before the last leaves the thinnest core. */
    public static int step(int chops, int needed) {
        if (chops <= 0 || needed <= 1) return 0;
        return Math.min(MAX_STEP, Math.round((float) MAX_STEP * chops / (needed - 1)));
    }

    /**
     * The box of cell ({@code x}, {@code z}) in an upright layer whose footprint spans {@code x0..x1}, {@code z0..z1}
     * (block coordinates, inclusive): the footprint shrinks by {@code size x step} sixteenths on each side.
     */
    public static int upright(int x0, int x1, int z0, int z1, int x, int z, int step) {
        int[] ax = span(x0, x1, x, step), az = span(z0, z1, z, step);
        return pack(ax[0], ax[1], 0, 16, az[0], az[1]);
    }

    /**
     * The box of cell ({@code x}, {@code z}) in an upright layer of any shape: on each axis the layer's run through the
     * cell ({@code rx0..rx1} along x, {@code rz0..rz1} along z, block coordinates, inclusive) shrinks by
     * {@code run length x step} sixteenths from both ends, so every run narrows like a 1xN trunk would. On a rectangle
     * every run is the whole footprint, so this is {@link #upright}; a plus, an arch's pillars or a ragged redwood base
     * each erode from their own outside.
     */
    public static int shaped(int rx0, int rx1, int rz0, int rz1, int x, int z, int step) {
        int[] ax = span(rx0, rx1, x, step), az = span(rz0, rz1, z, step);
        return pack(ax[0], ax[1], 0, 16, az[0], az[1]);
    }

    private static int[] span(int from, int to, int at, int step) {
        int size = to - from + 1;
        int lo = from * 16 + size * step - at * 16, hi = (to + 1) * 16 - size * step - at * 16;
        return new int[] {Math.max(0, Math.min(16, lo)), Math.max(0, Math.min(16, hi))};
    }

    /** The footprint has left the cell on some axis: the box has no volume (a wide trunk's outer ring late in the cut). */
    public static boolean empty(int box) {
        for (int a = 0; a < 3; a++) if (hi(box, a) <= lo(box, a)) return true;
        return false;
    }

    /** A lone log lying along {@code axis} (0 x, 1 y, 2 z): it shrinks around its centre on the other two axes. */
    public static int across(int axis, int step) {
        int[] b = new int[6];
        for (int a = 0; a < 3; a++) {
            b[a * 2] = a == axis ? 0 : step;
            b[a * 2 + 1] = a == axis ? 16 : 16 - step;
        }
        return pack(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    /** The box reaches the cell's face on {@code axis} in the positive ({@code up}) or negative direction. */
    public static boolean touches(int box, int axis, boolean up) {
        return up ? hi(box, axis) == 16 : lo(box, axis) == 0;
    }

    /** The box covers the whole face on {@code axis}: it touches it and spans the other two axes fully. */
    public static boolean covers(int box, int axis, boolean up) {
        if (!touches(box, axis, up)) return false;
        for (int a = 0; a < 3; a++) if (a != axis && (lo(box, a) != 0 || hi(box, a) != 16)) return false;
        return true;
    }

    /** Two neighbouring cells' boxes meet face to face across their shared face ({@code axis}, {@code a} on the negative side). */
    public static boolean meets(int a, int b, int axis) {
        if (!touches(a, axis, true) || !touches(b, axis, false)) return false;
        for (int o = 0; o < 3; o++) if (o != axis && (lo(a, o) != lo(b, o) || hi(a, o) != hi(b, o))) return false;
        return true;
    }
}
