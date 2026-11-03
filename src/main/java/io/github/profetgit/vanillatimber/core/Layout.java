package io.github.profetgit.vanillatimber.core;

/**
 * Where each block of a fall sits in the tree frame, in the integer thousandths 1.3.6 used ({@code fall/sincos},
 * {@code fall/pivot}, {@code disp/loop}), so the server's displays and the client's mesh start from the same numbers.
 *
 * <p>The fall yaw is one of 32 bins of 11.25 degrees; sin/cos are the pack's 4-decimal table and the right rotation
 * its 6-decimal quaternion. Integer division is floor division, as scoreboard {@code /=} is.
 */
public final class Layout {
    private Layout() {}

    static final int[] SIN = {0, 1951, 3827, 5556, 7071, 8315, 9239, 9808, 10000, 9808, 9239, 8315, 7071, 5556, 3827, 1951,
        0, -1951, -3827, -5556, -7071, -8315, -9239, -9808, -10000, -9808, -9239, -8315, -7071, -5556, -3827, -1951};
    static final double[] QS = {0.0, 0.098017, 0.19509, 0.290285, 0.382683, 0.471397, 0.55557, 0.634393, 0.707107,
        0.77301, 0.83147, 0.881921, 0.92388, 0.95694, 0.980785, 0.995185, 1.0, 0.995185, 0.980785, 0.95694, 0.92388,
        0.881921, 0.83147, 0.77301, 0.707107, 0.634393, 0.55557, 0.471397, 0.382683, 0.290285, 0.19509, 0.098017};

    /** sin(yaw) x 10000 of yaw bin {@code i} (0..31). */
    public static int sin(int i) {
        return SIN[i & 31];
    }

    /** cos(yaw) x 10000 of yaw bin {@code i}. */
    public static int cos(int i) {
        return SIN[(i + 8) & 31];
    }

    /** The y and w components of the display's right rotation Ry(yaw), as 1.3.6 stored them. */
    public static double qs(int i) {
        return QS[i & 31];
    }

    public static double qc(int i) {
        // cos(yaw/2): the table's qc column is qs shifted by 16 bins, negated past half a turn
        int j = i & 31;
        return j <= 16 ? QS[16 - j] : -QS[j - 16];
    }

    /** The player's yaw snapped to a bin start, x100 ({@code job/p5}: +5.62 degrees, floored to 11.25). */
    public static int snapYaw(float yaw) {
        int y = (int) Math.floor(yaw * 100.0);
        return Math.floorDiv(Math.floorMod(y + 562, 36000), 1125) * 1125;
    }

    /** The pivot, x1000: the footprint centre (fcx, fcz) moved {@code hw} toward the fall ({@code fall/pivot}). */
    public static int pivotX(int fcx, int hw, int bin) {
        return fcx - Math.floorDiv(hw * sin(bin), 10000);
    }

    public static int pivotZ(int fcz, int hw, int bin) {
        return fcz + Math.floorDiv(hw * cos(bin), 10000);
    }

    /** Ring of a block whose corner is {@code wy} thousandths above the pivot. */
    public static int ring(int wy) {
        return Math.floorDiv(wy + 500, 1000);
    }

    /** The rest corner across the fall, x1000, from the corner's offset (wx, wz) to the pivot. */
    public static int tx(int wx, int wz, int bin) {
        return Math.floorDiv(wx * cos(bin) + wz * sin(bin), 10000);
    }

    public static int tz(int wx, int wz, int bin) {
        return Math.floorDiv(wz * cos(bin) - wx * sin(bin), 10000);
    }

    /** The spawn pose's 1.006x offsets about the block centre, x1000 ({@code job/p7} #ox, #oz). */
    public static int ox(int bin) {
        return Math.floorDiv((cos(bin) + sin(bin)) * -3, 10000);
    }

    public static int oz(int bin) {
        return Math.floorDiv((cos(bin) - sin(bin)) * -3, 10000);
    }
}
