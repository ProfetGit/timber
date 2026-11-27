package io.github.profetgit.vanillatimber.client.ui;

import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.vanillatimber.core.Timeline;
import io.github.profetgit.vanillatimber.server.Config.Key;
import java.util.Random;

// One chop and the whole tree comes down: the axe mines the stump, the tree falls along Timber's own timeline (lean,
// fall, slam, bounce), its leaves burst and its logs pop one ring at a time into items. The same scene answers the fall
// options: style (classic or poof), the animation switch, where drops go, how much stays fallen.
final class FallScene extends TreeScene {
    enum Topic { FALL, DROPS, LAND, REPLANT }

    // ms: the axe starts mining, the log breaks; after the fall the items lie this long, then the stage clears
    private static final float START = 350, BREAK = START + MINE, REST = 1500, CLEAR = 300;

    private final Topic topic;
    private final Grove.Tree tree;
    private Timeline tl;
    private int tlKey = Integer.MIN_VALUE;

    FallScene(Topic topic) {
        this.topic = topic;
        // what stays fallen needs a trunk long enough to lie as a row of logs beyond the crown
        tree = new Grove.Tree(Grove.OAK, topic == Topic.LAND ? 6 : 4);
    }

    private Timeline timeline(boolean poof, int land) {
        int key = (poof ? 1000 : 0) + land;
        if (tl == null || key != tlKey) {
            tl = tree.timeline(poof, land);
            tlKey = key;
            keep(tl);
        }
        return tl;
    }

    // land_blocks: the cells that stay fallen, and each one's light as a block in the lying pile (rest faces)
    private final java.util.Map<Grove.Cell, float[]> kept = new java.util.HashMap<>();

    // Fall's rule: the logs of a landed ring, and the leaves within 1 of one whose resting cell is above the ground; the
    // rest pose is the fall's last frame, the same one the game turns into blocks
    private void keep(Timeline tl) {
        kept.clear();
        if (tl.landTick() < 0) return;
        Grove.Pose end = new Grove.Pose().at(tl, tree, tl.end);
        java.util.Map<Grove.Cell, Grove.Cell> world = new java.util.LinkedHashMap<>();
        double[] q = new double[3];
        for (Grove.Cell cell : tree.cells) {
            if (cell.leaf() || !tl.lands(cell.y())) continue;
            world.put(cell, cellAt(end, cell, q));
        }
        java.util.Set<Long> taken = new java.util.HashSet<>();
        for (Grove.Cell w : world.values()) taken.add(key(w.x(), w.y(), w.z()));
        for (Grove.Cell cell : tree.cells) {
            if (!cell.leaf()) continue;
            boolean near = false;
            for (Grove.Cell log : world.keySet()) near |= Math.abs(log.x() - cell.x()) <= 1 && Math.abs(log.y() - cell.y()) <= 1 && Math.abs(log.z() - cell.z()) <= 1;
            if (!near) continue;
            Grove.Cell w = cellAt(end, cell, q);
            if (w.y() < 0 || !taken.add(key(w.x(), w.y(), w.z()))) continue;
            world.put(cell, w);
        }
        Grove.Tree pile = new Grove.Tree(tree.wood, new java.util.ArrayList<>(world.values()));
        for (var e : world.entrySet()) kept.put(e.getKey(), restFaces(end, e.getKey(), pile.shade(e.getValue(), true)));
    }

    private Grove.Cell cellAt(Grove.Pose pose, Grove.Cell cell, double[] q) {
        pose.place(cell.x(), cell.y() + 0.5, cell.z(), cell.y(), cell.leaf(), q);
        return new Grove.Cell((int) Math.round(q[0]), (int) Math.floor(q[1] + 1e-6), (int) Math.round(q[2]), cell.leaf());
    }

    // world-face light {top, bottom, +x, -x, +z, -z} onto the rest faces that face those ways at the pose
    private float[] restFaces(Grove.Pose pose, Grove.Cell cell, float[] world) {
        double[][] n = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        double[] a = new double[3], b = new double[3];
        float[] out = new float[6];
        pose.place(cell.x(), cell.y() + 0.5, cell.z(), cell.y(), cell.leaf(), a);
        for (int i = 0; i < 6; i++) {
            pose.place(cell.x() + n[i][0] * 0.5, cell.y() + 0.5 + n[i][1] * 0.5, cell.z() + n[i][2] * 0.5, cell.y(), cell.leaf(), b);
            double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
            int j = Math.abs(dy) >= Math.abs(dx) && Math.abs(dy) >= Math.abs(dz) ? (dy > 0 ? 0 : 1) : Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? 2 : 3) : (dz > 0 ? 4 : 5);
            out[i] = world[j];
        }
        return out;
    }

    @Override
    public void render(Gfx g, Ctx c, int x, int y, int w, int h) {
        boolean replant = on(Key.REPLANT), anim = on(Key.ANIMATION), poof = anim && get(Key.STYLE) == 1, atPlayer = get(Key.DROPS) == 1;
        int land = anim && !poof ? get(Key.LAND_BLOCKS) : 0;
        Timeline tl = timeline(poof, land);
        float fallMs = anim ? tl.end * MS : 0;
        float period = BREAK + fallMs + REST + CLEAR;
        float time = time(c, period, anim, poof, atPlayer, land, replant);
        float cx = cx(x, w), floor = y + STUMP_Y;
        Diorama.backdrop(g, x + (w - Diorama.W) / 2, y);
        double tau = (time - BREAK) / MS;

        Grove.Look look = new Grove.Look();
        if (!kept.isEmpty()) {
            look.kept = kept::containsKey;
            look.lying = kept::get;
        }
        boolean fell = time >= BREAK;
        Grove.Pose at = fell && anim ? pose.at(tl, tree, tau) : null;

        // shadows first, under everything
        // what stays fallen keeps its pose (the fall's last frame) after the fall has ended
        boolean lies = anim && !kept.isEmpty();
        if (!fell) Grove.shadows(g, cx, floor, tree, null, look);
        else if (anim && tau < tl.end || lies) Grove.shadows(g, cx, floor, tree, at, look);

        if (!fell) {
            look.crack = (int) Math.min(9, Math.max(-1, (time - START) / MINE * 10));
            if (time < START) look.crack = -1;
            Grove.standing(g, painter, cx, floor, tree, look);
        } else if (anim && tau < tl.end || lies) {
            Grove.draw(g, painter, cx, floor, tree, at, look);
        }
        drops(tl, cx, floor, time, tau, anim, poof, atPlayer);
        // Replant: a free sapling in the stump cell once the fall has ended (or at once without the animation)
        double end = anim ? tl.end : 0;
        if (replant && fell && tau >= end) Grove.plant(painter, cx, floor, Stage.tex(tree.wood.sapling()), 0, 0, Math.min(1, (tau - end) / 3 + 0.4));
        painter.flush(g);

        // Instant: the crown bursts into leaf crumbs where it stood, with a gust at the stump (Fx.instant)
        if (fell && !anim) {
            leafBurst(g, cx, floor, tree, new Grove.Pose().at(tl, tree, 0), (float) tau, true);
            gust(g, cx, floor, (float) tau);
        }
        if (fell && anim && !poof && tl.leavesGone >= 0) leafBurst(g, cx, floor, tree, new Grove.Pose().at(tl, tree, tl.leavesGone - 1), (float) (tau - (tl.leavesGone - 1)), true, kept::containsKey);
        if (poof) {
            int puff = tick(tl, Timeline.Kind.POOF);
            if (puff >= 0) poof(g, cx, floor, tree, new Grove.Pose().at(tl, tree, puff), (float) (tau - (puff - 1)));
        }
        mine(g, cx, floor, time, START);
        attack(g, c, x, y, w, time >= START && time < BREAK);
        float clear = BREAK + fallMs + REST;
        if (time >= clear) Diorama.curtain(g, x, y, w, h, (time - clear) / (CLEAR - 30));
        String off = switch (topic) {
            case FALL -> anim ? null : t("off.animation");
            case LAND -> !anim ? t("off.animation") : poof ? t("off.land_poof") : land == 0 ? t("off.land") : null;
            case DROPS -> !anim ? t("off.animation") : null;
            case REPLANT -> replant ? null : t("off.replant");
        };
        if (off != null) Diorama.note(g, x, y, w, h, off);
    }

    // vanilla's small gust at the stump: three of Timber's small puffs rising and fading
    private static void gust(Gfx g, float cx, float floor, float age) {
        if (age < 0 || age >= 12) return;
        for (int i = 0; i < 3; i++) {
            double ox = (i - 1) * 0.35, oz = (i % 2) * 0.3 - 0.15, rise = 0.6 + age * 0.03 + i * 0.15;
            float s = 14 - i * 2;
            g.pushTranslate(Grove.sx(cx, ox, oz) - s / 2, Grove.sy(floor, ox, rise, oz) - s / 2);
            g.scale(s / 8, s / 8);
            g.blit(net.minecraft.resources.Identifier.tryParse("vanilla_timber:textures/particle/poof_small_" + Math.min(7, (int) (age * 0.66)) + ".png"), 0, 0, 0, 0, 8, 8, 8, 8, 8, 8, 0xFFFFFFFF);
            g.pop();
        }
    }

    static int tick(Timeline tl, Timeline.Kind kind) {
        for (Timeline.Event e : tl.events) if (e.kind() == kind) return e.tick();
        return -1;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }

    // the logs as items: popped out ring by ring where they lie (classic), left behind by the cloud (poof), dropped at
    // the stump (no animation), or all at the player (drops: at the player)
    private void drops(Timeline tl, float cx, float floor, float time, double tau, boolean anim, boolean poof, boolean atPlayer) {
        if (time < BREAK) return;
        if (!atPlayer) cutLog(tree, cx, floor, tau);
        else logItem(tree, cx, floor, 1.9, 0, 0.5, 0.02, 0.2, tau, 9);
        Random r = new Random(11);
        double[] q = new double[3];
        int poofTick = poof ? tick(tl, Timeline.Kind.POOF) : -1;
        for (int k = 0; k < tree.h; k++) {
            if (!tree.logRings[k]) continue;
            double vx = (r.nextDouble() - 0.5) * 0.1, vy = 0.2, vz = (r.nextDouble() - 0.5) * 0.3;
            if (anim && tl.lands(k)) continue;
            double born;
            double px, pz, py = 0.4;
            if (!anim) {
                born = 0;
                px = 0;
                pz = 0;
            } else {
                int t0 = poof ? poofTick : tl.popTick[k] < 0 ? tl.end : tl.popTick[k] - 1 + 4;
                if (t0 < 0) continue;
                born = t0;
                new Grove.Pose().at(tl, tree, t0).place(0, k + 0.5, 0, k, false, q);
                px = q[0];
                pz = q[2];
                py = Math.max(0, q[1] - 0.125);
            }
            double age = tau - born;
            if (age < 0) continue;
            if (atPlayer) {
                // vanilla's drop at the player's feet: the stack lands by the axe
                px = 1.7 + 0.15 * (k % 2);
                pz = 0.4 + 0.2 * (k % 3) - 0.2;
                vx = 0.02;
            }
            logItem(tree, cx, floor, px, py, pz + vz * 0.3, vx, vy, age, k);
        }
    }
}
