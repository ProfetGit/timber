package io.github.profetgit.vanillatimber.client.ui;

import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.vanillatimber.core.FallSpec;
import io.github.profetgit.vanillatimber.core.Landing;
import io.github.profetgit.vanillatimber.core.Rig;
import io.github.profetgit.vanillatimber.core.Timeline;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

// The preview's tree: real block textures on cubes, posed every frame by Timber's own fall (Timeline, Rig) exactly as the
// mesh in the world is, and drawn with the stage's camera (Diorama: +x to the lower right, +z to the lower left) at half
// the stage's size, so a whole tree and its fall fit the box. Block space: 1 = a block, the stump's floor centre is the
// origin. Trees fall toward -x (up and to the left, away from the player, who chops the stump's east face from the right).
final class Grove {
    private Grove() {}

    // the stage's size: a block is about 22 GUI px wide, so a whole oak and its fall fit the box
    static final float K = 0.34f;
    // the light of the stage (Tool): from the upper front, warm, with a cool shade
    private static final float[] LIGHT = {0.239f, 0.813f, 0.526f};

    static float sx(float cx, double x, double z) {
        return (float) (cx + K * (x - z) * Diorama.HX);
    }

    static float sy(float floor, double x, double y, double z) {
        return (float) (floor + K * ((x + z) * Diorama.HZ - y * Diorama.TALL));
    }

    // nearness to the camera
    static double depth(double x, double y, double z) {
        return x + 0.8 * y + z;
    }

    // ---- trees ----

    // the wood a tree is made of: log side, log end, leaves and their colour (vanilla's foliage colours)
    record Wood(String log, String leaves, int foliage, String sapling) {
        Identifier side() {
            return Stage.tex(log);
        }

        Identifier end() {
            return Stage.tex(log + "_top");
        }

        Identifier leaf() {
            return Stage.tex(leaves);
        }

        // the core chop mode leaves standing (ChopModels: the stripped log's own faces)
        Identifier strippedSide() {
            return Stage.tex("stripped_" + log);
        }

        Identifier strippedEnd() {
            return Stage.tex("stripped_" + log + "_top");
        }
    }

    static final Wood OAK = new Wood("oak_log", "oak_leaves", 0xFF59AE30, "oak_sapling");
    static final Wood BIRCH = new Wood("birch_log", "birch_leaves", 0xFF80A755, "birch_sapling");

    // one block of a tree, grid cell (x, y, z) from the stump
    record Cell(int x, int y, int z, boolean leaf) {}

    static final class Tree {
        final Wood wood;
        final List<Cell> cells = new ArrayList<>();
        // logs a player placed (drawn in spruce, as a post they built against the tree)
        final List<Cell> placed = new ArrayList<>();
        final int h, km, rs;
        final boolean[] logRings;
        final FallSpec spec;

        // a pile of blocks already in place (what stays fallen): shade only, no fall
        Tree(Wood wood, List<Cell> pile) {
            this.wood = wood;
            cells.addAll(pile);
            h = 1;
            km = 0;
            rs = 0;
            logRings = new boolean[1];
            spec = null;
        }

        // a small oak: {@code logs} logs, a crown of leaves from ring {@code logs - 2} up to one ring over the top log
        Tree(Wood wood, int logs) {
            this.wood = wood;
            for (int y = 0; y < logs; y++) cells.add(new Cell(0, y, 0, false));
            int top = logs;
            for (int y = logs - 2; y <= top; y++) {
                int r = y >= top ? 1 : 2;
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        if (x == 0 && z == 0 && y < logs) continue;
                        if (r == 2 && Math.abs(x) == 2 && Math.abs(z) == 2) continue;
                        if (r == 1 && Math.abs(x) == 1 && Math.abs(z) == 1) continue;
                        cells.add(new Cell(x, y, z, true));
                    }
                }
            }
            h = top + 1;
            km = logs - 1;
            rs = logs - 2;
            logRings = new boolean[h];
            // ring 0 is the cut: the chopped log breaks as in vanilla, so the tree has an open cell under it and drops
            // into it before it leans (Timeline's plunge, gap 1), exactly as the mod's fall does
            for (int k = 1; k < logs; k++) logRings[k] = true;
            // the stage's ground is flat: the tree lands lying (90 degrees), one open cell under the cut, no hang
            spec = FallSpec.sized(h, 90, 1, false, 0, km, rs);
        }

        private java.util.Set<Long> filled;

        private java.util.Set<Long> logs;

        boolean has(int x, int y, int z, boolean leaves) {
            if (filled == null) {
                filled = new java.util.HashSet<>();
                logs = new java.util.HashSet<>();
                for (Cell c : cells) {
                    filled.add(key(c.x(), c.y(), c.z()));
                    if (!c.leaf()) logs.add(key(c.x(), c.y(), c.z()));
                }
            }
            return (leaves ? filled : logs).contains(key(x, y, z));
        }

        private static long key(int x, int y, int z) {
            return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
        }

        // light on each face of a cell {top, bottom, +x, -x, +z, -z}, like the game's smooth lighting: a face against
        // another block of the tree is in its shade, and every block above the cell takes some sky light away
        float[] shade(Cell c, boolean leaves) {
            int above = 0;
            // only the crown above takes sky light away; the trunk's own logs above a log don't
            for (int y = c.y() + 1; y <= c.y() + 4; y++) if (has(c.x(), y, c.z(), leaves) && !has(c.x(), y, c.z(), false)) above++;
            float sky = (float) Math.pow(0.7, above);
            // a log is lit from the sides under the crown: shaded a little, never near black
            if (!c.leaf()) sky = Math.max(0.8f, sky);
            // the crown's own depth: leaves near the trunk are deeper inside it
            float deep = c.leaf() ? 1 - 0.07f * Math.max(0, 2 - (Math.abs(c.x()) + Math.abs(c.z()))) : 1;
            int[][] dir = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
            float[] out = new float[6];
            for (int i = 0; i < 6; i++) {
                boolean shut = has(c.x() + dir[i][0], c.y() + dir[i][1], c.z() + dir[i][2], leaves);
                // open neighbours count too: a face next to an occupied diagonal corner gets a little darker
                int corners = 0;
                for (int a = -1; a <= 1; a += 2) {
                    for (int b = -1; b <= 1; b += 2) {
                        int dx = dir[i][0], dy = dir[i][1], dz = dir[i][2];
                        int nx = c.x() + dx + (dx == 0 ? a : 0), ny = c.y() + dy + (dy == 0 ? (dx == 0 ? b : a) : 0), nz = c.z() + dz + (dz == 0 ? b : 0);
                        if (has(nx, ny, nz, leaves)) corners++;
                    }
                }
                float f = (shut ? 0.5f : 1f) * (1 - (i == 0 ? 0.025f : 0.06f) * corners);
                out[i] = f * (i == 0 ? Math.max(sky, shut ? 0 : 0.85f) : i == 1 ? 0.45f : sky) * deep;
            }
            return out;
        }

        Timeline timeline(boolean poof, int landPct) {
            return new Timeline(spec, logRings, Landing.rings(logRings, landPct), poof);
        }
    }

    // ---- posing ----

    // the pivot: the trunk's floor edge on the fall side (Layout.pivotX: the footprint centre moved half a block toward the fall)
    static final double PIVOT_X = -0.5;

    // a pose of a whole tree at one drawing time: what Rig needs, computed once per frame
    static final class Pose {
        final Timeline.Sample s = new Timeline.Sample();
        double[] oy, oz;
        final float[] pop = new float[2];
        final double[] out = new double[3];
        Timeline tl;
        int kt;

        Pose at(Timeline tl, Tree tree, double tau) {
            this.tl = tl;
            tl.sample(tau, s);
            kt = tree.h - 1;
            if (oy == null || oy.length != tree.h) {
                oy = new double[tree.h];
                oz = new double[tree.h];
            }
            Rig.origins(s.pitch, s.bend, s.gy, tree.spec.h(), 0, kt, oy, oz);
            return this;
        }

        // a rest point (block space) of a block of ring k to its posed point; tree frame: x across (= +z), z along the fall (= -x)
        void place(double x, double y, double z, int k, boolean leaf, double[] p) {
            double tx = z, wy = y, tz = PIVOT_X - x;
            Rig.corner(tx, wy, tz, k, leaf ? s.lx : s.gx, s.gy, leaf ? s.lz : s.gz, s.pitch, oy[Math.min(k, kt)], oz[Math.min(k, kt)], out);
            p[0] = PIVOT_X - out[2];
            p[1] = out[1] + s.dy;
            p[2] = out[0];
        }
    }

    // ---- the painter: faces of every cube, far to near ----

    // a region (u, v, w, h) of a tw x th texture on a parallelogram
    record Face(double depth, Matrix3x2f pose, Identifier tex, int tint, Identifier crack, int[] uv) {}

    static final int[] FULL = {0, 0, 16, 16, 16, 16};

    static final class Painter {
        final List<Face> faces = new ArrayList<>();
        private final double[][] c = new double[8][3];

        // an axis-aligned box (rest block space) moved by {@code place}; each face gets the texture its rest side has
        interface Placer {
            void place(double x, double y, double z, double[] out);
        }

        void cube(float cx, float floor, double x0, double y0, double z0, double x1, double y1, double z1, Placer pl,
            Identifier side, Identifier end, int tint, Identifier crack) {
            cube(cx, floor, x0, y0, z0, x1, y1, z1, pl, side, end, tint, crack, null);
        }

        // {@code shade}: a light factor per rest face {top, bottom, +x, -x, +z, -z} (null: all lit)
        void cube(float cx, float floor, double x0, double y0, double z0, double x1, double y1, double z1, Placer pl,
            Identifier side, Identifier end, int tint, Identifier crack, float[] shade) {
            for (int i = 0; i < 8; i++) pl.place((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1, c[i]);
            // corners: bit 0 x, bit 1 y, bit 2 z. Each face: origin, along u, along v (v runs down the side)
            face(cx, floor, 3, 7, 6, 2, end, dim(tint, shade, 0), crack, FULL);   // top (y1): from (x0,z0) to x1 / to z1
            face(cx, floor, 4, 5, 1, 0, end, dim(tint, shade, 1), crack, FULL);   // bottom
            face(cx, floor, 7, 3, 1, 5, side, dim(tint, shade, 2), crack, FULL);  // +x
            face(cx, floor, 2, 6, 4, 0, side, dim(tint, shade, 3), crack, FULL);  // -x
            face(cx, floor, 6, 7, 5, 4, side, dim(tint, shade, 4), crack, FULL);  // +z
            face(cx, floor, 3, 2, 0, 1, side, dim(tint, shade, 5), crack, FULL);  // -z
        }

        private static int dim(int tint, float[] shade, int i) {
            if (shade == null) return tint;
            int v = Math.round(255 * Math.max(0, Math.min(1, shade[i])));
            return mul(tint, 0xFF000000 | v << 16 | v << 8 | v);
        }

        // a box with its own texture region per face: {top, +x, +z, -x, -z, bottom}, each {u, v, w, h}, of a tw x th texture
        void skinned(float cx, float floor, double x0, double y0, double z0, double x1, double y1, double z1, Placer pl,
            Identifier tex, int tw, int th, int[][] regions, int tint) {
            for (int i = 0; i < 8; i++) pl.place((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1, c[i]);
            int[][] q = new int[6][];
            for (int i = 0; i < 6; i++) q[i] = new int[] {regions[i][0], regions[i][1], regions[i][2], regions[i][3], tw, th};
            face(cx, floor, 2, 3, 7, 6, tex, tint, null, q[0]);
            face(cx, floor, 7, 3, 1, 5, tex, tint, null, q[1]);
            face(cx, floor, 6, 7, 5, 4, tex, tint, null, q[2]);
            face(cx, floor, 2, 6, 4, 0, tex, tint, null, q[3]);
            face(cx, floor, 3, 2, 0, 1, tex, tint, null, q[4]);
            face(cx, floor, 4, 5, 1, 0, tex, tint, null, q[5]);
        }

        // face with corners o (texture 0,0), a (16,0), b (16,16), d (0,16)
        private void face(float cx, float floor, int o, int a, int b, int d, Identifier tex, int tint, Identifier crack, int[] uv) {
            double[] po = c[o], pa = c[a], pd = c[d], pb = c[b];
            double ux = pa[0] - po[0], uy = pa[1] - po[1], uz = pa[2] - po[2];
            double vx = pd[0] - po[0], vy = pd[1] - po[1], vz = pd[2] - po[2];
            // outward normal: the face centre away from the box centre
            double mx = 0, my = 0, mz = 0;
            for (double[] q : c) {
                mx += q[0] / 8;
                my += q[1] / 8;
                mz += q[2] / 8;
            }
            double fx = (po[0] + pb[0]) / 2, fy = (po[1] + pb[1]) / 2, fz = (po[2] + pb[2]) / 2;
            double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            if (nx * (fx - mx) + ny * (fy - my) + nz * (fz - mz) < 0) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-9) return;
            nx /= len;
            ny /= len;
            nz /= len;
            if (nx + 0.8 * ny + nz <= 0.02) return;
            float ox = sx(cx, po[0], po[2]), oy = sy(floor, po[0], po[1], po[2]);
            float ax = sx(cx, pa[0], pa[2]) - ox, ay = sy(floor, pa[0], pa[1], pa[2]) - oy;
            float bx = sx(cx, pd[0], pd[2]) - ox, by = sy(floor, pd[0], pd[1], pd[2]) - oy;
            float lit = (float) Math.max(0, Math.min(1, (nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]) * 1.15));
            int shade = light(lit);
            faces.add(new Face(depth(fx, fy, fz), new Matrix3x2f(ax / uv[2], ay / uv[2], bx / uv[3], by / uv[3], ox, oy), tex, mul(shade, tint), crack, uv));
        }

        // a flat textured quad standing in the world: corners o (0,0), a (16,0), d (0,16); drawn from either side
        void quad(float cx, float floor, double[] o, double[] a, double[] d, Identifier tex, int tint) {
            float ox = sx(cx, o[0], o[2]), oy = sy(floor, o[0], o[1], o[2]);
            float ax = sx(cx, a[0], a[2]) - ox, ay = sy(floor, a[0], a[1], a[2]) - oy;
            float bx = sx(cx, d[0], d[2]) - ox, by = sy(floor, d[0], d[1], d[2]) - oy;
            double mx = (a[0] + d[0]) / 2, my = (a[1] + d[1]) / 2, mz = (a[2] + d[2]) / 2;
            faces.add(new Face(depth(mx, my, mz), new Matrix3x2f(ax / 16, ay / 16, bx / 16, by / 16, ox, oy), tex, tint, null, FULL));
        }

        void flush(Gfx g) {
            faces.sort(Comparator.comparingDouble(Face::depth));
            for (Face f : faces) {
                blit(g, f.pose(), f.tex(), f.tint(), f.uv());
                if (f.crack() != null) blit(g, f.pose(), f.crack(), 0x70000000, FULL);
            }
            faces.clear();
        }
    }

    // a 16 x 16 texture onto a parallelogram; the GUI pipeline drops mirrored quads, so those go from the far end reversed
    static void blit(Gfx g, Matrix3x2f pose, Identifier tex, int tint, int[] uv) {
        int w = uv[2], h = uv[3];
        g.push();
        g.mul(pose);
        boolean mirrored = pose.determinant() < 0;
        if (mirrored) {
            g.translate(w, 0);
            g.scale(-1, 1);
        }
        g.blit(tex, 0, 0, mirrored ? uv[0] + w : uv[0], uv[1], w, h, mirrored ? -w : w, h, uv[4], uv[5], tint);
        g.pop();
    }

    static int light(float lit) {
        int r = channel(0.62f, 1.08f, lit), gr = channel(0.58f, 1.04f, lit), b = channel(0.68f, 0.98f, lit);
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    private static int channel(float shade, float light, float lit) {
        return Math.round(255 * Math.min(1, shade + (light - shade) * lit));
    }

    static int mul(int a, int b) {
        int r = ((a >> 16) & 255) * ((b >> 16) & 255) / 255, gg = ((a >> 8) & 255) * ((b >> 8) & 255) / 255, bl = (a & 255) * (b & 255) / 255;
        int al = ((a >>> 24) * (b >>> 24)) / 255;
        return al << 24 | r << 16 | gg << 8 | bl;
    }

    // ---- drawing a tree ----

    // what to draw of a tree at one moment
    static final class Look {
        boolean leaves = true, logs = true;
        // the trunk's lowest log, cut by chop mode: its box in sixteenths (Cut) and crack stage (-1: none)
        int cutBox = io.github.profetgit.vanillatimber.core.Cut.WHOLE, crack = -1;
        // the stump's log is gone (vanilla's break of the chopped block)
        boolean stumpGone;
        // how many of the tree's placed logs are there yet
        int placedShown = Integer.MAX_VALUE;
        // protected: the placed logs stay where they were put while the tree falls; onlyPlaced: nothing else is left
        boolean placedStay, onlyPlaced;
        float scale = 1;
        // land_blocks: the parts that stay fallen (never burst or pop), and each one's light once it lies as a block
        java.util.function.Predicate<Cell> kept = c -> false;
        java.util.function.Function<Cell, float[]> lying = c -> null;
    }

    // the whole tree standing, untouched
    static void standing(Gfx g, Painter p, float cx, float floor, Tree tree, Look look) {
        draw(g, p, cx, floor, tree, null, look);
    }

    // the tree at a pose (null: standing); landed rings past their land tick are left to the caller (lying blocks)
    static void draw(Gfx g, Painter p, float cx, float floor, Tree tree, Pose posed, Look look) {
        for (Cell cell : tree.cells) {
            if (cell.leaf() && !look.leaves || !cell.leaf() && !look.logs) continue;
            if (!cell.leaf() && cell.y() == 0 && look.stumpGone && cell.x() == 0 && cell.z() == 0) continue;
            int placedAt = tree.placed.indexOf(cell);
            if (placedAt >= look.placedShown || placedAt < 0 && look.onlyPlaced) continue;
            Pose pose = placedAt >= 0 && look.placedStay ? null : posed;
            Wood wood = tree.wood;
            int k = cell.y();
            double sxz = 1, sy = 1;
            if (pose != null) {
                // the chopped log is gone once the tree moves
                if (!cell.leaf() && k == 0 && cell.x() == 0 && cell.z() == 0) continue;
                if (cell.leaf() && !pose.s.leaves && !look.kept.test(cell)) continue;
                if (!cell.leaf()) {
                    float[] pp = pose.tl.pop(k, pose.s.tau, pose.pop);
                    if (pp != null) {
                        if (pp[0] <= 0.001f) continue;
                        sxz = pp[0];
                        sy = pp[1];
                    }
                }
            }
            double x0 = cell.x() - 0.5, z0 = cell.z() - 0.5, y0 = cell.y();
            double bx0 = x0, bx1 = x0 + 1, by0 = y0, by1 = y0 + 1, bz0 = z0, bz1 = z0 + 1;
            boolean cut = !cell.leaf() && k == 0 && look.cutBox != io.github.profetgit.vanillatimber.core.Cut.WHOLE;
            if (cut) {
                int box = look.cutBox;
                if (io.github.profetgit.vanillatimber.core.Cut.empty(box)) continue;
                bx0 = x0 + io.github.profetgit.vanillatimber.core.Cut.lo(box, 0) / 16.0;
                bx1 = x0 + io.github.profetgit.vanillatimber.core.Cut.hi(box, 0) / 16.0;
                bz0 = z0 + io.github.profetgit.vanillatimber.core.Cut.lo(box, 2) / 16.0;
                bz1 = z0 + io.github.profetgit.vanillatimber.core.Cut.hi(box, 2) / 16.0;
            }
            if (sxz != 1 || sy != 1) {
                double mx = (bx0 + bx1) / 2, my = (by0 + by1) / 2, mz = (bz0 + bz1) / 2;
                bx0 = mx + (bx0 - mx) * sxz;
                bx1 = mx + (bx1 - mx) * sxz;
                bz0 = mz + (bz0 - mz) * sxz;
                bz1 = mz + (bz1 - mz) * sxz;
                by0 = my + (by0 - my) * sy;
                by1 = my + (by1 - my) * sy;
            }
            if (look.scale != 1) {
                float s = look.scale;
                bx0 *= s;
                bx1 *= s;
                by0 *= s;
                by1 *= s;
                bz0 *= s;
                bz1 *= s;
            }
            // the stage has no floor: a leaf sunk into the ground (the crown's far side at the slam) is not drawn
            if (pose != null && cell.leaf()) {
                double[] c = new double[3];
                pose.place(cell.x(), cell.y() + 0.5, cell.z(), k, true, c);
                if (c[1] < 0) continue;
            }
            Painter.Placer pl = pose == null ? (x, y, z, out) -> {
                out[0] = x;
                out[1] = y;
                out[2] = z;
            } : (x, y, z, out) -> pose.place(x, y, z, k, cell.leaf(), out);
            // a placed log lies on its side along z: turned about its own centre, then posed like the rest
            if (placedAt >= 0) {
                Painter.Placer upright = pl;
                double mid = cell.y() + 0.5, along = cell.z();
                pl = (x, y, z, out) -> upright.place(x, mid - (z - along), along + (y - mid), out);
            }
            Identifier crack = !cell.leaf() && k == 0 && look.crack >= 0 ? Stage.tex("destroy_stage_" + Math.min(9, look.crack)) : null;
            // the tree shades itself; it falls in one piece, so the shade travels with it
            // a log the player placed lies in the open: lit, so it reads against the trunk
            float[] shade = placedAt >= 0 ? null : tree.shade(cell, true);
            if (shade != null && pose != null && !pose.s.leaves) {
                // the crown has gone: the trunk comes out of its shade over 4 ticks, not in one frame
                float[] open = look.lying.apply(cell);
                if (open == null) open = tree.shade(cell, false);
                float f = (float) Math.max(0, Math.min(1, (pose.s.tau - (pose.tl.leavesGone - 1)) / 4));
                for (int i = 0; i < 6; i++) shade[i] += (open[i] - shade[i]) * f;
            }
            // a log's ends only show once its neighbour is gone (the chop, then the pops), so they are lit as open ends
            if (shade != null && pose != null && !cell.leaf()) {
                for (int i = 0; i < 2; i++) shade[i] = Math.max(shade[i], 0.85f);
            }
            if (cell.leaf()) p.cube(cx, floor, bx0, by0, bz0, bx1, by1, bz1, pl, tree.wood.leaf(), tree.wood.leaf(), tree.wood.foliage(), null, shade);
            else if (cut) p.cube(cx, floor, bx0, by0, bz0, bx1, by1, bz1, pl, wood.strippedSide(), wood.strippedEnd(), 0xFFFFFFFF, crack, shade);
            else p.cube(cx, floor, bx0, by0, bz0, bx1, by1, bz1, pl, wood.side(), wood.end(), 0xFFFFFFFF, cell.x() == 0 && cell.z() == 0 ? crack : null, shade);
        }
    }

    // a log block lying on the ground along x (a landed ring, or a log that rests where it fell)
    static void lyingLog(Painter p, float cx, float floor, Wood wood, double x, double z) {
        lyingLog(p, cx, floor, wood, x, z, null);
    }

    // {@code shade} in world faces {top, bottom, +x, -x, +z, -z}; turned onto the log's own faces here
    static void lyingLog(Painter p, float cx, float floor, Wood wood, double x, double z, float[] shade) {
        float[] rest = shade == null ? null : new float[] {shade[2], shade[3], shade[1], shade[0], shade[4], shade[5]};
        p.cube(cx, floor, x - 0.5, 0, z - 0.5, x + 0.5, 1, z + 0.5, (px, py, pz, out) -> {
            // turned on its side: the log's ends face along x
            out[0] = x + (py - 0.5);
            out[1] = 0.5 - (px - x);
            out[2] = pz;
        }, wood.side(), wood.end(), 0xFFFFFFFF, null, rest);
    }

    // a leaf block resting where it landed
    static void restingLeaf(Painter p, float cx, float floor, Wood wood, double x, double y, double z) {
        restingLeaf(p, cx, floor, wood, x, y, z, null);
    }

    static void restingLeaf(Painter p, float cx, float floor, Wood wood, double x, double y, double z, float[] shade) {
        p.cube(cx, floor, x - 0.5, y, z - 0.5, x + 0.5, y + 1, z + 0.5, (px, py, pz, out) -> {
            out[0] = px;
            out[1] = py;
            out[2] = pz;
        }, wood.leaf(), wood.leaf(), wood.foliage(), null, shade);
    }

    // a dropped block item: a quarter-size cube, spinning like vanilla's (one turn in 10 s at 20 ticks), bobbing above
    // the ground at (x, z) with its bottom {@code y} blocks up
    static void droppedBlock(Painter p, float cx, float floor, Identifier side, Identifier end, int tint, double x, double y, double z, double spin) {
        double c = Math.cos(spin), s = Math.sin(spin), h = 0.125;
        p.cube(cx, floor, -h, 0, -h, h, 2 * h, h, (px, py, pz, out) -> {
            out[0] = x + px * c - pz * s;
            out[1] = y + py;
            out[2] = z + px * s + pz * c;
        }, side, end, tint, null);
    }

    // a plant as the game draws it (the cross model): two diagonal quads through the cell, {@code grow} 0..1 its size
    static void plant(Painter p, float cx, float floor, Identifier tex, double x, double z, double grow) {
        if (grow <= 0.01) return;
        double r = 0.45 * grow, hgt = grow;
        p.quad(cx, floor, new double[] {x - r, hgt, z - r}, new double[] {x + r, hgt, z + r}, new double[] {x - r, 0, z - r}, tex, light(0.85f));
        p.quad(cx, floor, new double[] {x - r, hgt, z + r}, new double[] {x + r, hgt, z - r}, new double[] {x - r, 0, z + r}, tex, light(0.7f));
    }

    // ---- shadows ----

    // the contact shadow of the standing or posed tree: every block near the ground shades the floor under it
    static void shadows(Gfx g, float cx, float floor, Tree tree, Pose posed, Look look) {
        double[] q = new double[3];
        for (Cell cell : tree.cells) {
            int placedAt = tree.placed.indexOf(cell);
            if (placedAt < 0 && look.onlyPlaced) continue;
            Pose pose = placedAt >= 0 && look.placedStay ? null : posed;
            if (cell.leaf() && !look.leaves) continue;
            if (!cell.leaf() && cell.y() == 0 && look.stumpGone && cell.x() == 0 && cell.z() == 0) continue;
            if (placedAt >= look.placedShown) continue;
            if (pose != null && cell.leaf() && !pose.s.leaves && !look.kept.test(cell)) continue;
            if (pose != null && !cell.leaf() && cell.y() == 0 && cell.x() == 0 && cell.z() == 0) continue;
            double x = cell.x(), y = cell.y() + 0.5, z = cell.z();
            if (pose != null) {
                if (!cell.leaf()) {
                    float[] pp = pose.tl.pop(cell.y(), pose.s.tau, pose.pop);
                    if (pp != null && pp[0] <= 0.001f) continue;
                }
                pose.place(x, y, z, cell.y(), cell.leaf(), q);
                x = q[0];
                y = q[1];
                z = q[2];
            }
            double above = y - 0.5;
            if (above > 2.5) continue;
            float size = cell.leaf() ? 0.8f : 1f;
            shadow(g, sx(cx, x, z), sy(floor, x, 0, z), size, above);
        }
    }

    // the stage's block shadow at the half size, fading as its owner rises
    static void shadow(Gfx g, float x, float y, float size, double height) {
        float a = g.alpha;
        g.alpha *= (float) Math.max(0, Math.min(1, 1 - height * 0.4)) * 0.6f;
        g.pushTranslate(x, y);
        g.scale(K, K);
        Diorama.shadow(g, 0, 0, size);
        g.pop();
        g.alpha = a;
    }
}
