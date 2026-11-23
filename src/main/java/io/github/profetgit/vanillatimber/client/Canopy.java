package io.github.profetgit.vanillatimber.client;

import io.github.profetgit.vanillatimber.net.TreeFall;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The tree's own sky shade: how much light each face would get standing alone under an open sky, so the pockets under the
 * canopy and between leaf clumps stay dark for the whole fall (a falling tree is drawn in open air where the world's
 * light has none of it). A vanilla-style sky flood (15 straight down through air, one less per step, leaves cost 1, logs
 * stop it) over the tree's cells only, read through the lightmap's curve as a colour multiplier per face. It is rigid, so
 * it is baked once; {@link MeshFall} crossfades it in as the terrain's own light fades out.
 */
final class Canopy {
    /** How much of the standing shade a face keeps, and the darkest multiplier. */
    static final float STRENGTH = 0.85f, FLOOR = 0.4f;
    private static final int MAX_CELLS = 4_000_000;
    private static final Direction[] DIRS = Direction.values();
    private static final float[] CURVE = new float[16];

    static {
        // the default lightmap: ambient curve v / (4 - 3v), then brightness 0.5 lifts it by 1 - (1 - x)^4, relative to light 15
        for (int l = 0; l < 16; l++) {
            float v = l / 15f, base = v / (4 - 3 * v), lifted = 1 - (float) Math.pow(1 - base, 4);
            CURVE[l] = base + (lifted - base) * 0.5f;
        }
        float top = CURVE[15];
        for (int l = 0; l < 16; l++) CURVE[l] /= top;
    }

    private Canopy() {}

    /** Per block {@code i} and face {@code Direction.ordinal}, at {@code [i * 6 + d]}: the colour multiplier 1 (open) .. {@link #FLOOR}. */
    static float[] faces(TreeFall msg) {
        int blocks = msg.blocks();
        float[] out = new float[blocks * 6];
        java.util.Arrays.fill(out, 1f);
        if (blocks == 0) return out;
        int[] cells = msg.cells();
        int x0 = Integer.MAX_VALUE, y0 = x0, z0 = x0, x1 = Integer.MIN_VALUE, y1 = x1, z1 = x1;
        for (int i = 0; i < blocks; i++) {
            x0 = Math.min(x0, cells[i * 4]);
            x1 = Math.max(x1, cells[i * 4]);
            y0 = Math.min(y0, cells[i * 4 + 1]);
            y1 = Math.max(y1, cells[i * 4 + 1]);
            z0 = Math.min(z0, cells[i * 4 + 2]);
            z1 = Math.max(z1, cells[i * 4 + 2]);
        }
        // one open cell around the tree on every side, so the crown's faces always have a neighbour to read
        x0--;
        y0--;
        z0--;
        int nx = x1 - x0 + 2, ny = y1 - y0 + 2, nz = z1 - z0 + 2;
        long total = (long) nx * ny * nz;
        if (total > MAX_CELLS) return out;
        int n = (int) total;
        byte[] damp = new byte[n], level = new byte[n];
        for (int i = 0; i < blocks; i++) {
            BlockState st = msg.palette().get(cells[i * 4 + 3] >> 2);
            damp[index(cells[i * 4] - x0, cells[i * 4 + 1] - y0, cells[i * 4 + 2] - z0, nx, nz)] = (byte) Math.min(15, st.getLightDampening());
        }
        IntArrayList[] queue = new IntArrayList[16];
        for (int l = 1; l < 16; l++) queue[l] = new IntArrayList();
        for (int z = 0; z < nz; z++) {
            for (int x = 0; x < nx; x++) {
                int at = index(x, ny - 1, z, nx, nz);
                level[at] = 15;
                queue[15].add(at);
            }
        }
        int plane = nx * nz;
        for (int l = 15; l >= 1; l--) {
            IntArrayList q = queue[l];
            for (int k = 0; k < q.size(); k++) {
                int at = q.getInt(k);
                if (level[at] != l) continue;
                int x = at % nx, z = at / nx % nz, y = at / plane;
                for (Direction d : DIRS) {
                    int tx = x + d.getStepX(), ty = y + d.getStepY(), tz = z + d.getStepZ();
                    if (tx < 0 || ty < 0 || tz < 0 || tx >= nx || ty >= ny || tz >= nz) continue;
                    int to = index(tx, ty, tz, nx, nz);
                    int next = d == Direction.DOWN && l == 15 && damp[to] == 0 ? 15 : l - Math.max(1, damp[to]);
                    if (next > level[to]) {
                        level[to] = (byte) next;
                        queue[next].add(to);
                    }
                }
            }
        }
        for (int i = 0; i < blocks; i++) {
            int x = cells[i * 4] - x0, y = cells[i * 4 + 1] - y0, z = cells[i * 4 + 2] - z0;
            int own = level[index(x, y, z, nx, nz)];
            for (Direction d : DIRS) {
                int to = index(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ(), nx, nz);
                // a face against a log (or the grid's edge) reads the block's own cell
                int l = damp[to] >= 15 ? own : level[to];
                out[i * 6 + d.ordinal()] = Math.max(FLOOR, 1 - STRENGTH * (1 - CURVE[l]));
            }
        }
        return out;
    }

    private static int index(int x, int y, int z, int nx, int nz) {
        return (y * nz + z) * nx + x;
    }
}
