package io.github.profetgit.vanillatimber.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The mesh's copy of a vanilla display's interpolation, and the birch tick-5 key it rounds off (DESIGN § Birch Host pops). */
class TrailTest {
    @Test
    void anUpdateStartsWhereTheLastFrameWas() {
        Trail t = new Trail(1);
        t.reset(new double[] {0});
        t.update(0, 1, new double[] {1});
        assertEquals(0.84, t.draw(0.84)[0], 1e-12);
        t.update(1, 1, new double[] {0});
        // from = 0.84 (not 1), so the frame after the key shows lerp(0.84, 0, 0.17), not lerp(1, 0, 0.17)
        assertEquals(0.84 * (1 - 0.17), t.draw(1.17)[0], 1e-12);
    }

    @Test
    void withoutAnUpdateItReachesTheTarget() {
        Trail t = new Trail(1);
        t.reset(new double[] {0});
        t.update(0, 1, new double[] {1});
        t.draw(0.5);
        assertEquals(1, t.draw(1.7)[0], 1e-12);
        t.update(2, 1, new double[] {3});
        assertEquals(2, t.draw(2.5)[0], 1e-12);
    }

    @ParameterizedTest
    @ValueSource(strings = {"oak", "birch", "spruce"})
    void framesAtEachTickEndDrawTheTimeline(String tree) throws Exception {
        Timeline tl = TimelineTest.timeline(TimelineTest.load(tree));
        DrawnPose d = new DrawnPose(tl, -2, tl.spec.km());
        Timeline.Sample s = new Timeline.Sample();
        double[] oy = new double[d.oy.length], oz = new double[d.oz.length];
        for (int n = 0; n < tl.end; n++) {
            double tau = n + 0.999999;
            d.draw(tau);
            tl.sample(tau, s);
            assertEquals(s.pitch, d.rot + d.res, 1e-3, tree + " pitch at " + n);
            assertEquals(s.gy, d.gy, 1e-5, tree + " gy at " + n);
            assertEquals(s.lx, d.lx, 1e-5, tree + " lx at " + n);
            assertEquals(s.dy, d.dy, 1e-5, tree + " dy at " + n);
            // the drawn origins live below the byte step: turned by it, they are the timeline's
            Rig.origins(s.pitch, s.bend, s.gy, tl.spec.h(), -2, tl.spec.km(), oy, oz);
            double c = Math.cos(Math.toRadians(d.rot)), sn = Math.sin(Math.toRadians(d.rot));
            for (int i = 0; i < oy.length; i++) {
                assertEquals(oy[i], d.oy[i] * c - d.oz[i] * sn, 2e-4, tree + " ring " + i + " oy at " + n);
                assertEquals(oz[i], d.oy[i] * sn + d.oz[i] * c, 2e-4, tree + " ring " + i + " oz at " + n);
            }
        }
    }

    @Test
    void birchTickFiveSquashIsRoundedAt60fps() throws Exception {
        Timeline tl = TimelineTest.timeline(TimelineTest.load("birch"));
        assertTrue(tl.gy[5] < tl.gy[4] && tl.gy[5] < tl.gy[6], "tick 5 is a one-tick squash key");
        DrawnPose d = new DrawnPose(tl, -2, tl.spec.km());
        Timeline.Sample s = new Timeline.Sample();
        // three frames a tick, the last one in each at fraction 0.84 (the Host's phase in the m1 run): 4.84, then 5.17
        double[] drawn = new double[2], exact = new double[2];
        for (int j = 0; j < 16; j++) {
            double tau = j / 3.0 + 0.84 - 2 / 3.0;
            d.draw(tau);
            tl.sample(tau, s);
            if (j >= 14) {
                drawn[j - 14] = d.gy;
                exact[j - 14] = s.gy;
            }
        }
        // the exact pose turns at the key's corner (the frame after it is still squashed deeper); a display, restarting
        // from 4.84's pose, is already on its way back up
        assertTrue(exact[1] < exact[0], "exact " + exact[0] + " -> " + exact[1]);
        assertTrue(drawn[1] > drawn[0] + 0.002, "drawn " + drawn[0] + " -> " + drawn[1]);
        assertEquals(drawn[0] + (tl.gy[6] / 1000 - drawn[0]) * (15 / 3.0 + 0.84 - 2 / 3.0 - 5), drawn[1], 1e-9);
    }
}
