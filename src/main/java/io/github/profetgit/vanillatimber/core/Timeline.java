package io.github.profetgit.vanillatimber.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The whole fall as a pure function of (tree, tick): a tick-exact port of the 1.3.6 controller ({@code anim/tick} and
 * the phases under it), run once from the spec. Server and client build the same timeline from the same spec, so the
 * displays the server poses and the mesh a modded client draws can't disagree.
 *
 * <p>Tick 0 is the chop tick (the rest pose); frame {@code n} holds the controller state at the end of tick {@code n}.
 * Viewers see tick {@code n} one tick after its packets arrive (displays interpolate over 1 tick), so the drawing time
 * {@code tau} of {@link #sample} is in the same units: the pose of tick n is reached at tau = n.
 */
public final class Timeline {
    /** Something the server does on a tick: a sound, the slam, a ring's drops, the end. */
    public enum Kind { CHOP, LAND, CREAK, CRACK, WHOOSH, IMPACT, LEAVES_GONE, THUD, RING, FINISH }

    public record Event(int tick, Kind kind, int ring, float volume, float pitch) {}

    // spring and shiver constants of 1.3.6 (load.mcfunction #flex_*, anim/shiver)
    static final double FLEX_K = 0.35, FLEX_C = 0.35, FLEX_M = 0.90, BEND_MAX = 700;
    static final int[] SHIVER = {350, -260, 180, -110, 50};
    /** Ticks a popping ring lives after it is tagged (pop keys at u = 1, 4, 5; killed at u = 7). */
    public static final int POP_LIFE = 6;

    public final FallSpec spec;
    /** Per frame: pitch and tip bend (centi-degrees, as 1.3.6), squash (x1000), height offset (blocks), phase. */
    public final double[] p, bn, gx, gy, gz, lx, lz, dy;
    public final int[] ph;
    /** Tick each log ring is tagged to pop (-1: never), for rings 0..km. */
    public final int[] popTick;
    /** Tick the leaves are killed (they are gone from tau = leavesGone - 1). */
    public final int leavesGone;
    /** Last tick (everything is killed at its end). */
    public final int end;
    public final List<Event> events;

    public Timeline(FallSpec spec, boolean[] logRings) {
        this.spec = spec;
        Sim s = new Sim(spec, logRings);
        s.run();
        int n = s.frames.size();
        p = new double[n];
        bn = new double[n];
        gx = new double[n];
        gy = new double[n];
        gz = new double[n];
        lx = new double[n];
        lz = new double[n];
        dy = new double[n];
        ph = new int[n];
        for (int i = 0; i < n; i++) {
            double[] f = s.frames.get(i);
            p[i] = f[0];
            bn[i] = f[1];
            gx[i] = f[2];
            gy[i] = f[3];
            gz[i] = f[4];
            lx[i] = f[5];
            lz[i] = f[6];
            dy[i] = f[7];
            ph[i] = (int) f[8];
        }
        popTick = s.popTick;
        leavesGone = s.leavesGone;
        end = n - 1;
        events = Collections.unmodifiableList(s.events);
    }

    /** Events of one tick, in the order 1.3.6 fired them. */
    public List<Event> eventsAt(int tick) {
        List<Event> out = new ArrayList<>();
        for (Event e : events) if (e.tick == tick) out.add(e);
        return out;
    }

    /** The pose at drawing time {@code tau} (ticks; fractional between frames), interpolated linearly like displays. */
    public Sample sample(double tau, Sample out) {
        double t = Math.max(0, Math.min(end, tau));
        int a = (int) Math.floor(t);
        int b = Math.min(end, a + 1);
        double f = t - a;
        out.tau = tau;
        out.pitch = lerp(p[a], p[b], f) / 100.0;
        out.bend = lerp(bn[a], bn[b], f) / 100.0;
        out.gx = lerp(gx[a], gx[b], f) / 1000.0;
        out.gy = lerp(gy[a], gy[b], f) / 1000.0;
        out.gz = lerp(gz[a], gz[b], f) / 1000.0;
        out.lx = lerp(lx[a], lx[b], f) / 1000.0;
        out.lz = lerp(lz[a], lz[b], f) / 1000.0;
        out.dy = lerp(dy[a], dy[b], f);
        out.leaves = leavesGone < 0 || tau < leavesGone - 1;
        out.alive = tau < end;
        // the spawn pose is 1.006x about each block's centre (no z-fighting with the blocks still standing); the first
        // pose update brings it to 1 over the next tick
        out.inflate = 0.006 * (1 - Math.max(0, Math.min(1, tau)));
        return out;
    }

    /**
     * A popping log ring's scale at drawing time {@code tau}: {xz, y}, about each block's centre; null when the ring
     * isn't popping, {0, 0} once it is gone. The keys (1.25/0.7 over 3 ticks, 0.8/1.45 over 1, 0 over 2) start when
     * the tag tick's packets arrive, one tick before that tick's pose is reached.
     */
    public float[] pop(int ring, double tau, float[] out) {
        if (ring < 0 || ring >= popTick.length || popTick[ring] < 0) return null;
        double x = tau - (popTick[ring] - 1);
        if (x <= 0) return null;
        if (x < 3) {
            double u = x / 3;
            out[0] = (float) lerp(1, 1.25, u);
            out[1] = (float) lerp(1, 0.7, u);
        } else if (x < 4) {
            double u = x - 3;
            out[0] = (float) lerp(1.25, 0.8, u);
            out[1] = (float) lerp(0.7, 1.45, u);
        } else if (x < 6) {
            double u = (x - 4) / 2;
            out[0] = (float) lerp(0.8, 0, u);
            out[1] = (float) lerp(1.45, 0, u);
        } else {
            out[0] = 0;
            out[1] = 0;
        }
        return out;
    }

    static double lerp(double a, double b, double f) {
        return a + (b - a) * f;
    }

    /** One drawn pose: degrees, scale factors, blocks. */
    public static final class Sample {
        public double tau, pitch, bend, gx, gy, gz, lx, lz, dy, inflate;
        public boolean leaves, alive;
    }

    /** The 1.3.6 controller, one call of {@link #tick} per server tick. */
    private static final class Sim {
        final FallSpec s;
        final boolean[] logRings;
        final List<double[]> frames = new ArrayList<>();
        final List<Event> events = new ArrayList<>();
        final int[] popTick;
        int leavesGone = -1;
        int n, t, ph, dur, kr, ks, kf, kx;
        double p, bn, bv, pv, wv, gx = 1000, gy = 1000, gz = 1000, lx = 1000, lz = 1000, y, y0;
        boolean shiver = true, flex = true, hang, whoosh, dropped, done;

        Sim(FallSpec s, boolean[] logRings) {
            this.s = s;
            this.logRings = logRings;
            popTick = new int[s.km() + 1];
            java.util.Arrays.fill(popTick, -1);
            dur = s.dur();
            hang = s.fd() > 0;
            ph = hang ? 6 : 0;
        }

        void run() {
            event(Kind.CHOP, -1, 1, 0.6f);
            frame();
            while (!done && n < 2000) {
                n++;
                tick();
                frame();
            }
        }

        void frame() {
            frames.add(new double[] {p, bn, gx, gy, gz, lx, lz, y, ph});
        }

        void event(Kind k, int ring, float v, float pitch) {
            events.add(new Event(n, k, ring, v, pitch));
        }

        void phase(int to) {
            ph = to;
            t = 0;
        }

        void shape(double gx, double gy, double gz, double lx, double lz) {
            this.gx = gx;
            this.gy = gy;
            this.gz = gz;
            this.lx = lx;
            this.lz = lz;
        }

        void tick() {
            t++;
            switch (ph) {
                case 0 -> lean();
                case 1 -> fall();
                case 2 -> bounce();
                case 4 -> tipping();
                case 5 -> drop();
                case 6 -> { }
                default -> ripple();
            }
            pose();
            if (ph == 6) plunge();
        }

        void lean() {
            if (hang) {
                if (t == 5) shapeY(350);
                if (t == 6) shapeY(150);
                if (t == 7) shapeY(-120);
                if (t == 8) shapeY(-40);
                if (t == 9) shapeY(0);
                leanBack(t - 5);
            } else {
                leanBack(t - 2);
            }
        }

        /** The whole tree squashes (a > 0) or stretches by a/1000 blocks at its top, and widens by half as much. */
        void shapeY(double a) {
            double q = a / s.h();
            gy = 1000 - q;
            gx = 1000 + q / 2;
            gz = gx;
            lx = gx;
            lz = gx;
        }

        void leanBack(int l) {
            if (l <= 0) return;
            if (l == 1) event(Kind.CREAK, -1, 1, 0.5f);
            double x = Math.min(l, 7) / 7.0;
            p = -Curve.LEAN * 100 * x * x * (3 - 2 * x);
            if (l >= 7) {
                event(Kind.CRACK, -1, 0.9f, 0.55f);
                phase(1);
            }
        }

        void fall() {
            int u = Math.min(t, dur);
            double f = Curve.at(dur, u);
            double fr = Math.max(s.hit() * 100, 9000) + 1000;
            p = f * fr / 10000 - 1000;
            if (u * 100 / dur >= 55 && !whoosh) {
                whoosh = true;
                event(Kind.WHOOSH, -1, 0.8f, 0.6f);
            }
            if (p >= s.hit() * 100) settle();
        }

        void settle() {
            p = s.hit() * 100;
            phase(4);
            dur = 1;
        }

        void tipping() {
            if (t < dur) return;
            if (s.dr() >= 1 && !dropped) {
                phase(5);
                dropped = true;
                y0 = y;
                dur = Math.min(s.dr() + 2, 10);
                return;
            }
            p = s.hit() * 100;
            phase(2);
            flex = false;
            bn = 0;
            bv = 0;
            shape(1060, 1000, 820, 1080, 380);
            event(Kind.IMPACT, -1, 1.6f, 0.6f);
        }

        void drop() {
            y = y0 - (double) s.dr() * t * t / ((double) dur * dur);
            if (t >= dur) settle();
        }

        void bounce() {
            if (t == 1) shape(980, 1000, 1060, 1140, 90);
            if (t == 2) {
                leavesGone = n;
                event(Kind.LEAVES_GONE, -1, 0, 0);
                shape(1010, 1000, 985, 1000, 1000);
            }
            if (t == 3) shape(1000, 1000, 1000, 1000, 1000);
            if (t == 7) {
                event(Kind.THUD, -1, 0.8f, 0.9f);
                shape(1040, 1000, 900, 1000, 1000);
            }
            if (t == 8) shape(990, 1000, 1030, 1000, 1000);
            if (t >= 8) {
                phase(7);
                kr = 0;
                ks = 0;
                kf = 0;
                kx = 4;
                shape(1000, 1000, 1000, 1000, 1000);
                return;
            }
            arc(1, 6, 400, 3600);
        }

        void arc(int t0, int len, double num, double den) {
            double x = t - t0;
            p = s.hit() * 100 - x * (len - x) * s.amp() * 100 * num / den;
        }

        int gap(int k) {
            int g0 = s.km() >= 17 ? 2 : s.km() >= 9 ? 3 : 4;
            return Math.max(1, g0 - Math.floorDiv(k, 3));
        }

        void ripple() {
            if (t <= 4) arc(0, 4, 140, 1600);
            if (t == 4) event(Kind.THUD, -1, 0.5f, 1.2f);
            while (kr <= s.km() && t >= ks) {
                popTick[kr] = n;
                kr++;
                ks += gap(kr);
            }
            while (kf <= s.km() && t >= kx) {
                float pp = Math.min(80 + 6 * kf, 170) / 100f;
                event(Kind.RING, kf, 0.7f, pp);
                kf++;
                kx += gap(kf);
            }
            if (kf > s.km() && !popping()) {
                event(Kind.FINISH, -1, 0, 0);
                done = true;
            }
        }

        boolean popping() {
            for (int k = 0; k < popTick.length; k++) {
                if (popTick[k] >= 0 && logRings[k] && n - popTick[k] < POP_LIFE) return true;
            }
            return false;
        }

        void pose() {
            double w = p - pv;
            double acc = w - wv;
            pv = p;
            wv = w;
            if (shiver) {
                int a = t >= 1 && t <= 5 ? SHIVER[t - 1] : 0;
                bn = a * 17.2 / s.h();
                bv = 0;
                if (t >= 6) shiver = false;
            }
            // the tick the shiver ends, the spring already runs (1.3.6 tests the tag after anim/shiver removed it)
            if (flex && !shiver) {
                bv -= bn * FLEX_K;
                bv -= bv * FLEX_C;
                bv -= acc * FLEX_M;
                bn = Math.max(-BEND_MAX, Math.min(BEND_MAX, bn + bv));
            }
        }

        void plunge() {
            if (t <= 2) return;
            int tau = t - 3;
            double tt = (double) s.ft() * s.ft();
            double u = (s.fd() - 0.3) * (tt - (double) tau * tau) / tt;
            y = -s.fd() + u;
            if (tau < s.ft()) return;
            event(Kind.LAND, -1, 1, 0.6f);
            phase(0);
            t = 4;
        }
    }
}
