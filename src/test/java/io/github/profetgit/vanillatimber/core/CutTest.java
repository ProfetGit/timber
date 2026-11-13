package io.github.profetgit.vanillatimber.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Chop mode's cut boxes (PRD 5.13): a layer shrinks as one footprint, 1x1 16 -> 4 px, 2x2 32 -> 8. */
class CutTest {
    private static int width(int box, int axis) {
        return Cut.hi(box, axis) - Cut.lo(box, axis);
    }

    @Test
    void steps() {
        assertEquals(0, Cut.step(0, 4));
        assertEquals(2, Cut.step(1, 4));
        assertEquals(4, Cut.step(2, 4));
        assertEquals(6, Cut.step(3, 4));
        assertEquals(6, Cut.step(9, 4));
        assertEquals(0, Cut.step(1, 1));
    }

    @Test
    void oneByOneShrinksAroundItsCentre() {
        int b = Cut.upright(5, 5, 7, 7, 5, 7, 6);
        assertEquals(Cut.pack(6, 10, 0, 16, 6, 10), b);
        assertEquals(4, width(b, 0));
        assertEquals(Cut.WHOLE, Cut.upright(5, 5, 7, 7, 5, 7, 0));
    }

    @Test
    void twoByTwoShrinksAsOneFootprint() {
        // at the deepest step the four cells hold 4 x 4 px each, touching at the trunk's centre: 32 -> 8 px
        for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) {
            int b = Cut.upright(0, 1, 0, 1, x, z, 6);
            assertEquals(4, width(b, 0));
            assertEquals(4, width(b, 2));
            assertEquals(16, width(b, 1));
            assertTrue(Cut.touches(b, 0, x == 0), "inner x face on the boundary");
            assertTrue(Cut.touches(b, 2, z == 0), "inner z face on the boundary");
            assertFalse(Cut.touches(b, 0, x == 1), "outer x face moved in");
        }
        assertTrue(Cut.meets(Cut.upright(0, 1, 0, 1, 0, 0, 3), Cut.upright(0, 1, 0, 1, 1, 0, 3), 0));
        int total = width(Cut.upright(0, 1, 0, 1, 0, 0, 3), 0) + width(Cut.upright(0, 1, 0, 1, 1, 0, 3), 0);
        assertEquals(32 - 2 * 2 * 3, total);
    }

    @Test
    void wideLayersShrinkAsOneFootprint() {
        // 3x3 .. 5x5 at the deepest step: n*16 -> n*16 - 2*n*6 px, inner cells untouched until the outer ones are gone
        for (int n = 3; n <= 5; n++) {
            for (int step = 0; step <= Cut.MAX_STEP; step++) {
                for (int z = 0; z < n; z++) {
                    int total = 0;
                    for (int x = 0; x < n; x++) {
                        int b = Cut.upright(0, n - 1, 0, n - 1, x, z, step);
                        total += width(b, 0);
                        assertEquals(16, width(b, 1));
                        if (x > 0 && width(b, 0) > 0 && width(Cut.upright(0, n - 1, 0, n - 1, x - 1, z, step), 0) > 0)
                            assertTrue(Cut.meets(Cut.upright(0, n - 1, 0, n - 1, x - 1, z, step), b, 0) || width(b, 2) == 0, "neighbours meet");
                    }
                    if (width(Cut.upright(0, n - 1, 0, n - 1, n / 2, z, step), 2) > 0) assertEquals(16 * n - 2 * n * step, total, n + "x" + n + " step " + step);
                }
            }
            int centre = Cut.upright(0, n - 1, 0, n - 1, n / 2, n / 2, Cut.MAX_STEP);
            assertEquals(n == 3 ? 12 : n == 4 ? 8 : 16, width(centre, 0), n + "x" + n + " cell right of the centre line: 4n px left in all");
        }
        assertEquals(Cut.WHOLE, Cut.upright(0, 2, 0, 2, 1, 1, 2));
        // 3x3 at the deepest step: the outer ring has no volume left, the centre cell holds the 12 px core
        assertTrue(Cut.empty(Cut.upright(0, 2, 0, 2, 0, 1, 6)));
        assertTrue(Cut.empty(Cut.upright(0, 2, 0, 2, 1, 0, 6)));
        assertFalse(Cut.empty(Cut.upright(0, 2, 0, 2, 1, 1, 6)));
        assertFalse(Cut.empty(Cut.WHOLE));
    }

    @Test
    void sidewaysLogShrinksAcross() {
        int b = Cut.across(0, 4);
        assertEquals(Cut.pack(0, 16, 4, 12, 4, 12), b);
        assertTrue(Cut.covers(b, 0, true) == false);
        assertTrue(Cut.covers(Cut.WHOLE, 1, true));
    }

    @Test
    void shapedIsUprightOnRectanglesAndErodesEveryShape() {
        for (int step = 0; step <= 6; step++)
            for (int x = 3; x <= 5; x++)
                for (int z = 7; z <= 8; z++)
                    assertEquals(Cut.upright(3, 5, 7, 8, x, z, step), Cut.shaped(3, 5, 7, 8, x, z, step));
        // a plus, step 1: its top arm (1,0) is 1 wide along x (run 1) and the column is 3 long
        assertEquals(Cut.pack(1, 15, 0, 16, 3, 16), Cut.shaped(1, 1, 0, 2, 1, 0, 1));
        // its left cell (0,1): the row is 3 long, the column 1 wide
        assertEquals(Cut.pack(3, 16, 0, 16, 1, 15), Cut.shaped(0, 2, 1, 1, 0, 1, 1));
        // the centre keeps its middle: both runs reach past it
        assertEquals(Cut.WHOLE, Cut.shaped(0, 2, 0, 2, 1, 1, 1));
        // a detached pillar is a 1x1 run: 16 -> 4 at the last stage, whatever the rest of the trunk does
        assertEquals(Cut.pack(6, 10, 0, 16, 6, 10), Cut.shaped(9, 9, 4, 4, 9, 4, 6));
    }
}
