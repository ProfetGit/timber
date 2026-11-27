package io.github.profetgit.vanillatimber.client.ui;

import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.vanillatimber.core.Cut;
import io.github.profetgit.vanillatimber.core.Timeline;
import io.github.profetgit.vanillatimber.server.Chops;
import io.github.profetgit.vanillatimber.server.Config.Key;

// Chop mode: each break of the stump is one chop; the trunk's cut layer narrows from both sides (Cut.step) and the tree
// falls on the last of Chops.needed(logs, chop_curve) chops. Off, the first break fells it.
final class ChopScene extends TreeScene {
    private static final float START = 350, GAP = 250, REST = 1300, CLEAR = 300;
    private final Grove.Tree tree = new Grove.Tree(Grove.OAK, 4);
    private Timeline tl;

    @Override
    public void render(Gfx g, Ctx c, int x, int y, int w, int h) {
        boolean chops = on(Key.CHOPS);
        int curve = get(Key.CHOP_CURVE);
        int logs = 0;
        for (boolean b : tree.logRings) if (b) logs++;
        int needed = chops ? Chops.needed(logs, curve) : 1;
        if (tl == null) tl = tree.timeline(false, 0);
        float breakAt = START + needed * MINE + (needed - 1) * GAP;
        float period = breakAt + tl.end * MS + REST + CLEAR;
        float time = time(c, period, chops, curve);
        float cx = cx(x, w), floor = y + STUMP_Y;
        Diorama.backdrop(g, x + (w - Diorama.W) / 2, y);

        // which chop is under way and how many are done
        int done = 0;
        float chopStart = START;
        for (int i = 0; i < needed; i++) {
            float s = START + i * (MINE + GAP);
            if (time >= s + MINE) done = i + 1;
            if (time >= s) chopStart = s;
        }
        Grove.Look look = new Grove.Look();
        boolean fell = time >= breakAt;
        double tau = (time - breakAt) / MS;
        if (!fell) {
            look.cutBox = Cut.upright(0, 0, 0, 0, 0, 0, Cut.step(done, needed));
            boolean mining = time >= chopStart && time < chopStart + MINE;
            look.crack = mining ? Math.min(9, (int) ((time - chopStart) / MINE * 10)) : -1;
            Grove.shadows(g, cx, floor, tree, null, look);
            Grove.standing(g, painter, cx, floor, tree, look);
        } else if (tau < tl.end) {
            Grove.Pose at = pose.at(tl, tree, tau);
            Grove.shadows(g, cx, floor, tree, at, look);
            Grove.draw(g, painter, cx, floor, tree, at, look);
        }
        if (fell) {
            cutLog(tree, cx, floor, tau);
            for (int k = 0; k < tree.h; k++) {
                if (!tree.logRings[k] || tl.popTick[k] < 0) continue;
                double born = tl.popTick[k] - 1 + 4;
                if (tau < born) continue;
                double[] q = new double[3];
                new Grove.Pose().at(tl, tree, born).place(0, k + 0.5, 0, k, false, q);
                logItem(tree, cx, floor, q[0], Math.max(0, q[1] - 0.125), q[2], 0.02 * (k % 2 == 0 ? 1 : -1), 0.2, tau - born, k);
            }
        }
        painter.flush(g);
        if (fell && tl.leavesGone >= 0) leafBurst(g, cx, floor, tree, new Grove.Pose().at(tl, tree, tl.leavesGone - 1), (float) (tau - (tl.leavesGone - 1)), true);
        float swingFrom = Math.max(START, chopStart);
        mine(g, cx, floor, time, swingFrom);
        attack(g, c, x, y, w, time >= chopStart && time < chopStart + MINE && !fell);
        float clear = breakAt + tl.end * MS + REST;
        if (time >= clear) Diorama.curtain(g, x, y, w, h, (time - clear) / (CLEAR - 30));
        if (!chops) Diorama.note(g, x, y, w, h, t("off.chops"));
    }
}
