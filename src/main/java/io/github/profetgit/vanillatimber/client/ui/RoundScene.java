package io.github.profetgit.vanillatimber.client.ui;

import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.vanillatimber.core.Timeline;
import io.github.profetgit.vanillatimber.server.Config.Key;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

// Chops that either fell the tree or, as in vanilla, break only the chopped log and leave the rest standing; which one
// follows Timber's own rules with the current settings. Sneaking and the tool show two chops one after the other that
// differ in that one thing. Protected logs: the player builds a log wall against the trunk and chops the tree; the wall
// stays (protect on) or comes down with it. The refusal message: a chop the server turns down, with its words.
final class RoundScene extends TreeScene {
    enum Topic { SNEAK, TOOL, PROTECT, FEEDBACK }

    // one chop: sneaking, a stick in hand instead of the axe
    record Round(boolean sneak, boolean stick) {}

    // ms: the wall goes up one log at a time from PLACE_AT, PLACE_GAP apart
    private static final float START = 350, PLACE_AT = 100, PLACE_GAP = 100, PLACE = 650, REST = 1000, WALL_REST = 1500, CLEAR = 300;
    // the sneaking scene's only key, left of the stump
    private static final int SNEAK_KEY_X = 60;
    private final Topic topic;
    private final Grove.Tree tree;
    private final Tool stick = Tool.of("stick", false);
    private Timeline tl;
    private boolean joined;

    RoundScene(Topic topic) {
        this.topic = topic;
        tree = new Grove.Tree(Grove.OAK, 5);
        // a wall the player laid against the trunk: the same oak logs, on their side
        if (topic == Topic.PROTECT) {
            for (int py = 0; py < 2; py++) {
                for (int pz = 1; pz <= 3; pz++) {
                    Grove.Cell cell = new Grove.Cell(0, py, pz, false);
                    tree.cells.add(cell);
                    tree.placed.add(cell);
                }
            }
        }
    }

    // an unprotected wall belongs to the tree, so its bottom ring falls and pops too
    private Timeline timeline() {
        boolean wall = topic == Topic.PROTECT && !on(Key.PROTECT);
        if (tl == null || wall != joined) {
            joined = wall;
            tree.logRings[0] = wall;
            tl = tree.timeline(false, 0);
        }
        return tl;
    }

    // outside the sneaking scene the player sneaks only when that is what fells a tree
    private Round[] rounds() {
        boolean must = get(Key.SNEAK) == 1;
        return switch (topic) {
            case SNEAK -> new Round[] {new Round(false, false), new Round(true, false)};
            case TOOL -> new Round[] {new Round(must, false), new Round(must, true)};
            case PROTECT, FEEDBACK -> new Round[] {new Round(must, false)};
        };
    }

    // Falls.gates / Chops.mayChop with the current values; the message scene is a tree the server turns down
    private boolean fells(Round r) {
        if (topic == Topic.FEEDBACK) return false;
        int sneak = get(Key.SNEAK);
        if (sneak == 0 && r.sneak() || sneak == 1 && !r.sneak()) return false;
        return !(r.stick() && on(Key.REQUIRE_AXE));
    }

    private float lead() {
        return topic == Topic.PROTECT ? PLACE : 0;
    }

    private float length(Round r) {
        float fall = fells(r) ? timeline().end * MS : 900;
        return lead() + START + MINE + fall + (topic == Topic.PROTECT ? WALL_REST : REST) + CLEAR;
    }

    @Override
    public void render(Gfx g, Ctx c, int x, int y, int w, int h) {
        Round[] rounds = rounds();
        float period = 0;
        for (Round r : rounds) period += length(r);
        float time = time(c, period, get(Key.SNEAK), get(Key.REQUIRE_AXE), get(Key.PROTECT), get(Key.FEEDBACK));
        int i = 0;
        while (i < rounds.length - 1 && time >= length(rounds[i])) time -= length(rounds[i++]);
        Round r = rounds[i];
        boolean fells = fells(r), protect = on(Key.PROTECT);
        Timeline tl = timeline();
        float cx = cx(x, w), floor = y + STUMP_Y;
        Diorama.backdrop(g, x + (w - Diorama.W) / 2, y);

        float start = lead() + START, broke = start + MINE;
        double tau = (time - broke) / MS;
        Grove.Look look = new Grove.Look();
        look.placedStay = protect;
        if (topic == Topic.PROTECT) look.placedShown = placed(time);
        if (time < broke || !fells) {
            look.crack = time >= start && time < broke ? Math.min(9, (int) ((time - start) / MINE * 10)) : -1;
            look.stumpGone = time >= broke;
            Grove.shadows(g, cx, floor, tree, null, look);
            Grove.standing(g, painter, cx, floor, tree, look);
            if (time >= broke) logItem(tree, cx, floor, 0, 0.3, 0, 0.03, 0.2, tau, 0);
        } else if (tau < tl.end) {
            Grove.Pose at = pose.at(tl, tree, tau);
            Grove.shadows(g, cx, floor, tree, at, look);
            Grove.draw(g, painter, cx, floor, tree, at, look);
        } else if (topic == Topic.PROTECT && protect) {
            look.onlyPlaced = true;
            Grove.shadows(g, cx, floor, tree, null, look);
            Grove.standing(g, painter, cx, floor, tree, look);
        }
        if (fells && time >= broke) {
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
        if (fells && time >= broke && tl.leavesGone >= 0) leafBurst(g, cx, floor, tree, new Grove.Pose().at(tl, tree, tl.leavesGone - 1), (float) (tau - (tl.leavesGone - 1)), true);

        // the tool, the keys
        g.pushTranslate(cx - K() * Diorama.AX, floor - K() * Diorama.AY);
        g.scale(K(), K());
        (r.stick() ? stick : axe).draw(g, 0, 0, swing(time, start, broke, true));
        g.pop();
        if (topic == Topic.SNEAK) {
            // the sneak key beside the axe: up while standing, held in the sneaking round
            heldKey(g, c, Minecraft.getInstance().options.keyShift, x + SNEAK_KEY_X, y + KEY_Y, r.sneak() && time >= 80);
        } else {
            if (topic == Topic.PROTECT && time < start - 100) {
                g.binding(Minecraft.getInstance().options.keyUse, x + 46, y + KEY_Y, c.sz.fs, clicking(time));
            } else attack(g, c, x, y, w, time >= start && time < broke);
            if (r.sneak()) heldKey(g, c, Minecraft.getInstance().options.keyShift, x + 46 + 52, y + KEY_Y, time >= 80);
        }
        // the refusal on the action bar (feedback), as the server sends it
        if (topic == Topic.FEEDBACK && on(Key.FEEDBACK) && time >= broke && time < broke + 2000) {
            String msg = Component.translatableWithFallback("vanilla_timber.msg.protected", "Timber: part of this tree is protected").getString();
            int mw = g.vanillaWidth(msg);
            g.vanilla(msg, x + (w - mw) / 2, y + 8, 0xFFFF5555, true);
        }
        float clear = length(r) - CLEAR;
        if (time >= clear) Diorama.curtain(g, x, y, w, h, (time - clear) / (CLEAR - 30));
        String off = switch (topic) {
            case SNEAK, TOOL -> null;
            case PROTECT -> protect ? null : t("off.protect");
            case FEEDBACK -> on(Key.FEEDBACK) ? null : t("off.feedback");
        };
        if (off != null) Diorama.note(g, x, y, w, h, off);
    }

    // how many of the wall's logs are there at {@code time}
    private int placed(float time) {
        return time < PLACE_AT ? 0 : Math.min(tree.placed.size(), 1 + (int) ((time - PLACE_AT) / PLACE_GAP));
    }

    // the use button goes down once for each log
    private boolean clicking(float time) {
        float since = time - PLACE_AT;
        return since >= 0 && since < tree.placed.size() * PLACE_GAP && since % PLACE_GAP < 60;
    }
}
