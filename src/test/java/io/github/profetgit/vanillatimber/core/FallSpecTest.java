package io.github.profetgit.vanillatimber.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Fall length by size alone (user 2026-11-18): 1.3.6's up to 28 rings, slower past it like a falling pole. */
class FallSpecTest {
    private static int old(int h) {
        return Math.max(10, Math.min(22, h / 2 + 8));
    }

    @Test
    void upToGiantSizeIsOneThreeSix() {
        for (int h = 1; h <= FallSpec.GIANT; h++) assertEquals(old(h), FallSpec.duration(h), "h " + h);
        assertEquals(FallSpec.derive(7, 90, 0, false, 0, 6, 4), FallSpec.sized(7, 90, 0, false, 0, 6, 4));
    }

    @Test
    void biggerTreesFallSlower() {
        assertEquals(31, FallSpec.duration(56));
        assertEquals(39, FallSpec.duration(90));
        int last = 0;
        for (int h = 1; h <= 255; h++) {
            int d = FallSpec.duration(h);
            assertTrue(d >= last, "monotonic at h " + h);
            assertTrue(d <= Curve.MAX_DUR);
            last = d;
        }
    }

    @Test
    void longCurvesRunTheWholeArc() {
        for (int d : new int[] {39, 80, Curve.MAX_DUR}) {
            assertEquals(0, Curve.at(d, 0));
            assertEquals(10000, Curve.at(d, d));
            for (int t = 1; t <= d; t++) assertTrue(Curve.at(d, t) >= Curve.at(d, t - 1));
        }
    }
}
