package io.github.profetgit.vanillatimber.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RigTest {
    @Test
    void straightTrunkStacksRingsAlongThePitch() {
        double[] oy = new double[12], oz = new double[12];
        Rig.origins(30, 0, 1, 10, -2, 9, oy, oz);
        for (int k = -2; k <= 9; k++) {
            assertEquals(k * Math.cos(Math.toRadians(30)), oy[k + 2], 1e-9, "oy ring " + k);
            assertEquals(k * Math.sin(Math.toRadians(30)), oz[k + 2], 1e-9, "oz ring " + k);
        }
    }

    @Test
    void bendShearsForwardGrowingWithHeight() {
        double[] oy = new double[11], oz = new double[11], sy = new double[11], sz = new double[11];
        Rig.origins(0, 5, 1, 10, 0, 10, oy, oz);
        Rig.origins(0, 0, 1, 10, 0, 10, sy, sz);
        // ring 0 has weight 0, so ring 1 sits straight on it; every ring above slides further
        assertEquals(0, oz[1], 1e-12);
        double last = 0;
        for (int k = 2; k <= 10; k++) {
            assertEquals(sy[k], oy[k], 1e-12, "upright bend keeps heights");
            assertTrue(oz[k] > last, "ring " + k + " slides further forward");
            last = oz[k];
        }
        // the slide of ring k+1 over ring k is bend * (k/h)^2 radians of a block
        assertEquals(Math.toRadians(5) * 0.25, oz[6] - oz[5], 1e-12);
    }

    @Test
    void cornerRotatesAboutThePivot() {
        double[] out = new double[3];
        Rig.corner(0.25, 3.0, -0.5, 3, 1, 1, 1, 90, 0, 3, out);
        assertEquals(0.25, out[0], 1e-12);
        assertEquals(0.5, out[1], 1e-12);
        assertEquals(3.0, out[2], 1e-12);
    }

    @Test
    void stepIsTheByteBelowThePitch() {
        assertEquals(0, Rig.step(1.0), 1e-12);
        assertEquals(1.40625, Rig.step(1.5), 1e-12);
        assertEquals(-1.40625, Rig.step(-0.1), 1e-12);
        assertEquals(90, Rig.step(91), 1e-12);
        assertEquals(-90, Rig.step(-120), 1e-12);
    }
}
