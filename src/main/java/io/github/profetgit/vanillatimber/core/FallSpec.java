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
        double hit = Math.max(best, 6.0);
        int dur = Math.max(10, Math.min(22, h / 2 + 8));
        // centi-degrees with floor division, as the scoreboard did it
        int hc = (int) Math.round(hit * 100), tilt = Math.max(hc - 9000, 0);
        int ac = Math.floorDiv(Math.floorDiv((hc - tilt) * 12, 100) * 7, Math.max(7, h));
        double amp = Math.max(200, Math.min(1100, ac)) / 100.0;
        double fd = gap + (hang ? 1 : 0);
        int ft = Math.min(gap + 1, 8);
        return new FallSpec(h, hit, dur, amp, km, fd, ft, dr, rs);
    }
}
