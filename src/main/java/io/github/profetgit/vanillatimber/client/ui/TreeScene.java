package io.github.profetgit.vanillatimber.client.ui;

import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.vanillatimber.core.Timeline;
import io.github.profetgit.vanillatimber.server.Config;
import io.github.profetgit.vanillatimber.server.Config.Key;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

// What Timber's previews share: a small tree on the stage, chopped at its stump by the player's own axe, falling along
// Timber's own Timeline, then its logs hopping out as items with vanilla's item physics. Scenes read the values the
// screen edits (TimberSettings.Values), so a preview on a server shows that server's settings.
abstract class TreeScene extends Stage {
    // the stump's floor centre in the box (art is centred in a Diorama.W wide box)
    static final float STUMP_X = 122, STUMP_Y = 110;
    // mining a log with the axe: vanilla's crack stages over 0.6 s (copper axe on a log: 12 ticks)
    static final float MINE = 600;
    // the key row: clear above the off note's line
    static final int KEY_Y = 114;

    final Tool axe = Tool.axe();
    final Grove.Painter painter = new Grove.Painter();
    final Grove.Pose pose = new Grove.Pose();

    // a Quick setup's preview plays with that preset's values, not the live ones (null: live)
    java.util.Map<Key, Integer> fixed;

    TreeScene pin(java.util.Map<Key, Integer> values) {
        fixed = values;
        return this;
    }

    int get(Key k) {
        if (fixed != null) return fixed.getOrDefault(k, k.def);
        TimberSettings.Values v = TimberSettings.live;
        return v != null ? v.get(k) : Config.get(k);
    }

    boolean on(Key k) {
        return get(k) == 1;
    }

    float cx(int x, int w) {
        return x + (w - Diorama.W) / 2f + STUMP_X;
    }

    // the axe mining the stump from {@code start} for MINE ms; returns the crack stage (-1 none) at {@code time}
    int mine(Gfx g, float cx, float floor, float time, float start) {
        float end = start + MINE;
        g.pushTranslate(cx - K() * Diorama.AX, floor - K() * Diorama.AY);
        g.scale(K(), K());
        axe.draw(g, 0, 0, swing(time, start, end, true));
        g.pop();
        if (time < start || time >= end) return -1;
        return Math.min(9, (int) ((time - start) / MINE * 10));
    }

    static float K() {
        return Grove.K;
    }

    // the player's attack button, lit while it is held
    static void attack(Gfx g, Ctx c, int x, int y, int w, boolean pressed) {
        g.binding(Minecraft.getInstance().options.keyAttack, x + 46, y + KEY_Y, c.sz.fs, pressed);
    }

    // ---- the fall's leftovers ----

    // leaf chips at the slam: little squares of the leaf texture, thrown out of each leaf's posed spot, falling with
    // vanilla's particle gravity; {@code age} ticks after the leaves went
    static void leafBurst(Gfx g, float cx, float floor, Grove.Tree tree, Grove.Pose at, float age, boolean front) {
        leafBurst(g, cx, floor, tree, at, age, front, c -> false);
    }

    // {@code stays}: leaves that stay fallen (land_blocks) and never burst
    static void leafBurst(Gfx g, float cx, float floor, Grove.Tree tree, Grove.Pose at, float age, boolean front, java.util.function.Predicate<Grove.Cell> stays) {
        // the burst starts one tick before the leaves go, so no frame shows the bare trunk alone
        age += 1;
        if (age < 0 || age > 24) return;
        Random r = new Random(7);
        double[] q = new double[3];
        for (Grove.Cell cell : tree.cells) {
            if (!cell.leaf()) continue;
            // the random stream stays the same with or without kept leaves
            boolean skip = stays.test(cell);
            at.place(cell.x(), cell.y() + 0.5, cell.z(), cell.y(), true, q);
            for (int i = 0; i < 2; i++) {
                double vx = (r.nextDouble() - 0.5) * 0.25, vy = r.nextDouble() * 0.22 + 0.05, vz = (r.nextDouble() - 0.5) * 0.25;
                float life = 14 + r.nextInt(10);
                int u = r.nextInt(12), v = r.nextInt(12);
                if (age >= life || skip) continue;
                double x = q[0], y = q[1], z = q[2], dx = vx, dy = vy, dz = vz;
                for (int t = 0; t < (int) age; t++) {
                    dy -= 0.04;
                    x += dx;
                    y += dy;
                    z += dz;
                    dx *= 0.98;
                    dy *= 0.98;
                    dz *= 0.98;
                    if (y < 0) {
                        y = 0;
                        dx *= 0.7;
                        dz *= 0.7;
                    }
                }
                if ((Grove.depth(x, y, z) > Grove.depth(q[0], q[1], q[2])) != front) continue;
                float s = 3 * Math.min(1, (life - age) / 4f);
                if (s < 1) continue;
                g.pushTranslate(Grove.sx(cx, x, z) - s / 2, Grove.sy(floor, x, y, z) - s / 2);
                g.scale(s / 4, s / 4);
                g.blit(tree.wood.leaf(), 0, 0, u, v, 4, 4, 4, 4, 16, 16, Grove.mul(0xFFE8E8E8, tree.wood.foliage()));
                g.pop();
            }
        }
    }

    // the poof style's cloud: Timber's own puff sprites along the lying tree, {@code age} ticks after the POOF
    static void poof(Gfx g, float cx, float floor, Grove.Tree tree, Grove.Pose at, float age) {
        if (age < 0 || age >= 16) return;
        double[] q = new double[3];
        Random r = new Random(3);
        for (int k = 0; k < tree.h; k++) {
            for (int j = 0; j < 2; j++) {
                at.place(0, k + 0.5, 0, k, false, q);
                double ox = (r.nextDouble() - 0.5) * 1.2, oz = (r.nextDouble() - 0.5) * 2.2, rise = age * 0.02 + r.nextDouble() * 0.3;
                int frame = Math.min(15, (int) (age + r.nextInt(2)));
                float s = (float) ((k >= tree.rs ? 30 : 20) * (0.8 + 0.4 * r.nextDouble()));
                float px = Grove.sx(cx, q[0] + ox, q[2] + oz), py = Grove.sy(floor, q[0] + ox, q[1] + rise, q[2] + oz);
                g.pushTranslate(px - s / 2, py - s / 2);
                g.scale(s / 16, s / 16);
                g.blit(Identifier.tryParse("vanilla_timber:textures/particle/poof_" + frame + ".png"), 0, 0, 0, 0, 16, 16, 16, 16, 16, 16, 0xFFFFFFFF);
                g.pop();
            }
        }
    }

    // a dropped log: vanilla's item hop from where its ring popped, then the bob; {@code t} ticks after it appeared
    void logItem(Grove.Tree tree, float cx, float floor, double x, double y, double z, double vx, double vy, double t, int seed) {
        double[] f = fall(0, y, vx, vy, t);
        double bob = f[2] >= 0 ? bob(f[2]) : 0;
        double spin = (t + seed * 37) / 20.0 * 2 * Math.PI / 10;
        Grove.droppedBlock(painter, cx, floor, tree.wood.side(), tree.wood.end(), 0xFFFFFFFF, x + f[0], f[1] + bob, z, spin);
    }

    // the chopped log: vanilla breaks it, its item hops out of the stump cell; {@code tau} ticks after the break
    void cutLog(Grove.Tree tree, float cx, float floor, double tau) {
        if (tau >= 0) logItem(tree, cx, floor, 0.1, 0.3, 0.05, 0.04, 0.2, tau, 9);
    }

    // where ring k's log lies once the tree has landed (its posed centre at the end of the fall)
    double[] landed(Grove.Tree tree, Timeline tl, int k) {
        Grove.Pose p = new Grove.Pose().at(tl, tree, Math.max(0, tl.landTick()));
        double[] q = new double[3];
        p.place(0, k + 0.5, 0, k, false, q);
        return q;
    }

    // the plain classic chop: the axe mines the stump from {@code start}, the tree falls when the log breaks, its logs pop
    // into items and its leaves burst. Everything but the tool and the keys is painted (call painter.flush after adding more).
    void chopAndFall(Gfx g, Grove.Tree tree, Timeline tl, float cx, float floor, float time, float start) {
        float broke = start + MINE;
        double tau = (time - broke) / MS;
        Grove.Look look = new Grove.Look();
        if (time < broke) {
            look.crack = time >= start ? Math.min(9, (int) ((time - start) / MINE * 10)) : -1;
            Grove.shadows(g, cx, floor, tree, null, look);
            Grove.standing(g, painter, cx, floor, tree, look);
            return;
        }
        if (tau < tl.end) {
            Grove.Pose at = pose.at(tl, tree, tau);
            Grove.shadows(g, cx, floor, tree, at, look);
            Grove.draw(g, painter, cx, floor, tree, at, look);
        }
        cutLog(tree, cx, floor, tau);
        double[] q = new double[3];
        for (int k = 0; k < tree.h; k++) {
            if (!tree.logRings[k] || tl.popTick[k] < 0) continue;
            double born = tl.popTick[k] - 1 + 4;
            if (tau < born) continue;
            new Grove.Pose().at(tl, tree, born).place(0, k + 0.5, 0, k, false, q);
            logItem(tree, cx, floor, q[0], Math.max(0, q[1] - 0.125), q[2], 0.02 * (k % 2 == 0 ? 1 : -1), 0.2, tau - born, k);
        }
    }

    // the leaf burst after the slam, drawn over everything
    void burst(Gfx g, Grove.Tree tree, Timeline tl, float cx, float floor, float time, float broke) {
        double tau = (time - broke) / MS;
        if (time >= broke && tl.leavesGone >= 0) leafBurst(g, cx, floor, tree, new Grove.Pose().at(tl, tree, tl.leavesGone - 1), (float) (tau - (tl.leavesGone - 1)), true);
    }
}
