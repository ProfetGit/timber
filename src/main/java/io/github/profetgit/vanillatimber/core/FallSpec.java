package io.github.profetgit.vanillatimber.core;

/**
 * What the chop decided about one fall, the timeline's whole input (1.3.6 {@code job/p7}, {@code p8}, {@code disp/start}).
 *
 * @param h    tree height in rings, cut level included
 * @param hit  landing pitch in degrees (6..135; past 90 the tree tips over an edge)
 * @param dur  fall length in ticks
 * @param amp  bounce height in degrees
 * @param km   highest ring with a log (the ripple runs 0..km)
 * @param fd   plunge depth in blocks before the lean (0: no plunge; the hang case adds 1)
 * @param ft   plunge length in ticks
 * @param dr   blocks the lying tree drops after tipping over an edge (0: none)
 * @param rs   first crown ring (the slam's leaf burst starts there)
 */
public record FallSpec(int h, double hit, int dur, double amp, int km, double fd, int ft, int dr, int rs) {
    /**
     * The spec 1.3.6 derives from the scan: {@code best} the sweep's landing pitch (degrees), {@code gap} open cells
     * under the cut, {@code hang} whether the tree hangs over the cut (one log at the cut level, no leaves there).
     */
    public static FallSpec derive(int h, double best, int gap, boolean hang, int dr, int km, int rs) {
        return make(h, best, gap, hang, dr, km, rs, Math.max(10, Math.min(22, h / 2 + 8)));
    }

    /** Trees past this many rings fall in more than 1.3.6's 22 ticks (user 2026-11-17: giants fell "like toothpicks"). */
    public static final int GIANT = 28;

    /**
     * As {@link #derive(int, double, int, boolean, int, int, int)} with the length from the tree's size alone: bigger
     * trees fall slower (user 2026-11-18: no speed setting, "larger trees should just in general fall a little bit
     * slower than the smaller ones").
     */
    public static FallSpec sized(int h, double best, int gap, boolean hang, int dr, int km, int rs) {
        return make(h, best, gap, hang, dr, km, rs, duration(h));
    }

    /**
     * Fall length in ticks for a tree {@code h} rings tall: 1.3.6's up to {@link #GIANT} rings (oak 6 -> 11, 28 -> 22),
     * past it 22 x sqrt(h / 28), like a falling pole (56 rings 31, 90 rings 39), at most {@link Curve#MAX_DUR}.
     */
    public static int duration(int h) {
        double base = h > GIANT ? 22 * Math.sqrt(h / (double) GIANT) : Math.max(10, Math.min(22, h / 2 + 8));
        return (int) Math.max(Curve.MIN_DUR, Math.min(Curve.MAX_DUR, Math.round(base)));
    }

    private static FallSpec make(int h, double best, int gap, boolean hang, int dr, int km, int rs, int dur) {
        double hit = Math.max(best, 6.0);
        // centi-degrees with floor division, as the scoreboard did it
        int hc = (int) Math.round(hit * 100), tilt = Math.max(hc - 9000, 0);
        int ac = Math.floorDiv(Math.floorDiv((hc - tilt) * 12, 100) * 7, Math.max(7, h));
        double amp = Math.max(200, Math.min(1100, ac)) / 100.0;
        double fd = gap + (hang ? 1 : 0);
        int ft = Math.min(gap + 1, 8);
        return new FallSpec(h, hit, dur, amp, km, fd, ft, dr, rs);
    }
}
