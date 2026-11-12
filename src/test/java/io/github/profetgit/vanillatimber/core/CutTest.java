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
    void sidewaysLogShrinksAcross() {
        int b = Cut.across(0, 4);
        assertEquals(Cut.pack(0, 16, 4, 12, 4, 12), b);
        assertTrue(Cut.covers(b, 0, true) == false);
        assertTrue(Cut.covers(Cut.WHOLE, 1, true));
    }
}
